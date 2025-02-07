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

package com.android.adservices.metriclogger;

import static com.android.adservices.shared.metriclogger.AbstractMetricLogger.TAG;

import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.proto.LogSamplingConfig;
import com.android.adservices.shared.util.ProtoParser;

import com.google.common.base.Supplier;

/** Helper class to read and parse the config flag. */
public final class SamplingConfigFlagReader {

    private SamplingConfigFlagReader() {
        throw new UnsupportedOperationException("provides only static methods");
    }

    /**
     * Retrieves the sampling configuration from a flag. If the flag is not set or the configuration
     * cannot be parsed, returns the {@link LogSamplingConfig#getDefaultInstance()}.
     */
    public static LogSamplingConfig getSamplingConfigOrDefault(
            Supplier<String> encodedFlagSupplier, AdServicesErrorLogger errorLogger) {
        LogSamplingConfig config =
                ProtoParser.parseBase64EncodedStringToProto(
                        LogSamplingConfig.parser(), errorLogger, TAG, encodedFlagSupplier.get());
        return config == null ? LogSamplingConfig.getDefaultInstance() : config;
    }
}
