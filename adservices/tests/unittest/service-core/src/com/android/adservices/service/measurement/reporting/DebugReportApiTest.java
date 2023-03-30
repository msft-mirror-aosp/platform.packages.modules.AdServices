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
package com.android.adservices.service.measurement.reporting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.compatibility.common.util.TestUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;


/** Unit tests for {@link DebugReport} */
@SmallTest
public final class DebugReportApiTest {

    private static final UnsignedLong SOURCE_EVENT_ID = new UnsignedLong("7213872");
    private static final String LIMIT = "100";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private DebugReportApi mDebugReportApi;
    @Mock private IMeasurementDao mMeasurementDao;

    @Before
    public void setup() {
        mDebugReportApi = new DebugReportApi(mContext);
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testScheduleAppSourceSuccessDebugReport_success() throws Exception {
        runWithMocks(
                () -> {
                    Source source =
                            SourceFixture.getValidSourceBuilder()
                                    .setEventId(SOURCE_EVENT_ID)
                                    .setIsDebugReporting(true)
                                    .setPublisherType(EventSurfaceType.APP)
                                    .setAppDestinations(
                                            SourceFixture.ValidSourceParams
                                                    .ATTRIBUTION_DESTINATIONS)
                                    .setAdIdPermission(true)
                                    .build();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            DebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean(), anyBoolean()));

                    mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao);
                    verify(mMeasurementDao, times(1)).insertDebugReport(any());
                });
    }

    @Test
    public void testScheduleWebSourceSuccessDebugReport_success() throws Exception {
        runWithMocks(
                () -> {
                    Source source =
                            SourceFixture.getValidSourceBuilder()
                                    .setEventId(SOURCE_EVENT_ID)
                                    .setIsDebugReporting(true)
                                    .setPublisherType(EventSurfaceType.WEB)
                                    .setWebDestinations(
                                            SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                                    .setArDebugPermission(true)
                                    .build();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            DebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean(), anyBoolean()));

                    mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao);
                    verify(mMeasurementDao, times(1)).insertDebugReport(any());
                });
    }

    @Test
    public void testScheduleSourceSuccessDebugReport_without_enrollmentId_dontSchedule()
            throws Exception {
        runWithMocks(
                () -> {
                    Source source =
                            SourceFixture.getValidSourceBuilder()
                                    .setEventId(SOURCE_EVENT_ID)
                                    .setIsDebugReporting(true)
                                    .setAdIdPermission(true)
                                    .setEnrollmentId("")
                                    .build();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            DebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean(), anyBoolean()));

                    mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao);
                    verify(mMeasurementDao, never()).insertDebugReport(any());
                });
    }

    @Test
    public void testScheduleSourceSuccessDebugReport_adTechNotOptIn_dontSchedule()
            throws Exception {
        runWithMocks(
                () -> {
                    Source source =
                            SourceFixture.getValidSourceBuilder()
                                    .setEventId(SOURCE_EVENT_ID)
                                    .setIsDebugReporting(false)
                                    .setAdIdPermission(true)
                                    .build();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            DebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean(), anyBoolean()));

                    mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao);
                    verify(mMeasurementDao, never()).insertDebugReport(any());
                });
    }

    @Test
    public void testScheduleAppSourceSuccessDebugReport_without_adidPermission_dontSchedule()
            throws Exception {
        runWithMocks(
                () -> {
                    Source source =
                            SourceFixture.getValidSourceBuilder()
                                    .setEventId(SOURCE_EVENT_ID)
                                    .setIsDebugReporting(true)
                                    .setPublisherType(EventSurfaceType.APP)
                                    .setAppDestinations(
                                            SourceFixture.ValidSourceParams
                                                    .ATTRIBUTION_DESTINATIONS)
                                    .setAdIdPermission(false)
                                    .build();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            DebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean(), anyBoolean()));

                    mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao);
                    verify(mMeasurementDao, never()).insertDebugReport(any());
                });
    }

    @Test
    public void testScheduleWebSourceSuccessDebugReport_without_arDebugPermission_dontSchedule()
            throws Exception {
        runWithMocks(
                () -> {
                    Source source =
                            SourceFixture.getValidSourceBuilder()
                                    .setEventId(SOURCE_EVENT_ID)
                                    .setIsDebugReporting(true)
                                    .setPublisherType(EventSurfaceType.WEB)
                                    .setWebDestinations(
                                            SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                                    .setArDebugPermission(false)
                                    .build();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            DebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean(), anyBoolean()));

                    mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao);
                    verify(mMeasurementDao, never()).insertDebugReport(any());
                });
    }

    @Test
    public void testScheduleSourceDestinationLimitDebugReport_success() throws Exception {
        runWithMocks(
                () -> {
                    Source source = SourceFixture.getValidSource();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            DebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean(), anyBoolean()));

                    mDebugReportApi.scheduleSourceDestinationLimitDebugReport(
                            source, LIMIT, mMeasurementDao);
                    verify(mMeasurementDao, times(1)).insertDebugReport(any());
                });
    }

    @Test
    public void testScheduleSourceDestinationLimitDebugReport_without_enrollmentId_dontSchedule()
            throws Exception {
        runWithMocks(
                () -> {
                    Source source =
                            SourceFixture.getValidSourceBuilder()
                                    .setEventId(SOURCE_EVENT_ID)
                                    .setIsDebugReporting(true)
                                    .setEnrollmentId("")
                                    .build();

                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            DebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean(), anyBoolean()));

                    mDebugReportApi.scheduleSourceDestinationLimitDebugReport(
                            source, LIMIT, mMeasurementDao);
                    verify(mMeasurementDao, never()).insertDebugReport(any());
                });
    }

    @Test
    public void testScheduleSourceDestinationLimitDebugReport_adTechNotOptIn_dontSchedule()
            throws Exception {
        runWithMocks(
                () -> {
                    Source source =
                            SourceFixture.getValidSourceBuilder()
                                    .setEventId(SOURCE_EVENT_ID)
                                    .setIsDebugReporting(false)
                                    .build();

                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            DebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean(), anyBoolean()));

                    mDebugReportApi.scheduleSourceDestinationLimitDebugReport(
                            source, LIMIT, mMeasurementDao);
                    verify(mMeasurementDao, never()).insertDebugReport(any());
                });
    }

    @Test
    public void testScheduleSourceNoisedDebugReport_success() throws Exception {
        runWithMocks(
                () -> {
                    Source source =
                            SourceFixture.getValidSourceBuilder()
                                    .setEventId(SOURCE_EVENT_ID)
                                    .setIsDebugReporting(true)
                                    .setAdIdPermission(true)
                                    .build();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            DebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean(), anyBoolean()));

                    mDebugReportApi.scheduleSourceNoisedDebugReport(source, mMeasurementDao);
                    verify(mMeasurementDao, times(1)).insertDebugReport(any());
                });
    }

    @Test
    public void testScheduleWebSourceNoisedDebugReport_success() throws Exception {
        runWithMocks(
                () -> {
                    Source source =
                            SourceFixture.getValidSourceBuilder()
                                    .setEventId(SOURCE_EVENT_ID)
                                    .setIsDebugReporting(true)
                                    .setPublisherType(EventSurfaceType.WEB)
                                    .setWebDestinations(
                                            SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                                    .setArDebugPermission(true)
                                    .build();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            DebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean(), anyBoolean()));

                    mDebugReportApi.scheduleSourceNoisedDebugReport(source, mMeasurementDao);
                    verify(mMeasurementDao, times(1)).insertDebugReport(any());
                });
    }

    @Test
    public void testScheduleSourceNoisedDebugReport_without_enrollmentId_dontSchedule()
            throws Exception {
        runWithMocks(
                () -> {
                    Source source =
                            SourceFixture.getValidSourceBuilder()
                                    .setEventId(SOURCE_EVENT_ID)
                                    .setIsDebugReporting(true)
                                    .setAdIdPermission(true)
                                    .setEnrollmentId("")
                                    .build();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            DebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean(), anyBoolean()));

                    mDebugReportApi.scheduleSourceNoisedDebugReport(source, mMeasurementDao);
                    verify(mMeasurementDao, never()).insertDebugReport(any());
                });
    }

    @Test
    public void testScheduleSourceNoisedDebugReport_noAdIdPermission_dontSchedule()
            throws Exception {
        runWithMocks(
                () -> {
                    Source source =
                            SourceFixture.getValidSourceBuilder()
                                    .setEventId(SOURCE_EVENT_ID)
                                    .setIsDebugReporting(true)
                                    .setAdIdPermission(false)
                                    .build();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            DebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean(), anyBoolean()));

                    mDebugReportApi.scheduleSourceNoisedDebugReport(source, mMeasurementDao);
                    verify(mMeasurementDao, never()).insertDebugReport(any());
                });
    }

    @Test
    public void testScheduleSourceNoisedDebugReport_adTechNotOpIn_dontSchedule() throws Exception {
        runWithMocks(
                () -> {
                    Source source =
                            SourceFixture.getValidSourceBuilder()
                                    .setEventId(SOURCE_EVENT_ID)
                                    .setIsDebugReporting(false)
                                    .setAdIdPermission(true)
                                    .build();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            DebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean(), anyBoolean()));

                    mDebugReportApi.scheduleSourceNoisedDebugReport(source, mMeasurementDao);
                    verify(mMeasurementDao, never()).insertDebugReport(any());
                });
    }

    @Test
    public void testScheduleSourceStorageLimitDebugReport_success() throws Exception {
        runWithMocks(
                () -> {
                    Source source =
                            SourceFixture.getValidSourceBuilder()
                                    .setEventId(SOURCE_EVENT_ID)
                                    .setIsDebugReporting(true)
                                    .setAdIdPermission(true)
                                    .build();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            DebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean(), anyBoolean()));

                    mDebugReportApi.scheduleSourceStorageLimitDebugReport(
                            source, LIMIT, mMeasurementDao);
                    verify(mMeasurementDao, times(1)).insertDebugReport(any());
                });
    }

    @Test
    public void testScheduleSourceStorageLimitDebugReport_adTechNotOpIn_dontSchedule()
            throws Exception {
        runWithMocks(
                () -> {
                    Source source =
                            SourceFixture.getValidSourceBuilder()
                                    .setEventId(SOURCE_EVENT_ID)
                                    .setIsDebugReporting(false)
                                    .setAdIdPermission(true)
                                    .build();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            DebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean(), anyBoolean()));

                    mDebugReportApi.scheduleSourceStorageLimitDebugReport(
                            source, LIMIT, mMeasurementDao);
                    verify(mMeasurementDao, never()).insertDebugReport(any());
                });
    }

    private void runWithMocks(TestUtils.RunnableWithThrow execute) throws Exception {
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(DebugReportingJobService.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        try {
            ExtendedMockito.doNothing()
                    .when(() -> DebugReportingJobService.schedule(any(), any(), anyBoolean()));

            // Execute
            execute.run();
        } finally {
            session.finishMocking();
        }
    }
}
