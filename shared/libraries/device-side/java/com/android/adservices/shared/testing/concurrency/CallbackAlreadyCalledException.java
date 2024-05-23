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

import androidx.annotation.Nullable;

import java.util.Objects;

/** Custom exception thrown when a callback was called more times then expected. */
public final class CallbackAlreadyCalledException extends IllegalStateException {

    private final String mName;
    private final @Nullable Object mPreviousValue;
    private final @Nullable Object mNewValue;

    public CallbackAlreadyCalledException(
            String name, @Nullable Object previousValue, @Nullable Object newValue) {
        super(
                String.format(
                        "%s already called with %s (and now called with %s)",
                        Objects.requireNonNull(name), previousValue, newValue));
        mPreviousValue = previousValue;
        mNewValue = newValue;
        mName = name;
    }

    @Nullable
    public Object getPreviousValue() {
        return mPreviousValue;
    }

    @Nullable
    public Object getNewValue() {
        return mNewValue;
    }

    public String getName() {
        return mName;
    }
}
