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

import com.android.internal.util.Preconditions;

import java.util.Objects;

/**
 * Represent input parameters to the reportImpression API.
 *
 * <p>Hiding for future implementation and review for public exposure.
 *
 * @hide
 */
public class ReportImpressionInput {
    private static final long UNSET = 0;

    private final long mAdSelectionId;
    @NonNull private final AdSelectionConfig mAdSelectionConfig;

    private ReportImpressionInput(
            long adSelectionId, @NonNull AdSelectionConfig adSelectionConfig) {
        Objects.requireNonNull(adSelectionConfig);

        this.mAdSelectionId = adSelectionId;
        this.mAdSelectionConfig = adSelectionConfig;
    }

    /** Returns the adSelectionId, one of the inputs to {@link ReportImpressionInput} */
    public long getAdSelectionId() {
        return mAdSelectionId;
    }

    /** Returns the adSelectionConfig, one of the inputs to {@link ReportImpressionInput} */
    @NonNull
    public AdSelectionConfig getAdSelectionConfig() {
        return mAdSelectionConfig;
    }

    /** Builder for {@link ReportImpressionInput} objects. */
    public static final class Builder {
        // Initializing mAdSelectionId to start as -1, to differentiate it from the default
        // initialization of 0.
        private long mAdSelectionId = UNSET;
        private AdSelectionConfig mAdSelectionConfig;

        public Builder() {}

        /** Set the mAdSelectionId. */
        @NonNull
        public ReportImpressionInput.Builder setAdSelectionId(long adSelectionId) {
            this.mAdSelectionId = adSelectionId;
            return this;
        }

        /** Set the AdSelectionConfig. */
        @NonNull
        public ReportImpressionInput.Builder setAdSelectionConfig(
                @NonNull AdSelectionConfig adSelectionConfig) {
            Objects.requireNonNull(adSelectionConfig);

            this.mAdSelectionConfig = adSelectionConfig;
            return this;
        }

        /** Builds a {@link ReportImpressionInput} instance. */
        @NonNull
        public ReportImpressionInput build() {
            Objects.requireNonNull(mAdSelectionConfig);

            Preconditions.checkArgument(mAdSelectionId != UNSET, "AdSelectionId not set");

            return new ReportImpressionInput(mAdSelectionId, mAdSelectionConfig);
        }
    }
}
