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

package com.android.adservices.service.common.compat;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_NAME_NOT_FOUND_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.LogUtil;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Objects;

/** Utility class for compatibility of PackageManager APIs with Android S and earlier. */
public final class PackageManagerCompatUtils {

    private PackageManagerCompatUtils() {
        // Prevent instantiation
    }

    // This list is the same as the list declared in the AdExtServicesManifest, where the
    // activities need need to be enabled/disabled based on flag settings and SDK version.
    // LINT.IfChange(activities_and_services)
    public static final ImmutableList<String> CONSENT_ACTIVITIES_CLASSES =
            ImmutableList.of(
                    "com.android.adservices.ui.settings.activities."
                            + "AdServicesSettingsMainActivity",
                    "com.android.adservices.ui.settings.activities.TopicsActivity",
                    "com.android.adservices.ui.settings.activities.BlockedTopicsActivity",
                    "com.android.adservices.ui.settings.activities.AppsActivity",
                    "com.android.adservices.ui.settings.activities.BlockedAppsActivity",
                    "com.android.adservices.ui.settings.activities.MeasurementActivity",
                    "com.android.adservices.ui.notifications.ConsentNotificationActivity");

    // This list is the same as the list declared in the AdExtServicesManifest, where the
    // services with intent filters need to be enabled/disabled based on flag settings and SDK
    // version.
    public static final ImmutableList<String> SERVICE_CLASSES =
            ImmutableList.of(
                    "com.android.adservices.adid.AdIdService",
                    "com.android.adservices.measurement.MeasurementService",
                    "com.android.adservices.common.AdServicesCommonService",
                    "com.android.adservices.adselection.AdSelectionService",
                    "com.android.adservices.customaudience.CustomAudienceService",
                    "android.adservices.signals.ProtectedSignalsService",
                    "com.android.adservices.topics.TopicsService",
                    "com.android.adservices.appsetid.AppSetIdService");

    // LINT.ThenChange()

    /**
     * Invokes the appropriate overload of {@code getInstalledPackages} on {@link PackageManager}
     * depending on the SDK version.
     *
     * <p>{@code PackageInfoFlags.of()} actually takes a {@code long} as input whereas the earlier
     * overload takes an {@code int}. For backward-compatibility, we're limited to the {@code int}
     * range, so using {@code int} as a parameter to this method.
     *
     * @param packageManager the package manager instance to query
     * @param flags the flags to be used for querying package manager
     * @return the list of installed packages returned from the query to {@link PackageManager}
     */
    @NonNull
    public static List<PackageInfo> getInstalledPackages(
            @NonNull PackageManager packageManager, int flags) {
        Objects.requireNonNull(packageManager);
        return SdkLevel.isAtLeastT()
                ? packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags))
                : packageManager.getInstalledPackages(flags);
    }

    /**
     * Invokes the appropriate overload of {@code getInstalledApplications} on {@link
     * PackageManager} depending on the SDK version.
     *
     * <p>{@code ApplicationInfoFlags.of()} actually takes a {@code long} as input whereas the
     * earlier overload takes an {@code int}. For backward-compatibility, we're limited to the
     * {@code int} range, so using {@code int} as a parameter to this method.
     *
     * @param packageManager the package manager instance to query
     * @param flags the flags to be used for querying package manager
     * @return the list of installed applications returned from the query to {@link PackageManager}
     */
    @NonNull
    public static List<ApplicationInfo> getInstalledApplications(
            @NonNull PackageManager packageManager, int flags) {
        Objects.requireNonNull(packageManager);
        return SdkLevel.isAtLeastT()
                ? packageManager.getInstalledApplications(
                        PackageManager.ApplicationInfoFlags.of(flags))
                : packageManager.getInstalledApplications(flags);
    }

    /**
     * Invokes the appropriate overload of {@code getApplicationInfo} on {@link PackageManager}
     * depending on the SDK version.
     *
     * <p>{@code ApplicationInfoFlags.of()} actually takes a {@code long} as input whereas the
     * earlier overload takes an {@code int}. For backward-compatibility, we're limited to the
     * {@code int} range, so using {@code int} as a parameter to this method.
     *
     * @param packageManager the package manager instance to query
     * @param flags the flags to be used for querying package manager
     * @param packageName the name of the package for which the ApplicationInfo should be retrieved
     * @return the application info returned from the query to {@link PackageManager}
     */
    @NonNull
    public static ApplicationInfo getApplicationInfo(
            @NonNull PackageManager packageManager, @NonNull String packageName, int flags)
            throws PackageManager.NameNotFoundException {
        Objects.requireNonNull(packageManager);
        Objects.requireNonNull(packageName);
        return SdkLevel.isAtLeastT()
                ? packageManager.getApplicationInfo(
                        packageName, PackageManager.ApplicationInfoFlags.of(flags))
                : packageManager.getApplicationInfo(packageName, flags);
    }

    /**
     * Invokes the appropriate overload of {@code getPackageUid} on {@link PackageManager} depending
     * on the SDK version.
     *
     * <p>{@code PackageInfoFlags.of()} actually takes a {@code long} as input whereas the earlier
     * overload takes an {@code int}. For backward-compatibility, we're limited to the {@code int}
     * range, so using {@code int} as a parameter to this method.
     *
     * @param packageManager the packageManager instance to query
     * @param packageName the name of the package for which the uid needs to be returned
     * @param flags the flags to be used for querying the packageManager
     * @return the uid of the package with the specified name
     * @throws PackageManager.NameNotFoundException if the package was not found
     */
    public static int getPackageUid(
            @NonNull PackageManager packageManager, @NonNull String packageName, int flags)
            throws PackageManager.NameNotFoundException {
        Objects.requireNonNull(packageManager);
        Objects.requireNonNull(packageName);
        return SdkLevel.isAtLeastT()
                ? packageManager.getPackageUid(
                        packageName, PackageManager.PackageInfoFlags.of(flags))
                : packageManager.getPackageUid(packageName, flags);
    }

    /**
     * Check whether the activities for user consent and control are enabled
     *
     * @param context the context
     * @return true if AdServices activities are enabled, otherwise false
     */
    // TODO(b/311183933): Remove passed in Context from static method.
    @SuppressWarnings("AvoidStaticContext")
    public static boolean isAdServicesActivityEnabled(@NonNull Context context) {
        Objects.requireNonNull(context);
        String packageName = context.getPackageName();
        if (packageName == null) {
            return false;
        }

        // Activities are enabled by default in AdServices package
        if (packageName.endsWith(AdServicesCommon.ADSERVICES_APK_PACKAGE_NAME_SUFFIX)) {
            return true;
        }
        PackageManager packageManager = context.getPackageManager();
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
            for (String activity : CONSENT_ACTIVITIES_CLASSES) {
                int componentEnabledState =
                        packageManager.getComponentEnabledSetting(
                                new ComponentName(packageInfo.packageName, activity));
                // Activities are disabled by default in ExtServices package
                if (componentEnabledState != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    return false;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.e("Error when checking if activities are enabled: " + e.getMessage());
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_NAME_NOT_FOUND_EXCEPTION,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
            return false;
        }
        return true;
    }
}
