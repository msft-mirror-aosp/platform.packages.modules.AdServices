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

import org.junit.Test;

import java.io.IOException;

public final class CallableSyncCallbackTest
        extends FailableResultSyncCallbackTestCase<
                String, Throwable, CallableSyncCallback<String>> {

    @Override
    protected String newResult() {
        return "I AM GROOT #" + getNextUniqueId();
    }

    @Override
    protected Throwable newFailure() {
        return new IOException("D'OH! #" + getNextUniqueId());
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Class<?> getClassOfDifferentFailure() {
        return ArithmeticException.class;
    }

    @Override
    protected CallableSyncCallback<String> newCallback(SyncCallbackSettings settings) {
        return new CallableSyncCallback<String>(settings);
    }

    @Test
    public void testInjectFailure_thrownOnAssertResultReceived() throws Exception {
        Throwable failure = newFailure();

        runAsync(BEFORE_ASSERT_CALLED_NAP_TIMEOUT_MS, () -> mCallback.injectFailure(failure));

        IllegalStateException assertReceiveFailure =
                assertThrows(IllegalStateException.class, () -> mCallback.assertResultReceived());

        expect.withMessage("thrown exception")
                .that(assertReceiveFailure)
                .hasCauseThat()
                .isSameInstanceAs(failure);
    }

    @Test
    public void testInjectCallable_null() throws Exception {
        assertThrows(NullPointerException.class, () -> mCallback.injectCallable(null));
    }

    @Test
    public void testInjectCallable_success() throws Exception {
        String injectedResult = newResult();

        runAsync(
                BEFORE_ASSERT_CALLED_NAP_TIMEOUT_MS,
                () -> mCallback.injectCallable(() -> injectedResult));

        String receivedResult = mCallback.assertResultReceived();

        expect.withMessage("%s.assertResultReceived()", mCallback)
                .that(receivedResult)
                .isSameInstanceAs(injectedResult);
        expect.withMessage("getResult()")
                .that(mCallback.getResult())
                .isSameInstanceAs(injectedResult);
        expect.withMessage("getResults()")
                .that(mCallback.getResults())
                .containsExactly(injectedResult);
        expect.withMessage("getFailure()").that(mCallback.getFailure()).isNull();
        expect.withMessage("getFailures()").that(mCallback.getFailures()).isEmpty();
    }

    @Test
    public void testInjectCallable_throws() throws Exception {
        Throwable failure = newFailure();

        runAsync(
                BEFORE_ASSERT_CALLED_NAP_TIMEOUT_MS,
                () ->
                        mCallback.injectCallable(
                                () -> {
                                    throw failure;
                                }));

        IllegalStateException assertReceiveFailure =
                assertThrows(IllegalStateException.class, () -> mCallback.assertResultReceived());

        expect.withMessage("thrown exception")
                .that(assertReceiveFailure)
                .hasCauseThat()
                .isSameInstanceAs(failure);
        expect.withMessage("getFailure()").that(mCallback.getFailure()).isSameInstanceAs(failure);
        expect.withMessage("getFailures()").that(mCallback.getFailures()).containsExactly(failure);
        expect.withMessage("getResult()").that(mCallback.getResult()).isNull();
        expect.withMessage("getResults()").that(mCallback.getResults()).isEmpty();
    }

    @Test
    public void testInjectCallable_multipleCalls_successFirst() throws Exception {
        SyncCallbackSettings settings = mDefaultSettingsBuilder.setExpectedNumberCalls(6).build();
        CallableSyncCallback<String> callback = new CallableSyncCallback<>(settings);
        String result1 = "Saul Goodman!";
        String result2 = "U2";
        String result3 = "3rd time is a charm";
        Throwable failure1 = new IOException("D'OH!");
        Throwable failure2 = new IOException("DuOH!");
        Throwable failure3 = new IOException("Tri-D'OH!");

        runAsync(
                BEFORE_ASSERT_CALLED_NAP_TIMEOUT_MS,
                () -> {
                    callback.injectCallable(() -> result1);
                    callback.injectCallable(
                            () -> {
                                throw failure1;
                            });
                    callback.injectCallable(() -> result2);
                    callback.injectCallable(
                            () -> {
                                throw failure2;
                            });
                    callback.injectResult(result3);
                    callback.injectFailure(failure3);
                });

        String receivedResult = callback.assertResultReceived();
        expect.withMessage("%s.assertResultReceived()", callback)
                .that(receivedResult)
                .isSameInstanceAs(result1);

        expect.withMessage("getResult()").that(callback.getResult()).isSameInstanceAs(result1);
        expect.withMessage("getResults()")
                .that(callback.getResults())
                .containsExactly(result1, result2, result3)
                .inOrder();
        expect.withMessage("getFailure()").that(callback.getFailure()).isNull();
        expect.withMessage("getFailures()")
                .that(callback.getFailures())
                .containsExactly(failure1, failure2, failure3)
                .inOrder();
    }

    @Test
    public void testInjectCallable_multipleCalls_failsFirst() throws Exception {
        SyncCallbackSettings settings = mDefaultSettingsBuilder.setExpectedNumberCalls(6).build();
        CallableSyncCallback<String> callback = new CallableSyncCallback<>(settings);
        String result1 = "Saul Goodman!";
        String result2 = "U2";
        String result3 = "3rd time is a charm";
        Throwable failure1 = new IOException("D'OH!");
        Throwable failure2 = new IOException("DuOH!");
        Throwable failure3 = new IOException("Tri-D'OH!");

        runAsync(
                BEFORE_ASSERT_CALLED_NAP_TIMEOUT_MS,
                () -> {
                    callback.injectCallable(
                            () -> {
                                throw failure1;
                            });
                    callback.injectCallable(() -> result1);
                    callback.injectCallable(() -> result2);
                    callback.injectCallable(
                            () -> {
                                throw failure2;
                            });
                    callback.injectResult(result3);
                    callback.injectFailure(failure3);
                });

        IllegalStateException assertReceiveFailure =
                assertThrows(IllegalStateException.class, () -> callback.assertResultReceived());
        expect.withMessage("thrown exception")
                .that(assertReceiveFailure)
                .hasCauseThat()
                .isSameInstanceAs(failure1);

        expect.withMessage("getFailure()").that(callback.getFailure()).isSameInstanceAs(failure1);
        expect.withMessage("getFailures()")
                .that(callback.getFailures())
                .containsExactly(failure1, failure2, failure3)
                .inOrder();
        expect.withMessage("getResult()").that(callback.getResult()).isNull();
        expect.withMessage("getResults()")
                .that(callback.getResults())
                .containsExactly(result1, result2, result3)
                .inOrder();
    }
}
