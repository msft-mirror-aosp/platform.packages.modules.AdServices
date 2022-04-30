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

package com.android.adservices.service.adselection;

import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.customaudience.DBCustomAudience;

import java.util.Objects;

/**
 * Represents the data with respect to CustomAudience that will be passed as part of output of the
 * bidding workflow to the persisting ad selection data workflow. */
 /** TODO(b/230554731): build the CustomAudienceAuctionInfo class with AutoValue. */
public class CustomAudienceBiddingInfo {
    @NonNull private final Uri mBiddingLogicUrl;
    @NonNull private final String mBuyerDecisionLogicJs;
    @NonNull private final CustomAudienceSignals mCustomAudienceSignals;

    /** Gets the biddingLogicUrl to fetch javascript. */
    @NonNull
    public Uri getBiddingLogicUrl() {
        return mBiddingLogicUrl;
    }

    /** Get the javascript fetched via the biddingLogicUrl. */
    @NonNull
    public String getBuyerDecisionLogicJs() {
        return mBuyerDecisionLogicJs;
    }

    /** Get the {@link CustomAudienceSignals} generated from the {@link DBCustomAudience}. */
    @NonNull
    public CustomAudienceSignals getCustomAudienceSignals() {
        return mCustomAudienceSignals;
    }

    public CustomAudienceBiddingInfo(
            @NonNull Uri biddingLogicUrl,
            @NonNull String buyerDecisionLogicJs,
            @NonNull CustomAudienceSignals customAudienceSignals) {
        Objects.requireNonNull(biddingLogicUrl);
        Objects.requireNonNull(buyerDecisionLogicJs);
        Objects.requireNonNull(customAudienceSignals);

        mBiddingLogicUrl = biddingLogicUrl;
        mBuyerDecisionLogicJs = buyerDecisionLogicJs;
        mCustomAudienceSignals = customAudienceSignals;
    }

    public CustomAudienceBiddingInfo(
            @NonNull DBCustomAudience customAudience, @NonNull String buyerDecisionLogicJs) {
        Objects.requireNonNull(customAudience);
        Objects.requireNonNull(buyerDecisionLogicJs);

        mBiddingLogicUrl = customAudience.getBiddingLogicUrl();
        mBuyerDecisionLogicJs = buyerDecisionLogicJs;
        mCustomAudienceSignals =
                new CustomAudienceSignals(
                        customAudience.getOwner(),
                        customAudience.getBuyer(),
                        customAudience.getName(),
                        customAudience.getActivationTime(),
                        customAudience.getExpirationTime(),
                        customAudience.getUserBiddingSignals());
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof CustomAudienceBiddingInfo) {
            CustomAudienceBiddingInfo customAudienceBiddingInfo = (CustomAudienceBiddingInfo) o;
            return mBiddingLogicUrl.equals(customAudienceBiddingInfo.mBiddingLogicUrl)
                    && mBuyerDecisionLogicJs.equals(customAudienceBiddingInfo.mBuyerDecisionLogicJs)
                    && mCustomAudienceSignals.equals(
                            customAudienceBiddingInfo.mCustomAudienceSignals);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mBiddingLogicUrl, mBuyerDecisionLogicJs, mCustomAudienceSignals);
    }

    /** Builder for {@link CustomAudienceBiddingInfo} objects. */
    public static final class Builder {
        @NonNull private Uri mBiddingLogicUrl;
        @NonNull private String mBuyerDecisionLogicJs;
        @NonNull private CustomAudienceSignals mCustomAudienceSignals;

        /** Sets the bidding_logic_url. */
        public CustomAudienceBiddingInfo.Builder setBiddingLogicUrl(@NonNull Uri biddingLogicUrl) {
            this.mBiddingLogicUrl = biddingLogicUrl;
            return this;
        }

        /** Sets the buyer_decision_logic_js. */
        public CustomAudienceBiddingInfo.Builder setBuyerDecisionLogicJs(
                @NonNull String buyerDecisionLogicJs) {
            this.mBuyerDecisionLogicJs = buyerDecisionLogicJs;
            return this;
        }

        /** Sets the custom_audience_signals. */
        public CustomAudienceBiddingInfo.Builder setCustomAudienceSignals(
                @NonNull CustomAudienceSignals customAudienceSignals) {
            this.mCustomAudienceSignals = customAudienceSignals;
            return this;
        }

        /**
         * Build a {@link CustomAudienceBiddingInfo} instance.
         *
         * @throws NullPointerException if any NonNull field is unset.
         */
        @NonNull
        public CustomAudienceBiddingInfo build() {
            Objects.requireNonNull(mBiddingLogicUrl);
            Objects.requireNonNull(mBuyerDecisionLogicJs);
            Objects.requireNonNull(mCustomAudienceSignals);

            return new CustomAudienceBiddingInfo(
                    mBiddingLogicUrl, mBuyerDecisionLogicJs, mCustomAudienceSignals);
        }
    }
}
