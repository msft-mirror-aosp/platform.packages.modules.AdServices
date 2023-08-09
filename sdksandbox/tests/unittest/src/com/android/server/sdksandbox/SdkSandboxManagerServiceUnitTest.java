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

package com.android.server.sdksandbox;

import static android.Manifest.permission.DUMP;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.app.sdksandbox.ISharedPreferencesSyncCallback.PREFERENCES_SYNC_INTERNAL_ERROR;
import static android.app.sdksandbox.SdkSandboxManager.ACTION_START_SANDBOXED_ACTIVITY;
import static android.app.sdksandbox.SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR;

import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__ADD_SDK_SANDBOX_LIFECYCLE_CALLBACK;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__REMOVE_SDK_SANDBOX_LIFECYCLE_CALLBACK;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SANDBOX_TO_SYSTEM_SERVER;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_SANDBOX_TO_APP;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_TO_SANDBOX;
import static com.android.server.wm.ActivityInterceptorCallback.MAINLINE_SDK_SANDBOX_ORDER_ID;

import com.android.modules.utils.build.SdkLevel;
import com.android.sdksandbox.IComputeSdkStorageCallback;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;

import android.Manifest;
import android.app.ActivityManager;
import android.app.sdksandbox.AppOwnedSdkSandboxInterface;
import android.app.sdksandbox.ILoadSdkCallback;
import android.app.sdksandbox.ISdkSandboxManager;
import android.app.sdksandbox.ISharedPreferencesSyncCallback;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxLatencyInfo;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.SharedPreferencesUpdate;
import android.app.sdksandbox.testutils.DeviceSupportUtils;
import android.app.sdksandbox.testutils.FakeLoadSdkCallbackBinder;
import android.app.sdksandbox.testutils.FakeRequestSurfacePackageCallbackBinder;
import android.app.sdksandbox.testutils.FakeSdkSandboxManagerLocal;
import android.app.sdksandbox.testutils.FakeSdkSandboxProcessDeathCallbackBinder;
import android.app.sdksandbox.testutils.FakeSdkSandboxService;
import android.app.sdksandbox.testutils.FakeSharedPreferencesSyncCallback;
import android.app.sdksandbox.testutils.SdkSandboxStorageManagerUtility;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.UserInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSessionBuilder;
import com.android.modules.utils.build.SdkLevel;
import com.android.sdksandbox.ISdkSandboxService;
import com.android.sdksandbox.IUnloadSdkCallback;
import com.android.sdksandbox.service.stats.SdkSandboxStatsLog;
import com.android.server.LocalManagerRegistry;
import com.android.server.SystemService.TargetUser;
import com.android.server.am.ActivityManagerLocal;
import com.android.server.pm.PackageManagerLocal;
import com.android.server.sdksandbox.SdkSandboxStorageManager.StorageDirInfo;
import com.android.server.sdksandbox.proto.Services.AllowedService;
import com.android.server.sdksandbox.proto.Services.AllowedServices;
import com.android.server.wm.ActivityInterceptorCallback;
import com.android.server.wm.ActivityInterceptorCallbackRegistry;


import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Unit tests for {@link SdkSandboxManagerService}.
 */
public class SdkSandboxManagerServiceUnitTest {

    private static final String TAG = SdkSandboxManagerServiceUnitTest.class.getSimpleName();

    private SdkSandboxManagerService mService;
    private ActivityManager mAmSpy;
    private FakeSdkSandboxService mSdkSandboxService;
    private MockitoSession mStaticMockSession;
    private Context mSpyContext;
    private SdkSandboxManagerService.Injector mInjector;
    private int mClientAppUid;
    private PackageManagerLocal mPmLocal;
    private ActivityManagerLocal mAmLocal;
    private ArgumentCaptor<ActivityInterceptorCallback> mInterceptorCallbackArgumentCaptor =
            ArgumentCaptor.forClass(ActivityInterceptorCallback.class);
    private SdkSandboxStorageManagerUtility mSdkSandboxStorageManagerUtility;
    private boolean mDisabledNetworkChecks;
    private boolean mDisabledForegroundCheck;

    @Mock private IBinder mAdServicesManager;

    private static FakeSdkSandboxProvider sProvider;
    private static SdkSandboxPulledAtoms sSdkSandboxPulledAtoms;

    private static SdkSandboxManagerService.SdkSandboxSettingsListener sSdkSandboxSettingsListener;

    private SdkSandboxStorageManager mSdkSandboxStorageManager;
    private static SdkSandboxManagerLocal sSdkSandboxManagerLocal;
    private static final String SDK_NAME = "com.android.codeprovider";
    private static final String APP_OWNED_SDK_SANDBOX_INTERFACE_NAME = "com.android.testinterface";
    private static final String SDK_PROVIDER_PACKAGE = "com.android.codeprovider_1";
    private static final String SDK_PROVIDER_RESOURCES_SDK_NAME =
            "com.android.codeproviderresources";
    private static final String SDK_PROVIDER_RESOURCES_PACKAGE =
            "com.android.codeproviderresources_1";
    private static final String TEST_PACKAGE = "com.android.server.sdksandbox.tests";
    private static final String PROPERTY_DISABLE_SANDBOX = "disable_sdk_sandbox";
    private static final long TIME_APP_CALLED_SYSTEM_SERVER = 1;
    private static final long TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP = 3;
    private static final long START_TIME_TO_LOAD_SANDBOX = 5;
    private static final long END_TIME_TO_LOAD_SANDBOX = 7;
    private static final long TIME_SYSTEM_SERVER_CALLS_SANDBOX = 9;
    private static final long TIME_FAILURE_HANDLED = 11;
    private static final long END_TIME_IN_SYSTEM_SERVER = 15;
    private static final long TIME_SYSTEM_SERVER_CALLED_SANDBOX = 17;
    private static final long TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER = 18;
    private static final long TIME_SANDBOX_CALLED_SDK = 19;
    private static final long TIME_SDK_CALL_COMPLETED = 20;
    private static final long TIME_SANDBOX_CALLED_SYSTEM_SERVER = 21;
    private static final long TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX = 22;
    private static final long TIME_SYSTEM_SERVER_CALLED_APP = 23;
    private static final long TIME_SYSTEM_SERVER_COMPLETED_EXECUTION = 24;

    private static final String TEST_KEY = "key";
    private static final String TEST_VALUE = "value";
    private static final SharedPreferencesUpdate TEST_UPDATE =
            new SharedPreferencesUpdate(new ArrayList<>(), getTestBundle());

    private static final String PROPERTY_ENFORCE_RESTRICTIONS = "sdksandbox_enforce_restrictions";

    private static final String PROPERTY_SERVICES_ALLOWLIST =
            "services_allowlist_per_targetSdkVersion";

    private static final String INTENT_ACTION = "action.test";
    private static final String PACKAGE_NAME = "packageName.test";
    private static final String COMPONENT_CLASS_NAME = "className.test";
    private static final String COMPONENT_PACKAGE_NAME = "componentPackageName.test";
    private String mInitialServiceAllowlistValue;

    private static final String PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS =
            "apply_sdk_sandbox_next_restrictions";

    private String mInitialApplyNextSdkSandboxRestrictions = null;
    private static final String PROPERTY_NEXT_SERVICE_ALLOWLIST =
            "sdksandbox_next_service_allowlist";
    private String mInitialValueNextServiceAllowlist;

    private String mInitialEnforceRestrictions;

    @Before
    public void setup() {
        assumeTrue(
                DeviceSupportUtils.isSdkSandboxSupported(
                        InstrumentationRegistry.getInstrumentation().getContext()));
        StaticMockitoSessionBuilder mockitoSessionBuilder =
                ExtendedMockito.mockitoSession()
                        .strictness(Strictness.LENIENT)
                        .mockStatic(LocalManagerRegistry.class)
                        .mockStatic(SdkSandboxStatsLog.class)
                        .spyStatic(Process.class)
                        .initMocks(this);
        if (SdkLevel.isAtLeastU()) {
            mockitoSessionBuilder =
                    mockitoSessionBuilder.mockStatic(ActivityInterceptorCallbackRegistry.class);
        }
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

        // Required to access <sdk-library> information and DeviceConfig update.
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.ACCESS_SHARED_LIBRARIES,
                        Manifest.permission.INSTALL_PACKAGES,
                        Manifest.permission.READ_DEVICE_CONFIG,
                        Manifest.permission.WRITE_DEVICE_CONFIG,
                        // for Context#registerReceiverForAllUsers
                        Manifest.permission.INTERACT_ACROSS_USERS_FULL);
        mSdkSandboxService = Mockito.spy(FakeSdkSandboxService.class);
        mSdkSandboxService.setTimeValues(
                TIME_SYSTEM_SERVER_CALLED_SANDBOX,
                TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER,
                TIME_SANDBOX_CALLED_SDK,
                TIME_SDK_CALL_COMPLETED,
                TIME_SANDBOX_CALLED_SYSTEM_SERVER);
        sProvider = new FakeSdkSandboxProvider(mSdkSandboxService);

        // Populate LocalManagerRegistry
        mAmLocal = Mockito.mock(ActivityManagerLocal.class);
        ExtendedMockito.doReturn(mAmLocal)
                .when(() -> LocalManagerRegistry.getManager(ActivityManagerLocal.class));
        mPmLocal = Mockito.spy(PackageManagerLocal.class);

        sSdkSandboxPulledAtoms = Mockito.spy(new SdkSandboxPulledAtoms());

        String testDir = context.getDir("test_dir", Context.MODE_PRIVATE).getPath();
        mSdkSandboxStorageManager =
                new SdkSandboxStorageManager(
                        mSpyContext, new FakeSdkSandboxManagerLocal(), mPmLocal, testDir);
        mSdkSandboxStorageManagerUtility =
                new SdkSandboxStorageManagerUtility(mSdkSandboxStorageManager);

        mInjector = Mockito.spy(new InjectorForTest(mSpyContext, mSdkSandboxStorageManager));

        mService = new SdkSandboxManagerService(mSpyContext, mInjector);
        mService.forceEnableSandbox();
        sSdkSandboxManagerLocal = mService.getLocalManager();
        assertThat(sSdkSandboxManagerLocal).isNotNull();

        sSdkSandboxSettingsListener = mService.getSdkSandboxSettingsListener();
        assertThat(sSdkSandboxSettingsListener).isNotNull();

        mClientAppUid = Process.myUid();

        /** Save the initial value to reset the property to original configuration */
        mInitialApplyNextSdkSandboxRestrictions =
                DeviceConfig.getProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS);
        DeviceConfig.deleteProperty(
                DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS);

        mInitialEnforceRestrictions =
                DeviceConfig.getProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_ENFORCE_RESTRICTIONS);
        DeviceConfig.deleteProperty(
                DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_ENFORCE_RESTRICTIONS);

        mInitialServiceAllowlistValue =
                DeviceConfig.getProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_NEXT_SERVICE_ALLOWLIST);
        DeviceConfig.deleteProperty(
                DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_NEXT_SERVICE_ALLOWLIST);

        mInitialValueNextServiceAllowlist =
                DeviceConfig.getProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_NEXT_SERVICE_ALLOWLIST);
        DeviceConfig.deleteProperty(
                DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_NEXT_SERVICE_ALLOWLIST);

    }

    @After
    public void tearDown() {
        if (sSdkSandboxSettingsListener != null) {
            sSdkSandboxSettingsListener.unregisterPropertiesListener();
        }
        mStaticMockSession.finishMocking();

        resetDeviceConfigProperty(PROPERTY_ENFORCE_RESTRICTIONS, mInitialEnforceRestrictions);
        resetDeviceConfigProperty(PROPERTY_SERVICES_ALLOWLIST, mInitialServiceAllowlistValue);
        resetDeviceConfigProperty(
                PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS,
                mInitialApplyNextSdkSandboxRestrictions);
        resetDeviceConfigProperty(
                PROPERTY_NEXT_SERVICE_ALLOWLIST, mInitialValueNextServiceAllowlist);
    }

    private void resetDeviceConfigProperty(String property, String value) {
        if (Objects.isNull(value)) {
            DeviceConfig.deleteProperty(DeviceConfig.NAMESPACE_ADSERVICES, property);
        } else {
            DeviceConfig.setProperty(
                    DeviceConfig.NAMESPACE_ADSERVICES, property, value, /*makeDefault=*/ false);
        }
    }

    /** Mock the ActivityManager::killUid to avoid SecurityException thrown in test. **/
    private void disableKillUid() {
        Mockito.doNothing().when(mAmSpy).killUid(Mockito.anyInt(), Mockito.anyString());
    }

    private void disableForegroundCheck() {
        if (!mDisabledForegroundCheck) {
            Mockito.doReturn(IMPORTANCE_FOREGROUND).when(mAmSpy).getUidImportance(Mockito.anyInt());
            mDisabledForegroundCheck = true;
        }
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

    @Test
    public void testOnUserUnlocking() {
        UserInfo userInfo = new UserInfo(/*id=*/ 0, /*name=*/ "tempUser", /*flags=*/ 0);
        TargetUser user = new TargetUser(userInfo);

        SdkSandboxManagerService.Lifecycle lifeCycle =
                new SdkSandboxManagerService.Lifecycle(mSpyContext);
        lifeCycle.mService = Mockito.mock(SdkSandboxManagerService.class);
        lifeCycle.onUserUnlocking(user);
        Mockito.verify(lifeCycle.mService).onUserUnlocking(ArgumentMatchers.eq(/*userId=*/ 0));
    }

    @Test
    public void testSdkSandboxManagerIsRegistered() throws Exception {
        ServiceManager.getServiceOrThrow(SdkSandboxManager.SDK_SANDBOX_SERVICE);
    }

    @Test
    public void testRegisterAndGetAppOwnedSdkSandboxInterfaceSuccess() throws Exception {
        final IBinder iBinder = new Binder();

        mService.registerAppOwnedSdkSandboxInterface(
                TEST_PACKAGE,
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME,
                        /*version=*/ 0,
                        /*interfaceIBinder=*/ iBinder),
                TIME_APP_CALLED_SYSTEM_SERVER);
        final List<AppOwnedSdkSandboxInterface> appOwnedSdkSandboxInterfaceList =
                mService.getAppOwnedSdkSandboxInterfaces(
                        TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER);

        assertThat(appOwnedSdkSandboxInterfaceList).hasSize(1);
        assertThat(appOwnedSdkSandboxInterfaceList.get(0).getName())
                .isEqualTo(APP_OWNED_SDK_SANDBOX_INTERFACE_NAME);
        assertThat(appOwnedSdkSandboxInterfaceList.get(0).getVersion()).isEqualTo(0);
        assertThat(appOwnedSdkSandboxInterfaceList.get(0).getInterface()).isEqualTo(iBinder);
    }

    @Test
    public void testRegisterAppOwnedSdkSandboxInterfaceAlreadyRegistered() throws Exception {
        mService.registerAppOwnedSdkSandboxInterface(
                TEST_PACKAGE,
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME,
                        /*version=*/ 0,
                        /*interfaceIBinder=*/ new Binder()),
                TIME_APP_CALLED_SYSTEM_SERVER);

        assertThrows(
                IllegalStateException.class,
                () ->
                        mService.registerAppOwnedSdkSandboxInterface(
                                TEST_PACKAGE,
                                new AppOwnedSdkSandboxInterface(
                                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME,
                                        /*version=*/ 0,
                                        /*interfaceIBinder=*/ new Binder()),
                                TIME_APP_CALLED_SYSTEM_SERVER));
    }

    @Test
    public void testUnregisterAppOwnedSdkSandboxInterface() throws Exception {
        mService.registerAppOwnedSdkSandboxInterface(
                TEST_PACKAGE,
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME,
                        /*version=*/ 0,
                        /*interfaceIBinder=*/ new Binder()),
                TIME_APP_CALLED_SYSTEM_SERVER);
        mService.unregisterAppOwnedSdkSandboxInterface(
                TEST_PACKAGE, APP_OWNED_SDK_SANDBOX_INTERFACE_NAME, TIME_APP_CALLED_SYSTEM_SERVER);

        assertThat(
                        mService.getAppOwnedSdkSandboxInterfaces(
                                TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER))
                .hasSize(0);
    }

    @Test
    public void testLoadSdkIsSuccessful() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback);
        // Assume sdk sandbox loads successfully
        mSdkSandboxService.sendLoadCodeSuccessful();
        callback.assertLoadSdkIsSuccessful();
    }

    @Test
    public void testLoadSdkNonExistentCallingPackage() {
        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();

        mService.loadSdk(
                "does.not.exist",
                null,
                SDK_NAME,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback);

        LoadSdkException thrown = callback.getLoadSdkException();
        assertEquals(LOAD_SDK_INTERNAL_ERROR, thrown.getLoadSdkErrorCode());
        assertThat(thrown).hasMessageThat().contains("does.not.exist not found");
    }

    @Test
    public void testLoadSdkIncorrectCallingPackage() {
        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();

        mService.loadSdk(
                SDK_PROVIDER_PACKAGE,
                null,
                SDK_NAME,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback);

        LoadSdkException thrown = callback.getLoadSdkException();
        assertEquals(LOAD_SDK_INTERNAL_ERROR, thrown.getLoadSdkErrorCode());
        assertThat(thrown).hasMessageThat().contains("does not belong to uid");
    }

    @Test
    public void testLoadSdkPackageDoesNotExist() {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(
                TEST_PACKAGE,
                null,
                "does.not.exist",
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback);

        // Verify loading failed
        callback.assertLoadSdkIsUnsuccessful();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_NOT_FOUND);
        assertThat(callback.getLoadSdkErrorMsg()).contains("not found for loading");
    }

    @Test
    public void testLoadSdk_errorFromSdkSandbox() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();

        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback);
        mSdkSandboxService.sendLoadCodeError();

        // Verify loading failed
        callback.assertLoadSdkIsUnsuccessful();
        assertThat(callback.getLoadSdkErrorCode()).isEqualTo(LOAD_SDK_INTERNAL_ERROR);
    }

    @Test
    public void testLoadSdk_errorNoInternet() throws Exception {
        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();

        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback);

        LoadSdkException thrown = callback.getLoadSdkException();
        assertEquals(LOAD_SDK_INTERNAL_ERROR, thrown.getLoadSdkErrorCode());
        assertThat(thrown).hasMessageThat().contains(android.Manifest.permission.INTERNET);
    }

    @Test
    public void testLoadSdk_errorNoAccessNetworkState() throws Exception {
        // Stub out internet permission check
        Mockito.doNothing().when(mSpyContext).enforceCallingPermission(
                Mockito.eq("android.permission.INTERNET"), Mockito.anyString());

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();

        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback);

        LoadSdkException thrown = callback.getLoadSdkException();
        assertEquals(LOAD_SDK_INTERNAL_ERROR, thrown.getLoadSdkErrorCode());
        assertThat(thrown).hasMessageThat().contains(
                android.Manifest.permission.ACCESS_NETWORK_STATE);
    }

    @Test
    public void testLoadSdk_successOnFirstLoad_errorOnLoadAgain() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        // Load it once
        {
            FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
            mService.loadSdk(
                    TEST_PACKAGE,
                    null,
                    SDK_NAME,
                    TIME_APP_CALLED_SYSTEM_SERVER,
                    new Bundle(),
                    callback);
            // Assume SupplementalProcess loads successfully
            mSdkSandboxService.sendLoadCodeSuccessful();
            callback.assertLoadSdkIsSuccessful();
        }

        // Load it again
        {
            FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
            mService.loadSdk(
                    TEST_PACKAGE,
                    null,
                    SDK_NAME,
                    TIME_APP_CALLED_SYSTEM_SERVER,
                    new Bundle(),
                    callback);
            // Verify loading failed
            callback.assertLoadSdkIsUnsuccessful();
            assertThat(callback.getLoadSdkErrorCode()).isEqualTo(
                    SdkSandboxManager.LOAD_SDK_ALREADY_LOADED);
            assertThat(callback.getLoadSdkErrorMsg()).contains("has been loaded already");
        }
    }

    @Test
    public void testLoadSdk_firstLoadPending_errorOnLoadAgainRequest() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        // Request to load the SDK, but do not complete loading it
        {
            FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
            mService.loadSdk(
                    TEST_PACKAGE,
                    null,
                    SDK_NAME,
                    TIME_APP_CALLED_SYSTEM_SERVER,
                    new Bundle(),
                    callback);
        }

        // Requesting to load the SDK while the first load is still pending should throw an error
        {
            FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
            mService.loadSdk(
                    TEST_PACKAGE,
                    null,
                    SDK_NAME,
                    TIME_APP_CALLED_SYSTEM_SERVER,
                    new Bundle(),
                    callback);
            // Verify loading failed
            callback.assertLoadSdkIsUnsuccessful();
            assertThat(callback.getLoadSdkErrorCode())
                    .isEqualTo(SdkSandboxManager.LOAD_SDK_ALREADY_LOADED);
            assertThat(callback.getLoadSdkErrorMsg()).contains("is currently being loaded");
        }
    }

    @Test
    public void testLoadSdk_errorOnFirstLoad_canBeLoadedAgain() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        // Load code, but make it fail
        {
            FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
            mService.loadSdk(
                    TEST_PACKAGE,
                    null,
                    SDK_NAME,
                    TIME_APP_CALLED_SYSTEM_SERVER,
                    new Bundle(),
                    callback);
            // Assume sdk load fails
            mSdkSandboxService.sendLoadCodeError();
            callback.assertLoadSdkIsUnsuccessful();
        }

        // Caller should be able to retry loading the code
        loadSdk(SDK_NAME);
    }

    @Test
    public void testLoadSdk_sandboxDiesInBetween() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        // Load an sdk
        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback);

        // Kill the sandbox before the SDK can call the callback
        killSandbox();

        callback.assertLoadSdkIsUnsuccessful();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.SDK_SANDBOX_PROCESS_NOT_AVAILABLE);
    }

    @Test
    public void testSdkCanBeLoadedAfterSandboxDeath() throws Exception {
        loadSdk(SDK_NAME);
        killSandbox();
        loadSdk(SDK_NAME);
    }

    @Test
    public void testLoadSdk_sandboxIsInitialized() throws Exception {
        loadSdk(SDK_NAME);

        // Verify that sandbox was initialized
        assertThat(mSdkSandboxService.getInitializationCount()).isEqualTo(1);
    }

    @Test
    public void testLoadSdk_sandboxIsInitialized_onlyOnce() throws Exception {
        loadSdk(SDK_NAME);
        loadSdk(SDK_PROVIDER_RESOURCES_SDK_NAME);

        // Verify that sandbox was initialized
        assertThat(mSdkSandboxService.getInitializationCount()).isEqualTo(1);
    }

    @Test
    public void testLoadSdk_sandboxIsInitializedAfterRestart() throws Exception {
        loadSdk(SDK_NAME);
        restartAndSetSandboxService();
        // Restarting creates and sets a new sandbox service. Verify that the new one has been
        // initialized.
        assertThat(mSdkSandboxService.getInitializationCount()).isEqualTo(1);
    }

    @Test
    public void testLoadSdk_sandboxInitializationFails() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();
        disableKillUid();

        mSdkSandboxService.failInitialization = true;

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback);

        // If initialization failed, the sandbox would be unbound.
        final CallingInfo callingInfo = new CallingInfo(Process.myUid(), TEST_PACKAGE);
        assertThat(sProvider.getSdkSandboxServiceForApp(callingInfo)).isNull();

        // Call binderDied() on the sandbox to apply the effects of sandbox death detection after
        // unbinding.
        killSandbox();

        mSdkSandboxService.failInitialization = false;
        // SDK loading should succeed afterwards.
        loadSdk(SDK_NAME);
        assertThat(sProvider.getSdkSandboxServiceForApp(callingInfo)).isNotNull();
    }

    @Test
    public void testLoadSdk_sdkDataPrepared_onlyOnce() throws Exception {
        loadSdk(SDK_NAME);
        loadSdk(SDK_PROVIDER_RESOURCES_SDK_NAME);

        // Verify that SDK data was prepared.
        Mockito.verify(mPmLocal, Mockito.times(1))
                .reconcileSdkData(
                        Mockito.nullable(String.class),
                        Mockito.anyString(),
                        Mockito.anyList(),
                        Mockito.anyInt(),
                        Mockito.anyInt(),
                        Mockito.anyInt(),
                        Mockito.anyString(),
                        Mockito.anyInt());
    }

    @Test
    public void testLoadSdk_sdkDataPreparedAfterSandboxRestart() throws Exception {
        loadSdk(SDK_NAME);
        restartAndSetSandboxService();

        // Verify that SDK data was prepared for the newly restarted sandbox.
        Mockito.verify(mPmLocal, Mockito.times(1))
                .reconcileSdkData(
                        Mockito.nullable(String.class),
                        Mockito.anyString(),
                        Mockito.anyList(),
                        Mockito.anyInt(),
                        Mockito.anyInt(),
                        Mockito.anyInt(),
                        Mockito.anyString(),
                        Mockito.anyInt());
    }

    @Test
    public void testRequestSurfacePackageSdkNotLoaded_SandboxExists() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback);
        // Assume SupplementalProcess loads successfully
        mSdkSandboxService.sendLoadCodeSuccessful();
        callback.assertLoadSdkIsSuccessful();

        // Trying to request package with not exist SDK packageName
        String sdkName = "invalid";
        FakeRequestSurfacePackageCallbackBinder surfacePackageCallback =
                new FakeRequestSurfacePackageCallbackBinder();
        mService.requestSurfacePackage(
                TEST_PACKAGE,
                sdkName,
                new Binder(),
                0,
                500,
                500,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                surfacePackageCallback);
        assertThat(surfacePackageCallback.isRequestSurfacePackageSuccessful()).isFalse();
        assertThat(surfacePackageCallback.getSurfacePackageErrorCode())
                .isEqualTo(SdkSandboxManager.REQUEST_SURFACE_PACKAGE_SDK_NOT_LOADED);
    }

    @Test
    public void testRequestSurfacePackageSdkNotLoaded_SandboxDoesNotExist() throws Exception {
        disableForegroundCheck();

        // Trying to request package when sandbox is not there
        String sdkName = "invalid";
        FakeRequestSurfacePackageCallbackBinder callback =
                new FakeRequestSurfacePackageCallbackBinder();
        mService.requestSurfacePackage(
                TEST_PACKAGE,
                sdkName,
                new Binder(),
                0,
                500,
                500,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback);
        assertThat(callback.isRequestSurfacePackageSuccessful()).isFalse();
        assertThat(callback.getSurfacePackageErrorCode())
                .isEqualTo(SdkSandboxManager.REQUEST_SURFACE_PACKAGE_SDK_NOT_LOADED);
    }

    @Test
    public void testRequestSurfacePackage() throws Exception {
        // 1. We first need to collect a proper sdkToken by calling loadCode
        loadSdk(SDK_NAME);

        // 2. Call request package with the retrieved sdkToken
        FakeRequestSurfacePackageCallbackBinder surfacePackageCallback =
                new FakeRequestSurfacePackageCallbackBinder();
        mService.requestSurfacePackage(
                TEST_PACKAGE,
                SDK_NAME,
                new Binder(),
                0,
                500,
                500,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                surfacePackageCallback);
        mSdkSandboxService.sendSurfacePackageReady(new SandboxLatencyInfo());
        assertThat(surfacePackageCallback.isRequestSurfacePackageSuccessful()).isTrue();
    }

    @Test
    public void testRequestSurfacePackageFailedAfterAppDied() throws Exception {
        disableKillUid();
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        FakeLoadSdkCallbackBinder callback = Mockito.spy(new FakeLoadSdkCallbackBinder());
        Mockito.doReturn(Mockito.mock(Binder.class)).when(callback).asBinder();

        ArgumentCaptor<IBinder.DeathRecipient> deathRecipient =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);

        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback);
        mSdkSandboxService.sendLoadCodeSuccessful();
        callback.assertLoadSdkIsSuccessful();

        Mockito.verify(callback.asBinder())
                .linkToDeath(deathRecipient.capture(), ArgumentMatchers.eq(0));

        // App Died
        deathRecipient.getValue().binderDied();

        FakeRequestSurfacePackageCallbackBinder requestSurfacePackageCallback =
                new FakeRequestSurfacePackageCallbackBinder();
        mService.requestSurfacePackage(
                TEST_PACKAGE,
                SDK_NAME,
                new Binder(),
                0,
                500,
                500,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                requestSurfacePackageCallback);
        assertThat(requestSurfacePackageCallback.isRequestSurfacePackageSuccessful()).isFalse();
        assertThat(requestSurfacePackageCallback.getSurfacePackageErrorCode())
                .isEqualTo(SdkSandboxManager.REQUEST_SURFACE_PACKAGE_SDK_NOT_LOADED);
    }

    @Test
    public void testSurfacePackageError() throws Exception {
        loadSdk(SDK_NAME);

        // Assume SurfacePackage encounters an error.
        FakeRequestSurfacePackageCallbackBinder surfacePackageCallback =
                new FakeRequestSurfacePackageCallbackBinder();
        mSdkSandboxService.sendSurfacePackageError(
                SdkSandboxManager.REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR,
                "bad surface",
                surfacePackageCallback);
        assertThat(surfacePackageCallback.getSurfacePackageErrorMsg()).contains("bad surface");
        assertThat(surfacePackageCallback.getSurfacePackageErrorCode())
                .isEqualTo(SdkSandboxManager.REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR);
    }

    @Test
    public void testRequestSurfacePackage_SandboxDiesInBetween() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        // Load an sdk
        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback);
        mSdkSandboxService.sendLoadCodeSuccessful();
        callback.assertLoadSdkIsSuccessful();

        // Request surface package from the SDK
        FakeRequestSurfacePackageCallbackBinder surfacePackageCallback =
                new FakeRequestSurfacePackageCallbackBinder();
        mService.requestSurfacePackage(
                TEST_PACKAGE,
                SDK_NAME,
                new Binder(),
                0,
                500,
                500,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                surfacePackageCallback);

        // Kill the sandbox before the SDK can call the callback
        killSandbox();

        assertThat(surfacePackageCallback.isRequestSurfacePackageSuccessful()).isFalse();
        assertThat(surfacePackageCallback.getSurfacePackageErrorCode())
                .isEqualTo(SdkSandboxManager.REQUEST_SURFACE_PACKAGE_SDK_NOT_LOADED);
    }

    @Test
    public void testAddSdkSandboxProcessDeathCallback_BeforeStartingSandbox() throws Exception {
        // Register for sandbox death event
        FakeSdkSandboxProcessDeathCallbackBinder lifecycleCallback =
                new FakeSdkSandboxProcessDeathCallbackBinder();
        mService.addSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER, lifecycleCallback);

        // Load SDK and start the sandbox
        loadSdk(SDK_NAME);

        killSandbox();

        // Check that death is recorded correctly
        assertThat(lifecycleCallback.waitForSandboxDeath()).isTrue();
    }

    @Test
    public void testAddSdkSandboxProcessDeathCallback_AfterStartingSandbox() throws Exception {
        // Load SDK and start the sandbox
        loadSdk(SDK_NAME);

        // Register for sandbox death event
        FakeSdkSandboxProcessDeathCallbackBinder lifecycleCallback =
                new FakeSdkSandboxProcessDeathCallbackBinder();
        mService.addSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER, lifecycleCallback);

        killSandbox();

        // Check that death is recorded correctly
        assertThat(lifecycleCallback.waitForSandboxDeath()).isTrue();
    }

    @Test
    public void testSdkSandboxProcessDeathCallback_AfterRestartingSandbox() throws Exception {
        loadSdk(SDK_NAME);

        // Register for sandbox death event
        FakeSdkSandboxProcessDeathCallbackBinder lifecycleCallback1 =
                new FakeSdkSandboxProcessDeathCallbackBinder();
        mService.addSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER, lifecycleCallback1);
        killSandbox();
        assertThat(lifecycleCallback1.waitForSandboxDeath()).isTrue();

        restartAndSetSandboxService();

        // Register for sandbox death event again and verify that death is detected.
        FakeSdkSandboxProcessDeathCallbackBinder lifecycleCallback2 =
                new FakeSdkSandboxProcessDeathCallbackBinder();
        mService.addSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER, lifecycleCallback2);
        assertThat(lifecycleCallback2.waitForSandboxDeath()).isFalse();
        killSandbox();
        assertThat(lifecycleCallback2.waitForSandboxDeath()).isTrue();
    }

    @Test
    public void testMultipleAddSdkSandboxProcessDeathCallbacks() throws Exception {
        // Load SDK and start the sandbox
        loadSdk(SDK_NAME);

        // Register for sandbox death event
        FakeSdkSandboxProcessDeathCallbackBinder lifecycleCallback1 =
                new FakeSdkSandboxProcessDeathCallbackBinder();
        mService.addSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER, lifecycleCallback1);

        // Register for sandbox death event again
        FakeSdkSandboxProcessDeathCallbackBinder lifecycleCallback2 =
                new FakeSdkSandboxProcessDeathCallbackBinder();
        mService.addSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER, lifecycleCallback2);

        killSandbox();

        // Check that death is recorded correctly
        assertThat(lifecycleCallback1.waitForSandboxDeath()).isTrue();
        assertThat(lifecycleCallback2.waitForSandboxDeath()).isTrue();
    }

    @Test
    public void testRemoveSdkSandboxProcessDeathCallback() throws Exception {
        // Load SDK and start the sandbox
        loadSdk(SDK_NAME);

        // Register for sandbox death event
        FakeSdkSandboxProcessDeathCallbackBinder lifecycleCallback1 =
                new FakeSdkSandboxProcessDeathCallbackBinder();
        mService.addSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER, lifecycleCallback1);

        // Register for sandbox death event again
        FakeSdkSandboxProcessDeathCallbackBinder lifecycleCallback2 =
                new FakeSdkSandboxProcessDeathCallbackBinder();
        mService.addSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER, lifecycleCallback2);

        // Unregister one of the lifecycle callbacks
        mService.removeSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER, lifecycleCallback1);

        killSandbox();

        // Check that death is recorded correctly
        assertThat(lifecycleCallback1.waitForSandboxDeath()).isFalse();
        assertThat(lifecycleCallback2.waitForSandboxDeath()).isTrue();
    }

    @Test
    public void testSdkSandboxServiceUnbindingWhenAppDied() throws Exception {
        disableKillUid();
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        ILoadSdkCallback.Stub callback = Mockito.spy(ILoadSdkCallback.Stub.class);
        int callingUid = Binder.getCallingUid();
        final CallingInfo callingInfo = new CallingInfo(callingUid, TEST_PACKAGE);
        assertThat(sProvider.getSdkSandboxServiceForApp(callingInfo)).isNull();

        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback);

        ArgumentCaptor<IBinder.DeathRecipient> deathRecipient = ArgumentCaptor
                .forClass(IBinder.DeathRecipient.class);
        Mockito.verify(callback.asBinder(), Mockito.times(1))
                .linkToDeath(deathRecipient.capture(), Mockito.eq(0));

        assertThat(sProvider.getSdkSandboxServiceForApp(callingInfo)).isNotNull();
        deathRecipient.getValue().binderDied();
        assertThat(sProvider.getSdkSandboxServiceForApp(callingInfo)).isNull();
    }

    /** Tests that only allowed intents may be sent from the sdk sandbox. */
    @Test
    public void testEnforceAllowedToSendBroadcast() {
        Intent disallowedIntent = new Intent(Intent.ACTION_SCREEN_ON);
        assertThrows(
                SecurityException.class,
                () -> sSdkSandboxManagerLocal.enforceAllowedToSendBroadcast(disallowedIntent));
    }

    /** Tests that no broadcast can be sent from the sdk sandbox. */
    @Test
    public void testCanSendBroadcast() {
        assertThat(sSdkSandboxManagerLocal.canSendBroadcast(new Intent())).isFalse();
    }

    /** Tests that only allowed activities may be started from the sdk sandbox. */
    @Test
    public void testEnforceAllowedToStartActivity_allowedValues() {
        final ArrayList<String> allowedActions =
                new ArrayList<>(
                        Arrays.asList(
                                Intent.ACTION_VIEW,
                                Intent.ACTION_DIAL,
                                Intent.ACTION_EDIT,
                                Intent.ACTION_INSERT));

        for (String action : allowedActions) {
            final Intent allowedIntent = new Intent(action);
            sSdkSandboxManagerLocal.enforceAllowedToStartActivity(allowedIntent);
        }

        final Intent intentWithoutAction = new Intent();
        sSdkSandboxManagerLocal.enforceAllowedToStartActivity(intentWithoutAction);

        final Intent disallowedIntent = new Intent(Intent.ACTION_SCREEN_OFF);
        assertThrows(
                SecurityException.class,
                () -> sSdkSandboxManagerLocal.enforceAllowedToStartActivity(disallowedIntent));
    }

    @Test
    public void testEnforceAllowedToStartActivity_restrictionsNotEnforced() {
        sSdkSandboxSettingsListener.onPropertiesChanged(
                new DeviceConfig.Properties(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        Map.of(PROPERTY_ENFORCE_RESTRICTIONS, "false")));
        sSdkSandboxManagerLocal.enforceAllowedToStartActivity(new Intent());
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivityFailIfCalledFromSandboxUid()
            throws RemoteException {
        loadSdk(SDK_NAME);

        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () -> {
                            sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                                    new Intent(),
                                    Process.toSdkSandboxUid(Process.myUid()),
                                    TEST_PACKAGE);
                        });
        assertEquals(
                "Sandbox process is not allowed to start sandbox activities.",
                exception.getMessage());
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivityFailForNullIntents() {
        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () -> {
                            sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                                    null, Process.myUid(), TEST_PACKAGE);
                        });
        assertEquals("Intent to start sandbox activity is null.", exception.getMessage());
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivityFailForNullActions() {
        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () -> {
                            sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                                    new Intent(), Process.myUid(), TEST_PACKAGE);
                        });
        assertEquals(
                "Sandbox activity intent must have an action ("
                        + ACTION_START_SANDBOXED_ACTIVITY
                        + ").",
                exception.getMessage());
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivityFailForWrongAction() {
        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () -> {
                            sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                                    new Intent().setAction(Intent.ACTION_VIEW),
                                    Process.myUid(),
                                    TEST_PACKAGE);
                        });
        assertEquals(
                "Sandbox activity intent must have an action ("
                        + ACTION_START_SANDBOXED_ACTIVITY
                        + ").",
                exception.getMessage());
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivityFailForNullPackage() {
        Intent intent = new Intent().setAction(ACTION_START_SANDBOXED_ACTIVITY);
        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () -> {
                            sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                                    intent, Process.myUid(), TEST_PACKAGE);
                        });
        assertEquals(
                "Sandbox activity intent's package must be set to the sandbox package",
                exception.getMessage());
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivityFailForIntentsTargetingOtherPackages() {
        Intent intent =
                new Intent()
                        .setAction(ACTION_START_SANDBOXED_ACTIVITY)
                        .setPackage("com.random.package");
        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () -> {
                            sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                                    intent, Process.myUid(), TEST_PACKAGE);
                        });
        assertEquals(
                "Sandbox activity intent's package must be set to the sandbox package",
                exception.getMessage());
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivityFailForIntentsWithWrongComponent()
            throws Exception {
        loadSdk(SDK_NAME);

        Intent intent =
                new Intent()
                        .setAction(ACTION_START_SANDBOXED_ACTIVITY)
                        .setPackage(getSandboxPackageName())
                        .setComponent(new ComponentName("random", ""));
        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () -> {
                            sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                                    intent, Process.myUid(), TEST_PACKAGE);
                        });
        assertEquals(
                "Sandbox activity intent's component must refer to the sandbox package",
                exception.getMessage());
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivityFailIfNoSandboxProcees() {
        Intent intent = new Intent().setAction(ACTION_START_SANDBOXED_ACTIVITY);
        intent.setPackage(getSandboxPackageName());

        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () -> {
                            sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                                    intent, Process.myUid(), TEST_PACKAGE);
                        });
        assertEquals(
                "There is no sandbox process running for the caller uid: " + Process.myUid() + ".",
                exception.getMessage());
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivityFailIfIntentHasNoExtras()
            throws RemoteException {
        loadSdk(SDK_NAME);

        Intent intent = new Intent().setAction(ACTION_START_SANDBOXED_ACTIVITY);
        intent.setPackage(getSandboxPackageName());

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                                    intent, Process.myUid(), TEST_PACKAGE);
                        });
        assertEquals(
                "Intent should contain an extra params with key = "
                        + mService.getSandboxedActivityHandlerKey()
                        + " and value is an IBinder that identifies a registered "
                        + "SandboxedActivityHandler.",
                exception.getMessage());
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivityFailIfIntentHasNoHandlerExtra()
            throws RemoteException {
        loadSdk(SDK_NAME);

        Intent intent = new Intent().setAction(ACTION_START_SANDBOXED_ACTIVITY);
        intent.setPackage(getSandboxPackageName());
        intent.putExtras(new Bundle());

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                                    intent, Process.myUid(), TEST_PACKAGE);
                        });
        assertEquals(
                "Intent should contain an extra params with key = "
                        + mService.getSandboxedActivityHandlerKey()
                        + " and value is an IBinder that identifies a registered "
                        + "SandboxedActivityHandler.",
                exception.getMessage());
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivityFailIfIntentHasWrongTypeOfHandlerExtra()
            throws RemoteException {
        loadSdk(SDK_NAME);

        Intent intent = new Intent().setAction(ACTION_START_SANDBOXED_ACTIVITY);
        intent.setPackage(getSandboxPackageName());
        Bundle params = new Bundle();
        params.putString(mService.getSandboxedActivityHandlerKey(), "");
        intent.putExtras(params);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                                    intent, Process.myUid(), TEST_PACKAGE);
                        });
        assertEquals(
                "Intent should contain an extra params with key = "
                        + mService.getSandboxedActivityHandlerKey()
                        + " and value is an IBinder that identifies a registered "
                        + "SandboxedActivityHandler.",
                exception.getMessage());
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivitySuccessWithoutComponent()
            throws Exception {
        loadSdk(SDK_NAME);

        Intent intent = new Intent().setAction(ACTION_START_SANDBOXED_ACTIVITY);
        intent.setPackage(getSandboxPackageName());
        Bundle params = new Bundle();
        params.putBinder(mService.getSandboxedActivityHandlerKey(), new Binder());
        intent.putExtras(params);
        sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                intent, Process.myUid(), TEST_PACKAGE);
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivitySuccessWithComponentReferToSandboxPackage()
            throws Exception {
        loadSdk(SDK_NAME);

        Intent intent = new Intent().setAction(ACTION_START_SANDBOXED_ACTIVITY);
        intent.setPackage(getSandboxPackageName());
        intent.setComponent(new ComponentName(getSandboxPackageName(), ""));
        Bundle params = new Bundle();
        params.putBinder(mService.getSandboxedActivityHandlerKey(), new Binder());
        intent.putExtras(params);
        sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                intent, Process.myUid(), TEST_PACKAGE);
    }

    @Test
    public void testGetSdkSandboxProcessNameForInstrumentation() throws Exception {
        final PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        final ApplicationInfo info = pm.getApplicationInfo(TEST_PACKAGE, 0);
        final String processName =
                sSdkSandboxManagerLocal.getSdkSandboxProcessNameForInstrumentation(info);
        assertThat(processName).isEqualTo(TEST_PACKAGE + "_sdk_sandbox_instr");
    }

    /**
     * Tests expected behavior when restrictions are enabled and only protected broadcasts included.
     */
    @Test
    public void testCanRegisterBroadcastReceiver_deviceConfigUnsetProtectedBroadcasts() {
        ExtendedMockito.when(Process.isSdkSandboxUid(Mockito.anyInt())).thenReturn(true);
        assertThat(
                        sSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(Intent.ACTION_SCREEN_OFF),
                                /*flags= */ 0,
                                /*onlyProtectedBroadcasts= */ true))
                .isTrue();
    }

    /** Tests expected behavior when restrictions are enabled and no protected broadcast. */
    @Test
    public void testCanRegisterBroadcastReceiver_deviceConfigUnsetUnprotectedBroadcasts() {
        ExtendedMockito.when(Process.isSdkSandboxUid(Mockito.anyInt())).thenReturn(true);
        assertThat(
                        sSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(Intent.ACTION_SEND),
                                /*flags= */ 0,
                                /*onlyProtectedBroadcasts= */ false))
                .isFalse();
    }

    /** Tests expected behavior when broadcast receiver restrictions are not applied. */
    @Test
    public void testCanRegisterBroadcastReceiver_restrictionsNotApplied() {
        ExtendedMockito.when(Process.isSdkSandboxUid(Mockito.anyInt())).thenReturn(true);
        sSdkSandboxSettingsListener.onPropertiesChanged(
                new DeviceConfig.Properties(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        Map.of(PROPERTY_ENFORCE_RESTRICTIONS, "false")));
        assertThat(
                        sSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(Intent.ACTION_SEND),
                                /*flags= */ 0,
                                /*onlyProtectedBroadcasts= */ false))
                .isTrue();
    }

    /** Tests expected behavior when broadcast receiver restrictions are applied. */
    @Test
    public void testCanRegisterBroadcastReceiver_restrictionsApplied() {
        ExtendedMockito.when(Process.isSdkSandboxUid(Mockito.anyInt())).thenReturn(true);
        sSdkSandboxSettingsListener.onPropertiesChanged(
                new DeviceConfig.Properties(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        Map.of(PROPERTY_ENFORCE_RESTRICTIONS, "true")));
        assertThat(
                        sSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(Intent.ACTION_SEND),
                                /*flags= */ 0,
                                /*onlyProtectedBroadcasts= */ false))
                .isFalse();
    }

    /** Tests expected behavior when callingUid is not a sandbox UID. */
    @Test
    public void testCanRegisterBroadcastReceiver_notSandboxProcess() {
        assertThat(
                        sSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(Intent.ACTION_SEND),
                                /*flags= */ 0,
                                /*onlyProtectedBroadcasts= */ false))
                .isTrue();
    }

    /** Tests expected behavior when IntentFilter is blank. */
    @Test
    public void testCanRegisterBroadcastReceiver_blankIntentFilter() {
        ExtendedMockito.when(Process.isSdkSandboxUid(Mockito.anyInt())).thenReturn(true);
        assertThat(
                        sSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(),
                                /*flags= */ 0,
                                /*onlyProtectedBroadcasts= */ false))
                .isFalse();
    }

    /**
     * Tests expected behavior when broadcast receiver is registering a broadcast which contains
     * only protected broadcasts
     */
    @Test
    public void testCanRegisterBroadcastReceiver_protectedBroadcast() {
        ExtendedMockito.when(Process.isSdkSandboxUid(Mockito.anyInt())).thenReturn(true);
        sSdkSandboxSettingsListener.onPropertiesChanged(
                new DeviceConfig.Properties(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        Map.of(PROPERTY_ENFORCE_RESTRICTIONS, "true")));
        assertThat(
                        sSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(Intent.ACTION_SEND),
                                /*flags= */ Context.RECEIVER_NOT_EXPORTED,
                                /*onlyProtectedBroadcasts= */ true))
                .isTrue();
    }

    @Test
    public void testNotifyInstrumentationStarted_killsSandboxProcess() throws Exception {
        disableKillUid();

        // First load SDK.
        loadSdk(SDK_NAME);

        final CallingInfo callingInfo = new CallingInfo(Process.myUid(), TEST_PACKAGE);

        // Check that sdk sandbox for TEST_PACKAGE is bound
        assertThat(sProvider.getSdkSandboxServiceForApp(callingInfo)).isNotNull();

        sSdkSandboxManagerLocal.notifyInstrumentationStarted(TEST_PACKAGE, Process.myUid());

        // Verify that sdk sandbox was killed
        Mockito.verify(mAmSpy)
                .killUid(Mockito.eq(Process.toSdkSandboxUid(Process.myUid())), Mockito.anyString());
        assertThat(sProvider.getSdkSandboxServiceForApp(callingInfo)).isNull();
    }

    @Test
    public void testNotifyInstrumentationStarted_doesNotAllowLoadSdk() throws Exception {
        disableKillUid();

        // First load SDK.
        loadSdk(SDK_NAME);

        final CallingInfo callingInfo = new CallingInfo(Process.myUid(), TEST_PACKAGE);

        // Check that sdk sandbox for TEST_PACKAGE is bound
        assertThat(sProvider.getSdkSandboxServiceForApp(callingInfo)).isNotNull();

        sSdkSandboxManagerLocal.notifyInstrumentationStarted(TEST_PACKAGE, Process.myUid());
        assertThat(sProvider.getSdkSandboxServiceForApp(callingInfo)).isNull();

        // Try load again, it should throw SecurityException
        FakeLoadSdkCallbackBinder callback2 = new FakeLoadSdkCallbackBinder();

        mService.loadSdk(
                TEST_PACKAGE,
                callback2.asBinder(),
                SDK_NAME,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback2);

        LoadSdkException thrown = callback2.getLoadSdkException();
        assertEquals(LOAD_SDK_INTERNAL_ERROR, thrown.getLoadSdkErrorCode());
        assertThat(thrown)
                .hasMessageThat()
                .contains("Currently running instrumentation of this sdk sandbox process");
    }

    @Test
    public void testNotifyInstrumentationFinished_canLoadSdk() throws Exception {
        disableKillUid();
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        sSdkSandboxManagerLocal.notifyInstrumentationStarted(TEST_PACKAGE, Process.myUid());

        final CallingInfo callingInfo = new CallingInfo(Process.myUid(), TEST_PACKAGE);
        assertThat(sProvider.getSdkSandboxServiceForApp(callingInfo)).isNull();

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();

        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback);

        LoadSdkException thrown = callback.getLoadSdkException();
        assertEquals(LOAD_SDK_INTERNAL_ERROR, thrown.getLoadSdkErrorCode());
        assertThat(thrown)
                .hasMessageThat()
                .contains("Currently running instrumentation of this sdk sandbox process");

        sSdkSandboxManagerLocal.notifyInstrumentationFinished(TEST_PACKAGE, Process.myUid());

        FakeLoadSdkCallbackBinder callback2 = new FakeLoadSdkCallbackBinder();
        // Now loading should work
        mService.loadSdk(
                TEST_PACKAGE,
                callback2.asBinder(),
                SDK_NAME,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback2);
        mSdkSandboxService.sendLoadCodeSuccessful();
        callback2.assertLoadSdkIsSuccessful();
        assertThat(sProvider.getSdkSandboxServiceForApp(callingInfo)).isNotNull();
    }

    @Test
    public void testGetSandboxedSdks_afterLoadSdkSuccess() throws Exception {
        loadSdk(SDK_NAME);
        assertThat(mService.getSandboxedSdks(TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER))
                .hasSize(1);
        assertThat(
                        mService.getSandboxedSdks(TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER)
                                .get(0)
                                .getSharedLibraryInfo()
                                .getName())
                .isEqualTo(SDK_NAME);
    }

    @Test
    public void testGetSandboxedSdks_errorLoadingSdk() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();

        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback);
        mSdkSandboxService.sendLoadCodeError();

        // Verify sdkInfo is missing when loading failed
        callback.assertLoadSdkIsUnsuccessful();
        assertThat(callback.getLoadSdkErrorCode()).isEqualTo(LOAD_SDK_INTERNAL_ERROR);
        assertThat(mService.getSandboxedSdks(TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER))
                .isEmpty();
    }

    @Test
    public void testEnforceAllowedToStartOrBindService_disallowNonExistentPackage() {
        Intent intent = new Intent().setComponent(new ComponentName("nonexistent.package", "test"));
        assertThrows(
                SecurityException.class,
                () -> sSdkSandboxManagerLocal.enforceAllowedToStartOrBindService(intent));
    }

    @Test
    public void testEnforceAllowedToStartOrBindService_AdServicesApkNotPresent() throws Exception {
        String adServicesPackageName = mInjector.getAdServicesPackageName();
        Mockito.when(mInjector.getAdServicesPackageName()).thenReturn(null);
        Intent intent = new Intent().setComponent(new ComponentName(adServicesPackageName, "test"));
        assertThrows(
                SecurityException.class,
                () -> sSdkSandboxManagerLocal.enforceAllowedToStartOrBindService(intent));
    }

    @Test
    public void testEnforceAllowedToStartOrBindService_allowedPackages() throws Exception {
        Intent intent =
                new Intent()
                        .setComponent(
                                new ComponentName(mInjector.getAdServicesPackageName(), "test"));
        sSdkSandboxManagerLocal.enforceAllowedToStartOrBindService(intent);
    }

    @Test
    public void testServiceRestriction_noFieldsSet() {
        /**
         * Service allowlist
         * allowlist_per_target_sdk {
         *   key: 34
         *   value: {
         *     allowed_services: {
         *     }
         *   }
         * }
         */
        final String encodedServiceAllowlist = "CgYIIhICCgA=";
        setDeviceConfigProperty(PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);

        /** Allows all the services to start/ bind */
        assertThrows(
                SecurityException.class,
                () ->
                        testServiceRestriction(
                                /*action=*/ null,
                                /*packageName=*/ null,
                                /*componentClassName=*/ null,
                                /*componentPackageName=*/ null));
    }

    @Test
    public void testServiceRestriction_oneFieldSet() {
        /**
         * Service allowlist
         * allowlist_per_target_sdk {
         *   key: 34
         *   value: {
         *     allowed_services: {
         *       action : "*"
         *       packageName : "packageName.test"
         *       componentClassName : "*"
         *       componentPackageName : "*"
         *     }
         *     allowed_services: {
         *       action : "*"
         *       packageName : "*"
         *       componentClassName : "className.test"
         *       componentPackageName : "*"
         *     }
         *     allowed_services: {
         *       action : "action.test"
         *       packageName : "*"
         *       componentClassName : "*"
         *       componentPackageName : "*"
         *     }
         *     allowed_services: {
         *       action : "*"
         *       packageName : "*"
         *       componentClassName : "*"
         *       componentPackageName : "componentPackageName.test"
         *     }
         *   }
         * }
         */
        final String encodedServiceAllowlist =
                "CnoIIhJ2ChsKASoSEHBhY2thZ2VOYW1lLnRlc3QaASoiASoKGQoBKhIBKhoOY2xhc3NOYW1lLnRlc3QiA"
                    + "SoKFgoLYWN0aW9uLnRlc3QSASoaASoiASoKJAoBKhIBKhoBKiIZY29tcG9uZW50UGFja2FnZU5h"
                    + "bWUudGVzdA==";
        setDeviceConfigProperty(PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);

        testServiceRestriction(
                /*action=*/ INTENT_ACTION,
                /*packageName=*/ null,
                /*componentClassName=*/ null,
                /*componentPackageName=*/ null);

        testServiceRestriction(
                /*action=*/ null,
                /*packageName=*/ PACKAGE_NAME,
                /*componentClassName=*/ null,
                /*componentPackageName=*/ null);

        testServiceRestriction(
                /*action=*/ null,
                /*packageName=*/ null,
                /*componentClassName=*/ COMPONENT_CLASS_NAME,
                /*componentPackageName=*/ null);

        assertThrows(
                SecurityException.class,
                () ->
                        testServiceRestriction(
                                /*action=*/ null,
                                /*packageName=*/ null,
                                /*componentClassName=*/ null,
                                /*componentPackageName=*/ null));
    }

    @Test
    public void testServiceRestriction_twoFieldsSet() {
        /**
         * Service allowlist
         * allowlist_per_target_sdk {
         *   key: 34
         *   value: {
         *     allowed_services: {
         *       action : "action.test"
         *       packageName : "packageName.test"
         *       componentClassName : "*"
         *       componentPackageName : "*"
         *     }
         *     allowed_services: {
         *       action : "action.test"
         *       packageName : "*"
         *       componentClassName : "className.test"
         *       componentPackageName : "*"
         *     }
         *     allowed_services: {
         *       action : "*"
         *       packageName : "packageName.test"
         *       componentClassName : "className.test"
         *       componentPackageName : "*"
         *     }
         *   }
         * }
         */
        final String encodedServiceAllowlist =
                "CnoIIhJ2CiUKC2FjdGlvbi50ZXN0EhBwYWNrYWdlTmFtZS50ZXN0GgEqIgEqCiMKC2FjdGlvbi50ZXN0Eg"
                    + "EqGg5jbGFzc05hbWUudGVzdCIBKgooCgEqEhBwYWNrYWdlTmFtZS50ZXN0Gg5jbGFzc05hbWUud"
                    + "GVzdCIBKg==";
        setDeviceConfigProperty(PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);

        testServiceRestriction(
                /*action=*/ INTENT_ACTION,
                /*packageName=*/ PACKAGE_NAME,
                /*componentClassName=*/ null,
                /*componentPackageName=*/ null);

        testServiceRestriction(
                /*action=*/ INTENT_ACTION,
                /*packageName=*/ null,
                /*componentClassName=*/ COMPONENT_CLASS_NAME,
                /*componentPackageName=*/ null);

        testServiceRestriction(
                /*action=*/ null,
                /*packageName=*/ PACKAGE_NAME,
                /*componentClassName=*/ COMPONENT_CLASS_NAME,
                /*componentPackageName=*/ null);

        assertThrows(
                SecurityException.class,
                () ->
                        testServiceRestriction(
                                /*action=*/ null,
                                /*packageName=*/ null,
                                /*componentClassName=*/ null,
                                /*componentPackageName=*/ null));

        assertThrows(
                SecurityException.class,
                () ->
                        testServiceRestriction(
                                /*action=*/ INTENT_ACTION,
                                /*packageName=*/ null,
                                /*componentClassName=*/ null,
                                /*componentPackageName=*/ null));
    }

    @Test
    public void testServiceRestriction_threeFieldsSet() {
        /**
         * Service allowlist
         * allowlist_per_target_sdk {
         *   key: 34
         *   value: {
         *     allowed_services: {
         *       action : "action.test"
         *       packageName : "packageName.test"
         *       componentClassName : "className.test"
         *       componentPackageName : "*"
         *     }
         *     allowed_services: {
         *       action : "action.test"
         *       packageName : "packageName.test"
         *       componentClassName : "*"
         *       componentPackageName : "componentPackageName.test"
         *     }
         *     allowed_services: {
         *       action : "action.test"
         *       packageName : "*"
         *       componentClassName : "className.test"
         *       componentPackageName : "componentPackageName.test"
         *     }
         *     allowed_services: {
         *       action : "*"
         *       packageName : "packageName.test"
         *       componentClassName : "className.test"
         *       componentPackageName : "componentPackageName.test"
         *     }
         *   }
         * }
         */
        final String encodedServiceAllowlist =
                "CvcBCCIS8gEKMgoLYWN0aW9uLnRlc3QSEHBhY2thZ2VOYW1lLnRlc3QaDmNsYXNzTmFtZS50ZXN0IgEqCj"
                    + "0KC2FjdGlvbi50ZXN0EhBwYWNrYWdlTmFtZS50ZXN0GgEqIhljb21wb25lbnRQYWNrYWdlTmFtZ"
                    + "S50ZXN0CjsKC2FjdGlvbi50ZXN0EgEqGg5jbGFzc05hbWUudGVzdCIZY29tcG9uZW50UGFja2Fn"
                    + "ZU5hbWUudGVzdApACgEqEhBwYWNrYWdlTmFtZS50ZXN0Gg5jbGFzc05hbWUudGVzdCIZY29tcG9"
                    + "uZW50UGFja2FnZU5hbWUudGVzdA==";
        setDeviceConfigProperty(PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);

        testServiceRestriction(
                /*action=*/ INTENT_ACTION,
                /*packageName=*/ PACKAGE_NAME,
                /*componentClassName=*/ COMPONENT_CLASS_NAME,
                /*componentPackageName=*/ null);

        testServiceRestriction(
                /*action=*/ INTENT_ACTION,
                /*packageName=*/ PACKAGE_NAME,
                /*componentClassName=*/ null,
                /*componentPackageName=*/ COMPONENT_PACKAGE_NAME);

        testServiceRestriction(
                /*action=*/ INTENT_ACTION,
                /*packageName=*/ null,
                /*componentClassName=*/ COMPONENT_CLASS_NAME,
                /*componentPackageName=*/ COMPONENT_PACKAGE_NAME);

        testServiceRestriction(
                /*action=*/ null,
                /*packageName=*/ PACKAGE_NAME,
                /*componentClassName=*/ COMPONENT_CLASS_NAME,
                /*componentPackageName=*/ COMPONENT_PACKAGE_NAME);

        assertThrows(
                SecurityException.class,
                () ->
                        testServiceRestriction(
                                /*action=*/ INTENT_ACTION,
                                /*packageName=*/ null,
                                /*componentClassName=*/ null,
                                /*componentPackageName=*/ null));
    }

    @Test
    public void testServiceRestriction_multipleEntriesAllowlist() {
        /**
         * Service allowlist
         * allowlist_per_target_sdk {
         *   key: 34
         *   value: {
         *     allowed_services: {
         *       action : "action.test1"
         *       packageName : "packageName.test1"
         *       componentClassName : "className.test1"
         *       componentPackageName : "componentPackageName.test1"
         *     }
         *     allowed_services: {
         *       action : "action.test2"
         *       packageName : "packageName.test2"
         *       componentClassName : "className.test2"
         *       componentPackageName : "componentPackageName.test2"
         *     }
         *   }
         * }
         */
        final String encodedServiceAllowlist =
                "CqUBCCISoAEKTgoMYWN0aW9uLnRlc3QxEhFwYWNrYWdlTmFtZS50ZXN0MRoPY2xhc3NOYW1lLnRlc3QxI"
                    + "hpjb21wb25lbnRQYWNrYWdlTmFtZS50ZXN0MQpOCgxhY3Rpb24udGVzdDISEXBhY2thZ2VOYW1l"
                    + "LnRlc3QyGg9jbGFzc05hbWUudGVzdDIiGmNvbXBvbmVudFBhY2thZ2VOYW1lLnRlc3Qy";
        setDeviceConfigProperty(PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);

        testServiceRestriction(
                /*action=*/ "action.test1",
                /*packageName=*/ "packageName.test1",
                /*componentClassName=*/ "className.test1",
                /*componentPackageName=*/ "componentPackageName.test1");
    }

    @Test
    public void testServiceRestrictions_DeviceConfigNextAllowlistApplied() throws Exception {
        setDeviceConfigProperty(PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS, "true");
        /**
         * Service allowlist
         * allowlist_per_target_sdk {
         *   key: 34
         *   value: {
         *     allowed_services: {
         *       action : "action.test"
         *       packageName : "packageName.test"
         *       componentClassName : "className.test"
         *       componentPackageName : "*"
         *     }
         *   }
         * }
         */
        final String encodedServiceAllowlist =
                "CjgIIhI0CjIKC2FjdGlvbi50ZXN0EhBwYWNrYWdlTmFtZS50ZXN0Gg5jbGFzc05hbWUudGVzdCIBKg==";
        setDeviceConfigProperty(PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);

        /**
         * Service allowlist
         * allowed_services {
         *   action : "action.next"
         *   packageName : "packageName.next"
         *   componentClassName : "className.next"
         *   componentPackageName : "*"
         * }
         */
        final String encodedNextServiceAllowlist =
                "CjIKC2FjdGlvbi5uZXh0EhBwYWNrYWdlTmFtZS5uZXh0Gg5jbGFzc05hbWUubmV4dCIBKg==";
        setDeviceConfigProperty(PROPERTY_NEXT_SERVICE_ALLOWLIST, encodedNextServiceAllowlist);

        testServiceRestriction(
                /*action=*/ "action.next",
                /*packageName=*/ "packageName.next",
                /*componentClassName=*/ "className.next",
                /*componentPackageName=*/ null);

        assertThrows(
                SecurityException.class,
                () ->
                        testServiceRestriction(
                                /*action=*/ "action.test",
                                /*packageName=*/ "packageName.test",
                                /*componentClassName=*/ "className.test",
                                /*componentPackageName=*/ null));
    }

    @Test
    public void testServiceRestrictions_ComponentNotSet() {
        /**
         * Service allowlist
         * allowlist_per_target_sdk {
         *   key: 34
         *   value: {
         *     allowed_services: {
         *       action : "action.test"
         *       packageName : "*"
         *       componentClassName : "*"
         *       componentPackageName: "*"
         *     }
         *   }
         * }
         */
        final String encodedServiceAllowlist = "ChwIIhIYChYKC2FjdGlvbi50ZXN0EgEqGgEqIgEq";
        setDeviceConfigProperty(PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);

        final Intent intent = new Intent(INTENT_ACTION);
        sSdkSandboxManagerLocal.enforceAllowedToStartOrBindService(intent);
    }
    @Test
    public void testServiceRestrictions_AllFieldsSetToWildcard() {
        /**
         * Service allowlist
         * allowlist_per_target_sdk {
         *   key: 34
         *   value: {
         *     allowed_services: {
         *       action : "*"
         *       packageName : "*"
         *       componentPackageName : "*"
         *       componentClassName : "*"
         *     }
         *   }
         * }
         */
        final String encodedServiceAllowlist = "ChIIIhIOCgwKASoSASoaASoiASo=";
        setDeviceConfigProperty(PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);

        testServiceRestriction(
                /*action=*/ INTENT_ACTION,
                /*packageName=*/ COMPONENT_PACKAGE_NAME,
                /*componentClassName=*/ COMPONENT_CLASS_NAME,
                /*componentPackageName=*/ COMPONENT_PACKAGE_NAME);

        testServiceRestriction(
                /*action=*/ null,
                /*packageName=*/ null,
                /*componentClassName=*/ null,
                /*componentPackageName=*/ null);

        testServiceRestriction(
                /*action=*/ INTENT_ACTION,
                /*packageName=*/ null,
                /*componentClassName=*/ null,
                /*componentPackageName=*/ null);
    }

    @Test
    public void testServiceRestrictions_AllFieldsSet() {
        /**Service allowlist
         * allowlist_per_target_sdk {
         *   key: 34
         *   value: {
         *     allowed_services: {
         *       action : "action.test"
         *       packageName : "packageName.test"
         *       componentClassName : "className.test"
         *       componentPackageName : "componentPackageName.test"
         *       }
         *     }
         * }
         */
        final String encodedServiceAllowlist =
                "ClAIIhJMCkoKC2FjdGlvbi50ZXN0EhBwYWNrYWdlTmFtZS50ZXN0Gg5jbGFzc05hbWUudGVzdCIZY29tc"
                        + "G9uZW50UGFja2FnZU5hbWUudGVzdA==";
        setDeviceConfigProperty(PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);
        testServiceRestriction(
                INTENT_ACTION, PACKAGE_NAME, COMPONENT_CLASS_NAME, COMPONENT_PACKAGE_NAME);
    }

    @Test
    public void testAdServicesPackageIsResolved() throws Exception {
        assertThat(mInjector.getAdServicesPackageName()).contains("adservices");
    }

    @Test
    public void testUnloadSdkThatIsNotLoaded() throws Exception {
        // Load SDK to bring up a sandbox
        loadSdk(SDK_NAME);
        // Trying to unload an SDK that is not loaded should do nothing - it's a no-op.
        mService.unloadSdk(TEST_PACKAGE, SDK_PROVIDER_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER);
    }

    @Test
    public void testUnloadSdkThatIsLoaded() throws Exception {
        disableKillUid();
        loadSdk(SDK_NAME);

        loadSdk(SDK_PROVIDER_RESOURCES_SDK_NAME);

        final CallingInfo callingInfo = new CallingInfo(Process.myUid(), TEST_PACKAGE);
        mService.unloadSdk(TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER);

        // One SDK should still be loaded, therefore the sandbox should still be alive.
        assertThat(sProvider.getSdkSandboxServiceForApp(callingInfo)).isNotNull();

        mService.unloadSdk(
                TEST_PACKAGE, SDK_PROVIDER_RESOURCES_SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER);

        // No more SDKs should be loaded at this point. Verify that the sandbox has been killed.
        if (!SdkLevel.isAtLeastU()) {
            // For T, killUid() is used to kill the sandbox.
            Mockito.verify(mAmSpy)
                    .killUid(
                            Mockito.eq(Process.toSdkSandboxUid(Process.myUid())),
                            Mockito.anyString());
        }
        assertThat(sProvider.getSdkSandboxServiceForApp(callingInfo)).isNull();
    }

    @Test
    public void testUnloadSdkThatIsBeingLoaded() throws Exception {
        // Ask to load SDK, but don't finish loading it
        disableKillUid();
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback);

        // Trying to unload an SDK that is being loaded should fail
        assertThrows(
                IllegalArgumentException.class,
                () -> mService.unloadSdk(TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER));

        // After loading the SDK, unloading should not fail
        mSdkSandboxService.sendLoadCodeSuccessful();
        callback.assertLoadSdkIsSuccessful();
        mService.unloadSdk(TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER);
    }

    @Test
    public void testUnloadSdkAfterKillingSandboxDoesNotThrowException() throws Exception {
        loadSdk(SDK_NAME);
        killSandbox();

        // Unloading SDK should be a no-op
        mService.unloadSdk(TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER);
    }

    @Test
    public void test_syncDataFromClient_verifiesCallingPackageName() {
        FakeSharedPreferencesSyncCallback callback = new FakeSharedPreferencesSyncCallback();
        mService.syncDataFromClient(
                "does.not.exist", TIME_APP_CALLED_SYSTEM_SERVER, TEST_UPDATE, callback);

        assertEquals(PREFERENCES_SYNC_INTERNAL_ERROR, callback.getErrorCode());
        assertThat(callback.getErrorMsg()).contains("does.not.exist not found");
    }

    @Test
    public void test_syncDataFromClient_sandboxServiceIsNotBound() {
        // Sync data from client
        final FakeSharedPreferencesSyncCallback callback = new FakeSharedPreferencesSyncCallback();
        mService.syncDataFromClient(
                TEST_PACKAGE,
                /*timeAppCalledSystemServer=*/ System.currentTimeMillis(),
                TEST_UPDATE,
                callback);

        // Verify when sandbox is not bound, manager service does not try to sync
        assertThat(mSdkSandboxService.getLastSyncUpdate()).isNull();
        // Verify on error was called
        assertThat(callback.hasError()).isTrue();
        assertThat(callback.getErrorCode())
                .isEqualTo(ISharedPreferencesSyncCallback.SANDBOX_NOT_AVAILABLE);
        assertThat(callback.getErrorMsg()).contains("Sandbox not available");
    }

    @Test
    public void test_syncDataFromClient_sandboxServiceIsNotBound_sandboxStartedLater()
            throws Exception {
        // Sync data from client
        final FakeSharedPreferencesSyncCallback callback = new FakeSharedPreferencesSyncCallback();
        mService.syncDataFromClient(
                TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER, TEST_UPDATE, callback);

        // Verify on error was called
        assertThat(callback.hasError()).isTrue();
        callback.resetLatch();

        // Now loadSdk so that sandbox is created
        loadSdk(SDK_NAME);

        // Verify that onSandboxStart was called
        assertThat(callback.hasSandboxStarted()).isTrue();
    }

    @Test
    public void test_syncDataFromClient_sandboxServiceIsAlreadyBound_forwardsToSandbox()
            throws Exception {
        // Ensure a sandbox service is already bound for the client
        final CallingInfo callingInfo = new CallingInfo(Process.myUid(), TEST_PACKAGE);
        sProvider.bindService(callingInfo, Mockito.mock(ServiceConnection.class));

        // Sync data from client
        final Bundle data = new Bundle();
        final FakeSharedPreferencesSyncCallback callback = new FakeSharedPreferencesSyncCallback();
        mService.syncDataFromClient(
                TEST_PACKAGE,
                /*timeAppCalledSystemServer=*/ System.currentTimeMillis(),
                TEST_UPDATE,
                callback);

        // Verify that manager service calls sandbox to sync data
        assertThat(mSdkSandboxService.getLastSyncUpdate()).isSameInstanceAs(TEST_UPDATE);
    }

    @Test
    public void testStopSdkSandbox() throws Exception {
        disableKillUid();
        loadSdk(SDK_NAME);

        Mockito.doNothing()
                .when(mSpyContext)
                .enforceCallingPermission(
                        Mockito.eq("com.android.app.sdksandbox.permission.STOP_SDK_SANDBOX"),
                        Mockito.anyString());
        mService.stopSdkSandbox(TEST_PACKAGE);
        int callingUid = Binder.getCallingUid();
        final CallingInfo callingInfo = new CallingInfo(callingUid, TEST_PACKAGE);
        assertThat(sProvider.getSdkSandboxServiceForApp(callingInfo)).isEqualTo(null);
    }

    @Test(expected = SecurityException.class)
    public void testStopSdkSandbox_WithoutPermission() {
        mService.stopSdkSandbox(TEST_PACKAGE);
    }

    @Test
    public void testDump_preU_notPublished() throws Exception {
        requiresAtLeastU(false);
        mockGrantedPermission(DUMP);
        mService.registerAdServicesManagerService(mAdServicesManager, /* published= */ false);

        String dump;
        try (StringWriter stringWriter = new StringWriter()) {
            // Mock call to mAdServicesManager.dump();
            FileDescriptor fd = new FileDescriptor();
            PrintWriter writer = new PrintWriter(stringWriter);
            String[] args = new String[0];
            Mockito.doAnswer(
                    (inv) -> {
                        writer.println("FakeAdServiceDump");
                        return null;
                    })
                    .when(mAdServicesManager)
                    .dump(fd, args);

            mService.dump(fd, writer, args);

            dump = stringWriter.toString();
        }

        assertThat(dump).contains("FakeDump");
        assertThat(dump).contains("FakeAdServiceDump");
    }

    @Test
    public void testDump_preU_published() throws Exception {
        requiresAtLeastU(false);
        mockGrantedPermission(DUMP);
        mService.registerAdServicesManagerService(mAdServicesManager, /* published= */ true);

        String dump;
        try (StringWriter stringWriter = new StringWriter()) {
            mService.dump(new FileDescriptor(), new PrintWriter(stringWriter), new String[0]);

            dump = stringWriter.toString();
        }

        assertThat(dump).contains("FakeDump");

        Mockito.verify(mAdServicesManager, Mockito.never())
                .dump(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    public void testDump_atLeastU_notPublished() throws Exception {
        requiresAtLeastU(true);
        mockGrantedPermission(DUMP);
        mService.registerAdServicesManagerService(mAdServicesManager, /* published= */ false);

        String dump;
        try (StringWriter stringWriter = new StringWriter()) {
            mService.dump(new FileDescriptor(), new PrintWriter(stringWriter), new String[0]);
            dump = stringWriter.toString();
        }

        assertThat(dump).contains("FakeDump");

        Mockito.verify(mAdServicesManager, Mockito.never())
                .dump(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    public void testDump_atLeastU_published() throws Exception {
        requiresAtLeastU(true);
        mockGrantedPermission(DUMP);
        mService.registerAdServicesManagerService(mAdServicesManager, /* published= */ true);

        String dump;
        try (StringWriter stringWriter = new StringWriter()) {
            mService.dump(new FileDescriptor(), new PrintWriter(stringWriter), new String[0]);
            dump = stringWriter.toString();
        }

        assertThat(dump).contains("FakeDump");

        Mockito.verify(mAdServicesManager, Mockito.never())
                .dump(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    public void testDump_adServices_preU_notPublished() throws Exception {
        requiresAtLeastU(false);
        mockGrantedPermission(DUMP);
        mService.registerAdServicesManagerService(mAdServicesManager, /* published= */ false);

        String dump;
        try (StringWriter stringWriter = new StringWriter()) {
            // Mock call to mAdServicesManager.dump();
            FileDescriptor fd = new FileDescriptor();
            PrintWriter writer = new PrintWriter(stringWriter);
            String[] args = new String[] {"--AdServices"};
            Mockito.doAnswer(
                    (inv) -> {
                        writer.println("FakeAdServiceDump");
                        return null;
                    })
                    .when(mAdServicesManager)
                    .dump(fd, args);

            mService.dump(fd, writer, args);

            dump = stringWriter.toString();
        }

        assertThat(dump).isEqualTo("AdServices:\n\nFakeAdServiceDump\n\n");
    }

    @Test
    public void testDump_adServices_preU_published() throws Exception {
        requiresAtLeastU(false);
        mockGrantedPermission(DUMP);
        mService.registerAdServicesManagerService(mAdServicesManager, /* published= */ true);

        String dump;
        try (StringWriter stringWriter = new StringWriter()) {
            mService.dump(
                    new FileDescriptor(),
                    new PrintWriter(stringWriter),
                    new String[] {"--AdServices"});
            dump = stringWriter.toString();
        }

        assertThat(dump)
                .isEqualTo(
                        SdkSandboxManagerService
                                        .DUMP_AD_SERVICES_MESSAGE_HANDLED_BY_AD_SERVICES_ITSELF
                                + "\n");
        Mockito.verify(mAdServicesManager, Mockito.never())
                .dump(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    public void testDump_adServices_atLeastU_notPublished() throws Exception {
        requiresAtLeastU(true);
        mockGrantedPermission(DUMP);
        mService.registerAdServicesManagerService(mAdServicesManager, /* published= */ false);

        String dump;
        try (StringWriter stringWriter = new StringWriter()) {
            mService.dump(
                    new FileDescriptor(),
                    new PrintWriter(stringWriter),
                    new String[] {"--AdServices"});
            dump = stringWriter.toString();
        }

        assertThat(dump)
                .isEqualTo(
                        SdkSandboxManagerService.DUMP_AD_SERVICES_MESSAGE_HANDLED_BY_SYSTEM_SERVICE
                                + "\n");

        Mockito.verify(mAdServicesManager, Mockito.never())
                .dump(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    public void testDump_adServices_atLeastU_published() throws Exception {
        requiresAtLeastU(true);
        mockGrantedPermission(DUMP);
        mService.registerAdServicesManagerService(mAdServicesManager, /* published= */ true);

        String dump;
        try (StringWriter stringWriter = new StringWriter()) {
            mService.dump(
                    new FileDescriptor(),
                    new PrintWriter(stringWriter),
                    new String[] {"--AdServices"});
            dump = stringWriter.toString();
        }

        assertThat(dump)
                .isEqualTo(
                        SdkSandboxManagerService
                                        .DUMP_AD_SERVICES_MESSAGE_HANDLED_BY_AD_SERVICES_ITSELF
                                + "\n");

        Mockito.verify(mAdServicesManager, Mockito.never())
                .dump(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test(expected = SecurityException.class)
    public void testDump_WithoutPermission() {
        mService.dump(new FileDescriptor(), new PrintWriter(new StringWriter()), new String[0]);
    }

    @Test
    public void testHandleShellCommandExecutesCommand() {
        final FileDescriptor in = FileDescriptor.in;
        final FileDescriptor out = FileDescriptor.out;
        final FileDescriptor err = FileDescriptor.err;

        final SdkSandboxShellCommand command = Mockito.mock(SdkSandboxShellCommand.class);
        Mockito.when(mInjector.createShellCommand(mService, mSpyContext)).thenReturn(command);

        final String[] args = new String[] {"start"};

        mService.handleShellCommand(
                new ParcelFileDescriptor(in),
                new ParcelFileDescriptor(out),
                new ParcelFileDescriptor(err),
                args);

        Mockito.verify(mInjector).createShellCommand(mService, mSpyContext);
        Mockito.verify(command).exec(mService, in, out, err, args);
    }

    @Test
    public void testLatencyMetrics_IpcFromAppToSystemServer_RegisterAppOwnedSdkSandboxInterface()
            throws Exception {
        mService.registerAppOwnedSdkSandboxInterface(
                TEST_PACKAGE,
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME,
                        /*version=*/ 0,
                        /*interfaceIBinder=*/ new Binder()),
                TIME_APP_CALLED_SYSTEM_SERVER);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                (int)
                                        (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP
                                                - TIME_APP_CALLED_SYSTEM_SERVER),
                                /*success=*/ true,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER,
                                mClientAppUid));
    }

    @Test
    public void testLatencyMetrics_IpcFromAppToSystemServer_UnregisterAppOwnedSdkSandboxInterface()
            throws Exception {
        mService.unregisterAppOwnedSdkSandboxInterface(
                TEST_PACKAGE, APP_OWNED_SDK_SANDBOX_INTERFACE_NAME, TIME_APP_CALLED_SYSTEM_SERVER);

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
    }

    @Test
    public void testLatencyMetrics_IpcFromAppToSystemServer_GetAppOwnedSdkSandboxInterfaces()
            throws Exception {
        mService.getAppOwnedSdkSandboxInterfaces(TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER);

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
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER,
                                mClientAppUid));
    }

    @Test
    public void
            testLatencyMetrics_SystemServerAppToSandbox_RegisterAppOwnedSdkSandboxInterface_NoFailure()
                    throws Exception {
        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_SYSTEM_SERVER_CALLS_SANDBOX);

        mService.registerAppOwnedSdkSandboxInterface(
                TEST_PACKAGE,
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME,
                        /*version=*/ 0,
                        /*interfaceIBinder=*/ new Binder()),
                TIME_APP_CALLED_SYSTEM_SERVER);

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
                TIME_APP_CALLED_SYSTEM_SERVER);

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
    public void testLatencyMetrics_SystemServerAppToSandbox_UnregisterAppOwnedSdkSandboxInterface()
            throws Exception {
        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_SYSTEM_SERVER_CALLS_SANDBOX);

        mService.unregisterAppOwnedSdkSandboxInterface(
                TEST_PACKAGE, APP_OWNED_SDK_SANDBOX_INTERFACE_NAME, TIME_APP_CALLED_SYSTEM_SERVER);

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
    public void testLatencyMetrics_SystemServerAppToSandbox_GetAppOwnedSdkSandboxInterfaces()
            throws Exception {
        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_SYSTEM_SERVER_CALLS_SANDBOX);

        mService.getAppOwnedSdkSandboxInterfaces(TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER);

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

    @Test
    public void testLatencyMetrics_IpcFromAppToSystemServer_LoadSdk() throws Exception {
        loadSdk(SDK_NAME);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                (int)
                                        (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP
                                                - TIME_APP_CALLED_SYSTEM_SERVER),
                                /*success=*/ true,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER,
                                mClientAppUid));
    }

    @Test
    public void testLatencyMetrics_IpcFromAppToSystemServer_RequestSurfacePackage()
            throws Exception {
        loadSdk(SDK_NAME);

        // 2. Call request package
        FakeRequestSurfacePackageCallbackBinder surfacePackageCallback =
                new FakeRequestSurfacePackageCallbackBinder();
        mService.requestSurfacePackage(
                TEST_PACKAGE,
                SDK_NAME,
                new Binder(),
                0,
                500,
                500,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                surfacePackageCallback);
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE,
                                (int)
                                        (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP
                                                - TIME_APP_CALLED_SYSTEM_SERVER),
                                /*success=*/ true,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER,
                                mClientAppUid));
    }

    @Test
    public void testLatencyMetrics_IpcFromAppToSystemServer_GetSandboxedSdks() {
        mService.getSandboxedSdks(TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER);
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
    public void testLatencyMetrics_IpcFromAppToSystemServer_SyncDataFromClient() {
        // Sync data from client
        mService.syncDataFromClient(
                TEST_PACKAGE,
                TIME_APP_CALLED_SYSTEM_SERVER,
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

    // TODO(b/242149555): Update tests to use fake for getting time series.
    @Test
    public void testLatencyMetrics_SystemServerAppToSandbox_LoadSdk() throws Exception {
        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        START_TIME_TO_LOAD_SANDBOX,
                        END_TIME_TO_LOAD_SANDBOX,
                        TIME_SYSTEM_SERVER_CALLS_SANDBOX);
        loadSdk(SDK_NAME);

        final int timeToLoadSdk = (int) (END_TIME_TO_LOAD_SANDBOX - START_TIME_TO_LOAD_SANDBOX);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                timeToLoadSdk,
                                /*success=*/ true,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__LOAD_SANDBOX,
                                mClientAppUid));

        int timeSystemServerAppToSandbox =
                (int)
                        (TIME_SYSTEM_SERVER_CALLS_SANDBOX
                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP
                                - timeToLoadSdk);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                timeSystemServerAppToSandbox,
                                /*success=*/ true,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                                mClientAppUid));
    }

    @Test
    public void testLatencyMetrics_SystemServerSandboxToApp_LoadSdk() throws Exception {
        loadSdk(SDK_NAME);

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
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SANDBOX),
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
    }

    @Test
    public void testLatencyMetrics_SystemServerSandboxToAppWithSandboxLatencyInfo_LoadSdk()
            throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        START_TIME_TO_LOAD_SANDBOX,
                        END_TIME_TO_LOAD_SANDBOX,
                        TIME_SYSTEM_SERVER_CALLS_SANDBOX,
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX);

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback);
        mSdkSandboxService.sendLoadCodeSuccessfulWithSandboxLatencies();

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                (int)
                                        (TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER
                                                - TIME_SYSTEM_SERVER_CALLED_SANDBOX),
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
                        TIME_SYSTEM_SERVER_CALLED_SANDBOX,
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX,
                        TIME_SYSTEM_SERVER_CALLED_APP,
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_FAILURE_HANDLED);

        // Load it once
        {
            FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
            mService.loadSdk(
                    TEST_PACKAGE,
                    null,
                    SDK_NAME,
                    TIME_APP_CALLED_SYSTEM_SERVER,
                    new Bundle(),
                    callback);
            mSdkSandboxService.sendLoadCodeSuccessful();
        }

        // Load it again
        {
            FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
            mService.loadSdk(
                    TEST_PACKAGE,
                    null,
                    SDK_NAME,
                    TIME_APP_CALLED_SYSTEM_SERVER,
                    new Bundle(),
                    callback);
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
                .thenReturn(TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP, TIME_FAILURE_HANDLED);

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(
                TEST_PACKAGE,
                null,
                "RANDOM",
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback);

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
    public void testLatencyMetrics_SystemServerAppToSandbox_LoadSdk_FailureOnAppDeath()
            throws RemoteException {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        FakeLoadSdkCallbackBinder callback = Mockito.mock(FakeLoadSdkCallbackBinder.class);
        IBinder binder = Mockito.mock(IBinder.class);
        Mockito.when(callback.asBinder()).thenReturn(binder);

        Mockito.doThrow(new RemoteException())
                .when(binder)
                .linkToDeath(Mockito.any(), Mockito.anyInt());

        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP, TIME_FAILURE_HANDLED);

        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback);

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

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback);

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
    public void testLatencyMetrics_SystemServerAppToSandbox_RequestSurfacePackage()
            throws Exception {
        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        START_TIME_TO_LOAD_SANDBOX,
                        END_TIME_TO_LOAD_SANDBOX,
                        TIME_SYSTEM_SERVER_CALLED_SANDBOX,
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX,
                        TIME_SYSTEM_SERVER_CALLED_APP,
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_SYSTEM_SERVER_CALLED_SANDBOX);
        loadSdk(SDK_NAME);

        // 2. Call request package
        FakeRequestSurfacePackageCallbackBinder surfacePackageCallback =
                new FakeRequestSurfacePackageCallbackBinder();
        final SandboxLatencyInfo sandboxLatencyInfo = new SandboxLatencyInfo();
        sandboxLatencyInfo.setTimeSystemServerCalledSandbox(TIME_SYSTEM_SERVER_CALLED_SANDBOX);
        mService.requestSurfacePackage(
                TEST_PACKAGE,
                SDK_NAME,
                new Binder(),
                0,
                500,
                500,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                surfacePackageCallback);
        mSdkSandboxService.sendSurfacePackageReady(sandboxLatencyInfo);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE,
                                (int)
                                        (TIME_SYSTEM_SERVER_CALLED_SANDBOX
                                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP),
                                /*success=*/ true,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                                mClientAppUid));

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
    }

    @Test
    public void
            testLatencyMetrics_SystemServerAppToSandbox_RequestSurfacePackage_WithSandboxLatencies()
                    throws Exception {
        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        START_TIME_TO_LOAD_SANDBOX,
                        END_TIME_TO_LOAD_SANDBOX,
                        TIME_SYSTEM_SERVER_CALLED_SANDBOX,
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX,
                        TIME_SYSTEM_SERVER_CALLED_APP,
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_SYSTEM_SERVER_CALLED_SANDBOX);
        loadSdk(SDK_NAME);

        // 2. Call request package
        FakeRequestSurfacePackageCallbackBinder surfacePackageCallback =
                new FakeRequestSurfacePackageCallbackBinder();
        mService.requestSurfacePackage(
                TEST_PACKAGE,
                SDK_NAME,
                new Binder(),
                0,
                500,
                500,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                surfacePackageCallback);

        mSdkSandboxService.sendSurfacePackageReady(getFakedSandboxLatencies());

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE,
                                (int)
                                        (TIME_SYSTEM_SERVER_CALLED_SANDBOX
                                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP),
                                /*success=*/ true,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                                mClientAppUid));

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
                                Mockito.eq(SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SDK),
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
    }

    @Test
    public void testLatencyMetrics_SystemServerSandboxToApp_RequestSurfacePackage()
            throws RemoteException {
        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        START_TIME_TO_LOAD_SANDBOX,
                        END_TIME_TO_LOAD_SANDBOX,
                        TIME_SYSTEM_SERVER_CALLED_SANDBOX,
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX,
                        TIME_SYSTEM_SERVER_CALLED_APP,
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_SYSTEM_SERVER_CALLED_SANDBOX,
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX,
                        TIME_SYSTEM_SERVER_CALLED_APP);
        loadSdk(SDK_NAME);

        // 2. Call request package
        FakeRequestSurfacePackageCallbackBinder surfacePackageCallback =
                new FakeRequestSurfacePackageCallbackBinder();
        final SandboxLatencyInfo sandboxLatencyInfo = new SandboxLatencyInfo();
        sandboxLatencyInfo.setTimeSystemServerCalledSandbox(TIME_SYSTEM_SERVER_CALLED_SANDBOX);
        mService.requestSurfacePackage(
                TEST_PACKAGE,
                SDK_NAME,
                new Binder(),
                0,
                500,
                500,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                surfacePackageCallback);
        mSdkSandboxService.sendSurfacePackageReady(sandboxLatencyInfo);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE,
                                (int)
                                        (TIME_SYSTEM_SERVER_CALLED_APP
                                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX),
                                /*success=*/ true,
                                SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_SANDBOX_TO_APP,
                                mClientAppUid));
    }

    @Test
    public void testLatencyMetrics_SystemServerAppToSandbox_GetSandboxedSdks() {
        // TODO(b/242149555): Update tests to use fake for getting time series.
        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP, END_TIME_IN_SYSTEM_SERVER);

        mService.getSandboxedSdks(TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER);

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
    public void testLatencyMetrics_IpcFromSystemServerToApp_RequestSurfacePackage() {
        mService.logLatencyFromSystemServerToApp(
                ISdkSandboxManager.REQUEST_SURFACE_PACKAGE, /*latency=*/ 1);
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE,
                                /*latency=*/ 1,
                                /*success=*/ true,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_TO_APP,
                                mClientAppUid));
    }

    @Test
    public void testIsDisabled() {
        mService.forceEnableSandbox();
        mSdkSandboxService.setIsDisabledResponse(false);
        assertThat(mService.isSdkSandboxDisabled(mSdkSandboxService)).isFalse();

        mService.clearSdkSandboxState();
        mSdkSandboxService.setIsDisabledResponse(true);
        assertThat(mService.isSdkSandboxDisabled(mSdkSandboxService)).isTrue();

        mService.forceEnableSandbox();
    }

    @Test
    public void testSdkSandboxDisabledCallback() {
        SdkSandboxManagerService.SdkSandboxDisabledCallback callback =
                new SdkSandboxManagerService.SdkSandboxDisabledCallback();
        // In this case the callback has not been called, so a timeout will occur and sandbox
        // will be disabled.
        assertThat(callback.getIsDisabled()).isTrue();

        callback.onResult(false);
        assertThat(callback.getIsDisabled()).isFalse();

        callback = new SdkSandboxManagerService.SdkSandboxDisabledCallback();
        callback.onResult(true);
        assertThat(callback.getIsDisabled()).isTrue();
    }

    @Test
    public void testVisibilityPatchChecked() {
        mService.clearSdkSandboxState();
        // We should only check for the visibility patch on T devices.
        boolean visibilityPatchCheckExpected = !SdkLevel.isAtLeastU();
        mService.isSdkSandboxDisabled(mSdkSandboxService);
        assertThat(mSdkSandboxService.wasVisibilityPatchChecked())
                .isEqualTo(visibilityPatchCheckExpected);
    }

    @Test
    public void testSdkSandboxEnabledForEmulator() {
        // SDK sandbox is enabled for an emulator, even if the killswitch is turned on provided
        // AdServices APK is present.
        Mockito.when(mInjector.isEmulator()).thenReturn(true);
        sSdkSandboxSettingsListener.setKillSwitchState(true);
        assertThat(mService.isSdkSandboxDisabled(mSdkSandboxService)).isFalse();

        // SDK sandbox is disabled when the killswitch is enabled if the device is not an emulator.
        mService.clearSdkSandboxState();
        Mockito.when(mInjector.isEmulator()).thenReturn(false);
        sSdkSandboxSettingsListener.setKillSwitchState(true);
        assertThat(mService.isSdkSandboxDisabled(mSdkSandboxService)).isTrue();
    }

    @Test
    public void testSdkSandboxDisabledForEmulator() {
        // SDK sandbox is disabled for an emulator, if AdServices APK is not present.
        Mockito.doReturn(false).when(mInjector).isAdServiceApkPresent();
        Mockito.when(mInjector.isEmulator()).thenReturn(true);
        sSdkSandboxSettingsListener.setKillSwitchState(true);
        assertThat(mService.isSdkSandboxDisabled(mSdkSandboxService)).isTrue();
    }

    @Test
    public void testSdkSandboxDisabledForAdServiceApkMissing() {
        Mockito.doReturn(true).when(mInjector).isAdServiceApkPresent();
        sSdkSandboxSettingsListener.setKillSwitchState(false);
        assertThat(mService.isSdkSandboxDisabled(mSdkSandboxService)).isFalse();

        Mockito.doReturn(false).when(mInjector).isAdServiceApkPresent();
        sSdkSandboxSettingsListener.setKillSwitchState(false);
        assertThat(mService.isSdkSandboxDisabled(mSdkSandboxService)).isTrue();
    }

    @Test
    public void testSdkSandboxSettings_killSwitch() {
        assertThat(sSdkSandboxSettingsListener.isKillSwitchEnabled()).isFalse();
        sSdkSandboxSettingsListener.onPropertiesChanged(
                new DeviceConfig.Properties(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        Map.of(PROPERTY_DISABLE_SANDBOX, "true")));
        assertThat(sSdkSandboxSettingsListener.isKillSwitchEnabled()).isTrue();
        sSdkSandboxSettingsListener.onPropertiesChanged(
                new DeviceConfig.Properties(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        Map.of(PROPERTY_DISABLE_SANDBOX, "false")));
        assertThat(sSdkSandboxSettingsListener.isKillSwitchEnabled()).isFalse();
    }

    @Test
    public void testOtherPropertyChangeDoesNotAffectKillSwitch() {
        assertThat(sSdkSandboxSettingsListener.isKillSwitchEnabled()).isFalse();
        sSdkSandboxSettingsListener.onPropertiesChanged(
                new DeviceConfig.Properties(
                        DeviceConfig.NAMESPACE_ADSERVICES, Map.of("other_property", "true")));
        assertThat(sSdkSandboxSettingsListener.isKillSwitchEnabled()).isFalse();
    }

    @Test
    public void testSdkSandboxSettings_applySdkSandboxRestrictionsNext() {
        assertThat(sSdkSandboxSettingsListener.applySdkSandboxRestrictionsNext()).isFalse();
        sSdkSandboxSettingsListener.onPropertiesChanged(
                new DeviceConfig.Properties(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        Map.of(PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS, "true")));
        assertThat(sSdkSandboxSettingsListener.applySdkSandboxRestrictionsNext()).isTrue();
        sSdkSandboxSettingsListener.onPropertiesChanged(
                new DeviceConfig.Properties(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        Map.of(PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS, "false")));
        assertThat(sSdkSandboxSettingsListener.applySdkSandboxRestrictionsNext()).isFalse();
    }

    @Test
    public void testServiceAllowlist_DeviceConfigNotAvailable() {
        /** Save the initial value to reset the property to original configuration */
        final String initialServiceAllowlistValue =
                DeviceConfig.getProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_SERVICES_ALLOWLIST);

        DeviceConfig.deleteProperty(DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_SERVICES_ALLOWLIST);

        /**
         * Explicitly calling the onPropertiesChanged method to ensure that the value is propagated
         * and the updated value is read
         */
        sSdkSandboxSettingsListener.onPropertiesChanged(
                new DeviceConfig.Properties(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        Map.of(PROPERTY_SERVICES_ALLOWLIST, "")));

        assertThat(
                        sSdkSandboxSettingsListener.getServiceAllowlistForTargetSdkVersion(
                                /*targetSdkVersion=*/ 34))
                .isNull();

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                PROPERTY_SERVICES_ALLOWLIST,
                initialServiceAllowlistValue,
                /*makeDefault=*/ false);
    }

    @Test
    public void testServiceAllowlist_DeviceConfigAllowlistApplied() {
        /** Save the initial value to reset the property to original configuration */
        String initialServiceAllowlistValue =
                DeviceConfig.getProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_SERVICES_ALLOWLIST);
        /**
         * Base64 encoded Service allowlist allowlist_per_target_sdk { key: 33 value: {
         * allowed_services: { intentAction : "android.test.33" componentPackageName :
         * "packageName.test.33" componentClassName : "className.test.33" } } }
         *
         * <p>allowlist_per_target_sdk { key: 34 value: { allowed_services: { intentAction :
         * "android.test.34" componentPackageName : "packageName.test.34" componentClassName :
         * "className.test.34" } } }
         */
        final String encodedServiceAllowlist =
                "Cj8IIRI7CjkKD2FuZHJvaWQudGVzdC4zMxITcGFja2FnZU5hbWUudGVzdC4zMxoRY2xhc3NOYW1lLnRl"
                        + "c3QuMzMKPwgiEjsKOQoPYW5kcm9pZC50ZXN0LjM0EhNwYWNrYWdlTmFtZS50ZXN0LjM0GhFj"
                        + "bGFzc05hbWUudGVzdC4zNA==";

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                PROPERTY_SERVICES_ALLOWLIST,
                encodedServiceAllowlist,
                /*makeDefault=*/ false);
        /**
         * Explicitly calling the onPropertiesChanged method to ensure that the value is propagated
         * and the updated value is read
         */
        sSdkSandboxSettingsListener.onPropertiesChanged(
                new DeviceConfig.Properties(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        Map.of(PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist)));

        AllowedServices allowedServices =
                sSdkSandboxSettingsListener.getServiceAllowlistForTargetSdkVersion(
                        /*targetSdkVersion=*/ 33);
        assertThat(allowedServices).isNotNull();

        verifyAllowlistEntryContents(
                allowedServices.getAllowedServices(0),
                /*action=*/ "android.test.33",
                /*packageName=*/ "packageName.test.33",
                /*componentClassName=*/ "className.test.33");

        allowedServices =
                sSdkSandboxSettingsListener.getServiceAllowlistForTargetSdkVersion(
                        /*targetSdkVersion=*/ 34);
        assertThat(allowedServices).isNotNull();

        verifyAllowlistEntryContents(
                allowedServices.getAllowedServices(0),
                /*action=*/ "android.test.34",
                /*packageName=*/ "packageName.test.34",
                /*componentClassName=*/ "className.test.34");

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                PROPERTY_SERVICES_ALLOWLIST,
                initialServiceAllowlistValue,
                /*makeDefault=*/ false);
    }

    private void verifyAllowlistEntryContents(
            AllowedService allowedService,
            String action,
            String packageName,
            String componentClassName) {
        assertThat(allowedService.getAction()).isEqualTo(action);
        assertThat(allowedService.getPackageName()).isEqualTo(packageName);
        assertThat(allowedService.getComponentClassName()).isEqualTo(componentClassName);
    }

    @Test
    public void testKillswitchStopsSandbox() throws Exception {
        disableKillUid();
        sSdkSandboxSettingsListener.onPropertiesChanged(
                new DeviceConfig.Properties(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        Map.of(PROPERTY_DISABLE_SANDBOX, "false")));
        sSdkSandboxSettingsListener.setKillSwitchState(false);
        loadSdk(SDK_NAME);
        sSdkSandboxSettingsListener.onPropertiesChanged(
                new DeviceConfig.Properties(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        Map.of(PROPERTY_DISABLE_SANDBOX, "true")));
        int callingUid = Binder.getCallingUid();
        final CallingInfo callingInfo = new CallingInfo(callingUid, TEST_PACKAGE);
        assertThat(sProvider.getSdkSandboxServiceForApp(callingInfo)).isEqualTo(null);
    }

    @Test
    public void testLoadSdkFailsWhenSandboxDisabled() {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        sSdkSandboxSettingsListener.setKillSwitchState(true);
        sSdkSandboxSettingsListener.onPropertiesChanged(
                new DeviceConfig.Properties(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        Map.of(PROPERTY_DISABLE_SANDBOX, "true")));
        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback);
        callback.assertLoadSdkIsUnsuccessful();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_SDK_SANDBOX_DISABLED);
        assertThat(callback.getLoadSdkErrorMsg()).isEqualTo("SDK sandbox is disabled");
    }

    @Test
    public void testSdkSandboxSettings_canAccessContentProviderFromSdkSandbox_DefaultAccess()
            throws Exception {
        /** Ensuring that the property is not present in DeviceConfig */
        DeviceConfig.deleteProperty(
                DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_ENFORCE_RESTRICTIONS);
        ExtendedMockito.when(Process.isSdkSandboxUid(Mockito.anyInt())).thenReturn(true);
        // The default value of the flag enforcing restrictions is true and access should be
        // restricted.
        assertThat(
                        sSdkSandboxManagerLocal.canAccessContentProviderFromSdkSandbox(
                                new ProviderInfo()))
                .isFalse();
    }

    @Test
    public void testSdkSandboxSettings_canAccessContentProviderFromSdkSandbox_AccessNotAllowed() {
        ExtendedMockito.when(Process.isSdkSandboxUid(Mockito.anyInt())).thenReturn(true);
        sSdkSandboxSettingsListener.onPropertiesChanged(
                new DeviceConfig.Properties(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        Map.of(PROPERTY_ENFORCE_RESTRICTIONS, "true")));
        assertThat(
                        sSdkSandboxManagerLocal.canAccessContentProviderFromSdkSandbox(
                                new ProviderInfo()))
                .isFalse();
    }

    @Test
    public void testSdkSandboxSettings_canAccessContentProviderFromSdkSandbox_AccessAllowed() {
        ExtendedMockito.when(Process.isSdkSandboxUid(Mockito.anyInt())).thenReturn(true);
        sSdkSandboxSettingsListener.onPropertiesChanged(
                new DeviceConfig.Properties(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        Map.of(PROPERTY_ENFORCE_RESTRICTIONS, "false")));
        assertThat(
                        sSdkSandboxManagerLocal.canAccessContentProviderFromSdkSandbox(
                                new ProviderInfo()))
                .isTrue();
    }

    @Test
    public void
            testLatencyMetrics_systemServerAppToSandbox_RequestSurfacePackage_sandboxNotLoaded() {
        disableForegroundCheck();

        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP, TIME_FAILURE_HANDLED);

        FakeRequestSurfacePackageCallbackBinder surfacePackageCallback =
                new FakeRequestSurfacePackageCallbackBinder();
        mService.requestSurfacePackage(
                TEST_PACKAGE,
                SDK_NAME,
                new Binder(),
                0,
                500,
                500,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                surfacePackageCallback);

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
                        TIME_SYSTEM_SERVER_CALLED_SANDBOX,
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
                                        (TIME_SYSTEM_SERVER_CALLED_SANDBOX
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
                        TIME_SYSTEM_SERVER_CALLED_SANDBOX,
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
                                                - TIME_SYSTEM_SERVER_CALLED_SANDBOX),
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
    public void testRemoveAppOwnedSdkSandboxInterfacesOnAppDeath() throws Exception {
        IBinder iBinder = Mockito.mock(IBinder.class);
        mService.registerAppOwnedSdkSandboxInterface(
                TEST_PACKAGE,
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME,
                        /*version=*/ 0,
                        /*interfaceIBinder=*/ iBinder),
                TIME_APP_CALLED_SYSTEM_SERVER);
        ArgumentCaptor<IBinder.DeathRecipient> deathRecipient =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);

        Mockito.verify(iBinder).linkToDeath(deathRecipient.capture(), ArgumentMatchers.eq(0));

        // App Died
        deathRecipient.getValue().binderDied();

        assertThat(
                        mService.getAppOwnedSdkSandboxInterfaces(
                                TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER))
                .hasSize(0);
    }

    @Test
    public void testUnloadSdkNotCalledOnAppDeath() throws Exception {
        disableKillUid();
        disableForegroundCheck();
        disableNetworkPermissionChecks();
        FakeLoadSdkCallbackBinder callback = Mockito.spy(new FakeLoadSdkCallbackBinder());
        Mockito.doReturn(Mockito.mock(Binder.class)).when(callback).asBinder();

        ArgumentCaptor<IBinder.DeathRecipient> deathRecipient =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);

        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback);
        mSdkSandboxService.sendLoadCodeSuccessful();
        callback.assertLoadSdkIsSuccessful();

        Mockito.verify(callback.asBinder())
                .linkToDeath(deathRecipient.capture(), ArgumentMatchers.eq(0));

        // App Died
        deathRecipient.getValue().binderDied();

        Mockito.verify(mSdkSandboxService, Mockito.never())
                .unloadSdk(
                        Mockito.anyString(),
                        Mockito.any(IUnloadSdkCallback.class),
                        Mockito.any(SandboxLatencyInfo.class));
    }

    @Test
    public void testLoadSdk_computeSdkStorage() throws Exception {
        final int callingUid = Process.myUid();
        final CallingInfo callingInfo = new CallingInfo(callingUid, TEST_PACKAGE);

        mSdkSandboxStorageManagerUtility.createSdkStorageForTest(
                UserHandle.getUserId(callingUid),
                TEST_PACKAGE,
                Arrays.asList("sdk1", "sdk2"),
                Arrays.asList(
                        SdkSandboxStorageManager.SubDirectories.SHARED_DIR,
                        SdkSandboxStorageManager.SubDirectories.SANDBOX_DIR));

        loadSdk(SDK_NAME);
        // Assume sdk storage information calculated and sent
        mSdkSandboxService.sendStorageInfoToSystemServer();

        final List<SdkSandboxStorageManager.StorageDirInfo> internalStorageDirInfo =
                mSdkSandboxStorageManager.getInternalStorageDirInfo(callingInfo);
        final List<SdkSandboxStorageManager.StorageDirInfo> sdkStorageDirInfo =
                mSdkSandboxStorageManager.getSdkStorageDirInfo(callingInfo);

        Mockito.verify(sSdkSandboxPulledAtoms, Mockito.timeout(5000))
                .logStorage(mClientAppUid, /*sharedStorage=*/ 0, /*sdkStorage=*/ 0);

        Mockito.verify(mSdkSandboxService, Mockito.times(1))
                .computeSdkStorage(
                        Mockito.eq(mService.getListOfStoragePaths(internalStorageDirInfo)),
                        Mockito.eq(mService.getListOfStoragePaths(sdkStorageDirInfo)),
                        Mockito.any(IComputeSdkStorageCallback.class));
    }

    @Test
    public void testLoadSdk_CustomizedApplicationInfoIsPopulatedProperly() throws Exception {
        final int userId = UserHandle.getUserId(Process.myUid());

        // Create fake storage directories
        mSdkSandboxStorageManagerUtility.createSdkStorageForTest(
                userId, TEST_PACKAGE, Arrays.asList(SDK_NAME), Collections.emptyList());
        StorageDirInfo storageInfo =
                mSdkSandboxStorageManagerUtility
                        .getSdkStorageDirInfoForTest(
                                null, userId, TEST_PACKAGE, Arrays.asList(SDK_NAME))
                        .get(0);

        // Load SDK so that information is passed to sandbox service
        loadSdk(SDK_NAME);

        // Verify customized application info is overloaded with per-sdk storage paths
        ApplicationInfo ai = mSdkSandboxService.getCustomizedInfo();
        assertThat(ai.dataDir).isEqualTo(storageInfo.getCeDataDir());
        assertThat(ai.credentialProtectedDataDir).isEqualTo(storageInfo.getCeDataDir());
        assertThat(ai.deviceProtectedDataDir).isEqualTo(storageInfo.getDeDataDir());
    }

    @Test
    public void testRegisterActivityInterceptorCallbackOnServiceStart() {
        assumeTrue(SdkLevel.isAtLeastU());

        // Build ActivityInterceptorInfo
        int callingUid = 1000;
        Intent intent = new Intent();
        intent.setAction(SdkSandboxManager.ACTION_START_SANDBOXED_ACTIVITY);
        intent.setPackage(getSandboxPackageName());
        ActivityInfo activityInfo = new ActivityInfo();

        ActivityInterceptorCallback.ActivityInterceptResult result =
                interceptActivityLunch(intent, callingUid, activityInfo);

        assertThat(result.getIntent()).isEqualTo(intent);
        assertThat(result.getActivityOptions()).isNull();
        assertThat(result.isActivityResolved()).isTrue();
        assertThat(activityInfo.processName)
                .isEqualTo(
                        mInjector
                                .getSdkSandboxServiceProvider()
                                .toSandboxProcessName(TEST_PACKAGE));
        assertThat(activityInfo.applicationInfo.uid).isEqualTo(Process.toSdkSandboxUid(callingUid));
    }

    @Test
    public void testRegisterActivityInterceptionWithRightComponentSuccess() {
        assumeTrue(SdkLevel.isAtLeastU());

        // Build ActivityInterceptorInfo
        int callingUid = 1000;
        Intent intent = new Intent();
        intent.setAction(SdkSandboxManager.ACTION_START_SANDBOXED_ACTIVITY);
        intent.setPackage(getSandboxPackageName());
        intent.setComponent(new ComponentName(getSandboxPackageName(), ""));
        ActivityInfo activityInfo = new ActivityInfo();

        ActivityInterceptorCallback.ActivityInterceptResult result =
                interceptActivityLunch(intent, callingUid, activityInfo);

        assertThat(result.getIntent()).isEqualTo(intent);
        assertThat(result.getActivityOptions()).isNull();
        assertThat(result.isActivityResolved()).isTrue();
        assertThat(activityInfo.processName)
                .isEqualTo(
                        mInjector
                                .getSdkSandboxServiceProvider()
                                .toSandboxProcessName(TEST_PACKAGE));
        assertThat(activityInfo.applicationInfo.uid).isEqualTo(Process.toSdkSandboxUid(callingUid));
    }

    @Test
    public void testRegisterActivityInterceptionNotProceedForNullIntent() {
        assumeTrue(SdkLevel.isAtLeastU());

        ActivityInterceptorCallback.ActivityInterceptResult result = interceptActivityLunch(null);

        assertThat(result).isNull();
    }

    @Test
    public void testRegisterActivityInterceptionNotProceedForNullPackage() {
        assumeTrue(SdkLevel.isAtLeastU());

        Intent intent = new Intent();

        ActivityInterceptorCallback.ActivityInterceptResult result = interceptActivityLunch(intent);

        assertThat(result).isNull();
    }

    @Test
    public void testRegisterActivityInterceptionNotProceedForWrongPackage() {
        assumeTrue(SdkLevel.isAtLeastU());

        Intent intent = new Intent();
        intent.setPackage("com.random.package");

        ActivityInterceptorCallback.ActivityInterceptResult result = interceptActivityLunch(intent);

        assertThat(result).isNull();
    }

    @Test
    public void testRegisterActivityInterceptionCallbackReturnNullForNullAction() {
        assumeTrue(SdkLevel.isAtLeastU());

        Intent intent = new Intent();
        intent.setPackage("com.random.package");

        ActivityInterceptorCallback.ActivityInterceptResult result = interceptActivityLunch(intent);

        assertThat(result).isNull();
    }

    @Test
    public void testRegisterActivityInterceptionCallbackReturnNullForWrongAction() {
        assumeTrue(SdkLevel.isAtLeastU());

        Intent intent = new Intent();
        intent.setPackage("com.random.package");
        intent.setAction(Intent.ACTION_VIEW);

        ActivityInterceptorCallback.ActivityInterceptResult result = interceptActivityLunch(intent);

        assertThat(result).isNull();
    }

    @Test
    public void testRegisterActivityInterceptionCallbackReturnNullForWrongComponent() {
        assumeTrue(SdkLevel.isAtLeastU());

        Intent intent = new Intent();
        intent.setAction(SdkSandboxManager.ACTION_START_SANDBOXED_ACTIVITY);
        intent.setPackage(getSandboxPackageName());
        intent.setComponent(new ComponentName("random", ""));

        ActivityInterceptorCallback.ActivityInterceptResult result = interceptActivityLunch(intent);

        assertThat(result).isNull();
    }

    @Test
    public void testWildcardPatternMatch() {
        String pattern1 = "abcd*";
        verifyPatternMatch(pattern1, "abcd", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern1, "abcdef", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern1, "abcdabcd", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern1, "efgh", /*matchOnNullInput=*/ false, false);
        verifyPatternMatch(pattern1, "efgabcd", /*matchOnNullInput=*/ false, false);
        verifyPatternMatch(pattern1, "abc", /*matchOnNullInput=*/ false, false);

        String pattern2 = "*";
        verifyPatternMatch(pattern2, "", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern2, "abcd", /*matchOnNullInput=*/ false, true);

        String pattern3 = "abcd*efgh*";
        verifyPatternMatch(pattern3, "abcdefgh", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern3, "abcdrefghij", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern3, "abcd", /*matchOnNullInput=*/ false, false);
        verifyPatternMatch(pattern3, "abcdteffgh", /*matchOnNullInput=*/ false, false);

        String pattern4 = "*abcd";
        verifyPatternMatch(pattern4, "abcdabcd", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern4, "abcdabcdabcd", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern4, "efgabcd", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern4, "abcd", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern4, "abcde", /*matchOnNullInput=*/ false, false);

        String pattern5 = "abcd*e";
        verifyPatternMatch(pattern5, "abcde", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern5, "abcdee", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern5, "abcdef", /*matchOnNullInput=*/ false, false);

        String pattern6 = "";
        verifyPatternMatch(pattern6, "", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern6, "ab", /*matchOnNullInput=*/ false, false);

        String pattern7 = "*abcd*";
        verifyPatternMatch(pattern7, "abcdabcdabcd", /*matchOnNullInput=*/ false, true);

        String pattern8 = "a*a";
        verifyPatternMatch(pattern8, "aa", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern8, "a", /*matchOnNullInput=*/ false, false);

        String pattern9 = "abcd";
        verifyPatternMatch(pattern9, "abcd", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern9, "a", /*matchOnNullInput=*/ false, false);

        verifyPatternMatch("*aab", "aaaab", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch("a", "ab", /*matchOnNullInput=*/ false, false);

        verifyPatternMatch("*", null, /*matchOnNullInput=*/ false, false);
        verifyPatternMatch("*", null, /*matchOnNullInput=*/ true, true);
    }

    private void verifyPatternMatch(
            String pattern, String input, boolean matchOnNullInput, boolean shouldMatch) {
        if (shouldMatch) {
            assertThat(
                            SdkSandboxManagerService.doesInputMatchWildcardPattern(
                                    pattern, input, matchOnNullInput))
                    .isTrue();
        } else {
            assertThat(
                            SdkSandboxManagerService.doesInputMatchWildcardPattern(
                                    pattern, input, matchOnNullInput))
                    .isFalse();
        }
    }

    private ActivityInterceptorCallback.ActivityInterceptResult interceptActivityLunch(
            Intent intent) {
        return interceptActivityLunch(intent, 1000, new ActivityInfo());
    }

    private ActivityInterceptorCallback.ActivityInterceptResult interceptActivityLunch(
            Intent intent, int callingUid, ActivityInfo activityInfo) {
        activityInfo.applicationInfo = new ApplicationInfo();
        ActivityInterceptorCallback.ActivityInterceptorInfo info =
                new ActivityInterceptorCallback.ActivityInterceptorInfo.Builder(
                                callingUid, 0, 0, 0, 0, intent, null, activityInfo)
                        .setCallingPackage(TEST_PACKAGE)
                        .build();
        return mInterceptorCallbackArgumentCaptor.getValue().onInterceptActivityLaunch(info);
    }

    private SandboxLatencyInfo getFakedSandboxLatencies() {
        final SandboxLatencyInfo sandboxLatencyInfo = new SandboxLatencyInfo();
        sandboxLatencyInfo.setTimeSystemServerCalledSandbox(TIME_SYSTEM_SERVER_CALLED_SANDBOX);
        sandboxLatencyInfo.setTimeSandboxReceivedCallFromSystemServer(
                TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER);
        sandboxLatencyInfo.setTimeSandboxCalledSdk(TIME_SANDBOX_CALLED_SDK);
        sandboxLatencyInfo.setTimeSdkCallCompleted(TIME_SDK_CALL_COMPLETED);
        sandboxLatencyInfo.setTimeSandboxCalledSystemServer(TIME_SANDBOX_CALLED_SYSTEM_SERVER);

        return sandboxLatencyInfo;
    }

    private void loadSdk(String sdkName) throws RemoteException {
        disableNetworkPermissionChecks();
        disableForegroundCheck();
        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(
                TEST_PACKAGE, null, sdkName, TIME_APP_CALLED_SYSTEM_SERVER, new Bundle(), callback);
        mSdkSandboxService.sendLoadCodeSuccessful();
        callback.assertLoadSdkIsSuccessful();
    }

    private void killSandbox() throws Exception {
        ArgumentCaptor<IBinder.DeathRecipient> deathRecipientCaptor =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);
        Mockito.verify(mSdkSandboxService.asBinder(), Mockito.atLeastOnce())
                .linkToDeath(deathRecipientCaptor.capture(), ArgumentMatchers.eq(0));
        List<IBinder.DeathRecipient> deathRecipients = deathRecipientCaptor.getAllValues();
        for (IBinder.DeathRecipient deathRecipient : deathRecipients) {
            deathRecipient.binderDied();
        }
    }

    // Restart sandbox which creates a new sandbox service binder.
    private void restartAndSetSandboxService() throws Exception {
        mSdkSandboxService = sProvider.restartSandbox();
    }

    private String getSandboxPackageName() {
        return mSpyContext.getPackageManager().getSdkSandboxPackageName();
    }

    private void mockGrantedPermission(String permission) {
        Log.d(TAG, "mockGrantedPermission(" + permission + ")");
        Mockito.doNothing()
                .when(mSpyContext)
                .enforceCallingPermission(Mockito.eq(permission), Mockito.anyString());
    }

    private void requiresAtLeastU(boolean required) {
        Log.d(
                TAG,
                "requireAtLeastU("
                        + required
                        + "): SdkLevel.isAtLeastU()="
                        + SdkLevel.isAtLeastU());
        // TODO(b/280677793): rather than assuming it's the given version, mock it:
        //     ExtendedMockito.doReturn(required).when(() -> SdkLevel.isAtLeastU());
        if (required) {
            assumeTrue("Device must be at least U", SdkLevel.isAtLeastU());
        } else {
            assumeFalse("Device must be less than U", SdkLevel.isAtLeastU());
        }
    }

    private void testServiceRestriction(
            @Nullable String action,
            @Nullable String packageName,
            @Nullable String componentClassName,
            @Nullable String componentPackageName) {
        final Intent intent = Objects.isNull(action) ? new Intent() : new Intent(action);
        intent.setPackage(packageName);
        if (Objects.isNull(componentPackageName)) {
            componentPackageName = "nonexistent.package";
        }
        if (Objects.isNull(componentClassName)) {
            componentClassName = "nonexistent.class";
        }
        intent.setComponent(new ComponentName(componentPackageName, componentClassName));

        sSdkSandboxManagerLocal.enforceAllowedToStartOrBindService(intent);
    }

    private void setDeviceConfigProperty(String property, String value) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES, property, value, /*makeDefault=*/ false);
        /**
         * Explicitly calling the onPropertiesChanged method to ensure that the value is propagated
         * and the updated value is read
         */
        sSdkSandboxSettingsListener.onPropertiesChanged(
                new DeviceConfig.Properties(
                        DeviceConfig.NAMESPACE_ADSERVICES, Map.of(property, value)));
    }

    /** Fake service provider that returns local instance of {@link SdkSandboxServiceProvider} */
    private static class FakeSdkSandboxProvider implements SdkSandboxServiceProvider {
        private FakeSdkSandboxService mSdkSandboxService;
        private final ArrayMap<CallingInfo, ISdkSandboxService> mService = new ArrayMap<>();

        // When set to true, this will fail the bindService call
        private boolean mFailBinding = false;

        private ServiceConnection mServiceConnection = null;

        FakeSdkSandboxProvider(FakeSdkSandboxService service) {
            mSdkSandboxService = service;
        }

        public void disableBinding() {
            mFailBinding = true;
        }

        public FakeSdkSandboxService restartSandbox() {
            mServiceConnection.onServiceDisconnected(null);

            // Create a new sandbox service.
            mSdkSandboxService = Mockito.spy(FakeSdkSandboxService.class);

            // Call onServiceConnected() again with the new fake sandbox service.
            mServiceConnection.onServiceConnected(null, mSdkSandboxService.asBinder());
            return mSdkSandboxService;
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
            mServiceConnection = serviceConnection;
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
        public String toSandboxProcessName(@NonNull String packageName) {
            return TEST_PACKAGE + SANDBOX_PROCESS_NAME_SUFFIX;
        }
    }

    private static Bundle getTestBundle() {
        final Bundle data = new Bundle();
        data.putString(TEST_KEY, TEST_VALUE);
        return data;
    }

    public static class InjectorForTest extends SdkSandboxManagerService.Injector {
        private SdkSandboxStorageManager mSdkSandboxStorageManager = null;

        InjectorForTest(Context context, SdkSandboxStorageManager sdkSandboxStorageManager) {
            super(context);
            mSdkSandboxStorageManager = sdkSandboxStorageManager;
        }

        public InjectorForTest(Context spyContext) {
            super(spyContext);
        }

        @Override
        public long getCurrentTime() {
            return TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP;
        }

        @Override
        public SdkSandboxServiceProvider getSdkSandboxServiceProvider() {
            return sProvider;
        }

        @Override
        public SdkSandboxPulledAtoms getSdkSandboxPulledAtoms() {
            return sSdkSandboxPulledAtoms;
        }

        @Override
        public SdkSandboxStorageManager getSdkSandboxStorageManager() {
            return mSdkSandboxStorageManager;
        }
    }
}
