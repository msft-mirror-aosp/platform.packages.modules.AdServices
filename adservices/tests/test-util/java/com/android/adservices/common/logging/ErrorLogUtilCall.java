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

import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall.DEFAULT_TIMES;
import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;

import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.shared.testing.LogCall;

import java.util.Objects;

/**
 * Corresponding class for {@link ExpectErrorLogUtilCall}.
 *
 * <p>Class is modeled to represent {@code ErrorLogUtil.e(int, int)}, {@code
 * ErrorLogUtil.e(Throwable, int, int)} calls, including the case where the Throwable parameter
 * could be a wildcard i.e. {@link Any}.
 */
public final class ErrorLogUtilCall extends LogCall {
    public final Class<? extends Throwable> mThrowable;
    public final int mErrorCode;
    public final int mPpapiName;

    /** Init ErrorLogUtilCall with default number of times (i.e. 1). */
    public ErrorLogUtilCall(Class<? extends Throwable> throwable, int errorCode, int ppapiName) {
        this(throwable, errorCode, ppapiName, DEFAULT_TIMES);
    }

    /** Init ErrorLogUtilCall with specific number of times. */
    public ErrorLogUtilCall(
            Class<? extends Throwable> throwable, int errorCode, int ppapiName, int times) {
        super(times);
        mThrowable = throwable;
        mErrorCode = errorCode;
        mPpapiName = ppapiName;
    }

    /**
     * Creates an appropriate object to represent {@code ErrorLogUtil.e(int, int)} with appropriate
     * number of invocation times.
     */
    public static ErrorLogUtilCall createWithNoException(int errorCode, int ppapiName, int times) {
        return new ErrorLogUtilCall(None.class, errorCode, ppapiName, times);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ErrorLogUtilCall other)) {
            return false;
        }

        return Objects.equals(mThrowable, other.mThrowable)
                && mErrorCode == other.mErrorCode
                && mPpapiName == other.mPpapiName;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mThrowable, mErrorCode, mPpapiName);
    }

    @Override
    public boolean isEquivalentInvocation(LogCall o) {
        if (!(o instanceof ErrorLogUtilCall other)) {
            return false;
        }

        // Check if both are ErrorLogUtil.e(Throwable, int, int) or ErrorLogUtil.e(int, int) calls.
        if (!isIdenticalApi(other)) {
            return false;
        }

        // Check if logging arguments are the same; if either of the log calls are specified with
        // Any as the exception type, then omit throwable comparison.
        return (isCallWithAnyException()
                        || other.isCallWithAnyException()
                        || Objects.equals(mThrowable, other.mThrowable))
                && mErrorCode == other.mErrorCode
                && mPpapiName == other.mPpapiName;
    }

    @Override
    public String logInvocationToString() {
        if (!isCallWithException()) {
            return "ErrorLogUtil.e(" + mErrorCode + ", " + mPpapiName + ")";
        }

        return "ErrorLogUtil.e("
                + mThrowable.getSimpleName()
                + ", "
                + mErrorCode
                + ", "
                + mPpapiName
                + ")";
    }

    private boolean isIdenticalApi(ErrorLogUtilCall other) {
        if (isCallWithException()) {
            return other.isCallWithException();
        }

        return !other.isCallWithException();
    }

    private boolean isCallWithException() {
        return !Objects.equals(mThrowable, None.class);
    }

    private boolean isCallWithAnyException() {
        return Objects.equals(mThrowable, Any.class);
    }

    /** Placeholder exception used to represent {@code ErrorLogUtil.e(int, int)} calls. */
    static final class None extends Throwable {
        private None() {}
    }
}
