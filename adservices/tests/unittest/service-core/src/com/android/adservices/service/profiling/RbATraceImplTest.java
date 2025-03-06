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

package com.android.adservices.service.profiling;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.shared.util.Trace;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.atomic.AtomicInteger;

public final class RbATraceImplTest extends AdServicesMockitoTestCase {
    private static String sFeatureName = RbATraceProvider.FeatureNames.TOPICS_API;
    private static String sMetricName = "metricName";
    private static String sClassName = "ClassName";
    private static String sMethodName = "methodName";
    private static int sCookie = 777;

    @Mock private Trace mMockTrace;
    @Mock private AtomicInteger mCookieGenerator;
    private RbATraceImpl mRbATrace;

    @Before
    public void setup() {
        mRbATrace = new RbATraceImpl(mMockTrace, mCookieGenerator);
        when(mMockTrace.isEnabled()).thenReturn(true);
        when(mCookieGenerator.getAndIncrement()).thenReturn(sCookie);
    }

    @Test
    public void testBeginSection_metricName() {
        mRbATrace.beginSection(sFeatureName, sMetricName);

        verify(mMockTrace).beginSection(sFeatureName + "_" + sMetricName);
    }

    @Test
    public void testBeginSection_metricName_tracingDisabled() {
        when(mMockTrace.isEnabled()).thenReturn(false);

        mRbATrace.beginSection(sFeatureName, sMetricName);

        verify(mMockTrace, never()).beginSection(any());
    }

    @Test
    public void testBeginSection_classNameMethodName() {
        mRbATrace.beginSection(sFeatureName, sClassName, sMethodName);

        verify(mMockTrace).beginSection(sFeatureName + "_" + sClassName + "#" + sMethodName);
    }

    @Test
    public void testBeginSection_classNameMethodName_tracingDisabled() {
        when(mMockTrace.isEnabled()).thenReturn(false);

        mRbATrace.beginSection(sFeatureName, sClassName, sMethodName);

        verify(mMockTrace, never()).beginSection(any());
    }

    @Test
    public void testBeginAsyncSection_metricName() {
        assertThat(mRbATrace.beginAsyncSection(sFeatureName, sMetricName)).isEqualTo(sCookie);

        verify(mMockTrace).beginAsyncSection(sFeatureName + "_" + sMetricName, sCookie);

        verify(mCookieGenerator, times(1)).getAndIncrement();
    }

    @Test
    public void testBeginAsyncSection_metricName_tracingDisabled() {
        when(mMockTrace.isEnabled()).thenReturn(false);

        assertThat(mRbATrace.beginAsyncSection(sFeatureName, sMetricName)).isEqualTo(-1);

        verify(mMockTrace, never()).beginSection(any());
    }

    @Test
    public void testBeginAsyncSection_classNameMethodName() {
        assertThat(mRbATrace.beginAsyncSection(sFeatureName, sClassName, sMethodName))
                .isEqualTo(sCookie);

        verify(mMockTrace)
                .beginAsyncSection(sFeatureName + "_" + sClassName + "#" + sMethodName, sCookie);

        verify(mCookieGenerator, times(1)).getAndIncrement();
    }

    @Test
    public void testBeginAsyncSection_classNameMethodName_tracingDisabled() {
        when(mMockTrace.isEnabled()).thenReturn(false);

        assertThat(mRbATrace.beginAsyncSection(sFeatureName, sClassName, sMethodName))
                .isEqualTo(-1);

        verify(mMockTrace, never()).beginSection(any());
    }

    @Test
    public void endSection() {
        mRbATrace.endSection();

        verify(mMockTrace).endSection();
    }

    @Test
    public void endAsyncSection_metricName() {
        mRbATrace.endAsyncSection(sFeatureName, sMetricName, sCookie);

        verify(mMockTrace).endAsyncSection(sFeatureName + "_" + sMetricName, sCookie);
    }

    @Test
    public void endSection_classNameMethodName() {
        mRbATrace.endAsyncSection(sFeatureName, sClassName, sMethodName, sCookie);

        verify(mMockTrace)
                .endAsyncSection(sFeatureName + "_" + sClassName + "#" + sMethodName, sCookie);
    }

    @Test
    public void testCreateMetricName_featureNameAndMetricName() {
        assertThat(RbATraceImpl.createMetricName("A", "B")).isEqualTo("A_B");
    }

    @Test
    public void testCreateMetricName_featureNameClassNameAndMethodName() {
        assertThat(RbATraceImpl.createMetricName("A", "B", "C")).isEqualTo("A_B#C");
    }
}
