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

import static com.android.adservices.service.shell.AbstractShellCommand.ERROR_TEMPLATE_INVALID_ARGS;
import static com.android.adservices.service.shell.AdServicesShellCommandHandler.CMD_HELP;
import static com.android.adservices.service.shell.AdServicesShellCommandHandler.CMD_SHORT_HELP;
import static com.android.adservices.service.shell.AdServicesShellCommandHandler.ERROR_EMPTY_COMMAND;
import static com.android.adservices.service.shell.common.EchoCommand.CMD_ECHO;
import static com.android.adservices.service.shell.common.EchoCommand.HELP_ECHO;
import static com.android.adservices.service.shell.common.IsAllowedAdSelectionAccessCommand.HELP_IS_ALLOWED_AD_SELECTION_ACCESS;
import static com.android.adservices.service.shell.common.IsAllowedAttributionAccessCommand.HELP_IS_ALLOWED_ATTRIBUTION_ACCESS;
import static com.android.adservices.service.shell.common.IsAllowedCustomAudiencesAccessCommand.HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS;
import static com.android.adservices.service.shell.common.IsAllowedProtectedSignalsAccessCommand.HELP_IS_ALLOWED_PROTECTED_SIGNALS_ACCESS;
import static com.android.adservices.service.shell.common.IsAllowedTopicsAccessCommand.HELP_IS_ALLOWED_TOPICS_ACCESS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.annotation.Nullable;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.ConsentedDebugConfigurationDao;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.EncoderLogicMetadataDao;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.service.adselection.AuctionServerDataCompressor;
import com.android.adservices.service.adselection.BuyerInputGenerator;
import com.android.adservices.service.adselection.CompressedBuyerInputCreatorFactory;
import com.android.adservices.service.adselection.debug.ConsentedDebugConfigurationGenerator;
import com.android.adservices.service.common.AppManifestConfigHelper;
import com.android.adservices.service.customaudience.BackgroundFetchRunner;
import com.android.adservices.service.devapi.DevSessionController;
import com.android.adservices.service.devapi.DevSessionDataStore;
import com.android.adservices.service.shell.adselection.ConsentedDebugShellCommand;
import com.android.adservices.service.shell.adselection.GetAdSelectionDataCommand;
import com.android.adservices.service.shell.adselection.ViewAuctionResultCommand;
import com.android.adservices.service.shell.adservicesapi.DevSessionCommand;
import com.android.adservices.service.shell.adservicesapi.EnableAdServicesCommand;
import com.android.adservices.service.shell.adservicesapi.ResetConsentCommand;
import com.android.adservices.service.shell.customaudience.CustomAudienceListCommand;
import com.android.adservices.service.shell.customaudience.CustomAudienceRefreshCommand;
import com.android.adservices.service.shell.customaudience.CustomAudienceShellCommandFactory;
import com.android.adservices.service.shell.customaudience.CustomAudienceViewCommand;
import com.android.adservices.service.shell.signals.GenerateInputForEncodingCommand;
import com.android.adservices.service.shell.signals.TriggerEncodingCommand;
import com.android.adservices.service.signals.PeriodicEncodingJobRunner;
import com.android.adservices.service.signals.ProtectedSignalsArgument;
import com.android.adservices.service.signals.SignalsProviderAndArgumentFactory;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.ShellCommandStats;
import com.android.adservices.service.stats.pas.EncodingExecutionLogHelper;
import com.android.adservices.service.stats.pas.EncodingJobRunStatsLogger;
import com.android.adservices.shared.util.Clock;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@SpyStatic(AppManifestConfigHelper.class)
public final class AdServicesShellCommandHandlerTest extends AdServicesExtendedMockitoTestCase {
    private static final boolean CUSTOM_AUDIENCE_CLI_ENABLED = true;
    private static final boolean CONSENTED_DEBUG_CLI_ENABLED = true;
    private static final boolean SIGNALS_CLI_ENABLED = true;

    // mCmd is used on most tests methods, excepted those that runs more than one command
    private OneTimeCommand mCmd;
    @Mock private CustomAudienceDao mCustomAudienceDao;
    @Mock private BackgroundFetchRunner mBackgroundFetchRunner;
    @Mock private ConsentedDebugConfigurationDao mConsentedDebugConfigurationDao;
    @Mock private ProtectedSignalsDao mProtectedSignalsDao;
    @Mock private AdServicesLogger mAdServicesLogger;
    @Mock private Clock mClock;
    @Mock private BuyerInputGenerator mBuyerInputGenerator;
    @Mock private AuctionServerDataCompressor mAuctionServerDataCompressor;
    @Mock private CompressedBuyerInputCreatorFactory mMockCompressedBuyerInputCreatorFactory;
    @Mock private PeriodicEncodingJobRunner mEncodingJobRunner;
    @Mock private EncoderLogicHandler mEncoderLogicHandler;
    @Mock private EncodingExecutionLogHelper mEncodingExecutionLogHelper;
    @Mock private EncodingJobRunStatsLogger mEncodingJobRunStatsLogger;
    @Mock private EncoderLogicMetadataDao mEncoderLogicMetadataDao;
    @Mock private ConsentedDebugConfigurationGenerator mConsentedDebugConfigurationGenerator;
    @Mock private ProtectedSignalsArgument mProtectedSignalsArgument;
    @Mock private SignalsProviderAndArgumentFactory mSignalsProviderAndArgumentFactory;
    @Mock private AdSelectionEntryDao mAdSelectionEntryDao;
    @Mock private DevSessionController mDevSessionSetter;
    @Mock private DevSessionDataStore mDevSessionDataStore;

    private ShellCommandFactorySupplier mShellCommandFactorySupplier;

    @Before
    public void setup() {
        doNothing().when(mAdServicesLogger).logShellCommandStats(any());
        when(mSignalsProviderAndArgumentFactory.getProtectedSignalsArgument())
                .thenReturn(mProtectedSignalsArgument);
        mShellCommandFactorySupplier =
                new TestShellCommandFactorySupplier(
                        CUSTOM_AUDIENCE_CLI_ENABLED,
                        CONSENTED_DEBUG_CLI_ENABLED,
                        SIGNALS_CLI_ENABLED,
                        mBackgroundFetchRunner,
                        mCustomAudienceDao,
                        mConsentedDebugConfigurationDao,
                        mSignalsProviderAndArgumentFactory,
                        mBuyerInputGenerator,
                        mAuctionServerDataCompressor,
                        mEncodingJobRunner,
                        mEncoderLogicHandler,
                        mEncodingExecutionLogHelper,
                        mEncodingJobRunStatsLogger,
                        mEncoderLogicMetadataDao,
                        mConsentedDebugConfigurationGenerator,
                        mAdSelectionEntryDao,
                        mDevSessionSetter,
                        mDevSessionDataStore);
        mCmd = new OneTimeCommand(expect, mShellCommandFactorySupplier, mAdServicesLogger, mClock);
    }

    @Test
    public void testInvalidConstructor() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new AdServicesShellCommandHandler(
                                null, mShellCommandFactorySupplier, mAdServicesLogger));
        assertThrows(
                NullPointerException.class,
                () ->
                        new AdServicesShellCommandHandler(
                                new PrintWriter(new StringWriter()), null, mAdServicesLogger));
    }

    @Test
    public void testRun_invalidArgs() {
        assertThrows(NullPointerException.class, () -> mCmd.run(/* args...= */ (String[]) null));
        assertThrows(IllegalArgumentException.class, () -> mCmd.run(/* args...= */ new String[0]));
    }

    @Test
    public void testRunHelp() throws Exception {
        String result = mCmd.runValid(CMD_HELP);

        assertHelpContents(result);
    }

    @Test
    public void testRunHelpShort() throws Exception {
        String result = mCmd.runValid(CMD_SHORT_HELP);

        assertHelpContents(result);
    }

    @Test
    public void testRun_noCommand() throws Exception {
        String result = mCmd.runInvalid("");

        expect.withMessage("result of ''").that(result).isEqualTo(ERROR_EMPTY_COMMAND + "\n");
    }

    @Test
    public void testRun_invalidCommand() throws Exception {
        String cmd = "I can't believe this command is valid";
        String result = mCmd.runInvalid(cmd);

        expect.withMessage("result of '%s'", cmd).that(result).contains("Unknown command: " + cmd);
    }

    @Test
    public void testRunEcho_invalid() throws Exception {
        // no args
        expectInvalidArgument(HELP_ECHO, CMD_ECHO);
        // empty message
        expectInvalidArgument(HELP_ECHO, CMD_ECHO, "");
    }

    @Test
    public void testRunEcho_valid() throws Exception {
        when(mClock.currentTimeMillis()).thenReturn(0L, 2000L);

        String result = mCmd.runValid(CMD_ECHO, "108");

        expect.withMessage("result of '%s 108'", CMD_ECHO).that(result).isEqualTo("108\n");
        ShellCommandStats stats =
                new ShellCommandStats(
                        ShellCommandStats.COMMAND_ECHO, ShellCommandStats.RESULT_SUCCESS, 2000);
        verify(mAdServicesLogger).logShellCommandStats(stats);
    }

    @Test
    public void testUniqueCommandsInShellCommandFactory() {
        Map<String, List<String>> commandToFactories = new HashMap<>();
        for (ShellCommandFactory factory :
                mShellCommandFactorySupplier.getAllShellCommandFactories()) {
            commandToFactories
                    .computeIfAbsent(factory.getCommandPrefix(), unused -> new ArrayList<>())
                    .add(factory.getClass().getSimpleName());
        }

        for (String cmd : commandToFactories.keySet()) {
            List<String> factories = commandToFactories.get(cmd);
            expect.withMessage("factories(%s) for cmd %s", factories, cmd)
                    .that(factories)
                    .hasSize(1);
        }
    }

    @Test
    public void testRun_catchesExceptionAndReturnsError() throws Exception {
        Exception exception = new RuntimeException("something went wrong");
        ShellCommandFactory factory =
                new ShellCommandFactory() {
                    @Nullable
                    @Override
                    public ShellCommand getShellCommand(String cmd) {
                        throw new RuntimeException(exception);
                    }

                    @Override
                    public String getCommandPrefix() {
                        return CustomAudienceShellCommandFactory.COMMAND_PREFIX;
                    }

                    @Override
                    public List<String> getAllCommandsHelp() {
                        return null;
                    }
                };

        mShellCommandFactorySupplier =
                new ShellCommandFactorySupplier() {
                    @Override
                    public ImmutableList<ShellCommandFactory> getAllShellCommandFactories() {
                        return ImmutableList.of(factory);
                    }
                };

        mCmd = new OneTimeCommand(expect, mShellCommandFactorySupplier, mAdServicesLogger, mClock);
        String result = mCmd.runInvalid(CustomAudienceShellCommandFactory.COMMAND_PREFIX);

        expect.withMessage("err").that(result).contains(exception.getMessage());
    }

    private void assertHelpContents(String help) {
        HashSet<String> actualHelp = Sets.newHashSet(help.split("\n\n"));
        expect.withMessage("help")
                .that(actualHelp)
                .containsExactlyElementsIn(
                        Sets.newHashSet(
                                HELP_ECHO,
                                HELP_IS_ALLOWED_ATTRIBUTION_ACCESS,
                                HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS,
                                HELP_IS_ALLOWED_PROTECTED_SIGNALS_ACCESS,
                                HELP_IS_ALLOWED_AD_SELECTION_ACCESS,
                                HELP_IS_ALLOWED_TOPICS_ACCESS,
                                CustomAudienceListCommand.HELP,
                                CustomAudienceViewCommand.HELP,
                                CustomAudienceRefreshCommand.HELP,
                                ConsentedDebugShellCommand.HELP,
                                GenerateInputForEncodingCommand.HELP,
                                TriggerEncodingCommand.HELP,
                                GetAdSelectionDataCommand.HELP,
                                ViewAuctionResultCommand.HELP,
                                EnableAdServicesCommand.HELP_ENABLE_ADSERVICES,
                                ResetConsentCommand.HELP_RESET_CONSENT_DATA,
                                DevSessionCommand.HELP));
    }

    private void expectInvalidArgument(String syntax, String... args) throws IOException {
        OneTimeCommand cmd =
                new OneTimeCommand(expect, mShellCommandFactorySupplier, mAdServicesLogger, mClock);

        String expectedResult =
                String.format(ERROR_TEMPLATE_INVALID_ARGS, Arrays.toString(args), syntax);
        String actualResult = cmd.runInvalid(args);

        expect.withMessage("result of %s", Arrays.toString(args))
                .that(actualResult)
                .isEqualTo(expectedResult);
    }

    /** Helper to run a command and get the output. */
    private static final class OneTimeCommand {
        public final Expect expect;

        private final StringWriter mOutStringWriter = new StringWriter();
        private final PrintWriter mOut = new PrintWriter(mOutStringWriter);
        private final StringWriter mErrStringWriter = new StringWriter();
        private final PrintWriter mErr = new PrintWriter(mErrStringWriter);
        public final AdServicesShellCommandHandler cmd;

        private boolean mOutCalled;

        private OneTimeCommand(
                Expect expect,
                ShellCommandFactorySupplier shellCommandFactorySupplier,
                AdServicesLogger adservicesLogger,
                Clock clock) {
            this.expect = expect;
            cmd =
                    new AdServicesShellCommandHandler(
                            mOut, mErr, shellCommandFactorySupplier, adservicesLogger, clock);
        }

        /**
         * Runs a command that is expected to return a positive result.
         *
         * @return command output
         */
        String runValid(String... args) throws IOException {
            int result = run(args);
            expect.withMessage("result of run(%s)", Arrays.toString(args))
                    .that(result)
                    .isAtLeast(0);

            return getResult(mOut, mOutStringWriter);
        }

        /**
         * Runs a command that is expected to return a negative result.
         *
         * @return command output
         */
        String runInvalid(String... args) throws IOException {
            int result = run(args);
            expect.withMessage("result of run(%s)", Arrays.toString(args))
                    .that(result)
                    .isLessThan(0);

            return getResult(mErr, mErrStringWriter);
        }

        /**
         * Runs the given command.
         *
         * @return command result
         */
        int run(String... args) throws IOException {
            return cmd.run(args);
        }

        /**
         * Returns the output of the command.
         *
         * <p>Can only be called once per test, as there is no way to reset it, which could cause
         * confusion for the test developer.
         */
        String getResult(PrintWriter pw, StringWriter sw) throws IOException {
            if (mOutCalled) {
                throw new IllegalStateException("getOut() already called");
            }
            pw.flush();
            String out = sw.toString();
            pw.close();
            mOutCalled = true;
            return out;
        }
    }
}
