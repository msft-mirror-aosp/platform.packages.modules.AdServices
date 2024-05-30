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

public final class FailableResultSyncCallbackTest
        extends FailableResultSyncCallbackTestCase<
                String, RuntimeException, FailableResultSyncCallback<String, RuntimeException>> {

    @Override
    protected String newResult() {
        return "I AM GROOT #" + getNextUniqueId();
    }

    @Override
    protected RuntimeException newFailure() {
        return new UnsupportedOperationException("D'OH#");
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Class<?> getClassOfDifferentFailure() {
        return ArithmeticException.class;
    }

    @Override
    protected FailableResultSyncCallback<String, RuntimeException> newCallback(
            SyncCallbackSettings settings) {
        return new FailableResultSyncCallback<String, RuntimeException>(settings);
    }

    @Test
    public void testUnsupportedMethods() {
        assertThrows(UnsupportedOperationException.class, () -> mCallback.setCalled());
    }
}
