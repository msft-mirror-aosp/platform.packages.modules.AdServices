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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.service.profiling.JSScriptEngineLogConstants;
import com.android.adservices.service.profiling.Profiler;
import com.android.adservices.service.profiling.StopWatch;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSession;

import com.google.common.util.concurrent.Futures;

import org.chromium.android_webview.js_sandbox.client.JsSandbox;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SmallTest
public class JsSandboxProviderTest {
    private final Context mApplicationContext = ApplicationProvider.getApplicationContext();
    private StaticMockitoSession mStaticMockSession;
    @Mock private StopWatch mSandboxInitWatch;
    @Mock private JsSandbox mSandbox;
    @Mock private Profiler mProfilerMock;

    private JSScriptEngine.JsSandboxProvider mJsSandboxProvider;

    @Before
    public void setUp() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(JsSandbox.class)
                        .initMocks(this)
                        .startMocking();

        doReturn(Futures.immediateFuture(mSandbox))
                .when(
                        () -> {
                            return JsSandbox.newConnectedInstanceAsync(mApplicationContext);
                        });

        when(mProfilerMock.start(JSScriptEngineLogConstants.SANDBOX_INIT_TIME))
                .thenReturn(mSandboxInitWatch);
        mJsSandboxProvider = new JSScriptEngine.JsSandboxProvider(mProfilerMock);
    }

    @After
    public void shutDown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testJsSandboxProviderCreatesOnlyOneInstance()
            throws ExecutionException, InterruptedException, TimeoutException {
        mJsSandboxProvider.getFutureInstance(mApplicationContext).get(5, TimeUnit.SECONDS);
        mJsSandboxProvider.getFutureInstance(mApplicationContext).get(5, TimeUnit.SECONDS);

        verify(() -> JsSandbox.newConnectedInstanceAsync(mApplicationContext));
        verify(mProfilerMock).start(JSScriptEngineLogConstants.SANDBOX_INIT_TIME);
    }

    @Test
    public void testJsSandboxProviderCreatesNewInstanceAfterFirstIsDestroyed()
            throws ExecutionException, InterruptedException, TimeoutException {
        mJsSandboxProvider.getFutureInstance(mApplicationContext).get(5, TimeUnit.SECONDS);

        // Waiting for the first instance closure
        mJsSandboxProvider.destroyCurrentInstance().get(4, TimeUnit.SECONDS);

        mJsSandboxProvider.getFutureInstance(mApplicationContext).get(5, TimeUnit.SECONDS);

        verify(() -> JsSandbox.newConnectedInstanceAsync(mApplicationContext), Mockito.times(2));

        verify(mProfilerMock, Mockito.times(2)).start(JSScriptEngineLogConstants.SANDBOX_INIT_TIME);
    }
}
