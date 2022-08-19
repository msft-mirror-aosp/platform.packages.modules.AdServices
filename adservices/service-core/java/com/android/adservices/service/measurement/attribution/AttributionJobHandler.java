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

import android.annotation.NonNull;
import android.net.Uri;
import android.util.Pair;

import com.android.adservices.LogUtil;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.measurement.Attribution;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.EventTrigger;
import com.android.adservices.service.measurement.PrivacyParams;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SystemHealthParams;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.aggregation.AggregatableAttributionSource;
import com.android.adservices.service.measurement.aggregation.AggregatableAttributionTrigger;
import com.android.adservices.service.measurement.aggregation.AggregateAttributionData;
import com.android.adservices.service.measurement.aggregation.AggregateFilterData;
import com.android.adservices.service.measurement.aggregation.AggregateHistogramContribution;
import com.android.adservices.service.measurement.aggregation.AggregatePayloadGenerator;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.util.BaseUriExtractor;
import com.android.adservices.service.measurement.util.Filter;
import com.android.adservices.service.measurement.util.Web;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

class AttributionJobHandler {

    private static final String API_VERSION = "0.1";
    private static final long MIN_TIME_MS = TimeUnit.MINUTES.toMillis(10L);
    private static final long MAX_TIME_MS = TimeUnit.MINUTES.toMillis(60L);
    private final DatastoreManager mDatastoreManager;

    AttributionJobHandler(DatastoreManager datastoreManager) {
        mDatastoreManager = datastoreManager;
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
                    Optional<Source> sourceOpt = getMatchingSource(trigger, measurementDao);
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
                            || !isAdTechWithinPrivacyBounds(source, trigger, measurementDao)) {
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
        try {
            Optional<AggregatableAttributionSource> aggregateAttributionSource =
                    source.parseAggregateSource();
            Optional<AggregatableAttributionTrigger> aggregateAttributionTrigger =
                    trigger.parseAggregateTrigger();
            if (aggregateAttributionSource.isPresent() && aggregateAttributionTrigger.isPresent()) {
                Optional<List<AggregateHistogramContribution>> contributions =
                        AggregatePayloadGenerator.generateAttributionReport(
                                aggregateAttributionSource.get(),
                                aggregateAttributionTrigger.get());
                if (contributions.isPresent()) {
                    OptionalInt newAggregateContributions =
                            validateAndGetUpdatedAggregateContributions(
                                    contributions.get(), source);
                    if (newAggregateContributions.isPresent()) {
                        source.setAggregateContributions(newAggregateContributions.getAsInt());
                    } else {
                        LogUtil.d("Aggregate contributions exceeded bound. Source ID: %s ; "
                                + "Trigger ID: %s ", source.getId(), trigger.getId());
                        return false;
                    }

                    long randomTime = (long) ((Math.random() * (MAX_TIME_MS - MIN_TIME_MS))
                            + MIN_TIME_MS);
                    AggregateReport aggregateReport =
                            new AggregateReport.Builder()
                                    .setPublisher(source.getRegistrant())
                                    .setAttributionDestination(source.getAppDestination())
                                    .setSourceRegistrationTime(
                                            roundDownToDay(source.getEventTime()))
                                    .setScheduledReportTime(trigger.getTriggerTime() + randomTime)
                                    .setAdTechDomain(source.getAdTechDomain())
                                    .setEnrollmentId(source.getEnrollmentId())
                                    .setDebugCleartextPayload(
                                            AggregateReport.generateDebugPayload(
                                                    contributions.get()))
                                    .setAggregateAttributionData(
                                            new AggregateAttributionData.Builder()
                                                    .setContributions(contributions.get())
                                                    .build())
                                    .setStatus(AggregateReport.Status.PENDING)
                                    .setApiVersion(API_VERSION)
                                    .build();

                    measurementDao.updateSourceAggregateContributions(source);
                    measurementDao.insertAggregateReport(aggregateReport);
                    // TODO (b/230618328): read from DB and upload unencrypted aggregate report.
                    return true;
                }
            }
        } catch (JSONException e) {
            LogUtil.e("JSONException when parse aggregate fields in AttributionJobHandler.");
            return false;
        }
        return false;
    }

    private Optional<Source> getMatchingSource(Trigger trigger, IMeasurementDao measurementDao)
            throws DatastoreException {
        List<Source> matchingSources = measurementDao.getMatchingActiveSources(trigger);

        if (matchingSources.isEmpty()) {
            return Optional.empty();
        }

        // Sort based on isInstallAttributed, Priority and Event Time.
        matchingSources.sort(
                Comparator.comparing(
                        (Source source) ->
                            // Is a valid install-attributed source.
                            source.isInstallAttributed()
                                    && isWithinInstallCooldownWindow(source,
                                    trigger),
                            Comparator.reverseOrder())
                        .thenComparing(Source::getPriority, Comparator.reverseOrder())
                        .thenComparing(Source::getEventTime, Comparator.reverseOrder()));

        Source selectedSource = matchingSources.get(0);
        matchingSources.remove(0);
        if (!matchingSources.isEmpty()) {
            matchingSources.forEach((s) -> s.setStatus(Source.Status.IGNORED));
            measurementDao.updateSourceStatus(matchingSources, Source.Status.IGNORED);
        }
        return Optional.of(selectedSource);
    }

    private boolean maybeGenerateEventReport(
            Source source, Trigger trigger, IMeasurementDao measurementDao)
            throws DatastoreException {
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
                && source.getDedupKeys().contains(eventTrigger.getDedupKey())) {
            return false;
        }

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

        if (isWithinReportLimit(
                source,
                sourceEventReports.size(),
                trigger.getDestinationType())) {
            return true;
        }

        List<EventReport> relevantEventReports = sourceEventReports.stream()
                .filter((r) -> r.getStatus() == EventReport.Status.PENDING)
                .filter((r) -> r.getReportTime() == newEventReport.getReportTime())
                .sorted(Comparator.comparingLong(EventReport::getTriggerPriority)
                        .thenComparing(EventReport::getTriggerTime, Comparator.reverseOrder()))
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
            source.getDedupKeys().remove(lowestPriorityEventReport.getTriggerDedupKey());
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
            source.getDedupKeys().add(eventTrigger.getDedupKey());
        }
        measurementDao.updateSourceDedupKeys(source);

        measurementDao.insertEventReport(eventReport);
    }

    private void attributeTriggerAndInsertAttribution(Trigger trigger, Source source,
            IMeasurementDao measurementDao)
            throws DatastoreException {
        trigger.setStatus(Trigger.Status.ATTRIBUTED);
        measurementDao.updateTriggerStatus(trigger);
        measurementDao.insertAttribution(createAttribution(source, trigger));
    }

    private void ignoreTrigger(Trigger trigger, IMeasurementDao measurementDao)
            throws DatastoreException {
        trigger.setStatus(Trigger.Status.IGNORED);
        measurementDao.updateTriggerStatus(trigger);
    }

    private boolean hasAttributionQuota(Source source, Trigger trigger,
            IMeasurementDao measurementDao) throws DatastoreException {
        long attributionCount =
                measurementDao.getAttributionsPerRateLimitWindow(source, trigger);
        return attributionCount < PrivacyParams.MAX_ATTRIBUTION_PER_RATE_LIMIT_WINDOW;
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
        String triggerFilters = trigger.getFilters();
        String sourceFilters = source.getAggregateFilterData();
        if (triggerFilters == null
                || sourceFilters == null
                || triggerFilters.isEmpty()
                || sourceFilters.isEmpty()) {
            // Nothing to match
            return true;
        }

        try {
            AggregateFilterData sourceFiltersData = extractFilterMap(sourceFilters);
            AggregateFilterData triggerFiltersData = extractFilterMap(triggerFilters);
            return Filter.isFilterMatch(sourceFiltersData, triggerFiltersData, true);
        } catch (JSONException e) {
            // If JSON is malformed, we shall consider as not matched.
            LogUtil.e(e, "Malformed JSON string.");
            return false;
        }
    }

    private Optional<EventTrigger> findFirstMatchingEventTrigger(Source source, Trigger trigger) {
        try {
            String sourceFilters = source.getAggregateFilterData();

            AggregateFilterData sourceFiltersData;
            if (sourceFilters == null || sourceFilters.isEmpty()) {
                // Initialize an empty map to add source_type to it later
                sourceFiltersData = new AggregateFilterData.Builder().build();
            } else {
                sourceFiltersData = extractFilterMap(sourceFilters);
            }

            // Add source type
            appendToAggregateFilterData(
                    sourceFiltersData,
                    "source_type",
                    Collections.singletonList(source.getSourceType().getValue()));

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
            AggregateFilterData sourceFiltersData, EventTrigger eventTrigger) {
        if (eventTrigger.getFilterData().isPresent()
                && !Filter.isFilterMatch(
                        sourceFiltersData, eventTrigger.getFilterData().get(), true)) {
            return false;
        }

        if (eventTrigger.getNotFilterData().isPresent()
                && !Filter.isFilterMatch(
                        sourceFiltersData, eventTrigger.getNotFilterData().get(), false)) {
            return false;
        }

        return true;
    }

    private AggregateFilterData extractFilterMap(String object) throws JSONException {
        JSONObject sourceFilterObject = new JSONObject(object);
        return new AggregateFilterData.Builder()
                .buildAggregateFilterData(sourceFilterObject)
                .build();
    }

    private void appendToAggregateFilterData(
            AggregateFilterData filterData, String key, List<String> value) {
        Map<String, List<String>> attributeFilterMap = filterData.getAttributionFilterMap();
        attributeFilterMap.put(key, value);
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

    private static boolean isAdTechWithinPrivacyBounds(Source source, Trigger trigger,
            IMeasurementDao measurementDao) throws DatastoreException {
        Optional<Pair<Uri, Uri>> publisherAndDestination =
                getPublisherAndDestinationTopPrivateDomains(source, trigger);
        if (publisherAndDestination.isPresent()) {
            Integer count =
                    measurementDao.countDistinctAdTechsPerPublisherXDestinationInAttribution(
                            publisherAndDestination.get().first,
                            publisherAndDestination.get().second,
                            trigger.getAdTechDomain(), // TODO: will be replaced with enrollment ID
                            trigger.getTriggerTime()
                                    - PrivacyParams.RATE_LIMIT_WINDOW_MILLISECONDS,
                            trigger.getTriggerTime());

            return count < PrivacyParams
                    .MAX_DISTINCT_AD_TECHS_PER_PUBLISHER_X_DESTINATION_IN_ATTRIBUTION;
        } else {
            LogUtil.d("isAdTechWithinPrivacyBounds: getPublisherAndDestinationTopPrivateDomains"
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
        Optional<Uri> publisherBaseUri =
                extractBaseUri(source.getPublisher(), source.getPublisherType());
        Uri destination = trigger.getAttributionDestination();
        Optional<Uri> destinationBaseUri =
                extractBaseUri(destination, trigger.getDestinationType());

        if (!publisherBaseUri.isPresent() || !destinationBaseUri.isPresent()) {
            throw new IllegalArgumentException(
                    String.format(
                            "insertAttributionRateLimit: "
                                    + "getSourceAndDestinationTopPrivateDomains"
                                    + " failed. Publisher: %s; Attribution destination: %s",
                            source.getPublisher(), destination));
        }

        String publisherTopPrivateDomain = publisherBaseUri.get().toString();
        String triggerDestinationTopPrivateDomain = destinationBaseUri.get().toString();
        return new Attribution.Builder()
                .setSourceSite(publisherTopPrivateDomain)
                .setSourceOrigin(BaseUriExtractor.getBaseUri(source.getPublisher()).toString())
                .setDestinationSite(triggerDestinationTopPrivateDomain)
                .setDestinationOrigin(BaseUriExtractor.getBaseUri(destination).toString())
                .setAdTechDomain(trigger.getAdTechDomain().toString())
                .setEnrollmentId(trigger.getEnrollmentId())
                .setTriggerTime(trigger.getTriggerTime())
                .setRegistrant(trigger.getRegistrant().toString())
                .build();
    }

    private static Optional<Uri> extractBaseUri(Uri uri, @EventSurfaceType int eventSurfaceType) {
        return eventSurfaceType == EventSurfaceType.APP
                ? Optional.of(BaseUriExtractor.getBaseUri(uri))
                : Web.topPrivateDomainAndScheme(uri);
    }
}
