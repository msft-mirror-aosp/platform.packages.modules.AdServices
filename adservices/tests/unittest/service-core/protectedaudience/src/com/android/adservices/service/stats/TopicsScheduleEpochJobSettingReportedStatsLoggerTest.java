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
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_UNKNOWN_SETTING;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_RESCHEDULE_SUCCESS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_SKIP_RESCHEDULE_EMPTY_JOB_SCHEDULER;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.shared.testing.AnswerSyncCallback;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public final class TopicsScheduleEpochJobSettingReportedStatsLoggerTest
        extends AdServicesMockitoTestCase {
    @Mock private AdServicesLogger mAdServicesLoggerMock;

    private TopicsScheduleEpochJobSettingReportedStatsLogger mLogger;

    @Test
    public void testTopicsScheduleEpochJobSettingReportedStatsLogger_successLoggingAllFields()
            throws InterruptedException {
        AnswerSyncCallback<Void> callback = AnswerSyncCallback.forSingleVoidAnswer();
        doAnswer(callback).when(mAdServicesLoggerMock)
                .logTopicsScheduleEpochJobSettingReportedStats(any());
        when(mMockFlags.getTopicsEpochJobBatteryConstraintLoggingEnabled()).thenReturn(true);
        when(mMockFlags.getTopicsEpochJobBatteryNotLowInsteadOfCharging()).thenReturn(true);
        mLogger = new TopicsScheduleEpochJobSettingReportedStatsLogger(
                mAdServicesLoggerMock, mMockFlags);

        ArgumentCaptor<TopicsScheduleEpochJobSettingReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(TopicsScheduleEpochJobSettingReportedStats.class);

        mLogger.setPreviousEpochJobStatus(
                /* previousScheduledEpochJobRequireBatteryNotLow */ false);
        mLogger.logScheduleIfNeeded();
        callback.assertCalled();

        // Verify the logging of TopicsScheduleEpochJobSettingReportedStats
        verify(mAdServicesLoggerMock)
                .logTopicsScheduleEpochJobSettingReportedStats(argumentCaptor.capture());

        TopicsScheduleEpochJobSettingReportedStats stats = argumentCaptor.getValue();
        assertThat(stats.getRescheduleEpochJobStatus())
                .isEqualTo(TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_RESCHEDULE_SUCCESS);
        assertThat(stats.getPreviousEpochJobSetting())
                .isEqualTo(TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_REQUIRES_CHARGING);
        assertThat(stats.getCurrentEpochJobSetting())
                .isEqualTo(TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_REQUIRES_BATTERY_NOT_LOW);
        assertThat(stats.getScheduleIfNeededEpochJobStatus())
                .isEqualTo(TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_REQUIRES_BATTERY_NOT_LOW);
    }

    @Test
    public void testTopicsScheduleEpochJobSettingReportedStatsLogger_skipReschedule()
            throws InterruptedException {
        AnswerSyncCallback<Void> callback = AnswerSyncCallback.forSingleVoidAnswer();
        doAnswer(callback).when(mAdServicesLoggerMock)
                .logTopicsScheduleEpochJobSettingReportedStats(any());
        when(mMockFlags.getTopicsEpochJobBatteryConstraintLoggingEnabled()).thenReturn(true);
        mLogger = new TopicsScheduleEpochJobSettingReportedStatsLogger(
                mAdServicesLoggerMock, mMockFlags);

        ArgumentCaptor<TopicsScheduleEpochJobSettingReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(TopicsScheduleEpochJobSettingReportedStats.class);

        mLogger.logSkipRescheduleEpochJob(
                TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_SKIP_RESCHEDULE_EMPTY_JOB_SCHEDULER);
        callback.assertCalled();

        // Verify the logging of TopicsScheduleEpochJobSettingReportedStats
        verify(mAdServicesLoggerMock)
                .logTopicsScheduleEpochJobSettingReportedStats(argumentCaptor.capture());

        TopicsScheduleEpochJobSettingReportedStats stats = argumentCaptor.getValue();
        assertThat(stats.getRescheduleEpochJobStatus())
                .isEqualTo(TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_SKIP_RESCHEDULE_EMPTY_JOB_SCHEDULER);
        assertThat(stats.getPreviousEpochJobSetting())
                .isEqualTo(TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_UNKNOWN_SETTING);
        assertThat(stats.getCurrentEpochJobSetting())
                .isEqualTo(TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_UNKNOWN_SETTING);
        assertThat(stats.getScheduleIfNeededEpochJobStatus())
                .isEqualTo(TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_UNKNOWN_SETTING);
    }

    @Test
    public void testLogTopicsScheduleEpochJobSettingReportedStats_noLogging() {
        when(mMockFlags.getTopicsEpochJobBatteryConstraintLoggingEnabled()).thenReturn(false);
        mLogger = new TopicsScheduleEpochJobSettingReportedStatsLogger(
                mAdServicesLoggerMock, mMockFlags);

        mLogger.setPreviousEpochJobStatus(
                /* previousScheduledEpochJobRequireBatteryNotLow */ false);
        mLogger.logScheduleIfNeeded();

        verifyZeroInteractions(mAdServicesLoggerMock);
    }
}
