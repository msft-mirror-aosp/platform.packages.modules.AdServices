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

import static com.android.adservices.service.shell.common.IsAllowedCustomAudiencesAccessCommand.CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS;
import static com.android.adservices.service.shell.common.IsAllowedCustomAudiencesAccessCommand.HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import com.android.adservices.service.common.AppManifestConfigHelper;
import com.android.adservices.service.shell.ShellCommandTestCase;
import com.android.adservices.service.stats.ShellCommandStats;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;

@SpyStatic(AppManifestConfigHelper.class)
public final class IsAllowedCustomAudiencesAccessCommandTest
        extends ShellCommandTestCase<IsAllowedCustomAudiencesAccessCommand> {
    private static final String PKG_NAME = "d.h.a.r.m.a";
    private static final String ENROLLMENT_ID = "42";

    @Test
    public void testRun_invalid() {
        IsAllowedCustomAudiencesAccessCommand cmd = new IsAllowedCustomAudiencesAccessCommand();

        // no args
        runAndExpectInvalidArgument(
                cmd,
                HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS,
                ShellCommandStats.COMMAND_IS_ALLOWED_CUSTOM_AUDIENCE_ACCESS,
                CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS);
        // missing id
        runAndExpectInvalidArgument(
                cmd,
                HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS,
                ShellCommandStats.COMMAND_IS_ALLOWED_CUSTOM_AUDIENCE_ACCESS,
                CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS,
                PKG_NAME);
    }

    @Test
    public void testRun_valid() {
        IsAllowedCustomAudiencesAccessCommand cmd = new IsAllowedCustomAudiencesAccessCommand();
        doReturn(true)
                .when(
                        () ->
                                AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                        PKG_NAME, ENROLLMENT_ID));

        Result actualResult =
                run(cmd, CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS, PKG_NAME, ENROLLMENT_ID);

        expectSuccess(
                actualResult,
                "true\n",
                ShellCommandStats.COMMAND_IS_ALLOWED_CUSTOM_AUDIENCE_ACCESS);
    }

    @Test
    public void testGetCommandName_valid() {
        expect.that(new IsAllowedCustomAudiencesAccessCommand().getCommandName())
                .isEqualTo(CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS);
    }

    @Test
    public void testGetCommandHelp_valid() {
        expect.that(new IsAllowedCustomAudiencesAccessCommand().getCommandHelp())
                .isEqualTo(HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS);
    }
}
