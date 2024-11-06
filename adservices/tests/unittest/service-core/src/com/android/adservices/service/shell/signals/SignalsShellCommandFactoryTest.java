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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.EncoderLogicMetadataDao;
import com.android.adservices.service.shell.NoOpShellCommand;
import com.android.adservices.service.shell.ShellCommand;
import com.android.adservices.service.shell.ShellCommandFactory;
import com.android.adservices.service.signals.PeriodicEncodingJobRunner;
import com.android.adservices.service.signals.ProtectedSignalsArgument;
import com.android.adservices.service.signals.SignalsProviderAndArgumentFactory;
import com.android.adservices.service.stats.pas.EncodingExecutionLogHelper;
import com.android.adservices.service.stats.pas.EncodingJobRunStatsLogger;

import com.google.common.collect.Sets;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public final class SignalsShellCommandFactoryTest extends AdServicesMockitoTestCase {
    private static final boolean SIGNALS_CLI_ENABLED = true;
    private ShellCommandFactory mFactory;

    @Mock private PeriodicEncodingJobRunner mMockPeriodicEncodingJobRunner;
    @Mock private EncoderLogicHandler mMockEncoderLogicHandler;
    @Mock private EncodingExecutionLogHelper mMockEncodingExecutionLogHelper;
    @Mock private EncodingJobRunStatsLogger mMockEncodingJobRunStatsLogger;
    @Mock private EncoderLogicMetadataDao mMockEncoderLogicMetadataDao;
    @Mock private ProtectedSignalsArgument mMockProtectedSignalsArgument;
    @Mock private SignalsProviderAndArgumentFactory mMockSignalsProviderAndArgumentFactory;

    @Before
    public void setup() {
        when(mMockSignalsProviderAndArgumentFactory.getProtectedSignalsArgument())
                .thenReturn(mMockProtectedSignalsArgument);
        mFactory =
                new SignalsShellCommandFactory(
                        SIGNALS_CLI_ENABLED,
                        mMockSignalsProviderAndArgumentFactory,
                        mMockPeriodicEncodingJobRunner,
                        mMockEncoderLogicHandler,
                        mMockEncodingExecutionLogHelper,
                        mMockEncodingJobRunStatsLogger,
                        mMockEncoderLogicMetadataDao);
    }

    @Test
    public void test_generateInputCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand(GenerateInputForEncodingCommand.CMD);
        assertThat(shellCommand).isInstanceOf(GenerateInputForEncodingCommand.class);
    }

    @Test
    public void test_invalidCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand("invalid");
        assertThat(shellCommand).isNull();
    }

    @Test
    public void test_nullCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand(null);
        assertThat(shellCommand).isNull();
    }

    @Test
    public void test_emptyCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand("");
        assertThat(shellCommand).isNull();
    }

    @Test
    public void test_cliDisabled() {
        mFactory =
                new SignalsShellCommandFactory(
                        false,
                        mMockSignalsProviderAndArgumentFactory,
                        mMockPeriodicEncodingJobRunner,
                        mMockEncoderLogicHandler,
                        mMockEncodingExecutionLogHelper,
                        mMockEncodingJobRunStatsLogger,
                        mMockEncoderLogicMetadataDao);
        ShellCommand shellCommand = mFactory.getShellCommand(GenerateInputForEncodingCommand.CMD);
        assertThat(shellCommand).isInstanceOf(NoOpShellCommand.class);
    }

    @Test
    public void test_invalidCmdCLIDisabled() {
        mFactory =
                new SignalsShellCommandFactory(
                        false,
                        mMockSignalsProviderAndArgumentFactory,
                        mMockPeriodicEncodingJobRunner,
                        mMockEncoderLogicHandler,
                        mMockEncodingExecutionLogHelper,
                        mMockEncodingJobRunStatsLogger,
                        mMockEncoderLogicMetadataDao);
        ShellCommand shellCommand = mFactory.getShellCommand("invalid");
        assertThat(shellCommand).isNull();
    }

    @Test
    public void test_getAllCommandsHelp() {
        mFactory =
                new SignalsShellCommandFactory(
                        SIGNALS_CLI_ENABLED,
                        mMockSignalsProviderAndArgumentFactory,
                        mMockPeriodicEncodingJobRunner,
                        mMockEncoderLogicHandler,
                        mMockEncodingExecutionLogHelper,
                        mMockEncodingJobRunStatsLogger,
                        mMockEncoderLogicMetadataDao);

        assertThat(Sets.newHashSet(mFactory.getAllCommandsHelp()))
                .containsExactlyElementsIn(
                        Sets.newHashSet(
                                GenerateInputForEncodingCommand.HELP, TriggerEncodingCommand.HELP));
    }
}
