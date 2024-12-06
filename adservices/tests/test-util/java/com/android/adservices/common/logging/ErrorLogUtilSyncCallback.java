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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

import android.util.Log;

import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.shared.testing.concurrency.FailableResultSyncCallback;
import com.android.adservices.shared.testing.concurrency.SyncCallbackFactory;

import com.google.common.truth.Expect;

import java.util.List;
import java.util.stream.Collectors;

// TODO(b/355696393): integrate with rule and/or add unit tests (and document on internal site)

/**
 * {@code SyncCallback} used in conjunction with {@link #mockErrorLogUtilWithoutThrowable()} /
 * {@link #mockErrorLogUtilWithThrowable()}.
 */
public final class ErrorLogUtilSyncCallback
        extends FailableResultSyncCallback<ErrorLogUtilCall, Exception> {
    private static final int DEFAULT_NUM_CALLS = 1;
    private static final String TAG = ErrorLogUtilSyncCallback.class.getSimpleName();

    /**
     * Asserts {@link ErrorLogUtil#e(Throwable, int, int)}) was called once with the given values.
     */
    public void assertReceived(Expect expect, Throwable throwable, int errorCode, int ppapiName)
            throws InterruptedException {
        assertReceived(expect, throwable, errorCode, ppapiName, DEFAULT_NUM_CALLS);
    }

    /**
     * Asserts {@link ErrorLogUtil#e(Throwable, int, int)}) was called a certain number of times
     * with the given values.
     */
    public void assertReceived(
            Expect expect, Throwable throwable, int errorCode, int ppapiName, int numExpectedCalls)
            throws InterruptedException {
        assertCalls(
                expect,
                ErrorLogUtilCall.createWithNullableThrowable(
                        throwable, errorCode, ppapiName, numExpectedCalls),
                numExpectedCalls);
    }

    /** Asserts {@link ErrorLogUtil#e(int, int)}) was called once with the given values. */
    public void assertReceived(Expect expect, int errorCode, int ppapiName)
            throws InterruptedException {
        assertReceived(expect, errorCode, ppapiName, DEFAULT_NUM_CALLS);
    }

    /**
     * Asserts {@link ErrorLogUtil#e(int, int)}) was called a certain number of times with the given
     * values.
     */
    public void assertReceived(Expect expect, int errorCode, int ppapiName, int numExpectedCalls)
            throws InterruptedException {
        assertReceived(expect, /* throwable= */ null, errorCode, ppapiName, numExpectedCalls);
    }

    private void assertCalls(
            Expect expect, ErrorLogUtilCall expectedInvocation, int numExpectedCalls)
            throws InterruptedException {
        List<ErrorLogUtilCall> results = internalAssertResultsReceived();
        List<ErrorLogUtilCall> actualInvocations =
                results.stream().filter(expectedInvocation::equals).collect(Collectors.toList());
        expect.withMessage(
                        "total invocations of %s from actual %s",
                        expectedInvocation.logInvocationToString(), format(results))
                .that(actualInvocations.size())
                .isEqualTo(numExpectedCalls);
    }

    private String format(List<ErrorLogUtilCall> rawCalls) {
        StringBuilder stringBuilder = new StringBuilder("[");

        // Just print the raw invocations without "times". If needed, could potentially enhance this
        // by grouping all similar invocations together.
        for (ErrorLogUtilCall call : rawCalls) {
            stringBuilder.append(call.logInvocationToString()).append(",");
        }

        // Delete the last comma
        if (stringBuilder.length() > 1) {
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        }

        return stringBuilder.append("]").toString();
    }

    /**
     * Mocks a call to {@link ErrorLogUtil#e(Throwable, int, int)} and returns a callback object
     * that blocks until that call is made.
     *
     * <p>Useful in cases where {@link ErrorLogUtil} is expected to be called once asynchronously.
     */
    public static ErrorLogUtilSyncCallback mockErrorLogUtilWithThrowable() {
        ErrorLogUtilSyncCallback callback = new ErrorLogUtilSyncCallback();
        mockInvocationWithThrowable(callback);
        return callback;
    }

    /**
     * Mocks a call to {@link ErrorLogUtil#e(Throwable, int, int)} and returns a callback object
     * that blocks until expected number of calls are made.
     *
     * <p>Useful in cases where {@link ErrorLogUtil} is expected to be called multiple times
     * asynchronously.
     */
    public static ErrorLogUtilSyncCallback mockErrorLogUtilWithThrowable(int numExpectedCalls) {
        ErrorLogUtilSyncCallback callback = new ErrorLogUtilSyncCallback(numExpectedCalls);
        mockInvocationWithThrowable(callback);
        return callback;
    }

    private static void mockInvocationWithThrowable(ErrorLogUtilSyncCallback callback) {
        doAnswer(
                        inv -> {
                            Log.d(TAG, "mockErrorLogUtilError(): inv= " + inv);
                            callback.injectResult(
                                    ErrorLogUtilCall.createWithNullableThrowable(
                                            inv.getArgument(0),
                                            inv.getArgument(1),
                                            inv.getArgument(2),
                                            DEFAULT_NUM_CALLS));
                            return null;
                        })
                .when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
    }

    /**
     * Mocks a call to {@link ErrorLogUtil#e(int, int)} and returns a callback object that blocks
     * until that call is made.
     *
     * <p>Useful in cases where {@link ErrorLogUtil} is expected to be called once asynchronously.
     */
    public static ErrorLogUtilSyncCallback mockErrorLogUtilWithoutThrowable() {
        ErrorLogUtilSyncCallback callback = new ErrorLogUtilSyncCallback();
        mockInvocationWithoutThrowable(callback);
        return callback;
    }

    /**
     * Mocks a call to {@link ErrorLogUtil#e(int, int)} and returns a callback object that blocks
     * until expected number of calls are made.
     *
     * <p>Useful in cases where {@link ErrorLogUtil} is expected to be called multiple times
     * asynchronously.
     */
    public static ErrorLogUtilSyncCallback mockErrorLogUtilWithoutThrowable(int numExpectedCalls) {
        ErrorLogUtilSyncCallback callback = new ErrorLogUtilSyncCallback(numExpectedCalls);
        mockInvocationWithoutThrowable(callback);
        return callback;
    }

    private static void mockInvocationWithoutThrowable(ErrorLogUtilSyncCallback callback) {
        doAnswer(
                        inv -> {
                            Log.d(TAG, "mockErrorLogUtilError(): inv= " + inv);
                            callback.injectResult(
                                    ErrorLogUtilCall.createWithNullableThrowable(
                                            /* throwable= */ null,
                                            inv.getArgument(0),
                                            inv.getArgument(1),
                                            DEFAULT_NUM_CALLS));
                            return null;
                        })
                .when(() -> ErrorLogUtil.e(anyInt(), anyInt()));
    }

    private ErrorLogUtilSyncCallback() {}

    private ErrorLogUtilSyncCallback(int numExpectedCalls) {
        super(
                SyncCallbackFactory.newSettingsBuilder()
                        .setExpectedNumberCalls(numExpectedCalls)
                        .build());
    }
}
