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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Abstract log verifier to hold common logic across all log verifiers.
 *
 * @param <T> expected {@link LogCall} type to be verified
 */
// TODO (b/323000746) - Add unit test coverage for this file after call comparison logic has been
//  implemented.
public abstract class AbstractLogVerifier<T extends LogCall> implements LogVerifier {
    protected final String mTag = getClass().getSimpleName();

    // Key is the LogCall object, value is the source of truth for times; we do not keep track of
    // the times value in the LogCall object itself because we would like to group together all
    // identical log call invocations, irrespective of number of times they have been invoked.
    private final Map<T, Integer> mActualCalls = new HashMap<>();

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
     * Subclasses are expected to use this method to record actual log call. Assumes only a single
     * call is being recorded at once.
     *
     * @param actualCall Actual call to be recorded
     */
    protected void recordActualCall(T actualCall) {
        mActualCalls.put(actualCall, mActualCalls.getOrDefault(actualCall, 0) + 1);
    }

    private void verifyCalls(Set<T> expectedCalls, Set<T> actualCalls) {
        Log.d(mTag, "Total expected calls: " + expectedCalls.size());
        Log.d(mTag, "Total actual calls: " + actualCalls.size());

        // TODO (b/323000746) - Provide detailed error message (e.g. Expected X calls, but Y
        //  actual calls etc.).
        if (!Objects.equals(expectedCalls, actualCalls)) {
            throw new IllegalStateException("Mismatch in log verification calls!");
        }
    }

    private Set<T> getActualCalls() {
        // At this point, the map of actual calls will no longer be updated. Therefore, it's safe
        // to alter the times field in the actual LogCall objects.
        mActualCalls
                .keySet()
                .forEach(actualLogCall -> actualLogCall.mTimes = mActualCalls.get(actualLogCall));

        return mActualCalls.keySet();
    }
}
