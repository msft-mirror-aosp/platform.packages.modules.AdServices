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

import com.android.adservices.service.FlagsFactory;

import java.util.Set;

/**
 * An abstraction layer used to collect all traces following the same naming convention.
 *
 * @hide
 */
public class RbATraceProvider {
    private static RbATrace sTrace = createTrace();

    private static RbATrace createTrace() {
        if (FlagsFactory.getFlags().getEnableRbAtrace()) {
            return new RbATraceImpl();
        }

        return new NoOpRbATrace();
    }

    /**
     * Writes a trace message for the {@code metricName} to indicate that a given section of code
     * has begun. The trace name will be concatenated from the {@code featureName} and the {@code
     * metricName}.
     *
     * @param featureName Use the {@code FeatureNames} to specify the feature name.
     * @param metricName The metric name to appear in the trace.
     */
    public static void beginSection(String featureName, String metricName) {
        sTrace.beginSection(featureName, metricName);
    }

    /**
     * Writes a trace message for the {@code className} {@code methodName} to indicate that a given
     * section of code has begun. The trace name will be concatenated from the {@code featureName},
     * {@code className} and the {@code methodName}.
     *
     * @param featureName Use the {@code FeatureNames} to specify the feature name.
     * @param className The class name to appear in the trace.
     * @param methodName The method name to appear in the trace.
     */
    public static void beginSection(String featureName, String className, String methodName) {
        sTrace.beginSection(featureName, className, methodName);
    }

    /** Writes a trace message to indicate that a given section of code has ended. */
    public static void endSection() {
        sTrace.endSection();
    }

    /** Feature name to group metrics from the same project together. */
    public static final class FeatureNames {
        public static final String MEASUREMENT_API = "MeasurementApi";
        public static final String TOPICS_API = "TopicsApi";
        public static final String AD_ID_API = "AdIdApi";

        private static final Set<String> VALID_FEATURE_NAMES =
                Set.of(MEASUREMENT_API, TOPICS_API, AD_ID_API);

        private FeatureNames() {}

        static boolean isValidFeatureName(String featureName) {
            return VALID_FEATURE_NAMES.contains(featureName);
        }
    }
}
