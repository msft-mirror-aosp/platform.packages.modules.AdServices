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

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.SharedExtendedMockitoTestCase;

import org.junit.Test;

/** Base test for classes that extend R esultSyncCallback. */
abstract class ResultTestSyncCallbackTestCase<T, R extends ResultTestSyncCallback<T>>
        extends SharedExtendedMockitoTestCase {

    protected static final long INJECTION_TIMEOUT_MS = 200;
    protected static final long CALLBACK_TIMEOUT_MS = INJECTION_TIMEOUT_MS + 400;

    protected final R mCallback =
            newCallback(
                    new SyncCallbackSettings.Builder()
                            .setMaxTimeoutMs(CALLBACK_TIMEOUT_MS)
                            .build());

    protected R getCallback() {
        return mCallback;
    }

    protected abstract T newResult();

    protected abstract R newCallback(SyncCallbackSettings settings);

    @Test
    public final void testGetSettings() throws Exception {
        expect.withMessage("%s.getSettings()", mCallback).that(mCallback.getSettings()).isNotNull();
    }

    @Test
    public final void testGetId() throws Exception {
        String id1 = mCallback.getId();
        expect.withMessage("%s.getId()", mCallback).that(id1).isNotEmpty();

        R callback2 = newCallback(new SyncCallbackSettings.Builder().build());
        String id2 = callback2.getId();
        expect.withMessage("getId() from 2nd callback (%s)", callback2).that(id2).isNotEqualTo(id1);
    }

    @Test
    public final void testInjectResult_assertResultReceived() throws Exception {
        expect.withMessage("%s.isCalled() before injectResult()", mCallback)
                .that(mCallback.isCalled())
                .isFalse();
        expect.withMessage("%s.getResult() before injectResult()", mCallback)
                .that(mCallback.getResult())
                .isNull();
        expect.withMessage("toString() before injectResult()")
                .that(mCallback.toString())
                .contains("(no result yet)");
        T injectedResult = newResult();

        runAsync(INJECTION_TIMEOUT_MS, () -> mCallback.injectResult(injectedResult));
        T receivedResult = mCallback.assertResultReceived();

        expect.withMessage("%s.assertResultReceived()", mCallback)
                .that(receivedResult)
                .isSameInstanceAs(injectedResult);
        expect.withMessage("%s.isCalled() after injectResult()", mCallback)
                .that(mCallback.isCalled())
                .isTrue();
        T gottenResult = mCallback.getResult();
        expect.withMessage("%s.getResult()", mCallback)
                .that(gottenResult)
                .isSameInstanceAs(injectedResult);
        expect.withMessage("toString() after injectResult()")
                .that(mCallback.toString())
                .contains("result=" + injectedResult);
    }

    @Test
    public final void testInjectNullResult_assertResultReceived() throws Exception {
        expect.withMessage("%s.isCalled() before injectResult()", mCallback)
                .that(mCallback.isCalled())
                .isFalse();
        expect.withMessage("%s.getResult() before injectResult()", mCallback)
                .that(mCallback.getResult())
                .isNull();

        runAsync(INJECTION_TIMEOUT_MS, () -> mCallback.injectResult(null));
        T receivedResult = mCallback.assertResultReceived();

        expect.withMessage("%s.assertResultReceived()", mCallback).that(receivedResult).isNull();
        expect.withMessage("%s.isCalled() after injectResult()", mCallback)
                .that(mCallback.isCalled())
                .isTrue();

        T gottenResult = mCallback.getResult();
        expect.withMessage("%s.getResult()", mCallback).that(gottenResult).isNull();
        expect.withMessage("toString()").that(mCallback.toString()).contains("result=null");
    }

    @Test
    public final void testInjectResult_assertCalled() throws Exception {
        expect.withMessage("%s.isCalled() before injectResult()", mCallback)
                .that(mCallback.isCalled())
                .isFalse();
        expect.withMessage("%s.getResult() before injectResult()", mCallback)
                .that(mCallback.getResult())
                .isNull();
        T injectedResult = newResult();

        runAsync(INJECTION_TIMEOUT_MS, () -> mCallback.injectResult(injectedResult));
        mCallback.assertCalled();

        expect.withMessage("%s.isCalled() after injectResult()", mCallback)
                .that(mCallback.isCalled())
                .isTrue();
        T gottenResult = mCallback.getResult();
        expect.withMessage("%s.getResult()", mCallback)
                .that(gottenResult)
                .isSameInstanceAs(injectedResult);
    }

    @Test
    public final void testInjectNullResult_assertCalled() throws Exception {
        expect.withMessage("%s.isCalled() before injectResult()", mCallback)
                .that(mCallback.isCalled())
                .isFalse();
        expect.withMessage("%s.getResult() before injectResult()", mCallback)
                .that(mCallback.getResult())
                .isNull();

        runAsync(INJECTION_TIMEOUT_MS, () -> mCallback.injectResult(null));
        mCallback.assertCalled();

        T gottenResult = mCallback.getResult();
        expect.withMessage("%s.getResult()", mCallback).that(gottenResult).isNull();
    }

    @Test
    public final void testInjectResult_calledTwice() {
        T firstResult = newResult();
        mCallback.injectResult(firstResult);

        T secondResult = newResult();
        mCallback.injectResult(secondResult);

        CallbackAlreadyCalledException thrown =
                assertThrows(CallbackAlreadyCalledException.class, () -> mCallback.assertCalled());

        thrown.assertWith(expect, "injectResult()", firstResult, secondResult);
    }

    @Test
    public final void testInjectResult_calledTwice_firstWasNull() {
        mCallback.injectResult(null);
        T newResult = newResult();
        mCallback.injectResult(newResult);

        CallbackAlreadyCalledException thrown =
                assertThrows(CallbackAlreadyCalledException.class, () -> mCallback.assertCalled());

        thrown.assertWith(expect, "injectResult()", null, newResult);
    }

    @Test
    public final void testAsBinder() {
        expect.withMessage("asBinder()").that(mCallback.asBinder()).isNull();
    }
}
