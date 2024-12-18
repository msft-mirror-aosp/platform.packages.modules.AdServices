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
import static com.android.adservices.service.customaudience.AdditionalScheduleRequestsEnabledStrategyHelper.MIN_DELAY_KEY;
import static com.android.adservices.service.customaudience.AdditionalScheduleRequestsEnabledStrategyHelper.PARTIAL_CUSTOM_AUDIENCES_KEY;
import static com.android.adservices.service.customaudience.AdditionalScheduleRequestsEnabledStrategyHelper.REQUESTS_KEY;
import static com.android.adservices.service.customaudience.AdditionalScheduleRequestsEnabledStrategyHelper.SCHEDULE_REQUESTS_KEY;
import static com.android.adservices.service.customaudience.AdditionalScheduleRequestsEnabledStrategyHelper.SHOULD_REPLACE_PENDING_UPDATES_KEY;
import static com.android.adservices.service.customaudience.AdditionalScheduleRequestsEnabledStrategyHelper.UPDATE_URI_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceBlobFixture.addActivationTime;
import static com.android.adservices.service.customaudience.CustomAudienceBlobFixture.addAuctionServerRequestFlags;
import static com.android.adservices.service.customaudience.CustomAudienceBlobFixture.addExpirationTime;
import static com.android.adservices.service.customaudience.CustomAudienceBlobFixture.addName;
import static com.android.adservices.service.customaudience.CustomAudienceBlobFixture.addPriority;
import static com.android.adservices.service.customaudience.CustomAudienceBlobFixture.addUserBiddingSignals;
import static com.android.adservices.service.customaudience.ScheduledUpdatesHandler.JOIN_CUSTOM_AUDIENCE_KEY;
import static com.android.adservices.service.customaudience.ScheduledUpdatesHandler.LEAVE_CUSTOM_AUDIENCE_KEY;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.argThat;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.PartialCustomAudience;
import android.adservices.customaudience.ScheduleCustomAudienceUpdateCallback;
import android.net.Uri;
import android.os.RemoteException;

import com.android.adservices.LoggerFactory;
import com.android.adservices.customaudience.DBTrustedBiddingDataFixture;
import com.android.adservices.data.customaudience.DBCustomAudienceToLeave;
import com.android.adservices.data.customaudience.DBPartialCustomAudience;
import com.android.adservices.data.customaudience.DBScheduledCustomAudienceUpdate;

import com.google.common.collect.ImmutableList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** Utility that helps test scheduleCustomAudienceUpdate() API */
public class ScheduleCustomAudienceUpdateTestUtils {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    public static final Long UPDATE_ID = 1L;
    public static final String PACKAGE = CommonFixture.TEST_PACKAGE_NAME_1;
    public static final String OWNER = CustomAudienceFixture.VALID_OWNER;
    public static final AdTechIdentifier BUYER = CommonFixture.VALID_BUYER_1;
    public static final AdTechIdentifier BUYER_2 = CommonFixture.VALID_BUYER_2;
    public static final Uri BUYER_UPDATE_URI = CommonFixture.getUri(BUYER, "/updateUri");
    public static final String PARTIAL_CA_1 = "partial_ca_1";
    public static final String PARTIAL_CA_2 = "partial_ca_2";
    public static final String PARTIAL_CA_3 = "partial_ca_3";
    public static final String LEAVE_CA_1 = "leave_ca_1";
    public static final String LEAVE_CA_2 = "leave_ca_2";
    public static final String LEAVE_CA_3 = "leave_ca_3";
    public static final Instant ACTIVATION_TIME = CommonFixture.FIXED_NOW;
    public static final Instant EXPIRATION_TIME = CommonFixture.FIXED_NEXT_ONE_DAY;
    public static final String SIGNALS_STRING = "{\"a\":\"b\"}";
    public static final AdSelectionSignals VALID_BIDDING_SIGNALS =
            AdSelectionSignals.fromString(SIGNALS_STRING);
    public static final DBPartialCustomAudience DB_PARTIAL_CUSTOM_AUDIENCE_1 =
            DBPartialCustomAudience.builder()
                    .setUpdateId(UPDATE_ID)
                    .setName(PARTIAL_CA_1)
                    .setActivationTime(ACTIVATION_TIME)
                    .setExpirationTime(EXPIRATION_TIME)
                    .setUserBiddingSignals(VALID_BIDDING_SIGNALS)
                    .build();

    public static final DBPartialCustomAudience DB_PARTIAL_CUSTOM_AUDIENCE_2 =
            DBPartialCustomAudience.builder()
                    .setUpdateId(UPDATE_ID)
                    .setName(PARTIAL_CA_2)
                    .setActivationTime(ACTIVATION_TIME)
                    .setExpirationTime(EXPIRATION_TIME)
                    .setUserBiddingSignals(VALID_BIDDING_SIGNALS)
                    .build();

    public static final DBPartialCustomAudience DB_PARTIAL_CUSTOM_AUDIENCE_3 =
            DBPartialCustomAudience.builder()
                    .setUpdateId(UPDATE_ID)
                    .setName(PARTIAL_CA_3)
                    .setActivationTime(ACTIVATION_TIME)
                    .setExpirationTime(EXPIRATION_TIME)
                    .setUserBiddingSignals(VALID_BIDDING_SIGNALS)
                    .build();

    public static final DBCustomAudienceToLeave DB_CUSTOM_AUDIENCE_TO_LEAVE_1 =
            DBCustomAudienceToLeave.builder().setUpdateId(UPDATE_ID).setName(LEAVE_CA_1).build();

    public static final DBCustomAudienceToLeave DB_CUSTOM_AUDIENCE_TO_LEAVE_2 =
            DBCustomAudienceToLeave.builder().setUpdateId(UPDATE_ID).setName(LEAVE_CA_2).build();

    public static final JSONArray CUSTOM_AUDIENCE_TO_LEAVE_JSON_ARRAY =
            new JSONArray(List.of(LEAVE_CA_1, LEAVE_CA_2));

    public static final int MIN_DELAY =
            FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MIN_DELAY_MINS_OVERRIDE;

    public static final PartialCustomAudience PARTIAL_CUSTOM_AUDIENCE_1 =
            new PartialCustomAudience.Builder(PARTIAL_CA_1).build();
    public static final PartialCustomAudience PARTIAL_CUSTOM_AUDIENCE_2 =
            new PartialCustomAudience.Builder(PARTIAL_CA_2).build();
    public static final PartialCustomAudience PARTIAL_CUSTOM_AUDIENCE_3 =
            new PartialCustomAudience.Builder(PARTIAL_CA_3).build();

    /** Get Partial Custom Audience 1 */
    public static JSONObject getPartialCustomAudience_1() throws JSONException {
        return generatePartialCustomAudienceFromName(PARTIAL_CA_1);
    }

    /** Get Partial Custom Audience 2 */
    public static JSONObject getPartialCustomAudience_2() throws JSONException {
        return generatePartialCustomAudienceFromName(PARTIAL_CA_2);
    }

    /** Get Partial Custom Audience Json Array */
    public static JSONArray getPartialCustomAudienceJsonArray() throws JSONException {
        return new JSONArray(List.of(getPartialCustomAudience_1(), getPartialCustomAudience_2()));
    }

    /** Get Schedule Request 1 */
    public static JSONObject getScheduleRequest_1() throws JSONException {
        return generateScheduleRequestFromCustomAudienceNames(
                BUYER,
                MIN_DELAY,
                List.of(PARTIAL_CA_1, PARTIAL_CA_2),
                List.of(LEAVE_CA_1, LEAVE_CA_2),
                true);
    }

    /** Get Schedule Request 1 */
    public static JSONObject getScheduleRequest_2() throws JSONException {
        return generateScheduleRequestFromCustomAudienceNames(
                BUYER, MIN_DELAY, List.of(PARTIAL_CA_3), List.of(LEAVE_CA_3), true);
    }

    /** Creates a JSON response that is expected to be returned from the server for update */
    public static JSONObject createJsonResponsePayload(
            AdTechIdentifier buyer,
            String owner,
            List<String> joinCustomAudienceNames,
            List<String> leaveCustomAudienceNames,
            boolean auctionServerRequestFlagsEnabled,
            boolean sellerConfigurationEnabled)
            throws JSONException {

        JSONObject responseJson = new JSONObject();

        JSONArray joinCustomAudienceArray = new JSONArray();
        for (int i = 0; i < joinCustomAudienceNames.size(); i++) {
            JSONObject generatedCa =
                    generateCustomAudienceWithName(buyer, owner, joinCustomAudienceNames.get(i));
            if (auctionServerRequestFlagsEnabled) {
                // Add auction server request flags
                generatedCa =
                        addAuctionServerRequestFlags(
                                generatedCa,
                                ImmutableList.of(CustomAudienceBlob.OMIT_ADS_VALUE),
                                false);
            }
            if (sellerConfigurationEnabled) {
                // give every CA a priority of 1.0
                generatedCa =
                        addPriority(
                                /* jsonObject */ generatedCa,
                                CustomAudienceFixture.VALID_PRIORITY_1,
                                /* shouldAddHarmlessJunk= */ false);
            }
            joinCustomAudienceArray.put(i, generatedCa);
        }

        JSONArray leaveCustomAudienceArray = new JSONArray();
        for (int i = 0; i < leaveCustomAudienceNames.size(); i++) {
            leaveCustomAudienceArray.put(i, leaveCustomAudienceNames.get(i));
        }

        responseJson.put(JOIN_CUSTOM_AUDIENCE_KEY, joinCustomAudienceArray);
        responseJson.put(LEAVE_CUSTOM_AUDIENCE_KEY, leaveCustomAudienceArray);

        return responseJson;
    }

    /**
     * Creates a JSON response that is expected to be returned from the server for update. The join
     * custom audiences in this request have invalid expiration time.
     */
    public static JSONObject createJsonResponsePayloadWithInvalidExpirationTime(
            AdTechIdentifier buyer, String owner, List<String> joinCustomAudienceNames)
            throws JSONException {

        JSONObject responseJson = new JSONObject();

        JSONArray joinCustomAudienceArray = new JSONArray();
        for (int i = 0; i < joinCustomAudienceNames.size(); i++) {
            JSONObject generatedCa =
                    generateCustomAudienceWithNameWithInvalidExpirationTime(
                            buyer, owner, joinCustomAudienceNames.get(i));
            joinCustomAudienceArray.put(i, generatedCa);
        }

        responseJson.put(JOIN_CUSTOM_AUDIENCE_KEY, joinCustomAudienceArray);

        return responseJson;
    }

    /** Creates a JSON response that is expected to be returned from the server for update */
    public static JSONObject createJsonResponsePayloadWithScheduleRequests(
            AdTechIdentifier buyer,
            String owner,
            List<String> joinCustomAudienceNames,
            List<String> leaveCustomAudienceNames,
            JSONArray scheduleRequests,
            boolean auctionServerRequestFlagsEnabled,
            boolean sellerConfigurationEnabled)
            throws JSONException {

        JSONObject responseJson = new JSONObject();

        JSONArray joinCustomAudienceArray = new JSONArray();
        for (int i = 0; i < joinCustomAudienceNames.size(); i++) {
            JSONObject generatedCa =
                    generateCustomAudienceWithName(buyer, owner, joinCustomAudienceNames.get(i));
            if (auctionServerRequestFlagsEnabled) {
                // Add auction server request flags
                generatedCa =
                        addAuctionServerRequestFlags(
                                generatedCa,
                                ImmutableList.of(CustomAudienceBlob.OMIT_ADS_VALUE),
                                false);
            }
            if (sellerConfigurationEnabled) {
                // give every CA a priority of 1.0
                generatedCa =
                        addPriority(
                                /* jsonObject */ generatedCa,
                                CustomAudienceFixture.VALID_PRIORITY_1,
                                /* shouldAddHarmlessJunk= */ false);
            }
            joinCustomAudienceArray.put(i, generatedCa);
        }

        JSONArray leaveCustomAudienceArray = new JSONArray();
        for (int i = 0; i < leaveCustomAudienceNames.size(); i++) {
            leaveCustomAudienceArray.put(i, leaveCustomAudienceNames.get(i));
        }

        JSONObject scheduleObject = new JSONObject();
        scheduleObject.put(REQUESTS_KEY, scheduleRequests);

        responseJson.put(JOIN_CUSTOM_AUDIENCE_KEY, joinCustomAudienceArray);
        responseJson.put(LEAVE_CUSTOM_AUDIENCE_KEY, leaveCustomAudienceArray);
        responseJson.put(SCHEDULE_REQUESTS_KEY, scheduleObject);

        return responseJson;
    }

    /**
     * Creates a JSON response with schedule requests that is expected to be returned from the
     * server for update
     */
    public static JSONObject createJsonResponsePayloadWithScheduleRequests(
            JSONArray scheduleRequests) throws JSONException {
        JSONObject scheduleObject = new JSONObject();
        scheduleObject.put(REQUESTS_KEY, scheduleRequests);

        JSONObject updateResponseJson = new JSONObject();
        updateResponseJson.put(SCHEDULE_REQUESTS_KEY, scheduleObject);

        return updateResponseJson;
    }

    /** Creates a Schedule Request JSONObject without an update uri */
    public static JSONObject generateScheduleRequestMissingUpdateUriKey() throws JSONException {
        JSONObject responseJson = new JSONObject();

        responseJson.put(MIN_DELAY_KEY, MIN_DELAY);
        responseJson.put(SHOULD_REPLACE_PENDING_UPDATES_KEY, true);
        responseJson.put(PARTIAL_CUSTOM_AUDIENCES_KEY, getPartialCustomAudienceJsonArray());
        responseJson.put(LEAVE_CUSTOM_AUDIENCE_KEY, CUSTOM_AUDIENCE_TO_LEAVE_JSON_ARRAY);
        return responseJson;
    }

    /**
     * Creates a JSON response with join and schedule requests that is expected to be returned from
     * the server for update
     */
    public static JSONObject createJsonResponseWithJoinAndScheduleRequests(
            JSONArray scheduleRequests) throws JSONException {
        JSONObject scheduleObject = new JSONObject();
        scheduleObject.put(REQUESTS_KEY, scheduleRequests);

        JSONObject updateResponseJson = new JSONObject();
        updateResponseJson.put(SCHEDULE_REQUESTS_KEY, scheduleObject);

        return updateResponseJson;
    }

    /**
     * Creates a JSON response that is expected to be returned from the server for update without
     * Leave CA fields
     */
    public static JSONObject createJsonResponsePayloadWithoutLeaveCA(
            AdTechIdentifier buyer,
            String owner,
            List<String> joinCustomAudienceNames,
            List<String> leaveCustomAudienceNames,
            boolean auctionServerRequestFlagsEnabled,
            boolean sellerConfigurationEnabled)
            throws JSONException {
        JSONObject responseJson = new JSONObject();

        JSONObject scheduleRequest =
                generateScheduleRequestFromCustomAudienceNames(
                        buyer, 40, joinCustomAudienceNames, leaveCustomAudienceNames, true);
        JSONArray scheduleRequests = new JSONArray(List.of(scheduleRequest));
        JSONObject scheduleObject = new JSONObject();
        scheduleObject.put(REQUESTS_KEY, scheduleRequests);
        responseJson.put(SCHEDULE_REQUESTS_KEY, scheduleObject);

        JSONArray joinCustomAudienceArray =
                createJoinCustomAudienceArray(
                        buyer,
                        owner,
                        joinCustomAudienceNames,
                        leaveCustomAudienceNames,
                        auctionServerRequestFlagsEnabled,
                        sellerConfigurationEnabled);
        responseJson.put(JOIN_CUSTOM_AUDIENCE_KEY, joinCustomAudienceArray);
        return responseJson;
    }

    private static JSONArray createJoinCustomAudienceArray(
            AdTechIdentifier buyer,
            String owner,
            List<String> joinCustomAudienceNames,
            List<String> leaveCustomAudienceNames,
            boolean auctionServerRequestFlagsEnabled,
            boolean sellerConfigurationEnabled)
            throws JSONException {
        JSONArray joinCustomAudienceArray = new JSONArray();
        for (int i = 0; i < joinCustomAudienceNames.size(); i++) {
            JSONObject generatedCa =
                    generateCustomAudienceWithName(buyer, owner, joinCustomAudienceNames.get(i));
            if (auctionServerRequestFlagsEnabled) {
                // Add auction server request flags
                generatedCa =
                        addAuctionServerRequestFlags(
                                generatedCa,
                                ImmutableList.of(CustomAudienceBlob.OMIT_ADS_VALUE),
                                false);
            }
            if (sellerConfigurationEnabled) {
                // give every CA a priority of 1.0
                generatedCa =
                        addPriority(
                                /* jsonObject */ generatedCa,
                                CustomAudienceFixture.VALID_PRIORITY_1,
                                /* shouldAddHarmlessJunk= */ false);
            }
            joinCustomAudienceArray.put(i, generatedCa);
        }
        return joinCustomAudienceArray;
    }

    /**
     * Creates a JSON response that with invalid join ca json object. The last CA in the JSON
     * response will be invalid, the first N - 1 will be valid.
     */
    public static JSONObject createJsonResponsePayloadInvalidJoinCA(
            AdTechIdentifier buyer,
            String owner,
            List<String> joinCustomAudienceNames,
            List<String> leaveCustomAudienceNames,
            boolean auctionServerRequestFlagsEnabled,
            boolean sellerConfigurationEnabled)
            throws JSONException {
        JSONObject responseJson = new JSONObject();

        JSONArray joinCustomAudienceArray = new JSONArray();
        // Inserting N - 1 valid join custom audience JSON
        for (int i = 0; i < joinCustomAudienceNames.size() - 1; i++) {
            JSONObject generatedCa =
                    generateCustomAudienceWithName(buyer, owner, joinCustomAudienceNames.get(i));
            if (auctionServerRequestFlagsEnabled) {
                // Add auction server request flags
                generatedCa =
                        addAuctionServerRequestFlags(
                                generatedCa,
                                ImmutableList.of(CustomAudienceBlob.OMIT_ADS_VALUE),
                                false);
            }
            if (sellerConfigurationEnabled) {
                // give every CA a priority of 1.0
                generatedCa =
                        addPriority(
                                /* jsonObject */ generatedCa,
                                CustomAudienceFixture.VALID_PRIORITY_1,
                                /* shouldAddHarmlessJunk= */ false);
            }
            joinCustomAudienceArray.put(i, generatedCa);
        }

        // Inserting invalid join CA JSON. This will insert the name as an array instead of a string
        JSONObject generatedCa = new JSONObject();
        generatedCa.append("name", "garbageName");
        joinCustomAudienceArray.put(joinCustomAudienceNames.size() - 1, generatedCa);

        responseJson.put(JOIN_CUSTOM_AUDIENCE_KEY, joinCustomAudienceArray);

        return responseJson;
    }

    /**
     * Creates a JSON response that is expected to be returned from the server for update without
     * join ca fields
     */
    public static JSONObject createJsonResponsePayloadWithoutJoinCA(
            AdTechIdentifier buyer,
            String owner,
            List<String> joinCustomAudienceNames,
            List<String> leaveCustomAudienceNames,
            boolean auctionServerRequestFlagsEnabled,
            boolean sellerConfigurationEnabled)
            throws JSONException {

        JSONObject responseJson = new JSONObject();

        JSONArray leaveCustomAudienceArray = new JSONArray();
        for (int i = 0; i < leaveCustomAudienceNames.size(); i++) {
            leaveCustomAudienceArray.put(i, leaveCustomAudienceNames.get(i));
        }
        responseJson.put(LEAVE_CUSTOM_AUDIENCE_KEY, leaveCustomAudienceArray);

        return responseJson;
    }

    /** Creates a CustomAudience JSONObject with the given buyer, owner and name. */
    public static JSONObject generateCustomAudienceWithName(
            AdTechIdentifier buyer, String owner, String name) throws JSONException {

        CustomAudience ca =
                CustomAudienceFixture.getValidBuilderForBuyer(buyer).setName(name).build();
        return CustomAudienceBlobFixture.asJSONObject(
                owner,
                ca.getBuyer(),
                name,
                ca.getActivationTime(),
                ca.getExpirationTime(),
                ca.getDailyUpdateUri(),
                ca.getBiddingLogicUri(),
                AdSelectionSignals.EMPTY.toString(),
                DBTrustedBiddingDataFixture.getValidBuilderByBuyer(buyer).build(),
                Collections.emptyList(),
                false);
    }

    /**
     * Creates a CustomAudience JSONObject with the given buyer, owner and name and with invalid
     * expiration date.
     */
    public static JSONObject generateCustomAudienceWithNameWithInvalidExpirationTime(
            AdTechIdentifier buyer, String owner, String name) throws JSONException {

        CustomAudience ca =
                CustomAudienceFixture.getValidBuilderForBuyer(buyer).setName(name).build();
        return CustomAudienceBlobFixture.asJSONObject(
                owner,
                ca.getBuyer(),
                name,
                ca.getActivationTime(),
                CustomAudienceFixture.INVALID_NOW_EXPIRATION_TIME,
                ca.getDailyUpdateUri(),
                ca.getBiddingLogicUri(),
                AdSelectionSignals.EMPTY.toString(),
                DBTrustedBiddingDataFixture.getValidBuilderByBuyer(buyer).build(),
                Collections.emptyList(),
                false);
    }

    /** Creates a Schedule Request JSONObject from custom audience names. */
    public static JSONObject generateScheduleRequestFromCustomAudienceNames(
            AdTechIdentifier buyer,
            int minDelay,
            List<String> partialCustomAudienceNames,
            List<String> leaveCustomAudienceNames,
            Boolean shouldReplacePendingUpdates)
            throws JSONException {
        JSONArray partialCustomAudiences = new JSONArray();
        for (int i = 0; i < partialCustomAudienceNames.size(); i++) {
            partialCustomAudiences.put(
                    i, generatePartialCustomAudienceFromName(partialCustomAudienceNames.get(i)));
        }

        JSONArray customAudiencesToLeave = new JSONArray();
        for (int i = 0; i < leaveCustomAudienceNames.size(); i++) {
            customAudiencesToLeave.put(i, leaveCustomAudienceNames.get(i));
        }

        return createScheduleRequest(
                buyer,
                minDelay,
                partialCustomAudiences,
                customAudiencesToLeave,
                shouldReplacePendingUpdates);
    }

    /** Creates a Schedule Request JSONObject from custom audience names with invalid partial CA. */
    public static JSONObject generateScheduleRequestFromCustomAudienceNamesWithInvalidPartialCA(
            AdTechIdentifier buyer,
            int minDelay,
            List<String> partialCustomAudienceNames,
            List<String> leaveCustomAudienceNames,
            Boolean shouldReplacePendingUpdates)
            throws JSONException {
        JSONArray partialCustomAudiences = new JSONArray();
        for (int i = 0; i < partialCustomAudienceNames.size(); i++) {
            partialCustomAudiences.put(i, new JSONObject());
        }

        JSONArray customAudiencesToLeave = new JSONArray();
        for (int i = 0; i < leaveCustomAudienceNames.size(); i++) {
            customAudiencesToLeave.put(i, leaveCustomAudienceNames.get(i));
        }

        return createScheduleRequest(
                buyer,
                minDelay,
                partialCustomAudiences,
                customAudiencesToLeave,
                shouldReplacePendingUpdates);
    }

    /** Creates a Schedule Request JSONObject */
    public static JSONObject createScheduleRequestWithUpdateUri(
            String updateUri,
            int minDelay,
            JSONArray partialCustomAudiences,
            JSONArray customAudiencesToLeave,
            Boolean shouldReplacePendingUpdates)
            throws JSONException {
        JSONObject responseJson = new JSONObject();

        responseJson.put(MIN_DELAY_KEY, minDelay);
        responseJson.put(UPDATE_URI_KEY, updateUri);
        responseJson.put(SHOULD_REPLACE_PENDING_UPDATES_KEY, shouldReplacePendingUpdates);
        responseJson.put(PARTIAL_CUSTOM_AUDIENCES_KEY, partialCustomAudiences);
        responseJson.put(LEAVE_CUSTOM_AUDIENCE_KEY, customAudiencesToLeave);

        return responseJson;
    }

    /** Creates a Schedule Request JSONObject */
    public static JSONObject createScheduleRequest(
            AdTechIdentifier buyer,
            int minDelay,
            JSONArray partialCustomAudiences,
            JSONArray customAudiencesToLeave,
            Boolean shouldReplacePendingUpdates)
            throws JSONException {
        JSONObject responseJson = new JSONObject();

        responseJson.put(MIN_DELAY_KEY, minDelay);
        responseJson.put(UPDATE_URI_KEY, CommonFixture.getUri(buyer, "/updateUri"));
        responseJson.put(SHOULD_REPLACE_PENDING_UPDATES_KEY, shouldReplacePendingUpdates);
        responseJson.put(PARTIAL_CUSTOM_AUDIENCES_KEY, partialCustomAudiences);
        responseJson.put(LEAVE_CUSTOM_AUDIENCE_KEY, customAudiencesToLeave);

        return responseJson;
    }

    /** Converts Schedule Request JsonObject to DBScheduledCustomAudienceUpdate */
    public static DBScheduledCustomAudienceUpdate
            convertScheduleRequestToDBScheduledCustomAudienceUpdate(
                    String owner, JSONObject scheduleRequest, Instant now) throws JSONException {
        Uri updateUri = Uri.parse(scheduleRequest.getString(UPDATE_URI_KEY));
        Duration minDelay = Duration.ofMinutes(scheduleRequest.getLong(MIN_DELAY_KEY));
        Instant scheduledTime = now.plus(minDelay.toMinutes(), ChronoUnit.MINUTES);

        return DBScheduledCustomAudienceUpdate.builder()
                .setUpdateUri(updateUri)
                .setOwner(owner)
                .setBuyer(AdTechIdentifier.fromString(updateUri.getHost()))
                .setCreationTime(now)
                .setScheduledTime(scheduledTime)
                .build();
    }

    /** Creates a Partial Custom Audience JSONObject from name */
    public static JSONObject generatePartialCustomAudienceFromName(String name)
            throws JSONException {
        return createPartialCustomAudience(
                name,
                CustomAudienceFixture.VALID_ACTIVATION_TIME,
                CustomAudienceFixture.VALID_EXPIRATION_TIME,
                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
    }

    /** Creates a Partial Custom Audience JSONObject */
    public static JSONObject createPartialCustomAudience(
            String name,
            Instant activationTime,
            Instant expirationTime,
            AdSelectionSignals userBiddingSignals)
            throws JSONException {
        JSONObject object = new JSONObject();
        object = addName(object, name, false);
        if (activationTime != null) {
            object = addActivationTime(object, activationTime, false);
        }
        if (expirationTime != null) {
            object = addExpirationTime(object, expirationTime, false);
        }
        if (userBiddingSignals != null) {
            object = addUserBiddingSignals(object, userBiddingSignals.toString(), false);
        }
        return object;
    }

    /**
     * Extracts the Partial Custom Audience objects sent in the update request. Helps validate that
     * the request to server had expected payload.
     */
    public static List<CustomAudienceBlob> extractPartialCustomAudiencesFromRequest(
            byte[] requestBody) {
        String requestBodyString = new String(requestBody);
        List<CustomAudienceBlob> overrideCustomAudienceBlobs = new ArrayList<>();
        try {
            JSONObject jsonObject = new JSONObject(requestBodyString);
            JSONArray jsonArray = jsonObject.getJSONArray(PARTIAL_CUSTOM_AUDIENCES_KEY);

            for (int i = 0; i < jsonArray.length(); i++) {
                CustomAudienceBlob blob = new CustomAudienceBlob();
                blob.overrideFromJSONObject(jsonArray.getJSONObject(i));
                overrideCustomAudienceBlobs.add(blob);
            }
        } catch (JSONException e) {
            sLogger.e(e, "Unable to extract partial CAs from request");
        }
        return overrideCustomAudienceBlobs;
    }

    /**
     * Extracts custom audiences to leave sent in the update request. Helps validate that the
     * request to server had expected payload.
     */
    public static List<String> extractCustomAudiencesToLeaveFromScheduleRequest(
            byte[] requestBody) {
        String requestBodyString = new String(requestBody);
        List<String> customAudiencesToLeave = new ArrayList<>();
        try {
            JSONObject jsonObject = new JSONObject(requestBodyString);
            JSONArray jsonArray = jsonObject.getJSONArray(LEAVE_CUSTOM_AUDIENCE_KEY);

            for (int i = 0; i < jsonArray.length(); i++) {
                customAudiencesToLeave.add(jsonArray.getString(i));
            }
        } catch (JSONException e) {
            sLogger.e(e, "Unable to extract CAs to leave from request");
        }
        return customAudiencesToLeave;
    }

    /** Create request body with partial custom audiences and custom audiences to leave */
    public static JSONObject createRequestBody(
            JSONArray partialCustomAudienceJsonArray,
            List<DBCustomAudienceToLeave> customAudienceToLeaveList)
            throws JSONException {
        JSONArray jsonArray = new JSONArray();

        for (int i = 0; i < customAudienceToLeaveList.size(); i++) {
            jsonArray.put(i, customAudienceToLeaveList.get(i).getName());
        }
        JSONObject jsonObject = new JSONObject();

        jsonObject.put(PARTIAL_CUSTOM_AUDIENCES_KEY, partialCustomAudienceJsonArray);
        jsonObject.put(
                AdditionalScheduleRequestsEnabledStrategyHelper.LEAVE_CUSTOM_AUDIENCE_KEY,
                jsonArray);

        return jsonObject;
    }

    /** Create request body with only partial custom audiences, this should only be used for v1 */
    public static String createRequestBodyWithOnlyPartialCustomAudiences(
            JSONArray partialCustomAudienceJsonArray) throws JSONException {
        JSONObject jsonObject = new JSONObject();

        jsonObject.put(PARTIAL_CUSTOM_AUDIENCES_KEY, partialCustomAudienceJsonArray);

        return jsonObject.toString();
    }

    /** Converts list of PartialCustomAudience to JSONArray */
    public static JSONArray partialCustomAudienceListToJsonArray(List<PartialCustomAudience> paList)
            throws JSONException {
        JSONArray jsonArray = new JSONArray();
        for (int i = 0; i < paList.size(); i++) {
            PartialCustomAudience pa = paList.get(i);
            jsonArray.put(
                    createPartialCustomAudience(
                            pa.getName(),
                            pa.getActivationTime(),
                            pa.getExpirationTime(),
                            pa.getUserBiddingSignals()));
        }
        return jsonArray;
    }

    /**
     * Verify if two JSONObject instances are content-equal by comparing their string
     * representations.
     */
    public static JSONObject eqJsonObject(JSONObject expected) {
        return argThat(
                actual -> {
                    try {
                        assertThat(expected.toString()).isEqualTo(actual.toString());
                        return true;
                    } catch (AssertionError e) {
                        return false;
                    }
                });
    }

    /**
     * Verify if two JSONArray instances are content-equal by comparing their string
     * representations.
     */
    public static JSONArray eqJsonArray(JSONArray expected) {
        return argThat(
                actual -> {
                    try {
                        assertThat(expected.toString()).isEqualTo(actual.toString());
                        return true;
                    } catch (AssertionError e) {
                        return false;
                    }
                });
    }

    /**
     * Test-callback that ensures latch is unlatched before we check for API request completion.
     * Also provides success or failure status of an API call.
     */
    public static class ScheduleUpdateTestCallback
            extends ScheduleCustomAudienceUpdateCallback.Stub {
        private final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        public ScheduleUpdateTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        public boolean isSuccess() {
            return mIsSuccess;
        }

        @Override
        public void onSuccess() {
            LoggerFactory.getFledgeLogger().v("Reporting success to Schedule CA Update.");
            mIsSuccess = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            LoggerFactory.getFledgeLogger().v("Reporting failure to Schedule CA Update.");
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }
}
