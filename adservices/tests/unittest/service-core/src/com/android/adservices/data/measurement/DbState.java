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

import com.android.adservices.service.measurement.Attribution;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.util.UnsignedLong;

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
    List<EventReport> mEventReportList;
    List<Attribution> mAttrRateLimitList;
    List<AggregateEncryptionKey> mAggregateEncryptionKeyList;
    List<AggregateReport> mAggregateReportList;

    public DbState() {
        mSourceList = new ArrayList<>();
        mTriggerList = new ArrayList<>();
        mEventReportList = new ArrayList<>();
        mAttrRateLimitList = new ArrayList<>();
        mAggregateEncryptionKeyList = new ArrayList<>();
        mAggregateReportList = new ArrayList<>();
    }

    public DbState(JSONObject testInput) throws JSONException {
        this();

        // Sources
        if (testInput.has("sources")) {
            JSONArray sources = testInput.getJSONArray("sources");
            for (int i = 0; i < sources.length(); i++) {
                JSONObject sJSON = sources.getJSONObject(i);
                Source source = getSourceFrom(sJSON);
                mSourceList.add(source);
            }
        }

        // Triggers
        if (testInput.has("triggers")) {
            JSONArray triggers = testInput.getJSONArray("triggers");
            for (int i = 0; i < triggers.length(); i++) {
                JSONObject tJSON = triggers.getJSONObject(i);
                Trigger trigger = getTriggerFrom(tJSON);
                mTriggerList.add(trigger);
            }
        }

        // EventReports
        if (testInput.has("event_reports")) {
            JSONArray eventReports = testInput.getJSONArray("event_reports");
            for (int i = 0; i < eventReports.length(); i++) {
                JSONObject rJSON = eventReports.getJSONObject(i);
                EventReport eventReport = getEventReportFrom(rJSON);
                mEventReportList.add(eventReport);
            }
        }

        // Attributions
        if (testInput.has("attributions")) {
            JSONArray attrs = testInput.getJSONArray("attributions");
            for (int i = 0; i < attrs.length(); i++) {
                JSONObject attrJSON = attrs.getJSONObject(i);
                Attribution attrRateLimit = getAttributionFrom(attrJSON);
                mAttrRateLimitList.add(attrRateLimit);
            }
        }

        // AggregateEncryptionKeys
        if (testInput.has("aggregate_encryption_keys")) {
            JSONArray keys = testInput.getJSONArray("aggregate_encryption_keys");
            for (int i = 0; i < keys.length(); i++) {
                JSONObject keyJSON = keys.getJSONObject(i);
                AggregateEncryptionKey key = getAggregateEncryptionKeyFrom(keyJSON);
                mAggregateEncryptionKeyList.add(key);
            }
        }

        if (testInput.has("aggregate_reports")) {
            // AggregateReports
            JSONArray aggregateReports = testInput.getJSONArray("aggregate_reports");
            for (int i = 0; i < aggregateReports.length(); i++) {
                JSONObject rJSON = aggregateReports.getJSONObject(i);
                AggregateReport aggregateReport = getAggregateReportFrom(rJSON);
                mAggregateReportList.add(aggregateReport);
            }
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
        Cursor eventReportCursor =
                readerDB.query(
                        MeasurementTables.EventReportContract.TABLE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        MeasurementTables.EventReportContract.ID);
        while (eventReportCursor.moveToNext()) {
            mEventReportList.add(
                    SqliteObjectMapper.constructEventReportFromCursor(eventReportCursor));
        }
        eventReportCursor.close();

        // Read Attribution table
        Cursor attrCursor = readerDB.query(MeasurementTables.AttributionContract.TABLE,
                null, null, null, null, null, MeasurementTables.AttributionContract.ID);
        while (attrCursor.moveToNext()) {
            mAttrRateLimitList.add(getAttributionFrom(attrCursor));
        }
        attrCursor.close();

        // Read AggregateReport table
        Cursor aggregateReportCursor =
                readerDB.query(
                        MeasurementTables.AggregateReport.TABLE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        MeasurementTables.AggregateReport.ID);
        while (aggregateReportCursor.moveToNext()) {
            mAggregateReportList.add(
                    SqliteObjectMapper.constructAggregateReport(aggregateReportCursor));
        }
        aggregateReportCursor.close();

        // Read AggregateEncryptionKey table
        Cursor keyCursor = readerDB.query(MeasurementTables.AggregateEncryptionKey.TABLE,
                null, null, null, null, null, MeasurementTables.AggregateEncryptionKey.ID);
        while (keyCursor.moveToNext()) {
            mAggregateEncryptionKeyList.add(
                    SqliteObjectMapper.constructAggregateEncryptionKeyFromCursor(keyCursor));
        }
        keyCursor.close();
    }

    public void sortAll() {
        mSourceList.sort(
                Comparator.comparing(Source::getEventTime)
                        .thenComparing(Source::getPriority));

        mTriggerList.sort(Comparator.comparing(Trigger::getTriggerTime));

        mEventReportList.sort(
                Comparator.comparing(EventReport::getReportTime)
                        .thenComparing(EventReport::getTriggerTime));

        mAttrRateLimitList.sort(
                Comparator.comparing(Attribution::getTriggerTime));

        mAggregateEncryptionKeyList.sort(
                Comparator.comparing(AggregateEncryptionKey::getKeyId));

        mAggregateReportList.sort(
                Comparator.comparing(AggregateReport::getScheduledReportTime)
                        .thenComparing(AggregateReport::getSourceRegistrationTime));
    }

    public List<AggregateEncryptionKey> getAggregateEncryptionKeyList() {
        return mAggregateEncryptionKeyList;
    }

    private Source getSourceFrom(JSONObject sJSON) throws JSONException {
        return new Source.Builder()
                .setId(sJSON.getString("id"))
                .setEventId(new UnsignedLong(sJSON.getString("eventId")))
                .setSourceType(
                        Source.SourceType.valueOf(
                                sJSON.getString("sourceType").toUpperCase(Locale.ENGLISH)))
                .setPublisher(Uri.parse(sJSON.getString("publisher")))
                .setPublisherType(sJSON.optInt("publisherType"))
                .setAppDestination(Uri.parse(sJSON.getString("appDestination")))
                .setWebDestination(parseIfNonNull(sJSON.optString("webDestination", null)))
                .setAggregateSource(sJSON.optString("aggregationKeys", null))
                .setAggregateContributions(sJSON.optInt("aggregateContributions"))
                .setEnrollmentId(sJSON.getString("enrollmentId"))
                .setEventTime(sJSON.getLong("eventTime"))
                .setExpiryTime(sJSON.getLong("expiryTime"))
                .setPriority(sJSON.getLong("priority"))
                .setStatus(sJSON.getInt("status"))
                .setRegistrant(Uri.parse(sJSON.getString("registrant")))
                .setInstallAttributionWindow(
                        sJSON.optLong("installAttributionWindow", TimeUnit.DAYS.toMillis(30)))
                .setInstallCooldownWindow(sJSON.optLong("installCooldownWindow", 0))
                .setInstallAttributed(sJSON.optBoolean("installAttributed", false))
                .setAttributionMode(
                        sJSON.optInt("attribution_mode", Source.AttributionMode.TRUTHFULLY))
                .setAggregateFilterData(sJSON.optString("aggregateFilterData", null))
                .build();
    }

    private Trigger getTriggerFrom(JSONObject tJSON) throws JSONException {
        return new Trigger.Builder()
                .setId(tJSON.getString("id"))
                .setAttributionDestination(Uri.parse(tJSON.getString("attributionDestination")))
                .setDestinationType(tJSON.optInt("destinationType"))
                .setEnrollmentId(tJSON.getString("enrollmentId"))
                .setEventTriggers(tJSON.getString("eventTriggers"))
                .setAggregateTriggerData(tJSON.optString("aggregatableTriggerData", null))
                .setAggregateValues(tJSON.optString("aggregatableValues", null))
                .setTriggerTime(tJSON.getLong("triggerTime"))
                .setStatus(tJSON.getInt("status"))
                .setRegistrant(Uri.parse(tJSON.getString("registrant")))
                .setFilters(tJSON.optString("filters", null))
                .build();
    }

    private EventReport getEventReportFrom(JSONObject rJSON) throws JSONException {
        return new EventReport.Builder()
                .setId(rJSON.getString("id"))
                .setSourceId(new UnsignedLong(rJSON.getString("sourceId")))
                .setAttributionDestination(Uri.parse(rJSON.getString("attributionDestination")))
                .setEnrollmentId(rJSON.getString("enrollmentId"))
                .setTriggerData(new UnsignedLong(rJSON.getString("triggerData")))
                .setTriggerTime(rJSON.getLong("triggerTime"))
                .setReportTime(rJSON.getLong("reportTime"))
                .setTriggerPriority(rJSON.getLong("triggerPriority"))
                .setStatus(rJSON.getInt("status"))
                .setRandomizedTriggerRate(rJSON.optDouble("randomizedTriggerRate", 0.0D))
                .setSourceType(
                        Source.SourceType.valueOf(
                                rJSON.getString("sourceType").toUpperCase(Locale.ENGLISH)))
                .build();
    }

    private Attribution getAttributionFrom(JSONObject attrJSON)
            throws JSONException {
        return new Attribution.Builder()
                .setId(attrJSON.getString("id"))
                .setSourceSite(attrJSON.getString("sourceSite"))
                .setSourceOrigin(attrJSON.getString("sourceOrigin"))
                .setDestinationSite(attrJSON.getString("destinationSite"))
                .setDestinationOrigin(attrJSON.getString("destinationOrigin"))
                .setEnrollmentId(attrJSON.getString("enrollmentId"))
                .setTriggerTime(attrJSON.getLong("triggerTime"))
                .setRegistrant(attrJSON.getString("registrant"))
                .build();
    }

    private AggregateEncryptionKey getAggregateEncryptionKeyFrom(JSONObject keyJSON)
            throws JSONException {
        return new AggregateEncryptionKey.Builder()
                .setId(keyJSON.getString("id"))
                .setKeyId(keyJSON.getString("keyId"))
                .setPublicKey(keyJSON.getString("publicKey"))
                .setExpiry(keyJSON.getLong("expiry"))
                .build();
    }

    private Attribution getAttributionFrom(Cursor cursor) {
        return new Attribution.Builder()
                .setId(
                        cursor.getString(
                                cursor.getColumnIndex(MeasurementTables.AttributionContract.ID)))
                .setSourceSite(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AttributionContract.SOURCE_SITE)))
                .setSourceOrigin(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AttributionContract.SOURCE_ORIGIN)))
                .setDestinationSite(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AttributionContract.DESTINATION_SITE)))
                .setDestinationOrigin(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AttributionContract.DESTINATION_ORIGIN)))
                .setEnrollmentId(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AttributionContract.ENROLLMENT_ID)))
                .setTriggerTime(
                        cursor.getLong(
                                cursor.getColumnIndex(
                                        MeasurementTables.AttributionContract.TRIGGER_TIME)))
                .setRegistrant(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AttributionContract.REGISTRANT)))
                .build();
    }

    private AggregateReport getAggregateReportFrom(JSONObject rJSON)
            throws JSONException {
        return new AggregateReport.Builder()
                .setId(rJSON.getString("id"))
                .setPublisher(Uri.parse(rJSON.getString("publisher")))
                .setAttributionDestination(Uri.parse(rJSON.getString("attributionDestination")))
                .setSourceRegistrationTime(rJSON.getLong("sourceRegistrationTime"))
                .setScheduledReportTime(rJSON.getLong("scheduledReportTime"))
                .setEnrollmentId(rJSON.getString("enrollmentId"))
                .setDebugCleartextPayload(rJSON.getString("debugCleartextPayload"))
                .setStatus(rJSON.getInt("status"))
                .setApiVersion(rJSON.optString("apiVersion", null))
                .build();
    }

    private Uri parseIfNonNull(String s) {
        if (s == null) {
            return null;
        }
        return Uri.parse(s);
    }
}
