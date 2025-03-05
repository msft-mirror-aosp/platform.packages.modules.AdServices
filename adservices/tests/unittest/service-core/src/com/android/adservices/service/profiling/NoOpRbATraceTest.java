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

import org.junit.Test;

public final class NoOpRbATraceTest {
    private static NoOpRbATrace sTrace = new NoOpRbATrace();
    private static String sFeatureName = "FeatureName";
    private static String sMetricName = "metricName";
    private static String sClassName = "ClassName";
    private static String sMethodName = "methodName";

    @Test
    public void beginAsyncSection_metricName_returnsMinusOne() {
        assertThat(sTrace.beginAsyncSection(sFeatureName, sMetricName)).isEqualTo(-1);
    }

    @Test
    public void beginAsyncSection_classNameMethodName_returnsMinusOne() {
        assertThat(sTrace.beginAsyncSection(sFeatureName, sClassName, sMethodName)).isEqualTo(-1);
    }
}
