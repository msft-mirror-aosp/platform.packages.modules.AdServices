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


import android.os.SystemProperties;

import com.android.adservices.shared.testing.AndroidLogger;
import com.android.adservices.shared.testing.Nullable;
import com.android.adservices.shared.testing.SystemPropertiesHelper;
import com.android.compatibility.common.util.ShellUtils;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

/** Device-side implementation of {@link SystemPropertiesHelper.Interface}. */
final class DeviceSideSystemPropertiesHelper extends SystemPropertiesHelper.Interface {

    private static final DeviceSideSystemPropertiesHelper sInstance =
            new DeviceSideSystemPropertiesHelper();

    public static DeviceSideSystemPropertiesHelper getInstance() {
        return sInstance;
    }

    private DeviceSideSystemPropertiesHelper() {
        super(AndroidLogger.getInstance());
    }

    @Override
    public String get(String name) {
        return SystemProperties.get(name);
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
