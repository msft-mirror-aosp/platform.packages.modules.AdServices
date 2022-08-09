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

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.os.OutcomeReceiver;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * This POJO represents the {@link TestCustomAudienceManager#removeCustomAudienceRemoteInfoOverride(
 * RemoveCustomAudienceOverrideRequest, Executor, OutcomeReceiver)} request
 *
 * <p>It contains 3 fields {@code ownerPackageName}, {@code buyer}, and {@code name} which will
 * serve as the identifier for the overrides
 */
public class RemoveCustomAudienceOverrideRequest {
    @NonNull private final String mOwnerPackageName;
    @NonNull private final AdTechIdentifier mBuyer;
    @NonNull private final String mName;

    public RemoveCustomAudienceOverrideRequest(
            @NonNull String ownerPackageName,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name) {
        mOwnerPackageName = ownerPackageName;
        mBuyer = buyer;
        mName = name;
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

    /** Builder for {@link RemoveCustomAudienceOverrideRequest} objects. */
    public static final class Builder {
        private String mOwnerPackageName;
        private AdTechIdentifier mBuyer;
        private String mName;

        public Builder() {}

        /** Sets the owner application's package name. */
        @NonNull
        public RemoveCustomAudienceOverrideRequest.Builder setOwnerPackageName(
                @NonNull String ownerPackageName) {
            Objects.requireNonNull(ownerPackageName);

            this.mOwnerPackageName = ownerPackageName;
            return this;
        }

        /** Sets the buyer {@link AdTechIdentifier} for the custom audience. */
        @NonNull
        public RemoveCustomAudienceOverrideRequest.Builder setBuyer(
                @NonNull AdTechIdentifier buyer) {
            Objects.requireNonNull(buyer);

            this.mBuyer = buyer;
            return this;
        }

        /** Sets the name for the custom audience that was overridden. */
        @NonNull
        public RemoveCustomAudienceOverrideRequest.Builder setName(@NonNull String name) {
            Objects.requireNonNull(name);

            this.mName = name;
            return this;
        }

        /** Builds a {@link RemoveCustomAudienceOverrideRequest} instance. */
        @NonNull
        public RemoveCustomAudienceOverrideRequest build() {
            Objects.requireNonNull(mOwnerPackageName);
            Objects.requireNonNull(mBuyer);
            Objects.requireNonNull(mName);

            return new RemoveCustomAudienceOverrideRequest(mOwnerPackageName, mBuyer, mName);
        }
    }
}
