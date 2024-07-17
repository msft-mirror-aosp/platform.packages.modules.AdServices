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

package com.android.adservices.service.adselection;

import android.adservices.adselection.PerBuyerConfiguration;

import androidx.annotation.NonNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;

import java.util.Set;

/** POJO to hold fields used for payload optimization. */
@AutoValue
public abstract class PayloadOptimizationContext {
    abstract boolean getOptimizationsEnabled();

    abstract int getMaxBuyerInputSizeBytes();

    abstract Set<PerBuyerConfiguration> getPerBuyerConfigurations();

    /** Returns a builder for a {@link PayloadOptimizationContext}. */
    public static Builder builder() {
        return new AutoValue_PayloadOptimizationContext.Builder()
                .setOptimizationsEnabled(false)
                .setMaxBuyerInputSizeBytes(0)
                .setPerBuyerConfigurations(ImmutableSet.of());
    }

    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets whether optimizations are enabled. */
        public abstract Builder setOptimizationsEnabled(boolean optimizationsEnabled);

        /** Sets the maximum size in bytes of buyer inputs created by {@link BuyerInputGenerator} */
        public abstract Builder setMaxBuyerInputSizeBytes(int maxBuyerInputSizeBytes);

        /**
         * Sets the per buyer configurations that the service will attempt to respect during buyer
         * input generation.
         */
        public abstract Builder setPerBuyerConfigurations(
                @NonNull Set<PerBuyerConfiguration> value);

        /** Builds a {@link PayloadOptimizationContext}. */
        public abstract PayloadOptimizationContext build();
    }
}
