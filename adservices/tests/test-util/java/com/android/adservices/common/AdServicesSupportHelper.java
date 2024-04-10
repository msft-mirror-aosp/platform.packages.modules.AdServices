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

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.testing.AndroidLogger;
import com.android.adservices.shared.testing.Nullable;
import com.android.compatibility.common.util.PackageUtil;
import com.android.compatibility.common.util.PropertyUtil;

// TODO(b/297248322): move this class to sideless as the logic is duplicated on hostside
/** Helper to check if AdServices is supported / enabled in a device. */
public final class AdServicesSupportHelper extends AbstractDeviceSupportHelper {

    private static final String PLAY_STORE_USER_CERT =
            "F0:FD:6C:5B:41:0F:25:CB:25:C3:B5:33:46:C8:97:2F:"
                    + "AE:30:F8:EE:74:11:DF:91:04:80:AD:6B:2D:60:DB:83";

    /** The minimum screen width for a 7-inch large screen device. */
    public static final int SEVEN_INCH_TABLET_MINIMUM_SCREEN_WIDTH_DP = 600;

    private static final AdServicesSupportHelper sInstance = new AdServicesSupportHelper();

    private final Context mContext;

    public static AdServicesSupportHelper getInstance() {
        return sInstance;
    }

    /** Gets the value of AdServices global kill switch. */
    public static boolean getGlobalKillSwitch() throws Exception {
        Flags flags = FlagsFactory.getFlags();
        boolean value = invokeStaticMethodWithShellPermissions(() -> flags.getGlobalKillSwitch());
        sInstance.mLog.v("getGlobalKillSwitch(): %b", value);
        return value;
    }

    @Override
    protected boolean hasPackageManagerFeature(String feature) {
        return mContext.getPackageManager().hasSystemFeature(feature);
    }

    @Override
    protected boolean hasGmsCore() {
        return PackageUtil.exists(GMS_CORE_PACKAGE);
    }

    @Override
    protected boolean hasPlayStore() {
        return (PropertyUtil.isUserBuild())
                ? PackageUtil.exists(PLAY_STORE_PACKAGE, PLAY_STORE_USER_CERT)
                : PackageUtil.exists(PLAY_STORE_PACKAGE);
    }

    @Override
    protected boolean isLowRamDeviceByDefault() {
        return mContext.getSystemService(ActivityManager.class).isLowRamDevice();
    }

    @Override
    protected boolean isLargeScreenDeviceByDefault() {
        return isLargeScreen(mContext.getResources());
    }

    @Override
    protected boolean isDebuggable() {
        return AdservicesTestHelper.isDebuggable();
    }

    // TODO(b/308009734): Add tests for getAdServicesPackageName().
    @Override
    @Nullable
    public String getAdServicesPackageName() {
        return AdservicesTestHelper.getAdServicesPackageName(mContext);
    }

    private AdServicesSupportHelper() {
        super(AndroidLogger.getInstance(), DeviceSideSystemPropertiesHelper.getInstance());

        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private boolean isLargeScreen(Resources resources) {
        if (resources == null) {
            return false;
        }
        // Consider it to be large screen if it's either a) xlarge, or b) sw600dp.
        boolean isXlarge =
                (resources.getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK)
                        > Configuration.SCREENLAYOUT_SIZE_LARGE;
        boolean isSevenInchLargeScreen =
                (resources.getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK)
                                <= Configuration.SCREENLAYOUT_SIZE_LARGE
                        && resources.getConfiguration().smallestScreenWidthDp
                                >= SEVEN_INCH_TABLET_MINIMUM_SCREEN_WIDTH_DP;
        return isXlarge || isSevenInchLargeScreen;
    }
}
