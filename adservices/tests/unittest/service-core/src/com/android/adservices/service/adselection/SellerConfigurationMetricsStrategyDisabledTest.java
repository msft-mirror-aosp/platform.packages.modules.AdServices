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

package com.android.adservices.service.adselection;

import static com.android.adservices.service.stats.GetAdSelectionDataApiCalledStats.PayloadOptimizationResult.PAYLOAD_WITHIN_REQUESTED_MAX;

import static org.mockito.Mockito.verifyZeroInteractions;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.service.stats.GetAdSelectionDataApiCalledStats;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class SellerConfigurationMetricsStrategyDisabledTest extends AdServicesMockitoTestCase {
    @Mock private GetAdSelectionDataApiCalledStats.Builder mBuilderMock;
    private SellerConfigurationMetricsStrategy mSellerConfigurationMetricsStrategy;

    @Before
    public void setup() {
        mSellerConfigurationMetricsStrategy = new SellerConfigurationMetricsStrategyDisabled();
    }

    @Test
    public void testSetSellerConfigurationMetricsCalls() {
        mSellerConfigurationMetricsStrategy.setSellerConfigurationMetrics(
                mBuilderMock, PAYLOAD_WITHIN_REQUESTED_MAX, 3, 1, 2);
        verifyZeroInteractions(mBuilderMock);
    }

    @Test
    public void testSetSellerMaxPayloadSizeKBCalls() {
        mSellerConfigurationMetricsStrategy.setSellerMaxPayloadSizeKb(mBuilderMock, 5);
        verifyZeroInteractions(mBuilderMock);
    }

    @Test
    public void testSetInputGenerationLatencyMsAndCompressedBuyerInputCreatorVersion() {
        int inputLatencyMs = 3;
        mSellerConfigurationMetricsStrategy.setInputGenerationLatencyMsAndBuyerCreatorVersion(
                mBuilderMock, inputLatencyMs, CompressedBuyerInputCreatorNoOptimizations.VERSION);
        verifyZeroInteractions(mBuilderMock);
    }
}
