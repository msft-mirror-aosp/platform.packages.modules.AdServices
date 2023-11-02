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

import static com.android.adservices.service.measurement.PrivacyParams.AGGREGATE_REPORT_DELAY_SPAN;
import static com.android.adservices.service.measurement.PrivacyParams.AGGREGATE_REPORT_MIN_DELAY;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_ATTRIBUTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_DELAYED_SOURCE_REGISTRATION;

import android.annotation.NonNull;
import android.net.Uri;
import android.util.Pair;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.AdServicesConfig;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.WebAddresses;
import com.android.adservices.service.measurement.AttributedTrigger;
import com.android.adservices.service.measurement.Attribution;
import com.android.adservices.service.measurement.AttributionConfig;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.EventTrigger;
import com.android.adservices.service.measurement.FilterMap;
import com.android.adservices.service.measurement.PrivacyParams;
import com.android.adservices.service.measurement.ReportSpec;
import com.android.adservices.service.measurement.ReportSpecUtil;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.aggregation.AggregatableAttributionSource;
import com.android.adservices.service.measurement.aggregation.AggregatableAttributionTrigger;
import com.android.adservices.service.measurement.aggregation.AggregateAttributionData;
import com.android.adservices.service.measurement.aggregation.AggregateDeduplicationKey;
import com.android.adservices.service.measurement.aggregation.AggregateHistogramContribution;
import com.android.adservices.service.measurement.aggregation.AggregatePayloadGenerator;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.noising.SourceNoiseHandler;
import com.android.adservices.service.measurement.reporting.DebugKeyAccessor;
import com.android.adservices.service.measurement.reporting.DebugReportApi;
import com.android.adservices.service.measurement.reporting.DebugReportApi.Type;
import com.android.adservices.service.measurement.reporting.EventReportWindowCalcDelegate;
import com.android.adservices.service.measurement.util.BaseUriExtractor;
import com.android.adservices.service.measurement.util.Filter;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.MeasurementAttributionStats;
import com.android.adservices.service.stats.MeasurementDelayedSourceRegistrationStats;

import com.google.common.collect.ImmutableList;

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
    private static final String AGGREGATE_REPORT_DELAY_DELIMITER = ",";
    private final DatastoreManager mDatastoreManager;
    private final DebugReportApi mDebugReportApi;
    private final EventReportWindowCalcDelegate mEventReportWindowCalcDelegate;
    private final SourceNoiseHandler mSourceNoiseHandler;
    private final AdServicesLogger mLogger;
    private final XnaSourceCreator mXnaSourceCreator;
    private final Flags mFlags;
    private final Filter mFilter;

    private enum TriggeringStatus {
        DROPPED,
        ATTRIBUTED
    }

    AttributionJobHandler(DatastoreManager datastoreManager, DebugReportApi debugReportApi) {
        this(
                datastoreManager,
                FlagsFactory.getFlags(),
                debugReportApi,
                new EventReportWindowCalcDelegate(FlagsFactory.getFlags()),
                new SourceNoiseHandler(FlagsFactory.getFlags()),
                AdServicesLoggerImpl.getInstance(),
                new XnaSourceCreator(FlagsFactory.getFlags()));
    }

    AttributionJobHandler(
            DatastoreManager datastoreManager,
            Flags flags,
            DebugReportApi debugReportApi,
            EventReportWindowCalcDelegate eventReportWindowCalcDelegate,
            SourceNoiseHandler sourceNoiseHandler,
            AdServicesLogger logger,
            XnaSourceCreator xnaSourceCreator) {
        mDatastoreManager = datastoreManager;
        mFlags = flags;
        mDebugReportApi = debugReportApi;
        mEventReportWindowCalcDelegate = eventReportWindowCalcDelegate;
        mSourceNoiseHandler = sourceNoiseHandler;
        mLogger = logger;
        mXnaSourceCreator = xnaSourceCreator;
        mFilter = new Filter(mFlags);
    }

    /**
     * Perform attribution by finding relevant {@link Source} and generates {@link EventReport}.
     *
     * @return false if there are datastore failures or pending {@link Trigger} left, true otherwise
     */
    boolean performPendingAttributions() {
        Optional<List<String>> pendingTriggersOpt = mDatastoreManager
                .runInTransactionWithResult(IMeasurementDao::getPendingTriggerIds);
        if (!pendingTriggersOpt.isPresent()) {
            // Failure during trigger retrieval
            // Reschedule for retry
            return false;
        }
        List<String> pendingTriggers = pendingTriggersOpt.get();

        for (int i = 0;
                i < pendingTriggers.size()
                        && i < mFlags.getMeasurementMaxAttributionsPerInvocation();
                i++) {
            AttributionStatus attributionStatus = new AttributionStatus();
            boolean success = performAttribution(pendingTriggers.get(i), attributionStatus);
            logAttributionStats(attributionStatus);
            if (!success) {
                // Failure during trigger attribution
                // Reschedule for retry
                return false;
            }
        }

        // Reschedule if there are unprocessed pending triggers.
        return mFlags.getMeasurementMaxAttributionsPerInvocation() >= pendingTriggers.size();
    }

    /**
     * Perform attribution for {@code triggerId}.
     *
     * @param triggerId datastore id of the {@link Trigger}
     * @return success
     */
    private boolean performAttribution(String triggerId, AttributionStatus attributionStatus) {
        return mDatastoreManager.runInTransaction(
                measurementDao -> {
                    Trigger trigger = measurementDao.getTrigger(triggerId);
                    attributionStatus.setAttributionDelay(
                            System.currentTimeMillis() - trigger.getTriggerTime());

                    if (trigger.getStatus() != Trigger.Status.PENDING) {
                        attributionStatus.setFailureTypeFromTriggerStatus(trigger.getStatus());
                        return;
                    }

                    Optional<Pair<Source, List<Source>>> sourceOpt =
                            selectSourceToAttribute(trigger, measurementDao, attributionStatus);

                    // Log competing source that did not win attribution because of delay
                    Optional<Source> matchingDelayedSource =
                            measurementDao.getNearestDelayedMatchingActiveSource(trigger);
                    if (matchingDelayedSource.isPresent()) {
                        logDelayedSourceRegistrationStats(matchingDelayedSource.get(), trigger);
                    }

                    if (sourceOpt.isEmpty()) {
                        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                                trigger, measurementDao, Type.TRIGGER_NO_MATCHING_SOURCE);
                        attributionStatus.setAttributionResult(
                                AttributionStatus.AttributionResult.NOT_ATTRIBUTED);
                        attributionStatus.setFailureType(
                                AttributionStatus.FailureType.NO_MATCHING_SOURCE);
                        ignoreTrigger(trigger, measurementDao);
                        return;
                    }

                    Source source = sourceOpt.get().first;
                    List<Source> remainingMatchingSources = sourceOpt.get().second;

                    attributionStatus.setSourceType(source.getSourceType());
                    attributionStatus.setSurfaceTypeFromSourceAndTrigger(source, trigger);
                    attributionStatus.setSourceRegistrant(source.getRegistrant().toString());

                    if (source.isInstallAttributed()) {
                        attributionStatus.setInstallAttribution(true);
                    }

                    if (!doTopLevelFiltersMatch(source, trigger, measurementDao)) {
                        attributionStatus.setAttributionResult(
                                AttributionStatus.AttributionResult.NOT_ATTRIBUTED);
                        attributionStatus.setFailureType(
                                AttributionStatus.FailureType.TOP_LEVEL_FILTER_MATCH_FAILURE);
                        ignoreTrigger(trigger, measurementDao);
                        return;
                    }

                    if (mFlags.getMeasurementEnableSourceDeactivationAfterFiltering()) {
                        ignoreCompetingSources(
                                measurementDao,
                                remainingMatchingSources,
                                trigger.getEnrollmentId());
                    }

                    if (shouldAttributionBeBlockedByRateLimits(source, trigger, measurementDao)) {
                        attributionStatus.setAttributionResult(
                                AttributionStatus.AttributionResult.NOT_ATTRIBUTED);
                        attributionStatus.setFailureType(
                                AttributionStatus.FailureType.RATE_LIMIT_EXCEEDED);
                        ignoreTrigger(trigger, measurementDao);
                        return;
                    }

                    TriggeringStatus aggregateTriggeringStatus =
                            maybeGenerateAggregateReport(
                                    source, trigger, measurementDao, attributionStatus);

                    TriggeringStatus eventTriggeringStatus =
                            maybeGenerateEventReport(
                                    source, trigger, measurementDao, attributionStatus);

                    boolean isEventTriggeringStatusAttributed =
                            eventTriggeringStatus == TriggeringStatus.ATTRIBUTED;
                    boolean isAggregateTriggeringStatusAttributed =
                            aggregateTriggeringStatus == TriggeringStatus.ATTRIBUTED;
                    if (isEventTriggeringStatusAttributed
                            || isAggregateTriggeringStatusAttributed) {
                        if (!mFlags.getMeasurementEnableSourceDeactivationAfterFiltering()) {
                            ignoreCompetingSources(
                                    measurementDao,
                                    remainingMatchingSources,
                                    trigger.getEnrollmentId());
                        }
                        attributeTriggerAndInsertAttribution(trigger, source, measurementDao);
                        attributionStatus.setAttributionResult(
                                isAggregateTriggeringStatusAttributed,
                                isEventTriggeringStatusAttributed);
                    } else {
                        attributionStatus.setAttributionResult(
                                AttributionStatus.AttributionResult.NOT_ATTRIBUTED);
                        attributionStatus.setFailureType(
                                AttributionStatus.FailureType.NO_REPORTS_GENERATED);
                        ignoreTrigger(trigger, measurementDao);
                    }
                });
    }

    private boolean shouldAttributionBeBlockedByRateLimits(
            Source source, Trigger trigger, IMeasurementDao measurementDao)
            throws DatastoreException {
        if (!hasAttributionQuota(source, trigger, measurementDao)
                || !isReportingOriginWithinPrivacyBounds(source, trigger, measurementDao)) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "Attribution blocked by rate limits. Source ID: %s ; Trigger ID: %s ",
                            source.getId(), trigger.getId());
            return true;
        }
        return false;
    }

    private TriggeringStatus maybeGenerateAggregateReport(
            Source source,
            Trigger trigger,
            IMeasurementDao measurementDao,
            AttributionStatus attributionStatus)
            throws DatastoreException {

        if (trigger.getTriggerTime() >= source.getAggregatableReportWindow()) {
            mDebugReportApi.scheduleTriggerDebugReport(
                    source,
                    trigger,
                    null,
                    measurementDao,
                    Type.TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED);
            return TriggeringStatus.DROPPED;
        }

        int numReportsPerDestination =
                measurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType());

        if (numReportsPerDestination >= mFlags.getMeasurementMaxAggregateReportsPerDestination()) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            String.format(
                                    Locale.ENGLISH,
                                    "Aggregate reports for destination %1$s exceeds system health"
                                            + " limit of %2$d.",
                                    trigger.getAttributionDestination(),
                                    mFlags.getMeasurementMaxAggregateReportsPerDestination()));
            mDebugReportApi.scheduleTriggerDebugReport(
                    source,
                    trigger,
                    String.valueOf(numReportsPerDestination),
                    measurementDao,
                    Type.TRIGGER_AGGREGATE_STORAGE_LIMIT);
            return TriggeringStatus.DROPPED;
        }

        if (mFlags.getMeasurementEnableMaxAggregateReportsPerSource()) {
            int numReportsPerSource =
                    measurementDao.getNumAggregateReportsPerSource(source.getId());
            if (numReportsPerSource >= mFlags.getMeasurementMaxAggregateReportsPerSource()) {
                LoggerFactory.getMeasurementLogger()
                        .d(
                                String.format(
                                        Locale.ENGLISH,
                                        "Aggregate reports for source %1$s exceeds system"
                                                + " health limit of %2$d.",
                                        source.getId(),
                                        mFlags.getMeasurementMaxAggregateReportsPerSource()));
                mDebugReportApi.scheduleTriggerDebugReport(
                        source,
                        trigger,
                        String.valueOf(numReportsPerSource),
                        measurementDao,
                        Type.TRIGGER_AGGREGATE_EXCESSIVE_REPORTS);
                return TriggeringStatus.DROPPED;
            }
        }

        try {
            Optional<AggregateDeduplicationKey> aggregateDeduplicationKeyOptional =
                    maybeGetAggregateDeduplicationKey(source, trigger);
            if (aggregateDeduplicationKeyOptional.isPresent()
                    && source.getAggregateReportDedupKeys()
                            .contains(
                                    aggregateDeduplicationKeyOptional
                                            .get()
                                            .getDeduplicationKey()
                                            .get())) {
                mDebugReportApi.scheduleTriggerDebugReport(
                        source,
                        trigger,
                        /* limit = */ null,
                        measurementDao,
                        Type.TRIGGER_AGGREGATE_DEDUPLICATED);
                return TriggeringStatus.DROPPED;
            }
            Optional<List<AggregateHistogramContribution>> contributions =
                    new AggregatePayloadGenerator(mFlags)
                            .generateAttributionReport(source, trigger);
            if (!contributions.isPresent()) {
                if (source.getAggregatableAttributionSource(trigger, mFlags).isPresent()
                        && trigger.getAggregatableAttributionTrigger(mFlags).isPresent()) {
                    mDebugReportApi.scheduleTriggerDebugReport(
                            source,
                            trigger,
                            /* limit = */ null,
                            measurementDao,
                            Type.TRIGGER_AGGREGATE_NO_CONTRIBUTIONS);
                }
                return TriggeringStatus.DROPPED;
            }
            OptionalInt newAggregateContributions =
                    validateAndGetUpdatedAggregateContributions(
                            contributions.get(), source, trigger, measurementDao);
            if (!newAggregateContributions.isPresent()) {
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "Aggregate contributions exceeded bound. Source ID: %s ; "
                                        + "Trigger ID: %s ",
                                source.getId(), trigger.getId());
                return TriggeringStatus.DROPPED;
            }

            source.setAggregateContributions(newAggregateContributions.getAsInt());
            long randomTime = getAggregateReportDelay();
            Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                    new DebugKeyAccessor(measurementDao).getDebugKeys(source, trigger);
            UnsignedLong sourceDebugKey = debugKeyPair.first;
            UnsignedLong triggerDebugKey = debugKeyPair.second;

            int debugReportStatus = AggregateReport.DebugReportStatus.NONE;
            if (sourceDebugKey != null && triggerDebugKey != null) {
                debugReportStatus = AggregateReport.DebugReportStatus.PENDING;
            }
            AggregateReport.Builder aggregateReportBuilder =
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
                            .setRegistrationOrigin(trigger.getRegistrationOrigin());
            if (trigger.getAggregationCoordinatorOrigin() != null) {
                aggregateReportBuilder.setAggregationCoordinatorOrigin(
                        trigger.getAggregationCoordinatorOrigin());
            } else {
                aggregateReportBuilder.setAggregationCoordinatorOrigin(
                        Uri.parse(
                                AdServicesConfig
                                        .getMeasurementDefaultAggregationCoordinatorOrigin()));
            }

            if (aggregateDeduplicationKeyOptional.isPresent()) {
                aggregateReportBuilder.setDedupKey(
                        aggregateDeduplicationKeyOptional.get().getDeduplicationKey().get());
            }
            AggregateReport aggregateReport = aggregateReportBuilder.build();

            finalizeAggregateReportCreation(
                    source, aggregateDeduplicationKeyOptional, aggregateReport, measurementDao);
            incrementAggregateReportCountBy(attributionStatus, 1);
            if (aggregateReport.getDebugReportStatus()
                    == AggregateReport.DebugReportStatus.PENDING) {
                incrementAggregateDebugReportCountBy(attributionStatus, 1);
            }
            // TODO (b/230618328): read from DB and upload unencrypted aggregate report.
            return TriggeringStatus.ATTRIBUTED;
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger()
                    .e(
                            e,
                            "AttributionJobHandler::maybeGenerateAggregateReport JSONException when"
                                    + " parse aggregate fields.");
            return TriggeringStatus.DROPPED;
        }
    }

    private Optional<Pair<Source, List<Source>>> selectSourceToAttribute(
            Trigger trigger, IMeasurementDao measurementDao, AttributionStatus attributionStatus)
            throws DatastoreException {
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
                    mXnaSourceCreator.generateDerivedSources(trigger, otherEnrollmentBasedSources);
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

        if (selectedSource.getParentId() != null) {
            attributionStatus.setSourceDerived(true);
        }

        return Optional.of(Pair.create(selectedSource, matchingSources));
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
            LoggerFactory.getMeasurementLogger().d(e, "Failed to parse attribution configs.");
        }
        return enrollmentIds;
    }

    private Optional<AggregateDeduplicationKey> maybeGetAggregateDeduplicationKey(
            Source source, Trigger trigger) {
        try {
            Optional<AggregateDeduplicationKey> dedupKey;
            Optional<AggregatableAttributionSource> optionalAggregateAttributionSource =
                    source.getAggregatableAttributionSource(trigger, mFlags);
            Optional<AggregatableAttributionTrigger> optionalAggregateAttributionTrigger =
                    trigger.getAggregatableAttributionTrigger(mFlags);
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
                            aggregateAttributionSource.getFilterMap(), mFlags);
            return dedupKey;
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger()
                    .e(
                            e,
                            "AttributionJobHandler::maybeGetAggregateDeduplicationKey JSONException"
                                    + " when parse aggregate dedup key fields in"
                                    + " AttributionJobHandler.");
            return Optional.empty();
        }
    }

    private void ignoreCompetingSources(
            IMeasurementDao measurementDao,
            List<Source> remainingMatchingSources,
            String triggerEnrollmentId)
            throws DatastoreException {
        if (!remainingMatchingSources.isEmpty()) {
            List<String> ignoredOriginalSourceIds = new ArrayList<>();
            for (Source source : remainingMatchingSources) {
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

    private TriggeringStatus maybeGenerateEventReport(
            Source source,
            Trigger trigger,
            IMeasurementDao measurementDao,
            AttributionStatus attributionStatus)
            throws DatastoreException {

        if (source.getParentId() != null) {
            LoggerFactory.getMeasurementLogger()
                    .d("Event report generation skipped because it's a derived source.");
            return TriggeringStatus.DROPPED;
        }

        // TODO: Handle attribution rate limit consideration for non-truthful cases.
        if (source.getAttributionMode() != Source.AttributionMode.TRUTHFULLY) {
            mDebugReportApi.scheduleTriggerDebugReport(
                    source, trigger, null, measurementDao, Type.TRIGGER_EVENT_NOISE);
            return TriggeringStatus.DROPPED;
        }

        if (mEventReportWindowCalcDelegate.getReportingTime(
                                source, trigger.getTriggerTime(), trigger.getDestinationType())
                        == -1
                && (source.getTriggerSpecs() == null || source.getTriggerSpecs().isEmpty())) {
            mDebugReportApi.scheduleTriggerDebugReport(
                    source, trigger, null, measurementDao, Type.TRIGGER_EVENT_REPORT_WINDOW_PASSED);
            return TriggeringStatus.DROPPED;
        }

        Optional<EventTrigger> matchingEventTrigger =
                findFirstMatchingEventTrigger(source, trigger, measurementDao);
        if (!matchingEventTrigger.isPresent()) {
            return TriggeringStatus.DROPPED;
        }

        EventTrigger eventTrigger = matchingEventTrigger.get();
        // Check if deduplication key clashes with existing reports.
        if (eventTrigger.getDedupKey() != null) {
            boolean alreadyAttributed;
            if (mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()) {
                try {
                    source.buildAttributedTriggers();
                    alreadyAttributed = hasDeduplicationKey(source, eventTrigger.getDedupKey());
                } catch (JSONException e) {
                    LoggerFactory.getMeasurementLogger()
                            .e(e, "maybeGenerateEventReport: failed to build attributed triggers.");
                    return TriggeringStatus.DROPPED;
                }
            } else {
                alreadyAttributed = source.getEventReportDedupKeys().contains(
                        eventTrigger.getDedupKey());
            }
            if (alreadyAttributed) {
                mDebugReportApi.scheduleTriggerDebugReport(
                        source,
                        trigger,
                        /* limit = */ null,
                        measurementDao,
                        Type.TRIGGER_EVENT_DEDUPLICATED);
                return TriggeringStatus.DROPPED;
            }
        }

        int numReports =
                measurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType());

        if (numReports >= mFlags.getMeasurementMaxEventReportsPerDestination()) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            String.format(
                                    Locale.ENGLISH,
                                    "Event reports for destination %1$s exceeds system health limit"
                                            + " of %2$d.",
                                    trigger.getAttributionDestination(),
                                    mFlags.getMeasurementMaxEventReportsPerDestination()));
            mDebugReportApi.scheduleTriggerDebugReport(
                    source,
                    trigger,
                    String.valueOf(numReports),
                    measurementDao,
                    Type.TRIGGER_EVENT_STORAGE_LIMIT);
            return TriggeringStatus.DROPPED;
        }

        Pair<List<Uri>, List<Uri>> destinations =
                measurementDao.getSourceDestinations(source.getId());
        source.setAppDestinations(destinations.first);
        source.setWebDestinations(destinations.second);

        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                new DebugKeyAccessor(measurementDao).getDebugKeys(source, trigger);

        EventReport newEventReport =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(
                                source,
                                trigger,
                                eventTrigger,
                                debugKeyPair,
                                mEventReportWindowCalcDelegate,
                                mSourceNoiseHandler,
                                getEventReportDestinations(source, trigger.getDestinationType()),
                                mFlags.getMeasurementFlexibleEventReportingApiEnabled())
                        .build();
        if (!mFlags.getMeasurementFlexibleEventReportingApiEnabled()
                || source.getTriggerSpecs() == null
                || source.getTriggerSpecs().isEmpty()) {
            if (!provisionEventReportQuota(source, trigger, newEventReport, measurementDao)) {
                return TriggeringStatus.DROPPED;
            }
            if (mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()) {
                finalizeEventReportCreation(
                        source, eventTrigger, trigger, newEventReport, measurementDao);
            } else {
                finalizeEventReportCreation(source, eventTrigger, newEventReport, measurementDao);
            }
            incrementEventReportCountBy(attributionStatus, 1);
            if (newEventReport.getDebugReportStatus() == EventReport.DebugReportStatus.PENDING) {
                incrementEventDebugReportCountBy(attributionStatus, 1);
            }
        } else {

            if (!provisionEventReportFlexEventApiQuota(
                    source, newEventReport, measurementDao, eventTrigger)) {
                return TriggeringStatus.DROPPED;
            }
        }
        return TriggeringStatus.ATTRIBUTED;
    }

    private boolean provisionEventReportFlexEventApiQuota(
            Source source,
            EventReport newEventReport,
            IMeasurementDao measurementDao,
            EventTrigger eventTrigger)
            throws DatastoreException {
        ReportSpec reportSpec = source.getFlexEventReportSpec();
        if (!reportSpec.containsTriggerData(newEventReport.getTriggerData())) {
            return false;
        }

        // for flexible event API.
        int bucketIncrements = ReportSpecUtil.countBucketIncrements(reportSpec, newEventReport);
        if (bucketIncrements == 0) {
            // the new proposed report doesn't cause bucket increments so no new report
            // generated
            reportSpec.insertAttributedTrigger(newEventReport);
            // Flex API already inserts the attributed trigger and does not need an explicit action
            // for that.
            if (eventTrigger.getDedupKey() != null
                    && !mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()) {
                source.getEventReportDedupKeys().add(eventTrigger.getDedupKey());
                measurementDao.updateSourceEventReportDedupKeys(source);
            }
        } else {
            List<EventReport> sourceEventReports = measurementDao.getSourceEventReports(source);
            if (sourceEventReports.size() + bucketIncrements <= reportSpec.getMaxReports()) {
                // there are enough quota to generate all report for this trigger. No competing
                // condition
                reportSpec.insertAttributedTrigger(newEventReport);

                finalizeMultipleEventReportCreationWithTriggerSummaryBucket(
                        source,
                        eventTrigger,
                        newEventReport,
                        measurementDao,
                        bucketIncrements,
                        getReportCountForTriggerData(
                                newEventReport.getTriggerData(), sourceEventReports));
            } else {
                // competing condition: more event report candidate than allowed quota
                Pair<List<EventReport>, Integer> tmp =
                        ReportSpecUtil.processIncomingReport(
                                reportSpec, bucketIncrements, newEventReport, sourceEventReports);
                List<EventReport> toBeDeletedReports = tmp.first;
                int numOfNewReportGenerated = tmp.second;
                for (EventReport report : toBeDeletedReports) {
                    measurementDao.deleteEventReport(report);
                    if (report.getTriggerDedupKey() != null
                            && !mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()) {
                        source.getEventReportDedupKeys().remove(report.getTriggerDedupKey());
                    }
                }
                // create a filtered list to remove new event report and deleted event report
                List<EventReport> nonDeletedReports =
                        sourceEventReports.stream()
                                .filter(obj -> !toBeDeletedReports.contains(obj))
                                .collect(Collectors.toList());
                finalizeMultipleEventReportCreationWithTriggerSummaryBucket(
                        source,
                        eventTrigger,
                        newEventReport,
                        measurementDao,
                        numOfNewReportGenerated,
                        getReportCountForTriggerData(
                                newEventReport.getTriggerData(), nonDeletedReports));
                resetSummaryBucketForEventReportsRelatedToDeletion(
                        source, nonDeletedReports, toBeDeletedReports, measurementDao);
            }
        }
        measurementDao.updateSourceAttributedTriggers(
                source.getId(),
                source.attributedTriggersToJsonFlexApi());
        return true;
    }

    private List<Uri> getEventReportDestinations(@NonNull Source source, int destinationType) {
        ImmutableList.Builder<Uri> destinations = new ImmutableList.Builder<>();
        if (mFlags.getMeasurementEnableCoarseEventReportDestinations()
                && source.getCoarseEventReportDestinations()) {
            Optional.ofNullable(source.getAppDestinations()).ifPresent(destinations::addAll);
            Optional.ofNullable(source.getWebDestinations()).ifPresent(destinations::addAll);
        } else {
            destinations.addAll(source.getAttributionDestinations(destinationType));
        }
        return destinations.build();
    }

    private boolean provisionEventReportQuota(
            Source source,
            Trigger trigger,
            EventReport newEventReport,
            IMeasurementDao measurementDao)
            throws DatastoreException {
        List<EventReport> sourceEventReports = measurementDao.getSourceEventReports(source);

        if (isWithinReportLimit(source, sourceEventReports.size(), trigger.getDestinationType())) {
            return true;
        }

        List<EventReport> relevantEventReports =
                sourceEventReports.stream()
                        .filter(
                                (r) ->
                                        r.getStatus() == EventReport.Status.PENDING
                                                && r.getReportTime()
                                                        == newEventReport.getReportTime())
                        .sorted(
                                Comparator.comparingLong(EventReport::getTriggerPriority)
                                        .thenComparing(
                                                EventReport::getTriggerTime,
                                                Comparator.reverseOrder()))
                        .collect(Collectors.toList());

        if (relevantEventReports.isEmpty()) {
            UnsignedLong triggerData = newEventReport.getTriggerData();
            mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                    source,
                    trigger,
                    triggerData,
                    measurementDao,
                    Type.TRIGGER_EVENT_EXCESSIVE_REPORTS);
            return false;
        }

        EventReport lowestPriorityEventReport = relevantEventReports.get(0);
        if (lowestPriorityEventReport.getTriggerPriority() >= newEventReport.getTriggerPriority()) {
            UnsignedLong triggerData = newEventReport.getTriggerData();
            mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                    source, trigger, triggerData, measurementDao, Type.TRIGGER_EVENT_LOW_PRIORITY);
            return false;
        }

        if (lowestPriorityEventReport.getTriggerDedupKey() != null
                && !mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()) {
            source.getEventReportDedupKeys().remove(lowestPriorityEventReport.getTriggerDedupKey());
        }

        measurementDao.deleteEventReport(lowestPriorityEventReport);
        return true;
    }

    private static void finalizeEventReportCreation(
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

    private static void finalizeEventReportCreation(
            Source source,
            EventTrigger eventTrigger,
            Trigger trigger,
            EventReport eventReport,
            IMeasurementDao measurementDao)
            throws DatastoreException {
        if (eventTrigger.getDedupKey() != null) {
            source.getAttributedTriggers().add(
                    new AttributedTrigger(
                            trigger.getId(),
                            eventTrigger.getTriggerData(),
                            eventTrigger.getDedupKey()));
            measurementDao.updateSourceAttributedTriggers(
                    source.getId(),
                    source.attributedTriggersToJson());
        }

        measurementDao.insertEventReport(eventReport);
    }

    private void finalizeMultipleEventReportCreationWithTriggerSummaryBucket(
            Source source,
            EventTrigger eventTrigger,
            EventReport eventReport,
            IMeasurementDao measurementDao,
            int numNewReport,
            int numCurrentReportWithSameTriggerData)
            throws DatastoreException {
        // Flex API already inserts the attributed trigger and does not need an explicit action for
        // that.
        if (eventTrigger.getDedupKey() != null
                && !mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()) {
                source.getEventReportDedupKeys().add(eventTrigger.getDedupKey());
        }
        measurementDao.updateSourceEventReportDedupKeys(source);
        List<Long> summaryBuckets =
                ReportSpecUtil.getSummaryBucketsForTriggerData(
                        source.getFlexEventReportSpec(), eventTrigger.getTriggerData());
        for (int i = 0; i < numNewReport; i++) {
            eventReport.updateSummaryBucket(
                    ReportSpecUtil.getSummaryBucketFromIndex(
                            numCurrentReportWithSameTriggerData++, summaryBuckets));
            measurementDao.insertEventReport(eventReport);
        }
    }

    private static void finalizeAggregateReportCreation(
            Source source,
            Optional<AggregateDeduplicationKey> aggregateDeduplicationKeyOptional,
            AggregateReport aggregateReport,
            IMeasurementDao measurementDao)
            throws DatastoreException {
        if (aggregateDeduplicationKeyOptional.isPresent()) {
            source.getAggregateReportDedupKeys()
                    .add(aggregateDeduplicationKeyOptional.get().getDeduplicationKey().get());
        }

        if (source.getParentId() == null) {
            // Only update aggregate contributions for an original source, not for a derived
            // source
            measurementDao.updateSourceAggregateContributions(source);
            measurementDao.updateSourceAggregateReportDedupKeys(source);
        }
        measurementDao.insertAggregateReport(aggregateReport);
    }

    private static void ignoreTrigger(Trigger trigger, IMeasurementDao measurementDao)
            throws DatastoreException {
        trigger.setStatus(Trigger.Status.IGNORED);
        measurementDao.updateTriggerStatus(
                Collections.singletonList(trigger.getId()), Trigger.Status.IGNORED);
    }

    private static void attributeTriggerAndInsertAttribution(Trigger trigger, Source source,
            IMeasurementDao measurementDao)
            throws DatastoreException {
        trigger.setStatus(Trigger.Status.ATTRIBUTED);
        measurementDao.updateTriggerStatus(
                Collections.singletonList(trigger.getId()), Trigger.Status.ATTRIBUTED);
        measurementDao.insertAttribution(createAttribution(source, trigger));
    }

    private boolean hasAttributionQuota(
            Source source, Trigger trigger, IMeasurementDao measurementDao)
            throws DatastoreException {
        long attributionCount = measurementDao.getAttributionsPerRateLimitWindow(source, trigger);
        if (attributionCount >= mFlags.getMeasurementMaxAttributionPerRateLimitWindow()) {
            mDebugReportApi.scheduleTriggerDebugReport(
                    source,
                    trigger,
                    String.valueOf(attributionCount),
                    measurementDao,
                    Type.TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT);
        }
        return attributionCount < mFlags.getMeasurementMaxAttributionPerRateLimitWindow();
    }

    private boolean isWithinReportLimit(
            Source source, int existingReportCount, @EventSurfaceType int destinationType) {
        return mEventReportWindowCalcDelegate.getMaxReportCount(
                        source, hasAppInstallAttributionOccurred(source, destinationType))
                > existingReportCount;
    }

    private static boolean hasAppInstallAttributionOccurred(
            Source source, @EventSurfaceType int destinationType) {
        return destinationType == EventSurfaceType.APP && source.isInstallAttributed();
    }

    private static boolean isWithinInstallCooldownWindow(Source source, Trigger trigger) {
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
    private boolean doTopLevelFiltersMatch(
            @NonNull Source source, @NonNull Trigger trigger, IMeasurementDao measurementDao)
            throws DatastoreException {
        try {
            FilterMap sourceFilters = source.getFilterData(trigger, mFlags);
            List<FilterMap> triggerFilterSet = extractFilterSet(trigger.getFilters());
            List<FilterMap> triggerNotFilterSet = extractFilterSet(trigger.getNotFilters());
            boolean isFilterMatch =
                    mFilter.isFilterMatch(sourceFilters, triggerFilterSet, true)
                            && mFilter.isFilterMatch(sourceFilters, triggerNotFilterSet, false);
            if (!isFilterMatch
                    && !sourceFilters.isEmpty(mFlags)
                    && (!triggerFilterSet.isEmpty() || !triggerNotFilterSet.isEmpty())) {
                mDebugReportApi.scheduleTriggerDebugReport(
                        source,
                        trigger,
                        /* limit = */ null,
                        measurementDao,
                        Type.TRIGGER_NO_MATCHING_FILTER_DATA);
            }
            return isFilterMatch;
        } catch (JSONException e) {
            // If JSON is malformed, we shall consider as not matched.
            LoggerFactory.getMeasurementLogger()
                    .e(e, "AttributionJobHandler::doTopLevelFiltersMatch: JSON parse failed.");
            return false;
        }
    }

    private Optional<EventTrigger> findFirstMatchingEventTrigger(
            Source source, Trigger trigger, IMeasurementDao measurementDao)
            throws DatastoreException {
        try {
            FilterMap sourceFiltersData = source.getFilterData(trigger, mFlags);
            List<EventTrigger> eventTriggers = trigger.parseEventTriggers(mFlags);
            Optional<EventTrigger> matchingEventTrigger =
                    eventTriggers.stream()
                            .filter(
                                    eventTrigger ->
                                            doEventLevelFiltersMatch(
                                                    sourceFiltersData, eventTrigger))
                            .findFirst();
            // trigger-no-matching-configurations verbose debug report is generated when event
            // trigger "filters/not_filters" field doesn't match source "filter_data" field. It
            // won't be generated when trigger doesn't have event_trigger_data field.
            if (!matchingEventTrigger.isPresent() && !eventTriggers.isEmpty()) {
                mDebugReportApi.scheduleTriggerDebugReport(
                        source,
                        trigger,
                        /* limit = */ null,
                        measurementDao,
                        Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS);
            }
            return matchingEventTrigger;
        } catch (JSONException e) {
            // If JSON is malformed, we shall consider as not matched.
            LoggerFactory.getMeasurementLogger()
                    .e(
                            e,
                            "AttributionJobHandler::findFirstMatchingEventTrigger: Malformed JSON"
                                    + " string.");
            return Optional.empty();
        }
    }

    private boolean doEventLevelFiltersMatch(
            FilterMap sourceFiltersData, EventTrigger eventTrigger) {
        if (eventTrigger.getFilterSet().isPresent()
                && !mFilter.isFilterMatch(
                        sourceFiltersData, eventTrigger.getFilterSet().get(), true)) {
            return false;
        }

        if (eventTrigger.getNotFilterSet().isPresent()
                && !mFilter.isFilterMatch(
                        sourceFiltersData, eventTrigger.getNotFilterSet().get(), false)) {
            return false;
        }

        return true;
    }

    private List<FilterMap> extractFilterSet(String str) throws JSONException {
        return mFlags.getMeasurementEnableLookbackWindowFilter()
                ? extractFilterSetV2(str)
                : extractFilterSetV1(str);
    }

    private List<FilterMap> extractFilterSetV1(String str) throws JSONException {
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

    private List<FilterMap> extractFilterSetV2(String str) throws JSONException {
        String json = (str == null || str.isEmpty()) ? "[]" : str;
        JSONArray filters = new JSONArray(json);
        return mFilter.deserializeFilterSet(filters);
    }

    private OptionalInt validateAndGetUpdatedAggregateContributions(
            List<AggregateHistogramContribution> contributions,
            Source source,
            Trigger trigger,
            IMeasurementDao measurementDao)
            throws DatastoreException {
        int newAggregateContributions = source.getAggregateContributions();
        for (AggregateHistogramContribution contribution : contributions) {
            try {
                newAggregateContributions =
                        Math.addExact(newAggregateContributions, contribution.getValue());
                if (newAggregateContributions
                        >= PrivacyParams.MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE) {
                    // When histogram value is >= 65536 (aggregatable_budget_per_source),
                    // generate verbose debug report, record the actual histogram value.
                    mDebugReportApi.scheduleTriggerDebugReport(
                            source,
                            trigger,
                            String.valueOf(PrivacyParams.MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE),
                            measurementDao,
                            Type.TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET);
                }
                if (newAggregateContributions
                        > PrivacyParams.MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE) {
                    return OptionalInt.empty();
                }
            } catch (ArithmeticException e) {
                LoggerFactory.getMeasurementLogger()
                        .e(
                                e,
                                "AttributionJobHandler::validateAndGetUpdatedAggregateContributions"
                                        + " Error adding aggregate contribution values.");
                return OptionalInt.empty();
            }
        }
        return OptionalInt.of(newAggregateContributions);
    }

    private static long roundDownToDay(long timestamp) {
        return Math.floorDiv(timestamp, TimeUnit.DAYS.toMillis(1)) * TimeUnit.DAYS.toMillis(1);
    }

    private boolean isReportingOriginWithinPrivacyBounds(
            Source source, Trigger trigger, IMeasurementDao measurementDao)
            throws DatastoreException {
        Optional<Pair<Uri, Uri>> publisherAndDestination =
                getPublisherAndDestinationTopPrivateDomains(source, trigger);
        if (publisherAndDestination.isPresent()) {
            Integer count =
                    measurementDao.countDistinctReportingOriginsPerPublisherXDestInAttribution(
                            publisherAndDestination.get().first,
                            publisherAndDestination.get().second,
                            trigger.getRegistrationOrigin(),
                            trigger.getTriggerTime() - PrivacyParams.RATE_LIMIT_WINDOW_MILLISECONDS,
                            trigger.getTriggerTime());
            if (count >= mFlags.getMeasurementMaxDistinctEnrollmentsInAttribution()) {
                mDebugReportApi.scheduleTriggerDebugReport(
                        source,
                        trigger,
                        String.valueOf(count),
                        measurementDao,
                        Type.TRIGGER_REPORTING_ORIGIN_LIMIT);
            }

            return count < mFlags.getMeasurementMaxDistinctEnrollmentsInAttribution();
        } else {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "isEnrollmentWithinPrivacyBounds:"
                                    + " getPublisherAndDestinationTopPrivateDomains failed. %s %s",
                            source.getPublisher(), trigger.getAttributionDestination());
            return true;
        }
    }

    private static Optional<Pair<Uri, Uri>> getPublisherAndDestinationTopPrivateDomains(
            Source source, Trigger trigger) {
        Uri attributionDestination = trigger.getAttributionDestination();
        Optional<Uri> triggerDestinationTopPrivateDomain =
                trigger.getDestinationType() == EventSurfaceType.APP
                        ? Optional.of(BaseUriExtractor.getBaseUri(attributionDestination))
                        : WebAddresses.topPrivateDomainAndScheme(attributionDestination);
        Uri publisher = source.getPublisher();
        Optional<Uri> publisherTopPrivateDomain =
                source.getPublisherType() == EventSurfaceType.APP
                        ? Optional.of(publisher)
                        : WebAddresses.topPrivateDomainAndScheme(publisher);
        if (!triggerDestinationTopPrivateDomain.isPresent()
                || !publisherTopPrivateDomain.isPresent()) {
            return Optional.empty();
        } else {
            return Optional.of(Pair.create(
                    publisherTopPrivateDomain.get(),
                    triggerDestinationTopPrivateDomain.get()));
        }
    }

    public static Attribution createAttribution(@NonNull Source source, @NonNull Trigger trigger) {
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
                // TODO: b/276638412 rename to Attribution::setSourceTime
                .setTriggerTime(source.getEventTime())
                .setRegistrant(trigger.getRegistrant().toString())
                .setSourceId(source.getId())
                .setTriggerId(trigger.getId())
                .setRegistrationOrigin(trigger.getRegistrationOrigin())
                .build();
    }

    private static Optional<Uri> getTopPrivateDomain(
            Uri uri, @EventSurfaceType int eventSurfaceType) {
        return eventSurfaceType == EventSurfaceType.APP
                ? Optional.of(BaseUriExtractor.getBaseUri(uri))
                : WebAddresses.topPrivateDomainAndScheme(uri);
    }

    private static boolean hasDeduplicationKey(@NonNull Source source,
            @NonNull UnsignedLong dedupKey) {
        for (AttributedTrigger attributedTrigger : source.getAttributedTriggers()) {
            if (dedupKey.equals(attributedTrigger.getDedupKey())) {
                return true;
            }
        }
        return false;
    }

    private void logAttributionStats(AttributionStatus attributionStatus) {
        mLogger.logMeasurementAttributionStats(
                new MeasurementAttributionStats.Builder()
                        .setCode(AD_SERVICES_MEASUREMENT_ATTRIBUTION)
                        .setSourceType(attributionStatus.getSourceType().getValue())
                        .setSurfaceType(attributionStatus.getAttributionSurface().getValue())
                        .setResult(attributionStatus.getAttributionResult().getValue())
                        .setFailureType(attributionStatus.getFailureType().getValue())
                        .setSourceDerived(attributionStatus.isSourceDerived())
                        .setInstallAttribution(attributionStatus.isInstallAttribution())
                        .setAttributionDelay(attributionStatus.getAttributionDelay())
                        .setSourceRegistrant(attributionStatus.getSourceRegistrant())
                        .build());
    }

    private void logDelayedSourceRegistrationStats(Source source, Trigger trigger) {
        DelayedSourceRegistrationStatus delayedSourceRegistrationStatus =
                new DelayedSourceRegistrationStatus();
        delayedSourceRegistrationStatus.setRegistrationDelay(
                source.getEventTime() - trigger.getTriggerTime());

        mLogger.logMeasurementDelayedSourceRegistrationStats(
                new MeasurementDelayedSourceRegistrationStats.Builder()
                        .setCode(AD_SERVICES_MEASUREMENT_DELAYED_SOURCE_REGISTRATION)
                        .setRegistrationStatus(delayedSourceRegistrationStatus.UNKNOWN)
                        .setRegistrationDelay(
                                delayedSourceRegistrationStatus.getRegistrationDelay())
                        .setRegistrant(source.getRegistrant().toString())
                        .build());
    }

    private long getAggregateReportDelay() {
        long reportDelayFromDefaults =
                (long) (Math.random() * AGGREGATE_REPORT_DELAY_SPAN + AGGREGATE_REPORT_MIN_DELAY);

        if (!mFlags.getMeasurementEnableConfigurableAggregateReportDelay()) {
            return reportDelayFromDefaults;
        }

        String aggregateReportDelayString = mFlags.getMeasurementAggregateReportDelayConfig();

        if (aggregateReportDelayString == null) {
            LoggerFactory.getMeasurementLogger()
                    .d("Invalid configurable aggregate report delay: null");
            return reportDelayFromDefaults;
        }

        String[] split = aggregateReportDelayString.split(AGGREGATE_REPORT_DELAY_DELIMITER);

        if (split.length != 2) {
            LoggerFactory.getMeasurementLogger()
                    .d("Invalid configurable aggregate report delay: length is not two");
            return reportDelayFromDefaults;
        }

        try {
            final long minDelay = Long.parseLong(split[0].trim());
            final long delaySpan = Long.parseLong(split[1].trim());
            return (long) (Math.random() * delaySpan + minDelay);
        } catch (NumberFormatException e) {
            LoggerFactory.getMeasurementLogger()
                    .e(e, "Configurable aggregate report delay parsing failed.");
            return reportDelayFromDefaults;
        }
    }

    private int getReportCountForTriggerData(
            UnsignedLong triggerData, List<EventReport> currentReports) {
        int count = 0;
        for (EventReport report : currentReports) {
            if (report.getTriggerData().equals(triggerData)) {
                count++;
            }
        }
        return count;
    }

    private void resetSummaryBucketForEventReportsRelatedToDeletion(
            Source source,
            List<EventReport> currentEventReports,
            List<EventReport> deletedReports,
            IMeasurementDao measurementDao)
            throws DatastoreException {
        Set<UnsignedLong> processedTriggerData = new HashSet<>();
        List<EventReport> orderedEventReports =
                currentEventReports.stream()
                        .sorted(Comparator.comparingLong(EventReport::getTriggerTime).reversed())
                        .collect(Collectors.toList());

        for (EventReport deletedReport : deletedReports) {
            UnsignedLong triggerData = deletedReport.getTriggerData();
            if (processedTriggerData.contains(triggerData)) {
                continue;
            }
            processedTriggerData.add(triggerData);
            int count = 0;
            List<Long> summaryBuckets =
                    ReportSpecUtil.getSummaryBucketsForTriggerData(
                            source.getFlexEventReportSpec(), triggerData);
            for (EventReport currentReport : orderedEventReports) {
                Pair<Long, Long> newSummaryBucket =
                        ReportSpecUtil.getSummaryBucketFromIndex(count, summaryBuckets);
                count++;
                if (!newSummaryBucket.equals(currentReport.getTriggerSummaryBucket())) {
                    // only the new bucket different with original one, we need to update DB
                    currentReport.updateSummaryBucket(newSummaryBucket);
                    measurementDao.updateEventReportSummaryBucket(
                            currentReport.getId(),
                            currentReport.getStringEncodedTriggerSummaryBucket());
                }
            }
        }
    }

    private void incrementEventReportCountBy(AttributionStatus attributionStatus, int count) {
        attributionStatus.setEventReportCount(attributionStatus.getEventReportCount() + count);
    }

    private void incrementEventDebugReportCountBy(AttributionStatus attributionStatus, int count) {
        attributionStatus.setEventDebugReportCount(
                attributionStatus.getEventDebugReportCount() + count);
    }

    private void incrementAggregateReportCountBy(AttributionStatus attributionStatus, int count) {
        attributionStatus.setAggregateReportCount(
                attributionStatus.getAggregateReportCount() + count);
    }

    private void incrementAggregateDebugReportCountBy(
            AttributionStatus attributionStatus, int count) {
        attributionStatus.setAggregateDebugReportCount(
                attributionStatus.getAggregateDebugReportCount() + count);
    }
}
