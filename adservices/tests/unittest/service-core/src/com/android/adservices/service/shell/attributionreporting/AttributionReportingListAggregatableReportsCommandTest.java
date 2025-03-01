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

import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_ATTRIBUTION_REPORTING_LIST_AGGREGATABLE_REPORTS;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_DEV_MODE_UNCONFIRMED;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_GENERIC_ERROR;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.net.Uri;

import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.devapi.DevSessionFixture;
import com.android.adservices.service.devapi.DevSession;
import com.android.adservices.service.devapi.DevSessionDataStore;
import com.android.adservices.service.devapi.DevSessionState;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.aggregation.AggregateReportFixture;
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

public class AttributionReportingListAggregatableReportsCommandTest
        extends ShellCommandTestCase<AttributionReportingListAggregatableReportsCommand> {
    public static final String STATUS = "status";
    private static final String TRIGGER_TIME = "trigger_time";
    private static final String SCHEMA_FULL = "full";
    private static final String SCHEMA_PARTIAL = "partial";
    private static final String SCHEMA_SUB_COMMAND = "--schema";
    DatastoreManager mDatastoreManager = Mockito.mock(DatastoreManager.class);
    @Mock
    private DevSessionDataStore mDevSessionDataStore;

    AggregateReport aggregatableReport1 =
            AggregateReportFixture.getValidAggregateReportBuilder()
                    .setTriggerContextId("aggregatableTriggerContext1")
                    .build();

    AggregateReport aggregatableReport2 =
            AggregateReportFixture.getValidAggregateReportBuilder()
                    .setTriggerContextId("aggregatableTriggerContext2")
                    .build();

    AggregateReport aggregatableReport3 =
            AggregateReportFixture.getValidAggregateReportBuilder()
                    .setTriggerContextId("aggregatableTriggerContext3")
                    .setApiVersion(AggregateReportFixture.ValidAggregateReportParams.API_VERSION)
                    .build();

    AggregateReport aggregatableReport4 =
            AggregateReportFixture.getValidAggregateReportBuilder()
                    .setTriggerContextId("aggregatableTriggerContext4")
                    .setApiVersion(AggregateReportFixture.ValidAggregateReportParams.API_VERSION)
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
    public void testRunAggregatableReports_outsideDevSessionError() {
        when(mDevSessionDataStore.get())
                .thenReturn(
                        immediateFuture(
                                DevSession.builder().setState(DevSessionState.IN_PROD).build()));

        Result result = runCommandAndGetResult();

        expect.that(result.mOut).isEmpty();
        expect.that(result.mResultCode).isEqualTo(RESULT_DEV_MODE_UNCONFIRMED);
    }

    @Test
    public void testRunListAggregatableReports_transitioningError() {
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
    public void testRunListAggregatableReports_pass() throws JSONException {
        doReturn(Optional.ofNullable(List.of(aggregatableReport1, aggregatableReport2)))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());

        Result result = runCommandAndGetResult();

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_AGGREGATABLE_REPORTS);

        JSONObject jsonOutput = new JSONObject(result.mOut);
        JSONArray registrationsArray = jsonOutput.getJSONArray("attribution_reporting");

        List<AggregateReport> expectedAggregatableReports =
                List.of(aggregatableReport1, aggregatableReport2);

        for (int i = 0; i < registrationsArray.length(); i++) {
            JSONObject registrationsObject = registrationsArray.getJSONObject(i);
            AggregateReport outputAggregatableReport =
                    getAggregatableReportFromJson(registrationsObject, SCHEMA_PARTIAL).build();
            assertThat(outputAggregatableReport).isEqualTo(expectedAggregatableReports.get(i));
            assertAggregatableReportJson(
                    registrationsObject, outputAggregatableReport, SCHEMA_PARTIAL);
        }
    }

    @Test
    public void testRunListAggregatableReports_emptyListAggregatableReports() throws JSONException {
        doReturn(Optional.ofNullable(List.of()))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());

        Result result = runCommandAndGetResult();

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_AGGREGATABLE_REPORTS);

        JSONObject jsonOutput = new JSONObject(result.mOut);
        JSONArray registrationsArray = jsonOutput.getJSONArray("attribution_reporting");

        assertThat(registrationsArray.length()).isEqualTo(0);
    }

    @Test
    public void testRunListAggregatableReports_nullAggregatableReportsJSON() {
        doReturn(Optional.empty()).when(mDatastoreManager).runInTransactionWithResult(any());

        Result result = runCommandAndGetResult();

        String errorMessage =
                "Failed to list aggregatable reports: Error in retrieving aggregatable reports from"
                        + " database";
        expectFailure(
                result,
                errorMessage,
                COMMAND_ATTRIBUTION_REPORTING_LIST_AGGREGATABLE_REPORTS,
                RESULT_GENERIC_ERROR);
    }

    @Test
    public void testRunListAggregatableReports_singleAggregatableReportsPartialSchemaJson()
            throws JSONException {
        String[] args = {SCHEMA_SUB_COMMAND, SCHEMA_PARTIAL};
        List<AggregateReport> aggregatableReports = List.of(aggregatableReport1);
        testRunListAggregatableReportsWithSchema(aggregatableReports, args);
    }

    @Test
    public void testRunListAggregatableReports_multipleAggregatableReportsPartialSchemaJson()
            throws JSONException {
        String[] args = {SCHEMA_SUB_COMMAND, SCHEMA_PARTIAL};
        List<AggregateReport> aggregatableReports =
                List.of(aggregatableReport1, aggregatableReport2);
        testRunListAggregatableReportsWithSchema(aggregatableReports, args);
    }

    @Test
    public void testRunListAggregatableReports_singleAggregatableReportsFullSchemaJson()
            throws JSONException {
        String[] args = {SCHEMA_SUB_COMMAND, SCHEMA_FULL};
        List<AggregateReport> aggregatableReports = List.of(aggregatableReport3);
        testRunListAggregatableReportsWithSchema(aggregatableReports, args);
    }

    @Test
    public void testRunListAggregatableReports_multipleAggregatableReportsFullSchemaJson()
            throws JSONException {
        String[] args = {SCHEMA_SUB_COMMAND, SCHEMA_FULL};
        List<AggregateReport> aggregatableReports =
                List.of(aggregatableReport3, aggregatableReport4);
        testRunListAggregatableReportsWithSchema(aggregatableReports, args);
    }

    @Test
    public void testRunListAggregatableReports_invalidSchema() {
        String[] args = {SCHEMA_SUB_COMMAND, "invalid_schema"};
        Result result = runCommandAndGetResult(args);

        String errorMessage =
                "Failed to list aggregatable reports: Invalid schema. The 'schema' parameter must"
                        + " be either 'partial' or 'full'. Check for typos.";
        expectFailure(
                result,
                errorMessage,
                COMMAND_ATTRIBUTION_REPORTING_LIST_AGGREGATABLE_REPORTS,
                RESULT_GENERIC_ERROR);
    }

    private void testRunListAggregatableReportsWithSchema(
            List<AggregateReport> aggregatableReports, String[] schema) throws JSONException {
        doReturn(Optional.ofNullable(aggregatableReports))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());

        Result result = runCommandAndGetResult(schema);

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_AGGREGATABLE_REPORTS);

        JSONObject jsonOutput = new JSONObject(result.mOut);
        JSONArray registrationsArray = jsonOutput.getJSONArray("attribution_reporting");

        for (int i = 0; i < registrationsArray.length(); i++) {
            JSONObject registrationsObject = registrationsArray.getJSONObject(i);
            AggregateReport aggregatableReport =
                    getAggregatableReportFromJson(registrationsObject, schema[1]).build();
            assertThat(aggregatableReport).isEqualTo(aggregatableReports.get(i));
            assertAggregatableReportJson(registrationsObject, aggregatableReport, schema[1]);
        }
    }

    private Result runCommandAndGetResult(String[] args) {
        String[] stringArray = new String[2 + args.length];
        stringArray[0] = AttributionReportingShellCommandFactory.COMMAND_PREFIX;
        stringArray[1] = AttributionReportingListAggregatableReportsCommand.CMD;
        for (int i = 0; i < args.length; i++) {
            stringArray[i + 2] = args[i];
        }
        return run(
                new AttributionReportingListAggregatableReportsCommand(
                        mDatastoreManager, mDevSessionDataStore),
                stringArray);
    }

    private Result runCommandAndGetResult() {
        return run(
                new AttributionReportingListAggregatableReportsCommand(
                        mDatastoreManager, mDevSessionDataStore),
                AttributionReportingShellCommandFactory.COMMAND_PREFIX,
                AttributionReportingListAggregatableReportsCommand.CMD);
    }

    /**
     * Creates a AggregateReport.Builder from JSON. Missing fields are populated with default
     * values.
     */
    private static AggregateReport.Builder getAggregatableReportFromJson(
            JSONObject jsonObject, String schema) throws JSONException {
        AggregateReport.Builder builder =
                new AggregateReport.Builder()
                        .setPublisher(AggregateReportFixture.ValidAggregateReportParams.PUBLISHER)
                        .setAttributionDestination(
                                Uri.parse(
                                        jsonObject.getString(
                                                MeasurementTables.AggregateReport
                                                        .ATTRIBUTION_DESTINATION)))
                        .setSourceRegistrationTime(
                                AggregateReportFixture.ValidAggregateReportParams
                                        .SOURCE_REGISTRATION_TIME)
                        .setScheduledReportTime(
                                jsonObject.getLong(
                                        MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME))
                        .setEnrollmentId(
                                AggregateReportFixture.ValidAggregateReportParams.ENROLLMENT_ID)
                        .setSourceDebugKey(
                                AggregateReportFixture.ValidAggregateReportParams.SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(
                                AggregateReportFixture.ValidAggregateReportParams.TRIGGER_DEBUG_KEY)
                        .setDebugCleartextPayload(
                                AggregateReportFixture.ValidAggregateReportParams.getDebugPayload())
                        .setStatus(jsonObject.getInt(MeasurementTables.AggregateReport.STATUS))
                        .setDebugReportStatus(AggregateReport.DebugReportStatus.PENDING)
                        .setDedupKey(AggregateReportFixture.ValidAggregateReportParams.DEDUP_KEY)
                        .setRegistrationOrigin(
                                Uri.parse(
                                        jsonObject.getString(
                                                MeasurementTables.AggregateReport
                                                        .REGISTRATION_ORIGIN)))
                        .setAggregationCoordinatorOrigin(
                                Uri.parse(
                                        jsonObject.getString(
                                                MeasurementTables.AggregateReport
                                                        .AGGREGATION_COORDINATOR_ORIGIN)))
                        .setIsFakeReport(false)
                        .setTriggerContextId(
                                jsonObject.getString(
                                        MeasurementTables.AggregateReport.TRIGGER_CONTEXT_ID))
                        .setApi(AggregateReportFixture.ValidAggregateReportParams.API)
                        .setAggregatableFilteringIdMaxBytes(
                                AggregateReportFixture.ValidAggregateReportParams
                                        .AGGREGATABLE_FILTERING_ID_MAX_BYTES)
                        .setTriggerTime(jsonObject.getLong(TRIGGER_TIME));

        if (schema.equals(SCHEMA_FULL)) {
            builder.setApi(jsonObject.getString(MeasurementTables.AggregateReport.API))
                    .setDebugReportStatus(
                            jsonObject.getInt(
                                    MeasurementTables.AggregateReport.DEBUG_REPORT_STATUS))
                    .setPublisher(
                            Uri.parse(
                                    jsonObject.getString(
                                            MeasurementTables.AggregateReport.PUBLISHER)))
                    .setApiVersion(
                            jsonObject.getString(MeasurementTables.AggregateReport.API_VERSION))
                    .setSourceDebugKey(
                            new UnsignedLong(
                                    jsonObject.getLong(
                                            MeasurementTables.AggregateReport.SOURCE_DEBUG_KEY)))
                    .setTriggerDebugKey(
                            new UnsignedLong(
                                    jsonObject.getLong(
                                            MeasurementTables.AggregateReport.TRIGGER_DEBUG_KEY)));
        }

        return builder;
    }

    private void assertAggregatableReportJson(
            JSONObject reportJson, AggregateReport report, String schema) throws JSONException {
        assertThat(reportJson.getString(MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION))
                .isEqualTo(report.getAttributionDestination().toString());
        assertThat(reportJson.getLong(MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME))
                .isEqualTo(report.getScheduledReportTime());
        assertThat(reportJson.getInt(MeasurementTables.AggregateReport.STATUS))
                .isEqualTo(report.getStatus());
        assertThat(reportJson.getString(MeasurementTables.AggregateReport.TRIGGER_CONTEXT_ID))
                .isEqualTo(report.getTriggerContextId());
        assertThat(reportJson.getLong(TRIGGER_TIME)).isEqualTo(report.getTriggerTime());
        assertThat(
                        reportJson.getString(
                                MeasurementTables.AggregateReport.AGGREGATION_COORDINATOR_ORIGIN))
                .isEqualTo(report.getAggregationCoordinatorOrigin().toString());
        assertThat(reportJson.getString(MeasurementTables.AggregateReport.DEBUG_CLEARTEXT_PAYLOAD))
                .isEqualTo(report.getDebugCleartextPayload());
        assertThat(reportJson.getString(MeasurementTables.AggregateReport.REGISTRATION_ORIGIN))
                .isEqualTo(report.getRegistrationOrigin().toString());

        if (schema.equals(SCHEMA_FULL)) {
            assertThat(reportJson.getString(MeasurementTables.AggregateReport.API))
                    .isEqualTo(report.getApi());
            assertThat(reportJson.getInt(MeasurementTables.AggregateReport.DEBUG_REPORT_STATUS))
                    .isEqualTo(report.getDebugReportStatus());
            assertThat(reportJson.getString(MeasurementTables.AggregateReport.PUBLISHER))
                    .isEqualTo(report.getPublisher().toString());
            assertThat(reportJson.getString(MeasurementTables.AggregateReport.API_VERSION))
                    .isEqualTo(report.getApiVersion());
            assertThat(reportJson.getString(MeasurementTables.AggregateReport.SOURCE_DEBUG_KEY))
                    .isEqualTo(report.getSourceDebugKey().toString());
            assertThat(reportJson.getString(MeasurementTables.AggregateReport.TRIGGER_DEBUG_KEY))
                    .isEqualTo(report.getTriggerDebugKey().toString());
        } else if (schema.equals(SCHEMA_PARTIAL)) {
            assertThat(reportJson.has(MeasurementTables.AggregateReport.API)).isFalse();
            assertThat(reportJson.has(MeasurementTables.AggregateReport.DEBUG_REPORT_STATUS))
                    .isFalse();
            assertThat(reportJson.has(MeasurementTables.AggregateReport.PUBLISHER)).isFalse();
            assertThat(reportJson.has(MeasurementTables.AggregateReport.API_VERSION)).isFalse();
            assertThat(reportJson.has(MeasurementTables.AggregateReport.SOURCE_DEBUG_KEY))
                    .isFalse();
            assertThat(reportJson.has(MeasurementTables.AggregateReport.TRIGGER_DEBUG_KEY))
                    .isFalse();
        }
    }
}
