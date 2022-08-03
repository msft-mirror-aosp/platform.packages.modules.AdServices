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

package android.adservices.customaudience;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.os.OutcomeReceiver;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * This POJO represents the {@link
 * CustomAudienceManager#overrideCustomAudienceRemoteInfo(AddCustomAudienceOverrideRequest,
 * Executor, OutcomeReceiver)} request
 *
 * <p>It contains 3 fields {@code owner}, {@code buyer}, and {@code name} which will serve as the
 * identifier for the specific two override fields, {@code biddingLogicJs} and {@code
 * trustedBiddingData}
 */
public class AddCustomAudienceOverrideRequest {
    @NonNull private final String mOwner;
    @NonNull private final AdTechIdentifier mBuyer;
    @NonNull private final String mName;
    @NonNull private final String mBiddingLogicJs;
    @NonNull private final AdSelectionSignals mTrustedBiddingSignals;

    public AddCustomAudienceOverrideRequest(
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull String biddingLogicJs,
            @NonNull AdSelectionSignals trustedBiddingSignals) {
        mOwner = owner;
        mBuyer = buyer;
        mName = name;
        mBiddingLogicJs = biddingLogicJs;
        mTrustedBiddingSignals = trustedBiddingSignals;
    }

    /**
     * @return the owner
     */
    @NonNull
    public String getOwner() {
        return mOwner;
    }

    /** @return the buyer */
    @NonNull
    public AdTechIdentifier getBuyer() {
        return mBuyer;
    }

    /**
     * @return name
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /** @return The override javascript result */
    @NonNull
    public String getBiddingLogicJs() {
        return mBiddingLogicJs;
    }

    /** @return The override trusted bidding signals result */
    @NonNull
    public AdSelectionSignals getTrustedBiddingSignals() {
        return mTrustedBiddingSignals;
    }

    /** Builder for {@link AddCustomAudienceOverrideRequest} objects. */
    public static final class Builder {
        private String mOwner;
        private AdTechIdentifier mBuyer;
        private String mName;
        private String mBiddingLogicJs;
        private AdSelectionSignals mTrustedBiddingSignals;

        public Builder() {}

        /** Set the Owner. */
        @NonNull
        public AddCustomAudienceOverrideRequest.Builder setOwner(@NonNull String owner) {
            Objects.requireNonNull(owner);

            this.mOwner = owner;
            return this;
        }

        /** Set the Buyer. */
        @NonNull
        public AddCustomAudienceOverrideRequest.Builder setBuyer(@NonNull AdTechIdentifier buyer) {
            Objects.requireNonNull(buyer);

            this.mBuyer = buyer;
            return this;
        }

        /** Set the Name. */
        @NonNull
        public AddCustomAudienceOverrideRequest.Builder setName(@NonNull String name) {
            Objects.requireNonNull(name);

            this.mName = name;
            return this;
        }

        /** Set the TrustedBiddingSignals. */
        @NonNull
        public AddCustomAudienceOverrideRequest.Builder setTrustedBiddingSignals(
                @NonNull AdSelectionSignals trustedBiddingSignals) {
            Objects.requireNonNull(trustedBiddingSignals);

            this.mTrustedBiddingSignals = trustedBiddingSignals;
            return this;
        }

        /** Set the BiddingLogicJs. */
        @NonNull
        public AddCustomAudienceOverrideRequest.Builder setBiddingLogicJs(
                @NonNull String biddingLogicJs) {
            Objects.requireNonNull(biddingLogicJs);

            this.mBiddingLogicJs = biddingLogicJs;
            return this;
        }

        /** Builds a {@link AddCustomAudienceOverrideRequest} instance. */
        @NonNull
        public AddCustomAudienceOverrideRequest build() {
            Objects.requireNonNull(mOwner);
            Objects.requireNonNull(mBuyer);
            Objects.requireNonNull(mName);
            Objects.requireNonNull(mBiddingLogicJs);
            Objects.requireNonNull(mTrustedBiddingSignals);

            return new AddCustomAudienceOverrideRequest(
                    mOwner, mBuyer, mName, mBiddingLogicJs, mTrustedBiddingSignals);
        }
    }
}
