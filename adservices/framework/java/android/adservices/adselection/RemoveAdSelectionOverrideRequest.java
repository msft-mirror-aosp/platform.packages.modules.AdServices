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

package android.adservices.adselection;

import android.annotation.NonNull;
import android.os.OutcomeReceiver;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * This POJO represents the {@link
 * AdSelectionManager#removeAdSelectionConfigRemoteInfoOverride(RemoveAdSelectionOverrideRequest,
 * Executor, OutcomeReceiver)} request
 *
 * <p>It contains one field, a {@link AdSelectionConfig} which serves as the identifier of the
 * override to be removed
 */
public class RemoveAdSelectionOverrideRequest {
    @NonNull private final AdSelectionConfig mAdSelectionConfig;

    private RemoveAdSelectionOverrideRequest(AdSelectionConfig adSelectionConfig) {
        mAdSelectionConfig = adSelectionConfig;
    }

    /**
     * @return AdSelectionConfig, the configuration of the ad selection process.
     */
    @NonNull
    public AdSelectionConfig getAdSelectionConfig() {
        return mAdSelectionConfig;
    }

    /** Builder for {@link RemoveAdSelectionOverrideRequest} objects. */
    public static final class Builder {
        private AdSelectionConfig mAdSelectionConfig;

        public Builder() {}

        /** Set the AdSelectionConfig. */
        @NonNull
        public RemoveAdSelectionOverrideRequest.Builder setAdSelectionConfig(
                @NonNull AdSelectionConfig adSelectionConfig) {
            Objects.requireNonNull(adSelectionConfig);

            this.mAdSelectionConfig = adSelectionConfig;
            return this;
        }

        /** Builds a {@link RemoveAdSelectionOverrideRequest} instance. */
        @NonNull
        public RemoveAdSelectionOverrideRequest build() {
            Objects.requireNonNull(mAdSelectionConfig);

            return new RemoveAdSelectionOverrideRequest(mAdSelectionConfig);
        }
    }
}
