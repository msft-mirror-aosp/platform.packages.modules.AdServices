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

import static com.android.adservices.common.logging.AdServicesLogVerifierFactory.LogType;
import static com.android.adservices.common.logging.AdServicesLogVerifierFactory.LogType.ERROR_LOG_UTIL;

import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.shared.testing.AbstractLoggingUsageRule;
import com.android.adservices.shared.testing.LogVerifier;

import com.google.common.collect.ImmutableSet;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of AdServices specific logging usage rule. Intended to be used for AdServices
 * specific loggers (e.g. ErrorLogUtil).
 */
public final class AdServicesLoggingUsageRule extends AbstractLoggingUsageRule {
    private final List<LogVerifier> mLogVerifiers;

    /** Init AdServicesLoggingUsageRule. */
    public AdServicesLoggingUsageRule(Set<LogType> enabledLogTypes) {
        mLogVerifiers = mapToLogVerifiers(enabledLogTypes);
    }

    /**
     * Rule that scans for usage of {@code ErrorLogUtil.e(int, int)} and {@code
     * ErrorLogUtil.e(Throwable, int, int)} invocations. Fails the test if calls haven't been
     * verified using {@link ExpectErrorLogUtilCall} and/or {@link
     * ExpectErrorLogUtilWithExceptionCall}.
     */
    public static AdServicesLoggingUsageRule errorLogUtilUsageRule() {
        return new AdServicesLoggingUsageRule(ImmutableSet.of(ERROR_LOG_UTIL));
    }

    @Override
    public List<LogVerifier> getLogVerifiers() {
        return mLogVerifiers;
    }

    private List<LogVerifier> mapToLogVerifiers(Set<LogType> enabledLogTypes) {
        return enabledLogTypes.stream()
                .flatMap(type -> AdServicesLogVerifierFactory.create(type).stream())
                .collect(Collectors.toList());
    }
}
