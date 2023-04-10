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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.TriggerFixture;
import com.android.adservices.service.measurement.reporting.DebugReportApi.Type;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.quality.Strictness;

/** Unit tests for {@link DebugReport} */
@RunWith(MockitoJUnitRunner.class)
@SmallTest
public final class DebugReportApiTest {

    private static final UnsignedLong SOURCE_EVENT_ID = new UnsignedLong("7213872");
    private static final String LIMIT = "100";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private DebugReportApi mDebugReportApi;
    private MockitoSession mStaticMockSession;
    @Mock private IMeasurementDao mMeasurementDao;
    @Mock private Flags mFlags;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(DebugReportingJobService.class)
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();

        ExtendedMockito.doNothing()
                .when(() -> DebugReportingJobService.schedule(any(), any(), anyBoolean()));
        ExtendedMockito.doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);
        mDebugReportApi = spy(new DebugReportApi(mContext, mFlags));

        when(mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist())
                .thenReturn(SourceFixture.ValidSourceParams.ENROLLMENT_ID);
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(true);
        when(mFlags.getMeasurementEnableSourceDebugReport()).thenReturn(true);
        when(mFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(true);
    }

    @After
    public void teardown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testScheduleAppSourceSuccessDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void testScheduleWebSourceSuccessDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceSuccessDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceSuccessDebugReport_sourceFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableSourceDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceSuccessDebugReport_without_enrollmentId_dontSchedule()
            throws Exception {
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
    }

    @Test
    public void testScheduleSourceSuccessDebugReport_adTechNotOptIn_dontSchedule()
            throws Exception {
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
    }

    @Test
    public void testScheduleAppSourceSuccessDebugReport_without_adidPermission_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setAdIdPermission(false)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleWebSourceSuccessDebugReport_without_arDebugPermission_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceDestinationLimitDebugReport_success() throws Exception {
        Source source = SourceFixture.getValidSource();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceDestinationLimitDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source = SourceFixture.getValidSource();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceDestinationLimitDebugReport_sourceFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableSourceDebugReport()).thenReturn(false);
        Source source = SourceFixture.getValidSource();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceDestinationLimitDebugReport_without_enrollmentId_dontSchedule()
            throws Exception {
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

        mDebugReportApi.scheduleSourceDestinationLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceDestinationLimitDebugReport_adTechNotOptIn_dontSchedule()
            throws Exception {
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

        mDebugReportApi.scheduleSourceDestinationLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceNoisedDebugReport_success() throws Exception {
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
    }

    @Test
    public void testScheduleWebSourceNoisedDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleSourceNoisedDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceNoisedDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
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
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceNoisedDebugReport_sourceFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableSourceDebugReport()).thenReturn(false);
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
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceNoisedDebugReport_without_enrollmentId_dontSchedule()
            throws Exception {
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
    }

    @Test
    public void testScheduleSourceNoisedDebugReport_noAdIdPermission_dontSchedule()
            throws Exception {
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
    }

    @Test
    public void testScheduleSourceNoisedDebugReport_adTechNotOpIn_dontSchedule() throws Exception {
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
    }

    @Test
    public void testScheduleSourceStorageLimitDebugReport_success() throws Exception {
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

        mDebugReportApi.scheduleSourceStorageLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceStorageLimitDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
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

        mDebugReportApi.scheduleSourceStorageLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceStorageLimitDebugReport_sourceFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableSourceDebugReport()).thenReturn(false);
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

        mDebugReportApi.scheduleSourceStorageLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceStorageLimitDebugReport_adTechNotOpIn_dontSchedule()
            throws Exception {
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

        mDebugReportApi.scheduleSourceStorageLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceStorageLimitDebugReport_without_enrollmentId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .setEnrollmentId("")
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleSourceStorageLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleAppSourceUnknownErrorDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleSourceUnknownErrorDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void testScheduleWebSourceUnknownErrorDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleSourceUnknownErrorDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceUnknownErrorDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleSourceUnknownErrorDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceUnknownErrorDebugReport_sourceFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableSourceDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleSourceUnknownErrorDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceUnknownErrorDebugReport_without_enrollmentId_dontSchedule()
            throws Exception {
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

        mDebugReportApi.scheduleSourceUnknownErrorDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceUnknownErrorDebugReport_adTechNotOptIn_dontSchedule()
            throws Exception {
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

        mDebugReportApi.scheduleSourceUnknownErrorDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleAppSourceUnknownErrorDebugReport_without_adidPermission_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setAdIdPermission(false)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleSourceUnknownErrorDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleWebSourceUnknownErrorDebugReport_without_arDebugPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleSourceUnknownErrorDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerNoMatchingSourceDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(trigger, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerNoMatchingSourceDebugReport_success() throws Exception {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(trigger, mMeasurementDao);
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerNoMatchingSourceDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(trigger, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerNoMatchingSourceDebugReport_triggerFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(trigger, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerNoMatchingFilterDataDebugReport_sourceNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoLimitDebugReport(
                source,
                trigger,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_NO_MATCHING_FILTER_DATA);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerNoMatchingFilterDataDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoLimitDebugReport(
                source, trigger, mMeasurementDao, Type.TRIGGER_NO_MATCHING_FILTER_DATA);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerNoMatchingFilterDataDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoLimitDebugReport(
                source, trigger, mMeasurementDao, Type.TRIGGER_NO_MATCHING_FILTER_DATA);
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerNoMatchingFilterDataDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoLimitDebugReport(
                source, trigger, mMeasurementDao, Type.TRIGGER_NO_MATCHING_FILTER_DATA);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerNoMatchingFilterDataDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoLimitDebugReport(
                source, trigger, mMeasurementDao, Type.TRIGGER_NO_MATCHING_FILTER_DATA);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerEventNoMatchingConfigurationDebugReport_sourceNotOpIn_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoLimitDebugReport(
                source, trigger, mMeasurementDao, Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerEventNoMatchingConfigurationDebugReport_triggerNotOpIn_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoLimitDebugReport(
                source, trigger, mMeasurementDao, Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventNoMatchingConfigurationDebugReport_success()
            throws Exception {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoLimitDebugReport(
                source, trigger, mMeasurementDao, Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS);
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventNoMatchingConfigDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoLimitDebugReport(
                source, trigger, mMeasurementDao, Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerEventNoMatchingConfigDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoLimitDebugReport(
                source, trigger, mMeasurementDao, Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventLowPriorityDebugReport_sourceNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source, trigger, mMeasurementDao, Type.TRIGGER_EVENT_LOW_PRIORITY);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventLowPriorityDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source, trigger, mMeasurementDao, Type.TRIGGER_EVENT_LOW_PRIORITY);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventLowPriorityDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source, trigger, mMeasurementDao, Type.TRIGGER_EVENT_LOW_PRIORITY);
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventLowPriorityDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source, trigger, mMeasurementDao, Type.TRIGGER_EVENT_LOW_PRIORITY);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventLowPriorityDebugReport_triggerFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source, trigger, mMeasurementDao, Type.TRIGGER_EVENT_LOW_PRIORITY);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventExcessiveReportsDebugReport_sourceNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source, trigger, mMeasurementDao, Type.TRIGGER_EVENT_EXCESSIVE_REPORTS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventExcessiveReportsDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source, trigger, mMeasurementDao, Type.TRIGGER_EVENT_EXCESSIVE_REPORTS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventExcessiveReportsDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source, trigger, mMeasurementDao, Type.TRIGGER_EVENT_EXCESSIVE_REPORTS);
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventExcessiveReportsDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source, trigger, mMeasurementDao, Type.TRIGGER_EVENT_EXCESSIVE_REPORTS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerEventExcessiveReportsDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                DebugReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source, trigger, mMeasurementDao, Type.TRIGGER_EVENT_EXCESSIVE_REPORTS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }
}
