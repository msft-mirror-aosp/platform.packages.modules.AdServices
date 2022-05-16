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
 * This POJO represents the removeCustomAudienceRemoteInfoOverride request
 *
 * @hide
 */
public class RemoveCustomAudienceOverrideRequest {
    @NonNull private final String mOwner;
    @NonNull private final String mBuyer;
    @NonNull private final String mName;

    public RemoveCustomAudienceOverrideRequest(String owner, String buyer, String name) {
        mOwner = owner;
        mBuyer = buyer;
        mName = name;
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

    /** Builder for {@link RemoveCustomAudienceOverrideRequest} objects. */
    public static final class Builder {
        private String mOwner;
        private String mBuyer;
        private String mName;

        public Builder() {}

        /** Set the Owner. */
        @NonNull
        public RemoveCustomAudienceOverrideRequest.Builder setOwner(@NonNull String owner) {
            Objects.requireNonNull(owner);

            this.mOwner = owner;
            return this;
        }

        /** Set the Buyer. */
        @NonNull
        public RemoveCustomAudienceOverrideRequest.Builder setBuyer(@NonNull String buyer) {
            Objects.requireNonNull(buyer);

            this.mBuyer = buyer;
            return this;
        }

        /** Set the Name. */
        @NonNull
        public RemoveCustomAudienceOverrideRequest.Builder setName(@NonNull String name) {
            Objects.requireNonNull(name);

            this.mName = name;
            return this;
        }

        /** Builds a {@link RemoveCustomAudienceOverrideRequest} instance. */
        @NonNull
        public RemoveCustomAudienceOverrideRequest build() {
            Objects.requireNonNull(mOwner);
            Objects.requireNonNull(mBuyer);
            Objects.requireNonNull(mName);

            return new RemoveCustomAudienceOverrideRequest(mOwner, mBuyer, mName);
        }
    }
}
