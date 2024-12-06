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

package com.android.server.sdksandbox;

import static com.android.adservices.flags.Flags.FLAG_SDKSANDBOX_USE_EFFECTIVE_TARGET_SDK_VERSION_FOR_RESTRICTIONS;
import static com.android.sdksandbox.flags.Flags.FLAG_SERVICE_RESTRICTION_PACKAGE_NAME_LOGIC_UPDATED;
import static com.android.server.wm.ActivityInterceptorCallback.MAINLINE_SDK_SANDBOX_ORDER_ID;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;

import android.Manifest;
import android.app.sdksandbox.testutils.FakeSdkSandboxManagerLocal;
import android.app.sdksandbox.testutils.FakeSdkSandboxService;
import android.app.sdksandbox.testutils.ProtoUtil;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ProviderInfo;
import android.os.Build;
import android.os.Process;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.ArrayMap;
import android.util.ArraySet;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSessionBuilder;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.pm.PackageManagerLocal;
import com.android.server.sdksandbox.DeviceSupportedBaseTest;
import com.android.server.sdksandbox.testutils.FakeSdkSandboxProvider;
import com.android.server.wm.ActivityInterceptorCallback;
import com.android.server.wm.ActivityInterceptorCallbackRegistry;

import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class SdkSandboxRestrictionsUnitTest extends DeviceSupportedBaseTest {
    private static final String INTENT_ACTION = "action.test";
    private static final String PACKAGE_NAME = "packageName.test";
    private static final ComponentName COMPONENT =
            new ComponentName("componentPackageName.test", "className.test");
    private static final String PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS =
            "apply_sdk_sandbox_next_restrictions";
    private static final String PROPERTY_ENFORCE_RESTRICTIONS = "sdksandbox_enforce_restrictions";
    private static final String PROPERTY_ACTIVITY_ALLOWLIST =
            "sdksandbox_activity_allowlist_per_targetSdkVersion";
    private static final String PROPERTY_NEXT_ACTIVITY_ALLOWLIST =
            "sdksandbox_next_activity_allowlist";
    private static final String PROPERTY_SERVICES_ALLOWLIST =
            "services_allowlist_per_targetSdkVersion";
    private static final String PROPERTY_NEXT_SERVICE_ALLOWLIST =
            "sdksandbox_next_service_allowlist";

    // Keep consistent with SdkSandboxManagerService.PROPERTY_BROADCASTRECEIVER_ALLOWLIST
    private static final String PROPERTY_BROADCASTRECEIVER_ALLOWLIST =
            "sdksandbox_broadcastreceiver_allowlist_per_targetSdkVersion";

    // Keep the value consistent with SdkSandboxmanagerService.PROPERTY_CONTENTPROVIDER_ALLOWLIST.
    private static final String PROPERTY_CONTENTPROVIDER_ALLOWLIST =
            "contentprovider_allowlist_per_targetSdkVersion";

    private SdkSandboxManagerService mService;
    private MockitoSession mStaticMockSession;
    private ArgumentCaptor<ActivityInterceptorCallback> mInterceptorCallbackArgumentCaptor =
            ArgumentCaptor.forClass(ActivityInterceptorCallback.class);
    private SdkSandboxManagerLocal mSdkSandboxManagerLocal;
    private SdkSandboxSettingsListener mSdkSandboxSettingsListener;
    private SdkSandboxManagerService.Injector mInjector;
    private DeviceConfigUtil mDeviceConfigUtil;
    private SdkSandboxRestrictionManager mSdkSandboxRestrictionManager;

    @Rule(order = 0)
    public final CheckFlagsRule checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule(order = 1)
    public final Expect expect = Expect.create();

    @Before
    public void setup() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();

        StaticMockitoSessionBuilder mockitoSessionBuilder =
                ExtendedMockito.mockitoSession()
                        .strictness(Strictness.LENIENT)
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

        // Required to access <sdk-library> information and DeviceConfig update.
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.READ_DEVICE_CONFIG,
                        Manifest.permission.WRITE_DEVICE_CONFIG,
                        Manifest.permission.WRITE_ALLOWLISTED_DEVICE_CONFIG,
                        // for Context#registerReceiverForAllUsers
                        Manifest.permission.INTERACT_ACROSS_USERS_FULL);

        mSdkSandboxRestrictionManager = Mockito.spy(new SdkSandboxRestrictionManager(context));

        mInjector =
                Mockito.spy(
                        new FakeInjector(
                                context,
                                new SdkSandboxStorageManager(
                                        context,
                                        new FakeSdkSandboxManagerLocal(),
                                        Mockito.spy(PackageManagerLocal.class),
                                        /*rootDir=*/ context.getDir(
                                                        "test_dir", Context.MODE_PRIVATE)
                                                .getPath()),
                                new FakeSdkSandboxProvider(
                                        Mockito.spy(FakeSdkSandboxService.class)),
                                Mockito.spy(SdkSandboxPulledAtoms.class),
                                new SdkSandboxStatsdLogger(),
                                mSdkSandboxRestrictionManager));
        mService = new SdkSandboxManagerService(context, mInjector);
        mSdkSandboxManagerLocal = mService.getLocalManager();
        assertThat(mSdkSandboxManagerLocal).isNotNull();

        mSdkSandboxSettingsListener = mService.getSdkSandboxSettingsListener();
        mDeviceConfigUtil = new DeviceConfigUtil(mSdkSandboxSettingsListener);

        ExtendedMockito.doReturn(true).when(() -> Process.isSdkSandboxUid(Mockito.anyInt()));
    }

    @After
    public void tearDown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    /** Tests that only allowed intents may be sent from the sdk sandbox. */
    @Test
    public void testEnforceAllowedToSendBroadcast() {
        Intent disallowedIntent = new Intent(Intent.ACTION_SCREEN_ON);
        assertThrows(
                SecurityException.class,
                () -> mSdkSandboxManagerLocal.enforceAllowedToSendBroadcast(disallowedIntent));
    }

    /** Tests that no broadcast can be sent from the sdk sandbox. */
    @Test
    public void testCanSendBroadcast() {
        assertThat(mSdkSandboxManagerLocal.canSendBroadcast(new Intent())).isFalse();
    }

    /** Tests that only allowed activities may be started from the sdk sandbox. */
    @Test
    public void testEnforceAllowedToStartActivity_defaultAllowedValues() {
        for (String action : SdkSandboxManagerService.DEFAULT_ACTIVITY_ALLOWED_ACTIONS) {
            Intent allowedIntent = new Intent(action);
            mSdkSandboxManagerLocal.enforceAllowedToStartActivity(allowedIntent);
        }

        Intent intentWithoutAction = new Intent();
        mSdkSandboxManagerLocal.enforceAllowedToStartActivity(intentWithoutAction);

        Intent disallowedIntent = new Intent(Intent.ACTION_SCREEN_OFF);
        assertThrows(
                SecurityException.class,
                () -> mSdkSandboxManagerLocal.enforceAllowedToStartActivity(disallowedIntent));
    }

    @Test
    public void testEnforceAllowedToStartActivity_restrictionsNotApplied() {
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_ENFORCE_RESTRICTIONS, "false");
        Intent intent = new Intent(Intent.ACTION_CALL);
        mSdkSandboxManagerLocal.enforceAllowedToStartActivity(intent);
    }

    @Test
    public void testEnforceAllowedToStartActivity_deviceConfigAllowlist() {
        ArrayMap<Integer, List<String>> allowedActivities = new ArrayMap<>();
        allowedActivities.put(
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                new ArrayList<>(Arrays.asList(Intent.ACTION_CALL)));
        String encodedAllowedActivities = ProtoUtil.encodeActivityAllowlist(allowedActivities);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_ACTIVITY_ALLOWLIST, encodedAllowedActivities);

        Intent intent = new Intent(Intent.ACTION_CALL);
        mSdkSandboxManagerLocal.enforceAllowedToStartActivity(intent);

        for (String action : SdkSandboxManagerService.DEFAULT_ACTIVITY_ALLOWED_ACTIONS) {
            assertThrows(
                    SecurityException.class,
                    () ->
                            mSdkSandboxManagerLocal.enforceAllowedToStartActivity(
                                    new Intent(action)));
        }
    }

    @Test
    public void testEnforceAllowedToStartActivity_restrictionEnforcedDeviceConfigAllowlistNotSet() {
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_ACTIVITY_ALLOWLIST, null);

        for (String action : SdkSandboxManagerService.DEFAULT_ACTIVITY_ALLOWED_ACTIONS) {
            mSdkSandboxManagerLocal.enforceAllowedToStartActivity(new Intent(action));
        }

        assertThrows(
                SecurityException.class,
                () ->
                        mSdkSandboxManagerLocal.enforceAllowedToStartActivity(
                                new Intent(Intent.ACTION_CALL)));
    }

    @Test
    public void testEnforceAllowedToStartActivity_restrictionsNotEnforced() {
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_ENFORCE_RESTRICTIONS, "false");
        mSdkSandboxManagerLocal.enforceAllowedToStartActivity(new Intent());
    }

    @Test
    public void testEnforceAllowedToStartActivity_nextRestrictionsApplied() {
        ArrayMap<Integer, List<String>> allowedActivities = new ArrayMap<>();
        allowedActivities.put(
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                new ArrayList<>(Arrays.asList(Intent.ACTION_CALL)));
        String encodedAllowedActivities = ProtoUtil.encodeActivityAllowlist(allowedActivities);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_ACTIVITY_ALLOWLIST, encodedAllowedActivities);

        mSdkSandboxManagerLocal.enforceAllowedToStartActivity(new Intent(Intent.ACTION_CALL));
        assertThrows(
                SecurityException.class,
                () ->
                        mSdkSandboxManagerLocal.enforceAllowedToStartActivity(
                                new Intent(Intent.ACTION_VIEW)));

        ArraySet<String> nextAllowedActivities =
                new ArraySet<>(Arrays.asList(Intent.ACTION_WEB_SEARCH));
        String encodedNextAllowedActivities =
                ProtoUtil.encodeActivityAllowlist(nextAllowedActivities);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_NEXT_ACTIVITY_ALLOWLIST, encodedNextAllowedActivities);

        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS, "true");
        mSdkSandboxManagerLocal.enforceAllowedToStartActivity(new Intent(Intent.ACTION_WEB_SEARCH));
        assertThrows(
                SecurityException.class,
                () ->
                        mSdkSandboxManagerLocal.enforceAllowedToStartActivity(
                                new Intent(Intent.ACTION_CALL)));
    }

    @Test
    public void testEnforceAllowedToStartActivity_nextRestrictionsAppliedButAllowlistNotSet() {
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS, "true");
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_NEXT_ACTIVITY_ALLOWLIST, "");

        Intent intent = new Intent(Intent.ACTION_CALL);

        assertThrows(
                SecurityException.class,
                () -> mSdkSandboxManagerLocal.enforceAllowedToStartActivity(intent));
        for (String action : SdkSandboxManagerService.DEFAULT_ACTIVITY_ALLOWED_ACTIONS) {
            mSdkSandboxManagerLocal.enforceAllowedToStartActivity(new Intent(action));
        }

        ArrayMap<Integer, List<String>> allowedActivities = new ArrayMap<>();
        allowedActivities.put(
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                new ArrayList<>(Arrays.asList(Intent.ACTION_CALL)));
        String encodedAllowedActivities = ProtoUtil.encodeActivityAllowlist(allowedActivities);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_ACTIVITY_ALLOWLIST, encodedAllowedActivities);

        mSdkSandboxManagerLocal.enforceAllowedToStartActivity(intent);
        for (String action : SdkSandboxManagerService.DEFAULT_ACTIVITY_ALLOWED_ACTIONS) {
            assertThrows(
                    SecurityException.class,
                    () ->
                            mSdkSandboxManagerLocal.enforceAllowedToStartActivity(
                                    new Intent(action)));
        }
    }

    @Test
    public void testEnforceAllowedToStartOrBindService_disallowNonExistentPackage() {
        Intent intent = new Intent().setComponent(new ComponentName("nonexistent.package", "test"));
        assertThrows(
                SecurityException.class,
                () -> mSdkSandboxManagerLocal.enforceAllowedToStartOrBindService(intent));
    }

    @Test
    public void testEnforceAllowedToStartOrBindService_AdServicesApkNotPresent() throws Exception {
        String adServicesPackageName = mInjector.getAdServicesPackageName();
        Mockito.when(mInjector.getAdServicesPackageName()).thenReturn(null);
        Intent intent = new Intent().setComponent(new ComponentName(adServicesPackageName, "test"));
        assertThrows(
                SecurityException.class,
                () -> mSdkSandboxManagerLocal.enforceAllowedToStartOrBindService(intent));
    }

    @Test
    public void testEnforceAllowedToStartOrBindService_allowedPackages() throws Exception {
        Intent intent =
                new Intent()
                        .setComponent(
                                new ComponentName(mInjector.getAdServicesPackageName(), "test"));
        mSdkSandboxManagerLocal.enforceAllowedToStartOrBindService(intent);
    }

    @Test
    @RequiresFlagsDisabled(FLAG_SERVICE_RESTRICTION_PACKAGE_NAME_LOGIC_UPDATED)
    public void testServiceRestriction_actionNotSet() {
        assertThrows(
                SecurityException.class,
                () ->
                        testServiceRestriction(
                                /* action= */ null,
                                /* packageName= */ null,
                                /* component= */ null));

        assertThrows(
                SecurityException.class,
                () ->
                        testServiceRestriction(
                                /* action= */ null,
                                /* packageName= */ PACKAGE_NAME,
                                /* component= */ null));

        assertThrows(
                SecurityException.class,
                () ->
                        testServiceRestriction(
                                /* action= */ null,
                                /* packageName= */ null,
                                /* component= */ COMPONENT));

        assertThrows(
                SecurityException.class,
                () ->
                        testServiceRestriction(
                                /* action= */ null,
                                /* packageName= */ PACKAGE_NAME,
                                /* component= */ COMPONENT));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SERVICE_RESTRICTION_PACKAGE_NAME_LOGIC_UPDATED)
    public void testServiceRestriction_packageNameNotSet() {
        ArrayMap<Integer, List<ArrayMap<String, String>>> allowedServices = new ArrayMap<>();
        List<ArrayMap<String, String>> services = new ArrayList<>();
        services.add(
                getAllowedServicesMap(
                        /* action= */ INTENT_ACTION,
                        /* packageName= */ "*",
                        /* componentClassName= */ "*",
                        /* componentPackageName= */ "*"));
        allowedServices.put(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, services);
        String encodedServiceAllowlist = ProtoUtil.encodeServiceAllowlist(allowedServices);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);

        assertThrows(
                SecurityException.class,
                () ->
                        testServiceRestriction(
                                /* action= */ INTENT_ACTION,
                                /* packageName= */ null,
                                /* component= */ null));

        testServiceRestriction(
                /* action= */ INTENT_ACTION, /* packageName= */ null, /* component= */ COMPONENT);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SERVICE_RESTRICTION_PACKAGE_NAME_LOGIC_UPDATED)
    public void testServiceRestriction_componentNameNotSet() {
        ArrayMap<Integer, List<ArrayMap<String, String>>> allowedServices = new ArrayMap<>();
        List<ArrayMap<String, String>> services = new ArrayList<>();
        services.add(
                getAllowedServicesMap(
                        /* action= */ INTENT_ACTION,
                        /* packageName= */ "*",
                        /* componentClassName= */ "*",
                        /* componentPackageName= */ "*"));
        allowedServices.put(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, services);
        String encodedServiceAllowlist = ProtoUtil.encodeServiceAllowlist(allowedServices);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);

        assertThrows(
                SecurityException.class,
                () ->
                        testServiceRestriction(
                                /* action= */ INTENT_ACTION,
                                /* packageName= */ null,
                                /* component= */ null));

        testServiceRestriction(
                /* action= */ INTENT_ACTION,
                /* packageName= */ PACKAGE_NAME,
                /* component= */ null);
    }

    @Test
    public void testServiceRestriction_noFieldsSet() {
        /*
         * Service allowlist
         * allowlist_per_target_sdk {
         *   key: 34
         *   value: {
         *     allowed_services: {
         *     }
         *   }
         * }
         */
        ArrayMap<Integer, List<ArrayMap<String, String>>> allowedServices = new ArrayMap<>();
        allowedServices.put(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, Arrays.asList());
        String encodedServiceAllowlist = ProtoUtil.encodeServiceAllowlist(allowedServices);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);

        /* Allows none of the services to start/ bind */
        assertThrows(
                SecurityException.class,
                () ->
                        testServiceRestriction(
                                /* action= */ INTENT_ACTION,
                                /* packageName= */ PACKAGE_NAME,
                                /* component= */ null));

        assertThrows(
                SecurityException.class,
                () ->
                        testServiceRestriction(
                                /* action= */ INTENT_ACTION,
                                /* packageName= */ null,
                                /* component= */ COMPONENT));
    }

    @Test
    public void testServiceRestriction_threeFieldsSet() {
        /*
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
        ArrayMap<Integer, List<ArrayMap<String, String>>> allowedServices = new ArrayMap<>();
        List<ArrayMap<String, String>> services = new ArrayList<>();
        services.add(
                getAllowedServicesMap(
                        /* action= */ INTENT_ACTION,
                        /* packageName= */ PACKAGE_NAME,
                        /* componentClassName= */ COMPONENT.getClassName(),
                        /* componentPackageName= */ "*"));
        services.add(
                getAllowedServicesMap(
                        /* action= */ INTENT_ACTION,
                        /* packageName= */ PACKAGE_NAME,
                        /* componentClassName= */ "*",
                        /* componentPackageName= */ COMPONENT.getPackageName()));
        services.add(
                getAllowedServicesMap(
                        /* action= */ INTENT_ACTION,
                        /* packageName= */ "*",
                        /* componentClassName= */ COMPONENT.getClassName(),
                        /* componentPackageName= */ COMPONENT.getPackageName()));
        services.add(
                getAllowedServicesMap(
                        /* action= */ "*",
                        /* packageName= */ PACKAGE_NAME,
                        /* componentClassName= */ COMPONENT.getClassName(),
                        /* componentPackageName= */ COMPONENT.getPackageName()));
        allowedServices.put(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, services);
        String encodedServiceAllowlist = ProtoUtil.encodeServiceAllowlist(allowedServices);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);

        testServiceRestriction(
                /* action= */ INTENT_ACTION,
                /* packageName= */ PACKAGE_NAME,
                /* component= */ new ComponentName(
                        COMPONENT.getPackageName(), "randomClassName.test"));

        testServiceRestriction(
                /* action= */ INTENT_ACTION,
                /* packageName= */ PACKAGE_NAME,
                /* component= */ new ComponentName(
                        "randomComponentPackageName.test", COMPONENT.getClassName()));

        testServiceRestriction(
                /* action= */ INTENT_ACTION, /* packageName= */ null, /* component= */ COMPONENT);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SERVICE_RESTRICTION_PACKAGE_NAME_LOGIC_UPDATED)
    public void testServiceRestriction_multipleEntriesAllowlist() {
        /*
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
        ArrayMap<Integer, List<ArrayMap<String, String>>> allowedServices = new ArrayMap<>();
        List<ArrayMap<String, String>> services = new ArrayList<>();
        services.add(
                getAllowedServicesMap(
                        "action.test1",
                        "packageName.test1",
                        "className.test1",
                        "componentPackageName.test1"));
        services.add(
                getAllowedServicesMap(
                        "action.test2",
                        "packageName.test2",
                        "className.test2",
                        "componentPackageName.test2"));
        allowedServices.put(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, services);
        String encodedServiceAllowlist = ProtoUtil.encodeServiceAllowlist(allowedServices);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);

        testServiceRestriction(
                "action.test1",
                "packageName.test1",
                new ComponentName("className.test1", "componentPackageName.test1"));

        testServiceRestriction(
                "action.test2",
                "packageName.test2",
                new ComponentName("className.test2", "componentPackageName.test2"));
    }

    @Test
    public void testServiceRestrictions_DeviceConfigNextAllowlistApplied() throws Exception {
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS, "true");
        /*
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
        ArrayMap<Integer, List<ArrayMap<String, String>>> allowedServices = new ArrayMap<>();
        List<ArrayMap<String, String>> services = new ArrayList<>();
        services.add(
                getAllowedServicesMap(
                        INTENT_ACTION,
                        PACKAGE_NAME,
                        COMPONENT.getClassName(),
                        /* componentPackageName= */ "*"));
        allowedServices.put(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, services);
        String encodedServiceAllowlist = ProtoUtil.encodeServiceAllowlist(allowedServices);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);

        /*
         * Service allowlist
         * allowed_services {
         *   action : "action.next"
         *   packageName : "packageName.next"
         *   componentClassName : "className.next"
         *   componentPackageName : "*"
         * }
         */
        List<ArrayMap<String, String>> allowedNextServices =
                new ArrayList<>(
                        Arrays.asList(
                                getAllowedServicesMap(
                                        "action.next",
                                        "packageName.next",
                                        "className.next",
                                        /* componentPackageName= */ "*")));
        String encodedNextServiceAllowlist = ProtoUtil.encodeServiceAllowlist(allowedNextServices);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_NEXT_SERVICE_ALLOWLIST, encodedNextServiceAllowlist);

        testServiceRestriction(
                "action.next",
                "packageName.next",
                new ComponentName("componentPackageName.next", "className.next"));

        assertThrows(
                SecurityException.class,
                () -> testServiceRestriction(INTENT_ACTION, PACKAGE_NAME, COMPONENT));
    }

    @Test
    public void testServiceRestrictions_ComponentNotSet() {
        /*
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
        ArrayMap<Integer, List<ArrayMap<String, String>>> allowedServices = new ArrayMap<>();
        List<ArrayMap<String, String>> services =
                new ArrayList<>(
                        Arrays.asList(
                                getAllowedServicesMap(
                                        INTENT_ACTION,
                                        /* packageName= */ "*",
                                        /* componentClassName= */ "*",
                                        /* componentPackageName= */ "*")));
        allowedServices.put(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, services);
        String encodedServiceAllowlist = ProtoUtil.encodeServiceAllowlist(allowedServices);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);

        Intent intent = new Intent(INTENT_ACTION).setPackage(PACKAGE_NAME);
        mSdkSandboxManagerLocal.enforceAllowedToStartOrBindService(intent);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SERVICE_RESTRICTION_PACKAGE_NAME_LOGIC_UPDATED)
    public void serviceRestrictionsDeviceConfig_setAllFieldsToWildcard_flagEnabled() {
        setServiceRestrictionsDeviceConfigSetAllFieldsToWildcard();
        testServiceRestriction(INTENT_ACTION, PACKAGE_NAME, COMPONENT);

        testServiceRestriction(INTENT_ACTION, PACKAGE_NAME, /* component= */ null);

        testServiceRestriction(INTENT_ACTION, /* packageName= */ null, COMPONENT);
        assertThrows(
                SecurityException.class,
                () -> {
                    testServiceRestriction(
                            INTENT_ACTION, /* packageName= */ null, /* component= */ null);
                });
    }

    @Test
    @RequiresFlagsDisabled(FLAG_SERVICE_RESTRICTION_PACKAGE_NAME_LOGIC_UPDATED)
    public void serviceRestrictionsDeviceConfig_setAllFieldsToWildcard_flagDisabled() {
        setServiceRestrictionsDeviceConfigSetAllFieldsToWildcard();
        testServiceRestriction(INTENT_ACTION, PACKAGE_NAME, COMPONENT);

        testServiceRestriction(INTENT_ACTION, PACKAGE_NAME, /* component= */ null);

        testServiceRestriction(INTENT_ACTION, /* packageName= */ null, COMPONENT);

        testServiceRestriction(INTENT_ACTION, /* packageName= */ null, /* component= */ null);
    }

    @Test
    @RequiresFlagsDisabled(FLAG_SERVICE_RESTRICTION_PACKAGE_NAME_LOGIC_UPDATED)
    public void testServiceRestrictions_AllFieldsSet_flagDisabled() {
        setServiceRestrictionsDeviceConfigSetAllFields();
        assertThrows(
                SecurityException.class,
                () -> {
                    testServiceRestriction(INTENT_ACTION, /* packageName= */ null, COMPONENT);
                });
        assertThrows(
                SecurityException.class,
                () -> {
                    testServiceRestriction(INTENT_ACTION, PACKAGE_NAME, /* component= */ null);
                });
        testServiceRestriction(INTENT_ACTION, PACKAGE_NAME, COMPONENT);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SERVICE_RESTRICTION_PACKAGE_NAME_LOGIC_UPDATED)
    public void testServiceRestrictions_AllFieldsSet_flagEnabled() {
        setServiceRestrictionsDeviceConfigSetAllFields();
        testServiceRestriction(INTENT_ACTION, /* packageName= */ null, COMPONENT);
        testServiceRestriction(INTENT_ACTION, PACKAGE_NAME, /* component= */ null);
        testServiceRestriction(INTENT_ACTION, PACKAGE_NAME, COMPONENT);
    }

    /**
     * Tests expected behavior when restrictions are enabled and only protected broadcasts included.
     */
    @Test
    public void testCanRegisterBroadcastReceiver_deviceConfigUnsetProtectedBroadcasts() {
        assertThat(
                        mSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(Intent.ACTION_SCREEN_OFF),
                                /*flags= */ 0,
                                /*onlyProtectedBroadcasts= */ true))
                .isTrue();
    }

    /** Tests expected behavior when restrictions are enabled and no protected broadcast. */
    @Test
    public void testCanRegisterBroadcastReceiver_deviceConfigUnsetUnprotectedBroadcasts() {
        assertThat(
                        mSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(Intent.ACTION_SEND),
                                /*flags= */ 0,
                                /*onlyProtectedBroadcasts= */ false))
                .isFalse();
    }

    /** Tests expected behavior when broadcast receiver restrictions are not applied. */
    @Test
    public void testCanRegisterBroadcastReceiver_restrictionsNotApplied() {
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_ENFORCE_RESTRICTIONS, "false");
        assertThat(
                        mSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(Intent.ACTION_SEND),
                                /*flags= */ 0,
                                /*onlyProtectedBroadcasts= */ false))
                .isTrue();
    }

    /** Tests expected behavior when broadcast receiver restrictions are applied. */
    @Test
    public void testCanRegisterBroadcastReceiver_restrictionsApplied() {
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_ENFORCE_RESTRICTIONS, "true");
        assertThat(
                        mSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(Intent.ACTION_SEND),
                                /*flags= */ 0,
                                /*onlyProtectedBroadcasts= */ false))
                .isFalse();
    }

    /** Tests expected behavior when callingUid is not a sandbox UID. */
    @Test
    public void testCanRegisterBroadcastReceiver_notSandboxProcess() {
        ExtendedMockito.doReturn(false).when(() -> Process.isSdkSandboxUid(Mockito.anyInt()));
        assertThat(
                        mSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(Intent.ACTION_SEND),
                                /*flags= */ 0,
                                /*onlyProtectedBroadcasts= */ false))
                .isTrue();
    }

    /** Tests expected behavior when IntentFilter is blank. */
    @Test
    public void testCanRegisterBroadcastReceiver_blankIntentFilter() {
        assertThat(
                        mSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
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
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_ENFORCE_RESTRICTIONS, "true");
        assertThat(
                        mSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(Intent.ACTION_SEND),
                                /*flags= */ Context.RECEIVER_NOT_EXPORTED,
                                /*onlyProtectedBroadcasts= */ true))
                .isTrue();
    }

    @Test
    public void testSdkSandboxSettings_canAccessContentProviderFromSdkSandbox_DefaultAccess() {
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_ENFORCE_RESTRICTIONS, null);
        // The default value of the flag enforcing restrictions is true and access should be
        // restricted.
        assertThat(
                        mSdkSandboxManagerLocal.canAccessContentProviderFromSdkSandbox(
                                new ProviderInfo()))
                .isFalse();
    }

    @Test
    public void testSdkSandboxSettings_canAccessContentProviderFromSdkSandbox_AccessNotAllowed() {
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_ENFORCE_RESTRICTIONS, "true");
        assertThat(
                        mSdkSandboxManagerLocal.canAccessContentProviderFromSdkSandbox(
                                new ProviderInfo()))
                .isFalse();
    }

    @Test
    public void testSdkSandboxSettings_canAccessContentProviderFromSdkSandbox_AccessAllowed() {
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_ENFORCE_RESTRICTIONS, "false");
        assertThat(
                        mSdkSandboxManagerLocal.canAccessContentProviderFromSdkSandbox(
                                new ProviderInfo()))
                .isTrue();
    }

    @Test
    public void setTestContentProviderAllowlist() {
        ProviderInfo testCp = new ProviderInfo();
        testCp.authority = "example";

        assertThat(mSdkSandboxManagerLocal.canAccessContentProviderFromSdkSandbox(testCp))
                .isFalse();

        SdkSandboxShellCommand cmd =
                new SdkSandboxShellCommand(
                        mService,
                        InstrumentationRegistry.getInstrumentation().getContext(),
                        true,
                        new ShellInjector());

        assertThat(
                        cmd.exec(
                                mService,
                                FileDescriptor.in,
                                FileDescriptor.out,
                                FileDescriptor.err,
                                new String[] {
                                    "append-test-allowlist", "content-provider", testCp.authority
                                }))
                .isEqualTo(0);

        assertThat(mSdkSandboxManagerLocal.canAccessContentProviderFromSdkSandbox(testCp)).isTrue();

        assertThat(
                        cmd.exec(
                                mService,
                                FileDescriptor.in,
                                FileDescriptor.out,
                                FileDescriptor.err,
                                new String[] {"clear-test-allowlists"}))
                .isEqualTo(0);

        assertThat(mSdkSandboxManagerLocal.canAccessContentProviderFromSdkSandbox(testCp))
                .isFalse();
    }

    @Test
    public void setTestSendBroadcastAllowlist() {
        Intent broadcastIntent = new Intent(Intent.ACTION_SCREEN_ON);
        assertThat(mSdkSandboxManagerLocal.canSendBroadcast(broadcastIntent)).isFalse();

        SdkSandboxShellCommand cmd =
                new SdkSandboxShellCommand(
                        mService,
                        InstrumentationRegistry.getInstrumentation().getContext(),
                        true,
                        new ShellInjector());

        assertThat(
                        cmd.exec(
                                mService,
                                FileDescriptor.in,
                                FileDescriptor.out,
                                FileDescriptor.err,
                                new String[] {
                                    "append-test-allowlist",
                                    "send-broadcast",
                                    broadcastIntent.getAction()
                                }))
                .isEqualTo(0);

        assertThat(mSdkSandboxManagerLocal.canSendBroadcast(broadcastIntent)).isTrue();

        assertThat(
                        cmd.exec(
                                mService,
                                FileDescriptor.in,
                                FileDescriptor.out,
                                FileDescriptor.err,
                                new String[] {"clear-test-allowlists"}))
                .isEqualTo(0);

        assertThat(mSdkSandboxManagerLocal.canSendBroadcast(broadcastIntent)).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SDKSANDBOX_USE_EFFECTIVE_TARGET_SDK_VERSION_FOR_RESTRICTIONS)
    public void
            testCanRegisterBroadcastReceiver_withAllowlistAndDifferentEffectiveTargetSdkVersion()
                    throws Exception {
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_ENFORCE_RESTRICTIONS, "true");
        ArrayMap<Integer, List<String>> allowedBroadcastReceivers = new ArrayMap<>();
        allowedBroadcastReceivers.put(
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                new ArrayList<>(Arrays.asList(Intent.ACTION_VIEW)));
        allowedBroadcastReceivers.put(
                /* target_sdk_version= */ 35,
                new ArrayList<>(Arrays.asList(Intent.ACTION_SCREEN_OFF)));
        String encodedAllowlist =
                ProtoUtil.encodeBroadcastReceiverAllowlist(allowedBroadcastReceivers);

        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_BROADCASTRECEIVER_ALLOWLIST, encodedAllowlist);

        expect.that(
                        mSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(Intent.ACTION_VIEW),
                                /* flags= */ Context.RECEIVER_NOT_EXPORTED,
                                /* onlyProtectedBroadcasts= */ false))
                .isTrue();

        expect.that(
                        mSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(Intent.ACTION_SCREEN_OFF),
                                /* flags= */ Context.RECEIVER_NOT_EXPORTED,
                                /* onlyProtectedBroadcasts= */ false))
                .isFalse();

        Mockito.doReturn(35)
                .when(mSdkSandboxRestrictionManager)
                .getEffectiveTargetSdkVersion(Mockito.anyInt());
        expect.that(
                        mSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(Intent.ACTION_SCREEN_OFF),
                                /* flags= */ Context.RECEIVER_NOT_EXPORTED,
                                /* onlyProtectedBroadcasts= */ false))
                .isTrue();

        expect.that(
                        mSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(Intent.ACTION_VIEW),
                                /* flags= */ Context.RECEIVER_NOT_EXPORTED,
                                /* onlyProtectedBroadcasts= */ false))
                .isFalse();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SDKSANDBOX_USE_EFFECTIVE_TARGET_SDK_VERSION_FOR_RESTRICTIONS)
    public void testCanAccessContentProvider_withAllowlistAndDifferentEffectiveTargetSdkVersion()
            throws Exception {
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_ENFORCE_RESTRICTIONS, "true");
        ArrayMap<Integer, List<String>> allowedAuthorities = new ArrayMap<>();
        allowedAuthorities.put(
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                new ArrayList<>(Arrays.asList("com.android.textclassifier.icons")));
        allowedAuthorities.put(
                /* target_sdk_version= */ 35, new ArrayList<>(Arrays.asList("user_dictionary")));
        String encodedAllowlist = ProtoUtil.encodeContentProviderAllowlist(allowedAuthorities);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_CONTENTPROVIDER_ALLOWLIST, encodedAllowlist);

        ProviderInfo providerInfo1 = new ProviderInfo();
        providerInfo1.authority = "com.android.textclassifier.icons";

        ProviderInfo providerInfo2 = new ProviderInfo();
        providerInfo2.authority = "user_dictionary";

        expect.that(mSdkSandboxManagerLocal.canAccessContentProviderFromSdkSandbox(providerInfo1))
                .isTrue();

        expect.that(mSdkSandboxManagerLocal.canAccessContentProviderFromSdkSandbox(providerInfo2))
                .isFalse();

        Mockito.doReturn(35)
                .when(mSdkSandboxRestrictionManager)
                .getEffectiveTargetSdkVersion(Mockito.anyInt());

        expect.that(mSdkSandboxManagerLocal.canAccessContentProviderFromSdkSandbox(providerInfo1))
                .isFalse();

        expect.that(mSdkSandboxManagerLocal.canAccessContentProviderFromSdkSandbox(providerInfo2))
                .isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SDKSANDBOX_USE_EFFECTIVE_TARGET_SDK_VERSION_FOR_RESTRICTIONS)
    public void testCanStartActivity_withAllowlistAndDifferentEffectiveTargetSdkVersion()
            throws Exception {
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_ENFORCE_RESTRICTIONS, "true");
        ArrayMap<Integer, List<String>> allowedActivities = new ArrayMap<>();
        allowedActivities.put(
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                new ArrayList<>(Arrays.asList(Intent.ACTION_CALL)));
        allowedActivities.put(
                /* target_sdk_version= */ 35, new ArrayList<>(Arrays.asList(Intent.ACTION_VIEW)));
        String encodedAllowlist = ProtoUtil.encodeContentProviderAllowlist(allowedActivities);
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_ACTIVITY_ALLOWLIST, encodedAllowlist);

        Intent intent1 = new Intent(Intent.ACTION_CALL);
        Intent intent2 = new Intent(Intent.ACTION_VIEW);

        Mockito.doReturn(34)
                .when(mSdkSandboxRestrictionManager)
                .getEffectiveTargetSdkVersion(Mockito.anyInt());

        // No exception thrown as ACTION_CALL is allowed for Android 34
        mSdkSandboxManagerLocal.enforceAllowedToStartActivity(intent1);

        SecurityException thrown =
                assertThrows(
                        SecurityException.class,
                        () -> mSdkSandboxManagerLocal.enforceAllowedToStartActivity(intent2));
        assertThat(thrown).hasMessageThat().contains("may not be started from an SDK sandbox uid.");

        Mockito.doReturn(35)
                .when(mSdkSandboxRestrictionManager)
                .getEffectiveTargetSdkVersion(Mockito.anyInt());

        // No exception thrown as ACTION_CALL is allowed for Android 35
        mSdkSandboxManagerLocal.enforceAllowedToStartActivity(intent2);

        thrown =
                assertThrows(
                        SecurityException.class,
                        () -> mSdkSandboxManagerLocal.enforceAllowedToStartActivity(intent1));
        assertThat(thrown).hasMessageThat().contains("may not be started from an SDK sandbox uid.");
    }

    private void testServiceRestriction(
            @Nullable String action,
            @Nullable String packageName,
            @Nullable ComponentName component) {
        Intent intent = Objects.isNull(action) ? new Intent() : new Intent(action);
        if (packageName != null) {
            intent.setPackage(packageName);
        }

        if (component != null) {
            intent.setComponent(component);
        }

        mSdkSandboxManagerLocal.enforceAllowedToStartOrBindService(intent);
    }

    private void setServiceRestrictionsDeviceConfigSetAllFields() {
        /*
         * Service allowlist
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
        ArrayMap<Integer, List<ArrayMap<String, String>>> allowedServices = new ArrayMap<>();
        List<ArrayMap<String, String>> services =
                new ArrayList<>(
                        Arrays.asList(
                                getAllowedServicesMap(
                                        /* action= */ INTENT_ACTION,
                                        /* packageName= */ PACKAGE_NAME,
                                        /* componentClassName= */ COMPONENT.getClassName(),
                                        /* componentPackageName= */ COMPONENT.getPackageName())));
        allowedServices.put(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, services);
        String encodedServiceAllowlist = ProtoUtil.encodeServiceAllowlist(allowedServices);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);
    }

    private void setServiceRestrictionsDeviceConfigSetAllFieldsToWildcard() {
        /*
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
        ArrayMap<Integer, List<ArrayMap<String, String>>> allowedServices = new ArrayMap<>();
        List<ArrayMap<String, String>> services =
                new ArrayList<>(
                        Arrays.asList(
                                getAllowedServicesMap(
                                        /* action= */ "*",
                                        /* packageName= */ "*",
                                        /* componentClassName= */ "*",
                                        /* componentPackageName= */ "*")));
        allowedServices.put(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, services);
        String encodedServiceAllowlist = ProtoUtil.encodeServiceAllowlist(allowedServices);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);
    }

    private ArrayMap<String, String> getAllowedServicesMap(
            String action,
            String packageName,
            String componentClassName,
            String componentPackageName) {
        ArrayMap<String, String> data = new ArrayMap<>(/* capacity= */ 4);
        data.put("action", action);
        data.put("packageName", packageName);
        data.put("componentClassName", componentClassName);
        data.put("componentPackageName", componentPackageName);
        return data;
    }

    private static class ShellInjector extends SdkSandboxShellCommand.Injector {
        @Override
        int getCallingUid() {
            return Process.SHELL_UID;
        }
    }
}
