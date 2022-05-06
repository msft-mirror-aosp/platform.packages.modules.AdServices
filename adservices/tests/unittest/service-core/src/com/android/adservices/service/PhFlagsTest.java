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

import static com.android.adservices.service.Flags.MAINTENANCE_JOB_FLEX_MS;
import static com.android.adservices.service.Flags.MAINTENANCE_JOB_PERIOD_MS;
import static com.android.adservices.service.Flags.MEASUREMENT_APP_NAME;
import static com.android.adservices.service.Flags.MEASUREMENT_MAIN_REPORTING_JOB_PERIOD_MS;
import static com.android.adservices.service.Flags.TOPICS_EPOCH_JOB_FLEX_MS;
import static com.android.adservices.service.Flags.TOPICS_EPOCH_JOB_PERIOD_MS;
import static com.android.adservices.service.Flags.TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS;
import static com.android.adservices.service.Flags.TOPICS_NUMBER_OF_RANDOM_TOPICS;
import static com.android.adservices.service.Flags.TOPICS_NUMBER_OF_TOP_TOPICS;
import static com.android.adservices.service.Flags.TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC;
import static com.android.adservices.service.PhFlags.KEY_MAINTENANCE_JOB_FLEX_MS;
import static com.android.adservices.service.PhFlags.KEY_MAINTENANCE_JOB_PERIOD_MS;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_APP_NAME;
import static com.android.adservices.service.PhFlags.KEY_MEASUREMENT_MAIN_REPORTING_JOB_PERIOD_MS;
import static com.android.adservices.service.PhFlags.KEY_TOPICS_EPOCH_JOB_FLEX_MS;
import static com.android.adservices.service.PhFlags.KEY_TOPICS_EPOCH_JOB_PERIOD_MS;
import static com.android.adservices.service.PhFlags.KEY_TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS;
import static com.android.adservices.service.PhFlags.KEY_TOPICS_NUMBER_OF_RANDOM_TOPICS;
import static com.android.adservices.service.PhFlags.KEY_TOPICS_NUMBER_OF_TOP_TOPICS;
import static com.android.adservices.service.PhFlags.KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC;

import static com.google.common.truth.Truth.assertThat;

import android.provider.DeviceConfig;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.testing.TestableDeviceConfig;

import org.junit.Rule;
import org.junit.Test;

/** Unit tests for {@link com.android.adservices.service.PhFlags} */
@SmallTest
public class PhFlagsTest {
    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule
            mDeviceConfigRule = new TestableDeviceConfig.TestableDeviceConfigRule();

    @Test
    public void testGetTopicsEpochJobPeriodMs() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getTopicsEpochJobPeriodMs()).isEqualTo(
                TOPICS_EPOCH_JOB_PERIOD_MS);

        // Now overriding with the value from PH.
        final long phOverridingValue = 1;
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_TOPICS_EPOCH_JOB_PERIOD_MS,
                Long.toString(phOverridingValue),
                /* makeDefault */false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getTopicsEpochJobPeriodMs()).isEqualTo(
                phOverridingValue);
    }

    @Test
    public void testGetTopicsEpochJobFlexMs() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getTopicsEpochJobFlexMs()).isEqualTo(
                TOPICS_EPOCH_JOB_FLEX_MS);

        // Now overriding with the value from PH.
        final long phOverridingValue = 2;
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_TOPICS_EPOCH_JOB_FLEX_MS,
                Long.toString(phOverridingValue),
                /* makeDefault */false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getTopicsEpochJobFlexMs()).isEqualTo(
                phOverridingValue);
    }

    @Test
    public void testGetTopicsPercentageForRandomTopic() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getTopicsPercentageForRandomTopic()).isEqualTo(
                TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC);

        final long phOverridingValue = 3;
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC,
                Long.toString(phOverridingValue),
                /* makeDefault */false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getTopicsPercentageForRandomTopic()).isEqualTo(
                phOverridingValue);
    }

    @Test
    public void testGetTopicsNumberOfRandomTopics() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getTopicsNumberOfRandomTopics()).isEqualTo(
                TOPICS_NUMBER_OF_RANDOM_TOPICS);

        final long phOverridingValue = 4;
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_TOPICS_NUMBER_OF_RANDOM_TOPICS,
                Long.toString(phOverridingValue),
                /* makeDefault */false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getTopicsNumberOfRandomTopics()).isEqualTo(
                phOverridingValue);
    }

    @Test
    public void testGetTopicsNumberOfTopTopics() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getTopicsNumberOfTopTopics()).isEqualTo(
                TOPICS_NUMBER_OF_TOP_TOPICS);

        final long phOverridingValue = 5;
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_TOPICS_NUMBER_OF_TOP_TOPICS,
                Long.toString(phOverridingValue),
                /* makeDefault */false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getTopicsNumberOfTopTopics()).isEqualTo(
                phOverridingValue);
    }

    @Test
    public void testGetTopicsNumberOfLookBackEpochs() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getTopicsNumberOfLookBackEpochs()).isEqualTo(
                TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS);

        final long phOverridingValue = 6;
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS,
                Long.toString(phOverridingValue),
                /* makeDefault */false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getTopicsNumberOfLookBackEpochs()).isEqualTo(
                phOverridingValue);
    }

    @Test
    public void testGetMaintenanceJobPeriodMs() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getMaintenanceJobPeriodMs()).isEqualTo(
                MAINTENANCE_JOB_PERIOD_MS);

        final long phOverridingValue = 7;
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MAINTENANCE_JOB_PERIOD_MS,
                Long.toString(phOverridingValue),
                /* makeDefault */false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMaintenanceJobPeriodMs()).isEqualTo(
                phOverridingValue);
    }

    @Test
    public void testGetMaintenanceJobFlexMs() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getMaintenanceJobFlexMs()).isEqualTo(
                MAINTENANCE_JOB_FLEX_MS);

        final long phOverridingValue = 8;
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MAINTENANCE_JOB_FLEX_MS,
                Long.toString(phOverridingValue),
                /* makeDefault */false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMaintenanceJobFlexMs()).isEqualTo(
                phOverridingValue);
    }

    @Test
    public void testGetMeasurementMainReportingJobPeriodMs() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getMeasurementMainReportingJobPeriodMs())
                .isEqualTo(MEASUREMENT_MAIN_REPORTING_JOB_PERIOD_MS);

        final long phOverridingValue = 8;

        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_MAIN_REPORTING_JOB_PERIOD_MS,
                Long.toString(phOverridingValue),
                /* makeDefault */false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementMainReportingJobPeriodMs()).isEqualTo(
                phOverridingValue);
    }

    @Test
    public void testGetMeasurementAppName() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(FlagsFactory.getFlags().getMeasurementAppName())
                .isEqualTo(MEASUREMENT_APP_NAME);

        final String phOverridingValue = "testAppName";

        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_MEASUREMENT_APP_NAME,
                phOverridingValue,
                /* makeDefault */false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMeasurementAppName()).isEqualTo(phOverridingValue);
    }
}
