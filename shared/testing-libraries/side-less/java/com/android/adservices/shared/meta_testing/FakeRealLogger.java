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

import com.android.adservices.shared.testing.DynamicLogger;
import com.android.adservices.shared.testing.LogEntry;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.Logger.LogLevel;
import com.android.adservices.shared.testing.Logger.RealLogger;
import com.android.adservices.shared.testing.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple implementation of {@link RealLogger} that stores log calls for further assertions (they
 * can be obtained by {@link #getEntries()}.
 */
public final class FakeRealLogger implements RealLogger {

    private final List<LogEntry> mEntries = new ArrayList<>();

    private static final Logger sRealLogger =
            new Logger(DynamicLogger.getInstance(), FakeRealLogger.class);

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
        return "[" + FakeRealLogger.class.getSimpleName() + ": " + mEntries.size() + " entries]";
    }

    @FormatMethod
    private void addEntry(
            LogLevel level,
            String tag,
            Throwable throwable,
            @FormatString String msgFmt,
            @Nullable Object... msgArgs) {
        String message = String.format(msgFmt, msgArgs);
        LogEntry logEntry = new LogEntry(level, tag, message, throwable);

        // Also log "for real"
        // TODO(b/380449177): ideally should be .v or .d , but it's .i because Ravenwood logs at
        // that level by default
        sRealLogger.i("Adding entry: %s", logEntry);
        mEntries.add(logEntry);
    }
}
