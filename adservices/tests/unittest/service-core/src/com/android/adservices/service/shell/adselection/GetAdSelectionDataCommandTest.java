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

import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_AD_SELECTION_GET_AD_SELECTION_DATA;

import static org.mockito.Mockito.when;

import com.android.adservices.service.adselection.BuyerInputGenerator;
import com.android.adservices.service.shell.ShellCommandTestCase;
import com.android.adservices.service.stats.ShellCommandStats;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;

import org.junit.Test;
import org.mockito.Mock;

import java.util.Map;

public class GetAdSelectionDataCommandTest extends ShellCommandTestCase<GetAdSelectionDataCommand> {

    @ShellCommandStats.Command
    private static final int EXPECTED_COMMAND = COMMAND_AD_SELECTION_GET_AD_SELECTION_DATA;

    @Mock private BuyerInputGenerator mBuyerInputGenerator;

    @Test
    public void testRun_missingBuyerArgument_returnsHelp() {
        runAndExpectInvalidArgument(
                new GetAdSelectionDataCommand(mBuyerInputGenerator),
                GetAdSelectionDataCommand.HELP,
                EXPECTED_COMMAND,
                AdSelectionShellCommandFactory.COMMAND_PREFIX,
                GetAdSelectionDataCommand.CMD);
    }

    @Test
    public void testRun_withAllArguments_returnsSuccess() {
        when(mBuyerInputGenerator.createCompressedBuyerInputs())
                .thenReturn(FluentFuture.from(Futures.immediateFuture(Map.of())));

        // TODO(b/339851172): Replace with full implementation later.
        Result result =
                run(
                        new GetAdSelectionDataCommand(mBuyerInputGenerator),
                        AdSelectionShellCommandFactory.COMMAND_PREFIX,
                        GetAdSelectionDataCommand.CMD,
                        GetAdSelectionDataArgs.BUYER,
                        "valid-buyer");

        expectSuccess(result, EXPECTED_COMMAND);
    }
}
