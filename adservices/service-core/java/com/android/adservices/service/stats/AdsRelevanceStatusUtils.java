/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.stats;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PERSIST_AD_SELECTION_RESULT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__REPORT_IMPRESSION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__REPORT_INTERACTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;

import android.annotation.IntDef;
import android.os.Binder;

import com.android.adservices.errorlogging.ErrorLogUtil;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Utility class containing status enum types and functions used by various stats objects.
 *
 * <p>Those status codes are internal only.
 *
 * @hide
 */
public class AdsRelevanceStatusUtils {
    /** The Ad Filtering is UNSET process. */
    public static final int FILTER_PROCESS_TYPE_UNSET = 0;
    /** The Ad Filtering is used in Custom Audiences process. */
    public static final int FILTER_PROCESS_TYPE_CUSTOM_AUDIENCES = 1;
    /** The Ad Filtering is used in Contextual Ads process. */
    public static final int FILTER_PROCESS_TYPE_CONTEXTUAL_ADS = 2;

    /** The beacon comes from UNSET winner. */
    public static final int BEACON_SOURCE_UNSET = 0;
    /** The beacon comes from PROTECTED_SIGNALS winner. */
    public static final int BEACON_SOURCE_PROTECTED_SIGNALS = 1;
    /** The beacon comes from CUSTOM_AUDIENCE winner. */
    public static final int BEACON_SOURCE_CUSTOM_AUDIENCE = 2;

    /** The status of Json processing is UNSET. */
    public static final int JSON_PROCESSING_STATUS_UNSET = 0;
    /** The status of Json processing is SUCCESS. */
    public static final int JSON_PROCESSING_STATUS_SUCCESS = 1;
    /** The status of Json processing is TOO_BIG. */
    public static final int JSON_PROCESSING_STATUS_TOO_BIG = 2;
    /** The status of Json processing is SYNTACTIC_ERROR. */
    public static final int JSON_PROCESSING_STATUS_SYNTACTIC_ERROR = 3;
    /** The status of Json processing is SEMANTIC_ERROR. */
    public static final int JSON_PROCESSING_STATUS_SEMANTIC_ERROR = 4;
    /** The status of Json processing is OTHER_ERROR. */
    public static final int JSON_PROCESSING_STATUS_OTHER_ERROR = 5;

    /** The status of encoding fetch is UNSET. */
    public static final int ENCODING_FETCH_STATUS_UNSET = 0;
    /** The status of encoding fetch is SUCCESS. */
    public static final int ENCODING_FETCH_STATUS_SUCCESS = 1;
    /** The status of encoding fetch is TOO_BIG. */
    public static final int ENCODING_FETCH_STATUS_TOO_BIG = 2;
    /** The status of encoding fetch is TIMEOUT. */
    public static final int ENCODING_FETCH_STATUS_TIMEOUT = 3;
    /** The status of encoding fetch is NETWORK_FAILURE. */
    public static final int ENCODING_FETCH_STATUS_NETWORK_FAILURE = 4;
    /** The status of encoding fetch is OTHER_FAILURE. */
    public static final int ENCODING_FETCH_STATUS_OTHER_FAILURE = 5;

    /** The status of JavaScript run is UNSET. */
    public static final int JS_RUN_STATUS_UNSET = 0;
    /** The status of JavaScript run is SUCCESS. */
    public static final int JS_RUN_STATUS_SUCCESS = 1;
    /** The status of JavaScript run is TIMEOUT. */
    public static final int JS_RUN_STATUS_TIMEOUT = 2;
    /** The status of JavaScript run is JS_SYNTAX_ERROR. */
    public static final int JS_RUN_STATUS_JS_SYNTAX_ERROR = 3;
    /** The status of JavaScript run is OUTPUT_SYNTAX_ERROR. */
    public static final int JS_RUN_STATUS_OUTPUT_SYNTAX_ERROR = 4;
    /** The status of JavaScript run is OUTPUT_SEMANTIC_ERROR. */
    public static final int JS_RUN_STATUS_OUTPUT_SEMANTIC_ERROR = 5;
    /** The status of JavaScript run is OUTPUT_NON_ZERO_RESULT. */
    public static final int JS_RUN_STATUS_OUTPUT_NON_ZERO_RESULT = 6;
    /** The status of JavaScript run is DB_PERSIST_FAILURE. */
    public static final int JS_RUN_STATUS_DB_PERSIST_FAILURE = 7;
    /** The status of JavaScript run is OTHER_FAILURE. */
    public static final int JS_RUN_STATUS_OTHER_FAILURE = 8;

    /** The status of JavaScript run is JS_REFERENCE_ERROR. */
    public static final int JS_RUN_STATUS_JS_REFERENCE_ERROR = 9;

    /** The auction winner type is UNSET. */
    public static final int WINNER_TYPE_UNSET = 0;
    /** The auction winner type is NO_WINNER. */
    public static final int WINNER_TYPE_NO_WINNER = 1;
    /** The auction winner type is CA_WINNER. */
    public static final int WINNER_TYPE_CA_WINNER = 2;
    /** The auction winner type is PAS_WINNER. */
    public static final int WINNER_TYPE_PAS_WINNER = 3;

    /** Leaving the following 6 size type.
     * These not tied to specific numbers gives us flexibility to change them later. */
    public static final int SIZE_UNSET = 0;
    public static final int SIZE_VERY_SMALL = 1;
    public static final int SIZE_SMALL = 2;
    public static final int SIZE_MEDIUM = 3;
    public static final int SIZE_LARGE = 4;
    public static final int SIZE_VERY_LARGE = 5;

    /** The auction coordinator source is UNSET. */
    public static final int SERVER_AUCTION_COORDINATOR_SOURCE_UNSET = 0;
    /** The auction coordinator source is DEFAULT. */
    public static final int SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT = 1;
    /** The auction coordinator source is API. */
    public static final int SERVER_AUCTION_COORDINATOR_SOURCE_API = 2;

    // We expect most JSONs to be small, at least initially, so we'll bucket more there.
    public static final long[] JSON_SIZE_BUCKETS = {100, 300, 1000, 5000};

    // Buckets for JS download latency in ms
    public static final long[] JS_DOWNLOAD_LATENCY_BUCKETS = {50, 200, 1000, 2000};

    // Buckets for JS execution latency in ms
    public static final long[] JS_EXECUTION_LATENCY_BUCKETS = {50, 200, 1000, 2000};

    /** The key fetch status is UNSET. */
    public static final int BACKGROUND_KEY_FETCH_STATUS_UNSET = 0;
    /** The key fetch status is NO_OP. */
    public static final int BACKGROUND_KEY_FETCH_STATUS_NO_OP = 1;
    /** The key fetch status is REFRESH_KEYS_INITIATED. */
    public static final int BACKGROUND_KEY_FETCH_STATUS_REFRESH_KEYS_INITIATED = 2;

    /** The server auction key fetch source is UNSET. */
    public static final int SERVER_AUCTION_KEY_FETCH_SOURCE_UNSET = 0;
    /** The server auction key fetch source is via a background fetch. */
    public static final int SERVER_AUCTION_KEY_FETCH_SOURCE_BACKGROUND_FETCH = 1;
    /** The server auction key fetch source is via an auction. */
    public static final int SERVER_AUCTION_KEY_FETCH_SOURCE_AUCTION = 2;

    /** The server auction encryption key source is UNSET. */
    public static final int SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_UNSET = 0;
    /** The server auction encryption key source is the database. */
    public static final int SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_DATABASE = 1;
    /** The server auction encryption key source is the network. */
    public static final int SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_NETWORK = 2;

    /** Buckets for per buyer signal size in the update signals process. */
    public static final long[] PER_BUYER_SIGNAL_SIZE_BUCKETS = {10, 100, 500, 5000};

    /** The topics reschedule epoch job status is unset. */
    public static final int TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_UNSET = 0;
    /** The topics reschedule epoch job status is success. */
    public static final int TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_RESCHEDULE_SUCCESS = 1;
    /** The topics reschedule epoch job status is skipped because of empty job scheduler. */
    public static final int
            TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_SKIP_RESCHEDULE_EMPTY_JOB_SCHEDULER = 2;
    /** The topics reschedule epoch job status is skipped because of empty pending job. */
    public static final int
            TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_SKIP_RESCHEDULE_EMPTY_PENDING_JOB = 3;

    /** The topics epoch job battery constraint is unknown setting. */
    public static final int TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_UNKNOWN_SETTING = 0;
    /** The topics epoch job battery constraint is requires charging. */
    public static final int TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_REQUIRES_CHARGING = 1;
    /** The topics epoch job battery constraint is battery not low. */
    public static final int TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_REQUIRES_BATTERY_NOT_LOW = 2;

    /** Schedule ca update failure during http call. */
    public static final int SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_HTTP_CALL = 0;

    /** Schedule ca update failure during join custom audience. */
    public static final int SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_JOIN_CA = 1;

    /** Schedule ca update failure during leaving custom audience. */
    public static final int SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_LEAVE_CA = 2;

    /** Schedule ca update failure during scheduling ca update for second hop. */
    public static final int SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_SCHEDULE_CA_UPDATE = 3;

    /** Unknown failure during schedule custom audience update by the background job. */
    public static final int SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_UNKNOWN = 0;

    /** Http error during schedule custom audience update by the background job. */
    public static final int SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_UNKNOWN_ERROR = 1;

    /**
     * Http server error during schedule custom audience update by the background job. Error code
     * indicating that the user has sent too many requests in a given amount of time and the service
     * received an HTTP 429 status code
     */
    public static final int SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_TOO_MANY_REQUESTS = 2;

    /**
     * Http server error during schedule custom audience update by the background job. Error code
     * indicating that the service received an HTTP 3xx status code
     */
    public static final int SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_REDIRECTION = 3;

    /**
     * Http server error during schedule custom audience update by the background job. Error code
     * indicating that the service received an HTTP 4xx status code
     */
    public static final int SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_CLIENT_ERROR = 4;

    /**
     * Http server error during schedule custom audience update by the background job. Error code
     * indicating that the service received an HTTP 5xx.
     */
    public static final int SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_SERVER_ERROR = 5;

    /** Json error during schedule custom audience update by the background job. */
    public static final int SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_JSON_ERROR = 6;

    /** Internal error during schedule custom audience update by the background job. */
    public static final int SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_INTERNAL_ERROR = 7;

    /**
     * Used for logging IO Exception thrown by the AdServicesHttpsClient. This exception is thrown
     * by IOException.
     */
    public static final int SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_IO_EXCEPTION = 8;

    /**
     * Used for logging HttpContentSizeException thrown by the AdServicesHttpsClient. This exception
     * is thrown when the http response is exceeds the maximum permitted value.
     */
    public static final int SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_CONTENT_SIZE_ERROR = 9;

    /** Unknown status for existing update in the database. */
    public static final int SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_UNKNOWN = 0;

    /** Schedule custom audience request overwriting an already existing update in the database. */
    public static final int
            SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_DID_OVERWRITE_EXISTING_UPDATE = 1;

    /** No existing update in the database present in the database. */
    public static final int SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_NO_EXISTING_UPDATE = 2;

    /**
     * Schedule custom audience request rejected because of an already existing update in the
     * database.
     */
    public static final int SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_REJECTED_BY_EXISTING_UPDATE =
            3;

    /** The PAS encoding source type is unset. */
    public static final int PAS_ENCODING_SOURCE_TYPE_UNSET = 0;

    /** The PAS raw signals are encoded in {@code PeriodicEncodingJobService}. */
    public static final int PAS_ENCODING_SOURCE_TYPE_ENCODING_JOB_SERVICE = 1;

    /** The PAS raw signals are encoded in {@code PeriodicSignalsServiceImpl}. */
    public static final int PAS_ENCODING_SOURCE_TYPE_SERVICE_IMPL = 2;

    /** The kind of winner did the beacon come from. */
    @IntDef(
            prefix = {"BEACON_SOURCE_"},
            value = {
                BEACON_SOURCE_UNSET,
                BEACON_SOURCE_PROTECTED_SIGNALS,
                BEACON_SOURCE_CUSTOM_AUDIENCE
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface BeaconSource {}

    /** The status of JSON processing. */
    @IntDef(
            prefix = {"JSON_PROCESSING_STATUS_"},
            value = {
                JSON_PROCESSING_STATUS_UNSET,
                JSON_PROCESSING_STATUS_SUCCESS,
                JSON_PROCESSING_STATUS_TOO_BIG,
                JSON_PROCESSING_STATUS_SYNTACTIC_ERROR,
                JSON_PROCESSING_STATUS_SEMANTIC_ERROR,
                JSON_PROCESSING_STATUS_OTHER_ERROR
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface JsonProcessingStatus {}

    /** The status of encoding fetch. */
    @IntDef(
            prefix = {"ENCODING_FETCH_STATUS_"},
            value = {
                ENCODING_FETCH_STATUS_UNSET,
                ENCODING_FETCH_STATUS_SUCCESS,
                ENCODING_FETCH_STATUS_TOO_BIG,
                ENCODING_FETCH_STATUS_TIMEOUT,
                ENCODING_FETCH_STATUS_NETWORK_FAILURE,
                ENCODING_FETCH_STATUS_OTHER_FAILURE
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface EncodingFetchStatus {}

    /** The status of JavaScript Run. */
    @IntDef(
            prefix = {"JS_RUN_STATUS_"},
            value = {
                JS_RUN_STATUS_UNSET,
                JS_RUN_STATUS_SUCCESS,
                JS_RUN_STATUS_TIMEOUT,
                JS_RUN_STATUS_JS_SYNTAX_ERROR,
                JS_RUN_STATUS_OUTPUT_SYNTAX_ERROR,
                JS_RUN_STATUS_OUTPUT_SEMANTIC_ERROR,
                JS_RUN_STATUS_OUTPUT_NON_ZERO_RESULT,
                JS_RUN_STATUS_DB_PERSIST_FAILURE,
                JS_RUN_STATUS_OTHER_FAILURE,
                JS_RUN_STATUS_JS_REFERENCE_ERROR
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface JsRunStatus {}

    /** The type of auction winner. */
    @IntDef(
            prefix = {"WINNER_TYPE_"},
            value = {
                WINNER_TYPE_UNSET,
                WINNER_TYPE_NO_WINNER,
                WINNER_TYPE_CA_WINNER,
                WINNER_TYPE_PAS_WINNER
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface WinnerType {}

    /** The size type of JSON or JavaScript. */
    @IntDef(
            prefix = {"SIZE_"},
            value = {
                SIZE_UNSET,
                SIZE_VERY_SMALL,
                SIZE_SMALL,
                SIZE_MEDIUM,
                SIZE_LARGE,
                SIZE_VERY_LARGE
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Size {}

    /** Filter processing type */
    @IntDef(
            prefix = {"FILTER_PROCESS_TYPE_"},
            value = {
                FILTER_PROCESS_TYPE_UNSET,
                FILTER_PROCESS_TYPE_CUSTOM_AUDIENCES,
                FILTER_PROCESS_TYPE_CONTEXTUAL_ADS
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FilterProcessType {}

    /** The type of server auction coordinator source. */
    @IntDef(
            prefix = {"SERVER_AUCTION_COORDINATOR_SOURCE_"},
            value = {
                SERVER_AUCTION_COORDINATOR_SOURCE_UNSET,
                SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT,
                SERVER_AUCTION_COORDINATOR_SOURCE_API
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ServerAuctionCoordinatorSource {}

    @IntDef(
            prefix = {"SCHEDULE_CUSTOM_AUDIENCE_UPDATE_EXISTING_UPDATE_STATUS_"},
            value = {
                SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_UNKNOWN,
                SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_DID_OVERWRITE_EXISTING_UPDATE,
                SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_NO_EXISTING_UPDATE,
                SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_REJECTED_BY_EXISTING_UPDATE
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ScheduleCustomAudienceUpdateExistingUpdateStatus {}

    @IntDef(
            prefix = {"SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION"},
            value = {
                SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_HTTP_CALL,
                SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_JOIN_CA,
                SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_LEAVE_CA,
                SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_SCHEDULE_CA_UPDATE
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ScheduleCustomAudienceUpdatePerformedFailureAction {}

    @IntDef(
            prefix = {"SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE"},
            value = {
                SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_UNKNOWN,
                SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_UNKNOWN_ERROR,
                SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_TOO_MANY_REQUESTS,
                SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_REDIRECTION,
                SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_CLIENT_ERROR,
                SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_SERVER_ERROR,
                SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_JSON_ERROR,
                SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_INTERNAL_ERROR,
                SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_IO_EXCEPTION,
                SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_CONTENT_SIZE_ERROR
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ScheduleCustomAudienceUpdatePerformedFailureType {}

    @IntDef(
            prefix = {"PAS_ENCODING_SOURCE_TYPE_"},
            value = {
                PAS_ENCODING_SOURCE_TYPE_UNSET,
                PAS_ENCODING_SOURCE_TYPE_ENCODING_JOB_SERVICE,
                PAS_ENCODING_SOURCE_TYPE_SERVICE_IMPL
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface PasEncodingSourceType {}

    /** Returns the size bucket for a raw value. */
    @Size
    public static int computeSize(long rawSize, long[] buckets) {
        if (rawSize < buckets[0]) {
            return SIZE_VERY_SMALL;
        } else if (rawSize < buckets[1]) {
            return SIZE_SMALL;
        } else if (rawSize < buckets[2]) {
            return SIZE_MEDIUM;
        } else if (rawSize < buckets[3]) {
            return SIZE_LARGE;
        }
        return SIZE_VERY_LARGE;
    }

    /** The status of the background key fetch. */
    @IntDef(
            prefix = {"BACKGROUND_KEY_FETCH_STATUS_"},
            value = {
                BACKGROUND_KEY_FETCH_STATUS_UNSET,
                BACKGROUND_KEY_FETCH_STATUS_NO_OP,
                BACKGROUND_KEY_FETCH_STATUS_REFRESH_KEYS_INITIATED,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface BackgroundKeyFetchStatus {}

    /** The source of the server auction key fetch. */
    @IntDef(
            prefix = {"SERVER_AUCTION_KEY_FETCH_SOURCE_"},
            value = {
                SERVER_AUCTION_KEY_FETCH_SOURCE_UNSET,
                SERVER_AUCTION_KEY_FETCH_SOURCE_BACKGROUND_FETCH,
                SERVER_AUCTION_KEY_FETCH_SOURCE_AUCTION
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ServerAuctionKeyFetchSource {}

    /** The source of the server auction encryption key. */
    @IntDef(
            prefix = {"SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_"},
            value = {
                SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_UNSET,
                SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_DATABASE,
                SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_NETWORK
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ServerAuctionEncryptionKeySource {}

    /** The status when forcing reschedule Topics API EpochJob. */
    @IntDef(
            prefix = {"TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_"},
            value = {
                TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_UNSET,
                TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_RESCHEDULE_SUCCESS,
                TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_SKIP_RESCHEDULE_EMPTY_JOB_SCHEDULER,
                TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_SKIP_RESCHEDULE_EMPTY_PENDING_JOB
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface TopicsRescheduleEpochJobStatus {}

    /** The Epoch job setting of the Topics API EpochJob. */
    @IntDef(
            prefix = {"TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_"},
            value = {
                TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_UNKNOWN_SETTING,
                TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_REQUIRES_CHARGING,
                TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_REQUIRES_BATTERY_NOT_LOW
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface TopicsEpochJobBatteryConstraint {}

    /**
     * Returns the Cel PP API name ID from AdServices API name ID.
     */
    public static int getCelPpApiNameId(int apiNameLoggingId) {
        int celPpApiNameId = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED;
        switch (apiNameLoggingId) {
            case AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS:
                celPpApiNameId = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;
                break;
            case AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA:
                celPpApiNameId = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA;
                break;
            case AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT:
                celPpApiNameId =
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PERSIST_AD_SELECTION_RESULT;
                break;
            case AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS:
                celPpApiNameId = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS;
                break;
            case AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE:
                celPpApiNameId = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__JOIN_CUSTOM_AUDIENCE;
                break;
            case AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE:
                celPpApiNameId = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__LEAVE_CUSTOM_AUDIENCE;
                break;
            case AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE:
                celPpApiNameId =
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE;
                break;
            case AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION:
                celPpApiNameId = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__REPORT_IMPRESSION;
                break;
            case AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION:
                celPpApiNameId = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__REPORT_INTERACTION;
                break;
            case AD_SERVICES_API_CALLED__API_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE:
                celPpApiNameId =
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE;
        }
        return celPpApiNameId;
    }

    /**
     * Clean the caller's identity and log CEL.
     * TODO(b/376542959): this is a temporary solution for CEL logs inside the Binder thread.
     */
    public static void logCelInsideBinderThread(int errorCode, int ppapiName) {
        long token = Binder.clearCallingIdentity();
        try {
            ErrorLogUtil.e(errorCode, ppapiName);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Clean the caller's identity and log CEL.
     * TODO(b/376542959): this is a temporary solution for CEL logs inside the Binder thread.
     */
    public static void logCelInsideBinderThread(Throwable tr, int errorCode, int ppapiName) {
        long token = Binder.clearCallingIdentity();
        try {
            ErrorLogUtil.e(tr, errorCode, ppapiName);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    // Takes an AdServicesApiName, and attempts to log the CEL error.
    public static void checkAndLogCelByApiNameLoggingId(Throwable throwable, int celErrorCode,
            int apiNameLoggingId) {
        checkPpapiNameAndLogCel(throwable, celErrorCode, getCelPpApiNameId(apiNameLoggingId));
    }

    /**
     * Logs a CEL error when the API name is not
     * AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED
     */
    public static void checkPpapiNameAndLogCel(Throwable throwable, int celErrorCode,
            int celApiNameId) {
        if (celApiNameId != AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED) {
            if (throwable != null) {
                ErrorLogUtil.e(throwable, celErrorCode, celApiNameId);
            } else {
                ErrorLogUtil.e(celErrorCode, celApiNameId);
            }
        }
    }
}