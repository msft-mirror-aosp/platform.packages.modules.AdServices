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
package com.android.adservices.shared.testing;

import static com.android.adservices.shared.testing.LogEntry.Subject.logEntry;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.adservices.shared.meta_testing.FakeDdmLibLogger;
import com.android.adservices.shared.testing.Logger.LogLevel;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public final class HostSideDynamicLoggerTest extends HostSideTestCase {

    private final String mTag = "El Taggo";
    private final Throwable mThrowable = new Throwable("D'OH!");
    private final FakeDdmLibLogger mFakeLogger = FakeDdmLibLogger.addToDdmLib();
    private final DynamicLogger mLogger = DynamicLogger.getInstance();

    @After
    public void unregisterDdmLibLogger() {
        mFakeLogger.removeSelf();
    }

    @Test
    public void testToString() {
        expect.withMessage("toString()")
                .that(mLogger.toString())
                .isEqualTo("DynamicLogger[com.android.tradefed.log.LogUtil$CLog]");
    }

    @Test
    public void testWtf() {
        mLogger.log(LogLevel.WTF, mTag, "%s %s", "message", "in a bottle");

        assertLogEntry(LogLevel.WTF, "message in a bottle");
    }

    @Test
    public void testWtf_withThrowable() throws Exception {
        mLogger.log(LogLevel.WTF, mTag, mThrowable, "%s %s", "message", "in a bottle");

        ImmutableList<LogEntry> logEntries = mFakeLogger.getEntries();
        assertWithMessage("log entries").that(logEntries).isNotNull();
        assertWithMessage("log entries").that(logEntries).hasSize(1);

        String expectedMessage = getExpectedMessage(LogLevel.WTF, mTag, "message in a bottle");
        LogEntry logEntry = logEntries.get(0);

        // NOTE: LogEntry.tag doesn't matter, it's some internal value
        expect.withMessage("logged message")
                .about(logEntry())
                .that(logEntry)
                .hasLevel(LogLevel.WTF)
                .hasThrowable(null);

        // CLog includes the exception message and the 1st 2 lines of the stack trace in the message
        // but checking for the message itself is enough
        expect.withMessage("1st line")
                .that(
                        logEntry.message.startsWith(
                                getExpectedMessage(LogLevel.WTF, mThrowable.toString()) + "\n"))
                .isTrue();
    }

    @Test
    public void testE() throws Exception {
        mLogger.log(LogLevel.ERROR, mTag, "%s %s", "message", "in a bottle");

        assertLogEntry(LogLevel.ERROR, "message in a bottle");
    }

    @Test
    public void testE_withThrowable() throws Exception {
        mLogger.log(LogLevel.ERROR, mTag, mThrowable, "%s %s", "message", "in a bottle");

        assertLogEntryWithMultiLineException(LogLevel.ERROR, mThrowable, "message in a bottle");
    }

    @Test
    public void testW() throws Exception {
        mLogger.log(LogLevel.WARNING, mTag, "%s %s", "message", "in a bottle");

        assertLogEntry(LogLevel.WARNING, "message in a bottle");
    }

    @Test
    public void testW_withThrowable() throws Exception {
        mLogger.log(LogLevel.WARNING, mTag, mThrowable, "%s %s", "message", "in a bottle");

        assertLogEntryWithMultiLineException(LogLevel.WARNING, mThrowable, "message in a bottle");
    }

    @Test
    public void testI() throws Exception {
        mLogger.log(LogLevel.INFO, mTag, "%s %s", "message", "in a bottle");

        assertLogEntry(LogLevel.INFO, "message in a bottle");
    }

    @Test
    public void testI_withThrowable() throws Exception {
        mLogger.log(LogLevel.INFO, mTag, mThrowable, "%s %s", "message", "in a bottle");

        assertLogEntryWithFlattenedException(LogLevel.INFO, mThrowable, "message in a bottle");
    }

    @Test
    public void testD() throws Exception {
        mLogger.log(LogLevel.DEBUG, mTag, "%s %s", "message", "in a bottle");

        assertLogEntry(LogLevel.DEBUG, "message in a bottle");
    }

    @Test
    public void testD_withThrowable() throws Exception {
        mLogger.log(LogLevel.DEBUG, mTag, mThrowable, "%s %s", "message", "in a bottle");

        assertLogEntryWithFlattenedException(LogLevel.DEBUG, mThrowable, "message in a bottle");
    }

    @Test
    public void testV() throws Exception {
        mLogger.log(LogLevel.VERBOSE, mTag, "%s %s", "message", "in a bottle");

        assertLogEntry(LogLevel.VERBOSE, "message in a bottle");
    }

    @Test
    public void testV_withThrowable() throws Exception {
        mLogger.log(LogLevel.VERBOSE, mTag, mThrowable, "%s %s", "message", "in a bottle");

        assertLogEntryWithFlattenedException(LogLevel.VERBOSE, mThrowable, "message in a bottle");
    }

    private void assertLogEntry(LogLevel level, String message) {
        // NOTE: LogEntry.tag doesn't matter, it's some internal value
        ImmutableList<LogEntry> logEntries = mFakeLogger.getEntries();
        assertWithMessage("log entries").that(logEntries).isNotNull();
        assertWithMessage("log entries").that(logEntries).hasSize(1);

        String expectedMessage = getExpectedMessage(level, mTag, message);
        expect.withMessage("logged message")
                .about(logEntry())
                .that(logEntries.get(0))
                .hasLevel(level)
                .hasMessage(expectedMessage)
                .hasThrowable(null);
    }

    private void assertLogEntryWithFlattenedException(
            LogLevel level, Throwable throwable, String message) throws IOException {
        assertLogEntry(level, DynamicLogger.getMessageWithFlattenedException(message, throwable));
    }

    private void assertLogEntryWithMultiLineException(
            LogLevel level, Throwable throwable, String message) throws IOException {
        // NOTE: LogEntry.tag doesn't matter, it's some internal value
        ImmutableList<LogEntry> logEntries = mFakeLogger.getEntries();
        assertWithMessage("log entries").that(logEntries).isNotNull();
        assertWithMessage("log entries").that(logEntries).hasSize(2);

        // 1st entry is the message
        String expectedMessage = getExpectedMessage(level, mTag, message);
        expect.withMessage("1st logged message")
                .about(logEntry())
                .that(logEntries.get(0))
                .hasLevel(level)
                .hasMessage(expectedMessage)
                .hasThrowable(null);

        // 2nd entry is exception (message on line 1, stack trace on next lines)
        String stackTrace;
        try (StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw); ) {
            throwable.printStackTrace(pw);
            stackTrace = sw.toString();
        }
        LogEntry secondEntry = logEntries.get(1);
        expect.withMessage("2nd logged message")
                .about(logEntry())
                .that(secondEntry)
                .hasLevel(level)
                .hasThrowable(null);
        // CLog includes the exception message and the 1st 2 lines of the stack trace in the message
        // but checking for the message itself is enough
        expect.withMessage("1st line of 2nd logged message")
                .that(
                        secondEntry.message.startsWith(
                                getExpectedMessage(level, throwable.toString()) + "\n"))
                .isTrue();
    }

    /**
     * Gets the expected message, according to the level - this method is needed because {@code
     * CLog} includes {@code WTF} as prefix.
     */
    private static String getExpectedMessage(LogLevel level, @Nullable String tag, String message) {
        StringBuilder expectedMessage = new StringBuilder();
        if (level.equals(LogLevel.WTF)) {
            // CLog previx the WTF messages with "WTF -"
            expectedMessage.append("WTF - ");
        }
        if (tag != null) {
            expectedMessage.append('[').append(tag).append("]: ");
        }

        return expectedMessage.append(message).toString();
    }

    /**
     * Gets the expected message, according to the level - this method is needed because {@code
     * CLog} includes {@code WTF} as prefix.
     */
    private static String getExpectedMessage(LogLevel level, String message) {
        return getExpectedMessage(level, /* tag= */ null, message);
    }
}
