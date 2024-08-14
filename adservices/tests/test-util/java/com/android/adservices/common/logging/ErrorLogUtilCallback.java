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

import com.google.common.truth.Expect;

// TODO(b/355696393): integrate with rule and/or add unit tests (and document on internal site)
/**
 * {@code SyncCallback} used in conjunction with {@link #mockErrorLogUtilWithoutThrowable()} /
 * {@link #mockErrorLogUtilWithThrowable()}.
 */
public final class ErrorLogUtilCallback
        extends FailableResultSyncCallback<ErrorLogUtilInvocation, Exception> {

    private static final String TAG = ErrorLogUtilCallback.class.getSimpleName();

    /** Asserts {@link ErrorLogUtil#e(Throwable, int, int)}) was called with the given values. */
    public void assertReceived(Expect expect, Throwable throwable, int errorCode, int ppapiName)
            throws InterruptedException {
        ErrorLogUtilInvocation result = assertResultReceived();
        expect.withMessage("throwable on %s", result)
                .that(result.throwable)
                .isSameInstanceAs(throwable);
        expect.withMessage("errorCode on %s", result)
                .that(result.errorCode)
                .isSameInstanceAs(errorCode);
        expect.withMessage("ppapiName on %s", result)
                .that(result.ppapiName)
                .isSameInstanceAs(ppapiName);
    }

    /** Asserts {@link ErrorLogUtil#e(int, int)}) was called with the given values. */
    public void assertReceived(Expect expect, int errorCode, int ppapiName)
            throws InterruptedException {
        ErrorLogUtilInvocation result = assertResultReceived();
        expect.withMessage("throwable on %s", result).that(result.throwable).isNull();
        expect.withMessage("errorCode on %s", result)
                .that(result.errorCode)
                .isSameInstanceAs(errorCode);
        expect.withMessage("ppapiName on %s", result)
                .that(result.ppapiName)
                .isSameInstanceAs(ppapiName);
    }

    /**
     * Mocks a call to {@link ErrorLogUtil#e(Throwable, int, int)} and returns a callback object
     * that blocks until that call is made.
     *
     * <p>Useful in cases where {@link ErrorLogUtil} is expected to be called asynchronously.
     */
    public static ErrorLogUtilCallback mockErrorLogUtilWithThrowable() {
        ErrorLogUtilCallback callback = new ErrorLogUtilCallback();
        doAnswer(
                        inv -> {
                            Log.d(TAG, "mockErrorLogUtilError(): inv= " + inv);
                            callback.injectResult(
                                    new ErrorLogUtilInvocation(
                                            inv.getArgument(0),
                                            inv.getArgument(1),
                                            inv.getArgument(2)));
                            return null;
                        })
                .when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
        return callback;
    }

    /**
     * Mocks a call to {@link ErrorLogUtil#e(int, int)} and returns a callback object that blocks
     * until that call is made.
     *
     * <p>Useful in cases where {@link ErrorLogUtil} is expected to be called asynchronously.
     */
    public static ErrorLogUtilCallback mockErrorLogUtilWithoutThrowable() {
        ErrorLogUtilCallback callback = new ErrorLogUtilCallback();
        doAnswer(
                        inv -> {
                            Log.d(TAG, "mockErrorLogUtilError(): inv= " + inv);
                            callback.injectResult(
                                    new ErrorLogUtilInvocation(
                                            /* throwable= */ null,
                                            inv.getArgument(0),
                                            inv.getArgument(1)));
                            return null;
                        })
                .when(() -> ErrorLogUtil.e(anyInt(), anyInt()));
        return callback;
    }

    private ErrorLogUtilCallback() {}
    ;
}
