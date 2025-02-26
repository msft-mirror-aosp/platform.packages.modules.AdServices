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

import static com.android.adservices.service.measurement.util.Time.roundDownToDay;

import static junit.framework.Assert.assertFalse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
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
import com.android.adservices.service.exception.CryptoException;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.KeyValueData;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.TriggerFixture;
import com.android.adservices.service.measurement.aggregation.AggregateCryptoFixture;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKeyManager;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.aggregation.AggregateReportFixture;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Unit test for {@link AggregateReportingJobHandler} */
@RunWith(MockitoJUnitRunner.class)
public class AggregateReportingJobHandlerTest {
    private static final Uri REPORTING_URI = WebUtil.validUri("https://subdomain.example.test");
    private static final Uri DEFAULT_WEB_DESTINATION =
            WebUtil.validUri("https://def-web-destination.test");
    private static final Uri ALT_WEB_DESTINATION =
            WebUtil.validUri("https://alt-web-destination.test");
    private static final Uri APP_DESTINATION = Uri.parse("android-app://com.app_destination.test");
    private static final Uri COORDINATOR_ORIGIN =
            WebUtil.validUri("https://coordinator.example.test");
    private static final String ENROLLMENT_ID = "enrollment-id";
    private static final String SOURCE_ID = "source-id";
    private static final String TRIGGER_ID = "trigger-id";
    private static final String AGGREGATE_REPORT_ID = "aggregateReportId";
    private static final String API_ATTRIBUTION_REPORTING_DEUB = "attribution-reporting-debug";
    private static final String CLEARTEXT_PAYLOAD =
            "{\"operation\":\"histogram\",\"data\":[{\"bucket\":\"1\",\"value\":2}]}";

    private static final UnsignedLong SOURCE_DEBUG_KEY = new UnsignedLong(237865L);
    private static final UnsignedLong TRIGGER_DEBUG_KEY = new UnsignedLong(928762L);

    private static final String TRIGGER_CONTEXT_ID = "test_context_id";

    protected static Context sContext;

    DatastoreManager mDatastoreManager;

    @Mock IMeasurementDao mMeasurementDao;

    @Mock ITransaction mTransaction;

    @Mock Flags mMockFlags;
    @Mock AdServicesLogger mLogger;
    @Mock AdServicesErrorLogger mErrorLogger;
    @Mock PackageManager mPackageManager;

    AndroidTimeSource mTimeSource;

    AggregateReportingJobHandler mAggregateReportingJobHandler;
    AggregateReportingJobHandler mSpyAggregateReportingJobHandler;
    AggregateReportingJobHandler mSpyDebugAggregateReportingJobHandler;

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
    public void setUp() {
        sContext = spy(ApplicationProvider.getApplicationContext());
        AggregateEncryptionKeyManager mockKeyManager = mock(AggregateEncryptionKeyManager.class);
        ArgumentCaptor<Integer> captorNumberOfKeys = ArgumentCaptor.forClass(Integer.class);
        when(mockKeyManager.getAggregateEncryptionKeys(any(), captorNumberOfKeys.capture()))
                .thenAnswer(
                        invocation -> {
                            List<AggregateEncryptionKey> keys = new ArrayList<>();
                            for (int i = 0; i < captorNumberOfKeys.getValue(); i++) {
                                keys.add(AggregateCryptoFixture.getKey());
                            }
                            return keys;
                        });
        mDatastoreManager = new FakeDatasoreManager();

        mTimeSource = new AndroidTimeSource();

        mAggregateReportingJobHandler =
                new AggregateReportingJobHandler(
                        mDatastoreManager,
                        mockKeyManager,
                        mMockFlags,
                        mLogger,
                        ReportingStatus.ReportType.AGGREGATE,
                        ReportingStatus.UploadMethod.UNKNOWN,
                        sContext,
                        mTimeSource);
        mSpyAggregateReportingJobHandler = Mockito.spy(mAggregateReportingJobHandler);
        mSpyDebugAggregateReportingJobHandler =
                Mockito.spy(
                        new AggregateReportingJobHandler(
                                        mDatastoreManager,
                                        mockKeyManager,
                                        mMockFlags,
                                        mLogger,
                                        ReportingStatus.ReportType.AGGREGATE,
                                        ReportingStatus.UploadMethod.UNKNOWN,
                                        sContext,
                                        mTimeSource)
                                .setIsDebugInstance(true));

        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        when(mMockFlags.getMeasurementAggregationCoordinatorOriginEnabled()).thenReturn(true);
        when(mMockFlags.getMeasurementEnableAppPackageNameLogging()).thenReturn(true);
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(anyInt(), anyInt()));
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
    }

    @Test
    public void testSendReportForPendingReportSuccess()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId(AGGREGATE_REPORT_ID)
                        .setStatus(AggregateReport.Status.PENDING)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .setApi(AggregateReportFixture.ValidAggregateReportParams.API)
                        .build();
        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);
        assertEquals(
                aggregateReportBody.getString("aggregation_coordinator_origin"),
                COORDINATOR_ORIGIN.toString());

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(any(), any(), any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(
                        aggregateReport.getId(), AggregateReport.Status.DELIVERED);
        ReportingStatus status = new ReportingStatus();

        mSpyAggregateReportingJobHandler.performReport(
                aggregateReport.getId(), AggregateCryptoFixture.getKey(), status);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, status.getUploadStatus());
        verify(mMeasurementDao, times(1)).markAggregateReportStatus(any(), anyInt());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void testSendReportSuccess_reinstallAttributionEnabled_persistsAppReportHistory()
            throws DatastoreException, IOException, JSONException {
        when(mMockFlags.getMeasurementEnableReinstallReattribution()).thenReturn(true);
        Long reportTime = 10L;
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId(AGGREGATE_REPORT_ID)
                        .setStatus(AggregateReport.Status.PENDING)
                        .setSourceId(SOURCE_ID)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .setScheduledReportTime(reportTime)
                        .setAttributionDestination(APP_DESTINATION)
                        .setApi(AggregateReportFixture.ValidAggregateReportParams.API)
                        .build();
        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);
        assertEquals(
                aggregateReportBody.getString("aggregation_coordinator_origin"),
                COORDINATOR_ORIGIN.toString());

        Pair<List<Uri>, List<Uri>> destinations =
                new Pair<>(
                        List.of(APP_DESTINATION),
                        List.of(DEFAULT_WEB_DESTINATION, ALT_WEB_DESTINATION));
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId(SOURCE_ID)
                        .setRegistrationOrigin(REPORTING_URI)
                        .build();
        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        when(mMeasurementDao.getSourceDestinations(aggregateReport.getSourceId()))
                .thenReturn(destinations);
        when(mMeasurementDao.getSource(aggregateReport.getSourceId())).thenReturn(source);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(any(), eq(REPORTING_URI), any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(
                        aggregateReport.getId(), AggregateReport.Status.DELIVERED);
        ReportingStatus status = new ReportingStatus();

        mSpyAggregateReportingJobHandler.performReport(
                aggregateReport.getId(), AggregateCryptoFixture.getKey(), status);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, status.getUploadStatus());
        verify(mMeasurementDao, times(1)).markAggregateReportStatus(any(), anyInt());
        verify(mTransaction, times(3)).begin();
        verify(mTransaction, times(3)).end();
        verify(mMeasurementDao, times(1))
                .insertOrUpdateAppReportHistory(any(), any(), eq(reportTime));
    }

    @Test
    public void testSendReportSuccess_reinstallAttributionEnabled_skipNullAggregateReport()
            throws DatastoreException, IOException, JSONException {
        when(mMockFlags.getMeasurementEnableReinstallReattribution()).thenReturn(true);
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId(AGGREGATE_REPORT_ID)
                        .setStatus(AggregateReport.Status.PENDING)
                        .setSourceId(SOURCE_ID)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .setAttributionDestination(APP_DESTINATION)
                        .setIsFakeReport(true)
                        .setApi(AggregateReportFixture.ValidAggregateReportParams.API)
                        .build();
        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);
        assertEquals(
                aggregateReportBody.getString("aggregation_coordinator_origin"),
                COORDINATOR_ORIGIN.toString());

        Pair<List<Uri>, List<Uri>> destinations =
                new Pair<>(
                        List.of(APP_DESTINATION),
                        List.of(DEFAULT_WEB_DESTINATION, ALT_WEB_DESTINATION));
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId(SOURCE_ID)
                        .setRegistrationOrigin(REPORTING_URI)
                        .build();
        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        when(mMeasurementDao.getSourceDestinations(aggregateReport.getSourceId()))
                .thenReturn(destinations);
        when(mMeasurementDao.getSource(aggregateReport.getSourceId())).thenReturn(source);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(any(), eq(REPORTING_URI), any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(
                        aggregateReport.getId(), AggregateReport.Status.DELIVERED);
        ReportingStatus status = new ReportingStatus();

        mSpyAggregateReportingJobHandler.performReport(
                aggregateReport.getId(), AggregateCryptoFixture.getKey(), status);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, status.getUploadStatus());
        verify(mMeasurementDao, times(1)).markAggregateReportStatus(any(), anyInt());
        verify(mTransaction, times(3)).begin();
        verify(mTransaction, times(3)).end();
        verify(mMeasurementDao, times(0)).insertOrUpdateAppReportHistory(any(), any(), anyLong());
    }

    @Test
    public void testSendReportSuccess_reinstallAttributionDisabled_doesNotPersistsAppReportHistory()
            throws DatastoreException, IOException, JSONException {
        when(mMockFlags.getMeasurementEnableReinstallReattribution()).thenReturn(false);
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId(AGGREGATE_REPORT_ID)
                        .setStatus(AggregateReport.Status.PENDING)
                        .setSourceId(SOURCE_ID)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .setAttributionDestination(APP_DESTINATION)
                        .setApi(AggregateReportFixture.ValidAggregateReportParams.API)
                        .build();
        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);
        assertEquals(
                aggregateReportBody.getString("aggregation_coordinator_origin"),
                COORDINATOR_ORIGIN.toString());

        Pair<List<Uri>, List<Uri>> destinations =
                new Pair<>(
                        List.of(APP_DESTINATION),
                        List.of(DEFAULT_WEB_DESTINATION, ALT_WEB_DESTINATION));
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId(SOURCE_ID)
                        .setRegistrationOrigin(REPORTING_URI)
                        .build();
        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        when(mMeasurementDao.getSourceDestinations(aggregateReport.getSourceId()))
                .thenReturn(destinations);
        when(mMeasurementDao.getSource(aggregateReport.getSourceId())).thenReturn(source);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(any(), eq(REPORTING_URI), any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(
                        aggregateReport.getId(), AggregateReport.Status.DELIVERED);
        ReportingStatus status = new ReportingStatus();

        mSpyAggregateReportingJobHandler.performReport(
                aggregateReport.getId(), AggregateCryptoFixture.getKey(), status);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, status.getUploadStatus());
        verify(mMeasurementDao, times(1)).markAggregateReportStatus(any(), anyInt());
        verify(mTransaction, times(3)).begin();
        verify(mTransaction, times(3)).end();
        verify(mMeasurementDao, never()).insertOrUpdateAppReportHistory(any(), any(), anyLong());
    }

    @Test
    public void testSendReportFailed_uninstallEnabled_deleteReport()
            throws DatastoreException, IOException, JSONException {
        when(mMockFlags.getMeasurementEnableMinReportLifespanForUninstall()).thenReturn(true);
        when(mMockFlags.getMeasurementMinReportLifespanForUninstallSeconds())
                .thenReturn(TimeUnit.DAYS.toSeconds(1));

        long currentTime = System.currentTimeMillis();
        Uri publisher = Uri.parse("https://publisher.test");

        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId(AGGREGATE_REPORT_ID)
                        .setStatus(AggregateReport.Status.PENDING)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAttributionDestination(APP_DESTINATION)
                        .setPublisher(publisher)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .setApi(AggregateReportFixture.ValidAggregateReportParams.API)
                        .setTriggerTime(currentTime - TimeUnit.HOURS.toMillis(25))
                        .build();

        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(any(), eq(REPORTING_URI), any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(
                        aggregateReport.getId(), AggregateReport.Status.DELIVERED);
        ReportingStatus status = new ReportingStatus();

        mSpyAggregateReportingJobHandler.performReport(
                aggregateReport.getId(), AggregateCryptoFixture.getKey(), status);

        Truth.assertThat(status.getUploadStatus()).isEqualTo(ReportingStatus.UploadStatus.FAILURE);
        Truth.assertThat(status.getFailureStatus())
                .isEqualTo(ReportingStatus.FailureStatus.APP_UNINSTALLED_OR_OUTSIDE_WINDOW);
        verify(mMeasurementDao, times(1)).deleteAggregateReport(aggregateReport);
    }

    @Test
    public void testSendReportSuccess_uninstallEnabled_hasApps_sendReport()
            throws DatastoreException,
                    IOException,
                    JSONException,
                    PackageManager.NameNotFoundException {

        when(mMockFlags.getMeasurementEnableMinReportLifespanForUninstall()).thenReturn(true);
        when(mMockFlags.getMeasurementMinReportLifespanForUninstallSeconds())
                .thenReturn(TimeUnit.DAYS.toSeconds(1));

        long currentTime = System.currentTimeMillis();
        Uri publisher = Uri.parse("https://publisher.test");

        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId(AGGREGATE_REPORT_ID)
                        .setStatus(AggregateReport.Status.PENDING)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAttributionDestination(APP_DESTINATION)
                        .setPublisher(publisher)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .setApi(AggregateReportFixture.ValidAggregateReportParams.API)
                        .setTriggerTime(currentTime - TimeUnit.HOURS.toMillis(25))
                        .build();

        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(any(), eq(REPORTING_URI), any());

        ApplicationInfo applicationInfo1 = new ApplicationInfo();
        applicationInfo1.packageName = APP_DESTINATION.getHost();
        ApplicationInfo applicationInfo2 = new ApplicationInfo();
        applicationInfo2.packageName = publisher.getHost();

        when(sContext.getPackageManager()).thenReturn(mPackageManager);

        when(mPackageManager.getApplicationInfo(APP_DESTINATION.getHost(), 0))
                .thenReturn(applicationInfo1);
        when(mPackageManager.getApplicationInfo(publisher.getHost(), 0))
                .thenReturn(applicationInfo2);

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(
                        aggregateReport.getId(), AggregateReport.Status.DELIVERED);
        ReportingStatus status = new ReportingStatus();

        mSpyAggregateReportingJobHandler.performReport(
                aggregateReport.getId(), AggregateCryptoFixture.getKey(), status);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, status.getUploadStatus());
        verify(mMeasurementDao, times(0)).deleteAggregateReport(aggregateReport);
    }

    @Test
    public void testSendReportSuccess_uninstallEnabled_sendReport()
            throws DatastoreException, IOException, JSONException {
        when(mMockFlags.getMeasurementEnableMinReportLifespanForUninstall()).thenReturn(true);
        when(mMockFlags.getMeasurementMinReportLifespanForUninstallSeconds())
                .thenReturn(TimeUnit.DAYS.toSeconds(1));

        long currentTime = System.currentTimeMillis();

        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId(AGGREGATE_REPORT_ID)
                        .setStatus(AggregateReport.Status.PENDING)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .setApi(AggregateReportFixture.ValidAggregateReportParams.API)
                        .setTriggerTime(currentTime - TimeUnit.HOURS.toMillis(23))
                        .build();

        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(any(), eq(REPORTING_URI), any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(
                        aggregateReport.getId(), AggregateReport.Status.DELIVERED);
        ReportingStatus status = new ReportingStatus();

        mSpyAggregateReportingJobHandler.performReport(
                aggregateReport.getId(), AggregateCryptoFixture.getKey(), status);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, status.getUploadStatus());
        verify(mMeasurementDao, times(0)).deleteAggregateReport(aggregateReport);
    }

    @Test
    public void testSendReportForPendingReportSuccess_originFlagDisabled()
            throws DatastoreException, IOException, JSONException {
        when(mMockFlags.getMeasurementAggregationCoordinatorOriginEnabled()).thenReturn(false);
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId(AGGREGATE_REPORT_ID)
                        .setStatus(AggregateReport.Status.PENDING)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .setApi(AggregateReportFixture.ValidAggregateReportParams.API)
                        .build();

        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);
        // No Aggregation coordinator
        assertTrue(aggregateReportBody.isNull("aggregation_coordinator_origin"));

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(any(), eq(REPORTING_URI), any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(
                        aggregateReport.getId(), AggregateReport.Status.DELIVERED);
        ReportingStatus status = new ReportingStatus();

        mSpyAggregateReportingJobHandler.performReport(
                aggregateReport.getId(), AggregateCryptoFixture.getKey(), status);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, status.getUploadStatus());
        verify(mMeasurementDao, times(1)).markAggregateReportStatus(any(), anyInt());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void testSendReportForPendingDebugReportSuccess()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId(AGGREGATE_REPORT_ID)
                        .setStatus(AggregateReport.Status.PENDING)
                        .setDebugReportStatus(AggregateReport.DebugReportStatus.PENDING)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .setApi(AggregateReportFixture.ValidAggregateReportParams.API)
                        .build();
        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyDebugAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doReturn(aggregateReportBody)
                .when(mSpyDebugAggregateReportingJobHandler)
                .createReportJsonPayload(any(), eq(REPORTING_URI), any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateDebugReportDelivered(aggregateReport.getId());
        ReportingStatus status = new ReportingStatus();

        mSpyDebugAggregateReportingJobHandler.performReport(
                aggregateReport.getId(), AggregateCryptoFixture.getKey(), status);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, status.getUploadStatus());
        verify(mMeasurementDao, times(1)).markAggregateDebugReportDelivered(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void testSendReportForPendingReportSuccessSingleTriggerDebugKey()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId(AGGREGATE_REPORT_ID)
                        .setStatus(AggregateReport.Status.PENDING)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .setApi(AggregateReportFixture.ValidAggregateReportParams.API)
                        .build();
        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(any(), eq(REPORTING_URI), any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(
                        aggregateReport.getId(), AggregateReport.Status.DELIVERED);
        ReportingStatus status = new ReportingStatus();

        mSpyAggregateReportingJobHandler.performReport(
                aggregateReport.getId(), AggregateCryptoFixture.getKey(), status);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, status.getUploadStatus());
        verify(mMeasurementDao, times(1)).markAggregateReportStatus(any(), anyInt());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void testSendReportForPendingReportSuccessSingleSourceDebugKey()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId(AGGREGATE_REPORT_ID)
                        .setStatus(AggregateReport.Status.PENDING)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .setApi(AggregateReportFixture.ValidAggregateReportParams.API)
                        .build();
        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(any(), eq(REPORTING_URI), any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(
                        aggregateReport.getId(), AggregateReport.Status.DELIVERED);
        ReportingStatus status = new ReportingStatus();

        mSpyAggregateReportingJobHandler.performReport(
                aggregateReport.getId(), AggregateCryptoFixture.getKey(), status);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, status.getUploadStatus());
        verify(mMeasurementDao, times(1)).markAggregateReportStatus(any(), anyInt());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void testSendReportForPendingReportSuccessNullDebugKeys()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId(AGGREGATE_REPORT_ID)
                        .setStatus(AggregateReport.Status.PENDING)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setSourceDebugKey(null)
                        .setTriggerDebugKey(null)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .setApi(AggregateReportFixture.ValidAggregateReportParams.API)
                        .build();
        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(any(), eq(REPORTING_URI), any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(
                        aggregateReport.getId(), AggregateReport.Status.DELIVERED);
        ReportingStatus status = new ReportingStatus();

        mSpyAggregateReportingJobHandler.performReport(
                aggregateReport.getId(), AggregateCryptoFixture.getKey(), status);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, status.getUploadStatus());
        verify(mMeasurementDao, times(1)).markAggregateReportStatus(any(), anyInt());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void testSendReportForPendingReportFailure()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId(AGGREGATE_REPORT_ID)
                        .setStatus(AggregateReport.Status.PENDING)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .setApi(AggregateReportFixture.ValidAggregateReportParams.API)
                        .build();
        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_BAD_REQUEST)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(any(), eq(REPORTING_URI), any());
        ReportingStatus status = new ReportingStatus();

        mSpyAggregateReportingJobHandler.performReport(
                aggregateReport.getId(), AggregateCryptoFixture.getKey(), status);

        assertEquals(ReportingStatus.UploadStatus.FAILURE, status.getUploadStatus());
        assertEquals(
                status.getFailureStatus(),
                ReportingStatus.FailureStatus.UNSUCCESSFUL_HTTP_RESPONSE_CODE);
        verify(mMeasurementDao, never()).markAggregateReportStatus(any(), anyInt());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void testSendReportForAlreadyDeliveredReport() throws DatastoreException {
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId(AGGREGATE_REPORT_ID)
                        .setStatus(AggregateReport.Status.DELIVERED)
                        .setDebugCleartextPayload(CLEARTEXT_PAYLOAD)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .setApi(AggregateReportFixture.ValidAggregateReportParams.API)
                        .build();

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        ReportingStatus status = new ReportingStatus();

        mSpyAggregateReportingJobHandler.performReport(
                aggregateReport.getId(), AggregateCryptoFixture.getKey(), status);

        assertEquals(ReportingStatus.UploadStatus.FAILURE, status.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.REPORT_NOT_PENDING, status.getFailureStatus());
        verify(mMeasurementDao, never()).markAggregateReportStatus(any(), anyInt());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void testPerformScheduledPendingReportsForMultipleReports()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport1 = createASampleAggregateReport();
        JSONObject aggregateReportBody1 = createASampleAggregateReportBody(aggregateReport1);
        AggregateReport aggregateReport2 =
                new AggregateReport.Builder()
                        .setId("aggregateReportId2")
                        .setStatus(AggregateReport.Status.PENDING)
                        .setScheduledReportTime(1100L)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .setApi(AggregateReportFixture.ValidAggregateReportParams.API)
                        .build();
        JSONObject aggregateReportBody2 = createASampleAggregateReportBody(aggregateReport2);

        when(mMeasurementDao.getPendingAggregateReportIdsByCoordinatorInWindow(1000, 1100))
                .thenReturn(
                        Map.of(
                                COORDINATOR_ORIGIN.toString(),
                                List.of(aggregateReport1.getId(), aggregateReport2.getId())));
        when(mMeasurementDao.getAggregateReport(aggregateReport1.getId()))
                .thenReturn(aggregateReport1);
        when(mMeasurementDao.getAggregateReport(aggregateReport2.getId()))
                .thenReturn(aggregateReport2);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doReturn(aggregateReportBody1)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(
                        aggregateReport1, REPORTING_URI, AggregateCryptoFixture.getKey());
        doReturn(aggregateReportBody2)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(
                        aggregateReport2, REPORTING_URI, AggregateCryptoFixture.getKey());

        assertTrue(
                mSpyAggregateReportingJobHandler.performScheduledPendingReportsInWindow(
                        1000, 1100));

        verify(mMeasurementDao, times(2)).markAggregateReportStatus(any(), anyInt());
        verify(mTransaction, times(5)).begin();
        verify(mTransaction, times(5)).end();
    }

    @Test
    public void testPerformScheduledPendingReportsInWindow_noKeys()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport = createASampleAggregateReport();
        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);

        when(mMeasurementDao.getPendingAggregateReportIdsByCoordinatorInWindow(1000, 1100))
                .thenReturn(
                        Map.of(COORDINATOR_ORIGIN.toString(), List.of(aggregateReport.getId())));
        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(
                        aggregateReport, REPORTING_URI, AggregateCryptoFixture.getKey());

        AggregateEncryptionKeyManager mockKeyManager = mock(AggregateEncryptionKeyManager.class);
        when(mockKeyManager.getAggregateEncryptionKeys(any(), anyInt()))
                .thenReturn(Collections.emptyList());
        mAggregateReportingJobHandler =
                new AggregateReportingJobHandler(
                        new FakeDatasoreManager(),
                        mockKeyManager,
                        mMockFlags,
                        mLogger,
                        sContext,
                        new AndroidTimeSource());
        mSpyAggregateReportingJobHandler = Mockito.spy(mAggregateReportingJobHandler);

        assertTrue(
                mSpyAggregateReportingJobHandler.performScheduledPendingReportsInWindow(
                        1000, 1100));

        verify(mMeasurementDao, never()).markAggregateReportStatus(any(), anyInt());
    }

    @Test
    public void testPerformScheduledPendingReports_ThreadInterrupted()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport1 = createASampleAggregateReport();
        JSONObject aggregateReportBody1 = createASampleAggregateReportBody(aggregateReport1);
        AggregateReport aggregateReport2 =
                new AggregateReport.Builder()
                        .setId("aggregateReportId2")
                        .setStatus(AggregateReport.Status.PENDING)
                        .setScheduledReportTime(1100L)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .setApi(AggregateReportFixture.ValidAggregateReportParams.API)
                        .build();
        JSONObject aggregateReportBody2 = createASampleAggregateReportBody(aggregateReport2);

        when(mMeasurementDao.getPendingAggregateReportIdsByCoordinatorInWindow(1000, 1100))
                .thenReturn(
                        Map.of(
                                COORDINATOR_ORIGIN.toString(),
                                List.of(aggregateReport1.getId(), aggregateReport2.getId())));
        when(mMeasurementDao.getAggregateReport(aggregateReport1.getId()))
                .thenReturn(aggregateReport1);
        when(mMeasurementDao.getAggregateReport(aggregateReport2.getId()))
                .thenReturn(aggregateReport2);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(Mockito.eq(REPORTING_URI), any(), eq(null), anyString());
        doReturn(aggregateReportBody1)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(
                        aggregateReport1, REPORTING_URI, AggregateCryptoFixture.getKey());
        doReturn(aggregateReportBody2)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(
                        aggregateReport2, REPORTING_URI, AggregateCryptoFixture.getKey());

        Thread.currentThread().interrupt();
        assertTrue(
                mSpyAggregateReportingJobHandler.performScheduledPendingReportsInWindow(
                        1000, 1100));

        // 0 reports processed, since the thread exits early.
        verify(mMeasurementDao, times(0)).markAggregateReportStatus(any(), anyInt());

        // 1 transaction for initial retrieval of pending report ids.
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void testPerformScheduledPendingReports_LogZeroRetryCount()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport1 = createASampleAggregateReport();
        JSONObject aggregateReportBody1 = createASampleAggregateReportBody(aggregateReport1);

        when(mMeasurementDao.getPendingAggregateReportIdsByCoordinatorInWindow(1000, 1100))
                .thenReturn(
                        Map.of(COORDINATOR_ORIGIN.toString(), List.of(aggregateReport1.getId())));
        when(mMeasurementDao.getAggregateReport(aggregateReport1.getId()))
                .thenReturn(aggregateReport1);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doReturn(aggregateReportBody1)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(
                        aggregateReport1, REPORTING_URI, AggregateCryptoFixture.getKey());

        assertTrue(
                mSpyAggregateReportingJobHandler.performScheduledPendingReportsInWindow(
                        1000, 1100));
        ArgumentCaptor<MeasurementReportsStats> statusArg =
                ArgumentCaptor.forClass(MeasurementReportsStats.class);
        verify(mLogger).logMeasurementReports(statusArg.capture(), eq(ENROLLMENT_ID));
        MeasurementReportsStats measurementReportsStats = statusArg.getValue();
        assertEquals(
                measurementReportsStats.getType(), ReportingStatus.ReportType.AGGREGATE.getValue());
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
        AggregateReport aggregateReport1 = createASampleAggregateReport();

        when(mMeasurementDao.getPendingAggregateReportIdsByCoordinatorInWindow(1000, 1100))
                .thenReturn(
                        Map.of(COORDINATOR_ORIGIN.toString(), List.of(aggregateReport1.getId())));
        when(mMeasurementDao.getAggregateReport(aggregateReport1.getId())).thenReturn(null);

        assertTrue(
                mSpyAggregateReportingJobHandler.performScheduledPendingReportsInWindow(
                        1000, 1100));
        ArgumentCaptor<MeasurementReportsStats> statusArg =
                ArgumentCaptor.forClass(MeasurementReportsStats.class);
        verify(mLogger).logMeasurementReports(statusArg.capture(), eq(null));
        MeasurementReportsStats measurementReportsStats = statusArg.getValue();
        assertEquals(
                measurementReportsStats.getType(), ReportingStatus.ReportType.AGGREGATE.getValue());
        assertEquals(
                measurementReportsStats.getResultCode(),
                ReportingStatus.UploadStatus.FAILURE.getValue());
        assertEquals(
                measurementReportsStats.getFailureType(),
                ReportingStatus.FailureStatus.REPORT_NOT_FOUND.getValue());
        verify(mMeasurementDao)
                .incrementAndGetReportingRetryCount(
                        aggregateReport1.getId(),
                        KeyValueData.DataType.AGGREGATE_REPORT_RETRY_COUNT);
    }

    @Test
    public void performReport_throwsIOException_logsReportingStatus()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport = createASampleAggregateReport();
        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doThrow(new IOException())
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(any(), any(), any());
        ReportingStatus status = new ReportingStatus();

        mSpyAggregateReportingJobHandler.performReport(
                aggregateReport.getId(), AggregateCryptoFixture.getKey(), status);

        assertEquals(ReportingStatus.UploadStatus.FAILURE, status.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.NETWORK, status.getFailureStatus());
        verify(mMeasurementDao, never()).markAggregateReportStatus(any(), anyInt());
        verify(mSpyAggregateReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_throwsJsonDisabledToThrow_logsAndSwallowsException()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport = createASampleAggregateReport();

        doReturn(false)
                .when(mMockFlags)
                .getMeasurementEnableReportDeletionOnUnrecoverableException();
        doReturn(false).when(mMockFlags).getMeasurementEnableReportingJobsThrowJsonException();
        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);

        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(any(), any(), eq(null), anyString());
        doThrow(new JSONException("cause message"))
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(any(), any(), any());
        ReportingStatus status = new ReportingStatus();

        mSpyAggregateReportingJobHandler.performReport(
                aggregateReport.getId(), AggregateCryptoFixture.getKey(), status);

        assertEquals(ReportingStatus.UploadStatus.FAILURE, status.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.SERIALIZATION_ERROR, status.getFailureStatus());
        verify(mMeasurementDao, never()).markAggregateReportStatus(anyString(), anyInt());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_throwsJsonExceptionNoSampling_logsAndSwallowsException()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport = createASampleAggregateReport();

        doReturn(true)
                .when(mMockFlags)
                .getMeasurementEnableReportDeletionOnUnrecoverableException();
        doReturn(true).when(mMockFlags).getMeasurementEnableReportingJobsThrowJsonException();
        doReturn(0.0f).when(mMockFlags).getMeasurementThrowUnknownExceptionSamplingRate();
        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);

        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(any(), any(), eq(null), anyString());
        doThrow(new JSONException("cause message"))
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(any(), any(), any());
        ReportingStatus status = new ReportingStatus();

        mSpyAggregateReportingJobHandler.performReport(
                aggregateReport.getId(), AggregateCryptoFixture.getKey(), status);

        assertEquals(ReportingStatus.UploadStatus.FAILURE, status.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.SERIALIZATION_ERROR, status.getFailureStatus());
        verify(mMeasurementDao).markAggregateReportStatus(eq(aggregateReport.getId()), anyInt());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performReport_throwsJsonEnabledToThrow_marksReportDeletedAndRethrowsException()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport = createASampleAggregateReport();

        doReturn(true)
                .when(mMockFlags)
                .getMeasurementEnableReportDeletionOnUnrecoverableException();
        doReturn(true).when(mMockFlags).getMeasurementEnableReportingJobsThrowJsonException();
        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doThrow(new JSONException("cause message"))
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(any(), any(), any());
        doReturn(1.0f).when(mMockFlags).getMeasurementThrowUnknownExceptionSamplingRate();

        try {
            mSpyAggregateReportingJobHandler.performReport(
                    aggregateReport.getId(),
                    AggregateCryptoFixture.getKey(),
                    new ReportingStatus());
            fail();
        } catch (IllegalStateException e) {
            assertEquals(JSONException.class, e.getCause().getClass());
            assertEquals("cause message", e.getCause().getMessage());
        }

        verify(mMeasurementDao)
                .markAggregateReportStatus(
                        aggregateReport.getId(), AggregateReport.Status.MARKED_TO_DELETE);
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performReport_throwsUnknownExceptionDisabledToThrow_logsAndSwallowsException()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport = createASampleAggregateReport();
        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);

        doReturn(false)
                .when(mMockFlags)
                .getMeasurementEnableReportingJobsThrowUnaccountedException();
        doReturn(aggregateReport).when(mMeasurementDao).getAggregateReport(aggregateReport.getId());
        doThrow(new RuntimeException("unknown exception"))
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doReturn(aggregateReportBody)
                .when(mSpyDebugAggregateReportingJobHandler)
                .createReportJsonPayload(any(), any(), any());
        ReportingStatus status = new ReportingStatus();

        mSpyAggregateReportingJobHandler.performReport(
                aggregateReport.getId(), AggregateCryptoFixture.getKey(), status);

        assertEquals(ReportingStatus.UploadStatus.FAILURE, status.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.UNKNOWN, status.getFailureStatus());
        verify(mMeasurementDao, never()).markAggregateReportStatus(anyString(), anyInt());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_throwsUnknownExceptionNoSampling_logsAndSwallowsException()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport = createASampleAggregateReport();
        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);

        doReturn(true)
                .when(mMockFlags)
                .getMeasurementEnableReportingJobsThrowUnaccountedException();
        doReturn(0.0f).when(mMockFlags).getMeasurementThrowUnknownExceptionSamplingRate();
        doReturn(aggregateReport).when(mMeasurementDao).getAggregateReport(aggregateReport.getId());
        doThrow(new RuntimeException("unknown exception"))
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doReturn(aggregateReportBody)
                .when(mSpyDebugAggregateReportingJobHandler)
                .createReportJsonPayload(any(), any(), any());
        ReportingStatus status = new ReportingStatus();

        mSpyAggregateReportingJobHandler.performReport(
                aggregateReport.getId(), AggregateCryptoFixture.getKey(), status);

        assertEquals(ReportingStatus.UploadStatus.FAILURE, status.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.UNKNOWN, status.getFailureStatus());
        verify(mMeasurementDao, never()).markAggregateReportStatus(anyString(), anyInt());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_throwsUnknownExceptionEnabledToThrow_rethrowsException()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport = createASampleAggregateReport();

        doReturn(true)
                .when(mMockFlags)
                .getMeasurementEnableReportingJobsThrowUnaccountedException();
        doReturn(aggregateReport).when(mMeasurementDao).getAggregateReport(aggregateReport.getId());
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doThrow(new RuntimeException("unknown exception"))
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(any(), any(), any());
        doReturn(1.0f).when(mMockFlags).getMeasurementThrowUnknownExceptionSamplingRate();

        try {
            mSpyAggregateReportingJobHandler.performReport(
                    aggregateReport.getId(),
                    AggregateCryptoFixture.getKey(),
                    new ReportingStatus());
            fail();
        } catch (RuntimeException e) {
            assertEquals("unknown exception", e.getMessage());
        }

        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_throwsCryptoExceptionDisabledToThrow_logsAndSwallowsException()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport = createASampleAggregateReport();

        doReturn(false).when(mMockFlags).getMeasurementEnableReportingJobsThrowCryptoException();
        doReturn(aggregateReport).when(mMeasurementDao).getAggregateReport(aggregateReport.getId());
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doThrow(new CryptoException("exception message"))
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(any(), any(), any());
        ReportingStatus status = new ReportingStatus();

        mSpyAggregateReportingJobHandler.performReport(
                aggregateReport.getId(), AggregateCryptoFixture.getKey(), status);

        assertEquals(ReportingStatus.UploadStatus.FAILURE, status.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.ENCRYPTION_ERROR, status.getFailureStatus());
        verify(mMeasurementDao, never()).markAggregateReportStatus(anyString(), anyInt());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_throwsCryptoExceptionNoSampling_logsAndSwallowsException()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport = createASampleAggregateReport();

        doReturn(true).when(mMockFlags).getMeasurementEnableReportingJobsThrowCryptoException();
        doReturn(0.0f).when(mMockFlags).getMeasurementThrowUnknownExceptionSamplingRate();
        doReturn(aggregateReport).when(mMeasurementDao).getAggregateReport(aggregateReport.getId());
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doThrow(new CryptoException("exception message"))
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(any(), any(), any());
        ReportingStatus status = new ReportingStatus();

        mSpyAggregateReportingJobHandler.performReport(
                aggregateReport.getId(), AggregateCryptoFixture.getKey(), status);

        assertEquals(ReportingStatus.UploadStatus.FAILURE, status.getUploadStatus());
        assertEquals(ReportingStatus.FailureStatus.ENCRYPTION_ERROR, status.getFailureStatus());
        verify(mMeasurementDao, never()).markAggregateReportStatus(anyString(), anyInt());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_throwsCryptoExceptionEnabledToThrow_rethrowsException()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport = createASampleAggregateReport();

        doReturn(true).when(mMockFlags).getMeasurementEnableReportingJobsThrowCryptoException();
        doReturn(aggregateReport).when(mMeasurementDao).getAggregateReport(aggregateReport.getId());
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doThrow(new CryptoException("exception message"))
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(any(), any(), any());
        doReturn(1.0f).when(mMockFlags).getMeasurementThrowUnknownExceptionSamplingRate();

        try {
            mSpyAggregateReportingJobHandler.performReport(
                    aggregateReport.getId(),
                    AggregateCryptoFixture.getKey(),
                    new ReportingStatus());
            fail();
        } catch (CryptoException e) {
            assertEquals("exception message", e.getMessage());
        }

        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_normalReportWithDebugKeys_hasDebugModeEnabled()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                AggregateReportFixture.getValidAggregateReportBuilder()
                        .setSourceDebugKey(
                                AggregateReportFixture.ValidAggregateReportParams.SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(
                                AggregateReportFixture.ValidAggregateReportParams.TRIGGER_DEBUG_KEY)
                        .build();
        executeDebugModeVerification(aggregateReport, mSpyAggregateReportingJobHandler, "enabled");
        verify(mMeasurementDao, times(1))
                .markAggregateReportStatus(
                        eq(aggregateReport.getId()), eq(AggregateReport.Status.DELIVERED));
    }

    @Test
    public void performReport_normalReportWithOnlySourceDebugKey_hasDebugModeNull()
            throws DatastoreException, IOException, JSONException {
        // Setup
        AggregateReport aggregateReport =
                AggregateReportFixture.getValidAggregateReportBuilder()
                        .setSourceDebugKey(
                                AggregateReportFixture.ValidAggregateReportParams.SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(null)
                        .build();
        executeDebugModeVerification(aggregateReport, mSpyAggregateReportingJobHandler, "");
        verify(mMeasurementDao, times(1))
                .markAggregateReportStatus(
                        eq(aggregateReport.getId()), eq(AggregateReport.Status.DELIVERED));
    }

    @Test
    public void performReport_normalReportWithOnlyTriggerDebugKey_hasDebugModeNull()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                AggregateReportFixture.getValidAggregateReportBuilder()
                        .setSourceDebugKey(null)
                        .setTriggerDebugKey(
                                AggregateReportFixture.ValidAggregateReportParams.TRIGGER_DEBUG_KEY)
                        .build();
        executeDebugModeVerification(aggregateReport, mSpyAggregateReportingJobHandler, "");
        verify(mMeasurementDao, times(1))
                .markAggregateReportStatus(
                        eq(aggregateReport.getId()), eq(AggregateReport.Status.DELIVERED));
    }

    @Test
    public void performReport_normalReportWithNoDebugKey_hasDebugModeNull()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                AggregateReportFixture.getValidAggregateReportBuilder()
                        .setSourceDebugKey(null)
                        .setTriggerDebugKey(null)
                        .build();
        executeDebugModeVerification(aggregateReport, mSpyAggregateReportingJobHandler, "");
        verify(mMeasurementDao, times(1))
                .markAggregateReportStatus(
                        eq(aggregateReport.getId()), eq(AggregateReport.Status.DELIVERED));
    }

    @Test
    public void performReport_debugReportWithDebugKeys_hasDebugModeEnabled()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                AggregateReportFixture.getValidAggregateReportBuilder()
                        .setSourceDebugKey(
                                AggregateReportFixture.ValidAggregateReportParams.SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(
                                AggregateReportFixture.ValidAggregateReportParams.TRIGGER_DEBUG_KEY)
                        .build();
        executeDebugModeVerification(
                aggregateReport, mSpyDebugAggregateReportingJobHandler, "enabled");
        verify(mMeasurementDao, times(1))
                .markAggregateDebugReportDelivered(eq(aggregateReport.getId()));
    }

    @Test
    public void performReport_debugReportWithOnlySourceDebugKey_hasDebugModeNull()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                AggregateReportFixture.getValidAggregateReportBuilder()
                        .setSourceDebugKey(
                                AggregateReportFixture.ValidAggregateReportParams.SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(null)
                        .build();
        executeDebugModeVerification(aggregateReport, mSpyDebugAggregateReportingJobHandler, "");
        verify(mMeasurementDao, times(1))
                .markAggregateDebugReportDelivered(eq(aggregateReport.getId()));
    }

    @Test
    public void performReport_debugReportWithOnlyTriggerDebugKey_hasDebugModeNull()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                AggregateReportFixture.getValidAggregateReportBuilder()
                        .setSourceDebugKey(null)
                        .setTriggerDebugKey(
                                AggregateReportFixture.ValidAggregateReportParams.TRIGGER_DEBUG_KEY)
                        .build();
        executeDebugModeVerification(aggregateReport, mSpyDebugAggregateReportingJobHandler, "");
    }

    @Test
    public void performReport_debugReportWithNoDebugKey_hasDebugModeNull()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                AggregateReportFixture.getValidAggregateReportBuilder()
                        .setSourceDebugKey(null)
                        .setTriggerDebugKey(null)
                        .build();
        executeDebugModeVerification(aggregateReport, mSpyDebugAggregateReportingJobHandler, "");
        verify(mMeasurementDao, times(1))
                .markAggregateDebugReportDelivered(eq(aggregateReport.getId()));
    }

    @Test
    public void testSendReportForPendingReportSuccess_contextIdEnabled()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId(AGGREGATE_REPORT_ID)
                        .setStatus(AggregateReport.Status.PENDING)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .setTriggerContextId(TRIGGER_CONTEXT_ID)
                        .setApi(AggregateReportFixture.ValidAggregateReportParams.API)
                        .build();

        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);
        assertEquals(
                TRIGGER_CONTEXT_ID,
                aggregateReportBody.getString(
                        AggregateReportBody.PayloadBodyKeys.TRIGGER_CONTEXT_ID));

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(any(), eq(REPORTING_URI), any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(
                        aggregateReport.getId(), AggregateReport.Status.DELIVERED);
        ReportingStatus status = new ReportingStatus();

        mSpyAggregateReportingJobHandler.performReport(
                aggregateReport.getId(), AggregateCryptoFixture.getKey(), status);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, status.getUploadStatus());
        verify(mMeasurementDao, times(1)).markAggregateReportStatus(any(), anyInt());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void testCreateReportJsonPayload_roundSourceRegistrationTime() throws JSONException {
        long unroundedSourceRegistrationTime = 1674000000001L;
        String debugCleartextPayload =
                "{\"operation\":\"histogram\","
                        + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},{\"bucket\":\"3461\","
                        + "\"value\":1664}]}";
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId(AGGREGATE_REPORT_ID)
                        .setStatus(AggregateReport.Status.PENDING)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .setAttributionDestination(APP_DESTINATION)
                        .setSourceRegistrationTime(unroundedSourceRegistrationTime)
                        .setDebugCleartextPayload(debugCleartextPayload)
                        .setApi(AggregateReportFixture.ValidAggregateReportParams.API)
                        .build();

        JSONObject reportJson =
                mSpyAggregateReportingJobHandler.createReportJsonPayload(
                        aggregateReport, REPORTING_URI, AggregateCryptoFixture.getKey());

        JSONObject sharedInfo =
                new JSONObject(
                        reportJson.getString(AggregateReportBody.PayloadBodyKeys.SHARED_INFO));

        assertEquals(
                "1674000000",
                sharedInfo.getString(AggregateReportBody.SharedInfoKeys.SOURCE_REGISTRATION_TIME));
    }

    @Test
    public void createReportJsonPayload_adrReport_doesNotIncludeSRTIfNotSpecified()
            throws JSONException {
        String debugCleartextPayload =
                "{\"operation\":\"histogram\","
                        + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},{\"bucket\":\"3461\","
                        + "\"value\":1664}]}";
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId(AGGREGATE_REPORT_ID)
                        .setStatus(AggregateReport.Status.PENDING)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .setAttributionDestination(APP_DESTINATION)
                        .setSourceRegistrationTime(null)
                        .setApi(AggregateDebugReportApi.AGGREGATE_DEBUG_REPORT_API)
                        .setDebugCleartextPayload(debugCleartextPayload)
                        .build();

        JSONObject reportJson =
                mSpyAggregateReportingJobHandler.createReportJsonPayload(
                        aggregateReport, REPORTING_URI, AggregateCryptoFixture.getKey());

        JSONObject sharedInfo =
                new JSONObject(
                        reportJson.getString(AggregateReportBody.PayloadBodyKeys.SHARED_INFO));

        assertTrue(sharedInfo.isNull(AggregateReportBody.SharedInfoKeys.SOURCE_REGISTRATION_TIME));
    }

    @Test
    public void createReportJsonPayload_normalAggregateReport_doesNotIncludeSRTIfNotSpecified()
            throws JSONException {
        String debugCleartextPayload =
                "{\"operation\":\"histogram\","
                        + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},{\"bucket\":\"3461\","
                        + "\"value\":1664}]}";
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId(AGGREGATE_REPORT_ID)
                        .setStatus(AggregateReport.Status.PENDING)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .setAttributionDestination(APP_DESTINATION)
                        .setSourceRegistrationTime(null)
                        .setDebugCleartextPayload(debugCleartextPayload)
                        .setApi(AggregateReportFixture.ValidAggregateReportParams.API)
                        .build();

        JSONObject reportJson =
                mSpyAggregateReportingJobHandler.createReportJsonPayload(
                        aggregateReport, REPORTING_URI, AggregateCryptoFixture.getKey());

        JSONObject sharedInfo =
                new JSONObject(
                        reportJson.getString(AggregateReportBody.PayloadBodyKeys.SHARED_INFO));

        assertFalse(sharedInfo.has(AggregateReportBody.SharedInfoKeys.SOURCE_REGISTRATION_TIME));
    }

    @Test
    public void testSendReportSuccess_webDestination_hasTriggerDebugHeaderTrue()
            throws DatastoreException, IOException, JSONException {
        setUpTestForTriggerDebugAvailableHeader(
                EventSurfaceType.WEB, /* hasArDebugPermission= */ true);
        ReportingStatus status = new ReportingStatus();

        mSpyAggregateReportingJobHandler.performReport(
                AGGREGATE_REPORT_ID, AggregateCryptoFixture.getKey(), status);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, status.getUploadStatus());
        verify(mMeasurementDao, times(1))
                .markAggregateReportStatus(AGGREGATE_REPORT_ID, AggregateReport.Status.DELIVERED);
        verify(mTransaction, times(4)).begin();
        verify(mTransaction, times(4)).end();
        verify(mSpyAggregateReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(Boolean.TRUE), anyString());
    }

    @Test
    public void testSendReportSuccess_webDestination_hasTriggerDebugHeaderFalse()
            throws DatastoreException, IOException, JSONException {
        setUpTestForTriggerDebugAvailableHeader(
                EventSurfaceType.WEB, /* hasArDebugPermission= */ false);
        ReportingStatus status = new ReportingStatus();

        mSpyAggregateReportingJobHandler.performReport(
                AGGREGATE_REPORT_ID, AggregateCryptoFixture.getKey(), status);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, status.getUploadStatus());
        verify(mMeasurementDao, times(1))
                .markAggregateReportStatus(AGGREGATE_REPORT_ID, AggregateReport.Status.DELIVERED);
        verify(mTransaction, times(4)).begin();
        verify(mTransaction, times(4)).end();
        verify(mSpyAggregateReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(Boolean.FALSE), anyString());
    }

    @Test
    public void getReportUriPath_adrInstance_returnsPathAsExpected() {
        Truth.assertThat(
                        mSpyDebugAggregateReportingJobHandler.getReportUriPath(
                                AggregateDebugReportApi.AGGREGATE_DEBUG_REPORT_API))
                .isEqualTo(AggregateReportingJobHandler.AGGREGATE_DEBUG_REPORT_URI_PATH);
    }

    @Test
    public void getReportUriPath_debugAggregateReport_returnsPathAsExpected() {
        Truth.assertThat(
                        mSpyDebugAggregateReportingJobHandler.getReportUriPath(
                                AggregateReportFixture.ValidAggregateReportParams.API))
                .isEqualTo(
                        AggregateReportingJobHandler.DEBUG_AGGREGATE_ATTRIBUTION_REPORT_URI_PATH);
    }

    @Test
    public void getReportUriPath_aggregateReport_returnsPathAsExpected() {
        Truth.assertThat(
                        mSpyAggregateReportingJobHandler.getReportUriPath(
                                AggregateReportFixture.ValidAggregateReportParams.API))
                .isEqualTo(AggregateReportingJobHandler.AGGREGATE_ATTRIBUTION_REPORT_URI_PATH);
    }

    @Test
    public void getReportUriPath_aggregateReport_defaultUri() {
        Truth.assertThat(mSpyAggregateReportingJobHandler.getReportUriPath("some-random-api"))
                .isEqualTo(AggregateReportingJobHandler.AGGREGATE_ATTRIBUTION_REPORT_URI_PATH);
    }

    @Test
    public void testSendReportSuccess_appDestination_noTriggerDebugHeader()
            throws DatastoreException, IOException, JSONException {
        setUpTestForTriggerDebugAvailableHeader(
                EventSurfaceType.APP, /* hasArDebugPermission= */ true);
        ReportingStatus status = new ReportingStatus();

        mSpyAggregateReportingJobHandler.performReport(
                AGGREGATE_REPORT_ID, AggregateCryptoFixture.getKey(), status);

        assertEquals(ReportingStatus.UploadStatus.SUCCESS, status.getUploadStatus());
        verify(mMeasurementDao, times(1))
                .markAggregateReportStatus(AGGREGATE_REPORT_ID, AggregateReport.Status.DELIVERED);
        verify(mTransaction, times(4)).begin();
        verify(mTransaction, times(4)).end();
        verify(mSpyAggregateReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());
    }

    @Test
    public void createReportJsonPayload_givenAggregateReport_aggregateReportBodyGenerated()
            throws JSONException {
        // Setup
        AggregateReport build =
                AggregateReportFixture.getValidAggregateReportBuilder()
                        .setApi(API_ATTRIBUTION_REPORTING_DEUB)
                        .build();

        // Execution
        JSONObject actualBody =
                mSpyAggregateReportingJobHandler.createReportJsonPayload(
                        build, REPORTING_URI, AggregateCryptoFixture.getKey());

        // Assertion
        JSONObject sharedInfoObject =
                new JSONObject(
                        actualBody.getString(AggregateReportBody.PayloadBodyKeys.SHARED_INFO));
        Truth.assertThat(sharedInfoObject.getString(AggregateReportBody.SharedInfoKeys.API_NAME))
                .isEqualTo(build.getApi());
        Truth.assertThat(
                        sharedInfoObject.getString(
                                AggregateReportBody.SharedInfoKeys.ATTRIBUTION_DESTINATION))
                .isEqualTo(build.getAttributionDestination().toString());
        Truth.assertThat(sharedInfoObject.getString(AggregateReportBody.SharedInfoKeys.REPORT_ID))
                .isEqualTo(build.getId());
        Truth.assertThat(
                        sharedInfoObject.getString(
                                AggregateReportBody.SharedInfoKeys.REPORTING_ORIGIN))
                .isEqualTo(REPORTING_URI.toString());
        Truth.assertThat(
                        String.valueOf(
                                sharedInfoObject.getLong(
                                        AggregateReportBody.SharedInfoKeys.SCHEDULED_REPORT_TIME)))
                .isEqualTo(
                        String.valueOf(
                                TimeUnit.MILLISECONDS.toSeconds(build.getScheduledReportTime())));
        Truth.assertThat(
                        sharedInfoObject.getString(
                                AggregateReportBody.SharedInfoKeys.SOURCE_REGISTRATION_TIME))
                .isEqualTo(
                        String.valueOf(
                                TimeUnit.MILLISECONDS.toSeconds(
                                        roundDownToDay(build.getSourceRegistrationTime()))));

        Truth.assertThat(actualBody.getString(AggregateReportBody.PayloadBodyKeys.SOURCE_DEBUG_KEY))
                .isEqualTo(String.valueOf(build.getSourceDebugKey()));
        Truth.assertThat(
                        actualBody.getString(AggregateReportBody.PayloadBodyKeys.TRIGGER_DEBUG_KEY))
                .isEqualTo(String.valueOf(build.getTriggerDebugKey()));
        Truth.assertThat(
                        actualBody.getString(
                                AggregateReportBody.PayloadBodyKeys.AGGREGATION_COORDINATOR_ORIGIN))
                .isEqualTo(build.getAggregationCoordinatorOrigin().toString());
    }

    private void executeDebugModeVerification(
            AggregateReport aggregateReport,
            AggregateReportingJobHandler aggregateReportingJobHandler,
            String expectedDebugMode)
            throws DatastoreException, IOException, JSONException {
        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(aggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), eq(null), anyString());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(
                        aggregateReport.getId(), AggregateReport.Status.DELIVERED);
        ArgumentCaptor<JSONObject> aggregateReportBodyCaptor =
                ArgumentCaptor.forClass(JSONObject.class);

        // Execution
        ReportingStatus status = new ReportingStatus();
        aggregateReportingJobHandler.performReport(
                aggregateReport.getId(), AggregateCryptoFixture.getKey(), status);

        // Assertion
        assertEquals(ReportingStatus.UploadStatus.SUCCESS, status.getUploadStatus());
        verify(aggregateReportingJobHandler)
                .makeHttpPostRequest(
                        eq(REPORTING_URI),
                        aggregateReportBodyCaptor.capture(),
                        eq(null),
                        anyString());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();

        JSONObject aggregateReportBody = aggregateReportBodyCaptor.getValue();
        JSONObject sharedInfo =
                new JSONObject(
                        aggregateReportBody.getString(
                                AggregateReportBody.PayloadBodyKeys.SHARED_INFO));
        assertEquals(
                expectedDebugMode,
                sharedInfo.optString(AggregateReportBody.SharedInfoKeys.DEBUG_MODE));
    }

    private JSONObject createASampleAggregateReportBody(AggregateReport aggregateReport)
            throws JSONException {
        return new AggregateReportBody.Builder()
                .setReportId(aggregateReport.getId())
                .setDebugCleartextPayload(CLEARTEXT_PAYLOAD)
                .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                .setTriggerContextId(TRIGGER_CONTEXT_ID)
                .setReportingOrigin(REPORTING_URI.toString())
                .build()
                .toJson(AggregateCryptoFixture.getKey(), mMockFlags);
    }

    private static AggregateReport createASampleAggregateReport() {
        return new AggregateReport.Builder()
                .setId(AGGREGATE_REPORT_ID)
                .setStatus(AggregateReport.Status.PENDING)
                .setScheduledReportTime(1000L)
                .setEnrollmentId(ENROLLMENT_ID)
                .setRegistrationOrigin(REPORTING_URI)
                .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                .setApi(AggregateReportFixture.ValidAggregateReportParams.API)
                .build();
    }

    private void setUpTestForTriggerDebugAvailableHeader(
            @EventSurfaceType int destinationType, boolean hasArDebugPermission)
            throws DatastoreException, IOException, JSONException {
        when(mMockFlags.getMeasurementEnableTriggerDebugSignal()).thenReturn(true);
        Uri testUri =
                destinationType == EventSurfaceType.APP ? APP_DESTINATION : DEFAULT_WEB_DESTINATION;
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId(AGGREGATE_REPORT_ID)
                        .setStatus(AggregateReport.Status.PENDING)
                        .setSourceId(SOURCE_ID)
                        .setTriggerId(TRIGGER_ID)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .setAttributionDestination(testUri)
                        .setApi(AggregateReportFixture.ValidAggregateReportParams.API)
                        .build();
        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);
        assertEquals(
                aggregateReportBody.getString("aggregation_coordinator_origin"),
                COORDINATOR_ORIGIN.toString());
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId(TRIGGER_ID)
                        .setDestinationType(destinationType)
                        .setAttributionDestination(testUri)
                        .setArDebugPermission(hasArDebugPermission)
                        .build();

        when(mMeasurementDao.getAggregateReport(AGGREGATE_REPORT_ID)).thenReturn(aggregateReport);
        when(mMeasurementDao.getTrigger(TRIGGER_ID)).thenReturn(trigger);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), any(), any(), anyString());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(any(), eq(REPORTING_URI), any());
        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(AGGREGATE_REPORT_ID, AggregateReport.Status.DELIVERED);
    }
}
