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

import static com.android.adservices.service.measurement.reporting.DebugReportFixture.ValidDebugReportParams;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_ATTRIBUTION_REPORTING_LIST_DEBUG_REPORTS;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_DEV_MODE_UNCONFIRMED;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.net.Uri;

import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.devapi.DevSessionFixture;
import com.android.adservices.service.devapi.DevSession;
import com.android.adservices.service.devapi.DevSessionDataStore;
import com.android.adservices.service.devapi.DevSessionState;
import com.android.adservices.service.measurement.reporting.DebugReport;
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

public class AttributionReportingListDebugReportsCommandTest
        extends ShellCommandTestCase<AttributionReportingListDebugReportsCommand> {
    private static final String INSERTION_TIME = "insertion_time";
    private static final String REGISTRATION_ORIGIN = "registration_origin";
    private static final String TYPE = "type";
    private static final String BODY = "body";
    private static final String SCHEMA_FULL = "full";
    private static final String SCHEMA_PARTIAL = "partial";
    private static final String SCHEMA_SUB_COMMAND = "--schema";
    DatastoreManager mDatastoreManager = Mockito.mock(DatastoreManager.class);
    @Mock private DevSessionDataStore mDevSessionDataStore;

    DebugReport debugReport1 =
            new DebugReport.Builder()
                    .setId("report1")
                    .setType("trigger-event-deduplicated")
                    .setBody(ValidDebugReportParams.BODY)
                    .setEnrollmentId(ValidDebugReportParams.ENROLLMENT_ID)
                    .setRegistrationOrigin(ValidDebugReportParams.REGISTRATION_ORIGIN)
                    .setRegistrant(ValidDebugReportParams.REGISTRANT)
                    .setInsertionTime(ValidDebugReportParams.INSERTION_TIME)
                    .build();
    DebugReport debugReport2 =
            new DebugReport.Builder()
                    .setId("report2")
                    .setType("trigger-no-matching-source")
                    .setBody(ValidDebugReportParams.BODY)
                    .setEnrollmentId(ValidDebugReportParams.ENROLLMENT_ID)
                    .setRegistrationOrigin(ValidDebugReportParams.REGISTRATION_ORIGIN)
                    .setRegistrant(ValidDebugReportParams.REGISTRANT)
                    .setInsertionTime(ValidDebugReportParams.INSERTION_TIME)
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
    public void testRunDebugReports_outsideDevSessionError() {
        when(mDevSessionDataStore.get())
                .thenReturn(
                        immediateFuture(
                                DevSession.builder().setState(DevSessionState.IN_PROD).build()));

        Result result = runCommandAndGetResult();

        expect.that(result.mOut).isEmpty();
        expect.that(result.mResultCode).isEqualTo(RESULT_DEV_MODE_UNCONFIRMED);
    }

    @Test
    public void testRunListDebugReports_transitioningError() {
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
    public void testRunListDebugReports_pass() throws JSONException {
        doReturn(Optional.ofNullable(List.of(debugReport1, debugReport2)))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());

        Result result = runCommandAndGetResult();

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_DEBUG_REPORTS);

        JSONObject jsonOutput = new JSONObject(result.mOut);
        JSONArray registrationsArray = jsonOutput.getJSONArray("attribution_reporting");

        List<DebugReport> expectedDebugReports = List.of(debugReport1, debugReport2);

        for (int i = 0; i < registrationsArray.length(); i++) {
            String id = "report" + (i + 1);
            JSONObject registrationsObject = registrationsArray.getJSONObject(i);
            DebugReport outputDebugReport =
                    getDebugReportFromJson(registrationsObject, id, "").build();
            assertThat(outputDebugReport).isEqualTo(expectedDebugReports.get(i));
            assertDebugReportJson(registrationsObject, outputDebugReport, SCHEMA_PARTIAL);
        }
    }

    @Test
    public void testRunListDebugReports_emptyListDebugReports() throws JSONException {
        doReturn(Optional.ofNullable(List.of()))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());

        Result result = runCommandAndGetResult();

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_DEBUG_REPORTS);

        JSONObject jsonOutput = new JSONObject(result.mOut);
        JSONArray registrationsArray = jsonOutput.getJSONArray("attribution_reporting");

        assertThat(registrationsArray.length()).isEqualTo(0);
    }

    @Test
    public void testRunListDebugReports_nullDebugReportsJson() {
        doReturn(Optional.empty()).when(mDatastoreManager).runInTransactionWithResult(any());

        Result result = runCommandAndGetResult();

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_DEBUG_REPORTS);

        assertThat(result.mOut)
                .isEqualTo("Error in retrieving verbose debug reports from database");
    }

    @Test
    public void testRunListDebugReports_singleDebugReportsPartialSchemaJson() throws JSONException {
        String[] args = {SCHEMA_SUB_COMMAND, SCHEMA_PARTIAL};
        List<DebugReport> debugReports = List.of(debugReport1);
        testRunListDebugReportsWithSchema(debugReports, args);
    }

    @Test
    public void testRunListDebugReports_multipleDebugReportsPartialSchemaJson()
            throws JSONException {
        String[] args = {SCHEMA_SUB_COMMAND, SCHEMA_PARTIAL};
        List<DebugReport> debugReports = List.of(debugReport1, debugReport2);
        testRunListDebugReportsWithSchema(debugReports, args);
    }

    @Test
    public void testRunListDebugReports_singleDebugReportsFullSchemaJson() throws JSONException {
        String[] args = {SCHEMA_SUB_COMMAND, SCHEMA_FULL};
        List<DebugReport> debugReports = List.of(debugReport1);
        testRunListDebugReportsWithSchema(debugReports, args);
    }

    @Test
    public void testRunListDebugReports_multipleDebugReportsFullSchemaJson() throws JSONException {
        String[] args = {SCHEMA_SUB_COMMAND, SCHEMA_FULL};
        List<DebugReport> debugReports = List.of(debugReport1, debugReport2);
        testRunListDebugReportsWithSchema(debugReports, args);
    }

    private void testRunListDebugReportsWithSchema(List<DebugReport> debugReports, String[] schema)
            throws JSONException {
        doReturn(Optional.ofNullable(debugReports))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());

        Result result = runCommandAndGetResult(schema);

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_DEBUG_REPORTS);

        JSONObject jsonOutput = new JSONObject(result.mOut);
        JSONArray registrationsArray = jsonOutput.getJSONArray("attribution_reporting");

        for (int i = 0; i < registrationsArray.length(); i++) {
            String id = "debugReport" + (i + 1);
            JSONObject registrationsObject = registrationsArray.getJSONObject(i);
            DebugReport outputDebugReport =
                    getDebugReportFromJson(registrationsObject, id, schema[1]).build();
            assertThat(outputDebugReport).isEqualTo(debugReports.get(i));
            assertDebugReportJson(registrationsObject, outputDebugReport, schema[1]);
        }
    }

    private Result runCommandAndGetResult(String[] args) {
        String[] stringArray = new String[2 + args.length];
        stringArray[0] = AttributionReportingShellCommandFactory.COMMAND_PREFIX;
        stringArray[1] = AttributionReportingListDebugReportsCommand.CMD;
        for (int i = 0; i < args.length; i++) {
            stringArray[i + 1] = args[i];
        }
        return run(
                new AttributionReportingListDebugReportsCommand(
                        mDatastoreManager, mDevSessionDataStore),
                stringArray);
    }

    private Result runCommandAndGetResult() {
        return run(
                new AttributionReportingListDebugReportsCommand(
                        mDatastoreManager, mDevSessionDataStore),
                AttributionReportingShellCommandFactory.COMMAND_PREFIX,
                AttributionReportingListDebugReportsCommand.CMD);
    }

    /**
     * Creates a DebugReport.Builder from JSON. Missing fields are populated with default values.
     */
    private static DebugReport.Builder getDebugReportFromJson(
            JSONObject jsonObject, String id, String schema) throws JSONException {
        DebugReport.Builder builder =
                new DebugReport.Builder()
                        .setId(id)
                        .setType(jsonObject.getString(TYPE))
                        .setBody(ValidDebugReportParams.BODY)
                        .setEnrollmentId(ValidDebugReportParams.ENROLLMENT_ID)
                        .setRegistrationOrigin(Uri.parse(jsonObject.getString(REGISTRATION_ORIGIN)))
                        .setInsertionTime(jsonObject.getLong(INSERTION_TIME))
                        .setRegistrant(ValidDebugReportParams.REGISTRANT);

        if (schema.equals(SCHEMA_FULL)) {
            builder.setBody(jsonObject.getString(BODY));
        }
        return builder;
    }

    private void assertDebugReportJson(JSONObject reportJson, DebugReport report, String schema)
            throws JSONException {
        assertThat(reportJson.getString(TYPE)).isEqualTo(report.getType());
        assertThat(reportJson.getString(REGISTRATION_ORIGIN))
                .isEqualTo(report.getRegistrationOrigin().toString());
        assertThat(reportJson.getLong(INSERTION_TIME)).isEqualTo(report.getInsertionTime());

        if (schema.equals(SCHEMA_FULL)) {
            assertThat(reportJson.getString(BODY)).isEqualTo(report.getBody().toString());
        } else if (schema.equals(SCHEMA_PARTIAL)) {
            assertThat(reportJson.has(BODY)).isFalse();
        }
    }
}
