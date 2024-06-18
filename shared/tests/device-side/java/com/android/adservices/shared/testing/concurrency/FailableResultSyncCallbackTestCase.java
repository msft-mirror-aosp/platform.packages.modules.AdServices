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

import static com.android.adservices.shared.testing.concurrency.FailableResultSyncCallback.MSG_WRONG_ERROR_RECEIVED;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.testing.Nullable;

import org.junit.Test;

import java.util.List;

// TODO(b/342448771): make it package protected (must move some subclass to this package first)
/** Base test for classes that extend FailableResultSyncCallback. */
public abstract class FailableResultSyncCallbackTestCase<
                R, F, CB extends FailableResultSyncCallback<R, F>>
        extends IResultSyncCallbackTestCase<R, CB> {

    /** Returns a new failure. */
    protected abstract F newFailure();

    /**
     * Returns a class that is not the same as the class of objects returned by {@link
     * #newFailure()} .
     */
    protected abstract Class<?> getClassOfDifferentFailure();

    @Test
    public final void testNewFailureAndGetClassOfDifferentFailure() {
        Class<?> clazz1 = getClassOfDifferentFailure();
        expect.withMessage("1st call to getClassOfdifferentFailure()").that(clazz1).isNotNull();

        Class<?> clazz2 = getClassOfDifferentFailure();
        expect.withMessage("2nd call to getClassOfdifferentFailure()").that(clazz2).isNotNull();
        expect.withMessage("2nd call to getClassOfdifferentFailure()")
                .that(clazz2)
                .isSameInstanceAs(clazz1);

        F failure1 = newFailure();
        expect.withMessage("1st failure").that(failure1).isNotNull();
        expect.withMessage("1st failure").that(failure1).isNotInstanceOf(clazz1);

        F failure2 = newFailure();
        expect.withMessage("2nd failure").that(failure2).isNotNull();
        expect.withMessage("2nd failure").that(failure2).isNotSameInstanceAs(failure1);
        expect.withMessage("2nd failure").that(failure2).isNotInstanceOf(clazz1);
    }

    @Test
    public final void testInjectResult_checkFailure() throws Exception {
        assertInitialState(mCallback);
        R injectedResult = null;

        runAsync(BEFORE_ASSERT_CALLED_NAP_TIMEOUT, () -> mCallback.injectResult(injectedResult));
        mCallback.assertCalled();

        String when = "after injectFailure()";
        assertGetResultMethods(mCallback, when, injectedResult);
        assertGetFailureMethodsWhenNoFailure(mCallback, when);
    }

    @Test
    public final void testInjectFailure_null() {
        assertThrows(NullPointerException.class, () -> mCallback.injectFailure(null));
    }

    @Test
    public final void testInjectFailure() throws Exception {
        F failure = newFailure();
        assertInitialState(mCallback);

        runAsync(BEFORE_ASSERT_CALLED_NAP_TIMEOUT, () -> mCallback.injectFailure(failure));

        F receivedFailure = mCallback.assertFailureReceived();

        expect.withMessage("%s.assertFailureReceived()", mCallback)
                .that(receivedFailure)
                .isSameInstanceAs(failure);
        expect.withMessage("%s.isCalled() after assertFailureReceived()", mCallback)
                .that(mCallback.isCalled())
                .isTrue();

        String when = "after assertFailureReceived()";
        assertGetFailureMethods(mCallback, when, failure);
        assertGetResultMethodsWhenNoResult(mCallback, when);
    }

    @Test
    public final void testInjectFailure_calledTwice() throws Exception {
        F failure = newFailure();
        F anotherFailure = newFailure();
        mCallback.injectFailure(failure);
        mCallback.injectFailure(anotherFailure);

        mCallback.assertFailureReceived();

        String when = "after 2 injectFailure() calls";
        assertGetResultMethodsWhenNoResult(mCallback, when);
        assertGetFailureMethods(mCallback, when, failure, anotherFailure);
    }

    @Test
    public final void testInjectFailure_calledAfterInjectResult() throws Exception {
        String when = "after injectResult() and injectFailure()";
        R result = null;
        F failure = newFailure();
        mCallback.injectResult(result);
        mCallback.injectFailure(failure);

        mCallback.assertFailureReceived();

        assertGetResultMethods(mCallback, when, result);
        assertGetFailureMethodsWhenInjectedResultWasCalledFirst(mCallback, when, failure);
    }

    @Test
    public final void testInjectResult_calledAfterInjectFailure() throws Exception {
        R result = null;
        F failure = newFailure();
        mCallback.injectFailure(failure);
        mCallback.injectResult(result);

        F failureReceived = mCallback.assertFailureReceived();
        expect.withMessage("%s.assertFailureReceived()", mCallback)
                .that(failureReceived)
                .isSameInstanceAs(failure);

        String when = "after injectFailure() and injectResult()";
        assertGetFailureMethods(mCallback, when, failure);
        assertGetResultMethods(mCallback, when, result);
    }

    @Test
    public final void testAssertFailureReceived_null() throws Exception {
        assertThrows(NullPointerException.class, () -> mCallback.assertFailureReceived(null));
    }

    @Test
    public final void testAssertFailureReceived_rightClass() throws Exception {
        F failure = newFailure();
        mCallback.injectFailure(failure);
        @SuppressWarnings("unchecked")
        Class<F> subFailureClass = (Class<F>) failure.getClass();

        F failureReceived = mCallback.assertFailureReceived(subFailureClass);

        expect.withMessage("%s.assertFailureReceived(...)", mCallback)
                .that(failureReceived)
                .isSameInstanceAs(failure);
    }

    @Test
    public final void testAssertFailureReceived_wrongClass() throws Exception {
        @SuppressWarnings("unchecked")
        Class<F> subFailureClass = (Class<F>) getClassOfDifferentFailure();
        F failure = newFailure();
        mCallback.injectFailure(failure);

        IllegalStateException thrown =
                assertThrows(
                        IllegalStateException.class,
                        () -> mCallback.assertFailureReceived(subFailureClass));

        expect.withMessage("thrown")
                .that(thrown)
                .hasMessageThat()
                .isEqualTo(String.format(MSG_WRONG_ERROR_RECEIVED, subFailureClass, failure));
    }

    @Test
    public final void testGetFailures_immutable() throws Exception {
        List<F> failures = mCallback.getFailures();
        expect.withMessage("%s.getFailures() initially", mCallback).that(failures).isEmpty();

        assertThrows(UnsupportedOperationException.class, () -> failures.add(newFailure()));

        expect.withMessage("%s.getFailures() after", mCallback)
                .that(mCallback.getFailures())
                .isEmpty();
    }

    @Test
    public final void testToString_containsFailures() throws Exception {
        // Initial state
        String toString = mCallback.toString();
        expect.withMessage("toString() initially").that(toString).contains("(no failure yet)");

        // NOTE: failures are shown as "results" - see why on
        // FailableResultSyncCallback.customizeToString()

        // 1st call
        F firstFailure = newFailure();
        mCallback.injectFailure(firstFailure);
        toString = mCallback.toString();
        expect.withMessage("toString() after 1st call")
                .that(toString)
                .contains("result=" + firstFailure);
        expect.withMessage("toString() after 1st call")
                .that(toString)
                .contains("results=[" + firstFailure + "]");

        // 2nd call
        F secondFailure = newFailure();
        mCallback.injectFailure(secondFailure);
        toString = mCallback.toString();
        expect.withMessage("toString() after 2nd call")
                .that(toString)
                .contains("result=" + firstFailure);
        expect.withMessage("toString() after 2nd call")
                .that(toString)
                .contains("results=[" + firstFailure + ", " + secondFailure + "]");
    }

    protected final void assertGetFailureMethodsWhenNoFailure(CB callback, String when) {
        expect.withMessage("%s.getFailure() %s", callback, when)
                .that(callback.getFailure())
                .isNull();
        expect.withMessage("%s.getFailures() %s", callback, when)
                .that(callback.getFailures())
                .isEmpty();
    }

    @SafeVarargs
    protected final void assertGetFailureMethods(
            CB callback, String when, @Nullable F... failures) {
        expect.withMessage("%s.getFailure() %s", callback, when)
                .that(callback.getFailure())
                .isSameInstanceAs(failures[0]);
        expect.withMessage("%s.getFailures() %s", callback, when)
                .that(callback.getFailures())
                .containsExactly(failures)
                .inOrder();
    }

    @SafeVarargs
    protected final void assertGetFailureMethodsWhenInjectedResultWasCalledFirst(
            CB callback, String when, @Nullable F... failures) {
        expect.withMessage("%s.getFailure() %s", callback, when)
                .that(callback.getFailure())
                .isNull();
        expect.withMessage("%s.getFailures() %s", callback, when)
                .that(callback.getFailures())
                .containsExactly(failures)
                .inOrder();
    }

    @SafeVarargs
    protected final void assertGetResultMethodsWhenInjectFailureWasCalledFirst(
            CB callback, String when, @Nullable R... expectedResults) {
        expect.withMessage("%s.getResult() %s", callback, when).that(callback.getResult()).isNull();
        expect.withMessage("%s.getResults() %s", callback, when)
                .that(callback.getResults())
                .containsExactly(expectedResults)
                .inOrder();
    }

    private void assertInitialState(CB callback) {
        assertGetResultMethodsWhenNoResult(callback, "initially");
        assertGetFailureMethodsWhenNoFailure(callback, "initially");
    }
}
