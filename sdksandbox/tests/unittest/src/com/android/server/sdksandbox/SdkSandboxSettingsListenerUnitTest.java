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

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.Context;
import android.provider.DeviceConfig;
import android.util.ArrayMap;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.sdksandbox.proto.Services;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Map;

public class SdkSandboxSettingsListenerUnitTest {
    private static final String PROPERTY_DISABLE_SANDBOX = "disable_sdk_sandbox";
    private static final String PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS =
            "apply_sdk_sandbox_next_restrictions";
    private static final String PROPERTY_SERVICES_ALLOWLIST =
            "services_allowlist_per_targetSdkVersion";
    private SdkSandboxSettingsListener mSdkSandboxSettingsListener;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.READ_DEVICE_CONFIG,
                        Manifest.permission.WRITE_DEVICE_CONFIG);

        mSdkSandboxSettingsListener =
                new SdkSandboxSettingsListener(
                        context, Mockito.mock(SdkSandboxManagerService.class));
    }

    @After
    public void tearDown() {
        if (mSdkSandboxSettingsListener != null) {
            mSdkSandboxSettingsListener.unregisterPropertiesListener();
        }
    }

    @Test
    public void testSdkSandboxSettings_killSwitch() {
        assertThat(mSdkSandboxSettingsListener.isKillSwitchEnabled()).isFalse();
        setDeviceConfigProperty(PROPERTY_DISABLE_SANDBOX, "true");
        assertThat(mSdkSandboxSettingsListener.isKillSwitchEnabled()).isTrue();
        setDeviceConfigProperty(PROPERTY_DISABLE_SANDBOX, "false");
        assertThat(mSdkSandboxSettingsListener.isKillSwitchEnabled()).isFalse();
    }

    @Test
    public void testOtherPropertyChangeDoesNotAffectKillSwitch() {
        assertThat(mSdkSandboxSettingsListener.isKillSwitchEnabled()).isFalse();
        setDeviceConfigProperty("other_property", "true");
        assertThat(mSdkSandboxSettingsListener.isKillSwitchEnabled()).isFalse();
    }

    @Test
    public void testSdkSandboxSettings_applySdkSandboxRestrictionsNext() {
        assertThat(mSdkSandboxSettingsListener.applySdkSandboxRestrictionsNext()).isFalse();
        setDeviceConfigProperty(PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS, "true");
        assertThat(mSdkSandboxSettingsListener.applySdkSandboxRestrictionsNext()).isTrue();
        setDeviceConfigProperty(PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS, "false");
        assertThat(mSdkSandboxSettingsListener.applySdkSandboxRestrictionsNext()).isFalse();
    }

    @Test
    public void testServiceAllowlist_DeviceConfigNotAvailable() {
        setDeviceConfigProperty(PROPERTY_SERVICES_ALLOWLIST, null);

        assertThat(
                        mSdkSandboxSettingsListener.getServiceAllowlistForTargetSdkVersion(
                                /*targetSdkVersion=*/ 34))
                .isNull();
    }

    @Test
    public void testServiceAllowlist_DeviceConfigAllowlistApplied() {
        /*
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

        setDeviceConfigProperty(PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);

        Services.AllowedServices allowedServices =
                mSdkSandboxSettingsListener.getServiceAllowlistForTargetSdkVersion(
                        /*targetSdkVersion=*/ 33);
        assertThat(allowedServices).isNotNull();

        verifyAllowlistEntryContents(
                allowedServices.getAllowedServices(0),
                /*action=*/ "android.test.33",
                /*packageName=*/ "packageName.test.33",
                /*componentClassName=*/ "className.test.33");

        allowedServices =
                mSdkSandboxSettingsListener.getServiceAllowlistForTargetSdkVersion(
                        /*targetSdkVersion=*/ 34);
        assertThat(allowedServices).isNotNull();

        verifyAllowlistEntryContents(
                allowedServices.getAllowedServices(0),
                /*action=*/ "android.test.34",
                /*packageName=*/ "packageName.test.34",
                /*componentClassName=*/ "className.test.34");
    }

    private void verifyAllowlistEntryContents(
            Services.AllowedService allowedService,
            String action,
            String packageName,
            String componentClassName) {
        assertThat(allowedService.getAction()).isEqualTo(action);
        assertThat(allowedService.getPackageName()).isEqualTo(packageName);
        assertThat(allowedService.getComponentClassName()).isEqualTo(componentClassName);
    }

    private void setDeviceConfigProperty(String property, String value) {
        // Explicitly calling the onPropertiesChanged method to avoid race conditions
        if (value == null) {
            // Map.of() does not handle null, so we need to use an ArrayMap to delete a property
            ArrayMap<String, String> properties = new ArrayMap<>();
            properties.put(property, null);
            mSdkSandboxSettingsListener.onPropertiesChanged(
                    new DeviceConfig.Properties(DeviceConfig.NAMESPACE_ADSERVICES, properties));
        } else {
            mSdkSandboxSettingsListener.onPropertiesChanged(
                    new DeviceConfig.Properties(
                            DeviceConfig.NAMESPACE_ADSERVICES, Map.of(property, value)));
        }
    }
}