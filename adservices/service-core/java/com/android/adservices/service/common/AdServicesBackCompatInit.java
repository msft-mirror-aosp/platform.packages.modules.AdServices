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

    @VisibleForTesting
    AdServicesBackCompatInit(Context context) {
        this.mContext = Objects.requireNonNull(context);
    }

    /** Gets an instance of {@link AdServicesBackCompatInit}. */
    public static AdServicesBackCompatInit getInstance() {
        Context context = ApplicationContextSingleton.get();
        return new AdServicesBackCompatInit(context);
    }

    /** Initialize back compat components in ExtServices package. */
    public void initializeComponents() {

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
        if (!FlagsFactory.getFlags().getEnableBackCompat()
                || !FlagsFactory.getFlags().getAdServicesEnabled()
                || FlagsFactory.getFlags().getGlobalKillSwitch()) {
            LogUtil.d("Exiting AdServicesBackCompatInit because flags are disabled");
            return;
        }

        registerPackagedChangedBroadcastReceivers();
        updateAdExtServicesActivities(/* shouldEnable= */ true);
        updateAdExtServicesServices(/* shouldEnable= */ true);
    }

    /**
     * Cancels all scheduled jobs if running within the ExtServices APK. Needed because we could
     * have some persistent jobs that were scheduled on S before an OTA to T.
     */
    @VisibleForTesting
    void disableScheduledBackgroundJobs() {

        try {
            String packageName = mContext.getPackageName();
            if (packageName == null
                    || packageName.endsWith(AdServicesCommon.ADSERVICES_APK_PACKAGE_NAME_SUFFIX)) {
                // Running within the AdServices package, so don't do anything.
                LogUtil.e("Running within AdServices package, not changing scheduled job state");
                return;
            }

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
            LogUtil.d("All AdServices scheduled jobs cancelled on package %s", packageName);
        } catch (Exception e) {
            LogUtil.e(e, "Error when cancelling scheduled jobs");
        }
    }

    /**
     * Registers a receiver for any broadcasts regarding changes to any packages for all users on
     * the device at boot up. After receiving the broadcast, send an explicit broadcast to the
     * AdServices module as that user.
     */
    @VisibleForTesting
    @SuppressWarnings("NewApi")
    void registerPackagedChangedBroadcastReceivers() {
        String packageName = mContext.getPackageName();
        if (packageName == null
                || packageName.endsWith(AdServicesCommon.ADSERVICES_APK_PACKAGE_NAME_SUFFIX)) {
            LogUtil.e("Running within %s, do not enable package changed receiver", packageName);
            return;
        }
        PackageChangedReceiver.enableReceiver(mContext, FlagsFactory.getFlags());
        LogUtil.d("Package changed broadcast receivers registered from package %s", packageName);
    }

    @VisibleForTesting
    @SuppressWarnings("NewApi")
    void unregisterPackageChangedBroadcastReceivers() {
        String packageName = mContext.getPackageName();
        if (packageName == null
                || packageName.endsWith(AdServicesCommon.ADSERVICES_APK_PACKAGE_NAME_SUFFIX)) {
            LogUtil.e("Running within AdServices package, do not disable package changed receiver");
            return;
        }
        PackageChangedReceiver.disableReceiver(mContext, FlagsFactory.getFlags());
        LogUtil.d("Package change broadcast receivers unregistered from package %s", packageName);
    }

    /**
     * Activities for user consent and control are disabled by default. Only on S- devices, after
     * the flag is enabled, we enable the activities.
     */
    @VisibleForTesting
    void updateAdExtServicesActivities(boolean shouldEnable) {

        try {
            String packageName = mContext.getPackageName();
            updateComponents(
                    mContext,
                    PackageManagerCompatUtils.CONSENT_ACTIVITIES_CLASSES,
                    packageName,
                    shouldEnable);
            LogUtil.d("Updated state of AdExtServices activities: [enabled=%s]", shouldEnable);
        } catch (Exception e) {
            LogUtil.e("Error when updating activities: %s", e.getMessage());
        }
    }

    /**
     * Disables services with intent filters defined in AdExtServicesManifest to avoid dupes on T+
     * devices, or enables the same services on S to make sure they are re-enabled after OTA from R.
     */
    @VisibleForTesting
    void updateAdExtServicesServices(boolean shouldEnable) {

        try {
            String packageName = mContext.getPackageName();
            List<String> servicesToUpdate =
                    PackageManagerCompatUtils.SERVICE_CLASSES_AND_ENABLE_STATUS_ON_R_PAIRS.stream()
                            // If enabling, enable services that are only supported on current
                            // SDK version. If disabling, it's safe to disable all service
                            // components just in case they were enabled prior.
                            .filter(p -> !shouldEnable || p.second <= Build.VERSION.SDK_INT)
                            .map(p -> p.first)
                            .collect(Collectors.toList());
            updateComponents(mContext, servicesToUpdate, packageName, shouldEnable);
            LogUtil.d("Updated state of AdExtServices services: [enable=%s]", shouldEnable);
        } catch (Exception e) {
            LogUtil.e("Error when updating services: %s", e.getMessage());
        }
    }

    @VisibleForTesting
    static void updateComponents(
            Context context,
            List<String> components,
            String adServicesPackageName,
            boolean shouldEnable) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(components);
        Objects.requireNonNull(adServicesPackageName);
        if (adServicesPackageName.endsWith(AdServicesCommon.ADSERVICES_APK_PACKAGE_NAME_SUFFIX)) {
            throw new IllegalStateException(
                    "Components for package with AdServices APK package suffix should not be "
                            + "updated!");
        }

        PackageManager packageManager = context.getPackageManager();
        for (String component : components) {
            packageManager.setComponentEnabledSetting(
                    new ComponentName(adServicesPackageName, component),
                    shouldEnable
                            ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }
}
