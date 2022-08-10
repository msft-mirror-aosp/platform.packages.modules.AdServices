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

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_IO_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static com.android.adservices.service.measurement.attribution.TriggerContentProvider.TRIGGER_URI;
import static com.android.adservices.service.measurement.util.BaseUriExtractor.getBaseUri;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.measurement.DeletionParam;
import android.adservices.measurement.MeasurementManager;
import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebSourceRegistrationRequestInternal;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.adservices.measurement.WebTriggerRegistrationRequestInternal;
import android.annotation.NonNull;
import android.annotation.WorkerThread;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.RemoteException;
import android.view.InputEvent;

import com.android.adservices.LogUtil;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.measurement.inputverification.ClickVerifier;
import com.android.adservices.service.measurement.registration.SourceFetcher;
import com.android.adservices.service.measurement.registration.SourceRegistration;
import com.android.adservices.service.measurement.registration.TriggerFetcher;
import com.android.adservices.service.measurement.registration.TriggerRegistration;
import com.android.adservices.service.measurement.util.Web;
import com.android.internal.annotations.VisibleForTesting;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import javax.annotation.concurrent.ThreadSafe;

/**
 * This class is thread safe.
 *
 * @hide
 */
@ThreadSafe
@WorkerThread
public final class MeasurementImpl {
    private static final String ANDROID_APP_SCHEME = "android-app";
    private static volatile MeasurementImpl sMeasurementImpl;
    private final Context mContext;
    private final ReadWriteLock mReadWriteLock = new ReentrantReadWriteLock();
    private final DatastoreManager mDatastoreManager;
    private final SourceFetcher mSourceFetcher;
    private final TriggerFetcher mTriggerFetcher;
    private final ContentResolver mContentResolver;
    private final ClickVerifier mClickVerifier;
    private final Flags mFlags;

    private MeasurementImpl(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mDatastoreManager = DatastoreManagerFactory.getDatastoreManager(context);
        mSourceFetcher = new SourceFetcher();
        mTriggerFetcher = new TriggerFetcher();
        mClickVerifier = new ClickVerifier(context);
        mFlags = FlagsFactory.getFlags();
    }

    @VisibleForTesting
    MeasurementImpl(
            Context context,
            ContentResolver contentResolver,
            DatastoreManager datastoreManager,
            SourceFetcher sourceFetcher,
            TriggerFetcher triggerFetcher,
            ClickVerifier clickVerifier) {
        mContext = context;
        mContentResolver = contentResolver;
        mDatastoreManager = datastoreManager;
        mSourceFetcher = sourceFetcher;
        mTriggerFetcher = triggerFetcher;
        mClickVerifier = clickVerifier;
        mFlags = FlagsFactory.getFlagsForTest();
    }

    /**
     * Gets an instance of MeasurementImpl to be used.
     *
     * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the
     * existing instance will be returned.
     */
    @NonNull
    public static MeasurementImpl getInstance(Context context) {
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
     * Invoked when a package is installed.
     *
     * @param packageUri installed package {@link Uri}.
     * @param eventTime  time when the package was installed.
     */
    public void doInstallAttribution(@NonNull Uri packageUri, long eventTime) {
        LogUtil.i("Attributing installation for: " + packageUri);
        Uri appUri = getAppUri(packageUri);
        mReadWriteLock.readLock().lock();
        try {
            mDatastoreManager.runInTransaction(
                    (dao) -> dao.doInstallAttribution(appUri, eventTime));
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }

    /** Implement a registration request, returning a {@link AdServicesStatusUtils.StatusCode}. */
    @AdServicesStatusUtils.StatusCode
    int register(@NonNull RegistrationRequest request, long requestTime) {
        mReadWriteLock.readLock().lock();
        try {
            switch (request.getRegistrationType()) {
                case RegistrationRequest.REGISTER_SOURCE:
                    return fetchAndInsertSources(request, requestTime);

                case RegistrationRequest.REGISTER_TRIGGER:
                    return fetchAndInsertTriggers(request, requestTime);

                default:
                    return STATUS_INVALID_ARGUMENT;
            }
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }

    /**
     * Processes a source registration request delegated to OS from the caller, e.g. Chrome,
     * returning a status code.
     */
    int registerWebSource(@NonNull WebSourceRegistrationRequestInternal request, long requestTime) {
        WebSourceRegistrationRequest sourceRegistrationRequest =
                request.getSourceRegistrationRequest();
        if (!isValid(sourceRegistrationRequest)) {
            LogUtil.e("registerWebSource received invalid parameters");
            return STATUS_INVALID_ARGUMENT;
        }
        mReadWriteLock.readLock().lock();
        try {
            Optional<List<SourceRegistration>> fetch =
                    mSourceFetcher.fetchWebSources(sourceRegistrationRequest);
            LogUtil.d("MeasurementImpl: registerWebSource: success=" + fetch.isPresent());
            if (fetch.isPresent()) {
                insertSources(
                        fetch.get(),
                        requestTime,
                        sourceRegistrationRequest.getTopOriginUri(),
                        getRegistrant(request.getPackageName()),
                        getSourceType(
                                sourceRegistrationRequest.getInputEvent(),
                                request.getRequestTime()));
                return STATUS_SUCCESS;
            } else {
                return STATUS_IO_ERROR;
            }
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }

    /**
     * Processes a trigger registration request delegated to OS from the caller, e.g. Chrome,
     * returning a status code.
     */
    int registerWebTrigger(
            @NonNull WebTriggerRegistrationRequestInternal request, long requestTime) {
        WebTriggerRegistrationRequest triggerRegistrationRequest =
                request.getTriggerRegistrationRequest();
        if (!isValid(triggerRegistrationRequest)) {
            LogUtil.e("registerWebTrigger received invalid parameters");
            return STATUS_INVALID_ARGUMENT;
        }
        mReadWriteLock.readLock().lock();
        try {
            Optional<List<TriggerRegistration>> fetch =
                    mTriggerFetcher.fetchWebTriggers(triggerRegistrationRequest);
            LogUtil.d("MeasurementImpl: registerWebTrigger: success=" + fetch.isPresent());
            if (fetch.isPresent()) {
                insertTriggers(
                        fetch.get(),
                        requestTime,
                        triggerRegistrationRequest.getDestination(),
                        getRegistrant(request.getPackageName()));
                return STATUS_SUCCESS;
            } else {
                return STATUS_IO_ERROR;
            }
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }

    /**
     * Implement a deleteRegistrations request, returning a r{@link
     * AdServicesStatusUtils.StatusCode}.
     */
    @AdServicesStatusUtils.StatusCode
    int deleteRegistrations(@NonNull DeletionParam request) {
        mReadWriteLock.readLock().lock();
        try {
            final boolean deleteResult =
                    mDatastoreManager.runInTransaction(
                            (dao) ->
                                    dao.deleteMeasurementData(
                                            getRegistrant(request.getPackageName()),
                                            request.getStart(),
                                            request.getEnd(),
                                            request.getOriginUris(),
                                            request.getDomainUris(),
                                            request.getMatchBehavior(),
                                            request.getDeletionMode()));
            return deleteResult ? STATUS_SUCCESS : STATUS_INTERNAL_ERROR;
        } catch (NullPointerException | IllegalArgumentException e) {
            LogUtil.e(e, "Delete registration received invalid parameters");
            return STATUS_INVALID_ARGUMENT;
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }

    /**
     * Implement a getMeasurementApiStatus request, returning a result code.
     */
    @MeasurementManager.MeasurementApiState int getMeasurementApiStatus() {
        AdServicesApiConsent consent =
                ConsentManager.getInstance(mContext).getConsent(mContext.getPackageManager());
        if (consent.isGiven()) {
            return MeasurementManager.MEASUREMENT_API_STATE_ENABLED;
        } else {
            return MeasurementManager.MEASUREMENT_API_STATE_DISABLED;
        }
    }

    /**
     * Delete all records from a specific package.
     */
    public void deletePackageRecords(Uri packageUri) {
        Uri appUri = getAppUri(packageUri);
        LogUtil.d("Deleting records for " + appUri);
        mReadWriteLock.writeLock().lock();
        try {
            mDatastoreManager.runInTransaction((dao) -> {
                dao.deleteAppRecords(appUri);
                dao.undoInstallAttribution(appUri);
            });
        } catch (NullPointerException | IllegalArgumentException e) {
            LogUtil.e(e, "Delete package records received invalid parameters");
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    /**
     * Delete all data generated by Measurement API, except for tables in the exclusion list.
     *
     * @param tablesToExclude a {@link List} of tables that won't be deleted.
     */
    public void deleteAllMeasurementData(@NonNull List<String> tablesToExclude) {
        mReadWriteLock.writeLock().lock();
        try {
            mDatastoreManager.runInTransaction(
                    (dao) -> dao.deleteAllMeasurementData(tablesToExclude));
            LogUtil.v(
                    "All data is cleared for Measurement API except: %s",
                    tablesToExclude.toString());
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    private int fetchAndInsertTriggers(RegistrationRequest request, long requestTime) {
        Optional<List<TriggerRegistration>> fetch = mTriggerFetcher.fetchTrigger(request);
        LogUtil.d("MeasurementImpl: register: success=" + fetch.isPresent());
        if (fetch.isPresent()) {
            insertTriggers(
                    fetch.get(),
                    requestTime,
                    request.getTopOriginUri(),
                    getRegistrant(request.getPackageName()));
            return STATUS_SUCCESS;
        } else {
            return STATUS_IO_ERROR;
        }
    }

    private int fetchAndInsertSources(RegistrationRequest request, long requestTime) {
        Optional<List<SourceRegistration>> fetch = mSourceFetcher.fetchSource(request);
        LogUtil.d("MeasurementImpl: register: success=" + fetch.isPresent());
        if (fetch.isPresent()) {
            insertSources(
                    fetch.get(),
                    requestTime,
                    request.getTopOriginUri(),
                    getRegistrant(request.getPackageName()),
                    getSourceType(request.getInputEvent(), request.getRequestTime()));
            return STATUS_SUCCESS;
        } else {
            return STATUS_IO_ERROR;
        }
    }

    private void insertSources(
            List<SourceRegistration> sourceRegistrations,
            long sourceEventTime,
            Uri topOriginUri,
            Uri registrant,
            Source.SourceType sourceType) {
        for (SourceRegistration registration : sourceRegistrations) {
            Source source =
                    createSource(
                            sourceEventTime,
                            registration,
                            topOriginUri,
                            registrant,
                            sourceType,
                            // Only first destination to avoid AdTechs change this
                            sourceRegistrations.get(0).getAppDestination(),
                            sourceRegistrations.get(0).getWebDestination());
            insertSource(source);
        }
    }

    private Source createSource(
            long sourceEventTime,
            SourceRegistration registration,
            Uri topOriginUri,
            Uri registrant,
            Source.SourceType sourceType,
            Uri destination,
            Uri webDestination) {
        return new Source.Builder()
                .setEventId(registration.getSourceEventId())
                .setPublisher(getBaseUri(topOriginUri))
                .setAppDestination(destination)
                .setWebDestination(webDestination)
                .setAdTechDomain(getBaseUri(registration.getReportingOrigin()))
                .setRegistrant(registrant)
                .setSourceType(sourceType)
                .setPriority(registration.getSourcePriority())
                .setEventTime(sourceEventTime)
                .setExpiryTime(
                        sourceEventTime + TimeUnit.SECONDS.toMillis(registration.getExpiry()))
                .setInstallAttributionWindow(
                        TimeUnit.SECONDS.toMillis(registration.getInstallAttributionWindow()))
                .setInstallCooldownWindow(
                        TimeUnit.SECONDS.toMillis(registration.getInstallCooldownWindow()))
                // Setting as TRUTHFULLY as default value for tests.
                // This will be overwritten by getSourceEventReports.
                .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                .setAggregateSource(registration.getAggregateSource())
                .setAggregateFilterData(registration.getAggregateFilterData())
                .setDebugKey(registration.getDebugKey())
                .build();
    }

    private void insertSource(Source source) {
        List<EventReport> eventReports = getSourceEventReports(source);
        mDatastoreManager.runInTransaction(
                (dao) -> {
                    dao.insertSource(source);
                    for (EventReport report : eventReports) {
                        dao.insertEventReport(report);
                    }
                });
    }

    @VisibleForTesting
    List<EventReport> getSourceEventReports(Source source) {
        List<Source.FakeReport> fakeReports = source.assignAttributionModeAndGenerateFakeReport();
        return fakeReports.stream()
                .map(
                        fakeReport ->
                                new EventReport.Builder()
                                        .setSourceId(source.getEventId())
                                        .setReportTime(fakeReport.getReportingTime())
                                        .setTriggerData(fakeReport.getTriggerData())
                                        .setAttributionDestination(source.getAppDestination())
                                        .setAdTechDomain(source.getAdTechDomain())
                                        .setTriggerTime(0)
                                        .setTriggerPriority(0L)
                                        .setTriggerDedupKey(null)
                                        .setSourceType(source.getSourceType())
                                        .setStatus(EventReport.Status.PENDING)
                                        .setRandomizedTriggerRate(
                                                source.getRandomAttributionProbability())
                                        .build())
                .collect(Collectors.toList());
    }

    @VisibleForTesting
    Source.SourceType getSourceType(InputEvent inputEvent, long requestTime) {
        // If click verification is enabled and the InputEvent is not null, but it cannot be
        // verified, then the SourceType is demoted to EVENT.
        if (mFlags.getMeasurementIsClickVerificationEnabled()
                && inputEvent != null
                && !mClickVerifier.isInputEventVerifiable(inputEvent, requestTime)) {
            return Source.SourceType.EVENT;
        } else {
            return inputEvent == null ? Source.SourceType.EVENT : Source.SourceType.NAVIGATION;
        }
    }

    private void insertTriggers(
            List<TriggerRegistration> responseBasedRegistrations,
            long triggerTime,
            Uri topOrigin,
            Uri registrant) {
        for (TriggerRegistration registration : responseBasedRegistrations) {
            Trigger trigger = createTrigger(registration, triggerTime, topOrigin, registrant);
            mDatastoreManager.runInTransaction((dao) -> dao.insertTrigger(trigger));
        }
        notifyTriggerContentProvider();
    }

    private void notifyTriggerContentProvider() {
        try (ContentProviderClient contentProviderClient =
                     mContentResolver.acquireContentProviderClient(TRIGGER_URI)) {
            if (contentProviderClient != null) {
                contentProviderClient.insert(TRIGGER_URI, null);
            }
        } catch (RemoteException e) {
            LogUtil.e(e, "Trigger Content Provider invocation failed.");
        }
    }

    private Trigger createTrigger(
            TriggerRegistration registration, long triggerTime, Uri topOrigin, Uri registrant) {
        return new Trigger.Builder()
                .setAttributionDestination(topOrigin)
                .setAdTechDomain(getBaseUri(registration.getReportingOrigin()))
                .setRegistrant(registrant)
                .setTriggerTime(triggerTime)
                .setEventTriggers(registration.getEventTriggers())
                .setAggregateTriggerData(registration.getAggregateTriggerData())
                .setAggregateValues(registration.getAggregateValues())
                .setFilters(registration.getFilters())
                .setDebugKey(registration.getDebugKey())
                .build();
    }

    private Uri getRegistrant(String packageName) {
        return Uri.parse(ANDROID_APP_SCHEME + "://" + packageName);
    }

    private Uri getAppUri(Uri packageUri) {
        return Uri.parse(ANDROID_APP_SCHEME + "://" + packageUri.getEncodedSchemeSpecificPart());
    }

    private boolean isValid(WebSourceRegistrationRequest sourceRegistrationRequest) {
        Uri verifiedDestination = sourceRegistrationRequest.getVerifiedDestination();
        Uri webDestination = sourceRegistrationRequest.getWebDestination();

        if (verifiedDestination == null) {
            return webDestination == null
                    ? true
                    : Web.topPrivateDomainAndScheme(webDestination).isPresent();
        }

        return isVerifiedDestination(
                verifiedDestination, webDestination, sourceRegistrationRequest.getAppDestination());
    }

    private boolean isVerifiedDestination(
            Uri verifiedDestination, Uri webDestination, Uri appDestination) {
        String destinationPackage = null;
        if (appDestination != null) {
            destinationPackage = appDestination.getHost();
        }
        String verifiedScheme = verifiedDestination.getScheme();
        String verifiedHost = verifiedDestination.getHost();

        // Verified destination matches appDestination value
        if (destinationPackage != null
                && verifiedHost != null
                && (verifiedScheme == null || verifiedScheme.equals(ANDROID_APP_SCHEME))
                && verifiedHost.equals(destinationPackage)) {
            return true;
        }

        try {
            Intent intent = Intent.parseUri(verifiedDestination.toString(), 0);
            ComponentName componentName = intent.resolveActivity(mContext.getPackageManager());
            if (componentName == null) {
                return false;
            }

            // (ComponentName::getPackageName cannot be null)
            String verifiedPackage = componentName.getPackageName();

            // Try to match an app vendor store and extract a target package
            if (destinationPackage != null
                    && verifiedPackage.equals(AppVendorPackages.PLAY_STORE)) {
                String targetPackage = getTargetPackageFromPlayStoreUri(verifiedDestination);
                return targetPackage != null && targetPackage.equals(destinationPackage);

            // Try to match web destination
            } else if (webDestination == null) {
                return false;
            } else {
                Optional<Uri> webDestinationTopPrivateDomainAndScheme =
                        Web.topPrivateDomainAndScheme(webDestination);
                Optional<Uri> verifiedDestinationTopPrivateDomainAndScheme =
                        Web.topPrivateDomainAndScheme(verifiedDestination);
                return webDestinationTopPrivateDomainAndScheme.isPresent()
                        && verifiedDestinationTopPrivateDomainAndScheme.isPresent()
                        && webDestinationTopPrivateDomainAndScheme.get().equals(
                                verifiedDestinationTopPrivateDomainAndScheme.get());
            }
        } catch (URISyntaxException e) {
            LogUtil.e(e,
                    "MeasurementImpl::handleVerifiedDestination: failed to parse intent URI: %s",
                    verifiedDestination.toString());
            return false;
        }
    }

    private static boolean isValid(WebTriggerRegistrationRequest triggerRegistrationRequest) {
        Uri destination = triggerRegistrationRequest.getDestination();
        return Web.topPrivateDomainAndScheme(destination).isPresent();
    }

    private static String getTargetPackageFromPlayStoreUri(Uri uri) {
        return uri.getQueryParameter("id");
    }

    private interface AppVendorPackages {
        String PLAY_STORE = "com.android.vending";
    }
}
