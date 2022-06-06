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

import static com.android.adservices.service.js.JSScriptArgument.arrayArg;
import static com.android.adservices.service.js.JSScriptArgument.numericArg;
import static com.android.adservices.service.js.JSScriptArgument.recordArg;
import static com.android.adservices.service.js.JSScriptArgument.stringArg;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.service.exception.JSExecutionException;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.chromium.android_webview.js_sandbox.client.AwJsSandbox;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@SmallTest
public class JSScriptEngineTest {
    private static final String TAG = JSScriptEngineTest.class.getSimpleName();
    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(10);
    private final JSScriptEngine mJSScriptEngine = new JSScriptEngine(sContext);

    @Test
    public void testCanRunSimpleScriptWithNoArgs() throws Exception {
        assertThat(
                        callJSEngine(
                                "function test() { return \"hello world\"; }",
                                ImmutableList.of(),
                                "test"))
                .isEqualTo("\"hello world\"");
    }

    @Test
    public void testCanRunAScriptWithNoArgs() throws Exception {
        assertThat(
                        callJSEngine(
                                "function helloWorld() { return \"hello world\"; };",
                                ImmutableList.of(),
                                "helloWorld"))
                .isEqualTo("\"hello world\"");
    }

    @Test
    public void testCanRunSimpleScriptWithOneArg() throws Exception {
        assertThat(
                        callJSEngine(
                                "function hello(name) { return \"hello \" + name; };",
                                ImmutableList.of(stringArg("name", "Stefano")),
                                "hello"))
                .isEqualTo("\"hello Stefano\"");
    }

    @Test
    public void testCanRunAScriptWithOneArg() throws Exception {
        assertThat(
                        callJSEngine(
                                "function helloPerson(personName) { return \"hello \" + personName;"
                                        + " };",
                                ImmutableList.of(stringArg("name", "Stefano")),
                                "helloPerson"))
                .isEqualTo("\"hello Stefano\"");
    }

    @Test
    public void testCanUseJSONArguments() throws Exception {
        assertThat(
                        callJSEngine(
                                "function helloPerson(person) {  return \"hello \" + person.name; "
                                        + " };",
                                ImmutableList.of(
                                        recordArg("jsonArg", stringArg("name", "Stefano"))),
                                "helloPerson"))
                .isEqualTo("\"hello Stefano\"");
    }

    @Test
    public void testCanNotReferToScriptArguments() throws Exception {
        try {
            callJSEngine(
                    "function helloPerson(person) {  return \"hello \" +"
                            + " personOuter.name;  };",
                    ImmutableList.of(recordArg("personOuter", stringArg("name", "Stefano"))),
                    "helloPerson");
            Assert.fail("Expected exception");
        } catch (ExecutionException e) {
            Assert.assertTrue(e.getCause().getClass() == JSExecutionException.class);
        }
    }

    // During tests, look for logcat messages with tag "chromium" to check if any of your scripts
    // have syntax errors. Those messages won't be available on prod builds (need to register
    // a listener to WebChromeClient.onConsoleMessage to receive them if needed).
    @Test
    public void testWillReturnAStringWithContentNullEvaluatingScriptWithErrors() throws Exception {
        try {
            callJSEngine(
                    "function test() { return \"hello world\"; }",
                    ImmutableList.of(),
                    "undefinedFunction");
            Assert.fail();
        } catch (ExecutionException e) {
            Assert.assertTrue(e.getCause().getClass() == JSExecutionException.class);
        }
    }

    @Test
    public void testParallelCallsToTheScriptEngineDoNotInterfere() throws Exception {
        CountDownLatch resultsLatch = new CountDownLatch(2);

        final ImmutableList<JSScriptArgument> arguments =
                ImmutableList.of(recordArg("jsonArg", stringArg("name", "Stefano")));

        ListenableFuture<String> firstCallResult =
                callJSEngineAsync(
                        "function helloPerson(person) {  return \"hello \" + person.name; " + " };",
                        arguments,
                        "helloPerson",
                        resultsLatch);

        // The previous call reset the status, we can redefine the function and use the same
        // argument
        ListenableFuture<String> secondCallResult =
                callJSEngineAsync(
                        "function helloPerson(person) {  return \"hello again \" + person.name; "
                                + " };",
                        arguments,
                        "helloPerson",
                        resultsLatch);

        resultsLatch.await();

        assertThat(firstCallResult.get()).isEqualTo("\"hello Stefano\"");

        assertThat(secondCallResult.get()).isEqualTo("\"hello again Stefano\"");
    }

    @Test
    public void testCanCreateMultipleInstancesOfScriptEngine()
            throws InterruptedException, ExecutionException {
        JSScriptEngine otherEngine = new JSScriptEngine(sContext);
        CountDownLatch resultsLatch = new CountDownLatch(2);
        final ImmutableList<JSScriptArgument> arguments =
                ImmutableList.of(recordArg("jsonArg", stringArg("name", "Stefano")));

        ListenableFuture<String> firstCallResult =
                callJSEngineAsync(
                        "function helloPerson(person) {  return \"hello \" + person.name; " + " };",
                        arguments,
                        "helloPerson",
                        resultsLatch);

        // The previous call reset the status, we can redefine the function and use the same
        // argument
        ListenableFuture<String> secondCallResult =
                callJSEngineAsync(
                        otherEngine,
                        "function helloPerson(person) {  return \"hello again \" + person.name; "
                                + " };",
                        arguments,
                        "helloPerson",
                        resultsLatch);

        resultsLatch.await();

        assertThat(firstCallResult.get()).isEqualTo("\"hello Stefano\"");

        assertThat(secondCallResult.get()).isEqualTo("\"hello again Stefano\"");
    }

    @Test
    public void testCanHandleFailuresFromWebView() {
        // The binder can transfer at most 1MB, this is larger than needed since, once
        // converted into a JS array initialization script will be way over the limits.
        List<JSScriptNumericArgument<Integer>> tooBigForBinder =
                Arrays.stream(new int[1024 * 1024])
                        .boxed()
                        .map(value -> numericArg("_", value))
                        .collect(Collectors.toList());

        ExecutionException outerException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                callJSEngine(
                                        "function helloBigArray(array) {\n"
                                                + " return array.length;\n"
                                                + "}",
                                        ImmutableList.of(arrayArg("array", tooBigForBinder)),
                                        "test"));
        assertThat(outerException.getCause()).isInstanceOf(JSExecutionException.class);
    }

    @Test
    public void testCanCloseAndThenWorkWithSameInstance() throws Exception {
        assertThat(
                        callJSEngine(
                                "function test() { return \"hello world\"; }",
                                ImmutableList.of(),
                                "test"))
                .isEqualTo("\"hello world\"");

        JSScriptEngine.shutdown();

        assertThat(
                        callJSEngine(
                                "function test() { return \"hello world\"; }",
                                ImmutableList.of(),
                                "test"))
                .isEqualTo("\"hello world\"");
    }

    @Test
    public void testCanCloseAndThenWorkWithNewInstance() throws Exception {
        assertThat(
                        callJSEngine(
                                "function test() { return \"hello world\"; }",
                                ImmutableList.of(),
                                "test"))
                .isEqualTo("\"hello world\"");

        JSScriptEngine.shutdown();

        JSScriptEngine newEngine = new JSScriptEngine(sContext);

        assertThat(
                        callJSEngine(
                                newEngine,
                                "function test() { return \"hello world\"; }",
                                ImmutableList.of(),
                                "test"))
                .isEqualTo("\"hello world\"");
    }

    @Test
    public void testConnectionIsResetIfJSProcessIsTerminated() {
        JSScriptEngine.JsSandboxProvider mockSandboxProvider =
                mock(JSScriptEngine.JsSandboxProvider.class);

        AwJsSandbox throwingSandbox = mock(AwJsSandbox.class);
        when(throwingSandbox.createIsolate())
                .thenThrow(
                        new IllegalStateException(
                                "simulating a failure caused by AwJsSandbox being disconnected"));

        FluentFuture<AwJsSandbox> futureInstance =
                FluentFuture.from(Futures.immediateFuture(throwingSandbox));
        when(mockSandboxProvider.getFutureInstance(Mockito.any(Context.class)))
                .thenReturn(futureInstance);

        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                callJSEngine(
                                        new JSScriptEngine(
                                                ApplicationProvider.getApplicationContext(),
                                                mockSandboxProvider),
                                        "function test() { return \"hello world\"; }",
                                        ImmutableList.of(),
                                        "test"));

        verify(mockSandboxProvider).destroyCurrentInstance();
        assertThat(executionException.getCause())
                .isInstanceOf(JSScriptEngineConnectionException.class);
    }

    @Test
    public void testJsSandboxProviderCreatesOnlyOneInstance()
            throws ExecutionException, InterruptedException, TimeoutException {
        JSScriptEngine.JsSandboxProvider jsSandboxProvider = new JSScriptEngine.JsSandboxProvider();
        Context applicationContext = ApplicationProvider.getApplicationContext();

        AwJsSandbox firstInstance =
                jsSandboxProvider.getFutureInstance(applicationContext).get(5, TimeUnit.SECONDS);
        AwJsSandbox secondInstance =
                jsSandboxProvider.getFutureInstance(applicationContext).get(5, TimeUnit.SECONDS);

        assertSame(firstInstance, secondInstance);
    }

    @Test
    public void testJsSandboxProviderCreatesNewInstanceAfterFirstIsDestroyed()
            throws ExecutionException, InterruptedException, TimeoutException {
        JSScriptEngine.JsSandboxProvider jsSandboxProvider = new JSScriptEngine.JsSandboxProvider();
        Context applicationContext = ApplicationProvider.getApplicationContext();

        AwJsSandbox firstInstance =
                jsSandboxProvider.getFutureInstance(applicationContext).get(5, TimeUnit.SECONDS);
        jsSandboxProvider.destroyCurrentInstance();

        AwJsSandbox secondInstance =
                jsSandboxProvider.getFutureInstance(applicationContext).get(5, TimeUnit.SECONDS);

        assertNotSame(firstInstance, secondInstance);
    }

    private String callJSEngine(
            @NonNull String jsScript,
            @NonNull List<JSScriptArgument> args,
            @NonNull String functionName)
            throws Exception {
        return callJSEngine(mJSScriptEngine, jsScript, args, functionName);
    }

    private String callJSEngine(
            @NonNull JSScriptEngine jsScriptEngine,
            @NonNull String jsScript,
            @NonNull List<JSScriptArgument> args,
            @NonNull String functionName)
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        ListenableFuture<String> futureResult =
                callJSEngineAsync(jsScriptEngine, jsScript, args, functionName, resultLatch);
        resultLatch.await();
        return futureResult.get();
    }

    private ListenableFuture<String> callJSEngineAsync(
            @NonNull String jsScript,
            @NonNull List<JSScriptArgument> args,
            @NonNull String functionName,
            @NonNull CountDownLatch resultLatch) {
        return callJSEngineAsync(mJSScriptEngine, jsScript, args, functionName, resultLatch);
    }

    private ListenableFuture<String> callJSEngineAsync(
            @NonNull JSScriptEngine engine,
            @NonNull String jsScript,
            @NonNull List<JSScriptArgument> args,
            @NonNull String functionName,
            @NonNull CountDownLatch resultLatch) {
        Objects.requireNonNull(engine);
        Objects.requireNonNull(resultLatch);
        Log.i(TAG, "Calling WebVew");
        ListenableFuture<String> result = engine.evaluate(jsScript, args, functionName);
        result.addListener(resultLatch::countDown, mExecutorService);
        return result;
    }
}
