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

import static com.android.adservices.shared.testing.TestFailure.throwTestFailure;
import static com.android.adservices.shared.testing.util.IoHelper.printStreamToString;
import static com.android.adservices.shared.testing.util.IoHelper.printWriterToString;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.SharedSidelessTestCase;

import org.junit.Test;

public final class TestFailureTest extends SharedSidelessTestCase {

    private static final Throwable CAUSE = new Throwable("D'OH!");
    private static final String EXTRA_INFO = "Extra! Extra!";
    private static final String ANOTHER_EXTRA_INFO = "Read all about it...Extra!";
    @Deprecated // TODO(b/383404021): remove it
    private static final StringBuilder DEPRECATED_EXTRA_INFO = new StringBuilder("Bob");

    @Test
    public void testThrowTestFailure_null() {
        assertThrows(
                NullPointerException.class, () -> throwTestFailure(CAUSE, /* extraInfo= */ null));
        assertThrows(
                NullPointerException.class, () -> throwTestFailure(/* cause= */ null, EXTRA_INFO));
    }

    @Test
    public void testThrowTestFailure_differentExceptionType() throws Exception {
        var e = assertThrows(TestFailure.class, () -> throwTestFailure(CAUSE, EXTRA_INFO));

        assertWithMessage("rethrow()").that(e).isNotNull();

        expect.withMessage("getCause()").that(e).hasCauseThat().isSameInstanceAs(CAUSE);

        String expectedMessage = String.format(TestFailure.MESSAGE_TEMPLATE, EXTRA_INFO);
        expect.withMessage("getMessage()").that(e.getMessage()).isEqualTo(expectedMessage);

        var extraInfo = e.getExtraInfo();
        assertWithMessage("getExtraInfo()").that(extraInfo).isNotNull();
        expect.withMessage("getExtraInfo()").that(extraInfo).containsExactly(EXTRA_INFO);

        expect.withMessage("getStackTrace()")
                .that(e.getStackTrace())
                .isEqualTo(CAUSE.getStackTrace());

        expect.withMessage("toString()")
                .that(e.toString())
                .isEqualTo("TestFailure: " + e.getMessage());

        assertStackTrace(e, EXTRA_INFO);
    }

    @Test
    public void testRethrow_testFailure() throws Exception {
        var cause = assertThrows(TestFailure.class, () -> throwTestFailure(CAUSE, EXTRA_INFO));

        var e = assertThrows(TestFailure.class, () -> throwTestFailure(cause, ANOTHER_EXTRA_INFO));

        expect.withMessage("rethrow()").that(e).isSameInstanceAs(cause);
        expect.withMessage("getCause()").that(e).hasCauseThat().isSameInstanceAs(CAUSE);

        String expectedMessage = String.format(TestFailure.MESSAGE_TEMPLATE, EXTRA_INFO);
        expect.withMessage("getMessage()").that(e.getMessage()).isEqualTo(expectedMessage);

        var extraInfo = e.getExtraInfo();
        assertWithMessage("getExtraInfo()").that(extraInfo).isNotNull();
        expect.withMessage("getExtraInfo()")
                .that(extraInfo)
                .containsExactly(EXTRA_INFO, ANOTHER_EXTRA_INFO)
                .inOrder();

        expect.withMessage("getStackTrace()")
                .that(e.getStackTrace())
                .isEqualTo(CAUSE.getStackTrace());

        expect.withMessage("toString()")
                .that(e.toString())
                .isEqualTo("TestFailure: " + e.getMessage());

        assertStackTrace(e, EXTRA_INFO, ANOTHER_EXTRA_INFO);
    }

    @Test
    @Deprecated // TODO(b/383404021): remove it
    public void testNullArgs() {
        assertThrows(
                NullPointerException.class,
                () -> new TestFailure(/* cause= */ null, EXTRA_INFO, DEPRECATED_EXTRA_INFO));
        assertThrows(
                NullPointerException.class,
                () -> new TestFailure(CAUSE, /* dumpDescription= */ null, DEPRECATED_EXTRA_INFO));
        assertThrows(
                NullPointerException.class,
                () -> new TestFailure(CAUSE, EXTRA_INFO, /* dump= */ null));
    }

    @Test
    @Deprecated // TODO(b/383404021): remove it
    public void testGetters() {
        TestFailure e = new TestFailure(CAUSE, EXTRA_INFO, DEPRECATED_EXTRA_INFO);
        String expectedMessage = String.format(TestFailure.MESSAGE_TEMPLATE, EXTRA_INFO);

        expect.withMessage("getCause()").that(e.getCause()).isSameInstanceAs(CAUSE);
        expect.withMessage("getMessage()").that(e.getMessage()).isEqualTo(expectedMessage);
        var extraInfo = e.getExtraInfo();
        assertWithMessage("getExtraInfo()").that(extraInfo).isNotNull();
        expect.withMessage("getExtraInfo()")
                .that(extraInfo)
                .containsExactly(DEPRECATED_EXTRA_INFO.toString());
    }

    @Test
    @Deprecated // TODO(b/383404021): remove it
    public void testGetStackTrace() throws Exception {
        TestFailure e = new TestFailure(CAUSE, EXTRA_INFO, DEPRECATED_EXTRA_INFO);

        expect.withMessage("getStackTrace()")
                .that(e.getStackTrace())
                .isEqualTo(CAUSE.getStackTrace());
    }

    @Test
    @Deprecated // TODO(b/383404021): remove it
    public void testToString() {
        TestFailure e = new TestFailure(CAUSE, EXTRA_INFO, DEPRECATED_EXTRA_INFO);

        expect.withMessage("toString()")
                .that(e.toString())
                .isEqualTo("TestFailure: " + e.getMessage());
    }

    @Test
    @Deprecated // TODO(b/383404021): remove it
    public void testPrintStackTrace_printWriter() throws Exception {
        TestFailure e = new TestFailure(CAUSE, EXTRA_INFO, DEPRECATED_EXTRA_INFO);
        String causeStackTrace = printWriterToString((writer) -> CAUSE.printStackTrace(writer));

        String stackTrace = printWriterToString((writer) -> e.printStackTrace(writer));

        expect.withMessage("stack trace").that(stackTrace).startsWith(e + "\nCaused by: " + CAUSE);
        expect.withMessage("stack trace").that(stackTrace).contains(causeStackTrace);
        expect.withMessage("stack trace")
                .that(stackTrace)
                .endsWith(DEPRECATED_EXTRA_INFO.toString() + "\n");
    }

    @Test
    @Deprecated // TODO(b/383404021): remove it
    public void testPrintStackTrace_printStream() throws Exception {
        TestFailure e = new TestFailure(CAUSE, EXTRA_INFO, DEPRECATED_EXTRA_INFO);

        String causeStackTrace = printStreamToString((stream) -> CAUSE.printStackTrace(stream));

        String stackTrace = printStreamToString((stream) -> e.printStackTrace(stream));

        expect.withMessage("stack trace").that(stackTrace).startsWith(e + "\nCaused by: " + CAUSE);
        expect.withMessage("stack trace").that(stackTrace).contains(causeStackTrace);
        expect.withMessage("stack trace")
                .that(stackTrace)
                .endsWith(DEPRECATED_EXTRA_INFO.toString() + "\n");
    }

    private void assertStackTrace(TestFailure e, String... extraInfo) throws Exception {
        String pwStackTrace = printWriterToString((pw) -> e.printStackTrace(pw));
        assertStackTraceContents(e, "printStackTrace(PrintWriter)", pwStackTrace, extraInfo);

        String psStackTrace = printStreamToString((os) -> e.printStackTrace(os));
        assertStackTraceContents(e, "printStackTrace(PrintStream)", psStackTrace, extraInfo);
    }

    private void assertStackTraceContents(
            TestFailure e, String what, String stackTrace, String... extraInfo) throws Exception {
        var cause = e.getCause();
        String causeStackTrace = printWriterToString((writer) -> cause.printStackTrace(writer));

        expect.withMessage(what).that(stackTrace).startsWith(e + "\nCaused by: " + cause);
        expect.withMessage(what).that(stackTrace).contains(causeStackTrace);
        expect.withMessage(what).that(stackTrace).endsWith(String.join("\n", extraInfo) + "\n");
    }
}
