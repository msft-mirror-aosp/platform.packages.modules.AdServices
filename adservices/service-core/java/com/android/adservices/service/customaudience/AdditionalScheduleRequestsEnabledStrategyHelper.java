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

import static com.android.adservices.service.common.AppManifestConfigCall.API_CUSTOM_AUDIENCES;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.USER_BIDDING_SIGNALS_KEY;
import static com.android.adservices.service.customaudience.FetchCustomAudienceReader.ACTIVATION_TIME_KEY;
import static com.android.adservices.service.customaudience.FetchCustomAudienceReader.EXPIRATION_TIME_KEY;
import static com.android.adservices.service.customaudience.FetchCustomAudienceReader.NAME_KEY;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.PartialCustomAudience;
import android.content.Context;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.customaudience.DBScheduledCustomAudienceUpdate;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.devapi.DevContext;
import com.android.internal.util.Preconditions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RequiresApi(Build.VERSION_CODES.S)
public class AdditionalScheduleRequestsEnabledStrategyHelper {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    protected static final int MIN_DELAY_TIME_MINUTES = 30;
    protected static final int MAX_DELAY_TIME_MINUTES = 300;
    protected static final String LEAVE_CUSTOM_AUDIENCE_KEY = "leave";
    protected static final String PARTIAL_CUSTOM_AUDIENCES_KEY = "partial_custom_audience_data";
    protected static final String SCHEDULE_REQUESTS_KEY = "schedule";
    protected static final String REQUESTS_KEY = "requests";
    protected static final String MIN_DELAY_KEY = "min_delay";
    protected static final String UPDATE_URI_KEY = "update_uri";
    protected static final String SHOULD_REPLACE_PENDING_UPDATES_KEY =
            "should_replace_pending_updates";

    private final int mFledgeScheduleCustomAudienceMinDelayMinsOverride;
    private final boolean mDisableFledgeEnrollmentCheck;

    private final Context mContext;
    private final FledgeAuthorizationFilter mFledgeAuthorizationFilter;

    AdditionalScheduleRequestsEnabledStrategyHelper(
            Context context,
            FledgeAuthorizationFilter fledgeAuthorizationFilter,
            int minDelayMinsOverride,
            boolean disableFledgeEnrollmentCheck) {
        mContext = context;
        mFledgeAuthorizationFilter = fledgeAuthorizationFilter;
        mFledgeScheduleCustomAudienceMinDelayMinsOverride = minDelayMinsOverride;
        mDisableFledgeEnrollmentCheck = disableFledgeEnrollmentCheck;
    }

    /**
     * This method should only be called when scheduling additional requests. In order to keep the
     * flow in two hops, it sets the {@link
     * DBScheduledCustomAudienceUpdate#getAllowScheduleInResponse()} flag false.
     */
    DBScheduledCustomAudienceUpdate validateAndConvertScheduleRequest(
            String owner, JSONObject scheduleRequest, Instant now, DevContext devContext)
            throws IllegalArgumentException,
                    JSONException,
                    FledgeAuthorizationFilter.AdTechNotAllowedException {

        try {
            Uri updateUri = Uri.parse(scheduleRequest.getString(UPDATE_URI_KEY));
            Duration minDelay = Duration.ofMinutes(scheduleRequest.getLong(MIN_DELAY_KEY));
            Instant scheduledTime = now.plus(minDelay.toMinutes(), ChronoUnit.MINUTES);

            validateDelayTime(minDelay);
            AdTechIdentifier buyer = validateAndExtractIdentifier(updateUri, owner);

            return DBScheduledCustomAudienceUpdate.builder()
                    .setUpdateUri(updateUri)
                    .setOwner(owner)
                    .setBuyer(buyer)
                    .setCreationTime(now)
                    .setScheduledTime(scheduledTime)
                    .setIsDebuggable(devContext.getDeviceDevOptionsEnabled())
                    .setAllowScheduleInResponse(false)
                    .build();
        } catch (Throwable e) {
            sLogger.e(e, "Cannot convert schedule request json to DBScheduledCustomAudienceUpdate");
            throw new JSONException(e);
        }
    }

    List<JSONObject> extractScheduleRequestsFromResponse(JSONObject updateResponseJson) {
        JSONArray jsonArray;
        try {
            jsonArray =
                    updateResponseJson
                            .getJSONObject(SCHEDULE_REQUESTS_KEY)
                            .getJSONArray(REQUESTS_KEY);
        } catch (Throwable e) {
            sLogger.d(e, "There are no schedule requests in the request");
            return new ArrayList<>();
        }

        List<JSONObject> scheduleRequestJsonList = new ArrayList<>();

        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                scheduleRequestJsonList.add(jsonArray.getJSONObject(i));
            } catch (Throwable e) {
                sLogger.e(e, "Invalid Schedule Request object, skipping");
            }
        }

        sLogger.v(
                "No of schedule requests obtained from update: %s", scheduleRequestJsonList.size());

        return scheduleRequestJsonList;
    }

    List<PartialCustomAudience> extractPartialCustomAudiencesFromRequest(JSONObject requestJson)
            throws JSONException {
        JSONArray jsonArray;
        try {
            jsonArray = requestJson.getJSONArray(PARTIAL_CUSTOM_AUDIENCES_KEY);
        } catch (JSONException e) {
            sLogger.d(e, "There are no partial custom audiences in the request");
            return new ArrayList<>();
        }
        List<PartialCustomAudience> partialCustomAudiences = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                partialCustomAudiences.add(jsonObjectToPartialCustomAudience(jsonObject));
            } catch (Throwable e) {
                sLogger.e(
                        e,
                        "Invalid Partial Custom Audience object, skipping the schedule CA request");
                throw new JSONException(e);
            }
        }
        sLogger.d(
                "No of partial CAs to join obtained from request: %s",
                partialCustomAudiences.size());

        return partialCustomAudiences;
    }

    List<String> extractCustomAudiencesToLeaveFromRequest(JSONObject scheduleRequest)
            throws JSONException {
        JSONArray jsonArray;
        try {
            jsonArray = scheduleRequest.getJSONArray(LEAVE_CUSTOM_AUDIENCE_KEY);
        } catch (JSONException e) {
            sLogger.e(e, "There are no custom audiences to leave in the request");
            return new ArrayList<>();
        }

        List<String> customAudienceToLeaveList = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                String caName = jsonArray.getString(i);
                Preconditions.checkStringNotEmpty(caName);
                customAudienceToLeaveList.add(caName);
            } catch (Throwable e) {
                sLogger.e(
                        e,
                        "Invalid Custom Audience to leave object, skipping the schedule CA"
                                + " request");
                throw new JSONException(e);
            }
        }
        sLogger.d("No of CAs to leave obtained from request: %s", customAudienceToLeaveList.size());

        return customAudienceToLeaveList;
    }

    PartialCustomAudience jsonObjectToPartialCustomAudience(JSONObject partialCaJson)
            throws JSONException {
        String name = partialCaJson.getString(NAME_KEY);
        PartialCustomAudience.Builder builder = new PartialCustomAudience.Builder(name);

        if (partialCaJson.has(ACTIVATION_TIME_KEY)) {
            Instant activationTime =
                    Instant.ofEpochMilli(partialCaJson.getLong(ACTIVATION_TIME_KEY));
            builder.setActivationTime(activationTime);
        }
        if (partialCaJson.has(EXPIRATION_TIME_KEY)) {
            Instant expirationTime =
                    Instant.ofEpochMilli(partialCaJson.getLong(EXPIRATION_TIME_KEY));
            builder.setExpirationTime(expirationTime);
        }
        if (partialCaJson.has(USER_BIDDING_SIGNALS_KEY)) {
            AdSelectionSignals userBiddingSignals =
                    AdSelectionSignals.fromString(
                            partialCaJson.getJSONObject(USER_BIDDING_SIGNALS_KEY).toString());
            builder.setUserBiddingSignals(userBiddingSignals);
        }
        return builder.build();
    }

    private void validateDelayTime(Duration delayTime) throws IllegalArgumentException {
        int minTimeDelayMinutes =
                Math.min(MIN_DELAY_TIME_MINUTES, mFledgeScheduleCustomAudienceMinDelayMinsOverride);
        if (delayTime.toMinutes() < minTimeDelayMinutes
                || delayTime.toMinutes() > MAX_DELAY_TIME_MINUTES) {
            sLogger.e("Delay Time not within permissible limits");
            throw new IllegalArgumentException("Delay Time not within permissible limits");
        }
    }

    private AdTechIdentifier validateAndExtractIdentifier(
            Uri uriForAdTech, String callerPackageName)
            throws FledgeAuthorizationFilter.AdTechNotAllowedException, NullPointerException {
        AdTechIdentifier adTech;
        if (mDisableFledgeEnrollmentCheck) {
            sLogger.v("Using URI host as ad tech's identifier.");
            adTech = AdTechIdentifier.fromString(Objects.requireNonNull(uriForAdTech.getHost()));
        } else {
            sLogger.v("Extracting ad tech's eTLD+1 identifier.");
            adTech =
                    mFledgeAuthorizationFilter.getAndAssertAdTechFromUriAllowed(
                            mContext,
                            callerPackageName,
                            uriForAdTech,
                            AD_SERVICES_API_CALLED__API_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE,
                            API_CUSTOM_AUDIENCES);
        }
        return adTech;
    }
}
