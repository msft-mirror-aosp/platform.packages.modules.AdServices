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

package com.android.adservices.data.measurement.migration;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.service.measurement.util.BaseUriExtractor;
import com.android.adservices.service.measurement.util.Web;

import java.util.Optional;

/** Migrates Measurement DB from user version 2 to 3. */
public class MeasurementDbMigratorV3 extends AbstractMeasurementDbMigrator {
    private static final String ANDROID_APP_SCHEME = "android-app";
    private static final String EVENT_REPORT_CONTRACT_BACKUP =
            MeasurementTables.EventReportContract.TABLE + "_backup";
    private static final String AGGREGATE_REPORT_CONTRACT_BACKUP =
            MeasurementTables.AggregateReport.TABLE + "_backup";
    private static final String ATTRIBUTION_REPORT_CONTRACT_BACKUP =
            MeasurementTables.AttributionContract.TABLE + "_backup";

    private static final String[] ALTER_STATEMENTS_VER_3 = {
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.AsyncRegistrationContract.TABLE,
                MeasurementTables.AsyncRegistrationContract.DEBUG_KEY_ALLOWED),
        String.format(
                "ALTER TABLE %1$s ADD %2$s TEXT",
                MeasurementTables.AsyncRegistrationContract.TABLE,
                MeasurementTables.AsyncRegistrationContract.ENROLLMENT_ID),
        String.format(
                "ALTER TABLE %1$s " + "RENAME COLUMN %2$s TO %3$s",
                MeasurementTables.AsyncRegistrationContract.TABLE,
                MeasurementTablesDeprecated.AsyncRegistration.INPUT_EVENT,
                MeasurementTables.AsyncRegistrationContract.SOURCE_TYPE),
        String.format(
                "ALTER TABLE %1$s " + "RENAME COLUMN %2$s TO %3$s",
                MeasurementTables.AsyncRegistrationContract.TABLE,
                MeasurementTablesDeprecated.AsyncRegistration.REDIRECT,
                MeasurementTables.AsyncRegistrationContract.REDIRECT_TYPE),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.AsyncRegistrationContract.TABLE,
                MeasurementTables.AsyncRegistrationContract.REDIRECT_COUNT),
        String.format(
                "ALTER TABLE %1$s RENAME COLUMN %2$s TO %3$s",
                MeasurementTables.EventReportContract.TABLE,
                MeasurementTables.EventReportContract.SOURCE_ID,
                MeasurementTables.EventReportContract.SOURCE_EVENT_ID),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.EventReportContract.TABLE,
                MeasurementTables.EventReportContract.SOURCE_ID),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.EventReportContract.TABLE,
                MeasurementTables.EventReportContract.TRIGGER_ID),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.AggregateReport.TABLE,
                MeasurementTables.AggregateReport.SOURCE_ID),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.AggregateReport.TABLE,
                MeasurementTables.AggregateReport.TRIGGER_ID),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.AttributionContract.TABLE,
                MeasurementTables.AttributionContract.SOURCE_ID),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.AttributionContract.TABLE,
                MeasurementTables.AttributionContract.TRIGGER_ID),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.EventReportContract.TABLE,
                MeasurementTables.EventReportContract.DEBUG_REPORT_STATUS),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.AggregateReport.TABLE,
                MeasurementTables.AggregateReport.DEBUG_REPORT_STATUS),

        // SQLite does not support ALTER TABLE statement with foreign keys
        String.format(
                "ALTER TABLE %1$s RENAME TO %2$s",
                MeasurementTables.EventReportContract.TABLE, EVENT_REPORT_CONTRACT_BACKUP),
        MeasurementTables.CREATE_TABLE_EVENT_REPORT_V3,
        String.format(
                "INSERT INTO %1$s SELECT * FROM %2$s",
                MeasurementTables.EventReportContract.TABLE, EVENT_REPORT_CONTRACT_BACKUP),
        String.format("DROP TABLE %1$s", EVENT_REPORT_CONTRACT_BACKUP),
        String.format(
                "ALTER TABLE %1$s RENAME TO %2$s",
                MeasurementTables.AggregateReport.TABLE, AGGREGATE_REPORT_CONTRACT_BACKUP),
        MeasurementTables.CREATE_TABLE_AGGREGATE_REPORT_V3,
        String.format(
                "INSERT INTO %1$s SELECT * FROM %2$s",
                MeasurementTables.AggregateReport.TABLE, AGGREGATE_REPORT_CONTRACT_BACKUP),
        String.format("DROP TABLE %1$s", AGGREGATE_REPORT_CONTRACT_BACKUP),
        String.format(
                "ALTER TABLE %1$s RENAME TO %2$s",
                MeasurementTables.AttributionContract.TABLE, ATTRIBUTION_REPORT_CONTRACT_BACKUP),
        MeasurementTables.CREATE_TABLE_ATTRIBUTION_V3,
        String.format(
                "INSERT INTO %1$s SELECT * FROM %2$s",
                MeasurementTables.AttributionContract.TABLE, ATTRIBUTION_REPORT_CONTRACT_BACKUP),
        String.format("DROP TABLE %1$s", ATTRIBUTION_REPORT_CONTRACT_BACKUP),
    };

    public MeasurementDbMigratorV3() {
        super(3);
    }

    @Override
    protected void performMigration(SQLiteDatabase db) {
        for (String sql : ALTER_STATEMENTS_VER_3) {
            db.execSQL(sql);
        }
        migrateEventReportData(db);
    }

    private static void migrateEventReportData(SQLiteDatabase db) {
        try (Cursor cursor = db.query(
                    MeasurementTables.EventReportContract.TABLE,
                    new String[] {
                        MeasurementTables.EventReportContract.ID,
                        MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION
                    },
                    null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                updateEventReport(db, cursor);
            }
        }
    }

    private static void updateEventReport(SQLiteDatabase db, Cursor cursor) {
        String id = cursor.getString(cursor.getColumnIndex(
                MeasurementTables.EventReportContract.ID));
        String destination = cursor.getString(cursor.getColumnIndex(
                MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION));
        Optional<String> baseUri = extractBaseUri(destination);
        if (baseUri.isPresent()) {
            ContentValues values = new ContentValues();
            values.put(MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION,
                    baseUri.get());
            long rowCount = db.update(
                    MeasurementTables.EventReportContract.TABLE,
                    values,
                    MeasurementTables.EventReportContract.ID + " = ?",
                    new String[]{id});
            if (rowCount != 1) {
                LogUtil.d("MeasurementDbMigratorV3: failed to update event report record.");
            }
        } else {
            LogUtil.d("MeasurementDbMigratorV3: baseUri not present. %s", destination);
        }
    }

    private static Optional<String> extractBaseUri(String destination) {
        if (destination == null) {
            return Optional.empty();
        }
        Uri uri = Uri.parse(destination);
        if (uri.getScheme() == null || !uri.isHierarchical() || !uri.isAbsolute()) {
            return Optional.empty();
        }
        if (uri.getScheme().equals(ANDROID_APP_SCHEME)) {
            return Optional.of(BaseUriExtractor.getBaseUri(uri).toString());
        }
        Optional<Uri> topPrivateDomainAndScheme = Web.topPrivateDomainAndScheme(uri);
        if (topPrivateDomainAndScheme.isPresent()) {
            return Optional.of(topPrivateDomainAndScheme.get().toString());
        }
        return Optional.empty();
    }
}
