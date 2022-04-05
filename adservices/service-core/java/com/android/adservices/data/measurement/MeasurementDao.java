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
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.android.adservices.LogUtil;
import com.android.adservices.data.DbHelper;
import com.android.adservices.service.measurement.AdtechUrl;
import com.android.adservices.service.measurement.BaseUriExtractor;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.PrivacyParams;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Data Access Object for the Measurement PPAPI module.
 */
public class MeasurementDao {

    private static final String TAG = "MeasurementDAO";
    private static MeasurementDao sSingleton;
    private final DbHelper mDbHelper;

    private MeasurementDao(Context ctx) {
        mDbHelper = DbHelper.getInstance(ctx);
    }

    /** Returns an instance of the MeasurementDAO given a context. */
    public static synchronized MeasurementDao getInstance(Context ctx) {
        if (sSingleton == null) {
            sSingleton = new MeasurementDao(ctx);
        }
        return sSingleton;
    }

    /**
     * Returns list of ids for all pending {@link Trigger}.
     */
    public List<String> getPendingTriggerIds() {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return null;
        }
        try (Cursor cursor = db.query(
                MeasurementTables.TriggerContract.TABLE,
                new String[]{MeasurementTables.TriggerContract.ID},
                MeasurementTables.TriggerContract.STATUS + " = ? ",
                new String[]{String.valueOf(Trigger.Status.PENDING)},
                /*groupBy=*/null, /*having=*/null,
                /*orderBy=*/MeasurementTables.TriggerContract.TRIGGER_TIME, /*limit=*/null)) {
            if (cursor == null) {
                return null;
            }
            List<String> result = new ArrayList<>();
            while (cursor.moveToNext()) {
                result.add(cursor.getString(/*columnIndex=*/0));
            }
            return result;
        }
    }

    /**
     * Queries and returns the {@link Trigger}.
     *
     * @param triggerId Id of the request Trigger
     * @return the requested Trigger; Null in case of SQL failure
     */
    @Nullable
    public Trigger getTrigger(String triggerId) {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return null;
        }
        try (Cursor cursor = db.query(
                MeasurementTables.TriggerContract.TABLE,
                /*columns=*/null,
                MeasurementTables.TriggerContract.ID + " = ? ",
                new String[]{triggerId},
                /*groupBy=*/null, /*having=*/null, /*orderBy=*/null, /*limit=*/null)) {
            if (cursor == null || cursor.getCount() == 0) {
                return null;
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructTriggerFromCursor(cursor);
        }
    }

    /**
     * Queries and returns the {@link EventReport}.
     *
     * @param eventReportId Id of the request Event Report
     * @return the requested Event Report; Null in case of SQL failure
     */
    @Nullable
    public EventReport getEventReport(String eventReportId) {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return null;
        }
        try (Cursor cursor = db.query(
                MeasurementTables.EventReportContract.TABLE,
                null,
                MeasurementTables.EventReportContract.ID + " = ? ",
                new String[]{eventReportId},
                null,
                null,
                null,
                null)) {
            if (cursor == null || cursor.getCount() == 0) {
                return null;
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructEventReportFromCursor(cursor);
        }
    }

    /**
     * Queries and returns the list of matching {@link Source} for the provided {@link Trigger}.
     *
     * @return list of active matching sources; Null in case of SQL failure
     */
    @Nullable
    public List<Source> getMatchingActiveSources(Trigger trigger) {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return null;
        }
        try (Cursor cursor = db.query(
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
            if (cursor == null) {
                return null;
            }
            List<Source> sources = new ArrayList<>();
            while (cursor.moveToNext()) {
                sources.add(SqliteObjectMapper.constructSourceFromCursor(cursor));
            }
            return sources;
        }
    }

    /**
     * Updates the {@link Trigger.Status} value for the provided {@link Trigger}.
     *
     * @return success
     */
    public boolean updateTriggerStatus(Trigger trigger) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return false;
        }
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.TriggerContract.STATUS, trigger.getStatus());
        long rows = db.update(MeasurementTables.TriggerContract.TABLE, values,
                MeasurementTables.TriggerContract.ID + " = ?",
                new String[]{trigger.getId()});
        return rows == 1;
    }

    /**
     * Updates the {@link Source.Status} value for the provided list of {@link Source}
     *
     * @param sources list of sources.
     * @param status  value to be set
     * @return success
     */
    public boolean updateSourceStatus(List<Source> sources, @Source.Status int status) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return false;
        }
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceContract.STATUS, status);
        long rows = db.update(MeasurementTables.SourceContract.TABLE, values,
                MeasurementTables.SourceContract.ID + " IN (?)",
                new String[]{sources.stream().map(Source::getId)
                        .collect(Collectors.joining(","))}
        );
        return rows == sources.size();
    }

    /**
     * Change the status of an event report to DELIVERED
     * @param eventReportId the id of the event report to be updated
     * @return success
     */
    public boolean markEventReportDelivered(String eventReportId) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return false;
        }
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.EventReportContract.STATUS, EventReport.Status.DELIVERED);
        long rows = db.update(MeasurementTables.EventReportContract.TABLE, values,
                MeasurementTables.EventReportContract.ID + " = ?",
                new String[]{eventReportId});
        return rows == 1;
    }

    /**
     * Returns list of all the reports associated with the {@link Source}.
     *
     * @param source for querying reports
     * @return list of relevant eventReports; Null in case of SQL failure
     */
    @Nullable
    public List<EventReport> getSourceEventReports(Source source) {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return null;
        }
        List<EventReport> eventReports = new ArrayList<>();
        try (Cursor cursor = db.query(
                MeasurementTables.EventReportContract.TABLE,
                /*columns=*/null,
                MeasurementTables.EventReportContract.SOURCE_ID + " = ? ",
                new String[]{source.getId()},
                /*groupBy=*/null, /*having=*/null, /*orderBy=*/null, /*limit=*/null)) {
            if (cursor == null) {
                return null;
            }
            while (cursor.moveToNext()) {
                eventReports.add(SqliteObjectMapper.constructEventReportFromCursor(cursor));
            }
            return eventReports;
        }
    }

    /**
     * Deletes the {@link EventReport} from datastore.
     *
     * @return success
     */
    public boolean deleteEventReport(EventReport eventReport) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return false;
        }
        long rows = db.delete(MeasurementTables.EventReportContract.TABLE,
                MeasurementTables.EventReportContract.ID + " = ?",
                new String[]{eventReport.getId()});
        return rows == 1;
    }

    /**
     * Saves the {@link EventReport} to datastore.
     *
     * @return success
     */
    public boolean insertEventReportToDB(EventReport eventReport) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return false;
        }
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
        long rowId = db.insert(MeasurementTables.EventReportContract.TABLE,
                /*nullColumnHack=*/null, values);
        return rowId != -1;
    }

    /**
     * Update the value of {@link Source.Status} for the corresponding {@link Source}
     *
     * @param source the {@link Source} object.
     * @return success
     */
    public boolean updateSourceDedupKeys(Source source) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return false;
        }
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceContract.DEDUP_KEYS,
                source.getDedupKeys().stream().map(String::valueOf).collect(
                        Collectors.joining(",")));
        long rows = db.update(MeasurementTables.SourceContract.TABLE, values,
                MeasurementTables.SourceContract.ID + " = ?",
                new String[]{source.getId()});
        return rows == 1;
    }

    /**
     * Add an entry in AttributionRateLimit datastore for the provided {@link Source} and
     * {@link Trigger}
     *
     * @return success
     */
    public boolean addAttributionRateLimitEntry(Source source, Trigger trigger) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return false;
        }
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
        long rowId = db.insert(MeasurementTables.AttributionRateLimitContract.TABLE,
                /*nullColumnHack=*/null,
                values);
        return rowId != -1;
    }

    /**
     * Find the number of entries for a rate limit window using the {@link Source} and
     * {@link Trigger}.
     * Rate-Limit Window: (Source Site, Destination Site, Window) from triggerTime.
     *
     * @return the number of entries for the window. -1 if sql failure
     */
    public long getAttributionsPerRateLimitWindow(Source source, Trigger trigger) {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return -1;
        }
        try {
            return DatabaseUtils.queryNumEntries(
                    db,
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
        } catch (IllegalArgumentException exception) {
            return -1;
        }
    }

    /**
     * Gets the number of sources a registerer has registered.
     */
    public long getNumSourcesPerRegisterer(Uri registerer) {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return -1;
        }
        return DatabaseUtils.queryNumEntries(
                db,
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.REGISTERER + " = ? ",
                new String[]{registerer.toString()});
    }

    /**
     * Gets the number of triggers a registerer has registered.
     */
    public long getNumTriggersPerRegisterer(Uri registerer) {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return -1;
        }
        return DatabaseUtils.queryNumEntries(
                db,
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.REGISTERER + " = ? ",
                new String[]{registerer.toString()});
    }

    /**
     * Deletes all records in measurement tables that correspond with the provided Uri.
     *
     * @param uri the Uri to match on
     * @return success
     */
    public boolean deleteAppRecords(Uri uri) {
        String uriStr = uri.toString();
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return false;
        }
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
        return true;
    }

    /**
     * Queries and returns the {@link AdtechUrl}.
     *
     * @param postbackUrl the postback Url of the request AdtechUrl
     * @return the requested AdtechUrl; Null in case of SQL failure
     */
    @Nullable
    public AdtechUrl getAdtechEnrollmentData(String postbackUrl) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return null;
        }
        try (Cursor cursor = db.query(MeasurementTables.AdTechUrlsContract.TABLE,
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

    /**
     * Given one postback urls, queries and returns all the postback urls with the same adtech id.
     *
     * @param postbackUrl the postback url of the request AdtechUrl
     * @return all the postback urls with the same adtech id; Null in case of SQL failure
     */
    public List<String> getAllAdtechUrls(String postbackUrl) {
        List<String> res = new ArrayList<>();
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return null;
        }
        AdtechUrl adtechUrl = getAdtechEnrollmentData(postbackUrl);
        if (adtechUrl == null) {
            return res;
        }
        String adtechId = adtechUrl.getAdtechId();
        if (adtechId == null) {
            return res;
        }
        try (Cursor cursor = db.query(MeasurementTables.AdTechUrlsContract.TABLE,
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

    /**
     * Saves the {@link AdtechUrl} to datastore.
     *
     * @return success or not
     */
    public boolean insertAdtechUrlToDB(AdtechUrl adtechUrl) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return false;
        }
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AdTechUrlsContract.POSTBACK_URL, adtechUrl.getPostbackUrl());
        values.put(MeasurementTables.AdTechUrlsContract.AD_TECH_ID, adtechUrl.getAdtechId());
        long rowId = db.insert(MeasurementTables.AdTechUrlsContract.TABLE,
                /*nullColumnHack=*/null, values);
        return rowId != 1;
    }

    /**
     * Deletes the {@link AdtechUrl} from datastore using the given postback url.
     *
     * @return success or not
     */
    public boolean deleteAdtechUrl(String postbackUrl) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return false;
        }
        long rows = db.delete(MeasurementTables.AdTechUrlsContract.TABLE,
                MeasurementTables.AdTechUrlsContract.POSTBACK_URL + " = ?",
                new String[]{postbackUrl});
        return rows == 1;
    }

    /**
     * Deletes all expired records in measurement tables.
     *
     * @return success
     */
    public boolean deleteExpiredRecords() {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();

        if (db == null) {
            return false;
        }
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
        return true;
    }

    /**
     * Deletes all measurement data owned by a registrant and optionally providing an origin uri
     * and/or a range of dates.
     *
     * @param registerer who owns the data
     * @param origin uri for deletion. May be null
     * @param start time for deletion range. May be null. If null, end must be null as well
     * @param end time for deletion range. May be null. If null, start must be null as well
     * @return success
     */
    public boolean deleteMeasurementData(
            @NonNull Uri registerer,
            @Nullable Uri origin,
            @Nullable Instant start,
            @Nullable Instant end) {
        Objects.requireNonNull(registerer);
        validateOptionalRange(start, end);
        final SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        db.beginTransaction();
        try {
            deleteMeasurementData(db, registerer, origin, start, end);
            db.setTransactionSuccessful();
        } catch (Exception e) {
            LogUtil.e("Error while deleting browser measurement data", e);
            return false;
        } finally {
            db.endTransaction();
        }
        return true;
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
