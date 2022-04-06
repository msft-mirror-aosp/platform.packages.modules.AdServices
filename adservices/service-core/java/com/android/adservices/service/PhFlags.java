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

import android.annotation.NonNull;
import android.os.SystemProperties;
import android.provider.DeviceConfig;

import com.android.internal.annotations.VisibleForTesting;

/** Flags Implementation that delegates to Phenotype. */
// TODO(b/228037065): Add validation logics for Feature flags read from PH.
public final class PhFlags implements Flags {
    /*
     * Keys for ALL the flags stored in DeviceConfig.
     */
    static final String KEY_MAINTENANCE_JOB_PERIOD_MS = "maintenance_job_period_ms";
    static final String KEY_MAINTENANCE_JOB_FLEX_MS = "maintenance_job_flex_ms";
    static final String KEY_TOPICS_EPOCH_JOB_PERIOD_MS = "topics_epoch_job_period_ms";
    static final String KEY_TOPICS_EPOCH_JOB_FLEX_MS = "topics_epoch_job_flex_ms";
    static final String KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC =
            "topics_percentage_for_random_topics";
    static final String KEY_TOPICS_NUMBER_OF_TOP_TOPICS = "topics_number_of_top_topics";
    static final String KEY_TOPICS_NUMBER_OF_RANDOM_TOPICS =
            "topics_number_of_random_topics";
    static final String KEY_TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS =
            "topics_number_of_lookback_epochs";

    // SystemProperty prefix. We can use SystemProperty to override the AdService Configs.
    private static final String SYSTEM_PROPERTY_PREFIX = "debug.adservices.";

    private static final PhFlags sSingleton = new PhFlags();

    /** Returns the singleton instance of the PhFlags. */
    @NonNull
    public static PhFlags getInstance() {
        return sSingleton;
    }

    @Override
    public long getTopicsEpochJobPeriodMs() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return SystemProperties.getLong(getSystemPropertyName(KEY_TOPICS_EPOCH_JOB_PERIOD_MS),
                /* defaultValue =*/ DeviceConfig.getLong(DeviceConfig.NAMESPACE_ADSERVICES,
                        /* flagName = */  KEY_TOPICS_EPOCH_JOB_PERIOD_MS,
                        /* defaultValue = */ TOPICS_EPOCH_JOB_PERIOD_MS));
    }

    @Override
    public long getTopicsEpochJobFlexMs() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return SystemProperties.getLong(getSystemPropertyName(KEY_TOPICS_EPOCH_JOB_FLEX_MS),
                /* defaultValue = */ DeviceConfig.getLong(DeviceConfig.NAMESPACE_ADSERVICES,
                        /* flagName = */  KEY_TOPICS_EPOCH_JOB_FLEX_MS,
                        /* defaultValue = */ TOPICS_EPOCH_JOB_FLEX_MS));
    }

    @Override
    public int getTopicsPercentageForRandomTopic() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName = */  KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC,
                /* defaultValue = */ TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC);
    }

    @Override
    public int getTopicsNumberOfTopTopics() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName = */  KEY_TOPICS_NUMBER_OF_TOP_TOPICS,
                /* defaultValue = */ TOPICS_NUMBER_OF_TOP_TOPICS);
    }

    @Override
    public int getTopicsNumberOfRandomTopics() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName = */  KEY_TOPICS_NUMBER_OF_RANDOM_TOPICS,
                /* defaultValue = */ TOPICS_NUMBER_OF_RANDOM_TOPICS);
    }

    @Override
    public int getTopicsNumberOfLookBackEpochs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName = */  KEY_TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS,
                /* defaultValue = */ TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS);
    }

    @Override
    public long getMaintenanceJobPeriodMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName = */  KEY_MAINTENANCE_JOB_PERIOD_MS,
                /* defaultValue = */ MAINTENANCE_JOB_PERIOD_MS);
    }

    @Override
    public long getMaintenanceJobFlexMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName = */  KEY_MAINTENANCE_JOB_FLEX_MS,
                /* defaultValue = */ MAINTENANCE_JOB_FLEX_MS);
    }

    @VisibleForTesting
    static String getSystemPropertyName(String key) {
        return SYSTEM_PROPERTY_PREFIX + key;
    }
}
