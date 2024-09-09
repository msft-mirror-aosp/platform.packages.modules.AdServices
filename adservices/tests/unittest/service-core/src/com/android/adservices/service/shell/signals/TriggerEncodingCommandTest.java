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

import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_APP_SIGNALS_TRIGGER_ENCODING;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_GENERIC_ERROR;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;

import com.android.adservices.data.signals.DBEncoderLogicMetadata;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.EncoderLogicMetadataDao;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.shell.ShellCommandTestCase;
import com.android.adservices.service.signals.PeriodicEncodingJobRunner;
import com.android.adservices.service.stats.ShellCommandStats;
import com.android.adservices.service.stats.pas.EncodingExecutionLogHelper;
import com.android.adservices.service.stats.pas.EncodingJobRunStatsLogger;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;

import org.junit.Test;
import org.mockito.Mock;

import java.time.Instant;

public final class TriggerEncodingCommandTest extends ShellCommandTestCase<TriggerEncodingCommand> {
    private static final AdTechIdentifier BUYER = CommonFixture.VALID_BUYER_1;


    @ShellCommandStats.Command
    private static final int EXPECTED_COMMAND = COMMAND_APP_SIGNALS_TRIGGER_ENCODING;

    @Mock private PeriodicEncodingJobRunner mPeriodicEncodingJobRunner;
    @Mock private EncoderLogicHandler mEncoderLogicHandler;
    @Mock private EncodingExecutionLogHelper mEncodingExecutionLogHelper;
    @Mock private EncodingJobRunStatsLogger mEncodingJobRunStatsLogger;
    @Mock private EncoderLogicMetadataDao mEncoderLogicMetadataDao;

    @Test
    public void testRun_happyPath_returnsSuccess() {
        when(mEncoderLogicHandler.downloadAndUpdate(BUYER, DevContext.createForDevIdentity()))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(true)));
        when(mPeriodicEncodingJobRunner.runEncodingPerBuyer(any(), anyInt(), any(), any()))
                .thenReturn(FluentFuture.from(Futures.immediateVoidFuture()));
        when(mEncoderLogicMetadataDao.getMetadata(BUYER))
                .thenReturn(
                        DBEncoderLogicMetadata.builder()
                                .setBuyer(BUYER)
                                .setCreationTime(Instant.now())
                                .setVersion(1)
                                .setFailedEncodingCount(0)
                                .build());

        Result actualResult =
                run(
                        new TriggerEncodingCommand(
                                mPeriodicEncodingJobRunner,
                                mEncoderLogicHandler,
                                mEncodingExecutionLogHelper,
                                mEncodingJobRunStatsLogger,
                                mEncoderLogicMetadataDao),
                        SignalsShellCommandFactory.COMMAND_PREFIX,
                        TriggerEncodingCommand.CMD,
                        SignalsShellCommandArgs.BUYER,
                        BUYER.toString());

        expectSuccess(actualResult, EXPECTED_COMMAND);
        assertThat(actualResult.mOut).isEqualTo(TriggerEncodingCommand.OUTPUT_SUCCESS);
    }

    @Test
    public void testRun_withFailedDownload_returnsFailure() {
        when(mEncoderLogicHandler.downloadAndUpdate(BUYER, DevContext.createForDevIdentity()))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(false)));
        when(mPeriodicEncodingJobRunner.runEncodingPerBuyer(any(), anyInt(), any(), any()))
                .thenReturn(FluentFuture.from(Futures.immediateVoidFuture()));
        when(mEncoderLogicMetadataDao.getMetadata(BUYER))
                .thenReturn(
                        DBEncoderLogicMetadata.builder()
                                .setBuyer(BUYER)
                                .setCreationTime(Instant.now())
                                .setVersion(1)
                                .setFailedEncodingCount(0)
                                .build());

        Result actualResult =
                run(
                        new TriggerEncodingCommand(
                                mPeriodicEncodingJobRunner,
                                mEncoderLogicHandler,
                                mEncodingExecutionLogHelper,
                                mEncodingJobRunStatsLogger,
                                mEncoderLogicMetadataDao),
                        SignalsShellCommandFactory.COMMAND_PREFIX,
                        TriggerEncodingCommand.CMD,
                        SignalsShellCommandArgs.BUYER,
                        BUYER.toString());

        expectFailure(
                actualResult,
                TriggerEncodingCommand.ERROR_FAIL_TO_ENCODE_SIGNALS,
                EXPECTED_COMMAND,
                RESULT_GENERIC_ERROR);
    }

    @Test
    public void testRun_withDownloadFailure_returnsFailure() {
        when(mEncoderLogicHandler.downloadAndUpdate(BUYER, DevContext.createForDevIdentity()))
                .thenAnswer(
                        i -> {
                            throw new IllegalStateException("some failure");
                        });
        when(mPeriodicEncodingJobRunner.runEncodingPerBuyer(any(), anyInt(), any(), any()))
                .thenReturn(FluentFuture.from(Futures.immediateVoidFuture()));

        Result actualResult =
                run(
                        new TriggerEncodingCommand(
                                mPeriodicEncodingJobRunner,
                                mEncoderLogicHandler,
                                mEncodingExecutionLogHelper,
                                mEncodingJobRunStatsLogger,
                                mEncoderLogicMetadataDao),
                        SignalsShellCommandFactory.COMMAND_PREFIX,
                        TriggerEncodingCommand.CMD,
                        SignalsShellCommandArgs.BUYER,
                        BUYER.toString());

        expectFailure(
                actualResult,
                TriggerEncodingCommand.ERROR_FAIL_TO_ENCODE_SIGNALS,
                EXPECTED_COMMAND,
                RESULT_GENERIC_ERROR);
    }

    @Test
    public void testRun_withoutBuyerParam_throwsException() {
        Result actualResult =
                run(
                        new TriggerEncodingCommand(
                                mPeriodicEncodingJobRunner,
                                mEncoderLogicHandler,
                                mEncodingExecutionLogHelper,
                                mEncodingJobRunStatsLogger,
                                mEncoderLogicMetadataDao),
                        SignalsShellCommandFactory.COMMAND_PREFIX,
                        TriggerEncodingCommand.CMD);

        expectFailure(
                actualResult,
                TriggerEncodingCommand.ERROR_BUYER_ARGUMENT,
                EXPECTED_COMMAND,
                RESULT_GENERIC_ERROR);
    }
}
