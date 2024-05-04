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

package com.android.adservices.service.stats;

import static com.android.adservices.service.stats.AdServicesLoggerUtil.FIELD_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_UNSET;

import com.google.auto.value.AutoValue;

/** Class for SelectAdsFromOutcomes API Called stats */
@AutoValue
public abstract class SelectAdsFromOutcomesApiCalledStats {

    /** Number of IDs passed to the mediation call */
    public abstract int getCountIds();

    /** Number of non-existing IDs during mediation call */
    public abstract int getCountNonExistingIds();

    /** Whether the truncation API call used a prebuilt script */
    public abstract boolean getUsedPrebuilt();

    /** Mediation script download result code */
    public abstract int getDownloadResultCode();

    /** Mediation script download latency in milliseconds */
    public abstract int getDownloadLatencyMillis();

    /** Mediation script execution result code */
    @AdsRelevanceStatusUtils.JsRunStatus
    public abstract int getExecutionResultCode();

    /** Mediation script execution latency in milliseconds */
    public abstract int getExecutionLatencyMillis();

    /** Returns a generic builder. */
    public static Builder builder() {
        return new AutoValue_SelectAdsFromOutcomesApiCalledStats.Builder()
                .setCountIds(FIELD_UNSET)
                .setCountNonExistingIds(FIELD_UNSET)
                .setDownloadResultCode(FIELD_UNSET)
                .setDownloadLatencyMillis(FIELD_UNSET)
                .setExecutionResultCode(JS_RUN_STATUS_UNSET)
                .setExecutionLatencyMillis(FIELD_UNSET);
    }

    /** Builder class for SelectAdsFromOutcomesApiCalledStats. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the number of IDs passed to the mediation call */
        public abstract Builder setCountIds(int countIds);

        /** Sets the number of non-existing IDs during mediation call */
        public abstract Builder setCountNonExistingIds(int countNonExistingIds);

        /** Sets whether the truncation API call used a prebuilt script */
        public abstract Builder setUsedPrebuilt(boolean usedPrebuilt);

        /** Sets the mediation script download result code */
        public abstract Builder setDownloadResultCode(int downloadResultCode);

        /** Sets the mediation script download latency in milliseconds */
        public abstract Builder setDownloadLatencyMillis(int downloadLatencyMillis);

        /** Sets the mediation script execution result code */
        public abstract Builder setExecutionResultCode(
                @AdsRelevanceStatusUtils.JsRunStatus int executionResultCode);

        /** Sets the mediation script execution latency in milliseconds */
        public abstract Builder setExecutionLatencyMillis(int executionLatencyMillis);

        /** Builds the {@link SelectAdsFromOutcomesApiCalledStats} object. */
        public abstract SelectAdsFromOutcomesApiCalledStats build();
    }
}
