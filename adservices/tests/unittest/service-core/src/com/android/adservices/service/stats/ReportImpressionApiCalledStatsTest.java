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

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public final class ReportImpressionApiCalledStatsTest extends AdServicesUnitTestCase {

    private static final boolean REPORT_WIN_BUYER_ADDITIONAL_SIGNALS_CONTAINED_AD_COST = true;
    private static final boolean REPORT_WIN_BUYER_ADDITIONAL_SIGNALS_CONTAINED_DATA_VERSION = false;
    private static final boolean REPORT_RESULT_SELLER_ADDITIONAL_SIGNALS_CONTAINED_DATA_VERSION =
            true;
    private static final int REPORT_WIN_JS_SCRIPT_RESULT_CODE = 1;
    private static final int REPORT_RESULT_JS_SCRIPT_RESULT_CODE = 2;

    @Test
    public void testBuildReportImpressionApiCalledStats() {
        ReportImpressionApiCalledStats stats =
                ReportImpressionApiCalledStats.builder()
                        .setReportWinBuyerAdditionalSignalsContainedAdCost(
                                REPORT_WIN_BUYER_ADDITIONAL_SIGNALS_CONTAINED_AD_COST)
                        .setReportWinBuyerAdditionalSignalsContainedDataVersion(
                                REPORT_WIN_BUYER_ADDITIONAL_SIGNALS_CONTAINED_DATA_VERSION)
                        .setReportResultSellerAdditionalSignalsContainedDataVersion(
                                REPORT_RESULT_SELLER_ADDITIONAL_SIGNALS_CONTAINED_DATA_VERSION)
                        .setReportWinJsScriptResultCode(REPORT_WIN_JS_SCRIPT_RESULT_CODE)
                        .setReportResultJsScriptResultCode(REPORT_RESULT_JS_SCRIPT_RESULT_CODE)
                        .build();

        expect.that(stats.getReportWinBuyerAdditionalSignalsContainedAdCost())
                .isEqualTo(REPORT_WIN_BUYER_ADDITIONAL_SIGNALS_CONTAINED_AD_COST);
        expect.that(stats.getReportWinBuyerAdditionalSignalsContainedDataVersion())
                .isEqualTo(REPORT_WIN_BUYER_ADDITIONAL_SIGNALS_CONTAINED_DATA_VERSION);
        expect.that(stats.getReportResultSellerAdditionalSignalsContainedDataVersion())
                .isEqualTo(REPORT_RESULT_SELLER_ADDITIONAL_SIGNALS_CONTAINED_DATA_VERSION);
        expect.that(stats.getReportWinJsScriptResultCode())
                .isEqualTo(REPORT_WIN_JS_SCRIPT_RESULT_CODE);
        expect.that(stats.getReportResultJsScriptResultCode())
                .isEqualTo(REPORT_RESULT_JS_SCRIPT_RESULT_CODE);
    }
}
