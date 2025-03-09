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

package com.android.adservices.service.shell.adservicesapi;

import static com.android.adservices.service.devapi.DevSessionControllerResult.FAILURE;
import static com.android.adservices.service.devapi.DevSessionControllerResult.SUCCESS;
import static com.android.adservices.service.shell.adservicesapi.AdServicesApiShellCommandFactory.COMMAND_PREFIX;
import static com.android.adservices.service.shell.adservicesapi.DevSessionCommand.ARG_ERASE_DB;
import static com.android.adservices.service.shell.adservicesapi.DevSessionCommand.CMD;
import static com.android.adservices.service.shell.adservicesapi.DevSessionCommand.ERROR_ALREADY_IN_DEV_MODE;
import static com.android.adservices.service.shell.adservicesapi.DevSessionCommand.ERROR_FAILED_TO_RESET;
import static com.android.adservices.service.shell.adservicesapi.DevSessionCommand.ERROR_NEED_ACKNOWLEDGEMENT;
import static com.android.adservices.service.shell.adservicesapi.DevSessionCommand.OUTPUT_SUCCESS_FORMAT;
import static com.android.adservices.service.shell.adservicesapi.DevSessionCommand.SUB_CMD_END;
import static com.android.adservices.service.shell.adservicesapi.DevSessionCommand.SUB_CMD_START;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_DEV_SESSION;
import static com.android.adservices.shared.testing.concurrency.DeviceSideConcurrencyHelper.sleep;

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.service.devapi.DevSessionController;
import com.android.adservices.service.devapi.DevSessionControllerResult;
import com.android.adservices.service.shell.ShellCommandTestCase;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Test;

public final class DevSessionCommandTest extends ShellCommandTestCase<DevSessionCommand> {

    private final FakeDevSessionController mFakeDevSessionController =
            new FakeDevSessionController();

    private static final int EXPECTED_COMMAND = COMMAND_DEV_SESSION;

    @Test
    public void testRun_withoutEnableOrDisable_returnsHelp() {
        runAndExpectInvalidArgument(
                new DevSessionCommand(mFakeDevSessionController),
                DevSessionCommand.HELP,
                EXPECTED_COMMAND,
                CMD);
    }

    @Test
    public void testRun_withUnknownSubCommand_returnsHelp() {
        runAndExpectInvalidArgument(
                new DevSessionCommand(mFakeDevSessionController),
                DevSessionCommand.HELP,
                EXPECTED_COMMAND,
                CMD,
                "unknown-sub-command");
    }

    @Test
    public void testRun_startDevSessionWithoutAcknowledgement_returnsErrorMessage() {
        Result result =
                run(
                        new DevSessionCommand(mFakeDevSessionController),
                        COMMAND_PREFIX,
                        CMD,
                        SUB_CMD_START);

        assertThat(result.mErr).startsWith(ERROR_NEED_ACKNOWLEDGEMENT);
        assertThat(result.mOut).isEmpty();
        assertThat(mFakeDevSessionController.mNumCalls).isEqualTo(0);
    }

    @Test
    public void testRun_endDevSessionWithoutAcknowledgement_returnsErrorMessage() {
        mFakeDevSessionController.mReturnValue = FAILURE;

        Result result =
                run(
                        new DevSessionCommand(mFakeDevSessionController),
                        COMMAND_PREFIX,
                        CMD,
                        SUB_CMD_END);

        assertThat(result.mErr).startsWith(ERROR_NEED_ACKNOWLEDGEMENT);
        assertThat(result.mOut).isEmpty();
        assertThat(mFakeDevSessionController.mNumCalls).isEqualTo(0);
    }

    @Test
    public void testRun_startDevSession_resetIsCalled() {
        mFakeDevSessionController.mReturnValue = SUCCESS;

        Result result =
                run(
                        new DevSessionCommand(mFakeDevSessionController),
                        COMMAND_PREFIX,
                        CMD,
                        SUB_CMD_START,
                        ARG_ERASE_DB);

        assertThat(result.mOut).isEqualTo(String.format(OUTPUT_SUCCESS_FORMAT, true));
        assertThat(result.mErr).isEmpty();
        assertThat(mFakeDevSessionController.mNumCalls).isEqualTo(1);
        assertThat(mFakeDevSessionController.mDevModeState).isEqualTo(true);
    }

    @Test
    public void testRun_startDevSession_throwsErrorOnFailure() {
        mFakeDevSessionController.mReturnValue = FAILURE;

        Result result =
                run(
                        new DevSessionCommand(mFakeDevSessionController),
                        COMMAND_PREFIX,
                        CMD,
                        SUB_CMD_START,
                        ARG_ERASE_DB);

        assertThat(result.mOut).isEmpty();
        assertThat(result.mErr).isEqualTo(ERROR_FAILED_TO_RESET);
    }

    @Test
    public void testRun_startDevSession_throwsErrorOnTimeout() {
        Result result =
                run(
                        new DevSessionCommand(
                                new DevSessionController() {
                                    @Override
                                    public ListenableFuture<DevSessionControllerResult>
                                            startDevSession() throws IllegalStateException {
                                        sleep(
                                                DevSessionCommand.TIMEOUT_SEC,
                                                "sleeping to allow timeout");
                                        return Futures.immediateFuture(FAILURE);
                                    }

                                    @Override
                                    public ListenableFuture<DevSessionControllerResult>
                                            endDevSession() throws IllegalStateException {
                                        // Should not trigger this code path.
                                        return Futures.immediateFuture(FAILURE);
                                    }
                                }),
                        COMMAND_PREFIX,
                        CMD,
                        SUB_CMD_START,
                        ARG_ERASE_DB);

        assertThat(result.mOut).isEmpty();
        assertThat(result.mErr).isEqualTo(ERROR_FAILED_TO_RESET);
    }

    @Test
    public void testRun_endDevSession_resetIsCalled() {
        mFakeDevSessionController.mReturnValue = SUCCESS;

        Result result =
                run(
                        new DevSessionCommand(mFakeDevSessionController),
                        COMMAND_PREFIX,
                        CMD,
                        SUB_CMD_END,
                        ARG_ERASE_DB);

        assertThat(result.mOut).isEqualTo(String.format(OUTPUT_SUCCESS_FORMAT, false));
        assertThat(result.mErr).isEmpty();
        assertThat(mFakeDevSessionController.mNumCalls).isEqualTo(1);
        assertThat(mFakeDevSessionController.mDevModeState).isEqualTo(false);
    }

    @Test
    public void testRun_startDevSessionInDevSession_returnsNoOpError() {
        mFakeDevSessionController.mDevModeState = true;
        mFakeDevSessionController.mReturnValue = DevSessionControllerResult.NO_OP;

        Result result =
                run(
                        new DevSessionCommand(mFakeDevSessionController),
                        COMMAND_PREFIX,
                        CMD,
                        SUB_CMD_START,
                        ARG_ERASE_DB);

        assertThat(result.mErr).isEqualTo(ERROR_ALREADY_IN_DEV_MODE);
        assertThat(result.mOut).isEmpty();
        assertThat(mFakeDevSessionController.mNumCalls).isEqualTo(1);
        assertThat(mFakeDevSessionController.mDevModeState).isEqualTo(true);
    }

    @Test
    public void testRun_endDevSessionOutOfDevSession_returnsNoOpError() {
        mFakeDevSessionController.mDevModeState = false;
        mFakeDevSessionController.mReturnValue = DevSessionControllerResult.NO_OP;

        Result result =
                run(
                        new DevSessionCommand(mFakeDevSessionController),
                        COMMAND_PREFIX,
                        CMD,
                        SUB_CMD_END,
                        ARG_ERASE_DB);

        assertThat(result.mErr).isEqualTo(ERROR_ALREADY_IN_DEV_MODE);
        assertThat(result.mOut).isEmpty();
        assertThat(mFakeDevSessionController.mNumCalls).isEqualTo(1);
        assertThat(mFakeDevSessionController.mDevModeState).isEqualTo(false);
    }

    private static class FakeDevSessionController implements DevSessionController {

        Boolean mDevModeState = null;
        DevSessionControllerResult mReturnValue = DevSessionControllerResult.UNKNOWN;
        int mNumCalls = 0;

        @Override
        public ListenableFuture<DevSessionControllerResult> startDevSession() {
            mDevModeState = true;
            mNumCalls += 1;
            return Futures.immediateFuture(mReturnValue);
        }

        @Override
        public ListenableFuture<DevSessionControllerResult> endDevSession()
                throws IllegalStateException {
            mDevModeState = false;
            mNumCalls += 1;
            return Futures.immediateFuture(mReturnValue);
        }
    }
}
