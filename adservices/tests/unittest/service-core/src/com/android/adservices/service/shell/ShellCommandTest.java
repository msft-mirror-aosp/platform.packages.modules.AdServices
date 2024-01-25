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

import com.android.adservices.common.AdServicesUnitTestCase;

import java.io.PrintWriter;
import java.io.StringWriter;

abstract class ShellCommandTest<T extends ShellCommand> extends AdServicesUnitTestCase {
    private final T mCmd;

    ShellCommandTest(T cmd) {
        mCmd = cmd;
    }

    Result run(String... args) {
        StringWriter outStringWriter = new StringWriter();
        PrintWriter outPw = new PrintWriter(outStringWriter);
        StringWriter errStringWriter = new StringWriter();
        PrintWriter errPw = new PrintWriter(errStringWriter);

        int status = mCmd.run(outPw, errPw, args);
        String out = getResultAndClosePrintWriter(outPw, outStringWriter);
        String err = getResultAndClosePrintWriter(errPw, errStringWriter);
        return new Result(out, err, status);
    }

    void expectSuccess(Result actual, String expectedOut) {
        expect.withMessage("result").that(actual.mStatus).isAtLeast(0);
        expect.withMessage("out").that(actual.mOut).isEqualTo(expectedOut);
        expect.withMessage("err").that(actual.mErr).isEmpty();
    }

    void expectFailure(Result actual, String expectedErr) {
        expect.withMessage("result").that(actual.mStatus).isLessThan(0);
        expect.withMessage("out").that(actual.mOut).isEmpty();
        expect.withMessage("err").that(actual.mErr).isEqualTo(expectedErr);
    }

    private String getResultAndClosePrintWriter(PrintWriter pw, StringWriter sw) {
        pw.flush();
        String out = sw.toString();
        pw.close();
        return out;
    }

    static final class Result {
        final String mOut;
        final String mErr;
        final int mStatus;

        Result(String out, String err, int status) {
            mOut = out;
            mErr = err;
            mStatus = status;
        }
    }
}
