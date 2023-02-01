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

package com.android.tests.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.EmptyActivity;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.DeviceConfig;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.tests.sdkprovider.restrictions.broadcasts.IBroadcastSdkApi;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BroadcastRestrictionsTestApp {
    private SdkSandboxManager mSdkSandboxManager;
    private static final String ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS =
            "enforce_broadcast_receiver_restrictions";

    private static final String SDK_PACKAGE =
            "com.android.tests.sdkprovider.restrictions.broadcasts";

    private Boolean mEnforceBroadcastRestrictions = null;

    /** This rule is defined to start an activity in the foreground to call the sandbox APIs */
    @Rule public final ActivityScenarioRule mRule = new ActivityScenarioRule<>(EmptyActivity.class);

    @Before
    public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        mSdkSandboxManager = context.getSystemService(SdkSandboxManager.class);
        assertThat(mSdkSandboxManager).isNotNull();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.WRITE_DEVICE_CONFIG,
                        Manifest.permission.READ_DEVICE_CONFIG);
        DeviceConfig.Properties properties =
                DeviceConfig.getProperties(
                        DeviceConfig.NAMESPACE_SDK_SANDBOX,
                        ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS);
        if (properties.getKeyset().contains(ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS)) {
            mEnforceBroadcastRestrictions =
                    properties.getBoolean(
                            ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS, /*defaultValue=*/ false);
        }
        mRule.getScenario();
    }

    @After
    public void tearDown() {
        /**
         * We check if the properties contain enforce_broadcast_receiver_restrictions key, and
         * update the property to the pre-test value
         */
        if (mEnforceBroadcastRestrictions != null) {
            DeviceConfig.setProperty(
                    DeviceConfig.NAMESPACE_SDK_SANDBOX,
                    ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS,
                    String.valueOf(mEnforceBroadcastRestrictions),
                    /*makeDefault=*/ false);
        }
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /**
     * Tests that a SecurityException is not thrown when SDK sandbox process tries to register a
     * broadcast receiver because the default value of false.
     */
    @Test
    public void testRegisterBroadcastReceiver_defaultValueRestrictionsNotApplied()
            throws Exception {
        /** Ensuring that the property is not present in DeviceConfig */
        DeviceConfig.deleteProperty(
                DeviceConfig.NAMESPACE_ADSERVICES, ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS);
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        IBinder binder = sandboxedSdk.getInterface();
        IBroadcastSdkApi broadcastSdkApi = IBroadcastSdkApi.Stub.asInterface(binder);

        broadcastSdkApi.registerBroadcastReceiver();
    }

    /**
     * Tests that a SecurityException is thrown when SDK sandbox process tries to register a
     * broadcast receiver. This behavior depends on the value of a {@link DeviceConfig} property.
     */
    @Test
    public void testRegisterBroadcastReceiver_restrictionsApplied() throws Exception {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS,
                "true",
                false);

        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        IBinder binder = sandboxedSdk.getInterface();
        IBroadcastSdkApi broadcastSdkApi = IBroadcastSdkApi.Stub.asInterface(binder);

        assertThrows(SecurityException.class, () -> broadcastSdkApi.registerBroadcastReceiver());
    }

    /**
     * Tests that a SecurityException is not thrown when SDK sandbox process tries to register a
     * broadcast receiver. This behavior depends on the value of a {@link DeviceConfig} property.
     */
    @Test(expected = Test.None.class /* no exception expected */)
    public void testRegisterBroadcastReceiver_restrictionsNotApplied() throws Exception {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS,
                "false",
                false);

        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        IBinder binder = sandboxedSdk.getInterface();
        IBroadcastSdkApi broadcastSdkApi = IBroadcastSdkApi.Stub.asInterface(binder);

        broadcastSdkApi.registerBroadcastReceiver();
    }
}
