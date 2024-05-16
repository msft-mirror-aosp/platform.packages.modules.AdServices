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

import static com.android.adservices.service.signals.ProtectedSignalsArgumentUtil.validateAndSerializeBase64;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_APP_SIGNALS_GENERATE_INPUT_FOR_ENCODING;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_GENERIC_ERROR;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;

import com.android.adservices.LoggerFactory;
import com.android.adservices.common.WebViewSupportUtil;
import com.android.adservices.service.common.NoOpRetryStrategyImpl;
import com.android.adservices.service.js.IsolateSettings;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.service.shell.ShellCommandTestCase;
import com.android.adservices.service.signals.ProtectedSignal;
import com.android.adservices.service.signals.ProtectedSignalsFixture;
import com.android.adservices.service.signals.SignalsProvider;
import com.android.adservices.service.stats.ShellCommandStats;
import com.android.adservices.shared.testing.SupportedByConditionRule;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class GenerateInputForEncodingCommandTest
        extends ShellCommandTestCase<GenerateInputForEncodingCommand> {
    private static final AdTechIdentifier BUYER = CommonFixture.VALID_BUYER_1;

    @ShellCommandStats.Command
    private static final int EXPECTED_COMMAND = COMMAND_APP_SIGNALS_GENERATE_INPUT_FOR_ENCODING;

    private static final String SIGNAL_GENERATION_SEED_1 = "hello";
    private static final String SIGNAL_GENERATION_SEED_2 = "world";
    private static final long JS_SCRIPT_ENGINE_TIMEOUT_SEC = 10;
    public static final String VALID_SIGNAL_KEY = "someKey";

    @Mock
    private SignalsProvider mSignalsProvider;

    // For executing the generated script after the command runs.
    private JSScriptEngine mJSScriptEngine;
    private final IsolateSettings mIsolateSettings =
            IsolateSettings.builder()
                    .setEnforceMaxHeapSizeFeature(true)
                    .setMaxHeapSizeBytes(50000)
                    .setIsolateConsoleMessageInLogsEnabled(true)
                    .build();

    @Rule(order = 6)
    public final SupportedByConditionRule webViewSupportsJSSandbox =
            WebViewSupportUtil.createJSSandboxAvailableRule(sContext);

    @Before
    public void setUp() {
        this.mJSScriptEngine =
                JSScriptEngine.getInstance(appContext.get(), LoggerFactory.getFledgeLogger());
    }

    @Test
    public void testRun_happyPath_returnsSuccess()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        List<ProtectedSignal> signals =
                List.of(
                        ProtectedSignalsFixture.generateProtectedSignal(
                                SIGNAL_GENERATION_SEED_1, new byte[]{(byte) 0xA1}),
                        ProtectedSignalsFixture.generateProtectedSignal(
                                SIGNAL_GENERATION_SEED_2, new byte[]{(byte) 0xA2}));
        when(mSignalsProvider.getSignals(BUYER)).thenReturn(Map.of(VALID_SIGNAL_KEY, signals));

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
        // We expect that ad techs need to define the encodeSignals() logic for this to execute, but
        // there should be no parsing errors.
        assertThat(exception.getMessage())
                .contains("Uncaught ReferenceError: encodeSignals is not defined");
        assertThat(jsScript).contains(ProtectedSignalsFixture.PACKAGE_NAME_PREFIX);
        Base64.Encoder encoder = Base64.getEncoder();
        assertThat(jsScript)
                .contains(
                        validateAndSerializeBase64(signals.get(0).getBase64EncodedValue()));
        assertThat(jsScript)
                .contains(
                        validateAndSerializeBase64(signals.get(1).getBase64EncodedValue()));
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
                        new GenerateInputForEncodingCommand(mSignalsProvider),
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
                new GenerateInputForEncodingCommand(mSignalsProvider),
                SignalsShellCommandFactory.COMMAND_PREFIX,
                GenerateInputForEncodingCommand.CMD,
                "--buyer",
                buyer.toString());
    }
}
