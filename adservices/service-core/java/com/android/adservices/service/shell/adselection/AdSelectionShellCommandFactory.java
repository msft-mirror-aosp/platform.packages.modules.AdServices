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

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.ConsentedDebugConfigurationDao;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.adselection.AdFilteringFeatureFactory;
import com.android.adservices.service.adselection.AuctionServerDataCompressor;
import com.android.adservices.service.adselection.AuctionServerDataCompressorFactory;
import com.android.adservices.service.adselection.AuctionServerPayloadMetricsStrategyDisabled;
import com.android.adservices.service.adselection.BuyerInputGenerator;
import com.android.adservices.service.adselection.CompressedBuyerInputCreatorFactory;
import com.android.adservices.service.adselection.CompressedBuyerInputCreatorHelper;
import com.android.adservices.service.adselection.CompressedBuyerInputCreatorNoOptimizations;
import com.android.adservices.service.adselection.FrequencyCapAdFiltererNoOpImpl;
import com.android.adservices.service.adselection.debug.ConsentedDebugConfigurationGenerator;
import com.android.adservices.service.adselection.debug.ConsentedDebugConfigurationGeneratorFactory;
import com.android.adservices.service.devapi.DevSessionDataStore;
import com.android.adservices.service.devapi.DevSessionDataStoreFactory;
import com.android.adservices.service.shell.AdServicesShellCommandHandler;
import com.android.adservices.service.shell.NoOpShellCommand;
import com.android.adservices.service.shell.ShellCommand;
import com.android.adservices.service.shell.ShellCommandFactory;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableSet;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AdSelectionShellCommandFactory implements ShellCommandFactory {

    public static final String COMMAND_PREFIX = "ad-selection";
    private final Map<String, ShellCommand> mAllCommandsMap;
    private final boolean mIsConsentedDebugCliEnabled;
    private final boolean mIsAdSelectionCliEnabled;

    @VisibleForTesting
    public AdSelectionShellCommandFactory(
            boolean isConsentedDebugCliEnabled,
            boolean isAdSelectionCliEnabled,
            ConsentedDebugConfigurationDao consentedDebugConfigurationDao,
            BuyerInputGenerator buyerInputGenerator,
            AuctionServerDataCompressor auctionServerDataCompressor,
            ConsentedDebugConfigurationGenerator consentedDebugConfigurationGenerator,
            AdSelectionEntryDao adSelectionEntryDao,
            DevSessionDataStore devSessionDataStore) {
        Objects.requireNonNull(consentedDebugConfigurationDao);
        Objects.requireNonNull(adSelectionEntryDao);

        mIsConsentedDebugCliEnabled = isConsentedDebugCliEnabled;
        mIsAdSelectionCliEnabled = isAdSelectionCliEnabled;
        Set<ShellCommand> allCommands =
                ImmutableSet.of(
                        new ConsentedDebugShellCommand(consentedDebugConfigurationDao),
                        new GetAdSelectionDataCommand(
                                buyerInputGenerator,
                                auctionServerDataCompressor,
                                consentedDebugConfigurationGenerator,
                                devSessionDataStore),
                        new ViewAuctionResultCommand(adSelectionEntryDao));
        mAllCommandsMap =
                allCommands.stream()
                        .collect(
                                Collectors.toMap(
                                        ShellCommand::getCommandName, Function.identity()));
    }

    /** Gets a new {@link AdSelectionShellCommandFactory} instance. */
    // TODO(b/311183933): Remove passed in Context from static method.
    @SuppressWarnings("AvoidStaticContext")
    public static AdSelectionShellCommandFactory newInstance(
            DebugFlags debugFlags, Flags flags, Context context) {
        SharedStorageDatabase sharedStorageDatabase = SharedStorageDatabase.getInstance();
        AuctionServerDataCompressor auctionServerDataCompressor =
                AuctionServerDataCompressorFactory.getDataCompressor(
                        flags.getFledgeAuctionServerCompressionAlgorithmVersion());
        // TODO(b/342574944): Decide which fields need to be configurable and update.
        AuctionServerDataCompressor dataCompressor =
                AuctionServerDataCompressorFactory.getDataCompressor(
                        flags.getFledgeAuctionServerCompressionAlgorithmVersion());
        CompressedBuyerInputCreatorHelper compressedBuyerInputCreatorHelper =
                new CompressedBuyerInputCreatorHelper(
                        new AuctionServerPayloadMetricsStrategyDisabled(),
                        flags.getPasExtendedMetricsEnabled(),
                        flags.getFledgeAuctionServerOmitAdsEnabled());
        CompressedBuyerInputCreatorFactory compressedBuyerInputCreatorFactory =
                new CompressedBuyerInputCreatorFactory(
                        compressedBuyerInputCreatorHelper,
                        dataCompressor,
                        flags.getFledgeGetAdSelectionDataSellerConfigurationEnabled(),
                        CustomAudienceDatabase.getInstance().customAudienceDao(),
                        ProtectedSignalsDatabase.getInstance().getEncodedPayloadDao(),
                        CompressedBuyerInputCreatorNoOptimizations.VERSION,
                        flags.getFledgeGetAdSelectionDataMaxNumEntirePayloadCompressions(),
                        flags.getProtectedSignalsEncodedPayloadMaxSizeBytes(),
                        Clock.systemUTC());
        BuyerInputGenerator buyerInputGenerator =
                new BuyerInputGenerator(
                        new FrequencyCapAdFiltererNoOpImpl(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        flags.getFledgeCustomAudienceActiveTimeWindowInMs(),
                        flags.getFledgeAuctionServerEnableAdFilterInGetAdSelectionData(),
                        flags.getProtectedSignalsPeriodicEncodingEnabled(),
                        new AdFilteringFeatureFactory(
                                        sharedStorageDatabase.appInstallDao(),
                                        sharedStorageDatabase.frequencyCapDao(),
                                        flags)
                                .getAppInstallAdFilterer(),
                        compressedBuyerInputCreatorFactory);
        AdSelectionDatabase adSelectionDatabase = AdSelectionDatabase.getInstance();
        ConsentedDebugConfigurationDao consentedDebugConfigurationDao =
                adSelectionDatabase.consentedDebugConfigurationDao();
        AdSelectionEntryDao adSelectionEntryDao = adSelectionDatabase.adSelectionEntryDao();
        ConsentedDebugConfigurationGenerator consentedDebugConfigurationGenerator =
                new ConsentedDebugConfigurationGeneratorFactory(
                                debugFlags.getFledgeAuctionServerConsentedDebuggingEnabled(),
                                consentedDebugConfigurationDao)
                        .create();
        DevSessionDataStore devSessionDataStore = DevSessionDataStoreFactory.get();
        return new AdSelectionShellCommandFactory(
                debugFlags.getFledgeConsentedDebuggingCliEnabledStatus(),
                debugFlags.getAdSelectionCommandsEnabled(),
                consentedDebugConfigurationDao,
                buyerInputGenerator,
                auctionServerDataCompressor,
                consentedDebugConfigurationGenerator,
                adSelectionEntryDao,
                devSessionDataStore);
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
            case GetAdSelectionDataCommand.CMD, ViewAuctionResultCommand.CMD -> {
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
