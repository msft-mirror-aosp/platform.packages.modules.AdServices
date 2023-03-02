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
import android.os.Parcel;
import android.os.Parcelable;

import com.android.adservices.AdServicesParcelableUtil;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

// TODO(b/266837113) link to setAppInstallAdvertisers once unhidden.

/**
 * A container for the ad filters that are based on app install state.
 *
 * <p>App install filters filter out ads based on the presence of packages installed on the device.
 * In order for filtering to work, a package must call the setAppInstallAdvertisers API with the
 * identifier of the adtech who owns this ad. If that call has been made, and the ad contains an
 * {@link AppInstallFilters} object whose package name set contains the name of the package, the ad
 * will be removed from the auction.
 *
 * <p>Note that the filtering is based on any package with one of the listed package names being on
 * the device. It is possible that the package holding the package name is not the application
 * targeted by the ad.
 *
 * @hide
 */
public final class AppInstallFilters implements Parcelable {

    @NonNull private final Set<String> mPackageNames;

    @NonNull
    public static final Creator<AppInstallFilters> CREATOR =
            new Creator<AppInstallFilters>() {
                @NonNull
                @Override
                public AppInstallFilters createFromParcel(@NonNull Parcel in) {
                    Objects.requireNonNull(in);
                    return new AppInstallFilters(in);
                }

                @NonNull
                @Override
                public AppInstallFilters[] newArray(int size) {
                    return new AppInstallFilters[size];
                }
            };

    private AppInstallFilters(@NonNull Builder builder) {
        Objects.requireNonNull(builder);

        mPackageNames = builder.mPackageNames;
    }

    private AppInstallFilters(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        mPackageNames = AdServicesParcelableUtil.readStringSetFromParcel(in);
    }

    /**
     * Gets the list of package names this ad is filtered on.
     *
     * <p>The ad containing this filter will be removed from the ad auction if any of the package
     * names are present on the device and have called setAppInstallAdvertisers.
     */
    @NonNull
    public Set<String> getPackageNames() {
        return mPackageNames;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);
        AdServicesParcelableUtil.writeStringSetToParcel(dest, mPackageNames);
    }

    /** @hide */
    @Override
    public int describeContents() {
        return 0;
    }

    /** Checks whether the {@link AppInstallFilters} objects contain the same information. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppInstallFilters)) return false;
        AppInstallFilters that = (AppInstallFilters) o;
        return mPackageNames.equals(that.mPackageNames);
    }

    /** Returns the hash of the {@link AppInstallFilters} object's data. */
    @Override
    public int hashCode() {
        return Objects.hash(mPackageNames);
    }

    @Override
    public String toString() {
        return "AppInstallFilters{" + "mPackageNames=" + mPackageNames + '}';
    }

    /** Builder for creating {@link AppInstallFilters} objects. */
    public static final class Builder {
        @NonNull private Set<String> mPackageNames = new HashSet<>();

        public Builder() {}

        /**
         * Gets the list of package names this ad is filtered on.
         *
         * <p>See {@link #getPackageNames()} for more information.
         */
        @NonNull
        public Builder setPackageNames(@NonNull Set<String> packageNames) {
            Objects.requireNonNull(packageNames);
            mPackageNames = packageNames;
            return this;
        }

        /** Builds and returns a {@link AppInstallFilters} instance. */
        @NonNull
        public AppInstallFilters build() {
            return new AppInstallFilters(this);
        }
    }
}
