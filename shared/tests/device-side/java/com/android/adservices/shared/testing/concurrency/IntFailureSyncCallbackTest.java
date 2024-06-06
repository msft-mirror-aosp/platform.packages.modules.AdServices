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

import com.android.adservices.shared.testing.IntFailureSyncCallback;

import org.junit.Test;

public final class IntFailureSyncCallbackTest
        extends OnFailableResultSyncCallbackTestCase<
                String, Integer, IntFailureSyncCallback<String>> {

    @Override
    protected String newResult() {
        return "INT #" + getNextUniqueId() + ", Y U NO FAIL?";
    }

    @Override
    protected IntFailureSyncCallback<String> newCallback(SyncCallbackSettings settings) {
        return new ConcreteIntFailureSyncCallback(settings);
    }

    @Override
    protected Integer newFailure() {
        return getNextUniqueId();
    }

    @Test
    public void testAssertSuccess() throws Exception {
        runAsync(INJECTION_TIMEOUT_MS, () -> mCallback.onResult("YES!"));

        String success = mCallback.assertSuccess();

        expect.withMessage("%s.assertSuccess()", mCallback).that(success).isEqualTo("YES!");
        expect.withMessage("%s.getResult() after assertSuccess()", mCallback)
                .that(mCallback.getResult())
                .isEqualTo("YES!");
        expect.withMessage("%s.getFailure() after assertSuccess()", mCallback)
                .that(mCallback.getFailure())
                .isNull();
    }

    @Test
    public void testAssertFailed() throws Exception {
        runAsync(INJECTION_TIMEOUT_MS, () -> mCallback.onFailure(42));

        mCallback.assertFailed(42);

        expect.withMessage("%s.getFailure() after assertFailure()", mCallback)
                .that(mCallback.getFailure())
                .isEqualTo(42);
        expect.withMessage("%s.getResult() after assertFailure()", mCallback)
                .that(mCallback.getResult())
                .isNull();
    }

    @Test
    public void testAssertFailed_wrongValue() throws Exception {
        // Don't need to run async
        mCallback.onFailure(42);

        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> mCallback.assertFailed(108));

        expect.withMessage("exception")
                .that(thrown)
                .hasMessageThat()
                .isEqualTo("Expected code 108, but it failed with code 42");
    }

    private static final class ConcreteIntFailureSyncCallback
            extends IntFailureSyncCallback<String> {

        @SuppressWarnings("unused") // Called by superclass using reflection
        ConcreteIntFailureSyncCallback() {
            super();
        }

        ConcreteIntFailureSyncCallback(SyncCallbackSettings settings) {
            super(settings);
        }
    }
}
