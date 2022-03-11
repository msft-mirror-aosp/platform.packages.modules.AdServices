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

import static com.android.adservices.service.js.JSScriptArgument.recordArg;
import static com.android.adservices.service.js.JSScriptArgument.stringArg;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SmallTest
public class JSScriptEngineTest {
    private static final String TAG = "JSScriptEngineTest";
    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private final JSScriptEngine mJSScriptEngine = new JSScriptEngine(sContext);
    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(10);

    @Test
    public void testCanRunSimpleScriptWithNoArgs() throws Exception {
        assertThat(callJSEngine("(function() { return \"hello world\"; })();", null))
                .isEqualTo("\"hello world\"");
    }

    @Test
    public void testCanRunAScriptWithNoArgs() throws Exception {
        assertThat(
                        callJSEngine(
                                "function helloWorld() { return \"hello world\"; };\n"
                                        + "helloWorld();",
                                null))
                .isEqualTo("\"hello world\"");
    }

    @Test
    public void testCanRunSimpleScriptWithOneArg() throws Exception {
        assertThat(
                        callJSEngine(
                                "(function() { return \"hello \" + name; })();",
                                ImmutableList.of(stringArg("name", "Stefano"))))
                .isEqualTo("\"hello Stefano\"");
    }

    @Test
    public void testCanRunAScriptWithOneArg() throws Exception {
        assertThat(
                        callJSEngine(
                                "function helloPerson(personName) { return \"hello \" + personName;"
                                        + " };\n"
                                        + "helloPerson(name);",
                                ImmutableList.of(stringArg("name", "Stefano"))))
                .isEqualTo("\"hello Stefano\"");
    }

    @Test
    public void testCanUseJSONArguments() throws Exception {
        assertThat(
                        callJSEngine(
                                "function helloPerson(person) {  return \"hello \" + person.name; "
                                        + " };\n"
                                        + "helloPerson(jsonArg);",
                                ImmutableList.of(
                                        recordArg("jsonArg", stringArg("name", "Stefano")))))
                .isEqualTo("\"hello Stefano\"");
    }

    // During tests, look for logcat messages with tag "chromium" to check if any of your scripts
    // have syntax errors. Those messages won't be available on prod builds (need to register
    // a listener to WebChromeClient.onConsoleMessage to receive them if needed).
    @Test
    public void testWillReturnAStringWithContentNullEvaluatingScriptWithErrors() throws Exception {
        assertThat(callJSEngine("undefinedFunction();", null)).isEqualTo("null");
    }

    @Test
    public void testParallelCallsToTheScriptEngineDoNotInterfere() throws Exception {
        CountDownLatch resultsLatch = new CountDownLatch(2);

        final ImmutableList<JSScriptArgument> arguments =
                ImmutableList.of(recordArg("jsonArg", stringArg("name", "Stefano")));

        ListenableFuture<String> firstCallResult =
                callJSEngineAsync(
                        "function helloPerson(person) {  return \"hello \" + person.name; "
                                + " };\n"
                                + "helloPerson(jsonArg);",
                        arguments,
                        resultsLatch);

        // The previous call reset the status, we can redefine the function and use the same
        // argument
        ListenableFuture<String> secondCallResult =
                callJSEngineAsync(
                        "function helloPerson(person) {  return \"hello again \" + person.name; "
                                + " };\n"
                                + "helloPerson(jsonArg);",
                        arguments,
                        resultsLatch);

        resultsLatch.await();

        assertThat(firstCallResult.get()).isEqualTo("\"hello Stefano\"");

        assertThat(secondCallResult.get()).isEqualTo("\"hello again Stefano\"");
    }

    private String callJSEngine(String jsScript, List<JSScriptArgument> args) throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        ListenableFuture<String> futureResult = callJSEngineAsync(jsScript, args, resultLatch);
        resultLatch.await();
        return futureResult.get();
    }

    private ListenableFuture<String> callJSEngineAsync(
            String jsScript, List<JSScriptArgument> args, CountDownLatch resultLatch) {
        Log.i(TAG, "Calling WebVew");
        ListenableFuture<String> result = mJSScriptEngine.evaluate(jsScript, args);
        result.addListener(resultLatch::countDown, mExecutorService);
        return result;
    }
}
