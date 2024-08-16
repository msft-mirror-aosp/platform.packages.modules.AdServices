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

package com.android.adservices.service.adselection;

import com.android.adservices.LoggerFactory;
import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.common.WebViewSupportUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.NoOpRetryStrategyImpl;
import com.android.adservices.service.js.IsolateSettings;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.shared.testing.FutureSyncCallback;
import com.android.adservices.shared.testing.SupportedByConditionRule;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;

import com.google.common.util.concurrent.FluentFuture;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RequiresSdkLevelAtLeastS
public class JSScriptEngineE2ETest extends AdServicesMockitoTestCase {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @Rule(order = 15)
    public final SupportedByConditionRule mJSSandboxSupportedRule =
            WebViewSupportUtil.createJSSandboxAvailableRule(mContext);

    @Mock private static LoggerFactory.Logger sMockLogger;
    private JSScriptEngine mJSScriptEngine;

    @Before
    public void setup() throws ExecutionException, InterruptedException, TimeoutException {
        sLogger.v(
                "Checking if the WebView version installed on the device supports console"
                        + " callback");
        Assume.assumeTrue(
                "ConsoleCallback is not supported by the installed WebView version",
                WebViewSupportUtil.isJSIsolateConsoleCallbackAvailable(mSpyContext));
        // Destroy any existing JSScriptEngine and JavascriptSandbox
        sLogger.v("Destroy any existing JSScriptEngine and JavascriptSandbox");
        JSScriptEngine.getInstance(sMockLogger).shutdown().get(1, TimeUnit.SECONDS);
    }

    @After
    public void cleanup() throws ExecutionException, InterruptedException, TimeoutException {
        // Destroy mJSScriptEngine as part of cleanup
        sLogger.v("Destroy mJSScriptEngine as part of cleanup");
        if (mJSScriptEngine != null) {
            mJSScriptEngine.shutdown().get(1, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testJSScriptEngineLogsConsoleLogMessages() throws InterruptedException {
        String scriptWithConsoleLog =
                """
                function test() {
                    console.log("Hello Log");
                    return "Hello Log";
                }""";

        callAndVerifyJSScriptEngine(scriptWithConsoleLog);

        Thread.sleep(1000); // wait for the console callback to happen.
        Mockito.verify(sMockLogger)
                .v("Javascript Console Message: %s", "L <expression>:2:13: Hello Log");
    }

    @Test
    public void testJSScriptEngineLogsConsoleDebugMessages() throws InterruptedException {
        String scriptWithConsoleDebug =
                """
                function test() {
                    console.debug("Hello Debug");
                    return "Hello Debug";
                }""";

        callAndVerifyJSScriptEngine(scriptWithConsoleDebug);

        Thread.sleep(1000); // wait for the console callback to happen.
        Mockito.verify(sMockLogger)
                .v("Javascript Console Message: %s", "D <expression>:2:13: Hello Debug");
    }

    @Test
    public void testJSScriptEngineLogsConsoleInfoMessages() throws InterruptedException {
        String scriptWithConsoleInfo =
                """
                function test() {
                    console.info("Hello Info");
                    return "Hello Info";
                }""";

        callAndVerifyJSScriptEngine(scriptWithConsoleInfo);

        Thread.sleep(1000); // wait for the console callback to happen.
        Mockito.verify(sMockLogger)
                .v("Javascript Console Message: %s", "I <expression>:2:13: Hello Info");
    }

    @Test
    public void testJSScriptEngineLogsConsoleWarningMessages() throws InterruptedException {
        String scriptWithConsoleWarning =
                """
                function test() {
                    console.warn("Hello Warning");
                    return "Hello Warning";
                }""";

        callAndVerifyJSScriptEngine(scriptWithConsoleWarning);

        Thread.sleep(1000); // wait for the console callback to happen.
        Mockito.verify(sMockLogger)
                .v("Javascript Console Message: %s", "W <expression>:2:13: Hello Warning");
    }

    @Test
    public void testJSScriptEngineLogsConsoleErrorMessages() throws InterruptedException {
        String scriptWithConsoleError =
                """
                function test() {
                    console.error("Hello Error");
                    return "Hello Error";
                }""";

        callAndVerifyJSScriptEngine(scriptWithConsoleError);

        Thread.sleep(1000); // wait for the console callback to happen.
        Mockito.verify(sMockLogger)
                .v("Javascript Console Message: %s", "E <expression>:2:13: Hello Error");
    }

    private void callAndVerifyJSScriptEngine(String jsScript) throws InterruptedException {
        sLogger.v("callAndVerifyJSScriptEngine called with script\n%s", jsScript);
        mJSScriptEngine = JSScriptEngine.getInstance(sMockLogger);

        boolean consoleMessagesInLogEnabled = true;
        IsolateSettings isolateSettings =
                IsolateSettings.builder()
                        .setMaxHeapSizeBytes(Flags.ISOLATE_MAX_HEAP_SIZE_BYTES)
                        .setIsolateConsoleMessageInLogsEnabled(consoleMessagesInLogEnabled)
                        .build();
        FutureSyncCallback<String> jsStringFutureSyncCallback = new FutureSyncCallback<>();
        FluentFuture.from(
                        mJSScriptEngine.evaluate(
                                jsScript,
                                List.of(),
                                "test",
                                isolateSettings,
                                new NoOpRetryStrategyImpl()))
                .addCallback(jsStringFutureSyncCallback, Runnable::run);

        jsStringFutureSyncCallback.assertResultReceived();
        sLogger.v("callAndVerifyJSScriptEngine completed successfully");
    }
}
