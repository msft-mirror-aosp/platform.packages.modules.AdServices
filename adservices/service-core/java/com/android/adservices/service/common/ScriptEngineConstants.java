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

package com.android.adservices.service.common;

/** Common constants for script engines. */
public class ScriptEngineConstants {
    public static final String RESULTS_FIELD_NAME = "results";
    public static final String STATUS_FIELD_NAME = "status";
    public static final String JS_EXECUTION_STATUS_UNSUCCESSFUL =
            "Outcome selection script failed with status '%s' or returned unexpected result '%s'";
    public static final String JS_EXECUTION_RESULT_INVALID =
            "Result of outcome selection script result is invalid: %s";
    // TODO: (b/228094391): Put these common constants in a separate class
    public static final String SCRIPT_ARGUMENT_NAME_IGNORED = "ignored";
    public static final int JS_SCRIPT_STATUS_SUCCESS = 0;
}
