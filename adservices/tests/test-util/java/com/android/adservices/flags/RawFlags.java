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
package com.android.adservices.flags;

// Need to disable checkstyle as there's no need to import 500+ constants.
// CHECKSTYLE:OFF Generated code
import static com.android.adservices.service.FlagsConstants.*;
// CHECKSTYLE:ON

import android.text.TextUtils;

import com.android.adservices.LogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.PhFlags;
import com.android.adservices.shared.common.flags.Constants;
import com.android.adservices.shared.flags.FlagsBackend;

import com.google.common.collect.ImmutableList;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

// TODO(b/391406689): this class is should be moved to service-core, but currently it's only used by
// tests
/**
 * Implementation of {@link Flags} that simply returns the "raw" value of flags from a backend,
 * without any additional logic (like range validation or feature-flag dependency).
 *
 * <p>Ideally this class should be final and {@link PhFlags} should have a reference to it, but that
 * would require adding the same methods in 2 places, so {@link PhFlags} extends it instead,
 * implementing the methods that require validating.
 *
 * <p>Also, initially it was implementing all methods which the methods requiring validation not
 * being final, but that approach increased the memory utilization of the process, as there are
 * hundreds of such methods and each would require duplicated code (for this class and the
 * subclass). Hence it now provides only the {@code final} methods (and the subclasses are
 * responsible to implement the ones that require validation directly).
 *
 * @param <FB> flags backend type
 */
abstract class RawFlags<FB extends FlagsBackend> implements Flags {

    protected final FB mBackend;

    public RawFlags(FB backend) {
        mBackend = Objects.requireNonNull(backend, "backend cannot be null");
    }

    @Override
    public final long getAsyncRegistrationJobQueueIntervalMs() {
        return mBackend.getFlag(
                KEY_ASYNC_REGISTRATION_JOB_QUEUE_INTERVAL_MS,
                ASYNC_REGISTRATION_JOB_QUEUE_INTERVAL_MS);
    }

    @Override
    public final boolean getTopicsDisableDirectAppCalls() {
        return mBackend.getFlag(
                KEY_TOPICS_DISABLE_DIRECT_APP_CALLS, TOPICS_DISABLE_DIRECT_APP_CALLS);
    }

    @Override
    public final boolean getTopicsEncryptionEnabled() {
        return mBackend.getFlag(KEY_TOPICS_ENCRYPTION_ENABLED, TOPICS_ENCRYPTION_ENABLED);
    }

    @Override
    public final boolean getTopicsEncryptionMetricsEnabled() {
        return mBackend.getFlag(
                KEY_TOPICS_ENCRYPTION_METRICS_ENABLED, TOPICS_ENCRYPTION_METRICS_ENABLED);
    }

    @Override
    public final boolean getTopicsEpochJobBatteryConstraintLoggingEnabled() {
        return mBackend.getFlag(
                KEY_TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_LOGGING_ENABLED,
                TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_LOGGING_ENABLED);
    }

    @Override
    public final boolean getTopicsDisablePlaintextResponse() {
        return mBackend.getFlag(
                KEY_TOPICS_DISABLE_PLAINTEXT_RESPONSE, TOPICS_DISABLE_PLAINTEXT_RESPONSE);
    }

    @Override
    public final String getTopicsTestEncryptionPublicKey() {
        return mBackend.getFlag(
                KEY_TOPICS_TEST_ENCRYPTION_PUBLIC_KEY, TOPICS_TEST_ENCRYPTION_PUBLIC_KEY);
    }

    @Override
    public final float getClassifierThreshold() {
        return mBackend.getFlag(KEY_CLASSIFIER_THRESHOLD, CLASSIFIER_THRESHOLD);
    }

    @Override
    public final int getClassifierDescriptionMaxWords() {
        return mBackend.getFlag(
                KEY_CLASSIFIER_DESCRIPTION_MAX_WORDS, CLASSIFIER_DESCRIPTION_MAX_WORDS);
    }

    @Override
    public final int getClassifierDescriptionMaxLength() {
        return mBackend.getFlag(
                KEY_CLASSIFIER_DESCRIPTION_MAX_LENGTH, CLASSIFIER_DESCRIPTION_MAX_LENGTH);
    }

    @Override
    public final boolean getClassifierForceUseBundledFiles() {
        return mBackend.getFlag(
                KEY_CLASSIFIER_FORCE_USE_BUNDLED_FILES, CLASSIFIER_FORCE_USE_BUNDLED_FILES);
    }

    @Override
    public final boolean getTopicsJobSchedulerRescheduleEnabled() {
        return mBackend.getFlag(
                KEY_TOPICS_JOB_SCHEDULER_RESCHEDULE_ENABLED,
                TOPICS_JOB_SCHEDULER_RESCHEDULE_ENABLED);
    }

    @Override
    public final boolean getTopicsEpochJobBatteryNotLowInsteadOfCharging() {
        return mBackend.getFlag(
                KEY_TOPICS_EPOCH_JOB_BATTERY_NOT_LOW_INSTEAD_OF_CHARGING,
                TOPICS_EPOCH_JOB_BATTERY_NOT_LOW_INSTEAD_OF_CHARGING);
    }

    @Override
    public final boolean getTopicsCleanDBWhenEpochJobSettingsChanged() {
        return mBackend.getFlag(
                KEY_TOPICS_CLEAN_DB_WHEN_EPOCH_JOB_SETTINGS_CHANGED,
                TOPICS_CLEAN_DB_WHEN_EPOCH_JOB_SETTINGS_CHANGED);
    }

    @Override
    public final int getAppNameApiErrorCobaltLoggingSamplingRate() {
        return mBackend.getFlag(
                KEY_APP_NAME_API_ERROR_COBALT_LOGGING_SAMPLING_RATE,
                APP_NAME_API_ERROR_COBALT_LOGGING_SAMPLING_RATE);
    }

    @Override
    public final String getCobaltAdservicesApiKeyHex() {
        return mBackend.getFlag(KEY_COBALT_ADSERVICES_API_KEY_HEX, COBALT_ADSERVICES_API_KEY_HEX);
    }

    @Override
    public final String getAdservicesReleaseStageForCobalt() {
        return mBackend.getFlag(
                KEY_ADSERVICES_RELEASE_STAGE_FOR_COBALT, ADSERVICES_RELEASE_STAGE_FOR_COBALT);
    }

    @Override
    public final boolean getCobaltRegistryOutOfBandUpdateEnabled() {
        return mBackend.getFlag(
                KEY_COBALT_REGISTRY_OUT_OF_BAND_UPDATE_ENABLED,
                COBALT_REGISTRY_OUT_OF_BAND_UPDATE_ENABLED);
    }

    @Override
    public final String getMddCobaltRegistryManifestFileUrl() {
        return mBackend.getFlag(
                KEY_MDD_COBALT_REGISTRY_MANIFEST_FILE_URL, MDD_COBALT_REGISTRY_MANIFEST_FILE_URL);
    }

    @Override
    public final boolean getCobaltOperationalLoggingEnabled() {
        return mBackend.getFlag(
                KEY_COBALT_OPERATIONAL_LOGGING_ENABLED, COBALT_OPERATIONAL_LOGGING_ENABLED);
    }

    @Override
    public final boolean getCobaltFallBackToDefaultBaseRegistry() {
        return mBackend.getFlag(
                KEY_COBALT__FALL_BACK_TO_DEFAULT_BASE_REGISTRY,
                COBALT__FALL_BACK_TO_DEFAULT_BASE_REGISTRY);
    }

    @Override
    public final String getCobaltIgnoredReportIdList() {
        return mBackend.getFlag(KEY_COBALT__IGNORED_REPORT_ID_LIST, COBALT__IGNORED_REPORT_ID_LIST);
    }

    @Override
    public final boolean getCobaltEnableApiCallResponseLogging() {
        return mBackend.getFlag(
                KEY_COBALT__ENABLE_API_CALL_RESPONSE_LOGGING,
                COBALT__FALL_BACK_TO_DEFAULT_BASE_REGISTRY);
    }

    @Override
    public final int getEncryptionKeyNetworkConnectTimeoutMs() {
        return mBackend.getFlag(
                KEY_ENCRYPTION_KEY_NETWORK_CONNECT_TIMEOUT_MS,
                ENCRYPTION_KEY_NETWORK_CONNECT_TIMEOUT_MS);
    }

    @Override
    public final int getEncryptionKeyNetworkReadTimeoutMs() {
        return mBackend.getFlag(
                KEY_ENCRYPTION_KEY_NETWORK_READ_TIMEOUT_MS, ENCRYPTION_KEY_NETWORK_READ_TIMEOUT_MS);
    }

    @Override
    public final long getMeasurementEventMainReportingJobPeriodMs() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS,
                MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS);
    }

    @Override
    public final long getMeasurementEventFallbackReportingJobPeriodMs() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS,
                MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS);
    }

    @Override
    public final boolean getMeasurementAggregationCoordinatorOriginEnabled() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_ENABLED,
                MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_ENABLED);
    }

    @Override
    public final String getMeasurementAggregationCoordinatorOriginList() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_LIST,
                MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_LIST);
    }

    @Override
    public final String getMeasurementDefaultAggregationCoordinatorOrigin() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_DEFAULT_AGGREGATION_COORDINATOR_ORIGIN,
                MEASUREMENT_DEFAULT_AGGREGATION_COORDINATOR_ORIGIN);
    }

    @Override
    public final String getMeasurementAggregationCoordinatorPath() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_AGGREGATION_COORDINATOR_PATH,
                MEASUREMENT_AGGREGATION_COORDINATOR_PATH);
    }

    @Override
    public final long getMeasurementAggregateMainReportingJobPeriodMs() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS,
                MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS);
    }

    @Override
    public final long getMeasurementAggregateFallbackReportingJobPeriodMs() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS,
                MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS);
    }

    @Override
    public final int getMeasurementNetworkConnectTimeoutMs() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_NETWORK_CONNECT_TIMEOUT_MS, MEASUREMENT_NETWORK_CONNECT_TIMEOUT_MS);
    }

    @Override
    public final int getMeasurementNetworkReadTimeoutMs() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_NETWORK_READ_TIMEOUT_MS, MEASUREMENT_NETWORK_READ_TIMEOUT_MS);
    }

    @Override
    public final long getMeasurementDbSizeLimit() {
        return mBackend.getFlag(KEY_MEASUREMENT_DB_SIZE_LIMIT, MEASUREMENT_DB_SIZE_LIMIT);
    }

    @Override
    public final boolean getMeasurementReportingRetryLimitEnabled() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_REPORT_RETRY_LIMIT_ENABLED, MEASUREMENT_REPORT_RETRY_LIMIT_ENABLED);
    }

    @Override
    public final int getMeasurementReportingRetryLimit() {
        return mBackend.getFlag(KEY_MEASUREMENT_REPORT_RETRY_LIMIT, MEASUREMENT_REPORT_RETRY_LIMIT);
    }

    @Override
    public final String getMeasurementManifestFileUrl() {
        return mBackend.getFlag(KEY_MEASUREMENT_MANIFEST_FILE_URL, MEASUREMENT_MANIFEST_FILE_URL);
    }

    @Override
    public final long getMeasurementRegistrationInputEventValidWindowMs() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_REGISTRATION_INPUT_EVENT_VALID_WINDOW_MS,
                MEASUREMENT_REGISTRATION_INPUT_EVENT_VALID_WINDOW_MS);
    }

    @Override
    public final boolean getMeasurementIsClickVerificationEnabled() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_IS_CLICK_VERIFICATION_ENABLED,
                MEASUREMENT_IS_CLICK_VERIFICATION_ENABLED);
    }

    @Override
    public final boolean getMeasurementIsClickVerifiedByInputEvent() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_IS_CLICK_VERIFIED_BY_INPUT_EVENT,
                MEASUREMENT_IS_CLICK_VERIFIED_BY_INPUT_EVENT);
    }

    @Override
    public final boolean getMeasurementIsClickDeduplicationEnabled() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_IS_CLICK_DEDUPLICATION_ENABLED,
                MEASUREMENT_IS_CLICK_DEDUPLICATION_ENABLED);
    }

    @Override
    public final boolean getMeasurementIsClickDeduplicationEnforced() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_IS_CLICK_DEDUPLICATION_ENFORCED,
                MEASUREMENT_IS_CLICK_DEDUPLICATION_ENFORCED);
    }

    @Override
    public final long getMeasurementMaxSourcesPerClick() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_SOURCES_PER_CLICK, MEASUREMENT_MAX_SOURCES_PER_CLICK);
    }

    @Override
    public final boolean getMeasurementEnableXNA() {
        return mBackend.getFlag(KEY_MEASUREMENT_ENABLE_XNA, MEASUREMENT_ENABLE_XNA);
    }

    @Override
    public final boolean getMeasurementEnableSharedSourceDebugKey() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_SHARED_SOURCE_DEBUG_KEY,
                MEASUREMENT_ENABLE_SHARED_SOURCE_DEBUG_KEY);
    }

    @Override
    public final boolean getMeasurementEnableSharedFilterDataKeysXNA() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_SHARED_FILTER_DATA_KEYS_XNA,
                MEASUREMENT_ENABLE_SHARED_FILTER_DATA_KEYS_XNA);
    }

    @Override
    public final boolean getMeasurementEnableDebugReport() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_DEBUG_REPORT, MEASUREMENT_ENABLE_DEBUG_REPORT);
    }

    @Override
    public final boolean getMeasurementEnableSourceDebugReport() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_SOURCE_DEBUG_REPORT, MEASUREMENT_ENABLE_SOURCE_DEBUG_REPORT);
    }

    @Override
    public final boolean getMeasurementEnableTriggerDebugReport() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_TRIGGER_DEBUG_REPORT,
                MEASUREMENT_ENABLE_TRIGGER_DEBUG_REPORT);
    }

    @Override
    public final boolean getMeasurementEnableHeaderErrorDebugReport() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_HEADER_ERROR_DEBUG_REPORT,
                MEASUREMENT_ENABLE_HEADER_ERROR_DEBUG_REPORT);
    }

    @Override
    public final long getMeasurementDataExpiryWindowMs() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_DATA_EXPIRY_WINDOW_MS, MEASUREMENT_DATA_EXPIRY_WINDOW_MS);
    }

    @Override
    public final int getMeasurementMaxRegistrationRedirects() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_REGISTRATION_REDIRECTS, MEASUREMENT_MAX_REGISTRATION_REDIRECTS);
    }

    @Override
    public final int getMeasurementMaxRegistrationsPerJobInvocation() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_REGISTRATIONS_PER_JOB_INVOCATION,
                MEASUREMENT_MAX_REGISTRATIONS_PER_JOB_INVOCATION);
    }

    @Override
    public final int getMeasurementMaxRetriesPerRegistrationRequest() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_RETRIES_PER_REGISTRATION_REQUEST,
                MEASUREMENT_MAX_RETRIES_PER_REGISTRATION_REQUEST);
    }

    @Override
    public final long getMeasurementAsyncRegistrationJobTriggerMinDelayMs() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ASYNC_REGISTRATION_JOB_TRIGGER_MIN_DELAY_MS,
                DEFAULT_MEASUREMENT_ASYNC_REGISTRATION_JOB_TRIGGER_MIN_DELAY_MS);
    }

    @Override
    public final long getMeasurementAsyncRegistrationJobTriggerMaxDelayMs() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ASYNC_REGISTRATION_JOB_TRIGGER_MAX_DELAY_MS,
                DEFAULT_MEASUREMENT_ASYNC_REGISTRATION_JOB_TRIGGER_MAX_DELAY_MS);
    }

    @Override
    public final int getMeasurementMaxBytesPerAttributionFilterString() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_BYTES_PER_ATTRIBUTION_FILTER_STRING,
                DEFAULT_MEASUREMENT_MAX_BYTES_PER_ATTRIBUTION_FILTER_STRING);
    }

    @Override
    public final int getMeasurementMaxFilterMapsPerFilterSet() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET,
                DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET);
    }

    @Override
    public final int getMeasurementMaxValuesPerAttributionFilter() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER,
                DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER);
    }

    @Override
    public final int getMeasurementMaxAttributionFilters() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_ATTRIBUTION_FILTERS,
                DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS);
    }

    @Override
    public final int getMeasurementMaxBytesPerAttributionAggregateKeyId() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_BYTES_PER_ATTRIBUTION_AGGREGATE_KEY_ID,
                DEFAULT_MEASUREMENT_MAX_BYTES_PER_ATTRIBUTION_AGGREGATE_KEY_ID);
    }

    @Override
    public final int getMeasurementMaxAggregateDeduplicationKeysPerRegistration() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_AGGREGATE_DEDUPLICATION_KEYS_PER_REGISTRATION,
                DEFAULT_MEASUREMENT_MAX_AGGREGATE_DEDUPLICATION_KEYS_PER_REGISTRATION);
    }

    @Override
    public final long getMeasurementAttributionJobTriggerDelayMs() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ATTRIBUTION_JOB_TRIGGERING_DELAY_MS,
                DEFAULT_MEASUREMENT_ATTRIBUTION_JOB_TRIGGERING_DELAY_MS);
    }

    @Override
    public final int getMeasurementMaxAttributionsPerInvocation() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_ATTRIBUTIONS_PER_INVOCATION,
                DEFAULT_MEASUREMENT_MAX_ATTRIBUTIONS_PER_INVOCATION);
    }

    @Override
    public final long getMeasurementMaxEventReportUploadRetryWindowMs() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_EVENT_REPORT_UPLOAD_RETRY_WINDOW_MS,
                DEFAULT_MEASUREMENT_MAX_EVENT_REPORT_UPLOAD_RETRY_WINDOW_MS);
    }

    @Override
    public final long getMeasurementMaxAggregateReportUploadRetryWindowMs() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS,
                DEFAULT_MEASUREMENT_MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS);
    }

    @Override
    public final long getMeasurementMaxDelayedSourceRegistrationWindow() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_DELAYED_SOURCE_REGISTRATION_WINDOW,
                DEFAULT_MEASUREMENT_MAX_DELAYED_SOURCE_REGISTRATION_WINDOW);
    }

    @Override
    public final long getMeasurementAttributionFallbackJobPeriodMs() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_PERIOD_MS,
                MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_PERIOD_MS);
    }

    @Override
    public final int getMeasurementMaxEventAttributionPerRateLimitWindow() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_EVENT_ATTRIBUTION_PER_RATE_LIMIT_WINDOW,
                MEASUREMENT_MAX_EVENT_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);
    }

    @Override
    public final int getMeasurementMaxAggregateAttributionPerRateLimitWindow() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_AGGREGATE_ATTRIBUTION_PER_RATE_LIMIT_WINDOW,
                MEASUREMENT_MAX_AGGREGATE_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);
    }

    @Override
    public final int getMeasurementMaxDistinctReportingOriginsInAttribution() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_DISTINCT_REPORTING_ORIGINS_IN_ATTRIBUTION,
                MEASUREMENT_MAX_DISTINCT_REPORTING_ORIGINS_IN_ATTRIBUTION);
    }

    @Override
    public final int getMeasurementMaxDistinctDestinationsInActiveSource() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_DISTINCT_DESTINATIONS_IN_ACTIVE_SOURCE,
                MEASUREMENT_MAX_DISTINCT_DESTINATIONS_IN_ACTIVE_SOURCE);
    }

    @Override
    public final int getMeasurementMaxReportingOriginsPerSourceReportingSitePerWindow() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_REPORTING_ORIGINS_PER_SOURCE_REPORTING_SITE_PER_WINDOW,
                MEASUREMENT_MAX_REPORTING_ORIGINS_PER_SOURCE_REPORTING_SITE_PER_WINDOW);
    }

    @Override
    public final int getMeasurementMaxDistinctRepOrigPerPublXDestInSource() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_DISTINCT_REPORTING_ORIGINS_IN_SOURCE,
                MEASUREMENT_MAX_DISTINCT_REP_ORIG_PER_PUBLISHER_X_DEST_IN_SOURCE);
    }

    @Override
    public final boolean getMeasurementEnableDestinationRateLimit() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_DESTINATION_RATE_LIMIT,
                MEASUREMENT_ENABLE_DESTINATION_RATE_LIMIT);
    }

    @Override
    public final int getMeasurementMaxDestinationsPerPublisherPerRateLimitWindow() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_DESTINATIONS_PER_PUBLISHER_PER_RATE_LIMIT_WINDOW,
                MEASUREMENT_MAX_DESTINATIONS_PER_PUBLISHER_PER_RATE_LIMIT_WINDOW);
    }

    @Override
    public final int getMeasurementMaxDestPerPublisherXEnrollmentPerRateLimitWindow() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_DEST_PER_PUBLISHER_X_ENROLLMENT_PER_RATE_LIMIT_WINDOW,
                MEASUREMENT_MAX_DEST_PER_PUBLISHER_X_ENROLLMENT_PER_RATE_LIMIT_WINDOW);
    }

    @Override
    public final long getMeasurementDestinationRateLimitWindow() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_DESTINATION_RATE_LIMIT_WINDOW,
                MEASUREMENT_DESTINATION_RATE_LIMIT_WINDOW);
    }

    @Override
    public final boolean getMeasurementEnableDestinationPerDayRateLimitWindow() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW,
                MEASUREMENT_ENABLE_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW);
    }

    @Override
    public final int getMeasurementDestinationPerDayRateLimit() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT,
                MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT);
    }

    @Override
    public final long getMeasurementDestinationPerDayRateLimitWindowInMs() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW_IN_MS,
                MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW_IN_MS);
    }

    @Override
    public final boolean getFledgeAppPackageNameLoggingEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_APP_PACKAGE_NAME_LOGGING_ENABLED,
                FLEDGE_APP_PACKAGE_NAME_LOGGING_ENABLED);
    }

    @Override
    public final long getFledgeCustomAudienceMaxCount() {
        return mBackend.getFlag(
                KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT, FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT);
    }

    @Override
    public final long getFledgeCustomAudiencePerAppMaxCount() {
        return mBackend.getFlag(
                KEY_FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT,
                FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT);
    }

    @Override
    public final long getFledgeCustomAudienceMaxOwnerCount() {
        return mBackend.getFlag(
                KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT, FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT);
    }

    @Override
    public final long getFledgeCustomAudiencePerBuyerMaxCount() {
        return mBackend.getFlag(
                KEY_FLEDGE_CUSTOM_AUDIENCE_PER_BUYER_MAX_COUNT,
                FLEDGE_CUSTOM_AUDIENCE_PER_BUYER_MAX_COUNT);
    }

    @Override
    public final long getFledgeCustomAudienceDefaultExpireInMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN_MS,
                FLEDGE_CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN_MS);
    }

    @Override
    public final long getFledgeCustomAudienceMaxActivationDelayInMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN_MS,
                FLEDGE_CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN_MS);
    }

    @Override
    public final long getFledgeCustomAudienceMaxExpireInMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_EXPIRE_IN_MS,
                FLEDGE_CUSTOM_AUDIENCE_MAX_EXPIRE_IN_MS);
    }

    @Override
    public final int getFledgeCustomAudienceMaxNameSizeB() {
        return mBackend.getFlag(
                KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NAME_SIZE_B, FLEDGE_CUSTOM_AUDIENCE_MAX_NAME_SIZE_B);
    }

    @Override
    public final int getFledgeCustomAudienceMaxDailyUpdateUriSizeB() {
        return mBackend.getFlag(
                KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_DAILY_UPDATE_URI_SIZE_B,
                FLEDGE_CUSTOM_AUDIENCE_MAX_DAILY_UPDATE_URI_SIZE_B);
    }

    @Override
    public final int getFledgeCustomAudienceMaxBiddingLogicUriSizeB() {
        return mBackend.getFlag(
                KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_BIDDING_LOGIC_URI_SIZE_B,
                FLEDGE_CUSTOM_AUDIENCE_MAX_BIDDING_LOGIC_URI_SIZE_B);
    }

    @Override
    public final int getFledgeCustomAudienceMaxUserBiddingSignalsSizeB() {
        return mBackend.getFlag(
                KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B,
                FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B);
    }

    @Override
    public final int getFledgeCustomAudienceMaxTrustedBiddingDataSizeB() {
        return mBackend.getFlag(
                KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B,
                FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B);
    }

    @Override
    public final int getFledgeCustomAudienceMaxAdsSizeB() {
        return mBackend.getFlag(
                KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ADS_SIZE_B, FLEDGE_CUSTOM_AUDIENCE_MAX_ADS_SIZE_B);
    }

    @Override
    public final int getFledgeCustomAudienceMaxNumAds() {
        return mBackend.getFlag(
                KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS, FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS);
    }

    @Override
    public final long getFledgeCustomAudienceActiveTimeWindowInMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS,
                FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS);
    }

    @Override
    public final int getFledgeFetchCustomAudienceMaxUserBiddingSignalsSizeB() {
        return mBackend.getFlag(
                KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B,
                FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B);
    }

    @Override
    public final int getFledgeFetchCustomAudienceMaxRequestCustomHeaderSizeB() {
        return mBackend.getFlag(
                KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_REQUEST_CUSTOM_HEADER_SIZE_B,
                FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_REQUEST_CUSTOM_HEADER_SIZE_B);
    }

    @Override
    public final int getFledgeFetchCustomAudienceMaxCustomAudienceSizeB() {
        return mBackend.getFlag(
                KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_CUSTOM_AUDIENCE_SIZE_B,
                FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_CUSTOM_AUDIENCE_SIZE_B);
    }

    @Override
    public final long getFledgeFetchCustomAudienceMinRetryAfterValueMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_MIN_RETRY_AFTER_VALUE_MS,
                FLEDGE_FETCH_CUSTOM_AUDIENCE_MIN_RETRY_AFTER_VALUE_MS);
    }

    @Override
    public final long getFledgeFetchCustomAudienceMaxRetryAfterValueMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_RETRY_AFTER_VALUE_MS,
                FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_RETRY_AFTER_VALUE_MS);
    }

    @Override
    public final boolean getFledgeBackgroundFetchEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_BACKGROUND_FETCH_ENABLED, FLEDGE_BACKGROUND_FETCH_ENABLED);
    }

    @Override
    public final long getFledgeBackgroundFetchJobPeriodMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS, FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS);
    }

    @Override
    public final long getFledgeBackgroundFetchJobFlexMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS, FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS);
    }

    @Override
    public final long getFledgeBackgroundFetchJobMaxRuntimeMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_BACKGROUND_FETCH_JOB_MAX_RUNTIME_MS,
                FLEDGE_BACKGROUND_FETCH_JOB_MAX_RUNTIME_MS);
    }

    @Override
    public final long getFledgeBackgroundFetchMaxNumUpdated() {
        return mBackend.getFlag(
                KEY_FLEDGE_BACKGROUND_FETCH_MAX_NUM_UPDATED,
                FLEDGE_BACKGROUND_FETCH_MAX_NUM_UPDATED);
    }

    @Override
    public final int getFledgeBackgroundFetchThreadPoolSize() {
        return mBackend.getFlag(
                KEY_FLEDGE_BACKGROUND_FETCH_THREAD_POOL_SIZE,
                FLEDGE_BACKGROUND_FETCH_THREAD_POOL_SIZE);
    }

    @Override
    public final long getFledgeBackgroundFetchEligibleUpdateBaseIntervalS() {
        return mBackend.getFlag(
                KEY_FLEDGE_BACKGROUND_FETCH_ELIGIBLE_UPDATE_BASE_INTERVAL_S,
                FLEDGE_BACKGROUND_FETCH_ELIGIBLE_UPDATE_BASE_INTERVAL_S);
    }

    @Override
    public final int getFledgeBackgroundFetchNetworkConnectTimeoutMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS,
                FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS);
    }

    @Override
    public final int getFledgeBackgroundFetchNetworkReadTimeoutMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS,
                FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS);
    }

    @Override
    public final int getFledgeBackgroundFetchMaxResponseSizeB() {
        return mBackend.getFlag(
                KEY_FLEDGE_BACKGROUND_FETCH_MAX_RESPONSE_SIZE_B,
                FLEDGE_BACKGROUND_FETCH_MAX_RESPONSE_SIZE_B);
    }

    @Override
    public final boolean getProtectedSignalsPeriodicEncodingEnabled() {
        return mBackend.getFlag(
                KEY_PROTECTED_SIGNALS_PERIODIC_ENCODING_ENABLED,
                PROTECTED_SIGNALS_PERIODIC_ENCODING_ENABLED);
    }

    @Override
    public final long getProtectedSignalPeriodicEncodingJobPeriodMs() {
        return mBackend.getFlag(
                KEY_PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_PERIOD_MS,
                PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_PERIOD_MS);
    }

    @Override
    public final long getProtectedSignalsEncoderRefreshWindowSeconds() {
        return mBackend.getFlag(
                KEY_PROTECTED_SIGNALS_ENCODER_REFRESH_WINDOW_SECONDS,
                PROTECTED_SIGNALS_ENCODER_REFRESH_WINDOW_SECONDS);
    }

    @Override
    public final long getProtectedSignalsPeriodicEncodingJobFlexMs() {
        return mBackend.getFlag(
                KEY_PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_FLEX_MS,
                PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_FLEX_MS);
    }

    @Override
    public final int getProtectedSignalsEncodedPayloadMaxSizeBytes() {
        return mBackend.getFlag(
                KEY_PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES,
                PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES);
    }

    @Override
    public final int getProtectedSignalsFetchSignalUpdatesMaxSizeBytes() {
        return mBackend.getFlag(
                KEY_PROTECTED_SIGNALS_FETCH_SIGNAL_UPDATES_MAX_SIZE_BYTES,
                PROTECTED_SIGNALS_FETCH_SIGNAL_UPDATES_MAX_SIZE_BYTES);
    }

    @Override
    public final boolean getFledgeEnableForcedEncodingAfterSignalsUpdate() {
        return mBackend.getFlag(
                KEY_FLEDGE_ENABLE_FORCED_ENCODING_AFTER_SIGNALS_UPDATE,
                FLEDGE_ENABLE_FORCED_ENCODING_AFTER_SIGNALS_UPDATE);
    }

    @Override
    public final long getFledgeForcedEncodingAfterSignalsUpdateCooldownSeconds() {
        return mBackend.getFlag(
                KEY_FLEDGE_FORCED_ENCODING_AFTER_SIGNALS_UPDATE_COOLDOWN_SECONDS,
                FLEDGE_FORCED_ENCODING_AFTER_SIGNALS_UPDATE_COOLDOWN_SECONDS);
    }

    @Override
    public final int getProtectedSignalsMaxJsFailureExecutionOnCertainVersionBeforeStop() {
        return mBackend.getFlag(
                KEY_PROTECTED_SIGNALS_MAX_JS_FAILURE_EXECUTION_ON_CERTAIN_VERSION_BEFORE_STOP,
                PROTECTED_SIGNALS_MAX_JS_FAILURE_EXECUTION_ON_CERTAIN_VERSION_BEFORE_STOP);
    }

    @Override
    public final int getProtectedSignalsMaxSignalSizePerBuyerBytes() {
        return mBackend.getFlag(
                KEY_PROTECTED_SIGNALS_MAX_SIGNAL_SIZE_PER_BUYER_BYTES,
                PROTECTED_SIGNALS_MAX_SIGNAL_SIZE_PER_BUYER_BYTES);
    }

    @Override
    public final int getProtectedSignalsMaxSignalSizePerBuyerWithOversubsciptionBytes() {
        return mBackend.getFlag(
                KEY_PROTECTED_SIGNALS_MAX_SIGNAL_SIZE_PER_BUYER_WITH_OVERSUBSCIPTION_BYTES,
                PROTECTED_SIGNALS_MAX_SIGNAL_SIZE_PER_BUYER_WITH_OVERSUBSCIPTION_BYTES);
    }

    @Override
    public final int getAdSelectionMaxConcurrentBiddingCount() {
        return mBackend.getFlag(
                KEY_FLEDGE_AD_SELECTION_MAX_CONCURRENT_BIDDING_COUNT,
                FLEDGE_AD_SELECTION_MAX_CONCURRENT_BIDDING_COUNT);
    }

    @Override
    public final long getAdSelectionBiddingTimeoutPerCaMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS,
                FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS);
    }

    @Override
    public final long getAdSelectionBiddingTimeoutPerBuyerMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_BUYER_MS,
                FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_BUYER_MS);
    }

    @Override
    public final long getAdSelectionScoringTimeoutMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS, FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS);
    }

    @Override
    public final long getAdSelectionSelectingOutcomeTimeoutMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS,
                FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS);
    }

    @Override
    public final long getAdSelectionOverallTimeoutMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS, FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS);
    }

    @Override
    public final long getAdSelectionFromOutcomesOverallTimeoutMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS,
                FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS);
    }

    @Override
    public final long getAdSelectionOffDeviceOverallTimeoutMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_AD_SELECTION_OFF_DEVICE_OVERALL_TIMEOUT_MS,
                FLEDGE_AD_SELECTION_OFF_DEVICE_OVERALL_TIMEOUT_MS);
    }

    @Override
    public final boolean getFledgeAppInstallFilteringEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_APP_INSTALL_FILTERING_ENABLED, FLEDGE_APP_INSTALL_FILTERING_ENABLED);
    }

    @Override
    public final boolean getFledgeFrequencyCapFilteringEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_FREQUENCY_CAP_FILTERING_ENABLED, FLEDGE_FREQUENCY_CAP_FILTERING_ENABLED);
    }

    @Override
    @SuppressWarnings("InlinedApi")
    public final boolean getFledgeAdSelectionContextualAdsEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_AD_SELECTION_CONTEXTUAL_ADS_ENABLED,
                FLEDGE_AD_SELECTION_CONTEXTUAL_ADS_ENABLED);
    }

    @Override
    public final boolean getFledgeAdSelectionContextualAdsMetricsEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_AD_SELECTION_CONTEXTUAL_ADS_METRICS_ENABLED,
                FLEDGE_AD_SELECTION_CONTEXTUAL_ADS_METRICS_ENABLED);
    }

    @Override
    public final boolean getFledgeAppInstallFilteringMetricsEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_APP_INSTALL_FILTERING_METRICS_ENABLED,
                FLEDGE_APP_INSTALL_FILTERING_METRICS_ENABLED);
    }

    @Override
    public final boolean getFledgeFrequencyCapFilteringMetricsEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_FREQUENCY_CAP_FILTERING_METRICS_ENABLED,
                FLEDGE_FREQUENCY_CAP_FILTERING_METRICS_ENABLED);
    }

    @Override
    public final boolean getFledgeFetchCustomAudienceEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED, FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED);
    }

    @Override
    public final long getFledgeAdSelectionBiddingLogicJsVersion() {
        return mBackend.getFlag(
                KEY_FLEDGE_AD_SELECTION_BIDDING_LOGIC_JS_VERSION,
                FLEDGE_AD_SELECTION_BIDDING_LOGIC_JS_VERSION);
    }

    @Override
    public final long getReportImpressionOverallTimeoutMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS,
                FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS);
    }

    @Override
    public final long getFledgeReportImpressionMaxRegisteredAdBeaconsTotalCount() {
        return mBackend.getFlag(
                KEY_FLEDGE_REPORT_IMPRESSION_MAX_REGISTERED_AD_BEACONS_TOTAL_COUNT,
                FLEDGE_REPORT_IMPRESSION_MAX_REGISTERED_AD_BEACONS_TOTAL_COUNT);
    }

    @Override
    public final long getFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount() {
        return mBackend.getFlag(
                KEY_FLEDGE_REPORT_IMPRESSION_MAX_REGISTERED_AD_BEACONS_PER_AD_TECH_COUNT,
                FLEDGE_REPORT_IMPRESSION_MAX_REGISTERED_AD_BEACONS_PER_AD_TECH_COUNT);
    }

    @Override
    public final long getFledgeReportImpressionRegisteredAdBeaconsMaxInteractionKeySizeB() {
        return mBackend.getFlag(
                KEY_FLEDGE_REPORT_IMPRESSION_REGISTERED_AD_BEACONS_MAX_INTERACTION_KEY_SIZE_B,
                FLEDGE_REPORT_IMPRESSION_REGISTERED_AD_BEACONS_MAX_INTERACTION_KEY_SIZE_B);
    }

    @Override
    public final long getFledgeReportImpressionMaxInteractionReportingUriSizeB() {
        return mBackend.getFlag(
                KEY_FLEDGE_REPORT_IMPRESSION_MAX_INTERACTION_REPORTING_URI_SIZE_B,
                FLEDGE_REPORT_IMPRESSION_MAX_INTERACTION_REPORTING_URI_SIZE_B);
    }

    @Override
    public final boolean getFledgeScheduleCustomAudienceUpdateEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED,
                FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED);
    }

    @Override
    public final long getFledgeScheduleCustomAudienceUpdateJobPeriodMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_JOB_PERIOD_MS,
                FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_JOB_PERIOD_MS);
    }

    @Override
    public final long getFledgeScheduleCustomAudienceUpdateJobFlexMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_JOB_FLEX_MS,
                FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_JOB_FLEX_MS);
    }

    @Override
    public final int getFledgeScheduleCustomAudienceMinDelayMinsOverride() {
        return mBackend.getFlag(
                KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MIN_DELAY_MINS_OVERRIDE,
                FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MIN_DELAY_MINS_OVERRIDE);
    }

    @Override
    public final int getFledgeScheduleCustomAudienceUpdateMaxBytes() {
        return mBackend.getFlag(
                KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MAX_BYTES,
                FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MAX_BYTES);
    }

    @Override
    public final boolean getFledgeHttpCachingEnabled() {
        return mBackend.getFlag(KEY_FLEDGE_HTTP_CACHE_ENABLE, FLEDGE_HTTP_CACHE_ENABLE);
    }

    @Override
    public final boolean getFledgeHttpJsCachingEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_HTTP_CACHE_ENABLE_JS_CACHING, FLEDGE_HTTP_CACHE_ENABLE_JS_CACHING);
    }

    @Override
    public final long getFledgeHttpCacheMaxEntries() {
        return mBackend.getFlag(KEY_FLEDGE_HTTP_CACHE_MAX_ENTRIES, FLEDGE_HTTP_CACHE_MAX_ENTRIES);
    }

    @Override
    public final boolean getFledgeOnDeviceAuctionShouldUseUnifiedTables() {
        return mBackend.getFlag(
                KEY_FLEDGE_ON_DEVICE_AUCTION_SHOULD_USE_UNIFIED_TABLES,
                FLEDGE_ON_DEVICE_AUCTION_SHOULD_USE_UNIFIED_TABLES);
    }

    @Override
    public final long getFledgeHttpCacheMaxAgeSeconds() {
        return mBackend.getFlag(
                KEY_FLEDGE_HTTP_CACHE_DEFAULT_MAX_AGE_SECONDS,
                FLEDGE_HTTP_CACHE_DEFAULT_MAX_AGE_SECONDS);
    }

    @Override
    public final int getFledgeAdCounterHistogramAbsoluteMaxTotalEventCount() {
        return mBackend.getFlag(
                KEY_FLEDGE_AD_COUNTER_HISTOGRAM_ABSOLUTE_MAX_TOTAL_EVENT_COUNT,
                FLEDGE_AD_COUNTER_HISTOGRAM_ABSOLUTE_MAX_TOTAL_EVENT_COUNT);
    }

    @Override
    public final int getFledgeAdCounterHistogramLowerMaxTotalEventCount() {
        return mBackend.getFlag(
                KEY_FLEDGE_AD_COUNTER_HISTOGRAM_LOWER_MAX_TOTAL_EVENT_COUNT,
                FLEDGE_AD_COUNTER_HISTOGRAM_LOWER_MAX_TOTAL_EVENT_COUNT);
    }

    @Override
    public final int getFledgeAdCounterHistogramAbsoluteMaxPerBuyerEventCount() {
        return mBackend.getFlag(
                KEY_FLEDGE_AD_COUNTER_HISTOGRAM_ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT,
                FLEDGE_AD_COUNTER_HISTOGRAM_ABSOLUTE_MAX_PER_BUYER_EVENT_COUNT);
    }

    @Override
    public final int getFledgeAdCounterHistogramLowerMaxPerBuyerEventCount() {
        return mBackend.getFlag(
                KEY_FLEDGE_AD_COUNTER_HISTOGRAM_LOWER_MAX_PER_BUYER_EVENT_COUNT,
                FLEDGE_AD_COUNTER_HISTOGRAM_LOWER_MAX_PER_BUYER_EVENT_COUNT);
    }

    @Override
    public final boolean getProtectedSignalsCleanupEnabled() {
        return mBackend.getFlag(
                KEY_PROTECTED_SIGNALS_CLEANUP_ENABLED, PROTECTED_SIGNALS_CLEANUP_ENABLED);
    }

    // MDD related flags.
    @Override
    public final int getDownloaderConnectionTimeoutMs() {
        return mBackend.getFlag(
                KEY_DOWNLOADER_CONNECTION_TIMEOUT_MS, DOWNLOADER_CONNECTION_TIMEOUT_MS);
    }

    @Override
    public final int getDownloaderReadTimeoutMs() {
        return mBackend.getFlag(KEY_DOWNLOADER_READ_TIMEOUT_MS, DOWNLOADER_READ_TIMEOUT_MS);
    }

    @Override
    public final int getDownloaderMaxDownloadThreads() {
        return mBackend.getFlag(
                KEY_DOWNLOADER_MAX_DOWNLOAD_THREADS, DOWNLOADER_MAX_DOWNLOAD_THREADS);
    }

    @Override
    public final String getMddTopicsClassifierManifestFileUrl() {
        return mBackend.getFlag(
                KEY_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL,
                MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL);
    }

    @Override
    public final String getMeasurementDebugJoinKeyEnrollmentAllowlist() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_DEBUG_JOIN_KEY_ENROLLMENT_ALLOWLIST,
                DEFAULT_MEASUREMENT_DEBUG_JOIN_KEY_ENROLLMENT_ALLOWLIST);
    }

    @Override
    public final String getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_DEBUG_KEY_AD_ID_MATCHING_ENROLLMENT_BLOCKLIST,
                DEFAULT_MEASUREMENT_PLATFORM_DEBUG_AD_ID_MATCHING_BLOCKLIST);
    }

    @Override
    public final boolean getEnableComputeVersionFromMappings() {
        return mBackend.getFlag(
                KEY_ENABLE_COMPUTE_VERSION_FROM_MAPPINGS,
                DEFAULT_COMPUTE_VERSION_FROM_MAPPINGS_ENABLED);
    }

    @Override
    public final String getMainlineTrainVersion() {
        return mBackend.getFlag(KEY_MAINLINE_TRAIN_VERSION, DEFAULT_MAINLINE_TRAIN_VERSION);
    }

    @Override
    public final String getAdservicesVersionMappings() {
        return mBackend.getFlag(
                KEY_ADSERVICES_VERSION_MAPPINGS, DEFAULT_ADSERVICES_VERSION_MAPPINGS);
    }

    // ADID Killswitches

    // APPSETID Killswitch.

    // TOPICS Killswitches

    // MDD Killswitches

    // FLEDGE Kill switches

    // Encryption key related flags.
    @Override
    public final int getEncryptionKeyJobRequiredNetworkType() {
        return mBackend.getFlag(
                KEY_ENCRYPTION_KEY_JOB_REQUIRED_NETWORK_TYPE,
                ENCRYPTION_KEY_JOB_REQUIRED_NETWORK_TYPE);
    }

    @Override
    public final long getEncryptionKeyJobPeriodMs() {
        return mBackend.getFlag(KEY_ENCRYPTION_KEY_JOB_PERIOD_MS, ENCRYPTION_KEY_JOB_PERIOD_MS);
    }

    @Override
    public final boolean getEnableMddEncryptionKeys() {
        return mBackend.getFlag(KEY_ENABLE_MDD_ENCRYPTION_KEYS, ENABLE_MDD_ENCRYPTION_KEYS);
    }

    @Override
    public final String getMddEncryptionKeysManifestFileUrl() {
        return mBackend.getFlag(
                KEY_MDD_ENCRYPTION_KEYS_MANIFEST_FILE_URL, MDD_ENCRYPTION_KEYS_MANIFEST_FILE_URL);
    }

    @Override
    public final String getPpapiAppAllowList() {
        return mBackend.getFlag(KEY_PPAPI_APP_ALLOW_LIST, PPAPI_APP_ALLOW_LIST);
    }

    @Override
    public final String getPasAppAllowList() {
        // default to using the same fixed list as custom audiences
        return mBackend.getFlag(KEY_PAS_APP_ALLOW_LIST, PPAPI_APP_ALLOW_LIST);
    }

    @Override
    public final String getAdIdApiAppBlockList() {
        return mBackend.getFlag(KEY_AD_ID_API_APP_BLOCK_LIST, AD_ID_API_APP_BLOCK_LIST);
    }

    @Override
    public final String getMsmtApiAppAllowList() {
        return mBackend.getFlag(KEY_MSMT_API_APP_ALLOW_LIST, MSMT_API_APP_ALLOW_LIST);
    }

    @Override
    public final String getMsmtApiAppBlockList() {
        return mBackend.getFlag(KEY_MSMT_API_APP_BLOCK_LIST, MSMT_API_APP_BLOCK_LIST);
    }

    // AdServices APK SHA certs.
    @Override
    public final String getAdservicesApkShaCertificate() {
        return mBackend.getFlag(KEY_ADSERVICES_APK_SHA_CERTS, ADSERVICES_APK_SHA_CERTIFICATE);
    }

    // PPAPI Signature allow-list.
    @Override
    public final String getPpapiAppSignatureAllowList() {
        return mBackend.getFlag(KEY_PPAPI_APP_SIGNATURE_ALLOW_LIST, PPAPI_APP_SIGNATURE_ALLOW_LIST);
    }

    // AppSearch writer allow-list
    @Override
    public final String getAppsearchWriterAllowListOverride() {
        return mBackend.getFlag(
                KEY_APPSEARCH_WRITER_ALLOW_LIST_OVERRIDE, APPSEARCH_WRITER_ALLOW_LIST_OVERRIDE);
    }

    // Rate Limit Flags.
    @Override
    public final float getFledgeUpdateAdCounterHistogramRequestPermitsPerSecond() {
        return mBackend.getFlag(
                KEY_FLEDGE_UPDATE_AD_COUNTER_HISTOGRAM_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_UPDATE_AD_COUNTER_HISTOGRAM_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public final long getUiOtaStringsDownloadDeadline() {
        return mBackend.getFlag(
                KEY_UI_OTA_STRINGS_DOWNLOAD_DEADLINE, UI_OTA_STRINGS_DOWNLOAD_DEADLINE);
    }

    @Override
    public final boolean isUiFeatureTypeLoggingEnabled() {
        return mBackend.getFlag(
                KEY_UI_FEATURE_TYPE_LOGGING_ENABLED, UI_FEATURE_TYPE_LOGGING_ENABLED);
    }

    @Override
    public final boolean getFledgeAdSelectionPrebuiltUriEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_AD_SELECTION_PREBUILT_URI_ENABLED,
                FLEDGE_AD_SELECTION_PREBUILT_URI_ENABLED);
    }

    // TODO(b/384798806): add method to get ImmutableList<String> on FlagBackend?
    @Override
    public final ImmutableList<Integer> getFledgeAuctionServerPayloadBucketSizes() {
        String bucketSizesString =
                mBackend.getFlag(KEY_FLEDGE_AUCTION_SERVER_PAYLOAD_BUCKET_SIZES, null);
        try {
            return Optional.ofNullable(bucketSizesString)
                    .map(
                            s ->
                                    Arrays.stream(s.split(Constants.ARRAY_SPLITTER_COMMA))
                                            .map(Integer::valueOf)
                                            .collect(Collectors.toList()))
                    .map(ImmutableList::copyOf)
                    .orElse(FLEDGE_AUCTION_SERVER_PAYLOAD_BUCKET_SIZES);
        } catch (Exception e) {
            // TODO(b/384578475): Add CEL here
            LogUtil.e("Malformed bucket list found in device config, setting to default.");
            return FLEDGE_AUCTION_SERVER_PAYLOAD_BUCKET_SIZES;
        }
    }

    @Override
    public final boolean getFledgeAuctionServerForceSearchWhenOwnerIsAbsentEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_FORCE_SEARCH_WHEN_OWNER_IS_ABSENT_ENABLED,
                FLEDGE_AUCTION_SERVER_FORCE_SEARCH_WHEN_OWNER_IS_ABSENT_ENABLED);
    }

    @Override
    public final boolean getAdSelectionOffDeviceRequestCompressionEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_AD_SELECTION_OFF_DEVICE_REQUEST_COMPRESSION_ENABLED,
                FLEDGE_AD_SELECTION_OFF_DEVICE_REQUEST_COMPRESSION_ENABLED);
    }

    @Override
    public final boolean getFledgeAuctionServerEnabled() {
        return mBackend.getFlag(KEY_FLEDGE_AUCTION_SERVER_ENABLED, FLEDGE_AUCTION_SERVER_ENABLED);
    }

    @Override
    public final boolean getFledgeAuctionServerEnableAdFilterInGetAdSelectionData() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_ENABLE_AD_FILTER_IN_GET_AD_SELECTION_DATA,
                FLEDGE_AUCTION_SERVER_ENABLE_AD_FILTER_IN_GET_AD_SELECTION_DATA);
    }

    @Override
    public final boolean getFledgeAuctionServerMediaTypeChangeEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_MEDIA_TYPE_CHANGE_ENABLED,
                FLEDGE_AUCTION_SERVER_MEDIA_TYPE_CHANGE_ENABLED);
    }

    @Override
    public final String getFledgeAuctionServerAuctionKeyFetchUri() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_URI,
                FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_URI);
    }

    @Override
    public final boolean getFledgeAuctionServerRefreshExpiredKeysDuringAuction() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_REFRESH_EXPIRED_KEYS_DURING_AUCTION,
                FLEDGE_AUCTION_SERVER_REFRESH_EXPIRED_KEYS_DURING_AUCTION);
    }

    @Override
    public final boolean getFledgeAuctionServerRequestFlagsEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_REQUEST_FLAGS_ENABLED,
                FLEDGE_AUCTION_SERVER_REQUEST_FLAGS_ENABLED);
    }

    @Override
    public final boolean getFledgeAuctionServerOmitAdsEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_OMIT_ADS_ENABLED, FLEDGE_AUCTION_SERVER_OMIT_ADS_ENABLED);
    }

    @Override
    public final String getFledgeAuctionServerJoinKeyFetchUri() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_JOIN_KEY_FETCH_URI,
                FLEDGE_AUCTION_SERVER_JOIN_KEY_FETCH_URI);
    }

    @Override
    public final long getFledgeAuctionServerEncryptionKeyMaxAgeSeconds() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_ENCRYPTION_KEY_MAX_AGE_SECONDS,
                FLEDGE_AUCTION_SERVER_ENCRYPTION_KEY_MAX_AGE_SECONDS);
    }

    @Override
    public final int getFledgeAuctionServerAuctionKeySharding() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_AUCTION_KEY_SHARDING,
                FLEDGE_AUCTION_SERVER_AUCTION_KEY_SHARDING);
    }

    public final int getFledgeAuctionServerEncryptionAlgorithmKemId() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_KEM_ID,
                FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_KEM_ID);
    }

    @Override
    public final int getFledgeAuctionServerEncryptionAlgorithmKdfId() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_KDF_ID,
                FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_KDF_ID);
    }

    @Override
    public final int getFledgeAuctionServerEncryptionAlgorithmAeadId() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_AEAD_ID,
                FLEDGE_AUCTION_SERVER_ENCRYPTION_ALGORITHM_AEAD_ID);
    }

    @Override
    public final long getFledgeAuctionServerAuctionKeyFetchTimeoutMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_TIMEOUT_MS,
                FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_TIMEOUT_MS);
    }

    @Override
    public final long getFledgeAuctionServerOverallTimeoutMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_OVERALL_TIMEOUT_MS,
                FLEDGE_AUCTION_SERVER_OVERALL_TIMEOUT_MS);
    }

    @Override
    public final boolean getFledgeAuctionServerBackgroundKeyFetchJobEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_ENABLED,
                FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_ENABLED);
    }

    @Override
    public final boolean getFledgeAuctionServerBackgroundAuctionKeyFetchEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_AUCTION_KEY_FETCH_ENABLED,
                FLEDGE_AUCTION_SERVER_BACKGROUND_AUCTION_KEY_FETCH_ENABLED);
    }

    @Override
    public final boolean getFledgeAuctionServerBackgroundJoinKeyFetchEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_JOIN_KEY_FETCH_ENABLED,
                FLEDGE_AUCTION_SERVER_BACKGROUND_JOIN_KEY_FETCH_ENABLED);
    }

    @Override
    public final int getFledgeAuctionServerBackgroundKeyFetchNetworkConnectTimeoutMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_CONNECT_TIMEOUT_MS,
                FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_CONNECT_TIMEOUT_MS);
    }

    @Override
    public final int getFledgeAuctionServerBackgroundKeyFetchNetworkReadTimeoutMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_READ_TIMEOUT_MS,
                FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_READ_TIMEOUT_MS);
    }

    @Override
    public final int getFledgeAuctionServerBackgroundKeyFetchMaxResponseSizeB() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RESPONSE_SIZE_B,
                FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RESPONSE_SIZE_B);
    }

    @Override
    public final long getFledgeAuctionServerBackgroundKeyFetchJobMaxRuntimeMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RUNTIME_MS,
                FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RUNTIME_MS);
    }

    @Override
    public final long getFledgeAuctionServerBackgroundKeyFetchJobPeriodMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_PERIOD_MS,
                FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_PERIOD_MS);
    }

    @Override
    public final long getFledgeAuctionServerBackgroundKeyFetchJobFlexMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_FLEX_MS,
                FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_FLEX_MS);
    }

    @Override
    public final boolean getFledgeAuctionServerBackgroundKeyFetchOnEmptyDbAndInAdvanceEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_ON_EMPTY_DB_AND_IN_ADVANCE_ENABLED,
                FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_ON_EMPTY_DB_AND_IN_ADVANCE_ENABLED);
    }

    @Override
    public final long getFledgeAuctionServerBackgroundKeyFetchInAdvanceIntervalMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_IN_ADVANCE_INTERVAL_MS,
                FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_IN_ADVANCE_INTERVAL_MS);
    }

    @Override
    public final int getFledgeAuctionServerCompressionAlgorithmVersion() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_COMPRESSION_ALGORITHM_VERSION,
                FLEDGE_AUCTION_SERVER_COMPRESSION_ALGORITHM_VERSION);
    }

    @Override
    public final int getFledgeAuctionServerPayloadFormatVersion() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_PAYLOAD_FORMAT_VERSION,
                FLEDGE_AUCTION_SERVER_PAYLOAD_FORMAT_VERSION);
    }

    @Override
    public final boolean getFledgeAuctionServerEnableDebugReporting() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_ENABLE_DEBUG_REPORTING,
                FLEDGE_AUCTION_SERVER_ENABLE_DEBUG_REPORTING);
    }

    @Override
    public final long getFledgeAuctionServerAdIdFetcherTimeoutMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_AD_ID_FETCHER_TIMEOUT_MS,
                DEFAULT_AUCTION_SERVER_AD_ID_FETCHER_TIMEOUT_MS);
    }

    @Override
    public final boolean getFledgeAuctionServerEnablePasUnlimitedEgress() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_ENABLE_PAS_UNLIMITED_EGRESS,
                DEFAULT_FLEDGE_AUCTION_SERVER_ENABLE_PAS_UNLIMITED_EGRESS);
    }

    @Override
    public final boolean getFledgeAuctionServerAdRenderIdEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_AD_RENDER_ID_ENABLED,
                FLEDGE_AUCTION_SERVER_AD_RENDER_ID_ENABLED);
    }

    @Override
    public final long getFledgeAuctionServerAdRenderIdMaxLength() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_AD_RENDER_ID_MAX_LENGTH,
                FLEDGE_AUCTION_SERVER_AD_RENDER_ID_MAX_LENGTH);
    }

    @Override
    public final String getFledgeAuctionServerCoordinatorUrlAllowlist() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_COORDINATOR_URL_ALLOWLIST,
                FLEDGE_AUCTION_SERVER_COORDINATOR_URL_ALLOWLIST);
    }

    @Override
    public final boolean getFledgeAuctionServerGetAdSelectionDataPayloadMetricsEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_GET_AD_SELECTION_DATA_PAYLOAD_METRICS_ENABLED,
                FLEDGE_AUCTION_SERVER_GET_AD_SELECTION_DATA_PAYLOAD_METRICS_ENABLED);
    }

    @Override
    public final boolean getFledgeGetAdSelectionDataSellerConfigurationEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_GET_AD_SELECTION_DATA_SELLER_CONFIGURATION_ENABLED,
                FLEDGE_GET_AD_SELECTION_DATA_SELLER_CONFIGURATION_ENABLED);
    }

    @Override
    public final int getFledgeGetAdSelectionDataBuyerInputCreatorVersion() {
        return mBackend.getFlag(
                KEY_FLEDGE_GET_AD_SELECTION_DATA_BUYER_INPUT_CREATOR_VERSION,
                FLEDGE_GET_AD_SELECTION_DATA_BUYER_INPUT_CREATOR_VERSION);
    }

    @Override
    public final int getFledgeGetAdSelectionDataMaxNumEntirePayloadCompressions() {
        return mBackend.getFlag(
                KEY_FLEDGE_GET_AD_SELECTION_DATA_MAX_NUM_ENTIRE_PAYLOAD_COMPRESSIONS,
                FLEDGE_GET_AD_SELECTION_DATA_MAX_NUM_ENTIRE_PAYLOAD_COMPRESSIONS);
    }

    @Override
    public final boolean getFledgeGetAdSelectionDataDeserializeOnlyAdRenderIds() {
        return mBackend.getFlag(
                KEY_FLEDGE_GET_AD_SELECTION_DATA_DESERIALIZE_ONLY_AD_RENDER_IDS,
                FLEDGE_GET_AD_SELECTION_DATA_DESERIALIZE_ONLY_AD_RENDER_IDS);
    }

    @Override
    public final boolean getFledgeAuctionServerMultiCloudEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_MULTI_CLOUD_ENABLED,
                FLEDGE_AUCTION_SERVER_MULTI_CLOUD_ENABLED);
    }

    @Override
    public final boolean isEnableEnrollmentTestSeed() {
        return mBackend.getFlag(KEY_ENABLE_ENROLLMENT_TEST_SEED, ENABLE_ENROLLMENT_TEST_SEED);
    }

    @Override
    public final boolean getEnrollmentMddRecordDeletionEnabled() {
        return mBackend.getFlag(
                KEY_ENROLLMENT_MDD_RECORD_DELETION_ENABLED, ENROLLMENT_MDD_RECORD_DELETION_ENABLED);
    }

    @Override
    public final boolean getEnrollmentEnableLimitedLogging() {
        return mBackend.getFlag(
                KEY_ENROLLMENT_ENABLE_LIMITED_LOGGING, ENROLLMENT_ENABLE_LIMITED_LOGGING);
    }

    @Override
    public final boolean getFledgeEventLevelDebugReportingEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_ENABLED,
                FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_ENABLED);
    }

    @Override
    public final boolean getFledgeEventLevelDebugReportSendImmediately() {
        return mBackend.getFlag(
                KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORT_SEND_IMMEDIATELY,
                FLEDGE_EVENT_LEVEL_DEBUG_REPORT_SEND_IMMEDIATELY);
    }

    @Override
    public final int getFledgeEventLevelDebugReportingBatchDelaySeconds() {
        return mBackend.getFlag(
                KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_BATCH_DELAY_SECONDS,
                FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_BATCH_DELAY_SECONDS);
    }

    @Override
    public final int getFledgeEventLevelDebugReportingMaxItemsPerBatch() {
        return mBackend.getFlag(
                KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_MAX_ITEMS_PER_BATCH,
                FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_MAX_ITEMS_PER_BATCH);
    }

    @Override
    public final int getFledgeDebugReportSenderJobNetworkConnectionTimeoutMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_DEBUG_REPORTI_SENDER_JOB_NETWORK_CONNECT_TIMEOUT_MS,
                FLEDGE_DEBUG_REPORT_SENDER_JOB_NETWORK_CONNECT_TIMEOUT_MS);
    }

    @Override
    public final int getFledgeDebugReportSenderJobNetworkReadTimeoutMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_DEBUG_REPORTI_SENDER_JOB_NETWORK_READ_TIMEOUT_MS,
                FLEDGE_DEBUG_REPORT_SENDER_JOB_NETWORK_READ_TIMEOUT_MS);
    }

    @Override
    public final long getFledgeDebugReportSenderJobMaxRuntimeMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_DEBUG_REPORTI_SENDER_JOB_MAX_TIMEOUT_MS,
                FLEDGE_DEBUG_REPORT_SENDER_JOB_MAX_RUNTIME_MS);
    }

    @Override
    public final long getFledgeDebugReportSenderJobPeriodMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_DEBUG_REPORT_SENDER_JOB_PERIOD_MS,
                FLEDGE_DEBUG_REPORT_SENDER_JOB_PERIOD_MS);
    }

    @Override
    public final long getFledgeDebugReportSenderJobFlexMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_DEBUG_REPORT_SENDER_JOB_FLEX_MS, FLEDGE_DEBUG_REPORT_SENDER_JOB_FLEX_MS);
    }

    @Override
    public final boolean getEnforceForegroundStatusForFledgeRunAdSelection() {
        return mBackend.getFlag(
                KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION,
                ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION);
    }

    @Override
    public final boolean getEnforceForegroundStatusForFledgeReportImpression() {
        return mBackend.getFlag(
                KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION,
                ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION);
    }

    @Override
    public final boolean getEnforceForegroundStatusForFledgeReportInteraction() {
        return mBackend.getFlag(
                KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_INTERACTION,
                ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_INTERACTION);
    }

    @Override
    public final int getForegroundStatuslLevelForValidation() {
        return mBackend.getFlag(KEY_FOREGROUND_STATUS_LEVEL, FOREGROUND_STATUS_LEVEL);
    }

    @Override
    public final boolean getEnforceForegroundStatusForFledgeOverrides() {
        return mBackend.getFlag(
                KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDE,
                ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDES);
    }

    @Override
    public final boolean getEnforceForegroundStatusForFledgeCustomAudience() {
        return mBackend.getFlag(
                KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE,
                ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE);
    }

    @Override
    public final boolean getEnforceForegroundStatusForFetchAndJoinCustomAudience() {
        return mBackend.getFlag(
                KEY_ENFORCE_FOREGROUND_STATUS_FETCH_AND_JOIN_CUSTOM_AUDIENCE,
                ENFORCE_FOREGROUND_STATUS_FETCH_AND_JOIN_CUSTOM_AUDIENCE);
    }

    @Override
    public final boolean getEnforceForegroundStatusForLeaveCustomAudience() {
        return mBackend.getFlag(
                KEY_ENFORCE_FOREGROUND_STATUS_LEAVE_CUSTOM_AUDIENCE,
                ENFORCE_FOREGROUND_STATUS_LEAVE_CUSTOM_AUDIENCE);
    }

    @Override
    public final boolean getEnforceForegroundStatusForScheduleCustomAudience() {
        return mBackend.getFlag(
                KEY_ENFORCE_FOREGROUND_STATUS_SCHEDULE_CUSTOM_AUDIENCE,
                ENFORCE_FOREGROUND_STATUS_SCHEDULE_CUSTOM_AUDIENCE);
    }

    @Override
    public final boolean getEnableCustomAudienceComponentAds() {
        return mBackend.getFlag(
                KEY_ENABLE_CUSTOM_AUDIENCE_COMPONENT_ADS, ENABLE_CUSTOM_AUDIENCE_COMPONENT_ADS);
    }

    @Override
    public final boolean getEnablePasComponentAds() {
        return mBackend.getFlag(KEY_ENABLE_PAS_COMPONENT_ADS, ENABLE_PAS_COMPONENT_ADS);
    }

    @Override
    public final int getMaxComponentAdsPerCustomAudience() {
        return mBackend.getFlag(
                KEY_MAX_COMPONENT_ADS_PER_CUSTOM_AUDIENCE, MAX_COMPONENT_ADS_PER_CUSTOM_AUDIENCE);
    }

    @Override
    public final int getComponentAdRenderIdMaxLengthBytes() {
        return mBackend.getFlag(
                KEY_COMPONENT_AD_RENDER_ID_MAX_LENGTH_BYTES,
                COMPONENT_AD_RENDER_ID_MAX_LENGTH_BYTES);
    }

    @Override
    public final boolean getFledgeRegisterAdBeaconEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED, FLEDGE_REGISTER_AD_BEACON_ENABLED);
    }

    @Override
    public final boolean getFledgeCpcBillingEnabled() {
        return mBackend.getFlag(KEY_FLEDGE_CPC_BILLING_ENABLED, FLEDGE_CPC_BILLING_ENABLED);
    }

    @Override
    public final boolean getFledgeDataVersionHeaderEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_DATA_VERSION_HEADER_ENABLED, FLEDGE_DATA_VERSION_HEADER_ENABLED);
    }

    @Override
    public final boolean getFledgeSelectAdsFromOutcomesApiMetricsEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_SELECT_ADS_FROM_OUTCOMES_API_METRICS_ENABLED,
                FLEDGE_SELECT_ADS_FROM_OUTCOMES_API_METRICS_ENABLED);
    }

    @Override
    public final boolean getFledgeCpcBillingMetricsEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_CPC_BILLING_METRICS_ENABLED, FLEDGE_CPC_BILLING_METRICS_ENABLED);
    }

    @Override
    public final boolean getFledgeDataVersionHeaderMetricsEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_DATA_VERSION_HEADER_METRICS_ENABLED,
                FLEDGE_DATA_VERSION_HEADER_METRICS_ENABLED);
    }

    @Override
    public final boolean getFledgeReportImpressionApiMetricsEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_REPORT_IMPRESSION_API_METRICS_ENABLED,
                FLEDGE_REPORT_IMPRESSION_API_METRICS_ENABLED);
    }

    @Override
    public final boolean getFledgeJsScriptResultCodeMetricsEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_JS_SCRIPT_RESULT_CODE_METRICS_ENABLED,
                FLEDGE_JS_SCRIPT_RESULT_CODE_METRICS_ENABLED);
    }

    @Override
    public final boolean getEnforceForegroundStatusForMeasurementDeleteRegistrations() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_DELETE_REGISTRATIONS,
                MEASUREMENT_ENFORCE_FOREGROUND_STATUS_DELETE_REGISTRATIONS);
    }

    @Override
    public final boolean getEnforceForegroundStatusForMeasurementRegisterSource() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCE,
                MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCE);
    }

    @Override
    public final boolean getEnforceForegroundStatusForMeasurementRegisterTrigger() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_TRIGGER,
                MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_TRIGGER);
    }

    @Override
    public final boolean getEnforceForegroundStatusForMeasurementRegisterWebSource() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_SOURCE,
                MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_SOURCE);
    }

    @Override
    public final boolean getEnforceForegroundStatusForMeasurementRegisterWebTrigger() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_TRIGGER,
                MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_TRIGGER);
    }

    @Override
    public final boolean getEnforceForegroundStatusForMeasurementStatus() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_GET_STATUS,
                MEASUREMENT_ENFORCE_FOREGROUND_STATUS_GET_STATUS);
    }

    @Override
    public final boolean getEnforceForegroundStatusForMeasurementRegisterSources() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCES,
                MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCES);
    }

    @Override
    public final long getIsolateMaxHeapSizeBytes() {
        return mBackend.getFlag(KEY_ISOLATE_MAX_HEAP_SIZE_BYTES, ISOLATE_MAX_HEAP_SIZE_BYTES);
    }

    @Override
    public final String getWebContextClientAppAllowList() {
        return mBackend.getFlag(KEY_WEB_CONTEXT_CLIENT_ALLOW_LIST, WEB_CONTEXT_CLIENT_ALLOW_LIST);
    }

    @Override
    public final boolean getConsentManagerLazyEnableMode() {
        return mBackend.getFlag(
                KEY_CONSENT_MANAGER_LAZY_ENABLE_MODE, CONSENT_MANAGER_LAZY_ENABLE_MODE);
    }

    @Override
    public final boolean getConsentAlreadyInteractedEnableMode() {
        return mBackend.getFlag(
                KEY_CONSENT_ALREADY_INTERACTED_FIX_ENABLE, CONSENT_ALREADY_INTERACTED_FIX_ENABLE);
    }

    @Override
    public final String getConsentNotificationResetToken() {
        return mBackend.getFlag(
                KEY_CONSENT_NOTIFICATION_RESET_TOKEN, CONSENT_NOTIFICATION_RESET_TOKEN);
    }

    @Override
    public final long getConsentNotificationIntervalBeginMs() {
        return mBackend.getFlag(
                KEY_CONSENT_NOTIFICATION_INTERVAL_BEGIN_MS, CONSENT_NOTIFICATION_INTERVAL_BEGIN_MS);
    }

    @Override
    public final long getConsentNotificationIntervalEndMs() {
        return mBackend.getFlag(
                KEY_CONSENT_NOTIFICATION_INTERVAL_END_MS, CONSENT_NOTIFICATION_INTERVAL_END_MS);
    }

    @Override
    public final long getConsentNotificationMinimalDelayBeforeIntervalEnds() {
        return mBackend.getFlag(
                KEY_CONSENT_NOTIFICATION_MINIMAL_DELAY_BEFORE_INTERVAL_ENDS,
                CONSENT_NOTIFICATION_MINIMAL_DELAY_BEFORE_INTERVAL_ENDS);
    }

    @Override
    public final int getConsentSourceOfTruth() {
        return mBackend.getFlag(KEY_CONSENT_SOURCE_OF_TRUTH, DEFAULT_CONSENT_SOURCE_OF_TRUTH);
    }

    @Override
    public final int getBlockedTopicsSourceOfTruth() {
        return mBackend.getFlag(
                KEY_BLOCKED_TOPICS_SOURCE_OF_TRUTH, DEFAULT_BLOCKED_TOPICS_SOURCE_OF_TRUTH);
    }

    @Override
    public final long getMaxResponseBasedRegistrationPayloadSizeBytes() {
        return mBackend.getFlag(
                KEY_MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES,
                MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES);
    }

    @Override
    public final long getMaxTriggerRegistrationHeaderSizeBytes() {
        return mBackend.getFlag(
                KEY_MAX_TRIGGER_REGISTRATION_HEADER_SIZE_BYTES,
                MAX_TRIGGER_REGISTRATION_HEADER_SIZE_BYTES);
    }

    @Override
    public final long getMaxOdpTriggerRegistrationHeaderSizeBytes() {
        return mBackend.getFlag(
                KEY_MAX_ODP_TRIGGER_REGISTRATION_HEADER_SIZE_BYTES,
                MAX_ODP_TRIGGER_REGISTRATION_HEADER_SIZE_BYTES);
    }

    @Override
    public final boolean getMeasurementEnableUpdateTriggerHeaderLimit() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_UPDATE_TRIGGER_REGISTRATION_HEADER_LIMIT,
                MEASUREMENT_ENABLE_UPDATE_TRIGGER_REGISTRATION_HEADER_LIMIT);
    }

    @Override
    public final boolean getUiDialogsFeatureEnabled() {
        return mBackend.getFlag(KEY_UI_DIALOGS_FEATURE_ENABLED, UI_DIALOGS_FEATURE_ENABLED);
    }

    @Override
    public final boolean getUiDialogFragmentEnabled() {
        return mBackend.getFlag(KEY_UI_DIALOG_FRAGMENT_ENABLED, UI_DIALOG_FRAGMENT);
    }

    @Override
    public final boolean isEeaDeviceFeatureEnabled() {
        return mBackend.getFlag(KEY_IS_EEA_DEVICE_FEATURE_ENABLED, IS_EEA_DEVICE_FEATURE_ENABLED);
    }

    @Override
    public final boolean isEeaDevice() {
        return mBackend.getFlag(KEY_IS_EEA_DEVICE, IS_EEA_DEVICE);
    }

    @Override
    public final boolean getRecordManualInteractionEnabled() {
        return mBackend.getFlag(
                KEY_RECORD_MANUAL_INTERACTION_ENABLED, RECORD_MANUAL_INTERACTION_ENABLED);
    }

    @Override
    public final String getUiEeaCountries() {
        return mBackend.getFlag(KEY_UI_EEA_COUNTRIES, UI_EEA_COUNTRIES);
    }

    @Override
    public final String getDebugUx() {
        return mBackend.getFlag(KEY_DEBUG_UX, DEBUG_UX);
    }

    @Override
    public final boolean getToggleSpeedBumpEnabled() {
        return mBackend.getFlag(KEY_UI_TOGGLE_SPEED_BUMP_ENABLED, TOGGLE_SPEED_BUMP_ENABLED);
    }

    @Override
    public final long getAdSelectionExpirationWindowS() {
        return mBackend.getFlag(
                KEY_FLEDGE_AD_SELECTION_EXPIRATION_WINDOW_S,
                FLEDGE_AD_SELECTION_EXPIRATION_WINDOW_S);
    }

    @Override
    public final boolean getMeasurementEnableAggregatableNamedBudgets() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_AGGREGATABLE_NAMED_BUDGETS,
                MEASUREMENT_ENABLE_AGGREGATABLE_NAMED_BUDGETS);
    }

    @Override
    public final boolean getMeasurementEnableV1SourceTriggerData() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_V1_SOURCE_TRIGGER_DATA,
                MEASUREMENT_ENABLE_V1_SOURCE_TRIGGER_DATA);
    }

    @Override
    public final boolean getMeasurementFlexibleEventReportingApiEnabled() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_FLEXIBLE_EVENT_REPORTING_API_ENABLED,
                MEASUREMENT_FLEXIBLE_EVENT_REPORTING_API_ENABLED);
    }

    @Override
    public final boolean getMeasurementEnableTriggerDataMatching() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_TRIGGER_DATA_MATCHING,
                MEASUREMENT_ENABLE_TRIGGER_DATA_MATCHING);
    }

    @Override
    public final float getMeasurementFlexApiMaxInformationGainEvent() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_EVENT,
                MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_EVENT);
    }

    @Override
    public final float getMeasurementFlexApiMaxInformationGainNavigation() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_NAVIGATION,
                MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_NAVIGATION);
    }

    @Override
    public final float getMeasurementFlexApiMaxInformationGainDualDestinationEvent() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_EVENT,
                MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_EVENT);
    }

    @Override
    public final float getMeasurementFlexApiMaxInformationGainDualDestinationNavigation() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_NAVIGATION,
                MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_NAVIGATION);
    }

    @Override
    public final float getMeasurementAttributionScopeMaxInfoGainNavigation() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_NAVIGATION,
                MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_NAVIGATION);
    }

    @Override
    public final float getMeasurementAttributionScopeMaxInfoGainDualDestinationNavigation() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_NAVIGATION,
                MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_NAVIGATION);
    }

    @Override
    public final float getMeasurementAttributionScopeMaxInfoGainEvent() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_EVENT,
                MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_EVENT);
    }

    @Override
    public final float getMeasurementAttributionScopeMaxInfoGainDualDestinationEvent() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_EVENT,
                MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_EVENT);
    }

    @Override
    public final boolean getMeasurementEnableFakeReportTriggerTime() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_FAKE_REPORT_TRIGGER_TIME,
                MEASUREMENT_ENABLE_FAKE_REPORT_TRIGGER_TIME);
    }

    @Override
    public final long getMeasurementMaxReportStatesPerSourceRegistration() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION,
                MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION);
    }

    @Override
    public final int getMeasurementFlexApiMaxEventReports() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_FLEX_API_MAX_EVENT_REPORTS, MEASUREMENT_FLEX_API_MAX_EVENT_REPORTS);
    }

    @Override
    public final int getMeasurementFlexApiMaxEventReportWindows() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_FLEX_API_MAX_EVENT_REPORT_WINDOWS,
                MEASUREMENT_FLEX_API_MAX_EVENT_REPORT_WINDOWS);
    }

    @Override
    public final int getMeasurementFlexApiMaxTriggerDataCardinality() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_FLEX_API_MAX_TRIGGER_DATA_CARDINALITY,
                MEASUREMENT_FLEX_API_MAX_TRIGGER_DATA_CARDINALITY);
    }

    @Override
    public final long getMeasurementMinimumEventReportWindowInSeconds() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MINIMUM_EVENT_REPORT_WINDOW_IN_SECONDS,
                MEASUREMENT_MINIMUM_EVENT_REPORT_WINDOW_IN_SECONDS);
    }

    @Override
    public final long getMeasurementMinimumAggregatableReportWindowInSeconds() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MINIMUM_AGGREGATABLE_REPORT_WINDOW_IN_SECONDS,
                MEASUREMENT_MINIMUM_AGGREGATABLE_REPORT_WINDOW_IN_SECONDS);
    }

    @Override
    public final int getMeasurementMaxSourcesPerPublisher() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_SOURCES_PER_PUBLISHER, MEASUREMENT_MAX_SOURCES_PER_PUBLISHER);
    }

    @Override
    public final int getMeasurementMaxTriggersPerDestination() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_TRIGGERS_PER_DESTINATION,
                MEASUREMENT_MAX_TRIGGERS_PER_DESTINATION);
    }

    @Override
    public final int getMeasurementMaxAggregateReportsPerDestination() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_DESTINATION,
                MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_DESTINATION);
    }

    @Override
    public final int getMeasurementMaxEventReportsPerDestination() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_EVENT_REPORTS_PER_DESTINATION,
                MEASUREMENT_MAX_EVENT_REPORTS_PER_DESTINATION);
    }

    @Override
    public final int getMeasurementMaxAggregateReportsPerSource() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_SOURCE,
                MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_SOURCE);
    }

    @Override
    public final boolean getMeasurementEnableUnboundedReportsWithTriggerContextId() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_UNBOUNDED_REPORTS_WITH_TRIGGER_CONTEXT_ID,
                MEASUREMENT_ENABLE_UNBOUNDED_REPORTS_WITH_TRIGGER_CONTEXT_ID);
    }

    @Override
    public final boolean getMeasurementEnableFifoDestinationsDeleteAggregateReports() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_FIFO_DESTINATIONS_DELETE_AGGREGATE_REPORTS,
                MEASUREMENT_ENABLE_FIFO_DESTINATIONS_DELETE_AGGREGATE_REPORTS);
    }

    @Override
    public final int getMeasurementMaxAggregateKeysPerSourceRegistration() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_AGGREGATE_KEYS_PER_SOURCE_REGISTRATION,
                MEASUREMENT_MAX_AGGREGATE_KEYS_PER_SOURCE_REGISTRATION);
    }

    @Override
    public final int getMeasurementMaxAggregateKeysPerTriggerRegistration() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_AGGREGATE_KEYS_PER_TRIGGER_REGISTRATION,
                MEASUREMENT_MAX_AGGREGATE_KEYS_PER_TRIGGER_REGISTRATION);
    }

    @Override
    public final String getMeasurementEventReportsVtcEarlyReportingWindows() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS,
                MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS);
    }

    @Override
    public final String getMeasurementEventReportsCtcEarlyReportingWindows() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS,
                MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS);
    }

    @Override
    public final String getMeasurementAggregateReportDelayConfig() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_AGGREGATE_REPORT_DELAY_CONFIG,
                MEASUREMENT_AGGREGATE_REPORT_DELAY_CONFIG);
    }

    @Override
    public final boolean getMeasurementEnableLookbackWindowFilter() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_LOOKBACK_WINDOW_FILTER,
                MEASUREMENT_ENABLE_LOOKBACK_WINDOW_FILTER);
    }

    @Override
    public final boolean getEnableLoggedTopic() {
        return mBackend.getFlag(KEY_ENABLE_LOGGED_TOPIC, ENABLE_LOGGED_TOPIC);
    }

    @Override
    public final boolean getEnableDatabaseSchemaVersion8() {
        return mBackend.getFlag(
                KEY_ENABLE_DATABASE_SCHEMA_VERSION_8, ENABLE_DATABASE_SCHEMA_VERSION_8);
    }

    @Override
    public final boolean getEnableDatabaseSchemaVersion9() {
        return mBackend.getFlag(
                KEY_ENABLE_DATABASE_SCHEMA_VERSION_9, ENABLE_DATABASE_SCHEMA_VERSION_9);
    }

    @Override
    public final boolean getMsmtEnableApiStatusAllowListCheck() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_API_STATUS_ALLOW_LIST_CHECK,
                MEASUREMENT_ENABLE_API_STATUS_ALLOW_LIST_CHECK);
    }

    @Override
    public final boolean getMeasurementEnableAttributionScope() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_ATTRIBUTION_SCOPE, MEASUREMENT_ENABLE_ATTRIBUTION_SCOPE);
    }

    @Override
    public final boolean getMeasurementEnableReinstallReattribution() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_REINSTALL_REATTRIBUTION,
                MEASUREMENT_ENABLE_REINSTALL_REATTRIBUTION);
    }

    @Override
    public final long getMeasurementMaxReinstallReattributionWindowSeconds() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_REINSTALL_REATTRIBUTION_WINDOW,
                MEASUREMENT_MAX_REINSTALL_REATTRIBUTION_WINDOW_SECONDS);
    }

    @Override
    public final boolean getMeasurementEnableMinReportLifespanForUninstall() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_MIN_REPORT_LIFESPAN_FOR_UNINSTALL,
                MEASUREMENT_ENABLE_MIN_REPORT_LIFESPAN_FOR_UNINSTALL);
    }

    @Override
    public final long getMeasurementMinReportLifespanForUninstallSeconds() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MIN_REPORT_LIFESPAN_FOR_UNINSTALL_SECONDS,
                MEASUREMENT_MIN_REPORT_LIFESPAN_FOR_UNINSTALL_SECONDS);
    }

    @Override
    public final boolean getMeasurementEnableInstallAttributionOnS() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_INSTALL_ATTRIBUTION_ON_S,
                MEASUREMENT_ENABLE_INSTALL_ATTRIBUTION_ON_S);
    }

    @Override
    public final boolean getMeasurementEnableNavigationReportingOriginCheck() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_NAVIGATION_REPORTING_ORIGIN_CHECK,
                MEASUREMENT_ENABLE_NAVIGATION_REPORTING_ORIGIN_CHECK);
    }

    @Override
    public final boolean getMeasurementEnableSeparateDebugReportTypesForAttributionRateLimit() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_SEPARATE_DEBUG_REPORT_TYPES_FOR_ATTRIBUTION_RATE_LIMIT,
                MEASUREMENT_ENABLE_SEPARATE_DEBUG_REPORT_TYPES_FOR_ATTRIBUTION_RATE_LIMIT);
    }

    @Override
    public final int getMeasurementMaxAttributionScopesPerSource() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_ATTRIBUTION_SCOPES_PER_SOURCE,
                MEASUREMENT_MAX_ATTRIBUTION_SCOPES_PER_SOURCE);
    }

    @Override
    public final int getMeasurementMaxAttributionScopeLength() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_ATTRIBUTION_SCOPE_LENGTH,
                MEASUREMENT_MAX_ATTRIBUTION_SCOPE_LENGTH);
    }

    @Override
    public final int getMeasurementMaxLengthPerBudgetName() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_LENGTH_PER_BUDGET_NAME, MEASUREMENT_MAX_LENGTH_PER_BUDGET_NAME);
    }

    @Override
    public final int getMeasurementMaxNamedBudgetsPerSourceRegistration() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_NAMED_BUDGETS_PER_SOURCE_REGISTRATION,
                MEASUREMENT_MAX_NAMED_BUDGETS_PER_SOURCE_REGISTRATION);
    }

    @Override
    public final boolean getFledgeMeasurementReportAndRegisterEventApiEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED,
                FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED);
    }

    @Override
    public final boolean getMeasurementEnableOdpWebTriggerRegistration() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_ODP_WEB_TRIGGER_REGISTRATION,
                MEASUREMENT_ENABLE_ODP_WEB_TRIGGER_REGISTRATION);
    }

    @Override
    public final boolean getMeasurementEnableSourceDestinationLimitPriority() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_SOURCE_DESTINATION_LIMIT_PRIORITY,
                MEASUREMENT_ENABLE_DESTINATION_LIMIT_PRIORITY);
    }

    @Override
    public final int getMeasurementDefaultSourceDestinationLimitAlgorithm() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_DEFAULT_DESTINATION_LIMIT_ALGORITHM,
                MEASUREMENT_DEFAULT_DESTINATION_LIMIT_ALGORITHM);
    }

    @Override
    public final boolean getMeasurementEnableSourceDestinationLimitAlgorithmField() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_DESTINATION_LIMIT_ALGORITHM_FIELD,
                MEASUREMENT_ENABLE_DESTINATION_LIMIT_ALGORITHM_FIELD);
    }

    @Override
    public final int getMeasurementDefaultFilteringIdMaxBytes() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES,
                MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES);
    }

    @Override
    public final int getMeasurementMaxFilteringIdMaxBytes() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_FILTERING_ID_MAX_BYTES, MEASUREMENT_MAX_FILTERING_ID_MAX_BYTES);
    }

    @Override
    public final boolean getMeasurementEnableFlexibleContributionFiltering() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_FLEXIBLE_CONTRIBUTION_FILTERING,
                MEASUREMENT_ENABLE_FLEXIBLE_CONTRIBUTION_FILTERING);
    }

    @Override
    public final String getAdServicesModuleJobPolicy() {
        return mBackend.getFlag(KEY_AD_SERVICES_MODULE_JOB_POLICY, AD_SERVICES_MODULE_JOB_POLICY);
    }

    @Override
    public final void dump(PrintWriter writer, String[] args) {
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

    // TODO(b/384798806): add method to get ImmutableList<String> on FlagBackend?
    @Override
    public final ImmutableList<String> getEnrollmentBlocklist() {
        String blocklistFlag = mBackend.getFlag(KEY_ENROLLMENT_BLOCKLIST_IDS, "");

        if (TextUtils.isEmpty(blocklistFlag)) {
            return ImmutableList.of();
        }
        String[] blocklistList = blocklistFlag.split(Constants.ARRAY_SPLITTER_COMMA);
        return ImmutableList.copyOf(blocklistList);
    }

    @Override
    public final boolean getCompatLoggingKillSwitch() {
        return mBackend.getFlag(KEY_COMPAT_LOGGING_KILL_SWITCH, COMPAT_LOGGING_KILL_SWITCH);
    }

    @Override
    public final boolean getEnableAppsearchConsentData() {
        return mBackend.getFlag(KEY_ENABLE_APPSEARCH_CONSENT_DATA, ENABLE_APPSEARCH_CONSENT_DATA);
    }

    @Override
    public final boolean getEnableU18AppsearchMigration() {
        return mBackend.getFlag(
                KEY_ENABLE_U18_APPSEARCH_MIGRATION, DEFAULT_ENABLE_U18_APPSEARCH_MIGRATION);
    }

    // TODO(b/384798806): add method to get ImmutableList<String> on FlagBackend?
    @Override
    public final ImmutableList<Integer> getGlobalBlockedTopicIds() {
        String defaultGlobalBlockedTopicIds =
                TOPICS_GLOBAL_BLOCKED_TOPIC_IDS.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(Constants.ARRAY_SPLITTER_COMMA));

        String globalBlockedTopicIds =
                mBackend.getFlag(KEY_GLOBAL_BLOCKED_TOPIC_IDS, defaultGlobalBlockedTopicIds);
        if (TextUtils.isEmpty(globalBlockedTopicIds)) {
            return ImmutableList.of();
        }
        globalBlockedTopicIds = globalBlockedTopicIds.trim();
        String[] globalBlockedTopicIdsList =
                globalBlockedTopicIds.split(Constants.ARRAY_SPLITTER_COMMA);

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

    // TODO(b/384798806): add method to get ImmutableList<String> on FlagBackend?
    @Override
    public final ImmutableList<Integer> getErrorCodeLoggingDenyList() {
        String defaultErrorCodeLoggingDenyStr =
                ERROR_CODE_LOGGING_DENY_LIST.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(Constants.ARRAY_SPLITTER_COMMA));

        String errorCodeLoggingDenyStr =
                mBackend.getFlag(KEY_ERROR_CODE_LOGGING_DENY_LIST, defaultErrorCodeLoggingDenyStr);
        if (TextUtils.isEmpty(errorCodeLoggingDenyStr)) {
            return ImmutableList.of();
        }
        errorCodeLoggingDenyStr = errorCodeLoggingDenyStr.trim();
        String[] errorCodeLoggingDenyStrList =
                errorCodeLoggingDenyStr.split(Constants.ARRAY_SPLITTER_COMMA);

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
    public final long getMeasurementDebugJoinKeyHashLimit() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_DEBUG_JOIN_KEY_HASH_LIMIT,
                DEFAULT_MEASUREMENT_DEBUG_JOIN_KEY_HASH_LIMIT);
    }

    @Override
    public final long getMeasurementPlatformDebugAdIdMatchingLimit() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_DEBUG_KEY_AD_ID_MATCHING_LIMIT,
                DEFAULT_MEASUREMENT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT);
    }

    @Override
    public final boolean getNotificationDismissedOnClick() {
        return mBackend.getFlag(
                KEY_NOTIFICATION_DISMISSED_ON_CLICK, DEFAULT_NOTIFICATION_DISMISSED_ON_CLICK);
    }

    @Override
    public final boolean getEnableBackCompatInit() {
        return mBackend.getFlag(KEY_ENABLE_BACK_COMPAT_INIT, DEFAULT_ENABLE_BACK_COMPAT_INIT);
    }

    @Override
    public final boolean getEnableAdServicesSystemApi() {
        return mBackend.getFlag(
                KEY_ENABLE_AD_SERVICES_SYSTEM_API, DEFAULT_ENABLE_AD_SERVICES_SYSTEM_API);
    }

    @Override
    public final boolean getEeaPasUxEnabled() {
        return mBackend.getFlag(KEY_EEA_PAS_UX_ENABLED, DEFAULT_EEA_PAS_UX_ENABLED);
    }

    @Override
    public final Map<String, Boolean> getUxFlags() {
        Map<String, Boolean> uxMap = new HashMap<>();
        uxMap.put(KEY_UI_DIALOGS_FEATURE_ENABLED, getUiDialogsFeatureEnabled());
        uxMap.put(KEY_UI_DIALOG_FRAGMENT_ENABLED, getUiDialogFragmentEnabled());
        uxMap.put(KEY_IS_EEA_DEVICE_FEATURE_ENABLED, isEeaDeviceFeatureEnabled());
        uxMap.put(KEY_IS_EEA_DEVICE, isEeaDevice());
        uxMap.put(KEY_RECORD_MANUAL_INTERACTION_ENABLED, getRecordManualInteractionEnabled());
        uxMap.put(KEY_GA_UX_FEATURE_ENABLED, getGaUxFeatureEnabled());
        uxMap.put(KEY_UI_OTA_STRINGS_FEATURE_ENABLED, getUiOtaStringsFeatureEnabled());
        uxMap.put(KEY_UI_OTA_RESOURCES_FEATURE_ENABLED, getUiOtaResourcesFeatureEnabled());
        uxMap.put(KEY_UI_FEATURE_TYPE_LOGGING_ENABLED, isUiFeatureTypeLoggingEnabled());
        uxMap.put(KEY_ADSERVICES_ENABLED, getAdServicesEnabled());
        uxMap.put(KEY_U18_UX_ENABLED, getU18UxEnabled());
        uxMap.put(KEY_NOTIFICATION_DISMISSED_ON_CLICK, getNotificationDismissedOnClick());
        uxMap.put(KEY_IS_U18_UX_DETENTION_CHANNEL_ENABLED, isU18UxDetentionChannelEnabled());
        uxMap.put(KEY_IS_U18_SUPERVISED_ACCOUNT_ENABLED, isU18SupervisedAccountEnabled());
        uxMap.put(
                KEY_CONSENT_ALREADY_INTERACTED_FIX_ENABLE, getConsentAlreadyInteractedEnableMode());
        uxMap.put(
                KEY_IS_GET_ADSERVICES_COMMON_STATES_API_ENABLED,
                isGetAdServicesCommonStatesApiEnabled());
        uxMap.put(KEY_PAS_UX_ENABLED, getPasUxEnabled());
        uxMap.put(KEY_EEA_PAS_UX_ENABLED, getEeaPasUxEnabled());
        return uxMap;
    }

    @Override
    public final boolean getMeasurementEnableCoarseEventReportDestinations() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_COARSE_EVENT_REPORT_DESTINATIONS,
                DEFAULT_MEASUREMENT_ENABLE_COARSE_EVENT_REPORT_DESTINATIONS);
    }

    @Override
    public final int getMeasurementMaxDistinctWebDestinationsInSourceRegistration() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_DISTINCT_WEB_DESTINATIONS_IN_SOURCE_REGISTRATION,
                MEASUREMENT_MAX_DISTINCT_WEB_DESTINATIONS_IN_SOURCE_REGISTRATION);
    }

    @Override
    public final long getMeasurementMaxReportingRegisterSourceExpirationInSeconds() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS,
                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
    }

    @Override
    public final long getMeasurementMinReportingRegisterSourceExpirationInSeconds() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS,
                MEASUREMENT_MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
    }

    @Override
    public final long getMeasurementMaxInstallAttributionWindow() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_INSTALL_ATTRIBUTION_WINDOW,
                MEASUREMENT_MAX_INSTALL_ATTRIBUTION_WINDOW);
    }

    @Override
    public final long getMeasurementMinInstallAttributionWindow() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MIN_INSTALL_ATTRIBUTION_WINDOW,
                MEASUREMENT_MIN_INSTALL_ATTRIBUTION_WINDOW);
    }

    @Override
    public final long getMeasurementMaxPostInstallExclusivityWindow() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_POST_INSTALL_EXCLUSIVITY_WINDOW,
                MEASUREMENT_MAX_POST_INSTALL_EXCLUSIVITY_WINDOW);
    }

    @Override
    public final long getMeasurementMinPostInstallExclusivityWindow() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MIN_POST_INSTALL_EXCLUSIVITY_WINDOW,
                MEASUREMENT_MIN_POST_INSTALL_EXCLUSIVITY_WINDOW);
    }

    @Override
    public final int getMeasurementMaxSumOfAggregateValuesPerSource() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE,
                MEASUREMENT_MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE);
    }

    @Override
    public final long getMeasurementRateLimitWindowMilliseconds() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return mBackend.getFlag(
                KEY_MEASUREMENT_RATE_LIMIT_WINDOW_MILLISECONDS,
                MEASUREMENT_RATE_LIMIT_WINDOW_MILLISECONDS);
    }

    @Override
    public final long getMeasurementMinReportingOriginUpdateWindow() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return mBackend.getFlag(
                KEY_MEASUREMENT_MIN_REPORTING_ORIGIN_UPDATE_WINDOW,
                MEASUREMENT_MIN_REPORTING_ORIGIN_UPDATE_WINDOW);
    }

    @Override
    public final boolean getMeasurementEnablePreinstallCheck() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_PREINSTALL_CHECK, MEASUREMENT_ENABLE_PREINSTALL_CHECK);
    }

    @Override
    public final int getMeasurementVtcConfigurableMaxEventReportsCount() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT,
                DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT);
    }

    @Override
    public final boolean getMeasurementEnableAraDeduplicationAlignmentV1() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_ARA_DEDUPLICATION_ALIGNMENT_V1,
                MEASUREMENT_ENABLE_ARA_DEDUPLICATION_ALIGNMENT_V1);
    }

    @Override
    public final boolean getMeasurementEnableSourceDeactivationAfterFiltering() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_SOURCE_DEACTIVATION_AFTER_FILTERING,
                MEASUREMENT_ENABLE_SOURCE_DEACTIVATION_AFTER_FILTERING);
    }

    @Override
    public final boolean getMeasurementEnableReportingJobsThrowUnaccountedException() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_UNACCOUNTED_EXCEPTION,
                MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_UNACCOUNTED_EXCEPTION);
    }

    @Override
    public final boolean getMeasurementEnableReportingJobsThrowJsonException() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_JSON_EXCEPTION,
                MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_JSON_EXCEPTION);
    }

    @Override
    public final boolean getMeasurementEnableReportDeletionOnUnrecoverableException() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_DELETE_REPORTS_ON_UNRECOVERABLE_EXCEPTION,
                MEASUREMENT_ENABLE_DELETE_REPORTS_ON_UNRECOVERABLE_EXCEPTION);
    }

    @Override
    public final boolean getMeasurementEnableReportingJobsThrowCryptoException() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_CRYPTO_EXCEPTION,
                MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_CRYPTO_EXCEPTION);
    }

    @Override
    public final boolean getMeasurementEnableDatastoreManagerThrowDatastoreException() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_DATASTORE_MANAGER_THROW_DATASTORE_EXCEPTION,
                MEASUREMENT_ENABLE_DATASTORE_MANAGER_THROW_DATASTORE_EXCEPTION);
    }

    @Override
    public final float getMeasurementThrowUnknownExceptionSamplingRate() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_THROW_UNKNOWN_EXCEPTION_SAMPLING_RATE,
                MEASUREMENT_THROW_UNKNOWN_EXCEPTION_SAMPLING_RATE);
    }

    @Override
    public final boolean getMeasurementDeleteUninstalledJobPersisted() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_DELETE_UNINSTALLED_JOB_PERSISTED,
                MEASUREMENT_DELETE_UNINSTALLED_JOB_PERSISTED);
    }

    @Override
    public final long getMeasurementDeleteUninstalledJobPeriodMs() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_DELETE_UNINSTALLED_JOB_PERIOD_MS,
                MEASUREMENT_DELETE_UNINSTALLED_JOB_PERIOD_MS);
    }

    @Override
    public final boolean getMeasurementDeleteExpiredJobPersisted() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_DELETE_EXPIRED_JOB_PERSISTED,
                MEASUREMENT_DELETE_EXPIRED_JOB_PERSISTED);
    }

    @Override
    public final boolean getMeasurementDeleteExpiredJobRequiresDeviceIdle() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_DELETE_EXPIRED_JOB_REQUIRES_DEVICE_IDLE,
                MEASUREMENT_DELETE_EXPIRED_JOB_REQUIRES_DEVICE_IDLE);
    }

    @Override
    public final long getMeasurementDeleteExpiredJobPeriodMs() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_DELETE_EXPIRED_JOB_PERIOD_MS,
                MEASUREMENT_DELETE_EXPIRED_JOB_PERIOD_MS);
    }

    @Override
    public final boolean getMeasurementEventReportingJobRequiredBatteryNotLow() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_EVENT_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW,
                MEASUREMENT_EVENT_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW);
    }

    @Override
    public final int getMeasurementEventReportingJobRequiredNetworkType() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_EVENT_REPORTING_JOB_REQUIRED_NETWORK_TYPE,
                MEASUREMENT_EVENT_REPORTING_JOB_REQUIRED_NETWORK_TYPE);
    }

    @Override
    public final boolean getMeasurementEventReportingJobPersisted() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_EVENT_REPORTING_JOB_PERSISTED,
                MEASUREMENT_EVENT_REPORTING_JOB_PERSISTED);
    }

    @Override
    public final boolean getMeasurementEnableTriggerDebugSignal() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_TRIGGER_DEBUG_SIGNAL,
                MEASUREMENT_ENABLE_TRIGGER_DEBUG_SIGNAL);
    }

    @Override
    public final boolean getMeasurementEnableEventTriggerDebugSignalForCoarseDestination() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_EVENT_TRIGGER_DEBUG_SIGNAL_FOR_COARSE_DESTINATION,
                MEASUREMENT_ENABLE_EVENT_TRIGGER_DEBUG_SIGNAL_FOR_COARSE_DESTINATION);
    }

    @Override
    public final float getMeasurementTriggerDebugSignalProbabilityForFakeReports() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_TRIGGER_DEBUG_SIGNAL_PROBABILITY_FOR_FAKE_REPORTS,
                MEASUREMENT_TRIGGER_DEBUG_SIGNAL_PROBABILITY_FOR_FAKE_REPORTS);
    }

    @Override
    public final boolean getMeasurementEventFallbackReportingJobRequiredBatteryNotLow() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW,
                MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW);
    }

    @Override
    public final int getMeasurementEventFallbackReportingJobRequiredNetworkType() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_REQUIRED_NETWORK_TYPE,
                MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_REQUIRED_NETWORK_TYPE);
    }

    @Override
    public final boolean getMeasurementEventFallbackReportingJobPersisted() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERSISTED,
                MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERSISTED);
    }

    @Override
    public final int getMeasurementDebugReportingJobRequiredNetworkType() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_DEBUG_REPORTING_JOB_REQUIRED_NETWORK_TYPE,
                MEASUREMENT_DEBUG_REPORTING_JOB_REQUIRED_NETWORK_TYPE);
    }

    @Override
    public final int getMeasurementDebugReportingFallbackJobRequiredNetworkType() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_REQUIRED_NETWORK_TYPE,
                MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_REQUIRED_NETWORK_TYPE);
    }

    @Override
    public final boolean getMeasurementDebugReportingFallbackJobPersisted() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_PERSISTED,
                MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_PERSISTED);
    }

    @Override
    public final int getMeasurementVerboseDebugReportingJobRequiredNetworkType() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_VERBOSE_DEBUG_REPORTING_JOB_REQUIRED_NETWORK_TYPE,
                MEASUREMENT_VERBOSE_DEBUG_REPORTING_JOB_REQUIRED_NETWORK_TYPE);
    }

    @Override
    public final boolean getMeasurementVerboseDebugReportingFallbackJobPersisted() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_PERSISTED,
                MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_PERSISTED);
    }

    @Override
    public final boolean getMeasurementAttributionJobPersisted() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ATTRIBUTION_JOB_PERSISTED, MEASUREMENT_ATTRIBUTION_JOB_PERSISTED);
    }

    @Override
    public final boolean getMeasurementAttributionFallbackJobPersisted() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_PERSISTED,
                MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_PERSISTED);
    }

    @Override
    public final long getMeasurementAttributionJobTriggeringDelayMs() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ATTRIBUTION_JOB_TRIGGERING_DELAY_MS,
                MEASUREMENT_ATTRIBUTION_JOB_TRIGGERING_DELAY_MS);
    }

    @Override
    public final int getMeasurementAsyncRegistrationQueueJobRequiredNetworkType() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ASYNC_REGISTRATION_QUEUE_JOB_REQUIRED_NETWORK_TYPE,
                MEASUREMENT_ASYNC_REGISTRATION_QUEUE_JOB_REQUIRED_NETWORK_TYPE);
    }

    @Override
    public final boolean getMeasurementAsyncRegistrationQueueJobPersisted() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ASYNC_REGISTRATION_QUEUE_JOB_PERSISTED,
                MEASUREMENT_ASYNC_REGISTRATION_QUEUE_JOB_PERSISTED);
    }

    @Override
    public final boolean getMeasurementAsyncRegistrationFallbackJobRequiredBatteryNotLow() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_REQUIRED_BATTERY_NOT_LOW,
                MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_REQUIRED_BATTERY_NOT_LOW);
    }

    @Override
    public final int getMeasurementAsyncRegistrationFallbackJobRequiredNetworkType() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_REQUIRED_NETWORK_TYPE,
                MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_REQUIRED_NETWORK_TYPE);
    }

    @Override
    public final boolean getMeasurementAsyncRegistrationFallbackJobPersisted() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_PERSISTED,
                MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_PERSISTED);
    }

    @Override
    public final boolean getMeasurementAggregateReportingJobRequiredBatteryNotLow() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_AGGREGATE_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW,
                MEASUREMENT_AGGREGATE_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW);
    }

    @Override
    public final int getMeasurementAggregateReportingJobRequiredNetworkType() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_AGGREGATE_REPORTING_JOB_REQUIRED_NETWORK_TYPE,
                MEASUREMENT_AGGREGATE_REPORTING_JOB_REQUIRED_NETWORK_TYPE);
    }

    @Override
    public final boolean getMeasurementAggregateReportingJobPersisted() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_AGGREGATE_REPORTING_JOB_PERSISTED,
                MEASUREMENT_AGGREGATE_REPORTING_JOB_PERSISTED);
    }

    @Override
    public final boolean getMeasurementAggregateFallbackReportingJobRequiredBatteryNotLow() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW,
                MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW);
    }

    @Override
    public final int getMeasurementAggregateFallbackReportingJobRequiredNetworkType() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_REQUIRED_NETWORK_TYPE,
                MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_REQUIRED_NETWORK_TYPE);
    }

    @Override
    public final boolean getMeasurementAggregateFallbackReportingJobPersisted() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERSISTED,
                MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERSISTED);
    }

    @Override
    public final boolean getMeasurementImmediateAggregateReportingJobRequiredBatteryNotLow() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW,
                MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW);
    }

    @Override
    public final int getMeasurementImmediateAggregateReportingJobRequiredNetworkType() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_REQUIRED_NETWORK_TYPE,
                MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_REQUIRED_NETWORK_TYPE);
    }

    @Override
    public final boolean getMeasurementImmediateAggregateReportingJobPersisted() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_PERSISTED,
                MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_PERSISTED);
    }

    @Override
    public final boolean getMeasurementReportingJobRequiredBatteryNotLow() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW,
                MEASUREMENT_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW);
    }

    @Override
    public final int getMeasurementReportingJobRequiredNetworkType() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_REPORTING_JOB_REQUIRED_NETWORK_TYPE,
                MEASUREMENT_REPORTING_JOB_REQUIRED_NETWORK_TYPE);
    }

    @Override
    public final boolean getMeasurementReportingJobPersisted() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_REPORTING_JOB_PERSISTED, MEASUREMENT_REPORTING_JOB_PERSISTED);
    }

    @Override
    public final boolean getAdservicesConsentMigrationLoggingEnabled() {
        return mBackend.getFlag(
                KEY_ADSERVICES_CONSENT_MIGRATION_LOGGING_ENABLED,
                DEFAULT_ADSERVICES_CONSENT_MIGRATION_LOGGING_ENABLED);
    }

    @Override
    public final boolean isU18UxDetentionChannelEnabled() {
        return mBackend.getFlag(
                KEY_IS_U18_UX_DETENTION_CHANNEL_ENABLED,
                IS_U18_UX_DETENTION_CHANNEL_ENABLED_DEFAULT);
    }

    @Override
    public final boolean getMeasurementEnableAppPackageNameLogging() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_APP_PACKAGE_NAME_LOGGING,
                MEASUREMENT_ENABLE_APP_PACKAGE_NAME_LOGGING);
    }

    @Override
    public final long getMeasurementDebugReportingFallbackJobPeriodMs() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_PERIOD_MS,
                MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_PERIOD_MS);
    }

    @Override
    public final long getMeasurementVerboseDebugReportingFallbackJobPeriodMs() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_PERIOD_MS,
                MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_PERIOD_MS);
    }

    @Override
    public final float getMeasurementPrivacyEpsilon() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_EVENT_API_DEFAULT_EPSILON, DEFAULT_MEASUREMENT_PRIVACY_EPSILON);
    }

    @Override
    public final boolean getMeasurementEnableEventLevelEpsilonInSource() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_EVENT_LEVEL_EPSILON_IN_SOURCE,
                MEASUREMENT_ENABLE_EVENT_LEVEL_EPSILON_IN_SOURCE);
    }

    @Override
    public final boolean getMeasurementEnableAggregateValueFilters() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_AGGREGATE_VALUE_FILTERS,
                MEASUREMENT_ENABLE_AGGREGATE_VALUE_FILTERS);
    }

    @Override
    public final String getMeasurementAppPackageNameLoggingAllowlist() {
        return mBackend.getFlag(KEY_MEASUREMENT_APP_PACKAGE_NAME_LOGGING_ALLOWLIST, "");
    }

    @Override
    public final boolean isU18SupervisedAccountEnabled() {
        return mBackend.getFlag(
                KEY_IS_U18_SUPERVISED_ACCOUNT_ENABLED, IS_U18_SUPERVISED_ACCOUNT_ENABLED_DEFAULT);
    }

    @Override
    public final long getAdIdFetcherTimeoutMs() {
        return mBackend.getFlag(KEY_AD_ID_FETCHER_TIMEOUT_MS, DEFAULT_AD_ID_FETCHER_TIMEOUT_MS);
    }

    @Override
    public final float getMeasurementNullAggReportRateInclSourceRegistrationTime() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_NULL_AGG_REPORT_RATE_INCL_SOURCE_REGISTRATION_TIME,
                MEASUREMENT_NULL_AGG_REPORT_RATE_INCL_SOURCE_REGISTRATION_TIME);
    }

    @Override
    public final float getMeasurementNullAggReportRateExclSourceRegistrationTime() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_NULL_AGG_REPORT_RATE_EXCL_SOURCE_REGISTRATION_TIME,
                MEASUREMENT_NULL_AGG_REPORT_RATE_EXCL_SOURCE_REGISTRATION_TIME);
    }

    @Override
    public final int getMeasurementMaxLengthOfTriggerContextId() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_LENGTH_OF_TRIGGER_CONTEXT_ID,
                MEASUREMENT_MAX_LENGTH_OF_TRIGGER_CONTEXT_ID);
    }

    @Override
    public final boolean getMeasurementEnableSessionStableKillSwitches() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_SESSION_STABLE_KILL_SWITCHES,
                MEASUREMENT_ENABLE_SESSION_STABLE_KILL_SWITCHES);
    }

    @Override
    public final boolean getMeasurementEnableAggregateDebugReporting() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_AGGREGATE_DEBUG_REPORTING,
                MEASUREMENT_ENABLE_AGGREGATE_DEBUG_REPORTING);
    }

    @Override
    public final int getMeasurementAdrBudgetOriginXPublisherXWindow() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ADR_BUDGET_PER_ORIGIN_PUBLISHER_WINDOW,
                MEASUREMENT_ADR_BUDGET_PER_ORIGIN_PUBLISHER_WINDOW);
    }

    @Override
    public final int getMeasurementAdrBudgetPublisherXWindow() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ADR_BUDGET_PER_PUBLISHER_WINDOW,
                MEASUREMENT_ADR_BUDGET_PER_PUBLISHER_WINDOW);
    }

    @Override
    public final long getMeasurementAdrBudgetWindowLengthMillis() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ADR_BUDGET_WINDOW_LENGTH_MS,
                MEASUREMENT_ADR_BUDGET_WINDOW_LENGTH_MILLIS);
    }

    @Override
    public final int getMeasurementMaxAdrCountPerSource() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_MAX_ADR_COUNT_PER_SOURCE, MEASUREMENT_MAX_ADR_COUNT_PER_SOURCE);
    }

    @Override
    public final boolean getMeasurementEnableBothSideDebugKeysInReports() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_BOTH_SIDE_DEBUG_KEYS_IN_REPORTS,
                MEASUREMENT_ENABLE_BOTH_SIDE_DEBUG_KEYS_IN_REPORTS);
    }

    @Override
    public final long getMeasurementReportingJobServiceBatchWindowMillis() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS,
                MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS);
    }

    @Override
    public final long getMeasurementReportingJobServiceMinExecutionWindowMillis() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_REPORTING_JOB_SERVICE_MIN_EXECUTION_WINDOW_MILLIS,
                MEASUREMENT_REPORTING_JOB_SERVICE_MIN_EXECUTION_WINDOW_MILLIS);
    }

    @Override
    public final boolean getEnableAdExtDataServiceApis() {
        return mBackend.getFlag(
                KEY_ENABLE_ADEXT_DATA_SERVICE_APIS, DEFAULT_ENABLE_ADEXT_DATA_SERVICE_APIS);
    }

    @Override
    public final int getAppSearchWriteTimeout() {
        return mBackend.getFlag(KEY_APPSEARCH_WRITE_TIMEOUT_MS, DEFAULT_APPSEARCH_WRITE_TIMEOUT_MS);
    }

    @Override
    public final int getAppSearchReadTimeout() {
        return mBackend.getFlag(KEY_APPSEARCH_READ_TIMEOUT_MS, DEFAULT_APPSEARCH_READ_TIMEOUT_MS);
    }

    @Override
    public final boolean isGetAdServicesCommonStatesApiEnabled() {
        return mBackend.getFlag(
                KEY_IS_GET_ADSERVICES_COMMON_STATES_API_ENABLED,
                DEFAULT_IS_GET_ADSERVICES_COMMON_STATES_API_ENABLED);
    }

    @Override
    public final String getFledgeKAnonFetchServerParamsUrl() {
        return mBackend.getFlag(
                KEY_KANON_FETCH_PARAMETERS_URL, FLEDGE_DEFAULT_KANON_FETCH_SERVER_PARAMS_URL);
    }

    @Override
    public final String getFledgeKAnonGetChallengeUrl() {
        return mBackend.getFlag(KEY_ANON_GET_CHALLENGE_URL, FLEDGE_DEFAULT_GET_CHALLENGE_URL);
    }

    @Override
    public final String getFledgeKAnonRegisterClientParametersUrl() {
        return mBackend.getFlag(
                KEY_FLEDGE_KANON_REGISTER_CLIENT_PARAMETERS_URL,
                FLEDGE_DEFAULT_KANON_REGISTER_CLIENT_PARAMETERS_URL);
    }

    @Override
    public final String getFledgeKAnonGetTokensUrl() {
        return mBackend.getFlag(
                KEY_FLEDGE_KANON_GET_TOKENS_URL, FLEDGE_DEFAULT_KANON_GET_TOKENS_URL);
    }

    @Override
    public final String getFledgeKAnonJoinUrl() {
        return mBackend.getFlag(KEY_FLEDGE_KANON_JOIN_URL, FLEDGE_DEFAULT_KANON_JOIN_URL);
    }

    @Override
    public final int getFledgeKAnonSignBatchSize() {
        return mBackend.getFlag(
                KEY_FLEDGE_KANON_SIGN_BATCH_SIZE, FLEDGE_DEFAULT_KANON_SIGN_BATCH_SIZE);
    }

    @Override
    public final int getFledgeKAnonPercentageImmediateSignJoinCalls() {
        return mBackend.getFlag(
                KEY_FLEDGE_KANON_PERCENTAGE_IMMEDIATE_SIGN_JOIN_CALLS,
                FLEDGE_DEFAULT_KANON_PERCENTAGE_IMMEDIATE_SIGN_JOIN_CALLS);
    }

    @Override
    public final long getFledgeKAnonMessageTtlSeconds() {
        return mBackend.getFlag(
                KEY_FLEDGE_KANON_MESSAGE_TTL_SECONDS, FLEDGE_DEFAULT_KANON_MESSAGE_TTL_SECONDS);
    }

    @Override
    public final long getFledgeKAnonBackgroundProcessTimePeriodInMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_KANON_BACKGROUND_TIME_PERIOD_IN_MS,
                FLEDGE_DEFAULT_KANON_BACKGROUND_JOB_TIME_PERIOD_MS);
    }

    @Override
    public final int getFledgeKAnonMessagesPerBackgroundProcess() {
        return mBackend.getFlag(
                KEY_FLEDGE_KANON_NUMBER_OF_MESSAGES_PER_BACKGROUND_PROCESS,
                FLEDGE_DEFAULT_KANON_NUMBER_OF_MESSAGES_PER_BACKGROUND_PROCESS);
    }

    @Override
    public final String getAdServicesCommonStatesAllowList() {
        return mBackend.getFlag(
                KEY_GET_ADSERVICES_COMMON_STATES_ALLOW_LIST,
                GET_ADSERVICES_COMMON_STATES_ALLOW_LIST);
    }

    @Override
    public final String getFledgeKAnonSetTypeToSignJoin() {
        return mBackend.getFlag(
                KEY_FLEDGE_KANON_SET_TYPE_TO_SIGN_JOIN, FLEDGE_DEFAULT_KANON_SET_TYPE_TO_SIGN_JOIN);
    }

    @Override
    public final String getFledgeKAnonUrlAuthorityToJoin() {
        return mBackend.getFlag(
                KEY_FLEDGE_KANON_JOIN_URL_AUTHORIY, FLEDGE_DEFAULT_KANON_AUTHORIY_URL_JOIN);
    }

    @Override
    public final int getFledgeKanonHttpClientTimeoutInMs() {
        return mBackend.getFlag(
                KEY_FLEDGE_KANON_HTTP_CLIENT_TIMEOUT,
                FLEDGE_DEFAULT_KANON_HTTP_CLIENT_TIMEOUT_IN_MS);
    }

    @Override
    public final boolean getFledgeKAnonBackgroundJobRequiresBatteryNotLow() {
        return mBackend.getFlag(
                KEY_FLEDGE_KANON_BACKGROUND_JOB_REQUIRES_BATTERY_NOT_LOW,
                FLEDGE_DEFAULT_KANON_BACKGROUND_JOB_REQUIRES_BATTERY_NOT_LOW);
    }

    @Override
    public final boolean getFledgeKAnonBackgroundJobRequiresDeviceIdle() {
        return mBackend.getFlag(
                KEY_FLEDGE_KANON_BACKGROUND_JOB_REQUIRES_DEVICE_IDLE,
                FLEDGE_DEFAULT_KANON_BACKGROUND_JOB_REQUIRES_DEVICE_IDLE);
    }

    @Override
    public final int getFledgeKanonBackgroundJobConnectionType() {
        return mBackend.getFlag(
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
    public final boolean getBackgroundJobsLoggingEnabled() {
        return true;
    }

    @Override
    public final boolean getAdServicesRetryStrategyEnabled() {
        return mBackend.getFlag(
                KEY_AD_SERVICES_RETRY_STRATEGY_ENABLED, DEFAULT_AD_SERVICES_RETRY_STRATEGY_ENABLED);
    }

    @Override
    public final int getAdServicesJsScriptEngineMaxRetryAttempts() {
        return mBackend.getFlag(
                KEY_AD_SERVICES_JS_SCRIPT_ENGINE_MAX_RETRY_ATTEMPTS,
                DEFAULT_AD_SERVICES_JS_SCRIPT_ENGINE_MAX_RETRY_ATTEMPTS);
    }

    @Override
    public final boolean getEnableConsentManagerV2() {
        return mBackend.getFlag(KEY_ENABLE_CONSENT_MANAGER_V2, DEFAULT_ENABLE_CONSENT_MANAGER_V2);
    }

    @Override
    public final boolean getPasExtendedMetricsEnabled() {
        return mBackend.getFlag(KEY_PAS_EXTENDED_METRICS_ENABLED, PAS_EXTENDED_METRICS_ENABLED);
    }

    @Override
    public final boolean getPasProductMetricsV1Enabled() {
        return mBackend.getFlag(KEY_PAS_PRODUCT_METRICS_V1_ENABLED, PAS_PRODUCT_METRICS_V1_ENABLED);
    }

    @Override
    public final boolean getSpeOnPilotJobsEnabled() {
        return mBackend.getFlag(KEY_SPE_ON_PILOT_JOBS_ENABLED, DEFAULT_SPE_ON_PILOT_JOBS_ENABLED);
    }

    @Override
    public final boolean getEnrollmentApiBasedSchemaEnabled() {
        return mBackend.getFlag(
                KEY_ENROLLMENT_API_BASED_SCHEMA_ENABLED, ENROLLMENT_API_BASED_SCHEMA_ENABLED);
    }

    @Override
    public final boolean getEnableEnrollmentConfigV3Db() {
        return mBackend.getFlag(
                KEY_CONFIG_DELIVERY__ENABLE_ENROLLMENT_CONFIG_V3_DB,
                DEFAULT_ENABLE_ENROLLMENT_CONFIG_V3_DB);
    }

    @Override
    public final boolean getUseConfigsManagerToQueryEnrollment() {
        return mBackend.getFlag(
                KEY_CONFIG_DELIVERY__USE_CONFIGS_MANAGER_TO_QUERY_ENROLLMENT,
                DEFAULT_USE_CONFIGS_MANAGER_TO_QUERY_ENROLLMENT);
    }

    @Override
    public final String getConfigDeliveryMddManifestUrls() {
        return mBackend.getFlag(
                KEY_CONFIG_DELIVERY__MDD_MANIFEST_URLS, DEFAULT_CONFIG_DELIVERY__MDD_MANIFEST_URLS);
    }

    @Override
    public final boolean getSharedDatabaseSchemaVersion4Enabled() {
        return mBackend.getFlag(
                KEY_SHARED_DATABASE_SCHEMA_VERSION_4_ENABLED,
                SHARED_DATABASE_SCHEMA_VERSION_4_ENABLED);
    }

    @Override
    public final boolean getJobSchedulingLoggingEnabled() {
        return mBackend.getFlag(
                KEY_JOB_SCHEDULING_LOGGING_ENABLED, DEFAULT_JOB_SCHEDULING_LOGGING_ENABLED);
    }

    @Override
    public final int getJobSchedulingLoggingSamplingRate() {
        return mBackend.getFlag(
                KEY_JOB_SCHEDULING_LOGGING_SAMPLING_RATE,
                DEFAULT_JOB_SCHEDULING_LOGGING_SAMPLING_RATE);
    }

    @Override
    public final boolean getEnableTabletRegionFix() {
        return mBackend.getFlag(KEY_ENABLE_TABLET_REGION_FIX, DEFAULT_ENABLE_TABLET_REGION_FIX);
    }

    @Override
    public final String getEncodedErrorCodeListPerSampleInterval() {
        return mBackend.getFlag(
                KEY_ENCODED_ERROR_CODE_LIST_PER_SAMPLE_INTERVAL,
                ENCODED_ERROR_CODE_LIST_PER_SAMPLE_INTERVAL);
    }

    @Override
    public final boolean getCustomErrorCodeSamplingEnabled() {
        return mBackend.getFlag(
                KEY_CUSTOM_ERROR_CODE_SAMPLING_ENABLED, DEFAULT_CUSTOM_ERROR_CODE_SAMPLING_ENABLED);
    }

    @Override
    public final int getPasScriptDownloadReadTimeoutMs() {
        return mBackend.getFlag(
                KEY_PAS_SCRIPT_DOWNLOAD_READ_TIMEOUT_MS,
                DEFAULT_PAS_SCRIPT_DOWNLOAD_READ_TIMEOUT_MS);
    }

    @Override
    public final int getPasScriptDownloadConnectionTimeoutMs() {
        return mBackend.getFlag(
                KEY_PAS_SCRIPT_DOWNLOAD_CONNECTION_TIMEOUT_MS,
                DEFAULT_PAS_SCRIPT_DOWNLOAD_CONNECTION_TIMEOUT_MS);
    }

    @Override
    public final int getPasSignalsDownloadReadTimeoutMs() {
        return mBackend.getFlag(
                KEY_PAS_SIGNALS_DOWNLOAD_READ_TIMEOUT_MS,
                DEFAULT_PAS_SIGNALS_DOWNLOAD_READ_TIMEOUT_MS);
    }

    @Override
    public final int getPasSignalsDownloadConnectionTimeoutMs() {
        return mBackend.getFlag(
                KEY_PAS_SIGNALS_DOWNLOAD_CONNECTION_TIMEOUT_MS,
                DEFAULT_PAS_SIGNALS_DOWNLOAD_CONNECTION_TIMEOUT_MS);
    }

    @Override
    public final int getPasScriptExecutionTimeoutMs() {
        return mBackend.getFlag(
                KEY_PAS_SCRIPT_EXECUTION_TIMEOUT_MS, DEFAULT_PAS_SCRIPT_EXECUTION_TIMEOUT_MS);
    }

    @Override
    public final boolean getSpeOnPilotJobsBatch2Enabled() {
        return mBackend.getFlag(
                KEY_SPE_ON_PILOT_JOBS_BATCH_2_ENABLED, DEFAULT_SPE_ON_PILOT_JOBS_BATCH_2_ENABLED);
    }

    @Override
    public final boolean getSpeOnEpochJobEnabled() {
        return mBackend.getFlag(KEY_SPE_ON_EPOCH_JOB_ENABLED, DEFAULT_SPE_ON_EPOCH_JOB_ENABLED);
    }

    @Override
    public final boolean getSpeOnBackgroundFetchJobEnabled() {
        return mBackend.getFlag(
                KEY_SPE_ON_BACKGROUND_FETCH_JOB_ENABLED,
                DEFAULT_SPE_ON_BACKGROUND_FETCH_JOB_ENABLED);
    }

    @Override
    public final boolean getSpeOnAsyncRegistrationFallbackJobEnabled() {
        return mBackend.getFlag(
                KEY_SPE_ON_ASYNC_REGISTRATION_FALLBACK_JOB_ENABLED,
                DEFAULT_SPE_ON_ASYNC_REGISTRATION_FALLBACK_JOB_ENABLED);
    }

    @Override
    public final boolean getAdServicesConsentBusinessLogicMigrationEnabled() {
        return mBackend.getFlag(
                KEY_ADSERVICES_CONSENT_BUSINESS_LOGIC_MIGRATION_ENABLED,
                DEFAULT_ADSERVICES_CONSENT_BUSINESS_LOGIC_MIGRATION_ENABLED);
    }

    @Override
    public final String getMddEnrollmentManifestFileUrl() {
        return mBackend.getFlag(
                KEY_MDD_ENROLLMENT_MANIFEST_FILE_URL, MDD_DEFAULT_ENROLLMENT_MANIFEST_FILE_URL);
    }

    @Override
    public final boolean getEnrollmentProtoFileEnabled() {
        return mBackend.getFlag(
                KEY_ENROLLMENT_PROTO_FILE_ENABLED, DEFAULT_ENROLLMENT_PROTO_FILE_ENABLED);
    }

    @Override
    public final boolean getRNotificationDefaultConsentFixEnabled() {
        return mBackend.getFlag(
                KEY_R_NOTIFICATION_DEFAULT_CONSENT_FIX_ENABLED,
                DEFAULT_R_NOTIFICATION_DEFAULT_CONSENT_FIX_ENABLED);
    }

    @Override
    public final boolean getPasEncodingJobImprovementsEnabled() {
        return mBackend.getFlag(
                KEY_PAS_ENCODING_JOB_IMPROVEMENTS_ENABLED, PAS_ENCODING_JOB_IMPROVEMENTS_ENABLED);
    }

    @Override
    public final long getAdIdCacheTtlMs() {
        return mBackend.getFlag(KEY_AD_ID_CACHE_TTL_MS, DEFAULT_ADID_CACHE_TTL_MS);
    }

    @Override
    public final boolean getEnablePackageDenyService() {
        return mBackend.getFlag(
                KEY_PACKAGE_DENY__ENABLE_PACKAGE_DENY_SERVICE, DEFAULT_ENABLE_PACKAGE_DENY_SERVICE);
    }

    @Override
    public final boolean getEnablePackageDenyMdd() {
        return mBackend.getFlag(
                KEY_PACKAGE_DENY__ENABLE_PACKAGE_DENY_MDD, DEFAULT_ENABLE_PACKAGE_DENY_MDD);
    }

    @Override
    public final boolean getEnablePackageDenyJobOnPackageAdd() {
        return mBackend.getFlag(
                KEY_PACKAGE_DENY__ENABLE_PACKAGE_DENY_JOB_ON_PACKAGE_ADD,
                DEFAULT_ENABLE_PACKAGE_DENY_JOB_ON_PACKAGE_ADD);
    }

    @Override
    public final boolean getEnablePackageDenyBgJob() {
        return mBackend.getFlag(
                KEY_PACKAGE_DENY__ENABLE_PACKAGE_DENY_BG_JOB, DEFAULT_ENABLE_PACKAGE_DENY_BG_JOB);
    }

    @Override
    public final boolean getPackageDenyEnableInstalledPackageFilter() {
        return mBackend.getFlag(
                KEY_PACKAGE_DENY_ENABLE_INSTALLED_PACKAGE_FILTER,
                DEFAULT_PACKAGE_DENY_ENABLE_INSTALLED_PACKAGE_FILTER);
    }

    @Override
    public final long getPackageDenyBackgroundJobPeriodMillis() {
        return mBackend.getFlag(
                KEY_PACKAGE_DENY_BACKGROUND_JOB_PERIOD_MILLIS,
                DEFAULT_PACKAGE_DENY_BACKGROUND_JOB_PERIOD_MILLIS);
    }

    @Override
    public final boolean getEnablePackageDenyJobOnMddDownload() {
        return mBackend.getFlag(
                KEY_PACKAGE_DENY__ENABLE_PACKAGE_DENY_JOB_ON_MDD_DOWNLOAD,
                DEFAULT_ENABLE_PACKAGE_DENY_JOB_ON_MDD_DOWNLOAD);
    }

    @Override
    public final String getMddPackageDenyRegistryManifestFileUrl() {
        return mBackend.getFlag(
                KEY_MDD_PACKAGE_DENY_REGISTRY_MANIFEST_FILE_URL,
                DEFAULT_MDD_PACKAGE_DENY_REGISTRY_MANIFEST_FILE_URL);
    }

    @Override
    public final boolean getEnableAtomicFileDatastoreBatchUpdateApi() {
        return mBackend.getFlag(
                KEY_ENABLE_ATOMIC_FILE_DATASTORE_BATCH_UPDATE_API,
                DEFAULT_ENABLE_ATOMIC_FILE_DATASTORE_BATCH_UPDATE_API);
    }

    @Override
    public final boolean getAdIdMigrationEnabled() {
        return mBackend.getFlag(KEY_AD_ID_MIGRATION_ENABLED, DEFAULT_AD_ID_MIGRATION_ENABLED);
    }

    @Override
    public final boolean getEnableReportEventForComponentSeller() {
        return mBackend.getFlag(
                KEY_FLEDGE_ENABLE_REPORT_EVENT_FOR_COMPONENT_SELLER,
                DEFAULT_ENABLE_REPORT_EVENT_FOR_COMPONENT_SELLER);
    }

    @Override
    public final boolean getEnableWinningSellerIdInAdSelectionOutcome() {
        return mBackend.getFlag(
                KEY_FLEDGE_ENABLE_WINNING_SELLER_ID_IN_AD_SELECTION_OUTCOME,
                DEFAULT_ENABLE_WINNING_SELLER_ID_IN_AD_SELECTION_OUTCOME);
    }

    @Override
    public final boolean getEnableProdDebugInAuctionServer() {
        return mBackend.getFlag(
                KEY_FLEDGE_ENABLE_PROD_DEBUG_IN_SERVER_AUCTION,
                DEFAULT_PROD_DEBUG_IN_AUCTION_SERVER);
    }

    @Override
    public final boolean getEnableRbAtrace() {
        return mBackend.getFlag(KEY_ENABLE_RB_ATRACE, DEFAULT_ENABLE_RB_ATRACE);
    }

    @Override
    public boolean getEnableMsmtRegisterSourcePackageDenyList() {
        return mBackend.getFlag(
                KEY_MSMT_REGISTER_SOURCE_PACKAGE_DENY_LIST,
                DEFAULT_MSMT_REGISTER_SOURCE_PACKAGE_DENY_LIST);
    }

    @Override
    public boolean getMeasurementEnablePackageNameUidCheck() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ENABLE_PACKAGE_NAME_UID_CHECK,
                DEFAULT_MEASUREMENT_ENABLE_PACKAGE_NAME_UID_CHECK);
    }
}
