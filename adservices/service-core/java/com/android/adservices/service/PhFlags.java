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

import static com.android.adservices.service.DeviceConfigFlagsHelper.getDeviceConfigFlag;
import static com.android.adservices.service.FlagsConstants.KEY_ADSERVICES_CONSENT_BUSINESS_LOGIC_MIGRATION_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_AD_SERVICES_JS_SCRIPT_ENGINE_MAX_RETRY_ATTEMPTS;
import static com.android.adservices.service.FlagsConstants.KEY_AD_SERVICES_MODULE_JOB_POLICY;
import static com.android.adservices.service.FlagsConstants.KEY_AD_SERVICES_RETRY_STRATEGY_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_CONFIG_DELIVERY__ENABLE_ENROLLMENT_CONFIG_V3_DB;
import static com.android.adservices.service.FlagsConstants.KEY_CONFIG_DELIVERY__USE_CONFIGS_MANAGER_TO_QUERY_ENROLLMENT;
import static com.android.adservices.service.FlagsConstants.KEY_CUSTOM_ERROR_CODE_SAMPLING_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_CONSENT_MANAGER_V2;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_MDD_ENCRYPTION_KEYS;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_TABLET_REGION_FIX;
import static com.android.adservices.service.FlagsConstants.KEY_ENCODED_ERROR_CODE_LIST_PER_SAMPLE_INTERVAL;
import static com.android.adservices.service.FlagsConstants.KEY_ENCRYPTION_KEY_JOB_PERIOD_MS;
import static com.android.adservices.service.FlagsConstants.KEY_ENCRYPTION_KEY_JOB_REQUIRED_NETWORK_TYPE;
import static com.android.adservices.service.FlagsConstants.KEY_ENROLLMENT_API_BASED_SCHEMA_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_ENROLLMENT_PROTO_FILE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_ON_EMPTY_DB_AND_IN_ADVANCE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_ENABLE_PROD_DEBUG_IN_SERVER_AUCTION;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_KANON_BACKGROUND_JOB_REQUIRES_BATTERY_NOT_LOW;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_KANON_BACKGROUND_JOB_REQUIRES_DEVICE_IDLE;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_KANON_BACKGROUND_JOB_TYPE_OF_CONNECTION;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_KANON_BACKGROUND_PROCESS_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_KANON_HTTP_CLIENT_TIMEOUT;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_KANON_JOIN_URL_AUTHORIY;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_KANON_KEY_ATTESTATION_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_KANON_NUMBER_OF_MESSAGES_PER_BACKGROUND_PROCESS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_KANON_SET_TYPE_TO_SIGN_JOIN;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_KANON_SIGN_JOIN_LOGGING_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_FALLBACK_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_JOB_SCHEDULING_LOGGING_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_JOB_SCHEDULING_LOGGING_SAMPLING_RATE;
import static com.android.adservices.service.FlagsConstants.KEY_MDD_ENCRYPTION_KEYS_MANIFEST_FILE_URL;
import static com.android.adservices.service.FlagsConstants.KEY_MDD_ENROLLMENT_MANIFEST_FILE_URL;
import static com.android.adservices.service.FlagsConstants.KEY_MDD_LOGGER_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERSISTED;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_REQUIRED_NETWORK_TYPE;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_AGGREGATE_REPORTING_JOB_PERSISTED;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_AGGREGATE_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_AGGREGATE_REPORTING_JOB_REQUIRED_NETWORK_TYPE;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_PERSISTED;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_REQUIRED_BATTERY_NOT_LOW;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_REQUIRED_NETWORK_TYPE;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ASYNC_REGISTRATION_QUEUE_JOB_PERSISTED;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ASYNC_REGISTRATION_QUEUE_JOB_REQUIRED_NETWORK_TYPE;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_PERSISTED;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ATTRIBUTION_JOB_PERSISTED;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ATTRIBUTION_JOB_TRIGGERING_DELAY_MS;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_EVENT;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_NAVIGATION;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_EVENT;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_NAVIGATION;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_PERSISTED;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_REQUIRED_NETWORK_TYPE;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_DEBUG_REPORTING_JOB_REQUIRED_NETWORK_TYPE;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_DELETE_EXPIRED_JOB_PERIOD_MS;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_DELETE_EXPIRED_JOB_PERSISTED;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_DELETE_EXPIRED_JOB_REQUIRES_DEVICE_IDLE;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_DELETE_UNINSTALLED_JOB_PERIOD_MS;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_DELETE_UNINSTALLED_JOB_PERSISTED;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ENABLE_EVENT_TRIGGER_DEBUG_SIGNAL_FOR_COARSE_DESTINATION;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ENABLE_FAKE_REPORT_TRIGGER_TIME;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ENABLE_INSTALL_ATTRIBUTION_ON_S;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ENABLE_MIN_REPORT_LIFESPAN_FOR_UNINSTALL;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ENABLE_REINSTALL_REATTRIBUTION;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ENABLE_SEPARATE_DEBUG_REPORT_TYPES_FOR_ATTRIBUTION_RATE_LIMIT;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ENABLE_SESSION_STABLE_KILL_SWITCHES;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ENABLE_TRIGGER_DEBUG_SIGNAL;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERSISTED;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_REQUIRED_NETWORK_TYPE;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_EVENT_REPORTING_JOB_PERSISTED;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_EVENT_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_EVENT_REPORTING_JOB_REQUIRED_NETWORK_TYPE;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_PERSISTED;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_REQUIRED_NETWORK_TYPE;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_MAX_ATTRIBUTION_SCOPES_PER_SOURCE;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_MAX_ATTRIBUTION_SCOPE_LENGTH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_MAX_LENGTH_PER_BUDGET_NAME;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_MAX_NAMED_BUDGETS_PER_SOURCE_REGISTRATION;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_REPORTING_JOB_PERSISTED;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_REPORTING_JOB_REQUIRED_NETWORK_TYPE;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_REPORTING_JOB_SERVICE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_TRIGGER_DEBUG_SIGNAL_PROBABILITY_FOR_FAKE_REPORTS;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_PERSISTED;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_VERBOSE_DEBUG_REPORTING_JOB_REQUIRED_NETWORK_TYPE;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_ENCODING_JOB_IMPROVEMENTS_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_EXTENDED_METRICS_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_PRODUCT_METRICS_V1_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_SCRIPT_DOWNLOAD_CONNECTION_TIMEOUT_MS;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_SCRIPT_DOWNLOAD_READ_TIMEOUT_MS;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_SCRIPT_EXECUTION_TIMEOUT_MS;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_SIGNALS_DOWNLOAD_CONNECTION_TIMEOUT_MS;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_SIGNALS_DOWNLOAD_READ_TIMEOUT_MS;
import static com.android.adservices.service.FlagsConstants.KEY_R_NOTIFICATION_DEFAULT_CONSENT_FIX_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_SHARED_DATABASE_SCHEMA_VERSION_4_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_SPE_ON_ASYNC_REGISTRATION_FALLBACK_JOB_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_SPE_ON_BACKGROUND_FETCH_JOB_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_SPE_ON_EPOCH_JOB_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_SPE_ON_PILOT_JOBS_BATCH_2_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_SPE_ON_PILOT_JOBS_ENABLED;
import static com.android.adservices.service.FlagsConstants.MAX_PERCENTAGE;

import static java.lang.Float.parseFloat;

import android.annotation.NonNull;
import android.os.SystemProperties;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.LogUtil;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableList;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Flags Implementation that delegates to DeviceConfig. */
// TODO(b/228037065): Add validation logics for Feature flags read from PH.
public final class PhFlags implements Flags {

    private static final PhFlags sSingleton = new PhFlags();

    /** Returns the singleton instance of the PhFlags. */
    @NonNull
    static PhFlags getInstance() {
        return sSingleton;
    }

    @Override
    public long getAsyncRegistrationJobQueueIntervalMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ASYNC_REGISTRATION_JOB_QUEUE_INTERVAL_MS,
                ASYNC_REGISTRATION_JOB_QUEUE_INTERVAL_MS);
    }

    @Override
    public long getTopicsEpochJobPeriodMs() {
        long topicsEpochJobPeriodMs =
                getDeviceConfigFlag(
                        FlagsConstants.KEY_TOPICS_EPOCH_JOB_PERIOD_MS, TOPICS_EPOCH_JOB_PERIOD_MS);
        if (topicsEpochJobPeriodMs <= 0) {
            throw new IllegalArgumentException("topicsEpochJobPeriodMs should > 0");
        }
        return topicsEpochJobPeriodMs;
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public long getTopicsEpochJobFlexMs() {
        long topicsEpochJobFlexMs =
                SystemProperties.getLong(
                        getSystemPropertyName(FlagsConstants.KEY_TOPICS_EPOCH_JOB_FLEX_MS),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_TOPICS_EPOCH_JOB_FLEX_MS,
                                TOPICS_EPOCH_JOB_FLEX_MS));
        if (topicsEpochJobFlexMs <= 0) {
            throw new IllegalArgumentException("topicsEpochJobFlexMs should > 0");
        }
        return topicsEpochJobFlexMs;
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public int getTopicsPercentageForRandomTopic() {
        int topicsPercentageForRandomTopic =
                SystemProperties.getInt(
                        getSystemPropertyName(
                                FlagsConstants.KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC,
                                TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC));
        if (topicsPercentageForRandomTopic < 0 || topicsPercentageForRandomTopic > MAX_PERCENTAGE) {
            throw new IllegalArgumentException(
                    "topicsPercentageForRandomTopic should be between 0 and 100");
        }
        return topicsPercentageForRandomTopic;
    }

    @Override
    public int getTopicsNumberOfTopTopics() {
        int topicsNumberOfTopTopics =
                getDeviceConfigFlag(
                        FlagsConstants.KEY_TOPICS_NUMBER_OF_TOP_TOPICS,
                        TOPICS_NUMBER_OF_TOP_TOPICS);
        if (topicsNumberOfTopTopics < 0) {
            throw new IllegalArgumentException("topicsNumberOfTopTopics should >= 0");
        }

        return topicsNumberOfTopTopics;
    }

    @Override
    public int getTopicsNumberOfRandomTopics() {
        int topicsNumberOfTopTopics =
                getDeviceConfigFlag(
                        FlagsConstants.KEY_TOPICS_NUMBER_OF_RANDOM_TOPICS,
                        TOPICS_NUMBER_OF_RANDOM_TOPICS);
        if (topicsNumberOfTopTopics < 0) {
            throw new IllegalArgumentException("topicsNumberOfTopTopics should >= 0");
        }

        return topicsNumberOfTopTopics;
    }

    @Override
    public int getTopicsNumberOfLookBackEpochs() {
        int topicsNumberOfLookBackEpochs =
                getDeviceConfigFlag(
                        FlagsConstants.KEY_TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS,
                        TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS);
        if (topicsNumberOfLookBackEpochs < 1) {
            throw new IllegalArgumentException("topicsNumberOfLookBackEpochs should  >= 1");
        }

        return topicsNumberOfLookBackEpochs;
    }

    @Override
    public float getTopicsPrivacyBudgetForTopicIdDistribution() {
        float topicsPrivacyBudgetForTopicIdDistribution =
                getDeviceConfigFlag(
                        FlagsConstants.KEY_TOPICS_PRIVACY_BUDGET_FOR_TOPIC_ID_DISTRIBUTION,
                        TOPICS_PRIVACY_BUDGET_FOR_TOPIC_ID_DISTRIBUTION);

        if (topicsPrivacyBudgetForTopicIdDistribution <= 0) {
            throw new IllegalArgumentException(
                    "topicsPrivacyBudgetForTopicIdDistribution should be > 0");
        }

        return topicsPrivacyBudgetForTopicIdDistribution;
    }

    @Override
    public boolean getTopicsDisableDirectAppCalls() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_TOPICS_DISABLE_DIRECT_APP_CALLS,
                TOPICS_DISABLE_DIRECT_APP_CALLS);
    }

    @Override
    public boolean getTopicsEncryptionEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_TOPICS_ENCRYPTION_ENABLED, TOPICS_ENCRYPTION_ENABLED);
    }

    @Override
    public boolean getTopicsEncryptionMetricsEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_TOPICS_ENCRYPTION_METRICS_ENABLED,
                TOPICS_ENCRYPTION_METRICS_ENABLED);
    }

    @Override
    public boolean getTopicsEpochJobBatteryConstraintLoggingEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_LOGGING_ENABLED,
                TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_LOGGING_ENABLED);
    }

    @Override
    public boolean getTopicsDisablePlaintextResponse() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_TOPICS_DISABLE_PLAINTEXT_RESPONSE,
                TOPICS_DISABLE_PLAINTEXT_RESPONSE);
    }

    @Override
    public String getTopicsTestEncryptionPublicKey() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_TOPICS_TEST_ENCRYPTION_PUBLIC_KEY,
                TOPICS_TEST_ENCRYPTION_PUBLIC_KEY);
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public int getClassifierType() {
        return SystemProperties.getInt(
                getSystemPropertyName(FlagsConstants.KEY_CLASSIFIER_TYPE),
                getDeviceConfigFlag(FlagsConstants.KEY_CLASSIFIER_TYPE, DEFAULT_CLASSIFIER_TYPE));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public int getClassifierNumberOfTopLabels() {
        return SystemProperties.getInt(
                getSystemPropertyName(FlagsConstants.KEY_CLASSIFIER_NUMBER_OF_TOP_LABELS),
                getDeviceConfigFlag(
                        FlagsConstants.KEY_CLASSIFIER_NUMBER_OF_TOP_LABELS,
                        CLASSIFIER_NUMBER_OF_TOP_LABELS));
    }

    @Override
    public float getClassifierThreshold() {
        return getDeviceConfigFlag(FlagsConstants.KEY_CLASSIFIER_THRESHOLD, CLASSIFIER_THRESHOLD);
    }

    @Override
    public int getClassifierDescriptionMaxWords() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_CLASSIFIER_DESCRIPTION_MAX_WORDS,
                CLASSIFIER_DESCRIPTION_MAX_WORDS);
    }

    @Override
    public int getClassifierDescriptionMaxLength() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_CLASSIFIER_DESCRIPTION_MAX_LENGTH,
                CLASSIFIER_DESCRIPTION_MAX_LENGTH);
    }

    @Override
    public boolean getClassifierForceUseBundledFiles() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_CLASSIFIER_FORCE_USE_BUNDLED_FILES,
                CLASSIFIER_FORCE_USE_BUNDLED_FILES);
    }

    @Override
    public boolean getTopicsCobaltLoggingEnabled() {
        return getCobaltLoggingEnabled()
                && getDeviceConfigFlag(
                        FlagsConstants.KEY_TOPICS_COBALT_LOGGING_ENABLED,
                        TOPICS_COBALT_LOGGING_ENABLED);
    }

    @Override
    public boolean getTopicsJobSchedulerRescheduleEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_TOPICS_JOB_SCHEDULER_RESCHEDULE_ENABLED,
                TOPICS_JOB_SCHEDULER_RESCHEDULE_ENABLED);
    }

    @Override
    public boolean getTopicsEpochJobBatteryNotLowInsteadOfCharging() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_TOPICS_EPOCH_JOB_BATTERY_NOT_LOW_INSTEAD_OF_CHARGING,
                TOPICS_EPOCH_JOB_BATTERY_NOT_LOW_INSTEAD_OF_CHARGING);
    }

    @Override
    public boolean getTopicsCleanDBWhenEpochJobSettingsChanged() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_TOPICS_CLEAN_DB_WHEN_EPOCH_JOB_SETTINGS_CHANGED,
                TOPICS_CLEAN_DB_WHEN_EPOCH_JOB_SETTINGS_CHANGED);
    }

    @Override
    public boolean getMsmtRegistrationCobaltLoggingEnabled() {
        return getCobaltLoggingEnabled()
                && getDeviceConfigFlag(
                        FlagsConstants.KEY_MSMT_REGISTRATION_COBALT_LOGGING_ENABLED,
                        MSMT_REGISTRATION_COBALT_LOGGING_ENABLED);
    }

    @Override
    public boolean getMsmtAttributionCobaltLoggingEnabled() {
        return getCobaltLoggingEnabled()
                && getDeviceConfigFlag(
                        FlagsConstants.KEY_MSMT_ATTRIBUTION_COBALT_LOGGING_ENABLED,
                        MSMT_ATTRIBUTION_COBALT_LOGGING_ENABLED);
    }

    @Override
    public boolean getMsmtReportingCobaltLoggingEnabled() {
        return getCobaltLoggingEnabled()
                && getDeviceConfigFlag(
                        FlagsConstants.KEY_MSMT_REPORTING_COBALT_LOGGING_ENABLED,
                        MSMT_REPORTING_COBALT_LOGGING_ENABLED);
    }

    @Override
    public boolean getAppNameApiErrorCobaltLoggingEnabled() {
        return getCobaltLoggingEnabled()
                && getDeviceConfigFlag(
                        FlagsConstants.KEY_APP_NAME_API_ERROR_COBALT_LOGGING_ENABLED,
                        APP_NAME_API_ERROR_COBALT_LOGGING_ENABLED);
    }

    @Override
    public int getAppNameApiErrorCobaltLoggingSamplingRate() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_APP_NAME_API_ERROR_COBALT_LOGGING_SAMPLING_RATE,
                APP_NAME_API_ERROR_COBALT_LOGGING_SAMPLING_RATE);
    }

    @Override
    public String getCobaltAdservicesApiKeyHex() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_COBALT_ADSERVICES_API_KEY_HEX, COBALT_ADSERVICES_API_KEY_HEX);
    }

    @Override
    public String getAdservicesReleaseStageForCobalt() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ADSERVICES_RELEASE_STAGE_FOR_COBALT,
                ADSERVICES_RELEASE_STAGE_FOR_COBALT);
    }

    @Override
    public long getCobaltLoggingJobPeriodMs() {
        long cobaltLoggingJobPeriodMs =
                getDeviceConfigFlag(
                        FlagsConstants.KEY_COBALT_LOGGING_JOB_PERIOD_MS,
                        COBALT_LOGGING_JOB_PERIOD_MS);
        if (cobaltLoggingJobPeriodMs < 0) {
            throw new IllegalArgumentException(
                    String.format(
                            "cobaltLoggingJobPeriodMs=%d. cobaltLoggingJobPeriodMs should >= 0",
                            cobaltLoggingJobPeriodMs));
        }
        return cobaltLoggingJobPeriodMs;
    }

    @Override
    public long getCobaltUploadServiceUnbindDelayMs() {
        long cobaltUploadServiceUnbindDelayMs =
                getDeviceConfigFlag(
                        FlagsConstants.KEY_COBALT_UPLOAD_SERVICE_UNBIND_DELAY_MS,
                        COBALT_UPLOAD_SERVICE_UNBIND_DELAY_MS);
        if (cobaltUploadServiceUnbindDelayMs < 0) {
            throw new IllegalArgumentException(
                    String.format(
                            "cobaltUploadServiceUnbindDelayMs=%d. cobaltLoggingJobPeriodMs should"
                                    + " >= 0",
                            cobaltUploadServiceUnbindDelayMs));
        }
        return cobaltUploadServiceUnbindDelayMs;
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getCobaltLoggingEnabled() {
        return !getGlobalKillSwitch()
                && SystemProperties.getBoolean(
                        getSystemPropertyName(FlagsConstants.KEY_COBALT_LOGGING_ENABLED),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_COBALT_LOGGING_ENABLED, COBALT_LOGGING_ENABLED));
    }

    @Override
    public boolean getCobaltRegistryOutOfBandUpdateEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_COBALT_REGISTRY_OUT_OF_BAND_UPDATE_ENABLED,
                COBALT_REGISTRY_OUT_OF_BAND_UPDATE_ENABLED);
    }

    @Override
    public String getMddCobaltRegistryManifestFileUrl() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MDD_COBALT_REGISTRY_MANIFEST_FILE_URL,
                MDD_COBALT_REGISTRY_MANIFEST_FILE_URL);
    }

    @Override
    public boolean getCobaltOperationalLoggingEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_COBALT_OPERATIONAL_LOGGING_ENABLED,
                COBALT_OPERATIONAL_LOGGING_ENABLED);
    }

    @Override
    public boolean getCobaltFallBackToDefaultBaseRegistry() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_COBALT__FALL_BACK_TO_DEFAULT_BASE_REGISTRY,
                COBALT__FALL_BACK_TO_DEFAULT_BASE_REGISTRY);
    }

    @Override
    public String getCobaltIgnoredReportIdList() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_COBALT__IGNORED_REPORT_ID_LIST, COBALT__IGNORED_REPORT_ID_LIST);
    }

    @Override
    public boolean getCobaltEnableApiCallResponseLogging() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_COBALT__ENABLE_API_CALL_RESPONSE_LOGGING,
                COBALT__FALL_BACK_TO_DEFAULT_BASE_REGISTRY);
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public long getMaintenanceJobPeriodMs() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig) and then
        // hard-coded value.
        long maintenanceJobPeriodMs =
                SystemProperties.getLong(
                        getSystemPropertyName(FlagsConstants.KEY_MAINTENANCE_JOB_PERIOD_MS),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_MAINTENANCE_JOB_PERIOD_MS,
                                MAINTENANCE_JOB_PERIOD_MS));
        if (maintenanceJobPeriodMs < 0) {
            throw new IllegalArgumentException("maintenanceJobPeriodMs should  >= 0");
        }
        return maintenanceJobPeriodMs;
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public long getMaintenanceJobFlexMs() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig) and then
        // hard-coded value.
        long maintenanceJobFlexMs =
                SystemProperties.getLong(
                        getSystemPropertyName(FlagsConstants.KEY_MAINTENANCE_JOB_FLEX_MS),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_MAINTENANCE_JOB_FLEX_MS,
                                MAINTENANCE_JOB_FLEX_MS));

        if (maintenanceJobFlexMs <= 0) {
            throw new IllegalArgumentException("maintenanceJobFlexMs should  > 0");
        }

        return maintenanceJobFlexMs;
    }

    @Override
    public int getEncryptionKeyNetworkConnectTimeoutMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ENCRYPTION_KEY_NETWORK_CONNECT_TIMEOUT_MS,
                ENCRYPTION_KEY_NETWORK_CONNECT_TIMEOUT_MS);
    }

    @Override
    public int getEncryptionKeyNetworkReadTimeoutMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ENCRYPTION_KEY_NETWORK_READ_TIMEOUT_MS,
                ENCRYPTION_KEY_NETWORK_READ_TIMEOUT_MS);
    }

    @Override
    public long getMeasurementEventMainReportingJobPeriodMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS,
                MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS);
    }

    @Override
    public long getMeasurementEventFallbackReportingJobPeriodMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS,
                MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS);
    }

    @Override
    public boolean getMeasurementAggregationCoordinatorOriginEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_ENABLED,
                MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_ENABLED);
    }

    @Override
    public String getMeasurementAggregationCoordinatorOriginList() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_LIST,
                MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_LIST);
    }

    @Override
    public String getMeasurementDefaultAggregationCoordinatorOrigin() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_DEFAULT_AGGREGATION_COORDINATOR_ORIGIN,
                MEASUREMENT_DEFAULT_AGGREGATION_COORDINATOR_ORIGIN);
    }

    @Override
    public String getMeasurementAggregationCoordinatorPath() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_AGGREGATION_COORDINATOR_PATH,
                MEASUREMENT_AGGREGATION_COORDINATOR_PATH);
    }

    @Override
    public long getMeasurementAggregateMainReportingJobPeriodMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS,
                MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS);
    }

    @Override
    public long getMeasurementAggregateFallbackReportingJobPeriodMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS,
                MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS);
    }

    @Override
    public int getMeasurementNetworkConnectTimeoutMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_NETWORK_CONNECT_TIMEOUT_MS,
                MEASUREMENT_NETWORK_CONNECT_TIMEOUT_MS);
    }

    @Override
    public int getMeasurementNetworkReadTimeoutMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_NETWORK_READ_TIMEOUT_MS,
                MEASUREMENT_NETWORK_READ_TIMEOUT_MS);
    }

    @Override
    public long getMeasurementDbSizeLimit() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_DB_SIZE_LIMIT, MEASUREMENT_DB_SIZE_LIMIT);
    }

    @Override
    public boolean getMeasurementReportingRetryLimitEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_REPORT_RETRY_LIMIT_ENABLED,
                MEASUREMENT_REPORT_RETRY_LIMIT_ENABLED);
    }

    @Override
    public int getMeasurementReportingRetryLimit() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_REPORT_RETRY_LIMIT, MEASUREMENT_REPORT_RETRY_LIMIT);
    }

    @Override
    public String getMeasurementManifestFileUrl() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MANIFEST_FILE_URL, MEASUREMENT_MANIFEST_FILE_URL);
    }

    @Override
    public long getMeasurementRegistrationInputEventValidWindowMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_REGISTRATION_INPUT_EVENT_VALID_WINDOW_MS,
                MEASUREMENT_REGISTRATION_INPUT_EVENT_VALID_WINDOW_MS);
    }

    @Override
    public boolean getMeasurementIsClickVerificationEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_IS_CLICK_VERIFICATION_ENABLED,
                MEASUREMENT_IS_CLICK_VERIFICATION_ENABLED);
    }

    @Override
    public boolean getMeasurementIsClickVerifiedByInputEvent() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_IS_CLICK_VERIFIED_BY_INPUT_EVENT,
                MEASUREMENT_IS_CLICK_VERIFIED_BY_INPUT_EVENT);
    }

    @Override
    public boolean getMeasurementIsClickDeduplicationEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_IS_CLICK_DEDUPLICATION_ENABLED,
                MEASUREMENT_IS_CLICK_DEDUPLICATION_ENABLED);
    }

    @Override
    public boolean getMeasurementIsClickDeduplicationEnforced() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_IS_CLICK_DEDUPLICATION_ENFORCED,
                MEASUREMENT_IS_CLICK_DEDUPLICATION_ENFORCED);
    }

    @Override
    public long getMeasurementMaxSourcesPerClick() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_SOURCES_PER_CLICK,
                MEASUREMENT_MAX_SOURCES_PER_CLICK);
    }

    @Override
    public boolean getMeasurementEnableXNA() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_XNA, MEASUREMENT_ENABLE_XNA);
    }

    @Override
    public boolean getMeasurementEnableSharedSourceDebugKey() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_SHARED_SOURCE_DEBUG_KEY,
                MEASUREMENT_ENABLE_SHARED_SOURCE_DEBUG_KEY);
    }

    @Override
    public boolean getMeasurementEnableSharedFilterDataKeysXNA() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_SHARED_FILTER_DATA_KEYS_XNA,
                MEASUREMENT_ENABLE_SHARED_FILTER_DATA_KEYS_XNA);
    }

    @Override
    public boolean getMeasurementEnableDebugReport() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_DEBUG_REPORT,
                MEASUREMENT_ENABLE_DEBUG_REPORT);
    }

    @Override
    public boolean getMeasurementEnableSourceDebugReport() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_SOURCE_DEBUG_REPORT,
                MEASUREMENT_ENABLE_SOURCE_DEBUG_REPORT);
    }

    @Override
    public boolean getMeasurementEnableTriggerDebugReport() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_TRIGGER_DEBUG_REPORT,
                MEASUREMENT_ENABLE_TRIGGER_DEBUG_REPORT);
    }

    @Override
    public boolean getMeasurementEnableHeaderErrorDebugReport() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_HEADER_ERROR_DEBUG_REPORT,
                MEASUREMENT_ENABLE_HEADER_ERROR_DEBUG_REPORT);
    }

    @Override
    public long getMeasurementDataExpiryWindowMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_DATA_EXPIRY_WINDOW_MS,
                MEASUREMENT_DATA_EXPIRY_WINDOW_MS);
    }

    @Override
    public int getMeasurementMaxRegistrationRedirects() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_REGISTRATION_REDIRECTS,
                MEASUREMENT_MAX_REGISTRATION_REDIRECTS);
    }

    @Override
    public int getMeasurementMaxRegistrationsPerJobInvocation() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_REGISTRATIONS_PER_JOB_INVOCATION,
                MEASUREMENT_MAX_REGISTRATIONS_PER_JOB_INVOCATION);
    }

    @Override
    public int getMeasurementMaxRetriesPerRegistrationRequest() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_RETRIES_PER_REGISTRATION_REQUEST,
                MEASUREMENT_MAX_RETRIES_PER_REGISTRATION_REQUEST);
    }

    @Override
    public long getMeasurementAsyncRegistrationJobTriggerMinDelayMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ASYNC_REGISTRATION_JOB_TRIGGER_MIN_DELAY_MS,
                DEFAULT_MEASUREMENT_ASYNC_REGISTRATION_JOB_TRIGGER_MIN_DELAY_MS);
    }

    @Override
    public long getMeasurementAsyncRegistrationJobTriggerMaxDelayMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ASYNC_REGISTRATION_JOB_TRIGGER_MAX_DELAY_MS,
                DEFAULT_MEASUREMENT_ASYNC_REGISTRATION_JOB_TRIGGER_MAX_DELAY_MS);
    }

    @Override
    public int getMeasurementMaxBytesPerAttributionFilterString() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_BYTES_PER_ATTRIBUTION_FILTER_STRING,
                DEFAULT_MEASUREMENT_MAX_BYTES_PER_ATTRIBUTION_FILTER_STRING);
    }

    @Override
    public int getMeasurementMaxFilterMapsPerFilterSet() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET,
                DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET);
    }

    @Override
    public int getMeasurementMaxValuesPerAttributionFilter() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER,
                DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER);
    }

    @Override
    public int getMeasurementMaxAttributionFilters() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_ATTRIBUTION_FILTERS,
                DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS);
    }

    @Override
    public int getMeasurementMaxBytesPerAttributionAggregateKeyId() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_BYTES_PER_ATTRIBUTION_AGGREGATE_KEY_ID,
                DEFAULT_MEASUREMENT_MAX_BYTES_PER_ATTRIBUTION_AGGREGATE_KEY_ID);
    }

    @Override
    public int getMeasurementMaxAggregateDeduplicationKeysPerRegistration() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_AGGREGATE_DEDUPLICATION_KEYS_PER_REGISTRATION,
                DEFAULT_MEASUREMENT_MAX_AGGREGATE_DEDUPLICATION_KEYS_PER_REGISTRATION);
    }

    @Override
    public long getMeasurementAttributionJobTriggerDelayMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ATTRIBUTION_JOB_TRIGGERING_DELAY_MS,
                DEFAULT_MEASUREMENT_ATTRIBUTION_JOB_TRIGGERING_DELAY_MS);
    }

    @Override
    public int getMeasurementMaxAttributionsPerInvocation() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_ATTRIBUTIONS_PER_INVOCATION,
                DEFAULT_MEASUREMENT_MAX_ATTRIBUTIONS_PER_INVOCATION);
    }

    @Override
    public long getMeasurementMaxEventReportUploadRetryWindowMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_EVENT_REPORT_UPLOAD_RETRY_WINDOW_MS,
                DEFAULT_MEASUREMENT_MAX_EVENT_REPORT_UPLOAD_RETRY_WINDOW_MS);
    }

    @Override
    public long getMeasurementMaxAggregateReportUploadRetryWindowMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS,
                DEFAULT_MEASUREMENT_MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS);
    }

    @Override
    public long getMeasurementMaxDelayedSourceRegistrationWindow() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_DELAYED_SOURCE_REGISTRATION_WINDOW,
                DEFAULT_MEASUREMENT_MAX_DELAYED_SOURCE_REGISTRATION_WINDOW);
    }

    @Override
    public boolean getMeasurementAttributionFallbackJobEnabled() {
        return getLegacyMeasurementKillSwitch()
                ? false
                : !getFlagFromSystemPropertiesOrDeviceConfig(
                        FlagsConstants.KEY_MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_KILL_SWITCH,
                        MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_KILL_SWITCH);
    }

    @Override
    public long getMeasurementAttributionFallbackJobPeriodMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_PERIOD_MS,
                MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_PERIOD_MS);
    }

    @Override
    public int getMeasurementMaxEventAttributionPerRateLimitWindow() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_EVENT_ATTRIBUTION_PER_RATE_LIMIT_WINDOW,
                MEASUREMENT_MAX_EVENT_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);
    }

    @Override
    public int getMeasurementMaxAggregateAttributionPerRateLimitWindow() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_AGGREGATE_ATTRIBUTION_PER_RATE_LIMIT_WINDOW,
                MEASUREMENT_MAX_AGGREGATE_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);
    }

    @Override
    public int getMeasurementMaxDistinctReportingOriginsInAttribution() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_DISTINCT_REPORTING_ORIGINS_IN_ATTRIBUTION,
                MEASUREMENT_MAX_DISTINCT_REPORTING_ORIGINS_IN_ATTRIBUTION);
    }

    @Override
    public int getMeasurementMaxDistinctDestinationsInActiveSource() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_DISTINCT_DESTINATIONS_IN_ACTIVE_SOURCE,
                MEASUREMENT_MAX_DISTINCT_DESTINATIONS_IN_ACTIVE_SOURCE);
    }

    @Override
    public int getMeasurementMaxReportingOriginsPerSourceReportingSitePerWindow() {
        return getDeviceConfigFlag(
                FlagsConstants
                        .KEY_MEASUREMENT_MAX_REPORTING_ORIGINS_PER_SOURCE_REPORTING_SITE_PER_WINDOW,
                MEASUREMENT_MAX_REPORTING_ORIGINS_PER_SOURCE_REPORTING_SITE_PER_WINDOW);
    }

    @Override
    public int getMeasurementMaxDistinctRepOrigPerPublXDestInSource() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_DISTINCT_REPORTING_ORIGINS_IN_SOURCE,
                MEASUREMENT_MAX_DISTINCT_REP_ORIG_PER_PUBLISHER_X_DEST_IN_SOURCE);
    }

    @Override
    public boolean getMeasurementEnableDestinationRateLimit() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_DESTINATION_RATE_LIMIT,
                MEASUREMENT_ENABLE_DESTINATION_RATE_LIMIT);
    }

    @Override
    public int getMeasurementMaxDestinationsPerPublisherPerRateLimitWindow() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_DESTINATIONS_PER_PUBLISHER_PER_RATE_LIMIT_WINDOW,
                MEASUREMENT_MAX_DESTINATIONS_PER_PUBLISHER_PER_RATE_LIMIT_WINDOW);
    }

    @Override
    public int getMeasurementMaxDestPerPublisherXEnrollmentPerRateLimitWindow() {
        return getDeviceConfigFlag(
                FlagsConstants
                        .KEY_MEASUREMENT_MAX_DEST_PER_PUBLISHER_X_ENROLLMENT_PER_RATE_LIMIT_WINDOW,
                MEASUREMENT_MAX_DEST_PER_PUBLISHER_X_ENROLLMENT_PER_RATE_LIMIT_WINDOW);
    }

    @Override
    public long getMeasurementDestinationRateLimitWindow() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_DESTINATION_RATE_LIMIT_WINDOW,
                MEASUREMENT_DESTINATION_RATE_LIMIT_WINDOW);
    }

    @Override
    public boolean getMeasurementEnableDestinationPerDayRateLimitWindow() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW,
                MEASUREMENT_ENABLE_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW);
    }

    @Override
    public int getMeasurementDestinationPerDayRateLimit() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT,
                MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT);
    }

    @Override
    public long getMeasurementDestinationPerDayRateLimitWindowInMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW_IN_MS,
                MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW_IN_MS);
    }

    @Override
    public boolean getFledgeAppPackageNameLoggingEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_APP_PACKAGE_NAME_LOGGING_ENABLED,
                FLEDGE_APP_PACKAGE_NAME_LOGGING_ENABLED);
    }

    @Override
    public long getFledgeCustomAudienceMaxCount() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT,
                FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT);
    }

    @Override
    public long getFledgeCustomAudiencePerAppMaxCount() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT,
                FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT);
    }

    @Override
    public long getFledgeCustomAudienceMaxOwnerCount() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT,
                FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT);
    }

    @Override
    public long getFledgeCustomAudiencePerBuyerMaxCount() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_PER_BUYER_MAX_COUNT,
                FLEDGE_CUSTOM_AUDIENCE_PER_BUYER_MAX_COUNT);
    }

    @Override
    public long getFledgeCustomAudienceDefaultExpireInMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN_MS,
                FLEDGE_CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN_MS);
    }

    @Override
    public long getFledgeCustomAudienceMaxActivationDelayInMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN_MS,
                FLEDGE_CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN_MS);
    }

    @Override
    public long getFledgeCustomAudienceMaxExpireInMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_EXPIRE_IN_MS,
                FLEDGE_CUSTOM_AUDIENCE_MAX_EXPIRE_IN_MS);
    }

    @Override
    public int getFledgeCustomAudienceMaxNameSizeB() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NAME_SIZE_B,
                FLEDGE_CUSTOM_AUDIENCE_MAX_NAME_SIZE_B);
    }

    @Override
    public int getFledgeCustomAudienceMaxDailyUpdateUriSizeB() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_DAILY_UPDATE_URI_SIZE_B,
                FLEDGE_CUSTOM_AUDIENCE_MAX_DAILY_UPDATE_URI_SIZE_B);
    }

    @Override
    public int getFledgeCustomAudienceMaxBiddingLogicUriSizeB() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_BIDDING_LOGIC_URI_SIZE_B,
                FLEDGE_CUSTOM_AUDIENCE_MAX_BIDDING_LOGIC_URI_SIZE_B);
    }

    @Override
    public int getFledgeCustomAudienceMaxUserBiddingSignalsSizeB() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B,
                FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B);
    }

    @Override
    public int getFledgeCustomAudienceMaxTrustedBiddingDataSizeB() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B,
                FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B);
    }

    @Override
    public int getFledgeCustomAudienceMaxAdsSizeB() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ADS_SIZE_B,
                FLEDGE_CUSTOM_AUDIENCE_MAX_ADS_SIZE_B);
    }

    @Override
    public int getFledgeCustomAudienceMaxNumAds() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS,
                FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS);
    }

    @Override
    public long getFledgeCustomAudienceActiveTimeWindowInMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS,
                FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS);
    }

    @Override
    public int getFledgeFetchCustomAudienceMaxUserBiddingSignalsSizeB() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B,
                FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B);
    }

    @Override
    public int getFledgeFetchCustomAudienceMaxRequestCustomHeaderSizeB() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_REQUEST_CUSTOM_HEADER_SIZE_B,
                FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_REQUEST_CUSTOM_HEADER_SIZE_B);
    }

    @Override
    public int getFledgeFetchCustomAudienceMaxCustomAudienceSizeB() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_CUSTOM_AUDIENCE_SIZE_B,
                FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_CUSTOM_AUDIENCE_SIZE_B);
    }

    @Override
    public long getFledgeFetchCustomAudienceMinRetryAfterValueMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_MIN_RETRY_AFTER_VALUE_MS,
                FLEDGE_FETCH_CUSTOM_AUDIENCE_MIN_RETRY_AFTER_VALUE_MS);
    }

    @Override
    public long getFledgeFetchCustomAudienceMaxRetryAfterValueMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_RETRY_AFTER_VALUE_MS,
                FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_RETRY_AFTER_VALUE_MS);
    }

    @Override
    public boolean getFledgeBackgroundFetchEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_ENABLED,
                FLEDGE_BACKGROUND_FETCH_ENABLED);
    }

    @Override
    public long getFledgeBackgroundFetchJobPeriodMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS,
                FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS);
    }

    @Override
    public long getFledgeBackgroundFetchJobFlexMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS,
                FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS);
    }

    @Override
    public long getFledgeBackgroundFetchJobMaxRuntimeMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_JOB_MAX_RUNTIME_MS,
                FLEDGE_BACKGROUND_FETCH_JOB_MAX_RUNTIME_MS);
    }

    @Override
    public long getFledgeBackgroundFetchMaxNumUpdated() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_MAX_NUM_UPDATED,
                FLEDGE_BACKGROUND_FETCH_MAX_NUM_UPDATED);
    }

    @Override
    public int getFledgeBackgroundFetchThreadPoolSize() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_THREAD_POOL_SIZE,
                FLEDGE_BACKGROUND_FETCH_THREAD_POOL_SIZE);
    }

    @Override
    public long getFledgeBackgroundFetchEligibleUpdateBaseIntervalS() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_ELIGIBLE_UPDATE_BASE_INTERVAL_S,
                FLEDGE_BACKGROUND_FETCH_ELIGIBLE_UPDATE_BASE_INTERVAL_S);
    }

    @Override
    public int getFledgeBackgroundFetchNetworkConnectTimeoutMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS,
                FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS);
    }

    @Override
    public int getFledgeBackgroundFetchNetworkReadTimeoutMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS,
                FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS);
    }

    @Override
    public int getFledgeBackgroundFetchMaxResponseSizeB() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_MAX_RESPONSE_SIZE_B,
                FLEDGE_BACKGROUND_FETCH_MAX_RESPONSE_SIZE_B);
    }

    @Override
    public boolean getProtectedSignalsPeriodicEncodingEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_PROTECTED_SIGNALS_PERIODIC_ENCODING_ENABLED,
                PROTECTED_SIGNALS_PERIODIC_ENCODING_ENABLED);
    }

    @Override
    public long getProtectedSignalPeriodicEncodingJobPeriodMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_PERIOD_MS,
                PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_PERIOD_MS);
    }

    @Override
    public long getProtectedSignalsEncoderRefreshWindowSeconds() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_PROTECTED_SIGNALS_ENCODER_REFRESH_WINDOW_SECONDS,
                PROTECTED_SIGNALS_ENCODER_REFRESH_WINDOW_SECONDS);
    }

    @Override
    public long getProtectedSignalsPeriodicEncodingJobFlexMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_FLEX_MS,
                PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_FLEX_MS);
    }

    @Override
    public int getProtectedSignalsEncodedPayloadMaxSizeBytes() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES,
                PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES);
    }

    @Override
    public int getProtectedSignalsFetchSignalUpdatesMaxSizeBytes() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_PROTECTED_SIGNALS_FETCH_SIGNAL_UPDATES_MAX_SIZE_BYTES,
                PROTECTED_SIGNALS_FETCH_SIGNAL_UPDATES_MAX_SIZE_BYTES);
    }

    @Override
    public boolean getFledgeEnableForcedEncodingAfterSignalsUpdate() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_ENABLE_FORCED_ENCODING_AFTER_SIGNALS_UPDATE,
                FLEDGE_ENABLE_FORCED_ENCODING_AFTER_SIGNALS_UPDATE);
    }

    @Override
    public long getFledgeForcedEncodingAfterSignalsUpdateCooldownSeconds() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_FORCED_ENCODING_AFTER_SIGNALS_UPDATE_COOLDOWN_SECONDS,
                FLEDGE_FORCED_ENCODING_AFTER_SIGNALS_UPDATE_COOLDOWN_SECONDS);
    }

    @Override
    public int getProtectedSignalsMaxJsFailureExecutionOnCertainVersionBeforeStop() {
        return getDeviceConfigFlag(
                FlagsConstants
                        .KEY_PROTECTED_SIGNALS_MAX_JS_FAILURE_EXECUTION_ON_CERTAIN_VERSION_BEFORE_STOP,
                PROTECTED_SIGNALS_MAX_JS_FAILURE_EXECUTION_ON_CERTAIN_VERSION_BEFORE_STOP);
    }

    @Override
    public int getProtectedSignalsMaxSignalSizePerBuyerBytes() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_PROTECTED_SIGNALS_MAX_SIGNAL_SIZE_PER_BUYER_BYTES,
                PROTECTED_SIGNALS_MAX_SIGNAL_SIZE_PER_BUYER_BYTES);
    }

    @Override
    public int getProtectedSignalsMaxSignalSizePerBuyerWithOversubsciptionBytes() {
        return getDeviceConfigFlag(
                FlagsConstants
                        .KEY_PROTECTED_SIGNALS_MAX_SIGNAL_SIZE_PER_BUYER_WITH_OVERSUBSCIPTION_BYTES,
                PROTECTED_SIGNALS_MAX_SIGNAL_SIZE_PER_BUYER_WITH_OVERSUBSCIPTION_BYTES);
    }

    @Override
    public int getAdSelectionMaxConcurrentBiddingCount() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AD_SELECTION_MAX_CONCURRENT_BIDDING_COUNT,
                FLEDGE_AD_SELECTION_MAX_CONCURRENT_BIDDING_COUNT);
    }

    @Override
    public long getAdSelectionBiddingTimeoutPerCaMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS,
                FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS);
    }

    @Override
    public long getAdSelectionBiddingTimeoutPerBuyerMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_BUYER_MS,
                FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_BUYER_MS);
    }

    @Override
    public long getAdSelectionScoringTimeoutMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS,
                FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS);
    }

    @Override
    public long getAdSelectionSelectingOutcomeTimeoutMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS,
                FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS);
    }

    @Override
    public long getAdSelectionOverallTimeoutMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS,
                FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS);
    }

    @Override
    public long getAdSelectionFromOutcomesOverallTimeoutMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS,
                FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS);
    }

    @Override
    public long getAdSelectionOffDeviceOverallTimeoutMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AD_SELECTION_OFF_DEVICE_OVERALL_TIMEOUT_MS,
                FLEDGE_AD_SELECTION_OFF_DEVICE_OVERALL_TIMEOUT_MS);
    }

    @Override
    public boolean getFledgeAppInstallFilteringEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_APP_INSTALL_FILTERING_ENABLED,
                FLEDGE_APP_INSTALL_FILTERING_ENABLED);
    }

    @Override
    public boolean getFledgeFrequencyCapFilteringEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_FREQUENCY_CAP_FILTERING_ENABLED,
                FLEDGE_FREQUENCY_CAP_FILTERING_ENABLED);
    }

    @Override
    @SuppressWarnings("InlinedApi")
    public boolean getFledgeAdSelectionContextualAdsEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AD_SELECTION_CONTEXTUAL_ADS_ENABLED,
                FLEDGE_AD_SELECTION_CONTEXTUAL_ADS_ENABLED);
    }

    @Override
    public boolean getFledgeAdSelectionContextualAdsMetricsEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AD_SELECTION_CONTEXTUAL_ADS_METRICS_ENABLED,
                FLEDGE_AD_SELECTION_CONTEXTUAL_ADS_METRICS_ENABLED);
    }

    @Override
    public boolean getFledgeAppInstallFilteringMetricsEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_APP_INSTALL_FILTERING_METRICS_ENABLED,
                FLEDGE_APP_INSTALL_FILTERING_METRICS_ENABLED);
    }

    @Override
    public boolean getFledgeFrequencyCapFilteringMetricsEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_FREQUENCY_CAP_FILTERING_METRICS_ENABLED,
                FLEDGE_FREQUENCY_CAP_FILTERING_METRICS_ENABLED);
    }

    @Override
    @SuppressWarnings("InlinedApi")
    public boolean getFledgeFetchCustomAudienceEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED,
                FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED);
    }

    @Override
    public long getFledgeAdSelectionBiddingLogicJsVersion() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AD_SELECTION_BIDDING_LOGIC_JS_VERSION,
                FLEDGE_AD_SELECTION_BIDDING_LOGIC_JS_VERSION);
    }

    @Override
    public long getReportImpressionOverallTimeoutMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS,
                FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS);
    }

    @Override
    public long getFledgeReportImpressionMaxRegisteredAdBeaconsTotalCount() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_REPORT_IMPRESSION_MAX_REGISTERED_AD_BEACONS_TOTAL_COUNT,
                FLEDGE_REPORT_IMPRESSION_MAX_REGISTERED_AD_BEACONS_TOTAL_COUNT);
    }

    @Override
    public long getFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount() {
        return getDeviceConfigFlag(
                FlagsConstants
                        .KEY_FLEDGE_REPORT_IMPRESSION_MAX_REGISTERED_AD_BEACONS_PER_AD_TECH_COUNT,
                FLEDGE_REPORT_IMPRESSION_MAX_REGISTERED_AD_BEACONS_PER_AD_TECH_COUNT);
    }

    @Override
    public long getFledgeReportImpressionRegisteredAdBeaconsMaxInteractionKeySizeB() {
        return getDeviceConfigFlag(
                FlagsConstants
                        .KEY_FLEDGE_REPORT_IMPRESSION_REGISTERED_AD_BEACONS_MAX_INTERACTION_KEY_SIZE_B,
                FLEDGE_REPORT_IMPRESSION_REGISTERED_AD_BEACONS_MAX_INTERACTION_KEY_SIZE_B);
    }

    @Override
    public long getFledgeReportImpressionMaxInteractionReportingUriSizeB() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_REPORT_IMPRESSION_MAX_INTERACTION_REPORTING_URI_SIZE_B,
                FLEDGE_REPORT_IMPRESSION_MAX_INTERACTION_REPORTING_URI_SIZE_B);
    }

    @Override
    public boolean getFledgeScheduleCustomAudienceUpdateEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED,
                FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED);
    }

    @Override
    public boolean getFledgeEnableScheduleCustomAudienceUpdateAdditionalScheduleRequests() {
        return getFledgeScheduleCustomAudienceUpdateEnabled()
                && getDeviceConfigFlag(
                        FlagsConstants
                                .KEY_FLEDGE_ENABLE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ADDITIONAL_SCHEDULE_REQUESTS,
                        FLEDGE_ENABLE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ADDITIONAL_SCHEDULE_REQUESTS);
    }

    @Override
    public long getFledgeScheduleCustomAudienceUpdateJobPeriodMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_JOB_PERIOD_MS,
                FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_JOB_PERIOD_MS);
    }

    @Override
    public long getFledgeScheduleCustomAudienceUpdateJobFlexMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_JOB_FLEX_MS,
                FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_JOB_FLEX_MS);
    }

    @Override
    public int getFledgeScheduleCustomAudienceMinDelayMinsOverride() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MIN_DELAY_MINS_OVERRIDE,
                FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MIN_DELAY_MINS_OVERRIDE);
    }

    @Override
    public int getFledgeScheduleCustomAudienceUpdateMaxBytes() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MAX_BYTES,
                FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MAX_BYTES);
    }

    @Override
    public boolean getFledgeHttpCachingEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_HTTP_CACHE_ENABLE, FLEDGE_HTTP_CACHE_ENABLE);
    }

    @Override
    public boolean getFledgeHttpJsCachingEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_HTTP_CACHE_ENABLE_JS_CACHING,
                FLEDGE_HTTP_CACHE_ENABLE_JS_CACHING);
    }

    @Override
    public long getFledgeHttpCacheMaxEntries() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_HTTP_CACHE_MAX_ENTRIES, FLEDGE_HTTP_CACHE_MAX_ENTRIES);
    }

    @Override
    public boolean getFledgeOnDeviceAuctionShouldUseUnifiedTables() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_ON_DEVICE_AUCTION_SHOULD_USE_UNIFIED_TABLES,
                FLEDGE_ON_DEVICE_AUCTION_SHOULD_USE_UNIFIED_TABLES);
    }

    @Override
    public long getFledgeHttpCacheMaxAgeSeconds() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_HTTP_CACHE_DEFAULT_MAX_AGE_SECONDS,
                FLEDGE_HTTP_CACHE_DEFAULT_MAX_AGE_SECONDS);
    }

    @Override
    public int getFledgeAdCounterHistogramAbsoluteMaxTotalEventCount() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AD_COUNTER_HISTOGRAM_ABSOLUTE_MAX_TOTAL_EVENT_COUNT,
                FLEDGE_AD_COUNTER_HISTOGRAM_ABSOLUTE_MAX_TOTAL_EVENT_COUNT);
    }

    @Override
    public int getFledgeAdCounterHistogramLowerMaxTotalEventCount() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AD_COUNTER_HISTOGRAM_LOWER_MAX_TOTAL_EVENT_COUNT,
                FLEDGE_AD_COUNTER_HISTOGRAM_LOWER_MAX_TOTAL_EVENT_COUNT);
    }

    @Override
    public int getFledgeAdCounterHistogramAbsoluteMaxPerBuyerEventCount() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AD_COUNTER_HISTOGRAM_ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT,
                FLEDGE_AD_COUNTER_HISTOGRAM_ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT);
    }

    @Override
    public int getFledgeAdCounterHistogramLowerMaxPerBuyerEventCount() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AD_COUNTER_HISTOGRAM_LOWER_MAX_PER_BUYER_EVENT_COUNT,
                FLEDGE_AD_COUNTER_HISTOGRAM_LOWER_MAX_PER_BUYER_EVENT_COUNT);
    }

    @Override
    public boolean getProtectedSignalsCleanupEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_PROTECTED_SIGNALS_CLEANUP_ENABLED,
                PROTECTED_SIGNALS_CLEANUP_ENABLED);
    }

    // MDD related flags.
    @Override
    public int getDownloaderConnectionTimeoutMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_DOWNLOADER_CONNECTION_TIMEOUT_MS,
                DOWNLOADER_CONNECTION_TIMEOUT_MS);
    }

    @Override
    public int getDownloaderReadTimeoutMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_DOWNLOADER_READ_TIMEOUT_MS, DOWNLOADER_READ_TIMEOUT_MS);
    }

    @Override
    public int getDownloaderMaxDownloadThreads() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_DOWNLOADER_MAX_DOWNLOAD_THREADS,
                DOWNLOADER_MAX_DOWNLOAD_THREADS);
    }

    @Override
    public String getMddTopicsClassifierManifestFileUrl() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL,
                MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL);
    }

    // Group of All Killswitches
    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getGlobalKillSwitch() {
        return SdkLevel.isAtLeastT()
                ? SystemProperties.getBoolean(
                        getSystemPropertyName(FlagsConstants.KEY_GLOBAL_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_GLOBAL_KILL_SWITCH, GLOBAL_KILL_SWITCH))
                : !getEnableBackCompat();
    }

    // MEASUREMENT Killswitches

    @Override
    public boolean getLegacyMeasurementKillSwitch() {
        return !getMeasurementEnabled();
    }

    @Override
    public boolean getMeasurementEnabled() {
        return getGlobalKillSwitch()
                ? false
                : !getFlagFromSystemPropertiesOrDeviceConfig(
                        FlagsConstants.KEY_MEASUREMENT_KILL_SWITCH, MEASUREMENT_KILL_SWITCH);
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementApiDeleteRegistrationsKillSwitch() {
        final boolean defaultValue = MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants
                                        .KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH,
                                defaultValue));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementApiStatusKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_MEASUREMENT_API_STATUS_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_MEASUREMENT_API_STATUS_KILL_SWITCH,
                                MEASUREMENT_API_STATUS_KILL_SWITCH));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementApiRegisterSourceKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH,
                                MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementApiRegisterTriggerKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH,
                                MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementApiRegisterWebSourceKillSwitch() {
        boolean defaultValue = MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH,
                                defaultValue));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementApiRegisterSourcesKillSwitch() {
        boolean defaultValue = MEASUREMENT_API_REGISTER_SOURCES_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_MEASUREMENT_API_REGISTER_SOURCES_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_MEASUREMENT_API_REGISTER_SOURCES_KILL_SWITCH,
                                defaultValue));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementApiRegisterWebTriggerKillSwitch() {
        boolean defaultValue = MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants
                                        .KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH,
                                defaultValue));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementJobAggregateFallbackReportingKillSwitch() {
        String flagName =
                FlagsConstants.KEY_MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH;
        boolean defaultValue = MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(flagName),
                        getDeviceConfigFlag(flagName, defaultValue));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementJobAggregateReportingKillSwitch() {
        boolean defaultValue = MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH,
                                defaultValue));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead

    public boolean getMeasurementJobImmediateAggregateReportingKillSwitch() {
        return !getMeasurementEnabled()
                || getDeviceConfigFlag(
                        FlagsConstants
                                .KEY_MEASUREMENT_JOB_IMMEDIATE_AGGREGATE_REPORTING_KILL_SWITCH,
                        MEASUREMENT_JOB_IMMEDIATE_AGGREGATE_REPORTING_KILL_SWITCH);
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementJobAttributionKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH,
                                MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementJobDeleteExpiredKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH,
                                MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementJobDeleteUninstalledKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_MEASUREMENT_JOB_DELETE_UNINSTALLED_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_MEASUREMENT_JOB_DELETE_UNINSTALLED_KILL_SWITCH,
                                MEASUREMENT_JOB_DELETE_UNINSTALLED_KILL_SWITCH));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementJobEventFallbackReportingKillSwitch() {
        String flagName = FlagsConstants.KEY_MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH;
        boolean defaultValue = MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(flagName),
                        getDeviceConfigFlag(flagName, defaultValue));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementJobEventReportingKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH,
                                MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getAsyncRegistrationJobQueueKillSwitch() {
        String flagName = FlagsConstants.KEY_MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH;
        boolean defaultValue = MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(flagName),
                        getDeviceConfigFlag(flagName, defaultValue));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getAsyncRegistrationFallbackJobKillSwitch() {
        String flagName = FlagsConstants.KEY_MEASUREMENT_REGISTRATION_FALLBACK_JOB_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(flagName),
                        getDeviceConfigFlag(
                                flagName, MEASUREMENT_REGISTRATION_FALLBACK_JOB_KILL_SWITCH));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementReceiverInstallAttributionKillSwitch() {
        String flagName = FlagsConstants.KEY_MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH;
        boolean defaultValue = MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(flagName),
                        getDeviceConfigFlag(flagName, defaultValue));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementReceiverDeletePackagesKillSwitch() {
        boolean defaultValue = MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants
                                        .KEY_MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH,
                                defaultValue));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementRollbackDeletionKillSwitch() {
        final boolean defaultValue = MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH,
                                defaultValue));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementRollbackDeletionAppSearchKillSwitch() {
        final boolean defaultValue = MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants
                                        .KEY_MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH),
                        /* def= */ getDeviceConfigFlag(

                                /* name= */ FlagsConstants
                                        .KEY_MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH,
                                defaultValue));
    }

    @Override
    public String getMeasurementDebugJoinKeyEnrollmentAllowlist() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_DEBUG_JOIN_KEY_ENROLLMENT_ALLOWLIST,
                DEFAULT_MEASUREMENT_DEBUG_JOIN_KEY_ENROLLMENT_ALLOWLIST);
    }

    @Override
    public String getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_DEBUG_KEY_AD_ID_MATCHING_ENROLLMENT_BLOCKLIST,
                DEFAULT_MEASUREMENT_PLATFORM_DEBUG_AD_ID_MATCHING_BLOCKLIST);
    }

    @Override
    public boolean getEnableComputeVersionFromMappings() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ENABLE_COMPUTE_VERSION_FROM_MAPPINGS,
                DEFAULT_COMPUTE_VERSION_FROM_MAPPINGS_ENABLED);
    }

    @Override
    public String getMainlineTrainVersion() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MAINLINE_TRAIN_VERSION, DEFAULT_MAINLINE_TRAIN_VERSION);
    }

    @Override
    public String getAdservicesVersionMappings() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ADSERVICES_VERSION_MAPPINGS,
                DEFAULT_ADSERVICES_VERSION_MAPPINGS);
    }

    // ADID Killswitches
    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getAdIdKillSwitch() {
        // Ignore Global Killswitch for adid.
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_ADID_KILL_SWITCH),
                getDeviceConfigFlag(FlagsConstants.KEY_ADID_KILL_SWITCH, ADID_KILL_SWITCH));
    }

    // APPSETID Killswitch.
    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead

    public boolean getAppSetIdKillSwitch() {
        // Ignore Global Killswitch for appsetid.
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_APPSETID_KILL_SWITCH),
                getDeviceConfigFlag(FlagsConstants.KEY_APPSETID_KILL_SWITCH, APPSETID_KILL_SWITCH));
    }

    // TOPICS Killswitches
    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getTopicsKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(FlagsConstants.KEY_TOPICS_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_TOPICS_KILL_SWITCH, TOPICS_KILL_SWITCH));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getTopicsOnDeviceClassifierKillSwitch() {
        // This is an emergency flag that could be used to divert all traffic from on-device
        // classifier to precomputed classifier in case of fatal ML model crashes in Topics.
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH),
                getDeviceConfigFlag(
                        FlagsConstants.KEY_TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH,
                        TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH));
    }

    // MDD Killswitches
    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMddBackgroundTaskKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(FlagsConstants.KEY_MDD_BACKGROUND_TASK_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_MDD_BACKGROUND_TASK_KILL_SWITCH,
                                MDD_BACKGROUND_TASK_KILL_SWITCH));
    }

    // TODO(b/326254556): ideally it should be removed and the logic moved to getBillEnabled(), but
    // this is a legacy flag that also reads system properties, and the system properties workflow
    // is not unit tested.
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    private boolean getMddLoggerKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(FlagsConstants.KEY_MDD_LOGGER_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_MDD_LOGGER_KILL_SWITCH, MDD_LOGGER_KILL_SWITCH));
    }

    @Override
    public boolean getMddLoggerEnabled() {
        return getGlobalKillSwitch()
                ? false
                : !getFlagFromSystemPropertiesOrDeviceConfig(
                        KEY_MDD_LOGGER_KILL_SWITCH, MDD_LOGGER_KILL_SWITCH);
    }

    // FLEDGE Kill switches

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getFledgeSelectAdsKillSwitch() {
        // We check the Global Kill switch first. As a result, it overrides all other kill switches.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(FlagsConstants.KEY_FLEDGE_SELECT_ADS_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_FLEDGE_SELECT_ADS_KILL_SWITCH,
                                FLEDGE_SELECT_ADS_KILL_SWITCH));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getFledgeCustomAudienceServiceKillSwitch() {
        // We check the Global Kill switch first. As a result, it overrides all other kill switches.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH,
                                FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getProtectedSignalsEnabled() {
        // We check the Global Kill switch first. As a result, it overrides all other kill switches.
        return getGlobalKillSwitch()
                ? false
                : SystemProperties.getBoolean(
                        getSystemPropertyName(FlagsConstants.KEY_PROTECTED_SIGNALS_ENABLED),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_PROTECTED_SIGNALS_ENABLED,
                                PROTECTED_SIGNALS_ENABLED));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getFledgeAuctionServerKillSwitch() {
        return getFledgeSelectAdsKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_KILL_SWITCH,
                                FLEDGE_AUCTION_SERVER_KILL_SWITCH));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getFledgeOnDeviceAuctionKillSwitch() {
        return getFledgeSelectAdsKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_FLEDGE_ON_DEVICE_AUCTION_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_FLEDGE_ON_DEVICE_AUCTION_KILL_SWITCH,
                                FLEDGE_ON_DEVICE_AUCTION_KILL_SWITCH));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getEncryptionKeyNewEnrollmentFetchKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_ENCRYPTION_KEY_NEW_ENROLLMENT_FETCH_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_ENCRYPTION_KEY_NEW_ENROLLMENT_FETCH_KILL_SWITCH,
                                ENCRYPTION_KEY_NEW_ENROLLMENT_FETCH_KILL_SWITCH));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getEncryptionKeyPeriodicFetchKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_ENCRYPTION_KEY_PERIODIC_FETCH_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_ENCRYPTION_KEY_PERIODIC_FETCH_KILL_SWITCH,
                                ENCRYPTION_KEY_PERIODIC_FETCH_KILL_SWITCH));
    }

    // Encryption key related flags.
    @Override
    public int getEncryptionKeyJobRequiredNetworkType() {
        return getDeviceConfigFlag(
                KEY_ENCRYPTION_KEY_JOB_REQUIRED_NETWORK_TYPE,
                ENCRYPTION_KEY_JOB_REQUIRED_NETWORK_TYPE);
    }

    @Override
    public long getEncryptionKeyJobPeriodMs() {
        return getDeviceConfigFlag(KEY_ENCRYPTION_KEY_JOB_PERIOD_MS, ENCRYPTION_KEY_JOB_PERIOD_MS);
    }

    @Override
    public boolean getEnableMddEncryptionKeys() {
        return getDeviceConfigFlag(KEY_ENABLE_MDD_ENCRYPTION_KEYS, ENABLE_MDD_ENCRYPTION_KEYS);
    }

    @Override
    public String getMddEncryptionKeysManifestFileUrl() {
        return getDeviceConfigFlag(
                KEY_MDD_ENCRYPTION_KEYS_MANIFEST_FILE_URL, MDD_ENCRYPTION_KEYS_MANIFEST_FILE_URL);
    }

    @Override
    public String getPpapiAppAllowList() {
        return getDeviceConfigFlag(FlagsConstants.KEY_PPAPI_APP_ALLOW_LIST, PPAPI_APP_ALLOW_LIST);
    }

    @Override
    public String getPasAppAllowList() {
        // default to using the same fixed list as custom audiences
        return getDeviceConfigFlag(FlagsConstants.KEY_PAS_APP_ALLOW_LIST, PPAPI_APP_ALLOW_LIST);
    }

    @Override
    public String getAdIdApiAppBlockList() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_AD_ID_API_APP_BLOCK_LIST, AD_ID_API_APP_BLOCK_LIST);
    }

    @Override
    public String getMsmtApiAppAllowList() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MSMT_API_APP_ALLOW_LIST, MSMT_API_APP_ALLOW_LIST);
    }

    @Override
    public String getMsmtApiAppBlockList() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MSMT_API_APP_BLOCK_LIST, MSMT_API_APP_BLOCK_LIST);
    }

    // AdServices APK SHA certs.
    @Override
    public String getAdservicesApkShaCertificate() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ADSERVICES_APK_SHA_CERTS, ADSERVICES_APK_SHA_CERTIFICATE);
    }

    // PPAPI Signature allow-list.
    @Override
    public String getPpapiAppSignatureAllowList() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_PPAPI_APP_SIGNATURE_ALLOW_LIST, PPAPI_APP_SIGNATURE_ALLOW_LIST);
    }

    // AppSearch writer allow-list
    @Override
    public String getAppsearchWriterAllowListOverride() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_APPSEARCH_WRITER_ALLOW_LIST_OVERRIDE,
                APPSEARCH_WRITER_ALLOW_LIST_OVERRIDE);
    }

    // Rate Limit Flags.
    @Override
    public float getSdkRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                FlagsConstants.KEY_SDK_REQUEST_PERMITS_PER_SECOND, SDK_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getAdIdRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                FlagsConstants.KEY_ADID_REQUEST_PERMITS_PER_SECOND,
                ADID_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getAppSetIdRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                FlagsConstants.KEY_APPSETID_REQUEST_PERMITS_PER_SECOND,
                APPSETID_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getMeasurementRegisterSourceRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                FlagsConstants.KEY_MEASUREMENT_REGISTER_SOURCE_REQUEST_PERMITS_PER_SECOND,
                MEASUREMENT_REGISTER_SOURCE_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getMeasurementRegisterSourcesRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                FlagsConstants.KEY_MEASUREMENT_REGISTER_SOURCES_REQUEST_PERMITS_PER_SECOND,
                MEASUREMENT_REGISTER_SOURCES_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getMeasurementRegisterWebSourceRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                FlagsConstants.KEY_MEASUREMENT_REGISTER_WEB_SOURCE_REQUEST_PERMITS_PER_SECOND,
                MEASUREMENT_REGISTER_WEB_SOURCE_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getMeasurementRegisterTriggerRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                FlagsConstants.KEY_MEASUREMENT_REGISTER_TRIGGER_REQUEST_PERMITS_PER_SECOND,
                MEASUREMENT_REGISTER_TRIGGER_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getMeasurementRegisterWebTriggerRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                FlagsConstants.KEY_MEASUREMENT_REGISTER_WEB_TRIGGER_REQUEST_PERMITS_PER_SECOND,
                MEASUREMENT_REGISTER_WEB_TRIGGER_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getTopicsApiAppRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                FlagsConstants.KEY_TOPICS_API_APP_REQUEST_PERMITS_PER_SECOND,
                TOPICS_API_APP_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getTopicsApiSdkRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                FlagsConstants.KEY_TOPICS_API_SDK_REQUEST_PERMITS_PER_SECOND,
                TOPICS_API_SDK_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgeJoinCustomAudienceRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                FlagsConstants.KEY_FLEDGE_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgeFetchAndJoinCustomAudienceRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                FlagsConstants.KEY_FLEDGE_FETCH_AND_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_FETCH_AND_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgeScheduleCustomAudienceUpdateRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                FlagsConstants
                        .KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgeLeaveCustomAudienceRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                FlagsConstants.KEY_FLEDGE_LEAVE_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_LEAVE_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgeUpdateSignalsRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                FlagsConstants.KEY_FLEDGE_UPDATE_SIGNALS_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_UPDATE_SIGNALS_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgeSelectAdsRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                FlagsConstants.KEY_FLEDGE_SELECT_ADS_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_SELECT_ADS_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgeSelectAdsWithOutcomesRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                FlagsConstants.KEY_FLEDGE_SELECT_ADS_WITH_OUTCOMES_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_SELECT_ADS_WITH_OUTCOMES_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgeGetAdSelectionDataRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                FlagsConstants.KEY_FLEDGE_GET_AD_SELECTION_DATA_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_GET_AD_SELECTION_DATA_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgePersistAdSelectionResultRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                FlagsConstants.KEY_FLEDGE_PERSIST_AD_SELECTION_RESULT_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_PERSIST_AD_SELECTION_RESULT_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgeReportImpressionRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                FlagsConstants.KEY_FLEDGE_REPORT_IMPRESSION_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_REPORT_IMPRESSION_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgeReportInteractionRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                FlagsConstants.KEY_FLEDGE_REPORT_INTERACTION_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_REPORT_INTERACTION_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgeSetAppInstallAdvertisersRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                FlagsConstants.KEY_FLEDGE_SET_APP_INSTALL_ADVERTISERS_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_SET_APP_INSTALL_ADVERTISERS_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgeUpdateAdCounterHistogramRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                FlagsConstants.KEY_FLEDGE_UPDATE_AD_COUNTER_HISTOGRAM_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_UPDATE_AD_COUNTER_HISTOGRAM_REQUEST_PERMITS_PER_SECOND);
    }

    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    private float getPermitsPerSecond(String flagName, float defaultValue) {
        try {
            final String permitString = SystemProperties.get(getSystemPropertyName(flagName));
            if (!TextUtils.isEmpty(permitString)) {
                return parseFloat(permitString);
            }
        } catch (NumberFormatException e) {
            LogUtil.e(e, "Failed to parse %s", flagName);
            return defaultValue;
        }

        return getDeviceConfigFlag(flagName, defaultValue);
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public String getUiOtaStringsManifestFileUrl() {
        return SystemProperties.get(
                getSystemPropertyName(FlagsConstants.KEY_UI_OTA_STRINGS_MANIFEST_FILE_URL),
                getDeviceConfigFlag(
                        FlagsConstants.KEY_UI_OTA_STRINGS_MANIFEST_FILE_URL,
                        UI_OTA_STRINGS_MANIFEST_FILE_URL));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getUiOtaStringsFeatureEnabled() {
        if (getGlobalKillSwitch()) {
            return false;
        }
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_UI_OTA_STRINGS_FEATURE_ENABLED),
                getDeviceConfigFlag(
                        FlagsConstants.KEY_UI_OTA_STRINGS_FEATURE_ENABLED,
                        UI_OTA_STRINGS_FEATURE_ENABLED));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public String getUiOtaResourcesManifestFileUrl() {
        return SystemProperties.get(
                getSystemPropertyName(FlagsConstants.KEY_UI_OTA_RESOURCES_MANIFEST_FILE_URL),
                getDeviceConfigFlag(
                        FlagsConstants.KEY_UI_OTA_RESOURCES_MANIFEST_FILE_URL,
                        UI_OTA_RESOURCES_MANIFEST_FILE_URL));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getUiOtaResourcesFeatureEnabled() {
        if (getGlobalKillSwitch()) {
            return false;
        }
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_UI_OTA_RESOURCES_FEATURE_ENABLED),
                getDeviceConfigFlag(
                        FlagsConstants.KEY_UI_OTA_RESOURCES_FEATURE_ENABLED,
                        UI_OTA_RESOURCES_FEATURE_ENABLED));
    }

    @Override
    public long getUiOtaStringsDownloadDeadline() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_UI_OTA_STRINGS_DOWNLOAD_DEADLINE,
                UI_OTA_STRINGS_DOWNLOAD_DEADLINE);
    }

    @Override
    public boolean isUiFeatureTypeLoggingEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_UI_FEATURE_TYPE_LOGGING_ENABLED,
                UI_FEATURE_TYPE_LOGGING_ENABLED);
    }

    @Override
    public boolean getAdServicesEnabled() {
        // if the global kill switch is enabled, feature should be disabled.
        if (getGlobalKillSwitch()) {
            return false;
        }
        return getDeviceConfigFlag(FlagsConstants.KEY_ADSERVICES_ENABLED, ADSERVICES_ENABLED);
    }

    @Override
    public int getNumberOfEpochsToKeepInHistory() {
        int numberOfEpochsToKeepInHistory =
                getDeviceConfigFlag(
                        FlagsConstants.KEY_NUMBER_OF_EPOCHS_TO_KEEP_IN_HISTORY,
                        NUMBER_OF_EPOCHS_TO_KEEP_IN_HISTORY);

        if (numberOfEpochsToKeepInHistory < 1) {
            throw new IllegalArgumentException("numberOfEpochsToKeepInHistory should  >= 0");
        }

        return numberOfEpochsToKeepInHistory;
    }

    @Override
    public boolean getFledgeAdSelectionPrebuiltUriEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AD_SELECTION_PREBUILT_URI_ENABLED,
                FLEDGE_AD_SELECTION_PREBUILT_URI_ENABLED);
    }

    @Override
    public ImmutableList<Integer> getFledgeAuctionServerPayloadBucketSizes() {
        String bucketSizesString =
                getDeviceConfigFlag(
                        FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_PAYLOAD_BUCKET_SIZES, null);
        // TODO(b/290401812): Decide the fate of malformed bucket size config string.
        return Optional.ofNullable(bucketSizesString)
                .map(
                        s ->
                                Arrays.stream(s.split(FlagsConstants.ARRAY_SPLITTER_COMMA))
                                        .map(Integer::valueOf)
                                        .collect(Collectors.toList()))
                .map(ImmutableList::copyOf)
                .orElse(FLEDGE_AUCTION_SERVER_PAYLOAD_BUCKET_SIZES);
    }

    @Override
    public boolean getFledgeAuctionServerForceSearchWhenOwnerIsAbsentEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_FORCE_SEARCH_WHEN_OWNER_IS_ABSENT_ENABLED,
                FLEDGE_AUCTION_SERVER_FORCE_SEARCH_WHEN_OWNER_IS_ABSENT_ENABLED);
    }

    @Override
    public boolean getAdSelectionOffDeviceRequestCompressionEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AD_SELECTION_OFF_DEVICE_REQUEST_COMPRESSION_ENABLED,
                FLEDGE_AD_SELECTION_OFF_DEVICE_REQUEST_COMPRESSION_ENABLED);
    }

    @Override
    public boolean getFledgeAuctionServerEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENABLED, FLEDGE_AUCTION_SERVER_ENABLED);
    }

    @Override
    public boolean getFledgeAuctionServerEnabledForReportImpression() {
        return getFledgeAuctionServerEnabled()
                && getDeviceConfigFlag(
                        FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENABLED_FOR_REPORT_IMPRESSION,
                        FLEDGE_AUCTION_SERVER_ENABLED_FOR_REPORT_IMPRESSION);
    }

    @Override
    public boolean getFledgeAuctionServerEnabledForReportEvent() {
        return getFledgeAuctionServerEnabled()
                && getDeviceConfigFlag(
                        FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENABLED_FOR_REPORT_EVENT,
                        FLEDGE_AUCTION_SERVER_ENABLED_FOR_REPORT_EVENT);
    }

    @Override
    public boolean getFledgeAuctionServerEnabledForUpdateHistogram() {
        return getFledgeAuctionServerEnabled()
                && getDeviceConfigFlag(
                        FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENABLED_FOR_UPDATE_HISTOGRAM,
                        FLEDGE_AUCTION_SERVER_ENABLED_FOR_UPDATE_HISTOGRAM);
    }

    @Override
    public boolean getFledgeAuctionServerEnabledForSelectAdsMediation() {
        return getFledgeAuctionServerEnabled()
                && getDeviceConfigFlag(
                        FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENABLED_FOR_SELECT_ADS_MEDIATION,
                        FLEDGE_AUCTION_SERVER_ENABLED_FOR_SELECT_ADS_MEDIATION);
    }

    @Override
    public boolean getFledgeAuctionServerEnableAdFilterInGetAdSelectionData() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENABLE_AD_FILTER_IN_GET_AD_SELECTION_DATA,
                FLEDGE_AUCTION_SERVER_ENABLE_AD_FILTER_IN_GET_AD_SELECTION_DATA);
    }

    @Override
    public boolean getFledgeAuctionServerMediaTypeChangeEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_MEDIA_TYPE_CHANGE_ENABLED,
                FLEDGE_AUCTION_SERVER_MEDIA_TYPE_CHANGE_ENABLED);
    }

    @Override
    public String getFledgeAuctionServerAuctionKeyFetchUri() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_URI,
                FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_URI);
    }

    @Override
    public boolean getFledgeAuctionServerRefreshExpiredKeysDuringAuction() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_REFRESH_EXPIRED_KEYS_DURING_AUCTION,
                FLEDGE_AUCTION_SERVER_REFRESH_EXPIRED_KEYS_DURING_AUCTION);
    }

    @Override
    public boolean getFledgeAuctionServerRequestFlagsEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_REQUEST_FLAGS_ENABLED,
                FLEDGE_AUCTION_SERVER_REQUEST_FLAGS_ENABLED);
    }

    @Override
    public boolean getFledgeAuctionServerOmitAdsEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_OMIT_ADS_ENABLED,
                FLEDGE_AUCTION_SERVER_OMIT_ADS_ENABLED);
    }

    @Override
    public String getFledgeAuctionServerJoinKeyFetchUri() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_JOIN_KEY_FETCH_URI,
                FLEDGE_AUCTION_SERVER_JOIN_KEY_FETCH_URI);
    }

    @Override
    public long getFledgeAuctionServerEncryptionKeyMaxAgeSeconds() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENCRYPTION_KEY_MAX_AGE_SECONDS,
                FLEDGE_AUCTION_SERVER_ENCRYPTION_KEY_MAX_AGE_SECONDS);
    }

    @Override
    public int getFledgeAuctionServerAuctionKeySharding() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_AUCTION_KEY_SHARDING,
                FLEDGE_AUCTION_SERVER_AUCTION_KEY_SHARDING);
    }

    public int getFledgeAuctionServerEncryptionAlgorithmKemId() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_KEM_ID,
                FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_KEM_ID);
    }

    @Override
    public int getFledgeAuctionServerEncryptionAlgorithmKdfId() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_KDF_ID,
                FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_KDF_ID);
    }

    @Override
    public int getFledgeAuctionServerEncryptionAlgorithmAeadId() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_AEAD_ID,
                FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_AEAD_ID);
    }

    @Override
    public long getFledgeAuctionServerAuctionKeyFetchTimeoutMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_TIMEOUT_MS,
                FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_TIMEOUT_MS);
    }

    @Override
    public long getFledgeAuctionServerOverallTimeoutMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_OVERALL_TIMEOUT_MS,
                FLEDGE_AUCTION_SERVER_OVERALL_TIMEOUT_MS);
    }

    @Override
    public boolean getFledgeAuctionServerBackgroundKeyFetchJobEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_ENABLED,
                FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_ENABLED);
    }

    @Override
    public boolean getFledgeAuctionServerBackgroundAuctionKeyFetchEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_AUCTION_KEY_FETCH_ENABLED,
                FLEDGE_AUCTION_SERVER_BACKGROUND_AUCTION_KEY_FETCH_ENABLED);
    }

    @Override
    public boolean getFledgeAuctionServerBackgroundJoinKeyFetchEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_JOIN_KEY_FETCH_ENABLED,
                FLEDGE_AUCTION_SERVER_BACKGROUND_JOIN_KEY_FETCH_ENABLED);
    }

    @Override
    public int getFledgeAuctionServerBackgroundKeyFetchNetworkConnectTimeoutMs() {
        return getDeviceConfigFlag(
                FlagsConstants
                        .KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_CONNECT_TIMEOUT_MS,
                FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_CONNECT_TIMEOUT_MS);
    }

    @Override
    public int getFledgeAuctionServerBackgroundKeyFetchNetworkReadTimeoutMs() {
        return getDeviceConfigFlag(
                FlagsConstants
                        .KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_READ_TIMEOUT_MS,
                FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_READ_TIMEOUT_MS);
    }

    @Override
    public int getFledgeAuctionServerBackgroundKeyFetchMaxResponseSizeB() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RESPONSE_SIZE_B,
                FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RESPONSE_SIZE_B);
    }

    @Override
    public long getFledgeAuctionServerBackgroundKeyFetchJobMaxRuntimeMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RUNTIME_MS,
                FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RUNTIME_MS);
    }

    @Override
    public long getFledgeAuctionServerBackgroundKeyFetchJobPeriodMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_PERIOD_MS,
                FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_PERIOD_MS);
    }

    @Override
    public long getFledgeAuctionServerBackgroundKeyFetchJobFlexMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_FLEX_MS,
                FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_FLEX_MS);
    }

    @Override
    public boolean getFledgeAuctionServerBackgroundKeyFetchOnEmptyDbAndInAdvanceEnabled() {
        String key =
                KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_ON_EMPTY_DB_AND_IN_ADVANCE_ENABLED;
        return getDeviceConfigFlag(
                key, FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_ON_EMPTY_DB_AND_IN_ADVANCE_ENABLED);
    }

    @Override
    public long getFledgeAuctionServerBackgroundKeyFetchInAdvanceIntervalMs() {
        return getDeviceConfigFlag(
                FlagsConstants
                        .KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_IN_ADVANCE_INTERVAL_MS,
                FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_IN_ADVANCE_INTERVAL_MS);
    }

    @Override
    public int getFledgeAuctionServerCompressionAlgorithmVersion() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_COMPRESSION_ALGORITHM_VERSION,
                FLEDGE_AUCTION_SERVER_COMPRESSION_ALGORITHM_VERSION);
    }

    @Override
    public int getFledgeAuctionServerPayloadFormatVersion() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_PAYLOAD_FORMAT_VERSION,
                FLEDGE_AUCTION_SERVER_PAYLOAD_FORMAT_VERSION);
    }

    @Override
    public boolean getFledgeAuctionServerEnableDebugReporting() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENABLE_DEBUG_REPORTING,
                FLEDGE_AUCTION_SERVER_ENABLE_DEBUG_REPORTING);
    }

    @Override
    public long getFledgeAuctionServerAdIdFetcherTimeoutMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_AD_ID_FETCHER_TIMEOUT_MS,
                DEFAULT_AUCTION_SERVER_AD_ID_FETCHER_TIMEOUT_MS);
    }

    @Override
    public boolean getFledgeAuctionServerEnablePasUnlimitedEgress() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENABLE_PAS_UNLIMITED_EGRESS,
                DEFAULT_FLEDGE_AUCTION_SERVER_ENABLE_PAS_UNLIMITED_EGRESS);
    }

    @Override
    public boolean getFledgeAuctionServerAdRenderIdEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_AD_RENDER_ID_ENABLED,
                FLEDGE_AUCTION_SERVER_AD_RENDER_ID_ENABLED);
    }

    /** Returns the max length of Ad Render Id. */
    @Override
    public long getFledgeAuctionServerAdRenderIdMaxLength() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_AD_RENDER_ID_MAX_LENGTH,
                FLEDGE_AUCTION_SERVER_AD_RENDER_ID_MAX_LENGTH);
    }

    @Override
    public String getFledgeAuctionServerCoordinatorUrlAllowlist() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_COORDINATOR_URL_ALLOWLIST,
                FLEDGE_AUCTION_SERVER_COORDINATOR_URL_ALLOWLIST);
    }

    @Override
    public boolean getFledgeAuctionServerGetAdSelectionDataPayloadMetricsEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants
                        .KEY_FLEDGE_AUCTION_SERVER_GET_AD_SELECTION_DATA_PAYLOAD_METRICS_ENABLED,
                FLEDGE_AUCTION_SERVER_GET_AD_SELECTION_DATA_PAYLOAD_METRICS_ENABLED);
    }

    @Override
    public boolean getFledgeGetAdSelectionDataSellerConfigurationEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_GET_AD_SELECTION_DATA_SELLER_CONFIGURATION_ENABLED,
                FLEDGE_GET_AD_SELECTION_DATA_SELLER_CONFIGURATION_ENABLED);
    }

    @Override
    public int getFledgeGetAdSelectionDataBuyerInputCreatorVersion() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_GET_AD_SELECTION_DATA_BUYER_INPUT_CREATOR_VERSION,
                FLEDGE_GET_AD_SELECTION_DATA_BUYER_INPUT_CREATOR_VERSION);
    }

    @Override
    public int getFledgeGetAdSelectionDataMaxNumEntirePayloadCompressions() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_GET_AD_SELECTION_DATA_MAX_NUM_ENTIRE_PAYLOAD_COMPRESSIONS,
                FLEDGE_GET_AD_SELECTION_DATA_MAX_NUM_ENTIRE_PAYLOAD_COMPRESSIONS);
    }

    @Override
    public boolean getFledgeGetAdSelectionDataDeserializeOnlyAdRenderIds() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_GET_AD_SELECTION_DATA_DESERIALIZE_ONLY_AD_RENDER_IDS,
                FLEDGE_GET_AD_SELECTION_DATA_DESERIALIZE_ONLY_AD_RENDER_IDS);
    }

    @Override
    public boolean getFledgeAuctionServerMultiCloudEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_MULTI_CLOUD_ENABLED,
                FLEDGE_AUCTION_SERVER_MULTI_CLOUD_ENABLED);
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean isDisableTopicsEnrollmentCheck() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_DISABLE_TOPICS_ENROLLMENT_CHECK),
                getDeviceConfigFlag(
                        FlagsConstants.KEY_DISABLE_TOPICS_ENROLLMENT_CHECK,
                        DISABLE_TOPICS_ENROLLMENT_CHECK));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean isDisableMeasurementEnrollmentCheck() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_DISABLE_MEASUREMENT_ENROLLMENT_CHECK),
                getDeviceConfigFlag(
                        FlagsConstants.KEY_DISABLE_MEASUREMENT_ENROLLMENT_CHECK,
                        DISABLE_MEASUREMENT_ENROLLMENT_CHECK));
    }

    @Override
    public boolean isEnableEnrollmentTestSeed() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ENABLE_ENROLLMENT_TEST_SEED, ENABLE_ENROLLMENT_TEST_SEED);
    }

    @Override
    public boolean getEnrollmentMddRecordDeletionEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ENROLLMENT_MDD_RECORD_DELETION_ENABLED,
                ENROLLMENT_MDD_RECORD_DELETION_ENABLED);
    }

    @Override
    public boolean getEnrollmentEnableLimitedLogging() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ENROLLMENT_ENABLE_LIMITED_LOGGING,
                ENROLLMENT_ENABLE_LIMITED_LOGGING);
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getDisableFledgeEnrollmentCheck() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK),
                getDeviceConfigFlag(
                        FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK,
                        DISABLE_FLEDGE_ENROLLMENT_CHECK));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead

    public boolean getEnforceForegroundStatusForTopics() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_TOPICS),
                getDeviceConfigFlag(
                        FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_TOPICS,
                        ENFORCE_FOREGROUND_STATUS_TOPICS));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getEnforceForegroundStatusForSignals() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_SIGNALS),
                getDeviceConfigFlag(
                        FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_SIGNALS,
                        ENFORCE_FOREGROUND_STATUS_SIGNALS));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getEnforceForegroundStatusForAdId() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_ADID),
                getDeviceConfigFlag(
                        FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_ADID,
                        ENFORCE_FOREGROUND_STATUS_ADID));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getEnforceForegroundStatusForAppSetId() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_APPSETID),
                getDeviceConfigFlag(
                        FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_APPSETID,
                        ENFORCE_FOREGROUND_STATUS_APPSETID));
    }

    @Override
    public boolean getFledgeEventLevelDebugReportingEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_ENABLED,
                FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_ENABLED);
    }

    @Override
    public boolean getFledgeEventLevelDebugReportSendImmediately() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORT_SEND_IMMEDIATELY,
                FLEDGE_EVENT_LEVEL_DEBUG_REPORT_SEND_IMMEDIATELY);
    }

    @Override
    public int getFledgeEventLevelDebugReportingBatchDelaySeconds() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_BATCH_DELAY_SECONDS,
                FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_BATCH_DELAY_SECONDS);
    }

    @Override
    public int getFledgeEventLevelDebugReportingMaxItemsPerBatch() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_MAX_ITEMS_PER_BATCH,
                FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_MAX_ITEMS_PER_BATCH);
    }

    @Override
    public int getFledgeDebugReportSenderJobNetworkConnectionTimeoutMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_DEBUG_REPORTI_SENDER_JOB_NETWORK_CONNECT_TIMEOUT_MS,
                FLEDGE_DEBUG_REPORT_SENDER_JOB_NETWORK_CONNECT_TIMEOUT_MS);
    }

    @Override
    public int getFledgeDebugReportSenderJobNetworkReadTimeoutMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_DEBUG_REPORTI_SENDER_JOB_NETWORK_READ_TIMEOUT_MS,
                FLEDGE_DEBUG_REPORT_SENDER_JOB_NETWORK_READ_TIMEOUT_MS);
    }

    @Override
    public long getFledgeDebugReportSenderJobMaxRuntimeMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_DEBUG_REPORTI_SENDER_JOB_MAX_TIMEOUT_MS,
                FLEDGE_DEBUG_REPORT_SENDER_JOB_MAX_RUNTIME_MS);
    }

    @Override
    public long getFledgeDebugReportSenderJobPeriodMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_DEBUG_REPORT_SENDER_JOB_PERIOD_MS,
                FLEDGE_DEBUG_REPORT_SENDER_JOB_PERIOD_MS);
    }

    @Override
    public long getFledgeDebugReportSenderJobFlexMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_DEBUG_REPORT_SENDER_JOB_FLEX_MS,
                FLEDGE_DEBUG_REPORT_SENDER_JOB_FLEX_MS);
    }

    @Override
    public boolean getEnforceForegroundStatusForFledgeRunAdSelection() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION,
                ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION);
    }

    @Override
    public boolean getEnforceForegroundStatusForFledgeReportImpression() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION,
                ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION);
    }

    @Override
    public boolean getEnforceForegroundStatusForFledgeReportInteraction() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_INTERACTION,
                ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_INTERACTION);
    }

    @Override
    public int getForegroundStatuslLevelForValidation() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FOREGROUND_STATUS_LEVEL, FOREGROUND_STATUS_LEVEL);
    }

    @Override
    public boolean getEnforceForegroundStatusForFledgeOverrides() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDE,
                ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDES);
    }

    @Override
    public boolean getEnforceForegroundStatusForFledgeCustomAudience() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE,
                ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE);
    }

    @Override
    public boolean getEnforceForegroundStatusForFetchAndJoinCustomAudience() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_FETCH_AND_JOIN_CUSTOM_AUDIENCE,
                ENFORCE_FOREGROUND_STATUS_FETCH_AND_JOIN_CUSTOM_AUDIENCE);
    }

    @Override
    public boolean getEnforceForegroundStatusForLeaveCustomAudience() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_LEAVE_CUSTOM_AUDIENCE,
                ENFORCE_FOREGROUND_STATUS_LEAVE_CUSTOM_AUDIENCE);
    }

    @Override
    public boolean getEnforceForegroundStatusForScheduleCustomAudience() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_SCHEDULE_CUSTOM_AUDIENCE,
                ENFORCE_FOREGROUND_STATUS_SCHEDULE_CUSTOM_AUDIENCE);
    }

    @Override
    public boolean getEnableCustomAudienceComponentAds() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ENABLE_CUSTOM_AUDIENCE_COMPONENT_ADS,
                ENABLE_CUSTOM_AUDIENCE_COMPONENT_ADS);
    }

    @Override
    public int getMaxComponentAdsPerCustomAudience() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MAX_COMPONENT_ADS_PER_CUSTOM_AUDIENCE,
                MAX_COMPONENT_ADS_PER_CUSTOM_AUDIENCE);
    }

    @Override
    public int getComponentAdRenderIdMaxLengthBytes() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_COMPONENT_AD_RENDER_ID_MAX_LENGTH_BYTES,
                COMPONENT_AD_RENDER_ID_MAX_LENGTH_BYTES);
    }

    @Override
    public boolean getFledgeRegisterAdBeaconEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED,
                FLEDGE_REGISTER_AD_BEACON_ENABLED);
    }

    @Override
    public boolean getFledgeCpcBillingEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_CPC_BILLING_ENABLED, FLEDGE_CPC_BILLING_ENABLED);
    }

    @Override
    public boolean getFledgeDataVersionHeaderEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_DATA_VERSION_HEADER_ENABLED,
                FLEDGE_DATA_VERSION_HEADER_ENABLED);
    }

    @Override
    public boolean getFledgeBeaconReportingMetricsEnabled() {
        return getFledgeRegisterAdBeaconEnabled()
                && getDeviceConfigFlag(
                        FlagsConstants.KEY_FLEDGE_BEACON_REPORTING_METRICS_ENABLED,
                        FLEDGE_BEACON_REPORTING_METRICS_ENABLED);
    }

    @Override
    public boolean getFledgeAuctionServerApiUsageMetricsEnabled() {
        return getFledgeAuctionServerEnabled()
                && getDeviceConfigFlag(
                        FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_API_USAGE_METRICS_ENABLED,
                        FLEDGE_AUCTION_SERVER_API_USAGE_METRICS_ENABLED);
    }

    @Override
    public boolean getFledgeAuctionServerKeyFetchMetricsEnabled() {
        return getFledgeAuctionServerEnabled()
                && getDeviceConfigFlag(
                        FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_KEY_FETCH_METRICS_ENABLED,
                        FLEDGE_AUCTION_SERVER_KEY_FETCH_METRICS_ENABLED);
    }

    @Override
    public boolean getFledgeSelectAdsFromOutcomesApiMetricsEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_SELECT_ADS_FROM_OUTCOMES_API_METRICS_ENABLED,
                FLEDGE_SELECT_ADS_FROM_OUTCOMES_API_METRICS_ENABLED);
    }

    @Override
    public boolean getFledgeCpcBillingMetricsEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_CPC_BILLING_METRICS_ENABLED,
                FLEDGE_CPC_BILLING_METRICS_ENABLED);
    }

    @Override
    public boolean getFledgeDataVersionHeaderMetricsEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_DATA_VERSION_HEADER_METRICS_ENABLED,
                FLEDGE_DATA_VERSION_HEADER_METRICS_ENABLED);
    }

    @Override
    public boolean getFledgeReportImpressionApiMetricsEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_REPORT_IMPRESSION_API_METRICS_ENABLED,
                FLEDGE_REPORT_IMPRESSION_API_METRICS_ENABLED);
    }

    @Override
    public boolean getFledgeJsScriptResultCodeMetricsEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_JS_SCRIPT_RESULT_CODE_METRICS_ENABLED,
                FLEDGE_JS_SCRIPT_RESULT_CODE_METRICS_ENABLED);
    }

    @Override
    public boolean getEnforceForegroundStatusForMeasurementDeleteRegistrations() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_DELETE_REGISTRATIONS,
                MEASUREMENT_ENFORCE_FOREGROUND_STATUS_DELETE_REGISTRATIONS);
    }

    @Override
    public boolean getEnforceForegroundStatusForMeasurementRegisterSource() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCE,
                MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCE);
    }

    @Override
    public boolean getEnforceForegroundStatusForMeasurementRegisterTrigger() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_TRIGGER,
                MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_TRIGGER);
    }

    @Override
    public boolean getEnforceForegroundStatusForMeasurementRegisterWebSource() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_SOURCE,
                MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_SOURCE);
    }

    @Override
    public boolean getEnforceForegroundStatusForMeasurementRegisterWebTrigger() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_TRIGGER,
                MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_TRIGGER);
    }

    @Override
    public boolean getEnforceForegroundStatusForMeasurementStatus() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_GET_STATUS,
                MEASUREMENT_ENFORCE_FOREGROUND_STATUS_GET_STATUS);
    }

    @Override
    public boolean getEnforceForegroundStatusForMeasurementRegisterSources() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCES,
                MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCES);
    }

    @Override
    public long getIsolateMaxHeapSizeBytes() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ISOLATE_MAX_HEAP_SIZE_BYTES, ISOLATE_MAX_HEAP_SIZE_BYTES);
    }

    @Override
    public String getWebContextClientAppAllowList() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_WEB_CONTEXT_CLIENT_ALLOW_LIST, WEB_CONTEXT_CLIENT_ALLOW_LIST);
    }

    @Override
    public boolean getConsentManagerLazyEnableMode() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_CONSENT_MANAGER_LAZY_ENABLE_MODE,
                CONSENT_MANAGER_LAZY_ENABLE_MODE);
    }

    @Override
    public boolean getConsentAlreadyInteractedEnableMode() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_CONSENT_ALREADY_INTERACTED_FIX_ENABLE,
                CONSENT_ALREADY_INTERACTED_FIX_ENABLE);
    }

    @Override
    public String getConsentNotificationResetToken() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_CONSENT_NOTIFICATION_RESET_TOKEN,
                CONSENT_NOTIFICATION_RESET_TOKEN);
    }

    @Override
    public long getConsentNotificationIntervalBeginMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_CONSENT_NOTIFICATION_INTERVAL_BEGIN_MS,
                CONSENT_NOTIFICATION_INTERVAL_BEGIN_MS);
    }

    @Override
    public long getConsentNotificationIntervalEndMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_CONSENT_NOTIFICATION_INTERVAL_END_MS,
                CONSENT_NOTIFICATION_INTERVAL_END_MS);
    }

    @Override
    public long getConsentNotificationMinimalDelayBeforeIntervalEnds() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_CONSENT_NOTIFICATION_MINIMAL_DELAY_BEFORE_INTERVAL_ENDS,
                CONSENT_NOTIFICATION_MINIMAL_DELAY_BEFORE_INTERVAL_ENDS);
    }

    @Override
    public int getConsentSourceOfTruth() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_CONSENT_SOURCE_OF_TRUTH, DEFAULT_CONSENT_SOURCE_OF_TRUTH);
    }

    @Override
    public int getBlockedTopicsSourceOfTruth() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_BLOCKED_TOPICS_SOURCE_OF_TRUTH,
                DEFAULT_BLOCKED_TOPICS_SOURCE_OF_TRUTH);
    }

    @Override
    public long getMaxResponseBasedRegistrationPayloadSizeBytes() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES,
                MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES);
    }

    @Override
    public long getMaxTriggerRegistrationHeaderSizeBytes() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MAX_TRIGGER_REGISTRATION_HEADER_SIZE_BYTES,
                MAX_TRIGGER_REGISTRATION_HEADER_SIZE_BYTES);
    }

    @Override
    public long getMaxOdpTriggerRegistrationHeaderSizeBytes() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MAX_ODP_TRIGGER_REGISTRATION_HEADER_SIZE_BYTES,
                MAX_ODP_TRIGGER_REGISTRATION_HEADER_SIZE_BYTES);
    }

    @Override
    public boolean getMeasurementEnableUpdateTriggerHeaderLimit() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_UPDATE_TRIGGER_REGISTRATION_HEADER_LIMIT,
                MEASUREMENT_ENABLE_UPDATE_TRIGGER_REGISTRATION_HEADER_LIMIT);
    }

    @Override
    public boolean getUiDialogsFeatureEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_UI_DIALOGS_FEATURE_ENABLED, UI_DIALOGS_FEATURE_ENABLED);
    }

    @Override
    public boolean getUiDialogFragmentEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_UI_DIALOG_FRAGMENT_ENABLED, UI_DIALOG_FRAGMENT);
    }

    @Override
    public boolean isEeaDeviceFeatureEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_IS_EEA_DEVICE_FEATURE_ENABLED, IS_EEA_DEVICE_FEATURE_ENABLED);
    }

    @Override
    public boolean isEeaDevice() {
        return getDeviceConfigFlag(FlagsConstants.KEY_IS_EEA_DEVICE, IS_EEA_DEVICE);
    }

    @Override
    public boolean getRecordManualInteractionEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_RECORD_MANUAL_INTERACTION_ENABLED,
                RECORD_MANUAL_INTERACTION_ENABLED);
    }

    @Override
    @SuppressWarnings("InlinedApi")
    public boolean isBackCompatActivityFeatureEnabled() {
        // Check if enable Back compat is true first and then check flag value
        return getEnableBackCompat()
                && getDeviceConfigFlag(
                        FlagsConstants.KEY_IS_BACK_COMPACT_ACTIVITY_FEATURE_ENABLED,
                        IS_BACK_COMPACT_ACTIVITY_FEATURE_ENABLED);
    }

    @Override
    public String getUiEeaCountries() {
        return getDeviceConfigFlag(FlagsConstants.KEY_UI_EEA_COUNTRIES, UI_EEA_COUNTRIES);
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getGaUxFeatureEnabled() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_GA_UX_FEATURE_ENABLED),
                getDeviceConfigFlag(
                        FlagsConstants.KEY_GA_UX_FEATURE_ENABLED, GA_UX_FEATURE_ENABLED));
    }

    @Override
    public String getDebugUx() {
        return getDeviceConfigFlag(FlagsConstants.KEY_DEBUG_UX, DEBUG_UX);
    }

    @Override
    public boolean getToggleSpeedBumpEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_UI_TOGGLE_SPEED_BUMP_ENABLED, TOGGLE_SPEED_BUMP_ENABLED);
    }

    @Override
    public long getAdSelectionExpirationWindowS() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_AD_SELECTION_EXPIRATION_WINDOW_S,
                FLEDGE_AD_SELECTION_EXPIRATION_WINDOW_S);
    }

    @Override
    public boolean getMeasurementEnableAggregatableNamedBudgets() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_AGGREGATABLE_NAMED_BUDGETS,
                MEASUREMENT_ENABLE_AGGREGATABLE_NAMED_BUDGETS);
    }

    @Override
    public boolean getMeasurementEnableV1SourceTriggerData() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_V1_SOURCE_TRIGGER_DATA,
                MEASUREMENT_ENABLE_V1_SOURCE_TRIGGER_DATA);
    }

    @Override
    public boolean getMeasurementFlexibleEventReportingApiEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_FLEXIBLE_EVENT_REPORTING_API_ENABLED,
                MEASUREMENT_FLEXIBLE_EVENT_REPORTING_API_ENABLED);
    }

    @Override
    public boolean getMeasurementEnableTriggerDataMatching() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_TRIGGER_DATA_MATCHING,
                MEASUREMENT_ENABLE_TRIGGER_DATA_MATCHING);
    }

    @Override
    public float getMeasurementFlexApiMaxInformationGainEvent() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_EVENT,
                MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_EVENT);
    }

    @Override
    public float getMeasurementFlexApiMaxInformationGainNavigation() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_NAVIGATION,
                MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_NAVIGATION);
    }

    @Override
    public float getMeasurementFlexApiMaxInformationGainDualDestinationEvent() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_EVENT,
                MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_EVENT);
    }

    @Override
    public float getMeasurementFlexApiMaxInformationGainDualDestinationNavigation() {
        return getDeviceConfigFlag(
                FlagsConstants
                        .KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_NAVIGATION,
                MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_NAVIGATION);
    }

    @Override
    public float getMeasurementAttributionScopeMaxInfoGainNavigation() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_NAVIGATION,
                MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_NAVIGATION);
    }

    @Override
    public float getMeasurementAttributionScopeMaxInfoGainDualDestinationNavigation() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_NAVIGATION,
                MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_NAVIGATION);
    }

    @Override
    public float getMeasurementAttributionScopeMaxInfoGainEvent() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_EVENT,
                MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_EVENT);
    }

    @Override
    public float getMeasurementAttributionScopeMaxInfoGainDualDestinationEvent() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_EVENT,
                MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_EVENT);
    }

    @Override
    public boolean getMeasurementEnableFakeReportTriggerTime() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_ENABLE_FAKE_REPORT_TRIGGER_TIME,
                MEASUREMENT_ENABLE_FAKE_REPORT_TRIGGER_TIME);
    }

    @Override
    public long getMeasurementMaxReportStatesPerSourceRegistration() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION,
                MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION);
    }

    @Override
    public int getMeasurementFlexApiMaxEventReports() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_FLEX_API_MAX_EVENT_REPORTS,
                MEASUREMENT_FLEX_API_MAX_EVENT_REPORTS);
    }

    @Override
    public int getMeasurementFlexApiMaxEventReportWindows() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_FLEX_API_MAX_EVENT_REPORT_WINDOWS,
                MEASUREMENT_FLEX_API_MAX_EVENT_REPORT_WINDOWS);
    }

    @Override
    public int getMeasurementFlexApiMaxTriggerDataCardinality() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_FLEX_API_MAX_TRIGGER_DATA_CARDINALITY,
                MEASUREMENT_FLEX_API_MAX_TRIGGER_DATA_CARDINALITY);
    }

    @Override
    public long getMeasurementMinimumEventReportWindowInSeconds() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MINIMUM_EVENT_REPORT_WINDOW_IN_SECONDS,
                MEASUREMENT_MINIMUM_EVENT_REPORT_WINDOW_IN_SECONDS);
    }

    @Override
    public long getMeasurementMinimumAggregatableReportWindowInSeconds() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MINIMUM_AGGREGATABLE_REPORT_WINDOW_IN_SECONDS,
                MEASUREMENT_MINIMUM_AGGREGATABLE_REPORT_WINDOW_IN_SECONDS);
    }

    @Override
    public int getMeasurementMaxSourcesPerPublisher() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_SOURCES_PER_PUBLISHER,
                MEASUREMENT_MAX_SOURCES_PER_PUBLISHER);
    }

    @Override
    public int getMeasurementMaxTriggersPerDestination() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_TRIGGERS_PER_DESTINATION,
                MEASUREMENT_MAX_TRIGGERS_PER_DESTINATION);
    }

    @Override
    public int getMeasurementMaxAggregateReportsPerDestination() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_DESTINATION,
                MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_DESTINATION);
    }

    @Override
    public int getMeasurementMaxEventReportsPerDestination() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_EVENT_REPORTS_PER_DESTINATION,
                MEASUREMENT_MAX_EVENT_REPORTS_PER_DESTINATION);
    }

    @Override
    public int getMeasurementMaxAggregateReportsPerSource() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_SOURCE,
                MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_SOURCE);
    }

    @Override
    public boolean getMeasurementEnableFifoDestinationsDeleteAggregateReports() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_FIFO_DESTINATIONS_DELETE_AGGREGATE_REPORTS,
                MEASUREMENT_ENABLE_FIFO_DESTINATIONS_DELETE_AGGREGATE_REPORTS);
    }

    @Override
    public int getMeasurementMaxAggregateKeysPerSourceRegistration() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_AGGREGATE_KEYS_PER_SOURCE_REGISTRATION,
                MEASUREMENT_MAX_AGGREGATE_KEYS_PER_SOURCE_REGISTRATION);
    }

    @Override
    public int getMeasurementMaxAggregateKeysPerTriggerRegistration() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_AGGREGATE_KEYS_PER_TRIGGER_REGISTRATION,
                MEASUREMENT_MAX_AGGREGATE_KEYS_PER_TRIGGER_REGISTRATION);
    }

    @Override
    public String getMeasurementEventReportsVtcEarlyReportingWindows() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS,
                MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS);
    }

    @Override
    public String getMeasurementEventReportsCtcEarlyReportingWindows() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS,
                MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS);
    }

    @Override
    public String getMeasurementAggregateReportDelayConfig() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_AGGREGATE_REPORT_DELAY_CONFIG,
                MEASUREMENT_AGGREGATE_REPORT_DELAY_CONFIG);
    }

    @Override
    public boolean getMeasurementEnableLookbackWindowFilter() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_LOOKBACK_WINDOW_FILTER,
                MEASUREMENT_ENABLE_LOOKBACK_WINDOW_FILTER);
    }

    @Override
    public boolean isEnrollmentBlocklisted(String enrollmentId) {
        return getEnrollmentBlocklist().contains(enrollmentId);
    }

    @Override
    public boolean getEnableLoggedTopic() {
        return getDeviceConfigFlag(FlagsConstants.KEY_ENABLE_LOGGED_TOPIC, ENABLE_LOGGED_TOPIC);
    }

    @Override
    public boolean getEnableDatabaseSchemaVersion8() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ENABLE_DATABASE_SCHEMA_VERSION_8,
                ENABLE_DATABASE_SCHEMA_VERSION_8);
    }

    @Override
    public boolean getEnableDatabaseSchemaVersion9() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ENABLE_DATABASE_SCHEMA_VERSION_9,
                ENABLE_DATABASE_SCHEMA_VERSION_9);
    }

    @Override
    public boolean getMsmtEnableApiStatusAllowListCheck() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_API_STATUS_ALLOW_LIST_CHECK,
                MEASUREMENT_ENABLE_API_STATUS_ALLOW_LIST_CHECK);
    }

    @Override
    public boolean getMeasurementEnableAttributionScope() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_ATTRIBUTION_SCOPE,
                MEASUREMENT_ENABLE_ATTRIBUTION_SCOPE);
    }

    @Override
    public boolean getMeasurementEnableReinstallReattribution() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_ENABLE_REINSTALL_REATTRIBUTION,
                MEASUREMENT_ENABLE_REINSTALL_REATTRIBUTION);
    }

    @Override
    public long getMeasurementMaxReinstallReattributionWindowSeconds() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_REINSTALL_REATTRIBUTION_WINDOW,
                MEASUREMENT_MAX_REINSTALL_REATTRIBUTION_WINDOW_SECONDS);
    }

    @Override
    public boolean getMeasurementEnableMinReportLifespanForUninstall() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_ENABLE_MIN_REPORT_LIFESPAN_FOR_UNINSTALL,
                MEASUREMENT_ENABLE_MIN_REPORT_LIFESPAN_FOR_UNINSTALL);
    }

    @Override
    public long getMeasurementMinReportLifespanForUninstallSeconds() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MIN_REPORT_LIFESPAN_FOR_UNINSTALL_SECONDS,
                MEASUREMENT_MIN_REPORT_LIFESPAN_FOR_UNINSTALL_SECONDS);
    }

    @Override
    public boolean getMeasurementEnableInstallAttributionOnS() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_ENABLE_INSTALL_ATTRIBUTION_ON_S,
                MEASUREMENT_ENABLE_INSTALL_ATTRIBUTION_ON_S);
    }

    @Override
    public boolean getMeasurementEnableNavigationReportingOriginCheck() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_NAVIGATION_REPORTING_ORIGIN_CHECK,
                MEASUREMENT_ENABLE_NAVIGATION_REPORTING_ORIGIN_CHECK);
    }

    @Override
    public boolean getMeasurementEnableSeparateDebugReportTypesForAttributionRateLimit() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_ENABLE_SEPARATE_DEBUG_REPORT_TYPES_FOR_ATTRIBUTION_RATE_LIMIT,
                MEASUREMENT_ENABLE_SEPARATE_DEBUG_REPORT_TYPES_FOR_ATTRIBUTION_RATE_LIMIT);
    }

    @Override
    public int getMeasurementMaxAttributionScopesPerSource() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_MAX_ATTRIBUTION_SCOPES_PER_SOURCE,
                MEASUREMENT_MAX_ATTRIBUTION_SCOPES_PER_SOURCE);
    }

    @Override
    public int getMeasurementMaxAttributionScopeLength() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_MAX_ATTRIBUTION_SCOPE_LENGTH,
                MEASUREMENT_MAX_ATTRIBUTION_SCOPE_LENGTH);
    }

    @Override
    public int getMeasurementMaxLengthPerBudgetName() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_MAX_LENGTH_PER_BUDGET_NAME, MEASUREMENT_MAX_LENGTH_PER_BUDGET_NAME);
    }

    @Override
    public int getMeasurementMaxNamedBudgetsPerSourceRegistration() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_MAX_NAMED_BUDGETS_PER_SOURCE_REGISTRATION,
                MEASUREMENT_MAX_NAMED_BUDGETS_PER_SOURCE_REGISTRATION);
    }

    @Override
    public boolean getFledgeMeasurementReportAndRegisterEventApiEnabled() {
        return getDeviceConfigFlag(
                KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED,
                FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED);
    }

    @Override
    public boolean getFledgeMeasurementReportAndRegisterEventApiFallbackEnabled() {
        String flagName = KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_FALLBACK_ENABLED;
        boolean defaultValue = FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_FALLBACK_ENABLED;

        return getFledgeMeasurementReportAndRegisterEventApiEnabled()
                && getDeviceConfigFlag(flagName, defaultValue);
    }

    @Override
    public boolean getMeasurementEnableOdpWebTriggerRegistration() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_ODP_WEB_TRIGGER_REGISTRATION,
                MEASUREMENT_ENABLE_ODP_WEB_TRIGGER_REGISTRATION);
    }

    @Override
    public boolean getMeasurementEnableSourceDestinationLimitPriority() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_SOURCE_DESTINATION_LIMIT_PRIORITY,
                MEASUREMENT_ENABLE_DESTINATION_LIMIT_PRIORITY);
    }

    @Override
    public int getMeasurementDefaultSourceDestinationLimitAlgorithm() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_DEFAULT_DESTINATION_LIMIT_ALGORITHM,
                MEASUREMENT_DEFAULT_DESTINATION_LIMIT_ALGORITHM);
    }

    @Override
    public boolean getMeasurementEnableSourceDestinationLimitAlgorithmField() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_DESTINATION_LIMIT_ALGORITHM_FIELD,
                MEASUREMENT_ENABLE_DESTINATION_LIMIT_ALGORITHM_FIELD);
    }

    @Override
    public int getMeasurementDefaultFilteringIdMaxBytes() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES,
                MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES);
    }

    @Override
    public int getMeasurementMaxFilteringIdMaxBytes() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_FILTERING_ID_MAX_BYTES,
                MEASUREMENT_MAX_FILTERING_ID_MAX_BYTES);
    }

    @Override
    public boolean getMeasurementEnableFlexibleContributionFiltering() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_FLEXIBLE_CONTRIBUTION_FILTERING,
                MEASUREMENT_ENABLE_FLEXIBLE_CONTRIBUTION_FILTERING);
    }

    @Override
    public String getAdServicesModuleJobPolicy() {
        return getDeviceConfigFlag(
                KEY_AD_SERVICES_MODULE_JOB_POLICY, AD_SERVICES_MODULE_JOB_POLICY);
    }

    @Override
    public void dump(PrintWriter writer, @Nullable String[] args) {
        // First filter out non-getters...
        Method[] allMethods = Flags.class.getMethods();
        TreeMap<String, Method> dumpableMethods = new TreeMap<>(); // sorted by method name
        for (var method : allMethods) {
            // We could check if starts with is... or get... , but in reality only dump(...) is not
            // a flag, and some take args (like isEnrollmentBlocklisted(enrollmentId))
            if (method.getParameterCount() == 0) {
                dumpableMethods.put(method.getName(), method);
            }
        }

        // ...then print the count
        int numberFlags = dumpableMethods.size();
        writer.printf("%d flags:\n", numberFlags);

        // ...then dump their values
        for (var method : dumpableMethods.values()) {
            String methodName = method.getName() + "()";
            try {
                Object value = method.invoke(this);
                writer.printf("\t%s = %s\n", methodName, value);
            } catch (Exception e) {
                writer.printf("\tFailed to dump value of %s: %s\n", methodName, e);
            }
        }
    }

    @VisibleForTesting
    @Override
    public ImmutableList<String> getEnrollmentBlocklist() {
        String blocklistFlag = getDeviceConfigFlag(FlagsConstants.KEY_ENROLLMENT_BLOCKLIST_IDS, "");

        if (TextUtils.isEmpty(blocklistFlag)) {
            return ImmutableList.of();
        }
        String[] blocklistList = blocklistFlag.split(FlagsConstants.ARRAY_SPLITTER_COMMA);
        return ImmutableList.copyOf(blocklistList);
    }

    @Override
    public boolean getCompatLoggingKillSwitch() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_COMPAT_LOGGING_KILL_SWITCH, COMPAT_LOGGING_KILL_SWITCH);
    }

    @Override
    public boolean getEnableBackCompat() {
        // If SDK is T+, the value should always be false
        // Check the flag value for S Minus
        return !SdkLevel.isAtLeastT()
                && getDeviceConfigFlag(FlagsConstants.KEY_ENABLE_BACK_COMPAT, ENABLE_BACK_COMPAT);
    }

    @Override
    public boolean getEnableAppsearchConsentData() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ENABLE_APPSEARCH_CONSENT_DATA, ENABLE_APPSEARCH_CONSENT_DATA);
    }

    @Override
    public boolean getEnableU18AppsearchMigration() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ENABLE_U18_APPSEARCH_MIGRATION,
                DEFAULT_ENABLE_U18_APPSEARCH_MIGRATION);
    }

    @Override
    public ImmutableList<Integer> getGlobalBlockedTopicIds() {
        String defaultGlobalBlockedTopicIds =
                TOPICS_GLOBAL_BLOCKED_TOPIC_IDS.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(FlagsConstants.ARRAY_SPLITTER_COMMA));

        String globalBlockedTopicIds =
                getDeviceConfigFlag(
                        FlagsConstants.KEY_GLOBAL_BLOCKED_TOPIC_IDS, defaultGlobalBlockedTopicIds);
        if (TextUtils.isEmpty(globalBlockedTopicIds)) {
            return ImmutableList.of();
        }
        globalBlockedTopicIds = globalBlockedTopicIds.trim();
        String[] globalBlockedTopicIdsList =
                globalBlockedTopicIds.split(FlagsConstants.ARRAY_SPLITTER_COMMA);

        List<Integer> globalBlockedTopicIdsIntList = new ArrayList<>();

        for (String blockedTopicId : globalBlockedTopicIdsList) {
            try {
                int topicIdInteger = Integer.parseInt(blockedTopicId.trim());
                globalBlockedTopicIdsIntList.add(topicIdInteger);
            } catch (NumberFormatException e) {
                LogUtil.e("Parsing global blocked topic ids failed for " + globalBlockedTopicIds);
                return TOPICS_GLOBAL_BLOCKED_TOPIC_IDS;
            }
        }
        return ImmutableList.copyOf(globalBlockedTopicIdsIntList);
    }

    @Override
    public ImmutableList<Integer> getErrorCodeLoggingDenyList() {
        String defaultErrorCodeLoggingDenyStr =
                ERROR_CODE_LOGGING_DENY_LIST.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(FlagsConstants.ARRAY_SPLITTER_COMMA));

        String errorCodeLoggingDenyStr =
                getDeviceConfigFlag(
                        FlagsConstants.KEY_ERROR_CODE_LOGGING_DENY_LIST,
                        defaultErrorCodeLoggingDenyStr);
        if (TextUtils.isEmpty(errorCodeLoggingDenyStr)) {
            return ImmutableList.of();
        }
        errorCodeLoggingDenyStr = errorCodeLoggingDenyStr.trim();
        String[] errorCodeLoggingDenyStrList =
                errorCodeLoggingDenyStr.split(FlagsConstants.ARRAY_SPLITTER_COMMA);

        List<Integer> errorCodeLoggingDenyIntList = new ArrayList<>();

        for (String errorCode : errorCodeLoggingDenyStrList) {
            try {
                int errorCodeInteger = Integer.parseInt(errorCode.trim());
                errorCodeLoggingDenyIntList.add(errorCodeInteger);
            } catch (NumberFormatException e) {
                LogUtil.e("Parsing denied error code logging failed for " + errorCode);
                // TODO (b/283323414) : Add CEL for this.
            }
        }
        return ImmutableList.copyOf(errorCodeLoggingDenyIntList);
    }

    @Override
    public long getMeasurementDebugJoinKeyHashLimit() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_DEBUG_JOIN_KEY_HASH_LIMIT,
                DEFAULT_MEASUREMENT_DEBUG_JOIN_KEY_HASH_LIMIT);
    }

    @Override
    public long getMeasurementPlatformDebugAdIdMatchingLimit() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_DEBUG_KEY_AD_ID_MATCHING_LIMIT,
                DEFAULT_MEASUREMENT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT);
    }

    @Override
    public boolean getNotificationDismissedOnClick() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_NOTIFICATION_DISMISSED_ON_CLICK,
                DEFAULT_NOTIFICATION_DISMISSED_ON_CLICK);
    }

    @Override
    public boolean getU18UxEnabled() {
        return getEnableAdServicesSystemApi()
                && getDeviceConfigFlag(FlagsConstants.KEY_U18_UX_ENABLED, DEFAULT_U18_UX_ENABLED);
    }

    @Override
    public boolean getEnableBackCompatInit() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ENABLE_BACK_COMPAT_INIT, DEFAULT_ENABLE_BACK_COMPAT_INIT);
    }

    @Override
    public boolean getEnableAdServicesSystemApi() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ENABLE_AD_SERVICES_SYSTEM_API,
                DEFAULT_ENABLE_AD_SERVICES_SYSTEM_API);
    }

    @Override
    public boolean getPasUxEnabled() {
        if (getEeaPasUxEnabled()) {
            // EEA devices (if EEA device feature is not enabled, assume EEA to be safe)
            if (!isEeaDeviceFeatureEnabled() || isEeaDevice()) {
                return true;
            }
            // ROW devices
            return getDeviceConfigFlag(FlagsConstants.KEY_PAS_UX_ENABLED, DEFAULT_PAS_UX_ENABLED);
        }
        return isEeaDeviceFeatureEnabled()
                && !isEeaDevice()
                && getDeviceConfigFlag(FlagsConstants.KEY_PAS_UX_ENABLED, DEFAULT_PAS_UX_ENABLED);
    }

    @Override
    public boolean getEeaPasUxEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_EEA_PAS_UX_ENABLED, DEFAULT_EEA_PAS_UX_ENABLED);
    }

    @Override
    public Map<String, Boolean> getUxFlags() {
        Map<String, Boolean> uxMap = new HashMap<>();
        uxMap.put(FlagsConstants.KEY_UI_DIALOGS_FEATURE_ENABLED, getUiDialogsFeatureEnabled());
        uxMap.put(FlagsConstants.KEY_UI_DIALOG_FRAGMENT_ENABLED, getUiDialogFragmentEnabled());
        uxMap.put(FlagsConstants.KEY_IS_EEA_DEVICE_FEATURE_ENABLED, isEeaDeviceFeatureEnabled());
        uxMap.put(FlagsConstants.KEY_IS_EEA_DEVICE, isEeaDevice());
        uxMap.put(
                FlagsConstants.KEY_RECORD_MANUAL_INTERACTION_ENABLED,
                getRecordManualInteractionEnabled());
        uxMap.put(FlagsConstants.KEY_GA_UX_FEATURE_ENABLED, getGaUxFeatureEnabled());
        uxMap.put(
                FlagsConstants.KEY_UI_OTA_STRINGS_FEATURE_ENABLED, getUiOtaStringsFeatureEnabled());
        uxMap.put(
                FlagsConstants.KEY_UI_OTA_RESOURCES_FEATURE_ENABLED,
                getUiOtaResourcesFeatureEnabled());
        uxMap.put(
                FlagsConstants.KEY_UI_FEATURE_TYPE_LOGGING_ENABLED,
                isUiFeatureTypeLoggingEnabled());
        uxMap.put(FlagsConstants.KEY_ADSERVICES_ENABLED, getAdServicesEnabled());
        uxMap.put(FlagsConstants.KEY_U18_UX_ENABLED, getU18UxEnabled());
        uxMap.put(
                FlagsConstants.KEY_NOTIFICATION_DISMISSED_ON_CLICK,
                getNotificationDismissedOnClick());
        uxMap.put(
                FlagsConstants.KEY_IS_U18_UX_DETENTION_CHANNEL_ENABLED,
                isU18UxDetentionChannelEnabled());
        uxMap.put(
                FlagsConstants.KEY_IS_U18_SUPERVISED_ACCOUNT_ENABLED,
                isU18SupervisedAccountEnabled());
        uxMap.put(
                FlagsConstants.KEY_CONSENT_ALREADY_INTERACTED_FIX_ENABLE,
                getConsentAlreadyInteractedEnableMode());
        uxMap.put(
                FlagsConstants.KEY_IS_GET_ADSERVICES_COMMON_STATES_API_ENABLED,
                isGetAdServicesCommonStatesApiEnabled());
        uxMap.put(FlagsConstants.KEY_PAS_UX_ENABLED, getPasUxEnabled());
        uxMap.put(FlagsConstants.KEY_EEA_PAS_UX_ENABLED, getEeaPasUxEnabled());
        return uxMap;
    }

    @Override
    public boolean getMeasurementEnableCoarseEventReportDestinations() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_COARSE_EVENT_REPORT_DESTINATIONS,
                DEFAULT_MEASUREMENT_ENABLE_COARSE_EVENT_REPORT_DESTINATIONS);
    }

    @Override
    public int getMeasurementMaxDistinctWebDestinationsInSourceRegistration() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_DISTINCT_WEB_DESTINATIONS_IN_SOURCE_REGISTRATION,
                MEASUREMENT_MAX_DISTINCT_WEB_DESTINATIONS_IN_SOURCE_REGISTRATION);
    }

    @Override
    public long getMeasurementMaxReportingRegisterSourceExpirationInSeconds() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS,
                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
    }

    @Override
    public long getMeasurementMinReportingRegisterSourceExpirationInSeconds() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS,
                MEASUREMENT_MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
    }

    @Override
    public long getMeasurementMaxInstallAttributionWindow() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_INSTALL_ATTRIBUTION_WINDOW,
                MEASUREMENT_MAX_INSTALL_ATTRIBUTION_WINDOW);
    }

    @Override
    public long getMeasurementMinInstallAttributionWindow() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MIN_INSTALL_ATTRIBUTION_WINDOW,
                MEASUREMENT_MIN_INSTALL_ATTRIBUTION_WINDOW);
    }

    @Override
    public long getMeasurementMaxPostInstallExclusivityWindow() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_POST_INSTALL_EXCLUSIVITY_WINDOW,
                MEASUREMENT_MAX_POST_INSTALL_EXCLUSIVITY_WINDOW);
    }

    @Override
    public long getMeasurementMinPostInstallExclusivityWindow() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MIN_POST_INSTALL_EXCLUSIVITY_WINDOW,
                MEASUREMENT_MIN_POST_INSTALL_EXCLUSIVITY_WINDOW);
    }

    @Override
    public int getMeasurementMaxSumOfAggregateValuesPerSource() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE,
                MEASUREMENT_MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE);
    }

    @Override
    public long getMeasurementRateLimitWindowMilliseconds() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_RATE_LIMIT_WINDOW_MILLISECONDS,
                MEASUREMENT_RATE_LIMIT_WINDOW_MILLISECONDS);
    }

    @Override
    public long getMeasurementMinReportingOriginUpdateWindow() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MIN_REPORTING_ORIGIN_UPDATE_WINDOW,
                MEASUREMENT_MIN_REPORTING_ORIGIN_UPDATE_WINDOW);
    }

    @Override
    public boolean getMeasurementEnablePreinstallCheck() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_PREINSTALL_CHECK,
                MEASUREMENT_ENABLE_PREINSTALL_CHECK);
    }

    @Override
    public int getMeasurementVtcConfigurableMaxEventReportsCount() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT,
                DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT);
    }

    @Override
    public boolean getMeasurementEnableAraDeduplicationAlignmentV1() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_ARA_DEDUPLICATION_ALIGNMENT_V1,
                MEASUREMENT_ENABLE_ARA_DEDUPLICATION_ALIGNMENT_V1);
    }

    @Override
    public boolean getMeasurementEnableSourceDeactivationAfterFiltering() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_SOURCE_DEACTIVATION_AFTER_FILTERING,
                MEASUREMENT_ENABLE_SOURCE_DEACTIVATION_AFTER_FILTERING);
    }

    @Override
    public boolean getMeasurementEnableReportingJobsThrowUnaccountedException() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_UNACCOUNTED_EXCEPTION,
                MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_UNACCOUNTED_EXCEPTION);
    }

    @Override
    public boolean getMeasurementEnableReportingJobsThrowJsonException() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_JSON_EXCEPTION,
                MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_JSON_EXCEPTION);
    }

    @Override
    public boolean getMeasurementEnableReportDeletionOnUnrecoverableException() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_DELETE_REPORTS_ON_UNRECOVERABLE_EXCEPTION,
                MEASUREMENT_ENABLE_DELETE_REPORTS_ON_UNRECOVERABLE_EXCEPTION);
    }

    @Override
    public boolean getMeasurementEnableReportingJobsThrowCryptoException() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_CRYPTO_EXCEPTION,
                MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_CRYPTO_EXCEPTION);
    }

    @Override
    public boolean getMeasurementEnableDatastoreManagerThrowDatastoreException() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_DATASTORE_MANAGER_THROW_DATASTORE_EXCEPTION,
                MEASUREMENT_ENABLE_DATASTORE_MANAGER_THROW_DATASTORE_EXCEPTION);
    }

    @Override
    public float getMeasurementThrowUnknownExceptionSamplingRate() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_THROW_UNKNOWN_EXCEPTION_SAMPLING_RATE,
                MEASUREMENT_THROW_UNKNOWN_EXCEPTION_SAMPLING_RATE);
    }

    @Override
    public boolean getMeasurementDeleteUninstalledJobPersisted() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_DELETE_UNINSTALLED_JOB_PERSISTED,
                MEASUREMENT_DELETE_UNINSTALLED_JOB_PERSISTED);
    }

    @Override
    public long getMeasurementDeleteUninstalledJobPeriodMs() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_DELETE_UNINSTALLED_JOB_PERIOD_MS,
                MEASUREMENT_DELETE_UNINSTALLED_JOB_PERIOD_MS);
    }

    @Override
    public boolean getMeasurementDeleteExpiredJobPersisted() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_DELETE_EXPIRED_JOB_PERSISTED,
                MEASUREMENT_DELETE_EXPIRED_JOB_PERSISTED);
    }

    @Override
    public boolean getMeasurementDeleteExpiredJobRequiresDeviceIdle() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_DELETE_EXPIRED_JOB_REQUIRES_DEVICE_IDLE,
                MEASUREMENT_DELETE_EXPIRED_JOB_REQUIRES_DEVICE_IDLE);
    }

    @Override
    public long getMeasurementDeleteExpiredJobPeriodMs() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_DELETE_EXPIRED_JOB_PERIOD_MS,
                MEASUREMENT_DELETE_EXPIRED_JOB_PERIOD_MS);
    }

    @Override
    public boolean getMeasurementEventReportingJobRequiredBatteryNotLow() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_EVENT_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW,
                MEASUREMENT_EVENT_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW);
    }

    @Override
    public int getMeasurementEventReportingJobRequiredNetworkType() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_EVENT_REPORTING_JOB_REQUIRED_NETWORK_TYPE,
                MEASUREMENT_EVENT_REPORTING_JOB_REQUIRED_NETWORK_TYPE);
    }

    @Override
    public boolean getMeasurementEventReportingJobPersisted() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_EVENT_REPORTING_JOB_PERSISTED,
                MEASUREMENT_EVENT_REPORTING_JOB_PERSISTED);
    }

    @Override
    public boolean getMeasurementEnableTriggerDebugSignal() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_ENABLE_TRIGGER_DEBUG_SIGNAL,
                MEASUREMENT_ENABLE_TRIGGER_DEBUG_SIGNAL);
    }

    @Override
    public boolean getMeasurementEnableEventTriggerDebugSignalForCoarseDestination() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_ENABLE_EVENT_TRIGGER_DEBUG_SIGNAL_FOR_COARSE_DESTINATION,
                MEASUREMENT_ENABLE_EVENT_TRIGGER_DEBUG_SIGNAL_FOR_COARSE_DESTINATION);
    }

    @Override
    public float getMeasurementTriggerDebugSignalProbabilityForFakeReports() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_TRIGGER_DEBUG_SIGNAL_PROBABILITY_FOR_FAKE_REPORTS,
                MEASUREMENT_TRIGGER_DEBUG_SIGNAL_PROBABILITY_FOR_FAKE_REPORTS);
    }

    @Override
    public boolean getMeasurementEventFallbackReportingJobRequiredBatteryNotLow() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW,
                MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW);
    }

    @Override
    public int getMeasurementEventFallbackReportingJobRequiredNetworkType() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_REQUIRED_NETWORK_TYPE,
                MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_REQUIRED_NETWORK_TYPE);
    }

    @Override
    public boolean getMeasurementEventFallbackReportingJobPersisted() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERSISTED,
                MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERSISTED);
    }

    @Override
    public int getMeasurementDebugReportingJobRequiredNetworkType() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_DEBUG_REPORTING_JOB_REQUIRED_NETWORK_TYPE,
                MEASUREMENT_DEBUG_REPORTING_JOB_REQUIRED_NETWORK_TYPE);
    }

    @Override
    public int getMeasurementDebugReportingFallbackJobRequiredNetworkType() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_REQUIRED_NETWORK_TYPE,
                MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_REQUIRED_NETWORK_TYPE);
    }

    @Override
    public boolean getMeasurementDebugReportingFallbackJobPersisted() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_PERSISTED,
                MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_PERSISTED);
    }

    @Override
    public int getMeasurementVerboseDebugReportingJobRequiredNetworkType() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_VERBOSE_DEBUG_REPORTING_JOB_REQUIRED_NETWORK_TYPE,
                MEASUREMENT_VERBOSE_DEBUG_REPORTING_JOB_REQUIRED_NETWORK_TYPE);
    }

    @Override
    public boolean getMeasurementVerboseDebugReportingFallbackJobPersisted() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_PERSISTED,
                MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_PERSISTED);
    }

    @Override
    public boolean getMeasurementAttributionJobPersisted() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_ATTRIBUTION_JOB_PERSISTED, MEASUREMENT_ATTRIBUTION_JOB_PERSISTED);
    }

    @Override
    public boolean getMeasurementAttributionFallbackJobPersisted() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_PERSISTED,
                MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_PERSISTED);
    }

    @Override
    public long getMeasurementAttributionJobTriggeringDelayMs() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_ATTRIBUTION_JOB_TRIGGERING_DELAY_MS,
                MEASUREMENT_ATTRIBUTION_JOB_TRIGGERING_DELAY_MS);
    }

    @Override
    public int getMeasurementAsyncRegistrationQueueJobRequiredNetworkType() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_ASYNC_REGISTRATION_QUEUE_JOB_REQUIRED_NETWORK_TYPE,
                MEASUREMENT_ASYNC_REGISTRATION_QUEUE_JOB_REQUIRED_NETWORK_TYPE);
    }

    @Override
    public boolean getMeasurementAsyncRegistrationQueueJobPersisted() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_ASYNC_REGISTRATION_QUEUE_JOB_PERSISTED,
                MEASUREMENT_ASYNC_REGISTRATION_QUEUE_JOB_PERSISTED);
    }

    @Override
    public boolean getMeasurementAsyncRegistrationFallbackJobRequiredBatteryNotLow() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_REQUIRED_BATTERY_NOT_LOW,
                MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_REQUIRED_BATTERY_NOT_LOW);
    }

    @Override
    public int getMeasurementAsyncRegistrationFallbackJobRequiredNetworkType() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_REQUIRED_NETWORK_TYPE,
                MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_REQUIRED_NETWORK_TYPE);
    }

    @Override
    public boolean getMeasurementAsyncRegistrationFallbackJobPersisted() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_PERSISTED,
                MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_PERSISTED);
    }

    @Override
    public boolean getMeasurementAggregateReportingJobRequiredBatteryNotLow() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_AGGREGATE_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW,
                MEASUREMENT_AGGREGATE_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW);
    }

    @Override
    public int getMeasurementAggregateReportingJobRequiredNetworkType() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_AGGREGATE_REPORTING_JOB_REQUIRED_NETWORK_TYPE,
                MEASUREMENT_AGGREGATE_REPORTING_JOB_REQUIRED_NETWORK_TYPE);
    }

    @Override
    public boolean getMeasurementAggregateReportingJobPersisted() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_AGGREGATE_REPORTING_JOB_PERSISTED,
                MEASUREMENT_AGGREGATE_REPORTING_JOB_PERSISTED);
    }

    @Override
    public boolean getMeasurementAggregateFallbackReportingJobRequiredBatteryNotLow() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW,
                MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW);
    }

    @Override
    public int getMeasurementAggregateFallbackReportingJobRequiredNetworkType() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_REQUIRED_NETWORK_TYPE,
                MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_REQUIRED_NETWORK_TYPE);
    }

    @Override
    public boolean getMeasurementAggregateFallbackReportingJobPersisted() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERSISTED,
                MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERSISTED);
    }

    @Override
    public boolean getMeasurementImmediateAggregateReportingJobRequiredBatteryNotLow() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW,
                MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW);
    }

    @Override
    public int getMeasurementImmediateAggregateReportingJobRequiredNetworkType() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_REQUIRED_NETWORK_TYPE,
                MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_REQUIRED_NETWORK_TYPE);
    }

    @Override
    public boolean getMeasurementImmediateAggregateReportingJobPersisted() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_PERSISTED,
                MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_PERSISTED);
    }

    @Override
    public boolean getMeasurementReportingJobRequiredBatteryNotLow() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW,
                MEASUREMENT_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW);
    }

    @Override
    public int getMeasurementReportingJobRequiredNetworkType() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_REPORTING_JOB_REQUIRED_NETWORK_TYPE,
                MEASUREMENT_REPORTING_JOB_REQUIRED_NETWORK_TYPE);
    }

    @Override
    public boolean getMeasurementReportingJobPersisted() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_REPORTING_JOB_PERSISTED, MEASUREMENT_REPORTING_JOB_PERSISTED);
    }

    @Override
    public boolean getAdservicesConsentMigrationLoggingEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ADSERVICES_CONSENT_MIGRATION_LOGGING_ENABLED,
                DEFAULT_ADSERVICES_CONSENT_MIGRATION_LOGGING_ENABLED);
    }

    @Override
    public boolean isU18UxDetentionChannelEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_IS_U18_UX_DETENTION_CHANNEL_ENABLED,
                IS_U18_UX_DETENTION_CHANNEL_ENABLED_DEFAULT);
    }

    /** Returns whether Measurement app package name logging is enabled. */
    @Override
    public boolean getMeasurementEnableAppPackageNameLogging() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_APP_PACKAGE_NAME_LOGGING,
                MEASUREMENT_ENABLE_APP_PACKAGE_NAME_LOGGING);
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementDebugReportingFallbackJobKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants
                                        .KEY_MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants
                                        .KEY_MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH,
                                MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementVerboseDebugReportingFallbackJobKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants
                                        .KEY_MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants
                                        .KEY_MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH,
                                MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH));
    }

    @Override
    public long getMeasurementDebugReportingFallbackJobPeriodMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_PERIOD_MS,
                MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_PERIOD_MS);
    }

    @Override
    public long getMeasurementVerboseDebugReportingFallbackJobPeriodMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_PERIOD_MS,
                MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_PERIOD_MS);
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementJobDebugReportingKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_MEASUREMENT_JOB_DEBUG_REPORTING_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants.KEY_MEASUREMENT_JOB_DEBUG_REPORTING_KILL_SWITCH,
                                MEASUREMENT_JOB_DEBUG_REPORTING_KILL_SWITCH));
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementJobVerboseDebugReportingKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants
                                        .KEY_MEASUREMENT_JOB_VERBOSE_DEBUG_REPORTING_KILL_SWITCH),
                        getDeviceConfigFlag(
                                FlagsConstants
                                        .KEY_MEASUREMENT_JOB_VERBOSE_DEBUG_REPORTING_KILL_SWITCH,
                                MEASUREMENT_JOB_VERBOSE_DEBUG_REPORTING_KILL_SWITCH));
    }

    @Override
    public float getMeasurementPrivacyEpsilon() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_EVENT_API_DEFAULT_EPSILON,
                DEFAULT_MEASUREMENT_PRIVACY_EPSILON);
    }

    @Override
    public boolean getMeasurementEnableEventLevelEpsilonInSource() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_EVENT_LEVEL_EPSILON_IN_SOURCE,
                MEASUREMENT_ENABLE_EVENT_LEVEL_EPSILON_IN_SOURCE);
    }

    @Override
    public boolean getMeasurementEnableAggregateValueFilters() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_AGGREGATE_VALUE_FILTERS,
                MEASUREMENT_ENABLE_AGGREGATE_VALUE_FILTERS);
    }

    @Override
    public String getMeasurementAppPackageNameLoggingAllowlist() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_APP_PACKAGE_NAME_LOGGING_ALLOWLIST, "");
    }

    @Override
    public boolean isU18SupervisedAccountEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_IS_U18_SUPERVISED_ACCOUNT_ENABLED,
                IS_U18_SUPERVISED_ACCOUNT_ENABLED_DEFAULT);
    }

    @Override
    public long getAdIdFetcherTimeoutMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_AD_ID_FETCHER_TIMEOUT_MS, DEFAULT_AD_ID_FETCHER_TIMEOUT_MS);
    }

    @Override
    public float getMeasurementNullAggReportRateInclSourceRegistrationTime() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_NULL_AGG_REPORT_RATE_INCL_SOURCE_REGISTRATION_TIME,
                MEASUREMENT_NULL_AGG_REPORT_RATE_INCL_SOURCE_REGISTRATION_TIME);
    }

    @Override
    public float getMeasurementNullAggReportRateExclSourceRegistrationTime() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_NULL_AGG_REPORT_RATE_EXCL_SOURCE_REGISTRATION_TIME,
                MEASUREMENT_NULL_AGG_REPORT_RATE_EXCL_SOURCE_REGISTRATION_TIME);
    }

    @Override
    public int getMeasurementMaxLengthOfTriggerContextId() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_LENGTH_OF_TRIGGER_CONTEXT_ID,
                MEASUREMENT_MAX_LENGTH_OF_TRIGGER_CONTEXT_ID);
    }

    @Override
    public boolean getMeasurementEnableSessionStableKillSwitches() {
        return getDeviceConfigFlag(
                KEY_MEASUREMENT_ENABLE_SESSION_STABLE_KILL_SWITCHES,
                MEASUREMENT_ENABLE_SESSION_STABLE_KILL_SWITCHES);
    }

    @Override
    public boolean getMeasurementReportingJobServiceEnabled() {
        return getMeasurementEnabled()
                && getDeviceConfigFlag(
                        KEY_MEASUREMENT_REPORTING_JOB_SERVICE_ENABLED,
                        MEASUREMENT_REPORTING_JOB_ENABLED);
    }

    @Override
    public boolean getMeasurementEnableAggregateDebugReporting() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_AGGREGATE_DEBUG_REPORTING,
                MEASUREMENT_ENABLE_AGGREGATE_DEBUG_REPORTING);
    }

    @Override
    public int getMeasurementAdrBudgetOriginXPublisherXWindow() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ADR_BUDGET_PER_ORIGIN_PUBLISHER_WINDOW,
                MEASUREMENT_ADR_BUDGET_PER_ORIGIN_PUBLISHER_WINDOW);
    }

    @Override
    public int getMeasurementAdrBudgetPublisherXWindow() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ADR_BUDGET_PER_PUBLISHER_WINDOW,
                MEASUREMENT_ADR_BUDGET_PER_PUBLISHER_WINDOW);
    }

    @Override
    public long getMeasurementAdrBudgetWindowLengthMillis() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ADR_BUDGET_WINDOW_LENGTH_MS,
                MEASUREMENT_ADR_BUDGET_WINDOW_LENGTH_MILLIS);
    }

    @Override
    public int getMeasurementMaxAdrCountPerSource() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_MAX_ADR_COUNT_PER_SOURCE,
                MEASUREMENT_MAX_ADR_COUNT_PER_SOURCE);
    }

    @Override
    public boolean getMeasurementEnableBothSideDebugKeysInReports() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_ENABLE_BOTH_SIDE_DEBUG_KEYS_IN_REPORTS,
                MEASUREMENT_ENABLE_BOTH_SIDE_DEBUG_KEYS_IN_REPORTS);
    }

    @Override
    public long getMeasurementReportingJobServiceBatchWindowMillis() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS,
                MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS);
    }

    @Override
    public long getMeasurementReportingJobServiceMinExecutionWindowMillis() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MEASUREMENT_REPORTING_JOB_SERVICE_MIN_EXECUTION_WINDOW_MILLIS,
                MEASUREMENT_REPORTING_JOB_SERVICE_MIN_EXECUTION_WINDOW_MILLIS);
    }

    @Override
    public boolean getEnableAdExtDataServiceApis() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ENABLE_ADEXT_DATA_SERVICE_APIS,
                DEFAULT_ENABLE_ADEXT_DATA_SERVICE_APIS);
    }

    @Override
    public int getBackgroundJobSamplingLoggingRate() {
        int loggingRatio =
                getDeviceConfigFlag(
                        FlagsConstants.KEY_BACKGROUND_JOB_SAMPLING_LOGGING_RATE,
                        DEFAULT_BACKGROUND_JOB_SAMPLING_LOGGING_RATE);

        // TODO(b/323187832): Calling JobServiceConstants.MAX_PERCENTAGE meets dependency error.
        if (loggingRatio < 0 || loggingRatio > MAX_PERCENTAGE) {
            throw new IllegalArgumentException(
                    "BackgroundJobSamplingLoggingRatio should be in the range of [0, 100]");
        }

        return loggingRatio;
    }

    @Override
    public int getAppSearchWriteTimeout() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_APPSEARCH_WRITE_TIMEOUT_MS, DEFAULT_APPSEARCH_WRITE_TIMEOUT_MS);
    }

    @Override
    public int getAppSearchReadTimeout() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_APPSEARCH_READ_TIMEOUT_MS, DEFAULT_APPSEARCH_READ_TIMEOUT_MS);
    }

    @Override
    public boolean isGetAdServicesCommonStatesApiEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_IS_GET_ADSERVICES_COMMON_STATES_API_ENABLED,
                DEFAULT_IS_GET_ADSERVICES_COMMON_STATES_API_ENABLED);
    }

    @Override
    public boolean getFledgeKAnonSignJoinFeatureEnabled() {
        return getFledgeAuctionServerEnabled()
                && getDeviceConfigFlag(

                        /*flagName */ FlagsConstants.KEY_FLEDGE_ENABLE_KANON_SIGN_JOIN_FEATURE,
                        /*defaultValue */ FLEDGE_DEFAULT_KANON_SIGN_JOIN_FEATURE_ENABLED);
    }

    @Override
    public boolean getFledgeKAnonSignJoinFeatureOnDeviceAuctionEnabled() {
        return getFledgeKAnonSignJoinFeatureEnabled()
                && getDeviceConfigFlag(
                        FlagsConstants.KEY_FLEDGE_ENABLE_KANON_ON_DEVICE_AUCTION_FEATURE,
                        FLEDGE_DEFAULT_KANON_FEATURE_ON_DEVICE_AUCTION_ENABLED);
    }

    @Override
    public boolean getFledgeKAnonSignJoinFeatureAuctionServerEnabled() {
        return getFledgeKAnonSignJoinFeatureEnabled()
                && getDeviceConfigFlag(
                        FlagsConstants.KEY_FLEDGE_ENABLE_KANON_AUCTION_SERVER_FEATURE,
                        FLEDGE_DEFAULT_KANON_FEATURE_AUCTION_SERVER_ENABLED);
    }

    @Override
    public String getFledgeKAnonFetchServerParamsUrl() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_KANON_FETCH_PARAMETERS_URL,
                FLEDGE_DEFAULT_KANON_FETCH_SERVER_PARAMS_URL);
    }

    @Override
    public String getFledgeKAnonGetChallengeUrl() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ANON_GET_CHALLENGE_URl, FLEDGE_DEFAULT_GET_CHALLENGE_URL);
    }

    @Override
    public String getFledgeKAnonRegisterClientParametersUrl() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_KANON_REGISTER_CLIENT_PARAMETERS_URL,
                FLEDGE_DEFAULT_KANON_REGISTER_CLIENT_PARAMETERS_URL);
    }

    @Override
    public String getFledgeKAnonGetTokensUrl() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_KANON_GET_TOKENS_URL,
                FLEDGE_DEFAULT_KANON_GET_TOKENS_URL);
    }

    @Override
    public String getFledgeKAnonJoinUrl() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_KANON_JOIN_URL, FLEDGE_DEFAULT_KANON_JOIN_URL);
    }

    @Override
    public int getFledgeKAnonSignBatchSize() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_KANON_SIGN_BATCH_SIZE,
                FLEDGE_DEFAULT_KANON_SIGN_BATCH_SIZE);
    }

    @Override
    public int getFledgeKAnonPercentageImmediateSignJoinCalls() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_KANON_PERCENTAGE_IMMEDIATE_SIGN_JOIN_CALLS,
                FLEDGE_DEFAULT_KANON_PERCENTAGE_IMMEDIATE_SIGN_JOIN_CALLS);
    }

    @Override
    public long getFledgeKAnonMessageTtlSeconds() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_KANON_MESSAGE_TTL_SECONDS,
                FLEDGE_DEFAULT_KANON_MESSAGE_TTL_SECONDS);
    }

    @Override
    public long getFledgeKAnonBackgroundProcessTimePeriodInMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_KANON_BACKGROUND_TIME_PERIOD_IN_MS,
                FLEDGE_DEFAULT_KANON_BACKGROUND_JOB_TIME_PERIOD_MS);
    }

    @Override
    public int getFledgeKAnonMessagesPerBackgroundProcess() {
        return getDeviceConfigFlag(
                KEY_FLEDGE_KANON_NUMBER_OF_MESSAGES_PER_BACKGROUND_PROCESS,
                FLEDGE_DEFAULT_KANON_NUMBER_OF_MESSAGES_PER_BACKGROUND_PROCESS);
    }

    @Override
    public String getAdServicesCommonStatesAllowList() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_GET_ADSERVICES_COMMON_STATES_ALLOW_LIST,
                GET_ADSERVICES_COMMON_STATES_ALLOW_LIST);
    }

    @Override
    public boolean getFledgeKAnonBackgroundProcessEnabled() {
        return getFledgeKAnonSignJoinFeatureEnabled()
                && getDeviceConfigFlag(
                        KEY_FLEDGE_KANON_BACKGROUND_PROCESS_ENABLED,
                        FLEDGE_DEFAULT_KANON_BACKGROUND_PROCESS_ENABLED);
    }

    @Override
    public boolean getFledgeKAnonLoggingEnabled() {
        return getFledgeKAnonSignJoinFeatureEnabled()
                && getDeviceConfigFlag(
                        KEY_FLEDGE_KANON_SIGN_JOIN_LOGGING_ENABLED,
                        FLEDGE_DEFAULT_KANON_SIGN_JOIN_LOGGING_ENABLED);
    }

    @Override
    public boolean getFledgeKAnonKeyAttestationEnabled() {
        return getFledgeKAnonSignJoinFeatureEnabled()
                && getDeviceConfigFlag(
                        KEY_FLEDGE_KANON_KEY_ATTESTATION_ENABLED,
                        FLEDGE_DEFAULT_KANON_KEY_ATTESTATION_ENABLED);
    }

    @Override
    public String getFledgeKAnonSetTypeToSignJoin() {
        return getDeviceConfigFlag(
                KEY_FLEDGE_KANON_SET_TYPE_TO_SIGN_JOIN, FLEDGE_DEFAULT_KANON_SET_TYPE_TO_SIGN_JOIN);
    }

    @Override
    public String getFledgeKAnonUrlAuthorityToJoin() {
        return getDeviceConfigFlag(
                KEY_FLEDGE_KANON_JOIN_URL_AUTHORIY, FLEDGE_DEFAULT_KANON_AUTHORIY_URL_JOIN);
    }

    @Override
    public int getFledgeKanonHttpClientTimeoutInMs() {
        return getDeviceConfigFlag(
                KEY_FLEDGE_KANON_HTTP_CLIENT_TIMEOUT,
                FLEDGE_DEFAULT_KANON_HTTP_CLIENT_TIMEOUT_IN_MS);
    }

    @Override
    public boolean getFledgeKAnonBackgroundJobRequiresBatteryNotLow() {
        return getDeviceConfigFlag(
                KEY_FLEDGE_KANON_BACKGROUND_JOB_REQUIRES_BATTERY_NOT_LOW,
                FLEDGE_DEFAULT_KANON_BACKGROUND_JOB_REQUIRES_BATTERY_NOT_LOW);
    }

    @Override
    public boolean getFledgeKAnonBackgroundJobRequiresDeviceIdle() {
        return getDeviceConfigFlag(
                KEY_FLEDGE_KANON_BACKGROUND_JOB_REQUIRES_DEVICE_IDLE,
                FLEDGE_DEFAULT_KANON_BACKGROUND_JOB_REQUIRES_DEVICE_IDLE);
    }

    @Override
    public int getFledgeKanonBackgroundJobConnectionType() {
        return getDeviceConfigFlag(
                KEY_FLEDGE_KANON_BACKGROUND_JOB_TYPE_OF_CONNECTION,
                FLEDGE_DEFAULT_KANON_BACKGROUND_JOB_CONNECTION_TYPE);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method always return {@code true} because the underlying flag is fully launched on
     * {@code Adservices} but the method cannot be removed (as it's defined on {@code
     * ModuleSharedFlags}).
     */
    @Override
    public boolean getBackgroundJobsLoggingEnabled() {
        return true;
    }

    @Override
    public boolean getAdServicesRetryStrategyEnabled() {
        return getDeviceConfigFlag(
                KEY_AD_SERVICES_RETRY_STRATEGY_ENABLED, DEFAULT_AD_SERVICES_RETRY_STRATEGY_ENABLED);
    }

    @Override
    public int getAdServicesJsScriptEngineMaxRetryAttempts() {
        return getDeviceConfigFlag(
                KEY_AD_SERVICES_JS_SCRIPT_ENGINE_MAX_RETRY_ATTEMPTS,
                DEFAULT_AD_SERVICES_JS_SCRIPT_ENGINE_MAX_RETRY_ATTEMPTS);
    }

    @Override
    public boolean getEnableConsentManagerV2() {
        return getDeviceConfigFlag(
                KEY_ENABLE_CONSENT_MANAGER_V2, DEFAULT_ENABLE_CONSENT_MANAGER_V2);
    }

    @Override
    public boolean getPasExtendedMetricsEnabled() {
        return getDeviceConfigFlag(KEY_PAS_EXTENDED_METRICS_ENABLED, PAS_EXTENDED_METRICS_ENABLED);
    }

    @Override
    public boolean getPasProductMetricsV1Enabled() {
        return getDeviceConfigFlag(
                KEY_PAS_PRODUCT_METRICS_V1_ENABLED, PAS_PRODUCT_METRICS_V1_ENABLED);
    }

    @Override
    public boolean getSpeOnPilotJobsEnabled() {
        return getDeviceConfigFlag(
                KEY_SPE_ON_PILOT_JOBS_ENABLED, DEFAULT_SPE_ON_PILOT_JOBS_ENABLED);
    }

    @Override
    public boolean getEnrollmentApiBasedSchemaEnabled() {
        return getDeviceConfigFlag(
                KEY_ENROLLMENT_API_BASED_SCHEMA_ENABLED, ENROLLMENT_API_BASED_SCHEMA_ENABLED);
    }

    @Override
    public boolean getEnableEnrollmentConfigV3Db() {
        return getDeviceConfigFlag(
                KEY_CONFIG_DELIVERY__ENABLE_ENROLLMENT_CONFIG_V3_DB,
                DEFAULT_ENABLE_ENROLLMENT_CONFIG_V3_DB);
    }

    @Override
    public boolean getUseConfigsManagerToQueryEnrollment() {
        return getDeviceConfigFlag(
                KEY_CONFIG_DELIVERY__USE_CONFIGS_MANAGER_TO_QUERY_ENROLLMENT,
                DEFAULT_USE_CONFIGS_MANAGER_TO_QUERY_ENROLLMENT);
    }

    @Override
    public boolean getSharedDatabaseSchemaVersion4Enabled() {
        return getDeviceConfigFlag(
                KEY_SHARED_DATABASE_SCHEMA_VERSION_4_ENABLED,
                SHARED_DATABASE_SCHEMA_VERSION_4_ENABLED);
    }

    @Override
    public boolean getJobSchedulingLoggingEnabled() {
        return getDeviceConfigFlag(
                KEY_JOB_SCHEDULING_LOGGING_ENABLED, DEFAULT_JOB_SCHEDULING_LOGGING_ENABLED);
    }

    @Override
    public int getJobSchedulingLoggingSamplingRate() {
        return getDeviceConfigFlag(
                KEY_JOB_SCHEDULING_LOGGING_SAMPLING_RATE,
                DEFAULT_JOB_SCHEDULING_LOGGING_SAMPLING_RATE);
    }

    @Override
    public boolean getEnableTabletRegionFix() {
        return getDeviceConfigFlag(KEY_ENABLE_TABLET_REGION_FIX, DEFAULT_ENABLE_TABLET_REGION_FIX);
    }

    @Override
    public String getEncodedErrorCodeListPerSampleInterval() {
        return getDeviceConfigFlag(
                KEY_ENCODED_ERROR_CODE_LIST_PER_SAMPLE_INTERVAL,
                ENCODED_ERROR_CODE_LIST_PER_SAMPLE_INTERVAL);
    }

    @Override
    public boolean getCustomErrorCodeSamplingEnabled() {
        return getDeviceConfigFlag(
                KEY_CUSTOM_ERROR_CODE_SAMPLING_ENABLED, DEFAULT_CUSTOM_ERROR_CODE_SAMPLING_ENABLED);
    }

    @Override
    public int getPasScriptDownloadReadTimeoutMs() {
        return getDeviceConfigFlag(
                KEY_PAS_SCRIPT_DOWNLOAD_READ_TIMEOUT_MS,
                DEFAULT_PAS_SCRIPT_DOWNLOAD_READ_TIMEOUT_MS);
    }

    @Override
    public int getPasScriptDownloadConnectionTimeoutMs() {
        return getDeviceConfigFlag(
                KEY_PAS_SCRIPT_DOWNLOAD_CONNECTION_TIMEOUT_MS,
                DEFAULT_PAS_SCRIPT_DOWNLOAD_CONNECTION_TIMEOUT_MS);
    }

    @Override
    public int getPasSignalsDownloadReadTimeoutMs() {
        return getDeviceConfigFlag(
                KEY_PAS_SIGNALS_DOWNLOAD_READ_TIMEOUT_MS,
                DEFAULT_PAS_SIGNALS_DOWNLOAD_READ_TIMEOUT_MS);
    }

    @Override
    public int getPasSignalsDownloadConnectionTimeoutMs() {
        return getDeviceConfigFlag(
                KEY_PAS_SIGNALS_DOWNLOAD_CONNECTION_TIMEOUT_MS,
                DEFAULT_PAS_SIGNALS_DOWNLOAD_CONNECTION_TIMEOUT_MS);
    }

    @Override
    public int getPasScriptExecutionTimeoutMs() {
        return getDeviceConfigFlag(
                KEY_PAS_SCRIPT_EXECUTION_TIMEOUT_MS, DEFAULT_PAS_SCRIPT_EXECUTION_TIMEOUT_MS);
    }

    @Override
    public boolean getSpeOnPilotJobsBatch2Enabled() {
        return getDeviceConfigFlag(
                KEY_SPE_ON_PILOT_JOBS_BATCH_2_ENABLED, DEFAULT_SPE_ON_PILOT_JOBS_BATCH_2_ENABLED);
    }

    @Override
    public boolean getSpeOnEpochJobEnabled() {
        return getDeviceConfigFlag(KEY_SPE_ON_EPOCH_JOB_ENABLED, DEFAULT_SPE_ON_EPOCH_JOB_ENABLED);
    }

    @Override
    public boolean getSpeOnBackgroundFetchJobEnabled() {
        return getDeviceConfigFlag(
                KEY_SPE_ON_BACKGROUND_FETCH_JOB_ENABLED,
                DEFAULT_SPE_ON_BACKGROUND_FETCH_JOB_ENABLED);
    }

    @Override
    public boolean getSpeOnAsyncRegistrationFallbackJobEnabled() {
        return getDeviceConfigFlag(
                KEY_SPE_ON_ASYNC_REGISTRATION_FALLBACK_JOB_ENABLED,
                DEFAULT_SPE_ON_ASYNC_REGISTRATION_FALLBACK_JOB_ENABLED);
    }

    @Override
    public boolean getAdServicesConsentBusinessLogicMigrationEnabled() {
        return getDeviceConfigFlag(
                KEY_ADSERVICES_CONSENT_BUSINESS_LOGIC_MIGRATION_ENABLED,
                DEFAULT_ADSERVICES_CONSENT_BUSINESS_LOGIC_MIGRATION_ENABLED);
    }

    @Override
    public String getMddEnrollmentManifestFileUrl() {
        return getDeviceConfigFlag(
                KEY_MDD_ENROLLMENT_MANIFEST_FILE_URL, MDD_DEFAULT_ENROLLMENT_MANIFEST_FILE_URL);
    }

    @Override
    public boolean getEnrollmentProtoFileEnabled() {
        return getDeviceConfigFlag(
                KEY_ENROLLMENT_PROTO_FILE_ENABLED, DEFAULT_ENROLLMENT_PROTO_FILE_ENABLED);
    }

    @Override
    public boolean getRNotificationDefaultConsentFixEnabled() {
        return getDeviceConfigFlag(
                KEY_R_NOTIFICATION_DEFAULT_CONSENT_FIX_ENABLED,
                DEFAULT_R_NOTIFICATION_DEFAULT_CONSENT_FIX_ENABLED);
    }

    @Override
    public boolean getPasEncodingJobImprovementsEnabled() {
        return getDeviceConfigFlag(
                KEY_PAS_ENCODING_JOB_IMPROVEMENTS_ENABLED, PAS_ENCODING_JOB_IMPROVEMENTS_ENABLED);
    }

    // Do NOT add Flag / @Override methods below - it should only contain helpers

    /**
     * @deprecated - reading a flag from {@link SystemProperties} first is deprecated - this method
     *     should only be used to refactor existing methods in this class, not on new ones.
     */
    @Deprecated
    @SuppressWarnings("AvoidSystemPropertiesUsage") // Helper method.
    private static boolean getFlagFromSystemPropertiesOrDeviceConfig(
            String name, boolean defaultValue) {
        return SystemProperties.getBoolean(
                getSystemPropertyName(name), getDeviceConfigFlag(name, defaultValue));
    }

    @VisibleForTesting
    static String getSystemPropertyName(String key) {
        return AdServicesCommon.SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX + key;
    }

    @Override
    public long getAdIdCacheTtlMs() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_AD_ID_CACHE_TTL_MS, DEFAULT_ADID_CACHE_TTL_MS);
    }

    @Override
    public boolean getEnablePackageDenyService() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_PACKAGE_DENY__ENABLE_PACKAGE_DENY_SERVICE,
                DEFAULT_ENABLE_PACKAGE_DENY_SERVICE);
    }

    @Override
    public boolean getEnablePackageDenyMdd() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_PACKAGE_DENY__ENABLE_PACKAGE_DENY_MDD,
                DEFAULT_ENABLE_PACKAGE_DENY_MDD);
    }

    @Override
    public boolean getEnablePackageDenyJobOnPackageAdd() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_PACKAGE_DENY__ENABLE_PACKAGE_DENY_JOB_ON_PACKAGE_ADD,
                DEFAULT_ENABLE_PACKAGE_DENY_JOB_ON_PACKAGE_ADD);
    }

    @Override
    public boolean getEnablePackageDenyBgJob() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_PACKAGE_DENY__ENABLE_PACKAGE_DENY_BG_JOB,
                DEFAULT_ENABLE_PACKAGE_DENY_BG_JOB);
    }

    @Override
    public boolean getPackageDenyEnableInstalledPackageFilter() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_PACKAGE_DENY_ENABLE_INSTALLED_PACKAGE_FILTER,
                DEFAULT_PACKAGE_DENY_ENABLE_INSTALLED_PACKAGE_FILTER);
    }

    @Override
    public long getPackageDenyBackgroundJobPeriodMillis() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_PACKAGE_DENY_BACKGROUND_JOB_PERIOD_MILLIS,
                DEFAULT_PACKAGE_DENY_BACKGROUND_JOB_PERIOD_MILLIS);
    }

    @Override
    public boolean getEnablePackageDenyJobOnMddDownload() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_PACKAGE_DENY__ENABLE_PACKAGE_DENY_JOB_ON_MDD_DOWNLOAD,
                DEFAULT_ENABLE_PACKAGE_DENY_JOB_ON_MDD_DOWNLOAD);
    }

    @Override
    public String getMddPackageDenyRegistryManifestFileUrl() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_MDD_PACKAGE_DENY_REGISTRY_MANIFEST_FILE_URL,
                DEFAULT_MDD_PACKAGE_DENY_REGISTRY_MANIFEST_FILE_URL);
    }

    @Override
    public boolean getEnableAtomicFileDatastoreBatchUpdateApi() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_ENABLE_ATOMIC_FILE_DATASTORE_BATCH_UPDATE_API,
                DEFAULT_ENABLE_ATOMIC_FILE_DATASTORE_BATCH_UPDATE_API);
    }

    @Override
    public boolean getAdIdMigrationEnabled() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_AD_ID_MIGRATION_ENABLED, DEFAULT_AD_ID_MIGRATION_ENABLED);
    }

    @Override
    public boolean getEnableReportEventForComponentSeller() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_ENABLE_REPORT_EVENT_FOR_COMPONENT_SELLER,
                DEFAULT_ENABLE_REPORT_EVENT_FOR_COMPONENT_SELLER);
    }

    @Override
    public boolean getEnableWinningSellerIdInAdSelectionOutcome() {
        return getDeviceConfigFlag(
                FlagsConstants.KEY_FLEDGE_ENABLE_WINNING_SELLER_ID_IN_AD_SELECTION_OUTCOME,
                DEFAULT_ENABLE_WINNING_SELLER_ID_IN_AD_SELECTION_OUTCOME);
    }

    @Override
    public boolean getEnableProdDebugInAuctionServer() {
        return getDeviceConfigFlag(
                KEY_FLEDGE_ENABLE_PROD_DEBUG_IN_SERVER_AUCTION,
                DEFAULT_PROD_DEBUG_IN_AUCTION_SERVER);
    }

    @Override
    public boolean getEnableRbAtrace() {
        return getDeviceConfigFlag(FlagsConstants.KEY_ENABLE_RB_ATRACE, DEFAULT_ENABLE_RB_ATRACE);
    }
}
