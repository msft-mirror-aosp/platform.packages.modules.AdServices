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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.data.measurement.ITransaction;
import com.android.adservices.service.measurement.EventReport;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;

/** Unit test for {@link EventReportingJobHandler} */
@RunWith(MockitoJUnitRunner.class)
public class EventReportingJobHandlerTest {

    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    DatastoreManager mDatastoreManager;

    @Mock IMeasurementDao mMeasurementDao;

    @Mock ITransaction mTransaction;

    EventReportingJobHandler mEventReportingJobHandler;
    EventReportingJobHandler mSpyEventReportingJobHandler;

    class FakeDatasoreManager extends DatastoreManager {

        @Override
        public ITransaction createNewTransaction() {
            return mTransaction;
        }

        @Override
        public IMeasurementDao getMeasurementDao() {
            return mMeasurementDao;
        }
    }

    @Before
    public void setUp() {
        mDatastoreManager = new FakeDatasoreManager();
        mEventReportingJobHandler = new EventReportingJobHandler(mDatastoreManager);
        mSpyEventReportingJobHandler = Mockito.spy(mEventReportingJobHandler);
    }

    @Test
    public void testSendReportForPendingReportSuccess()
            throws DatastoreException, IOException, JSONException {
        EventReport eventReport =
                new EventReport.Builder()
                        .setId("eventReportId")
                        .setStatus(EventReport.Status.PENDING)
                        .setAdTechDomain(Uri.parse("https://adtech.domain"))
                        .build();
        JSONObject eventReportPayload =
                new EventReportPayload.Builder().setReportId(eventReport.getId()).build().toJson();

        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(Mockito.any(), Mockito.any());
        doReturn(eventReportPayload)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(Mockito.any());

        doNothing().when(mMeasurementDao).markAggregateReportDelivered(eventReport.getId());
        Assert.assertEquals(
                EventReportingJobHandler.PerformReportResult.SUCCESS,
                mSpyEventReportingJobHandler.performReport(eventReport.getId()));

        verify(mMeasurementDao, times(1)).markEventReportDelivered(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void testSendReportForPendingReportFailure()
            throws DatastoreException, IOException, JSONException {
        EventReport eventReport =
                new EventReport.Builder()
                        .setId("eventReportId")
                        .setStatus(EventReport.Status.PENDING)
                        .setAdTechDomain(Uri.parse("https://adtech.domain"))
                        .build();
        JSONObject eventReportPayload =
                new EventReportPayload.Builder().setReportId(eventReport.getId()).build().toJson();

        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);
        doReturn(HttpURLConnection.HTTP_BAD_REQUEST)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(Mockito.any(), Mockito.any());
        doReturn(eventReportPayload)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(Mockito.any());

        Assert.assertEquals(
                EventReportingJobHandler.PerformReportResult.POST_REQUEST_ERROR,
                mSpyEventReportingJobHandler.performReport(eventReport.getId()));

        verify(mMeasurementDao, never()).markEventReportDelivered(any());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void testSendReportForAlreadyDeliveredReport() throws DatastoreException {
        EventReport eventReport =
                new EventReport.Builder()
                        .setId("eventReportId")
                        .setStatus(EventReport.Status.DELIVERED)
                        .setAdTechDomain(Uri.parse("https://adtech.domain"))
                        .build();

        when(mMeasurementDao.getEventReport(eventReport.getId())).thenReturn(eventReport);
        Assert.assertEquals(
                EventReportingJobHandler.PerformReportResult.ALREADY_DELIVERED,
                mSpyEventReportingJobHandler.performReport(eventReport.getId()));

        verify(mMeasurementDao, never()).markEventReportDelivered(any());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void testPerformScheduledPendingReportsForMultipleReports()
            throws DatastoreException, IOException, JSONException {
        EventReport eventReport1 =
                new EventReport.Builder()
                        .setId("eventReport1")
                        .setStatus(EventReport.Status.PENDING)
                        .setAdTechDomain(Uri.parse("https://adtech.domain"))
                        .setReportTime(1000L)
                        .build();
        JSONObject eventReportPayload1 =
                new EventReportPayload.Builder().setReportId(eventReport1.getId()).build().toJson();
        EventReport eventReport2 =
                new EventReport.Builder()
                        .setId("eventReport2")
                        .setStatus(EventReport.Status.PENDING)
                        .setAdTechDomain(Uri.parse("https://adtech.domain"))
                        .setReportTime(1100L)
                        .build();
        JSONObject eventReportPayload2 =
                new EventReportPayload.Builder().setReportId(eventReport2.getId()).build().toJson();

        when(mMeasurementDao.getPendingEventReportIdsInWindow(1000, 1100))
                .thenReturn(List.of(eventReport1.getId(), eventReport2.getId()));
        when(mMeasurementDao.getEventReport(eventReport1.getId())).thenReturn(eventReport1);
        when(mMeasurementDao.getEventReport(eventReport2.getId())).thenReturn(eventReport2);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(any(), any());
        doReturn(eventReportPayload1)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(eventReport1);
        doReturn(eventReportPayload2)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(eventReport2);

        Assert.assertTrue(
                mSpyEventReportingJobHandler.performScheduledPendingReportsInWindow(1000, 1100));

        verify(mMeasurementDao, times(2)).markEventReportDelivered(any());
        verify(mTransaction, times(5)).begin();
        verify(mTransaction, times(5)).end();
    }

    @Test
    public void testPerformAllPendingReportsForGivenAppForMultipleReports()
            throws DatastoreException, IOException, JSONException {
        EventReport eventReport1 =
                new EventReport.Builder()
                        .setId("eventReport1")
                        .setStatus(EventReport.Status.PENDING)
                        .setAdTechDomain(Uri.parse("https://adtech.domain"))
                        .build();
        JSONObject eventReportPayload1 =
                new EventReportPayload.Builder().setReportId(eventReport1.getId()).build().toJson();
        EventReport eventReport2 =
                new EventReport.Builder()
                        .setId("eventReport2")
                        .setStatus(EventReport.Status.PENDING)
                        .setAdTechDomain(Uri.parse("https://adtech.domain"))
                        .build();
        JSONObject eventReportPayload2 =
                new EventReportPayload.Builder().setReportId(eventReport2.getId()).build().toJson();

        when(mMeasurementDao.getPendingEventReportIdsForGivenApp(
                        Uri.parse("https://adtech.domain")))
                .thenReturn(List.of(eventReport1.getId(), eventReport2.getId()));
        when(mMeasurementDao.getEventReport(eventReport1.getId())).thenReturn(eventReport1);
        when(mMeasurementDao.getEventReport(eventReport2.getId())).thenReturn(eventReport2);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyEventReportingJobHandler)
                .makeHttpPostRequest(any(), any());
        doReturn(eventReportPayload1)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(eventReport1);
        doReturn(eventReportPayload2)
                .when(mSpyEventReportingJobHandler)
                .createReportJsonPayload(eventReport2);

        Assert.assertTrue(
                mSpyEventReportingJobHandler.performAllPendingReportsForGivenApp(
                        Uri.parse("https://adtech.domain")));

        verify(mMeasurementDao, times(2)).markEventReportDelivered(any());
        verify(mTransaction, times(5)).begin();
        verify(mTransaction, times(5)).end();
    }
}
