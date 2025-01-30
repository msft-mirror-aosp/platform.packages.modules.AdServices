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

    DatastoreManager mDatastoreManager = Mockito.mock(DatastoreManager.class);
    @Mock
    private DevSessionDataStore mDevSessionDataStore;

    AggregateReport aggregatableReport1 =
            AggregateReportFixture.getValidAggregateReportBuilder().setId("report1").build();

    AggregateReport aggregatableReport2 =
            AggregateReportFixture.getValidAggregateReportBuilder().setId("report2").build();

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
            String id = "report" + (i + 1);
            AggregateReport outputAggregatableReport =
                    getAggregatableReportFromJson(registrationsArray.getJSONObject(i), id).build();
            assertThat(outputAggregatableReport).isEqualTo(expectedAggregatableReports.get(i));
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

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_AGGREGATABLE_REPORTS);

        assertThat(result.mOut)
                .isEqualTo("Error in retrieving aggregatable reports from database.");
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
            JSONObject jsonObject, String id) throws JSONException {
        AggregateReport.Builder builder =
                new AggregateReport.Builder()
                        .setId(id)
                        .setPublisher(AggregateReportFixture.ValidAggregateReportParams.PUBLISHER)
                        .setAttributionDestination(
                                Uri.parse(jsonObject.getString(
                                        MeasurementTables.AggregateReport
                                                .ATTRIBUTION_DESTINATION)))
                        .setSourceRegistrationTime(
                                AggregateReportFixture.ValidAggregateReportParams
                                        .SOURCE_REGISTRATION_TIME)
                        .setScheduledReportTime(jsonObject.getLong(
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
        return builder;
    }
}
