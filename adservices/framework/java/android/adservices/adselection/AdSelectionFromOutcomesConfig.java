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

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;
import java.util.Objects;

/**
 * Contains the configuration of the ad selection process that takes ad selection ids as inputs.
 *
 * <p>Instances of this class are created by SDKs to be provided as arguments to the {@link
 * AdSelectionManager#selectAds} methods in {@link AdSelectionManager}.
 *
 * @hide
 */
public final class AdSelectionFromOutcomesConfig implements Parcelable {
    @NonNull private final AdTechIdentifier mSeller;
    @NonNull private final List<Long> mAdSelectionIds;
    @NonNull private final AdSelectionSignals mSelectionSignals;
    @NonNull private final Uri mSelectionLogicUri;

    @NonNull
    public static final Creator<AdSelectionFromOutcomesConfig> CREATOR =
            new Creator<AdSelectionFromOutcomesConfig>() {
                @Override
                public AdSelectionFromOutcomesConfig createFromParcel(@NonNull Parcel in) {
                    return new AdSelectionFromOutcomesConfig(in);
                }

                @Override
                public AdSelectionFromOutcomesConfig[] newArray(int size) {
                    return new AdSelectionFromOutcomesConfig[size];
                }
            };

    private AdSelectionFromOutcomesConfig(
            @NonNull AdTechIdentifier seller,
            @NonNull List<Long> adSelectionIds,
            @NonNull AdSelectionSignals selectionSignals,
            @NonNull Uri selectionLogicUri) {
        Objects.requireNonNull(seller);
        Objects.requireNonNull(adSelectionIds);
        Objects.requireNonNull(selectionSignals);
        Objects.requireNonNull(selectionLogicUri);

        this.mSeller = seller;
        this.mAdSelectionIds = adSelectionIds;
        this.mSelectionSignals = selectionSignals;
        this.mSelectionLogicUri = selectionLogicUri;
    }

    private AdSelectionFromOutcomesConfig(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        this.mSeller = AdTechIdentifier.CREATOR.createFromParcel(in);
        this.mAdSelectionIds = in.readArrayList(Long.class.getClassLoader(), Long.class);
        this.mSelectionSignals = AdSelectionSignals.CREATOR.createFromParcel(in);
        this.mSelectionLogicUri = Uri.CREATOR.createFromParcel(in);
    }

    @NonNull
    public AdTechIdentifier getSeller() {
        return mSeller;
    }

    @NonNull
    public List<Long> getAdSelectionIds() {
        return mAdSelectionIds;
    }

    @NonNull
    public AdSelectionSignals getSelectionSignals() {
        return mSelectionSignals;
    }

    @NonNull
    public Uri getSelectionLogicUri() {
        return mSelectionLogicUri;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);

        mSeller.writeToParcel(dest, flags);
        dest.writeList(mAdSelectionIds);
        mSelectionSignals.writeToParcel(dest, flags);
        mSelectionLogicUri.writeToParcel(dest, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdSelectionFromOutcomesConfig)) return false;
        AdSelectionFromOutcomesConfig that = (AdSelectionFromOutcomesConfig) o;
        return Objects.equals(this.mSeller, that.mSeller)
                && Objects.equals(this.mAdSelectionIds, that.mAdSelectionIds)
                && Objects.equals(this.mSelectionSignals, that.mSelectionSignals)
                && Objects.equals(this.mSelectionLogicUri, that.mSelectionLogicUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSeller, mAdSelectionIds, mSelectionSignals, mSelectionLogicUri);
    }

    /**
     * Builder for {@link AdSelectionFromOutcomesConfig} objects.
     *
     * @hide
     */
    public static final class Builder {
        @Nullable private AdTechIdentifier mSeller;
        @Nullable private List<Long> mAdSelectionIds;
        @Nullable private AdSelectionSignals mSelectionSignals;
        @Nullable private Uri mSelectionLogicUri;

        public Builder() {}

        /** Sets the seller {@link AdTechIdentifier}. */
        @NonNull
        public AdSelectionFromOutcomesConfig.Builder setSeller(@NonNull AdTechIdentifier seller) {
            Objects.requireNonNull(seller);

            this.mSeller = seller;
            return this;
        }

        /** Sets the list of ad selection ids. */
        @NonNull
        public AdSelectionFromOutcomesConfig.Builder setAdSelectionIds(
                @NonNull List<Long> adSelectionIds) {
            Objects.requireNonNull(adSelectionIds);

            this.mAdSelectionIds = adSelectionIds;
            return this;
        }

        /** Sets the AdSelectionSignals. */
        @NonNull
        public AdSelectionFromOutcomesConfig.Builder setSelectionSignals(
                @NonNull AdSelectionSignals selectionSignals) {
            Objects.requireNonNull(selectionSignals);

            this.mSelectionSignals = selectionSignals;
            return this;
        }

        /** Sets the Uri. */
        @NonNull
        public AdSelectionFromOutcomesConfig.Builder setSelectionUri(
                @NonNull Uri selectionLogicUri) {
            Objects.requireNonNull(selectionLogicUri);

            this.mSelectionLogicUri = selectionLogicUri;
            return this;
        }

        /** Builds a {@link AdSelectionFromOutcomesConfig} instance. */
        @NonNull
        public AdSelectionFromOutcomesConfig build() {
            Objects.requireNonNull(mSeller);
            Objects.requireNonNull(mAdSelectionIds);
            Objects.requireNonNull(mSelectionSignals);
            Objects.requireNonNull(mSelectionLogicUri);

            return new AdSelectionFromOutcomesConfig(
                    mSeller, mAdSelectionIds, mSelectionSignals, mSelectionLogicUri);
        }
    }
}
