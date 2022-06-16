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
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SystemHealthParams;

import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.HttpURLConnection;

public class EventReportingJobHandlerTest {

    protected static final Context sContext = ApplicationProvider.getApplicationContext();

    private final long mTimestampMs = 20000L;

    private final EventReport mPendingEventReport = new EventReport.Builder()
            .setId("1")
            .setSourceId(1)
            .setAttributionDestination(
                    Uri.parse("https://www.example1.com/d1"))
            .setAdTechDomain(
                    Uri.parse("https://www.example1.com"))
            .setTriggerData(2)
            .setTriggerTime(8640000002L)
            .setStatus(0)
            .setSourceType(Source.SourceType.NAVIGATION)
            .build();

    private final EventReport mDeliveredEventReport = new EventReport.Builder()
                .setId("2")
                .setSourceId(1)
                .setAttributionDestination(
                        Uri.parse("https://www.example2.com/d2"))
                .setAdTechDomain(
                        Uri.parse("https://www.example2.com"))
                .setTriggerData(2)
                .setTriggerTime(8640000002L)
                .setStatus(1)
                .setSourceType(Source.SourceType.NAVIGATION)
                .build();

    private final EventReport mPendingEventReportDeadlineReached1 = new EventReport.Builder()
            .setId("10")
            .setSourceId(1)
            .setAttributionDestination(
                    Uri.parse("https://www.example1.com/d1"))
            .setAdTechDomain(
                    Uri.parse("https://www.example1.com"))
            .setTriggerData(2)
            .setTriggerTime(8640000002L)
            .setStatus(EventReport.Status.PENDING)
            .setReportTime(mTimestampMs - 10000)
            .setSourceType(Source.SourceType.NAVIGATION)
            .build();

    private final EventReport mPendingEventReportDeadlineReached2 = new EventReport.Builder()
            .setId("11")
            .setSourceId(1)
            .setAttributionDestination(
                    Uri.parse("https://www.example1.com/d1"))
            .setAdTechDomain(
                    Uri.parse("https://www.example1.com"))
            .setTriggerData(2)
            .setTriggerTime(8640000002L)
            .setStatus(EventReport.Status.PENDING)
            .setReportTime(mTimestampMs)
            .setSourceType(Source.SourceType.NAVIGATION)
            .build();

    private final EventReport mPendingEventReportDeadlineNotReached1 = new EventReport.Builder()
            .setId("12")
            .setSourceId(1)
            .setAttributionDestination(
                    Uri.parse("https://www.example1.com/d1"))
            .setAdTechDomain(
                    Uri.parse("https://www.example1.com"))
            .setTriggerData(2)
            .setTriggerTime(8640000002L)
            .setStatus(EventReport.Status.PENDING)
            .setReportTime(mTimestampMs + 200)
            .setSourceType(Source.SourceType.NAVIGATION)
            .build();

    private final EventReport mPendingEventReportOutsideWindow = new EventReport.Builder()
            .setId("13")
            .setSourceId(1)
            .setAttributionDestination(
                    Uri.parse("https://www.example1.com/d1"))
            .setAdTechDomain(
                    Uri.parse("https://www.example1.com"))
            .setTriggerData(2)
            .setTriggerTime(8640000002L)
            .setStatus(EventReport.Status.PENDING)
            .setReportTime(
                    mTimestampMs - SystemHealthParams.MAX_EVENT_REPORT_UPLOAD_RETRY_WINDOW_MS - 1)
            .setSourceType(Source.SourceType.NAVIGATION)
            .build();

    private final EventReport mPendingEventReportNoDeadline1 = new EventReport.Builder()
            .setId("100")
            .setSourceId(10L)
            .setAttributionDestination(
                    Uri.parse("https://www.example1.com/d1"))
            .setAdTechDomain(
                    Uri.parse("https://www.example1.com"))
            .setTriggerData(2)
            .setTriggerTime(8640000002L)
            .setStatus(EventReport.Status.PENDING)
            .setSourceType(Source.SourceType.NAVIGATION)
            .build();

    private final EventReport mPendingEventReportNoDeadline2 = new EventReport.Builder()
            .setId("101")
            .setSourceId(11L)
            .setAttributionDestination(
                    Uri.parse("https://www.example1.com/d1"))
            .setAdTechDomain(
                    Uri.parse("https://www.example1.com"))
            .setTriggerData(2)
            .setTriggerTime(8640000002L)
            .setStatus(EventReport.Status.PENDING)
            .setSourceType(Source.SourceType.NAVIGATION)
            .build();

    private final EventReport mNotPendingEventReportNoDeadline = new EventReport.Builder()
            .setId("102")
            .setSourceId(12L)
            .setAttributionDestination(
                    Uri.parse("https://www.example1.com/d1"))
            .setAdTechDomain(
                    Uri.parse("https://www.example1.com"))
            .setTriggerData(2)
            .setTriggerTime(8640000002L)
            .setStatus(EventReport.Status.DELIVERED)
            .setSourceType(Source.SourceType.NAVIGATION)
            .build();

    private final Source mSourceWithAppName1 = new Source.Builder()
            .setId("1000")
            .setEventId(10L)
            .setRegistrant(Uri.parse("android-app://com.example.abc")).build();

    private final Source mSourceWithAppName2 = new Source.Builder()
            .setId("1001")
            .setEventId(11L)
            .setRegistrant(Uri.parse("android-app://com.example.xyz")).build();

    private final Source mSourceWithAppName3 = new Source.Builder()
            .setId("1002")
            .setEventId(12L)
            .setRegistrant(Uri.parse("android-app://com.example.abc")).build();

    @After
    public void after() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).getWritableDatabase();
        db.delete("msmt_event_report", null, null);
        db.delete("msmt_source", null, null);
    }

    /**
     * Tests for a report that is sent successfully, where the status is set to DELIVERED.
     */
    @Test
    public void testSendReportForPendingReportSuccess() throws JSONException, IOException {
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        EventReportingJobHandler reportingService =
                new EventReportingJobHandler(datastoreManager);
        EventReportingJobHandler spyReportingService = Mockito.spy(reportingService);
        Mockito.doReturn(200).when(spyReportingService)
                .makeHttpPostRequest(Mockito.any(), Mockito.any());

        String eventReportId = mPendingEventReport.getId();

        DbHelper dbHelper = DbHelper.getInstance(sContext);
        SQLiteDatabase db = dbHelper.safeGetWritableDatabase();
        db.insert("msmt_event_report", null,
                valuesFromReport(mPendingEventReport));

        Assert.assertEquals("Event Report Failed.",
                spyReportingService.performReport(eventReportId),
                EventReportingJobHandler.PerformReportResult.SUCCESS);

        Cursor eventReportCursor = db.query("msmt_event_report", null, null,
                null, null, null,
                "_id", null);
        eventReportCursor.moveToFirst();
        EventReport deliveredEventReport = createEventReportFromCursor(eventReportCursor);

        Assert.assertEquals(deliveredEventReport.getStatus(), EventReport.Status.DELIVERED);
    }

    /**
     * Test for a report that receives an error on the POST request, where the status is still
     * PENDING.
     */
    @Test
    public void testSendReportForPendingReportFailure() throws IOException, JSONException {
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        EventReportingJobHandler reportingService =
                new EventReportingJobHandler(datastoreManager);
        EventReportingJobHandler spyReportingService = Mockito.spy(reportingService);
        Mockito.doReturn(400).when(spyReportingService)
                .makeHttpPostRequest(Mockito.any(), Mockito.any());

        String eventReportId = mPendingEventReport.getId();

        DbHelper dbHelper = DbHelper.getInstance(sContext);
        SQLiteDatabase db = dbHelper.safeGetWritableDatabase();
        db.insert("msmt_event_report", null,
                valuesFromReport(mPendingEventReport));

        Assert.assertEquals("Event Report Succeeded.",
                spyReportingService.performReport(eventReportId),
                EventReportingJobHandler.PerformReportResult.POST_REQUEST_ERROR);

        Cursor eventReportCursor = db.query("msmt_event_report", null, null,
                null, null, null,
                "_id", null);
        eventReportCursor.moveToFirst();
        EventReport postPerformEventReport = createEventReportFromCursor(eventReportCursor);

        Assert.assertTrue(mPendingEventReport.equals(postPerformEventReport));
    }

    /**
     * Test for trying to send a report with a status of DELIVERED.
     */
    @Test
    public void testSendReportForDoneReport() throws IOException, JSONException {
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        EventReportingJobHandler reportingService =
                new EventReportingJobHandler(datastoreManager);
        EventReportingJobHandler spyReportingService = Mockito.spy(reportingService);
        Mockito.doReturn(400).when(spyReportingService)
                .makeHttpPostRequest(Mockito.any(), Mockito.any());

        String eventReportId = mDeliveredEventReport.getId();

        DbHelper dbHelper = DbHelper.getInstance(sContext);
        SQLiteDatabase db = dbHelper.safeGetWritableDatabase();
        db.insert("msmt_event_report", null,
                valuesFromReport(mDeliveredEventReport));

        Assert.assertEquals("Event Report Failed.",
                spyReportingService.performReport(eventReportId),
                EventReportingJobHandler.PerformReportResult.ALREADY_DELIVERED);

        Cursor eventReportCursor = db.query("msmt_event_report", null, null,
                null, null, null,
                "_id", null);
        eventReportCursor.moveToFirst();
        EventReport postPerformEventReport = createEventReportFromCursor(eventReportCursor);

        Assert.assertTrue(mDeliveredEventReport.equals(
                postPerformEventReport));
    }
    /**
     * Test calling performScheduledPendingReports with multiple pending reports
     * past the scheduled deadline.
     */
    @Test
    public void testPerformScheduledPendingReportsForMultipleReports() throws IOException {
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        EventReportingJobHandler reportingService = new EventReportingJobHandler(datastoreManager);
        EventReportingJobHandler spyReportingService = Mockito.spy(reportingService);
        Mockito.doReturn(HttpURLConnection.HTTP_OK).when(spyReportingService)
                .makeHttpPostRequest(Mockito.any(), Mockito.any());

        DbHelper dbHelper = DbHelper.getInstance(sContext);
        SQLiteDatabase db = dbHelper.safeGetWritableDatabase();
        db.insert("msmt_event_report", null,
                valuesFromReport(mPendingEventReportDeadlineReached1));
        db.insert("msmt_event_report", null,
                valuesFromReport(mPendingEventReportDeadlineReached2));

        spyReportingService.performScheduledPendingReportsInWindow(
                mTimestampMs - SystemHealthParams.MAX_EVENT_REPORT_UPLOAD_RETRY_WINDOW_MS,
                mTimestampMs);

        Cursor eventReportCursor = db.query("msmt_event_report", null,
                "_id = ? ", new String[]{mPendingEventReportDeadlineReached1.getId()},
                null, null, "_id", null);

        eventReportCursor.moveToFirst();
        Assert.assertEquals(EventReport.Status.DELIVERED,
                createEventReportFromCursor(eventReportCursor).getStatus());

        eventReportCursor = db.query("msmt_event_report", null, "_id = ? ",
                new String[]{mPendingEventReportDeadlineReached2.getId()},
                null, null, "_id", null);

        eventReportCursor.moveToFirst();
        Assert.assertEquals(EventReport.Status.DELIVERED,
                createEventReportFromCursor(eventReportCursor).getStatus());
    }

    /**
     * Test that calling performScheduledPendingReports does not send a report that has not
     * reached the deadline yet.
     */
    @Test
    public void testPerformScheduledPendingReportsDoesntSendReportsIfDeadlineNotReached()
            throws IOException {
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        EventReportingJobHandler reportingService = new EventReportingJobHandler(datastoreManager);
        EventReportingJobHandler spyReportingService = Mockito.spy(reportingService);
        Mockito.doReturn(HttpURLConnection.HTTP_OK).when(spyReportingService)
                .makeHttpPostRequest(Mockito.any(), Mockito.any());

        DbHelper dbHelper = DbHelper.getInstance(sContext);
        SQLiteDatabase db = dbHelper.safeGetWritableDatabase();
        db.insert("msmt_event_report", null,
                valuesFromReport(mPendingEventReportDeadlineReached1));
        db.insert("msmt_event_report", null,
                valuesFromReport(mPendingEventReportDeadlineNotReached1));

        spyReportingService.performScheduledPendingReportsInWindow(
                0,
                mTimestampMs);

        Cursor eventReportCursor = db.query("msmt_event_report", null,
                "_id = ? ",
                new String[]{mPendingEventReportDeadlineReached1.getId()},
                null, null, "_id", null);

        eventReportCursor.moveToFirst();
        Assert.assertEquals(EventReport.Status.DELIVERED,
                createEventReportFromCursor(eventReportCursor).getStatus());

        // The report's reportTime is still in the future so it should not have sent.
        eventReportCursor = db.query("msmt_event_report", null, "_id = ? ",
                new String[]{mPendingEventReportDeadlineNotReached1.getId()}, null, null,
                "_id", null);

        eventReportCursor.moveToFirst();
        Assert.assertEquals(EventReport.Status.PENDING,
                createEventReportFromCursor(eventReportCursor).getStatus());
    }

    /**
     * Test that calling performScheduledPendingReports does not send a report if it is outside the
     * maximum reporting window.
     */
    @Test
    public void testPerformScheduledPendingReportsDoesntSendReportsOutsideWindow()
            throws IOException {
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        EventReportingJobHandler reportingService = new EventReportingJobHandler(datastoreManager);
        EventReportingJobHandler spyReportingService = Mockito.spy(reportingService);
        Mockito.doReturn(HttpURLConnection.HTTP_OK).when(spyReportingService)
                .makeHttpPostRequest(Mockito.any(), Mockito.any());

        DbHelper dbHelper = DbHelper.getInstance(sContext);
        SQLiteDatabase db = dbHelper.safeGetWritableDatabase();
        db.insert("msmt_event_report", null,
                valuesFromReport(mPendingEventReportDeadlineReached1));
        db.insert("msmt_event_report", null,
                valuesFromReport(mPendingEventReportOutsideWindow));

        spyReportingService.performScheduledPendingReportsInWindow(
                0,
                mTimestampMs);

        Cursor eventReportCursor = db.query("msmt_event_report", null,
                "_id = ? ",
                new String[]{mPendingEventReportDeadlineReached1.getId()},
                null, null, "_id", null);

        eventReportCursor.moveToFirst();
        Assert.assertEquals(EventReport.Status.DELIVERED,
                createEventReportFromCursor(eventReportCursor).getStatus());

        eventReportCursor = db.query("msmt_event_report", null, "_id = ? ",
                new String[]{mPendingEventReportOutsideWindow.getId()}, null, null,
                "_id", null);

        eventReportCursor.moveToFirst();
        Assert.assertEquals(EventReport.Status.PENDING,
                createEventReportFromCursor(eventReportCursor).getStatus());
    }

    /**
     * Test calling performAllPendingReportsForGivenApp with multiple pending reports for the given
     * app name.
     */
    @Test
    public void testPerformAllPendingReportsForGivenApp() throws IOException {
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        EventReportingJobHandler reportingService = new EventReportingJobHandler(datastoreManager);
        EventReportingJobHandler spyReportingService = Mockito.spy(reportingService);
        Mockito.doReturn(HttpURLConnection.HTTP_OK).when(spyReportingService)
                .makeHttpPostRequest(Mockito.any(), Mockito.any());

        DbHelper dbHelper = DbHelper.getInstance(sContext);
        SQLiteDatabase db = dbHelper.safeGetWritableDatabase();
        db.insert("msmt_event_report", null,
                valuesFromReport(mPendingEventReportNoDeadline1));
        db.insert("msmt_event_report", null,
                valuesFromReport(mPendingEventReportNoDeadline2));
        db.insert("msmt_event_report", null,
                valuesFromReport(mNotPendingEventReportNoDeadline));
        db.insert("msmt_source", null, valuesFromSource(mSourceWithAppName1));
        db.insert("msmt_source", null, valuesFromSource(mSourceWithAppName2));
        db.insert("msmt_source", null, valuesFromSource(mSourceWithAppName3));

        spyReportingService.performAllPendingReportsForGivenApp(
                Uri.parse("android-app://com.example.abc"));
        Mockito.verify(spyReportingService, Mockito.times(1))
                .makeHttpPostRequest(Mockito.any(), Mockito.any());

        Cursor eventReportCursor = db.query("msmt_event_report", null,
                "_id = ? ", new String[]{mPendingEventReportNoDeadline1.getId()},
                null, null, "_id", null);
        eventReportCursor.moveToFirst();
        Assert.assertEquals(EventReport.Status.DELIVERED,
                createEventReportFromCursor(eventReportCursor).getStatus());
        Assert.assertEquals(10L,
                createEventReportFromCursor(eventReportCursor).getSourceId());
    }

    private ContentValues valuesFromReport(EventReport eventReport) {
        ContentValues values = new ContentValues();
        values.put("_id", eventReport.getId());
        values.put("source_id", eventReport.getSourceId());
        values.put("attribution_destination", eventReport.getAttributionDestination().toString());
        values.put("ad_tech_domain", eventReport.getAdTechDomain().toString());
        values.put("trigger_data", eventReport.getTriggerData());
        values.put("trigger_time", eventReport.getTriggerTime());
        values.put("status", eventReport.getStatus());
        values.put("report_time", eventReport.getReportTime());
        values.put("source_type", eventReport.getSourceType().toString());
        return values;
    }

    private ContentValues valuesFromSource(Source source) {
        ContentValues values = new ContentValues();
        values.put("_id", source.getId());
        values.put("event_id", source.getEventId());
        values.put("registrant", source.getRegistrant().toString());
        return values;
    }

    private EventReport createEventReportFromCursor(Cursor cursor) {
        return new EventReport.Builder()
                .setId(cursor.getString(cursor.getColumnIndex("_id")))
                .setSourceId(cursor.getLong(cursor.getColumnIndex("source_id")))
                .setAdTechDomain(Uri.parse(cursor.getString(
                        cursor.getColumnIndex("ad_tech_domain"))))
                .setAttributionDestination(Uri.parse(
                        cursor.getString(cursor.getColumnIndex("attribution_destination"))))
                .setStatus(cursor.getInt(cursor.getColumnIndex("status")))
                .setTriggerTime(cursor.getLong(cursor.getColumnIndex("trigger_time")))
                .setTriggerData(cursor.getLong(cursor.getColumnIndex("trigger_data")))
                .setReportTime(cursor.getLong(cursor.getColumnIndex("report_time")))
                .setSourceType(Source.SourceType.valueOf(cursor
                        .getString(cursor.getColumnIndex("source_type"))))
                .build();
    }
}
