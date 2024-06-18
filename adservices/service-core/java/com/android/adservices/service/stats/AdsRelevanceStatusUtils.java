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

import android.annotation.IntDef;

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
}
