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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.shell.IShellCommandCallback;
import android.adservices.shell.ShellCommandParam;
import android.adservices.shell.ShellCommandResult;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.ConsentedDebugConfigurationDao;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.FakeFlagsFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.customaudience.BackgroundFetchRunner;
import com.android.adservices.service.shell.adselection.AdSelectionShellCommandFactory;
import com.android.adservices.service.shell.adselection.ConsentedDebugShellCommand;
import com.android.adservices.service.shell.customaudience.CustomAudienceListCommand;
import com.android.adservices.service.shell.customaudience.CustomAudienceShellCommandFactory;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.CustomAudienceLoggerFactory;
import com.android.adservices.service.stats.ShellCommandStats;
import com.android.adservices.shared.testing.common.BlockingCallableWrapper;
import com.android.adservices.shared.testing.concurrency.ResultSyncCallback;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;

public final class ShellCommandServiceImplTest extends AdServicesMockitoTestCase {

    private static final boolean CUSTOM_AUDIENCE_CLI_ENABLED = true;
    private static final boolean CONSENTED_DEBUG_CLI_ENABLED = true;
    private static final boolean SIGNALS_CLI_ENABLED = true;

    @Mock private AdServicesLogger mAdServicesLogger;

    private final Flags mFlags = FakeFlagsFactory.getFlagsForTest();
    private ShellCommandServiceImpl mShellCommandService;
    private SyncIShellCommandCallback mSyncIShellCommandCallback;

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
                        sContext.getPackageManager(),
                        new EnrollmentDao(mContext, DbTestUtil.getSharedDbHelperForTest(), mFlags),
                        mFlags,
                        CustomAudienceLoggerFactory.getNoOpInstance());
        ShellCommandFactorySupplier adServicesShellCommandHandlerFactory =
                new TestShellCommandFactorySupplier(
                        CUSTOM_AUDIENCE_CLI_ENABLED,
                        CONSENTED_DEBUG_CLI_ENABLED,
                        SIGNALS_CLI_ENABLED,
                        backgroundFetchRunner,
                        customAudienceDao,
                        consentedDebugConfigurationDao,
                        protectedSignalsDao);
        mShellCommandService =
                new ShellCommandServiceImpl(
                        adServicesShellCommandHandlerFactory,
                        AdServicesExecutors.getLightWeightExecutor(),
                        AdServicesExecutors.getScheduler(),
                        mAdServicesLogger,
                        3000L);
        mSyncIShellCommandCallback = new SyncIShellCommandCallback();
        doNothing().when(mAdServicesLogger).logShellCommandStats(any());
    }

    @Test
    public void testRunShellCommand() throws Exception {
        mShellCommandService.runShellCommand(
                new ShellCommandParam("echo", "xxx"), mSyncIShellCommandCallback);

        ShellCommandResult response = mSyncIShellCommandCallback.assertResultReceived();
        expect.withMessage("result").that(response.getResultCode()).isEqualTo(0);
        expect.withMessage("out").that(response.getOut()).contains("xxx");
        expect.withMessage("err").that(response.getErr()).isEmpty();
        verify(mAdServicesLogger).logShellCommandStats(any());
    }

    @Test
    public void testRunShellCommand_invalidCommand() throws Exception {
        mShellCommandService.runShellCommand(
                new ShellCommandParam("invalid-cmd"), mSyncIShellCommandCallback);

        ShellCommandResult response = mSyncIShellCommandCallback.assertResultReceived();
        expect.withMessage("result").that(response.getResultCode()).isEqualTo(-1);
        expect.withMessage("out").that(response.getOut()).isEmpty();
        expect.withMessage("err").that(response.getErr()).contains("Unknown command");
    }

    @Test
    public void testRunShellCommand_customAudienceList() throws Exception {
        mShellCommandService.runShellCommand(
                new ShellCommandParam(
                        CustomAudienceShellCommandFactory.COMMAND_PREFIX,
                        CustomAudienceListCommand.CMD,
                        "--owner",
                        CustomAudienceFixture.VALID_OWNER,
                        "--buyer",
                        "example-dsp.com"),
                mSyncIShellCommandCallback);

        ShellCommandResult response = mSyncIShellCommandCallback.assertResultReceived();
        expect.withMessage("result").that(response.getResultCode()).isEqualTo(0);
        expect.withMessage("out").that(response.getOut()).contains("{\"custom_audiences\":[]}");
        expect.withMessage("err").that(response.getErr()).isEmpty();
    }

    @Test
    public void testRunShellCommand_consentDebug_view() throws Exception {
        mShellCommandService.runShellCommand(
                new ShellCommandParam(
                        AdSelectionShellCommandFactory.COMMAND_PREFIX,
                        ConsentedDebugShellCommand.CMD,
                        ConsentedDebugShellCommand.VIEW_SUB_CMD),
                mSyncIShellCommandCallback);

        ShellCommandResult response = mSyncIShellCommandCallback.assertResultReceived();
        expect.withMessage("result").that(response.getResultCode()).isEqualTo(0);
        expect.withMessage("out")
                .that(response.getOut())
                .contains(ConsentedDebugShellCommand.VIEW_SUCCESS_NO_CONFIGURATION);
        expect.withMessage("err").that(response.getErr()).isEmpty();
    }

    @Test
    public void testRunShellCommand_offloadsWorkToExecutor() throws InterruptedException {
        String commandPrefix = "prefix";
        String commandName = "cmd";
        int commandResponse = 10;

        BlockingCallableWrapper<com.android.adservices.service.shell.ShellCommandResult>
                waitingCommand =
                        BlockingCallableWrapper.createBlockableInstance(
                                () ->
                                        com.android.adservices.service.shell.ShellCommandResult
                                                .create(
                                                        ShellCommandStats.RESULT_SUCCESS,
                                                        ShellCommandStats.COMMAND_ECHO));
        ShellCommand shellCommand =
                new ShellCommand() {
                    @Override
                    public com.android.adservices.service.shell.ShellCommandResult run(
                            PrintWriter out, PrintWriter err, String[] args) {
                        try {
                            return waitingCommand.call();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
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
                        mAdServicesLogger,
                        3000L);
        shellCommandService.runShellCommand(
                new ShellCommandParam(commandPrefix, commandName, "param"),
                mSyncIShellCommandCallback);

        // If we are here the command is running in a different thread and is still blocked
        expect.that(mSyncIShellCommandCallback.getResult()).isNull();

        // Letting the command complete
        waitingCommand.startWork();
        ShellCommandResult response = mSyncIShellCommandCallback.assertResultReceived();

        expect.that(response.getResultCode()).isEqualTo(AbstractShellCommand.RESULT_OK);
    }

    @Test
    public void testRunShellCommand_commandTimesOut() throws InterruptedException {
        String commandPrefix = "prefix";

        BlockingCallableWrapper<com.android.adservices.service.shell.ShellCommandResult>
                blockedCommand =
                        BlockingCallableWrapper.createBlockableInstance(
                                () ->
                                        com.android.adservices.service.shell.ShellCommandResult
                                                .create(
                                                        ShellCommandStats.RESULT_SUCCESS,
                                                        ShellCommandStats.COMMAND_ECHO));
        ShellCommand shellCommand =
                new ShellCommand() {
                    @Override
                    public com.android.adservices.service.shell.ShellCommandResult run(
                            PrintWriter out, PrintWriter err, String[] args) {
                        try {
                            return blockedCommand.call();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
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
                        mAdServicesLogger,
                        500L);
        shellCommandService.runShellCommand(
                new ShellCommandParam(commandPrefix, "cmd", "param"), mSyncIShellCommandCallback);

        // If we are here the command is running in a different thread and is still blocked
        expect.that(mSyncIShellCommandCallback.getResult()).isNull();

        // Waiting for the timeout to trigger
        Thread.sleep(1500L);

        ShellCommandResult response = mSyncIShellCommandCallback.assertResultReceived();

        expect.that(response.getResultCode())
                .isEqualTo(ShellCommandServiceImpl.RESULT_SHELL_COMMAND_EXECUTION_TIMED_OUT);
    }

    @Test
    public void testRunShellCommand_commandThrowsException() throws InterruptedException {
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
                        mAdServicesLogger,
                        500L);
        shellCommandService.runShellCommand(
                new ShellCommandParam(commandPrefix, "cmd", "param"), mSyncIShellCommandCallback);

        ShellCommandResult response = mSyncIShellCommandCallback.assertResultReceived();

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
            extends ResultSyncCallback<ShellCommandResult> implements IShellCommandCallback {

        @Override
        public void onResult(ShellCommandResult response) {
            injectResult(response);
        }

        @Override
        public IBinder asBinder() {
            return null;
        }
    }
}
