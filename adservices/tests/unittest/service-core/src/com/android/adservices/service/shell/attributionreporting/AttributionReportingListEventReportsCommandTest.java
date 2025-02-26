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

import static com.android.adservices.service.measurement.EventReportFixture.ValidEventReportParams;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_ATTRIBUTION_REPORTING_LIST_EVENT_REPORTS;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_DEV_MODE_UNCONFIRMED;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_GENERIC_ERROR;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.net.Uri;

import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.MeasurementTables.EventReportContract;
import com.android.adservices.devapi.DevSessionFixture;
import com.android.adservices.service.devapi.DevSession;
import com.android.adservices.service.devapi.DevSessionDataStore;
import com.android.adservices.service.devapi.DevSessionState;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.EventReportFixture;
import com.android.adservices.service.measurement.Source;
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

public class AttributionReportingListEventReportsCommandTest
        extends ShellCommandTestCase<AttributionReportingListEventReportsCommand> {
    public static final String STATUS = "status";
    private static final String TRIGGER_TIME = "trigger_time";
    private static final String REPORT_TIME = "report_time";
    private static final String REGISTRATION_ORIGIN = "registration_origin";
    private static final String SCHEMA_FULL = "full";
    private static final String SCHEMA_PARTIAL = "partial";
    private static final String SCHEMA_SUB_COMMAND = "--schema";
    DatastoreManager mDatastoreManager = Mockito.mock(DatastoreManager.class);
    @Mock
    private DevSessionDataStore mDevSessionDataStore;

    private static EventReport eventReport1 =
            EventReportFixture.getBaseEventReportBuild()
                    .setId("eventReport1")
                    .setSourceId(ValidEventReportParams.SOURCE_ID)
                    .setTriggerId(ValidEventReportParams.TRIGGER_ID)
                    .build();

    private static EventReport eventReport2 =
            EventReportFixture.getBaseEventReportBuild()
                    .setId("eventReport2")
                    .setSourceId(ValidEventReportParams.SOURCE_ID)
                    .setTriggerId(ValidEventReportParams.TRIGGER_ID)
                    .build();

    private static EventReport eventReport3 =
            EventReportFixture.getBaseEventReportBuild()
                    .setId("eventReport3")
                    .setSourceId(ValidEventReportParams.SOURCE_ID)
                    .setTriggerId(ValidEventReportParams.TRIGGER_ID)
                    .setTriggerData(new UnsignedLong(1L))
                    .build();

    private static EventReport eventReport4 =
            EventReportFixture.getBaseEventReportBuild()
                    .setId("eventReport4")
                    .setSourceId(ValidEventReportParams.SOURCE_ID)
                    .setTriggerId(ValidEventReportParams.TRIGGER_ID)
                    .setTriggerData(new UnsignedLong(2L))
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
    public void testRunListEventReports_outsideDevSessionError() {
        when(mDevSessionDataStore.get())
                .thenReturn(
                        immediateFuture(
                                DevSession.builder().setState(DevSessionState.IN_PROD).build()));

        Result result = runCommandAndGetResult();

        expect.that(result.mOut).isEmpty();
        expect.that(result.mResultCode).isEqualTo(RESULT_DEV_MODE_UNCONFIRMED);
    }

    @Test
    public void testRunListEventReports_transitioningError() {
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
    public void testRunListEventReports_pass() throws JSONException {
        doReturn(Optional.ofNullable(List.of(eventReport1, eventReport2)))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());

        Result result = runCommandAndGetResult();

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_EVENT_REPORTS);

        JSONObject jsonOutput = new JSONObject(result.mOut);
        JSONArray registrationsArray = jsonOutput.getJSONArray("attribution_reporting");

        List<EventReport> eventReports = List.of(eventReport1, eventReport2);

        for (int i = 0; i < registrationsArray.length(); i++) {
            String id = "eventReport" + (i + 1);
            JSONObject registrationsObject = registrationsArray.getJSONObject(i);
            EventReport outputEventReport =
                    getEventReportFromJson(registrationsArray.getJSONObject(i), id, "").build();
            assertThat(outputEventReport).isEqualTo(eventReports.get(i));
            assertEventReportJson(registrationsObject, outputEventReport, SCHEMA_PARTIAL);
        }
    }

    @Test
    public void testRunListEventReports_emptyListEventReports() throws JSONException {
        doReturn(Optional.ofNullable(List.of()))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());

        Result result = runCommandAndGetResult();

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_EVENT_REPORTS);

        JSONObject jsonOutput = new JSONObject(result.mOut);
        JSONArray registrationsArray = jsonOutput.getJSONArray("attribution_reporting");

        assertThat(registrationsArray.length()).isEqualTo(0);
    }

    @Test
    public void testRunListEventReports_nullEventReportsJson() {
        doReturn(Optional.empty()).when(mDatastoreManager).runInTransactionWithResult(any());

        Result result = runCommandAndGetResult();

        String errorMessage =
                "Failed to list event reports: Error in retrieving event reports from database";
        expectFailure(
                result,
                errorMessage,
                COMMAND_ATTRIBUTION_REPORTING_LIST_EVENT_REPORTS,
                RESULT_GENERIC_ERROR);
    }

    @Test
    public void testRunListEventReports_singleEventReportsPartialSchemaJson() throws JSONException {
        String[] args = {SCHEMA_SUB_COMMAND, SCHEMA_PARTIAL};
        List<EventReport> eventReports = List.of(eventReport1);
        testRunListEventReportsWithSchema(eventReports, args);
    }

    @Test
    public void testRunListEventReports_multipleEventReportsPartialSchemaJson()
            throws JSONException {
        String[] args = {SCHEMA_SUB_COMMAND, SCHEMA_PARTIAL};
        List<EventReport> eventReports = List.of(eventReport1, eventReport2);
        testRunListEventReportsWithSchema(eventReports, args);
    }

    @Test
    public void testRunListEventReports_singleEventReportsFullSchemaJson() throws JSONException {
        String[] args = {SCHEMA_SUB_COMMAND, SCHEMA_FULL};
        List<EventReport> eventReports = List.of(eventReport3);
        testRunListEventReportsWithSchema(eventReports, args);
    }

    @Test
    public void testRunListEventReports_multipleEventReportsFullSchemaJson() throws JSONException {
        String[] args = {SCHEMA_SUB_COMMAND, SCHEMA_FULL};
        List<EventReport> eventReports = List.of(eventReport3, eventReport4);
        testRunListEventReportsWithSchema(eventReports, args);
    }

    @Test
    public void testRunListEventReports_invalidSchema() {
        String[] args = {SCHEMA_SUB_COMMAND, "invalid_schema"};

        Result result = runCommandAndGetResult(args);

        String errorMessage =
                "Failed to list event reports: Invalid schema. The 'schema' parameter must be"
                        + " either 'partial' or 'full'. Check for typos.";
        expectFailure(
                result,
                errorMessage,
                COMMAND_ATTRIBUTION_REPORTING_LIST_EVENT_REPORTS,
                RESULT_GENERIC_ERROR);
    }

    private void testRunListEventReportsWithSchema(List<EventReport> eventReports, String[] schema)
            throws JSONException {
        doReturn(Optional.ofNullable(eventReports))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());

        Result result = runCommandAndGetResult(schema);

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_EVENT_REPORTS);

        JSONObject jsonOutput = new JSONObject(result.mOut);
        JSONArray registrationsArray = jsonOutput.getJSONArray("attribution_reporting");

        for (int i = 0; i < registrationsArray.length(); i++) {
            String id = "eventReport" + (i + 3);
            JSONObject registrationsObject = registrationsArray.getJSONObject(i);
            EventReport outputEventReport =
                    getEventReportFromJson(registrationsObject, id, schema[1]).build();
            assertThat(outputEventReport).isEqualTo(eventReports.get(i));
            assertEventReportJson(registrationsObject, outputEventReport, schema[1]);
        }
    }

    private Result runCommandAndGetResult(String[] args) {
        String[] stringArray = new String[2 + args.length];
        stringArray[0] = AttributionReportingShellCommandFactory.COMMAND_PREFIX;
        stringArray[1] = AttributionReportingListEventReportsCommand.CMD;
        for (int i = 0; i < args.length; i++) {
            stringArray[i + 2] = args[i];
        }
        return run(
                new AttributionReportingListEventReportsCommand(
                        mDatastoreManager, mDevSessionDataStore),
                stringArray);
    }

    private Result runCommandAndGetResult() {
        return run(
                new AttributionReportingListEventReportsCommand(
                        mDatastoreManager, mDevSessionDataStore),
                AttributionReportingShellCommandFactory.COMMAND_PREFIX,
                AttributionReportingListEventReportsCommand.CMD);
    }

    /**
     * Creates a EventReport.Builder from JSON. Missing fields are populated with default values.
     */
    private static EventReport.Builder getEventReportFromJson(
            JSONObject jsonObject, String id, String schema) throws JSONException {
        String attributionDestinationString = jsonObject.getString("attribution_destination");
        String cleanedAttributionDestinationString =
                attributionDestinationString.substring(
                        1, attributionDestinationString.length() - 1);
        List<Uri> attributionDestinations = List.of(Uri.parse(cleanedAttributionDestinationString));
        EventReport.Builder builder =
                new EventReport.Builder()
                        .setId(id)
                        .setSourceEventId(ValidEventReportParams.SOURCE_EVENT_ID)
                        .setEnrollmentId(ValidEventReportParams.ENROLLMENT_ID)
                        .setAttributionDestinations(attributionDestinations)
                        .setTriggerTime(jsonObject.getLong(TRIGGER_TIME))
                        .setTriggerDedupKey(ValidEventReportParams.TRIGGER_DEDUP_KEY)
                        .setReportTime(jsonObject.getLong(REPORT_TIME))
                        .setStatus(jsonObject.getInt(STATUS))
                        .setDebugReportStatus(ValidEventReportParams.DEBUG_REPORT_STATUS)
                        .setSourceType(ValidEventReportParams.SOURCE_TYPE)
                        .setSourceDebugKey(ValidEventReportParams.SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(ValidEventReportParams.TRIGGER_DEBUG_KEY)
                        .setSourceId(ValidEventReportParams.SOURCE_ID)
                        .setTriggerId(ValidEventReportParams.TRIGGER_ID)
                        .setRegistrationOrigin(Uri.parse(jsonObject.getString(REGISTRATION_ORIGIN)))
                        .setTriggerSummaryBucket(ValidEventReportParams.TRIGGER_SUMMARY_BUCKET);

        if (schema.equals(SCHEMA_FULL)) {
            builder.setId(jsonObject.getString(EventReportContract.ID))
                    .setSourceDebugKey(
                            new UnsignedLong(
                                    jsonObject.getLong(EventReportContract.SOURCE_DEBUG_KEY)))
                    .setSourceEventId(
                            new UnsignedLong(
                                    jsonObject.getLong(EventReportContract.SOURCE_EVENT_ID)))
                    .setSourceType(
                            getSourceTypeFromString(
                                    jsonObject.getString(EventReportContract.SOURCE_TYPE)))
                    .setTriggerData(
                            new UnsignedLong(jsonObject.getLong(EventReportContract.TRIGGER_DATA)))
                    .setTriggerDebugKey(
                            new UnsignedLong(
                                    jsonObject.getLong(EventReportContract.TRIGGER_DEBUG_KEY)));
        }

        return builder;
    }

    private static Source.SourceType getSourceTypeFromString(String sourceTypeString) {
        if (sourceTypeString.equals(Source.SourceType.EVENT.getValue())) {
            return Source.SourceType.EVENT;
        } else if (sourceTypeString.equals(Source.SourceType.NAVIGATION.getValue())) {
            return Source.SourceType.NAVIGATION;
        } else {
            throw new IllegalArgumentException("Invalid SourceType: " + sourceTypeString);
        }
    }

    private void assertEventReportJson(JSONObject reportJson, EventReport report, String schema)
            throws JSONException {
        assertThat(reportJson.getInt(STATUS)).isEqualTo(report.getStatus());
        assertThat(reportJson.getString(EventReportContract.ATTRIBUTION_DESTINATION))
                .isEqualTo(report.getAttributionDestinations().toString());
        assertThat(reportJson.getLong(TRIGGER_TIME)).isEqualTo(report.getTriggerTime());
        assertThat(reportJson.getLong(REPORT_TIME)).isEqualTo(report.getReportTime());
        assertThat(reportJson.getLong(EventReportContract.TRIGGER_PRIORITY))
                .isEqualTo(report.getTriggerPriority());
        assertThat(reportJson.getLong(EventReportContract.TRIGGER_PRIORITY))
                .isEqualTo(report.getTriggerPriority());
        assertThat(reportJson.getDouble(EventReportContract.RANDOMIZED_TRIGGER_RATE))
                .isEqualTo(report.getRandomizedTriggerRate());
        assertThat(reportJson.getString(REGISTRATION_ORIGIN))
                .isEqualTo(report.getRegistrationOrigin().toString());

        if (schema.equals(SCHEMA_FULL)) {
            assertThat(reportJson.getString(EventReportContract.ID)).isEqualTo(report.getId());
            assertThat(reportJson.getString(EventReportContract.SOURCE_DEBUG_KEY))
                    .isEqualTo(report.getSourceDebugKey().toString());
            assertThat(reportJson.getString(EventReportContract.SOURCE_EVENT_ID))
                    .isEqualTo(report.getSourceEventId().toString());
            assertThat(reportJson.getString(EventReportContract.SOURCE_TYPE))
                    .isEqualTo(report.getSourceType().getValue());
            if (report.getTriggerData() != null) {
                assertThat(reportJson.getString(EventReportContract.TRIGGER_DATA))
                        .isEqualTo(report.getTriggerData().toString());
            } else {
                assertThat(reportJson.has(EventReportContract.TRIGGER_DATA)).isFalse();
            }
            assertThat(reportJson.getString(EventReportContract.TRIGGER_DEBUG_KEY))
                    .isEqualTo(report.getTriggerDebugKey().toString());
        } else if (schema.equals(SCHEMA_PARTIAL)) {
            assertThat(reportJson.has(EventReportContract.ID)).isFalse();
            assertThat(reportJson.has(EventReportContract.SOURCE_DEBUG_KEY)).isFalse();
            assertThat(reportJson.has(EventReportContract.SOURCE_EVENT_ID)).isFalse();
            assertThat(reportJson.has(EventReportContract.SOURCE_TYPE)).isFalse();
            assertThat(reportJson.has(EventReportContract.TRIGGER_DATA)).isFalse();
            assertThat(reportJson.has(EventReportContract.TRIGGER_DEBUG_KEY)).isFalse();
        }
    }
}


