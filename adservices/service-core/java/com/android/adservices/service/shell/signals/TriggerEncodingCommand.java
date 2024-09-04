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

import static com.android.adservices.service.shell.AdServicesShellCommandHandler.TAG;
import static com.android.adservices.service.shell.signals.SignalsShellCommandArgs.ARG_PARSE_START_INDEX;
import static com.android.adservices.service.shell.signals.SignalsShellCommandArgs.BUYER;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_APP_SIGNALS_TRIGGER_ENCODING;

import android.adservices.common.AdTechIdentifier;
import android.util.Log;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.signals.DBEncoderLogicMetadata;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.EncoderLogicMetadataDao;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.shell.AbstractShellCommand;
import com.android.adservices.service.shell.ShellCommandArgParserHelper;
import com.android.adservices.service.shell.ShellCommandResult;
import com.android.adservices.service.signals.PeriodicEncodingJobRunner;
import com.android.adservices.service.stats.ShellCommandStats;
import com.android.adservices.service.stats.pas.EncodingExecutionLogHelper;
import com.android.adservices.service.stats.pas.EncodingJobRunStatsLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FluentFuture;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TriggerEncodingCommand extends AbstractShellCommand {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting public static final String CMD = "trigger-encoding";

    public static final String HELP =
            SignalsShellCommandFactory.COMMAND_PREFIX
                    + " "
                    + CMD
                    + " "
                    + BUYER
                    + " <buyer>"
                    + " "
                    + "\n    Trigger signals encoding for a given buyer.";

    @VisibleForTesting public static final int TIMEOUT_SEC = 5;

    @VisibleForTesting
    public static final String ERROR_BUYER_ARGUMENT = "--buyer argument must be provided";

    @VisibleForTesting
    public static final String ERROR_TIMEOUT =
            "timed out during signals download or encoding execution";

    @VisibleForTesting
    public static final String OUTPUT_SUCCESS = "successfully completed signals encoding";

    @VisibleForTesting
    public static final String ERROR_FAIL_TO_ENCODE_SIGNALS =
            "failed during signal encoding execution";

    private final PeriodicEncodingJobRunner mPeriodicEncodingJobRunner;
    private final EncoderLogicHandler mEncoderLogicHandler;
    private final EncodingExecutionLogHelper mEncodingExecutionLogHelper;
    private final EncodingJobRunStatsLogger mEncodingJobRunStatsLogger;
    private final EncoderLogicMetadataDao mEncoderLogicMetadataDao;

    public TriggerEncodingCommand(
            PeriodicEncodingJobRunner encodingJobRunner,
            EncoderLogicHandler encoderLogicHandler,
            EncodingExecutionLogHelper encodingExecutionLogHelper,
            EncodingJobRunStatsLogger encodingJobRunStatsLogger,
            EncoderLogicMetadataDao encoderLogicMetadataDao) {
        mPeriodicEncodingJobRunner = Objects.requireNonNull(encodingJobRunner);
        mEncoderLogicHandler = Objects.requireNonNull(encoderLogicHandler);
        mEncodingExecutionLogHelper = Objects.requireNonNull(encodingExecutionLogHelper);
        mEncodingJobRunStatsLogger = Objects.requireNonNull(encodingJobRunStatsLogger);
        mEncoderLogicMetadataDao = Objects.requireNonNull(encoderLogicMetadataDao);
    }

    @Override
    public ShellCommandResult run(PrintWriter out, PrintWriter err, String[] args) {
        ImmutableMap<String, String> cliArgs;
        try {
            cliArgs = ShellCommandArgParserHelper.parseCliArguments(args, ARG_PARSE_START_INDEX);
        } catch (IllegalArgumentException exception) {
            Log.e(
                    TAG,
                    "IllegalArgumentException while running trigger-encoding command",
                    exception);
            return invalidArgsError(HELP, err, getMetricsLoggerCommand(), args);
        }

        if (!cliArgs.containsKey(BUYER) || cliArgs.get(BUYER) == null) {
            sLogger.v(
                    "failure with error message: %s. current args are: %s",
                    ERROR_BUYER_ARGUMENT, Arrays.toString(args));
            err.write(ERROR_BUYER_ARGUMENT);
            return toShellCommandResult(
                    ShellCommandStats.RESULT_GENERIC_ERROR, getMetricsLoggerCommand());
        }

        AdTechIdentifier buyer =
                AdTechIdentifier.fromString(Objects.requireNonNull(cliArgs.get(BUYER)));
        sLogger.v("triggering signals update for buyer: %s", buyer);
        try {
            return updateAndTriggerEncoding(buyer, out);
        } catch (InterruptedException | ExecutionException | IllegalStateException e) {
            err.write(ERROR_FAIL_TO_ENCODE_SIGNALS);
            sLogger.w(e, ERROR_FAIL_TO_ENCODE_SIGNALS);
            return toShellCommandResult(
                    ShellCommandStats.RESULT_GENERIC_ERROR, getMetricsLoggerCommand());
        } catch (TimeoutException e) {
            err.write(ERROR_TIMEOUT);
            sLogger.v(ERROR_TIMEOUT + ": " + e.getMessage());
            return toShellCommandResult(
                    ShellCommandStats.RESULT_TIMEOUT_ERROR, getMetricsLoggerCommand());
        }
    }

    private ShellCommandResult updateAndTriggerEncoding(AdTechIdentifier buyer, PrintWriter out)
            throws ExecutionException, InterruptedException, TimeoutException {
        sLogger.v("triggering download and update for buyer: " + buyer);
        FluentFuture<Boolean> downloadAndUpdateFuture =
                mEncoderLogicHandler.downloadAndUpdate(buyer, DevContext.createForDevIdentity());
        boolean success = downloadAndUpdateFuture.get(TIMEOUT_SEC, TimeUnit.SECONDS);
        if (!success) {
            throw new IllegalStateException(ERROR_FAIL_TO_ENCODE_SIGNALS);
        }

        sLogger.v("triggering encoding for buyer: " + buyer);
        mPeriodicEncodingJobRunner
                .runEncodingPerBuyer(
                        getDbEncoderLogicMetadata(buyer),
                        TIMEOUT_SEC,
                        mEncodingExecutionLogHelper,
                        mEncodingJobRunStatsLogger)
                .get(TIMEOUT_SEC, TimeUnit.SECONDS);

        out.write(OUTPUT_SUCCESS);
        sLogger.v("successfully completed encoding of signals for buyer: %s", buyer);
        return toShellCommandResult(ShellCommandStats.RESULT_SUCCESS, getMetricsLoggerCommand());
    }

    private DBEncoderLogicMetadata getDbEncoderLogicMetadata(AdTechIdentifier buyer) {
        return mEncoderLogicMetadataDao.getMetadata(buyer);
    }

    @Override
    public String getCommandName() {
        return CMD;
    }

    @Override
    public int getMetricsLoggerCommand() {
        return COMMAND_APP_SIGNALS_TRIGGER_ENCODING;
    }

    @Override
    public String getCommandHelp() {
        return HELP;
    }
}
