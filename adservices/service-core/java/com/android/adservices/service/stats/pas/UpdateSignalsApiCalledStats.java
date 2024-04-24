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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JSON_PROCESSING_STATUS_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_UNSET;

import com.android.adservices.service.stats.AdsRelevanceStatusUtils;

import com.google.auto.value.AutoValue;

/** Class for updateSignals API called stats. */
@AutoValue
public abstract class UpdateSignalsApiCalledStats {
    /** Returns http response code. */
    public abstract int getHttpResponseCode();

    /** Returns the size of the JSON. */
    @AdsRelevanceStatusUtils.Size
    public abstract int getJsonSize();

    /** Returns the status of JSON processing. */
    @AdsRelevanceStatusUtils.JsonProcessingStatus
    public abstract int getJsonProcessingStatus();

    /** Returns the package uid when the JsonProcessingStatus is not success. */
    public abstract int getPackageUid();

    /** Returns AdTech's enrollment id when the JsonProcessingStatus is not success. */
    public abstract String getAdTechId();

    /** Returns generic builder */
    public static Builder builder() {
        return new AutoValue_UpdateSignalsApiCalledStats.Builder()
                .setJsonProcessingStatus(JSON_PROCESSING_STATUS_UNSET)
                .setHttpResponseCode(0)
                .setJsonSize(SIZE_UNSET)
                .setPackageUid(0)
                .setAdTechId("");
    }

    /** Builder class for UpdateSignalsApiCalledStats. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setHttpResponseCode(int value);

        public abstract Builder setJsonSize(@AdsRelevanceStatusUtils.Size int value);

        public abstract Builder setJsonProcessingStatus(
                @AdsRelevanceStatusUtils.JsonProcessingStatus int value);

        public abstract Builder setPackageUid(int value);

        public abstract Builder setAdTechId(String value);

        public abstract UpdateSignalsApiCalledStats build();
    }
}
