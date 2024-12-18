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

package com.android.adservices.service.shell.customaudience;

import static com.android.adservices.service.DebugFlagsConstants.KEY_FLEDGE_IS_CUSTOM_AUDIENCE_CLI_ENABLED;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.customaudience.BackgroundFetchRunner;
import com.android.adservices.service.shell.AdServicesShellCommandHandler;
import com.android.adservices.service.shell.NoOpShellCommand;
import com.android.adservices.service.shell.ShellCommand;
import com.android.adservices.service.shell.ShellCommandFactory;
import com.android.adservices.service.stats.CustomAudienceLoggerFactory;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableSet;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CustomAudienceShellCommandFactory implements ShellCommandFactory {
    public static final String COMMAND_PREFIX = "custom-audience";
    private final Map<String, ShellCommand> mAllCommandsMap;
    private final boolean mIsCustomAudienceCliEnabled;

    @VisibleForTesting
    public CustomAudienceShellCommandFactory(
            boolean isCustomAudienceCliEnabled,
            BackgroundFetchRunner backgroundFetchRunner,
            CustomAudienceDao customAudienceDao,
            Clock clock,
            long fledgeCustomAudienceActiveTimeWindowInMs) {
        mIsCustomAudienceCliEnabled = isCustomAudienceCliEnabled;
        Set<ShellCommand> allCommands =
                ImmutableSet.of(
                        new CustomAudienceListCommand(
                                customAudienceDao, clock, fledgeCustomAudienceActiveTimeWindowInMs),
                        new CustomAudienceViewCommand(
                                customAudienceDao, clock, fledgeCustomAudienceActiveTimeWindowInMs),
                        new CustomAudienceRefreshCommand(
                                backgroundFetchRunner,
                                customAudienceDao,
                                AdServicesExecutors.getScheduler()));
        mAllCommandsMap =
                allCommands.stream()
                        .collect(
                                Collectors.toMap(
                                        ShellCommand::getCommandName, Function.identity()));
    }

    /** Gets a new {@link CustomAudienceShellCommandFactory} instance . */
    // TODO(b/311183933): Remove passed in Context from static method.
    @SuppressWarnings("AvoidStaticContext")
    public static ShellCommandFactory newInstance(
            DebugFlags debugFlags, Flags flags, Context context) {
        CustomAudienceDao customAudienceDao =
                CustomAudienceDatabase.getInstance().customAudienceDao();
        return new CustomAudienceShellCommandFactory(
                debugFlags.getFledgeCustomAudienceCLIEnabledStatus(),
                new BackgroundFetchRunner(
                        customAudienceDao,
                        SharedStorageDatabase.getInstance().appInstallDao(),
                        ApplicationContextSingleton.get().getPackageManager(),
                        EnrollmentDao.getInstance(),
                        flags,
                        // Avoid logging metrics when using shell commands (such as daily update).
                        CustomAudienceLoggerFactory.getNoOpInstance()),
                customAudienceDao,
                Clock.systemUTC(),
                flags.getFledgeCustomAudienceActiveTimeWindowInMs());
    }

    @Nullable
    @Override
    public ShellCommand getShellCommand(String cmd) {
        if (!mAllCommandsMap.containsKey(cmd)) {
            Log.d(
                    AdServicesShellCommandHandler.TAG,
                    String.format("Invalid command for Custom Audience Shell Factory: %s", cmd));
            return null;
        }
        ShellCommand command = mAllCommandsMap.get(cmd);
        if (!mIsCustomAudienceCliEnabled) {
            return new NoOpShellCommand(
                    cmd,
                    command.getMetricsLoggerCommand(),
                    KEY_FLEDGE_IS_CUSTOM_AUDIENCE_CLI_ENABLED);
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
