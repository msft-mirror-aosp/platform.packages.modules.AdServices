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

import android.os.Handler;
import android.os.Looper;

import com.android.adservices.shared.testing.AndroidLogger;

import java.util.Objects;

public final class DeviceSideConcurrencyHelper {

    private static final ConcurrencyHelper sConcurrencyHelper =
            new ConcurrencyHelper(AndroidLogger.getInstance());

    /** Runs the given runnable in the main thread. */
    public static void runOnMainThread(Runnable r) {
        Objects.requireNonNull(r);
        new Handler(Looper.getMainLooper()).post(r);
    }

    /** Gets the device-side {@link ConcurrencyHelper}. */
    public static ConcurrencyHelper getConcurrencyHelper() {
        return sConcurrencyHelper;
    }

    private DeviceSideConcurrencyHelper() {
        throw new UnsupportedOperationException("Contains only static members");
    }
}
