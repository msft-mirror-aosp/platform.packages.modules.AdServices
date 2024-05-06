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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_OTHER_FAILURE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_OUTPUT_NON_ZERO_RESULT;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_OUTPUT_SYNTAX_ERROR;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.common.NoOpRetryStrategyImpl;
import com.android.adservices.service.common.RetryStrategy;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.js.IsolateSettings;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.service.stats.pas.EncodingExecutionLogHelper;
import com.android.adservices.shared.testing.SdkLevelSupportRule;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SignalsScriptEngineTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String TAG = "SignalsScriptEngineTest";
    private static final DevContext DEV_CONTEXT =
            DevContext.builder().setDevOptionsEnabled(true).build();
    private static final IsolateSettings ISOLATE_SETTINGS =
            IsolateSettings.forMaxHeapSizeEnforcementDisabled(DEV_CONTEXT.getDevOptionsEnabled());

    private SignalsScriptEngine mSignalsScriptEngine;

    @Mock private EncodingExecutionLogHelper mEncodingExecutionLoggerMock;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        mSignalsScriptEngine =
                createSignalsScriptEngine(ISOLATE_SETTINGS, new NoOpRetryStrategyImpl());
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testEncodeSignals()
            throws ExecutionException, InterruptedException, TimeoutException {
        List<String> seeds = List.of("SignalsA", "SignalsB");
        Map<String, List<ProtectedSignal>> rawSignalsMap =
                ProtectedSignalsFixture.generateMapOfProtectedSignals(seeds, 20);

        byte[] expectedResult = new byte[] {0x0A, (byte) 0xB1};
        String encodeSignalsJS =
                "function encodeSignals(signals, maxSize) {\n"
                        + "  return {'status': 0, 'results': new Uint8Array([0x0A, 0xB1])};\n"
                        + "}\n";
        ListenableFuture<byte[]> jsOutcome =
                mSignalsScriptEngine.encodeSignals(
                        encodeSignalsJS, rawSignalsMap, 10, mEncodingExecutionLoggerMock);
        byte[] result = jsOutcome.get(5, TimeUnit.SECONDS);

        assertArrayEquals(
                "The result expected is the size of keys in the input signals",
                expectedResult,
                result);
    }

    @Test
    public void testEncodeSignalsSignalsAreRepresentedAsAMapInJS()
            throws ExecutionException, InterruptedException, TimeoutException {
        Map<String, List<ProtectedSignal>> rawSignalsMap = new HashMap<>();
        rawSignalsMap.put(
                Base64.getEncoder().encodeToString(new byte[] {0x00}),
                List.of(
                        ProtectedSignalsFixture.generateDBProtectedSignal(
                                "", new byte[] {(byte) 0xA0})));

        rawSignalsMap.put(
                Base64.getEncoder().encodeToString(new byte[] {0x01}),
                List.of(
                        ProtectedSignalsFixture.generateDBProtectedSignal(
                                "", new byte[] {(byte) 0xA1}),
                        ProtectedSignalsFixture.generateDBProtectedSignal(
                                "", new byte[] {(byte) 0xA2})));

        // Assumes keys and values are 1 byte long
        // Generates an array with the following structure
        // [signals.size() signal0.key #signal0.values.size() signal0.values[0] signal0.values[2]
        //                 signal1.key ... ]
        String encodeSignalsJS =
                "function encodeSignals(signals, maxSize) {\n"
                        + "  let result = new Uint8Array(maxSize);\n"
                        + "  // first entry will contain the total size\n"
                        + "  let size = 1;\n"
                        + "  let keys = 0;\n"
                        + "  \n"
                        + "  for (const [key, values] of signals.entries()) {\n"
                        + "    keys++;\n"
                        + "    // Assuming all data are 1 byte only\n"
                        + "    console.log(\"key \" + keys + \" is \" + key)\n"
                        + "    result[size++] = key[0];\n"
                        + "    result[size++] = values.length;\n"
                        + "    for(const value of values) {\n"
                        + "      result[size++] = value.signal_value[0];\n"
                        + "    }\n"
                        + "  }\n"
                        + "  result[0] = keys;\n"
                        + "  \n"
                        + "  return { 'status': 0, 'results': result.subarray(0, size)};\n"
                        + "}\n";
        ListenableFuture<byte[]> jsOutcome =
                mSignalsScriptEngine.encodeSignals(
                        encodeSignalsJS, rawSignalsMap, 10, mEncodingExecutionLoggerMock);
        byte[] result = jsOutcome.get(5, TimeUnit.SECONDS);

        assertEquals(
                "Encoded result has wrong count of signal keys",
                (byte) rawSignalsMap.size(),
                result[0]);
        int offset = 1;
        for (int i = 0; i < rawSignalsMap.size(); i++) {
            byte signalKey = result[offset++];
            assertTrue(signalKey == 0x00 || signalKey == 0x01);
            if (signalKey == 0x00) {
                assertEquals("Wrong signal values length", 0x01, result[offset++]);
                assertEquals("Wrong signal values", (byte) 0xA0, result[offset++]);
            } else {
                assertEquals("Wrong signal values length", 0x02, result[offset++]);
                assertEquals("Wrong signal values", (byte) 0xA1, result[offset++]);
                assertEquals("Wrong signal values", (byte) 0xA2, result[offset++]);
            }
        }
    }

    @Test
    public void testEncodeSignalsSignalsAreRepresentedAsAMapInJS_timestampIsCorrect()
            throws ExecutionException, InterruptedException, TimeoutException {
        Map<String, List<ProtectedSignal>> rawSignalsMap = new HashMap<>();
        ProtectedSignal signalValue =
                ProtectedSignalsFixture.generateDBProtectedSignal("", new byte[] {(byte) 0xA0});
        rawSignalsMap.put(
                Base64.getEncoder().encodeToString(new byte[] {0x00}), List.of(signalValue));

        String encodeSignalsJS =
                String.format(
                        "function encodeSignals(signals, maxSize) {\n"
                                + "  // returning error if the creation time name of the only "
                                + "  // signal is correct\n"
                                + "  if(signals.size != 1) {\n"
                                + "     return { 'status': 0, 'results': new Uint8Array([1]) };\n"
                                + "  }\n"
                                + "  let signalValues = signals.values().next().value;\n"
                                + "  if(signalValues[0].creation_time == %d) {\n"
                                + "     return { 'status': 0, 'results': new Uint8Array([0]) };\n"
                                + "  }\n"
                                + "  return { 'status': 0, 'results': new Uint8Array([2]) };\n"
                                + "}\n",
                        signalValue.getCreationTime().getEpochSecond());
        ListenableFuture<byte[]> jsOutcome =
                mSignalsScriptEngine.encodeSignals(
                        encodeSignalsJS, rawSignalsMap, 10, mEncodingExecutionLoggerMock);
        byte[] result = jsOutcome.get(5, TimeUnit.SECONDS);

        assertArrayEquals(
                "Expected a single byte response with value 0 to indicate success "
                        + "in the JS validations",
                new byte[] {0},
                result);
    }

    @Test
    public void testEncodeSignalsSignalsAreRepresentedAsAMapInJS_packageNameIsCorrect()
            throws ExecutionException, InterruptedException, TimeoutException {
        Map<String, List<ProtectedSignal>> rawSignalsMap = new HashMap<>();
        ProtectedSignal signalValue =
                ProtectedSignalsFixture.generateDBProtectedSignal("", new byte[] {(byte) 0xA0});
        rawSignalsMap.put(
                Base64.getEncoder().encodeToString(new byte[] {0x00}), List.of(signalValue));

        String encodeSignalsJS =
                String.format(
                        "function encodeSignals(signals, maxSize) {\n"
                                + "  // returning error if the package name of the only signal is"
                                + "  // correct\n"
                                + "  if(signals.size != 1) {\n"
                                + "     return { 'status': 0, 'results': new Uint8Array([1]) };\n"
                                + "  }\n"
                                + "  let signalValues = signals.values().next().value;\n"
                                + "  if(signalValues[0].package_name == '%s') {\n"
                                + "     return { 'status': 0, 'results': new Uint8Array([0]) };\n"
                                + "  }\n"
                                + "  return { 'status': 0, 'results': new Uint8Array([2]) };\n"
                                + "}\n",
                        signalValue.getPackageName());
        ListenableFuture<byte[]> jsOutcome =
                mSignalsScriptEngine.encodeSignals(
                        encodeSignalsJS, rawSignalsMap, 10, mEncodingExecutionLoggerMock);
        byte[] result = jsOutcome.get(5, TimeUnit.SECONDS);

        assertArrayEquals(
                "Expected a single byte response with value 0 to indicate success "
                        + "in the JS validations",
                new byte[] {0},
                result);
    }

    @Test
    public void testEncodeEmptySignals()
            throws ExecutionException, InterruptedException, TimeoutException {
        String encodeSignalsJS =
                "function encodeSignals(signals, maxSize) {\n"
                        + "    return {'status' : 0, 'results' : new Uint8Array()};\n"
                        + "}\n";
        ListenableFuture<byte[]> jsOutcome =
                mSignalsScriptEngine.encodeSignals(
                        encodeSignalsJS, Collections.EMPTY_MAP, 10, mEncodingExecutionLoggerMock);
        byte[] result = jsOutcome.get(5, TimeUnit.SECONDS);
        verify(mEncodingExecutionLoggerMock).startClock();
        verify(mEncodingExecutionLoggerMock).setStatus(JS_RUN_STATUS_OTHER_FAILURE);
        verify(mEncodingExecutionLoggerMock).finish();

        Assert.assertTrue("The result should have been empty", result.length == 0);
    }

    @Test
    public void testHandleEncodingEmptyOutput() {
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> {
                            mSignalsScriptEngine.handleEncodingOutput(
                                    "", mEncodingExecutionLoggerMock);
                        });
        assertEquals(
                "The encoding script either doesn't contain the required function or the"
                        + " function returned null",
                exception.getMessage());
        verify(mEncodingExecutionLoggerMock).setStatus(JS_RUN_STATUS_OUTPUT_SYNTAX_ERROR);
        verify(mEncodingExecutionLoggerMock).finish();
    }

    @Test
    public void testHandleEncodingOutputFailedStatus() {
        int status = 1;
        String result = "unused";

        String encodingScriptOutput =
                "  {\"status\": " + status + ", \"results\" : \"" + result + "\" }";
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> {
                            mSignalsScriptEngine.handleEncodingOutput(
                                    encodingScriptOutput, mEncodingExecutionLoggerMock);
                        });
        assertEquals(
                String.format(
                        "Outcome selection script failed with status '%s' or returned unexpected"
                                + " result '%s'",
                        status, result),
                exception.getMessage());
        verify(mEncodingExecutionLoggerMock).setStatus(JS_RUN_STATUS_OUTPUT_NON_ZERO_RESULT);
        verify(mEncodingExecutionLoggerMock).finish();
    }

    @Test
    public void testHandleEncodingOutputMissingResult() {
        int status = 1;
        String result = "unused";

        String encodingScriptOutput =
                "  {\"status\": " + status + ", \"bad_result_key\" : \"" + result + "\" }";
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> {
                            mSignalsScriptEngine.handleEncodingOutput(
                                    encodingScriptOutput, mEncodingExecutionLoggerMock);
                        });
        assertEquals("Exception processing result from encoding", exception.getMessage());
        verify(mEncodingExecutionLoggerMock).setStatus(JS_RUN_STATUS_OUTPUT_SYNTAX_ERROR);
        verify(mEncodingExecutionLoggerMock).finish();
    }

    private SignalsScriptEngine createSignalsScriptEngine(
            IsolateSettings isolateSettings, RetryStrategy retryStrategy) {
        return new SignalsScriptEngine(
                CONTEXT,
                isolateSettings::getEnforceMaxHeapSizeFeature,
                isolateSettings::getMaxHeapSizeBytes,
                retryStrategy,
                DEV_CONTEXT);
    }
}
