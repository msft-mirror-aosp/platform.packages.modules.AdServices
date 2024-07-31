/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.adservices.common;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.adservices.shared.testing.DeviceSideTestCase;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import org.junit.Rule;

// TODO(b/285014040): need to add unit tests for this class itself, as it's now providing logic.

/** Superclass for all other "base classes" on {@code AdServices} projects. */
abstract class AdServicesTestCase extends DeviceSideTestCase {

    private static final String TAG = AdServicesTestCase.class.getSimpleName();

    @Rule(order = 1)
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    @Override
    public final String getTestName() {
        return processLifeguard.getTestName();
    }

    /** Sleeps for the given amount of time. */
    @FormatMethod
    protected final void sleep(
            int timeMs, @FormatString String reasonFmt, @Nullable Object... reasonArgs) {
        String reason = String.format(reasonFmt, reasonArgs);
        Log.i(
                TAG,
                getTestName()
                        + ": napping "
                        + timeMs
                        + "ms on thread "
                        + Thread.currentThread()
                        + ". Reason: "
                        + reason);
        SystemClock.sleep(timeMs);
        Log.i(TAG, "Little Suzie woke up!");
    }
}
