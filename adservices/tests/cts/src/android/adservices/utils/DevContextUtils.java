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

package android.adservices.utils;

import android.content.Context;
import android.os.Process;
import android.util.Log;

import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.shared.testing.SupportedByConditionRule;

/** Class to manage all utilities required for DevContext in CTS */
public class DevContextUtils {

    private static final boolean DEVELOPER_MODE_FEATURE_ENABLED = false;

    /** Method to create DevOptions Enabled rule.. */
    public static SupportedByConditionRule createDevOptionsAvailableRule(
            Context context, String logTag) {
        return new SupportedByConditionRule(
                "Developer Options are not enabled or the calling app is not debuggable",
                () -> isDevOptionsEnabled(context, logTag));
    }

    /** Method to check if Dev Options are enabled based on DevContext. */
    public static boolean isDevOptionsEnabled(Context context, String logTag) {
        DevContextFilter devContextFilter =
                DevContextFilter.create(context, DEVELOPER_MODE_FEATURE_ENABLED);
        DevContext mDevContext = devContextFilter.createDevContext(Process.myUid());
        boolean isDebuggable =
                devContextFilter.isDebuggable(mDevContext.getCallingAppPackageName());
        boolean isDeveloperMode = devContextFilter.isDeviceDevOptionsEnabledOrDebuggable();
        Log.d(
                logTag,
                String.format("Debuggable: %b\n", isDebuggable)
                        + String.format("Developer options on: %b", isDeveloperMode));
        return mDevContext.getDeviceDevOptionsEnabled();
    }
}
