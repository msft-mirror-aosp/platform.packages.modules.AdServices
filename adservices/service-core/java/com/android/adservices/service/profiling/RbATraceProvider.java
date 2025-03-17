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

import com.android.adservices.service.Flags;
import com.android.adservices.service.common.BinderFlagReader;
import com.android.adservices.shared.util.Trace;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An abstraction layer used to collect all traces following the same naming convention.
 *
 * @hide
 */
public class RbATraceProvider {
    private static RbATrace sTrace;
    private static final Object sLock = new Object();

    @VisibleForTesting
    static RbATrace getTrace(Flags flags) {
        if (sTrace != null) {
            return sTrace;
        }

        synchronized (sLock) {
            if (sTrace == null) {
                if (BinderFlagReader.readFlag(flags::getEnableRbAtrace)) {
                    sTrace = new RbATraceImpl(new Trace(), new AtomicInteger());
                } else {
                    sTrace = new NoOpRbATrace();
                }
            }

            return sTrace;
        }
    }

    /** Should only be used in tests. */
    @VisibleForTesting
    public static void setTrace(RbATrace trace) {
        sTrace = trace;
    }

    /**
     * Writes a trace message for the {@code metricName} to indicate that a given section of code
     * has begun. The trace name will be concatenated from the {@code featureName} and the {@code
     * metricName}.
     *
     * @param featureName Use the {@code FeatureNames} to specify the feature name.
     * @param metricName The metric name to appear in the trace.
     * @param flags for accessing feature flags
     */
    public static void beginSection(String featureName, String metricName, Flags flags) {
        getTrace(flags).beginSection(featureName, metricName);
    }

    /**
     * Writes a trace message for the {@code className} {@code methodName} to indicate that a given
     * section of code has begun. The trace name will be concatenated from the {@code featureName},
     * {@code className} and the {@code methodName}.
     *
     * @param featureName Use the {@code FeatureNames} to specify the feature name.
     * @param className The class name to appear in the trace.
     * @param methodName The method name to appear in the trace.
     * @param flags for accessing feature flags
     */
    public static void beginSection(
            String featureName, String className, String methodName, Flags flags) {
        getTrace(flags).beginSection(featureName, className, methodName);
    }

    /**
     * Writes a trace message for the {@code metricName} to indicate that a given section of code
     * has begun. The trace name will be concatenated from the {@code featureName} and the {@code
     * metricName}. Must be followed by a call to {@code endAsyncSection} with the same {@code
     * featureName}, {@code metricName} and provided {@code cookie}. Asynchronous events do not need
     * to be nested.
     *
     * @param featureName Use the {@code FeatureNames} to specify the feature name.
     * @param metricName The metric name to appear in the trace.
     * @param flags for accessing feature flags
     * @return unique cookie for identifying trace.
     */
    public static int beginAsyncSection(String featureName, String metricName, Flags flags) {
        return getTrace(flags).beginAsyncSection(featureName, metricName);
    }

    /**
     * Writes a trace message for the {@code className} {@code methodName} to indicate that a given
     * section of code has begun. The trace name will be concatenated from the {@code featureName},
     * {@code className} and {@code methodName}. Must be followed by a call to {@code
     * endAsyncSection} with the same {@code featureName}, {@code className}, {@code methodName} and
     * provided {@code cookie}. Asynchronous events do not need to be nested.
     *
     * @param className The class name to appear in the trace.
     * @param methodName The method name to appear in the trace.
     * @param flags for accessing feature flags
     * @return unique cookie for identifying trace.
     */
    public static int beginAsyncSection(
            String featureName, String className, String methodName, Flags flags) {
        return getTrace(flags).beginAsyncSection(featureName, className, methodName);
    }

    /**
     * Writes a trace message to indicate that a given section of code has ended.
     *
     * @param flags for accessing feature flags
     */
    public static void endSection(Flags flags) {
        getTrace(flags).endSection();
    }

    /**
     * Writes a trace message to indicate that a given section of code has ended. Must be called
     * exactly once for each call to {@code beginAsyncSection(java.lang.String, java.lang.String)}
     * using the same parameters and provided {@code cookie}.
     *
     * @param featureName Use the {@code FeatureNames} to specify the feature name.
     * @param metricName The metric name to appear in the trace.
     * @param cookie a unique cookie for identifying trace.
     * @param flags for accessing feature flags
     */
    public static void endAsyncSection(
            String featureName, String metricName, int cookie, Flags flags) {
        getTrace(flags).endAsyncSection(featureName, metricName, cookie);
    }

    /**
     * Writes a trace message to indicate that a given section of code has ended. Must be called
     * exactly once for each call to {@code beginAsyncSection(java.lang.String, java.lang.String,
     * java.lang.String)} using the same parameters and provided {@code cookie}.
     *
     * @param featureName Use the {@code FeatureNames} to specify the feature name.
     * @param className The class name to appear in the trace.
     * @param methodName The method name to appear in the trace.
     * @param cookie a unique cookie for identifying trace.
     * @param flags for accessing feature flags
     */
    public static void endAsyncSection(
            String featureName, String className, String methodName, int cookie, Flags flags) {
        getTrace(flags).endAsyncSection(featureName, className, methodName, cookie);
    }

    /** Feature name to group metrics from the same project together. */
    public static final class FeatureNames {
        public static final String MEASUREMENT_API = "MeasurementApi";
        public static final String TOPICS_API = "TopicsApi";
        public static final String AD_ID_API = "AdIdApi";
        public static final String CONSENT_MANAGER = "ConsentManager";

        private static final Set<String> VALID_FEATURE_NAMES =
                Set.of(MEASUREMENT_API, TOPICS_API, AD_ID_API, CONSENT_MANAGER);

        private FeatureNames() {}

        static boolean isValidFeatureName(String featureName) {
            return VALID_FEATURE_NAMES.contains(featureName);
        }
    }
}
