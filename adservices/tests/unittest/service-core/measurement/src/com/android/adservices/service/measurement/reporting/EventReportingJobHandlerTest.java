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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.WebUtil;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.data.measurement.ITransaction;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.KeyValueData;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.TriggerFixture;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.MeasurementReportsStats;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.android.libraries.mobiledatadownload.internal.AndroidTimeSource;
import com.google.common.truth.Truth;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Unit test for {@link EventReportingJobHandler} */
@RunWith(MockitoJUnitRunner.class)
public class EventReportingJobHandlerTest {
    private static final UnsignedLong SOURCE_DEBUG_KEY = new UnsignedLong(237865L);
    private static final UnsignedLong TRIGGER_DEBUG_KEY = new UnsignedLong(928762L);
    private static final UnsignedLong SOURCE_EVENT_ID = new UnsignedLong(1234L);

    private static final Uri REPORTING_ORIGIN = WebUtil.validUri("https://subdomain.example.test");
    private static final List<Uri> ATTRIBUTION_DESTINATIONS = List.of(
            Uri.parse("https://destination.test"));
    private static final Uri DEFAULT_WEB_DESTINATION =
            WebUtil.validUri("https://def-web-destination.test");
    private static final Uri ALT_WEB_DESTINATION =
            WebUtil.validUri("https://alt-web-destination.test");
    private static final Uri APP_DESTINATION = Uri.parse("android-app://com.app_destination.test");
    private static final String SOURCE_REGISTRANT = "android-app://com.registrant";
    private static final String ENROLLMENT_ID = "enrollment-id";
    private static final String SOURCE_ID = "source-id";
    private static final String TRIGGER_ID = "trigger-id";
    private static final String EVENT_REPORT_ID = "eventReportId";
    protected static Context sContext;
    DatastoreManager mDatastoreManager;

    @Mock IMeasurementDao mMeasurementDao;

    @Mock ITransaction mTransaction;

    @Mock private Flags mMockFlags;

    @Mock Flags mFlags;
    @Mock AdServicesLogger mLogger;
    @Mock AdServicesErrorLogger mErrorLogger;
    @Mock private PackageManager mPackageManager;
    AndroidTimeSource mTimeSource;

    EventReportingJobHandler mEventReportingJobHandler;
    EventReportingJobHandler mSpyEventReportingJobHandler;
    EventReportingJobHandler mSpyDebugEventReportingJobHandler;

    @Rule
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(FlagsFactory.class)
                    .spyStatic(ErrorLogUtil.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    class FakeDatasoreManager extends DatastoreManager {
        FakeDatasoreManager() {
            super(mErrorLogger);
        }

        @Override
        public ITransaction createNewTransaction() {
            return mTransaction;
        }

        @Override
        public IMeasurementDao getMeasurementDao() {
            return mMeasurementDao;
        }

        @Override
        protected int getDataStoreVersion() {
            return 0;
        }
    }

    @Before
    public void setUp() throws DatastoreException {
        mMockFlags = mock(Flags.class);
        sContext = spy(ApplicationProvider.getApplicationContext());
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        when(mMockFlags.getMeasurementEnableAppPackageNameLogging()).thenReturn(true);
        mDatastoreManager = new FakeDatasoreManager();
        when(mMeasurementDao.getSourceRegistrant(any())).thenReturn(SOURCE_REGISTRANT);
        doReturn(false).when(mFlags).getMeasurementEnableReportingJobsThrowJsonException();
        doReturn(false).when(mFlags).getMeasurementEnableReportingJobsThrowCryptoException();
        doReturn(false).when(mFlags).getMeasurementEnableReportingJobsThrowUnaccountedException();
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(anyInt(), anyInt()));
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));

        mTimeSource = new AndroidTimeSource();

        mEventReportingJobHandler =
                new EventReportingJobHandler(
                        mDatastoreManager,
                        mFlags,
                        mLogger,
                        ReportingStatus.ReportType.EVENT,
                        ReportingStatus.UploadMethod.UNKNOWN,
                        sContext,
                        mTimeSource);
        mSpyEventReportingJobHandler = Mockito.spy(mEventReportingJobHandler);
        mSpyDebugEventReportingJobHandler =
                Mockito.spy(
                        new EventReportingJobHandler(
                                        mDatastoreManager,
                                        mFlags,
                                        mLogger,
                                        ReportingStatus.ReportType.EVENT,
                                        ReportingStatus.UploadMethod.UNKNOWN,
                                        sContext,
                                        mTimeSource)
                                .setIsDebugInstance(true));
    }

    @Test
    public void testSendReportForPendingReportSuccess()
            throws DatastoreException, IOException, JSONException {
        EventReport eventReport =
                new EventReport.Builder()
                        .setId(EVENT_REPORT_ID)
                        .setSourceEventId(SOURCE_EVENT_ID)
                        .setAttributionDestinations(ATTRIBUTION_DESTINATIONS)
                        .setStatus(EventReport.Status.PENDING)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .build();
        JSONObject eventReportPayload = createEventReportPayloadFromReport(eventReport);

        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        doReturn(eventReportPayload)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(Mockito.any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(eventReport.getId(), AggregateReport.Status.DELIVERED);

        ReportingStatus reportingStatus = new ReportingStatus();
        mSpyEventReportingJobHandler.performReport(eventReport.getId(), reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, reportingStatus.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.UNKNOWN, reportingStatus.getFailureStatus());

        verify(mMeasurementDao, times(1)).markEventReportStatus(any(), anyInt());
        verify(mSpyEventReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void testSendReportForPendingDebugReportSuccess()
            throws DatastoreException, IOException, JSONException {
        EventReport eventReport =
                new EventReport.Builder()
                        .setId(EVENT_REPORT_ID)
                        .setSourceEventId(SOURCE_EVENT_ID)
                        .setAttributionDestinations(ATTRIBUTION_DESTINATIONS)
                        .setStatus(EventReport.Status.PENDING)
                        .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .build();
        JSONObject eventReportPayload = createEventReportPayloadFromReport(eventReport);

        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyDebugEventReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        doReturn(eventReportPayload)
                .when(mSpyDebugEventReportingJobHandler)
                .createReportJsonPayload(Mockito.any());

        doNothing().when(mMeasurementDao).markEventDebugReportDelivered(eventReport.getId());

        ReportingStatus reportingStatus = new ReportingStatus();
        mSpyDebugEventReportingJobHandler.performReport(eventReport.getId(), reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, reportingStatus.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.UNKNOWN, reportingStatus.getFailureStatus());

        verify(mMeasurementDao, times(1)).markEventDebugReportDelivered(any());
        verify(mSpyDebugEventReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void testSendReportSuccess_reinstallAttributionEnabled_persistsAppReportHistory()
            throws DatastoreException, IOException, JSONException {
        when(mFlags.getMeasurementEnableReinstallReattribution()).thenReturn(true);
        Long reportTime = 10L;
        EventReport eventReport =
                new EventReport.Builder()
                        .setId(EVENT_REPORT_ID)
                        .setSourceEventId(SOURCE_EVENT_ID)
                        .setStatus(EventReport.Status.PENDING)
                        .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .setReportTime(reportTime)
                        .setSourceId(SOURCE_ID)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setAttributionDestinations(
                                List.of(
                                        DEFAULT_WEB_DESTINATION,
                                        ALT_WEB_DESTINATION,
                                        APP_DESTINATION))
                        .build();
        JSONObject eventReportPayload = createEventReportPayloadFromReport(eventReport);

        Pair<List<Uri>, List<Uri>> destinations =
                new Pair<>(
                        List.of(APP_DESTINATION),
                        List.of(DEFAULT_WEB_DESTINATION, ALT_WEB_DESTINATION));
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId(SOURCE_ID)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .build();
        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);
        when(mMeasurementDao.getSourceDestinations(eventReport.getSourceId()))
                .thenReturn(destinations);
        when(mMeasurementDao.getSource(eventReport.getSourceId())).thenReturn(source);
        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        doReturn(eventReportPayload)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(Mockito.any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(eventReport.getId(), AggregateReport.Status.DELIVERED);

        ReportingStatus reportingStatus = new ReportingStatus();
        mSpyEventReportingJobHandler.performReport(eventReport.getId(), reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, reportingStatus.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.UNKNOWN, reportingStatus.getFailureStatus());

        verify(mMeasurementDao, times(1)).markEventReportStatus(any(), anyInt());
        verify(mSpyEventReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));

        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
        verify(mMeasurementDao, times(1))
                .insertOrUpdateAppReportHistory(
                        eq(APP_DESTINATION), eq(REPORTING_ORIGIN), eq(reportTime));
    }

    @Test
    public void testSendReportSuccess_reinstallAttributionDisabled_doesNotPersistsAppReportHistory()
            throws DatastoreException, IOException, JSONException {
        when(mFlags.getMeasurementEnableReinstallReattribution()).thenReturn(false);
        EventReport eventReport =
                new EventReport.Builder()
                        .setId(EVENT_REPORT_ID)
                        .setSourceEventId(SOURCE_EVENT_ID)
                        .setStatus(EventReport.Status.PENDING)
                        .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .setSourceId(SOURCE_ID)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setAttributionDestinations(
                                List.of(
                                        DEFAULT_WEB_DESTINATION,
                                        ALT_WEB_DESTINATION,
                                        APP_DESTINATION))
                        .build();
        JSONObject eventReportPayload = createEventReportPayloadFromReport(eventReport);

        Pair<List<Uri>, List<Uri>> destinations =
                new Pair<>(
                        List.of(APP_DESTINATION),
                        List.of(DEFAULT_WEB_DESTINATION, ALT_WEB_DESTINATION));
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId(SOURCE_ID)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .build();
        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);
        when(mMeasurementDao.getSourceDestinations(eventReport.getSourceId()))
                .thenReturn(destinations);
        when(mMeasurementDao.getSource(eventReport.getSourceId())).thenReturn(source);
        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        doReturn(eventReportPayload)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(Mockito.any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(eventReport.getId(), AggregateReport.Status.DELIVERED);

        ReportingStatus reportingStatus = new ReportingStatus();
        mSpyEventReportingJobHandler.performReport(eventReport.getId(), reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, reportingStatus.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.UNKNOWN, reportingStatus.getFailureStatus());

        verify(mMeasurementDao, times(1)).markEventReportStatus(any(), anyInt());
        verify(mSpyEventReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));

        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
        verify(mMeasurementDao, never()).insertOrUpdateAppReportHistory(any(), any(), anyLong());
    }

    @Test
    public void testSendReportForPendingReportSuccess_coarseSource_doesNotPersistAppReportHistory()
            throws DatastoreException, IOException, JSONException {
        when(mFlags.getMeasurementEnableReinstallReattribution()).thenReturn(true);
        EventReport eventReport =
                new EventReport.Builder()
                        .setId(EVENT_REPORT_ID)
                        .setSourceEventId(SOURCE_EVENT_ID)
                        .setStatus(EventReport.Status.PENDING)
                        .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .setSourceId(SOURCE_ID)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setAttributionDestinations(
                                List.of(
                                        DEFAULT_WEB_DESTINATION,
                                        ALT_WEB_DESTINATION,
                                        APP_DESTINATION))
                        .build();
        JSONObject eventReportPayload = createEventReportPayloadFromReport(eventReport);

        Pair<List<Uri>, List<Uri>> destinations =
                new Pair<>(
                        List.of(APP_DESTINATION),
                        List.of(DEFAULT_WEB_DESTINATION, ALT_WEB_DESTINATION));
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId(SOURCE_ID)
                        .setCoarseEventReportDestinations(true)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .build();
        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);
        when(mMeasurementDao.getSourceDestinations(eventReport.getSourceId()))
                .thenReturn(destinations);
        when(mMeasurementDao.getSource(eventReport.getSourceId())).thenReturn(source);
        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        doReturn(eventReportPayload)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(Mockito.any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(eventReport.getId(), AggregateReport.Status.DELIVERED);

        ReportingStatus reportingStatus = new ReportingStatus();
        mSpyEventReportingJobHandler.performReport(eventReport.getId(), reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, reportingStatus.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.UNKNOWN, reportingStatus.getFailureStatus());

        verify(mMeasurementDao, times(1)).markEventReportStatus(any(), anyInt());
        verify(mSpyEventReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));

        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
        verify(mMeasurementDao, never()).insertOrUpdateAppReportHistory(any(), any(), anyLong());
    }

    @Test
    public void testSendReportFailed_uninstallEnabled_deleteReport()
            throws DatastoreException, IOException, JSONException {
        when(mFlags.getMeasurementEnableMinReportLifespanForUninstall()).thenReturn(true);
        when(mFlags.getMeasurementMinReportLifespanForUninstallSeconds())
                .thenReturn(TimeUnit.DAYS.toSeconds(1));

        long currentTime = System.currentTimeMillis();

        EventReport eventReport =
                new EventReport.Builder()
                        .setId("eventReportId")
                        .setSourceEventId(new UnsignedLong(1234L))
                        .setAttributionDestinations(ATTRIBUTION_DESTINATIONS)
                        .setStatus(EventReport.Status.PENDING)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .setTriggerTime(currentTime - TimeUnit.HOURS.toMillis(25))
                        .build();

        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(eventReport.getId(), AggregateReport.Status.DELIVERED);

        ReportingStatus status = new ReportingStatus();

        mSpyEventReportingJobHandler.performReport(eventReport.getId(), status);
        Truth.assertThat(status.getUploadStatus()).isEqualTo(ReportingStatus.UploadStatus.FAILURE);
        Truth.assertThat(status.getFailureStatus())
                .isEqualTo(ReportingStatus.FailureStatus.APP_UNINSTALLED_OR_OUTSIDE_WINDOW);
        verify(mMeasurementDao, times(1)).deleteEventReport(eventReport);
    }

    @Test
    public void testSendReportSuccess_uninstallEnabled_sendReport()
            throws DatastoreException, IOException, JSONException {
        when(mFlags.getMeasurementEnableMinReportLifespanForUninstall()).thenReturn(true);
        when(mFlags.getMeasurementMinReportLifespanForUninstallSeconds())
                .thenReturn(TimeUnit.DAYS.toSeconds(1));

        long currentTime = System.currentTimeMillis();

        EventReport eventReport =
                new EventReport.Builder()
                        .setId("eventReportId")
                        .setSourceEventId(new UnsignedLong(1234L))
                        .setAttributionDestinations(ATTRIBUTION_DESTINATIONS)
                        .setStatus(EventReport.Status.PENDING)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .setTriggerTime(currentTime - TimeUnit.HOURS.toMillis(23))
                        .build();

        JSONObject eventReportPayload =
                new EventReportPayload.Builder()
                        .setReportId(eventReport.getId())
                        .setSourceEventId(eventReport.getSourceEventId())
                        .setAttributionDestination(eventReport.getAttributionDestinations())
                        .build()
                        .toJson();

        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);

        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        doReturn(eventReportPayload)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(Mockito.any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(eventReport.getId(), AggregateReport.Status.DELIVERED);
        ReportingStatus status = new ReportingStatus();

        mSpyEventReportingJobHandler.performReport(eventReport.getId(), status);

        Truth.assertThat(status.getUploadStatus()).isEqualTo(ReportingStatus.UploadStatus.SUCCESS);
        verify(mMeasurementDao, times(0)).deleteEventReport(eventReport);
    }

    @Test
    public void testSendReportSuccess_uninstallEnabled_hasApps_sendReport()
            throws DatastoreException,
                    IOException,
                    JSONException,
                    PackageManager.NameNotFoundException {
        when(mFlags.getMeasurementEnableMinReportLifespanForUninstall()).thenReturn(true);
        when(mFlags.getMeasurementMinReportLifespanForUninstallSeconds())
                .thenReturn(TimeUnit.DAYS.toSeconds(1));

        long currentTime = System.currentTimeMillis();
        Uri publisher = Uri.parse("https://publisher.test");

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId(SOURCE_ID)
                        .setCoarseEventReportDestinations(true)
                        .setPublisher(publisher)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .build();

        EventReport eventReport =
                new EventReport.Builder()
                        .setId("eventReportId")
                        .setSourceEventId(new UnsignedLong(1234L))
                        .setAttributionDestinations(ATTRIBUTION_DESTINATIONS)
                        .setStatus(EventReport.Status.PENDING)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .setSourceId(SOURCE_ID)
                        .setTriggerTime(currentTime - TimeUnit.HOURS.toMillis(25))
                        .build();

        JSONObject eventReportPayload =
                new EventReportPayload.Builder()
                        .setReportId(eventReport.getId())
                        .setSourceEventId(eventReport.getSourceEventId())
                        .setAttributionDestination(eventReport.getAttributionDestinations())
                        .build()
                        .toJson();

        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);

        ApplicationInfo applicationInfo1 = new ApplicationInfo();
        applicationInfo1.packageName = ATTRIBUTION_DESTINATIONS.get(0).getHost();
        ApplicationInfo applicationInfo2 = new ApplicationInfo();
        applicationInfo2.packageName = publisher.getHost();

        when(sContext.getPackageManager()).thenReturn(mPackageManager);

        when(mMeasurementDao.getSource(SOURCE_ID)).thenReturn(source);

        when(mPackageManager.getApplicationInfo(ATTRIBUTION_DESTINATIONS.get(0).getHost(), 0))
                .thenReturn(applicationInfo1);
        when(mPackageManager.getApplicationInfo(publisher.getHost(), 0))
                .thenReturn(applicationInfo2);

        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        doReturn(eventReportPayload)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(Mockito.any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(eventReport.getId(), AggregateReport.Status.DELIVERED);
        ReportingStatus status = new ReportingStatus();

        mSpyEventReportingJobHandler.performReport(eventReport.getId(), status);

        Truth.assertThat(status.getUploadStatus()).isEqualTo(ReportingStatus.UploadStatus.SUCCESS);
        verify(mMeasurementDao, times(0)).deleteEventReport(eventReport);
    }

    @Test
    public void testSendReportForPendingReportSuccessSingleTriggerDebugKey()
            throws DatastoreException, IOException, JSONException {
        EventReport eventReport =
                new EventReport.Builder()
                        .setId(EVENT_REPORT_ID)
                        .setSourceEventId(SOURCE_EVENT_ID)
                        .setAttributionDestinations(ATTRIBUTION_DESTINATIONS)
                        .setStatus(EventReport.Status.PENDING)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .build();
        JSONObject eventReportPayload = createEventReportPayloadFromReport(eventReport);

        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        doReturn(eventReportPayload)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(Mockito.any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(eventReport.getId(), AggregateReport.Status.DELIVERED);

        ReportingStatus reportingStatus = new ReportingStatus();
        mSpyEventReportingJobHandler.performReport(eventReport.getId(), reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, reportingStatus.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.UNKNOWN, reportingStatus.getFailureStatus());

        verify(mMeasurementDao, times(1)).markEventReportStatus(any(), anyInt());
        verify(mSpyEventReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void testSendReportForPendingReportSuccessSingleSourceDebugKey()
            throws DatastoreException, IOException, JSONException {
        EventReport eventReport =
                new EventReport.Builder()
                        .setId(EVENT_REPORT_ID)
                        .setSourceEventId(SOURCE_EVENT_ID)
                        .setAttributionDestinations(ATTRIBUTION_DESTINATIONS)
                        .setStatus(EventReport.Status.PENDING)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .build();
        JSONObject eventReportPayload = createEventReportPayloadFromReport(eventReport);

        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        doReturn(eventReportPayload)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(Mockito.any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(eventReport.getId(), AggregateReport.Status.DELIVERED);

        ReportingStatus reportingStatus = new ReportingStatus();
        mSpyEventReportingJobHandler.performReport(eventReport.getId(), reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, reportingStatus.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.UNKNOWN, reportingStatus.getFailureStatus());

        verify(mMeasurementDao, times(1)).markEventReportStatus(any(), anyInt());
        verify(mSpyEventReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void testSendReportForPendingReportSuccessWithNullDebugKeys()
            throws DatastoreException, IOException, JSONException {
        EventReport eventReport =
                new EventReport.Builder()
                        .setId(EVENT_REPORT_ID)
                        .setSourceEventId(SOURCE_EVENT_ID)
                        .setAttributionDestinations(ATTRIBUTION_DESTINATIONS)
                        .setStatus(EventReport.Status.PENDING)
                        .setSourceDebugKey(null)
                        .setTriggerDebugKey(null)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .build();
        JSONObject eventReportPayload = createEventReportPayloadFromReport(eventReport);

        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        doReturn(eventReportPayload)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(Mockito.any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(eventReport.getId(), AggregateReport.Status.DELIVERED);
        ReportingStatus reportingStatus = new ReportingStatus();
        mSpyEventReportingJobHandler.performReport(eventReport.getId(), reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, reportingStatus.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.UNKNOWN, reportingStatus.getFailureStatus());

        verify(mMeasurementDao, times(1)).markEventReportStatus(any(), anyInt());
        verify(mSpyEventReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void testSendReportForPendingReportFailure()
            throws DatastoreException, IOException, JSONException {
        EventReport eventReport = createSampleEventReport();
        JSONObject eventReportPayload = createEventReportPayloadFromReport(eventReport);

        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);
        doReturn(HttpURLConnection.HTTP_BAD_REQUEST)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        doReturn(eventReportPayload)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(Mockito.any());
        ReportingStatus reportingStatus = new ReportingStatus();

        mSpyEventReportingJobHandler.performReport(eventReport.getId(), reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.FAILURE, reportingStatus.getUploadStatus());
        assertEquals(
                ReportingStatus.FailureStatus.UNSUCCESSFUL_HTTP_RESPONSE_CODE,
                reportingStatus.getFailureStatus());

        verify(mMeasurementDao, never()).markEventReportStatus(any(), anyInt());
        verify(mSpyEventReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_throwsIOException_logsReportingStatus()
            throws DatastoreException, IOException, JSONException {
        EventReport eventReport = createSampleEventReport();
        JSONObject eventReportPayload = createEventReportPayloadFromReport(eventReport);

        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);
        doThrow(new IOException())
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        doReturn(eventReportPayload)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(Mockito.any());
        ReportingStatus reportingStatus = new ReportingStatus();

        mSpyEventReportingJobHandler.performReport(eventReport.getId(), reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.FAILURE, reportingStatus.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.NETWORK, reportingStatus.getFailureStatus());

        verify(mMeasurementDao, never()).markEventReportStatus(any(), anyInt());
        verify(mSpyEventReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_throwsJsonDisabledToThrow_logsAndSwallowsException()
            throws DatastoreException, IOException, JSONException {
        EventReport eventReport = createSampleEventReport();

        doReturn(false).when(mFlags).getMeasurementEnableReportDeletionOnUnrecoverableException();
        doReturn(false).when(mFlags).getMeasurementEnableReportingJobsThrowJsonException();
        doReturn(eventReport).when(mMeasurementDao).getEventReport(eventReport.getId());
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        doThrow(new JSONException("cause message"))
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(Mockito.any());
        ReportingStatus reportingStatus = new ReportingStatus();

        mSpyEventReportingJobHandler.performReport(eventReport.getId(), reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.FAILURE, reportingStatus.getUploadStatus());
        assertEquals(
                ReportingStatus.FailureStatus.SERIALIZATION_ERROR,
                reportingStatus.getFailureStatus());
        verify(mMeasurementDao, never()).markEventReportStatus(anyString(), anyInt());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_throwsJsonEnabledToThrow_marksReportDeletedAndRethrowsException()
            throws DatastoreException, IOException, JSONException {
        EventReport eventReport = createSampleEventReport();

        doReturn(true).when(mFlags).getMeasurementEnableReportingJobsThrowJsonException();
        doReturn(true).when(mFlags).getMeasurementEnableReportDeletionOnUnrecoverableException();
        doReturn(1.0f).when(mFlags).getMeasurementThrowUnknownExceptionSamplingRate();
        doReturn(eventReport).when(mMeasurementDao).getEventReport(eventReport.getId());
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        doThrow(new JSONException("cause message"))
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(Mockito.any());

        try {
            mSpyEventReportingJobHandler.performReport(eventReport.getId(), new ReportingStatus());
            fail();
        } catch (IllegalStateException e) {
            assertEquals(JSONException.class, e.getCause().getClass());
            assertEquals("cause message", e.getCause().getMessage());
        }

        verify(mMeasurementDao)
                .markEventReportStatus(eventReport.getId(), EventReport.Status.MARKED_TO_DELETE);
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performReport_throwsJsonEnabledToThrowNoSampling_logsAndSwallowsException()
            throws DatastoreException, IOException, JSONException {
        EventReport eventReport = createSampleEventReport();

        doReturn(true).when(mFlags).getMeasurementEnableReportingJobsThrowJsonException();
        doReturn(true).when(mFlags).getMeasurementEnableReportDeletionOnUnrecoverableException();
        doReturn(0.0f).when(mFlags).getMeasurementThrowUnknownExceptionSamplingRate();
        doReturn(eventReport).when(mMeasurementDao).getEventReport(eventReport.getId());
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        doThrow(new JSONException("cause message"))
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(Mockito.any());
        ReportingStatus reportingStatus = new ReportingStatus();

        mSpyEventReportingJobHandler.performReport(eventReport.getId(), reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.FAILURE, reportingStatus.getUploadStatus());
        assertEquals(
                ReportingStatus.FailureStatus.SERIALIZATION_ERROR,
                reportingStatus.getFailureStatus());
        verify(mMeasurementDao)
                .markEventReportStatus(
                        eq(eventReport.getId()), eq(EventReport.Status.MARKED_TO_DELETE));
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performReport_throwsUnknownExceptionDisabledToThrow_logsAndSwallowsException()
            throws DatastoreException, IOException, JSONException {
        EventReport eventReport = createSampleEventReport();
        JSONObject eventReportPayload = createEventReportPayloadFromReport(eventReport);

        doReturn(false).when(mFlags).getMeasurementEnableReportingJobsThrowUnaccountedException();
        doReturn(eventReport).when(mMeasurementDao).getEventReport(eventReport.getId());
        doThrow(new RuntimeException("unknown exception"))
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        doReturn(eventReportPayload)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(Mockito.any());
        ReportingStatus reportingStatus = new ReportingStatus();

        mSpyEventReportingJobHandler.performReport(eventReport.getId(), reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.FAILURE, reportingStatus.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.UNKNOWN, reportingStatus.getFailureStatus());
        verify(mMeasurementDao, never()).markEventReportStatus(anyString(), anyInt());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_throwsUnknownExceptionEnabledToThrow_rethrowsException()
            throws DatastoreException, IOException, JSONException {
        EventReport eventReport = createSampleEventReport();

        doReturn(true).when(mFlags).getMeasurementEnableReportingJobsThrowUnaccountedException();
        doReturn(eventReport).when(mMeasurementDao).getEventReport(eventReport.getId());
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        doThrow(new RuntimeException("unknown exception"))
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(Mockito.any());
        doReturn(1.0f).when(mFlags).getMeasurementThrowUnknownExceptionSamplingRate();

        try {
            mSpyEventReportingJobHandler.performReport(eventReport.getId(), new ReportingStatus());
            fail();
        } catch (RuntimeException e) {
            assertEquals("unknown exception", e.getMessage());
        }

        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_enabledToThrowNoSampling_logsAndSwallowsException()
            throws DatastoreException, IOException, JSONException {
        EventReport eventReport = createSampleEventReport();

        doReturn(true).when(mFlags).getMeasurementEnableReportingJobsThrowUnaccountedException();
        doReturn(eventReport).when(mMeasurementDao).getEventReport(eventReport.getId());
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        doThrow(new RuntimeException("unknown exception"))
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(Mockito.any());
        doReturn(0.0f).when(mFlags).getMeasurementThrowUnknownExceptionSamplingRate();
        ReportingStatus reportingStatus = new ReportingStatus();

        mSpyEventReportingJobHandler.performReport(eventReport.getId(), reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.FAILURE, reportingStatus.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.UNKNOWN, reportingStatus.getFailureStatus());
        verify(mMeasurementDao, never()).markEventReportStatus(anyString(), anyInt());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void testSendReportForAlreadyDeliveredReport() throws DatastoreException, IOException {
        EventReport eventReport =
                new EventReport.Builder()
                        .setId(EVENT_REPORT_ID)
                        .setStatus(EventReport.Status.DELIVERED)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .build();

        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);
        ReportingStatus reportingStatus = new ReportingStatus();

        mSpyEventReportingJobHandler.performReport(eventReport.getId(), reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.FAILURE, reportingStatus.getUploadStatus());
        assertEquals(
                ReportingStatus.FailureStatus.REPORT_NOT_PENDING,
                reportingStatus.getFailureStatus());

        verify(mMeasurementDao, never()).markEventReportStatus(any(), anyInt());
        verify(mSpyEventReportingJobHandler, times(0))
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void testPerformScheduledPendingReportsForMultipleReports()
            throws DatastoreException, IOException, JSONException {
        EventReport eventReport1 =
                new EventReport.Builder()
                        .setId("eventReport1")
                        .setSourceEventId(SOURCE_EVENT_ID)
                        .setAttributionDestinations(ATTRIBUTION_DESTINATIONS)
                        .setStatus(EventReport.Status.PENDING)
                        .setReportTime(1000L)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .build();
        JSONObject eventReportPayload1 = createEventReportPayloadFromReport(eventReport1);
        EventReport eventReport2 =
                new EventReport.Builder()
                        .setId("eventReport2")
                        .setSourceEventId(new UnsignedLong(12345L))
                        .setAttributionDestinations(ATTRIBUTION_DESTINATIONS)
                        .setStatus(EventReport.Status.PENDING)
                        .setReportTime(1100L)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .build();
        JSONObject eventReportPayload2 = createEventReportPayloadFromReport(eventReport2);

        when(mMeasurementDao.getPendingEventReportIdsInWindow(1000, 1100))
                .thenReturn(List.of(eventReport1.getId(), eventReport2.getId()));
        when(mMeasurementDao.getEventReport(eventReport1.getId())).thenReturn(eventReport1);
        when(mMeasurementDao.getEventReport(eventReport2.getId())).thenReturn(eventReport2);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        doReturn(eventReportPayload1)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(eventReport1);
        doReturn(eventReportPayload2)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(eventReport2);

        assertTrue(mSpyEventReportingJobHandler.performScheduledPendingReportsInWindow(1000, 1100));

        verify(mMeasurementDao, times(2)).markEventReportStatus(any(), anyInt());
        verify(mSpyEventReportingJobHandler, times(2))
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        verify(mTransaction, times(5)).begin();
        verify(mTransaction, times(5)).end();
    }

    @Test
    public void testPerformScheduledPendingReports_ThreadInterrupted()
            throws JSONException, DatastoreException, IOException {
        EventReport eventReport1 =
                new EventReport.Builder()
                        .setId("eventReport1")
                        .setSourceEventId(SOURCE_EVENT_ID)
                        .setAttributionDestinations(ATTRIBUTION_DESTINATIONS)
                        .setStatus(EventReport.Status.PENDING)
                        .setReportTime(1000L)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .build();
        JSONObject eventReportPayload1 = createEventReportPayloadFromReport(eventReport1);
        EventReport eventReport2 =
                new EventReport.Builder()
                        .setId("eventReport2")
                        .setSourceEventId(new UnsignedLong(12345L))
                        .setAttributionDestinations(ATTRIBUTION_DESTINATIONS)
                        .setStatus(EventReport.Status.PENDING)
                        .setReportTime(1100L)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .build();
        JSONObject eventReportPayload2 = createEventReportPayloadFromReport(eventReport2);

        when(mMeasurementDao.getPendingEventReportIdsInWindow(1000, 1100))
                .thenReturn(List.of(eventReport1.getId(), eventReport2.getId()));
        when(mMeasurementDao.getEventReport(eventReport1.getId())).thenReturn(eventReport1);
        when(mMeasurementDao.getEventReport(eventReport2.getId())).thenReturn(eventReport2);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        doReturn(eventReportPayload1)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(eventReport1);
        doReturn(eventReportPayload2)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(eventReport2);

        Thread.currentThread().interrupt();

        assertTrue(mSpyEventReportingJobHandler.performScheduledPendingReportsInWindow(1000, 1100));

        // 1 transaction for initial retrieval of pending report ids.
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();

        // 0 reports processed, since the thread exits early.
        verify(mMeasurementDao, times(0)).markEventReportStatus(any(), anyInt());
        verify(mSpyEventReportingJobHandler, times(0))
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
    }

    @Test
    public void testPerformScheduledPendingReports_LogZeroRetryCount()
            throws DatastoreException, IOException, JSONException {
        EventReport eventReport1 =
                new EventReport.Builder()
                        .setId("eventReport1")
                        .setSourceEventId(SOURCE_EVENT_ID)
                        .setAttributionDestinations(ATTRIBUTION_DESTINATIONS)
                        .setStatus(EventReport.Status.PENDING)
                        .setReportTime(1000L)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .build();
        JSONObject eventReportPayload1 = createEventReportPayloadFromReport(eventReport1);

        when(mMeasurementDao.getPendingEventReportIdsInWindow(1000, 1100))
                .thenReturn(List.of(eventReport1.getId()));
        when(mMeasurementDao.getEventReport(eventReport1.getId())).thenReturn(eventReport1);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        doReturn(eventReportPayload1)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(eventReport1);

        assertTrue(mSpyEventReportingJobHandler.performScheduledPendingReportsInWindow(1000, 1100));
        ArgumentCaptor<MeasurementReportsStats> statusArg =
                ArgumentCaptor.forClass(MeasurementReportsStats.class);
        verify(mLogger).logMeasurementReports(statusArg.capture(), eq(ENROLLMENT_ID));
        MeasurementReportsStats measurementReportsStats = statusArg.getValue();
        assertEquals(
                measurementReportsStats.getType(), ReportingStatus.ReportType.EVENT.getValue());
        assertEquals(
                measurementReportsStats.getResultCode(),
                ReportingStatus.UploadStatus.SUCCESS.getValue());
        assertEquals(
                measurementReportsStats.getFailureType(),
                ReportingStatus.FailureStatus.UNKNOWN.getValue());
        verify(mMeasurementDao, never()).incrementAndGetReportingRetryCount(any(), any());
    }

    @Test
    public void testPerformScheduledPendingReports_LogReportNotFound() throws DatastoreException {
        EventReport eventReport1 =
                new EventReport.Builder()
                        .setId(EVENT_REPORT_ID)
                        .setSourceEventId(SOURCE_EVENT_ID)
                        .setAttributionDestinations(ATTRIBUTION_DESTINATIONS)
                        .setStatus(EventReport.Status.PENDING)
                        .setReportTime(1000L)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .build();

        when(mMeasurementDao.getPendingEventReportIdsInWindow(1000, 1100))
                .thenReturn(List.of(eventReport1.getId()));
        when(mMeasurementDao.getEventReport(eventReport1.getId())).thenReturn(null);

        assertTrue(mSpyEventReportingJobHandler.performScheduledPendingReportsInWindow(1000, 1100));
        ArgumentCaptor<MeasurementReportsStats> statusArg =
                ArgumentCaptor.forClass(MeasurementReportsStats.class);
        verify(mLogger).logMeasurementReports(statusArg.capture(), eq(null));
        MeasurementReportsStats measurementReportsStats = statusArg.getValue();
        assertEquals(
                measurementReportsStats.getType(), ReportingStatus.ReportType.EVENT.getValue());
        assertEquals(
                measurementReportsStats.getResultCode(),
                ReportingStatus.UploadStatus.FAILURE.getValue());
        assertEquals(
                measurementReportsStats.getFailureType(),
                ReportingStatus.FailureStatus.REPORT_NOT_FOUND.getValue());
        verify(mMeasurementDao)
                .incrementAndGetReportingRetryCount(
                        eventReport1.getId(), KeyValueData.DataType.EVENT_REPORT_RETRY_COUNT);
    }

    @Test
    public void testSendReportSuccess_appDestOnly_noTriggerDebugAvailableHeader()
            throws DatastoreException, IOException, JSONException {
        when(mFlags.getMeasurementEnableTriggerDebugSignal()).thenReturn(true);
        setUpTestForTriggerDebugAvailableHeader(
                List.of(APP_DESTINATION),
                List.of(),
                /* hasAdIdPermission= */ true,
                /* hasArDebugPermission= */ false,
                /* coarseDestination= */ false);

        ReportingStatus reportingStatus = new ReportingStatus();

        mSpyEventReportingJobHandler.performReport(EVENT_REPORT_ID, reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, reportingStatus.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.UNKNOWN, reportingStatus.getFailureStatus());
        verify(mMeasurementDao, times(1)).markEventReportStatus(any(), anyInt());
        verify(mSpyEventReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        verify(mTransaction, times(3)).begin();
        verify(mTransaction, times(3)).end();
    }

    @Test
    public void testSendReportSuccess_webDestOnlyWithArDebug_hasTriggerDebugHeaderTrue()
            throws DatastoreException, IOException, JSONException {
        when(mFlags.getMeasurementEnableTriggerDebugSignal()).thenReturn(true);
        setUpTestForTriggerDebugAvailableHeader(
                List.of(),
                List.of(DEFAULT_WEB_DESTINATION),
                /* hasAdIdPermission= */ false,
                /* hasArDebugPermission= */ true,
                /* coarseDestination= */ false);

        ReportingStatus reportingStatus = new ReportingStatus();

        mSpyEventReportingJobHandler.performReport(EVENT_REPORT_ID, reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, reportingStatus.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.UNKNOWN, reportingStatus.getFailureStatus());
        verify(mMeasurementDao, times(1)).markEventReportStatus(any(), anyInt());
        verify(mSpyEventReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(Boolean.TRUE));
        verify(mTransaction, times(3)).begin();
        verify(mTransaction, times(3)).end();
    }

    @Test
    public void testSendReportSuccess_webDestOnlyWithoutArDebug_hasTriggerDebugHeaderFalse()
            throws DatastoreException, IOException, JSONException {
        when(mFlags.getMeasurementEnableTriggerDebugSignal()).thenReturn(true);
        setUpTestForTriggerDebugAvailableHeader(
                List.of(),
                List.of(DEFAULT_WEB_DESTINATION),
                /* hasAdIdPermission= */ false,
                /* hasArDebugPermission= */ false,
                /* coarseDestination= */ false);

        ReportingStatus reportingStatus = new ReportingStatus();

        mSpyEventReportingJobHandler.performReport(EVENT_REPORT_ID, reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, reportingStatus.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.UNKNOWN, reportingStatus.getFailureStatus());
        verify(mMeasurementDao, times(1)).markEventReportStatus(any(), anyInt());
        verify(mSpyEventReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(Boolean.FALSE));
        verify(mTransaction, times(3)).begin();
        verify(mTransaction, times(3)).end();
    }

    @Test
    public void testSendReportSuccess_appAndWebDest_coarseDestFalse_hasTriggerDebugHeaderTrue()
            throws DatastoreException, IOException, JSONException {
        when(mFlags.getMeasurementEnableTriggerDebugSignal()).thenReturn(true);
        setUpTestForTriggerDebugAvailableHeader(
                List.of(APP_DESTINATION),
                List.of(DEFAULT_WEB_DESTINATION),
                /* hasAdIdPermission= */ false,
                /* hasArDebugPermission= */ true,
                /* coarseDestination= */ false);

        ReportingStatus reportingStatus = new ReportingStatus();

        mSpyEventReportingJobHandler.performReport(EVENT_REPORT_ID, reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, reportingStatus.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.UNKNOWN, reportingStatus.getFailureStatus());
        verify(mMeasurementDao, times(1)).markEventReportStatus(any(), anyInt());
        verify(mSpyEventReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(Boolean.TRUE));
        verify(mTransaction, times(3)).begin();
        verify(mTransaction, times(3)).end();
    }

    @Test
    public void testSendReportSuccess_appAndWebDest_coarseDestFalse_hasTriggerDebugHeaderFalse()
            throws DatastoreException, IOException, JSONException {
        when(mFlags.getMeasurementEnableTriggerDebugSignal()).thenReturn(true);
        setUpTestForTriggerDebugAvailableHeader(
                List.of(APP_DESTINATION),
                List.of(DEFAULT_WEB_DESTINATION),
                /* hasAdIdPermission= */ false,
                /* hasArDebugPermission= */ false,
                /* coarseDestination= */ false);

        ReportingStatus reportingStatus = new ReportingStatus();

        mSpyEventReportingJobHandler.performReport(EVENT_REPORT_ID, reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, reportingStatus.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.UNKNOWN, reportingStatus.getFailureStatus());
        verify(mMeasurementDao, times(1)).markEventReportStatus(any(), anyInt());
        verify(mSpyEventReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(Boolean.FALSE));
        verify(mTransaction, times(3)).begin();
        verify(mTransaction, times(3)).end();
    }

    @Test
    public void testSendReportSuccess_coarseDestFlagDisabled_noTriggerDebugHeader()
            throws DatastoreException, IOException, JSONException {
        when(mFlags.getMeasurementEnableTriggerDebugSignal()).thenReturn(true);
        when(mFlags.getMeasurementEnableEventTriggerDebugSignalForCoarseDestination())
                .thenReturn(false);
        setUpTestForTriggerDebugAvailableHeader(
                List.of(APP_DESTINATION),
                List.of(DEFAULT_WEB_DESTINATION),
                /* hasAdIdPermission= */ true,
                /* hasArDebugPermission= */ true,
                /* coarseDestination= */ true);

        ReportingStatus reportingStatus = new ReportingStatus();

        mSpyEventReportingJobHandler.performReport(EVENT_REPORT_ID, reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, reportingStatus.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.UNKNOWN, reportingStatus.getFailureStatus());
        verify(mMeasurementDao, times(1)).markEventReportStatus(any(), anyInt());
        verify(mSpyEventReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(null));
        verify(mTransaction, times(3)).begin();
        verify(mTransaction, times(3)).end();
    }

    @Test
    public void testSendReportSuccess_coarseDestFlagEnabled_hasTriggerDebugHeaderTrue()
            throws DatastoreException, IOException, JSONException {
        when(mFlags.getMeasurementEnableTriggerDebugSignal()).thenReturn(true);
        when(mFlags.getMeasurementEnableEventTriggerDebugSignalForCoarseDestination())
                .thenReturn(true);

        setUpTestForTriggerDebugAvailableHeader(
                List.of(APP_DESTINATION),
                List.of(DEFAULT_WEB_DESTINATION),
                /* hasAdIdPermission= */ true,
                /* hasArDebugPermission= */ false,
                /* coarseDestination= */ true);

        ReportingStatus reportingStatus = new ReportingStatus();

        mSpyEventReportingJobHandler.performReport(EVENT_REPORT_ID, reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, reportingStatus.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.UNKNOWN, reportingStatus.getFailureStatus());
        verify(mMeasurementDao, times(1)).markEventReportStatus(any(), anyInt());
        verify(mSpyEventReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(Boolean.TRUE));
        verify(mTransaction, times(3)).begin();
        verify(mTransaction, times(3)).end();
    }

    @Test
    public void testSendReportSuccess_coarseDestFlagEnabled_hasTriggerDebugHeaderFalse()
            throws DatastoreException, IOException, JSONException {
        when(mFlags.getMeasurementEnableTriggerDebugSignal()).thenReturn(true);
        when(mFlags.getMeasurementEnableEventTriggerDebugSignalForCoarseDestination())
                .thenReturn(true);

        setUpTestForTriggerDebugAvailableHeader(
                List.of(APP_DESTINATION),
                List.of(DEFAULT_WEB_DESTINATION),
                /* hasAdIdPermission= */ false,
                /* hasArDebugPermission= */ false,
                /* coarseDestination= */ true);

        ReportingStatus reportingStatus = new ReportingStatus();

        mSpyEventReportingJobHandler.performReport(EVENT_REPORT_ID, reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, reportingStatus.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.UNKNOWN, reportingStatus.getFailureStatus());
        verify(mMeasurementDao, times(1)).markEventReportStatus(any(), anyInt());
        verify(mSpyEventReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(Boolean.FALSE));
        verify(mTransaction, times(3)).begin();
        verify(mTransaction, times(3)).end();
    }

    @Test
    public void testSendReportSuccess_fakeReport_hasTriggerDebugHeaderTrue()
            throws DatastoreException, IOException, JSONException {
        when(mFlags.getMeasurementEnableTriggerDebugSignal()).thenReturn(true);
        when(mFlags.getMeasurementTriggerDebugSignalProbabilityForFakeReports()).thenReturn(1.0F);

        EventReport eventReport =
                new EventReport.Builder()
                        .setId(EVENT_REPORT_ID)
                        .setSourceEventId(SOURCE_EVENT_ID)
                        .setAttributionDestinations(ATTRIBUTION_DESTINATIONS)
                        .setStatus(EventReport.Status.PENDING)
                        .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .setReportTime(10L)
                        .setSourceId(SOURCE_ID)
                        // Set triggerId to null to test fake reports.
                        .setTriggerId(null)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setAttributionDestinations(List.of(DEFAULT_WEB_DESTINATION))
                        .build();
        JSONObject eventReportPayload = createEventReportPayloadFromReport(eventReport);

        Pair<List<Uri>, List<Uri>> destinations =
                new Pair<>(List.of(), List.of(DEFAULT_WEB_DESTINATION));
        Source.Builder sourceBuilder =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId(SOURCE_ID)
                        .setRegistrationOrigin(REPORTING_ORIGIN);
        sourceBuilder.setWebDestinations(List.of(DEFAULT_WEB_DESTINATION));
        Source source = sourceBuilder.build();

        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);
        when(mMeasurementDao.getSourceDestinations(eventReport.getSourceId()))
                .thenReturn(destinations);
        when(mMeasurementDao.getSource(eventReport.getSourceId())).thenReturn(source);
        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);

        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(Boolean.TRUE));
        doReturn(eventReportPayload)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(Mockito.any());
        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(eventReport.getId(), AggregateReport.Status.DELIVERED);

        ReportingStatus reportingStatus = new ReportingStatus();

        mSpyEventReportingJobHandler.performReport(EVENT_REPORT_ID, reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, reportingStatus.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.UNKNOWN, reportingStatus.getFailureStatus());
        verify(mMeasurementDao, times(1)).markEventReportStatus(any(), anyInt());
        verify(mSpyEventReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(Boolean.TRUE));
        verify(mTransaction, times(3)).begin();
        verify(mTransaction, times(3)).end();
    }

    @Test
    public void testSendReportSuccess_fakeReport_hasTriggerDebugHeaderFalse()
            throws DatastoreException, IOException, JSONException {
        when(mFlags.getMeasurementEnableTriggerDebugSignal()).thenReturn(true);
        when(mFlags.getMeasurementTriggerDebugSignalProbabilityForFakeReports()).thenReturn(0.0F);

        EventReport eventReport =
                new EventReport.Builder()
                        .setId(EVENT_REPORT_ID)
                        .setSourceEventId(SOURCE_EVENT_ID)
                        .setStatus(EventReport.Status.PENDING)
                        .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .setReportTime(10L)
                        .setSourceId(SOURCE_ID)
                        // Set triggerId to null to test fake reports.
                        .setTriggerId(null)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setAttributionDestinations(List.of(DEFAULT_WEB_DESTINATION))
                        .build();
        JSONObject eventReportPayload = createEventReportPayloadFromReport(eventReport);

        Pair<List<Uri>, List<Uri>> destinations =
                new Pair<>(List.of(), List.of(DEFAULT_WEB_DESTINATION));
        Source.Builder sourceBuilder =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId(SOURCE_ID)
                        .setRegistrationOrigin(REPORTING_ORIGIN);
        sourceBuilder.setWebDestinations(List.of(DEFAULT_WEB_DESTINATION));
        Source source = sourceBuilder.build();

        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);
        when(mMeasurementDao.getSourceDestinations(eventReport.getSourceId()))
                .thenReturn(destinations);
        when(mMeasurementDao.getSource(eventReport.getSourceId())).thenReturn(source);
        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);

        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(Boolean.FALSE));
        doReturn(eventReportPayload)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(Mockito.any());
        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(eventReport.getId(), AggregateReport.Status.DELIVERED);

        ReportingStatus reportingStatus = new ReportingStatus();

        mSpyEventReportingJobHandler.performReport(EVENT_REPORT_ID, reportingStatus);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, reportingStatus.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.UNKNOWN, reportingStatus.getFailureStatus());
        verify(mMeasurementDao, times(1)).markEventReportStatus(any(), anyInt());
        verify(mSpyEventReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), eq(Boolean.FALSE));
        verify(mTransaction, times(3)).begin();
        verify(mTransaction, times(3)).end();
    }

    private void setUpTestForTriggerDebugAvailableHeader(
            List<Uri> appDestinations,
            List<Uri> webDestinations,
            boolean hasAdIdPermission,
            boolean hasArDebugPermission,
            boolean coarseDestination)
            throws DatastoreException, IOException, JSONException {
        EventReport eventReport =
                new EventReport.Builder()
                        .setId(EVENT_REPORT_ID)
                        .setSourceEventId(SOURCE_EVENT_ID)
                        .setStatus(EventReport.Status.PENDING)
                        .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .setReportTime(10L)
                        .setSourceId(SOURCE_ID)
                        .setTriggerId(TRIGGER_ID)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setAttributionDestinations(
                                Stream.concat(appDestinations.stream(), webDestinations.stream())
                                        .collect(Collectors.toList()))
                        .build();
        JSONObject eventReportPayload = createEventReportPayloadFromReport(eventReport);

        Pair<List<Uri>, List<Uri>> destinations = new Pair<>(appDestinations, webDestinations);
        Source.Builder sourceBuilder =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId(SOURCE_ID)
                        .setRegistrationOrigin(REPORTING_ORIGIN)
                        .setCoarseEventReportDestinations(coarseDestination);
        if (!appDestinations.isEmpty()) {
            sourceBuilder.setAppDestinations(appDestinations);
        }
        if (!webDestinations.isEmpty()) {
            sourceBuilder.setWebDestinations(webDestinations);
        }
        Source source = sourceBuilder.build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId(TRIGGER_ID)
                        .setAdIdPermission(hasAdIdPermission)
                        .setArDebugPermission(hasArDebugPermission)
                        .build();

        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);
        when(mMeasurementDao.getSourceDestinations(eventReport.getSourceId()))
                .thenReturn(destinations);
        when(mMeasurementDao.getSource(eventReport.getSourceId())).thenReturn(source);
        when(mMeasurementDao.getTrigger(eventReport.getTriggerId())).thenReturn(trigger);
        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_ORIGIN), any(), any());
        doReturn(eventReportPayload)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(Mockito.any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(eventReport.getId(), AggregateReport.Status.DELIVERED);
    }

    private static EventReport createSampleEventReport() {
        return new EventReport.Builder()
                .setId(EVENT_REPORT_ID)
                .setSourceEventId(SOURCE_EVENT_ID)
                .setAttributionDestinations(ATTRIBUTION_DESTINATIONS)
                .setStatus(EventReport.Status.PENDING)
                .setRegistrationOrigin(REPORTING_ORIGIN)
                .setEnrollmentId(ENROLLMENT_ID)
                .build();
    }

    private static JSONObject createEventReportPayloadFromReport(EventReport eventReport)
            throws JSONException {
        return new EventReportPayload.Builder()
                .setReportId(eventReport.getId())
                .setSourceEventId(eventReport.getSourceEventId())
                .setAttributionDestination(eventReport.getAttributionDestinations())
                .build()
                .toJson();
    }
}
