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
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.EmptyActivity;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.DeviceConfig;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.modules.utils.build.SdkLevel;
import com.android.tests.sdkprovider.restrictions.broadcasts.IBroadcastSdkApi;

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
public class BroadcastRestrictionsTestApp {
    private SdkSandboxManager mSdkSandboxManager;
    private static final String PROPERTY_ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS =
            "enforce_broadcast_receiver_restrictions";
    private static final String PROPERTY_ENFORCE_RESTRICTIONS = "sdksandbox_enforce_restrictions";

    // Keep consistent with SdkSandboxManagerService.PROPERTY_BROADCASTRECEIVER_ALLOWLIST
    private static final String PROPERTY_BROADCASTRECEIVER_ALLOWLIST =
            "sdksandbox_broadcastreceiver_allowlist_per_targetSdkVersion";

    // Keep the value consistent with
    // SdkSandboxManagerService.PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS.
    private static final String PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS =
            "apply_sdk_sandbox_next_restrictions";

    // Keep the value consistent with
    // SdkSandboxManagerService.PROPERTY_NEXT_BROADCASTRECEIVER_ALLOWLIST.
    private static final String PROPERTY_NEXT_BROADCASTRECEIVER_ALLOWLIST =
            "sdksandbox_next_broadcastreceiver_allowlist";

    private static final String SDK_PACKAGE =
            "com.android.tests.sdkprovider.restrictions.broadcasts";

    private String mEnforceBroadcastRestrictionsSandboxNamespace;
    private String mEnforceBroadcastRestrictionsAdServicesNamespace;
    private String mInitialBroadcastReceiverAllowlistValue;
    private String mInitialApplySdkSandboxNextRestrictionsValue;
    private String mInitialNextBroadcastReceiverAllowlistValue;

    private static final List<String> INTENT_ACTIONS =
            new ArrayList<>(Arrays.asList(Intent.ACTION_SEND, Intent.ACTION_VIEW));

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

        mEnforceBroadcastRestrictionsSandboxNamespace =
                DeviceConfig.getProperty(
                        DeviceConfig.NAMESPACE_SDK_SANDBOX,
                        PROPERTY_ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS);
        mEnforceBroadcastRestrictionsAdServicesNamespace =
                DeviceConfig.getProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_ENFORCE_RESTRICTIONS);
        mInitialBroadcastReceiverAllowlistValue =
                DeviceConfig.getProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_BROADCASTRECEIVER_ALLOWLIST);
        mInitialApplySdkSandboxNextRestrictionsValue =
                DeviceConfig.getProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS);
        mInitialNextBroadcastReceiverAllowlistValue =
                DeviceConfig.getProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        PROPERTY_NEXT_BROADCASTRECEIVER_ALLOWLIST);

        DeviceConfig.deleteProperty(
                DeviceConfig.NAMESPACE_SDK_SANDBOX,
                PROPERTY_ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS);
        DeviceConfig.deleteProperty(
                DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_ENFORCE_RESTRICTIONS);
        DeviceConfig.deleteProperty(
                DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_BROADCASTRECEIVER_ALLOWLIST);
        DeviceConfig.deleteProperty(
                DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS);
        DeviceConfig.deleteProperty(
                DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_NEXT_BROADCASTRECEIVER_ALLOWLIST);

        mRule.getScenario();

        // Greedily unload SDK to reduce flakiness
        mSdkSandboxManager.unloadSdk(SDK_PACKAGE);
    }

    @After
    public void tearDown() {
        /**
         * We check if the properties contain enforce_broadcast_receiver_restrictions key, and
         * update the property to the pre-test value
         */
        if (mEnforceBroadcastRestrictionsSandboxNamespace != null) {
            DeviceConfig.setProperty(
                    DeviceConfig.NAMESPACE_SDK_SANDBOX,
                    PROPERTY_ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS,
                    String.valueOf(mEnforceBroadcastRestrictionsSandboxNamespace),
                    /*makeDefault=*/ false);
        } else {
            DeviceConfig.deleteProperty(
                    DeviceConfig.NAMESPACE_SDK_SANDBOX,
                    PROPERTY_ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS);
        }

        /**
         * We check if the properties contain enforce_restrictions key, and update the property to
         * the pre-test value
         */
        if (mEnforceBroadcastRestrictionsAdServicesNamespace != null) {
            DeviceConfig.setProperty(
                    DeviceConfig.NAMESPACE_ADSERVICES,
                    PROPERTY_ENFORCE_RESTRICTIONS,
                    String.valueOf(mEnforceBroadcastRestrictionsAdServicesNamespace),
                    /*makeDefault=*/ false);
        } else {
            DeviceConfig.deleteProperty(
                    DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_ENFORCE_RESTRICTIONS);
        }

        if (mInitialBroadcastReceiverAllowlistValue != null) {
            DeviceConfig.setProperty(
                    DeviceConfig.NAMESPACE_ADSERVICES,
                    PROPERTY_BROADCASTRECEIVER_ALLOWLIST,
                    mInitialBroadcastReceiverAllowlistValue, /*makeDefault*/
                    false);
        } else {
            DeviceConfig.deleteProperty(
                    DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_BROADCASTRECEIVER_ALLOWLIST);
        }

        if (mInitialApplySdkSandboxNextRestrictionsValue != null) {
            DeviceConfig.setProperty(
                    DeviceConfig.NAMESPACE_ADSERVICES,
                    PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS,
                    mInitialApplySdkSandboxNextRestrictionsValue,
                    /*makeDefault=*/ false);
        } else {
            DeviceConfig.deleteProperty(
                    DeviceConfig.NAMESPACE_ADSERVICES,
                    PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS);
        }

        if (mInitialNextBroadcastReceiverAllowlistValue != null) {
            DeviceConfig.setProperty(
                    DeviceConfig.NAMESPACE_ADSERVICES,
                    PROPERTY_NEXT_BROADCASTRECEIVER_ALLOWLIST,
                    mInitialNextBroadcastReceiverAllowlistValue,
                    /*makeDefault=*/ false);
        } else {
            DeviceConfig.deleteProperty(
                    DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_NEXT_BROADCASTRECEIVER_ALLOWLIST);
        }

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();

        // Greedily unload SDK to reduce flakiness
        mSdkSandboxManager.unloadSdk(SDK_PACKAGE);
    }

    /**
     * Tests that a SecurityException is thrown when SDK sandbox process tries to register a
     * broadcast receiver because of the default value of true.
     */
    @Test
    public void testRegisterBroadcastReceiver_defaultValueRestrictionsApplied() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());

        /** Ensuring that the property is not present in DeviceConfig */
        DeviceConfig.deleteProperty(
                DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_ENFORCE_RESTRICTIONS);
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        IBinder binder = sandboxedSdk.getInterface();
        IBroadcastSdkApi broadcastSdkApi = IBroadcastSdkApi.Stub.asInterface(binder);

        assertThrows(
                SecurityException.class,
                () -> broadcastSdkApi.registerBroadcastReceiver(INTENT_ACTIONS));
    }

    /**
     * Tests that a SecurityException is thrown when SDK sandbox process tries to register a
     * broadcast receiver. This behavior depends on the value of a {@link DeviceConfig} property.
     */
    @Test
    public void testRegisterBroadcastReceiver_restrictionsApplied() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_ENFORCE_RESTRICTIONS, "true", false);

        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        IBinder binder = sandboxedSdk.getInterface();
        IBroadcastSdkApi broadcastSdkApi = IBroadcastSdkApi.Stub.asInterface(binder);

        final SecurityException thrown =
                assertThrows(
                        SecurityException.class,
                        () -> broadcastSdkApi.registerBroadcastReceiver(INTENT_ACTIONS));

        assertThat(thrown).hasMessageThat().contains("android.intent.action.SEND");
        assertThat(thrown).hasMessageThat().contains("android.intent.action.VIEW");
        assertThat(thrown)
                .hasMessageThat()
                .contains(
                        "SDK sandbox not allowed to register receiver with the given IntentFilter");
    }

    /**
     * Tests that a SecurityException is not thrown when SDK sandbox process tries to register a
     * broadcast receiver. This behavior depends on the value of a {@link DeviceConfig} property.
     */
    @Test(expected = Test.None.class /* no exception expected */)
    public void testRegisterBroadcastReceiver_restrictionsNotApplied() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_ENFORCE_RESTRICTIONS, "false", false);

        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        IBinder binder = sandboxedSdk.getInterface();
        IBroadcastSdkApi broadcastSdkApi = IBroadcastSdkApi.Stub.asInterface(binder);

        broadcastSdkApi.registerBroadcastReceiver(INTENT_ACTIONS);
    }

    @Test
    public void testRegisterBroadcastReceiver_DeviceConfigEmptyAllowlistApplied() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                PROPERTY_ENFORCE_RESTRICTIONS,
                "true",
                /*makeDefault=*/ false);

        // Set an empty allowlist for effectiveTargetSdkVersion U. This should block all
        // BroadcastReceivers.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                PROPERTY_BROADCASTRECEIVER_ALLOWLIST,
                "CgQIIhIA",
                /*makeDefault=*/ false);

        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        IBinder binder = sandboxedSdk.getInterface();
        IBroadcastSdkApi broadcastSdkApi = IBroadcastSdkApi.Stub.asInterface(binder);

        assertThrows(
                SecurityException.class,
                () -> broadcastSdkApi.registerBroadcastReceiver(INTENT_ACTIONS));

        // Even protected broadcasts should be blocked.
        assertThrows(
                SecurityException.class,
                () ->
                        broadcastSdkApi.registerBroadcastReceiver(
                                new ArrayList<>(Arrays.asList(Intent.ACTION_SCREEN_OFF))));
    }

    @Test
    public void testRegisterBroadcastReceiver_DeviceConfigAllowlistApplied() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                PROPERTY_ENFORCE_RESTRICTIONS,
                "true",
                /*makeDefault=*/ false);

        // Set an allowlist mapping from U to {android.intent.action.VIEW,
        // android.intent.action.SCREEN_OFF}
        final String encodedAllowlist =
                "CkIIIhI+ChphbmRyb2lkLmludGVudC5hY3Rpb24uVklFVwogYW5kcm9pZC5pbnRlbnQuYWN0aW9uLlNDUk"
                        + "VFTl9PRkY=";
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                PROPERTY_BROADCASTRECEIVER_ALLOWLIST,
                encodedAllowlist,
                /*makeDefault=*/ false);

        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        IBinder binder = sandboxedSdk.getInterface();
        IBroadcastSdkApi broadcastSdkApi = IBroadcastSdkApi.Stub.asInterface(binder);

        broadcastSdkApi.registerBroadcastReceiver(
                new ArrayList<>(Arrays.asList(Intent.ACTION_VIEW)));
        broadcastSdkApi.registerBroadcastReceiver(
                new ArrayList<>(Arrays.asList(Intent.ACTION_SCREEN_OFF)));
        broadcastSdkApi.registerBroadcastReceiver(
                new ArrayList<>(Arrays.asList(Intent.ACTION_VIEW, Intent.ACTION_SCREEN_OFF)));
        assertThrows(
                SecurityException.class,
                () ->
                        broadcastSdkApi.registerBroadcastReceiver(
                                new ArrayList<>(Arrays.asList(Intent.ACTION_BATTERY_CHANGED))));
        assertThrows(
                SecurityException.class,
                () ->
                        broadcastSdkApi.registerBroadcastReceiver(
                                new ArrayList<>(Arrays.asList(Intent.ACTION_SEND))));
        assertThrows(
                SecurityException.class,
                () -> broadcastSdkApi.registerBroadcastReceiver(INTENT_ACTIONS));
    }

    @Test
    public void testRegisterBroadcastReceiver_DeviceConfigNextAllowlistApplied() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_ENFORCE_RESTRICTIONS, "true", false);

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS,
                "true",
                false);

        // Base64 encoded proto AllowedBroadcastReceivers containing the strings Intent.ACTION_VIEW
        // and Intent.ACTION_SEND.
        final String encodedNextAllowlist =
                "ChphbmRyb2lkLmludGVudC5hY3Rpb24uVklFVwoaYW5kcm9pZC5pbnRlbnQuYWN0aW9uLlNFTkQ=";
        // Set the canary set.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                PROPERTY_NEXT_BROADCASTRECEIVER_ALLOWLIST,
                encodedNextAllowlist,
                false);

        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        final SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        final IBinder binder = sandboxedSdk.getInterface();
        final IBroadcastSdkApi broadcastSdkApi = IBroadcastSdkApi.Stub.asInterface(binder);

        // No exception should be thrown when registering a BroadcastReceiver with
        // Intent.ACTION_VIEW and Intent.ACTION_SEND.
        broadcastSdkApi.registerBroadcastReceiver(new ArrayList<>(List.of(Intent.ACTION_VIEW)));
        broadcastSdkApi.registerBroadcastReceiver(new ArrayList<>(List.of(Intent.ACTION_SEND)));
        broadcastSdkApi.registerBroadcastReceiver(INTENT_ACTIONS);
        assertThrows(
                SecurityException.class,
                () ->
                        broadcastSdkApi.registerBroadcastReceiver(
                                new ArrayList<>(List.of(Intent.ACTION_SCREEN_OFF))));
        assertThrows(
                SecurityException.class,
                () ->
                        broadcastSdkApi.registerBroadcastReceiver(
                                new ArrayList<>(
                                        Arrays.asList(
                                                Intent.ACTION_SEND, Intent.ACTION_SCREEN_OFF))));
    }

    @Test
    public void testRegisterBroadcastReceiver_DeviceConfigNextRestrictions_AllowlistNotSet()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_ENFORCE_RESTRICTIONS, "true", false);

        // Apply next restrictions, but don't set any value for the allowlist.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS,
                "true",
                false);

        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        final SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        final IBinder binder = sandboxedSdk.getInterface();
        final IBroadcastSdkApi broadcastSdkApi = IBroadcastSdkApi.Stub.asInterface(binder);

        // No exception should be thrown when it is a protected broadcast.
        broadcastSdkApi.registerBroadcastReceiver(
                new ArrayList<>(List.of(Intent.ACTION_SCREEN_OFF)));
        assertThrows(
                SecurityException.class,
                () -> broadcastSdkApi.registerBroadcastReceiver(INTENT_ACTIONS));
    }

    @Test
    public void testRegisterBroadcastReceiver_defaultValueRestrictionsNotApplied_preU()
            throws Exception {
        if (SdkLevel.isAtLeastU()) {
            return;
        }

        /** Ensuring that the property is not present in DeviceConfig */
        DeviceConfig.deleteProperty(
                DeviceConfig.NAMESPACE_SDK_SANDBOX,
                PROPERTY_ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS);
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        IBinder binder = sandboxedSdk.getInterface();
        IBroadcastSdkApi broadcastSdkApi = IBroadcastSdkApi.Stub.asInterface(binder);

        broadcastSdkApi.registerBroadcastReceiver(INTENT_ACTIONS);
    }

    @Test
    public void testRegisterBroadcastReceiver_restrictionsApplied_preU() throws Exception {
        if (SdkLevel.isAtLeastU()) {
            return;
        }

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_SDK_SANDBOX,
                PROPERTY_ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS,
                "true",
                false);

        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        IBinder binder = sandboxedSdk.getInterface();
        IBroadcastSdkApi broadcastSdkApi = IBroadcastSdkApi.Stub.asInterface(binder);

        final SecurityException thrown =
                assertThrows(
                        SecurityException.class,
                        () -> broadcastSdkApi.registerBroadcastReceiver(INTENT_ACTIONS));

        assertThat(thrown)
                .hasMessageThat()
                .contains("SDK sandbox process not allowed to call registerReceiver");
    }

    /**
     * Tests that a SecurityException is not thrown when SDK sandbox process tries to register a
     * broadcast receiver. This behavior depends on the value of a {@link DeviceConfig} property.
     */
    @Test(expected = Test.None.class /* no exception expected */)
    public void testRegisterBroadcastReceiver_restrictionsNotApplied_preU() throws Exception {
        if (SdkLevel.isAtLeastU()) {
            return;
        }

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_SDK_SANDBOX,
                PROPERTY_ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS,
                "false",
                false);

        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        IBinder binder = sandboxedSdk.getInterface();
        IBroadcastSdkApi broadcastSdkApi = IBroadcastSdkApi.Stub.asInterface(binder);

        broadcastSdkApi.registerBroadcastReceiver(INTENT_ACTIONS);
    }

    /**
     * Tests that a SecurityException is thrown when SDK sandbox process tries to register a
     * broadcast receiver with no action mentioned in the {@link android.content.IntentFilter}
     * object.
     */
    @Test
    public void testRegisterBroadcastReceiver_intentFilterWithoutAction() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());

        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        IBinder binder = sandboxedSdk.getInterface();
        IBroadcastSdkApi broadcastSdkApi = IBroadcastSdkApi.Stub.asInterface(binder);

        final SecurityException thrown =
                assertThrows(
                        SecurityException.class,
                        () -> broadcastSdkApi.registerBroadcastReceiver(new ArrayList<>()));

        assertThat(thrown)
                .hasMessageThat()
                .contains(
                        "SDK sandbox not allowed to register receiver with the given IntentFilter");
    }

    /**
     * Tests that broadcast receiver is registered successfully from SDK sandbox process with no
     * action mentioned in the {@link android.content.IntentFilter} object.
     */
    @Test
    public void testRegisterBroadcastReceiver_intentFilterWithoutAction_preU() throws Exception {
        if (SdkLevel.isAtLeastU()) {
            return;
        }

        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        IBinder binder = sandboxedSdk.getInterface();
        IBroadcastSdkApi broadcastSdkApi = IBroadcastSdkApi.Stub.asInterface(binder);

        broadcastSdkApi.registerBroadcastReceiver(new ArrayList<>());
    }

    @Test
    public void testRegisterBroadcastReceiver_protectedBroadcast() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_ENFORCE_RESTRICTIONS, "true", false);

        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        IBinder binder = sandboxedSdk.getInterface();
        IBroadcastSdkApi broadcastSdkApi = IBroadcastSdkApi.Stub.asInterface(binder);

        broadcastSdkApi.registerBroadcastReceiver(
                new ArrayList<>(Arrays.asList(Intent.ACTION_SCREEN_OFF)));
    }
}
