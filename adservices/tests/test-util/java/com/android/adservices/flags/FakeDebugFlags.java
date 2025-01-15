/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.adservices.flags;

import com.android.adservices.service.DebugFlags;
import com.android.adservices.shared.testing.AndroidLogger;
import com.android.adservices.shared.testing.Logger;

/** {@link DebugFlags} implementation for unit tests */
final class FakeDebugFlags extends DebugFlags {

    private static final String TAG = FakeDebugFlags.class.getSimpleName();

    private final Logger mLog = new Logger(AndroidLogger.getInstance(), TAG);

    private final FakeFlagsBackend mBackend = new FakeFlagsBackend(TAG);

    void set(String name, String value) {
        mBackend.setFlag(name, value);
    }

    @Override
    protected boolean getBoolean(String name, boolean defaultValue) {
        boolean value = mBackend.getFlag(name, defaultValue);
        mLog.v("getBoolean(%s, %b): returning %b", name, defaultValue, value);
        return value;
    }
}
