/*
 * Copyright (C) 2024 The Android Open Source Project
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
import android.os.OutcomeReceiver;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.adservices.AdServicesParcelableUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * This object will be part of the {@link GetAdSelectionDataRequest} and will be constructed and
 * used by the SDK to influence the size of the response of {@link
 * AdSelectionManager#getAdSelectionData(GetAdSelectionDataRequest, Executor, OutcomeReceiver)}
 *
 * @hide
 */
public final class SellerConfiguration implements Parcelable {
    private final int mTargetPayloadSizeBytes;
    private final Set<PerBuyerConfiguration> mPerBuyerConfigurations;

    private SellerConfiguration(Parcel in) {
        mTargetPayloadSizeBytes = in.readInt();
        List<PerBuyerConfiguration> perBuyerConfigurationList =
                AdServicesParcelableUtil.readNullableFromParcel(
                        in,
                        (sourceParcel -> in.createTypedArrayList(PerBuyerConfiguration.CREATOR)));
        if (Objects.nonNull(perBuyerConfigurationList)) {
            mPerBuyerConfigurations = new HashSet<>(perBuyerConfigurationList);
        } else {
            mPerBuyerConfigurations = new HashSet<>();
        }
    }

    public static final Creator<SellerConfiguration> CREATOR =
            new Creator<>() {
                @Override
                public SellerConfiguration createFromParcel(@NonNull Parcel in) {
                    Objects.requireNonNull(in);

                    return new SellerConfiguration(in);
                }

                @Override
                public SellerConfiguration[] newArray(int size) {
                    return new SellerConfiguration[size];
                }
            };

    private SellerConfiguration(
            int targetPayloadSizeBytes, Set<PerBuyerConfiguration> perBuyerConfigurations) {

        mTargetPayloadSizeBytes = targetPayloadSizeBytes;
        mPerBuyerConfigurations = perBuyerConfigurations;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);

        dest.writeInt(mTargetPayloadSizeBytes);
        AdServicesParcelableUtil.writeNullableToParcel(
                dest,
                mPerBuyerConfigurations,
                (targetParcel, sourceSet) -> dest.writeTypedList(new ArrayList<>(sourceSet)));
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof SellerConfiguration) {
            SellerConfiguration sellerConfiguration = (SellerConfiguration) o;
            return Objects.equals(
                            mTargetPayloadSizeBytes, sellerConfiguration.mTargetPayloadSizeBytes)
                    && Objects.equals(
                            mPerBuyerConfigurations, sellerConfiguration.mPerBuyerConfigurations);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTargetPayloadSizeBytes, mPerBuyerConfigurations);
    }

    /**
     * Returns the size of the payload in bytes that the service will return. If there is not enough
     * data to fill up the payload to this limit, the service will fill up the rest of the payload
     * with padding.
     */
    public int getTargetPayloadSizeBytes() {
        return mTargetPayloadSizeBytes;
    }

    /**
     * Returns a set of per buyer configurations that the service will do a best effort to respect
     * when constructing the response without exceeding {@link #getTargetPayloadSizeBytes()}.
     *
     * <p>If this is empty, the service will fill up the response with buyer data until {@link
     * #getTargetPayloadSizeBytes()} is reached. Otherwise, only data from buyers from the per buyer
     * configuration will be included. If the sum of {@link
     * PerBuyerConfiguration#getTargetInputSizeBytes()} sizes is larger than {@link
     * #getTargetPayloadSizeBytes()}, the service will do a best effort attempt to proportionally
     * include the buyer data based on the ratio between that specific buyer's target and the sum of
     * {@link PerBuyerConfiguration#getTargetInputSizeBytes()}.
     */
    @NonNull
    public Set<PerBuyerConfiguration> getPerBuyerConfigurations() {
        return mPerBuyerConfigurations;
    }

    /** Builder for {@link SellerConfiguration} objects. */
    public static final class Builder {
        private int mTargetPayloadSizeBytes;
        @NonNull private Set<PerBuyerConfiguration> mPerBuyerConfigurations = new HashSet<>();

        /**
         * Sets the target payload size in bytes. For more information see {@link
         * #getTargetPayloadSizeBytes()}
         */
        @NonNull
        public Builder setTargetPayloadSizeBytes(int targetPayloadSizeBytes) {
            if (targetPayloadSizeBytes <= 0) {
                throw new IllegalArgumentException("Target size must be greater than 0.");
            }

            mTargetPayloadSizeBytes = targetPayloadSizeBytes;
            return this;
        }

        /**
         * Sets the per buyer configurations. For more information see {@link
         * #getPerBuyerConfigurations()}
         */
        @NonNull
        public Builder setPerBuyerConfigurations(
                @NonNull Set<PerBuyerConfiguration> perBuyerConfigurations) {
            mPerBuyerConfigurations =
                    Objects.requireNonNull(
                            perBuyerConfigurations, "Per Buyer Configurations cannot be null.");
            return this;
        }

        /** Builds a {@link SellerConfiguration} instance. */
        @NonNull
        public SellerConfiguration build() {
            if (mTargetPayloadSizeBytes == 0) {
                throw new IllegalStateException("Target size must be set.");
            }

            return new SellerConfiguration(mTargetPayloadSizeBytes, mPerBuyerConfigurations);
        }
    }
}
