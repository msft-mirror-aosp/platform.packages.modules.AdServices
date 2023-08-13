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
import android.util.ArrayMap;
import android.util.Log;

import com.android.compatibility.common.util.ShellUtils;
import com.android.modules.utils.build.SdkLevel;

import java.util.concurrent.Callable;

// TODO(b/294423183): add unit tests
// TODO(b/294423183): use an existing class like DeviceConfigStateManager or DeviceConfigStateHelper
/**
 * Helper class to set {@link android.provider.DeviceConfig} flags and properly reset then to their
 * original values.
 *
 * <p><b>Note: </b>this class is not thread safe.
 */
public final class DeviceConfigHelper {

    private static final String TAG = DeviceConfigHelper.class.getSimpleName();

    private final String mNamespace;
    private final ArrayMap<String, String> mFlagsToBeReset = new ArrayMap<>();

    public DeviceConfigHelper(String namespace) {
        Log.v(TAG, "Constructor for " + namespace);
        mNamespace = namespace;
    }

    public void set(String name, String value) {
        savePreviousValue(name);
        setOnly(name, value);
    }

    public void setWithSeparator(String name, String value, String separator) {
        String oldValue = savePreviousValue(name);
        String newValue = oldValue == null ? value : oldValue + separator + value;
        setOnly(name, newValue);
    }

    public void reset() {
        int size = mFlagsToBeReset.size();
        if (size == 0) {
            Log.d(TAG, "reset(): not needed");
            return;
        }
        Log.v(TAG, "reset(): restoring " + size + " flags");
        try {
            for (int i = 0; i < size; i++) {
                String name = mFlagsToBeReset.keyAt(i);
                String value = mFlagsToBeReset.valueAt(i);
                if (value == null) {
                    delete(name);
                } else {
                    setOnly(name, value);
                }
            }
        } finally {
            mFlagsToBeReset.clear();
        }
    }

    public void setSyncDisabledMode(SyncDisabledMode mode) {
        String value = mode.name().toLowerCase();
        Log.v(TAG, "setSyncDisabledMode(" + value + ")");
        ShellUtils.runShellCommand("device_config set_sync_disabled_for_test %s", value);
    }

    // TODO(b/294423183): temporarily exposed as it's used by legacy helper methods on
    // AdServicesFlagsSetterRule
    String get(String name) {
        return callWithDeviceConfigPermissions(
                () -> DeviceConfig.getString(mNamespace, name, /* defaultValue= */ null));
    }

    private String savePreviousValue(String name) {
        String oldValue = get(name);
        if (mFlagsToBeReset.containsKey(name)) {
            Log.v(
                    TAG,
                    "Value of "
                            + name
                            + "("
                            + mFlagsToBeReset.get(name)
                            + ") already saved for reset()");
            return oldValue;
        }
        Log.v(TAG, "Saving " + name + "=" + oldValue + " for reset");
        mFlagsToBeReset.put(name, oldValue);
        return oldValue;
    }

    private void setOnly(String name, String value) {
        Log.v(TAG, "set(" + name + ", " + value + ")");

        callWithDeviceConfigPermissions(
                () -> DeviceConfig.setProperty(mNamespace, name, value, /* makeDefault= */ false));
    }

    private void delete(String name) {
        Log.v(TAG, "delete(" + name + ")");

        if (SdkLevel.isAtLeastT()) {
            callWithDeviceConfigPermissions(() -> DeviceConfig.deleteProperty(mNamespace, name));
        } else {
            ShellUtils.runShellCommand("device_config delete %s %s", mNamespace, name);
        }
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

    enum SyncDisabledMode {
        NONE,
        PERSISTENT,
        UNTIL_REBOOT
    }
}
