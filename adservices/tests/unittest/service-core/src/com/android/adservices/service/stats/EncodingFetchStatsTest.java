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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.ENCODING_FETCH_STATUS_SUCCESS;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.service.stats.pas.EncodingFetchStats;

import org.junit.Test;

public class EncodingFetchStatsTest extends AdServicesUnitTestCase {
    private static final int JS_DOWNLOAD_TIME = 50;
    private static final int HTTP_RESPONSE_CODE = 404;
    private static final int FETCH_STATUS = ENCODING_FETCH_STATUS_SUCCESS;
    private static final String AD_TECH_ID = "com.google.android";

    @Test
    public void testBuildEncodingJsFetchStats() {
        EncodingFetchStats stats =
                EncodingFetchStats.builder()
                        .setJsDownloadTime(JS_DOWNLOAD_TIME)
                        .setHttpResponseCode(HTTP_RESPONSE_CODE)
                        .setFetchStatus(FETCH_STATUS)
                        .setAdTechId(AD_TECH_ID)
                        .build();

        expect.that(stats.getJsDownloadTime()).isEqualTo(JS_DOWNLOAD_TIME);
        expect.that(stats.getHttpResponseCode()).isEqualTo(HTTP_RESPONSE_CODE);
        expect.that(stats.getFetchStatus()).isEqualTo(FETCH_STATUS);
        expect.that(stats.getAdTechId()).isEqualTo(AD_TECH_ID);
    }
}
