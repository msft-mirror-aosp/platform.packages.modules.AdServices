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
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_AD_ID_STATE__AD_ID_DISABLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_AD_ID_STATE__AD_ID_ENABLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_AD_ID_STATE__STATE_UNSPECIFIED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__CONSENT_UNSPECIFIED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__FLEDGE_DEFAULT_OPT_IN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__FLEDGE_DEFAULT_OPT_OUT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__MEASUREMENT_DEFAULT_OPT_IN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__MEASUREMENT_DEFAULT_OPT_OUT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__PP_API_DEFAULT_OPT_IN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__PP_API_DEFAULT_OPT_OUT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__TOPICS_DEFAULT_OPT_IN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__TOPICS_DEFAULT_OPT_OUT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.consent.DeviceRegionProvider;

/** Logger for UiStats. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class UiStatsLogger {

    private static AdServicesLoggerImpl sLogger = AdServicesLoggerImpl.getInstance();

    /** Logs that a notification was requested. */
    public static void logRequestedNotification(@NonNull Context context) {
        UIStats uiStats = getBaseUiStats(context);

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_NOTIFICATION_REQUESTED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__REQUESTED_NOTIFICATION);

        sLogger.logUIStats(uiStats);
    }

    /** Logs that notifications are disabled on a device. */
    public static void logNotificationDisabled(@NonNull Context context) {
        UIStats uiStats = getBaseUiStats(context);

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_NOTIFICATION_DISABLED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__NOTIFICATION_DISABLED);

        sLogger.logUIStats(uiStats);
    }

    /** Logs that the landing page was shown to a user. */
    public static void logLandingPageDisplayed(@NonNull Context context) {
        UIStats uiStats = getBaseUiStats(context);

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_NOTIFICATION_LANDING_PAGE_DISPLAYED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_DISPLAYED);

        sLogger.logUIStats(uiStats);
    }

    /** Logs that the confirmation page was shown to a user. */
    public static void logConfirmationPageDisplayed(@NonNull Context context) {
        UIStats uiStats = getBaseUiStats(context);

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_NOTIFICATION_CONFIRMATION_PAGE_DISPLAYED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__CONFIRMATION_PAGE_DISPLAYED);

        sLogger.logUIStats(uiStats);
    }

    /** Logs that a user has reset the measurement feature. */
    public static void logResetMeasurement(@NonNull Context context) {
        UIStats uiStats = getBaseUiStats(context);

        uiStats.setAction(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__RESET_MEASUREMENT_SELECTED);

        sLogger.logUIStats(uiStats);
    }

    /** Logs that a user has opened the measurement page. */
    public static void logManageMeasurement(@NonNull Context context) {
        UIStats uiStats = getBaseUiStats(context);

        uiStats.setAction(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__MANAGE_MEASUREMENT_SELECTED);

        sLogger.logUIStats(uiStats);
    }

    /** Logs user opt-in action for PP API. */
    public static void logOptInSelected(@NonNull Context context) {
        UIStats uiStats = getBaseUiStats(context);

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__NOTIFICATION_OPT_IN_SELECTED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_IN_SELECTED);

        sLogger.logUIStats(uiStats);
    }

    /** Logs user opt-out action for PP API. */
    public static void logOptOutSelected(@NonNull Context context) {
        UIStats uiStats = getBaseUiStats(context);

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__NOTIFICATION_OPT_OUT_SELECTED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_OUT_SELECTED);

        sLogger.logUIStats(uiStats);
    }

    /** Logs user opt-in action given an ApiType. */
    public static void logOptInSelected(@NonNull Context context, AdServicesApiType apiType) {
        UIStats uiStats = getBaseUiStats(context, apiType);

        uiStats.setAction(getPerApiConsentAction(apiType, /* isOptIn */ true));

        sLogger.logUIStats(uiStats);
    }

    /** Logs user opt-out action given an ApiType. */
    public static void logOptOutSelected(@NonNull Context context, AdServicesApiType apiType) {
        UIStats uiStats = getBaseUiStats(context, apiType);

        uiStats.setAction(getPerApiConsentAction(apiType, /* isOptIn */ false));

        sLogger.logUIStats(uiStats);
    }

    /** Logs that a user has opened the settings page. */
    public static void logSettingsPageDisplayed(@NonNull Context context) {
        UIStats uiStats = getBaseUiStats(context);

        uiStats.setAction(
                AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__PRIVACY_SANDBOX_SETTINGS_PAGE_DISPLAYED);

        sLogger.logUIStats(uiStats);
    }

    private static int getRegion(@NonNull Context context) {
        return DeviceRegionProvider.isEuDevice(context)
                ? AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU
                : AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW;
    }

    private static int getDefaultConsent(@NonNull Context context) {
        Boolean defaultConsent = ConsentManager.getInstance(context).getDefaultConsent();
        // edge case where the user opens the settings pages before receiving consent notification.
        if (defaultConsent == null) {
            return AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__CONSENT_UNSPECIFIED;
        } else {
            return defaultConsent
                    ? AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__PP_API_DEFAULT_OPT_IN
                    : AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__PP_API_DEFAULT_OPT_OUT;
        }
    }

    private static int getDefaultAdIdState(@NonNull Context context) {
        Boolean defaultAdIdState = ConsentManager.getInstance(context).getDefaultAdIdState();
        // edge case where the user opens the settings pages before receiving consent notification.
        if (defaultAdIdState == null) {
            return AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_AD_ID_STATE__STATE_UNSPECIFIED;
        } else {
            return defaultAdIdState
                    ? AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_AD_ID_STATE__AD_ID_ENABLED
                    : AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_AD_ID_STATE__AD_ID_DISABLED;
        }
    }

    private static int getDefaultConsent(@NonNull Context context, AdServicesApiType apiType) {
        switch (apiType) {
            case TOPICS:
                Boolean topicsDefaultConsent =
                        ConsentManager.getInstance(context).getTopicsDefaultConsent();
                // edge case where the user checks topic consent before receiving consent
                // notification.
                if (topicsDefaultConsent == null) {
                    return AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_AD_ID_STATE__STATE_UNSPECIFIED;
                } else {
                    return topicsDefaultConsent
                            ? AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__TOPICS_DEFAULT_OPT_IN
                            : AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__TOPICS_DEFAULT_OPT_OUT;
                }
            case FLEDGE:
                Boolean fledgeDefaultConsent =
                        ConsentManager.getInstance(context).getFledgeDefaultConsent();
                // edge case where the user checks FLEDGE consent before receiving consent
                // notification.
                if (fledgeDefaultConsent == null) {
                    return AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_AD_ID_STATE__STATE_UNSPECIFIED;
                } else {
                    return fledgeDefaultConsent
                            ? AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__FLEDGE_DEFAULT_OPT_IN
                            : AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__FLEDGE_DEFAULT_OPT_OUT;
                }
            case MEASUREMENTS:
                Boolean measurementDefaultConsent =
                        ConsentManager.getInstance(context).getMeasurementDefaultConsent();
                // edge case where the user checks measurement consent before receiving consent
                // notification.
                if (measurementDefaultConsent == null) {
                    return AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_AD_ID_STATE__STATE_UNSPECIFIED;
                } else {
                    return measurementDefaultConsent
                            ? AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__MEASUREMENT_DEFAULT_OPT_IN
                            : AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__MEASUREMENT_DEFAULT_OPT_OUT;
                }
            default:
                return AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__CONSENT_UNSPECIFIED;
        }
    }

    private static int getPerApiConsentAction(AdServicesApiType apiType, boolean isOptIn) {
        switch (apiType) {
            case TOPICS:
                return isOptIn
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__TOPICS_OPT_IN_SELECTED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__TOPICS_OPT_OUT_SELECTED;
            case FLEDGE:
                return isOptIn
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__FLEDGE_OPT_IN_SELECTED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__FLEDGE_OPT_OUT_SELECTED;
            case MEASUREMENTS:
                return isOptIn
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__MEASUREMENT_OPT_IN_SELECTED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__MEASUREMENT_OPT_OUT_SELECTED;
            default:
                return AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__ACTION_UNSPECIFIED;
        }
    }

    private static UIStats getBaseUiStats(@NonNull Context context) {
        return new UIStats.Builder()
                .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                .setRegion(getRegion(context))
                .setDefaultConsent(getDefaultConsent(context))
                .setAdIdState(getDefaultAdIdState(context))
                .build();
    }

    private static UIStats getBaseUiStats(@NonNull Context context, AdServicesApiType apiType) {
        return new UIStats.Builder()
                .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                .setRegion(getRegion(context))
                .setDefaultConsent(getDefaultConsent(context, apiType))
                .setAdIdState(getDefaultAdIdState(context))
                .build();
    }
}
