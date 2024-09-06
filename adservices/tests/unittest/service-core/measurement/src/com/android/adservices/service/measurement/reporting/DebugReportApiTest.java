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


import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.WebUtil;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.TriggerFixture;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link DebugReport} */
@SpyStatic(VerboseDebugReportingJobService.class)
@SpyStatic(FlagsFactory.class)
public final class DebugReportApiTest extends AdServicesExtendedMockitoTestCase {

    private static final UnsignedLong SOURCE_EVENT_ID = new UnsignedLong("7213872");
    private static final UnsignedLong TRIGGER_DATA = new UnsignedLong(1L);
    private static final Uri TEST_REGISTRANT = Uri.parse("android-app://com.registrant");
    private static final Uri TEST_REGISTRATION_ORIGIN =
            WebUtil.validUri("https://subdomain.example.test");
    private static final String HEADER_NAME_SOURCE_REGISTRATION =
            "Attribution-Reporting-Register-Source";
    private static final String TEST_ENROLLMENT_ID = "enrollment-id";
    private static final String TEST_HEADER_CONTENT = "header-content";

    private static final String LIMIT = "100";

    private DebugReportApi mDebugReportApi;
    @Mock private IMeasurementDao mMeasurementDao;

    @Before
    public void setup() {
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.schedule(any(), any()));
        mocker.mockGetFlagsForTesting();
        mDebugReportApi = spy(new DebugReportApi(mContext, mMockFlags));

        when(mMockFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist())
                .thenReturn(SourceFixture.ValidSourceParams.ENROLLMENT_ID);
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(true);
        when(mMockFlags.getMeasurementEnableSourceDebugReport()).thenReturn(true);
        when(mMockFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(true);
        when(mMockFlags.getMeasurementVtcConfigurableMaxEventReportsCount())
                .thenReturn(Flags.DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT);
        when(mMockFlags.getMeasurementEventReportsVtcEarlyReportingWindows())
                .thenReturn(Flags.MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS);
        when(mMockFlags.getMeasurementEventReportsCtcEarlyReportingWindows())
                .thenReturn(Flags.MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS);
        when(mMockFlags.getMeasurementMaxReportStatesPerSourceRegistration())
                .thenReturn(Flags.MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION);
        when(mMockFlags.getMeasurementAttributionScopeMaxInfoGainNavigation()).thenReturn(11.5f);
    }

    @Test
    public void testScheduleAppToAppSourceSuccessDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao, Map.of());
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertSourceDebugReportParameters(
                report,
                DebugReportApi.Type.SOURCE_SUCCESS.getValue(),
                SourceFixture.ValidSourceParams.PUBLISHER.toString(),
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS.get(0).toString(),
                Map.of());
    }

    @Test
    public void testScheduleWebToWebSourceSuccessDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao, Map.of());
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertSourceDebugReportParameters(
                report,
                DebugReportApi.Type.SOURCE_SUCCESS.getValue(),
                SourceFixture.ValidSourceParams.WEB_PUBLISHER.toString(),
                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0).toString(),
                Map.of());
    }

    @Test
    public void testScheduleAppToWebSourceSuccessDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao, Map.of());
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport actualReport = captor.getValue();

        assertSourceDebugReportParameters(
                actualReport,
                DebugReportApi.Type.SOURCE_SUCCESS.getValue(),
                SourceFixture.ValidSourceParams.PUBLISHER.toString(),
                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0).toString(),
                Map.of());
    }

    @Test
    public void testScheduleWebToAppSourceSuccessDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setWebDestinations(null)
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao, Map.of());
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport actualReport = captor.getValue();

        assertSourceDebugReportParameters(
                actualReport,
                DebugReportApi.Type.SOURCE_SUCCESS.getValue(),
                SourceFixture.ValidSourceParams.WEB_PUBLISHER.toString(),
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS.get(0).toString(),
                Map.of());
    }

    @Test
    public void testScheduleAppToAppAndWebSourceSuccessDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao, Map.of());
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport actualReport = captor.getValue();

        assertSourceDebugReportParameters(
                actualReport,
                DebugReportApi.Type.SOURCE_SUCCESS.getValue(),
                SourceFixture.ValidSourceParams.PUBLISHER.toString(),
                new JSONArray(
                                Arrays.asList(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS
                                                .get(0)
                                                .toString(),
                                        SourceFixture.ValidSourceParams.WEB_DESTINATIONS
                                                .get(0)
                                                .toString()))
                        .toString(),
                Map.of());
    }

    @Test
    public void testScheduleWebToAppAndWebSourceSuccessDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao, Map.of());
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport actualReport = captor.getValue();

        assertSourceDebugReportParameters(
                actualReport,
                DebugReportApi.Type.SOURCE_SUCCESS.getValue(),
                SourceFixture.ValidSourceParams.WEB_PUBLISHER.toString(),
                new JSONArray(
                                Arrays.asList(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS
                                                .get(0)
                                                .toString(),
                                        SourceFixture.ValidSourceParams.WEB_DESTINATIONS
                                                .get(0)
                                                .toString()))
                        .toString(),
                Map.of());
    }

    @Test
    public void testScheduleSourceSuccessDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao, Map.of());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceSuccessDebugReport_sourceFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSourceDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao, Map.of());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceSuccessDebugReport_without_enrollmentId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .setEnrollmentId("")
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao, Map.of());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceSuccessDebugReport_adTechNotOptIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao, Map.of());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleAppSourceSuccessDebugReport_without_adidPermission_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setAdIdPermission(false)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao, Map.of());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleWebSourceSuccessDebugReport_without_arDebugPermission_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao, Map.of());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void scheduleSourceDestinationLimitDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setWebDestinations(null)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationLimitDebugReport(source, LIMIT, mMeasurementDao);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertSourceDebugReportParameters(
                report,
                DebugReportApi.Type.SOURCE_DESTINATION_LIMIT.getValue(),
                SourceFixture.ValidSourceParams.PUBLISHER.toString(),
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS.get(0).toString(),
                Map.of(DebugReportApi.Body.LIMIT, LIMIT));
    }

    @Test
    public void scheduleSourceDestinationPerMinuteRateLimitDebugReport__success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setWebDestinations(null)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationPerMinuteRateLimitDebugReport(
                source, LIMIT, mMeasurementDao);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertSourceDebugReportParameters(
                report,
                DebugReportApi.Type.SOURCE_DESTINATION_RATE_LIMIT.getValue(),
                SourceFixture.ValidSourceParams.PUBLISHER.toString(),
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS.get(0).toString(),
                Map.of(DebugReportApi.Body.LIMIT, LIMIT));
    }

    @Test
    public void scheduleSourceDestinationPerDayRateLimitDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setWebDestinations(null)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationPerDayRateLimitDebugReport(
                source, LIMIT, mMeasurementDao);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertSourceDebugReportParameters(
                report,
                DebugReportApi.Type.SOURCE_DESTINATION_PER_DAY_RATE_LIMIT.getValue(),
                SourceFixture.ValidSourceParams.PUBLISHER.toString(),
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS.get(0).toString(),
                Map.of(DebugReportApi.Body.LIMIT, LIMIT));
    }

    @Test
    public void scheduleSourceDestinationLimitDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source = SourceFixture.getValidSource();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            scheduleSourceDestinationPerMinuteRateLimitDebugReport_debugFlagDisabled_dontSchedule()
                    throws Exception {
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source = SourceFixture.getValidSource();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationPerMinuteRateLimitDebugReport(
                source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void scheduleSourceDestinationPerDayRateLimitDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source = SourceFixture.getValidSource();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationPerDayRateLimitDebugReport(
                source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void scheduleSourceDestinationLimitDebugReport_sourceFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSourceDebugReport()).thenReturn(false);
        Source source = SourceFixture.getValidSource();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            scheduleSourceDestinationPerMinuteRateLimitDebugReport_sourceFlagDisabled_dontSchedule()
                    throws Exception {
        when(mMockFlags.getMeasurementEnableSourceDebugReport()).thenReturn(false);
        Source source = SourceFixture.getValidSource();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationPerMinuteRateLimitDebugReport(
                source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            scheduleSourceDestinationPerDayRateLimitDebugReport_sourceFlagDisabled_dontSchedule()
                    throws Exception {
        when(mMockFlags.getMeasurementEnableSourceDebugReport()).thenReturn(false);
        Source source = SourceFixture.getValidSource();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationPerDayRateLimitDebugReport(
                source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void scheduleSourceDestinationLimitDebugReport_without_enrollmentId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setEnrollmentId("")
                        .build();

        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            scheduleSourceDestinationPerMinuteRateLimitDebugReport_withoutEnrollment_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setEnrollmentId("")
                        .build();

        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationPerMinuteRateLimitDebugReport(
                source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            scheduleSourceDestinationPerDayRateLimitDebugReport_without_enrollmentId_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setEnrollmentId("")
                        .build();

        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationPerDayRateLimitDebugReport(
                source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void scheduleSourceDestinationLimitDebugReport_adTechNotOptIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(false)
                        .build();

        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void scheduleSourceDestinationPerMinuteRateLimitDebugReport_adTechNotOptIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(false)
                        .build();

        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationPerMinuteRateLimitDebugReport(
                source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceNoisedDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        Map<String, String> additionalParam = Map.of("paramKey", "paramValue");
        mDebugReportApi.scheduleSourceNoisedDebugReport(source, mMeasurementDao, additionalParam);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());

        DebugReport report = captor.getValue();
        assertSourceDebugReportParameters(
                report,
                DebugReportApi.Type.SOURCE_NOISED.getValue(),
                SourceFixture.ValidSourceParams.PUBLISHER.toString(),
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS.get(0).toString(),
                additionalParam);
    }

    @Test
    public void testScheduleWebSourceNoisedDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        Map<String, String> additionalParam = Map.of("paramKey", "paramValue");
        mDebugReportApi.scheduleSourceNoisedDebugReport(source, mMeasurementDao, additionalParam);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertSourceDebugReportParameters(
                report,
                DebugReportApi.Type.SOURCE_NOISED.getValue(),
                SourceFixture.ValidSourceParams.WEB_PUBLISHER.toString(),
                new JSONArray(
                                Arrays.asList(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS
                                                .get(0)
                                                .toString(),
                                        SourceFixture.ValidSourceParams.WEB_DESTINATIONS
                                                .get(0)
                                                .toString()))
                        .toString(),
                additionalParam);
    }

    @Test
    public void testScheduleSourceNoisedDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceNoisedDebugReport(source, mMeasurementDao, null);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceNoisedDebugReport_sourceFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSourceDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceNoisedDebugReport(source, mMeasurementDao, null);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceNoisedDebugReport_without_enrollmentId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .setEnrollmentId("")
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceNoisedDebugReport(source, mMeasurementDao, null);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceNoisedDebugReport_noAdIdPermission_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceNoisedDebugReport(source, mMeasurementDao, null);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceNoisedDebugReport_adTechNotOpIn_dontSchedule() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceNoisedDebugReport(source, mMeasurementDao, null);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceStorageLimitDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceStorageLimitDebugReport(source, LIMIT, mMeasurementDao);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertSourceDebugReportParameters(
                report,
                DebugReportApi.Type.SOURCE_STORAGE_LIMIT.getValue(),
                SourceFixture.ValidSourceParams.PUBLISHER.toString(),
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS.get(0).toString(),
                Map.of(DebugReportApi.Body.LIMIT, LIMIT));
    }

    @Test
    public void testScheduleSourceStorageLimitDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceStorageLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceStorageLimitDebugReport_sourceFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSourceDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceStorageLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceStorageLimitDebugReport_adTechNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceStorageLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceStorageLimitDebugReport_without_enrollmentId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .setEnrollmentId("")
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceStorageLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleAppSourceUnknownErrorDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceUnknownErrorDebugReport(source, mMeasurementDao);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertSourceDebugReportParameters(
                report,
                DebugReportApi.Type.SOURCE_UNKNOWN_ERROR.getValue(),
                SourceFixture.ValidSourceParams.PUBLISHER.toString(),
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS.get(0).toString(),
                Map.of());
    }

    @Test
    public void testScheduleWebSourceUnknownErrorDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceUnknownErrorDebugReport(source, mMeasurementDao);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertSourceDebugReportParameters(
                report,
                DebugReportApi.Type.SOURCE_UNKNOWN_ERROR.getValue(),
                SourceFixture.ValidSourceParams.WEB_PUBLISHER.toString(),
                new JSONArray(
                                Arrays.asList(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS
                                                .get(0)
                                                .toString(),
                                        SourceFixture.ValidSourceParams.WEB_DESTINATIONS
                                                .get(0)
                                                .toString()))
                        .toString(),
                Map.of());
    }

    @Test
    public void testScheduleSourceUnknownErrorDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceUnknownErrorDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceUnknownErrorDebugReport_sourceFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSourceDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceUnknownErrorDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceUnknownErrorDebugReport_without_enrollmentId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .setEnrollmentId("")
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceUnknownErrorDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceUnknownErrorDebugReport_adTechNotOptIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceUnknownErrorDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleAppSourceUnknownErrorDebugReport_without_adidPermission_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setAdIdPermission(false)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceUnknownErrorDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleWebSourceUnknownErrorDebugReport_without_arDebugPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_NO_MATCHING_SOURCE.getValue());
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_NO_MATCHING_SOURCE.getValue());
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        Assert.assertEquals(
                TriggerFixture.ValidTriggerParams.ENROLLMENT_ID, report.getEnrollmentId());
        Assert.assertEquals(
                DebugReportApi.Type.TRIGGER_NO_MATCHING_SOURCE.getValue(), report.getType());
        Assert.assertEquals(
                TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN,
                report.getRegistrationOrigin());
    }

    @Test
    public void testScheduleTriggerNoMatchingSourceDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_NO_MATCHING_SOURCE.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerNoMatchingSourceDebugReport_triggerFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_NO_MATCHING_SOURCE.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerNoMatchingSourceDebugReport_noTriggerPermission_dontSchedule()
            throws Exception {
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_NO_MATCHING_SOURCE.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerUnknownErrorDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger, mMeasurementDao, DebugReportApi.Type.TRIGGER_UNKNOWN_ERROR.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerUnknownErrorDebugReport_success() throws Exception {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger, mMeasurementDao, DebugReportApi.Type.TRIGGER_UNKNOWN_ERROR.getValue());
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerUnknownErrorDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger, mMeasurementDao, DebugReportApi.Type.TRIGGER_UNKNOWN_ERROR.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerUnknownErrorDebugReport_triggerFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger, mMeasurementDao, DebugReportApi.Type.TRIGGER_UNKNOWN_ERROR.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerUnknownErrorDebugReport_noTriggerPermission_dontSchedule()
            throws Exception {
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger, mMeasurementDao, DebugReportApi.Type.TRIGGER_UNKNOWN_ERROR.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerNoMatchingFilterDataDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_NO_MATCHING_FILTER_DATA.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerNoMatchingFilterDataDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_NO_MATCHING_FILTER_DATA.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerNoMatchingFilterDataDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_NO_MATCHING_FILTER_DATA.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerNoMatchingFilterDataDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_NO_MATCHING_FILTER_DATA.getValue());
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertEquals(TriggerFixture.ValidTriggerParams.ENROLLMENT_ID, report.getEnrollmentId());
        assertEquals(
                DebugReportApi.Type.TRIGGER_NO_MATCHING_FILTER_DATA.getValue(), report.getType());
        assertEquals(
                TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN,
                report.getRegistrationOrigin());
    }

    @Test
    public void testScheduleTriggerNoMatchingFilterDataDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_NO_MATCHING_FILTER_DATA.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerNoMatchingFilterDataDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mMockFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_NO_MATCHING_FILTER_DATA.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerNoMatchingFilterDataDebugReport_noTriggerPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_NO_MATCHING_FILTER_DATA.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerEventNoMatchingConfigurationDebugReport_triggerNotOpIn_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerEventNoMatchingConfigurationDebugReport_sourceNoAdId_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerEventNoMatchingConfigurationDebugReport_srcNoArDebug_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventNoMatchingConfigurationDebugReport_success()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS.getValue());
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertEquals(TriggerFixture.ValidTriggerParams.ENROLLMENT_ID, report.getEnrollmentId());
        assertEquals(
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS.getValue(),
                report.getType());
        assertEquals(
                TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN,
                report.getRegistrationOrigin());
    }

    @Test
    public void testScheduleTriggerEventNoMatchingConfigDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerEventNoMatchingConfigDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mMockFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerEventNoMatchingConfigDebugReport_noTriggerPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventLowPriorityDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                TRIGGER_DATA,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_LOW_PRIORITY.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventLowPriorityDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                TRIGGER_DATA,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_LOW_PRIORITY.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventLowPriorityDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                TRIGGER_DATA,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_LOW_PRIORITY.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventLowPriorityDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                TRIGGER_DATA,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_LOW_PRIORITY.getValue());
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertEquals(TriggerFixture.ValidTriggerParams.ENROLLMENT_ID, report.getEnrollmentId());
        assertEquals(DebugReportApi.Type.TRIGGER_EVENT_LOW_PRIORITY.getValue(), report.getType());
        assertEquals(
                TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN,
                report.getRegistrationOrigin());
    }

    @Test
    public void testScheduleTriggerEventLowPriorityDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                TRIGGER_DATA,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_LOW_PRIORITY.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventLowPriorityDebugReport_triggerFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                TRIGGER_DATA,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_LOW_PRIORITY.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventLowPriorityDebugReport_noTriggerPermission_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        UnsignedLong triggerData = new UnsignedLong(1L);

        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                triggerData,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_LOW_PRIORITY.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventExcessiveReportsDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                TRIGGER_DATA,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_EXCESSIVE_REPORTS.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventExcessiveReportsDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                TRIGGER_DATA,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_EXCESSIVE_REPORTS.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventExcessiveReportsDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                TRIGGER_DATA,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_EXCESSIVE_REPORTS.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventExcessiveReportsDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                TRIGGER_DATA,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_EXCESSIVE_REPORTS.getValue());
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertEquals(TriggerFixture.ValidTriggerParams.ENROLLMENT_ID, report.getEnrollmentId());
        assertEquals(
                DebugReportApi.Type.TRIGGER_EVENT_EXCESSIVE_REPORTS.getValue(), report.getType());
        assertEquals(
                TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN,
                report.getRegistrationOrigin());
    }

    @Test
    public void testScheduleTriggerEventExcessiveReportsDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                TRIGGER_DATA,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_EXCESSIVE_REPORTS.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerEventExcessiveReportsDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mMockFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                TRIGGER_DATA,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_EXCESSIVE_REPORTS.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerEventExcessiveReportsDebugReport_noTriggerPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        UnsignedLong triggerData = new UnsignedLong(1L);

        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                triggerData,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_EXCESSIVE_REPORTS.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAttriPerSourceDesLimitDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAttriPerSourceDesLimitDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAttriPerSourceDesLimitDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAttriPerSourceDesLimitDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT.getValue());
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertEquals(TriggerFixture.ValidTriggerParams.ENROLLMENT_ID, report.getEnrollmentId());
        assertEquals(
                DebugReportApi.Type.TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT.getValue(),
                report.getType());
        assertEquals(
                TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN,
                report.getRegistrationOrigin());
    }

    @Test
    public void
            testScheduleTriggerAttriPerSourceDesLimitDebugReport_debugFlagDisabled_dontSchedule()
                    throws Exception {
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerAttriPerSourceDesLimitDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mMockFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerAttriPerSourceDesLimitDebugReport_noTriggerPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventWindowPassedDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_PASSED.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventWindowPassedDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_PASSED.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventWindowPassedDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_PASSED.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventWindowPassedDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_PASSED.getValue());
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertEquals(TriggerFixture.ValidTriggerParams.ENROLLMENT_ID, report.getEnrollmentId());
        assertEquals(
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_PASSED.getValue(),
                report.getType());
        assertEquals(
                TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN,
                report.getRegistrationOrigin());
    }

    @Test
    public void testScheduleTriggerEventWindowPassedDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_PASSED.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventWindowPassedDebugReport_triggerFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_PASSED.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventWindowPassedDebugReport_noTriggerPermission_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_PASSED.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerReportingOriginLimitDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_REPORTING_ORIGIN_LIMIT.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerReportingOriginLimitDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_REPORTING_ORIGIN_LIMIT.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerReportingOriginLimitDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_REPORTING_ORIGIN_LIMIT.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerReportingOriginLimitDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_REPORTING_ORIGIN_LIMIT.getValue());
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertEquals(TriggerFixture.ValidTriggerParams.ENROLLMENT_ID, report.getEnrollmentId());
        assertEquals(
                DebugReportApi.Type.TRIGGER_REPORTING_ORIGIN_LIMIT.getValue(), report.getType());
        assertEquals(
                TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN,
                report.getRegistrationOrigin());
    }

    @Test
    public void testScheduleTriggerReportingOriginLimitDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_REPORTING_ORIGIN_LIMIT.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerReportingOriginLimitDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mMockFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_REPORTING_ORIGIN_LIMIT.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerReportingOriginLimitDebugReport_noTriggerPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_REPORTING_ORIGIN_LIMIT.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventNoiseDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NOISE.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventNoiseDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NOISE.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventNoiseDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NOISE.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventNoiseDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NOISE.getValue());
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertEquals(TriggerFixture.ValidTriggerParams.ENROLLMENT_ID, report.getEnrollmentId());
        assertEquals(DebugReportApi.Type.TRIGGER_EVENT_NOISE.getValue(), report.getType());
        assertEquals(
                TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN,
                report.getRegistrationOrigin());
    }

    @Test
    public void testScheduleTriggerEventNoiseDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NOISE.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventNoiseDebugReport_triggerFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NOISE.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventNoiseDebugReport_noTriggerPermission_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NOISE.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventStorageLimitDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_STORAGE_LIMIT.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventStorageLimitDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_STORAGE_LIMIT.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventStorageLimitDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_STORAGE_LIMIT.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventStorageLimitDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_STORAGE_LIMIT.getValue());
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertEquals(TriggerFixture.ValidTriggerParams.ENROLLMENT_ID, report.getEnrollmentId());
        assertEquals(DebugReportApi.Type.TRIGGER_EVENT_STORAGE_LIMIT.getValue(), report.getType());
        assertEquals(
                TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN,
                report.getRegistrationOrigin());
    }

    @Test
    public void testScheduleTriggerEventStorageLimitDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_STORAGE_LIMIT.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventStorageLimitDebugReport_triggerFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_STORAGE_LIMIT.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventStorageLimitDebugReport_noTriggerPermission_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_STORAGE_LIMIT.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateWindowPassedDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateWindowPassedDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateWindowPassedDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateWindowPassedDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED.getValue());

        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertEquals(TriggerFixture.ValidTriggerParams.ENROLLMENT_ID, report.getEnrollmentId());
        assertEquals(
                DebugReportApi.Type.TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED.getValue(),
                report.getType());
        assertEquals(
                TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN,
                report.getRegistrationOrigin());
    }

    @Test
    public void testScheduleTriggerAggregateWindowPassedDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerAggregateWindowPassedDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mMockFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerAggregateWindowPassedDebugReport_noTriggerPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggreInsufficientBudgetDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggreInsufficientBudgetDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggreInsufficientBudgetDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggreInsufficientBudgetDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET.getValue());
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerAggreInsufficientBudgetDebugReport_debugFlagDisabled_dontSchedule()
                    throws Exception {
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerAggreInsufficientBudgetDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mMockFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerAggreInsufficientBudgetDebugReport_noTriggerPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateNoContributionDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_NO_CONTRIBUTIONS.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateNoContributionDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_NO_CONTRIBUTIONS.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateNoContributionDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_NO_CONTRIBUTIONS.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateNoContributionDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_NO_CONTRIBUTIONS.getValue());
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerAggregateNoContributionDebugReport_debugFlagDisabled_dontSchedule()
                    throws Exception {
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_NO_CONTRIBUTIONS.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerAggregateNoContributionDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mMockFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_NO_CONTRIBUTIONS.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerAggregateNoContributionDebugReport_noTriggerPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_NO_CONTRIBUTIONS.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateStorageLimitDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_STORAGE_LIMIT.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateStorageLimitDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_STORAGE_LIMIT.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateStorageLimitDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_STORAGE_LIMIT.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateStorageLimitDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_STORAGE_LIMIT.getValue());
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateStorageLimitDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_STORAGE_LIMIT.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerAggregateStorageLimitDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mMockFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_STORAGE_LIMIT.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerAggregateStorageLimitDebugReport_noTriggerPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_STORAGE_LIMIT.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventWindowNotStartedDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_NOT_STARTED.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventWindowNotStartedDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_NOT_STARTED.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventWindowNotStartedDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_NOT_STARTED.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventWindowNotStartedDebugReport_success() throws Exception {
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_NOT_STARTED.getValue());
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventWindowNotStartedDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(false);
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_NOT_STARTED.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerEventWindowNotStartedDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mMockFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_NOT_STARTED.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerEventWindowNotStartedDebugReport_noTriggerPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_NOT_STARTED.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void scheduleTriggerEventNoMatchingTriggerDataDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_TRIGGER_DATA.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void scheduleTriggerEventNoMatchingTriggerDataDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_TRIGGER_DATA.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void scheduleTriggerEventNoMatchingTriggerDataDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_TRIGGER_DATA.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void scheduleTriggerEventNoMatchingTriggerDataDebugReport_success() throws Exception {
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_TRIGGER_DATA.getValue());
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void
            scheduleTriggerEventNoMatchingTriggerDataDebugReport_debugFlagDisabled_dontSchedule()
                    throws Exception {
        when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(false);
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_TRIGGER_DATA.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            scheduleTriggerEventNoMatchingTriggerDataDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mMockFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
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
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_TRIGGER_DATA.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            scheduleTriggerEventNoMatchingTriggerDataDebugReport_noTriggerPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit= */ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_TRIGGER_DATA.getValue());
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleHeaderErrorDebugReport_success() throws Exception {
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleHeaderErrorReport(
                TEST_REGISTRATION_ORIGIN,
                TEST_REGISTRATION_ORIGIN,
                TEST_REGISTRANT,
                HEADER_NAME_SOURCE_REGISTRATION,
                TEST_ENROLLMENT_ID,
                TEST_HEADER_CONTENT,
                mMeasurementDao);
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void scheduleSourceAttributionScopeDebugReport_maxEventStatesLimit_success()
            throws Exception {
        long baseTime = System.currentTimeMillis();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventTime(baseTime)
                        .setEventReportWindows(
                                "{"
                                        + "\"start_time\": \"0\","
                                        + String.format(
                                                "\"end_times\": [%s, %s]}",
                                                baseTime + TimeUnit.DAYS.toMillis(7),
                                                baseTime + TimeUnit.DAYS.toMillis(30)))
                        .setMaxEventLevelReports(2)
                        .setAttributionScopes(List.of("1", "2"))
                        .setAttributionScopeLimit(5L)
                        .setMaxEventStates(3L)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleAttributionScopeDebugReport(
                source,
                Source.AttributionScopeValidationResult.INVALID_MAX_EVENT_STATES_LIMIT,
                mMeasurementDao);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        assertSourceDebugReportParameters(
                captor.getValue(),
                DebugReportApi.Type.SOURCE_MAX_EVENT_STATES_LIMIT.getValue(),
                SourceFixture.ValidSourceParams.PUBLISHER.toString(),
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS.get(0).toString(),
                Map.of(DebugReportApi.Body.LIMIT, String.valueOf(source.getMaxEventStates())));
    }

    @Test
    public void scheduleSourceAttributionScopeDebugReport_infoGainLimit_success() throws Exception {
        long baseTime = System.currentTimeMillis();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventTime(baseTime)
                        .setEventReportWindows(
                                "{"
                                        + "\"start_time\": \"0\","
                                        + String.format(
                                                "\"end_times\": [%s, %s]}",
                                                baseTime + TimeUnit.DAYS.toMillis(7),
                                                baseTime + TimeUnit.DAYS.toMillis(30)))
                        .setMaxEventLevelReports(2)
                        .setAttributionScopes(List.of("1", "2"))
                        // Attribution scope information gain: 11.622509454683335 > 11.5.
                        .setAttributionScopeLimit(4L)
                        .setMaxEventStates(1000L)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleAttributionScopeDebugReport(
                source,
                Source.AttributionScopeValidationResult.INVALID_INFORMATION_GAIN_LIMIT,
                mMeasurementDao);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());

        assertSourceDebugReportParameters(
                captor.getValue(),
                DebugReportApi.Type.SOURCE_SCOPES_CHANNEL_CAPACITY_LIMIT.getValue(),
                SourceFixture.ValidSourceParams.PUBLISHER.toString(),
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS.get(0).toString(),
                Map.of(DebugReportApi.Body.LIMIT, String.valueOf(11.5f)));
    }

    private static void assertSourceDebugReportParameters(
            DebugReport actualReport,
            String expectedReportType,
            String expectedSourcePublisher,
            String expectedSerializedDestinations,
            Map<String, String> expectedAdditionalParams)
            throws JSONException {
        JSONObject actualReportBody = actualReport.getBody();
        assertEquals(SourceFixture.ValidSourceParams.ENROLLMENT_ID, actualReport.getEnrollmentId());
        assertEquals(expectedReportType, actualReport.getType());
        assertEquals(
                SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN,
                actualReport.getRegistrationOrigin());
        assertEquals(
                SOURCE_EVENT_ID.toString(),
                actualReportBody.getString(DebugReportApi.Body.SOURCE_EVENT_ID));
        assertEquals(
                expectedSerializedDestinations,
                actualReportBody.getString(DebugReportApi.Body.ATTRIBUTION_DESTINATION));
        assertEquals(
                expectedSourcePublisher,
                actualReportBody.getString(DebugReportApi.Body.SOURCE_SITE));
        for (Map.Entry<String, String> entry : expectedAdditionalParams.entrySet()) {
            assertEquals(
                    entry.getValue(),
                    actualReportBody.isNull(entry.getKey())
                            ? null
                            : actualReportBody.getString(entry.getKey()));
        }
    }
}
