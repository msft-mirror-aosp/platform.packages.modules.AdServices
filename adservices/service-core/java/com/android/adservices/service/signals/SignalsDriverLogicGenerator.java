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

import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.js.JSScriptEngineCommonCodeGenerator;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONException;

import java.util.List;
import java.util.Map;

public class SignalsDriverLogicGenerator {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    static final String ENCODE_SIGNALS_DRIVER_FUNCTION_NAME = "encodeSignalsDriver";
    static final String ENCODE_SIGNALS_FUNCTION_NAME = "encodeSignals";
    static final String SIGNALS_ARG_NAME = "__rb_protected_signals";
    static final String MAX_SIZE_BYTES_ARG_NAME = "__rb_max_size_bytes";

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
    @VisibleForTesting
    public static final String ENCODE_SIGNALS_DRIVER_JS =
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

    static String getCombinedDriverAndEncodingLogic(String encodingLogic) {
        return ENCODE_SIGNALS_DRIVER_JS + encodingLogic;
    }

    /**
     * Generate combined driver and encoding logic from given arguments.
     *
     * <p>This generates an alternative version to the internal scripts that the rest of this class
     * is designed for. This specific version is designed to give ad techs an easy way to test their
     * encoding scripts, by providing encoded signals in the same format and about the same wrapper
     * JavaScript as we execute internally.
     *
     * @param rawSignals List of {@link ProtectedSignal} elements to be added to the script.
     * @param maxSizeInBytes The maximum size in bytes to be passed into the script.
     * @return String containing generated JavaScript logic that an ad tech can use.
     * @throws JSONException if the signals could not be properly marshalled into arguments.
     */
    public static String getDriverLogicWithArguments(
            @NonNull Map<String, List<ProtectedSignal>> rawSignals,
            int maxSizeInBytes,
            ProtectedSignalsArgument protectedSignalsArgument)
            throws JSONException {
        return ENCODE_SIGNALS_DRIVER_JS
                + JSScriptEngineCommonCodeGenerator.generateEntryPointCallingCode(
                        protectedSignalsArgument.getArgumentsFromRawSignalsAndMaxSize(
                                rawSignals, maxSizeInBytes),
                        ENCODE_SIGNALS_DRIVER_FUNCTION_NAME,
                        false);
    }
}
