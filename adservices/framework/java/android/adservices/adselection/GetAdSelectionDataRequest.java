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

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * Represents a request containing the information to get ad selection data.
 *
 * <p>Instances of this class are created by SDKs to be provided as arguments to the {@link
 * AdSelectionManager#getAdSelectionData} methods in {@link AdSelectionManager}.
 *
 * @hide
 */
public final class GetAdSelectionDataRequest implements Parcelable {
    @Nullable private final AdTechIdentifier mSeller;

    @NonNull
    public static final Creator<GetAdSelectionDataRequest> CREATOR =
            new Creator<>() {
                @Override
                public GetAdSelectionDataRequest createFromParcel(@NonNull Parcel in) {
                    Objects.requireNonNull(in);

                    return new GetAdSelectionDataRequest(in);
                }

                @Override
                public GetAdSelectionDataRequest[] newArray(int size) {
                    return new GetAdSelectionDataRequest[size];
                }
            };

    private GetAdSelectionDataRequest(@Nullable AdTechIdentifier seller) {
        this.mSeller = seller;
    }

    private GetAdSelectionDataRequest(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        mSeller = AdTechIdentifier.CREATOR.createFromParcel(in);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);
        Objects.requireNonNull(mSeller);

        mSeller.writeToParcel(dest, flags);
    }

    /**
     * @return a AdTechIdentifier of the seller, for example "www.example-ssp.com"
     */
    @Nullable
    public AdTechIdentifier getSeller() {
        return mSeller;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GetAdSelectionDataRequest)) return false;
        GetAdSelectionDataRequest that = (GetAdSelectionDataRequest) o;
        return Objects.equals(mSeller, that.mSeller);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSeller);
    }

    /**
     * Builder for {@link GetAdSelectionDataRequest} objects.
     *
     * @hide
     */
    public static final class Builder {
        @Nullable private AdTechIdentifier mSeller;

        public Builder() {}

        /** Sets the seller {@link AdTechIdentifier}. */
        @NonNull
        public GetAdSelectionDataRequest.Builder setSeller(@Nullable AdTechIdentifier seller) {
            this.mSeller = seller;
            return this;
        }

        /**
         * Builds a {@link GetAdSelectionDataRequest} instance.
         *
         * @throws NullPointerException if the Seller is null
         */
        @NonNull
        public GetAdSelectionDataRequest build() {
            return new GetAdSelectionDataRequest(mSeller);
        }
    }
}
