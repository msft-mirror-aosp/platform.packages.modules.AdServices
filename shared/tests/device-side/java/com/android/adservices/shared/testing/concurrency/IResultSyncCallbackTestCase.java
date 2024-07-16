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
package com.android.adservices.shared.testing.concurrency;

import static org.junit.Assert.assertThrows;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Base test for classes that extend ResultTestSyncCallback. */
public abstract class IResultSyncCallbackTestCase<
                R, CB extends AbstractSyncCallback & IResultSyncCallback<R>>
        extends IBinderSyncCallbackTestCase<CB> {

    private static final AtomicInteger sNextId = new AtomicInteger();

    // Must be set on @Before otherwise OutcomeReceiverForTestsTest would fail on R
    protected CB mCallback;

    /** Gets a new, unique result object, preferably with a user-friendly string representation. */
    protected abstract R newResult();

    @Before
    public final void setFixtures() {
        mCallback = newCallback(mDefaultSettings);
        mLog.v("setFixtures(): mCallback=%s", mCallback);
    }

    /**
     * Gets a unique id.
     *
     * <p>Useful to make sure {@link #newResult()} return unique objects.
     */
    protected final int getNextUniqueId() {
        return sNextId.incrementAndGet();
    }

    @Override
    protected String callCallback(CB callback) {
        R result = newResult();
        callback.injectResult(result);
        return "injectResult(" + result + ")";
    }

    @Test
    public final void testNewResult() {
        R result1 = newResult();
        expect.withMessage("1st result").that(result1).isNotNull();

        R result2 = newResult();
        expect.withMessage("2nd result").that(result2).isNotNull();
        expect.withMessage("2nd result").that(result2).isNotSameInstanceAs(result1);
    }

    @Test
    public final void testInjectResult_assertResultReceived() throws Exception {
        assertInitialState(mCallback);
        R injectedResult = newResult();

        runAsync(BEFORE_ASSERT_CALLED_NAP_TIMEOUT_MS, () -> mCallback.injectResult(injectedResult));
        R receivedResult = mCallback.assertResultReceived();

        expect.withMessage("%s.assertResultReceived()", mCallback)
                .that(receivedResult)
                .isSameInstanceAs(injectedResult);
        expect.withMessage("%s.isCalled() after injectResult()", mCallback)
                .that(mCallback.isCalled())
                .isTrue();
        R gottenResult = mCallback.getResult();
        expect.withMessage("%s.getResult()", mCallback)
                .that(gottenResult)
                .isSameInstanceAs(injectedResult);
        expect.withMessage("%s.getResults() after injectResult()", mCallback)
                .that(mCallback.getResults())
                .containsExactly(injectedResult);
        expect.withMessage("toString() after injectResult()")
                .that(mCallback.toString())
                .contains("result=" + injectedResult);
    }

    @Test
    public final void testInjectNullResult_assertResultReceived() throws Exception {
        assertInitialState(mCallback);
        R injectedResult = null;

        runAsync(BEFORE_ASSERT_CALLED_NAP_TIMEOUT_MS, () -> mCallback.injectResult(injectedResult));
        R receivedResult = mCallback.assertResultReceived();

        expect.withMessage("%s.assertResultReceived()", mCallback).that(receivedResult).isNull();
        expect.withMessage("%s.isCalled() after injectResult()", mCallback)
                .that(mCallback.isCalled())
                .isTrue();

        assertGetResultMethods(mCallback, "after injectResult()", injectedResult);
    }

    @Test
    public final void testInjectResult_assertCalled() throws Exception {
        assertInitialState(mCallback);
        R injectedResult = newResult();

        runAsync(BEFORE_ASSERT_CALLED_NAP_TIMEOUT_MS, () -> mCallback.injectResult(injectedResult));
        mCallback.assertCalled();

        assertGetResultMethods(mCallback, "after injectResult()", injectedResult);
    }

    @Test
    public final void testInjectNullResult_assertCalled() throws Exception {
        assertInitialState(mCallback);
        R injectedResult = null;

        runAsync(BEFORE_ASSERT_CALLED_NAP_TIMEOUT_MS, () -> mCallback.injectResult(injectedResult));
        mCallback.assertCalled();

        assertGetResultMethods(mCallback, "after injectResult()", injectedResult);
    }

    @Test
    public final void testInjectResult_calledTwice() throws Exception {
        R firstResult = newResult();
        mCallback.injectResult(firstResult);
        R secondResult = newResult();
        mCallback.injectResult(secondResult);

        R assertReceivedResult = mCallback.assertResultReceived();

        expect.withMessage("%s.assertResultReceived()", mCallback)
                .that(assertReceivedResult)
                .isSameInstanceAs(firstResult);

        assertGetResultMethods(
                mCallback, "after 2 injectResult() calls", firstResult, secondResult);
    }

    @Test
    public final void testInjectResult_calledTwice_firstWasNull() throws Exception {
        R firstResult = null;
        mCallback.injectResult(firstResult);
        R secondResult = newResult();
        mCallback.injectResult(secondResult);

        R assertReceivedResult = mCallback.assertResultReceived();

        expect.withMessage("%s.assertResultReceived()", mCallback)
                .that(assertReceivedResult)
                .isSameInstanceAs(firstResult);

        assertGetResultMethods(
                mCallback, "after 2 injectResult() calls", firstResult, secondResult);
    }

    @Test
    public final void testGetResults_immutable() throws Exception {
        List<R> results = mCallback.getResults();
        expect.withMessage("%s.getResults() initially", mCallback).that(results).isEmpty();

        assertThrows(UnsupportedOperationException.class, () -> results.add(newResult()));

        expect.withMessage("%s.getResults() after", mCallback)
                .that(mCallback.getResults())
                .isEmpty();
    }

    @Test
    public final void testToString_containsResults() throws Exception {
        // Initial state
        String toString = mCallback.toString();
        expect.withMessage("toString()").that(toString).contains("(no result yet)");

        // 1st call
        R firstResult = newResult();
        mCallback.injectResult(firstResult);
        toString = mCallback.toString();
        expect.withMessage("toString()").that(toString).contains("result=" + firstResult);
        expect.withMessage("toString()").that(toString).contains("results=[" + firstResult + "]");

        // 2nd call
        R secondResult = newResult();
        mCallback.injectResult(secondResult);
        toString = mCallback.toString();
        expect.withMessage("toString()").that(toString).contains("result=" + firstResult);
        expect.withMessage("toString()")
                .that(toString)
                .contains("results=[" + firstResult + ", " + secondResult + "]");
    }

    protected final void assertGetResultMethodsWhenNoResult(CB callback, String when) {
        expect.withMessage("%s.getResult() %s", callback, when).that(callback.getResult()).isNull();
        expect.withMessage("%s.getResults() %s", callback, when)
                .that(callback.getResults())
                .isEmpty();
    }

    @SafeVarargs
    protected final void assertGetResultMethods(CB callback, String when, R... expectedResults) {
        expect.withMessage("%s.getResult() %s", callback, when)
                .that(callback.getResult())
                .isSameInstanceAs(expectedResults[0]);
        expect.withMessage("%s.getResults() %s", callback, when)
                .that(callback.getResults())
                .containsExactly(expectedResults)
                .inOrder();
    }

    private void assertInitialState(CB callback) {
        assertGetResultMethodsWhenNoResult(callback, "initially");
    }
}
