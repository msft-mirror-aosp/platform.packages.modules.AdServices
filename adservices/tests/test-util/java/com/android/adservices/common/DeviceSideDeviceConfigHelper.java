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
package com.android.adservices.common;

import static com.android.compatibility.common.util.ShellIdentityUtils.invokeStaticMethodWithShellPermissions;

import android.provider.DeviceConfig;

import com.android.adservices.common.DeviceConfigHelper.SyncDisabledModeForTest;
import com.android.compatibility.common.util.ShellUtils;
import com.android.modules.utils.build.SdkLevel;

import java.util.Objects;
import java.util.concurrent.Callable;

/** Device-side implementation of {@link DeviceConfigHelper.Interface}. */
final class DeviceSideDeviceConfigHelper implements DeviceConfigHelper.Interface {

    private static final Logger sLogger =
            new Logger(AndroidLogger.getInstance(), DeviceSideDeviceConfigHelper.class);

    private final String mNamespace;
    // TODO(b/294423183): remove once legacy usage is gone
    private final boolean mUsedByLegacyHelper;

    DeviceSideDeviceConfigHelper(String namespace, boolean usedByLegacyHelper) {
        mNamespace = Objects.requireNonNull(namespace);
        mUsedByLegacyHelper = usedByLegacyHelper;
    }

    @Override
    public void setSyncDisabledModeForTest(SyncDisabledModeForTest mode) {
        String value = mode.name().toLowerCase();
        sLogger.v("setSyncDisabledModeForTest(%s)", value);
        ShellUtils.runShellCommand("device_config set_sync_disabled_for_test %s", value);
    }

    @Override
    public String get(String name, String defaultValue) {
        return call(() -> DeviceConfig.getString(mNamespace, name, /* defaultValue= */ null));
    }

    @Override
    public void set(String name, String value) {
        sLogger.v("set(%s=%s)", name, value);
        call(() -> DeviceConfig.setProperty(mNamespace, name, value, /* makeDefault= */ false));
    }

    @Override
    public void delete(String name) {
        sLogger.v("delete(%s)", name);

        if (SdkLevel.isAtLeastT()) {
            call(() -> DeviceConfig.deleteProperty(mNamespace, name));
            return;
        }
        ShellUtils.runShellCommand("device_config delete %s %s", mNamespace, name);
    }

    @Override
    public String dump() {
        return ShellUtils.runShellCommand("device_config list %s", mNamespace).trim();
    }

    @Override
    public String toString() {
        return DeviceSideDeviceConfigHelper.class.getSimpleName();
    }

    // TODO(b/294423183): remove (and change calls above to callWithDeviceConfigPermissions()) once
    // legacy usage is gone
    private <T> T call(Callable<T> c) {
        T result = callWithDeviceConfigPermissions(c);
        if (mUsedByLegacyHelper) {
            String permission = android.Manifest.permission.WRITE_DEVICE_CONFIG;
            sLogger.d("re-adopting Shell permission %s for legacy purposes", permission);
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(permission);
        }
        return result;
    }

    static <T> T callWithDeviceConfigPermissions(Callable<T> c) {
        return invokeStaticMethodWithShellPermissions(
                () -> {
                    try {
                        return c.call();
                    } catch (Exception e) {
                        throw new RuntimeException(
                                "Failed to call something with Shell permissions: " + e);
                    }
                });
    }
}
