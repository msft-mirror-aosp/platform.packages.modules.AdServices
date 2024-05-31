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

package com.android.adservices.service.shell.adselection;

import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_AD_SELECTION_MOCK_AUCTION_RESULT;

import com.android.adservices.service.shell.ShellCommandTestCase;
import com.android.adservices.service.stats.ShellCommandStats;

import org.junit.Test;

public class MockAuctionResultCommandTest extends ShellCommandTestCase<MockAuctionResultCommand> {

    @ShellCommandStats.Command
    private static final int EXPECTED_COMMAND = COMMAND_AD_SELECTION_MOCK_AUCTION_RESULT;

    @Test
    public void testRun_missingArguments_returnsHelp() {
        runAndExpectInvalidArgument(
                new MockAuctionResultCommand(),
                MockAuctionResultCommand.HELP,
                EXPECTED_COMMAND,
                AdSelectionShellCommandFactory.COMMAND_PREFIX,
                MockAuctionResultCommand.CMD);
    }

    @Test
    public void testRun_withAllArguments_returnsSuccess() {
        // TODO(b/338389241): Replace with full implementation later.
        Result result =
                run(
                        new MockAuctionResultCommand(),
                        AdSelectionShellCommandFactory.COMMAND_PREFIX,
                        MockAuctionResultCommand.CMD,
                        MockAuctionResultArgs.AUCTION_RESULT,
                        "valid-auction-result");

        expectSuccess(result, EXPECTED_COMMAND);
    }
}
