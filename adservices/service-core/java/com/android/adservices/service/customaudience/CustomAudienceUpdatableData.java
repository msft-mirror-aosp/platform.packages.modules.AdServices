/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** This class represents the result of a daily fetch that will update a custom audience. */
@AutoValue
public abstract class CustomAudienceUpdatableData {
    public static final String USER_BIDDING_SIGNALS_KEY = "user_bidding_signals";
    public static final String TRUSTED_BIDDING_DATA_KEY = "trusted_bidding_data";
    public static final String TRUSTED_BIDDING_URL_KEY = "trusted_bidding_url";
    public static final String TRUSTED_BIDDING_KEYS_KEY = "trusted_bidding_keys";
    public static final String ADS_KEY = "ads";
    public static final String RENDER_URL_KEY = "render_url";
    public static final String METADATA_KEY = "metadata";

    /**
     * @return the user bidding signals as a JSON object serialized as a string that were sent in
     *     the update response. If there were no valid user bidding signals, returns {@code null}.
     */
    @Nullable
    public abstract String getUserBiddingSignals();

    /**
     * @return trusted bidding data that was sent in the update response. If no valid trusted
     *     bidding data was found, returns {@code null}.
     */
    @Nullable
    public abstract DBTrustedBiddingData getTrustedBiddingData();

    /**
     * @return the list of ads that were sent in the update response. If no valid ads were sent,
     *     returns {@code null}.
     */
    @Nullable
    public abstract ImmutableList<DBAdData> getAds();

    /** @return the time at which the custom audience update was attempted */
    @NonNull
    public abstract Instant getAttemptedUpdateTime();

    /**
     * @return the result type for the update attempt before {@link
     *     #createFromResponseString(Instant, BackgroundFetchRunner.UpdateResultType, String)} was
     *     called
     */
    public abstract BackgroundFetchRunner.UpdateResultType getInitialUpdateResult();

    /**
     * Returns whether this object represents a successful update.
     *
     * <ul>
     *   <li>An empty response is valid, representing that the buyer does not want to update its
     *       custom audience.
     *   <li>If a response is not empty but fails to be parsed into a JSON object, it will be
     *       considered a failed response which does not contain a successful update.
     *   <li>If a response is not empty and is parsed successfully into a JSON object but does not
     *       contain any units of updatable data, it is considered empty (albeit full of junk) and
     *       valid, representing that the buyer does not want to update its custom audience.
     *   <li>A non-empty response that contains relevant fields but which all fail to be parsed into
     *       valid objects is considered a failed update. This might happen if fields are found but
     *       do not follow the correct schema/expected object types.
     *   <li>A non-empty response that is not completely invalid and which does have at least one
     *       successful field is considered successful.
     * </ul>
     *
     * @return {@code true} if this object represents a successful update; otherwise, {@code false}
     */
    public abstract boolean getContainsSuccessfulUpdate();

    /**
     * Creates a {@link CustomAudienceUpdatableData} object based on the response of a GET request
     * to a custom audience's daily fetch URL.
     *
     * <p>Note that if a response contains extra fields in its JSON, the extra information will be
     * ignored, and the validation of the response will continue as if the extra data had not been
     * included. For example, if {@code trusted_bidding_data} contains an extra field {@code
     * campaign_ids} (which is not considered part of the {@code trusted_bidding_data} JSON schema),
     * the resulting {@link CustomAudienceUpdatableData} object will not be built with the extra
     * data.
     *
     * <p>See {@link #getContainsSuccessfulUpdate()} for more details.
     *
     * @param attemptedUpdateTime the time at which the update for this custom audience was
     *     attempted
     * @param initialUpdateResult the result type of the fetch attempt prior to parsing the {@code
     *     response}
     * @param response the String response returned from querying the custom audience's daily fetch
     *     URL
     */
    @NonNull
    public static CustomAudienceUpdatableData createFromResponseString(
            @NonNull Instant attemptedUpdateTime,
            BackgroundFetchRunner.UpdateResultType initialUpdateResult,
            @NonNull final String response) {
        Objects.requireNonNull(response);

        // Use the hash of the response string as a session identifier for logging purposes
        final String responseHash = "[" + response.hashCode() + "]";
        LogUtil.v("Parsing JSON response string with hash %s", responseHash);

        // By default unset nullable AutoValue fields are null
        CustomAudienceUpdatableData.Builder dataBuilder =
                builder()
                        .setAttemptedUpdateTime(attemptedUpdateTime)
                        .setContainsSuccessfulUpdate(false)
                        .setInitialUpdateResult(initialUpdateResult);

        // No need to continue if an error occurred upstream for this custom audience update
        if (initialUpdateResult != BackgroundFetchRunner.UpdateResultType.SUCCESS) {
            LogUtil.v("%s Skipping response string parsing due to upstream failure", responseHash);
            dataBuilder.setContainsSuccessfulUpdate(false);
            return dataBuilder.build();
        }

        if (response.isEmpty()) {
            LogUtil.v("%s Response string was empty", responseHash);
            dataBuilder.setContainsSuccessfulUpdate(true);
            return dataBuilder.build();
        }

        JSONObject responseObject;
        try {
            responseObject = new JSONObject(response);
        } catch (JSONException exception) {
            LogUtil.e("%s Error parsing JSON response into an object", responseHash);
            dataBuilder.setContainsSuccessfulUpdate(false);
            return dataBuilder.build();
        }

        boolean foundUserBiddingSignals = true;
        boolean errorParsingUserBiddingSignals = false;
        boolean foundTrustedBiddingData = true;
        boolean errorParsingTrustedBiddingData = false;
        boolean foundAds = true;
        boolean errorParsingAds = false;

        // TODO(b/233739309): Implement data validation for the number of ads allowed, per-field
        //  size constraints, URL schema, etc.

        try {
            dataBuilder.setUserBiddingSignals(getUserBiddingSignalsFromJsonObject(responseObject));
            LogUtil.v("%s Found valid %s in JSON response", responseHash, USER_BIDDING_SIGNALS_KEY);
        } catch (JSONException | NullPointerException exception) {
            LogUtil.e(
                    exception,
                    "%s Invalid JSON type while parsing %s found in JSON response",
                    responseHash,
                    USER_BIDDING_SIGNALS_KEY);
            errorParsingUserBiddingSignals = true;
            dataBuilder.setUserBiddingSignals(null);
        } catch (IllegalArgumentException ignoredException) {
            LogUtil.v("%s %s not found in JSON response", responseHash, USER_BIDDING_SIGNALS_KEY);
            foundUserBiddingSignals = false;
            dataBuilder.setUserBiddingSignals(null);
        }

        try {
            dataBuilder.setTrustedBiddingData(
                    getTrustedBiddingDataFromJsonObject(responseObject, responseHash));
            LogUtil.v("%s Found valid %s in JSON response", responseHash, TRUSTED_BIDDING_DATA_KEY);
        } catch (JSONException | NullPointerException exception) {
            LogUtil.e(
                    exception,
                    "%s Invalid JSON type while parsing %s found in JSON response",
                    responseHash,
                    TRUSTED_BIDDING_DATA_KEY);
            errorParsingTrustedBiddingData = true;
            dataBuilder.setTrustedBiddingData(null);
        } catch (IllegalArgumentException ignoredException) {
            LogUtil.v("%s %s not found in JSON response", responseHash, TRUSTED_BIDDING_DATA_KEY);
            foundTrustedBiddingData = false;
            dataBuilder.setTrustedBiddingData(null);
        }

        try {
            dataBuilder.setAds(getAdsFromJsonObject(responseObject, responseHash));
            LogUtil.v("%s Found valid %s in JSON response", responseHash, ADS_KEY);
        } catch (JSONException | NullPointerException exception) {
            LogUtil.e(
                    exception,
                    "%s Invalid JSON type while parsing %s found in JSON response",
                    responseHash,
                    ADS_KEY);
            errorParsingAds = true;
            dataBuilder.setAds(null);
        } catch (IllegalArgumentException ignoredException) {
            LogUtil.v("%s %s not found in JSON response", responseHash, ADS_KEY);
            foundAds = false;
            dataBuilder.setAds(null);
        }

        // If there were no useful fields found, or if there was something useful found and
        // successfully updated, then this object should signal a successful update.
        boolean containsSuccessfulUpdate =
                !(foundUserBiddingSignals || foundTrustedBiddingData || foundAds)
                        || (foundUserBiddingSignals && !errorParsingUserBiddingSignals)
                        || (foundTrustedBiddingData && !errorParsingTrustedBiddingData)
                        || (foundAds && !errorParsingAds);
        LogUtil.v(
                "%s Completed parsing JSON response with containsSuccessfulUpdate = %b",
                responseHash, containsSuccessfulUpdate);
        dataBuilder.setContainsSuccessfulUpdate(containsSuccessfulUpdate);

        return dataBuilder.build();
    }

    /**
     * Returns the user bidding signals extracted from the input object, if found.
     *
     * @throws IllegalArgumentException if the {@code responseObject} does not contain user bidding
     *     signals
     * @throws JSONException if the key is found but the schema is incorrect
     * @throws NullPointerException if the key found by the field is null
     */
    @VisibleForTesting
    @NonNull
    public static String getUserBiddingSignalsFromJsonObject(@NonNull JSONObject responseObject)
            throws IllegalArgumentException, JSONException, NullPointerException {
        if (responseObject.has(USER_BIDDING_SIGNALS_KEY)) {
            JSONObject signalsJsonObj =
                    Objects.requireNonNull(responseObject.getJSONObject(USER_BIDDING_SIGNALS_KEY));
            return signalsJsonObj.toString();
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Returns the trusted bidding data extracted from the input object, if found.
     *
     * @throws IllegalArgumentException if the {@code responseObject} does not contain trusted
     *     bidding data
     * @throws JSONException if the key is found but the schema is incorrect
     * @throws NullPointerException if the key found by the field is null
     */
    @VisibleForTesting
    @NonNull
    public static DBTrustedBiddingData getTrustedBiddingDataFromJsonObject(
            @NonNull JSONObject responseObject, @NonNull String responseHash)
            throws IllegalArgumentException, JSONException, NullPointerException {
        if (responseObject.has(TRUSTED_BIDDING_DATA_KEY)) {
            JSONObject dataJsonObj = responseObject.getJSONObject(TRUSTED_BIDDING_DATA_KEY);

            String urlString = dataJsonObj.getString(TRUSTED_BIDDING_URL_KEY);
            Uri parsedUrl = Uri.parse(urlString);

            JSONArray keysJsonArray = dataJsonObj.getJSONArray(TRUSTED_BIDDING_KEYS_KEY);
            int keysListLength = keysJsonArray.length();
            List<String> keysList = new ArrayList<>(keysListLength);
            for (int i = 0; i < keysListLength; i++) {
                try {
                    // Note: getString() coerces values to be strings; use get() instead
                    Object key = keysJsonArray.get(i);
                    if (key instanceof String) {
                        keysList.add(Objects.requireNonNull((String) key));
                    } else {
                        LogUtil.v(
                                "%s Invalid JSON type while parsing a single key in the %s found"
                                        + " in JSON response; ignoring and continuing",
                                responseHash, TRUSTED_BIDDING_KEYS_KEY);
                    }
                } catch (JSONException | NullPointerException ignoredException) {
                    // Skip any keys that are malformed and continue to the next in the list; note
                    // that if the entire given list of keys is junk, then any existing trusted
                    // bidding keys are cleared from the custom audience
                    LogUtil.v(
                            "%s Invalid JSON type while parsing a single key in the %s found in"
                                    + " JSON response; ignoring and continuing",
                            responseHash, TRUSTED_BIDDING_KEYS_KEY);
                }
            }

            return new DBTrustedBiddingData.Builder().setUrl(parsedUrl).setKeys(keysList).build();
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Returns the list of ads extracted from the input object, if found.
     *
     * @throws IllegalArgumentException if the {@code responseObject} does not contain any ads
     * @throws JSONException if the key is found but the schema is incorrect
     * @throws NullPointerException if the key found by the field is null
     */
    @VisibleForTesting
    @NonNull
    public static List<DBAdData> getAdsFromJsonObject(
            @NonNull JSONObject responseObject, @NonNull String responseHash)
            throws IllegalArgumentException, JSONException, NullPointerException {
        if (responseObject.has(ADS_KEY)) {
            JSONArray adsJsonArray = responseObject.getJSONArray(ADS_KEY);
            int adsListLength = adsJsonArray.length();
            List<DBAdData> adsList = new ArrayList<>();
            for (int i = 0; i < adsListLength; i++) {
                try {
                    JSONObject adDataJsonObj = adsJsonArray.getJSONObject(i);

                    String urlString = adDataJsonObj.getString(RENDER_URL_KEY);
                    Uri parsedUrl = Uri.parse(urlString);

                    String metadata = Objects.requireNonNull(adDataJsonObj.getString(METADATA_KEY));

                    DBAdData adData =
                            new DBAdData.Builder()
                                    .setRenderUri(parsedUrl)
                                    .setMetadata(metadata)
                                    .build();
                    adsList.add(adData);
                } catch (JSONException | NullPointerException ignoredException) {
                    // Skip any ads that are malformed and continue to the next in the list; note
                    // that if the entire given list of ads is junk, then any existing ads are
                    // cleared from the custom audience
                    LogUtil.v(
                            "%s Invalid JSON type while parsing a single ad in the %s found in"
                                    + " JSON response; ignoring and continuing",
                            responseHash, ADS_KEY);
                }
            }

            return adsList;
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Gets a Builder to make {@link #createFromResponseString(Instant,
     * BackgroundFetchRunner.UpdateResultType, String)} easier.
     */
    @VisibleForTesting
    @NonNull
    public static CustomAudienceUpdatableData.Builder builder() {
        return new AutoValue_CustomAudienceUpdatableData.Builder();
    }

    /**
     * This is a hidden (visible for testing) AutoValue builder to make {@link
     * #createFromResponseString(Instant, BackgroundFetchRunner.UpdateResultType, String)} easier.
     */
    @VisibleForTesting
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the user bidding signals found in the response string. */
        @NonNull
        public abstract Builder setUserBiddingSignals(@Nullable String value);

        /** Sets the trusted bidding data found in the response string. */
        @NonNull
        public abstract Builder setTrustedBiddingData(@Nullable DBTrustedBiddingData value);

        /** Sets the list of ads found in the response string. */
        @NonNull
        public abstract Builder setAds(@Nullable List<DBAdData> value);

        /** Sets the time at which the custom audience update was attempted. */
        @NonNull
        public abstract Builder setAttemptedUpdateTime(@NonNull Instant value);

        /** Sets the result of the update prior to parsing the response string. */
        @NonNull
        public abstract Builder setInitialUpdateResult(
                BackgroundFetchRunner.UpdateResultType value);

        /**
         * Sets whether the response contained a successful update.
         *
         * <p>See {@link #getContainsSuccessfulUpdate()} for more details.
         */
        @NonNull
        public abstract Builder setContainsSuccessfulUpdate(boolean value);

        /**
         * Builds the {@link CustomAudienceUpdatableData} object and returns it.
         *
         * <p>Note that AutoValue doesn't by itself do any validation, so splitting the builder with
         * a manual verification is recommended. See go/autovalue/builders-howto#validate for more
         * information.
         */
        @NonNull
        protected abstract CustomAudienceUpdatableData autoValueBuild();

        /** Builds, validates, and returns the {@link CustomAudienceUpdatableData} object. */
        @NonNull
        public final CustomAudienceUpdatableData build() {
            CustomAudienceUpdatableData updatableData = autoValueBuild();

            Preconditions.checkArgument(
                    updatableData.getContainsSuccessfulUpdate()
                            || (updatableData.getUserBiddingSignals() == null
                                    && updatableData.getTrustedBiddingData() == null
                                    && updatableData.getAds() == null),
                    "CustomAudienceUpdatableData should not contain non-null updatable fields if"
                            + " the object does not represent a successful update");

            return updatableData;
        }
    }
}
