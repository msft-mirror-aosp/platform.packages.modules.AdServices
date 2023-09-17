/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.common;

import android.content.Context;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.js.JSScriptEngine;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class WebViewSupportUtil {
    public static SupportedByConditionRule createJSSandboxAvailableRule() {
        return new SupportedByConditionRule(
                "WebView does not support JS Sandbox",
                JSScriptEngine.AvailabilityChecker::isJSSandboxAvailable);
    }

    public static SupportedByConditionRule createJSSandboxConfigurableHeapSizeRule(
            Context context) {
        Objects.requireNonNull(context);
        return new SupportedByConditionRule(
                "JS Sandbox does not support configurable heap size",
                () -> WebViewSupportUtil.isConfigurableHeapSizeSupported(context));
    }

    private static boolean isConfigurableHeapSizeSupported(Context context)
            throws ExecutionException, InterruptedException, TimeoutException {
        return JSScriptEngine.getInstance(context, LoggerFactory.getFledgeLogger())
                .isConfigurableHeapSizeSupported()
                .get(2, TimeUnit.SECONDS);
    }
}
