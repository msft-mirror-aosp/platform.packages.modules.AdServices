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
import android.net.Uri;

import androidx.annotation.NonNull;

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
                        MeasurementTables.SourceContract.ID + " IN (?)",
                        new String[]{sources.stream().map(Source::getId)
                                .collect(Collectors.joining(","))}
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
                new String[]{source.getId()},
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
                BaseUriExtractor.getBaseUri(source.getAttributionSource()));
        values.put(MeasurementTables.AttributionRateLimitContract.DESTINATION_SITE,
                BaseUriExtractor.getBaseUri(trigger.getAttributionDestination()));
        values.put(MeasurementTables.AttributionRateLimitContract.REPORT_TO,
                trigger.getReportTo().toString());
        values.put(MeasurementTables.AttributionRateLimitContract.TRIGGER_TIME,
                trigger.getTriggerTime());
        values.put(MeasurementTables.AttributionRateLimitContract.REGISTERER,
                BaseUriExtractor.getBaseUri(trigger.getRegisterer()));
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
                        BaseUriExtractor.getBaseUri(source.getAttributionSource()),
                        BaseUriExtractor.getBaseUri(trigger.getAttributionDestination()),
                        trigger.getReportTo().toString(),
                        String.valueOf(trigger.getTriggerTime()
                                - PrivacyParams.RATE_LIMIT_WINDOW_MILLISECONDS),
                        String.valueOf(trigger.getTriggerTime())}
        );
    }

    @Override
    public long getNumSourcesPerRegisterer(Uri registerer) throws DatastoreException {
        return DatabaseUtils.queryNumEntries(
                mSQLTransaction.getDatabase(),
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.REGISTERER + " = ? ",
                new String[]{registerer.toString()});
    }

    @Override
    public long getNumTriggersPerRegisterer(Uri registerer) throws DatastoreException {
        return DatabaseUtils.queryNumEntries(
                mSQLTransaction.getDatabase(),
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.REGISTERER + " = ? ",
                new String[]{registerer.toString()});
    }

    @Override
    public void deleteAppRecords(Uri uri) throws DatastoreException {
        String uriStr = uri.toString();
        SQLiteDatabase db = mSQLTransaction.getDatabase();
        // For all Source records matching the given Uri
        // as REGISTERER, obtains EventReport records who's SOURCE_ID
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
                        MeasurementTables.SourceContract.REGISTERER),
                new String[]{uriStr});
        // EventReport table
        db.delete(MeasurementTables.EventReportContract.TABLE,
                MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION + " = ?",
                new String[]{uriStr});
        // Source table
        db.delete(MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.REGISTERER + " = ?",
                new String[]{uriStr});
        // Trigger table
        db.delete(MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.REGISTERER + " = ?",
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
            @NonNull Uri registerer,
            @Nullable Uri origin,
            @Nullable Instant start,
            @Nullable Instant end) throws DatastoreException {
        Objects.requireNonNull(registerer);
        validateOptionalRange(start, end);
        final SQLiteDatabase db = mSQLTransaction.getDatabase();
        deleteMeasurementData(db, registerer, origin, start, end);
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
            SQLiteDatabase db, Uri registerer, Uri origin, Instant start, Instant end) {
        if (origin == null && start == null) {
            // Deletes all measurement data
            deleteAttributionRateLimitByRegisterer(db, registerer);
            deleteEventReportByRegisterer(db, registerer);
            deleteTriggerByRegisterer(db, registerer);
            deleteSourceByRegisterer(db, registerer);
        } else if (start == null) {
            // Deletes all measurement data by uri
            deleteAttributionRateLimitByRegistererAndUri(db, registerer, origin);
            deleteEventReportByRegistererAndUri(db, registerer, origin);
            deleteTriggerByRegistererAndUri(db, registerer, origin);
            deleteSourceByRegistererAndUri(db, registerer, origin);
        } else if (origin == null) {
            // Deletes all measurement data by date range
            deleteAttributionRateLimitByRegistererAndRange(db, registerer, start, end);
            deleteEventReportByRegistererAndRange(db, registerer, start, end);
            deleteTriggerByRegistererAndRange(db, registerer, start, end);
            deleteSourceByRegistererAndRange(db, registerer, start, end);
        } else {
            // Deletes all measurement data by uri and date range
            deleteAttributionRateLimitByRegistererAndUriAndRange(
                    db, registerer, origin, start, end);
            deleteEventReportByRegistererAndUriAndRange(db, registerer, origin, start, end);
            deleteTriggerByRegistererAndUriAndRange(db, registerer, origin, start, end);
            deleteSourceByRegistererAndUriAndRange(db, registerer, origin, start, end);
        }
    }

    private void deleteSourceByRegisterer(SQLiteDatabase db, Uri registerer) {
        db.delete(MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.REGISTERER + " = ?",
                new String[]{registerer.toString()});
    }

    private void deleteSourceByRegistererAndUri(
            SQLiteDatabase db, Uri registerer, Uri attributionSource) {
        db.delete(MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.REGISTERER + " = ? AND "
                        + MeasurementTables.SourceContract.ATTRIBUTION_SOURCE + " = ?",
                new String[]{registerer.toString(), attributionSource.toString()});
    }

    private void deleteSourceByRegistererAndRange(
            SQLiteDatabase db, Uri registerer, Instant start, Instant end) {
        db.delete(MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.REGISTERER + " = ? AND "
                        + MeasurementTables.SourceContract.EVENT_TIME + " >= ? AND "
                        + MeasurementTables.SourceContract.EVENT_TIME + " <= ?",
                new String[]{
                        registerer.toString(),
                        String.valueOf(start.toEpochMilli()),
                        String.valueOf(end.toEpochMilli())
                });
    }

    private void deleteSourceByRegistererAndUriAndRange(
            SQLiteDatabase db, Uri registerer, Uri attributionSource, Instant start, Instant end) {
        db.delete(MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.REGISTERER + " = ? AND "
                        + MeasurementTables.SourceContract.ATTRIBUTION_SOURCE + " = ? AND "
                        + MeasurementTables.SourceContract.EVENT_TIME + " >= ? AND "
                        + MeasurementTables.SourceContract.EVENT_TIME + " <= ?",
                new String[]{
                        registerer.toString(),
                        attributionSource.toString(),
                        String.valueOf(start.toEpochMilli()),
                        String.valueOf(end.toEpochMilli())
                });
    }

    private void deleteTriggerByRegisterer(SQLiteDatabase db, Uri registerer) {
        db.delete(MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.REGISTERER + " = ?",
                new String[]{registerer.toString()});
    }

    private void deleteTriggerByRegistererAndUri(
            SQLiteDatabase db, Uri registerer, Uri attributionDestination) {
        db.delete(MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.REGISTERER + " = ? AND "
                        + MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION + " = ?",
                new String[]{registerer.toString(), attributionDestination.toString()});
    }

    private void deleteTriggerByRegistererAndRange(
            SQLiteDatabase db, Uri registerer, Instant start, Instant end) {
        db.delete(MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.REGISTERER + " = ? AND "
                        + MeasurementTables.TriggerContract.TRIGGER_TIME + " >= ? AND "
                        + MeasurementTables.TriggerContract.TRIGGER_TIME + " <= ?",
                new String[]{
                        registerer.toString(),
                        String.valueOf(start.toEpochMilli()),
                        String.valueOf(end.toEpochMilli())
                });
    }

    private void deleteTriggerByRegistererAndUriAndRange(
            SQLiteDatabase db,
            Uri registerer,
            Uri attributionDestination,
            Instant start,
            Instant end) {
        db.delete(MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.REGISTERER + " = ? AND "
                        + MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION + " = ? AND "
                        + MeasurementTables.TriggerContract.TRIGGER_TIME + " >= ? AND "
                        + MeasurementTables.TriggerContract.TRIGGER_TIME + " <= ?",
                new String[]{
                        registerer.toString(),
                        attributionDestination.toString(),
                        String.valueOf(start.toEpochMilli()),
                        String.valueOf(end.toEpochMilli())
                });
    }

    private void deleteEventReportByRegisterer(SQLiteDatabase db, Uri registerer) {
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
                        MeasurementTables.SourceContract.REGISTERER),
                new String[]{registerer.toString()});
    }

    private void deleteEventReportByRegistererAndUri(SQLiteDatabase db, Uri registerer, Uri site) {
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
                        MeasurementTables.SourceContract.REGISTERER,
                        MeasurementTables.SourceContract.ATTRIBUTION_SOURCE,
                        MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION),
                new String[]{registerer.toString(), site.toString(), site.toString()});
    }

    private void deleteEventReportByRegistererAndRange(
            SQLiteDatabase db, Uri registerer, Instant start, Instant end) {
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
                        MeasurementTables.SourceContract.REGISTERER,
                        MeasurementTables.SourceContract.EVENT_TIME,
                        MeasurementTables.EventReportContract.TRIGGER_TIME),
                new String[]{
                        registerer.toString(),
                        startValue,
                        endValue,
                        startValue,
                        endValue
                });
    }

    private void deleteEventReportByRegistererAndUriAndRange(
            SQLiteDatabase db, Uri registerer, Uri site, Instant start, Instant end) {
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
                        MeasurementTables.SourceContract.REGISTERER,
                        MeasurementTables.SourceContract.ATTRIBUTION_SOURCE,
                        MeasurementTables.SourceContract.EVENT_TIME,
                        MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION,
                        MeasurementTables.EventReportContract.TRIGGER_TIME),
                new String[]{
                        registerer.toString(),
                        site.toString(),
                        startValue,
                        endValue,
                        site.toString(),
                        startValue,
                        endValue
                });
    }

    private void deleteAttributionRateLimitByRegisterer(SQLiteDatabase db, Uri registerer) {
        db.delete(MeasurementTables.AttributionRateLimitContract.TABLE,
                MeasurementTables.AttributionRateLimitContract.REGISTERER + " = ?",
                new String[]{registerer.toString()});
    }

    private void deleteAttributionRateLimitByRegistererAndUri(
            SQLiteDatabase db, Uri registerer, Uri site) {
        db.delete(MeasurementTables.AttributionRateLimitContract.TABLE,
                String.format("%1$s = ? AND (%2$s = ? OR %3$s = ?)",
                        MeasurementTables.AttributionRateLimitContract.REGISTERER,
                        MeasurementTables.AttributionRateLimitContract.SOURCE_SITE,
                        MeasurementTables.AttributionRateLimitContract.DESTINATION_SITE),
                new String[]{registerer.toString(), site.toString(), site.toString()});
    }

    private void deleteAttributionRateLimitByRegistererAndRange(
            SQLiteDatabase db, Uri registerer, Instant start, Instant end) {
        db.delete(MeasurementTables.AttributionRateLimitContract.TABLE,
                MeasurementTables.AttributionRateLimitContract.REGISTERER + " = ? AND "
                        + MeasurementTables.AttributionRateLimitContract.TRIGGER_TIME + " >= ? AND "
                        + MeasurementTables.AttributionRateLimitContract.TRIGGER_TIME + " <= ?",
                new String[]{
                        registerer.toString(),
                        String.valueOf(start.toEpochMilli()),
                        String.valueOf(end.toEpochMilli())});
    }

    private void deleteAttributionRateLimitByRegistererAndUriAndRange(
            SQLiteDatabase db, Uri registerer, Uri site, Instant start, Instant end) {
        db.delete(MeasurementTables.AttributionRateLimitContract.TABLE,
                String.format("%1$s = ? AND (%2$s = ? OR %3$s = ?) AND (%4$s >= ? AND %4$s <= ?)",
                        MeasurementTables.AttributionRateLimitContract.REGISTERER,
                        MeasurementTables.AttributionRateLimitContract.SOURCE_SITE,
                        MeasurementTables.AttributionRateLimitContract.DESTINATION_SITE,
                        MeasurementTables.AttributionRateLimitContract.TRIGGER_TIME),
                new String[]{
                        registerer.toString(),
                        site.toString(),
                        site.toString(),
                        String.valueOf(start.toEpochMilli()),
                        String.valueOf(end.toEpochMilli())});
    }
}
