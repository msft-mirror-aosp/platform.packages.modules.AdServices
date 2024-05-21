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

import static com.android.adservices.service.DebugFlagsConstants.KEY_AD_SELECTION_CLI_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_FLEDGE_IS_CONSENTED_DEBUGGING_CLI_ENABLED;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.ConsentedDebugConfigurationDao;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.shell.AdServicesShellCommandHandler;
import com.android.adservices.service.shell.NoOpShellCommand;
import com.android.adservices.service.shell.ShellCommand;
import com.android.adservices.service.shell.ShellCommandFactory;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableSet;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AdSelectionShellCommandFactory implements ShellCommandFactory {

    public static final String COMMAND_PREFIX = "ad_selection";
    private final Map<String, ShellCommand> mAllCommandsMap;
    private final boolean mIsConsentedDebugCliEnabled;
    private final boolean mIsAdSelectionCliEnabled;

    @VisibleForTesting
    public AdSelectionShellCommandFactory(
            boolean isConsentedDebugCliEnabled,
            boolean isAdSelectionCliEnabled,
            @NonNull ConsentedDebugConfigurationDao consentedDebugConfigurationDao) {
        Objects.requireNonNull(consentedDebugConfigurationDao);

        mIsConsentedDebugCliEnabled = isConsentedDebugCliEnabled;
        mIsAdSelectionCliEnabled = isAdSelectionCliEnabled;
        Set<ShellCommand> allCommands =
                ImmutableSet.of(
                        new ConsentedDebugShellCommand(consentedDebugConfigurationDao),
                        new GetAdSelectionDataCommand());
        mAllCommandsMap =
                allCommands.stream()
                        .collect(
                                Collectors.toMap(
                                        ShellCommand::getCommandName, Function.identity()));
    }

    /**
     * @return an instance of the {@link AdSelectionShellCommandFactory}.
     */
    public static AdSelectionShellCommandFactory getInstance(
            DebugFlags debugFlags, Context context) {
        return new AdSelectionShellCommandFactory(
                debugFlags.getFledgeConsentedDebuggingCliEnabledStatus(),
                debugFlags.getAdSelectionCommandsEnabled(),
                AdSelectionDatabase.getInstance(context).consentedDebugConfigurationDao());
    }

    @SuppressLint("VisibleForTests")
    @Override
    public ShellCommand getShellCommand(String cmd) {
        if (!mAllCommandsMap.containsKey(cmd)) {
            Log.d(
                    AdServicesShellCommandHandler.TAG,
                    String.format(
                            "Invalid command for Ad Selection Command Shell Factory: %s", cmd));
            return null;
        }
        ShellCommand command = mAllCommandsMap.get(cmd);

        switch (cmd) {
            case ConsentedDebugShellCommand.CMD -> {
                if (!mIsConsentedDebugCliEnabled) {
                    return new NoOpShellCommand(
                            cmd,
                            command.getMetricsLoggerCommand(),
                            KEY_FLEDGE_IS_CONSENTED_DEBUGGING_CLI_ENABLED);
                }
                return command;
            }
            case GetAdSelectionDataCommand.CMD -> {
                if (!mIsAdSelectionCliEnabled) {
                    return new NoOpShellCommand(
                            cmd, command.getMetricsLoggerCommand(), KEY_AD_SELECTION_CLI_ENABLED);
                }
                return command;
            }
            default -> {
                return null;
            }
        }
    }

    @Override
    public String getCommandPrefix() {
        return COMMAND_PREFIX;
    }

    @Override
    public List<String> getAllCommandsHelp() {
        return mAllCommandsMap.values().stream()
                .map(ShellCommand::getCommandHelp)
                .collect(Collectors.toList());
    }
}
