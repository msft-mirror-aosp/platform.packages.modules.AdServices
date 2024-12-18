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
package com.android.adservices.shared.testing.common;

import static com.android.adservices.shared.testing.common.DumpHelper.assertDumpHasPrefix;
import static com.android.adservices.shared.testing.common.DumpHelper.dump;
import static com.android.adservices.shared.testing.common.DumpHelper.mockDump;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.android.adservices.shared.SharedExtendedMockitoTestCase;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import org.junit.Test;

import java.io.IOException;
import java.io.PrintWriter;

public final class DumpHelperTest extends SharedExtendedMockitoTestCase {

    @Test
    public void testDump_null() throws Exception {
        assertThrows(NullPointerException.class, () -> dump(null));
    }

    @Test
    public void testDump() throws Exception {
        String dump = dump(pw -> pw.write("I dump(), therefore I am()"));

        assertWithMessage("dump()").that(dump).isEqualTo("I dump(), therefore I am()");
    }

    @Test
    public void testDump_throwsRuntimeException() throws Exception {
        Exception cause = new RuntimeException("D'OH!");

        var actual = assertDumpThrowsWhenConsumerThrows(cause);

        expect.withMessage("actual exception").that(actual).isSameInstanceAs(cause);
    }

    @Test
    public void testDump_throwsIOException() throws Exception {
        Exception cause = new IOException("D'OH!");

        var actual = assertDumpThrowsWhenConsumerThrows(cause);

        expect.withMessage("actual exception").that(actual).isSameInstanceAs(cause);
    }

    @Test
    public void testDump_throwsNonIoCheckedException() throws Exception {
        Exception cause = new Exception("D'OH!");

        var actual = assertDumpThrowsWhenConsumerThrows(cause);

        assertWithMessage("actual exception").that(actual).isNotSameInstanceAs(cause);
        expect.withMessage("cause").that(actual).hasCauseThat().isSameInstanceAs(cause);
    }

    private static Exception assertDumpThrowsWhenConsumerThrows(Exception cause) throws Exception {
        return assertThrows(
                Exception.class,
                () ->
                        dump(
                                pw -> {
                                    throw cause;
                                }));
    }

    @Test
    public void testAssertDumpHasPrefix_null() throws Exception {
        assertThrows(AssertionError.class, () -> assertDumpHasPrefix(null, "pref."));
    }

    @Test
    public void testAssertDumpHasPrefix_empty() throws Exception {
        assertThrows(AssertionError.class, () -> assertDumpHasPrefix("", "pref."));
    }

    @Test
    public void testAssertDumpHasPrefix_singleLine_missingPrefix() throws Exception {
        AssertionError error =
                assertThrows(
                        AssertionError.class, () -> assertDumpHasPrefix("single line", "pref_"));
        assertWithMessage("exception")
                .that(error)
                .hasMessageThat()
                .matches(".*start with 'pref_'.*[0].*\nsingle line.*");
    }

    @Test
    public void testAssertDumpHasPrefix_singleLine_hasPrefix() throws Exception {
        String[] lines = assertDumpHasPrefix("pref_single_line", "pref_");

        assertWithMessage("lines")
                .that(lines)
                .asList()
                .containsExactly("pref_single_line")
                .inOrder();
    }

    @Test
    public void testAssertDumpHasPrefix_multipleLines_firstMissingPrefix() throws Exception {
        AssertionError error =
                assertThrows(
                        AssertionError.class,
                        () -> assertDumpHasPrefix("line1\npref_line2", "pref_"));

        assertWithMessage("exception")
                .that(error)
                .hasMessageThat()
                .matches(".*start with 'pref_'.*\\[0\\].*\nline1\npref_line2.*");
    }

    @Test
    public void testAssertDumpHasPrefix_multipleLines_secondMissingPrefix() throws Exception {
        AssertionError error =
                assertThrows(
                        AssertionError.class,
                        () -> assertDumpHasPrefix("pref_line1\nline2", "pref_"));

        assertWithMessage("exception")
                .that(error)
                .hasMessageThat()
                .matches(".*start with 'pref_'.*\\[1\\].*\npref_line1\nline2.*");
    }

    @Test
    public void testAssertDumpHasPrefix_multipleLines_allMissingPrefix() throws Exception {
        AssertionError error =
                assertThrows(
                        AssertionError.class, () -> assertDumpHasPrefix("line1\nline2", "pref_"));

        assertWithMessage("exception")
                .that(error)
                .hasMessageThat()
                .matches(".*start with 'pref_'.*\\[0, 1\\].*\nline1\nline2.*");
    }

    @Test
    public void testAssertDumpHasPrefix_multipleLines_allHavePrefix() throws Exception {
        String[] lines = assertDumpHasPrefix("pref_line1\npref_line2", "pref_");

        assertWithMessage("lines")
                .that(lines)
                .asList()
                .containsExactly("pref_line1", "pref_line2")
                .inOrder();
    }

    @Test
    public void testMockDump_null() {
        assertThrows(
                NullPointerException.class,
                () -> mockDump(/* invocation= */ null, /* pwArgIndex= */ 0, "Dumpo"));
        assertThrows(
                NullPointerException.class,
                () -> mockDump(/* invocation= */ () -> {}, /* pwArgIndex= */ 0, /* dump= */ null));
    }

    @Test
    @MockStatic(MyStaticDumper.class)
    public void testMockDump() {
        PrintWriter mockPw = mock(PrintWriter.class);

        mockDump(() -> MyStaticDumper.dump(any()), /* pwArgIndex= */ 0, "El Dumpo!");
        MyStaticDumper.dump(mockPw);

        verify(mockPw).println("El Dumpo!");
    }

    private static final class MyStaticDumper {

        static void dump(PrintWriter printWriter) {
            throw new UnsupportedOperationException("Should have been static mocked");
        }
    }
}
