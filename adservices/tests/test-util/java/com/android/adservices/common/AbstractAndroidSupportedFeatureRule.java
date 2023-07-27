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

import android.annotation.Nullable;
import android.util.Log;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.Objects;

/**
 * Extension of {@link AbstractSupportedFeatureRule} that defines a common Android logging utility
 * function. All Android supported feature rules should extend this class.
 */
public abstract class AbstractAndroidSupportedFeatureRule extends AbstractSupportedFeatureRule {
    private final String mLoggingTag;

    /** Creates a rule with the given mode and logging tag. */
    public AbstractAndroidSupportedFeatureRule(Mode mode, String loggingTag) {
        super(mode);
        mLoggingTag = Objects.requireNonNull(loggingTag);
    }

    @Override
    @FormatMethod
    protected final void log(
            LogLevel level, @FormatString String msgFmt, @Nullable Object... msgArgs) {
        String message = String.format(msgFmt, msgArgs);
        switch (level) {
            case ERROR:
                Log.e(mLoggingTag, message);
                return;
            case WARNING:
                Log.w(mLoggingTag, message);
                return;
            case INFO:
                Log.i(mLoggingTag, message);
                return;
            case DEBUG:
                Log.d(mLoggingTag, message);
                return;
            case VERBOSE:
                Log.v(mLoggingTag, message);
                return;
            default:
                Log.wtf(mLoggingTag, "invalid level (" + level + "): " + message);
        }
    }
}
