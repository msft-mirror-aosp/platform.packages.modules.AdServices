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

import static com.android.adservices.shared.testing.util.IoHelper.printStreamToString;
import static com.android.adservices.shared.testing.util.IoHelper.printWriterToString;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.SharedSidelessTestCase;

import org.junit.Test;

public final class TestFailureTest extends SharedSidelessTestCase {

    private static final Throwable CAUSE = new Throwable("D'OH!");
    private static final String EXTRA_INFO_DESCRIPTION = "Extra! Extra!";
    private static final StringBuilder EXTRA_INFO = new StringBuilder("Bob");

    @Test
    public void testNullArgs() {
        assertThrows(
                NullPointerException.class,
                () -> new TestFailure(/* cause= */ null, EXTRA_INFO_DESCRIPTION, EXTRA_INFO));
        assertThrows(
                NullPointerException.class,
                () -> new TestFailure(CAUSE, /* dumpDescription= */ null, EXTRA_INFO));
        assertThrows(
                NullPointerException.class,
                () -> new TestFailure(CAUSE, EXTRA_INFO_DESCRIPTION, /* dump= */ null));
    }

    @Test
    public void testGetters() {
        TestFailure e = new TestFailure(CAUSE, EXTRA_INFO_DESCRIPTION, EXTRA_INFO);
        String expectedMessage =
                String.format(TestFailure.MESSAGE_TEMPLATE, EXTRA_INFO_DESCRIPTION);

        expect.withMessage("getCause()").that(e.getCause()).isSameInstanceAs(CAUSE);
        expect.withMessage("getMessage()").that(e.getMessage()).isEqualTo(expectedMessage);
        expect.withMessage("getExtraInfo()")
                .that(e.getExtraInfo())
                .isEqualTo(EXTRA_INFO.toString());
    }

    @Test
    public void testToString() {
        TestFailure e = new TestFailure(CAUSE, EXTRA_INFO_DESCRIPTION, EXTRA_INFO);

        expect.withMessage("toString()")
                .that(e.toString())
                .isEqualTo("TestFailure: " + e.getMessage());
    }

    @Test
    public void testPrintStackTrace_printWriter() throws Exception {
        TestFailure e = new TestFailure(CAUSE, EXTRA_INFO_DESCRIPTION, EXTRA_INFO);
        String causeStackTrace = printWriterToString((writer) -> CAUSE.printStackTrace(writer));

        String stackTrace = printWriterToString((writer) -> e.printStackTrace(writer));

        expect.withMessage("stack trace").that(stackTrace).startsWith(e + "\nCaused by: " + CAUSE);
        expect.withMessage("stack trace").that(stackTrace).contains(causeStackTrace);
        expect.withMessage("stack trace").that(stackTrace).endsWith(EXTRA_INFO.toString() + "\n");
    }

    @Test
    public void testPrintStackTrace_printStream() throws Exception {
        TestFailure e = new TestFailure(CAUSE, EXTRA_INFO_DESCRIPTION, EXTRA_INFO);
        String causeStackTrace = printStreamToString((stream) -> CAUSE.printStackTrace(stream));

        String stackTrace = printStreamToString((stream) -> e.printStackTrace(stream));

        expect.withMessage("stack trace").that(stackTrace).startsWith(e + "\nCaused by: " + CAUSE);
        expect.withMessage("stack trace").that(stackTrace).contains(causeStackTrace);
        expect.withMessage("stack trace").that(stackTrace).endsWith(EXTRA_INFO.toString() + "\n");
    }
}
