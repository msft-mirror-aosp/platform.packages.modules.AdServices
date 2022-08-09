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
 * TestCustomAudienceManager#overrideCustomAudienceRemoteInfo(AddCustomAudienceOverrideRequest,
 * Executor, OutcomeReceiver)} request
 *
 * <p>It contains 3 fields {@code ownerPackageName}, {@code buyer}, and {@code name} which will
 * serve as the identifier for the two specific override fields, {@code biddingLogicJs} and {@code
 * trustedBiddingSignals}
 */
public class AddCustomAudienceOverrideRequest {
    @NonNull private final String mOwnerPackageName;
    @NonNull private final AdTechIdentifier mBuyer;
    @NonNull private final String mName;
    @NonNull private final String mBiddingLogicJs;
    @NonNull private final AdSelectionSignals mTrustedBiddingSignals;

    public AddCustomAudienceOverrideRequest(
            @NonNull String ownerPackageName,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull String biddingLogicJs,
            @NonNull AdSelectionSignals trustedBiddingSignals) {
        mOwnerPackageName = ownerPackageName;
        mBuyer = buyer;
        mName = name;
        mBiddingLogicJs = biddingLogicJs;
        mTrustedBiddingSignals = trustedBiddingSignals;
    }

    /** @return the package name for the owner application */
    @NonNull
    public String getOwnerPackageName() {
        return mOwnerPackageName;
    }

    /** @return an {@link AdTechIdentifier} representing the buyer */
    @NonNull
    public AdTechIdentifier getBuyer() {
        return mBuyer;
    }

    /** @return name of the custom audience being overridden */
    @NonNull
    public String getName() {
        return mName;
    }

    /** @return the override JavaScript result that should be served during ad selection */
    @NonNull
    public String getBiddingLogicJs() {
        return mBiddingLogicJs;
    }

    /** @return the override trusted bidding signals that should be served during ad selection */
    @NonNull
    public AdSelectionSignals getTrustedBiddingSignals() {
        return mTrustedBiddingSignals;
    }

    /** Builder for {@link AddCustomAudienceOverrideRequest} objects. */
    public static final class Builder {
        private String mOwnerPackageName;
        private AdTechIdentifier mBuyer;
        private String mName;
        private String mBiddingLogicJs;
        private AdSelectionSignals mTrustedBiddingSignals;

        public Builder() {}

        /** Sets the owner application's package name. */
        @NonNull
        public AddCustomAudienceOverrideRequest.Builder setOwnerPackageName(
                @NonNull String ownerPackageName) {
            Objects.requireNonNull(ownerPackageName);

            this.mOwnerPackageName = ownerPackageName;
            return this;
        }

        /** Sets the buyer {@link AdTechIdentifier} for the custom audience. */
        @NonNull
        public AddCustomAudienceOverrideRequest.Builder setBuyer(@NonNull AdTechIdentifier buyer) {
            Objects.requireNonNull(buyer);

            this.mBuyer = buyer;
            return this;
        }

        /** Sets the name for the custom audience to be overridden. */
        @NonNull
        public AddCustomAudienceOverrideRequest.Builder setName(@NonNull String name) {
            Objects.requireNonNull(name);

            this.mName = name;
            return this;
        }

        /** Sets the trusted bidding signals to be served during ad selection. */
        @NonNull
        public AddCustomAudienceOverrideRequest.Builder setTrustedBiddingSignals(
                @NonNull AdSelectionSignals trustedBiddingSignals) {
            Objects.requireNonNull(trustedBiddingSignals);

            this.mTrustedBiddingSignals = trustedBiddingSignals;
            return this;
        }

        /** Sets the bidding logic JavaScript that should be served during ad selection. */
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
            Objects.requireNonNull(mOwnerPackageName);
            Objects.requireNonNull(mBuyer);
            Objects.requireNonNull(mName);
            Objects.requireNonNull(mBiddingLogicJs);
            Objects.requireNonNull(mTrustedBiddingSignals);

            return new AddCustomAudienceOverrideRequest(
                    mOwnerPackageName, mBuyer, mName, mBiddingLogicJs, mTrustedBiddingSignals);
        }
    }
}
