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

package com.android.adservices.service.shell;

import static com.android.adservices.service.shell.AbstractShellCommand.ERROR_TEMPLATE_INVALID_ARGS;
import static com.android.adservices.service.stats.ShellCommandStats.Command;
import static com.android.adservices.service.stats.ShellCommandStats.CommandResult;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_INVALID_ARGS;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_SUCCESS;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;

/**
 * Abstract class to perform unit testing for a {@link ShellCommand}
 *
 * @param <T>
 */
public abstract class ShellCommandTestCase<T extends ShellCommand>
        extends AdServicesExtendedMockitoTestCase {

    /** Runs the provided shell command with its arguments. */
    public Result run(T cmd, String... args) {
        StringWriter outStringWriter = new StringWriter();
        PrintWriter outPw = new PrintWriter(outStringWriter);
        StringWriter errStringWriter = new StringWriter();
        PrintWriter errPw = new PrintWriter(errStringWriter);

        ShellCommandResult result = cmd.run(outPw, errPw, args);
        String out = getResultAndClosePrintWriter(outPw, outStringWriter);
        String err = getResultAndClosePrintWriter(errPw, errStringWriter);
        return new Result(out, err, result.getResultCode(), result.getCommand());
    }

    /**
     * Expects success in the result and the expected output from the execution of the Shell
     * Command.
     */
    public void expectSuccess(Result actual, String expectedOut, @Command int expectedCommand) {
        expect.withMessage("resultCode").that(actual.mResultCode).isEqualTo(RESULT_SUCCESS);
        expect.withMessage("out").that(actual.mOut).isEqualTo(expectedOut);
        expect.withMessage("err").that(actual.mErr).isEmpty();
        expect.withMessage("command").that(actual.mCommand).isEqualTo(expectedCommand);
    }

    /** Expects success in the result from the execution of the Shell Command. */
    public void expectSuccess(Result actual, @Command int expectedCommand) {
        expect.withMessage("resultCode").that(actual.mResultCode).isEqualTo(RESULT_SUCCESS);
        expect.withMessage("err").that(actual.mErr).isEmpty();
        expect.withMessage("command").that(actual.mCommand).isEqualTo(expectedCommand);
    }

    /** Expects failure in the result from the execution of the Shell Command. */
    public void expectFailure(
            Result actual,
            String expectedErr,
            @Command int expectedCommand,
            @CommandResult int expectedCommandResult) {
        expect.withMessage("resultCode").that(actual.mResultCode).isEqualTo(expectedCommandResult);
        expect.withMessage("out").that(actual.mOut).isEmpty();
        expect.withMessage("err").that(actual.mErr).endsWith(expectedErr);
        expect.withMessage("command").that(actual.mCommand).isEqualTo(expectedCommand);
    }

    /** Expects invalid arguments in the result from the execution of the Shell Command. */
    public void runAndExpectInvalidArgument(
            T cmd, String syntax, @Command int expectedCommand, String... args) {
        Result actualResult = run(cmd, args);
        String expectedErr =
                String.format(ERROR_TEMPLATE_INVALID_ARGS, Arrays.toString(args), syntax);

        expectFailure(actualResult, expectedErr, expectedCommand, RESULT_INVALID_ARGS);
    }

    private String getResultAndClosePrintWriter(PrintWriter pw, StringWriter sw) {
        pw.flush();
        String out = sw.toString();
        pw.close();
        return out;
    }

    /** POJO to capture the Result of a {@link ShellCommand} */
    public static final class Result {
        public final String mOut;
        public final String mErr;
        public final int mResultCode;
        public final int mCommand;

        Result(String out, String err, int resultCode, int command) {
            mOut = out;
            mErr = err;
            mResultCode = resultCode;
            mCommand = command;
        }
    }
}
