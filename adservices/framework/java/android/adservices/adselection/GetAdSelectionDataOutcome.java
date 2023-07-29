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
 */
public final class GetAdSelectionDataOutcome implements Parcelable {
    private final long mAdSelectionId;
    @Nullable private final byte[] mAdSelectionData;

    @NonNull
    public static final Creator<GetAdSelectionDataOutcome> CREATOR =
            new Creator<>() {
                @Override
                public GetAdSelectionDataOutcome createFromParcel(Parcel in) {
                    Objects.requireNonNull(in);

                    return new GetAdSelectionDataOutcome(in);
                }

                @Override
                public GetAdSelectionDataOutcome[] newArray(int size) {
                    return new GetAdSelectionDataOutcome[size];
                }
            };

    private GetAdSelectionDataOutcome(long adSelectionId, @Nullable byte[] adSelectionData) {
        this.mAdSelectionId = adSelectionId;
        this.mAdSelectionData = adSelectionData;
    }

    private GetAdSelectionDataOutcome(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        this.mAdSelectionId = in.readLong();
        this.mAdSelectionData = in.createByteArray();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** Returns the adSelectionId that identifies the AdSelection. */
    @Nullable
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
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);

        dest.writeLong(mAdSelectionId);
        dest.writeByteArray(mAdSelectionData);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GetAdSelectionDataOutcome)) return false;
        GetAdSelectionDataOutcome that = (GetAdSelectionDataOutcome) o;
        return mAdSelectionId == that.mAdSelectionId
                && Arrays.equals(mAdSelectionData, that.mAdSelectionData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAdSelectionId, Arrays.hashCode(mAdSelectionData));
    }

    /**
     * Builder for {@link GetAdSelectionDataOutcome} objects.
     *
     * @hide
     */
    public static final class Builder {
        private long mAdSelectionId;
        @Nullable private byte[] mAdSelectionData;

        public Builder() {}

        /** Sets the adSelectionId. */
        @NonNull
        public GetAdSelectionDataOutcome.Builder setAdSelectionId(long adSelectionId) {
            this.mAdSelectionId = adSelectionId;
            return this;
        }

        /** Sets the adSelectionData. */
        @NonNull
        public GetAdSelectionDataOutcome.Builder setAdSelectionData(
                @Nullable byte[] adSelectionData) {
            if (!Objects.isNull(adSelectionData)) {
                this.mAdSelectionData = Arrays.copyOf(adSelectionData, adSelectionData.length);
            } else {
                this.mAdSelectionData = null;
            }
            return this;
        }

        /**
         * Builds a {@link GetAdSelectionDataOutcome} instance.
         *
         * @throws IllegalArgumentException if the adSelectionIid is not set
         * @throws NullPointerException if the RenderUri is null
         */
        @NonNull
        public GetAdSelectionDataOutcome build() {
            Preconditions.checkArgument(
                    mAdSelectionId != UNSET_AD_SELECTION_ID, UNSET_AD_SELECTION_ID_MESSAGE);

            return new GetAdSelectionDataOutcome(mAdSelectionId, mAdSelectionData);
        }
    }
}
