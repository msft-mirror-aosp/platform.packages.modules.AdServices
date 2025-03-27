/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.data.measurement.ITransaction;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.CountUniqueReport;
import com.android.adservices.service.measurement.CountUniqueReportFixture;
import com.android.adservices.service.measurement.aggregation.AggregateCryptoFixture;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKeyManager;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

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

/** Unit test for {@link CountUniqueReportingJobHandler} */
@RunWith(MockitoJUnitRunner.class)
public class CountUniqueReportingJobHandlerTest {
    // private static final fields

    // mocked stuff
    @Mock IMeasurementDao mMeasurementDao;

    @Mock ITransaction mTransaction;
    @Mock Flags mMockFlags;
    @Mock AdServicesLogger mLogger;
    @Mock AdServicesErrorLogger mErrorLogger;
    @Mock PackageManager mPackageManager;
    protected static Context sContext;
    DatastoreManager mDatastoreManager;

    CountUniqueReportingJobHandler mCountUniqueReportingJobHandler;
    CountUniqueReportingJobHandler mSpyCountUniqueReportingJobHandler;

    // setup

    @Rule
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(FlagsFactory.class)
                    .spyStatic(ErrorLogUtil.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    class FakeDatastoreManager extends DatastoreManager {

        FakeDatastoreManager() {
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
        mDatastoreManager = new FakeDatastoreManager();
        mCountUniqueReportingJobHandler =
                new CountUniqueReportingJobHandler(
                        mDatastoreManager, mockKeyManager, mMockFlags, sContext);
        mSpyCountUniqueReportingJobHandler = Mockito.spy(mCountUniqueReportingJobHandler);
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        when(mMockFlags.getMeasurementAggregationCoordinatorOriginEnabled()).thenReturn(true);
        when(mMockFlags.getMeasurementEnableAppPackageNameLogging()).thenReturn(true);
        when(mMockFlags.getMeasurementDefaultAggregationCoordinatorOrigin())
                .thenReturn("https://publickeyservice.msmt.aws.privacysandboxservices.com");
        when(mMockFlags.getMeasurementEnableCountUniqueService()).thenReturn(true);
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(anyInt(), anyInt()));
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
    }

    @Test
    public void testSendReportForPendingReportSuccess()
            throws DatastoreException, IOException, JSONException {
        CountUniqueReport countUniqueReport =
                CountUniqueReportFixture.getValidCountUniqueReportBuilder().build();
        when(mMeasurementDao.getCountUniqueReport(countUniqueReport.getReportId()))
                .thenReturn(countUniqueReport);
        JSONObject countUniqueReportBody = createSampleCountUniqueReportBody(countUniqueReport);

        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyCountUniqueReportingJobHandler)
                .makeHttpPostRequest(eq(countUniqueReport.getReportingOrigin()), any());
        doReturn(countUniqueReportBody)
                .when(mSpyCountUniqueReportingJobHandler)
                .createReportJsonPayload(any(), any());
        doNothing()
                .when(mMeasurementDao)
                .markCountUniqueReportStatus(
                        countUniqueReport.getReportId(),
                        CountUniqueReport.ReportDeliveryStatus.DELIVERED);

        mSpyCountUniqueReportingJobHandler.performReport(
                countUniqueReport.getReportId(), AggregateCryptoFixture.getKey());

        verify(mMeasurementDao, times(1))
                .markCountUniqueReportStatus(
                        countUniqueReport.getReportId(),
                        CountUniqueReport.ReportDeliveryStatus.DELIVERED);
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void testSendReportForPendingReportFailure()
            throws DatastoreException, JSONException, IOException {
        CountUniqueReport countUniqueReport =
                CountUniqueReportFixture.getValidCountUniqueReportBuilder().build();
        when(mMeasurementDao.getCountUniqueReport(countUniqueReport.getReportId()))
                .thenReturn(countUniqueReport);
        JSONObject countUniqueReportBody = createSampleCountUniqueReportBody(countUniqueReport);

        doReturn(HttpURLConnection.HTTP_BAD_REQUEST)
                .when(mSpyCountUniqueReportingJobHandler)
                .makeHttpPostRequest(eq(countUniqueReport.getReportingOrigin()), any());
        doReturn(countUniqueReportBody)
                .when(mSpyCountUniqueReportingJobHandler)
                .createReportJsonPayload(any(), any());
        doNothing()
                .when(mMeasurementDao)
                .markCountUniqueReportStatus(
                        countUniqueReport.getReportId(),
                        CountUniqueReport.ReportDeliveryStatus.DELIVERED);

        mSpyCountUniqueReportingJobHandler.performReport(
                countUniqueReport.getReportId(), AggregateCryptoFixture.getKey());

        verify(mMeasurementDao, never())
                .markCountUniqueReportStatus(
                        countUniqueReport.getReportId(),
                        CountUniqueReport.ReportDeliveryStatus.DELIVERED);
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void testSendingReportForPendingReportAlreadyDelivered() throws DatastoreException {
        CountUniqueReport countUniqueReport =
                CountUniqueReportFixture.getValidCountUniqueReportBuilder()
                        .setStatus(CountUniqueReport.ReportDeliveryStatus.DELIVERED)
                        .build();
        when(mMeasurementDao.getCountUniqueReport(countUniqueReport.getReportId()))
                .thenReturn(countUniqueReport);

        mSpyCountUniqueReportingJobHandler.performReport(
                countUniqueReport.getReportId(), AggregateCryptoFixture.getKey());

        verify(mMeasurementDao, never())
                .markCountUniqueReportStatus(
                        countUniqueReport.getReportId(),
                        CountUniqueReport.ReportDeliveryStatus.DELIVERED);
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void testPerformScheduledPendingReportsForMultipleReports()
            throws JSONException, DatastoreException, IOException {
        CountUniqueReport countUniqueReport1 =
                CountUniqueReportFixture.getValidCountUniqueReportBuilder()
                        .setReportId("R1")
                        .build();
        CountUniqueReport countUniqueReport2 =
                CountUniqueReportFixture.getValidCountUniqueReportBuilder()
                        .setReportId("R2")
                        .build();

        JSONObject body1 = createSampleCountUniqueReportBody(countUniqueReport1);
        JSONObject body2 = createSampleCountUniqueReportBody(countUniqueReport2);

        when(mMeasurementDao.getPendingCountUniqueReportIds())
                .thenReturn(
                        List.of(
                                countUniqueReport1.getReportId(),
                                countUniqueReport2.getReportId()));
        when(mMeasurementDao.getCountUniqueReport(countUniqueReport1.getReportId()))
                .thenReturn(countUniqueReport1);
        when(mMeasurementDao.getCountUniqueReport(countUniqueReport2.getReportId()))
                .thenReturn(countUniqueReport2);

        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyCountUniqueReportingJobHandler)
                .makeHttpPostRequest(any(), any());

        doReturn(body1)
                .when(mSpyCountUniqueReportingJobHandler)
                .createReportJsonPayload(eq(countUniqueReport1), any());
        doReturn(body2)
                .when(mSpyCountUniqueReportingJobHandler)
                .createReportJsonPayload(eq(countUniqueReport2), any());

        assertThat(mSpyCountUniqueReportingJobHandler.performScheduledPendingReports()).isTrue();

        verify(mMeasurementDao, times(1))
                .markCountUniqueReportStatus(
                        countUniqueReport1.getReportId(),
                        CountUniqueReport.ReportDeliveryStatus.DELIVERED);
        verify(mMeasurementDao, times(1))
                .markCountUniqueReportStatus(
                        countUniqueReport2.getReportId(),
                        CountUniqueReport.ReportDeliveryStatus.DELIVERED);
        verify(mTransaction, times(5)).begin();
        verify(mTransaction, times(5)).end();
    }

    @Test
    public void testPerformScheduledPendingReports_noKeys()
            throws JSONException, DatastoreException, IOException {
        CountUniqueReport countUniqueReport =
                CountUniqueReportFixture.getValidCountUniqueReportBuilder()
                        .setStatus(CountUniqueReport.ReportDeliveryStatus.DELIVERED)
                        .build();
        JSONObject countUniqueReportBody = createSampleCountUniqueReportBody(countUniqueReport);

        when(mMeasurementDao.getPendingCountUniqueReportIds())
                .thenReturn(List.of(countUniqueReport.getReportId()));
        when(mMeasurementDao.getCountUniqueReport(countUniqueReport.getReportId()))
                .thenReturn(countUniqueReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyCountUniqueReportingJobHandler)
                .makeHttpPostRequest(any(), any());

        doReturn(countUniqueReportBody)
                .when(mSpyCountUniqueReportingJobHandler)
                .createReportJsonPayload(eq(countUniqueReport), any());

        AggregateEncryptionKeyManager mockKeyManager = mock(AggregateEncryptionKeyManager.class);
        when(mockKeyManager.getAggregateEncryptionKeys(any(), anyInt()))
                .thenReturn(Collections.emptyList());
        mCountUniqueReportingJobHandler =
                new CountUniqueReportingJobHandler(
                        mDatastoreManager, mockKeyManager, mMockFlags, sContext);
        mSpyCountUniqueReportingJobHandler = Mockito.spy(mCountUniqueReportingJobHandler);

        assertThat(mSpyCountUniqueReportingJobHandler.performScheduledPendingReports()).isTrue();

        verify(mMeasurementDao, never()).markCountUniqueReportStatus(any(), anyInt());
    }

    @Test
    public void testPerformScheduledPendingReportsInWindow_threadInterrupted()
            throws JSONException, DatastoreException, IOException {
        CountUniqueReport countUniqueReport1 =
                CountUniqueReportFixture.getValidCountUniqueReportBuilder()
                        .setReportId("R1")
                        .build();
        CountUniqueReport countUniqueReport2 =
                CountUniqueReportFixture.getValidCountUniqueReportBuilder()
                        .setReportId("R2")
                        .build();

        JSONObject body1 = createSampleCountUniqueReportBody(countUniqueReport1);
        JSONObject body2 = createSampleCountUniqueReportBody(countUniqueReport2);

        when(mMeasurementDao.getPendingCountUniqueReportIds())
                .thenReturn(
                        List.of(
                                countUniqueReport1.getReportId(),
                                countUniqueReport2.getReportId()));
        when(mMeasurementDao.getCountUniqueReport(countUniqueReport1.getReportId()))
                .thenReturn(countUniqueReport1);
        when(mMeasurementDao.getCountUniqueReport(countUniqueReport2.getReportId()))
                .thenReturn(countUniqueReport2);

        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyCountUniqueReportingJobHandler)
                .makeHttpPostRequest(any(), any());

        doReturn(body1)
                .when(mSpyCountUniqueReportingJobHandler)
                .createReportJsonPayload(eq(countUniqueReport1), any());
        doReturn(body2)
                .when(mSpyCountUniqueReportingJobHandler)
                .createReportJsonPayload(eq(countUniqueReport2), any());

        Thread.currentThread().interrupt();
        assertThat(mSpyCountUniqueReportingJobHandler.performScheduledPendingReports()).isTrue();

        // 0 reports processed, since the thread exits early.
        verify(mMeasurementDao, times(0)).markCountUniqueReportStatus(any(), anyInt());

        // 1 transaction for initial retrieval of pending report ids.
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    private JSONObject createSampleCountUniqueReportBody(CountUniqueReport countUniqueReport)
            throws JSONException {
        return new CountUniqueReportBody.Builder()
                .setReportId(countUniqueReport.getReportId())
                .setDebugCleartextPayload(countUniqueReport.getPayload())
                .setContextId(countUniqueReport.getContextId())
                .setReportingOrigin(countUniqueReport.getReportingOrigin())
                .setApi(CountUniqueReportingJobHandler.API_NAME)
                .setApiVersion(countUniqueReport.getApiVersion())
                .setScheduledReportTime(countUniqueReport.getScheduledReportTime())
                .build()
                .toJson(AggregateCryptoFixture.getKey(), mMockFlags);
    }
}
