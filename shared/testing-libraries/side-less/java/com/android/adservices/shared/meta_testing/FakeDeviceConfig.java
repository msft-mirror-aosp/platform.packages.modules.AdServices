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
package com.android.adservices.shared.meta_testing;

import com.android.adservices.shared.testing.DynamicLogger;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.Nullable;
import com.android.adservices.shared.testing.device.DeviceConfig;

/** Fake implementation of {@link DeviceConfig} so tests don't need to mock it. */
public final class FakeDeviceConfig implements DeviceConfig {

    private final Logger mLog = new Logger(DynamicLogger.getInstance(), getClass());

    private SyncDisabledModeForTest mMode = SyncDisabledModeForTest.NONE;

    @Nullable private Runnable mOnGetCallback;
    @Nullable private Runnable mOnSetCallback;

    @Override
    public FakeDeviceConfig setSyncDisabledMode(@Nullable SyncDisabledModeForTest mode) {
        mLog.i("setSyncDisabledMode(%s): mMode=%s, mOnSetCallback=%s", mode, mMode, mOnSetCallback);
        if (mOnSetCallback != null) {
            mOnSetCallback.run();
        }
        mLog.i("setSyncDisabledMode(): from %s to %s", mMode, mode);
        mMode = mode;
        return this;
    }

    @Override
    public SyncDisabledModeForTest getSyncDisabledMode() {
        mLog.d("getSyncDisabledMode(): mMode=%s, mOnGetCallback=%s", mMode, mOnGetCallback);
        if (mOnGetCallback != null) {
            mOnGetCallback.run();
        }
        return mMode;
    }

    /** When set to non-{@code null}, calls to {@code setSyncDisabledMode()} will call it. */
    public void onSetSyncDisabledModeCallback(@Nullable Runnable callback) {
        mLog.i("onSetSyncDisabledModeCallback(%s)", callback);
        mOnSetCallback = callback;
    }

    /** When set to non-{@code null}, calls to {@code getSyncDisabledMode()} will call it. */
    public void onGetSyncDisabledModeCallback(@Nullable Runnable callback) {
        mLog.i("ongetSyncDisabledModeCallback(%s)", callback);
        mOnGetCallback = callback;
    }

    @Override
    public String toString() {
        return "FakeDeviceConfig[mMode="
                + mMode
                + ",mOnSetCallback="
                + mOnSetCallback
                + ",mOnGetCallback="
                + mOnGetCallback
                + "]";
    }
}
