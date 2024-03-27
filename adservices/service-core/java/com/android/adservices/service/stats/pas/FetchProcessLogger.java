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

public interface FetchProcessLogger {
    /** Invokes the logger to log {@link EncodingFetchStats}. */
    void logEncodingJsFetchStats(@AdsRelevanceStatusUtils.EncodingFetchStatus int jsFetchStatus);

    /** Sets the AdTech's eTLD+1 ID. */
    void setAdTechId(String adTechId);

    /** Sets the timestamp to start download the js. */
    void setJsDownloadStartTimestamp(long jsDownloadStartTimestamp);
}
