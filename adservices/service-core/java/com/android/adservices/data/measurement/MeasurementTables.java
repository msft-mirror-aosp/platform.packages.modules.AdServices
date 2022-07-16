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
        MeasurementTables.EnrollmentDataContract.TABLE,
        MeasurementTables.AggregateReport.TABLE,
        MeasurementTables.AggregateEncryptionKey.TABLE,
        MeasurementTables.AttributionRateLimitContract.TABLE
    };

    /** Contract for Source. */
    public interface SourceContract {
        String TABLE = MSMT_TABLE_PREFIX + "source";
        String ID = "_id";
        String EVENT_ID = "event_id";
        String ATTRIBUTION_DESTINATION = "attribution_destination";
        String WEB_DESTINATION = "web_destination";
        String DEDUP_KEYS = "dedup_keys";
        String EVENT_TIME = "event_time";
        String EXPIRY_TIME = "expiry_time";
        String PRIORITY = "priority";
        String STATUS = "status";
        String SOURCE_TYPE = "source_type";
        String REGISTRANT = "registrant";
        String ATTRIBUTION_MODE = "attribution_mode";
        String INSTALL_ATTRIBUTION_WINDOW = "install_attribution_window";
        String INSTALL_COOLDOWN_WINDOW = "install_cooldown_window";
        String IS_INSTALL_ATTRIBUTED = "is_install_attributed";
        String PUBLISHER = "publisher";
        String AD_TECH_DOMAIN = "ad_tech_domain";
        String FILTER_DATA = "filter_data";
        String AGGREGATE_SOURCE = "aggregate_source";
        String AGGREGATE_CONTRIBUTIONS = "aggregate_contributions";

        /** @deprecated replaced by {@link #PUBLISHER} */
        @Deprecated String DEPRECATED_ATTRIBUTION_SOURCE = "attribution_source";
        /** @deprecated replaced by {@link #AD_TECH_DOMAIN} */
        @Deprecated String DEPRECATED_REPORT_TO = "report_to";
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

        /** @deprecated replaced by AD_TECH_DOMAIN */
        @Deprecated
        String DEPRECATED_REPORT_TO = "report_to";
        /** @deprecated replaced by EVENT_TRIGGER_DATA */
        @Deprecated
        String DEPRECATED_TRIGGER_DATA = "trigger_data";
        /** @deprecated replaced by {@link #EVENT_TRIGGERS} */
        @Deprecated String DEPRECATED_EVENT_TRIGGER_DATA = "event_trigger_data";
        /** @deprecated replaced by {@link #EVENT_TRIGGERS} */
        @Deprecated String DEPRECATED_DEDUP_KEY = "deduplication_key";
        /** @deprecated replaced by {@link #EVENT_TRIGGERS} */
        @Deprecated String DEPRECATED_PRIORITY = "priority";
    }

    // TODO: delete all AdtechUrl related methods.
    /** Contract for AdTechUrls. */
    public interface AdTechUrlsContract {
        String TABLE = MSMT_TABLE_PREFIX + "adtech_urls";
        String POSTBACK_URL = "postback_url";
        String AD_TECH_ID = "ad_tech_id";
    }

    /** Contract for Adtech enrollment data. */
    public interface EnrollmentDataContract {
        String TABLE = MSMT_TABLE_PREFIX + "enrollment_data";
        String ENROLLMENT_ID = "enrollment_id";
        String COMPANY_ID = "company_id";
        // Following six string columns each consist of a space separated list.
        String SDK_NAMES = "sdk_names";
        String ATTRIBUTION_SOURCE_REGISTRATION_URL = "attribution_source_registration_url";
        String ATTRIBUTION_TRIGGER_REGISTRATION_URL = "attribution_trigger_registration_url";
        String ATTRIBUTION_REPORTING_URL = "attribution_reporting_url";
        String REMARKETING_RESPONSE_BASED_REGISTRATION_URL =
                "remarketing_response_based_registration_url";
        String ENCRYPTION_KEY_URL = "encryption_key_url";
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

        /** @deprecated replaced by {@link #AD_TECH_DOMAIN} */
        @Deprecated String DEPRECATED_REPORT_TO = "report_to";
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

        /** @deprecated replaced by AD_TECH_DOMAIN */
        @Deprecated String DEPRECATED_REPORT_TO = "report_to";
    }

    /** Contract for Unencrypted aggregate payload. */
    public interface AggregateReport {
        String TABLE = MSMT_TABLE_PREFIX + "aggregate_report";
        String ID = "_id";
        String PUBLISHER = "publisher";
        String ATTRIBUTION_DESTINATION = "attribution_destination";
        String SOURCE_REGISTRATION_TIME = "source_registration_time";
        String SCHEDULED_REPORT_TIME = "scheduled_report_time";
        String DEPRECATED_PRIVACY_BUDGET_KEY = "privacy_budget_key";
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

    public static final String CREATE_TABLE_SOURCE =
            "CREATE TABLE "
                    + SourceContract.TABLE
                    + " ("
                    + SourceContract.ID + " TEXT PRIMARY KEY NOT NULL, "
                    + SourceContract.EVENT_ID + " INTEGER, "
                    + SourceContract.DEPRECATED_ATTRIBUTION_SOURCE + " TEXT, "
                    + SourceContract.ATTRIBUTION_DESTINATION + " TEXT, "
                    + SourceContract.DEPRECATED_REPORT_TO + " TEXT, "
                    + SourceContract.EVENT_TIME + " INTEGER, "
                    + SourceContract.EXPIRY_TIME + " INTEGER, "
                    + SourceContract.PRIORITY + " INTEGER, "
                    + SourceContract.STATUS + " INTEGER, "
                    + SourceContract.DEDUP_KEYS + " TEXT, "
                    + SourceContract.SOURCE_TYPE + " TEXT, "
                    + SourceContract.REGISTRANT + " TEXT, "
                    + SourceContract.ATTRIBUTION_MODE + " INTEGER, "
                    + SourceContract.INSTALL_ATTRIBUTION_WINDOW + " INTEGER, "
                    + SourceContract.INSTALL_COOLDOWN_WINDOW + " INTEGER, "
                    + SourceContract.IS_INSTALL_ATTRIBUTED + " INTEGER "
                    + ")";

    public static final String CREATE_TABLE_TRIGGER =
            "CREATE TABLE "
                    + TriggerContract.TABLE
                    + " ("
                    + TriggerContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + TriggerContract.ATTRIBUTION_DESTINATION
                    + " TEXT, "
                    + TriggerContract.DEPRECATED_REPORT_TO
                    + " TEXT, "
                    + TriggerContract.TRIGGER_TIME
                    + " INTEGER, "
                    + TriggerContract.DEPRECATED_TRIGGER_DATA
                    + " INTEGER, "
                    + TriggerContract.DEPRECATED_PRIORITY
                    + " INTEGER, "
                    + TriggerContract.DEPRECATED_DEDUP_KEY
                    + " TEXT, "
                    + TriggerContract.STATUS
                    + " INTEGER, "
                    + TriggerContract.REGISTRANT
                    + " TEXT "
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
                    + EventReportContract.DEPRECATED_REPORT_TO + " TEXT, "
                    + EventReportContract.ATTRIBUTION_DESTINATION + " TEXT, "
                    + EventReportContract.REPORT_TIME + " INTEGER, "
                    + EventReportContract.TRIGGER_DATA + " INTEGER, "
                    + EventReportContract.TRIGGER_PRIORITY + " INTEGER, "
                    + EventReportContract.TRIGGER_DEDUP_KEY + " INTEGER, "
                    + EventReportContract.TRIGGER_TIME + " INTEGER, "
                    + EventReportContract.STATUS + " INTEGER, "
                    + EventReportContract.SOURCE_TYPE + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_ATTRIBUTION_RATE_LIMIT =
            "CREATE TABLE "
                    + AttributionRateLimitContract.TABLE
                    + " ("
                    + AttributionRateLimitContract.ID + " TEXT PRIMARY KEY NOT NULL, "
                    + AttributionRateLimitContract.SOURCE_SITE + " TEXT, "
                    + AttributionRateLimitContract.DESTINATION_SITE + " TEXT, "
                    + AttributionRateLimitContract.DEPRECATED_REPORT_TO + " TEXT, "
                    + AttributionRateLimitContract.TRIGGER_TIME + " INTEGER, "
                    + AttributionRateLimitContract.REGISTRANT + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_AGGREGATE_REPORT =
            "CREATE TABLE "
                    + AggregateReport.TABLE
                    + " ("
                    + AggregateReport.ID + " TEXT PRIMARY KEY NOT NULL, "
                    + AggregateReport.PUBLISHER + " TEXT, "
                    + AggregateReport.ATTRIBUTION_DESTINATION + " TEXT, "
                    + AggregateReport.SOURCE_REGISTRATION_TIME + " INTEGER, "
                    + AggregateReport.SCHEDULED_REPORT_TIME + " INTEGER, "
                    + AggregateReport.DEPRECATED_PRIVACY_BUDGET_KEY + " TEXT, "
                    + AggregateReport.REPORTING_ORIGIN + " TEXT, "
                    + AggregateReport.DEBUG_CLEARTEXT_PAYLOAD + " TEXT, "
                    + SourceContract.STATUS + " INTEGER "
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

    public static final String CREATE_TABLE_ENROLLMENT_DATA =
            "CREATE TABLE "
                    + EnrollmentDataContract.TABLE
                    + " ("
                    + EnrollmentDataContract.ENROLLMENT_ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + EnrollmentDataContract.COMPANY_ID
                    + " TEXT, "
                    + EnrollmentDataContract.SDK_NAMES
                    + " TEXT, "
                    + EnrollmentDataContract.ATTRIBUTION_SOURCE_REGISTRATION_URL
                    + " TEXT, "
                    + EnrollmentDataContract.ATTRIBUTION_TRIGGER_REGISTRATION_URL
                    + " TEXT, "
                    + EnrollmentDataContract.ATTRIBUTION_REPORTING_URL
                    + " TEXT, "
                    + EnrollmentDataContract.REMARKETING_RESPONSE_BASED_REGISTRATION_URL
                    + " TEXT, "
                    + EnrollmentDataContract.ENCRYPTION_KEY_URL
                    + " TEXT "
                    + ")";

    public static final String[] CREATE_INDEXES = {
            "CREATE INDEX "
                    + INDEX_PREFIX + SourceContract.TABLE + "_ad_rt_et " + "ON "
                    + SourceContract.TABLE + "( "
                    + SourceContract.ATTRIBUTION_DESTINATION + ", "
                    + SourceContract.DEPRECATED_REPORT_TO + ", "
                    + SourceContract.EXPIRY_TIME + " DESC " + ")",
            "CREATE INDEX "
                    + INDEX_PREFIX + TriggerContract.TABLE + "_ad_rt_tt " + "ON "
                    + TriggerContract.TABLE + "( "
                    + TriggerContract.ATTRIBUTION_DESTINATION + ", "
                    + TriggerContract.DEPRECATED_REPORT_TO + ", "
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
                    + AttributionRateLimitContract.DEPRECATED_REPORT_TO + ", "
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
                            CREATE_TABLE_ATTRIBUTION_RATE_LIMIT,
                            CREATE_TABLE_ENROLLMENT_DATA));

    // Private constructor to prevent instantiation.
    private MeasurementTables() {
    }
}
