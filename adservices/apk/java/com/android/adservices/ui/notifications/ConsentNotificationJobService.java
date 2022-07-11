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

import static com.android.adservices.service.AdServicesConfig.CONSENT_NOTIFICATION_JOB_ID;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import androidx.annotation.NonNull;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.ConsentManager;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Consent Notification job. This will be run every day during acceptable hours (provided by PH
 * flags) to trigger the Notification for Privacy Sandbox.
 */
public class ConsentNotificationJobService extends JobService {
    static final String EEA_DEVICE = "com.google.android.feature.EEA_DEVICE";
    static final long MILLISECONDS_IN_THE_DAY = 86400000L;

    private ConsentManager mConsentManager;

    /** Schedule the Job. */
    public static void schedule(Context context) {
        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        long initialDelay = calculateInitialDelay(Calendar.getInstance(TimeZone.getDefault()));
        long deadline = calculateDeadline(Calendar.getInstance(TimeZone.getDefault()));
        final JobInfo job =
                new JobInfo.Builder(
                                CONSENT_NOTIFICATION_JOB_ID,
                                new ComponentName(context, ConsentNotificationJobService.class))
                        .setMinimumLatency(initialDelay)
                        .setOverrideDeadline(deadline)
                        .build();
        jobScheduler.schedule(job);
        LogUtil.d("Scheduling Consent notification job ...");
    }

    static long calculateInitialDelay(Calendar calendar) {
        Flags flags = FlagsFactory.getFlags();

        long millisecondsInTheCurrentDay = getMillisecondsInTheCurrentDay(calendar);

        // If the current time (millisecondsInTheCurrentDay) is before
        // ConsentNotificationIntervalBeginMs (by default 9AM), schedule a job the same day at
        // earliest (ConsentNotificationIntervalBeginMs).
        if (millisecondsInTheCurrentDay < flags.getConsentNotificationIntervalBeginMs()) {
            return flags.getConsentNotificationIntervalBeginMs() - millisecondsInTheCurrentDay;
        }

        // If the current time (millisecondsInTheCurrentDay) is in the interval:
        // (ConsentNotificationIntervalBeginMs, ConsentNotificationIntervalEndMs) schedule
        // a job ASAP.
        if (millisecondsInTheCurrentDay >= flags.getConsentNotificationIntervalBeginMs()
                && millisecondsInTheCurrentDay
                        < flags.getConsentNotificationIntervalEndMs()
                                - flags.getConsentNotificationMinimalDelayBeforeIntervalEnds()) {
            return 0L;
        }

        // If the current time (millisecondsInTheCurrentDay) is after
        // ConsentNotificationIntervalEndMs (by default 5 PM) schedule a job the following day at
        // ConsentNotificationIntervalBeginMs (by default 9AM).
        return MILLISECONDS_IN_THE_DAY
                - millisecondsInTheCurrentDay
                + flags.getConsentNotificationIntervalBeginMs();
    }

    static long calculateDeadline(Calendar calendar) {
        Flags flags = FlagsFactory.getFlags();
        long millisecondsInTheCurrentDay = getMillisecondsInTheCurrentDay(calendar);

        // If the current time (millisecondsInTheCurrentDay) is before
        // ConsentNotificationIntervalEndMs (by default 5PM) reduced by
        // ConsentNotificationMinimalDelayBeforeIntervalEnds (offset period - default 1 hour) set
        // a deadline for the ConsentNotificationIntervalEndMs the same day.
        if (millisecondsInTheCurrentDay
                < flags.getConsentNotificationIntervalEndMs()
                        - flags.getConsentNotificationMinimalDelayBeforeIntervalEnds()) {
            return flags.getConsentNotificationIntervalEndMs() - millisecondsInTheCurrentDay;
        }

        // Otherwise, set a deadline for the ConsentNotificationIntervalEndMs the following day.
        return MILLISECONDS_IN_THE_DAY
                - millisecondsInTheCurrentDay
                + flags.getConsentNotificationIntervalEndMs();
    }

    static boolean isEuDevice(Context context) {
        return context.getPackageManager().hasSystemFeature(EEA_DEVICE);
    }

    private static long getMillisecondsInTheCurrentDay(Calendar calendar) {
        long currentHour = calendar.get(Calendar.HOUR_OF_DAY);
        long currentMinute = calendar.get(Calendar.MINUTE);
        long currentSeconds = calendar.get(Calendar.SECOND);
        long currentMilliseconds = calendar.get(Calendar.MILLISECOND);
        long millisecondsInTheCurrentDay = 0;

        millisecondsInTheCurrentDay += currentHour * 60 * 60 * 1000;
        millisecondsInTheCurrentDay += currentMinute * 60 * 1000;
        millisecondsInTheCurrentDay += currentSeconds * 1000;
        millisecondsInTheCurrentDay += currentMilliseconds;

        return millisecondsInTheCurrentDay;
    }

    public void setConsentManager(@NonNull ConsentManager consentManager) {
        mConsentManager = consentManager;
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        LogUtil.d("ConsentNotificationJobService.onStartJob");

        AdServicesExecutors.getBackgroundExecutor()
                .execute(
                        () -> {
                            try {
                                if (mConsentManager.wasNotificationDisplayed()) {
                                    return;
                                }

                                ConsentNotificationTrigger.showConsentNotification(
                                        this, isEuDevice(this));
                            } finally {
                                jobFinished(params, false);
                            }
                        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogUtil.d("ConsentNotificationJobService.onStopJob");
        return true;
    }
}
