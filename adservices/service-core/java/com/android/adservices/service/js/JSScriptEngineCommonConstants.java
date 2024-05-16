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

/** Common constants for script engines. */
public final class JSScriptEngineCommonConstants {
    /** Named field for the result returned from the script. */
    public static final String RESULTS_FIELD_NAME = "results";

    /** Named field for the status returned from the script. */
    public static final String STATUS_FIELD_NAME = "status";

    /** Failure message to be returned from JS. */
    public static final String JS_EXECUTION_STATUS_UNSUCCESSFUL =
            "Outcome selection script failed with status '%s' or returned unexpected result '%s'";

    /** Failure message to be returned from JS. */
    public static final String JS_EXECUTION_RESULT_INVALID =
            "Result of outcome selection script result is invalid: %s";

    /** Named WASM module argument. */
    public static final String SCRIPT_ARGUMENT_NAME_IGNORED = "ignored";

    /** Success status code to be returned from JS. Other status codes are feature dependent. */
    public static final int JS_SCRIPT_STATUS_SUCCESS = 0;

    /** Named WASM module argument. */
    public static final String WASM_MODULE_ARG_NAME = "wasmModule";

    /** Named WASM bytes argument. */
    public static final String WASM_MODULE_BYTES_ID = "__wasmModuleBytes";

    /** Named entry point for Rubidium scripts. */
    public static final String ENTRY_POINT_FUNC_NAME = "__rb_entry_point";
}
