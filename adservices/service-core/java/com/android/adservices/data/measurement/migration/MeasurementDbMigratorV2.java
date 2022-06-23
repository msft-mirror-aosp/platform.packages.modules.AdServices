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

import static com.android.adservices.data.measurement.MeasurementTables.INDEX_PREFIX;

import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementTables;

import java.util.Arrays;
import java.util.stream.Stream;

/** Migrates Measurement DB from user version 1 (base) to 2. */
public class MeasurementDbMigratorV2 extends AbstractMeasurementDbMigrator {

    private static final String[] DROP_INDEXES_VER_2 = {
        "DROP INDEX " + INDEX_PREFIX + MeasurementTables.SourceContract.TABLE + "_ad_rt_et",
        "DROP INDEX " + INDEX_PREFIX + MeasurementTables.TriggerContract.TABLE + "_ad_rt_tt",
        "DROP INDEX "
                + INDEX_PREFIX
                + MeasurementTables.AttributionRateLimitContract.TABLE
                + "_ss_ds_tt"
    };

    private static final String[] ALTER_STATEMENTS_VER_2 = {
        String.format(
                "ALTER TABLE %1$s ADD %2$s TEXT",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.FILTER_DATA),
        String.format(
                "ALTER TABLE %1$s ADD %2$s TEXT",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.AGGREGATE_SOURCE),
        String.format(
                "ALTER TABLE %1$s RENAME COLUMN %2$s TO %3$s",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.DEPRECATED_ATTRIBUTION_SOURCE,
                MeasurementTables.SourceContract.PUBLISHER),
        String.format(
                "ALTER TABLE %1$s RENAME COLUMN %2$s TO %3$s",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.DEPRECATED_REPORT_TO,
                MeasurementTables.SourceContract.AD_TECH_DOMAIN),
        String.format(
                "ALTER TABLE %1$s ADD %2$s TEXT",
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA),
        String.format(
                "ALTER TABLE %1$s ADD %2$s TEXT",
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.AGGREGATE_VALUES),
        String.format(
                "ALTER TABLE %1$s RENAME COLUMN %2$s TO %3$s",
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.DEPRECATED_TRIGGER_DATA,
                MeasurementTables.TriggerContract.DEPRECATED_EVENT_TRIGGER_DATA),
        String.format(
                "ALTER TABLE %1$s RENAME COLUMN %2$s TO %3$s",
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.DEPRECATED_REPORT_TO,
                MeasurementTables.TriggerContract.AD_TECH_DOMAIN),
        String.format(
                "ALTER TABLE %1$s RENAME COLUMN %2$s TO %3$s",
                MeasurementTables.EventReportContract.TABLE,
                MeasurementTables.EventReportContract.DEPRECATED_REPORT_TO,
                MeasurementTables.EventReportContract.AD_TECH_DOMAIN),
        String.format(
                "ALTER TABLE %1$s ADD %2$s DOUBLE",
                MeasurementTables.EventReportContract.TABLE,
                MeasurementTables.EventReportContract.RANDOMIZED_TRIGGER_RATE),
        String.format(
                "ALTER TABLE %1$s RENAME COLUMN %2$s TO %3$s",
                MeasurementTables.AttributionRateLimitContract.TABLE,
                MeasurementTables.AttributionRateLimitContract.DEPRECATED_REPORT_TO,
                MeasurementTables.AttributionRateLimitContract.AD_TECH_DOMAIN),
    };

    private static final String[] CREATE_INDEXES_VER_2 = {
        "CREATE INDEX "
                + INDEX_PREFIX
                + MeasurementTables.SourceContract.TABLE
                + "_ad_rt_et "
                + "ON "
                + MeasurementTables.SourceContract.TABLE
                + "( "
                + MeasurementTables.SourceContract.ATTRIBUTION_DESTINATION
                + ", "
                + MeasurementTables.SourceContract.AD_TECH_DOMAIN
                + ", "
                + MeasurementTables.SourceContract.EXPIRY_TIME
                + " DESC "
                + ")",
        "CREATE INDEX "
                + INDEX_PREFIX
                + MeasurementTables.TriggerContract.TABLE
                + "_ad_rt_tt "
                + "ON "
                + MeasurementTables.TriggerContract.TABLE
                + "( "
                + MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION
                + ", "
                + MeasurementTables.TriggerContract.AD_TECH_DOMAIN
                + ", "
                + MeasurementTables.TriggerContract.TRIGGER_TIME
                + " ASC)",
        "CREATE INDEX "
                + INDEX_PREFIX
                + MeasurementTables.AttributionRateLimitContract.TABLE
                + "_ss_ds_tt"
                + " ON "
                + MeasurementTables.AttributionRateLimitContract.TABLE
                + "("
                + MeasurementTables.AttributionRateLimitContract.SOURCE_SITE
                + ", "
                + MeasurementTables.AttributionRateLimitContract.DESTINATION_SITE
                + ", "
                + MeasurementTables.AttributionRateLimitContract.AD_TECH_DOMAIN
                + ", "
                + MeasurementTables.AttributionRateLimitContract.TRIGGER_TIME
                + ")"
    };

    public MeasurementDbMigratorV2() {
        super(2);
    }

    @Override
    protected void performMigration(SQLiteDatabase db) {
        for (String sql : migrationScriptVersion2()) {
            db.execSQL(sql);
        }
    }

    /**
     * Consolidated migration scripts that will execute on the following tables:
     *
     * <p>Source
     *
     * <ul>
     *   <li>Rename : attribution_source to publisher
     *   <li>Rename : report_to to ad_tech_domain
     *   <li>Add : filter_data
     *   <li>Add : aggregate_source
     * </ul>
     *
     * Trigger
     *
     * <ul>
     *   <li>Rename : report_to to ad_tech_domain
     *   <li>Rename : trigger_data to event_trigger_data
     *   <li>Add : aggregate_trigger_data
     *   <li>Add : aggregate_values
     * </ul>
     *
     * Event Report
     *
     * <ul>
     *   <li>Add : randomized_trigger_rate
     *   <li>Rename : report_to to ad_tech_domain
     * </ul>
     *
     * Attribution Rate Limit
     *
     * <ul>
     *   <li>Rename : report_to to ad_tech_domain
     * </ul>
     *
     * <p>Also updates indexes to use the updated field names
     *
     * @return consolidated scripts to migrate to version 2
     */
    public static String[] migrationScriptVersion2() {
        return Stream.of(
                        DROP_INDEXES_VER_2,
                        ALTER_STATEMENTS_VER_2,
                        CREATE_INDEXES_VER_2,
                        new String[] {MeasurementTables.CREATE_TABLE_AGGREGATE_REPORT})
                .flatMap(Arrays::stream)
                .toArray(String[]::new);
    }
}
