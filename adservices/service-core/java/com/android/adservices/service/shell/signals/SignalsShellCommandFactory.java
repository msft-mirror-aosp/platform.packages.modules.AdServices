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

import static com.android.adservices.service.DebugFlagsConstants.KEY_PROTECTED_APP_SIGNALS_CLI_ENABLED;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.EncoderLogicMetadataDao;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.RetryStrategy;
import com.android.adservices.service.common.RetryStrategyFactory;
import com.android.adservices.service.shell.AdServicesShellCommandHandler;
import com.android.adservices.service.shell.NoOpShellCommand;
import com.android.adservices.service.shell.ShellCommand;
import com.android.adservices.service.shell.ShellCommandFactory;
import com.android.adservices.service.signals.PeriodicEncodingJobRunner;
import com.android.adservices.service.signals.SignalsProviderAndArgumentFactory;
import com.android.adservices.service.signals.SignalsScriptEngine;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.pas.EncodingExecutionLogHelper;
import com.android.adservices.service.stats.pas.EncodingExecutionLogHelperImpl;
import com.android.adservices.service.stats.pas.EncodingJobRunStats;
import com.android.adservices.service.stats.pas.EncodingJobRunStatsLogger;
import com.android.adservices.service.stats.pas.EncodingJobRunStatsLoggerImpl;
import com.android.adservices.shared.util.Clock;

import com.google.common.collect.ImmutableSet;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SignalsShellCommandFactory implements ShellCommandFactory {
    public static final String COMMAND_PREFIX = "app-signals";
    private final Map<String, ShellCommand> mAllCommandsMap;
    private final boolean mIsSignalsCliEnabled;

    public SignalsShellCommandFactory(
            boolean isSignalsCliEnabled,
            SignalsProviderAndArgumentFactory signalsProviderAndArgumentFactory,
            PeriodicEncodingJobRunner encodingJobRunner,
            EncoderLogicHandler encoderLogicHandler,
            EncodingExecutionLogHelper encodingExecutionLogHelper,
            EncodingJobRunStatsLogger encodingJobRunStatsLogger,
            EncoderLogicMetadataDao encoderLogicMetadataDao) {
        mIsSignalsCliEnabled = isSignalsCliEnabled;
        Set<ShellCommand> allCommandsMap =
                ImmutableSet.of(
                        new GenerateInputForEncodingCommand(signalsProviderAndArgumentFactory),
                        new TriggerEncodingCommand(
                                encodingJobRunner,
                                encoderLogicHandler,
                                encodingExecutionLogHelper,
                                encodingJobRunStatsLogger,
                                encoderLogicMetadataDao));
        mAllCommandsMap =
                allCommandsMap.stream()
                        .collect(
                                Collectors.toMap(
                                        ShellCommand::getCommandName, Function.identity()));
    }

    /** Gets a new {@link SignalsShellCommandFactory} instance. */
    // TODO(b/311183933): Remove passed in Context from static method.
    @SuppressWarnings("AvoidStaticContext")
    public static ShellCommandFactory newInstance(
            DebugFlags debugFlags,
            ProtectedSignalsDao protectedSignalsDao,
            Flags flags,
            Context context) {
        EncoderLogicHandler encoderLogicHandler = new EncoderLogicHandler(context);
        SignalsProviderAndArgumentFactory signalsProviderAndArgumentFactory =
                new SignalsProviderAndArgumentFactory(
                        protectedSignalsDao, flags.getPasEncodingJobImprovementsEnabled());
        return new SignalsShellCommandFactory(
                debugFlags.getProtectedAppSignalsCommandsEnabled(),
                signalsProviderAndArgumentFactory,
                new PeriodicEncodingJobRunner(
                        signalsProviderAndArgumentFactory,
                        protectedSignalsDao,
                        new SignalsScriptEngine(
                                flags::getIsolateMaxHeapSizeBytes,
                                getRetryStrategy(flags),
                                debugFlags::getAdServicesJsIsolateConsoleMessagesInLogsEnabled),
                        flags.getProtectedSignalsMaxJsFailureExecutionOnCertainVersionBeforeStop(),
                        flags.getProtectedSignalsEncodedPayloadMaxSizeBytes(),
                        encoderLogicHandler,
                        ProtectedSignalsDatabase.getInstance().getEncodedPayloadDao(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor()),
                encoderLogicHandler,
                new EncodingExecutionLogHelperImpl(
                        AdServicesLoggerImpl.getInstance(),
                        Clock.getInstance(),
                        EnrollmentDao.getInstance()),
                new EncodingJobRunStatsLoggerImpl(
                        AdServicesLoggerImpl.getInstance(),
                        EncodingJobRunStats.builder(),
                        flags.getFledgeEnableForcedEncodingAfterSignalsUpdate()),
                ProtectedSignalsDatabase.getInstance().getEncoderLogicMetadataDao());
    }

    private static RetryStrategy getRetryStrategy(Flags flags) {
        return RetryStrategyFactory.createInstance(
                        flags.getAdServicesRetryStrategyEnabled(),
                        AdServicesExecutors.getLightWeightExecutor())
                .createRetryStrategy(flags.getAdServicesJsScriptEngineMaxRetryAttempts());
    }

    @Nullable
    @Override
    public ShellCommand getShellCommand(String cmd) {
        if (!mAllCommandsMap.containsKey(cmd)) {
            Log.d(
                    AdServicesShellCommandHandler.TAG,
                    String.format("Invalid command for Signals Shell Factory: %s", cmd));
            return null;
        }
        ShellCommand command = mAllCommandsMap.get(cmd);
        if (!mIsSignalsCliEnabled) {
            return new NoOpShellCommand(
                    cmd, command.getMetricsLoggerCommand(), KEY_PROTECTED_APP_SIGNALS_CLI_ENABLED);
        }
        return command;
    }

    @NonNull
    @Override
    public String getCommandPrefix() {
        return COMMAND_PREFIX;
    }

    @NonNull
    @Override
    public List<String> getAllCommandsHelp() {
        return mAllCommandsMap.values().stream()
                .map(ShellCommand::getCommandHelp)
                .collect(Collectors.toList());
    }
}
