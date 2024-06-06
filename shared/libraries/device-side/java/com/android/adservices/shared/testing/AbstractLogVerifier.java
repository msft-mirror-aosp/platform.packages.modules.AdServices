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

import android.util.Log;

import org.junit.runner.Description;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Abstract log verifier to hold common logic across all log verifiers.
 *
 * @param <T> expected {@link LogCall} type to be verified
 */
public abstract class AbstractLogVerifier<T extends LogCall> implements LogVerifier {
    protected final String mTag = getClass().getSimpleName();

    // Key is the LogCall object, value is the source of truth for times; we do not keep track of
    // the times value in the LogCall object itself because we would like to group together all
    // identical log call invocations, irrespective of number of times they have been invoked.
    private final Map<T, Integer> mActualCalls = new LinkedHashMap<>();

    @Override
    public void setup() {
        mockLogCalls();
    }

    @Override
    public void verify(Description description) {
        // Obtain expected log call entries from subclass.
        Set<T> expectedCalls = getExpectedLogCalls(description);
        // Extract actual log calls entries from map that was built through mocking log calls.
        Set<T> actualCalls = getActualCalls();

        verifyCalls(expectedCalls, actualCalls);
    }

    /**
     * Mocks relevant calls in order to store metadata of log calls that were actually made.
     * Subclasses are expected to call recordActualCall to store actual calls.
     */
    protected abstract void mockLogCalls();

    /**
     * Return a set of {@link LogCall} to be verified.
     *
     * @param description test that was executed.
     */
    protected abstract Set<T> getExpectedLogCalls(Description description);

    /**
     * Returns relevant message providing information on how to use appropriate annotations when
     * test fails due to mismatch of expected and actual log calls.
     */
    protected abstract String getResolutionMessage();

    /**
     * Subclasses are expected to use this method to record actual log call. Assumes only a single
     * call is being recorded at once.
     *
     * @param actualCall Actual call to be recorded
     */
    public void recordActualCall(T actualCall) {
        mActualCalls.put(actualCall, mActualCalls.getOrDefault(actualCall, 0) + 1);
    }

    /**
     * Checks if a list of {@link LogCall} are unique in terms of invocation.
     *
     * <p>Note: {@link LogCall#isIdenticalInvocation(LogCall)} is used for invocation equality as it
     * does not take into account number of invocations (i.e. times), by definition.
     *
     * @param logCalls log calls to verify
     * @return {@code true} if all log call invocations are unique; {@code false} otherwise.
     */
    protected boolean containsUniqueLogInvocations(List<T> logCalls) {
        return IntStream.range(0, logCalls.size())
                .noneMatch(
                        i ->
                                IntStream.range(i + 1, logCalls.size())
                                        .anyMatch(
                                                n ->
                                                        logCalls.get(i)
                                                                .isIdenticalInvocation(
                                                                        logCalls.get(n))));
    }

    private void verifyCalls(Set<T> expectedCalls, Set<T> actualCalls) {
        Log.v(mTag, "Total expected calls: " + expectedCalls.size());
        Log.v(mTag, "Total actual calls: " + actualCalls.size());

        if (Objects.equals(expectedCalls, actualCalls)) {
            Log.v(mTag, "No unexpected logging calls!");
            return;
        }

        StringBuilder sb = new StringBuilder();

        // Check if there are any unverified expected calls, denoted via annotations.
        Set<T> clonedExpectedCalls = new LinkedHashSet<>(expectedCalls);
        clonedExpectedCalls.removeAll(actualCalls);

        if (!clonedExpectedCalls.isEmpty()) {
            sb.append("Detected logging calls that were expected but not actually made:\n");
            sb.append("Unexpected Calls:")
                    .append("\n[")
                    .append(callsToStr(clonedExpectedCalls))
                    .append("\n]\n");
            sb.append("Actual Calls:\n[").append(callsToStr(actualCalls)).append("\n]\n");
            throw new IllegalStateException(sb.toString());
        }

        // Check if there are any actual calls that were not expected via annotations.
        Set<T> clonedActualCalls = new LinkedHashSet<>(actualCalls);
        clonedActualCalls.removeAll(expectedCalls);
        sb.append(
                "Detected logging calls that were actually made but not expected via "
                        + "annotations:\n");
        sb.append("Unexpected Actual Calls:\n[")
                .append(callsToStr(clonedActualCalls))
                .append("\n]\n");
        sb.append("Expected Calls:")
                .append("\n[")
                .append(callsToStr(expectedCalls))
                .append("\n]\n");
        // Include info about annotation usage since something is wrong with expected log calls.
        sb.append(getResolutionMessage()).append('\n');

        throw new IllegalStateException(sb.toString());
    }

    private String callsToStr(Set<T> calls) {
        return calls.stream().map(call -> "\n\t" + call).reduce("", (a, b) -> a + b);
    }

    private Set<T> getActualCalls() {
        // At this point, the map of actual calls will no longer be updated. Therefore, it's safe
        // // to alter the times field in the actual LogCall objects and retrieve all keys.
        mActualCalls
                .keySet()
                .forEach(actualLogCall -> actualLogCall.mTimes = mActualCalls.get(actualLogCall));

        return mActualCalls.keySet();
    }
}
