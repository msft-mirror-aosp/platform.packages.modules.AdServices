/*
 * Copyright (C) 2025 The Android Open Source Project
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
import android.net.Uri;
import android.util.Pair;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.WebAddresses;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.reporting.DebugReportApi;
import com.android.adservices.service.measurement.util.BaseUriExtractor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Validation for inserting source to database */
public class SourceEligibilityChecker {
    private final Flags mFlags;
    private final DebugReportApi mDebugReportApi;

    public SourceEligibilityChecker(Flags flags, DebugReportApi debugReportApi) {
        mFlags = flags;
        mDebugReportApi = debugReportApi;
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
     * Determines the permission for inserting a source into the database.
     *
     * @param source incoming {@link Source}
     * @param topOrigin a {@link Uri}
     * @param publisherType represents an event surface type
     * @param dao a {@link IMeasurementDao}
     * @param asyncFetchStatus a {@link AsyncFetchStatus}, stores Ad Tech server status
     * @param adrTypes a set of types for aggregate debug reporting
     * @return a {@link InsertSourcePermission}, indicating whether the source can be inserted
     */
    public InsertSourcePermission isAllowedToInsert(
            Source source,
            Uri topOrigin,
            @EventSurfaceType int publisherType,
            IMeasurementDao dao,
            AsyncFetchStatus asyncFetchStatus,
            Set<DebugReportApi.Type> adrTypes)
            throws DatastoreException {
        // Do not persist the navigation source if the same reporting origin has been registered
        // for the registration.
        if (isNavigationOriginAlreadyRegisteredForRegistration(source, dao)) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "storeSource (FAILURE): Duplicate reporting origin in registration"
                                + " sequence. Enrollment ID: %s, Source ID: %s, Source Event ID:"
                                + " %s",
                            source.getEnrollmentId(), source.getId(), source.getEventId());
            return InsertSourcePermission.NOT_ALLOWED;
        }
        long windowStartTime =
                source.getEventTime() - mFlags.getMeasurementRateLimitWindowMilliseconds();
        Optional<Uri> publisher = getTopLevelPublisher(topOrigin, publisherType);
        if (publisher.isEmpty()) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "storeSource (FAILURE): getTopLevelPublisher failed. topOrigin: %s,"
                                    + " Enrollment ID: %s, Source ID: %s, Source Event ID: %s",
                            topOrigin,
                            source.getEnrollmentId(),
                            source.getId(),
                            source.getEventId());
            return InsertSourcePermission.NOT_ALLOWED;
        }
        long numOfSourcesPerPublisher =
                dao.getNumSourcesPerPublisher(
                        BaseUriExtractor.getBaseUri(topOrigin), publisherType);
        if (numOfSourcesPerPublisher >= mFlags.getMeasurementMaxSourcesPerPublisher()) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "storeSource (FAILURE): Reached limit of %s sources for publisher - %s."
                                    + " Enrollment ID: %s, Source ID: %s, Source Event ID: %s",
                            mFlags.getMeasurementMaxSourcesPerPublisher(),
                            publisher,
                            source.getEnrollmentId(),
                            source.getId(),
                            source.getEventId());
            mDebugReportApi.scheduleSourceReport(
                    source,
                    DebugReportApi.Type.SOURCE_STORAGE_LIMIT,
                    Map.of(DebugReportApi.Body.LIMIT, String.valueOf(numOfSourcesPerPublisher)),
                    dao);
            adrTypes.add(DebugReportApi.Type.SOURCE_STORAGE_LIMIT);
            return InsertSourcePermission.NOT_ALLOWED;
        }

        // Blocks ad-techs to register multiple sources with various destinations in a short window
        // (per minute)
        int destinationsPerMinuteRateLimit =
                mFlags.getMeasurementMaxDestPerPublisherXEnrollmentPerRateLimitWindow();
        if (mFlags.getMeasurementEnableDestinationRateLimit()
                && sourceExceedsTimeBasedDestinationLimits(
                        source,
                        publisher.get(),
                        publisherType,
                        mFlags.getMeasurementDestinationRateLimitWindow(),
                        destinationsPerMinuteRateLimit,
                        dao)) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "storeSource (FAILURE): Exceeded limit of %s destinations per minute."
                                    + " Enrollment ID: %s, Source ID: %s, Source Event ID: %s",
                            destinationsPerMinuteRateLimit,
                            source.getEnrollmentId(),
                            source.getId(),
                            source.getEventId());
            mDebugReportApi.scheduleSourceDestinationPerMinuteRateLimitDebugReport(
                    source, String.valueOf(destinationsPerMinuteRateLimit), dao);
            adrTypes.add(DebugReportApi.Type.SOURCE_DESTINATION_RATE_LIMIT);
            return InsertSourcePermission.NOT_ALLOWED;
        }

        // Global (cross reporting-origin) destinations rate limit. This needs to be recorded before
        // FIFO based deletion as it's a LIFO based rate limit. Although reject the source if it
        // fails only if every other (enrollment based) rate limit passes to not reveal cross site
        // data.
        boolean destinationExceedsGlobalRateLimit =
                destinationExceedsGlobalRateLimit(source, publisher.get(), dao);

        // Blocks ad-techs to reconstruct browser history by registering multiple sources with
        // various destinations in a medium window (per day). The larger window is 30 days.
        int destinationsPerDayRateLimit = mFlags.getMeasurementDestinationPerDayRateLimit();
        if (mFlags.getMeasurementEnableDestinationPerDayRateLimitWindow()
                && sourceExceedsTimeBasedDestinationLimits(
                        source,
                        publisher.get(),
                        publisherType,
                        mFlags.getMeasurementDestinationPerDayRateLimitWindowInMs(),
                        destinationsPerDayRateLimit,
                        dao)) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "storeSource (FAILURE): Exceeded limit of %s destinations per"
                                    + " day. Enrollment ID: %s, Source ID: %s, Source Event ID: %s",
                            destinationsPerDayRateLimit,
                            source.getEnrollmentId(),
                            source.getId(),
                            source.getEventId());
            mDebugReportApi.scheduleSourceDestinationPerDayRateLimitDebugReport(
                    source, String.valueOf(destinationsPerDayRateLimit), dao);
            adrTypes.add(DebugReportApi.Type.SOURCE_DESTINATION_PER_DAY_RATE_LIMIT);
            return InsertSourcePermission.NOT_ALLOWED;
        }

        if (source.getAppDestinations() != null
                && isDestinationOutOfBounds(
                        source,
                        publisher.get(),
                        publisherType,
                        source.getEnrollmentId(),
                        source.getAppDestinations(),
                        EventSurfaceType.APP,
                        windowStartTime,
                        source.getEventTime(),
                        dao,
                        adrTypes)) {
            return InsertSourcePermission.NOT_ALLOWED;
        }

        if (source.getWebDestinations() != null
                && isDestinationOutOfBounds(
                        source,
                        publisher.get(),
                        publisherType,
                        source.getEnrollmentId(),
                        source.getWebDestinations(),
                        EventSurfaceType.WEB,
                        windowStartTime,
                        source.getEventTime(),
                        dao,
                        adrTypes)) {
            return InsertSourcePermission.NOT_ALLOWED;
        }

        Map<String, Object> additionalDebugReportParams = null;
        InsertSourcePermission result = InsertSourcePermission.ALLOWED;
        // Should be deprecated once destination priority is fully launched
        if (extractSourceDestinationLimitingAlgo(mFlags, source)
                == Source.DestinationLimitAlgorithm.FIFO) {
            InsertSourcePermission appDestSourceAllowedToInsert =
                    deleteLowPriorityDestinationSourcesToAccommodateNewSource(
                            source,
                            publisherType,
                            dao,
                            publisher.get(),
                            EventSurfaceType.APP,
                            source.getAppDestinations(),
                            asyncFetchStatus);
            if (appDestSourceAllowedToInsert == InsertSourcePermission.NOT_ALLOWED) {
                // Return early without checking web destinations
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "storeSource (FAILURE): Cannot make space for app destination in"
                                        + " source. Enrollment ID: %s, Source ID: %s, Source"
                                        + " Event ID: %s",
                                source.getEnrollmentId(), source.getId(), source.getEventId());
                mDebugReportApi.scheduleSourceDestinationLimitDebugReport(
                        source,
                        String.valueOf(
                                mFlags.getMeasurementMaxDistinctDestinationsInActiveSource()),
                        dao);
                adrTypes.add(DebugReportApi.Type.SOURCE_DESTINATION_LIMIT);
                return InsertSourcePermission.NOT_ALLOWED;
            }
            InsertSourcePermission webDestSourceAllowedToInsert =
                    deleteLowPriorityDestinationSourcesToAccommodateNewSource(
                            source,
                            publisherType,
                            dao,
                            publisher.get(),
                            EventSurfaceType.WEB,
                            source.getWebDestinations(),
                            asyncFetchStatus);
            if (webDestSourceAllowedToInsert == InsertSourcePermission.NOT_ALLOWED) {
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "storeSource (FAILURE): Cannot make space for web destinations in"
                                        + " source. Enrollment ID: %s, Source ID: %s, Source"
                                        + " Event ID: %s",
                                source.getEnrollmentId(), source.getId(), source.getEventId());
                mDebugReportApi.scheduleSourceDestinationLimitDebugReport(
                        source,
                        String.valueOf(
                                mFlags.getMeasurementMaxDistinctDestinationsInActiveSource()),
                        dao);
                adrTypes.add(DebugReportApi.Type.SOURCE_DESTINATION_LIMIT);
                return InsertSourcePermission.NOT_ALLOWED;
            }

            if (appDestSourceAllowedToInsert == InsertSourcePermission.ALLOWED_FIFO_SUCCESS
                    || webDestSourceAllowedToInsert
                            == InsertSourcePermission.ALLOWED_FIFO_SUCCESS) {
                int limit = mFlags.getMeasurementMaxDistinctDestinationsInActiveSource();
                additionalDebugReportParams =
                        Map.of(DebugReportApi.Body.SOURCE_DESTINATION_LIMIT, String.valueOf(limit));
                result = InsertSourcePermission.ALLOWED_FIFO_SUCCESS;
                adrTypes.add(DebugReportApi.Type.SOURCE_DESTINATION_LIMIT_REPLACED);
            }
        }

        // Global (cross ad-tech) destinations rate limit
        if (destinationExceedsGlobalRateLimit) {
            // Source won't be inserted, yet we produce a success to debug report to avoid side
            // channel leakage of cross site data
            mDebugReportApi.scheduleSourceReport(
                    source,
                    source.getAttributionMode() != Source.AttributionMode.TRUTHFULLY
                            ? DebugReportApi.Type.SOURCE_NOISED
                            : DebugReportApi.Type.SOURCE_SUCCESS,
                    additionalDebugReportParams,
                    dao);
            adrTypes.add(DebugReportApi.Type.SOURCE_DESTINATION_GLOBAL_RATE_LIMIT);
            return InsertSourcePermission.NOT_ALLOWED;
        }

        int numOfDistinctOriginExcludingRegistrationOrigin =
                dao.countDistinctRegOriginPerPublisherXEnrollmentExclRegOrigin(
                        source.getRegistrationOrigin(),
                        publisher.get(),
                        publisherType,
                        source.getEnrollmentId(),
                        source.getEventTime(),
                        mFlags.getMeasurementMinReportingOriginUpdateWindow());
        if (numOfDistinctOriginExcludingRegistrationOrigin
                >= mFlags.getMeasurementMaxReportingOriginsPerSourceReportingSitePerWindow()) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "storeSource (FAILURE): Reached limit of %s reporting origin for"
                                    + " publisher - %s and enrollment - %s per window."
                                    + " Source ID: %s, Source Event ID: %s",
                            mFlags
                                .getMeasurementMaxReportingOriginsPerSourceReportingSitePerWindow(),
                            publisher,
                            source.getEnrollmentId(),
                            source.getId(),
                            source.getEventId());
            mDebugReportApi.scheduleSourceReport(
                    source,
                    source.getAttributionMode() != Source.AttributionMode.TRUTHFULLY
                            ? DebugReportApi.Type.SOURCE_NOISED
                            : DebugReportApi.Type.SOURCE_SUCCESS,
                    additionalDebugReportParams,
                    dao);
            adrTypes.add(DebugReportApi.Type.SOURCE_REPORTING_ORIGIN_PER_SITE_LIMIT);
            return InsertSourcePermission.NOT_ALLOWED;
        }

        LoggerFactory.getMeasurementLogger()
                .d(
                        "storeSource: Source allowed to be inserted. Enrollment ID: %s, "
                                + "Source ID: %s, Source Event ID: %s",
                        source.getEnrollmentId(), source.getId(), source.getEventId());
        return result;
    }

    private boolean isNavigationOriginAlreadyRegisteredForRegistration(
            @NonNull Source source, IMeasurementDao dao) throws DatastoreException {
        if (!mFlags.getMeasurementEnableNavigationReportingOriginCheck()
                || source.getSourceType() != Source.SourceType.NAVIGATION) {
            return false;
        }
        return dao.countNavigationSourcesPerReportingOrigin(
                        source.getRegistrationOrigin(), source.getRegistrationId())
                > 0;
    }

    private static Optional<Uri> getTopLevelPublisher(
            Uri topOrigin, @EventSurfaceType int publisherType) {
        return publisherType == EventSurfaceType.APP
                ? Optional.of(topOrigin)
                : WebAddresses.topPrivateDomainAndScheme(topOrigin);
    }

    private static boolean sourceExceedsTimeBasedDestinationLimits(
            Source source,
            Uri publisher,
            @EventSurfaceType int publisherType,
            long window,
            int limit,
            IMeasurementDao dao)
            throws DatastoreException {
        List<Uri> appDestinations = source.getAppDestinations();
        if (appDestinations != null) {
            int appDestinationReportingCount =
                    dao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                            publisher,
                            publisherType,
                            source.getEnrollmentId(),
                            appDestinations,
                            EventSurfaceType.APP,
                            /* window start time */ source.getEventTime() - window,
                            /*window end time*/ source.getEventTime());
            // Same reporting-site destination limit
            if (appDestinationReportingCount + appDestinations.size() > limit) {
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "AsyncRegistrationQueueRunner: App time based destination limit"
                                        + " exceeded");
                return true;
            }
        }

        List<Uri> webDestinations = source.getWebDestinations();
        if (webDestinations != null) {
            int webDestinationReportingCount =
                    dao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                            publisher,
                            publisherType,
                            source.getEnrollmentId(),
                            webDestinations,
                            EventSurfaceType.WEB,
                            /* window start time */ source.getEventTime() - window,
                            /*window end time*/ source.getEventTime());

            // Same reporting-site destination limit
            if (webDestinationReportingCount + webDestinations.size() > limit) {
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "AsyncRegistrationQueueRunner: Web time based destination limit"
                                        + " exceeded");
                return true;
            }
        }

        return false;
    }

    private boolean destinationExceedsGlobalRateLimit(
            Source source, Uri publisher, IMeasurementDao dao) throws DatastoreException {
        long window = mFlags.getMeasurementDestinationRateLimitWindow();
        long limit = mFlags.getMeasurementMaxDestinationsPerPublisherPerRateLimitWindow();
        long windowStartTime = source.getEventTime() - window;
        List<Uri> appDestinations = source.getAppDestinations();
        if (appDestinations != null) {
            int destinationCount =
                    dao.countDistinctDestinationsPerPublisherPerRateLimitWindow(
                            publisher,
                            source.getPublisherType(),
                            /* excluded destinations */ appDestinations,
                            EventSurfaceType.APP,
                            windowStartTime,
                            /* windowEndTime */ source.getEventTime());

            if (destinationCount + appDestinations.size() > limit) {
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "AsyncRegistrationQueueRunner: App destination global rate limit "
                                        + "exceeded");
                return true;
            }
        }

        List<Uri> webDestinations = source.getWebDestinations();
        if (webDestinations != null) {
            int destinationCount =
                    dao.countDistinctDestinationsPerPublisherPerRateLimitWindow(
                            publisher,
                            source.getPublisherType(),
                            /* excluded destinations */ webDestinations,
                            EventSurfaceType.WEB,
                            windowStartTime,
                            /* windowEndTime */ source.getEventTime());

            if (destinationCount + webDestinations.size() > limit) {
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "AsyncRegistrationQueueRunner: Web destination global rate limit "
                                        + "exceeded");
                return true;
            }
        }

        return false;
    }

    private boolean isDestinationOutOfBounds(
            Source source,
            Uri publisher,
            @EventSurfaceType int publisherType,
            String enrollmentId,
            List<Uri> destinations,
            @EventSurfaceType int destinationType,
            long windowStartTime,
            long requestTime,
            IMeasurementDao dao,
            Set<DebugReportApi.Type> adrTypes)
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
                                        + " destination count >="
                                        + " MaxDistinctDestinationsPerPublisherXEnrollmentInActive"
                                        + "Source. Enrollment ID: "
                                        + source.getEnrollmentId()
                                        + ", Source ID: "
                                        + source.getId()
                                        + ", Source Event ID: "
                                        + source.getEventId());
                mDebugReportApi.scheduleSourceDestinationLimitDebugReport(
                        source, String.valueOf(maxDistinctDestinations), dao);
                adrTypes.add(DebugReportApi.Type.SOURCE_DESTINATION_LIMIT);
                return true;
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
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "AsyncRegistrationQueueRunner: "
                                    + (destinationType == EventSurfaceType.APP ? "App" : "Web")
                                    + " distinct reporting origin count >= "
                                    + "MaxDistinctRepOrigPerPublisherXDestInSource exceeded."
                                    + " Enrollment ID: "
                                    + source.getEnrollmentId()
                                    + ", Source ID: "
                                    + source.getId()
                                    + ", Source Event ID: "
                                    + source.getEventId());
            mDebugReportApi.scheduleSourceReport(
                    source,
                    source.getAttributionMode() != Source.AttributionMode.TRUTHFULLY
                            ? DebugReportApi.Type.SOURCE_NOISED
                            : DebugReportApi.Type.SOURCE_SUCCESS,
                    null,
                    dao);
            adrTypes.add(DebugReportApi.Type.SOURCE_REPORTING_ORIGIN_LIMIT);
            return true;
        }
        return false;
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

    private InsertSourcePermission deleteLowPriorityDestinationSourcesToAccommodateNewSource(
            Source source,
            @EventSurfaceType int publisherType,
            IMeasurementDao dao,
            Uri publisher,
            @EventSurfaceType int destinationType,
            List<Uri> destinations,
            AsyncFetchStatus asyncFetchStatus)
            throws DatastoreException {
        if (destinations == null || destinations.isEmpty()) {
            return InsertSourcePermission.ALLOWED;
        }
        int fifoLimit = mFlags.getMeasurementMaxDistinctDestinationsInActiveSource();
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
            if (mFlags.getMeasurementEnableFifoDestinationsDeleteAggregateReports()) {
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
            asyncFetchStatus.incrementNumDeletedEntities(sourceIdsToDelete.size());
        }
        return InsertSourcePermission.ALLOWED_FIFO_SUCCESS;
    }
}
