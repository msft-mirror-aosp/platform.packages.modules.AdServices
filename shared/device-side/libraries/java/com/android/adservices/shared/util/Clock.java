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

package com.android.adservices.shared.util;

import android.os.SystemClock;

/** Class for SystemClock call. */
public abstract class Clock {
    /** Elapsed Realtime, see SystemClock.elapsedRealtime() */
    public long elapsedRealtime() {
        throw new UnsupportedOperationException();
    }

    /** Uptime, see SystemClock.uptimeMillis() */
    public long uptimeMillis() {
        throw new UnsupportedOperationException();
    }

    /** Wall-clock time as per System.currentTimeMillis() */
    public long currentTimeMillis() {
        throw new UnsupportedOperationException();
    }

    private static final Clock SYSTEM_CLOCK =
            new Clock() {

                @Override
                public long elapsedRealtime() {
                    return SystemClock.elapsedRealtime();
                }

                @Override
                public long uptimeMillis() {
                    return SystemClock.uptimeMillis();
                }

                @Override
                public long currentTimeMillis() {
                    return System.currentTimeMillis();
                }
            };

    /** Returns the static instance of the {@link Clock} using {@link SystemClock}. */
    public static Clock getInstance() {
        return SYSTEM_CLOCK;
    }
}
