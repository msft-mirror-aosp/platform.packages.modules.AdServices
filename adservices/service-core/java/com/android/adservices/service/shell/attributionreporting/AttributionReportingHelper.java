/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.shell.attributionreporting;

import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.data.measurement.MeasurementTables.DebugReportContract;
import com.android.adservices.data.measurement.MeasurementTables.EventReportContract;
import com.android.adservices.data.measurement.MeasurementTables.SourceContract;
import com.android.adservices.data.measurement.MeasurementTables.TriggerContract;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.reporting.DebugReport;

import com.google.common.collect.ImmutableMap;

import org.json.JSONException;
import org.json.JSONObject;

public final class AttributionReportingHelper {
    private static final String APP_DESTINATION = "app_destination";
    private static final String WEB_DESTINATION = "web_destination";
    private static final String ACTIVE = "active";
    private static final String IGNORED = "ignored";
    private static final String MARKED_TO_DELETE = "marked_to_delete";
    private static final String RANDOMIZED = "randomized";
    private static final String SCHEMA_FULL = "full";

    public static final ImmutableMap<Integer, String> STATUS_MAP =
            ImmutableMap.of(
                    Source.Status.ACTIVE, ACTIVE,
                    Source.Status.IGNORED, IGNORED,
                    Source.Status.MARKED_TO_DELETE, MARKED_TO_DELETE);

    public static final ImmutableMap<Integer, String> ATTRIBUTION_MODE_MAP =
            ImmutableMap.of(
                    Source.AttributionMode.TRUTHFULLY, "Attributable",
                    Source.AttributionMode.FALSELY, "Unattributable: noised with fake reports",
                    Source.AttributionMode.NEVER, "Unattributable: noised with no reports",
                    Source.AttributionMode.UNASSIGNED, "Unassigned");

    public static final ImmutableMap<Source.TriggerDataMatching, String>
            TRIGGER_DATA_MATCHING_STRING_IMMUTABLE_MAP =
                    ImmutableMap.of(
                            Source.TriggerDataMatching.MODULUS, "Modulus",
                            Source.TriggerDataMatching.EXACT, "Exact");

    private AttributionReportingHelper() {
        throw new UnsupportedOperationException(
                "AttributingReportingHelper only provides static methods");
    }

    static JSONObject sourceToJson(Source source, String schema) throws JSONException {
        JSONObject jsonObject =
                new JSONObject()
                        .put(SourceContract.EVENT_ID, source.getEventId())
                        .put(SourceContract.STATUS, STATUS_MAP.get(source.getStatus()))
                        .put(SourceContract.REGISTRATION_ORIGIN, source.getRegistrationOrigin())
                        .put(SourceContract.REGISTRANT, source.getRegistrant())
                        .put(SourceContract.EVENT_TIME, source.getEventTime())
                        .put(SourceContract.EXPIRY_TIME, source.getExpiryTime())
                        .put(SourceContract.SOURCE_TYPE, source.getSourceType().getValue())
                        .put(
                                SourceContract.ATTRIBUTION_MODE,
                                ATTRIBUTION_MODE_MAP.get(source.getAttributionMode()));

        if (source.getDebugKey() != null) {
            jsonObject.put(SourceContract.DEBUG_KEY, source.getDebugKey().toString());
        }

        if (source.hasAppDestinations()) {
            jsonObject.put(APP_DESTINATION, source.getAppDestinations());
        }

        if (source.hasWebDestinations()) {
            jsonObject.put(WEB_DESTINATION, source.getWebDestinations());
        }

        if (schema.equals(SCHEMA_FULL)) {
            jsonObject
                    .put(
                            SourceContract.AGGREGATABLE_REPORT_WINDOW,
                            source.getAggregatableReportWindow())
                    .put(SourceContract.SHARED_AGGREGATION_KEYS, source.getSharedAggregationKeys())
                    .put(
                            replaceWithAggregatable(SourceContract.AGGREGATE_DEBUG_REPORTING),
                            source.getAggregateDebugReportingString())
                    .put(
                            SourceContract.DESTINATION_LIMIT_PRIORITY,
                            source.getDestinationLimitPriority())
                    .put(SourceContract.EVENT_LEVEL_EPSILON, source.getEventLevelEpsilon())
                    .put(SourceContract.FILTER_DATA, source.getFilterDataString())
                    .put(SourceContract.MAX_EVENT_LEVEL_REPORTS, source.getMaxEventLevelReports())
                    .put(SourceContract.PRIORITY, source.getPriority())
                    .put(
                            SourceContract.TRIGGER_DATA_MATCHING,
                            TRIGGER_DATA_MATCHING_STRING_IMMUTABLE_MAP.get(
                                    source.getTriggerDataMatching()));
        }

        return jsonObject;
    }

    static JSONObject triggerToJson(Trigger trigger, String schema) throws JSONException {
        JSONObject jsonObject =
                new JSONObject()
                        .put(TriggerContract.TRIGGER_TIME, trigger.getTriggerTime())
                        .put(
                                TriggerContract.ATTRIBUTION_DESTINATION,
                                trigger.getAttributionDestination())
                        .put(TriggerContract.REGISTRATION_ORIGIN, trigger.getRegistrationOrigin());

        if (trigger.getDebugKey() != null) {
            jsonObject.put(TriggerContract.DEBUG_KEY, trigger.getDebugKey().toString());
        }

        if (schema.equals(SCHEMA_FULL)) {
            jsonObject
                    .put(
                            replaceWithAggregatable(TriggerContract.AGGREGATE_DEBUG_REPORTING),
                            trigger.getAggregateDebugReportingString())
                    .put(
                            TriggerContract.AGGREGATABLE_DEDUPLICATION_KEYS,
                            trigger.getAggregateDeduplicationKeys())
                    .put(
                            TriggerContract.AGGREGATABLE_FILTERING_ID_MAX_BYTES,
                            trigger.getAggregatableFilteringIdMaxBytes())
                    .put(
                            replaceWithAggregatable(TriggerContract.AGGREGATE_TRIGGER_DATA),
                            trigger.getAggregateTriggerData())
                    .put(
                            replaceWithAggregatable(TriggerContract.AGGREGATE_VALUES),
                            trigger.getAggregateValuesString())
                    .put(
                            TriggerContract.AGGREGATION_COORDINATOR_ORIGIN,
                            trigger.getAggregationCoordinatorOrigin())
                    .put(
                            TriggerContract.DEBUG_REPORTING,
                            trigger.getAggregateDebugReportingString())
                    .put(TriggerContract.EVENT_TRIGGERS, trigger.getEventTriggers());
        }

        return jsonObject;
    }

    static JSONObject eventReportToJson(EventReport eventReport, String schema)
            throws JSONException {
        JSONObject jsonObject =
                new JSONObject()
                        .put(EventReportContract.STATUS, eventReport.getStatus())
                        .put(
                                EventReportContract.ATTRIBUTION_DESTINATION,
                                eventReport.getAttributionDestinations())
                        .put(EventReportContract.TRIGGER_TIME, eventReport.getTriggerTime())
                        .put(EventReportContract.REPORT_TIME, eventReport.getReportTime())
                        .put(EventReportContract.TRIGGER_PRIORITY, eventReport.getTriggerPriority())
                        .put(
                                EventReportContract.RANDOMIZED_TRIGGER_RATE,
                                eventReport.getRandomizedTriggerRate())
                        .put(RANDOMIZED, eventReport.isRandomized())
                        .put(
                                EventReportContract.REGISTRATION_ORIGIN,
                                eventReport.getRegistrationOrigin());

        if (schema.equals(SCHEMA_FULL)) {
            jsonObject
                    .put(EventReportContract.SOURCE_DEBUG_KEY, eventReport.getSourceDebugKey())
                    .put(EventReportContract.SOURCE_EVENT_ID, eventReport.getSourceEventId())
                    .put(EventReportContract.SOURCE_TYPE, eventReport.getSourceType().getValue())
                    .put(EventReportContract.TRIGGER_DATA, eventReport.getTriggerData())
                    .put(EventReportContract.TRIGGER_DEBUG_KEY, eventReport.getTriggerDebugKey());
        }

        return jsonObject;
    }

    static JSONObject aggregatableReportToJson(AggregateReport aggregateReport, String schema)
            throws JSONException {
        JSONObject jsonObject =
                new JSONObject()
                        .put(MeasurementTables.AggregateReport.STATUS, aggregateReport.getStatus())
                        .put(
                                MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION,
                                aggregateReport.getAttributionDestination())
                        .put(
                                MeasurementTables.AggregateReport.TRIGGER_TIME,
                                aggregateReport.getTriggerTime())
                        .put(
                                MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME,
                                aggregateReport.getScheduledReportTime())
                        .put(
                                MeasurementTables.AggregateReport.AGGREGATION_COORDINATOR_ORIGIN,
                                aggregateReport.getAggregationCoordinatorOrigin())
                        .put(
                                MeasurementTables.AggregateReport.DEBUG_CLEARTEXT_PAYLOAD,
                                aggregateReport.getDebugCleartextPayload())
                        .put(
                                MeasurementTables.AggregateReport.REGISTRATION_ORIGIN,
                                aggregateReport.getRegistrationOrigin())
                        .put(
                                MeasurementTables.AggregateReport.TRIGGER_CONTEXT_ID,
                                aggregateReport.getTriggerContextId());

        if (schema.equals(SCHEMA_FULL)) {
            jsonObject
                    .put(MeasurementTables.AggregateReport.API, aggregateReport.getApi())
                    .put(
                            MeasurementTables.AggregateReport.DEBUG_REPORT_STATUS,
                            aggregateReport.getDebugReportStatus())
                    .put(
                            MeasurementTables.AggregateReport.PUBLISHER,
                            aggregateReport.getPublisher())
                    .put(
                            MeasurementTables.AggregateReport.API_VERSION,
                            aggregateReport.getApiVersion())
                    .put(
                            MeasurementTables.AggregateReport.SOURCE_DEBUG_KEY,
                            aggregateReport.getSourceDebugKey())
                    .put(
                            MeasurementTables.AggregateReport.TRIGGER_DEBUG_KEY,
                            aggregateReport.getTriggerDebugKey());
        }

        return jsonObject;
    }

    static JSONObject debugReportToJson(DebugReport debugReport, String schema)
            throws JSONException {
        JSONObject jsonObject =
                new JSONObject()
                        .put(DebugReportContract.INSERTION_TIME, debugReport.getInsertionTime())
                        .put(
                                DebugReportContract.REGISTRATION_ORIGIN,
                                debugReport.getRegistrationOrigin())
                        .put(DebugReportContract.TYPE, debugReport.getType());

        if (schema.equals(SCHEMA_FULL)) {
            jsonObject.put(DebugReportContract.BODY, debugReport.getBody());
        }

        return jsonObject;
    }

    public static String replaceWithAggregatable(String input) {
        return input.replaceAll("aggregate", "aggregatable");
    }
}
