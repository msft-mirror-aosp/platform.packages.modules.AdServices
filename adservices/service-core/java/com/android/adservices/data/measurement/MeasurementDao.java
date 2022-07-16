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

import android.adservices.measurement.DeletionRequest;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.adservices.service.measurement.AdtechUrl;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.PrivacyParams;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.attribution.BaseUriExtractor;
import com.android.adservices.service.measurement.enrollment.EnrollmentData;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Data Access Object for the Measurement PPAPI module.
 */
class MeasurementDao implements IMeasurementDao {

    private static final String ANDROID_APP_SCHEME = "android-app";
    private SQLTransaction mSQLTransaction;

    @Override
    public void setTransaction(ITransaction transaction) {
        if (!(transaction instanceof SQLTransaction)) {
            throw new IllegalArgumentException("transaction should be a SQLTransaction.");
        }
        mSQLTransaction = (SQLTransaction) transaction;
    }

    @Override
    public void insertTrigger(@NonNull Trigger trigger) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.TriggerContract.ID, UUID.randomUUID().toString());
        values.put(MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION,
                trigger.getAttributionDestination().toString());
        values.put(MeasurementTables.TriggerContract.TRIGGER_TIME, trigger.getTriggerTime());
        values.put(MeasurementTables.TriggerContract.EVENT_TRIGGERS,
                trigger.getEventTriggers());
        values.put(MeasurementTables.TriggerContract.STATUS, Trigger.Status.PENDING);
        values.put(MeasurementTables.TriggerContract.AD_TECH_DOMAIN,
                trigger.getAdTechDomain().toString());
        values.put(MeasurementTables.TriggerContract.REGISTRANT,
                trigger.getRegistrant().toString());
        values.put(MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA,
                trigger.getAggregateTriggerData());
        values.put(MeasurementTables.TriggerContract.AGGREGATE_VALUES,
                trigger.getAggregateValues());
        values.put(MeasurementTables.TriggerContract.FILTERS, trigger.getFilters());
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
    public Trigger getTrigger(@NonNull String triggerId) throws DatastoreException {
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
    public EventReport getEventReport(@NonNull String eventReportId) throws DatastoreException {
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
    public AggregateReport getAggregateReport(@NonNull String aggregateReportId)
            throws DatastoreException {
        try (Cursor cursor = mSQLTransaction.getDatabase().query(
                MeasurementTables.AggregateReport.TABLE,
                null,
                MeasurementTables.AggregateReport.ID + " = ? ",
                new String[]{aggregateReportId},
                null,
                null,
                null,
                null)) {
            if (cursor.getCount() == 0) {
                throw new DatastoreException(
                        "AggregateReport retrieval failed. Id: " + aggregateReportId);
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructAggregateReport(cursor);
        }
    }

    @Override
    public void insertSource(@NonNull Source source) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceContract.ID, UUID.randomUUID().toString());
        values.put(MeasurementTables.SourceContract.EVENT_ID, source.getEventId());
        values.put(MeasurementTables.SourceContract.PUBLISHER, source.getPublisher().toString());
        values.put(
                MeasurementTables.SourceContract.ATTRIBUTION_DESTINATION,
                getNullableUriString(source.getAttributionDestination()));
        values.put(
                MeasurementTables.SourceContract.WEB_DESTINATION,
                getNullableUriString(source.getWebDestination()));
        values.put(MeasurementTables.SourceContract.AD_TECH_DOMAIN,
                source.getAdTechDomain().toString());
        values.put(MeasurementTables.SourceContract.EVENT_TIME, source.getEventTime());
        values.put(MeasurementTables.SourceContract.EXPIRY_TIME, source.getExpiryTime());
        values.put(MeasurementTables.SourceContract.PRIORITY, source.getPriority());
        values.put(MeasurementTables.SourceContract.STATUS, Source.Status.ACTIVE);
        values.put(MeasurementTables.SourceContract.SOURCE_TYPE, source.getSourceType().name());
        values.put(MeasurementTables.SourceContract.REGISTRANT, source.getRegistrant().toString());
        values.put(MeasurementTables.SourceContract.INSTALL_ATTRIBUTION_WINDOW,
                source.getInstallAttributionWindow());
        values.put(MeasurementTables.SourceContract.INSTALL_COOLDOWN_WINDOW,
                source.getInstallCooldownWindow());
        values.put(MeasurementTables.SourceContract.ATTRIBUTION_MODE, source.getAttributionMode());
        values.put(MeasurementTables.SourceContract.AGGREGATE_SOURCE, source.getAggregateSource());
        values.put(MeasurementTables.SourceContract.FILTER_DATA, source.getAggregateFilterData());
        values.put(MeasurementTables.SourceContract.AGGREGATE_CONTRIBUTIONS, 0);
        long rowId = mSQLTransaction.getDatabase()
                .insert(MeasurementTables.SourceContract.TABLE,
                        /*nullColumnHack=*/null, values);
        LogUtil.d("MeasurementDao: insertSource: rowId=" + rowId);

        if (rowId == -1) {
            throw new DatastoreException("Source insertion failed.");
        }
    }

    @Override
    public List<Source> getMatchingActiveSources(@NonNull Trigger trigger)
            throws DatastoreException {
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.SourceContract.TABLE,
                                /*columns=*/ null,
                                getSourceDestinationColumnForTrigger(trigger)
                                        + " = ? AND "
                                        + MeasurementTables.SourceContract.AD_TECH_DOMAIN
                                        + " = ? AND "
                                        // EventTime should be strictly less than TriggerTime as it
                                        // is highly
                                        // unlikely for matching Source and Trigger to happen at
                                        // same instant
                                        // in milliseconds.
                                        + MeasurementTables.SourceContract.EVENT_TIME
                                        + " < ? AND "
                                        + MeasurementTables.SourceContract.EXPIRY_TIME
                                        + " >= ? AND "
                                        + MeasurementTables.SourceContract.STATUS
                                        + " != ?",
                                new String[] {
                                    trigger.getAttributionDestination().toString(),
                                    trigger.getAdTechDomain().toString(),
                                    String.valueOf(trigger.getTriggerTime()),
                                    String.valueOf(trigger.getTriggerTime()),
                                    String.valueOf(Source.Status.IGNORED)
                                },
                                /*groupBy=*/ null,
                                /*having=*/ null,
                                /*orderBy=*/ null,
                                /*limit=*/ null)) {
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
    public void updateSourceAggregateContributions(Source source) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceContract.AGGREGATE_CONTRIBUTIONS,
                source.getAggregateContributions());
        long rows = mSQLTransaction.getDatabase()
                .update(MeasurementTables.SourceContract.TABLE, values,
                        MeasurementTables.SourceContract.ID + " = ?",
                        new String[]{source.getId()});
        if (rows != 1) {
            throw new DatastoreException("Source aggregate contributions update failed.");
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
    public void markAggregateReportDelivered(String aggregateReportId) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AggregateReport.STATUS,
                AggregateReport.Status.DELIVERED);
        long rows = mSQLTransaction.getDatabase().update(MeasurementTables.AggregateReport.TABLE,
                values, MeasurementTables.AggregateReport.ID + " = ? ",
                new String[]{aggregateReportId});
        if (rows != 1) {
            throw new DatastoreException("AggregateReport update failed");
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
        values.put(MeasurementTables.EventReportContract.AD_TECH_DOMAIN,
                eventReport.getAdTechDomain().toString());
        values.put(MeasurementTables.EventReportContract.STATUS,
                eventReport.getStatus());
        values.put(MeasurementTables.EventReportContract.REPORT_TIME,
                eventReport.getReportTime());
        values.put(MeasurementTables.EventReportContract.TRIGGER_PRIORITY,
                eventReport.getTriggerPriority());
        values.put(MeasurementTables.EventReportContract.SOURCE_TYPE,
                eventReport.getSourceType().toString());
        values.put(MeasurementTables.EventReportContract.RANDOMIZED_TRIGGER_RATE,
                eventReport.getRandomizedTriggerRate());
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
                BaseUriExtractor.getBaseUri(source.getPublisher()).toString());
        values.put(MeasurementTables.AttributionRateLimitContract.DESTINATION_SITE,
                BaseUriExtractor.getBaseUri(trigger.getAttributionDestination()).toString());
        values.put(MeasurementTables.AttributionRateLimitContract.AD_TECH_DOMAIN,
                trigger.getAdTechDomain().toString());
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
                        + MeasurementTables.AttributionRateLimitContract.AD_TECH_DOMAIN
                        + " = ? AND "
                        + MeasurementTables.AttributionRateLimitContract.TRIGGER_TIME
                        + " >= ? AND "
                        + MeasurementTables.AttributionRateLimitContract.TRIGGER_TIME
                        + " <= ? ",
                new String[]{
                        BaseUriExtractor.getBaseUri(source.getPublisher()).toString(),
                        BaseUriExtractor.getBaseUri(trigger.getAttributionDestination()).toString(),
                        trigger.getAdTechDomain().toString(),
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
                        MeasurementTables.EventReportContract.AD_TECH_DOMAIN,
                        MeasurementTables.SourceContract.AD_TECH_DOMAIN,
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

    /**
     * Queries and returns the {@link EnrollmentData}.
     *
     * @param enrollmentId ID provided to the adtech at the end of the enrollment process.
     * @return the EnrollmentData; Null in case of SQL failure
     */
    // TODO(b/230617871): Move EnrollmentData related methods to a Dao class that is common across
    //  for PPAPIs since Enrollment data will likely be used by others as well.
    @Override
    @Nullable
    public EnrollmentData getEnrollmentData(String enrollmentId) throws DatastoreException {
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.EnrollmentDataContract.TABLE,
                                /*columns=*/ null,
                                MeasurementTables.EnrollmentDataContract.ENROLLMENT_ID + " = ? ",
                                new String[] {enrollmentId},
                                /*groupBy=*/ null,
                                /*having=*/ null,
                                /*orderBy=*/ null,
                                /*limit=*/ null)) {
            if (cursor == null || cursor.getCount() == 0) {
                return null;
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructEnrollmentDataFromCursor(cursor);
        }
    }

    /**
     * Queries and returns the {@link EnrollmentData} given measurement registration URLs.
     *
     * @param url could be source registration url or trigger registration url.
     * @return the EnrollmentData; Null in case of SQL failure.
     */
    @Override
    @Nullable
    public EnrollmentData getEnrollmentDataGivenUrl(String url) throws DatastoreException {
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.EnrollmentDataContract.TABLE,
                                /*columns=*/ null,
                                MeasurementTables.EnrollmentDataContract
                                                .ATTRIBUTION_SOURCE_REGISTRATION_URL
                                        + " LIKE '%"
                                        + url
                                        + "%' OR "
                                        + MeasurementTables.EnrollmentDataContract
                                                .ATTRIBUTION_TRIGGER_REGISTRATION_URL
                                        + " LIKE '%"
                                        + url
                                        + "%'",
                                null,
                                /*groupBy=*/ null,
                                /*having=*/ null,
                                /*orderBy=*/ null,
                                /*limit=*/ null)) {
            if (cursor == null || cursor.getCount() == 0) {
                return null;
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructEnrollmentDataFromCursor(cursor);
        }
    }

    /**
     * Queries and returns the {@link EnrollmentData} given AdTech SDK Name.
     *
     * @param sdkName List of SDKs belonging to the same enrollment.
     * @return the EnrollmentData; Null in case of SQL failure
     */
    @Override
    @Nullable
    public EnrollmentData getEnrollmentDataGivenSdkName(String sdkName) throws DatastoreException {
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.EnrollmentDataContract.TABLE,
                                /*columns=*/ null,
                                MeasurementTables.EnrollmentDataContract.SDK_NAMES
                                        + " LIKE '%"
                                        + sdkName
                                        + "%'",
                                null,
                                /*groupBy=*/ null,
                                /*having=*/ null,
                                /*orderBy=*/ null,
                                /*limit=*/ null)) {
            if (cursor == null || cursor.getCount() == 0) {
                return null;
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructEnrollmentDataFromCursor(cursor);
        }
    }

    @Override
    public void insertEnrollmentData(EnrollmentData enrollmentData) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(
                MeasurementTables.EnrollmentDataContract.ENROLLMENT_ID,
                enrollmentData.getEnrollmentId());
        values.put(
                MeasurementTables.EnrollmentDataContract.COMPANY_ID, enrollmentData.getCompanyId());
        values.put(
                MeasurementTables.EnrollmentDataContract.SDK_NAMES,
                String.join(" ", enrollmentData.getSdkNames()));
        values.put(
                MeasurementTables.EnrollmentDataContract.ATTRIBUTION_SOURCE_REGISTRATION_URL,
                String.join(" ", enrollmentData.getAttributionSourceRegistrationUrl()));
        values.put(
                MeasurementTables.EnrollmentDataContract.ATTRIBUTION_TRIGGER_REGISTRATION_URL,
                String.join(" ", enrollmentData.getAttributionTriggerRegistrationUrl()));
        values.put(
                MeasurementTables.EnrollmentDataContract.ATTRIBUTION_REPORTING_URL,
                String.join(" ", enrollmentData.getAttributionReportingUrl()));
        values.put(
                MeasurementTables.EnrollmentDataContract
                        .REMARKETING_RESPONSE_BASED_REGISTRATION_URL,
                String.join(" ", enrollmentData.getRemarketingResponseBasedRegistrationUrl()));
        values.put(
                MeasurementTables.EnrollmentDataContract.ENCRYPTION_KEY_URL,
                String.join(" ", enrollmentData.getEncryptionKeyUrl()));
        long rowId =
                mSQLTransaction
                        .getDatabase()
                        .insert(
                                MeasurementTables.EnrollmentDataContract.TABLE,
                                /*nullColumnHack=*/ null,
                                values);
        if (rowId == -1) {
            throw new DatastoreException("EnrollmentData insertion failed.");
        }
    }

    @Override
    public void deleteEnrollmentData(String enrollmentId) throws DatastoreException {
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .delete(
                                MeasurementTables.EnrollmentDataContract.TABLE,
                                MeasurementTables.EnrollmentDataContract.ENROLLMENT_ID + " = ?",
                                new String[] {enrollmentId});
        if (rows != 1) {
            throw new DatastoreException("EnrollmentData deletion failed.");
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
            @Nullable Instant start,
            @Nullable Instant end,
            @NonNull List<Uri> origins,
            @NonNull List<Uri> domains,
            @DeletionRequest.MatchBehavior int matchBehavior,
            @DeletionRequest.DeletionMode int deletionMode)
            throws DatastoreException {
        Objects.requireNonNull(registrant);
        Objects.requireNonNull(origins);
        Objects.requireNonNull(domains);
        validateOptionalRange(start, end);
        // Handle no-op case
        // Preserving everything => Do Nothing
        if (domains.isEmpty()
                && origins.isEmpty()
                && matchBehavior == DeletionRequest.MATCH_BEHAVIOR_PRESERVE) {
            return;
        }
        final SQLiteDatabase db = mSQLTransaction.getDatabase();
        Function<String, String> registrantMatcher = getRegistrantMatcher(registrant);
        Function<String, String> siteMatcher = getsiteMatcher(origins, domains, matchBehavior);
        Function<String, String> timeMatcher = getTimeMatcher(start, end);

        if (deletionMode == DeletionRequest.DELETION_MODE_ALL) {
            deleteAttributionRateLimit(db, registrantMatcher, siteMatcher, timeMatcher);
        }
        deleteEventReport(db, registrantMatcher, siteMatcher, timeMatcher);
        deleteTrigger(db, registrantMatcher, siteMatcher, timeMatcher);
        deleteSource(db, registrantMatcher, siteMatcher, timeMatcher);
    }

    private void deleteSource(
            SQLiteDatabase db,
            Function<String, String> registrantMatcher,
            Function<String, String> siteMatcher,
            Function<String, String> timeMatcher) {
        db.delete(
                MeasurementTables.SourceContract.TABLE,
                mergeConditions(
                        " AND ",
                        registrantMatcher.apply(MeasurementTables.SourceContract.REGISTRANT),
                        siteMatcher.apply(MeasurementTables.SourceContract.PUBLISHER),
                        timeMatcher.apply(MeasurementTables.SourceContract.EVENT_TIME)),
                null);
    }

    private void deleteTrigger(
            SQLiteDatabase db,
            Function<String, String> registrantMatcher,
            Function<String, String> siteMatcher,
            Function<String, String> timeMatcher) {
        // Where Statement:
        // (registrant - RegistrantMatching) AND
        // (attributionStatement - OriginMatching) AND
        // (triggerTime - TimeMatching)
        db.delete(
                MeasurementTables.TriggerContract.TABLE,
                mergeConditions(
                        " AND ",
                        registrantMatcher.apply(MeasurementTables.TriggerContract.REGISTRANT),
                        siteMatcher.apply(
                                MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION),
                        timeMatcher.apply(MeasurementTables.TriggerContract.TRIGGER_TIME)),
                null);
    }

    private void deleteEventReport(
            SQLiteDatabase db,
            Function<String, String> registrantMatcher,
            Function<String, String> siteMatcher,
            Function<String, String> timeMatcher) {
        String sourceSiteColumn = "s." + MeasurementTables.SourceContract.PUBLISHER;
        String sourceTimeColumn = "s." + MeasurementTables.SourceContract.EVENT_TIME;
        String eventReportSiteColumn =
                "e." + MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION;
        String eventReportTimeColumn = "e." + MeasurementTables.EventReportContract.TRIGGER_TIME;

        // Where Statement:
        // evenReport.ID IN (
        // SELECT e.ID FROM event_report e INNER JOIN source s ON
        // (e.event_id = s.event_id) WHERE (
        //     (registrant - RegistrantMatching) AND
        //       (((s.publisher - OriginMatching) AND (s.eventTime - TimeMatching)) OR
        //         ((e.destination - OriginMatching) AND (e.triggerTime - TimeMatching)))
        //     )
        //   )
        // )
        String whereString =
                MeasurementTables.EventReportContract.ID
                        + " IN ("
                        + "SELECT e."
                        + MeasurementTables.EventReportContract.ID
                        + " FROM "
                        + MeasurementTables.EventReportContract.TABLE
                        + "  e "
                        + "INNER JOIN "
                        + MeasurementTables.SourceContract.TABLE
                        + " s "
                        + "ON (e."
                        + MeasurementTables.EventReportContract.SOURCE_ID
                        + " = "
                        + " s."
                        + MeasurementTables.SourceContract.EVENT_ID
                        + ") "
                        // Where string
                        + " WHERE "
                        + mergeConditions(
                                /* operator = */ " AND ",
                                registrantMatcher.apply(
                                        MeasurementTables.SourceContract.REGISTRANT),
                                mergeConditions(
                                        /* operator = */ " OR ",
                                        mergeConditions(
                                                /* operator = */ " AND ",
                                                siteMatcher.apply(sourceSiteColumn),
                                                timeMatcher.apply(sourceTimeColumn)),
                                        mergeConditions(
                                                /* operator = */ " AND ",
                                                siteMatcher.apply(eventReportSiteColumn),
                                                timeMatcher.apply(eventReportTimeColumn))))
                        + ")";
        db.delete(MeasurementTables.EventReportContract.TABLE, whereString, null);
    }

    private void deleteAttributionRateLimit(
            SQLiteDatabase db,
            Function<String, String> registrantMatcher,
            Function<String, String> siteMatcher,
            Function<String, String> timeMatcher) {
        // Where Statement:
        // (registrant - RegistrantMatching) AND
        // ((destinationSite - OriginMatching) OR (sourceSite - OriginMatching)) AND
        // (triggerTime - TimeMatching)
        db.delete(
                MeasurementTables.AttributionRateLimitContract.TABLE,
                mergeConditions(
                        " AND ",
                        registrantMatcher.apply(
                                MeasurementTables.AttributionRateLimitContract.REGISTRANT),
                        mergeConditions(
                                " OR ",
                                siteMatcher.apply(
                                        MeasurementTables.AttributionRateLimitContract
                                                .DESTINATION_SITE),
                                siteMatcher.apply(
                                        MeasurementTables.AttributionRateLimitContract
                                                .SOURCE_SITE)),
                        timeMatcher.apply(
                                MeasurementTables.AttributionRateLimitContract.TRIGGER_TIME)),
                null);
    }

    private static Function<String, String> getRegistrantMatcher(Uri registrant) {
        return (String columnName) -> columnName + " = '" + registrant + "'";
    }

    private static Function<String, String> getTimeMatcher(Instant start, Instant end) {
        return (String columnName) -> {
            if (start == null || end == null) {
                return "";
            }
            return " ( "
                    + columnName
                    + " >= "
                    + start.toEpochMilli()
                    + " AND "
                    + columnName
                    + " <= "
                    + end.toEpochMilli()
                    + " ) ";
        };
    }

    private static Function<String, String> getsiteMatcher(
            List<Uri> origins,
            List<Uri> domains,
            @DeletionRequest.MatchBehavior int matchBehavior) {
        if (origins.isEmpty()
                && domains.isEmpty()
                && matchBehavior == DeletionRequest.MATCH_BEHAVIOR_PRESERVE) {
            throw new IllegalStateException("No-op conditions");
        }

        return (String columnName) -> {
            if (origins.isEmpty() && domains.isEmpty()) {
                return "";
            }
            StringBuilder whereBuilder = new StringBuilder();
            boolean started = false;
            if (!origins.isEmpty()) {
                started = true;
                whereBuilder.append("(");
                whereBuilder.append(columnName);
                // For Delete case:
                // (columnName IN ( origin1, origin2 )
                // For Preserve case:
                // (columnName NOT IN ( origin1, origin2 )
                if (matchBehavior == DeletionRequest.MATCH_BEHAVIOR_PRESERVE) {
                    whereBuilder.append(" NOT IN (");
                } else {
                    whereBuilder.append(" IN (");
                }
                whereBuilder.append(
                        origins.stream()
                                .map((o) -> "'" + o + "'")
                                .collect(Collectors.joining(", ")));
                whereBuilder.append(")");
            }

            if (!domains.isEmpty()) {
                if (started) {
                    whereBuilder.append(
                            matchBehavior == DeletionRequest.MATCH_BEHAVIOR_PRESERVE
                                    ? " AND "
                                    : " OR ");
                } else {
                    whereBuilder.append(" ( ");
                    started = true;
                }
                whereBuilder.append(" ( ");
                String operator =
                        matchBehavior == DeletionRequest.MATCH_BEHAVIOR_PRESERVE
                                ? " NOT LIKE "
                                : " LIKE ";
                String concatOperator =
                        matchBehavior == DeletionRequest.MATCH_BEHAVIOR_PRESERVE ? " AND " : " OR ";
                String equalityOperator =
                        matchBehavior == DeletionRequest.MATCH_BEHAVIOR_PRESERVE ? " != " : " = ";
                // Domains have 2 cases: subdomain(*.example.com) and the parent domain(example.com)
                // For Delete case:
                // (columnName LIKE "SCHEME1://%.SITE1" OR columnName = "SCHEME1://SITE1") OR
                // (columnName LIKE "SCHEME2://%.SITE2" OR columnName = "SCHEME2://SITE2")
                // For Preserve case:
                // (columnName NOT LIKE 'SCHEME1://%.SITE1' AND columnName != 'SCHEME1://SITE1')
                // AND
                // (columnName NOT LIKE 'SCHEME2://%.SITE2' AND columnName != 'SCHEME2://SITE2')
                whereBuilder.append(
                        domains.stream()
                                .map(
                                        (uri) ->
                                                ("("
                                                        + columnName
                                                        + operator
                                                        + "'"
                                                        + uri.getScheme()
                                                        + "://%."
                                                        + uri.getAuthority()
                                                        + "'"
                                                        + concatOperator
                                                        + columnName
                                                        + equalityOperator
                                                        + "'"
                                                        + uri
                                                        + "'"
                                                        + ")"))
                                .collect(Collectors.joining(concatOperator)));
                whereBuilder.append(" ) ");
            }
            if (started) {
                whereBuilder.append(" ) ");
            }
            return whereBuilder.toString();
        };
    }

    private String mergeConditions(String operator, String... matcherStrings) {
        String res =
                Arrays.stream(matcherStrings)
                        .filter(Predicate.not(String::isEmpty))
                        .collect(Collectors.joining(operator));
        if (!res.isEmpty()) {
            res = "(" + res + ")";
        }
        return res;
    }

    @Override
    public void deleteAllMeasurementData(@NonNull List<String> tablesToExclude)
            throws DatastoreException {
        SQLiteDatabase db = mSQLTransaction.getDatabase();
        for (String table : MeasurementTables.ALL_MSMT_TABLES) {
            if (!tablesToExclude.contains(table)) {
                db.delete(table, /* whereClause */ null, /* whereArgs */ null);
            }
        }
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

    @Override
    public void insertAggregateEncryptionKey(AggregateEncryptionKey aggregateEncryptionKey)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AggregateEncryptionKey.ID, UUID.randomUUID().toString());
        values.put(MeasurementTables.AggregateEncryptionKey.KEY_ID,
                aggregateEncryptionKey.getKeyId());
        values.put(MeasurementTables.AggregateEncryptionKey.PUBLIC_KEY,
                aggregateEncryptionKey.getPublicKey());
        values.put(MeasurementTables.AggregateEncryptionKey.EXPIRY,
                aggregateEncryptionKey.getExpiry());
        long rowId = mSQLTransaction.getDatabase()
                .insert(MeasurementTables.AggregateEncryptionKey.TABLE,
                        /*nullColumnHack=*/null, values);
        if (rowId == -1) {
            throw new DatastoreException("Aggregate encryption key insertion failed.");
        }
    }

    @Override
    public List<AggregateEncryptionKey> getNonExpiredAggregateEncryptionKeys(long expiry)
            throws DatastoreException {
        List<AggregateEncryptionKey> aggregateEncryptionKeys = new ArrayList<>();
        try (Cursor cursor = mSQLTransaction.getDatabase().query(
                MeasurementTables.AggregateEncryptionKey.TABLE,
                /*columns=*/null,
                MeasurementTables.AggregateEncryptionKey.EXPIRY + " >= ?",
                new String[]{String.valueOf(expiry)},
                /*groupBy=*/null, /*having=*/null, /*orderBy=*/null, /*limit=*/null)) {
            while (cursor.moveToNext()) {
                aggregateEncryptionKeys
                        .add(SqliteObjectMapper.constructAggregateEncryptionKeyFromCursor(cursor));
            }
            return aggregateEncryptionKeys;
        }
    }

    @Override
    public void deleteExpiredAggregateEncryptionKeys(long expiry) throws DatastoreException {
        SQLiteDatabase db = mSQLTransaction.getDatabase();
        db.delete(MeasurementTables.AggregateEncryptionKey.TABLE,
                MeasurementTables.AggregateEncryptionKey.EXPIRY + " < ?",
                new String[]{String.valueOf(expiry)});
    }

    @Override
    public void insertAggregateReport(AggregateReport aggregateReport)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AggregateReport.ID, UUID.randomUUID().toString());
        values.put(MeasurementTables.AggregateReport.PUBLISHER,
                aggregateReport.getPublisher().toString());
        values.put(MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION,
                aggregateReport.getAttributionDestination().toString());
        values.put(MeasurementTables.AggregateReport.SOURCE_REGISTRATION_TIME,
                aggregateReport.getSourceRegistrationTime());
        values.put(MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME,
                aggregateReport.getScheduledReportTime());
        values.put(MeasurementTables.AggregateReport.REPORTING_ORIGIN,
                aggregateReport.getReportingOrigin().toString());
        values.put(MeasurementTables.AggregateReport.DEBUG_CLEARTEXT_PAYLOAD,
                aggregateReport.getDebugCleartextPayload());
        values.put(MeasurementTables.AggregateReport.STATUS,
                aggregateReport.getStatus());
        values.put(MeasurementTables.AggregateReport.API_VERSION,
                aggregateReport.getApiVersion());
        long rowId = mSQLTransaction.getDatabase()
                .insert(MeasurementTables.AggregateReport.TABLE,
                        /*nullColumnHack=*/null, values);
        if (rowId == -1) {
            throw new DatastoreException("Unencrypted aggregate payload insertion failed.");
        }
    }

    @Override
    public List<String> getPendingAggregateReportIdsInWindow(long windowStartTime,
            long windowEndTime) throws DatastoreException {
        List<String> aggregateReports = new ArrayList<>();
        try (Cursor cursor = mSQLTransaction.getDatabase().query(
                MeasurementTables.AggregateReport.TABLE,
                /*columns=*/null,
                MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME + " >= ? AND "
                        + MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME + " <= ? AND "
                        + MeasurementTables.AggregateReport.STATUS + " = ? ",
                new String[]{String.valueOf(windowStartTime), String.valueOf(windowEndTime),
                        String.valueOf(AggregateReport.Status.PENDING)},
                /*groupBy=*/null, /*having=*/null, /*orderBy=*/"RANDOM()", /*limit=*/null)) {
            while (cursor.moveToNext()) {
                aggregateReports.add(cursor.getString(cursor.getColumnIndex(
                        MeasurementTables.EventReportContract.ID)));
            }
            return aggregateReports;
        }
    }

    @Override
    public List<String> getPendingAggregateReportIdsForGivenApp(Uri appName)
            throws DatastoreException {
        List<String> aggregateReports = new ArrayList<>();
        try (Cursor cursor = mSQLTransaction.getDatabase().query(
                MeasurementTables.AggregateReport.TABLE, null,
                MeasurementTables.AggregateReport.PUBLISHER + " = ? AND "
                + MeasurementTables.AggregateReport.STATUS + " = ? ",
                new String[]{appName.toString(),
                        String.valueOf(AggregateReport.Status.PENDING)},
                null, null, "RANDOM()", null)) {
            while (cursor.moveToNext()) {
                aggregateReports.add(cursor.getString(cursor.getColumnIndex(
                        MeasurementTables.AggregateReport.ID)));
            }
            return aggregateReports;
        }
    }

    private String getSourceDestinationColumnForTrigger(Trigger trigger) {
        boolean isAppDestination =
                trigger.getAttributionDestination().getScheme().startsWith(ANDROID_APP_SCHEME);
        return isAppDestination
                ? MeasurementTables.SourceContract.ATTRIBUTION_DESTINATION
                : MeasurementTables.SourceContract.WEB_DESTINATION;
    }

    private String getNullableUriString(@Nullable Uri uri) {
        return Optional.ofNullable(uri).map(Uri::toString).orElse(null);
    }
}
