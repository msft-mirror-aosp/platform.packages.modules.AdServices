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

import com.android.adservices.shared.testing.BooleanSyncCallback;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public final class BooleanSyncCallbackTest
        extends IResultSyncCallbackTestCase<Boolean, BooleanSyncCallback> {

    private final AtomicInteger mNewResultCall = new AtomicInteger();

    @Override
    protected BooleanSyncCallback newCallback(SyncCallbackSettings settings) {
        return new BooleanSyncCallback(settings);
    }

    @Override
    protected Boolean newResult() {
        // TODO(b/337014024): once assumeCallbackSupportsSetCalled(), it might be changed to
        // return 3 values (null as 3rd) or don't throw, in which case we should also update/rename
        // (or remove testNewResult_calledThreeTimes)
        int callNumber = mNewResultCall.incrementAndGet();
        switch (callNumber) {
            case 1:
                return Boolean.FALSE;
            case 2:
                return Boolean.TRUE;
            default:
                // There are only 2 boolean values...
                throw new IllegalStateException(
                        "cannot be called more than 2x (this is call #" + callNumber + ")");
        }
    }

    @Test
    public void testNewResult_calledThreeTimes() {
        newResult();
        newResult();
        assertThrows(IllegalStateException.class, () -> newResult());
    }
}
