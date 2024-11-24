/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.shared.errorlogging;

import com.android.adservices.shared.util.LogUtil;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * Abstract Class that logs AdServices error/ exception to {@code Statsd}. This class internally
 * calls {@link StatsdAdServicesErrorLogger} to log the error to {@code Statsd}.
 */
public abstract class AbstractAdServicesErrorLogger implements AdServicesErrorLogger {
    private final StatsdAdServicesErrorLogger mStatsdAdServicesErrorLogger;
    private static final int MAX_CAUSE_NUMBER = 5;

    protected AbstractAdServicesErrorLogger(
            StatsdAdServicesErrorLogger statsdAdServicesErrorLogger) {
        mStatsdAdServicesErrorLogger = Objects.requireNonNull(statsdAdServicesErrorLogger);
    }

    @Override
    public void logError(int errorCode, int ppapiName) {
        // Create a temporary exception to get stack trace.
        logErrorInternal(new Throwable(), errorCode, ppapiName, false);
    }

    @Override
    @Deprecated
    public void logErrorWithExceptionInfo(Throwable tr, int errorCode, int ppapiName) {
        if (!isEnabled(errorCode)) {
            return;
        }
        AdServicesErrorStats.Builder builder =
                AdServicesErrorStats.builder().setErrorCode(errorCode).setPpapiName(ppapiName);
        populateExceptionInfo(tr, builder);

        mStatsdAdServicesErrorLogger.logAdServicesError(builder.build());
    }

    @Override
    public void logError(Throwable tr, int errorCode, int ppapiName) {
        logErrorInternal(tr, errorCode, ppapiName, true);
    }

    /** Checks if error logging is enabled for a particular error code. */
    protected abstract boolean isEnabled(int errorCode);

    @VisibleForTesting
    void logErrorInternal(Throwable tr, int errorCode, int ppapiName, boolean isSearchingCause) {
        if (!isEnabled(errorCode)) {
            return;
        }

        StackTraceElement stackTraceElement = null;
        if (!isSearchingCause) {
            StackTraceElement[] stackTrace = tr.getStackTrace();
            // Look at the 3rd element of the stack trace as that's where we actually log the error.
            // For example, StackTrace = {AdServicesErrorLoggerImpl.logError, ErrorLogUtil.e,
            // EpochJobService.onStartJob, ... } and we log stats for EpochJobService.onStartJob.
            int elementIdx = 2;
            if (stackTrace.length < elementIdx + 1) {
                LogUtil.w("Stack trace length less than 3, skipping client error logging");
                return;
            }
            stackTraceElement = stackTrace[elementIdx];
        } else {
            stackTraceElement = getRootCauseStackTraceElement(tr);
            if (stackTraceElement == null) {
                return;
            }
        }
        AdServicesErrorStats.Builder builder =
                AdServicesErrorStats.builder().setErrorCode(errorCode).setPpapiName(ppapiName);
        populateClassInfo(stackTraceElement, builder);
        mStatsdAdServicesErrorLogger.logAdServicesError(builder.build());
    }

    private StackTraceElement getRootCauseStackTraceElement(Throwable tr) {
        int maxCauseNumber = MAX_CAUSE_NUMBER;
        StackTraceElement stackTraceElement = null;
        while (tr != null && maxCauseNumber-- > 0) {
            if (tr.getStackTrace().length > 0) {
                stackTraceElement = tr.getStackTrace()[0];
            }
            tr = tr.getCause();
        }
        return stackTraceElement;
    }

    private void populateExceptionInfo(Throwable tr, AdServicesErrorStats.Builder builder) {
        if (tr.getStackTrace().length == 0) {
            return;
        }
        // We just populate the first element of the stack trace
        StackTraceElement element = tr.getStackTrace()[0];
        populateClassInfo(element, builder);
        // Get the exception name and is not full qualified.
        String shortExceptionName = getLastElement(tr.getClass().getName(), '.');
        builder.setLastObservedExceptionName(shortExceptionName);
    }

    private void populateClassInfo(
            StackTraceElement element, AdServicesErrorStats.Builder builder) {
        // Get the class name and is not full qualified.
        String shortClassName = getLastElement(element.getClassName(), '.');
        builder.setClassName(shortClassName)
                .setMethodName(element.getMethodName())
                .setLineNumber(element.getLineNumber());
    }

    // Gets the last element of the String based on the delimiter.
    // Example ("com.adservices.Topics", '.')  => "Topics"
    // Example ("Topics", '.')  => "Topics"
    // Example ("", '.')  => ""
    private String getLastElement(String str, int delimiter) {
        return str.isEmpty() ? str : str.substring(str.lastIndexOf(delimiter) + 1);
    }
}
