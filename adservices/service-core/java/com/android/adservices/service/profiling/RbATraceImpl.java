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

package com.android.adservices.service.profiling;

import android.os.Trace;

import com.android.adservices.LogUtil;

final class RbATraceImpl implements RbATrace {
    RbATraceImpl() {}

    @Override
    public void beginSection(String featureName, String metricName) {
        if (!Trace.isEnabled()) {
            return;
        }
        if (!RbATraceProvider.FeatureNames.isValidFeatureName(featureName)) {
            LogUtil.e("Attempt to add a Trace slice to the unknown feature name: " + featureName);

            // Still need to begin the trace for consistency with the following
            // {@code endSection} and to track it anyway.
        }

        Trace.beginSection(createMetricName(featureName, metricName));
    }

    @Override
    public void beginSection(String featureName, String className, String methodName) {
        if (!Trace.isEnabled()) {
            return;
        }
        if (!RbATraceProvider.FeatureNames.isValidFeatureName(featureName)) {
            LogUtil.e("Attempt to add a Trace slice to the unknown feature name: " + featureName);

            // Still need to begin the trace for consistency with the following
            // {@code endSection} and to track it anyway.
        }

        Trace.beginSection(createMetricName(featureName, className, methodName));
    }

    @Override
    public void endSection() {
        Trace.endSection();
    }

    private static String createMetricName(String featureName, String metricName) {
        return featureName + "_" + metricName;
    }

    private static String createMetricName(
            String featureName, String className, String methodName) {
        return featureName + "_" + className + "#" + methodName;
    }
}
