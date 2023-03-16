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

import static com.android.adservices.service.measurement.SystemHealthParams.MAX_TRIGGER_REGISTERS_PER_DESTINATION;
import static com.android.adservices.service.measurement.attribution.TriggerContentProvider.TRIGGER_URI;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.measurement.registration.AsyncSourceFetcher;
import com.android.adservices.service.measurement.registration.AsyncTriggerFetcher;
import com.android.adservices.service.measurement.reporting.DebugReportApi;
import com.android.adservices.service.measurement.util.AsyncFetchStatus;
import com.android.adservices.service.measurement.util.AsyncRedirect;
import com.android.adservices.service.measurement.util.Enrollment;
import com.android.adservices.service.measurement.util.Web;
import com.android.internal.annotations.VisibleForTesting;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Runner for servicing queued registration requests */
public class AsyncRegistrationQueueRunner {
    private static AsyncRegistrationQueueRunner sAsyncRegistrationQueueRunner;
    private DatastoreManager mDatastoreManager;
    private AsyncSourceFetcher mAsyncSourceFetcher;
    private AsyncTriggerFetcher mAsyncTriggerFetcher;
    private EnrollmentDao mEnrollmentDao;
    private final ContentResolver mContentResolver;
    private final DebugReportApi mDebugReportApi;

    private AsyncRegistrationQueueRunner(Context context) {
        mDatastoreManager = DatastoreManagerFactory.getDatastoreManager(context);
        mAsyncSourceFetcher = new AsyncSourceFetcher(context);
        mAsyncTriggerFetcher = new AsyncTriggerFetcher(context);
        mEnrollmentDao = EnrollmentDao.getInstance(context);
        mContentResolver = context.getContentResolver();
        mDebugReportApi = new DebugReportApi(context);
    }

    @VisibleForTesting
    AsyncRegistrationQueueRunner(
            ContentResolver contentResolver,
            AsyncSourceFetcher asyncSourceFetcher,
            AsyncTriggerFetcher asyncTriggerFetcher,
            EnrollmentDao enrollmentDao,
            DatastoreManager datastoreManager,
            DebugReportApi debugReportApi) {
        mAsyncSourceFetcher = asyncSourceFetcher;
        mAsyncTriggerFetcher = asyncTriggerFetcher;
        mDatastoreManager = datastoreManager;
        mEnrollmentDao = enrollmentDao;
        mContentResolver = contentResolver;
        mDebugReportApi = debugReportApi;
    }

    /**
     * Returns an instance of AsyncRegistrationQueueRunner.
     *
     * @param context the current {@link Context}.
     */
    public static synchronized AsyncRegistrationQueueRunner getInstance(Context context) {
        Objects.requireNonNull(context);
        if (sAsyncRegistrationQueueRunner == null) {
            sAsyncRegistrationQueueRunner = new AsyncRegistrationQueueRunner(context);
        }
        return sAsyncRegistrationQueueRunner;
    }

    /**
     * Service records in the AsyncRegistration Queue table.
     *
     * @param recordServiceLimit a long representing how many records will be serviced during this
     *     run.
     * @param retryLimit represents the amount of retries that will be allowed for each record.
     */
    public void runAsyncRegistrationQueueWorker(long recordServiceLimit, short retryLimit) {
        Set<String> failedAdTechEnrollmentIds = new HashSet<>();
        for (int i = 0; i < recordServiceLimit; i++) {
            Optional<AsyncRegistration> optionalAsyncRegistration =
                    mDatastoreManager.runInTransactionWithResult(
                            (dao) -> {
                                List<String> failedAdTechEnrollmentIdsList =
                                        new ArrayList<String>();
                                failedAdTechEnrollmentIdsList.addAll(failedAdTechEnrollmentIds);
                                return dao.fetchNextQueuedAsyncRegistration(
                                        retryLimit, failedAdTechEnrollmentIdsList);
                            });

            AsyncRegistration asyncRegistration;
            if (optionalAsyncRegistration.isPresent()) {
                asyncRegistration = optionalAsyncRegistration.get();
            } else {
                LogUtil.d("AsyncRegistrationQueueRunner: no async registration fetched.");
                return;
            }

            if (asyncRegistration.getType() == AsyncRegistration.RegistrationType.APP_SOURCE
                    || asyncRegistration.getType()
                            == AsyncRegistration.RegistrationType.WEB_SOURCE) {
                LogUtil.d("AsyncRegistrationQueueRunner:" + " processing source");
                processSourceRegistration(asyncRegistration, failedAdTechEnrollmentIds);
            } else if (asyncRegistration.getType() == AsyncRegistration.RegistrationType.APP_TRIGGER
                    || asyncRegistration.getType()
                            == AsyncRegistration.RegistrationType.WEB_TRIGGER) {
                LogUtil.d("AsyncRegistrationQueueRunner:" + " processing trigger");
                processTriggerRegistration(asyncRegistration, failedAdTechEnrollmentIds);
            }
        }
    }

    private void processSourceRegistration(
            AsyncRegistration asyncRegistration, Set<String> failedAdTechEnrollmentIds) {
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        Optional<Source> resultSource =
                mAsyncSourceFetcher.fetchSource(asyncRegistration, asyncFetchStatus, asyncRedirect);

        mDatastoreManager.runInTransaction(
                (dao) -> {
                    if (asyncFetchStatus.getStatus()
                                    == AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE
                            || asyncFetchStatus.getStatus()
                                    == AsyncFetchStatus.ResponseStatus.NETWORK_ERROR) {
                        failedAdTechEnrollmentIds.add(asyncRegistration.getEnrollmentId());
                        asyncRegistration.incrementRetryCount();
                        dao.updateRetryCount(asyncRegistration);
                        LogUtil.d(
                                "AsyncRegistrationQueueRunner: "
                                        + "async "
                                        + "source registration will be queued for retry "
                                        + "Fetch Status : "
                                        + asyncFetchStatus.getStatus());
                    } else if (asyncFetchStatus.getStatus()
                                    == AsyncFetchStatus.ResponseStatus.PARSING_ERROR
                            || asyncFetchStatus.getStatus()
                                    == AsyncFetchStatus.ResponseStatus.INVALID_ENROLLMENT) {
                        dao.deleteAsyncRegistration(asyncRegistration.getId());
                        LogUtil.d(
                                "AsyncRegistrationQueueRunner: "
                                        + "async "
                                        + "source registration will not be queued for retry. "
                                        + "Fetch Status : "
                                        + asyncFetchStatus.getStatus());
                    } else if (asyncFetchStatus.getStatus()
                            == AsyncFetchStatus.ResponseStatus.SUCCESS) {
                        LogUtil.d(
                                "AsyncRegistrationQueueRunner: "
                                        + "async "
                                        + "source registration success case. "
                                        + "Fetch Status : "
                                        + asyncFetchStatus.getStatus());
                        if (resultSource.isPresent()) {
                            Source source = resultSource.get();
                            Uri topOrigin =
                                    asyncRegistration.getType()
                                                    == AsyncRegistration.RegistrationType.WEB_SOURCE
                                            ? asyncRegistration.getTopOrigin()
                                            : getPublisher(asyncRegistration);
                            @EventSurfaceType
                            int publisherType =
                                    asyncRegistration.getType()
                                                    == AsyncRegistration.RegistrationType.WEB_SOURCE
                                            ? EventSurfaceType.WEB
                                            : EventSurfaceType.APP;
                            if (isSourceAllowedToInsert(
                                    source, topOrigin, publisherType, dao, mDebugReportApi)) {
                                insertSourceFromTransaction(source, dao);
                            }
                            if (asyncRegistration.shouldProcessRedirects()) {
                                LogUtil.d(
                                        "AsyncRegistrationQueueRunner: "
                                                + "async "
                                                + "source registration; processing redirects. "
                                                + "Fetch Status : "
                                                + asyncFetchStatus.getStatus());
                                processRedirects(
                                        asyncRegistration,
                                        asyncRedirect,
                                        asyncRegistration.getWebDestination(),
                                        getNullableUriFromDestinationsList(
                                                source.getAppDestinations()),
                                        dao);
                            }
                        }
                        dao.deleteAsyncRegistration(asyncRegistration.getId());
                    }
                });
    }

    private void processTriggerRegistration(
            AsyncRegistration asyncRegistration, Set<String> failedAdTechEnrollmentIds) {
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        Optional<Trigger> resultTrigger = mAsyncTriggerFetcher.fetchTrigger(
                asyncRegistration, asyncFetchStatus, asyncRedirect);
        boolean transactionSuccessful =
                mDatastoreManager.runInTransaction(
                        (dao) -> {
                            if (asyncFetchStatus.getStatus()
                                            == AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE
                                    || asyncFetchStatus.getStatus()
                                            == AsyncFetchStatus.ResponseStatus.NETWORK_ERROR) {
                                failedAdTechEnrollmentIds.add(asyncRegistration.getEnrollmentId());
                                asyncRegistration.incrementRetryCount();
                                dao.updateRetryCount(asyncRegistration);
                                LogUtil.d(
                                        "AsyncRegistrationQueueRunner: "
                                                + "async "
                                                + "trigger registration will be queued for retry "
                                                + "Fetch Status : "
                                                + asyncFetchStatus.getStatus());
                            } else if (asyncFetchStatus.getStatus()
                                            == AsyncFetchStatus.ResponseStatus.PARSING_ERROR
                                    || asyncFetchStatus.getStatus()
                                            == AsyncFetchStatus.ResponseStatus.INVALID_ENROLLMENT) {
                                dao.deleteAsyncRegistration(asyncRegistration.getId());
                                LogUtil.d(
                                        "AsyncRegistrationQueueRunner: async trigger "
                                                + "registration will not be queued for retry. "
                                                + "Fetch Status : "
                                                + asyncFetchStatus.getStatus());
                            } else if (asyncFetchStatus.getStatus()
                                    == AsyncFetchStatus.ResponseStatus.SUCCESS) {
                                LogUtil.d(
                                        "AsyncRegistrationQueueRunner: "
                                                + "async "
                                                + "trigger registration success case. "
                                                + "Fetch Status : "
                                                + asyncFetchStatus.getStatus());
                                if (resultTrigger.isPresent()) {
                                    Trigger trigger = resultTrigger.get();
                                    if (asyncRegistration.shouldProcessRedirects()) {
                                        LogUtil.d(
                                                "AsyncRegistrationQueueRunner: async trigger"
                                                    + " registration; processing redirects. Fetch"
                                                    + " Status : "
                                                        + asyncFetchStatus.getStatus());
                                        processRedirects(
                                                asyncRegistration,
                                                asyncRedirect,
                                                asyncRegistration.getWebDestination(),
                                                asyncRegistration.getOsDestination(),
                                                dao);
                                    }
                                    if (isTriggerAllowedToInsert(dao, trigger)) {
                                        dao.insertTrigger(trigger);
                                    }
                                }
                                dao.deleteAsyncRegistration(asyncRegistration.getId());
                            }
                        });
        if (transactionSuccessful
                && asyncFetchStatus.getStatus() == AsyncFetchStatus.ResponseStatus.SUCCESS) {
            notifyTriggerContentProvider();
        }
    }

    private static Integer countDistinctDestinationsPerPublisher(
            Uri publisher,
            @EventSurfaceType int publisherType,
            String enrollmentId,
            Uri destination,
            @EventSurfaceType int destinationType,
            long windowStartTime,
            long requestTime,
            IMeasurementDao dao) {

        try {
            return dao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                    publisher,
                    publisherType,
                    enrollmentId,
                    destination,
                    destinationType,
                    windowStartTime,
                    requestTime);
        } catch (DatastoreException e) {
            LogUtil.d(
                    "AsyncRegistrationQueueRunner: "
                            + " countDistinctDestinationsPerPublisher failed: "
                            + e);
            return null;
        }
    }

    private static Integer countDistinctEnrollmentsPerPublisher(
            Uri publisher,
            @EventSurfaceType int publisherType,
            Uri destination,
            String enrollmentId,
            long windowStartTime,
            long requestTime,
            IMeasurementDao dao) {

        try {
            return dao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
                    publisher,
                    publisherType,
                    destination,
                    enrollmentId,
                    windowStartTime,
                    requestTime);
        } catch (DatastoreException e) {
            LogUtil.d(
                    "AsyncRegistrationQueueRunner: "
                            + " countDistinctEnrollmentsPerPublisher failed "
                            + e);
            return null;
        }
    }

    @VisibleForTesting
    static boolean isSourceAllowedToInsert(
            Source source,
            Uri topOrigin,
            @EventSurfaceType int publisherType,
            IMeasurementDao dao,
            DebugReportApi debugReportApi) {
        long windowStartTime = source.getEventTime() - PrivacyParams.RATE_LIMIT_WINDOW_MILLISECONDS;
        Optional<Uri> publisher = getTopLevelPublisher(topOrigin, publisherType);
        if (!publisher.isPresent()) {
            LogUtil.d("insertSources: getTopLevelPublisher failed", topOrigin);
            return false;
        }
        Long numOfSourcesPerPublisher;

        try {
            numOfSourcesPerPublisher =
                    dao.getNumSourcesPerPublisher(publisher.get(), publisherType);
        } catch (DatastoreException e) {
            LogUtil.d("insertSources: getNumSourcesPerPublisher failed", topOrigin);
            return false;
        }

        if (numOfSourcesPerPublisher == null) {
            LogUtil.d("insertSources: getNumSourcesPerPublisher failed", publisher.get());
            return false;
        }

        if (numOfSourcesPerPublisher >= SystemHealthParams.getMaxSourcesPerPublisher()) {
            LogUtil.d(
                    "insertSources: Max limit of %s sources for publisher - %s reached.",
                    SystemHealthParams.getMaxSourcesPerPublisher(), publisher);
            debugReportApi.scheduleSourceStorageLimitDebugReport(
                    source, numOfSourcesPerPublisher.toString(), dao);
            return false;
        }
        if (source.getAppDestinations() != null) {
            Integer optionalAppDestinationCount =
                    countDistinctDestinationsPerPublisher(
                            publisher.get(),
                            publisherType,
                            source.getEnrollmentId(),
                            source.getAppDestinations().get(0),
                            EventSurfaceType.APP,
                            windowStartTime,
                            source.getEventTime(),
                            dao);
            if (optionalAppDestinationCount != null) {
                if (optionalAppDestinationCount >= PrivacyParams
                        .getMaxDistinctDestinationsPerPublisherXEnrollmentInActiveSource()) {
                    LogUtil.d(
                            "AsyncRegistrationQueueRunner: App destination count >= "
                                + "MaxDistinctDestinationsPerPublisherXEnrollmentInActiveSource");
                        debugReportApi.scheduleSourceDestinationLimitDebugReport(
                                source, optionalAppDestinationCount.toString(), dao);
                    return false;
                }
            } else {
                LogUtil.e(
                        "isDestinationWithinPrivacyBounds:"
                            + " dao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource"
                            + " not present. %s ::: %s ::: %s ::: %s ::: %s",
                        source.getPublisher(),
                        source.getAppDestinations().get(0),
                        source.getEnrollmentId(),
                        windowStartTime,
                        source.getEventTime());
                return false;
            }
            Integer optionalAppEnrollmentsCount =
                    countDistinctEnrollmentsPerPublisher(
                            publisher.get(),
                            publisherType,
                            source.getAppDestinations().get(0),
                            source.getEnrollmentId(),
                            windowStartTime,
                            source.getEventTime(),
                            dao);
            if (optionalAppEnrollmentsCount != null) {
                if (optionalAppEnrollmentsCount >= PrivacyParams
                        .getMaxDistinctEnrollmentsPerPublisherXDestinationInSource()) {
                    LogUtil.d(
                            "AsyncRegistrationQueueRunner: "
                                    + "App enrollment count >= "
                                    + "MaxDistinctEnrollmentsPerPublisherXDestinationInSource");
                    return false;
                }
            } else {
                LogUtil.e(
                        "isAdTechWithinPrivacyBounds: "
                                + "dao.countDistinctEnrollmentsPerPublisherXDestinationInSource"
                                + " not present"
                                + ". %s ::: %s ::: %s ::: %s ::: $s",
                        source.getPublisher(),
                        source.getAppDestinations().get(0),
                        source.getEnrollmentId(),
                        windowStartTime,
                        source.getEventTime());
                return false;
            }
        }
        if (source.getWebDestinations() != null) {
            Integer optionalDestinationCountWeb =
                    countDistinctDestinationsPerPublisher(
                            publisher.get(),
                            publisherType,
                            source.getEnrollmentId(),
                            source.getWebDestinations().get(0),
                            EventSurfaceType.WEB,
                            windowStartTime,
                            source.getEventTime(),
                            dao);
            if (optionalDestinationCountWeb != null) {
                if (optionalDestinationCountWeb >= PrivacyParams
                        .getMaxDistinctDestinationsPerPublisherXEnrollmentInActiveSource()) {
                    LogUtil.d(
                            "AsyncRegistrationQueueRunner:  Web destination count >= "
                                + "MaxDistinctDestinationsPerPublisherXEnrollmentInActiveSource");
                    debugReportApi.scheduleSourceDestinationLimitDebugReport(
                            source, optionalDestinationCountWeb.toString(), dao);
                    return false;
                }
            } else {
                LogUtil.e(
                        "isDestinationWithinPrivacyBounds:"
                            + " dao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource"
                            + " not present. %s ::: %s ::: %s ::: %s ::: %s",
                        source.getPublisher(),
                        source.getAppDestinations().get(0),
                        source.getEnrollmentId(),
                        windowStartTime,
                        source.getEventTime());
                return false;
            }
            Integer optionalWebEnrollmentsCount =
                    countDistinctEnrollmentsPerPublisher(
                            publisher.get(),
                            publisherType,
                            source.getWebDestinations().get(0),
                            source.getEnrollmentId(),
                            windowStartTime,
                            source.getEventTime(),
                            dao);

            if (optionalWebEnrollmentsCount != null) {
                if (optionalWebEnrollmentsCount >= PrivacyParams
                        .getMaxDistinctEnrollmentsPerPublisherXDestinationInSource()) {
                    LogUtil.d(
                            "AsyncRegistrationQueueRunner: "
                                    + " Web enrollment count >= "
                                    + "MaxDistinctEnrollmentsPerPublisherXDestinationInSource");
                    return false;
                }
            } else {
                LogUtil.e(
                        "isAdTechWithinPrivacyBounds: "
                                + "dao.countDistinctEnrollmentsPerPublisherXDestinationInSource"
                                + " not present"
                                + ". %s ::: %s ::: %s ::: %s ::: $s",
                        source.getPublisher(),
                        source.getAppDestinations().get(0),
                        source.getEnrollmentId(),
                        windowStartTime,
                        source.getEventTime());
                return false;
            }
        }
        return true;
    }

    @VisibleForTesting
    static boolean isTriggerAllowedToInsert(IMeasurementDao dao, Trigger trigger) {
        long triggerInsertedPerDestination;
        try {
            triggerInsertedPerDestination =
                    dao.getNumTriggersPerDestination(
                            trigger.getAttributionDestination(), trigger.getDestinationType());
        } catch (DatastoreException e) {
            LogUtil.e("Unable to fetch number of triggers currently registered per destination.");
            return false;
        }

        return triggerInsertedPerDestination < MAX_TRIGGER_REGISTERS_PER_DESTINATION;
    }

    private static AsyncRegistration createAsyncRegistrationRedirect(
            String id,
            String enrollmentId,
            Uri registrationUri,
            Uri webDestination,
            Uri osDestination,
            Uri registrant,
            Uri verifiedDestination,
            Uri topOrigin,
            AsyncRegistration.RegistrationType registrationType,
            Source.SourceType sourceType,
            long requestTime,
            long retryCount,
            long lastProcessingTime,
            @AsyncRegistration.RedirectType int redirectType,
            int redirectCount,
            boolean debugKeyAllowed) {
        return new AsyncRegistration.Builder()
                .setId(id)
                .setEnrollmentId(enrollmentId)
                .setRegistrationUri(registrationUri)
                .setWebDestination(webDestination)
                .setOsDestination(osDestination)
                .setRegistrant(registrant)
                .setVerifiedDestination(verifiedDestination)
                .setTopOrigin(topOrigin)
                .setType(registrationType.ordinal())
                .setSourceType(
                        registrationType == AsyncRegistration.RegistrationType.APP_SOURCE
                                        || registrationType
                                                == AsyncRegistration.RegistrationType.WEB_SOURCE
                                ? sourceType
                                : null)
                .setRequestTime(requestTime)
                .setRetryCount(retryCount)
                .setLastProcessingTime(lastProcessingTime)
                .setRedirectType(redirectType)
                .setRedirectCount(redirectCount)
                .setDebugKeyAllowed(debugKeyAllowed)
                .build();
    }

    private static void insertAsyncRegistrationFromTransaction(
            AsyncRegistration asyncRegistration, IMeasurementDao dao) throws DatastoreException {
        dao.insertAsyncRegistration(asyncRegistration);
    }

    @VisibleForTesting
    List<EventReport> generateFakeEventReports(Source source) {
        List<Source.FakeReport> fakeReports = source.assignAttributionModeAndGenerateFakeReports();
        return fakeReports.stream()
                .map(
                        fakeReport ->
                                new EventReport.Builder()
                                        .setSourceEventId(source.getEventId())
                                        .setReportTime(fakeReport.getReportingTime())
                                        .setTriggerData(fakeReport.getTriggerData())
                                        .setAttributionDestinations(fakeReport.getDestinations())
                                        .setEnrollmentId(source.getEnrollmentId())
                                        // The query for attribution check is from
                                        // (triggerTime - 30 days) to triggerTime and max expiry is
                                        // 30 days, so it's safe to choose triggerTime as source
                                        // event time so that it gets considered when the query is
                                        // fired for attribution rate limit check.
                                        .setTriggerTime(source.getEventTime())
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
    void insertSourceFromTransaction(Source source, IMeasurementDao dao) throws DatastoreException {
        List<EventReport> eventReports = generateFakeEventReports(source);
        if (!eventReports.isEmpty()) {
            mDebugReportApi.scheduleSourceNoisedDebugReport(source, dao);
        }
        dao.insertSource(source);
        for (EventReport report : eventReports) {
            dao.insertEventReport(report);
        }
        // We want to account for attribution if fake report generation was considered
        // based on the probability. In that case the attribution mode will be NEVER
        // (empty fake reports state) or FALSELY (non-empty fake reports).
        if (source.getAttributionMode() != Source.AttributionMode.TRUTHFULLY) {
            // Attribution rate limits for app and web destinations are counted
            // separately, so add a fake report entry for each type of destination if
            // non-null.
            if (!Objects.isNull(source.getAppDestinations())) {
                dao.insertAttribution(
                        createFakeAttributionRateLimit(source, source.getAppDestinations().get(0)));
            }

            if (!Objects.isNull(source.getWebDestinations())) {
                dao.insertAttribution(
                        createFakeAttributionRateLimit(source, source.getWebDestinations().get(0)));
            }
        }
    }

    private void processRedirects(
            AsyncRegistration asyncRegistration,
            AsyncRedirect redirectsAndType,
            Uri webDestination,
            Uri osDestination,
            IMeasurementDao dao)
            throws DatastoreException {
        for (Uri redirectUri : redirectsAndType.getRedirects()) {
            Optional<String> enrollmentData =
                    Enrollment.maybeGetEnrollmentId(redirectUri, mEnrollmentDao);
            if (enrollmentData == null || enrollmentData.isEmpty()) {
                LogUtil.d(
                        "AsyncRegistrationQueueRunner: Invalid enrollment data while "
                                + "processing redirects");
                return;
            }
            String enrollmentId = enrollmentData.get();
            insertAsyncRegistrationFromTransaction(
                    createAsyncRegistrationRedirect(
                            UUID.randomUUID().toString(),
                            enrollmentId,
                            redirectUri,
                            webDestination,
                            osDestination,
                            asyncRegistration.getRegistrant(),
                            asyncRegistration.getVerifiedDestination(),
                            asyncRegistration.getTopOrigin(),
                            asyncRegistration.getType(),
                            asyncRegistration.getSourceType(),
                            asyncRegistration.getRequestTime(),
                            /* mRetryCount */ 0,
                            System.currentTimeMillis(),
                            redirectsAndType.getRedirectType(),
                            asyncRegistration.getNextRedirectCount(),
                            asyncRegistration.getDebugKeyAllowed()),
                    dao);
        }
    }

    /**
     * {@link Attribution} generated from here will only be used for fake report attribution.
     *
     * @param source source to derive parameters from
     * @param destination destination for attribution
     * @return a fake {@link Attribution}
     */
    private Attribution createFakeAttributionRateLimit(Source source, Uri destination) {
        Optional<Uri> topLevelPublisher =
                getTopLevelPublisher(source.getPublisher(), source.getPublisherType());

        if (!topLevelPublisher.isPresent()) {
            throw new IllegalArgumentException(
                    String.format(
                            "insertAttributionRateLimit: getSourceAndDestinationTopPrivateDomains"
                                    + " failed. Publisher: %s; Attribution destination: %s",
                            source.getPublisher(), destination));
        }

        return new Attribution.Builder()
                .setSourceSite(topLevelPublisher.get().toString())
                .setSourceOrigin(source.getPublisher().toString())
                .setDestinationSite(destination.toString())
                .setDestinationOrigin(destination.toString())
                .setEnrollmentId(source.getEnrollmentId())
                .setTriggerTime(source.getEventTime())
                .setRegistrant(source.getRegistrant().toString())
                .setSourceId(source.getId())
                // Intentionally kept it as null because it's a fake attribution
                .setTriggerId(null)
                .build();
    }

    private static Optional<Uri> getTopLevelPublisher(
            Uri topOrigin, @EventSurfaceType int publisherType) {
        return publisherType == EventSurfaceType.APP
                ? Optional.of(topOrigin)
                : Web.topPrivateDomainAndScheme(topOrigin);
    }

    private Uri getPublisher(AsyncRegistration request) {
        return request.getRegistrant();
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

    private Uri getNullableUriFromDestinationsList(List<Uri> uris) {
        // Destinations are validated as having at least one in the list
        return uris != null ? uris.get(0) : null;
    }
}
