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

import static com.android.adservices.shared.testing.TestFailure.EXTRA_INFO_HEADER;
import static com.android.adservices.shared.testing.TestFailure.MESSAGE;
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

        expect.withMessage("getMessage()").that(e.getMessage()).isEqualTo(MESSAGE);

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

        expect.withMessage("getMessage()").that(e.getMessage()).isEqualTo(MESSAGE);

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
        expect.withMessage(what)
                .that(stackTrace)
                .endsWith(EXTRA_INFO_HEADER + String.join("\n", extraInfo) + "\n");
    }
}
