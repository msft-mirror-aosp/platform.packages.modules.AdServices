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
import static com.android.adservices.service.shell.signals.GenerateInputForEncodingArgs.ARG_PARSE_START_INDEX;
import static com.android.adservices.service.shell.signals.GenerateInputForEncodingArgs.BUYER;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_APP_SIGNALS_GENERATE_INPUT_FOR_ENCODING;

import android.adservices.common.AdTechIdentifier;
import android.util.Log;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.shell.AbstractShellCommand;
import com.android.adservices.service.shell.ShellCommandArgParserHelper;
import com.android.adservices.service.shell.ShellCommandResult;
import com.android.adservices.service.signals.ProtectedSignal;
import com.android.adservices.service.signals.SignalsDriverLogicGenerator;
import com.android.adservices.service.signals.SignalsProvider;
import com.android.adservices.service.signals.SignalsProviderImpl;
import com.android.adservices.service.stats.ShellCommandStats;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableMap;

import org.json.JSONException;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GenerateInputForEncodingCommand extends AbstractShellCommand {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting public static final String CMD = "generate-input-for-encoding";

    public static final String HELP =
            SignalsShellCommandFactory.COMMAND_PREFIX
                    + " "
                    + CMD
                    + " "
                    + BUYER
                    + " <buyer>"
                    + " "
                    + "\n    Generate input JavaScript for signals encoding. This command generates"
                    + " JavaScript code that can be appended to a user-supplied encodeSignals"
                    + " method for offline testing.";

    private static final int MAX_SIZE_IN_BYTES =
            Flags.PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES;

    private final SignalsProvider mSignalsProvider;

    public GenerateInputForEncodingCommand(SignalsProvider signalsProvider) {
        mSignalsProvider = signalsProvider;
    }

    @Override
    public ShellCommandResult run(PrintWriter out, PrintWriter err, String[] args) {
        ImmutableMap<String, String> cliArgs;
        try {
            cliArgs = ShellCommandArgParserHelper.parseCliArguments(args, ARG_PARSE_START_INDEX);
        } catch (IllegalArgumentException exception) {
            Log.e(
                    TAG,
                    "IllegalArgumentException while running generate_input_for_encoding command",
                    exception);
            return invalidArgsError(
                    HELP, err, COMMAND_APP_SIGNALS_GENERATE_INPUT_FOR_ENCODING, args);
        }

        if (!cliArgs.containsKey(BUYER) || cliArgs.get(BUYER) == null) {
            return getFailureResult("--buyer argument must be provided", err);
        }

        AdTechIdentifier buyer =
                AdTechIdentifier.fromString(Objects.requireNonNull(cliArgs.get(BUYER)));
        sLogger.v("Querying for all signals for buyer: %s", buyer);

        Map<String, List<ProtectedSignal>> rawSignalsMap = mSignalsProvider.getSignals(buyer);
        if (rawSignalsMap.isEmpty()) {
            return getFailureResult("no signals found.", err);
        }

        try {
            String driverLogicWithRawSignals =
                    SignalsDriverLogicGenerator.getDriverLogicWithArguments(
                            rawSignalsMap, MAX_SIZE_IN_BYTES);
            sLogger.v("generated code for buyer: " + driverLogicWithRawSignals);
            out.write(driverLogicWithRawSignals);
        } catch (JSONException e) {
            return getFailureResult("could not encode signals for printing", err);
        }
        return toShellCommandResult(
                ShellCommandStats.RESULT_SUCCESS, COMMAND_APP_SIGNALS_GENERATE_INPUT_FOR_ENCODING);
    }

    @Override
    public String getCommandName() {
        return CMD;
    }

    @Override
    public int getMetricsLoggerCommand() {
        return COMMAND_APP_SIGNALS_GENERATE_INPUT_FOR_ENCODING;
    }

    @Override
    public String getCommandHelp() {
        return HELP;
    }

    private static ShellCommandResult getFailureResult(String errorMessage, PrintWriter err) {
        sLogger.v("Failure with error message: %s", errorMessage);
        err.write(errorMessage);
        return toShellCommandResult(
                ShellCommandStats.RESULT_GENERIC_ERROR,
                COMMAND_APP_SIGNALS_GENERATE_INPUT_FOR_ENCODING);
    }
}
