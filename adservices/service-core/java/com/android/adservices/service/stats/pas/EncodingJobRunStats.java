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

package com.android.adservices.service.stats.pas;

import com.android.adservices.service.stats.AdsRelevanceStatusUtils;

import com.google.auto.value.AutoValue;

/** Class for logging per encoding background job run stats. */
@AutoValue
public abstract class EncodingJobRunStats {
    /** Returns the number of AdTechs who successfully encoded in this background job run. */
    public abstract int getSignalEncodingSuccesses();

    /** Returns the number of AdTechs who failed to encoded in this background job run. */
    public abstract int getSignalEncodingFailures();

    /** Returns the number of AdTechs skipped due to their signals being unmodified. */
    public abstract int getSignalEncodingSkips();

    /** Returns the PAS encoding source type. */
    public abstract int getEncodingSourceType();

    /** Returns generic builder. */
    public static Builder builder() {
        return new AutoValue_EncodingJobRunStats.Builder()
                .setEncodingSourceType(AdsRelevanceStatusUtils.PAS_ENCODING_SOURCE_TYPE_UNSET);
    }

    /** Builder class for EncodingJobRunStats. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setSignalEncodingSuccesses(int value);

        public abstract Builder setSignalEncodingFailures(int value);

        public abstract Builder setSignalEncodingSkips(int value);

        public abstract Builder setEncodingSourceType(
                @AdsRelevanceStatusUtils.PasEncodingSourceType int encodingSourceType);

        public abstract EncodingJobRunStats build();
    }
}
