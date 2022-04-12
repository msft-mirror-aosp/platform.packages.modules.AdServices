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

package com.android.adservices.service.measurement;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;

import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;

public class EventReportingJobHandlerTest {

    protected static final Context sContext = ApplicationProvider.getApplicationContext();

    private final EventReport mPendingEventReport = new EventReport.Builder()
            .setId("1")
            .setSourceId(1)
            .setAttributionDestination(
                    Uri.parse("https://www.example1.com/d1"))
            .setReportTo(
                    Uri.parse("https://www.example1.com/r1"))
            .setTriggerData(2)
            .setTriggerTime(8640000002L)
            .setStatus(0)
            .build();

    private final EventReport mDeliveredEventReport = new EventReport.Builder()
                .setId("2")
                .setSourceId(1)
                .setAttributionDestination(
                        Uri.parse("https://www.example2.com/d2"))
                .setReportTo(
                        Uri.parse("https://www.example2.com/r2"))
                .setTriggerData(2)
                .setTriggerTime(8640000002L)
                .setStatus(1)
                .build();

    @After
    public void after() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).getWritableDatabase();
        db.delete("msmt_event_report", null, null);
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

    private ContentValues valuesFromReport(EventReport eventReport) {
        ContentValues values = new ContentValues();
        values.put("_id", eventReport.getId());
        values.put("source_id", eventReport.getSourceId());
        values.put("attribution_destination", eventReport.getAttributionDestination().toString());
        values.put("report_to", eventReport.getReportTo().toString());
        values.put("trigger_data", eventReport.getTriggerData());
        values.put("trigger_time", eventReport.getTriggerTime());
        values.put("status", eventReport.getStatus());
        return values;
    }

    private EventReport createEventReportFromCursor(Cursor cursor) {
        return new EventReport.Builder()
                .setId(cursor.getString(cursor.getColumnIndex("_id")))
                .setSourceId(cursor.getLong(cursor.getColumnIndex("source_id")))
                .setReportTo(Uri.parse(cursor.getString(cursor.getColumnIndex("report_to"))))
                .setAttributionDestination(Uri.parse(
                        cursor.getString(cursor.getColumnIndex("attribution_destination"))))
                .setStatus(cursor.getInt(cursor.getColumnIndex("status")))
                .setTriggerTime(cursor.getLong(cursor.getColumnIndex("trigger_time")))
                .setTriggerData(cursor.getLong(cursor.getColumnIndex("trigger_data")))
                .build();
    }
}
