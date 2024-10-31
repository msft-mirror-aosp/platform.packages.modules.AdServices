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
import static com.android.adservices.service.common.AppManifestConfigCall.API_CUSTOM_AUDIENCES;
import static com.android.adservices.service.customaudience.AdditionalScheduleRequestsEnabledStrategyHelper.MIN_DELAY_KEY;
import static com.android.adservices.service.customaudience.AdditionalScheduleRequestsEnabledStrategyHelper.PARTIAL_CUSTOM_AUDIENCES_KEY;
import static com.android.adservices.service.customaudience.AdditionalScheduleRequestsEnabledStrategyHelper.SHOULD_REPLACE_PENDING_UPDATES_KEY;
import static com.android.adservices.service.customaudience.AdditionalScheduleRequestsEnabledStrategyHelper.UPDATE_URI_KEY;
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
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.getPartialCustomAudienceJsonArray;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.getPartialCustomAudience_1;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.getScheduleRequest_1;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.getScheduleRequest_2;
import static com.android.adservices.service.customaudience.ScheduledUpdatesHandler.LEAVE_CUSTOM_AUDIENCE_KEY;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.PartialCustomAudience;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.data.customaudience.DBScheduledCustomAudienceUpdate;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.devapi.DevContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@SetErrorLogUtilDefaultParams(
        throwable = ExpectErrorLogUtilWithExceptionCall.Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS)
public class AdditionalScheduleRequestsEnabledStrategyHelperTest
        extends AdServicesExtendedMockitoTestCase {

    private AdditionalScheduleRequestsEnabledStrategyHelper mHelper;
    private DevContext mDevContext;
    @Mock private FledgeAuthorizationFilter mFledgeAuthorizationFilterMock;

    private static final Instant NOW = Instant.now();

    @Before
    public void setup() {
        mHelper =
                new AdditionalScheduleRequestsEnabledStrategyHelper(
                        mContext,
                        mFledgeAuthorizationFilterMock,
                        FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MIN_DELAY_MINS_OVERRIDE,
                        /* disableFledgeEnrollmentCheck */ true);
        mDevContext = DevContext.builder(PACKAGE).setDeviceDevOptionsEnabled(false).build();
    }

    @Test
    public void testValidateAndConvertScheduleRequest_Success() throws JSONException {
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
                        .setCreationTime(NOW)
                        .setScheduledTime(NOW.plus(MIN_DELAY, ChronoUnit.MINUTES))
                        .setIsDebuggable(mDevContext.getDeviceDevOptionsEnabled())
                        .build();

        DBScheduledCustomAudienceUpdate result =
                mHelper.validateAndConvertScheduleRequest(OWNER, scheduleRequest, NOW, mDevContext);

        assertThat(result.toString()).isEqualTo(expected.toString());
    }

    @Test
    public void testValidateAndConvertScheduleRequest_MissingUpdateUri_ThrowsException()
            throws JSONException {
        JSONObject scheduleRequest =
                createScheduleRequestWithUpdateUri(
                        null,
                        MIN_DELAY,
                        getPartialCustomAudienceJsonArray(),
                        CUSTOM_AUDIENCE_TO_LEAVE_JSON_ARRAY,
                        true);

        assertThrows(
                JSONException.class,
                () ->
                        mHelper.validateAndConvertScheduleRequest(
                                OWNER, scheduleRequest, NOW, mDevContext));
    }

    @Test
    public void testValidateAndConvertScheduleRequest_InvalidMinDelay_ThrowsException()
            throws JSONException {
        int invalidMinDelay = -10;

        JSONObject scheduleRequest =
                createScheduleRequest(
                        BUYER,
                        invalidMinDelay,
                        getPartialCustomAudienceJsonArray(),
                        CUSTOM_AUDIENCE_TO_LEAVE_JSON_ARRAY,
                        true);

        Throwable error =
                assertThrows(
                        JSONException.class,
                        () ->
                                mHelper.validateAndConvertScheduleRequest(
                                        OWNER, scheduleRequest, NOW, mDevContext));
        assertThat(error).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testValidateAndConvertScheduleRequest_NullHost_ThrowsException()
            throws JSONException {
        JSONObject scheduleRequest =
                createScheduleRequestWithUpdateUri(
                        "noHost",
                        MIN_DELAY,
                        getPartialCustomAudienceJsonArray(),
                        CUSTOM_AUDIENCE_TO_LEAVE_JSON_ARRAY,
                        true);

        Throwable errorThrown =
                assertThrows(
                        JSONException.class,
                        () -> {
                            mHelper.validateAndConvertScheduleRequest(
                                    OWNER, scheduleRequest, NOW, mDevContext);
                        });
        assertThat(errorThrown).hasCauseThat().isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testValidateAndConvertScheduleRequest_EnrollmentCheckEnabled_Success()
            throws JSONException {
        setupWithDisableFledgeEnrollmentCheckFalse();

        when(mFledgeAuthorizationFilterMock.getAndAssertAdTechFromUriAllowed(
                        mContext,
                        OWNER,
                        BUYER_UPDATE_URI,
                        AD_SERVICES_API_CALLED__API_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE,
                        API_CUSTOM_AUDIENCES))
                .thenReturn(AdTechIdentifier.fromString(BUYER_UPDATE_URI.getHost()));

        JSONObject scheduleRequest =
                createScheduleRequestWithUpdateUri(
                        BUYER_UPDATE_URI.toString(),
                        MIN_DELAY,
                        getPartialCustomAudienceJsonArray(),
                        CUSTOM_AUDIENCE_TO_LEAVE_JSON_ARRAY,
                        true);

        DBScheduledCustomAudienceUpdate result =
                mHelper.validateAndConvertScheduleRequest(OWNER, scheduleRequest, NOW, mDevContext);

        verify(mFledgeAuthorizationFilterMock)
                .getAndAssertAdTechFromUriAllowed(
                        mContext,
                        OWNER,
                        BUYER_UPDATE_URI,
                        AD_SERVICES_API_CALLED__API_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE,
                        API_CUSTOM_AUDIENCES);
        assertThat(result.getBuyer()).isEqualTo(BUYER);
    }

    @Test
    public void
            testValidateAndConvertScheduleRequest_EnrollmentCheckEnabled_NotEnrolled_ThrowsException()
                    throws JSONException {
        setupWithDisableFledgeEnrollmentCheckFalse();

        when(mFledgeAuthorizationFilterMock.getAndAssertAdTechFromUriAllowed(
                        mContext,
                        OWNER,
                        BUYER_UPDATE_URI,
                        AD_SERVICES_API_CALLED__API_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE,
                        API_CUSTOM_AUDIENCES))
                .thenThrow(FledgeAuthorizationFilter.AdTechNotAllowedException.class);

        JSONObject scheduleRequest =
                createScheduleRequestWithUpdateUri(
                        BUYER_UPDATE_URI.toString(),
                        MIN_DELAY,
                        getPartialCustomAudienceJsonArray(),
                        CUSTOM_AUDIENCE_TO_LEAVE_JSON_ARRAY,
                        true);

        Throwable error =
                assertThrows(
                        JSONException.class,
                        () -> {
                            mHelper.validateAndConvertScheduleRequest(
                                    OWNER, scheduleRequest, NOW, mDevContext);
                        });

        assertThat(error)
                .hasCauseThat()
                .isInstanceOf(FledgeAuthorizationFilter.AdTechNotAllowedException.class);
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
    public void testExtractScheduleRequestsFromResponse_NoScheduleRequestsKey_ReturnsEmptyList() {
        List<JSONObject> result = mHelper.extractScheduleRequestsFromResponse(new JSONObject());

        assertThat(result).isEmpty();
    }

    @Test
    public void testExtractPartialCustomAudiencesFromRequest_Success() throws JSONException {
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
    public void testExtractPartialCustomAudiencesFromRequest_UnableToParseOne()
            throws JSONException {
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
        assertThrows(
                JSONException.class,
                () -> mHelper.extractPartialCustomAudiencesFromRequest(scheduleRequest));
    }

    @Test
    public void
            testExtractPartialCustomAudiencesFromRequest_UnableToParseAnything_ReturnsEmptyList()
                    throws JSONException {
        JSONArray partialCustomAudiences = new JSONArray(List.of(new JSONArray(), new JSONArray()));

        JSONObject scheduleRequest =
                createScheduleRequest(
                        BUYER,
                        MIN_DELAY,
                        partialCustomAudiences,
                        CUSTOM_AUDIENCE_TO_LEAVE_JSON_ARRAY,
                        true);
        assertThrows(
                JSONException.class,
                () -> mHelper.extractPartialCustomAudiencesFromRequest(scheduleRequest));
    }

    @Test
    public void
            testExtractPartialCustomAudiencesFromRequest_NoPartialCustomAudiencesKey_ReturnsEmptyList()
                    throws JSONException {
        JSONObject scheduleRequest = new JSONObject();

        scheduleRequest.put(MIN_DELAY_KEY, MIN_DELAY);
        scheduleRequest.put(UPDATE_URI_KEY, BUYER_UPDATE_URI);
        scheduleRequest.put(SHOULD_REPLACE_PENDING_UPDATES_KEY, true);
        scheduleRequest.put(LEAVE_CUSTOM_AUDIENCE_KEY, new JSONArray());

        List<PartialCustomAudience> result =
                mHelper.extractPartialCustomAudiencesFromRequest(scheduleRequest);

        assertThat(result).isEmpty();
    }

    @Test
    public void testExtractCustomAudiencesToLeaveFromRequest_Success() throws JSONException {
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
    public void testExtractCustomAudiencesToLeaveFromRequest_UnableToParseOne()
            throws JSONException {
        JSONArray customAudiencesToLeave = new JSONArray(List.of("", LEAVE_CA_1));

        JSONObject scheduleRequest =
                createScheduleRequest(
                        BUYER,
                        MIN_DELAY,
                        getPartialCustomAudienceJsonArray(),
                        customAudiencesToLeave,
                        true);

        assertThrows(
                JSONException.class,
                () -> mHelper.extractCustomAudiencesToLeaveFromRequest(scheduleRequest));
    }

    @Test
    public void
            testExtractCustomAudiencesToLeaveFromRequest_UnableToParseAnything_ReturnsEmptyList()
                    throws JSONException {
        JSONArray customAudiencesToLeave = new JSONArray(List.of("", ""));

        JSONObject scheduleRequest =
                createScheduleRequest(
                        BUYER,
                        MIN_DELAY,
                        getPartialCustomAudienceJsonArray(),
                        customAudiencesToLeave,
                        true);

        assertThrows(
                JSONException.class,
                () -> mHelper.extractCustomAudiencesToLeaveFromRequest(scheduleRequest));
    }

    @Test
    public void
            testExtractCustomAudiencesToLeaveFromRequest_NoCustomAudiencesToLeaveKey_ReturnsEmptyList()
                    throws JSONException {
        JSONObject scheduleRequest = new JSONObject();

        scheduleRequest.put(MIN_DELAY_KEY, MIN_DELAY);
        scheduleRequest.put(UPDATE_URI_KEY, BUYER_UPDATE_URI);
        scheduleRequest.put(SHOULD_REPLACE_PENDING_UPDATES_KEY, true);
        scheduleRequest.put(PARTIAL_CUSTOM_AUDIENCES_KEY, new JSONArray());

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

    private void setupWithDisableFledgeEnrollmentCheckFalse() {
        mHelper =
                new AdditionalScheduleRequestsEnabledStrategyHelper(
                        mContext,
                        mFledgeAuthorizationFilterMock,
                        FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MIN_DELAY_MINS_OVERRIDE,
                        /* disableFledgeEnrollmentCheck */ false);
    }
}
