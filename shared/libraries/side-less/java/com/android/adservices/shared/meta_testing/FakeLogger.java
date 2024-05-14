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

import com.android.adservices.shared.testing.Logger.LogLevel;
import com.android.adservices.shared.testing.Logger.RealLogger;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple implementation of {@link RealLogger} that stores log calls for further assertions (they
 * can be obtained by {@link #getEntries()}.
 */
public final class FakeLogger implements RealLogger {

    private final List<LogEntry> mEntries = new ArrayList<>();

    @Override
    @FormatMethod
    public void log(LogLevel level, String tag, @FormatString String msgFmt, Object... msgArgs) {
        addEntry(level, tag, /* throwable= */ null, msgFmt, msgArgs);
    }

    @Override
    @FormatMethod
    public void log(
            LogLevel level,
            String tag,
            Throwable throwable,
            @FormatString String msgFmt,
            Object... msgArgs) {
        addEntry(level, tag, throwable, msgFmt, msgArgs);
    }

    /** Gets all logged entries. */
    public ImmutableList<LogEntry> getEntries() {
        return ImmutableList.copyOf(mEntries);
    }

    @Override
    public String toString() {
        return "[" + FakeLogger.class.getSimpleName() + ": " + mEntries.size() + " entries]";
    }

    @FormatMethod
    private void addEntry(
            LogLevel level,
            String tag,
            Throwable throwable,
            @FormatString String msgFmt,
            Object... msgArgs) {
        String message = String.format(msgFmt, msgArgs);
        mEntries.add(new LogEntry(level, tag, throwable, message));
    }
}