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

import static com.android.adservices.service.shell.common.EchoCommand.CMD_ECHO;
import static com.android.adservices.service.shell.common.EchoCommand.HELP_ECHO;
import static com.android.adservices.service.shell.common.IsAllowedAdSelectionAccessCommand.CMD_IS_ALLOWED_AD_SELECTION_ACCESS;
import static com.android.adservices.service.shell.common.IsAllowedAdSelectionAccessCommand.HELP_IS_ALLOWED_AD_SELECTION_ACCESS;
import static com.android.adservices.service.shell.common.IsAllowedAttributionAccessCommand.CMD_IS_ALLOWED_ATTRIBUTION_ACCESS;
import static com.android.adservices.service.shell.common.IsAllowedAttributionAccessCommand.HELP_IS_ALLOWED_ATTRIBUTION_ACCESS;
import static com.android.adservices.service.shell.common.IsAllowedCustomAudiencesAccessCommand.CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS;
import static com.android.adservices.service.shell.common.IsAllowedCustomAudiencesAccessCommand.HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS;
import static com.android.adservices.service.shell.common.IsAllowedProtectedSignalsAccessCommand.CMD_IS_ALLOWED_PROTECTED_SIGNALS_ACCESS;
import static com.android.adservices.service.shell.common.IsAllowedProtectedSignalsAccessCommand.HELP_IS_ALLOWED_PROTECTED_SIGNALS_ACCESS;
import static com.android.adservices.service.shell.common.IsAllowedTopicsAccessCommand.CMD_IS_ALLOWED_TOPICS_ACCESS;
import static com.android.adservices.service.shell.common.IsAllowedTopicsAccessCommand.HELP_IS_ALLOWED_TOPICS_ACCESS;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.service.shell.common.EchoCommand;
import com.android.adservices.service.shell.common.IsAllowedAdSelectionAccessCommand;
import com.android.adservices.service.shell.common.IsAllowedAttributionAccessCommand;
import com.android.adservices.service.shell.common.IsAllowedCustomAudiencesAccessCommand;
import com.android.adservices.service.shell.common.IsAllowedProtectedSignalsAccessCommand;
import com.android.adservices.service.shell.common.IsAllowedTopicsAccessCommand;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

public final class CommonShellCommandFactoryTest extends AdServicesUnitTestCase {
    private final ShellCommandFactory mFactory = new CommonShellCommandFactory();

    @Test
    public void testGetShellCommand() {
        expect.withMessage(CMD_ECHO)
                .that(mFactory.getShellCommand(CMD_ECHO))
                .isInstanceOf(EchoCommand.class);
    }

    @Test
    public void testGetShellCommand_isAllowedTopicsAccess() {
        expect.withMessage(CMD_ECHO)
                .that(mFactory.getShellCommand(CMD_IS_ALLOWED_TOPICS_ACCESS))
                .isInstanceOf(IsAllowedTopicsAccessCommand.class);
    }

    @Test
    public void testGetShellCommand_isAllowedCustomAudienceAccess() {
        expect.withMessage(CMD_ECHO)
                .that(mFactory.getShellCommand(CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS))
                .isInstanceOf(IsAllowedCustomAudiencesAccessCommand.class);
    }

    @Test
    public void testGetShellCommand_isAllowedAdSelectionAccess() {
        expect.withMessage(CMD_ECHO)
                .that(mFactory.getShellCommand(CMD_IS_ALLOWED_AD_SELECTION_ACCESS))
                .isInstanceOf(IsAllowedAdSelectionAccessCommand.class);
    }

    @Test
    public void testGetShellCommand_isAllowedAttributionAccess() {
        expect.withMessage(CMD_ECHO)
                .that(mFactory.getShellCommand(CMD_IS_ALLOWED_ATTRIBUTION_ACCESS))
                .isInstanceOf(IsAllowedAttributionAccessCommand.class);
    }

    @Test
    public void testGetShellCommand_isProtectedAppSignalsAccess() {
        expect.withMessage(CMD_ECHO)
                .that(mFactory.getShellCommand(CMD_IS_ALLOWED_PROTECTED_SIGNALS_ACCESS))
                .isInstanceOf(IsAllowedProtectedSignalsAccessCommand.class);
    }

    @Test
    public void testGetShellCommand_invalidCommand() {
        String cmd = "abc";

        expect.withMessage(cmd).that(mFactory.getShellCommand(cmd)).isNull();
    }

    @Test
    public void test_getAllCommandsHelp() {
        ImmutableList<String> expectedHelpSet =
                ImmutableList.of(
                        HELP_ECHO,
                        HELP_IS_ALLOWED_ATTRIBUTION_ACCESS,
                        HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS,
                        HELP_IS_ALLOWED_PROTECTED_SIGNALS_ACCESS,
                        HELP_IS_ALLOWED_AD_SELECTION_ACCESS,
                        HELP_IS_ALLOWED_TOPICS_ACCESS);
        expect.withMessage("all command help")
                .that(mFactory.getAllCommandsHelp())
                .isEqualTo(expectedHelpSet);
    }
}
