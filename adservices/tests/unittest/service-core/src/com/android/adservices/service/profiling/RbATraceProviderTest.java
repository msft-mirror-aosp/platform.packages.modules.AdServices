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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.FlagsFactory;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.After;
import org.junit.Test;
import org.mockito.Mock;

@SpyStatic(FlagsFactory.class)
public final class RbATraceProviderTest extends AdServicesExtendedMockitoTestCase {
    private static String sFeatureName = RbATraceProvider.FeatureNames.TOPICS_API;
    private static String sMetricName = "metricName";
    private static String sClassName = "ClassName";
    private static String sMethodName = "methodName";
    private static int sCookie = 777;

    @Mock private RbATrace mTrace;

    @After
    public void tearDown() {
        RbATraceProvider.setTrace(null);
    }

    @Test
    public void testCreateTrace_enable_returnsRbATrace() {
        when(mMockFlags.getEnableRbAtrace()).thenReturn(true);
        assertThat(RbATraceProvider.getTrace(mMockFlags)).isInstanceOf(RbATrace.class);
    }

    @Test
    public void testCreateTrace_disable_returnsNoOpRbATrace() {
        when(mMockFlags.getEnableRbAtrace()).thenReturn(false);
        assertThat(RbATraceProvider.getTrace(mMockFlags)).isInstanceOf(NoOpRbATrace.class);
    }

    @Test
    public void testValidFeatureName() {
        assertThat(RbATraceProvider.FeatureNames.isValidFeatureName("MeasurementApi")).isTrue();
    }

    @Test
    public void testInvalidFeatureName() {
        assertThat(RbATraceProvider.FeatureNames.isValidFeatureName("MyApi")).isFalse();
    }

    @Test
    public void testBeginSection_metricName() {
        RbATraceProvider.setTrace(mTrace);
        RbATraceProvider.beginSection(sFeatureName, sMetricName, mMockFlags);
        verify(mTrace).beginSection(sFeatureName, sMetricName);
    }

    @Test
    public void testBeginSection_classNameMethodName() {
        RbATraceProvider.setTrace(mTrace);
        RbATraceProvider.beginSection(sFeatureName, sClassName, sMethodName, mMockFlags);
        verify(mTrace).beginSection(sFeatureName, sClassName, sMethodName);
    }

    @Test
    public void testBeginAsyncSection_metricName() {
        RbATraceProvider.setTrace(mTrace);
        RbATraceProvider.beginAsyncSection(sFeatureName, sClassName, sMethodName, mMockFlags);
        verify(mTrace).beginAsyncSection(sFeatureName, sClassName, sMethodName);
    }

    @Test
    public void testBeginAsyncSection_classNameMethodName() {
        RbATraceProvider.setTrace(mTrace);
        RbATraceProvider.beginAsyncSection(sFeatureName, sClassName, sMethodName, mMockFlags);
        verify(mTrace).beginAsyncSection(sFeatureName, sClassName, sMethodName);
    }

    @Test
    public void testEndSection() {
        RbATraceProvider.setTrace(mTrace);
        RbATraceProvider.endSection(mMockFlags);
        verify(mTrace).endSection();
    }

    @Test
    public void testEndAsyncSection_metricName() {
        RbATraceProvider.setTrace(mTrace);
        RbATraceProvider.endAsyncSection(sFeatureName, sMetricName, sCookie, mMockFlags);
        verify(mTrace).endAsyncSection(sFeatureName, sMetricName, sCookie);
    }

    @Test
    public void testAsyncEndSection_classNameMethodName() {
        RbATraceProvider.setTrace(mTrace);
        RbATraceProvider.endAsyncSection(
                sFeatureName, sClassName, sMethodName, sCookie, mMockFlags);
        verify(mTrace).endAsyncSection(sFeatureName, sClassName, sMethodName, sCookie);
    }
}
