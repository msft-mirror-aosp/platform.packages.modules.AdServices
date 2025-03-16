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

import static org.mockito.Mockito.verify;

import com.android.adservices.LoggerFactory;
import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.common.WebViewSupportUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.NoOpRetryStrategyImpl;
import com.android.adservices.service.js.IsolateSettings;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.shared.testing.FutureSyncCallback;
import com.android.adservices.shared.testing.SupportedByConditionRule;

import com.google.common.util.concurrent.FluentFuture;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class JSScriptEngineE2ETest extends AdServicesMockitoTestCase {
    @Rule(order = 15)
    public final SupportedByConditionRule mJSSandboxSupportedRule =
            WebViewSupportUtil.createJSSandboxAvailableRule(mContext);

    @Mock private LoggerFactory.Logger mMockLogger;

    private JSScriptEngine mJSScriptEngine;

    @Before
    public void setup() throws Exception {
        mLog.v(
                "Checking if the WebView version installed on the device supports console"
                        + " callback");
        Assume.assumeTrue(
                "ConsoleCallback is not supported by the installed WebView version",
                WebViewSupportUtil.isJSIsolateConsoleCallbackAvailable(mSpyContext));
        // Destroy any existing JSScriptEngine and JavascriptSandbox
        mLog.v("Destroy any existing JSScriptEngine and JavascriptSandbox");
        JSScriptEngine previousEngine = JSScriptEngine.getInstance();
        previousEngine.shutdown().get(1, TimeUnit.SECONDS);

        mJSScriptEngine = JSScriptEngine.updateSingletonForE2ETest(mMockLogger);
        mLog.v("Changed JSScriptEngine singleton from %s to %s", previousEngine, mJSScriptEngine);
    }

    @After
    public void cleanup() throws Exception {
        if (mJSScriptEngine == null) {
            mLog.v("mJSScriptEngine not set, clean-up not needed");
            return;
        }
        mLog.v("Destroy mJSScriptEngine as part of cleanup");
        mJSScriptEngine.shutdown().get(1, TimeUnit.SECONDS);
        JSScriptEngine.resetSingletonForE2ETest();
    }

    @Test
    public void testJSScriptEngineLogsConsoleLogMessages() throws Exception {
        String scriptWithConsoleLog =
                """
                function test() {
                    console.log("Hello Log");
                    return "Hello Log";
                }""";

        callAndVerifyJSScriptEngine(scriptWithConsoleLog);

        Thread.sleep(1000); // wait for the console callback to happen.
        verify(mMockLogger).v("Javascript Console Message: %s", "L <expression>:2:13: Hello Log");
    }

    @Test
    public void testJSScriptEngineLogsConsoleDebugMessages() throws Exception {
        String scriptWithConsoleDebug =
                """
                function test() {
                    console.debug("Hello Debug");
                    return "Hello Debug";
                }""";

        callAndVerifyJSScriptEngine(scriptWithConsoleDebug);

        Thread.sleep(1000); // wait for the console callback to happen.
        verify(mMockLogger).v("Javascript Console Message: %s", "D <expression>:2:13: Hello Debug");
    }

    @Test
    public void testJSScriptEngineLogsConsoleInfoMessages() throws Exception {
        String scriptWithConsoleInfo =
                """
                function test() {
                    console.info("Hello Info");
                    return "Hello Info";
                }""";

        callAndVerifyJSScriptEngine(scriptWithConsoleInfo);

        Thread.sleep(1000); // wait for the console callback to happen.
        verify(mMockLogger).v("Javascript Console Message: %s", "I <expression>:2:13: Hello Info");
    }

    @Test
    public void testJSScriptEngineLogsConsoleWarningMessages() throws Exception {
        String scriptWithConsoleWarning =
                """
                function test() {
                    console.warn("Hello Warning");
                    return "Hello Warning";
                }""";

        callAndVerifyJSScriptEngine(scriptWithConsoleWarning);

        Thread.sleep(1000); // wait for the console callback to happen.
        verify(mMockLogger)
                .v("Javascript Console Message: %s", "W <expression>:2:13: Hello Warning");
    }

    @Test
    public void testJSScriptEngineLogsConsoleErrorMessages() throws Exception {
        String scriptWithConsoleError =
                """
                function test() {
                    console.error("Hello Error");
                    return "Hello Error";
                }""";

        callAndVerifyJSScriptEngine(scriptWithConsoleError);

        Thread.sleep(1000); // wait for the console callback to happen.
        verify(mMockLogger).v("Javascript Console Message: %s", "E <expression>:2:13: Hello Error");
    }

    private void callAndVerifyJSScriptEngine(String jsScript) throws Exception {
        mLog.v("callAndVerifyJSScriptEngine called with script\n%s", jsScript);

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
        mLog.v("callAndVerifyJSScriptEngine completed successfully");
    }

}
