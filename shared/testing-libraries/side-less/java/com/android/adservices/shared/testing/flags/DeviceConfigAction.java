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
package com.android.adservices.shared.testing.flags;

import com.android.adservices.shared.testing.Action;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.device.DeviceConfig;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Base class for {@code DeviceConfig} actions. */
public abstract class DeviceConfigAction implements Action {

    protected final Logger mLog;
    protected final DeviceConfig mDeviceConfig;

    private final AtomicBoolean mCalled = new AtomicBoolean();

    protected DeviceConfigAction(Logger logger, DeviceConfig deviceConfig) {
        mLog = Objects.requireNonNull(logger, "deviceConfig cannot be null");
        mDeviceConfig = Objects.requireNonNull(deviceConfig, "deviceConfig cannot be null");
    }

    @Override
    public final boolean execute() throws Exception {
        if (mCalled.getAndSet(true)) {
            throw new IllegalStateException("Already executed");
        }

        return onExecute();
    }

    /** Actual call to {@link #onExecute()} */
    protected abstract boolean onExecute() throws Exception;

    @Override
    public final void revert() throws Exception {
        if (!mCalled.get()) {
            throw new IllegalStateException("Not executed yet");
        }
        onRevert();
    }

    /** Actual call to {@link #onRevert()}. */
    protected abstract void onRevert() throws Exception;
}
