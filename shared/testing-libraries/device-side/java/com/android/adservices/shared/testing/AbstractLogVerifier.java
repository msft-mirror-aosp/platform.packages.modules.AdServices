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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Abstract log verifier to hold common logic across all log verifiers.
 *
 * @param <T> expected {@link LogCall} type to be verified
 */
public abstract class AbstractLogVerifier<T extends LogCall> implements LogVerifier {
    protected final String mTag = getClass().getSimpleName();

    @Override
    public void setup(Description description) {
        mockLogCalls(description);
    }

    @Override
    public void verify(Description description) {
        // Obtain expected log call entries from subclass.
        Set<T> expectedCalls = getExpectedLogCalls(description);
        // Obtain actual log call entries from subclass.
        Set<T> actualCalls = getActualLogCalls();

        verifyCalls(expectedCalls, actualCalls);
    }

    /**
     * Mocks relevant calls in order to store metadata of log calls that were actually made.
     * Subclasses are expected to call recordActualCall to store actual calls.
     *
     * @param description test that was executed
     */
    protected abstract void mockLogCalls(Description description);

    /**
     * Return a set of {@link LogCall} to be verified.
     *
     * @param description test that was executed
     */
    protected abstract Set<T> getExpectedLogCalls(Description description);

    /** Return a set of {@link LogCall} that were invoked. */
    protected abstract Set<T> getActualLogCalls();

    /**
     * Returns relevant message providing information on how to use appropriate annotations when
     * test fails due to mismatch of expected and actual log calls.
     */
    protected abstract String getResolutionMessage();

    /**
     * Ensures log call parameter is > 0 for a given annotation type. Throws exception otherwise.
     *
     * @param times times to validate
     * @param annotation name of annotation that holds the times value
     */
    public void validateTimes(int times, String annotation) {
        if (times == 0) {
            throw new IllegalArgumentException(
                    "Detected @"
                            + annotation
                            + " with times = 0. Remove annotation as the "
                            + "test will automatically fail if any log calls are detected.");
        }

        if (times < 0) {
            throw new IllegalArgumentException("Detected @" + annotation + " with times < 0!");
        }
    }

    /**
     * Returns a set of deduped log calls by updating the {@code mTimes} field of the duplicated log
     * call object.
     *
     * @param calls list of raw calls where each entry is assumed to represent exactly 1 call as
     *     represented by the {@code mTimes} field.
     */
    public Set<T> dedupeCalls(List<T> calls) {
        // Updating the mTimes field of a LogCall object is not expected to change the hash as
        // mTimes isn't supposed to be factored into the equals / hash definition by design.
        Map<T, T> frequencyMap = new LinkedHashMap<>();
        for (T call : calls) {
            if (!frequencyMap.containsKey(call)) {
                frequencyMap.put(call, call);
            } else {
                frequencyMap.get(call).mTimes++;
            }
        }

        return new LinkedHashSet<>(frequencyMap.values());
    }

    /**
     * Matching algorithm that detects any mismatches within expected and actual calls. This works
     * for verifying wild card argument cases as well.
     */
    private void verifyCalls(Set<T> expectedCalls, Set<T> actualCalls) {
        Log.v(mTag, "Total expected calls: " + expectedCalls.size());
        Log.v(mTag, "Total actual calls: " + actualCalls.size());

        if (!containsAll(expectedCalls, actualCalls) || !containsAll(actualCalls, expectedCalls)) {
            throw new IllegalStateException(constructErrorMessage(expectedCalls, actualCalls));
        }

        Log.v(mTag, "All log calls successfully verified!");
    }

    /**
     * "Brute-force" algorithm to verify if all the LogCalls in first set are present in the second
     * set.
     *
     * <p>To support wild card matching of parameters, we need to identify 1:1 matching of LogCalls
     * in both sets. This is achieved by scanning for matching calls using {@link
     * LogCall#equals(Object)} and then followed by {@link LogCall#isEquivalentInvocation(LogCall)}.
     * This way, log calls are matched exactly based on their parameters first before wild card.
     *
     * <p>Note: In reality, the size of the sets are expected to be very small, so performance
     * tuning isn't a major concern.
     */
    private boolean containsAll(Set<T> calls1, Set<T> calls2) {
        // Create wrapper objects so times can be altered safely.
        Set<MutableLogCall> mutableCalls1 = createMutableLogCalls(calls1);
        Set<MutableLogCall> mutableCalls2 = createMutableLogCalls(calls2);

        removeAll(mutableCalls1, mutableCalls2, MutableLogCall::isLogCallEqual);
        if (!calls1.isEmpty()) {
            removeAll(mutableCalls1, mutableCalls2, MutableLogCall::isLogCallEquivalentInvocation);
        }

        return mutableCalls1.isEmpty();
    }

    private Set<MutableLogCall> createMutableLogCalls(Set<T> calls) {
        return calls.stream()
                .map(call -> new MutableLogCall(call, call.mTimes))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Algorithm iterates through each call in the second list and removes the corresponding match
     * in the first list given an equality function definition. Also removes element in the second
     * list if all corresponding matches are identified in the first list.
     */
    private void removeAll(
            Set<MutableLogCall> calls1,
            Set<MutableLogCall> calls2,
            BiFunction<MutableLogCall, MutableLogCall, Boolean> func) {
        Iterator<MutableLogCall> iterator2 = calls2.iterator();
        while (iterator2.hasNext()) {
            // LogCall to find a match for in the first list
            MutableLogCall c2 = iterator2.next();
            Iterator<MutableLogCall> iterator1 = calls1.iterator();
            while (iterator1.hasNext()) {
                MutableLogCall c1 = iterator1.next();
                // Use custom equality definition to identify if two LogCalls are matching and
                // alter times based on their frequency.
                if (func.apply(c1, c2)) {
                    if (c1.mTimes >= c2.mTimes) {
                        c1.mTimes -= c2.mTimes;
                        c2.mTimes = 0;
                    } else {
                        c2.mTimes -= c1.mTimes;
                        c1.mTimes = 0;
                    }
                }
                // LogCall in the first list has a corresponding match in the second list. Remove it
                // so it can no longer be used.
                if (c1.mTimes == 0) {
                    iterator1.remove();
                }
                // Match for LogCall in the second list has been identified, remove it and move
                // on to the next element.
                if (c2.mTimes == 0) {
                    iterator2.remove();
                    break;
                }
            }
        }
    }

    private String callsToStr(Set<T> calls) {
        return calls.stream().map(call -> "\n\t" + call).reduce("", (a, b) -> a + b);
    }

    private String constructErrorMessage(Set<T> expectedCalls, Set<T> actualCalls) {
        StringBuilder message = new StringBuilder();
        // Header
        message.append("Detected mismatch in logging calls between expected and actual:\n");
        // Print recorded expected calls
        message.append("Expected Calls:\n[").append(callsToStr(expectedCalls)).append("\n]\n");
        // Print recorded actual calls
        message.append("Actual Calls:\n[").append(callsToStr(actualCalls)).append("\n]\n");
        // Print hint to use annotations - just in case test author isn't aware.
        message.append(getResolutionMessage()).append('\n');

        return message.toString();
    }

    /**
     * Internal wrapper class that encapsulates the log call and number of times so the times can be
     * altered safely during the log verification process.
     */
    private final class MutableLogCall {
        private final T mLogCall;
        private int mTimes;

        private MutableLogCall(T logCall, int times) {
            mLogCall = logCall;
            mTimes = times;
        }

        /*
         * Util method to check if log calls encapsulated within two MutableLogCall objects are
         * equal.
         */
        private boolean isLogCallEqual(MutableLogCall other) {
            return mLogCall.equals(other.mLogCall);
        }

        /*
         * Util method to check if log calls encapsulated within two MutableLogCall objects have
         * equivalent invocations.
         */
        private boolean isLogCallEquivalentInvocation(MutableLogCall other) {
            return mLogCall.isEquivalentInvocation(other.mLogCall);
        }
    }
}
