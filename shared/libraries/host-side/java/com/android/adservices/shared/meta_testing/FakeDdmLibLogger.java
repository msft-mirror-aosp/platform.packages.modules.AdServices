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

import com.android.adservices.shared.testing.LogEntry;
import com.android.adservices.shared.testing.Logger;
import com.android.ddmlib.Log;
import com.android.ddmlib.Log.ILogOutput;
import com.android.tradefed.log.LogUtil.CLog;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

/**
 * Fake implementation of {@link ILogOutput}.
 *
 * <p>Should be obtained using {@link #addToDdmLib()} (which automatically registers it as
 * listener), then released by {@link #removeSelf()}.
 */
public final class FakeDdmLibLogger implements ILogOutput {

    private final List<LogEntry> mEntries = new ArrayList<>();

    private FakeDdmLibLogger() {}

    /** Factory method. */
    public static FakeDdmLibLogger addToDdmLib() {
        var logger = new FakeDdmLibLogger();
        CLog.i("Calling com.android.ddmlib.Log.addLogger(%s)", logger);
        Log.addLogger(logger);
        return logger;
    }

    /** Unregister itself as listener. */
    public void removeSelf() {
        // Note: message before probably won't be logged as we're intercepting it
        CLog.i("Calling com.android.ddmlib.Log.removeLogger(%s)", this);
        Log.removeLogger(this);
    }

    /** Gets all logged entries. */
    public ImmutableList<LogEntry> getEntries() {
        return ImmutableList.copyOf(mEntries);
    }

    @Override
    public void printAndPromptLog(Log.LogLevel logLevel, String tag, String message) {
        printLog(logLevel, tag, message);
    }

    @Override
    public void printLog(Log.LogLevel logLevel, String tag, String message) {
        mEntries.add(new LogEntry(convertLevel(logLevel), tag, message));
    }

    private static Logger.LogLevel convertLevel(Log.LogLevel level) {
        switch (level) {
            case ASSERT:
                return Logger.LogLevel.WTF;
            case ERROR:
                return Logger.LogLevel.ERROR;
            case WARN:
                return Logger.LogLevel.WARNING;
            case INFO:
                return Logger.LogLevel.INFO;
            case DEBUG:
                return Logger.LogLevel.DEBUG;
            case VERBOSE:
                return Logger.LogLevel.VERBOSE;
            default:
                throw new UnsupportedOperationException("Invalid level: " + level);
        }
    }

    @Override
    public String toString() {
        return "FakeDdmLibLogger [mEntries=" + mEntries + "]";
    }
}
