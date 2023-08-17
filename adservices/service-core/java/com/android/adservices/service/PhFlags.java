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

import static java.lang.Float.parseFloat;

import android.annotation.NonNull;
import android.os.SystemProperties;
import android.provider.DeviceConfig;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.LogUtil;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableList;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Flags Implementation that delegates to DeviceConfig. */
// TODO(b/228037065): Add validation logics for Feature flags read from PH.
public final class PhFlags implements Flags {

    private static final PhFlags sSingleton = new PhFlags();

    /** Returns the singleton instance of the PhFlags. */
    @NonNull
    public static PhFlags getInstance() {
        return sSingleton;
    }

    @Override
    public long getAsyncRegistrationJobQueueIntervalMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_ASYNC_REGISTRATION_JOB_QUEUE_INTERVAL_MS,
                /* defaultValue */ ASYNC_REGISTRATION_JOB_QUEUE_INTERVAL_MS);
    }

    @Override
    public long getTopicsEpochJobPeriodMs() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        long topicsEpochJobPeriodMs =
                SystemProperties.getLong(
                        getSystemPropertyName(FlagsConstants.KEY_TOPICS_EPOCH_JOB_PERIOD_MS),
                        /* defaultValue */ DeviceConfig.getLong(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants.KEY_TOPICS_EPOCH_JOB_PERIOD_MS,
                                /* defaultValue */ TOPICS_EPOCH_JOB_PERIOD_MS));
        if (topicsEpochJobPeriodMs <= 0) {
            throw new IllegalArgumentException("topicsEpochJobPeriodMs should > 0");
        }
        return topicsEpochJobPeriodMs;
    }

    @Override
    public long getTopicsEpochJobFlexMs() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        long topicsEpochJobFlexMs =
                SystemProperties.getLong(
                        getSystemPropertyName(FlagsConstants.KEY_TOPICS_EPOCH_JOB_FLEX_MS),
                        /* defaultValue */ DeviceConfig.getLong(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants.KEY_TOPICS_EPOCH_JOB_FLEX_MS,
                                /* defaultValue */ TOPICS_EPOCH_JOB_FLEX_MS));
        if (topicsEpochJobFlexMs <= 0) {
            throw new IllegalArgumentException("topicsEpochJobFlexMs should > 0");
        }
        return topicsEpochJobFlexMs;
    }

    @Override
    public int getTopicsPercentageForRandomTopic() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        int topicsPercentageForRandomTopic =
                SystemProperties.getInt(
                        getSystemPropertyName(
                                FlagsConstants.KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC),
                        /* defaultValue */ DeviceConfig.getInt(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants
                                        .KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC,
                                /* defaultValue */ TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC));
        if (topicsPercentageForRandomTopic < 0
                || topicsPercentageForRandomTopic > FlagsConstants.MAX_PERCENTAGE) {
            throw new IllegalArgumentException(
                    "topicsPercentageForRandomTopic should be between 0 and 100");
        }
        return topicsPercentageForRandomTopic;
    }

    @Override
    public int getTopicsNumberOfTopTopics() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        int topicsNumberOfTopTopics =
                DeviceConfig.getInt(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* flagName */ FlagsConstants.KEY_TOPICS_NUMBER_OF_TOP_TOPICS,
                        /* defaultValue */ TOPICS_NUMBER_OF_TOP_TOPICS);
        if (topicsNumberOfTopTopics < 0) {
            throw new IllegalArgumentException("topicsNumberOfTopTopics should >= 0");
        }

        return topicsNumberOfTopTopics;
    }

    @Override
    public int getTopicsNumberOfRandomTopics() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        int topicsNumberOfTopTopics =
                DeviceConfig.getInt(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* flagName */ FlagsConstants.KEY_TOPICS_NUMBER_OF_RANDOM_TOPICS,
                        /* defaultValue */ TOPICS_NUMBER_OF_RANDOM_TOPICS);
        if (topicsNumberOfTopTopics < 0) {
            throw new IllegalArgumentException("topicsNumberOfTopTopics should >= 0");
        }

        return topicsNumberOfTopTopics;
    }

    @Override
    public int getTopicsNumberOfLookBackEpochs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        int topicsNumberOfLookBackEpochs =
                DeviceConfig.getInt(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* flagName */ FlagsConstants.KEY_TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS,
                        /* defaultValue */ TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS);
        if (topicsNumberOfLookBackEpochs < 1) {
            throw new IllegalArgumentException("topicsNumberOfLookBackEpochs should  >= 1");
        }

        return topicsNumberOfLookBackEpochs;
    }

    @Override
    public float getTopicsPrivacyBudgetForTopicIdDistribution() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        float topicsPrivacyBudgetForTopicIdDistribution =
                DeviceConfig.getFloat(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* flagName */ FlagsConstants
                                .KEY_TOPICS_PRIVACY_BUDGET_FOR_TOPIC_ID_DISTRIBUTION,
                        /* defaultValue */ TOPICS_PRIVACY_BUDGET_FOR_TOPIC_ID_DISTRIBUTION);

        if (topicsPrivacyBudgetForTopicIdDistribution <= 0) {
            throw new IllegalArgumentException(
                    "topicsPrivacyBudgetForTopicIdDistribution should be > 0");
        }

        return topicsPrivacyBudgetForTopicIdDistribution;
    }

    @Override
    public int getClassifierType() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return SystemProperties.getInt(
                getSystemPropertyName(FlagsConstants.KEY_CLASSIFIER_TYPE),
                DeviceConfig.getInt(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* flagName */ FlagsConstants.KEY_CLASSIFIER_TYPE,
                        /* defaultValue */ DEFAULT_CLASSIFIER_TYPE));
    }

    @Override
    public int getClassifierNumberOfTopLabels() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return SystemProperties.getInt(
                getSystemPropertyName(FlagsConstants.KEY_CLASSIFIER_NUMBER_OF_TOP_LABELS),
                DeviceConfig.getInt(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* flagName */ FlagsConstants.KEY_CLASSIFIER_NUMBER_OF_TOP_LABELS,
                        /* defaultValue */ CLASSIFIER_NUMBER_OF_TOP_LABELS));
    }

    @Override
    public float getClassifierThreshold() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getFloat(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_CLASSIFIER_THRESHOLD,
                /* defaultValue */ CLASSIFIER_THRESHOLD);
    }

    @Override
    public int getClassifierDescriptionMaxWords() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_CLASSIFIER_DESCRIPTION_MAX_WORDS,
                /* defaultValue */ CLASSIFIER_DESCRIPTION_MAX_WORDS);
    }

    @Override
    public int getClassifierDescriptionMaxLength() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_CLASSIFIER_DESCRIPTION_MAX_LENGTH,
                /* defaultValue */ CLASSIFIER_DESCRIPTION_MAX_LENGTH);
    }

    @Override
    public boolean getClassifierForceUseBundledFiles() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_CLASSIFIER_FORCE_USE_BUNDLED_FILES,
                /* defaultValue */ CLASSIFIER_FORCE_USE_BUNDLED_FILES);
    }

    @Override
    public boolean getTopicsCobaltLoggingEnabled() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_TOPICS_COBALT_LOGGING_ENABLED,
                /* defaultValue */ TOPICS_COBALT_LOGGING_ENABLED);
    }

    @Override
    public String getCobaltAdservicesApiKeyHex() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getString(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_COBALT_ADSERVICES_API_KEY_HEX,
                /* defaultValue */ COBALT_ADSERVICES_API_KEY_HEX);
    }

    @Override
    public String getAdservicesReleaseStageForCobalt() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getString(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_ADSERVICES_RELEASE_STAGE_FOR_COBALT,
                /* defaultValue */ ADSERVICES_RELEASE_STAGE_FOR_COBALT);
    }

    @Override
    public long getMaintenanceJobPeriodMs() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig) and then
        // hard-coded value.
        long maintenanceJobPeriodMs =
                SystemProperties.getLong(
                        getSystemPropertyName(FlagsConstants.KEY_MAINTENANCE_JOB_PERIOD_MS),
                        /* defaultValue */ DeviceConfig.getLong(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants.KEY_MAINTENANCE_JOB_PERIOD_MS,
                                /* defaultValue */ MAINTENANCE_JOB_PERIOD_MS));
        if (maintenanceJobPeriodMs < 0) {
            throw new IllegalArgumentException("maintenanceJobPeriodMs should  >= 0");
        }
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return maintenanceJobPeriodMs;
    }

    @Override
    public long getMaintenanceJobFlexMs() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig) and then
        // hard-coded value.
        long maintenanceJobFlexMs =
                SystemProperties.getLong(
                        getSystemPropertyName(FlagsConstants.KEY_MAINTENANCE_JOB_FLEX_MS),
                        /* defaultValue */ DeviceConfig.getLong(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants.KEY_MAINTENANCE_JOB_FLEX_MS,
                                /* defaultValue */ MAINTENANCE_JOB_FLEX_MS));

        if (maintenanceJobFlexMs <= 0) {
            throw new IllegalArgumentException("maintenanceJobFlexMs should  > 0");
        }

        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return maintenanceJobFlexMs;
    }

    @Override
    public long getMeasurementEventMainReportingJobPeriodMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS,
                /* defaultValue */ MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS);
    }

    @Override
    public long getMeasurementEventFallbackReportingJobPeriodMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS,
                /* defaultValue */ MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS);
    }

    @Override
    public boolean getMeasurementAggregationCoordinatorOriginEnabled() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_ENABLED,
                /* defaultValue */ MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_ENABLED);
    }

    @Override
    public String getMeasurementAggregationCoordinatorOriginList() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getString(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_LIST,
                /* defaultValue */ MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_LIST);
    }

    @Override
    public String getMeasurementDefaultAggregationCoordinatorOrigin() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getString(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_DEFAULT_AGGREGATION_COORDINATOR_ORIGIN,
                /* defaultValue */ MEASUREMENT_DEFAULT_AGGREGATION_COORDINATOR_ORIGIN);
    }

    @Override
    public String getMeasurementAggregationCoordinatorPath() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getString(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_AGGREGATION_COORDINATOR_PATH,
                /* defaultValue */ MEASUREMENT_AGGREGATION_COORDINATOR_PATH);
    }

    @Override
    public long getMeasurementAggregateMainReportingJobPeriodMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS,
                /* defaultValue */ MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS);
    }

    @Override
    public long getMeasurementAggregateFallbackReportingJobPeriodMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS,
                /* defaultValue */ MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS);
    }

    @Override
    public int getMeasurementNetworkConnectTimeoutMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_NETWORK_CONNECT_TIMEOUT_MS,
                /* defaultValue */ MEASUREMENT_NETWORK_CONNECT_TIMEOUT_MS);
    }

    @Override
    public int getMeasurementNetworkReadTimeoutMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_NETWORK_READ_TIMEOUT_MS,
                /* defaultValue */ MEASUREMENT_NETWORK_READ_TIMEOUT_MS);
    }

    @Override
    public long getMeasurementDbSizeLimit() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_DB_SIZE_LIMIT,
                /* defaultValue */ MEASUREMENT_DB_SIZE_LIMIT);
    }

    @Override
    public String getMeasurementManifestFileUrl() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getString(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_MANIFEST_FILE_URL,
                /* defaultValue */ MEASUREMENT_MANIFEST_FILE_URL);
    }

    @Override
    public long getMeasurementRegistrationInputEventValidWindowMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_REGISTRATION_INPUT_EVENT_VALID_WINDOW_MS,
                /* defaultValue */ MEASUREMENT_REGISTRATION_INPUT_EVENT_VALID_WINDOW_MS);
    }

    @Override
    public boolean getMeasurementIsClickVerificationEnabled() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_IS_CLICK_VERIFICATION_ENABLED,
                /* defaultValue */ MEASUREMENT_IS_CLICK_VERIFICATION_ENABLED);
    }

    @Override
    public boolean getMeasurementIsClickVerifiedByInputEvent() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_IS_CLICK_VERIFIED_BY_INPUT_EVENT,
                /* defaultValue */ MEASUREMENT_IS_CLICK_VERIFIED_BY_INPUT_EVENT);
    }

    @Override
    public boolean getMeasurementEnableXNA() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_ENABLE_XNA,
                /* defaultValue */ MEASUREMENT_ENABLE_XNA);
    }

    @Override
    public boolean getMeasurementEnableSharedSourceDebugKey() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_ENABLE_SHARED_SOURCE_DEBUG_KEY,
                /* defaultValue */ MEASUREMENT_ENABLE_SHARED_SOURCE_DEBUG_KEY);
    }

    @Override
    public boolean getMeasurementEnableSharedFilterDataKeysXNA() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_ENABLE_SHARED_FILTER_DATA_KEYS_XNA,
                /* defaultValue */ MEASUREMENT_ENABLE_SHARED_FILTER_DATA_KEYS_XNA);
    }

    @Override
    public boolean getMeasurementEnableDebugReport() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_ENABLE_DEBUG_REPORT,
                /* defaultValue */ MEASUREMENT_ENABLE_DEBUG_REPORT);
    }

    @Override
    public boolean getMeasurementEnableSourceDebugReport() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_ENABLE_SOURCE_DEBUG_REPORT,
                /* defaultValue */ MEASUREMENT_ENABLE_SOURCE_DEBUG_REPORT);
    }

    @Override
    public boolean getMeasurementEnableTriggerDebugReport() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_ENABLE_TRIGGER_DEBUG_REPORT,
                /* defaultValue */ MEASUREMENT_ENABLE_TRIGGER_DEBUG_REPORT);
    }

    @Override
    public long getMeasurementDataExpiryWindowMs() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_DATA_EXPIRY_WINDOW_MS,
                /* defaultValue */ MEASUREMENT_DATA_EXPIRY_WINDOW_MS);
    }

    @Override
    public int getMeasurementMaxRegistrationRedirects() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_MAX_REGISTRATION_REDIRECTS,
                /* defaultValue */ MEASUREMENT_MAX_REGISTRATION_REDIRECTS);
    }

    @Override
    public int getMeasurementMaxRegistrationsPerJobInvocation() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_MAX_REGISTRATIONS_PER_JOB_INVOCATION,
                /* defaultValue */ MEASUREMENT_MAX_REGISTRATIONS_PER_JOB_INVOCATION);
    }

    @Override
    public int getMeasurementMaxRetriesPerRegistrationRequest() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_MAX_RETRIES_PER_REGISTRATION_REQUEST,
                /* defaultValue */ MEASUREMENT_MAX_RETRIES_PER_REGISTRATION_REQUEST);
    }

    @Override
    public long getMeasurementAsyncRegistrationJobTriggerMinDelayMs() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_ASYNC_REGISTRATION_JOB_TRIGGER_MIN_DELAY_MS,
                /* defaultValue */ DEFAULT_MEASUREMENT_ASYNC_REGISTRATION_JOB_TRIGGER_MIN_DELAY_MS);
    }

    @Override
    public long getMeasurementAsyncRegistrationJobTriggerMaxDelayMs() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_ASYNC_REGISTRATION_JOB_TRIGGER_MAX_DELAY_MS,
                /* defaultValue */ DEFAULT_MEASUREMENT_ASYNC_REGISTRATION_JOB_TRIGGER_MAX_DELAY_MS);
    }

    @Override
    public boolean getMeasurementAttributionFallbackJobKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        final String flagName = FlagsConstants.KEY_MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_KILL_SWITCH;
        final boolean defaultValue = MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_KILL_SWITCH;
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(flagName),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES, flagName, defaultValue));
    }

    @Override
    public long getMeasurementAttributionFallbackJobPeriodMs() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_PERIOD_MS,
                /* defaultValue */ MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_PERIOD_MS);
    }

    @Override
    public int getMeasurementMaxAttributionPerRateLimitWindow() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_MAX_ATTRIBUTION_PER_RATE_LIMIT_WINDOW,
                /* defaultValue */ MEASUREMENT_MAX_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);
    }

    @Override
    public int getMeasurementMaxDistinctEnrollmentsInAttribution() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_MAX_DISTINCT_ENROLLMENTS_IN_ATTRIBUTION,
                /* defaultValue */ MEASUREMENT_MAX_DISTINCT_ENROLLMENTS_IN_ATTRIBUTION);
    }

    @Override
    public int getMeasurementMaxDistinctDestinationsInActiveSource() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_MAX_DISTINCT_DESTINATIONS_IN_ACTIVE_SOURCE,
                /* defaultValue */ MEASUREMENT_MAX_DISTINCT_DESTINATIONS_IN_ACTIVE_SOURCE);
    }

    @Override
    public long getFledgeCustomAudienceMaxCount() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT);
    }

    @Override
    public long getFledgeCustomAudiencePerAppMaxCount() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT);
    }

    @Override
    public long getFledgeCustomAudienceMaxOwnerCount() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT);
    }

    @Override
    public long getFledgeCustomAudienceDefaultExpireInMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN_MS,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN_MS);
    }

    @Override
    public long getFledgeCustomAudienceMaxActivationDelayInMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN_MS,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN_MS);
    }

    @Override
    public long getFledgeCustomAudienceMaxExpireInMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_EXPIRE_IN_MS,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_EXPIRE_IN_MS);
    }

    @Override
    public int getFledgeCustomAudienceMaxNameSizeB() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NAME_SIZE_B,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_NAME_SIZE_B);
    }

    @Override
    public int getFledgeCustomAudienceMaxDailyUpdateUriSizeB() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_DAILY_UPDATE_URI_SIZE_B,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_DAILY_UPDATE_URI_SIZE_B);
    }

    @Override
    public int getFledgeCustomAudienceMaxBiddingLogicUriSizeB() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_BIDDING_LOGIC_URI_SIZE_B,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_BIDDING_LOGIC_URI_SIZE_B);
    }

    @Override
    public int getFledgeCustomAudienceMaxUserBiddingSignalsSizeB() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B);
    }

    @Override
    public int getFledgeCustomAudienceMaxTrustedBiddingDataSizeB() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B);
    }

    @Override
    public int getFledgeCustomAudienceMaxAdsSizeB() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ADS_SIZE_B,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_ADS_SIZE_B);
    }

    @Override
    public int getFledgeCustomAudienceMaxNumAds() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS);
    }

    @Override
    public long getFledgeCustomAudienceActiveTimeWindowInMs() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS);
    }

    @Override
    public int getFledgeFetchCustomAudienceMaxUserBiddingSignalsSizeB() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B,
                /* defaultValue */ FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B);
    }

    @Override
    public int getFledgeFetchCustomAudienceMaxRequestCustomHeaderSizeB() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_REQUEST_CUSTOM_HEADER_SIZE_B,
                /* defaultValue */ FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_REQUEST_CUSTOM_HEADER_SIZE_B);
    }

    @Override
    public int getFledgeFetchCustomAudienceMaxCustomAudienceSizeB() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_CUSTOM_AUDIENCE_SIZE_B,
                /* defaultValue */ FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_CUSTOM_AUDIENCE_SIZE_B);
    }

    @Override
    public boolean getFledgeBackgroundFetchEnabled() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_ENABLED,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_ENABLED);
    }

    @Override
    public long getFledgeBackgroundFetchJobPeriodMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS);
    }

    @Override
    public long getFledgeBackgroundFetchJobFlexMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS);
    }

    @Override
    public long getFledgeBackgroundFetchJobMaxRuntimeMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_JOB_MAX_RUNTIME_MS,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_JOB_MAX_RUNTIME_MS);
    }

    @Override
    public long getFledgeBackgroundFetchMaxNumUpdated() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_MAX_NUM_UPDATED,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_MAX_NUM_UPDATED);
    }

    @Override
    public int getFledgeBackgroundFetchThreadPoolSize() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_THREAD_POOL_SIZE,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_THREAD_POOL_SIZE);
    }

    @Override
    public long getFledgeBackgroundFetchEligibleUpdateBaseIntervalS() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_FLEDGE_BACKGROUND_FETCH_ELIGIBLE_UPDATE_BASE_INTERVAL_S,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_ELIGIBLE_UPDATE_BASE_INTERVAL_S);
    }

    @Override
    public int getFledgeBackgroundFetchNetworkConnectTimeoutMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS);
    }

    @Override
    public int getFledgeBackgroundFetchNetworkReadTimeoutMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS);
    }

    @Override
    public int getFledgeBackgroundFetchMaxResponseSizeB() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_MAX_RESPONSE_SIZE_B,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_MAX_RESPONSE_SIZE_B);
    }

    @Override
    public int getAdSelectionMaxConcurrentBiddingCount() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_AD_SELECTION_MAX_CONCURRENT_BIDDING_COUNT,
                /* defaultValue */ FLEDGE_AD_SELECTION_MAX_CONCURRENT_BIDDING_COUNT);
    }

    @Override
    public long getAdSelectionBiddingTimeoutPerCaMs() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS,
                /* defaultValue */ FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS);
    }

    @Override
    public long getAdSelectionBiddingTimeoutPerBuyerMs() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_BUYER_MS,
                /* defaultValue */ FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_BUYER_MS);
    }

    @Override
    public long getAdSelectionScoringTimeoutMs() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS,
                /* defaultValue */ FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS);
    }

    @Override
    public long getAdSelectionSelectingOutcomeTimeoutMs() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS,
                /* defaultValue */ FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS);
    }

    @Override
    public long getAdSelectionOverallTimeoutMs() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS,
                /* defaultValue */ FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS);
    }

    @Override
    public long getAdSelectionFromOutcomesOverallTimeoutMs() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS,
                /* defaultValue */ FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS);
    }

    @Override
    public long getAdSelectionOffDeviceOverallTimeoutMs() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_AD_SELECTION_OFF_DEVICE_OVERALL_TIMEOUT_MS,
                /* defaultValue */ FLEDGE_AD_SELECTION_OFF_DEVICE_OVERALL_TIMEOUT_MS);
    }

    @Override
    public boolean getFledgeAdSelectionFilteringEnabled() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_AD_SELECTION_FILTERING_ENABLED,
                /* defaultValue */ FLEDGE_AD_SELECTION_FILTERING_ENABLED);
    }

    @Override
    @SuppressWarnings("InlinedApi")
    public boolean getFledgeAdSelectionContextualAdsEnabled() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                // The key deliberately kept same as Filtering as the two features are coupled
                /* flagName */ FlagsConstants.KEY_FLEDGE_AD_SELECTION_FILTERING_ENABLED,
                /* defaultValue */ FLEDGE_AD_SELECTION_CONTEXTUAL_ADS_ENABLED);
    }

    @Override
    @SuppressWarnings("InlinedApi")
    public boolean getFledgeFetchCustomAudienceEnabled() {
        // The priority of applying the flag values: PH (DeviceConfig), then hard-coded value.
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED,
                /* defaultValue */ FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED);
    }

    @Override
    public long getFledgeAdSelectionBiddingLogicJsVersion() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_AD_SELECTION_BIDDING_LOGIC_JS_VERSION,
                /* defaultValue */ FLEDGE_AD_SELECTION_BIDDING_LOGIC_JS_VERSION);
    }

    @Override
    public long getReportImpressionOverallTimeoutMs() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS,
                /* defaultValue */ FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS);
    }

    @Override
    public long getFledgeReportImpressionMaxRegisteredAdBeaconsTotalCount() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_FLEDGE_REPORT_IMPRESSION_MAX_REGISTERED_AD_BEACONS_TOTAL_COUNT,
                /* defaultValue */ FLEDGE_REPORT_IMPRESSION_MAX_REGISTERED_AD_BEACONS_TOTAL_COUNT);
    }

    @Override
    public long getFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */
                FlagsConstants
                        .KEY_FLEDGE_REPORT_IMPRESSION_MAX_REGISTERED_AD_BEACONS_PER_AD_TECH_COUNT,
                /* defaultValue */
                FLEDGE_REPORT_IMPRESSION_MAX_REGISTERED_AD_BEACONS_PER_AD_TECH_COUNT);
    }

    @Override
    public long getFledgeReportImpressionRegisteredAdBeaconsMaxInteractionKeySizeB() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */
                FlagsConstants
                        .KEY_FLEDGE_REPORT_IMPRESSION_REGISTERED_AD_BEACONS_MAX_INTERACTION_KEY_SIZE_B,
                /* defaultValue */
                FLEDGE_REPORT_IMPRESSION_REGISTERED_AD_BEACONS_MAX_INTERACTION_KEY_SIZE_B);
    }

    @Override
    public long getFledgeReportImpressionMaxInteractionReportingUriSizeB() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */
                FlagsConstants.KEY_FLEDGE_REPORT_IMPRESSION_MAX_INTERACTION_REPORTING_URI_SIZE_B,
                /* defaultValue */
                FLEDGE_REPORT_IMPRESSION_MAX_INTERACTION_REPORTING_URI_SIZE_B);
    }

    @Override
    public boolean getFledgeHttpCachingEnabled() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_HTTP_CACHE_ENABLE,
                /* defaultValue */ FLEDGE_HTTP_CACHE_ENABLE);
    }

    @Override
    public boolean getFledgeHttpJsCachingEnabled() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_HTTP_CACHE_ENABLE_JS_CACHING,
                /* defaultValue */ FLEDGE_HTTP_CACHE_ENABLE_JS_CACHING);
    }

    @Override
    public long getFledgeHttpCacheMaxEntries() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_HTTP_CACHE_MAX_ENTRIES,
                /* defaultValue */ FLEDGE_HTTP_CACHE_MAX_ENTRIES);
    }

    @Override
    public long getFledgeHttpCacheMaxAgeSeconds() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_HTTP_CACHE_DEFAULT_MAX_AGE_SECONDS,
                /* defaultValue */ FLEDGE_HTTP_CACHE_DEFAULT_MAX_AGE_SECONDS);
    }

    @Override
    public int getFledgeAdCounterHistogramAbsoluteMaxTotalEventCount() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_FLEDGE_AD_COUNTER_HISTOGRAM_ABSOLUTE_MAX_TOTAL_EVENT_COUNT,
                /* defaultValue */ FLEDGE_AD_COUNTER_HISTOGRAM_ABSOLUTE_MAX_TOTAL_EVENT_COUNT);
    }

    @Override
    public int getFledgeAdCounterHistogramLowerMaxTotalEventCount() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_FLEDGE_AD_COUNTER_HISTOGRAM_LOWER_MAX_TOTAL_EVENT_COUNT,
                /* defaultValue */ FLEDGE_AD_COUNTER_HISTOGRAM_LOWER_MAX_TOTAL_EVENT_COUNT);
    }

    @Override
    public int getFledgeAdCounterHistogramAbsoluteMaxPerBuyerEventCount() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_FLEDGE_AD_COUNTER_HISTOGRAM_ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT,
                /* defaultValue */ FLEDGE_AD_COUNTER_HISTOGRAM_ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT);
    }

    @Override
    public int getFledgeAdCounterHistogramLowerMaxPerBuyerEventCount() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_FLEDGE_AD_COUNTER_HISTOGRAM_LOWER_MAX_PER_BUYER_EVENT_COUNT,
                /* defaultValue */ FLEDGE_AD_COUNTER_HISTOGRAM_LOWER_MAX_PER_BUYER_EVENT_COUNT);
    }

    // MDD related flags.
    @Override
    public int getDownloaderConnectionTimeoutMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_DOWNLOADER_CONNECTION_TIMEOUT_MS,
                /* defaultValue */ DOWNLOADER_CONNECTION_TIMEOUT_MS);
    }

    @Override
    public int getDownloaderReadTimeoutMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_DOWNLOADER_READ_TIMEOUT_MS,
                /* defaultValue */ DOWNLOADER_READ_TIMEOUT_MS);
    }

    @Override
    public int getDownloaderMaxDownloadThreads() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_DOWNLOADER_MAX_DOWNLOAD_THREADS,
                /* defaultValue */ DOWNLOADER_MAX_DOWNLOAD_THREADS);
    }

    @Override
    public String getMddTopicsClassifierManifestFileUrl() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getString(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL,
                /* defaultValue */ MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL);
    }

    // Group of All Killswitches
    @Override
    public boolean getGlobalKillSwitch() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return SystemProperties.getBoolean(
                        getSystemPropertyName(FlagsConstants.KEY_GLOBAL_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants.KEY_GLOBAL_KILL_SWITCH,
                                /* defaultValue */ GLOBAL_KILL_SWITCH))
                || /* S Minus Kill Switch */ !(SdkLevel.isAtLeastT() || getEnableBackCompat());
    }

    // MEASUREMENT Killswitches
    @Override
    public boolean getMeasurementKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(FlagsConstants.KEY_MEASUREMENT_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants.KEY_MEASUREMENT_KILL_SWITCH,
                                /* defaultValue */ MEASUREMENT_KILL_SWITCH));
    }

    @Override
    public boolean getMeasurementApiDeleteRegistrationsKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        final boolean defaultValue = MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH;
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants
                                        .KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants
                                        .KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH,
                                defaultValue));
    }

    @Override
    public boolean getMeasurementApiStatusKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_MEASUREMENT_API_STATUS_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants
                                        .KEY_MEASUREMENT_API_STATUS_KILL_SWITCH,
                                /* defaultValue */ MEASUREMENT_API_STATUS_KILL_SWITCH));
    }

    @Override
    public boolean getMeasurementApiRegisterSourceKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants
                                        .KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH,
                                /* defaultValue */ MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH));
    }

    @Override
    public boolean getMeasurementApiRegisterTriggerKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants
                                        .KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH,
                                /* defaultValue */ MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH));
    }

    @Override
    public boolean getMeasurementApiRegisterWebSourceKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        final boolean defaultValue = MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH;
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants
                                        .KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH,
                                defaultValue));
    }

    @Override
    public boolean getMeasurementApiRegisterWebTriggerKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        final boolean defaultValue = MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH;
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants
                                        .KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants
                                        .KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH,
                                defaultValue));
    }

    @Override
    public boolean getMeasurementJobAggregateFallbackReportingKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        final String flagName =
                FlagsConstants.KEY_MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH;
        final boolean defaultValue = MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH;
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(flagName),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES, flagName, defaultValue));
    }

    @Override
    public boolean getMeasurementJobAggregateReportingKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        final boolean defaultValue = MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH;
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants
                                        .KEY_MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH,
                                defaultValue));
    }

    @Override
    public boolean getMeasurementJobAttributionKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants
                                        .KEY_MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH,
                                /* defaultValue */ MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH));
    }

    @Override
    public boolean getMeasurementJobDeleteExpiredKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants
                                        .KEY_MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH,
                                /* defaultValue */ MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH));
    }

    @Override
    public boolean getMeasurementJobDeleteUninstalledKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_MEASUREMENT_JOB_DELETE_UNINSTALLED_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants
                                        .KEY_MEASUREMENT_JOB_DELETE_UNINSTALLED_KILL_SWITCH,
                                /* defaultValue */ MEASUREMENT_JOB_DELETE_UNINSTALLED_KILL_SWITCH));
    }

    @Override
    public boolean getMeasurementJobEventFallbackReportingKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        final String flagName =
                FlagsConstants.KEY_MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH;
        final boolean defaultValue = MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH;
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(flagName),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES, flagName, defaultValue));
    }

    @Override
    public boolean getMeasurementJobEventReportingKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants
                                        .KEY_MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH,
                                /* defaultValue */ MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH));
    }

    @Override
    public boolean getAsyncRegistrationJobQueueKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        final String flagName = FlagsConstants.KEY_MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH;
        final boolean defaultValue = MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH;
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(flagName),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES, flagName, defaultValue));
    }

    @Override
    public boolean getAsyncRegistrationFallbackJobKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        final String flagName =
                FlagsConstants.KEY_MEASUREMENT_REGISTRATION_FALLBACK_JOB_KILL_SWITCH;
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(flagName),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                flagName,
                                MEASUREMENT_REGISTRATION_FALLBACK_JOB_KILL_SWITCH));
    }

    @Override
    public boolean getMeasurementReceiverInstallAttributionKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        final String flagName =
                FlagsConstants.KEY_MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH;
        final boolean defaultValue = MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH;
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(flagName),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES, flagName, defaultValue));
    }

    @Override
    public boolean getMeasurementReceiverDeletePackagesKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        final boolean defaultValue = MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH;
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants
                                        .KEY_MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants
                                        .KEY_MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH,
                                defaultValue));
    }

    @Override
    public boolean getMeasurementRollbackDeletionKillSwitch() {
        final boolean defaultValue = MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH;
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants
                                        .KEY_MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH,
                                defaultValue));
    }

    @Override
    public boolean getMeasurementRollbackDeletionAppSearchKillSwitch() {
        final boolean defaultValue = MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH;
        return SystemProperties.getBoolean(
                getSystemPropertyName(
                        FlagsConstants.KEY_MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH),
                /* def= */ DeviceConfig.getBoolean(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* name= */ FlagsConstants
                                .KEY_MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH,
                        defaultValue));
    }

    @Override
    public String getMeasurementDebugJoinKeyEnrollmentAllowlist() {
        return DeviceConfig.getString(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_DEBUG_JOIN_KEY_ENROLLMENT_ALLOWLIST,
                /* defaultValue */ DEFAULT_MEASUREMENT_DEBUG_JOIN_KEY_ENROLLMENT_ALLOWLIST);
    }

    @Override
    public String getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist() {
        return DeviceConfig.getString(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_DEBUG_KEY_AD_ID_MATCHING_ENROLLMENT_BLOCKLIST,
                /* defaultValue */ DEFAULT_MEASUREMENT_PLATFORM_DEBUG_AD_ID_MATCHING_BLOCKLIST);
    }

    // ADID Killswitches
    @Override
    public boolean getAdIdKillSwitch() {
        // Ignore Global Killswitch for adid.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_ADID_KILL_SWITCH),
                /* defaultValue */ DeviceConfig.getBoolean(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* flagName */ FlagsConstants.KEY_ADID_KILL_SWITCH,
                        /* defaultValue */ ADID_KILL_SWITCH));
    }

    // APPSETID Killswitch.
    @Override
    public boolean getAppSetIdKillSwitch() {
        // Ignore Global Killswitch for appsetid.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_APPSETID_KILL_SWITCH),
                /* defaultValue */ DeviceConfig.getBoolean(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* flagName */ FlagsConstants.KEY_APPSETID_KILL_SWITCH,
                        /* defaultValue */ APPSETID_KILL_SWITCH));
    }

    // TOPICS Killswitches
    @Override
    public boolean getTopicsKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(FlagsConstants.KEY_TOPICS_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants.KEY_TOPICS_KILL_SWITCH,
                                /* defaultValue */ TOPICS_KILL_SWITCH));
    }

    @Override
    public boolean getTopicsOnDeviceClassifierKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH),
                /* defaultValue */ DeviceConfig.getBoolean(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* flagName */ FlagsConstants.KEY_TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH,
                        /* defaultValue */ TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH));
    }

    // MDD Killswitches
    @Override
    public boolean getMddBackgroundTaskKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(FlagsConstants.KEY_MDD_BACKGROUND_TASK_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants.KEY_MDD_BACKGROUND_TASK_KILL_SWITCH,
                                /* defaultValue */ MDD_BACKGROUND_TASK_KILL_SWITCH));
    }

    // MDD Logger Killswitches
    @Override
    public boolean getMddLoggerKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(FlagsConstants.KEY_MDD_LOGGER_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants.KEY_MDD_LOGGER_KILL_SWITCH,
                                /* defaultValue */ MDD_LOGGER_KILL_SWITCH));
    }

    // FLEDGE Kill switches

    @Override
    public boolean getFledgeSelectAdsKillSwitch() {
        // We check the Global Kill switch first. As a result, it overrides all other kill switches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(FlagsConstants.KEY_FLEDGE_SELECT_ADS_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants.KEY_FLEDGE_SELECT_ADS_KILL_SWITCH,
                                /* defaultValue */ FLEDGE_SELECT_ADS_KILL_SWITCH));
    }

    @Override
    public boolean getFledgeCustomAudienceServiceKillSwitch() {
        // We check the Global Kill switch first. As a result, it overrides all other kill switches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants
                                        .KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH,
                                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH));
    }

    @Override
    public boolean getFledgeAuctionServerKillSwitch() {
        // We check the Global Kill switch first. As a result, it overrides all other kill switches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                FlagsConstants.NAMESPACE_ADSERVICES,
                                /* flagName */ FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_KILL_SWITCH,
                                /* defaultValue */ FLEDGE_AUCTION_SERVER_KILL_SWITCH));
    }

    @Override
    public String getPpapiAppAllowList() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getString(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_PPAPI_APP_ALLOW_LIST,
                /* defaultValue */ PPAPI_APP_ALLOW_LIST);
    }

    @Override
    public String getMsmtApiAppAllowList() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getString(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MSMT_API_APP_ALLOW_LIST,
                /* defaultValue */ MSMT_API_APP_ALLOW_LIST);
    }

    // AdServices APK SHA certs.
    @Override
    public String getAdservicesApkShaCertificate() {
        return DeviceConfig.getString(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_ADSERVICES_APK_SHA_CERTS,
                /* defaultValue */ ADSERVICES_APK_SHA_CERTIFICATE);
    }

    // PPAPI Signature allow-list.
    @Override
    public String getPpapiAppSignatureAllowList() {
        return DeviceConfig.getString(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_PPAPI_APP_SIGNATURE_ALLOW_LIST,
                /* defaultValue */ PPAPI_APP_SIGNATURE_ALLOW_LIST);
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
    public float getFledgeReportInteractionRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                FlagsConstants.KEY_FLEDGE_REPORT_INTERACTION_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_REPORT_INTERACTION_REQUEST_PERMITS_PER_SECOND);
    }

    private float getPermitsPerSecond(String flagName, float defaultValue) {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        try {
            final String permitString = SystemProperties.get(getSystemPropertyName(flagName));
            if (!TextUtils.isEmpty(permitString)) {
                return parseFloat(permitString);
            }
        } catch (NumberFormatException e) {
            LogUtil.e(e, "Failed to parse %s", flagName);
            return defaultValue;
        }

        return DeviceConfig.getFloat(FlagsConstants.NAMESPACE_ADSERVICES, flagName, defaultValue);
    }

    @Override
    public String getUiOtaStringsManifestFileUrl() {
        return SystemProperties.get(
                getSystemPropertyName(FlagsConstants.KEY_UI_OTA_STRINGS_MANIFEST_FILE_URL),
                /* defaultValue */ DeviceConfig.getString(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* flagName */ FlagsConstants.KEY_UI_OTA_STRINGS_MANIFEST_FILE_URL,
                        /* defaultValue */ UI_OTA_STRINGS_MANIFEST_FILE_URL));
    }

    @Override
    public boolean getUiOtaStringsFeatureEnabled() {
        if (getGlobalKillSwitch()) {
            return false;
        }
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_UI_OTA_STRINGS_FEATURE_ENABLED),
                /* defaultValue */ DeviceConfig.getBoolean(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* flagName */ FlagsConstants.KEY_UI_OTA_STRINGS_FEATURE_ENABLED,
                        /* defaultValue */ UI_OTA_STRINGS_FEATURE_ENABLED));
    }

    @Override
    public long getUiOtaStringsDownloadDeadline() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_UI_OTA_STRINGS_DOWNLOAD_DEADLINE,
                /* defaultValue */ UI_OTA_STRINGS_DOWNLOAD_DEADLINE);
    }

    @Override
    public boolean isUiFeatureTypeLoggingEnabled() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_UI_FEATURE_TYPE_LOGGING_ENABLED,
                /* defaultValue */ UI_FEATURE_TYPE_LOGGING_ENABLED);
    }

    @Override
    public boolean getAdServicesEnabled() {
        // if the global kill switch is enabled, feature should be disabled.
        if (getGlobalKillSwitch()) {
            return false;
        }
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_ADSERVICES_ENABLED,
                /* defaultValue */ ADSERVICES_ENABLED);
    }

    @Override
    public boolean getAdServicesErrorLoggingEnabled() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_ADSERVICES_ERROR_LOGGING_ENABLED,
                /* defaultValue */ ADSERVICES_ERROR_LOGGING_ENABLED);
    }

    @Override
    public int getNumberOfEpochsToKeepInHistory() {
        int numberOfEpochsToKeepInHistory =
                DeviceConfig.getInt(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* flagName */ FlagsConstants.KEY_NUMBER_OF_EPOCHS_TO_KEEP_IN_HISTORY,
                        /* defaultValue */ NUMBER_OF_EPOCHS_TO_KEEP_IN_HISTORY);

        if (numberOfEpochsToKeepInHistory < 1) {
            throw new IllegalArgumentException("numberOfEpochsToKeepInHistory should  >= 0");
        }

        return numberOfEpochsToKeepInHistory;
    }

    @Override
    public boolean getAdSelectionOffDeviceEnabled() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_AD_SELECTION_OFF_DEVICE_ENABLED,
                FLEDGE_AD_SELECTION_OFF_DEVICE_ENABLED);
    }

    @Override
    public boolean getFledgeAdSelectionPrebuiltUriEnabled() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_AD_SELECTION_PREBUILT_URI_ENABLED,
                FLEDGE_AD_SELECTION_PREBUILT_URI_ENABLED);
    }

    @Override
    public ImmutableList<Integer> getFledgeAuctionServerPayloadBucketSizes() {
        String bucketSizesString =
                DeviceConfig.getString(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_PAYLOAD_BUCKET_SIZES,
                        null);
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
    public boolean getAdSelectionOffDeviceRequestCompressionEnabled() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_AD_SELECTION_OFF_DEVICE_REQUEST_COMPRESSION_ENABLED,
                FLEDGE_AD_SELECTION_OFF_DEVICE_REQUEST_COMPRESSION_ENABLED);
    }

    @Override
    public String getFledgeAuctionServerAuctionKeyFetchUri() {
        return DeviceConfig.getString(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_URI,
                FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_URI);
    }

    @Override
    public String getFledgeAuctionServerJoinKeyFetchUri() {
        return DeviceConfig.getString(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_JOIN_KEY_FETCH_URI,
                FLEDGE_AUCTION_SERVER_JOIN_KEY_FETCH_URI);
    }

    @Override
    public long getFledgeAuctionServerEncryptionKeyMaxAgeSeconds() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENCRYPTION_KEY_MAX_AGE_SECONDS,
                FLEDGE_AUCTION_SERVER_ENCRYPTION_KEY_MAX_AGE_SECONDS);
    }

    @Override
    public int getFledgeAuctionServerAuctionKeySharding() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_AUCTION_KEY_SHARDING,
                FLEDGE_AUCTION_SERVER_AUCTION_KEY_SHARDING);
    }

    public int getFledgeAuctionServerEncryptionAlgorithmKemId() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_KEM_ID,
                FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_KEM_ID);
    }

    @Override
    public int getFledgeAuctionServerEncryptionAlgorithmKdfId() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_KDF_ID,
                FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_KDF_ID);
    }

    @Override
    public int getFledgeAuctionServerEncryptionAlgorithmAeadId() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_AEAD_ID,
                FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_AEAD_ID);
    }

    @Override
    public long getFledgeAuctionServerAuctionKeyFetchTimeoutMs() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_TIMEOUT_MS,
                FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_TIMEOUT_MS);
    }

    @Override
    public boolean getFledgeAuctionServerBackgroundKeyFetchJobEnabled() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_ENABLED,
                FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_ENABLED);
    }

    @Override
    public boolean getFledgeAuctionServerBackgroundAuctionKeyFetchEnabled() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_AUCTION_KEY_FETCH_ENABLED,
                FLEDGE_AUCTION_SERVER_BACKGROUND_AUCTION_KEY_FETCH_ENABLED);
    }

    @Override
    public boolean getFledgeAuctionServerBackgroundJoinKeyFetchEnabled() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_JOIN_KEY_FETCH_ENABLED,
                FLEDGE_AUCTION_SERVER_BACKGROUND_JOIN_KEY_FETCH_ENABLED);
    }

    @Override
    public int getFledgeAuctionServerBackgroundKeyFetchNetworkConnectTimeoutMs() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants
                        .KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_CONNECT_TIMEOUT_MS,
                FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_CONNECT_TIMEOUT_MS);
    }

    @Override
    public int getFledgeAuctionServerBackgroundKeyFetchNetworkReadTimeoutMs() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants
                        .KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_READ_TIMEOUT_MS,
                FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_READ_TIMEOUT_MS);
    }

    @Override
    public int getFledgeAuctionServerBackgroundKeyFetchMaxResponseSizeB() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RESPONSE_SIZE_B,
                FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RESPONSE_SIZE_B);
    }

    @Override
    public long getFledgeAuctionServerBackgroundKeyFetchJobMaxRuntimeMs() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RUNTIME_MS,
                FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RUNTIME_MS);
    }

    @Override
    public long getFledgeAuctionServerBackgroundKeyFetchJobPeriodMs() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_PERIOD_MS,
                FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_PERIOD_MS);
    }

    @Override
    public long getFledgeAuctionServerBackgroundKeyFetchJobFlexMs() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_FLEX_MS,
                FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_FLEX_MS);
    }

    @Override
    public int getFledgeAuctionServerCompressionAlgorithmVersion() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_COMPRESSION_ALGORITHM_VERSION,
                FLEDGE_AUCTION_SERVER_COMPRESSION_ALGORITHM_VERSION);
    }

    @Override
    public int getFledgeAuctionServerPayloadFormatVersion() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_PAYLOAD_FORMAT_VERSION,
                FLEDGE_AUCTION_SERVER_PAYLOAD_FORMAT_VERSION);
    }

    @Override
    public boolean getFledgeAuctionServerEnableDebugReporting() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENABLE_DEBUG_REPORTING,
                FLEDGE_AUCTION_SERVER_ENABLE_DEBUG_REPORTING);
    }

    @Override
    public boolean getFledgeAuctionServerAdRenderIdEnabled() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_AD_RENDER_ID_ENABLED,
                /* defaultValue */ FLEDGE_AUCTION_SERVER_AD_RENDER_ID_ENABLED);
    }

    /** Returns the max length of Ad Render Id. */
    @Override
    public long getFledgeAuctionServerAdRenderIdMaxLength() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_AD_RENDER_ID_MAX_LENGTH,
                /* defaultValue */ FLEDGE_AUCTION_SERVER_AD_RENDER_ID_MAX_LENGTH);
    }

    @Override
    public boolean isDisableTopicsEnrollmentCheck() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_DISABLE_TOPICS_ENROLLMENT_CHECK),
                /* defaultValue */ DeviceConfig.getBoolean(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* flagName */ FlagsConstants.KEY_DISABLE_TOPICS_ENROLLMENT_CHECK,
                        /* defaultValue */ DISABLE_TOPICS_ENROLLMENT_CHECK));
    } //

    @Override
    public boolean isDisableMeasurementEnrollmentCheck() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_DISABLE_MEASUREMENT_ENROLLMENT_CHECK),
                /* defaultValue */ DeviceConfig.getBoolean(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* flagName */ FlagsConstants.KEY_DISABLE_MEASUREMENT_ENROLLMENT_CHECK,
                        /* defaultValue */ DISABLE_MEASUREMENT_ENROLLMENT_CHECK));
    }

    @Override
    public boolean isEnableEnrollmentTestSeed() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_ENABLE_ENROLLMENT_TEST_SEED,
                /* defaultValue */ ENABLE_ENROLLMENT_TEST_SEED);
    }

    @Override
    public boolean getEnrollmentMddRecordDeletionEnabled() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_ENROLLMENT_MDD_RECORD_DELETION_ENABLED,
                /* defaultValue */ ENROLLMENT_MDD_RECORD_DELETION_ENABLED);
    }

    @Override
    public boolean getEnrollmentEnableLimitedLogging() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_ENROLLMENT_ENABLE_LIMITED_LOGGING,
                /* defaultValue */ ENROLLMENT_ENABLE_LIMITED_LOGGING);
    }

    @Override
    public boolean getDisableFledgeEnrollmentCheck() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK),
                /* defaultValue */ DeviceConfig.getBoolean(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* flagName */ FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK,
                        /* defaultValue */ DISABLE_FLEDGE_ENROLLMENT_CHECK));
    }

    @Override
    public boolean getEnforceForegroundStatusForTopics() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_TOPICS),
                /* defaultValue */ DeviceConfig.getBoolean(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* flagName */ FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_TOPICS,
                        /* defaultValue */ ENFORCE_FOREGROUND_STATUS_TOPICS));
    }

    @Override
    public boolean getEnforceForegroundStatusForAdId() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_ADID),
                /* defaultValue */ DeviceConfig.getBoolean(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* flagName */ FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_ADID,
                        /* defaultValue */ ENFORCE_FOREGROUND_STATUS_ADID));
    }

    @Override
    public boolean getEnforceForegroundStatusForAppSetId() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_APPSETID),
                /* defaultValue */ DeviceConfig.getBoolean(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* flagName */ FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_APPSETID,
                        /* defaultValue */ ENFORCE_FOREGROUND_STATUS_APPSETID));
    }

    @Override
    public boolean getFledgeEventLevelDebugReportingEnabled() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_ENABLED,
                /* defaultValue */ FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_ENABLED);
    }

    @Override
    public int getFledgeEventLevelDebugReportingBatchDelaySeconds() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_BATCH_DELAY_SECONDS,
                /* defaultValue */
                FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_BATCH_DELAY_SECONDS);
    }

    @Override
    public int getFledgeEventLevelDebugReportingMaxItemsPerBatch() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_MAX_ITEMS_PER_BATCH,
                /* defaultValue */
                FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_MAX_ITEMS_PER_BATCH);
    }

    @Override
    public boolean getEnforceForegroundStatusForFledgeRunAdSelection() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION,
                /* defaultValue */ ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION);
    }

    @Override
    public boolean getEnforceForegroundStatusForFledgeReportImpression() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION,
                /* defaultValue */ ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION);
    }

    @Override
    public boolean getEnforceForegroundStatusForFledgeReportInteraction() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_INTERACTION,
                /* defaultValue */ ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_INTERACTION);
    }

    @Override
    public int getForegroundStatuslLevelForValidation() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FOREGROUND_STATUS_LEVEL,
                /* defaultValue */ FOREGROUND_STATUS_LEVEL);
    }

    @Override
    public boolean getEnforceForegroundStatusForFledgeOverrides() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDE,
                /* defaultValue */ ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDES);
    }

    @Override
    public boolean getEnforceForegroundStatusForFledgeCustomAudience() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE,
                /* defaultValue */ ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE);
    }

    @Override
    public boolean getFledgeRegisterAdBeaconEnabled() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED,
                /* defaultValue */ FLEDGE_REGISTER_AD_BEACON_ENABLED);
    }

    @Override
    public boolean getFledgeCpcBillingEnabled() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_CPC_BILLING_ENABLED,
                /* defaultValue */ FLEDGE_CPC_BILLING_ENABLED);
    }

    @Override
    public boolean getEnforceForegroundStatusForMeasurementDeleteRegistrations() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_DELETE_REGISTRATIONS,
                /* defaultValue */ MEASUREMENT_ENFORCE_FOREGROUND_STATUS_DELETE_REGISTRATIONS);
    }

    @Override
    public boolean getEnforceForegroundStatusForMeasurementRegisterSource() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCE,
                /* defaultValue */ MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCE);
    }

    @Override
    public boolean getEnforceForegroundStatusForMeasurementRegisterTrigger() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_TRIGGER,
                /* defaultValue */ MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_TRIGGER);
    }

    @Override
    public boolean getEnforceForegroundStatusForMeasurementRegisterWebSource() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_SOURCE,
                /* defaultValue */ MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_SOURCE);
    }

    @Override
    public boolean getEnforceForegroundStatusForMeasurementRegisterWebTrigger() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_TRIGGER,
                /* defaultValue */ MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_TRIGGER);
    }

    @Override
    public boolean getEnforceForegroundStatusForMeasurementStatus() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_GET_STATUS,
                /* defaultValue */ MEASUREMENT_ENFORCE_FOREGROUND_STATUS_GET_STATUS);
    }

    @Override
    public boolean getEnforceEnrollmentOriginMatch() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_ENFORCE_ENROLLMENT_ORIGIN_MATCH,
                /* defaultValue */ MEASUREMENT_ENFORCE_ENROLLMENT_ORIGIN_MATCH);
    }

    @Override
    public boolean getEnforceIsolateMaxHeapSize() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_ENFORCE_ISOLATE_MAX_HEAP_SIZE,
                /* defaultValue */ ENFORCE_ISOLATE_MAX_HEAP_SIZE);
    }

    @Override
    public long getIsolateMaxHeapSizeBytes() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_ISOLATE_MAX_HEAP_SIZE_BYTES,
                /* defaultValue */ ISOLATE_MAX_HEAP_SIZE_BYTES);
    }

    @Override
    public String getWebContextClientAppAllowList() {
        return DeviceConfig.getString(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_WEB_CONTEXT_CLIENT_ALLOW_LIST,
                /* defaultValue */ WEB_CONTEXT_CLIENT_ALLOW_LIST);
    }

    @Override
    public String getConsentNotificationResetToken() {
        return DeviceConfig.getString(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_CONSENT_NOTIFICATION_RESET_TOKEN,
                /* defaultValue */ CONSENT_NOTIFICATION_RESET_TOKEN);
    }

    @Override
    public long getConsentNotificationIntervalBeginMs() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_CONSENT_NOTIFICATION_INTERVAL_BEGIN_MS,
                /* defaultValue */ CONSENT_NOTIFICATION_INTERVAL_BEGIN_MS);
    }

    @Override
    public long getConsentNotificationIntervalEndMs() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_CONSENT_NOTIFICATION_INTERVAL_END_MS,
                /* defaultValue */ CONSENT_NOTIFICATION_INTERVAL_END_MS);
    }

    @Override
    public long getConsentNotificationMinimalDelayBeforeIntervalEnds() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_CONSENT_NOTIFICATION_MINIMAL_DELAY_BEFORE_INTERVAL_ENDS,
                /* defaultValue */ CONSENT_NOTIFICATION_MINIMAL_DELAY_BEFORE_INTERVAL_ENDS);
    }

    @Override
    public boolean getConsentNotificationDebugMode() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE,
                /* defaultValue */ CONSENT_NOTIFICATION_DEBUG_MODE);
    }

    @Override
    public boolean getConsentNotificationActivityDebugMode() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE,
                /* defaultValue */ CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE);
    }

    @Override
    public boolean getConsentManagerDebugMode() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE),
                /* defaultValue */ CONSENT_MANAGER_DEBUG_MODE);
    }

    @Override
    public int getConsentSourceOfTruth() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_CONSENT_SOURCE_OF_TRUTH,
                /* defaultValue */ DEFAULT_CONSENT_SOURCE_OF_TRUTH);
    }

    @Override
    public int getBlockedTopicsSourceOfTruth() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_BLOCKED_TOPICS_SOURCE_OF_TRUTH,
                /* defaultValue */ DEFAULT_BLOCKED_TOPICS_SOURCE_OF_TRUTH);
    }

    @Override
    public long getMaxResponseBasedRegistrationPayloadSizeBytes() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES,
                /* defaultValue */ MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES);
    }

    @VisibleForTesting
    static String getSystemPropertyName(String key) {
        return AdServicesCommon.SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX + key;
    }

    @Override
    public boolean getUIDialogsFeatureEnabled() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_UI_DIALOGS_FEATURE_ENABLED),
                /* defaultValue */ DeviceConfig.getBoolean(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* flagName */ FlagsConstants.KEY_UI_DIALOGS_FEATURE_ENABLED,
                        /* defaultValue */ UI_DIALOGS_FEATURE_ENABLED));
    }

    @Override
    public boolean getUiDialogFragmentEnabled() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_UI_DIALOG_FRAGMENT_ENABLED),
                /* defaultValue */ DeviceConfig.getBoolean(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* flagName */ FlagsConstants.KEY_UI_DIALOG_FRAGMENT_ENABLED,
                        /* defaultValue */ UI_DIALOG_FRAGMENT));
    }

    @Override
    public boolean isEeaDeviceFeatureEnabled() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_IS_EEA_DEVICE_FEATURE_ENABLED,
                IS_EEA_DEVICE_FEATURE_ENABLED);
    }

    @Override
    public boolean isEeaDevice() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_IS_EEA_DEVICE,
                IS_EEA_DEVICE);
    }

    @Override
    public boolean getRecordManualInteractionEnabled() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                FlagsConstants.KEY_RECORD_MANUAL_INTERACTION_ENABLED,
                RECORD_MANUAL_INTERACTION_ENABLED);
    }

    @Override
    @SuppressWarnings("InlinedApi")
    public boolean isBackCompatActivityFeatureEnabled() {
        // Check if enable Back compat is true first and then check flag value
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return getEnableBackCompat()
                && DeviceConfig.getBoolean(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* flagName */ FlagsConstants.KEY_IS_BACK_COMPACT_ACTIVITY_FEATURE_ENABLED,
                        /* defaultValue */ IS_BACK_COMPACT_ACTIVITY_FEATURE_ENABLED);
    }

    @Override
    public String getUiEeaCountries() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        String uiEeaCountries =
                DeviceConfig.getString(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        FlagsConstants.KEY_UI_EEA_COUNTRIES,
                        UI_EEA_COUNTRIES);
        return uiEeaCountries;
    }

    @Override
    public boolean getGaUxFeatureEnabled() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return SystemProperties.getBoolean(
                getSystemPropertyName(FlagsConstants.KEY_GA_UX_FEATURE_ENABLED),
                /* defaultValue */ DeviceConfig.getBoolean(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* flagName */ FlagsConstants.KEY_GA_UX_FEATURE_ENABLED,
                        /* defaultValue */ GA_UX_FEATURE_ENABLED));
    }

    @Override
    public String getDebugUx() {
        return DeviceConfig.getString(
                FlagsConstants.NAMESPACE_ADSERVICES, /* flagName */
                FlagsConstants.KEY_DEBUG_UX, /* defaultValue */
                DEBUG_UX);
    }

    @Override
    public boolean getToggleSpeedBumpEnabled() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_UI_TOGGLE_SPEED_BUMP_ENABLED,
                /* defaultValue */ TOGGLE_SPEED_BUMP_ENABLED);
    }

    @Override
    public long getAdSelectionExpirationWindowS() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_FLEDGE_AD_SELECTION_EXPIRATION_WINDOW_S,
                /* defaultValue */ FLEDGE_AD_SELECTION_EXPIRATION_WINDOW_S);
    }

    @Override
    public boolean getMeasurementFlexibleEventReportingApiEnabled() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_FLEXIBLE_EVENT_REPORTING_API_ENABLED,
                /* defaultValue */ MEASUREMENT_FLEXIBLE_EVENT_REPORTING_API_ENABLED);
    }

    @Override
    public boolean getMeasurementFlexLiteAPIEnabled() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_FLEX_LITE_API_ENABLED,
                /* defaultValue */ MEASUREMENT_FLEX_LITE_API_ENABLED);
    }

    @Override
    public float getMeasurementFlexApiMaxInformationGainEvent() {
        return DeviceConfig.getFloat(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_EVENT,
                /* defaultValue */ MEASUREMENT_FLEX_API_MAX_INFO_GAIN_EVENT);
    }

    @Override
    public float getMeasurementFlexApiMaxInformationGainNavigation() {
        return DeviceConfig.getFloat(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_NAVIGATION,
                /* defaultValue */ MEASUREMENT_FLEX_API_MAX_INFO_GAIN_NAVIGATION);
    }

    @Override
    public int getMeasurementFlexApiMaxEventReports() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_FLEX_API_MAX_EVENT_REPORTS,
                /* defaultValue */ MEASUREMENT_FLEX_API_MAX_EVENT_REPORTS);
    }

    @Override
    public int getMeasurementFlexApiMaxEventReportWindows() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_FLEX_API_MAX_EVENT_REPORT_WINDOWS,
                /* defaultValue */ MEASUREMENT_FLEX_API_MAX_EVENT_REPORT_WINDOWS);
    }

    @Override
    public int getMeasurementFlexApiMaxTriggerDataCardinality() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_FLEX_API_MAX_TRIGGER_DATA_CARDINALITY,
                /* defaultValue */ MEASUREMENT_FLEX_API_MAX_TRIGGER_DATA_CARDINALITY);
    }

    @Override
    public long getMeasurementMinimumEventReportWindowInSeconds() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_MINIMUM_EVENT_REPORT_WINDOW_IN_SECONDS,
                /* defaultValue */ MEASUREMENT_MINIMUM_EVENT_REPORT_WINDOW_IN_SECONDS);
    }

    @Override
    public int getMeasurementMaxSourcesPerPublisher() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_MAX_SOURCES_PER_PUBLISHER,
                /* defaultValue */ MEASUREMENT_MAX_SOURCES_PER_PUBLISHER);
    }

    @Override
    public int getMeasurementMaxTriggersPerDestination() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_MAX_TRIGGERS_PER_DESTINATION,
                /* defaultValue */ MEASUREMENT_MAX_TRIGGERS_PER_DESTINATION);
    }

    @Override
    public int getMeasurementMaxAggregateReportsPerDestination() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_DESTINATION,
                /* defaultValue */ MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_DESTINATION);
    }

    @Override
    public int getMeasurementMaxEventReportsPerDestination() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_MAX_EVENT_REPORTS_PER_DESTINATION,
                /* defaultValue */ MEASUREMENT_MAX_EVENT_REPORTS_PER_DESTINATION);
    }

    @Override
    public boolean getMeasurementEnableMaxAggregateReportsPerSource() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_ENABLE_MAX_AGGREGATE_REPORTS_PER_SOURCE,
                /* defaultValue */ MEASUREMENT_ENABLE_MAX_AGGREGATE_REPORTS_PER_SOURCE);
    }

    @Override
    public int getMeasurementMaxAggregateReportsPerSource() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_SOURCE,
                /* defaultValue */ MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_SOURCE);
    }

    @Override
    public int getMeasurementMaxAggregateKeysPerSourceRegistration() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_MAX_AGGREGATE_KEYS_PER_SOURCE_REGISTRATION,
                /* defaultValue */ MEASUREMENT_MAX_AGGREGATE_KEYS_PER_SOURCE_REGISTRATION);
    }

    @Override
    public int getMeasurementMaxAggregateKeysPerTriggerRegistration() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_MAX_AGGREGATE_KEYS_PER_TRIGGER_REGISTRATION,
                /* defaultValue */ MEASUREMENT_MAX_AGGREGATE_KEYS_PER_TRIGGER_REGISTRATION);
    }

    @Override
    public long getMeasurementMinEventReportDelayMillis() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS,
                /* defaultValue */ MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS);
    }

    @Override
    public boolean getMeasurementEnableConfigurableEventReportingWindows() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_ENABLE_CONFIGURABLE_EVENT_REPORTING_WINDOWS,
                /* defaultValue */ MEASUREMENT_ENABLE_CONFIGURABLE_EVENT_REPORTING_WINDOWS);
    }

    @Override
    public String getMeasurementEventReportsVtcEarlyReportingWindows() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getString(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS,
                /* defaultValue */ MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS);
    }

    @Override
    public String getMeasurementEventReportsCtcEarlyReportingWindows() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getString(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS,
                /* defaultValue */ MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS);
    }

    @Override
    public boolean getMeasurementEnableConfigurableAggregateReportDelay() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_ENABLE_CONFIGURABLE_AGGREGATE_REPORT_DELAY,
                /* defaultValue */ MEASUREMENT_ENABLE_CONFIGURABLE_AGGREGATE_REPORT_DELAY);
    }

    @Override
    public String getMeasurementAggregateReportDelayConfig() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getString(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_AGGREGATE_REPORT_DELAY_CONFIG,
                /* defaultValue */ MEASUREMENT_AGGREGATE_REPORT_DELAY_CONFIG);
    }

    @Override
    public boolean isEnrollmentBlocklisted(String enrollmentId) {
        return getEnrollmentBlocklist().contains(enrollmentId);
    }

    @Override
    public boolean getEnableLoggedTopic() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_ENABLE_LOGGED_TOPIC,
                /* defaultValue */ ENABLE_LOGGED_TOPIC);
    }

    @Override
    public boolean getEnableDatabaseSchemaVersion8() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_ENABLE_DATABASE_SCHEMA_VERSION_8,
                /* defaultValue */ ENABLE_DATABASE_SCHEMA_VERSION_8);
    }

    @Override
    public boolean getFledgeMeasurementReportAndRegisterEventApiEnabled() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED,
                /* defaultValue */ FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED);
    }

    @Override
    public void dump(@NonNull PrintWriter writer, @Nullable String[] args) {
        writer.println("\t" + FlagsConstants.KEY_DEBUG_UX + " = " + getDebugUx());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_ENABLE_AD_SERVICES_SYSTEM_API
                        + " = "
                        + getEnableAdServicesSystemApi());
        writer.println("\t" + FlagsConstants.KEY_U18_UX_ENABLED + " = " + getU18UxEnabled());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE
                        + " = "
                        + getConsentNotificationActivityDebugMode());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_CONSENT_NOTIFICATION_RESET_TOKEN
                        + " = "
                        + getConsentNotificationResetToken());
        writer.println("==== AdServices PH Flags Dump Enrollment ====");
        writer.println(
                "\t"
                        + FlagsConstants.KEY_DISABLE_TOPICS_ENROLLMENT_CHECK
                        + " = "
                        + isDisableTopicsEnrollmentCheck());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK
                        + " = "
                        + getDisableFledgeEnrollmentCheck());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_DISABLE_MEASUREMENT_ENROLLMENT_CHECK
                        + " = "
                        + isDisableMeasurementEnrollmentCheck());

        writer.println(
                "\t"
                        + FlagsConstants.KEY_ENABLE_ENROLLMENT_TEST_SEED
                        + " = "
                        + isEnableEnrollmentTestSeed());

        writer.println(
                "\t"
                        + FlagsConstants.KEY_ENROLLMENT_MDD_RECORD_DELETION_ENABLED
                        + " = "
                        + getEnrollmentMddRecordDeletionEnabled());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_ENROLLMENT_ENABLE_LIMITED_LOGGING
                        + " = "
                        + getEnrollmentEnableLimitedLogging());

        writer.println("==== AdServices PH Flags Dump killswitches ====");
        writer.println(
                "\t" + FlagsConstants.KEY_GLOBAL_KILL_SWITCH + " = " + getGlobalKillSwitch());
        writer.println(
                "\t" + FlagsConstants.KEY_TOPICS_KILL_SWITCH + " = " + getTopicsKillSwitch());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH
                        + " = "
                        + getTopicsOnDeviceClassifierKillSwitch());
        writer.println("\t" + FlagsConstants.KEY_ADID_KILL_SWITCH + " = " + getAdIdKillSwitch());
        writer.println(
                "\t" + FlagsConstants.KEY_APPSETID_KILL_SWITCH + " = " + getAppSetIdKillSwitch());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_SDK_REQUEST_PERMITS_PER_SECOND
                        + " = "
                        + getSdkRequestPermitsPerSecond());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_REGISTER_SOURCE_REQUEST_PERMITS_PER_SECOND
                        + " = "
                        + getMeasurementRegisterSourceRequestPermitsPerSecond());
        writer.println(
                "\t"
                        + FlagsConstants
                                .KEY_MEASUREMENT_REGISTER_WEB_SOURCE_REQUEST_PERMITS_PER_SECOND
                        + " = "
                        + getMeasurementRegisterWebSourceRequestPermitsPerSecond());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_REGISTER_SOURCES_REQUEST_PERMITS_PER_SECOND
                        + " = "
                        + getMeasurementRegisterSourcesRequestPermitsPerSecond());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_REGISTER_TRIGGER_REQUEST_PERMITS_PER_SECOND
                        + " = "
                        + getMeasurementRegisterTriggerRequestPermitsPerSecond());
        writer.println(
                "\t"
                        + FlagsConstants
                                .KEY_MEASUREMENT_REGISTER_WEB_TRIGGER_REQUEST_PERMITS_PER_SECOND
                        + " = "
                        + getMeasurementRegisterWebTriggerRequestPermitsPerSecond());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MDD_BACKGROUND_TASK_KILL_SWITCH
                        + " = "
                        + getMddBackgroundTaskKillSwitch());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MDD_LOGGER_KILL_SWITCH
                        + " = "
                        + getMddLoggerKillSwitch());

        writer.println("==== AdServices PH Flags Dump AllowList ====");
        writer.println(
                "\t"
                        + FlagsConstants.KEY_PPAPI_APP_SIGNATURE_ALLOW_LIST
                        + " = "
                        + getPpapiAppSignatureAllowList());
        writer.println(
                "\t" + FlagsConstants.KEY_PPAPI_APP_ALLOW_LIST + " = " + getPpapiAppAllowList());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MSMT_API_APP_ALLOW_LIST
                        + " = "
                        + getMsmtApiAppAllowList());

        writer.println("==== AdServices PH Flags Dump MDD related flags: ====");
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_MANIFEST_FILE_URL
                        + " = "
                        + getMeasurementManifestFileUrl());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_UI_OTA_STRINGS_MANIFEST_FILE_URL
                        + " = "
                        + getUiOtaStringsManifestFileUrl());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_DOWNLOADER_CONNECTION_TIMEOUT_MS
                        + " = "
                        + getDownloaderConnectionTimeoutMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_DOWNLOADER_READ_TIMEOUT_MS
                        + " = "
                        + getDownloaderReadTimeoutMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_DOWNLOADER_MAX_DOWNLOAD_THREADS
                        + " = "
                        + getDownloaderMaxDownloadThreads());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL
                        + " = "
                        + getMddTopicsClassifierManifestFileUrl());
        writer.println("==== AdServices PH Flags Dump Topics related flags ====");
        writer.println(
                "\t"
                        + FlagsConstants.KEY_TOPICS_EPOCH_JOB_PERIOD_MS
                        + " = "
                        + getTopicsEpochJobPeriodMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_TOPICS_EPOCH_JOB_FLEX_MS
                        + " = "
                        + getTopicsEpochJobFlexMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC
                        + " = "
                        + getTopicsPercentageForRandomTopic());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_TOPICS_NUMBER_OF_TOP_TOPICS
                        + " = "
                        + getTopicsNumberOfTopTopics());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_TOPICS_NUMBER_OF_RANDOM_TOPICS
                        + " = "
                        + getTopicsNumberOfRandomTopics());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS
                        + " = "
                        + getTopicsNumberOfLookBackEpochs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_GLOBAL_BLOCKED_TOPIC_IDS
                        + " = "
                        + getGlobalBlockedTopicIds());

        writer.println("==== AdServices PH Flags Dump Topics Classifier related flags ====");
        writer.println(
                "\t"
                        + FlagsConstants.KEY_CLASSIFIER_NUMBER_OF_TOP_LABELS
                        + " = "
                        + getClassifierNumberOfTopLabels());
        writer.println("\t" + FlagsConstants.KEY_CLASSIFIER_TYPE + " = " + getClassifierType());
        writer.println(
                "\t" + FlagsConstants.KEY_CLASSIFIER_THRESHOLD + " = " + getClassifierThreshold());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_CLASSIFIER_DESCRIPTION_MAX_LENGTH
                        + " = "
                        + getClassifierDescriptionMaxLength());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_CLASSIFIER_DESCRIPTION_MAX_WORDS
                        + " = "
                        + getClassifierDescriptionMaxWords());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_CLASSIFIER_FORCE_USE_BUNDLED_FILES
                        + " = "
                        + getClassifierForceUseBundledFiles());

        writer.println(
                "\t"
                        + FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_TOPICS
                        + " = "
                        + getEnforceForegroundStatusForTopics());

        writer.println("==== AdServices PH Flags Dump Measurement related flags: ====");
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_DB_SIZE_LIMIT
                        + " = "
                        + getMeasurementDbSizeLimit());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS
                        + " = "
                        + getMeasurementEventMainReportingJobPeriodMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS
                        + " = "
                        + getMeasurementEventFallbackReportingJobPeriodMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_ENABLED
                        + " = "
                        + getMeasurementAggregationCoordinatorOriginEnabled());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_LIST
                        + " = "
                        + getMeasurementAggregationCoordinatorOriginList());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_DEFAULT_AGGREGATION_COORDINATOR_ORIGIN
                        + " = "
                        + getMeasurementDefaultAggregationCoordinatorOrigin());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_AGGREGATION_COORDINATOR_PATH
                        + " = "
                        + getMeasurementAggregationCoordinatorPath());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS
                        + " = "
                        + getMeasurementAggregateMainReportingJobPeriodMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS
                        + " = "
                        + getMeasurementAggregateFallbackReportingJobPeriodMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_NETWORK_CONNECT_TIMEOUT_MS
                        + " = "
                        + getMeasurementNetworkConnectTimeoutMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_NETWORK_READ_TIMEOUT_MS
                        + " = "
                        + getMeasurementNetworkReadTimeoutMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_IS_CLICK_VERIFICATION_ENABLED
                        + " = "
                        + getMeasurementIsClickVerificationEnabled());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_REGISTRATION_INPUT_EVENT_VALID_WINDOW_MS
                        + " = "
                        + getMeasurementRegistrationInputEventValidWindowMs());
        writer.println(
                "\t"
                        + FlagsConstants
                                .KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_DELETE_REGISTRATIONS
                        + " = "
                        + getEnforceForegroundStatusForMeasurementDeleteRegistrations());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_GET_STATUS
                        + " = "
                        + getEnforceForegroundStatusForMeasurementStatus());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCE
                        + " = "
                        + getEnforceForegroundStatusForMeasurementRegisterSource());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_TRIGGER
                        + " = "
                        + getEnforceForegroundStatusForMeasurementRegisterTrigger());
        writer.println(
                "\t"
                        + FlagsConstants
                                .KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_SOURCE
                        + " = "
                        + getEnforceForegroundStatusForMeasurementRegisterWebSource());
        writer.println(
                "\t"
                        + FlagsConstants
                                .KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_TRIGGER
                        + " = "
                        + getEnforceForegroundStatusForMeasurementRegisterWebTrigger());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_ENABLE_XNA
                        + " = "
                        + getMeasurementEnableXNA());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_ENABLE_SHARED_SOURCE_DEBUG_KEY
                        + " = "
                        + getMeasurementEnableSharedSourceDebugKey());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_ENABLE_SHARED_FILTER_DATA_KEYS_XNA
                        + " = "
                        + getMeasurementEnableSharedFilterDataKeysXNA());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_ENFORCE_ENROLLMENT_ORIGIN_MATCH
                        + " = "
                        + getEnforceEnrollmentOriginMatch());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_ENABLE_DEBUG_REPORT
                        + " = "
                        + getMeasurementEnableDebugReport());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_ENABLE_SOURCE_DEBUG_REPORT
                        + " = "
                        + getMeasurementEnableSourceDebugReport());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_ENABLE_TRIGGER_DEBUG_REPORT
                        + " = "
                        + getMeasurementEnableTriggerDebugReport());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_DATA_EXPIRY_WINDOW_MS
                        + " = "
                        + getMeasurementDataExpiryWindowMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH
                        + " = "
                        + getMeasurementRollbackDeletionKillSwitch());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_DEBUG_JOIN_KEY_HASH_LIMIT
                        + " = "
                        + getMeasurementDebugJoinKeyHashLimit());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_DEBUG_KEY_AD_ID_MATCHING_LIMIT
                        + " = "
                        + getMeasurementPlatformDebugAdIdMatchingLimit());
        writer.println(
                "\t"
                        + FlagsConstants
                                .KEY_MEASUREMENT_DEBUG_KEY_AD_ID_MATCHING_ENROLLMENT_BLOCKLIST
                        + " = "
                        + getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_FLEXIBLE_EVENT_REPORTING_API_ENABLED
                        + " = "
                        + getMeasurementFlexibleEventReportingApiEnabled());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_FLEX_LITE_API_ENABLED
                        + " = "
                        + getMeasurementFlexLiteAPIEnabled());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_EVENT
                        + " = "
                        + getMeasurementFlexApiMaxInformationGainEvent());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_NAVIGATION
                        + " = "
                        + getMeasurementFlexApiMaxInformationGainNavigation());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_FLEX_API_MAX_EVENT_REPORTS
                        + " = "
                        + getMeasurementFlexApiMaxEventReports());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_FLEX_API_MAX_EVENT_REPORT_WINDOWS
                        + " = "
                        + getMeasurementFlexApiMaxEventReportWindows());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_FLEX_API_MAX_TRIGGER_DATA_CARDINALITY
                        + " = "
                        + getMeasurementFlexApiMaxTriggerDataCardinality());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_MINIMUM_EVENT_REPORT_WINDOW_IN_SECONDS
                        + " = "
                        + getMeasurementMinimumEventReportWindowInSeconds());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_WEB_CONTEXT_CLIENT_ALLOW_LIST
                        + " = "
                        + getWebContextClientAppAllowList());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES
                        + " = "
                        + getMaxResponseBasedRegistrationPayloadSizeBytes());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_MAX_REGISTRATION_REDIRECTS
                        + " = "
                        + getMeasurementMaxRegistrationRedirects());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_MAX_REGISTRATIONS_PER_JOB_INVOCATION
                        + " = "
                        + getMeasurementMaxRegistrationsPerJobInvocation());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_MAX_RETRIES_PER_REGISTRATION_REQUEST
                        + " = "
                        + getMeasurementMaxRetriesPerRegistrationRequest());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_ASYNC_REGISTRATION_JOB_TRIGGER_MIN_DELAY_MS
                        + " = "
                        + getMeasurementAsyncRegistrationJobTriggerMinDelayMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_ASYNC_REGISTRATION_JOB_TRIGGER_MAX_DELAY_MS
                        + " = "
                        + getMeasurementAsyncRegistrationJobTriggerMaxDelayMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH
                        + " = "
                        + getAsyncRegistrationJobQueueKillSwitch());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_REGISTRATION_FALLBACK_JOB_KILL_SWITCH
                        + " = "
                        + getAsyncRegistrationFallbackJobKillSwitch());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_KILL_SWITCH
                        + " = "
                        + getMeasurementAttributionFallbackJobKillSwitch());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_PERIOD_MS
                        + " = "
                        + getMeasurementAttributionFallbackJobPeriodMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_MAX_SOURCES_PER_PUBLISHER
                        + " = "
                        + getMeasurementMaxSourcesPerPublisher());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_MAX_TRIGGERS_PER_DESTINATION
                        + " = "
                        + getMeasurementMaxTriggersPerDestination());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_DESTINATION
                        + " = "
                        + getMeasurementMaxAggregateReportsPerDestination());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_MAX_EVENT_REPORTS_PER_DESTINATION
                        + " = "
                        + getMeasurementMaxEventReportsPerDestination());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS
                        + " = "
                        + getMeasurementMinEventReportDelayMillis());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_ENABLE_CONFIGURABLE_EVENT_REPORTING_WINDOWS
                        + " = "
                        + getMeasurementEnableConfigurableEventReportingWindows());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS
                        + " = "
                        + getMeasurementEventReportsVtcEarlyReportingWindows());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS
                        + " = "
                        + getMeasurementEventReportsCtcEarlyReportingWindows());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_ENABLE_CONFIGURABLE_AGGREGATE_REPORT_DELAY
                        + " = "
                        + getMeasurementEnableConfigurableAggregateReportDelay());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_AGGREGATE_REPORT_DELAY_CONFIG
                        + " = "
                        + getMeasurementAggregateReportDelayConfig());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_MAX_ATTRIBUTION_PER_RATE_LIMIT_WINDOW
                        + " = "
                        + getMeasurementMaxAttributionPerRateLimitWindow());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_MAX_DISTINCT_ENROLLMENTS_IN_ATTRIBUTION
                        + " = "
                        + getMeasurementMaxDistinctEnrollmentsInAttribution());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_MAX_DISTINCT_DESTINATIONS_IN_ACTIVE_SOURCE
                        + " = "
                        + getMeasurementMaxDistinctDestinationsInActiveSource());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT
                        + " = "
                        + getMeasurementVtcConfigurableMaxEventReportsCount());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_ENABLE_COARSE_EVENT_REPORT_DESTINATIONS
                        + " = "
                        + getMeasurementEnableCoarseEventReportDestinations());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_ENABLE_ARA_PARSING_ALIGNMENT_V1
                        + " = "
                        + getMeasurementEnableAraParsingAlignmentV1());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_ENABLE_ARA_DEDUPLICATION_ALIGNMENT_V1
                        + " = "
                        + getMeasurementEnableAraDeduplicationAlignmentV1());
        writer.println("==== AdServices PH Flags Dump FLEDGE related flags: ====");
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_SELECT_ADS_KILL_SWITCH
                        + " = "
                        + getFledgeSelectAdsKillSwitch());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH
                        + " = "
                        + getFledgeCustomAudienceServiceKillSwitch());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_KILL_SWITCH
                        + " = "
                        + getFledgeAuctionServerKillSwitch());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT
                        + " = "
                        + getFledgeCustomAudienceMaxCount());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT
                        + " = "
                        + getFledgeCustomAudienceMaxOwnerCount());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT
                        + " = "
                        + getFledgeCustomAudiencePerAppMaxCount());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN_MS
                        + " = "
                        + getFledgeCustomAudienceDefaultExpireInMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN_MS
                        + " = "
                        + getFledgeCustomAudienceMaxActivationDelayInMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_EXPIRE_IN_MS
                        + " = "
                        + getFledgeCustomAudienceMaxExpireInMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NAME_SIZE_B
                        + " = "
                        + getFledgeCustomAudienceMaxNameSizeB());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_DAILY_UPDATE_URI_SIZE_B
                        + " = "
                        + getFledgeCustomAudienceMaxDailyUpdateUriSizeB());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_BIDDING_LOGIC_URI_SIZE_B
                        + " = "
                        + getFledgeCustomAudienceMaxBiddingLogicUriSizeB());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B
                        + " = "
                        + getFledgeCustomAudienceMaxUserBiddingSignalsSizeB());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B
                        + " = "
                        + getFledgeCustomAudienceMaxTrustedBiddingDataSizeB());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ADS_SIZE_B
                        + " = "
                        + getFledgeCustomAudienceMaxAdsSizeB());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS
                        + " = "
                        + getFledgeCustomAudienceActiveTimeWindowInMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS
                        + " = "
                        + getFledgeCustomAudienceMaxNumAds());
        writer.println(
                "\t"
                        + FlagsConstants
                                .KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B
                        + " = "
                        + getFledgeFetchCustomAudienceMaxUserBiddingSignalsSizeB());
        writer.println(
                "\t"
                        + FlagsConstants
                                .KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_REQUEST_CUSTOM_HEADER_SIZE_B
                        + " = "
                        + getFledgeFetchCustomAudienceMaxRequestCustomHeaderSizeB());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_CUSTOM_AUDIENCE_SIZE_B
                        + " = "
                        + getFledgeFetchCustomAudienceMaxCustomAudienceSizeB());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_HTTP_CACHE_ENABLE
                        + " = "
                        + getFledgeHttpCachingEnabled());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_HTTP_CACHE_ENABLE_JS_CACHING
                        + " = "
                        + getFledgeHttpJsCachingEnabled());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_HTTP_CACHE_MAX_ENTRIES
                        + " = "
                        + getFledgeHttpCacheMaxEntries());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_HTTP_CACHE_DEFAULT_MAX_AGE_SECONDS
                        + " = "
                        + getFledgeHttpCacheMaxAgeSeconds());
        writer.println(
                "\t"
                        + FlagsConstants
                                .KEY_FLEDGE_AD_COUNTER_HISTOGRAM_ABSOLUTE_MAX_TOTAL_EVENT_COUNT
                        + " = "
                        + getFledgeAdCounterHistogramAbsoluteMaxTotalEventCount());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AD_COUNTER_HISTOGRAM_LOWER_MAX_TOTAL_EVENT_COUNT
                        + " = "
                        + getFledgeAdCounterHistogramLowerMaxTotalEventCount());
        writer.println(
                "\t"
                        + FlagsConstants
                                .KEY_FLEDGE_AD_COUNTER_HISTOGRAM_ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT
                        + " = "
                        + getFledgeAdCounterHistogramAbsoluteMaxPerBuyerEventCount());
        writer.println(
                "\t"
                        + FlagsConstants
                                .KEY_FLEDGE_AD_COUNTER_HISTOGRAM_LOWER_MAX_PER_BUYER_EVENT_COUNT
                        + " = "
                        + getFledgeAdCounterHistogramLowerMaxPerBuyerEventCount());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_ENABLED
                        + " = "
                        + getFledgeBackgroundFetchEnabled());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS
                        + " = "
                        + getFledgeBackgroundFetchJobPeriodMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS
                        + " = "
                        + getFledgeBackgroundFetchJobFlexMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_MAX_NUM_UPDATED
                        + " = "
                        + getFledgeBackgroundFetchMaxNumUpdated());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_THREAD_POOL_SIZE
                        + " = "
                        + getFledgeBackgroundFetchThreadPoolSize());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_ELIGIBLE_UPDATE_BASE_INTERVAL_S
                        + " = "
                        + getFledgeBackgroundFetchEligibleUpdateBaseIntervalS());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS
                        + " = "
                        + getFledgeBackgroundFetchNetworkConnectTimeoutMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS
                        + " = "
                        + getFledgeBackgroundFetchNetworkReadTimeoutMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_MAX_RESPONSE_SIZE_B
                        + " = "
                        + getFledgeBackgroundFetchMaxResponseSizeB());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AD_SELECTION_MAX_CONCURRENT_BIDDING_COUNT
                        + " = "
                        + getAdSelectionMaxConcurrentBiddingCount());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS
                        + " = "
                        + getAdSelectionBiddingTimeoutPerCaMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_BUYER_MS
                        + " = "
                        + getAdSelectionBiddingTimeoutPerBuyerMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS
                        + " = "
                        + getAdSelectionScoringTimeoutMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS
                        + " = "
                        + getAdSelectionSelectingOutcomeTimeoutMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_URI
                        + " = "
                        + getFledgeAuctionServerAuctionKeyFetchUri());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_JOIN_KEY_FETCH_URI
                        + " = "
                        + getFledgeAuctionServerJoinKeyFetchUri());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_AUCTION_KEY_SHARDING
                        + " = "
                        + getFledgeAuctionServerAuctionKeySharding());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_KEM_ID
                        + " = "
                        + getFledgeAuctionServerEncryptionAlgorithmKemId());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_KDF_ID
                        + " = "
                        + getFledgeAuctionServerEncryptionAlgorithmKdfId());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_AEAD_ID
                        + " = "
                        + getFledgeAuctionServerEncryptionAlgorithmAeadId());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS
                        + " = "
                        + getAdSelectionOverallTimeoutMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS
                        + " = "
                        + getAdSelectionFromOutcomesOverallTimeoutMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AD_SELECTION_OFF_DEVICE_OVERALL_TIMEOUT_MS
                        + " = "
                        + getAdSelectionOffDeviceOverallTimeoutMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AD_SELECTION_FILTERING_ENABLED
                        + " = "
                        + getFledgeAdSelectionFilteringEnabled());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AD_SELECTION_FILTERING_ENABLED
                        + " = "
                        + getFledgeAdSelectionContextualAdsEnabled());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_TIMEOUT_MS
                        + " = "
                        + getFledgeAuctionServerAuctionKeyFetchTimeoutMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_ENABLED
                        + " = "
                        + getFledgeAuctionServerBackgroundKeyFetchJobEnabled());
        writer.println(
                "\t"
                        + FlagsConstants
                                .KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_AUCTION_KEY_FETCH_ENABLED
                        + " = "
                        + getFledgeAuctionServerBackgroundAuctionKeyFetchEnabled());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_JOIN_KEY_FETCH_ENABLED
                        + " = "
                        + getFledgeAuctionServerBackgroundJoinKeyFetchEnabled());
        writer.println(
                "\t"
                        + FlagsConstants
                                .KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_CONNECT_TIMEOUT_MS
                        + " = "
                        + getFledgeAuctionServerBackgroundKeyFetchNetworkConnectTimeoutMs());
        writer.println(
                "\t"
                        + FlagsConstants
                                .KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_READ_TIMEOUT_MS
                        + " = "
                        + getFledgeAuctionServerBackgroundKeyFetchNetworkReadTimeoutMs());
        writer.println(
                "\t"
                        + FlagsConstants
                                .KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RESPONSE_SIZE_B
                        + " = "
                        + getFledgeAuctionServerBackgroundKeyFetchMaxResponseSizeB());
        writer.println(
                "\t"
                        + FlagsConstants
                                .KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RUNTIME_MS
                        + " = "
                        + getFledgeAuctionServerBackgroundKeyFetchJobMaxRuntimeMs());
        writer.println(
                "\t"
                        + FlagsConstants
                                .KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_PERIOD_MS
                        + " = "
                        + getFledgeAuctionServerBackgroundKeyFetchJobPeriodMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_FLEX_MS
                        + " = "
                        + getFledgeAuctionServerBackgroundKeyFetchJobFlexMs());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_AD_RENDER_ID_ENABLED
                        + " = "
                        + getFledgeAuctionServerAdRenderIdEnabled());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_AD_RENDER_ID_MAX_LENGTH
                        + " = "
                        + getFledgeAuctionServerAdRenderIdMaxLength());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_COMPRESSION_ALGORITHM_VERSION
                        + " = "
                        + getFledgeAuctionServerCompressionAlgorithmVersion());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_PAYLOAD_FORMAT_VERSION
                        + " = "
                        + getFledgeAuctionServerPayloadFormatVersion());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENABLE_DEBUG_REPORTING
                        + " = "
                        + getFledgeAuctionServerEnableDebugReporting());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AD_SELECTION_BIDDING_LOGIC_JS_VERSION
                        + " = "
                        + getFledgeAdSelectionBiddingLogicJsVersion());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS
                        + " = "
                        + getReportImpressionOverallTimeoutMs());
        writer.println(
                "\t"
                        + FlagsConstants
                                .KEY_FLEDGE_REPORT_IMPRESSION_MAX_REGISTERED_AD_BEACONS_TOTAL_COUNT
                        + " = "
                        + getFledgeReportImpressionMaxRegisteredAdBeaconsTotalCount());
        writer.println(
                "\t"
                        + FlagsConstants
                                .KEY_FLEDGE_REPORT_IMPRESSION_MAX_REGISTERED_AD_BEACONS_PER_AD_TECH_COUNT
                        + " = "
                        + getFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount());
        writer.println(
                "\t"
                        + FlagsConstants
                                .KEY_FLEDGE_REPORT_IMPRESSION_REGISTERED_AD_BEACONS_MAX_INTERACTION_KEY_SIZE_B
                        + " = "
                        + getFledgeReportImpressionRegisteredAdBeaconsMaxInteractionKeySizeB());
        writer.println(
                "\t"
                        + FlagsConstants
                                .KEY_FLEDGE_REPORT_IMPRESSION_MAX_INTERACTION_REPORTING_URI_SIZE_B
                        + " = "
                        + getFledgeReportImpressionMaxInteractionReportingUriSizeB());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDE
                        + " = "
                        + getEnforceForegroundStatusForFledgeOverrides());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION
                        + " = "
                        + getEnforceForegroundStatusForFledgeReportImpression());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_INTERACTION
                        + " = "
                        + getEnforceForegroundStatusForFledgeReportInteraction());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION
                        + " = "
                        + getEnforceForegroundStatusForFledgeRunAdSelection());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE
                        + " = "
                        + getEnforceForegroundStatusForFledgeCustomAudience());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FOREGROUND_STATUS_LEVEL
                        + " = "
                        + getForegroundStatuslLevelForValidation());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AD_SELECTION_OFF_DEVICE_ENABLED
                        + " = "
                        + getAdSelectionOffDeviceEnabled());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_PAYLOAD_BUCKET_SIZES
                        + " = "
                        + getFledgeAuctionServerPayloadBucketSizes());
        writer.println(
                "\t"
                        + FlagsConstants
                                .KEY_FLEDGE_AD_SELECTION_OFF_DEVICE_REQUEST_COMPRESSION_ENABLED
                        + " = "
                        + getAdSelectionOffDeviceRequestCompressionEnabled());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_ENFORCE_ISOLATE_MAX_HEAP_SIZE
                        + " = "
                        + getEnforceIsolateMaxHeapSize());

        writer.println(
                "\t"
                        + FlagsConstants.KEY_ISOLATE_MAX_HEAP_SIZE_BYTES
                        + " = "
                        + getIsolateMaxHeapSizeBytes());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_AD_SELECTION_EXPIRATION_WINDOW_S
                        + " = "
                        + getAdSelectionExpirationWindowS());

        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_ENABLED
                        + " = "
                        + getFledgeEventLevelDebugReportingEnabled());

        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_BATCH_DELAY_SECONDS
                        + " = "
                        + getFledgeEventLevelDebugReportingBatchDelaySeconds());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_MAX_ITEMS_PER_BATCH
                        + " = "
                        + getFledgeEventLevelDebugReportingMaxItemsPerBatch());
        writer.println("==== AdServices PH Flags Throttling Related Flags ====");
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_REPORT_INTERACTION_REQUEST_PERMITS_PER_SECOND
                        + " = "
                        + getFledgeReportInteractionRequestPermitsPerSecond());
        writer.println("==== AdServices PH Flags Error Logging ====");
        writer.println(
                "\t"
                        + FlagsConstants.KEY_ADSERVICES_ERROR_LOGGING_ENABLED
                        + " = "
                        + getAdServicesErrorLoggingEnabled());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_ERROR_CODE_LOGGING_DENY_LIST
                        + " = "
                        + getErrorCodeLoggingDenyList());

        writer.println("==== AdServices PH Flags Dump UI Related Flags ====");
        writer.println(
                "\t"
                        + FlagsConstants.KEY_EU_NOTIF_FLOW_CHANGE_ENABLED
                        + " = "
                        + getEuNotifFlowChangeEnabled());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_UI_FEATURE_TYPE_LOGGING_ENABLED
                        + " = "
                        + isUiFeatureTypeLoggingEnabled());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_UI_DIALOGS_FEATURE_ENABLED
                        + " = "
                        + getUIDialogsFeatureEnabled());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_IS_EEA_DEVICE_FEATURE_ENABLED
                        + " = "
                        + isEeaDeviceFeatureEnabled());
        writer.println("\t" + FlagsConstants.KEY_IS_EEA_DEVICE + " = " + isEeaDevice());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_IS_BACK_COMPACT_ACTIVITY_FEATURE_ENABLED
                        + " = "
                        + isBackCompatActivityFeatureEnabled());
        writer.println("\t" + FlagsConstants.KEY_UI_EEA_COUNTRIES + " = " + getUiEeaCountries());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_NOTIFICATION_DISMISSED_ON_CLICK
                        + " = "
                        + getNotificationDismissedOnClick());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_UI_OTA_STRINGS_FEATURE_ENABLED
                        + " = "
                        + getUiOtaStringsFeatureEnabled());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_UI_OTA_STRINGS_DOWNLOAD_DEADLINE
                        + " = "
                        + getUiOtaStringsDownloadDeadline());
        writer.println("==== AdServices New Feature Flags ====");
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED
                        + " = "
                        + getFledgeRegisterAdBeaconEnabled());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FLEDGE_CPC_BILLING_ENABLED
                        + " = "
                        + getFledgeCpcBillingEnabled());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_TOPICS_COBALT_LOGGING_ENABLED
                        + " = "
                        + getTopicsCobaltLoggingEnabled());
        writer.println("==== AdServices PH Flags Dump STATUS ====");
        writer.println(
                "\t" + FlagsConstants.KEY_ADSERVICES_ENABLED + " = " + getAdServicesEnabled());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_FOREGROUND_STATUS_LEVEL
                        + " = "
                        + getForegroundStatuslLevelForValidation());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_ADSERVICES_RELEASE_STAGE_FOR_COBALT
                        + " = "
                        + getAdservicesReleaseStageForCobalt());
        writer.println("==== AdServices Consent Dump STATUS ====");
        writer.println(
                "\t"
                        + FlagsConstants.KEY_CONSENT_SOURCE_OF_TRUTH
                        + " = "
                        + getConsentSourceOfTruth());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_BLOCKED_TOPICS_SOURCE_OF_TRUTH
                        + " = "
                        + getBlockedTopicsSourceOfTruth());
        writer.println("==== Back-Compat PH Flags Dump STATUS ====");
        writer.println(
                "\t"
                        + FlagsConstants.KEY_COMPAT_LOGGING_KILL_SWITCH
                        + " = "
                        + getCompatLoggingKillSwitch());
        writer.println("==== Enable Back-Compat PH Flags Dump STATUS ====");
        writer.println(
                "\t" + FlagsConstants.KEY_ENABLE_BACK_COMPAT + " = " + getEnableBackCompat());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_ENABLE_APPSEARCH_CONSENT_DATA
                        + " = "
                        + getEnableAppsearchConsentData());
        writer.println(
                "\t"
                        + FlagsConstants.ADSERVICES_CONSENT_MIGRATION_LOGGING_ENABLED
                        + " = "
                        + getAdservicesConsentMigrationLoggingEnabled());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH
                        + " = "
                        + getMeasurementRollbackDeletionAppSearchKillSwitch());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_ENABLE_MAX_AGGREGATE_REPORTS_PER_SOURCE
                        + " = "
                        + getMeasurementEnableMaxAggregateReportsPerSource());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_SOURCE
                        + " = "
                        + getMeasurementMaxAggregateReportsPerSource());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_MAX_AGGREGATE_KEYS_PER_SOURCE_REGISTRATION
                        + " = "
                        + getMeasurementMaxAggregateKeysPerSourceRegistration());
        writer.println(
                "\t"
                        + FlagsConstants.KEY_MEASUREMENT_MAX_AGGREGATE_KEYS_PER_TRIGGER_REGISTRATION
                        + " = "
                        + getMeasurementMaxAggregateKeysPerTriggerRegistration());
        writer.println(
                "\t"
                        + FlagsConstants
                                .KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED
                        + " = "
                        + getFledgeMeasurementReportAndRegisterEventApiEnabled());
    }

    @VisibleForTesting
    @Override
    public ImmutableList<String> getEnrollmentBlocklist() {
        String blocklistFlag =
                DeviceConfig.getString(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        FlagsConstants.KEY_ENROLLMENT_BLOCKLIST_IDS,
                        "");
        if (TextUtils.isEmpty(blocklistFlag)) {
            return ImmutableList.of();
        }
        String[] blocklistList = blocklistFlag.split(FlagsConstants.ARRAY_SPLITTER_COMMA);
        return ImmutableList.copyOf(blocklistList);
    }

    @Override
    public boolean getCompatLoggingKillSwitch() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_COMPAT_LOGGING_KILL_SWITCH,
                /* defaultValue */ COMPAT_LOGGING_KILL_SWITCH);
    }

    @Override
    public boolean getBackgroundJobsLoggingKillSwitch() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_BACKGROUND_JOBS_LOGGING_KILL_SWITCH,
                /* defaultValue */ BACKGROUND_JOBS_LOGGING_KILL_SWITCH);
    }

    @Override
    public boolean getEnableBackCompat() {
        // If SDK is T+, the value should always be false
        // Check the flag value for S Minus
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return !SdkLevel.isAtLeastT()
                && DeviceConfig.getBoolean(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* flagName */ FlagsConstants.KEY_ENABLE_BACK_COMPAT,
                        /* defaultValue */ ENABLE_BACK_COMPAT);
    }

    @Override
    public boolean getEnableAppsearchConsentData() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_ENABLE_APPSEARCH_CONSENT_DATA,
                /* defaultValue */ ENABLE_APPSEARCH_CONSENT_DATA);
    }

    @Override
    public ImmutableList<Integer> getGlobalBlockedTopicIds() {
        String defaultGlobalBlockedTopicIds =
                TOPICS_GLOBAL_BLOCKED_TOPIC_IDS.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(FlagsConstants.ARRAY_SPLITTER_COMMA));

        String globalBlockedTopicIds =
                DeviceConfig.getString(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        FlagsConstants.KEY_GLOBAL_BLOCKED_TOPIC_IDS,
                        defaultGlobalBlockedTopicIds);
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
                DeviceConfig.getString(
                        FlagsConstants.NAMESPACE_ADSERVICES,
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
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_DEBUG_JOIN_KEY_HASH_LIMIT,
                /* defaultValue */ DEFAULT_MEASUREMENT_DEBUG_JOIN_KEY_HASH_LIMIT);
    }

    @Override
    public long getMeasurementPlatformDebugAdIdMatchingLimit() {
        return DeviceConfig.getLong(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_DEBUG_KEY_AD_ID_MATCHING_LIMIT,
                /* defaultValue */ DEFAULT_MEASUREMENT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT);
    }

    @Override
    public boolean getEuNotifFlowChangeEnabled() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_EU_NOTIF_FLOW_CHANGE_ENABLED,
                /* defaultValue */ DEFAULT_EU_NOTIF_FLOW_CHANGE_ENABLED);
    }

    @Override
    public boolean getNotificationDismissedOnClick() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_NOTIFICATION_DISMISSED_ON_CLICK,
                /* defaultValue */ DEFAULT_NOTIFICATION_DISMISSED_ON_CLICK);
    }

    @Override
    public boolean getU18UxEnabled() {
        return getEnableAdServicesSystemApi()
                && DeviceConfig.getBoolean(
                        FlagsConstants.NAMESPACE_ADSERVICES,
                        /* flagName */ FlagsConstants.KEY_U18_UX_ENABLED,
                        /* defaultValue */ DEFAULT_U18_UX_ENABLED);
    }

    @Override
    public boolean getEnableAdServicesSystemApi() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_ENABLE_AD_SERVICES_SYSTEM_API,
                /* defaultValue */ DEFAULT_ENABLE_AD_SERVICES_SYSTEM_API);
    }

    @Override
    public Map<String, Boolean> getUxFlags() {
        Map<String, Boolean> uxMap = new HashMap<>();
        uxMap.put(FlagsConstants.KEY_UI_DIALOGS_FEATURE_ENABLED, getUIDialogsFeatureEnabled());
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
                FlagsConstants.KEY_UI_FEATURE_TYPE_LOGGING_ENABLED,
                isUiFeatureTypeLoggingEnabled());
        uxMap.put(FlagsConstants.KEY_ADSERVICES_ENABLED, getAdServicesEnabled());
        uxMap.put(
                FlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE,
                getConsentNotificationDebugMode());
        uxMap.put(
                FlagsConstants.KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE,
                getConsentNotificationActivityDebugMode());
        uxMap.put(FlagsConstants.KEY_EU_NOTIF_FLOW_CHANGE_ENABLED, getEuNotifFlowChangeEnabled());
        uxMap.put(FlagsConstants.KEY_U18_UX_ENABLED, getU18UxEnabled());
        uxMap.put(
                FlagsConstants.KEY_NOTIFICATION_DISMISSED_ON_CLICK,
                getNotificationDismissedOnClick());
        return uxMap;
    }

    @Override
    public boolean getMeasurementEnableCoarseEventReportDestinations() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_ENABLE_COARSE_EVENT_REPORT_DESTINATIONS,
                /* defaultValue */ DEFAULT_MEASUREMENT_ENABLE_COARSE_EVENT_REPORT_DESTINATIONS);
    }

    @Override
    public boolean getMeasurementEnableVtcConfigurableMaxEventReports() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_ENABLE_VTC_CONFIGURABLE_MAX_EVENT_REPORTS,
                /* defaultValue */
                DEFAULT_MEASUREMENT_ENABLE_VTC_CONFIGURABLE_MAX_EVENT_REPORTS);
    }

    @Override
    public int getMeasurementVtcConfigurableMaxEventReportsCount() {
        return DeviceConfig.getInt(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants
                        .KEY_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT,
                /* defaultValue */ DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT);
    }

    @Override
    public boolean getMeasurementEnableAraParsingAlignmentV1() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_ENABLE_ARA_PARSING_ALIGNMENT_V1,
                /* defaultValue */ MEASUREMENT_ENABLE_ARA_PARSING_ALIGNMENT_V1);
    }

    @Override
    public boolean getMeasurementEnableAraDeduplicationAlignmentV1() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.KEY_MEASUREMENT_ENABLE_ARA_DEDUPLICATION_ALIGNMENT_V1,
                /* defaultValue */ MEASUREMENT_ENABLE_ARA_DEDUPLICATION_ALIGNMENT_V1);
    }

    @Override
    public boolean getAdservicesConsentMigrationLoggingEnabled() {
        return DeviceConfig.getBoolean(
                FlagsConstants.NAMESPACE_ADSERVICES,
                /* flagName */ FlagsConstants.ADSERVICES_CONSENT_MIGRATION_LOGGING_ENABLED,
                /* defaultValue */ DEFAULT_ADSERVICES_CONSENT_MIGRATION_LOGGING_ENABLED);
    }
}
