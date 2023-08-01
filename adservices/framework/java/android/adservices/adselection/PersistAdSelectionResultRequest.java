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

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a request containing the seller, the ad selection id and data.
 *
 * <p>Instances of this class are created by SDKs to be provided as arguments to the {@link
 * AdSelectionManager#persistAdSelectionResult} methods in {@link AdSelectionManager}.
 *
 * @hide
 */
public final class PersistAdSelectionResultRequest implements Parcelable {
    private final long mAdSelectionId;
    @Nullable private final AdTechIdentifier mSeller;
    @Nullable private final byte[] mAdSelectionResult;

    @NonNull
    public static final Parcelable.Creator<PersistAdSelectionResultRequest> CREATOR =
            new Parcelable.Creator<>() {
                @Override
                public PersistAdSelectionResultRequest createFromParcel(@NonNull Parcel in) {
                    Objects.requireNonNull(in);

                    return new PersistAdSelectionResultRequest(in);
                }

                @Override
                public PersistAdSelectionResultRequest[] newArray(int size) {
                    return new PersistAdSelectionResultRequest[size];
                }
            };

    private PersistAdSelectionResultRequest(
            long adSelectionId,
            @Nullable AdTechIdentifier seller,
            @Nullable byte[] adSelectionResult) {
        this.mAdSelectionId = adSelectionId;
        this.mSeller = seller;
        this.mAdSelectionResult = adSelectionResult;
    }

    private PersistAdSelectionResultRequest(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        this.mAdSelectionId = in.readLong();
        this.mSeller = AdTechIdentifier.CREATOR.createFromParcel(in);
        this.mAdSelectionResult = in.createByteArray();
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
        dest.writeByteArray(mAdSelectionResult);
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
    public byte[] getAdSelectionResult() {
        if (Objects.isNull(mAdSelectionResult)) {
            return null;
        } else {
            return Arrays.copyOf(mAdSelectionResult, mAdSelectionResult.length);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PersistAdSelectionResultRequest)) return false;
        PersistAdSelectionResultRequest that = (PersistAdSelectionResultRequest) o;
        return mAdSelectionId == that.mAdSelectionId
                && Objects.equals(mSeller, that.mSeller)
                && Arrays.equals(mAdSelectionResult, that.mAdSelectionResult);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAdSelectionId, mSeller, Arrays.hashCode(mAdSelectionResult));
    }

    /** Builder for {@link PersistAdSelectionResultRequest} objects.
     *
     * @hide
     */
    public static final class Builder {
        private long mAdSelectionId;
        @Nullable private AdTechIdentifier mSeller;
        @Nullable private byte[] mAdSelectionResult;

        public Builder() {}

        /** Sets the ad selection id {@link Long}. */
        @NonNull
        public PersistAdSelectionResultRequest.Builder setAdSelectionId(long adSelectionId) {
            this.mAdSelectionId = adSelectionId;
            return this;
        }

        /** Sets the seller {@link AdTechIdentifier}. */
        @NonNull
        public PersistAdSelectionResultRequest.Builder setSeller(
                @Nullable AdTechIdentifier seller) {
            this.mSeller = seller;
            return this;
        }

        /** Sets the ad selection result {@link String}. */
        @NonNull
        public PersistAdSelectionResultRequest.Builder setAdSelectionResult(
                @Nullable byte[] adSelectionResult) {
            if (!Objects.isNull(adSelectionResult)) {
                this.mAdSelectionResult =
                        Arrays.copyOf(adSelectionResult, adSelectionResult.length);
            } else {
                this.mAdSelectionResult = null;
            }
            return this;
        }

        /**
         * Builds a {@link PersistAdSelectionResultRequest} instance.
         *
         * @throws IllegalArgumentException if the adSelectionIid is not set
         * @throws NullPointerException if the mAdSelectionResult or Seller is null
         */
        @NonNull
        public PersistAdSelectionResultRequest build() {
            Preconditions.checkArgument(
                    mAdSelectionId != UNSET_AD_SELECTION_ID, UNSET_AD_SELECTION_ID_MESSAGE);

            return new PersistAdSelectionResultRequest(mAdSelectionId, mSeller, mAdSelectionResult);
        }
    }
}
