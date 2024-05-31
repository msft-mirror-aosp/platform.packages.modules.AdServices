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

import static com.android.adservices.shared.testing.ConcurrencyHelper.runAsync;
import static com.android.adservices.shared.testing.concurrency.FailableResultSyncCallback.INJECT_RESULT_OR_FAILURE;
import static com.android.adservices.shared.testing.concurrency.FailableResultSyncCallback.MSG_WRONG_ERROR_RECEIVED;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

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
        expect.withMessage("%s.getFailure() before injectResult()", mCallback)
                .that(mCallback.getFailure())
                .isNull();
        String toStringBefore = mCallback.toString();
        expect.withMessage("toString() before injectResult()")
                .that(toStringBefore)
                .contains("(no result yet)");
        expect.withMessage("toString() before injectResult()")
                .that(toStringBefore)
                .contains("(no failure yet)");

        runAsync(INJECTION_TIMEOUT_MS, () -> mCallback.injectResult(null));
        mCallback.assertCalled();

        expect.withMessage("%s.getFailure() after injectResult()", mCallback)
                .that(mCallback.getFailure())
                .isNull();
        String toStringAfter = mCallback.toString();
        expect.withMessage("toString() after injectResult()")
                .that(toStringAfter)
                .doesNotContain("(no result yet)");
        expect.withMessage("toString() after injectResult()")
                .that(toStringAfter)
                .doesNotContain("(no failure yet)");
        expect.withMessage("toString() after injectResult()")
                .that(toStringAfter)
                .contains("result=null");
    }

    @Test
    public final void testInjectFailure_null() {
        assertThrows(NullPointerException.class, () -> mCallback.injectFailure(null));
    }

    @Test
    public final void testInjectFailure() throws Exception {
        F failure = newFailure();
        expect.withMessage("%s.getFailure() before injectFailure()", mCallback)
                .that(mCallback.getFailure())
                .isNull();
        expect.withMessage("%s.getResult() before injectFailure()", mCallback)
                .that(mCallback.getResult())
                .isNull();
        expect.withMessage("%s.isCalled() before injectFailure()", mCallback)
                .that(mCallback.isCalled())
                .isFalse();

        runAsync(INJECTION_TIMEOUT_MS, () -> mCallback.injectFailure(failure));

        F receivedFailure = mCallback.assertFailureReceived();

        expect.withMessage("%s.assertFailureReceived()", mCallback)
                .that(receivedFailure)
                .isSameInstanceAs(failure);

        expect.withMessage("%s.getFailure() after assertFailureReceived()", mCallback)
                .that(mCallback.getFailure())
                .isSameInstanceAs(failure);
        expect.withMessage("%s.getResult() after assertFailureReceived()", mCallback)
                .that(mCallback.getResult())
                .isNull();
        expect.withMessage("%s.isCalled() after assertFailureReceived()", mCallback)
                .that(mCallback.isCalled())
                .isTrue();
        String toString = mCallback.toString();
        expect.withMessage("toString() after assertFailureReceived()")
                .that(toString)
                .contains("result=" + failure);
        expect.withMessage("toString() after assertFailureReceived()")
                .that(toString)
                .doesNotContain("(no result yet)");
        expect.withMessage("toString() after assertFailureReceived()")
                .that(toString)
                .doesNotContain("(no failure yet)");
    }

    @Test
    public final void testInjectFailure_calledTwice() {
        F failure = newFailure();
        F anotherFailure = newFailure();
        mCallback.injectFailure(failure);
        mCallback.injectFailure(anotherFailure);

        CallbackAlreadyCalledException thrown =
                assertThrows(
                        CallbackAlreadyCalledException.class,
                        () -> mCallback.assertFailureReceived());

        thrown.assertWith(expect, INJECT_RESULT_OR_FAILURE, failure, anotherFailure);
    }

    @Test
    public final void testInjectFailure_calledAfterInjectResult() {
        F failure = newFailure();
        mCallback.injectResult(null);
        mCallback.injectFailure(failure);

        CallbackAlreadyCalledException thrown =
                assertThrows(CallbackAlreadyCalledException.class, () -> mCallback.assertCalled());

        thrown.assertWith(expect, INJECT_RESULT_OR_FAILURE, null, failure);
    }

    @Test
    public final void testInjectResult_calledAfterInjectFailure() {
        F failure = newFailure();
        mCallback.injectFailure(failure);
        mCallback.injectResult(null);

        CallbackAlreadyCalledException thrown =
                assertThrows(CallbackAlreadyCalledException.class, () -> mCallback.assertCalled());

        thrown.assertWith(expect, INJECT_RESULT_OR_FAILURE, failure, null);
    }

    @Test
    public void testAssertFailureReceived_null() throws Exception {
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
}
