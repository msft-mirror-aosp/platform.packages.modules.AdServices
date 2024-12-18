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

import static android.adservices.common.CommonFixture.FIXED_NOW;

import static com.android.adservices.service.js.JSScriptArgument.jsonArrayArg;
import static com.android.adservices.service.js.JSScriptArgument.numericArg;
import static com.android.adservices.service.signals.ProtectedSignalsArgumentImpl.validateAndSerializeBase64;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_APP_SIGNALS_GENERATE_INPUT_FOR_ENCODING;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_GENERIC_ERROR;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;

import com.android.adservices.LoggerFactory;
import com.android.adservices.common.WebViewSupportUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.NoOpRetryStrategyImpl;
import com.android.adservices.service.js.IsolateSettings;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.service.shell.ShellCommandTestCase;
import com.android.adservices.service.signals.ProtectedSignal;
import com.android.adservices.service.signals.ProtectedSignalsArgument;
import com.android.adservices.service.signals.ProtectedSignalsFixture;
import com.android.adservices.service.signals.SignalsProvider;
import com.android.adservices.service.signals.SignalsProviderAndArgumentFactory;
import com.android.adservices.service.stats.ShellCommandStats;
import com.android.adservices.shared.testing.SupportedByConditionRule;

import com.google.common.collect.ImmutableList;

import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public final class GenerateInputForEncodingCommandTest
        extends ShellCommandTestCase<GenerateInputForEncodingCommand> {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private static final AdTechIdentifier BUYER = CommonFixture.VALID_BUYER_1;

    @ShellCommandStats.Command
    private static final int EXPECTED_COMMAND = COMMAND_APP_SIGNALS_GENERATE_INPUT_FOR_ENCODING;

    private static final String SIGNAL_GENERATION_SEED_1 = "746573745F6B65795F41";
    private static final String SIGNAL_GENERATION_SEED_2 = "746573745F6B65795F42";
    private static final long JS_SCRIPT_ENGINE_TIMEOUT_SEC = 10;
    public static final String VALID_SIGNAL_KEY = "746573745F6B65795F42";
    public static final int MAX_SIZE_BYTES = Flags.PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES;

    @Mock private SignalsProvider mSignalsProvider;
    @Mock private ProtectedSignalsArgument mProtectedSignalsArgument;
    @Mock private SignalsProviderAndArgumentFactory mSignalsProviderAndArgumentFactory;

    // For executing the generated script after the command runs.
    private JSScriptEngine mJSScriptEngine;
    private final IsolateSettings mIsolateSettings =
            IsolateSettings.builder()
                    .setMaxHeapSizeBytes(50000)
                    .setIsolateConsoleMessageInLogsEnabled(true)
                    .build();

    private static final String VALID_SIGNALS_JSON_ARRAY =
            "[{\""
                    + SIGNAL_GENERATION_SEED_1
                    + "\":"
                    + "[{\"val\":\"A1\","
                    + "\"time\":"
                    + FIXED_NOW.getEpochSecond()
                    + ",\"app\":\"com.fake.packagesignalA1\"},"
                    + "{\"val\":\"A2\","
                    + "\"time\":"
                    + FIXED_NOW.getEpochSecond()
                    + ",\"app\":\"com.fake.packagesignalA1\"}]},"
                    + "{\""
                    + SIGNAL_GENERATION_SEED_2
                    + "\":"
                    + "[{\"val\":\"B3\","
                    + "\"time\":"
                    + FIXED_NOW.getEpochSecond()
                    + ",\"app\":\"com.fake.packagesignalB1\"}]}]";

    @Rule(order = 6)
    public final SupportedByConditionRule webViewSupportsJSSandbox =
            WebViewSupportUtil.createJSSandboxAvailableRule(sContext);

    @Before
    public void killAndRecreateJavascriptSandboxIsolate() throws Exception {
        if (mJSScriptEngine == null) {
            mJSScriptEngine = JSScriptEngine.getInstance();
        }
        // Kill the JSScriptEngine to ensure it re-creates the JavascriptEngine isolate.
        mJSScriptEngine.shutdown().get(JS_SCRIPT_ENGINE_TIMEOUT_SEC, TimeUnit.SECONDS);

        when(mSignalsProviderAndArgumentFactory.getSignalsProvider()).thenReturn(mSignalsProvider);
        when(mSignalsProviderAndArgumentFactory.getProtectedSignalsArgument())
                .thenReturn(mProtectedSignalsArgument);
    }

    @After
    public void tearDown() throws Exception {
        if (mJSScriptEngine != null) {
            mJSScriptEngine.shutdown().get(JS_SCRIPT_ENGINE_TIMEOUT_SEC, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testRun_happyPath_returnsSuccess() throws JSONException {
        List<ProtectedSignal> signals =
                List.of(
                        ProtectedSignalsFixture.generateBase64ProtectedSignal(
                                SIGNAL_GENERATION_SEED_1, new byte[] {(byte) 0xA1}),
                        ProtectedSignalsFixture.generateBase64ProtectedSignal(
                                SIGNAL_GENERATION_SEED_2, new byte[] {(byte) 0xA2}));
        Map<String, List<ProtectedSignal>> rawSignals = Map.of(VALID_SIGNAL_KEY, signals);
        when(mSignalsProvider.getSignals(BUYER)).thenReturn(rawSignals);
        when(mProtectedSignalsArgument.getArgumentsFromRawSignalsAndMaxSize(
                        eq(rawSignals), eq(MAX_SIZE_BYTES)))
                .thenReturn(
                        ImmutableList.of(
                                jsonArrayArg("__rb_protected_signals", VALID_SIGNALS_JSON_ARRAY),
                                numericArg("__rb_max_size_bytes", MAX_SIZE_BYTES)));

        Result actualResult = runCommandAndGetResult(BUYER);
        expectSuccess(actualResult, EXPECTED_COMMAND);
        String jsScript = actualResult.mOut;
        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mJSScriptEngine
                                        .evaluate(
                                                jsScript,
                                                mIsolateSettings,
                                                new NoOpRetryStrategyImpl())
                                        .get(JS_SCRIPT_ENGINE_TIMEOUT_SEC, TimeUnit.SECONDS));

        // We expect that ad techs need to define the encodeSignals() logic for this to
        // execute, but there should be no parsing errors.
        assertThat(exception)
                .hasMessageThat()
                .contains("Uncaught ReferenceError: encodeSignals is not defined");
        assertThat(jsScript).contains(ProtectedSignalsFixture.PACKAGE_NAME_PREFIX);
        assertThat(jsScript)
                .contains(validateAndSerializeBase64(signals.get(0).getBase64EncodedValue()));
        assertThat(jsScript)
                .contains(validateAndSerializeBase64(signals.get(1).getBase64EncodedValue()));
    }

    @Test
    public void testRun_withEmptyDb_returnsEmpty() {
        when(mSignalsProvider.getSignals(BUYER)).thenReturn(Map.of());

        Result actualResult = runCommandAndGetResult(BUYER);

        expectFailure(actualResult, "no signals found.", EXPECTED_COMMAND, RESULT_GENERIC_ERROR);
    }

    @Test
    public void testRun_withoutBuyerParam_throwsException() {
        Result actualResult =
                run(
                        new GenerateInputForEncodingCommand(mSignalsProviderAndArgumentFactory),
                        SignalsShellCommandFactory.COMMAND_PREFIX,
                        GenerateInputForEncodingCommand.CMD);

        expectFailure(
                actualResult,
                "--buyer argument must be provided",
                EXPECTED_COMMAND,
                RESULT_GENERIC_ERROR);
    }

    private Result runCommandAndGetResult(AdTechIdentifier buyer) {
        return run(
                new GenerateInputForEncodingCommand(mSignalsProviderAndArgumentFactory),
                SignalsShellCommandFactory.COMMAND_PREFIX,
                GenerateInputForEncodingCommand.CMD,
                "--buyer",
                buyer.toString());
    }
}
