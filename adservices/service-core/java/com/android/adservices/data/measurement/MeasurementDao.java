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

import static android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE;

import android.adservices.measurement.DeletionRequest;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.measurement.MeasurementTables.SourceAttributionScopeContract;
import com.android.adservices.data.measurement.MeasurementTables.SourceContract;
import com.android.adservices.data.measurement.MeasurementTables.SourceDestination;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.WebAddresses;
import com.android.adservices.service.measurement.Attribution;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.KeyValueData;
import com.android.adservices.service.measurement.KeyValueData.DataType;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.registration.AsyncRegistration;
import com.android.adservices.service.measurement.reporting.DebugReport;
import com.android.adservices.service.measurement.util.BaseUriExtractor;
import com.android.adservices.service.measurement.util.UnsignedLong;

import com.google.common.collect.ImmutableList;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Data Access Object for the Measurement PPAPI module. */
class MeasurementDao implements IMeasurementDao {

    private static final int MAX_COMPOUND_SELECT = 250;
    private Supplier<Boolean> mDbFileMaxSizeLimitReachedSupplier;
    private Supplier<Integer> mReportingRetryLimitSupplier;
    private Supplier<Boolean> mReportingRetryLimitEnabledSupplier;

    private SQLTransaction mSQLTransaction;

    MeasurementDao(
            @NonNull Supplier<Boolean> dbFileMaxSizeLimitReachedSupplier,
            @NonNull Supplier<Integer> reportingRetryLimitSupplier,
            @NonNull Supplier<Boolean> reportingRetryLimitEnabledSupplier) {
        mDbFileMaxSizeLimitReachedSupplier = dbFileMaxSizeLimitReachedSupplier;
        mReportingRetryLimitSupplier = reportingRetryLimitSupplier;
        mReportingRetryLimitEnabledSupplier = reportingRetryLimitEnabledSupplier;
    }

    @Override
    public void setTransaction(ITransaction transaction) {
        if (!(transaction instanceof SQLTransaction)) {
            throw new IllegalArgumentException("transaction should be a SQLTransaction.");
        }
        mSQLTransaction = (SQLTransaction) transaction;
    }

    @Override
    public void insertTrigger(@NonNull Trigger trigger) throws DatastoreException {
        if (mDbFileMaxSizeLimitReachedSupplier.get()) {
            LoggerFactory.getMeasurementLogger()
                    .d("DB size has reached the limit, trigger will not be inserted");
            return;
        }

        ContentValues values = new ContentValues();
        values.put(MeasurementTables.TriggerContract.ID, UUID.randomUUID().toString());
        values.put(
                MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION,
                trigger.getAttributionDestination().toString());
        values.put(
                MeasurementTables.TriggerContract.DESTINATION_TYPE, trigger.getDestinationType());
        values.put(MeasurementTables.TriggerContract.TRIGGER_TIME, trigger.getTriggerTime());
        values.put(MeasurementTables.TriggerContract.EVENT_TRIGGERS, trigger.getEventTriggers());
        values.put(MeasurementTables.TriggerContract.STATUS, Trigger.Status.PENDING);
        values.put(MeasurementTables.TriggerContract.ENROLLMENT_ID, trigger.getEnrollmentId());
        values.put(
                MeasurementTables.TriggerContract.REGISTRANT, trigger.getRegistrant().toString());
        values.put(
                MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA,
                trigger.getAggregateTriggerData());
        values.put(
                MeasurementTables.TriggerContract.AGGREGATE_VALUES, trigger.getAggregateValues());
        values.put(
                MeasurementTables.TriggerContract.AGGREGATABLE_DEDUPLICATION_KEYS,
                trigger.getAggregateDeduplicationKeys());
        values.put(MeasurementTables.TriggerContract.FILTERS, trigger.getFilters());
        values.put(MeasurementTables.TriggerContract.NOT_FILTERS, trigger.getNotFilters());
        values.put(
                MeasurementTables.TriggerContract.DEBUG_KEY,
                getNullableUnsignedLong(trigger.getDebugKey()));
        values.put(MeasurementTables.TriggerContract.DEBUG_REPORTING, trigger.isDebugReporting());
        values.put(MeasurementTables.TriggerContract.AD_ID_PERMISSION, trigger.hasAdIdPermission());
        values.put(
                MeasurementTables.TriggerContract.AR_DEBUG_PERMISSION,
                trigger.hasArDebugPermission());
        values.put(
                MeasurementTables.TriggerContract.ATTRIBUTION_CONFIG,
                trigger.getAttributionConfig());
        values.put(
                MeasurementTables.TriggerContract.X_NETWORK_KEY_MAPPING,
                trigger.getAdtechKeyMapping());
        values.put(MeasurementTables.TriggerContract.DEBUG_JOIN_KEY, trigger.getDebugJoinKey());
        values.put(MeasurementTables.TriggerContract.PLATFORM_AD_ID, trigger.getPlatformAdId());
        values.put(MeasurementTables.TriggerContract.DEBUG_AD_ID, trigger.getDebugAdId());
        values.put(
                MeasurementTables.TriggerContract.REGISTRATION_ORIGIN,
                trigger.getRegistrationOrigin().toString());
        values.put(
                MeasurementTables.TriggerContract.AGGREGATION_COORDINATOR_ORIGIN,
                getNullableUriString(trigger.getAggregationCoordinatorOrigin()));
        values.put(
                MeasurementTables.TriggerContract.AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG,
                trigger.getAggregatableSourceRegistrationTimeConfig().name());
        values.put(
                MeasurementTables.TriggerContract.TRIGGER_CONTEXT_ID,
                trigger.getTriggerContextId());
        values.put(
                MeasurementTables.TriggerContract.ATTRIBUTION_SCOPES,
                trigger.getAttributionScopesString());

        long rowId =
                mSQLTransaction
                        .getDatabase()
                        .insert(
                                MeasurementTables.TriggerContract.TABLE,
                                /* nullColumnHack= */ null,
                                values);
        LoggerFactory.getMeasurementLogger().d("MeasurementDao: insertTrigger: rowId=" + rowId);
        if (rowId == -1) {
            throw new DatastoreException("Trigger insertion failed.");
        }
    }

    @Override
    public List<String> getPendingTriggerIds() throws DatastoreException {
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.TriggerContract.TABLE,
                                new String[] {MeasurementTables.TriggerContract.ID},
                                MeasurementTables.TriggerContract.STATUS + " = ? ",
                                new String[] {String.valueOf(Trigger.Status.PENDING)},
                                /* groupBy= */ null,
                                /* having= */ null,
                                /* orderBy= */ MeasurementTables.TriggerContract.TRIGGER_TIME,
                                /* limit= */ null)) {
            List<String> result = new ArrayList<>();
            while (cursor.moveToNext()) {
                result.add(cursor.getString(/* columnIndex= */ 0));
            }
            return result;
        }
    }

    @Override
    public Source getSource(@NonNull String sourceId) throws DatastoreException {
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                SourceContract.TABLE,
                                /* columns= */ null,
                                SourceContract.ID + " = ? ",
                                new String[] {sourceId},
                                /* groupBy= */ null,
                                /* having= */ null,
                                /* orderBy= */ null,
                                /* limit= */ null)) {
            if (cursor.getCount() == 0) {
                throw new DatastoreException("Source retrieval failed. Id: " + sourceId);
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructSourceFromCursor(cursor);
        }
    }

    @Override
    @Nullable
    public Pair<List<Uri>, List<Uri>> getSourceDestinations(@NonNull String sourceId)
            throws DatastoreException {
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                SourceDestination.TABLE,
                                new String[] {
                                    SourceDestination.DESTINATION,
                                    SourceDestination.DESTINATION_TYPE
                                },
                                SourceDestination.SOURCE_ID + " = ? ",
                                new String[] {sourceId},
                                /* groupBy= */ null,
                                /* having= */ null,
                                /* orderBy= */ null,
                                /* limit= */ null)) {
            List<Uri> appDestinations = new ArrayList<>();
            List<Uri> webDestinations = new ArrayList<>();
            while (cursor.moveToNext()) {
                int destinationType =
                        cursor.getInt(cursor.getColumnIndex(SourceDestination.DESTINATION_TYPE));
                if (destinationType == EventSurfaceType.APP) {
                    appDestinations.add(
                            Uri.parse(
                                    cursor.getString(
                                            cursor.getColumnIndex(SourceDestination.DESTINATION))));
                } else {
                    webDestinations.add(
                            Uri.parse(
                                    cursor.getString(
                                            cursor.getColumnIndex(SourceDestination.DESTINATION))));
                }
            }
            return Pair.create(appDestinations, webDestinations);
        }
    }

    @Override
    public List<String> getSourceAttributionScopes(@NonNull String sourceId)
            throws DatastoreException {
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                SourceAttributionScopeContract.TABLE,
                                new String[] {SourceAttributionScopeContract.ATTRIBUTION_SCOPE},
                                SourceAttributionScopeContract.SOURCE_ID + " = ? ",
                                new String[] {sourceId},
                                /* groupBy= */ null,
                                /* having= */ null,
                                /* orderBy= */ null,
                                /* limit= */ null)) {
            List<String> attributionScopes = new ArrayList<>();
            while (cursor.moveToNext()) {
                attributionScopes.add(
                        cursor.getString(
                                cursor.getColumnIndexOrThrow(
                                        SourceAttributionScopeContract.ATTRIBUTION_SCOPE)));
            }
            return attributionScopes;
        }
    }

    @Override
    public Set<String> getNavigationAttributionScopesForRegistration(
            @NonNull String registrationId, @NonNull String registrationOrigin)
            throws DatastoreException {
        // Joins Source, SourceDestination and SourceAttributionScope tables on source id.
        String joinString =
                " FROM "
                        + SourceContract.TABLE
                        + " s INNER JOIN "
                        + SourceAttributionScopeContract.TABLE
                        + " a ON s."
                        + SourceContract.ID
                        + " = a."
                        + SourceAttributionScopeContract.SOURCE_ID;

        String sourceWhereStatement =
                mergeConditions(
                        " AND ",
                        SourceContract.STATUS + " = " + Source.Status.ACTIVE,
                        SourceContract.REGISTRATION_ID
                                + " = "
                                + DatabaseUtils.sqlEscapeString(registrationId),
                        SourceContract.SOURCE_TYPE
                                + " = "
                                + DatabaseUtils.sqlEscapeString(
                                        String.valueOf(Source.SourceType.NAVIGATION)),
                        SourceContract.REGISTRATION_ORIGIN
                                + " = "
                                + DatabaseUtils.sqlEscapeString(registrationOrigin));

        String query =
                String.format(
                        Locale.ENGLISH,
                        "SELECT DISTINCT %1$s" + joinString + " WHERE " + sourceWhereStatement,
                        SourceAttributionScopeContract.ATTRIBUTION_SCOPE);
        try (Cursor cursor = mSQLTransaction.getDatabase().rawQuery(query, null)) {
            Set<String> attributionScopes = new HashSet<>();
            while (cursor.moveToNext()) {
                attributionScopes.add(
                        cursor.getString(
                                cursor.getColumnIndexOrThrow(
                                        SourceAttributionScopeContract.ATTRIBUTION_SCOPE)));
            }
            return attributionScopes;
        }
    }

    // Given a list of destinations and destination types, return a SELECT statement for matching
    // source IDs.
    private static String selectSourceIdsByDestinations(
            @NonNull List<Pair<Integer, String>> destinations,
            @NonNull String sourceWhereStatement) {
        List<String> destinationConditions = new ArrayList<>();
        destinations.stream()
                .forEach(
                        destination ->
                                destinationConditions.add(
                                        mergeConditions(
                                                " AND ",
                                                SourceDestination.DESTINATION_TYPE
                                                        + " = "
                                                        + destination.first,
                                                SourceDestination.DESTINATION
                                                        + " = '"
                                                        + destination.second
                                                        + "'")));
        return String.format(
                Locale.ENGLISH,
                "SELECT "
                        + SourceContract.ID
                        + " FROM "
                        + SourceContract.TABLE
                        + " WHERE "
                        + SourceContract.ID
                        + " IN (SELECT "
                        + SourceDestination.SOURCE_ID
                        + " FROM "
                        + SourceDestination.TABLE
                        + " WHERE "
                        + mergeConditions(" OR ", destinationConditions.toArray(new String[0]))
                        + ") AND ("
                        + sourceWhereStatement
                        + ")");
    }

    private String selectSourceIdsByOriginAndDestination(
            @NonNull String registrationOrigin,
            @NonNull List<Pair<Integer, String>> destinations,
            @NonNull String extraSourceWhereStatement) {

        String sourceWhereStatement =
                String.format(
                        Locale.ENGLISH,
                        mergeConditions(
                                " AND ",
                                SourceContract.STATUS + " = %1$d",
                                SourceContract.REGISTRATION_ORIGIN + " = '%2$s'",
                                extraSourceWhereStatement),
                        Source.Status.ACTIVE,
                        registrationOrigin);
        return selectSourceIdsByDestinations(destinations, sourceWhereStatement);
    }

    private void ignoreSourcesAndDeleteFakeReportsForAttributionScope(
            @NonNull Source pendingSource, @NonNull String selectActiveSourceIdsStatement)
            throws DatastoreException {
        final SQLiteDatabase db = mSQLTransaction.getDatabase();
        // Delete any pending reports which have trigger_time >= new source’s registration time.
        // Note selectActiveSourceIdsStatement will only select ACTIVE sources, which means we
        // need to delete pending reports before deactivating the sources (before they are
        // de-activated).
        ContentValues eventReportValues = new ContentValues();
        eventReportValues.put(
                MeasurementTables.EventReportContract.STATUS, EventReport.Status.MARKED_TO_DELETE);
        db.update(
                MeasurementTables.EventReportContract.TABLE,
                eventReportValues,
                mergeConditions(
                        " AND ",
                        MeasurementTables.EventReportContract.SOURCE_ID
                                + " IN ("
                                + selectActiveSourceIdsStatement
                                + ")",
                        MeasurementTables.EventReportContract.TRIGGER_TIME
                                + " >= "
                                + pendingSource.getEventTime()),
                new String[] {});

        // Delete any pending reports which have trigger_time >= new source’s registration time.
        ContentValues sourceValues = new ContentValues();
        sourceValues.put(SourceContract.STATUS, Source.Status.IGNORED);
        db.update(
                SourceContract.TABLE,
                sourceValues,
                SourceContract.ID + " IN (" + selectActiveSourceIdsStatement + ")",
                new String[] {});
    }

    private String selectSourcesWithOldAttributionScopesWhereStatement(
            @EventSurfaceType int destinationType,
            @NonNull String destination,
            @NonNull String registrationOrigin,
            long attributionScopeLimit) {
        // Selects sources with given destination and reporting origin.
        String whereString =
                mergeConditions(
                        " AND ",
                        SourceContract.REGISTRATION_ORIGIN
                                + " = "
                                + DatabaseUtils.sqlEscapeString(registrationOrigin),
                        SourceDestination.DESTINATION
                                + " = "
                                + DatabaseUtils.sqlEscapeString(destination),
                        SourceDestination.DESTINATION_TYPE + " = " + destinationType);

        // Joins Source, SourceDestination and SourceAttributionScope tables on source id.
        String joinString =
                " FROM "
                        + SourceContract.TABLE
                        + " s INNER JOIN "
                        + SourceAttributionScopeContract.TABLE
                        + " a ON s."
                        + SourceContract.ID
                        + " = a."
                        + SourceAttributionScopeContract.SOURCE_ID
                        + " INNER JOIN "
                        + SourceDestination.TABLE
                        + " d ON s."
                        + SourceContract.ID
                        + " = d."
                        + SourceDestination.SOURCE_ID;

        // Arrange all attribution scopes according to their last source registration time, from
        // latest to earliest, and assign a unique rank number to each scope.
        // In case of identical registration times, prioritize the higher attribution scope
        // alphabetically.
        String attributionScopeRankQuery =
                String.format(
                        Locale.ENGLISH,
                        "SELECT a.%1$s,"
                                + " ROW_NUMBER() OVER"
                                + " (ORDER BY MAX(s.%2$s) DESC, a.%1$s DESC) AS rn"
                                + joinString
                                + " WHERE "
                                + whereString
                                + " GROUP BY a.%1$s",
                        SourceAttributionScopeContract.ATTRIBUTION_SCOPE,
                        SourceContract.EVENT_TIME);

        return String.format(
                Locale.ENGLISH,
                "SELECT DISTINCT s.%1$s"
                        + joinString
                        + " INNER JOIN ("
                        + attributionScopeRankQuery
                        + ") rank ON a.%3$s = rank.%3$s WHERE rank.rn > %4$s"
                        + " AND "
                        + whereString,
                SourceContract.ID,
                SourceAttributionScopeContract.TABLE,
                SourceAttributionScopeContract.ATTRIBUTION_SCOPE,
                attributionScopeLimit);
    }

    private void ignoreSourcesWithOldAttributionScopes(@NonNull Source pendingSource)
            throws DatastoreException {
        if (pendingSource.getAttributionScopeLimit() == null
                || pendingSource.getAttributionScopes() == null
                || pendingSource.getAttributionScopes().isEmpty()) {
            return;
        }
        SQLiteDatabase db = mSQLTransaction.getDatabase();
        // Make sure destinations are processed alphabetically.
        List<Pair<Integer, String>> destinations = pendingSource.getAllAttributionDestinations();
        Collections.sort(destinations, Comparator.comparing(pair -> pair.second));
        for (Pair<Integer, String> destination : destinations) {
            ignoreSourcesAndDeleteFakeReportsForAttributionScope(
                    pendingSource,
                    selectSourcesWithOldAttributionScopesWhereStatement(
                            destination.first,
                            destination.second,
                            pendingSource.getRegistrationOrigin().toString(),
                            pendingSource.getAttributionScopeLimit()));
        }
    }

    private void removeAttributionScopesForOldSources(@NonNull Source pendingSource)
            throws DatastoreException {
        String selectSourceIdStatement =
                selectSourceIdsByOriginAndDestination(
                        pendingSource.getRegistrationOrigin().toString(),
                        pendingSource.getAllAttributionDestinations(),
                        "");
        SQLiteDatabase db = mSQLTransaction.getDatabase();
        db.delete(
                SourceAttributionScopeContract.TABLE,
                SourceAttributionScopeContract.SOURCE_ID + " IN (" + selectSourceIdStatement + ")",
                new String[] {});
        ContentValues attributionScopeValues = new ContentValues();
        attributionScopeValues.putNull(SourceContract.ATTRIBUTION_SCOPE_LIMIT);
        attributionScopeValues.putNull(SourceContract.MAX_EVENT_STATES);
        db.update(
                SourceContract.TABLE,
                attributionScopeValues,
                SourceContract.ID + " IN (" + selectSourceIdStatement + ")",
                new String[] {});
    }

    @Override
    public void updateSourcesForAttributionScope(@NonNull Source pendingSource)
            throws DatastoreException {
        if (pendingSource.getAttributionScopeLimit() == null) {
            removeAttributionScopesForOldSources(pendingSource);
            return;
        }
        String incompatibleAttributionScopeLimitWhereStatement =
                mergeConditions(
                        " OR ",
                        SourceContract.ATTRIBUTION_SCOPE_LIMIT + " IS NULL",
                        SourceContract.ATTRIBUTION_SCOPE_LIMIT
                                + " < "
                                + pendingSource.getAttributionScopeLimit());
        String incompatibleMaxEventStatesWhereStatement =
                mergeConditions(
                        " AND ",
                        SourceContract.MAX_EVENT_STATES + " IS NOT NULL",
                        SourceContract.MAX_EVENT_STATES
                                + " != "
                                + pendingSource.getMaxEventStates());
        String incompatibleWhereStatement =
                mergeConditions(
                        " OR ",
                        incompatibleMaxEventStatesWhereStatement,
                        incompatibleAttributionScopeLimitWhereStatement);

        String selectIncompatibleSourceIdStatement =
                selectSourceIdsByOriginAndDestination(
                        pendingSource.getRegistrationOrigin().toString(),
                        pendingSource.getAllAttributionDestinations(),
                        incompatibleWhereStatement);

        ignoreSourcesAndDeleteFakeReportsForAttributionScope(
                pendingSource, selectIncompatibleSourceIdStatement);
        ignoreSourcesWithOldAttributionScopes(pendingSource);
    }

    @Override
    public String getSourceRegistrant(@NonNull String sourceId) throws DatastoreException {
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                SourceContract.TABLE,
                                new String[] {
                                    SourceContract.REGISTRANT,
                                },
                                SourceContract.ID + " = ? ",
                                new String[] {sourceId},
                                /* groupBy= */ null,
                                /* having= */ null,
                                /* orderBy= */ null,
                                /* limit= */ null)) {
            if (cursor.getCount() == 0) {
                throw new DatastoreException("Source retrieval failed. Id: " + sourceId);
            }
            cursor.moveToNext();
            return cursor.getString(cursor.getColumnIndex(SourceContract.REGISTRANT));
        }
    }

    @Override
    public Trigger getTrigger(@NonNull String triggerId) throws DatastoreException {
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.TriggerContract.TABLE,
                                /* columns= */ null,
                                MeasurementTables.TriggerContract.ID + " = ? ",
                                new String[] {triggerId},
                                /* groupBy= */ null,
                                /* having= */ null,
                                /* orderBy= */ null,
                                /* limit= */ null)) {
            if (cursor.getCount() == 0) {
                throw new DatastoreException("Trigger retrieval failed. Id: " + triggerId);
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructTriggerFromCursor(cursor);
        }
    }

    @Override
    public int getNumAggregateReportsPerDestination(
            @NonNull Uri attributionDestination, @EventSurfaceType int destinationType)
            throws DatastoreException {
        return getNumReportsPerDestination(
                MeasurementTables.AggregateReport.TABLE,
                MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION,
                attributionDestination,
                destinationType);
    }

    @Override
    public int getNumEventReportsPerDestination(
            @NonNull Uri attributionDestination, @EventSurfaceType int destinationType)
            throws DatastoreException {
        return getNumReportsPerDestination(
                MeasurementTables.EventReportContract.TABLE,
                MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION,
                attributionDestination,
                destinationType);
    }

    @Override
    public int getNumAggregateReportsPerSource(@NonNull String sourceId) throws DatastoreException {
        String query =
                String.format(
                        Locale.ENGLISH,
                        "SELECT COUNT(*) FROM %1$s WHERE %2$s = '%3$s'",
                        MeasurementTables.AggregateReport.TABLE,
                        MeasurementTables.AggregateReport.SOURCE_ID,
                        sourceId);
        return (int) DatabaseUtils.longForQuery(mSQLTransaction.getDatabase(), query, null);
    }

    @Override
    public EventReport getEventReport(@NonNull String eventReportId) throws DatastoreException {
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.EventReportContract.TABLE,
                                null,
                                MeasurementTables.EventReportContract.ID + " = ? ",
                                new String[] {eventReportId},
                                null,
                                null,
                                null,
                                null)) {
            if (cursor.getCount() == 0) {
                throw new DatastoreException("EventReport retrieval failed. Id: " + eventReportId);
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructEventReportFromCursor(cursor);
        }
    }

    @Override
    public AggregateReport getAggregateReport(@NonNull String aggregateReportId)
            throws DatastoreException {
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.AggregateReport.TABLE,
                                null,
                                MeasurementTables.AggregateReport.ID + " = ? ",
                                new String[] {aggregateReportId},
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

    @Nullable
    @Override
    public DebugReport getDebugReport(String debugReportId) throws DatastoreException {
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.DebugReportContract.TABLE,
                                /* columns= */ null,
                                MeasurementTables.DebugReportContract.ID + " = ? ",
                                new String[] {debugReportId},
                                /* groupBy= */ null,
                                /* having= */ null,
                                /* orderBy= */ null,
                                /* limit= */ null)) {
            if (cursor.getCount() == 0) {
                throw new DatastoreException("DebugReport retrieval failed. Id: " + debugReportId);
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructDebugReportFromCursor(cursor);
        }
    }

    @Override
    public String insertSource(@NonNull Source source) throws DatastoreException {
        if (mDbFileMaxSizeLimitReachedSupplier.get()) {
            LoggerFactory.getMeasurementLogger()
                    .d("DB size has reached the limit, source will not be inserted");
            return null;
        }

        String sourceId = UUID.randomUUID().toString();

        ContentValues values = new ContentValues();
        values.put(SourceContract.ID, sourceId);
        values.put(SourceContract.EVENT_ID, source.getEventId().getValue());
        values.put(SourceContract.PUBLISHER, source.getPublisher().toString());
        values.put(SourceContract.PUBLISHER_TYPE, source.getPublisherType());
        values.put(SourceContract.ENROLLMENT_ID, source.getEnrollmentId());
        values.put(SourceContract.EVENT_TIME, source.getEventTime());
        values.put(SourceContract.EXPIRY_TIME, source.getExpiryTime());
        values.put(SourceContract.EVENT_REPORT_WINDOW, source.getEventReportWindow());
        values.put(
                SourceContract.REINSTALL_REATTRIBUTION_WINDOW,
                source.getReinstallReattributionWindow());
        values.put(SourceContract.AGGREGATABLE_REPORT_WINDOW, source.getAggregatableReportWindow());
        values.put(SourceContract.PRIORITY, source.getPriority());
        values.put(SourceContract.STATUS, source.getStatus());
        values.put(SourceContract.SOURCE_TYPE, source.getSourceType().name());
        values.put(SourceContract.REGISTRANT, source.getRegistrant().toString());
        values.put(SourceContract.INSTALL_ATTRIBUTION_WINDOW, source.getInstallAttributionWindow());
        values.put(SourceContract.INSTALL_COOLDOWN_WINDOW, source.getInstallCooldownWindow());
        values.put(SourceContract.ATTRIBUTION_MODE, source.getAttributionMode());
        values.put(SourceContract.AGGREGATE_SOURCE, source.getAggregateSource());
        values.put(SourceContract.FILTER_DATA, source.getFilterDataString());
        values.put(SourceContract.SHARED_FILTER_DATA_KEYS, source.getSharedFilterDataKeys());
        values.put(SourceContract.AGGREGATE_CONTRIBUTIONS, 0);
        values.put(SourceContract.DEBUG_KEY, getNullableUnsignedLong(source.getDebugKey()));
        values.put(SourceContract.DEBUG_REPORTING, source.isDebugReporting());
        values.put(SourceContract.AD_ID_PERMISSION, source.hasAdIdPermission());
        values.put(SourceContract.AR_DEBUG_PERMISSION, source.hasArDebugPermission());
        values.put(SourceContract.SHARED_AGGREGATION_KEYS, source.getSharedAggregationKeys());
        values.put(SourceContract.REGISTRATION_ID, source.getRegistrationId());
        values.put(SourceContract.INSTALL_TIME, source.getInstallTime());
        values.put(SourceContract.DEBUG_JOIN_KEY, source.getDebugJoinKey());
        values.put(SourceContract.PLATFORM_AD_ID, source.getPlatformAdId());
        values.put(SourceContract.DEBUG_AD_ID, source.getDebugAdId());
        values.put(SourceContract.REGISTRATION_ORIGIN, source.getRegistrationOrigin().toString());
        values.put(
                SourceContract.COARSE_EVENT_REPORT_DESTINATIONS,
                source.hasCoarseEventReportDestinations());
        if (source.getTriggerData() != null) {
            values.put(
                    SourceContract.TRIGGER_DATA,
                    collectionToCommaSeparatedString(source.getTriggerData()));
        }
        if (source.getTriggerSpecs() != null) {
            values.put(SourceContract.TRIGGER_SPECS, source.getTriggerSpecs().encodeToJson());
            values.put(
                    SourceContract.PRIVACY_PARAMETERS,
                    source.getTriggerSpecs().encodePrivacyParametersToJsonString());
        }
        values.put(SourceContract.MAX_EVENT_LEVEL_REPORTS, source.getMaxEventLevelReports());
        values.put(SourceContract.EVENT_REPORT_WINDOWS, source.getEventReportWindows());
        values.put(
                SourceContract.SHARED_DEBUG_KEY,
                getNullableUnsignedLong(source.getSharedDebugKey()));
        values.put(SourceContract.TRIGGER_DATA_MATCHING, source.getTriggerDataMatching().name());

        Flags flags = FlagsFactory.getFlags();
        if (flags.getMeasurementEnableSourceDestinationLimitPriority()) {
            values.put(
                    SourceContract.DESTINATION_LIMIT_PRIORITY,
                    source.getDestinationLimitPriority());
        }

        boolean attributionScopeEnabled = flags.getMeasurementEnableAttributionScope();
        if (attributionScopeEnabled) {
            values.put(SourceContract.ATTRIBUTION_SCOPE_LIMIT, source.getAttributionScopeLimit());
            values.put(SourceContract.MAX_EVENT_STATES, source.getMaxEventStates());
        }

        if (flags.getMeasurementEnableEventLevelEpsilonInSource()
                && source.getEventLevelEpsilon() != null) {
            values.put(SourceContract.EVENT_LEVEL_EPSILON, source.getEventLevelEpsilon());
        }

        long rowId =
                mSQLTransaction
                        .getDatabase()
                        .insert(SourceContract.TABLE, /* nullColumnHack= */ null, values);
        LoggerFactory.getMeasurementLogger().d("MeasurementDao: insertSource: rowId=" + rowId);

        if (rowId == -1) {
            throw new DatastoreException("Source insertion failed.");
        }

        // Insert source destinations
        if (source.getAppDestinations() != null) {
            for (Uri appDestination : source.getAppDestinations()) {
                ContentValues destinationValues = new ContentValues();
                destinationValues.put(SourceDestination.SOURCE_ID, sourceId);
                destinationValues.put(SourceDestination.DESTINATION_TYPE, EventSurfaceType.APP);
                destinationValues.put(SourceDestination.DESTINATION, appDestination.toString());
                long destinationRowId =
                        mSQLTransaction
                                .getDatabase()
                                .insert(
                                        SourceDestination.TABLE,
                                        /* nullColumnHack= */ null,
                                        destinationValues);
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "MeasurementDao: insertSource: insert sourceDestination: rowId="
                                        + destinationRowId);
                if (destinationRowId == -1) {
                    throw new DatastoreException(
                            "Source insertion failed on inserting app destination.");
                }
            }
        }

        if (source.getWebDestinations() != null) {
            for (Uri webDestination : source.getWebDestinations()) {
                ContentValues destinationValues = new ContentValues();
                destinationValues.put(SourceDestination.SOURCE_ID, sourceId);
                destinationValues.put(SourceDestination.DESTINATION_TYPE, EventSurfaceType.WEB);
                destinationValues.put(SourceDestination.DESTINATION, webDestination.toString());
                long destinationRowId =
                        mSQLTransaction
                                .getDatabase()
                                .insert(
                                        SourceDestination.TABLE,
                                        /* nullColumnHack= */ null,
                                        destinationValues);
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "MeasurementDao: insertSource: insert sourceDestination: rowId="
                                        + destinationRowId);
                if (destinationRowId == -1) {
                    throw new DatastoreException(
                            "Source insertion failed on inserting web destination.");
                }
            }
        }

        if (attributionScopeEnabled && source.getAttributionScopes() != null) {
            for (String attributionScope : new HashSet<>(source.getAttributionScopes())) {
                ContentValues attributionScopeValues = new ContentValues();
                attributionScopeValues.put(SourceAttributionScopeContract.SOURCE_ID, sourceId);
                attributionScopeValues.put(
                        SourceAttributionScopeContract.ATTRIBUTION_SCOPE, attributionScope);
                long attributionScopeRowId =
                        mSQLTransaction
                                .getDatabase()
                                .insert(
                                        SourceAttributionScopeContract.TABLE,
                                        /* nullColumnHack= */ null,
                                        attributionScopeValues);
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "MeasurementDao: insertSource: insert sourceAttributionScope:"
                                        + " rowId="
                                        + attributionScopeRowId);
                if (attributionScopeRowId == -1) {
                    throw new DatastoreException(
                            "Source insertion failed on inserting attribution scopes.");
                }
            }
        }
        return sourceId;
    }

    private List<Source> populateAttributionScopes(List<Source> sources) throws DatastoreException {
        Map<String, Source.Builder> sourceIdToSource =
                sources.stream()
                        .collect(
                                Collectors.toMap(
                                        Source::getId, source -> Source.Builder.from(source)));
        String attributionScopesWhereStatement =
                SourceAttributionScopeContract.SOURCE_ID
                        + " IN ("
                        + sources.stream()
                                .map(Source::getId)
                                .map(DatabaseUtils::sqlEscapeString)
                                .collect(Collectors.joining(","))
                        + ")";
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                SourceAttributionScopeContract.TABLE,
                                new String[] {
                                    SourceAttributionScopeContract.SOURCE_ID,
                                    SourceAttributionScopeContract.ATTRIBUTION_SCOPE
                                },
                                attributionScopesWhereStatement,
                                null,
                                null,
                                null,
                                null)) {
            Map<String, List<String>> sourceIdToAttributionScopes = new HashMap<>();
            while (cursor.moveToNext()) {
                String sourceId =
                        cursor.getString(
                                cursor.getColumnIndexOrThrow(
                                        SourceAttributionScopeContract.SOURCE_ID));
                String attributionScope =
                        cursor.getString(
                                cursor.getColumnIndexOrThrow(
                                        SourceAttributionScopeContract.ATTRIBUTION_SCOPE));
                sourceIdToAttributionScopes.putIfAbsent(sourceId, new ArrayList<>());
                sourceIdToAttributionScopes.get(sourceId).add(attributionScope);
            }
            sourceIdToAttributionScopes.forEach(
                    (sourceId, attributionScopes) -> {
                        sourceIdToSource.get(sourceId).setAttributionScopes(attributionScopes);
                    });
        }
        return sourceIdToSource.values().stream()
                .map(Source.Builder::build)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public List<Source> getMatchingActiveSources(@NonNull Trigger trigger)
            throws DatastoreException {
        List<Source> sources = new ArrayList<>();
        Optional<String> destinationValue = getDestinationValue(trigger);
        if (!destinationValue.isPresent()) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "getMatchingActiveSources: unable to obtain destination value: %s",
                            trigger.getAttributionDestination().toString());
            return sources;
        }
        String triggerDestinationValue = destinationValue.get();
        String sourceWhereStatement =
                String.format(
                        "%1$s.%2$s = ? "
                                + "AND %1$s.%3$s <= ? "
                                + "AND %1$s.%4$s > ? "
                                + "AND %1$s.%5$s = ?",
                        SourceContract.TABLE,
                        SourceContract.REGISTRATION_ORIGIN,
                        SourceContract.EVENT_TIME,
                        SourceContract.EXPIRY_TIME,
                        SourceContract.STATUS);
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .rawQuery(
                                selectSourcesByDestination(
                                        triggerDestinationValue,
                                        trigger.getDestinationType(),
                                        sourceWhereStatement),
                                new String[] {
                                    trigger.getRegistrationOrigin().toString(),
                                    String.valueOf(trigger.getTriggerTime()),
                                    String.valueOf(trigger.getTriggerTime()),
                                    String.valueOf(Source.Status.ACTIVE)
                                })) {
            while (cursor.moveToNext()) {
                sources.add(SqliteObjectMapper.constructSourceFromCursor(cursor));
            }
            return FlagsFactory.getFlags().getMeasurementEnableAttributionScope()
                            && trigger.getAttributionScopesString() != null
                    ? populateAttributionScopes(sources)
                    : sources;
        }
    }

    @Override
    public Optional<Source> getNearestDelayedMatchingActiveSource(@NonNull Trigger trigger)
            throws DatastoreException {
        Optional<String> destinationValue = getDestinationValue(trigger);
        if (!destinationValue.isPresent()) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "getMatchingActiveDelayedSources: unable to obtain destination value:"
                                    + " %s",
                            trigger.getAttributionDestination().toString());
            return Optional.empty();
        }
        String triggerDestinationValue = destinationValue.get();
        String sourceWhereStatement =
                String.format(
                        "%1$s.%2$s = ? "
                                + "AND %1$s.%3$s > ? "
                                + "AND %1$s.%3$s <= ? "
                                + "AND %1$s.%4$s > ? "
                                + "AND %1$s.%5$s = ?",
                        SourceContract.TABLE,
                        SourceContract.REGISTRATION_ORIGIN,
                        SourceContract.EVENT_TIME,
                        SourceContract.EXPIRY_TIME,
                        SourceContract.STATUS);
        String sourceOrderByStatement =
                String.format(" ORDER BY %1$s ASC", SourceContract.EVENT_TIME);
        String sourceLimitStatement = String.format(" LIMIT %1$s", 1);
        long maxDelayedSourceRegistrationWindow =
                FlagsFactory.getFlags().getMeasurementMaxDelayedSourceRegistrationWindow();

        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .rawQuery(
                                selectSourcesByDestination(
                                                triggerDestinationValue,
                                                trigger.getDestinationType(),
                                                sourceWhereStatement)
                                        + sourceOrderByStatement
                                        + sourceLimitStatement,
                                new String[] {
                                    trigger.getRegistrationOrigin().toString(),
                                    String.valueOf(trigger.getTriggerTime()),
                                    String.valueOf(
                                            trigger.getTriggerTime()
                                                    + maxDelayedSourceRegistrationWindow),
                                    String.valueOf(trigger.getTriggerTime()),
                                    String.valueOf(Source.Status.ACTIVE)
                                })) {
            if (cursor.moveToNext()) {
                return Optional.of(SqliteObjectMapper.constructSourceFromCursor(cursor));
            }
            return Optional.empty();
        }
    }

    @Override
    public void updateTriggerStatus(Collection<String> triggerIds, @Trigger.Status int status)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.TriggerContract.STATUS, status);
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .update(
                                MeasurementTables.TriggerContract.TABLE,
                                values,
                                MeasurementTables.TriggerContract.ID
                                        + " IN ("
                                        + Stream.generate(() -> "?")
                                                .limit(triggerIds.size())
                                                .collect(Collectors.joining(","))
                                        + ")",
                                triggerIds.toArray(new String[0]));
        if (rows != triggerIds.size()) {
            throw new DatastoreException("Trigger status update failed.");
        }
    }

    @Override
    public void updateSourceStatus(@NonNull Collection<String> sourceIds, @Source.Status int status)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(SourceContract.STATUS, status);
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .update(
                                SourceContract.TABLE,
                                values,
                                SourceContract.ID
                                        + " IN ("
                                        + Stream.generate(() -> "?")
                                                .limit(sourceIds.size())
                                                .collect(Collectors.joining(","))
                                        + ")",
                                sourceIds.toArray(new String[0]));
        if (rows != sourceIds.size()) {
            throw new DatastoreException("Source status update failed.");
        }
    }

    @Override
    public void updateSourceAttributedTriggers(
            @NonNull String sourceId, @Nullable String attributionStatus)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(SourceContract.EVENT_ATTRIBUTION_STATUS, attributionStatus);
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .update(
                                SourceContract.TABLE,
                                values,
                                SourceContract.ID + " = ?",
                                new String[] {sourceId});
        if (rows != 1) {
            throw new DatastoreException("Source  event attribution status update failed.");
        }
    }

    @Override
    public void updateSourceAggregateContributions(@NonNull Source source)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(SourceContract.AGGREGATE_CONTRIBUTIONS, source.getAggregateContributions());
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .update(
                                SourceContract.TABLE,
                                values,
                                SourceContract.ID + " = ?",
                                new String[] {source.getId()});
        if (rows != 1) {
            throw new DatastoreException("Source aggregate contributions update failed.");
        }
    }

    @Override
    public void markEventReportStatus(@NonNull String eventReportId, @EventReport.Status int status)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.EventReportContract.STATUS, status);
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .update(
                                MeasurementTables.EventReportContract.TABLE,
                                values,
                                MeasurementTables.EventReportContract.ID + " = ?",
                                new String[] {eventReportId});
        if (rows != 1) {
            throw new DatastoreException("EventReport status update failed.");
        }
    }

    @Override
    public void updateEventReportSummaryBucket(
            @NonNull String eventReportId, @NonNull String summaryBucket)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.EventReportContract.TRIGGER_SUMMARY_BUCKET, summaryBucket);
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .update(
                                MeasurementTables.EventReportContract.TABLE,
                                values,
                                MeasurementTables.EventReportContract.ID + " = ?",
                                new String[] {eventReportId});
        if (rows != 1) {
            throw new DatastoreException("EventReport summary bucket update failed.");
        }
    }

    @Override
    public void markAggregateReportStatus(
            String aggregateReportId, @AggregateReport.Status int status)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AggregateReport.STATUS, status);
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .update(
                                MeasurementTables.AggregateReport.TABLE,
                                values,
                                MeasurementTables.AggregateReport.ID + " = ? ",
                                new String[] {aggregateReportId});
        if (rows != 1) {
            throw new DatastoreException("AggregateReport update failed");
        }
    }

    @Override
    public void markEventDebugReportDelivered(String eventReportId) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(
                MeasurementTables.EventReportContract.DEBUG_REPORT_STATUS,
                EventReport.DebugReportStatus.DELIVERED);
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .update(
                                MeasurementTables.EventReportContract.TABLE,
                                values,
                                MeasurementTables.EventReportContract.ID + " = ?",
                                new String[] {eventReportId});
        if (rows != 1) {
            throw new DatastoreException("EventReport update failed.");
        }
    }

    @Override
    public void markAggregateDebugReportDelivered(String aggregateReportId)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(
                MeasurementTables.AggregateReport.DEBUG_REPORT_STATUS,
                AggregateReport.DebugReportStatus.DELIVERED);
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .update(
                                MeasurementTables.AggregateReport.TABLE,
                                values,
                                MeasurementTables.AggregateReport.ID + " = ? ",
                                new String[] {aggregateReportId});
        if (rows != 1) {
            throw new DatastoreException("AggregateReport update failed");
        }
    }

    @Override
    @Nullable
    public List<EventReport> getSourceEventReports(Source source) throws DatastoreException {
        List<EventReport> eventReports = new ArrayList<>();
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.EventReportContract.TABLE,
                                /* columns= */ null,
                                MeasurementTables.EventReportContract.SOURCE_ID + " = ? ",
                                new String[] {source.getId()},
                                /* groupBy= */ null,
                                /* having= */ null,
                                /* orderBy= */ null,
                                /* limit= */ null)) {
            while (cursor.moveToNext()) {
                eventReports.add(SqliteObjectMapper.constructEventReportFromCursor(cursor));
            }
            return eventReports;
        }
    }

    @Override
    public void deleteEventReportAndAttribution(EventReport eventReport) throws DatastoreException {
        long eventReportRows =
                mSQLTransaction
                        .getDatabase()
                        .delete(
                                MeasurementTables.EventReportContract.TABLE,
                                MeasurementTables.EventReportContract.ID + " = ?",
                                new String[] {eventReport.getId()});

        long attributionRows =
                mSQLTransaction
                        .getDatabase()
                        .delete(
                                MeasurementTables.AttributionContract.TABLE,
                                MeasurementTables.AttributionContract.SCOPE + " = ? "
                                + "AND " + MeasurementTables.AttributionContract.SOURCE_ID
                                        + " = ? "
                                + "AND " + MeasurementTables.AttributionContract.TRIGGER_ID
                                        + " = ?",
                                new String[] {
                                        String.valueOf(Attribution.Scope.EVENT),
                                        eventReport.getSourceId(),
                                        eventReport.getTriggerId()});

        if (eventReportRows != 1 || attributionRows != 1) {
            throw new DatastoreException("EventReport and/or Attribution deletion failed.");
        }
    }

    @Override
    public void deleteDebugReport(String debugReportId) throws DatastoreException {
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .delete(
                                MeasurementTables.DebugReportContract.TABLE,
                                MeasurementTables.DebugReportContract.ID + " = ?",
                                new String[] {debugReportId});
        if (rows != 1) {
            throw new DatastoreException("DebugReport deletion failed.");
        }
        LoggerFactory.getMeasurementLogger()
                .d("MeasurementDao: deleteDebugReport: row deleted: " + rows);
    }

    @Override
    public int deleteDebugReports(
            @NonNull Uri registrant, @NonNull Instant start, @NonNull Instant end)
            throws DatastoreException {
        Objects.requireNonNull(registrant);
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        validateRange(start, end);
        Instant cappedStart = capDeletionRange(start);
        Instant cappedEnd = capDeletionRange(end);
        Function<String, String> registrantMatcher = getRegistrantMatcher(registrant);
        Function<String, String> timeMatcher = getTimeMatcher(cappedStart, cappedEnd);

        final SQLiteDatabase db = mSQLTransaction.getDatabase();
        return db.delete(
                MeasurementTables.DebugReportContract.TABLE,
                mergeConditions(
                        " AND ",
                        registrantMatcher.apply(MeasurementTables.DebugReportContract.REGISTRANT),
                        timeMatcher.apply(MeasurementTables.DebugReportContract.INSERTION_TIME)),
                /* whereArgs*/ null);
    }

    @Override
    public List<String> getPendingEventReportIdsInWindow(long windowStartTime, long windowEndTime)
            throws DatastoreException {
        List<String> eventReports = new ArrayList<>();
        try (Cursor cursor =
                mReportingRetryLimitEnabledSupplier.get()
                        ? pendingEventReportIdsInWindowCursorWithRetryLimit(
                                windowStartTime, windowEndTime)
                        : pendingEventReportIdsInWindowCursor(windowStartTime, windowEndTime)) {
            while (cursor.moveToNext()) {
                eventReports.add(
                        cursor.getString(
                                cursor.getColumnIndex(MeasurementTables.EventReportContract.ID)));
            }
            return eventReports;
        }
    }

    @Override
    public List<String> getPendingDebugEventReportIds() throws DatastoreException {
        List<String> eventReports = new ArrayList<>();
        try (Cursor cursor =
                mReportingRetryLimitEnabledSupplier.get()
                        ? pendingDebugEventReportIdsLimitRetryCursor()
                        : pendingDebugEventReportIdsCursor()) {
            while (cursor.moveToNext()) {
                eventReports.add(
                        cursor.getString(
                                cursor.getColumnIndex(MeasurementTables.EventReportContract.ID)));
            }
            return eventReports;
        }
    }

    @Override
    public List<String> getPendingEventReportIdsForGivenApp(Uri appName) throws DatastoreException {
        List<String> eventReports = new ArrayList<>();
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .rawQuery(
                                String.format(
                                        Locale.ENGLISH,
                                        "SELECT e.%1$s FROM %2$s e "
                                                + "INNER JOIN %3$s s ON (e.%4$s = s.%5$s) "
                                                + "WHERE e.%6$s = ? AND s.%7$s = ?",
                                        MeasurementTables.EventReportContract.ID,
                                        MeasurementTables.EventReportContract.TABLE,
                                        SourceContract.TABLE,
                                        MeasurementTables.EventReportContract.SOURCE_ID,
                                        SourceContract.ID,
                                        MeasurementTables.EventReportContract.STATUS,
                                        SourceContract.REGISTRANT),
                                new String[] {
                                    String.valueOf(EventReport.Status.PENDING),
                                    String.valueOf(appName)
                                })) {
            while (cursor.moveToNext()) {
                eventReports.add(
                        cursor.getString(
                                cursor.getColumnIndex(MeasurementTables.EventReportContract.ID)));
            }
            return eventReports;
        }
    }

    @Override
    public void insertEventReport(EventReport eventReport) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.EventReportContract.ID, eventReport.getId());
        values.put(
                MeasurementTables.EventReportContract.SOURCE_EVENT_ID,
                eventReport.getSourceEventId().getValue());
        values.put(
                MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION,
                String.join(
                        " ",
                        eventReport.getAttributionDestinations().stream()
                                .map(Uri::toString)
                                .collect(Collectors.toList())));
        values.put(
                MeasurementTables.EventReportContract.TRIGGER_TIME, eventReport.getTriggerTime());
        values.put(
                MeasurementTables.EventReportContract.TRIGGER_DATA,
                getNullableUnsignedLong(eventReport.getTriggerData()));
        values.put(
                MeasurementTables.EventReportContract.TRIGGER_DEDUP_KEY,
                getNullableUnsignedLong(eventReport.getTriggerDedupKey()));
        values.put(
                MeasurementTables.EventReportContract.ENROLLMENT_ID, eventReport.getEnrollmentId());
        values.put(MeasurementTables.EventReportContract.STATUS, eventReport.getStatus());
        values.put(
                MeasurementTables.EventReportContract.DEBUG_REPORT_STATUS,
                eventReport.getDebugReportStatus());
        values.put(MeasurementTables.EventReportContract.REPORT_TIME, eventReport.getReportTime());
        values.put(
                MeasurementTables.EventReportContract.TRIGGER_PRIORITY,
                eventReport.getTriggerPriority());
        values.put(
                MeasurementTables.EventReportContract.SOURCE_TYPE,
                eventReport.getSourceType().toString());
        values.put(
                MeasurementTables.EventReportContract.RANDOMIZED_TRIGGER_RATE,
                eventReport.getRandomizedTriggerRate());
        values.put(
                MeasurementTables.EventReportContract.SOURCE_DEBUG_KEY,
                getNullableUnsignedLong(eventReport.getSourceDebugKey()));
        values.put(
                MeasurementTables.EventReportContract.TRIGGER_DEBUG_KEY,
                getNullableUnsignedLong(eventReport.getTriggerDebugKey()));
        values.put(
                MeasurementTables.EventReportContract.TRIGGER_DEBUG_KEYS,
                collectionToCommaSeparatedString(eventReport.getTriggerDebugKeys()));
        values.put(MeasurementTables.EventReportContract.SOURCE_ID, eventReport.getSourceId());
        values.put(MeasurementTables.EventReportContract.TRIGGER_ID, eventReport.getTriggerId());
        values.put(
                MeasurementTables.EventReportContract.REGISTRATION_ORIGIN,
                eventReport.getRegistrationOrigin().toString());
        values.put(
                MeasurementTables.EventReportContract.TRIGGER_SUMMARY_BUCKET,
                eventReport.getStringEncodedTriggerSummaryBucket());
        long rowId =
                mSQLTransaction
                        .getDatabase()
                        .insert(
                                MeasurementTables.EventReportContract.TABLE,
                                /* nullColumnHack= */ null,
                                values);
        if (rowId == -1) {
            throw new DatastoreException("EventReport insertion failed.");
        }
    }

    @Override
    public void updateSourceEventReportDedupKeys(@NonNull Source source) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(
                SourceContract.EVENT_REPORT_DEDUP_KEYS,
                collectionToCommaSeparatedString(source.getEventReportDedupKeys()));
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .update(
                                SourceContract.TABLE,
                                values,
                                SourceContract.ID + " = ?",
                                new String[] {source.getId()});
        if (rows != 1) {
            throw new DatastoreException("Source event report dedup key updated failed.");
        }
    }

    @Override
    public void updateSourceAggregateReportDedupKeys(@NonNull Source source)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(
                SourceContract.AGGREGATE_REPORT_DEDUP_KEYS,
                collectionToCommaSeparatedString(source.getAggregateReportDedupKeys()));
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .update(
                                SourceContract.TABLE,
                                values,
                                SourceContract.ID + " = ?",
                                new String[] {source.getId()});
        if (rows != 1) {
            throw new DatastoreException("Source aggregate report dedup key updated failed.");
        }
    }

    @Override
    public void insertAttribution(@NonNull Attribution attribution) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AttributionContract.ID, UUID.randomUUID().toString());
        values.put(MeasurementTables.AttributionContract.SCOPE, attribution.getScope());
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
                MeasurementTables.AttributionContract.ENROLLMENT_ID, attribution.getEnrollmentId());
        values.put(
                MeasurementTables.AttributionContract.TRIGGER_TIME, attribution.getTriggerTime());
        values.put(MeasurementTables.AttributionContract.REGISTRANT, attribution.getRegistrant());
        values.put(MeasurementTables.AttributionContract.SOURCE_ID, attribution.getSourceId());
        values.put(MeasurementTables.AttributionContract.TRIGGER_ID, attribution.getTriggerId());
        values.put(
                MeasurementTables.AttributionContract.REGISTRATION_ORIGIN,
                attribution.getRegistrationOrigin().toString());
        values.put(MeasurementTables.AttributionContract.REPORT_ID, attribution.getReportId());
        long rowId =
                mSQLTransaction
                        .getDatabase()
                        .insert(
                                MeasurementTables.AttributionContract.TABLE,
                                /* nullColumnHack= */ null,
                                values);
        if (rowId == -1) {
            throw new DatastoreException("Attribution insertion failed.");
        }
    }

    @Override
    public long getAttributionsPerRateLimitWindow(@Attribution.Scope int scope,
            @NonNull Source source, @NonNull Trigger trigger) throws DatastoreException {
        Optional<Uri> publisherBaseUri =
                extractBaseUri(source.getPublisher(), source.getPublisherType());
        Optional<Uri> destinationBaseUri =
                extractBaseUri(trigger.getAttributionDestination(), trigger.getDestinationType());

        if (publisherBaseUri.isEmpty() || destinationBaseUri.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format(
                            Locale.ENGLISH,
                            "getAttributionsPerRateLimitWindow:"
                                    + " getSourceAndDestinationTopPrivateDomains failed. Publisher:"
                                    + " %s; Attribution destination: %s",
                            source.getPublisher().toString(),
                            trigger.getAttributionDestination().toString()));
        }

        String publisherTopPrivateDomain = publisherBaseUri.get().toString();
        String triggerDestinationTopPrivateDomain = destinationBaseUri.get().toString();

        return DatabaseUtils.queryNumEntries(
                mSQLTransaction.getDatabase(),
                MeasurementTables.AttributionContract.TABLE,
                MeasurementTables.AttributionContract.SCOPE
                        + " = ? AND "
                        + MeasurementTables.AttributionContract.SOURCE_SITE
                        + " = ? AND "
                        + MeasurementTables.AttributionContract.DESTINATION_SITE
                        + " = ? AND "
                        + MeasurementTables.AttributionContract.ENROLLMENT_ID
                        + " = ? AND "
                        + MeasurementTables.AttributionContract.TRIGGER_TIME
                        + " > ? AND "
                        + MeasurementTables.AttributionContract.TRIGGER_TIME
                        + " <= ? ",
                new String[] {
                    String.valueOf(scope),
                    publisherTopPrivateDomain,
                    triggerDestinationTopPrivateDomain,
                    trigger.getEnrollmentId(),
                    String.valueOf(
                            trigger.getTriggerTime()
                                    - FlagsFactory.getFlags()
                                            .getMeasurementRateLimitWindowMilliseconds()),
                    String.valueOf(trigger.getTriggerTime())
                });
    }

    @Override
    public long getNumSourcesPerPublisher(Uri publisherUri, @EventSurfaceType int publisherType)
            throws DatastoreException {
        return DatabaseUtils.queryNumEntries(
                mSQLTransaction.getDatabase(),
                SourceContract.TABLE,
                mergeConditions(
                        " AND ",
                        SourceContract.PUBLISHER + " = ?",
                        SourceContract.STATUS + " != ?"),
                new String[] {
                    publisherUri.toString(), String.valueOf(Source.Status.MARKED_TO_DELETE)
                });
    }

    @Override
    public long getNumTriggersPerDestination(Uri destination, @EventSurfaceType int destinationType)
            throws DatastoreException {
        return DatabaseUtils.queryNumEntries(
                mSQLTransaction.getDatabase(),
                MeasurementTables.TriggerContract.TABLE,
                getDestinationWhereStatement(destination, destinationType));
    }

    @Override
    public List<Uri> getUninstalledAppNamesHavingMeasurementData(List<Uri> installedApps)
            throws DatastoreException {
        final List<Uri> uninstallAppNames = new ArrayList<>();

        final String installedAppsFormatted = flattenAsSqlQueryList(installedApps);
        final String query =
                String.format(
                        Locale.ENGLISH,
                        "SELECT %2$s FROM %1$s WHERE %2$s NOT IN %7$s "
                                + "UNION "
                                + "SELECT %4$s FROM %3$s WHERE %4$s NOT IN %7$s "
                                + "UNION "
                                + "SELECT %6$s FROM %5$s WHERE %6$s NOT IN %7$s",
                        SourceContract.TABLE,
                        SourceContract.REGISTRANT,
                        MeasurementTables.TriggerContract.TABLE,
                        MeasurementTables.TriggerContract.REGISTRANT,
                        MeasurementTables.AsyncRegistrationContract.TABLE,
                        MeasurementTables.AsyncRegistrationContract.REGISTRANT,
                        installedAppsFormatted);
        try (Cursor cursor =
                mSQLTransaction.getDatabase().rawQuery(query, /* selectionArgs= */ null)) {
            while (cursor.moveToNext()) {
                uninstallAppNames.add(
                        Uri.parse(
                                cursor.getString(
                                        cursor.getColumnIndexOrThrow(SourceContract.REGISTRANT))));
            }
        }
        return uninstallAppNames;
    }

    @Override
    public Long getLatestReportTimeInBatchWindow(long batchWindow) throws DatastoreException {
        final String cte =
                String.format(
                        Locale.ENGLISH,
                        "SELECT %2$s AS report_time "
                                + "FROM %1$s "
                                + "WHERE %3$s = %4$s "
                                + "UNION "
                                + "SELECT %6$s AS report_time "
                                + "FROM %5$s "
                                + "WHERE %7$s = %8$s",
                        MeasurementTables.AggregateReport.TABLE,
                        MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME,
                        MeasurementTables.AggregateReport.STATUS,
                        AggregateReport.Status.PENDING,
                        MeasurementTables.EventReportContract.TABLE,
                        MeasurementTables.EventReportContract.REPORT_TIME,
                        MeasurementTables.EventReportContract.STATUS,
                        EventReport.Status.PENDING);

        final String query =
                String.format(
                        Locale.ENGLISH,
                        "WITH t AS (%1$s) SELECT MAX(t.report_time) FROM t WHERE t.report_time <="
                                + " (SELECT MIN(t.report_time) FROM t) + %2$s",
                        cte,
                        batchWindow);

        try (Cursor cursor =
                mSQLTransaction.getDatabase().rawQuery(query, /* selectionArgs= */ null)) {
            if (cursor != null && cursor.moveToNext()) {
                long timestamp = cursor.getLong(0);
                if (timestamp == 0) {
                    return null;
                }
                return timestamp;
            }

            return null;
        }
    }

    @Override
    public Integer countDistinctReportingOriginsPerPublisherXDestInAttribution(
            Uri sourceSite,
            Uri destinationSite,
            Uri excludedReportingOrigin,
            long windowStartTime,
            long windowEndTime)
            throws DatastoreException {
        String query =
                String.format(
                        Locale.ENGLISH,
                        "SELECT COUNT(DISTINCT %1$s) FROM %2$s "
                                + "WHERE %3$s = ? AND %4$s = ? AND %1s != ? "
                                + "AND %5$s > ? AND %5$s <= ?",
                        MeasurementTables.AttributionContract.REGISTRATION_ORIGIN,
                        MeasurementTables.AttributionContract.TABLE,
                        MeasurementTables.AttributionContract.SOURCE_SITE,
                        MeasurementTables.AttributionContract.DESTINATION_SITE,
                        MeasurementTables.AttributionContract.TRIGGER_TIME);
        return (int)
                DatabaseUtils.longForQuery(
                        mSQLTransaction.getDatabase(),
                        query,
                        new String[] {
                            sourceSite.toString(),
                            destinationSite.toString(),
                            excludedReportingOrigin.toString(),
                            String.valueOf(windowStartTime),
                            String.valueOf(windowEndTime)
                        });
    }

    @Override
    public Integer countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
            Uri publisher,
            @EventSurfaceType int publisherType,
            String enrollmentId,
            List<Uri> excludedDestinations,
            @EventSurfaceType int destinationType,
            long windowStartTime,
            long windowEndTime)
            throws DatastoreException {
        String query =
                String.format(
                        Locale.ENGLISH,
                        "WITH source_ids AS ("
                                + "SELECT %1$s FROM %2$s "
                                + "WHERE %3$s AND %4$s = ? "
                                + "AND %5$s > ? AND %5$s <= ? AND %6$s > ?"
                                + ") "
                                + "SELECT COUNT(DISTINCT %7$s) FROM %8$s "
                                + "WHERE %9$s IN source_ids AND %10$s = ? "
                                + "AND %7$s NOT IN "
                                + flattenAsSqlQueryList(excludedDestinations),
                        SourceContract.ID,
                        SourceContract.TABLE,
                        getPublisherWhereStatement(publisher, publisherType),
                        SourceContract.ENROLLMENT_ID,
                        SourceContract.EVENT_TIME,
                        SourceContract.EXPIRY_TIME,
                        SourceDestination.DESTINATION,
                        SourceDestination.TABLE,
                        SourceDestination.SOURCE_ID,
                        SourceDestination.DESTINATION_TYPE);
        return (int)
                DatabaseUtils.longForQuery(
                        mSQLTransaction.getDatabase(),
                        query,
                        new String[] {
                            enrollmentId,
                            String.valueOf(windowStartTime),
                            String.valueOf(windowEndTime),
                            String.valueOf(windowEndTime),
                            String.valueOf(destinationType)
                        });
    }

    @Override
    public Integer countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
            Uri publisher,
            @EventSurfaceType int publisherType,
            String enrollmentId,
            List<Uri> excludedDestinations,
            @EventSurfaceType int destinationType,
            long windowEndTime)
            throws DatastoreException {
        String query =
                String.format(
                        Locale.ENGLISH,
                        "WITH source_ids AS ("
                                + "SELECT %1$s FROM %2$s "
                                + "WHERE %3$s AND %4$s = ? "
                                + "AND %5$s > ? "
                                + "AND %6$s != ?"
                                + ") "
                                + "SELECT COUNT(DISTINCT %7$s) FROM %8$s "
                                + "WHERE %9$s IN source_ids AND %10$s = ? "
                                + "AND %7$s NOT IN "
                                + flattenAsSqlQueryList(excludedDestinations),
                        SourceContract.ID, // 1
                        SourceContract.TABLE, // 2
                        getPublisherWhereStatement(publisher, publisherType), // 3
                        SourceContract.ENROLLMENT_ID, // 4
                        SourceContract.EXPIRY_TIME, // 5
                        SourceContract.STATUS, // 6
                        SourceDestination.DESTINATION, // 7
                        SourceDestination.TABLE, // 8
                        SourceDestination.SOURCE_ID, // 9
                        SourceDestination.DESTINATION_TYPE); // 10
        return (int)
                DatabaseUtils.longForQuery(
                        mSQLTransaction.getDatabase(),
                        query,
                        new String[] {
                            enrollmentId,
                            String.valueOf(windowEndTime),
                            String.valueOf(Source.Status.MARKED_TO_DELETE),
                            String.valueOf(destinationType)
                        });
    }

    @Override
    public Integer countDistinctDestinationsPerPublisherPerRateLimitWindow(
            Uri publisher,
            @EventSurfaceType int publisherType,
            List<Uri> excludedDestinations,
            @EventSurfaceType int destinationType,
            long windowStartTime,
            long windowEndTime)
            throws DatastoreException {
        String query =
                String.format(
                        Locale.ENGLISH,
                        "WITH source_ids AS ("
                                + "SELECT %1$s FROM %2$s "
                                + "WHERE %3$s "
                                + "AND %4$s > ? "
                                + "AND %4$s <= ? "
                                + "AND %5$s > ?"
                                + ") "
                                + "SELECT COUNT(DISTINCT %6$s) FROM %7$s "
                                + "WHERE %8$s IN source_ids AND %9$s = ? "
                                + "AND %6$s NOT IN "
                                + flattenAsSqlQueryList(excludedDestinations),
                        SourceContract.ID,
                        SourceContract.TABLE,
                        getPublisherWhereStatement(publisher, publisherType),
                        SourceContract.EVENT_TIME,
                        SourceContract.EXPIRY_TIME,
                        SourceDestination.DESTINATION,
                        SourceDestination.TABLE,
                        SourceDestination.SOURCE_ID,
                        SourceDestination.DESTINATION_TYPE);
        return (int)
                DatabaseUtils.longForQuery(
                        mSQLTransaction.getDatabase(),
                        query,
                        new String[] {
                            String.valueOf(windowStartTime),
                            String.valueOf(windowEndTime),
                            String.valueOf(windowEndTime),
                            String.valueOf(destinationType)
                        });
    }

    @Override
    public Integer countSourcesPerPublisherXEnrollmentExcludingRegOrigin(
            Uri registrationOrigin,
            Uri publisher,
            @EventSurfaceType int publisherType,
            String enrollmentId,
            long eventTime,
            long timePeriodInMs)
            throws DatastoreException {

        String query =
                String.format(
                        Locale.ENGLISH,
                        "SELECT COUNT (*) FROM %1$s "
                                + "WHERE %2$s AND "
                                + "%3$s = ? AND "
                                + "%4$s != ? AND "
                                + "%5$s > ?",
                        SourceContract.TABLE,
                        getPublisherWhereStatement(publisher, publisherType),
                        SourceContract.ENROLLMENT_ID,
                        SourceContract.REGISTRATION_ORIGIN,
                        SourceContract.EVENT_TIME);

        return (int)
                DatabaseUtils.longForQuery(
                        mSQLTransaction.getDatabase(),
                        query,
                        new String[] {
                            enrollmentId,
                            registrationOrigin.toString(),
                            String.valueOf(eventTime - timePeriodInMs)
                        });
    }

    @Override
    public Integer countDistinctReportingOriginsPerPublisherXDestinationInSource(
            Uri publisher,
            @EventSurfaceType int publisherType,
            List<Uri> destinations,
            Uri excludedReportingOrigin,
            long windowStartTime,
            long windowEndTime)
            throws DatastoreException {
        // Each destination can be paired with the given publisher. Return the maximum count of
        // distinct reporting origins among the pairs of destination-and-publisher.
        String query =
                String.format(
                        Locale.ENGLISH,
                        "WITH joined as ("
                                + "SELECT source."
                                + SourceContract.REGISTRATION_ORIGIN
                                + ", "
                                + "source_dest."
                                + SourceDestination.DESTINATION
                                + " "
                                + "FROM "
                                + SourceContract.TABLE
                                + " source "
                                + "INNER JOIN "
                                + SourceDestination.TABLE
                                + " source_dest"
                                + " ON (source."
                                + SourceContract.ID
                                + " = "
                                + "source_dest."
                                + SourceDestination.SOURCE_ID
                                + ") "
                                + "WHERE %1$s "
                                + "AND source."
                                + SourceContract.REGISTRATION_ORIGIN
                                + " != ? "
                                + "AND source."
                                + SourceContract.EVENT_TIME
                                + " > ? "
                                + "AND source."
                                + SourceContract.EVENT_TIME
                                + " <= ? "
                                + "AND source_dest."
                                + SourceDestination.DESTINATION
                                + " IN %2$s), distinct_registration_origins as (SELECT DENSE_RANK()"
                                + " OVER (PARTITION BY destination ORDER BY registration_origin) AS"
                                + " distinct_registration_origin FROM joined) SELECT"
                                + " MAX(distinct_registration_origin) FROM"
                                + " distinct_registration_origins",
                        getPublisherWhereStatement(publisher, publisherType),
                        flattenAsSqlQueryList(destinations));

        return (int)
                DatabaseUtils.longForQuery(
                        mSQLTransaction.getDatabase(),
                        query,
                        new String[] {
                            excludedReportingOrigin.toString(),
                            String.valueOf(windowStartTime),
                            String.valueOf(windowEndTime)
                        });
    }

    @Override
    public List<AggregateReport> fetchMatchingAggregateReports(
            @NonNull Collection<String> sourceIds, @NonNull Collection<String> triggerIds)
            throws DatastoreException {
        return fetchRecordsMatchingWithParameters(
                MeasurementTables.AggregateReport.TABLE,
                MeasurementTables.AggregateReport.SOURCE_ID,
                sourceIds,
                MeasurementTables.AggregateReport.TRIGGER_ID,
                triggerIds,
                SqliteObjectMapper::constructAggregateReport);
    }

    @Override
    public List<EventReport> fetchMatchingEventReports(
            @NonNull Collection<String> sourceIds, @NonNull Collection<String> triggerIds)
            throws DatastoreException {
        return fetchRecordsMatchingWithParameters(
                MeasurementTables.EventReportContract.TABLE,
                MeasurementTables.EventReportContract.SOURCE_ID,
                sourceIds,
                MeasurementTables.EventReportContract.TRIGGER_ID,
                triggerIds,
                SqliteObjectMapper::constructEventReportFromCursor);
    }

    @Override
    public Set<String> fetchFlexSourceIdsFor(@NonNull Collection<String> triggerIds)
            throws DatastoreException {
        Set<String> sourceIds = new HashSet<>();
        if (triggerIds.isEmpty()) {
            return new HashSet<>();
        }

        // Reduce the number of trigger IDs to query to a set and convert to a list for batching.
        List<String> triggerIdsList = new ArrayList<>(new HashSet<>(triggerIds));

        // SQlite limits the number of compound SELECT statements so we need to process them in
        // batches.
        int i = 0;
        while (i < triggerIdsList.size()) {
            int sublistEnd = Math.min(i + MAX_COMPOUND_SELECT, triggerIdsList.size());

            List<String> queries =
                    triggerIdsList.subList(i, sublistEnd).stream()
                            .map(
                                    triggerId ->
                                            "SELECT * FROM "
                                                    + SourceContract.TABLE
                                                    + " WHERE "
                                                    + SourceContract.TRIGGER_SPECS
                                                    + " IS NOT NULL"
                                                    + " AND "
                                                    + SourceContract.EVENT_ATTRIBUTION_STATUS
                                                    + " LIKE '%\""
                                                    + triggerId
                                                    + "\"%'")
                            .collect(Collectors.toList());

            String unionQuery = String.join(" UNION ", queries);

            try (Cursor cursor =
                    mSQLTransaction
                            .getDatabase()
                            .rawQuery(
                                    String.format(
                                            Locale.ENGLISH,
                                            "SELECT DISTINCT %s FROM (%s)",
                                            SourceContract.ID,
                                            unionQuery),
                                    null)) {
                while (cursor.moveToNext()) {
                    String sourceId =
                            cursor.getString(cursor.getColumnIndexOrThrow(SourceContract.ID));
                    sourceIds.add(sourceId);
                }
            }
            i += MAX_COMPOUND_SELECT;
        }
        return sourceIds;
    }

    @Override
    public void deleteFlexEventReportsAndAttributions(List<EventReport> eventReports)
            throws DatastoreException {
        if (eventReports.isEmpty()) {
            return;
        }

        String sourceId = eventReports.get(0).getSourceId();

        Map<String, List<String>> triggerIdToEventReportIdsMap =
                eventReports.stream()
                        .collect(
                                Collectors.groupingBy(
                                        EventReport::getTriggerId,
                                        Collectors.mapping(
                                                EventReport::getId, Collectors.toList())));

        SQLiteDatabase db = mSQLTransaction.getDatabase();

        for (Map.Entry<String, List<String>> entry : triggerIdToEventReportIdsMap.entrySet()) {
            String triggerId = entry.getKey();
            List<String> eventReportIds = entry.getValue();

            String eventReportsWhereStatement =
                    MeasurementTables.EventReportContract.ID
                            + " IN ('"
                            + String.join("','", eventReportIds)
                            + "')";

            db.delete(
                    MeasurementTables.EventReportContract.TABLE,
                    eventReportsWhereStatement,
                    new String[0]);

            // There can be multiple attributions per source ID / trigger ID matching pair, so we
            // limit the number of attributions deleted to the number of event reports provided that
            // were associated with the trigger ID.
            String attributionWhereStatement =
                    MeasurementTables.AttributionContract.ID
                            + " IN ("
                            + "SELECT "
                            + MeasurementTables.AttributionContract.ID
                            + " FROM "
                            + MeasurementTables.AttributionContract.TABLE
                            + " WHERE "
                            + MeasurementTables.AttributionContract.SCOPE
                            + " = ?"
                            + " AND "
                            + MeasurementTables.AttributionContract.SOURCE_ID
                            + " = ?"
                            + " AND "
                            + MeasurementTables.AttributionContract.TRIGGER_ID
                            + " = ?"
                            + " LIMIT "
                            + String.valueOf(eventReportIds.size())
                            + ")";

            db.delete(
                    MeasurementTables.AttributionContract.TABLE,
                    attributionWhereStatement,
                    new String[] {String.valueOf(Attribution.Scope.EVENT), sourceId, triggerId});
        }
    }

    private String flattenAsSqlQueryList(List<?> valueList) {
        // Construct query, as list of all packages present on the device
        return "("
                + valueList.stream()
                        .map((value) -> DatabaseUtils.sqlEscapeString(value.toString()))
                        .collect(Collectors.joining(", "))
                + ")";
    }

    @Override
    public void deleteExpiredRecords(
            long earliestValidInsertion,
            int registrationRetryLimit,
            @Nullable Long earliestValidAppReportInsertion)
            throws DatastoreException {
        SQLiteDatabase db = mSQLTransaction.getDatabase();
        String earliestValidInsertionStr = String.valueOf(earliestValidInsertion);
        // Deleting the sources and triggers will take care of deleting records from
        // event report, aggregate report and attribution tables as well. No explicit deletion is
        // required for them. Although, having proactive deletion of expired records help clean up
        // space.
        // Source table
        db.delete(
                SourceContract.TABLE,
                SourceContract.EVENT_TIME + " < ?",
                new String[] {earliestValidInsertionStr});
        // Trigger table
        db.delete(
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.TRIGGER_TIME + " < ?",
                new String[] {earliestValidInsertionStr});
        // Event Reports
        // TODO(b/277362712): Optimize Deletion of event reports
        // We rely on source/trigger deletion to delete event reports because delivered event
        // reports are used for calculating total event reports for a Source during Attribution.

        // AggregateReport table
        db.delete(
                MeasurementTables.AggregateReport.TABLE,
                MeasurementTables.AggregateReport.STATUS
                        + " = ? OR "
                        + MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME
                        + " < ?",
                new String[] {
                    String.valueOf(AggregateReport.Status.DELIVERED), earliestValidInsertionStr
                });
        // Attribution table
        db.delete(
                MeasurementTables.AttributionContract.TABLE,
                MeasurementTables.AttributionContract.TRIGGER_TIME + " < ?",
                new String[] {earliestValidInsertionStr});
        // Async Registration table
        db.delete(
                MeasurementTables.AsyncRegistrationContract.TABLE,
                MeasurementTables.AsyncRegistrationContract.REQUEST_TIME + " < ? OR "
                        + MeasurementTables.AsyncRegistrationContract.RETRY_COUNT + " >= ? ",
                new String[] {earliestValidInsertionStr, String.valueOf(registrationRetryLimit)});

        // Cleanup unnecessary Registration Redirect Counts
        String subQuery =
                "SELECT "
                        + "DISTINCT("
                        + MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID
                        + ")"
                        + " FROM "
                        + MeasurementTables.AsyncRegistrationContract.TABLE;
        db.delete(
                MeasurementTables.KeyValueDataContract.TABLE,
                MeasurementTables.KeyValueDataContract.DATA_TYPE
                        + " = ? "
                        + " AND "
                        + MeasurementTables.KeyValueDataContract.KEY
                        + " NOT IN "
                        + "("
                        + subQuery
                        + ")",
                new String[] {KeyValueData.DataType.REGISTRATION_REDIRECT_COUNT.toString()});

        // When Limiting Retries, consider Verbose Debug Reports Expired when Exceeds Limit.
        if (mReportingRetryLimitEnabledSupplier.get()) {
            db.delete(
                    MeasurementTables.DebugReportContract.TABLE,
                    MeasurementTables.DebugReportContract.ID
                            + " IN ("
                            + "SELECT "
                            + MeasurementTables.DebugReportContract.ID
                            + " FROM "
                            + MeasurementTables.DebugReportContract.TABLE
                            + " LEFT JOIN "
                            + MeasurementTables.KeyValueDataContract.TABLE
                            + " ON ("
                            + MeasurementTables.DebugReportContract.ID
                            + " = "
                            + MeasurementTables.KeyValueDataContract.KEY
                            + ") "
                            + "WHERE CAST("
                            + MeasurementTables.KeyValueDataContract.VALUE
                            + " AS INTEGER) >= ? "
                            + "AND "
                            + MeasurementTables.KeyValueDataContract.DATA_TYPE
                            + " = ? "
                            + ")",
                    new String[] {
                        mReportingRetryLimitSupplier.get().toString(),
                        DataType.DEBUG_REPORT_RETRY_COUNT.toString()
                    });
        } else {
            db.delete(
                    MeasurementTables.DebugReportContract.TABLE,
                    MeasurementTables.DebugReportContract.INSERTION_TIME + " < ?",
                    new String[] {earliestValidInsertionStr});
        }

        // Cleanup unnecessary AggregateReport Retry Counts
        subQuery =
                "SELECT "
                        + MeasurementTables.AggregateReport.ID
                        + " FROM "
                        + MeasurementTables.AggregateReport.TABLE;
        db.delete(
                MeasurementTables.KeyValueDataContract.TABLE,
                MeasurementTables.KeyValueDataContract.DATA_TYPE
                        + " IN (\""
                        + DataType.AGGREGATE_REPORT_RETRY_COUNT
                        + "\", \""
                        + DataType.DEBUG_AGGREGATE_REPORT_RETRY_COUNT
                        + "\")"
                        + " AND "
                        + MeasurementTables.KeyValueDataContract.KEY
                        + " NOT IN "
                        + "("
                        + subQuery
                        + ")",
                new String[] {});
        // Cleanup unnecessary DebugReport Retry Counts
        subQuery =
                "SELECT "
                        + MeasurementTables.DebugReportContract.ID
                        + " FROM "
                        + MeasurementTables.DebugReportContract.TABLE;
        db.delete(
                MeasurementTables.KeyValueDataContract.TABLE,
                MeasurementTables.KeyValueDataContract.DATA_TYPE
                        + " = ? "
                        + " AND "
                        + MeasurementTables.KeyValueDataContract.KEY
                        + " NOT IN "
                        + "("
                        + subQuery
                        + ")",
                new String[] {DataType.DEBUG_REPORT_RETRY_COUNT.toString()});
        // Cleanup unnecessary EventReport Retry Counts
        subQuery =
                "SELECT "
                        + MeasurementTables.EventReportContract.ID
                        + " FROM "
                        + MeasurementTables.EventReportContract.TABLE;
        db.delete(
                MeasurementTables.KeyValueDataContract.TABLE,
                MeasurementTables.KeyValueDataContract.DATA_TYPE
                        + " IN (\""
                        + DataType.EVENT_REPORT_RETRY_COUNT
                        + "\", \""
                        + DataType.DEBUG_EVENT_REPORT_RETRY_COUNT
                        + "\")"
                        + " AND "
                        + MeasurementTables.KeyValueDataContract.KEY
                        + " NOT IN "
                        + "("
                        + subQuery
                        + ")",
                new String[] {});
        if (earliestValidAppReportInsertion != null) {
            db.delete(
                    MeasurementTables.AppReportHistoryContract.TABLE,
                    MeasurementTables.AppReportHistoryContract.LAST_REPORT_DELIVERED_TIME + " < ?",
                    new String[] {String.valueOf(earliestValidAppReportInsertion)});
        }
    }

    @Override
    public List<String> fetchMatchingSources(
            @NonNull Uri registrant,
            @NonNull Instant start,
            @NonNull Instant end,
            @NonNull List<Uri> origins,
            @NonNull List<Uri> domains,
            // TODO: change this to selection and invert selection mode
            @DeletionRequest.MatchBehavior int matchBehavior)
            throws DatastoreException {
        Objects.requireNonNull(registrant);
        Objects.requireNonNull(origins);
        Objects.requireNonNull(domains);
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        validateRange(start, end);
        Instant cappedStart = capDeletionRange(start);
        Instant cappedEnd = capDeletionRange(end);
        Function<String, String> registrantMatcher = getRegistrantMatcher(registrant);
        Function<String, String> siteMatcher = getSiteMatcher(origins, domains, matchBehavior);
        Function<String, String> timeMatcher = getTimeMatcher(cappedStart, cappedEnd);

        final SQLiteDatabase db = mSQLTransaction.getDatabase();
        ImmutableList.Builder<String> sourceIds = new ImmutableList.Builder<>();
        try (Cursor cursor =
                db.query(
                        SourceContract.TABLE,
                        new String[] {SourceContract.ID},
                        mergeConditions(
                                " AND ",
                                registrantMatcher.apply(SourceContract.REGISTRANT),
                                siteMatcher.apply(SourceContract.PUBLISHER),
                                timeMatcher.apply(SourceContract.EVENT_TIME)),
                        null,
                        null,
                        null,
                        null)) {
            while (cursor.moveToNext()) {
                sourceIds.add(cursor.getString(0));
            }
        }

        return sourceIds.build();
    }

    @Override
    public Set<String> fetchMatchingTriggers(
            @NonNull Uri registrant,
            @NonNull Instant start,
            @NonNull Instant end,
            @NonNull List<Uri> origins,
            @NonNull List<Uri> domains,
            // TODO: change this to selection and invert selection mode
            @DeletionRequest.MatchBehavior int matchBehavior)
            throws DatastoreException {
        Objects.requireNonNull(registrant);
        Objects.requireNonNull(origins);
        Objects.requireNonNull(domains);
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        validateRange(start, end);
        Instant cappedStart = capDeletionRange(start);
        Instant cappedEnd = capDeletionRange(end);
        Function<String, String> registrantMatcher = getRegistrantMatcher(registrant);
        Function<String, String> siteMatcher = getSiteMatcher(origins, domains, matchBehavior);
        Function<String, String> timeMatcher = getTimeMatcher(cappedStart, cappedEnd);

        final SQLiteDatabase db = mSQLTransaction.getDatabase();
        // Mutable set, as the caller may add trigger IDs from flex sources.
        Set<String> triggerIds = new HashSet<>();
        try (Cursor cursor =
                db.query(
                        MeasurementTables.TriggerContract.TABLE,
                        new String[] {MeasurementTables.TriggerContract.ID},
                        mergeConditions(
                                " AND ",
                                registrantMatcher.apply(
                                        MeasurementTables.TriggerContract.REGISTRANT),
                                siteMatcher.apply(
                                        MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION),
                                timeMatcher.apply(MeasurementTables.TriggerContract.TRIGGER_TIME)),
                        null,
                        null,
                        null,
                        null)) {
            while (cursor.moveToNext()) {
                triggerIds.add(cursor.getString(0));
            }
        }

        return triggerIds;
    }

    @Override
    public List<String> fetchMatchingAsyncRegistrations(
            @NonNull Uri registrant,
            @NonNull Instant start,
            @NonNull Instant end,
            @NonNull List<Uri> origins,
            @NonNull List<Uri> domains,
            // TODO: change this to selection and invert selection mode
            @DeletionRequest.MatchBehavior int matchBehavior)
            throws DatastoreException {
        Objects.requireNonNull(registrant);
        Objects.requireNonNull(origins);
        Objects.requireNonNull(domains);
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        validateRange(start, end);
        Instant cappedStart = capDeletionRange(start);
        Instant cappedEnd = capDeletionRange(end);
        Function<String, String> registrantMatcher = getRegistrantMatcher(registrant);
        Function<String, String> siteMatcher = getSiteMatcher(origins, domains, matchBehavior);
        Function<String, String> timeMatcher = getTimeMatcher(cappedStart, cappedEnd);

        final SQLiteDatabase db = mSQLTransaction.getDatabase();
        ImmutableList.Builder<String> asyncRegistrationIds = new ImmutableList.Builder<>();
        try (Cursor cursor =
                db.query(
                        MeasurementTables.AsyncRegistrationContract.TABLE,
                        new String[] {MeasurementTables.AsyncRegistrationContract.ID},
                        mergeConditions(
                                " AND ",
                                registrantMatcher.apply(
                                        MeasurementTables.AsyncRegistrationContract.REGISTRANT),
                                siteMatcher.apply(
                                        MeasurementTables.AsyncRegistrationContract.TOP_ORIGIN),
                                timeMatcher.apply(
                                        MeasurementTables.AsyncRegistrationContract.REQUEST_TIME)),
                        null,
                        null,
                        null,
                        null)) {
            while (cursor.moveToNext()) {
                asyncRegistrationIds.add(cursor.getString(0));
            }
        }

        return asyncRegistrationIds.build();
    }

    private static Function<String, String> getRegistrantMatcher(Uri registrant) {
        return (String columnName) ->
                columnName + " = " + DatabaseUtils.sqlEscapeString(registrant.toString());
    }

    private String collectionToCommaSeparatedString(Collection<UnsignedLong> collection) {
        return collection.stream().map(UnsignedLong::toString).collect(Collectors.joining(","));
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

        return (String columnName) -> {
            if (origins.isEmpty() && domains.isEmpty()) {
                if (matchBehavior == DeletionRequest.MATCH_BEHAVIOR_PRESERVE) {
                    // MATCH EVERYTHING
                    return "";
                } else {
                    // MATCH NOTHING
                    return columnName + " IN ()";
                }
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
                                .map((o) -> DatabaseUtils.sqlEscapeString(o.toString()))
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
                                                        + DatabaseUtils.sqlEscapeString(
                                                                uri.getScheme()
                                                                        + "://%."
                                                                        + uri.getAuthority())
                                                        + concatOperator
                                                        + columnName
                                                        + equalityOperator
                                                        + DatabaseUtils.sqlEscapeString(
                                                                uri.toString())
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

    private static String mergeConditions(String operator, String... matcherStrings) {
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

        String sourceIdSubQuery =
                selectSourceIdsByDestination(uri.toString(), EventSurfaceType.APP);

        String whereString =
                String.format(
                        Locale.ENGLISH,
                        mergeConditions(
                                " AND ",
                                SourceContract.ID + " IN source_ids",
                                SourceContract.EVENT_TIME + " <= %1$d",
                                SourceContract.EXPIRY_TIME + " > %1$d",
                                SourceContract.INSTALL_COOLDOWN_WINDOW + " > 0",
                                SourceContract.EVENT_TIME
                                        + " + "
                                        + SourceContract.INSTALL_ATTRIBUTION_WINDOW
                                        + " >= %1$d",
                                SourceContract.STATUS + " = %2$d"),
                        eventTimestamp,
                        Source.Status.ACTIVE);

        // Will generate the records that we are interested in
        String filterQuery =
                String.format(
                        Locale.ENGLISH,
                        " ( WITH source_ids AS (%1$s) SELECT * from %2$s WHERE %3$s )",
                        sourceIdSubQuery,
                        SourceContract.TABLE,
                        whereString);

        Flags flags = FlagsFactory.getFlags();
        if (flags.getMeasurementEnableReinstallReattribution()) {
            String reinstallSubQuery =
                    getReinstallRegistrationOriginsQuery(uri, eventTimestamp, whereString);

            // Filter out sources where registration origin has reinstall signal
            String reinstallWhereString =
                    String.format(
                            Locale.ENGLISH,
                            mergeConditions(
                                    " AND ",
                                    whereString,
                                    SourceContract.TABLE
                                            + "."
                                            + SourceContract.REGISTRATION_ORIGIN
                                            + " NOT IN (%1$s)"),
                            reinstallSubQuery);

            filterQuery =
                    String.format(
                            Locale.ENGLISH,
                            " ( WITH source_ids AS (%1$s) SELECT * from %2$s WHERE %3$s )",
                            sourceIdSubQuery,
                            SourceContract.TABLE,
                            reinstallWhereString);
        }

        // The inner query picks the top record based on priority and recency order after applying
        // the filter. But first_value generates one value per partition but applies to all the rows
        // that input has, so we have to nest it with distinct in order to get unique source_ids.
        String sourceIdsProjection =
                String.format(
                        Locale.ENGLISH,
                        "SELECT DISTINCT(first_source_id) from "
                                + "(SELECT first_value(%1$s) "
                                + "OVER (PARTITION BY %2$s ORDER BY %3$s DESC, %4$s DESC) "
                                + "first_source_id FROM %5$s)",
                        SourceContract.ID,
                        SourceContract.REGISTRATION_ORIGIN,
                        SourceContract.PRIORITY,
                        SourceContract.EVENT_TIME,
                        filterQuery);

        ContentValues values = new ContentValues();
        values.put(SourceContract.IS_INSTALL_ATTRIBUTED, true);
        values.put(SourceContract.INSTALL_TIME, eventTimestamp);
        db.update(
                SourceContract.TABLE,
                values,
                SourceContract.ID + " IN (" + sourceIdsProjection + ")",
                null);
    }

    @Override
    public void undoInstallAttribution(Uri uri) throws DatastoreException {
        SQLiteDatabase db = mSQLTransaction.getDatabase();
        ContentValues values = new ContentValues();
        values.put(SourceContract.IS_INSTALL_ATTRIBUTED, false);
        values.putNull(SourceContract.INSTALL_TIME);
        db.update(
                SourceContract.TABLE,
                values,
                SourceContract.ID
                        + " IN ("
                        + selectSourceIdsByDestination(uri.toString(), EventSurfaceType.APP)
                        + ")",
                new String[] {});
    }

    @Override
    public void insertAggregateEncryptionKey(AggregateEncryptionKey aggregateEncryptionKey)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AggregateEncryptionKey.ID, UUID.randomUUID().toString());
        values.put(
                MeasurementTables.AggregateEncryptionKey.KEY_ID, aggregateEncryptionKey.getKeyId());
        values.put(
                MeasurementTables.AggregateEncryptionKey.PUBLIC_KEY,
                aggregateEncryptionKey.getPublicKey());
        values.put(
                MeasurementTables.AggregateEncryptionKey.EXPIRY,
                aggregateEncryptionKey.getExpiry());
        values.put(
                MeasurementTables.AggregateEncryptionKey.AGGREGATION_COORDINATOR_ORIGIN,
                aggregateEncryptionKey.getAggregationCoordinatorOrigin().toString());
        long rowId =
                mSQLTransaction
                        .getDatabase()
                        .insert(
                                MeasurementTables.AggregateEncryptionKey.TABLE,
                                /* nullColumnHack= */ null,
                                values);
        if (rowId == -1) {
            throw new DatastoreException("Aggregate encryption key insertion failed.");
        }
    }

    @Override
    public List<AggregateEncryptionKey> getNonExpiredAggregateEncryptionKeys(
            Uri coordinatorOrigin, long expiry) throws DatastoreException {
        List<AggregateEncryptionKey> aggregateEncryptionKeys = new ArrayList<>();
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.AggregateEncryptionKey.TABLE,
                                /* columns= */ null,
                                MeasurementTables.AggregateEncryptionKey.EXPIRY
                                        + " >= ? "
                                        + " AND "
                                        + MeasurementTables.AggregateEncryptionKey
                                                .AGGREGATION_COORDINATOR_ORIGIN
                                        + " = ?",
                                new String[] {String.valueOf(expiry), coordinatorOrigin.toString()},
                                /* groupBy= */ null,
                                /* having= */ null,
                                /* orderBy= */ null,
                                /* limit= */ null)) {
            while (cursor.moveToNext()) {
                aggregateEncryptionKeys.add(
                        SqliteObjectMapper.constructAggregateEncryptionKeyFromCursor(cursor));
            }
            return aggregateEncryptionKeys;
        }
    }

    @Override
    public void deleteExpiredAggregateEncryptionKeys(long expiry) throws DatastoreException {
        SQLiteDatabase db = mSQLTransaction.getDatabase();
        db.delete(
                MeasurementTables.AggregateEncryptionKey.TABLE,
                MeasurementTables.AggregateEncryptionKey.EXPIRY + " < ?",
                new String[] {String.valueOf(expiry)});
    }

    @Override
    public void insertAggregateReport(AggregateReport aggregateReport) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AggregateReport.ID, aggregateReport.getId());
        values.put(
                MeasurementTables.AggregateReport.PUBLISHER,
                aggregateReport.getPublisher().toString());
        values.put(
                MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION,
                aggregateReport.getAttributionDestination().toString());
        values.put(
                MeasurementTables.AggregateReport.SOURCE_REGISTRATION_TIME,
                aggregateReport.getSourceRegistrationTime());
        values.put(
                MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME,
                aggregateReport.getScheduledReportTime());
        values.put(
                MeasurementTables.AggregateReport.ENROLLMENT_ID, aggregateReport.getEnrollmentId());
        values.put(
                MeasurementTables.AggregateReport.DEBUG_CLEARTEXT_PAYLOAD,
                aggregateReport.getDebugCleartextPayload());
        values.put(MeasurementTables.AggregateReport.STATUS, aggregateReport.getStatus());
        values.put(
                MeasurementTables.AggregateReport.DEBUG_REPORT_STATUS,
                aggregateReport.getDebugReportStatus());
        values.put(MeasurementTables.AggregateReport.API_VERSION, aggregateReport.getApiVersion());
        values.put(
                MeasurementTables.AggregateReport.SOURCE_DEBUG_KEY,
                getNullableUnsignedLong(aggregateReport.getSourceDebugKey()));
        values.put(
                MeasurementTables.AggregateReport.TRIGGER_DEBUG_KEY,
                getNullableUnsignedLong(aggregateReport.getTriggerDebugKey()));
        values.put(MeasurementTables.AggregateReport.SOURCE_ID, aggregateReport.getSourceId());
        values.put(MeasurementTables.AggregateReport.TRIGGER_ID, aggregateReport.getTriggerId());
        values.put(
                MeasurementTables.AggregateReport.DEDUP_KEY,
                aggregateReport.getDedupKey() != null
                        ? aggregateReport.getDedupKey().getValue()
                        : null);
        values.put(
                MeasurementTables.AggregateReport.REGISTRATION_ORIGIN,
                aggregateReport.getRegistrationOrigin().toString());
        values.put(
                MeasurementTables.AggregateReport.AGGREGATION_COORDINATOR_ORIGIN,
                aggregateReport.getAggregationCoordinatorOrigin().toString());
        values.put(
                MeasurementTables.AggregateReport.IS_FAKE_REPORT, aggregateReport.isFakeReport());
        values.put(
                MeasurementTables.AggregateReport.TRIGGER_CONTEXT_ID,
                aggregateReport.getTriggerContextId());
        values.put(
                MeasurementTables.AggregateReport.TRIGGER_TIME, aggregateReport.getTriggerTime());
        long rowId =
                mSQLTransaction
                        .getDatabase()
                        .insert(
                                MeasurementTables.AggregateReport.TABLE,
                                /* nullColumnHack= */ null,
                                values);
        if (rowId == -1) {
            throw new DatastoreException("Unencrypted aggregate payload insertion failed.");
        }
    }

    @Override
    public void insertDebugReport(DebugReport debugReport) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.DebugReportContract.ID, debugReport.getId());
        values.put(MeasurementTables.DebugReportContract.TYPE, debugReport.getType());
        values.put(MeasurementTables.DebugReportContract.BODY, debugReport.getBody().toString());
        values.put(
                MeasurementTables.DebugReportContract.ENROLLMENT_ID, debugReport.getEnrollmentId());
        values.put(
                MeasurementTables.DebugReportContract.REGISTRATION_ORIGIN,
                debugReport.getRegistrationOrigin().toString());
        values.put(
                MeasurementTables.DebugReportContract.REFERENCE_ID, debugReport.getReferenceId());
        values.put(
                MeasurementTables.DebugReportContract.INSERTION_TIME,
                debugReport.getInsertionTime());
        values.put(
                MeasurementTables.DebugReportContract.REGISTRANT,
                getNullableUriString(debugReport.getRegistrant()));
        long rowId =
                mSQLTransaction
                        .getDatabase()
                        .insert(
                                MeasurementTables.DebugReportContract.TABLE,
                                /* nullColumnHack= */ null,
                                values);
        LoggerFactory.getMeasurementLogger().d("MeasurementDao: insertDebugReport: rowId=" + rowId);

        if (rowId == -1) {
            throw new DatastoreException("Debug report payload insertion failed.");
        }
    }

    @Override
    public Map<String, List<String>> getPendingAggregateReportIdsByCoordinatorInWindow(
            long windowStartTime, long windowEndTime) throws DatastoreException {
        Map<String, List<String>> result = new HashMap<>();
        try (Cursor cursor =
                mReportingRetryLimitEnabledSupplier.get()
                        ? pendingAggregateReportIdsByCoordinatorInWindowLimitRetryCursor(
                                windowStartTime, windowEndTime)
                        : pendingAggregateReportIdsByCoordinatorInWindowCursor(
                                windowStartTime, windowEndTime)) {
            while (cursor.moveToNext()) {
                String coordinator =
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AggregateReport
                                                .AGGREGATION_COORDINATOR_ORIGIN));
                result.putIfAbsent(coordinator, new ArrayList<>());
                result.get(coordinator)
                        .add(
                                cursor.getString(
                                        cursor.getColumnIndex(
                                                MeasurementTables.AggregateReport.ID)));
            }
            return result;
        }
    }

    @Override
    public List<String> getDebugReportIds() throws DatastoreException {
        List<String> debugReportIds = new ArrayList<>();
        try (Cursor cursor =
                mReportingRetryLimitEnabledSupplier.get()
                        ? debugReportIdsLimitRetryCursor()
                        : debugReportIdsCursor()) {
            while (cursor.moveToNext()) {
                debugReportIds.add(
                        cursor.getString(
                                cursor.getColumnIndex(MeasurementTables.DebugReportContract.ID)));
            }
            return debugReportIds;
        }
    }

    @Override
    public Map<String, List<String>> getPendingAggregateDebugReportIdsByCoordinator()
            throws DatastoreException {
        Map<String, List<String>> result = new HashMap<>();
        try (Cursor cursor =
                mReportingRetryLimitEnabledSupplier.get()
                        ? pendingAggregateDebugReportIdsByCoordinatorLimitRetryCursor()
                        : pendingAggregateDebugReportIdsByCoordinatorCursor()) {

            while (cursor.moveToNext()) {
                String coordinator =
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AggregateReport
                                                .AGGREGATION_COORDINATOR_ORIGIN));
                result.putIfAbsent(coordinator, new ArrayList<>());
                result.get(coordinator)
                        .add(
                                cursor.getString(
                                        cursor.getColumnIndex(
                                                MeasurementTables.AggregateReport.ID)));
            }
            return result;
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
                aggregateReports.add(
                        cursor.getString(
                                cursor.getColumnIndex(MeasurementTables.AggregateReport.ID)));
            }
            return aggregateReports;
        }
    }

    @Override
    public void deleteSources(@NonNull Collection<String> sourceIds) throws DatastoreException {
        deleteRecordsColumnBased(sourceIds, SourceContract.TABLE, SourceContract.ID);
    }

    @Override
    public void deleteTriggers(@NonNull Collection<String> triggerIds) throws DatastoreException {
        deleteRecordsColumnBased(
                triggerIds,
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.ID);
    }

    @Override
    public void deleteAsyncRegistrations(@NonNull List<String> asyncRegistrationIds)
            throws DatastoreException {
        deleteRecordsColumnBased(
                asyncRegistrationIds,
                MeasurementTables.AsyncRegistrationContract.TABLE,
                MeasurementTables.AsyncRegistrationContract.ID);
    }

    private void deleteRecordsColumnBased(
            Collection<String> columnValues, String tableName, String columnName)
            throws DatastoreException {
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .delete(
                                tableName,
                                columnName
                                        + " IN ("
                                        + Stream.generate(() -> "?")
                                                .limit(columnValues.size())
                                                .collect(Collectors.joining(","))
                                        + ")",
                                columnValues.toArray(new String[0]));
        if (rows < 0) {
            throw new DatastoreException(
                    String.format("Deletion failed from %1s on %2s.", tableName, columnName));
        }
    }

    @Override
    public List<Source> fetchTriggerMatchingSourcesForXna(
            @NonNull Trigger trigger, @NonNull Collection<String> xnaEnrollmentIds)
            throws DatastoreException {
        List<Source> sources = new ArrayList<>();
        Optional<String> destinationValue = getDestinationValue(trigger);
        if (!destinationValue.isPresent()) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "getTriggerMatchingSourcesForXna: unable to obtain destination value:"
                                    + " %s",
                            trigger.getAttributionDestination().toString());
            return sources;
        }
        String triggerDestinationValue = destinationValue.get();
        String delimitedXnaEnrollmentIds =
                xnaEnrollmentIds.stream()
                        .map(DatabaseUtils::sqlEscapeString)
                        .collect(Collectors.joining(","));
        String triggerEnrollmentId = trigger.getEnrollmentId();
        String eligibleXnaEnrollmentRegisteredSourcesWhereClause =
                mergeConditions(
                        " AND ",
                        SourceContract.ENROLLMENT_ID + " IN (" + delimitedXnaEnrollmentIds + ")",
                        SourceContract.ID
                                + " NOT IN "
                                // Avoid the sources which have lost XNA attribution before
                                + "("
                                + "select "
                                + MeasurementTables.XnaIgnoredSourcesContract.SOURCE_ID
                                + " from "
                                + MeasurementTables.XnaIgnoredSourcesContract.TABLE
                                + " where "
                                + MeasurementTables.XnaIgnoredSourcesContract.ENROLLMENT_ID
                                + " = "
                                + DatabaseUtils.sqlEscapeString(triggerEnrollmentId)
                                + ")",
                        SourceContract.REGISTRATION_ID
                                + " NOT IN "
                                // Avoid the sources (XNA parent) whose registration chain had a
                                // source registered by trigger's AdTech (by matching enrollmentId)
                                + "("
                                + "select "
                                + SourceContract.REGISTRATION_ID
                                + " from "
                                + SourceContract.TABLE
                                + " where "
                                + SourceContract.ENROLLMENT_ID
                                + " = "
                                + DatabaseUtils.sqlEscapeString(triggerEnrollmentId)
                                + ")",
                        SourceContract.SHARED_AGGREGATION_KEYS + " IS NOT NULL");

        String eligibleTriggerNetworkRegisteredSourcesWhereClause =
                String.format(
                        SourceContract.ENROLLMENT_ID + " = %s",
                        DatabaseUtils.sqlEscapeString(triggerEnrollmentId));
        // The following filtering logic is applied -
        //  - AND
        //     - destination == trigger's destination
        //     - expiryTime > triggerTime
        //     - eventTime < triggerTime
        //     - OR
        //      - AND
        //       - sourceEnrollmentId == trigger's enrollmentId
        //      - AND
        //       - sourceEnrollmentId IN XNA enrollment IDs
        //       - sourceId NOT IN (sources associated to XNA that have lost
        //         attribution in the past -- lose once lose always)
        //       - triggerEnrollmentId NOT IN (enrollmentIds of the sources registered under this
        // registration ID)
        //       - sharedAggregationKeys NOT NULL
        String sourceWhereStatement =
                mergeConditions(
                        " AND ",
                        SourceContract.EXPIRY_TIME + " > ?",
                        mergeConditions(
                                " OR ",
                                eligibleTriggerNetworkRegisteredSourcesWhereClause,
                                eligibleXnaEnrollmentRegisteredSourcesWhereClause),
                        SourceContract.EVENT_TIME + " <= ? ");

        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .rawQuery(
                                selectSourcesByDestination(
                                        triggerDestinationValue,
                                        trigger.getDestinationType(),
                                        sourceWhereStatement),
                                new String[] {
                                    String.valueOf(trigger.getTriggerTime()),
                                    String.valueOf(trigger.getTriggerTime())
                                })) {
            while (cursor.moveToNext()) {
                sources.add(SqliteObjectMapper.constructSourceFromCursor(cursor));
            }
            return sources;
        }
    }

    @Override
    public void insertIgnoredSourceForEnrollment(
            @NonNull String sourceId, @NonNull String enrollmentId) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.XnaIgnoredSourcesContract.SOURCE_ID, sourceId);
        values.put(MeasurementTables.XnaIgnoredSourcesContract.ENROLLMENT_ID, enrollmentId);
        long rowId =
                mSQLTransaction
                        .getDatabase()
                        .insert(
                                MeasurementTables.XnaIgnoredSourcesContract.TABLE,
                                /* nullColumnHack= */ null,
                                values);
        if (rowId == -1) {
            throw new DatastoreException("Xna ignored source insertion failed.");
        }
    }

    private static Optional<String> getDestinationValue(Trigger trigger) {
        if (trigger.getDestinationType() == EventSurfaceType.APP) {
            return Optional.of(trigger.getAttributionDestination().toString());
        } else {
            Optional<Uri> topPrivateDomainAndScheme =
                    WebAddresses.topPrivateDomainAndScheme(trigger.getAttributionDestination());
            return topPrivateDomainAndScheme.map(Uri::toString);
        }
    }

    private static Optional<Uri> extractBaseUri(Uri uri, @EventSurfaceType int eventSurfaceType) {
        return eventSurfaceType == EventSurfaceType.APP
                ? Optional.of(BaseUriExtractor.getBaseUri(uri))
                : WebAddresses.topPrivateDomainAndScheme(uri);
    }

    private static String getPublisherWhereStatement(
            Uri publisher, @EventSurfaceType int publisherType) {
        if (publisherType == EventSurfaceType.APP) {
            return String.format(
                    Locale.ENGLISH,
                    "%s = %s",
                    SourceContract.PUBLISHER,
                    DatabaseUtils.sqlEscapeString(publisher.toString()));
        } else {
            return String.format(
                    Locale.ENGLISH,
                    "(%1$s = %2$s OR %1$s LIKE %3$s)",
                    SourceContract.PUBLISHER,
                    DatabaseUtils.sqlEscapeString(publisher.toString()),
                    DatabaseUtils.sqlEscapeString(
                            publisher.getScheme() + "://%." + publisher.getEncodedAuthority()));
        }
    }

    /** Returns a SQL where statement for matching an app/web destination. */
    private static String getDestinationWhereStatement(
            Uri destination, @EventSurfaceType int destinationType) {
        Optional<Uri> destinationBaseUriOptional = extractBaseUri(destination, destinationType);
        if (!destinationBaseUriOptional.isPresent()) {
            throw new IllegalArgumentException(
                    String.format(
                            Locale.ENGLISH,
                            "getDestinationWhereStatement:" + " Unable to extract base uri from %s",
                            destination.toString()));
        }
        Uri destinationBaseUri = destinationBaseUriOptional.get();

        if (destinationType == EventSurfaceType.APP) {
            return String.format(
                    Locale.ENGLISH,
                    "(%1$s = %2$s OR %1$s LIKE %3$s)",
                    MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION,
                    DatabaseUtils.sqlEscapeString(destinationBaseUri.toString()),
                    DatabaseUtils.sqlEscapeString(destinationBaseUri + "/%"));
        } else {
            String schemeSubDomainMatcher =
                    destination.getScheme() + "://%." + destinationBaseUri.getAuthority();
            String schemeDomainMatcher =
                    destination.getScheme() + "://" + destinationBaseUri.getAuthority();
            String domainAndPathMatcher = schemeDomainMatcher + "/%";
            String subDomainAndPathMatcher = schemeSubDomainMatcher + "/%";
            return String.format(
                    Locale.ENGLISH,
                    "(%1$s = %2$s OR %1$s LIKE %3$s OR %1$s LIKE %4$s OR %1$s LIKE %5$s)",
                    MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION,
                    DatabaseUtils.sqlEscapeString(schemeDomainMatcher),
                    DatabaseUtils.sqlEscapeString(schemeSubDomainMatcher),
                    DatabaseUtils.sqlEscapeString(domainAndPathMatcher),
                    DatabaseUtils.sqlEscapeString(subDomainAndPathMatcher));
        }
    }

    private static String getNullableUriString(@Nullable Uri uri) {
        return Optional.ofNullable(uri).map(Uri::toString).orElse(null);
    }

    private static Long getNullableUnsignedLong(@Nullable UnsignedLong ulong) {
        return Optional.ofNullable(ulong).map(UnsignedLong::getValue).orElse(null);
    }

    /**
     * Return registration origins that recognizes the app install as a re-install i.e. exists rows
     * in AppReportHistory table with the app destination for the report origin and the last report
     * is within reinstall reattribution window and is install attributed.
     */
    private static String getReinstallRegistrationOriginsQuery(
            Uri uri, long eventTimestamp, String whereString) {
        String earliestReportTime =
                String.format(
                        Locale.ENGLISH,
                        "%1$d - "
                                + "("
                                + SourceContract.TABLE
                                + "."
                                + SourceContract.REINSTALL_REATTRIBUTION_WINDOW
                                + ")",
                        eventTimestamp);

        // Check if install is reinstall by filtering sources that are install attributed and
        // are within the reinstall window.
        final String reinstallWhereClause =
                mergeConditions(
                        " AND ",
                        whereString,
                        MeasurementTables.SourceContract.TABLE
                                + "."
                                + MeasurementTables.SourceContract.IS_INSTALL_ATTRIBUTED
                                + " = 1",
                        // Only consider app report history within the signal lifetime
                        MeasurementTables.AppReportHistoryContract.TABLE
                                + "."
                                + MeasurementTables.AppReportHistoryContract
                                        .LAST_REPORT_DELIVERED_TIME
                                + " > "
                                + earliestReportTime);

        final String sourceAppReportHistoryJoinClause =
                mergeConditions(
                        " AND ",
                        MeasurementTables.SourceContract.TABLE
                                + "."
                                + MeasurementTables.SourceContract.REGISTRATION_ORIGIN
                                + " = "
                                + MeasurementTables.AppReportHistoryContract.TABLE
                                + "."
                                + MeasurementTables.AppReportHistoryContract.REGISTRATION_ORIGIN,
                        MeasurementTables.AppReportHistoryContract.TABLE
                                + "."
                                + MeasurementTables.AppReportHistoryContract.APP_DESTINATION
                                + " = "
                                + DatabaseUtils.sqlEscapeString(uri.toString()));

        String reinstallQuery =
                "SELECT DISTINCT "
                        + SourceContract.TABLE
                        + "."
                        + SourceContract.REGISTRATION_ORIGIN
                        + " FROM "
                        + MeasurementTables.SourceContract.TABLE
                        + " INNER JOIN "
                        + MeasurementTables.AppReportHistoryContract.TABLE
                        + " ON "
                        + sourceAppReportHistoryJoinClause
                        + " WHERE "
                        + reinstallWhereClause;
        return reinstallQuery;
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

    public void insertAsyncRegistration(@NonNull AsyncRegistration asyncRegistration)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AsyncRegistrationContract.ID, asyncRegistration.getId());
        values.put(
                MeasurementTables.AsyncRegistrationContract.REGISTRATION_URI,
                asyncRegistration.getRegistrationUri().toString());
        values.put(
                MeasurementTables.AsyncRegistrationContract.WEB_DESTINATION,
                getNullableUriString(asyncRegistration.getWebDestination()));
        values.put(
                MeasurementTables.AsyncRegistrationContract.VERIFIED_DESTINATION,
                getNullableUriString(asyncRegistration.getVerifiedDestination()));
        values.put(
                MeasurementTables.AsyncRegistrationContract.OS_DESTINATION,
                getNullableUriString(asyncRegistration.getOsDestination()));
        values.put(
                MeasurementTables.AsyncRegistrationContract.REGISTRANT,
                getNullableUriString(asyncRegistration.getRegistrant()));
        values.put(
                MeasurementTables.AsyncRegistrationContract.TOP_ORIGIN,
                asyncRegistration.getTopOrigin().toString());
        values.put(
                MeasurementTables.AsyncRegistrationContract.SOURCE_TYPE,
                asyncRegistration.getSourceType() == null
                        ? null
                        : asyncRegistration.getSourceType().ordinal());
        values.put(
                MeasurementTables.AsyncRegistrationContract.REQUEST_TIME,
                asyncRegistration.getRequestTime());
        values.put(
                MeasurementTables.AsyncRegistrationContract.RETRY_COUNT,
                asyncRegistration.getRetryCount());
        values.put(
                MeasurementTables.AsyncRegistrationContract.TYPE,
                asyncRegistration.getType().ordinal());
        values.put(
                MeasurementTables.AsyncRegistrationContract.DEBUG_KEY_ALLOWED,
                asyncRegistration.getDebugKeyAllowed());
        values.put(
                MeasurementTables.AsyncRegistrationContract.AD_ID_PERMISSION,
                asyncRegistration.hasAdIdPermission());
        values.put(
                MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID,
                asyncRegistration.getRegistrationId());
        values.put(
                MeasurementTables.AsyncRegistrationContract.PLATFORM_AD_ID,
                asyncRegistration.getPlatformAdId());
        values.put(
                MeasurementTables.AsyncRegistrationContract.REQUEST_POST_BODY,
                asyncRegistration.getPostBody());
        values.put(
                MeasurementTables.AsyncRegistrationContract.REDIRECT_BEHAVIOR,
                asyncRegistration.getRedirectBehavior().name());
        long rowId =
                mSQLTransaction
                        .getDatabase()
                        .insert(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                /* nullColumnHack= */ null,
                                values);
        LoggerFactory.getMeasurementLogger()
                .d("MeasurementDao: insertAsyncRegistration: rowId=" + rowId);
        if (rowId == -1) {
            throw new DatastoreException("Async Registration insertion failed.");
        }
    }

    @Override
    public void deleteAsyncRegistration(@NonNull String id) throws DatastoreException {
        SQLiteDatabase db = mSQLTransaction.getDatabase();
        int rows =
                db.delete(
                        MeasurementTables.AsyncRegistrationContract.TABLE,
                        MeasurementTables.AsyncRegistrationContract.ID + " = ?",
                        new String[] {id});
        if (rows == 0) {
            // This is needed because we want to fail the transaction in case the registration was
            // deleted by some other job while it was being processed.
            throw new DatastoreException("Async Registration already deleted");
        }
        LoggerFactory.getMeasurementLogger()
                .d("MeasurementDao: deleteAsyncRegistration: rows affected=" + rows);
    }

    @Override
    public AsyncRegistration fetchNextQueuedAsyncRegistration(
            int retryLimit, Set<Uri> failedOrigins) throws DatastoreException {
        String originExclusion = "";

        if (!failedOrigins.isEmpty()) {
            List<String> notLikes = new ArrayList<>();
            failedOrigins.forEach(
                    (origin) -> {
                        notLikes.add(
                                MeasurementTables.AsyncRegistrationContract.REGISTRATION_URI
                                        + " NOT LIKE "
                                        + DatabaseUtils.sqlEscapeString(origin + "%"));
                    });
            originExclusion = mergeConditions(" AND ", notLikes.toArray(new String[0]));
        }
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                /* columns= */ null,
                                mergeConditions(
                                        " AND ",
                                        MeasurementTables.AsyncRegistrationContract.RETRY_COUNT
                                                + " < ? ",
                                        originExclusion),
                                new String[] {String.valueOf(retryLimit)},
                                /* groupBy= */ null,
                                /* having= */ null,
                                /* orderBy= */ MeasurementTables.AsyncRegistrationContract
                                        .REQUEST_TIME,
                                /* limit= */ "1")) {
            if (cursor.getCount() == 0) {
                return null;
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructAsyncRegistration(cursor);
        }
    }

    @Override
    public KeyValueData getKeyValueData(@NonNull String key, @NonNull DataType dataType)
            throws DatastoreException {
        String value = null;
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.KeyValueDataContract.TABLE,
                                new String[] {MeasurementTables.KeyValueDataContract.VALUE},
                                MeasurementTables.KeyValueDataContract.DATA_TYPE
                                        + " = ? "
                                        + " AND "
                                        + MeasurementTables.KeyValueDataContract.KEY
                                        + " = ?",
                                new String[] {dataType.toString(), key},
                                null,
                                null,
                                null,
                                null)) {
            if (cursor.moveToNext()) {
                value = cursor.getString(0);
            }
        }
        return new KeyValueData.Builder().setDataType(dataType).setKey(key).setValue(value).build();
    }

    @Override
    public void insertOrUpdateKeyValueData(@NonNull KeyValueData keyValueData)
            throws DatastoreException {
        ContentValues contentValues = new ContentValues();
        contentValues.put(
                MeasurementTables.KeyValueDataContract.DATA_TYPE,
                keyValueData.getDataType().toString());
        contentValues.put(MeasurementTables.KeyValueDataContract.KEY, keyValueData.getKey());
        contentValues.put(MeasurementTables.KeyValueDataContract.VALUE, keyValueData.getValue());
        long rowId =
                mSQLTransaction
                        .getDatabase()
                        .insertWithOnConflict(
                                MeasurementTables.KeyValueDataContract.TABLE,
                                null,
                                contentValues,
                                CONFLICT_REPLACE);
        if (rowId == -1) {
            throw new DatastoreException("KeyValueData insertion failed: " + contentValues);
        }
    }

    @Override
    public void updateRetryCount(AsyncRegistration asyncRegistration) throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(
                MeasurementTables.AsyncRegistrationContract.RETRY_COUNT,
                asyncRegistration.getRetryCount());
        long rows =
                mSQLTransaction
                        .getDatabase()
                        .update(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                values,
                                MeasurementTables.AsyncRegistrationContract.ID + " = ?",
                                new String[] {asyncRegistration.getId()});
        if (rows != 1) {
            throw new DatastoreException("Retry Count update failed.");
        }
    }

    @Override
    public int incrementAndGetReportingRetryCount(String id, DataType reportType)
            throws DatastoreException {
        KeyValueData eventRetry = getKeyValueData(id, reportType);
        eventRetry.setReportRetryCount(eventRetry.getReportRetryCount() + 1);
        insertOrUpdateKeyValueData(eventRetry);
        int retryCount = eventRetry.getReportRetryCount();
        LoggerFactory.getMeasurementLogger()
                .d("Incrementing: " + reportType + " Retry Count: " + retryCount);
        return retryCount;
    }

    @Override
    public long countDistinctDebugAdIdsUsedByEnrollment(@NonNull String enrollmentId)
            throws DatastoreException {
        return DatabaseUtils.longForQuery(
                mSQLTransaction.getDatabase(),
                countDistinctDebugAdIdsUsedByEnrollmentQuery(),
                new String[] {
                    enrollmentId,
                    String.valueOf(EventSurfaceType.WEB),
                    enrollmentId,
                    String.valueOf(EventSurfaceType.WEB)
                });
    }

    @Override
    public long countNavigationSourcesPerReportingOrigin(
            @NonNull Uri reportingOrigin, @NonNull String registrationId)
            throws DatastoreException {
        String query =
                String.format(
                        Locale.ENGLISH,
                        "SELECT COUNT(*) "
                                + "FROM "
                                + MeasurementTables.SourceContract.TABLE
                                + " WHERE "
                                + MeasurementTables.SourceContract.REGISTRATION_ORIGIN
                                + " = ? AND "
                                + MeasurementTables.SourceContract.REGISTRATION_ID
                                + " = ? AND "
                                + MeasurementTables.SourceContract.SOURCE_TYPE
                                + " = ?");
        return DatabaseUtils.longForQuery(
                mSQLTransaction.getDatabase(),
                query,
                new String[] {
                    reportingOrigin.toString(),
                    registrationId,
                    String.valueOf(Source.SourceType.NAVIGATION)
                });
    }

    @Override
    public Pair<Long, List<String>> fetchSourceIdsForLowestPriorityDestinationXEnrollmentXPublisher(
            Uri publisher,
            int publisherType,
            String enrollmentId,
            List<Uri> excludedDestinations,
            int destinationType,
            long windowEndTime)
            throws DatastoreException {
        String unexpiredSources =
                String.format(
                        Locale.ENGLISH,
                        // Unexpired sources, excludes MARKED_TO_DELETE sources as they are
                        // deactivated already
                        "WITH source_ids AS ( "
                                + "SELECT %1$s FROM %2$s "
                                + "WHERE %3$s "
                                + "AND %4$s = %5$s "
                                + "AND %6$s > %7$s "
                                + "AND %8$s != %9$s "
                                + ")",
                        MeasurementTables.SourceContract.ID, // 1
                        MeasurementTables.SourceContract.TABLE, // 2
                        getPublisherWhereStatement(publisher, publisherType), // 3
                        MeasurementTables.SourceContract.ENROLLMENT_ID, // 4
                        DatabaseUtils.sqlEscapeString(enrollmentId), // 5
                        MeasurementTables.SourceContract.EXPIRY_TIME, // 6
                        windowEndTime, // 7
                        MeasurementTables.SourceContract.STATUS, // 8
                        Source.Status.MARKED_TO_DELETE // 9
                        );

        boolean isDestinationLimitPriorityEnabled =
                FlagsFactory.getFlags().getMeasurementEnableSourceDestinationLimitPriority();
        String selectHighestDestinationPriority =
                isDestinationLimitPriorityEnabled
                        ? String.format(
                                " MAX (s.%1$s) AS destination_limit_priority, ",
                                SourceContract.DESTINATION_LIMIT_PRIORITY)
                        : "";
        String orderByDestinationPriority =
                isDestinationLimitPriorityEnabled ? " destination_limit_priority ASC, " : "";

        String oldestDestination =
                String.join(
                        ", ",
                        unexpiredSources,
                        String.format(
                                "oldest_destination AS ( "
                                        + "SELECT d.%1$s, "
                                        + selectHighestDestinationPriority
                                        + "MAX(s.%2$s) AS source_event_time FROM %3$s AS d "
                                        + "INNER JOIN %4$s AS s ON (d.%5$s = s.%6$s) "
                                        + "WHERE d.%5$s IN source_ids "
                                        + "AND d.%7$s = %8$s "
                                        + "AND %1$s NOT IN "
                                        + flattenAsSqlQueryList(excludedDestinations)
                                        + " GROUP BY d.%1$s "
                                        + "ORDER BY "
                                        + orderByDestinationPriority
                                        + "source_event_time ASC LIMIT 1) ",
                                MeasurementTables.SourceDestination.DESTINATION, // 1
                                MeasurementTables.SourceContract.EVENT_TIME, // 2
                                MeasurementTables.SourceDestination.TABLE, // 3
                                MeasurementTables.SourceContract.TABLE, // 4
                                MeasurementTables.SourceDestination.SOURCE_ID, // 5
                                MeasurementTables.SourceContract.ID, // 6
                                MeasurementTables.SourceDestination.DESTINATION_TYPE, // 7
                                destinationType // 8
                                ));

        String sourcesAssociatedToOldestDestination =
                oldestDestination
                        + String.format(
                                "SELECT DISTINCT(%1$s) FROM %2$s "
                                        + "WHERE %3$s IN "
                                        + "(SELECT %3$s FROM oldest_destination)"
                                        + " AND %1$s IN source_ids",
                                MeasurementTables.SourceDestination.SOURCE_ID,
                                MeasurementTables.SourceDestination.TABLE,
                                MeasurementTables.SourceDestination.DESTINATION);

        List<String> sourceIds = new ArrayList<>();
        long lowestDestinationLimitPriority = Long.MIN_VALUE;
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .query(
                                MeasurementTables.SourceContract.TABLE,
                                new String[] {
                                    MeasurementTables.SourceContract.ID,
                                    SourceContract.DESTINATION_LIMIT_PRIORITY
                                },
                                MeasurementTables.SourceContract.ID
                                        + " IN "
                                        + "("
                                        + sourcesAssociatedToOldestDestination
                                        + ")",
                                null,
                                null,
                                null,
                                null)) {
            while (cursor.moveToNext()) {
                sourceIds.add(
                        cursor.getString(
                                cursor.getColumnIndex(MeasurementTables.SourceContract.ID)));
                long sourceDestinationLimitPriority =
                        cursor.getLong(
                                cursor.getColumnIndex(SourceContract.DESTINATION_LIMIT_PRIORITY));
                // Find the maximum destination limit priority value, i.e. if two sources assign
                // different destination limit priority value to the associated destinations, we
                // consider higher of the two values.
                if (sourceDestinationLimitPriority > lowestDestinationLimitPriority) {
                    lowestDestinationLimitPriority = sourceDestinationLimitPriority;
                }
            }
        }
        return new Pair<>(lowestDestinationLimitPriority, sourceIds);
    }

    @Override
    public void deletePendingAggregateReportsAndAttributionsForSources(List<String> sourceIds)
            throws DatastoreException {
        String aggregateReportsWhereClause =
                mergeConditions(
                        " AND ",
                        MeasurementTables.AggregateReport.SOURCE_ID
                                + " IN "
                                + flattenAsSqlQueryList(sourceIds),
                        MeasurementTables.AggregateReport.STATUS
                                + " = "
                                + AggregateReport.Status.PENDING);
        String selectPendingAggregateReports =
                String.format(
                        "( SELECT %1$s FROM %2$s WHERE %3$s )",
                        MeasurementTables.AggregateReport.ID,
                        MeasurementTables.AggregateReport.TABLE,
                        aggregateReportsWhereClause);

        // Delete the attributions first
        if (mSQLTransaction
                        .getDatabase()
                        .delete(
                                MeasurementTables.AttributionContract.TABLE,
                                String.format(
                                        "%1$s IN %2$s",
                                        MeasurementTables.AttributionContract.REPORT_ID,
                                        selectPendingAggregateReports),
                                null)
                < 0) {
            throw new DatastoreException("Attribution deletion failed.");
        }

        // Delete the reports
        if (mSQLTransaction
                        .getDatabase()
                        .delete(
                                MeasurementTables.AggregateReport.TABLE,
                                aggregateReportsWhereClause,
                                null)
                < 0) {
            throw new DatastoreException("Aggregate Reports deletion failed.");
        }
    }

    @Override
    public void deleteFutureFakeEventReportsForSources(
            List<String> sourceIds, long currentTimeStamp) throws DatastoreException {
        String fakeEventReportsWhereClause =
                mergeConditions(
                        " AND ",
                        MeasurementTables.EventReportContract.SOURCE_ID
                                + " IN "
                                + flattenAsSqlQueryList(sourceIds),
                        MeasurementTables.EventReportContract.STATUS
                                + " = "
                                + EventReport.Status.PENDING,
                        MeasurementTables.EventReportContract.TRIGGER_TIME
                                + " >= "
                                + currentTimeStamp);

        // Delete the reports
        if (mSQLTransaction
                        .getDatabase()
                        .delete(
                                MeasurementTables.EventReportContract.TABLE,
                                fakeEventReportsWhereClause,
                                null)
                < 0) {
            throw new DatastoreException("Fake event report deletion failed.");
        }
    }

    @Override
    public void insertOrUpdateAppReportHistory(
            @NonNull Uri appDestination,
            @NonNull Uri registrationOrigin,
            long lastReportDeliveredTimestamp)
            throws DatastoreException {
        ContentValues values = new ContentValues();
        values.put(
                MeasurementTables.AppReportHistoryContract.APP_DESTINATION,
                appDestination.toString());
        values.put(
                MeasurementTables.AppReportHistoryContract.REGISTRATION_ORIGIN,
                registrationOrigin.toString());
        values.put(
                MeasurementTables.AppReportHistoryContract.LAST_REPORT_DELIVERED_TIME,
                lastReportDeliveredTimestamp);
        long rowId =
                mSQLTransaction
                        .getDatabase()
                        .insertWithOnConflict(
                                MeasurementTables.AppReportHistoryContract.TABLE,
                                /* nullColumnHack= */ null,
                                values,
                                CONFLICT_REPLACE);
        LoggerFactory.getMeasurementLogger()
                .d("MeasurementDao: insertAppReportHistory: rowId=" + rowId);
        if (rowId == -1) {
            throw new DatastoreException("App report history insertion failed.");
        }
    }

    private int getNumReportsPerDestination(
            String tableName,
            String columnName,
            Uri attributionDestination,
            @EventSurfaceType int destinationType)
            throws DatastoreException {
        Optional<Uri> destinationBaseUri = extractBaseUri(attributionDestination, destinationType);
        if (!destinationBaseUri.isPresent()) {
            throw new IllegalStateException("extractBaseUri failed for destination.");
        }

        // Example: https://destination.com
        String noSubdomainOrPostfixMatch =
                DatabaseUtils.sqlEscapeString(
                        destinationBaseUri.get().getScheme()
                                + "://"
                                + destinationBaseUri.get().getHost());

        // Example: https://subdomain.destination.com/path
        String subdomainAndPostfixMatch =
                DatabaseUtils.sqlEscapeString(
                        destinationBaseUri.get().getScheme()
                                + "://%."
                                + destinationBaseUri.get().getHost()
                                + "/%");

        // Example: https://subdomain.destination.com
        String subdomainMatch =
                DatabaseUtils.sqlEscapeString(
                        destinationBaseUri.get().getScheme()
                                + "://%."
                                + destinationBaseUri.get().getHost());

        // Example: https://destination.com/path
        String postfixMatch =
                DatabaseUtils.sqlEscapeString(
                        destinationBaseUri.get().getScheme()
                                + "://"
                                + destinationBaseUri.get().getHost()
                                + "/%");
        String query;
        if (destinationType == EventSurfaceType.WEB) {
            query =
                    String.format(
                            Locale.ENGLISH,
                            "SELECT COUNT(*) FROM %2$s WHERE (%1$s = %3$s"
                                    + " OR %1$s LIKE %4$s"
                                    + " OR %1$s LIKE %5$s"
                                    + " OR %1$s LIKE %6$s)",
                            columnName,
                            tableName,
                            noSubdomainOrPostfixMatch,
                            subdomainAndPostfixMatch,
                            subdomainMatch,
                            postfixMatch);
        } else {
            query =
                    String.format(
                            Locale.ENGLISH,
                            "SELECT COUNT(*) FROM %2$s WHERE"
                                    + " (%1$s = %3$s"
                                    + " OR %1$s LIKE %4$s)",
                            columnName,
                            tableName,
                            noSubdomainOrPostfixMatch,
                            postfixMatch);
        }

        if (FlagsFactory.getFlags().getMeasurementNullAggregateReportEnabled()
                && tableName.equals(MeasurementTables.AggregateReport.TABLE)) {
            query +=
                    String.format(
                            Locale.ENGLISH,
                            " AND %1$s = 0",
                            MeasurementTables.AggregateReport.IS_FAKE_REPORT);
        }

        return (int) DatabaseUtils.longForQuery(mSQLTransaction.getDatabase(), query, null);
    }

    private <T> List<T> fetchRecordsMatchingWithParameters(
            String tableName,
            String sourceColumnName,
            Collection<String> sourceIds,
            String triggerColumnName,
            Collection<String> triggerIds,
            Function<Cursor, T> sqlMapperFunction)
            throws DatastoreException {
        List<T> reports = new ArrayList<>();
        String delimitedSourceIds =
                sourceIds.stream()
                        .map(DatabaseUtils::sqlEscapeString)
                        .collect(Collectors.joining(","));
        String delimitedTriggerIds =
                triggerIds.stream()
                        .map(DatabaseUtils::sqlEscapeString)
                        .collect(Collectors.joining(","));

        String whereString =
                mergeConditions(
                        /* operator= */ " OR ",
                        sourceColumnName + " IN (" + delimitedSourceIds + ")",
                        triggerColumnName + " IN (" + delimitedTriggerIds + ")");
        try (Cursor cursor =
                mSQLTransaction
                        .getDatabase()
                        .rawQuery(
                                String.format(
                                        Locale.ENGLISH,
                                        "SELECT * FROM %1$s WHERE " + whereString,
                                        tableName),
                                null)) {
            while (cursor.moveToNext()) {
                reports.add(sqlMapperFunction.apply(cursor));
            }
        }
        return reports;
    }

    /**
     * Given a destination and destination type, return a SELECT statement for matching source IDs.
     */
    private static String selectSourceIdsByDestination(
            String destination, @EventSurfaceType int destinationType) {
        return String.format(
                Locale.ENGLISH,
                "SELECT "
                        + SourceDestination.SOURCE_ID
                        + " "
                        + "FROM "
                        + SourceDestination.TABLE
                        + " "
                        + "WHERE "
                        + SourceDestination.DESTINATION
                        + " = "
                        + DatabaseUtils.sqlEscapeString(destination)
                        + " "
                        + "AND "
                        + SourceDestination.DESTINATION_TYPE
                        + " = "
                        + destinationType);
    }

    /**
     * Given a trigger destination and type, and constraints on the source, return all matching
     * sources.
     */
    private static String selectSourcesByDestination(
            String triggerDestinationValue,
            @EventSurfaceType int destinationType,
            String sourceWhereStatement) {
        return String.format(
                Locale.ENGLISH,
                "SELECT * FROM "
                        + SourceContract.TABLE
                        + " WHERE "
                        + SourceContract.ID
                        + " IN ("
                        + "SELECT "
                        + SourceDestination.SOURCE_ID
                        + " FROM "
                        + SourceDestination.TABLE
                        + " WHERE "
                        + SourceDestination.DESTINATION
                        + " = '"
                        + triggerDestinationValue
                        + "' "
                        + "AND "
                        + SourceDestination.DESTINATION_TYPE
                        + " = "
                        + destinationType
                        + ") "
                        + "AND ("
                        + sourceWhereStatement
                        + ")");
    }

    /**
     * Given an enrollment id, return the number unique debug ad id values present in sources and
     * triggers with this enrollment id.
     */
    private static String countDistinctDebugAdIdsUsedByEnrollmentQuery() {
        return String.format(
                Locale.ENGLISH,
                "SELECT COUNT (DISTINCT "
                        + SourceContract.DEBUG_AD_ID
                        + ") "
                        + "FROM ( "
                        + "SELECT "
                        + SourceContract.DEBUG_AD_ID
                        + " FROM "
                        + SourceContract.TABLE
                        + " WHERE "
                        + SourceContract.DEBUG_AD_ID
                        + " IS NOT NULL "
                        + "AND "
                        + SourceContract.ENROLLMENT_ID
                        + " = ? "
                        + "AND "
                        + SourceContract.PUBLISHER_TYPE
                        + " = ? "
                        + "UNION ALL "
                        + "SELECT "
                        + MeasurementTables.TriggerContract.DEBUG_AD_ID
                        + " FROM "
                        + MeasurementTables.TriggerContract.TABLE
                        + " WHERE "
                        + MeasurementTables.TriggerContract.DEBUG_AD_ID
                        + " IS NOT NULL "
                        + "AND "
                        + MeasurementTables.TriggerContract.ENROLLMENT_ID
                        + " = ? "
                        + "AND "
                        + MeasurementTables.TriggerContract.DESTINATION_TYPE
                        + " = ?"
                        + ")");
    }

    private Cursor pendingEventReportIdsInWindowCursor(long windowStartTime, long windowEndTime)
            throws DatastoreException {
        return mSQLTransaction
                .getDatabase()
                .query(
                        MeasurementTables.EventReportContract.TABLE,
                        /* columns= */ null,
                        MeasurementTables.EventReportContract.REPORT_TIME
                                + " >= ? AND "
                                + MeasurementTables.EventReportContract.REPORT_TIME
                                + " <= ? AND "
                                + MeasurementTables.EventReportContract.STATUS
                                + " = ? ",
                        new String[] {
                            String.valueOf(windowStartTime),
                            String.valueOf(windowEndTime),
                            String.valueOf(EventReport.Status.PENDING)
                        },
                        /* groupBy= */ null,
                        /* having= */ null,
                        /* orderBy= */ "RANDOM()",
                        /* limit= */ null);
    }

    private Cursor pendingEventReportIdsInWindowCursorWithRetryLimit(
            long windowStartTime, long windowEndTime) throws DatastoreException {
        return mSQLTransaction
                .getDatabase()
                .rawQuery(
                        "SELECT "
                                + MeasurementTables.EventReportContract.ID
                                + ", "
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " FROM "
                                + MeasurementTables.EventReportContract.TABLE
                                + " LEFT JOIN "
                                + MeasurementTables.KeyValueDataContract.TABLE
                                + " ON ("
                                + MeasurementTables.EventReportContract.ID
                                + " = "
                                + MeasurementTables.KeyValueDataContract.KEY
                                + ") "
                                + "WHERE "
                                + "(CAST("
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " AS INTEGER) < ?"
                                + "OR "
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " IS NULL)"
                                + " AND "
                                + MeasurementTables.EventReportContract.STATUS
                                + " = ? "
                                + "AND "
                                + MeasurementTables.EventReportContract.REPORT_TIME
                                + " >= ?"
                                + "AND "
                                + MeasurementTables.EventReportContract.REPORT_TIME
                                + " <= ?"
                                + "AND ("
                                + MeasurementTables.KeyValueDataContract.DATA_TYPE
                                + " = ? "
                                + "OR "
                                + MeasurementTables.KeyValueDataContract.DATA_TYPE
                                + " IS NULL)"
                                + " ORDER BY RANDOM()",
                        new String[] {
                            String.valueOf(mReportingRetryLimitSupplier.get()),
                            String.valueOf(EventReport.Status.PENDING),
                            String.valueOf(windowStartTime),
                            String.valueOf(windowEndTime),
                            String.valueOf(DataType.EVENT_REPORT_RETRY_COUNT),
                        });
    }

    private Cursor pendingDebugEventReportIdsCursor() throws DatastoreException {
        return mSQLTransaction
                .getDatabase()
                .query(
                        MeasurementTables.EventReportContract.TABLE,
                        /* columns= */ null,
                        MeasurementTables.EventReportContract.DEBUG_REPORT_STATUS + " = ? ",
                        new String[] {String.valueOf(EventReport.DebugReportStatus.PENDING)},
                        /* groupBy= */ null,
                        /* having= */ null,
                        /* orderBy= */ "RANDOM()",
                        /* limit= */ null);
    }

    private Cursor pendingDebugEventReportIdsLimitRetryCursor() throws DatastoreException {
        return mSQLTransaction
                .getDatabase()
                .rawQuery(
                        "SELECT "
                                + MeasurementTables.EventReportContract.ID
                                + ", "
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " FROM "
                                + MeasurementTables.EventReportContract.TABLE
                                + " LEFT JOIN "
                                + MeasurementTables.KeyValueDataContract.TABLE
                                + " ON ("
                                + MeasurementTables.EventReportContract.ID
                                + " = "
                                + MeasurementTables.KeyValueDataContract.KEY
                                + ") "
                                + " WHERE "
                                + MeasurementTables.EventReportContract.DEBUG_REPORT_STATUS
                                + " = ?"
                                + " AND (CAST("
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " AS INTEGER) < ? "
                                + " OR "
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " IS NULL)"
                                + " AND ("
                                + MeasurementTables.KeyValueDataContract.DATA_TYPE
                                + " = ? "
                                + " OR "
                                + MeasurementTables.KeyValueDataContract.DATA_TYPE
                                + " IS NULL)"
                                + " ORDER BY RANDOM()",
                        new String[] {
                            String.valueOf(EventReport.DebugReportStatus.PENDING),
                            String.valueOf(mReportingRetryLimitSupplier.get()),
                            String.valueOf(DataType.DEBUG_EVENT_REPORT_RETRY_COUNT),
                        });
    }

    private Cursor pendingAggregateReportIdsByCoordinatorInWindowCursor(
            long windowStartTime, long windowEndTime) throws DatastoreException {
        return mSQLTransaction
                .getDatabase()
                .query(
                        MeasurementTables.AggregateReport.TABLE,
                        /* columns= */ new String[] {
                            MeasurementTables.AggregateReport.ID,
                            MeasurementTables.AggregateReport.AGGREGATION_COORDINATOR_ORIGIN
                        },
                        MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME
                                + " >= ? AND "
                                + MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME
                                + " <= ? AND "
                                + MeasurementTables.AggregateReport.STATUS
                                + " = ? ",
                        new String[] {
                            String.valueOf(windowStartTime),
                            String.valueOf(windowEndTime),
                            String.valueOf(AggregateReport.Status.PENDING)
                        },
                        /* groupBy= */ null,
                        /* having= */ null,
                        /* orderBy= */ "RANDOM()",
                        /* limit= */ null);
    }

    private Cursor pendingAggregateReportIdsByCoordinatorInWindowLimitRetryCursor(
            long windowStartTime, long windowEndTime) throws DatastoreException {
        return mSQLTransaction
                .getDatabase()
                .rawQuery(
                        "SELECT "
                                + MeasurementTables.AggregateReport.ID
                                + ", "
                                + MeasurementTables.AggregateReport.AGGREGATION_COORDINATOR_ORIGIN
                                + ", "
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " FROM "
                                + MeasurementTables.AggregateReport.TABLE
                                + " LEFT JOIN "
                                + MeasurementTables.KeyValueDataContract.TABLE
                                + " ON ("
                                + MeasurementTables.AggregateReport.ID
                                + " = "
                                + MeasurementTables.KeyValueDataContract.KEY
                                + ") "
                                + " WHERE "
                                + MeasurementTables.AggregateReport.STATUS
                                + " = ? "
                                + "AND (CAST("
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " AS INTEGER) < ? "
                                + "OR "
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " IS NULL)"
                                + " AND "
                                + MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME
                                + " >= ?"
                                + " AND "
                                + MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME
                                + " <= ?"
                                + "AND ("
                                + MeasurementTables.KeyValueDataContract.DATA_TYPE
                                + " = ? "
                                + "OR "
                                + MeasurementTables.KeyValueDataContract.DATA_TYPE
                                + " IS NULL)"
                                + " ORDER BY RANDOM()",
                        new String[] {
                            String.valueOf(AggregateReport.Status.PENDING),
                            String.valueOf(mReportingRetryLimitSupplier.get()),
                            String.valueOf(windowStartTime),
                            String.valueOf(windowEndTime),
                            String.valueOf(DataType.AGGREGATE_REPORT_RETRY_COUNT),
                        });
    }

    private Cursor debugReportIdsCursor() throws DatastoreException {
        return mSQLTransaction
                .getDatabase()
                .query(
                        MeasurementTables.DebugReportContract.TABLE,
                        /* columns= */ null,
                        /* selection= */ null,
                        /* selectionArgs= */ null,
                        /* groupBy= */ null,
                        /* having= */ null,
                        /* orderBy= */ null,
                        /* limit= */ null);
    }

    private Cursor debugReportIdsLimitRetryCursor() throws DatastoreException {
        return mSQLTransaction
                .getDatabase()
                .rawQuery(
                        "SELECT "
                                + MeasurementTables.DebugReportContract.ID
                                + ", "
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " FROM "
                                + MeasurementTables.DebugReportContract.TABLE
                                + " LEFT JOIN "
                                + MeasurementTables.KeyValueDataContract.TABLE
                                + " ON ("
                                + MeasurementTables.DebugReportContract.ID
                                + " = "
                                + MeasurementTables.KeyValueDataContract.KEY
                                + ") "
                                + "WHERE (CAST("
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " AS INTEGER) < ? "
                                + "OR "
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " IS NULL)"
                                + "AND ("
                                + MeasurementTables.KeyValueDataContract.DATA_TYPE
                                + " = ? "
                                + "OR "
                                + MeasurementTables.KeyValueDataContract.DATA_TYPE
                                + " IS NULL)",
                        new String[] {
                            String.valueOf(mReportingRetryLimitSupplier.get()),
                            String.valueOf(DataType.DEBUG_REPORT_RETRY_COUNT.toString()),
                        });
    }

    private Cursor pendingAggregateDebugReportIdsByCoordinatorCursor() throws DatastoreException {
        return mSQLTransaction
                .getDatabase()
                .query(
                        MeasurementTables.AggregateReport.TABLE,
                        /* columns= */ new String[] {
                            MeasurementTables.AggregateReport.ID,
                            MeasurementTables.AggregateReport.AGGREGATION_COORDINATOR_ORIGIN,
                        },
                        MeasurementTables.AggregateReport.DEBUG_REPORT_STATUS + " = ? ",
                        new String[] {String.valueOf(AggregateReport.DebugReportStatus.PENDING)},
                        /* groupBy= */ null,
                        /* having= */ null,
                        /* orderBy= */ "RANDOM()",
                        /* limit= */ null);
    }

    private Cursor pendingAggregateDebugReportIdsByCoordinatorLimitRetryCursor()
            throws DatastoreException {
        return mSQLTransaction
                .getDatabase()
                .rawQuery(
                        "SELECT "
                                + MeasurementTables.AggregateReport.ID
                                + ", "
                                + MeasurementTables.AggregateReport.AGGREGATION_COORDINATOR_ORIGIN
                                + ", "
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " FROM "
                                + MeasurementTables.AggregateReport.TABLE
                                + " LEFT JOIN "
                                + MeasurementTables.KeyValueDataContract.TABLE
                                + " ON ("
                                + MeasurementTables.AggregateReport.ID
                                + " = "
                                + MeasurementTables.KeyValueDataContract.KEY
                                + ") "
                                + " WHERE "
                                + MeasurementTables.AggregateReport.DEBUG_REPORT_STATUS
                                + " = ? "
                                + "AND (CAST("
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " AS INTEGER) < ? "
                                + "OR "
                                + MeasurementTables.KeyValueDataContract.VALUE
                                + " IS NULL)"
                                + "AND ("
                                + MeasurementTables.KeyValueDataContract.DATA_TYPE
                                + " = ? "
                                + "OR "
                                + MeasurementTables.KeyValueDataContract.DATA_TYPE
                                + " IS NULL)"
                                + " ORDER BY RANDOM()",
                        new String[] {
                            String.valueOf(AggregateReport.DebugReportStatus.PENDING),
                            String.valueOf(mReportingRetryLimitSupplier.get()),
                            String.valueOf(DataType.DEBUG_AGGREGATE_REPORT_RETRY_COUNT),
                        });
    }
}
