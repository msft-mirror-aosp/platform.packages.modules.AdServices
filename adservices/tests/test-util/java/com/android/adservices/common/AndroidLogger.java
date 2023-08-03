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

import android.util.Log;

import com.android.adservices.common.Logger.LogLevel;
import com.android.adservices.common.Logger.RealLogger;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.Objects;

public final class AndroidLogger implements RealLogger {

    private final String mTag;

    public AndroidLogger(Class<?> clazz) {
        this(clazz.getSimpleName());
    }

    public AndroidLogger(String tag) {
        mTag = Objects.requireNonNull(tag);
    }

    @Override
    @FormatMethod
    public void log(LogLevel level, @FormatString String msgFmt, Object... msgArgs) {
        String message = String.format(msgFmt, msgArgs);
        switch (level) {
            case ERROR:
                Log.e(mTag, message);
                return;
            case WARNING:
                Log.w(mTag, message);
                return;
            case INFO:
                Log.i(mTag, message);
                return;
            case DEBUG:
                Log.d(mTag, message);
                return;
            case VERBOSE:
                Log.v(mTag, message);
                return;
            default:
                Log.wtf(mTag, "invalid level (" + level + "): " + message);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[tag=" + mTag + "]";
    }
}
