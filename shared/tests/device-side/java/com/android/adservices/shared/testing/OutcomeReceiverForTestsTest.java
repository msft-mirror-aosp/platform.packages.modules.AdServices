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

import static com.android.adservices.shared.testing.concurrency.FailableResultSyncCallback.INJECT_RESULT_OR_FAILURE;
import static com.android.adservices.shared.testing.ConcurrencyHelper.runAsync;
import static com.android.adservices.shared.testing.ConcurrencyHelper.runOnMainThread;
import static com.android.adservices.shared.testing.concurrency.FailableResultSyncCallback.MSG_WRONG_ERROR_RECEIVED;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;
import com.android.adservices.shared.testing.concurrency.CallbackAlreadyCalledException;
import com.android.adservices.shared.testing.concurrency.CalledOnMainThreadException;
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

    private static final boolean DONT_FAIL_IF_CALLED_ON_MAIN_THREAD = false;
    private static final String RESULT = "Saul Goodman!";
    private static final Exception ERROR = new UnsupportedOperationException("D'OH!");

    private static final int TIMEOUT_MS = 200;

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
    public void testOnResult_calledTwice() {
        OutcomeReceiverForTests<String> receiver = mCallback;
        receiver.onResult(RESULT);
        String anotherError = "You Shall Not Pass!";
        receiver.onResult(anotherError);

        CallbackAlreadyCalledException thrown =
                assertThrows(CallbackAlreadyCalledException.class, () -> receiver.assertCalled());

        thrown.assertWith(expect, INJECT_RESULT_OR_FAILURE, RESULT, anotherError);
    }

    @Test
    public void testOnResult_afterOnError() {
        OutcomeReceiverForTests<String> receiver = mCallback;
        receiver.onError(ERROR);
        receiver.onResult(RESULT);

        CallbackAlreadyCalledException thrown =
                assertThrows(CallbackAlreadyCalledException.class, () -> receiver.assertCalled());

        thrown.assertWith(expect, INJECT_RESULT_OR_FAILURE, ERROR, RESULT);
    }

    @Test
    public void testOnResult_calledOnMainThread_fails() throws Exception {
        OutcomeReceiverForTests<String> receiver = newReceiver(TIMEOUT_MS * 3);

        runOnMainThread(() -> receiver.onResult(RESULT));

        assertThrows(CalledOnMainThreadException.class, () -> receiver.assertCalled());
    }

    @Test
    public void testOnResult_calledOnMainThread_pass() throws Exception {
        OutcomeReceiverForTests<String> receiver =
                newReceiver(TIMEOUT_MS * 3, DONT_FAIL_IF_CALLED_ON_MAIN_THREAD);

        runOnMainThread(() -> receiver.onResult(RESULT));

        assertSuccess(receiver, RESULT);
    }

    @Test
    public void testAssertFailure_nullArg() {
        OutcomeReceiverForTests<String> receiver = mCallback;
        receiver.onError(ERROR);

        assertThrows(NullPointerException.class, () -> receiver.assertFailure(null));
    }

    @Test
    public void testOnError() throws Exception {
        OutcomeReceiverForTests<String> receiver = newReceiver(TIMEOUT_MS * 3);

        runAsync(TIMEOUT_MS, () -> receiver.onError(ERROR));

        assertFailure(receiver, ERROR);
    }

    @Test
    public void testOnError_wrongExceptionClass() throws Exception {
        OutcomeReceiverForTests<String> receiver = newReceiver(TIMEOUT_MS * 3);

        runAsync(TIMEOUT_MS, () -> receiver.onError(ERROR));

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> receiver.assertFailure(NoSuchElementException.class));
        expect.withMessage("exception")
                .that(exception)
                .hasMessageThat()
                .isEqualTo(
                        String.format(
                                MSG_WRONG_ERROR_RECEIVED, NoSuchElementException.class, ERROR));
    }

    @Test
    public void testOnError_calledTwice() {
        OutcomeReceiverForTests<String> receiver = mCallback;
        receiver.onError(ERROR);
        Exception anotherError = new UnsupportedOperationException("Again?");
        receiver.onError(anotherError);

        CallbackAlreadyCalledException thrown =
                assertThrows(CallbackAlreadyCalledException.class, () -> receiver.assertCalled());

        thrown.assertWith(expect, INJECT_RESULT_OR_FAILURE, ERROR, anotherError);
    }

    @Test
    public void testOnError_afterOnResult() {
        OutcomeReceiverForTests<String> receiver = mCallback;
        receiver.onResult(RESULT);
        receiver.onError(ERROR);

        CallbackAlreadyCalledException thrown =
                assertThrows(CallbackAlreadyCalledException.class, () -> receiver.assertCalled());

        thrown.assertWith(expect, INJECT_RESULT_OR_FAILURE, RESULT, ERROR);
    }

    @Test
    public void testOnError_calledOnMainThread_fails() throws Exception {
        OutcomeReceiverForTests<String> receiver = newReceiver(TIMEOUT_MS * 3);

        runOnMainThread(() -> receiver.onError(ERROR));

        assertThrows(CalledOnMainThreadException.class, () -> receiver.assertCalled());
    }

    @Test
    public void testOnError_calledOnMainThread_pass() throws Exception {
        OutcomeReceiverForTests<String> receiver =
                newReceiver(TIMEOUT_MS * 3, DONT_FAIL_IF_CALLED_ON_MAIN_THREAD);

        runOnMainThread(() -> receiver.onError(ERROR));

        assertFailure(receiver, ERROR);
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
