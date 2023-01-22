/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.ui.notifications;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__REQUESTED_NOTIFICATION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW;
import static com.android.adservices.ui.notifications.ConsentNotificationFragment.IS_EU_DEVICE_ARGUMENT_KEY;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.android.adservices.api.R;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.UIStats;
import com.android.adservices.ui.OTAResourcesManager;

/** Provides methods which can be used to display Privacy Sandbox consent notification. */
public class ConsentNotificationTrigger {
    // Random integer for NotificationCompat purposes
    private static final int NOTIFICATION_ID = 67920;
    private static final String CHANNEL_ID = "PRIVACY_SANDBOX_CHANNEL";
    private static final int NOTIFICATION_PRIORITY = NotificationCompat.PRIORITY_MAX;

    /**
     * Shows consent notification as the highest priority notification to the user.
     *
     * @param context Context which is used to display {@link NotificationCompat}
     */
    public static void showConsentNotification(@NonNull Context context, boolean isEuDevice) {
        boolean gaUxFeatureEnabled = FlagsFactory.getFlags().getGaUxFeatureEnabled();
        UIStats uiStats =
                new UIStats.Builder()
                        .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                        .setRegion(
                                isEuDevice
                                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU
                                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW)
                        .setAction(
                                AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__REQUESTED_NOTIFICATION)
                        .build();
        AdServicesLoggerImpl.getInstance().logUIStats(uiStats);
        // Set OTA resources if it exists.
        if (FlagsFactory.getFlags().getUiOtaStringsFeatureEnabled()) {
            OTAResourcesManager.applyOTAResources(context.getApplicationContext(), true);
        }
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        ConsentManager consentManager = ConsentManager.getInstance(context);

        if (!notificationManager.areNotificationsEnabled()) {
            recordNotificationDisplayed(gaUxFeatureEnabled, consentManager);
            // TODO(b/242001860): add logging
            return;
        }

        setupConsents(context, isEuDevice, gaUxFeatureEnabled, consentManager);

        createNotificationChannel(context);
        Notification notification = getNotification(context, isEuDevice, gaUxFeatureEnabled);
        notificationManager.notify(NOTIFICATION_ID, notification);

        recordNotificationDisplayed(gaUxFeatureEnabled, consentManager);
    }

    private static void recordNotificationDisplayed(
            boolean gaUxFeatureEnabled, ConsentManager consentManager) {
        if (gaUxFeatureEnabled) {
            consentManager.recordGaUxNotificationDisplayed();
        } else {
            consentManager.recordNotificationDisplayed();
        }
    }

    @NonNull
    private static Notification getNotification(
            @NonNull Context context, boolean isEuDevice, boolean gaUxFeatureEnabled) {
        Notification notification =
                gaUxFeatureEnabled
                        ? getGaConsentNotification(context, isEuDevice)
                        : getConsentNotification(context, isEuDevice);
        // make notification sticky (non-dismissible) for EuDevices when the GA UX feature is on
        if (gaUxFeatureEnabled && isEuDevice) {
            notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
        }
        return notification;
    }

    // setup default consents based on information whether the device is EU or non-EU device and
    // GA UX feature flag is enabled.
    private static void setupConsents(
            @NonNull Context context,
            boolean isEuDevice,
            boolean gaUxFeatureEnabled,
            ConsentManager consentManager) {
        // Keep the feature flag at the upper level to make it easier to cleanup the code once
        // the beta functionality is fully deprecated and abandoned.
        if (gaUxFeatureEnabled) {
            // EU: all APIs are by default disabled
            // ROW: all APIs are by default enabled
            // TODO(b/260266623): change consent state to UNDEFINED
            if (isEuDevice) {
                consentManager.disable(context, AdServicesApiType.TOPICS);
                consentManager.disable(context, AdServicesApiType.FLEDGE);
                consentManager.disable(context, AdServicesApiType.MEASUREMENTS);
            } else {
                consentManager.enable(context, AdServicesApiType.TOPICS);
                consentManager.enable(context, AdServicesApiType.FLEDGE);
                consentManager.enable(context, AdServicesApiType.MEASUREMENTS);
            }
        } else {
            // For the ROW devices, set the consent to GIVEN (enabled).
            // For the EU devices, set the consent to REVOKED (disabled)
            if (!isEuDevice) {
                consentManager.enable(context);
            } else {
                consentManager.disable(context);
            }
        }
    }

    /**
     * Returns a {@link NotificationCompat.Builder} which can be used to display consent
     * notification to the user when GaUxFeature flag is enabled.
     *
     * @param context {@link Context} which is used to prepare a {@link NotificationCompat}.
     */
    private static Notification getGaConsentNotification(
            @NonNull Context context, boolean isEuDevice) {
        Intent intent = new Intent(context, ConsentNotificationActivity.class);
        intent.putExtra(IS_EU_DEVICE_ARGUMENT_KEY, isEuDevice);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.BigTextStyle textStyle =
                new NotificationCompat.BigTextStyle()
                        .bigText(
                                isEuDevice
                                        ? context.getString(
                                                R.string.notificationUI_notification_ga_content_eu)
                                        : context.getString(
                                                R.string.notificationUI_notification_ga_content));
        NotificationCompat.Builder notification =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_info_icon)
                        .setContentTitle(
                                context.getString(
                                        isEuDevice
                                                ? R.string.notificationUI_notification_ga_title_eu
                                                : R.string.notificationUI_notification_ga_title))
                        .setContentText(
                                context.getString(
                                        isEuDevice
                                                ? R.string.notificationUI_notification_ga_content_eu
                                                : R.string.notificationUI_notification_ga_content))
                        .setStyle(textStyle)
                        .setPriority(NOTIFICATION_PRIORITY)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent);
        // EU needs a "View Details" CTA
        return isEuDevice
                ? notification
                        .addAction(
                                R.string.notificationUI_notification_ga_cta_eu,
                                context.getString(R.string.notificationUI_notification_ga_cta_eu),
                                pendingIntent)
                        .build()
                : notification.build();
    }

    /**
     * Returns a {@link NotificationCompat.Builder} which can be used to display consent
     * notification to the user.
     *
     * @param context {@link Context} which is used to prepare a {@link NotificationCompat}.
     */
    private static Notification getConsentNotification(
            @NonNull Context context, boolean isEuDevice) {
        Intent intent = new Intent(context, ConsentNotificationActivity.class);
        intent.putExtra(IS_EU_DEVICE_ARGUMENT_KEY, isEuDevice);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        context, 1, intent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.BigTextStyle textStyle =
                new NotificationCompat.BigTextStyle()
                        .bigText(
                                isEuDevice
                                        ? context.getString(
                                                R.string.notificationUI_notification_content_eu)
                                        : context.getString(
                                                R.string.notificationUI_notification_content));
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_info_icon)
                .setContentTitle(
                        context.getString(
                                isEuDevice
                                        ? R.string.notificationUI_notification_title_eu
                                        : R.string.notificationUI_notification_title))
                .setContentText(
                        context.getString(
                                isEuDevice
                                        ? R.string.notificationUI_notification_content_eu
                                        : R.string.notificationUI_notification_content))
                .setStyle(textStyle)
                .setPriority(NOTIFICATION_PRIORITY)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(
                        isEuDevice
                                ? R.string.notificationUI_notification_cta_eu
                                : R.string.notificationUI_notification_cta,
                        context.getString(
                                isEuDevice
                                        ? R.string.notificationUI_notification_cta_eu
                                        : R.string.notificationUI_notification_cta),
                        pendingIntent)
                .build();
    }

    private static void createNotificationChannel(@NonNull Context context) {
        // TODO (b/230372892): styling -> adjust channels to use Android System labels.
        int importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.settingsUI_main_view_title),
                        importance);
        channel.setDescription(context.getString(R.string.settingsUI_main_view_title));
        NotificationManager notificationManager =
                context.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }
}
