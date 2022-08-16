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

import static com.android.adservices.service.Flags.ADID_KILL_SWITCH;
import static com.android.adservices.service.Flags.APPSETID_KILL_SWITCH;
import static com.android.adservices.service.Flags.CLASSIFIER_NUMBER_OF_TOP_LABELS;
import static com.android.adservices.service.Flags.DEFAULT_CLASSIFIER_TYPE;
import static com.android.adservices.service.Flags.DISABLE_TOPICS_ENROLLMENT_CHECK;
import static com.android.adservices.service.Flags.DOWNLOADER_CONNECTION_TIMEOUT_MS;
import static com.android.adservices.service.Flags.DOWNLOADER_MAX_DOWNLOAD_THREADS;
import static com.android.adservices.service.Flags.DOWNLOADER_READ_TIMEOUT_MS;
import static com.android.adservices.service.Flags.ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.Flags.ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDES;
import static com.android.adservices.service.Flags.ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION;
import static com.android.adservices.service.Flags.ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION;
import static com.android.adservices.service.Flags.ENFORCE_FOREGROUND_STATUS_TOPICS;
import static com.android.adservices.service.Flags.ENFORCE_ISOLATE_MAX_HEAP_SIZE;
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
import static com.android.adservices.service.Flags.FLEDGE_CUSTOM_AUDIENCE_MAX_BIDDING_LOGIC_URI_SIZE_B;
import static com.android.adservices.service.Flags.FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT;
import static com.android.adservices.service.Flags.FLEDGE_CUSTOM_AUDIENCE_MAX_DAILY_UPDATE_URI_SIZE_B;
import static com.android.adservices.service.Flags.FLEDGE_CUSTOM_AUDIENCE_MAX_EXPIRE_IN_MS;
import static com.android.adservices.service.Flags.FLEDGE_CUSTOM_AUDIENCE_MAX_NAME_SIZE_B;
import static com.android.adservices.service.Flags.FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS;
import static com.android.adservices.service.Flags.FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT;
import static com.android.adservices.service.Flags.FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B;
import static com.android.adservices.service.Flags.FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B;
import static com.android.adservices.service.Flags.FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT;
import static com.android.adservices.service.Flags.FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH;
import static com.android.adservices.service.Flags.FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.service.Flags.FLEDGE_SELECT_ADS_KILL_SWITCH;
import static com.android.adservices.service.Flags.FOREGROUND_STATUS_LEVEL;
import static com.android.adservices.service.Flags.GLOBAL_KILL_SWITCH;
import static com.android.adservices.service.Flags.ISOLATE_MAX_HEAP_SIZE_BYTES;
import static com.android.adservices.service.Flags.MAINTENANCE_JOB_FLEX_MS;
import static com.android.adservices.service.Flags.MAINTENANCE_JOB_PERIOD_MS;
import static com.android.adservices.service.Flags.MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL;
import static com.android.adservices.service.Flags.MEASUREMENT_AGGREGATE_ENCRYPTION_KEY_COORDINATOR_URL;
import static com.android.adservices.service.Flags.MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS;
import static com.android.adservices.service.Flags.MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS;
import static com.android.adservices.service.Flags.MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_API_STATUS_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_APP_NAME;
import static com.android.adservices.service.Flags.MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS;
import static com.android.adservices.service.Flags.MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS;
import static com.android.adservices.service.Flags.MEASUREMENT_IS_CLICK_VERIFICATION_ENABLED;
import static com.android.adservices.service.Flags.MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_MANIFEST_FILE_URL;
import static com.android.adservices.service.Flags.MEASUREMENT_NETWORK_CONNECT_TIMEOUT_MS;
import static com.android.adservices.service.Flags.MEASUREMENT_NETWORK_READ_TIMEOUT_MS;
import static com.android.adservices.service.Flags.MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_REGISTRATION_INPUT_EVENT_VALID_WINDOW_MS;
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
import static com.android.adservices.service.PhFlags.KEY_ADID_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_APPSETID_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_CLASSIFIER_NUMBER_OF_TOP_LABELS;
import static com.android.adservices.service.PhFlags.KEY_CLASSIFIER_TYPE;
import static com.android.adservices.service.PhFlags.KEY_DISABLE_TOPICS_ENROLLMENT_CHECK;
import static com.android.adservices.service.PhFlags.KEY_DOWNLOADER_CONNECTION_TIMEOUT_MS;
import static com.android.adservices.service.PhFlags.KEY_DOWNLOADER_MAX_DOWNLOAD_THREADS;
import static com.android.adservices.service.PhFlags.KEY_DOWNLOADER_READ_TIMEOUT_MS;
import static com.android.adservices.service.PhFlags.KEY_ENFORCE_FOREGROUND_STATUS_TOPICS;
import static com.android.adservices.service.PhFlags.KEY_ENFORCE_ISOLATE_MAX_HEAP_SIZE;
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
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_BIDDING_LOGIC_URI_SIZE_B;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_DAILY_UPDATE_URI_SIZE_B;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_EXPIRE_IN_MS;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NAME_SIZE_B;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_SELECT_ADS_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_FOREGROUND_STATUS_LEVEL;
import static com.android.adservices.service.PhFlags.KEY_GLOBAL_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_ISOLATE_MAX_HEAP_SIZE_BYTES;
import static com.android.adservices.service.PhFlags.KEY_MAINTENANCE_JOB_FLEX_MS;
import static com.android.adservices.service.PhFlags.KEY_MAINTENANCE_JOB_PERIOD_MS;
import static com.android.adservices.service.PhFlags.KEY_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_AGGREGATE_ENCRYPTION_KEY_COORDINATOR_URL;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_API_STATUS_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_APP_NAME;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_IS_CLICK_VERIFICATION_ENABLED;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_MANIFEST_FILE_URL;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_NETWORK_CONNECT_TIMEOUT_MS;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_NETWORK_READ_TIMEOUT_MS;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_REGISTRATION_INPUT_EVENT_VALID_WINDOW_MS;
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
        assertThat(phFlags.getClassifierType()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetClassifierNumberOfTopLabels() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getClassifierNumberOfTopLabels())
                .isEqualTo(CLASSIFIER_NUMBER_OF_TOP_LABELS);

        int phOverridingValue = 3;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_CLASSIFIER_NUMBER_OF_TOP_LABELS,
                Integer.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getClassifierType()).isEqualTo(phOverridingValue);
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
    public void testGetDownloaderConnectionTimeoutMs() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getDownloaderConnectionTimeoutMs())
                .isEqualTo(DOWNLOADER_CONNECTION_TIMEOUT_MS);

        final int phOverrideValue = 923;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_DOWNLOADER_CONNECTION_TIMEOUT_MS,
                Integer.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getDownloaderConnectionTimeoutMs()).isEqualTo(phOverrideValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getDownloaderConnectionTimeoutMs())
                .isEqualTo(DOWNLOADER_CONNECTION_TIMEOUT_MS);
    }

    @Test
    public void testGetDownloaderReadTimeoutMs() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getDownloaderReadTimeoutMs())
                .isEqualTo(DOWNLOADER_READ_TIMEOUT_MS);

        final int phOverrideValue = 349;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_DOWNLOADER_READ_TIMEOUT_MS,
                Integer.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getDownloaderReadTimeoutMs()).isEqualTo(phOverrideValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getDownloaderReadTimeoutMs()).isEqualTo(DOWNLOADER_READ_TIMEOUT_MS);
    }

    @Test
    public void testGetDownloaderMaxDownloadThreads() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getDownloaderMaxDownloadThreads())
                .isEqualTo(DOWNLOADER_MAX_DOWNLOAD_THREADS);

        final int phOverrideValue = 5;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_DOWNLOADER_MAX_DOWNLOAD_THREADS,
                Integer.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getDownloaderMaxDownloadThreads()).isEqualTo(phOverrideValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getDownloaderMaxDownloadThreads())
                .isEqualTo(DOWNLOADER_MAX_DOWNLOAD_THREADS);
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

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getMeasurementEventMainReportingJobPeriodMs())
                .isEqualTo(MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS);
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

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getMeasurementEventFallbackReportingJobPeriodMs())
                .isEqualTo(MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS);
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

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getMeasurementAggregateEncryptionKeyCoordinatorUrl())
                .isEqualTo(MEASUREMENT_AGGREGATE_ENCRYPTION_KEY_COORDINATOR_URL);
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

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getMeasurementAggregateMainReportingJobPeriodMs())
                .isEqualTo(MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS);
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

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getMeasurementAggregateFallbackReportingJobPeriodMs())
                .isEqualTo(MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS);
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

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getMeasurementAppName()).isEqualTo(MEASUREMENT_APP_NAME);
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

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getMeasurementManifestFileUrl()).isEqualTo(MEASUREMENT_MANIFEST_FILE_URL);
    }

    @Test
    public void testGetMeasurementNetworkConnectTimeoutMs() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementNetworkConnectTimeoutMs())
                .isEqualTo(MEASUREMENT_NETWORK_CONNECT_TIMEOUT_MS);

        final int phOverrideValue = 123;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_NETWORK_CONNECT_TIMEOUT_MS,
                Integer.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementNetworkConnectTimeoutMs()).isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementNetworkReadTimeoutMs() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementNetworkReadTimeoutMs())
                .isEqualTo(MEASUREMENT_NETWORK_READ_TIMEOUT_MS);

        final int phOverrideValue = 123;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_NETWORK_READ_TIMEOUT_MS,
                Integer.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementNetworkReadTimeoutMs()).isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementIsClickVerificationEnabled() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getMeasurementIsClickVerificationEnabled())
                .isEqualTo(MEASUREMENT_IS_CLICK_VERIFICATION_ENABLED);

        final boolean phOverridingValue = false;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_IS_CLICK_VERIFICATION_ENABLED,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementIsClickVerificationEnabled()).isFalse();
    }

    @Test
    public void testGetMeasurementRegistrationInputEventValidWindowMs() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getMeasurementRegistrationInputEventValidWindowMs())
                .isEqualTo(MEASUREMENT_REGISTRATION_INPUT_EVENT_VALID_WINDOW_MS);

        final long phOverridingValue = 8;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_REGISTRATION_INPUT_EVENT_VALID_WINDOW_MS,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementRegistrationInputEventValidWindowMs())
                .isEqualTo(phOverridingValue);
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
    public void testGetFledgeCustomAudienceMaxNameSizeB() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getFledgeCustomAudienceMaxNameSizeB())
                .isEqualTo(FLEDGE_CUSTOM_AUDIENCE_MAX_NAME_SIZE_B);

        final int phOverridingValue = 234;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NAME_SIZE_B,
                Integer.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeCustomAudienceMaxNameSizeB()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetFledgeCustomAudienceMaxDailyUpdateUriSizeB() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getFledgeCustomAudienceMaxDailyUpdateUriSizeB())
                .isEqualTo(FLEDGE_CUSTOM_AUDIENCE_MAX_DAILY_UPDATE_URI_SIZE_B);

        final int phOverridingValue = 234;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_DAILY_UPDATE_URI_SIZE_B,
                Integer.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeCustomAudienceMaxDailyUpdateUriSizeB())
                .isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetFledgeCustomAudienceMaxBiddingLogicUriSizeB() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getFledgeCustomAudienceMaxBiddingLogicUriSizeB())
                .isEqualTo(FLEDGE_CUSTOM_AUDIENCE_MAX_BIDDING_LOGIC_URI_SIZE_B);

        final int phOverridingValue = 234;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_BIDDING_LOGIC_URI_SIZE_B,
                Integer.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeCustomAudienceMaxBiddingLogicUriSizeB())
                .isEqualTo(phOverridingValue);
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
    public void testGetEnforceForegroundStatusForFledgeRunAdSelection() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getEnforceForegroundStatusForFledgeRunAdSelection())
                .isEqualTo(ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION);

        final boolean phOverridingValue = !ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION;

        PhFlagsFixture.overrideForegroundStatusForFledgeRunAdSelection(phOverridingValue);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getEnforceForegroundStatusForFledgeRunAdSelection())
                .isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetEnforceForegroundStatusForFledgeReportImpression() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getEnforceForegroundStatusForFledgeReportImpression())
                .isEqualTo(ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION);

        final boolean phOverridingValue = !ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION;

        PhFlagsFixture.overrideForegroundStatusForFledgeReportImpression(phOverridingValue);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getEnforceForegroundStatusForFledgeReportImpression())
                .isEqualTo(phOverridingValue);
    }

    @Test
    public void testEnforceForegroundStatusForFledgeOverrides() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getEnforceForegroundStatusForFledgeOverrides())
                .isEqualTo(ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDES);

        final boolean phOverridingValue = !ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDES;

        PhFlagsFixture.overrideForegroundStatusForFledgeOverrides(phOverridingValue);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getEnforceForegroundStatusForFledgeOverrides())
                .isEqualTo(phOverridingValue);
    }

    @Test
    public void testEnforceForegroundStatusForFledgeCustomAudience() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getEnforceForegroundStatusForFledgeCustomAudience())
                .isEqualTo(ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE);

        final boolean phOverridingValue = !ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE;

        PhFlagsFixture.overrideForegroundStatusForFledgeCustomAudience(phOverridingValue);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getEnforceForegroundStatusForFledgeCustomAudience())
                .isEqualTo(phOverridingValue);
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
    public void testMeasurementKillSwitch() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getMeasurementKillSwitch())
                .isEqualTo(MEASUREMENT_KILL_SWITCH);

        // Now overriding with the value from PH.
        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_KILL_SWITCH,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementKillSwitch()).isEqualTo(phOverridingValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getMeasurementApiDeleteRegistrationsKillSwitch())
                .isEqualTo(MEASUREMENT_KILL_SWITCH);
    }

    @Test
    public void testMeasurementKillSwitch_globalOverride() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getMeasurementKillSwitch())
                .isEqualTo(MEASUREMENT_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementKillSwitch()).isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementApiDeleteRegistrationsKillSwitch() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementApiDeleteRegistrationsKillSwitch())
                .isEqualTo(MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementApiDeleteRegistrationsKillSwitch())
                .isEqualTo(phOverrideValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getMeasurementApiDeleteRegistrationsKillSwitch())
                .isEqualTo(MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH);
    }

    @Test
    public void testGetMeasurementApiDeleteRegistrationsKillSwitch_measurementOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementApiDeleteRegistrationsKillSwitch())
                .isEqualTo(MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementApiDeleteRegistrationsKillSwitch())
                .isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementApiDeleteRegistrationsKillSwitch_globalOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementApiDeleteRegistrationsKillSwitch())
                .isEqualTo(MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementApiDeleteRegistrationsKillSwitch())
                .isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementApiStatusKillSwitch() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementApiStatusKillSwitch())
                .isEqualTo(MEASUREMENT_API_STATUS_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_API_STATUS_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementApiStatusKillSwitch()).isEqualTo(phOverrideValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getMeasurementApiStatusKillSwitch())
                .isEqualTo(MEASUREMENT_API_STATUS_KILL_SWITCH);
    }

    @Test
    public void testGetMeasurementApiStatusKillSwitch_measurementOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementApiStatusKillSwitch())
                .isEqualTo(MEASUREMENT_API_STATUS_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementApiStatusKillSwitch()).isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementApiStatusKillSwitch_globalOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementApiStatusKillSwitch())
                .isEqualTo(MEASUREMENT_API_STATUS_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementApiStatusKillSwitch()).isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementApiRegisterSourceKillSwitch() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementApiRegisterSourceKillSwitch())
                .isEqualTo(MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementApiRegisterSourceKillSwitch()).isEqualTo(phOverrideValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getMeasurementApiRegisterSourceKillSwitch())
                .isEqualTo(MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH);
    }

    @Test
    public void testGetMeasurementApiRegisterSourceKillSwitch_measurementOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementApiRegisterSourceKillSwitch())
                .isEqualTo(MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementApiRegisterSourceKillSwitch()).isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementApiRegisterSourceKillSwitch_globalOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementApiRegisterSourceKillSwitch())
                .isEqualTo(MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementApiRegisterSourceKillSwitch()).isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementApiRegisterTriggerKillSwitch() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementApiRegisterTriggerKillSwitch())
                .isEqualTo(MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementApiRegisterTriggerKillSwitch()).isEqualTo(phOverrideValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getMeasurementApiRegisterTriggerKillSwitch())
                .isEqualTo(MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH);
    }

    @Test
    public void testGetMeasurementApiRegisterTriggerKillSwitch_measurementOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementApiRegisterTriggerKillSwitch())
                .isEqualTo(MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementApiRegisterTriggerKillSwitch()).isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementApiRegisterTriggerKillSwitch_globalOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementApiRegisterTriggerKillSwitch())
                .isEqualTo(MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementApiRegisterTriggerKillSwitch()).isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementApiRegisterWebSourceKillSwitch() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementApiRegisterWebSourceKillSwitch())
                .isEqualTo(MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementApiRegisterWebSourceKillSwitch())
                .isEqualTo(phOverrideValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getMeasurementApiRegisterWebSourceKillSwitch())
                .isEqualTo(MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH);
    }

    @Test
    public void testGetMeasurementApiRegisterWebSourceKillSwitch_measurementOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementApiRegisterWebSourceKillSwitch())
                .isEqualTo(MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementApiRegisterWebSourceKillSwitch())
                .isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementApiRegisterWebSourceKillSwitch_globalOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementApiRegisterWebSourceKillSwitch())
                .isEqualTo(MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementApiRegisterWebSourceKillSwitch())
                .isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementApiRegisterWebTriggerKillSwitch() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementApiRegisterWebTriggerKillSwitch())
                .isEqualTo(MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementApiRegisterWebTriggerKillSwitch())
                .isEqualTo(phOverrideValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getMeasurementApiRegisterWebTriggerKillSwitch())
                .isEqualTo(MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH);
    }

    @Test
    public void testGetMeasurementApiRegisterWebTriggerKillSwitch_measurementOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementApiRegisterWebTriggerKillSwitch())
                .isEqualTo(MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementApiRegisterWebTriggerKillSwitch())
                .isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementApiRegisterWebTriggerKillSwitch_globalOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementApiRegisterWebTriggerKillSwitch())
                .isEqualTo(MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementApiRegisterWebTriggerKillSwitch())
                .isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementJobAggregateFallbackReportingKillSwitch() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementJobAggregateFallbackReportingKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementJobAggregateFallbackReportingKillSwitch())
                .isEqualTo(phOverrideValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getMeasurementJobAggregateFallbackReportingKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH);
    }

    @Test
    public void testGetMeasurementJobAggregateFallbackReportingKillSwitch_measurementOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementJobAggregateFallbackReportingKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementJobAggregateFallbackReportingKillSwitch())
                .isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementJobAggregateFallbackReportingKillSwitch_globalOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementJobAggregateFallbackReportingKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementJobAggregateFallbackReportingKillSwitch())
                .isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementJobAggregateReportingKillSwitch() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementJobAggregateReportingKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementJobAggregateReportingKillSwitch())
                .isEqualTo(phOverrideValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getMeasurementJobAggregateReportingKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH);
    }

    @Test
    public void testGetMeasurementJobAggregateReportingKillSwitch_measurementOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementJobAggregateReportingKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementJobAggregateReportingKillSwitch())
                .isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementJobAggregateReportingKillSwitch_globalOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementJobAggregateReportingKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementJobAggregateReportingKillSwitch())
                .isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementJobAttributionKillSwitch() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementJobAttributionKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementJobAttributionKillSwitch()).isEqualTo(phOverrideValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getMeasurementJobAttributionKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH);
    }

    @Test
    public void testGetMeasurementJobAttributionKillSwitch_measurementOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementJobAttributionKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementJobAttributionKillSwitch()).isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementJobAttributionKillSwitch_globalOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementJobAttributionKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementJobAttributionKillSwitch()).isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementJobDeleteExpiredKillSwitch() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementJobDeleteExpiredKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementJobDeleteExpiredKillSwitch()).isEqualTo(phOverrideValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getMeasurementJobDeleteExpiredKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH);
    }

    @Test
    public void testGetMeasurementJobDeleteExpiredKillSwitch_measurementOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementJobDeleteExpiredKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementJobDeleteExpiredKillSwitch()).isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementJobDeleteExpiredKillSwitch_globalOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementJobDeleteExpiredKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementJobDeleteExpiredKillSwitch()).isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementJobEventFallbackReportingKillSwitch() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementJobEventFallbackReportingKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementJobEventFallbackReportingKillSwitch())
                .isEqualTo(phOverrideValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getMeasurementJobEventFallbackReportingKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH);
    }

    @Test
    public void testGetMeasurementJobEventFallbackReportingKillSwitch_measurementOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementJobEventFallbackReportingKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementJobEventFallbackReportingKillSwitch())
                .isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementJobEventFallbackReportingKillSwitch_globalOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementJobEventFallbackReportingKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementJobEventFallbackReportingKillSwitch())
                .isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementJobEventReportingKillSwitch() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementJobEventReportingKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementJobEventReportingKillSwitch()).isEqualTo(phOverrideValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getMeasurementJobEventReportingKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH);
    }

    @Test
    public void testGetMeasurementJobEventReportingKillSwitch_measurementOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementJobEventReportingKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementJobEventReportingKillSwitch()).isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementJobEventReportingKillSwitch_globalOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementJobEventReportingKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementJobEventReportingKillSwitch()).isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementReceiverInstallAttributionKillSwitch() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementReceiverInstallAttributionKillSwitch())
                .isEqualTo(MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementReceiverInstallAttributionKillSwitch())
                .isEqualTo(phOverrideValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getMeasurementReceiverInstallAttributionKillSwitch())
                .isEqualTo(MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH);
    }

    @Test
    public void testGetMeasurementReceiverInstallAttributionKillSwitch_measurementOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementReceiverInstallAttributionKillSwitch())
                .isEqualTo(MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementReceiverInstallAttributionKillSwitch())
                .isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementReceiverInstallAttributionKillSwitch_globalOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementReceiverInstallAttributionKillSwitch())
                .isEqualTo(MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementReceiverInstallAttributionKillSwitch())
                .isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementReceiverDeletePackagesKillSwitch() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementReceiverDeletePackagesKillSwitch())
                .isEqualTo(MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementReceiverDeletePackagesKillSwitch())
                .isEqualTo(phOverrideValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getMeasurementReceiverDeletePackagesKillSwitch())
                .isEqualTo(MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH);
    }

    @Test
    public void testGetMeasurementReceiverDeletePackagesKillSwitch_measurementOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementReceiverDeletePackagesKillSwitch())
                .isEqualTo(MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementReceiverDeletePackagesKillSwitch())
                .isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementReceiverDeletePackagesKillSwitch_globalOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementReceiverDeletePackagesKillSwitch())
                .isEqualTo(MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementReceiverDeletePackagesKillSwitch())
                .isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetAdIdKillSwitch() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getAdIdKillSwitch()).isEqualTo(ADID_KILL_SWITCH);

        // Now overriding with the value from PH.
        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ADID_KILL_SWITCH,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getAdIdKillSwitch()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetAppSetIdKillSwitch() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getAppSetIdKillSwitch()).isEqualTo(APPSETID_KILL_SWITCH);

        // Now overriding with the value from PH.
        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_APPSETID_KILL_SWITCH,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getAppSetIdKillSwitch()).isEqualTo(phOverridingValue);
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
    public void test_globalKillswitchOverrides_getAdIdKillSwitch() {
        // Without any overriding, AdId Killswitch is off.
        assertThat(FlagsFactory.getFlags().getAdIdKillSwitch()).isEqualTo(ADID_KILL_SWITCH);

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

        // Global Killswitch is on and overrides the getAdIdKillswitch.
        assertThat(FlagsFactory.getFlags().getAdIdKillSwitch()).isEqualTo(true);
    }

    @Test
    public void test_globalKillswitchOverrides_getAppSetIdKillSwitch() {
        // Without any overriding, AppSetId Killswitch is off.
        assertThat(FlagsFactory.getFlags().getAppSetIdKillSwitch()).isEqualTo(APPSETID_KILL_SWITCH);

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

        // Global Killswitch is on and overrides the getAppSetIdKillswitch.
        assertThat(FlagsFactory.getFlags().getAppSetIdKillSwitch()).isEqualTo(true);
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
    public void testGetFledgeSelectAdsKillSwitch() {
        // without any overrides the Ad Selection Service kill switch should be off
        assertThat(FlagsFactory.getFlags().getFledgeSelectAdsKillSwitch())
                .isEqualTo(FLEDGE_SELECT_ADS_KILL_SWITCH);

        // Now overriding with the value from PH.
        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_SELECT_ADS_KILL_SWITCH,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeSelectAdsKillSwitch()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetFledgeCustomAudienceServiceKillSwitch() {
        // without any overrides the Custom Audience Service kill switch should be off
        assertThat(FlagsFactory.getFlags().getFledgeCustomAudienceServiceKillSwitch())
                .isEqualTo(FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH);

        // Now overriding with the value from PH.
        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeCustomAudienceServiceKillSwitch()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetFledgeGlobalKillSwitchOverridesOtherFledgeKillSwitches() {
        // without any overrides the Fledge API kill switch should be off
        assertThat(FlagsFactory.getFlags().getFledgeSelectAdsKillSwitch())
                .isEqualTo(FLEDGE_SELECT_ADS_KILL_SWITCH);
        assertThat(FlagsFactory.getFlags().getFledgeCustomAudienceServiceKillSwitch())
                .isEqualTo(FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH);

        // Now overriding with the value from PH.
        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFledgeSelectAdsKillSwitch()).isEqualTo(phOverridingValue);
        assertThat(phFlags.getFledgeCustomAudienceServiceKillSwitch()).isEqualTo(phOverridingValue);
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
    public void testGetForegroundStatuslLevelForValidation() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getForegroundStatuslLevelForValidation())
                .isEqualTo(FOREGROUND_STATUS_LEVEL);

        final int phOverridingValue = 6;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FOREGROUND_STATUS_LEVEL,
                Integer.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getForegroundStatuslLevelForValidation()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetEnforceIsolateMaxHeapSize() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getEnforceIsolateMaxHeapSize())
                .isEqualTo(ENFORCE_ISOLATE_MAX_HEAP_SIZE);

        // Now overriding with the value from PH.
        final boolean phOverridingValue = false;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ENFORCE_ISOLATE_MAX_HEAP_SIZE,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getEnforceIsolateMaxHeapSize()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetIsolateMaxHeapSizeBytes() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getIsolateMaxHeapSizeBytes())
                .isEqualTo(ISOLATE_MAX_HEAP_SIZE_BYTES);

        // Now overriding with the value from PH.
        final long phOverridingValue = 1000;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ISOLATE_MAX_HEAP_SIZE_BYTES,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getIsolateMaxHeapSizeBytes()).isEqualTo(phOverridingValue);
    }

    // Troubles between google-java-format and checkstyle
    // CHECKSTYLE:OFF IndentationCheck
    @Test
    public void testGetReportImpressionOverallTimeoutMs() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getReportImpressionOverallTimeoutMs())
                .isEqualTo(FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS);

        final int phOverrideValue = 4;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS,
                Integer.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getReportImpressionOverallTimeoutMs()).isEqualTo(phOverrideValue);
    }

    @Test
    public void testIsDisableTopicsEnrollmentCheck() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().isDisableTopicsEnrollmentCheck())
                .isEqualTo(DISABLE_TOPICS_ENROLLMENT_CHECK);

        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_DISABLE_TOPICS_ENROLLMENT_CHECK,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.isDisableTopicsEnrollmentCheck()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetEnforceForegroundStatusForTopics() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getEnforceForegroundStatusForTopics())
                .isEqualTo(ENFORCE_FOREGROUND_STATUS_TOPICS);

        // Now overriding with the value from PH.
        final boolean disabledEnforcing = !ENFORCE_FOREGROUND_STATUS_TOPICS;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ENFORCE_FOREGROUND_STATUS_TOPICS,
                Boolean.toString(disabledEnforcing),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getEnforceForegroundStatusForTopics()).isEqualTo(disabledEnforcing);
    }

    @Test
    public void testDump() throws FileNotFoundException {
        // Trigger the dump to verify no crash
        PrintWriter printWriter =
                new PrintWriter(
                        new Writer() {
                            @Override
                            public void write(char[] cbuf, int off, int len) throws IOException {}

                            @Override
                            public void flush() throws IOException {}

                            @Override
                            public void close() throws IOException {}
                        });
        String[] args = new String[] {};
        Flags phFlags = FlagsFactory.getFlags();
        phFlags.dump(printWriter, args);
    }
    // CHECKSTYLE:ON IndentationCheck
}
