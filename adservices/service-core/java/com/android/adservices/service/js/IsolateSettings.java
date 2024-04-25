/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.js;

import androidx.javascriptengine.JavaScriptIsolate;

import com.android.internal.util.Preconditions;

import com.google.auto.value.AutoValue;

/** Class used to set startup parameters for {@link JavaScriptIsolate}. */
@AutoValue
public abstract class IsolateSettings {
    /**
     * Gets the max heap size used by the {@link JavaScriptIsolate}.
     *
     * <p>The default value is 0 which indicates no heap size limit.
     *
     * @return heap size in bytes
     */
    public abstract long getMaxHeapSizeBytes();

    /**
     * Gets the condition if the Max Heap feature is enforced for JS Isolate
     *
     * <p>The default value is false
     *
     * @return boolean value stating if the feature is enforced
     */
    public abstract boolean getEnforceMaxHeapSizeFeature();

    /**
     * Gets the condition if the console message in logs is enabled for JS Isolate.
     *
     * <p>The default value is false
     *
     * @return boolean value stating if the feature is enabled.
     */
    public abstract boolean getIsolateConsoleMessageInLogsEnabled();

    /** Creates setting for which memory restrictions are not enforced */
    public static IsolateSettings forMaxHeapSizeEnforcementDisabled(
            boolean isolateConsoleMessageInLogsEnabled) {
        return IsolateSettings.builder()
                .setEnforceMaxHeapSizeFeature(false)
                .setMaxHeapSizeBytes(0)
                .setIsolateConsoleMessageInLogsEnabled(isolateConsoleMessageInLogsEnabled)
                .build();
    }

    /**
     * @return {@link Builder} for {@link IsolateSettings}
     */
    public static IsolateSettings.Builder builder() {
        return new AutoValue_IsolateSettings.Builder();
    }

    /** Builder clsas for {@link IsolateSettings} */
    @AutoValue.Builder
    public abstract static class Builder {
        abstract Builder maxHeapSizeBytes(long maxHeapSizeBytes);

        abstract Builder enforceMaxHeapSizeFeature(boolean value);

        abstract Builder isolateConsoleMessageInLogsEnabled(boolean value);

        /** Sets the max heap size used by the {@link JavaScriptIsolate}. */
        public Builder setMaxHeapSizeBytes(long maxHeapSizeBytes) {
            Preconditions.checkArgument(maxHeapSizeBytes >= 0, "maxHeapSizeBytes should be >= 0");
            return maxHeapSizeBytes(maxHeapSizeBytes);
        }

        /** Sets the condition if the max heap size is enforced for JS Isolate. */
        public Builder setEnforceMaxHeapSizeFeature(boolean value) {
            return enforceMaxHeapSizeFeature(value);
        }

        /** Sets the condition if the console message in logs is enabled for JS Isolate. */
        public Builder setIsolateConsoleMessageInLogsEnabled(boolean value) {
            return isolateConsoleMessageInLogsEnabled(value);
        }

        /**
         * @return {@link IsolateSettings}
         */
        public abstract IsolateSettings build();
    }
}
