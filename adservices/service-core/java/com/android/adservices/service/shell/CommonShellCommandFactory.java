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

import androidx.annotation.Nullable;

import com.android.adservices.service.shell.common.EchoCommand;
import com.android.adservices.service.shell.common.IsAllowedAdSelectionAccessCommand;
import com.android.adservices.service.shell.common.IsAllowedAttributionAccessCommand;
import com.android.adservices.service.shell.common.IsAllowedCustomAudiencesAccessCommand;
import com.android.adservices.service.shell.common.IsAllowedProtectedSignalsAccessCommand;
import com.android.adservices.service.shell.common.IsAllowedTopicsAccessCommand;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * Factory class which handles common shell commands. API specific shell commands should be part of
 * API specific factory.
 */
public final class CommonShellCommandFactory implements ShellCommandFactory {

    private static final ImmutableList<String> ALL_COMMANDS_HELP =
            ImmutableList.of(
                    HELP_ECHO,
                    HELP_IS_ALLOWED_ATTRIBUTION_ACCESS,
                    HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS,
                    HELP_IS_ALLOWED_PROTECTED_SIGNALS_ACCESS,
                    HELP_IS_ALLOWED_AD_SELECTION_ACCESS,
                    HELP_IS_ALLOWED_TOPICS_ACCESS);

    public static ShellCommandFactory getInstance() {
        return new CommonShellCommandFactory();
    }

    @Nullable
    @Override
    public ShellCommand getShellCommand(String cmd) {
        switch (cmd) {
            case CMD_ECHO:
                return new EchoCommand();
            case CMD_IS_ALLOWED_TOPICS_ACCESS:
                return new IsAllowedTopicsAccessCommand();
            case CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS:
                return new IsAllowedCustomAudiencesAccessCommand();
            case CMD_IS_ALLOWED_AD_SELECTION_ACCESS:
                return new IsAllowedAdSelectionAccessCommand();
            case CMD_IS_ALLOWED_ATTRIBUTION_ACCESS:
                return new IsAllowedAttributionAccessCommand();
            case CMD_IS_ALLOWED_PROTECTED_SIGNALS_ACCESS:
                return new IsAllowedProtectedSignalsAccessCommand();
            default:
                return null;
        }
    }

    @Override
    public String getCommandPrefix() {
        return "";
    }

    @Override
    public List<String> getAllCommandsHelp() {
        return ALL_COMMANDS_HELP;
    }
}
