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

package com.android.adservices.service.common;

import static com.android.adservices.service.AdServicesConfig.CONSENT_NOTIFICATION_JOB_ID;
import static com.android.adservices.service.AdServicesConfig.FLEDGE_BACKGROUND_FETCH_JOB_ID;
import static com.android.adservices.service.AdServicesConfig.MAINTENANCE_JOB_ID;
import static com.android.adservices.service.AdServicesConfig.MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID;
import static com.android.adservices.service.AdServicesConfig.MDD_CHARGING_PERIODIC_TASK_JOB_ID;
import static com.android.adservices.service.AdServicesConfig.MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID;
import static com.android.adservices.service.AdServicesConfig.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID;
import static com.android.adservices.service.AdServicesConfig.MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID;
import static com.android.adservices.service.AdServicesConfig.MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID;
import static com.android.adservices.service.AdServicesConfig.MEASUREMENT_ATTRIBUTION_JOB_ID;
import static com.android.adservices.service.AdServicesConfig.MEASUREMENT_DELETE_EXPIRED_JOB_ID;
import static com.android.adservices.service.AdServicesConfig.MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_ID;
import static com.android.adservices.service.AdServicesConfig.MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID;
import static com.android.adservices.service.AdServicesConfig.TOPICS_EPOCH_JOB_ID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.job.JobScheduler;
import android.content.Context;

import com.android.adservices.download.MddJobService;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.MaintenanceJobService;
import com.android.adservices.service.measurement.DeleteExpiredJobService;
import com.android.adservices.service.measurement.attribution.AttributionJobService;
import com.android.adservices.service.measurement.reporting.AggregateFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.AggregateReportingJobService;
import com.android.adservices.service.measurement.reporting.EventFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.EventReportingJobService;
import com.android.adservices.service.topics.EpochJobService;
import com.android.compatibility.common.util.TestUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

public class BackgroundJobsManagerTest {

    private Flags mMockFlags;

    @Before
    public void setUp() {
        mMockFlags = mock(Flags.class);
    }

    @Test
    public void testScheduleAllBackgroundJobs_killSwitchOff() throws Exception {
        runWithMocks(
                () -> {
                    ExtendedMockito.doReturn(false).when(mMockFlags).getMeasurementKillSwitch();
                    ExtendedMockito.doReturn(false).when(mMockFlags).getTopicsKillSwitch();

                    BackgroundJobsManager.scheduleAllBackgroundJobs(Mockito.mock(Context.class));

                    assertMeasurementJobsScheduled(1);
                    assertTopicsJobsScheduled(1);
                });
    }

    @Test
    public void testScheduleAllBackgroundJobs_measurementKillSwitchOn() throws Exception {
        runWithMocks(
                () -> {
                    ExtendedMockito.doReturn(true).when(mMockFlags).getMeasurementKillSwitch();
                    ExtendedMockito.doReturn(false).when(mMockFlags).getTopicsKillSwitch();

                    BackgroundJobsManager.scheduleAllBackgroundJobs(Mockito.mock(Context.class));

                    assertMeasurementJobsScheduled(0);
                    assertTopicsJobsScheduled(1);
                });
    }

    @Test
    public void testScheduleAllBackgroundJobs_topicsKillSwitchOn() throws Exception {
        runWithMocks(
                () -> {
                    ExtendedMockito.doReturn(false).when(mMockFlags).getMeasurementKillSwitch();
                    ExtendedMockito.doReturn(true).when(mMockFlags).getTopicsKillSwitch();

                    BackgroundJobsManager.scheduleAllBackgroundJobs(Mockito.mock(Context.class));

                    assertMeasurementJobsScheduled(1);
                    assertTopicsJobsScheduled(0);
                });
    }

    @Test
    public void testUnscheduleAllBackgroundJobs() {
        // Execute
        JobScheduler mockJobScheduler = mock(JobScheduler.class);
        BackgroundJobsManager.unscheduleAllBackgroundJobs(mockJobScheduler);

        // Verification
        verify(mockJobScheduler, times(1)).cancel(eq(MAINTENANCE_JOB_ID));
        verify(mockJobScheduler, times(1)).cancel(eq(TOPICS_EPOCH_JOB_ID));
        verify(mockJobScheduler, times(1)).cancel(eq(MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID));
        verify(mockJobScheduler, times(1)).cancel(eq(MEASUREMENT_DELETE_EXPIRED_JOB_ID));
        verify(mockJobScheduler, times(1)).cancel(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));
        verify(mockJobScheduler, times(1)).cancel(eq(MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_ID));
        verify(mockJobScheduler, times(1)).cancel(eq(MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID));
        verify(mockJobScheduler, times(1))
                .cancel(eq(MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID));
        verify(mockJobScheduler, times(1)).cancel(eq(FLEDGE_BACKGROUND_FETCH_JOB_ID));
        verify(mockJobScheduler, times(1)).cancel(eq(CONSENT_NOTIFICATION_JOB_ID));
        verify(mockJobScheduler, times(1)).cancel(eq(MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID));
        verify(mockJobScheduler, times(1)).cancel(eq(MDD_CHARGING_PERIODIC_TASK_JOB_ID));
        verify(mockJobScheduler, times(1)).cancel(eq(MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID));
        verify(mockJobScheduler, times(1)).cancel(eq(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID));
    }

    private void runWithMocks(TestUtils.RunnableWithThrow execute) throws Exception {
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(AggregateReportingJobService.class)
                        .spyStatic(AggregateFallbackReportingJobService.class)
                        .spyStatic(AttributionJobService.class)
                        .spyStatic(BackgroundJobsManager.class)
                        .spyStatic(EpochJobService.class)
                        .spyStatic(EventReportingJobService.class)
                        .spyStatic(EventFallbackReportingJobService.class)
                        .spyStatic(DeleteExpiredJobService.class)
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(MaintenanceJobService.class)
                        .spyStatic(MddJobService.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();

        try {
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
            ExtendedMockito.doNothing()
                    .when(() -> AggregateReportingJobService.scheduleIfNeeded(any(), anyBoolean()));
            ExtendedMockito.doNothing()
                    .when(
                            () ->
                                    AggregateFallbackReportingJobService.scheduleIfNeeded(
                                            any(), anyBoolean()));
            ExtendedMockito.doNothing()
                    .when(() -> AttributionJobService.scheduleIfNeeded(any(), anyBoolean()));
            ExtendedMockito.doReturn(true)
                    .when(() -> EpochJobService.scheduleIfNeeded(any(), anyBoolean()));
            ExtendedMockito.doNothing()
                    .when(() -> EventReportingJobService.scheduleIfNeeded(any(), anyBoolean()));
            ExtendedMockito.doNothing()
                    .when(
                            () ->
                                    EventFallbackReportingJobService.scheduleIfNeeded(
                                            any(), anyBoolean()));
            ExtendedMockito.doNothing()
                    .when(() -> DeleteExpiredJobService.scheduleIfNeeded(any(), anyBoolean()));
            ExtendedMockito.doReturn(true)
                    .when(() -> MaintenanceJobService.scheduleIfNeeded(any(), anyBoolean()));
            ExtendedMockito.doReturn(true)
                    .when(() -> MddJobService.scheduleIfNeeded(any(), anyBoolean()));

            // Execute
            execute.run();
        } finally {
            session.finishMocking();
        }
    }

    private void assertMeasurementJobsScheduled(int numberOfTimes) {
        ExtendedMockito.verify(
                () -> AggregateReportingJobService.scheduleIfNeeded(any(), eq(false)),
                times(numberOfTimes));
        ExtendedMockito.verify(
                () -> AggregateFallbackReportingJobService.scheduleIfNeeded(any(), eq(false)),
                times(numberOfTimes));
        ExtendedMockito.verify(
                () -> AttributionJobService.scheduleIfNeeded(any(), eq(false)),
                times(numberOfTimes));
        ExtendedMockito.verify(
                () -> EventReportingJobService.scheduleIfNeeded(any(), eq(false)),
                times(numberOfTimes));
        ExtendedMockito.verify(
                () -> EventFallbackReportingJobService.scheduleIfNeeded(any(), eq(false)),
                times(numberOfTimes));
        ExtendedMockito.verify(
                () -> DeleteExpiredJobService.scheduleIfNeeded(any(), eq(false)),
                times(numberOfTimes));
    }

    private void assertTopicsJobsScheduled(int numberOfTimes) {
        ExtendedMockito.verify(
                () -> EpochJobService.scheduleIfNeeded(any(), eq(false)), times(numberOfTimes));
        ExtendedMockito.verify(
                () -> MaintenanceJobService.scheduleIfNeeded(any(), eq(false)),
                times(numberOfTimes));
        ExtendedMockito.verify(
                () -> MddJobService.scheduleIfNeeded(any(), eq(false)), times(numberOfTimes));
    }
}
