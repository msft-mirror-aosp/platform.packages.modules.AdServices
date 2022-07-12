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

import com.google.auto.value.AutoValue;

/**
 * Represents outcome of the scoring, with Ads their score and their Custom Audience information
 * selection process.
 *
 * The ads and their scores are used to decide the winner for Ad Selection. The Custom audience
 * information is used on reporting
 *
 */
@AutoValue
public abstract class AdScoringOutcome {
    /**
     * @return Ad with score based on seller scoring logic
     */
    public abstract AdWithScore getAdWithScore();

    /**
     * @return CA bidding info chained from bidding and used for reporting
     */
    public abstract CustomAudienceBiddingInfo getCustomAudienceBiddingInfo();

    /**
     * @return generic builder
     */
    static Builder builder() {
        return new AutoValue_AdScoringOutcome.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setAdWithScore(AdWithScore adWithScore);
        abstract Builder setCustomAudienceBiddingInfo(
                CustomAudienceBiddingInfo customAudienceBiddingInfo);
        abstract AdScoringOutcome build();
    }
}
