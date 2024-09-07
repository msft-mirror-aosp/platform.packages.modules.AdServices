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
    @Test
    public void testToString() {
        String toStr = new TestLogCall(/* val= */ true).toString();
        expect.that(toStr).isEqualTo("TestLogCall.log(true), times = 1");
    }

    @Test
    public void testIsEquivalentInvocation_withEqualsReturningFalse_returnsFalse() {
        TestLogCall logCall1 = new TestLogCall(/* val= */ true);
        TestLogCall logCall2 = new TestLogCall(/* val= */ false);

        expect.that(logCall1.isEquivalentInvocation(logCall2)).isFalse();
        expect.that(logCall2.isEquivalentInvocation(logCall1)).isFalse();
    }

    @Test
    public void testIsEquivalentInvocation_withEqualsReturningTrue_returnsTrue() {
        TestLogCall logCall1 = new TestLogCall(/* val= */ true);
        TestLogCall logCall2 = new TestLogCall(/* val= */ true);

        expect.that(logCall1.isEquivalentInvocation(logCall2)).isTrue();
        expect.that(logCall2.isEquivalentInvocation(logCall1)).isTrue();
    }

    // Test log call object that mocks a log call to log a boolean
    private static final class TestLogCall extends LogCall {
        private final boolean mVal;

        TestLogCall(boolean val) {
            super();
            mVal = val;
        }

        @Override
        public boolean equals(Object o) {
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
