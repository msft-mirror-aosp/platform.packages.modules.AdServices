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

import com.android.adservices.data.measurement.migration.MeasurementTablesDeprecated;

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
        MeasurementTables.AttributionContract.TABLE,
        MeasurementTables.AsyncRegistrationContract.TABLE
    };

    /** Contract for asynchronous Registration. */
    public interface AsyncRegistrationContract {
        String TABLE = MSMT_TABLE_PREFIX + "async_registration_contract";
        String ID = "_id";
        String ENROLLMENT_ID = "enrollment_id";
        String REGISTRATION_URI = "registration_uri";
        String TOP_ORIGIN = "top_origin";
        String SOURCE_TYPE = "source_type";
        String REDIRECT_TYPE = "redirect_type";
        String REDIRECT_COUNT = "redirect_count";
        String REGISTRANT = "registrant";
        String REQUEST_TIME = "request_time";
        String RETRY_COUNT = "retry_count";
        String LAST_PROCESSING_TIME = "last_processing_time";
        String TYPE = "type";
        String WEB_DESTINATION = "web_destination";
        String OS_DESTINATION = "os_destination";
        String VERIFIED_DESTINATION = "verified_destination";
        String DEBUG_KEY_ALLOWED = "debug_key_allowed";
    }

    /** Contract for Source. */
    public interface SourceContract {
        String TABLE = MSMT_TABLE_PREFIX + "source";
        String ID = "_id";
        String EVENT_ID = "event_id";
        String PUBLISHER = "publisher";
        String PUBLISHER_TYPE = "publisher_type";
        String APP_DESTINATION = "app_destination";
        String WEB_DESTINATION = "web_destination";
        String DEDUP_KEYS = "dedup_keys";
        String EVENT_TIME = "event_time";
        String EXPIRY_TIME = "expiry_time";
        String PRIORITY = "priority";
        String STATUS = "status";
        String SOURCE_TYPE = "source_type";
        String ENROLLMENT_ID = "enrollment_id";
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
        String DESTINATION_TYPE = "destination_type";
        String TRIGGER_TIME = "trigger_time";
        String STATUS = "status";
        String REGISTRANT = "registrant";
        String ENROLLMENT_ID = "enrollment_id";
        String EVENT_TRIGGERS = "event_triggers";
        String AGGREGATE_TRIGGER_DATA = "aggregate_trigger_data";
        String AGGREGATE_VALUES = "aggregate_values";
        String FILTERS = "filters";
        String NOT_FILTERS = "not_filters";
        String DEBUG_KEY = "debug_key";
    }

    /** Contract for EventReport. */
    public interface EventReportContract {
        String TABLE = MSMT_TABLE_PREFIX + "event_report";
        String ID = "_id";
        String SOURCE_EVENT_ID = "source_event_id";
        String ATTRIBUTION_DESTINATION = "attribution_destination";
        String REPORT_TIME = "report_time";
        String TRIGGER_DATA = "trigger_data";
        String TRIGGER_PRIORITY = "trigger_priority";
        String TRIGGER_DEDUP_KEY = "trigger_dedup_key";
        String TRIGGER_TIME = "trigger_time";
        String STATUS = "status";
        String DEBUG_REPORT_STATUS = "debug_report_status";
        String SOURCE_TYPE = "source_type";
        String ENROLLMENT_ID = "enrollment_id";
        String RANDOMIZED_TRIGGER_RATE = "randomized_trigger_rate";
        String SOURCE_DEBUG_KEY = "source_debug_key";
        String TRIGGER_DEBUG_KEY = "trigger_debug_key";
        String SOURCE_ID = "source_id";
        String TRIGGER_ID = "trigger_id";
    }

    /** Contract for Attribution rate limit. */
    public interface AttributionContract {
        String TABLE = MSMT_TABLE_PREFIX + "attribution";
        String ID = "_id";
        String SOURCE_SITE = "source_site";
        String SOURCE_ORIGIN = "source_origin";
        String DESTINATION_SITE = "attribution_destination_site";
        String DESTINATION_ORIGIN = "destination_origin";
        String TRIGGER_TIME = "trigger_time";
        String REGISTRANT = "registrant";
        String ENROLLMENT_ID = "enrollment_id";
        String SOURCE_ID = "source_id";
        String TRIGGER_ID = "trigger_id";
    }

    /** Contract for Unencrypted aggregate payload. */
    public interface AggregateReport {
        String TABLE = MSMT_TABLE_PREFIX + "aggregate_report";
        String ID = "_id";
        String PUBLISHER = "publisher";
        String ATTRIBUTION_DESTINATION = "attribution_destination";
        String SOURCE_REGISTRATION_TIME = "source_registration_time";
        String SCHEDULED_REPORT_TIME = "scheduled_report_time";
        String ENROLLMENT_ID = "enrollment_id";
        String DEBUG_CLEARTEXT_PAYLOAD = "debug_cleartext_payload";
        String STATUS = "status";
        String DEBUG_REPORT_STATUS = "debug_report_status";
        String API_VERSION = "api_version";
        String SOURCE_DEBUG_KEY = "source_debug_key";
        String TRIGGER_DEBUG_KEY = "trigger_debug_key";
        String SOURCE_ID = "source_id";
        String TRIGGER_ID = "trigger_id";
    }

    /** Contract for aggregate encryption key. */
    public interface AggregateEncryptionKey {
        String TABLE = MSMT_TABLE_PREFIX + "aggregate_encryption_key";
        String ID = "_id";
        String KEY_ID = "key_id";
        String PUBLIC_KEY = "public_key";
        String EXPIRY = "expiry";
    }

    public static final String CREATE_TABLE_ASYNC_REGISTRATION_V1 =
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
                    + MeasurementTablesDeprecated.AsyncRegistration.REDIRECT
                    + " INTEGER, "
                    + MeasurementTablesDeprecated.AsyncRegistration.INPUT_EVENT
                    + " INTEGER, "
                    + AsyncRegistrationContract.REGISTRANT
                    + " TEXT, "
                    + MeasurementTablesDeprecated.AsyncRegistration.SCHEDULED_TIME
                    + " INTEGER, "
                    + AsyncRegistrationContract.RETRY_COUNT
                    + " INTEGER, "
                    + AsyncRegistrationContract.LAST_PROCESSING_TIME
                    + " INTEGER, "
                    + AsyncRegistrationContract.TYPE
                    + " INTEGER "
                    + ")";

    public static final String CREATE_TABLE_ASYNC_REGISTRATION_V2 =
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
                    + " INTEGER "
                    + ")";

    public static final String CREATE_TABLE_SOURCE_V1 =
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

    public static final String CREATE_TABLE_TRIGGER_V1 =
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
                    + TriggerContract.FILTERS
                    + " TEXT, "
                    + TriggerContract.DEBUG_KEY
                    + " INTEGER "
                    + ")";

    public static final String CREATE_TABLE_TRIGGER_V2 =
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
                    + TriggerContract.FILTERS
                    + " TEXT, "
                    + TriggerContract.NOT_FILTERS
                    + " TEXT, "
                    + TriggerContract.DEBUG_KEY
                    + " INTEGER "
                    + ")";

    public static final String CREATE_TABLE_EVENT_REPORT_V1 =
            "CREATE TABLE "
                    + EventReportContract.TABLE
                    + " ("
                    + EventReportContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + EventReportContract.SOURCE_ID
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
                    + EventReportContract.SOURCE_TYPE
                    + " TEXT, "
                    + EventReportContract.RANDOMIZED_TRIGGER_RATE
                    + " DOUBLE "
                    + ")";

    public static final String CREATE_TABLE_EVENT_REPORT_V3 =
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

    public static final String CREATE_TABLE_ATTRIBUTION_V1 =
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
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_ATTRIBUTION_V3 =
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

    public static final String CREATE_TABLE_AGGREGATE_REPORT_V1 =
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
                    + AggregateReport.API_VERSION
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_AGGREGATE_REPORT_V3 =
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

    public static final String CREATE_TABLE_AGGREGATE_ENCRYPTION_KEY_V1 =
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

    public static final String[] CREATE_INDEXES = {
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
                + ")"
    };

    // Consolidated list of create statements for all tables.
    public static final List<String> CREATE_STATEMENTS =
            Collections.unmodifiableList(
                    Arrays.asList(
                            CREATE_TABLE_SOURCE_V1,
                            CREATE_TABLE_TRIGGER_V2,
                            CREATE_TABLE_EVENT_REPORT_V3,
                            CREATE_TABLE_ATTRIBUTION_V3,
                            CREATE_TABLE_AGGREGATE_REPORT_V3,
                            CREATE_TABLE_AGGREGATE_ENCRYPTION_KEY_V1,
                            CREATE_TABLE_ASYNC_REGISTRATION_V2));

    // Private constructor to prevent instantiation.
    private MeasurementTables() {
    }
}
