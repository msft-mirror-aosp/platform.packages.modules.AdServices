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

import android.annotation.NonNull;
import android.os.SystemProperties;
import android.provider.DeviceConfig;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;

/** Flags Implementation that delegates to DeviceConfig. */
// TODO(b/228037065): Add validation logics for Feature flags read from PH.
public final class PhFlags implements Flags {
    /*
     * Keys for ALL the flags stored in DeviceConfig.
     */
    // Common Keys
    static final String KEY_MAINTENANCE_JOB_PERIOD_MS = "maintenance_job_period_ms";
    static final String KEY_MAINTENANCE_JOB_FLEX_MS = "maintenance_job_flex_ms";

    // Topics keys
    static final String KEY_TOPICS_EPOCH_JOB_PERIOD_MS = "topics_epoch_job_period_ms";
    static final String KEY_TOPICS_EPOCH_JOB_FLEX_MS = "topics_epoch_job_flex_ms";
    static final String KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC =
            "topics_percentage_for_random_topics";
    static final String KEY_TOPICS_NUMBER_OF_TOP_TOPICS = "topics_number_of_top_topics";
    static final String KEY_TOPICS_NUMBER_OF_RANDOM_TOPICS = "topics_number_of_random_topics";
    static final String KEY_TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS = "topics_number_of_lookback_epochs";

    // Measurement keys
    static final String KEY_MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS =
            "measurement_event_main_reporting_job_period_ms";
    static final String KEY_MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS =
            "measurement_event_fallback_reporting_job_period_ms";
    static final String KEY_MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS =
            "measurement_aggregate_main_reporting_job_period_ms";
    static final String KEY_MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS =
            "measurement_aggregate_fallback_reporting_job_period_ms";
    static final String KEY_MEASUREMENT_APP_NAME = "measurement_app_name";

    // FLEDGE Custom Audience keys
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT = "fledge_custom_audience_max_count";
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT =
            "fledge_custom_audience_per_app_max_count";
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT =
            "fledge_custom_audience_max_owner_count";
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN_MS =
            "fledge_custom_audience_default_expire_in_days";
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN_MS =
            "fledge_custom_audience_max_activate_in_days";
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_EXPIRE_IN_MS =
            "fledge_custom_audience_max_expire_in_days";
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B =
            "fledge_custom_audience_max_user_bidding_signals_size_b";
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B =
            "fledge_custom_audience_max_trusted_bidding_data_size_b";
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ADS_SIZE_B =
            "fledge_custom_audience_max_ads_size_b";
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS =
            "fledge_custom_audience_max_num_ads";

    // FLEDGE Background Fetch keys
    static final String KEY_FLEDGE_BACKGROUND_FETCH_ENABLED = "fledge_background_fetch_enabled";
    static final String KEY_FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS =
            "fledge_background_fetch_job_period_ms";
    static final String KEY_FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS =
            "fledge_background_fetch_job_flex_ms";
    static final String KEY_FLEDGE_BACKGROUND_FETCH_JOB_MAX_RUNTIME_MS =
            "fledge_background_fetch_job_max_runtime_ms";
    static final String KEY_FLEDGE_BACKGROUND_FETCH_MAX_NUM_UPDATED =
            "fledge_background_fetch_max_num_updated";
    static final String KEY_FLEDGE_BACKGROUND_FETCH_THREAD_POOL_SIZE =
            "fledge_background_fetch_thread_pool_size";
    static final String KEY_FLEDGE_BACKGROUND_FETCH_ELIGIBLE_UPDATE_BASE_INTERVAL_S =
            "fledge_background_fetch_eligible_update_base_interval_s";
    static final String KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS =
            "fledge_background_fetch_network_connect_timeout_ms";
    static final String KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS =
            "fledge_background_fetch_network_read_timeout_ms";
    static final String KEY_FLEDGE_BACKGROUND_FETCH_MAX_RESPONSE_SIZE_B =
            "fledge_background_fetch_max_response_size_b";

    // FLEDGE Ad Selection keys
    static final String KEY_FLEDGE_AD_SELECTION_CONCURRENT_BIDDING_COUNT =
            "fledge_ad_selection_concurrent_bidding_count";
    static final String KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS =
            "fledge_ad_selection_bidding_timeout_per_ca_ms";
    static final String KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS =
            "fledge_ad_selection_scoring_timeout_ms";
    static final String KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS =
            "fledge_ad_selection_overall_timeout_ms";
    static final String KEY_NUMBER_OF_EPOCHS_TO_KEEP_IN_HISTORY =
            "topics_number_of_epochs_to_keep_in_history";

    // MDD keys.
    static final String KEY_DOWNLOADER_CONNECTION_TIMEOUT_MS = "downloader_connection_timeout_ms";
    static final String KEY_DOWNLOADER_READ_TIMEOUT_MS = "downloader_read_timeout_ms";
    static final String KEY_DOWNLOADER_MAX_DOWNLOAD_THREADS = "downloader_max_download_threads";

    // Killswitch keys
    static final String KEY_GLOBAL_KILL_SWITCH = "global_kill_switch";
    static final String KEY_TOPICS_KILL_SWITCH = "topics_kill_switch";

    // Adservices enable status keys.
    static final String KEY_ADSERVICES_ENABLE_STATUS = "adservice_enable_status";

    // SystemProperty prefix. We can use SystemProperty to override the AdService Configs.
    private static final String SYSTEM_PROPERTY_PREFIX = "debug.adservices.";

    private static final PhFlags sSingleton = new PhFlags();

    /** Returns the singleton instance of the PhFlags. */
    @NonNull
    public static PhFlags getInstance() {
        return sSingleton;
    }

    @Override
    public long getTopicsEpochJobPeriodMs() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return SystemProperties.getLong(
                getSystemPropertyName(KEY_TOPICS_EPOCH_JOB_PERIOD_MS),
                /* defaultValue */ DeviceConfig.getLong(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        /* flagName */ KEY_TOPICS_EPOCH_JOB_PERIOD_MS,
                        /* defaultValue */ TOPICS_EPOCH_JOB_PERIOD_MS));
    }

    @Override
    public long getTopicsEpochJobFlexMs() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return SystemProperties.getLong(
                getSystemPropertyName(KEY_TOPICS_EPOCH_JOB_FLEX_MS),
                /* defaultValue */ DeviceConfig.getLong(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        /* flagName */ KEY_TOPICS_EPOCH_JOB_FLEX_MS,
                        /* defaultValue */ TOPICS_EPOCH_JOB_FLEX_MS));
    }

    @Override
    public int getTopicsPercentageForRandomTopic() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return SystemProperties.getInt(
                getSystemPropertyName(KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC),
                /* defaultValue */ DeviceConfig.getInt(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        /* flagName */ KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC,
                        /* defaultValue */ TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC));
    }

    @Override
    public int getTopicsNumberOfTopTopics() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_TOPICS_NUMBER_OF_TOP_TOPICS,
                /* defaultValue */ TOPICS_NUMBER_OF_TOP_TOPICS);
    }

    @Override
    public int getTopicsNumberOfRandomTopics() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_TOPICS_NUMBER_OF_RANDOM_TOPICS,
                /* defaultValue */ TOPICS_NUMBER_OF_RANDOM_TOPICS);
    }

    @Override
    public int getTopicsNumberOfLookBackEpochs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS,
                /* defaultValue */ TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS);
    }

    @Override
    public long getMaintenanceJobPeriodMs() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig) and then
        // hard-coded value.
        return SystemProperties.getLong(
                getSystemPropertyName(KEY_MAINTENANCE_JOB_PERIOD_MS),
                /* defaultValue */ DeviceConfig.getLong(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        /* flagName */ KEY_MAINTENANCE_JOB_PERIOD_MS,
                        /* defaultValue */ MAINTENANCE_JOB_PERIOD_MS));
    }

    @Override
    public long getMaintenanceJobFlexMs() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig) and then
        // hard-coded value.
        return SystemProperties.getLong(
                getSystemPropertyName(KEY_MAINTENANCE_JOB_FLEX_MS),
                /* defaultValue */ DeviceConfig.getLong(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        /* flagName */ KEY_MAINTENANCE_JOB_FLEX_MS,
                        /* defaultValue */ MAINTENANCE_JOB_FLEX_MS));
    }

    @Override
    public long getMeasurementEventMainReportingJobPeriodMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS,
                /* defaultValue */ MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS);
    }

    @Override
    public long getMeasurementEventFallbackReportingJobPeriodMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS,
                /* defaultValue */ MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS);
    }

    @Override
    public long getMeasurementAggregateMainReportingJobPeriodMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS,
                /* defaultValue */ MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS);
    }

    @Override
    public long getMeasurementAggregateFallbackReportingJobPeriodMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS,
                /* defaultValue */ MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS);
    }

    @Override
    public String getMeasurementAppName() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getString(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MEASUREMENT_APP_NAME,
                /* defaultValue */ MEASUREMENT_APP_NAME);
    }

    @Override
    public long getFledgeCustomAudienceMaxCount() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT);
    }

    @Override
    public long getFledgeCustomAudiencePerAppMaxCount() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT);
    }

    @Override
    public long getFledgeCustomAudienceMaxOwnerCount() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT);
    }

    @Override
    public long getFledgeCustomAudienceDefaultExpireInMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN_MS,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN_MS);
    }

    @Override
    public long getFledgeCustomAudienceMaxActivationDelayInMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN_MS,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN_MS);
    }

    @Override
    public long getFledgeCustomAudienceMaxExpireInMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_EXPIRE_IN_MS,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_EXPIRE_IN_MS);
    }

    @Override
    public int getFledgeCustomAudienceMaxUserBiddingSignalsSizeB() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B);
    }

    @Override
    public int getFledgeCustomAudienceMaxTrustedBiddingDataSizeB() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B);
    }

    @Override
    public int getFledgeCustomAudienceMaxAdsSizeB() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ADS_SIZE_B,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_ADS_SIZE_B);
    }

    @Override
    public int getFledgeCustomAudienceMaxNumAds() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS);
    }

    @Override
    public boolean getFledgeBackgroundFetchEnabled() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_BACKGROUND_FETCH_ENABLED,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_ENABLED);
    }

    @Override
    public long getFledgeBackgroundFetchJobPeriodMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS);
    }

    @Override
    public long getFledgeBackgroundFetchJobFlexMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS);
    }

    @Override
    public long getFledgeBackgroundFetchJobMaxRuntimeMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_BACKGROUND_FETCH_JOB_MAX_RUNTIME_MS,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_JOB_MAX_RUNTIME_MS);
    }

    @Override
    public long getFledgeBackgroundFetchMaxNumUpdated() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_BACKGROUND_FETCH_MAX_NUM_UPDATED,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_MAX_NUM_UPDATED);
    }

    @Override
    public int getFledgeBackgroundFetchThreadPoolSize() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_BACKGROUND_FETCH_THREAD_POOL_SIZE,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_THREAD_POOL_SIZE);
    }

    @Override
    public long getFledgeBackgroundFetchEligibleUpdateBaseIntervalS() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_BACKGROUND_FETCH_ELIGIBLE_UPDATE_BASE_INTERVAL_S,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_ELIGIBLE_UPDATE_BASE_INTERVAL_S);
    }

    @Override
    public int getFledgeBackgroundFetchNetworkConnectTimeoutMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS);
    }

    @Override
    public int getFledgeBackgroundFetchNetworkReadTimeoutMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS);
    }

    @Override
    public int getFledgeBackgroundFetchMaxResponseSizeB() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_BACKGROUND_FETCH_MAX_RESPONSE_SIZE_B,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_MAX_RESPONSE_SIZE_B);
    }

    @Override
    public int getAdSelectionConcurrentBiddingCount() {
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_AD_SELECTION_CONCURRENT_BIDDING_COUNT,
                /* defaultValue */ FLEDGE_AD_SELECTION_CONCURRENT_BIDDING_COUNT);
    }

    @Override
    public long getAdSelectionBiddingTimeoutPerCaMs() {
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS,
                /* defaultValue */ FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS);
    }

    @Override
    public long getAdSelectionScoringTimeoutMs() {
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS,
                /* defaultValue */ FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS);
    }

    @Override
    public long getAdSelectionOverallTimeoutMs() {
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS,
                /* defaultValue */ FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS);
    }

    @Override
    public int getDownloaderConnectionTimeoutMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_DOWNLOADER_CONNECTION_TIMEOUT_MS,
                /* defaultValue */ DOWNLOADER_CONNECTION_TIMEOUT_MS);
    }

    @Override
    public int getDownloaderReadTimeoutMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_DOWNLOADER_READ_TIMEOUT_MS,
                /* defaultValue */ DOWNLOADER_READ_TIMEOUT_MS);
    }

    @Override
    public int getDownloaderMaxDownloadThreads() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_DOWNLOADER_MAX_DOWNLOAD_THREADS,
                /* defaultValue */ DOWNLOADER_MAX_DOWNLOAD_THREADS);
    }

    // Group of All Killswitches
    @Override
    public boolean getGlobalKillSwitch() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return SystemProperties.getBoolean(
                getSystemPropertyName(KEY_GLOBAL_KILL_SWITCH),
                /* defaultValue */ DeviceConfig.getBoolean(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        /* flagName */ KEY_GLOBAL_KILL_SWITCH,
                        /* defaultValue */ GLOBAL_KILL_SWITCH));
    }

    // TOPICS Killswitches
    @Override
    public boolean getTopicsKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_TOPICS_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_TOPICS_KILL_SWITCH,
                                /* defaultValue */ TOPICS_KILL_SWITCH));
    }

    @Override
    public boolean getAdservicesEnableStatus() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_ADSERVICES_ENABLE_STATUS,
                /* defaultValue */ ADSERVICES_ENABLE_STATUS);
    }

    @Override
    public int getNumberOfEpochsToKeepInHistory() {
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_NUMBER_OF_EPOCHS_TO_KEEP_IN_HISTORY,
                /* defaultValue */ NUMBER_OF_EPOCHS_TO_KEEP_IN_HISTORY);
    }

    @VisibleForTesting
    static String getSystemPropertyName(String key) {
        return SYSTEM_PROPERTY_PREFIX + key;
    }

    @Override
    public void dump(@NonNull PrintWriter writer, @Nullable String[] args) {
        writer.println("==== AdServices PH Flags Dump killswitches ====");
        writer.println("\t" + KEY_GLOBAL_KILL_SWITCH + " = " + getGlobalKillSwitch());
        writer.println("\t" + KEY_TOPICS_KILL_SWITCH + " = " + getTopicsKillSwitch());

        writer.println("==== AdServices PH Flags Dump Topics related flags ====");
        writer.println("\t" + KEY_TOPICS_EPOCH_JOB_PERIOD_MS + " = " + getTopicsEpochJobPeriodMs());
        writer.println("\t" + KEY_TOPICS_EPOCH_JOB_FLEX_MS + " = " + getTopicsEpochJobFlexMs());

        writer.println("==== AdServices PH Flags Dump Measurement related flags: ====");
        writer.println(
                "\t"
                        + KEY_MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS
                        + " = "
                        + getMeasurementEventMainReportingJobPeriodMs());
        writer.println(
                "\t"
                        + KEY_MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS
                        + " = "
                        + getMeasurementEventFallbackReportingJobPeriodMs());
        writer.println(
                "\t"
                        + KEY_MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS
                        + " = "
                        + getMeasurementAggregateMainReportingJobPeriodMs());
        writer.println(
                "\t"
                        + KEY_MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS
                        + " = "
                        + getMeasurementAggregateFallbackReportingJobPeriodMs());
        writer.println("\t" + KEY_MEASUREMENT_APP_NAME + " = " + getMeasurementAppName());

        writer.println("==== AdServices PH Flags Dump FLEDGE related flags: ====");
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT
                        + " = "
                        + getFledgeCustomAudienceMaxCount());
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT
                        + " = "
                        + getFledgeCustomAudienceMaxOwnerCount());
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT
                        + " = "
                        + getFledgeCustomAudiencePerAppMaxCount());
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN_MS
                        + " = "
                        + getFledgeCustomAudienceDefaultExpireInMs());
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN_MS
                        + " = "
                        + getFledgeCustomAudienceMaxActivationDelayInMs());
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_EXPIRE_IN_MS
                        + " = "
                        + getFledgeCustomAudienceMaxExpireInMs());
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B
                        + " = "
                        + getFledgeCustomAudienceMaxUserBiddingSignalsSizeB());
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B
                        + " = "
                        + getFledgeCustomAudienceMaxTrustedBiddingDataSizeB());
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ADS_SIZE_B
                        + " = "
                        + getFledgeCustomAudienceMaxAdsSizeB());
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS
                        + " = "
                        + getFledgeCustomAudienceMaxNumAds());
        writer.println(
                "\t"
                        + KEY_FLEDGE_BACKGROUND_FETCH_ENABLED
                        + " = "
                        + getFledgeBackgroundFetchEnabled());
        writer.println(
                "\t"
                        + KEY_FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS
                        + " = "
                        + getFledgeBackgroundFetchJobPeriodMs());
        writer.println(
                "\t"
                        + KEY_FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS
                        + " = "
                        + getFledgeBackgroundFetchJobFlexMs());
        writer.println(
                "\t"
                        + KEY_FLEDGE_BACKGROUND_FETCH_MAX_NUM_UPDATED
                        + " = "
                        + getFledgeBackgroundFetchMaxNumUpdated());
        writer.println(
                "\t"
                        + KEY_FLEDGE_BACKGROUND_FETCH_THREAD_POOL_SIZE
                        + " = "
                        + getFledgeBackgroundFetchThreadPoolSize());
        writer.println(
                "\t"
                        + KEY_FLEDGE_BACKGROUND_FETCH_ELIGIBLE_UPDATE_BASE_INTERVAL_S
                        + " = "
                        + getFledgeBackgroundFetchEligibleUpdateBaseIntervalS());
        writer.println(
                "\t"
                        + KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS
                        + " = "
                        + getFledgeBackgroundFetchNetworkConnectTimeoutMs());
        writer.println(
                "\t"
                        + KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS
                        + " = "
                        + getFledgeBackgroundFetchNetworkReadTimeoutMs());
        writer.println(
                "\t"
                        + KEY_FLEDGE_BACKGROUND_FETCH_MAX_RESPONSE_SIZE_B
                        + " = "
                        + getFledgeBackgroundFetchMaxResponseSizeB());
        writer.println(
                "\t"
                        + KEY_FLEDGE_AD_SELECTION_CONCURRENT_BIDDING_COUNT
                        + " = "
                        + getAdSelectionConcurrentBiddingCount());
        writer.println(
                "\t"
                        + KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS
                        + " = "
                        + getAdSelectionBiddingTimeoutPerCaMs());
        writer.println(
                "\t"
                        + KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS
                        + " = "
                        + getAdSelectionScoringTimeoutMs());
        writer.println(
                "\t"
                        + KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS
                        + " = "
                        + getAdSelectionScoringTimeoutMs());
        writer.println("==== AdServices PH Flags Dump STATUS ====");
        writer.println("\t" + KEY_ADSERVICES_ENABLE_STATUS + " = " + getAdservicesEnableStatus());
    }
}
