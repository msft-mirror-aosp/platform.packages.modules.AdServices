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
import com.android.adservices.shared.testing.Nullable;

import com.google.common.truth.FailureMetadata;

/** Abstracts a log entry. */
public final class LogEntry {

    /** Level of the message. */
    public final LogLevel level;

    /** Tag of the message. */
    public final String tag;

    /** Throable of the message. */
    @Nullable public final Throwable throwable;

    /** Message itself. */
    public final String message;

    LogEntry(LogLevel level, String tag, @Nullable Throwable throwable, String message) {
        this.level = level;
        this.tag = tag;
        this.throwable = throwable;
        this.message = message;
    }

    @Override
    public String toString() {
        return "LogEntry [level="
                + level
                + ", tag="
                + tag
                + ", throwable="
                + throwable
                + ", message="
                + message
                + "]";
    }

    /**
     * Custom {@code Truth} subject for a {@link LogEntry}.
     *
     * <p>Typical usage: <code>
     * import static com.android.adservices.shared.meta_testing.LogEntry.Subject.logEntry;
     *
     * LogEntry entry = ...
     * expect.withMessage("logged message")
     * .about(logEntry())
     * .that(entry))
     * .hasLevel(..)
     * .hasTag(...)
     * .hasXyz(...);
     * </code>
     */
    public static final class Subject extends com.google.common.truth.Subject {

        /** Factory method. */
        public static Factory<Subject, LogEntry> logEntry() {
            return Subject::new;
        }

        @Nullable private final LogEntry mActual;

        private Subject(FailureMetadata metadata, @Nullable Object actual) {
            super(metadata, actual);
            mActual = (LogEntry) actual;
        }

        /** Checks it has the expected level. */
        public Subject hasLevel(LogLevel expected) {
            check("level").that(mActual.level).isEqualTo(expected);
            return this;
        }

        /** Checks it has the expected tag. */
        public Subject hasTag(String expected) {
            check("tag").that(mActual.tag).isEqualTo(expected);
            return this;
        }

        /** Checks it has the expected throwable. */
        public Subject hasThrowable(Throwable expected) {
            check("throwable").that(mActual.throwable).isSameInstanceAs(expected);
            return this;
        }

        /** Checks it has the expected message. */
        public Subject hasMessage(String expected) {
            check("message").that(mActual.message).isEqualTo(expected);
            return this;
        }
    }
}
