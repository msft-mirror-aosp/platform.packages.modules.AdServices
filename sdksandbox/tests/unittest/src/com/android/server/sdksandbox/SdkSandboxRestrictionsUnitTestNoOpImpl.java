/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static android.app.sdksandbox.flags.Flags.FLAG_SDK_SANDBOX_NO_OP_IMPL;

import static com.android.server.wm.ActivityInterceptorCallback.MAINLINE_SDK_SANDBOX_ORDER_ID;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;

import android.Manifest;
import android.app.sdksandbox.testutils.FakeSdkSandboxManagerLocal;
import android.app.sdksandbox.testutils.FakeSdkSandboxService;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Process;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSessionBuilder;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.pm.PackageManagerLocal;
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

@RequiresFlagsEnabled(FLAG_SDK_SANDBOX_NO_OP_IMPL)
public class SdkSandboxRestrictionsUnitTestNoOpImpl extends DeviceSupportedBaseTest {

    private SdkSandboxManagerService mService;
    private MockitoSession mStaticMockSession;
    private ArgumentCaptor<ActivityInterceptorCallback> mInterceptorCallbackArgumentCaptor =
            ArgumentCaptor.forClass(ActivityInterceptorCallback.class);
    private SdkSandboxManagerLocal mSdkSandboxManagerLocal;
    private SdkSandboxSettingsListener mSdkSandboxSettingsListener;
    private SdkSandboxManagerService.Injector mInjector;
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
                                        Mockito.mock(SdkSandboxSettingsListener.class),
                                        Mockito.spy(PackageManagerLocal.class),
                                        /* rootDir= */ context.getDir(
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

        ExtendedMockito.doReturn(true).when(() -> Process.isSdkSandboxUid(Mockito.anyInt()));
    }

    @After
    public void tearDown() {
        if (mSdkSandboxSettingsListener != null) {
            mSdkSandboxSettingsListener.unregisterPropertiesListener();
        }

        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    /** Tests that no broadcast can be sent from the sdk sandbox. */
    @Test
    public void testCanSendBroadcast() {
        assertThat(mSdkSandboxManagerLocal.canSendBroadcast(new Intent())).isTrue();
    }

    @Test
    public void testEnforceAllowedToSendBroadcast() {
        Intent disallowedIntent = new Intent(Intent.ACTION_SCREEN_ON);
        mSdkSandboxManagerLocal.enforceAllowedToSendBroadcast(disallowedIntent);
    }

    @Test
    public void testCanRegisterBroadcastReceiver() {
        assertThat(
                        mSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(Intent.ACTION_SCREEN_OFF),
                                /* flags= */ 0,
                                /* onlyProtectedBroadcasts= */ true))
                .isTrue();
    }
}
