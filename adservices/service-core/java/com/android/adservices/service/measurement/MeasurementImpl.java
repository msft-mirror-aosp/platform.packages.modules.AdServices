/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.adservices.service.measurement;

import static com.android.adservices.service.measurement.attribution.BaseUriExtractor.getBaseUri;
import static com.android.adservices.service.measurement.attribution.TriggerContentProvider.TRIGGER_URI;

import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.IMeasurementCallback;
import android.adservices.measurement.RegistrationRequest;
import android.annotation.NonNull;
import android.annotation.WorkerThread;
import android.content.AttributionSource;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.service.measurement.registration.SourceFetcher;
import com.android.adservices.service.measurement.registration.SourceRegistration;
import com.android.adservices.service.measurement.registration.TriggerFetcher;
import com.android.adservices.service.measurement.registration.TriggerRegistration;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import javax.annotation.concurrent.ThreadSafe;


/**
 *
 * <p>This class is thread safe.
 * @hide
 */
@ThreadSafe
@WorkerThread
public final class MeasurementImpl {
    private final ReadWriteLock mReadWriteLock = new ReentrantReadWriteLock();
    private static volatile MeasurementImpl sMeasurementImpl;
    private final DatastoreManager mDatastoreManager;
    private final SourceFetcher mSourceFetcher;
    private final TriggerFetcher mTriggerFetcher;
    private final ContentResolver mContentResolver;

    private MeasurementImpl(Context context) {
        mContentResolver = context.getContentResolver();
        mDatastoreManager = DatastoreManagerFactory.getDatastoreManager(context);
        mSourceFetcher = new SourceFetcher();
        mTriggerFetcher = new TriggerFetcher();
    }

    @VisibleForTesting
    MeasurementImpl(ContentResolver contentResolver, DatastoreManager datastoreManager,
            SourceFetcher sourceFetcher, TriggerFetcher triggerFetcher) {
        mContentResolver = contentResolver;
        mDatastoreManager = datastoreManager;
        mSourceFetcher = sourceFetcher;
        mTriggerFetcher = triggerFetcher;
    }

    /**
     * Gets an instance of MeasurementImpl to be used.
     *
     * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the
     * existing instance will be returned.
     */
    @NonNull
    static MeasurementImpl getInstance(Context context) {
        if (sMeasurementImpl == null) {
            synchronized (MeasurementImpl.class) {
                if (sMeasurementImpl == null) {
                    sMeasurementImpl = new MeasurementImpl(context);
                }
            }
        }
        return sMeasurementImpl;
    }

    /**
     * Implement a registration request, returning a result code.
     */
    int register(@NonNull RegistrationRequest request, long requestTime) {
        mReadWriteLock.readLock().lock();
        try {
            switch (request.getRegistrationType()) {
                case RegistrationRequest.REGISTER_SOURCE: {
                    ArrayList<SourceRegistration> results = new ArrayList();
                    boolean success = mSourceFetcher.fetchSource(request, results);
                    LogUtil.d("MeasurementImpl: register: success=" + success);
                    if (success) {
                        insertSources(request, results, requestTime);
                        return IMeasurementCallback.RESULT_OK;
                    } else {
                        return IMeasurementCallback.RESULT_IO_ERROR;
                    }
                }

                case RegistrationRequest.REGISTER_TRIGGER: {
                    ArrayList<TriggerRegistration> results = new ArrayList();
                    boolean success = mTriggerFetcher.fetchTrigger(request, results);
                    LogUtil.d("MeasurementImpl: register: success=" + success);
                    if (success) {
                        insertTriggers(request, results, requestTime);
                        return IMeasurementCallback.RESULT_OK;
                    } else {
                        return IMeasurementCallback.RESULT_IO_ERROR;
                    }
                }

                default:
                    return IMeasurementCallback.RESULT_INVALID_ARGUMENT;
            }
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }

    /**
     * Implement a deleteRegistrations request, returning a result code.
     */
    int deleteRegistrations(@NonNull DeletionRequest request) {
        mReadWriteLock.readLock().lock();
        try {
            final boolean deleteResult = mDatastoreManager.runInTransaction((dao) ->
                    dao.deleteMeasurementData(
                            getRegistrant(request.getAttributionSource()),
                            request.getOriginUri(),
                            request.getStart(),
                            request.getEnd()
                    )
            );
            return deleteResult
                    ? IMeasurementCallback.RESULT_OK : IMeasurementCallback.RESULT_INTERNAL_ERROR;
        } catch (NullPointerException | IllegalArgumentException e) {
            LogUtil.e(e, "Delete registration received invalid parameters");
            return IMeasurementCallback.RESULT_INVALID_ARGUMENT;
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }

    private void insertSources(
            @NonNull RegistrationRequest request,
            ArrayList<SourceRegistration> responseBasedRegistrations,
            long sourceEventTime) {
        for (SourceRegistration registration : responseBasedRegistrations) {
            Source source = new Source.Builder()
                    .setEventId(registration.getSourceEventId())
                    .setAttributionSource(request.getTopOriginUri())
                    // Only first destination to avoid AdTechs change this
                    .setAttributionDestination(responseBasedRegistrations.get(0)
                            .getDestination())
                    .setReportTo(getBaseUri(registration.getReportingOrigin()))
                    .setRegistrant(getRegistrant(request.getAttributionSource()))
                    .setSourceType(getSourceType(request))
                    .setPriority(registration.getSourcePriority())
                    .setEventTime(sourceEventTime)
                    .setExpiryTime(sourceEventTime
                            + TimeUnit.SECONDS.toMillis(registration.getExpiry()))
                    // Setting as TRUTHFULLY as default value for tests.
                    // This will be overwritten by getSourceEventReports.
                    .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                    .build();
            List<EventReport> eventReports = getSourceEventReports(source);
            mDatastoreManager.runInTransaction((dao) -> {
                dao.insertSource(
                        /* sourceEventId */ source.getEventId(),
                        /* attributionSource */ source.getAttributionSource(),
                        /* attributionDestination */ source.getAttributionDestination(),
                        /* reportTo */ source.getReportTo(),
                        /* registrant */ source.getRegistrant(),
                        /* sourceEventTime */ source.getEventTime(),
                        /* expiryTime */ source.getExpiryTime(),
                        /* priority */ source.getPriority(),
                        /* sourceType */ source.getSourceType(),
                        /* attributionMode */ source.getAttributionMode());
                for (EventReport report : eventReports) {
                    dao.insertEventReport(report);
                }
            });
        }
    }

    @VisibleForTesting
    List<EventReport> getSourceEventReports(Source source) {
        List<Source.FakeReport> fakeReports = source.assignAttributionModeAndGenerateFakeReport();
        return fakeReports.stream().map(fakeReport ->
                new EventReport.Builder()
                        .setSourceId(source.getEventId())
                        .setReportTime(fakeReport.getReportingTime())
                        .setTriggerData(fakeReport.getTriggerData())
                        .setAttributionDestination(source.getAttributionDestination())
                        .setReportTo(source.getReportTo())
                        .setTriggerTime(0)
                        .setTriggerPriority(0)
                        .setTriggerDedupKey(null)
                        .setSourceType(source.getSourceType())
                        .setStatus(EventReport.Status.PENDING)
                        .build()
        ).collect(Collectors.toList());
    }

    private Source.SourceType getSourceType(RegistrationRequest request) {
        return request.getInputEvent() == null
                ? Source.SourceType.EVENT : Source.SourceType.NAVIGATION;
    }

    private void insertTriggers(
            @NonNull RegistrationRequest request,
            ArrayList<TriggerRegistration> responseBasedRegistrations,
            long triggerTime) {
        for (TriggerRegistration registration : responseBasedRegistrations) {
            mDatastoreManager.runInTransaction((dao) ->
                    dao.insertTrigger(
                            /* attributionDestination */ request.getTopOriginUri(),
                            /* reportTo */ getBaseUri(registration.getReportingOrigin()),
                            /* registrant */ getRegistrant(request.getAttributionSource()),
                            /* triggerTime */ triggerTime,
                            /* triggerData */ registration.getTriggerData(),
                            /* dedupKey */ registration.getDeduplicationKey(),
                            /* priority */ registration.getTriggerPriority()));
        }
        try (ContentProviderClient contentProviderClient =
                     mContentResolver.acquireContentProviderClient(TRIGGER_URI)) {
            if (contentProviderClient != null) {
                contentProviderClient.insert(TRIGGER_URI, null);
            }
        } catch (RemoteException e) {
            LogUtil.e(e, "Trigger Content Provider invocation failed.");
        }
    }

    private Uri getRegistrant(AttributionSource attributionSource) {
        return Uri.parse(
                "android-app://" + attributionSource.getPackageName());
    }
}
