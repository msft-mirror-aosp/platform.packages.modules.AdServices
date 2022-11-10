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
import static com.android.adservices.service.Flags.ADSERVICES_ENABLED;
import static com.android.adservices.service.Flags.APPSETID_KILL_SWITCH;
import static com.android.adservices.service.Flags.ASYNC_REGISTRATION_JOB_QUEUE_INTERVAL_MS;
import static com.android.adservices.service.Flags.CLASSIFIER_DESCRIPTION_MAX_LENGTH;
import static com.android.adservices.service.Flags.CLASSIFIER_DESCRIPTION_MAX_WORDS;
import static com.android.adservices.service.Flags.CLASSIFIER_FORCE_USE_BUNDLED_FILES;
import static com.android.adservices.service.Flags.CLASSIFIER_NUMBER_OF_TOP_LABELS;
import static com.android.adservices.service.Flags.CLASSIFIER_THRESHOLD;
import static com.android.adservices.service.Flags.DEFAULT_CLASSIFIER_TYPE;
import static com.android.adservices.service.Flags.DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.Flags.DISABLE_MEASUREMENT_ENROLLMENT_CHECK;
import static com.android.adservices.service.Flags.DISABLE_TOPICS_ENROLLMENT_CHECK;
import static com.android.adservices.service.Flags.DOWNLOADER_CONNECTION_TIMEOUT_MS;
import static com.android.adservices.service.Flags.DOWNLOADER_MAX_DOWNLOAD_THREADS;
import static com.android.adservices.service.Flags.DOWNLOADER_READ_TIMEOUT_MS;
import static com.android.adservices.service.Flags.ENABLE_DATABASE_SCHEMA_VERSION_3;
import static com.android.adservices.service.Flags.ENABLE_TOPIC_CONTRIBUTORS_CHECK;
import static com.android.adservices.service.Flags.ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.Flags.ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDES;
import static com.android.adservices.service.Flags.ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION;
import static com.android.adservices.service.Flags.ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION;
import static com.android.adservices.service.Flags.ENFORCE_FOREGROUND_STATUS_TOPICS;
import static com.android.adservices.service.Flags.ENFORCE_ISOLATE_MAX_HEAP_SIZE;
import static com.android.adservices.service.Flags.FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_BUYER_MS;
import static com.android.adservices.service.Flags.FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS;
import static com.android.adservices.service.Flags.FLEDGE_AD_SELECTION_EXPIRATION_WINDOW_S;
import static com.android.adservices.service.Flags.FLEDGE_AD_SELECTION_MAX_CONCURRENT_BIDDING_COUNT;
import static com.android.adservices.service.Flags.FLEDGE_AD_SELECTION_OFF_DEVICE_ENABLED;
import static com.android.adservices.service.Flags.FLEDGE_AD_SELECTION_OFF_DEVICE_OVERALL_TIMEOUT_MS;
import static com.android.adservices.service.Flags.FLEDGE_AD_SELECTION_OFF_DEVICE_REQUEST_COMPRESSION_ENABLED;
import static com.android.adservices.service.Flags.FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.service.Flags.FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS;
import static com.android.adservices.service.Flags.FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS;
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
import static com.android.adservices.service.Flags.GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.Flags.GLOBAL_KILL_SWITCH;
import static com.android.adservices.service.Flags.ISOLATE_MAX_HEAP_SIZE_BYTES;
import static com.android.adservices.service.Flags.MAINTENANCE_JOB_FLEX_MS;
import static com.android.adservices.service.Flags.MAINTENANCE_JOB_PERIOD_MS;
import static com.android.adservices.service.Flags.MDD_BACKGROUND_TASK_KILL_SWITCH;
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
import static com.android.adservices.service.Flags.MEASUREMENT_DB_SIZE_LIMIT;
import static com.android.adservices.service.Flags.MEASUREMENT_ENFORCE_FOREGROUND_STATUS_DELETE_REGISTRATIONS;
import static com.android.adservices.service.Flags.MEASUREMENT_ENFORCE_FOREGROUND_STATUS_GET_STATUS;
import static com.android.adservices.service.Flags.MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCE;
import static com.android.adservices.service.Flags.MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_TRIGGER;
import static com.android.adservices.service.Flags.MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_SOURCE;
import static com.android.adservices.service.Flags.MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_TRIGGER;
import static com.android.adservices.service.Flags.MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS;
import static com.android.adservices.service.Flags.MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS;
import static com.android.adservices.service.Flags.MEASUREMENT_IS_CLICK_VERIFICATION_ENABLED;
import static com.android.adservices.service.Flags.MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_JOB_DELETE_UNINSTALLED_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_MANIFEST_FILE_URL;
import static com.android.adservices.service.Flags.MEASUREMENT_NETWORK_CONNECT_TIMEOUT_MS;
import static com.android.adservices.service.Flags.MEASUREMENT_NETWORK_READ_TIMEOUT_MS;
import static com.android.adservices.service.Flags.MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_REGISTRATION_INPUT_EVENT_VALID_WINDOW_MS;
import static com.android.adservices.service.Flags.MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH;
import static com.android.adservices.service.Flags.NUMBER_OF_EPOCHS_TO_KEEP_IN_HISTORY;
import static com.android.adservices.service.Flags.PPAPI_APP_ALLOW_LIST;
import static com.android.adservices.service.Flags.PPAPI_APP_SIGNATURE_ALLOW_LIST;
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
import static com.android.adservices.service.PhFlags.KEY_ADSERVICES_ENABLED;
import static com.android.adservices.service.PhFlags.KEY_APPSETID_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_CLASSIFIER_DESCRIPTION_MAX_LENGTH;
import static com.android.adservices.service.PhFlags.KEY_CLASSIFIER_DESCRIPTION_MAX_WORDS;
import static com.android.adservices.service.PhFlags.KEY_CLASSIFIER_FORCE_USE_BUNDLED_FILES;
import static com.android.adservices.service.PhFlags.KEY_CLASSIFIER_NUMBER_OF_TOP_LABELS;
import static com.android.adservices.service.PhFlags.KEY_CLASSIFIER_THRESHOLD;
import static com.android.adservices.service.PhFlags.KEY_CLASSIFIER_TYPE;
import static com.android.adservices.service.PhFlags.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.PhFlags.KEY_DISABLE_MEASUREMENT_ENROLLMENT_CHECK;
import static com.android.adservices.service.PhFlags.KEY_DISABLE_TOPICS_ENROLLMENT_CHECK;
import static com.android.adservices.service.PhFlags.KEY_DOWNLOADER_CONNECTION_TIMEOUT_MS;
import static com.android.adservices.service.PhFlags.KEY_DOWNLOADER_MAX_DOWNLOAD_THREADS;
import static com.android.adservices.service.PhFlags.KEY_DOWNLOADER_READ_TIMEOUT_MS;
import static com.android.adservices.service.PhFlags.KEY_ENABLE_DATABASE_SCHEMA_VERSION_3;
import static com.android.adservices.service.PhFlags.KEY_ENABLE_TOPIC_CONTRIBUTORS_CHECK;
import static com.android.adservices.service.PhFlags.KEY_ENFORCE_FOREGROUND_STATUS_TOPICS;
import static com.android.adservices.service.PhFlags.KEY_ENFORCE_ISOLATE_MAX_HEAP_SIZE;
import static com.android.adservices.service.PhFlags.KEY_ENROLLMENT_BLOCKLIST_IDS;
import static com.android.adservices.service.PhFlags.KEY_FLEDE_AD_SELECTION_OFF_DEVICE_ENABLED;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_BUYER_MS;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_AD_SELECTION_EXPIRATION_WINDOW_S;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_AD_SELECTION_MAX_CONCURRENT_BIDDING_COUNT;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_AD_SELECTION_OFF_DEVICE_OVERALL_TIMEOUT_MS;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_AD_SELECTION_OFF_DEVICE_REQUEST_COMPRESSION_ENABLED;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS;
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
import static com.android.adservices.service.PhFlags.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.PhFlags.KEY_GLOBAL_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_ISOLATE_MAX_HEAP_SIZE_BYTES;
import static com.android.adservices.service.PhFlags.KEY_MAINTENANCE_JOB_FLEX_MS;
import static com.android.adservices.service.PhFlags.KEY_MAINTENANCE_JOB_PERIOD_MS;
import static com.android.adservices.service.PhFlags.KEY_MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES;
import static com.android.adservices.service.PhFlags.KEY_MDD_BACKGROUND_TASK_KILL_SWITCH;
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
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_DB_SIZE_LIMIT;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_DELETE_REGISTRATIONS;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_GET_STATUS;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCE;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_TRIGGER;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_SOURCE;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_TRIGGER;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_IS_CLICK_VERIFICATION_ENABLED;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_JOB_DELETE_UNINSTALLED_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_MANIFEST_FILE_URL;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_NETWORK_CONNECT_TIMEOUT_MS;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_NETWORK_READ_TIMEOUT_MS;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_REGISTRATION_INPUT_EVENT_VALID_WINDOW_MS;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_NUMBER_OF_EPOCHS_TO_KEEP_IN_HISTORY;
import static com.android.adservices.service.PhFlags.KEY_PPAPI_APP_ALLOW_LIST;
import static com.android.adservices.service.PhFlags.KEY_PPAPI_APP_SIGNATURE_ALLOW_LIST;
import static com.android.adservices.service.PhFlags.KEY_REGISTRATION_JOB_QUEUE_INTERVAL_MS;
import static com.android.adservices.service.PhFlags.KEY_SDK_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.PhFlags.KEY_TOPICS_EPOCH_JOB_FLEX_MS;
import static com.android.adservices.service.PhFlags.KEY_TOPICS_EPOCH_JOB_PERIOD_MS;
import static com.android.adservices.service.PhFlags.KEY_TOPICS_KILL_SWITCH;
import static com.android.adservices.service.PhFlags.KEY_TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS;
import static com.android.adservices.service.PhFlags.KEY_TOPICS_NUMBER_OF_RANDOM_TOPICS;
import static com.android.adservices.service.PhFlags.KEY_TOPICS_NUMBER_OF_TOP_TOPICS;
import static com.android.adservices.service.PhFlags.KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

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

        final long illegalPhOverridingValue = -1;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_TOPICS_EPOCH_JOB_PERIOD_MS,
                Long.toString(illegalPhOverridingValue),
                /* makeDefault */ false);
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    phFlags.getTopicsEpochJobPeriodMs();
                });
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

        // Validate that topicsEpochJobFlexMs got from PH > 0 and
        // less than topicsEpochJobPeriodMs
        final long illegalPhOverridingValue = -1;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_TOPICS_EPOCH_JOB_FLEX_MS,
                Long.toString(illegalPhOverridingValue),
                /* makeDefault */ false);
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    phFlags.getTopicsEpochJobFlexMs();
                });
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

        // Validate that topicsPercentageForRandomTopic got from PH is between 0 and 100
        final long illegalPhOverridingValue = -1;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC,
                Long.toString(illegalPhOverridingValue),
                /* makeDefault */ false);
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    phFlags.getTopicsPercentageForRandomTopic();
                });
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

        // Validate that topicsNumberOfRandomTopics got from PH >= 0
        final long illegalPhOverridingValue = -1;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_TOPICS_NUMBER_OF_RANDOM_TOPICS,
                Long.toString(illegalPhOverridingValue),
                /* makeDefault */ false);
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    phFlags.getTopicsNumberOfRandomTopics();
                });
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

        // Validate that topicsNumberOfTopTopics got from PH >= 0
        final long illegalPhOverridingValue = -1;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_TOPICS_NUMBER_OF_TOP_TOPICS,
                Long.toString(illegalPhOverridingValue),
                /* makeDefault */ false);
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    phFlags.getTopicsNumberOfTopTopics();
                });
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

        // Validate that topicsNumberOfLookBackEpochs got from PH >= 0
        final long illegalPhOverridingValue = -1;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS,
                Long.toString(illegalPhOverridingValue),
                /* makeDefault */ false);
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    phFlags.getTopicsNumberOfLookBackEpochs();
                });
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
    public void testGetClassifierThreshold() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getClassifierThreshold())
                .isEqualTo(CLASSIFIER_THRESHOLD);

        float phOverridingValue = 0.3f;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_CLASSIFIER_THRESHOLD,
                Float.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getClassifierThreshold()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetClassifierDescriptionMaxWords() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getClassifierDescriptionMaxWords())
                .isEqualTo(CLASSIFIER_DESCRIPTION_MAX_WORDS);

        int phOverridingValue = 150;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_CLASSIFIER_DESCRIPTION_MAX_WORDS,
                Integer.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getClassifierDescriptionMaxWords()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetClassifierDescriptionMaxLength() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getClassifierDescriptionMaxLength())
                .isEqualTo(CLASSIFIER_DESCRIPTION_MAX_LENGTH);

        int phOverridingValue = 999;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_CLASSIFIER_DESCRIPTION_MAX_LENGTH,
                Integer.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getClassifierDescriptionMaxLength()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetClassifierForceUseBundledFiles() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getClassifierForceUseBundledFiles())
                .isEqualTo(CLASSIFIER_FORCE_USE_BUNDLED_FILES);

        boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_CLASSIFIER_FORCE_USE_BUNDLED_FILES,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getClassifierForceUseBundledFiles()).isEqualTo(phOverridingValue);
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

        // Validate that maintenanceJobPeriodMs got from PH > 0
        final long illegalPhOverridingValue = -1;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MAINTENANCE_JOB_PERIOD_MS,
                Long.toString(illegalPhOverridingValue),
                /* makeDefault */ false);
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    phFlags.getMaintenanceJobPeriodMs();
                });
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

        // Validate that maintenanceJobFlexMs got from PH > 0 and less
        // than maintenanceJobPeriodMs
        final long illegalPhOverridingValue = -1;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MAINTENANCE_JOB_FLEX_MS,
                Long.toString(illegalPhOverridingValue),
                /* makeDefault */ false);
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    phFlags.getMaintenanceJobFlexMs();
                });
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
    public void testGetAdSelectionMaxConcurrentBiddingCount() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getAdSelectionMaxConcurrentBiddingCount())
                .isEqualTo(FLEDGE_AD_SELECTION_MAX_CONCURRENT_BIDDING_COUNT);

        final int phOverrideValue = 4;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_AD_SELECTION_MAX_CONCURRENT_BIDDING_COUNT,
                Integer.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getAdSelectionMaxConcurrentBiddingCount()).isEqualTo(phOverrideValue);
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
    public void testGetAdSelectionBiddingTimeoutPerBuyerMs() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getAdSelectionBiddingTimeoutPerBuyerMs())
                .isEqualTo(FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_BUYER_MS);

        final long phOverrideValue = 5000;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_BUYER_MS,
                Long.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getAdSelectionBiddingTimeoutPerBuyerMs()).isEqualTo(phOverrideValue);
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
    public void testGetAdSelectionSelectingOutcomeTimeoutMs() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getAdSelectionSelectingOutcomeTimeoutMs())
                .isEqualTo(FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS);

        final int phOverrideValue = 5;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS,
                Integer.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getAdSelectionSelectingOutcomeTimeoutMs()).isEqualTo(phOverrideValue);
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
    public void testGetOffDeviceAdSelectionOverallTimeoutMs() {
        assertThat(FlagsFactory.getFlags().getAdSelectionOffDeviceOverallTimeoutMs())
                .isEqualTo(FLEDGE_AD_SELECTION_OFF_DEVICE_OVERALL_TIMEOUT_MS);

        final int phOverrideValue = 4;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_AD_SELECTION_OFF_DEVICE_OVERALL_TIMEOUT_MS,
                Integer.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getAdSelectionOffDeviceOverallTimeoutMs()).isEqualTo(phOverrideValue);
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
    public void testGetMeasurementDbSizeLimit() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getMeasurementDbSizeLimit())
                .isEqualTo(MEASUREMENT_DB_SIZE_LIMIT);

        final long phOverridingValue = 1024 * 1024 * 5;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_DB_SIZE_LIMIT,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementDbSizeLimit()).isEqualTo(phOverridingValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getMeasurementDbSizeLimit()).isEqualTo(MEASUREMENT_DB_SIZE_LIMIT);
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
    public void testGetEnforceForegroundStatusForMeasurementDeleteRegistrations() {
        // without any overriding, the value is hard coded constant
        assertThat(
                        FlagsFactory.getFlags()
                                .getEnforceForegroundStatusForMeasurementDeleteRegistrations())
                .isEqualTo(MEASUREMENT_ENFORCE_FOREGROUND_STATUS_DELETE_REGISTRATIONS);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_DELETE_REGISTRATIONS,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getEnforceForegroundStatusForMeasurementDeleteRegistrations())
                .isEqualTo(phOverrideValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getEnforceForegroundStatusForMeasurementDeleteRegistrations())
                .isEqualTo(MEASUREMENT_ENFORCE_FOREGROUND_STATUS_DELETE_REGISTRATIONS);
    }

    @Test
    public void testGetEnforceForegroundStatusForMeasurementRegisterSource() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getEnforceForegroundStatusForMeasurementRegisterSource())
                .isEqualTo(MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCE);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCE,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getEnforceForegroundStatusForMeasurementRegisterSource())
                .isEqualTo(phOverrideValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getEnforceForegroundStatusForMeasurementRegisterSource())
                .isEqualTo(MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCE);
    }

    @Test
    public void testGetEnforceForegroundStatusForMeasurementRegisterTrigger() {
        // without any overriding, the value is hard coded constant
        assertThat(
                        FlagsFactory.getFlags()
                                .getEnforceForegroundStatusForMeasurementRegisterTrigger())
                .isEqualTo(MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_TRIGGER);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_TRIGGER,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getEnforceForegroundStatusForMeasurementRegisterTrigger())
                .isEqualTo(phOverrideValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getEnforceForegroundStatusForMeasurementRegisterTrigger())
                .isEqualTo(MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_TRIGGER);
    }

    @Test
    public void testGetEnforceForegroundStatusForMeasurementRegisterWebSource() {
        // without any overriding, the value is hard coded constant
        assertThat(
                        FlagsFactory.getFlags()
                                .getEnforceForegroundStatusForMeasurementRegisterWebSource())
                .isEqualTo(MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_SOURCE);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_SOURCE,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getEnforceForegroundStatusForMeasurementRegisterWebSource())
                .isEqualTo(phOverrideValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getEnforceForegroundStatusForMeasurementRegisterWebSource())
                .isEqualTo(MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_SOURCE);
    }

    @Test
    public void testGetEnforceForegroundStatusForMeasurementRegisterWebTrigger() {
        // without any overriding, the value is hard coded constant
        assertThat(
                        FlagsFactory.getFlags()
                                .getEnforceForegroundStatusForMeasurementRegisterWebTrigger())
                .isEqualTo(MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_TRIGGER);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_TRIGGER,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getEnforceForegroundStatusForMeasurementRegisterWebTrigger())
                .isEqualTo(phOverrideValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getEnforceForegroundStatusForMeasurementRegisterWebTrigger())
                .isEqualTo(MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_WEB_TRIGGER);
    }

    @Test
    public void testGetEnforceForegroundStatusForMeasurementStatus() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getEnforceForegroundStatusForMeasurementStatus())
                .isEqualTo(MEASUREMENT_ENFORCE_FOREGROUND_STATUS_GET_STATUS);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_GET_STATUS,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getEnforceForegroundStatusForMeasurementStatus())
                .isEqualTo(phOverrideValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getEnforceForegroundStatusForMeasurementStatus())
                .isEqualTo(MEASUREMENT_ENFORCE_FOREGROUND_STATUS_GET_STATUS);
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
    public void testGetAdServicesEnabled() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getAdServicesEnabled()).isEqualTo(ADSERVICES_ENABLED);

        // Now overriding with the value from PH.
        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ADSERVICES_ENABLED,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getAdServicesEnabled()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetGaUxFeatureEnabled() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getGaUxFeatureEnabled())
                .isEqualTo(GA_UX_FEATURE_ENABLED);

        // Now overriding with the value from PH.
        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_GA_UX_FEATURE_ENABLED,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getGaUxFeatureEnabled()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetAdServicesEnabledWhenGlobalKillSwitchOn() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getAdServicesEnabled()).isEqualTo(ADSERVICES_ENABLED);

        // Now overriding with the value from PH.
        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ADSERVICES_ENABLED,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);
        // enable global kill switch ->
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(true),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getAdServicesEnabled()).isFalse();
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
    public void testGetMeasurementJobDeleteUninstalledKillSwitch() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementJobDeleteUninstalledKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_DELETE_UNINSTALLED_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_JOB_DELETE_UNINSTALLED_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementJobDeleteUninstalledKillSwitch())
                .isEqualTo(phOverrideValue);

        Flags flags = FlagsFactory.getFlagsForTest();
        assertThat(flags.getMeasurementJobDeleteUninstalledKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_DELETE_UNINSTALLED_KILL_SWITCH);
    }

    @Test
    public void testGetMeasurementJobDeleteUninstalledKillSwitch_measurementOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementJobDeleteUninstalledKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementJobDeleteUninstalledKillSwitch())
                .isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementJobDeleteUninstalledKillSwitch_globalOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMeasurementJobDeleteUninstalledKillSwitch())
                .isEqualTo(MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementJobDeleteUninstalledKillSwitch())
                .isEqualTo(phOverrideValue);
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
    public void testGetAdIdKillSwitch_globalOverride() {
        // test that global killswitch override has no effect on
        // AdIdKillswitch.
        assertThat(FlagsFactory.getFlags().getAdIdKillSwitch()).isEqualTo(ADID_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getAdIdKillSwitch()).isEqualTo(ADID_KILL_SWITCH);
    }

    @Test
    public void testGetMeasurementRegistrationJobQueueKillSwitch() {
        // without any overrides the Registration Job Queue kill switch should be off
        assertThat(FlagsFactory.getFlags().getRegistrationJobQueueKillSwitch())
                .isEqualTo(MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH);

        // Now overriding with the value from PH.
        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getRegistrationJobQueueKillSwitch()).isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementRegistrationJobQueueKillSwitch_measurementOverride() {
        // without any overrides the Registration Job Queue kill switch should be off
        assertThat(FlagsFactory.getFlags().getRegistrationJobQueueKillSwitch())
                .isEqualTo(MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH);

        // Now overriding with the value from PH.
        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getRegistrationJobQueueKillSwitch()).isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetMeasurementRegistrationJobQueueKillSwitch_globalOverride() {
        // without any overrides the Registration Job Queue kill switch should be off
        assertThat(FlagsFactory.getFlags().getRegistrationJobQueueKillSwitch())
                .isEqualTo(MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH);

        // Now overriding with the value from PH.
        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getRegistrationJobQueueKillSwitch()).isEqualTo(phOverrideValue);
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
    public void testGetMddBackgroundTaskKillSwitch() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getMddBackgroundTaskKillSwitch())
                .isEqualTo(MDD_BACKGROUND_TASK_KILL_SWITCH);

        // Now overriding with the value from PH.
        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MDD_BACKGROUND_TASK_KILL_SWITCH,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMddBackgroundTaskKillSwitch()).isEqualTo(phOverridingValue);
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

        // Global Killswitch is on, but is ignored by the getAdIdKillswitch.
        assertThat(FlagsFactory.getFlags().getAdIdKillSwitch()).isEqualTo(false);
    }

    @Test
    public void testGetAppSetIdKillSwitch_globalOverride() {
        // test that global killswitch override has no effect on
        // AppSetIdKillswitch.
        assertThat(FlagsFactory.getFlags().getAppSetIdKillSwitch()).isEqualTo(APPSETID_KILL_SWITCH);

        final boolean phOverrideValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getAppSetIdKillSwitch()).isEqualTo(APPSETID_KILL_SWITCH);
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

        // Global Killswitch is on, but is ignored by getAppSetIdKillswitch.
        assertThat(FlagsFactory.getFlags().getAppSetIdKillSwitch()).isEqualTo(false);
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
    public void testGetPpapiAppSignatureAllowList() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getPpapiAppSignatureAllowList())
                .isEqualTo(PPAPI_APP_SIGNATURE_ALLOW_LIST);

        // Now overriding with the value from PH.
        final String phOverridingValue = "SomePackageName,AnotherPackageName";
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_PPAPI_APP_SIGNATURE_ALLOW_LIST,
                phOverridingValue,
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getPpapiAppSignatureAllowList()).isEqualTo(phOverridingValue);
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

        final float illegalPhOverridingValue = -1;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_SDK_REQUEST_PERMITS_PER_SECOND,
                Float.toString(illegalPhOverridingValue),
                /* makeDefault */ false);

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    phFlags.getSdkRequestPermitsPerSecond();
                });
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

        final long illegalPhOverridingValue = -1;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_NUMBER_OF_EPOCHS_TO_KEEP_IN_HISTORY,
                Long.toString(illegalPhOverridingValue),
                /* makeDefault */ false);

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    phFlags.getNumberOfEpochsToKeepInHistory();
                });
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

        final boolean phOverridingValue = !DISABLE_TOPICS_ENROLLMENT_CHECK;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_DISABLE_TOPICS_ENROLLMENT_CHECK,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.isDisableTopicsEnrollmentCheck()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetDisableFledgeEnrollmentCheck() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getDisableFledgeEnrollmentCheck())
                .isEqualTo(DISABLE_FLEDGE_ENROLLMENT_CHECK);

        final boolean phOverridingValue = !DISABLE_FLEDGE_ENROLLMENT_CHECK;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getDisableFledgeEnrollmentCheck()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testIsDisableMeasurementEnrollmentCheck() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().isDisableMeasurementEnrollmentCheck())
                .isEqualTo(DISABLE_MEASUREMENT_ENROLLMENT_CHECK);

        final boolean phOverridingValue = !DISABLE_MEASUREMENT_ENROLLMENT_CHECK;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_DISABLE_MEASUREMENT_ENROLLMENT_CHECK,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.isDisableMeasurementEnrollmentCheck()).isEqualTo(phOverridingValue);
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
    public void testGetOffDeviceAdSelectionEnabled() {
        assertThat(FlagsFactory.getFlags().getAdSelectionOffDeviceEnabled())
                .isEqualTo(FLEDGE_AD_SELECTION_OFF_DEVICE_ENABLED);

        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDE_AD_SELECTION_OFF_DEVICE_ENABLED,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getAdSelectionOffDeviceEnabled()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testgetFledgeAdselectionExpirationWindow() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getAdSelectionExpirationWindowS())
                .isEqualTo(FLEDGE_AD_SELECTION_EXPIRATION_WINDOW_S);

        final int phOverridingValue = 4;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_AD_SELECTION_EXPIRATION_WINDOW_S,
                Integer.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getAdSelectionExpirationWindowS()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetRegistrationJobQueueIntervalMs() {
        assertThat(FlagsFactory.getFlags().getRegistrationJobQueueIntervalMs())
                .isEqualTo(ASYNC_REGISTRATION_JOB_QUEUE_INTERVAL_MS);

        final long phOverridingValue = 1L;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_REGISTRATION_JOB_QUEUE_INTERVAL_MS,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getRegistrationJobQueueIntervalMs()).isEqualTo(phOverridingValue);
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

    @Test
    public void testGetMaxResponseBasedRegistrationPayloadSizeBytes_measurementOverride() {
        // without any overriding, the value is hard coded constant
        assertThat(FlagsFactory.getFlags().getMaxResponseBasedRegistrationPayloadSizeBytes())
                .isEqualTo(Flags.MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES);

        final long phOverrideValue = 5L;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES,
                Long.toString(phOverrideValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMaxResponseBasedRegistrationPayloadSizeBytes())
                .isEqualTo(phOverrideValue);
    }

    @Test
    public void testGetOffDeviceAdSelectionRequestCompressionEnabled() {
        assertThat(FlagsFactory.getFlags().getAdSelectionOffDeviceRequestCompressionEnabled())
                .isEqualTo(FLEDGE_AD_SELECTION_OFF_DEVICE_REQUEST_COMPRESSION_ENABLED);

        final boolean phOverridingValue = false;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_AD_SELECTION_OFF_DEVICE_REQUEST_COMPRESSION_ENABLED,
                Boolean.toString(phOverridingValue),
                false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getAdSelectionOffDeviceRequestCompressionEnabled())
                .isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetEnableTopicContributorsCheck() {
        assertThat(FlagsFactory.getFlags().getEnableTopicContributorsCheck())
                .isEqualTo(ENABLE_TOPIC_CONTRIBUTORS_CHECK);

        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ENABLE_TOPIC_CONTRIBUTORS_CHECK,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getEnableTopicContributorsCheck()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetEnableDatabaseSchemaVersion3() {
        assertThat(FlagsFactory.getFlags().getEnableDatabaseSchemaVersion3())
                .isEqualTo(ENABLE_DATABASE_SCHEMA_VERSION_3);

        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ENABLE_DATABASE_SCHEMA_VERSION_3,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getEnableDatabaseSchemaVersion3()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testEnrollmentBlocklist_singleEnrollment() {
        Flags phFlags = FlagsFactory.getFlags();

        String blocklistedEnrollmentId = "enrollmentId1";
        setEnrollmentBlocklist(blocklistedEnrollmentId);

        assertThat(phFlags.getEnrollmentBlocklist()).contains(blocklistedEnrollmentId);
    }

    @Test
    public void testEnrollmentBlocklist_multipleEnrollments() {
        Flags phFlags = FlagsFactory.getFlags();

        String enrollmentId1 = "enrollmentId1";
        String enrollmentId2 = "enrollmentId2";
        String enrollmentId3 = "enrollmentId3";

        String blocklistedEnrollmentId =
                String.format("%s,%s,%s", enrollmentId1, enrollmentId2, enrollmentId3);
        setEnrollmentBlocklist(blocklistedEnrollmentId);

        assertThat(phFlags.getEnrollmentBlocklist())
                .containsExactly(enrollmentId1, enrollmentId2, enrollmentId3);
    }

    @Test
    public void testEnrollmentBlocklist_malformedList() {
        Flags phFlags = FlagsFactory.getFlags();

        String enrollmentId1 = "enrollmentId1";
        String enrollmentId2 = "enrollmentId2";
        String enrollmentId3 = "enrollmentId3";

        String blocklistedEnrollmentId =
                String.format("%s%s%s", enrollmentId1, enrollmentId2, enrollmentId3);
        setEnrollmentBlocklist(blocklistedEnrollmentId);

        assertThat(phFlags.getEnrollmentBlocklist())
                .containsNoneOf(enrollmentId1, enrollmentId2, enrollmentId3);
    }

    private void setEnrollmentBlocklist(String blocklistFlag) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ENROLLMENT_BLOCKLIST_IDS,
                blocklistFlag,
                false);
    }
    // CHECKSTYLE:ON IndentationCheck
}
