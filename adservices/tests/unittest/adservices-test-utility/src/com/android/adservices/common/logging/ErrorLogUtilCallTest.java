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

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.LogCall;

import org.junit.Test;

public final class ErrorLogUtilCallTest extends AdServicesUnitTestCase {
    @Test
    public void testIsIdenticalInvocation_withNonLogCallObject_returnsFalse() {
        ErrorLogUtilCall errorLogUtilCall =
                new ErrorLogUtilCall(IllegalArgumentException.class, 10, 5, 1);
        TestLogCall testLogCall = new TestLogCall(10);

        expect.that(errorLogUtilCall.isIdenticalInvocation(testLogCall)).isFalse();
    }

    @Test
    public void testIsIdenticalInvocation_withDifferentInvocation_returnsFalseDifferentHashCode() {
        ErrorLogUtilCall errorLogUtilCall1 =
                new ErrorLogUtilCall(IllegalArgumentException.class, 10, 5, 1);
        ErrorLogUtilCall errorLogUtilCall2 =
                new ErrorLogUtilCall(IllegalArgumentException.class, 10, 4, 1);

        expect.that(errorLogUtilCall1.isIdenticalInvocation(errorLogUtilCall2)).isFalse();
        expect.that(errorLogUtilCall1.hashCode()).isNotEqualTo(errorLogUtilCall2.hashCode());
    }

    @Test
    public void testIsIdenticalInvocation_withSameInvocation_returnsTrueSameHashCode() {
        ErrorLogUtilCall errorLogUtilCall1 =
                new ErrorLogUtilCall(IllegalArgumentException.class, 10, 5, 1);
        ErrorLogUtilCall errorLogUtilCall2 =
                new ErrorLogUtilCall(IllegalArgumentException.class, 10, 5, 1);

        expect.that(errorLogUtilCall1.isIdenticalInvocation(errorLogUtilCall2)).isTrue();
        expect.that(errorLogUtilCall1.hashCode()).isEqualTo(errorLogUtilCall2.hashCode());
    }

    private static final class TestLogCall extends LogCall {
        TestLogCall(int times) {
            super(times);
        }

        @Override
        public boolean isIdenticalInvocation(LogCall other) {
            return false;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }
}
