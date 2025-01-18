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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.junit.Test;
import org.junit.runner.Description;
import org.mockito.Mock;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class AbstractLogVerifierTest extends SharedMockitoTestCase {
    private static final String HEADER =
            "Detected mismatch in logging calls between expected and actual:\n";
    private static final String EXPECTED_CALLS_PREFIX = "Expected Calls:\n" + "[\n";
    private static final String ACTUAL_CALLS_PREFIX = "Actual Calls:\n" + "[\n";
    private static final String RESOLUTION_MESSAGE = "Use appropriate annotations over test method";

    @Mock private Description mDescription;

    @Test
    public void testValidateTimes_withLessThanZeroTimes_throwsException() {
        // Init verifier with expected and actual log calls
        TestLogVerifier testLogVerifier =
                new TestLogVerifier(
                        /* expectedLogCalls= */ ImmutableSet.of(),
                        /* actualLogCalls= */ ImmutableSet.of());

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> testLogVerifier.validateTimes(-1, "TestAnnotation"));

        expect.that(exception)
                .hasMessageThat()
                .contains("Detected @TestAnnotation with times < 0!");
    }

    @Test
    public void testValidateTimes_withZeroTimes_throwsException() {
        // Init verifier with expected and actual log calls
        TestLogVerifier testLogVerifier =
                new TestLogVerifier(
                        /* expectedLogCalls= */ ImmutableSet.of(),
                        /* actualLogCalls= */ ImmutableSet.of());

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> testLogVerifier.validateTimes(0, "TestAnnotation"));

        expect.that(exception)
                .hasMessageThat()
                .contains(
                        "Detected @TestAnnotation with times = 0. Remove annotation as the test"
                                + " will automatically fail if any log calls are detected.");
    }

    @Test
    public void testValidateTimes_withGreaterThanZeroTimes_throwsNoException() {
        // Init verifier with expected and actual log calls
        TestLogVerifier testLogVerifier =
                new TestLogVerifier(
                        /* expectedLogCalls= */ ImmutableSet.of(),
                        /* actualLogCalls= */ ImmutableSet.of());
        testLogVerifier.validateTimes(1, "TestAnnotation");

        testLogVerifier.verify(mDescription);
    }

    @Test
    public void testVerify_withNoExpectedAndActualCalls_throwsNoException() {
        // Init verifier with expected and actual log calls
        TestLogVerifier testLogVerifier =
                new TestLogVerifier(
                        /* expectedLogCalls= */ ImmutableSet.of(),
                        /* actualLogCalls= */ ImmutableSet.of());

        testLogVerifier.verify(mDescription);
    }

    @Test
    public void testVerify_withMatchingExpectedAndActualCalls_throwsNoException() {
        // Init verifier with expected and actual log calls
        Set<TestLogCall> expectedCalls =
                ImmutableSet.of(
                        new TestLogCall(/* times= */ 2, /* param= */ 2),
                        new TestLogCall(/* times= */ 1, /* param= */ 3));
        Set<TestLogCall> actualLogCalls =
                ImmutableSet.of(
                        new TestLogCall(/* times= */ 1, /* param= */ 3),
                        new TestLogCall(/* times= */ 2, /* param= */ 2));
        TestLogVerifier testLogVerifier = new TestLogVerifier(expectedCalls, actualLogCalls);

        testLogVerifier.verify(mDescription);
    }

    @Test
    public void testVerify_withNoExpectedAndNonEmptyActualCalls_throwsException() {
        // Init verifier with expected and actual log calls
        Set<TestLogCall> actualLogCalls =
                ImmutableSet.of(
                        new TestLogCall(/* times= */ 2, /* param= */ 2),
                        new TestLogCall(/* times= */ 1, /* param= */ 3));
        TestLogVerifier testLogVerifier =
                new TestLogVerifier(/* expectedLogCalls= */ ImmutableSet.of(), actualLogCalls);

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
        // Init verifier with expected and actual log calls
        Set<TestLogCall> expectedLogCalls =
                ImmutableSet.of(
                        new TestLogCall(/* times= */ 2, /* param= */ 2),
                        new TestLogCall(/* times= */ 1, /* param= */ 3));
        TestLogVerifier testLogVerifier =
                new TestLogVerifier(expectedLogCalls, /* actualLogCalls= */ ImmutableSet.of());

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
        // Init verifier with expected and actual log calls
        Set<TestLogCall> expectedCalls =
                ImmutableSet.of(
                        new TestLogCall(/* times= */ 3, /* param= */ 2),
                        new TestLogCall(/* times= */ 2, /* param= */ 4));
        Set<TestLogCall> actualLogCalls =
                ImmutableSet.of(
                        new TestLogCall(/* times= */ 3, /* param= */ 2),
                        new TestLogCall(/* times= */ 2, /* param= */ 4),
                        new TestLogCall(/* times= */ 2, /* param= */ 5));
        TestLogVerifier testLogVerifier = new TestLogVerifier(expectedCalls, actualLogCalls);

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
        // Init verifier with expected and actual log calls
        Set<TestLogCall> expectedLogCalls =
                ImmutableSet.of(
                        new TestLogCall(/* times= */ 2, /* param= */ 1),
                        new TestLogCall(/* times= */ 4, /* param= */ 2),
                        new TestLogCall(/* times= */ 1, /* param= */ 3));
        Set<TestLogCall> actualLogCalls =
                ImmutableSet.of(
                        new TestLogCall(/* times= */ 2, /* param= */ 1),
                        new TestLogCall(/* times= */ 1, /* param= */ 2));
        TestLogVerifier testLogVerifier = new TestLogVerifier(expectedLogCalls, actualLogCalls);

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
        // Init verifier with expected and actual log calls
        Set<TestLogCall> expectedLogCalls =
                ImmutableSet.of(new TestLogCall(/* times= */ 2, /* param= */ TestLogCall.ANY));
        Set<TestLogCall> actualLogCalls =
                ImmutableSet.of(
                        new TestLogCall(/* times= */ 1, /* param= */ 2),
                        new TestLogCall(/* times= */ 1, /* param= */ 5));
        TestLogVerifier testLogVerifier = new TestLogVerifier(expectedLogCalls, actualLogCalls);

        testLogVerifier.verify(mDescription);
    }

    @Test
    public void testVerify_withIdenticalExpectedInvocationsMatchesActual_throwsNoException() {
        // Init verifier with expected and actual log calls
        Set<TestLogCall> expectedLogCalls =
                ImmutableSet.of(
                        new TestLogCall(/* times= */ 1, /* param= */ 4),
                        new TestLogCall(/* times= */ 2, /* param= */ TestLogCall.ANY));
        Set<TestLogCall> actualLogCalls =
                ImmutableSet.of(
                        new TestLogCall(/* times= */ 2, /* param= */ 2),
                        new TestLogCall(/* times= */ 1, /* param= */ 4));
        TestLogVerifier testLogVerifier = new TestLogVerifier(expectedLogCalls, actualLogCalls);

        testLogVerifier.verify(mDescription);
    }

    @Test
    public void testVerify_withActualCallMatchingAnyAndIdenticalExpectedCall_throwsNoException() {
        // Init verifier with expected and actual log calls
        Set<TestLogCall> expectedLogCalls =
                ImmutableSet.of(
                        new TestLogCall(/* times= */ 1, /* param= */ TestLogCall.ANY),
                        new TestLogCall(/* times= */ 2, /* param= */ 5));
        Set<TestLogCall> actualLogCalls =
                ImmutableSet.of(
                        new TestLogCall(/* times= */ 2, /* param= */ 5),
                        new TestLogCall(/* times= */ 1, /* param= */ 4));
        TestLogVerifier testLogVerifier = new TestLogVerifier(expectedLogCalls, actualLogCalls);

        testLogVerifier.verify(mDescription);
    }

    @Test
    public void testVerify_withLongActualExpectedList_throwsNoException() {
        // Init verifier with expected and actual log calls
        Set<TestLogCall> expectedLogCalls =
                ImmutableSet.of(
                        new TestLogCall(/* times= */ 2, /* param= */ 10),
                        new TestLogCall(/* times= */ 1, /* param= */ 6),
                        new TestLogCall(/* times= */ 3, /* param= */ TestLogCall.ANY),
                        new TestLogCall(/* times= */ 1, /* param= */ 5));
        Set<TestLogCall> actualLogCalls =
                ImmutableSet.of(
                        new TestLogCall(/* times= */ 3, /* param= */ 10),
                        new TestLogCall(/* times= */ 1, /* param= */ 6),
                        new TestLogCall(/* times= */ 2, /* param= */ 100),
                        new TestLogCall(/* times= */ 1, /* param= */ 5));
        TestLogVerifier testLogVerifier = new TestLogVerifier(expectedLogCalls, actualLogCalls);

        testLogVerifier.verify(mDescription);
    }

    @Test
    public void testVerify_withAdditionalAnyCalls_throwsException() {
        // Init verifier with expected and actual log calls
        Set<TestLogCall> expectedLogCalls =
                ImmutableSet.of(new TestLogCall(/* times= */ 2, /* param= */ TestLogCall.ANY));
        Set<TestLogCall> actualLogCalls =
                ImmutableSet.of(
                        new TestLogCall(/* times= */ 2, /* param= */ 5),
                        new TestLogCall(/* times= */ 1, /* param= */ 4));
        TestLogVerifier testLogVerifier = new TestLogVerifier(expectedLogCalls, actualLogCalls);

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
        // Init verifier with expected and actual log calls
        Set<TestLogCall> expectedLogCalls =
                ImmutableSet.of(
                        new TestLogCall(/* times= */ 2, /* param= */ 20),
                        new TestLogCall(/* times= */ 1, /* param= */ TestLogCall.ANY));
        Set<TestLogCall> actualLogCalls =
                ImmutableSet.of(new TestLogCall(/* times= */ 4, /* param= */ 20));
        TestLogVerifier testLogVerifier = new TestLogVerifier(expectedLogCalls, actualLogCalls);

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
        // Init verifier with expected and actual log calls
        Set<TestLogCall> expectedLogCalls =
                ImmutableSet.of(
                        new TestLogCall(/* times= */ 1, /* param= */ 10),
                        new TestLogCall(/* times= */ 3, /* param= */ 20),
                        new TestLogCall(/* times= */ 2, /* param= */ TestLogCall.ANY));
        Set<TestLogCall> actualLogCalls =
                ImmutableSet.of(
                        new TestLogCall(/* times= */ 4, /* param= */ 20),
                        new TestLogCall(/* times= */ 4, /* param= */ 75));
        TestLogVerifier testLogVerifier = new TestLogVerifier(expectedLogCalls, actualLogCalls);

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

    @Test
    public void testDedupeCalls() {
        TestLogVerifier testLogVerifier = new TestLogVerifier(ImmutableSet.of(), ImmutableSet.of());

        List<TestLogCall> rawCalls =
                ImmutableList.of(
                        new TestLogCall(/* times= */ 1, /* param= */ 10),
                        new TestLogCall(/* times= */ 1, /* param= */ 11),
                        new TestLogCall(/* times= */ 1, /* param= */ 10));

        List<TestLogCall> dedupedCalls =
                testLogVerifier.dedupeCalls(rawCalls).stream().collect(Collectors.toList());

        // Need to verify "times" manually because times field is not baked into equals
        // definition for log calls.
        expect.that(dedupedCalls).hasSize(2);
        expect.that(dedupedCalls.get(0).mParam).isEqualTo(10);
        expect.that(dedupedCalls.get(0).mTimes).isEqualTo(2);
        expect.that(dedupedCalls.get(1).mParam).isEqualTo(11);
        expect.that(dedupedCalls.get(1).mTimes).isEqualTo(1);
    }

    // Simple log verifier for testing
    private static class TestLogVerifier extends AbstractLogVerifier<TestLogCall> {
        private final Set<TestLogCall> mExpectedLogCalls;
        private final Set<TestLogCall> mActualLogCalls;

        TestLogVerifier(Set<TestLogCall> expectedLogCalls, Set<TestLogCall> actualLogCalls) {
            mExpectedLogCalls = expectedLogCalls;
            mActualLogCalls = actualLogCalls;
        }

        @Override
        protected void mockLogCalls(Description description) {}

        @Override
        protected Set<TestLogCall> getExpectedLogCalls(Description description) {
            return mExpectedLogCalls;
        }

        @Override
        protected Set<TestLogCall> getActualLogCalls() {
            return mActualLogCalls;
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
