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
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.adservices.service.measurement.Attribution;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.PrivacyParams;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.util.BaseUriExtractor;
import com.android.adservices.service.measurement.util.Web;

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
        values.put(MeasurementTables.TriggerContract.DESTINATION_TYPE,
                trigger.getDestinationType());
        values.put(MeasurementTables.TriggerContract.TRIGGER_TIME, trigger.getTriggerTime());
        values.put(MeasurementTables.TriggerContract.EVENT_TRIGGERS,
                trigger.getEventTriggers());
        values.put(MeasurementTables.TriggerContract.STATUS, Trigger.Status.PENDING);
        values.put(MeasurementTables.TriggerContract.AD_TECH_DOMAIN,
                trigger.getAdTechDomain().toString());
        values.put(MeasurementTables.TriggerContract.ENROLLMENT_ID, trigger.getEnrollmentId());
        values.put(MeasurementTables.TriggerContract.REGISTRANT,
                trigger.getRegistrant().toString());
        values.put(MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA,
                trigger.getAggregateTriggerData());
        values.put(MeasurementTables.TriggerContract.AGGREGATE_VALUES,
                trigger.getAggregateValues());
        values.put(MeasurementTables.TriggerContract.FILTERS, trigger.getFilters());
        values.put(MeasurementTables.TriggerContract.DEBUG_KEY, trigger.getDebugKey());
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
        values.put(MeasurementTables.SourceContract.PUBLISHER_TYPE, source.getPublisherType());
        values.put(
                MeasurementTables.SourceContract.APP_DESTINATION,
                getNullableUriString(source.getAppDestination()));
        values.put(
                MeasurementTables.SourceContract.WEB_DESTINATION,
                getNullableUriString(source.getWebDestination()));
        values.put(MeasurementTables.SourceContract.AD_TECH_DOMAIN,
                source.getAdTechDomain().toString());
        values.put(MeasurementTables.SourceContract.ENROLLMENT_ID, source.getEnrollmentId());
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
        values.put(MeasurementTables.SourceContract.DEBUG_KEY, source.getDebugKey());
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
        List<Source> sources = new ArrayList<>();
        Optional<Pair<String, String>> destinationColumnAndValue =
                getDestinationColumnAndValue(trigger);
        if (!destinationColumnAndValue.isPresent()) {
            LogUtil.d("getMatchingActiveSources: unable to obtain destination column and value: %s",
                    trigger.getAttributionDestination().toString());
            return sources;
        }
        String sourceDestinationColumn = destinationColumnAndValue.get().first;
        String triggerDestinationValue = destinationColumnAndValue.get().second;
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.SourceContract.TABLE,
                                /*columns=*/ null,
                                sourceDestinationColumn
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
                                    triggerDestinationValue,
                                    trigger.getAdTechDomain().toString(),
                                    String.valueOf(trigger.getTriggerTime()),
                                    String.valueOf(trigger.getTriggerTime()),
                                    String.valueOf(Source.Status.IGNORED)
                                },
                                /*groupBy=*/ null,
                                /*having=*/ null,
                                /*orderBy=*/ null,
                                /*limit=*/ null)) {
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
        values.put(MeasurementTables.EventReportContract.ENROLLMENT_ID,
                eventReport.getEnrollmentId());
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
    public void insertAttribution(@NonNull Attribution attribution) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AttributionContract.ID, UUID.randomUUID().toString());
        values.put(MeasurementTables.AttributionContract.SOURCE_SITE, attribution.getSourceSite());
        values.put(
                MeasurementTables.AttributionContract.SOURCE_ORIGIN, attribution.getSourceOrigin());
        values.put(
                MeasurementTables.AttributionContract.DESTINATION_SITE,
                attribution.getDestinationSite());
        values.put(
                MeasurementTables.AttributionContract.DESTINATION_ORIGIN,
                attribution.getDestinationOrigin());
        values.put(
                MeasurementTables.AttributionContract.AD_TECH_DOMAIN,
                attribution.getAdTechDomain());
        values.put(
                MeasurementTables.AttributionContract.ENROLLMENT_ID,
                attribution.getEnrollmentId());
        values.put(
                MeasurementTables.AttributionContract.TRIGGER_TIME, attribution.getTriggerTime());
        values.put(MeasurementTables.AttributionContract.REGISTRANT, attribution.getRegistrant());
        long rowId =
                mSQLTransaction
                        .getDatabase()
                        .insert(
                                MeasurementTables.AttributionContract.TABLE,
                                /*nullColumnHack=*/ null,
                                values);
        if (rowId == -1) {
            throw new DatastoreException("Attribution insertion failed.");
        }
    }

    @Override
    public long getAttributionsPerRateLimitWindow(@NonNull Source source, @NonNull Trigger trigger)
            throws DatastoreException {
        Optional<Uri> publisherBaseUri =
                extractBaseUri(source.getPublisher(), source.getPublisherType());
        Optional<Uri> destinationBaseUri =
                extractBaseUri(trigger.getAttributionDestination(), trigger.getDestinationType());

        if (!publisherBaseUri.isPresent() || !destinationBaseUri.isPresent()) {
            throw new IllegalArgumentException(String.format(
                    "getAttributionsPerRateLimitWindow: getSourceAndDestinationTopPrivateDomains "
                    + "failed. Publisher: %s; Attribution destination: %s",
                    source.getPublisher().toString(),
                    trigger.getAttributionDestination().toString()));
        }

        String publisherTopPrivateDomain = publisherBaseUri.get().toString();
        String triggerDestinationTopPrivateDomain = destinationBaseUri.get().toString();

        return DatabaseUtils.queryNumEntries(
                mSQLTransaction.getDatabase(),
                MeasurementTables.AttributionContract.TABLE,
                MeasurementTables.AttributionContract.SOURCE_SITE + " = ? AND "
                        + MeasurementTables.AttributionContract.DESTINATION_SITE
                        + " = ? AND "
                        + MeasurementTables.AttributionContract.AD_TECH_DOMAIN
                        + " = ? AND "
                        + MeasurementTables.AttributionContract.TRIGGER_TIME
                        + " >= ? AND "
                        + MeasurementTables.AttributionContract.TRIGGER_TIME
                        + " <= ? ",
                new String[] {
                        publisherTopPrivateDomain,
                        triggerDestinationTopPrivateDomain,
                        trigger.getAdTechDomain().toString(),
                        String.valueOf(trigger.getTriggerTime()
                                - PrivacyParams.RATE_LIMIT_WINDOW_MILLISECONDS),
                        String.valueOf(trigger.getTriggerTime())
                });
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
    public Integer countDistinctAdTechsPerPublisherXDestinationInAttribution(Uri sourceSite,
            Uri destinationSite, Uri excludedAdTech, long windowStartTime, long windowEndTime)
            throws DatastoreException {
        String query = String.format(
                "SELECT COUNT(DISTINCT %1$s) FROM %2$s "
                + "WHERE %3$s = ? AND %4$s = ? AND %1s != ? "
                + "AND %5$s < ? AND %5$s >= ?",
                MeasurementTables.AttributionContract.AD_TECH_DOMAIN,
                MeasurementTables.AttributionContract.TABLE,
                MeasurementTables.AttributionContract.SOURCE_SITE,
                MeasurementTables.AttributionContract.DESTINATION_SITE,
                MeasurementTables.AttributionContract.TRIGGER_TIME);
        return Integer.valueOf((int) DatabaseUtils.longForQuery(
                mSQLTransaction.getDatabase(),
                query,
                new String[] {
                        sourceSite.toString(),
                        destinationSite.toString(),
                        excludedAdTech.toString(),
                        String.valueOf(windowEndTime),
                        String.valueOf(windowStartTime) }));
    }

    @Override
    public Integer countDistinctDestinationsPerPublisherXAdTechInActiveSource(Uri publisher,
            @EventSurfaceType int publisherType, Uri adTechDomain, Uri excludedDestination,
            @EventSurfaceType int destinationType, long windowStartTime, long windowEndTime)
            throws DatastoreException {
        String destinationColumn = destinationType == EventSurfaceType.APP
                ? MeasurementTables.SourceContract.APP_DESTINATION
                : MeasurementTables.SourceContract.WEB_DESTINATION;
        String query = String.format(
                "SELECT COUNT(DISTINCT %1$s) FROM %2$s "
                + "WHERE %3$s AND %4$s = ? AND %5$s = ? AND %1$s != ? "
                + "AND %6$s < ? AND %6$s >= ?",
                destinationColumn,
                MeasurementTables.SourceContract.TABLE,
                getPublisherWhereStatement(publisher, publisherType),
                MeasurementTables.SourceContract.AD_TECH_DOMAIN,
                MeasurementTables.SourceContract.STATUS,
                MeasurementTables.SourceContract.EVENT_TIME);
        return (int) DatabaseUtils.longForQuery(
                mSQLTransaction.getDatabase(),
                query,
                new String[] {
                        adTechDomain.toString(),
                        String.valueOf(Source.Status.ACTIVE),
                        excludedDestination.toString(),
                        String.valueOf(windowEndTime),
                        String.valueOf(windowStartTime) });
    }

    @Override
    public Integer countDistinctAdTechsPerPublisherXDestinationInSource(Uri publisher,
            @EventSurfaceType int publisherType, Uri destination, Uri excludedAdTech,
            long windowStartTime, long windowEndTime) throws DatastoreException {
        String query = String.format(
                "SELECT COUNT(DISTINCT %1$s) FROM %2$s "
                + "WHERE %3$s AND (%4$s = ? OR %5$s = ?) AND %1s != ? "
                + "AND %6$s < ? AND %6$s >= ?",
                MeasurementTables.SourceContract.AD_TECH_DOMAIN,
                MeasurementTables.SourceContract.TABLE,
                getPublisherWhereStatement(publisher, publisherType),
                MeasurementTables.SourceContract.APP_DESTINATION,
                MeasurementTables.SourceContract.WEB_DESTINATION,
                MeasurementTables.SourceContract.EVENT_TIME);
        return Integer.valueOf((int) DatabaseUtils.longForQuery(
                mSQLTransaction.getDatabase(),
                query,
                new String[] {
                        destination.toString(),
                        destination.toString(),
                        excludedAdTech.toString(),
                        String.valueOf(windowEndTime),
                        String.valueOf(windowStartTime) }));
    }

    @Override
    public void deleteAppRecords(Uri uri) throws DatastoreException {
        String uriStr = uri.toString();
        SQLiteDatabase db = mSQLTransaction.getDatabase();
        // For all Source records matching the given Uri
        // as REGISTRANT, obtains EventReport records who's SOURCE_ID
        // matches a Source records' EVENT_ID.
        db.delete(
                MeasurementTables.EventReportContract.TABLE,
                String.format(
                        "%1$s IN ("
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
                        MeasurementTables.SourceContract.APP_DESTINATION,
                        MeasurementTables.EventReportContract.AD_TECH_DOMAIN,
                        MeasurementTables.SourceContract.AD_TECH_DOMAIN,
                        MeasurementTables.SourceContract.REGISTRANT),
                new String[] {uriStr});
        // EventReport table
        db.delete(MeasurementTables.EventReportContract.TABLE,
                MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION + " = ?",
                new String[]{uriStr});
        // Source table
        db.delete(
                MeasurementTables.SourceContract.TABLE,
                "( "
                        + MeasurementTables.SourceContract.REGISTRANT
                        + " = ? ) OR "
                        + "("
                        + MeasurementTables.SourceContract.STATUS
                        + " = ? AND "
                        + MeasurementTables.SourceContract.APP_DESTINATION
                        + " = ? )",
                new String[] {uriStr, String.valueOf(Source.Status.IGNORED), uriStr});
        // Trigger table
        db.delete(MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.REGISTRANT + " = ?",
                new String[]{uriStr});
        // Attribution table
        db.delete(MeasurementTables.AttributionContract.TABLE,
                MeasurementTables.AttributionContract.SOURCE_SITE + " = ? OR "
                        + MeasurementTables.AttributionContract.DESTINATION_SITE + " = ?",
                new String[]{uriStr, uriStr});
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
        // Attribution table
        db.delete(MeasurementTables.AttributionContract.TABLE,
                MeasurementTables.AttributionContract.TRIGGER_TIME + " < ?",
                new String[]{earliestValidInsertionStr});
    }

    @Override
    public void deleteMeasurementData(
            @NonNull Uri registrant,
            @NonNull Instant start,
            @NonNull Instant end,
            @NonNull List<Uri> origins,
            @NonNull List<Uri> domains,
            @DeletionRequest.MatchBehavior int matchBehavior,
            @DeletionRequest.DeletionMode int deletionMode)
            throws DatastoreException {
        Objects.requireNonNull(registrant);
        Objects.requireNonNull(origins);
        Objects.requireNonNull(domains);
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        Instant cappedStart = capDeletionRange(start);
        Instant cappedEnd = capDeletionRange(end);
        validateRange(cappedStart, cappedEnd);
        // Handle no-op case
        // Preserving everything => Do Nothing
        if (domains.isEmpty()
                && origins.isEmpty()
                && matchBehavior == DeletionRequest.MATCH_BEHAVIOR_PRESERVE) {
            return;
        }
        final SQLiteDatabase db = mSQLTransaction.getDatabase();
        Function<String, String> registrantMatcher = getRegistrantMatcher(registrant);
        Function<String, String> siteMatcher = getSiteMatcher(origins, domains, matchBehavior);
        Function<String, String> timeMatcher = getTimeMatcher(cappedStart, cappedEnd);

        if (deletionMode == DeletionRequest.DELETION_MODE_ALL) {
            deleteAttribution(db, registrantMatcher, siteMatcher, timeMatcher);
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

    private void deleteAttribution(
            SQLiteDatabase db,
            Function<String, String> registrantMatcher,
            Function<String, String> siteMatcher,
            Function<String, String> timeMatcher) {
        // Where Statement:
        // (registrant - RegistrantMatching) AND
        // ((destinationOrigin - OriginMatching) OR (sourceOrigin - OriginMatching)) AND
        // (triggerTime - TimeMatching)
        db.delete(
                MeasurementTables.AttributionContract.TABLE,
                mergeConditions(
                        " AND ",
                        registrantMatcher.apply(
                                MeasurementTables.AttributionContract.REGISTRANT),
                        mergeConditions(
                                " OR ",
                                siteMatcher.apply(
                                        MeasurementTables.AttributionContract
                                                .DESTINATION_ORIGIN),
                                siteMatcher.apply(
                                        MeasurementTables.AttributionContract
                                                .SOURCE_ORIGIN)),
                        timeMatcher.apply(
                                MeasurementTables.AttributionContract.TRIGGER_TIME)),
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

    private static Function<String, String> getSiteMatcher(
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

    private void validateRange(Instant start, Instant end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start or end date is null");
        }

        if (start.isAfter(end)) {
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
        String subQuery =
                sqb.buildQuery(
                        new String[] {MeasurementTables.SourceContract.ID},
                        String.format(
                                MeasurementTables.SourceContract.APP_DESTINATION
                                        + " = \"%s\" AND "
                                        + MeasurementTables.SourceContract.EVENT_TIME
                                        + " <= %2$d AND "
                                        + MeasurementTables.SourceContract.EXPIRY_TIME
                                        + " > %2$d AND "
                                        + MeasurementTables.SourceContract.EVENT_TIME
                                        + " + "
                                        + MeasurementTables.SourceContract
                                                .INSTALL_ATTRIBUTION_WINDOW
                                        + " >= %2$d",
                                uri.toString(),
                                eventTimestamp),
                        /* groupBy= */ null,
                        /* having= */ null,
                        /* sortOrder= */ MeasurementTables.SourceContract.PRIORITY
                                + " DESC, "
                                + MeasurementTables.SourceContract.EVENT_TIME
                                + " DESC",
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
        db.update(
                MeasurementTables.SourceContract.TABLE,
                values,
                MeasurementTables.SourceContract.APP_DESTINATION + " = ?",
                new String[] {uri.toString()});
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
        values.put(MeasurementTables.AggregateReport.AD_TECH_DOMAIN,
                aggregateReport.getAdTechDomain().toString());
        values.put(MeasurementTables.AggregateReport.ENROLLMENT_ID,
                aggregateReport.getEnrollmentId());
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
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.AggregateReport.TABLE,
                                null,
                                MeasurementTables.AggregateReport.PUBLISHER
                                        + " = ? AND "
                                        + MeasurementTables.AggregateReport.STATUS
                                        + " = ? ",
                                new String[] {
                                    appName.toString(),
                                    String.valueOf(AggregateReport.Status.PENDING)
                                },
                                null,
                                null,
                                "RANDOM()",
                                null)) {
            while (cursor.moveToNext()) {
                aggregateReports.add(cursor.getString(cursor.getColumnIndex(
                        MeasurementTables.AggregateReport.ID)));
            }
            return aggregateReports;
        }
    }

    private static Optional<Pair<String, String>> getDestinationColumnAndValue(Trigger trigger) {
        if (trigger.getDestinationType() == EventSurfaceType.APP) {
            return Optional.of(Pair.create(
                    MeasurementTables.SourceContract.APP_DESTINATION,
                    trigger.getAttributionDestination().toString()));
        } else {
            Optional<Uri> topPrivateDomainAndScheme =
                    Web.topPrivateDomainAndScheme(trigger.getAttributionDestination());
            return topPrivateDomainAndScheme.map(
                    uri ->
                            Pair.create(
                                    MeasurementTables.SourceContract.WEB_DESTINATION,
                                    uri.toString()));
        }
    }

    private static Optional<Uri> extractBaseUri(Uri uri, @EventSurfaceType int eventSurfaceType) {
        return eventSurfaceType == EventSurfaceType.APP
                ? Optional.of(BaseUriExtractor.getBaseUri(uri))
                : Web.topPrivateDomainAndScheme(uri);
    }

    private static String getPublisherWhereStatement(Uri publisher,
            @EventSurfaceType int publisherType) {
        if (publisherType == EventSurfaceType.APP) {
            return String.format("%s = '%s'", MeasurementTables.SourceContract.PUBLISHER,
                    publisher.toString());
        } else {
            return String.format("(%1$s = '%2$s://%3$s' OR %1$s LIKE '%2$s://%%.%3$s')",
                    MeasurementTables.SourceContract.PUBLISHER,
                    publisher.getScheme(),
                    publisher.getEncodedAuthority());
        }
    }

    private static String getNullableUriString(@Nullable Uri uri) {
        return Optional.ofNullable(uri).map(Uri::toString).orElse(null);
    }

    /**
     * Returns the min or max possible long value to avoid the ArithmeticException thrown when
     * calling toEpochMilli() on Instant.MAX or Instant.MIN
     */
    private static Instant capDeletionRange(Instant instant) {
        Instant[] instants = {
            Instant.ofEpochMilli(Long.MIN_VALUE), instant, Instant.ofEpochMilli(Long.MAX_VALUE)
        };
        Arrays.sort(instants);
        return instants[1];
    }
}
