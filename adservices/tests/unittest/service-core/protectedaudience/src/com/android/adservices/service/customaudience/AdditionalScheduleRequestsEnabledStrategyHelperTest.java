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

package com.android.adservices.service.customaudience;

import static com.android.adservices.service.Flags.FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MIN_DELAY_MINS_OVERRIDE;
import static com.android.adservices.service.customaudience.CustomAudienceBlobFixture.addActivationTime;
import static com.android.adservices.service.customaudience.CustomAudienceBlobFixture.addExpirationTime;
import static com.android.adservices.service.customaudience.CustomAudienceBlobFixture.addName;
import static com.android.adservices.service.customaudience.CustomAudienceBlobFixture.addUserBiddingSignals;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.BUYER;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.BUYER_UPDATE_URI;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.CUSTOM_AUDIENCE_TO_LEAVE_JSON_ARRAY;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.LEAVE_CA_1;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.LEAVE_CA_2;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.MIN_DELAY;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.OWNER;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.PACKAGE;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.PARTIAL_CA_1;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.PARTIAL_CA_2;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.createJsonResponsePayloadWithScheduleRequests;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.createPartialCustomAudience;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.createScheduleRequest;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.createScheduleRequestWithUpdateUri;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.generateScheduleRequestFromCustomAudienceNames;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.generateScheduleRequestMissingUpdateUriKey;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.getPartialCustomAudienceJsonArray;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.getPartialCustomAudience_1;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.getScheduleRequest_1;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.getScheduleRequest_2;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.PartialCustomAudience;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.data.customaudience.DBScheduledCustomAudienceUpdate;
import com.android.adservices.service.devapi.DevContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

public class AdditionalScheduleRequestsEnabledStrategyHelperTest
        extends AdServicesExtendedMockitoTestCase {

    private AdditionalScheduleRequestsEnabledStrategyHelper mHelper;
    private DevContext mDevContext;

    @Before
    public void setup() {
        mHelper =
                new AdditionalScheduleRequestsEnabledStrategyHelper(
                        FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MIN_DELAY_MINS_OVERRIDE);

        mDevContext = DevContext.builder(PACKAGE).setDeviceDevOptionsEnabled(false).build();
    }

    @Test
    public void testValidateAndConvertScheduleRequest_Success() throws JSONException {
        Instant now = Instant.now();

        JSONObject scheduleRequest =
                createScheduleRequest(
                        BUYER,
                        MIN_DELAY,
                        getPartialCustomAudienceJsonArray(),
                        CUSTOM_AUDIENCE_TO_LEAVE_JSON_ARRAY,
                        true);

        DBScheduledCustomAudienceUpdate expected =
                DBScheduledCustomAudienceUpdate.builder()
                        .setAllowScheduleInResponse(false)
                        .setBuyer(BUYER)
                        .setUpdateUri(BUYER_UPDATE_URI)
                        .setOwner(OWNER)
                        .setCreationTime(now)
                        .setScheduledTime(now.plus(MIN_DELAY, ChronoUnit.MINUTES))
                        .setIsDebuggable(mDevContext.getDeviceDevOptionsEnabled())
                        .build();

        DBScheduledCustomAudienceUpdate result =
                mHelper.validateAndConvertScheduleRequest(OWNER, scheduleRequest, now, mDevContext);

        assertThat(result.toString()).isEqualTo(expected.toString());
    }

    @Test
    public void
            testValidateAndConvertScheduleRequest_InvalidMinDelay_ThrowsIllegalArgumentException()
                    throws JSONException {
        Instant now = Instant.now();

        int invalidMinDelay = 10;

        JSONObject scheduleRequest =
                createScheduleRequest(
                        BUYER,
                        invalidMinDelay,
                        getPartialCustomAudienceJsonArray(),
                        CUSTOM_AUDIENCE_TO_LEAVE_JSON_ARRAY,
                        true);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mHelper.validateAndConvertScheduleRequest(
                                OWNER, scheduleRequest, now, mDevContext));
    }

    @Test
    public void testValidateAndConvertScheduleRequest_MissingUpdateUriField_ThrowsJSONException()
            throws JSONException {
        Instant now = Instant.now();

        JSONObject scheduleRequest = generateScheduleRequestMissingUpdateUriKey();

        assertThrows(
                JSONException.class,
                () ->
                        mHelper.validateAndConvertScheduleRequest(
                                OWNER, scheduleRequest, now, mDevContext));
    }

    @Test
    public void testValidateAndConvertScheduleRequest_NullUpdateUri_ThrowsJSONException()
            throws JSONException {
        Instant now = Instant.now();

        JSONObject scheduleRequest =
                createScheduleRequestWithUpdateUri(
                        "",
                        MIN_DELAY,
                        getPartialCustomAudienceJsonArray(),
                        CUSTOM_AUDIENCE_TO_LEAVE_JSON_ARRAY,
                        true);

        assertThrows(
                JSONException.class,
                () -> {
                    mHelper.validateAndConvertScheduleRequest(
                            OWNER, scheduleRequest, now, mDevContext);
                });
    }

    @Test
    public void testExtractScheduleRequestsFromResponse_Success() throws JSONException {
        JSONObject scheduleRequest_1 = getScheduleRequest_1();
        JSONObject scheduleRequest_2 = getScheduleRequest_2();

        JSONArray scheduleRequests = new JSONArray(List.of(scheduleRequest_1, scheduleRequest_2));

        JSONObject updateResponseJson =
                createJsonResponsePayloadWithScheduleRequests(scheduleRequests);

        List<JSONObject> result = mHelper.extractScheduleRequestsFromResponse(updateResponseJson);

        assertThat(result).containsExactly(scheduleRequest_1, scheduleRequest_2);
    }

    @Test
    public void testExtractScheduleRequestsFromResponse_UnableToParseOne() throws JSONException {
        JSONObject scheduleRequest = getScheduleRequest_1();

        JSONArray scheduleRequests = new JSONArray(List.of(new JSONArray(), scheduleRequest));

        JSONObject updateResponseJson =
                createJsonResponsePayloadWithScheduleRequests(scheduleRequests);

        List<JSONObject> result = mHelper.extractScheduleRequestsFromResponse(updateResponseJson);

        assertThat(result).containsExactly(scheduleRequest);
    }

    @Test
    public void testExtractScheduleRequestsFromResponse_UnableToParseAnything_ReturnsEmptyList()
            throws JSONException {
        JSONArray scheduleRequestArray = new JSONArray(List.of(new JSONArray(), new JSONArray()));

        JSONObject updateResponseJson =
                createJsonResponsePayloadWithScheduleRequests(scheduleRequestArray);

        List<JSONObject> result = mHelper.extractScheduleRequestsFromResponse(updateResponseJson);

        assertThat(result).isEmpty();
    }

    @Test
    public void extractPartialCustomAudiencesFromRequest_Success() throws JSONException {
        List<String> partialCaList = List.of(PARTIAL_CA_1, PARTIAL_CA_2);

        JSONObject scheduleRequest =
                generateScheduleRequestFromCustomAudienceNames(
                        BUYER, MIN_DELAY, partialCaList, List.of(LEAVE_CA_1, LEAVE_CA_2), true);

        List<PartialCustomAudience> result =
                mHelper.extractPartialCustomAudiencesFromRequest(scheduleRequest);

        assertTrue(
                result.stream()
                        .map(ca -> ca.getName())
                        .collect(Collectors.toList())
                        .containsAll(partialCaList));
    }

    @Test
    public void extractPartialCustomAudiencesFromRequest_UnableToParseOne() throws JSONException {
        JSONObject partialCustomAudience = getPartialCustomAudience_1();

        JSONArray partialCustomAudiences =
                new JSONArray(List.of(new JSONArray(), partialCustomAudience));

        JSONObject scheduleRequest =
                createScheduleRequest(
                        BUYER,
                        MIN_DELAY,
                        partialCustomAudiences,
                        CUSTOM_AUDIENCE_TO_LEAVE_JSON_ARRAY,
                        true);

        List<PartialCustomAudience> result =
                mHelper.extractPartialCustomAudiencesFromRequest(scheduleRequest);

        assertThat(result.stream().map(ca -> ca.getName()).collect(Collectors.toList()))
                .containsExactly(PARTIAL_CA_1);
    }

    @Test
    public void extractPartialCustomAudiencesFromRequest_UnableToParseAnything_ReturnsEmptyList()
            throws JSONException {
        JSONArray partialCustomAudiences = new JSONArray(List.of(new JSONArray(), new JSONArray()));

        JSONObject scheduleRequest =
                createScheduleRequest(
                        BUYER,
                        MIN_DELAY,
                        partialCustomAudiences,
                        CUSTOM_AUDIENCE_TO_LEAVE_JSON_ARRAY,
                        true);

        List<PartialCustomAudience> result =
                mHelper.extractPartialCustomAudiencesFromRequest(scheduleRequest);

        assertThat(result).isEmpty();
    }

    @Test
    public void extractCustomAudiencesToLeaveFromRequest_Success() throws JSONException {
        JSONObject scheduleRequest =
                generateScheduleRequestFromCustomAudienceNames(
                        BUYER,
                        MIN_DELAY,
                        List.of(PARTIAL_CA_1, PARTIAL_CA_2),
                        List.of(LEAVE_CA_1, LEAVE_CA_2),
                        true);

        List<String> result = mHelper.extractCustomAudiencesToLeaveFromRequest(scheduleRequest);

        assertThat(result).containsExactly(LEAVE_CA_1, LEAVE_CA_2);
    }

    @Test
    public void extractCustomAudiencesToLeaveFromRequest_UnableToParseOne() throws JSONException {
        JSONArray customAudiencesToLeave = new JSONArray(List.of("", LEAVE_CA_1));

        JSONObject scheduleRequest =
                createScheduleRequest(
                        BUYER,
                        MIN_DELAY,
                        getPartialCustomAudienceJsonArray(),
                        customAudiencesToLeave,
                        true);

        List<String> result = mHelper.extractCustomAudiencesToLeaveFromRequest(scheduleRequest);

        assertThat(result).containsExactly(LEAVE_CA_1);
    }

    @Test
    public void extractCustomAudiencesToLeaveFromRequest_UnableToParseAnything_ReturnsEmptyList()
            throws JSONException {
        JSONArray customAudiencesToLeave = new JSONArray(List.of("", ""));

        JSONObject scheduleRequest =
                createScheduleRequest(
                        BUYER,
                        MIN_DELAY,
                        getPartialCustomAudienceJsonArray(),
                        customAudiencesToLeave,
                        true);

        List<String> result = mHelper.extractCustomAudiencesToLeaveFromRequest(scheduleRequest);

        assertThat(result).isEmpty();
    }

    @Test
    public void testJsonObjectToPartialCustomAudience_Success() throws JSONException {
        JSONObject jsonObject =
                createPartialCustomAudience(
                        PARTIAL_CA_1,
                        CustomAudienceFixture.VALID_ACTIVATION_TIME,
                        CustomAudienceFixture.VALID_EXPIRATION_TIME,
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
        PartialCustomAudience result = mHelper.jsonObjectToPartialCustomAudience(jsonObject);

        PartialCustomAudience expected =
                new PartialCustomAudience.Builder(PARTIAL_CA_1)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build();

        assertThat(result).isEqualTo(expected);
    }

    @Test
    public void testJsonObjectToPartialCustomAudience_MissingOptionalFields_Success()
            throws JSONException {
        JSONObject object = new JSONObject();
        object = addName(object, PARTIAL_CA_1, false);

        PartialCustomAudience result = mHelper.jsonObjectToPartialCustomAudience(object);

        PartialCustomAudience expected = new PartialCustomAudience.Builder(PARTIAL_CA_1).build();

        assertThat(result).isEqualTo(expected);
    }

    @Test
    public void testJsonObjectToPartialCustomAudience_MissingName_ThrowsJsonException()
            throws JSONException {

        JSONObject object = new JSONObject();
        object = addActivationTime(object, CustomAudienceFixture.VALID_ACTIVATION_TIME, false);
        object = addExpirationTime(object, CustomAudienceFixture.VALID_EXPIRATION_TIME, false);
        object =
                addUserBiddingSignals(
                        object, CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString(), false);

        JSONObject finalObject = object;

        assertThrows(
                JSONException.class,
                () -> {
                    mHelper.jsonObjectToPartialCustomAudience(finalObject);
                });
    }
}
