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
package com.android.adservices.mockito;

import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.AdServicesLoggingUsageRule;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.shared.testing.concurrency.FailableResultSyncCallback;
import com.android.adservices.spe.AdServicesJobServiceLogger;

import com.google.common.truth.Expect;

import org.mockito.verification.VerificationMode;

import java.util.Objects;

/**
 * Provides Mockito expectation for common calls.
 *
 * <p><b>NOTE: </b> most expectations require {@code spyStatic()} or {@code mockStatic()} in the
 * {@link com.android.dx.mockito.inline.extended.StaticMockitoSession session} ahead of time - this
 * helper doesn't check that such calls were made, it's up to the caller to do so.
 *
 * @deprecated - use {@code mocker} reference provided by test superclasses (or {@link
 *     AdServicesExtendedMockitoMocker} when they're not available).
 */
@Deprecated // TODO(b/314969513): remove when not used anymore
public final class ExtendedMockitoExpectations {

    private static final String TAG = ExtendedMockitoExpectations.class.getSimpleName();

    // NOTE: not really "Generated code", but we're using mocker (instead of sMocker or MOCKER) as
    // that's the name of the reference provided by the superclasses - once tests are refactored
    // to use the superclasses, they wouldn't need to change the variable name.

    // CHECKSTYLE:OFF Generated code
    public static final AdServicesExtendedMockitoTestCase.Mocker mocker =
            new AdServicesExtendedMockitoTestCase.Mocker(new StaticClassChecker() {});

    // CHECKSTYLE:ON

    /**
     * Mocks a call to {@link ErrorLogUtil#e()}, does nothing.
     *
     * <p>Mocks behavior for both variants of the method.
     *
     * @deprecated Use {@link AdServicesLoggingUsageRule} to verify {@link ErrorLogUtil#e()} calls.
     *     Tests using this rule should NOT mock {@link ErrorLogUtil#e()} calls as it's taken care
     *     of under the hood.
     */
    @Deprecated
    public static void doNothingOnErrorLogUtilError() {
        doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
        doNothing().when(() -> ErrorLogUtil.e(anyInt(), anyInt()));
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

    /**
     * Mocks {@link AdServicesJobServiceLogger} to not actually log the stats to server.
     *
     * @deprecated Use {@link AdServicesJobMocker#mockNoOpAdServicesJobServiceLogger(Context,
     *     Flags))} instead.
     */
    @Deprecated
    public static AdServicesJobServiceLogger mockAdServicesJobServiceLogger(
            Context context, Flags flags) {
        return MockitoExpectations.jobMocker.mockNoOpAdServicesJobServiceLogger(context, flags);
    }

    /**
     * Mocks {@link AdServicesJobServiceLogger#getInstance()} to return a mocked logger.
     *
     * @deprecated Use {@link
     *     AdServicesJobMocker#mockGetAdServicesJobServiceLogger(AdServicesJobServiceLogger)}
     *     instead.
     */
    @Deprecated
    public static void mockGetAdServicesJobServiceLogger(AdServicesJobServiceLogger logger) {
        MockitoExpectations.jobMocker.mockGetAdServicesJobServiceLogger(logger);
    }

    /**
     * Verifies {@link ErrorLogUtil#e()} was called with the expected values.
     *
     * <p><b>Note: </b>you must call either {@link #doNothingOnErrorLogUtilError()} or {@link
     * #mockErrorLogUtilWithThrowable()} before the test calls {@link ErrorLogUtil#e(Throwable, int,
     * int)}.
     *
     * @deprecated Use {@link AdServicesLoggingUsageRule} to verify {@link ErrorLogUtil#e()} calls.
     *     To specify excepted calls with any exception, use {@link
     *     ExpectErrorLogUtilWithExceptionCall} along with {@link Any}.
     */
    @Deprecated
    public static void verifyErrorLogUtilErrorWithAnyException(int errorCode, int ppapiName) {
        verifyErrorLogUtilErrorWithAnyException(errorCode, ppapiName, times(1));
    }

    /**
     * Verifies {@link ErrorLogUtil#e()} was called with the expected values, using Mockito's {@link
     * VerificationMode} to set the number of times (like {@code times(2)} or {@code never}).
     *
     * <p><b>Note: </b>you must call either {@link #doNothingOnErrorLogUtilError()} or {@link
     * #mockErrorLogUtilWithThrowable()} before the test calls {@link ErrorLogUtil#e(Throwable, int,
     * int)}.
     *
     * @deprecated Use {@link AdServicesLoggingUsageRule} to verify {@link ErrorLogUtil#e()} calls.
     *     To specify excepted calls with any exception, use {@link
     *     ExpectErrorLogUtilWithExceptionCall} along with {@link Any} as the throwable parameter.
     */
    @Deprecated
    public static void verifyErrorLogUtilErrorWithAnyException(
            int errorCode, int ppapiName, VerificationMode mode) {
        verify(() -> ErrorLogUtil.e(any(), eq(errorCode), eq(ppapiName)), mode);
    }

    /**
     * Verifies {@link ErrorLogUtil#e()} was called with the expected values.
     *
     * <p><b>Note: </b>you must call either {@link #doNothingOnErrorLogUtilError()} or {@link
     * #mockErrorLogUtilWithThrowable()} before the test calls {@link ErrorLogUtil#e(Throwable, int,
     * int)}.
     *
     * @deprecated Use {@link AdServicesLoggingUsageRule} to verify {@link ErrorLogUtil#e()} calls.
     *     Use {@link ExpectErrorLogUtilWithExceptionCall} to specify expected calls with exception.
     */
    @Deprecated
    public static void verifyErrorLogUtilError(Throwable throwable, int errorCode, int ppapiName) {
        verifyErrorLogUtilError(throwable, errorCode, ppapiName, times(1));
    }

    /**
     * Verifies {@link ErrorLogUtil#e()} was called with the expected values, using Mockito's {@link
     * VerificationMode} to set the number of times (like {@code times(2)} or {@code never}).
     *
     * <p><b>Note: </b>you must call either {@link #doNothingOnErrorLogUtilError()} or {@link
     * #mockErrorLogUtilWithThrowable()} before the test calls {@link ErrorLogUtil#e(Throwable, int,
     * int)}.
     *
     * @deprecated Use {@link AdServicesLoggingUsageRule} to verify {@link ErrorLogUtil#e()} calls.
     *     Use {@link ExpectErrorLogUtilWithExceptionCall} to specify expected calls with exception.
     */
    @Deprecated
    public static void verifyErrorLogUtilError(
            Throwable throwable, int errorCode, int ppapiName, VerificationMode mode) {
        verify(() -> ErrorLogUtil.e(throwable, errorCode, ppapiName), mode);
    }

    /**
     * Verifies {@link ErrorLogUtil#e()} was called with the expected values.
     *
     * <p><b>Note: </b>you must call either {@link #doNothingOnErrorLogUtilError()} or {@link
     * #mockErrorLogUtilWithoutThrowable()} before the test calls {@link ErrorLogUtil#e(int, int)}.
     *
     * @deprecated Use {@link AdServicesLoggingUsageRule} to verify {@link ErrorLogUtil#e()} calls.
     *     Use {@link ExpectErrorLogUtilCall} to specify expected calls without exception.
     */
    @Deprecated
    public static void verifyErrorLogUtilError(int errorCode, int ppapiName) {
        verify(() -> ErrorLogUtil.e(errorCode, ppapiName));
    }

    /**
     * Verifies {@link ErrorLogUtil#e()} was called with the expected values, using Mockito's {@link
     * VerificationMode} to set the number of times (like {@code times(2)} or {@code never}).
     *
     * <p><b>Note: </b>you must call either {@link #doNothingOnErrorLogUtilError()} or {@link
     * #mockErrorLogUtilWithoutThrowable()} before the test calls {@link ErrorLogUtil#e(int, int)}.
     *
     * @deprecated Use {@link AdServicesLoggingUsageRule} to verify {@link ErrorLogUtil#e()} calls.
     *     Use {@link ExpectErrorLogUtilCall} to specify expected calls without exception.
     */
    @Deprecated
    public static void verifyErrorLogUtilError(
            int errorCode, int ppapiName, VerificationMode mode) {
        verify(() -> ErrorLogUtil.e(errorCode, ppapiName), mode);
    }

    /**
     * {@code SyncCallback} used in conjunction with {@link #mockErrorLogUtilWithoutThrowable()} /
     * {@link #mockErrorLogUtilWithThrowable()}.
     */
    public static final class ErrorLogUtilCallback
            extends FailableResultSyncCallback<ErrorLogUtilInvocation, Exception> {

        /**
         * Asserts {@link ErrorLogUtil#e(Throwable, int, int)}) was called with the given values.
         */
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
    }

    private static final class ErrorLogUtilInvocation {
        @Nullable public final Throwable throwable;
        public final int errorCode;
        public final int ppapiName;

        ErrorLogUtilInvocation(Throwable throwable, int errorCode, int ppapiName) {
            this.throwable = throwable;
            this.errorCode = errorCode;
            this.ppapiName = ppapiName;
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, ppapiName, throwable);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            ErrorLogUtilInvocation other = (ErrorLogUtilInvocation) obj;
            return errorCode == other.errorCode
                    && ppapiName == other.ppapiName
                    && Objects.equals(throwable, other.throwable);
        }

        @Override
        public String toString() {
            return "ErrorLogUtilInvocation [exception="
                    + throwable
                    + ", errorCode="
                    + errorCode
                    + ", ppapiName="
                    + ppapiName
                    + "]";
        }
    }

    private ExtendedMockitoExpectations() {
        throw new UnsupportedOperationException("Provides only static methods");
    }
}
