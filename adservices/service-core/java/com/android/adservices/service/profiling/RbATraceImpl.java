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

import com.android.adservices.LogUtil;
import com.android.adservices.shared.util.Trace;
import com.android.internal.annotations.VisibleForTesting;

import java.util.concurrent.atomic.AtomicInteger;

final class RbATraceImpl implements RbATrace {
    private Trace mTrace;
    private AtomicInteger mCookieGenerator;

    RbATraceImpl(Trace trace, AtomicInteger cookieGenerator) {
        mTrace = trace;
        mCookieGenerator = cookieGenerator;
    }

    @Override
    public void beginSection(String featureName, String metricName) {
        if (!mTrace.isEnabled()) {
            return;
        }
        if (!RbATraceProvider.FeatureNames.isValidFeatureName(featureName)) {
            LogUtil.e("Attempt to add a Trace slice to the unknown feature name: " + featureName);

            // Still need to begin the trace for consistency with the following
            // {@code endSection} and to track it anyway.
        }

        mTrace.beginSection(createMetricName(featureName, metricName));
    }

    @Override
    public void beginSection(String featureName, String className, String methodName) {
        if (!mTrace.isEnabled()) {
            return;
        }
        if (!RbATraceProvider.FeatureNames.isValidFeatureName(featureName)) {
            LogUtil.e("Attempt to add a Trace slice to the unknown feature name: " + featureName);

            // Still need to begin the trace for consistency with the following
            // {@code endSection} and to track it anyway.
        }

        mTrace.beginSection(createMetricName(featureName, className, methodName));
    }

    @Override
    public int beginAsyncSection(String featureName, String metricName) {
        if (!mTrace.isEnabled()) {
            return -1;
        }

        if (!RbATraceProvider.FeatureNames.isValidFeatureName(featureName)) {
            LogUtil.e("Attempt to add a Trace slice to the unknown feature name: " + featureName);

            // Still need to begin the trace for consistency with the following
            // {@code endAsyncSection} and to track it anyway.
        }

        int traceCookie = mCookieGenerator.getAndIncrement();

        mTrace.beginAsyncSection(createMetricName(featureName, metricName), traceCookie);
        return traceCookie;
    }

    @Override
    public int beginAsyncSection(String featureName, String className, String methodName) {
        if (!mTrace.isEnabled()) {
            return -1;
        }

        if (!RbATraceProvider.FeatureNames.isValidFeatureName(featureName)) {
            LogUtil.e("Attempt to add a Trace slice to the unknown feature name: " + featureName);

            // Still need to begin the trace for consistency with the following
            // {@code endAsyncSection} and to track it anyway.
        }

        int traceCookie = mCookieGenerator.getAndIncrement();

        mTrace.beginAsyncSection(createMetricName(featureName, className, methodName), traceCookie);
        return traceCookie;
    }

    @Override
    public void endSection() {
        mTrace.endSection();
    }

    @Override
    public void endAsyncSection(String featureName, String metricName, int cookie) {
        mTrace.endAsyncSection(createMetricName(featureName, metricName), cookie);
    }

    @Override
    public void endAsyncSection(
            String featureName, String className, String methodName, int cookie) {
        mTrace.endAsyncSection(createMetricName(featureName, className, methodName), cookie);
    }

    @VisibleForTesting
    static String createMetricName(String featureName, String metricName) {
        return featureName + "_" + metricName;
    }

    @VisibleForTesting
    static String createMetricName(String featureName, String className, String methodName) {
        return featureName + "_" + className + "#" + methodName;
    }
}
