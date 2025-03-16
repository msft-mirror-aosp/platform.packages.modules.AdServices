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
import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall.UNDEFINED_INT_PARAM;
import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;
import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Undefined;

import android.annotation.Nullable;

import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.shared.testing.LogCall;

import java.util.Objects;

/**
 * Corresponding class for {@link ExpectErrorLogUtilCall} and {@link
 * ExpectErrorLogUtilWithExceptionCall}.
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
     * Creates {@link ErrorLogUtilCall} object to represent {@code ErrorLogUtil.e(int, int)} with
     * appropriate number of invocation times.
     */
    public static ErrorLogUtilCall createWithNoException(int errorCode, int ppapiName, int times) {
        return new ErrorLogUtilCall(None.class, errorCode, ppapiName, times);
    }

    /**
     * Creates {@link ErrorLogUtilCall} object to represent {@code ErrorLogUtil.e(int, int)} if
     * throwable is null; otherwise creates {@link ErrorLogUtilCall} object to represent {@code
     * ErrorLogUtil.e(Throwable, int, int)}
     */
    public static ErrorLogUtilCall createWithNullableThrowable(
            @Nullable Throwable throwable, int errorCode, int ppapiName, int times) {
        return throwable == null
                ? createWithNoException(errorCode, ppapiName, times)
                : new ErrorLogUtilCall(throwable.getClass(), errorCode, ppapiName, times);
    }

    /**
     * Creates {@link ErrorLogUtilCall} object to represent {@code ErrorLogUtil.e(int, int)} from
     * {@link ExpectErrorLogUtilCall} annotation by deriving any missing values from {@link
     * SetErrorLogUtilDefaultParams}.
     *
     * <p>If a value cannot be deduced, {@link IllegalArgumentException} is thrown.
     */
    public static ErrorLogUtilCall createFrom(
            ExpectErrorLogUtilCall annotation,
            @Nullable SetErrorLogUtilDefaultParams defaultParamsAnnotation) {
        ErrorLogUtilCall defaultParams = createFrom(defaultParamsAnnotation);

        ErrorLogUtilCall call =
                createWithNoException(
                        annotation.errorCode() == UNDEFINED_INT_PARAM
                                ? defaultParams.mErrorCode
                                : annotation.errorCode(),
                        annotation.ppapiName() == UNDEFINED_INT_PARAM
                                ? defaultParams.mPpapiName
                                : annotation.ppapiName(),
                        annotation.times());
        validateAllParamsDefined(call, ExpectErrorLogUtilCall.ANNOTATION_NAME);

        return call;
    }

    /**
     * Creates {@link ErrorLogUtilCall} object to represent {@code ErrorLogUtil.e(Throwable, int,
     * int)} from {@link ExpectErrorLogUtilWithExceptionCall} annotation by deriving any missing
     * values from {@link SetErrorLogUtilDefaultParams}.
     *
     * <p>If a value cannot be deduced, {@link IllegalArgumentException} is thrown.
     */
    public static ErrorLogUtilCall createFrom(
            ExpectErrorLogUtilWithExceptionCall annotation,
            @Nullable SetErrorLogUtilDefaultParams defaultParamsAnnotation) {
        ErrorLogUtilCall defaultParams = createFrom(defaultParamsAnnotation);

        ErrorLogUtilCall call =
                new ErrorLogUtilCall(
                        Objects.equals(annotation.throwable(), Undefined.class)
                                ? defaultParams.mThrowable
                                : annotation.throwable(),
                        annotation.errorCode() == UNDEFINED_INT_PARAM
                                ? defaultParams.mErrorCode
                                : annotation.errorCode(),
                        annotation.ppapiName() == UNDEFINED_INT_PARAM
                                ? defaultParams.mPpapiName
                                : annotation.ppapiName(),
                        annotation.times());
        validateAllParamsDefined(call, ExpectErrorLogUtilWithExceptionCall.ANNOTATION_NAME);

        return call;
    }

    private static ErrorLogUtilCall createFrom(SetErrorLogUtilDefaultParams defaultParams) {
        return defaultParams == null
                ? new ErrorLogUtilCall(Undefined.class, UNDEFINED_INT_PARAM, UNDEFINED_INT_PARAM)
                : new ErrorLogUtilCall(
                        defaultParams.throwable(),
                        defaultParams.errorCode(),
                        defaultParams.ppapiName());
    }

    private static void validateAllParamsDefined(ErrorLogUtilCall call, String annotationName) {
        if (Objects.equals(call.mThrowable, Undefined.class)) {
            throw new IllegalArgumentException("Cannot resolve throwable for @" + annotationName);
        }

        if (call.mErrorCode == UNDEFINED_INT_PARAM) {
            throw new IllegalArgumentException("Cannot resolve errorCode for @" + annotationName);
        }

        if (call.mPpapiName == UNDEFINED_INT_PARAM) {
            throw new IllegalArgumentException("Cannot resolve ppapiName for @" + annotationName);
        }
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
