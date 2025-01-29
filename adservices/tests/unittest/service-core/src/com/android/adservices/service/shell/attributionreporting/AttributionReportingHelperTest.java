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

import static com.android.adservices.service.shell.attributionreporting.AttributionReportingHelper.TRIGGER_DATA_MATCHING_STRING_IMMUTABLE_MAP;
import static com.android.adservices.service.shell.attributionreporting.AttributionReportingHelper.replaceWithAggregatable;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.data.measurement.MeasurementTables.EventReportContract;
import com.android.adservices.data.measurement.MeasurementTables.SourceContract;
import com.android.adservices.data.measurement.MeasurementTables.TriggerContract;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.EventReportFixture;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.TriggerFixture;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.aggregation.AggregateReportFixture;
import com.android.adservices.service.measurement.reporting.DebugReport;
import com.android.adservices.service.measurement.reporting.DebugReportFixture;
import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public final class AttributionReportingHelperTest extends AdServicesUnitTestCase {
    private static final String APP_DESTINATION = "app_destination";
    private static final String WEB_DESTINATION = "web_destination";
    private static final String RANDOMIZED = "randomized";
    private static final String SCHEMA_PARTIAL = "partial";
    private static final String SCHEMA_FULL = "full";

    @Test
    public void testSourceToJson_partialSchemaHappyPath() throws JSONException {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setDebugKey(SourceFixture.ValidSourceParams.DEBUG_KEY)
                        .build();
        JSONObject jsonObject = AttributionReportingHelper.sourceToJson(source, SCHEMA_PARTIAL);

        expect.withMessage("ID")
                .that(jsonObject.getString(SourceContract.ID))
                .isEqualTo(source.getId());
        expect.withMessage("STATUS")
                .that(jsonObject.getString(SourceContract.STATUS))
                .isEqualTo(AttributionReportingHelper.STATUS_MAP.get(source.getStatus()));
        expect.withMessage("REGISTRATION_ORIGIN")
                .that(jsonObject.getString(SourceContract.REGISTRATION_ORIGIN))
                .isEqualTo(source.getRegistrationOrigin().toString());
        expect.withMessage("APP_DESTINATION")
                .that(jsonObject.getString(APP_DESTINATION))
                .isEqualTo(source.getAppDestinations().toString());
        expect.withMessage("WEB_DESTINATION")
                .that(jsonObject.getString(WEB_DESTINATION))
                .isEqualTo(source.getWebDestinations().toString());
        expect.withMessage("REGISTRANT")
                .that(jsonObject.getString(SourceContract.REGISTRANT))
                .isEqualTo(source.getRegistrant().toString());
        expect.withMessage("EVENT_TIME")
                .that(jsonObject.getLong(SourceContract.EVENT_TIME))
                .isEqualTo(source.getEventTime());
        expect.withMessage("EXPIRY_TIME")
                .that(jsonObject.getLong(SourceContract.EXPIRY_TIME))
                .isEqualTo(source.getExpiryTime());
        expect.withMessage("SOURCE_TYPE")
                .that(jsonObject.getString(SourceContract.SOURCE_TYPE))
                .isEqualTo(source.getSourceType().getValue());

        String debugKeyString = jsonObject.getString(SourceContract.DEBUG_KEY);
        expect.withMessage("DEBUG_KEY")
                .that(debugKeyString)
                .isEqualTo(source.getDebugKey().toString());
        expect.withMessage("ATTRIBUTION_MODE")
                .that(jsonObject.getString(SourceContract.ATTRIBUTION_MODE))
                .isEqualTo(
                        AttributionReportingHelper.ATTRIBUTION_MODE_MAP.get(
                                source.getAttributionMode()));
    }

    @Test
    public void testSourceToJson_fullSchemaHappyPath() throws JSONException {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setDebugKey(SourceFixture.ValidSourceParams.DEBUG_KEY)
                        .setMaxEventLevelReports(10)
                        .build();
        JSONObject jsonObject = AttributionReportingHelper.sourceToJson(source, SCHEMA_FULL);

        expect.withMessage("ID")
                .that(jsonObject.getString(SourceContract.ID))
                .isEqualTo(source.getId());
        expect.withMessage("STATUS")
                .that(jsonObject.getString(SourceContract.STATUS))
                .isEqualTo(AttributionReportingHelper.STATUS_MAP.get(source.getStatus()));
        expect.withMessage("REGISTRATION_ORIGIN")
                .that(jsonObject.getString(SourceContract.REGISTRATION_ORIGIN))
                .isEqualTo(source.getRegistrationOrigin().toString());
        expect.withMessage("APP_DESTINATION")
                .that(jsonObject.getString(APP_DESTINATION))
                .isEqualTo(source.getAppDestinations().toString());
        expect.withMessage("WEB_DESTINATION")
                .that(jsonObject.getString(WEB_DESTINATION))
                .isEqualTo(source.getWebDestinations().toString());
        expect.withMessage("REGISTRANT")
                .that(jsonObject.getString(SourceContract.REGISTRANT))
                .isEqualTo(source.getRegistrant().toString());
        expect.withMessage("EVENT_TIME")
                .that(jsonObject.getLong(SourceContract.EVENT_TIME))
                .isEqualTo(source.getEventTime());
        expect.withMessage("EXPIRY_TIME")
                .that(jsonObject.getLong(SourceContract.EXPIRY_TIME))
                .isEqualTo(source.getExpiryTime());
        expect.withMessage("SOURCE_TYPE")
                .that(jsonObject.getString(SourceContract.SOURCE_TYPE))
                .isEqualTo(source.getSourceType().getValue());

        String debugKeyString = jsonObject.getString(SourceContract.DEBUG_KEY);
        expect.withMessage("DEBUG_KEY")
                .that(debugKeyString)
                .isEqualTo(source.getDebugKey().toString());
        expect.withMessage("ATTRIBUTION_MODE")
                .that(jsonObject.getString(SourceContract.ATTRIBUTION_MODE))
                .isEqualTo(AttributionReportingHelper.ATTRIBUTION_MODE_MAP.get(
                        source.getAttributionMode()));
        expect.withMessage("AGGREGATABLE_REPORT_WINDOW")
                .that(jsonObject.getLong(SourceContract.AGGREGATABLE_REPORT_WINDOW))
                .isEqualTo(source.getAggregatableReportWindow());
        expect.withMessage("SHARED_AGGREGATION_KEYS")
                .that(jsonObject.getString(SourceContract.SHARED_AGGREGATION_KEYS))
                .isEqualTo(source.getSharedAggregationKeys());
        String aggregatableDebugReporting =
                replaceWithAggregatable(SourceContract.AGGREGATE_DEBUG_REPORTING);
        expect.withMessage("AGGREGATABLE_DEBUG_REPORTING")
                .that(jsonObject.get(aggregatableDebugReporting))
                .isEqualTo(source.getAggregateDebugReportingString());
        expect.withMessage("DESTINATION_LIMIT_PRIORITY")
                .that(jsonObject.getLong(SourceContract.DESTINATION_LIMIT_PRIORITY))
                .isEqualTo(source.getDestinationLimitPriority());
        expect.withMessage("EVENT_LEVEL_EPSILON")
                .that(jsonObject.getDouble(SourceContract.EVENT_LEVEL_EPSILON))
                .isEqualTo(source.getEventLevelEpsilon());
        expect.withMessage("FILTER_DATA")
                .that(jsonObject.getString(SourceContract.FILTER_DATA))
                .isEqualTo(source.getFilterDataString());
        expect.withMessage("MAX_EVENT_LEVEL_REPORTS")
                .that(jsonObject.getInt(SourceContract.MAX_EVENT_LEVEL_REPORTS))
                .isEqualTo(source.getMaxEventLevelReports());
        expect.withMessage("PRIORITY")
                .that(jsonObject.getLong(SourceContract.PRIORITY))
                .isEqualTo(source.getPriority());
        expect.withMessage("TRIGGER_DATA_MATCHING")
                .that(jsonObject.get(SourceContract.TRIGGER_DATA_MATCHING))
                .isEqualTo(
                        TRIGGER_DATA_MATCHING_STRING_IMMUTABLE_MAP.get(
                                source.getTriggerDataMatching()));
    }

    @Test
    public void testTriggerToJson_happyPath() throws JSONException {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setTriggerTime(TriggerFixture.ValidTriggerParams.TRIGGER_TIME)
                        .setDebugKey(TriggerFixture.ValidTriggerParams.DEBUG_KEY)
                        .build();
        JSONObject jsonObject = AttributionReportingHelper.triggerToJson(trigger, SCHEMA_PARTIAL);

        expect.withMessage("TRIGGER_TIME")
                .that(jsonObject.getLong(TriggerContract.TRIGGER_TIME))
                .isEqualTo(trigger.getTriggerTime());
        expect.withMessage("ATTRIBUTION_DESTINATION")
                .that(jsonObject.getString(TriggerContract.ATTRIBUTION_DESTINATION))
                .isEqualTo(trigger.getAttributionDestination().toString());
        expect.withMessage("REGISTRATION_ORIGIN")
                .that(jsonObject.getString(TriggerContract.REGISTRATION_ORIGIN))
                .isEqualTo(trigger.getRegistrationOrigin().toString());
        expect.withMessage("TRIGGER_TIME")
                .that(jsonObject.getLong(TriggerContract.TRIGGER_TIME))
                .isEqualTo(trigger.getTriggerTime());
        String debugKeyString = jsonObject.getString(TriggerContract.DEBUG_KEY);
        expect.withMessage("DEBUG_KEY")
                .that(debugKeyString)
                .isEqualTo(trigger.getDebugKey().toString());
    }

    @Test
    public void testTriggerToJson_fullSchemaHappyPath() throws JSONException {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setTriggerTime(TriggerFixture.ValidTriggerParams.TRIGGER_TIME)
                        .setDebugKey(TriggerFixture.ValidTriggerParams.DEBUG_KEY)
                        .setAttributionDestination(
                                TriggerFixture.ValidTriggerParams.ATTRIBUTION_DESTINATION)
                        .setRegistrationOrigin(
                                TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN)
                        .setDebugKey(TriggerFixture.ValidTriggerParams.DEBUG_KEY)
                        .setRegistrant(TriggerFixture.ValidTriggerParams.REGISTRANT)
                        .setAggregatableSourceRegistrationTimeConfig(
                                TriggerFixture.ValidTriggerParams
                                        .AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG)
                        .setAggregateDebugReportingString(
                                TriggerFixture.ValidTriggerParams.AGGREGATE_DEBUG_REPORT)
                        .setAggregateDeduplicationKeys(
                                TriggerFixture.ValidTriggerParams.AGGREGATE_DEDUPLICATION_KEYS)
                        .setAggregatableFilteringIdMaxBytes(
                                TriggerFixture.ValidTriggerParams
                                        .AGGREGATABLE_FILTERING_ID_MAX_BYTES)
                        .setAggregateTriggerData(
                                TriggerFixture.ValidTriggerParams.AGGREGATE_TRIGGER_DATA)
                        .setAggregateValuesString(
                                TriggerFixture.ValidTriggerParams.AGGREGATE_VALUES_STRING)
                        .setAggregationCoordinatorOrigin(
                                TriggerFixture.ValidTriggerParams.AGGREGATION_COORDINATOR_ORIGIN)
                        .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                        .build();

        JSONObject jsonObject = AttributionReportingHelper.triggerToJson(trigger, SCHEMA_FULL);

        expect.withMessage("TRIGGER_TIME")
                .that(jsonObject.getLong(TriggerContract.TRIGGER_TIME))
                .isEqualTo(trigger.getTriggerTime());
        expect.withMessage("ATTRIBUTION_DESTINATION")
                .that(jsonObject.getString(TriggerContract.ATTRIBUTION_DESTINATION))
                .isEqualTo(trigger.getAttributionDestination().toString());
        expect.withMessage("REGISTRATION_ORIGIN")
                .that(jsonObject.getString(TriggerContract.REGISTRATION_ORIGIN))
                .isEqualTo(trigger.getRegistrationOrigin().toString());
        expect.withMessage("TRIGGER_TIME")
                .that(jsonObject.getLong(TriggerContract.TRIGGER_TIME))
                .isEqualTo(trigger.getTriggerTime());
        String debugKeyString = jsonObject.getString(TriggerContract.DEBUG_KEY);
        expect.withMessage("DEBUG_KEY")
                .that(debugKeyString)
                .isEqualTo(trigger.getDebugKey().toString());

        String aggregatableDebugReporting =
                replaceWithAggregatable(TriggerContract.AGGREGATE_DEBUG_REPORTING);
        expect.withMessage("AGGREGATABLE_DEBUG_REPORT")
                .that(jsonObject.getString(aggregatableDebugReporting))
                .isEqualTo(trigger.getAggregateDebugReportingString());
        expect.withMessage("AGGREGATE_DEDUPLICATION_KEYS")
                .that(jsonObject.getString(TriggerContract.AGGREGATABLE_DEDUPLICATION_KEYS))
                .isEqualTo(trigger.getAggregateDeduplicationKeys());
        expect.withMessage("AGGREGATABLE_FILTERING_ID_MAX_BYTES")
                .that(jsonObject.getInt(TriggerContract.AGGREGATABLE_FILTERING_ID_MAX_BYTES))
                .isEqualTo(trigger.getAggregatableFilteringIdMaxBytes());
        String aggregatableTriggerData =
                replaceWithAggregatable(TriggerContract.AGGREGATE_TRIGGER_DATA);
        expect.withMessage("AGGREGATABLE_TRIGGER_DATA")
                .that(jsonObject.getString(aggregatableTriggerData))
                .isEqualTo(trigger.getAggregateTriggerData());
        String aggregatableValues = replaceWithAggregatable(TriggerContract.AGGREGATE_VALUES);
        expect.withMessage("AGGREGATABLE_VALUES_STRING")
                .that(jsonObject.getString(aggregatableValues))
                .isEqualTo(trigger.getAggregateValuesString());
        expect.withMessage("AGGREGATION_COORDINATOR_ORIGIN")
                .that(jsonObject.getString(TriggerContract.AGGREGATION_COORDINATOR_ORIGIN))
                .isEqualTo(trigger.getAggregationCoordinatorOrigin().toString());
        expect.withMessage("EVENT_TRIGGERS")
                .that(jsonObject.getString(TriggerContract.EVENT_TRIGGERS))
                .isEqualTo(trigger.getEventTriggers());
    }

    @Test
    public void testEventReportToJson_happyPath() throws JSONException {
        EventReport eventReport =
                EventReportFixture.getBaseEventReportBuild()
                        .setId("Event1")
                        .setSourceId("S1")
                        .setTriggerId(null)
                        .build();

        JSONObject jsonObject =
                AttributionReportingHelper.eventReportToJson(eventReport, SCHEMA_PARTIAL);

        expect.withMessage("STATUS")
                .that(jsonObject.getInt(EventReportContract.STATUS))
                .isEqualTo(eventReport.getStatus());
        expect.withMessage("ATTRIBUTION_DESTINATION")
                .that(jsonObject.getString(EventReportContract.ATTRIBUTION_DESTINATION))
                .isEqualTo(eventReport.getAttributionDestinations().toString());
        expect.withMessage("TRIGGER_TIME")
                .that(jsonObject.getLong(EventReportContract.TRIGGER_TIME))
                .isEqualTo(eventReport.getTriggerTime());
        expect.withMessage("REPORT_TIME")
                .that(jsonObject.getLong(EventReportContract.REPORT_TIME))
                .isEqualTo(eventReport.getReportTime());
        expect.withMessage("TRIGGER_PRIORITY")
                .that(jsonObject.getLong(EventReportContract.TRIGGER_PRIORITY))
                .isEqualTo(eventReport.getTriggerPriority());
        expect.withMessage("RANDOMIZED_TRIGGER_RATE")
                .that(jsonObject.getDouble(EventReportContract.RANDOMIZED_TRIGGER_RATE))
                .isEqualTo(eventReport.getRandomizedTriggerRate());
        expect.withMessage("RANDOMIZED").that(jsonObject.getBoolean(RANDOMIZED)).isEqualTo(true);
        expect.withMessage("REGISTRATION_ORIGIN")
                .that(jsonObject.getString(EventReportContract.REGISTRATION_ORIGIN))
                .isEqualTo(eventReport.getRegistrationOrigin().toString());
    }

    @Test
    public void testEventReportToJson_fullSchemaHappyPath() throws JSONException {
        EventReport eventReport =
                EventReportFixture.getBaseEventReportBuild()
                        .setId("Event1")
                        .setSourceId("S1")
                        .setTriggerId(null)
                        .setTriggerData(new UnsignedLong(1L))
                        .build();

        JSONObject jsonObject =
                AttributionReportingHelper.eventReportToJson(eventReport, SCHEMA_FULL);

        expect.withMessage("STATUS")
                .that(jsonObject.getInt(EventReportContract.STATUS))
                .isEqualTo(eventReport.getStatus());
        expect.withMessage("ATTRIBUTION_DESTINATION")
                .that(jsonObject.getString(EventReportContract.ATTRIBUTION_DESTINATION))
                .isEqualTo(eventReport.getAttributionDestinations().toString());
        expect.withMessage("TRIGGER_TIME")
                .that(jsonObject.getLong(EventReportContract.TRIGGER_TIME))
                .isEqualTo(eventReport.getTriggerTime());
        expect.withMessage("REPORT_TIME")
                .that(jsonObject.getLong(EventReportContract.REPORT_TIME))
                .isEqualTo(eventReport.getReportTime());
        expect.withMessage("TRIGGER_PRIORITY")
                .that(jsonObject.getLong(EventReportContract.TRIGGER_PRIORITY))
                .isEqualTo(eventReport.getTriggerPriority());
        expect.withMessage("RANDOMIZED_TRIGGER_RATE")
                .that(jsonObject.getDouble(EventReportContract.RANDOMIZED_TRIGGER_RATE))
                .isEqualTo(eventReport.getRandomizedTriggerRate());
        expect.withMessage("RANDOMIZED").that(jsonObject.getBoolean(RANDOMIZED)).isEqualTo(true);
        expect.withMessage("REGISTRATION_ORIGIN").that(
                jsonObject.getString(EventReportContract.REGISTRATION_ORIGIN)).isEqualTo(
                eventReport.getRegistrationOrigin().toString());

        expect.withMessage("ID")
                .that(jsonObject.getString(EventReportContract.ID))
                .isEqualTo(eventReport.getId());
        expect.withMessage("SOURCE_DEBUG_KEY")
                .that(jsonObject.getString(EventReportContract.SOURCE_DEBUG_KEY))
                .isEqualTo(eventReport.getSourceDebugKey().toString());
        expect.withMessage("SOURCE_EVENT_ID")
                .that(jsonObject.getString(EventReportContract.SOURCE_EVENT_ID))
                .isEqualTo(eventReport.getSourceEventId().toString());
        expect.withMessage("SOURCE_TYPE")
                .that(jsonObject.getString(EventReportContract.SOURCE_TYPE))
                .isEqualTo(eventReport.getSourceType().getValue());
        expect.withMessage("TRIGGER_DATA")
                .that(jsonObject.getString(EventReportContract.TRIGGER_DATA))
                .isEqualTo(eventReport.getTriggerData().toString());
        expect.withMessage("TRIGGER_DEBUG_KEY")
                .that(jsonObject.getString(EventReportContract.TRIGGER_DEBUG_KEY))
                .isEqualTo(eventReport.getTriggerDebugKey().toString());
    }

    @Test
    public void testAggregatableReportToJson_happyPath() throws JSONException {
        AggregateReport aggregatableReport =
                AggregateReportFixture.getValidAggregateReportBuilder().build();

        JSONObject jsonObject =
                AttributionReportingHelper.aggregatableReportToJson(
                        aggregatableReport, SCHEMA_PARTIAL);

        expect.withMessage("STATUS")
                .that(jsonObject.getInt(MeasurementTables.AggregateReport.STATUS))
                .isEqualTo(aggregatableReport.getStatus());
        expect.withMessage("ATTRIBUTION_DESTINATION")
                .that(
                        jsonObject.getString(
                                MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION))
                .isEqualTo(aggregatableReport.getAttributionDestination().toString());
        expect.withMessage("TRIGGER_TIME")
                .that(jsonObject.getLong(MeasurementTables.AggregateReport.TRIGGER_TIME))
                .isEqualTo(aggregatableReport.getTriggerTime());
        expect.withMessage("SCHEDULED_REPORT_TIME")
                .that(jsonObject.getLong(MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME))
                .isEqualTo(aggregatableReport.getScheduledReportTime());
        expect.withMessage("AGGREGATION_COORDINATOR_ORIGIN")
                .that(
                        jsonObject.getString(
                                MeasurementTables.AggregateReport.AGGREGATION_COORDINATOR_ORIGIN))
                .isEqualTo(aggregatableReport.getAggregationCoordinatorOrigin().toString());
        expect.withMessage("DEBUG_CLEARTEXT_PAYLOAD")
                .that(
                        jsonObject.getString(
                                MeasurementTables.AggregateReport.DEBUG_CLEARTEXT_PAYLOAD))
                .isEqualTo(aggregatableReport.getDebugCleartextPayload());
        expect.withMessage("REGISTRATION_ORIGIN")
                .that(jsonObject.getString(MeasurementTables.AggregateReport.REGISTRATION_ORIGIN))
                .isEqualTo(aggregatableReport.getRegistrationOrigin().toString());
        expect.withMessage("TRIGGER_CONTEXT_ID")
                .that(jsonObject.getString(MeasurementTables.AggregateReport.TRIGGER_CONTEXT_ID))
                .isEqualTo(aggregatableReport.getTriggerContextId());
    }

    @Test
    public void testAggregatableReportToJson_fullSchemaHappyPath() throws JSONException {
        AggregateReport aggregatableReport =
                AggregateReportFixture.getValidAggregateReportBuilder()
                        .setApiVersion(
                                AggregateReportFixture.ValidAggregateReportParams.API_VERSION)
                        .build();

        JSONObject jsonObject =
                AttributionReportingHelper.aggregatableReportToJson(
                        aggregatableReport, SCHEMA_FULL);

        expect.withMessage("STATUS")
                .that(jsonObject.getInt(MeasurementTables.AggregateReport.STATUS))
                .isEqualTo(aggregatableReport.getStatus());
        expect.withMessage("ATTRIBUTION_DESTINATION")
                .that(
                        jsonObject.getString(
                                MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION))
                .isEqualTo(aggregatableReport.getAttributionDestination().toString());
        expect.withMessage("TRIGGER_TIME")
                .that(jsonObject.getLong(MeasurementTables.AggregateReport.TRIGGER_TIME))
                .isEqualTo(aggregatableReport.getTriggerTime());
        expect.withMessage("SCHEDULED_REPORT_TIME")
                .that(jsonObject.getLong(MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME))
                .isEqualTo(aggregatableReport.getScheduledReportTime());
        expect.withMessage("AGGREGATION_COORDINATOR_ORIGIN")
                .that(
                        jsonObject.getString(
                                MeasurementTables.AggregateReport.AGGREGATION_COORDINATOR_ORIGIN))
                .isEqualTo(aggregatableReport.getAggregationCoordinatorOrigin().toString());
        expect.withMessage("DEBUG_CLEARTEXT_PAYLOAD")
                .that(
                        jsonObject.getString(
                                MeasurementTables.AggregateReport.DEBUG_CLEARTEXT_PAYLOAD))
                .isEqualTo(aggregatableReport.getDebugCleartextPayload());
        expect.withMessage("REGISTRATION_ORIGIN")
                .that(jsonObject.getString(
                        MeasurementTables.AggregateReport.REGISTRATION_ORIGIN))
                .isEqualTo(aggregatableReport.getRegistrationOrigin().toString());
        expect.withMessage("TRIGGER_CONTEXT_ID")
                .that(jsonObject.getString(MeasurementTables.AggregateReport.TRIGGER_CONTEXT_ID))
                .isEqualTo(aggregatableReport.getTriggerContextId());

        expect.withMessage("API")
                .that(jsonObject.getString(MeasurementTables.AggregateReport.API))
                .isEqualTo(aggregatableReport.getApi());
        expect.withMessage("ID")
                .that(jsonObject.getString(MeasurementTables.AggregateReport.ID))
                .isEqualTo(aggregatableReport.getId());
        expect.withMessage("DEBUG_REPORT_STATUS")
                .that(jsonObject.getInt(MeasurementTables.AggregateReport.DEBUG_REPORT_STATUS))
                .isEqualTo(aggregatableReport.getDebugReportStatus());
        expect.withMessage("PUBLISHER")
                .that(jsonObject.getString(MeasurementTables.AggregateReport.PUBLISHER))
                .isEqualTo(aggregatableReport.getPublisher().toString());
        expect.withMessage("API_VERSION")
                .that(jsonObject.getString(MeasurementTables.AggregateReport.API_VERSION))
                .isEqualTo(aggregatableReport.getApiVersion());
        expect.withMessage("SOURCE_DEBUG_KEY")
                .that(jsonObject.getString(MeasurementTables.AggregateReport.SOURCE_DEBUG_KEY))
                .isEqualTo(aggregatableReport.getSourceDebugKey().toString());
        expect.withMessage("TRIGGER_DEBUG_KEY")
                .that(jsonObject.getString(MeasurementTables.AggregateReport.TRIGGER_DEBUG_KEY))
                .isEqualTo(aggregatableReport.getTriggerDebugKey().toString());
    }

    @Test
    public void testDebugReportToJson_happyPath() throws JSONException {
        DebugReport debugReport =
                new DebugReport.Builder()
                        .setId("report1")
                        .setType(DebugReportFixture.ValidDebugReportParams.TYPE)
                        .setBody(DebugReportFixture.ValidDebugReportParams.BODY)
                        .setEnrollmentId(DebugReportFixture.ValidDebugReportParams.ENROLLMENT_ID)
                        .setRegistrationOrigin(
                                DebugReportFixture.ValidDebugReportParams.REGISTRATION_ORIGIN)
                        .setRegistrant(DebugReportFixture.ValidDebugReportParams.REGISTRANT)
                        .setInsertionTime(DebugReportFixture.ValidDebugReportParams.INSERTION_TIME)
                        .build();

        JSONObject jsonObject = AttributionReportingHelper.debugReportToJson(debugReport, "");

        expect.withMessage("INSERTION_TIME")
                .that(jsonObject.getLong(MeasurementTables.DebugReportContract.INSERTION_TIME))
                .isEqualTo(debugReport.getInsertionTime());
        expect.withMessage("REGISTRATION_ORIGIN")
                .that(
                        jsonObject.getString(
                                MeasurementTables.DebugReportContract.REGISTRATION_ORIGIN))
                .isEqualTo(debugReport.getRegistrationOrigin().toString());
        expect.withMessage("TYPE")
                .that(jsonObject.getString(MeasurementTables.DebugReportContract.TYPE))
                .isEqualTo(debugReport.getType());
    }

    @Test
    public void testDebugReportToJson_fullSchemaHappyPath() throws JSONException {
        DebugReport debugReport =
                new DebugReport.Builder()
                        .setId("report1")
                        .setType(DebugReportFixture.ValidDebugReportParams.TYPE)
                        .setBody(DebugReportFixture.ValidDebugReportParams.BODY)
                        .setEnrollmentId(DebugReportFixture.ValidDebugReportParams.ENROLLMENT_ID)
                        .setRegistrationOrigin(
                                DebugReportFixture.ValidDebugReportParams.REGISTRATION_ORIGIN)
                        .setRegistrant(DebugReportFixture.ValidDebugReportParams.REGISTRANT)
                        .setInsertionTime(DebugReportFixture.ValidDebugReportParams.INSERTION_TIME)
                        .build();

        JSONObject jsonObject =
                AttributionReportingHelper.debugReportToJson(debugReport, SCHEMA_FULL);

        expect.withMessage("INSERTION_TIME")
                .that(jsonObject.getLong(MeasurementTables.DebugReportContract.INSERTION_TIME))
                .isEqualTo(debugReport.getInsertionTime());
        expect.withMessage("REGISTRATION_ORIGIN")
                .that(
                        jsonObject.getString(
                                MeasurementTables.DebugReportContract.REGISTRATION_ORIGIN))
                .isEqualTo(debugReport.getRegistrationOrigin().toString());
        expect.withMessage("TYPE")
                .that(jsonObject.getString(MeasurementTables.DebugReportContract.TYPE))
                .isEqualTo(debugReport.getType());
        expect.withMessage("BODY")
                .that(jsonObject.getString(MeasurementTables.DebugReportContract.BODY))
                .isEqualTo(debugReport.getBody().toString());
    }
}
