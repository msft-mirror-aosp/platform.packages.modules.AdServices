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

package android.app.sdksandbox.testutils;

import android.content.Context;

import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.PackageUtil;
import com.android.compatibility.common.util.PropertyUtil;

/** Utility class to control which devices SDK sandbox tests run on. */
public class DeviceSupportUtils {

    private static final String GMS_CORE_PACKAGE = "com.google.android.gms";
    private static final String PLAY_STORE_PACKAGE = "com.android.vending";
    private static final String PLAY_STORE_USER_CERT =
            "F0:FD:6C:5B:41:0F:25:CB:25:C3:B5:33:46:C8:97:2F:"
                    + "AE:30:F8:EE:74:11:DF:91:04:80:AD:6B:2D:60:DB:83";

    public static boolean isSdkSandboxSupported(Context context) {
        return !FeatureUtil.isWatch()
                && !FeatureUtil.isTV()
                && !FeatureUtil.isAutomotive()
                && !isGoDevice();
    }

    // Taken from vendor/xts/common/device-side/util/src/com/android/xts/common/util/GmsUtil.java
    private static boolean isGoDevice() {
        return FeatureUtil.isLowRam()
                && hasGmsCore()
                && hasPlayStore()
                && !FeatureUtil.isWatch()
                && !FeatureUtil.isTV()
                && !FeatureUtil.isAutomotive()
                && !FeatureUtil.isVrHeadset()
                && !FeatureUtil.isArc();
    }

    /** Returns whether GMS Core is installed for this build */
    private static boolean hasGmsCore() {
        return PackageUtil.exists(GMS_CORE_PACKAGE);
    }

    private static boolean hasPlayStore() {
        return (PropertyUtil.isUserBuild())
                ? PackageUtil.exists(PLAY_STORE_PACKAGE, PLAY_STORE_USER_CERT)
                : PackageUtil.exists(PLAY_STORE_PACKAGE);
    }
}
