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

package com.android.adservices.data.measurement;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Class for providing test data for measurement tests.
 */
public class DbState {
    List<Source> mSourceList;
    List<Trigger> mTriggerList;
    List<EventReport> mReportList;
    List<AttributionRateLimit> mAttrRateLimitList;

    public DbState() {
        mSourceList = new ArrayList<>();
        mTriggerList = new ArrayList<>();
        mReportList = new ArrayList<>();
        mAttrRateLimitList = new ArrayList<>();
    }

    // TODO: consider extracting business logic in these
    // constructors to avoid side effects in the DbState class.
    public DbState(JSONObject testInput) throws JSONException {
        this();

        // Sources
        JSONArray sources = testInput.getJSONArray("sources");
        for (int i = 0; i < sources.length(); i++) {
            JSONObject sJSON = sources.getJSONObject(i);
            Source source = getSourceFrom(sJSON);
            mSourceList.add(source);
        }

        // Triggers
        JSONArray triggers = testInput.getJSONArray("triggers");
        for (int i = 0; i < triggers.length(); i++) {
            JSONObject tJSON = triggers.getJSONObject(i);
            Trigger trigger = getTriggerFrom(tJSON);
            mTriggerList.add(trigger);
        }

        // EventReports
        JSONArray reports = testInput.getJSONArray("event_reports");
        for (int i = 0; i < reports.length(); i++) {
            JSONObject rJSON = reports.getJSONObject(i);
            EventReport eventReport = getEventReportFrom(rJSON);
            mReportList.add(eventReport);
        }

        // AttributionRateLimits
        JSONArray attrs = testInput.getJSONArray("attribution_rate_limits");
        for (int i = 0; i < attrs.length(); i++) {
            JSONObject attrJSON = attrs.getJSONObject(i);
            AttributionRateLimit attrRateLimit = getAttributionRateLimitFrom(attrJSON);
            mAttrRateLimitList.add(attrRateLimit);
        }
    }

    public DbState(SQLiteDatabase readerDB) {
        this();

        // Read Source table
        Cursor sourceCursor = readerDB.query(MeasurementTables.SourceContract.TABLE,
                null, null, null, null, null, MeasurementTables.SourceContract.ID);
        while (sourceCursor.moveToNext()) {
            mSourceList.add(SqliteObjectMapper.constructSourceFromCursor(sourceCursor));
        }
        sourceCursor.close();

        // Read Trigger table
        Cursor triggerCursor = readerDB.query(MeasurementTables.TriggerContract.TABLE,
                null, null, null, null, null, MeasurementTables.TriggerContract.ID);
        while (triggerCursor.moveToNext()) {
            mTriggerList.add(SqliteObjectMapper.constructTriggerFromCursor(triggerCursor));
        }
        triggerCursor.close();

        // Read EventReport table
        Cursor reportCursor = readerDB.query(MeasurementTables.EventReportContract.TABLE,
                null, null, null, null, null, MeasurementTables.EventReportContract.ID);
        while (reportCursor.moveToNext()) {
            mReportList.add(SqliteObjectMapper.constructEventReportFromCursor(reportCursor));
        }
        reportCursor.close();

        // Read AttributionRateLimit table
        Cursor attrCursor = readerDB.query(MeasurementTables.AttributionRateLimitContract.TABLE,
                null, null, null, null, null, MeasurementTables.AttributionRateLimitContract.ID);
        while (attrCursor.moveToNext()) {
            mAttrRateLimitList.add(getAttributionRateLimitFrom(attrCursor));
        }
        attrCursor.close();
    }

    public void sortAll() {
        mSourceList.sort(
                Comparator.comparing(Source::getEventTime)
                        .thenComparing(Source::getPriority));
        mTriggerList.sort(
                Comparator.comparing(Trigger::getTriggerTime)
                        .thenComparing(Trigger::getPriority));
        mReportList.sort(
                Comparator.comparing(EventReport::getReportTime)
                        .thenComparing(EventReport::getTriggerTime));
        mAttrRateLimitList.sort(
                Comparator.comparing(AttributionRateLimit::getTriggerTime));
    }

    private Source getSourceFrom(JSONObject sJSON) throws JSONException {
        return new Source.Builder()
                .setId(sJSON.getString("id"))
                .setEventId(sJSON.getLong("eventId"))
                .setSourceType(
                        Source.SourceType.valueOf(sJSON.getString("sourceType").toUpperCase(
                                Locale.ENGLISH)))
                .setPublisher(Uri.parse(sJSON.getString("publisher")))
                .setAttributionDestination(Uri.parse(sJSON.getString("attributionDestination")))
                .setAdTechDomain(Uri.parse(sJSON.getString("adTechDomain")))
                .setEventTime(sJSON.getLong("eventTime"))
                .setExpiryTime(sJSON.getLong("expiryTime"))
                .setPriority(sJSON.getLong("priority"))
                .setStatus(sJSON.getInt("status"))
                .setRegistrant(Uri.parse(sJSON.getString("registrant")))
                .setInstallAttributionWindow(sJSON.optLong("installAttributionWindow",
                        TimeUnit.DAYS.toMillis(30)))
                .setInstallCooldownWindow(sJSON.optLong("installCooldownWindow",
                        0))
                .setInstallAttributed(sJSON.optBoolean("installAttributed", false))
                .setAttributionMode(sJSON.optInt("attribution_mode",
                        Source.AttributionMode.TRUTHFULLY))
                .build();
    }

    private Trigger getTriggerFrom(JSONObject tJSON) throws JSONException {
        return new Trigger.Builder()
                .setId(tJSON.getString("id"))
                .setAttributionDestination(Uri.parse(tJSON.getString("attributionDestination")))
                .setAdTechDomain(Uri.parse(tJSON.getString("adTechDomain")))
                .setEventTriggerData(tJSON.getLong("triggerData"))
                .setTriggerTime(tJSON.getLong("triggerTime"))
                .setPriority(tJSON.getLong("priority"))
                .setStatus(tJSON.getInt("status"))
                .setRegistrant(Uri.parse(tJSON.getString("registrant")))
                .build();
    }

    private EventReport getEventReportFrom(JSONObject rJSON) throws JSONException {
        return new EventReport.Builder()
                .setId(rJSON.getString("id"))
                .setSourceId(rJSON.getLong("sourceId"))
                .setAttributionDestination(
                        Uri.parse(rJSON.getString("attributionDestination")))
                .setAdTechDomain(Uri.parse(rJSON.getString("adTechDomain")))
                .setTriggerData(rJSON.getLong("triggerData"))
                .setTriggerTime(rJSON.getLong("triggerTime"))
                .setReportTime(rJSON.getLong("reportTime"))
                .setTriggerPriority(rJSON.getLong("triggerPriority"))
                .setStatus(rJSON.getInt("status"))
                .setRandomizedTriggerRate(rJSON.optDouble("randomizedTriggerRate",
                        0.0D))
                .setSourceType(
                Source.SourceType.valueOf(rJSON.getString("sourceType").toUpperCase(
                        Locale.ENGLISH)))
                .build();
    }

    private AttributionRateLimit getAttributionRateLimitFrom(JSONObject attrJSON)
            throws JSONException {
        return new AttributionRateLimit.Builder()
                .setId(attrJSON.getString("id"))
                .setSourceSite(attrJSON.getString("sourceSite"))
                .setDestinationSite(attrJSON.getString("destinationSite"))
                .setAdTechDomain(attrJSON.getString("adTechDomain"))
                .setTriggerTime(attrJSON.getLong("triggerTime"))
                .setRegistrant(attrJSON.getString("registrant"))
                .build();
    }

    private AttributionRateLimit getAttributionRateLimitFrom(Cursor cursor) {
        return new AttributionRateLimit.Builder()
                .setId(cursor.getString(cursor.getColumnIndex(
                        MeasurementTables.AttributionRateLimitContract.ID)))
                .setSourceSite(cursor.getString(cursor.getColumnIndex(
                        MeasurementTables.AttributionRateLimitContract.SOURCE_SITE)))
                .setDestinationSite(cursor.getString(cursor.getColumnIndex(
                        MeasurementTables.AttributionRateLimitContract.DESTINATION_SITE)))
                .setAdTechDomain(cursor.getString(cursor.getColumnIndex(
                        MeasurementTables.AttributionRateLimitContract.AD_TECH_DOMAIN)))
                .setTriggerTime(cursor.getLong(cursor.getColumnIndex(
                        MeasurementTables.AttributionRateLimitContract.TRIGGER_TIME)))
                .setRegistrant(cursor.getString(cursor.getColumnIndex(
                        MeasurementTables.AttributionRateLimitContract.REGISTRANT)))
                .build();
    }
}
