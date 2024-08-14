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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.service.stats.GetAdSelectionDataApiCalledStats;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class SellerConfigurationMetricsStrategyEnabledTest extends AdServicesMockitoTestCase {
    @Mock private GetAdSelectionDataApiCalledStats.Builder mBuilderMock;
    private SellerConfigurationMetricsStrategy mSellerConfigurationMetricsStrategy;

    @Before
    public void setup() {
        mSellerConfigurationMetricsStrategy = new SellerConfigurationMetricsStrategyEnabled();
    }

    @Test
    public void testSetSellerConfigurationMetricsCalls() {
        int inputLatencyMs = 3;
        int version = 1;
        int numReEstimations = 2;

        when(mBuilderMock.setPayloadOptimizationResult(any())).thenReturn(mBuilderMock);
        when(mBuilderMock.setInputGenerationLatencyMs(anyInt())).thenReturn(mBuilderMock);
        when(mBuilderMock.setCompressedBuyerInputCreatorVersion(anyInt())).thenReturn(mBuilderMock);
        when(mBuilderMock.setNumReEstimations(anyInt())).thenReturn(mBuilderMock);

        mSellerConfigurationMetricsStrategy.setSellerConfigurationMetrics(
                mBuilderMock,
                PAYLOAD_WITHIN_REQUESTED_MAX,
                inputLatencyMs,
                version,
                numReEstimations);

        verify(mBuilderMock).setPayloadOptimizationResult(PAYLOAD_WITHIN_REQUESTED_MAX);
        verify(mBuilderMock).setInputGenerationLatencyMs(inputLatencyMs);
        verify(mBuilderMock).setCompressedBuyerInputCreatorVersion(version);
        verify(mBuilderMock).setNumReEstimations(numReEstimations);
    }

    @Test
    public void testSetSellerMaxPayloadSizeKBCalls() {
        int sellerMaxPayloadSize = 5;
        mSellerConfigurationMetricsStrategy.setSellerMaxPayloadSizeKb(
                mBuilderMock, sellerMaxPayloadSize);
        verify(mBuilderMock).setSellerMaxSizeKb(sellerMaxPayloadSize);
    }

    @Test
    public void testSetInputGenerationLatencyMsAndCompressedBuyerInputCreatorVersion() {
        when(mBuilderMock.setInputGenerationLatencyMs(anyInt())).thenReturn(mBuilderMock);
        when(mBuilderMock.setCompressedBuyerInputCreatorVersion(anyInt())).thenReturn(mBuilderMock);

        int inputLatencyMs = 3;
        int buyerCreatorVersion = 1;
        mSellerConfigurationMetricsStrategy.setInputGenerationLatencyMsAndBuyerCreatorVersion(
                mBuilderMock, inputLatencyMs, buyerCreatorVersion);
        verify(mBuilderMock).setInputGenerationLatencyMs(inputLatencyMs);
        verify(mBuilderMock).setCompressedBuyerInputCreatorVersion(1);
    }
}
