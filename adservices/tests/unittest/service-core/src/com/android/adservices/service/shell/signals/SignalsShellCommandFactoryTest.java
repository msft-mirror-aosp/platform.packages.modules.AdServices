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

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.EncoderLogicMetadataDao;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.service.shell.NoOpShellCommand;
import com.android.adservices.service.shell.ShellCommand;
import com.android.adservices.service.shell.ShellCommandFactory;
import com.android.adservices.service.signals.PeriodicEncodingJobRunner;
import com.android.adservices.service.stats.pas.EncodingExecutionLogHelper;
import com.android.adservices.service.stats.pas.EncodingJobRunStatsLogger;

import com.google.common.collect.Sets;
import com.google.common.truth.Truth;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class SignalsShellCommandFactoryTest extends AdServicesMockitoTestCase {
    private static final boolean SIGNALS_CLI_ENABLED = true;
    private ShellCommandFactory mFactory;
    @Mock private ProtectedSignalsDao mProtectedSignalsDao;
    @Mock private PeriodicEncodingJobRunner mPeriodicEncodingJobRunner;
    @Mock private EncoderLogicHandler mEncoderLogicHandler;
    @Mock private EncodingExecutionLogHelper mEncodingExecutionLogHelper;
    @Mock private EncodingJobRunStatsLogger mEncodingJobRunStatsLogger;
    @Mock private EncoderLogicMetadataDao mEncoderLogicMetadataDao;

    @Before
    public void setup() {
        mFactory =
                new SignalsShellCommandFactory(
                        SIGNALS_CLI_ENABLED,
                        mProtectedSignalsDao,
                        mPeriodicEncodingJobRunner,
                        mEncoderLogicHandler,
                        mEncodingExecutionLogHelper,
                        mEncodingJobRunStatsLogger,
                        mEncoderLogicMetadataDao);
    }

    @Test
    public void test_generateInputCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand(GenerateInputForEncodingCommand.CMD);
        Truth.assertThat(shellCommand).isInstanceOf(GenerateInputForEncodingCommand.class);
    }

    @Test
    public void test_invalidCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand("invalid");
        Truth.assertThat(shellCommand).isNull();
    }

    @Test
    public void test_nullCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand(null);
        Truth.assertThat(shellCommand).isNull();
    }

    @Test
    public void test_emptyCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand("");
        Truth.assertThat(shellCommand).isNull();
    }

    @Test
    public void test_cliDisabled() {
        mFactory =
                new SignalsShellCommandFactory(
                        false,
                        mProtectedSignalsDao,
                        mPeriodicEncodingJobRunner,
                        mEncoderLogicHandler,
                        mEncodingExecutionLogHelper,
                        mEncodingJobRunStatsLogger,
                        mEncoderLogicMetadataDao);
        ShellCommand shellCommand = mFactory.getShellCommand(GenerateInputForEncodingCommand.CMD);
        Truth.assertThat(shellCommand).isInstanceOf(NoOpShellCommand.class);
    }

    @Test
    public void test_invalidCmdCLIDisabled() {
        mFactory =
                new SignalsShellCommandFactory(
                        false,
                        mProtectedSignalsDao,
                        mPeriodicEncodingJobRunner,
                        mEncoderLogicHandler,
                        mEncodingExecutionLogHelper,
                        mEncodingJobRunStatsLogger,
                        mEncoderLogicMetadataDao);
        ShellCommand shellCommand = mFactory.getShellCommand("invalid");
        Truth.assertThat(shellCommand).isNull();
    }

    @Test
    public void test_getAllCommandsHelp() {
        mFactory =
                new SignalsShellCommandFactory(
                        SIGNALS_CLI_ENABLED,
                        mProtectedSignalsDao,
                        mPeriodicEncodingJobRunner,
                        mEncoderLogicHandler,
                        mEncodingExecutionLogHelper,
                        mEncodingJobRunStatsLogger,
                        mEncoderLogicMetadataDao);

        Truth.assertThat(Sets.newHashSet(mFactory.getAllCommandsHelp()))
                .containsExactlyElementsIn(
                        Sets.newHashSet(
                                GenerateInputForEncodingCommand.HELP, TriggerEncodingCommand.HELP));
    }
}
