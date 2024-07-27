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

import android.os.Looper;

import com.android.adservices.shared.SharedUnitTestCase;
import com.android.adservices.shared.testing.BooleanSyncCallback;

import org.junit.Test;

public final class DeviceSideConcurrencyHelperTest extends SharedUnitTestCase {

    @Test
    public void testRunOnMainThread_null() {
        assertThrows(
                NullPointerException.class,
                () -> DeviceSideConcurrencyHelper.runOnMainThread(null));
    }

    @Test
    public void testRunOnMainThread() throws Exception {
        BooleanSyncCallback callback =
                new BooleanSyncCallback(
                        SyncCallbackFactory.newSettingsBuilder()
                                .setFailIfCalledOnMainThread(false)
                                .build());

        DeviceSideConcurrencyHelper.runOnMainThread(
                () -> callback.injectResult(Looper.getMainLooper().isCurrentThread()));

        boolean isMainThread = callback.assertResultReceived();
        expect.withMessage("called on main thread").that(isMainThread).isTrue();
    }
}
