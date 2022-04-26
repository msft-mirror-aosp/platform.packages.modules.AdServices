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

import android.adservices.adselection.AdWithBid;
import android.annotation.NonNull;

import java.util.Objects;

/** This represents the output object of the filtering and bidding stage. */
/** TODO(b/230554731): Build AdBiddingOutcome abstract class with AutoValue. */
public class AdBiddingOutcome {
    @NonNull private final AdWithBid mAdWithBid;
    @NonNull private final CustomAudienceBiddingInfo mCustomAudienceBiddingInfo;

    public AdBiddingOutcome(
            @NonNull AdWithBid adWithBid,
            @NonNull CustomAudienceBiddingInfo customAudienceBiddingInfo) {
        mAdWithBid = adWithBid;
        mCustomAudienceBiddingInfo = customAudienceBiddingInfo;
    }

    /** Get the {@link AdWithBid} from the {@link AdBiddingOutcome}. */
    public AdWithBid getAdWithBid() {
        return mAdWithBid;
    }

    /** Get the {@link CustomAudienceBiddingInfo} from the {@link AdBiddingOutcome}. */
    public CustomAudienceBiddingInfo getCustomAudienceBiddingInfo() {
        return mCustomAudienceBiddingInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdBiddingOutcome)) return false;
        AdBiddingOutcome that = (AdBiddingOutcome) o;
        return Objects.equals(mAdWithBid, that.mAdWithBid)
                && Objects.equals(mCustomAudienceBiddingInfo, that.mCustomAudienceBiddingInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAdWithBid, mCustomAudienceBiddingInfo);
    }

    /** Builder for {@link AdBiddingOutcome} objects. */
    public static final class Builder {
        @NonNull private AdWithBid mAdWithBid;
        @NonNull private CustomAudienceBiddingInfo mCustomAudienceBiddingInfo;

        /** Sets the AdWithBid for the AdBiddingOutcome object. */
        public AdBiddingOutcome.Builder setAdWithBid(@NonNull AdWithBid adWithBid) {
            this.mAdWithBid = adWithBid;
            return this;
        }

        /** Sets the CustomAudienceBiddingInfo for the AdBiddingOutcome object. */
        public AdBiddingOutcome.Builder setCustomAudienceBiddingInfo(
                @NonNull CustomAudienceBiddingInfo customAudienceBiddingInfo) {
            this.mCustomAudienceBiddingInfo = customAudienceBiddingInfo;
            return this;
        }

        /**
         * Build a {@link AdBiddingOutcome} instance.
         *
         * @throws NullPointerException if any NonNull field is unset.
         */
        @NonNull
        public AdBiddingOutcome build() {
            Objects.requireNonNull(mAdWithBid);
            Objects.requireNonNull(mCustomAudienceBiddingInfo);

            return new AdBiddingOutcome(mAdWithBid, mCustomAudienceBiddingInfo);
        }
    }
}
