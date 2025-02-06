/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertWithMessage;

import org.junit.Test;

public class NumberOfTypesOfReportingUrlsReceivedTest {

    @Test
    public void testBuilderAndGetters() {
        NumberOfTypesOfReportingUrlsReceivedStats stats =
                NumberOfTypesOfReportingUrlsReceivedStats.builder()
                        .setNumberOfTopLevelSellerReportingUrl(1)
                        .setNumberOfBuyerReportingUrl(2)
                        .setNumberOfComponentSellerReportingUrl(3)
                        .setNumberOfComponentSellerEventReportingUrl(4)
                        .setNumberOfTopLevelSellerEventReportingUrl(5)
                        .setNumberOfBuyerEventReportingUrl(6)
                        .build();

        assertWithMessage("Number of top level seller reporting url")
                .that(stats.getNumberOfTopLevelSellerReportingUrl())
                .isEqualTo(1);
        assertWithMessage("Number of buyer reporting url")
                .that(stats.getNumberOfBuyerReportingUrl())
                .isEqualTo(2);
        assertWithMessage("Number of component seller reporting url")
                .that(stats.getNumberOfComponentSellerReportingUrl())
                .isEqualTo(3);
        assertWithMessage("Number of component event reporting url ")
                .that(stats.getNumberOfComponentSellerEventReportingUrl())
                .isEqualTo(4);
        assertWithMessage("Number of top level seller event reporting url")
                .that(stats.getNumberOfTopLevelSellerEventReportingUrl())
                .isEqualTo(5);
        assertWithMessage("Number of buyer event reporting url")
                .that(stats.getNumberOfBuyerEventReportingUrl())
                .isEqualTo(6);
    }
}
