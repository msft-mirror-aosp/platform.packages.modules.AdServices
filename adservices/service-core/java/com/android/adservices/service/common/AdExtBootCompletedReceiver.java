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

package com.android.adservices.service.common;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.internal.annotations.VisibleForTesting;

/** Handles the BootCompleted initialization for AdExtServices APK on S-. */
// TODO(b/269798827): Enable for R.
// TODO(b/274675141): add e2e test for boot complete receiver
@RequiresApi(Build.VERSION_CODES.S)
public class AdExtBootCompletedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO(b/269798827): Enable for R.
        // On T+ devices, always disable the AdExtServices activities.
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.S
                && Build.VERSION.SDK_INT != Build.VERSION_CODES.S_V2) {
            // If this is not an S- device, disable the service activities and do not register the
            // broadcast receivers.
            updateAdExtServicesActivities(context, /* enable= */ false);
            return;
        }
        // If this is an S- device but the flags are disabled, do nothing.
        if (!FlagsFactory.getFlags().getEnableBackCompat()
                || !FlagsFactory.getFlags().getAdServicesEnabled()
                || FlagsFactory.getFlags().getGlobalKillSwitch()) {
            return;
        }

        registerPackagedChangedBroadcastReceivers(context);
        updateAdExtServicesActivities(context, /* enable= */ true);
    }

    /**
     * Registers a receiver for any broadcasts regarding changes to any packages for all users on
     * the device at boot up. After receiving the broadcast, send an explicit broadcast to the
     * AdServices module as that user.
     */
    @VisibleForTesting
    void registerPackagedChangedBroadcastReceivers(Context context) {
        PackageChangedReceiver.enableReceiver(context, FlagsFactory.getFlags());
        LogUtil.d("Package changed broadcast receivers registered.");
    }

    /**
     * Activities for user consent and control are disabled by default. Only on S- devices, after
     * the flag is enabled, we enable the activities.
     */
    @VisibleForTesting
    void updateAdExtServicesActivities(Context context, boolean enable) {
        PackageManager packageManager = context.getPackageManager();
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            PackageManagerCompatUtils.updateAdExtServicesActivities(
                    context, packageInfo.packageName, enable);
            LogUtil.d("Updated state of AdExtServices activities: [enabled=" + enable + "]");
        } catch (Exception e) {
            LogUtil.e("Error when enabling activities: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
