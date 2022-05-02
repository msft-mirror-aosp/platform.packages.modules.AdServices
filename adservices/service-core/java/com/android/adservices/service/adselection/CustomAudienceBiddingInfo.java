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

import com.google.auto.value.AutoValue;

import java.util.Objects;

/**
 * Information related to Ad bidding that is used for reporting
 */
@AutoValue
public abstract class CustomAudienceBiddingInfo {

    /**
     * @return Uri that is used for getting Bidding logic
     */
    public abstract Uri getBiddingLogicUrl();

    /**
     * @return logic for buyer decision used in bidding
     */
    public abstract String getBuyerDecisionLogicJs();

    /**
     * @return CA data used for bidding and later reporting
     */
    public abstract CustomAudienceSignals getCustomAudienceSignals();

    /**
     * @return generic builder
     */
    static Builder builder() {
        return new AutoValue_CustomAudienceBiddingInfo.Builder();
    }

    /**
     * Creates an object of CustomAudienceBiddingInfo
     * @param biddingLogicUrl url that fetches bidding logic
     * @param buyerDecisionLogicJs JS that helps in making bidding decision
     * @param customAudienceSignals signals for CA
     * @return an instance of CustomAudienceBiddingInfo
     */
    public static CustomAudienceBiddingInfo create(
            @NonNull Uri biddingLogicUrl,
            @NonNull String buyerDecisionLogicJs,
            @NonNull CustomAudienceSignals customAudienceSignals) {
        Objects.requireNonNull(biddingLogicUrl);
        Objects.requireNonNull(buyerDecisionLogicJs);
        Objects.requireNonNull(customAudienceSignals);

        return builder().setBiddingLogicUrl(biddingLogicUrl)
                .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                .setCustomAudienceSignals(customAudienceSignals)
                .build();
    }

    /**
     * Creates an object of CustomAudienceBiddingInfo
     * @param customAudience CA data
     * @param buyerDecisionLogicJs JS that helps in making bidding decision
     * @return an instance of CustomAudienceBiddingInfo
     */
    public static CustomAudienceBiddingInfo create(
            @NonNull DBCustomAudience customAudience, @NonNull String buyerDecisionLogicJs) {
        Objects.requireNonNull(customAudience);
        Objects.requireNonNull(buyerDecisionLogicJs);

        Uri biddingLogicUrl = customAudience.getBiddingLogicUrl();
        CustomAudienceSignals customAudienceSignals =
                new CustomAudienceSignals(
                        customAudience.getOwner(),
                        customAudience.getBuyer(),
                        customAudience.getName(),
                        customAudience.getActivationTime(),
                        customAudience.getExpirationTime(),
                        customAudience.getUserBiddingSignals());

        return builder().setBiddingLogicUrl(biddingLogicUrl)
                .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                .setCustomAudienceSignals(customAudienceSignals)
                .build();
    }

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setBiddingLogicUrl(Uri biddingUri);
        abstract Builder setBuyerDecisionLogicJs(String buyerDecisionLogicJs);
        abstract Builder setCustomAudienceSignals(CustomAudienceSignals customAudienceSignals);
        abstract CustomAudienceBiddingInfo build();
    }
}
