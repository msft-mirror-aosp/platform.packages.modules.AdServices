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

import static com.android.adservices.shared.common.flags.FeatureFlag.Type.LEGACY_KILL_SWITCH;
import static com.android.adservices.shared.common.flags.FeatureFlag.Type.LEGACY_KILL_SWITCH_GLOBAL;
import static com.android.adservices.shared.common.flags.FeatureFlag.Type.LEGACY_KILL_SWITCH_RAMPED_UP;

import android.annotation.IntDef;
import android.app.job.JobInfo;

import androidx.annotation.Nullable;

import com.android.adservices.cobalt.AppNameApiErrorLogger;
import com.android.adservices.cobalt.CobaltConstants;
import com.android.adservices.service.measurement.attribution.AttributionJobService;
import com.android.adservices.shared.common.flags.ConfigFlag;
import com.android.adservices.shared.common.flags.FeatureFlag;
import com.android.adservices.shared.common.flags.ModuleSharedFlags;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableList;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AdServices Feature Flags interface. This Flags interface hold the default values of Ad Services
 * Flags. The default values in this class must match with the default values in PH since we will
 * migrate to Flag Codegen in the future. With that migration, the Flags.java file will be generated
 * from the GCL.
 *
 * <p><b>NOTE: </b>cannot have any dependency on Android or other AdServices code.
 */
public interface Flags extends ModuleSharedFlags {
    /** Topics Epoch Job Period. */
    long TOPICS_EPOCH_JOB_PERIOD_MS = 7 * 86_400_000; // 7 days.

    /** Returns the max time period (in millis) between each epoch computation job run. */
    default long getTopicsEpochJobPeriodMs() {
        return TOPICS_EPOCH_JOB_PERIOD_MS;
    }

    /**
     * Topics Epoch Job Flex. Note the minimum value system allows is 5 minutes
     * or 5% of background job interval time, whichever is greater.
     * Topics epoch job interval time is 7 days, so the minimum flex time should be 8.4 hours.
     */
    @ConfigFlag long TOPICS_EPOCH_JOB_FLEX_MS = 9 * 60 * 60 * 1000; // 9 hours.

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

    /** Global blocked Topics. Default value is empty list. */
    ImmutableList<Integer> TOPICS_GLOBAL_BLOCKED_TOPIC_IDS = ImmutableList.of();

    /** Returns a list of global blocked topics. */
    default ImmutableList<Integer> getGlobalBlockedTopicIds() {
        return TOPICS_GLOBAL_BLOCKED_TOPIC_IDS;
    }

    /** How many epochs to look back when deciding if a caller has observed a topic before. */
    int TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS = 3;

    /** Flag to disable direct app calls for Topics API. go/app-calls-for-topics-api */
    boolean TOPICS_DISABLE_DIRECT_APP_CALLS = false;

    /** Returns the flag to disable direct app calls for Topics API. */
    default boolean getTopicsDisableDirectAppCalls() {
        return TOPICS_DISABLE_DIRECT_APP_CALLS;
    }

    /** Flag to enable encrypted Topics feature for Topics API. */
    boolean TOPICS_ENCRYPTION_ENABLED = false;

    /** Returns the feature flag to enable encryption for Topics API. */
    default boolean getTopicsEncryptionEnabled() {
        return TOPICS_ENCRYPTION_ENABLED;
    }

    /** Flag to enable Topics encryption metrics for Topics API. */
    boolean TOPICS_ENCRYPTION_METRICS_ENABLED = false;

    /** Returns the feature flag to enable Topics encryption metrics for Topics API. */
    default boolean getTopicsEncryptionMetricsEnabled() {
        return TOPICS_ENCRYPTION_METRICS_ENABLED;
    }

    /** Flag to enable Topics epoch job battery constraint logging for Topics API. */
    @FeatureFlag boolean TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_LOGGING_ENABLED = false;

    /**
     * Returns the feature flag to enable Topics epoch job battery constraint logging for Topics
     * API.
     */
    default boolean getTopicsEpochJobBatteryConstraintLoggingEnabled() {
        return TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_LOGGING_ENABLED;
    }

    /** Flag to disable plaintext Topics for Topics API response. */
    boolean TOPICS_DISABLE_PLAINTEXT_RESPONSE = false;

    /** Returns the feature flag to disable plaintext fields Topics API response. */
    default boolean getTopicsDisablePlaintextResponse() {
        return TOPICS_DISABLE_PLAINTEXT_RESPONSE;
    }

    /**
     * Flag to override base64 public key used for encryption testing.
     *
     * <p>Note: Default value for this flag should not be changed from empty.
     */
    String TOPICS_TEST_ENCRYPTION_PUBLIC_KEY = "";

    /** Returns test public key used for encrypting topics for testing. */
    default String getTopicsTestEncryptionPublicKey() {
        return TOPICS_TEST_ENCRYPTION_PUBLIC_KEY;
    }

    /**
     * Returns the number of epochs to look back when deciding if a caller has observed a topic
     * before.
     */
    default int getTopicsNumberOfLookBackEpochs() {
        return TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS;
    }

    /** Privacy budget for logging topic ID distributions with randomized response. */
    float TOPICS_PRIVACY_BUDGET_FOR_TOPIC_ID_DISTRIBUTION = 5f;

    /** Returns the privacy budget for logging topic ID distributions with randomized response. */
    default float getTopicsPrivacyBudgetForTopicIdDistribution() {
        return TOPICS_PRIVACY_BUDGET_FOR_TOPIC_ID_DISTRIBUTION;
    }

    /**
     * Flag to enable rescheduling of Topics job scheduler tasks when modifications are made to the
     * configuration of the background job.
     */
    @FeatureFlag boolean TOPICS_JOB_SCHEDULER_RESCHEDULE_ENABLED = false;

    /** Returns the feature flag to enable rescheduling of Topics job scheduler. */
    default boolean getTopicsJobSchedulerRescheduleEnabled() {
        return TOPICS_JOB_SCHEDULER_RESCHEDULE_ENABLED;
    }

    /**
     * This flag allows you to execute the job regardless of the device's charging status.
     *
     * <p>By default, the Topics API background job scheduler requires the device to be in charging
     * mode for job execution. The default value for this flag is false, indicating that the job
     * should only be executed when the device is charging. By enabling this flag, the job can be
     * executed when the battery level is not low.
     */
    @FeatureFlag boolean TOPICS_EPOCH_JOB_BATTERY_NOT_LOW_INSTEAD_OF_CHARGING = false;

    /**
     * Returns the feature flag to enable the device to be in batter not low mode for Topics API job
     * execution.
     */
    default boolean getTopicsEpochJobBatteryNotLowInsteadOfCharging() {
        return TOPICS_EPOCH_JOB_BATTERY_NOT_LOW_INSTEAD_OF_CHARGING;
    }

    /**
     * Flag to enable cleaning Topics database when the settings of Topics epoch job
     * is changed from server side.
     */
    @FeatureFlag boolean TOPICS_CLEAN_DB_WHEN_EPOCH_JOB_SETTINGS_CHANGED = false;

    /**
     * Returns the feature flag to enable cleaning Topics database when epoch job settings changed.
     */
    default boolean getTopicsCleanDBWhenEpochJobSettingsChanged() {
        return TOPICS_CLEAN_DB_WHEN_EPOCH_JOB_SETTINGS_CHANGED;
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
    float CLASSIFIER_THRESHOLD = 0.2f;

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

    default int getEncryptionKeyNetworkConnectTimeoutMs() {
        return ENCRYPTION_KEY_NETWORK_CONNECT_TIMEOUT_MS;
    }

    int ENCRYPTION_KEY_NETWORK_CONNECT_TIMEOUT_MS = (int) TimeUnit.SECONDS.toMillis(5);

    default int getEncryptionKeyNetworkReadTimeoutMs() {
        return ENCRYPTION_KEY_NETWORK_READ_TIMEOUT_MS;
    }

    int ENCRYPTION_KEY_NETWORK_READ_TIMEOUT_MS = (int) TimeUnit.SECONDS.toMillis(30);

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

    /* The default value for whether the trigger debugging availability signal is enabled for event
    or aggregate reports. */
    @FeatureFlag boolean MEASUREMENT_ENABLE_TRIGGER_DEBUG_SIGNAL = false;

    /**
     * Returns whether the trigger debugging availability signal is enabled for event or aggregate
     * reports.
     */
    default boolean getMeasurementEnableTriggerDebugSignal() {
        return MEASUREMENT_ENABLE_TRIGGER_DEBUG_SIGNAL;
    }

    /* The default value for whether the trigger debugging availability signal is enabled for event
    reports that have coarse_event_report_destinations = true. */
    @FeatureFlag
    boolean MEASUREMENT_ENABLE_EVENT_TRIGGER_DEBUG_SIGNAL_FOR_COARSE_DESTINATION = false;

    /**
     * Returns whether the trigger debugging availability signal is enabled for event reports that
     * have coarse_event_report_destinations = true.
     */
    default boolean getMeasurementEnableEventTriggerDebugSignalForCoarseDestination() {
        return MEASUREMENT_ENABLE_EVENT_TRIGGER_DEBUG_SIGNAL_FOR_COARSE_DESTINATION;
    }

    /* The float to control the probability to set trigger debugging availability signal for fake
    event reports. */
    @ConfigFlag float MEASUREMENT_TRIGGER_DEBUG_SIGNAL_PROBABILITY_FOR_FAKE_REPORTS = 0.5F;

    /**
     * Returns the possibility of trigger debugging availability signal being true for fake event
     * reports.
     */
    default float getMeasurementTriggerDebugSignalProbabilityForFakeReports() {
        return MEASUREMENT_TRIGGER_DEBUG_SIGNAL_PROBABILITY_FOR_FAKE_REPORTS;
    }

    /**
     * The suffix that is appended to the aggregation coordinator origin for retrieving the
     * encryption keys.
     */
    String MEASUREMENT_AGGREGATION_COORDINATOR_PATH =
            ".well-known/aggregation-service/v1/public-keys";

    /** Returns the URL for fetching public encryption keys for aggregatable reports. */
    default String getMeasurementAggregationCoordinatorPath() {
        return MEASUREMENT_AGGREGATION_COORDINATOR_PATH;
    }

    boolean MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_ENABLED = true;

    /** Returns true if aggregation coordinator origin is enabled. */
    default boolean getMeasurementAggregationCoordinatorOriginEnabled() {
        return MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_ENABLED;
    }

    /**
     * Default list(comma-separated) of origins for creating a URL used to fetch public encryption
     * keys for aggregatable reports.
     */
    String MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_LIST =
            "https://publickeyservice.msmt.aws.privacysandboxservices.com,"
                    + "https://publickeyservice.msmt.gcp.privacysandboxservices.com";

    /**
     * Returns a string which is a comma separated list of origins used to fetch public encryption
     * keys for aggregatable reports.
     */
    default String getMeasurementAggregationCoordinatorOriginList() {
        return MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_LIST;
    }

    /* The list of origins for creating a URL used to fetch public encryption keys for
    aggregatable reports. AWS is the current default. */
    String MEASUREMENT_DEFAULT_AGGREGATION_COORDINATOR_ORIGIN =
            "https://publickeyservice.msmt.aws.privacysandboxservices.com";

    /**
     * Returns the default origin for creating the URI used to fetch public encryption keys for
     * aggregatable reports.
     */
    default String getMeasurementDefaultAggregationCoordinatorOrigin() {
        return MEASUREMENT_DEFAULT_AGGREGATION_COORDINATOR_ORIGIN;
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
    int MEASUREMENT_REPORT_RETRY_LIMIT = 3;
    boolean MEASUREMENT_REPORT_RETRY_LIMIT_ENABLED = true;

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

    /** Returns whether measurement click deduplication is enabled. */
    default boolean getMeasurementIsClickDeduplicationEnabled() {
        return MEASUREMENT_IS_CLICK_DEDUPLICATION_ENABLED;
    }

    /** Default whether measurement click deduplication is enabled. */
    boolean MEASUREMENT_IS_CLICK_DEDUPLICATION_ENABLED = false;

    /** Returns whether measurement click deduplication is enforced. */
    default boolean getMeasurementIsClickDeduplicationEnforced() {
        return MEASUREMENT_IS_CLICK_DEDUPLICATION_ENFORCED;
    }

    /** Default whether measurement click deduplication is enforced. */
    boolean MEASUREMENT_IS_CLICK_DEDUPLICATION_ENFORCED = false;

    /** Returns the number of sources that can be registered with a single click. */
    default long getMeasurementMaxSourcesPerClick() {
        return MEASUREMENT_MAX_SOURCES_PER_CLICK;
    }

    /** Default max number of sources that can be registered with single click. */
    long MEASUREMENT_MAX_SOURCES_PER_CLICK = 1;

    /** Returns the DB size limit for measurement. */
    default long getMeasurementDbSizeLimit() {
        return MEASUREMENT_DB_SIZE_LIMIT;
    }

    /** Returns Whether to limit number of Retries for Measurement Reports */
    default boolean getMeasurementReportingRetryLimitEnabled() {
        return MEASUREMENT_REPORT_RETRY_LIMIT_ENABLED;
    }

    /** Returns Maximum number of Retries for Measurement Reportss */
    default int getMeasurementReportingRetryLimit() {
        return MEASUREMENT_REPORT_RETRY_LIMIT;
    }

    /** Measurement manifest file url, used for MDD download. */
    String MEASUREMENT_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-adtech-enrollment/4503"
                    + "/fecd522d3dcfbe1b3b1f1054947be8528be43e97";

    /** Measurement manifest file url. */
    default String getMeasurementManifestFileUrl() {
        return MEASUREMENT_MANIFEST_FILE_URL;
    }

    boolean MEASUREMENT_ENABLE_XNA = false;

    /** Returns whether XNA should be used for eligible sources. */
    default boolean getMeasurementEnableXNA() {
        return MEASUREMENT_ENABLE_XNA;
    }

    boolean MEASUREMENT_ENABLE_SHARED_SOURCE_DEBUG_KEY = true;

    /** Enable/disable shared_debug_key processing from source RBR. */
    default boolean getMeasurementEnableSharedSourceDebugKey() {
        return MEASUREMENT_ENABLE_SHARED_SOURCE_DEBUG_KEY;
    }

    boolean MEASUREMENT_ENABLE_SHARED_FILTER_DATA_KEYS_XNA = true;

    /** Enable/disable shared_filter_data_keys processing from source RBR. */
    default boolean getMeasurementEnableSharedFilterDataKeysXNA() {
        return MEASUREMENT_ENABLE_SHARED_FILTER_DATA_KEYS_XNA;
    }

    boolean MEASUREMENT_ENABLE_DEBUG_REPORT = true;

    /** Returns whether verbose debug report generation is enabled. */
    default boolean getMeasurementEnableDebugReport() {
        return MEASUREMENT_ENABLE_DEBUG_REPORT;
    }

    boolean MEASUREMENT_ENABLE_SOURCE_DEBUG_REPORT = true;

    /** Returns whether source debug report generation is enabled. */
    default boolean getMeasurementEnableSourceDebugReport() {
        return MEASUREMENT_ENABLE_SOURCE_DEBUG_REPORT;
    }

    boolean MEASUREMENT_ENABLE_TRIGGER_DEBUG_REPORT = true;

    /** Returns whether trigger debug report generation is enabled. */
    default boolean getMeasurementEnableTriggerDebugReport() {
        return MEASUREMENT_ENABLE_TRIGGER_DEBUG_REPORT;
    }

    /** Default value for whether header error debug report is enabled. */
    @FeatureFlag boolean MEASUREMENT_ENABLE_HEADER_ERROR_DEBUG_REPORT = false;

    /** Returns whether header error debug report generation is enabled. */
    default boolean getMeasurementEnableHeaderErrorDebugReport() {
        return MEASUREMENT_ENABLE_HEADER_ERROR_DEBUG_REPORT;
    }

    long MEASUREMENT_DATA_EXPIRY_WINDOW_MS = TimeUnit.DAYS.toMillis(37);

    /** Returns the data expiry window in milliseconds. */
    default long getMeasurementDataExpiryWindowMs() {
        return MEASUREMENT_DATA_EXPIRY_WINDOW_MS;
    }

    int MEASUREMENT_MAX_REGISTRATION_REDIRECTS = 20;

    /** Returns the number of maximum registration redirects allowed. */
    default int getMeasurementMaxRegistrationRedirects() {
        return MEASUREMENT_MAX_REGISTRATION_REDIRECTS;
    }

    int MEASUREMENT_MAX_REGISTRATIONS_PER_JOB_INVOCATION = 100;

    /** Returns the number of maximum registration per job invocation. */
    default int getMeasurementMaxRegistrationsPerJobInvocation() {
        return MEASUREMENT_MAX_REGISTRATIONS_PER_JOB_INVOCATION;
    }

    int MEASUREMENT_MAX_RETRIES_PER_REGISTRATION_REQUEST = 5;

    /** Returns the number of maximum retires per registration request. */
    default int getMeasurementMaxRetriesPerRegistrationRequest() {
        return MEASUREMENT_MAX_RETRIES_PER_REGISTRATION_REQUEST;
    }

    long DEFAULT_MEASUREMENT_ASYNC_REGISTRATION_JOB_TRIGGER_MIN_DELAY_MS =
            TimeUnit.MINUTES.toMillis(2);

    /**
     * Returns the minimum delay (in milliseconds) in job triggering after a registration request is
     * received.
     */
    default long getMeasurementAsyncRegistrationJobTriggerMinDelayMs() {
        return DEFAULT_MEASUREMENT_ASYNC_REGISTRATION_JOB_TRIGGER_MIN_DELAY_MS;
    }

    long DEFAULT_MEASUREMENT_ASYNC_REGISTRATION_JOB_TRIGGER_MAX_DELAY_MS =
            TimeUnit.MINUTES.toMillis(5);

    /**
     * Returns the maximum delay (in milliseconds) in job triggering after a registration request is
     * received.
     */
    default long getMeasurementAsyncRegistrationJobTriggerMaxDelayMs() {
        return DEFAULT_MEASUREMENT_ASYNC_REGISTRATION_JOB_TRIGGER_MAX_DELAY_MS;
    }

    long DEFAULT_MEASUREMENT_ATTRIBUTION_JOB_TRIGGERING_DELAY_MS = TimeUnit.MINUTES.toMillis(2);

    /** Delay from trigger registration to attribution job triggering */
    default long getMeasurementAttributionJobTriggerDelayMs() {
        return DEFAULT_MEASUREMENT_ATTRIBUTION_JOB_TRIGGERING_DELAY_MS;
    }

    int DEFAULT_MEASUREMENT_MAX_ATTRIBUTIONS_PER_INVOCATION = 100;

    /** Max number of {@link Trigger} to process per job for {@link AttributionJobService} */
    default int getMeasurementMaxAttributionsPerInvocation() {
        return DEFAULT_MEASUREMENT_MAX_ATTRIBUTIONS_PER_INVOCATION;
    }

    long DEFAULT_MEASUREMENT_MAX_EVENT_REPORT_UPLOAD_RETRY_WINDOW_MS = TimeUnit.DAYS.toMillis(28);

    /** Maximum event report upload retry window. */
    default long getMeasurementMaxEventReportUploadRetryWindowMs() {
        return DEFAULT_MEASUREMENT_MAX_EVENT_REPORT_UPLOAD_RETRY_WINDOW_MS;
    }

    long DEFAULT_MEASUREMENT_MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS =
            TimeUnit.DAYS.toMillis(28);

    /** Maximum aggregate report upload retry window. */
    default long getMeasurementMaxAggregateReportUploadRetryWindowMs() {
        return DEFAULT_MEASUREMENT_MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS;
    }

    long DEFAULT_MEASUREMENT_MAX_DELAYED_SOURCE_REGISTRATION_WINDOW = TimeUnit.MINUTES.toMillis(2);

    /** Maximum window for a delayed source to be considered valid instead of missed. */
    default long getMeasurementMaxDelayedSourceRegistrationWindow() {
        return DEFAULT_MEASUREMENT_MAX_DELAYED_SOURCE_REGISTRATION_WINDOW;
    }

    int DEFAULT_MEASUREMENT_MAX_BYTES_PER_ATTRIBUTION_FILTER_STRING = 25;

    /** Maximum number of bytes allowed in an attribution filter string. */
    default int getMeasurementMaxBytesPerAttributionFilterString() {
        return DEFAULT_MEASUREMENT_MAX_BYTES_PER_ATTRIBUTION_FILTER_STRING;
    }

    int DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET = 20;

    /** Maximum number of filter maps allowed in an attribution filter set. */
    default int getMeasurementMaxFilterMapsPerFilterSet() {
        return DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET;
    }

    int DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER = 50;

    /** Maximum number of values allowed in an attribution filter. */
    default int getMeasurementMaxValuesPerAttributionFilter() {
        return DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER;
    }

    int DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS = 50;

    /** Maximum number of attribution filters allowed for a source. */
    default int getMeasurementMaxAttributionFilters() {
        return DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS;
    }

    int DEFAULT_MEASUREMENT_MAX_BYTES_PER_ATTRIBUTION_AGGREGATE_KEY_ID = 25;

    /** Maximum number of bytes allowed in an aggregate key ID. */
    default int getMeasurementMaxBytesPerAttributionAggregateKeyId() {
        return DEFAULT_MEASUREMENT_MAX_BYTES_PER_ATTRIBUTION_AGGREGATE_KEY_ID;
    }

    int DEFAULT_MEASUREMENT_MAX_AGGREGATE_DEDUPLICATION_KEYS_PER_REGISTRATION = 50;

    /** Maximum number of aggregate deduplication keys allowed during trigger registration. */
    default int getMeasurementMaxAggregateDeduplicationKeysPerRegistration() {
        return DEFAULT_MEASUREMENT_MAX_AGGREGATE_DEDUPLICATION_KEYS_PER_REGISTRATION;
    }

    @FeatureFlag(LEGACY_KILL_SWITCH_RAMPED_UP)
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_KILL_SWITCH = false;

    /** Returns the feature flag for Attribution Fallback Job . */
    default boolean getMeasurementAttributionFallbackJobEnabled() {
        return getMeasurementEnabled() && !MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_KILL_SWITCH;
    }

    long MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_PERIOD_MS = TimeUnit.HOURS.toMillis(1);

    /** Returns the job period in millis for Attribution Fallback Job . */
    default long getMeasurementAttributionFallbackJobPeriodMs() {
        return MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_PERIOD_MS;
    }

    int MEASUREMENT_MAX_EVENT_ATTRIBUTION_PER_RATE_LIMIT_WINDOW = 100;

    /**
     * Returns maximum event attributions per rate limit window. Rate limit unit: (Source Site,
     * Destination Site, Reporting Site, Window).
     */
    default int getMeasurementMaxEventAttributionPerRateLimitWindow() {
        return MEASUREMENT_MAX_EVENT_ATTRIBUTION_PER_RATE_LIMIT_WINDOW;
    }

    int MEASUREMENT_MAX_AGGREGATE_ATTRIBUTION_PER_RATE_LIMIT_WINDOW = 100;

    /**
     * Returns maximum aggregate attributions per rate limit window. Rate limit unit: (Source Site,
     * Destination Site, Reporting Site, Window).
     */
    default int getMeasurementMaxAggregateAttributionPerRateLimitWindow() {
        return MEASUREMENT_MAX_AGGREGATE_ATTRIBUTION_PER_RATE_LIMIT_WINDOW;
    }

    int MEASUREMENT_MAX_DISTINCT_REPORTING_ORIGINS_IN_ATTRIBUTION = 10;

    /**
     * Returns max distinct reporting origins for attribution per { Advertiser X Publisher X
     * TimePeriod }.
     */
    default int getMeasurementMaxDistinctReportingOriginsInAttribution() {
        return MEASUREMENT_MAX_DISTINCT_REPORTING_ORIGINS_IN_ATTRIBUTION;
    }

    int MEASUREMENT_MAX_DISTINCT_DESTINATIONS_IN_ACTIVE_SOURCE = 100;

    /**
     * Returns max distinct advertisers with pending impressions per { Publisher X Enrollment X
     * TimePeriod }.
     */
    default int getMeasurementMaxDistinctDestinationsInActiveSource() {
        return MEASUREMENT_MAX_DISTINCT_DESTINATIONS_IN_ACTIVE_SOURCE;
    }

    int MEASUREMENT_MAX_REPORTING_ORIGINS_PER_SOURCE_REPORTING_SITE_PER_WINDOW = 1;

    /**
     * Returns the maximum number of reporting origins per source site, reporting site,
     * reporting-origin-update-window counted per source registration.
     */
    default int getMeasurementMaxReportingOriginsPerSourceReportingSitePerWindow() {
        return MEASUREMENT_MAX_REPORTING_ORIGINS_PER_SOURCE_REPORTING_SITE_PER_WINDOW;
    }

    int MEASUREMENT_MAX_DISTINCT_REP_ORIG_PER_PUBLISHER_X_DEST_IN_SOURCE = 100;

    /**
     * Max distinct reporting origins with source registration per { Publisher X Advertiser X
     * TimePeriod }.
     */
    default int getMeasurementMaxDistinctRepOrigPerPublXDestInSource() {
        return MEASUREMENT_MAX_DISTINCT_REP_ORIG_PER_PUBLISHER_X_DEST_IN_SOURCE;
    }

    boolean MEASUREMENT_ENABLE_DESTINATION_RATE_LIMIT = true;

    /** Returns {@code true} if Measurement destination rate limit is enabled. */
    default boolean getMeasurementEnableDestinationRateLimit() {
        return MEASUREMENT_ENABLE_DESTINATION_RATE_LIMIT;
    }

    int MEASUREMENT_MAX_DESTINATIONS_PER_PUBLISHER_PER_RATE_LIMIT_WINDOW = 50;

    /**
     * Returns the maximum number of distinct destination sites per source site per rate limit
     * window.
     */
    default int getMeasurementMaxDestinationsPerPublisherPerRateLimitWindow() {
        return MEASUREMENT_MAX_DESTINATIONS_PER_PUBLISHER_PER_RATE_LIMIT_WINDOW;
    }

    int MEASUREMENT_MAX_DEST_PER_PUBLISHER_X_ENROLLMENT_PER_RATE_LIMIT_WINDOW = 200;

    /**
     * Returns the maximum number of distinct destination sites per source site X enrollment per
     * minute rate limit window.
     */
    default int getMeasurementMaxDestPerPublisherXEnrollmentPerRateLimitWindow() {
        return MEASUREMENT_MAX_DEST_PER_PUBLISHER_X_ENROLLMENT_PER_RATE_LIMIT_WINDOW;
    }

    @ConfigFlag long MEASUREMENT_DESTINATION_RATE_LIMIT_WINDOW = TimeUnit.MINUTES.toMillis(1);

    /** Returns the duration that controls the rate-limiting window for destinations per minute. */
    default long getMeasurementDestinationRateLimitWindow() {
        return MEASUREMENT_DESTINATION_RATE_LIMIT_WINDOW;
    }

    /**
     * Returns the maximum number of distinct destination sites per source site X enrollment per day
     * rate limit.
     */
    @ConfigFlag int MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT = 100;

    default int getMeasurementDestinationPerDayRateLimit() {
        return MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT;
    }

    @FeatureFlag boolean MEASUREMENT_ENABLE_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW = false;

    /** Returns true, if rate-limiting window for destinations per day is enabled. */
    default boolean getMeasurementEnableDestinationPerDayRateLimitWindow() {
        return MEASUREMENT_ENABLE_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW;
    }

    @ConfigFlag
    long MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW_IN_MS = TimeUnit.DAYS.toMillis(1);

    /** Returns the duration that controls the per day rate-limiting window for destinations. */
    default long getMeasurementDestinationPerDayRateLimitWindowInMs() {
        return MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW_IN_MS;
    }

    float MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_EVENT = 6.5F;

    /** Returns max information gain in Flexible Event API for Event sources */
    default float getMeasurementFlexApiMaxInformationGainEvent() {
        return MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_EVENT;
    }

    float MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_NAVIGATION = 11.5F;

    /** Returns max information gain in Flexible Event API for Navigation sources */
    default float getMeasurementFlexApiMaxInformationGainNavigation() {
        return MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_NAVIGATION;
    }

    float MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_EVENT = 6.5F;

    /** Returns max information gain for Flexible Event, dual destination Event sources */
    default float getMeasurementFlexApiMaxInformationGainDualDestinationEvent() {
        return MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_EVENT;
    }

    float MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_NAVIGATION = 11.5F;

    /** Returns max information gain for Flexible Event, dual destination Navigation sources */
    default float getMeasurementFlexApiMaxInformationGainDualDestinationNavigation() {
        return MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_NAVIGATION;
    }

    @ConfigFlag float MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_NAVIGATION = 11.55F;

    /** Returns max information gain for navigation sources with attribution scopes. */
    default float getMeasurementAttributionScopeMaxInfoGainNavigation() {
        return MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_NAVIGATION;
    }

    @ConfigFlag
    float MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_NAVIGATION = 11.55F;

    /**
     * Returns max information gain for navigation sources with dual destination and attribution
     * scopes.
     */
    default float getMeasurementAttributionScopeMaxInfoGainDualDestinationNavigation() {
        return MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_NAVIGATION;
    }

    @ConfigFlag float MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_EVENT = 6.5F;

    /** Returns max information gain for event sources with attribution scopes. */
    default float getMeasurementAttributionScopeMaxInfoGainEvent() {
        return MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_EVENT;
    }

    @ConfigFlag float MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_EVENT = 6.5F;

    /**
     * Returns max information gain for event sources with dual destination and attribution scopes.
     */
    default float getMeasurementAttributionScopeMaxInfoGainDualDestinationEvent() {
        return MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_EVENT;
    }

    @FeatureFlag boolean MEASUREMENT_ENABLE_FAKE_REPORT_TRIGGER_TIME = false;

    /** Returns true if fake report trigger time is enabled. */
    default boolean getMeasurementEnableFakeReportTriggerTime() {
        return MEASUREMENT_ENABLE_FAKE_REPORT_TRIGGER_TIME;
    }

    long MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION = (1L << 32) - 1L;

    /** Returns max repot states per source registration */
    default long getMeasurementMaxReportStatesPerSourceRegistration() {
        return MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION;
    }

    int MEASUREMENT_FLEX_API_MAX_EVENT_REPORTS = 20;

    /** Returns max event reports in Flexible Event API */
    default int getMeasurementFlexApiMaxEventReports() {
        return MEASUREMENT_FLEX_API_MAX_EVENT_REPORTS;
    }

    int MEASUREMENT_FLEX_API_MAX_EVENT_REPORT_WINDOWS = 5;

    /** Returns max event report windows in Flexible Event API */
    default int getMeasurementFlexApiMaxEventReportWindows() {
        return MEASUREMENT_FLEX_API_MAX_EVENT_REPORT_WINDOWS;
    }

    int MEASUREMENT_FLEX_API_MAX_TRIGGER_DATA_CARDINALITY = 32;

    /** Returns max trigger data cardinality in Flexible Event API */
    default int getMeasurementFlexApiMaxTriggerDataCardinality() {
        return MEASUREMENT_FLEX_API_MAX_TRIGGER_DATA_CARDINALITY;
    }

    long MEASUREMENT_MINIMUM_EVENT_REPORT_WINDOW_IN_SECONDS = TimeUnit.HOURS.toSeconds(1);

    /** Returns minimum event report window */
    default long getMeasurementMinimumEventReportWindowInSeconds() {
        return MEASUREMENT_MINIMUM_EVENT_REPORT_WINDOW_IN_SECONDS;
    }

    long MEASUREMENT_MINIMUM_AGGREGATABLE_REPORT_WINDOW_IN_SECONDS = TimeUnit.HOURS.toSeconds(1);

    /** Returns minimum aggregatable report window */
    default long getMeasurementMinimumAggregatableReportWindowInSeconds() {
        return MEASUREMENT_MINIMUM_AGGREGATABLE_REPORT_WINDOW_IN_SECONDS;
    }

    boolean MEASUREMENT_ENABLE_LOOKBACK_WINDOW_FILTER = false;

    /** Returns true if lookback window filter is enabled else false. */
    default boolean getMeasurementEnableLookbackWindowFilter() {
        return MEASUREMENT_ENABLE_LOOKBACK_WINDOW_FILTER;
    }

    /** Default FLEDGE app package name logging flag. */
    boolean FLEDGE_APP_PACKAGE_NAME_LOGGING_ENABLED = false;

    /** Returns whether FLEDGE app package name logging is enabled. */
    default boolean getFledgeAppPackageNameLoggingEnabled() {
        return FLEDGE_APP_PACKAGE_NAME_LOGGING_ENABLED;
    }

    long FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT = 4000L;
    long FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT = 1000L;
    long FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT = 1000L;
    @ConfigFlag long FLEDGE_CUSTOM_AUDIENCE_PER_BUYER_MAX_COUNT = 4000L;
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
    long FLEDGE_ENCRYPTION_KEY_MAX_AGE_SECONDS = TimeUnit.DAYS.toSeconds(14);
    long FLEDGE_FETCH_CUSTOM_AUDIENCE_MIN_RETRY_AFTER_VALUE_MS = 30 * 1000; // 30 seconds
    long FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_RETRY_AFTER_VALUE_MS =
            24 * 60 * 60 * 1000; // 24 hours in ms

    @FeatureFlag boolean ENABLE_CUSTOM_AUDIENCE_COMPONENT_ADS = false;
    @ConfigFlag int MAX_COMPONENT_ADS_PER_CUSTOM_AUDIENCE = 40;
    @ConfigFlag int COMPONENT_AD_RENDER_ID_MAX_LENGTH_BYTES = 12;

    /** Returns true if the component ads feature is enabled for custom audiences. */
    default boolean getEnableCustomAudienceComponentAds() {
        return ENABLE_CUSTOM_AUDIENCE_COMPONENT_ADS;
    }

    /** Returns the maximum number of component ads per custom audience. */
    default int getMaxComponentAdsPerCustomAudience() {
        return MAX_COMPONENT_ADS_PER_CUSTOM_AUDIENCE;
    }

    /** Returns the maximum length of component ad render ids in bytes. */
    default int getComponentAdRenderIdMaxLengthBytes() {
        return COMPONENT_AD_RENDER_ID_MAX_LENGTH_BYTES;
    }

    /**
     * Returns the minimum number of milliseconds before the same fetch CA request can be retried.
     */
    default long getFledgeFetchCustomAudienceMinRetryAfterValueMs() {
        return FLEDGE_FETCH_CUSTOM_AUDIENCE_MIN_RETRY_AFTER_VALUE_MS;
    }

    /**
     * Returns the maximum number of milliseconds before the same fetch CA request can be retried.
     */
    default long getFledgeFetchCustomAudienceMaxRetryAfterValueMs() {
        return FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_RETRY_AFTER_VALUE_MS;
    }

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

    /** Returns the maximum number of custom audiences per buyer ad tech. */
    default long getFledgeCustomAudiencePerBuyerMaxCount() {
        return FLEDGE_CUSTOM_AUDIENCE_PER_BUYER_MAX_COUNT;
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

    int FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B = 8 * 1024; // 8 KiB
    int FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_REQUEST_CUSTOM_HEADER_SIZE_B = 8 * 1024; // 8 KiB
    int FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_CUSTOM_AUDIENCE_SIZE_B = 8 * 1024; // 8 KiB

    /**
     * Returns the maximum size in bytes allowed for user bidding signals in each
     * fetchAndJoinCustomAudience request.
     */
    default int getFledgeFetchCustomAudienceMaxUserBiddingSignalsSizeB() {
        return FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B;
    }

    /**
     * Returns the maximum size in bytes allowed for the request custom header derived from each
     * fetchAndJoinCustomAudience request.
     */
    default int getFledgeFetchCustomAudienceMaxRequestCustomHeaderSizeB() {
        return FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_REQUEST_CUSTOM_HEADER_SIZE_B;
    }

    /**
     * Returns the maximum size in bytes for the fused custom audience allowed to be persisted by
     * the fetchAndJoinCustomAudience API.
     */
    default int getFledgeFetchCustomAudienceMaxCustomAudienceSizeB() {
        return FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_CUSTOM_AUDIENCE_SIZE_B;
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
    boolean FLEDGE_ON_DEVICE_AUCTION_SHOULD_USE_UNIFIED_TABLES = false;

    /** Returns {@code true} if the on device auction should use the unified flow tables */
    default boolean getFledgeOnDeviceAuctionShouldUseUnifiedTables() {
        return FLEDGE_ON_DEVICE_AUCTION_SHOULD_USE_UNIFIED_TABLES;
    }

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

    /**
     * Returns boolean, if the caching is enabled for {@link
     * com.android.adservices.service.common.cache.FledgeHttpCache}
     */
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

    boolean PROTECTED_SIGNALS_PERIODIC_ENCODING_ENABLED = true;
    long PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_PERIOD_MS = 1L * 60L * 60L * 1000L; // 1 hour
    long PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_FLEX_MS = 5L * 60L * 1000L; // 5 minutes
    int PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES = (int) (1.5 * 1024); // 1.5 KB
    int PROTECTED_SIGNALS_FETCH_SIGNAL_UPDATES_MAX_SIZE_BYTES = (int) (10 * 1024);
    int PROTECTED_SIGNALS_MAX_JS_FAILURE_EXECUTION_ON_CERTAIN_VERSION_BEFORE_STOP = 3;
    long PROTECTED_SIGNALS_ENCODER_REFRESH_WINDOW_SECONDS = 24L * 60L * 60L; // 1 day
    int PROTECTED_SIGNALS_MAX_SIGNAL_SIZE_PER_BUYER_BYTES = 10 * 1024;
    int PROTECTED_SIGNALS_MAX_SIGNAL_SIZE_PER_BUYER_WITH_OVERSUBSCIPTION_BYTES = 15 * 1024;

    /** Returns {@code true} feature flag if Periodic encoding of Protected Signals is enabled. */
    default boolean getProtectedSignalsPeriodicEncodingEnabled() {
        return PROTECTED_SIGNALS_PERIODIC_ENCODING_ENABLED;
    }

    /** Returns period of running periodic encoding in milliseconds */
    default long getProtectedSignalPeriodicEncodingJobPeriodMs() {
        return PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_PERIOD_MS;
    }

    /** Returns the flexible period of running periodic encoding in milliseconds */
    default long getProtectedSignalsPeriodicEncodingJobFlexMs() {
        return PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_FLEX_MS;
    }

    /** Returns the max size in bytes for encoded payload */
    default int getProtectedSignalsEncodedPayloadMaxSizeBytes() {
        return PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES;
    }

    /** Returns the maximum size of the signal update payload. */
    default int getProtectedSignalsFetchSignalUpdatesMaxSizeBytes() {
        return PROTECTED_SIGNALS_FETCH_SIGNAL_UPDATES_MAX_SIZE_BYTES;
    }

    /** Returns the maximum number of continues JS failure before we stop executing the JS. */
    default int getProtectedSignalsMaxJsFailureExecutionOnCertainVersionBeforeStop() {
        return PROTECTED_SIGNALS_MAX_JS_FAILURE_EXECUTION_ON_CERTAIN_VERSION_BEFORE_STOP;
    }

    /** Returns the maximum time window beyond which encoder logic should be refreshed */
    default long getProtectedSignalsEncoderRefreshWindowSeconds() {
        return PROTECTED_SIGNALS_ENCODER_REFRESH_WINDOW_SECONDS;
    }

    /** Returns the maximum size of signals in storage per buyer. */
    default int getProtectedSignalsMaxSignalSizePerBuyerBytes() {
        return PROTECTED_SIGNALS_MAX_SIGNAL_SIZE_PER_BUYER_BYTES;
    }

    /**
     * Returns the maximum size of signals in the storage per buyer with a graceful oversubscription
     * policy.
     */
    default int getProtectedSignalsMaxSignalSizePerBuyerWithOversubsciptionBytes() {
        return PROTECTED_SIGNALS_MAX_SIGNAL_SIZE_PER_BUYER_WITH_OVERSUBSCIPTION_BYTES;
    }

    @FeatureFlag boolean FLEDGE_ENABLE_FORCED_ENCODING_AFTER_SIGNALS_UPDATE = false;

    @ConfigFlag
    long FLEDGE_FORCED_ENCODING_AFTER_SIGNALS_UPDATE_COOLDOWN_SECONDS = 4L * 60L * 60L; // 4 hours

    /**
     * Returns {@code true} if forced encoding directly after a call to updateSignals() is enabled.
     */
    default boolean getFledgeEnableForcedEncodingAfterSignalsUpdate() {
        return FLEDGE_ENABLE_FORCED_ENCODING_AFTER_SIGNALS_UPDATE;
    }

    /**
     * Returns the cooldown period in seconds after any signals encoding during which forced
     * encoding directly after a call to updateSignals() will not occur.
     */
    default long getFledgeForcedEncodingAfterSignalsUpdateCooldownSeconds() {
        return FLEDGE_FORCED_ENCODING_AFTER_SIGNALS_UPDATE_COOLDOWN_SECONDS;
    }

    int FLEDGE_AD_COUNTER_HISTOGRAM_ABSOLUTE_MAX_TOTAL_EVENT_COUNT = 10_000;
    int FLEDGE_AD_COUNTER_HISTOGRAM_LOWER_MAX_TOTAL_EVENT_COUNT = 9_500;
    int FLEDGE_AD_COUNTER_HISTOGRAM_ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT = 1_000;
    int FLEDGE_AD_COUNTER_HISTOGRAM_LOWER_MAX_PER_BUYER_EVENT_COUNT = 900;

    /** Returns the maximum allowed number of events in the entire frequency cap histogram table. */
    default int getFledgeAdCounterHistogramAbsoluteMaxTotalEventCount() {
        return FLEDGE_AD_COUNTER_HISTOGRAM_ABSOLUTE_MAX_TOTAL_EVENT_COUNT;
    }

    /**
     * Returns the number of events that the entire frequency cap histogram table should be trimmed
     * to, if there are too many entries.
     */
    default int getFledgeAdCounterHistogramLowerMaxTotalEventCount() {
        return FLEDGE_AD_COUNTER_HISTOGRAM_LOWER_MAX_TOTAL_EVENT_COUNT;
    }

    /**
     * Returns the maximum allowed number of events per buyer in the frequency cap histogram table.
     */
    default int getFledgeAdCounterHistogramAbsoluteMaxPerBuyerEventCount() {
        return FLEDGE_AD_COUNTER_HISTOGRAM_ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT;
    }

    /**
     * Returns the number of events for a single buyer that the frequency cap histogram table should
     * be trimmed to, if there are too many entries for that buyer.
     */
    default int getFledgeAdCounterHistogramLowerMaxPerBuyerEventCount() {
        return FLEDGE_AD_COUNTER_HISTOGRAM_LOWER_MAX_PER_BUYER_EVENT_COUNT;
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
    long FLEDGE_AD_SELECTION_BIDDING_LOGIC_JS_VERSION = 2L;

    long FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS = 2000;

    // RegisterAdBeacon  Constants
    long FLEDGE_REPORT_IMPRESSION_MAX_REGISTERED_AD_BEACONS_TOTAL_COUNT = 1000; // Num entries
    long FLEDGE_REPORT_IMPRESSION_MAX_REGISTERED_AD_BEACONS_PER_AD_TECH_COUNT = 10; // Num entries
    long FLEDGE_REPORT_IMPRESSION_REGISTERED_AD_BEACONS_MAX_INTERACTION_KEY_SIZE_B =
            20 * 2; // Num characters * 2 bytes per char in UTF-8
    long FLEDGE_REPORT_IMPRESSION_MAX_INTERACTION_REPORTING_URI_SIZE_B = 400;

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
     * com.android.adservices.service.adselection.AdOutcomeSelectorImpl#runAdOutcomeSelector}
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

    /** Returns the default JS version for running bidding. */
    default long getFledgeAdSelectionBiddingLogicJsVersion() {
        return FLEDGE_AD_SELECTION_BIDDING_LOGIC_JS_VERSION;
    }

    /**
     * Returns the timeout constant in milliseconds that limits the overall impression reporting
     * execution
     */
    default long getReportImpressionOverallTimeoutMs() {
        return FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS;
    }

    /**
     * Returns the maximum number of {@link
     * com.android.adservices.data.adselection.DBRegisteredAdInteraction} that can be in the {@code
     * registered_ad_interactions} database at any one time.
     */
    default long getFledgeReportImpressionMaxRegisteredAdBeaconsTotalCount() {
        return FLEDGE_REPORT_IMPRESSION_MAX_REGISTERED_AD_BEACONS_TOTAL_COUNT;
    }

    /**
     * Returns the maximum number of {@link
     * com.android.adservices.data.adselection.DBRegisteredAdInteraction} that an ad-tech can
     * register in one call to {@code reportImpression}.
     */
    default long getFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount() {
        return FLEDGE_REPORT_IMPRESSION_MAX_REGISTERED_AD_BEACONS_PER_AD_TECH_COUNT;
    }

    /**
     * Returns the maximum size in bytes of {@link
     * com.android.adservices.data.adselection.DBRegisteredAdInteraction#getInteractionKey()}
     */
    default long getFledgeReportImpressionRegisteredAdBeaconsMaxInteractionKeySizeB() {
        return FLEDGE_REPORT_IMPRESSION_REGISTERED_AD_BEACONS_MAX_INTERACTION_KEY_SIZE_B;
    }

    /**
     * Returns the maximum size in bytes of {@link
     * com.android.adservices.data.adselection.DBRegisteredAdInteraction#getInteractionReportingUri()}
     */
    default long getFledgeReportImpressionMaxInteractionReportingUriSizeB() {
        return FLEDGE_REPORT_IMPRESSION_MAX_INTERACTION_REPORTING_URI_SIZE_B;
    }

    // 24 hours in seconds
    long FLEDGE_AD_SELECTION_EXPIRATION_WINDOW_S = 60 * 60 * 24;

    /**
     * Returns the amount of time in seconds after which ad selection data is considered expired.
     */
    default long getAdSelectionExpirationWindowS() {
        return FLEDGE_AD_SELECTION_EXPIRATION_WINDOW_S;
    }

    // Filtering feature flag disabled by default
    boolean FLEDGE_APP_INSTALL_FILTERING_ENABLED = false;

    /** Returns {@code true} if app install filtering of ads during ad selection is enabled. */
    default boolean getFledgeAppInstallFilteringEnabled() {
        return FLEDGE_APP_INSTALL_FILTERING_ENABLED;
    }

    // Filtering feature flag disabled by default
    boolean FLEDGE_FREQUENCY_CAP_FILTERING_ENABLED = false;

    /** Returns {@code true} if frequency cap filtering of ads during ad selection is enabled. */
    default boolean getFledgeFrequencyCapFilteringEnabled() {
        return FLEDGE_FREQUENCY_CAP_FILTERING_ENABLED;
    }

    boolean FLEDGE_AD_SELECTION_CONTEXTUAL_ADS_ENABLED = false;

    /** Returns {@code true} if negative filtering of ads during ad selection is enabled. */
    default boolean getFledgeAdSelectionContextualAdsEnabled() {
        return FLEDGE_AD_SELECTION_CONTEXTUAL_ADS_ENABLED;
    }

    boolean FLEDGE_AD_SELECTION_CONTEXTUAL_ADS_METRICS_ENABLED = false;

    /** Returns {@code true} if contextual ads signing metrics collection is enabled */
    default boolean getFledgeAdSelectionContextualAdsMetricsEnabled() {
        return FLEDGE_AD_SELECTION_CONTEXTUAL_ADS_METRICS_ENABLED;
    }

    boolean FLEDGE_APP_INSTALL_FILTERING_METRICS_ENABLED = false;

    /** Returns {@code true} if App Install Filtering metrics is enabled. */
    default boolean getFledgeAppInstallFilteringMetricsEnabled() {
        return FLEDGE_APP_INSTALL_FILTERING_METRICS_ENABLED;
    }

    boolean FLEDGE_FREQUENCY_CAP_FILTERING_METRICS_ENABLED = false;

    /** Returns {@code true} if Frequency Cap Filtering metrics is enabled. */
    default boolean getFledgeFrequencyCapFilteringMetricsEnabled() {
        return FLEDGE_FREQUENCY_CAP_FILTERING_METRICS_ENABLED;
    }

    // Enable FLEDGE fetchAndJoinCustomAudience API.
    boolean FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED = false;

    /** Returns {@code true} if FLEDGE fetchAndJoinCustomAudience API is enabled. */
    default boolean getFledgeFetchCustomAudienceEnabled() {
        return FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED;
    }

    /** Flags related to Delayed Custom Audience Updates */

    // Enable scheduleCustomAudienceUpdateApi()
    boolean FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED = false;

    @FeatureFlag
    boolean FLEDGE_ENABLE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ADDITIONAL_SCHEDULE_REQUESTS = false;

    long FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_JOB_PERIOD_MS = 1L * 60L * 60L * 1000L; // 1 hour
    long FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_JOB_FLEX_MS = 5L * 60L * 1000L; // 5 minutes
    int FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MIN_DELAY_MINS_OVERRIDE = 30;

    @ConfigFlag int FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MAX_BYTES = 100 * 1024;

    default boolean getFledgeScheduleCustomAudienceUpdateEnabled() {
        return !getGlobalKillSwitch() && FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED;
    }

    default boolean getFledgeEnableScheduleCustomAudienceUpdateAdditionalScheduleRequests() {
        return getFledgeScheduleCustomAudienceUpdateEnabled()
                && FLEDGE_ENABLE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ADDITIONAL_SCHEDULE_REQUESTS;
    }

    default long getFledgeScheduleCustomAudienceUpdateJobPeriodMs() {
        return FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_JOB_PERIOD_MS;
    }

    default long getFledgeScheduleCustomAudienceUpdateJobFlexMs() {
        return FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_JOB_FLEX_MS;
    }

    default int getFledgeScheduleCustomAudienceMinDelayMinsOverride() {
        return FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MIN_DELAY_MINS_OVERRIDE;
    }

    default int getFledgeScheduleCustomAudienceUpdateMaxBytes() {
        return FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MAX_BYTES;
    }

    boolean FLEDGE_AD_SELECTION_PREBUILT_URI_ENABLED = false;

    /** Returns whether to call trusted servers for off device ad selection. */
    default boolean getFledgeAdSelectionPrebuiltUriEnabled() {
        return FLEDGE_AD_SELECTION_PREBUILT_URI_ENABLED;
    }

    boolean FLEDGE_AUCTION_SERVER_ENABLED = false;

    /** Returns whether to enable server auction support in post-auction APIs. */
    default boolean getFledgeAuctionServerEnabled() {
        return FLEDGE_AUCTION_SERVER_ENABLED;
    }

    boolean FLEDGE_AUCTION_SERVER_ENABLED_FOR_REPORT_IMPRESSION = true;

    /** Returns whether to enable server auction support in report impression. */
    default boolean getFledgeAuctionServerEnabledForReportImpression() {
        return getFledgeAuctionServerEnabled()
                && FLEDGE_AUCTION_SERVER_ENABLED_FOR_REPORT_IMPRESSION;
    }

    boolean FLEDGE_AUCTION_SERVER_ENABLED_FOR_REPORT_EVENT = true;

    /** Returns whether to enable server auction support in report event API. */
    default boolean getFledgeAuctionServerEnabledForReportEvent() {
        return getFledgeAuctionServerEnabled() && FLEDGE_AUCTION_SERVER_ENABLED_FOR_REPORT_EVENT;
    }

    boolean FLEDGE_AUCTION_SERVER_ENABLED_FOR_UPDATE_HISTOGRAM = true;

    /** Returns whether to enable server auction support in update histogram API. */
    default boolean getFledgeAuctionServerEnabledForUpdateHistogram() {
        return getFledgeAuctionServerEnabled()
                && FLEDGE_AUCTION_SERVER_ENABLED_FOR_UPDATE_HISTOGRAM;
    }

    boolean FLEDGE_AUCTION_SERVER_ENABLED_FOR_SELECT_ADS_MEDIATION = true;

    /** Returns whether to enable server auction support in select ads mediation API. */
    default boolean getFledgeAuctionServerEnabledForSelectAdsMediation() {
        return getFledgeAuctionServerEnabled()
                && FLEDGE_AUCTION_SERVER_ENABLED_FOR_SELECT_ADS_MEDIATION;
    }

    boolean FLEDGE_AUCTION_SERVER_ENABLE_AD_FILTER_IN_GET_AD_SELECTION_DATA = true;

    /** Returns whether to enable ad filtering in get ad selection data API. */
    default boolean getFledgeAuctionServerEnableAdFilterInGetAdSelectionData() {
        return FLEDGE_AUCTION_SERVER_ENABLE_AD_FILTER_IN_GET_AD_SELECTION_DATA;
    }

    boolean FLEDGE_AUCTION_SERVER_MEDIA_TYPE_CHANGE_ENABLED = false;

    /** Returns whether to use the server auction media type. */
    default boolean getFledgeAuctionServerMediaTypeChangeEnabled() {
        return FLEDGE_AUCTION_SERVER_MEDIA_TYPE_CHANGE_ENABLED;
    }

    ImmutableList<Integer> FLEDGE_AUCTION_SERVER_PAYLOAD_BUCKET_SIZES =
            ImmutableList.of(0, 1024, 2048, 4096, 8192, 16384, 32768, 65536);

    /** Returns available bucket sizes for auction server payloads. */
    default ImmutableList<Integer> getFledgeAuctionServerPayloadBucketSizes() {
        return FLEDGE_AUCTION_SERVER_PAYLOAD_BUCKET_SIZES;
    }

    // TODO(b/291680065): Remove when owner field is returned from B&A
    boolean FLEDGE_AUCTION_SERVER_FORCE_SEARCH_WHEN_OWNER_IS_ABSENT_ENABLED = false;

    /**
     * Returns true if forcing {@link
     * android.adservices.adselection.AdSelectionManager#persistAdSelectionResult} to continue when
     * owner is null, otherwise false.
     */
    default boolean getFledgeAuctionServerForceSearchWhenOwnerIsAbsentEnabled() {
        return FLEDGE_AUCTION_SERVER_FORCE_SEARCH_WHEN_OWNER_IS_ABSENT_ENABLED;
    }

    boolean FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_ENABLED = false;

    /** Returns whether to call remote URLs for debug reporting. */
    default boolean getFledgeEventLevelDebugReportingEnabled() {
        return FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_ENABLED;
    }

    boolean FLEDGE_EVENT_LEVEL_DEBUG_REPORT_SEND_IMMEDIATELY = false;

    /** Returns whether to call remote URLs for debug reporting. */
    default boolean getFledgeEventLevelDebugReportSendImmediately() {
        return FLEDGE_EVENT_LEVEL_DEBUG_REPORT_SEND_IMMEDIATELY;
    }

    int FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_BATCH_DELAY_SECONDS = 60 * 15;

    /** Returns minimum number of seconds between debug report batch. */
    default int getFledgeEventLevelDebugReportingBatchDelaySeconds() {
        return FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_BATCH_DELAY_SECONDS;
    }

    int FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_MAX_ITEMS_PER_BATCH = 1000;

    /** Returns maximum number of items in a debug report batch. */
    default int getFledgeEventLevelDebugReportingMaxItemsPerBatch() {
        return FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_MAX_ITEMS_PER_BATCH;
    }

    int FLEDGE_DEBUG_REPORT_SENDER_JOB_NETWORK_CONNECT_TIMEOUT_MS = 5 * 1000; // 5 seconds

    /**
     * Returns the maximum time in milliseconds allowed for a network call to open its initial
     * connection during the FLEDGE debug report sender job.
     */
    default int getFledgeDebugReportSenderJobNetworkConnectionTimeoutMs() {
        return FLEDGE_DEBUG_REPORT_SENDER_JOB_NETWORK_CONNECT_TIMEOUT_MS;
    }

    int FLEDGE_DEBUG_REPORT_SENDER_JOB_NETWORK_READ_TIMEOUT_MS = 30 * 1000; // 30 seconds

    /**
     * Returns the maximum time in milliseconds allowed for a network call to read a response from a
     * target server during the FLEDGE debug report sender job.
     */
    default int getFledgeDebugReportSenderJobNetworkReadTimeoutMs() {
        return FLEDGE_DEBUG_REPORT_SENDER_JOB_NETWORK_READ_TIMEOUT_MS;
    }

    long FLEDGE_DEBUG_REPORT_SENDER_JOB_MAX_RUNTIME_MS = 10L * 60L * 1000L; // 5 minutes

    /**
     * Returns the maximum amount of time (in milliseconds) each FLEDGE debug report sender job is
     * allowed to run.
     */
    default long getFledgeDebugReportSenderJobMaxRuntimeMs() {
        return FLEDGE_DEBUG_REPORT_SENDER_JOB_MAX_RUNTIME_MS;
    }

    long FLEDGE_DEBUG_REPORT_SENDER_JOB_PERIOD_MS = TimeUnit.MINUTES.toMillis(10);

    /**
     * Returns the best effort max time (in milliseconds) between each FLEDGE debug report sender
     * job run.
     */
    default long getFledgeDebugReportSenderJobPeriodMs() {
        return FLEDGE_DEBUG_REPORT_SENDER_JOB_PERIOD_MS;
    }

    long FLEDGE_DEBUG_REPORT_SENDER_JOB_FLEX_MS = TimeUnit.MINUTES.toMillis(2);

    /**
     * Returns the amount of flex (in milliseconds) around the end of each period to run each FLEDGE
     * debug report sender job.
     */
    default long getFledgeDebugReportSenderJobFlexMs() {
        return FLEDGE_DEBUG_REPORT_SENDER_JOB_FLEX_MS;
    }

    boolean FLEDGE_AD_SELECTION_OFF_DEVICE_REQUEST_COMPRESSION_ENABLED = true;

    /** Returns whether to compress requests sent off device for ad selection. */
    default boolean getAdSelectionOffDeviceRequestCompressionEnabled() {
        return FLEDGE_AD_SELECTION_OFF_DEVICE_REQUEST_COMPRESSION_ENABLED;
    }

    /** The server uses the following version numbers: 1. Brotli : 1 2. Gzip : 2 */
    int FLEDGE_AUCTION_SERVER_COMPRESSION_ALGORITHM_VERSION = 2;

    /** Returns the compression algorithm version */
    default int getFledgeAuctionServerCompressionAlgorithmVersion() {
        return FLEDGE_AUCTION_SERVER_COMPRESSION_ALGORITHM_VERSION;
    }

    String FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_URI =
            "https://publickeyservice.pa.gcp.privacysandboxservices.com/"
                    + ".well-known/protected-auction/v1/public-keys";

    /** Returns Uri to fetch auction encryption key for fledge ad selection. */
    default String getFledgeAuctionServerAuctionKeyFetchUri() {
        return FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_URI;
    }

    boolean FLEDGE_AUCTION_SERVER_REFRESH_EXPIRED_KEYS_DURING_AUCTION = false;

    default boolean getFledgeAuctionServerRefreshExpiredKeysDuringAuction() {
        return FLEDGE_AUCTION_SERVER_REFRESH_EXPIRED_KEYS_DURING_AUCTION;
    }

    /** Default value of the url to fetch keys for KAnon encryption */
    String FLEDGE_AUCTION_SERVER_JOIN_KEY_FETCH_URI = "";

    /** Returns Uri to fetch join encryption key for fledge ad selection. */
    default String getFledgeAuctionServerJoinKeyFetchUri() {
        return FLEDGE_AUCTION_SERVER_JOIN_KEY_FETCH_URI;
    }

    int FLEDGE_AUCTION_SERVER_AUCTION_KEY_SHARDING = 5;

    /** Returns Shard count for using auction key for fledge ad selection. */
    default int getFledgeAuctionServerAuctionKeySharding() {
        return FLEDGE_AUCTION_SERVER_AUCTION_KEY_SHARDING;
    }

    long FLEDGE_AUCTION_SERVER_ENCRYPTION_KEY_MAX_AGE_SECONDS = TimeUnit.DAYS.toSeconds(14);

    default long getFledgeAuctionServerEncryptionKeyMaxAgeSeconds() {
        return FLEDGE_AUCTION_SERVER_ENCRYPTION_KEY_MAX_AGE_SECONDS;
    }

    int FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_KDF_ID = 0x0001;

    default int getFledgeAuctionServerEncryptionAlgorithmKdfId() {
        return FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_KDF_ID;
    }

    int FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_KEM_ID = 0x0020;

    default int getFledgeAuctionServerEncryptionAlgorithmKemId() {
        return FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_KEM_ID;
    }

    int FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_AEAD_ID = 0x0002;

    default int getFledgeAuctionServerEncryptionAlgorithmAeadId() {
        return FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_AEAD_ID;
    }

    int FLEDGE_AUCTION_SERVER_PAYLOAD_FORMAT_VERSION = 0;

    /** Returns the payload formatter version */
    default int getFledgeAuctionServerPayloadFormatVersion() {
        return FLEDGE_AUCTION_SERVER_PAYLOAD_FORMAT_VERSION;
    }

    long FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_TIMEOUT_MS = 3000;

    default long getFledgeAuctionServerAuctionKeyFetchTimeoutMs() {
        return FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_TIMEOUT_MS;
    }

    long FLEDGE_AUCTION_SERVER_OVERALL_TIMEOUT_MS = 5000;

    default long getFledgeAuctionServerOverallTimeoutMs() {
        return FLEDGE_AUCTION_SERVER_OVERALL_TIMEOUT_MS;
    }

    boolean FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_ENABLED = false;

    /** Returns whether to run periodic job to fetch encryption keys. */
    default boolean getFledgeAuctionServerBackgroundKeyFetchJobEnabled() {
        return FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_ENABLED;
    }

    int FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_CONNECT_TIMEOUT_MS =
            5 * 1000; // 5 seconds

    /**
     * Returns the maximum time in milliseconds allowed for a network call to open its initial
     * connection during the FLEDGE encryption key fetch.
     */
    default int getFledgeAuctionServerBackgroundKeyFetchNetworkConnectTimeoutMs() {
        return FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_CONNECT_TIMEOUT_MS;
    }

    int FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_READ_TIMEOUT_MS =
            30 * 1000; // 30 seconds

    /**
     * Returns the maximum time in milliseconds allowed for a network call to read a response from a
     * target server during the FLEDGE encryption key fetch.
     */
    default int getFledgeAuctionServerBackgroundKeyFetchNetworkReadTimeoutMs() {
        return FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_READ_TIMEOUT_MS;
    }

    int FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RESPONSE_SIZE_B = 2 * 1024; // 2 KiB

    /**
     * Returns the maximum size in bytes of a single key fetch response during the FLEDGE encryption
     * key fetch.
     */
    default int getFledgeAuctionServerBackgroundKeyFetchMaxResponseSizeB() {
        return FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RESPONSE_SIZE_B;
    }

    boolean FLEDGE_AUCTION_SERVER_BACKGROUND_AUCTION_KEY_FETCH_ENABLED = false;

    /** Returns whether to run periodic job to fetch AUCTION keys. */
    default boolean getFledgeAuctionServerBackgroundAuctionKeyFetchEnabled() {
        return getFledgeAuctionServerBackgroundKeyFetchJobEnabled()
                && FLEDGE_AUCTION_SERVER_BACKGROUND_AUCTION_KEY_FETCH_ENABLED;
    }

    boolean FLEDGE_AUCTION_SERVER_BACKGROUND_JOIN_KEY_FETCH_ENABLED = false;

    /** Returns whether to run periodic job to fetch JOIN keys. */
    default boolean getFledgeAuctionServerBackgroundJoinKeyFetchEnabled() {
        return getFledgeAuctionServerBackgroundKeyFetchJobEnabled()
                && FLEDGE_AUCTION_SERVER_BACKGROUND_JOIN_KEY_FETCH_ENABLED;
    }

    long FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RUNTIME_MS = TimeUnit.MINUTES.toMillis(5);

    /**
     * Returns the maximum amount of time (in milliseconds) each Ad selection Background key Fetch
     * job is allowed to run.
     */
    default long getFledgeAuctionServerBackgroundKeyFetchJobMaxRuntimeMs() {
        return FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RUNTIME_MS;
    }

    long FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_PERIOD_MS = TimeUnit.HOURS.toMillis(24);

    /**
     * Returns the best effort max time (in milliseconds) between each Background Key Fetch job run.
     */
    default long getFledgeAuctionServerBackgroundKeyFetchJobPeriodMs() {
        return FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_PERIOD_MS;
    }

    long FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_FLEX_MS = TimeUnit.HOURS.toMillis(2);

    /**
     * Returns the amount of flex (in milliseconds) around the end of each period to run each
     * Background Key Fetch job.
     */
    default long getFledgeAuctionServerBackgroundKeyFetchJobFlexMs() {
        return FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_FLEX_MS;
    }

    boolean FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_ON_EMPTY_DB_AND_IN_ADVANCE_ENABLED = false;

    /**
     * Returns whether that the periodic job to fetch encryption keys should force refresh if the
     * database is empty or if the keys are within
     * FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_IN_ADVANCE_INTERVAL_MS to expire.
     */
    default boolean getFledgeAuctionServerBackgroundKeyFetchOnEmptyDbAndInAdvanceEnabled() {
        return getFledgeAuctionServerBackgroundKeyFetchJobEnabled()
                && FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_ON_EMPTY_DB_AND_IN_ADVANCE_ENABLED;
    }

    long FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_IN_ADVANCE_INTERVAL_MS =
            TimeUnit.HOURS.toMillis(24);

    /**
     * Returns the interval at which a key is considered to be almost expired and preventive
     * refreshed
     */
    default long getFledgeAuctionServerBackgroundKeyFetchInAdvanceIntervalMs() {
        return FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_IN_ADVANCE_INTERVAL_MS;
    }

    boolean FLEDGE_AUCTION_SERVER_ENABLE_DEBUG_REPORTING = true;

    default boolean getFledgeAuctionServerEnableDebugReporting() {
        return FLEDGE_AUCTION_SERVER_ENABLE_DEBUG_REPORTING;
    }

    long DEFAULT_AUCTION_SERVER_AD_ID_FETCHER_TIMEOUT_MS = 20;

    /**
     * Returns configured timeout value for {@link
     * com.android.adservices.service.adselection.AdIdFetcher} logic for server auctions.
     *
     * <p>The intended goal is to override this value for tests.
     *
     * <p>Returns Timeout in mills.
     */
    default long getFledgeAuctionServerAdIdFetcherTimeoutMs() {
        return DEFAULT_AUCTION_SERVER_AD_ID_FETCHER_TIMEOUT_MS;
    }

    /** Default value for feature flag for PAS unlimited egress in Server auctions. */
    boolean DEFAULT_FLEDGE_AUCTION_SERVER_ENABLE_PAS_UNLIMITED_EGRESS = false;

    /**
     * @return feature flag to enable PAS unlimited egress in Server auctions
     */
    default boolean getFledgeAuctionServerEnablePasUnlimitedEgress() {
        return DEFAULT_FLEDGE_AUCTION_SERVER_ENABLE_PAS_UNLIMITED_EGRESS;
    }

    boolean FLEDGE_AUCTION_SERVER_AD_RENDER_ID_ENABLED = false;
    long FLEDGE_AUCTION_SERVER_AD_RENDER_ID_MAX_LENGTH = 12L;

    /** Returns whether ad render id is enabled. */
    default boolean getFledgeAuctionServerAdRenderIdEnabled() {
        return FLEDGE_AUCTION_SERVER_AD_RENDER_ID_ENABLED;
    }

    /** Returns the max length of Ad Render Id. */
    default long getFledgeAuctionServerAdRenderIdMaxLength() {
        return FLEDGE_AUCTION_SERVER_AD_RENDER_ID_MAX_LENGTH;
    }

    boolean FLEDGE_AUCTION_SERVER_REQUEST_FLAGS_ENABLED = false;

    /** Returns whether the server auction request flags are enabled */
    default boolean getFledgeAuctionServerRequestFlagsEnabled() {
        return FLEDGE_AUCTION_SERVER_REQUEST_FLAGS_ENABLED;
    }

    boolean FLEDGE_AUCTION_SERVER_OMIT_ADS_ENABLED = false;

    /** Returns whether the omit-ads flag is enabled for the server auction. */
    default boolean getFledgeAuctionServerOmitAdsEnabled() {
        return FLEDGE_AUCTION_SERVER_OMIT_ADS_ENABLED;
    }

    boolean FLEDGE_AUCTION_SERVER_MULTI_CLOUD_ENABLED = false;

    default boolean getFledgeAuctionServerMultiCloudEnabled() {
        return FLEDGE_AUCTION_SERVER_MULTI_CLOUD_ENABLED;
    }

    String FLEDGE_AUCTION_SERVER_COORDINATOR_URL_ALLOWLIST =
            "https://publickeyservice-v150"
                    + ".coordinator-a.bas-gcp.pstest.dev/"
                    + ".well-known/protected-auction/v1/public-keys";

    default String getFledgeAuctionServerCoordinatorUrlAllowlist() {
        return FLEDGE_AUCTION_SERVER_COORDINATOR_URL_ALLOWLIST;
    }

    @FeatureFlag
    boolean FLEDGE_AUCTION_SERVER_GET_AD_SELECTION_DATA_PAYLOAD_METRICS_ENABLED = false;

    /** Returns whether the fledge GetAdSelectionData payload metrics are enabled. */
    default boolean getFledgeAuctionServerGetAdSelectionDataPayloadMetricsEnabled() {
        return FLEDGE_AUCTION_SERVER_GET_AD_SELECTION_DATA_PAYLOAD_METRICS_ENABLED;
    }

    @FeatureFlag boolean FLEDGE_GET_AD_SELECTION_DATA_SELLER_CONFIGURATION_ENABLED = false;

    /** Returns whether the seller configuration feature for getAdSelectionData is enabled. */
    default boolean getFledgeGetAdSelectionDataSellerConfigurationEnabled() {
        return FLEDGE_GET_AD_SELECTION_DATA_SELLER_CONFIGURATION_ENABLED;
    }

    @ConfigFlag int FLEDGE_GET_AD_SELECTION_DATA_BUYER_INPUT_CREATOR_VERSION = 0;

    /** Returns the getAdSelectionData data buyer input creator version */
    default int getFledgeGetAdSelectionDataBuyerInputCreatorVersion() {
        return FLEDGE_GET_AD_SELECTION_DATA_BUYER_INPUT_CREATOR_VERSION;
    }

    @ConfigFlag int FLEDGE_GET_AD_SELECTION_DATA_MAX_NUM_ENTIRE_PAYLOAD_COMPRESSIONS = 5;

    /**
     * Returns the maximum number of times we can re-compress the entire payload during
     * getAdSelectionData payload optimization.
     */
    default int getFledgeGetAdSelectionDataMaxNumEntirePayloadCompressions() {
        return FLEDGE_GET_AD_SELECTION_DATA_MAX_NUM_ENTIRE_PAYLOAD_COMPRESSIONS;
    }

    @FeatureFlag boolean FLEDGE_GET_AD_SELECTION_DATA_DESERIALIZE_ONLY_AD_RENDER_IDS = false;

    /**
     * Returns whether querying custom audiences from the DB, for the getAdSelectionData API, will
     * deserialize only ad render ids or the entire ad. When enabled, the DB query will only
     * deserialize ad render ids.
     */
    default boolean getFledgeGetAdSelectionDataDeserializeOnlyAdRenderIds() {
        return FLEDGE_GET_AD_SELECTION_DATA_DESERIALIZE_ONLY_AD_RENDER_IDS;
    }

    // Protected signals cleanup feature flag disabled by default
    boolean PROTECTED_SIGNALS_CLEANUP_ENABLED = false;

    /** Returns {@code true} if protected signals cleanup is enabled. */
    default boolean getProtectedSignalsCleanupEnabled() {
        return PROTECTED_SIGNALS_CLEANUP_ENABLED;
    }

    boolean ADSERVICES_ENABLED = false;

    default boolean getAdServicesEnabled() {
        return ADSERVICES_ENABLED;
    }

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

    /** MDD Topics API Classifier Manifest Url. Topics classifier v2-3. Build_id = 1467. */
    String MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-topics-classifier/1467"
                    + "/80c34503413cea9ea44cbe94cd38dabc44ea8d70";

    default String getMddTopicsClassifierManifestFileUrl() {
        return MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL;
    }

    boolean CONSENT_MANAGER_LAZY_ENABLE_MODE = true;

    default boolean getConsentManagerLazyEnableMode() {
        return CONSENT_MANAGER_LAZY_ENABLE_MODE;
    }

    boolean CONSENT_ALREADY_INTERACTED_FIX_ENABLE = true;

    default boolean getConsentAlreadyInteractedEnableMode() {
        return CONSENT_ALREADY_INTERACTED_FIX_ENABLE;
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

    /** Available sources of truth to get consent for PPAPI. */
    @IntDef(
            flag = true,
            value = {
                SYSTEM_SERVER_ONLY,
                PPAPI_ONLY,
                PPAPI_AND_SYSTEM_SERVER,
                APPSEARCH_ONLY,
            })
    @Retention(RetentionPolicy.SOURCE)
    @interface ConsentSourceOfTruth {}

    /** Write and read consent from system server only. */
    int SYSTEM_SERVER_ONLY = FlagsConstants.SYSTEM_SERVER_ONLY;

    /** Write and read consent from PPAPI only */
    int PPAPI_ONLY = FlagsConstants.PPAPI_ONLY;

    /** Write consent to both PPAPI and system server. Read consent from system server only. */
    int PPAPI_AND_SYSTEM_SERVER = FlagsConstants.PPAPI_AND_SYSTEM_SERVER;

    /**
     * Write consent data to AppSearch only. To store consent data in AppSearch the flag
     * enable_appsearch_consent_data must also be true. This ensures that both writes and reads can
     * happen to/from AppSearch. The writes are done by code on S-, while reads are done from code
     * running on S- for all consent requests and on T+ once after OTA.
     */
    int APPSEARCH_ONLY = FlagsConstants.APPSEARCH_ONLY;

    /**
     * Consent source of truth intended to be used by default. On S devices, there is no AdServices
     * code running in the system server, so the default for those is PPAPI_ONLY.
     */
    @ConsentSourceOfTruth
    int DEFAULT_CONSENT_SOURCE_OF_TRUTH =
            SdkLevel.isAtLeastT() ? PPAPI_AND_SYSTEM_SERVER : APPSEARCH_ONLY;

    /** Returns the consent source of truth currently used for PPAPI. */
    @ConsentSourceOfTruth
    default int getConsentSourceOfTruth() {
        return DEFAULT_CONSENT_SOURCE_OF_TRUTH;
    }

    /**
     * Blocked topics source of truth intended to be used by default. On S- devices, there is no
     * AdServices code running in the system server, so the default for those is PPAPI_ONLY.
     */
    @ConsentSourceOfTruth
    int DEFAULT_BLOCKED_TOPICS_SOURCE_OF_TRUTH =
            SdkLevel.isAtLeastT() ? PPAPI_AND_SYSTEM_SERVER : APPSEARCH_ONLY;

    /** Returns the blocked topics source of truth currently used for PPAPI */
    @ConsentSourceOfTruth
    default int getBlockedTopicsSourceOfTruth() {
        return DEFAULT_BLOCKED_TOPICS_SOURCE_OF_TRUTH;
    }

    /**
     * The debug and release SHA certificates of the AdServices APK. This is required when writing
     * consent data to AppSearch in order to allow reads from T+ APK. This is a comma separated
     * list.
     */
    String ADSERVICES_APK_SHA_CERTIFICATE =
            "686d5c450e00ebe600f979300a29234644eade42f24ede07a073f2bc6b94a3a2," // debug
                    + "80f8fbb9a026807f58d98dbc28bf70724d8f66bbfcec997c6bdc0102c3230dee"; // release

    /** Only App signatures belonging to this Allow List can use PP APIs. */
    default String getAdservicesApkShaCertificate() {
        return ADSERVICES_APK_SHA_CERTIFICATE;
    }

    // Group of All Killswitches

    /**
     * Global PP API Kill Switch. This overrides all other killswitches. The default value is false
     * which means the PP API is enabled. This flag is used for emergency turning off the whole PP
     * API.
     */
    // Starting M-2023-05, global kill switch is enabled in the binary. Prior to this (namely in
    // M-2022-11), the value of this flag in the binary was false.
    @FeatureFlag(LEGACY_KILL_SWITCH_GLOBAL)
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean GLOBAL_KILL_SWITCH = true;

    default boolean getGlobalKillSwitch() {
        return GLOBAL_KILL_SWITCH;
    }

    // MEASUREMENT Killswitches

    /**
     * Measurement Kill Switch. This overrides all specific measurement kill switch. The default
     * value is {@code false} which means that Measurement is enabled.
     *
     * <p>This flag is used for emergency turning off the whole Measurement API.
     */
    @FeatureFlag(LEGACY_KILL_SWITCH_RAMPED_UP)
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_KILL_SWITCH = false;

    /**
     * @deprecated - TODO(b/325074749): remove once all methods that call it are unit-tested and
     *     changed to use !getMeasurementEnabled()
     */
    @Deprecated
    @VisibleForTesting
    default boolean getLegacyMeasurementKillSwitch() {
        return getGlobalKillSwitch() || MEASUREMENT_KILL_SWITCH;
    }

    /**
     * Returns whether the Global Measurement feature is enabled. Measurement will be disabled if
     * either the Global Kill Switch or the Measurement Kill Switch value is {@code true}.
     */
    default boolean getMeasurementEnabled() {
        return getGlobalKillSwitch() ? false : !MEASUREMENT_KILL_SWITCH;
    }

    /**
     * Measurement API Delete Registrations Kill Switch. The default value is false which means
     * Delete Registrations API is enabled. This flag is used for emergency turning off the Delete
     * Registrations API.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement API Delete Registrations. The API will be
     * disabled if either the Global Kill Switch, Measurement Kill Switch, or the Measurement API
     * Delete Registration Kill Switch value is true.
     */
    default boolean getMeasurementApiDeleteRegistrationsKillSwitch() {
        return getLegacyMeasurementKillSwitch() || MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH;
    }

    /**
     * Measurement API Status Kill Switch. The default value is false which means Status API is
     * enabled. This flag is used for emergency turning off the Status API.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_API_STATUS_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement API Status. The API will be disabled if either
     * the Global Kill Switch, Measurement Kill Switch, or the Measurement API Status Kill Switch
     * value is true.
     */
    default boolean getMeasurementApiStatusKillSwitch() {
        return getLegacyMeasurementKillSwitch() || MEASUREMENT_API_STATUS_KILL_SWITCH;
    }

    /**
     * Measurement API Register Source Kill Switch. The default value is false which means Register
     * Source API is enabled. This flag is used for emergency turning off the Register Source API.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement API Register Source. The API will be disabled
     * if either the Global Kill Switch, Measurement Kill Switch, or the Measurement API Register
     * Source Kill Switch value is true.
     */
    default boolean getMeasurementApiRegisterSourceKillSwitch() {
        return getLegacyMeasurementKillSwitch() || MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH;
    }

    /**
     * Measurement API Register Trigger Kill Switch. The default value is false which means Register
     * Trigger API is enabled. This flag is used for emergency turning off the Register Trigger API.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement API Register Trigger. The API will be disabled
     * if either the Global Kill Switch, Measurement Kill Switch, or the Measurement API Register
     * Trigger Kill Switch value is true.
     */
    default boolean getMeasurementApiRegisterTriggerKillSwitch() {
        return getLegacyMeasurementKillSwitch() || MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH;
    }

    /**
     * Measurement API Register Web Source Kill Switch. The default value is false which means
     * Register Web Source API is enabled. This flag is used for emergency turning off the Register
     * Web Source API.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement API Register Web Source. The API will be
     * disabled if either the Global Kill Switch, Measurement Kill Switch, or the Measurement API
     * Register Web Source Kill Switch value is true.
     */
    default boolean getMeasurementApiRegisterWebSourceKillSwitch() {
        return getLegacyMeasurementKillSwitch() || MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH;
    }

    /**
     * Measurement API Register Sources Kill Switch. The default value is false which means Register
     * Sources API is enabled. This flag is used for emergency turning off the Register Sources API.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_API_REGISTER_SOURCES_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement API Register Sources. The API will be disabled
     * if either the Global Kill Switch, Measurement Kill Switch, or the Measurement API Register
     * Sources Kill Switch value is true.
     */
    default boolean getMeasurementApiRegisterSourcesKillSwitch() {
        return getLegacyMeasurementKillSwitch() || MEASUREMENT_API_REGISTER_SOURCES_KILL_SWITCH;
    }

    /**
     * Measurement API Register Web Trigger Kill Switch. The default value is false which means
     * Register Web Trigger API is enabled. This flag is used for emergency turning off the Register
     * Web Trigger API.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement API Register Web Trigger. The API will be
     * disabled if either the Global Kill Switch, Measurement Kill Switch, or the Measurement API
     * Register Web Trigger Kill Switch value is true.
     */
    default boolean getMeasurementApiRegisterWebTriggerKillSwitch() {
        return getLegacyMeasurementKillSwitch() || MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH;
    }

    /**
     * Measurement Job Aggregate Fallback Reporting Kill Switch. The default value is false which
     * means Aggregate Fallback Reporting Job is enabled. This flag is used for emergency turning
     * off the Aggregate Fallback Reporting Job.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement Job Aggregate Fallback Reporting. The API will
     * be disabled if either the Global Kill Switch, Measurement Kill Switch, or the Measurement Job
     * Aggregate Fallback Reporting Kill Switch value is true.
     */
    default boolean getMeasurementJobAggregateFallbackReportingKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH;
    }

    /**
     * Measurement Job Aggregate Reporting Kill Switch. The default value is false which means
     * Aggregate Reporting Job is enabled. This flag is used for emergency turning off the Aggregate
     * Reporting Job.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement Job Aggregate Reporting. The API will be
     * disabled if either the Global Kill Switch, Measurement Kill Switch, or the Measurement Job
     * Aggregate Reporting Kill Switch value is true.
     */
    default boolean getMeasurementJobAggregateReportingKillSwitch() {
        return getLegacyMeasurementKillSwitch() || MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH;
    }

    /**
     * Measurement Immediate Aggregate Reporting Job Kill Switch. The default value is true which
     * means Immediate Aggregate Reporting Job is disabled. This flag is used for emergency turning
     * off of the Immediate Aggregate Reporting Job.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_JOB_IMMEDIATE_AGGREGATE_REPORTING_KILL_SWITCH = true;

    /**
     * Returns the kill switch value for Measurement Immediate Aggregate Reporting Job. The API will
     * be disabled if either the Global Kill Switch, Measurement Kill Switch, or the Measurement Job
     * Immediate Aggregate Reporting Kill Switch value is true.
     */
    default boolean getMeasurementJobImmediateAggregateReportingKillSwitch() {
        return !getMeasurementEnabled()
                || MEASUREMENT_JOB_IMMEDIATE_AGGREGATE_REPORTING_KILL_SWITCH;
    }

    /**
     * Measurement Job Attribution Kill Switch. The default value is false which means Attribution
     * Job is enabled. This flag is used for emergency turning off the Attribution Job.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement Job Attribution. The API will be disabled if
     * either the Global Kill Switch, Measurement Kill Switch, or the Measurement Job Attribution
     * Kill Switch value is true.
     */
    default boolean getMeasurementJobAttributionKillSwitch() {
        return getLegacyMeasurementKillSwitch() || MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH;
    }

    /**
     * Measurement Job Delete Expired Kill Switch. The default value is false which means Delete
     * Expired Job is enabled. This flag is used for emergency turning off the Delete Expired Job.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement Job Delete Expired. The API will be disabled if
     * either the Global Kill Switch, Measurement Kill Switch, or the Measurement Job Delete Expired
     * Kill Switch value is true.
     */
    default boolean getMeasurementJobDeleteExpiredKillSwitch() {
        return getLegacyMeasurementKillSwitch() || MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH;
    }

    /**
     * Measurement Job Delete Uninstalled Kill Switch. The default value is false which means Delete
     * Uninstalled Job is enabled. This flag is used for emergency turning off the Delete
     * Uninstalled Job.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_JOB_DELETE_UNINSTALLED_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement Job Delete Uninstalled. The API will be
     * disabled if either the Global Kill Switch, Measurement Kill Switch, or the Measurement Job
     * Delete Uninstalled Kill Switch value is true.
     */
    default boolean getMeasurementJobDeleteUninstalledKillSwitch() {
        return getLegacyMeasurementKillSwitch() || MEASUREMENT_JOB_DELETE_UNINSTALLED_KILL_SWITCH;
    }

    /**
     * Measurement Job Event Fallback Reporting Kill Switch. The default value is false which means
     * Event Fallback Reporting Job is enabled. This flag is used for emergency turning off the
     * Event Fallback Reporting Job.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement Job Event Fallback Reporting. The API will be
     * disabled if either the Global Kill Switch, Measurement Kill Switch, or the Measurement Job
     * Event Fallback Reporting Kill Switch value is true.
     */
    default boolean getMeasurementJobEventFallbackReportingKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH;
    }

    /**
     * Measurement Job Event Reporting Kill Switch. The default value is false which means Event
     * Reporting Job is enabled. This flag is used for emergency turning off the Event Reporting
     * Job.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement Job Event Reporting. The API will be disabled
     * if either the Global Kill Switch, Measurement Kill Switch, or the Measurement Job Event
     * Reporting Kill Switch value is true.
     */
    default boolean getMeasurementJobEventReportingKillSwitch() {
        return getLegacyMeasurementKillSwitch() || MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH;
    }

    /**
     * Measurement Job Debug Reporting Kill Switch. The default value is false which means Debug
     * Reporting Job is enabled. This flag is used for emergency turning off the Debug Reporting
     * Job.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_JOB_DEBUG_REPORTING_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement Job Debug Reporting. The Job will be disabled
     * if either the Global Kill Switch, Measurement Kill Switch, or the Measurement Job Debug
     * Reporting Kill Switch value is true.
     */
    default boolean getMeasurementJobDebugReportingKillSwitch() {
        return getLegacyMeasurementKillSwitch() || MEASUREMENT_JOB_DEBUG_REPORTING_KILL_SWITCH;
    }

    /**
     * Measurement Debug Reporting Fallback Job kill Switch. The default value is false which means
     * the job is enabled. This flag is used for emergency turning off the Debug Reporting Fallback
     * Job.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for the Measurement Debug Reporting Fallback Job. The API will
     * be disabled if either the Global Kill Switch, Measurement Kill Switch, or the Measurement
     * Debug Reporting Fallback Job kill switch value is true.
     */
    default boolean getMeasurementDebugReportingFallbackJobKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH;
    }

    /**
     * Measurement Verbose Debug Reporting Fallback Job kill Switch. The default value is false
     * which means the job is enabled. This flag is used for emergency turning off the Verbose Debug
     * Reporting Fallback Job.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for the Measurement Debug Reporting Fallback Job. The API will
     * be disabled if either the Global Kill Switch, Measurement Kill Switch, or the Measurement
     * Debug Reporting Fallback Job kill switch value is true.
     */
    default boolean getMeasurementVerboseDebugReportingFallbackJobKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH;
    }

    /**
     * Returns the job period in millis for the Measurement Verbose Debug Reporting Fallback Job.
     */
    long MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_PERIOD_MS = TimeUnit.HOURS.toMillis(1);

    /**
     * Returns the job period in millis for the Measurement Verbose Debug Reporting Fallback Job.
     */
    default long getMeasurementVerboseDebugReportingFallbackJobPeriodMs() {
        return MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_PERIOD_MS;
    }

    /** Returns the job period in millis for the Measurement Debug Reporting Fallback Job. */
    long MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_PERIOD_MS = TimeUnit.HOURS.toMillis(1);

    /** Returns the job period in millis for the Measurement Debug Reporting Fallback Job. */
    default long getMeasurementDebugReportingFallbackJobPeriodMs() {
        return MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_PERIOD_MS;
    }

    /*
     * Measurement Job Verbose Debug Reporting Kill Switch. The default value is false which means
     * the Verbose Debug Reporting Job is enabled. This flag is used for emergency turning off the
     * Verbose Debug Reporting Job.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_JOB_VERBOSE_DEBUG_REPORTING_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement Job Verbose Debug Reporting. The Job will be
     * disabled if either the Global Kill Switch, Measurement Kill Switch, or the Measurement Job
     * Verbose Debug Reporting Kill Switch value is true.
     */
    default boolean getMeasurementJobVerboseDebugReportingKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || MEASUREMENT_JOB_VERBOSE_DEBUG_REPORTING_KILL_SWITCH;
    }

    /**
     * Measurement Broadcast Receiver Install Attribution Kill Switch. The default value is false
     * which means Install Attribution is enabled. This flag is used for emergency turning off
     * Install Attribution Broadcast Receiver.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement Broadcast Receiver Install Attribution. The
     * Broadcast Receiver will be disabled if either the Global Kill Switch, Measurement Kill Switch
     * or the Measurement Kill Switch value is true.
     */
    default boolean getMeasurementReceiverInstallAttributionKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH;
    }

    /**
     * Measurement Broadcast Receiver Delete Packages Kill Switch. The default value is false which
     * means Delete Packages is enabled. This flag is used for emergency turning off Delete Packages
     * Broadcast Receiver.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement Broadcast Receiver Delete Packages. The
     * Broadcast Receiver will be disabled if either the Global Kill Switch, Measurement Kill Switch
     * or the Measurement Kill Switch value is true.
     */
    default boolean getMeasurementReceiverDeletePackagesKillSwitch() {
        return getLegacyMeasurementKillSwitch() || MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH;
    }

    /**
     * Measurement Rollback Kill Switch. The default value is false which means the rollback
     * handling on measurement service start is enabled. This flag is used for emergency turning off
     * measurement rollback data deletion handling.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Measurement rollback deletion handling. The rollback
     * deletion handling will be disabled if the Global Kill Switch, Measurement Kill Switch or the
     * Measurement rollback deletion Kill Switch value is true.
     */
    default boolean getMeasurementRollbackDeletionKillSwitch() {
        return getLegacyMeasurementKillSwitch() || MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH;
    }

    /**
     * Kill Switch for storing Measurement Rollback data in App Search for Android S. The default
     * value is false which means storing the rollback handling data in App Search is enabled. This
     * flag is used for emergency turning off measurement rollback data deletion handling on Android
     * S.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for storing Measurement rollback deletion handling data in App
     * Search. The rollback deletion handling on Android S will be disabled if this kill switch
     * value is true.
     */
    default boolean getMeasurementRollbackDeletionAppSearchKillSwitch() {
        return MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH;
    }

    // ADID Killswitch.
    /**
     * AdId API Kill Switch. The default value is false which means the AdId API is enabled. This
     * flag is used for emergency turning off the AdId API.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
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
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean APPSETID_KILL_SWITCH = false; // By default, the AppSetId API is enabled.

    /** Gets the state of the global and appSetId kill switch. */
    default boolean getAppSetIdKillSwitch() {
        return APPSETID_KILL_SWITCH;
    }

    // TOPICS Killswitches

    /**
     * Topics API Kill Switch. The default value is {@code true} which means the Topics API is
     * disabled.
     *
     * <p>This flag is used for emergency turning off the Topics API.
     */
    @FeatureFlag(LEGACY_KILL_SWITCH)
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean TOPICS_KILL_SWITCH = true;

    /** Returns value of Topics API kill switch */
    default boolean getTopicsKillSwitch() {
        return getGlobalKillSwitch() || TOPICS_KILL_SWITCH;
    }

    /**
     * Topics on-device classifier Kill Switch. The default value is false which means the on-device
     * classifier in enabled. This flag is used for emergency turning off the on-device classifier.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH = false;

    /** Returns value of Topics on-device classifier kill switch. */
    default boolean getTopicsOnDeviceClassifierKillSwitch() {
        return TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH;
    }

    // MDD Killswitches

    /**
     * MDD Background Task Kill Switch. The default value is false which means the MDD background
     * task is enabled. This flag is used for emergency turning off the MDD background tasks.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MDD_BACKGROUND_TASK_KILL_SWITCH = false;

    /** Returns value of Mdd Background Task kill switch */
    default boolean getMddBackgroundTaskKillSwitch() {
        return getGlobalKillSwitch() || MDD_BACKGROUND_TASK_KILL_SWITCH;
    }

    /**
     * MDD Logger Kill Switch. The default value is false which means the MDD Logger is enabled.
     * This flag is used for emergency turning off the MDD Logger.
     */
    @FeatureFlag(LEGACY_KILL_SWITCH_RAMPED_UP)
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MDD_LOGGER_KILL_SWITCH = false;

    /**
     * Returns whether the MDD Logger feature is enabled.
     *
     * <p>MDD Logger will be disabled if either the {@link #getGlobalKillSwitch() Global Kill
     * Switch} or the {@link #MDD_LOGGER_KILL_SWITCH} value is {@code true}.
     */
    default boolean getMddLoggerEnabled() {
        return getGlobalKillSwitch() ? false : !MDD_LOGGER_KILL_SWITCH;
    }

    // FLEDGE Kill switches

    /**
     * Fledge AdSelectionService kill switch. The default value is false which means that
     * AdSelectionService is enabled by default. This flag should be should as emergency andon cord.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean FLEDGE_SELECT_ADS_KILL_SWITCH = false;

    /** Returns value of Fledge Ad Selection Service API kill switch . */
    default boolean getFledgeSelectAdsKillSwitch() {
        // Check for global kill switch first, as it should override all other kill switches
        return getGlobalKillSwitch() || FLEDGE_SELECT_ADS_KILL_SWITCH;
    }

    /**
     * Fledge Auction Server API Kill switch. The default value is true which means that Auction
     * server APIs is disabled by default.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean FLEDGE_AUCTION_SERVER_KILL_SWITCH = true;

    /** Returns value of Fledge Auction server API kill switch. */
    default boolean getFledgeAuctionServerKillSwitch() {
        return getFledgeSelectAdsKillSwitch() || FLEDGE_AUCTION_SERVER_KILL_SWITCH;
    }

    /**
     * Fledge On Device Auction API Kill switch. The default value is false which means that On
     * Device Auction APIs is enabled by default.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean FLEDGE_ON_DEVICE_AUCTION_KILL_SWITCH = false;

    /** Returns value of On Device Auction API kill switch. */
    default boolean getFledgeOnDeviceAuctionKillSwitch() {
        return getFledgeSelectAdsKillSwitch() || FLEDGE_ON_DEVICE_AUCTION_KILL_SWITCH;
    }

    /**
     * Fledge Join Custom Audience API kill switch. The default value is false which means that Join
     * Custom Audience API is enabled by default. This flag should be should as emergency andon
     * cord.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH = false;

    /** Returns value of Fledge Join Custom Audience API kill switch */
    default boolean getFledgeCustomAudienceServiceKillSwitch() {
        // Check for global kill switch first, as it should override all other kill switches
        return getGlobalKillSwitch() || FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH;
    }

    /**
     * Protected signals API feature flag. The default value is {@code false}, which means that
     * protected signals is disabled by default.
     */
    @FeatureFlag boolean PROTECTED_SIGNALS_ENABLED = false;

    /** Returns value of the protected signals feature flag. */
    default boolean getProtectedSignalsEnabled() {
        // Check for global kill switch first, as it should override all other kill switches
        return getGlobalKillSwitch() ? false : PROTECTED_SIGNALS_ENABLED;
    }

    // Encryption key Kill switches

    /**
     * Encryption key new enrollment fetch kill switch. The default value is false which means
     * fetching encryption keys for new enrollments is enabled by default. This flag is used for
     * emergency turning off fetching encryption keys for new enrollments.
     *
     * <p>Set true to disable the function since no adtech actually provide encryption endpoint now.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean ENCRYPTION_KEY_NEW_ENROLLMENT_FETCH_KILL_SWITCH = true;

    /** Returns value of encryption key new enrollment fetch job kill switch */
    default boolean getEncryptionKeyNewEnrollmentFetchKillSwitch() {
        return getGlobalKillSwitch() || ENCRYPTION_KEY_NEW_ENROLLMENT_FETCH_KILL_SWITCH;
    }

    /**
     * Encryption key periodic fetch job kill switch. The default value is false which means
     * periodically fetching encryption keys is enabled by default. This flag is used for emergency
     * turning off periodically fetching encryption keys.
     *
     * <p>Set true to disable the function since no adtech actually provide encryption endpoint now.
     */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean ENCRYPTION_KEY_PERIODIC_FETCH_KILL_SWITCH = true;

    /** Returns value of encryption key new enrollment fetch job kill switch */
    default boolean getEncryptionKeyPeriodicFetchKillSwitch() {
        return getGlobalKillSwitch() || ENCRYPTION_KEY_PERIODIC_FETCH_KILL_SWITCH;
    }

    int ENCRYPTION_KEY_JOB_REQUIRED_NETWORK_TYPE = JobInfo.NETWORK_TYPE_UNMETERED;

    /** Returns the required network type (Wifi) for encryption key fetch job. */
    default int getEncryptionKeyJobRequiredNetworkType() {
        return ENCRYPTION_KEY_JOB_REQUIRED_NETWORK_TYPE;
    }

    /* The default time period (in millisecond) between each encryption key job to run. */
    long ENCRYPTION_KEY_JOB_PERIOD_MS = 24 * 60 * 60 * 1000L; // 24 hours.

    /** Returns min time period (in millis) between each event fallback reporting job run. */
    default long getEncryptionKeyJobPeriodMs() {
        return ENCRYPTION_KEY_JOB_PERIOD_MS;
    }

    /** Feature flag to ramp up mdd based encryption keys. */
    boolean ENABLE_MDD_ENCRYPTION_KEYS = false;

    /** Returns value of the feature flag used to determine ramp for mdd based encryption keys. */
    default boolean getEnableMddEncryptionKeys() {
        return ENABLE_MDD_ENCRYPTION_KEYS;
    }

    /** Manifest URL for encryption keys file group registered with MDD. */
    String MDD_ENCRYPTION_KEYS_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-encryption-keys/4543"
                    + "/e9d118728752e6a6bfb5d7d8d1520807591f0717";

    /** Returns manifest URL for encryption keys file group registered with MDD. */
    default String getMddEncryptionKeysManifestFileUrl() {
        return MDD_ENCRYPTION_KEYS_MANIFEST_FILE_URL;
    }

    /**
     * Enable Back Compat feature flag. The default value is false which means that all back compat
     * related features are disabled by default. This flag would be enabled for R/S during rollout.
     */
    @FeatureFlag boolean ENABLE_BACK_COMPAT = false;

    /** Returns value of enable Back Compat */
    default boolean getEnableBackCompat() {
        return ENABLE_BACK_COMPAT;
    }

    /**
     * Enable Back Compat feature init flag. When enabled, the back compat feature is initialized
     * (if it hasn't been initialized already) within the enableAdServices system API.
     */
    @FeatureFlag boolean DEFAULT_ENABLE_BACK_COMPAT_INIT = false;

    /** Returns value of enable Back Compat */
    default boolean getEnableBackCompatInit() {
        return DEFAULT_ENABLE_BACK_COMPAT_INIT;
    }

    /**
     * Enable AppSearch read for consent data feature flag. The default value is false which means
     * AppSearch is not considered as source of truth after OTA. This flag should be enabled for OTA
     * support of consent data on T+ devices.
     */
    boolean ENABLE_APPSEARCH_CONSENT_DATA = SdkLevel.isAtLeastS() && !SdkLevel.isAtLeastT();

    /** Returns value of enable appsearch consent data flag */
    default boolean getEnableAppsearchConsentData() {
        return ENABLE_APPSEARCH_CONSENT_DATA;
    }

    /** Default U18 AppSearch migration feature flag. */
    boolean DEFAULT_ENABLE_U18_APPSEARCH_MIGRATION = false;

    /** Returns value of enable U18 appsearch migration flag */
    default boolean getEnableU18AppsearchMigration() {
        return DEFAULT_ENABLE_U18_APPSEARCH_MIGRATION;
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
                    + "com.android.sdksandboxclient,"
                    + "com.example.adservices.samples.adid.app,"
                    + "com.example.adservices.samples.appsetid.app,"
                    + "com.example.adservices.samples.fledge.sampleapp,"
                    + "com.example.adservices.samples.fledge.sampleapp1,"
                    + "com.example.adservices.samples.fledge.sampleapp2,"
                    + "com.example.adservices.samples.fledge.sampleapp3,"
                    + "com.example.adservices.samples.fledge.sampleapp4,"
                    + "com.example.adservices.samples.signals.sampleapp,"
                    + "com.example.measurement.sampleapp,"
                    + "com.example.measurement.sampleapp2";

    /**
     * Returns bypass List for PPAPI app signature check. Apps with package name on this list will
     * bypass the signature check
     */
    default String getPpapiAppAllowList() {
        return PPAPI_APP_ALLOW_LIST;
    }

    default String getPasAppAllowList() {
        // default to using the same fixed list as custom audiences
        return PPAPI_APP_ALLOW_LIST;
    }

    String AD_ID_API_APP_BLOCK_LIST = "";

    /** Get the app allow list for the AD ID API. */
    default String getAdIdApiAppBlockList() {
        return AD_ID_API_APP_BLOCK_LIST;
    }

    /*
     * The allow-list for Measurement APIs. This list has the list of app package names that we
     * allow using Measurement APIs. Overridden by Block List
     */
    String MSMT_API_APP_ALLOW_LIST =
            "android.platform.test.scenario,"
                    + "android.adservices.crystalball,"
                    + "com.android.sdksandboxclient,"
                    + "com.example.adservices.samples.adid.app,"
                    + "com.example.adservices.samples.appsetid.app,"
                    + "com.example.adservices.samples.fledge.sampleapp,"
                    + "com.example.adservices.samples.fledge.sampleapp1,"
                    + "com.example.adservices.samples.fledge.sampleapp2,"
                    + "com.example.adservices.samples.fledge.sampleapp3,"
                    + "com.example.adservices.samples.fledge.sampleapp4,"
                    + "com.example.measurement.sampleapp,"
                    + "com.example.measurement.sampleapp2";

    /*
     * App Package Name that does not belong to this allow-list will not be able to use Measurement
     * APIs.
     * If this list has special value "*", then all package names are allowed.
     * Block List takes precedence over Allow List.
     * There must be not any empty space between comma.
     */
    default String getMsmtApiAppAllowList() {
        return MSMT_API_APP_ALLOW_LIST;
    }

    /*
     * The blocklist for Measurement APIs. This list has the list of app package names that we
     * do not allow to use Measurement APIs.
     */
    String MSMT_API_APP_BLOCK_LIST = "";

    /*
     * App Package Name that belong to this blocklist will not be able to use Measurement
     * APIs.
     * If this list has special value "*", then all package names are blocked.
     * Block List takes precedence over Allow List.
     * There must be not any empty space between comma.
     */
    default String getMsmtApiAppBlockList() {
        return MSMT_API_APP_BLOCK_LIST;
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
     * The allow list for AppSearch writers. If non-empty, only results written by a package on the
     * allow list will be read for consent migration.
     */
    String APPSEARCH_WRITER_ALLOW_LIST_OVERRIDE = "";

    /** Only data written by packages in the allow list will be read from AppSearch. */
    default String getAppsearchWriterAllowListOverride() {
        return APPSEARCH_WRITER_ALLOW_LIST_OVERRIDE;
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
    float ADID_REQUEST_PERMITS_PER_SECOND = FlagsConstants.ADID_REQUEST_PERMITS_PER_SECOND;

    /**
     * PP API Rate Limit for app set id. This is the max allowed QPS for one API client to one PP
     * API. Negative Value means skipping the rate limiting checking.
     */
    float APPSETID_REQUEST_PERMITS_PER_SECOND = 5;

    /**
     * PP API Rate Limit for measurement register source. This is the max allowed QPS for one API
     * client to one PP API. Negative Value means skipping the rate limiting checking.
     */
    float MEASUREMENT_REGISTER_SOURCE_REQUEST_PERMITS_PER_SECOND = 25;

    /**
     * PP API Rate Limit for measurement register web source. This is the max allowed QPS for one
     * API client to one PP API. Negative Value means skipping the rate limiting checking.
     */
    float MEASUREMENT_REGISTER_WEB_SOURCE_REQUEST_PERMITS_PER_SECOND = 25;

    /**
     * PP API Rate Limit for measurement register sources. This is the max allowed QPS for one API
     * client to one PP API. Negative Value means skipping the rate limiting checking.
     */
    float MEASUREMENT_REGISTER_SOURCES_REQUEST_PERMITS_PER_SECOND = 25;

    /**
     * PP API Rate Limit for measurement register trigger. This is the max allowed QPS for one API
     * client to one PP API. Negative Value means skipping the rate limiting checking.
     */
    float MEASUREMENT_REGISTER_TRIGGER_REQUEST_PERMITS_PER_SECOND = 25;

    /**
     * PP API Rate Limit for measurement register web trigger. This is the max allowed QPS for one
     * API client to one PP API. Negative Value means skipping the rate limiting checking.
     */
    float MEASUREMENT_REGISTER_WEB_TRIGGER_REQUEST_PERMITS_PER_SECOND = 25;

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

    /*
     * PP API Rate Limits for Protected Audience/Protected Signals APIs. These are the max allowed
     * QPS for an app to call a single API. A negative value means skipping the rate limiting
     * check.
     */

    @ConfigFlag float FLEDGE_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND = 1;
    @ConfigFlag float FLEDGE_FETCH_AND_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND = 1;
    @ConfigFlag float FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_REQUEST_PERMITS_PER_SECOND = 1;
    @ConfigFlag float FLEDGE_LEAVE_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND = 1;
    @ConfigFlag float FLEDGE_UPDATE_SIGNALS_REQUEST_PERMITS_PER_SECOND = 1;
    @ConfigFlag float FLEDGE_SELECT_ADS_REQUEST_PERMITS_PER_SECOND = 1;
    @ConfigFlag float FLEDGE_SELECT_ADS_WITH_OUTCOMES_REQUEST_PERMITS_PER_SECOND = 1;
    @ConfigFlag float FLEDGE_GET_AD_SELECTION_DATA_REQUEST_PERMITS_PER_SECOND = 1;
    @ConfigFlag float FLEDGE_PERSIST_AD_SELECTION_RESULT_REQUEST_PERMITS_PER_SECOND = 1;
    @ConfigFlag float FLEDGE_REPORT_IMPRESSION_REQUEST_PERMITS_PER_SECOND = 1;
    float FLEDGE_REPORT_INTERACTION_REQUEST_PERMITS_PER_SECOND = 1;
    @ConfigFlag float FLEDGE_SET_APP_INSTALL_ADVERTISERS_REQUEST_PERMITS_PER_SECOND = 1;
    @ConfigFlag float FLEDGE_UPDATE_AD_COUNTER_HISTOGRAM_REQUEST_PERMITS_PER_SECOND = 1;

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

    /** Returns the Measurement Register Sources Request Permits Per Second. */
    default float getMeasurementRegisterSourcesRequestPermitsPerSecond() {
        return MEASUREMENT_REGISTER_SOURCES_REQUEST_PERMITS_PER_SECOND;
    }

    /** Returns the Measurement Register Web Source Request Permits Per Second. */
    default float getMeasurementRegisterWebSourceRequestPermitsPerSecond() {
        return MEASUREMENT_REGISTER_WEB_SOURCE_REQUEST_PERMITS_PER_SECOND;
    }

    /** Returns the Measurement Register Trigger Request Permits Per Second. */
    default float getMeasurementRegisterTriggerRequestPermitsPerSecond() {
        return MEASUREMENT_REGISTER_TRIGGER_REQUEST_PERMITS_PER_SECOND;
    }

    /** Returns the Measurement Register Web Trigger Request Permits Per Second. */
    default float getMeasurementRegisterWebTriggerRequestPermitsPerSecond() {
        return MEASUREMENT_REGISTER_WEB_TRIGGER_REQUEST_PERMITS_PER_SECOND;
    }

    /** Returns the Protected Audience joinCustomAudience() API max request permits per second. */
    default float getFledgeJoinCustomAudienceRequestPermitsPerSecond() {
        return FLEDGE_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND;
    }

    /**
     * Returns the Protected Audience fetchAndJoinCustomAudience() API max request permits per
     * second.
     */
    default float getFledgeFetchAndJoinCustomAudienceRequestPermitsPerSecond() {
        return FLEDGE_FETCH_AND_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND;
    }

    /**
     * Returns the Protected Audience scheduleCustomAudienceUpdate() API max request permits per
     * second.
     */
    default float getFledgeScheduleCustomAudienceUpdateRequestPermitsPerSecond() {
        return FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_REQUEST_PERMITS_PER_SECOND;
    }

    /** Returns the Protected Audience leaveCustomAudience() API max request permits per second. */
    default float getFledgeLeaveCustomAudienceRequestPermitsPerSecond() {
        return FLEDGE_LEAVE_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND;
    }

    /** Returns the Protected Signals updateSignals() API max request permits per second. */
    default float getFledgeUpdateSignalsRequestPermitsPerSecond() {
        return FLEDGE_UPDATE_SIGNALS_REQUEST_PERMITS_PER_SECOND;
    }

    /** Returns the Protected Audience selectAds() API max request permits per second. */
    default float getFledgeSelectAdsRequestPermitsPerSecond() {
        return FLEDGE_SELECT_ADS_REQUEST_PERMITS_PER_SECOND;
    }

    /**
     * Returns the Protected Audience selectAds() with outcomes API max request permits per second.
     */
    default float getFledgeSelectAdsWithOutcomesRequestPermitsPerSecond() {
        return FLEDGE_SELECT_ADS_WITH_OUTCOMES_REQUEST_PERMITS_PER_SECOND;
    }

    /** Returns the Protected Audience getAdSelectionData() API max request permits per second. */
    default float getFledgeGetAdSelectionDataRequestPermitsPerSecond() {
        return FLEDGE_GET_AD_SELECTION_DATA_REQUEST_PERMITS_PER_SECOND;
    }

    /**
     * Returns the Protected Audience persistAdSelectionResult() API max request permits per second.
     */
    default float getFledgePersistAdSelectionResultRequestPermitsPerSecond() {
        return FLEDGE_PERSIST_AD_SELECTION_RESULT_REQUEST_PERMITS_PER_SECOND;
    }

    /** Returns the Protected Audience reportImpression() API max request permits per second. */
    default float getFledgeReportImpressionRequestPermitsPerSecond() {
        return FLEDGE_REPORT_IMPRESSION_REQUEST_PERMITS_PER_SECOND;
    }

    /** Returns the Protected Audience reportEvent() API max request permits per second. */
    default float getFledgeReportInteractionRequestPermitsPerSecond() {
        return FLEDGE_REPORT_INTERACTION_REQUEST_PERMITS_PER_SECOND;
    }

    /**
     * Returns the Protected Audience setAppInstallAdvertisers() API max request permits per second.
     */
    default float getFledgeSetAppInstallAdvertisersRequestPermitsPerSecond() {
        return FLEDGE_SET_APP_INSTALL_ADVERTISERS_REQUEST_PERMITS_PER_SECOND;
    }

    /**
     * Returns the Protected Audience updateAdCounterHistogram() API max request permits per second.
     */
    default float getFledgeUpdateAdCounterHistogramRequestPermitsPerSecond() {
        return FLEDGE_UPDATE_AD_COUNTER_HISTOGRAM_REQUEST_PERMITS_PER_SECOND;
    }

    // Flags for ad tech enrollment enforcement

    boolean DISABLE_TOPICS_ENROLLMENT_CHECK = false;
    boolean DISABLE_FLEDGE_ENROLLMENT_CHECK = false;
    boolean DISABLE_MEASUREMENT_ENROLLMENT_CHECK = false;
    boolean ENABLE_ENROLLMENT_TEST_SEED = false;

    /** Returns {@code true} if the Topics API should disable the ad tech enrollment check */
    default boolean isDisableTopicsEnrollmentCheck() {
        return DISABLE_TOPICS_ENROLLMENT_CHECK;
    }

    /** Returns {@code true} if the FLEDGE APIs should disable the ad tech enrollment check */
    default boolean getDisableFledgeEnrollmentCheck() {
        return DISABLE_FLEDGE_ENROLLMENT_CHECK;
    }

    /** Returns {@code true} if the Measurement APIs should disable the ad tech enrollment check */
    default boolean isDisableMeasurementEnrollmentCheck() {
        return DISABLE_MEASUREMENT_ENROLLMENT_CHECK;
    }

    /**
     * Returns {@code true} if the Enrollment seed is disabled. (Enrollment seed is only needed for
     * testing)
     */
    default boolean isEnableEnrollmentTestSeed() {
        return ENABLE_ENROLLMENT_TEST_SEED;
    }

    boolean ENFORCE_FOREGROUND_STATUS_ADID = true;
    boolean ENFORCE_FOREGROUND_STATUS_APPSETID = true;
    boolean ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION = true;
    boolean ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION = true;
    boolean ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_INTERACTION = true;
    boolean ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDES = true;
    boolean ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE = true;
    @ConfigFlag boolean ENFORCE_FOREGROUND_STATUS_FETCH_AND_JOIN_CUSTOM_AUDIENCE = true;
    @ConfigFlag boolean ENFORCE_FOREGROUND_STATUS_LEAVE_CUSTOM_AUDIENCE = true;
    @ConfigFlag boolean ENFORCE_FOREGROUND_STATUS_SCHEDULE_CUSTOM_AUDIENCE = true;
    boolean ENFORCE_FOREGROUND_STATUS_TOPICS = true;
    boolean ENFORCE_FOREGROUND_STATUS_SIGNALS = true;

    /**
     * Returns true if FLEDGE runAdSelection API should require that the caller is running in
     * foreground.
     */
    default boolean getEnforceForegroundStatusForFledgeRunAdSelection() {
        return ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION;
    }

    /**
     * Returns true if FLEDGE reportImpression API should require that the caller is running in
     * foreground.
     */
    default boolean getEnforceForegroundStatusForFledgeReportImpression() {
        return ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION;
    }

    /**
     * Returns true if FLEDGE reportInteraction API should require that the caller is running in
     * foreground.
     */
    default boolean getEnforceForegroundStatusForFledgeReportInteraction() {
        return ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_INTERACTION;
    }

    /**
     * Returns true if FLEDGE override API methods (for Custom Audience and Ad Selection) should
     * require that the caller is running in foreground.
     */
    default boolean getEnforceForegroundStatusForFledgeOverrides() {
        return ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDES;
    }

    /**
     * Returns true if FLEDGE Custom Audience API methods should require that the caller is running
     * in foreground.
     */
    default boolean getEnforceForegroundStatusForFledgeCustomAudience() {
        return ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE;
    }

    /**
     * Returns true if FetchAndJoin Custom Audience API should require that the caller is running in
     * foreground.
     */
    default boolean getEnforceForegroundStatusForFetchAndJoinCustomAudience() {
        return ENFORCE_FOREGROUND_STATUS_FETCH_AND_JOIN_CUSTOM_AUDIENCE;
    }

    /**
     * Returns true if Leave Custom Audience API should require that the caller is running in
     * foreground.
     */
    default boolean getEnforceForegroundStatusForLeaveCustomAudience() {
        return ENFORCE_FOREGROUND_STATUS_LEAVE_CUSTOM_AUDIENCE;
    }

    /**
     * Returns true if Schedule Custom Audience API should require that the caller is running in
     * foreground.
     */
    default boolean getEnforceForegroundStatusForScheduleCustomAudience() {
        return ENFORCE_FOREGROUND_STATUS_SCHEDULE_CUSTOM_AUDIENCE;
    }

    boolean MEASUREMENT_ENFORCE_FOREGROUND_STATUS_DELETE_REGISTRATIONS = true;
    boolean MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCE = true;
    boolean MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_TRIGGER = false;
    boolean MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_SOURCE = true;
    boolean MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_TRIGGER = false;
    boolean MEASUREMENT_ENFORCE_FOREGROUND_STATUS_GET_STATUS = false;
    boolean MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCES = true;

    /**
     * Returns true if Measurement Delete Registrations API should require that the calling API is
     * running in foreground.
     */
    default boolean getEnforceForegroundStatusForMeasurementDeleteRegistrations() {
        return MEASUREMENT_ENFORCE_FOREGROUND_STATUS_DELETE_REGISTRATIONS;
    }

    /**
     * Returns true if Measurement Register Source API should require that the calling API is
     * running in foreground.
     */
    default boolean getEnforceForegroundStatusForMeasurementRegisterSource() {
        return MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCE;
    }

    /**
     * Returns true if Measurement Register Trigger API should require that the calling API is
     * running in foreground.
     */
    default boolean getEnforceForegroundStatusForMeasurementRegisterTrigger() {
        return MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_TRIGGER;
    }

    /**
     * Returns true if Measurement Register Web Source API should require that the calling API is
     * running in foreground.
     */
    default boolean getEnforceForegroundStatusForMeasurementRegisterWebSource() {
        return MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_SOURCE;
    }

    /**
     * Returns true if Measurement Register Web Trigger API should require that the calling API is
     * running in foreground.
     */
    default boolean getEnforceForegroundStatusForMeasurementRegisterWebTrigger() {
        return MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_TRIGGER;
    }

    /**
     * Returns true if Measurement Get Status API should require that the calling API is running in
     * foreground.
     */
    default boolean getEnforceForegroundStatusForMeasurementStatus() {
        return MEASUREMENT_ENFORCE_FOREGROUND_STATUS_GET_STATUS;
    }

    /**
     * Returns true if Measurement Get Status API should require that the calling API is running in
     * foreground.
     */
    default boolean getEnforceForegroundStatusForMeasurementRegisterSources() {
        return MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCES;
    }

    /** Returns true if Topics API should require that the calling API is running in foreground. */
    default boolean getEnforceForegroundStatusForTopics() {
        return ENFORCE_FOREGROUND_STATUS_TOPICS;
    }

    /**
     * Returns true if Protected Signals API should require that the calling API is running in
     * foreground.
     */
    default boolean getEnforceForegroundStatusForSignals() {
        return ENFORCE_FOREGROUND_STATUS_SIGNALS;
    }

    /** Returns true if AdId API should require that the calling API is running in foreground. */
    default boolean getEnforceForegroundStatusForAdId() {
        return ENFORCE_FOREGROUND_STATUS_ADID;
    }

    int FOREGROUND_STATUS_LEVEL = IMPORTANCE_FOREGROUND_SERVICE;

    /**
     * Returns true if AppSetId API should require that the calling API is running in foreground.
     */
    default boolean getEnforceForegroundStatusForAppSetId() {
        return ENFORCE_FOREGROUND_STATUS_APPSETID;
    }

    /** Returns the importance level to use to check if an application is in foreground. */
    default int getForegroundStatuslLevelForValidation() {
        return FOREGROUND_STATUS_LEVEL;
    }

    default String getWebContextClientAppAllowList() {
        return WEB_CONTEXT_CLIENT_ALLOW_LIST;
    }

    long ISOLATE_MAX_HEAP_SIZE_BYTES = 10 * 1024 * 1024L; // 10 MB
    long MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES = 16 * 1024; // 16 kB
    long MAX_TRIGGER_REGISTRATION_HEADER_SIZE_BYTES = 250 * 1024; // 250 kB
    long MAX_ODP_TRIGGER_REGISTRATION_HEADER_SIZE_BYTES = 16 * 1024; // 16 kB

    /** Returns max allowed size in bytes for trigger registrations header. */
    default long getMaxTriggerRegistrationHeaderSizeBytes() {
        return MAX_TRIGGER_REGISTRATION_HEADER_SIZE_BYTES;
    }

    /** Returns max allowed size in bytes for ODP trigger registrations header. */
    default long getMaxOdpTriggerRegistrationHeaderSizeBytes() {
        return MAX_ODP_TRIGGER_REGISTRATION_HEADER_SIZE_BYTES;
    }

    boolean MEASUREMENT_ENABLE_UPDATE_TRIGGER_REGISTRATION_HEADER_LIMIT = false;

    /** Returns true when the new trigger registration header size limitation are applied. */
    default boolean getMeasurementEnableUpdateTriggerHeaderLimit() {
        return MEASUREMENT_ENABLE_UPDATE_TRIGGER_REGISTRATION_HEADER_LIMIT;
    }

    /** Returns size in bytes we bound the heap memory for JavaScript isolate */
    default long getIsolateMaxHeapSizeBytes() {
        return ISOLATE_MAX_HEAP_SIZE_BYTES;
    }

    /**
     * Returns max allowed size in bytes for response based registrations payload of an individual
     * source/trigger registration.
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
    String UI_OTA_STRINGS_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-ui-ota-strings/1341"
                    + "/95580b00edbd8cbf62bfa0df9ebd79fba1e5b7ca";

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

    /** UI OTA resources manifest file url, used for MDD download. */
    String UI_OTA_RESOURCES_MANIFEST_FILE_URL = "";

    /** UI OTA resources manifest file url. */
    default String getUiOtaResourcesManifestFileUrl() {
        return UI_OTA_RESOURCES_MANIFEST_FILE_URL;
    }

    /** UI OTA resources feature flag. */
    boolean UI_OTA_RESOURCES_FEATURE_ENABLED = false;

    /** Returns if UI OTA resources feature is enabled. */
    default boolean getUiOtaResourcesFeatureEnabled() {
        return UI_OTA_RESOURCES_FEATURE_ENABLED;
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
    default boolean getUiDialogsFeatureEnabled() {
        return UI_DIALOGS_FEATURE_ENABLED;
    }

    /** UI Dialog Fragment feature enabled. */
    boolean UI_DIALOG_FRAGMENT = false;

    /** Returns if the UI Dialog Fragment is enabled. */
    default boolean getUiDialogFragmentEnabled() {
        return UI_DIALOG_FRAGMENT;
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

    /** Default is that the manual interaction feature is enabled. */
    boolean RECORD_MANUAL_INTERACTION_ENABLED = true;

    /** Returns if the manual interaction feature is enabled. */
    default boolean getRecordManualInteractionEnabled() {
        return RECORD_MANUAL_INTERACTION_ENABLED;
    }

    /** Default is that the notification should be dismissed on click. */
    boolean DEFAULT_NOTIFICATION_DISMISSED_ON_CLICK = true;

    /** Determines whether the notification should be dismissed on click. */
    default boolean getNotificationDismissedOnClick() {
        return DEFAULT_NOTIFICATION_DISMISSED_ON_CLICK;
    }

    /**
     * The check activity feature is off by default. When enabled, we check whether all Rubidium
     * activities are enabled when we determine whether AdServices is enabled
     */
    boolean IS_BACK_COMPACT_ACTIVITY_FEATURE_ENABLED = false;

    /** Returns if the check activity feature has been enabled. */
    default boolean isBackCompatActivityFeatureEnabled() {
        return IS_BACK_COMPACT_ACTIVITY_FEATURE_ENABLED;
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
     *
     * This flag is set default to true as beta deprecated.
     */
    boolean GA_UX_FEATURE_ENABLED = true;

    /** Returns if the GA UX feature is enabled. */
    default boolean getGaUxFeatureEnabled() {
        return GA_UX_FEATURE_ENABLED;
    }

    /** Set the debug UX, which should correspond to the {@link PrivacySandboxUxCollection} enum. */
    String DEBUG_UX = "UNSUPPORTED_UX";

    /** Returns the debug UX. */
    default String getDebugUx() {
        return DEBUG_UX;
    }

    /** add speed bump dialogs when turning on or off the toggle of Topics, apps, measurement */
    boolean TOGGLE_SPEED_BUMP_ENABLED = false;

    /** Returns if the toggle speed bump dialog feature is enabled. */
    default boolean getToggleSpeedBumpEnabled() {
        return TOGGLE_SPEED_BUMP_ENABLED;
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
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Registration Job Queue. The job will be disabled if either
     * the Global Kill Switch, Measurement Kill Switch, or the Registration Job Queue Kill Switch
     * value is true.
     */
    default boolean getAsyncRegistrationJobQueueKillSwitch() {
        return getLegacyMeasurementKillSwitch() || MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH;
    }

    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_REGISTRATION_FALLBACK_JOB_KILL_SWITCH = false;

    /**
     * Returns the kill switch value for Registration Fallback Job. The Job will be disabled if
     * either the Global Kill Switch, Measurement Kill Switch, or the Registration Fallback Job Kill
     * Switch value is true.
     */
    default boolean getAsyncRegistrationFallbackJobKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || MEASUREMENT_REGISTRATION_FALLBACK_JOB_KILL_SWITCH;
    }

    /** Returns true if the given enrollmentId is blocked from using PP-API. */
    default boolean isEnrollmentBlocklisted(String enrollmentId) {
        return false;
    }

    /** Returns a list of enrollmentId blocked from using PP-API. */
    default ImmutableList<String> getEnrollmentBlocklist() {
        return ImmutableList.of();
    }

    long DEFAULT_MEASUREMENT_DEBUG_JOIN_KEY_HASH_LIMIT = 100L;

    /** Returns debug keys hash limit. */
    default long getMeasurementDebugJoinKeyHashLimit() {
        return DEFAULT_MEASUREMENT_DEBUG_JOIN_KEY_HASH_LIMIT;
    }

    /** Returns the limit to the number of unique AdIDs attempted to match for debug keys. */
    long DEFAULT_MEASUREMENT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT = 5L;

    default long getMeasurementPlatformDebugAdIdMatchingLimit() {
        return DEFAULT_MEASUREMENT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT;
    }

    /** Kill switch to guard backward-compatible logging. See go/rbc-ww-logging */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean COMPAT_LOGGING_KILL_SWITCH = false;

    /** Returns true if backward-compatible logging should be disabled; false otherwise. */
    default boolean getCompatLoggingKillSwitch() {
        return COMPAT_LOGGING_KILL_SWITCH;
    }

    /** Kill switch to guard background jobs logging. */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean BACKGROUND_JOBS_LOGGING_KILL_SWITCH = true;

    /** Returns true if background jobs logging should be disabled; false otherwise */
    default boolean getBackgroundJobsLoggingKillSwitch() {
        return BACKGROUND_JOBS_LOGGING_KILL_SWITCH;
    }

    // New Feature Flags
    boolean FLEDGE_REGISTER_AD_BEACON_ENABLED = false;
    boolean FLEDGE_CPC_BILLING_ENABLED = false;
    boolean FLEDGE_DATA_VERSION_HEADER_ENABLED = false;

    /** Returns whether the {@code registerAdBeacon} feature is enabled. */
    default boolean getFledgeRegisterAdBeaconEnabled() {
        return FLEDGE_REGISTER_AD_BEACON_ENABLED;
    }

    /** Returns whether the CPC billing feature is enabled. */
    default boolean getFledgeCpcBillingEnabled() {
        return FLEDGE_CPC_BILLING_ENABLED;
    }

    /** Returns whether the data version header feature is enabled. */
    default boolean getFledgeDataVersionHeaderEnabled() {
        return FLEDGE_DATA_VERSION_HEADER_ENABLED;
    }

    // New fledge beacon reporting metrics flag.
    boolean FLEDGE_BEACON_REPORTING_METRICS_ENABLED = false;

    /**
     * Returns whether the fledge beacon reporting metrics is enabled. This flag should not be
     * ramped on S- prior to M-2024-04.
     */
    default boolean getFledgeBeaconReportingMetricsEnabled() {
        return getFledgeRegisterAdBeaconEnabled() && FLEDGE_BEACON_REPORTING_METRICS_ENABLED;
    }

    // Fledge auction server API usage metrics flag.
    boolean FLEDGE_AUCTION_SERVER_API_USAGE_METRICS_ENABLED = false;

    /** Returns whether the fledge B&A API usage metrics is enabled */
    default boolean getFledgeAuctionServerApiUsageMetricsEnabled() {
        return getFledgeAuctionServerEnabled() && FLEDGE_AUCTION_SERVER_API_USAGE_METRICS_ENABLED;
    }

    // Fledge key fetch metrics flag.
    boolean FLEDGE_AUCTION_SERVER_KEY_FETCH_METRICS_ENABLED = false;

    /** Returns whether the fledge auction server key fetch metrics feature is enabled */
    default boolean getFledgeAuctionServerKeyFetchMetricsEnabled() {
        return getFledgeAuctionServerEnabled() && FLEDGE_AUCTION_SERVER_KEY_FETCH_METRICS_ENABLED;
    }

    // Fledge select ads from outcomes API metrics flag.
    @FeatureFlag boolean FLEDGE_SELECT_ADS_FROM_OUTCOMES_API_METRICS_ENABLED = false;

    /** Returns whether the fledge select ads from outcomes API metrics feature is enabled */
    default boolean getFledgeSelectAdsFromOutcomesApiMetricsEnabled() {
        return FLEDGE_SELECT_ADS_FROM_OUTCOMES_API_METRICS_ENABLED;
    }

    // Fledge CPC billing metrics flag.
    @FeatureFlag boolean FLEDGE_CPC_BILLING_METRICS_ENABLED = false;

    /** Returns whether the FLEDGE CPC billing metrics feature is enabled. */
    default boolean getFledgeCpcBillingMetricsEnabled() {
        return FLEDGE_CPC_BILLING_METRICS_ENABLED;
    }

    // Fledge data version header metrics flag.
    @FeatureFlag boolean FLEDGE_DATA_VERSION_HEADER_METRICS_ENABLED = false;

    /** Returns whether the FLEDGE data version header metrics feature is enabled. */
    default boolean getFledgeDataVersionHeaderMetricsEnabled() {
        return FLEDGE_DATA_VERSION_HEADER_METRICS_ENABLED;
    }

    // Fledge report impression API metrics flag.
    @FeatureFlag boolean FLEDGE_REPORT_IMPRESSION_API_METRICS_ENABLED = false;

    /** Returns whether the FLEDGE report impression API metrics feature is enabled. */
    default boolean getFledgeReportImpressionApiMetricsEnabled() {
        return FLEDGE_REPORT_IMPRESSION_API_METRICS_ENABLED;
    }

    // Fledge JS script result code metrics flag.
    @FeatureFlag boolean FLEDGE_JS_SCRIPT_RESULT_CODE_METRICS_ENABLED = false;

    /** Returns whether the FLEDGE JS script result code metrics feature is enabled. */
    default boolean getFledgeJsScriptResultCodeMetricsEnabled() {
        return FLEDGE_JS_SCRIPT_RESULT_CODE_METRICS_ENABLED;
    }

    /**
     * Default allowlist of the enrollments for whom debug key insertion based on join key matching
     * is allowed.
     */
    String DEFAULT_MEASUREMENT_DEBUG_JOIN_KEY_ENROLLMENT_ALLOWLIST = "";

    /**
     * Allowlist of the enrollments for whom debug key insertion based on join key matching is
     * allowed.
     */
    default String getMeasurementDebugJoinKeyEnrollmentAllowlist() {
        return DEFAULT_MEASUREMENT_DEBUG_JOIN_KEY_ENROLLMENT_ALLOWLIST;
    }

    /**
     * Default blocklist of the enrollments for whom debug key insertion based on AdID matching is
     * blocked.
     */
    String DEFAULT_MEASUREMENT_PLATFORM_DEBUG_AD_ID_MATCHING_BLOCKLIST = "*";

    /**
     * Blocklist of the enrollments for whom debug key insertion based on AdID matching is blocked.
     */
    default String getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist() {
        return DEFAULT_MEASUREMENT_PLATFORM_DEBUG_AD_ID_MATCHING_BLOCKLIST;
    }

    /** Default computation of adservices version */
    boolean DEFAULT_COMPUTE_VERSION_FROM_MAPPINGS_ENABLED = true;

    /** Get Compute adservices Version from mappings */
    default boolean getEnableComputeVersionFromMappings() {
        return DEFAULT_COMPUTE_VERSION_FROM_MAPPINGS_ENABLED;
    }

    /** Get mainline train version */
    String DEFAULT_MAINLINE_TRAIN_VERSION = "000000";

    /** Get mainline train version */
    default String getMainlineTrainVersion() {
        return DEFAULT_MAINLINE_TRAIN_VERSION;
    }

    /**
     * Default adservices version mappings Format -
     * start_range1,end_range1,header_version1|start_range2,end_range2,header_version2
     */
    String DEFAULT_ADSERVICES_VERSION_MAPPINGS =
            "341300000,341400000,202401|341400000,341500000,202402"
                    + "|341500000,341600000,202403|341600000,341700000,202404"
                    + "|341700000,341800000,202405|341800000,341900000,202406"
                    + "|341900000,342000000,202407|350800000,350900000,202408"
                    + "|350900000,351000000,202409|351000000,351100000,202410"
                    + "|351100000,351200000,202411|351200000,351300000,202412"
                    + "|351300000,351400000,202501|351400000,351500000,202502"
                    + "|351500000,351600000,202503|351600000,351700000,202504"
                    + "|351700000,351800000,202505|351800000,351900000,202506";

    /** Get adservices version mappings */
    default String getAdservicesVersionMappings() {
        return DEFAULT_ADSERVICES_VERSION_MAPPINGS;
    }

    /** Default value for Measurement aggregatable named budgets */
    @FeatureFlag boolean MEASUREMENT_ENABLE_AGGREGATABLE_NAMED_BUDGETS = false;

    /** Returns whether to enable Measurement aggregatable named budgets */
    default boolean getMeasurementEnableAggregatableNamedBudgets() {
        return MEASUREMENT_ENABLE_AGGREGATABLE_NAMED_BUDGETS;
    }

    /** Default value for Measurement V1 source trigger data */
    @FeatureFlag boolean MEASUREMENT_ENABLE_V1_SOURCE_TRIGGER_DATA = false;

    /** Returns whether to enable Measurement V1 source trigger data */
    default boolean getMeasurementEnableV1SourceTriggerData() {
        return MEASUREMENT_ENABLE_V1_SOURCE_TRIGGER_DATA;
    }

    /** Default value for Measurement flexible event reporting API */
    boolean MEASUREMENT_FLEXIBLE_EVENT_REPORTING_API_ENABLED = false;

    /** Returns whether to enable Measurement flexible event reporting API */
    default boolean getMeasurementFlexibleEventReportingApiEnabled() {
        return MEASUREMENT_FLEXIBLE_EVENT_REPORTING_API_ENABLED;
    }

    /** Default value for Measurement trigger data matching */
    boolean MEASUREMENT_ENABLE_TRIGGER_DATA_MATCHING = true;

    /** Returns whether to enable Measurement trigger data matching */
    default boolean getMeasurementEnableTriggerDataMatching() {
        return MEASUREMENT_ENABLE_TRIGGER_DATA_MATCHING;
    }

    /** Default maximum sources per publisher */
    int MEASUREMENT_MAX_SOURCES_PER_PUBLISHER = 4096;

    /** Returns maximum sources per publisher */
    default int getMeasurementMaxSourcesPerPublisher() {
        return MEASUREMENT_MAX_SOURCES_PER_PUBLISHER;
    }

    /** Default maximum triggers per destination */
    int MEASUREMENT_MAX_TRIGGERS_PER_DESTINATION = 1024;

    /** Returns maximum triggers per destination */
    default int getMeasurementMaxTriggersPerDestination() {
        return MEASUREMENT_MAX_TRIGGERS_PER_DESTINATION;
    }

    /** Default maximum Aggregate Reports per destination */
    int MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_DESTINATION = 1024;

    /** Returns maximum Aggregate Reports per publisher */
    default int getMeasurementMaxAggregateReportsPerDestination() {
        return MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_DESTINATION;
    }

    /** Default maximum Event Reports per destination */
    int MEASUREMENT_MAX_EVENT_REPORTS_PER_DESTINATION = 1024;

    /** Returns maximum Event Reports per destination */
    default int getMeasurementMaxEventReportsPerDestination() {
        return MEASUREMENT_MAX_EVENT_REPORTS_PER_DESTINATION;
    }

    /** Maximum Aggregate Reports per source. */
    int MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_SOURCE = 20;

    /** Returns maximum Aggregate Reports per source. */
    default int getMeasurementMaxAggregateReportsPerSource() {
        return MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_SOURCE;
    }

    /** Maximum number of aggregation keys allowed during source registration. */
    int MEASUREMENT_MAX_AGGREGATE_KEYS_PER_SOURCE_REGISTRATION = 50;

    /** Returns maximum number of aggregation keys allowed during source registration. */
    default int getMeasurementMaxAggregateKeysPerSourceRegistration() {
        return MEASUREMENT_MAX_AGGREGATE_KEYS_PER_SOURCE_REGISTRATION;
    }

    /** Maximum number of aggregation keys allowed during trigger registration. */
    int MEASUREMENT_MAX_AGGREGATE_KEYS_PER_TRIGGER_REGISTRATION = 50;

    /** Returns maximum number of aggregation keys allowed during trigger registration. */
    default int getMeasurementMaxAggregateKeysPerTriggerRegistration() {
        return MEASUREMENT_MAX_AGGREGATE_KEYS_PER_TRIGGER_REGISTRATION;
    }

    /**
     * Default early reporting windows for VTC type source. Derived from {@link
     * com.android.adservices.service.measurement.PrivacyParams#EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS}.
     */
    String MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS = "";

    /**
     * Returns configured comma separated early VTC based source's event reporting windows in
     * seconds.
     */
    default String getMeasurementEventReportsVtcEarlyReportingWindows() {
        return MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS;
    }

    /**
     * Default early reporting windows for CTC type source. Derived from {@link
     * com.android.adservices.service.measurement.PrivacyParams#NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS}.
     */
    String MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS =
            String.join(
                    ",",
                    Long.toString(TimeUnit.DAYS.toSeconds(2)),
                    Long.toString(TimeUnit.DAYS.toSeconds(7)));

    /**
     * Returns configured comma separated early CTC based source's event reporting windows in
     * seconds.
     */
    default String getMeasurementEventReportsCtcEarlyReportingWindows() {
        return MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS;
    }

    /**
     * Default aggregate report delay. Derived from {@link
     * com.android.adservices.service.measurement.PrivacyParams#AGGREGATE_REPORT_MIN_DELAY} and
     * {@link com.android.adservices.service.measurement.PrivacyParams#AGGREGATE_REPORT_DELAY_SPAN}.
     */
    String MEASUREMENT_AGGREGATE_REPORT_DELAY_CONFIG =
            String.join(
                    ",",
                    Long.toString(TimeUnit.MINUTES.toMillis(0L)),
                    Long.toString(TimeUnit.MINUTES.toMillis(10L)));

    /**
     * Returns configured comma separated aggregate report min delay and aggregate report delay
     * span.
     */
    default String getMeasurementAggregateReportDelayConfig() {
        return MEASUREMENT_AGGREGATE_REPORT_DELAY_CONFIG;
    }

    /** Default max allowed number of event reports. */
    int DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT = 1;

    /** Returns the default max allowed number of event reports. */
    default int getMeasurementVtcConfigurableMaxEventReportsCount() {
        return DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT;
    }

    boolean MEASUREMENT_ENABLE_FIFO_DESTINATIONS_DELETE_AGGREGATE_REPORTS = false;

    /**
     * Enable deletion of reports along with FIFO destinations. In practice it's a sub flag to
     * {@link #getMeasurementEnableSourceDestinationLimitPriority()}
     */
    default boolean getMeasurementEnableFifoDestinationsDeleteAggregateReports() {
        return MEASUREMENT_ENABLE_FIFO_DESTINATIONS_DELETE_AGGREGATE_REPORTS;
    }

    /** Default Measurement ARA parsing alignment v1 feature flag. */
    boolean MEASUREMENT_ENABLE_ARA_DEDUPLICATION_ALIGNMENT_V1 = true;

    /** Returns whether Measurement ARA deduplication alignment v1 feature is enabled. */
    default boolean getMeasurementEnableAraDeduplicationAlignmentV1() {
        return MEASUREMENT_ENABLE_ARA_DEDUPLICATION_ALIGNMENT_V1;
    }

    /** Default Measurement source deactivation after filtering feature flag. */
    boolean MEASUREMENT_ENABLE_SOURCE_DEACTIVATION_AFTER_FILTERING = false;

    /** Returns whether Measurement source deactivation after filtering feature is enabled. */
    default boolean getMeasurementEnableSourceDeactivationAfterFiltering() {
        return MEASUREMENT_ENABLE_SOURCE_DEACTIVATION_AFTER_FILTERING;
    }

    /** Default Measurement app package name logging flag. */
    boolean MEASUREMENT_ENABLE_APP_PACKAGE_NAME_LOGGING = true;

    /** Returns whether Measurement app package name logging is enabled. */
    default boolean getMeasurementEnableAppPackageNameLogging() {
        return MEASUREMENT_ENABLE_APP_PACKAGE_NAME_LOGGING;
    }

    /** Default allowlist to enable app package name logging. */
    String MEASUREMENT_APP_PACKAGE_NAME_LOGGING_ALLOWLIST = "";

    /** Returns a list of app package names that allows logging. */
    default String getMeasurementAppPackageNameLoggingAllowlist() {
        return MEASUREMENT_APP_PACKAGE_NAME_LOGGING_ALLOWLIST;
    }

    /** Disable measurement reporting jobs to throw unaccounted exceptions by default. */
    boolean MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_UNACCOUNTED_EXCEPTION = false;

    /**
     * If enabled, measurement reporting jobs will throw unaccounted e.g. unexpected unchecked
     * exceptions.
     */
    default boolean getMeasurementEnableReportingJobsThrowUnaccountedException() {
        return MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_UNACCOUNTED_EXCEPTION;
    }

    /**
     * Disable measurement reporting jobs to throw {@link org.json.JSONException} exception by
     * default.
     */
    boolean MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_JSON_EXCEPTION = false;

    /** If enabled, measurement reporting jobs will throw {@link org.json.JSONException}. */
    default boolean getMeasurementEnableReportingJobsThrowJsonException() {
        return MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_JSON_EXCEPTION;
    }

    /** Disable measurement report to be deleted if any unrecoverable exception occurs. */
    boolean MEASUREMENT_ENABLE_DELETE_REPORTS_ON_UNRECOVERABLE_EXCEPTION = false;

    /** If enabled, measurement reports will get deleted if any unrecoverable exception occurs. */
    default boolean getMeasurementEnableReportDeletionOnUnrecoverableException() {
        return MEASUREMENT_ENABLE_DELETE_REPORTS_ON_UNRECOVERABLE_EXCEPTION;
    }

    /** Disable measurement aggregate reporting jobs to throw {@code CryptoException} by default. */
    boolean MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_CRYPTO_EXCEPTION = false;

    /** If enabled, measurement aggregate reporting job will throw {@code CryptoException}. */
    default boolean getMeasurementEnableReportingJobsThrowCryptoException() {
        return MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_CRYPTO_EXCEPTION;
    }

    /**
     * Disable measurement datastore to throw {@link
     * com.android.adservices.data.measurement.DatastoreException} when it occurs by default.
     */
    boolean MEASUREMENT_ENABLE_DATASTORE_MANAGER_THROW_DATASTORE_EXCEPTION = false;

    /**
     * If enabled, measurement DatastoreManager can throw DatastoreException wrapped in an unchecked
     * exception.
     */
    default boolean getMeasurementEnableDatastoreManagerThrowDatastoreException() {
        return MEASUREMENT_ENABLE_DATASTORE_MANAGER_THROW_DATASTORE_EXCEPTION;
    }

    /** Set the sampling rate to 100% for unknown exceptions to be re-thrown. */
    float MEASUREMENT_THROW_UNKNOWN_EXCEPTION_SAMPLING_RATE = 1.0f;

    /** Sampling rate to decide whether to throw unknown exceptions for measurement. */
    default float getMeasurementThrowUnknownExceptionSamplingRate() {
        return MEASUREMENT_THROW_UNKNOWN_EXCEPTION_SAMPLING_RATE;
    }

    boolean MEASUREMENT_DELETE_UNINSTALLED_JOB_PERSISTED = true;

    /** Returns whether to persist this job across device reboots for delete uninstalled job. */
    default boolean getMeasurementDeleteUninstalledJobPersisted() {
        return MEASUREMENT_DELETE_UNINSTALLED_JOB_PERSISTED;
    }

    long MEASUREMENT_DELETE_UNINSTALLED_JOB_PERIOD_MS = TimeUnit.HOURS.toMillis(24);

    /**
     * Returns the min time period (in millis) between each uninstalled-record deletion maintenance
     * job run.
     */
    default long getMeasurementDeleteUninstalledJobPeriodMs() {
        return MEASUREMENT_DELETE_UNINSTALLED_JOB_PERIOD_MS;
    }

    boolean MEASUREMENT_DELETE_EXPIRED_JOB_PERSISTED = true;

    /** Returns whether to persist this job across device reboots for delete expired job. */
    default boolean getMeasurementDeleteExpiredJobPersisted() {
        return MEASUREMENT_DELETE_EXPIRED_JOB_PERSISTED;
    }

    boolean MEASUREMENT_DELETE_EXPIRED_JOB_REQUIRES_DEVICE_IDLE = true;

    /** Returns whether to require device to be idle for delete expired job. */
    default boolean getMeasurementDeleteExpiredJobRequiresDeviceIdle() {
        return MEASUREMENT_DELETE_EXPIRED_JOB_REQUIRES_DEVICE_IDLE;
    }

    long MEASUREMENT_DELETE_EXPIRED_JOB_PERIOD_MS = TimeUnit.HOURS.toMillis(24);

    /**
     * Returns the min time period (in millis) between each expired-record deletion maintenance job
     * run.
     */
    default long getMeasurementDeleteExpiredJobPeriodMs() {
        return MEASUREMENT_DELETE_EXPIRED_JOB_PERIOD_MS;
    }

    boolean MEASUREMENT_EVENT_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW = true;

    /** Returns whether to require battery not low for event reporting job . */
    default boolean getMeasurementEventReportingJobRequiredBatteryNotLow() {
        return MEASUREMENT_EVENT_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW;
    }

    int MEASUREMENT_EVENT_REPORTING_JOB_REQUIRED_NETWORK_TYPE = JobInfo.NETWORK_TYPE_UNMETERED;

    /** Returns the required network type for event reporting job . */
    default int getMeasurementEventReportingJobRequiredNetworkType() {
        return MEASUREMENT_EVENT_REPORTING_JOB_REQUIRED_NETWORK_TYPE;
    }

    boolean MEASUREMENT_EVENT_REPORTING_JOB_PERSISTED = true;

    /** Returns whether to persist this job across device reboots for event reporting job. */
    default boolean getMeasurementEventReportingJobPersisted() {
        return MEASUREMENT_EVENT_REPORTING_JOB_PERSISTED;
    }

    boolean MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW = true;

    /** Returns whether to require battery not low for event fallback reporting job . */
    default boolean getMeasurementEventFallbackReportingJobRequiredBatteryNotLow() {
        return MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW;
    }

    int MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_REQUIRED_NETWORK_TYPE = JobInfo.NETWORK_TYPE_ANY;

    /** Returns the required network type for event fallback reporting job . */
    default int getMeasurementEventFallbackReportingJobRequiredNetworkType() {
        return MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_REQUIRED_NETWORK_TYPE;
    }

    boolean MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERSISTED = true;

    /**
     * Returns whether to persist this job across device reboots for event fallback reporting job.
     */
    default boolean getMeasurementEventFallbackReportingJobPersisted() {
        return MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERSISTED;
    }

    int MEASUREMENT_DEBUG_REPORTING_JOB_REQUIRED_NETWORK_TYPE = JobInfo.NETWORK_TYPE_ANY;

    /** Returns the required network type for debug reporting job . */
    default int getMeasurementDebugReportingJobRequiredNetworkType() {
        return MEASUREMENT_DEBUG_REPORTING_JOB_REQUIRED_NETWORK_TYPE;
    }

    int MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_REQUIRED_NETWORK_TYPE = JobInfo.NETWORK_TYPE_ANY;

    /** Returns the required network type for debug reporting fallback job . */
    default int getMeasurementDebugReportingFallbackJobRequiredNetworkType() {
        return MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_REQUIRED_NETWORK_TYPE;
    }

    boolean MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_PERSISTED = true;

    /**
     * Returns whether to persist this job across device reboots for debug fallback reporting job.
     */
    default boolean getMeasurementDebugReportingFallbackJobPersisted() {
        return MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_PERSISTED;
    }

    int MEASUREMENT_VERBOSE_DEBUG_REPORTING_JOB_REQUIRED_NETWORK_TYPE = JobInfo.NETWORK_TYPE_ANY;

    /** Returns the required network type for verbose debug reporting job . */
    default int getMeasurementVerboseDebugReportingJobRequiredNetworkType() {
        return MEASUREMENT_VERBOSE_DEBUG_REPORTING_JOB_REQUIRED_NETWORK_TYPE;
    }

    boolean MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_PERSISTED = true;

    /**
     * Returns whether to persist this job across device reboots for verbose debug fallback
     * reporting job.
     */
    default boolean getMeasurementVerboseDebugReportingFallbackJobPersisted() {
        return MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_PERSISTED;
    }

    boolean MEASUREMENT_ATTRIBUTION_JOB_PERSISTED = false;

    /** Returns whether to persist this job across device reboots for attribution job. */
    default boolean getMeasurementAttributionJobPersisted() {
        return MEASUREMENT_ATTRIBUTION_JOB_PERSISTED;
    }

    long MEASUREMENT_ATTRIBUTION_JOB_TRIGGERING_DELAY_MS = TimeUnit.MINUTES.toMillis(2);

    /** Delay for attribution job triggering. */
    default long getMeasurementAttributionJobTriggeringDelayMs() {
        return MEASUREMENT_ATTRIBUTION_JOB_TRIGGERING_DELAY_MS;
    }

    boolean MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_PERSISTED = true;

    /** Returns whether to persist this job across device reboots for attribution fallback job. */
    default boolean getMeasurementAttributionFallbackJobPersisted() {
        return MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_PERSISTED;
    }

    int MEASUREMENT_ASYNC_REGISTRATION_QUEUE_JOB_REQUIRED_NETWORK_TYPE = JobInfo.NETWORK_TYPE_ANY;

    /** Returns the required network type for async registration queue job. */
    default int getMeasurementAsyncRegistrationQueueJobRequiredNetworkType() {
        return MEASUREMENT_ASYNC_REGISTRATION_QUEUE_JOB_REQUIRED_NETWORK_TYPE;
    }

    boolean MEASUREMENT_ASYNC_REGISTRATION_QUEUE_JOB_PERSISTED = false;

    /**
     * Returns whether to persist this job across device reboots for async registration queue job.
     */
    default boolean getMeasurementAsyncRegistrationQueueJobPersisted() {
        return MEASUREMENT_ASYNC_REGISTRATION_QUEUE_JOB_PERSISTED;
    }

    boolean MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_REQUIRED_BATTERY_NOT_LOW = true;

    /** Returns whether to require battery not low for async registration queue fallback job. */
    default boolean getMeasurementAsyncRegistrationFallbackJobRequiredBatteryNotLow() {
        return MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_REQUIRED_BATTERY_NOT_LOW;
    }

    int MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_REQUIRED_NETWORK_TYPE =
            JobInfo.NETWORK_TYPE_ANY;

    /** Returns the required network type for async registration queue fallback job. */
    default int getMeasurementAsyncRegistrationFallbackJobRequiredNetworkType() {
        return MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_REQUIRED_NETWORK_TYPE;
    }

    boolean MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_PERSISTED = true;

    /**
     * Returns whether to persist this job across device reboots for async registration queue
     * fallback job.
     */
    default boolean getMeasurementAsyncRegistrationFallbackJobPersisted() {
        return MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_PERSISTED;
    }

    boolean MEASUREMENT_AGGREGATE_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW = true;

    /** Returns whether to require battery not low for aggregate reporting job. */
    default boolean getMeasurementAggregateReportingJobRequiredBatteryNotLow() {
        return MEASUREMENT_AGGREGATE_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW;
    }

    int MEASUREMENT_AGGREGATE_REPORTING_JOB_REQUIRED_NETWORK_TYPE = JobInfo.NETWORK_TYPE_UNMETERED;

    /** Returns the required network type for aggregate reporting job. */
    default int getMeasurementAggregateReportingJobRequiredNetworkType() {
        return MEASUREMENT_AGGREGATE_REPORTING_JOB_REQUIRED_NETWORK_TYPE;
    }

    boolean MEASUREMENT_AGGREGATE_REPORTING_JOB_PERSISTED = true;

    /** Returns whether to persist this job across device reboots for aggregate reporting job. */
    default boolean getMeasurementAggregateReportingJobPersisted() {
        return MEASUREMENT_AGGREGATE_REPORTING_JOB_PERSISTED;
    }

    boolean MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW = true;

    /** Returns whether to require battery not low for aggregate fallback reporting job. */
    default boolean getMeasurementAggregateFallbackReportingJobRequiredBatteryNotLow() {
        return MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW;
    }

    int MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_REQUIRED_NETWORK_TYPE =
            JobInfo.NETWORK_TYPE_ANY;

    /** Returns the required network type for aggregate fallback reporting job . */
    default int getMeasurementAggregateFallbackReportingJobRequiredNetworkType() {
        return MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_REQUIRED_NETWORK_TYPE;
    }

    boolean MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERSISTED = true;

    /**
     * Returns whether to persist this job across device reboots for aggregate fallback reporting
     * job.
     */
    default boolean getMeasurementAggregateFallbackReportingJobPersisted() {
        return MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERSISTED;
    }

    boolean MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW = true;

    /** Returns whether to require battery not low for immediate aggregate reporting job. */
    default boolean getMeasurementImmediateAggregateReportingJobRequiredBatteryNotLow() {
        return MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW;
    }

    int MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_REQUIRED_NETWORK_TYPE =
            JobInfo.NETWORK_TYPE_ANY;

    /** Returns the required network type for immediate aggregate reporting job. */
    default int getMeasurementImmediateAggregateReportingJobRequiredNetworkType() {
        return MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_REQUIRED_NETWORK_TYPE;
    }

    boolean MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_PERSISTED = true;

    /** Returns whether to persist immediate aggregate reporting job across device reboots. */
    default boolean getMeasurementImmediateAggregateReportingJobPersisted() {
        return MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_PERSISTED;
    }

    /** Default value for Reporting Job feature flag */
    @FeatureFlag boolean MEASUREMENT_REPORTING_JOB_ENABLED = false;

    /** Feature flag for Reporting Job */
    default boolean getMeasurementReportingJobServiceEnabled() {
        // The Measurement API should be enabled as a prerequisite.
        return getMeasurementEnabled() && MEASUREMENT_REPORTING_JOB_ENABLED;
    }

    @ConfigFlag boolean MEASUREMENT_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW = true;

    /** Returns whether to require battery not low for reporting job. */
    default boolean getMeasurementReportingJobRequiredBatteryNotLow() {
        return MEASUREMENT_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW;
    }

    @ConfigFlag int MEASUREMENT_REPORTING_JOB_REQUIRED_NETWORK_TYPE = JobInfo.NETWORK_TYPE_ANY;

    /** Returns the required network type for reporting job. */
    default int getMeasurementReportingJobRequiredNetworkType() {
        return MEASUREMENT_REPORTING_JOB_REQUIRED_NETWORK_TYPE;
    }

    @ConfigFlag boolean MEASUREMENT_REPORTING_JOB_PERSISTED = true;

    /** Returns whether to persist this job across device reboots for reporting job. */
    default boolean getMeasurementReportingJobPersisted() {
        return MEASUREMENT_REPORTING_JOB_PERSISTED;
    }

    /**
     * Default value for delaying reporting job service so that reports can be batched. Values are
     * in milliseconds.
     */
    @ConfigFlag
    long MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(30);

    /**
     * Returns ms to defer transmission of reports in {@link
     * com.android.adservices.service.measurement.reporting.ReportingJobService}
     */
    default long getMeasurementReportingJobServiceBatchWindowMillis() {
        return MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS;
    }

    /**
     * Default value for minimum amount of time to wait between executions of reporting job service.
     * This is to throttle the service. Values are in milliseconds.
     */
    @ConfigFlag
    long MEASUREMENT_REPORTING_JOB_SERVICE_MIN_EXECUTION_WINDOW_MILLIS =
            TimeUnit.MINUTES.toMillis(30);

    /**
     * Returns minimum ms to wait between invocations of {@link
     * com.android.adservices.service.measurement.reporting.ReportingJobService}
     */
    default long getMeasurementReportingJobServiceMinExecutionWindowMillis() {
        return MEASUREMENT_REPORTING_JOB_SERVICE_MIN_EXECUTION_WINDOW_MILLIS;
    }

    /** Default value for null aggregate report rate including source registration time. */
    float MEASUREMENT_NULL_AGG_REPORT_RATE_INCL_SOURCE_REGISTRATION_TIME = .008f;

    /**
     * Returns the rate at which null aggregate reports are generated whenever an actual aggregate
     * report is successfully generated.
     */
    default float getMeasurementNullAggReportRateInclSourceRegistrationTime() {
        return MEASUREMENT_NULL_AGG_REPORT_RATE_INCL_SOURCE_REGISTRATION_TIME;
    }

    /** Default value for null report rate excluding source registration time. */
    float MEASUREMENT_NULL_AGG_REPORT_RATE_EXCL_SOURCE_REGISTRATION_TIME = .05f;

    /**
     * Returns the rate at which null aggregate reports are generated whenever the trigger is
     * configured to exclude the source registration time and there is no matching source.
     */
    default float getMeasurementNullAggReportRateExclSourceRegistrationTime() {
        return MEASUREMENT_NULL_AGG_REPORT_RATE_EXCL_SOURCE_REGISTRATION_TIME;
    }

    /** Default U18 UX feature flag. */
    boolean DEFAULT_U18_UX_ENABLED = false;

    /** U18 UX feature flag.. */
    default boolean getU18UxEnabled() {
        return DEFAULT_U18_UX_ENABLED;
    }

    /** Default enableAdServices system API feature flag.. */
    boolean DEFAULT_ENABLE_AD_SERVICES_SYSTEM_API = false;

    /** enableAdServices system API feature flag.. */
    default boolean getEnableAdServicesSystemApi() {
        return DEFAULT_ENABLE_AD_SERVICES_SYSTEM_API;
    }

    /** Disables client error logging for the list of error codes. Default value is empty list. */
    ImmutableList<Integer> ERROR_CODE_LOGGING_DENY_LIST = ImmutableList.of();

    /** Returns a list of error codes for which we don't want to do error logging. */
    default ImmutableList<Integer> getErrorCodeLoggingDenyList() {
        return ERROR_CODE_LOGGING_DENY_LIST;
    }

    /** Returns the map of UX flags. */
    default Map<String, Boolean> getUxFlags() {
        return new HashMap<>();
    }

    /** Enable feature to unify destinations for event reports by default. */
    boolean DEFAULT_MEASUREMENT_ENABLE_COARSE_EVENT_REPORT_DESTINATIONS = true;

    /**
     * Returns true if event reporting destinations are enabled to be reported in a coarse manner,
     * i.e. both app and web destinations are merged into a single array in the event report.
     */
    default boolean getMeasurementEnableCoarseEventReportDestinations() {
        return DEFAULT_MEASUREMENT_ENABLE_COARSE_EVENT_REPORT_DESTINATIONS;
    }

    /** Privacy Params */
    int MEASUREMENT_MAX_DISTINCT_WEB_DESTINATIONS_IN_SOURCE_REGISTRATION = 3;

    /** Max distinct web destinations in a source registration. */
    default int getMeasurementMaxDistinctWebDestinationsInSourceRegistration() {
        return MEASUREMENT_MAX_DISTINCT_WEB_DESTINATIONS_IN_SOURCE_REGISTRATION;
    }

    long MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS =
            TimeUnit.DAYS.toSeconds(30);

    /**
     * Max expiration value in seconds for attribution reporting register source. This value is also
     * the default if no expiration was specified.
     */
    default long getMeasurementMaxReportingRegisterSourceExpirationInSeconds() {
        return MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
    }

    long MEASUREMENT_MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS =
            TimeUnit.DAYS.toSeconds(1);

    /** Min expiration value in seconds for attribution reporting register source. */
    default long getMeasurementMinReportingRegisterSourceExpirationInSeconds() {
        return MEASUREMENT_MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
    }

    long MEASUREMENT_MAX_INSTALL_ATTRIBUTION_WINDOW = TimeUnit.DAYS.toSeconds(30);

    /** Maximum limit of duration to determine attribution for a verified installation. */
    default long getMeasurementMaxInstallAttributionWindow() {
        return MEASUREMENT_MAX_INSTALL_ATTRIBUTION_WINDOW;
    }

    long MEASUREMENT_MIN_INSTALL_ATTRIBUTION_WINDOW = TimeUnit.DAYS.toSeconds(1);

    /** Minimum limit of duration to determine attribution for a verified installation. */
    default long getMeasurementMinInstallAttributionWindow() {
        return MEASUREMENT_MIN_INSTALL_ATTRIBUTION_WINDOW;
    }

    long MEASUREMENT_MAX_POST_INSTALL_EXCLUSIVITY_WINDOW = TimeUnit.DAYS.toSeconds(30);

    /** Maximum acceptable install cooldown period. */
    default long getMeasurementMaxPostInstallExclusivityWindow() {
        return MEASUREMENT_MAX_POST_INSTALL_EXCLUSIVITY_WINDOW;
    }

    long MEASUREMENT_MIN_POST_INSTALL_EXCLUSIVITY_WINDOW = 0L;

    /** Default and minimum value for cooldown period of source which led to installation. */
    default long getMeasurementMinPostInstallExclusivityWindow() {
        return MEASUREMENT_MIN_POST_INSTALL_EXCLUSIVITY_WINDOW;
    }

    int MEASUREMENT_MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE = 65536;

    /**
     * L1, the maximum sum of the contributions (values) across all buckets for a given source
     * event.
     */
    default int getMeasurementMaxSumOfAggregateValuesPerSource() {
        return MEASUREMENT_MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE;
    }

    long MEASUREMENT_RATE_LIMIT_WINDOW_MILLISECONDS = TimeUnit.DAYS.toMillis(30);

    /**
     * Rate limit window for (Source Site, Destination Site, Reporting Site, Window) privacy unit.
     * 30 days.
     */
    default long getMeasurementRateLimitWindowMilliseconds() {
        return MEASUREMENT_RATE_LIMIT_WINDOW_MILLISECONDS;
    }

    long MEASUREMENT_MIN_REPORTING_ORIGIN_UPDATE_WINDOW = TimeUnit.DAYS.toMillis(1);

    /** Minimum time window after which reporting origin can be migrated */
    default long getMeasurementMinReportingOriginUpdateWindow() {
        return MEASUREMENT_MIN_REPORTING_ORIGIN_UPDATE_WINDOW;
    }

    boolean MEASUREMENT_ENABLE_PREINSTALL_CHECK = false;

    /** Returns true when pre-install check is enabled. */
    default boolean getMeasurementEnablePreinstallCheck() {
        return MEASUREMENT_ENABLE_PREINSTALL_CHECK;
    }

    /** Default value of flag for session stable kill switches. */
    @SuppressWarnings("AvoidKillSwitchFlagUsage") // Legacy kill switch flag
    boolean MEASUREMENT_ENABLE_SESSION_STABLE_KILL_SWITCHES = true;

    /** Returns true when session stable kill switches are enabled. */
    default boolean getMeasurementEnableSessionStableKillSwitches() {
        return MEASUREMENT_ENABLE_SESSION_STABLE_KILL_SWITCHES;
    }

    boolean MEASUREMENT_ENABLE_ATTRIBUTION_SCOPE = false;

    /** Returns true when attribution scope is enabled. */
    default boolean getMeasurementEnableAttributionScope() {
        return MEASUREMENT_ENABLE_ATTRIBUTION_SCOPE;
    }

    boolean MEASUREMENT_ENABLE_NAVIGATION_REPORTING_ORIGIN_CHECK = false;

    /**
     * Returns true if validation is enabled for one navigation per reporting origin per
     * registration.
     */
    default boolean getMeasurementEnableNavigationReportingOriginCheck() {
        return MEASUREMENT_ENABLE_NAVIGATION_REPORTING_ORIGIN_CHECK;
    }

    @FeatureFlag
    boolean MEASUREMENT_ENABLE_SEPARATE_DEBUG_REPORT_TYPES_FOR_ATTRIBUTION_RATE_LIMIT = false;

    /**
     * Enables separate debug report types for event and aggregate attribution rate limit
     * violations.
     */
    default boolean getMeasurementEnableSeparateDebugReportTypesForAttributionRateLimit() {
        return MEASUREMENT_ENABLE_SEPARATE_DEBUG_REPORT_TYPES_FOR_ATTRIBUTION_RATE_LIMIT;
    }

    int MEASUREMENT_MAX_ATTRIBUTION_SCOPES_PER_SOURCE = 20;

    /** Returns max number of attribution scopes per source. */
    default int getMeasurementMaxAttributionScopesPerSource() {
        return MEASUREMENT_MAX_ATTRIBUTION_SCOPES_PER_SOURCE;
    }

    int MEASUREMENT_MAX_ATTRIBUTION_SCOPE_LENGTH = 50;

    /** Returns max length of attribution scope. */
    default int getMeasurementMaxAttributionScopeLength() {
        return MEASUREMENT_MAX_ATTRIBUTION_SCOPE_LENGTH;
    }

    @ConfigFlag int MEASUREMENT_MAX_LENGTH_PER_BUDGET_NAME = 25;

    /** Returns max length of an attribution source's named budget's name. */
    default int getMeasurementMaxLengthPerBudgetName() {
        return MEASUREMENT_MAX_LENGTH_PER_BUDGET_NAME;
    }

    @ConfigFlag int MEASUREMENT_MAX_NAMED_BUDGETS_PER_SOURCE_REGISTRATION = 25;

    /** Returns max size of an attribution source's named budget list. */
    default int getMeasurementMaxNamedBudgetsPerSourceRegistration() {
        return MEASUREMENT_MAX_NAMED_BUDGETS_PER_SOURCE_REGISTRATION;
    }

    /** Default value of flag for logging consent migration metrics when OTA from S to T+. */
    boolean DEFAULT_ADSERVICES_CONSENT_MIGRATION_LOGGING_ENABLED = true;

    /***
     * Returns true when logging consent migration metrics is enabled when OTA from S to T+.
     */
    default boolean getAdservicesConsentMigrationLoggingEnabled() {
        return DEFAULT_ADSERVICES_CONSENT_MIGRATION_LOGGING_ENABLED;
    }

    /** The default token for resetting consent notificatio.. */
    String CONSENT_NOTIFICATION_RESET_TOKEN = "";

    /** Returns the consent notification reset token. */
    default String getConsentNotificationResetToken() {
        return CONSENT_NOTIFICATION_RESET_TOKEN;
    }

    /** Default whether Enrollment Mdd Record Deletion feature is enabled. */
    boolean ENROLLMENT_MDD_RECORD_DELETION_ENABLED = false;

    /** Returns whether the {@code enrollmentMddRecordDeletion} feature is enabled. */
    default boolean getEnrollmentMddRecordDeletionEnabled() {
        return ENROLLMENT_MDD_RECORD_DELETION_ENABLED;
    }

    /** Default value of whether topics cobalt logging feature is enabled. */
    boolean TOPICS_COBALT_LOGGING_ENABLED = false;

    /**
     * Returns whether the topics cobalt logging feature is enabled.
     *
     * <p>The topics cobalt logging will be disabled either the {@code getCobaltLoggingEnabled} or
     * {@code TOPICS_COBALT_LOGGING_ENABLED} is {@code false}.
     */
    default boolean getTopicsCobaltLoggingEnabled() {
        return getCobaltLoggingEnabled() && TOPICS_COBALT_LOGGING_ENABLED;
    }

    /**
     * Default value of whether cobalt logging feature is enabled for source and trigger
     * registrations in measurement service.
     */
    @FeatureFlag boolean MSMT_REGISTRATION_COBALT_LOGGING_ENABLED = false;

    /**
     * Returns whether the cobalt logging feature is enabled for source and trigger registration in
     * measurement service .
     *
     * <p>The cobalt logging for measurement registration will be disabled either the {@code
     * getCobaltLoggingEnabled} or {@code MSMT_REGISTRATION_COBALT_LOGGING_ENABLED} is {@code
     * false}.
     */
    default boolean getMsmtRegistrationCobaltLoggingEnabled() {
        return getCobaltLoggingEnabled() && MSMT_REGISTRATION_COBALT_LOGGING_ENABLED;
    }

    /**
     * Default value of whether cobalt logging feature is enabled for attribution metrics in
     * measurement service.
     */
    @FeatureFlag boolean MSMT_ATTRIBUTION_COBALT_LOGGING_ENABLED = false;

    /**
     * Returns whether the cobalt logging feature is enabled for attribution metrics in measurement
     * service .
     *
     * <p>The cobalt logging for measurement registration will be disabled either the {@code
     * getCobaltLoggingEnabled} or {@code MSMT_ATTRIBUTION_COBALT_LOGGING_ENABLED} is {@code false}.
     */
    default boolean getMsmtAttributionCobaltLoggingEnabled() {
        return getCobaltLoggingEnabled() && MSMT_ATTRIBUTION_COBALT_LOGGING_ENABLED;
    }

    /**
     * Default value of whether cobalt logging feature is enabled for reporting metrics in
     * measurement service.
     */
    @FeatureFlag boolean MSMT_REPORTING_COBALT_LOGGING_ENABLED = false;

    /**
     * Returns whether the cobalt logging feature is enabled for reporting metrics in measurement
     * service .
     *
     * <p>The cobalt logging for measurement registration will be disabled either the {@code
     * getCobaltLoggingEnabled} or {@code MSMT_REPORTING_COBALT_LOGGING_ENABLED} is {@code false}.
     */
    default boolean getMsmtReportingCobaltLoggingEnabled() {
        return getCobaltLoggingEnabled() && MSMT_REPORTING_COBALT_LOGGING_ENABLED;
    }

    /** Default value of whether app name and api error cobalt logging feature is enabled. */
    boolean APP_NAME_API_ERROR_COBALT_LOGGING_ENABLED = false;

    /**
     * Returns whether the app name and api error cobalt logging feature is enabled.
     *
     * <p>The app name and api error cobalt logging will be disabled either the {@code
     * getCobaltLoggingEnabled} or {@code APP_NAME_API_ERROR_COBALT_LOGGING_ENABLED} is {@code
     * false}.
     */
    default boolean getAppNameApiErrorCobaltLoggingEnabled() {
        return getCobaltLoggingEnabled() && APP_NAME_API_ERROR_COBALT_LOGGING_ENABLED;
    }

    /**
     * Default value of {@link AppNameApiErrorLogger} logging sampling rate.
     *
     * <p>The value should be an integer in the range of {@code [0, 100]}, where {@code 100} is to
     * log all events and {@code 0} is to log no events.
     */
    int APP_NAME_API_ERROR_COBALT_LOGGING_SAMPLING_RATE = 100;

    /** Returns the {@link AppNameApiErrorLogger} logging sampling rate. */
    default int getAppNameApiErrorCobaltLoggingSamplingRate() {
        return APP_NAME_API_ERROR_COBALT_LOGGING_SAMPLING_RATE;
    }

    /** Default value of Cobalt Adservices Api key. */
    String COBALT_ADSERVICES_API_KEY_HEX = CobaltConstants.DEFAULT_API_KEY;

    default String getCobaltAdservicesApiKeyHex() {
        return COBALT_ADSERVICES_API_KEY_HEX;
    }

    /**
     * Default value of Adservices release stage for Cobalt. The value should correspond to {@link
     * com.google.cobalt.ReleaseStage} enum.
     */
    String ADSERVICES_RELEASE_STAGE_FOR_COBALT = CobaltConstants.DEFAULT_RELEASE_STAGE;

    /** Returns the value of Adservices release stage for Cobalt. */
    default String getAdservicesReleaseStageForCobalt() {
        return ADSERVICES_RELEASE_STAGE_FOR_COBALT;
    }

    /** Default value of whether Cobalt registry out of band update feature is enabled */
    @FeatureFlag boolean COBALT_REGISTRY_OUT_OF_BAND_UPDATE_ENABLED = false;

    /** Returns whether Cobalt registry out of band update feature is enabled. */
    default boolean getCobaltRegistryOutOfBandUpdateEnabled() {
        return COBALT_REGISTRY_OUT_OF_BAND_UPDATE_ENABLED;
    }

    /** MDD Cobalt registry manifest url. */
    String MDD_COBALT_REGISTRY_MANIFEST_FILE_URL = "";

    /** Returns MDD Cobalt registry manifest url. */
    default String getMddCobaltRegistryManifestFileUrl() {
        return MDD_COBALT_REGISTRY_MANIFEST_FILE_URL;
    }

    /** Default value of whether Cobalt operational logging feature is enabled. */
    @FeatureFlag boolean COBALT_OPERATIONAL_LOGGING_ENABLED = false;

    /** Returns whether Cobalt operational logging is enabled. */
    default boolean getCobaltOperationalLoggingEnabled() {
        return COBALT_OPERATIONAL_LOGGING_ENABLED;
    }

    /**
     * The flag to enable falling back to the default base registry. It prevents Cobalt using merged
     * registry. So in case the MDD downloaded registry and merged with default registry already, we
     * can still fall back to use the default base registry.
     */
    @FeatureFlag Boolean COBALT__FALL_BACK_TO_DEFAULT_BASE_REGISTRY = false;

    /** Returns the flag to enable falling back to the default base registry. */
    default boolean getCobaltFallBackToDefaultBaseRegistry() {
        return COBALT__FALL_BACK_TO_DEFAULT_BASE_REGISTRY;
    }

    /**
     * The flag tells Cobalt to ignore specific list of report id(s). This flag is used for
     * out-of-band registry update. The format is a comma-separated list of 4 colon separated ints,
     * e.g. a single value is customer_id:project_id:metric_id:report_id.
     */
    @ConfigFlag String COBALT__IGNORED_REPORT_ID_LIST = "";

    /** Returns the flag to ignore specific list of report id(s). */
    default String getCobaltIgnoredReportIdList() {
        return COBALT__IGNORED_REPORT_ID_LIST;
    }

    /**
     * The flag to enable Cobalt logging for API call response, including both success and failure
     * responses. This metric is replacing the app package name API error metric, controlled by the
     * {@link APP_NAME_API_ERROR_COBALT_LOGGING_ENABLED} flag.
     */
    @FeatureFlag Boolean COBALT__ENABLE_API_CALL_RESPONSE_LOGGING = false;

    /** Returns whether Cobalt Api call response logging is enabled. */
    default boolean getCobaltEnableApiCallResponseLogging() {
        return COBALT__ENABLE_API_CALL_RESPONSE_LOGGING;
    }

    /**
     * A feature flag to enable DB schema change to version 8 in Topics API. Version 8 is to add
     * logged_topic column to ReturnedTopic table.
     *
     * <p>Default value is false, which means the feature is disabled by default and needs to be
     * ramped up.
     */
    boolean ENABLE_LOGGED_TOPIC = false;

    /** Returns if to enable logged_topic column in ReturnedTopic table. */
    default boolean getEnableLoggedTopic() {
        return ENABLE_LOGGED_TOPIC;
    }

    /** Whether to enable database schema version 8 */
    boolean ENABLE_DATABASE_SCHEMA_VERSION_8 = false;

    /** Returns if to enable database schema version 8. */
    default boolean getEnableDatabaseSchemaVersion8() {
        return ENABLE_DATABASE_SCHEMA_VERSION_8;
    }

    /** Whether to enable database schema version 9. */
    boolean ENABLE_DATABASE_SCHEMA_VERSION_9 = false;

    /** Returns if to enable database schema version 9. */
    default boolean getEnableDatabaseSchemaVersion9() {
        return ENABLE_DATABASE_SCHEMA_VERSION_9;
    }

    /** Flag to control which allow list in getMeasurementApiStatus. */
    boolean MEASUREMENT_ENABLE_API_STATUS_ALLOW_LIST_CHECK = false;

    /** Returns the flag to control which allow list to use in getMeasurementApiStatus. */
    default boolean getMsmtEnableApiStatusAllowListCheck() {
        return MEASUREMENT_ENABLE_API_STATUS_ALLOW_LIST_CHECK;
    }

    @ConfigFlag
    long MEASUREMENT_MAX_REINSTALL_REATTRIBUTION_WINDOW_SECONDS = TimeUnit.DAYS.toSeconds(90);

    /** Maximum limit of duration to determine reattribution for a verified installation. */
    default long getMeasurementMaxReinstallReattributionWindowSeconds() {
        return MEASUREMENT_MAX_REINSTALL_REATTRIBUTION_WINDOW_SECONDS;
    }

    @FeatureFlag boolean MEASUREMENT_ENABLE_REINSTALL_REATTRIBUTION = false;

    /** Returns whether to enable reinstall reattribution. */
    default boolean getMeasurementEnableReinstallReattribution() {
        return MEASUREMENT_ENABLE_REINSTALL_REATTRIBUTION;
    }

    @ConfigFlag
    long MEASUREMENT_MIN_REPORT_LIFESPAN_FOR_UNINSTALL_SECONDS = TimeUnit.DAYS.toSeconds(1);

    /** Minimum time a report can stay on the device after app uninstall. */
    default long getMeasurementMinReportLifespanForUninstallSeconds() {
        return MEASUREMENT_MIN_REPORT_LIFESPAN_FOR_UNINSTALL_SECONDS;
    }

    @FeatureFlag boolean MEASUREMENT_ENABLE_MIN_REPORT_LIFESPAN_FOR_UNINSTALL = false;

    /** Returns whether to enable uninstall report feature. */
    default boolean getMeasurementEnableMinReportLifespanForUninstall() {
        return MEASUREMENT_ENABLE_MIN_REPORT_LIFESPAN_FOR_UNINSTALL;
    }

    @FeatureFlag boolean MEASUREMENT_ENABLE_INSTALL_ATTRIBUTION_ON_S = false;

    /** Returns whether to enable install attribution on S feature. */
    default boolean getMeasurementEnableInstallAttributionOnS() {
        return MEASUREMENT_ENABLE_INSTALL_ATTRIBUTION_ON_S;
    }

    /** The maximum allowable length of a trigger context id. */
    int MEASUREMENT_MAX_LENGTH_OF_TRIGGER_CONTEXT_ID = 64;

    /** Return the maximum allowable length of a trigger context id. */
    default int getMeasurementMaxLengthOfTriggerContextId() {
        return MEASUREMENT_MAX_LENGTH_OF_TRIGGER_CONTEXT_ID;
    }

    /** Flag for enabling measurement registrations using ODP */
    boolean MEASUREMENT_ENABLE_ODP_WEB_TRIGGER_REGISTRATION = false;

    /** Return true if measurement registrations through ODP is enabled */
    default boolean getMeasurementEnableOdpWebTriggerRegistration() {
        return MEASUREMENT_ENABLE_ODP_WEB_TRIGGER_REGISTRATION;
    }

    float DEFAULT_MEASUREMENT_PRIVACY_EPSILON = 14f;

    default float getMeasurementPrivacyEpsilon() {
        return DEFAULT_MEASUREMENT_PRIVACY_EPSILON;
    }

    /** Flag for enabling measurement event level epsilon in source */
    @FeatureFlag boolean MEASUREMENT_ENABLE_EVENT_LEVEL_EPSILON_IN_SOURCE = false;

    /** Returns true if measurement event level epsilon in source is enabled */
    default boolean getMeasurementEnableEventLevelEpsilonInSource() {
        return MEASUREMENT_ENABLE_EVENT_LEVEL_EPSILON_IN_SOURCE;
    }

    @FeatureFlag boolean MEASUREMENT_ENABLE_DESTINATION_LIMIT_PRIORITY = false;

    default boolean getMeasurementEnableSourceDestinationLimitPriority() {
        return MEASUREMENT_ENABLE_DESTINATION_LIMIT_PRIORITY;
    }

    /**
     * Default destination limit algorithm configuration. LIFO (0) by default, can be configured as
     * FIFO (1).
     */
    @ConfigFlag int MEASUREMENT_DEFAULT_DESTINATION_LIMIT_ALGORITHM = 0;

    default int getMeasurementDefaultSourceDestinationLimitAlgorithm() {
        return MEASUREMENT_DEFAULT_DESTINATION_LIMIT_ALGORITHM;
    }

    @FeatureFlag boolean MEASUREMENT_ENABLE_DESTINATION_LIMIT_ALGORITHM_FIELD = false;

    default boolean getMeasurementEnableSourceDestinationLimitAlgorithmField() {
        return MEASUREMENT_ENABLE_DESTINATION_LIMIT_ALGORITHM_FIELD;
    }

    @FeatureFlag boolean MEASUREMENT_ENABLE_AGGREGATE_VALUE_FILTERS = false;

    /*
     * Returns whether filtering in Trigger's AGGREGATABLE_VALUES is allowed.
     */
    default boolean getMeasurementEnableAggregateValueFilters() {
        return MEASUREMENT_ENABLE_AGGREGATE_VALUE_FILTERS;
    }

    /** Flag for enabling measurement aggregate debug reporting */
    @FeatureFlag boolean MEASUREMENT_ENABLE_AGGREGATE_DEBUG_REPORTING = false;

    /** Returns whether measurement aggregate debug reporting is enabled. */
    default boolean getMeasurementEnableAggregateDebugReporting() {
        return MEASUREMENT_ENABLE_AGGREGATE_DEBUG_REPORTING;
    }

    @ConfigFlag int MEASUREMENT_ADR_BUDGET_PER_ORIGIN_PUBLISHER_WINDOW = 65536; // = 2^16

    /** Returns aggregatable debug reporting budget allocated per origin per publisher per window */
    default int getMeasurementAdrBudgetOriginXPublisherXWindow() {
        return MEASUREMENT_ADR_BUDGET_PER_ORIGIN_PUBLISHER_WINDOW;
    }

    @ConfigFlag int MEASUREMENT_ADR_BUDGET_PER_PUBLISHER_WINDOW = 1048576; // = 2^20

    /** Returns aggregatable debug reporting budget allocated per publisher per window */
    default int getMeasurementAdrBudgetPublisherXWindow() {
        return MEASUREMENT_ADR_BUDGET_PER_PUBLISHER_WINDOW;
    }

    @ConfigFlag long MEASUREMENT_ADR_BUDGET_WINDOW_LENGTH_MILLIS = TimeUnit.DAYS.toMillis(1);

    /**
     * Returns aggregatable debug reporting budget consumption tracking window length in
     * milliseconds.
     */
    default long getMeasurementAdrBudgetWindowLengthMillis() {
        return MEASUREMENT_ADR_BUDGET_WINDOW_LENGTH_MILLIS;
    }

    @ConfigFlag int MEASUREMENT_MAX_ADR_COUNT_PER_SOURCE = 5;

    /** Returns maximum number of aggregatable debug reports allowed per source. */
    default int getMeasurementMaxAdrCountPerSource() {
        return MEASUREMENT_MAX_ADR_COUNT_PER_SOURCE;
    }

    /**
     * Default whether to limit logging for enrollment metrics to avoid performance issues. This
     * includes not logging data that requires database queries and downloading MDD files.
     */
    boolean ENROLLMENT_ENABLE_LIMITED_LOGGING = false;

    /** Returns whether enrollment logging should be limited. */
    default boolean getEnrollmentEnableLimitedLogging() {
        return ENROLLMENT_ENABLE_LIMITED_LOGGING;
    }

    @ConfigFlag int MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES = 1;

    default int getMeasurementDefaultFilteringIdMaxBytes() {
        return MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES;
    }

    @ConfigFlag int MEASUREMENT_MAX_FILTERING_ID_MAX_BYTES = 8;

    default int getMeasurementMaxFilteringIdMaxBytes() {
        return MEASUREMENT_MAX_FILTERING_ID_MAX_BYTES;
    }

    @FeatureFlag boolean MEASUREMENT_ENABLE_FLEXIBLE_CONTRIBUTION_FILTERING = false;

    default boolean getMeasurementEnableFlexibleContributionFiltering() {
        return MEASUREMENT_ENABLE_FLEXIBLE_CONTRIBUTION_FILTERING;
    }

    /** Flag for enabling measurement debug keys privacy enforcement */
    @FeatureFlag boolean MEASUREMENT_ENABLE_BOTH_SIDE_DEBUG_KEYS_IN_REPORTS = false;

    /** Returns whether measurement debug keys privacy enforcement is enabled. */
    default boolean getMeasurementEnableBothSideDebugKeysInReports() {
        return MEASUREMENT_ENABLE_BOTH_SIDE_DEBUG_KEYS_IN_REPORTS;
    }

    /**
     * Default value for if events will be registered as a source of attribution in addition to
     * being reported.
     */
    boolean FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED = false;

    /**
     * Returns if events will be registered as a source of attribution in addition to being
     * reported.
     *
     * <p>This, unlocked by the short-term integration between Protected Audience (PA) and
     * Measurement's ARA, enables the {@link
     * android.adservices.adselection.AdSelectionManager#reportEvent} API to report an event and
     * register it as source of attribution, using a single API call, unified under the hood.
     *
     * <ul>
     *   <li>When enabled, by default: ARA will report and register the event.
     *   <li>When enabled, with fallback: PA will report the event and ARA will register the event.
     *   <li>When disabled, when {@link
     *       android.adservices.adselection.AdSelectionManager#reportEvent} is called, only PA will
     *       report the event.
     * </ul>
     */
    default boolean getFledgeMeasurementReportAndRegisterEventApiEnabled() {
        return FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED;
    }

    /** Default value for if the fallback for event reporting and source registration is enabled. */
    boolean FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_FALLBACK_ENABLED = false;

    /**
     * Returns if the fallback for event reporting and source registration is enabled.
     *
     * <ul>
     *   <li>Only relevant if {@link #getFledgeMeasurementReportAndRegisterEventApiEnabled} is
     *       {@code true}.
     *   <li>When enabled, PA will report the event and ARA will register the event.
     *   <li>When disabled, ARA will report and register the event.
     * </ul>
     *
     * <p>If enabled
     */
    default boolean getFledgeMeasurementReportAndRegisterEventApiFallbackEnabled() {
        return getFledgeMeasurementReportAndRegisterEventApiEnabled()
                && FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_FALLBACK_ENABLED;
    }

    /** Cobalt logging job period in milliseconds. */
    long COBALT_LOGGING_JOB_PERIOD_MS = 6 * 60 * 60 * 1000; // 6 hours.

    /** Returns the max time period (in milliseconds) between each cobalt logging job run. */
    default long getCobaltLoggingJobPeriodMs() {
        return COBALT_LOGGING_JOB_PERIOD_MS;
    }

    long COBALT_UPLOAD_SERVICE_UNBIND_DELAY_MS = 10 * 1000; // 10 seconds

    /**
     * Returns the amount of time Cobalt should wait (in milliseconds) before unbinding from its
     * upload service.
     */
    default long getCobaltUploadServiceUnbindDelayMs() {
        return COBALT_UPLOAD_SERVICE_UNBIND_DELAY_MS;
    }

    /** Cobalt logging feature flag. */
    @FeatureFlag boolean COBALT_LOGGING_ENABLED = false;

    /**
     * Returns the feature flag value for cobalt logging job. The cobalt logging feature will be
     * disabled if either the Global Kill Switch or the Cobalt Logging enabled flag is true.
     */
    default boolean getCobaltLoggingEnabled() {
        return !getGlobalKillSwitch() && COBALT_LOGGING_ENABLED;
    }

    /** U18 UX detention channel is enabled by default. */
    boolean IS_U18_UX_DETENTION_CHANNEL_ENABLED_DEFAULT = true;

    /** Returns whether the U18 UX detentional channel is enabled. */
    default boolean isU18UxDetentionChannelEnabled() {
        return IS_U18_UX_DETENTION_CHANNEL_ENABLED_DEFAULT;
    }

    /** U18 supervised account flow is enabled by default. */
    boolean IS_U18_SUPERVISED_ACCOUNT_ENABLED_DEFAULT = true;

    /** Returns whether the U18 supervised account is enabled. */
    default boolean isU18SupervisedAccountEnabled() {
        return IS_U18_SUPERVISED_ACCOUNT_ENABLED_DEFAULT;
    }

    long DEFAULT_AD_ID_FETCHER_TIMEOUT_MS = 50;

    /**
     * Returns configured timeout value for {@link
     * com.android.adservices.service.adselection.AdIdFetcher} logic.
     *
     * <p>The intended goal is to override this value for tests.
     *
     * <p>Returns Timeout in mills.
     */
    default long getAdIdFetcherTimeoutMs() {
        return DEFAULT_AD_ID_FETCHER_TIMEOUT_MS;
    }

    /**
     * Default value to determine whether AdServicesExtDataStorageService related APIs are enabled.
     */
    boolean DEFAULT_ENABLE_ADEXT_DATA_SERVICE_APIS = true;

    /** Returns whether AdServicesExtDataStorageService related APIs are enabled. */
    default boolean getEnableAdExtDataServiceApis() {
        return DEFAULT_ENABLE_ADEXT_DATA_SERVICE_APIS;
    }

    /**
     * Default value to determine how many logging events {@link AdServicesJobServiceLogger} should
     * upload to the server.
     *
     * <p>The value should be an integer in the range of [0, 100], where 100 is to log all events
     * and 0 is to log no events.
     */
    int DEFAULT_BACKGROUND_JOB_SAMPLING_LOGGING_RATE = 5;

    /**
     * Returns the sampling logging rate for {@link AdServicesJobServiceLogger} for logging events.
     */
    default int getBackgroundJobSamplingLoggingRate() {
        return DEFAULT_BACKGROUND_JOB_SAMPLING_LOGGING_RATE;
    }

    /** Default value of the timeout for AppSearch write operations */
    int DEFAULT_APPSEARCH_WRITE_TIMEOUT_MS = 3000;

    /**
     * Gets the value of the timeout for AppSearch write operations, in milliseconds.
     *
     * @return the timeout, in milliseconds, for AppSearch write operations
     */
    default int getAppSearchWriteTimeout() {
        return DEFAULT_APPSEARCH_WRITE_TIMEOUT_MS;
    }

    /** Default value of the timeout for AppSearch read operations */
    int DEFAULT_APPSEARCH_READ_TIMEOUT_MS = 750;

    /**
     * Gets the value of the timeout for AppSearch read operations, in milliseconds.
     *
     * @return the timeout, in milliseconds, for AppSearch read operations
     */
    default int getAppSearchReadTimeout() {
        return DEFAULT_APPSEARCH_READ_TIMEOUT_MS;
    }

    /** default value for get adservices common states enabled */
    boolean DEFAULT_IS_GET_ADSERVICES_COMMON_STATES_API_ENABLED = false;

    /** Returns if the get adservices common states service enabled. */
    default boolean isGetAdServicesCommonStatesApiEnabled() {
        return DEFAULT_IS_GET_ADSERVICES_COMMON_STATES_API_ENABLED;
    }

    /** Default value to determine whether ux related to the PAS Ux are enabled. */
    boolean DEFAULT_PAS_UX_ENABLED = false;

    /**
     * Returns whether features related to the PAS Ux are enabled. This flag has dependencies on
     * {@link #getEeaPasUxEnabled}. This method is the master control for PAS UX. If either EEA or
     * original PAS UX flag is on, then this method will return true.
     */
    default boolean getPasUxEnabled() {
        return DEFAULT_PAS_UX_ENABLED;
    }

    /** Default value to determine whether ux related to EEA PAS Ux are enabled. */
    boolean DEFAULT_EEA_PAS_UX_ENABLED = false;

    /** Returns whether features related to EEA PAS Ux are enabled. */
    default boolean getEeaPasUxEnabled() {
        return DEFAULT_EEA_PAS_UX_ENABLED;
    }

    /** Default value of the KAnon Sign/join feature flag */
    boolean FLEDGE_DEFAULT_KANON_SIGN_JOIN_FEATURE_ENABLED = false;

    /** Default value of KAnon Sign/Join feature in PersistAdSelection endpoint */
    boolean FLEDGE_DEFAULT_KANON_FEATURE_AUCTION_SERVER_ENABLED = false;

    /** Default value of KAnon sign/join feature in On Device AdSelection path */
    boolean FLEDGE_DEFAULT_KANON_FEATURE_ON_DEVICE_AUCTION_ENABLED = false;

    /** Default value of k-anon fetch server parameters url. */
    String FLEDGE_DEFAULT_KANON_FETCH_SERVER_PARAMS_URL = "";

    /** Default value of k-anon get challenge url. */
    String FLEDGE_DEFAULT_GET_CHALLENGE_URL = "";

    /** Default value of k-anon register client parameters url. */
    String FLEDGE_DEFAULT_KANON_REGISTER_CLIENT_PARAMETERS_URL = "";

    /** Default value of k-anon get tokens url. */
    String FLEDGE_DEFAULT_KANON_GET_TOKENS_URL = "";

    /** Default value of k-anon get tokens url. */
    String FLEDGE_DEFAULT_KANON_JOIN_URL = "";

    /** Default value of kanon join authority */
    String FLEDGE_DEFAULT_KANON_AUTHORIY_URL_JOIN = "";

    /** Default size of batch in a kanon sign call */
    int FLEDGE_DEFAULT_KANON_SIGN_BATCH_SIZE = 32;

    /** Default percentage of messages to be signed/joined immediately. */
    int FLEDGE_DEFAULT_KANON_PERCENTAGE_IMMEDIATE_SIGN_JOIN_CALLS = 10;

    /** Default ttl of kanon-messages stored in the database */
    long FLEDGE_DEFAULT_KANON_MESSAGE_TTL_SECONDS = 2 * 7 * 24 * 60 * 60; // 2 weeks

    /** Default time period of the KAnon Sign/Join background process */
    long FLEDGE_DEFAULT_KANON_BACKGROUND_JOB_TIME_PERIOD_MS = TimeUnit.HOURS.toMillis(24);

    /** Default number of messages processed in a single background process */
    int FLEDGE_DEFAULT_KANON_NUMBER_OF_MESSAGES_PER_BACKGROUND_PROCESS = 100;

    /** Default value for kanon background process flag */
    boolean FLEDGE_DEFAULT_KANON_BACKGROUND_PROCESS_ENABLED = false;

    /** Default value for kanon logging flag */
    boolean FLEDGE_DEFAULT_KANON_SIGN_JOIN_LOGGING_ENABLED = false;

    /** Default value for kanon key attestation feature flag */
    boolean FLEDGE_DEFAULT_KANON_KEY_ATTESTATION_ENABLED = false;

    /** Default value for kanon sign join set type */
    String FLEDGE_DEFAULT_KANON_SET_TYPE_TO_SIGN_JOIN = "fledge";

    /**
     * Default boolean for the field determining whether to run background job when the device's
     * battery is low.
     */
    boolean FLEDGE_DEFAULT_KANON_BACKGROUND_JOB_REQUIRES_BATTERY_NOT_LOW = true;

    /**
     * Default boolean for the field determining whether to run background job when the device is
     * not idle.
     */
    boolean FLEDGE_DEFAULT_KANON_BACKGROUND_JOB_REQUIRES_DEVICE_IDLE = true;

    /**
     * Default value for connection type required field for kanon background job. See {@link
     * JobInfo#NETWORK_TYPE_UNMETERED}
     */
    int FLEDGE_DEFAULT_KANON_BACKGROUND_JOB_CONNECTION_TYPE = 2;

    /** Default value for kanon http client connect timeout in milliseconds */
    int FLEDGE_DEFAULT_KANON_HTTP_CLIENT_TIMEOUT_IN_MS = 5000;

    /**
     * This is a feature flag for KAnon Sign/Join feature.
     *
     * @return {@code true} if the feature is enabled, otherwise returns {@code false}.
     */
    default boolean getFledgeKAnonSignJoinFeatureEnabled() {
        return getFledgeAuctionServerEnabled() && FLEDGE_DEFAULT_KANON_SIGN_JOIN_FEATURE_ENABLED;
    }

    /**
     * This is a feature flag for KAnon Sign/Join feature on the on device ad selection path.
     *
     * @return {@code true} if it's enabled, otherwise returns {@code false}.
     */
    default boolean getFledgeKAnonSignJoinFeatureOnDeviceAuctionEnabled() {
        return FLEDGE_DEFAULT_KANON_FEATURE_ON_DEVICE_AUCTION_ENABLED;
    }

    /**
     * This is a feature flag for KAnon Sign/Join feature on the server auction path.
     *
     * @return {@code true} if it's enabled, otherwise returns {@code false}.
     */
    default boolean getFledgeKAnonSignJoinFeatureAuctionServerEnabled() {
        return FLEDGE_DEFAULT_KANON_FEATURE_AUCTION_SERVER_ENABLED;
    }

    /**
     * This method returns the url that needs to be used to fetch server parameters during k-anon
     * sign call
     *
     * @return kanon fetch server params url.
     */
    default String getFledgeKAnonFetchServerParamsUrl() {
        return FLEDGE_DEFAULT_KANON_FETCH_SERVER_PARAMS_URL;
    }

    /**
     * This method returns the url that needs to be used to fetch server parameters during k-anon
     * sign call
     *
     * @return kanon fetch server params url.
     */
    default String getFledgeKAnonGetChallengeUrl() {
        return FLEDGE_DEFAULT_GET_CHALLENGE_URL;
    }

    /**
     * This method returns the url that needs to be used to register client parameters during k-anon
     * sign call.
     *
     * @return register client params url
     */
    default String getFledgeKAnonRegisterClientParametersUrl() {
        return FLEDGE_DEFAULT_KANON_REGISTER_CLIENT_PARAMETERS_URL;
    }

    /**
     * This method returns the url that needs to be used to fetch Tokens during k-anon sign call.
     *
     * @return default value of get tokens url
     */
    default String getFledgeKAnonGetTokensUrl() {
        return FLEDGE_DEFAULT_KANON_GET_TOKENS_URL;
    }

    /**
     * This method returns the url that needs to be used to make k-anon join call.
     *
     * @return default value of get tokens url
     */
    default String getFledgeKAnonJoinUrl() {
        return FLEDGE_DEFAULT_KANON_JOIN_URL;
    }

    /**
     * This method returns the value of batch size in a batch kanon sign call
     *
     * @return k-anon sign batch size
     */
    default int getFledgeKAnonSignBatchSize() {
        return FLEDGE_DEFAULT_KANON_SIGN_BATCH_SIZE;
    }

    /**
     * This method returns an integer tha represents the percentage of the messages that needs to be
     * signed/joined immediately.
     */
    default int getFledgeKAnonPercentageImmediateSignJoinCalls() {
        return FLEDGE_DEFAULT_KANON_PERCENTAGE_IMMEDIATE_SIGN_JOIN_CALLS;
    }

    /**
     * This method returns the max ttl of a KAnonMessage in the Database. This is used to determine
     * when to clean up the old KAnonMessages from the database
     *
     * @return kanon max ttl for a kano message
     */
    default long getFledgeKAnonMessageTtlSeconds() {
        return FLEDGE_DEFAULT_KANON_MESSAGE_TTL_SECONDS;
    }

    /** This method returns the number of k-anon sign/join background processes per day. */
    default long getFledgeKAnonBackgroundProcessTimePeriodInMs() {
        return FLEDGE_DEFAULT_KANON_BACKGROUND_JOB_TIME_PERIOD_MS;
    }

    /**
     * This method returns the number of k-anon messages to be processed per background process run.
     */
    default int getFledgeKAnonMessagesPerBackgroundProcess() {
        return FLEDGE_DEFAULT_KANON_NUMBER_OF_MESSAGES_PER_BACKGROUND_PROCESS;
    }

    /**
     * This method returns {@code true} if the kanon background process is enabled, {@code false}
     * otherwise.
     */
    default boolean getFledgeKAnonBackgroundProcessEnabled() {
        return getFledgeKAnonSignJoinFeatureEnabled()
                && FLEDGE_DEFAULT_KANON_BACKGROUND_PROCESS_ENABLED;
    }

    /**
     * This method returns {@code true} if the telemetry logging for kanon is enabled, {@code false}
     * otherwise.
     */
    default boolean getFledgeKAnonLoggingEnabled() {
        return getFledgeKAnonSignJoinFeatureEnabled()
                && FLEDGE_DEFAULT_KANON_SIGN_JOIN_LOGGING_ENABLED;
    }

    /**
     * This method return {@code true} if the KAnon Key attestaion is enabled, {@code false}
     * otherwise.
     */
    default boolean getFledgeKAnonKeyAttestationEnabled() {
        return getFledgeKAnonSignJoinFeatureEnabled()
                && FLEDGE_DEFAULT_KANON_KEY_ATTESTATION_ENABLED;
    }

    /**
     * This method returns the type of set we need to join during kanon sign join process. eg: In
     * the following example, fledge is the set type to join. "types/fledge/set/hashset"
     */
    default String getFledgeKAnonSetTypeToSignJoin() {
        return FLEDGE_DEFAULT_KANON_SET_TYPE_TO_SIGN_JOIN;
    }

    /**
     * This method returns the url authority that will be used in the {@link
     * com.android.adservices.service.common.bhttp.BinaryHttpMessage}. This BinaryHttpMessage is
     * sent as part of kanon http join request.
     */
    default String getFledgeKAnonUrlAuthorityToJoin() {
        return FLEDGE_DEFAULT_KANON_AUTHORIY_URL_JOIN;
    }

    /** This method returns the value for kanon http client connect timeout in milliseconds. */
    default int getFledgeKanonHttpClientTimeoutInMs() {
        return FLEDGE_DEFAULT_KANON_HTTP_CLIENT_TIMEOUT_IN_MS;
    }

    /**
     * This method returns the boolean field determining whether to run background job when the
     * device's battery is low.
     */
    default boolean getFledgeKAnonBackgroundJobRequiresBatteryNotLow() {
        return FLEDGE_DEFAULT_KANON_BACKGROUND_JOB_REQUIRES_BATTERY_NOT_LOW;
    }

    /**
     * This method returns the boolean field determining whether to run background job when the
     * device is not idle.
     */
    default boolean getFledgeKAnonBackgroundJobRequiresDeviceIdle() {
        return FLEDGE_DEFAULT_KANON_BACKGROUND_JOB_REQUIRES_DEVICE_IDLE;
    }

    /**
     * This method returns the value for connection type required field for kanon background job.
     * See {@link JobInfo#NETWORK_TYPE_UNMETERED}
     */
    default int getFledgeKanonBackgroundJobConnectionType() {
        return FLEDGE_DEFAULT_KANON_BACKGROUND_JOB_CONNECTION_TYPE;
    }

    /*
     * The allow-list for PP APIs. This list has the list of app package names that we allow
     * using PP APIs.
     * App Package Name that does not belong to this allow-list will not be able to use PP APIs.
     * If this list has special value "*", then all package names are allowed.
     * There must be not any empty space between comma.
     */
    String GET_ADSERVICES_COMMON_STATES_ALLOW_LIST = "com.android.adservices.tests.ui.common";

    /**
     * Returns bypass List for Get AdServices Common States app signature check. Apps with package
     * name on this list will bypass the signature check
     */
    default String getAdServicesCommonStatesAllowList() {
        return GET_ADSERVICES_COMMON_STATES_ALLOW_LIST;
    }

    /** Default value for the base64 encoded Job Policy proto for AdServices. */
    @ConfigFlag String AD_SERVICES_MODULE_JOB_POLICY = "";

    /** Returns the base64 encoded Job Policy proto for AdServices. */
    default String getAdServicesModuleJobPolicy() {
        return AD_SERVICES_MODULE_JOB_POLICY;
    }

    /**
     * Default value for the enabled status of the {@link
     * com.android.adservices.service.common.RetryStrategy}.
     */
    boolean DEFAULT_AD_SERVICES_RETRY_STRATEGY_ENABLED = false;

    /**
     * Returns the enabled status of the AdServices {@link
     * com.android.adservices.service.common.RetryStrategy}.
     */
    default boolean getAdServicesRetryStrategyEnabled() {
        return DEFAULT_AD_SERVICES_RETRY_STRATEGY_ENABLED;
    }

    /**
     * Default value for the max number of retry attempts for {@link
     * com.android.adservices.service.js.JSScriptEngine}
     */
    int DEFAULT_AD_SERVICES_JS_SCRIPT_ENGINE_MAX_RETRY_ATTEMPTS = 1;

    /**
     * Returns the max number of retry attempts for {@link
     * com.android.adservices.service.js.JSScriptEngine}.
     */
    default int getAdServicesJsScriptEngineMaxRetryAttempts() {
        return DEFAULT_AD_SERVICES_JS_SCRIPT_ENGINE_MAX_RETRY_ATTEMPTS;
    }

    /** Default value for consent manager v2 flag */
    boolean DEFAULT_ENABLE_CONSENT_MANAGER_V2 = false;

    /** Gets the Consent Manager V2 enable flag. */
    default boolean getEnableConsentManagerV2() {
        return DEFAULT_ENABLE_CONSENT_MANAGER_V2;
    }

    /** Protected app signals API extended metrics flag. */
    boolean PAS_EXTENDED_METRICS_ENABLED = false;

    /** Returns whether the PAS API extended metrics is enabled. */
    default boolean getPasExtendedMetricsEnabled() {
        return PAS_EXTENDED_METRICS_ENABLED;
    }

    /** Protected app signals API product metrics v1 flag. */
    @FeatureFlag boolean PAS_PRODUCT_METRICS_V1_ENABLED = false;

    /** Returns whether to enable PAS API product metrics v1. */
    default boolean getPasProductMetricsV1Enabled() {
        return PAS_PRODUCT_METRICS_V1_ENABLED;
    }

    /** Default enablement for applying SPE (Scheduling Policy Engine) to pilot jobs. */
    @FeatureFlag boolean DEFAULT_SPE_ON_PILOT_JOBS_ENABLED = false;

    /** Returns the default enablement of applying SPE (Scheduling Policy Engine) to pilot jobs. */
    default boolean getSpeOnPilotJobsEnabled() {
        return DEFAULT_SPE_ON_PILOT_JOBS_ENABLED;
    }

    /** A feature flag to enable the use of Enrollment API Based Schema. */
    @FeatureFlag boolean ENROLLMENT_API_BASED_SCHEMA_ENABLED = false;

    /**
     * @return the enabled status for enrollment api based schema.
     */
    default boolean getEnrollmentApiBasedSchemaEnabled() {
        return ENROLLMENT_API_BASED_SCHEMA_ENABLED;
    }

    /** Whether to enable shared database schema version 4 */
    @FeatureFlag boolean SHARED_DATABASE_SCHEMA_VERSION_4_ENABLED = false;

    /**
     * @return if to enable shared database schema version 4.
     */
    default boolean getSharedDatabaseSchemaVersion4Enabled() {
        return SHARED_DATABASE_SCHEMA_VERSION_4_ENABLED;
    }

    /** Default value for table region fix flag. */
    boolean DEFAULT_ENABLE_TABLET_REGION_FIX = false;

    /**
     * @return if to enable tablet region fix.
     */
    default boolean getEnableTabletRegionFix() {
        return DEFAULT_ENABLE_TABLET_REGION_FIX;
    }

    /** Default value for custom error code sampling enabled. */
    @FeatureFlag boolean DEFAULT_CUSTOM_ERROR_CODE_SAMPLING_ENABLED = false;

    /** Returns {@code boolean} determining whether custom error code sampling is enabled. */
    default boolean getCustomErrorCodeSamplingEnabled() {
        return DEFAULT_CUSTOM_ERROR_CODE_SAMPLING_ENABLED;
    }

    /** Read timeout for downloading PAS encoding scripts in milliseconds */
    int DEFAULT_PAS_SCRIPT_DOWNLOAD_READ_TIMEOUT_MS = 5000;

    /**
     * @return Read timeout for downloading PAS encoding scripts in milliseconds
     */
    default int getPasScriptDownloadReadTimeoutMs() {
        return DEFAULT_PAS_SCRIPT_DOWNLOAD_READ_TIMEOUT_MS;
    }

    /** Connection timeout for downloading PAS encoding scripts in milliseconds */
    int DEFAULT_PAS_SCRIPT_DOWNLOAD_CONNECTION_TIMEOUT_MS = 5000;

    /**
     * @return Connection timeout for downloading PAS encoding scripts in milliseconds
     */
    default int getPasScriptDownloadConnectionTimeoutMs() {
        return DEFAULT_PAS_SCRIPT_DOWNLOAD_CONNECTION_TIMEOUT_MS;
    }

    /** Read timeout for downloading PAS signals in milliseconds */
    int DEFAULT_PAS_SIGNALS_DOWNLOAD_READ_TIMEOUT_MS = 5000;

    /**
     * @return Read timeout for downloading PAS signals in milliseconds
     */
    default int getPasSignalsDownloadReadTimeoutMs() {
        return DEFAULT_PAS_SIGNALS_DOWNLOAD_READ_TIMEOUT_MS;
    }

    /** Connection timeout for downloading PAS encoding signals in milliseconds */
    int DEFAULT_PAS_SIGNALS_DOWNLOAD_CONNECTION_TIMEOUT_MS = 5000;

    /**
     * @return Connection timeout for downloading PAS signals in milliseconds
     */
    default int getPasSignalsDownloadConnectionTimeoutMs() {
        return DEFAULT_PAS_SIGNALS_DOWNLOAD_CONNECTION_TIMEOUT_MS;
    }

    /** Timeout for executing PAS encoding scripts in milliseconds */
    int DEFAULT_PAS_SCRIPT_EXECUTION_TIMEOUT_MS = 5000;

    /**
     * @return Timeout for executing PAS encoding scripts in milliseconds
     */
    default int getPasScriptExecutionTimeoutMs() {
        return DEFAULT_PAS_SCRIPT_EXECUTION_TIMEOUT_MS;
    }

    /** Default enablement for applying SPE (Scheduling Policy Engine) to the second pilot jobs. */
    @FeatureFlag boolean DEFAULT_SPE_ON_PILOT_JOBS_BATCH_2_ENABLED = false;

    /**
     * Returns the default enablement of applying SPE (Scheduling Policy Engine) to the second pilot
     * jobs.
     */
    default boolean getSpeOnPilotJobsBatch2Enabled() {
        return DEFAULT_SPE_ON_PILOT_JOBS_BATCH_2_ENABLED;
    }

    /**
     * Default enablement for applying SPE (Scheduling Policy Engine) to {@code EpochJobService}.
     */
    @FeatureFlag boolean DEFAULT_SPE_ON_EPOCH_JOB_ENABLED = false;

    /**
     * Returns the default enablement of applying SPE (Scheduling Policy Engine) to {@code
     * EpochJobService}.
     */
    default boolean getSpeOnEpochJobEnabled() {
        return DEFAULT_SPE_ON_EPOCH_JOB_ENABLED;
    }

    /**
     * Default enablement for applying SPE (Scheduling Policy Engine) to {@code
     * BackgroundFetchJobService}.
     */
    @FeatureFlag boolean DEFAULT_SPE_ON_BACKGROUND_FETCH_JOB_ENABLED = false;

    /**
     * Returns the default enablement of applying SPE (Scheduling Policy Engine) to {@code
     * BackgroundFetchJobService}.
     */
    default boolean getSpeOnBackgroundFetchJobEnabled() {
        return DEFAULT_SPE_ON_BACKGROUND_FETCH_JOB_ENABLED;
    }

    /**
     * Default enablement for applying SPE (Scheduling Policy Engine) to {@code
     * AsyncRegistrationFallbackJobService}.
     */
    @FeatureFlag boolean DEFAULT_SPE_ON_ASYNC_REGISTRATION_FALLBACK_JOB_ENABLED = false;

    /**
     * Returns the default enablement of applying SPE (Scheduling Policy Engine) to {@code
     * AsyncRegistrationFallbackJobService}.
     */
    default boolean getSpeOnAsyncRegistrationFallbackJobEnabled() {
        return DEFAULT_SPE_ON_ASYNC_REGISTRATION_FALLBACK_JOB_ENABLED;
    }

    /** Default value for the enablement the new apis for business logic migration. */
    @FeatureFlag boolean DEFAULT_ADSERVICES_CONSENT_BUSINESS_LOGIC_MIGRATION_ENABLED = false;

    /** Returns the default value of the enablement of adservices business logic migration. */
    default boolean getAdServicesConsentBusinessLogicMigrationEnabled() {
        return DEFAULT_ADSERVICES_CONSENT_BUSINESS_LOGIC_MIGRATION_ENABLED;
    }

    /** Default value for the enablement the R notification default consent fix. */
    @FeatureFlag boolean DEFAULT_R_NOTIFICATION_DEFAULT_CONSENT_FIX_ENABLED = false;

    /** Returns the default value for the enablement the R notification default consent fix */
    default boolean getRNotificationDefaultConsentFixEnabled() {
        return DEFAULT_R_NOTIFICATION_DEFAULT_CONSENT_FIX_ENABLED;
    }

    /** Enrollment Manifest File URL, used to provide proto file for MDD download. */
    @ConfigFlag String MDD_DEFAULT_ENROLLMENT_MANIFEST_FILE_URL = "";

    /**
     * @return default Enrollment Manifest File URL
     */
    default String getMddEnrollmentManifestFileUrl() {
        return MDD_DEFAULT_ENROLLMENT_MANIFEST_FILE_URL;
    }

    /** Feature flag to ramp up use of enrollment proto file. */
    @FeatureFlag boolean DEFAULT_ENROLLMENT_PROTO_FILE_ENABLED = false;

    /**
     * @return whether to enable use of enrollment proto file.
     */
    default boolean getEnrollmentProtoFileEnabled() {
        return DEFAULT_ENROLLMENT_PROTO_FILE_ENABLED;
    }

    /** Protected app signals encoding job performance improvements flag. */
    @FeatureFlag boolean PAS_ENCODING_JOB_IMPROVEMENTS_ENABLED = false;

    /** Returns whether the PAS encoding job performance improvements are enabled. */
    default boolean getPasEncodingJobImprovementsEnabled() {
        return PAS_ENCODING_JOB_IMPROVEMENTS_ENABLED;
    }

    /**
     * Default value to determine whether {@link
     * com.android.adservices.service.adid.AdIdCacheManager} cache is timeout.
     */
    @ConfigFlag long DEFAULT_ADID_CACHE_TTL_MS = 0;

    /**
     * Returns {@link com.android.adservices.service.adid.AdIdCacheManager} ttl(time to live) time.
     * It will be used to expire the cached adid.
     *
     * <ul>
     *   <li>if value is 0, the cache has no ttl.
     *   <li>otherwise, check the stored time and current time inverval, if larger than the ttl,
     *       refetch the adid.
     * </ul>
     *
     * Returns ttl of {@link com.android.adservices.service.adid.AdIdCacheManager}.
     */
    default long getAdIdCacheTtlMs() {
        return DEFAULT_ADID_CACHE_TTL_MS;
    }

    /** Feature flag to ramp up use of package deny service. */
    @FeatureFlag boolean DEFAULT_ENABLE_PACKAGE_DENY_SERVICE = false;

    /**
     * @return whether to enable use of package deny service.
     */
    default boolean getEnablePackageDenyService() {
        return DEFAULT_ENABLE_PACKAGE_DENY_SERVICE;
    }

    /** Feature flag to ramp up use of mdd package deny proto file. */
    @FeatureFlag boolean DEFAULT_ENABLE_PACKAGE_DENY_MDD = false;

    /**
     * @return whether to enable use of package deny mdd file.
     */
    default boolean getEnablePackageDenyMdd() {
        return DEFAULT_ENABLE_PACKAGE_DENY_MDD;
    }

    /** Feature flag to ramp up use of package deny service on adding a package. */
    @FeatureFlag boolean DEFAULT_ENABLE_PACKAGE_DENY_JOB_ON_PACKAGE_ADD = false;

    /**
     * @return whether to enable use of package deny service on adding a package.
     */
    default boolean getEnablePackageDenyJobOnPackageAdd() {
        return DEFAULT_ENABLE_PACKAGE_DENY_JOB_ON_PACKAGE_ADD;
    }

    /** Feature flag to ramp up use of package deny preprocessing background job. */
    @FeatureFlag boolean DEFAULT_ENABLE_PACKAGE_DENY_BG_JOB = false;

    /**
     * @return whether to enable use of package deny preprocessing background job.
     */
    default boolean getEnablePackageDenyBgJob() {
        return DEFAULT_ENABLE_PACKAGE_DENY_BG_JOB;
    }

    /** Feature flag to ramp up use of package deny preprocessing on mdd file download. */
    @FeatureFlag boolean DEFAULT_ENABLE_PACKAGE_DENY_JOB_ON_MDD_DOWNLOAD = false;

    /**
     * @return whether to enable use of package deny preprocessing job on mdd file download.
     */
    default boolean getEnablePackageDenyJobOnMddDownload() {
        return DEFAULT_ENABLE_PACKAGE_DENY_JOB_ON_MDD_DOWNLOAD;
    }

    /** MDD package deny registry manifest url. */
    @ConfigFlag String DEFAULT_MDD_PACKAGE_DENY_REGISTRY_MANIFEST_FILE_URL = "";

    /** Returns MDD package deny registry manifest url. */
    default String getMddPackageDenyRegistryManifestFileUrl() {
        return DEFAULT_MDD_PACKAGE_DENY_REGISTRY_MANIFEST_FILE_URL;
    }

    /**
     * Feature flag to enable enrollment configuration v3 delivery (mdd download + database
     * population).
     */
    @FeatureFlag boolean DEFAULT_ENABLE_ENROLLMENT_CONFIG_V3_DB = false;

    /** Enables enrollment configuration v3 delivery (mdd download + database population). */
    default boolean getEnableEnrollmentConfigV3Db() {
        return DEFAULT_ENABLE_ENROLLMENT_CONFIG_V3_DB;
    }

    /** Flag to use configurations manager to query enrollment data. */
    @FeatureFlag boolean DEFAULT_USE_CONFIGS_MANAGER_TO_QUERY_ENROLLMENT = false;

    default boolean getUseConfigsManagerToQueryEnrollment() {
        return DEFAULT_USE_CONFIGS_MANAGER_TO_QUERY_ENROLLMENT;
    }

    @FeatureFlag boolean DEFAULT_PACKAGE_DENY_ENABLE_INSTALLED_PACKAGE_FILTER = false;

    /**
     * @return whether to enable use of filtering of deny list based on installed packages
     */
    default boolean getPackageDenyEnableInstalledPackageFilter() {
        return DEFAULT_PACKAGE_DENY_ENABLE_INSTALLED_PACKAGE_FILTER;
    }

    @FeatureFlag long DEFAULT_PACKAGE_DENY_BACKGROUND_JOB_PERIOD_MILLIS = 43_200_000; // 12 hours

    /**
     * @return package dny background job period in millis
     */
    default long getPackageDenyBackgroundJobPeriodMillis() {
        return DEFAULT_PACKAGE_DENY_BACKGROUND_JOB_PERIOD_MILLIS;
    }

    /** Feature flag to enable AtomicFileDataStore update API for adservices apk. */
    @FeatureFlag boolean DEFAULT_ENABLE_ATOMIC_FILE_DATASTORE_BATCH_UPDATE_API = false;

    /** Returns whether atomic file datastore batch update Api is enabled. */
    default boolean getEnableAtomicFileDatastoreBatchUpdateApi() {
        return DEFAULT_ENABLE_ATOMIC_FILE_DATASTORE_BATCH_UPDATE_API;
    }

    /** Feature flag to enable Ad Id migration. */
    @FeatureFlag boolean DEFAULT_AD_ID_MIGRATION_ENABLED = false;

    /** Returns whether Ad Id migration is enabled. */
    default boolean getAdIdMigrationEnabled() {
        return DEFAULT_AD_ID_MIGRATION_ENABLED;
    }

    boolean DEFAULT_ENABLE_REPORT_EVENT_FOR_COMPONENT_SELLER = false;

    /** Returns if component seller as one of the destination in report event is enabled. */
    default boolean getEnableReportEventForComponentSeller() {
        return DEFAULT_ENABLE_REPORT_EVENT_FOR_COMPONENT_SELLER;
    }

    boolean DEFAULT_ENABLE_WINNING_SELLER_ID_IN_AD_SELECTION_OUTCOME = false;

    /** Returns if the winning seller id in AdSelectionOutcome is enabled. */
    default boolean getEnableWinningSellerIdInAdSelectionOutcome() {
        return DEFAULT_ENABLE_WINNING_SELLER_ID_IN_AD_SELECTION_OUTCOME;
    }

    boolean DEFAULT_PROD_DEBUG_IN_AUCTION_SERVER = false;

    /** Returns if the prod debug feature is enabled for server auctions. */
    default boolean getEnableProdDebugInAuctionServer() {
        return DEFAULT_PROD_DEBUG_IN_AUCTION_SERVER;
    }

    /**
     * Feature flag to enable the Android Trace to collect AdServices latency metrics in Crystalball
     * tests {@code RbATrace}.
     */
    @FeatureFlag boolean DEFAULT_ENABLE_RB_ATRACE = false;

    /** Returns whether the AdServices latency metrics {@code RbATrace} is enabled. */
    default boolean getEnableRbAtrace() {
        return DEFAULT_ENABLE_RB_ATRACE;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // NOTE: Add new getters either above this comment, or closer to the relevant getters         //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** Dump some debug info for the flags */
    default void dump(PrintWriter writer, @Nullable String[] args) {}

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // NOTE: do NOT add new getters down here                                                     //
    ////////////////////////////////////////////////////////////////////////////////////////////////
}
