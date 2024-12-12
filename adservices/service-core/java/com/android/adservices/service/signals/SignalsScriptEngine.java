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

import static com.android.adservices.service.js.JSScriptEngineCommonConstants.JS_EXECUTION_STATUS_UNSUCCESSFUL;
import static com.android.adservices.service.js.JSScriptEngineCommonConstants.JS_SCRIPT_STATUS_SUCCESS;
import static com.android.adservices.service.js.JSScriptEngineCommonConstants.RESULTS_FIELD_NAME;
import static com.android.adservices.service.js.JSScriptEngineCommonConstants.STATUS_FIELD_NAME;
import static com.android.adservices.service.signals.SignalsDriverLogicGenerator.ENCODE_SIGNALS_DRIVER_FUNCTION_NAME;
import static com.android.adservices.service.signals.SignalsDriverLogicGenerator.getCombinedDriverAndEncodingLogic;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_EMPTY_SCRIPT_RESULT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_JS_EXECUTION_STATUS_UNSUCCESSFUL;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_MALFORMED_ENCODED_PAYLOAD;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_NULL_ENCODING_SCRIPT_RESULT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_PROCESSING_JSON_VERSION_OF_SIGNALS_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_PROCESS_ENCODED_PAYLOAD_RESULT_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_OTHER_FAILURE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_OUTPUT_NON_ZERO_RESULT;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_OUTPUT_SEMANTIC_ERROR;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_OUTPUT_SYNTAX_ERROR;

import android.annotation.NonNull;
import android.os.Trace;

import com.android.adservices.LoggerFactory;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.common.RetryStrategy;
import com.android.adservices.service.js.IsolateSettings;
import com.android.adservices.service.js.JSScriptArgument;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.service.profiling.Tracing;
import com.android.adservices.service.stats.pas.EncodingExecutionLogHelper;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class SignalsScriptEngine {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    private final JSScriptEngine mJsEngine;
    // Used for the Futures.transform calls to compose futures.
    private final Executor mExecutor = MoreExecutors.directExecutor();
    private final Supplier<Long> mMaxHeapSizeBytesSupplier;
    private final RetryStrategy mRetryStrategy;
    private final Supplier<Boolean> mIsolateConsoleMessageInLogsEnabled;

    public SignalsScriptEngine(
            Supplier<Long> maxHeapSizeBytesSupplier,
            RetryStrategy retryStrategy,
            Supplier<Boolean> isolateConsoleMessageInLogsEnabled) {
        mMaxHeapSizeBytesSupplier = maxHeapSizeBytesSupplier;
        mRetryStrategy = retryStrategy;
        mIsolateConsoleMessageInLogsEnabled = isolateConsoleMessageInLogsEnabled;
        mJsEngine = JSScriptEngine.getInstance();
    }

    /**
     * Injects buyer provided encodeSignals() logic into a driver JS. The driver script first
     * un-marshals the signals, which would be marshaled by {@link ProtectedSignalsArgument}, and
     * then invokes the buyer provided encoding logic. The script marshals the Uint8Array returned
     * by encodeSignals as HEX string and converts it into the byte[] returned by this method.
     *
     * @param encodingLogic The buyer provided encoding logic
     * @param rawSignals The signals fetched from buyer delegation
     * @param maxSize maxSize of payload generated by the buyer
     * @param logHelper logHelper to log metrics for method
     * @throws IllegalStateException if the JSON created from raw Signals is invalid
     */
    public ListenableFuture<byte[]> encodeSignals(
            @NonNull String encodingLogic,
            @NonNull Map<String, List<ProtectedSignal>> rawSignals,
            int maxSize,
            @NonNull EncodingExecutionLogHelper logHelper,
            ProtectedSignalsArgument protectedSignalsArgument)
            throws IllegalStateException {
        int traceCookie = Tracing.beginAsyncSection(Tracing.ENCODE_SIGNALS);

        logHelper.startClock();
        if (rawSignals.isEmpty()) {
            logHelper.setStatus(JS_RUN_STATUS_OTHER_FAILURE);
            logHelper.finish();
            Tracing.endAsyncSection(Tracing.ENCODE_SIGNALS, traceCookie);
            return Futures.immediateFuture(new byte[0]);
        }

        String combinedDriverAndEncodingLogic = getCombinedDriverAndEncodingLogic(encodingLogic);
        ImmutableList<JSScriptArgument> args;
        try {
            Trace.beginSection(Tracing.ENCODE_SIGNALS + ":convert signals to JS");
            args =
                    protectedSignalsArgument.getArgumentsFromRawSignalsAndMaxSize(
                            rawSignals, maxSize);
        } catch (JSONException e) {
            logHelper.setStatus(JS_RUN_STATUS_OTHER_FAILURE);
            logHelper.finish();
            Tracing.endAsyncSection(Tracing.ENCODE_SIGNALS, traceCookie);
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_PROCESSING_JSON_VERSION_OF_SIGNALS_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS);
            throw new IllegalStateException("Exception processing JSON version of signals", e);
        } finally {
            Trace.endSection();
        }

        return FluentFuture.from(
                        mJsEngine.evaluate(
                                combinedDriverAndEncodingLogic,
                                args,
                                ENCODE_SIGNALS_DRIVER_FUNCTION_NAME,
                                buildIsolateSettings(
                                        mMaxHeapSizeBytesSupplier,
                                        mIsolateConsoleMessageInLogsEnabled),
                                mRetryStrategy))
                .transform(
                        encodingResult -> {
                            byte[] result = handleEncodingOutput(encodingResult, logHelper);
                            // Sets the encoded signal size in bytes.
                            logHelper.setEncodedSignalSize(result.length);
                            Tracing.endAsyncSection(Tracing.ENCODE_SIGNALS, traceCookie);
                            return result;
                        },
                        mExecutor);
    }

    @VisibleForTesting
    byte[] handleEncodingOutput(String encodingScriptResult, EncodingExecutionLogHelper logHelper)
            throws IllegalStateException {
        if (encodingScriptResult == null || encodingScriptResult.isEmpty()) {
            logHelper.setStatus(JS_RUN_STATUS_OUTPUT_SYNTAX_ERROR);
            logHelper.finish();
            if (encodingScriptResult == null) {
                ErrorLogUtil.e(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_NULL_ENCODING_SCRIPT_RESULT,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS);
            } else if (encodingScriptResult.isEmpty()) {
                ErrorLogUtil.e(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_EMPTY_SCRIPT_RESULT,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS);
            }
            throw new IllegalStateException(
                    "The encoding script either doesn't contain the required function or the"
                            + " function returned null");
        }

        try {
            Trace.beginSection(Tracing.CONVERT_JS_OUTPUT_TO_BINARY);

            JSONObject jsonResult = new JSONObject(encodingScriptResult);
            int status = jsonResult.getInt(STATUS_FIELD_NAME);
            String result = jsonResult.getString(RESULTS_FIELD_NAME);

            if (status != JS_SCRIPT_STATUS_SUCCESS || result == null) {
                String errorMsg = String.format(JS_EXECUTION_STATUS_UNSUCCESSFUL, status, result);
                sLogger.v(errorMsg);
                logHelper.setStatus(JS_RUN_STATUS_OUTPUT_NON_ZERO_RESULT);
                logHelper.finish();
                ErrorLogUtil.e(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_JS_EXECUTION_STATUS_UNSUCCESSFUL,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS);
                throw new IllegalStateException(errorMsg);
            }

            try {
                return decodeHexString(result);
            } catch (IllegalArgumentException e) {
                logHelper.setStatus(JS_RUN_STATUS_OUTPUT_SEMANTIC_ERROR);
                logHelper.finish();
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_MALFORMED_ENCODED_PAYLOAD,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS);
                throw new IllegalStateException("Malformed encoded payload.", e);
            }
        } catch (JSONException e) {
            logHelper.setStatus(JS_RUN_STATUS_OUTPUT_SYNTAX_ERROR);
            logHelper.finish();
            sLogger.e("Could not extract the Encoded Payload result");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_PROCESS_ENCODED_PAYLOAD_RESULT_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS);
            throw new IllegalStateException("Exception processing result from encoding");
        } finally {
            Trace.endSection();
        }
    }

    private byte[] decodeHexString(String hexString) {
        Preconditions.checkArgument(
                hexString.length() % 2 == 0, "Expected an hex string but the arg length is odd");

        byte[] result = new byte[hexString.length() / 2];
        for (int i = 0; i < hexString.length() / 2; i++) {
            result[i] = byteValue(hexString.charAt(2 * i), hexString.charAt(2 * i + 1));
        }
        return result;
    }

    private byte byteValue(char highNibble, char lowNibble) {
        int high = Character.digit(highNibble, 16);
        Preconditions.checkArgument(high >= 0, "Invalid value for HEX string char");
        int low = Character.digit(lowNibble, 16);
        Preconditions.checkArgument(low >= 0, "Invalid value for HEX string char");

        return (byte) (high << 4 | low);
    }

    private static IsolateSettings buildIsolateSettings(
            Supplier<Long> maxHeapSizeBytesSupplier,
            Supplier<Boolean> isolateConsoleMessageInLogsEnabled) {
        return IsolateSettings.builder()
                .setMaxHeapSizeBytes(maxHeapSizeBytesSupplier.get())
                .setIsolateConsoleMessageInLogsEnabled(isolateConsoleMessageInLogsEnabled.get())
                .build();
    }
}
