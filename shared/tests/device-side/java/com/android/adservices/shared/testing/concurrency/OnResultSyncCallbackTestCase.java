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

import org.junit.Test;

abstract class OnResultSyncCallbackTestCase<R, CB extends IOnResultSyncCallback<R>>
        extends IResultSyncCallbackTestCase<R, CB> {

    @Test
    public final void testOnResult() throws Exception {
        expect.withMessage("%s.isCalled() before onResult()", mCallback)
                .that(mCallback.isCalled())
                .isFalse();
        expect.withMessage("%s.getResult() before onResult()", mCallback)
                .that(mCallback.getResult())
                .isNull();
        expect.withMessage("toString() before onResult()")
                .that(mCallback.toString())
                .contains("(no result yet)");
        R injectedResult = newResult();

        runAsync(INJECTION_TIMEOUT_MS, () -> mCallback.onResult(injectedResult));
        R receivedResult = mCallback.assertResultReceived();

        expect.withMessage("%s.assertResultReceived()", mCallback)
                .that(receivedResult)
                .isSameInstanceAs(injectedResult);
        expect.withMessage("%s.isCalled() after onResult()", mCallback)
                .that(mCallback.isCalled())
                .isTrue();
    }
}
