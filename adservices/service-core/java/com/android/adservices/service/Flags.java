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

package com.android.adservices.service;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.util.Dumpable;

import androidx.annotation.Nullable;

import com.android.adservices.data.adselection.DBRegisteredAdInteraction;
import com.android.adservices.service.adselection.AdOutcomeSelectorImpl;
import com.android.adservices.service.common.cache.FledgeHttpCache;

import com.google.common.collect.ImmutableList;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.TimeUnit;

/**
 * AdServices Feature Flags interface. This Flags interface hold the default values of Ad Services
 * Flags.
 */
public interface Flags extends Dumpable {
    /** Topics Epoch Job Period. */
    long TOPICS_EPOCH_JOB_PERIOD_MS = 7 * 86_400_000; // 7 days.

    /** Returns the max time period (in millis) between each epoch computation job run. */
    default long getTopicsEpochJobPeriodMs() {
        return TOPICS_EPOCH_JOB_PERIOD_MS;
    }

    /** Topics Epoch Job Flex. Note the minimum value system allows is +8h24m0s0ms */
    long TOPICS_EPOCH_JOB_FLEX_MS = 9 * 60 * 60 * 1000; // 5 hours.

    /** Returns flex for the Epoch computation job in Millisecond. */
    default long getTopicsEpochJobFlexMs() {
        return TOPICS_EPOCH_JOB_FLEX_MS;
    }

    /* The percentage that we will return a random topic from the Taxonomy. */
    int TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC = 5;

    /** Returns the percentage that we will return a random topic from the Taxonomy. */
    default int getTopicsPercentageForRandomTopic() {
        return TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC;
    }

    /** The number of top Topics for each epoch. */
    int TOPICS_NUMBER_OF_TOP_TOPICS = 5;

    /** Returns the number of top topics. */
    default int getTopicsNumberOfTopTopics() {
        return TOPICS_NUMBER_OF_TOP_TOPICS;
    }

    /** The number of random Topics for each epoch. */
    int TOPICS_NUMBER_OF_RANDOM_TOPICS = 1;

    /** Returns the number of top topics. */
    default int getTopicsNumberOfRandomTopics() {
        return TOPICS_NUMBER_OF_RANDOM_TOPICS;
    }

    /** How many epochs to look back when deciding if a caller has observed a topic before. */
    int TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS = 3;

    /**
     * Returns the number of epochs to look back when deciding if a caller has observed a topic
     * before.
     */
    default int getTopicsNumberOfLookBackEpochs() {
        return TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS;
    }

    /** Available types of classifier behaviours for the Topics API. */
    @IntDef(
            flag = true,
            value = {
                UNKNOWN_CLASSIFIER,
                ON_DEVICE_CLASSIFIER,
                PRECOMPUTED_CLASSIFIER,
                PRECOMPUTED_THEN_ON_DEVICE_CLASSIFIER
            })
    @Retention(RetentionPolicy.SOURCE)
    @interface ClassifierType {}

    /** Unknown classifier option. */
    int UNKNOWN_CLASSIFIER = 0;
    /** Only on-device classification. */
    int ON_DEVICE_CLASSIFIER = 1;
    /** Only Precomputed classification. */
    int PRECOMPUTED_CLASSIFIER = 2;
    /** Precomputed classification values are preferred over on-device classification values. */
    int PRECOMPUTED_THEN_ON_DEVICE_CLASSIFIER = 3;

    /* Type of classifier intended to be used by default. */
    @ClassifierType int DEFAULT_CLASSIFIER_TYPE = PRECOMPUTED_THEN_ON_DEVICE_CLASSIFIER;

    /** Returns the type of classifier currently used by Topics. */
    @ClassifierType
    default int getClassifierType() {
        return DEFAULT_CLASSIFIER_TYPE;
    }

    /** Number of top labels allowed for every app. */
    int CLASSIFIER_NUMBER_OF_TOP_LABELS = 3;

    /** Returns the number of top labels allowed for every app after the classification process. */
    default int getClassifierNumberOfTopLabels() {
        return CLASSIFIER_NUMBER_OF_TOP_LABELS;
    }

    /** Threshold value for classification values. */
    float CLASSIFIER_THRESHOLD = 0.1f;

    /** Returns the threshold value for classification values. */
    default float getClassifierThreshold() {
        return CLASSIFIER_THRESHOLD;
    }

    /** Number of max words allowed in the description for topics classifier. */
    int CLASSIFIER_DESCRIPTION_MAX_WORDS = 500;

    /** Returns the number of max words allowed in the description for topics classifier. */
    default int getClassifierDescriptionMaxWords() {
        return CLASSIFIER_DESCRIPTION_MAX_WORDS;
    }

    /** Number of max characters allowed in the description for topics classifier. */
    int CLASSIFIER_DESCRIPTION_MAX_LENGTH = 2500;

    /** Returns the number of max characters allowed in the description for topics classifier. */
    default int getClassifierDescriptionMaxLength() {
        return CLASSIFIER_DESCRIPTION_MAX_LENGTH;
    }

    // TODO(b/243829477): Remove this flag when flow of pushing models is refined.
    /**
     * Whether classifier should force using bundled files. This flag is mainly used in CTS tests to
     * force using precomputed_app_list to avoid model mismatch due to update. Default value is
     * false which means to use downloaded files.
     */
    boolean CLASSIFIER_FORCE_USE_BUNDLED_FILES = false;

    /** Returns whether to force using bundled files */
    default boolean getClassifierForceUseBundledFiles() {
        return CLASSIFIER_FORCE_USE_BUNDLED_FILES;
    }

    /* The default period for the Maintenance job. */
    long MAINTENANCE_JOB_PERIOD_MS = 86_400_000; // 1 day.

    /** Returns the max time period (in millis) between each idle maintenance job run. */
    default long getMaintenanceJobPeriodMs() {
        return MAINTENANCE_JOB_PERIOD_MS;
    }

    /* The default flex for Maintenance Job. */
    long MAINTENANCE_JOB_FLEX_MS = 3 * 60 * 60 * 1000; // 3 hours.

    /** Returns flex for the Daily Maintenance job in Millisecond. */
    default long getMaintenanceJobFlexMs() {
        return MAINTENANCE_JOB_FLEX_MS;
    }

    /* The default min time period (in millis) between each event main reporting job run. */
    long MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS = 4 * 60 * 60 * 1000; // 4 hours.

    /** Returns min time period (in millis) between each event main reporting job run. */
    default long getMeasurementEventMainReportingJobPeriodMs() {
        return MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS;
    }

    /* The default min time period (in millis) between each event fallback reporting job run. */
    long MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS = 24 * 60 * 60 * 1000; // 24 hours.

    /** Returns min time period (in millis) between each event fallback reporting job run. */
    default long getMeasurementEventFallbackReportingJobPeriodMs() {
        return MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS;
    }

    /* The default URL for fetching public encryption keys for aggregatable reports. */
    String MEASUREMENT_AGGREGATE_ENCRYPTION_KEY_COORDINATOR_URL =
            "https://publickeyservice.aws.privacysandboxservices.com/v1alpha/publicKeys";

    /** Returns the URL for fetching public encryption keys for aggregatable reports. */
    default String getMeasurementAggregateEncryptionKeyCoordinatorUrl() {
        return MEASUREMENT_AGGREGATE_ENCRYPTION_KEY_COORDINATOR_URL;
    }

    /* The default min time period (in millis) between each aggregate main reporting job run. */
    long MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS = 4 * 60 * 60 * 1000; // 4 hours.

    /** Returns min time period (in millis) between each aggregate main reporting job run. */
    default long getMeasurementAggregateMainReportingJobPeriodMs() {
        return MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS;
    }

    /* The default min time period (in millis) between each aggregate fallback reporting job run. */
    long MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS = 24 * 60 * 60 * 1000; // 24 hours.

    /** Returns min time period (in millis) between each aggregate fallback job run. */
    default long getMeasurementAggregateFallbackReportingJobPeriodMs() {
        return MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS;
    }

    /**
     * Returns the maximum time in milliseconds allowed for a network call to open its initial
     * connection during Measurement API calls.
     */
    default int getMeasurementNetworkConnectTimeoutMs() {
        return MEASUREMENT_NETWORK_CONNECT_TIMEOUT_MS;
    }

    /**
     * Returns the maximum time in milliseconds allowed for a network call to read a response from a
     * target server during Measurement API calls.
     */
    default int getMeasurementNetworkReadTimeoutMs() {
        return MEASUREMENT_NETWORK_READ_TIMEOUT_MS;
    }

    long MEASUREMENT_DB_SIZE_LIMIT = (1024 * 1024) * 10; // 10 MBs
    int MEASUREMENT_NETWORK_CONNECT_TIMEOUT_MS = (int) TimeUnit.SECONDS.toMillis(5);
    int MEASUREMENT_NETWORK_READ_TIMEOUT_MS = (int) TimeUnit.SECONDS.toMillis(30);

    /**
     * Returns the window that an InputEvent has to be within for the system to register it as a
     * click.
     */
    long MEASUREMENT_REGISTRATION_INPUT_EVENT_VALID_WINDOW_MS = 60 * 1000; // 1 minute.

    default long getMeasurementRegistrationInputEventValidWindowMs() {
        return MEASUREMENT_REGISTRATION_INPUT_EVENT_VALID_WINDOW_MS;
    }

    /** Returns whether a click event should be verified before a registration request. */
    boolean MEASUREMENT_IS_CLICK_VERIFICATION_ENABLED = true;

    default boolean getMeasurementIsClickVerificationEnabled() {
        return MEASUREMENT_IS_CLICK_VERIFICATION_ENABLED;
    }

    /** Returns whether a click is verified by Input Event. */
    boolean MEASUREMENT_IS_CLICK_VERIFIED_BY_INPUT_EVENT = false;

    default boolean getMeasurementIsClickVerifiedByInputEvent() {
        return MEASUREMENT_IS_CLICK_VERIFIED_BY_INPUT_EVENT;
    }

    /** Returns the DB size limit for measurement. */
    default long getMeasurementDbSizeLimit() {
        return MEASUREMENT_DB_SIZE_LIMIT;
    }

    /** Measurement manifest file url, used for MDD download. */
    String MEASUREMENT_MANIFEST_FILE_URL =
            "https://dl.google.com/mdi-serving/adservices/adtech_enrollment/manifest_configs/1"
                    + "/manifest_config_1658790241927.binaryproto";

    /** Measurement manifest file url. */
    default String getMeasurementManifestFileUrl() {
        return MEASUREMENT_MANIFEST_FILE_URL;
    }

    boolean MEASUREMENT_ENABLE_XNA = false;

    /** Returns whether XNA should be used for eligible sources. */
    default boolean getMeasurementEnableXNA() {
        return MEASUREMENT_ENABLE_XNA;
    }

    long MEASUREMENT_DATA_EXPIRY_WINDOW_MS = TimeUnit.DAYS.toMillis(37);

    /** Returns the data expiry window in milliseconds. */
    default long getMeasurementDataExpiryWindowMs() {
        return MEASUREMENT_DATA_EXPIRY_WINDOW_MS;
    }

    long FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT = 4000L;
    long FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT = 1000L;
    long FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT = 1000L;
    long FLEDGE_CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN_MS = 60L * 24L * 60L * 60L * 1000L; // 60 days
    long FLEDGE_CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN_MS =
            60L * 24L * 60L * 60L * 1000L; // 60 days
    long FLEDGE_CUSTOM_AUDIENCE_MAX_EXPIRE_IN_MS = 60L * 24L * 60L * 60L * 1000L; // 60 days
    int FLEDGE_CUSTOM_AUDIENCE_MAX_NAME_SIZE_B = 200;
    int FLEDGE_CUSTOM_AUDIENCE_MAX_DAILY_UPDATE_URI_SIZE_B = 400;
    int FLEDGE_CUSTOM_AUDIENCE_MAX_BIDDING_LOGIC_URI_SIZE_B = 400;
    int FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B = 10 * 1024; // 10 KiB
    int FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B = 10 * 1024; // 10 KiB
    int FLEDGE_CUSTOM_AUDIENCE_MAX_ADS_SIZE_B = 10 * 1024; // 10 KiB
    int FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS = 100;
    // Keeping TTL as long as expiry, could be reduced later as we get more fresh CAs with adoption
    long FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS = 60 * 24 * 60L * 60L * 1000; // 60 days

    /** Returns the maximum number of custom audience can stay in the storage. */
    default long getFledgeCustomAudienceMaxCount() {
        return FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT;
    }

    /** Returns the maximum number of custom audience an app can create. */
    default long getFledgeCustomAudiencePerAppMaxCount() {
        return FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT;
    }

    /** Returns the maximum number of apps can have access to custom audience. */
    default long getFledgeCustomAudienceMaxOwnerCount() {
        return FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT;
    }

    /**
     * Returns the default amount of time in milliseconds a custom audience object will live before
     * being expiring and being removed
     */
    default long getFledgeCustomAudienceDefaultExpireInMs() {
        return FLEDGE_CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN_MS;
    }

    /**
     * Returns the maximum permitted difference in milliseconds between the custom audience object's
     * creation time and its activation time
     */
    default long getFledgeCustomAudienceMaxActivationDelayInMs() {
        return FLEDGE_CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN_MS;
    }

    /**
     * Returns the maximum permitted difference in milliseconds between the custom audience object's
     * activation time and its expiration time
     */
    default long getFledgeCustomAudienceMaxExpireInMs() {
        return FLEDGE_CUSTOM_AUDIENCE_MAX_EXPIRE_IN_MS;
    }

    /** Returns the maximum size in bytes allowed for name in each FLEDGE custom audience. */
    default int getFledgeCustomAudienceMaxNameSizeB() {
        return FLEDGE_CUSTOM_AUDIENCE_MAX_NAME_SIZE_B;
    }

    /**
     * Returns the maximum size in bytes allowed for daily update uri in each FLEDGE custom
     * audience.
     */
    default int getFledgeCustomAudienceMaxDailyUpdateUriSizeB() {
        return FLEDGE_CUSTOM_AUDIENCE_MAX_DAILY_UPDATE_URI_SIZE_B;
    }

    /**
     * Returns the maximum size in bytes allowed for bidding logic uri in each FLEDGE custom
     * audience.
     */
    default int getFledgeCustomAudienceMaxBiddingLogicUriSizeB() {
        return FLEDGE_CUSTOM_AUDIENCE_MAX_BIDDING_LOGIC_URI_SIZE_B;
    }

    /**
     * Returns the maximum size in bytes allowed for user bidding signals in each FLEDGE custom
     * audience.
     */
    default int getFledgeCustomAudienceMaxUserBiddingSignalsSizeB() {
        return FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B;
    }

    /**
     * Returns the maximum size in bytes allowed for trusted bidding data in each FLEDGE custom
     * audience.
     */
    default int getFledgeCustomAudienceMaxTrustedBiddingDataSizeB() {
        return FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B;
    }

    /** Returns the maximum size in bytes allowed for ads in each FLEDGE custom audience. */
    default int getFledgeCustomAudienceMaxAdsSizeB() {
        return FLEDGE_CUSTOM_AUDIENCE_MAX_ADS_SIZE_B;
    }

    /** Returns the maximum allowed number of ads per FLEDGE custom audience. */
    default int getFledgeCustomAudienceMaxNumAds() {
        return FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS;
    }

    /**
     * Returns the time window that defines how long after a successful update a custom audience can
     * participate in ad selection.
     */
    default long getFledgeCustomAudienceActiveTimeWindowInMs() {
        return FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS;
    }

    boolean FLEDGE_BACKGROUND_FETCH_ENABLED = true;
    long FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS = 4L * 60L * 60L * 1000L; // 4 hours
    long FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS = 30L * 60L * 1000L; // 30 minutes
    long FLEDGE_BACKGROUND_FETCH_JOB_MAX_RUNTIME_MS = 10L * 60L * 1000L; // 5 minutes
    long FLEDGE_BACKGROUND_FETCH_MAX_NUM_UPDATED = 1000;
    int FLEDGE_BACKGROUND_FETCH_THREAD_POOL_SIZE = 8;
    long FLEDGE_BACKGROUND_FETCH_ELIGIBLE_UPDATE_BASE_INTERVAL_S = 24L * 60L * 60L; // 24 hours
    int FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS = 5 * 1000; // 5 seconds
    int FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS = 30 * 1000; // 30 seconds
    int FLEDGE_BACKGROUND_FETCH_MAX_RESPONSE_SIZE_B = 10 * 1024; // 10 KiB
    boolean FLEDGE_HTTP_CACHE_ENABLE = true;
    boolean FLEDGE_HTTP_CACHE_ENABLE_JS_CACHING = true;
    long FLEDGE_HTTP_CACHE_DEFAULT_MAX_AGE_SECONDS = 2 * 24 * 60 * 60; // 2 days
    long FLEDGE_HTTP_CACHE_MAX_ENTRIES = 100;

    /** Returns {@code true} if the FLEDGE Background Fetch is enabled. */
    default boolean getFledgeBackgroundFetchEnabled() {
        return FLEDGE_BACKGROUND_FETCH_ENABLED;
    }

    /**
     * Returns the best effort max time (in milliseconds) between each FLEDGE Background Fetch job
     * run.
     */
    default long getFledgeBackgroundFetchJobPeriodMs() {
        return FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS;
    }

    /**
     * Returns the amount of flex (in milliseconds) around the end of each period to run each FLEDGE
     * Background Fetch job.
     */
    default long getFledgeBackgroundFetchJobFlexMs() {
        return FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS;
    }

    /**
     * Returns the maximum amount of time (in milliseconds) each FLEDGE Background Fetch job is
     * allowed to run.
     */
    default long getFledgeBackgroundFetchJobMaxRuntimeMs() {
        return FLEDGE_BACKGROUND_FETCH_JOB_MAX_RUNTIME_MS;
    }

    /**
     * Returns the maximum number of custom audiences updated in a single FLEDGE background fetch
     * job.
     */
    default long getFledgeBackgroundFetchMaxNumUpdated() {
        return FLEDGE_BACKGROUND_FETCH_MAX_NUM_UPDATED;
    }

    /**
     * Returns the maximum thread pool size to draw workers from in a single FLEDGE background fetch
     * job.
     */
    default int getFledgeBackgroundFetchThreadPoolSize() {
        return FLEDGE_BACKGROUND_FETCH_THREAD_POOL_SIZE;
    }

    /**
     * Returns the base interval in seconds after a successful FLEDGE background fetch job after
     * which a custom audience is next eligible to be updated.
     */
    default long getFledgeBackgroundFetchEligibleUpdateBaseIntervalS() {
        return FLEDGE_BACKGROUND_FETCH_ELIGIBLE_UPDATE_BASE_INTERVAL_S;
    }

    /**
     * Returns the maximum time in milliseconds allowed for a network call to open its initial
     * connection during the FLEDGE background fetch.
     */
    default int getFledgeBackgroundFetchNetworkConnectTimeoutMs() {
        return FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS;
    }

    /**
     * Returns the maximum time in milliseconds allowed for a network call to read a response from a
     * target server during the FLEDGE background fetch.
     */
    default int getFledgeBackgroundFetchNetworkReadTimeoutMs() {
        return FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS;
    }

    /**
     * Returns the maximum size in bytes of a single custom audience update response during the
     * FLEDGE background fetch.
     */
    default int getFledgeBackgroundFetchMaxResponseSizeB() {
        return FLEDGE_BACKGROUND_FETCH_MAX_RESPONSE_SIZE_B;
    }

    /** Returns boolean, if the caching is enabled for {@link FledgeHttpCache} */
    default boolean getFledgeHttpCachingEnabled() {
        return FLEDGE_HTTP_CACHE_ENABLE;
    }

    /** Returns boolean, if the caching is enabled for JS for bidding and scoring */
    default boolean getFledgeHttpJsCachingEnabled() {
        return FLEDGE_HTTP_CACHE_ENABLE_JS_CACHING;
    }

    /** Returns max number of entries that should be persisted in cache */
    default long getFledgeHttpCacheMaxEntries() {
        return FLEDGE_HTTP_CACHE_MAX_ENTRIES;
    }

    /** Returns the default max age of entries in cache */
    default long getFledgeHttpCacheMaxAgeSeconds() {
        return FLEDGE_HTTP_CACHE_DEFAULT_MAX_AGE_SECONDS;
    }

    int FLEDGE_AD_SELECTION_MAX_CONCURRENT_BIDDING_COUNT = 6;

    /** Returns the number of CA that can be bid in parallel for one Ad Selection */
    default int getAdSelectionMaxConcurrentBiddingCount() {
        return FLEDGE_AD_SELECTION_MAX_CONCURRENT_BIDDING_COUNT;
    }

    // TODO(b/240647148): Limits are increased temporarily, re-evaluate these numbers after
    //  getting real world data from telemetry & set accurately scoped timeout
    long FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS = 5000;
    long FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_BUYER_MS = 10000;
    long FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS = 5000;
    long FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS = 5000;
    // For *on device* ad selection.
    long FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS = 10000;
    long FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS = 20_000;
    long FLEDGE_AD_SELECTION_OFF_DEVICE_OVERALL_TIMEOUT_MS = 10_000;

    long FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS = 2000;

    // TODO(b/263055427): Replace with a more realistic number after getting input from ad-techs
    long FLEDGE_REPORT_IMPRESSION_MAX_EVENT_URI_ENTRIES_COUNT = 100;

    /** Returns the timeout constant in milliseconds that limits the bidding per CA */
    default long getAdSelectionBiddingTimeoutPerCaMs() {
        return FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS;
    }

    /** Returns the timeout constant in milliseconds that limits the bidding per Buyer */
    default long getAdSelectionBiddingTimeoutPerBuyerMs() {
        return FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_BUYER_MS;
    }

    /** Returns the timeout constant in milliseconds that limits the scoring */
    default long getAdSelectionScoringTimeoutMs() {
        return FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS;
    }

    /**
     * Returns the timeout constant in milliseconds that limits the {@link
     * AdOutcomeSelectorImpl#runAdOutcomeSelector}
     */
    default long getAdSelectionSelectingOutcomeTimeoutMs() {
        return FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS;
    }

    /**
     * Returns the timeout constant in milliseconds that limits the overall *on device* ad selection
     * orchestration.
     */
    default long getAdSelectionOverallTimeoutMs() {
        return FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS;
    }

    /**
     * Returns the timeout constant in milliseconds that limits the overall *on device* ad selection
     * from outcomes orchestration.
     */
    default long getAdSelectionFromOutcomesOverallTimeoutMs() {
        return FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS;
    }

    /**
     * Returns the timeout constant in milliseconds that limits the overall off device ad selection
     * orchestration.
     */
    default long getAdSelectionOffDeviceOverallTimeoutMs() {
        return FLEDGE_AD_SELECTION_OFF_DEVICE_OVERALL_TIMEOUT_MS;
    }

    /**
     * Returns the time out constant in milliseconds that limits the overall impression reporting
     * execution
     */
    default long getReportImpressionOverallTimeoutMs() {
        return FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS;
    }

    /**
     * Returns the maximum number of {@link DBRegisteredAdInteraction} that an ad-tech can register
     * in one call to {@code reportImpression}.
     */
    default long getReportImpressionMaxEventUriEntriesCount() {
        return FLEDGE_REPORT_IMPRESSION_MAX_EVENT_URI_ENTRIES_COUNT;
    }

    // 24 hours in seconds
    long FLEDGE_AD_SELECTION_EXPIRATION_WINDOW_S = 60 * 60 * 24;

    /**
     * Returns the amount of time in seconds after which ad selection data is considered expired.
     */
    default long getAdSelectionExpirationWindowS() {
        return FLEDGE_AD_SELECTION_EXPIRATION_WINDOW_S;
    }

    boolean FLEDGE_AD_SELECTION_OFF_DEVICE_ENABLED = false;

    /** @return whether to call trusted servers for off device ad selection. */
    default boolean getAdSelectionOffDeviceEnabled() {
        return FLEDGE_AD_SELECTION_OFF_DEVICE_ENABLED;
    }

    boolean FLEDGE_AD_SELECTION_OFF_DEVICE_REQUEST_COMPRESSION_ENABLED = true;

    /** Returns whether to compress requests sent off device for ad selection. */
    default boolean getAdSelectionOffDeviceRequestCompressionEnabled() {
        return FLEDGE_AD_SELECTION_OFF_DEVICE_REQUEST_COMPRESSION_ENABLED;
    }

    boolean ADSERVICES_ENABLED = false;

    default boolean getAdServicesEnabled() {
        return ADSERVICES_ENABLED;
    }

    /** Dump some debug info for the flags */
    default void dump(@NonNull PrintWriter writer, @Nullable String[] args) {}

    /**
     * The number of epoch to look back to do garbage collection for old epoch data. Assume current
     * Epoch is T, then any epoch data of (T-NUMBER_OF_EPOCHS_TO_KEEP_IN_HISTORY-1) (inclusive)
     * should be erased
     */
    int NUMBER_OF_EPOCHS_TO_KEEP_IN_HISTORY = TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS;

    /*
     * Return the number of epochs to keep in the history
     */
    default int getNumberOfEpochsToKeepInHistory() {
        return NUMBER_OF_EPOCHS_TO_KEEP_IN_HISTORY;
    }

    /** Downloader Connection Timeout in Milliseconds. */
    int DOWNLOADER_CONNECTION_TIMEOUT_MS = 10 * 1000; // 10 seconds.

    /*
     * Return the Downloader Connection Timeout in Milliseconds.
     */
    default int getDownloaderConnectionTimeoutMs() {
        return DOWNLOADER_CONNECTION_TIMEOUT_MS;
    }

    /** Downloader Read Timeout in Milliseconds. */
    int DOWNLOADER_READ_TIMEOUT_MS = 10 * 1000; // 10 seconds.

    /** Returns the Downloader Read Timeout in Milliseconds. */
    default int getDownloaderReadTimeoutMs() {
        return DOWNLOADER_READ_TIMEOUT_MS;
    }

    /** Downloader max download threads. */
    int DOWNLOADER_MAX_DOWNLOAD_THREADS = 2;

    /** Returns the Downloader Read Timeout in Milliseconds. */
    default int getDownloaderMaxDownloadThreads() {
        return DOWNLOADER_MAX_DOWNLOAD_THREADS;
    }

    /** MDD Topics API Classifier Manifest Url */
    // TODO(b/236761740): We use this for now for testing. We need to update to the correct one
    // when we actually upload the models.
    String MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL =
            "https://dl.google.com/mdi-serving/adservices/topics_classifier/manifest_configs/2"
                    + "/manifest_config_1661376643699.binaryproto";

    default String getMddTopicsClassifierManifestFileUrl() {
        return MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL;
    }

    long CONSENT_NOTIFICATION_INTERVAL_BEGIN_MS =
            /* hours */ 9 * /* minutes */ 60 * /* seconds */ 60 * /* milliseconds */ 1000; // 9 AM

    default long getConsentNotificationIntervalBeginMs() {
        return CONSENT_NOTIFICATION_INTERVAL_BEGIN_MS;
    }

    long CONSENT_NOTIFICATION_INTERVAL_END_MS =
            /* hours */ 17 * /* minutes */ 60 * /* seconds */ 60 * /* milliseconds */ 1000; // 5 PM

    default long getConsentNotificationIntervalEndMs() {
        return CONSENT_NOTIFICATION_INTERVAL_END_MS;
    }

    long CONSENT_NOTIFICATION_MINIMAL_DELAY_BEFORE_INTERVAL_ENDS =
            /* minutes */ 60 * /* seconds */ 60 * /* milliseconds */ 1000; // 1 hour

    default long getConsentNotificationMinimalDelayBeforeIntervalEnds() {
        return CONSENT_NOTIFICATION_MINIMAL_DELAY_BEFORE_INTERVAL_ENDS;
    }

    boolean CONSENT_NOTIFICATION_DEBUG_MODE = false;

    default boolean getConsentNotificationDebugMode() {
        return CONSENT_NOTIFICATION_DEBUG_MODE;
    }

    boolean CONSENT_MANAGER_DEBUG_MODE = false;

    default boolean getConsentManagerDebugMode() {
        return CONSENT_MANAGER_DEBUG_MODE;
    }

    /** Available sources of truth to get consent for PPAPI. */
    @IntDef(
            flag = true,
            value = {
                SYSTEM_SERVER_ONLY,
                PPAPI_ONLY,
                PPAPI_AND_SYSTEM_SERVER,
            })
    @Retention(RetentionPolicy.SOURCE)
    @interface ConsentSourceOfTruth {}

    /** Write and read consent from system server only. */
    int SYSTEM_SERVER_ONLY = 0;
    /** Write and read consent from PPAPI only */
    int PPAPI_ONLY = 1;
    /** Write consent to both PPAPI and system server. Read consent from system server only. */
    int PPAPI_AND_SYSTEM_SERVER = 2;

    /* Consent source of truth intended to be used by default. */
    @ConsentSourceOfTruth int DEFAULT_CONSENT_SOURCE_OF_TRUTH = PPAPI_AND_SYSTEM_SERVER;

    /** Returns the consent source of truth currently used for PPAPI. */
    @ConsentSourceOfTruth
    default int getConsentSourceOfTruth() {
        return DEFAULT_CONSENT_SOURCE_OF_TRUTH;
    }

    /* Blocked topics source of truth intended to be used by default */
    @ConsentSourceOfTruth int DEFAULT_BLOCKED_TOPICS_SOURCE_OF_TRUTH = PPAPI_AND_SYSTEM_SERVER;

    /** Returns the blocked topics source of truth currently used for PPAPI */
    @ConsentSourceOfTruth
    default int getBlockedTopicsSourceOfTruth() {
        return DEFAULT_BLOCKED_TOPICS_SOURCE_OF_TRUTH;
    }

    // Group of All Killswitches

    /**
     * Global PP API Kill Switch. This overrides all other killswitches. The default value is false
     * which means the PP API is enabled. This flag is used for emergency turning off the whole PP
     * API.
     */
    // Starting M-2023-05, global kill switch is enabled in the binary. Prior to this (namely in
    // M-2022-11), the value of this flag in the binary was false.
    boolean GLOBAL_KILL_SWITCH = true;

    default boolean getGlobalKillSwitch() {
        return GLOBAL_KILL_SWITCH;
    }

    // MEASUREMENT Killswitches

    /**
     * Measurement Kill Switch. This overrides all specific measurement kill switch. The default
     * value is false which means that Measurement is enabled. This flag is used for emergency
     * turning off the whole Measurement API.
     */
    boolean MEASUREMENT_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Global Measurement. Measurement will be disabled if either
     * the Global Kill Switch or the Measurement Kill Switch value is true.
     */
    default boolean getMeasurementKillSwitch() {
        return MEASUREMENT_KILL_SWITCH;
    }

    /**
     * Measurement API Delete Registrations Kill Switch. The default value is false which means
     * Delete Registrations API is enabled. This flag is used for emergency turning off the Delete
     * Registrations API.
     */
    boolean MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement API Delete Registrations. The API will be
     * disabled if either the Global Kill Switch, Measurement Kill Switch, or the Measurement API
     * Delete Registration Kill Switch value is true.
     */
    default boolean getMeasurementApiDeleteRegistrationsKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH;
    }

    /**
     * Measurement API Status Kill Switch. The default value is false which means Status API is
     * enabled. This flag is used for emergency turning off the Status API.
     */
    boolean MEASUREMENT_API_STATUS_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement API Status. The API will be disabled if either
     * the Global Kill Switch, Measurement Kill Switch, or the Measurement API Status Kill Switch
     * value is true.
     */
    default boolean getMeasurementApiStatusKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || MEASUREMENT_API_STATUS_KILL_SWITCH;
    }

    /**
     * Measurement API Register Source Kill Switch. The default value is false which means Register
     * Source API is enabled. This flag is used for emergency turning off the Register Source API.
     */
    boolean MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement API Register Source. The API will be disabled
     * if either the Global Kill Switch, Measurement Kill Switch, or the Measurement API Register
     * Source Kill Switch value is true.
     */
    default boolean getMeasurementApiRegisterSourceKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH;
    }

    /**
     * Measurement API Register Trigger Kill Switch. The default value is false which means Register
     * Trigger API is enabled. This flag is used for emergency turning off the Register Trigger API.
     */
    boolean MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement API Register Trigger. The API will be disabled
     * if either the Global Kill Switch, Measurement Kill Switch, or the Measurement API Register
     * Trigger Kill Switch value is true.
     */
    default boolean getMeasurementApiRegisterTriggerKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH;
    }

    /**
     * Measurement API Register Web Source Kill Switch. The default value is false which means
     * Register Web Source API is enabled. This flag is used for emergency turning off the Register
     * Web Source API.
     */
    boolean MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement API Register Web Source. The API will be
     * disabled if either the Global Kill Switch, Measurement Kill Switch, or the Measurement API
     * Register Web Source Kill Switch value is true.
     */
    default boolean getMeasurementApiRegisterWebSourceKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH;
    }

    /**
     * Measurement API Register Web Trigger Kill Switch. The default value is false which means
     * Register Web Trigger API is enabled. This flag is used for emergency turning off the Register
     * Web Trigger API.
     */
    boolean MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement API Register Web Trigger. The API will be
     * disabled if either the Global Kill Switch, Measurement Kill Switch, or the Measurement API
     * Register Web Trigger Kill Switch value is true.
     */
    default boolean getMeasurementApiRegisterWebTriggerKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH;
    }

    /**
     * Measurement Job Aggregate Fallback Reporting Kill Switch. The default value is false which
     * means Aggregate Fallback Reporting Job is enabled. This flag is used for emergency turning
     * off the Aggregate Fallback Reporting Job.
     */
    boolean MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement Job Aggregate Fallback Reporting. The API will
     * be disabled if either the Global Kill Switch, Measurement Kill Switch, or the Measurement Job
     * Aggregate Fallback Reporting Kill Switch value is true.
     */
    default boolean getMeasurementJobAggregateFallbackReportingKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH;
    }

    /**
     * Measurement Job Aggregate Reporting Kill Switch. The default value is false which means
     * Aggregate Reporting Job is enabled. This flag is used for emergency turning off the Aggregate
     * Reporting Job.
     */
    boolean MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement Job Aggregate Reporting. The API will be
     * disabled if either the Global Kill Switch, Measurement Kill Switch, or the Measurement Job
     * Aggregate Reporting Kill Switch value is true.
     */
    default boolean getMeasurementJobAggregateReportingKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH;
    }

    /**
     * Measurement Job Attribution Kill Switch. The default value is false which means Attribution
     * Job is enabled. This flag is used for emergency turning off the Attribution Job.
     */
    boolean MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement Job Attribution. The API will be disabled if
     * either the Global Kill Switch, Measurement Kill Switch, or the Measurement Job Attribution
     * Kill Switch value is true.
     */
    default boolean getMeasurementJobAttributionKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH;
    }

    /**
     * Measurement Job Delete Expired Kill Switch. The default value is false which means Delete
     * Expired Job is enabled. This flag is used for emergency turning off the Delete Expired Job.
     */
    boolean MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement Job Delete Expired. The API will be disabled if
     * either the Global Kill Switch, Measurement Kill Switch, or the Measurement Job Delete Expired
     * Kill Switch value is true.
     */
    default boolean getMeasurementJobDeleteExpiredKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH;
    }

    /**
     * Measurement Job Delete Uninstalled Kill Switch. The default value is false which means Delete
     * Uninstalled Job is enabled. This flag is used for emergency turning off the Delete
     * Uninstalled Job.
     */
    boolean MEASUREMENT_JOB_DELETE_UNINSTALLED_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement Job Delete Uninstalled. The API will be
     * disabled if either the Global Kill Switch, Measurement Kill Switch, or the Measurement Job
     * Delete Uninstalled Kill Switch value is true.
     */
    default boolean getMeasurementJobDeleteUninstalledKillSwitch() {
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || MEASUREMENT_JOB_DELETE_UNINSTALLED_KILL_SWITCH;
    }

    /**
     * Measurement Job Event Fallback Reporting Kill Switch. The default value is false which means
     * Event Fallback Reporting Job is enabled. This flag is used for emergency turning off the
     * Event Fallback Reporting Job.
     */
    boolean MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement Job Event Fallback Reporting. The API will be
     * disabled if either the Global Kill Switch, Measurement Kill Switch, or the Measurement Job
     * Event Fallback Reporting Kill Switch value is true.
     */
    default boolean getMeasurementJobEventFallbackReportingKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH;
    }

    /**
     * Measurement Job Event Reporting Kill Switch. The default value is false which means Event
     * Reporting Job is enabled. This flag is used for emergency turning off the Event Reporting
     * Job.
     */
    boolean MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement Job Event Reporting. The API will be disabled
     * if either the Global Kill Switch, Measurement Kill Switch, or the Measurement Job Event
     * Reporting Kill Switch value is true.
     */
    default boolean getMeasurementJobEventReportingKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH;
    }

    /**
     * Measurement Job Debug Reporting Kill Switch. The default value is false which means Debug
     * Reporting Job is enabled. This flag is used for emergency turning off the Debug Reporting
     * Job.
     */
    boolean MEASUREMENT_JOB_DEBUG_REPORTING_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement Job Debug Reporting. The API will be disabled
     * if either the Global Kill Switch, Measurement Kill Switch, or the Measurement Job Debug
     * Reporting Kill Switch value is true.
     */
    default boolean getMeasurementJobDebugReportingKillSwitch() {
        // We check the Global Kill Switch first. As a result, it overrides all other kill Switches.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || MEASUREMENT_JOB_DEBUG_REPORTING_KILL_SWITCH;
    }

    /**
     * Measurement Broadcast Receiver Install Attribution Kill Switch. The default value is false
     * which means Install Attribution is enabled. This flag is used for emergency turning off
     * Install Attribution Broadcast Receiver.
     */
    boolean MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement Broadcast Receiver Install Attribution. The
     * Broadcast Receiver will be disabled if either the Global Kill Switch, Measurement Kill Switch
     * or the Measurement Kill Switch value is true.
     */
    default boolean getMeasurementReceiverInstallAttributionKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH;
    }

    /**
     * Measurement Broadcast Receiver Delete Packages Kill Switch. The default value is false which
     * means Delete Packages is enabled. This flag is used for emergency turning off Delete Packages
     * Broadcast Receiver.
     */
    boolean MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement Broadcast Receiver Delete Packages. The
     * Broadcast Receiver will be disabled if either the Global Kill Switch, Measurement Kill Switch
     * or the Measurement Kill Switch value is true.
     */
    default boolean getMeasurementReceiverDeletePackagesKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH;
    }

    /**
     * Measurement Rollback Kill Switch. The default value is false which means the rollback
     * handling on measurement service start is enabled. This flag is used for emergency turning off
     * measurement rollback data deletion handling.
     */
    boolean MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement rollback deletion handling. The rollback
     * deletion handling will be disabled if the Global Kill Switch, Measurement Kill Switch or the
     * Measurement rollback deletion Kill Switch value is true.
     */
    default boolean getMeasurementRollbackDeletionKillSwitch() {
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH;
    }

    // ADID Killswitch.
    /**
     * AdId API Kill Switch. The default value is false which means the AdId API is enabled. This
     * flag is used for emergency turning off the AdId API.
     */
    boolean ADID_KILL_SWITCH = false; // By default, the AdId API is enabled.

    /** Gets the state of adId kill switch. */
    default boolean getAdIdKillSwitch() {
        return ADID_KILL_SWITCH;
    }

    // APPSETID Killswitch.
    /**
     * AppSetId API Kill Switch. The default value is false which means the AppSetId API is enabled.
     * This flag is used for emergency turning off the AppSetId API.
     */
    boolean APPSETID_KILL_SWITCH = false; // By default, the AppSetId API is enabled.

    /** Gets the state of the global and appSetId kill switch. */
    default boolean getAppSetIdKillSwitch() {
        return APPSETID_KILL_SWITCH;
    }

    // TOPICS Killswitches

    /**
     * Topics API Kill Switch. The default value is false which means the Topics API is enabled.
     * This flag is used for emergency turning off the Topics API.
     */
    boolean TOPICS_KILL_SWITCH = false; // By default, the Topics API is enabled.

    /** @return value of Topics API kill switch */
    default boolean getTopicsKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch() || TOPICS_KILL_SWITCH;
    }

    // MDD Killswitches

    /**
     * MDD Background Task Kill Switch. The default value is false which means the MDD background
     * task is enabled. This flag is used for emergency turning off the MDD background tasks.
     */
    boolean MDD_BACKGROUND_TASK_KILL_SWITCH = false;

    /** @return value of Mdd Background Task kill switch */
    default boolean getMddBackgroundTaskKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch() || MDD_BACKGROUND_TASK_KILL_SWITCH;
    }

    /**
     * MDD Logger Kill Switch. The default value is false which means the MDD Logger is enabled.
     * This flag is used for emergency turning off the MDD Logger.
     */
    boolean MDD_LOGGER_KILL_SWITCH = false;

    /** @return value of MDD Logger Kill Switch */
    default boolean getMddLoggerKillSwitch() {
        return getGlobalKillSwitch() || MDD_LOGGER_KILL_SWITCH;
    }

    // FLEDGE Kill switches

    /**
     * Fledge Ad Selection API kill switch. The default value is false which means that Select Ads
     * API is enabled by default. This flag should be should as emergency andon cord.
     */
    boolean FLEDGE_SELECT_ADS_KILL_SWITCH = false;

    /** @return value of Fledge Ad Selection API kill switch */
    default boolean getFledgeSelectAdsKillSwitch() {
        // Check for global kill switch first, as it should override all other kill switches
        return getGlobalKillSwitch() || FLEDGE_SELECT_ADS_KILL_SWITCH;
    }

    /**
     * Fledge Join Custom Audience API kill switch. The default value is false which means that Join
     * Custom Audience API is enabled by default. This flag should be should as emergency andon
     * cord.
     */
    boolean FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH = false;

    /** @return value of Fledge Join Custom Audience API kill switch */
    default boolean getFledgeCustomAudienceServiceKillSwitch() {
        // Check for global kill switch first, as it should override all other kill switches
        return getGlobalKillSwitch() || FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH;
    }

    /*
     * The allow-list for PP APIs. This list has the list of app package names that we allow
     * using PP APIs.
     * App Package Name that does not belong to this allow-list will not be able to use PP APIs.
     * If this list has special value "*", then all package names are allowed.
     * There must be not any empty space between comma.
     */
    String PPAPI_APP_ALLOW_LIST =
            "android.platform.test.scenario,"
                    + "android.adservices.crystalball,"
                    + "android.adservices.cts,"
                    + "android.adservices.debuggablects,"
                    + "com.android.adservices.endtoendtest,"
                    + "com.android.adservices.servicecoretest,"
                    + "com.android.adservices.tests.permissions.appoptout,"
                    + "com.android.adservices.tests.permissions.valid,"
                    + "com.android.adservices.tests.adid,"
                    + "com.android.adservices.tests.appsetid,"
                    + "com.android.sdksandboxclient,"
                    + "com.android.tests.sandbox.adid,"
                    + "com.android.tests.sandbox.appsetid,"
                    + "com.android.tests.sandbox.fledge,"
                    + "com.android.tests.sandbox.measurement,"
                    + "com.example.adservices.samples.adid.app,"
                    + "com.example.adservices.samples.appsetid.app,"
                    + "com.example.adservices.samples.fledge.sampleapp,"
                    + "com.example.adservices.samples.fledge.sampleapp1,"
                    + "com.example.adservices.samples.fledge.sampleapp2,"
                    + "com.example.adservices.samples.fledge.sampleapp3,"
                    + "com.example.adservices.samples.fledge.sampleapp4,"
                    + "com.example.measurement.sampleapp,"
                    + "com.example.measurement.sampleapp2,"
                    + "com.android.adservices.tests.cts.endtoendtest.measurement";

    /**
     * Returns bypass List for PPAPI app signature check. Apps with package name on this list will
     * bypass the signature check
     */
    default String getPpapiAppAllowList() {
        return PPAPI_APP_ALLOW_LIST;
    }

    /*
     * The allow-list for PP APIs. This list has the list of app signatures that we allow
     * using PP APIs. App Package signatures that do not belong to this allow-list will not be
     * able to use PP APIs, unless the package name of this app is in the bypass list.
     *
     * If this list has special value "*", then all package signatures are allowed.
     *
     * There must be not any empty space between comma.
     */
    String PPAPI_APP_SIGNATURE_ALLOW_LIST =
            // com.android.adservices.tests.cts.endtoendtest
            "6cecc50e34ae31bfb5678986d6d6d3736c571ded2f2459527793e1f054eb0c9b,"
                    // com.android.tests.sandbox.topics
                    + "a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc,"
                    // Topics Sample Apps
                    // For example, com.example.adservices.samples.topics.sampleapp1
                    + "301aa3cb081134501c45f1422abc66c24224fd5ded5fdc8f17e697176fd866aa,"
                    // com.android.adservices.tests.cts.topics.testapp1
                    // android.platform.test.scenario.adservices.GetTopicsApiCall
                    // Both have [certificate: "platform"] in .bp file
                    + "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8";

    /** Only App signatures belonging to this Allow List can use PP APIs. */
    default String getPpapiAppSignatureAllowList() {
        return PPAPI_APP_SIGNATURE_ALLOW_LIST;
    }

    /**
     * The client app packages that are allowed to invoke web context APIs, i.e. {@link
     * android.adservices.measurement.MeasurementManager#registerWebSource} and {@link
     * android.adservices.measurement.MeasurementManager#deleteRegistrations}. App packages that do
     * not belong to the list will be responded back with an error response.
     */
    String WEB_CONTEXT_CLIENT_ALLOW_LIST = "";

    // Rate Limit Flags.

    /**
     * PP API Rate Limit for each SDK. This is the max allowed QPS for one SDK to one PP API.
     * Negative Value means skipping the rate limiting checking.
     */
    float SDK_REQUEST_PERMITS_PER_SECOND = 1; // allow max 1 request to any PP API per second.

    /**
     * PP API Rate Limit for ad id. This is the max allowed QPS for one API client to one PP API.
     * Negative Value means skipping the rate limiting checking.
     */
    float ADID_REQUEST_PERMITS_PER_SECOND = 5;

    /**
     * PP API Rate Limit for app set id. This is the max allowed QPS for one API client to one PP
     * API. Negative Value means skipping the rate limiting checking.
     */
    float APPSETID_REQUEST_PERMITS_PER_SECOND = 5;

    /**
     * PP API Rate Limit for measurement register source. This is the max allowed QPS for one API
     * client to one PP API. Negative Value means skipping the rate limiting checking.
     */
    float MEASUREMENT_REGISTER_SOURCE_REQUEST_PERMITS_PER_SECOND = 5;

    /**
     * PP API Rate Limit for measurement register web source. This is the max allowed QPS for one
     * API client to one PP API. Negative Value means skipping the rate limiting checking.
     */
    float MEASUREMENT_REGISTER_WEB_SOURCE_REQUEST_PERMITS_PER_SECOND = 5;

    /**
     * PP API Rate Limit for Topics API based on App Package name. This is the max allowed QPS for
     * one API client to one PP API. Negative Value means skipping the rate limiting checking.
     */
    float TOPICS_API_APP_REQUEST_PERMITS_PER_SECOND = 1;

    /**
     * PP API Rate Limit for Topics API based on Sdk Name. This is the max allowed QPS for one API
     * client to one PP API. Negative Value means skipping the rate limiting checking.
     */
    float TOPICS_API_SDK_REQUEST_PERMITS_PER_SECOND = 1;

    /** Returns the Sdk Request Permits Per Second. */
    default float getSdkRequestPermitsPerSecond() {
        return SDK_REQUEST_PERMITS_PER_SECOND;
    }

    /** Returns the Ad id Request Permits Per Second. */
    default float getAdIdRequestPermitsPerSecond() {
        return ADID_REQUEST_PERMITS_PER_SECOND;
    }

    /** Returns the App Set Ad Request Permits Per Second. */
    default float getAppSetIdRequestPermitsPerSecond() {
        return APPSETID_REQUEST_PERMITS_PER_SECOND;
    }

    /** Returns the Topics API Based On App Package Name Request Permits Per Second. */
    default float getTopicsApiAppRequestPermitsPerSecond() {
        return TOPICS_API_APP_REQUEST_PERMITS_PER_SECOND;
    }

    /** Returns the Topics API Based On Sdk Name Request Permits Per Second. */
    default float getTopicsApiSdkRequestPermitsPerSecond() {
        return TOPICS_API_SDK_REQUEST_PERMITS_PER_SECOND;
    }

    /** Returns the Measurement Register Source Request Permits Per Second. */
    default float getMeasurementRegisterSourceRequestPermitsPerSecond() {
        return MEASUREMENT_REGISTER_SOURCE_REQUEST_PERMITS_PER_SECOND;
    }

    /** Returns the Measurement Register Web Source Request Permits Per Second. */
    default float getMeasurementRegisterWebSourceRequestPermitsPerSecond() {
        return MEASUREMENT_REGISTER_WEB_SOURCE_REQUEST_PERMITS_PER_SECOND;
    }

    // Flags for ad tech enrollment enforcement

    boolean DISABLE_TOPICS_ENROLLMENT_CHECK = false;
    boolean DISABLE_FLEDGE_ENROLLMENT_CHECK = false;
    boolean DISABLE_MEASUREMENT_ENROLLMENT_CHECK = false;

    /** @return {@code true} if the Topics API should disable the ad tech enrollment check */
    default boolean isDisableTopicsEnrollmentCheck() {
        return DISABLE_TOPICS_ENROLLMENT_CHECK;
    }

    /** @return {@code true} if the FLEDGE APIs should disable the ad tech enrollment check */
    default boolean getDisableFledgeEnrollmentCheck() {
        return DISABLE_FLEDGE_ENROLLMENT_CHECK;
    }

    /** @return {@code true} if the Measurement APIs should disable the ad tech enrollment check */
    default boolean isDisableMeasurementEnrollmentCheck() {
        return DISABLE_MEASUREMENT_ENROLLMENT_CHECK;
    }

    boolean ENFORCE_FOREGROUND_STATUS_ADID = true;
    boolean ENFORCE_FOREGROUND_STATUS_APPSETID = true;
    boolean ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION = true;
    boolean ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION = true;
    boolean ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDES = true;
    boolean ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE = true;
    boolean ENFORCE_FOREGROUND_STATUS_TOPICS = true;

    /**
     * @return true if FLEDGE runAdSelection API should require that the calling API is running in
     *     foreground.
     */
    default boolean getEnforceForegroundStatusForFledgeRunAdSelection() {
        return ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION;
    }

    /**
     * @return true if FLEDGE reportImpression API should require that the calling API is running in
     *     foreground.
     */
    default boolean getEnforceForegroundStatusForFledgeReportImpression() {
        return ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION;
    }

    /**
     * @return true if FLEDGE override API methods (for Custom Audience and Ad Selection) should
     *     require that the calling API is running in foreground.
     */
    default boolean getEnforceForegroundStatusForFledgeOverrides() {
        return ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDES;
    }

    /**
     * @return true if FLEDGE Custom Audience API methods should require that the calling API is
     *     running in foreground.
     */
    default boolean getEnforceForegroundStatusForFledgeCustomAudience() {
        return ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE;
    }

    boolean MEASUREMENT_ENFORCE_FOREGROUND_STATUS_DELETE_REGISTRATIONS = true;
    boolean MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCE = true;
    boolean MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_TRIGGER = true;
    boolean MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_SOURCE = true;
    boolean MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_TRIGGER = true;
    boolean MEASUREMENT_ENFORCE_FOREGROUND_STATUS_GET_STATUS = true;

    /**
     * @return true if Measurement Delete Registrations API should require that the calling API is
     *     running in foreground.
     */
    default boolean getEnforceForegroundStatusForMeasurementDeleteRegistrations() {
        return MEASUREMENT_ENFORCE_FOREGROUND_STATUS_DELETE_REGISTRATIONS;
    }

    /**
     * @return true if Measurement Register Source API should require that the calling API is
     *     running in foreground.
     */
    default boolean getEnforceForegroundStatusForMeasurementRegisterSource() {
        return MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCE;
    }

    /**
     * @return true if Measurement Register Trigger API should require that the calling API is
     *     running in foreground.
     */
    default boolean getEnforceForegroundStatusForMeasurementRegisterTrigger() {
        return MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_TRIGGER;
    }

    /**
     * @return true if Measurement Register Web Source API should require that the calling API is
     *     running in foreground.
     */
    default boolean getEnforceForegroundStatusForMeasurementRegisterWebSource() {
        return MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_SOURCE;
    }

    /**
     * @return true if Measurement Register Web Trigger API should require that the calling API is
     *     running in foreground.
     */
    default boolean getEnforceForegroundStatusForMeasurementRegisterWebTrigger() {
        return MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_TRIGGER;
    }

    /**
     * @return true if Measurement Get Status API should require that the calling API is running in
     *     foreground.
     */
    default boolean getEnforceForegroundStatusForMeasurementStatus() {
        return MEASUREMENT_ENFORCE_FOREGROUND_STATUS_GET_STATUS;
    }

    /** @return true if Topics API should require that the calling API is running in foreground. */
    default boolean getEnforceForegroundStatusForTopics() {
        return ENFORCE_FOREGROUND_STATUS_TOPICS;
    }

    /** @return true if AdId API should require that the calling API is running in foreground. */
    default boolean getEnforceForegroundStatusForAdId() {
        return ENFORCE_FOREGROUND_STATUS_ADID;
    }

    int FOREGROUND_STATUS_LEVEL = IMPORTANCE_FOREGROUND_SERVICE;

    /**
     * @return true if AppSetId API should require that the calling API is running in foreground.
     */
    default boolean getEnforceForegroundStatusForAppSetId() {
        return ENFORCE_FOREGROUND_STATUS_APPSETID;
    }

    /** @return the importance level to use to check if an application is in foreground. */
    default int getForegroundStatuslLevelForValidation() {
        return FOREGROUND_STATUS_LEVEL;
    }

    default String getWebContextClientAppAllowList() {
        return WEB_CONTEXT_CLIENT_ALLOW_LIST;
    }

    boolean ENFORCE_ISOLATE_MAX_HEAP_SIZE = true;
    long ISOLATE_MAX_HEAP_SIZE_BYTES = 10 * 1024 * 1024L; // 10 MB
    long MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES = 16 * 1024; // 16 kB

    /**
     * @return true if we enforce to check that JavaScriptIsolate supports limiting the max heap
     *     size
     */
    default boolean getEnforceIsolateMaxHeapSize() {
        return ENFORCE_ISOLATE_MAX_HEAP_SIZE;
    }

    /** @return size in bytes we bound the heap memory for JavaScript isolate */
    default long getIsolateMaxHeapSizeBytes() {
        return ISOLATE_MAX_HEAP_SIZE_BYTES;
    }

    /**
     * @return max allowed size in bytes for response based registrations payload of an individual
     *     source/trigger registration.
     */
    default long getMaxResponseBasedRegistrationPayloadSizeBytes() {
        return MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES;
    }

    /** Ui OTA strings group name, used for MDD download. */
    String UI_OTA_STRINGS_GROUP_NAME = "ui-ota-strings";

    /** UI OTA strings group name. */
    default String getUiOtaStringsGroupName() {
        return UI_OTA_STRINGS_GROUP_NAME;
    }

    /** Ui OTA strings manifest file url, used for MDD download. */
    String UI_OTA_STRINGS_MANIFEST_FILE_URL = "";

    /** UI OTA strings manifest file url. */
    default String getUiOtaStringsManifestFileUrl() {
        return UI_OTA_STRINGS_MANIFEST_FILE_URL;
    }

    /** Ui OTA strings feature flag. */
    boolean UI_OTA_STRINGS_FEATURE_ENABLED = false;

    /** Returns if UI OTA strings feature is enabled. */
    default boolean getUiOtaStringsFeatureEnabled() {
        return UI_OTA_STRINGS_FEATURE_ENABLED;
    }

    /** Deadline for downloading UI OTA strings. */
    long UI_OTA_STRINGS_DOWNLOAD_DEADLINE = 86700000; /* 1 day */

    /** Returns the deadline for downloading UI OTA strings. */
    default long getUiOtaStringsDownloadDeadline() {
        return UI_OTA_STRINGS_DOWNLOAD_DEADLINE;
    }

    /** UI Dialogs feature enabled. */
    boolean UI_DIALOGS_FEATURE_ENABLED = false;

    /** Returns if the UI Dialogs feature is enabled. */
    default boolean getUIDialogsFeatureEnabled() {
        return UI_DIALOGS_FEATURE_ENABLED;
    }

    /** The EEA device region feature is off by default. */
    boolean IS_EEA_DEVICE_FEATURE_ENABLED = false;

    /** Returns if the EEA device region feature has been enabled. */
    default boolean isEeaDeviceFeatureEnabled() {
        return IS_EEA_DEVICE_FEATURE_ENABLED;
    }

    /** Default is that the device is in the EEA region. */
    boolean IS_EEA_DEVICE = true;

    /** Returns if device is in the EEA region. */
    default boolean isEeaDevice() {
        return IS_EEA_DEVICE;
    }

    /** Default is that the ui feature type logging is enabled. */
    boolean UI_FEATURE_TYPE_LOGGING_ENABLED = true;

    /** Returns if device is in the EEA region. */
    default boolean isUiFeatureTypeLoggingEnabled() {
        return UI_FEATURE_TYPE_LOGGING_ENABLED;
    }

    String UI_EEA_COUNTRIES =
            "AT," // Austria
                    + "BE," // Belgium
                    + "BG," // Bulgaria
                    + "HR," // Croatia
                    + "CY," // Republic of Cyprus
                    + "CZ," // Czech Republic
                    + "DK," // Denmark
                    + "EE," // Estonia
                    + "FI," // Finland
                    + "FR," // France
                    + "DE," // Germany
                    + "GR," // Greece
                    + "HU," // Hungary
                    + "IE," // Ireland
                    + "IT," // Italy
                    + "LV," // Latvia
                    + "LT," // Lithuania
                    + "LU," // Luxembourg
                    + "MT," // Malta
                    + "NL," // Netherlands
                    + "PL," // Poland
                    + "PT," // Portugal
                    + "RO," // Romania
                    + "SK," // Slovakia
                    + "SI," // Slovenia
                    + "ES," // Spain
                    + "SE," // Sweden
                    + "IS," // Iceland
                    + "LI," // Liechtenstein
                    + "NO," // Norway
                    + "CH," // Switzerland
                    + "GB," // Great Britain
                    + "GI," // Gibraltar
                    + "GP," // Guadeloupe
                    + "GG," // Guernsey
                    + "JE," // Jersey
                    + "VA," // Vatican City
                    + "AX," // Åland Islands
                    + "IC," // Canary Islands
                    + "EA," // Ceuta & Melilla
                    + "GF," // French Guiana
                    + "PF," // French Polynesia
                    + "TF," // French Southern Territories
                    + "MQ," // Martinique
                    + "YT," // Mayotte
                    + "NC," // New Caledonia
                    + "RE," // Réunion
                    + "BL," // St. Barthélemy
                    + "MF," // St. Martin
                    + "PM," // St. Pierre & Miquelon
                    + "SJ," // Svalbard & Jan Mayen
                    + "WF"; // Wallis & Futuna

    /** Returns the list of EEA countries in a String separated by comma */
    default String getUiEeaCountries() {
        return UI_EEA_COUNTRIES;
    }

    /**
     * GA UX enabled. It contains features that have to be enabled at the same time:
     *
     * <ul>
     *   <li>Updated consent landing page
     *   <li>Consent per API (instead of aggregated one)
     *   <li>Separate page to control Measurement API
     * </ul>
     */
    boolean GA_UX_FEATURE_ENABLED = false;

    /** Returns if the GA UX feature is enabled. */
    default boolean getGaUxFeatureEnabled() {
        return GA_UX_FEATURE_ENABLED;
    }

    long ASYNC_REGISTRATION_JOB_QUEUE_INTERVAL_MS = (int) TimeUnit.HOURS.toMillis(1);

    /** Returns the interval in which to run Registration Job Queue Service. */
    default long getAsyncRegistrationJobQueueIntervalMs() {
        return ASYNC_REGISTRATION_JOB_QUEUE_INTERVAL_MS;
    }

    /**
     * Registration Job Queue Kill Switch. The default value is false which means Registration Job
     * Queue is enabled. This flag is used for emergency shutdown of the Registration Job Queue.
     */
    boolean MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Registration Job Queue. The API will be disabled if either
     * the Global Kill Switch, Measurement Kill Switch, or the Registration Job Queue Kill Switch
     * value is true.
     */
    default boolean getAsyncRegistrationJobQueueKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH;
    }

    /**
     * A feature flag to enable the feature of handling topics without any contributors. Note that
     * in an epoch, an app is a contributor to a topic if the app has called Topics API in this
     * epoch and is classified to the topic.
     *
     * <p>Default value is false, which means the feature is disabled by default and needs to be
     * ramped up.
     */
    boolean ENABLE_TOPIC_CONTRIBUTORS_CHECK = false;

    /** @return if to enable topic contributors check. */
    default boolean getEnableTopicContributorsCheck() {
        return ENABLE_TOPIC_CONTRIBUTORS_CHECK;
    }

    /** Whether to enable database schema version 7 */
    boolean ENABLE_TOPIC_MIGRATION = false;

    /** @return if to enable database schema version 7 */
    default boolean getEnableTopicMigration() {
        return ENABLE_TOPIC_MIGRATION;
    }

    /** Returns true if the given enrollmentId is blocked from using PP-API. */
    default boolean isEnrollmentBlocklisted(String enrollmentId) {
        return false;
    }

    /** Returns a list of enrollmentId blocked from using PP-API. */
    default ImmutableList<String> getEnrollmentBlocklist() {
        return ImmutableList.of();
    }

    /** Kill switch to guard backward-compatible logging. */
    boolean COMPAT_LOGGING_KILL_SWITCH = true;

    /** Returns whether backward-compatible logging should be enabled. */
    default boolean getCompatLoggingKillSwitch() {
        return COMPAT_LOGGING_KILL_SWITCH;
    }
}
