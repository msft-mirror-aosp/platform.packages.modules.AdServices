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

import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.AdFilteringFeatureFactory;
import com.android.adservices.service.common.DatabaseClearer;
import com.android.adservices.service.devapi.DevSessionController;
import com.android.adservices.service.devapi.DevSessionControllerImpl;
import com.android.adservices.service.devapi.DevSessionDataStoreFactory;
import com.android.adservices.service.shell.AdServicesShellCommandHandler;
import com.android.adservices.service.shell.NoOpShellCommand;
import com.android.adservices.service.shell.ShellCommand;
import com.android.adservices.service.shell.ShellCommandFactory;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableSet;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiresApi(Build.VERSION_CODES.S)
public final class AdServicesApiShellCommandFactory implements ShellCommandFactory {
    public static final String COMMAND_PREFIX = "adservices-api";

    private final Map<String, ShellCommand> mAllCommandsMap;
    private final boolean mDeveloperModeFeatureEnabled;

    @VisibleForTesting
    public AdServicesApiShellCommandFactory(
            DevSessionController devSessionSetter, boolean developerModeFeatureEnabled) {
        Set<ShellCommand> allCommands =
                ImmutableSet.of(
                        new EnableAdServicesCommand(),
                        new ResetConsentCommand(),
                        new DevSessionCommand(devSessionSetter));
        mAllCommandsMap =
                allCommands.stream()
                        .collect(
                                Collectors.toMap(
                                        ShellCommand::getCommandName, Function.identity()));
        mDeveloperModeFeatureEnabled = developerModeFeatureEnabled;
    }

    public static ShellCommandFactory getInstance() {
        AppInstallDao appInstallDao = SharedStorageDatabase.getInstance().appInstallDao();
        FrequencyCapDao frequencyCapDao = SharedStorageDatabase.getInstance().frequencyCapDao();
        return new AdServicesApiShellCommandFactory(
                new DevSessionControllerImpl(
                        new DatabaseClearer(
                                CustomAudienceDatabase.getInstance().customAudienceDao(),
                                SharedStorageDatabase.getInstance().appInstallDao(),
                                new AdFilteringFeatureFactory(
                                                appInstallDao,
                                                frequencyCapDao,
                                                FlagsFactory.getFlags())
                                        .getFrequencyCapDataClearer(),
                                ProtectedSignalsDatabase.getInstance().protectedSignalsDao(),
                                AdServicesExecutors.getBackgroundExecutor()),
                        DevSessionDataStoreFactory.get(),
                        AdServicesExecutors.getLightWeightExecutor()),
                DebugFlags.getInstance().getDeveloperSessionFeatureEnabled());
    }

    @Nullable
    @Override
    public ShellCommand getShellCommand(String cmd) {
        if (!mAllCommandsMap.containsKey(cmd)) {
            Log.d(
                    AdServicesShellCommandHandler.TAG,
                    String.format("Invalid command for Ad Services API Shell Factory: %s", cmd));
            return null;
        }

        ShellCommand command = mAllCommandsMap.get(cmd);
        if (DevSessionCommand.CMD.equals(cmd) && !mDeveloperModeFeatureEnabled) {
            return new NoOpShellCommand(cmd, command.getMetricsLoggerCommand());
        }
        return command;
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
