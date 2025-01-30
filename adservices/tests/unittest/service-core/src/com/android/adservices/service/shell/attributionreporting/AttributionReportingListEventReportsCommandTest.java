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
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.EventReportFixture;
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

        List<EventReport> expectedEventReports = List.of(eventReport1, eventReport2);

        for (int i = 0; i < registrationsArray.length(); i++) {
            String id = "eventReport" + (i + 1);
            EventReport outputEventReport =
                    getEventReportFromJson(registrationsArray.getJSONObject(i), id).build();
            boolean outputEventReportRandomized = outputEventReport.isRandomized();
            assertThat(outputEventReport).isEqualTo(expectedEventReports.get(i));
            assertThat(outputEventReportRandomized)
                    .isEqualTo(expectedEventReports.get(i).isRandomized());
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
    public void testRunListEventReports_nullEventReportsJSON() {
        doReturn(Optional.empty()).when(mDatastoreManager).runInTransactionWithResult(any());

        Result result = runCommandAndGetResult();

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_EVENT_REPORTS);

        assertThat(result.mOut).isEqualTo("Error in retrieving event reports from database.");
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
    private static EventReport.Builder getEventReportFromJson(JSONObject jsonObject, String id)
            throws JSONException {
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
        return builder;
    }
}
