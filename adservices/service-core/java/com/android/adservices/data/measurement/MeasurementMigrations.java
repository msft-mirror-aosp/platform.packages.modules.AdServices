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

import static com.android.adservices.data.measurement.MeasurementTables.AttributionRateLimitContract;
import static com.android.adservices.data.measurement.MeasurementTables.EventReportContract;
import static com.android.adservices.data.measurement.MeasurementTables.INDEX_PREFIX;
import static com.android.adservices.data.measurement.MeasurementTables.SourceContract;
import static com.android.adservices.data.measurement.MeasurementTables.TriggerContract;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Container class for Measurement PPAPI table migrations.
 */
public final class MeasurementMigrations {

    private static final String[] DROP_INDEXES_VER_2 = {
            "DROP INDEX "
                    + INDEX_PREFIX + SourceContract.TABLE + "_ad_rt_et",
            "DROP INDEX "
                    + INDEX_PREFIX + TriggerContract.TABLE + "_ad_rt_tt",
            "DROP INDEX "
                    + INDEX_PREFIX + AttributionRateLimitContract.TABLE + "_ss_ds_tt"
    };

    private static final String[] ALTER_STATEMENTS_VER_2 = {
            String.format("ALTER TABLE %1$s ADD %2$s TEXT",
                    SourceContract.TABLE,
                    SourceContract.FILTER_DATA),
            String.format("ALTER TABLE %1$s ADD %2$s TEXT",
                    SourceContract.TABLE,
                    SourceContract.AGGREGATE_SOURCE),
            String.format("ALTER TABLE %1$s RENAME COLUMN %2$s TO %3$s",
                    SourceContract.TABLE,
                    SourceContract.DEPRECATED_ATTRIBUTION_SOURCE,
                    SourceContract.PUBLISHER),
            String.format("ALTER TABLE %1$s RENAME COLUMN %2$s TO %3$s",
                    SourceContract.TABLE,
                    SourceContract.DEPRECATED_REPORT_TO,
                    SourceContract.AD_TECH_DOMAIN),

            String.format("ALTER TABLE %1$s ADD %2$s TEXT",
                    TriggerContract.TABLE,
                    TriggerContract.AGGREGATE_TRIGGER_DATA),
            String.format("ALTER TABLE %1$s ADD %2$s TEXT",
                    TriggerContract.TABLE,
                    TriggerContract.AGGREGATE_VALUES),
            String.format("ALTER TABLE %1$s RENAME COLUMN %2$s TO %3$s",
                    TriggerContract.TABLE,
                    TriggerContract.DEPRECATED_TRIGGER_DATA,
                    TriggerContract.EVENT_TRIGGER_DATA),
            String.format("ALTER TABLE %1$s RENAME COLUMN %2$s TO %3$s",
                    TriggerContract.TABLE,
                    TriggerContract.DEPRECATED_REPORT_TO,
                    TriggerContract.AD_TECH_DOMAIN),

            String.format("ALTER TABLE %1$s RENAME COLUMN %2$s TO %3$s",
                    EventReportContract.TABLE,
                    EventReportContract.DEPRECATED_REPORT_TO,
                    EventReportContract.AD_TECH_DOMAIN),

            String.format("ALTER TABLE %1$s RENAME COLUMN %2$s TO %3$s",
                    AttributionRateLimitContract.TABLE,
                    AttributionRateLimitContract.DEPRECATED_REPORT_TO,
                    AttributionRateLimitContract.AD_TECH_DOMAIN),
    };

    private static final String[] CREATE_INDEXES_VER_2 = {
            "CREATE INDEX "
                    + INDEX_PREFIX + SourceContract.TABLE + "_ad_rt_et " + "ON "
                    + SourceContract.TABLE + "( "
                    + SourceContract.ATTRIBUTION_DESTINATION + ", "
                    + SourceContract.AD_TECH_DOMAIN + ", "
                    + SourceContract.EXPIRY_TIME + " DESC " + ")",
            "CREATE INDEX "
                    + INDEX_PREFIX + TriggerContract.TABLE + "_ad_rt_tt " + "ON "
                    + TriggerContract.TABLE + "( "
                    + TriggerContract.ATTRIBUTION_DESTINATION + ", "
                    + TriggerContract.AD_TECH_DOMAIN + ", "
                    + TriggerContract.TRIGGER_TIME + " ASC)",
            "CREATE INDEX "
                    + INDEX_PREFIX + AttributionRateLimitContract.TABLE + "_ss_ds_tt" + " ON "
                    + AttributionRateLimitContract.TABLE + "("
                    + AttributionRateLimitContract.SOURCE_SITE + ", "
                    + AttributionRateLimitContract.DESTINATION_SITE + ", "
                    + AttributionRateLimitContract.AD_TECH_DOMAIN + ", "
                    + AttributionRateLimitContract.TRIGGER_TIME + ")"
    };

    private MeasurementMigrations() {
    }

    /**
     * Consolidated migration scripts that will execute on the following tables:
     *
     * Source
     * <ul>
     *     <li>Rename : attribution_source to publisher</li>
     *     <li>Rename : report_to to ad_tech_domain</li>
     *     <li>Add : filter_data</li>
     *     <li>Add : aggregate_source</li>
     * </ul>
     *
     * Trigger
     * <ul>
     *     <li>Rename : report_to to ad_tech_domain</li>
     *     <li>Rename : trigger_data to event_trigger_data</li>
     *     <li>Add : aggregate_trigger_data</li>
     *     <li>Add : aggregate_values</li>
     * </ul>
     *
     * Event Report
     * <ul>
     *     <li>Rename : report_to to ad_tech_domain</li>
     * </ul>
     *
     * Attribution Rate Limit
     * <ul>
     *     <li>Rename : report_to to ad_tech_domain</li>
     * </ul>
     *
     * <p>Also updates indexes to use the updated field names</p>
     *
     * @return consolidated scripts to migrate to version 2
     */
    public static String[] migrationScriptVersion2() {
        return Stream.of(
                        DROP_INDEXES_VER_2,
                        ALTER_STATEMENTS_VER_2,
                        CREATE_INDEXES_VER_2
                )
                .flatMap(Arrays::stream)
                .toArray(String[]::new);
    }
}
