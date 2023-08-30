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

import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.android.internal.util.Preconditions;

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents ad selection data collected from device for ad selection.
 *
 * @hide
 */
public final class GetAdSelectionDataResponse implements Parcelable {
    private final long mAdSelectionId;
    @Nullable private final byte[] mAdSelectionData;

    public static final Creator<GetAdSelectionDataResponse> CREATOR =
            new Creator<>() {
                @Override
                public GetAdSelectionDataResponse createFromParcel(Parcel in) {
                    Objects.requireNonNull(in);

                    return new GetAdSelectionDataResponse(in);
                }

                @Override
                public GetAdSelectionDataResponse[] newArray(int size) {
                    return new GetAdSelectionDataResponse[size];
                }
            };

    private GetAdSelectionDataResponse(long adSelectionId, byte[] adSelectionData) {
        this.mAdSelectionId = adSelectionId;
        this.mAdSelectionData = adSelectionData;
    }

    private GetAdSelectionDataResponse(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        this.mAdSelectionId = in.readLong();
        this.mAdSelectionData = in.createByteArray();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof GetAdSelectionDataResponse) {
            GetAdSelectionDataResponse response = (GetAdSelectionDataResponse) o;
            return mAdSelectionId == response.mAdSelectionId
                    && Arrays.equals(mAdSelectionData, response.mAdSelectionData);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAdSelectionId, Arrays.hashCode(mAdSelectionData));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** Returns the adSelectionId that identifies the AdSelection. */
    public long getAdSelectionId() {
        return mAdSelectionId;
    }

    /** Returns the adSelectionData that is collected from device. */
    @Nullable
    public byte[] getAdSelectionData() {
        if (Objects.isNull(mAdSelectionData)) {
            return null;
        } else {
            return Arrays.copyOf(mAdSelectionData, mAdSelectionData.length);
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest);

        dest.writeLong(mAdSelectionId);
        dest.writeByteArray(mAdSelectionData);
    }

    /**
     * Builder for {@link GetAdSelectionDataResponse} objects.
     *
     * @hide
     */
    public static final class Builder {
        private long mAdSelectionId;
        @Nullable private byte[] mAdSelectionData;

        public Builder() {}

        /** Sets the adSelectionId. */
        @NonNull
        public GetAdSelectionDataResponse.Builder setAdSelectionId(long adSelectionId) {
            this.mAdSelectionId = adSelectionId;
            return this;
        }

        /** Sets the adSelectionData. */
        @NonNull
        public GetAdSelectionDataResponse.Builder setAdSelectionData(
                @Nullable byte[] adSelectionData) {
            if (!Objects.isNull(adSelectionData)) {
                this.mAdSelectionData = Arrays.copyOf(adSelectionData, adSelectionData.length);
            } else {
                this.mAdSelectionData = null;
            }
            return this;
        }

        /**
         * Builds a {@link GetAdSelectionDataResponse} instance.
         *
         * @throws IllegalArgumentException if the adSelectionId is not set
         */
        @NonNull
        public GetAdSelectionDataResponse build() {
            Preconditions.checkArgument(
                    mAdSelectionId != UNSET_AD_SELECTION_ID, UNSET_AD_SELECTION_ID_MESSAGE);

            return new GetAdSelectionDataResponse(mAdSelectionId, mAdSelectionData);
        }
    }
}
