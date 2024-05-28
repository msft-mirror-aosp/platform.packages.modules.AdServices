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

import android.os.Looper;

/** Factory for device-side, {@code SyncCallback}-related objects. */
public final class SyncCallbackFactory {

    /** Factory method to get a settings object with default values. */
    public static SyncCallbackSettings newDefaultSettings() {
        return newSettingsBuilder().build();
    }

    /** Factory method to get a settings builder. */
    public static SyncCallbackSettings.Builder newSettingsBuilder() {
        return new SyncCallbackSettings.Builder(SyncCallbackFactory::isMainThread);
    }

    // TODO(b/337014024): add unit test to check this logic (currently it's indirectly checked by
    // AbstractTestSyncCallbackTest, but it will be refactored)
    private static boolean isMainThread() {
        return Looper.getMainLooper() != null && Looper.getMainLooper().isCurrentThread();
    }
}
