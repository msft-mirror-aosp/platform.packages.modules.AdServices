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

interface RbATrace {
    /**
     * Writes a trace message for the {@code metricName} to indicate that a given section of code
     * has begun. The trace name will be concatenated from the {@code featureName} and the {@code
     * metricName}.
     *
     * @param featureName Use the {@code FeatureNames} to specify the feature name.
     * @param metricName The metric name to appear in the trace.
     */
    void beginSection(String featureName, String metricName);

    /**
     * Writes a trace message for the {@code className} {@code methodName} to indicate that a given
     * section of code has begun. The trace name will be concatenated from the {@code featureName},
     * {@code className} and the {@code methodName}.
     *
     * @param featureName Use the {@code FeatureNames} to specify the feature name.
     * @param className The class name to appear in the trace.
     * @param methodName The method name to appear in the trace.
     */
    void beginSection(String featureName, String className, String methodName);

    /** Writes a trace message to indicate that a given section of code has ended. */
    void endSection();
}
