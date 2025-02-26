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

import static com.android.adservices.service.shell.attributionreporting.AttributionReportingHelper.STATUS_MAP;
import static com.android.adservices.service.shell.attributionreporting.AttributionReportingHelper.replaceWithAggregatable;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_ATTRIBUTION_REPORTING_LIST_SOURCE_REGISTRATIONS;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_DEV_MODE_UNCONFIRMED;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_GENERIC_ERROR;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.net.Uri;

import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.MeasurementTables.SourceContract;
import com.android.adservices.devapi.DevSessionFixture;
import com.android.adservices.service.devapi.DevSession;
import com.android.adservices.service.devapi.DevSessionDataStore;
import com.android.adservices.service.devapi.DevSessionState;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.shell.ShellCommandTestCase;

import com.google.common.collect.ImmutableMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AttributionReportingListSourceRegistrationsCommandTest
        extends ShellCommandTestCase<AttributionReportingListSourceRegistrationsCommand> {
    private static final String EVENT_ID = "event_id";
    private static final String STATUS = "status";
    private static final String REGISTRATION_ORIGIN = "registration_origin";
    private static final String REGISTRANT = "registrant";
    private static final String EVENT_TIME = "event_time";
    private static final String EXPIRY_TIME = "expiry_time";
    private static final String DEBUG_KEY = "debug_key";
    private static final String APP_DESTINATION = "app_destination";
    private static final String WEB_DESTINATION = "web_destination";
    private static final String ATTRIBUTION_MODE = "attribution_mode";
    private static final String ACTIVE = "active";
    private static final String IGNORED = "ignored";
    private static final String MARKED_TO_DELETE = "marked_to_delete";
    private static final String SCHEMA_FULL = "full";
    private static final String SCHEMA_PARTIAL = "partial";
    private static final String SCHEMA_SUB_COMMAND = "--schema";
    DatastoreManager mDatastoreManager = Mockito.mock(DatastoreManager.class);
    @Mock
    private DevSessionDataStore mDevSessionDataStore;

    public static List<Uri> multipleWebDestinations =
            List.of(Uri.parse("https://destination.test"), Uri.parse("https://destination2.test"));

    private static Source source1 =
            SourceFixture.getMinimalValidSourceBuilder()
                    .setEventId(new UnsignedLong(1L))
                    .setStatus(SourceFixture.ValidSourceParams.STATUS)
                    .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                    .setEventTime(SourceFixture.ValidSourceParams.SOURCE_EVENT_TIME)
                    .setExpiryTime(SourceFixture.ValidSourceParams.EXPIRY_TIME)
                    .setDebugKey(SourceFixture.ValidSourceParams.DEBUG_KEY)
                    .build();

    private static Source source2 =
            SourceFixture.getMinimalValidSourceBuilder()
                    .setEventId(new UnsignedLong(2L))
                    .setStatus(SourceFixture.ValidSourceParams.STATUS)
                    .setWebDestinations(multipleWebDestinations)
                    .setEventTime(SourceFixture.ValidSourceParams.SOURCE_EVENT_TIME)
                    .setExpiryTime(SourceFixture.ValidSourceParams.EXPIRY_TIME)
                    .setDebugKey(SourceFixture.ValidSourceParams.DEBUG_KEY)
                    .build();

    private static Source source3 =
            SourceFixture.getMinimalValidSourceBuilder()
                    .setEventId(new UnsignedLong(3L))
                    .setStatus(SourceFixture.ValidSourceParams.STATUS)
                    .setEventTime(SourceFixture.ValidSourceParams.SOURCE_EVENT_TIME)
                    .setExpiryTime(SourceFixture.ValidSourceParams.EXPIRY_TIME)
                    .setDebugKey(SourceFixture.ValidSourceParams.DEBUG_KEY)
                    .build();

    private static Source source4 =
            new Source.Builder()
                    .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
                    .setEnrollmentId(SourceFixture.ValidSourceParams.ENROLLMENT_ID)
                    .setRegistrant(SourceFixture.ValidSourceParams.REGISTRANT)
                    .setRegistrationOrigin(SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN)
                    .setEventId(new UnsignedLong(4L))
                    .setStatus(SourceFixture.ValidSourceParams.STATUS)
                    .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                    .setEventTime(SourceFixture.ValidSourceParams.SOURCE_EVENT_TIME)
                    .setExpiryTime(SourceFixture.ValidSourceParams.EXPIRY_TIME)
                    .setDebugKey(SourceFixture.ValidSourceParams.DEBUG_KEY)
                    .build();

    private static Source source5 =
            getValidSourceWithFullSchema().setEventId(new UnsignedLong(5L)).build();
    private static Source source6 =
            getValidSourceWithFullSchema().setEventId(new UnsignedLong(6L)).build();

    @Before
    public void setUp() {
        when(mDevSessionDataStore.get()).thenReturn(immediateFuture(DevSessionFixture.IN_DEV));

        when(mDevSessionDataStore.get())
                .thenReturn(
                        immediateFuture(
                                DevSession.builder().setState(DevSessionState.IN_DEV).build()));
    }

    @Test
    public void testRunListSourceRegistrations_outsideDevSessionError() {
        when(mDevSessionDataStore.get())
                .thenReturn(
                        immediateFuture(
                                DevSession.builder().setState(DevSessionState.IN_PROD).build()));

        Result result = runCommandAndGetResult();

        expect.that(result.mOut).isEmpty();
        expect.that(result.mResultCode).isEqualTo(RESULT_DEV_MODE_UNCONFIRMED);
    }

    @Test
    public void testRunListSourceRegistrations_transitioningError() {
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
    public void testRunListSourceRegistrations_singleSourceWithAppDestWithOneWebDestJson()
            throws JSONException {
        doReturn(Optional.ofNullable(List.of(source1)))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());

        Result result = runCommandAndGetResult();

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_SOURCE_REGISTRATIONS);

        JSONObject jsonOutput = new JSONObject(result.mOut);
        JSONArray registrationsArray = jsonOutput.getJSONArray("attribution_reporting");
        JSONObject registrationObject = registrationsArray.getJSONObject(0);

        Source outputSource = getSourceFromJson(registrationObject, "");

        assertThat(outputSource).isEqualTo(source1);
    }

    @Test
    public void testRunListSourceRegistrations_singleSourceWithAppDestWithMultipleWebDestJson()
            throws JSONException {
        doReturn(Optional.ofNullable(List.of(source2)))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());

        Result result = runCommandAndGetResult();

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_SOURCE_REGISTRATIONS);

        JSONObject jsonOutput = new JSONObject(result.mOut);
        JSONArray registrationsArray = jsonOutput.getJSONArray("attribution_reporting");
        JSONObject registrationObject = registrationsArray.getJSONObject(0);

        Source outputSource = getSourceFromJson(registrationObject, "");

        assertThat(outputSource).isEqualTo(source2);
    }

    @Test
    public void testRunListSourceRegistrations_singleSourceWithAppDestNoWebDestJson()
            throws JSONException {
        doReturn(Optional.ofNullable(List.of(source3)))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());

        Result result = runCommandAndGetResult();

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_SOURCE_REGISTRATIONS);

        JSONObject jsonOutput = new JSONObject(result.mOut);
        JSONArray registrationsArray = jsonOutput.getJSONArray("attribution_reporting");
        JSONObject registrationObject = registrationsArray.getJSONObject(0);

        Source outputSource = getSourceFromJson(registrationObject, "");

        assertThat(outputSource).isEqualTo(source3);
    }

    @Test
    public void testRunListSourceRegistrations_singleSourceNoAppDestWithOneWebDestJson()
            throws JSONException {
        doReturn(Optional.ofNullable(List.of(source4)))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());

        Result result = runCommandAndGetResult();

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_SOURCE_REGISTRATIONS);

        JSONObject jsonOutput = new JSONObject(result.mOut);
        JSONArray registrationsArray = jsonOutput.getJSONArray("attribution_reporting");
        JSONObject registrationObject = registrationsArray.getJSONObject(0);

        Source outputSource = getSourceFromJson(registrationObject, "");

        assertThat(outputSource).isEqualTo(source4);
    }

    @Test
    public void testRunListSourceRegistrations_multipleSources() throws JSONException {
        doReturn(Optional.ofNullable(List.of(source1, source2, source3, source4)))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());

        Result result = runCommandAndGetResult();

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_SOURCE_REGISTRATIONS);

        JSONObject jsonOutput = new JSONObject(result.mOut);
        JSONArray registrationsArray = jsonOutput.getJSONArray("attribution_reporting");

        List<Source> expectedSources = List.of(source1, source2, source3, source4);

        for (int i = 0; i < registrationsArray.length(); i++) {
            JSONObject registrationsObject = registrationsArray.getJSONObject(i);
            Source outputSource = getSourceFromJson(registrationsObject, "");
            assertThat(outputSource).isEqualTo(expectedSources.get(i));
            assertSourceJson(registrationsObject, outputSource, SCHEMA_PARTIAL);
        }
    }

    @Test
    public void testRunListSourceRegistrations_noSourcesJSON() throws JSONException {
        doReturn(Optional.ofNullable(List.of()))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());

        Result result = runCommandAndGetResult();

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_SOURCE_REGISTRATIONS);

        JSONObject jsonOutput = new JSONObject(result.mOut);
        JSONArray registrationsArray = jsonOutput.getJSONArray("attribution_reporting");

        assertThat(registrationsArray.length()).isEqualTo(0);
    }

    @Test
    public void testRunListSourceRegistrations_nullSourcesJSON() {
        doReturn(Optional.empty()).when(mDatastoreManager).runInTransactionWithResult(any());

        Result result = runCommandAndGetResult();

        String errorMessage =
                "Failed to list source registrations: Error in retrieving sources from database";
        expectFailure(
                result,
                errorMessage,
                COMMAND_ATTRIBUTION_REPORTING_LIST_SOURCE_REGISTRATIONS,
                RESULT_GENERIC_ERROR);
    }

    @Test
    public void testRunListSourceRegistrations_singleSourcePartialSchemaJSON()
            throws JSONException {
        String[] args = {SCHEMA_SUB_COMMAND, SCHEMA_PARTIAL};
        List<Source> sources = List.of(source1);
        testRunListSourceRegistrationsWithSchema(sources, args);
    }

    @Test
    public void testRunListSourceRegistrations_multipleSourcesPartialSchemaJSON()
            throws JSONException {
        String[] args = {SCHEMA_SUB_COMMAND, SCHEMA_PARTIAL};
        List<Source> sources = List.of(source1, source2);
        testRunListSourceRegistrationsWithSchema(sources, args);
    }

    @Test
    public void testRunListSourceRegistrations_singleSourceFullSchemaJSON() throws JSONException {
        String[] args = {SCHEMA_SUB_COMMAND, SCHEMA_FULL};
        List<Source> sources = List.of(source5);
        testRunListSourceRegistrationsWithSchema(sources, args);
    }

    @Test
    public void testRunListSourceRegistrations_multipleSourcesFullSchemaJSON()
            throws JSONException {
        String[] args = {SCHEMA_SUB_COMMAND, SCHEMA_FULL};
        List<Source> sources = List.of(source5, source6);
        testRunListSourceRegistrationsWithSchema(sources, args);
    }

    @Test
    public void testRunListSourceRegistrations_invalidSchema() {
        String[] args = {SCHEMA_SUB_COMMAND, "invalid_schema"};
        Result result = runCommandAndGetResult(args);

        String errorMessage =
                "Failed to list source registrations: Invalid schema. The 'schema' parameter must"
                        + " be either 'partial' or 'full'. Check for typos.";
        expectFailure(
                result,
                errorMessage,
                COMMAND_ATTRIBUTION_REPORTING_LIST_SOURCE_REGISTRATIONS,
                RESULT_GENERIC_ERROR);
    }

    private void testRunListSourceRegistrationsWithSchema(List<Source> sources, String[] schema)
            throws JSONException {
        doReturn(Optional.ofNullable(sources))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());

        Result result = runCommandAndGetResult(schema);

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_SOURCE_REGISTRATIONS);

        JSONObject jsonOutput = new JSONObject(result.mOut);
        JSONArray registrationsArray = jsonOutput.getJSONArray("attribution_reporting");

        for (int i = 0; i < registrationsArray.length(); i++) {
            JSONObject registrationsObject = registrationsArray.getJSONObject(i);
            Source outputSource = getSourceFromJson(registrationsObject, schema[1]);
            assertThat(outputSource).isEqualTo(sources.get(i));
            assertSourceJson(registrationsObject, outputSource, schema[1]);
        }
    }

    private Result runCommandAndGetResult(String[] args) {
        String[] stringArray = new String[2 + args.length];
        stringArray[0] = AttributionReportingShellCommandFactory.COMMAND_PREFIX;
        stringArray[1] = AttributionReportingListSourceRegistrationsCommand.CMD;
        for (int i = 0; i < args.length; i++) {
            stringArray[i + 2] = args[i];
        }
        return run(
                new AttributionReportingListSourceRegistrationsCommand(
                        mDatastoreManager, mDevSessionDataStore),
                stringArray);
    }

    private Result runCommandAndGetResult() {
        return run(
                new AttributionReportingListSourceRegistrationsCommand(
                        mDatastoreManager, mDevSessionDataStore),
                AttributionReportingShellCommandFactory.COMMAND_PREFIX,
                AttributionReportingListSourceRegistrationsCommand.CMD);
    }

    private static Source getSourceFromJson(JSONObject jsonObject, String schema)
            throws JSONException {
        Source.Builder builder =
                new Source.Builder()
                        .setEventId(new UnsignedLong(jsonObject.getLong(EVENT_ID)))
                        .setStatus(getStatusFromString(jsonObject.getString(STATUS)))
                        .setRegistrationOrigin(
                                Uri.parse(jsonObject.getString((REGISTRATION_ORIGIN))))
                        .setRegistrant(Uri.parse(jsonObject.getString(REGISTRANT)))
                        .setEventTime(jsonObject.getLong(EVENT_TIME))
                        .setExpiryTime(jsonObject.getLong(EXPIRY_TIME))
                        .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
                        .setEnrollmentId(SourceFixture.ValidSourceParams.ENROLLMENT_ID)
                        .setAttributionMode(
                                getAttributionModeFromString(
                                        jsonObject.getString(ATTRIBUTION_MODE)));
        ;

        if (jsonObject.has(APP_DESTINATION)) {
            List<Uri> fetchedAppDestinations =
                    parseDestinations(jsonObject.getString(APP_DESTINATION));
            builder.setAppDestinations(fetchedAppDestinations);
        }
        if (jsonObject.has(WEB_DESTINATION)) {
            List<Uri> fetchedWebDestinations =
                    parseDestinations(jsonObject.getString(WEB_DESTINATION));
            builder.setWebDestinations(fetchedWebDestinations);
        }

        if (jsonObject.has(DEBUG_KEY)) {
            builder.setDebugKey(new UnsignedLong(jsonObject.getLong(DEBUG_KEY)));
        }

        if (schema.equals(SCHEMA_FULL)) {
            String aggregatableDebugReporting =
                    replaceWithAggregatable(SourceContract.AGGREGATE_DEBUG_REPORTING);

            builder.setAggregatableReportWindow(
                    jsonObject.getLong(SourceContract.AGGREGATABLE_REPORT_WINDOW));
            builder.setSharedAggregationKeys(
                    jsonObject.getString(SourceContract.SHARED_AGGREGATION_KEYS));
            builder.setAggregateDebugReportingString(
                    jsonObject.getString(aggregatableDebugReporting));
            builder.setDestinationLimitPriority(
                    jsonObject.getLong(SourceContract.DESTINATION_LIMIT_PRIORITY));
            builder.setEventLevelEpsilon(jsonObject.getDouble(SourceContract.EVENT_LEVEL_EPSILON));
            builder.setFilterDataString(jsonObject.getString(SourceContract.FILTER_DATA));
            builder.setMaxEventLevelReports(
                    jsonObject.getInt(SourceContract.MAX_EVENT_LEVEL_REPORTS));
            builder.setPriority(jsonObject.getLong(SourceContract.PRIORITY));
            builder.setTriggerDataMatching(
                    STRING_MATCHING_TRIGGER_DATA_IMMUTABLE_MAP.get(
                            jsonObject.get(SourceContract.TRIGGER_DATA_MATCHING)));
        }

        return builder.build();
    }

    public static final ImmutableMap<String, Source.TriggerDataMatching>
            STRING_MATCHING_TRIGGER_DATA_IMMUTABLE_MAP =
                    ImmutableMap.of(
                            "Modulus",
                            Source.TriggerDataMatching.MODULUS,
                            "EXACT",
                            Source.TriggerDataMatching.EXACT);

    private static List<Uri> parseDestinations(String destinationsString) {
        List<Uri> destinations = new ArrayList<>();
        if (!destinationsString.trim().isEmpty()) {
            String[] destinationStrings =
                    destinationsString.replace("[", "").replace("]", "").split(",");
            for (String destinationString : destinationStrings) {
                destinations.add(Uri.parse(destinationString.trim()));
            }
        }
        return destinations;
    }

    private static int getStatusFromString(String statusString) {
        if (statusString.equals(ACTIVE)) {
            return Source.Status.ACTIVE;
        } else if (statusString.equals(IGNORED)) {
            return Source.Status.IGNORED;
        } else if (statusString.equals(MARKED_TO_DELETE)) {
            return Source.Status.MARKED_TO_DELETE;
        } else {
            throw new IllegalArgumentException("Invalid status: " + statusString);
        }
    }

    private static int getAttributionModeFromString(String attributionModeString) {
        if (attributionModeString.equals("Attributable")) {
            return Source.AttributionMode.TRUTHFULLY;
        } else if (attributionModeString.equals("Unattributable: noised with fake reports")) {
            return Source.AttributionMode.FALSELY;
        } else if (attributionModeString.equals("Unattributable: noised with no reports")) {
            return Source.AttributionMode.NEVER;
        } else if (attributionModeString.equals("Unassigned")) {
            return Source.AttributionMode.UNASSIGNED;
        } else {
            throw new IllegalArgumentException(
                    "Invalid attribution mode: " + attributionModeString);
        }
    }

    private static Source.Builder getValidSourceWithFullSchema() {
        return new Source.Builder()
                .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
                .setAppDestinations(SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                .setEnrollmentId(SourceFixture.ValidSourceParams.ENROLLMENT_ID)
                .setRegistrant(SourceFixture.ValidSourceParams.REGISTRANT)
                .setRegistrationOrigin(SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN)
                .setEventId(SourceFixture.ValidSourceParams.SOURCE_EVENT_ID)
                .setStatus(SourceFixture.ValidSourceParams.STATUS)
                .setRegistrationOrigin(SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN)
                .setRegistrant(SourceFixture.ValidSourceParams.REGISTRANT)
                .setEventTime(SourceFixture.ValidSourceParams.SOURCE_EVENT_TIME)
                .setExpiryTime(SourceFixture.ValidSourceParams.EXPIRY_TIME)
                .setSourceType(SourceFixture.ValidSourceParams.SOURCE_TYPE)
                .setAttributionMode(SourceFixture.ValidSourceParams.ATTRIBUTION_MODE)
                .setDebugKey(SourceFixture.ValidSourceParams.DEBUG_KEY)
                .setAggregatableReportWindow(SourceFixture.ValidSourceParams.EXPIRY_TIME)
                .setSharedAggregationKeys(SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS)
                .setAggregateDebugReportingString(
                        SourceFixture.ValidSourceParams.AGGREGATE_DEBUG_REPORT)
                .setDestinationLimitPriority(
                        SourceFixture.ValidSourceParams.DESTINATION_LIMIT_PRIORITY)
                .setEventLevelEpsilon(SourceFixture.ValidSourceParams.EVENT_LEVEL_EPSILON)
                .setFilterDataString(SourceFixture.ValidSourceParams.buildFilterDataString())
                .setMaxEventLevelReports(SourceFixture.ValidSourceParams.MAX_EVENT_LEVEL_REPORTS)
                .setPriority(SourceFixture.ValidSourceParams.PRIORITY)
                .setTriggerDataMatching(SourceFixture.ValidSourceParams.TRIGGER_DATA_MATCHING)
                .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS);
    }

    private void assertSourceJson(JSONObject sourceJson, Source source, String schema)
            throws JSONException {
        assertThat(sourceJson.getString(EVENT_ID)).isEqualTo(source.getEventId().toString());
        assertThat(sourceJson.getString(STATUS)).isEqualTo(STATUS_MAP.get(source.getStatus()));
        assertThat(sourceJson.getString(REGISTRATION_ORIGIN))
                .isEqualTo(source.getRegistrationOrigin().toString());
        assertThat(sourceJson.getString(REGISTRANT)).isEqualTo(source.getRegistrant().toString());
        assertThat(sourceJson.getLong(EVENT_TIME)).isEqualTo(source.getEventTime());
        assertThat(sourceJson.getLong(EXPIRY_TIME)).isEqualTo(source.getExpiryTime());
        assertThat(getAttributionModeFromString(sourceJson.getString(ATTRIBUTION_MODE)))
                .isEqualTo(source.getAttributionMode());

        if (source.getAppDestinations() != null) {
            List<Uri> fetchedAppDestinations =
                    parseDestinations(sourceJson.getString(APP_DESTINATION));
            assertThat(fetchedAppDestinations).isEqualTo(source.getAppDestinations());
        } else {
            assertThat(sourceJson.has(APP_DESTINATION)).isFalse();
        }

        if (source.getWebDestinations() != null) {
            List<Uri> fetchedWebDestinations =
                    parseDestinations(sourceJson.getString(WEB_DESTINATION));
            assertThat(fetchedWebDestinations).isEqualTo(source.getWebDestinations());
        } else {
            assertThat(sourceJson.has(WEB_DESTINATION)).isFalse();
        }

        if (source.getDebugKey() != null) {
            assertThat(sourceJson.getString(DEBUG_KEY)).isEqualTo(source.getDebugKey().toString());
        } else {
            assertThat(sourceJson.has(DEBUG_KEY)).isFalse();
        }

        if (schema.equals(SCHEMA_FULL)) {
            String aggregatableDebugReporting =
                    replaceWithAggregatable(SourceContract.AGGREGATE_DEBUG_REPORTING);
            assertThat(sourceJson.getLong(SourceContract.AGGREGATABLE_REPORT_WINDOW))
                    .isEqualTo(source.getAggregatableReportWindow());
            assertThat(sourceJson.getString(SourceContract.SHARED_AGGREGATION_KEYS))
                    .isEqualTo(source.getSharedAggregationKeys());
            assertThat(sourceJson.getString(aggregatableDebugReporting))
                    .isEqualTo(source.getAggregateDebugReportingString());
            assertThat(sourceJson.getLong(SourceContract.DESTINATION_LIMIT_PRIORITY))
                    .isEqualTo(source.getDestinationLimitPriority());
            assertThat(sourceJson.getDouble(SourceContract.EVENT_LEVEL_EPSILON))
                    .isEqualTo(source.getEventLevelEpsilon());
            assertThat(sourceJson.getString(SourceContract.FILTER_DATA))
                    .isEqualTo(source.getFilterDataString());
            assertThat(sourceJson.getInt(SourceContract.MAX_EVENT_LEVEL_REPORTS))
                    .isEqualTo(source.getMaxEventLevelReports());
            assertThat(sourceJson.getLong(SourceContract.PRIORITY)).isEqualTo(source.getPriority());
            assertThat(
                            STRING_MATCHING_TRIGGER_DATA_IMMUTABLE_MAP.get(
                                    sourceJson.getString(SourceContract.TRIGGER_DATA_MATCHING)))
                    .isEqualTo(source.getTriggerDataMatching());

        } else if (schema.equals(SCHEMA_PARTIAL)) {
            assertThat(sourceJson.has(SourceContract.AGGREGATABLE_REPORT_WINDOW)).isFalse();
            assertThat(sourceJson.has(SourceContract.SHARED_AGGREGATION_KEYS)).isFalse();
            assertThat(sourceJson.has(SourceContract.AGGREGATE_DEBUG_REPORTING)).isFalse();
            assertThat(sourceJson.has(SourceContract.DESTINATION_LIMIT_PRIORITY)).isFalse();
            assertThat(sourceJson.has(SourceContract.EVENT_LEVEL_EPSILON)).isFalse();
            assertThat(sourceJson.has(SourceContract.FILTER_DATA)).isFalse();
            assertThat(sourceJson.has(SourceContract.MAX_EVENT_LEVEL_REPORTS)).isFalse();
            assertThat(sourceJson.has(SourceContract.PRIORITY)).isFalse();
            assertThat(sourceJson.has(SourceContract.TRIGGER_DATA_MATCHING)).isFalse();
        }
    }
}
