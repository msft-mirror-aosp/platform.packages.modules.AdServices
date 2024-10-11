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

import static com.android.adservices.service.signals.ProtectedSignalsFixture.getHexString;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_EMPTY_SCRIPT_RESULT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_JS_EXECUTION_STATUS_UNSUCCESSFUL;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_PROCESS_ENCODED_PAYLOAD_RESULT_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_OTHER_FAILURE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_OUTPUT_NON_ZERO_RESULT;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_OUTPUT_SYNTAX_ERROR;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.WebViewSupportUtil;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.service.common.NoOpRetryStrategyImpl;
import com.android.adservices.service.common.RetryStrategy;
import com.android.adservices.service.js.IsolateSettings;
import com.android.adservices.service.stats.pas.EncodingExecutionLogHelper;
import com.android.adservices.shared.testing.SupportedByConditionRule;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SetErrorLogUtilDefaultParams(
        throwable = ExpectErrorLogUtilWithExceptionCall.Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS)
public final class SignalsScriptEngineTest extends AdServicesExtendedMockitoTestCase {
    private static final boolean ISOLATE_CONSOLE_MESSAGE_IN_LOGS_ENABLED = true;
    private static final IsolateSettings ISOLATE_SETTINGS =
            IsolateSettings.forMaxHeapSizeEnforcementEnabled(
                    ISOLATE_CONSOLE_MESSAGE_IN_LOGS_ENABLED);

    private SignalsScriptEngine mSignalsScriptEngine;

    @Mock private EncodingExecutionLogHelper mEncodingExecutionLoggerMock;

    // Every test in this class requires that the JS Sandbox be available. The JS Sandbox
    // availability depends on an external component (the system webview) being higher than a
    // certain minimum version.
    @Rule(order = 11)
    public final SupportedByConditionRule webViewSupportsJSSandbox =
            WebViewSupportUtil.createJSSandboxAvailableRule(
                    ApplicationProvider.getApplicationContext());

    @Before
    public void setUp() {
        mSignalsScriptEngine =
                createSignalsScriptEngine(ISOLATE_SETTINGS, new NoOpRetryStrategyImpl());
    }

    @Test
    public void test_encodeSignals_executesJSScriptWithFastImpl_returnsJSScriptObject()
            throws ExecutionException, InterruptedException, TimeoutException {
        encodeSignals_executesJSScript_returnsJSScriptObject(
                new ProtectedSignalsArgumentFastImpl());
    }

    @Test
    public void test_encodeSignals_executesJSScriptWithOGImpl_returnsJSScriptObject()
            throws ExecutionException, InterruptedException, TimeoutException {
        encodeSignals_executesJSScript_returnsJSScriptObject(new ProtectedSignalsArgumentImpl());
    }

    private void encodeSignals_executesJSScript_returnsJSScriptObject(
            ProtectedSignalsArgument protectedSignalsArgument)
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
                        encodeSignalsJS,
                        rawSignalsMap,
                        10,
                        mEncodingExecutionLoggerMock,
                        protectedSignalsArgument);
        byte[] result = jsOutcome.get(5, TimeUnit.SECONDS);

        assertArrayEquals(
                "The result expected is the size of keys in the input signals",
                expectedResult,
                result);
    }

    @Test
    public void test_encodeSignals_signalsAreRepresentedAsAMapInJSOGImpl_returnsSuccess()
            throws ExecutionException, InterruptedException, TimeoutException {
        Map<String, List<ProtectedSignal>> rawSignalsMap = new HashMap<>();
        rawSignalsMap.put(
                Base64.getEncoder().encodeToString(new byte[] {0x00}),
                List.of(
                        ProtectedSignalsFixture.generateBase64ProtectedSignal(
                                "", new byte[] {(byte) 0xA0})));

        rawSignalsMap.put(
                Base64.getEncoder().encodeToString(new byte[] {0x01}),
                List.of(
                        ProtectedSignalsFixture.generateBase64ProtectedSignal(
                                "", new byte[] {(byte) 0xA1}),
                        ProtectedSignalsFixture.generateBase64ProtectedSignal(
                                "", new byte[] {(byte) 0xA2})));

        ProtectedSignalsArgument protectedSignalsArgument = new ProtectedSignalsArgumentImpl();

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
                        encodeSignalsJS,
                        rawSignalsMap,
                        10,
                        mEncodingExecutionLoggerMock,
                        protectedSignalsArgument);
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
    public void test_encodeSignals_signalsAreRepresentedAsAMapInJSFastImpl_returnsSuccess()
            throws ExecutionException, InterruptedException, TimeoutException {
        Map<String, List<ProtectedSignal>> rawSignalsMap = new HashMap<>();
        rawSignalsMap.put(
                getHexString(new byte[] {0x00}),
                List.of(
                        ProtectedSignalsFixture.generateBase64ProtectedSignal(
                                "", new byte[] {(byte) 0xA0})));

        rawSignalsMap.put(
                getHexString(new byte[] {0x01}),
                List.of(
                        ProtectedSignalsFixture.generateBase64ProtectedSignal(
                                "", new byte[] {(byte) 0xA1}),
                        ProtectedSignalsFixture.generateBase64ProtectedSignal(
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

        ProtectedSignalsArgument protectedSignalsArgument = new ProtectedSignalsArgumentFastImpl();
        ListenableFuture<byte[]> jsOutcome =
                mSignalsScriptEngine.encodeSignals(
                        encodeSignalsJS,
                        rawSignalsMap,
                        10,
                        mEncodingExecutionLoggerMock,
                        protectedSignalsArgument);
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
    public void test_encodeSignals_signalsAreAMapInJSAndTimestampIsCorrectOGImpl_returnsSuccess()
            throws ExecutionException, InterruptedException, TimeoutException {
        encodeSignalsSignalsAreAMapInJS_timestampIsCorrect(new ProtectedSignalsArgumentImpl());
    }

    @Test
    public void test_encodeSignals_signalsAreAMapInJSAndTimestampIsCorrectFastImpl_returnsSuccess()
            throws ExecutionException, InterruptedException, TimeoutException {
        encodeSignalsSignalsAreAMapInJS_timestampIsCorrect(new ProtectedSignalsArgumentFastImpl());
    }

    private void encodeSignalsSignalsAreAMapInJS_timestampIsCorrect(
            ProtectedSignalsArgument protectedSignalsArgument)
            throws ExecutionException, InterruptedException, TimeoutException {
        Map<String, List<ProtectedSignal>> rawSignalsMap = new HashMap<>();
        ProtectedSignal signalValue =
                ProtectedSignalsFixture.generateBase64ProtectedSignal("", new byte[] {(byte) 0xA0});
        rawSignalsMap.put(
                Base64.getEncoder().encodeToString(new byte[] {0x00}), List.of(signalValue));

        String encodeSignalsJS =
                String.format(
                        Locale.ENGLISH,
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
                        encodeSignalsJS,
                        rawSignalsMap,
                        10,
                        mEncodingExecutionLoggerMock,
                        protectedSignalsArgument);
        byte[] result = jsOutcome.get(5, TimeUnit.SECONDS);

        assertArrayEquals(
                "Expected a single byte response with value 0 to indicate success "
                        + "in the JS validations",
                new byte[] {0},
                result);
    }

    @Test
    public void test_encodeSignals_signalsAreAMapInJSAndPackageNameIsCorrectOGImpl_returnsSuccess()
            throws ExecutionException, InterruptedException, TimeoutException {
        encodeSignalsSignalsAreAMapInJS_packageNameIsCorrect(new ProtectedSignalsArgumentImpl());
    }

    @Test
    public void
            test_encodeSignals_signalsAreAMapInJSAndPackageNameIsCorrectFastImpl_returnsSuccess()
                    throws ExecutionException, InterruptedException, TimeoutException {
        encodeSignalsSignalsAreAMapInJS_packageNameIsCorrect(
                new ProtectedSignalsArgumentFastImpl());
    }

    private void encodeSignalsSignalsAreAMapInJS_packageNameIsCorrect(
            ProtectedSignalsArgument protectedSignalsArgument)
            throws ExecutionException, InterruptedException, TimeoutException {
        Map<String, List<ProtectedSignal>> rawSignalsMap = new HashMap<>();
        ProtectedSignal signalValue =
                ProtectedSignalsFixture.generateBase64ProtectedSignal("", new byte[] {(byte) 0xA0});
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
                        encodeSignalsJS,
                        rawSignalsMap,
                        10,
                        mEncodingExecutionLoggerMock,
                        protectedSignalsArgument);
        byte[] result = jsOutcome.get(5, TimeUnit.SECONDS);

        assertArrayEquals(
                "Expected a single byte response with value 0 to indicate success "
                        + "in the JS validations",
                new byte[] {0},
                result);
    }

    @Test
    public void test_encodeSignals_emptySignalsWithOGImpl_returnsEmptyEncodedSignals()
            throws ExecutionException, InterruptedException, TimeoutException {
        encodeEmptySignals(new ProtectedSignalsArgumentImpl());
    }

    @Test
    public void test_encodeSignals_emptySignalsWithFastImpl_returnsEmptyEncodedSignals()
            throws ExecutionException, InterruptedException, TimeoutException {
        encodeEmptySignals(new ProtectedSignalsArgumentFastImpl());
    }

    private void encodeEmptySignals(ProtectedSignalsArgument protectedSignalsArgument)
            throws ExecutionException, InterruptedException, TimeoutException {
        String encodeSignalsJS =
                "function encodeSignals(signals, maxSize) {\n"
                        + "    return {'status' : 0, 'results' : new Uint8Array()};\n"
                        + "}\n";
        ListenableFuture<byte[]> jsOutcome =
                mSignalsScriptEngine.encodeSignals(
                        encodeSignalsJS,
                        Collections.EMPTY_MAP,
                        10,
                        mEncodingExecutionLoggerMock,
                        protectedSignalsArgument);
        byte[] result = jsOutcome.get(5, TimeUnit.SECONDS);
        verify(mEncodingExecutionLoggerMock).startClock();
        verify(mEncodingExecutionLoggerMock).setStatus(JS_RUN_STATUS_OTHER_FAILURE);
        verify(mEncodingExecutionLoggerMock).finish();

        Assert.assertTrue("The result should have been empty", result.length == 0);
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_EMPTY_SCRIPT_RESULT)
    public void test_handleEncodingOutput_emptyOutput_throwsException() {
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
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_JS_EXECUTION_STATUS_UNSUCCESSFUL)
    public void test_handleEncodingOutput_failedStatus_throwsException() {
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
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_PROCESS_ENCODED_PAYLOAD_RESULT_FAILURE)
    public void test_handleEncodingOutput_missingResult_throwsException() {
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
                isolateSettings::getMaxHeapSizeBytes,
                retryStrategy,
                isolateSettings::getIsolateConsoleMessageInLogsEnabled);
    }
}
