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

/**
 * Represent input params to the reportImpression API.
 *
 * <p>Hiding for future implementation and review for public exposure.
 *
 * @hide
 */
public final class ReportImpressionRequest implements Parcelable {
    @NonNull public static final int UNSET = -1;

    private final int mAdSelectionId;
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
            int adSelectionId, @NonNull AdSelectionConfig adSelectionConfig) {
        this.mAdSelectionId = adSelectionId;
        this.mAdSelectionConfig = adSelectionConfig;
    }

    private ReportImpressionRequest(@NonNull Parcel in) {
        this.mAdSelectionId = in.readInt();
        this.mAdSelectionConfig = AdSelectionConfig.CREATOR.createFromParcel(in);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mAdSelectionId);
        mAdSelectionConfig.writeToParcel(dest, flags);
    }

    /**
     * Returns the adSelectionId, one of the inputs to {@link ReportImpressionRequest} as noted in
     * {@link AdSelectionService}.
     */
    public int getAdSelectionId() {
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
        private int mAdSelectionId = UNSET;
        private AdSelectionConfig mAdSelectionConfig;

        public Builder() {}

        /** Set the mAdSelectionId. */
        @NonNull
        public ReportImpressionRequest.Builder setAdSelectionId(@NonNull int adSelectionId) {
            this.mAdSelectionId = adSelectionId;
            return this;
        }

        /** Set the AdSelectionConfig. */
        @NonNull
        public ReportImpressionRequest.Builder setAdSelectionConfig(
                @NonNull AdSelectionConfig adSelectionConfig) {
            this.mAdSelectionConfig = adSelectionConfig;
            return this;
        }

        /** Builds a {@link ReportImpressionRequest} instance. */
        @NonNull
        public ReportImpressionRequest build() {
            Preconditions.checkArgument(mAdSelectionConfig != null, "AdSelectionConfig not set");

            Preconditions.checkArgument(mAdSelectionId != UNSET, "AdSelectionId not set");

            return new ReportImpressionRequest(mAdSelectionId, mAdSelectionConfig);
        }
    }
}
