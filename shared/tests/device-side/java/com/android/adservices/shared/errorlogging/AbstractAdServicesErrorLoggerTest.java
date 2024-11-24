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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CONSENT_REVOKED_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.database.sqlite.SQLiteException;

import androidx.annotation.Nullable;

import com.android.adservices.shared.SharedMockitoTestCase;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public final class AbstractAdServicesErrorLoggerTest extends SharedMockitoTestCase {

    @Mock private StatsdAdServicesErrorLogger mMockStatsdLogger;

    // Constants used for tests
    private static final String CLASS_NAME = "TopicsService";
    private static final String METHOD_NAME = "getTopics";
    private static final int PPAPI_NAME = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;
    private static final int LINE_NUMBER = 11;
    private static final String SQ_LITE_EXCEPTION = "SQLiteException";

    private AbstractAdServicesErrorLogger mErrorLoggerEnabled;
    private AbstractAdServicesErrorLogger mErrorLoggerDisabled;

    @Before
    public void setUp() {
        mErrorLoggerEnabled =
                new AbstractAdServicesErrorLogger(mMockStatsdLogger) {
                    @Override
                    protected boolean isEnabled(int errorCode) {
                        return true;
                    }
                };
        mErrorLoggerDisabled =
                new AbstractAdServicesErrorLogger(mMockStatsdLogger) {
                    @Override
                    protected boolean isEnabled(int errorCode) {
                        return false;
                    }
                };
    }

    @Test
    public void testLogError_withOutThrowable_errorLoggingDisabled() {
        mErrorLoggerDisabled.logError(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CONSENT_REVOKED_ERROR, PPAPI_NAME);

        verify(mMockStatsdLogger, never()).logAdServicesError(any());
    }

    @Test
    public void testLogError_withOutThrowable_errorLoggingFlagEnabled() {
        mErrorLoggerEnabled.logError(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CONSENT_REVOKED_ERROR, PPAPI_NAME);

        verify(mMockStatsdLogger).logAdServicesError(any());
    }

    @Test
    public void testLogErrorInternal() {
        Exception exception =
                createSQLiteExceptionWith3StackTraceElements(CLASS_NAME, METHOD_NAME, LINE_NUMBER);
        mErrorLoggerEnabled.logErrorInternal(
                (Throwable) exception,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CONSENT_REVOKED_ERROR,
                PPAPI_NAME,
                false);

        AdServicesErrorStats stats =
                AdServicesErrorStats.builder()
                        .setErrorCode(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CONSENT_REVOKED_ERROR)
                        .setPpapiName(PPAPI_NAME)
                        .setClassName(CLASS_NAME)
                        .setMethodName(METHOD_NAME)
                        .setLineNumber(LINE_NUMBER)
                        .build();
        verify(mMockStatsdLogger).logAdServicesError(stats);
    }

    @Test
    public void testLogError_withThrowable_errorLoggingFlagEnabled() {
        Throwable tr = createThrowableWithMultipleCauses(CLASS_NAME, METHOD_NAME, LINE_NUMBER, 1);

        mErrorLoggerEnabled.logError(
                tr, AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CONSENT_REVOKED_ERROR, PPAPI_NAME);

        AdServicesErrorStats stats =
                AdServicesErrorStats.builder()
                        .setErrorCode(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CONSENT_REVOKED_ERROR)
                        .setPpapiName(PPAPI_NAME)
                        .setClassName(CLASS_NAME)
                        .setMethodName(METHOD_NAME)
                        .setLineNumber(LINE_NUMBER)
                        .build();
        verify(mMockStatsdLogger).logAdServicesError(stats);
    }

    // test get the stack trace that not from the first throwable, but further cause.
    @Test
    public void testLogError_getStackTraceFromCause() {
        Throwable tr = createThrowableWithMultipleCauses(CLASS_NAME, METHOD_NAME, LINE_NUMBER, 3);

        mErrorLoggerEnabled.logError(
                tr, AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CONSENT_REVOKED_ERROR, PPAPI_NAME);

        AdServicesErrorStats stats =
                AdServicesErrorStats.builder()
                        .setErrorCode(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CONSENT_REVOKED_ERROR)
                        .setPpapiName(PPAPI_NAME)
                        .setClassName(CLASS_NAME)
                        .setMethodName(METHOD_NAME)
                        .setLineNumber(LINE_NUMBER)
                        .build();
        verify(mMockStatsdLogger).logAdServicesError(stats);
    }

    // test get stack trace from further cause, but longer than the maximum limitation.
    @Test
    public void testLogError_getStackTraceFromCauseBeyondLimit() {
        Throwable tr = createThrowableWithMultipleCauses(CLASS_NAME, METHOD_NAME, LINE_NUMBER, 7);

        mErrorLoggerEnabled.logError(
                tr, AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CONSENT_REVOKED_ERROR, PPAPI_NAME);

        AdServicesErrorStats stats =
                AdServicesErrorStats.builder()
                        .setErrorCode(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CONSENT_REVOKED_ERROR)
                        .setPpapiName(PPAPI_NAME)
                        .setClassName(CLASS_NAME)
                        .setMethodName(METHOD_NAME)
                        .setLineNumber(LINE_NUMBER)
                        .build();
        verify(mMockStatsdLogger, never()).logAdServicesError(stats);
    }

    // Test if a empty stacktrace in the middle of list of cause, it won't affect get the later one.
    @Test
    public void logError_emptyStackTraceInMiddleOfCause() {
        Throwable tr = createThrowableWithMultipleCauses(CLASS_NAME, METHOD_NAME, LINE_NUMBER, 2);
        Throwable topTr = new Throwable("emptyStackTraceThrowable", tr);
        topTr.setStackTrace(new StackTraceElement[] {});

        mErrorLoggerEnabled.logError(
                tr, AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CONSENT_REVOKED_ERROR, PPAPI_NAME);

        AdServicesErrorStats stats =
                AdServicesErrorStats.builder()
                        .setErrorCode(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CONSENT_REVOKED_ERROR)
                        .setPpapiName(PPAPI_NAME)
                        .setClassName(CLASS_NAME)
                        .setMethodName(METHOD_NAME)
                        .setLineNumber(LINE_NUMBER)
                        .build();
        verify(mMockStatsdLogger).logAdServicesError(stats);
    }

    @Test
    public void testLogErrorWithExceptionInfo_errorLoggingFlagDisabled() {
        mErrorLoggerDisabled.logErrorWithExceptionInfo(
                new Exception(),
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION,
                PPAPI_NAME);

        verify(mMockStatsdLogger, never()).logAdServicesError(any());
    }

    @Test
    public void testLogErrorWithExceptionInfo_errorLoggingFlagEnabled() {
        Exception exception = createSQLiteException(CLASS_NAME, METHOD_NAME, LINE_NUMBER);

        mErrorLoggerEnabled.logErrorWithExceptionInfo(
                exception,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION,
                PPAPI_NAME);

        AdServicesErrorStats stats =
                AdServicesErrorStats.builder()
                        .setErrorCode(
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION)
                        .setPpapiName(PPAPI_NAME)
                        .setClassName(CLASS_NAME)
                        .setMethodName(METHOD_NAME)
                        .setLineNumber(LINE_NUMBER)
                        .setLastObservedExceptionName(SQ_LITE_EXCEPTION)
                        .build();
        verify(mMockStatsdLogger).logAdServicesError(stats);
    }

    @Test
    public void testLogErrorWithExceptionInfo_fullyQualifiedClassName_errorLoggingFlagEnabled() {
        String fullClassName = "com.android.adservices.topics.TopicsService";
        Exception exception = createSQLiteException(fullClassName, METHOD_NAME, LINE_NUMBER);

        mErrorLoggerEnabled.logErrorWithExceptionInfo(
                exception,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION,
                PPAPI_NAME);

        AdServicesErrorStats stats =
                AdServicesErrorStats.builder()
                        .setErrorCode(
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION)
                        .setPpapiName(PPAPI_NAME)
                        .setClassName(CLASS_NAME)
                        .setMethodName(METHOD_NAME)
                        .setLineNumber(LINE_NUMBER)
                        .setLastObservedExceptionName(SQ_LITE_EXCEPTION)
                        .build();
        verify(mMockStatsdLogger).logAdServicesError(stats);
    }

    @Test
    public void testLogErrorWithExceptionInfo_emptyClassName_errorLoggingFlagEnabled() {
        Exception exception = createSQLiteException(/* className = */ "", METHOD_NAME, LINE_NUMBER);

        mErrorLoggerEnabled.logErrorWithExceptionInfo(
                exception,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION,
                PPAPI_NAME);

        AdServicesErrorStats stats =
                AdServicesErrorStats.builder()
                        .setErrorCode(
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION)
                        .setPpapiName(PPAPI_NAME)
                        .setMethodName(METHOD_NAME)
                        .setLineNumber(LINE_NUMBER)
                        .setLastObservedExceptionName(SQ_LITE_EXCEPTION)
                        .build();
        verify(mMockStatsdLogger).logAdServicesError(stats);
    }

    Exception createSQLiteException(String className, String methodName, int lineNumber) {
        StackTraceElement[] stackTraceElements =
                new StackTraceElement[] {
                    new StackTraceElement(className, methodName, "file", lineNumber)
                };

        Exception exception = new SQLiteException();
        exception.setStackTrace(stackTraceElements);
        return exception;
    }

    Exception createSQLiteExceptionWith3StackTraceElements(
            String className, String methodName, int lineNumber) {
        StackTraceElement[] stackTraceElements =
                new StackTraceElement[] {
                    new StackTraceElement("AdServicesErrorLoggerImpl", "logError", "file", 4),
                    new StackTraceElement("ErrorLogUtil", "e", "file", 4),
                    new StackTraceElement(className, methodName, "file", lineNumber)
                };

        Exception exception = new SQLiteException();
        exception.setStackTrace(stackTraceElements);
        return exception;
    }

    @Nullable
    private Throwable createThrowableWithMultipleCauses(
            String className, String methodName, int lineNumber, int number) {
        if (number == 0) {
            return null;
        }
        Throwable lastThrowable = new Throwable("lastThrowable");
        StackTraceElement[] lastStackTraceElements =
                new StackTraceElement[] {
                    new StackTraceElement(className, methodName, "file", lineNumber)
                };
        lastThrowable.setStackTrace(lastStackTraceElements);
        Throwable formerThrowable = lastThrowable;
        for (int i = 0; i < number - 1; i++) {
            Throwable throwable =
                    new Throwable(String.format("createThrowable%d", i), formerThrowable);
            StackTraceElement[] stackTraceElements =
                    new StackTraceElement[] {
                        new StackTraceElement(
                                String.format("testClassName%d", i),
                                String.format("testMethodName%d", i),
                                "file",
                                i)
                    };
            throwable.setStackTrace(stackTraceElements);
            formerThrowable = throwable;
        }
        return formerThrowable;
    }
}
