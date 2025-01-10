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

import static android.provider.DeviceConfig.NAMESPACE_ADSERVICES;

//Need to disable checkstyle as there's no need to import 500+ constants.
//CHECKSTYLE:OFF Generated code
import static com.android.adservices.service.FlagsConstants.*;
//CHECKSTYLE:ON
import static com.android.adservices.shared.common.flags.Constants.MAX_PERCENTAGE;

import static java.lang.Float.parseFloat;

import android.os.SystemProperties;
import android.text.TextUtils;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.LogUtil;
import com.android.adservices.shared.flags.DeviceConfigFlagsBackend;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;

//TODO(b/386415138): this class is should be moved to service-core, but currently it's only used by
//tests
/**
 * Flags Implementation that adds extra logic to some getters (hence the "smart" name).
 *
 * TODO(b/386415138): this class was copied from PhFlags before it was reverted to be standalone
 * and is not currently used in production - it's only used to make sure (through SmartFlagsTest)
 * that new "smart" flags added on PhFlags are added here as well, which would make it simpler to
 * merge them (or use a different solution) in the future.
 */
public final class SmartFlags extends RawFlags {

    // NOTE: since it's not used in production yet, we don't need a getInstance() factory method
    @VisibleForTesting
    SmartFlags() {
        super(new DeviceConfigFlagsBackend(NAMESPACE_ADSERVICES));
    }

    @Override
    public long getTopicsEpochJobPeriodMs() {
        long topicsEpochJobPeriodMs = super.getTopicsEpochJobPeriodMs();
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
                        getSystemPropertyName(KEY_TOPICS_EPOCH_JOB_FLEX_MS),
                        super.getTopicsEpochJobFlexMs());
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
                        getSystemPropertyName(KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC),
                        super.getTopicsPercentageForRandomTopic());
        if (topicsPercentageForRandomTopic < 0 || topicsPercentageForRandomTopic > MAX_PERCENTAGE) {
            throw new IllegalArgumentException(
                    "topicsPercentageForRandomTopic should be between 0 and 100");
        }
        return topicsPercentageForRandomTopic;
    }

    @Override
    public int getTopicsNumberOfTopTopics() {
        int topicsNumberOfTopTopics = super.getTopicsNumberOfTopTopics();
        if (topicsNumberOfTopTopics < 0) {
            throw new IllegalArgumentException("topicsNumberOfTopTopics should >= 0");
        }

        return topicsNumberOfTopTopics;
    }

    @Override
    public int getTopicsNumberOfRandomTopics() {
        int topicsNumberOfTopTopics = super.getTopicsNumberOfRandomTopics();
        if (topicsNumberOfTopTopics < 0) {
            throw new IllegalArgumentException("topicsNumberOfTopTopics should >= 0");
        }

        return topicsNumberOfTopTopics;
    }

    @Override
    public int getTopicsNumberOfLookBackEpochs() {
        int topicsNumberOfLookBackEpochs = super.getTopicsNumberOfLookBackEpochs();
        if (topicsNumberOfLookBackEpochs < 1) {
            throw new IllegalArgumentException("topicsNumberOfLookBackEpochs should  >= 1");
        }

        return topicsNumberOfLookBackEpochs;
    }

    @Override
    public float getTopicsPrivacyBudgetForTopicIdDistribution() {
        float topicsPrivacyBudgetForTopicIdDistribution =
                super.getTopicsPrivacyBudgetForTopicIdDistribution();

        if (topicsPrivacyBudgetForTopicIdDistribution <= 0) {
            throw new IllegalArgumentException(
                    "topicsPrivacyBudgetForTopicIdDistribution should be > 0");
        }

        return topicsPrivacyBudgetForTopicIdDistribution;
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public int getClassifierType() {
        return SystemProperties.getInt(
                getSystemPropertyName(KEY_CLASSIFIER_TYPE), super.getClassifierType());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public int getClassifierNumberOfTopLabels() {
        return SystemProperties.getInt(
                getSystemPropertyName(KEY_CLASSIFIER_NUMBER_OF_TOP_LABELS),
                super.getClassifierNumberOfTopLabels());
    }

    @Override
    public boolean getTopicsCobaltLoggingEnabled() {
        return getCobaltLoggingEnabled() && super.getTopicsCobaltLoggingEnabled();
    }

    @Override
    public boolean getMsmtRegistrationCobaltLoggingEnabled() {
        return getCobaltLoggingEnabled() && super.getMsmtRegistrationCobaltLoggingEnabled();
    }

    @Override
    public boolean getMsmtAttributionCobaltLoggingEnabled() {
        return getCobaltLoggingEnabled() && super.getMsmtAttributionCobaltLoggingEnabled();
    }

    @Override
    public boolean getMsmtReportingCobaltLoggingEnabled() {
        return getCobaltLoggingEnabled() && super.getMsmtReportingCobaltLoggingEnabled();
    }

    @Override
    public boolean getAppNameApiErrorCobaltLoggingEnabled() {
        return getCobaltLoggingEnabled() && super.getAppNameApiErrorCobaltLoggingEnabled();
    }

    @Override
    public long getCobaltLoggingJobPeriodMs() {
        long cobaltLoggingJobPeriodMs = super.getCobaltLoggingJobPeriodMs();
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
        long cobaltUploadServiceUnbindDelayMs = super.getCobaltUploadServiceUnbindDelayMs();
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
                        getSystemPropertyName(KEY_COBALT_LOGGING_ENABLED),
                        super.getCobaltLoggingEnabled());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public long getMaintenanceJobPeriodMs() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig) and then
        // hard-coded value.
        long maintenanceJobPeriodMs =
                SystemProperties.getLong(
                        getSystemPropertyName(KEY_MAINTENANCE_JOB_PERIOD_MS),
                        super.getMaintenanceJobPeriodMs());
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
                        getSystemPropertyName(KEY_MAINTENANCE_JOB_FLEX_MS),
                        super.getMaintenanceJobFlexMs());

        if (maintenanceJobFlexMs <= 0) {
            throw new IllegalArgumentException("maintenanceJobFlexMs should  > 0");
        }

        return maintenanceJobFlexMs;
    }

    @Override
    public boolean getMeasurementAttributionFallbackJobEnabled() {
        return getLegacyMeasurementKillSwitch()
                ? false
                : !getFlagFromSystemPropertiesOrDeviceConfig(
                        KEY_MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_KILL_SWITCH,
                        MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_KILL_SWITCH);
    }

    @Override
    public boolean getFledgeEnableScheduleCustomAudienceUpdateAdditionalScheduleRequests() {
        return getFledgeScheduleCustomAudienceUpdateEnabled()
                && super.getFledgeEnableScheduleCustomAudienceUpdateAdditionalScheduleRequests();
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getGlobalKillSwitch() {
        return SdkLevel.isAtLeastT()
                ? SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_GLOBAL_KILL_SWITCH), super.getGlobalKillSwitch())
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
                        KEY_MEASUREMENT_KILL_SWITCH, MEASUREMENT_KILL_SWITCH);
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementApiDeleteRegistrationsKillSwitch() {
        final boolean defaultValue = MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH),
                        super.getMeasurementApiDeleteRegistrationsKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementApiStatusKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_API_STATUS_KILL_SWITCH),
                        super.getMeasurementApiStatusKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementApiRegisterSourceKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH),
                        super.getMeasurementApiRegisterSourceKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementApiRegisterTriggerKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH),
                        super.getMeasurementApiRegisterTriggerKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementApiRegisterWebSourceKillSwitch() {
        boolean defaultValue = MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH),
                        super.getMeasurementApiRegisterWebSourceKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementApiRegisterSourcesKillSwitch() {
        boolean defaultValue = MEASUREMENT_API_REGISTER_SOURCES_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_API_REGISTER_SOURCES_KILL_SWITCH),
                        super.getMeasurementApiRegisterSourcesKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementApiRegisterWebTriggerKillSwitch() {
        boolean defaultValue = MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH),
                        super.getMeasurementApiRegisterWebTriggerKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementJobAggregateFallbackReportingKillSwitch() {
        String flagName = KEY_MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH;
        boolean defaultValue = MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(flagName),
                        super.getMeasurementJobAggregateFallbackReportingKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementJobAggregateReportingKillSwitch() {
        boolean defaultValue = MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH),
                        super.getMeasurementJobAggregateReportingKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead

    public boolean getMeasurementJobImmediateAggregateReportingKillSwitch() {
        return !getMeasurementEnabled()
                || super.getMeasurementJobImmediateAggregateReportingKillSwitch();
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementJobAttributionKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH),
                        super.getMeasurementJobAttributionKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementJobDeleteExpiredKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH),
                        super.getMeasurementJobDeleteExpiredKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementJobDeleteUninstalledKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_JOB_DELETE_UNINSTALLED_KILL_SWITCH),
                        super.getMeasurementJobDeleteUninstalledKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementJobEventFallbackReportingKillSwitch() {
        String flagName = KEY_MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH;
        boolean defaultValue = MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(flagName),
                        super.getMeasurementJobEventFallbackReportingKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementJobEventReportingKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH),
                        super.getMeasurementJobEventReportingKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getAsyncRegistrationJobQueueKillSwitch() {
        String flagName = KEY_MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH;
        boolean defaultValue = MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(flagName),
                        super.getAsyncRegistrationJobQueueKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getAsyncRegistrationFallbackJobKillSwitch() {
        String flagName = KEY_MEASUREMENT_REGISTRATION_FALLBACK_JOB_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(flagName),
                        super.getAsyncRegistrationFallbackJobKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementReceiverInstallAttributionKillSwitch() {
        String flagName = KEY_MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH;
        boolean defaultValue = MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(flagName),
                        super.getMeasurementReceiverInstallAttributionKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementReceiverDeletePackagesKillSwitch() {
        boolean defaultValue = MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH),
                        super.getMeasurementReceiverDeletePackagesKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementRollbackDeletionKillSwitch() {
        final boolean defaultValue = MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH),
                        super.getMeasurementRollbackDeletionKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementRollbackDeletionAppSearchKillSwitch() {
        final boolean defaultValue = MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH;
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                KEY_MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH),
                        super.getMeasurementRollbackDeletionAppSearchKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getAdIdKillSwitch() {
        // Ignore Global Killswitch for adid.
        return SystemProperties.getBoolean(
                getSystemPropertyName(KEY_ADID_KILL_SWITCH), super.getAdIdKillSwitch());
    }

    // APPSETID Killswitch.
    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead

    public boolean getAppSetIdKillSwitch() {
        // Ignore Global Killswitch for appsetid.
        return SystemProperties.getBoolean(
                getSystemPropertyName(KEY_APPSETID_KILL_SWITCH), super.getAppSetIdKillSwitch());
    }

    // TOPICS Killswitches
    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getTopicsKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_TOPICS_KILL_SWITCH), super.getTopicsKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getTopicsOnDeviceClassifierKillSwitch() {
        // This is an emergency flag that could be used to divert all traffic from on-device
        // classifier to precomputed classifier in case of fatal ML model crashes in Topics.
        return SystemProperties.getBoolean(
                getSystemPropertyName(KEY_TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH),
                super.getTopicsOnDeviceClassifierKillSwitch());
    }

    // MDD Killswitches
    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMddBackgroundTaskKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MDD_BACKGROUND_TASK_KILL_SWITCH),
                        super.getMddBackgroundTaskKillSwitch());
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
                        getSystemPropertyName(KEY_MDD_LOGGER_KILL_SWITCH),
                        super.getMddBackgroundTaskKillSwitch());
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
                        getSystemPropertyName(KEY_FLEDGE_SELECT_ADS_KILL_SWITCH),
                        super.getFledgeSelectAdsKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getFledgeCustomAudienceServiceKillSwitch() {
        // We check the Global Kill switch first. As a result, it overrides all other kill switches.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH),
                        super.getFledgeCustomAudienceServiceKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getProtectedSignalsEnabled() {
        // We check the Global Kill switch first. As a result, it overrides all other kill switches.
        return getGlobalKillSwitch()
                ? false
                : SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_PROTECTED_SIGNALS_ENABLED),
                        super.getProtectedSignalsEnabled());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getFledgeAuctionServerKillSwitch() {
        return getFledgeSelectAdsKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_FLEDGE_AUCTION_SERVER_KILL_SWITCH),
                        super.getFledgeAuctionServerKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getFledgeOnDeviceAuctionKillSwitch() {
        return getFledgeSelectAdsKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_FLEDGE_ON_DEVICE_AUCTION_KILL_SWITCH),
                        super.getFledgeOnDeviceAuctionKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getEncryptionKeyNewEnrollmentFetchKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_ENCRYPTION_KEY_NEW_ENROLLMENT_FETCH_KILL_SWITCH),
                        super.getEncryptionKeyNewEnrollmentFetchKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getEncryptionKeyPeriodicFetchKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_ENCRYPTION_KEY_PERIODIC_FETCH_KILL_SWITCH),
                        super.getEncryptionKeyPeriodicFetchKillSwitch());
    }

    // Encryption key related flags.
    @Override
    public float getSdkRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                KEY_SDK_REQUEST_PERMITS_PER_SECOND, SDK_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getAdIdRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                KEY_ADID_REQUEST_PERMITS_PER_SECOND, ADID_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getAppSetIdRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                KEY_APPSETID_REQUEST_PERMITS_PER_SECOND, APPSETID_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getMeasurementRegisterSourceRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                KEY_MEASUREMENT_REGISTER_SOURCE_REQUEST_PERMITS_PER_SECOND,
                MEASUREMENT_REGISTER_SOURCE_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getMeasurementRegisterSourcesRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                KEY_MEASUREMENT_REGISTER_SOURCES_REQUEST_PERMITS_PER_SECOND,
                MEASUREMENT_REGISTER_SOURCES_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getMeasurementRegisterWebSourceRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                KEY_MEASUREMENT_REGISTER_WEB_SOURCE_REQUEST_PERMITS_PER_SECOND,
                MEASUREMENT_REGISTER_WEB_SOURCE_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getMeasurementRegisterTriggerRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                KEY_MEASUREMENT_REGISTER_TRIGGER_REQUEST_PERMITS_PER_SECOND,
                MEASUREMENT_REGISTER_TRIGGER_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getMeasurementRegisterWebTriggerRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                KEY_MEASUREMENT_REGISTER_WEB_TRIGGER_REQUEST_PERMITS_PER_SECOND,
                MEASUREMENT_REGISTER_WEB_TRIGGER_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getTopicsApiAppRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                KEY_TOPICS_API_APP_REQUEST_PERMITS_PER_SECOND,
                TOPICS_API_APP_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getTopicsApiSdkRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                KEY_TOPICS_API_SDK_REQUEST_PERMITS_PER_SECOND,
                TOPICS_API_SDK_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgeJoinCustomAudienceRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                KEY_FLEDGE_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgeFetchAndJoinCustomAudienceRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                KEY_FLEDGE_FETCH_AND_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_FETCH_AND_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgeScheduleCustomAudienceUpdateRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgeLeaveCustomAudienceRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                KEY_FLEDGE_LEAVE_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_LEAVE_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgeUpdateSignalsRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                KEY_FLEDGE_UPDATE_SIGNALS_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_UPDATE_SIGNALS_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgeSelectAdsRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                KEY_FLEDGE_SELECT_ADS_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_SELECT_ADS_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgeSelectAdsWithOutcomesRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                KEY_FLEDGE_SELECT_ADS_WITH_OUTCOMES_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_SELECT_ADS_WITH_OUTCOMES_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgeGetAdSelectionDataRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                KEY_FLEDGE_GET_AD_SELECTION_DATA_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_GET_AD_SELECTION_DATA_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgePersistAdSelectionResultRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                KEY_FLEDGE_PERSIST_AD_SELECTION_RESULT_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_PERSIST_AD_SELECTION_RESULT_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgeReportImpressionRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                KEY_FLEDGE_REPORT_IMPRESSION_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_REPORT_IMPRESSION_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgeReportInteractionRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                KEY_FLEDGE_REPORT_INTERACTION_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_REPORT_INTERACTION_REQUEST_PERMITS_PER_SECOND);
    }

    @Override
    public float getFledgeSetAppInstallAdvertisersRequestPermitsPerSecond() {
        return getPermitsPerSecond(
                KEY_FLEDGE_SET_APP_INSTALL_ADVERTISERS_REQUEST_PERMITS_PER_SECOND,
                FLEDGE_SET_APP_INSTALL_ADVERTISERS_REQUEST_PERMITS_PER_SECOND);
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

        return mBackend.getFlag(flagName, defaultValue);
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public String getUiOtaStringsManifestFileUrl() {
        return SystemProperties.get(
                getSystemPropertyName(KEY_UI_OTA_STRINGS_MANIFEST_FILE_URL),
                super.getUiOtaStringsManifestFileUrl());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getUiOtaStringsFeatureEnabled() {
        if (getGlobalKillSwitch()) {
            return false;
        }
        return SystemProperties.getBoolean(
                getSystemPropertyName(KEY_UI_OTA_STRINGS_FEATURE_ENABLED),
                super.getUiOtaStringsFeatureEnabled());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public String getUiOtaResourcesManifestFileUrl() {
        return SystemProperties.get(
                getSystemPropertyName(KEY_UI_OTA_RESOURCES_MANIFEST_FILE_URL),
                super.getUiOtaResourcesManifestFileUrl());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getUiOtaResourcesFeatureEnabled() {
        if (getGlobalKillSwitch()) {
            return false;
        }
        return SystemProperties.getBoolean(
                getSystemPropertyName(KEY_UI_OTA_RESOURCES_FEATURE_ENABLED),
                super.getUiOtaResourcesFeatureEnabled());
    }

    @Override
    public boolean getAdServicesEnabled() {
        // if the global kill switch is enabled, feature should be disabled.
        if (getGlobalKillSwitch()) {
            return false;
        }
        return super.getAdServicesEnabled();
    }

    @Override
    public int getNumberOfEpochsToKeepInHistory() {
        int numberOfEpochsToKeepInHistory = super.getNumberOfEpochsToKeepInHistory();

        if (numberOfEpochsToKeepInHistory < 1) {
            throw new IllegalArgumentException("numberOfEpochsToKeepInHistory should  >= 0");
        }

        return numberOfEpochsToKeepInHistory;
    }

    @Override
    public boolean getFledgeAuctionServerEnabledForReportImpression() {
        return getFledgeAuctionServerEnabled()
                && super.getFledgeAuctionServerEnabledForReportImpression();
    }

    @Override
    public boolean getFledgeAuctionServerEnabledForReportEvent() {
        return getFledgeAuctionServerEnabled()
                && super.getFledgeAuctionServerEnabledForReportEvent();
    }

    @Override
    public boolean getFledgeAuctionServerEnabledForUpdateHistogram() {
        return getFledgeAuctionServerEnabled()
                && super.getFledgeAuctionServerEnabledForUpdateHistogram();
    }

    @Override
    public boolean getFledgeAuctionServerEnabledForSelectAdsMediation() {
        return getFledgeAuctionServerEnabled()
                && super.getFledgeAuctionServerEnabledForSelectAdsMediation();
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean isDisableTopicsEnrollmentCheck() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(KEY_DISABLE_TOPICS_ENROLLMENT_CHECK),
                super.isDisableTopicsEnrollmentCheck());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean isDisableMeasurementEnrollmentCheck() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(KEY_DISABLE_MEASUREMENT_ENROLLMENT_CHECK),
                super.isDisableMeasurementEnrollmentCheck());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getDisableFledgeEnrollmentCheck() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK),
                super.getDisableFledgeEnrollmentCheck());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead

    public boolean getEnforceForegroundStatusForTopics() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(KEY_ENFORCE_FOREGROUND_STATUS_TOPICS),
                super.getEnforceForegroundStatusForTopics());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getEnforceForegroundStatusForSignals() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(KEY_ENFORCE_FOREGROUND_STATUS_SIGNALS),
                super.getEnforceForegroundStatusForSignals());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getEnforceForegroundStatusForAdId() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(KEY_ENFORCE_FOREGROUND_STATUS_ADID),
                super.getEnforceForegroundStatusForAdId());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getEnforceForegroundStatusForAppSetId() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(KEY_ENFORCE_FOREGROUND_STATUS_APPSETID),
                super.getEnforceForegroundStatusForAppSetId());
    }

    @Override
    public boolean getFledgeBeaconReportingMetricsEnabled() {
        return getFledgeRegisterAdBeaconEnabled() && super.getFledgeBeaconReportingMetricsEnabled();
    }

    @Override
    public boolean getFledgeAuctionServerApiUsageMetricsEnabled() {
        return getFledgeAuctionServerEnabled()
                && super.getFledgeAuctionServerApiUsageMetricsEnabled();
    }

    @Override
    public boolean getFledgeAuctionServerKeyFetchMetricsEnabled() {
        return getFledgeAuctionServerEnabled()
                && super.getFledgeAuctionServerKeyFetchMetricsEnabled();
    }

    @Override
    @SuppressWarnings("InlinedApi")
    public boolean isBackCompatActivityFeatureEnabled() {
        // Check if enable Back compat is true first and then check flag value
        return getEnableBackCompat() && super.isBackCompatActivityFeatureEnabled();
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getGaUxFeatureEnabled() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(KEY_GA_UX_FEATURE_ENABLED), super.getGaUxFeatureEnabled());
    }

    @Override
    public boolean isEnrollmentBlocklisted(String enrollmentId) {
        return getEnrollmentBlocklist().contains(enrollmentId);
    }

    @Override
    public boolean getFledgeMeasurementReportAndRegisterEventApiFallbackEnabled() {
        return getFledgeMeasurementReportAndRegisterEventApiEnabled()
                && super.getFledgeMeasurementReportAndRegisterEventApiFallbackEnabled();
    }

    @Override
    public boolean getEnableBackCompat() {
        // If SDK is T+, the value should always be false
        // Check the flag value for S Minus
        return !SdkLevel.isAtLeastT() && super.getEnableBackCompat();
    }

    @Override
    public boolean getU18UxEnabled() {
        return getEnableAdServicesSystemApi() && super.getU18UxEnabled();
    }

    @Override
    public boolean getPasUxEnabled() {
        if (getEeaPasUxEnabled()) {
            // EEA devices (if EEA device feature is not enabled, assume EEA to be safe)
            if (!isEeaDeviceFeatureEnabled() || isEeaDevice()) {
                return true;
            }
            // ROW devices
            return super.getPasUxEnabled();
        }
        return isEeaDeviceFeatureEnabled() && !isEeaDevice() && super.getPasUxEnabled();
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementDebugReportingFallbackJobKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                KEY_MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH),
                        super.getMeasurementDebugReportingFallbackJobKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementVerboseDebugReportingFallbackJobKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                KEY_MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH),
                        super.getMeasurementVerboseDebugReportingFallbackJobKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementJobDebugReportingKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_JOB_DEBUG_REPORTING_KILL_SWITCH),
                        super.getMeasurementJobDebugReportingKillSwitch());
    }

    @Override
    @SuppressWarnings("AvoidSystemPropertiesUsage")
    // TODO(b/300646389): call getFlagFromSystemPropertiesOrDeviceConfig() instead
    public boolean getMeasurementJobVerboseDebugReportingKillSwitch() {
        return getLegacyMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(
                                KEY_MEASUREMENT_JOB_VERBOSE_DEBUG_REPORTING_KILL_SWITCH),
                        super.getMeasurementJobVerboseDebugReportingKillSwitch());
    }

    @Override
    public boolean getMeasurementReportingJobServiceEnabled() {
        return getMeasurementEnabled() && super.getMeasurementReportingJobServiceEnabled();
    }

    @Override
    public int getBackgroundJobSamplingLoggingRate() {
        int loggingRatio = super.getBackgroundJobSamplingLoggingRate();

        // TODO(b/323187832): Calling JobServiceConstants.MAX_PERCENTAGE meets dependency error.
        if (loggingRatio < 0 || loggingRatio > MAX_PERCENTAGE) {
            throw new IllegalArgumentException(
                    "BackgroundJobSamplingLoggingRatio should be in the range of [0, 100]");
        }

        return loggingRatio;
    }

    @Override
    public boolean getFledgeKAnonSignJoinFeatureEnabled() {
        return getFledgeAuctionServerEnabled() && super.getFledgeKAnonSignJoinFeatureEnabled();
    }

    @Override
    public boolean getFledgeKAnonSignJoinFeatureOnDeviceAuctionEnabled() {
        return getFledgeKAnonSignJoinFeatureEnabled()
                && super.getFledgeKAnonSignJoinFeatureOnDeviceAuctionEnabled();
    }

    @Override
    public boolean getFledgeKAnonSignJoinFeatureAuctionServerEnabled() {
        return getFledgeKAnonSignJoinFeatureEnabled()
                && super.getFledgeKAnonSignJoinFeatureAuctionServerEnabled();
    }

    @Override
    public boolean getFledgeKAnonBackgroundProcessEnabled() {
        return getFledgeKAnonSignJoinFeatureEnabled()
                && super.getFledgeKAnonBackgroundProcessEnabled();
    }

    @Override
    public boolean getFledgeKAnonLoggingEnabled() {
        return getFledgeKAnonSignJoinFeatureEnabled() && super.getFledgeKAnonLoggingEnabled();
    }

    @Override
    public boolean getFledgeKAnonKeyAttestationEnabled() {
        return getFledgeKAnonSignJoinFeatureEnabled()
                && super.getFledgeKAnonKeyAttestationEnabled();
    }

    // Do NOT add Flag / @Override methods below - it should only contain helpers

    /**
     * @deprecated - reading a flag from {@link SystemProperties} first is deprecated - this method
     *     should only be used to refactor existing methods in this class, not on new ones.
     */
    @Deprecated
    @SuppressWarnings("AvoidSystemPropertiesUsage") // Helper method.
    private boolean getFlagFromSystemPropertiesOrDeviceConfig(String name, boolean defaultValue) {
        return SystemProperties.getBoolean(
                getSystemPropertyName(name), mBackend.getFlag(name, defaultValue));
    }

    @VisibleForTesting
    static String getSystemPropertyName(String key) {
        return AdServicesCommon.SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX + key;
    }
}
