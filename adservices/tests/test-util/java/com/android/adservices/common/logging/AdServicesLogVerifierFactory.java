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

package com.android.adservices.common.logging;

import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.shared.testing.LogVerifier;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Objects;

/** Factory class to centralize creation of {@link LogVerifier} objects. */
public final class AdServicesLogVerifierFactory {
    /** Supported log types. */
    enum LogType {
        /**
         * Verify ErrorLogUtil.e(Throwable, int, int) and ErrorLogUtil.e(int, int) calls. Use {@link
         * ExpectErrorLogUtilCall} annotation to denote expected logging calls.
         */
        ERROR_LOG_UTIL;
    }

    /**
     * Creates appropriate {@link LogVerifier} objects for a given {@link LogType}.
     *
     * @param logType {@link LogType}
     * @return list of {@link LogVerifier} objects associated with the log types.
     */
    public static List<LogVerifier> create(LogType logType) {
        Objects.requireNonNull(logType);

        switch (logType) {
            case ERROR_LOG_UTIL:
                return ImmutableList.of(
                        new AdServicesErrorLogUtilVerifier(),
                        new AdServicesErrorLogUtilWithExceptionVerifier());
            default:
                throw new IllegalArgumentException("Unsupported logType: " + logType);
        }
    }
}
