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

package com.android.adservices.service.shell.signals;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.shell.AdServicesShellCommandHandler;
import com.android.adservices.service.shell.NoOpShellCommand;
import com.android.adservices.service.shell.ShellCommand;
import com.android.adservices.service.shell.ShellCommandFactory;
import com.android.adservices.service.signals.SignalsProviderImpl;

import com.google.common.collect.ImmutableSet;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SignalsShellCommandFactory implements ShellCommandFactory {
    public static final String COMMAND_PREFIX = "app-signals";
    private final Map<String, ShellCommand> mAllCommandsMap;
    private final boolean mIsSignalsCliEnabled;

    public SignalsShellCommandFactory(
            boolean isSignalsCliEnabled, ProtectedSignalsDao protectedSignalsDao) {
        mIsSignalsCliEnabled = isSignalsCliEnabled;
        Set<ShellCommand> allCommandsMap =
                ImmutableSet.of(new GenerateInputForEncodingCommand(
                        new SignalsProviderImpl(protectedSignalsDao)));
        mAllCommandsMap =
                allCommandsMap.stream()
                        .collect(
                                Collectors.toMap(
                                        ShellCommand::getCommandName, Function.identity()));
    }

    /**
     * @return an instance of the {@link SignalsShellCommandFactory}.
     */
    public static ShellCommandFactory getInstance(
            DebugFlags debugFlags, ProtectedSignalsDao protectedSignalsDao) {
        return new SignalsShellCommandFactory(
                debugFlags.getProtectedAppSignalsCommandsEnabled(), protectedSignalsDao);
    }

    @Nullable
    @Override
    public ShellCommand getShellCommand(String cmd) {
        if (!mAllCommandsMap.containsKey(cmd)) {
            Log.d(
                    AdServicesShellCommandHandler.TAG,
                    String.format("Invalid command for Signals Shell Factory: %s", cmd));
            return null;
        }
        ShellCommand command = mAllCommandsMap.get(cmd);
        if (!mIsSignalsCliEnabled) {
            return new NoOpShellCommand(cmd, command.getMetricsLoggerCommand());
        }
        return command;
    }

    @NonNull
    @Override
    public String getCommandPrefix() {
        return COMMAND_PREFIX;
    }

    @NonNull
    @Override
    public List<String> getAllCommandsHelp() {
        return mAllCommandsMap.values().stream()
                .map(ShellCommand::getCommandHelp)
                .collect(Collectors.toList());
    }
}
