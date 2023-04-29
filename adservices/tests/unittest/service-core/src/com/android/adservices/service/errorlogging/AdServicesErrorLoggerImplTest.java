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

package com.android.adservices.service.errorlogging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.database.sqlite.SQLiteException;

import com.android.adservices.service.Flags;
import com.android.adservices.service.stats.StatsdAdServicesLogger;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AdServicesErrorLoggerImplTest {
    private AdServicesErrorLoggerImpl mErrorLogger;

    // Constants used for tests
    private static final String CLASS_NAME = "TopicsService";
    private static final String METHOD_NAME = "getTopics";
    private static final int PPAPI_NAME = 1;
    private static final int LINE_NUMBER = 11;
    private static final String SQ_LITE_EXCEPTION = "SQLiteException";

    @Mock private Flags mFlags;
    @Mock StatsdAdServicesLogger mStatsdLoggerMock;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mErrorLogger = new AdServicesErrorLoggerImpl(mFlags, mStatsdLoggerMock);
    }

    @Test
    public void testLogError_flagDisabled() {
        doReturn(false).when(mFlags).getAdServicesErrorLoggingEnabled();

        mErrorLogger.logError(
                AdServicesErrorCode.CONSENT_REVOKED_ERROR, PPAPI_NAME, CLASS_NAME, METHOD_NAME);

        verify(mStatsdLoggerMock, never()).logAdServicesError(any());
    }

    @Test
    public void testLogError_flagEnabled() {
        doReturn(true).when(mFlags).getAdServicesErrorLoggingEnabled();
        AdServicesErrorCode errorCode = AdServicesErrorCode.CONSENT_REVOKED_ERROR;

        mErrorLogger.logError(errorCode, PPAPI_NAME, CLASS_NAME, METHOD_NAME);

        AdServicesErrorStats stats =
                AdServicesErrorStats.builder()
                        .setErrorCode(errorCode.getErrorCode())
                        .setPpapiName(PPAPI_NAME)
                        .setClassName(CLASS_NAME)
                        .setMethodName(METHOD_NAME)
                        .build();
        verify(mStatsdLoggerMock).logAdServicesError(eq(stats));
    }

    @Test
    public void testLogErrorWithExceptionInfo_flagDisabled() {
        doReturn(false).when(mFlags).getAdServicesErrorLoggingEnabled();

        mErrorLogger.logErrorWithExceptionInfo(
                AdServicesErrorCode.DATABASE_READ_EXCEPTION, PPAPI_NAME, new Exception());

        verify(mStatsdLoggerMock, never()).logAdServicesError(any());
    }

    @Test
    public void testLogErrorWithExceptionInfo_flagEnabled() {
        doReturn(true).when(mFlags).getAdServicesErrorLoggingEnabled();

        AdServicesErrorCode errorCode = AdServicesErrorCode.DATABASE_READ_EXCEPTION;
        Exception exception = createSQLiteException(CLASS_NAME, METHOD_NAME, LINE_NUMBER);

        mErrorLogger.logErrorWithExceptionInfo(
                AdServicesErrorCode.DATABASE_READ_EXCEPTION, PPAPI_NAME, exception);

        AdServicesErrorStats stats =
                AdServicesErrorStats.builder()
                        .setErrorCode(errorCode.getErrorCode())
                        .setPpapiName(PPAPI_NAME)
                        .setClassName(CLASS_NAME)
                        .setMethodName(METHOD_NAME)
                        .setLineNumber(LINE_NUMBER)
                        .setLastObservedExceptionName(SQ_LITE_EXCEPTION)
                        .build();
        verify(mStatsdLoggerMock).logAdServicesError(eq(stats));
    }

    @Test
    public void testLogErrorWithExceptionInfo_fullyQualifiedClassName_flagEnabled() {
        doReturn(true).when(mFlags).getAdServicesErrorLoggingEnabled();

        AdServicesErrorCode errorCode = AdServicesErrorCode.DATABASE_READ_EXCEPTION;
        String fullClassName = "com.android.adservices.topics.TopicsService";
        Exception exception = createSQLiteException(fullClassName, METHOD_NAME, LINE_NUMBER);

        mErrorLogger.logErrorWithExceptionInfo(errorCode, PPAPI_NAME, exception);

        AdServicesErrorStats stats =
                AdServicesErrorStats.builder()
                        .setErrorCode(errorCode.getErrorCode())
                        .setPpapiName(PPAPI_NAME)
                        .setClassName(CLASS_NAME)
                        .setMethodName(METHOD_NAME)
                        .setLineNumber(LINE_NUMBER)
                        .setLastObservedExceptionName(SQ_LITE_EXCEPTION)
                        .build();
        verify(mStatsdLoggerMock).logAdServicesError(eq(stats));
    }

    @Test
    public void testLogErrorWithExceptionInfo_emptyClassName_flagEnabled() {
        doReturn(true).when(mFlags).getAdServicesErrorLoggingEnabled();

        AdServicesErrorCode errorCode = AdServicesErrorCode.DATABASE_READ_EXCEPTION;
        Exception exception = createSQLiteException(/* className = */ "", METHOD_NAME, LINE_NUMBER);

        mErrorLogger.logErrorWithExceptionInfo(
                AdServicesErrorCode.DATABASE_READ_EXCEPTION, PPAPI_NAME, exception);

        AdServicesErrorStats stats =
                AdServicesErrorStats.builder()
                        .setErrorCode(errorCode.getErrorCode())
                        .setPpapiName(PPAPI_NAME)
                        .setMethodName(METHOD_NAME)
                        .setLineNumber(LINE_NUMBER)
                        .setLastObservedExceptionName(SQ_LITE_EXCEPTION)
                        .build();
        verify(mStatsdLoggerMock).logAdServicesError(eq(stats));
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
}
