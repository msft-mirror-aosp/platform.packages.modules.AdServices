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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Container class for Measurement PPAPI table definitions and constants.
 */
public final class MeasurementTables {
    static final String MSMT_TABLE_PREFIX = "msmt_";
    static final String INDEX_PREFIX = "idx_";

    /**
     * Contract for Source.
     */
    interface SourceContract {
        String TABLE = MSMT_TABLE_PREFIX + "source";
        String ID = "_id";
        String EVENT_ID = "event_id";
        String ATTRIBUTION_SOURCE = "attribution_source";
        String ATTRIBUTION_DESTINATION = "attribution_destination";
        String REPORT_TO = "report_to";
        String DEDUP_KEYS = "dedup_keys";
        String EVENT_TIME = "event_time";
        String EXPIRY_TIME = "expiry_time";
        String PRIORITY = "priority";
        String STATUS = "status";
        String SOURCE_TYPE = "source_type";
        String REGISTERER = "registerer";
        String ATTRIBUTION_MODE = "attribution_mode";
    }

    /**
     * Contract for Trigger.
     */
    interface TriggerContract {
        String TABLE = MSMT_TABLE_PREFIX + "trigger";
        String ID = "_id";
        String ATTRIBUTION_DESTINATION = "attribution_destination";
        String REPORT_TO = "report_to";
        String TRIGGER_TIME = "trigger_time";
        String TRIGGER_DATA = "trigger_data";
        String DEDUP_KEY = "deduplication_key";
        String PRIORITY = "priority";
        String STATUS = "status";
        String REGISTERER = "registerer";
    }

    interface AdTechUrlsContract {
        String TABLE = MSMT_TABLE_PREFIX + "adtech_urls";
        String POSTBACK_URL = "postback_url";
        String AD_TECH_ID = "ad_tech_id";
    }

    /**
     * Contract for EventReport.
     */
    interface EventReportContract {
        String TABLE = MSMT_TABLE_PREFIX + "event_report";
        String ID = "_id";
        String SOURCE_ID = "source_id";
        String REPORT_TO = "report_to";
        String ATTRIBUTION_DESTINATION = "attribution_destination";
        String REPORT_TIME = "report_time";
        String TRIGGER_DATA = "trigger_data";
        String TRIGGER_PRIORITY = "trigger_priority";
        String TRIGGER_DEDUP_KEY = "trigger_dedup_key";
        String TRIGGER_TIME = "trigger_time";
        String STATUS = "status";
    }

    /**
     * Contract for Attribution rate limit.
     */
    interface AttributionRateLimitContract {
        String TABLE = MSMT_TABLE_PREFIX + "attribution_rate_limit";
        String ID = "_id";
        String SOURCE_SITE = "source_site";
        String DESTINATION_SITE = "attribution_destination_site";
        // TODO: Revisit this column to handle multiple reporting endpoints per AdTech.
        String REPORT_TO = "report_to";
        String TRIGGER_TIME = "trigger_time";
        String REGISTERER = "registerer";
    }

    public static final String CREATE_TABLE_SOURCE =
            "CREATE TABLE "
                    + SourceContract.TABLE
                    + " ("
                    + SourceContract.ID + " TEXT PRIMARY KEY NOT NULL, "
                    + SourceContract.EVENT_ID + " INTEGER, "
                    + SourceContract.ATTRIBUTION_SOURCE + " TEXT, "
                    + SourceContract.ATTRIBUTION_DESTINATION + " TEXT, "
                    + SourceContract.REPORT_TO + " TEXT, "
                    + SourceContract.EVENT_TIME + " INTEGER, "
                    + SourceContract.EXPIRY_TIME + " INTEGER, "
                    + SourceContract.PRIORITY + " INTEGER, "
                    + SourceContract.STATUS + " INTEGER, "
                    + SourceContract.DEDUP_KEYS + " TEXT, "
                    + SourceContract.SOURCE_TYPE + " TEXT, "
                    + SourceContract.REGISTERER + " TEXT, "
                    + SourceContract.ATTRIBUTION_MODE + " INTEGER "
                    + ")";

    public static final String CREATE_TABLE_TRIGGER =
            "CREATE TABLE "
                    + TriggerContract.TABLE
                    + " ("
                    + TriggerContract.ID + " TEXT PRIMARY KEY NOT NULL, "
                    + TriggerContract.ATTRIBUTION_DESTINATION + " TEXT, "
                    + TriggerContract.REPORT_TO + " TEXT, "
                    + TriggerContract.TRIGGER_TIME + " INTEGER, "
                    + TriggerContract.TRIGGER_DATA + " INTEGER, "
                    + TriggerContract.PRIORITY + " INTEGER, "
                    + TriggerContract.DEDUP_KEY + " TEXT, "
                    + TriggerContract.STATUS + " INTEGER, "
                    + TriggerContract.REGISTERER + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_ADTECH_URLS =
            "CREATE TABLE "
                    + AdTechUrlsContract.TABLE
                    + " ("
                    + AdTechUrlsContract.POSTBACK_URL + " TEXT PRIMARY KEY, "
                    + AdTechUrlsContract.AD_TECH_ID + " TEXT"
                    + ")";

    public static final String CREATE_TABLE_EVENT_REPORT =
            "CREATE TABLE "
                    + EventReportContract.TABLE
                    + " ("
                    + EventReportContract.ID + " TEXT PRIMARY KEY NOT NULL, "
                    + EventReportContract.SOURCE_ID + " INTEGER, "
                    + EventReportContract.REPORT_TO + " TEXT, "
                    + EventReportContract.ATTRIBUTION_DESTINATION + " TEXT, "
                    + EventReportContract.REPORT_TIME + " INTEGER, "
                    + EventReportContract.TRIGGER_DATA + " INTEGER, "
                    + EventReportContract.TRIGGER_PRIORITY + " INTEGER, "
                    + EventReportContract.TRIGGER_DEDUP_KEY + " INTEGER, "
                    + EventReportContract.TRIGGER_TIME + " INTEGER, "
                    + EventReportContract.STATUS + " INTEGER "
                    + ")";

    public static final String CREATE_TABLE_ATTRIBUTION_RATE_LIMIT =
            "CREATE TABLE "
                    + AttributionRateLimitContract.TABLE
                    + " ("
                    + AttributionRateLimitContract.ID + " TEXT PRIMARY KEY NOT NULL, "
                    + AttributionRateLimitContract.SOURCE_SITE + " TEXT, "
                    + AttributionRateLimitContract.DESTINATION_SITE + " TEXT, "
                    + AttributionRateLimitContract.REPORT_TO + " TEXT, "
                    + AttributionRateLimitContract.TRIGGER_TIME + " INTEGER, "
                    + AttributionRateLimitContract.REGISTERER + " TEXT "
                    + ")";

    public static final String[] CREATE_INDICES = {
            "CREATE INDEX "
                    + INDEX_PREFIX + SourceContract.TABLE + "_ad_rt_et " + "ON "
                    + SourceContract.TABLE + "( "
                    + SourceContract.ATTRIBUTION_DESTINATION + ", "
                    + SourceContract.REPORT_TO + ", "
                    + SourceContract.EXPIRY_TIME + " DESC " + ")",
            "CREATE INDEX "
                    + INDEX_PREFIX + TriggerContract.TABLE + "_ad_rt_tt " + "ON "
                    + TriggerContract.TABLE + "( "
                    + TriggerContract.ATTRIBUTION_DESTINATION + ", "
                    + TriggerContract.REPORT_TO + ", "
                    + TriggerContract.TRIGGER_TIME + " ASC)",
            "CREATE INDEX "
                    + INDEX_PREFIX + SourceContract.TABLE + "_et " + "ON "
                    + SourceContract.TABLE + "("
                    + SourceContract.EXPIRY_TIME + ")",
            "CREATE INDEX "
                    + INDEX_PREFIX + TriggerContract.TABLE + "_tt " + "ON "
                    + TriggerContract.TABLE + "("
                    + TriggerContract.TRIGGER_TIME + ")",
            "CREATE INDEX "
                    + INDEX_PREFIX + AttributionRateLimitContract.TABLE + "_ss_ds_tt" + " ON "
                    + AttributionRateLimitContract.TABLE + "("
                    + AttributionRateLimitContract.SOURCE_SITE + ", "
                    + AttributionRateLimitContract.DESTINATION_SITE + ", "
                    + AttributionRateLimitContract.REPORT_TO + ", "
                    + AttributionRateLimitContract.TRIGGER_TIME + ")"
    };

    // Consolidated list of create statements for all tables.
    public static final List<String> CREATE_STATEMENTS =
            Collections.unmodifiableList(
                    Arrays.asList(
                            CREATE_TABLE_SOURCE,
                            CREATE_TABLE_TRIGGER,
                            CREATE_TABLE_ADTECH_URLS,
                            CREATE_TABLE_EVENT_REPORT,
                            CREATE_TABLE_ATTRIBUTION_RATE_LIMIT));

    // Private constructor to prevent instantiation.
    private MeasurementTables() {
    }
}
