/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.adservices.shared.testing.flags.TestableFlagsBackend;

// NOTE: in theory we could merge it with FakeFlags, but they're split because in the long term
// this class should be gone (if no flags add extra validation) or auto-generated, while FakeFlags
// would still be needed.
/**
 * Provides all methods from {@code Flags} that are not implemented by {@link RawFlags} (because the
 * "real" implementation on {@code SmartFlags} / {@code PhFlags} adds validation logic).
 *
 * @param <FB> flags backend
 */
class RawFlagsForTests<FB extends TestableFlagsBackend> extends RawFlags<FB>
        implements TestableFlags {

    RawFlagsForTests(FB backend) {
        super(backend);
    }

    @Override
    public final FB getBackend() {
        return mBackend;
    }

    @Override
    public final long getTopicsEpochJobPeriodMs() {
        return mBackend.getFlag(KEY_TOPICS_EPOCH_JOB_PERIOD_MS, TOPICS_EPOCH_JOB_PERIOD_MS);
    }

    @Override
    public final long getTopicsEpochJobFlexMs() {
        return mBackend.getFlag(KEY_TOPICS_EPOCH_JOB_FLEX_MS, TOPICS_EPOCH_JOB_FLEX_MS);
    }

    @Override
    public final int getTopicsPercentageForRandomTopic() {
        return mBackend.getFlag(
                KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC, TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC);
    }

    @Override
    public final int getTopicsNumberOfTopTopics() {
        return mBackend.getFlag(KEY_TOPICS_NUMBER_OF_TOP_TOPICS, TOPICS_NUMBER_OF_TOP_TOPICS);
    }

    @Override
    public final int getTopicsNumberOfRandomTopics() {
        return mBackend.getFlag(KEY_TOPICS_NUMBER_OF_RANDOM_TOPICS, TOPICS_NUMBER_OF_RANDOM_TOPICS);
    }

    @Override
    public final int getTopicsNumberOfLookBackEpochs() {
        return mBackend.getFlag(
                KEY_TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS, TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS);
    }

    @Override
    public final float getTopicsPrivacyBudgetForTopicIdDistribution() {
        return mBackend.getFlag(
                KEY_TOPICS_PRIVACY_BUDGET_FOR_TOPIC_ID_DISTRIBUTION,
                TOPICS_PRIVACY_BUDGET_FOR_TOPIC_ID_DISTRIBUTION);
    }

    @Override
    public final int getClassifierType() {
        return mBackend.getFlag(KEY_CLASSIFIER_TYPE, DEFAULT_CLASSIFIER_TYPE);
    }

    @Override
    public final int getClassifierNumberOfTopLabels() {
        return mBackend.getFlag(
                KEY_CLASSIFIER_NUMBER_OF_TOP_LABELS, CLASSIFIER_NUMBER_OF_TOP_LABELS);
    }

    @Override
    public final boolean getTopicsCobaltLoggingEnabled() {
        return mBackend.getFlag(KEY_TOPICS_COBALT_LOGGING_ENABLED, TOPICS_COBALT_LOGGING_ENABLED);
    }

    @Override
    public final boolean getMsmtRegistrationCobaltLoggingEnabled() {
        return mBackend.getFlag(
                KEY_MSMT_REGISTRATION_COBALT_LOGGING_ENABLED,
                MSMT_REGISTRATION_COBALT_LOGGING_ENABLED);
    }

    @Override
    public final boolean getMsmtAttributionCobaltLoggingEnabled() {
        return mBackend.getFlag(
                KEY_MSMT_ATTRIBUTION_COBALT_LOGGING_ENABLED,
                MSMT_ATTRIBUTION_COBALT_LOGGING_ENABLED);
    }

    @Override
    public final boolean getMsmtReportingCobaltLoggingEnabled() {
        return mBackend.getFlag(
                KEY_MSMT_REPORTING_COBALT_LOGGING_ENABLED, MSMT_REPORTING_COBALT_LOGGING_ENABLED);
    }

    @Override
    public final boolean getAppNameApiErrorCobaltLoggingEnabled() {
        return mBackend.getFlag(
                KEY_APP_NAME_API_ERROR_COBALT_LOGGING_ENABLED,
                APP_NAME_API_ERROR_COBALT_LOGGING_ENABLED);
    }

    @Override
    public final long getCobaltLoggingJobPeriodMs() {
        return mBackend.getFlag(KEY_COBALT_LOGGING_JOB_PERIOD_MS, COBALT_LOGGING_JOB_PERIOD_MS);
    }

    @Override
    public final long getCobaltUploadServiceUnbindDelayMs() {
        return mBackend.getFlag(
                KEY_COBALT_UPLOAD_SERVICE_UNBIND_DELAY_MS, COBALT_UPLOAD_SERVICE_UNBIND_DELAY_MS);
    }

    @Override
    public final boolean getCobaltLoggingEnabled() {
        return mBackend.getFlag(KEY_COBALT_LOGGING_ENABLED, COBALT_LOGGING_ENABLED);
    }

    @Override
    public final long getMaintenanceJobPeriodMs() {
        return mBackend.getFlag(KEY_MAINTENANCE_JOB_PERIOD_MS, MAINTENANCE_JOB_PERIOD_MS);
    }

    @Override
    public final long getMaintenanceJobFlexMs() {
        return mBackend.getFlag(KEY_MAINTENANCE_JOB_FLEX_MS, MAINTENANCE_JOB_FLEX_MS);
    }

    @Override
    public final boolean getMeasurementAttributionFallbackJobEnabled() {
        return !mBackend.getFlag(
                KEY_MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_KILL_SWITCH,
                MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_KILL_SWITCH);
    }

    @Override
    public final boolean getFledgeEnableScheduleCustomAudienceUpdateAdditionalScheduleRequests() {
        return mBackend.getFlag(
                KEY_FLEDGE_ENABLE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ADDITIONAL_SCHEDULE_REQUESTS,
                FLEDGE_ENABLE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ADDITIONAL_SCHEDULE_REQUESTS);
    }

    @Override
    public final boolean getGlobalKillSwitch() {
        return mBackend.getFlag(KEY_GLOBAL_KILL_SWITCH, GLOBAL_KILL_SWITCH);
    }

    @Override
    public final boolean getLegacyMeasurementKillSwitch() {
        return mBackend.getFlag(KEY_MEASUREMENT_KILL_SWITCH, MEASUREMENT_KILL_SWITCH);
    }

    @Override
    public final boolean getMeasurementEnabled() {
        return !getLegacyMeasurementKillSwitch();
    }

    @Override
    public final boolean getMeasurementApiDeleteRegistrationsKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH,
                MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH);
    }

    @Override
    public final boolean getMeasurementApiStatusKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_API_STATUS_KILL_SWITCH, MEASUREMENT_API_STATUS_KILL_SWITCH);
    }

    @Override
    public final boolean getMeasurementApiRegisterSourceKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH,
                MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH);
    }

    @Override
    public final boolean getMeasurementApiRegisterTriggerKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH,
                MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH);
    }

    @Override
    public final boolean getMeasurementApiRegisterWebSourceKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH,
                MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH);
    }

    @Override
    public final boolean getMeasurementApiRegisterSourcesKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_API_REGISTER_SOURCES_KILL_SWITCH,
                MEASUREMENT_API_REGISTER_SOURCES_KILL_SWITCH);
    }

    @Override
    public final boolean getMeasurementApiRegisterWebTriggerKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH,
                MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH);
    }

    @Override
    public final boolean getMeasurementJobAggregateFallbackReportingKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH,
                MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH);
    }

    @Override
    public final boolean getMeasurementJobAggregateReportingKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH,
                MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH);
    }

    @Override
    public final boolean getMeasurementJobImmediateAggregateReportingKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_JOB_IMMEDIATE_AGGREGATE_REPORTING_KILL_SWITCH,
                MEASUREMENT_JOB_IMMEDIATE_AGGREGATE_REPORTING_KILL_SWITCH);
    }

    @Override
    public final boolean getMeasurementJobAttributionKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH,
                MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH);
    }

    @Override
    public final boolean getMeasurementJobDeleteExpiredKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH,
                MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH);
    }

    @Override
    public final boolean getMeasurementJobDeleteUninstalledKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_JOB_DELETE_UNINSTALLED_KILL_SWITCH,
                MEASUREMENT_JOB_DELETE_UNINSTALLED_KILL_SWITCH);
    }

    @Override
    public final boolean getMeasurementJobEventFallbackReportingKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH,
                MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH);
    }

    @Override
    public final boolean getMeasurementJobEventReportingKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH,
                MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH);
    }

    @Override
    public final boolean getAsyncRegistrationJobQueueKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH,
                MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH);
    }

    @Override
    public final boolean getAsyncRegistrationFallbackJobKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_REGISTRATION_FALLBACK_JOB_KILL_SWITCH,
                MEASUREMENT_REGISTRATION_FALLBACK_JOB_KILL_SWITCH);
    }

    @Override
    public final boolean getMeasurementReceiverInstallAttributionKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH,
                MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH);
    }

    @Override
    public final boolean getMeasurementReceiverDeletePackagesKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH,
                MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH);
    }

    @Override
    public final boolean getMeasurementRollbackDeletionKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH,
                MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH);
    }

    @Override
    public final boolean getMeasurementRollbackDeletionAppSearchKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH,
                MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH);
    }

    @Override
    public final boolean getAdIdKillSwitch() {
        return mBackend.getFlag(KEY_ADID_KILL_SWITCH, ADID_KILL_SWITCH);
    }

    @Override
    public final boolean getAppSetIdKillSwitch() {
        return mBackend.getFlag(KEY_APPSETID_KILL_SWITCH, APPSETID_KILL_SWITCH);
    }

    @Override
    public final boolean getTopicsKillSwitch() {
        return mBackend.getFlag(KEY_TOPICS_KILL_SWITCH, TOPICS_KILL_SWITCH);
    }

    @Override
    public final boolean getTopicsOnDeviceClassifierKillSwitch() {
        return mBackend.getFlag(
                KEY_TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH,
                TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH);
    }

    @Override
    public final boolean getMddBackgroundTaskKillSwitch() {
        return mBackend.getFlag(
                KEY_MDD_BACKGROUND_TASK_KILL_SWITCH, MDD_BACKGROUND_TASK_KILL_SWITCH);
    }

    @Override
    public final boolean getMddLoggerEnabled() {
        return !mBackend.getFlag(KEY_MDD_LOGGER_KILL_SWITCH, MDD_LOGGER_KILL_SWITCH);
    }

    @Override
    public final boolean getFledgeSelectAdsKillSwitch() {
        return mBackend.getFlag(KEY_FLEDGE_SELECT_ADS_KILL_SWITCH, FLEDGE_SELECT_ADS_KILL_SWITCH);
    }

    @Override
    public final boolean getFledgeCustomAudienceServiceKillSwitch() {
        return mBackend.getFlag(
                KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH,
                FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH);
    }

    @Override
    public final boolean getProtectedSignalsEnabled() {
        return mBackend.getFlag(KEY_PROTECTED_SIGNALS_ENABLED, PROTECTED_SIGNALS_ENABLED);
    }

    @Override
    public final boolean getFledgeAuctionServerKillSwitch() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_KILL_SWITCH, FLEDGE_AUCTION_SERVER_KILL_SWITCH);
    }

    @Override
    public final boolean getFledgeOnDeviceAuctionKillSwitch() {
        return mBackend.getFlag(
                KEY_FLEDGE_ON_DEVICE_AUCTION_KILL_SWITCH, FLEDGE_ON_DEVICE_AUCTION_KILL_SWITCH);
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public final boolean getEncryptionKeyNewEnrollmentFetchKillSwitch() {
        return mBackend.getFlag(
                KEY_ENCRYPTION_KEY_NEW_ENROLLMENT_FETCH_KILL_SWITCH,
                ENCRYPTION_KEY_NEW_ENROLLMENT_FETCH_KILL_SWITCH);
    }

    @Override
    public final boolean getEncryptionKeyPeriodicFetchKillSwitch() {
        return mBackend.getFlag(
                KEY_ENCRYPTION_KEY_PERIODIC_FETCH_KILL_SWITCH,
                ENCRYPTION_KEY_PERIODIC_FETCH_KILL_SWITCH);
    }

    @Override
    public final float getSdkRequestPermitsPerSecond() {
        return mBackend.getFlag(KEY_SDK_REQUEST_PERMITS_PER_SECOND, SDK_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public final float getAdIdRequestPermitsPerSecond() {
        return mBackend.getFlag(
                KEY_ADID_REQUEST_PERMITS_PER_SECOND, ADID_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public final float getAppSetIdRequestPermitsPerSecond() {
        return mBackend.getFlag(
                KEY_APPSETID_REQUEST_PERMITS_PER_SECOND, APPSETID_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public final float getMeasurementRegisterSourceRequestPermitsPerSecond() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_REGISTER_SOURCE_REQUEST_PERMITS_PER_SECOND,
                MEASUREMENT_REGISTER_SOURCE_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public final float getMeasurementRegisterSourcesRequestPermitsPerSecond() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_REGISTER_SOURCES_REQUEST_PERMITS_PER_SECOND,
                MEASUREMENT_REGISTER_SOURCES_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public final float getMeasurementRegisterWebSourceRequestPermitsPerSecond() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_REGISTER_WEB_SOURCE_REQUEST_PERMITS_PER_SECOND,
                MEASUREMENT_REGISTER_WEB_SOURCE_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public final float getMeasurementRegisterTriggerRequestPermitsPerSecond() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_REGISTER_TRIGGER_REQUEST_PERMITS_PER_SECOND,
                MEASUREMENT_REGISTER_TRIGGER_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public final float getMeasurementRegisterWebTriggerRequestPermitsPerSecond() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_REGISTER_WEB_TRIGGER_REQUEST_PERMITS_PER_SECOND,
                MEASUREMENT_REGISTER_WEB_TRIGGER_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public final float getTopicsApiAppRequestPermitsPerSecond() {
        return mBackend.getFlag(
                KEY_TOPICS_API_APP_REQUEST_PERMITS_PER_SECOND,
                TOPICS_API_APP_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public final float getTopicsApiSdkRequestPermitsPerSecond() {
        return mBackend.getFlag(
                KEY_TOPICS_API_SDK_REQUEST_PERMITS_PER_SECOND,
                TOPICS_API_SDK_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public final float getFledgeJoinCustomAudienceRequestPermitsPerSecond() {
        return mBackend.getFlag(
                KEY_FLEDGE_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public final float getFledgeFetchAndJoinCustomAudienceRequestPermitsPerSecond() {
        return mBackend.getFlag(
                KEY_FLEDGE_FETCH_AND_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_FETCH_AND_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public final float getFledgeScheduleCustomAudienceUpdateRequestPermitsPerSecond() {
        return mBackend.getFlag(
                KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public final float getFledgeLeaveCustomAudienceRequestPermitsPerSecond() {
        return mBackend.getFlag(
                KEY_FLEDGE_LEAVE_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_LEAVE_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public final float getFledgeUpdateSignalsRequestPermitsPerSecond() {
        return mBackend.getFlag(
                KEY_FLEDGE_UPDATE_SIGNALS_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_UPDATE_SIGNALS_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public final float getFledgeSelectAdsRequestPermitsPerSecond() {
        return mBackend.getFlag(
                KEY_FLEDGE_SELECT_ADS_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_SELECT_ADS_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public final float getFledgeSelectAdsWithOutcomesRequestPermitsPerSecond() {
        return mBackend.getFlag(
                KEY_FLEDGE_SELECT_ADS_WITH_OUTCOMES_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_SELECT_ADS_WITH_OUTCOMES_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public final float getFledgeGetAdSelectionDataRequestPermitsPerSecond() {
        return mBackend.getFlag(
                KEY_FLEDGE_GET_AD_SELECTION_DATA_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_GET_AD_SELECTION_DATA_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public final float getFledgePersistAdSelectionResultRequestPermitsPerSecond() {
        return mBackend.getFlag(
                KEY_FLEDGE_PERSIST_AD_SELECTION_RESULT_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_PERSIST_AD_SELECTION_RESULT_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public final float getFledgeReportImpressionRequestPermitsPerSecond() {
        return mBackend.getFlag(
                KEY_FLEDGE_REPORT_IMPRESSION_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_REPORT_IMPRESSION_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public final float getFledgeReportInteractionRequestPermitsPerSecond() {
        return mBackend.getFlag(
                KEY_FLEDGE_REPORT_INTERACTION_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_REPORT_INTERACTION_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public final float getFledgeSetAppInstallAdvertisersRequestPermitsPerSecond() {
        return mBackend.getFlag(
                KEY_FLEDGE_SET_APP_INSTALL_ADVERTISERS_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_SET_APP_INSTALL_ADVERTISERS_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public final String getUiOtaStringsManifestFileUrl() {
        return mBackend.getFlag(
                KEY_UI_OTA_STRINGS_MANIFEST_FILE_URL, UI_OTA_STRINGS_MANIFEST_FILE_URL);
    }

    @Override
    public final boolean getUiOtaStringsFeatureEnabled() {
        return mBackend.getFlag(KEY_UI_OTA_STRINGS_FEATURE_ENABLED, UI_OTA_STRINGS_FEATURE_ENABLED);
    }

    @Override
    public final String getUiOtaResourcesManifestFileUrl() {
        return mBackend.getFlag(
                KEY_UI_OTA_RESOURCES_MANIFEST_FILE_URL, UI_OTA_RESOURCES_MANIFEST_FILE_URL);
    }

    @Override
    public final boolean getUiOtaResourcesFeatureEnabled() {
        return mBackend.getFlag(
                KEY_UI_OTA_RESOURCES_FEATURE_ENABLED, UI_OTA_RESOURCES_FEATURE_ENABLED);
    }

    @Override
    public final boolean getAdServicesEnabled() {
        return mBackend.getFlag(KEY_ADSERVICES_ENABLED, ADSERVICES_ENABLED);
    }

    @Override
    public final int getNumberOfEpochsToKeepInHistory() {
        return mBackend.getFlag(
                KEY_NUMBER_OF_EPOCHS_TO_KEEP_IN_HISTORY, NUMBER_OF_EPOCHS_TO_KEEP_IN_HISTORY);
    }

    @Override
    public final boolean getFledgeAuctionServerEnabledForReportImpression() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_ENABLED_FOR_REPORT_IMPRESSION,
                FLEDGE_AUCTION_SERVER_ENABLED_FOR_REPORT_IMPRESSION);
    }

    @Override
    public final boolean getFledgeAuctionServerEnabledForReportEvent() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_ENABLED_FOR_REPORT_EVENT,
                FLEDGE_AUCTION_SERVER_ENABLED_FOR_REPORT_EVENT);
    }

    @Override
    public final boolean getFledgeAuctionServerEnabledForUpdateHistogram() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_ENABLED_FOR_UPDATE_HISTOGRAM,
                FLEDGE_AUCTION_SERVER_ENABLED_FOR_UPDATE_HISTOGRAM);
    }

    @Override
    public final boolean getFledgeAuctionServerEnabledForSelectAdsMediation() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_ENABLED_FOR_SELECT_ADS_MEDIATION,
                FLEDGE_AUCTION_SERVER_ENABLED_FOR_SELECT_ADS_MEDIATION);
    }

    @Override
    public final boolean isDisableTopicsEnrollmentCheck() {
        return mBackend.getFlag(
                KEY_DISABLE_TOPICS_ENROLLMENT_CHECK, DISABLE_TOPICS_ENROLLMENT_CHECK);
    }

    @Override
    public final boolean isDisableMeasurementEnrollmentCheck() {
        return mBackend.getFlag(
                KEY_DISABLE_MEASUREMENT_ENROLLMENT_CHECK, DISABLE_MEASUREMENT_ENROLLMENT_CHECK);
    }

    @Override
    public final boolean getDisableFledgeEnrollmentCheck() {
        return mBackend.getFlag(
                KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK, DISABLE_FLEDGE_ENROLLMENT_CHECK);
    }

    @Override
    public final boolean getEnforceForegroundStatusForTopics() {
        return mBackend.getFlag(
                KEY_ENFORCE_FOREGROUND_STATUS_TOPICS, ENFORCE_FOREGROUND_STATUS_TOPICS);
    }

    @Override
    public final boolean getEnforceForegroundStatusForSignals() {
        return mBackend.getFlag(
                KEY_ENFORCE_FOREGROUND_STATUS_SIGNALS, ENFORCE_FOREGROUND_STATUS_SIGNALS);
    }

    @Override
    public final boolean getEnforceForegroundStatusForAdId() {
        return mBackend.getFlag(KEY_ENFORCE_FOREGROUND_STATUS_ADID, ENFORCE_FOREGROUND_STATUS_ADID);
    }

    @Override
    public final boolean getEnforceForegroundStatusForAppSetId() {
        return mBackend.getFlag(
                KEY_ENFORCE_FOREGROUND_STATUS_APPSETID, ENFORCE_FOREGROUND_STATUS_APPSETID);
    }

    @Override
    public final boolean getFledgeBeaconReportingMetricsEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_BEACON_REPORTING_METRICS_ENABLED,
                FLEDGE_BEACON_REPORTING_METRICS_ENABLED);
    }

    @Override
    public final boolean getFledgeAuctionServerApiUsageMetricsEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_API_USAGE_METRICS_ENABLED,
                FLEDGE_AUCTION_SERVER_API_USAGE_METRICS_ENABLED);
    }

    @Override
    public final boolean getFledgeAuctionServerKeyFetchMetricsEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_AUCTION_SERVER_KEY_FETCH_METRICS_ENABLED,
                FLEDGE_AUCTION_SERVER_KEY_FETCH_METRICS_ENABLED);
    }

    @Override
    public final boolean isBackCompatActivityFeatureEnabled() {
        return mBackend.getFlag(
                KEY_IS_BACK_COMPACT_ACTIVITY_FEATURE_ENABLED,
                IS_BACK_COMPACT_ACTIVITY_FEATURE_ENABLED);
    }

    @Override
    public final boolean getGaUxFeatureEnabled() {
        return mBackend.getFlag(KEY_GA_UX_FEATURE_ENABLED, GA_UX_FEATURE_ENABLED);
    }

    @Override
    public final boolean isEnrollmentBlocklisted(String enrollmentId) {
        return getEnrollmentBlocklist().contains(enrollmentId);
    }

    @Override
    public final boolean getFledgeMeasurementReportAndRegisterEventApiFallbackEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_FALLBACK_ENABLED,
                FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_FALLBACK_ENABLED);
    }

    @Override
    public final boolean getEnableBackCompat() {
        return mBackend.getFlag(KEY_ENABLE_BACK_COMPAT, ENABLE_BACK_COMPAT);
    }

    @Override
    public final boolean getU18UxEnabled() {
        return mBackend.getFlag(KEY_U18_UX_ENABLED, DEFAULT_U18_UX_ENABLED);
    }

    @Override
    public final boolean getPasUxEnabled() {
        return mBackend.getFlag(KEY_PAS_UX_ENABLED, DEFAULT_PAS_UX_ENABLED);
    }

    @Override
    public final boolean getMeasurementDebugReportingFallbackJobKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH,
                MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH);
    }

    @Override
    public final boolean getMeasurementVerboseDebugReportingFallbackJobKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH,
                MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH);
    }

    @Override
    public final boolean getMeasurementJobDebugReportingKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_JOB_DEBUG_REPORTING_KILL_SWITCH,
                MEASUREMENT_JOB_DEBUG_REPORTING_KILL_SWITCH);
    }

    @Override
    public final boolean getMeasurementJobVerboseDebugReportingKillSwitch() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_JOB_VERBOSE_DEBUG_REPORTING_KILL_SWITCH,
                MEASUREMENT_JOB_VERBOSE_DEBUG_REPORTING_KILL_SWITCH);
    }

    @Override
    public final boolean getMeasurementReportingJobServiceEnabled() {
        return mBackend.getFlag(
                KEY_MEASUREMENT_REPORTING_JOB_SERVICE_ENABLED, MEASUREMENT_REPORTING_JOB_ENABLED);
    }

    @Override
    public final int getBackgroundJobSamplingLoggingRate() {
        return mBackend.getFlag(
                KEY_BACKGROUND_JOB_SAMPLING_LOGGING_RATE,
                DEFAULT_BACKGROUND_JOB_SAMPLING_LOGGING_RATE);
    }

    @Override
    public final boolean getFledgeKAnonSignJoinFeatureEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_ENABLE_KANON_SIGN_JOIN_FEATURE,
                FLEDGE_DEFAULT_KANON_SIGN_JOIN_FEATURE_ENABLED);
    }

    @Override
    public final boolean getFledgeKAnonSignJoinFeatureOnDeviceAuctionEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_ENABLE_KANON_ON_DEVICE_AUCTION_FEATURE,
                FLEDGE_DEFAULT_KANON_FEATURE_ON_DEVICE_AUCTION_ENABLED);
    }

    @Override
    public final boolean getFledgeKAnonSignJoinFeatureAuctionServerEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_ENABLE_KANON_AUCTION_SERVER_FEATURE,
                FLEDGE_DEFAULT_KANON_FEATURE_AUCTION_SERVER_ENABLED);
    }

    @Override
    public final boolean getFledgeKAnonBackgroundProcessEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_KANON_BACKGROUND_PROCESS_ENABLED,
                FLEDGE_DEFAULT_KANON_BACKGROUND_PROCESS_ENABLED);
    }

    @Override
    public final boolean getFledgeKAnonLoggingEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_KANON_SIGN_JOIN_LOGGING_ENABLED,
                FLEDGE_DEFAULT_KANON_SIGN_JOIN_LOGGING_ENABLED);
    }

    @Override
    public final boolean getFledgeKAnonKeyAttestationEnabled() {
        return mBackend.getFlag(
                KEY_FLEDGE_KANON_KEY_ATTESTATION_ENABLED,
                FLEDGE_DEFAULT_KANON_KEY_ATTESTATION_ENABLED);
    }
}
