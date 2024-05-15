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

package com.android.adservices.service.js;

import static com.android.adservices.service.js.JSScriptEngineCommonConstants.WASM_MODULE_ARG_NAME;
import static com.android.adservices.service.js.JSScriptEngineCommonConstants.WASM_MODULE_BYTES_ID;

import android.annotation.NonNull;

import java.util.List;
import java.util.stream.Collectors;

/** Helper method for generating code. */
public final class JSScriptEngineCommonCodeGenerator {

    /**
     * @return The JS code for the definition an anonymous function containing the declaration of
     *     the value of {@code args} and the invocation of the given {@code entryFunctionName}. If
     *     the {@code addWasmBinary} parameter is true, the target function is expected to accept an
     *     extra final parameter 'wasmModule' of type {@code WebAssembly.Module} and the method will
     *     return a promise.
     */
    @NonNull
    public static String generateEntryPointCallingCode(
            @NonNull List<JSScriptArgument> args,
            @NonNull String entryFunctionName,
            boolean addWasmBinary) {
        StringBuilder resultBuilder = new StringBuilder("(function() {\n");
        // Declare args as constant inside this function closure to avoid any direct access by
        // the functions in the script we are calling.
        for (JSScriptArgument arg : args) {
            // Avoiding to use addJavaScriptInterface because too expensive, just
            // declaring the string parameter as part of the script.
            resultBuilder.append(arg.variableDeclaration());
            resultBuilder.append("\n");
        }

        String argumentPassing =
                args.stream().map(JSScriptArgument::name).collect(Collectors.joining(","));
        if (addWasmBinary) {
            argumentPassing += "," + WASM_MODULE_ARG_NAME;
            resultBuilder.append(
                    String.format(
                            "return android.consumeNamedDataAsArrayBuffer(\"%s\")"
                                    + ".then((__value) => {\n"
                                    + " return WebAssembly.compile(__value).then((%s) => {\n",
                            WASM_MODULE_BYTES_ID, WASM_MODULE_ARG_NAME));
        }

        // Call entryFunctionName with the constants just declared as parameters
        resultBuilder.append(
                String.format(
                        "return JSON.stringify(%s(%s));\n", entryFunctionName, argumentPassing));

        if (addWasmBinary) {
            resultBuilder.append("})});\n");
        }
        resultBuilder.append("})();\n");

        return resultBuilder.toString();
    }
}
