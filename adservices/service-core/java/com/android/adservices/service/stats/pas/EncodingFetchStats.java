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

import static com.android.adservices.service.stats.AdServicesLoggerUtil.FIELD_UNSET;

import com.android.adservices.service.stats.AdsRelevanceStatusUtils;

import com.google.auto.value.AutoValue;

/** Class for logging per download of the encoding stats. */
@AutoValue
public abstract class EncodingFetchStats {
    /** Returns the time to download the js. */
    @AdsRelevanceStatusUtils.Size
    public abstract int getJsDownloadTime();

    /** Returns http response code. */
    public abstract int getHttpResponseCode();

    /** Returns the status of encoding fetch. */
    @AdsRelevanceStatusUtils.EncodingFetchStatus
    public abstract int getFetchStatus();

    /** Returns AdTech's eTLD+1 when the EncodingFetchStatus is not success. */
    public abstract String getAdTechId();

    /** Returns generic builder */
    public static Builder builder() {
        return new AutoValue_EncodingFetchStats.Builder().setHttpResponseCode(FIELD_UNSET);
    }

    /** Builder class for EncodingFetchStats. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setJsDownloadTime(@AdsRelevanceStatusUtils.Size int value);

        public abstract Builder setHttpResponseCode(int value);

        public abstract Builder setFetchStatus(
                @AdsRelevanceStatusUtils.EncodingFetchStatus int value);

        public abstract Builder setAdTechId(String value);

        public abstract EncodingFetchStats build();
    }
}
