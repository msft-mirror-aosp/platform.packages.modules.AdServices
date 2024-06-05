/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.adservices.shared.testing.ConcurrencyHelper.runAsync;
import static com.android.adservices.shared.testing.concurrency.FailableResultSyncCallback.MSG_WRONG_ERROR_RECEIVED;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;
import com.android.adservices.shared.testing.concurrency.FailableResultSyncCallbackTestCase;
import com.android.adservices.shared.testing.concurrency.SyncCallbackFactory;
import com.android.adservices.shared.testing.concurrency.SyncCallbackSettings;
import com.android.adservices.shared.testing.junit.SafeAndroidJUnitRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.NoSuchElementException;

@RequiresSdkLevelAtLeastS(reason = "android.os.OutcomeReceiver was introduced on S")
@RunWith(SafeAndroidJUnitRunner.class)
public final class OutcomeReceiverForTestsTest
        extends FailableResultSyncCallbackTestCase<
                String, Exception, OutcomeReceiverForTests<String>> {

    private static final String RESULT = "Saul Goodman!";

    private static final int TIMEOUT_MS = 200;

    private final Exception mError = new UnsupportedOperationException("D'OH!");

    @Override
    protected OutcomeReceiverForTests<String> newCallback(SyncCallbackSettings settings) {
        return new OutcomeReceiverForTests<>(settings);
    }

    @Override
    protected String newResult() {
        return "Ouchcome#" + getNextUniqueId();
    }

    @Override
    protected Exception newFailure() {
        return new UnsupportedOperationException(getNextUniqueId() + ": D'OH");
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Class<?> getClassOfDifferentFailure() {
        return ArithmeticException.class;
    }

    @Test
    public void testOnResult() throws Exception {
        OutcomeReceiverForTests<String> receiver = newReceiver(TIMEOUT_MS * 3);

        runAsync(TIMEOUT_MS, () -> receiver.onResult(RESULT));

        assertSuccess(receiver, RESULT);
    }

    @Test
    public void testOnResult_calledTwice() throws Exception {
        OutcomeReceiverForTests<String> receiver = mCallback;
        receiver.onResult(RESULT);
        String anotherResult = "You Shall Not Pass!";
        receiver.onResult(anotherResult);

        receiver.assertCalled();

        String when = "after 2 onResult() calls";
        assertGetResultMethods(receiver, when, RESULT, anotherResult);
        assertGetFailureMethodsWhenNoFailure(receiver, when);
    }

    @Test
    public void testOnResult_afterOnError() throws Exception {
        OutcomeReceiverForTests<String> receiver = mCallback;
        receiver.onError(mError);
        receiver.onResult(RESULT);

        receiver.assertCalled();

        String when = "after onError() and onResult()";
        assertGetResultMethodsWhenInjectFailureWasCalledFirst(receiver, when, RESULT);
        assertGetFailureMethods(receiver, when, mError);
    }

    @Test
    public void testAssertFailure_nullArg() {
        OutcomeReceiverForTests<String> receiver = mCallback;
        receiver.onError(mError);

        assertThrows(NullPointerException.class, () -> receiver.assertFailure(null));
    }

    @Test
    public void testOnError() throws Exception {
        OutcomeReceiverForTests<String> receiver = newReceiver(TIMEOUT_MS * 3);

        runAsync(TIMEOUT_MS, () -> receiver.onError(mError));

        assertFailure(receiver, mError);
    }

    @Test
    public void testOnError_wrongExceptionClass() throws Exception {
        OutcomeReceiverForTests<String> receiver = newReceiver(TIMEOUT_MS * 3);

        runAsync(TIMEOUT_MS, () -> receiver.onError(mError));

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> receiver.assertFailure(NoSuchElementException.class));
        expect.withMessage("exception")
                .that(exception)
                .hasMessageThat()
                .isEqualTo(
                        String.format(
                                MSG_WRONG_ERROR_RECEIVED, NoSuchElementException.class, mError));
    }

    @Test
    public void testOnError_calledTwice() throws Exception {
        OutcomeReceiverForTests<String> receiver = mCallback;
        receiver.onError(mError);
        Exception anotherError = new UnsupportedOperationException("Again?");
        receiver.onError(anotherError);

        receiver.assertCalled();

        String when = "after 2 onError() calls";
        assertGetResultMethodsWhenNoResult(receiver, when);
        assertGetFailureMethods(receiver, when, mError, anotherError);
    }

    @Test
    public void testOnError_afterOnResult() throws Exception {
        OutcomeReceiverForTests<String> receiver = mCallback;
        receiver.onResult(RESULT);
        receiver.onError(mError);

        receiver.assertCalled();

        String when = "after onResult() and onError() calls";
        assertGetResultMethods(receiver, when, RESULT);
        assertGetFailureMethodsWhenInjectedResultWasCalledFirst(receiver, when, mError);
    }

    private static OutcomeReceiverForTests<String> newReceiver(long timeoutMs) {
        return newReceiver(timeoutMs, /* failIfCalledOnMainThread= */ true);
    }

    private static OutcomeReceiverForTests<String> newReceiver(
            long timeoutMs, boolean failIfCalledOnMainThread) {
        return new OutcomeReceiverForTests<>(
                SyncCallbackFactory.newSettingsBuilder()
                        .setMaxTimeoutMs(timeoutMs)
                        .setFailIfCalledOnMainThread(failIfCalledOnMainThread)
                        .build());
    }

    private void assertSuccess(OutcomeReceiverForTests<String> receiver, String expectedResult)
            throws InterruptedException {
        String actualResult = receiver.assertSuccess();
        expect.withMessage("assertSuccess()").that(actualResult).isEqualTo(expectedResult);
        expect.withMessage("getResult()").that(receiver.getResult()).isEqualTo(expectedResult);
        expect.withMessage("getError()").that(receiver.getError()).isNull();
        String toString = receiver.toString();
        expect.withMessage("toString()").that(toString).contains("result=" + expectedResult);
    }

    private void assertFailure(OutcomeReceiverForTests<String> receiver, Exception expectedError)
            throws InterruptedException {
        Exception actualError = receiver.assertFailure(expectedError.getClass());
        expect.withMessage("assertFailure()").that(actualError).isSameInstanceAs(expectedError);
        expect.withMessage("getError()").that(receiver.getError()).isSameInstanceAs(expectedError);
        expect.withMessage("getResult()").that(receiver.getResult()).isNull();
        String toString = receiver.toString();
        expect.withMessage("toString()").that(toString).contains("result=" + expectedError);
    }
}
