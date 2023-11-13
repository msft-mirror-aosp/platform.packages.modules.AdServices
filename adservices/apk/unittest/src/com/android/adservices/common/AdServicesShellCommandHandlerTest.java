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

package com.android.adservices.common;

import static com.android.adservices.common.AdServicesShellCommandHandler.CMD_ECHO;
import static com.android.adservices.common.AdServicesShellCommandHandler.ERROR_EMPTY_COMMAND;
import static com.android.adservices.common.AdServicesShellCommandHandler.ERROR_ECHO_EMPTY;
import static com.android.adservices.common.AdServicesShellCommandHandler.ERROR_ECHO_HIGHLANDER;
import static com.android.adservices.common.AdServicesShellCommandHandler.HELP_ECHO;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.os.Binder;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;

public final class AdServicesShellCommandHandlerTest {

    public @Rule final Expect expect = Expect.create();

    private final StringWriter mOutStringWriter = new StringWriter();
    private final PrintWriter mOut = new PrintWriter(mOutStringWriter);

    private final AdServicesShellCommandHandler mCmd =
            new AdServicesShellCommandHandler(() -> mOut);

    @Test
    public void testInvalidConstructor() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> new AdServicesShellCommandHandler((FileDescriptor) null));
    }

    @Test
    public void testRun_invalidArgs() throws Exception {
        assertThrows(NullPointerException.class, () -> mCmd.run(/* args...= */ (String[]) null));
        assertThrows(IllegalArgumentException.class, () -> mCmd.run(/* args...= */ new String[0]));
    }

    @Test
    public void testExec() throws Exception {
        assertThrows(
                UnsupportedOperationException.class,
                () ->
                        mCmd.exec(
                                new Binder(),
                                FileDescriptor.in,
                                FileDescriptor.out,
                                FileDescriptor.err,
                                /* args= */ (String[]) null));
    }

    @Test
    public void testErrWriterIsSameAsOutWriter() throws Exception {
        PrintWriter outWriter = mCmd.getOutPrintWriter();

        assertWithMessage("err").that(mCmd.getErrPrintWriter()).isSameInstanceAs(outWriter);
    }

    @Test
    public void testOnHelp() throws Exception {
        mCmd.onHelp();

        assertHelpContents(getOut());
    }

    @Test
    public void testRunHelp() throws Exception {
        String result = runInvalid("help");

        assertHelpContents(result);
    }

    @Test
    public void testRunHelpShort() throws Exception {
        String result = runInvalid("-h");

        assertHelpContents(result);
    }

    @Test
    public void testRunEcho_noArg() throws Exception {
        String result = runInvalid(CMD_ECHO);

        expect.withMessage("result of %s", CMD_ECHO).that(result).isEqualTo(ERROR_ECHO_EMPTY);
    }

    @Test
    public void testRunEcho_oneArg() throws Exception {
        String result = runValid(CMD_ECHO, "108");

        expect.withMessage("result of '%s 108'", CMD_ECHO).that(result).isEqualTo("108\n");
    }

    @Test
    public void testRunEcho_multipleArgs() throws Exception {
        String result = runInvalid(CMD_ECHO, "4", "8", "15", "16", "23", "42");

        expect.withMessage("result of '%s 4 8 15 16 23 42'", CMD_ECHO)
                .that(result)
                .isEqualTo(ERROR_ECHO_HIGHLANDER);
    }

    @Test
    public void testRun_noCommand() throws Exception {
        String result = runInvalid("");

        expect.withMessage("result of '%s'").that(result).isEqualTo(ERROR_EMPTY_COMMAND);
    }

    @Test
    public void testRun_invalidCommand() throws Exception {
        String cmd = "I can't believe this command is valid";
        String result = runInvalid(cmd);

        expect.withMessage("result of '%s'", cmd).that(result).contains("Unknown command: " + cmd);
    }

    private String getOut() throws IOException {
        mOut.flush();
        return mOutStringWriter.toString();
    }

    /** Runs a command that is expected to return a positive result. */
    private String runValid(String... args) throws IOException {
        int result = mCmd.run(args);
        expect.withMessage("result of run(%s)", Arrays.toString(args)).that(result).isAtLeast(0);

        return getOut();
    }

    /** Runs a command that is expected to return a negative result. */
    private String runInvalid(String... args) throws IOException {
        int result = mCmd.run(args);
        expect.withMessage("result of run(%s)", Arrays.toString(args)).that(result).isLessThan(0);

        return getOut();
    }

    private void assertHelpContents(String help) {
        expect.withMessage("help").that(help.split("\n")).asList().containsExactly(HELP_ECHO);
    }
}
