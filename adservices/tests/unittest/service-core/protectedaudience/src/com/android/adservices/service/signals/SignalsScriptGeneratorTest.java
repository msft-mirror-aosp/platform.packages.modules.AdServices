/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.signals;

import static org.junit.Assert.assertThrows;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.LoggerFactory;
import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.common.WebViewSupportUtil;
import com.android.adservices.service.common.NoOpRetryStrategyImpl;
import com.android.adservices.service.js.IsolateSettings;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.shared.testing.SupportedByConditionRule;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SignalsScriptGeneratorTest extends AdServicesUnitTestCase {

    private static final int JS_SCRIPT_ENGINE_TIMEOUT_SEC = 10;
    private static final int MAX_SIZE_IN_BYTES = 10000;
    private static final String ENCODE_SIGNALS_STUB =
            "function encodeSignals(signals, maxSize) { \n"
                    + "   return {'status' : 0, 'results' : new Uint8Array([signals.length])}; \n"
                    + "}";
    private final IsolateSettings mIsolateSettings =
            IsolateSettings.builder()
                    .setMaxHeapSizeBytes(50000)
                    .setIsolateConsoleMessageInLogsEnabled(true)
                    .build();

    @Rule(order = 6)
    public final SupportedByConditionRule webViewSupportsJSSandbox =
            WebViewSupportUtil.createJSSandboxAvailableRule(
                    ApplicationProvider.getApplicationContext());

    private JSScriptEngine mJSScriptEngine;

    @Before
    public void setUp() {
        this.mJSScriptEngine = JSScriptEngine.getInstance(LoggerFactory.getFledgeLogger());
    }

    @Test
    public void test_getDriverLogicWithArguments_noArgumentsAndOGImpl_isNotValidSyntax()
            throws JSONException {
        getDriverLogicWithArguments_noArguments_isNotValidSyntax(
                new ProtectedSignalsArgumentImpl());
    }

    @Test
    public void test_getDriverLogicWithArguments_noArgumentsAndFastImpl_isNotValidSyntax()
            throws JSONException {
        getDriverLogicWithArguments_noArguments_isNotValidSyntax(
                new ProtectedSignalsArgumentFastImpl());
    }

    private void getDriverLogicWithArguments_noArguments_isNotValidSyntax(
            ProtectedSignalsArgument protectedSignalsArgument) throws JSONException {
        Map<String, List<ProtectedSignal>> signals = Map.of();

        String generatedCode =
                SignalsDriverLogicGenerator.getDriverLogicWithArguments(
                        signals, 1, protectedSignalsArgument);

        assertThrows(
                ExecutionException.class,
                () ->
                        mJSScriptEngine
                                .evaluate(
                                        ENCODE_SIGNALS_STUB + "\n" + generatedCode,
                                        List.of(),
                                        mIsolateSettings,
                                        new NoOpRetryStrategyImpl())
                                .get(JS_SCRIPT_ENGINE_TIMEOUT_SEC, TimeUnit.SECONDS));
    }

    @Test
    public void test_getDriverLogicWithArguments_happyPathAndOGImpl_isValidSyntax()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        getDriverLogicWithArguments_happyPath_isValidSyntax(new ProtectedSignalsArgumentImpl());
    }

    @Test
    public void test_getDriverLogicWithArguments_happyPathAndFastImpl_isValidSyntax()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        getDriverLogicWithArguments_happyPath_isValidSyntax(new ProtectedSignalsArgumentFastImpl());
    }

    private void getDriverLogicWithArguments_happyPath_isValidSyntax(
            ProtectedSignalsArgument protectedSignalsArgument)
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        List<String> seeds = List.of("SignalsA", "SignalsB");
        Map<String, List<ProtectedSignal>> rawSignalsMap =
                ProtectedSignalsFixture.generateMapOfProtectedSignals(seeds, 20);

        String generatedCode =
                SignalsDriverLogicGenerator.getDriverLogicWithArguments(
                        rawSignalsMap, MAX_SIZE_IN_BYTES, protectedSignalsArgument);
        String output =
                mJSScriptEngine
                        .evaluate(
                                ENCODE_SIGNALS_STUB + "\n" + generatedCode,
                                mIsolateSettings,
                                new NoOpRetryStrategyImpl())
                        .get(JS_SCRIPT_ENGINE_TIMEOUT_SEC, TimeUnit.SECONDS);

        // Check that the output is valid JSON.
        new JSONObject(output);
    }
}
