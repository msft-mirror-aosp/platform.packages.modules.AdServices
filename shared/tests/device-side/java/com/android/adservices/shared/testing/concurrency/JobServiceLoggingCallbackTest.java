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

import static com.android.adservices.shared.testing.concurrency.DeviceSideConcurrencyHelper.runAsync;

import com.android.adservices.shared.SharedMockitoTestCase;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;

import org.junit.Test;

public final class JobServiceLoggingCallbackTest extends SharedMockitoTestCase {

    private static final long INJECTION_TIMEOUT_MS = 200;
    private static final long CALLBACK_TIMEOUT_MS = INJECTION_TIMEOUT_MS + 5_000;

    @Test
    public void testCustomMethods() throws Exception {
        JobServiceLoggingCallback callback =
                new JobServiceLoggingCallback(
                        SyncCallbackFactory.newSettingsBuilder()
                                .setMaxTimeoutMs(CALLBACK_TIMEOUT_MS)
                                .build());

        runAsync(INJECTION_TIMEOUT_MS, () -> callback.onLoggingMethodCalled());

        callback.assertLoggingFinished();
    }
}
