/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.adservices.adselection.AdSelectionOutcome.UNSET_AD_SELECTION_ID;
import static android.adservices.adselection.AdSelectionOutcome.UNSET_AD_SELECTION_ID_MESSAGE;

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.util.Preconditions;

import java.util.Objects;

/**
 * Represents a request containing the seller, the ad selection id and data.
 *
 * <p>Instances of this class are created by SDKs to be provided as arguments to the {@link
 * AdSelectionManager#processAdSelectionResult} methods in {@link AdSelectionManager}.
 *
 * @hide
 */
public final class ProcessAdSelectionResultRequest implements Parcelable {
    private final long mAdSelectionId;
    @Nullable private final AdTechIdentifier mSeller;
    @Nullable private final String mAdSelectionResult;

    @NonNull
    public static final Parcelable.Creator<ProcessAdSelectionResultRequest> CREATOR =
            new Parcelable.Creator<>() {
                @Override
                public ProcessAdSelectionResultRequest createFromParcel(@NonNull Parcel in) {
                    Objects.requireNonNull(in);

                    return new ProcessAdSelectionResultRequest(in);
                }

                @Override
                public ProcessAdSelectionResultRequest[] newArray(int size) {
                    return new ProcessAdSelectionResultRequest[size];
                }
            };

    public ProcessAdSelectionResultRequest(
            long adSelectionId, AdTechIdentifier seller, String adSelectionResult) {
        this.mAdSelectionId = adSelectionId;
        this.mSeller = seller;
        this.mAdSelectionResult = adSelectionResult;
    }

    private ProcessAdSelectionResultRequest(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        this.mAdSelectionId = in.readLong();
        this.mSeller = AdTechIdentifier.CREATOR.createFromParcel(in);
        this.mAdSelectionResult = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);

        dest.writeLong(mAdSelectionId);
        mSeller.writeToParcel(dest, flags);
        dest.writeString(mAdSelectionResult);
    }

    /**
     * @return an ad selection id.
     */
    public long getAdSelectionId() {
        return mAdSelectionId;
    }

    /**
     * @return a seller.
     */
    @Nullable
    public AdTechIdentifier getSeller() {
        return mSeller;
    }

    /**
     * @return an ad selection result.
     */
    @Nullable
    public String getAdSelectionResult() {
        return mAdSelectionResult;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProcessAdSelectionResultRequest)) return false;
        ProcessAdSelectionResultRequest that = (ProcessAdSelectionResultRequest) o;
        return mAdSelectionId == that.mAdSelectionId
                && Objects.equals(mSeller, that.mSeller)
                && Objects.equals(mAdSelectionResult, that.mAdSelectionResult);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAdSelectionId, mSeller, mAdSelectionResult);
    }

    /**
     * Builder for {@link ProcessAdSelectionResultRequest} objects.
     *
     * @hide
     */
    public static final class Builder {
        private long mAdSelectionId;
        @Nullable private AdTechIdentifier mSeller;
        @Nullable private String mAdSelectionResult;

        public Builder() {}

        /** Sets the ad selection id {@link Long}. */
        public ProcessAdSelectionResultRequest.Builder setAdSelectionId(long adSelectionId) {
            this.mAdSelectionId = adSelectionId;
            return this;
        }

        /** Sets the seller {@link AdTechIdentifier}. */
        public ProcessAdSelectionResultRequest.Builder setSeller(
                @Nullable AdTechIdentifier seller) {
            this.mSeller = seller;
            return this;
        }

        /** Sets the ad selection result {@link String}. */
        public ProcessAdSelectionResultRequest.Builder setAdSelectionResult(
                @Nullable String adSelectionResult) {
            this.mAdSelectionResult = adSelectionResult;
            return this;
        }

        /**
         * Builds a {@link ProcessAdSelectionResultRequest} instance.
         *
         * @throws IllegalArgumentException if the adSelectionIid is not set
         * @throws NullPointerException if the mAdSelectionResult or Seller is null
         */
        @NonNull
        public ProcessAdSelectionResultRequest build() {
            Preconditions.checkArgument(
                    mAdSelectionId != UNSET_AD_SELECTION_ID, UNSET_AD_SELECTION_ID_MESSAGE);

            return new ProcessAdSelectionResultRequest(mAdSelectionId, mSeller, mAdSelectionResult);
        }
    }
}
