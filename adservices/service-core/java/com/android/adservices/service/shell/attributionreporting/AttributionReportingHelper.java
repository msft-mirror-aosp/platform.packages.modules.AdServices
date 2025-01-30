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

    private AttributionReportingHelper() {
        throw new UnsupportedOperationException(
                "AttributingReportingHelper only provides static methods");
    }

    static JSONObject sourceToJson(Source source) throws JSONException {
        JSONObject jsonObject =
                new JSONObject()
                        .put(SourceContract.ID, source.getId())
                        .put(SourceContract.STATUS, STATUS_MAP.get(source.getStatus()))
                        .put(SourceContract.REGISTRATION_ORIGIN, source.getRegistrationOrigin())
                        .put(SourceContract.REGISTRANT, source.getRegistrant())
                        .put(SourceContract.EVENT_TIME, source.getEventTime())
                        .put(SourceContract.EXPIRY_TIME, source.getExpiryTime())
                        .put(SourceContract.SOURCE_TYPE, source.getSourceType().getValue())
                        .put(SourceContract.ATTRIBUTION_MODE,
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

        return jsonObject;
    }

    static JSONObject triggerToJson(Trigger trigger) throws JSONException {
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

        return jsonObject;
    }

    static JSONObject eventReportToJson(EventReport eventReport) throws JSONException {
        return new JSONObject()
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
                .put(EventReportContract.REGISTRATION_ORIGIN, eventReport.getRegistrationOrigin());
    }

    static JSONObject aggregatableReportToJson(AggregateReport aggregateReport)
            throws JSONException {
        return new JSONObject()
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
    }

    static JSONObject debugReportToJson(DebugReport debugReport) throws JSONException {
        return new JSONObject()
                .put(DebugReportContract.INSERTION_TIME, debugReport.getInsertionTime())
                .put(DebugReportContract.REGISTRATION_ORIGIN, debugReport.getRegistrationOrigin())
                .put(DebugReportContract.TYPE, debugReport.getType());
    }
}
