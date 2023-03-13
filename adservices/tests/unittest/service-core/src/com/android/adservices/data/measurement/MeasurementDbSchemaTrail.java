/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.adservices.data.measurement.MeasurementTables.AggregateEncryptionKey;
import static com.android.adservices.data.measurement.MeasurementTables.AggregateReport;
import static com.android.adservices.data.measurement.MeasurementTables.AsyncRegistrationContract;
import static com.android.adservices.data.measurement.MeasurementTables.AttributionContract;
import static com.android.adservices.data.measurement.MeasurementTables.DebugReportContract;
import static com.android.adservices.data.measurement.MeasurementTables.EventReportContract;
import static com.android.adservices.data.measurement.MeasurementTables.INDEX_PREFIX;
import static com.android.adservices.data.measurement.MeasurementTables.SourceContract;
import static com.android.adservices.data.measurement.MeasurementTables.TriggerContract;
import static com.android.adservices.data.measurement.MeasurementTables.XnaIgnoredSourcesContract;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Has scripts to create database at any version. To introduce migration to a new version x, this
 * class should have one entry each to {@link #CREATE_TABLES_STATEMENTS_BY_VERSION} and {@link
 * #CREATE_INDEXES_STATEMENTS_BY_VERSION} for version x. These entries will cause creation of 2 new
 * methods {@code getCreateStatementByTableVx} and {@code getCreateIndexesVx}, where the previous
 * version's (x-1) scripts will be revised to create scripts for version x.
 */
public class MeasurementDbSchemaTrail {
    private static final String CREATE_TABLE_SOURCE_V6 =
            "CREATE TABLE "
                    + SourceContract.TABLE
                    + " ("
                    + SourceContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + SourceContract.EVENT_ID
                    + " INTEGER, "
                    + SourceContract.PUBLISHER
                    + " TEXT, "
                    + SourceContract.PUBLISHER_TYPE
                    + " INTEGER, "
                    + SourceContract.APP_DESTINATION
                    + " TEXT, "
                    + SourceContract.ENROLLMENT_ID
                    + " TEXT, "
                    + SourceContract.EVENT_TIME
                    + " INTEGER, "
                    + SourceContract.EXPIRY_TIME
                    + " INTEGER, "
                    + SourceContract.EVENT_REPORT_WINDOW
                    + " INTEGER, "
                    + SourceContract.AGGREGATABLE_REPORT_WINDOW
                    + " INTEGER, "
                    + SourceContract.PRIORITY
                    + " INTEGER, "
                    + SourceContract.STATUS
                    + " INTEGER, "
                    + SourceContract.EVENT_REPORT_DEDUP_KEYS
                    + " TEXT, "
                    + SourceContract.AGGREGATE_REPORT_DEDUP_KEYS
                    + " TEXT, "
                    + SourceContract.SOURCE_TYPE
                    + " TEXT, "
                    + SourceContract.REGISTRANT
                    + " TEXT, "
                    + SourceContract.ATTRIBUTION_MODE
                    + " INTEGER, "
                    + SourceContract.INSTALL_ATTRIBUTION_WINDOW
                    + " INTEGER, "
                    + SourceContract.INSTALL_COOLDOWN_WINDOW
                    + " INTEGER, "
                    + SourceContract.IS_INSTALL_ATTRIBUTED
                    + " INTEGER, "
                    + SourceContract.FILTER_DATA
                    + " TEXT, "
                    + SourceContract.AGGREGATE_SOURCE
                    + " TEXT, "
                    + SourceContract.AGGREGATE_CONTRIBUTIONS
                    + " INTEGER, "
                    + SourceContract.WEB_DESTINATION
                    + " TEXT, "
                    + SourceContract.DEBUG_KEY
                    + " INTEGER , "
                    + SourceContract.DEBUG_REPORTING
                    + " INTEGER, "
                    + SourceContract.AD_ID_PERMISSION
                    + " INTEGER, "
                    + SourceContract.AR_DEBUG_PERMISSION
                    + " INTEGER, "
                    + SourceContract.REGISTRATION_ID
                    + " TEXT, "
                    + SourceContract.SHARED_AGGREGATION_KEYS
                    + " TEXT, "
                    + SourceContract.INSTALL_TIME
                    + " INTEGER "
                    + ")";

    private static final String CREATE_TABLE_TRIGGER_V6 =
            "CREATE TABLE "
                    + TriggerContract.TABLE
                    + " ("
                    + TriggerContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + TriggerContract.ATTRIBUTION_DESTINATION
                    + " TEXT, "
                    + TriggerContract.DESTINATION_TYPE
                    + " INTEGER, "
                    + TriggerContract.ENROLLMENT_ID
                    + " TEXT, "
                    + TriggerContract.TRIGGER_TIME
                    + " INTEGER, "
                    + TriggerContract.EVENT_TRIGGERS
                    + " TEXT, "
                    + TriggerContract.STATUS
                    + " INTEGER, "
                    + TriggerContract.REGISTRANT
                    + " TEXT, "
                    + TriggerContract.AGGREGATE_TRIGGER_DATA
                    + " TEXT, "
                    + TriggerContract.AGGREGATE_VALUES
                    + " TEXT, "
                    + TriggerContract.AGGREGATABLE_DEDUPLICATION_KEYS
                    + " TEXT, "
                    + TriggerContract.FILTERS
                    + " TEXT, "
                    + TriggerContract.NOT_FILTERS
                    + " TEXT, "
                    + TriggerContract.DEBUG_KEY
                    + " INTEGER, "
                    + TriggerContract.DEBUG_REPORTING
                    + " INTEGER, "
                    + TriggerContract.AD_ID_PERMISSION
                    + " INTEGER, "
                    + TriggerContract.AR_DEBUG_PERMISSION
                    + " INTEGER, "
                    + TriggerContract.ATTRIBUTION_CONFIG
                    + " TEXT, "
                    + TriggerContract.X_NETWORK_KEY_MAPPING
                    + " TEXT "
                    + ")";

    private static final String CREATE_TABLE_EVENT_REPORT_V6 =
            "CREATE TABLE "
                    + EventReportContract.TABLE
                    + " ("
                    + EventReportContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + EventReportContract.SOURCE_EVENT_ID
                    + " INTEGER, "
                    + EventReportContract.ENROLLMENT_ID
                    + " TEXT, "
                    + EventReportContract.ATTRIBUTION_DESTINATION
                    + " TEXT, "
                    + EventReportContract.REPORT_TIME
                    + " INTEGER, "
                    + EventReportContract.TRIGGER_DATA
                    + " INTEGER, "
                    + EventReportContract.TRIGGER_PRIORITY
                    + " INTEGER, "
                    + EventReportContract.TRIGGER_DEDUP_KEY
                    + " INTEGER, "
                    + EventReportContract.TRIGGER_TIME
                    + " INTEGER, "
                    + EventReportContract.STATUS
                    + " INTEGER, "
                    + EventReportContract.DEBUG_REPORT_STATUS
                    + " INTEGER, "
                    + EventReportContract.SOURCE_TYPE
                    + " TEXT, "
                    + EventReportContract.RANDOMIZED_TRIGGER_RATE
                    + " DOUBLE, "
                    + EventReportContract.SOURCE_DEBUG_KEY
                    + " INTEGER, "
                    + EventReportContract.TRIGGER_DEBUG_KEY
                    + " INTEGER, "
                    + EventReportContract.SOURCE_ID
                    + " TEXT, "
                    + EventReportContract.TRIGGER_ID
                    + " TEXT, "
                    + "FOREIGN KEY ("
                    + EventReportContract.SOURCE_ID
                    + ") REFERENCES "
                    + SourceContract.TABLE
                    + "("
                    + SourceContract.ID
                    + ") ON DELETE CASCADE, "
                    + "FOREIGN KEY ("
                    + EventReportContract.TRIGGER_ID
                    + ") REFERENCES "
                    + TriggerContract.TABLE
                    + "("
                    + TriggerContract.ID
                    + ") ON DELETE CASCADE"
                    + ")";

    private static final String CREATE_TABLE_ATTRIBUTION_V6 =
            "CREATE TABLE "
                    + AttributionContract.TABLE
                    + " ("
                    + AttributionContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + AttributionContract.SOURCE_SITE
                    + " TEXT, "
                    + AttributionContract.SOURCE_ORIGIN
                    + " TEXT, "
                    + AttributionContract.DESTINATION_SITE
                    + " TEXT, "
                    + AttributionContract.DESTINATION_ORIGIN
                    + " TEXT, "
                    + AttributionContract.ENROLLMENT_ID
                    + " TEXT, "
                    + AttributionContract.TRIGGER_TIME
                    + " INTEGER, "
                    + AttributionContract.REGISTRANT
                    + " TEXT, "
                    + AttributionContract.SOURCE_ID
                    + " TEXT, "
                    + AttributionContract.TRIGGER_ID
                    + " TEXT, "
                    + "FOREIGN KEY ("
                    + AttributionContract.SOURCE_ID
                    + ") REFERENCES "
                    + SourceContract.TABLE
                    + "("
                    + SourceContract.ID
                    + ") ON DELETE CASCADE, "
                    + "FOREIGN KEY ("
                    + AttributionContract.TRIGGER_ID
                    + ") REFERENCES "
                    + TriggerContract.TABLE
                    + "("
                    + TriggerContract.ID
                    + ") ON DELETE CASCADE"
                    + ")";

    private static final String CREATE_TABLE_AGGREGATE_REPORT_V6 =
            "CREATE TABLE "
                    + AggregateReport.TABLE
                    + " ("
                    + AggregateReport.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + AggregateReport.PUBLISHER
                    + " TEXT, "
                    + AggregateReport.ATTRIBUTION_DESTINATION
                    + " TEXT, "
                    + AggregateReport.SOURCE_REGISTRATION_TIME
                    + " INTEGER, "
                    + AggregateReport.SCHEDULED_REPORT_TIME
                    + " INTEGER, "
                    + AggregateReport.ENROLLMENT_ID
                    + " TEXT, "
                    + AggregateReport.DEBUG_CLEARTEXT_PAYLOAD
                    + " TEXT, "
                    + AggregateReport.STATUS
                    + " INTEGER, "
                    + AggregateReport.DEBUG_REPORT_STATUS
                    + " INTEGER, "
                    + AggregateReport.API_VERSION
                    + " TEXT, "
                    + AggregateReport.SOURCE_DEBUG_KEY
                    + " INTEGER, "
                    + AggregateReport.TRIGGER_DEBUG_KEY
                    + " INTEGER, "
                    + AggregateReport.SOURCE_ID
                    + " TEXT, "
                    + AggregateReport.TRIGGER_ID
                    + " TEXT, "
                    + "FOREIGN KEY ("
                    + AggregateReport.SOURCE_ID
                    + ") REFERENCES "
                    + SourceContract.TABLE
                    + "("
                    + SourceContract.ID
                    + ") ON DELETE CASCADE "
                    + "FOREIGN KEY ("
                    + AggregateReport.TRIGGER_ID
                    + ") REFERENCES "
                    + TriggerContract.TABLE
                    + "("
                    + TriggerContract.ID
                    + ") ON DELETE CASCADE"
                    + ")";

    private static final String CREATE_TABLE_AGGREGATE_ENCRYPTION_KEY_V6 =
            "CREATE TABLE "
                    + AggregateEncryptionKey.TABLE
                    + " ("
                    + AggregateEncryptionKey.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + AggregateEncryptionKey.KEY_ID
                    + " TEXT, "
                    + AggregateEncryptionKey.PUBLIC_KEY
                    + " TEXT, "
                    + AggregateEncryptionKey.EXPIRY
                    + " INTEGER "
                    + ")";

    private static final String CREATE_TABLE_ASYNC_REGISTRATION_V6 =
            "CREATE TABLE "
                    + AsyncRegistrationContract.TABLE
                    + " ("
                    + AsyncRegistrationContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + AsyncRegistrationContract.ENROLLMENT_ID
                    + " TEXT, "
                    + AsyncRegistrationContract.REGISTRATION_URI
                    + " TEXT, "
                    + AsyncRegistrationContract.WEB_DESTINATION
                    + " TEXT, "
                    + AsyncRegistrationContract.OS_DESTINATION
                    + " TEXT, "
                    + AsyncRegistrationContract.VERIFIED_DESTINATION
                    + " TEXT, "
                    + AsyncRegistrationContract.TOP_ORIGIN
                    + " TEXT, "
                    + AsyncRegistrationContract.REDIRECT_TYPE
                    + " INTEGER, "
                    + AsyncRegistrationContract.REDIRECT_COUNT
                    + " INTEGER, "
                    + AsyncRegistrationContract.SOURCE_TYPE
                    + " INTEGER, "
                    + AsyncRegistrationContract.REGISTRANT
                    + " TEXT, "
                    + AsyncRegistrationContract.REQUEST_TIME
                    + " INTEGER, "
                    + AsyncRegistrationContract.RETRY_COUNT
                    + " INTEGER, "
                    + AsyncRegistrationContract.LAST_PROCESSING_TIME
                    + " INTEGER, "
                    + AsyncRegistrationContract.TYPE
                    + " INTEGER, "
                    + AsyncRegistrationContract.DEBUG_KEY_ALLOWED
                    + " INTEGER, "
                    + AsyncRegistrationContract.AD_ID_PERMISSION
                    + " INTEGER, "
                    + AsyncRegistrationContract.REGISTRATION_ID
                    + " TEXT "
                    + ")";

    private static final String CREATE_TABLE_DEBUG_REPORT_V6 =
            "CREATE TABLE IF NOT EXISTS "
                    + DebugReportContract.TABLE
                    + " ("
                    + DebugReportContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + DebugReportContract.TYPE
                    + " TEXT, "
                    + DebugReportContract.BODY
                    + " TEXT, "
                    + DebugReportContract.ENROLLMENT_ID
                    + " TEXT "
                    + ")";

    private static final String CREATE_TABLE_XNA_IGNORED_SOURCES_V6 =
            "CREATE TABLE "
                    + XnaIgnoredSourcesContract.TABLE
                    + " ("
                    + XnaIgnoredSourcesContract.SOURCE_ID
                    + " TEXT NOT NULL, "
                    + XnaIgnoredSourcesContract.ENROLLMENT_ID
                    + " TEXT NOT NULL, "
                    + "FOREIGN KEY ("
                    + XnaIgnoredSourcesContract.SOURCE_ID
                    + ") REFERENCES "
                    + SourceContract.TABLE
                    + "("
                    + SourceContract.ID
                    + ") ON DELETE CASCADE"
                    + ")";

    private static final Map<String, String> CREATE_STATEMENT_BY_TABLE_V6 =
            ImmutableMap.of(
                    SourceContract.TABLE, CREATE_TABLE_SOURCE_V6,
                    TriggerContract.TABLE, CREATE_TABLE_TRIGGER_V6,
                    EventReportContract.TABLE, CREATE_TABLE_EVENT_REPORT_V6,
                    AggregateReport.TABLE, CREATE_TABLE_AGGREGATE_REPORT_V6,
                    AttributionContract.TABLE, CREATE_TABLE_ATTRIBUTION_V6,
                    AggregateEncryptionKey.TABLE, CREATE_TABLE_AGGREGATE_ENCRYPTION_KEY_V6,
                    AsyncRegistrationContract.TABLE, CREATE_TABLE_ASYNC_REGISTRATION_V6,
                    DebugReportContract.TABLE, CREATE_TABLE_DEBUG_REPORT_V6,
                    XnaIgnoredSourcesContract.TABLE, CREATE_TABLE_XNA_IGNORED_SOURCES_V6);

    private static final List<String> CREATE_INDEXES_V6 =
            ImmutableList.of(
                    "CREATE INDEX "
                            + INDEX_PREFIX
                            + SourceContract.TABLE
                            + "_ad_ei_et "
                            + "ON "
                            + SourceContract.TABLE
                            + "( "
                            + SourceContract.APP_DESTINATION
                            + ", "
                            + SourceContract.ENROLLMENT_ID
                            + ", "
                            + SourceContract.EXPIRY_TIME
                            + " DESC "
                            + ")",
                    "CREATE INDEX "
                            + INDEX_PREFIX
                            + SourceContract.TABLE
                            + "_et "
                            + "ON "
                            + SourceContract.TABLE
                            + "("
                            + SourceContract.EXPIRY_TIME
                            + ")",
                    "CREATE INDEX "
                            + INDEX_PREFIX
                            + SourceContract.TABLE
                            + "_p_ad_wd_s_et "
                            + "ON "
                            + SourceContract.TABLE
                            + "("
                            + SourceContract.PUBLISHER
                            + ", "
                            + SourceContract.APP_DESTINATION
                            + ", "
                            + SourceContract.WEB_DESTINATION
                            + ", "
                            + SourceContract.STATUS
                            + ", "
                            + SourceContract.EVENT_TIME
                            + ")",
                    "CREATE INDEX "
                            + INDEX_PREFIX
                            + TriggerContract.TABLE
                            + "_ad_ei_tt "
                            + "ON "
                            + TriggerContract.TABLE
                            + "( "
                            + TriggerContract.ATTRIBUTION_DESTINATION
                            + ", "
                            + TriggerContract.ENROLLMENT_ID
                            + ", "
                            + TriggerContract.TRIGGER_TIME
                            + " ASC)",
                    "CREATE INDEX "
                            + INDEX_PREFIX
                            + TriggerContract.TABLE
                            + "_tt "
                            + "ON "
                            + TriggerContract.TABLE
                            + "("
                            + TriggerContract.TRIGGER_TIME
                            + ")",
                    "CREATE INDEX "
                            + INDEX_PREFIX
                            + AttributionContract.TABLE
                            + "_ss_so_ds_do_ei_tt"
                            + " ON "
                            + AttributionContract.TABLE
                            + "("
                            + AttributionContract.SOURCE_SITE
                            + ", "
                            + AttributionContract.SOURCE_ORIGIN
                            + ", "
                            + AttributionContract.DESTINATION_SITE
                            + ", "
                            + AttributionContract.DESTINATION_ORIGIN
                            + ", "
                            + AttributionContract.ENROLLMENT_ID
                            + ", "
                            + AttributionContract.TRIGGER_TIME
                            + ")");

    private static final String[] CREATE_INDEXES_V6_V7 = {
        "CREATE INDEX "
                + "idx_"
                + MeasurementTables.SourceContract.TABLE
                + "_ei "
                + "ON "
                + MeasurementTables.SourceContract.TABLE
                + "("
                + MeasurementTables.SourceContract.ENROLLMENT_ID
                + ")",
        "CREATE INDEX "
                + "idx_"
                + MeasurementTables.XnaIgnoredSourcesContract.TABLE
                + "_ei "
                + "ON "
                + MeasurementTables.XnaIgnoredSourcesContract.TABLE
                + "("
                + MeasurementTables.XnaIgnoredSourcesContract.ENROLLMENT_ID
                + ")"
    };

    private static Map<String, String> getCreateStatementByTableV7() {
        return CREATE_STATEMENT_BY_TABLE_V6;
    }

    private static List<String> getCreateIndexesV7() {
        ArrayList<String> createIndexes = new ArrayList<>(CREATE_INDEXES_V6);
        createIndexes.addAll(Arrays.asList(CREATE_INDEXES_V6_V7));
        return createIndexes;
    }

    private static final Map<Integer, Map<String, String>> CREATE_TABLES_STATEMENTS_BY_VERSION =
            new ImmutableMap.Builder<Integer, Map<String, String>>()
                    .put(6, CREATE_STATEMENT_BY_TABLE_V6)
                    .put(7, getCreateStatementByTableV7())
                    .build();

    private static final Map<Integer, List<String>> CREATE_INDEXES_STATEMENTS_BY_VERSION =
            new ImmutableMap.Builder<Integer, List<String>>()
                    .put(6, CREATE_INDEXES_V6)
                    .put(7, getCreateIndexesV7())
                    .build();

    /**
     * Returns a map of table to the respective create statement at the provided version. Supports
     * only 6+ versions.
     *
     * @param version version for which create statements are requested
     * @return map of table to their create statement
     */
    public static Map<String, String> getCreateTableStatementsByVersion(int version) {
        if (version < 6) {
            throw new IllegalArgumentException("Unsupported version " + version);
        }

        return CREATE_TABLES_STATEMENTS_BY_VERSION.get(version);
    }

    /**
     * Returns a list create index statement at the provided version. Supports only 6+ versions.
     *
     * @param version version for which create index statements are requested
     * @return list of create index statements
     */
    public static List<String> getCreateIndexStatementsByVersion(int version) {
        if (version < 6) {
            throw new IllegalArgumentException("Unsupported version " + version);
        }

        return CREATE_INDEXES_STATEMENTS_BY_VERSION.get(version);
    }
}
