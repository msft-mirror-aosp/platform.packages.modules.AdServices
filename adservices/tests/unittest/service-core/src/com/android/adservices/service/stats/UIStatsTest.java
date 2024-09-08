/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_OUT_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

/** Unit test for {@link UIStats}. */
public final class UIStatsTest extends AdServicesUnitTestCase {

    @Test
    public void testBuilderCreateSuccess() {
        UIStats stats = new UIStats.Builder()
                .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                .setRegion(AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW)
                .setAction(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_OUT_SELECTED)
                .build();

        expect.that(stats.getCode()).isEqualTo(AD_SERVICES_SETTINGS_USAGE_REPORTED);
        expect.that(stats.getRegion()).isEqualTo(AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW);
        expect.that(stats.getAction())
                .isEqualTo(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_OUT_SELECTED);
    }
}
