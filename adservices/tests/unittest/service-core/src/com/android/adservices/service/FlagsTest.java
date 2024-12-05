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

package com.android.adservices.service;

import static com.android.adservices.service.Flags.AD_SERVICES_MODULE_JOB_POLICY;
import static com.android.adservices.service.Flags.APPSEARCH_ONLY;
import static com.android.adservices.service.Flags.COBALT__IGNORED_REPORT_ID_LIST;
import static com.android.adservices.service.Flags.COMPONENT_AD_RENDER_ID_MAX_LENGTH_BYTES;
import static com.android.adservices.service.Flags.DEFAULT_ADID_CACHE_TTL_MS;
import static com.android.adservices.service.Flags.DEFAULT_BLOCKED_TOPICS_SOURCE_OF_TRUTH;
import static com.android.adservices.service.Flags.DEFAULT_CONSENT_SOURCE_OF_TRUTH;
import static com.android.adservices.service.Flags.DEFAULT_JOB_SCHEDULING_LOGGING_SAMPLING_RATE;
import static com.android.adservices.service.Flags.DEFAULT_MDD_PACKAGE_DENY_REGISTRY_MANIFEST_FILE_URL;
import static com.android.adservices.service.Flags.DEFAULT_PACKAGE_DENY_BACKGROUND_JOB_PERIOD_MILLIS;
import static com.android.adservices.service.Flags.DEFAULT_PAS_SCRIPT_DOWNLOAD_CONNECTION_TIMEOUT_MS;
import static com.android.adservices.service.Flags.DEFAULT_PAS_SCRIPT_DOWNLOAD_READ_TIMEOUT_MS;
import static com.android.adservices.service.Flags.DEFAULT_PAS_SCRIPT_EXECUTION_TIMEOUT_MS;
import static com.android.adservices.service.Flags.DEFAULT_PAS_SIGNALS_DOWNLOAD_CONNECTION_TIMEOUT_MS;
import static com.android.adservices.service.Flags.DEFAULT_PAS_SIGNALS_DOWNLOAD_READ_TIMEOUT_MS;
import static com.android.adservices.service.Flags.ENABLE_APPSEARCH_CONSENT_DATA;
import static com.android.adservices.service.Flags.ENABLE_CUSTOM_AUDIENCE_COMPONENT_ADS;
import static com.android.adservices.service.Flags.ENFORCE_FOREGROUND_STATUS_FETCH_AND_JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.Flags.ENFORCE_FOREGROUND_STATUS_LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.Flags.ENFORCE_FOREGROUND_STATUS_SCHEDULE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.Flags.FLEDGE_CUSTOM_AUDIENCE_PER_BUYER_MAX_COUNT;
import static com.android.adservices.service.Flags.FLEDGE_FETCH_AND_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.Flags.FLEDGE_FORCED_ENCODING_AFTER_SIGNALS_UPDATE_COOLDOWN_SECONDS;
import static com.android.adservices.service.Flags.FLEDGE_GET_AD_SELECTION_DATA_BUYER_INPUT_CREATOR_VERSION;
import static com.android.adservices.service.Flags.FLEDGE_GET_AD_SELECTION_DATA_DESERIALIZE_ONLY_AD_RENDER_IDS;
import static com.android.adservices.service.Flags.FLEDGE_GET_AD_SELECTION_DATA_MAX_NUM_ENTIRE_PAYLOAD_COMPRESSIONS;
import static com.android.adservices.service.Flags.FLEDGE_GET_AD_SELECTION_DATA_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.Flags.FLEDGE_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.Flags.FLEDGE_LEAVE_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.Flags.FLEDGE_PERSIST_AD_SELECTION_RESULT_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.Flags.FLEDGE_REPORT_IMPRESSION_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.Flags.FLEDGE_REPORT_INTERACTION_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.Flags.FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MAX_BYTES;
import static com.android.adservices.service.Flags.FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.Flags.FLEDGE_SELECT_ADS_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.Flags.FLEDGE_SELECT_ADS_WITH_OUTCOMES_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.Flags.FLEDGE_SET_APP_INSTALL_ADVERTISERS_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.Flags.FLEDGE_UPDATE_AD_COUNTER_HISTOGRAM_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.Flags.FLEDGE_UPDATE_SIGNALS_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.Flags.GLOBAL_KILL_SWITCH;
import static com.android.adservices.service.Flags.MAX_COMPONENT_ADS_PER_CUSTOM_AUDIENCE;
import static com.android.adservices.service.Flags.MDD_LOGGER_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_ADR_BUDGET_PER_ORIGIN_PUBLISHER_WINDOW;
import static com.android.adservices.service.Flags.MEASUREMENT_ADR_BUDGET_PER_PUBLISHER_WINDOW;
import static com.android.adservices.service.Flags.MEASUREMENT_ADR_BUDGET_WINDOW_LENGTH_MILLIS;
import static com.android.adservices.service.Flags.MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_EVENT;
import static com.android.adservices.service.Flags.MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_NAVIGATION;
import static com.android.adservices.service.Flags.MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_EVENT;
import static com.android.adservices.service.Flags.MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_NAVIGATION;
import static com.android.adservices.service.Flags.MEASUREMENT_DEFAULT_DESTINATION_LIMIT_ALGORITHM;
import static com.android.adservices.service.Flags.MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES;
import static com.android.adservices.service.Flags.MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT;
import static com.android.adservices.service.Flags.MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW_IN_MS;
import static com.android.adservices.service.Flags.MEASUREMENT_DESTINATION_RATE_LIMIT_WINDOW;
import static com.android.adservices.service.Flags.MEASUREMENT_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_MAX_ADR_COUNT_PER_SOURCE;
import static com.android.adservices.service.Flags.MEASUREMENT_MAX_FILTERING_ID_MAX_BYTES;
import static com.android.adservices.service.Flags.MEASUREMENT_MAX_LENGTH_PER_BUDGET_NAME;
import static com.android.adservices.service.Flags.MEASUREMENT_MAX_NAMED_BUDGETS_PER_SOURCE_REGISTRATION;
import static com.android.adservices.service.Flags.MEASUREMENT_MAX_REINSTALL_REATTRIBUTION_WINDOW_SECONDS;
import static com.android.adservices.service.Flags.MEASUREMENT_MIN_REPORT_LIFESPAN_FOR_UNINSTALL_SECONDS;
import static com.android.adservices.service.Flags.MEASUREMENT_REPORTING_JOB_PERSISTED;
import static com.android.adservices.service.Flags.MEASUREMENT_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW;
import static com.android.adservices.service.Flags.MEASUREMENT_REPORTING_JOB_REQUIRED_NETWORK_TYPE;
import static com.android.adservices.service.Flags.MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS;
import static com.android.adservices.service.Flags.MEASUREMENT_REPORTING_JOB_SERVICE_MIN_EXECUTION_WINDOW_MILLIS;
import static com.android.adservices.service.Flags.MEASUREMENT_TRIGGER_DEBUG_SIGNAL_PROBABILITY_FOR_FAKE_REPORTS;
import static com.android.adservices.service.Flags.PPAPI_AND_SYSTEM_SERVER;
import static com.android.adservices.service.Flags.PROTECTED_SIGNALS_MAX_JS_FAILURE_EXECUTION_ON_CERTAIN_VERSION_BEFORE_STOP;
import static com.android.adservices.service.Flags.PROTECTED_SIGNALS_MAX_SIGNAL_SIZE_PER_BUYER_BYTES;
import static com.android.adservices.service.Flags.PROTECTED_SIGNALS_MAX_SIGNAL_SIZE_PER_BUYER_WITH_OVERSUBSCIPTION_BYTES;
import static com.android.adservices.service.Flags.TOPICS_EPOCH_JOB_FLEX_MS;
import static com.android.adservices.shared.common.flags.ModuleSharedFlags.DEFAULT_JOB_SCHEDULING_LOGGING_ENABLED;
import static com.android.adservices.shared.testing.AndroidSdk.SC;
import static com.android.adservices.shared.testing.AndroidSdk.SC_V2;

import android.util.Log;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.common.flags.ConfigFlag;
import com.android.adservices.shared.common.flags.FeatureFlag;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.RequiresSdkRange;
import com.android.internal.util.Preconditions;

import org.junit.AssumptionViolatedException;
import org.junit.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// NOTE: when adding a new method to the class, try to find the proper "block"
public final class FlagsTest extends AdServicesUnitTestCase {

    private static final String TAG = FlagsTest.class.getSimpleName();

    static final String REASON_TO_NOT_MOCK_SDK_LEVEL =
            "Uses Flags.java constant that checks SDK level when the class is instantiated, hence"
                    + " calls to static SdkLevel methods cannot be mocked";

    private final Flags mFlags = new Flags() {};

    private final Flags mGlobalKsOnFlags = new GlobalKillSwitchAwareFlags(true);
    private final Flags mGlobalKsOffFlags = new GlobalKillSwitchAwareFlags(false);

    private final Flags mMsmtEnabledFlags = new MsmtFeatureAwareFlags(true);
    private final Flags mMsmtDisabledFlags = new MsmtFeatureAwareFlags(false);

    private final Flags mMsmtKsOnFlags = new MsmtKillSwitchAwareFlags(true);
    private final Flags mMsmtKsOffFlags = new MsmtKillSwitchAwareFlags(false);

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Tests for flags that depend on SDK level.                                                  //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    @RequiresSdkRange(atLeast = SC, atMost = SC_V2, reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testConsentSourceOfTruth_isS() {
        assertConsentSourceOfTruth(APPSEARCH_ONLY);
    }

    @Test
    @RequiresSdkLevelAtLeastT(reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testConsentSourceOfTruth_isAtLeastT() {
        assertConsentSourceOfTruth(PPAPI_AND_SYSTEM_SERVER);
    }

    private void assertConsentSourceOfTruth(int expected) {
        expect.withMessage("DEFAULT_CONSENT_SOURCE_OF_TRUTH")
                .that(DEFAULT_CONSENT_SOURCE_OF_TRUTH)
                .isEqualTo(expected);

        expect.withMessage("getConsentSourceOfTruth()")
                .that(mFlags.getConsentSourceOfTruth())
                .isEqualTo(expected);
    }

    @Test
    @RequiresSdkRange(atLeast = SC, atMost = SC_V2, reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testBlockedTopicsConsentSourceOfTruth_isS() {
        assertBlockedTopicsSourceOfTruth(APPSEARCH_ONLY);
    }

    @Test
    @RequiresSdkLevelAtLeastT(reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testBlockedTopicsConsentSourceOfTruth_isAtLeastT() {
        assertBlockedTopicsSourceOfTruth(PPAPI_AND_SYSTEM_SERVER);
    }

    private void assertBlockedTopicsSourceOfTruth(int expected) {
        expect.withMessage("DEFAULT_BLOCKED_TOPICS_SOURCE_OF_TRUTH")
                .that(DEFAULT_BLOCKED_TOPICS_SOURCE_OF_TRUTH)
                .isEqualTo(expected);

        expect.withMessage("getBlockedTopicsSourceOfTruth()")
                .that(mFlags.getBlockedTopicsSourceOfTruth())
                .isEqualTo(expected);
    }

    @Test
    @RequiresSdkRange(atLeast = SC, atMost = SC_V2, reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testEnableAppsearchConsentData_isS() {
        assertEnableAppsearchConsentData(true);
    }

    @Test
    @RequiresSdkLevelAtLeastT(reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testEnableAppsearchConsentData_isAtLeastT() {
        assertEnableAppsearchConsentData(false);
    }

    private void assertEnableAppsearchConsentData(boolean expected) {
        expect.withMessage("ENABLE_APPSEARCH_CONSENT_DATA")
                .that(ENABLE_APPSEARCH_CONSENT_DATA)
                .isEqualTo(expected);

        expect.withMessage("getEnableAppsearchConsentData()")
                .that(mFlags.getEnableAppsearchConsentData())
                .isEqualTo(expected);
    }

    @Test
    public void testGetAdServicesModuleJobPolicy() {
        expect.withMessage("AD_SERVICES_MODULE_JOB_POLICY")
                .that(AD_SERVICES_MODULE_JOB_POLICY)
                .isEqualTo("");
        expect.withMessage("getAdServicesModuleJobPolicy()")
                .that(mFlags.getAdServicesModuleJobPolicy())
                .isEqualTo(AD_SERVICES_MODULE_JOB_POLICY);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Tests for (legacy) kill-switch flags that are already in production - the flag name cannot //
    // change, but their underlying getter / constants might.                                     //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void testGetGlobalKillSwitch() {
        // Getter
        expect.withMessage("getGlobalKillSwitch()").that(mFlags.getGlobalKillSwitch()).isTrue();

        // Constant
        expect.withMessage("GLOBAL_KILL_SWITCH").that(GLOBAL_KILL_SWITCH).isTrue();
    }

    @Test
    public void testGetTopicsKillSwitch() {
        testNewKillSwitchGuardedByGlobalKillSwitch(
                "TOPICS_KILL_SWITCH", Flags::getTopicsKillSwitch);
    }

    @Test
    public void testGetLegacyMeasurementKillSwitch() {
        testRampedUpKillSwitchGuardedByGlobalKillSwitch(
                "MEASUREMENT_KILL_SWITCH", Flags::getLegacyMeasurementKillSwitch);
        expect.withMessage("getLegacyMeasurementKillSwitch()")
                .that(mFlags.getLegacyMeasurementKillSwitch())
                .isEqualTo(!mFlags.getMeasurementEnabled());
        expect.withMessage("getLegacyMeasurementKillSwitch() when global kill_switch is enabled")
                .that(mGlobalKsOnFlags.getLegacyMeasurementKillSwitch())
                .isEqualTo(!mGlobalKsOnFlags.getMeasurementEnabled());
        expect.withMessage("getLegacyMeasurementKillSwitch() when global kill_switch is enabled")
                .that(mGlobalKsOffFlags.getLegacyMeasurementKillSwitch())
                .isEqualTo(!mGlobalKsOffFlags.getMeasurementEnabled());
    }

    @Test
    public void testGetMeasurementEnabled() {
        testFeatureFlagBasedOnLegacyKillSwitchAndGuardedByGlobalKillSwitch(
                "getMeasurementEnabled()", MEASUREMENT_KILL_SWITCH, Flags::getMeasurementEnabled);
    }

    @Test
    public void testGetMddLoggerEnabled() {
        testFeatureFlagBasedOnLegacyKillSwitchAndGuardedByGlobalKillSwitch(
                "getMddLoggerEnabled()", MDD_LOGGER_KILL_SWITCH, Flags::getMddLoggerEnabled);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Tests for feature flags.                                                                   //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void testGetEnablePackageDenyService() {
        testFeatureFlag("DEFAULT_ENABLE_PACKAGE_DENY_SERVICE", Flags::getEnablePackageDenyService);
    }

    @Test
    public void testGetEnablePackageDenyMdd() {
        testFeatureFlag("DEFAULT_ENABLE_PACKAGE_DENY_MDD", Flags::getEnablePackageDenyMdd);
    }

    @Test
    public void testGetEnablePackageDenyJobOnPackageAdd() {
        testFeatureFlag(
                "DEFAULT_ENABLE_PACKAGE_DENY_JOB_ON_PACKAGE_ADD",
                Flags::getEnablePackageDenyJobOnPackageAdd);
    }

    @Test
    public void testGetEnablePackageDenyBgJob() {
        testFeatureFlag("DEFAULT_ENABLE_PACKAGE_DENY_BG_JOB", Flags::getEnablePackageDenyBgJob);
    }

    @Test
    public void testGetEnableInstalledPackageFilter() {
        testFeatureFlag(
                "DEFAULT_PACKAGE_DENY_ENABLE_INSTALLED_PACKAGE_FILTER",
                Flags::getPackageDenyEnableInstalledPackageFilter);
    }

    @Test
    public void testGetBackgroundJobPeriodMillis() {
        testFlag(
                "getBackgroundJobPeriodMillis",
                DEFAULT_PACKAGE_DENY_BACKGROUND_JOB_PERIOD_MILLIS,
                Flags::getPackageDenyBackgroundJobPeriodMillis);
    }

    @Test
    public void testGetEnablePackageDenyJobOnMddDownload() {
        testFeatureFlag(
                "DEFAULT_ENABLE_PACKAGE_DENY_JOB_ON_MDD_DOWNLOAD",
                Flags::getEnablePackageDenyJobOnMddDownload);
    }

    @Test
    public void testGetProtectedSignalsEnabled() {
        testFeatureFlagGuardedByGlobalKillSwitch(
                "PROTECTED_SIGNALS_ENABLED", Flags::getProtectedSignalsEnabled);
    }

    @Test
    public void testGetCobaltLoggingEnabled() {
        testFeatureFlagGuardedByGlobalKillSwitch(
                "COBALT_LOGGING_ENABLED", Flags::getCobaltLoggingEnabled);
    }

    @Test
    public void testGetMsmtRegistrationCobaltLoggingEnabled() {
        testFeatureFlagGuardedByGlobalKillSwitch(
                "MSMT_REGISTRATION_COBALT_LOGGING_ENABLED",
                Flags::getMsmtRegistrationCobaltLoggingEnabled);
    }

    @Test
    public void testGetMsmtAttributionCobaltLoggingEnabled() {
        testFeatureFlagGuardedByGlobalKillSwitch(
                "MSMT_ATTRIBUTION_COBALT_LOGGING_ENABLED",
                Flags::getMsmtAttributionCobaltLoggingEnabled);
    }

    @Test
    public void testGetMsmtReportigCobaltLoggingEnabled() {
        testFeatureFlagGuardedByGlobalKillSwitch(
                "MSMT_REPORTING_COBALT_LOGGING_ENABLED",
                Flags::getMsmtReportingCobaltLoggingEnabled);
    }

    @Test
    public void testGetMeasurementEnableHeaderErrorDebugReport() {
        testFeatureFlagGuardedByGlobalKillSwitch(
                "MEASUREMENT_ENABLE_HEADER_ERROR_DEBUG_REPORT",
                Flags::getMeasurementEnableHeaderErrorDebugReport);
    }

    @Test
    public void testGetEnableBackCompat() {
        testFeatureFlag("ENABLE_BACK_COMPAT", Flags::getEnableBackCompat);
    }

    @Test
    public void testGetFledgeAuctionServerGetAdSelectionDataPayloadMetricsEnabled() {
        testFeatureFlag(
                "FLEDGE_AUCTION_SERVER_GET_AD_SELECTION_DATA_PAYLOAD_METRICS_ENABLED",
                Flags::getFledgeAuctionServerGetAdSelectionDataPayloadMetricsEnabled);
    }

    @Test
    public void testGetFledgeAuctionServerKeyFetchMetricsEnabled() {
        testFeatureFlag(
                "FLEDGE_AUCTION_SERVER_KEY_FETCH_METRICS_ENABLED",
                Flags::getFledgeAuctionServerKeyFetchMetricsEnabled);
    }

    @Test
    public void testGetFledgeSelectAdsFromOutcomesApiMetricsEnabled() {
        testFeatureFlag(
                "FLEDGE_SELECT_ADS_FROM_OUTCOMES_API_METRICS_ENABLED",
                Flags::getFledgeSelectAdsFromOutcomesApiMetricsEnabled);
    }

    @Test
    public void testGetFledgeCpcBillingMetricsEnabled() {
        testFeatureFlag(
                "FLEDGE_CPC_BILLING_METRICS_ENABLED", Flags::getFledgeCpcBillingMetricsEnabled);
    }

    @Test
    public void testGetFledgeDataVersionHeaderMetricsEnabled() {
        testFeatureFlag(
                "FLEDGE_DATA_VERSION_HEADER_METRICS_ENABLED",
                Flags::getFledgeDataVersionHeaderMetricsEnabled);
    }

    @Test
    public void testGetFledgeReportImpressionApiMetricsEnabled() {
        testFeatureFlag(
                "FLEDGE_REPORT_IMPRESSION_API_METRICS_ENABLED",
                Flags::getFledgeReportImpressionApiMetricsEnabled);
    }

    @Test
    public void testGetFledgeJsScriptResultCodeMetricsEnabled() {
        testFeatureFlag(
                "FLEDGE_JS_SCRIPT_RESULT_CODE_METRICS_ENABLED",
                Flags::getFledgeJsScriptResultCodeMetricsEnabled);
    }

    @Test
    public void testGetSpeOnPilotJobsEnabled() {
        testFeatureFlag("DEFAULT_SPE_ON_PILOT_JOBS_ENABLED", Flags::getSpeOnPilotJobsEnabled);
    }

    @Test
    public void testGetEnrollmentApiBasedSchemaEnabled() {
        testFeatureFlag(
                "ENROLLMENT_API_BASED_SCHEMA_ENABLED", Flags::getEnrollmentApiBasedSchemaEnabled);
    }

    @Test
    public void testGetEnableEnrollmentConfigV3Db() {
        testFeatureFlag(
                "DEFAULT_ENABLE_ENROLLMENT_CONFIG_V3_DB", Flags::getEnableEnrollmentConfigV3Db);
    }

    @Test
    public void testGetUseConfigsManagerToQueryEnrollment() {
        testFeatureFlag(
                "DEFAULT_USE_CONFIGS_MANAGER_TO_QUERY_ENROLLMENT",
                Flags::getUseConfigsManagerToQueryEnrollment);
    }

    @Test
    public void testGetSharedDatabaseSchemaVersion4Enabled() {
        testFeatureFlag(
                "SHARED_DATABASE_SCHEMA_VERSION_4_ENABLED",
                Flags::getSharedDatabaseSchemaVersion4Enabled);
    }

    @Test
    public void testGetJobSchedulingLoggingEnabled() {
        testFlag(
                "getJobSchedulingLoggingEnabled()",
                DEFAULT_JOB_SCHEDULING_LOGGING_ENABLED,
                Flags::getJobSchedulingLoggingEnabled);
    }

    @Test
    public void testGetMeasurementMaxLengthPerBudgetName() {
        testFlag(
                "getMeasurementMaxLengthPerBudgetName()",
                MEASUREMENT_MAX_LENGTH_PER_BUDGET_NAME,
                Flags::getMeasurementMaxLengthPerBudgetName);
    }

    @Test
    public void testGetMeasurementMaxNamedBudgetsPerSourceRegistration() {
        testFlag(
                "getMeasurementMaxNamedBudgetsPerSourceRegistration()",
                MEASUREMENT_MAX_NAMED_BUDGETS_PER_SOURCE_REGISTRATION,
                Flags::getMeasurementMaxNamedBudgetsPerSourceRegistration);
    }

    @Test
    public void testGetMeasurementEnableTriggerDebugSignal() {
        testFeatureFlag(
                "MEASUREMENT_ENABLE_TRIGGER_DEBUG_SIGNAL",
                Flags::getMeasurementEnableTriggerDebugSignal);
    }

    @Test
    public void testGetMeasurementEnableEventTriggerDebugSignalForCoarseDestination() {
        testFeatureFlag(
                "MEASUREMENT_ENABLE_EVENT_TRIGGER_DEBUG_SIGNAL_FOR_COARSE_DESTINATION",
                Flags::getMeasurementEnableEventTriggerDebugSignalForCoarseDestination);
    }

    @Test
    public void testGetMeasurementTriggerDebugProbabilityForFakeReports() {
        testFloatFlag(
                "getMeasurementTriggerDebugSignalProbabilityForFakeReports",
                MEASUREMENT_TRIGGER_DEBUG_SIGNAL_PROBABILITY_FOR_FAKE_REPORTS,
                Flags::getMeasurementTriggerDebugSignalProbabilityForFakeReports);
    }

    @Test
    public void testGetEnableBackCompatInit() {
        testFeatureFlag("DEFAULT_ENABLE_BACK_COMPAT_INIT", Flags::getEnableBackCompatInit);
    }

    @Test
    public void testGetMsmtEnableSeparateReportTypes() {
        testFeatureFlag(
                "MEASUREMENT_ENABLE_SEPARATE_DEBUG_REPORT_TYPES_FOR_ATTRIBUTION_RATE_LIMIT",
                Flags::getMeasurementEnableSeparateDebugReportTypesForAttributionRateLimit);
    }

    @Test
    public void testGetMeasurementEnableReinstallReattribution() {
        testFeatureFlag(
                "MEASUREMENT_ENABLE_REINSTALL_REATTRIBUTION",
                Flags::getMeasurementEnableReinstallReattribution);
    }

    @Test
    public void testGetMeasurementEnableMinReportLifespanForUninstall() {
        testFeatureFlag(
                "MEASUREMENT_ENABLE_MIN_REPORT_LIFESPAN_FOR_UNINSTALL",
                Flags::getMeasurementEnableMinReportLifespanForUninstall);
    }

    @Test
    public void testGetMeasurementEnableInstallAttributionOnS() {
        testFeatureFlag(
                "MEASUREMENT_ENABLE_INSTALL_ATTRIBUTION_ON_S",
                Flags::getMeasurementEnableInstallAttributionOnS);
    }

    @Test
    public void testGetMeasurementEnableDestinationLimitPriority() {
        testFeatureFlag(
                "MEASUREMENT_ENABLE_DESTINATION_LIMIT_PRIORITY",
                Flags::getMeasurementEnableSourceDestinationLimitPriority);
    }

    @Test
    public void testGetMeasurementEnableDestinationPerDayRateLimitWindow() {
        testFeatureFlag(
                "MEASUREMENT_ENABLE_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW",
                Flags::getMeasurementEnableDestinationPerDayRateLimitWindow);
    }

    @Test
    public void testGetMeasurementEnableSourceDestinationLimitAlgorithmField() {
        testFeatureFlag(
                "MEASUREMENT_ENABLE_DESTINATION_LIMIT_ALGORITHM_FIELD",
                Flags::getMeasurementEnableSourceDestinationLimitAlgorithmField);
    }

    @Test
    public void testGetMeasurementEnableEventLevelEpsilonInSource() {
        testFeatureFlag(
                "MEASUREMENT_ENABLE_EVENT_LEVEL_EPSILON_IN_SOURCE",
                Flags::getMeasurementEnableEventLevelEpsilonInSource);
    }

    @Test
    public void testGetMeasurementEnableAggregateValueFilters() {
        testFeatureFlag(
                "MEASUREMENT_ENABLE_AGGREGATE_VALUE_FILTERS",
                Flags::getMeasurementEnableAggregateValueFilters);
    }

    @Test
    public void testGetMeasurementEnableAggregatableNamedBudgets() {
        testFeatureFlag(
                "MEASUREMENT_ENABLE_AGGREGATABLE_NAMED_BUDGETS",
                Flags::getMeasurementEnableAggregatableNamedBudgets);
    }

    @Test
    public void testGetMeasurementEnableV1SourceTriggerData() {
        testFeatureFlag(
                "MEASUREMENT_ENABLE_V1_SOURCE_TRIGGER_DATA",
                Flags::getMeasurementEnableV1SourceTriggerData);
    }

    @Test
    public void testGetCustomErrorCodeSamplingEnabled() {
        testFeatureFlag(
                "DEFAULT_CUSTOM_ERROR_CODE_SAMPLING_ENABLED",
                Flags::getCustomErrorCodeSamplingEnabled);
    }

    @Test
    public void testGetSpeOnPilotJobsBatch2Enabled() {
        testFeatureFlag(
                "DEFAULT_SPE_ON_PILOT_JOBS_BATCH_2_ENABLED", Flags::getSpeOnPilotJobsBatch2Enabled);
    }

    @Test
    public void testGetSpeOnEpochJobEnabled() {
        testFeatureFlag("DEFAULT_SPE_ON_EPOCH_JOB_ENABLED", Flags::getSpeOnEpochJobEnabled);
    }

    @Test
    public void testGetSpeOnBackgroundFetchJobEnabled() {
        testFeatureFlag(
                "DEFAULT_SPE_ON_BACKGROUND_FETCH_JOB_ENABLED",
                Flags::getSpeOnBackgroundFetchJobEnabled);
    }

    @Test
    public void testGetSpeOnAsyncRegistrationFallbackJobEnabled() {
        testFeatureFlag(
                "DEFAULT_SPE_ON_ASYNC_REGISTRATION_FALLBACK_JOB_ENABLED",
                Flags::getSpeOnAsyncRegistrationFallbackJobEnabled);
    }

    @Test
    public void testGetMeasurementReportingJobServiceEnabled() {
        testFeatureFlag(
                "MEASUREMENT_REPORTING_JOB_ENABLED",
                Flags::getMeasurementReportingJobServiceEnabled);
    }

    @Test
    public void testGetMddEnrollmentManifestFileUrl() {
        testFlag("getMddEnrollmentManifestFileUrl()", "", Flags::getMddEnrollmentManifestFileUrl);
    }

    @Test
    public void testGetEnrollmentProtoFileEnabled() {
        testFeatureFlag(
                "DEFAULT_ENROLLMENT_PROTO_FILE_ENABLED", Flags::getEnrollmentProtoFileEnabled);
    }

    @Test
    public void testGetCobaltOperationalLoggingEnabled() {
        testFeatureFlag(
                "COBALT_OPERATIONAL_LOGGING_ENABLED", Flags::getCobaltOperationalLoggingEnabled);
    }

    @Test
    public void testGetCobaltRegistryOutOfBandUpdateEnabled() {
        testFeatureFlag(
                "COBALT_REGISTRY_OUT_OF_BAND_UPDATE_ENABLED",
                Flags::getCobaltRegistryOutOfBandUpdateEnabled);
    }

    @Test
    public void testGetCobaltFallBackToDefaultBaseRegistry() {
        testFeatureFlag(
                "COBALT__FALL_BACK_TO_DEFAULT_BASE_REGISTRY",
                Flags::getCobaltFallBackToDefaultBaseRegistry);
    }

    @Test
    public void testGetCobaltEnableApiCallResponseLogging() {
        testFeatureFlag(
                "COBALT__ENABLE_API_CALL_RESPONSE_LOGGING",
                Flags::getCobaltEnableApiCallResponseLogging);
    }

    @Test
    public void testGetRNotificationDefaultConsentFixEnabled() {
        testFeatureFlag(
                "DEFAULT_R_NOTIFICATION_DEFAULT_CONSENT_FIX_ENABLED",
                Flags::getRNotificationDefaultConsentFixEnabled);
    }

    @Test
    public void testTopicsJobSchedulerRescheduleEnabled() {
        testFeatureFlag(
                "TOPICS_JOB_SCHEDULER_RESCHEDULE_ENABLED",
                Flags::getTopicsJobSchedulerRescheduleEnabled);
    }

    @Test
    public void testTopicsEpochJobBatteryNotLowInsteadOfCharging() {
        testFlag(
                "TOPICS_EPOCH_JOB_BATTERY_NOT_LOW_INSTEAD_OF_CHARGING",
                /* defaultValue */ false,
                Flags::getTopicsEpochJobBatteryNotLowInsteadOfCharging);
    }

    @Test
    public void testGetTopicsEpochJobBatteryConstraintLoggingEnabled() {
        testFlag(
                "TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_LOGGING_ENABLED",
                /* defaultValue */ false,
                Flags::getTopicsEpochJobBatteryConstraintLoggingEnabled);
    }

    @Test
    public void testGetTopicsCleanDBWhenEpochJobSettingsChanged() {
        testFlag(
                "TOPICS_CLEAN_DB_WHEN_EPOCH_JOB_SETTINGS_CHANGED",
                /* defaultValue */ false,
                Flags::getTopicsCleanDBWhenEpochJobSettingsChanged);
    }

    @Test
    public void testGetFledgeEnableForcedEncodingAfterSignalsUpdate() {
        testFeatureFlag(
                "FLEDGE_ENABLE_FORCED_ENCODING_AFTER_SIGNALS_UPDATE",
                Flags::getFledgeEnableForcedEncodingAfterSignalsUpdate);
    }

    @Test
    public void testGetEnableRbAtrace() {
        testFlag("DEFAULT_ENABLE_RB_ATRACE", /* defaultValue */ false, Flags::getEnableRbAtrace);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Tests for (legacy) kill-switch flags that will be refactored as feature flag - they should //
    // move to the block above once refactored.                                                   //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void testGetMeasurementApiDeleteRegistrationsKillSwitch() {
        testLegacyMsmtKillSwitchGuardedByMsmtKillSwitch(
                "getMeasurementApiDeleteRegistrationsKillSwitch()",
                "MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH",
                Flags::getMeasurementApiDeleteRegistrationsKillSwitch);
    }

    // TODO(b/325074749) - remove once all flags have been converted

    /**
     * @deprecated - flags that are converted should call some method like {@code
     *     testFeatureFlagGuardedByMsmtFeatureFlag} instead.
     */
    @Deprecated
    @SuppressWarnings("UnusedMethod") // will be used as more kill switches are refactored
    private void testLegacyMsmtKillSwitchGuardedByMsmtKillSwitch(
            String getterName, String killSwitchName, Flaginator<Flags, Boolean> flaginator) {
        boolean defaultKillSwitchValue = getConstantValue(killSwitchName);

        // Getter
        expect.withMessage("%s when global kill_switch is on", getterName)
                .that(flaginator.getFlagValue(mGlobalKsOnFlags))
                .isTrue();
        expect.withMessage("%s when msmt_enabled is true", getterName)
                .that(flaginator.getFlagValue(mMsmtEnabledFlags))
                .isEqualTo(defaultKillSwitchValue);
        expect.withMessage("%s when msmt enabled is false", getterName)
                .that(flaginator.getFlagValue(mMsmtDisabledFlags))
                .isFalse();

        // TODO(b/325074749): remove 2 checks below once Flags.getLegacyMeasurementKillSwitch() is
        // gone
        expect.withMessage("%s when msmt kill_switch is on", getterName)
                .that(flaginator.getFlagValue(mMsmtKsOnFlags))
                .isTrue();
        expect.withMessage("%s when msmt kill_switch is off", getterName)
                .that(flaginator.getFlagValue(mMsmtKsOffFlags))
                .isEqualTo(defaultKillSwitchValue);

        // Constant
        expect.withMessage("%s", killSwitchName).that(defaultKillSwitchValue).isFalse();
    }

    @Test
    public void testGetPasExtendedMetricsEnabled() {
        testFeatureFlag("PAS_EXTENDED_METRICS_ENABLED", Flags::getPasExtendedMetricsEnabled);
    }

    @Test
    public void testGetPasProductMetricsV1Enabled() {
        testFeatureFlag("PAS_PRODUCT_METRICS_V1_ENABLED", Flags::getPasProductMetricsV1Enabled);
    }

    @Test
    public void testGetMeasurementAttributionFallbackJobEnabled() {
        testMsmtFeatureFlagBackedByLegacyKillSwitchAndGuardedByMsmtEnabled(
                "getMeasurementAttributionFallbackJobEnabled()",
                "MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_KILL_SWITCH",
                flag -> flag.getMeasurementAttributionFallbackJobEnabled());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Tests for feature flags that already launched - they will eventually be removed (once the  //
    // underlying getter is removed).                                                             //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // NOTE: there isn't any such flag currently, so commented code below is shown as an example
    // @Test
    // public void testGetAppConfigReturnsEnabledByDefault() {
    //     testRetiredFeatureFlag(
    //             "APP_CONFIG_RETURNS_ENABLED_BY_DEFAULT",
    //             Flags::getAppConfigReturnsEnabledByDefault);
    // }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Tests for "config" flags (not feature flag / kill switch).                                 //
    ////////////////////////////////////////////////////////////////////////////////////////////////
    @Test
    public void testGetTopicsEpochJobFlexMs() {
        testFlag(
                "getTopicsEpochJobFlexMs()",
                TOPICS_EPOCH_JOB_FLEX_MS,
                Flags::getTopicsEpochJobFlexMs);
    }

    @Test
    public void testGetMeasurementDefaultDestinationLimitAlgorithm() {
        testFlag(
                "getMeasurementDefaultSourceDestinationLimitAlgorithm()",
                MEASUREMENT_DEFAULT_DESTINATION_LIMIT_ALGORITHM,
                Flags::getMeasurementDefaultSourceDestinationLimitAlgorithm);
    }

    @Test
    public void testGetMeasurementDestinationRateLimitWindow() {
        testFlag(
                "getMeasurementDestinationRateLimitWindow()",
                MEASUREMENT_DESTINATION_RATE_LIMIT_WINDOW,
                Flags::getMeasurementDestinationRateLimitWindow);
    }

    @Test
    public void testGetMeasurementDestinationPerDayRateLimitWindowInMs() {
        testFlag(
                "getMeasurementDestinationPerDayRateLimitWindowInMs()",
                MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW_IN_MS,
                Flags::getMeasurementDestinationPerDayRateLimitWindowInMs);
    }

    @Test
    public void testGetMeasurementDestinationPerDayRateLimit() {
        testFlag(
                "getMeasurementDestinationPerDayRateLimit()",
                MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT,
                Flags::getMeasurementDestinationPerDayRateLimit);
    }

    @Test
    public void testGetJobSchedulingLoggingSamplingRate() {
        testFlag(
                "getJobSchedulingLoggingSamplingRate()",
                DEFAULT_JOB_SCHEDULING_LOGGING_SAMPLING_RATE,
                Flags::getJobSchedulingLoggingSamplingRate);
    }

    @Test
    public void testGetPasScriptDownloadReadTimeoutMs() {
        testFlag(
                "getPasScriptDownloadReadTimeoutMs()",
                DEFAULT_PAS_SCRIPT_DOWNLOAD_READ_TIMEOUT_MS,
                Flags::getPasScriptDownloadReadTimeoutMs);
    }

    @Test
    public void testGetPasScriptDownloadConnectionTimeoutMs() {
        testFlag(
                "getPasScriptDownloadConnectionTimeoutMs()",
                DEFAULT_PAS_SCRIPT_DOWNLOAD_CONNECTION_TIMEOUT_MS,
                Flags::getPasScriptDownloadConnectionTimeoutMs);
    }

    @Test
    public void testGetPasSignalsDownloadReadTimeoutMs() {
        testFlag(
                "getPasSignalsDownloadReadTimeoutMs()",
                DEFAULT_PAS_SIGNALS_DOWNLOAD_READ_TIMEOUT_MS,
                Flags::getPasSignalsDownloadReadTimeoutMs);
    }

    @Test
    public void testGetPasSignalsDownloadConnectionTimeoutMs() {
        testFlag(
                "getPasSignalsDownloadConnectionTimeoutMs()",
                DEFAULT_PAS_SIGNALS_DOWNLOAD_CONNECTION_TIMEOUT_MS,
                Flags::getPasSignalsDownloadConnectionTimeoutMs);
    }

    @Test
    public void testGetPasScriptExecutionTimeoutMs() {
        testFlag(
                "getPasScriptExecutionTimeoutMs()",
                DEFAULT_PAS_SCRIPT_EXECUTION_TIMEOUT_MS,
                Flags::getPasScriptExecutionTimeoutMs);
    }

    @Test
    public void testGetAdServicesApiV2MigrationEnabled() {
        testFeatureFlag(
                "DEFAULT_ADSERVICES_CONSENT_BUSINESS_LOGIC_MIGRATION_ENABLED",
                Flags::getAdServicesConsentBusinessLogicMigrationEnabled);
    }

    @Test
    public void testGetMeasurementMaxReinstallReattributionWindowSeconds() {
        testFlag(
                "getMeasurementMaxReinstallReattributionWindowSeconds",
                MEASUREMENT_MAX_REINSTALL_REATTRIBUTION_WINDOW_SECONDS,
                Flags::getMeasurementMaxReinstallReattributionWindowSeconds);
    }

    @Test
    public void testGetMeasurementMinReportLifespanForUninstallSeconds() {
        testFlag(
                "getMeasurementMinReportLifespanForUninstallSeconds",
                MEASUREMENT_MIN_REPORT_LIFESPAN_FOR_UNINSTALL_SECONDS,
                Flags::getMeasurementMinReportLifespanForUninstallSeconds);
    }

    @Test
    public void testGetMeasurementReportingJobRequiredBatteryNotLow() {
        testFlag(
                "getMeasurementReportingJobRequiredBatteryNotLow",
                MEASUREMENT_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW,
                Flags::getMeasurementReportingJobRequiredBatteryNotLow);
    }

    @Test
    public void testGetMeasurementReportingJobRequiredNetworkType() {
        testFlag(
                "getMeasurementReportingJobRequiredNetworkType",
                MEASUREMENT_REPORTING_JOB_REQUIRED_NETWORK_TYPE,
                Flags::getMeasurementReportingJobRequiredNetworkType);
    }

    @Test
    public void testGetMeasurementReportingJobPersisted() {
        testFlag(
                "getMeasurementReportingJobPersisted",
                MEASUREMENT_REPORTING_JOB_PERSISTED,
                Flags::getMeasurementReportingJobPersisted);
    }

    @Test
    public void testGetMeasurementReportingJobServiceBatchWindowMillis() {
        testFlag(
                "getMeasurementReportingJobServiceBatchWindowMillis",
                MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS,
                Flags::getMeasurementReportingJobServiceBatchWindowMillis);
    }

    @Test
    public void testGetMeasurementReportingJobServiceMinExecutionWindowMillis() {
        testFlag(
                "getMeasurementReportingJobServiceMinExecutionWindowMillis",
                MEASUREMENT_REPORTING_JOB_SERVICE_MIN_EXECUTION_WINDOW_MILLIS,
                Flags::getMeasurementReportingJobServiceMinExecutionWindowMillis);
    }

    @Test
    public void testGetMeasurementAttributionScopeMaxInfoGainNavigation() {
        testFloatFlag(
                "getMeasurementAttributionScopeMaxInfoGainNavigation",
                MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_NAVIGATION,
                Flags::getMeasurementAttributionScopeMaxInfoGainNavigation);
    }

    @Test
    public void testGetMeasurementAttributionScopeMaxInfoGainDualDestinationNavigation() {
        testFloatFlag(
                "getMeasurementAttributionScopeMaxInfoGainDualDestinationNavigation",
                MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_NAVIGATION,
                Flags::getMeasurementAttributionScopeMaxInfoGainDualDestinationNavigation);
    }

    @Test
    public void testGetMeasurementAttributionScopeMaxInfoGainEvent() {
        testFloatFlag(
                "getMeasurementAttributionScopeMaxInfoGainEvent",
                MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_EVENT,
                Flags::getMeasurementAttributionScopeMaxInfoGainEvent);
    }

    @Test
    public void testGetMeasurementAttributionScopeMaxInfoGainDualDestinationEvent() {
        testFloatFlag(
                "getMeasurementAttributionScopeMaxInfoGainDualDestinationEvent",
                MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_EVENT,
                Flags::getMeasurementAttributionScopeMaxInfoGainDualDestinationEvent);
    }

    @Test
    public void testGetMeasurementEnableFakeReportTriggerTime() {
        testFeatureFlag(
                "MEASUREMENT_ENABLE_FAKE_REPORT_TRIGGER_TIME",
                Flags::getMeasurementEnableFakeReportTriggerTime);
    }

    @Test
    public void testGetMeasurementDefaultFilteringIdMaxBytes() {
        testFlag(
                "getMeasurementDefaultFilteringIdMaxBytes",
                MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES,
                Flags::getMeasurementDefaultFilteringIdMaxBytes);
    }

    @Test
    public void testGetMeasurementValidFilteringIdMaxBytes() {
        testFlag(
                "getMeasurementValidFilteringIdMaxBytes",
                MEASUREMENT_MAX_FILTERING_ID_MAX_BYTES,
                Flags::getMeasurementMaxFilteringIdMaxBytes);
    }

    @Test
    public void testGetMeasurementEnableFlexibleContributionFiltering() {
        testFeatureFlag(
                "MEASUREMENT_ENABLE_FLEXIBLE_CONTRIBUTION_FILTERING",
                Flags::getMeasurementEnableFlexibleContributionFiltering);
    }

    @Test
    public void testGetMeasurementEnableAggregateDebugReporting() {
        testFeatureFlag(
                "MEASUREMENT_ENABLE_AGGREGATE_DEBUG_REPORTING",
                Flags::getMeasurementEnableAggregateDebugReporting);
    }

    @Test
    public void testGetMeasurementEnableBothSideDebugKeysInReports() {
        testFeatureFlag(
                "MEASUREMENT_ENABLE_BOTH_SIDE_DEBUG_KEYS_IN_REPORTS",
                Flags::getMeasurementEnableBothSideDebugKeysInReports);
    }

    @Test
    public void testGetFledgeGetAdSelectionDataBuyerInputCreatorVersion() {
        testFlag(
                "getFledgeGetAdSelectionDataBuyerInputCreatorVersion",
                FLEDGE_GET_AD_SELECTION_DATA_BUYER_INPUT_CREATOR_VERSION,
                Flags::getFledgeGetAdSelectionDataBuyerInputCreatorVersion);
    }

    @Test
    public void testGetFledgeGetAdSelectionDataBuyerInputMaxNumEntirePayloadCompressions() {
        testFlag(
                "getFledgeGetAdSelectionDataMaxNumEntirePayloadCompressions",
                FLEDGE_GET_AD_SELECTION_DATA_MAX_NUM_ENTIRE_PAYLOAD_COMPRESSIONS,
                Flags::getFledgeGetAdSelectionDataMaxNumEntirePayloadCompressions);
    }

    @Test
    public void testGetFledgeGetAdSelectionDataDeserializeOnlyAdRenderIds() {
        testFlag(
                "getFledgeGetAdSelectionDataDeserializeOnlyAdRenderIds",
                FLEDGE_GET_AD_SELECTION_DATA_DESERIALIZE_ONLY_AD_RENDER_IDS,
                Flags::getFledgeGetAdSelectionDataDeserializeOnlyAdRenderIds);
    }

    @Test
    public void testGetPasEncodingJobImprovementsEnabled() {
        testFeatureFlag(
                "PAS_ENCODING_JOB_IMPROVEMENTS_ENABLED",
                Flags::getPasEncodingJobImprovementsEnabled);
    }

    @Test
    public void testGetCobaltIgnoredReportIdList() {
        testFlag(
                "getCobaltIgnoredReportIdList",
                COBALT__IGNORED_REPORT_ID_LIST,
                Flags::getCobaltIgnoredReportIdList);
    }

    @Test
    public void testGetMddPackageDenyRegistryManifestFileUrl() {
        testFlag(
                "getMddPackageDenyRegistryManifestFileUrl()",
                DEFAULT_MDD_PACKAGE_DENY_REGISTRY_MANIFEST_FILE_URL,
                Flags::getMddPackageDenyRegistryManifestFileUrl);
    }

    @Test
    public void testGetMeasurementAdrBudgetOriginXPublisherXWindow() {
        testFlag(
                "getMeasurementAdrBudgetOriginXPublisherXWindow",
                MEASUREMENT_ADR_BUDGET_PER_ORIGIN_PUBLISHER_WINDOW,
                Flags::getMeasurementAdrBudgetOriginXPublisherXWindow);
    }

    @Test
    public void testGetMeasurementAdrBudgetPublisherXWindow() {
        testFlag(
                "getMeasurementAdrBudgetPublisherXWindow",
                MEASUREMENT_ADR_BUDGET_PER_PUBLISHER_WINDOW,
                Flags::getMeasurementAdrBudgetPublisherXWindow);
    }

    @Test
    public void testGetMeasurementAdrBudgetWindowLengthMillis() {
        testFlag(
                "getMeasurementAdrBudgetWindowLengthMillis",
                MEASUREMENT_ADR_BUDGET_WINDOW_LENGTH_MILLIS,
                Flags::getMeasurementAdrBudgetWindowLengthMillis);
    }

    @Test
    public void testGetMeasurementMaxAdrCountPerSource() {
        testFlag(
                "getMeasurementMaxAdrCountPerSource",
                MEASUREMENT_MAX_ADR_COUNT_PER_SOURCE,
                Flags::getMeasurementMaxAdrCountPerSource);
    }

    @Test
    public void testGetFledgeEnableScheduleCustomAudienceUpdateAdditionalScheduleRequests() {
        testFeatureFlag(
                "FLEDGE_ENABLE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ADDITIONAL_SCHEDULE_REQUESTS",
                Flags::getFledgeEnableScheduleCustomAudienceUpdateAdditionalScheduleRequests);
    }

    @Test
    public void testGetFledgeScheduleCustomAudienceUpdateMaxBytes() {
        testFlag(
                "getFledgeScheduleCustomAudienceUpdateMaxBytes()",
                FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MAX_BYTES,
                Flags::getFledgeScheduleCustomAudienceUpdateMaxBytes);
    }

    @Test
    public void testGetFledgeForcedEncodingAfterSignalsUpdateCooldownSeconds() {
        testFlag(
                "getFledgeForcedEncodingAfterSignalsUpdateCooldownSeconds()",
                FLEDGE_FORCED_ENCODING_AFTER_SIGNALS_UPDATE_COOLDOWN_SECONDS,
                Flags::getFledgeForcedEncodingAfterSignalsUpdateCooldownSeconds);
    }

    @Test
    public void testGetFledgeJoinCustomAudienceRequestPermitsPerSecond() {
        testFloatFlag(
                "getFledgeJoinCustomAudienceRequestPermitsPerSecond()",
                FLEDGE_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND,
                Flags::getFledgeJoinCustomAudienceRequestPermitsPerSecond);
    }

    @Test
    public void testGetFledgeFetchAndJoinCustomAudienceRequestPermitsPerSecond() {
        testFloatFlag(
                "getFledgeFetchAndJoinCustomAudienceRequestPermitsPerSecond()",
                FLEDGE_FETCH_AND_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND,
                Flags::getFledgeFetchAndJoinCustomAudienceRequestPermitsPerSecond);
    }

    @Test
    public void testGetFledgeScheduleCustomAudienceUpdateRequestPermitsPerSecond() {
        testFloatFlag(
                "getFledgeScheduleCustomAudienceUpdateRequestPermitsPerSecond()",
                FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_REQUEST_PERMITS_PER_SECOND,
                Flags::getFledgeScheduleCustomAudienceUpdateRequestPermitsPerSecond);
    }

    @Test
    public void testGetFledgeLeaveCustomAudienceRequestPermitsPerSecond() {
        testFloatFlag(
                "getFledgeLeaveCustomAudienceRequestPermitsPerSecond()",
                FLEDGE_LEAVE_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND,
                Flags::getFledgeLeaveCustomAudienceRequestPermitsPerSecond);
    }

    @Test
    public void testGetFledgeUpdateSignalsRequestPermitsPerSecond() {
        testFloatFlag(
                "getFledgeUpdateSignalsRequestPermitsPerSecond()",
                FLEDGE_UPDATE_SIGNALS_REQUEST_PERMITS_PER_SECOND,
                Flags::getFledgeUpdateSignalsRequestPermitsPerSecond);
    }

    @Test
    public void testGetFledgeSelectAdsRequestPermitsPerSecond() {
        testFloatFlag(
                "getFledgeSelectAdsRequestPermitsPerSecond()",
                FLEDGE_SELECT_ADS_REQUEST_PERMITS_PER_SECOND,
                Flags::getFledgeSelectAdsRequestPermitsPerSecond);
    }

    @Test
    public void testGetFledgeSelectAdsWithOutcomesRequestPermitsPerSecond() {
        testFloatFlag(
                "getFledgeSelectAdsWithOutcomesRequestPermitsPerSecond()",
                FLEDGE_SELECT_ADS_WITH_OUTCOMES_REQUEST_PERMITS_PER_SECOND,
                Flags::getFledgeSelectAdsWithOutcomesRequestPermitsPerSecond);
    }

    @Test
    public void testGetFledgeGetAdSelectionDataRequestPermitsPerSecond() {
        testFloatFlag(
                "getFledgeGetAdSelectionDataRequestPermitsPerSecond()",
                FLEDGE_GET_AD_SELECTION_DATA_REQUEST_PERMITS_PER_SECOND,
                Flags::getFledgeGetAdSelectionDataRequestPermitsPerSecond);
    }

    @Test
    public void testGetFledgePersistAdSelectionResultRequestPermitsPerSecond() {
        testFloatFlag(
                "getFledgePersistAdSelectionResultRequestPermitsPerSecond()",
                FLEDGE_PERSIST_AD_SELECTION_RESULT_REQUEST_PERMITS_PER_SECOND,
                Flags::getFledgePersistAdSelectionResultRequestPermitsPerSecond);
    }

    @Test
    public void testGetFledgeReportImpressionRequestPermitsPerSecond() {
        testFloatFlag(
                "getFledgeReportImpressionRequestPermitsPerSecond()",
                FLEDGE_REPORT_IMPRESSION_REQUEST_PERMITS_PER_SECOND,
                Flags::getFledgeReportImpressionRequestPermitsPerSecond);
    }

    @Test
    public void testGetFledgeReportInteractionRequestPermitsPerSecond() {
        testFloatFlag(
                "getFledgeReportInteractionRequestPermitsPerSecond()",
                FLEDGE_REPORT_INTERACTION_REQUEST_PERMITS_PER_SECOND,
                Flags::getFledgeReportInteractionRequestPermitsPerSecond);
    }

    @Test
    public void testGetFledgeSetAppInstallAdvertisersRequestPermitsPerSecond() {
        testFloatFlag(
                "getFledgeSetAppInstallAdvertisersRequestPermitsPerSecond()",
                FLEDGE_SET_APP_INSTALL_ADVERTISERS_REQUEST_PERMITS_PER_SECOND,
                Flags::getFledgeSetAppInstallAdvertisersRequestPermitsPerSecond);
    }

    @Test
    public void testGetFledgeUpdateAdCounterHistogramRequestPermitsPerSecond() {
        testFloatFlag(
                "getFledgeUpdateAdCounterHistogramRequestPermitsPerSecond()",
                FLEDGE_UPDATE_AD_COUNTER_HISTOGRAM_REQUEST_PERMITS_PER_SECOND,
                Flags::getFledgeUpdateAdCounterHistogramRequestPermitsPerSecond);
    }

    @Test
    public void testGetProtectedSignalsMaxJsFailureExecutionOnCertainVersionBeforeStop() {
        testFlag(
                "getProtectedSignalsMaxJsFailureExecutionOnCertainVersionBeforeStop()",
                PROTECTED_SIGNALS_MAX_JS_FAILURE_EXECUTION_ON_CERTAIN_VERSION_BEFORE_STOP,
                Flags::getProtectedSignalsMaxJsFailureExecutionOnCertainVersionBeforeStop);
    }

    @Test
    public void testGetProtectedSignalsMaxSignalSizePerBuyerBytes() {
        testFlag(
                "getProtectedSignalsMaxSignalSizePerBuyerBytes()",
                PROTECTED_SIGNALS_MAX_SIGNAL_SIZE_PER_BUYER_BYTES,
                Flags::getProtectedSignalsMaxSignalSizePerBuyerBytes);
    }

    @Test
    public void testGetProtectedSignalsMaxSignalSizePerBuyerWithOversubsciptionBytes() {
        testFlag(
                "getProtectedSignalsMaxSignalSizePerBuyerWithOversubsciptionBytes()",
                PROTECTED_SIGNALS_MAX_SIGNAL_SIZE_PER_BUYER_WITH_OVERSUBSCIPTION_BYTES,
                Flags::getProtectedSignalsMaxSignalSizePerBuyerWithOversubsciptionBytes);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Internal helpers and tests - do not add new tests for flags following this point.          //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void testAllFlagsAreProperlyAnnotated() throws Exception {
        requireFlagAnnotationsRuntimeRetention();

        // NOTE: pass explicitly flags when developing, otherwise it would return hundreds of
        // failed fields. Example:
        //        List<Field> allFields = getAllFlagFields(
        //                "MEASUREMENT_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW",
        //                "MEASUREMENT_REPORTING_JOB_REQUIRED_NETWORK_TYPE",
        //                "MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW");
        List<Field> allFields = getAllFlagFields();
        List<String> fieldsMissingAnnotation = new ArrayList<>();

        for (Field field : allFields) {
            String name = field.getName();
            if (!hasAnnotation(field, FeatureFlag.class)
                    && !hasAnnotation(field, ConfigFlag.class)) {
                fieldsMissingAnnotation.add(name);
            }
        }
        expect.withMessage("fields missing @FeatureFlag or @ConfigFlag annotation")
                .that(fieldsMissingAnnotation)
                .isEmpty();
    }

    @Test
    public void testGetAdIdCacheTtl() {
        testFlag("getAdIdCacheTtl()", DEFAULT_ADID_CACHE_TTL_MS, Flags::getAdIdCacheTtlMs);
    }

    @Test
    public void testGetEnableAtomicFileDatastoreBatchUpdateApi() {
        testFeatureFlag(
                "DEFAULT_ENABLE_ATOMIC_FILE_DATASTORE_BATCH_UPDATE_API",
                Flags::getEnableAtomicFileDatastoreBatchUpdateApi);
    }

    @Test
    public void testGetAdIdMigrationEnabled() {
        testFeatureFlag("DEFAULT_AD_ID_MIGRATION_ENABLED", Flags::getAdIdMigrationEnabled);
    }

    @Test
    public void testGetFledgeCustomAudiencePerBuyerMaxCount() {
        testFlag(
                "getFledgeCustomAudiencePerBuyerMaxCount",
                FLEDGE_CUSTOM_AUDIENCE_PER_BUYER_MAX_COUNT,
                Flags::getFledgeCustomAudiencePerBuyerMaxCount);
    }

    @Test
    public void testGetEnforceForegroundStatusForFetchAndJoinCustomAudience() {
        testFlag(
                "getEnforceForegroundStatusForFetchAndJoinCustomAudience",
                ENFORCE_FOREGROUND_STATUS_FETCH_AND_JOIN_CUSTOM_AUDIENCE,
                Flags::getEnforceForegroundStatusForFetchAndJoinCustomAudience);
    }

    @Test
    public void testGetEnforceForegroundStatusForLeaveCustomAudience() {
        testFlag(
                "getEnforceForegroundStatusForLeaveCustomAudience",
                ENFORCE_FOREGROUND_STATUS_LEAVE_CUSTOM_AUDIENCE,
                Flags::getEnforceForegroundStatusForLeaveCustomAudience);
    }

    @Test
    public void testGetEnforceForegroundStatusForScheduleCustomAudience() {
        testFlag(
                "getEnforceForegroundStatusForScheduleCustomAudience",
                ENFORCE_FOREGROUND_STATUS_SCHEDULE_CUSTOM_AUDIENCE,
                Flags::getEnforceForegroundStatusForScheduleCustomAudience);
    }

    @Test
    public void testGetEnableCustomAudienceComponentAds() {
        testFlag(
                "getEnableCustomAudienceComponentAds",
                ENABLE_CUSTOM_AUDIENCE_COMPONENT_ADS,
                Flags::getEnableCustomAudienceComponentAds);
    }

    @Test
    public void testGetMaxComponentAdsPerCustomAudience() {
        testFlag(
                "getMaxComponentAdsPerCustomAudience",
                MAX_COMPONENT_ADS_PER_CUSTOM_AUDIENCE,
                Flags::getMaxComponentAdsPerCustomAudience);
    }

    @Test
    public void testGetComponentAdRenderIdMaxLengthBytes() {
        testFlag(
                "getComponentAdRenderIdMaxLengthBytes",
                COMPONENT_AD_RENDER_ID_MAX_LENGTH_BYTES,
                Flags::getComponentAdRenderIdMaxLengthBytes);
    }

    private boolean hasAnnotation(Field field, Class<? extends Annotation> annotationClass) {
        String name = field.getName();
        Annotation annotation = field.getAnnotation(annotationClass);
        if (annotation != null) {
            mLog.d("Found annotation on field %s : %s", name, annotation);
            return true;
        }
        return false;
    }

    private void testRampedUpKillSwitchGuardedByGlobalKillSwitch(
            String name, Flaginator<Flags, Boolean> flaginator) {
        internalHelperFortKillSwitchGuardedByGlobalKillSwitch(
                name, flaginator, /* expectedValue= */ false);
    }

    private void testNewKillSwitchGuardedByGlobalKillSwitch(
            String name, Flaginator<Flags, Boolean> flaginator) {
        internalHelperFortKillSwitchGuardedByGlobalKillSwitch(
                name, flaginator, /* expectedValue= */ true);
    }

    private void testFeatureFlagGuardedByGlobalKillSwitch(
            String name, Flaginator<Flags, Boolean> flaginator) {
        boolean defaultValue = getConstantValue(name);

        // Getter
        expect.withMessage("getter for %s when global kill_switch is on", name)
                .that(flaginator.getFlagValue(mGlobalKsOnFlags))
                .isFalse();

        expect.withMessage("getter for %s when global kill_switch is off", name)
                .that(flaginator.getFlagValue(mGlobalKsOffFlags))
                .isEqualTo(defaultValue);

        // Constant
        expect.withMessage("%s", name).that(defaultValue).isFalse();
    }

    private void testFeatureFlag(String name, Flaginator<Flags, Boolean> flaginator) {
        boolean defaultValue = getConstantValue(name);

        // Getter

        expect.withMessage("getter for %s", name)
                .that(flaginator.getFlagValue(mFlags))
                .isEqualTo(defaultValue);

        // Since the flag doesn't depend on global kill switch, it shouldn't matter if it's on or
        // off
        expect.withMessage("getter for %s when global kill_switch is on", name)
                .that(flaginator.getFlagValue(mGlobalKsOnFlags))
                .isEqualTo(defaultValue);
        expect.withMessage("getter for %s when global kill_switch is off", name)
                .that(flaginator.getFlagValue(mGlobalKsOffFlags))
                .isEqualTo(defaultValue);

        // Constant
        expect.withMessage("%s", name).that(defaultValue).isFalse();
    }

    private void testFlag(
            String getterName, long defaultValue, Flaginator<Flags, Long> flaginator) {
        expect.withMessage("%s", getterName)
                .that(flaginator.getFlagValue(mFlags))
                .isEqualTo(defaultValue);
    }

    private void testFloatFlag(
            String getterName, float defaultValue, Flaginator<Flags, Float> flaginator) {
        expect.withMessage("%s", getterName)
                .that(flaginator.getFlagValue(mFlags))
                .isEqualTo(defaultValue);
    }

    private void testFlag(
            String getterName, int defaultValue, Flaginator<Flags, Integer> flaginator) {
        expect.withMessage("%s", getterName)
                .that(flaginator.getFlagValue(mFlags))
                .isEqualTo(defaultValue);
    }

    private void testFlag(
            String getterName, boolean defaultValue, Flaginator<Flags, Boolean> flaginator) {
        expect.withMessage("%s", getterName)
                .that(flaginator.getFlagValue(mFlags))
                .isEqualTo(defaultValue);
    }

    private void testFlag(
            String getterName, String defaultValue, Flaginator<Flags, String> flaginator) {
        expect.withMessage("%s", getterName)
                .that(flaginator.getFlagValue(mFlags))
                .isEqualTo(defaultValue);
    }

    /**
     * @deprecated TODO(b / 324077542) - remove once all kill-switches have been converted
     */
    @Deprecated
    private void testKillSwitchBeingConvertedAndGuardedByGlobalKillSwitch(
            String name, Flaginator<Flags, Boolean> flaginator) {
        internalHelperFortKillSwitchGuardedByGlobalKillSwitch(
                name, flaginator, /* expectedValue= */ false);
    }

    private void testFeatureFlagBasedOnLegacyKillSwitchAndGuardedByGlobalKillSwitch(
            String getterName,
            boolean defaultKillSwitchValue,
            Flaginator<Flags, Boolean> flaginator) {
        expect.withMessage("%s when global kill_switch is on", getterName)
                .that(flaginator.getFlagValue(mGlobalKsOnFlags))
                .isFalse();
        expect.withMessage("%s when global kill_switch is off", getterName)
                .that(flaginator.getFlagValue(mGlobalKsOffFlags))
                .isEqualTo(!defaultKillSwitchValue);
    }

    private void testMsmtFeatureFlagBackedByLegacyKillSwitchAndGuardedByMsmtEnabled(
            String getterName, String killSwitchName, Flaginator<Flags, Boolean> flaginator) {
        boolean defaultKillSwitchValue = getConstantValue(killSwitchName);
        boolean defaultValue = !defaultKillSwitchValue;

        // Getter
        expect.withMessage("%s when msmt_enabled is true", getterName)
                .that(flaginator.getFlagValue(mMsmtEnabledFlags))
                .isEqualTo(defaultValue);
        expect.withMessage("getter for %s when msmt enabled is false", getterName)
                .that(flaginator.getFlagValue(mMsmtDisabledFlags))
                .isFalse();

        // Constant
        expect.withMessage("%s", killSwitchName).that(defaultKillSwitchValue).isFalse();
    }

    @SuppressWarnings({"unused"}) // Might become unused if no flag currently needs it
    private void testRetiredFeatureFlag(String name, Flaginator<Flags, Boolean> flaginator) {
        boolean defaultValue = getConstantValue(name);

        // Getter
        expect.withMessage("getter for %s", name)
                .that(flaginator.getFlagValue(mFlags))
                .isEqualTo(defaultValue);

        // Constant
        expect.withMessage("%s", name).that(defaultValue).isTrue();
    }

    static <T> T getConstantValue(String name) {
        return getConstantValue(Flags.class, name);
    }

    // Not passing type (and using type.cast(value)) because most of the flags are primitive types
    // (like boolean) and T would be their object equivalent (like Boolean)
    @SuppressWarnings("TypeParameterUnusedInFormals")
    static <T> T getConstantValue(Class<?> clazz, String name) {
        Field field;
        try {
            field = clazz.getDeclaredField(name);
        } catch (NoSuchFieldException | SecurityException e) {
            throw new IllegalArgumentException("Could not get field " + name + ": " + e);
        }

        int modifiers = field.getModifiers();
        Preconditions.checkArgument(
                Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers),
                "field %s is not static final",
                name);

        Object value;
        try {
            value = field.get(null);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new IllegalArgumentException("Could not get value of field " + name + ": " + e);
        }

        Log.v(TAG, "getConstant(): " + name + "=" + value);

        @SuppressWarnings("unchecked")
        T castValue = (T) value;

        return castValue;
    }

    // Should not be called directly
    private void internalHelperFortKillSwitchGuardedByGlobalKillSwitch(
            String name, Flaginator<Flags, Boolean> flaginator, boolean expectedValue) {
        boolean defaultValue = getConstantValue(name);

        // Getter
        expect.withMessage("getter for %s when global kill_switch is on", name)
                .that(flaginator.getFlagValue(mGlobalKsOnFlags))
                .isTrue();

        expect.withMessage("getter for %s when global kill_switch is off", name)
                .that(flaginator.getFlagValue(mGlobalKsOffFlags))
                .isEqualTo(defaultValue);

        // Constant
        expect.withMessage("%s", name).that(defaultValue).isEqualTo(expectedValue);
    }

    static class GlobalKillSwitchAwareFlags implements Flags {
        private final boolean mGlobalKsOnFlags;

        GlobalKillSwitchAwareFlags(boolean globalKsEnabled) {
            mGlobalKsOnFlags = globalKsEnabled;
        }

        @Override
        public boolean getGlobalKillSwitch() {
            Log.d(TAG, this + ".getGlobalKillSwitch(): returning " + mGlobalKsOnFlags);
            return mGlobalKsOnFlags;
        }

        @Override
        public String toString() {
            StringBuilder string = new StringBuilder(getClass().getSimpleName()).append('[');
            decorateToString(string);
            return string.append(']').toString();
        }

        protected void decorateToString(StringBuilder toString) {
            toString.append("globalKsEnabled=").append(mGlobalKsOnFlags);
        }
    }

    private static final class MsmtFeatureAwareFlags extends GlobalKillSwitchAwareFlags {
        private final boolean mMsmtEnabled;

        MsmtFeatureAwareFlags(boolean msmtEnabled) {
            super(false);
            mMsmtEnabled = msmtEnabled;
        }

        @Override
        public boolean getMeasurementEnabled() {
            Log.d(TAG, this + ".getMeasurementEnabled(): returning " + mMsmtEnabled);
            return mMsmtEnabled;
        }

        @Override
        protected void decorateToString(StringBuilder toString) {
            super.decorateToString(toString);
            toString.append(", msmtEnabled=").append(mMsmtEnabled);
        }
    }

    /**
     * @deprecated - TODO(b/325074749): remove once all methods are changed to use
     *     !getMeasurementEnabled()
     */
    @Deprecated
    private static final class MsmtKillSwitchAwareFlags extends GlobalKillSwitchAwareFlags {
        private final boolean mMsmtKsEnabled;

        MsmtKillSwitchAwareFlags(boolean msmtKsEnabled) {
            super(false);
            mMsmtKsEnabled = msmtKsEnabled;
        }

        @Override
        public boolean getLegacyMeasurementKillSwitch() {
            Log.d(TAG, this + ".getLegacyMeasurementKillSwitch(): returning " + mMsmtKsEnabled);
            return mMsmtKsEnabled;
        }

        @Override
        protected void decorateToString(StringBuilder toString) {
            super.decorateToString(toString);
            toString.append(", msmtKsEnabled=").append(mMsmtKsEnabled);
        }
    }

    /**
     * Gets all fields defining flags.
     *
     * @param flagNames if set, only return fields with those names
     */
    private List<Field> getAllFlagFields(String... flagNames) throws IllegalAccessException {
        List<String> filter =
                flagNames == null || flagNames.length == 0 ? null : Arrays.asList(flagNames);
        List<Field> fields = new ArrayList<>();
        for (Field field : Flags.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
                String name = field.getName();
                if (filter != null && !filter.contains(name)) {
                    mLog.v("Skipping %s because it matches filter (%s)", name, filter);
                    continue;
                }
                fields.add(field);
            }
        }
        return fields;
    }

    private static void requireFlagAnnotationsRuntimeRetention() throws Exception {
        Field field = FlagsTest.class.getField("fieldUsedToDetermineAnnotationRetention");
        boolean hasFeatureFlag = field.getAnnotation(FeatureFlag.class) != null;
        boolean hasConfigFlag = field.getAnnotation(ConfigFlag.class) != null;
        if (!(hasFeatureFlag && hasConfigFlag)) {
            throw new AssumptionViolatedException(
                    "Both @FeatureFlag and @ConfigFlag must be set with RUNTIME Retention, but"
                            + " @FeatureFlag="
                            + hasFeatureFlag
                            + " and @ConfigFlag="
                            + hasConfigFlag);
        }
    }

    // Used by requireFlagAnnotationsRuntimeRetention
    @FeatureFlag @ConfigFlag
    public final Object fieldUsedToDetermineAnnotationRetention = new Object();
}
