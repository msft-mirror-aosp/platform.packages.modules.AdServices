/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.adservices.service.AdServicesConfig.MEASUREMENT_DELETE_EXPIRED_WINDOW_MS;

import android.annotation.Nullable;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.android.adservices.LogUtil;
import com.android.adservices.service.measurement.AdtechUrl;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.PrivacyParams;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.attribution.BaseUriExtractor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Data Access Object for the Measurement PPAPI module.
 */
class MeasurementDao implements IMeasurementDao {

    private static final String TAG = "MeasurementDao";
    private SQLTransaction mSQLTransaction;

    @Override
    public void setTransaction(ITransaction transaction) {
        if (!(transaction instanceof SQLTransaction)) {
            throw new IllegalArgumentException("transaction should be a SQLTransaction.");
        }
        mSQLTransaction = (SQLTransaction) transaction;
    }

    @Override
    public void insertTrigger(@NonNull Uri attributionDestination, @NonNull Uri reportTo,
            @NonNull Uri registrant, @NonNull Long triggerTime, @NonNull Long triggerData,
            @Nullable Long dedupKey, @NonNull Long priority) throws DatastoreException {
        validateNonNull(attributionDestination, reportTo, registrant, triggerTime, triggerData,
                priority);
        validateUri(attributionDestination, reportTo, registrant);

        ContentValues values = new ContentValues();
        values.put(MeasurementTables.TriggerContract.ID, UUID.randomUUID().toString());
        values.put(MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION,
                attributionDestination.toString());
        values.put(MeasurementTables.TriggerContract.TRIGGER_TIME, triggerTime);
        values.put(MeasurementTables.TriggerContract.TRIGGER_DATA, triggerData);
        values.put(MeasurementTables.TriggerContract.DEDUP_KEY, dedupKey);
        values.put(MeasurementTables.TriggerContract.PRIORITY, priority);
        values.put(MeasurementTables.TriggerContract.STATUS, Trigger.Status.PENDING);
        values.put(MeasurementTables.TriggerContract.REPORT_TO, reportTo.toString());
        values.put(MeasurementTables.TriggerContract.REGISTRANT, registrant.toString());
        long rowId = mSQLTransaction.getDatabase()
                .insert(MeasurementTables.TriggerContract.TABLE,
                        /*nullColumnHack=*/null, values);
        LogUtil.d("MeasurementDao: insertTrigger: rowId=" + rowId);
        if (rowId == -1) {
            throw new DatastoreException("Trigger insertion failed.");
        }
    }

    @Override
    public List<String> getPendingTriggerIds() throws DatastoreException {
        try (Cursor cursor = mSQLTransaction.getDatabase().query(
                MeasurementTables.TriggerContract.TABLE,
                new String[]{MeasurementTables.TriggerContract.ID},
                MeasurementTables.TriggerContract.STATUS + " = ? ",
                new String[]{String.valueOf(Trigger.Status.PENDING)},
                /*groupBy=*/null, /*having=*/null,
                /*orderBy=*/MeasurementTables.TriggerContract.TRIGGER_TIME, /*limit=*/null)) {
            List<String> result = new ArrayList<>();
            while (cursor.moveToNext()) {
                result.add(cursor.getString(/*columnIndex=*/0));
            }
            return result;
        }
    }

    @Override
    public Trigger getTrigger(String triggerId) throws DatastoreException {
        try (Cursor cursor = mSQLTransaction.getDatabase().query(
                MeasurementTables.TriggerContract.TABLE,
                /*columns=*/null,
                MeasurementTables.TriggerContract.ID + " = ? ",
                new String[]{triggerId},
                /*groupBy=*/null, /*having=*/null, /*orderBy=*/null, /*limit=*/null)) {
            if (cursor.getCount() == 0) {
                throw new DatastoreException("Trigger retrieval failed. Id: " + triggerId);
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructTriggerFromCursor(cursor);
        }
    }

    @Override
    public EventReport getEventReport(String eventReportId) throws DatastoreException {
        try (Cursor cursor = mSQLTransaction.getDatabase().query(
                MeasurementTables.EventReportContract.TABLE,
                null,
                MeasurementTables.EventReportContract.ID + " = ? ",
                new String[]{eventReportId},
                null,
                null,
                null,
                null)) {
            if (cursor.getCount() == 0) {
                throw new DatastoreException(
                        "EventReport retrieval failed. Id: " + eventReportId);
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructEventReportFromCursor(cursor);
        }
    }

    @Override
    public void insertSource(@NonNull Long sourceEventId, @NonNull Uri attributionSource,
            @NonNull Uri attributionDestination, @NonNull Uri reportTo, @NonNull Uri registrant,
            @NonNull Long sourceEventTime, @NonNull Long expiryTime, @NonNull Long priority,
            @NonNull Source.SourceType sourceType) throws DatastoreException {
        validateNonNull(sourceEventId, attributionSource, attributionDestination, reportTo,
                registrant, sourceEventTime, expiryTime, priority, sourceType);
        validateUri(attributionSource, attributionDestination, reportTo, registrant);

        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceContract.ID, UUID.randomUUID().toString());
        values.put(MeasurementTables.SourceContract.EVENT_ID, sourceEventId);
        values.put(MeasurementTables.SourceContract.ATTRIBUTION_SOURCE,
                attributionSource.toString());
        values.put(MeasurementTables.SourceContract.ATTRIBUTION_DESTINATION,
                attributionDestination.toString());
        values.put(MeasurementTables.SourceContract.REPORT_TO, reportTo.toString());
        values.put(MeasurementTables.SourceContract.EVENT_TIME, sourceEventTime);
        values.put(MeasurementTables.SourceContract.EXPIRY_TIME, expiryTime);
        values.put(MeasurementTables.SourceContract.PRIORITY, priority);
        values.put(MeasurementTables.SourceContract.STATUS, Source.Status.ACTIVE);
        values.put(MeasurementTables.SourceContract.SOURCE_TYPE, sourceType.name());
        values.put(MeasurementTables.SourceContract.REGISTRANT, registrant.toString());
        long rowId = mSQLTransaction.getDatabase()
                .insert(MeasurementTables.SourceContract.TABLE,
                        /*nullColumnHack=*/null, values);
        LogUtil.d("MeasurementDao: insertSource: rowId=" + rowId);

        if (rowId == -1) {
            throw new DatastoreException("Source insertion failed.");
        }
    }

    @Override
    public List<Source> getMatchingActiveSources(Trigger trigger) throws DatastoreException {
        try (Cursor cursor = mSQLTransaction.getDatabase().query(
                MeasurementTables.SourceContract.TABLE,
                /*columns=*/null,
                MeasurementTables.SourceContract.ATTRIBUTION_DESTINATION + " = ? AND "
                        + MeasurementTables.SourceContract.REPORT_TO + " = ? AND "
                        + MeasurementTables.SourceContract.EXPIRY_TIME + " > ? AND "
                        + MeasurementTables.SourceContract.STATUS + " != ?",
                new String[]{trigger.getAttributionDestination().toString(),
                        trigger.getReportTo().toString(),
                        String.valueOf(trigger.getTriggerTime()),
                        String.valueOf(Trigger.Status.IGNORED)},
                /*groupBy=*/null, /*having=*/null, /*orderBy=*/null, /*limit=*/null)) {
            List<Source> sources = new ArrayList<>();
            while (cursor.moveToNext()) {
                sources.add(SqliteObjectMapper.constructSourceFromCursor(cursor));
            }
            return sources;
        }
    }

    @Override
    public void updateTriggerStatus(Trigger trigger) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.TriggerContract.STATUS, trigger.getStatus());
        long rows = mSQLTransaction.getDatabase()
                .update(MeasurementTables.TriggerContract.TABLE, values,
                        MeasurementTables.TriggerContract.ID + " = ?",
                        new String[]{trigger.getId()});
        if (rows != 1) {
            throw new DatastoreException("Trigger status update failed.");
        }
    }

    @Override
    public void updateSourceStatus(List<Source> sources, @Source.Status int status)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceContract.STATUS, status);
        long rows = mSQLTransaction.getDatabase()
                .update(MeasurementTables.SourceContract.TABLE, values,
                        MeasurementTables.SourceContract.ID + " IN ("
                                + Stream.generate(() -> "?").limit(sources.size())
                                .collect(Collectors.joining(",")) + ")",
                        sources.stream().map(Source::getId).toArray(String[]::new)
                );
        if (rows != sources.size()) {
            throw new DatastoreException("Source status update failed.");
        }
    }

    @Override
    public void markEventReportDelivered(String eventReportId) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.EventReportContract.STATUS, EventReport.Status.DELIVERED);
        long rows = mSQLTransaction.getDatabase()
                .update(MeasurementTables.EventReportContract.TABLE, values,
                        MeasurementTables.EventReportContract.ID + " = ?",
                        new String[]{eventReportId});
        if (rows != 1) {
            throw new DatastoreException("EventReport update failed.");
        }
    }

    @Override
    @Nullable
    public List<EventReport> getSourceEventReports(Source source) throws DatastoreException {
        List<EventReport> eventReports = new ArrayList<>();
        try (Cursor cursor = mSQLTransaction.getDatabase().query(
                MeasurementTables.EventReportContract.TABLE,
                /*columns=*/null,
                MeasurementTables.EventReportContract.SOURCE_ID + " = ? ",
                new String[]{String.valueOf(source.getEventId())},
                /*groupBy=*/null, /*having=*/null, /*orderBy=*/null, /*limit=*/null)) {
            while (cursor.moveToNext()) {
                eventReports.add(SqliteObjectMapper.constructEventReportFromCursor(cursor));
            }
            return eventReports;
        }
    }

    @Override
    public void deleteEventReport(EventReport eventReport) throws DatastoreException {
        long rows = mSQLTransaction.getDatabase()
                .delete(MeasurementTables.EventReportContract.TABLE,
                        MeasurementTables.EventReportContract.ID + " = ?",
                        new String[]{eventReport.getId()});
        if (rows != 1) {
            throw new DatastoreException("EventReport deletion failed.");
        }
    }

    @Override
    public List<String> getPendingEventReportIdsInWindow(long windowStartTime, long windowEndTime)
            throws DatastoreException {
        List<String> eventReports = new ArrayList<>();
        try (Cursor cursor = mSQLTransaction.getDatabase().query(
                MeasurementTables.EventReportContract.TABLE,
                /*columns=*/null,
                MeasurementTables.EventReportContract.REPORT_TIME + " >= ? AND "
                + MeasurementTables.EventReportContract.REPORT_TIME + " <= ? AND "
                + MeasurementTables.EventReportContract.STATUS + " = ? ",
                new String[]{String.valueOf(windowStartTime), String.valueOf(windowEndTime),
                String.valueOf(EventReport.Status.PENDING)},
                /*groupBy=*/null, /*having=*/null, /*orderBy=*/"RANDOM()", /*limit=*/null)) {
            while (cursor.moveToNext()) {
                eventReports.add(cursor.getString(cursor.getColumnIndex(
                        MeasurementTables.EventReportContract.ID)));
            }
            return eventReports;
        }
    }

    @Override
    public List<String> getPendingEventReportIdsForGivenApp(Uri appName)
            throws DatastoreException {
        List<String> eventReports = new ArrayList<>();
        try (Cursor cursor = mSQLTransaction.getDatabase().rawQuery(
                String.format("SELECT e.%1$s FROM %2$s e "
                                + "INNER JOIN %3$s s ON (e.%4$s = s.%5$s) "
                                + "WHERE e.%6$s = ? AND s.%7$s = ?",
                        MeasurementTables.EventReportContract.ID,
                        MeasurementTables.EventReportContract.TABLE,
                        MeasurementTables.SourceContract.TABLE,
                        MeasurementTables.EventReportContract.SOURCE_ID,
                        MeasurementTables.SourceContract.EVENT_ID,
                        MeasurementTables.EventReportContract.STATUS,
                        MeasurementTables.SourceContract.REGISTRANT),
                new String[]{String.valueOf(EventReport.Status.PENDING),
                        String.valueOf(appName)})) {
            while (cursor.moveToNext()) {
                eventReports.add(cursor.getString(cursor.getColumnIndex(
                        MeasurementTables.EventReportContract.ID)));
            }
            return eventReports;
        }
    }

    @Override
    public void insertEventReport(EventReport eventReport) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.EventReportContract.ID,
                UUID.randomUUID().toString());
        values.put(MeasurementTables.EventReportContract.SOURCE_ID,
                eventReport.getSourceId());
        values.put(MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION,
                eventReport.getAttributionDestination().toString());
        values.put(MeasurementTables.EventReportContract.TRIGGER_TIME,
                eventReport.getTriggerTime());
        values.put(MeasurementTables.EventReportContract.TRIGGER_DATA,
                eventReport.getTriggerData());
        values.put(MeasurementTables.EventReportContract.TRIGGER_DEDUP_KEY,
                eventReport.getTriggerDedupKey());
        values.put(MeasurementTables.EventReportContract.REPORT_TO,
                eventReport.getReportTo().toString());
        values.put(MeasurementTables.EventReportContract.STATUS,
                eventReport.getStatus());
        values.put(MeasurementTables.EventReportContract.REPORT_TIME,
                eventReport.getReportTime());
        values.put(MeasurementTables.EventReportContract.TRIGGER_PRIORITY,
                eventReport.getTriggerPriority());
        values.put(MeasurementTables.EventReportContract.SOURCE_TYPE,
                eventReport.getSourceType().toString());
        long rowId = mSQLTransaction.getDatabase()
                .insert(MeasurementTables.EventReportContract.TABLE,
                        /*nullColumnHack=*/null, values);
        if (rowId == -1) {
            throw new DatastoreException("EventReport insertion failed.");
        }
    }

    @Override
    public void updateSourceDedupKeys(Source source) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceContract.DEDUP_KEYS,
                source.getDedupKeys().stream().map(String::valueOf).collect(
                        Collectors.joining(",")));
        long rows = mSQLTransaction.getDatabase()
                .update(MeasurementTables.SourceContract.TABLE, values,
                        MeasurementTables.SourceContract.ID + " = ?",
                        new String[]{source.getId()});
        if (rows != 1) {
            throw new DatastoreException("Source dedup key updated failed.");
        }
    }

    @Override
    public void insertAttributionRateLimit(Source source, Trigger trigger)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AttributionRateLimitContract.ID,
                UUID.randomUUID().toString());
        values.put(MeasurementTables.AttributionRateLimitContract.SOURCE_SITE,
                BaseUriExtractor.getBaseUri(source.getAttributionSource()).toString());
        values.put(MeasurementTables.AttributionRateLimitContract.DESTINATION_SITE,
                BaseUriExtractor.getBaseUri(trigger.getAttributionDestination()).toString());
        values.put(MeasurementTables.AttributionRateLimitContract.REPORT_TO,
                trigger.getReportTo().toString());
        values.put(MeasurementTables.AttributionRateLimitContract.TRIGGER_TIME,
                trigger.getTriggerTime());
        values.put(MeasurementTables.AttributionRateLimitContract.REGISTRANT,
                trigger.getRegistrant().toString());
        long rowId = mSQLTransaction.getDatabase()
                .insert(MeasurementTables.AttributionRateLimitContract.TABLE,
                        /*nullColumnHack=*/null,
                        values);
        if (rowId == -1) {
            throw new DatastoreException("AttributionRateLimit insertion failed.");
        }
    }

    @Override
    public long getAttributionsPerRateLimitWindow(Source source, Trigger trigger)
            throws DatastoreException {
        return DatabaseUtils.queryNumEntries(
                mSQLTransaction.getDatabase(),
                MeasurementTables.AttributionRateLimitContract.TABLE,
                MeasurementTables.AttributionRateLimitContract.SOURCE_SITE + " = ? AND "
                        + MeasurementTables.AttributionRateLimitContract.DESTINATION_SITE
                        + " = ? AND "
                        + MeasurementTables.AttributionRateLimitContract.REPORT_TO
                        + " = ? AND "
                        + MeasurementTables.AttributionRateLimitContract.TRIGGER_TIME
                        + " >= ? AND "
                        + MeasurementTables.AttributionRateLimitContract.TRIGGER_TIME
                        + " <= ? ",
                new String[]{
                        BaseUriExtractor.getBaseUri(source.getAttributionSource()).toString(),
                        BaseUriExtractor.getBaseUri(trigger.getAttributionDestination()).toString(),
                        trigger.getReportTo().toString(),
                        String.valueOf(trigger.getTriggerTime()
                                - PrivacyParams.RATE_LIMIT_WINDOW_MILLISECONDS),
                        String.valueOf(trigger.getTriggerTime())}
        );
    }

    @Override
    public long getNumSourcesPerRegistrant(Uri registrant) throws DatastoreException {
        return DatabaseUtils.queryNumEntries(
                mSQLTransaction.getDatabase(),
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.REGISTRANT + " = ? ",
                new String[]{registrant.toString()});
    }

    @Override
    public long getNumTriggersPerRegistrant(Uri registrant) throws DatastoreException {
        return DatabaseUtils.queryNumEntries(
                mSQLTransaction.getDatabase(),
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.REGISTRANT + " = ? ",
                new String[]{registrant.toString()});
    }

    @Override
    public void deleteAppRecords(Uri uri) throws DatastoreException {
        String uriStr = uri.toString();
        SQLiteDatabase db = mSQLTransaction.getDatabase();
        // For all Source records matching the given Uri
        // as REGISTRANT, obtains EventReport records who's SOURCE_ID
        // matches a Source records' EVENT_ID.
        db.delete(MeasurementTables.EventReportContract.TABLE,
                String.format("%1$s IN ("
                                + "SELECT e.%1$s FROM %2$s e"
                                + " INNER JOIN %3$s s"
                                + " ON (e.%4$s = s.%5$s AND e.%6$s = s.%7$s AND e.%8$s = s.%9$s)"
                                + " WHERE s.%10$s = ?"
                                + ")",
                        MeasurementTables.EventReportContract.ID,
                        MeasurementTables.EventReportContract.TABLE,
                        MeasurementTables.SourceContract.TABLE,
                        MeasurementTables.EventReportContract.SOURCE_ID,
                        MeasurementTables.SourceContract.EVENT_ID,
                        MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION,
                        MeasurementTables.SourceContract.ATTRIBUTION_DESTINATION,
                        MeasurementTables.EventReportContract.REPORT_TO,
                        MeasurementTables.SourceContract.REPORT_TO,
                        MeasurementTables.SourceContract.REGISTRANT),
                new String[]{uriStr});
        // EventReport table
        db.delete(MeasurementTables.EventReportContract.TABLE,
                MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION + " = ?",
                new String[]{uriStr});
        // Source table
        db.delete(MeasurementTables.SourceContract.TABLE,
                "( " + MeasurementTables.SourceContract.REGISTRANT + " = ? ) OR "
                        + "(" + MeasurementTables.SourceContract.STATUS + " = ? AND "
                        + MeasurementTables.SourceContract.ATTRIBUTION_DESTINATION + " = ? )",
                new String[]{uriStr, String.valueOf(Source.Status.IGNORED), uriStr});
        // Trigger table
        db.delete(MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.REGISTRANT + " = ?",
                new String[]{uriStr});
        // AttributionRateLimit table
        db.delete(MeasurementTables.AttributionRateLimitContract.TABLE,
                MeasurementTables.AttributionRateLimitContract.SOURCE_SITE + " = ? OR "
                        + MeasurementTables.AttributionRateLimitContract.DESTINATION_SITE + " = ?",
                new String[]{uriStr, uriStr});
    }

    @Override
    @Nullable
    public AdtechUrl getAdtechEnrollmentData(String postbackUrl) throws DatastoreException {
        try (Cursor cursor = mSQLTransaction.getDatabase()
                .query(MeasurementTables.AdTechUrlsContract.TABLE,
                        /*columns=*/null,
                        MeasurementTables.AdTechUrlsContract.POSTBACK_URL + " = ? ",
                        new String[]{postbackUrl},
                        /*groupBy=*/null, /*having=*/null, /*orderBy=*/null,
                        /*limit=*/null)) {
            if (cursor == null || cursor.getCount() == 0) {
                return null;
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructAdtechUrlFromCursor(cursor);
        }
    }

    @Override
    public List<String> getAllAdtechUrls(String postbackUrl) throws DatastoreException {
        List<String> res = new ArrayList<>();
        AdtechUrl adtechUrl = getAdtechEnrollmentData(postbackUrl);
        if (adtechUrl == null) {
            return res;
        }
        String adtechId = adtechUrl.getAdtechId();
        if (adtechId == null) {
            return res;
        }
        try (Cursor cursor = mSQLTransaction.getDatabase()
                .query(MeasurementTables.AdTechUrlsContract.TABLE,
                        /*columns=*/null,
                        MeasurementTables.AdTechUrlsContract.AD_TECH_ID + " = ? ",
                        new String[]{adtechId},
                        /*groupBy=*/null, /*having=*/null, /*orderBy=*/null,
                        /*limit=*/null)) {
            if (cursor == null) {
                return res;
            }
            while (cursor.moveToNext()) {
                res.add(SqliteObjectMapper.constructAdtechUrlFromCursor(cursor).getPostbackUrl());
            }
            return res;
        }
    }

    @Override
    public void insertAdtechUrl(AdtechUrl adtechUrl) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AdTechUrlsContract.POSTBACK_URL, adtechUrl.getPostbackUrl());
        values.put(MeasurementTables.AdTechUrlsContract.AD_TECH_ID, adtechUrl.getAdtechId());
        long rowId = mSQLTransaction.getDatabase()
                .insert(MeasurementTables.AdTechUrlsContract.TABLE,
                        /*nullColumnHack=*/null, values);
        if (rowId == -1) {
            throw new DatastoreException("AdTechURL insertion failed.");
        }
    }

    @Override
    public void deleteAdtechUrl(String postbackUrl) throws DatastoreException {
        long rows = mSQLTransaction.getDatabase()
                .delete(MeasurementTables.AdTechUrlsContract.TABLE,
                        MeasurementTables.AdTechUrlsContract.POSTBACK_URL + " = ?",
                        new String[]{postbackUrl});
        if (rows != 1) {
            throw new DatastoreException("AdTechURL deletion failed.");
        }
    }

    @Override
    public void deleteExpiredRecords() throws DatastoreException {
        SQLiteDatabase db = mSQLTransaction.getDatabase();
        long earliestValidInsertion =
                System.currentTimeMillis() - MEASUREMENT_DELETE_EXPIRED_WINDOW_MS;
        String earliestValidInsertionStr = String.valueOf(earliestValidInsertion);
        // Source table
        db.delete(MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.EVENT_TIME + " < ?",
                new String[]{earliestValidInsertionStr});
        // Trigger table
        db.delete(MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.TRIGGER_TIME + " < ?",
                new String[]{earliestValidInsertionStr});
        // EventReport table
        db.delete(MeasurementTables.EventReportContract.TABLE,
                MeasurementTables.EventReportContract.STATUS + " = ? OR "
                        + MeasurementTables.EventReportContract.REPORT_TIME + " < ?",
                new String[]{
                        String.valueOf(EventReport.Status.DELIVERED),
                        earliestValidInsertionStr});
        // AttributionRateLimit table
        db.delete(MeasurementTables.AttributionRateLimitContract.TABLE,
                MeasurementTables.AttributionRateLimitContract.TRIGGER_TIME + " < ?",
                new String[]{earliestValidInsertionStr});
    }

    @Override
    public void deleteMeasurementData(
            @NonNull Uri registrant,
            @Nullable Uri origin,
            @Nullable Instant start,
            @Nullable Instant end) throws DatastoreException {
        Objects.requireNonNull(registrant);
        validateOptionalRange(start, end);
        final SQLiteDatabase db = mSQLTransaction.getDatabase();
        deleteMeasurementData(db, registrant, origin, start, end);
    }

    private void validateOptionalRange(Instant start, Instant end) {
        if (start == null ^ end == null) {
            throw new IllegalArgumentException(
                    "invalid range, both start and end dates must be provided if providing any");
        }
        if (start != null && start.isAfter(end)) {
            throw new IllegalArgumentException(
                    "invalid range, start date must be equal or before end date");
        }
    }

    private void deleteMeasurementData(
            SQLiteDatabase db, Uri registrant, Uri origin, Instant start, Instant end) {
        if (origin == null && start == null) {
            // Deletes all measurement data
            deleteAttributionRateLimitByRegistrant(db, registrant);
            deleteEventReportByRegistrant(db, registrant);
            deleteTriggerByRegistrant(db, registrant);
            deleteSourceByRegistrant(db, registrant);
        } else if (start == null) {
            // Deletes all measurement data by uri
            deleteAttributionRateLimitByRegistrantAndUri(db, registrant, origin);
            deleteEventReportByRegistrantAndUri(db, registrant, origin);
            deleteTriggerByRegistrantAndUri(db, registrant, origin);
            deleteSourceByRegistrantAndUri(db, registrant, origin);
        } else if (origin == null) {
            // Deletes all measurement data by date range
            deleteAttributionRateLimitByRegistrantAndRange(db, registrant, start, end);
            deleteEventReportByRegistrantAndRange(db, registrant, start, end);
            deleteTriggerByRegistrantAndRange(db, registrant, start, end);
            deleteSourceByRegistrantAndRange(db, registrant, start, end);
        } else {
            // Deletes all measurement data by uri and date range
            deleteAttributionRateLimitByRegistrantAndUriAndRange(
                    db, registrant, origin, start, end);
            deleteEventReportByRegistrantAndUriAndRange(db, registrant, origin, start, end);
            deleteTriggerByRegistrantAndUriAndRange(db, registrant, origin, start, end);
            deleteSourceByRegistrantAndUriAndRange(db, registrant, origin, start, end);
        }
    }

    private void deleteSourceByRegistrant(SQLiteDatabase db, Uri registrant) {
        db.delete(MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.REGISTRANT + " = ?",
                new String[]{registrant.toString()});
    }

    private void deleteSourceByRegistrantAndUri(
            SQLiteDatabase db, Uri registrant, Uri attributionSource) {
        db.delete(MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.REGISTRANT + " = ? AND "
                        + MeasurementTables.SourceContract.ATTRIBUTION_SOURCE + " = ?",
                new String[]{registrant.toString(), attributionSource.toString()});
    }

    private void deleteSourceByRegistrantAndRange(
            SQLiteDatabase db, Uri registrant, Instant start, Instant end) {
        db.delete(MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.REGISTRANT + " = ? AND "
                        + MeasurementTables.SourceContract.EVENT_TIME + " >= ? AND "
                        + MeasurementTables.SourceContract.EVENT_TIME + " <= ?",
                new String[]{
                        registrant.toString(),
                        String.valueOf(start.toEpochMilli()),
                        String.valueOf(end.toEpochMilli())
                });
    }

    private void deleteSourceByRegistrantAndUriAndRange(
            SQLiteDatabase db, Uri registrant, Uri attributionSource, Instant start, Instant end) {
        db.delete(MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.REGISTRANT + " = ? AND "
                        + MeasurementTables.SourceContract.ATTRIBUTION_SOURCE + " = ? AND "
                        + MeasurementTables.SourceContract.EVENT_TIME + " >= ? AND "
                        + MeasurementTables.SourceContract.EVENT_TIME + " <= ?",
                new String[]{
                        registrant.toString(),
                        attributionSource.toString(),
                        String.valueOf(start.toEpochMilli()),
                        String.valueOf(end.toEpochMilli())
                });
    }

    private void deleteTriggerByRegistrant(SQLiteDatabase db, Uri registrant) {
        db.delete(MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.REGISTRANT + " = ?",
                new String[]{registrant.toString()});
    }

    private void deleteTriggerByRegistrantAndUri(
            SQLiteDatabase db, Uri registrant, Uri attributionDestination) {
        db.delete(MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.REGISTRANT + " = ? AND "
                        + MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION + " = ?",
                new String[]{registrant.toString(), attributionDestination.toString()});
    }

    private void deleteTriggerByRegistrantAndRange(
            SQLiteDatabase db, Uri registrant, Instant start, Instant end) {
        db.delete(MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.REGISTRANT + " = ? AND "
                        + MeasurementTables.TriggerContract.TRIGGER_TIME + " >= ? AND "
                        + MeasurementTables.TriggerContract.TRIGGER_TIME + " <= ?",
                new String[]{
                        registrant.toString(),
                        String.valueOf(start.toEpochMilli()),
                        String.valueOf(end.toEpochMilli())
                });
    }

    private void deleteTriggerByRegistrantAndUriAndRange(
            SQLiteDatabase db,
            Uri registrant,
            Uri attributionDestination,
            Instant start,
            Instant end) {
        db.delete(MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.REGISTRANT + " = ? AND "
                        + MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION + " = ? AND "
                        + MeasurementTables.TriggerContract.TRIGGER_TIME + " >= ? AND "
                        + MeasurementTables.TriggerContract.TRIGGER_TIME + " <= ?",
                new String[]{
                        registrant.toString(),
                        attributionDestination.toString(),
                        String.valueOf(start.toEpochMilli()),
                        String.valueOf(end.toEpochMilli())
                });
    }

    private void deleteEventReportByRegistrant(SQLiteDatabase db, Uri registrant) {
        db.delete(MeasurementTables.EventReportContract.TABLE,
                String.format("%1$s IN ("
                                + "SELECT e.%1$s FROM %2$s e "
                                + "INNER JOIN %3$s s ON (e.%4$s = s.%5$s) "
                                + "WHERE %6$s = ?"
                                + ")",
                        MeasurementTables.EventReportContract.ID,
                        MeasurementTables.EventReportContract.TABLE,
                        MeasurementTables.SourceContract.TABLE,
                        MeasurementTables.EventReportContract.SOURCE_ID,
                        MeasurementTables.SourceContract.EVENT_ID,
                        MeasurementTables.SourceContract.REGISTRANT),
                new String[]{registrant.toString()});
    }

    private void deleteEventReportByRegistrantAndUri(SQLiteDatabase db, Uri registrant, Uri site) {
        db.delete(MeasurementTables.EventReportContract.TABLE,
                String.format("%1$s IN ("
                                + "SELECT e.%1$s FROM %2$s e "
                                + "INNER JOIN %3$s s ON (e.%4$s = s.%5$s) "
                                + "WHERE s.%6$s = ? AND (s.%7$s = ? OR e.%8$s = ?)"
                                + ")",
                        MeasurementTables.EventReportContract.ID,
                        MeasurementTables.EventReportContract.TABLE,
                        MeasurementTables.SourceContract.TABLE,
                        MeasurementTables.EventReportContract.SOURCE_ID,
                        MeasurementTables.SourceContract.EVENT_ID,
                        MeasurementTables.SourceContract.REGISTRANT,
                        MeasurementTables.SourceContract.ATTRIBUTION_SOURCE,
                        MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION),
                new String[]{registrant.toString(), site.toString(), site.toString()});
    }

    private void deleteEventReportByRegistrantAndRange(
            SQLiteDatabase db, Uri registrant, Instant start, Instant end) {
        final String startValue = String.valueOf(start.toEpochMilli());
        final String endValue = String.valueOf(end.toEpochMilli());
        db.delete(MeasurementTables.EventReportContract.TABLE,
                String.format("%1$s IN ("
                                + "SELECT e.%1$s FROM %2$s e "
                                + "INNER JOIN %3$s s ON (e.%4$s = s.%5$s) "
                                + "WHERE %6$s = ? AND "
                                + "((%7$s >= ? AND %7$s <= ?) OR (%8$s >= ? AND %8$s <= ?))"
                                + ")",
                        MeasurementTables.EventReportContract.ID,
                        MeasurementTables.EventReportContract.TABLE,
                        MeasurementTables.SourceContract.TABLE,
                        MeasurementTables.EventReportContract.SOURCE_ID,
                        MeasurementTables.SourceContract.EVENT_ID,
                        MeasurementTables.SourceContract.REGISTRANT,
                        MeasurementTables.SourceContract.EVENT_TIME,
                        MeasurementTables.EventReportContract.TRIGGER_TIME),
                new String[]{
                        registrant.toString(),
                        startValue,
                        endValue,
                        startValue,
                        endValue
                });
    }

    private void deleteEventReportByRegistrantAndUriAndRange(
            SQLiteDatabase db, Uri registrant, Uri site, Instant start, Instant end) {
        final String startValue = String.valueOf(start.toEpochMilli());
        final String endValue = String.valueOf(end.toEpochMilli());
        db.delete(MeasurementTables.EventReportContract.TABLE,
                String.format("%1$s IN ("
                                + "SELECT e.%1$s FROM %2$s e "
                                + "INNER JOIN %3$s s ON (e.%4$s = s.%5$s) "
                                + "WHERE s.%6$s = ? AND "
                                + "((s.%7$s = ? AND s.%8$s >= ? AND s.%8$s <= ?) OR "
                                + "(e.%9$s = ? AND e.%10$s >= ? AND e.%10$s <= ?))"
                                + ")",
                        MeasurementTables.EventReportContract.ID,
                        MeasurementTables.EventReportContract.TABLE,
                        MeasurementTables.SourceContract.TABLE,
                        MeasurementTables.EventReportContract.SOURCE_ID,
                        MeasurementTables.SourceContract.EVENT_ID,
                        MeasurementTables.SourceContract.REGISTRANT,
                        MeasurementTables.SourceContract.ATTRIBUTION_SOURCE,
                        MeasurementTables.SourceContract.EVENT_TIME,
                        MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION,
                        MeasurementTables.EventReportContract.TRIGGER_TIME),
                new String[]{
                        registrant.toString(),
                        site.toString(),
                        startValue,
                        endValue,
                        site.toString(),
                        startValue,
                        endValue
                });
    }

    private void deleteAttributionRateLimitByRegistrant(SQLiteDatabase db, Uri registrant) {
        db.delete(MeasurementTables.AttributionRateLimitContract.TABLE,
                MeasurementTables.AttributionRateLimitContract.REGISTRANT + " = ?",
                new String[]{registrant.toString()});
    }

    private void deleteAttributionRateLimitByRegistrantAndUri(
            SQLiteDatabase db, Uri registrant, Uri site) {
        db.delete(MeasurementTables.AttributionRateLimitContract.TABLE,
                String.format("%1$s = ? AND (%2$s = ? OR %3$s = ?)",
                        MeasurementTables.AttributionRateLimitContract.REGISTRANT,
                        MeasurementTables.AttributionRateLimitContract.SOURCE_SITE,
                        MeasurementTables.AttributionRateLimitContract.DESTINATION_SITE),
                new String[]{registrant.toString(), site.toString(), site.toString()});
    }

    private void deleteAttributionRateLimitByRegistrantAndRange(
            SQLiteDatabase db, Uri registrant, Instant start, Instant end) {
        db.delete(MeasurementTables.AttributionRateLimitContract.TABLE,
                MeasurementTables.AttributionRateLimitContract.REGISTRANT + " = ? AND "
                        + MeasurementTables.AttributionRateLimitContract.TRIGGER_TIME + " >= ? AND "
                        + MeasurementTables.AttributionRateLimitContract.TRIGGER_TIME + " <= ?",
                new String[]{
                        registrant.toString(),
                        String.valueOf(start.toEpochMilli()),
                        String.valueOf(end.toEpochMilli())});
    }

    private void deleteAttributionRateLimitByRegistrantAndUriAndRange(
            SQLiteDatabase db, Uri registrant, Uri site, Instant start, Instant end) {
        db.delete(MeasurementTables.AttributionRateLimitContract.TABLE,
                String.format("%1$s = ? AND (%2$s = ? OR %3$s = ?) AND (%4$s >= ? AND %4$s <= ?)",
                        MeasurementTables.AttributionRateLimitContract.REGISTRANT,
                        MeasurementTables.AttributionRateLimitContract.SOURCE_SITE,
                        MeasurementTables.AttributionRateLimitContract.DESTINATION_SITE,
                        MeasurementTables.AttributionRateLimitContract.TRIGGER_TIME),
                new String[]{
                        registrant.toString(),
                        site.toString(),
                        site.toString(),
                        String.valueOf(start.toEpochMilli()),
                        String.valueOf(end.toEpochMilli())});
    }

    @Override
    public void doInstallAttribution(Uri uri, long eventTimestamp) throws DatastoreException {
        SQLiteDatabase db = mSQLTransaction.getDatabase();

        SQLiteQueryBuilder sqb = new SQLiteQueryBuilder();
        sqb.setTables(MeasurementTables.SourceContract.TABLE);
        // Sub query for selecting relevant source ids.
        // Selecting the highest priority, most recent source with eventTimestamp falling in the
        // source's install attribution window.
        String subQuery = sqb.buildQuery(new String[]{MeasurementTables.SourceContract.ID},
                String.format(MeasurementTables.SourceContract.ATTRIBUTION_DESTINATION
                                + " = \"%s\" AND "
                                + MeasurementTables.SourceContract.EVENT_TIME + " <= %2$d AND "
                                + MeasurementTables.SourceContract.EXPIRY_TIME + " > %2$d AND "
                                + MeasurementTables.SourceContract.EVENT_TIME + " + "
                                + MeasurementTables.SourceContract.INSTALL_ATTRIBUTION_WINDOW
                                + " >= %2$d",
                        uri.toString(), eventTimestamp),
                /* groupBy= */null, /* having= */null,
                /* sortOrder= */MeasurementTables.SourceContract.PRIORITY + " DESC, "
                        + MeasurementTables.SourceContract.EVENT_TIME + " DESC",
                /* limit = */ "1");

        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceContract.IS_INSTALL_ATTRIBUTED, true);
        db.update(MeasurementTables.SourceContract.TABLE,
                values,
                MeasurementTables.SourceContract.ID + " IN (" + subQuery + ")",
                null);
    }

    @Override
    public void undoInstallAttribution(Uri uri) throws DatastoreException {
        SQLiteDatabase db = mSQLTransaction.getDatabase();
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceContract.IS_INSTALL_ATTRIBUTED, false);
        db.update(MeasurementTables.SourceContract.TABLE,
                values,
                MeasurementTables.SourceContract.ATTRIBUTION_DESTINATION + " = ?",
                new String[]{uri.toString()});
    }

    private void validateNonNull(Object... objects) throws DatastoreException {
        for (Object o : objects) {
            if (o == null) {
                throw new DatastoreException("Received null values");
            }
        }
    }

    private void validateUri(Uri... uris) throws DatastoreException {
        for (Uri uri : uris) {
            if (uri == null || uri.getScheme() == null) {
                throw new DatastoreException("Uri with no scheme is not valid");
            }
        }
    }
}
