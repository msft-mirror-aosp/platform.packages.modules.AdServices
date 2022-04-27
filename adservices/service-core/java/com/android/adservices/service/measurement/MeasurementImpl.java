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

import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.IMeasurementCallback;
import android.adservices.measurement.RegistrationRequest;
import android.annotation.NonNull;
import android.annotation.WorkerThread;
import android.content.AttributionSource;
import android.content.Context;
import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.service.measurement.registration.SourceFetcher;
import com.android.adservices.service.measurement.registration.SourceRegistration;
import com.android.adservices.service.measurement.registration.TriggerFetcher;
import com.android.adservices.service.measurement.registration.TriggerRegistration;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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

    private MeasurementImpl(Context context) {
        mDatastoreManager = DatastoreManagerFactory.getDatastoreManager(context);
        mSourceFetcher = new SourceFetcher();
        mTriggerFetcher = new TriggerFetcher();
    }

    @VisibleForTesting
    MeasurementImpl(DatastoreManager datastoreManager, SourceFetcher sourceFetcher,
            TriggerFetcher triggerFetcher) {
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
            mDatastoreManager.runInTransaction((dao) ->
                    dao.insertSource(
                            /* sourceEventId */ registration.getSourceEventId(),
                            /* attributionSource */ request.getTopOriginUri(),
                            /* attributionDestination */ registration.getDestination(),
                            /* reportTo */ getBaseUri(request.getRegistrationUri()),
                            /* registrant */ getRegistrant(request.getAttributionSource()),
                            /* sourceEventTime */ sourceEventTime,
                            /* expiryTime */ sourceEventTime
                                    + TimeUnit.SECONDS.toMillis(registration.getExpiry()),
                            /* priority */ registration.getSourcePriority(),
                            /* sourceType */ getSourceType(request)));
        }
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
                            /* reportTo */ getBaseUri(request.getRegistrationUri()),
                            /* registrant */ getRegistrant(request.getAttributionSource()),
                            /* triggerTime */ triggerTime,
                            /* triggerData */ getTruncatedTriggerData(registration),
                            /* dedupKey */ registration.getDeduplicationKey(),
                            /* priority */ registration.getTriggerPriority()));
        }
    }

    private long getTruncatedTriggerData(TriggerRegistration registration) {
        return registration.getTriggerData() & 7;
    }

    private Uri getRegistrant(AttributionSource attributionSource) {
        return Uri.parse(
                "android-app://" + attributionSource.getPackageName());
    }
}
