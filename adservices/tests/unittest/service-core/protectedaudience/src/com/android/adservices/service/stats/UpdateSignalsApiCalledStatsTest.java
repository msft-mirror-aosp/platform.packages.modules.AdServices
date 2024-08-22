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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JSON_PROCESSING_STATUS_SUCCESS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_MEDIUM;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.service.stats.pas.UpdateSignalsApiCalledStats;

import org.junit.Test;

public class UpdateSignalsApiCalledStatsTest extends AdServicesUnitTestCase {
    private static final int HTTP_RESPONSE_CODE = 400;
    private static final int JSON_SIZE = SIZE_MEDIUM;
    private static final int JSON_PROCESSING_STATUS = JSON_PROCESSING_STATUS_SUCCESS;
    private static final int PACKAGE_UID = 1234567890;
    private static final String AD_TECH_ID = "com.google.android";

    @Test
    public void testBuildUpdateSignalsApiCalledStats() {
        UpdateSignalsApiCalledStats stats =
                UpdateSignalsApiCalledStats.builder()
                        .setHttpResponseCode(HTTP_RESPONSE_CODE)
                        .setJsonSize(JSON_SIZE)
                        .setJsonProcessingStatus(JSON_PROCESSING_STATUS)
                        .setPackageUid(PACKAGE_UID)
                        .setAdTechId(AD_TECH_ID)
                        .build();

        expect.that(stats.getHttpResponseCode()).isEqualTo(HTTP_RESPONSE_CODE);
        expect.that(stats.getJsonSize()).isEqualTo(JSON_SIZE);
        expect.that(stats.getJsonProcessingStatus()).isEqualTo(JSON_PROCESSING_STATUS);
        expect.that(stats.getPackageUid()).isEqualTo(PACKAGE_UID);
        expect.that(stats.getAdTechId()).isEqualTo(AD_TECH_ID);
    }
}
