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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_DOWNLOAD_LATENCY_BUCKETS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.computeSize;

import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdsRelevanceStatusUtils;
import com.android.adservices.service.stats.FetchProcessLogger;
import com.android.adservices.shared.util.Clock;

public class EncodingJsFetchProcessLoggerImpl implements FetchProcessLogger {

    private final AdServicesLogger mAdServicesLogger;

    private final Clock mClock;

    private EncodingFetchStats.Builder mBuilder;

    private long mJsDownloadStartTimestamp;

    /** Constructs a {@link EncodingJsFetchProcessLoggerImpl} instance. */
    public EncodingJsFetchProcessLoggerImpl(
            AdServicesLogger adServicesLogger, Clock clock, EncodingFetchStats.Builder builder) {
        mAdServicesLogger = adServicesLogger;
        mClock = clock;
        mBuilder = builder;
    }

    @Override
    public void logEncodingJsFetchStats(
            @AdsRelevanceStatusUtils.EncodingFetchStatus int jsFetchStatus) {
        @AdsRelevanceStatusUtils.Size int size;
        if (mJsDownloadStartTimestamp == 0) {
            size = SIZE_UNSET;
        } else {
            size =
                    computeSize(
                            mClock.currentTimeMillis() - mJsDownloadStartTimestamp,
                            JS_DOWNLOAD_LATENCY_BUCKETS);
        }
        mBuilder.setJsDownloadTime(size);
        mBuilder.setFetchStatus(jsFetchStatus);
        mAdServicesLogger.logEncodingJsFetchStats(mBuilder.build());
    }

    @Override
    public void setAdTechId(String adTechId) {
        mBuilder.setAdTechId(adTechId);
    }

    @Override
    public void setJsDownloadStartTimestamp(long jsDownloadStartTimestamp) {
        mJsDownloadStartTimestamp = jsDownloadStartTimestamp;
    }
}
