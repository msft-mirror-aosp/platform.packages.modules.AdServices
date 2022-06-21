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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.SystemHealthParams;
import com.android.adservices.service.measurement.aggregation.CleartextAggregatePayload;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.HttpURLConnection;

public class AggregateReportingJobHandlerTest {
    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private final long mScheduledReportTimeMs = 20000000L;
    private final long mSourceRegistrationTimeMs = 19999000L;

    private final CleartextAggregatePayload mPendingAggregateReportDeadlineReached1 =
            new CleartextAggregatePayload.Builder()
                    .setPublisher(Uri.parse("https://source.site"))
                    .setAttributionDestination(Uri.parse("https://attribution.destination"))
                    .setId("AR1")
                    .setScheduledReportTime(mScheduledReportTimeMs)
                    .setSourceRegistrationTime(mSourceRegistrationTimeMs)
                    .setPrivacyBudgetKey("null")
                    .setReportingOrigin(Uri.parse("https://adtech.domain"))
                    .setDebugCleartextPayload("{\"operation\":\"histogram\","
                            + "\"data\":[{\"bucket\":1369,\"value\":32768},{\"bucket\":3461,"
                            + "\"value\":1664}]}")
                    .setStatus(CleartextAggregatePayload.Status.PENDING)
                    .build();

    private final CleartextAggregatePayload mDeliveredAggregateReport =
            new CleartextAggregatePayload.Builder()
                    .setPublisher(Uri.parse("https://source.site"))
                    .setAttributionDestination(Uri.parse("https://attribution.destination"))
                    .setId("AR2")
                    .setScheduledReportTime(mScheduledReportTimeMs)
                    .setSourceRegistrationTime(mSourceRegistrationTimeMs)
                    .setPrivacyBudgetKey("null")
                    .setReportingOrigin(Uri.parse("https://adtech.domain"))
                    .setDebugCleartextPayload("{\"operation\":\"histogram\","
                            + "\"data\":[{\"bucket\":1369,\"value\":32768},{\"bucket\":3461,"
                            + "\"value\":1664}]}")
                    .setStatus(CleartextAggregatePayload.Status.DELIVERED)
                    .build();

    private final CleartextAggregatePayload mPendingAggregateReportDeadlineReached2 =
            new CleartextAggregatePayload.Builder()
                    .setPublisher(Uri.parse("https://source.site"))
                    .setAttributionDestination(Uri.parse("https://attribution.destination"))
                    .setId("AR10")
                    .setScheduledReportTime(mScheduledReportTimeMs - 1000)
                    .setSourceRegistrationTime(mSourceRegistrationTimeMs)
                    .setPrivacyBudgetKey("null")
                    .setReportingOrigin(Uri.parse("https://adtech.domain"))
                    .setDebugCleartextPayload("{\"operation\":\"histogram\","
                            + "\"data\":[{\"bucket\":1369,\"value\":32768},{\"bucket\":3461,"
                            + "\"value\":1664}]}")
                    .setStatus(CleartextAggregatePayload.Status.PENDING)
                    .build();

    private final CleartextAggregatePayload mPendingAggregateReportDeadlineNotReached1 =
            new CleartextAggregatePayload.Builder()
                    .setPublisher(Uri.parse("https://source.site"))
                    .setAttributionDestination(Uri.parse("https://attribution.destination"))
                    .setId("AR11")
                    .setScheduledReportTime(mScheduledReportTimeMs + 20)
                    .setSourceRegistrationTime(mSourceRegistrationTimeMs)
                    .setPrivacyBudgetKey("null")
                    .setReportingOrigin(Uri.parse("https://adtech.domain"))
                    .setDebugCleartextPayload("{\"operation\":\"histogram\","
                            + "\"data\":[{\"bucket\":1369,\"value\":32768},{\"bucket\":3461,"
                            + "\"value\":1664}]}")
                    .setStatus(CleartextAggregatePayload.Status.PENDING)
                    .build();

    private final CleartextAggregatePayload mPendingAggregateReportOutsideWindow =
            new CleartextAggregatePayload.Builder()
                    .setPublisher(Uri.parse("https://source.site"))
                    .setAttributionDestination(Uri.parse("https://attribution.destination"))
                    .setId("AR12")
                    .setScheduledReportTime(mScheduledReportTimeMs
                            - SystemHealthParams.MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS - 1)
                    .setSourceRegistrationTime(mSourceRegistrationTimeMs)
                    .setPrivacyBudgetKey("null")
                    .setReportingOrigin(Uri.parse("https://adtech.domain"))
                    .setDebugCleartextPayload("{\"operation\":\"histogram\","
                            + "\"data\":[{\"bucket\":1369,\"value\":32768},{\"bucket\":3461,"
                            + "\"value\":1664}]}")
                    .setStatus(CleartextAggregatePayload.Status.PENDING)
                    .build();

    private final CleartextAggregatePayload mPendingAggregateReportFromSpecificSource1 =
            new CleartextAggregatePayload.Builder()
                    .setPublisher(Uri.parse("android-app://source.app1"))
                    .setAttributionDestination(Uri.parse("https://attribution.destination"))
                    .setId("AR100")
                    .setScheduledReportTime(mScheduledReportTimeMs
                            - SystemHealthParams.MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS)
                    .setSourceRegistrationTime(mSourceRegistrationTimeMs)
                    .setPrivacyBudgetKey("null")
                    .setReportingOrigin(Uri.parse("https://adtech.domain"))
                    .setDebugCleartextPayload("{\"operation\":\"histogram\","
                            + "\"data\":[{\"bucket\":1369,\"value\":32768},{\"bucket\":3461,"
                            + "\"value\":1664}]}")
                    .setStatus(CleartextAggregatePayload.Status.PENDING)
                    .build();

    private final CleartextAggregatePayload mPendingAggregateReportFromSpecificSource2 =
            new CleartextAggregatePayload.Builder()
                    .setPublisher(Uri.parse("android-app://source.app2"))
                    .setAttributionDestination(Uri.parse("https://attribution.destination"))
                    .setId("AR101")
                    .setScheduledReportTime(mScheduledReportTimeMs
                            - SystemHealthParams.MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS)
                    .setSourceRegistrationTime(mSourceRegistrationTimeMs)
                    .setPrivacyBudgetKey("null")
                    .setReportingOrigin(Uri.parse("https://adtech.domain"))
                    .setDebugCleartextPayload("{\"operation\":\"histogram\","
                            + "\"data\":[{\"bucket\":1369,\"value\":32768},{\"bucket\":3461,"
                            + "\"value\":1664}]}")
                    .setStatus(CleartextAggregatePayload.Status.PENDING)
                    .build();

    private final CleartextAggregatePayload mDeliveredAggregateReportFromSpecificSource =
            new CleartextAggregatePayload.Builder()
                    .setPublisher(Uri.parse("android-app://source.app1"))
                    .setAttributionDestination(Uri.parse("https://attribution.destination"))
                    .setId("AR102")
                    .setScheduledReportTime(mScheduledReportTimeMs
                            - SystemHealthParams.MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS)
                    .setSourceRegistrationTime(mSourceRegistrationTimeMs)
                    .setPrivacyBudgetKey("null")
                    .setReportingOrigin(Uri.parse("https://adtech.domain"))
                    .setDebugCleartextPayload("{\"operation\":\"histogram\","
                            + "\"data\":[{\"bucket\":1369,\"value\":32768},{\"bucket\":3461,"
                            + "\"value\":1664}]}")
                    .setStatus(CleartextAggregatePayload.Status.DELIVERED)
                    .build();

    private final Source mSourceWithAppName1 = SourceFixture.getValidSourceBuilder()
            .setId("1000")
            .setRegistrant(Uri.parse("android-app://source.app1"))
            .build();

    private final Source mSourceWithAppName2 = SourceFixture.getValidSourceBuilder()
            .setId("1001")
            .setRegistrant(Uri.parse("android-app://source.app2"))
            .build();

    private final Source mSourceWithAppName3 = SourceFixture.getValidSourceBuilder()
            .setId("1002")
            .setRegistrant(Uri.parse("android-app://source.app1"))
            .build();

    @After
    public void after() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        db.delete("msmt_aggregate_report", null, null);
        db.delete("msmt_source", null, null);
    }

    /**
     * Tests for a report that is sent successfully, where the status is set to delivered.
     */
    @Test
    public void testSendReportForPendingReportSuccess() throws IOException {
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        AggregateReportingJobHandler reportingService =
                new AggregateReportingJobHandler(datastoreManager);
        AggregateReportingJobHandler spyReportingService = Mockito.spy(reportingService);
        Mockito.doReturn(HttpURLConnection.HTTP_OK).when(spyReportingService)
                .makeHttpPostRequest(Mockito.any(), Mockito.any());
        String aggregateReportId = mPendingAggregateReportDeadlineReached1.getId();

        DbHelper dbHelper = DbHelper.getInstance(sContext);
        SQLiteDatabase db = dbHelper.safeGetWritableDatabase();
        db.insert("msmt_aggregate_report", null,
                valuesFromReport(mPendingAggregateReportDeadlineReached1));

        Assert.assertEquals("Aggregate Report Failed.",
                spyReportingService.performReport(aggregateReportId),
                AggregateReportingJobHandler.PerformReportResult.SUCCESS);

        Cursor aggregateReportCursor = db.query("msmt_aggregate_report", null, null,
                null, null, null, "_id", null);
        aggregateReportCursor.moveToFirst();
        CleartextAggregatePayload deliveredAggregateReport =
                createAggregateReportFromCursor(aggregateReportCursor);

        Assert.assertEquals(CleartextAggregatePayload.Status.DELIVERED,
                deliveredAggregateReport.getStatus());
    }

    /**
     * Tests for a report that receives an error on hte POST request, where the status is still
     * PENDING.
     */
    @Test
    public void testSendReportForPendingReportFailure() throws IOException {
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        AggregateReportingJobHandler reportingService =
                new AggregateReportingJobHandler(datastoreManager);
        AggregateReportingJobHandler spyReportingService = Mockito.spy(reportingService);
        Mockito.doReturn(HttpURLConnection.HTTP_BAD_REQUEST).when(spyReportingService)
                .makeHttpPostRequest(Mockito.any(), Mockito.any());
        String aggregateReportId = mPendingAggregateReportDeadlineReached1.getId();

        DbHelper dbHelper = DbHelper.getInstance(sContext);
        SQLiteDatabase db = dbHelper.safeGetWritableDatabase();
        db.insert("msmt_aggregate_report", null,
                valuesFromReport(mPendingAggregateReportDeadlineReached1));

        Assert.assertEquals("Aggregate Report Succeeded.",
                spyReportingService.performReport(aggregateReportId),
                AggregateReportingJobHandler.PerformReportResult.POST_REQUEST_ERROR);

        Cursor aggregateReportCursor = db.query("msmt_aggregate_report", null, null,
                null, null, null, "_id", null);

        aggregateReportCursor.moveToFirst();
        CleartextAggregatePayload postPerformAggregateReport =
                createAggregateReportFromCursor(aggregateReportCursor);

        Assert.assertEquals(CleartextAggregatePayload.Status.PENDING,
                postPerformAggregateReport.getStatus());
    }

    /**
     * Test for trying to send a report with a status of DELIVERED.
     */
    @Test
    public void testSendReportForDeliveredReport() throws IOException {
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        AggregateReportingJobHandler reportingService =
                new AggregateReportingJobHandler(datastoreManager);
        AggregateReportingJobHandler spyReportingService = Mockito.spy(reportingService);
        Mockito.doReturn(HttpURLConnection.HTTP_BAD_REQUEST).when(spyReportingService)
                .makeHttpPostRequest(Mockito.any(), Mockito.any());
        String aggregateReportId = mDeliveredAggregateReport.getId();

        DbHelper dbHelper = DbHelper.getInstance(sContext);
        SQLiteDatabase db = dbHelper.safeGetWritableDatabase();
        db.insert("msmt_aggregate_report", null,
                valuesFromReport(mDeliveredAggregateReport));

        Assert.assertEquals("Aggregate Report Failed.",
                spyReportingService.performReport(aggregateReportId),
                AggregateReportingJobHandler.PerformReportResult.ALREADY_DELIVERED);

        Cursor aggregateReportCursor = db.query("msmt_aggregate_report", null, null,
                null, null, null, "_id", null);

        aggregateReportCursor.moveToFirst();
        CleartextAggregatePayload postPerformAggregateReport =
                createAggregateReportFromCursor(aggregateReportCursor);

        Assert.assertEquals(CleartextAggregatePayload.Status.DELIVERED,
                postPerformAggregateReport.getStatus());
    }

    /**
     * Test calling performScheduledPendingReports with multiple pending reports past the scheduled
     * deadline.
     */
    @Test
    public void testPerformScheduledPendingReportsForMultipleReports() throws IOException {
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        AggregateReportingJobHandler reportingService =
                new AggregateReportingJobHandler(datastoreManager);
        AggregateReportingJobHandler spyReportingService = Mockito.spy(reportingService);
        Mockito.doReturn(HttpURLConnection.HTTP_OK).when(spyReportingService)
                .makeHttpPostRequest(Mockito.any(), Mockito.any());

        DbHelper dbHelper = DbHelper.getInstance(sContext);
        SQLiteDatabase db = dbHelper.safeGetWritableDatabase();
        db.insert("msmt_aggregate_report", null,
                valuesFromReport(mPendingAggregateReportDeadlineReached1));
        db.insert("msmt_aggregate_report", null,
                valuesFromReport(mPendingAggregateReportDeadlineReached2));

        spyReportingService.performScheduledPendingReportsInWindow(
                mScheduledReportTimeMs
                        - SystemHealthParams.MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS,
                mScheduledReportTimeMs);

        Cursor aggregateReportCursor = db.query("msmt_aggregate_report", null,
                "_id = ? ", new String[]{mPendingAggregateReportDeadlineReached1.getId()},
                null, null, "_id", null);

        aggregateReportCursor.moveToFirst();
        Assert.assertEquals(CleartextAggregatePayload.Status.DELIVERED,
                createAggregateReportFromCursor(aggregateReportCursor).getStatus());

        aggregateReportCursor = db.query("msmt_aggregate_report", null,
                "_id = ? ", new String[]{mPendingAggregateReportDeadlineReached2.getId()},
                null, null, "_id", null);

        aggregateReportCursor.moveToFirst();
        Assert.assertEquals(CleartextAggregatePayload.Status.DELIVERED,
                createAggregateReportFromCursor(aggregateReportCursor).getStatus());

    }

    /**
     * Test that calling performScheduledPendingReports does not send a report outside the window.
     */
    @Test
    public void testPerformScheduledPendingReportsDoesntSendOutsideWindow() throws IOException {
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        AggregateReportingJobHandler reportingService =
                new AggregateReportingJobHandler(datastoreManager);
        AggregateReportingJobHandler spyReportingService = Mockito.spy(reportingService);
        Mockito.doReturn(HttpURLConnection.HTTP_OK).when(spyReportingService)
                .makeHttpPostRequest(Mockito.any(), Mockito.any());

        DbHelper dbHelper = DbHelper.getInstance(sContext);
        SQLiteDatabase db = dbHelper.safeGetWritableDatabase();
        db.insert("msmt_aggregate_report", null,
                valuesFromReport(mPendingAggregateReportDeadlineReached1));
        db.insert("msmt_aggregate_report", null,
                valuesFromReport(mPendingAggregateReportDeadlineNotReached1));
        db.insert("msmt_aggregate_report", null,
                valuesFromReport(mPendingAggregateReportOutsideWindow));

        spyReportingService.performScheduledPendingReportsInWindow(
                mScheduledReportTimeMs
                        - SystemHealthParams.MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS,
                mScheduledReportTimeMs);

        Cursor aggregateReportCursor = db.query("msmt_aggregate_report", null,
                "_id = ? ", new String[]{mPendingAggregateReportDeadlineReached1.getId()},
                null, null, "_id", null);

        aggregateReportCursor.moveToFirst();
        Assert.assertEquals(CleartextAggregatePayload.Status.DELIVERED,
                createAggregateReportFromCursor(aggregateReportCursor).getStatus());

        aggregateReportCursor = db.query("msmt_aggregate_report", null,
                "_id = ? ", new String[]{mPendingAggregateReportDeadlineNotReached1.getId()},
                null, null, "_id", null);

        aggregateReportCursor.moveToFirst();
        Assert.assertEquals(CleartextAggregatePayload.Status.PENDING,
                createAggregateReportFromCursor(aggregateReportCursor).getStatus());

        aggregateReportCursor = db.query("msmt_aggregate_report", null,
                "_id = ? ", new String[]{mPendingAggregateReportOutsideWindow.getId()},
                null, null, "_id", null);

        aggregateReportCursor.moveToFirst();
        Assert.assertEquals(CleartextAggregatePayload.Status.PENDING,
                createAggregateReportFromCursor(aggregateReportCursor).getStatus());
    }

    /**
     * Test calling performAllPendingReportsForGivenApp with multiple pending reports for the given
     * app name.
     */
    @Test
    public void testPerformAllPendingReportsForGivenApp() throws IOException {
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        AggregateReportingJobHandler reportingService =
                new AggregateReportingJobHandler(datastoreManager);
        AggregateReportingJobHandler spyReportingService = Mockito.spy(reportingService);
        Mockito.doReturn(HttpURLConnection.HTTP_OK).when(spyReportingService)
                .makeHttpPostRequest(Mockito.any(), Mockito.any());

        DbHelper dbHelper = DbHelper.getInstance(sContext);
        SQLiteDatabase db = dbHelper.safeGetWritableDatabase();
        db.insert("msmt_aggregate_report", null,
                valuesFromReport(mPendingAggregateReportFromSpecificSource1));
        db.insert("msmt_aggregate_report", null,
                valuesFromReport(mPendingAggregateReportFromSpecificSource2));
        db.insert("msmt_aggregate_report", null,
                valuesFromReport(mDeliveredAggregateReportFromSpecificSource));
        db.insert("msmt_source", null, valuesFromSource(mSourceWithAppName1));
        db.insert("msmt_source", null, valuesFromSource(mSourceWithAppName2));
        db.insert("msmt_source", null, valuesFromSource(mSourceWithAppName3));

        spyReportingService.performAllPendingReportsForGivenApp(
                Uri.parse("android-app://source.app1"));
        Mockito.verify(spyReportingService, Mockito.times(1))
                .makeHttpPostRequest(Mockito.any(), Mockito.any());

        Cursor aggregateReportCursor = db.query("msmt_aggregate_report", null,
                "_id = ? ", new String[]{mPendingAggregateReportFromSpecificSource1.getId()},
                null, null, "_id", null);
        aggregateReportCursor.moveToFirst();
        Assert.assertEquals(CleartextAggregatePayload.Status.DELIVERED,
                createAggregateReportFromCursor(aggregateReportCursor).getStatus());
        Assert.assertEquals("android-app://source.app1",
                createAggregateReportFromCursor(aggregateReportCursor).getPublisher().toString());
    }


    private ContentValues valuesFromReport(CleartextAggregatePayload aggregateReport) {
        ContentValues values = new ContentValues();
        values.put("_id", aggregateReport.getId());
        values.put("publisher", aggregateReport.getPublisher().toString());
        values.put("attribution_destination",
                aggregateReport.getAttributionDestination().toString());
        values.put("source_registration_time", aggregateReport.getSourceRegistrationTime());
        values.put("scheduled_report_time", aggregateReport.getScheduledReportTime());
        values.put("privacy_budget_key", aggregateReport.getPrivacyBudgetKey());
        values.put("reporting_origin", aggregateReport.getReportingOrigin().toString());
        values.put("debug_cleartext_payload", aggregateReport.getDebugCleartextPayload());
        values.put("status", aggregateReport.getStatus());

        return values;
    }

    private ContentValues valuesFromSource(Source source) {
        ContentValues values = new ContentValues();
        values.put("_id", source.getId());
        values.put("registrant", source.getRegistrant().toString());
        return values;
    }

    private CleartextAggregatePayload createAggregateReportFromCursor(Cursor cursor) {
        return new CleartextAggregatePayload.Builder()
                .setId(cursor.getString(cursor.getColumnIndex("_id")))
                .setPublisher(Uri.parse(
                        cursor.getString(cursor.getColumnIndex("publisher"))))
                .setAttributionDestination(Uri.parse(
                        cursor.getString(cursor.getColumnIndex("attribution_destination"))))
                .setSourceRegistrationTime(
                        cursor.getLong(cursor.getColumnIndex("source_registration_time")))
                .setScheduledReportTime(
                        cursor.getLong(cursor.getColumnIndex("scheduled_report_time")))
                .setPrivacyBudgetKey(
                        cursor.getString(cursor.getColumnIndex("privacy_budget_key")))
                .setReportingOrigin(
                        Uri.parse(cursor.getString(cursor.getColumnIndex(("reporting_origin")))))
                .setDebugCleartextPayload(
                        cursor.getString(cursor.getColumnIndex("debug_cleartext_payload")))
                .setStatus(cursor.getInt(cursor.getColumnIndex("status")))
                .build();
    }


}
