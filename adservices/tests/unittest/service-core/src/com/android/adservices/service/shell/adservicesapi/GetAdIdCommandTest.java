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
import static com.android.adservices.service.shell.adservicesapi.GetAdIdCommand.CMD_GET_ADID;
import static com.android.adservices.service.shell.adservicesapi.GetAdIdCommand.HELP_GET_ADID;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_GET_AD_ID;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.mockito.ArgumentMatchers.any;

import android.adservices.adid.AdId;
import android.adservices.adid.AdIdManager;
import android.os.Build;
import android.os.OutcomeReceiver;

import com.android.adservices.service.shell.ShellCommandTestCase;
import com.android.adservices.service.stats.ShellCommandStats;
import com.android.modules.utils.testing.ExtendedMockitoRule;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

@ExtendedMockitoRule.SpyStatic(AdIdManager.class)
public final class GetAdIdCommandTest extends ShellCommandTestCase<GetAdIdCommand> {

    @Mock private AdIdManager mMockAdIdManager;

    @Before
    public void setup() {
        doReturn(mMockAdIdManager).when(() -> AdIdManager.get(any()));
        doAnswer(
                        invocation -> {
                            OutcomeReceiver<AdId, Exception> cb = invocation.getArgument(1);
                            cb.onResult(new AdId("test-ad-id", false));
                            return null;
                        })
                .when(mMockAdIdManager)
                .getAdId(any(), any(OutcomeReceiver.class));
    }

    @Test
    public void testRun_invalid() {
        GetAdIdCommand getAdIdCommand = new GetAdIdCommand();

        // Simulate extra arguments (e.g., "adservices get-adid --some-extra-param")
        runAndExpectInvalidArgument(
                getAdIdCommand,
                HELP_GET_ADID,
                ShellCommandStats.COMMAND_GET_AD_ID,
                COMMAND_PREFIX,
                CMD_GET_ADID,
                "foo-bar"); // Extra argument
    }

    @Test
    public void testRun_valid() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S);

        Result actualResult = run(new GetAdIdCommand(), COMMAND_PREFIX, CMD_GET_ADID);

        expectSuccess(actualResult, COMMAND_GET_AD_ID);

        expect.withMessage("out").that(actualResult.mOut.trim()).isEqualTo("test-ad-id");
    }

    @Test
    public void testGetCommandName_valid() {
        expect.that(new GetAdIdCommand().getCommandName()).isEqualTo(CMD_GET_ADID);
    }

    @Test
    public void testGetCommandHelp_valid() {
        expect.that(new GetAdIdCommand().getCommandHelp()).isEqualTo(HELP_GET_ADID);
    }
}
