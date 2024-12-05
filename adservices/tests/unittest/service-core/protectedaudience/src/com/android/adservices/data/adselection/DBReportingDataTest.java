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

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.common.CommonFixture;
import android.net.Uri;

import org.junit.Test;

public class DBReportingDataTest {
    private static final long AD_SELECTION_ID_1 = 1L;
    private static final String REPORTING_FRAGMENT = "/reporting";
    private static final Uri BUYER_REPORTING_URI_1 =
            CommonFixture.getUri(AdSelectionConfigFixture.BUYER, REPORTING_FRAGMENT);
    private static final Uri SELLER_REPORTING_URI_1 =
            CommonFixture.getUri(AdSelectionConfigFixture.SELLER, REPORTING_FRAGMENT);
    private static final Uri COMPONENT_REPORTING_URI_1 =
            CommonFixture.getUri(AdSelectionConfigFixture.COMPONENT_SELLER_1, REPORTING_FRAGMENT);

    @Test
    public void testBuild_unsetAdSelectionId_throwsISE() {
        assertThrows(IllegalStateException.class, () -> DBReportingData.builder().build());
    }

    @Test
    public void testBuild_success() {
        DBReportingData reportingData =
                DBReportingData.builder()
                        .setAdSelectionId(AD_SELECTION_ID_1)
                        .setBuyerReportingUri(BUYER_REPORTING_URI_1)
                        .setSellerReportingUri(SELLER_REPORTING_URI_1)
                        .build();

        assertEquals(AD_SELECTION_ID_1, reportingData.getAdSelectionId());
        assertEquals(BUYER_REPORTING_URI_1, reportingData.getBuyerReportingUri());
        assertEquals(SELLER_REPORTING_URI_1, reportingData.getSellerReportingUri());
    }

    @Test
    public void testBuild_sellerEmptyUri_success() {
        DBReportingData reportingData =
                DBReportingData.builder()
                        .setAdSelectionId(AD_SELECTION_ID_1)
                        .setBuyerReportingUri(BUYER_REPORTING_URI_1)
                        .setSellerReportingUri(Uri.EMPTY)
                        .build();

        assertEquals(AD_SELECTION_ID_1, reportingData.getAdSelectionId());
        assertEquals(BUYER_REPORTING_URI_1, reportingData.getBuyerReportingUri());
        assertEquals(Uri.EMPTY, reportingData.getSellerReportingUri());
    }

    @Test
    public void testBuild_buyerEmptyUri_success() {
        DBReportingData reportingData =
                DBReportingData.builder()
                        .setAdSelectionId(AD_SELECTION_ID_1)
                        .setBuyerReportingUri(Uri.EMPTY)
                        .setSellerReportingUri(SELLER_REPORTING_URI_1)
                        .build();

        assertEquals(AD_SELECTION_ID_1, reportingData.getAdSelectionId());
        assertEquals(Uri.EMPTY, reportingData.getBuyerReportingUri());
        assertEquals(SELLER_REPORTING_URI_1, reportingData.getSellerReportingUri());
    }

    @Test
    public void testCreate_withComponentSeller_success() {
        DBReportingData reportingData =
                DBReportingData.create(
                        AD_SELECTION_ID_1,
                        SELLER_REPORTING_URI_1,
                        BUYER_REPORTING_URI_1,
                        COMPONENT_REPORTING_URI_1);

        assertWithMessage("Ad selection id")
                .that(reportingData.getAdSelectionId())
                .isEqualTo(AD_SELECTION_ID_1);
        assertWithMessage("Buyer reporting uri")
                .that(reportingData.getBuyerReportingUri())
                .isEqualTo(BUYER_REPORTING_URI_1);
        assertWithMessage("Seller reporting uri")
                .that(reportingData.getSellerReportingUri())
                .isEqualTo(SELLER_REPORTING_URI_1);
        assertWithMessage("Component reporting uri")
                .that(reportingData.getComponentSellerReportingUri())
                .isEqualTo(COMPONENT_REPORTING_URI_1);
    }

    @Test
    public void testBuild_withComponentSeller_success() {
        DBReportingData reportingData =
                DBReportingData.builder()
                        .setAdSelectionId(AD_SELECTION_ID_1)
                        .setBuyerReportingUri(BUYER_REPORTING_URI_1)
                        .setSellerReportingUri(SELLER_REPORTING_URI_1)
                        .setComponentSellerReportingUri(COMPONENT_REPORTING_URI_1)
                        .build();

        assertWithMessage("Ad selection id")
                .that(reportingData.getAdSelectionId())
                .isEqualTo(AD_SELECTION_ID_1);
        assertWithMessage("Buyer reporting uri")
                .that(reportingData.getBuyerReportingUri())
                .isEqualTo(BUYER_REPORTING_URI_1);
        assertWithMessage("Seller reporting uri")
                .that(reportingData.getSellerReportingUri())
                .isEqualTo(SELLER_REPORTING_URI_1);
        assertWithMessage("Component reporting uri")
                .that(reportingData.getComponentSellerReportingUri())
                .isEqualTo(COMPONENT_REPORTING_URI_1);
    }

    @Test
    public void testBuild_emptyComponentSeller_success() {
        DBReportingData reportingData =
                DBReportingData.builder()
                        .setAdSelectionId(AD_SELECTION_ID_1)
                        .setBuyerReportingUri(BUYER_REPORTING_URI_1)
                        .setSellerReportingUri(SELLER_REPORTING_URI_1)
                        .build();

        assertWithMessage("Ad selection id")
                .that(reportingData.getAdSelectionId())
                .isEqualTo(AD_SELECTION_ID_1);
        assertWithMessage("Buyer reporting uri")
                .that(reportingData.getBuyerReportingUri())
                .isEqualTo(BUYER_REPORTING_URI_1);
        assertWithMessage("Seller reporting uri")
                .that(reportingData.getSellerReportingUri())
                .isEqualTo(SELLER_REPORTING_URI_1);
        assertWithMessage("Component reporting uri")
                .that(reportingData.getComponentSellerReportingUri())
                .isNull();
    }
}
