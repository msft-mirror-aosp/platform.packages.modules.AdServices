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

package com.android.adservices.service.measurement.attribution;

import static com.android.adservices.service.measurement.PrivacyParams.AGGREGATE_MAX_REPORT_DELAY;
import static com.android.adservices.service.measurement.PrivacyParams.AGGREGATE_MIN_REPORT_DELAY;

import android.annotation.NonNull;
import android.net.Uri;
import android.util.Pair;

import com.android.adservices.LogUtil;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.Attribution;
import com.android.adservices.service.measurement.AttributionConfig;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.EventTrigger;
import com.android.adservices.service.measurement.FilterMap;
import com.android.adservices.service.measurement.PrivacyParams;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SystemHealthParams;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.aggregation.AggregatableAttributionSource;
import com.android.adservices.service.measurement.aggregation.AggregatableAttributionTrigger;
import com.android.adservices.service.measurement.aggregation.AggregateAttributionData;
import com.android.adservices.service.measurement.aggregation.AggregateDeduplicationKey;
import com.android.adservices.service.measurement.aggregation.AggregateHistogramContribution;
import com.android.adservices.service.measurement.aggregation.AggregatePayloadGenerator;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.reporting.DebugKeyAccessor;
import com.android.adservices.service.measurement.util.BaseUriExtractor;
import com.android.adservices.service.measurement.util.Debug;
import com.android.adservices.service.measurement.util.Filter;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.measurement.util.Web;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

class AttributionJobHandler {

    private static final String API_VERSION = "0.1";
    private final DatastoreManager mDatastoreManager;

    private final Flags mFlags;

    AttributionJobHandler(DatastoreManager datastoreManager) {
        mDatastoreManager = datastoreManager;
        mFlags = FlagsFactory.getFlags();
    }

    AttributionJobHandler(DatastoreManager datastoreManager, Flags flags) {
        mDatastoreManager = datastoreManager;
        mFlags = flags;
    }

    /**
     * Perform attribution by finding relevant {@link Source} and generates {@link EventReport}.
     *
     * @return false if there are datastore failures or pending {@link Trigger} left, true otherwise
     */
    synchronized boolean performPendingAttributions() {
        Optional<List<String>> pendingTriggersOpt = mDatastoreManager
                .runInTransactionWithResult(IMeasurementDao::getPendingTriggerIds);
        if (!pendingTriggersOpt.isPresent()) {
            // Failure during trigger retrieval
            // Reschedule for retry
            return false;
        }
        List<String> pendingTriggers = pendingTriggersOpt.get();

        for (int i = 0; i < pendingTriggers.size()
                && i < SystemHealthParams.MAX_ATTRIBUTIONS_PER_INVOCATION; i++) {
            boolean success = performAttribution(pendingTriggers.get(i));
            if (!success) {
                // Failure during trigger attribution
                // Reschedule for retry
                return false;
            }
        }

        // Reschedule if there are unprocessed pending triggers.
        return SystemHealthParams.MAX_ATTRIBUTIONS_PER_INVOCATION >= pendingTriggers.size();
    }

    /**
     * Perform attribution for {@code triggerId}.
     *
     * @param triggerId datastore id of the {@link Trigger}
     * @return success
     */
    private boolean performAttribution(String triggerId) {
        return mDatastoreManager.runInTransaction(
                measurementDao -> {
                    Trigger trigger = measurementDao.getTrigger(triggerId);
                    if (trigger.getStatus() != Trigger.Status.PENDING) {
                        return;
                    }
                    Optional<Source> sourceOpt = selectSourceToAttribute(trigger, measurementDao);
                    if (sourceOpt.isEmpty()) {
                        ignoreTrigger(trigger, measurementDao);
                        return;
                    }
                    Source source = sourceOpt.get();

                    if (!doTopLevelFiltersMatch(source, trigger)) {
                        ignoreTrigger(trigger, measurementDao);
                        return;
                    }

                    if (!hasAttributionQuota(source, trigger, measurementDao)
                            || !isEnrollmentWithinPrivacyBounds(source, trigger, measurementDao)) {
                        ignoreTrigger(trigger, measurementDao);
                        return;
                    }

                    boolean aggregateReportGenerated =
                            maybeGenerateAggregateReport(source, trigger, measurementDao);

                    boolean eventReportGenerated =
                            maybeGenerateEventReport(source, trigger, measurementDao);

                    if (eventReportGenerated || aggregateReportGenerated) {
                        attributeTriggerAndInsertAttribution(trigger, source, measurementDao);
                    } else {
                        ignoreTrigger(trigger, measurementDao);
                    }
                });
    }

    private boolean maybeGenerateAggregateReport(Source source, Trigger trigger,
            IMeasurementDao measurementDao) throws DatastoreException {

        if (trigger.getTriggerTime() > source.getAggregatableReportWindow()) {
            return false;
        }

        int numReports =
                measurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType());

        if (numReports >= SystemHealthParams.getMaxAggregateReportsPerDestination()) {
            LogUtil.d(
                    String.format(
                            Locale.ENGLISH,
                            "Aggregate reports for destination %1$s exceeds system health limit of"
                                    + " %2$d.",
                            trigger.getAttributionDestination(),
                            SystemHealthParams.getMaxAggregateReportsPerDestination()));
            return false;
        }

        try {
            Optional<AggregateDeduplicationKey> aggregateDeduplicationKey =
                    maybeGetAggregateDeduplicationKey(source, trigger);
            if (aggregateDeduplicationKey.isPresent()
                    && source.getAggregateReportDedupKeys()
                            .contains(aggregateDeduplicationKey.get().getDeduplicationKey())) {
                return false;
            }
            Optional<List<AggregateHistogramContribution>> contributions =
                    AggregatePayloadGenerator.generateAttributionReport(source, trigger);
            if (!contributions.isPresent()) {
                return false;
            }
            OptionalInt newAggregateContributions =
                    validateAndGetUpdatedAggregateContributions(contributions.get(), source);
            if (!newAggregateContributions.isPresent()) {
                LogUtil.d(
                        "Aggregate contributions exceeded bound. Source ID: %s ; "
                                + "Trigger ID: %s ",
                        source.getId(), trigger.getId());
                return false;
            }

            source.setAggregateContributions(newAggregateContributions.getAsInt());
            long randomTime =
                    (long)
                            ((Math.random()
                                            * (AGGREGATE_MAX_REPORT_DELAY
                                                    - AGGREGATE_MIN_REPORT_DELAY))
                                    + AGGREGATE_MIN_REPORT_DELAY);
            Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                    new DebugKeyAccessor().getDebugKeys(source, trigger);
            UnsignedLong sourceDebugKey = debugKeyPair.first;
            UnsignedLong triggerDebugKey = debugKeyPair.second;

            int debugReportStatus = AggregateReport.DebugReportStatus.NONE;
            if (Debug.isAttributionDebugReportPermitted(source, trigger, sourceDebugKey,
                      triggerDebugKey)) {
                debugReportStatus = AggregateReport.DebugReportStatus.PENDING;
            }
            AggregateReport aggregateReport =
                    new AggregateReport.Builder()
                            // TODO: b/254855494 unused field, incorrect value; cleanup
                            .setPublisher(source.getRegistrant())
                            .setAttributionDestination(trigger.getAttributionDestinationBaseUri())
                            .setSourceRegistrationTime(roundDownToDay(source.getEventTime()))
                            .setScheduledReportTime(trigger.getTriggerTime() + randomTime)
                            .setEnrollmentId(trigger.getEnrollmentId())
                            .setDebugCleartextPayload(
                                    AggregateReport.generateDebugPayload(contributions.get()))
                            .setAggregateAttributionData(
                                    new AggregateAttributionData.Builder()
                                            .setContributions(contributions.get())
                                            .build())
                            .setStatus(AggregateReport.Status.PENDING)
                            .setDebugReportStatus(debugReportStatus)
                            .setApiVersion(API_VERSION)
                            .setSourceDebugKey(sourceDebugKey)
                            .setTriggerDebugKey(triggerDebugKey)
                            .setSourceId(source.getId())
                            .setTriggerId(trigger.getId())
                            .build();

            finalizeAggregateReportCreation(
                    source, aggregateDeduplicationKey, aggregateReport, measurementDao);
            // TODO (b/230618328): read from DB and upload unencrypted aggregate report.
            return true;
        } catch (JSONException e) {
            LogUtil.e(e, "JSONException when parse aggregate fields in AttributionJobHandler.");
            return false;
        }
    }

    private Optional<Source> selectSourceToAttribute(
            Trigger trigger, IMeasurementDao measurementDao) throws DatastoreException {
        List<Source> matchingSources;
        if (!mFlags.getMeasurementEnableXNA() || trigger.getAttributionConfig() == null) {
            matchingSources = measurementDao.getMatchingActiveSources(trigger);
        } else {
            // XNA attribution is possible
            Set<String> enrollmentIds = extractEnrollmentIds(trigger.getAttributionConfig());
            List<Source> allSources =
                    measurementDao.fetchTriggerMatchingSourcesForXna(trigger, enrollmentIds);
            List<Source> triggerEnrollmentMatchingSources = new ArrayList<>();
            List<Source> otherEnrollmentBasedSources = new ArrayList<>();
            for (Source source : allSources) {
                if (Objects.equals(source.getEnrollmentId(), trigger.getEnrollmentId())) {
                    triggerEnrollmentMatchingSources.add(source);
                } else {
                    otherEnrollmentBasedSources.add(source);
                }
            }
            List<Source> derivedSources =
                    new XnaSourceCreator()
                            .generateDerivedSources(trigger, otherEnrollmentBasedSources);
            matchingSources = new ArrayList<>();
            matchingSources.addAll(triggerEnrollmentMatchingSources);
            matchingSources.addAll(derivedSources);
        }

        if (matchingSources.isEmpty()) {
            return Optional.empty();
        }

        // Sort based on isInstallAttributed, Priority and Event Time.
        // Is a valid install-attributed source.
        Function<Source, Boolean> installAttributionComparator =
                (Source source) ->
                        source.isInstallAttributed()
                                && isWithinInstallCooldownWindow(source, trigger);
        matchingSources.sort(
                Comparator.comparing(installAttributionComparator, Comparator.reverseOrder())
                        .thenComparing(Source::getPriority, Comparator.reverseOrder())
                        .thenComparing(Source::getEventTime, Comparator.reverseOrder()));

        Source selectedSource = matchingSources.remove(0);

        // Ignore all sources not selected for attribution
        ignoreRemainingSources(measurementDao, matchingSources, trigger.getEnrollmentId());
        return Optional.of(selectedSource);
    }

    private Set<String> extractEnrollmentIds(String attributionConfigsString) {
        Set<String> enrollmentIds = new HashSet<>();
        try {
            JSONArray attributionConfigsJsonArray = new JSONArray(attributionConfigsString);
            for (int i = 0; i < attributionConfigsJsonArray.length(); i++) {
                JSONObject attributionConfigJson = attributionConfigsJsonArray.getJSONObject(i);
                // It can't be null, has already been validated at fetcher
                enrollmentIds.add(
                        attributionConfigJson.getString(
                                AttributionConfig.AttributionConfigContract.SOURCE_NETWORK));
            }
        } catch (JSONException e) {
            LogUtil.d(e, "Failed to parse attribution configs.");
        }
        return enrollmentIds;
    }

    private Optional<AggregateDeduplicationKey> maybeGetAggregateDeduplicationKey(
            Source source, Trigger trigger) {
        try {
            Optional<AggregateDeduplicationKey> dedupKey;
            Optional<AggregatableAttributionSource> optionalAggregateAttributionSource =
                    source.getAggregatableAttributionSource();
            Optional<AggregatableAttributionTrigger> optionalAggregateAttributionTrigger =
                    trigger.getAggregatableAttributionTrigger();
            if (!optionalAggregateAttributionSource.isPresent()
                    || !optionalAggregateAttributionTrigger.isPresent()) {
                return Optional.empty();
            }
            AggregatableAttributionSource aggregateAttributionSource =
                    optionalAggregateAttributionSource.get();
            AggregatableAttributionTrigger aggregateAttributionTrigger =
                    optionalAggregateAttributionTrigger.get();
            dedupKey =
                    aggregateAttributionTrigger.maybeExtractDedupKey(
                            aggregateAttributionSource.getFilterMap());
            return dedupKey;
        } catch (JSONException e) {
            LogUtil.e(
                    e,
                    "JSONException when parse aggregate dedup key fields in "
                            + "AttributionJobHandler.");
            return Optional.empty();
        }
    }

    private void ignoreRemainingSources(
            IMeasurementDao measurementDao,
            List<Source> remainingSources,
            String triggerEnrollmentId)
            throws DatastoreException {
        if (!remainingSources.isEmpty()) {
            List<String> ignoredOriginalSourceIds = new ArrayList<>();
            for (Source source : remainingSources) {
                source.setStatus(Source.Status.IGNORED);

                if (source.getParentId() == null) {
                    // Original source
                    ignoredOriginalSourceIds.add(source.getId());
                } else {
                    // Derived source (XNA)
                    measurementDao.insertIgnoredSourceForEnrollment(
                            source.getParentId(), triggerEnrollmentId);
                }
            }

            measurementDao.updateSourceStatus(ignoredOriginalSourceIds, Source.Status.IGNORED);
        }
    }

    private boolean maybeGenerateEventReport(
            Source source, Trigger trigger, IMeasurementDao measurementDao)
            throws DatastoreException {
        if (source.getParentId() != null) {
            LogUtil.d("Event report generation skipped because it's a derived source.");
            return false;
        }

        if (trigger.getTriggerTime() > source.getEventReportWindow()) {
            return false;
        }

        int numReports =
                measurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType());

        if (numReports >= SystemHealthParams.getMaxEventReportsPerDestination()) {
            LogUtil.d(
                    String.format(
                            Locale.ENGLISH,
                            "Event reports for destination %1$s exceeds system health limit of"
                                    + " %2$d.",
                            trigger.getAttributionDestination(),
                            SystemHealthParams.getMaxEventReportsPerDestination()));
            return false;
        }

        // Do not generate event reports for source which have attributionMode != Truthfully.
        // TODO: Handle attribution rate limit consideration for non-truthful cases.
        if (source.getAttributionMode() != Source.AttributionMode.TRUTHFULLY) {
            return false;
        }

        Optional<EventTrigger> matchingEventTrigger =
                findFirstMatchingEventTrigger(source, trigger);
        if (!matchingEventTrigger.isPresent()) {
            return false;
        }

        EventTrigger eventTrigger = matchingEventTrigger.get();
        // Check if deduplication key clashes with existing reports.
        if (eventTrigger.getDedupKey() != null
                && source.getEventReportDedupKeys().contains(eventTrigger.getDedupKey())) {
            return false;
        }

        Pair<List<Uri>, List<Uri>> destinations =
                measurementDao.getSourceDestinations(source.getId());
        source.setAppDestinations(destinations.first);
        source.setWebDestinations(destinations.second);

        EventReport newEventReport =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(source, trigger, eventTrigger)
                        .build();

        if (!provisionEventReportQuota(source, trigger, newEventReport, measurementDao)) {
            return false;
        }

        finalizeEventReportCreation(source, eventTrigger, newEventReport, measurementDao);
        return true;
    }

    private boolean provisionEventReportQuota(Source source, Trigger trigger,
            EventReport newEventReport, IMeasurementDao measurementDao)
            throws DatastoreException {
        List<EventReport> sourceEventReports = measurementDao.getSourceEventReports(source);

        if (isWithinReportLimit(source, sourceEventReports.size(), trigger.getDestinationType())) {
            return true;
        }

        List<EventReport> relevantEventReports =
                sourceEventReports.stream()
                        .filter((r) -> r.getStatus() == EventReport.Status.PENDING)
                        .filter((r) -> r.getReportTime() == newEventReport.getReportTime())
                        .sorted(
                                Comparator.comparingLong(EventReport::getTriggerPriority)
                                        .thenComparing(
                                                EventReport::getTriggerTime,
                                                Comparator.reverseOrder()))
                        .collect(Collectors.toList());

        if (relevantEventReports.isEmpty()) {
            return false;
        }

        EventReport lowestPriorityEventReport = relevantEventReports.get(0);
        if (lowestPriorityEventReport.getTriggerPriority()
                >= newEventReport.getTriggerPriority()) {
            return false;
        }

        if (lowestPriorityEventReport.getTriggerDedupKey() != null) {
            source.getEventReportDedupKeys().remove(lowestPriorityEventReport.getTriggerDedupKey());
        }
        measurementDao.deleteEventReport(lowestPriorityEventReport);
        return true;
    }

    private void finalizeEventReportCreation(
            Source source,
            EventTrigger eventTrigger,
            EventReport eventReport,
            IMeasurementDao measurementDao)
            throws DatastoreException {
        if (eventTrigger.getDedupKey() != null) {
            source.getEventReportDedupKeys().add(eventTrigger.getDedupKey());
        }
        measurementDao.updateSourceEventReportDedupKeys(source);

        measurementDao.insertEventReport(eventReport);
    }

    private void finalizeAggregateReportCreation(
            Source source,
            Optional<AggregateDeduplicationKey> aggregateDeduplicationKey,
            AggregateReport aggregateReport,
            IMeasurementDao measurementDao)
            throws DatastoreException {
        if (aggregateDeduplicationKey.isPresent()) {
            source.getAggregateReportDedupKeys()
                    .add(aggregateDeduplicationKey.get().getDeduplicationKey());
        }

        if (source.getParentId() == null) {
            // Only update aggregate contributions for an original source, not for a derived
            // source
            measurementDao.updateSourceAggregateContributions(source);
            measurementDao.updateSourceAggregateReportDedupKeys(source);
        }
        measurementDao.insertAggregateReport(aggregateReport);
    }

    private void attributeTriggerAndInsertAttribution(Trigger trigger, Source source,
            IMeasurementDao measurementDao)
            throws DatastoreException {
        trigger.setStatus(Trigger.Status.ATTRIBUTED);
        measurementDao.updateTriggerStatus(
                Collections.singletonList(trigger.getId()), Trigger.Status.ATTRIBUTED);
        measurementDao.insertAttribution(createAttribution(source, trigger));
    }

    private void ignoreTrigger(Trigger trigger, IMeasurementDao measurementDao)
            throws DatastoreException {
        trigger.setStatus(Trigger.Status.IGNORED);
        measurementDao.updateTriggerStatus(
                Collections.singletonList(trigger.getId()), Trigger.Status.IGNORED);
    }

    private boolean hasAttributionQuota(Source source, Trigger trigger,
            IMeasurementDao measurementDao) throws DatastoreException {
        long attributionCount =
                measurementDao.getAttributionsPerRateLimitWindow(source, trigger);
        return attributionCount < PrivacyParams.getMaxAttributionPerRateLimitWindow();
    }

    private boolean isWithinReportLimit(
            Source source, int existingReportCount, @EventSurfaceType int destinationType) {
        return source.getMaxReportCount(destinationType) > existingReportCount;
    }

    private boolean isWithinInstallCooldownWindow(Source source, Trigger trigger) {
        return trigger.getTriggerTime()
                < (source.getEventTime() + source.getInstallCooldownWindow());
    }

    /**
     * The logic works as following - 1. If source OR trigger filters are empty, we call it a match
     * since there is no restriction. 2. If source and trigger filters have no common keys, it's a
     * match. 3. All common keys between source and trigger filters should have intersection between
     * their list of values.
     *
     * @return true for a match, false otherwise
     */
    private boolean doTopLevelFiltersMatch(@NonNull Source source, @NonNull Trigger trigger) {
        try {
            FilterMap sourceFilters = source.getFilterData();
            List<FilterMap> triggerFilterSet = extractFilterSet(trigger.getFilters());
            List<FilterMap> triggerNotFilterSet = extractFilterSet(trigger.getNotFilters());
            return Filter.isFilterMatch(sourceFilters, triggerFilterSet, true)
                    && Filter.isFilterMatch(sourceFilters, triggerNotFilterSet, false);
        } catch (JSONException e) {
            // If JSON is malformed, we shall consider as not matched.
            LogUtil.e(e, "doTopLevelFiltersMatch: JSON parse failed.");
            return false;
        }
    }

    private Optional<EventTrigger> findFirstMatchingEventTrigger(Source source, Trigger trigger) {
        try {
            FilterMap sourceFiltersData = source.getFilterData();
            List<EventTrigger> eventTriggers = trigger.parseEventTriggers();
            return eventTriggers.stream()
                    .filter(
                            eventTrigger ->
                                    doEventLevelFiltersMatch(sourceFiltersData, eventTrigger))
                    .findFirst();
        } catch (JSONException e) {
            // If JSON is malformed, we shall consider as not matched.
            LogUtil.e(e, "Malformed JSON string.");
            return Optional.empty();
        }
    }

    private boolean doEventLevelFiltersMatch(
            FilterMap sourceFiltersData, EventTrigger eventTrigger) {
        if (eventTrigger.getFilterSet().isPresent()
                && !Filter.isFilterMatch(
                        sourceFiltersData, eventTrigger.getFilterSet().get(), true)) {
            return false;
        }

        if (eventTrigger.getNotFilterSet().isPresent()
                && !Filter.isFilterMatch(
                        sourceFiltersData, eventTrigger.getNotFilterSet().get(), false)) {
            return false;
        }

        return true;
    }

    private static List<FilterMap> extractFilterSet(String str) throws JSONException {
        String json = (str == null || str.isEmpty()) ? "[]" : str;
        List<FilterMap> filterSet = new ArrayList<>();
        JSONArray filters = new JSONArray(json);
        for (int i = 0; i < filters.length(); i++) {
            FilterMap filterMap =
                    new FilterMap.Builder()
                            .buildFilterData(filters.getJSONObject(i))
                            .build();
            filterSet.add(filterMap);
        }
        return filterSet;
    }

    private static OptionalInt validateAndGetUpdatedAggregateContributions(
            List<AggregateHistogramContribution> contributions, Source source) {
        int newAggregateContributions = source.getAggregateContributions();
        for (AggregateHistogramContribution contribution : contributions) {
            try {
                newAggregateContributions =
                        Math.addExact(newAggregateContributions, contribution.getValue());
                if (newAggregateContributions
                        > PrivacyParams.MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE) {
                    return OptionalInt.empty();
                }
            } catch (ArithmeticException e) {
                LogUtil.e(e, "Error adding aggregate contribution values.");
                return OptionalInt.empty();
            }
        }
        return OptionalInt.of(newAggregateContributions);
    }

    private static long roundDownToDay(long timestamp) {
        return Math.floorDiv(timestamp, TimeUnit.DAYS.toMillis(1)) * TimeUnit.DAYS.toMillis(1);
    }

    private static boolean isEnrollmentWithinPrivacyBounds(Source source, Trigger trigger,
            IMeasurementDao measurementDao) throws DatastoreException {
        Optional<Pair<Uri, Uri>> publisherAndDestination =
                getPublisherAndDestinationTopPrivateDomains(source, trigger);
        if (publisherAndDestination.isPresent()) {
            Integer count =
                    measurementDao.countDistinctEnrollmentsPerPublisherXDestinationInAttribution(
                            publisherAndDestination.get().first,
                            publisherAndDestination.get().second,
                            trigger.getEnrollmentId(),
                            trigger.getTriggerTime()
                                    - PrivacyParams.RATE_LIMIT_WINDOW_MILLISECONDS,
                            trigger.getTriggerTime());

            return count < PrivacyParams
                    .getMaxDistinctEnrollmentsPerPublisherXDestinationInAttribution();
        } else {
            LogUtil.d("isEnrollmentWithinPrivacyBounds: getPublisherAndDestinationTopPrivateDomains"
                    + " failed. %s %s", source.getPublisher(), trigger.getAttributionDestination());
            return true;
        }
    }

    private static Optional<Pair<Uri, Uri>> getPublisherAndDestinationTopPrivateDomains(
            Source source, Trigger trigger) {
        Uri attributionDestination = trigger.getAttributionDestination();
        Optional<Uri> triggerDestinationTopPrivateDomain =
                trigger.getDestinationType() == EventSurfaceType.APP
                        ? Optional.of(BaseUriExtractor.getBaseUri(attributionDestination))
                        : Web.topPrivateDomainAndScheme(attributionDestination);
        Uri publisher = source.getPublisher();
        Optional<Uri> publisherTopPrivateDomain =
                source.getPublisherType() == EventSurfaceType.APP
                ? Optional.of(publisher)
                : Web.topPrivateDomainAndScheme(publisher);
        if (!triggerDestinationTopPrivateDomain.isPresent()
                || !publisherTopPrivateDomain.isPresent()) {
            return Optional.empty();
        } else {
            return Optional.of(Pair.create(
                    publisherTopPrivateDomain.get(),
                    triggerDestinationTopPrivateDomain.get()));
        }
    }

    public Attribution createAttribution(@NonNull Source source, @NonNull Trigger trigger) {
        Optional<Uri> publisherTopPrivateDomain =
                getTopPrivateDomain(source.getPublisher(), source.getPublisherType());
        Uri destination = trigger.getAttributionDestination();
        Optional<Uri> destinationTopPrivateDomain =
                getTopPrivateDomain(destination, trigger.getDestinationType());

        if (!publisherTopPrivateDomain.isPresent()
                || !destinationTopPrivateDomain.isPresent()) {
            throw new IllegalArgumentException(
                    String.format(
                            "insertAttributionRateLimit: "
                                    + "getSourceAndDestinationTopPrivateDomains"
                                    + " failed. Publisher: %s; Attribution destination: %s",
                            source.getPublisher(), destination));
        }

        return new Attribution.Builder()
                .setSourceSite(publisherTopPrivateDomain.get().toString())
                .setSourceOrigin(source.getPublisher().toString())
                .setDestinationSite(destinationTopPrivateDomain.get().toString())
                .setDestinationOrigin(BaseUriExtractor.getBaseUri(destination).toString())
                .setEnrollmentId(trigger.getEnrollmentId())
                .setTriggerTime(trigger.getTriggerTime())
                .setRegistrant(trigger.getRegistrant().toString())
                .setSourceId(source.getId())
                .setTriggerId(trigger.getId())
                .build();
    }

    private static Optional<Uri> getTopPrivateDomain(
            Uri uri, @EventSurfaceType int eventSurfaceType) {
        return eventSurfaceType == EventSurfaceType.APP
                ? Optional.of(BaseUriExtractor.getBaseUri(uri))
                : Web.topPrivateDomainAndScheme(uri);
    }
}
