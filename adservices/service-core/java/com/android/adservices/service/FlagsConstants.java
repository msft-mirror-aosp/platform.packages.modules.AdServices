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
package com.android.adservices.service;

/**
 * Defines constants used by {@code Flags}, {@code PhFlags} and testing infra (both device and host
 * side).
 *
 * <p><b>NOTE: </b>cannot have any dependency on Android or other AdServices code.
 */
public final class FlagsConstants {

    private FlagsConstants() {
        throw new UnsupportedOperationException("Contains only static constants");
    }

    // ********************************************
    // * Flag values (initially defined by Flags) *
    // ********************************************

    public static final int SYSTEM_SERVER_ONLY = 0;
    public static final int PPAPI_ONLY = 1;
    public static final int PPAPI_AND_SYSTEM_SERVER = 2;
    public static final int APPSEARCH_ONLY = 3;
    public static final float ADID_REQUEST_PERMITS_PER_SECOND = 25;

    // **************************************************
    // * Other constants (initially defined by PhFlags) *
    // **************************************************

    // AdServices Namespace String from DeviceConfig class not available in S Minus
    public static final String NAMESPACE_ADSERVICES = "adservices";

    public static final String ARRAY_SPLITTER_COMMA = ",";

    // Maximum possible percentage for percentage variables
    public static final int MAX_PERCENTAGE = 100;

    // *********************************************
    // * Flag names (initially defined by PhFlags) *
    // *********************************************

    /*
     * Keys for ALL the flags stored in DeviceConfig.
     */
    // Common Keys
    public static final String KEY_MAINTENANCE_JOB_PERIOD_MS = "maintenance_job_period_ms";
    public static final String KEY_MAINTENANCE_JOB_FLEX_MS = "maintenance_job_flex_ms";

    public static final String KEY_ERROR_CODE_LOGGING_DENY_LIST = "error_code_logging_deny_list";

    // Topics keys
    public static final String KEY_TOPICS_EPOCH_JOB_PERIOD_MS = "topics_epoch_job_period_ms";
    public static final String KEY_TOPICS_EPOCH_JOB_FLEX_MS = "topics_epoch_job_flex_ms";
    public static final String KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC =
            "topics_percentage_for_random_topics";
    public static final String KEY_TOPICS_NUMBER_OF_TOP_TOPICS = "topics_number_of_top_topics";
    public static final String KEY_TOPICS_NUMBER_OF_RANDOM_TOPICS =
            "topics_number_of_random_topics";
    public static final String KEY_TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS =
            "topics_number_of_lookback_epochs";
    public static final String KEY_TOPICS_PRIVACY_BUDGET_FOR_TOPIC_ID_DISTRIBUTION =
            "topics_privacy_budget_for_topic_ids_distribution";
    public static final String KEY_NUMBER_OF_EPOCHS_TO_KEEP_IN_HISTORY =
            "topics_number_of_epochs_to_keep_in_history";
    public static final String KEY_GLOBAL_BLOCKED_TOPIC_IDS = "topics_global_blocked_topic_ids";
    public static final String KEY_TOPICS_DISABLE_DIRECT_APP_CALLS =
            "topics_disable_direct_app_calls";

    // Topics classifier keys
    public static final String KEY_CLASSIFIER_TYPE = "classifier_type";
    public static final String KEY_CLASSIFIER_NUMBER_OF_TOP_LABELS =
            "classifier_number_of_top_labels";
    public static final String KEY_CLASSIFIER_THRESHOLD = "classifier_threshold";
    public static final String KEY_CLASSIFIER_DESCRIPTION_MAX_WORDS =
            "classifier_description_max_words";
    public static final String KEY_CLASSIFIER_DESCRIPTION_MAX_LENGTH =
            "classifier_description_max_length";
    public static final String KEY_CLASSIFIER_FORCE_USE_BUNDLED_FILES =
            "classifier_force_use_bundled_files";

    // Topics Cobalt keys
    public static final String KEY_TOPICS_COBALT_LOGGING_ENABLED = "topics_cobalt_logging_enabled";
    public static final String KEY_COBALT_ADSERVICES_API_KEY_HEX = "cobalt_adservices_api_key_hex";
    public static final String KEY_ADSERVICES_RELEASE_STAGE_FOR_COBALT =
            "adservices_release_stage_for_cobalt";
    public static final String KEY_COBALT_LOGGING_JOB_PERIOD_MS = "cobalt_logging_job_period_ms";
    public static final String KEY_COBALT_LOGGING_ENABLED = "cobalt_logging_enabled";

    // Measurement keys
    public static final String KEY_MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS =
            "measurement_event_main_reporting_job_period_ms";
    public static final String KEY_MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS =
            "measurement_event_fallback_reporting_job_period_ms";

    public static final String KEY_MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_ENABLED =
            "measurement_aggregation_coordination_origin_enabled";

    public static final String KEY_MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_LIST =
            "measurement_aggregation_coordinator_origin_list";

    public static final String KEY_MEASUREMENT_DEFAULT_AGGREGATION_COORDINATOR_ORIGIN =
            "measurement_default_aggregation_coordinator_origin";

    public static final String KEY_MEASUREMENT_AGGREGATION_COORDINATOR_PATH =
            "measurement_aggregation_coordinator_path";
    public static final String KEY_MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS =
            "measurement_aggregate_main_reporting_job_period_ms";
    public static final String KEY_MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS =
            "measurement_aggregate_fallback_reporting_job_period_ms";
    public static final String KEY_MEASUREMENT_NETWORK_CONNECT_TIMEOUT_MS =
            "measurement_network_connect_timeout_ms";
    public static final String KEY_MEASUREMENT_NETWORK_READ_TIMEOUT_MS =
            "measurement_network_read_timeout_ms";
    public static final String KEY_MEASUREMENT_DB_SIZE_LIMIT = "measurement_db_size_limit";
    public static final String KEY_MEASUREMENT_MANIFEST_FILE_URL =
            "mdd_measurement_manifest_file_url";
    public static final String KEY_MEASUREMENT_REGISTRATION_INPUT_EVENT_VALID_WINDOW_MS =
            "measurement_registration_input_event_valid_window_ms";
    public static final String KEY_MEASUREMENT_IS_CLICK_VERIFICATION_ENABLED =
            "measurement_is_click_verification_enabled";
    public static final String KEY_MEASUREMENT_IS_CLICK_VERIFIED_BY_INPUT_EVENT =
            "measurement_is_click_verified_by_input_event";
    public static final String KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCE =
            "measurement_enforce_foreground_status_register_source";
    public static final String KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_TRIGGER =
            "measurement_enforce_foreground_status_register_trigger";
    public static final String KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_SOURCE =
            "measurement_enforce_foreground_status_register_web_source";
    public static final String KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_TRIGGER =
            "measurement_enforce_foreground_status_register_web_trigger";
    public static final String KEY_MEASUREMENT_ENFORCE_ENROLLMENT_ORIGIN_MATCH =
            "measurement_enforce_enrollment_origin_match";
    public static final String KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_DELETE_REGISTRATIONS =
            "measurement_enforce_foreground_status_delete_registrations";
    public static final String KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_GET_STATUS =
            "measurement_enforce_foreground_status_get_status";
    public static final String KEY_MEASUREMENT_ENABLE_XNA = "measurement_enable_xna";
    public static final String KEY_MEASUREMENT_ENABLE_SHARED_SOURCE_DEBUG_KEY =
            "measurement_enable_shared_source_debug_key";
    public static final String KEY_MEASUREMENT_ENABLE_SHARED_FILTER_DATA_KEYS_XNA =
            "measurement_enable_shared_filter_data_keys_xna";
    public static final String KEY_MEASUREMENT_ENABLE_DEBUG_REPORT =
            "measurement_enable_debug_report";
    public static final String KEY_MEASUREMENT_ENABLE_SOURCE_DEBUG_REPORT =
            "measurement_enable_source_debug_report";
    public static final String KEY_MEASUREMENT_ENABLE_TRIGGER_DEBUG_REPORT =
            "measurement_enable_trigger_debug_report";
    public static final String KEY_MEASUREMENT_DATA_EXPIRY_WINDOW_MS =
            "measurement_data_expiry_window_ms";

    public static final String KEY_MEASUREMENT_MAX_REGISTRATION_REDIRECTS =
            "measurement_max_registration_redirects";

    public static final String KEY_MEASUREMENT_MAX_REGISTRATIONS_PER_JOB_INVOCATION =
            "measurement_max_registration_per_job_invocation";

    public static final String KEY_MEASUREMENT_MAX_RETRIES_PER_REGISTRATION_REQUEST =
            "measurement_max_retries_per_registration_request";

    public static final String KEY_MEASUREMENT_ASYNC_REGISTRATION_JOB_TRIGGER_MIN_DELAY_MS =
            "measurement_async_registration_job_trigger_min_delay_ms";

    public static final String KEY_MEASUREMENT_ASYNC_REGISTRATION_JOB_TRIGGER_MAX_DELAY_MS =
            "measurement_async_registration_job_trigger_max_delay_ms";

    public static final String KEY_MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_KILL_SWITCH =
            "measurement_attribution_fallback_job_kill_switch";

    public static final String KEY_MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_PERIOD_MS =
            "measurement_attribution_fallback_job_period_ms";

    public static final String KEY_MEASUREMENT_MAX_ATTRIBUTION_PER_RATE_LIMIT_WINDOW =
            "measurement_max_attribution_per_rate_limit_window";

    public static final String KEY_MEASUREMENT_MAX_DISTINCT_ENROLLMENTS_IN_ATTRIBUTION =
            "measurement_max_distinct_enrollments_in_attribution";

    public static final String KEY_MEASUREMENT_MAX_DISTINCT_DESTINATIONS_IN_ACTIVE_SOURCE =
            "measurement_max_distinct_destinations_in_active_source";

    public static final String KEY_MEASUREMENT_ENABLE_COARSE_EVENT_REPORT_DESTINATIONS =
            "measurement_enable_coarse_event_report_destinations";

    public static final String KEY_MEASUREMENT_ENABLE_VTC_CONFIGURABLE_MAX_EVENT_REPORTS =
            "measurement_enable_vtc_configurable_max_event_reports_count";

    public static final String KEY_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT =
            "measurement_vtc_configurable_max_event_reports_count";

    public static final String KEY_MEASUREMENT_ENABLE_ARA_PARSING_ALIGNMENT_V1 =
            "measurement_enable_ara_parsing_alignment_v1";

    public static final String KEY_MEASUREMENT_ENABLE_ARA_DEDUPLICATION_ALIGNMENT_V1 =
            "measurement_enable_ara_deduplication_alignment_v1";
    public static final String KEY_MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_PERIOD_MS =
            "measurement_debug_reporting_fallback_job_period_ms";
    public static final String KEY_MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_PERIOD_MS =
            "measurement_verbose_debug_reporting_fallback_job_period_ms";

    public static final String KEY_MEASUREMENT_ENABLE_APP_PACKAGE_NAME_LOGGING =
            "measurement_enable_app_package_name_logging";

    public static final String KEY_MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_UNACCOUNTED_EXCEPTION =
            "measurement_enable_reporting_jobs_throw_accounted_exception";

    public static final String KEY_MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_JSON_EXCEPTION =
            "measurement_enable_reporting_jobs_throw_json_exception";

    public static final String KEY_MEASUREMENT_ENABLE_DELETE_REPORTS_ON_UNRECOVERABLE_EXCEPTION =
            "measurement_enable_delete_reports_on_unrecoverable_exception";

    public static final String KEY_MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_CRYPTO_EXCEPTION =
            "measurement_enable_reporting_jobs_throw_crypto_exception";

    public static final String KEY_MEASUREMENT_ENABLE_DATASTORE_MANAGER_THROW_DATASTORE_EXCEPTION =
            "measurement_enable_datastore_manager_throw_datastore_exception";

    public static final String KEY_MEASUREMENT_THROW_UNKNOWN_EXCEPTION_SAMPLING_RATE =
            "measurement_throw_unknown_exception_sampling_rate";

    // FLEDGE Custom Audience keys
    public static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT =
            "fledge_custom_audience_max_count";
    public static final String KEY_FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT =
            "fledge_custom_audience_per_app_max_count";
    public static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT =
            "fledge_custom_audience_max_owner_count";
    public static final String KEY_FLEDGE_CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN_MS =
            "fledge_custom_audience_default_expire_in_days";
    public static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN_MS =
            "fledge_custom_audience_max_activate_in_days";
    public static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_EXPIRE_IN_MS =
            "fledge_custom_audience_max_expire_in_days";
    public static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NAME_SIZE_B =
            "key_fledge_custom_audience_max_name_size_b";
    public static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_DAILY_UPDATE_URI_SIZE_B =
            "key_fledge_custom_audience_max_daily_update_uri_size_b";
    public static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_BIDDING_LOGIC_URI_SIZE_B =
            "key_fledge_custom_audience_max_bidding_logic_uri_size_b";
    public static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B =
            "fledge_custom_audience_max_user_bidding_signals_size_b";
    public static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B =
            "fledge_custom_audience_max_trusted_bidding_data_size_b";
    public static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ADS_SIZE_B =
            "fledge_custom_audience_max_ads_size_b";
    public static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS =
            "fledge_custom_audience_max_num_ads";
    public static final String KEY_FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS =
            "fledge_custom_audience_active_time_window_ms";

    // FLEDGE fetchAndJoinCustomAudience keys
    public static final String KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B =
            "fledge_fetch_custom_audience_max_user_bidding_signals_size_b";
    public static final String KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_REQUEST_CUSTOM_HEADER_SIZE_B =
            "fledge_fetch_custom_audience_max_custom_header_size_b";
    public static final String KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_CUSTOM_AUDIENCE_SIZE_B =
            "fledge_fetch_custom_audience_max_custom_audience_size_b";

    // FLEDGE Background Fetch keys
    public static final String KEY_FLEDGE_BACKGROUND_FETCH_ENABLED =
            "fledge_background_fetch_enabled";
    public static final String KEY_FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS =
            "fledge_background_fetch_job_period_ms";
    public static final String KEY_FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS =
            "fledge_background_fetch_job_flex_ms";
    public static final String KEY_FLEDGE_BACKGROUND_FETCH_JOB_MAX_RUNTIME_MS =
            "fledge_background_fetch_job_max_runtime_ms";
    public static final String KEY_FLEDGE_BACKGROUND_FETCH_MAX_NUM_UPDATED =
            "fledge_background_fetch_max_num_updated";
    public static final String KEY_FLEDGE_BACKGROUND_FETCH_THREAD_POOL_SIZE =
            "fledge_background_fetch_thread_pool_size";
    public static final String KEY_FLEDGE_BACKGROUND_FETCH_ELIGIBLE_UPDATE_BASE_INTERVAL_S =
            "fledge_background_fetch_eligible_update_base_interval_s";
    public static final String KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS =
            "fledge_background_fetch_network_connect_timeout_ms";
    public static final String KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS =
            "fledge_background_fetch_network_read_timeout_ms";
    public static final String KEY_FLEDGE_BACKGROUND_FETCH_MAX_RESPONSE_SIZE_B =
            "fledge_background_fetch_max_response_size_b";

    // FLEDGE Ad Selection keys
    public static final String KEY_FLEDGE_AD_SELECTION_MAX_CONCURRENT_BIDDING_COUNT =
            "fledge_ad_selection_max_concurrent_bidding_count";
    public static final String KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS =
            "fledge_ad_selection_bidding_timeout_per_ca_ms";
    public static final String KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS =
            "fledge_ad_selection_scoring_timeout_ms";
    public static final String KEY_FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS =
            "fledge_ad_selection_selecting_outcome_timeout_ms";
    public static final String KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS =
            "fledge_ad_selection_overall_timeout_ms";
    public static final String KEY_FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS =
            "fledge_ad_selection_from_outcomes_overall_timeout_ms";
    public static final String KEY_FLEDGE_AD_SELECTION_EXPIRATION_WINDOW_S =
            "fledge_ad_selection_expiration_window_s";
    public static final String KEY_FLEDGE_AD_SELECTION_FILTERING_ENABLED =
            "fledge_ad_selection_filtering_enabled";
    public static final String KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED =
            "fledge_fetch_custom_audience_enabled";
    public static final String KEY_FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS =
            "fledge_report_impression_overall_timeout_ms";
    public static final String KEY_FLEDGE_REPORT_IMPRESSION_MAX_REGISTERED_AD_BEACONS_TOTAL_COUNT =
            "fledge_report_impression_max_registered_ad_beacons_total_count";
    public static final String
            KEY_FLEDGE_REPORT_IMPRESSION_MAX_REGISTERED_AD_BEACONS_PER_AD_TECH_COUNT =
                    "fledge_report_impression_max_registered_ad_beacons_per_ad_tech_count";
    public static final String
            KEY_FLEDGE_REPORT_IMPRESSION_REGISTERED_AD_BEACONS_MAX_INTERACTION_KEY_SIZE_B =
                    "fledge_report_impression_registered_ad_beacons_max_interaction_key_size_b";
    public static final String KEY_FLEDGE_REPORT_IMPRESSION_MAX_INTERACTION_REPORTING_URI_SIZE_B =
            "fledge_report_impression_max_interaction_reporting_uri_size_b";
    public static final String KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_BUYER_MS =
            "fledge_ad_selection_bidding_timeout_per_buyer_ms";
    public static final String KEY_FLEDGE_HTTP_CACHE_ENABLE = "fledge_http_cache_enable";
    public static final String KEY_FLEDGE_HTTP_CACHE_ENABLE_JS_CACHING =
            "fledge_http_cache_enable_js_caching";
    public static final String KEY_FLEDGE_HTTP_CACHE_DEFAULT_MAX_AGE_SECONDS =
            "fledge_http_cache_default_max_age_seconds";
    public static final String KEY_FLEDGE_HTTP_CACHE_MAX_ENTRIES = "fledge_http_cache_max_entries";

    // FLEDGE Ad Counter Histogram keys
    public static final String KEY_FLEDGE_AD_COUNTER_HISTOGRAM_ABSOLUTE_MAX_TOTAL_EVENT_COUNT =
            "fledge_ad_counter_histogram_absolute_max_total_event_count";
    public static final String KEY_FLEDGE_AD_COUNTER_HISTOGRAM_LOWER_MAX_TOTAL_EVENT_COUNT =
            "fledge_ad_counter_histogram_lower_max_total_event_count";
    public static final String KEY_FLEDGE_AD_COUNTER_HISTOGRAM_ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT =
            "fledge_ad_counter_histogram_absolute_max_per_buyer_event_count";
    public static final String KEY_FLEDGE_AD_COUNTER_HISTOGRAM_LOWER_MAX_PER_BUYER_EVENT_COUNT =
            "fledge_ad_counter_histogram_lower_max_per_buyer_event_count";

    // FLEDGE Off device ad selection keys
    public static final String KEY_FLEDGE_AD_SELECTION_OFF_DEVICE_OVERALL_TIMEOUT_MS =
            "fledge_ad_selection_off_device_overall_timeout_ms";
    public static final String KEY_FLEDGE_AD_SELECTION_BIDDING_LOGIC_JS_VERSION =
            "fledge_ad_selection_bidding_logic_js_version";
    // Whether to call trusted servers for off device ad selection.
    public static final String KEY_FLEDGE_AD_SELECTION_OFF_DEVICE_ENABLED =
            "fledge_ad_selection_off_device_enabled";
    public static final String KEY_FLEDGE_AD_SELECTION_PREBUILT_URI_ENABLED =
            "fledge_ad_selection_ad_selection_prebuilt_uri_enabled";
    // Whether to compress the request object when calling trusted servers for off device ad
    // selection.
    public static final String KEY_FLEDGE_AD_SELECTION_OFF_DEVICE_REQUEST_COMPRESSION_ENABLED =
            "fledge_ad_selection_off_device_request_compression_enabled";

    // Event-level debug reporting for Protected Audience.
    public static final String KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_ENABLED =
            "fledge_event_level_debug_reporting_enabled";
    public static final String KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORT_SEND_IMMEDIATELY =
            "fledge_event_level_debug_report_send_immediately";
    public static final String KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_BATCH_DELAY_SECONDS =
            "fledge_event_level_debug_reporting_batch_delay_seconds";
    public static final String KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_MAX_ITEMS_PER_BATCH =
            "fledge_event_level_debug_reporting_max_items_per_batch";
    public static final String KEY_FLEDGE_DEBUG_REPORTI_SENDER_JOB_NETWORK_CONNECT_TIMEOUT_MS =
            "fledge_debug_report_sender_job_network_connect_timeout_ms";
    public static final String KEY_FLEDGE_DEBUG_REPORTI_SENDER_JOB_NETWORK_READ_TIMEOUT_MS =
            "fledge_debug_report_sender_job_network_read_timeout_ms";
    public static final String KEY_FLEDGE_DEBUG_REPORTI_SENDER_JOB_MAX_TIMEOUT_MS =
            "fledge_debug_report_sender_job_max_timeout_ms";
    public static final String KEY_FLEDGE_DEBUG_REPORT_SENDER_JOB_PERIOD_MS =
            "fledge_debug_report_sender_job_period_ms";
    public static final String KEY_FLEDGE_DEBUG_REPORT_SENDER_JOB_FLEX_MS =
            "fledge_debug_report_sender_job_flex_ms";

    // Server-auction flags for Protected Audience.
    public static final String KEY_FLEDGE_AUCTION_SERVER_ENABLED = "fledge_auction_server_enabled";
    public static final String KEY_FLEDGE_AUCTION_SERVER_ENABLED_FOR_REPORT_IMPRESSION =
            "fledge_auction_server_enabled_for_report_impression";
    public static final String KEY_FLEDGE_AUCTION_SERVER_ENABLED_FOR_REPORT_EVENT =
            "fledge_auction_server_enabled_for_report_event";
    public static final String KEY_FLEDGE_AUCTION_SERVER_ENABLED_FOR_UPDATE_HISTOGRAM =
            "fledge_auction_server_enabled_for_update_histogram";
    public static final String KEY_FLEDGE_AUCTION_SERVER_ENABLED_FOR_SELECT_ADS_MEDIATION =
            "fledge_auction_server_enabled_for_select_ads_mediation";
    public static final String KEY_FLEDGE_AUCTION_SERVER_ENABLE_AD_FILTER_IN_GET_AD_SELECTION_DATA =
            "fledge_auction_server_enable_ad_filter_in_get_ad_selection_data";
    public static final String KEY_FLEDGE_AUCTION_SERVER_PAYLOAD_BUCKET_SIZES =
            "fledge_auction_server_payload_bucket_sizes";
    public static final String KEY_FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_URI =
            "fledge_auction_server_auction_key_fetch_uri";
    public static final String KEY_FLEDGE_AUCTION_SERVER_AUCTION_KEY_SHARDING =
            "fledge_auction_server_auction_key_sharding";
    public static final String KEY_FLEDGE_AUCTION_SERVER_JOIN_KEY_FETCH_URI =
            "fledge_auction_server_join_key_fetch_uri";
    public static final String KEY_FLEDGE_AUCTION_SERVER_ENCRYPTION_KEY_MAX_AGE_SECONDS =
            "fledge_auction_server_encryption_key_max_age_seconds";
    public static final String KEY_FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_KEM_ID =
            "fledge_auction_server_encryption_algorithm_kem_id";
    public static final String KEY_FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_KDF_ID =
            "fledge_auction_server_encryption_algorithm_kdf_id";
    public static final String KEY_FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_AEAD_ID =
            "fledge_auction_server_encryption_algorithm_aead_id";
    public static final String KEY_FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_TIMEOUT_MS =
            "fledge_auction_server_auction_key_fetch_timeout_ms";
    public static final String KEY_FLEDGE_AUCTION_SERVER_OVERALL_TIMEOUT_MS =
            "fledge_auction_server_overall_timeout_ms";
    public static final String KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_ENABLED =
            "fledge_auction_server_background_key_fetch_job_enabled";
    public static final String KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_AUCTION_KEY_FETCH_ENABLED =
            "fledge_auction_server_background_auction_key_fetch_enabled";
    public static final String KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_JOIN_KEY_FETCH_ENABLED =
            "fledge_auction_server_background_join_key_fetch_enabled";
    public static final String KEY_FLEDGE_AUCTION_SERVER_FORCE_SEARCH_WHEN_OWNER_IS_ABSENT_ENABLED =
            "fledge_auction_server_force_search_when_owner_is_absent_enabled";
    public static final String
            KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_CONNECT_TIMEOUT_MS =
                    "fledge_auction_server_background_key_fetch_network_connect_timeout_ms";
    public static final String
            KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_READ_TIMEOUT_MS =
                    "fledge_auction_server_background_key_fetch_network_read_timeout_ms";
    public static final String KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RESPONSE_SIZE_B =
            "fledge_auction_server_background_key_fetch_max_response_size_b";

    public static final String KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RUNTIME_MS =
            "fledge_auction_server_background_key_fetch_max_runtime_ms";

    public static final String KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_PERIOD_MS =
            "fledge_auction_server_background_key_fetch_job_period_ms";

    public static final String KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_FLEX_MS =
            "fledge_auction_server_background_key_fetch_job_flex_ms";
    public static final String KEY_FLEDGE_AUCTION_SERVER_COMPRESSION_ALGORITHM_VERSION =
            "fledge_auction_server_compression_algorithm_version";
    public static final String KEY_FLEDGE_AUCTION_SERVER_PAYLOAD_FORMAT_VERSION =
            "fledge_auction_server_payload_format_version";
    public static final String KEY_FLEDGE_AUCTION_SERVER_ENABLE_DEBUG_REPORTING =
            "fledge_auction_server_enable_debug_reporting";
    public static final String KEY_FLEDGE_AUCTION_SERVER_AD_RENDER_ID_MAX_LENGTH =
            "fledge_auction_server_ad_render_id_max_length";
    public static final String KEY_FLEDGE_AUCTION_SERVER_AD_RENDER_ID_ENABLED =
            "fledge_auction_server_ad_render_id_enabled";

    // Fledge invoking app status keys
    public static final String KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION =
            "fledge_ad_selection_enforce_foreground_status_run_ad_selection";
    public static final String KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION =
            "fledge_ad_selection_enforce_foreground_status_report_impression";
    public static final String KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_INTERACTION =
            "fledge_ad_selection_enforce_foreground_status_report_interaction";
    public static final String KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDE =
            "fledge_ad_selection_enforce_foreground_status_ad_selection_override";
    public static final String KEY_FOREGROUND_STATUS_LEVEL = "foreground_validation_status_level";
    public static final String KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE =
            "fledge_ad_selection_enforce_foreground_status_custom_audience";

    // Topics invoking app status key.
    public static final String KEY_ENFORCE_FOREGROUND_STATUS_TOPICS =
            "topics_enforce_foreground_status";

    // AdId invoking app status key.
    public static final String KEY_ENFORCE_FOREGROUND_STATUS_ADID =
            "adid_enforce_foreground_status";

    // Fledge JS isolate setting keys
    public static final String KEY_ENFORCE_ISOLATE_MAX_HEAP_SIZE =
            "fledge_js_isolate_enforce_max_heap_size";
    public static final String KEY_ISOLATE_MAX_HEAP_SIZE_BYTES =
            "fledge_js_isolate_max_heap_size_bytes";

    // AppSetId invoking app status key.
    public static final String KEY_ENFORCE_FOREGROUND_STATUS_APPSETID =
            "appsetid_enforce_foreground_status";

    // MDD keys.
    public static final String KEY_DOWNLOADER_CONNECTION_TIMEOUT_MS =
            "downloader_connection_timeout_ms";
    public static final String KEY_DOWNLOADER_READ_TIMEOUT_MS = "downloader_read_timeout_ms";
    public static final String KEY_DOWNLOADER_MAX_DOWNLOAD_THREADS =
            "downloader_max_download_threads";
    public static final String KEY_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL =
            "mdd_topics_classifier_manifest_file_url";

    // Killswitch keys
    public static final String KEY_GLOBAL_KILL_SWITCH = "global_kill_switch";
    public static final String KEY_MEASUREMENT_KILL_SWITCH = "measurement_kill_switch";
    public static final String KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH =
            "measurement_api_delete_registrations_kill_switch";
    public static final String KEY_MEASUREMENT_API_STATUS_KILL_SWITCH =
            "measurement_api_status_kill_switch";
    public static final String KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH =
            "measurement_api_register_source_kill_switch";
    public static final String KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH =
            "measurement_api_register_trigger_kill_switch";
    public static final String KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH =
            "measurement_api_register_web_source_kill_switch";
    public static final String KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH =
            "measurement_api_register_web_trigger_kill_switch";
    public static final String KEY_MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH =
            "measurement_job_aggregate_fallback_reporting_kill_switch";
    public static final String KEY_MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH =
            "measurement_job_aggregate_reporting_kill_switch";
    public static final String KEY_MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH =
            "measurement_job_attribution_kill_switch";
    public static final String KEY_MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH =
            "measurement_job_delete_expired_kill_switch";
    public static final String KEY_MEASUREMENT_JOB_DELETE_UNINSTALLED_KILL_SWITCH =
            "measurement_job_delete_uninstalled_kill_switch";
    public static final String KEY_MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH =
            "measurement_job_event_fallback_reporting_kill_switch";
    public static final String KEY_MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH =
            "measurement_job_event_reporting_kill_switch";
    public static final String KEY_MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH =
            "measurement_receiver_install_attribution_kill_switch";
    public static final String KEY_MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH =
            "measurement_receiver_delete_packages_kill_switch";
    public static final String KEY_MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH =
            "measurement_job_registration_job_queue_kill_switch";

    public static final String KEY_MEASUREMENT_REGISTRATION_FALLBACK_JOB_KILL_SWITCH =
            "measurement_job_registration_fallback_job_kill_switch";
    public static final String KEY_MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH =
            "measurement_rollback_deletion_kill_switch";

    public static final String KEY_MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH =
            "measurement_rollback_deletion_app_search_kill_switch";
    public static final String KEY_TOPICS_KILL_SWITCH = "topics_kill_switch";
    public static final String KEY_TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH =
            "topics_on_device_classifier_kill_switch";
    public static final String KEY_MDD_BACKGROUND_TASK_KILL_SWITCH =
            "mdd_background_task_kill_switch";
    public static final String KEY_MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH =
            "measurement_debug_reporting_fallback_job_kill_switch";
    public static final String KEY_MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH =
            "measurement_verbose_debug_reporting_fallback_job_kill_switch";
    public static final String KEY_MDD_LOGGER_KILL_SWITCH = "mdd_logger_kill_switch";
    public static final String KEY_MEASUREMENT_JOB_DEBUG_REPORTING_KILL_SWITCH =
            "measurement_job_debug_reporting_kill_switch";
    public static final String KEY_MEASUREMENT_JOB_VERBOSE_DEBUG_REPORTING_KILL_SWITCH =
            "measurement_job_verbose_debug_reporting_kill_switch";

    public static final String KEY_ADID_KILL_SWITCH = "adid_kill_switch";
    public static final String KEY_APPSETID_KILL_SWITCH = "appsetid_kill_switch";
    public static final String KEY_FLEDGE_SELECT_ADS_KILL_SWITCH = "fledge_select_ads_kill_switch";
    public static final String KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH =
            "fledge_custom_audience_service_kill_switch";
    public static final String KEY_FLEDGE_AUCTION_SERVER_KILL_SWITCH =
            "fledge_auction_server_kill_switch";
    public static final String KEY_BACKGROUND_JOBS_LOGGING_KILL_SWITCH =
            "background_jobs_logging_kill_switch";
    public static final String KEY_FLEDGE_ON_DEVICE_AUCTION_KILL_SWITCH =
            "fledge_on_device_auction_kill_switch";

    public static final String KEY_PROTECTED_SIGNALS_SERVICE_KILL_SWITCH =
            "protected_signals_service_kill_switch";

    // App/SDK AllowList/DenyList keys
    public static final String KEY_PPAPI_APP_ALLOW_LIST = "ppapi_app_allow_list";

    public static final String KEY_MSMT_API_APP_ALLOW_LIST = "msmt_api_app_allow_list";
    public static final String KEY_MSMT_API_APP_BLOCK_LIST = "msmt_api_app_block_list";

    public static final String KEY_PPAPI_APP_SIGNATURE_ALLOW_LIST =
            "ppapi_app_signature_allow_list";

    // AdServices APK sha certs.
    public static final String KEY_ADSERVICES_APK_SHA_CERTS = "adservices_apk_sha_certs";

    // Rate Limit keys
    public static final String KEY_SDK_REQUEST_PERMITS_PER_SECOND =
            "sdk_request_permits_per_second";
    public static final String KEY_ADID_REQUEST_PERMITS_PER_SECOND =
            "adid_request_permits_per_second";
    public static final String KEY_APPSETID_REQUEST_PERMITS_PER_SECOND =
            "appsetid_request_permits_per_second";
    public static final String KEY_MEASUREMENT_REGISTER_SOURCE_REQUEST_PERMITS_PER_SECOND =
            "measurement_register_source_request_permits_per_second";
    public static final String KEY_MEASUREMENT_REGISTER_SOURCES_REQUEST_PERMITS_PER_SECOND =
            "measurement_register_sources_request_permits_per_second";
    public static final String KEY_MEASUREMENT_REGISTER_WEB_SOURCE_REQUEST_PERMITS_PER_SECOND =
            "measurement_register_web_source_request_permits_per_second";
    public static final String KEY_MEASUREMENT_REGISTER_TRIGGER_REQUEST_PERMITS_PER_SECOND =
            "measurement_register_trigger_request_permits_per_second";
    public static final String KEY_MEASUREMENT_REGISTER_WEB_TRIGGER_REQUEST_PERMITS_PER_SECOND =
            "measurement_register_web_trigger_request_permits_per_second";
    public static final String KEY_TOPICS_API_APP_REQUEST_PERMITS_PER_SECOND =
            "topics_api_app_request_permits_per_second";
    public static final String KEY_TOPICS_API_SDK_REQUEST_PERMITS_PER_SECOND =
            "topics_api_sdk_request_permits_per_second";
    public static final String KEY_FLEDGE_REPORT_INTERACTION_REQUEST_PERMITS_PER_SECOND =
            "fledge_report_interaction_request_permits_per_second";

    // Adservices enable status keys.
    public static final String KEY_ADSERVICES_ENABLED = "adservice_enabled";

    // AdServices error logging enabled
    public static final String KEY_ADSERVICES_ERROR_LOGGING_ENABLED =
            "adservice_error_logging_enabled";

    // Disable enrollment check
    public static final String KEY_DISABLE_TOPICS_ENROLLMENT_CHECK =
            "disable_topics_enrollment_check";
    public static final String KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK =
            "disable_fledge_enrollment_check";

    // Disable Measurement enrollment check.
    public static final String KEY_DISABLE_MEASUREMENT_ENROLLMENT_CHECK =
            "disable_measurement_enrollment_check";

    public static final String KEY_ENABLE_ENROLLMENT_TEST_SEED = "enable_enrollment_test_seed";

    // Enrollment Mdd Deletion Feature Enabled check

    public static final String KEY_ENROLLMENT_MDD_RECORD_DELETION_ENABLED =
            "enable_enrollment_mdd_record_deletion";

    // Consent Notification interval begin ms.
    public static final String KEY_CONSENT_NOTIFICATION_INTERVAL_BEGIN_MS =
            "consent_notification_interval_begin_ms";

    // Consent Notification interval end ms.
    public static final String KEY_CONSENT_NOTIFICATION_INTERVAL_END_MS =
            "consent_notification_interval_end_ms";

    // Consent Notification minimal delay before interval ms.
    public static final String KEY_CONSENT_NOTIFICATION_MINIMAL_DELAY_BEFORE_INTERVAL_ENDS =
            "consent_notification_minimal_delay_before_interval_ends";

    // Consent Notification debug mode keys.
    public static final String KEY_CONSENT_NOTIFICATION_DEBUG_MODE =
            "consent_notification_debug_mode";

    // Consent Manager debug mode keys.
    public static final String KEY_CONSENT_MANAGER_DEBUG_MODE = "consent_manager_debug_mode";

    // Consent notification activity debug mode keys.
    public static final String KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE =
            "consent_notification_activity_debug_mode";

    // Source of truth to get consent for PPAPI
    public static final String KEY_CONSENT_SOURCE_OF_TRUTH = "consent_source_of_truth";
    public static final String KEY_BLOCKED_TOPICS_SOURCE_OF_TRUTH =
            "blocked_topics_source_of_truth";

    // App/SDK AllowList/DenyList keys that have access to the web registration APIs
    public static final String KEY_WEB_CONTEXT_CLIENT_ALLOW_LIST = "web_context_client_allow_list";

    // Max response payload size allowed per source/trigger registration
    public static final String KEY_MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES =
            "max_response_based_registration_size_bytes";

    // UI keys
    public static final String KEY_UI_FEATURE_TYPE_LOGGING_ENABLED =
            "ui_feature_type_logging_enabled";

    public static final String KEY_CONSENT_NOTIFICATION_RESET_TOKEN =
            "consent_notification_reset_token";

    public static final String KEY_IS_EEA_DEVICE_FEATURE_ENABLED = "is_eea_device_feature_enabled";

    public static final String KEY_IS_EEA_DEVICE = "is_eea_device";

    public static final String KEY_RECORD_MANUAL_INTERACTION_ENABLED =
            "record_manual_interaction_enabled";

    public static final String KEY_IS_BACK_COMPACT_ACTIVITY_FEATURE_ENABLED =
            "is_check_activity_feature_enabled";

    public static final String KEY_UI_OTA_STRINGS_MANIFEST_FILE_URL =
            "mdd_ui_ota_strings_manifest_file_url";

    public static final String KEY_UI_OTA_STRINGS_FEATURE_ENABLED =
            "ui_ota_strings_feature_enabled";

    public static final String KEY_UI_OTA_STRINGS_DOWNLOAD_DEADLINE =
            "ui_ota_strings_download_deadline";

    public static final String KEY_UI_EEA_COUNTRIES = "ui_eea_countries";

    public static final String KEY_UI_DIALOGS_FEATURE_ENABLED = "ui_dialogs_feature_enabled";

    public static final String KEY_UI_DIALOG_FRAGMENT_ENABLED = "ui_dialog_fragment_enabled";

    public static final String KEY_UI_TOGGLE_SPEED_BUMP_ENABLED = "ui_toggle_speed_bump_enabled";

    public static final String KEY_GA_UX_FEATURE_ENABLED = "ga_ux_enabled";

    public static final String KEY_DEBUG_UX = "debug_ux";

    // Back-compat keys
    public static final String KEY_COMPAT_LOGGING_KILL_SWITCH = "compat_logging_kill_switch";

    public static final String ADSERVICES_CONSENT_MIGRATION_LOGGING_ENABLED =
            "adservices_consent_migration_logging_enabled";

    public static final String KEY_ENABLE_BACK_COMPAT = "enable_back_compat";

    public static final String KEY_ENABLE_APPSEARCH_CONSENT_DATA = "enable_appsearch_consent_data";

    // Whether to call trusted servers for off device ad selection.
    public static final String KEY_OFF_DEVICE_AD_SELECTION_ENABLED =
            "enable_off_device_ad_selection";

    // Interval in which to run Registration Job Queue Service.
    public static final String KEY_ASYNC_REGISTRATION_JOB_QUEUE_INTERVAL_MS =
            "key_async_registration_job_queue_interval_ms";

    // Enrollment flags.
    public static final String KEY_ENROLLMENT_BLOCKLIST_IDS = "enrollment_blocklist_ids";
    public static final String KEY_ENROLLMENT_ENABLE_LIMITED_LOGGING =
            "enrollment_enable_limited_logging";

    // New Feature Flags
    public static final String KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED =
            "fledge_register_ad_beacon_enabled";
    public static final String KEY_FLEDGE_CPC_BILLING_ENABLED = "fledge_cpc_billing_enabled";
    public static final String KEY_FLEDGE_DATA_VERSION_HEADER_ENABLED =
            "fledge_data_version_header_enabled";

    public static final String KEY_MEASUREMENT_DEBUG_JOIN_KEY_HASH_LIMIT =
            "measurement_debug_join_key_hash_limit";

    public static final String KEY_MEASUREMENT_DEBUG_JOIN_KEY_ENROLLMENT_ALLOWLIST =
            "measurement_debug_join_key_enrollment_allowlist";

    public static final String KEY_MEASUREMENT_DEBUG_KEY_AD_ID_MATCHING_LIMIT =
            "measurement_debug_key_ad_id_matching_limit";
    public static final String KEY_MEASUREMENT_DEBUG_KEY_AD_ID_MATCHING_ENROLLMENT_BLOCKLIST =
            "measurement_debug_key_ad_id_matching_enrollment_blocklist";

    public static final String KEY_MEASUREMENT_FLEXIBLE_EVENT_REPORTING_API_ENABLED =
            "measurement_flexible_event_reporting_api_enabled";

    public static final String KEY_MEASUREMENT_FLEX_LITE_API_ENABLED =
            "measurement_flex_lite_api_enabled";

    public static final String KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_EVENT =
            "measurement_flex_api_max_information_gain_event";

    public static final String KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_NAVIGATION =
            "measurement_flex_api_max_information_gain_navigation";

    public static final String KEY_MEASUREMENT_FLEX_API_MAX_EVENT_REPORTS =
            "measurement_flex_api_max_event_reports";

    public static final String KEY_MEASUREMENT_FLEX_API_MAX_EVENT_REPORT_WINDOWS =
            "measurement_flex_api_max_event_report_windows";

    public static final String KEY_MEASUREMENT_FLEX_API_MAX_TRIGGER_DATA_CARDINALITY =
            "measurement_flex_api_max_trigger_data_cardinality";

    public static final String KEY_MEASUREMENT_MINIMUM_EVENT_REPORT_WINDOW_IN_SECONDS =
            "measurement_minimum_event_report_window_in_seconds";

    public static final String KEY_MEASUREMENT_MINIMUM_AGGREGATABLE_REPORT_WINDOW_IN_SECONDS =
            "measurement_minimum_aggregatable_report_window_in_seconds";

    public static final String KEY_MEASUREMENT_MAX_SOURCES_PER_PUBLISHER =
            "measurement_max_sources_per_publisher";

    public static final String KEY_MEASUREMENT_MAX_TRIGGERS_PER_DESTINATION =
            "measurement_max_triggers_per_destination";

    public static final String KEY_MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_DESTINATION =
            "measurement_max_aggregate_reports_per_destination";

    public static final String KEY_MEASUREMENT_MAX_EVENT_REPORTS_PER_DESTINATION =
            "measurement_max_event_reports_per_destination";

    public static final String KEY_MEASUREMENT_ENABLE_MAX_AGGREGATE_REPORTS_PER_SOURCE =
            "measurement_enable_max_aggregate_reports_per_source";

    public static final String KEY_MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_SOURCE =
            "measurement_max_aggregate_reports_per_source";

    public static final String KEY_MEASUREMENT_MAX_AGGREGATE_KEYS_PER_SOURCE_REGISTRATION =
            "measurement_max_aggregate_keys_per_source_registration";

    public static final String KEY_MEASUREMENT_MAX_AGGREGATE_KEYS_PER_TRIGGER_REGISTRATION =
            "measurement_max_aggregate_keys_per_trigger_registration";

    public static final String KEY_MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS =
            "measurement_min_event_report_delay_millis";

    public static final String KEY_MEASUREMENT_ENABLE_CONFIGURABLE_EVENT_REPORTING_WINDOWS =
            "measurement_enable_configurable_event_reporting_windows";

    public static final String KEY_MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS =
            "measurement_event_reports_vtc_early_reporting_windows";

    public static final String KEY_MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS =
            "measurement_event_reports_ctc_early_reporting_windows";

    public static final String KEY_MEASUREMENT_ENABLE_CONFIGURABLE_AGGREGATE_REPORT_DELAY =
            "measurement_enable_configurable_aggregate_report_delay";

    public static final String KEY_MEASUREMENT_AGGREGATE_REPORT_DELAY_CONFIG =
            "measurement_aggregate_report_delay_config";

    public static final String KEY_MEASUREMENT_ENABLE_LOOKBACK_WINDOW_FILTER =
            "measurement_enable_lookback_window_filter";

    public static final String KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED =
            "fledge_measurement_report_and_register_event_api_enabled";

    public static final String
            KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_FALLBACK_ENABLED =
                    "fledge_measurement_report_and_register_event_api_fallback_enabled";

    public static final String KEY_ENABLE_LOGGED_TOPIC = "enable_logged_topic";

    // Database Schema Version Flags
    public static final String KEY_ENABLE_DATABASE_SCHEMA_VERSION_8 =
            "enable_database_schema_version_8";

    public static final String KEY_EU_NOTIF_FLOW_CHANGE_ENABLED = "eu_notif_flow_change_enabled";

    public static final String KEY_NOTIFICATION_DISMISSED_ON_CLICK =
            "notification_dmsmissed_on_click";

    public static final String KEY_U18_UX_ENABLED = "u18_ux_enabled";

    public static final String KEY_ENABLE_AD_SERVICES_SYSTEM_API = "enable_ad_services_system_api";

    public static final String KEY_IS_U18_UX_DETENTION_CHANNEL_ENABLED =
            "is_u18_ux_detention_channel_enabled";
}
