/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_REQUIRES_BATTERY_NOT_LOW;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_REQUIRES_CHARGING;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_RESCHEDULE_SUCCESS;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.concurrent.Future;

/** The class for logging {@link TopicsScheduleEpochJobSettingReportedStats}. */
public class TopicsScheduleEpochJobSettingReportedStatsLogger {
    private final AdServicesLogger mAdServicesLogger;

    private final TopicsScheduleEpochJobSettingReportedStats.Builder mBuilder;

    private final boolean mTopicsEpochJobBatteryConstraintLoggingEnabled;

    private final boolean mTopicsEpochJobBatteryNotLowInsteadOfCharging;

    @VisibleForTesting
    public TopicsScheduleEpochJobSettingReportedStatsLogger(
            AdServicesLogger adServicesLogger,
            Flags flags) {
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(flags);
        mAdServicesLogger = adServicesLogger;
        mBuilder = TopicsScheduleEpochJobSettingReportedStats.builder();
        mTopicsEpochJobBatteryConstraintLoggingEnabled =
                flags.getTopicsEpochJobBatteryConstraintLoggingEnabled();
        mTopicsEpochJobBatteryNotLowInsteadOfCharging =
                flags.getTopicsEpochJobBatteryNotLowInsteadOfCharging();
    }

    public static TopicsScheduleEpochJobSettingReportedStatsLogger getInstance() {
        return new TopicsScheduleEpochJobSettingReportedStatsLogger(
                AdServicesLoggerImpl.getInstance(),
                FlagsFactory.getFlags());
    }

    /**
     * Logs {@link TopicsScheduleEpochJobSettingReportedStats} in
     * {@code EpochJobService.scheduleIfNeeded()}.
     */
    @SuppressWarnings("FutureReturnValueIgnored") // TODO(b/331285831): fix this
    public void logScheduleIfNeeded() {
        if (!mTopicsEpochJobBatteryConstraintLoggingEnabled) {
            return;
        }
        int scheduleIfNeededEpochJobStatus =
                mTopicsEpochJobBatteryNotLowInsteadOfCharging
                        ? TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_REQUIRES_BATTERY_NOT_LOW
                        : TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_REQUIRES_CHARGING;
        mBuilder.setScheduleIfNeededEpochJobStatus(scheduleIfNeededEpochJobStatus);

        Future<?> unusedLogScheduleIfNeededFuture =
                AdServicesExecutors.getBackgroundExecutor().submit(
                        () -> mAdServicesLogger
                                .logTopicsScheduleEpochJobSettingReportedStats(
                                        mBuilder.build()));
    }

    /**
     * Logs {@link TopicsScheduleEpochJobSettingReportedStats} when
     * {@code EpochJobService.rescheduleEpochJob} is skipped because of some reasons.
     *
     * @param skipReason The reason of skipping reschedule epoch job.
     */
    @SuppressWarnings("FutureReturnValueIgnored") // TODO(b/331285831): fix this
    public void logSkipRescheduleEpochJob(
            @AdsRelevanceStatusUtils.TopicsRescheduleEpochJobStatus int skipReason) {
        if (!mTopicsEpochJobBatteryConstraintLoggingEnabled) {
            return;
        }
        mBuilder.setRescheduleEpochJobStatus(skipReason);

        Future<?> unusedLogSkipRescheduleEpochJobFuture =
                AdServicesExecutors.getBackgroundExecutor().submit(
                        () -> mAdServicesLogger
                                .logTopicsScheduleEpochJobSettingReportedStats(
                                        mBuilder.build()));
    }

    /**
     * Sets the previous epoch job configuration when the epoch job is rescheduled successfully in
     * {@code EpochJobService.rescheduleEpochJob}.
     *
     * @param previousScheduledEpochJobRequireBatteryNotLow The battery not low configuration of
     *                                                      previous epoch job.
     */
    public void setPreviousEpochJobStatus(
            boolean previousScheduledEpochJobRequireBatteryNotLow) {
        if (!mTopicsEpochJobBatteryConstraintLoggingEnabled) {
            return;
        }
        int previousEpochJobSetting =
                previousScheduledEpochJobRequireBatteryNotLow
                        ? TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_REQUIRES_BATTERY_NOT_LOW
                        : TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_REQUIRES_CHARGING;
        int currentEpochJobSetting =
                mTopicsEpochJobBatteryNotLowInsteadOfCharging
                        ? TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_REQUIRES_BATTERY_NOT_LOW
                        : TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_REQUIRES_CHARGING;
        mBuilder.setRescheduleEpochJobStatus(
                TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_RESCHEDULE_SUCCESS);
        mBuilder.setPreviousEpochJobSetting(previousEpochJobSetting);
        mBuilder.setCurrentEpochJobSetting(currentEpochJobSetting);
    }
}
