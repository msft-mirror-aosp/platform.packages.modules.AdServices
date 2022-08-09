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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.android.adservices.api.R;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.UIStats;

/** Provides methods which can be used to display Privacy Sandbox consent notification. */
public class ConsentNotificationTrigger {
    // Random integer for NotificationCompat purposes
    private static final int NOTIFICATION_ID = 67920;
    private static final String CHANNEL_ID = "PRIVACY_SANDBOX_CHANNEL";
    private static final int NOTIFICATION_PRIORITY = NotificationCompat.PRIORITY_MAX;

    /**
     * Returns a {@link NotificationCompat.Builder} which can be used to display consent
     * notification to the user.
     *
     * @param context {@link Context} which is used to prepare a {@link NotificationCompat}.
     */
    public static NotificationCompat.Builder getConsentNotificationBuilder(
            @NonNull Context context, boolean isEuDevice) {
        Intent intent = new Intent(context, ConsentNotificationActivity.class);
        intent.putExtra(IS_EU_DEVICE_ARGUMENT_KEY, isEuDevice);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        context, 1, intent, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_android_icon_small)
                .setContentTitle(isEuDevice
                        ? context.getString(R.string.notificationUI_notification_title_eu)
                        : context.getString(R.string.notificationUI_notification_title))
                .setContentText(isEuDevice
                        ? context.getString(R.string.notificationUI_notification_content_eu)
                        : context.getString(R.string.notificationUI_notification_content))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(isEuDevice
                        ? context.getString(R.string.notificationUI_notification_content_eu)
                        : context.getString(R.string.notificationUI_notification_content)))
                .setPriority(NOTIFICATION_PRIORITY)
                .setContentIntent(pendingIntent);
    }

    /**
     * Shows consent notification as the highest priority notification to the user.
     *
     * @param context Context which is used to display {@link NotificationCompat}
     */
    public static void showConsentNotification(@NonNull Context context, boolean isEuDevice) {
        UIStats uiStats = new UIStats.Builder()
                .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                .setRegion(
                        isEuDevice
                                ? AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU
                                : AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW)
                .setAction(
                        AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__REQUESTED_NOTIFICATION)
                .build();
        AdServicesLoggerImpl.getInstance().logUIStats(uiStats);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        createNotificationChannel(context);
        NotificationCompat.Builder consentNotificationBuilder =
                getConsentNotificationBuilder(context, isEuDevice);

        notificationManager.notify(NOTIFICATION_ID, consentNotificationBuilder.build());
        ConsentManager.getInstance(context)
                .recordNotificationDisplayed(context.getPackageManager());
    }

    private static void createNotificationChannel(@NonNull Context context) {
        // TODO (b/230372892): styling -> adjust channels to use Android System labels.
        int importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.settingsUI_privacy_sandbox_beta_title),
                        importance);
        channel.setDescription(context.getString(R.string.settingsUI_privacy_sandbox_beta_title));
        NotificationManager notificationManager =
                context.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }
}
