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

abstract class OnFailableResultSyncCallbackTestCase<
                R, F, CB extends FailableOnResultSyncCallback<R, F>>
        extends OnResultSyncCallbackTestCase<R, CB> {

    /** Gets a new, unique failure object, preferably with a user-friendly string representation. */
    protected abstract F newFailure();

    @Test
    public final void testNewFailure() {
        F failure1 = newFailure();
        expect.withMessage("1st failure").that(failure1).isNotNull();

        F failure2 = newFailure();
        expect.withMessage("2nd failure").that(failure2).isNotNull();
        expect.withMessage("2nd failure").that(failure2).isNotSameInstanceAs(failure1);
    }

    @Test
    public final void testOnFailure() throws Exception {
        expect.withMessage("%s.isCalled() before onFailure()", mCallback)
                .that(mCallback.isCalled())
                .isFalse();
        expect.withMessage("%s.getResult() before onFailure()", mCallback)
                .that(mCallback.getResult())
                .isNull();
        expect.withMessage("toString() before onFailure()")
                .that(mCallback.toString())
                .contains("(no failure yet)");
        F injectedFailure = newFailure();

        runAsync(INJECTION_TIMEOUT_MS, () -> mCallback.onFailure(injectedFailure));
        F receivedFailure = mCallback.assertFailureReceived();

        expect.withMessage("%s.assertFailureReceived()", mCallback)
                .that(receivedFailure)
                .isSameInstanceAs(injectedFailure);
        expect.withMessage("%s.isCalled() after onFailure()", mCallback)
                .that(mCallback.isCalled())
                .isTrue();
        expect.withMessage("%s.getResult() after onFailure()", mCallback)
                .that(mCallback.getResult())
                .isNull();
    }
}
