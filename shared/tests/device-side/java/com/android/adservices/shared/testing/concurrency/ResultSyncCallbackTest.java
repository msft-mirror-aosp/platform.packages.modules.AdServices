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

import com.android.adservices.shared.SharedExtendedMockitoTestCase;

import org.junit.Test;

public final class ResultSyncCallbackTest extends SharedExtendedMockitoTestCase {

    private final ResultSyncCallback<Object> mCallback = new ResultSyncCallback<>();

    @Test
    public void testUnsupportedMethods() {
        assertThrows(UnsupportedOperationException.class, () -> mCallback.setCalled());
    }

    @Test
    public void testCustomWorkflow() throws Exception {
        expect.withMessage("%s.isCalled() before injectResult()", mCallback)
                .that(mCallback.isCalled())
                .isFalse();
        expect.withMessage("%s.getResult() before injectResult()", mCallback)
                .that(mCallback.getResult())
                .isNull();

        Object injectedResult = new Object();
        mCallback.injectResult(injectedResult);

        Object receivedResult = mCallback.assertResultReceived();
        expect.withMessage("%s.assertResultReceived()", mCallback)
                .that(receivedResult)
                .isSameInstanceAs(injectedResult);
        expect.withMessage("%s.isCalled() after injectResult()", mCallback)
                .that(mCallback.isCalled())
                .isTrue();

        Object gottenResult = mCallback.getResult();
        expect.withMessage("%s.getResult()", mCallback)
                .that(gottenResult)
                .isSameInstanceAs(injectedResult);
    }

    @Test
    public void testCustomWorkflow_nullResult() throws Exception {
        expect.withMessage("%s.isCalled() before injectResult()", mCallback)
                .that(mCallback.isCalled())
                .isFalse();
        expect.withMessage("%s.getResult() before injectResult()", mCallback)
                .that(mCallback.getResult())
                .isNull();

        mCallback.injectResult(null);

        Object receivedResult = mCallback.assertResultReceived();
        expect.withMessage("%s.assertResultReceived()", mCallback).that(receivedResult).isNull();
        expect.withMessage("%s.isCalled() after injectResult()", mCallback)
                .that(mCallback.isCalled())
                .isTrue();

        Object gottenResult = mCallback.getResult();
        expect.withMessage("%s.getResult()", mCallback).that(gottenResult).isNull();
    }

    @Test
    public void testInjectResult_calledTwice() {
        mCallback.injectResult(new Object());

        assertThrows(IllegalStateException.class, () -> mCallback.injectResult(new Object()));
    }

    @Test
    public void testInjectResult_calledTwice_firstWasNull() {
        mCallback.injectResult(null);

        assertThrows(IllegalStateException.class, () -> mCallback.injectResult(new Object()));
    }
}
