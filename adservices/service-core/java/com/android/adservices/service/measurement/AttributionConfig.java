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

package com.android.adservices.service.measurement;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.Pair;

import java.util.List;
import java.util.Objects;

/** POJO for AttributionConfig. */
public class AttributionConfig {

    @NonNull private final String mSourceAdtech;
    @Nullable private final Pair<Long, Long> mSourcePriorityRange;
    @Nullable private final List<FilterMap> mSourceFilters;
    @Nullable private final List<FilterMap> mSourceNotFilters;
    @Nullable private final Long mSourceExpiryOverride;
    @Nullable private final Long mPriority;
    @Nullable private final Long mExpiry;
    @Nullable private final List<FilterMap> mFilterData;
    @Nullable private final Long mPostInstallExclusivityWindow;

    private AttributionConfig(@NonNull AttributionConfig.Builder builder) {
        mSourceAdtech = builder.mSourceAdtech;
        mSourcePriorityRange = builder.mSourcePriorityRange;
        mSourceFilters = builder.mSourceFilters;
        mSourceNotFilters = builder.mSourceNotFilters;
        mSourceExpiryOverride = builder.mSourceExpiryOverride;
        mPriority = builder.mPriority;
        mExpiry = builder.mExpiry;
        mFilterData = builder.mFilterData;
        mPostInstallExclusivityWindow = builder.mPostInstallExclusivityWindow;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AttributionConfig)) {
            return false;
        }
        AttributionConfig attributionConfig = (AttributionConfig) obj;
        return Objects.equals(mSourceAdtech, attributionConfig.mSourceAdtech)
                && Objects.equals(mSourcePriorityRange, attributionConfig.mSourcePriorityRange)
                && Objects.equals(mSourceFilters, attributionConfig.mSourceFilters)
                && Objects.equals(mSourceNotFilters, attributionConfig.mSourceNotFilters)
                && Objects.equals(mSourceExpiryOverride, attributionConfig.mSourceExpiryOverride)
                && Objects.equals(mPriority, attributionConfig.mPriority)
                && Objects.equals(mExpiry, attributionConfig.mExpiry)
                && Objects.equals(mFilterData, attributionConfig.mFilterData)
                && Objects.equals(
                        mPostInstallExclusivityWindow,
                        attributionConfig.mPostInstallExclusivityWindow);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mSourceAdtech,
                mSourcePriorityRange,
                mSourceFilters,
                mSourceNotFilters,
                mSourceExpiryOverride,
                mPriority,
                mExpiry,
                mFilterData,
                mPostInstallExclusivityWindow);
    }

    /** Returns the source adtech as String. */
    @NonNull
    public String getSourceAdtech() {
        return mSourceAdtech;
    }

    /**
     * Returns source priority range JSONObject as a Pair of Long values. example:
     * "source_priority_range": { “start”: 100, “end”: 1000 }
     */
    @Nullable
    public Pair<Long, Long> getSourcePriorityRange() {
        return mSourcePriorityRange;
    }

    /**
     * Returns source filter JSONObject as List of FilterMap. example: "source_filters": {
     * "campaign_type": ["install"], "source_type": ["navigation"] }
     */
    @Nullable
    public List<FilterMap> getSourceFilters() {
        return mSourceFilters;
    }

    /**
     * Returns source not filter JSONObject as List of FilterMap. example: "source_not_filters": {
     * "campaign_type": ["install"] }
     */
    @Nullable
    public List<FilterMap> getSourceNotFilters() {
        return mSourceNotFilters;
    }

    /**
     * Returns source expiry override as long. Source registration time + source expiry override <
     * trigger time.
     */
    @Nullable
    public Long getSourceExpiryOverride() {
        return mSourceExpiryOverride;
    }

    /** Returns the derived priority of the source as long */
    @Nullable
    public Long getPriority() {
        return mPriority;
    }

    /** Returns the derived source expiry as long. */
    @Nullable
    public Long getExpiry() {
        return mExpiry;
    }

    /**
     * Returns the derived filter data of the source as List of FilterMap. example: "filter_data": {
     * "conversion_subdomain": ["electronics.megastore"], "product": ["1234", "234"] }
     */
    @Nullable
    public List<FilterMap> getFilterData() {
        return mFilterData;
    }

    /** Returns the derived post install exclusivity window as long. */
    @Nullable
    public Long getPostInstallExclusivityWindow() {
        return mPostInstallExclusivityWindow;
    }

    /** Builder for {@link AttributionConfig}. */
    public static final class Builder {
        private String mSourceAdtech;
        private Pair<Long, Long> mSourcePriorityRange;
        private List<FilterMap> mSourceFilters;
        private List<FilterMap> mSourceNotFilters;
        private Long mSourceExpiryOverride;
        private Long mPriority;
        private Long mExpiry;
        private List<FilterMap> mFilterData;
        private Long mPostInstallExclusivityWindow;

        public Builder() {}

        /** See {@link AttributionConfig#getSourceAdtech()} */
        @NonNull
        public Builder setSourceAdtech(@NonNull String sourceAdtech) {
            Objects.requireNonNull(sourceAdtech);
            mSourceAdtech = sourceAdtech;
            return this;
        }

        /** See {@link AttributionConfig#getSourcePriorityRange()} */
        @NonNull
        public Builder setSourcePriorityRange(@Nullable Pair<Long, Long> sourcePriorityRange) {
            mSourcePriorityRange = sourcePriorityRange;
            return this;
        }

        /** See {@link AttributionConfig#getSourceFilters()} */
        @NonNull
        public Builder setSourceFilters(@Nullable List<FilterMap> sourceFilters) {
            mSourceFilters = sourceFilters;
            return this;
        }

        /** See {@link AttributionConfig#getSourceNotFilters()} */
        @NonNull
        public Builder setSourceNotFilters(@Nullable List<FilterMap> sourceNotFilters) {
            mSourceNotFilters = sourceNotFilters;
            return this;
        }

        /** See {@link AttributionConfig#getSourceExpiryOverride()} */
        @NonNull
        public Builder setSourceExpiryOverride(@Nullable Long sourceExpiryOverride) {
            mSourceExpiryOverride = sourceExpiryOverride;
            return this;
        }

        /** See {@link AttributionConfig#getPriority()} */
        @NonNull
        public Builder setPriority(@Nullable Long priority) {
            mPriority = priority;
            return this;
        }

        /** See {@link AttributionConfig#getExpiry()} */
        @NonNull
        public Builder setExpiry(@Nullable Long expiry) {
            mExpiry = expiry;
            return this;
        }

        /** See {@link AttributionConfig#getFilterData()} */
        @NonNull
        public Builder setFilterData(@Nullable List<FilterMap> filterData) {
            mFilterData = filterData;
            return this;
        }

        /** See {@link AttributionConfig#getPostInstallExclusivityWindow()} */
        @NonNull
        public Builder setPostInstallExclusivityWindow(
                @Nullable Long postInstallExclusivityWindow) {
            mPostInstallExclusivityWindow = postInstallExclusivityWindow;
            return this;
        }

        /** Build the {@link AttributionConfig}. */
        @NonNull
        public AttributionConfig build() {
            Objects.requireNonNull(mSourceAdtech);
            return new AttributionConfig(this);
        }
    }
}
