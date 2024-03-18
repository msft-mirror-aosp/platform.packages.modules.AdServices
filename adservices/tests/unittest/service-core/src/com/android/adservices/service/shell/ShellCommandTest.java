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

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;

/**
 * Abstract class to perform unit testing for a {@link ShellCommand}
 *
 * @param <T>
 */
public abstract class ShellCommandTest<T extends ShellCommand>
        extends AdServicesExtendedMockitoTestCase {

    /** Runs the provided shell command with its arguments. */
    public Result run(T cmd, String... args) {
        StringWriter outStringWriter = new StringWriter();
        PrintWriter outPw = new PrintWriter(outStringWriter);
        StringWriter errStringWriter = new StringWriter();
        PrintWriter errPw = new PrintWriter(errStringWriter);

        int status = cmd.run(outPw, errPw, args);
        String out = getResultAndClosePrintWriter(outPw, outStringWriter);
        String err = getResultAndClosePrintWriter(errPw, errStringWriter);
        return new Result(out, err, status);
    }

    /**
     * Expects success in the result and the expected output from the execution of the Shell
     * Command.
     */
    public void expectSuccess(Result actual, String expectedOut) {
        expect.withMessage("result").that(actual.mStatus).isEqualTo(0);
        expect.withMessage("out").that(actual.mOut).isEqualTo(expectedOut);
        expect.withMessage("err").that(actual.mErr).isEmpty();
    }

    /** Expects success in the result from the execution of the Shell Command. */
    public void expectSuccess(Result actual) {
        expect.withMessage("result").that(actual.mStatus).isEqualTo(0);
        expect.withMessage("err").that(actual.mErr).isEmpty();
    }

    /** Expects failure in the result from the execution of the Shell Command. */
    public void expectFailure(Result actual, String expectedErr) {
        expect.withMessage("result").that(actual.mStatus).isLessThan(0);
        expect.withMessage("out").that(actual.mOut).isEmpty();
        expect.withMessage("err").that(actual.mErr).endsWith(expectedErr);
    }

    /** Expects invalid arguments in the result from the execution of the Shell Command. */
    public void runAndExpectInvalidArgument(T cmd, String syntax, String... args) {
        Result actualResult = run(cmd, args);
        String expectedErr =
                String.format(ERROR_TEMPLATE_INVALID_ARGS, Arrays.toString(args), syntax);

        expectFailure(actualResult, expectedErr);
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
        public final int mStatus;

        Result(String out, String err, int status) {
            mOut = out;
            mErr = err;
            mStatus = status;
        }
    }
}
