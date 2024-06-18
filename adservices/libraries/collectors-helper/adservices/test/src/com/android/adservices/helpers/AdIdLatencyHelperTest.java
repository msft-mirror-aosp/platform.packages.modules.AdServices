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

package com.android.adservices.helpers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import com.android.helpers.LatencyHelper;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

/** Unit tests for {@link AdIdLatencyHelper}. */
public final class AdIdLatencyHelperTest {
    private static final String AD_ID_HOT_START_LATENCY_METRIC = "AD_ID_HOT_START_LATENCY_METRIC";
    private static final String AD_ID_COLD_START_LATENCY_METRIC = "AD_ID_COLD_START_LATENCY_METRIC";

    private static final String SAMPLE_AD_ID_HOT_START_LATENCY_OUTPUT =
            "06-13 18:09:24.058 20765 20781 D\n"
                    + " GetAdIdApiCall: (AD_ID_HOT_START_LATENCY_METRIC: 14)";
    private static final String SAMPLE_AD_ID_COLD_START_LATENCY_OUTPUT =
            "06-13 18:09:24.058 20765 20781 D\n"
                    + " GetAdIdApiCall: (AD_ID_COLD_START_LATENCY_METRIC: 200)";

    @Rule public final Expect expect = Expect.create();

    private LatencyHelper mAdIdLatencyHelper;

    @Mock private LatencyHelper.InputStreamFilter mInputStreamFilter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mAdIdLatencyHelper = AdIdLatencyHelper.getCollector(mInputStreamFilter);
    }

    // Test getting metrics for single package.
    @Test
    public void testGetMetrics() throws Exception {
        String outputString =
                SAMPLE_AD_ID_HOT_START_LATENCY_OUTPUT
                        + "\n"
                        + SAMPLE_AD_ID_COLD_START_LATENCY_OUTPUT;
        InputStream targetStream = new ByteArrayInputStream(outputString.getBytes());
        doReturn(targetStream).when(mInputStreamFilter).getStream(any(), any());
        Map<String, Long> adidLatencyMetrics = mAdIdLatencyHelper.getMetrics();

        expect.that(adidLatencyMetrics.get(AD_ID_HOT_START_LATENCY_METRIC)).isEqualTo(14);
        expect.that(adidLatencyMetrics.get(AD_ID_COLD_START_LATENCY_METRIC)).isEqualTo(200);
    }

    // Test getting no metrics for single package.
    @Test
    public void testEmptyLogcat_noMetrics() throws Exception {
        String outputString = "";
        InputStream targetStream = new ByteArrayInputStream(outputString.getBytes());
        doReturn(targetStream).when(mInputStreamFilter).getStream(any(), any());
        Map<String, Long> adidLatencyMetrics = mAdIdLatencyHelper.getMetrics();

        expect.that(adidLatencyMetrics.containsKey(AD_ID_COLD_START_LATENCY_METRIC)).isFalse();
        expect.that(adidLatencyMetrics.containsKey(AD_ID_HOT_START_LATENCY_METRIC)).isFalse();
    }
}
