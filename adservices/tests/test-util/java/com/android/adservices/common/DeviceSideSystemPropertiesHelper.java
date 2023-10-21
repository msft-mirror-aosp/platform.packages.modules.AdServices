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

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import android.os.SystemProperties;
import android.text.TextUtils;

import com.android.compatibility.common.util.ShellUtils;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

/** Device-side implementation of {@link SystemPropertiesHelper.Interface}. */
final class DeviceSideSystemPropertiesHelper extends SystemPropertiesHelper.Interface {

    private static final Logger sLogger =
            new Logger(AndroidLogger.getInstance(), DeviceSideSystemPropertiesHelper.class);

    private static final DeviceSideSystemPropertiesHelper sInstance =
            new DeviceSideSystemPropertiesHelper();

    public static DeviceSideSystemPropertiesHelper getInstance() {
        return sInstance;
    }

    private DeviceSideSystemPropertiesHelper() {}

    @Override
    public String get(String name) {
        return SystemProperties.get(name);
    }

    @Override
    public void set(String name, String value) {
        sLogger.v("set(%s, %s)", name, value);

        if (!TextUtils.isEmpty(value)) {
            runShellCommand("setprop %s %s", name, value);
            return;
        }
        // TODO(b/293132368): UIAutomation doesn't support passing a "" or '' - it will quote
        // them, which would cause the property value to be "" or '', not the empty String.
        // Another approach would be calling SystemProperties.set(), but that method is hidden
        // (b/294414609)
        sLogger.w(
                "NOT resetting property %s to empty String as it's not supported by"
                        + " runShellCommand(), but setting it as null",
                name);
        runShellCommand("setprop %s null", name);
    }

    @Override
    @FormatMethod
    protected String runShellCommand(@FormatString String cmdFmt, @Nullable Object... cmdArgs) {
        return ShellUtils.runShellCommand(cmdFmt, cmdArgs);
    }

    @Override
    public String toString() {
        return DeviceSideSystemPropertiesHelper.class.getSimpleName();
    }
}
