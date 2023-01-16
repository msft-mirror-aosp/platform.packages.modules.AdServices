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

package com.android.adservices.service.stats;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__ACTION_UNSPECIFIED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__CONFIRMATION_PAGE_DISPLAYED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__FLEDGE_OPT_IN_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__FLEDGE_OPT_OUT_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_NOTIFICATION_CONFIRMATION_PAGE_DISPLAYED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_NOTIFICATION_DISABLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_NOTIFICATION_LANDING_PAGE_DISPLAYED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_NOTIFICATION_REQUESTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_DISPLAYED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__MANAGE_MEASUREMENT_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__MEASUREMENT_OPT_IN_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__MEASUREMENT_OPT_OUT_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__NOTIFICATION_DISABLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__NOTIFICATION_OPT_IN_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__NOTIFICATION_OPT_OUT_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_IN_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_OUT_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__PRIVACY_SANDBOX_SETTINGS_PAGE_DISPLAYED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__REQUESTED_NOTIFICATION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__RESET_MEASUREMENT_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__TOPICS_OPT_IN_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__TOPICS_OPT_OUT_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW;

import android.content.Context;

import androidx.annotation.NonNull;

import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.DeviceRegionProvider;

/** Logger for UiStats. */
public class UiStatsLogger {

    private static AdServicesLoggerImpl sLogger = AdServicesLoggerImpl.getInstance();

    // rename the variables that have too many characters.
    private static int sGaUxNotificationLandingPageDisplayed =
            AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_NOTIFICATION_LANDING_PAGE_DISPLAYED;

    private static int sGaUxNotificationConfirmationPageDisplayed =
            AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_NOTIFICATION_CONFIRMATION_PAGE_DISPLAYED;

    /** Logs that a notification was requested. */
    public static void logRequestedNotification(@NonNull Context context) {
        int action =
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_NOTIFICATION_REQUESTED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__REQUESTED_NOTIFICATION;
        sLogger.logUIStats(
                new UIStats.Builder()
                        .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                        .setRegion(getRegion(context))
                        .setAction(action)
                        .build());
    }

    /** Logs that notifications are disabled on a device. */
    public static void logNotificationDisabled(@NonNull Context context) {
        int action =
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_NOTIFICATION_DISABLED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__NOTIFICATION_DISABLED;
        sLogger.logUIStats(
                new UIStats.Builder()
                        .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                        .setRegion(getRegion(context))
                        .setAction(
                                AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__NOTIFICATION_DISABLED)
                        .build());
    }

    /**
     * Logs that a user has opted in, a conversion opt-in if the user was a default inactive user, a
     * regular opt-in if the user was a default active user.
     */
    public static void logOptInSelected(@NonNull Context context) {
        int action =
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__NOTIFICATION_OPT_IN_SELECTED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_IN_SELECTED;
        sLogger.logUIStats(
                new UIStats.Builder()
                        .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                        .setRegion(getRegion(context))
                        .setAction(action)
                        .build());
    }

    /**
     * Logs that a user has opted out, a conversion opt-out if the user was a default active user, a
     * regular opt-out if the user was a default inactive user.
     */
    public static void logOptOutSelected(@NonNull Context context) {
        int action =
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__NOTIFICATION_OPT_OUT_SELECTED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_OUT_SELECTED;
        sLogger.logUIStats(
                new UIStats.Builder()
                        .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                        .setRegion(getRegion(context))
                        .setAction(action)
                        .build());
    }

    /** Logs that the landing page was shown to a user. */
    public static void logLandingPageDisplayed(@NonNull Context context) {
        sLogger.logUIStats(
                new UIStats.Builder()
                        .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                        .setRegion(getRegion(context))
                        .setAction(
                                AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_DISPLAYED)
                        .build());
    }

    /** Logs that the GA landing page was shown to a user. */
    public static void logGaLandingPageDisplayed(@NonNull Context context) {
        sLogger.logUIStats(
                new UIStats.Builder()
                        .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                        .setRegion(getRegion(context))
                        .setAction(sGaUxNotificationLandingPageDisplayed)
                        .build());
    }

    /** Logs that the confirmation page was shown to a user. */
    public static void logConfirmationPageDisplayed(@NonNull Context context) {
        sLogger.logUIStats(
                new UIStats.Builder()
                        .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                        .setRegion(getRegion(context))
                        .setAction(
                                AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__CONFIRMATION_PAGE_DISPLAYED)
                        .build());
    }

    /** Logs that the GA confirmation page was shown to a user. */
    public static void logGaConfirmationPageDisplayed(@NonNull Context context) {
        sLogger.logUIStats(
                new UIStats.Builder()
                        .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                        .setRegion(getRegion(context))
                        .setAction(sGaUxNotificationConfirmationPageDisplayed)
                        .build());
    }

    /** Logs user opt-in action given an ApiType. */
    public static void logPerApiOptInSelected(@NonNull Context context, AdServicesApiType apiType) {
        int action = AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__ACTION_UNSPECIFIED;
        switch (apiType) {
            case TOPICS:
                action = AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__TOPICS_OPT_IN_SELECTED;
                break;
            case FLEDGE:
                action = AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__FLEDGE_OPT_IN_SELECTED;
                break;
            case MEASUREMENTS:
                action = AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__MEASUREMENT_OPT_IN_SELECTED;
                break;
        }
        sLogger.logUIStats(
                new UIStats.Builder()
                        .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                        .setRegion(getRegion(context))
                        .setAction(action)
                        .build());
    }

    /** Logs user opt-out action given an ApiType. */
    public static void logPerApiOptOutSelected(
            @NonNull Context context, AdServicesApiType apiType) {
        int action = AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__ACTION_UNSPECIFIED;
        switch (apiType) {
            case TOPICS:
                action = AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__TOPICS_OPT_OUT_SELECTED;
                break;
            case FLEDGE:
                action = AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__FLEDGE_OPT_OUT_SELECTED;
                break;
            case MEASUREMENTS:
                action = AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__MEASUREMENT_OPT_OUT_SELECTED;
                break;
        }
        sLogger.logUIStats(
                new UIStats.Builder()
                        .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                        .setRegion(getRegion(context))
                        .setAction(action)
                        .build());
    }

    /** Logs that a user has reset the measurement feature. */
    public static void logResetMeasurement(@NonNull Context context) {
        sLogger.logUIStats(
                new UIStats.Builder()
                        .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                        .setRegion(getRegion(context))
                        .setAction(
                                AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__RESET_MEASUREMENT_SELECTED)
                        .build());
    }

    /** Logs that a user has opened the measurement page. */
    public static void logManageMeasurement(@NonNull Context context) {
        sLogger.logUIStats(
                new UIStats.Builder()
                        .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                        .setRegion(getRegion(context))
                        .setAction(
                                AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__MANAGE_MEASUREMENT_SELECTED)
                        .build());
    }

    /** Logs that a user has opened the settings page. */
    public static void logSettingsPageDisplayed(@NonNull Context context) {
        sLogger.logUIStats(
                new UIStats.Builder()
                        .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                        .setRegion(getRegion(context))
                        .setAction(
                                AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__PRIVACY_SANDBOX_SETTINGS_PAGE_DISPLAYED)
                        .build());
    }

    private static int getRegion(@NonNull Context context) {
        return DeviceRegionProvider.isEuDevice(context)
                ? AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU
                : AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW;
    }
}
