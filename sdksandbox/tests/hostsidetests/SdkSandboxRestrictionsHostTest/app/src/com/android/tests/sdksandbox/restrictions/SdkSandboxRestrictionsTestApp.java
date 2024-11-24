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

package com.android.tests.sdksandbox.restrictions;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.ConfigListener;
import android.app.sdksandbox.testutils.DeviceConfigUtils;
import android.app.sdksandbox.testutils.EmptyActivity;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.ProtoUtil;
import android.app.sdksandbox.testutils.SdkLifecycleHelper;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.DeviceConfig;
import android.util.ArrayMap;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.tests.sdkprovider.restrictions.IRestrictionsSdkApi;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(JUnit4.class)
public class SdkSandboxRestrictionsTestApp {
    private SdkSandboxManager mSdkSandboxManager;
    private static final String SDK_PACKAGE = "com.android.tests.sdkprovider.restrictions";
    // Keep consistent with SdkSandboxManagerService.PROPERTY_ENFORCE_RESTRICTIONS
    private static final String PROPERTY_ENFORCE_RESTRICTIONS = "sdksandbox_enforce_restrictions";

    // Keep consistent with SdkSandboxManagerService.PROPERTY_BROADCASTRECEIVER_ALLOWLIST
    private static final String PROPERTY_BROADCASTRECEIVER_ALLOWLIST =
            "sdksandbox_broadcastreceiver_allowlist_per_targetSdkVersion";

    // Keep the value consistent with
    // SdkSandboxSettingsListener.PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS.
    private static final String PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS =
            "apply_sdk_sandbox_next_restrictions";

    private static final String NAMESPACE = DeviceConfig.NAMESPACE_ADSERVICES;
    private String mEnforceBroadcastRestrictions;
    private String mInitialBroadcastReceiverAllowlistValue;
    private String mInitialApplySdkSandboxNextRestrictionsValue;

    private ConfigListener mConfigListener;
    private DeviceConfigUtils mDeviceConfigUtils;
    private IRestrictionsSdkApi mRestrictionsSdkApi;
    private SdkLifecycleHelper mSdkLifecycleHelper;

    /** This rule is defined to start an activity in the foreground to call the sandbox APIs */
    @Rule(order = 0)
    public final ActivityScenarioRule rule = new ActivityScenarioRule<>(EmptyActivity.class);

    @Before
    public void setup() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        mSdkLifecycleHelper = new SdkLifecycleHelper(context);
        mSdkSandboxManager = context.getSystemService(SdkSandboxManager.class);
        assertThat(mSdkSandboxManager).isNotNull();

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.WRITE_DEVICE_CONFIG,
                        Manifest.permission.WRITE_ALLOWLISTED_DEVICE_CONFIG,
                        Manifest.permission.READ_DEVICE_CONFIG);

        mConfigListener = new ConfigListener();
        DeviceConfig.addOnPropertiesChangedListener(
                NAMESPACE, context.getMainExecutor(), mConfigListener);
        mDeviceConfigUtils = new DeviceConfigUtils(mConfigListener, NAMESPACE);

        mEnforceBroadcastRestrictions =
                DeviceConfig.getProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_ENFORCE_RESTRICTIONS);
        mInitialBroadcastReceiverAllowlistValue =
                DeviceConfig.getProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_BROADCASTRECEIVER_ALLOWLIST);
        mInitialApplySdkSandboxNextRestrictionsValue =
                DeviceConfig.getProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS);

        mDeviceConfigUtils.setProperty(PROPERTY_ENFORCE_RESTRICTIONS, "true");

        ArrayMap<Integer, List<String>> allowedBroadcastReceivers = new ArrayMap<>();
        allowedBroadcastReceivers.put(
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                new ArrayList<>(Arrays.asList(Intent.ACTION_VIEW, Intent.ACTION_SCREEN_OFF)));
        String encodedAllowlist =
                ProtoUtil.encodeBroadcastReceiverAllowlist(allowedBroadcastReceivers);

        mDeviceConfigUtils.setProperty(PROPERTY_BROADCASTRECEIVER_ALLOWLIST, encodedAllowlist);
        mDeviceConfigUtils.setProperty(PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS, "false");

        // Unload the SDK before running tests to ensure that the SDK is not loaded before running a
        // test
        mSdkLifecycleHelper.unloadSdk(SDK_PACKAGE);

        rule.getScenario();
    }

    @After
    public void tearDown() throws Exception {
        mDeviceConfigUtils.resetToInitialValue(
                PROPERTY_ENFORCE_RESTRICTIONS, mEnforceBroadcastRestrictions);
        mDeviceConfigUtils.resetToInitialValue(
                PROPERTY_BROADCASTRECEIVER_ALLOWLIST, mInitialBroadcastReceiverAllowlistValue);
        mDeviceConfigUtils.resetToInitialValue(
                PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS,
                mInitialApplySdkSandboxNextRestrictionsValue);

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();

        // Greedily unload SDK to reduce flakiness
        mSdkLifecycleHelper.unloadSdk(SDK_PACKAGE);
    }

    @Test
    public void populateETSV() throws Exception {
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        IBinder binder = sandboxedSdk.getInterface();
        mRestrictionsSdkApi = IRestrictionsSdkApi.Stub.asInterface(binder);

        mRestrictionsSdkApi.registerBroadcastReceiver(
                new ArrayList<>(Arrays.asList(Intent.ACTION_SCREEN_OFF)));
    }
}
