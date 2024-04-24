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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_UNSET;

import com.android.adservices.service.stats.AdsRelevanceStatusUtils;

import com.google.auto.value.AutoValue;

/** Class for logging execution of the encoding JavaScript stats. */
@AutoValue
public abstract class EncodingJsExecutionStats {
    /** Returns the time to run the JavaScript. */
    public abstract int getJsLatency();

    /** Returns encoded signals size in bytes. */
    public abstract int getEncodedSignalsSize();

    /** Returns JavaScript run status. */
    @AdsRelevanceStatusUtils.JsRunStatus
    public abstract int getRunStatus();

    /** Returns how much memory did the JavaScript use. */
    public abstract int getJsMemoryUsed();

    /** Returns AdTech's eTLD+1 when the JsRunStatus is not success. */
    public abstract String getAdTechId();

    /** Returns generic builder. */
    public static Builder builder() {
        return new AutoValue_EncodingJsExecutionStats.Builder()
                .setJsLatency(SIZE_UNSET)
                .setEncodedSignalsSize(SIZE_UNSET)
                .setRunStatus(JS_RUN_STATUS_UNSET)
                .setJsMemoryUsed(0)
                .setAdTechId("");
    }

    /** Builder class for EncodingJsExecutionStats. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setJsLatency(int value);

        public abstract Builder setEncodedSignalsSize(int value);

        public abstract Builder setRunStatus(@AdsRelevanceStatusUtils.JsRunStatus int value);

        public abstract Builder setJsMemoryUsed(int value);

        public abstract Builder setAdTechId(String value);

        public abstract EncodingJsExecutionStats build();
    }
}
