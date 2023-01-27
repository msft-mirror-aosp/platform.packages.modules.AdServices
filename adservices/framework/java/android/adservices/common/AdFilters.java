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

package android.adservices.common;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.adservices.AdServicesParcelableUtil;

import java.util.Objects;

/**
 * A container class for filters which are associated with an ad.
 *
 * <p>If any of the filters in an {@link AdFilters} instance are met or exceeded, the associated ad
 * will not be eligible for ad selection. Filters are optional ad parameters and are not required as
 * part of {@link AdData}.
 *
 * @hide
 */
// TODO(b/221876775): Unhide for frequency cap API review
public final class AdFilters implements Parcelable {
    @Nullable private final FrequencyCapFilters mFrequencyCapFilters;
    @Nullable private final AppInstallFilters mAppInstallFilters;

    @NonNull
    public static final Creator<AdFilters> CREATOR =
            new Creator<AdFilters>() {
                @Override
                public AdFilters createFromParcel(@NonNull Parcel in) {
                    Objects.requireNonNull(in);
                    return new AdFilters(in);
                }

                @Override
                public AdFilters[] newArray(int size) {
                    return new AdFilters[size];
                }
            };

    private AdFilters(@NonNull Builder builder) {
        Objects.requireNonNull(builder);

        mFrequencyCapFilters = builder.mFrequencyCapFilters;
        mAppInstallFilters = builder.mAppInstallFilters;
    }

    private AdFilters(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        mFrequencyCapFilters =
                AdServicesParcelableUtil.readNullableFromParcel(
                        in, FrequencyCapFilters.CREATOR::createFromParcel);
        mAppInstallFilters =
                AdServicesParcelableUtil.readNullableFromParcel(
                        in, AppInstallFilters.CREATOR::createFromParcel);
    }

    /**
     * Gets the {@link FrequencyCapFilters} instance that represents all frequency cap filters for
     * the ad.
     *
     * <p>If {@code null}, there are no frequency cap filters which apply to the ad.
     */
    @Nullable
    public FrequencyCapFilters getFrequencyCapFilters() {
        return mFrequencyCapFilters;
    }

    /**
     * Gets the {@link AppInstallFilters} instance that represents all app install filters for the
     * ad.
     *
     * <p>If {@code null}, there are no app install filters which apply to the ad.
     */
    @Nullable
    public AppInstallFilters getAppInstallFilters() {
        return mAppInstallFilters;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);

        AdServicesParcelableUtil.writeNullableToParcel(
                dest,
                mFrequencyCapFilters,
                (targetParcel, sourceFilters) -> sourceFilters.writeToParcel(targetParcel, flags));
        AdServicesParcelableUtil.writeNullableToParcel(
                dest,
                mAppInstallFilters,
                (targetParcel, sourceFilters) -> sourceFilters.writeToParcel(targetParcel, flags));
    }

    /** @hide */
    @Override
    public int describeContents() {
        return 0;
    }

    /** Checks whether the {@link AdFilters} objects represent the same set of filters. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdFilters)) return false;
        AdFilters adFilters = (AdFilters) o;
        return Objects.equals(mFrequencyCapFilters, adFilters.mFrequencyCapFilters)
                && Objects.equals(mAppInstallFilters, adFilters.mAppInstallFilters);
    }

    /** Returns the hash of the {@link AdFilters} object's data. */
    @Override
    public int hashCode() {
        return Objects.hash(mFrequencyCapFilters, mAppInstallFilters);
    }

    @Override
    public String toString() {
        return "AdFilters{"
                + "mFrequencyCapFilters="
                + mFrequencyCapFilters
                + ", mAppInstallFilters="
                + mAppInstallFilters
                + '}';
    }

    /** Builder for creating {@link AdFilters} objects. */
    public static final class Builder {
        @Nullable private FrequencyCapFilters mFrequencyCapFilters;
        @Nullable private AppInstallFilters mAppInstallFilters;

        public Builder() {}

        /**
         * Sets the {@link FrequencyCapFilters} which will apply to the ad.
         *
         * <p>If set to {@code null} or not set, no frequency cap filters will be associated with
         * the ad.
         */
        @NonNull
        public Builder setFrequencyCapFilters(@Nullable FrequencyCapFilters frequencyCapFilters) {
            mFrequencyCapFilters = frequencyCapFilters;
            return this;
        }

        /**
         * Sets the {@link AppInstallFilters} which will apply to the ad.
         *
         * <p>If set to {@code null} or not set, no app install filters will be associated with the
         * ad.
         */
        @NonNull
        public Builder setAppInstallFilters(@Nullable AppInstallFilters appInstallFilters) {
            mAppInstallFilters = appInstallFilters;
            return this;
        }

        /** Builds and returns an {@link AdFilters} instance. */
        @NonNull
        public AdFilters build() {
            return new AdFilters(this);
        }
    }
}
