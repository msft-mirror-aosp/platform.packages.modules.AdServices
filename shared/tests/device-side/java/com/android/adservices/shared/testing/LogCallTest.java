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

package com.android.adservices.shared.testing;

import com.android.adservices.shared.SharedUnitTestCase;

import org.junit.Test;

import java.util.Objects;

public final class LogCallTest extends SharedUnitTestCase {
    private final EqualsTester mEqualsTester = new EqualsTester(expect);

    @Test
    public void testEquals_withNullObject_returnsFalse() {
        TestLogCall logCall = new TestLogCall(/* times= */ 10, /* val= */ true);

        mEqualsTester.expectObjectsAreNotEqual(logCall, null);
    }

    @Test
    public void testEquals_withNonLogCallObject_returnsFalse() {
        TestLogCall logCall = new TestLogCall(/* times= */ 10, /* val= */ true);

        mEqualsTester.expectObjectsAreNotEqual(logCall, "");
    }

    @Test
    public void testEquals_withDifferentInvocationSameTimes_returnsFalse() {
        TestLogCall logCall1 = new TestLogCall(/* times= */ 10, /* val= */ false);
        TestLogCall logCall2 = new TestLogCall(/* times= */ 10, /* val= */ true);

        mEqualsTester.expectObjectsAreNotEqual(logCall1, logCall2);
    }

    @Test
    public void testEquals_withSameInvocationDifferentTimes_returnsFalse() {
        TestLogCall logCall1 = new TestLogCall(/* times= */ 10, /* val= */ false);
        TestLogCall logCall2 = new TestLogCall(/* times= */ 9, /* val= */ false);

        mEqualsTester.expectObjectsAreNotEqual(logCall1, logCall2);
    }

    @Test
    public void testEquals_withIdenticalObject_returnsTrue() {
        TestLogCall logCall1 = new TestLogCall(/* times= */ 10, /* val= */ true);

        mEqualsTester.expectObjectsAreEqual(logCall1, logCall1);
    }

    @Test
    public void testEquals_withSameInvocationAndTimes_returnsTrue() {
        TestLogCall logCall1 = new TestLogCall(/* times= */ 10, /* val= */ true);
        TestLogCall logCall2 = new TestLogCall(/* times= */ 10, /* val= */ true);

        mEqualsTester.expectObjectsAreEqual(logCall1, logCall2);
    }

    @Test
    public void testToString() {
        String toStr = new TestLogCall(/* times= */ 2, /* val= */ true).toString();
        expect.that(toStr).isEqualTo("TestLogCall.log(true), times = 2");
    }

    // Test log call object that mocks a log call to log a boolean
    private static final class TestLogCall extends LogCall {
        private final boolean mVal;

        TestLogCall(int times, boolean val) {
            super(times);
            mVal = val;
        }

        @Override
        public boolean isIdenticalInvocation(LogCall o) {
            if (!(o instanceof TestLogCall other)) {
                return false;
            }

            return mVal == other.mVal;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mVal);
        }

        @Override
        public String logInvocationToString() {
            return "TestLogCall.log(" + mVal + ")";
        }
    }
}
