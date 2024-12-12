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

import static com.android.adservices.common.logging.ErrorLogUtilCall.None;
import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall.UNDEFINED_INT_PARAM;
import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;
import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Undefined;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.shared.testing.EqualsTester;
import com.android.adservices.shared.testing.LogCall;

import org.junit.Test;
import org.mockito.Mock;

public final class ErrorLogUtilCallTest extends AdServicesMockitoTestCase {
    private final EqualsTester mEqualsTester = new EqualsTester(expect);

    @Mock private ExpectErrorLogUtilCall mExpectErrorLogUtilCall;
    @Mock private ExpectErrorLogUtilWithExceptionCall mExpectErrorLogUtilWithExceptionCall;
    @Mock private SetErrorLogUtilDefaultParams mSetErrorLogUtilDefaultParams;

    @Test
    public void testCreateWithNoException() {
        ErrorLogUtilCall actual = ErrorLogUtilCall.createWithNoException(1, 2, 3);

        expect.that(actual).isEqualTo(new ErrorLogUtilCall(None.class, 1, 2, 3));
        expect.that(actual.mTimes).isEqualTo(3);
    }

    @Test
    public void testCreateWithNullableThrowable_withNullThrowable_createsObject() {
        ErrorLogUtilCall actual = ErrorLogUtilCall.createWithNullableThrowable(null, 1, 2, 3);
        ErrorLogUtilCall expected = ErrorLogUtilCall.createWithNoException(1, 2, 3);

        expect.that(actual).isEqualTo(expected);
        expect.that(actual.mTimes).isEqualTo(3);
    }

    @Test
    public void testCreateWithNullableThrowable_withNonNullThrowable_createsObject() {
        ErrorLogUtilCall actual =
                ErrorLogUtilCall.createWithNullableThrowable(new RuntimeException(), 1, 2, 3);
        ErrorLogUtilCall expected = new ErrorLogUtilCall(RuntimeException.class, 1, 2, 3);

        expect.that(actual).isEqualTo(expected);
        expect.that(actual.mTimes).isEqualTo(3);
    }

    @Test
    public void testCreateFrom_withNoExceptionAnnotationNullDefault_createsObject() {
        mockAnnotationWithoutException(20, 30, 2);
        ErrorLogUtilCall expected = ErrorLogUtilCall.createWithNoException(20, 30, 2);

        ErrorLogUtilCall actual = ErrorLogUtilCall.createFrom(mExpectErrorLogUtilCall, null);

        expect.that(actual).isEqualTo(expected);
        expect.that(actual.mTimes).isEqualTo(2);
    }

    @Test
    public void testCreateFrom_withNoExceptionAnnotationDerivesFromDefaults_createsObject() {
        mockAnnotationWithoutException(UNDEFINED_INT_PARAM, UNDEFINED_INT_PARAM, 3);
        mockDefaultParams(Any.class, 40, 50);
        ErrorLogUtilCall expected = ErrorLogUtilCall.createWithNoException(40, 50, 3);

        ErrorLogUtilCall actual =
                ErrorLogUtilCall.createFrom(mExpectErrorLogUtilCall, mSetErrorLogUtilDefaultParams);

        expect.that(actual).isEqualTo(expected);
        expect.that(actual.mTimes).isEqualTo(3);
    }

    @Test
    public void testCreateFrom_withNoExceptionAnnotationMissingErrorCode_throwsException() {
        mockAnnotationWithoutException(UNDEFINED_INT_PARAM, 30, 3);

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ErrorLogUtilCall.createFrom(mExpectErrorLogUtilCall, null));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo("Cannot resolve errorCode for @ExpectErrorLogUtilCall");
    }

    @Test
    public void testCreateFrom_withNoExceptionAnnotationMissingPpapiName_throwsException() {
        mockAnnotationWithoutException(2, UNDEFINED_INT_PARAM, 3);
        mockDefaultParams(Any.class, 60, UNDEFINED_INT_PARAM);

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ErrorLogUtilCall.createFrom(mExpectErrorLogUtilCall, null));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo("Cannot resolve ppapiName for @ExpectErrorLogUtilCall");
    }

    @Test
    public void testCreateFrom_withExceptionAnnotationNullDefault_createsObject() {
        mockAnnotationWithException(Any.class, 20, 30, 2);
        ErrorLogUtilCall expected = new ErrorLogUtilCall(Any.class, 20, 30, 2);

        ErrorLogUtilCall actual =
                ErrorLogUtilCall.createFrom(mExpectErrorLogUtilWithExceptionCall, null);

        expect.that(actual).isEqualTo(expected);
        expect.that(actual.mTimes).isEqualTo(2);
    }

    @Test
    public void testCreateFrom_withExceptionAnnotationDerivesFromDefaults_createsObject() {
        mockAnnotationWithException(Undefined.class, UNDEFINED_INT_PARAM, UNDEFINED_INT_PARAM, 1);
        mockDefaultParams(Any.class, 40, 50);
        ErrorLogUtilCall expected = new ErrorLogUtilCall(Any.class, 40, 50, 2);

        ErrorLogUtilCall actual =
                ErrorLogUtilCall.createFrom(
                        mExpectErrorLogUtilWithExceptionCall, mSetErrorLogUtilDefaultParams);

        expect.that(actual).isEqualTo(expected);
        expect.that(actual.mTimes).isEqualTo(1);
    }

    @Test
    public void testCreateFrom_withExceptionAnnotationMissingThrowable_throwsException() {
        mockAnnotationWithException(Undefined.class, 40, 60, 3);
        mockDefaultParams(Undefined.class, 60, 70);

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                ErrorLogUtilCall.createFrom(
                                        mExpectErrorLogUtilWithExceptionCall, null));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo("Cannot resolve throwable for @ExpectErrorLogUtilWithExceptionCall");
    }

    @Test
    public void testCreateFrom_withExceptionAnnotationMissingErrorCode_throwsException() {
        mockAnnotationWithException(Any.class, UNDEFINED_INT_PARAM, 30, 3);

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                ErrorLogUtilCall.createFrom(
                                        mExpectErrorLogUtilWithExceptionCall, null));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo("Cannot resolve errorCode for @ExpectErrorLogUtilWithExceptionCall");
    }

    @Test
    public void testCreateFrom_withExceptionAnnotationMissingPpapiName_throwsException() {
        mockAnnotationWithException(Any.class, 40, UNDEFINED_INT_PARAM, 3);
        mockDefaultParams(IllegalArgumentException.class, 60, UNDEFINED_INT_PARAM);

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                ErrorLogUtilCall.createFrom(
                                        mExpectErrorLogUtilWithExceptionCall, null));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo("Cannot resolve ppapiName for @ExpectErrorLogUtilWithExceptionCall");
    }

    @Test
    public void testEquals_withNonLogCallObject_returnsFalse() {
        ErrorLogUtilCall errorLogUtilCall =
                new ErrorLogUtilCall(IllegalArgumentException.class, 10, 5);
        TestLogCall testLogCall = new TestLogCall();

        mEqualsTester.expectObjectsAreNotEqual(errorLogUtilCall, testLogCall);
    }

    @Test
    public void testEquals_withEquivalentObj_returnsTrue() {
        ErrorLogUtilCall errorLogUtilCall1 =
                new ErrorLogUtilCall(IllegalArgumentException.class, 10, 5);
        ErrorLogUtilCall errorLogUtilCall2 =
                new ErrorLogUtilCall(IllegalArgumentException.class, 10, 5);

        mEqualsTester.expectObjectsAreEqual(errorLogUtilCall1, errorLogUtilCall2);
    }

    @Test
    public void testEquals_withNonEquivalentObj_returnsFalse() {
        ErrorLogUtilCall errorLogUtilCall1 =
                new ErrorLogUtilCall(IllegalArgumentException.class, 10, 5);
        ErrorLogUtilCall errorLogUtilCall2 =
                new ErrorLogUtilCall(IllegalArgumentException.class, 5, 4);

        mEqualsTester.expectObjectsAreNotEqual(errorLogUtilCall1, errorLogUtilCall2);
    }

    @Test
    public void testIsEquivalentInvocation_withNonLogCallObject_returnsFalse() {
        ErrorLogUtilCall errorLogUtilCall =
                new ErrorLogUtilCall(IllegalArgumentException.class, 10, 5);
        TestLogCall testLogCall = new TestLogCall();

        expect.that(errorLogUtilCall.isEquivalentInvocation(testLogCall)).isFalse();
        expect.that(testLogCall.isEquivalentInvocation(errorLogUtilCall)).isFalse();
    }

    @Test
    public void testIsEquivalentInvocation_withDifferentApi_returnsFalse() {
        ErrorLogUtilCall errorLogUtilCall1 = new ErrorLogUtilCall(Any.class, 10, 5);
        ErrorLogUtilCall errorLogUtilCall2 = new ErrorLogUtilCall(None.class, 10, 5);

        expect.that(errorLogUtilCall1.isEquivalentInvocation(errorLogUtilCall2)).isFalse();
        expect.that(errorLogUtilCall2.isEquivalentInvocation(errorLogUtilCall1)).isFalse();
    }

    @Test
    public void testIsEquivalentInvocation_withDifferentExceptions_returnsFalse() {
        ErrorLogUtilCall errorLogUtilCall1 =
                new ErrorLogUtilCall(IllegalArgumentException.class, 10, 5);
        ErrorLogUtilCall errorLogUtilCall2 =
                new ErrorLogUtilCall(NullPointerException.class, 10, 5);

        expect.that(errorLogUtilCall1.isEquivalentInvocation(errorLogUtilCall2)).isFalse();
        expect.that(errorLogUtilCall2.isEquivalentInvocation(errorLogUtilCall1)).isFalse();
    }

    @Test
    public void testIsEquivalentInvocation_withSameExceptionsDifferentArgs_returnsFalse() {
        ErrorLogUtilCall errorLogUtilCall1 =
                new ErrorLogUtilCall(IllegalArgumentException.class, 10, 5);
        ErrorLogUtilCall errorLogUtilCall2 =
                new ErrorLogUtilCall(IllegalArgumentException.class, 10, 4);

        expect.that(errorLogUtilCall1.isEquivalentInvocation(errorLogUtilCall2)).isFalse();
        expect.that(errorLogUtilCall2.isEquivalentInvocation(errorLogUtilCall1)).isFalse();
    }

    @Test
    public void testIsEquivalentInvocation_withMatchExceptionUsingAny_returnsTrue() {
        ErrorLogUtilCall errorLogUtilCall1 = new ErrorLogUtilCall(Any.class, 10, 5);
        ErrorLogUtilCall errorLogUtilCall2 =
                new ErrorLogUtilCall(NullPointerException.class, 10, 5);

        expect.that(errorLogUtilCall1.isEquivalentInvocation(errorLogUtilCall2)).isTrue();
        expect.that(errorLogUtilCall2.isEquivalentInvocation(errorLogUtilCall1)).isTrue();

        // Equals will still be false
        mEqualsTester.expectObjectsAreNotEqual(errorLogUtilCall1, errorLogUtilCall2);
    }

    @Test
    public void testIsEquivalentInvocation_withNoExceptionLoggingSameArgs_returnsTrue() {
        ErrorLogUtilCall errorLogUtilCall1 = new ErrorLogUtilCall(None.class, 10, 5);
        ErrorLogUtilCall errorLogUtilCall2 = new ErrorLogUtilCall(None.class, 10, 5);

        expect.that(errorLogUtilCall1.isEquivalentInvocation(errorLogUtilCall2)).isTrue();
        expect.that(errorLogUtilCall2.isEquivalentInvocation(errorLogUtilCall1)).isTrue();
    }

    @Test
    public void testIsEquivalentInvocation_withNoExceptionLoggingDifferentArgs_returnsFalse() {
        ErrorLogUtilCall errorLogUtilCall1 = new ErrorLogUtilCall(None.class, 10, 5);
        ErrorLogUtilCall errorLogUtilCall2 = new ErrorLogUtilCall(None.class, 2, 5);

        expect.that(errorLogUtilCall1.isEquivalentInvocation(errorLogUtilCall2)).isFalse();
        expect.that(errorLogUtilCall2.isEquivalentInvocation(errorLogUtilCall1)).isFalse();
    }

    @Test
    public void testLogInvocationToString_withNoThrowable_returnsFormattedString() {
        ErrorLogUtilCall errorLogUtilCall = new ErrorLogUtilCall(None.class, 10, 5, 1);

        expect.that(errorLogUtilCall.logInvocationToString()).isEqualTo("ErrorLogUtil.e(10, 5)");
    }

    private void mockAnnotationWithoutException(int errorCode, int ppapiName, int times) {
        when(mExpectErrorLogUtilCall.errorCode()).thenReturn(errorCode);
        when(mExpectErrorLogUtilCall.ppapiName()).thenReturn(ppapiName);
        when(mExpectErrorLogUtilCall.times()).thenReturn(times);
    }

    private void mockAnnotationWithException(
            Class<? extends Throwable> throwable, int errorCode, int ppapiName, int times) {
        doReturn(throwable).when(mExpectErrorLogUtilWithExceptionCall).throwable();
        when(mExpectErrorLogUtilWithExceptionCall.errorCode()).thenReturn(errorCode);
        when(mExpectErrorLogUtilWithExceptionCall.ppapiName()).thenReturn(ppapiName);
        when(mExpectErrorLogUtilWithExceptionCall.times()).thenReturn(times);
    }

    private void mockDefaultParams(
            Class<? extends Throwable> throwable, int errorCode, int ppapiName) {
        doReturn(throwable).when(mSetErrorLogUtilDefaultParams).throwable();
        when(mSetErrorLogUtilDefaultParams.errorCode()).thenReturn(errorCode);
        when(mSetErrorLogUtilDefaultParams.ppapiName()).thenReturn(ppapiName);
    }

    private static final class TestLogCall extends LogCall {
        @Override
        public boolean equals(Object other) {
            return false;
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public String logInvocationToString() {
            return null;
        }
    }
}
