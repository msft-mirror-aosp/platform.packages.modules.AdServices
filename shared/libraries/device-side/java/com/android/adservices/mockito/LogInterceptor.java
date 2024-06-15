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

package com.android.adservices.mockito;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import android.util.ArrayMap;
import android.util.Log;

import com.android.adservices.shared.testing.LogEntry;
import com.android.adservices.shared.testing.Logger.LogLevel;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

// TODO(b/306522832): move to shared project (device-side)
/** Class used to intercept static calls to {@link Log} so they can be verified. */
public final class LogInterceptor {

    private static final String TAG = LogInterceptor.class.getSimpleName();

    private Map<String, List<LogEntry>> mEntries = new ArrayMap<>();

    private LogInterceptor() {}

    /**
     * Creates an interceptor for {@link Log} calls using the given {@code tag} and {@code levels}.
     */
    public static LogInterceptor forTagAndLevels(String tag, LogLevel... levels) {
        // Log.v is used inside the Answers below, so it cannot be spied upon
        Preconditions.checkArgument(
                !TAG.equals(Objects.requireNonNull(tag, "tag cannot be null")),
                "cannot intercept tag %s",
                TAG);

        LogInterceptor interceptor = new LogInterceptor();
        for (LogLevel level : levels) {
            switch (level) {
                case DEBUG:
                    doAnswer(
                                    invocation -> {
                                        Log.d(TAG, invocation.toString());
                                        interceptor.log(
                                                new LogEntry(
                                                        LogLevel.DEBUG,
                                                        invocation.getArgument(0),
                                                        invocation.getArgument(1)));
                                        invocation.callRealMethod();
                                        return null;
                                    })
                            .when(() -> Log.d(eq(tag), any()));

                    doAnswer(
                                    invocation -> {
                                        Log.d(TAG, invocation.toString());
                                        interceptor.log(
                                                new LogEntry(
                                                        LogLevel.DEBUG,
                                                        invocation.getArgument(0),
                                                        invocation.getArgument(1),
                                                        invocation.getArgument(2)));
                                        invocation.callRealMethod();
                                        return null;
                                    })
                            .when(() -> Log.d(eq(tag), any(), any()));
                    break;
                case VERBOSE:
                    doAnswer(
                                    invocation -> {
                                        Log.v(TAG, invocation.toString());
                                        interceptor.log(
                                                new LogEntry(
                                                        LogLevel.VERBOSE,
                                                        invocation.getArgument(0),
                                                        invocation.getArgument(1)));
                                        invocation.callRealMethod();
                                        return null;
                                    })
                            .when(() -> Log.v(eq(tag), any()));

                    doAnswer(
                                    invocation -> {
                                        Log.v(TAG, invocation.toString());
                                        interceptor.log(
                                                new LogEntry(
                                                        LogLevel.VERBOSE,
                                                        invocation.getArgument(0),
                                                        invocation.getArgument(1),
                                                        invocation.getArgument(2)));
                                        invocation.callRealMethod();
                                        return null;
                                    })
                            .when(() -> Log.v(eq(tag), any(), any()));
                    break;
                case ERROR:
                    doAnswer(
                                    invocation -> {
                                        Log.v(TAG, invocation.toString());
                                        interceptor.log(
                                                new LogEntry(
                                                        LogLevel.ERROR,
                                                        invocation.getArgument(0),
                                                        invocation.getArgument(1)));
                                        invocation.callRealMethod();
                                        return null;
                                    })
                            .when(() -> Log.e(eq(tag), any()));

                    doAnswer(
                                    invocation -> {
                                        Log.v(TAG, invocation.toString());
                                        interceptor.log(
                                                new LogEntry(
                                                        LogLevel.ERROR,
                                                        invocation.getArgument(0),
                                                        invocation.getArgument(1),
                                                        invocation.getArgument(2)));
                                        invocation.callRealMethod();
                                        return null;
                                    })
                            .when(() -> Log.e(eq(tag), any(), any()));
                    break;
                default:
                    // NOTE: current tests are only intercepting VERBOSE and ERROR; more levels
                    // will be added on demand.
                    throw new UnsupportedOperationException(
                            "Not intercepting level " + level + " yet");
            }
        }

        return interceptor;
    }

    /** Gets all calls that used that {@code tag}. */
    public List<LogEntry> getAllEntries(String tag) {
        List<LogEntry> entries = mEntries.get(Objects.requireNonNull(tag, "tag cannot be null"));
        return entries == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /**
     * Gets the messages from all "plain" calls (i.e., whose argument was just the message, without
     * a {@link Throwable}) to that {@code tag}.
     */
    public List<String> getPlainMessages(String tag, LogLevel level) {
        Objects.requireNonNull(tag, "tag cannot be null");
        Objects.requireNonNull(level, "level cannot be null");
        return getAllEntries(tag).stream()
                .filter(
                        entry ->
                                entry.level.equals(level)
                                        && entry.tag.equals(tag)
                                        && entry.throwable == null)
                .map(entry -> entry.message)
                .collect(Collectors.toList());
    }

    private void log(LogEntry logEntry) {
        String tag = Objects.requireNonNull(logEntry, "logEntry cannot be null").tag;

        List<LogEntry> entries = mEntries.get(tag);
        if (entries == null) {
            entries = new ArrayList<>();
            mEntries.put(tag, entries);
        }
        entries.add(logEntry);
    }
}
