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

package com.android.adservices.service.shell.attributionreporting;

import static com.android.adservices.service.DebugFlagsConstants.KEY_ATTRIBUTION_REPORTING_CLI_ENABLED;

import android.util.Log;

import androidx.annotation.Nullable;

import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.devapi.DevSessionDataStore;
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

public final class AttributionReportingShellCommandFactory implements ShellCommandFactory {
    public static final String COMMAND_PREFIX = "attribution-reporting";
    private final boolean mIsAttributionReportingCliEnabled;
    private final Map<String, ShellCommand> mAllCommandsMap;

    @VisibleForTesting
    public AttributionReportingShellCommandFactory(
            boolean isAttributionReportingCliEnabled,
            DatastoreManager datastoreManager,
            DevSessionDataStore devSessionDataStore) {
        mIsAttributionReportingCliEnabled = isAttributionReportingCliEnabled;
        Set<ShellCommand> allCommands =
                ImmutableSet.of(
                        new AttributionReportingListSourceRegistrationsCommand(
                                datastoreManager, devSessionDataStore),
                        new AttributionReportingListTriggerRegistrationsCommand(
                                datastoreManager, devSessionDataStore),
                        new AttributionReportingListEventReportsCommand(
                                datastoreManager, devSessionDataStore),
                        new AttributionReportingListAggregatableReportsCommand(
                                datastoreManager, devSessionDataStore),
                        new AttributionReportingListDebugReportsCommand(
                                datastoreManager, devSessionDataStore));
        mAllCommandsMap =
                allCommands.stream()
                        .collect(
                                Collectors.toMap(
                                        ShellCommand::getCommandName, Function.identity()));
    }

    /** Creating new instance of Attribution Reporting Shell Command Factory */
    public static ShellCommandFactory newInstance(DebugFlags debugFlags) {
        return new AttributionReportingShellCommandFactory(
                debugFlags.getAttributionReportingCommandsEnabled(),
                DatastoreManagerFactory.getDatastoreManager(),
                DevSessionDataStoreFactory.get());
    }

    @Nullable
    @Override
    public ShellCommand getShellCommand(String cmd) {
        if (!mAllCommandsMap.containsKey(cmd)) {
            Log.e(
                    AdServicesShellCommandHandler.TAG,
                    String.format(
                            "Invalid command for Attribution Reporting Shell Factory: %s", cmd));
            return null;
        }
        ShellCommand command = mAllCommandsMap.get(cmd);
        if (!mIsAttributionReportingCliEnabled) {
            return new NoOpShellCommand(
                    cmd, command.getMetricsLoggerCommand(), KEY_ATTRIBUTION_REPORTING_CLI_ENABLED);
        }
        return command;
    }

    @Override
    public List<String> getAllCommandsHelp() {
        return mAllCommandsMap.values().stream()
                .map(ShellCommand::getCommandHelp)
                .collect(Collectors.toList());
    }

    @Override
    public String getCommandPrefix() {
        return COMMAND_PREFIX;
    }
}
