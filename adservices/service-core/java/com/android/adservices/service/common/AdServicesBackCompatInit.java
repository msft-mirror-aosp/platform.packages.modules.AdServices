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

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.Nullable;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.LogUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Handles the Back Compat initialization for AdExtServices APK. */
public final class AdServicesBackCompatInit {

    private final Context mContext;
    @Nullable private final String mPackageName;

    @VisibleForTesting
    AdServicesBackCompatInit(Context context) {
        this.mContext = Objects.requireNonNull(context);
        this.mPackageName = context.getPackageName();
    }

    /** Gets an instance of {@link AdServicesBackCompatInit}. */
    public static AdServicesBackCompatInit getInstance() {
        return new AdServicesBackCompatInit(ApplicationContextSingleton.get());
    }

    /** Initialize back compat components in ExtServices package. */
    public void initializeComponents() {

        if (isAdServicesPackageName(mPackageName)) {
            // Running within the AdServices package, so don't do anything.
            LogUtil.d("Running within package %s, not changing component state", mPackageName);
            return;
        }

        // On T+ devices, always disable the AdExtServices activities and services.
        if (SdkLevel.isAtLeastT()) {
            // If this is not an S- device, disable the activities, services, unregister the
            // broadcast receivers, and unschedule any background jobs.
            unregisterPackageChangedBroadcastReceivers();
            updateAdExtServicesActivities(/* shouldEnable= */ false);
            updateAdExtServicesServices(/* shouldEnable= */ false);
            disableScheduledBackgroundJobs();
            return;
        }

        // If this is an S- device but the flags are disabled, do nothing.
        if (FlagsFactory.getFlags().getEnableBackCompat()
                && FlagsFactory.getFlags().getAdServicesEnabled()
                && !FlagsFactory.getFlags().getGlobalKillSwitch()) {

            registerPackagedChangedBroadcastReceivers();
            updateAdExtServicesActivities(/* shouldEnable= */ true);
            updateAdExtServicesServices(/* shouldEnable= */ true);
            return;
        }

        LogUtil.d("Exiting AdServicesBackCompatInit because flags are disabled");
    }

    /**
     * Cancels all scheduled jobs if running within the ExtServices APK. Needed because we could
     * have some persistent jobs that were scheduled on S before an OTA to T. This operation will be
     * bypassed if executed within the AdServices package.
     */
    @VisibleForTesting
    void disableScheduledBackgroundJobs() {
        try {
            JobScheduler scheduler = mContext.getSystemService(JobScheduler.class);
            if (scheduler == null) {
                LogUtil.e("Could not retrieve JobScheduler instance, so not cancelling jobs");
                return;
            }
            for (JobInfo jobInfo : scheduler.getAllPendingJobs()) {
                String jobClassName = jobInfo.getService().getClassName();
                // Cancel jobs from AdServices only
                if (jobClassName.startsWith(AdServicesCommon.ADSERVICES_CLASS_PATH_PREFIX)) {
                    int jobId = jobInfo.getId();
                    LogUtil.d("Deleting ext AdServices job %d %s", jobId, jobClassName);
                    scheduler.cancel(jobId);
                }
            }
            LogUtil.d("All AdServices scheduled jobs cancelled on package %s", mPackageName);
        } catch (Exception e) {
            LogUtil.e(e, "Error when cancelling scheduled jobs");
        }
    }

    /**
     * Registers a receiver for any broadcasts regarding changes to any packages for all users on
     * the device at boot up. After receiving the broadcast, send an explicit broadcast to the
     * AdServices module as that user. This operation will be bypassed if executed within the
     * AdServices package.
     */
    @VisibleForTesting
    @SuppressWarnings("NewApi")
    void registerPackagedChangedBroadcastReceivers() {
        boolean result = PackageChangedReceiver.enableReceiver(mContext, FlagsFactory.getFlags());
        LogUtil.d(
                "Package Change Receiver registration: Success=%s, Package=%s",
                result, mPackageName);
    }

    @VisibleForTesting
    @SuppressWarnings("NewApi")
    void unregisterPackageChangedBroadcastReceivers() {
        boolean result = PackageChangedReceiver.disableReceiver(mContext, FlagsFactory.getFlags());
        LogUtil.d(
                "Package Change Receiver unregistration: Success=%s, Package=%s",
                result, mPackageName);
    }

    /**
     * Activities for user consent and control are disabled by default. Only on S- devices, after
     * the flag is enabled, we enable the activities. This operation will be bypassed if executed
     * within the AdServices package.
     */
    @VisibleForTesting
    void updateAdExtServicesActivities(boolean shouldEnable) {
        try {
            updateComponents(PackageManagerCompatUtils.CONSENT_ACTIVITIES_CLASSES, shouldEnable);
            LogUtil.d("Updated state of AdExtServices activities: [enabled=%s]", shouldEnable);
        } catch (IllegalArgumentException e) {
            LogUtil.e("Error when updating activities: %s", e.getMessage());
        }
    }

    /**
     * Disables services that have intent filters specified in the AdExtServicesManifest to prevent
     * duplicates on T+ devices, Conversely, it enables these services on devices running versions S
     * and below. This operation will be bypassed if executed within the AdServices package.
     */
    @VisibleForTesting
    void updateAdExtServicesServices(boolean shouldEnable) {
        try {
            List<String> servicesToUpdate =
                    PackageManagerCompatUtils.SERVICE_CLASSES_AND_ENABLE_STATUS_ON_R_PAIRS.stream()
                            // If enabling, enable services that are only supported on current
                            // SDK version. If disabling, it's safe to disable all service
                            // components just in case they were enabled prior.
                            .filter(p -> !shouldEnable || p.second <= Build.VERSION.SDK_INT)
                            .map(p -> p.first)
                            .collect(Collectors.toList());
            updateComponents(servicesToUpdate, shouldEnable);
            LogUtil.d("Updated state of AdExtServices services: [enable=%s]", shouldEnable);
        } catch (IllegalArgumentException e) {
            LogUtil.e("Error when updating services: %s", e.getMessage());
        }
    }

    @VisibleForTesting
    void updateComponents(List<String> components, boolean shouldEnable) {
        Objects.requireNonNull(components);
        Objects.requireNonNull(mPackageName);

        PackageManager packageManager = mContext.getPackageManager();
        for (String component : components) {
            packageManager.setComponentEnabledSetting(
                    new ComponentName(mPackageName, component),
                    shouldEnable
                            ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }

    private boolean isAdServicesPackageName(String packageName) {
        return packageName == null
                || packageName.endsWith(AdServicesCommon.ADSERVICES_APK_PACKAGE_NAME_SUFFIX);
    }
}
