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

package com.android.server.sdksandbox;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;

import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__ADD_SDK_SANDBOX_LIFECYCLE_CALLBACK;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__REMOVE_SDK_SANDBOX_LIFECYCLE_CALLBACK;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__LOAD_SANDBOX;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SANDBOX;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SANDBOX_TO_SYSTEM_SERVER;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_SANDBOX_TO_APP;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_TO_APP;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_TO_SANDBOX;
import static com.android.server.wm.ActivityInterceptorCallback.MAINLINE_SDK_SANDBOX_ORDER_ID;

import static org.mockito.ArgumentMatchers.eq;

import android.Manifest;
import android.app.ActivityManager;
import android.app.sdksandbox.AppOwnedSdkSandboxInterface;
import android.app.sdksandbox.ISharedPreferencesSyncCallback;
import android.app.sdksandbox.SandboxLatencyInfo;
import android.app.sdksandbox.SharedPreferencesUpdate;
import android.app.sdksandbox.testutils.FakeLoadSdkCallbackBinder;
import android.app.sdksandbox.testutils.FakeRequestSurfacePackageCallbackBinder;
import android.app.sdksandbox.testutils.FakeSdkSandboxProcessDeathCallbackBinder;
import android.app.sdksandbox.testutils.FakeSdkSandboxService;
import android.app.sdksandbox.testutils.SdkSandboxDeviceSupportedRule;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSessionBuilder;
import com.android.modules.utils.build.SdkLevel;
import com.android.sdksandbox.ISdkSandboxService;
import com.android.sdksandbox.service.stats.SdkSandboxStatsLog;
import com.android.server.wm.ActivityInterceptorCallback;
import com.android.server.wm.ActivityInterceptorCallbackRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Objects;

/** Unit tests for {@link SdkSandboxManagerService} StatsD metrics collection. */
public class SdkSandboxStatsdMetricsUnitTest {
    private static final String TAG = SdkSandboxStatsdMetricsUnitTest.class.getSimpleName();

    private static final String SDK_NAME = "com.android.codeprovider";
    private static final String APP_OWNED_SDK_SANDBOX_INTERFACE_NAME = "com.android.testinterface";
    private static final String TEST_PACKAGE = "com.android.server.sdksandbox.tests";
    private static final long TIME_APP_CALLED_SYSTEM_SERVER = 1;
    private static final long TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP = 3;
    private static final long START_TIME_TO_LOAD_SANDBOX = 5;
    private static final long END_TIME_TO_LOAD_SANDBOX = 7;
    private static final long TIME_SYSTEM_SERVER_CALLS_SANDBOX = 9;
    private static final long TIME_FAILURE_HANDLED = 11;
    private static final long END_TIME_IN_SYSTEM_SERVER = 15;
    private static final long TIME_SYSTEM_SERVER_CALL_FINISHED = 17;
    private static final long TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER = 18;
    private static final long TIME_SANDBOX_CALLED_SDK = 19;
    private static final long TIME_SDK_CALL_COMPLETED = 20;
    private static final long TIME_SANDBOX_CALLED_SYSTEM_SERVER = 21;
    private static final long TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX = 22;
    private static final long TIME_SYSTEM_SERVER_CALLED_APP = 23;
    private static final long TIME_SYSTEM_SERVER_COMPLETED_EXECUTION = 24;
    private static final long TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER = 25;

    private static final String TEST_KEY = "key";
    private static final String TEST_VALUE = "value";
    private static final SharedPreferencesUpdate TEST_UPDATE =
            new SharedPreferencesUpdate(new ArrayList<>(), getTestBundle());

    private SdkSandboxManagerService mService;
    private ActivityManager mAmSpy;
    private FakeSdkSandboxService mSdkSandboxService;
    private MockitoSession mStaticMockSession;
    private Context mSpyContext;
    private SdkSandboxManagerService.Injector mInjector;
    private int mClientAppUid;
    private ArgumentCaptor<ActivityInterceptorCallback> mInterceptorCallbackArgumentCaptor =
            ArgumentCaptor.forClass(ActivityInterceptorCallback.class);
    private boolean mDisabledNetworkChecks;
    private boolean mDisabledForegroundCheck;

    private static FakeSdkSandboxProvider sProvider;

    @Rule(order = 0)
    public final SdkSandboxDeviceSupportedRule supportedRule = new SdkSandboxDeviceSupportedRule();

    @Before
    public void setup() {
        StaticMockitoSessionBuilder mockitoSessionBuilder =
                ExtendedMockito.mockitoSession()
                        .strictness(Strictness.LENIENT)
                        .mockStatic(SdkSandboxStatsLog.class)
                        .initMocks(this);
        if (SdkLevel.isAtLeastU()) {
            mockitoSessionBuilder =
                    mockitoSessionBuilder.mockStatic(ActivityInterceptorCallbackRegistry.class);
        }
        // Required to access <sdk-library> information and DeviceConfig update.
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.READ_DEVICE_CONFIG,
                        Manifest.permission.WRITE_DEVICE_CONFIG,
                        // for Context#registerReceiverForAllUsers
                        Manifest.permission.INTERACT_ACROSS_USERS_FULL);

        mStaticMockSession = mockitoSessionBuilder.startMocking();

        if (SdkLevel.isAtLeastU()) {
            // mock the activity interceptor registry anc capture the callback if called
            ActivityInterceptorCallbackRegistry registryMock =
                    Mockito.mock(ActivityInterceptorCallbackRegistry.class);
            ExtendedMockito.doReturn(registryMock)
                    .when(ActivityInterceptorCallbackRegistry::getInstance);
            Mockito.doNothing()
                    .when(registryMock)
                    .registerActivityInterceptorCallback(
                            eq(MAINLINE_SDK_SANDBOX_ORDER_ID),
                            mInterceptorCallbackArgumentCaptor.capture());
        }

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mSpyContext = Mockito.spy(context);
        ActivityManager am = context.getSystemService(ActivityManager.class);
        mAmSpy = Mockito.spy(Objects.requireNonNull(am));

        Mockito.when(mSpyContext.getSystemService(ActivityManager.class)).thenReturn(mAmSpy);

        mSdkSandboxService = Mockito.spy(FakeSdkSandboxService.class);
        mSdkSandboxService.setTimeValues(
                TIME_SYSTEM_SERVER_CALL_FINISHED,
                TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER,
                TIME_SANDBOX_CALLED_SDK,
                TIME_SDK_CALL_COMPLETED,
                TIME_SANDBOX_CALLED_SYSTEM_SERVER);
        sProvider = new FakeSdkSandboxProvider(mSdkSandboxService);

        mInjector = Mockito.spy(new InjectorForTest(mSpyContext));

        mService = new SdkSandboxManagerService(mSpyContext, mInjector);
        mService.forceEnableSandbox();

        mClientAppUid = Process.myUid();
    }

    @After
    public void tearDown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testLatencyMetrics_LoadSdk() throws Exception {
        final SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_LOAD_SDK);
        // Explicitly set times app called and received call from System Server as these
        // timestamps are set in SdkSandboxManager.
        sandboxLatencyInfo.setTimeAppCalledSystemServer(TIME_APP_CALLED_SYSTEM_SERVER);
        loadSdk(SDK_NAME, sandboxLatencyInfo);
        mSdkSandboxService.sendLoadSdkSuccessfulWithSandboxLatencies(sandboxLatencyInfo);
        sandboxLatencyInfo.setTimeAppReceivedCallFromSystemServer(
                TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER);
        mService.logLatencies(sandboxLatencyInfo);

        // TODO(b/306445720): Create helper methods to verify calls to StatsD.
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED),
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK),
                                Mockito.anyInt(),
                                Mockito.eq(/*success=*/ true),
                                Mockito.eq(SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER),
                                Mockito.eq(mClientAppUid)));
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED),
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK),
                                Mockito.anyInt(),
                                Mockito.eq(/*success=*/ true),
                                Mockito.eq(SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX),
                                Mockito.eq(mClientAppUid)));
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED),
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK),
                                Mockito.anyInt(),
                                Mockito.eq(/*success=*/ true),
                                Mockito.eq(SANDBOX_API_CALLED__STAGE__LOAD_SANDBOX),
                                Mockito.eq(mClientAppUid)));
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED),
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK),
                                Mockito.anyInt(),
                                Mockito.eq(/*success=*/ true),
                                Mockito.eq(SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_TO_SANDBOX),
                                Mockito.eq(mClientAppUid)));
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED),
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK),
                                Mockito.anyInt(),
                                Mockito.eq(/*success=*/ true),
                                Mockito.eq(SANDBOX_API_CALLED__STAGE__SANDBOX),
                                Mockito.eq(mClientAppUid)));
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED),
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK),
                                Mockito.anyInt(),
                                Mockito.eq(/*success=*/ true),
                                Mockito.eq(SANDBOX_API_CALLED__STAGE__SANDBOX_TO_SYSTEM_SERVER),
                                Mockito.eq(mClientAppUid)));
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED),
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK),
                                Mockito.anyInt(),
                                Mockito.eq(/*success=*/ true),
                                Mockito.eq(SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_SANDBOX_TO_APP),
                                Mockito.eq(mClientAppUid)));
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED),
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK),
                                Mockito.anyInt(),
                                Mockito.eq(/*success=*/ true),
                                Mockito.eq(SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_TO_APP),
                                Mockito.eq(mClientAppUid)));
    }

    @Test
    public void testLatencyMetrics_LoadSdk_WithLatencies() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();
        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        START_TIME_TO_LOAD_SANDBOX,
                        END_TIME_TO_LOAD_SANDBOX,
                        TIME_SYSTEM_SERVER_CALLS_SANDBOX,
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX,
                        TIME_SYSTEM_SERVER_CALLED_APP);
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_LOAD_SDK);
        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();

        // Explicitly set times app called and received call from System Server as these
        // timestamps are set in SdkSandboxManager.
        sandboxLatencyInfo.setTimeAppCalledSystemServer(TIME_APP_CALLED_SYSTEM_SERVER);
        mService.loadSdk(TEST_PACKAGE, null, SDK_NAME, sandboxLatencyInfo, new Bundle(), callback);
        mSdkSandboxService.sendLoadSdkSuccessfulWithSandboxLatencies(sandboxLatencyInfo);
        sandboxLatencyInfo.setTimeAppReceivedCallFromSystemServer(
                TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER);
        mService.logLatencies(sandboxLatencyInfo);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                (int)
                                        (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP
                                                - TIME_APP_CALLED_SYSTEM_SERVER),
                                /*success=*/ true,
                                SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER,
                                mClientAppUid));

        final int systemServerAppToSandboxLatency =
                (int)
                        (TIME_SYSTEM_SERVER_CALL_FINISHED
                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP
                                - (END_TIME_TO_LOAD_SANDBOX - START_TIME_TO_LOAD_SANDBOX));
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                systemServerAppToSandboxLatency,
                                /*success=*/ true,
                                SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                                mClientAppUid));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                (int) (END_TIME_TO_LOAD_SANDBOX - START_TIME_TO_LOAD_SANDBOX),
                                /*success=*/ true,
                                SANDBOX_API_CALLED__STAGE__LOAD_SANDBOX,
                                mClientAppUid));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                (int)
                                        (TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER
                                                - TIME_SYSTEM_SERVER_CALL_FINISHED),
                                /*success=*/ true,
                                SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_TO_SANDBOX,
                                mClientAppUid));

        int sandboxLatency =
                (int)
                        (TIME_SANDBOX_CALLED_SYSTEM_SERVER
                                - TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER
                                - (TIME_SDK_CALL_COMPLETED - TIME_SANDBOX_CALLED_SDK));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                sandboxLatency,
                                /*success=*/ true,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SANDBOX,
                                mClientAppUid));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                (int) (TIME_SDK_CALL_COMPLETED - TIME_SANDBOX_CALLED_SDK),
                                /*success=*/ true,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SDK,
                                mClientAppUid));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                (int)
                                        (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX
                                                - TIME_SANDBOX_CALLED_SYSTEM_SERVER),
                                /*success=*/ true,
                                SANDBOX_API_CALLED__STAGE__SANDBOX_TO_SYSTEM_SERVER,
                                mClientAppUid));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                (int)
                                        (TIME_SYSTEM_SERVER_CALLED_APP
                                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX),
                                /*success=*/ true,
                                SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_SANDBOX_TO_APP,
                                mClientAppUid));
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                (int)
                                        (TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER
                                                - TIME_SYSTEM_SERVER_CALLED_APP),
                                /*success=*/ true,
                                SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_TO_APP,
                                mClientAppUid));
    }

    @Test
    public void testLatencyMetrics_SystemServerAppToSandbox_LoadSdk_FailureOnMultiLoad()
            throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        START_TIME_TO_LOAD_SANDBOX,
                        END_TIME_TO_LOAD_SANDBOX,
                        TIME_SYSTEM_SERVER_CALL_FINISHED,
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX,
                        TIME_SYSTEM_SERVER_CALLED_APP,
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_FAILURE_HANDLED);

        // Load it once
        {
            FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
            mService.loadSdk(
                    TEST_PACKAGE, null, SDK_NAME, new SandboxLatencyInfo(), new Bundle(), callback);
            mSdkSandboxService.sendLoadSdkSuccessful();
        }

        // Load it again
        {
            SandboxLatencyInfo sandboxLatencyInfo =
                    new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_LOAD_SDK);
            FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
            mService.loadSdk(
                    TEST_PACKAGE, null, SDK_NAME, sandboxLatencyInfo, new Bundle(), callback);
            mService.logLatencies(sandboxLatencyInfo);
        }

        int timeSystemServerAppToSandbox =
                (int) (TIME_FAILURE_HANDLED - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                timeSystemServerAppToSandbox,
                                /*success=*/ false,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                                mClientAppUid));
    }

    @Test
    public void testLatencyMetrics_SystemServerAppToSandbox_LoadSdk_InvalidSdkName()
            throws RemoteException {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_FAILURE_HANDLED,
                        TIME_FAILURE_HANDLED);

        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_LOAD_SDK);
        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        // Explicitly set times app called and received call from System Server as these
        // timestamps are set in SdkSandboxManager.
        sandboxLatencyInfo.setTimeAppCalledSystemServer(TIME_APP_CALLED_SYSTEM_SERVER);
        mService.loadSdk(TEST_PACKAGE, null, "RANDOM", sandboxLatencyInfo, new Bundle(), callback);
        sandboxLatencyInfo.setTimeAppReceivedCallFromSystemServer(
                TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER);
        mService.logLatencies(sandboxLatencyInfo);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                (int)
                                        (TIME_FAILURE_HANDLED
                                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP),
                                /*success=*/ false,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                                mClientAppUid));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                Mockito.anyInt(),
                                Mockito.anyInt(),
                                Mockito.anyInt(),
                                Mockito.anyBoolean(),
                                Mockito.anyInt(),
                                Mockito.anyInt()),
                Mockito.times(3));
    }

    @Test
    public void testLatencyMetrics_SystemServerAppToSandbox_LoadSdk_FailureOnAppDeath()
            throws RemoteException {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_LOAD_SDK);
        FakeLoadSdkCallbackBinder callback = Mockito.mock(FakeLoadSdkCallbackBinder.class);
        IBinder binder = Mockito.mock(IBinder.class);
        Mockito.when(callback.asBinder()).thenReturn(binder);

        Mockito.doThrow(new RemoteException())
                .when(binder)
                .linkToDeath(Mockito.any(), Mockito.anyInt());

        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_FAILURE_HANDLED,
                        TIME_FAILURE_HANDLED);

        // Explicitly set times app called and received call from System Server as these
        // timestamps are set in SdkSandboxManager.
        sandboxLatencyInfo.setTimeAppCalledSystemServer(TIME_APP_CALLED_SYSTEM_SERVER);
        mService.loadSdk(TEST_PACKAGE, null, SDK_NAME, sandboxLatencyInfo, new Bundle(), callback);
        mService.logLatencies(sandboxLatencyInfo);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                (int)
                                        (TIME_FAILURE_HANDLED
                                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP),
                                /*success=*/ false,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                                mClientAppUid));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                Mockito.anyInt(),
                                Mockito.anyInt(),
                                Mockito.anyInt(),
                                Mockito.anyBoolean(),
                                Mockito.anyInt(),
                                Mockito.anyInt()),
                Mockito.times(2));
    }

    @Test
    public void testLatencyMetrics_SystemServerAppToSandbox_LoadSdk_LoadSandboxFailure() {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        sProvider.disableBinding();

        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        START_TIME_TO_LOAD_SANDBOX,
                        TIME_FAILURE_HANDLED);

        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_LOAD_SDK);
        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(TEST_PACKAGE, null, SDK_NAME, sandboxLatencyInfo, new Bundle(), callback);
        mService.logLatencies(sandboxLatencyInfo);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                (int) (TIME_FAILURE_HANDLED - START_TIME_TO_LOAD_SANDBOX),
                                /*success=*/ false,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__LOAD_SANDBOX,
                                mClientAppUid));
    }

    @Test
    public void testLatencyMetrics_RequestSurfacePackage() throws Exception {
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_REQUEST_SURFACE_PACKAGE);
        sandboxLatencyInfo.setTimeAppCalledSystemServer(TIME_APP_CALLED_SYSTEM_SERVER);
        loadSdk(SDK_NAME);

        // 2. Call request package
        FakeRequestSurfacePackageCallbackBinder surfacePackageCallback =
                new FakeRequestSurfacePackageCallbackBinder();
        sandboxLatencyInfo.setTimeSystemServerCallFinished(TIME_SYSTEM_SERVER_CALL_FINISHED);
        mService.requestSurfacePackage(
                TEST_PACKAGE,
                SDK_NAME,
                new Binder(),
                0,
                500,
                500,
                sandboxLatencyInfo,
                new Bundle(),
                surfacePackageCallback);
        setSandboxLatencies(sandboxLatencyInfo);
        mSdkSandboxService.sendSurfacePackageReady(sandboxLatencyInfo);
        sandboxLatencyInfo.setTimeAppReceivedCallFromSystemServer(
                TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER);
        mService.logLatencies(sandboxLatencyInfo);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED),
                                Mockito.eq(SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE),
                                Mockito.anyInt(),
                                Mockito.eq(/*success=*/ true),
                                Mockito.eq(SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER),
                                Mockito.eq(mClientAppUid)));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED),
                                Mockito.eq(SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE),
                                Mockito.anyInt(),
                                Mockito.eq(/*success=*/ true),
                                Mockito.eq(SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX),
                                Mockito.eq(mClientAppUid)));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED),
                                Mockito.eq(SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE),
                                Mockito.anyInt(),
                                Mockito.eq(/*success=*/ true),
                                Mockito.eq(SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_TO_SANDBOX),
                                Mockito.eq(mClientAppUid)));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED),
                                Mockito.eq(SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE),
                                Mockito.anyInt(),
                                Mockito.eq(/*success=*/ true),
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SANDBOX),
                                Mockito.eq(mClientAppUid)));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED),
                                Mockito.eq(SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE),
                                Mockito.anyInt(),
                                Mockito.eq(/*success=*/ true),
                                Mockito.eq(SANDBOX_API_CALLED__STAGE__SANDBOX_TO_SYSTEM_SERVER),
                                Mockito.eq(mClientAppUid)));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED),
                                Mockito.eq(SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE),
                                Mockito.anyInt(),
                                Mockito.eq(/*success=*/ true),
                                Mockito.eq(SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_SANDBOX_TO_APP),
                                Mockito.eq(mClientAppUid)));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED),
                                Mockito.eq(SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE),
                                Mockito.anyInt(),
                                Mockito.eq(/*success=*/ true),
                                Mockito.eq(SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_TO_APP),
                                Mockito.eq(mClientAppUid)));
    }

    @Test
    public void testLatencyMetrics_RequestSurfacePackage_WithLatencies() throws Exception {
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_REQUEST_SURFACE_PACKAGE);
        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        // loadSdk timestamps
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        START_TIME_TO_LOAD_SANDBOX,
                        END_TIME_TO_LOAD_SANDBOX,
                        TIME_SYSTEM_SERVER_CALL_FINISHED,
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX,
                        TIME_SYSTEM_SERVER_CALLED_APP,
                        // requestSurfacePackage timestamps
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_SYSTEM_SERVER_CALL_FINISHED,
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX,
                        TIME_SYSTEM_SERVER_CALLED_APP);
        loadSdk(SDK_NAME);

        // 2. Call request package
        FakeRequestSurfacePackageCallbackBinder surfacePackageCallback =
                new FakeRequestSurfacePackageCallbackBinder();
        sandboxLatencyInfo.setTimeAppCalledSystemServer(TIME_APP_CALLED_SYSTEM_SERVER);
        mService.requestSurfacePackage(
                TEST_PACKAGE,
                SDK_NAME,
                new Binder(),
                0,
                500,
                500,
                sandboxLatencyInfo,
                new Bundle(),
                surfacePackageCallback);

        setSandboxLatencies(sandboxLatencyInfo);
        mSdkSandboxService.sendSurfacePackageReady(sandboxLatencyInfo);
        sandboxLatencyInfo.setTimeAppReceivedCallFromSystemServer(
                TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER);
        mService.logLatencies(sandboxLatencyInfo);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE,
                                (int)
                                        (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP
                                                - TIME_APP_CALLED_SYSTEM_SERVER),
                                /*success=*/ true,
                                SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER,
                                mClientAppUid));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE,
                                (int)
                                        (TIME_SYSTEM_SERVER_CALL_FINISHED
                                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP),
                                /*success=*/ true,
                                SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                                mClientAppUid));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE,
                                (int)
                                        (TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER
                                                - TIME_SYSTEM_SERVER_CALL_FINISHED),
                                /*success=*/ true,
                                SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_TO_SANDBOX,
                                mClientAppUid));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE,
                                (int)
                                        (TIME_SANDBOX_CALLED_SYSTEM_SERVER
                                                - TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER),
                                /*success=*/ true,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SANDBOX,
                                mClientAppUid));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE,
                                (int)
                                        (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX
                                                - TIME_SANDBOX_CALLED_SYSTEM_SERVER),
                                /*success=*/ true,
                                SANDBOX_API_CALLED__STAGE__SANDBOX_TO_SYSTEM_SERVER,
                                mClientAppUid));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE,
                                (int)
                                        (TIME_SYSTEM_SERVER_CALLED_APP
                                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX),
                                /*success=*/ true,
                                SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_SANDBOX_TO_APP,
                                mClientAppUid));
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE,
                                (int)
                                        (TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER
                                                - TIME_SYSTEM_SERVER_CALLED_APP),
                                /*success=*/ true,
                                SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_TO_APP,
                                mClientAppUid));
    }

    @Test
    public void
            testLatencyMetrics_systemServerAppToSandbox_RequestSurfacePackage_sandboxNotLoaded() {
        disableForegroundCheck();

        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP, TIME_FAILURE_HANDLED);

        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_REQUEST_SURFACE_PACKAGE);
        FakeRequestSurfacePackageCallbackBinder surfacePackageCallback =
                new FakeRequestSurfacePackageCallbackBinder();
        mService.requestSurfacePackage(
                TEST_PACKAGE,
                SDK_NAME,
                new Binder(),
                0,
                500,
                500,
                sandboxLatencyInfo,
                new Bundle(),
                surfacePackageCallback);
        mService.logLatencies(sandboxLatencyInfo);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE,
                                (int)
                                        (TIME_FAILURE_HANDLED
                                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP),
                                /*success=*/ false,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                                mClientAppUid));
    }

    @Test
    public void testLatencyMetrics_GetSandboxedSdks_WithLatencies() {
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_GET_SANDBOXED_SDKS);
        // TODO(b/242149555): Update tests to use fake for getting time series.
        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP, END_TIME_IN_SYSTEM_SERVER);

        sandboxLatencyInfo.setTimeAppCalledSystemServer(TIME_APP_CALLED_SYSTEM_SERVER);
        mService.getSandboxedSdks(TEST_PACKAGE, sandboxLatencyInfo);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__GET_SANDBOXED_SDKS,
                                (int)
                                        (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP
                                                - TIME_APP_CALLED_SYSTEM_SERVER),
                                /*success=*/ true,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER,
                                mClientAppUid));
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__GET_SANDBOXED_SDKS,
                                (int)
                                        (END_TIME_IN_SYSTEM_SERVER
                                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP),
                                /*success=*/ true,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                                mClientAppUid));
    }

    @Test
    public void testLatencyMetrics_SyncDataFromClient() {
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_SYNC_DATA_FROM_CLIENT);
        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP);

        sandboxLatencyInfo.setTimeAppCalledSystemServer(TIME_APP_CALLED_SYSTEM_SERVER);
        // Sync data from client
        mService.syncDataFromClient(
                TEST_PACKAGE,
                sandboxLatencyInfo,
                TEST_UPDATE,
                Mockito.mock(ISharedPreferencesSyncCallback.class));
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__METHOD__SYNC_DATA_FROM_CLIENT,
                                (int)
                                        (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP
                                                - TIME_APP_CALLED_SYSTEM_SERVER),
                                /*success=*/ true,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER,
                                mClientAppUid));
    }

    @Test
    public void testLatencyMetrics_IpcFromAppToSystemServer_UnloadSdk() throws Exception {
        disableKillUid();

        loadSdk(SDK_NAME);
        mService.unloadSdk(TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER);
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__UNLOAD_SDK,
                                (int)
                                        (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP
                                                - TIME_APP_CALLED_SYSTEM_SERVER),
                                /*success=*/ true,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER,
                                mClientAppUid));
    }

    @Test
    public void testLatencyMetrics_systemServer_unloadSdk() throws Exception {
        disableKillUid();

        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        // for loadSdk
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        START_TIME_TO_LOAD_SANDBOX,
                        END_TIME_TO_LOAD_SANDBOX,
                        TIME_SYSTEM_SERVER_CALLS_SANDBOX,
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX,
                        TIME_SYSTEM_SERVER_CALLED_APP,
                        // for unloadSdk
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_SYSTEM_SERVER_CALL_FINISHED,
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX,
                        TIME_SYSTEM_SERVER_CALLED_APP);

        loadSdk(SDK_NAME);
        mService.unloadSdk(TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER);
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__UNLOAD_SDK,
                                (int)
                                        (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP
                                                - TIME_APP_CALLED_SYSTEM_SERVER),
                                /*success=*/ true,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER,
                                mClientAppUid));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__UNLOAD_SDK,
                                (int)
                                        (TIME_SYSTEM_SERVER_CALL_FINISHED
                                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP),
                                /*success=*/ true,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                                mClientAppUid));
    }

    @Test
    public void testLatencyMetrics_SystemServer_UnloadSdk_WithSandboxLatencies() throws Exception {
        disableKillUid();

        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        // for loadSdk
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        START_TIME_TO_LOAD_SANDBOX,
                        END_TIME_TO_LOAD_SANDBOX,
                        TIME_SYSTEM_SERVER_CALLS_SANDBOX,
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX,
                        TIME_SYSTEM_SERVER_CALLED_APP,
                        // for unloadSdk
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_SYSTEM_SERVER_CALL_FINISHED,
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX,
                        TIME_SYSTEM_SERVER_CALLED_APP,
                        TIME_SANDBOX_CALLED_SYSTEM_SERVER);

        loadSdk(SDK_NAME);
        mService.unloadSdk(TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER);
        mSdkSandboxService.sendUnloadSdkSuccess();

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__UNLOAD_SDK,
                                (int)
                                        (TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER
                                                - TIME_SYSTEM_SERVER_CALL_FINISHED),
                                /*success=*/ true,
                                SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_TO_SANDBOX,
                                mClientAppUid));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__UNLOAD_SDK,
                                (int)
                                        (TIME_SANDBOX_CALLED_SYSTEM_SERVER
                                                - TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER
                                                - (TIME_SDK_CALL_COMPLETED
                                                        - TIME_SANDBOX_CALLED_SDK)),
                                /*success=*/ true,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SANDBOX,
                                mClientAppUid));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__UNLOAD_SDK,
                                (int) (TIME_SDK_CALL_COMPLETED - TIME_SANDBOX_CALLED_SDK),
                                /*success=*/ true,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SDK,
                                mClientAppUid));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__UNLOAD_SDK,
                                (int)
                                        (TIME_SANDBOX_CALLED_SYSTEM_SERVER
                                                - TIME_SANDBOX_CALLED_SYSTEM_SERVER),
                                /*success=*/ true,
                                SANDBOX_API_CALLED__STAGE__SANDBOX_TO_SYSTEM_SERVER,
                                mClientAppUid));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__UNLOAD_SDK,
                                (int)
                                        (TIME_SYSTEM_SERVER_CALLED_APP
                                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX),
                                /*success=*/ true,
                                SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_SANDBOX_TO_APP,
                                mClientAppUid));
    }

    @Test
    public void testLatencyMetrics_ipcFromAppToSystemServer_addSdkSandboxProcessDeathCallback()
            throws Exception {
        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP);

        // Register for sandbox death event
        FakeSdkSandboxProcessDeathCallbackBinder lifecycleCallback =
                new FakeSdkSandboxProcessDeathCallbackBinder();
        mService.addSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER, lifecycleCallback);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SANDBOX_API_CALLED__METHOD__ADD_SDK_SANDBOX_LIFECYCLE_CALLBACK,
                                (int)
                                        (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP
                                                - TIME_APP_CALLED_SYSTEM_SERVER),
                                /*success=*/ true,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER,
                                mClientAppUid));
    }

    @Test
    public void testLatencyMetrics_systemServerAppToSandbox_addSdkSandboxProcessDeathCallback()
            throws Exception {
        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_SYSTEM_SERVER_COMPLETED_EXECUTION);

        // Register for sandbox death event
        FakeSdkSandboxProcessDeathCallbackBinder lifecycleCallback =
                new FakeSdkSandboxProcessDeathCallbackBinder();
        mService.addSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER, lifecycleCallback);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SANDBOX_API_CALLED__METHOD__ADD_SDK_SANDBOX_LIFECYCLE_CALLBACK,
                                (int)
                                        (TIME_SYSTEM_SERVER_COMPLETED_EXECUTION
                                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP),
                                /*success=*/ true,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                                mClientAppUid));
    }

    @Test
    public void testLatencyMetrics_ipcFromAppToSystemServer_removeSdkSandboxProcessDeathCallback() {
        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        // for addSdkSandboxLifecycleCallback
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_SYSTEM_SERVER_COMPLETED_EXECUTION,
                        // for removeSdkSandboxLifecycleCallback
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP);

        // Register for sandbox death event again
        FakeSdkSandboxProcessDeathCallbackBinder lifecycleCallback =
                new FakeSdkSandboxProcessDeathCallbackBinder();
        mService.addSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER, lifecycleCallback);

        // Unregister one of the lifecycle callbacks
        mService.removeSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER, lifecycleCallback);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SANDBOX_API_CALLED__METHOD__REMOVE_SDK_SANDBOX_LIFECYCLE_CALLBACK,
                                (int)
                                        (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP
                                                - TIME_APP_CALLED_SYSTEM_SERVER),
                                /*success=*/ true,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER,
                                mClientAppUid));
    }

    @Test
    public void testLatencyMetrics_systemServerAppToSandbox_removeSdkSandboxProcessDeathCallback()
            throws Exception {
        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        // for addSdkSandboxLifecycleCallback
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_SYSTEM_SERVER_COMPLETED_EXECUTION,
                        // for removeSdkSandboxLifecycleCallback
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_SYSTEM_SERVER_COMPLETED_EXECUTION);

        // Register for sandbox death event again
        FakeSdkSandboxProcessDeathCallbackBinder lifecycleCallback =
                new FakeSdkSandboxProcessDeathCallbackBinder();
        mService.addSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER, lifecycleCallback);

        // Unregister one of the lifecycle callbacks
        mService.removeSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER, lifecycleCallback);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SANDBOX_API_CALLED__METHOD__REMOVE_SDK_SANDBOX_LIFECYCLE_CALLBACK,
                                (int)
                                        (TIME_SYSTEM_SERVER_COMPLETED_EXECUTION
                                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP),
                                /*success=*/ true,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                                mClientAppUid));
    }

    @Test
    public void testLatencyMetrics_RegisterAppOwnedSdkSandboxInterface() throws Exception {
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(
                        SandboxLatencyInfo.METHOD_REGISTER_APP_OWNED_SDK_SANDBOX_INTERFACE);
        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_SYSTEM_SERVER_CALLS_SANDBOX);

        sandboxLatencyInfo.setTimeAppCalledSystemServer(TIME_APP_CALLED_SYSTEM_SERVER);
        mService.registerAppOwnedSdkSandboxInterface(
                TEST_PACKAGE,
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME,
                        /*version=*/ 0,
                        /*interfaceIBinder=*/ new Binder()),
                sandboxLatencyInfo);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__METHOD__REGISTER_APP_OWNED_SDK_SANDBOX_INTERFACE,
                                (int)
                                        (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP
                                                - TIME_APP_CALLED_SYSTEM_SERVER),
                                /*success=*/ true,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER,
                                mClientAppUid));
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__METHOD__REGISTER_APP_OWNED_SDK_SANDBOX_INTERFACE,
                                (int)
                                        (TIME_SYSTEM_SERVER_CALLS_SANDBOX
                                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP),
                                /*success=*/ true,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                                mClientAppUid));
    }

    @Test
    public void
            testLatencyMetrics_SystemServerAppToSandbox_RegisterAppOwnedSdkSandboxInterface_FailureOnAppDeath()
                    throws RemoteException {
        IBinder binder = Mockito.mock(IBinder.class);
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(
                        SandboxLatencyInfo.METHOD_REGISTER_APP_OWNED_SDK_SANDBOX_INTERFACE);

        Mockito.doThrow(new RemoteException())
                .when(binder)
                .linkToDeath(Mockito.any(), Mockito.anyInt());

        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP, TIME_FAILURE_HANDLED);

        mService.registerAppOwnedSdkSandboxInterface(
                TEST_PACKAGE,
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME,
                        /*version=*/ 0,
                        /*interfaceIBinder=*/ binder),
                sandboxLatencyInfo);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__METHOD__REGISTER_APP_OWNED_SDK_SANDBOX_INTERFACE,
                                (int)
                                        (TIME_FAILURE_HANDLED
                                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP),
                                /*success=*/ false,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                                mClientAppUid));
    }

    @Test
    public void testLatencyMetrics_UnregisterAppOwnedSdkSandboxInterface() throws Exception {
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(
                        SandboxLatencyInfo.METHOD_UNREGISTER_APP_OWNED_SDK_SANDBOX_INTERFACE);
        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_SYSTEM_SERVER_CALLS_SANDBOX);

        sandboxLatencyInfo.setTimeAppCalledSystemServer(TIME_APP_CALLED_SYSTEM_SERVER);
        mService.unregisterAppOwnedSdkSandboxInterface(
                TEST_PACKAGE, APP_OWNED_SDK_SANDBOX_INTERFACE_NAME, sandboxLatencyInfo);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__METHOD__UNREGISTER_APP_OWNED_SDK_SANDBOX_INTERFACE,
                                (int)
                                        (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP
                                                - TIME_APP_CALLED_SYSTEM_SERVER),
                                /*success=*/ true,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER,
                                mClientAppUid));
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__METHOD__UNREGISTER_APP_OWNED_SDK_SANDBOX_INTERFACE,
                                (int)
                                        (TIME_SYSTEM_SERVER_CALLS_SANDBOX
                                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP),
                                /*success=*/ true,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                                mClientAppUid));
    }

    @Test
    public void testLatencyMetrics_GetAppOwnedSdkSandboxInterfaces() throws Exception {
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(
                        SandboxLatencyInfo.METHOD_GET_APP_OWNED_SDK_SANDBOX_INTERFACES);
        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_SYSTEM_SERVER_CALLS_SANDBOX);

        sandboxLatencyInfo.setTimeAppCalledSystemServer(TIME_APP_CALLED_SYSTEM_SERVER);
        mService.getAppOwnedSdkSandboxInterfaces(TEST_PACKAGE, sandboxLatencyInfo);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__METHOD__GET_APP_OWNED_SDK_SANDBOX_INTERFACES,
                                (int)
                                        (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP
                                                - TIME_APP_CALLED_SYSTEM_SERVER),
                                /*success=*/ true,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER,
                                mClientAppUid));
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__METHOD__GET_APP_OWNED_SDK_SANDBOX_INTERFACES,
                                (int)
                                        (TIME_SYSTEM_SERVER_CALLS_SANDBOX
                                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP),
                                /*success=*/ true,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                                mClientAppUid));
    }

    private void disableForegroundCheck() {
        if (!mDisabledForegroundCheck) {
            Mockito.doReturn(IMPORTANCE_FOREGROUND).when(mAmSpy).getUidImportance(Mockito.anyInt());
            mDisabledForegroundCheck = true;
        }
    }

    /** Mock the ActivityManager::killUid to avoid SecurityException thrown in test. */
    private void disableKillUid() {
        Mockito.doNothing().when(mAmSpy).killUid(Mockito.anyInt(), Mockito.anyString());
    }

    /* Ignores network permission checks. */
    private void disableNetworkPermissionChecks() {
        if (!mDisabledNetworkChecks) {
            Mockito.doNothing()
                    .when(mSpyContext)
                    .enforceCallingPermission(
                            Mockito.eq("android.permission.INTERNET"), Mockito.anyString());
            Mockito.doNothing()
                    .when(mSpyContext)
                    .enforceCallingPermission(
                            Mockito.eq("android.permission.ACCESS_NETWORK_STATE"),
                            Mockito.anyString());
            mDisabledNetworkChecks = true;
        }
    }

    private static Bundle getTestBundle() {
        final Bundle data = new Bundle();
        data.putString(TEST_KEY, TEST_VALUE);
        return data;
    }

    private void loadSdk(String sdkName) throws RemoteException {
        loadSdk(sdkName, new SandboxLatencyInfo());
    }

    private void loadSdk(String sdkName, SandboxLatencyInfo sandboxLatencyInfo)
            throws RemoteException {
        disableNetworkPermissionChecks();
        disableForegroundCheck();
        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(TEST_PACKAGE, null, sdkName, sandboxLatencyInfo, new Bundle(), callback);
        mSdkSandboxService.sendLoadSdkSuccessful();
        callback.assertLoadSdkIsSuccessful();
    }

    private void setSandboxLatencies(SandboxLatencyInfo sandboxLatencyInfo) {
        sandboxLatencyInfo.setTimeSandboxReceivedCallFromSystemServer(
                TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER);
        sandboxLatencyInfo.setTimeSandboxCalledSystemServer(TIME_SANDBOX_CALLED_SYSTEM_SERVER);
    }

    // TODO(b/300444716): turn FakeSdkSandboxProvider into a test utility class
    /** Fake service provider that returns local instance of {@link SdkSandboxServiceProvider} */
    private static class FakeSdkSandboxProvider implements SdkSandboxServiceProvider {
        private FakeSdkSandboxService mSdkSandboxService;
        private final ArrayMap<CallingInfo, ISdkSandboxService> mService = new ArrayMap<>();

        // When set to true, this will fail the bindService call
        private boolean mFailBinding = false;

        FakeSdkSandboxProvider(FakeSdkSandboxService service) {
            mSdkSandboxService = service;
        }

        public void disableBinding() {
            mFailBinding = true;
        }

        @Override
        public void bindService(CallingInfo callingInfo, ServiceConnection serviceConnection) {
            if (mFailBinding) {
                serviceConnection.onNullBinding(new ComponentName("random", "component"));
                return;
            }

            if (mService.containsKey(callingInfo)) {
                return;
            }
            mService.put(callingInfo, mSdkSandboxService);
            serviceConnection.onServiceConnected(null, mSdkSandboxService.asBinder());
        }

        @Override
        public void unbindService(CallingInfo callingInfo) {
            mService.remove(callingInfo);
        }

        @Override
        public void stopSandboxService(CallingInfo callingInfo) {
            mService.remove(callingInfo);
        }

        @Nullable
        @Override
        public ISdkSandboxService getSdkSandboxServiceForApp(CallingInfo callingInfo) {
            return mService.get(callingInfo);
        }

        @Override
        public void onServiceConnected(
                CallingInfo callingInfo, @NonNull ISdkSandboxService service) {
            mService.put(callingInfo, service);
        }

        @Override
        public void onServiceDisconnected(CallingInfo callingInfo) {
            mService.put(callingInfo, null);
        }

        @Override
        public void onAppDeath(CallingInfo callingInfo) {}

        @Override
        public void onSandboxDeath(CallingInfo callingInfo) {}

        @Override
        public boolean isSandboxBoundForApp(CallingInfo callingInfo) {
            return false;
        }

        @Override
        public int getSandboxStatusForApp(CallingInfo callingInfo) {
            if (mService.containsKey(callingInfo)) {
                return CREATED;
            } else {
                return NON_EXISTENT;
            }
        }

        @Override
        public void dump(PrintWriter writer) {
            writer.println("FakeDump");
        }

        @NonNull
        @Override
        public String toSandboxProcessName(@NonNull CallingInfo callingInfo) {
            return TEST_PACKAGE + SANDBOX_PROCESS_NAME_SUFFIX;
        }

        @NonNull
        @Override
        public String toSandboxProcessNameForInstrumentation(@NonNull CallingInfo callingInfo) {
            return callingInfo.getPackageName() + SANDBOX_INSTR_PROCESS_NAME_SUFFIX;
        }
    }

    public static class InjectorForTest extends SdkSandboxManagerService.Injector {

        public InjectorForTest(Context spyContext) {
            super(spyContext);
        }

        @Override
        public SdkSandboxServiceProvider getSdkSandboxServiceProvider() {
            return sProvider;
        }

        @Override
        public long getCurrentTime() {
            return TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP;
        }
    }
}
