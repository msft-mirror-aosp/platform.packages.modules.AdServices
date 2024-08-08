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

package com.android.cobalt.testing.observations;

import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Generates predictable results when asked to get random numbers. It is useful when a SecureRandom
 * object is necessary but randomness is not needed, such as in tests.
 */
public class FakeSecureRandom extends SecureRandom {
    private int mNextByteFill = 0;
    private int mNextInt = 0;

    /** Fill the array with an incrementing byte. */
    @Override
    public synchronized void nextBytes(byte[] bytes) {
        Arrays.fill(bytes, (byte) mNextByteFill);
        mNextByteFill++;
    }

    /** Return a value that will trigger a fabricated Poisson observation for a lambda >= 0.1. */
    @Override
    public double nextDouble() {
        return 0.905;
    }

    /** Return a decrementing value. The first value will be the maximum. */
    @Override
    public int nextInt(int bound) {
        return (bound - 1 - mNextInt++) % bound;
    }
}