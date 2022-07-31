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

import static com.android.adservices.service.Flags.DEFAULT_CLASSIFIER_TYPE;
import static com.android.adservices.service.Flags.FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS;
import static com.android.adservices.service.Flags.FLEDGE_AD_SELECTION_CONCURRENT_BIDDING_COUNT;
import static com.android.adservices.service.Flags.FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.service.Flags.FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS;
import static com.android.adservices.service.Flags.FLEDGE_BACKGROUND_FETCH_ELIGIBLE_UPDATE_BASE_INTERVAL_S;
import static com.android.adservices.service.Flags.FLEDGE_BACKGROUND_FETCH_ENABLED;
import static com.android.adservices.service.Flags.FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS;
import static com.android.adservices.service.Flags.FLEDGE_BACKGROUND_FETCH_JOB_MAX_RUNTIME_MS;
import static com.android.adservices.service.Flags.FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS;
import static com.android.adservices.service.Flags.FLEDGE_BACKGROUND_FETCH_MAX_NUM_UPDATED;
import static com.android.adservices.service.Flags.FLEDGE_BACKGROUND_FETCH_MAX_RESPONSE_SIZE_B;
import static com.android.adservices.service.Flags.FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS;
import static com.android.adservices.service.Flags.FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS;
import static com.android.adservices.service.Flags.FLEDGE_BACKGROUND_FETCH_THREAD_POOL_SIZE;
import static com.android.adservices.service.Flags.FLEDGE_CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN_MS;
import static com.android.adservices.service.Flags.FLEDGE_CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN_MS;
import static com.android.adservices.service.Flags.FLEDGE_CUSTOM_AUDIENCE_MAX_ADS_SIZE_B;
import static com.android.adservices.service.Flags.FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT;
import static com.android.adservices.service.Flags.FLEDGE_CUSTOM_AUDIENCE_MAX_EXPIRE_IN_MS;
import static com.android.adservices.service.Flags.FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS;
import static com.android.adservices.service.Flags.FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT;
import static com.android.adservices.service.Flags.FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B;
import static com.android.adservices.service.Flags.FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B;
import static com.android.adservices.service.Flags.FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT;
import static com.android.adservices.service.Flags.GLOBAL_KILL_SWITCH;
import static com.android.adservices.service.Flags.MAINTENANCE_JOB_FLEX_MS;
import static com.android.adservices.service.Flags.MAINTENANCE_JOB_PERIOD_MS;
import static com.android.adservices.service.Flags.MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL;
import static com.android.adservices.service.Flags.MEASUREMENT_AGGREGATE_ENCRYPTION_KEY_COORDINATOR_URL;
import static com.android.adservices.service.Flags.MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS;
import static com.android.adservices.service.Flags.MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS;
import static com.android.adservices.service.Flags.MEASUREMENT_APP_NAME;
import static com.android.adservices.service.Flags.MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS;
import static com.android.adservices.service.Flags.MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS;
import static com.android.adservices.service.Flags.MEASUREMENT_MANIFEST_FILE_URL;
import static com.android.adservices.service.Flags.NUMBER_OF_EPOCHS_TO_KEEP_IN_HISTORY;
import static com.android.adservices.service.Flags.PPAPI_APP_ALLOW_LIST;
import static com.android.adservices.service.Flags.PRECOMPUTED_CLASSIFIER;
import static com.android.adservices.service.Flags.SDK_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.Flags.TOPICS_EPOCH_JOB_FLEX_MS;
import static com.android.adservices.service.Flags.TOPICS_EPOCH_JOB_PERIOD_MS;
import static com.android.adservices.service.Flags.TOPICS_KILL_SWITCH;
import static com.android.adservices.service.Flags.TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS;
import static com.android.adservices.service.Flags.TOPICS_NUMBER_OF_RANDOM_TOPICS;
import static com.android.adservices.service.Flags.TOPICS_NUMBER_OF_TOP_TOPICS;
import static com.android.adservices.service.Flags.TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC;
import static com.android.adservices.service.PhFlags.KEY_CLASSIFIER_TYPE;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_AD_SELECTION_CONCURRENT_BIDDING_COUNT;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_BACKGROUND_FETCH_ELIGIBLE_UPDATE_BASE_INTERVAL_S;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_BACKGROUND_FETCH_ENABLED;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_BACKGROUND_FETCH_JOB_MAX_RUNTIME_MS;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_BACKGROUND_FETCH_MAX_NUM_UPDATED;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_BACKGROUND_FETCH_MAX_RESPONSE_SIZE_B;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_BACKGROUND_FETCH_THREAD_POOL_SIZE;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN_MS;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN_MS;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ADS_SIZE_B;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_EXPIRE_IN_MS;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT;
import static com.android.adservices.service.PhFlags.KEY_GLOBAL_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MAINTENANCE_JOB_FLEX_MS;
import static com.android.adservices.service.PhFlags.KEY_MAINTENANCE_JOB_PERIOD_MS;
import static com.android.adservices.service.PhFlags.KEY_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_AGGREGATE_ENCRYPTION_KEY_COORDINATOR_URL;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_APP_NAME;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_MANIFEST_FILE_URL;
import static com.android.adservices.service.PhFlags.KEY_NUMBER_OF_EPOCHS_TO_KEEP_IN_HISTORY;
import static com.android.adservices.service.PhFlags.KEY_PPAPI_APP_ALLOW_LIST;
import static com.android.adservices.service.PhFlags.KEY_SDK_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.PhFlags.KEY_TOPICS_EPOCH_JOB_FLEX_MS;
import static com.android.adservices.service.PhFlags.KEY_TOPICS_EPOCH_JOB_PERIOD_MS;
import static com.android.adservices.service.PhFlags.KEY_TOPICS_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS;
import static com.android.adservices.service.PhFlags.KEY_TOPICS_NUMBER_OF_RANDOM_TOPICS;
import static com.android.adservices.service.PhFlags.KEY_TOPICS_NUMBER_OF_TOP_TOPICS;
import static com.android.adservices.service.PhFlags.KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC;

import static com.google.common.truth.Truth.assertThat;

import android.provider.DeviceConfig;

import androidx.test.filters.SmallTest;

import com.android.adservices.service.Flags.ClassifierType;
import com.android.modules.utils.testing.TestableDeviceConfig;

import org.junit.Rule;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

/** Unit tests for {@link com.android.adservices.service.PhFlags} */
@SmallTest
public class PhFlagsTest {
    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule mDeviceConfigRule =
            new TestableDeviceConfig.TestableDeviceConfigRule();

    @Test
    public void testGetTopicsEpochJobPeriodMs() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getTopicsEpochJobPeriodMs())
                .isEqualTo(TOPICS_EPOCH_JOB_PERIOD_MS);

        // Now overriding with the value from PH.
        final long phOverridingValue = 1;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_TOPICS_EPOCH_JOB_PERIOD_MS,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getTopicsEpochJobPeriodMs()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetTopicsEpochJobFlexMs() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getTopicsEpochJobFlexMs())
                .isEqualTo(TOPICS_EPOCH_JOB_FLEX_MS);

        // Now overriding with the value from PH.
        final long phOverridingValue = 2;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_TOPICS_EPOCH_JOB_FLEX_MS,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getTopicsEpochJobFlexMs()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetTopicsPercentageForRandomTopic() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getTopicsPercentageForRandomTopic())
                .isEqualTo(TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC);

        final long phOverridingValue = 3;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getTopicsPercentageForRandomTopic()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetTopicsNumberOfRandomTopics() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getTopicsNumberOfRandomTopics())
                .isEqualTo(TOPICS_NUMBER_OF_RANDOM_TOPICS);

        final long phOverridingValue = 4;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_TOPICS_NUMBER_OF_RANDOM_TOPICS,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getTopicsNumberOfRandomTopics()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetTopicsNumberOfTopTopics() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getTopicsNumberOfTopTopics())
                .isEqualTo(TOPICS_NUMBER_OF_TOP_TOPICS);

        final long phOverridingValue = 5;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_TOPICS_NUMBER_OF_TOP_TOPICS,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getTopicsNumberOfTopTopics()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetTopicsNumberOfLookBackEpochs() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getTopicsNumberOfLookBackEpochs())
                .isEqualTo(TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS);

        final long phOverridingValue = 6;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getTopicsNumberOfLookBackEpochs()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testClassifierType() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getClassifierType()).isEqualTo(DEFAULT_CLASSIFIER_TYPE);

        @ClassifierType int phOverridingValue = PRECOMPUTED_CLASSIFIER;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_CLASSIFIER_TYPE,
                Integer.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getClassifierType()).isEqualTo(PRECOMPUTED_CLASSIFIER);
    }

    @Test
    public void testGetMaintenanceJobPeriodMs() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getMaintenanceJobPeriodMs())
                .isEqualTo(MAINTENANCE_JOB_PERIOD_MS);

        final long phOverridingValue = 7;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MAINTENANCE_JOB_PERIOD_MS,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMaintenanceJobPeriodMs()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetMaintenanceJobFlexMs() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getMaintenanceJobFlexMs())
                .isEqualTo(MAINTENANCE_JOB_FLEX_MS);

        final long phOverridingValue = 8;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MAINTENANCE_JOB_FLEX_MS,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMaintenanceJobFlexMs()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetMddTopicsClassifierManifestFileUrl() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getMddTopicsClassifierManifestFileUrl())
                .isEqualTo(MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL);

        final String phOverridingValue = "testFileUrl";
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL,
                phOverridingValue,
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMddTopicsClassifierManifestFileUrl()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetAdSelectionConcurrentBiddingCount() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getAdSelectionConcurrentBiddingCount())
                .isEqualTo(FLEDGE_AD_SELECTION_CONCURRENT_BIDDING_COUNT);

        final int phOverrideValue = 4;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_AD_SELECTION_CONCURRENT_BIDDING_COUNT,
                Integer.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getAdSelectionConcurrentBiddingCount()).isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetAdSelectionBiddingTimeoutPerCaMs() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getAdSelectionBiddingTimeoutPerCaMs())
                .isEqualTo(FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS);

        final int phOverrideValue = 4;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS,
                Integer.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getAdSelectionBiddingTimeoutPerCaMs()).isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetAdSelectionScoringTimeoutMs() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getAdSelectionScoringTimeoutMs())
                .isEqualTo(FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS);

        final int phOverrideValue = 4;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS,
                Integer.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getAdSelectionScoringTimeoutMs()).isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetAdSelectionOverallTimeoutMs() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getAdSelectionOverallTimeoutMs())
                .isEqualTo(FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS);

        final int phOverrideValue = 4;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS,
                Integer.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getAdSelectionOverallTimeoutMs()).isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementEventMainReportingJobPeriodMs() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getMeasurementEventMainReportingJobPeriodMs())
                .isEqualTo(MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS);

        final long phOverridingValue = 8;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementEventMainReportingJobPeriodMs())
                .isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetMeasurementEventFallbackReportingJobPeriodMs() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getMeasurementEventFallbackReportingJobPeriodMs())
                .isEqualTo(MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS);

        final long phOverridingValue = 8;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementEventFallbackReportingJobPeriodMs())
                .isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetMeasurementAggregateEncryptionKeyCoordinatorUrl() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getMeasurementAggregateEncryptionKeyCoordinatorUrl())
                .isEqualTo(MEASUREMENT_AGGREGATE_ENCRYPTION_KEY_COORDINATOR_URL);

        final String phOverridingValue = "testCoordinatorUrl";

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_AGGREGATE_ENCRYPTION_KEY_COORDINATOR_URL,
                phOverridingValue,
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementAggregateEncryptionKeyCoordinatorUrl())
                .isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetMeasurementAggregateMainReportingJobPeriodMs() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getMeasurementAggregateMainReportingJobPeriodMs())
                .isEqualTo(MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS);

        final long phOverridingValue = 8;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementAggregateMainReportingJobPeriodMs())
                .isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetMeasurementAggregateFallbackReportingJobPeriodMs() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getMeasurementAggregateFallbackReportingJobPeriodMs())
                .isEqualTo(MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS);

        final long phOverridingValue = 8;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementAggregateFallbackReportingJobPeriodMs())
                .isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetMeasurementAppName() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getMeasurementAppName()).isEqualTo(MEASUREMENT_APP_NAME);

        final String phOverridingValue = "testAppName";

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_APP_NAME,
                phOverridingValue,
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementAppName()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetMeasurementManifestFileUrl() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getMeasurementManifestFileUrl())
                .isEqualTo(MEASUREMENT_MANIFEST_FILE_URL);

        final String phOverridingValue = "testFileUrl";
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_MANIFEST_FILE_URL,
                phOverridingValue,
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementManifestFileUrl()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetFledgeCustomAudienceMaxCount() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getFledgeCustomAudienceMaxCount())
                .isEqualTo(FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT);

        final long phOverridingValue = 100L;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeCustomAudienceMaxCount()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetFledgeCustomAudiencePerAppMaxCount() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getFledgeCustomAudiencePerAppMaxCount())
                .isEqualTo(FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT);

        final long phOverridingValue = 100L;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeCustomAudiencePerAppMaxCount()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetFledgeCustomAudienceMaxOwnerCount() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getFledgeCustomAudienceMaxOwnerCount())
                .isEqualTo(FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT);

        final long phOverridingValue = 100L;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeCustomAudienceMaxOwnerCount()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetFledgeCustomAudienceDefaultExpireInMs() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getFledgeCustomAudienceDefaultExpireInMs())
                .isEqualTo(FLEDGE_CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN_MS);

        final long phOverridingValue = 100L;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN_MS,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeCustomAudienceDefaultExpireInMs()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetFledgeCustomAudienceMaxActivationDelayInMs() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getFledgeCustomAudienceMaxActivationDelayInMs())
                .isEqualTo(FLEDGE_CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN_MS);

        final long phOverridingValue = 100L;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN_MS,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeCustomAudienceMaxActivationDelayInMs())
                .isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetFledgeCustomAudienceMaxExpireInMs() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getFledgeCustomAudienceMaxExpireInMs())
                .isEqualTo(FLEDGE_CUSTOM_AUDIENCE_MAX_EXPIRE_IN_MS);

        final long phOverridingValue = 100L;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_EXPIRE_IN_MS,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeCustomAudienceMaxExpireInMs()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetFledgeCustomAudienceMaxUserBiddingSignalsSizeB() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getFledgeCustomAudienceMaxUserBiddingSignalsSizeB())
                .isEqualTo(FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B);

        final int phOverridingValue = 234;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B,
                Integer.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB())
                .isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetFledgeCustomAudienceMaxTrustedBiddingDataSizeB() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getFledgeCustomAudienceMaxTrustedBiddingDataSizeB())
                .isEqualTo(FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B);

        final int phOverridingValue = 123;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B,
                Integer.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB())
                .isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetFledgeCustomAudienceMaxAdsSizeB() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getFledgeCustomAudienceMaxAdsSizeB())
                .isEqualTo(FLEDGE_CUSTOM_AUDIENCE_MAX_ADS_SIZE_B);

        final int phOverridingValue = 345;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ADS_SIZE_B,
                Integer.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeCustomAudienceMaxAdsSizeB()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetFledgeCustomAudienceMaxNumAds() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getFledgeCustomAudienceMaxNumAds())
                .isEqualTo(FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS);

        final int phOverridingValue = 876;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS,
                Integer.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeCustomAudienceMaxNumAds()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetFledgeBackgroundFetchJobPeriodMs() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getFledgeBackgroundFetchJobPeriodMs())
                .isEqualTo(FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS);

        final long phOverridingValue = 100L;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeBackgroundFetchJobPeriodMs()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetFledgeBackgroundFetchEnabled() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getFledgeBackgroundFetchEnabled())
                .isEqualTo(FLEDGE_BACKGROUND_FETCH_ENABLED);

        final boolean phOverridingValue = false;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_BACKGROUND_FETCH_ENABLED,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeBackgroundFetchEnabled()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetFledgeBackgroundFetchJobFlexMs() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getFledgeBackgroundFetchJobFlexMs())
                .isEqualTo(FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS);

        final long phOverridingValue = 20L;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeBackgroundFetchJobFlexMs()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetFledgeBackgroundFetchJobMaxRuntimeMs() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getFledgeBackgroundFetchJobMaxRuntimeMs())
                .isEqualTo(FLEDGE_BACKGROUND_FETCH_JOB_MAX_RUNTIME_MS);

        final long phOverridingValue = 200L;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_BACKGROUND_FETCH_JOB_MAX_RUNTIME_MS,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeBackgroundFetchJobMaxRuntimeMs()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetFledgeBackgroundFetchMaxNumUpdated() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getFledgeBackgroundFetchMaxNumUpdated())
                .isEqualTo(FLEDGE_BACKGROUND_FETCH_MAX_NUM_UPDATED);

        final long phOverridingValue = 25L;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_BACKGROUND_FETCH_MAX_NUM_UPDATED,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeBackgroundFetchMaxNumUpdated()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetFledgeBackgroundFetchThreadPoolSize() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getFledgeBackgroundFetchThreadPoolSize())
                .isEqualTo(FLEDGE_BACKGROUND_FETCH_THREAD_POOL_SIZE);

        final int phOverridingValue = 3;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_BACKGROUND_FETCH_THREAD_POOL_SIZE,
                Integer.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeBackgroundFetchThreadPoolSize()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetFledgeBackgroundFetchEligibleUpdateBaseIntervalS() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getFledgeBackgroundFetchEligibleUpdateBaseIntervalS())
                .isEqualTo(FLEDGE_BACKGROUND_FETCH_ELIGIBLE_UPDATE_BASE_INTERVAL_S);

        final long phOverridingValue = 54321L;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_BACKGROUND_FETCH_ELIGIBLE_UPDATE_BASE_INTERVAL_S,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeBackgroundFetchEligibleUpdateBaseIntervalS())
                .isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetFledgeBackgroundFetchNetworkConnectTimeoutMs() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getFledgeBackgroundFetchNetworkConnectTimeoutMs())
                .isEqualTo(FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS);

        final int phOverridingValue = 99;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS,
                Integer.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeBackgroundFetchNetworkConnectTimeoutMs())
                .isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetFledgeBackgroundFetchNetworkReadTimeoutMs() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getFledgeBackgroundFetchNetworkReadTimeoutMs())
                .isEqualTo(FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS);

        final int phOverridingValue = 1111;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS,
                Integer.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeBackgroundFetchNetworkReadTimeoutMs())
                .isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetFledgeBackgroundFetchMaxResponseSizeB() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getFledgeBackgroundFetchMaxResponseSizeB())
                .isEqualTo(FLEDGE_BACKGROUND_FETCH_MAX_RESPONSE_SIZE_B);

        final int phOverridingValue = 9999;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_BACKGROUND_FETCH_MAX_RESPONSE_SIZE_B,
                Integer.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeBackgroundFetchMaxResponseSizeB()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetGlobalKillSwitch() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getGlobalKillSwitch()).isEqualTo(GLOBAL_KILL_SWITCH);

        // Now overriding with the value from PH.
        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getGlobalKillSwitch()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetTopicsKillSwitch() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getTopicsKillSwitch()).isEqualTo(TOPICS_KILL_SWITCH);

        // Now overriding with the value from PH.
        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_TOPICS_KILL_SWITCH,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getTopicsKillSwitch()).isEqualTo(phOverridingValue);
    }

    @Test
    public void test_globalKillswitchOverrides_getTopicsKillSwitch() {
        // Without any overriding, Topics Killswitch is off.
        assertThat(FlagsFactory.getFlags().getTopicsKillSwitch()).isEqualTo(TOPICS_KILL_SWITCH);

        // Without any overriding, Global Killswitch is off.
        assertThat(FlagsFactory.getFlags().getGlobalKillSwitch()).isEqualTo(GLOBAL_KILL_SWITCH);

        // Now overriding with the value from PH.
        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        // Now Global Killswitch is on.
        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getGlobalKillSwitch()).isEqualTo(phOverridingValue);

        // Global Killswitch is on and overrides the getTopicsKillswitch.
        assertThat(FlagsFactory.getFlags().getTopicsKillSwitch()).isEqualTo(true);
    }

    @Test
    public void testGetPpapiAppAllowList() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getPpapiAppAllowList()).isEqualTo(PPAPI_APP_ALLOW_LIST);

        // Now overriding with the value from PH.
        final String phOverridingValue = "SomePackageName,AnotherPackageName";
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_PPAPI_APP_ALLOW_LIST,
                phOverridingValue,
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getPpapiAppAllowList()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetSdkRequestPermitsPerSecond() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getSdkRequestPermitsPerSecond())
                .isEqualTo(SDK_REQUEST_PERMITS_PER_SECOND);

        final float phOverridingValue = 6;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_SDK_REQUEST_PERMITS_PER_SECOND,
                Float.toString(phOverridingValue),
                /* makeDefault */ false);

        // Now verify that the PhFlag value was overridden.
        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getSdkRequestPermitsPerSecond()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetNumberOfEpochsToKeepInHistory() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getNumberOfEpochsToKeepInHistory())
                .isEqualTo(NUMBER_OF_EPOCHS_TO_KEEP_IN_HISTORY);

        final long phOverridingValue = 6;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_NUMBER_OF_EPOCHS_TO_KEEP_IN_HISTORY,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getNumberOfEpochsToKeepInHistory()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testDump() throws FileNotFoundException {
        // Trigger the dump to verify no crash
        PrintWriter printWriter = new PrintWriter(new Writer() {
            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {

            }

            @Override
            public void flush() throws IOException {

            }

            @Override
            public void close() throws IOException {

            }
        });
        String[] args = new String[] {};
        Flags phFlags = FlagsFactory.getFlags();
        phFlags.dump(printWriter, args);
    }
}
