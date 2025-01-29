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

import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_ATTRIBUTION_REPORTING_LIST_SOURCE_REGISTRATIONS;
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
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.shell.ShellCommandTestCase;

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
    private static final String ID = "_id";
    private static final String STATUS = "status";
    private static final String REGISTRATION_ORIGIN = "registration_origin";
    private static final String REGISTRANT = "registrant";
    private static final String EVENT_TIME = "event_time";
    private static final String EXPIRY_TIME = "expiry_time";
    private static final String DEBUG_KEY = "debug_key";
    private static final String ATTRIBUTION_MODE = "attribution_mode";
    private static final String APP_DESTINATION = "app_destination";
    private static final String WEB_DESTINATION = "web_destination";
    private static final String ACTIVE = "active";
    private static final String IGNORED = "ignored";
    private static final String MARKED_TO_DELETE = "marked_to_delete";
    DatastoreManager mDatastoreManager = Mockito.mock(DatastoreManager.class);
    @Mock
    private DevSessionDataStore mDevSessionDataStore;

    public static List<Uri> multipleWebDestinations =
            List.of(Uri.parse("https://destination.test"), Uri.parse("https://destination2.test"));

    private static Source source1 =
            SourceFixture.getMinimalValidSourceBuilder()
                    .setId("reg1")
                    .setStatus(SourceFixture.ValidSourceParams.STATUS)
                    .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                    .setEventTime(SourceFixture.ValidSourceParams.SOURCE_EVENT_TIME)
                    .setExpiryTime(SourceFixture.ValidSourceParams.EXPIRY_TIME)
                    .setDebugKey(SourceFixture.ValidSourceParams.DEBUG_KEY)
                    .build();

    private static Source source2 =
            SourceFixture.getMinimalValidSourceBuilder()
                    .setId("reg2")
                    .setStatus(SourceFixture.ValidSourceParams.STATUS)
                    .setWebDestinations(multipleWebDestinations)
                    .setEventTime(SourceFixture.ValidSourceParams.SOURCE_EVENT_TIME)
                    .setExpiryTime(SourceFixture.ValidSourceParams.EXPIRY_TIME)
                    .setDebugKey(SourceFixture.ValidSourceParams.DEBUG_KEY)
                    .build();

    private static Source source3 =
            SourceFixture.getMinimalValidSourceBuilder()
                    .setId("reg3")
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
                    .setId("reg4")
                    .setStatus(SourceFixture.ValidSourceParams.STATUS)
                    .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                    .setEventTime(SourceFixture.ValidSourceParams.SOURCE_EVENT_TIME)
                    .setExpiryTime(SourceFixture.ValidSourceParams.EXPIRY_TIME)
                    .setDebugKey(SourceFixture.ValidSourceParams.DEBUG_KEY)
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
    public void testRunListSourceRegistrations_singleSourceWithAppDestWithOneWebDestJSON()
            throws JSONException {
        doReturn(Optional.ofNullable(List.of(source1)))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());

        Result result = runCommandAndGetResult();

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_SOURCE_REGISTRATIONS);

        JSONObject jsonOutput = new JSONObject(result.mOut);
        JSONArray registrationsArray = jsonOutput.getJSONArray("attribution_reporting");
        JSONObject registrationObject = registrationsArray.getJSONObject(0);

        Source outputSource = getSourceFromJson(registrationObject);

        assertThat(outputSource).isEqualTo(source1);
    }

    @Test
    public void testRunListSourceRegistrations_singleSourceWithAppDestWithMultipleWebDestJSON()
            throws JSONException {
        doReturn(Optional.ofNullable(List.of(source2)))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());

        Result result = runCommandAndGetResult();

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_SOURCE_REGISTRATIONS);

        JSONObject jsonOutput = new JSONObject(result.mOut);
        JSONArray registrationsArray = jsonOutput.getJSONArray("attribution_reporting");
        JSONObject registrationObject = registrationsArray.getJSONObject(0);

        Source outputSource = getSourceFromJson(registrationObject);

        assertThat(outputSource).isEqualTo(source2);
    }

    @Test
    public void testRunListSourceRegistrations_singleSourceWithAppDestNoWebDestJSON()
            throws JSONException {
        doReturn(Optional.ofNullable(List.of(source3)))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());

        Result result = runCommandAndGetResult();

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_SOURCE_REGISTRATIONS);

        JSONObject jsonOutput = new JSONObject(result.mOut);
        JSONArray registrationsArray = jsonOutput.getJSONArray("attribution_reporting");
        JSONObject registrationObject = registrationsArray.getJSONObject(0);

        Source outputSource = getSourceFromJson(registrationObject);

        assertThat(outputSource).isEqualTo(source3);
    }

    @Test
    public void testRunListSourceRegistrations_singleSourceNoAppDestWithOneWebDestJSON()
            throws JSONException {
        doReturn(Optional.ofNullable(List.of(source4)))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());

        Result result = runCommandAndGetResult();

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_SOURCE_REGISTRATIONS);

        JSONObject jsonOutput = new JSONObject(result.mOut);
        JSONArray registrationsArray = jsonOutput.getJSONArray("attribution_reporting");
        JSONObject registrationObject = registrationsArray.getJSONObject(0);

        Source outputSource = getSourceFromJson(registrationObject);

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
            Source outputSource = getSourceFromJson(registrationsArray.getJSONObject(i));
            assertThat(outputSource).isEqualTo(expectedSources.get(i));
        }
    }

    @Test
    public void testRunListSourceRegistrations_emptyListSources() throws JSONException {
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

        expectSuccess(result, COMMAND_ATTRIBUTION_REPORTING_LIST_SOURCE_REGISTRATIONS);

        assertThat(result.mOut).isEqualTo("Error in retrieving sources from database.");
    }

    private Result runCommandAndGetResult() {
        return run(
                new AttributionReportingListSourceRegistrationsCommand(
                        mDatastoreManager, mDevSessionDataStore),
                AttributionReportingShellCommandFactory.COMMAND_PREFIX,
                AttributionReportingListSourceRegistrationsCommand.CMD);
    }

    /** Creates a Source.Builder from JSON. Missing fields are populated with default values. */
    private static Source getSourceFromJson(JSONObject jsonObject) throws JSONException {
        Source.Builder builder =
                new Source.Builder()
                        .setId(jsonObject.getString(ID))
                        .setStatus(getStatusFromString(jsonObject.getString(STATUS)))
                        .setRegistrationOrigin(
                                Uri.parse(jsonObject.getString((REGISTRATION_ORIGIN))))
                        .setRegistrant(Uri.parse(jsonObject.getString(REGISTRANT)))
                        .setEventTime(jsonObject.getLong(EVENT_TIME))
                        .setExpiryTime(jsonObject.getLong(EXPIRY_TIME))
                        .setDebugKey(new UnsignedLong(jsonObject.getLong(DEBUG_KEY)))
                        .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
                        .setEnrollmentId(SourceFixture.ValidSourceParams.ENROLLMENT_ID)
                        .setAttributionMode(
                                getAttributionModeFromString(
                                        jsonObject.getString(ATTRIBUTION_MODE)));

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
        return builder.build();
    }

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
}
