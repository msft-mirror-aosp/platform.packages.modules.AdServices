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

import static com.android.adservices.service.common.ScriptEngineConstants.JS_EXECUTION_STATUS_UNSUCCESSFUL;
import static com.android.adservices.service.common.ScriptEngineConstants.JS_SCRIPT_STATUS_SUCCESS;
import static com.android.adservices.service.common.ScriptEngineConstants.RESULTS_FIELD_NAME;
import static com.android.adservices.service.common.ScriptEngineConstants.STATUS_FIELD_NAME;
import static com.android.adservices.service.js.JSScriptArgument.numericArg;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_OTHER_FAILURE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_OUTPUT_NON_ZERO_RESULT;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_OUTPUT_SEMANTIC_ERROR;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_OUTPUT_SYNTAX_ERROR;

import android.annotation.NonNull;
import android.content.Context;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.common.RetryStrategy;
import com.android.adservices.service.js.IsolateSettings;
import com.android.adservices.service.js.JSScriptArgument;
import com.android.adservices.service.js.JSScriptEngine;
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

public class SignalsScriptEngine {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    public static final String ENCODE_SIGNALS_DRIVER_FUNCTION_NAME = "encodeSignalsDriver";
    public static final String ENCODE_SIGNALS_FUNCTION_NAME = "encodeSignals";
    public static final String SIGNALS_ARG_NAME = "__rb_protected_signals";
    public static final String MAX_SIZE_BYTES_ARG_NAME = "__rb_max_size_bytes";

    /**
     * Minified JS that un-marshals signals to map their keys and values to byte[]. Should be
     * invoked by the driver script before passing the signals to encodeSignals() function.
     *
     * <pre>
     * function decodeHex(hexString) {
     *     hexString = hexString.replace(/\\s/g, '');
     *     if (hexString.length % 2 !== 0) {
     *         throw new Error('hex must have even chars.');
     *     }
     *     const byteArray = new Uint8Array(hexString.length / 2);
     *     for (let i = 0; i < hexString.length; i += 2) {
     *         const byteValue = parseInt(hexString.substr(i, 2), 16);
     *         byteArray[i / 2] = byteValue;
     *     }
     *
     *     return byteArray;
     * }
     *
     * function unmarshal(signalObjects){
     *    const result=new Map();
     *    signalObjects.forEach(
     *      (signalGroup=> {
     *        for(const key in signalGroup){
     *          const signal_key = decodeHex(key)
     *          const decodedValues= signalGroup[key].map((entry=>({
     *                 signal_value: decodeHex(entry.val),
     *                 creation_time: entry.time,
     *                 package_name: entry.app.trim()
     *               })));
     *
     *           result.set(signal_key, decodedValues)
     *         }
     *        })
     *     )
     *   return result;
     * }
     * </pre>
     */
    private static final String UNMARSHAL_SIGNALS_JS =
            "function decodeHex(e){if((e=e.replace(/\\\\s/g,\"\")).length%2!=0)throw Error(\"hex "
                    + "must have even chars.\");let t=new Uint8Array(e.length/2);for(let n=0;n<e"
                    + ".length;n+=2){let a=parseInt(e.substr(n,2),16);t[n/2]=a}return t}function "
                    + "unmarshal(e){let t=new Map;return e.forEach(e=>{for(let n in e){let a=e[n]."
                    + "map(e=>({signal_value:decodeHex(e.val),creation_time:e.time,package_name:e."
                    + "app.trim()}));t.set(decodeHex(n),a)}}),t}";

    /**
     * Function used to marshal the Uint8Array returned by encodeSignals into an hex string.
     *
     * <pre>
     * function encodeHex(signals) {
     *    return signals.reduce(
     *      (output, byte) => output + byte.toString(16).padStart(2, '0'), ''
     *    );
     * };
     * </pre>
     */
    private static final String MARSHALL_ENCODED_SIGNALS_JS =
            "function encodeHex(e){return e.reduce((e,n)=>e+n.toString(16).padStart(2,"
                    + " '0'),\"\");}";

    /**
     * This JS wraps around encodeSignals() logic. Un-marshals the signals and then invokes the
     * encodeSignals() script.
     */
    private static final String ENCODE_SIGNALS_DRIVER_JS =
            "function "
                    + ENCODE_SIGNALS_DRIVER_FUNCTION_NAME
                    + "(signals, maxSize) {\n"
                    + "  const unmarshalledSignals = unmarshal(signals);\n"
                    + "\n"
                    + "  "
                    + "  let encodeResult = "
                    + ENCODE_SIGNALS_FUNCTION_NAME
                    + "(unmarshalledSignals, maxSize);\n"
                    + "   return { 'status': encodeResult.status, "
                    + "'results': encodeHex(encodeResult.results) };\n"
                    + "}\n"
                    + "\n"
                    + UNMARSHAL_SIGNALS_JS
                    + "\n"
                    + MARSHALL_ENCODED_SIGNALS_JS
                    + "\n";

    private final JSScriptEngine mJsEngine;
    // Used for the Futures.transform calls to compose futures.
    private final Executor mExecutor = MoreExecutors.directExecutor();
    private final Supplier<Boolean> mEnforceMaxHeapSizeFeatureSupplier;
    private final Supplier<Long> mMaxHeapSizeBytesSupplier;
    private final RetryStrategy mRetryStrategy;
    private final Supplier<Boolean> mIsolateConsoleMessageInLogsEnabled;

    public SignalsScriptEngine(
            Context context,
            Supplier<Boolean> enforceMaxHeapSizeFeatureSupplier,
            Supplier<Long> maxHeapSizeBytesSupplier,
            RetryStrategy retryStrategy,
            Supplier<Boolean> isolateConsoleMessageInLogsEnabled) {
        mEnforceMaxHeapSizeFeatureSupplier = enforceMaxHeapSizeFeatureSupplier;
        mMaxHeapSizeBytesSupplier = maxHeapSizeBytesSupplier;
        mRetryStrategy = retryStrategy;
        mIsolateConsoleMessageInLogsEnabled = isolateConsoleMessageInLogsEnabled;
        mJsEngine = JSScriptEngine.getInstance(context, sLogger);
    }

    /**
     * Injects buyer provided encodeSignals() logic into a driver JS. The driver script first
     * un-marshals the signals, which would be marshaled by {@link ProtectedSignalsArgumentUtil},
     * and then invokes the buyer provided encoding logic. The script marshals the Uint8Array
     * returned by encodeSignals as HEX string and converts it into the byte[] returned by this
     * method.
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
            @NonNull EncodingExecutionLogHelper logHelper)
            throws IllegalStateException {

        logHelper.startClock();
        if (rawSignals.isEmpty()) {
            logHelper.setStatus(JS_RUN_STATUS_OTHER_FAILURE);
            logHelper.finish();
            return Futures.immediateFuture(new byte[0]);
        }

        String combinedDriverAndEncodingLogic = ENCODE_SIGNALS_DRIVER_JS + encodingLogic;
        ImmutableList<JSScriptArgument> args;
        try {
            args =
                    ImmutableList.<JSScriptArgument>builder()
                            .add(
                                    ProtectedSignalsArgumentUtil.asScriptArgument(
                                            SIGNALS_ARG_NAME, rawSignals))
                            .add(numericArg(MAX_SIZE_BYTES_ARG_NAME, maxSize))
                            .build();
        } catch (JSONException e) {
            logHelper.setStatus(JS_RUN_STATUS_OTHER_FAILURE);
            logHelper.finish();
            throw new IllegalStateException("Exception processing JSON version of signals");
        }

        return FluentFuture.from(
                        mJsEngine.evaluate(
                                combinedDriverAndEncodingLogic,
                                args,
                                ENCODE_SIGNALS_DRIVER_FUNCTION_NAME,
                                buildIsolateSettings(
                                        mEnforceMaxHeapSizeFeatureSupplier,
                                        mMaxHeapSizeBytesSupplier,
                                        mIsolateConsoleMessageInLogsEnabled),
                                mRetryStrategy))
                .transform(
                        encodingResult -> handleEncodingOutput(encodingResult, logHelper),
                        mExecutor);
    }

    @VisibleForTesting
    byte[] handleEncodingOutput(String encodingScriptResult, EncodingExecutionLogHelper logHelper)
            throws IllegalStateException {

        if (encodingScriptResult == null || encodingScriptResult.isEmpty()) {
            logHelper.setStatus(JS_RUN_STATUS_OUTPUT_SYNTAX_ERROR);
            logHelper.finish();
            throw new IllegalStateException(
                    "The encoding script either doesn't contain the required function or the"
                            + " function returned null");
        }

        try {
            JSONObject jsonResult = new JSONObject(encodingScriptResult);
            int status = jsonResult.getInt(STATUS_FIELD_NAME);
            String result = jsonResult.getString(RESULTS_FIELD_NAME);

            if (status != JS_SCRIPT_STATUS_SUCCESS || result == null) {
                String errorMsg = String.format(JS_EXECUTION_STATUS_UNSUCCESSFUL, status, result);
                sLogger.v(errorMsg);
                logHelper.setStatus(JS_RUN_STATUS_OUTPUT_NON_ZERO_RESULT);
                logHelper.finish();
                throw new IllegalStateException(errorMsg);
            }

            try {
                return decodeHexString(result);
            } catch (IllegalArgumentException e) {
                logHelper.setStatus(JS_RUN_STATUS_OUTPUT_SEMANTIC_ERROR);
                logHelper.finish();
                throw new IllegalStateException("Malformed encoded payload.", e);
            }
        } catch (JSONException e) {
            logHelper.setStatus(JS_RUN_STATUS_OUTPUT_SYNTAX_ERROR);
            logHelper.finish();
            sLogger.e("Could not extract the Encoded Payload result");
            throw new IllegalStateException("Exception processing result from encoding");
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
            Supplier<Boolean> enforceMaxHeapSizeFeatureSupplier,
            Supplier<Long> maxHeapSizeBytesSupplier,
            Supplier<Boolean> isolateConsoleMessageInLogsEnabled) {
        return IsolateSettings.builder()
                .setEnforceMaxHeapSizeFeature(enforceMaxHeapSizeFeatureSupplier.get())
                .setMaxHeapSizeBytes(maxHeapSizeBytesSupplier.get())
                .setIsolateConsoleMessageInLogsEnabled(isolateConsoleMessageInLogsEnabled.get())
                .build();
    }
}
