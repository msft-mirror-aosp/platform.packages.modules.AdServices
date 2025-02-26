/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.adservices.shared.testing.concurrency.DeviceSideConcurrencyHelper.sleepOnly;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.shell.IShellCommandCallback;
import android.adservices.shell.ShellCommandParam;
import android.adservices.shell.ShellCommandResult;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.common.DbTestUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.ConsentedDebugConfigurationDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.EncoderLogicMetadataDao;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.FakeFlagsFactory;
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
import com.android.adservices.service.customaudience.BackgroundFetchRunner;
import com.android.adservices.service.devapi.DevSessionController;
import com.android.adservices.service.devapi.DevSessionDataStore;
import com.android.adservices.service.shell.adselection.AdSelectionShellCommandFactory;
import com.android.adservices.service.shell.adselection.ConsentedDebugShellCommand;
import com.android.adservices.service.shell.customaudience.CustomAudienceListCommand;
import com.android.adservices.service.shell.customaudience.CustomAudienceShellCommandFactory;
import com.android.adservices.service.signals.PeriodicEncodingJobRunner;
import com.android.adservices.service.signals.SignalsProviderAndArgumentFactory;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.CustomAudienceLoggerFactory;
import com.android.adservices.service.stats.GetAdSelectionDataApiCalledStats;
import com.android.adservices.service.stats.ShellCommandStats;
import com.android.adservices.service.stats.pas.EncodingExecutionLogHelper;
import com.android.adservices.service.stats.pas.EncodingJobRunStatsLogger;
import com.android.adservices.shared.testing.BooleanSyncCallback;
import com.android.adservices.shared.testing.concurrency.OnResultSyncCallback;
import com.android.adservices.shared.testing.concurrency.ResultSyncCallback;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.PrintWriter;
import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class ShellCommandServiceImplTest extends AdServicesMockitoTestCase {

    private static final boolean CUSTOM_AUDIENCE_CLI_ENABLED = true;
    private static final boolean CONSENTED_DEBUG_CLI_ENABLED = true;
    private static final boolean SIGNALS_CLI_ENABLED = true;
    private static final long MAX_COMMAND_DURATION_MILLIS = 3000L;
    private static final com.android.adservices.service.shell.ShellCommandResult SUCCESSFUL_CMD =
            com.android.adservices.service.shell.ShellCommandResult.create(
                    ShellCommandStats.RESULT_SUCCESS, ShellCommandStats.COMMAND_ECHO);

    @Mock private AdServicesLogger mAdServicesLogger;
    @Mock private CompressedBuyerInputCreatorFactory mMockCompressedBuyerInputCreatorFactory;
    @Mock private PeriodicEncodingJobRunner mEncodingJobRunner;
    @Mock private EncoderLogicHandler mEncoderLogicHandler;
    @Mock private EncodingExecutionLogHelper mEncodingExecutionLogHelper;
    @Mock private EncodingJobRunStatsLogger mEncodingJobRunStatsLogger;
    @Mock private EncoderLogicMetadataDao mEncoderLogicMetadataDao;
    @Mock private AdSelectionEntryDao mAdSelectionEntryDao;
    @Mock private DevSessionController mDevSessionController;
    @Mock private DevSessionDataStore mDevSessionDataStore;

    private final Flags mFakeFlags = FakeFlagsFactory.getFlagsForTest();
    private ShellCommandServiceImpl mShellCommandService;
    private final SyncIShellCommandCallback mSyncIShellCommandCallback =
            new SyncIShellCommandCallback();

    @Before
    public void setup() {
        CustomAudienceDao customAudienceDao =
                Room.inMemoryDatabaseBuilder(mContext, CustomAudienceDatabase.class)
                        .addTypeConverter(new DBCustomAudience.Converters(true, true, true))
                        .build()
                        .customAudienceDao();
        AppInstallDao appInstallDao =
                Room.inMemoryDatabaseBuilder(mContext, SharedStorageDatabase.class)
                        .build()
                        .appInstallDao();
        FrequencyCapDao frequencyCapDao =
                Room.inMemoryDatabaseBuilder(mContext, SharedStorageDatabase.class)
                        .build()
                        .frequencyCapDao();
        ConsentedDebugConfigurationDao consentedDebugConfigurationDao =
                Room.inMemoryDatabaseBuilder(mContext, AdSelectionDatabase.class)
                        .build()
                        .consentedDebugConfigurationDao();
        ProtectedSignalsDao protectedSignalsDao =
                Room.inMemoryDatabaseBuilder(mContext, ProtectedSignalsDatabase.class)
                        .build()
                        .protectedSignalsDao();
        BackgroundFetchRunner backgroundFetchRunner =
                new BackgroundFetchRunner(
                        customAudienceDao,
                        appInstallDao,
                        mContext.getPackageManager(),
                        new EnrollmentDao(
                                mContext, DbTestUtil.getSharedDbHelperForTest(), mFakeFlags),
                        mFakeFlags,
                        CustomAudienceLoggerFactory.getNoOpInstance());
        AuctionServerDataCompressor auctionServerDataCompressor =
                AuctionServerDataCompressorFactory.getDataCompressor(
                        mFakeFlags.getFledgeAuctionServerCompressionAlgorithmVersion());

        CompressedBuyerInputCreatorHelper helper =
                new CompressedBuyerInputCreatorHelper(
                        new AuctionServerPayloadMetricsStrategyDisabled(),
                        /* pasExtendedMetricsEnabled= */ false,
                        /* omitAdsEnabled= */ false);
        when(mMockCompressedBuyerInputCreatorFactory.createCompressedBuyerInputCreator(
                        any(), any()))
                .thenReturn(
                        new CompressedBuyerInputCreatorNoOptimizations(
                                helper,
                                auctionServerDataCompressor,
                                Clock.systemUTC(),
                                GetAdSelectionDataApiCalledStats.builder()));
        BuyerInputGenerator buyerInputGenerator =
                new BuyerInputGenerator(
                        new FrequencyCapAdFiltererNoOpImpl(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        mFakeFlags.getFledgeCustomAudienceActiveTimeWindowInMs(),
                        mFakeFlags.getFledgeAuctionServerEnableAdFilterInGetAdSelectionData(),
                        mFakeFlags.getProtectedSignalsPeriodicEncodingEnabled(),
                        new AdFilteringFeatureFactory(appInstallDao, frequencyCapDao, mFakeFlags)
                                .getAppInstallAdFilterer(),
                        mMockCompressedBuyerInputCreatorFactory);
        ConsentedDebugConfigurationGenerator consentedDebugConfigurationGenerator =
                new ConsentedDebugConfigurationGeneratorFactory(
                                CONSENTED_DEBUG_CLI_ENABLED, consentedDebugConfigurationDao)
                        .create();
        SignalsProviderAndArgumentFactory signalsProviderAndArgumentFactory =
                new SignalsProviderAndArgumentFactory(
                        protectedSignalsDao, mFakeFlags.getPasEncodingJobImprovementsEnabled());
        ShellCommandFactorySupplier adServicesShellCommandHandlerFactory =
                new TestShellCommandFactorySupplier(
                        CUSTOM_AUDIENCE_CLI_ENABLED,
                        CONSENTED_DEBUG_CLI_ENABLED,
                        SIGNALS_CLI_ENABLED,
                        backgroundFetchRunner,
                        customAudienceDao,
                        consentedDebugConfigurationDao,
                        signalsProviderAndArgumentFactory,
                        buyerInputGenerator,
                        auctionServerDataCompressor,
                        mEncodingJobRunner,
                        mEncoderLogicHandler,
                        mEncodingExecutionLogHelper,
                        mEncodingJobRunStatsLogger,
                        mEncoderLogicMetadataDao,
                        consentedDebugConfigurationGenerator,
                        mAdSelectionEntryDao,
                        mDevSessionController,
                        mDevSessionDataStore);
        mShellCommandService =
                new ShellCommandServiceImpl(
                        adServicesShellCommandHandlerFactory,
                        AdServicesExecutors.getLightWeightExecutor(),
                        AdServicesExecutors.getScheduler(),
                        mAdServicesLogger);
        doNothing().when(mAdServicesLogger).logShellCommandStats(any());
    }

    @Test
    public void testRunShellCommand() throws Exception {
        mShellCommandService.runShellCommand(
                new ShellCommandParam(MAX_COMMAND_DURATION_MILLIS, "echo", "xxx"),
                mSyncIShellCommandCallback);

        ShellCommandResult response = mSyncIShellCommandCallback.assertResultReceived();
        assertThat(response).isNotNull();
        expect.withMessage("result").that(response.getResultCode()).isEqualTo(0);
        expect.withMessage("out").that(response.getOut()).contains("xxx");
        expect.withMessage("err").that(response.getErr()).isEmpty();
        verify(mAdServicesLogger).logShellCommandStats(any());
    }

    @Test
    public void testRunShellCommand_invalidCommand() throws Exception {
        mShellCommandService.runShellCommand(
                new ShellCommandParam(MAX_COMMAND_DURATION_MILLIS, "invalid-cmd"),
                mSyncIShellCommandCallback);

        ShellCommandResult response = mSyncIShellCommandCallback.assertResultReceived();
        assertThat(response).isNotNull();
        expect.withMessage("result").that(response.getResultCode()).isEqualTo(-1);
        expect.withMessage("out").that(response.getOut()).isEmpty();
        expect.withMessage("err").that(response.getErr()).contains("Unknown command");
    }

    @Test
    public void testRunShellCommand_customAudienceList() throws Exception {
        mShellCommandService.runShellCommand(
                new ShellCommandParam(
                        MAX_COMMAND_DURATION_MILLIS,
                        CustomAudienceShellCommandFactory.COMMAND_PREFIX,
                        CustomAudienceListCommand.CMD,
                        "--owner",
                        CustomAudienceFixture.VALID_OWNER,
                        "--buyer",
                        "example-dsp.com"),
                mSyncIShellCommandCallback);

        ShellCommandResult response = mSyncIShellCommandCallback.assertResultReceived();
        assertThat(response).isNotNull();
        expect.withMessage("result").that(response.getResultCode()).isEqualTo(0);
        expect.withMessage("out").that(response.getOut()).contains("{\"custom_audiences\":[]}");
        expect.withMessage("err").that(response.getErr()).isEmpty();
    }

    @Test
    public void testRunShellCommand_consentDebug_view() throws Exception {
        mShellCommandService.runShellCommand(
                new ShellCommandParam(
                        MAX_COMMAND_DURATION_MILLIS,
                        AdSelectionShellCommandFactory.COMMAND_PREFIX,
                        ConsentedDebugShellCommand.CMD,
                        ConsentedDebugShellCommand.VIEW_SUB_CMD),
                mSyncIShellCommandCallback);

        ShellCommandResult response = mSyncIShellCommandCallback.assertResultReceived();
        assertThat(response).isNotNull();
        expect.withMessage("result").that(response.getResultCode()).isEqualTo(0);
        expect.withMessage("out")
                .that(response.getOut())
                .contains(ConsentedDebugShellCommand.VIEW_SUCCESS_NO_CONFIGURATION);
        expect.withMessage("err").that(response.getErr()).isEmpty();
    }

    @Test
    public void testRunShellCommand_offloadsWorkToExecutor() throws Exception {
        String commandPrefix = "prefix";
        String commandName = "cmd";

        ResultSyncCallback<Thread> commandFinishedCallback = new ResultSyncCallback<>();
        ShellCommand shellCommand =
                new ShellCommand() {
                    @Override
                    public com.android.adservices.service.shell.ShellCommandResult run(
                            PrintWriter out, PrintWriter err, String[] args) {
                        commandFinishedCallback.injectResult(Thread.currentThread());
                        return SUCCESSFUL_CMD;
                    }

                    @Override
                    public String getCommandName() {
                        return commandName;
                    }

                    @Override
                    public int getMetricsLoggerCommand() {
                        return 0;
                    }

                    @Override
                    public String getCommandHelp() {
                        return null;
                    }
                };

        ShellCommandServiceImpl shellCommandService =
                new ShellCommandServiceImpl(
                        injectCommandToService(shellCommand, commandPrefix),
                        AdServicesExecutors.getLightWeightExecutor(),
                        AdServicesExecutors.getScheduler(),
                        mAdServicesLogger);
        shellCommandService.runShellCommand(
                new ShellCommandParam(
                        MAX_COMMAND_DURATION_MILLIS, commandPrefix, commandName, "param"),
                mSyncIShellCommandCallback);

        // Letting the command complete
        ShellCommandResult response = mSyncIShellCommandCallback.assertResultReceived();
        assertWithMessage("response").that(response).isNotNull();
        expect.that(response.getResultCode()).isEqualTo(AbstractShellCommand.RESULT_OK);

        Thread commandExecutionThread = commandFinishedCallback.assertResultReceived();
        expect.withMessage("execution thread")
                .that(commandExecutionThread)
                .isNotSameInstanceAs(Thread.currentThread());
    }

    @Test
    public void testRunShellCommand_commandTimesOut() throws Exception {
        String commandPrefix = "prefix";
        AtomicReference<Thread> commandThreadRef = new AtomicReference<>();
        BooleanSyncCallback commandInterruptedCallback = new BooleanSyncCallback();

        ShellCommand shellCommand =
                new ShellCommand() {
                    @Override
                    public com.android.adservices.service.shell.ShellCommandResult run(
                            PrintWriter out, PrintWriter err, String[] args) {
                        Thread currentThread = Thread.currentThread();
                        commandThreadRef.set(currentThread);
                        try {
                            sleepOnly(Long.MAX_VALUE, "Sleeping forever (or until interrupted...)");
                            commandInterruptedCallback.injectResult(false);
                        } catch (InterruptedException e) {
                            mLog.v("Little Suzie woke up: %s", currentThread.getName());
                            commandInterruptedCallback.injectResult(true);
                        }
                        return SUCCESSFUL_CMD;
                    }

                    @Override
                    public String getCommandName() {
                        return "cmd";
                    }

                    @Override
                    public int getMetricsLoggerCommand() {
                        return 0;
                    }

                    @Override
                    public String getCommandHelp() {
                        return null;
                    }
                };

        ShellCommandServiceImpl shellCommandService =
                new ShellCommandServiceImpl(
                        injectCommandToService(shellCommand, commandPrefix),
                        AdServicesExecutors.getLightWeightExecutor(),
                        AdServicesExecutors.getScheduler(),
                        mAdServicesLogger);
        shellCommandService.runShellCommand(
                new ShellCommandParam(500L, commandPrefix, "cmd", "param"),
                mSyncIShellCommandCallback);

        ShellCommandResult response = mSyncIShellCommandCallback.assertResultReceived();
        assertWithMessage("response").that(response).isNotNull();

        expect.that(response.getResultCode())
                .isEqualTo(ShellCommandServiceImpl.RESULT_SHELL_COMMAND_EXECUTION_TIMED_OUT);

        // Test itself is done, but we need to finish the thread that is blocking "forever"
        Thread commandThread = commandThreadRef.get();
        expect.withMessage("Command thread").that(commandThread).isNotNull();
        commandThread.interrupt();

        expect.withMessage("command finished")
                .that(commandInterruptedCallback.assertResultReceived())
                .isTrue();
    }

    @Test
    public void testRunShellCommand_commandThrowsException() throws Exception {
        String commandPrefix = "prefix";

        ShellCommand shellCommand =
                new ShellCommand() {
                    @Override
                    public com.android.adservices.service.shell.ShellCommandResult run(
                            PrintWriter out, PrintWriter err, String[] args) {
                        throw new RuntimeException("Test exception");
                    }

                    @Override
                    public String getCommandName() {
                        return "cmd";
                    }

                    @Override
                    public int getMetricsLoggerCommand() {
                        return 0;
                    }

                    @Override
                    public String getCommandHelp() {
                        return null;
                    }
                };

        ShellCommandServiceImpl shellCommandService =
                new ShellCommandServiceImpl(
                        injectCommandToService(shellCommand, commandPrefix),
                        AdServicesExecutors.getLightWeightExecutor(),
                        AdServicesExecutors.getScheduler(),
                        mAdServicesLogger);
        shellCommandService.runShellCommand(
                new ShellCommandParam(500L, commandPrefix, "cmd", "param"),
                mSyncIShellCommandCallback);

        ShellCommandResult response = mSyncIShellCommandCallback.assertResultReceived();
        assertThat(response).isNotNull();
        expect.that(response.getResultCode()).isEqualTo(AbstractShellCommand.RESULT_GENERIC_ERROR);
    }

    ShellCommandFactorySupplier injectCommandToService(ShellCommand command, String commandPrefix) {
        ShellCommandFactory testCommandFactory =
                new ShellCommandFactory() {
                    @Nullable
                    @Override
                    public ShellCommand getShellCommand(String cmd) {
                        return command;
                    }

                    @NonNull
                    @Override
                    public String getCommandPrefix() {
                        return commandPrefix;
                    }

                    @Override
                    public List<String> getAllCommandsHelp() {
                        return Collections.emptyList();
                    }
                };

        return new ShellCommandFactorySupplier() {
            @Override
            public ImmutableList<ShellCommandFactory> getAllShellCommandFactories() {
                return ImmutableList.of(testCommandFactory);
            }
        };
    }

    private static final class SyncIShellCommandCallback
            extends OnResultSyncCallback<ShellCommandResult> implements IShellCommandCallback {}
}
