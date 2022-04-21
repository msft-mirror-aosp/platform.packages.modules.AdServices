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
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.util.Preconditions;

import java.util.Objects;

/**
 * Represent input params to the reportImpression API.
 *
 * <p>Hiding for future implementation and review for public exposure.
 *
 * @hide
 */
public final class ReportImpressionRequest implements Parcelable {
    private static final long UNSET = 0;

    private final long mAdSelectionId;
    @NonNull private final AdSelectionConfig mAdSelectionConfig;

    @NonNull
    public static final Parcelable.Creator<ReportImpressionRequest> CREATOR =
            new Parcelable.Creator<ReportImpressionRequest>() {
                public ReportImpressionRequest createFromParcel(Parcel in) {
                    return new ReportImpressionRequest(in);
                }

                public ReportImpressionRequest[] newArray(int size) {
                    return new ReportImpressionRequest[size];
                }
            };

    public ReportImpressionRequest(
            long adSelectionId, @NonNull AdSelectionConfig adSelectionConfig) {
        Objects.requireNonNull(adSelectionConfig);

        this.mAdSelectionId = adSelectionId;
        this.mAdSelectionConfig = adSelectionConfig;
    }

    private ReportImpressionRequest(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        this.mAdSelectionId = in.readLong();
        this.mAdSelectionConfig = AdSelectionConfig.CREATOR.createFromParcel(in);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);

        dest.writeLong(mAdSelectionId);
        mAdSelectionConfig.writeToParcel(dest, flags);
    }

    /**
     * Returns the adSelectionId, one of the inputs to {@link ReportImpressionRequest} as noted in
     * {@link AdSelectionService}.
     */
    public long getAdSelectionId() {
        return mAdSelectionId;
    }

    /**
     * Returns the adSelectionConfig, one of the inputs to {@link ReportImpressionRequest} as noted
     * in {@link AdSelectionService}.
     */
    @NonNull
    public AdSelectionConfig getAdSelectionConfig() {
        return mAdSelectionConfig;
    }

    /** Builder for {@link ReportImpressionRequest} objects. */
    public static final class Builder {
        // Initializing mAdSelectionId to start as -1, to differentiate it from the default
        // initialization of 0.
        private long mAdSelectionId = UNSET;
        private AdSelectionConfig mAdSelectionConfig;

        public Builder() {}

        /** Set the mAdSelectionId. */
        @NonNull
        public ReportImpressionRequest.Builder setAdSelectionId(long adSelectionId) {
            this.mAdSelectionId = adSelectionId;
            return this;
        }

        /** Set the AdSelectionConfig. */
        @NonNull
        public ReportImpressionRequest.Builder setAdSelectionConfig(
                @NonNull AdSelectionConfig adSelectionConfig) {
            Objects.requireNonNull(adSelectionConfig);

            this.mAdSelectionConfig = adSelectionConfig;
            return this;
        }

        /** Builds a {@link ReportImpressionRequest} instance. */
        @NonNull
        public ReportImpressionRequest build() {
            Objects.requireNonNull(mAdSelectionConfig);

            Preconditions.checkArgument(mAdSelectionId != UNSET, "AdSelectionId not set");

            return new ReportImpressionRequest(mAdSelectionId, mAdSelectionConfig);
        }
    }
}
