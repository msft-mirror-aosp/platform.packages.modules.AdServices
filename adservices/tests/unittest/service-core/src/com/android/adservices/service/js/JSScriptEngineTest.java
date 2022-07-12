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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.LogUtil;
import com.android.adservices.service.exception.JSExecutionException;
import com.android.adservices.service.profiling.JSScriptEngineLogConstants;
import com.android.adservices.service.profiling.Profiler;
import com.android.adservices.service.profiling.StopWatch;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.chromium.android_webview.js_sandbox.client.AwJsIsolate;
import org.chromium.android_webview.js_sandbox.client.AwJsSandbox;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@SmallTest
public class JSScriptEngineTest {
    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String TAG = JSScriptEngineTest.class.getSimpleName();
    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(10);

    @Mock private Profiler mMockProfiler;
    @Mock private StopWatch mSandboxInitWatch;
    @Mock private StopWatch mIsolateCreateWatch;
    @Mock private StopWatch mJavaExecutionWatch;

    @Mock private AwJsSandbox mMockedSandbox;
    @Mock private AwJsIsolate mMockedIsolate;
    @Mock private JSScriptEngine.JsSandboxProvider mMockSandboxProvider;

    private JSScriptEngine mJSScriptEngine;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        when(mMockProfiler.start(JSScriptEngineLogConstants.SANDBOX_INIT_TIME))
                .thenReturn(mSandboxInitWatch);
        when(mMockProfiler.start(JSScriptEngineLogConstants.ISOLATE_CREATE_TIME))
                .thenReturn(mIsolateCreateWatch);
        when(mMockProfiler.start(JSScriptEngineLogConstants.JAVA_EXECUTION_TIME))
                .thenReturn(mJavaExecutionWatch);

        FluentFuture<AwJsSandbox> futureInstance =
                FluentFuture.from(Futures.immediateFuture(mMockedSandbox));
        when(mMockSandboxProvider.getFutureInstance(sContext)).thenReturn(futureInstance);

        mJSScriptEngine = JSScriptEngine.getInstanceForTesting(sContext, mMockProfiler);
    }

    @Test
    public void testCanRunSimpleScriptWithNoArgs() throws Exception {
        assertThat(
                        callJSEngine(
                                "function test() { return \"hello world\"; }",
                                ImmutableList.of(),
                                "test"))
                .isEqualTo("\"hello world\"");

        verify(mMockProfiler).start(JSScriptEngineLogConstants.SANDBOX_INIT_TIME);
        verify(mMockProfiler).start(JSScriptEngineLogConstants.ISOLATE_CREATE_TIME);
        verify(mMockProfiler).start(JSScriptEngineLogConstants.JAVA_EXECUTION_TIME);
        verify(mSandboxInitWatch).stop();
        verify(mIsolateCreateWatch).stop();
        verify(mJavaExecutionWatch).stop();
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

        JSScriptEngine.getInstance(sContext).shutdown();

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

        mJSScriptEngine.shutdown();
        JSScriptEngine newEngine = JSScriptEngine.getInstanceForTesting(sContext, mMockProfiler);

        assertThat(
                        callJSEngine(
                                newEngine,
                                "function test() { return \"hello world\"; }",
                                ImmutableList.of(),
                                "test"))
                .isEqualTo("\"hello world\"");

        verify(mMockProfiler, Mockito.times(2))
                .start(JSScriptEngineLogConstants.ISOLATE_CREATE_TIME);
    }

    @Test
    public void testConnectionIsResetIfJSProcessIsTerminated() {
        when(mMockedSandbox.createIsolate())
                .thenThrow(
                        new IllegalStateException(
                                "simulating a failure caused by AwJsSandbox being disconnected"));

        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                callJSEngine(
                                        JSScriptEngine.getInstanceForTesting(
                                                ApplicationProvider.getApplicationContext(),
                                                mMockSandboxProvider,
                                                mMockProfiler),
                                        "function test() { return \"hello world\"; }",
                                        ImmutableList.of(),
                                        "test"));

        verify(mMockSandboxProvider).destroyCurrentInstance();
        assertThat(executionException.getCause())
                .isInstanceOf(JSScriptEngineConnectionException.class);

        verify(mMockProfiler).start(JSScriptEngineLogConstants.SANDBOX_INIT_TIME);
        verify(mMockProfiler).start(JSScriptEngineLogConstants.ISOLATE_CREATE_TIME);
        verify(mSandboxInitWatch).stop();
    }

    // Troubles between google-java-format and checkstile
    // CHECKSTYLE:OFF IndentationCheck
    @Test
    public void testIsolateIsClosedWhenEvaluationCompletes() throws Exception {
        when(mMockedSandbox.createIsolate()).thenReturn(mMockedIsolate);
        doAnswer(
                        invocation -> {
                            AwJsIsolate.ExecutionCallback callback =
                                    (AwJsIsolate.ExecutionCallback) invocation.getArguments()[1];
                            callback.reportResult("hello world");
                            return null;
                        })
                .when(mMockedIsolate)
                .evaluateJavascript(anyString(), any(AwJsIsolate.ExecutionCallback.class));

        CountDownLatch isolateIsClosedLatch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            isolateIsClosedLatch.countDown();
                            return null;
                        })
                .when(mMockedIsolate)
                .close();

        callJSEngine(
                JSScriptEngine.getInstanceForTesting(
                        ApplicationProvider.getApplicationContext(),
                        mMockSandboxProvider,
                        mMockProfiler),
                "function test() { return \"hello world\"; }",
                ImmutableList.of(),
                "test");

        isolateIsClosedLatch.await(4, TimeUnit.SECONDS);
        verify(mMockedIsolate).close();
    }

    @Test
    public void testIsolateIsClosedWhenEvaluationFails() throws Exception {
        when(mMockedSandbox.createIsolate()).thenReturn(mMockedIsolate);
        doAnswer(
                        invocation -> {
                            AwJsIsolate.ExecutionCallback callback =
                                    (AwJsIsolate.ExecutionCallback) invocation.getArguments()[1];
                            callback.reportError("JS execution failed");
                            return null;
                        })
                .when(mMockedIsolate)
                .evaluateJavascript(anyString(), any(AwJsIsolate.ExecutionCallback.class));

        CountDownLatch isolateIsClosedLatch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            isolateIsClosedLatch.countDown();
                            return null;
                        })
                .when(mMockedIsolate)
                .close();

        assertThrows(
                ExecutionException.class,
                () ->
                        callJSEngine(
                                JSScriptEngine.getInstanceForTesting(
                                        ApplicationProvider.getApplicationContext(),
                                        mMockSandboxProvider,
                                        mMockProfiler),
                                "function test() { return \"hello world\"; }",
                                ImmutableList.of(),
                                "test"));

        isolateIsClosedLatch.await(4, TimeUnit.SECONDS);
        verify(mMockedIsolate).close();
    }

    @Test
    public void testIsolateIsClosedWhenEvaluationIsCancelled() throws Exception {
        when(mMockedSandbox.createIsolate()).thenReturn(mMockedIsolate);

        CountDownLatch jsEvaluationStartedLatch = new CountDownLatch(1);
        CountDownLatch completeJsEvaluationLatch = new CountDownLatch(1);
        ExecutorService callbackExecutor = Executors.newSingleThreadExecutor();
        doAnswer(
                        invocation -> {
                            callbackExecutor.submit(
                                    () -> {
                                        jsEvaluationStartedLatch.countDown();
                                        AwJsIsolate.ExecutionCallback callback =
                                                (AwJsIsolate.ExecutionCallback)
                                                        invocation.getArguments()[1];
                                        LogUtil.i("Waiting before reporting JS completion");
                                        try {
                                            completeJsEvaluationLatch.await();
                                        } catch (InterruptedException ignored) {
                                            Thread.currentThread().interrupt();
                                        }
                                        LogUtil.i("Reporting JS completion");
                                        callback.reportResult("hello world");
                                    });
                            return null;
                        })
                .when(mMockedIsolate)
                .evaluateJavascript(anyString(), any(AwJsIsolate.ExecutionCallback.class));
        CountDownLatch isolateIsClosedLatch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            isolateIsClosedLatch.countDown();
                            return null;
                        })
                .when(mMockedIsolate)
                .close();

        JSScriptEngine engine =
                JSScriptEngine.getInstanceForTesting(
                        ApplicationProvider.getApplicationContext(),
                        mMockSandboxProvider,
                        mMockProfiler);
        ListenableFuture<String> jsExecutionFuture =
                engine.evaluate(
                        "function test() { return \"hello world\"; }", ImmutableList.of(), "test");

        // cancelling only after the processing started and the sandbox has been created
        jsEvaluationStartedLatch.await(4, TimeUnit.SECONDS);
        LogUtil.i("Cancelling JS future");
        jsExecutionFuture.cancel(true);
        LogUtil.i("Waiting for isolate to close");
        isolateIsClosedLatch.await(4, TimeUnit.SECONDS);
        LogUtil.i("Checking");
        verify(mMockedIsolate).close();

        completeJsEvaluationLatch.countDown();
    }

    @Test
    public void testIsolateIsClosedWhenEvaluationTimesOut() throws Exception {
        when(mMockedSandbox.createIsolate()).thenReturn(mMockedIsolate);
        ExecutorService callbackExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch completeJsEvaluationLatch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            callbackExecutor.submit(
                                    () -> {
                                        AwJsIsolate.ExecutionCallback callback =
                                                (AwJsIsolate.ExecutionCallback)
                                                        invocation.getArguments()[1];
                                        try {
                                            LogUtil.i("Waiting before reporting JS completion");
                                            completeJsEvaluationLatch.await();
                                        } catch (InterruptedException ignored) {
                                            Thread.currentThread().interrupt();
                                        }
                                        LogUtil.i("Reporting JS completion");
                                        callback.reportResult("hello world");
                                    });
                            return null;
                        })
                .when(mMockedIsolate)
                .evaluateJavascript(anyString(), any(AwJsIsolate.ExecutionCallback.class));
        CountDownLatch isolateIsClosedLatch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            isolateIsClosedLatch.countDown();
                            return null;
                        })
                .when(mMockedIsolate)
                .close();

        JSScriptEngine engine =
                JSScriptEngine.getInstanceForTesting(
                        ApplicationProvider.getApplicationContext(),
                        mMockSandboxProvider,
                        mMockProfiler);
        ExecutionException timeoutException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                FluentFuture.from(
                                                engine.evaluate(
                                                        "function test() { return \"hello world\";"
                                                                + " }",
                                                        ImmutableList.of(),
                                                        "test"))
                                        .withTimeout(
                                                500,
                                                TimeUnit.MILLISECONDS,
                                                new ScheduledThreadPoolExecutor(1))
                                        .get());

        isolateIsClosedLatch.await(4, TimeUnit.SECONDS);
        verify(mMockedIsolate).close();

        completeJsEvaluationLatch.countDown();

        assertThat(timeoutException.getCause()).isInstanceOf(TimeoutException.class);
    }
    // CHECKSTYLE:ON IndentationCheck

    @Test
    public void testThrowsExceptionAndRecreateSandboxIfIsolateCreationFails() throws Exception {
        doThrow(new RuntimeException("Simulating isolate creation failure"))
                .when(mMockedSandbox)
                .createIsolate();

        JSScriptEngine engine =
                JSScriptEngine.getInstanceForTesting(
                        ApplicationProvider.getApplicationContext(),
                        mMockSandboxProvider,
                        mMockProfiler);

        assertThrows(
                ExecutionException.class,
                () ->
                        callJSEngine(
                                engine,
                                "function test() { return \"hello world\";" + " }",
                                ImmutableList.of(),
                                "test"));
        verify(mMockSandboxProvider).destroyCurrentInstance();
    }

    @Test
    public void testJsSandboxProviderCreatesOnlyOneInstance()
            throws ExecutionException, InterruptedException, TimeoutException {
        Profiler profilerMock = mock(Profiler.class);
        when(profilerMock.start(JSScriptEngineLogConstants.SANDBOX_INIT_TIME))
                .thenReturn(mSandboxInitWatch);
        JSScriptEngine.JsSandboxProvider jsSandboxProvider =
                new JSScriptEngine.JsSandboxProvider(profilerMock);
        Context applicationContext = ApplicationProvider.getApplicationContext();

        AwJsSandbox firstInstance =
                jsSandboxProvider.getFutureInstance(applicationContext).get(5, TimeUnit.SECONDS);
        AwJsSandbox secondInstance =
                jsSandboxProvider.getFutureInstance(applicationContext).get(5, TimeUnit.SECONDS);

        assertSame(firstInstance, secondInstance);
        verify(profilerMock).start(JSScriptEngineLogConstants.SANDBOX_INIT_TIME);
    }

    @Test
    public void testJsSandboxProviderCreatesNewInstanceAfterFirstIsDestroyed()
            throws ExecutionException, InterruptedException, TimeoutException {
        Profiler profilerMock = mock(Profiler.class);
        when(profilerMock.start(JSScriptEngineLogConstants.SANDBOX_INIT_TIME))
                .thenReturn(mSandboxInitWatch);
        JSScriptEngine.JsSandboxProvider jsSandboxProvider =
                new JSScriptEngine.JsSandboxProvider(profilerMock);
        Context applicationContext = ApplicationProvider.getApplicationContext();

        AwJsSandbox firstInstance =
                jsSandboxProvider.getFutureInstance(applicationContext).get(5, TimeUnit.SECONDS);
        jsSandboxProvider.destroyCurrentInstance();

        AwJsSandbox secondInstance =
                jsSandboxProvider.getFutureInstance(applicationContext).get(5, TimeUnit.SECONDS);

        assertNotSame(firstInstance, secondInstance);
        verify(profilerMock, Mockito.times(2)).start(JSScriptEngineLogConstants.SANDBOX_INIT_TIME);
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
