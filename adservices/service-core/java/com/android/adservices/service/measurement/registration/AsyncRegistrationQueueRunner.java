/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.measurement.registration;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Pair;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.WebAddresses;
import com.android.adservices.service.measurement.Attribution;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.KeyValueData;
import com.android.adservices.service.measurement.KeyValueData.DataType;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.attribution.TriggerContentProvider;
import com.android.adservices.service.measurement.noising.SourceNoiseHandler;
import com.android.adservices.service.measurement.reporting.DebugReportApi;
import com.android.adservices.service.measurement.util.Applications;
import com.android.adservices.service.measurement.util.BaseUriExtractor;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.internal.annotations.VisibleForTesting;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Runner for servicing queued registration requests */
public class AsyncRegistrationQueueRunner {
    /**
     * Single attribution entry is created for possibly multiple fake reports generated per source.
     * Setting a value to such attributions will help identify them that they are associated to fake
     * reports.
     */
    @VisibleForTesting static final String ATTRIBUTION_FAKE_REPORT_ID = "-1";

    private static AsyncRegistrationQueueRunner sAsyncRegistrationQueueRunner;
    private final DatastoreManager mDatastoreManager;
    private final AsyncSourceFetcher mAsyncSourceFetcher;
    private final AsyncTriggerFetcher mAsyncTriggerFetcher;
    private final ContentResolver mContentResolver;
    private final DebugReportApi mDebugReportApi;
    private final SourceNoiseHandler mSourceNoiseHandler;
    private final Flags mFlags;
    private final AdServicesLogger mLogger;
    private final Context mContext;

    private AsyncRegistrationQueueRunner(Context context) {
        mContext = context;
        mDatastoreManager = DatastoreManagerFactory.getDatastoreManager(context);
        mAsyncSourceFetcher = new AsyncSourceFetcher(context);
        mAsyncTriggerFetcher = new AsyncTriggerFetcher(context);
        mContentResolver = context.getContentResolver();
        mFlags = FlagsFactory.getFlags();
        mDebugReportApi = new DebugReportApi(context, mFlags);
        mSourceNoiseHandler = new SourceNoiseHandler(mFlags);
        mLogger = AdServicesLoggerImpl.getInstance();
    }

    @VisibleForTesting
    public AsyncRegistrationQueueRunner(
            Context context,
            ContentResolver contentResolver,
            AsyncSourceFetcher asyncSourceFetcher,
            AsyncTriggerFetcher asyncTriggerFetcher,
            DatastoreManager datastoreManager,
            DebugReportApi debugReportApi,
            SourceNoiseHandler sourceNoiseHandler,
            Flags flags) {
        this(
                context,
                contentResolver,
                asyncSourceFetcher,
                asyncTriggerFetcher,
                datastoreManager,
                debugReportApi,
                sourceNoiseHandler,
                flags,
                AdServicesLoggerImpl.getInstance());
    }

    @VisibleForTesting
    public AsyncRegistrationQueueRunner(
            Context context,
            ContentResolver contentResolver,
            AsyncSourceFetcher asyncSourceFetcher,
            AsyncTriggerFetcher asyncTriggerFetcher,
            DatastoreManager datastoreManager,
            DebugReportApi debugReportApi,
            SourceNoiseHandler sourceNoiseHandler,
            Flags flags,
            AdServicesLogger logger) {
        mContext = context;
        mAsyncSourceFetcher = asyncSourceFetcher;
        mAsyncTriggerFetcher = asyncTriggerFetcher;
        mDatastoreManager = datastoreManager;
        mContentResolver = contentResolver;
        mDebugReportApi = debugReportApi;
        mSourceNoiseHandler = sourceNoiseHandler;
        mFlags = flags;
        mLogger = logger;
    }

    enum ProcessingResult {
        THREAD_INTERRUPTED,
        SUCCESS_WITH_PENDING_RECORDS,
        SUCCESS_ALL_RECORDS_PROCESSED
    }

    enum InsertSourcePermission {
        NOT_ALLOWED(false),
        ALLOWED(true),
        ALLOWED_FIFO_SUCCESS(true);

        private final boolean mIsAllowed;

        InsertSourcePermission(boolean isAllowed) {
            mIsAllowed = isAllowed;
        }

        public boolean isAllowed() {
            return mIsAllowed;
        }
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

    /** Processes records in the AsyncRegistration Queue table. */
    public ProcessingResult runAsyncRegistrationQueueWorker() {
        int recordServiceLimit = mFlags.getMeasurementMaxRegistrationsPerJobInvocation();
        int retryLimit = mFlags.getMeasurementMaxRetriesPerRegistrationRequest();

        Set<Uri> failedOrigins = new HashSet<>();
        for (int i = 0; i < recordServiceLimit; i++) {
            // If the job service's requirements specified at runtime are no longer met, the job
            // service will interrupt this thread.  If the thread has been interrupted, it will exit
            // early.
            if (Thread.currentThread().isInterrupted()) {
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "AsyncRegistrationQueueRunner runAsyncRegistrationQueueWorker "
                                        + "thread interrupted, exiting early.");
                return ProcessingResult.THREAD_INTERRUPTED;
            }

            AsyncRegistration asyncRegistration = fetchNext(retryLimit, failedOrigins);
            if (null == asyncRegistration) {
                LoggerFactory.getMeasurementLogger()
                        .d("AsyncRegistrationQueueRunner: no async registration fetched.");
                return ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED;
            }

            processAsyncRecord(asyncRegistration, failedOrigins);
        }

        return hasPendingRecords(retryLimit, failedOrigins);
    }

    private AsyncRegistration fetchNext(int retryLimit, Set<Uri> failedOrigins) {
        return mDatastoreManager
                .runInTransactionWithResult(
                        (dao) -> dao.fetchNextQueuedAsyncRegistration(retryLimit, failedOrigins))
                .orElse(null);
    }

    private void processAsyncRecord(AsyncRegistration asyncRegistration, Set<Uri> failedOrigins) {
        if (asyncRegistration.isSourceRequest()) {
            LoggerFactory.getMeasurementLogger()
                    .d("AsyncRegistrationQueueRunner:" + " processing source");
            processSourceRegistration(asyncRegistration, failedOrigins);
        } else {
            LoggerFactory.getMeasurementLogger()
                    .d("AsyncRegistrationQueueRunner:" + " processing trigger");
            processTriggerRegistration(asyncRegistration, failedOrigins);
        }
    }

    private ProcessingResult hasPendingRecords(int retryLimit, Set<Uri> failedOrigins) {
        AsyncRegistration asyncRegistration = fetchNext(retryLimit, failedOrigins);
        if (null == asyncRegistration) {
            LoggerFactory.getMeasurementLogger()
                    .d("AsyncRegistrationQueueRunner: no more pending async records.");
            return ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED;
        } else {
            return ProcessingResult.SUCCESS_WITH_PENDING_RECORDS;
        }
    }

    private static boolean isNavigationOriginAlreadyRegisteredForRegistration(
            @NonNull Source source, IMeasurementDao dao, Flags flags) throws DatastoreException {
        if (!flags.getMeasurementEnableNavigationReportingOriginCheck()
                || source.getSourceType() != Source.SourceType.NAVIGATION) {
            return false;
        }
        return dao.countNavigationSourcesPerReportingOrigin(
                        source.getRegistrationOrigin(), source.getRegistrationId())
                > 0;
    }

    private void processSourceRegistration(
            AsyncRegistration asyncRegistration, Set<Uri> failedOrigins) {
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        long startTime = asyncRegistration.getRequestTime();
        Optional<Source> resultSource =
                mAsyncSourceFetcher.fetchSource(
                        asyncRegistration, asyncFetchStatus, asyncRedirects);
        long endTime = System.currentTimeMillis();
        asyncFetchStatus.setRegistrationDelay(endTime - startTime);

        boolean transactionResult =
                mDatastoreManager.runInTransaction(
                        (dao) -> {
                            if (asyncFetchStatus.isRequestSuccess()) {
                                if (resultSource.isPresent()) {
                                    storeSource(resultSource.get(), asyncRegistration, dao);
                                }
                                handleSuccess(
                                        asyncRegistration, asyncFetchStatus, asyncRedirects, dao);
                            } else {
                                handleFailure(
                                        asyncRegistration, asyncFetchStatus, failedOrigins, dao);
                            }
                        });

        if (!transactionResult) {
            asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.STORAGE_ERROR);
        }

        asyncFetchStatus.setRetryCount(Long.valueOf(asyncRegistration.getRetryCount()).intValue());
        FetcherUtil.emitHeaderMetrics(
                mFlags.getMaxResponseBasedRegistrationPayloadSizeBytes(),
                mLogger,
                asyncRegistration,
                asyncFetchStatus);
    }

    /** Visible only for testing. */
    @VisibleForTesting
    public void storeSource(
            Source source, AsyncRegistration asyncRegistration, IMeasurementDao dao)
            throws DatastoreException {
        Uri topOrigin =
                asyncRegistration.getType() == AsyncRegistration.RegistrationType.WEB_SOURCE
                        ? asyncRegistration.getTopOrigin()
                        : getPublisher(asyncRegistration);
        @EventSurfaceType
        int publisherType =
                asyncRegistration.getType() == AsyncRegistration.RegistrationType.WEB_SOURCE
                        ? EventSurfaceType.WEB
                        : EventSurfaceType.APP;
        // TODO(b/336403550) : Refactor isSourceAllowedToInsert out of this class
        InsertSourcePermission sourceAllowedToInsert =
                isSourceAllowedToInsert(source, topOrigin, publisherType, dao, mDebugReportApi);
        if (sourceAllowedToInsert.isAllowed()) {
            // If preinstall check is enabled and any app destinations are already installed,
            // mark the source for deletion. Note the source is persisted so that the fake event
            // report generated can be cleaned up after the source is deleted by
            // DeleteExpiredJobService.
            if (mFlags.getMeasurementEnablePreinstallCheck()
                    && source.shouldDropSourceIfInstalled()
                    && Applications.anyAppsInstalled(mContext, source.getAppDestinations())) {
                source.setStatus(Source.Status.MARKED_TO_DELETE);
            }
            Map<String, String> additionalDebugReportParams = null;
            if (mFlags.getMeasurementEnableSourceDestinationLimitPriority()
                    && InsertSourcePermission.ALLOWED_FIFO_SUCCESS.equals(sourceAllowedToInsert)) {
                int limit = mFlags.getMeasurementMaxDistinctDestinationsInActiveSource();
                additionalDebugReportParams =
                        Map.of(DebugReportApi.Body.SOURCE_DESTINATION_LIMIT, String.valueOf(limit));
            }
            insertSourceFromTransaction(source, dao, additionalDebugReportParams);
            mDebugReportApi.scheduleSourceSuccessDebugReport(
                    source, dao, additionalDebugReportParams);
        }
    }

    private void processTriggerRegistration(
            AsyncRegistration asyncRegistration, Set<Uri> failedOrigins) {
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        long startTime = asyncRegistration.getRequestTime();
        Optional<Trigger> resultTrigger =
                mAsyncTriggerFetcher.fetchTrigger(
                        asyncRegistration, asyncFetchStatus, asyncRedirects);
        long endTime = System.currentTimeMillis();
        asyncFetchStatus.setRegistrationDelay(endTime - startTime);

        boolean transactionResult =
                mDatastoreManager.runInTransaction(
                        (dao) -> {
                            if (asyncFetchStatus.isRequestSuccess()) {
                                if (resultTrigger.isPresent()) {
                                    storeTrigger(resultTrigger.get(), dao);
                                }
                                handleSuccess(
                                        asyncRegistration, asyncFetchStatus, asyncRedirects, dao);
                            } else {
                                handleFailure(
                                        asyncRegistration, asyncFetchStatus, failedOrigins, dao);
                            }
                        });

        if (!transactionResult) {
            asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.STORAGE_ERROR);
        }

        asyncFetchStatus.setRetryCount(Long.valueOf(asyncRegistration.getRetryCount()).intValue());
        long headerSizeLimitBytes =
                mFlags.getMeasurementEnableUpdateTriggerHeaderLimit()
                        ? mFlags.getMaxTriggerRegistrationHeaderSizeBytes()
                        : mFlags.getMaxResponseBasedRegistrationPayloadSizeBytes();
        FetcherUtil.emitHeaderMetrics(
                headerSizeLimitBytes, mLogger, asyncRegistration, asyncFetchStatus);
    }

    /** Visible only for testing. */
    @VisibleForTesting
    public void storeTrigger(Trigger trigger, IMeasurementDao dao) throws DatastoreException {
        if (isTriggerAllowedToInsert(dao, trigger)) {
            try {
                dao.insertTrigger(trigger);
            } catch (DatastoreException e) {
                mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                        trigger, dao, DebugReportApi.Type.TRIGGER_UNKNOWN_ERROR);
                LoggerFactory.getMeasurementLogger()
                        .e(e, "Insert trigger to DB error, generate trigger-unknown-error report");
                throw new DatastoreException(
                        "Insert trigger to DB error, generate trigger-unknown-error report");
            }
            notifyTriggerContentProvider();
        }
    }

    /** Visible only for testing. */
    @VisibleForTesting
    InsertSourcePermission isSourceAllowedToInsert(
            Source source,
            Uri topOrigin,
            @EventSurfaceType int publisherType,
            IMeasurementDao dao,
            DebugReportApi debugReportApi)
            throws DatastoreException {
        Flags flags = FlagsFactory.getFlags();
        // Do not persist the navigation source if the same reporting origin has been registered
        // for the registration.
        if (isNavigationOriginAlreadyRegisteredForRegistration(source, dao, flags)) {
            return InsertSourcePermission.NOT_ALLOWED;
        }
        long windowStartTime =
                source.getEventTime() - flags.getMeasurementRateLimitWindowMilliseconds();
        Optional<Uri> publisher = getTopLevelPublisher(topOrigin, publisherType);
        if (publisher.isEmpty()) {
            LoggerFactory.getMeasurementLogger()
                    .d("insertSources: getTopLevelPublisher failed, topOrigin: %s", topOrigin);
            return InsertSourcePermission.NOT_ALLOWED;
        }
        if (flags.getMeasurementEnableDestinationRateLimit()) {
            if (source.getAppDestinations() != null
                    && !sourceIsWithinTimeBasedDestinationLimits(
                            debugReportApi,
                            source,
                            publisher.get(),
                            publisherType,
                            source.getEnrollmentId(),
                            source.getAppDestinations(),
                            EventSurfaceType.APP,
                            source.getEventTime(),
                            dao)) {
                return InsertSourcePermission.NOT_ALLOWED;
            }

            if (source.getWebDestinations() != null
                    && !sourceIsWithinTimeBasedDestinationLimits(
                            debugReportApi,
                            source,
                            publisher.get(),
                            publisherType,
                            source.getEnrollmentId(),
                            source.getWebDestinations(),
                            EventSurfaceType.WEB,
                            source.getEventTime(),
                            dao)) {
                return InsertSourcePermission.NOT_ALLOWED;
            }
        }
        long numOfSourcesPerPublisher =
                dao.getNumSourcesPerPublisher(
                        BaseUriExtractor.getBaseUri(topOrigin), publisherType);
        if (numOfSourcesPerPublisher >= flags.getMeasurementMaxSourcesPerPublisher()) {
            LoggerFactory.getMeasurementLogger().d(
                    "insertSources: Max limit of %s sources for publisher - %s reached.",
                    flags.getMeasurementMaxSourcesPerPublisher(), publisher);
            debugReportApi.scheduleSourceStorageLimitDebugReport(
                    source, String.valueOf(numOfSourcesPerPublisher), dao);
            return InsertSourcePermission.NOT_ALLOWED;
        }
        if (source.getAppDestinations() != null
                && !isDestinationWithinBounds(
                        debugReportApi,
                        source,
                        publisher.get(),
                        publisherType,
                        source.getEnrollmentId(),
                        source.getAppDestinations(),
                        EventSurfaceType.APP,
                        windowStartTime,
                        source.getEventTime(),
                        dao)) {
            return InsertSourcePermission.NOT_ALLOWED;
        }

        if (source.getWebDestinations() != null
                && !isDestinationWithinBounds(
                        debugReportApi,
                        source,
                        publisher.get(),
                        publisherType,
                        source.getEnrollmentId(),
                        source.getWebDestinations(),
                        EventSurfaceType.WEB,
                        windowStartTime,
                        source.getEventTime(),
                        dao)) {
            return InsertSourcePermission.NOT_ALLOWED;
        }
        int numOfOriginExcludingRegistrationOrigin =
                dao.countSourcesPerPublisherXEnrollmentExcludingRegOrigin(
                        source.getRegistrationOrigin(),
                        publisher.get(),
                        publisherType,
                        source.getEnrollmentId(),
                        source.getEventTime(),
                        flags.getMeasurementMinReportingOriginUpdateWindow());
        if (numOfOriginExcludingRegistrationOrigin
                >= flags.getMeasurementMaxReportingOriginsPerSourceReportingSitePerWindow()) {
            debugReportApi.scheduleSourceSuccessDebugReport(source, dao, null);
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "insertSources: Max limit of 1 reporting origin for publisher - %s and"
                                    + " enrollment - %s reached.",
                            publisher, source.getEnrollmentId());
            return InsertSourcePermission.NOT_ALLOWED;
        }
        try {
            if (!source.validateAndSetNumReportStates(flags)
                    || !source.validateAndSetMaxEventStates(flags)
                    || !source.hasValidInformationGain(flags)) {
                debugReportApi.scheduleSourceFlexibleEventReportApiDebugReport(source, dao);
                return InsertSourcePermission.NOT_ALLOWED;
            }
        } catch (ArithmeticException e) {
            LoggerFactory.getMeasurementLogger()
                    .e(e, "Calculating the number of report states overflowed.");
            debugReportApi.scheduleSourceFlexibleEventReportApiDebugReport(source, dao);
            return InsertSourcePermission.NOT_ALLOWED;
        }

        // Should be deprecated once destination priority is fully launched
        if (extractSourceDestinationLimitingAlgo(flags, source)
                == Source.DestinationLimitAlgorithm.FIFO) {
            InsertSourcePermission appDestSourceAllowedToInsert =
                    deleteLowPriorityDestinationSourcesToAccommodateNewSource(
                            source,
                            publisherType,
                            dao,
                            flags,
                            publisher.get(),
                            EventSurfaceType.APP,
                            source.getAppDestinations());
            if (appDestSourceAllowedToInsert == InsertSourcePermission.NOT_ALLOWED) {
                // Return early without checking web destinations
                return InsertSourcePermission.NOT_ALLOWED;
            }
            InsertSourcePermission webDestSourceAllowedToInsert =
                    deleteLowPriorityDestinationSourcesToAccommodateNewSource(
                            source,
                            publisherType,
                            dao,
                            flags,
                            publisher.get(),
                            EventSurfaceType.WEB,
                            source.getWebDestinations());
            if (webDestSourceAllowedToInsert == InsertSourcePermission.NOT_ALLOWED) {
                return InsertSourcePermission.NOT_ALLOWED;
            }

            if (appDestSourceAllowedToInsert == InsertSourcePermission.ALLOWED_FIFO_SUCCESS
                    || webDestSourceAllowedToInsert
                            == InsertSourcePermission.ALLOWED_FIFO_SUCCESS) {
                return InsertSourcePermission.ALLOWED_FIFO_SUCCESS;
            }
        }
        return InsertSourcePermission.ALLOWED;
    }

    private static InsertSourcePermission deleteLowPriorityDestinationSourcesToAccommodateNewSource(
            Source source,
            @EventSurfaceType int publisherType,
            IMeasurementDao dao,
            Flags flags,
            Uri publisher,
            @EventSurfaceType int destinationType,
            List<Uri> destinations)
            throws DatastoreException {
        if (destinations == null || destinations.isEmpty()) {
            return InsertSourcePermission.ALLOWED;
        }
        int fifoLimit = flags.getMeasurementMaxDistinctDestinationsInActiveSource();
        if (destinations.size() > fifoLimit) {
            return InsertSourcePermission.NOT_ALLOWED;
        }
        int distinctDestinations =
                dao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        publisher,
                        publisherType,
                        source.getEnrollmentId(),
                        destinations,
                        destinationType,
                        source.getEventTime());
        if (distinctDestinations + destinations.size() <= fifoLimit) {
            // Source is allowed to be inserted without any deletion
            return InsertSourcePermission.ALLOWED;
        }

        // Delete sources associated to the oldest destination per enrollment per publisher.
        // The new source may have multiple app and web destination, because of which we might
        // need to delete multiple oldest destinations - in FIFO manner, i.e. in a loop.
        // Although it should not be more than 4 iterations because the new source can have
        // at max 1 app destination and 3 web destinations (configurable).
        while (distinctDestinations + destinations.size() > fifoLimit) {
            // Delete sources for the lowest priority / oldest destination
            Pair<Long, List<String>> destinationPriorityWithSourcesToDelete =
                    dao.fetchSourceIdsForLowestPriorityDestinationXEnrollmentXPublisher(
                            publisher,
                            publisherType,
                            source.getEnrollmentId(),
                            destinations,
                            destinationType,
                            source.getEventTime());
            if (source.getDestinationLimitPriority()
                    < destinationPriorityWithSourcesToDelete.first) {
                // If the incoming source has a lower priority than the least prioritized
                // destination, reject the incoming source.
                return InsertSourcePermission.NOT_ALLOWED;
            }

            List<String> sourceIdsToDelete = destinationPriorityWithSourcesToDelete.second;
            if (sourceIdsToDelete.isEmpty()) {
                // If destination limit exceeds, the oldest destination deletion should be
                // successful. This is an unexpected state.
                throw new IllegalStateException(
                        "No sources were deleted; incoming destinations: "
                                + destinations.size()
                                + "; FIFO limit:"
                                + fifoLimit);
            }
            dao.updateSourceStatus(sourceIdsToDelete, Source.Status.MARKED_TO_DELETE);
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "Deleted "
                                    + sourceIdsToDelete.size()
                                    + " sources to insert the new source.");
            if (flags.getMeasurementEnableFifoDestinationsDeleteAggregateReports()) {
                dao.deletePendingAggregateReportsAndAttributionsForSources(sourceIdsToDelete);
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "Deleted pending aggregate reports of"
                                        + sourceIdsToDelete.size()
                                        + " sources to insert the new source.");
            }
            dao.deleteFutureFakeEventReportsForSources(sourceIdsToDelete, source.getEventTime());
            distinctDestinations =
                    dao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                            publisher,
                            publisherType,
                            source.getEnrollmentId(),
                            destinations,
                            destinationType,
                            source.getEventTime());
        }
        return InsertSourcePermission.ALLOWED_FIFO_SUCCESS;
    }

    private static boolean sourceIsWithinTimeBasedDestinationLimits(
            DebugReportApi debugReportApi,
            Source source,
            Uri publisher,
            @EventSurfaceType int publisherType,
            String enrollmentId,
            List<Uri> destinations,
            @EventSurfaceType int destinationType,
            long requestTime,
            IMeasurementDao dao)
            throws DatastoreException {
        long windowStartTime = source.getEventTime()
                - FlagsFactory.getFlags().getMeasurementDestinationRateLimitWindow();
        int destinationReportingCount =
                dao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        publisher,
                        publisherType,
                        enrollmentId,
                        destinations,
                        destinationType,
                        windowStartTime,
                        requestTime);
        // Same reporting-site destination limit
        int maxDistinctReportingDestinations =
                FlagsFactory.getFlags()
                        .getMeasurementMaxDestPerPublisherXEnrollmentPerRateLimitWindow();
        boolean hitSameReportingRateLimit =
                destinationReportingCount + destinations.size() > maxDistinctReportingDestinations;
        if (hitSameReportingRateLimit) {
            LoggerFactory.getMeasurementLogger().d(
                    "AsyncRegistrationQueueRunner: "
                            + (destinationType == EventSurfaceType.APP ? "App" : "Web")
                            + " MaxDestPerPublisherXEnrollmentPerRateLimitWindow exceeded");
        }

        // TODO(b/336628903): Move this check at the end of other checks to not leak cross site data
        // Global destination limit
        int destinationCount =
                dao.countDistinctDestinationsPerPublisherPerRateLimitWindow(
                        publisher,
                        publisherType,
                        destinations,
                        destinationType,
                        windowStartTime,
                        requestTime);
        int maxDistinctDestinations =
                FlagsFactory.getFlags()
                        .getMeasurementMaxDestinationsPerPublisherPerRateLimitWindow();
        boolean hitRateLimit = destinationCount + destinations.size() > maxDistinctDestinations;
        if (hitRateLimit) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "AsyncRegistrationQueueRunner: "
                                    + (destinationType == EventSurfaceType.APP ? "App" : "Web")
                                    + " MaxDestinationsPerPublisherPerRateLimitWindow exceeded");
        }

        if (hitSameReportingRateLimit) {
            debugReportApi.scheduleSourceDestinationRateLimitDebugReport(
                    source, String.valueOf(maxDistinctReportingDestinations), dao);
            return false;
        } else if (hitRateLimit) {
            debugReportApi.scheduleSourceSuccessDebugReport(source, dao, null);
            return false;
        }
        return true;
    }

    private static boolean isDestinationWithinBounds(
            DebugReportApi debugReportApi,
            Source source,
            Uri publisher,
            @EventSurfaceType int publisherType,
            String enrollmentId,
            List<Uri> destinations,
            @EventSurfaceType int destinationType,
            long windowStartTime,
            long requestTime,
            IMeasurementDao dao)
            throws DatastoreException {
        Flags flags = FlagsFactory.getFlags();

        // If the source has destination algorithm overridden as LIFO, the source is rejected if the
        // destination rate limit is exceeded.
        if (extractSourceDestinationLimitingAlgo(flags, source)
                == Source.DestinationLimitAlgorithm.LIFO) {
            int destinationCount;
            if (flags.getMeasurementEnableDestinationRateLimit()) {
                destinationCount =
                        dao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                                publisher,
                                publisherType,
                                enrollmentId,
                                destinations,
                                destinationType,
                                requestTime);
            } else {
                destinationCount =
                        dao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                                publisher,
                                publisherType,
                                enrollmentId,
                                destinations,
                                destinationType,
                                windowStartTime,
                                requestTime);
            }
            int maxDistinctDestinations =
                    flags.getMeasurementMaxDistinctDestinationsInActiveSource();
            if (destinationCount + destinations.size() > maxDistinctDestinations) {
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "AsyncRegistrationQueueRunner: "
                                        + (destinationType == EventSurfaceType.APP ? "App" : "Web")
                                        + " destination count >= MaxDistinctDestinations"
                                        + "PerPublisherXEnrollmentInActiveSource");
                debugReportApi.scheduleSourceDestinationLimitDebugReport(
                        source, String.valueOf(maxDistinctDestinations), dao);
                return false;
            }
        }

        int distinctReportingOriginCount =
                dao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        publisher,
                        publisherType,
                        destinations,
                        source.getRegistrationOrigin(),
                        windowStartTime,
                        requestTime);
        if (distinctReportingOriginCount
                >= flags.getMeasurementMaxDistinctRepOrigPerPublXDestInSource()) {
            debugReportApi.scheduleSourceSuccessDebugReport(source, dao, null);
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "AsyncRegistrationQueueRunner: "
                                    + (destinationType == EventSurfaceType.APP ? "App" : "Web")
                                    + " distinct reporting origin count >= "
                                    + "MaxDistinctRepOrigPerPublisherXDestInSource exceeded");
            return false;
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
            LoggerFactory.getMeasurementLogger()
                    .e("Unable to fetch number of triggers currently registered per destination.");
            return false;
        }
        return triggerInsertedPerDestination
                < FlagsFactory.getFlags().getMeasurementMaxTriggersPerDestination();
    }

    private AsyncRegistration createAsyncRegistrationFromRedirect(
            AsyncRegistration asyncRegistration, AsyncRedirect asyncRedirect) {
        return new AsyncRegistration.Builder()
                .setId(UUID.randomUUID().toString())
                .setRegistrationUri(asyncRedirect.getUri())
                .setWebDestination(asyncRegistration.getWebDestination())
                .setOsDestination(asyncRegistration.getOsDestination())
                .setRegistrant(asyncRegistration.getRegistrant())
                .setVerifiedDestination(asyncRegistration.getVerifiedDestination())
                .setTopOrigin(asyncRegistration.getTopOrigin())
                .setType(asyncRegistration.getType())
                .setSourceType(asyncRegistration.getSourceType())
                .setRequestTime(asyncRegistration.getRequestTime())
                .setRetryCount(0)
                .setDebugKeyAllowed(asyncRegistration.getDebugKeyAllowed())
                .setAdIdPermission(asyncRegistration.hasAdIdPermission())
                .setRegistrationId(asyncRegistration.getRegistrationId())
                .setRedirectBehavior(asyncRedirect.getRedirectBehavior())
                .build();
    }

    private List<EventReport> generateFakeEventReports(
            String sourceId, Source source, List<Source.FakeReport> fakeReports) {
        return fakeReports.stream()
                .map(
                        fakeReport ->
                                new EventReport.Builder()
                                        .setId(UUID.randomUUID().toString())
                                        .setSourceId(sourceId)
                                        .setSourceEventId(source.getEventId())
                                        .setReportTime(fakeReport.getReportingTime())
                                        .setTriggerData(fakeReport.getTriggerData())
                                        .setAttributionDestinations(fakeReport.getDestinations())
                                        .setEnrollmentId(source.getEnrollmentId())
                                        .setTriggerTime(fakeReport.getTriggerTime())
                                        .setTriggerPriority(0L)
                                        .setTriggerDedupKey(null)
                                        .setSourceType(source.getSourceType())
                                        .setStatus(EventReport.Status.PENDING)
                                        .setRandomizedTriggerRate(
                                                mSourceNoiseHandler.getRandomizedTriggerRate(
                                                        source))
                                        .setRegistrationOrigin(source.getRegistrationOrigin())
                                        .setTriggerSummaryBucket(
                                                fakeReport.getTriggerSummaryBucket())
                                        .setSourceDebugKey(getSourceDebugKeyForNoisedReport(source))
                                        .build())
                .collect(Collectors.toList());
    }

    @VisibleForTesting
    void insertSourceFromTransaction(
            Source source, IMeasurementDao dao, Map<String, String> additionalDebugReportParams)
            throws DatastoreException {
        List<Source.FakeReport> fakeReports =
                mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(source);

        final String sourceId = insertSource(source, dao);
        if (sourceId == null) {
            // Source was not saved due to DB size restrictions
            return;
        }

        if (mFlags.getMeasurementEnableAttributionScope()) {
            dao.updateSourcesForAttributionScope(source);
        }

        if (fakeReports != null) {
            mDebugReportApi.scheduleSourceNoisedDebugReport(
                    source, dao, additionalDebugReportParams);
            for (EventReport report : generateFakeEventReports(sourceId, source, fakeReports)) {
                dao.insertEventReport(report);
            }
        }
        // We want to account for attribution if fake report generation was considered
        // based on the probability. In that case the attribution mode will be NEVER
        // (empty fake reports state) or FALSELY (non-empty fake reports).
        if (source.getAttributionMode() != Source.AttributionMode.TRUTHFULLY) {
            // Attribution rate limits for app and web destinations are counted
            // separately, so add a fake report entry for each type of destination if
            // non-null.
            if (!Objects.isNull(source.getAppDestinations())) {
                for (Uri destination : source.getAppDestinations()) {
                    dao.insertAttribution(
                            createFakeAttributionRateLimit(sourceId, source, destination));
                }
            }

            if (!Objects.isNull(source.getWebDestinations())) {
                for (Uri destination : source.getWebDestinations()) {
                    dao.insertAttribution(
                            createFakeAttributionRateLimit(sourceId, source, destination));
                }
            }
        }
    }

    /**
     * Returns the effective source destination limiting algorithm. Return if the source has
     * overridden the algorithm, otherwise fallback to the configured default destination algorithm.
     *
     * @param flags flags
     * @param source incoming source
     * @return the effective source destination limiting algorithm
     */
    private static Source.DestinationLimitAlgorithm extractSourceDestinationLimitingAlgo(
            Flags flags, Source source) {
        return Optional.ofNullable(source.getDestinationLimitAlgorithm())
                .orElse(
                        Source.DestinationLimitAlgorithm.values()[
                                flags.getMeasurementDefaultSourceDestinationLimitAlgorithm()]);
    }

    private String insertSource(Source source, IMeasurementDao dao) throws DatastoreException {
        try {
            return dao.insertSource(source);
        } catch (DatastoreException e) {
            mDebugReportApi.scheduleSourceUnknownErrorDebugReport(source, dao);
            LoggerFactory.getMeasurementLogger()
                    .e(e, "Insert source to DB error, generate source-unknown-error report");
            throw new DatastoreException(
                    "Insert source to DB error, generate source-unknown-error report");
        }
    }

    private void handleSuccess(
            AsyncRegistration asyncRegistration,
            AsyncFetchStatus asyncFetchStatus,
            AsyncRedirects asyncRedirects,
            IMeasurementDao dao)
            throws DatastoreException {
        // deleteAsyncRegistration will throw an exception & rollback the transaction if the record
        // is already deleted. This can happen if both fallback & regular job are running at the
        // same time or if deletion job deletes the records.
        dao.deleteAsyncRegistration(asyncRegistration.getId());
        if (asyncRedirects.getRedirects().isEmpty()) {
            return;
        }
        int maxRedirects = FlagsFactory.getFlags().getMeasurementMaxRegistrationRedirects();
        KeyValueData keyValueData =
                dao.getKeyValueData(
                        asyncRegistration.getRegistrationId(),
                        DataType.REGISTRATION_REDIRECT_COUNT);
        int currentCount = keyValueData.getRegistrationRedirectCount();
        if (currentCount >= maxRedirects) {
            asyncFetchStatus.setRedirectError(true);
            return;
        }

        for (AsyncRedirect asyncRedirect : asyncRedirects.getRedirects()) {
            if (currentCount >= maxRedirects) {
                break;
            }
            dao.insertAsyncRegistration(
                    createAsyncRegistrationFromRedirect(asyncRegistration, asyncRedirect));
            currentCount++;
        }
        keyValueData.setRegistrationRedirectCount(currentCount);
        dao.insertOrUpdateKeyValueData(keyValueData);
    }

    private void handleFailure(
            AsyncRegistration asyncRegistration,
            AsyncFetchStatus asyncFetchStatus,
            Set<Uri> failedOrigins,
            IMeasurementDao dao)
            throws DatastoreException {
        if (asyncFetchStatus.canRetry()) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "AsyncRegistrationQueueRunner: "
                                    + "async "
                                    + asyncRegistration.getType()
                                    + " registration will be queued for retry "
                                    + "Fetch Status : "
                                    + asyncFetchStatus.getResponseStatus());
            failedOrigins.add(BaseUriExtractor.getBaseUri(asyncRegistration.getRegistrationUri()));
            asyncRegistration.incrementRetryCount();
            dao.updateRetryCount(asyncRegistration);
        } else {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "AsyncRegistrationQueueRunner: "
                                    + "async "
                                    + asyncRegistration.getType()
                                    + " registration will not be queued for retry. "
                                    + "Fetch Status : "
                                    + asyncFetchStatus.getResponseStatus());
            dao.deleteAsyncRegistration(asyncRegistration.getId());
        }
    }

    /**
     * {@link Attribution} generated from here will only be used for fake report attribution.
     *
     * @param source source to derive parameters from
     * @param destination destination for attribution
     * @return a fake {@link Attribution}
     */
    private Attribution createFakeAttributionRateLimit(
            String sourceId, Source source, Uri destination) {
        Optional<Uri> topLevelPublisher =
                getTopLevelPublisher(source.getPublisher(), source.getPublisherType());

        if (topLevelPublisher.isEmpty()) {
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
                .setSourceId(sourceId)
                // Intentionally kept it as null because it's a fake attribution
                .setTriggerId(null)
                // Intentionally using source here since trigger is not available
                .setRegistrationOrigin(source.getRegistrationOrigin())
                .setReportId(ATTRIBUTION_FAKE_REPORT_ID)
                .build();
    }

    private static Optional<Uri> getTopLevelPublisher(
            Uri topOrigin, @EventSurfaceType int publisherType) {
        return publisherType == EventSurfaceType.APP
                ? Optional.of(topOrigin)
                : WebAddresses.topPrivateDomainAndScheme(topOrigin);
    }

    private Uri getPublisher(AsyncRegistration request) {
        return request.getRegistrant();
    }

    private void notifyTriggerContentProvider() {
        try (ContentProviderClient contentProviderClient =
                mContentResolver.acquireContentProviderClient(TriggerContentProvider.TRIGGER_URI)) {
            if (contentProviderClient != null) {
                contentProviderClient.insert(TriggerContentProvider.TRIGGER_URI, null);
            }
        } catch (RemoteException e) {
            LoggerFactory.getMeasurementLogger()
                    .e(e, "Trigger Content Provider invocation failed.");
        }
    }

    @Nullable
    private UnsignedLong getSourceDebugKeyForNoisedReport(@NonNull Source source) {
        if ((source.getPublisherType() == EventSurfaceType.APP && source.hasAdIdPermission())
                || (source.getPublisherType() == EventSurfaceType.WEB
                        && source.hasArDebugPermission())) {
            return source.getDebugKey();
        }
        return null;
    }
}
