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

import android.annotation.NonNull;

import java.util.Objects;

/**
 * This POJO represents the overrideCustomAudienceRemoteInfo request
 *
 * @hide
 */
public class AddCustomAudienceOverrideRequest {
    @NonNull private final String mOwner;
    @NonNull private final String mBuyer;
    @NonNull private final String mName;
    @NonNull private final String mBiddingLogicJs;
    @NonNull private final String mTrustedBiddingData;

    public AddCustomAudienceOverrideRequest(
            String owner,
            String buyer,
            String name,
            String biddingLogicJs,
            String trustedBiddingData) {
        mOwner = owner;
        mBuyer = buyer;
        mName = name;
        mBiddingLogicJs = biddingLogicJs;
        mTrustedBiddingData = trustedBiddingData;
    }

    /**
     * @return the owner
     */
    @NonNull
    public String getOwner() {
        return mOwner;
    }

    /**
     * @return the buyer
     */
    @NonNull
    public String getBuyer() {
        return mBuyer;
    }

    /**
     * @return name
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * @return The override javascript result
     */
    @NonNull
    public String getBiddingLogicJS() {
        return mBiddingLogicJs;
    }

    /**
     * @return The override trusted bidding data result
     */
    @NonNull
    public String getTrustedBiddingData() {
        return mTrustedBiddingData;
    }

    /** Builder for {@link AddCustomAudienceOverrideRequest} objects. */
    public static final class Builder {
        private String mOwner;
        private String mBuyer;
        private String mName;
        private String mBiddingLogicJs;
        private String mTrustedBiddingData;

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
        public AddCustomAudienceOverrideRequest.Builder setBuyer(@NonNull String buyer) {
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

        /** Set the TrustedBiddingData. */
        @NonNull
        public AddCustomAudienceOverrideRequest.Builder setTrustedBiddingData(
                @NonNull String trustedBiddingData) {
            Objects.requireNonNull(trustedBiddingData);

            this.mTrustedBiddingData = trustedBiddingData;
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
            Objects.requireNonNull(mTrustedBiddingData);

            return new AddCustomAudienceOverrideRequest(
                    mOwner, mBuyer, mName, mBiddingLogicJs, mTrustedBiddingData);
        }
    }
}
