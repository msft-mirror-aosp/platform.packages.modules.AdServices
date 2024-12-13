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

import static com.android.adservices.ResultCode.RESULT_OK;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__TYPE__EVENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MESUREMENT_REPORTS_UPLOADED;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

/** Unit test for {@link MeasurementReportsStats}. */
public final class MeasurementReportsStatsTest extends AdServicesUnitTestCase {

    @Test
    public void testBuilderCreateSucceed() {
        String appPackageName = "appPackageName";
        MeasurementReportsStats measurementReportsStats =
                new MeasurementReportsStats.Builder()
                        .setCode(AD_SERVICES_MESUREMENT_REPORTS_UPLOADED)
                        .setType(AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__TYPE__EVENT)
                        .setResultCode(RESULT_OK)
                        .setSourceRegistrant(appPackageName)
                        .build();
        expect.withMessage("code")
                .that(measurementReportsStats.getCode())
                .isEqualTo(AD_SERVICES_MESUREMENT_REPORTS_UPLOADED);
        expect.withMessage("type")
                .that(measurementReportsStats.getType())
                .isEqualTo(AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__TYPE__EVENT);
        expect.withMessage("result code")
                .that(measurementReportsStats.getResultCode())
                .isEqualTo(RESULT_OK);
        expect.withMessage("package name")
                .that(measurementReportsStats.getSourceRegistrant())
                .isEqualTo(appPackageName);
    }
}
