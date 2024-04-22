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
package android.app.sdksandbox.sdkprovider;

import static android.app.sdksandbox.SandboxLatencyInfo.RESULT_CODE_LOAD_SDK_ALREADY_LOADED;
import static android.app.sdksandbox.SandboxLatencyInfo.RESULT_CODE_LOAD_SDK_INTERNAL_ERROR;
import static android.app.sdksandbox.SandboxLatencyInfo.RESULT_CODE_LOAD_SDK_NOT_FOUND;
import static android.app.sdksandbox.SandboxLatencyInfo.RESULT_CODE_LOAD_SDK_SDK_DEFINED_ERROR;
import static android.app.sdksandbox.SandboxLatencyInfo.RESULT_CODE_LOAD_SDK_SDK_SANDBOX_DISABLED;
import static android.app.sdksandbox.SandboxLatencyInfo.RESULT_CODE_SDK_SANDBOX_PROCESS_NOT_AVAILABLE;
import static android.app.sdksandbox.SandboxLatencyInfo.RESULT_CODE_UNSPECIFIED;
import static android.app.sdksandbox.SdkSandboxManager.LOAD_SDK_ALREADY_LOADED;
import static android.app.sdksandbox.SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR;
import static android.app.sdksandbox.SdkSandboxManager.LOAD_SDK_NOT_FOUND;
import static android.app.sdksandbox.SdkSandboxManager.LOAD_SDK_SDK_DEFINED_ERROR;
import static android.app.sdksandbox.SdkSandboxManager.LOAD_SDK_SDK_SANDBOX_DISABLED;
import static android.app.sdksandbox.SdkSandboxManager.SDK_SANDBOX_PROCESS_NOT_AVAILABLE;

import static org.junit.Assert.assertEquals;

import android.app.sdksandbox.ILoadSdkCallback;
import android.app.sdksandbox.ISdkToServiceCallback;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxLatencyInfo;
import android.app.sdksandbox.SandboxedSdkContext;
import android.app.sdksandbox.SdkSandboxLocalSingleton;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSession;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collection;

/** Tests for the result code logging logic in {@link SdkSandboxController}. */
@RunWith(Parameterized.class)
public class SdkSandboxControllerResultCodeLoggingUnitTest {
    @SdkSandboxManager.LoadSdkErrorCode
    @Parameterized.Parameter(0)
    public int mLoadSdkErrorCode;

    @SandboxLatencyInfo.ResultCode
    @Parameterized.Parameter(1)
    public int mExpectedLoggingResultCode;

    /** Parameters for the test. */
    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[][] {
                    {LOAD_SDK_NOT_FOUND, RESULT_CODE_LOAD_SDK_NOT_FOUND},
                    {LOAD_SDK_ALREADY_LOADED, RESULT_CODE_LOAD_SDK_ALREADY_LOADED},
                    {LOAD_SDK_SDK_DEFINED_ERROR, RESULT_CODE_LOAD_SDK_SDK_DEFINED_ERROR},
                    {LOAD_SDK_SDK_SANDBOX_DISABLED, RESULT_CODE_LOAD_SDK_SDK_SANDBOX_DISABLED},
                    {LOAD_SDK_INTERNAL_ERROR, RESULT_CODE_LOAD_SDK_INTERNAL_ERROR},
                    {
                        SDK_SANDBOX_PROCESS_NOT_AVAILABLE,
                        RESULT_CODE_SDK_SANDBOX_PROCESS_NOT_AVAILABLE
                    },
                    {-1, RESULT_CODE_UNSPECIFIED}
                });
    }

    private SandboxedSdkContext mSandboxedSdkContext;
    private ISdkToServiceCallback mServiceCallback;
    private StaticMockitoSession mStaticMockSession;

    private static final String SDK_NAME = "com.android.codeproviderresources";
    private static final String ERROR_MSG = "Error";
    private static final String CLIENT_PACKAGE_NAME = "android.app.sdksandbox.sdkprovider";

    @Before
    public void setup() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mSandboxedSdkContext =
                new SandboxedSdkContext(
                        context,
                        getClass().getClassLoader(),
                        /* clientPackageName= */ CLIENT_PACKAGE_NAME,
                        new ApplicationInfo(),
                        /* sdkName= */ "",
                        /* sdkCeDataDir= */ null,
                        /* sdkDeDataDir= */ null);

        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(SdkSandboxLocalSingleton.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        SdkSandboxLocalSingleton sdkSandboxLocalSingleton =
                Mockito.mock(SdkSandboxLocalSingleton.class);
        mServiceCallback = Mockito.mock(ISdkToServiceCallback.class);
        Mockito.when(sdkSandboxLocalSingleton.getSdkToServiceCallback())
                .thenReturn(mServiceCallback);
        // Populate mSdkSandboxLocalSingleton
        ExtendedMockito.doReturn(sdkSandboxLocalSingleton)
                .when(SdkSandboxLocalSingleton::getExistingInstance);
    }

    @After
    public void tearDown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testLoadSdk_callFailsWithException_logSandboxApiLatencyCalledWithResultCode()
            throws Exception {
        SdkSandboxController sdkSandboxController = new SdkSandboxController(mSandboxedSdkContext);
        sdkSandboxController.loadSdk(
                SDK_NAME, new Bundle(), Runnable::run, new FakeLoadSdkCallback());
        ArgumentCaptor<SandboxLatencyInfo> sandboxLatencyInfoCaptor =
                ArgumentCaptor.forClass(SandboxLatencyInfo.class);
        ArgumentCaptor<ILoadSdkCallback> callbackArgumentCaptor =
                ArgumentCaptor.forClass(ILoadSdkCallback.class);
        Mockito.verify(mServiceCallback)
                .loadSdk(
                        Mockito.any(),
                        Mockito.any(),
                        sandboxLatencyInfoCaptor.capture(),
                        Mockito.any(),
                        callbackArgumentCaptor.capture());
        SandboxLatencyInfo sandboxLatencyInfo = sandboxLatencyInfoCaptor.getValue();
        // Simulate the error callback
        callbackArgumentCaptor
                .getValue()
                .onLoadSdkFailure(
                        new LoadSdkException(mLoadSdkErrorCode, ERROR_MSG), sandboxLatencyInfo);

        assertEquals(mExpectedLoggingResultCode, sandboxLatencyInfo.getResultCode());
    }
}
