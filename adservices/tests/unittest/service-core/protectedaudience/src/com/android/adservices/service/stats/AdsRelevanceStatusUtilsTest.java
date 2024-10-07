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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PERSIST_AD_SELECTION_RESULT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_VERY_LARGE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_VERY_SMALL;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.computeSize;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.getCelPpApiNameId;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AdsRelevanceStatusUtilsTest {
    @Test
    public void testComputeSize() {
        long[] buckets = {10, 20, 30, 40};
        int rawValue = 5;
        for (@AdsRelevanceStatusUtils.Size int i = SIZE_VERY_SMALL; i <= SIZE_VERY_LARGE; i++) {
            assertEquals(i, computeSize(rawValue, buckets));
            rawValue += 10;
        }
    }

    @Test
    public void testGetCelPpApiNameId() {
        assertEquals(
                getCelPpApiNameId(AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA),
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA);

        assertEquals(
                getCelPpApiNameId(AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT),
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PERSIST_AD_SELECTION_RESULT);

        assertEquals(
                getCelPpApiNameId(AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS),
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS);

        assertEquals(
                getCelPpApiNameId(AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE),
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED);

        assertEquals(
                getCelPpApiNameId(AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS),
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED);
    }
}
