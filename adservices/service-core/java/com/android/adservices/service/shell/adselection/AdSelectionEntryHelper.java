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

package com.android.adservices.service.shell.adselection;


import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.DBAdSelectionEntry;
import com.android.adservices.data.adselection.datahandlers.ReportingData;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

/** Helper for parting {@link DBAdSelectionEntry} objects into JSON. */
public class AdSelectionEntryHelper {

    @VisibleForTesting public static final String FIELD_AD_SELECTION_ID = "ad_selection_id";

    @VisibleForTesting public static final String FIELD_CREATION_TIMESTAMP = "timestamp";

    @VisibleForTesting public static final String FIELD_WINNING_AD_BID = "winning_bid";

    @VisibleForTesting
    public static final String FIELD_WINNING_AD_RENDER_URI = "winning_ad_render_uri";

    @VisibleForTesting
    public static final String FIELD_CUSTOM_AUDIENCE_SIGNALS = "custom_audience_signals";

    @VisibleForTesting public static final String FIELD_CUSTOM_AUDIENCE_SIGNALS_BUYER = "buyer";
    @VisibleForTesting public static final String FIELD_CUSTOM_AUDIENCE_SIGNALS_OWNER = "owner";
    @VisibleForTesting public static final String FIELD_CUSTOM_AUDIENCE_SIGNALS_NAME = "name";

    @VisibleForTesting
    public static final String FIELD_CUSTOM_AUDIENCE_SIGNALS_ACTIVATION_TIME = "activation_time";

    @VisibleForTesting
    public static final String FIELD_CUSTOM_AUDIENCE_SIGNALS_EXPIRATION_TIME = "expiration_time";

    @VisibleForTesting
    public static final String FIELD_CUSTOM_AUDIENCE_SIGNALS_USER_BIDDING_SIGNALS =
            "user_bidding_signals";

    @VisibleForTesting
    public static final String FIELD_BUYER_CONTEXTUAL_SIGNALS = "buyer_contextual_signals";

    @VisibleForTesting
    public static final String FIELD_BUYER_DECISION_LOGIC_JS = "buyer_decision_logic_js";

    @VisibleForTesting public static final String FIELD_BIDDING_LOGIC_URI = "bidding_logic_uri";

    @VisibleForTesting
    public static final String FIELD_SELLER_CONTEXTUAL_SIGNALS = "seller_contextual_signals";

    @VisibleForTesting
    public static final String FIELD_BUYER_WIN_REPORTING_URI = "buyer_win_reporting_uri";

    @VisibleForTesting
    public static final String FIELD_SELLER_WIN_REPORTING_URI = "seller_win_reporting_uri";

    /**
     * Parse an {@link DBAdSelectionEntry} into JSON format.
     *
     * <p>Not all fields are encoded so look at the implementation and various field constants if
     * you need to know or update them.
     *
     * @param adSelectionEntry The entry to parse.
     * @return An instance of {@link JSONObject} containing fields from {@link DBAdSelectionEntry}.
     * @throws JSONException If the parsing fails.
     */
    public static JSONObject getJsonFromAdSelectionEntry(
            DBAdSelectionEntry adSelectionEntry, ReportingData reportingUris) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(FIELD_AD_SELECTION_ID, adSelectionEntry.getAdSelectionId());
        jsonObject.put(FIELD_CREATION_TIMESTAMP, adSelectionEntry.getCreationTimestamp());
        jsonObject.put(FIELD_WINNING_AD_BID, adSelectionEntry.getWinningAdBid());
        jsonObject.put(FIELD_WINNING_AD_RENDER_URI, adSelectionEntry.getWinningAdRenderUri());
        jsonObject.put(FIELD_BUYER_WIN_REPORTING_URI, "none");
        jsonObject.put(FIELD_SELLER_WIN_REPORTING_URI, "none");
        if (reportingUris != null) {
            if (reportingUris.getBuyerWinReportingUri() != null) {
                jsonObject.put(
                        FIELD_BUYER_WIN_REPORTING_URI, reportingUris.getBuyerWinReportingUri());
            }

            if (reportingUris.getSellerWinReportingUri() != null) {
                jsonObject.put(
                        FIELD_SELLER_WIN_REPORTING_URI, reportingUris.getSellerWinReportingUri());
            }
        }
        if (adSelectionEntry.getCustomAudienceSignals() != null) {
            jsonObject.put(
                    FIELD_CUSTOM_AUDIENCE_SIGNALS,
                    getJsonFromCustomAudienceSignals(adSelectionEntry.getCustomAudienceSignals()));
        }
        // TODO(b/354392848): Add ad counter keys to class.
        if (adSelectionEntry.getSellerContextualSignals() != null) {
            jsonObject.put(
                    FIELD_SELLER_CONTEXTUAL_SIGNALS, adSelectionEntry.getSellerContextualSignals());
        }
        jsonObject.put(
                FIELD_BUYER_CONTEXTUAL_SIGNALS, adSelectionEntry.getBuyerContextualSignals());
        jsonObject.put(FIELD_BUYER_DECISION_LOGIC_JS, adSelectionEntry.getBuyerDecisionLogicJs());
        jsonObject.put(FIELD_BIDDING_LOGIC_URI, adSelectionEntry.getBiddingLogicUri());

        return jsonObject;
    }

    private static JSONObject getJsonFromCustomAudienceSignals(
            CustomAudienceSignals customAudienceSignals) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(FIELD_CUSTOM_AUDIENCE_SIGNALS_BUYER, customAudienceSignals.getBuyer());
        jsonObject.put(FIELD_CUSTOM_AUDIENCE_SIGNALS_OWNER, customAudienceSignals.getOwner());
        jsonObject.put(FIELD_CUSTOM_AUDIENCE_SIGNALS_NAME, customAudienceSignals.getName());
        jsonObject.put(
                FIELD_CUSTOM_AUDIENCE_SIGNALS_ACTIVATION_TIME,
                customAudienceSignals.getActivationTime());
        jsonObject.put(
                FIELD_CUSTOM_AUDIENCE_SIGNALS_EXPIRATION_TIME,
                customAudienceSignals.getExpirationTime());
        jsonObject.put(
                FIELD_CUSTOM_AUDIENCE_SIGNALS_USER_BIDDING_SIGNALS,
                customAudienceSignals.getUserBiddingSignals().toString());
        return jsonObject;
    }
}
