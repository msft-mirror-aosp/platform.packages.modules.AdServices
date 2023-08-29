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

package com.android.adservices.common;

import static org.junit.Assert.assertThrows;

import android.os.SystemClock;
import android.util.Log;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;

public final class OutcomeReceiverForTestsTest {

    private static final String TAG = OutcomeReceiverForTestsTest.class.getSimpleName();

    private static final String RESULT = "Saul Goodman!";

    private static final int TIMEOUT_MS = 200;

    private static int sThreadId;

    @Rule public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Rule public final Expect expect = Expect.create();

    private final Exception mError = new UnsupportedOperationException("D'OH!");

    @Test
    public void testOnResult() throws Exception {
        onResultTest(/* await= */ false);
    }

    @Test
    public void testOnResult_await() throws Exception {
        onResultTest(/* await= */ true);
    }

    private void onResultTest(boolean await) throws InterruptedException {
        OutcomeReceiverForTests<String> receiver = new OutcomeReceiverForTests<>();

        String result;
        if (await) {
            runAsync(TIMEOUT_MS, () -> receiver.onResult(RESULT));
            result = receiver.assertSuccess(TIMEOUT_MS * 3);
        } else {
            receiver.onResult(RESULT);
            result = receiver.assertSuccess();
        }

        expect.withMessage("assertSuccess()").that(result).isEqualTo(RESULT);
        expect.withMessage("getResult()").that(receiver.getResult()).isEqualTo(RESULT);
        expect.withMessage("getError()").that(receiver.getError()).isNull();
    }

    @Test
    public void testOnResult_calledTwice() {
        OutcomeReceiverForTests<String> receiver = new OutcomeReceiverForTests<>();
        receiver.onResult(RESULT);
        String anotherError = "You Shall Not Pass!";

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> receiver.onResult(anotherError));

        expect.withMessage("exception")
                .that(exception)
                .hasMessageThat()
                .contains("onResult(" + anotherError + ") called after onResult(" + RESULT + ")");
    }

    @Test
    public void testOnResult_afterOnError() {
        OutcomeReceiverForTests<String> receiver = new OutcomeReceiverForTests<>();
        receiver.onError(mError);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> receiver.onResult(RESULT));

        expect.withMessage("exception")
                .that(exception)
                .hasMessageThat()
                .contains("onResult(" + RESULT + ") called after onError(" + mError + ")");
    }

    @Test
    public void testOnError_nullArg() {
        OutcomeReceiverForTests<String> receiver = new OutcomeReceiverForTests<>();

        assertThrows(NullPointerException.class, () -> receiver.assertFailure(null));
    }

    @Test
    public void testOnError() throws Exception {
        onErrorTest(/* await= */ false);
    }

    @Test
    public void testOnError_await() throws Exception {
        onErrorTest(/* await= */ true);
    }

    private void onErrorTest(boolean await) throws InterruptedException {
        OutcomeReceiverForTests<String> receiver = new OutcomeReceiverForTests<>();

        Exception error;
        if (await) {
            runAsync(TIMEOUT_MS, () -> receiver.onError(mError));
            error = receiver.assertFailure(mError.getClass(), TIMEOUT_MS * 3);
        } else {
            receiver.onError(mError);
            error = receiver.assertFailure(mError.getClass());
        }

        expect.withMessage("assertFailure()").that(error).isSameInstanceAs(mError);
        expect.withMessage("getError()").that(receiver.getError()).isSameInstanceAs(mError);
        expect.withMessage("getResult()").that(receiver.getResult()).isNull();
    }

    @Test
    public void testOnError_calledTwice() {
        OutcomeReceiverForTests<String> receiver = new OutcomeReceiverForTests<>();
        receiver.onError(mError);
        Exception anotherError = new UnsupportedOperationException("Again?");

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> receiver.onError(anotherError));

        expect.withMessage("exception")
                .that(exception)
                .hasMessageThat()
                .contains("onError(" + anotherError + ") called after onError(" + mError + ")");
    }

    @Test
    public void testOnError_afterOnResult() {
        OutcomeReceiverForTests<String> receiver = new OutcomeReceiverForTests<>();
        receiver.onResult(RESULT);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> receiver.onError(mError));

        expect.withMessage("exception")
                .that(exception)
                .hasMessageThat()
                .contains("onError(" + mError + ") called after onResult(" + RESULT + ")");
    }

    private static void runAsync(long timeoutMs, Runnable r) {
        Thread t =
                new Thread(
                        () -> {
                            Log.v(TAG, "Sleeping " + timeoutMs + "ms on " + Thread.currentThread());
                            SystemClock.sleep(timeoutMs);
                            Log.v(TAG, "Woke up");
                            r.run();
                            Log.v(TAG, "Done");
                        },
                        TAG + ".runAsync()_thread#" + ++sThreadId);
        Log.v(TAG, "Starting thread " + t);
        t.start();
    }
}
