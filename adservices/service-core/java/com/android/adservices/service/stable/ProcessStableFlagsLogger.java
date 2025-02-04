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

package com.android.adservices.service.stable;

import static com.android.adservices.service.CommonFlagsConstants.DEFAULT_ENABLE_PROCESS_STABLE_FLAGS_LOGGING;
import static com.android.adservices.service.CommonFlagsConstants.KEY_ENABLE_PROCESS_STABLE_FLAGS_LOGGING;
import static com.android.adservices.service.CommonFlagsConstants.NAMESPACE_ADSERVICES;

import android.provider.DeviceConfig;
import android.provider.DeviceConfig.Properties;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * The logger logs metrics for the lifecycle of AdServices process and process stable flags'
 * framework.
 */
public final class ProcessStableFlagsLogger {
    private static final String DEFAULT_STRING_FLAG_VALUE = "defVal";

    private final ProcessStableFlagsStatsdLogger mStatsdLogger;
    private final ExecutorService mExecutor;

    /** Constructor for {@link ProcessStableFlagsLogger}. */
    @VisibleForTesting
    public ProcessStableFlagsLogger(
            ProcessStableFlagsStatsdLogger statsdLogger, ExecutorService executor) {
        mStatsdLogger = Objects.requireNonNull(statsdLogger);
        mExecutor = Objects.requireNonNull(executor);
    }

    /** Constructor for {@link ProcessStableFlagsLogger}. */
    public ProcessStableFlagsLogger() {
        this(new ProcessStableFlagsStatsdLogger(), AdServicesExecutors.getBackgroundExecutor());
    }

    /** Logs the occurrence of AdServices Process restarting. */
    public void logAdServicesProcessRestartEvent() {
        if (!isProcessStableFlagsLoggingEnabled()) {
            return;
        }

        mExecutor.execute(mStatsdLogger::logAdServicesProcessRestart);
    }

    /**
     * Logs the latency in milliseconds when reading all process stable flags from Device Config.
     *
     * @param latencyMs the latency in milliseconds.
     */
    void logBatchReadFromDeviceConfigLatencyMs(long latencyMs) {
        if (!isProcessStableFlagsLoggingEnabled()) {
            return;
        }

        mExecutor.execute(() -> mStatsdLogger.logBatchReadFromDeviceConfigLatencyMs(latencyMs));
    }

    /**
     * Logs the occurrence when the memory level of AdServices Process drops to a certain threshold.
     */
    void logAdServicesProcessLowMemoryLevel() {
        if (!isProcessStableFlagsLoggingEnabled()) {
            return;
        }

        mExecutor.execute(mStatsdLogger::logAdServicesProcessLowMemoryLevel);
    }

    /**
     * Logs the metrics when AdServices receives a flag update event from the server.
     *
     * @param cachedProperties the flags cached in the process stable flags' framework.
     * @param changedProperties the flags received in the update event.
     */
    void logAdServicesFlagsUpdateEvent(Properties cachedProperties, Properties changedProperties) {
        if (!isProcessStableFlagsLoggingEnabled()) {
            return;
        }

        mExecutor.execute(
                () -> {
                    Set<String> changedFlagNameSet = changedProperties.getKeyset();
                    Set<String> cachedFlagNameSet = cachedProperties.getKeyset();

                    // Log the number of flags that are changed and different as their values in the
                    // cache.
                    int numOfCacheMissFlags = 0;
                    for (String flagName : changedFlagNameSet) {
                        if (!cachedFlagNameSet.contains(flagName)) {
                            numOfCacheMissFlags++;
                        } else {
                            // Compare the string value of the flag, despite the type of its actual
                            // usage.
                            String changedValue =
                                    changedProperties.getString(
                                            flagName, DEFAULT_STRING_FLAG_VALUE);
                            String cachedValue =
                                    cachedProperties.getString(flagName, DEFAULT_STRING_FLAG_VALUE);
                            if (!changedValue.equals(cachedValue)) {
                                numOfCacheMissFlags++;
                            }
                        }
                    }

                    LogUtil.d(
                            "The number of flags updated to a different value as the process stable"
                                    + " flag cache: %d",
                            numOfCacheMissFlags);
                    mStatsdLogger.logAdServicesFlagsUpdateEvent(numOfCacheMissFlags);
                });
    }

    // Base flag framework, may get refactored later to use the DeviceConfigFlagsHelper.
    @SuppressWarnings("AvoidDeviceConfigUsage")
    @VisibleForTesting
    boolean isProcessStableFlagsLoggingEnabled() {
        return DeviceConfig.getBoolean(
                NAMESPACE_ADSERVICES,
                KEY_ENABLE_PROCESS_STABLE_FLAGS_LOGGING,
                DEFAULT_ENABLE_PROCESS_STABLE_FLAGS_LOGGING);
    }
}
