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

package com.android.adservices.service.adselection.debug;

import androidx.annotation.NonNull;

/** Helper class which wraps bidding and scoring JS with forDebuggingOnly.* APIs. */
public abstract class DebugReportingScriptStrategy {
    public static final String RESET_SCRIPT =
            "forDebuggingOnly.__rb_debug_reporting_win_uri = '';\n"
                    + "forDebuggingOnly.__rb_debug_reporting_loss_uri = '';\n";
    public static final String WIN_URI_GLOBAL_VARIABLE =
            "forDebuggingOnly.__rb_debug_reporting_win_uri";
    public static final String LOSS_URI_GLOBAL_VARIABLE =
            "forDebuggingOnly.__rb_debug_reporting_loss_uri";
    public static final String HEADER_SCRIPT = "let forDebuggingOnly = {};\n" + RESET_SCRIPT;

    /** Wraps a given generate bid javascript v3 code with debug reporting wrapper. */
    public abstract String wrapGenerateBidsV3Js(@NonNull String jsScript);

    /** Wraps a given iterative bidding or scoring javascript code with debug reporting wrapper. */
    public abstract String wrapIterativeJs(@NonNull String jsScript);
}
