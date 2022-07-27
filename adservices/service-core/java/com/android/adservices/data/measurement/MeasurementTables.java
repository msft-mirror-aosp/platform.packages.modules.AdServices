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
    public static final String MSMT_TABLE_PREFIX = "msmt_";
    public static final String INDEX_PREFIX = "idx_";

    /**
     * Array of all Measurement related tables. The AdTechUrls table is not included in the
     * Measurement tables because it will be used for a more general purpose.
     */
    // TODO(b/237306788): Move AdTechUrls tables to common tables and add method to delete common
    //  tables.
    public static final String[] ALL_MSMT_TABLES = {
        MeasurementTables.SourceContract.TABLE,
        MeasurementTables.TriggerContract.TABLE,
        MeasurementTables.EventReportContract.TABLE,
        MeasurementTables.AggregateReport.TABLE,
        MeasurementTables.AggregateEncryptionKey.TABLE,
        MeasurementTables.AttributionRateLimitContract.TABLE,
        MeasurementTables.AsyncRegistrationContract.TABLE
    };

    /** Contract for asynchronous Registration. */
    public interface AsyncRegistrationContract {
        String TABLE = MSMT_TABLE_PREFIX + "async_registration_contract";
        String ID = "_id";
        String REGISTRATION_URI = "registration_uri";
        String TOP_ORIGIN = "top_origin";
        String INPUT_EVENT = "input_event";
        String REDIRECT = "redirect";
        String REGISTRANT = "registrant";
        String SCHEDULE_TIME = "scheduled_time";
        String RETRY_COUNT = "retry_count";
        String LAST_TIME_PROCESSING = "last_processing_time";
        String TYPE = "type";
        String WEB_DESTINATION = "web_destination";
        String OS_DESTINATION = "os_destination";
        String VERIFIED_DESTINATION = "verified_destination";
    }

    /** Contract for Source. */
    public interface SourceContract {
        String TABLE = MSMT_TABLE_PREFIX + "source";
        String ID = "_id";
        String EVENT_ID = "event_id";
        String PUBLISHER = "publisher";
        String APP_DESTINATION = "app_destination";
        String WEB_DESTINATION = "web_destination";
        String DEDUP_KEYS = "dedup_keys";
        String EVENT_TIME = "event_time";
        String EXPIRY_TIME = "expiry_time";
        String PRIORITY = "priority";
        String STATUS = "status";
        String SOURCE_TYPE = "source_type";
        String AD_TECH_DOMAIN = "ad_tech_domain";
        String REGISTRANT = "registrant";
        String ATTRIBUTION_MODE = "attribution_mode";
        String INSTALL_ATTRIBUTION_WINDOW = "install_attribution_window";
        String INSTALL_COOLDOWN_WINDOW = "install_cooldown_window";
        String IS_INSTALL_ATTRIBUTED = "is_install_attributed";
        String FILTER_DATA = "filter_data";
        String AGGREGATE_SOURCE = "aggregate_source";
        String AGGREGATE_CONTRIBUTIONS = "aggregate_contributions";
        String DEBUG_KEY = "debug_key";
    }

    /** Contract for Trigger. */
    public interface TriggerContract {
        String TABLE = MSMT_TABLE_PREFIX + "trigger";
        String ID = "_id";
        String ATTRIBUTION_DESTINATION = "attribution_destination";
        String TRIGGER_TIME = "trigger_time";
        String STATUS = "status";
        String REGISTRANT = "registrant";
        String AD_TECH_DOMAIN = "ad_tech_domain";
        String EVENT_TRIGGERS = "event_triggers";
        String AGGREGATE_TRIGGER_DATA = "aggregate_trigger_data";
        String AGGREGATE_VALUES = "aggregate_values";
        String FILTERS = "filters";
        String DEBUG_KEY = "debug_key";
    }

    /** Contract for EventReport. */
    public interface EventReportContract {
        String TABLE = MSMT_TABLE_PREFIX + "event_report";
        String ID = "_id";
        String SOURCE_ID = "source_id";
        String ATTRIBUTION_DESTINATION = "attribution_destination";
        String REPORT_TIME = "report_time";
        String TRIGGER_DATA = "trigger_data";
        String TRIGGER_PRIORITY = "trigger_priority";
        String TRIGGER_DEDUP_KEY = "trigger_dedup_key";
        String TRIGGER_TIME = "trigger_time";
        String STATUS = "status";
        String SOURCE_TYPE = "source_type";
        String AD_TECH_DOMAIN = "ad_tech_domain";
        String RANDOMIZED_TRIGGER_RATE = "randomized_trigger_rate";
    }

    /** Contract for Attribution rate limit. */
    public interface AttributionRateLimitContract {
        String TABLE = MSMT_TABLE_PREFIX + "attribution_rate_limit";
        String ID = "_id";
        String SOURCE_SITE = "source_site";
        String DESTINATION_SITE = "attribution_destination_site";
        String TRIGGER_TIME = "trigger_time";
        String REGISTRANT = "registrant";
        String AD_TECH_DOMAIN = "ad_tech_domain";
    }

    /** Contract for Unencrypted aggregate payload. */
    public interface AggregateReport {
        String TABLE = MSMT_TABLE_PREFIX + "aggregate_report";
        String ID = "_id";
        String PUBLISHER = "publisher";
        String ATTRIBUTION_DESTINATION = "attribution_destination";
        String SOURCE_REGISTRATION_TIME = "source_registration_time";
        String SCHEDULED_REPORT_TIME = "scheduled_report_time";
        String REPORTING_ORIGIN = "reporting_origin";
        String DEBUG_CLEARTEXT_PAYLOAD = "debug_cleartext_payload";
        String STATUS = "status";
        String API_VERSION = "api_version";
    }

    /** Contract for aggregate encryption key. */
    public interface AggregateEncryptionKey {
        String TABLE = MSMT_TABLE_PREFIX + "aggregate_encryption_key";
        String ID = "_id";
        String KEY_ID = "key_id";
        String PUBLIC_KEY = "public_key";
        String EXPIRY = "expiry";
    }

    public static final String CREATE_TABLE_ASYNC_REGISTRATION =
            "CREATE TABLE "
                    + AsyncRegistrationContract.TABLE
                    + " ("
                    + AsyncRegistrationContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
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
                    + AsyncRegistrationContract.REDIRECT
                    + " INTEGER, "
                    + AsyncRegistrationContract.INPUT_EVENT
                    + " INTEGER, "
                    + AsyncRegistrationContract.REGISTRANT
                    + " TEXT, "
                    + AsyncRegistrationContract.SCHEDULE_TIME
                    + " INTEGER, "
                    + AsyncRegistrationContract.RETRY_COUNT
                    + " INTEGER, "
                    + AsyncRegistrationContract.LAST_TIME_PROCESSING
                    + " INTEGER, "
                    + AsyncRegistrationContract.TYPE
                    + " INTEGER "
                    + ")";

    public static final String CREATE_TABLE_SOURCE =
            "CREATE TABLE "
                    + SourceContract.TABLE
                    + " ("
                    + SourceContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + SourceContract.EVENT_ID
                    + " INTEGER, "
                    + SourceContract.PUBLISHER
                    + " TEXT, "
                    + SourceContract.APP_DESTINATION
                    + " TEXT, "
                    + SourceContract.AD_TECH_DOMAIN
                    + " TEXT, "
                    + SourceContract.EVENT_TIME
                    + " INTEGER, "
                    + SourceContract.EXPIRY_TIME
                    + " INTEGER, "
                    + SourceContract.PRIORITY
                    + " INTEGER, "
                    + SourceContract.STATUS
                    + " INTEGER, "
                    + SourceContract.DEDUP_KEYS
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
                    + " INTEGER "
                    + ")";

    public static final String CREATE_TABLE_TRIGGER =
            "CREATE TABLE "
                    + TriggerContract.TABLE
                    + " ("
                    + TriggerContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + TriggerContract.ATTRIBUTION_DESTINATION
                    + " TEXT, "
                    + TriggerContract.AD_TECH_DOMAIN
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
                    + TriggerContract.FILTERS
                    + " TEXT, "
                    + TriggerContract.DEBUG_KEY
                    + " INTEGER "
                    + ")";

    public static final String CREATE_TABLE_EVENT_REPORT =
            "CREATE TABLE "
                    + EventReportContract.TABLE
                    + " ("
                    + EventReportContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + EventReportContract.SOURCE_ID
                    + " INTEGER, "
                    + EventReportContract.AD_TECH_DOMAIN
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
                    + EventReportContract.SOURCE_TYPE
                    + " TEXT, "
                    + EventReportContract.RANDOMIZED_TRIGGER_RATE
                    + " DOUBLE "
                    + ")";

    public static final String CREATE_TABLE_ATTRIBUTION_RATE_LIMIT =
            "CREATE TABLE "
                    + AttributionRateLimitContract.TABLE
                    + " ("
                    + AttributionRateLimitContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + AttributionRateLimitContract.SOURCE_SITE
                    + " TEXT, "
                    + AttributionRateLimitContract.DESTINATION_SITE
                    + " TEXT, "
                    + AttributionRateLimitContract.AD_TECH_DOMAIN
                    + " TEXT, "
                    + AttributionRateLimitContract.TRIGGER_TIME
                    + " INTEGER, "
                    + AttributionRateLimitContract.REGISTRANT
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_AGGREGATE_REPORT =
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
                    + AggregateReport.REPORTING_ORIGIN
                    + " TEXT, "
                    + AggregateReport.DEBUG_CLEARTEXT_PAYLOAD
                    + " TEXT, "
                    + AggregateReport.STATUS
                    + " INTEGER, "
                    + AggregateReport.API_VERSION
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_AGGREGATE_ENCRYPTION_KEY =
            "CREATE TABLE "
                    + AggregateEncryptionKey.TABLE
                    + " ("
                    + AggregateEncryptionKey.ID + " TEXT PRIMARY KEY NOT NULL, "
                    + AggregateEncryptionKey.KEY_ID + " TEXT, "
                    + AggregateEncryptionKey.PUBLIC_KEY + " TEXT, "
                    + AggregateEncryptionKey.EXPIRY + " INTEGER "
                    + ")";

    public static final String[] CREATE_INDEXES = {
        "CREATE INDEX "
                + INDEX_PREFIX
                + SourceContract.TABLE
                + "_ad_atd_et "
                + "ON "
                + SourceContract.TABLE
                + "( "
                + SourceContract.APP_DESTINATION
                + ", "
                + SourceContract.AD_TECH_DOMAIN
                + ", "
                + SourceContract.EXPIRY_TIME
                + " DESC "
                + ")",
        "CREATE INDEX "
                + INDEX_PREFIX
                + TriggerContract.TABLE
                + "_ad_atd_tt "
                + "ON "
                + TriggerContract.TABLE
                + "( "
                + TriggerContract.ATTRIBUTION_DESTINATION
                + ", "
                + TriggerContract.AD_TECH_DOMAIN
                + ", "
                + TriggerContract.TRIGGER_TIME
                + " ASC)",
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
                + TriggerContract.TABLE
                + "_tt "
                + "ON "
                + TriggerContract.TABLE
                + "("
                + TriggerContract.TRIGGER_TIME
                + ")",
        "CREATE INDEX "
                + INDEX_PREFIX
                + AttributionRateLimitContract.TABLE
                + "_ss_ds_atd_tt"
                + " ON "
                + AttributionRateLimitContract.TABLE
                + "("
                + AttributionRateLimitContract.SOURCE_SITE
                + ", "
                + AttributionRateLimitContract.DESTINATION_SITE
                + ", "
                + AttributionRateLimitContract.AD_TECH_DOMAIN
                + ", "
                + AttributionRateLimitContract.TRIGGER_TIME
                + ")"
    };

    // Consolidated list of create statements for all tables.
    public static final List<String> CREATE_STATEMENTS =
            Collections.unmodifiableList(
                    Arrays.asList(
                            CREATE_TABLE_SOURCE,
                            CREATE_TABLE_TRIGGER,
                            CREATE_TABLE_EVENT_REPORT,
                            CREATE_TABLE_ATTRIBUTION_RATE_LIMIT,
                            CREATE_TABLE_AGGREGATE_REPORT,
                            CREATE_TABLE_AGGREGATE_ENCRYPTION_KEY,
                            CREATE_TABLE_ASYNC_REGISTRATION));

    // Private constructor to prevent instantiation.
    private MeasurementTables() {
    }
}
