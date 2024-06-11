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
import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.EqualsTester;
import com.android.adservices.shared.testing.LogCall;

import org.junit.Test;

public final class ErrorLogUtilCallTest extends AdServicesUnitTestCase {
    private final EqualsTester mEqualsTester = new EqualsTester(expect);

    @Test
    public void testCreateWithNoException() {
        ErrorLogUtilCall actual = ErrorLogUtilCall.createWithNoException(1, 2, 3);

        expect.that(actual).isEqualTo(new ErrorLogUtilCall(None.class, 1, 2, 3));
        expect.that(actual.mTimes).isEqualTo(3);
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
