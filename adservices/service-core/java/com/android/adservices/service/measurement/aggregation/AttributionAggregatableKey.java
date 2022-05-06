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

package com.android.adservices.service.measurement.aggregation;

import android.annotation.Nullable;

import java.util.Objects;

/**
 * key_piece in AttributionSource and AttributionTrigger. Tests are included in
 * AttributionSourceTest and AttributionTriggerTest.
 */
public class AttributionAggregatableKey {

    @Nullable
    private Long mHighBits;
    @Nullable
    private Long mLowBits;

    AttributionAggregatableKey() {
        mHighBits = null;
        mLowBits = null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AttributionAggregatableKey)) {
            return false;
        }
        AttributionAggregatableKey key = (AttributionAggregatableKey) obj;
        return Objects.equals(mHighBits, key.mHighBits) && Objects.equals(mLowBits, key.mLowBits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mHighBits, mLowBits);
    }

    /**
     * Returns high bits for this AttributionAggregatableKey.
     */
    @Nullable
    public Long getHighBits() {
        return mHighBits;
    }

    /**
     * Returns low bits for this AttributionAggregatableKey.
     */
    @Nullable
    public Long getLowBits() {
        return mLowBits;
    }

    /**
     * Builder for {@link AttributionAggregatableKey}.
     */
    public static final class Builder {
        private final AttributionAggregatableKey mBuilding;

        public Builder() {
            mBuilding = new AttributionAggregatableKey();
        }

        /**
         * See {@link AttributionAggregatableKey#getHighBits()}.
         */
        public Builder setHighBits(@Nullable Long highBits) {
            mBuilding.mHighBits = highBits;
            return this;
        }

        /**
         * See {@link AttributionAggregatableKey#getLowBits()}.
         */
        public Builder setLowBits(@Nullable Long lowBits) {
            mBuilding.mLowBits = lowBits;
            return this;
        }

        /**
         * Build the {@link AttributionAggregatableKey}.
         */
        public AttributionAggregatableKey build() {
            return mBuilding;
        }
    }
}
