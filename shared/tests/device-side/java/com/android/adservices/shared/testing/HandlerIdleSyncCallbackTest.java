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

package com.android.adservices.shared.testing;

import com.android.adservices.shared.SharedExtendedMockitoTestCase;

import org.junit.Test;

public final class HandlerIdleSyncCallbackTest extends SharedExtendedMockitoTestCase {

    @Test
    public void testCustomMethods() throws Exception {
        HandlerIdleSyncCallback callback = new HandlerIdleSyncCallback();
        // Cannot assert if it's idle or not right away, as the result is injected by the handler
        // thread.

        callback.assertIdle();
        expect.withMessage("isIdle() after called").that(callback.isIdle()).isTrue();
    }

}
