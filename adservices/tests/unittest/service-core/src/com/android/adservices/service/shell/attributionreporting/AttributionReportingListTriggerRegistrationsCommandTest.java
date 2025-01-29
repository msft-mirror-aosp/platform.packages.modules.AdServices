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

package com.android.adservices.service.shell.attributionreporting;

import static com.android.adservices.service.shell.attributionreporting.AttributionReportingHelper.replaceWithAggregatable;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_ATTRIBUTION_REPORTING_LIST_TRIGGER_REGISTRATIONS;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_DEV_MODE_UNCONFIRMED;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.net.Uri;

import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.MeasurementTables.TriggerContract;
import com.android.adservices.devapi.DevSessionFixture;
import com.android.adservices.service.devapi.DevSession;
import com.android.adservices.service.devapi.DevSessionDataStore;
import com.android.adservices.service.devapi.DevSessionState;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.TriggerFixture;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.shell.ShellCommandTestCase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

public class AttributionReportingListTriggerRegistrationsCommandTest
        extends ShellCommandTestCase<AttributionReportingListTriggerRegistrationsCommand> {
    private static final String TRIGGER_TIME = "trigger_time";
    private static final String ATTRIBUTION_DESTINATION = "attribution_destination";
    private static final String REGISTRATION_ORIGIN = "registration_origin";
    private static final String DEBUG_KEY = "debug_key";
    private static final String SCHEMA_FULL = "full";
    private static final String SCHEMA_PARTIAL = "partial";
    private static final String SCHEMA_SUB_COMMAND = "--schema";
    DatastoreManager mDatastoreManager = Mockito.mock(DatastoreManager.class);
    @Mock private DevSessionDataStore mDevSessionDataStore;
    private static Trigger trigger1 =
            TriggerFixture.getValidTriggerBuilder()
                    .setEnrollmentId("trigger1")
                    .setTriggerTime(TriggerFixture.ValidTriggerParams.TRIGGER_TIME)
                    .setAttributionDestination(
                            TriggerFixture.ValidTriggerParams.ATTRIBUTION_DESTINATION)
                    .setRegistrationOrigin(TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN)
                    .setDebugKey(TriggerFixture.ValidTriggerParams.DEBUG_KEY)
                    .setRegistrant(TriggerFixture.ValidTriggerParams.REGISTRANT)
                    .setAggregatableSourceRegistrationTimeConfig(
                            TriggerFixture.ValidTriggerParams
                                    .AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG)
                    .build();

    private static Trigger trigger2 =
            TriggerFixture.getValidTriggerBuilder()
                    .setEnrollmentId("trigger2")
                    .setTriggerTime(TriggerFixture.ValidTriggerParams.TRIGGER_TIME)
                    .setAttributionDestination(
                            TriggerFixture.ValidTriggerParams.ATTRIBUTION_DESTINATION)
                    .setRegistrationOrigin(TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN)
                    .setDebugKey(TriggerFixture.ValidTriggerParams.DEBUG_KEY)
                    .setRegistrant(TriggerFixture.ValidTriggerParams.REGISTRANT)
                    .setAggregatableSourceRegistrationTimeConfig(
                            TriggerFixture.ValidTriggerParams
                                    .AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG)
                    .build();

    private static Trigger trigger3 =
            TriggerFixture.getValidTriggerBuilder()
                    .setEnrollmentId("trigger3")
                    .setTriggerTime(TriggerFixture.ValidTriggerParams.TRIGGER_TIME)
                    .setAttributionDestination(
                            TriggerFixture.ValidTriggerParams.ATTRIBUTION_DESTINATION)
                    .setRegistrationOrigin(TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN)
                    .setDebugKey(TriggerFixture.ValidTriggerParams.DEBUG_KEY)
                    .setRegistrant(TriggerFixture.ValidTriggerParams.REGISTRANT)
                    .setAggregatableSourceRegistrationTimeConfig(
                            TriggerFixture.ValidTriggerParams
                                    .AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG)
                    .build();

    private static Trigger trigger4 =
            TriggerFixture.getValidTriggerBuilder()
                    .setEnrollmentId("trigger1")
                    .setTriggerTime(TriggerFixture.ValidTriggerParams.TRIGGER_TIME)
                    .setAttributionDestination(
                            TriggerFixture.ValidTriggerParams.ATTRIBUTION_DESTINATION)
                    .setRegistrationOrigin(TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN)
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
                            TriggerFixture.ValidTriggerParams.AGGREGATABLE_FILTERING_ID_MAX_BYTES)
                    .setAggregateTriggerData(
                            TriggerFixture.ValidTriggerParams.AGGREGATE_TRIGGER_DATA)
                    .setAggregateValuesString(
                            TriggerFixture.ValidTriggerParams.AGGREGATE_VALUES_STRING)
                    .setAggregationCoordinatorOrigin(
                            TriggerFixture.ValidTriggerParams.AGGREGATION_COORDINATOR_ORIGIN)
                    .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                    .build();

    private static Trigger trigger5 =
            TriggerFixture.getValidTriggerBuilder()
                    .setEnrollmentId("trigger2")
                    .setTriggerTime(TriggerFixture.ValidTriggerParams.TRIGGER_TIME)
                    .setAttributionDestination(
                            TriggerFixture.ValidTriggerParams.ATTRIBUTION_DESTINATION)
                    .setRegistrationOrigin(TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN)
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
                            TriggerFixture.ValidTriggerParams.AGGREGATABLE_FILTERING_ID_MAX_BYTES)
                    .setAggregateTriggerData(
                            TriggerFixture.ValidTriggerParams.AGGREGATE_TRIGGER_DATA)
                    .setAggregateValuesString(
                            TriggerFixture.ValidTriggerParams.AGGREGATE_VALUES_STRING)
                    .setAggregationCoordinatorOrigin(
                            TriggerFixture.ValidTriggerParams.AGGREGATION_COORDINATOR_ORIGIN)
                    .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                    .build();

    @Before
    public void setUp() {
        when(mDevSessionDataStore.get()).thenReturn(immediateFuture(DevSessionFixture.IN_DEV));

        when(mDevSessionDataStore.get())
                .thenReturn(
                        immediateFuture(
                                DevSession.builder().setState(DevSessionState.IN_DEV).build()));
    }

    @Test
    public void testRunListTriggerRegistrations_outsideDevSessionError() {
        when(mDevSessionDataStore.get())
                .thenReturn(
                        immediateFuture(
                                DevSession.builder().setState(DevSessionState.IN_PROD).build()));

        Result result = runCommandAndGetResult();

        expect.that(result.mOut).isEmpty();
        expect.that(result.mResultCode).isEqualTo(RESULT_DEV_MODE_UNCONFIRMED);
    }

    @Test
    public void testRunListTriggerRegistrations_transitioningError() {
        when(mDevSessionDataStore.get())
                .thenReturn(
                        immediateFuture(
                                DevSession.builder()
                                        .setState(DevSessionState.TRANSITIONING_PROD_TO_DEV)
                                        .build()));

        Result result = runCommandAndGetResult();

        expect.that(result.mOut).isEmpty();
        expect.that(result.mResultCode).isEqualTo(RESULT_DEV_MODE_UNCONFIRMED);
    }

    @Test
    public void testRunListTriggerRegistrations_pass() throws JSONException {
        doReturn(Optional.ofNullable(List.of(trigger1, trigger2, trigger3)))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());

        Result result = runCommandAndGetResult();

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_TRIGGER_REGISTRATIONS);

        JSONObject jsonOutput = new JSONObject(result.mOut);
        JSONArray registrationsArray = jsonOutput.getJSONArray("attribution_reporting");

        List<Trigger> expectedTriggers = List.of(trigger1, trigger2, trigger3);

        for (int i = 0; i < registrationsArray.length(); i++) {
            String triggerEnrollmentId = "trigger" + (i + 1);
            JSONObject registrationsObject = registrationsArray.getJSONObject(i);
            Trigger outputTrigger =
                    getTriggerFromJson(
                            registrationsArray.getJSONObject(i), triggerEnrollmentId, "");
            assertThat(outputTrigger).isEqualTo(expectedTriggers.get(i));
            assertTriggerJson(registrationsObject, outputTrigger, SCHEMA_PARTIAL);
        }
    }

    @Test
    public void testRunListTriggerRegistrations_emptyListTriggers() throws JSONException {
        doReturn(Optional.ofNullable(List.of()))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());

        Result result = runCommandAndGetResult();

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_TRIGGER_REGISTRATIONS);

        JSONObject jsonOutput = new JSONObject(result.mOut);
        JSONArray registrationsArray = jsonOutput.getJSONArray("attribution_reporting");

        assertThat(registrationsArray.length()).isEqualTo(0);
    }

    @Test
    public void testRunListTriggerRegistrations_nullTriggersJson() {
        doReturn(Optional.empty()).when(mDatastoreManager).runInTransactionWithResult(any());

        Result result = runCommandAndGetResult();

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_TRIGGER_REGISTRATIONS);

        assertThat(result.mOut).isEqualTo("Error in retrieving triggers from database");
    }

    @Test
    public void testRunListTriggerRegistrations_singleTriggerPartialSchemaJson()
            throws JSONException {
        String[] args = {SCHEMA_SUB_COMMAND, SCHEMA_PARTIAL};
        List<Trigger> triggers = List.of(trigger1);
        testRunListTriggerRegistrationsWithSchema(triggers, args);
    }

    @Test
    public void testRunListTriggerRegistrations_multipleTriggersPartialSchemaJson()
            throws JSONException {
        String[] args = {SCHEMA_SUB_COMMAND, SCHEMA_PARTIAL};
        List<Trigger> sources = List.of(trigger1, trigger2);
        testRunListTriggerRegistrationsWithSchema(sources, args);
    }

    @Test
    public void testRunListTriggerRegistrations_singleTriggerFullSchemaJson() throws JSONException {
        String[] args = {SCHEMA_SUB_COMMAND, SCHEMA_FULL};
        List<Trigger> triggers = List.of(trigger4);
        testRunListTriggerRegistrationsWithSchema(triggers, args);
    }

    @Test
    public void testRunListTriggerRegistrations_multipleTriggersFullSchemaJson()
            throws JSONException {
        String[] args = {SCHEMA_SUB_COMMAND, SCHEMA_FULL};
        List<Trigger> sources = List.of(trigger4, trigger5);
        testRunListTriggerRegistrationsWithSchema(sources, args);
    }

    private void testRunListTriggerRegistrationsWithSchema(List<Trigger> triggers, String[] schema)
            throws JSONException {
        doReturn(Optional.ofNullable(triggers))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());

        Result result = runCommandAndGetResult(schema);

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_TRIGGER_REGISTRATIONS);

        JSONObject jsonOutput = new JSONObject(result.mOut);
        JSONArray registrationsArray = jsonOutput.getJSONArray("attribution_reporting");

        for (int i = 0; i < registrationsArray.length(); i++) {
            String triggerEnrollmentId = "trigger" + (i + 1);
            JSONObject registrationsObject = registrationsArray.getJSONObject(i);
            Trigger outputTrigger =
                    getTriggerFromJson(
                            registrationsArray.getJSONObject(i), triggerEnrollmentId, schema[1]);
            assertThat(outputTrigger).isEqualTo(triggers.get(i));
            assertTriggerJson(registrationsObject, outputTrigger, schema[1]);
        }
    }

    private Result runCommandAndGetResult(String[] args) {
        String[] stringArray = new String[2 + args.length];
        stringArray[0] = AttributionReportingShellCommandFactory.COMMAND_PREFIX;
        stringArray[1] = AttributionReportingListTriggerRegistrationsCommand.CMD;
        for (int i = 0; i < args.length; i++) {
            stringArray[i + 1] = args[i];
        }
        return run(
                new AttributionReportingListTriggerRegistrationsCommand(
                        mDatastoreManager, mDevSessionDataStore),
                stringArray);
    }

    private Result runCommandAndGetResult() {
        return run(
                new AttributionReportingListTriggerRegistrationsCommand(
                        mDatastoreManager, mDevSessionDataStore),
                AttributionReportingShellCommandFactory.COMMAND_PREFIX,
                AttributionReportingListTriggerRegistrationsCommand.CMD);
    }

    /** Creates a Trigger.Builder from JSON. Missing fields are populated with default values. */
    private static Trigger getTriggerFromJson(
            JSONObject jsonObject, String enrollmentId, String schema) throws JSONException {
        Trigger.Builder builder =
                new Trigger.Builder()
                        .setEnrollmentId(enrollmentId)
                        .setTriggerTime(jsonObject.getLong(TRIGGER_TIME))
                        .setAttributionDestination(
                                Uri.parse(jsonObject.getString(ATTRIBUTION_DESTINATION)))
                        .setRegistrationOrigin(
                                Uri.parse(jsonObject.getString((REGISTRATION_ORIGIN))))
                        .setDebugKey(new UnsignedLong(jsonObject.getLong(DEBUG_KEY)))
                        .setRegistrant(TriggerFixture.ValidTriggerParams.REGISTRANT)
                        .setAggregatableSourceRegistrationTimeConfig(
                                TriggerFixture.ValidTriggerParams
                                        .AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG);

        if (schema.equals(SCHEMA_FULL)) {
            String aggregatableDebugReporting =
                    replaceWithAggregatable(TriggerContract.AGGREGATE_DEBUG_REPORTING);
            String aggregatableTriggerData =
                    replaceWithAggregatable(TriggerContract.AGGREGATE_TRIGGER_DATA);
            String aggregatableValues = replaceWithAggregatable(TriggerContract.AGGREGATE_VALUES);

            builder.setAggregateDebugReportingString(
                            jsonObject.getString(aggregatableDebugReporting))
                    .setAggregateDeduplicationKeys(
                            jsonObject.getString(TriggerContract.AGGREGATABLE_DEDUPLICATION_KEYS))
                    .setAggregatableFilteringIdMaxBytes(
                            jsonObject.getInt(TriggerContract.AGGREGATABLE_FILTERING_ID_MAX_BYTES))
                    .setAggregateTriggerData(jsonObject.getString(aggregatableTriggerData))
                    .setAggregateValuesString(jsonObject.getString(aggregatableValues))
                    .setAggregationCoordinatorOrigin(
                            Uri.parse(
                                    jsonObject.getString(
                                            TriggerContract.AGGREGATION_COORDINATOR_ORIGIN)))
                    .setEventTriggers(jsonObject.getString(TriggerContract.EVENT_TRIGGERS));
        }
        return builder.build();
    }

    private void assertTriggerJson(JSONObject triggerJson, Trigger trigger, String schema)
            throws JSONException {
        assertThat(triggerJson.getLong(TRIGGER_TIME)).isEqualTo(trigger.getTriggerTime());
        assertThat(triggerJson.getString(ATTRIBUTION_DESTINATION))
                .isEqualTo(trigger.getAttributionDestination().toString());
        assertThat(triggerJson.getString(REGISTRATION_ORIGIN))
                .isEqualTo(trigger.getRegistrationOrigin().toString());
        assertThat(triggerJson.getString(DEBUG_KEY)).isEqualTo(trigger.getDebugKey().toString());

        if (schema.equals(SCHEMA_FULL)) {
            String aggregatableDebugReporting =
                    replaceWithAggregatable(TriggerContract.AGGREGATE_DEBUG_REPORTING);
            String aggregatableTriggerData =
                    replaceWithAggregatable(TriggerContract.AGGREGATE_TRIGGER_DATA);
            String aggregatableValues = replaceWithAggregatable(TriggerContract.AGGREGATE_VALUES);

            assertThat(triggerJson.getString(aggregatableDebugReporting))
                    .isEqualTo(trigger.getAggregateDebugReportingString());
            assertThat(triggerJson.getString(TriggerContract.AGGREGATABLE_DEDUPLICATION_KEYS))
                    .isEqualTo(trigger.getAggregateDeduplicationKeys());
            assertThat(triggerJson.getInt(TriggerContract.AGGREGATABLE_FILTERING_ID_MAX_BYTES))
                    .isEqualTo(trigger.getAggregatableFilteringIdMaxBytes());
            assertThat(triggerJson.getString(aggregatableTriggerData))
                    .isEqualTo(trigger.getAggregateTriggerData());
            assertThat(triggerJson.getString(aggregatableValues))
                    .isEqualTo(trigger.getAggregateValuesString());
            assertThat(triggerJson.getString(TriggerContract.AGGREGATION_COORDINATOR_ORIGIN))
                    .isEqualTo(trigger.getAggregationCoordinatorOrigin().toString());
            assertThat(triggerJson.getString(TriggerContract.EVENT_TRIGGERS))
                    .isEqualTo(trigger.getEventTriggers());
        } else if (schema.equals(SCHEMA_PARTIAL)) {
            assertThat(triggerJson.has(TriggerContract.AGGREGATE_DEBUG_REPORTING)).isFalse();
            assertThat(triggerJson.has(TriggerContract.AGGREGATABLE_DEDUPLICATION_KEYS)).isFalse();
            assertThat(triggerJson.has(TriggerContract.AGGREGATABLE_FILTERING_ID_MAX_BYTES))
                    .isFalse();
            assertThat(triggerJson.has(TriggerContract.AGGREGATE_TRIGGER_DATA)).isFalse();
            assertThat(triggerJson.has(TriggerContract.AGGREGATE_VALUES)).isFalse();
            assertThat(triggerJson.has(TriggerContract.AGGREGATION_COORDINATOR_ORIGIN)).isFalse();
            assertThat(triggerJson.has(TriggerContract.EVENT_TRIGGERS)).isFalse();
        }
    }
}
