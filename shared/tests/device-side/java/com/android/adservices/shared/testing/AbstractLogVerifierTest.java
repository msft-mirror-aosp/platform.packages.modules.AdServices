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

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.SharedMockitoTestCase;

import com.google.common.collect.ImmutableSet;

import org.junit.Test;
import org.junit.runner.Description;
import org.mockito.Mock;

import java.util.Objects;
import java.util.Set;

public final class AbstractLogVerifierTest extends SharedMockitoTestCase {
    private static final String HEADER =
            "Detected mismatch in logging calls between expected and actual:\n";
    private static final String EXPECTED_CALLS_PREFIX = "Expected Calls:\n" + "[\n";
    private static final String ACTUAL_CALLS_PREFIX = "Actual Calls:\n" + "[\n";
    private static final String RESOLUTION_MESSAGE = "Use appropriate annotations over test method";

    @Mock private Description mDescription;

    @Test
    public void testValidateTimes_withLessThanZeroTimes_throwsException() {
        TestLogVerifier testLogVerifier = new TestLogVerifier(ImmutableSet.of());

        Exception exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> testLogVerifier.validateTimes(-1, "TestAnnotation"));

        expect.that(exception)
                .hasMessageThat()
                .contains("Detected @TestAnnotation with times < 0!");
    }

    @Test
    public void testValidateTimes_withZeroTimes_throwsException() {
        TestLogVerifier testLogVerifier = new TestLogVerifier(ImmutableSet.of());

        Exception exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> testLogVerifier.validateTimes(0, "TestAnnotation"));

        expect.that(exception)
                .hasMessageThat()
                .contains(
                        "Detected @TestAnnotation with times = 0. Remove annotation as the test"
                                + " will automatically fail if any log calls are detected.");
    }

    @Test
    public void testValidateTimes_withGreaterThanZeroTimes_throwsNoException() {
        TestLogVerifier testLogVerifier = new TestLogVerifier(ImmutableSet.of());
        testLogVerifier.validateTimes(1, "TestAnnotation");

        testLogVerifier.verify(mDescription);
    }

    @Test
    public void testVerify_withNoExpectedAndActualCalls_throwsNoException() {
        TestLogVerifier testLogVerifier = new TestLogVerifier(ImmutableSet.of());
        testLogVerifier.verify(mDescription);
    }

    @Test
    public void testVerify_withMatchingExpectedAndActualCalls_throwsNoException() {
        // Init verifier with expected calls
        TestLogVerifier testLogVerifier =
                new TestLogVerifier(
                        ImmutableSet.of(
                                new TestLogCall(/* times= */ 2, /* param= */ 2),
                                new TestLogCall(/* times= */ 1, /* param= */ 3)));

        // Record one actual call at a time to mimic mocking of log call invocations
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 2));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 3));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 2));

        testLogVerifier.verify(mDescription);
    }

    @Test
    public void testVerify_withNoExpectedAndNonEmptyActualCalls_throwsException() {
        // Init verifier with expected calls
        TestLogVerifier testLogVerifier = new TestLogVerifier(ImmutableSet.of());

        // Record one actual call at a time to mimic mocking of log call invocations
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 2));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 3));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 2));

        Exception exception =
                assertThrows(
                        IllegalStateException.class, () -> testLogVerifier.verify(mDescription));

        // Build error message
        StringBuilder expectedErrorMessage = new StringBuilder();
        // Header message
        expectedErrorMessage.append(HEADER);
        // List empty expected calls
        expectedErrorMessage.append(EXPECTED_CALLS_PREFIX).append("]\n");
        // List actual calls
        expectedErrorMessage
                .append(ACTUAL_CALLS_PREFIX)
                .append("\tTestLogCall(2), times = 2\n")
                .append("\tTestLogCall(3), times = 1\n]\n");

        expect.that(exception).hasMessageThat().contains(expectedErrorMessage);
        expect.that(exception).hasMessageThat().contains(RESOLUTION_MESSAGE);
    }

    @Test
    public void testVerify_withNoActualAndNonEmptyExpectedCalls_throwsException() {
        // Init verifier with expected calls
        TestLogVerifier testLogVerifier =
                new TestLogVerifier(
                        ImmutableSet.of(
                                new TestLogCall(/* times= */ 2, /* param= */ 2),
                                new TestLogCall(/* times= */ 1, /* param= */ 3)));

        Exception exception =
                assertThrows(
                        IllegalStateException.class, () -> testLogVerifier.verify(mDescription));

        // Build error message
        StringBuilder expectedErrorMessage = new StringBuilder();
        // Header message
        expectedErrorMessage.append(HEADER);
        // List expected calls
        expectedErrorMessage
                .append(EXPECTED_CALLS_PREFIX)
                .append("\tTestLogCall(2), times = 2\n")
                .append("\tTestLogCall(3), times = 1\n]\n");
        // List empty actual calls
        expectedErrorMessage.append(ACTUAL_CALLS_PREFIX).append("]");

        expect.that(exception).hasMessageThat().contains(expectedErrorMessage);
        expect.that(exception).hasMessageThat().contains(RESOLUTION_MESSAGE);
    }

    @Test
    public void testVerify_withSomeCommonButAdditionalActualCalls_throwsException() {
        // Init verifier with expected calls
        TestLogVerifier testLogVerifier =
                new TestLogVerifier(
                        ImmutableSet.of(
                                new TestLogCall(/* times= */ 3, /* param= */ 2),
                                new TestLogCall(/* times= */ 2, /* param= */ 4)));

        // Record one actual call at a time to mimic mocking of log call invocations
        // Same as expected calls
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 2));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 2));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 2));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 4));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 4));
        // Additional calls
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 5));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 5));

        Exception exception =
                assertThrows(
                        IllegalStateException.class, () -> testLogVerifier.verify(mDescription));

        // Build error message
        StringBuilder expectedErrorMessage = new StringBuilder();
        // Header message
        expectedErrorMessage.append(HEADER);
        // List expected calls
        expectedErrorMessage
                .append(EXPECTED_CALLS_PREFIX)
                .append("\tTestLogCall(2), times = 3\n")
                .append("\tTestLogCall(4), times = 2\n]\n");
        // List actual calls
        expectedErrorMessage
                .append(ACTUAL_CALLS_PREFIX)
                .append("\tTestLogCall(2), times = 3\n")
                .append("\tTestLogCall(4), times = 2\n")
                .append("\tTestLogCall(5), times = 2\n]");

        expect.that(exception).hasMessageThat().contains(expectedErrorMessage);
        expect.that(exception).hasMessageThat().contains(RESOLUTION_MESSAGE);
    }

    @Test
    public void testVerify_withSomeCommonButAdditionalExpectedCalls_throwsException() {
        // Init verifier with expected calls
        TestLogVerifier testLogVerifier =
                new TestLogVerifier(
                        ImmutableSet.of(
                                new TestLogCall(/* times= */ 2, /* param= */ 1),
                                new TestLogCall(/* times= */ 4, /* param= */ 2),
                                new TestLogCall(/* times= */ 1, /* param= */ 3)));

        // Record one actual call at a time to mimic mocking of log call invocations
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 1));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 2));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 1));

        Exception exception =
                assertThrows(
                        IllegalStateException.class, () -> testLogVerifier.verify(mDescription));

        // Build error message
        StringBuilder expectedErrorMessage = new StringBuilder();
        // Header message
        expectedErrorMessage.append(HEADER);
        // List unexpected calls
        expectedErrorMessage
                .append(EXPECTED_CALLS_PREFIX)
                .append("\tTestLogCall(1), times = 2\n")
                .append("\tTestLogCall(2), times = 4\n")
                .append("\tTestLogCall(3), times = 1\n]\n");
        // List all actual calls
        expectedErrorMessage
                .append(ACTUAL_CALLS_PREFIX)
                .append("\tTestLogCall(1), times = 2\n")
                .append("\tTestLogCall(2), times = 1\n]");

        expect.that(exception).hasMessageThat().contains(expectedErrorMessage);
        expect.that(exception).hasMessageThat().contains(RESOLUTION_MESSAGE);
    }

    @Test
    public void testVerify_withAnyMatchingAllActualCalls_throwsNoException() {
        // Init verifier with expected calls
        TestLogVerifier testLogVerifier =
                new TestLogVerifier(
                        ImmutableSet.of(
                                new TestLogCall(/* times= */ 2, /* param= */ TestLogCall.ANY)));

        // Record one actual call at a time to mimic mocking of log call invocations
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 2));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 5));

        testLogVerifier.verify(mDescription);
    }

    @Test
    public void testVerify_withIdenticalExpectedInvocationsMatchesActual_throwsNoException() {
        // Init verifier with expected calls
        TestLogVerifier testLogVerifier =
                new TestLogVerifier(
                        ImmutableSet.of(
                                new TestLogCall(/* times= */ 1, /* param= */ 4),
                                new TestLogCall(/* times= */ 2, /* param= */ TestLogCall.ANY)));

        // Record one actual call at a time to mimic mocking of log call invocations
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 2));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 4));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 2));

        testLogVerifier.verify(mDescription);
    }

    @Test
    public void testVerify_withActualCallMatchingAnyAndIdenticalExpectedCall_throwsNoException() {
        // Init verifier with expected calls
        TestLogVerifier testLogVerifier =
                new TestLogVerifier(
                        ImmutableSet.of(
                                new TestLogCall(/* times= */ 1, /* param= */ TestLogCall.ANY),
                                new TestLogCall(/* times= */ 2, /* param= */ 5)));

        // Record one actual call at a time to mimic mocking of log call invocations
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 5));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 4));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 5));

        testLogVerifier.verify(mDescription);
    }

    @Test
    public void testVerify_withLongActualExpectedList_throwsNoException() {
        // Init verifier with expected calls
        TestLogVerifier testLogVerifier =
                new TestLogVerifier(
                        ImmutableSet.of(
                                new TestLogCall(/* times= */ 2, /* param= */ 10),
                                new TestLogCall(/* times= */ 1, /* param= */ 6),
                                new TestLogCall(/* times= */ 3, /* param= */ TestLogCall.ANY),
                                new TestLogCall(/* times= */ 1, /* param= */ 5)));

        // Record one actual call at a time to mimic mocking of log call invocations
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 10));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 6));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 100));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 10));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 5));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 100));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 10));

        testLogVerifier.verify(mDescription);
    }

    @Test
    public void testVerify_withAdditionalAnyCalls_throwsException() {
        // Init verifier with expected calls
        TestLogVerifier testLogVerifier =
                new TestLogVerifier(
                        ImmutableSet.of(
                                new TestLogCall(/* times= */ 2, /* param= */ TestLogCall.ANY)));

        // Record one actual call at a time to mimic mocking of log call invocations
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 5));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 4));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 5));

        Exception exception =
                assertThrows(
                        IllegalStateException.class, () -> testLogVerifier.verify(mDescription));

        // Build error message
        StringBuilder expectedErrorMessage = new StringBuilder();
        // Header message
        expectedErrorMessage.append(HEADER);
        // List unexpected calls
        expectedErrorMessage
                .append(EXPECTED_CALLS_PREFIX)
                .append("\tTestLogCall(*), times = 2\n]\n");
        // List all actual calls
        expectedErrorMessage
                .append(ACTUAL_CALLS_PREFIX)
                .append("\tTestLogCall(5), times = 2\n")
                .append("\tTestLogCall(4), times = 1\n]");

        expect.that(exception).hasMessageThat().contains(expectedErrorMessage);
        expect.that(exception).hasMessageThat().contains(RESOLUTION_MESSAGE);
    }

    @Test
    public void testVerify_withAdditionalAnyCallsWithActualCallMatchingMultiple_throwsException() {
        // Init verifier with expected calls
        TestLogVerifier testLogVerifier =
                new TestLogVerifier(
                        ImmutableSet.of(
                                new TestLogCall(/* times= */ 2, /* param= */ 20),
                                new TestLogCall(/* times= */ 1, /* param= */ TestLogCall.ANY)));

        // Record one actual call at a time to mimic mocking of log call invocations
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 20));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 20));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 20));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 20));

        Exception exception =
                assertThrows(
                        IllegalStateException.class, () -> testLogVerifier.verify(mDescription));

        // Build error message
        StringBuilder expectedErrorMessage = new StringBuilder();
        // Header message
        expectedErrorMessage.append(HEADER);
        // List unexpected calls
        expectedErrorMessage
                .append(EXPECTED_CALLS_PREFIX)
                .append("\tTestLogCall(20), times = 2\n")
                .append("\tTestLogCall(*), times = 1\n]\n");
        // List all actual calls
        expectedErrorMessage.append(ACTUAL_CALLS_PREFIX).append("\tTestLogCall(20), times = 4\n]");

        expect.that(exception).hasMessageThat().contains(expectedErrorMessage);
        expect.that(exception).hasMessageThat().contains(RESOLUTION_MESSAGE);
    }

    @Test
    public void testVerify_withMismatchInLongList_throwsException() {
        // Init verifier with expected calls
        TestLogVerifier testLogVerifier =
                new TestLogVerifier(
                        ImmutableSet.of(
                                new TestLogCall(/* times= */ 1, /* param= */ 10),
                                new TestLogCall(/* times= */ 3, /* param= */ 20),
                                new TestLogCall(/* times= */ 2, /* param= */ TestLogCall.ANY)));

        // Record one actual call at a time to mimic mocking of log call invocations
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 20));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 20));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 20));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 20));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 75));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 75));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 75));
        testLogVerifier.recordActualCall(new TestLogCall(/* times= */ 1, /* param= */ 75));

        Exception exception =
                assertThrows(
                        IllegalStateException.class, () -> testLogVerifier.verify(mDescription));

        // Build error message
        StringBuilder expectedErrorMessage = new StringBuilder();
        // Header message
        expectedErrorMessage.append(HEADER);
        // List unexpected calls
        expectedErrorMessage
                .append(EXPECTED_CALLS_PREFIX)
                .append("\tTestLogCall(10), times = 1\n")
                .append("\tTestLogCall(20), times = 3\n")
                .append("\tTestLogCall(*), times = 2\n]\n");
        // List all actual calls
        expectedErrorMessage
                .append(ACTUAL_CALLS_PREFIX)
                .append("\tTestLogCall(20), times = 4\n")
                .append("\tTestLogCall(75), times = 4\n]");

        expect.that(exception).hasMessageThat().contains(expectedErrorMessage);
        expect.that(exception).hasMessageThat().contains(RESOLUTION_MESSAGE);
    }

    // Simple log verifier for testing
    private static class TestLogVerifier extends AbstractLogVerifier<TestLogCall> {
        private final Set<TestLogCall> mExpectedLogCalls;

        TestLogVerifier(Set<TestLogCall> expectedLogCalls) {
            mExpectedLogCalls = expectedLogCalls;
        }

        @Override
        protected void mockLogCalls() {}

        @Override
        protected Set<TestLogCall> getExpectedLogCalls(Description description) {
            return mExpectedLogCalls;
        }

        @Override
        protected String getResolutionMessage() {
            return RESOLUTION_MESSAGE;
        }
    }

    // Simple log call that takes in an integer value for testing.
    private static final class TestLogCall extends LogCall {
        private static final int ANY = Integer.MIN_VALUE;
        private final int mParam;

        TestLogCall(int times, int param) {
            super(times);
            mParam = param;
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof TestLogCall other) && other.mParam == mParam;
        }

        @Override
        public boolean isEquivalentInvocation(LogCall o) {
            // Skip comparison of param if ANY is used in either log call
            return (o instanceof TestLogCall other)
                    && (mParam == ANY || other.mParam == ANY || other.mParam == mParam);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mParam);
        }

        @Override
        public String logInvocationToString() {
            return "TestLogCall(" + (mParam == ANY ? "*" : mParam) + ")";
        }
    }
}
