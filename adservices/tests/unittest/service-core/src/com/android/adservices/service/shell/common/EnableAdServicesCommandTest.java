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

package com.android.adservices.service.shell.common;

import static com.android.adservices.service.shell.common.EnableAdServicesCommand.CMD_ENABLE_ADSERVICES;
import static com.android.adservices.service.shell.common.EnableAdServicesCommand.HELP_ENABLE_ADSERVICES;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.mockito.ArgumentMatchers.any;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesOutcomeReceiver;

import com.android.adservices.service.shell.ShellCommandTestCase;
import com.android.adservices.service.stats.ShellCommandStats;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

@SpyStatic(AdServicesCommonManager.class)
public final class EnableAdServicesCommandTest
        extends ShellCommandTestCase<EnableAdServicesCommand> {

    @Mock private AdServicesCommonManager mMockAdServicesCommonManager;

    @Before
    public void setup() {
        doReturn(mMockAdServicesCommonManager).when(() -> AdServicesCommonManager.get(any()));
        doAnswer(
                        invocation -> {
                            AdServicesOutcomeReceiver<Boolean, Exception> cb =
                                    invocation.getArgument(2);
                            cb.onResult(true);
                            return null;
                        })
                .when(mMockAdServicesCommonManager)
                .enableAdServices(any(), any(), any(AdServicesOutcomeReceiver.class));
    }

    @Test
    public void testRun_invalid() {
        EnableAdServicesCommand enableAdServicesCommand = new EnableAdServicesCommand();

        // only provide key, missing value in the args
        runAndExpectInvalidArgument(
                enableAdServicesCommand,
                HELP_ENABLE_ADSERVICES,
                ShellCommandStats.COMMAND_ENABLE_ADSERVICES,
                CMD_ENABLE_ADSERVICES,
                "--u18");
    }

    @Test
    public void testRun_valid() {
        Result actualResult =
                run(new EnableAdServicesCommand(), CMD_ENABLE_ADSERVICES, "--u18", "true");

        expectSuccess(actualResult, "", ShellCommandStats.COMMAND_ENABLE_ADSERVICES);
    }

    @Test
    public void testGetCommandName_valid() {
        expect.that(new EnableAdServicesCommand().getCommandName())
                .isEqualTo(CMD_ENABLE_ADSERVICES);
    }

    @Test
    public void testGetCommandHelp_valid() {
        expect.that(new EnableAdServicesCommand().getCommandHelp())
                .isEqualTo(HELP_ENABLE_ADSERVICES);
    }
}
