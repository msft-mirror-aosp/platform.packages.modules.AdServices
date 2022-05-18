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
import android.annotation.Nullable;

import java.util.Objects;

/**
 * The request object used to leave a custom audience.
 */
public final class LeaveCustomAudienceRequest {

    @Nullable
    private final String mOwner;
    @NonNull
    private final String mBuyer;
    @NonNull
    private final String mName;

    private LeaveCustomAudienceRequest(@NonNull LeaveCustomAudienceRequest.Builder builder) {
        mOwner = builder.mOwner;
        mBuyer = builder.mBuyer;
        mName = builder.mName;
    }

    /**
     * Returns a String representing the custom audience's owner application or null to be the
     * calling application.
     */
    @Nullable
    public String getOwner() {
        return mOwner;
    }

    /**
     * A buyer is identified by a domain in the form "buyerexample.com".
     *
     * @return a String containing the custom audience's buyer's domain
     */
    @NonNull
    public String getBuyer() {
        return mBuyer;
    }

    /**
     * This name of a custom audience is an opaque string provided by the owner and buyer on
     * creation of the {@link CustomAudience} object.
     *
     * @return the String name of the custom audience
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Checks whether two {@link LeaveCustomAudienceRequest} objects contain the same information.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LeaveCustomAudienceRequest)) return false;
        LeaveCustomAudienceRequest that = (LeaveCustomAudienceRequest) o;
        return Objects.equals(mOwner, that.mOwner) && mBuyer.equals(that.mBuyer)
                && mName.equals(that.mName);
    }

    /**
     * Returns the hash of the {@link LeaveCustomAudienceRequest} object's data.
     */
    @Override
    public int hashCode() {
        return Objects.hash(mOwner, mBuyer, mName);
    }

    /** Builder for {@link LeaveCustomAudienceRequest} objects. */
    public static final class Builder {
        @Nullable
        private String mOwner;
        @NonNull
        private String mBuyer;
        @NonNull
        private String mName;

        public Builder() {
        }

        /**
         * Sets the owner application.
         * <p>
         * See {@link #getOwner()} for more information.
         *
         * @param owner application name or leave null to default to the calling app.
         */
        @NonNull
        public LeaveCustomAudienceRequest.Builder setOwner(@Nullable String owner) {
            mOwner = owner;
            return this;
        }

        /**
         * Sets the buyer domain URL.
         * <p>
         * See {@link #getBuyer()} for more information.
         */
        @NonNull
        public LeaveCustomAudienceRequest.Builder setBuyer(@NonNull String buyer) {
            Objects.requireNonNull(buyer);
            mBuyer = buyer;
            return this;
        }

        /**
         * Sets the {@link CustomAudience} object's name.
         * <p>
         * See {@link #getName()} for more information.
         */
        @NonNull
        public LeaveCustomAudienceRequest.Builder setName(@NonNull String name) {
            Objects.requireNonNull(name);
            mName = name;
            return this;
        }

        /**
         * Builds an instance of a {@link LeaveCustomAudienceRequest}.
         *
         * @throws NullPointerException if any non-null parameter is null
         */
        @NonNull
        public LeaveCustomAudienceRequest build() {
            Objects.requireNonNull(mBuyer);
            Objects.requireNonNull(mName);

            return new LeaveCustomAudienceRequest(this);
        }
    }
}
