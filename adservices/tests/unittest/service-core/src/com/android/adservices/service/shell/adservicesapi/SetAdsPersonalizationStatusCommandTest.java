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

import static com.android.adservices.service.shell.adservicesapi.AdServicesApiShellCommandFactory.COMMAND_PREFIX;
import static com.android.adservices.service.shell.adservicesapi.SetAdsPersonalizationStatusCommand.CMD_SET_ADS_PERSONALIZATION_STATUS;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_GENERIC_ERROR;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

import android.adservices.common.AdServicesCommonManager;
import android.os.Build;
import android.os.OutcomeReceiver;

import com.android.adservices.service.shell.ShellCommandTestCase;
import com.android.adservices.service.stats.ShellCommandStats;
import com.android.modules.utils.testing.ExtendedMockitoRule;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

@ExtendedMockitoRule.SpyStatic(AdServicesCommonManager.class)
public final class SetAdsPersonalizationStatusCommandTest
        extends ShellCommandTestCase<SetAdsPersonalizationStatusCommand> {

    @Mock private AdServicesCommonManager mMockAdServicesCommonManager;

    @Before
    public void setup() {
        doReturn(mMockAdServicesCommonManager).when(() -> AdServicesCommonManager.get(any()));
        doAnswer(
                        invocation -> {
                            OutcomeReceiver<Boolean, Exception> cb = invocation.getArgument(2);
                            cb.onResult(true);
                            return null;
                        })
                .when(mMockAdServicesCommonManager)
                .setAdsPersonalizationStatus(anyInt(), any(), any(OutcomeReceiver.class));
    }

    @Test
    public void testRun_invalid() {
        SetAdsPersonalizationStatusCommand setAdsPersonalizationStatusCommand =
                new SetAdsPersonalizationStatusCommand();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Result actualResult =
                    run(
                            new SetAdsPersonalizationStatusCommand(),
                            COMMAND_PREFIX,
                            CMD_SET_ADS_PERSONALIZATION_STATUS,
                            "enabled");
            String expectedErr =
                    String.format(
                            "Command is not supported on Android version: %d",
                            Build.VERSION.SDK_INT);

            expectFailure(
                    actualResult,
                    expectedErr,
                    ShellCommandStats.COMMAND_SET_ADS_PERSONALIZATION_STATUS,
                    RESULT_GENERIC_ERROR);
        } else {
            // arg not correct
            runAndExpectInvalidArgument(
                    setAdsPersonalizationStatusCommand,
                    SetAdsPersonalizationStatusCommand.HELP,
                    ShellCommandStats.COMMAND_SET_ADS_PERSONALIZATION_STATUS,
                    COMMAND_PREFIX,
                    CMD_SET_ADS_PERSONALIZATION_STATUS,
                    "foo bar");
        }
    }

    @Test
    public void testRun_valid() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU);

        Result actualResult =
                run(
                        new SetAdsPersonalizationStatusCommand(),
                        COMMAND_PREFIX,
                        CMD_SET_ADS_PERSONALIZATION_STATUS,
                        "enabled");
        expectSuccess(actualResult, ShellCommandStats.COMMAND_SET_ADS_PERSONALIZATION_STATUS);

        expect.withMessage("out")
                .that(actualResult.mOut)
                .startsWith("Set Ads Personalization Status to");
    }

    @Test
    public void testGetCommandName_valid() {
        expect.that(new SetAdsPersonalizationStatusCommand().getCommandName())
                .isEqualTo(CMD_SET_ADS_PERSONALIZATION_STATUS);
    }

    @Test
    public void testGetCommandHelp_valid() {
        expect.that(new SetAdsPersonalizationStatusCommand().getCommandHelp())
                .isEqualTo(SetAdsPersonalizationStatusCommand.HELP);
    }
}
