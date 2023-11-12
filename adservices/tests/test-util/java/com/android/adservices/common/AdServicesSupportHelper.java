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

import android.adservices.adid.AdId;
import android.adservices.adid.AdIdCompatibleManager;
import android.app.ActivityManager;
import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.service.PhFlags;

import com.google.common.util.concurrent.MoreExecutors;

import java.util.Objects;

// TODO(b/297248322): move this class to sideless as the logic is duplicated on hostside

/** Helper to check if AdServices is supported / enabled in a device. */
public final class AdServicesSupportHelper extends AbstractDeviceSupportHelper {

    private static final AdServicesSupportHelper sInstance = new AdServicesSupportHelper();
    public static final int AD_ID_FETCH_TIMEOUT_MS = 5000;

    private final Context mContext;

    public static AdServicesSupportHelper getInstance() {
        return sInstance;
    }

    /** Gets the value of AdServices global kill switch. */
    public static boolean getGlobalKillSwitch() throws Exception {
        PhFlags flags = PhFlags.getInstance();
        boolean value = invokeStaticMethodWithShellPermissions(() -> flags.getGlobalKillSwitch());
        sInstance.mLog.v("getGlobalKillSwitch(): %b", value);
        return value;
    }

    @Override
    protected boolean isAdIdAvailable() {
        AdIdCompatibleManager adIdCompatibleManager;
        AdServicesOutcomeReceiverForTests<AdId> callback =
                new AdServicesOutcomeReceiverForTests<>();
        try {
            adIdCompatibleManager = new AdIdCompatibleManager(mContext);
            adIdCompatibleManager.getAdId(MoreExecutors.directExecutor(), callback);
        } catch (IllegalStateException e) {
            mLog.e(e, "isAdIdAvailable(): IllegalStateException detected in AdId manager.");
            return false;
        }

        boolean isAdIdAvailable;
        try {
            AdId result = callback.assertSuccess();
            isAdIdAvailable =
                    !Objects.isNull(result)
                            && !result.isLimitAdTrackingEnabled()
                            && !result.getAdId().equals(AdId.ZERO_OUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            mLog.e(e, "isAdIdAvailable(): failed to get AdId due to InterruptedException.");
            isAdIdAvailable = false;
        }

        mLog.v("isAdIdAvailable(): %b", isAdIdAvailable);
        return isAdIdAvailable;
    }

    @Override
    protected boolean hasPackageManagerFeature(String feature) {
        return mContext.getPackageManager().hasSystemFeature(feature);
    }

    @Override
    protected boolean isLowRamDeviceByDefault() {
        return mContext.getSystemService(ActivityManager.class).isLowRamDevice();
    }

    @Override
    protected boolean isDebuggable() {
        return AdservicesTestHelper.isDebuggable();
    }

    private AdServicesSupportHelper() {
        super(AndroidLogger.getInstance(), DeviceSideSystemPropertiesHelper.getInstance());

        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }
}
