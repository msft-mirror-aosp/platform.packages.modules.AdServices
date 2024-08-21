/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.data.adselection;

import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.common.CommonFixture;
import android.net.Uri;

import com.android.adservices.data.adselection.datahandlers.ReportingData;

/** Test data for reporting functionality. */
public final class ReportingDataFixture {
    public static final String REPORTING_FRAGMENT = "/reporting";
    public static final Uri SELLER_REPORTING_URI_1 =
            CommonFixture.getUri(AdSelectionConfigFixture.SELLER, REPORTING_FRAGMENT);
    public static final Uri BUYER_REPORTING_URI_1 =
            CommonFixture.getUri(AdSelectionConfigFixture.BUYER, REPORTING_FRAGMENT);
    public static final ReportingData REPORTING_DATA_WITHOUT_COMPUTATION_DATA =
            ReportingData.builder()
                    .setBuyerWinReportingUri(ReportingDataFixture.BUYER_REPORTING_URI_1)
                    .setSellerWinReportingUri(ReportingDataFixture.SELLER_REPORTING_URI_1)
                    .build();
}
