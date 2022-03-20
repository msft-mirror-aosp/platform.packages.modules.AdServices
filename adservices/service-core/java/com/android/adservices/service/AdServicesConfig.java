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

/**
 * Configs for AdServices.
 * These configs will be backed by PH Flags.
 */
public class AdServicesConfig {
    /**
     * Job Id for idle maintenance job ({@link MaintenanceJobService}).
     */
    public static final int MAINTENANCE_JOB_ID = 1;
    public static long MAINTENANCE_JOB_PERIOD_MS = 86_400_000; // 1 day.
    public static long MAINTENANCE_JOB_FLEX_MS = 3 * 60 * 1000;  // 3 hours.

    /**
     * Returns the max time period (in millis) between each idle maintenance job run.
     */
    public static long getMaintenanceJobPeriodMs() {
        return MAINTENANCE_JOB_PERIOD_MS;
    }

    /**
     * Returns flex for the Epoch computation job in Millisecond.
     */
    public static long getMaintenanceJobFlexMs() {
        return MAINTENANCE_JOB_FLEX_MS;
    }

    /**
     * Job Id for Topics Epoch Computation Job ({@link EpochJobService})
     */
    public static final int TOPICS_EPOCH_JOB_ID = 2;
    public static long TOPICS_EPOCH_JOB_PERIOD_MS = 7 * 86_400_000; // 7 days.
    public static long TOPICS_EPOCH_JOB_FLEX_MS = 5 * 60 * 1000; // 3 hours.

    /**
     * Returns the max time period (in millis) between each epoch computation job run.
     */
    public static long getTopicsEpochJobPeriodMs() {
        return TOPICS_EPOCH_JOB_PERIOD_MS;
    }

    /**
     * Returns flex for the Epoch computation job in Millisecond.
     */
    public static long getTopicsEpochJobFlexMs() {
        return TOPICS_EPOCH_JOB_FLEX_MS;
    }

    /** The percentage that we will return a random topic from the Taxonomy. Default value is 5%. */
    public static int TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC = 5;

    /** Returns the percentage that we will return a random topic from the Taxonomy. */
    public static int getTopicsPercentageForRandomTopic() {
        return TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC;
    }

    /**
     * The number of top Topics for each epoch.
     *
     * With the current explainer, for each epoch, Topics API will select 5 top Topics and 1 random
     * topic.
     * This param here is to make the number of selected top topics configurable.
     */
    public static int TOPICS_NUMBER_OF_TOP_TOPICS = 5;

    /** Returns the number of top topics. */
    public static int getTopicsNumberOfTopTopics() {
        return TOPICS_NUMBER_OF_TOP_TOPICS;
    }

    /**
     * The number of random Topics for each epoch.
     *
     * With the current explainer, for each epoch, Topics API will select 5 top Topics and 1 random
     * topic.
     * This param here is to make the number of random topics configurable.
     */
    public static int TOPICS_NUMBER_OF_RANDOM_TOPICS = 1;

    /** Returns the number of top topics. */
    public static int getTopicsNumberOfRandomTopics() {
        return TOPICS_NUMBER_OF_RANDOM_TOPICS;
    }

    /** How many epochs to look back when deciding if a caller has observed a topic before. */
    public static int TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS = 3;

    /** Returns the percentage that we will return a random topic from the Taxonomy. */
    public static int getTopicsNumberOfLookBackEpochs() {
        return TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS;
    }

    /**
     * Job Id for Measurement Reporting Job ({@link ReportingJobService})
     */
    public static final int MEASUREMENT_REPORTING_JOB_ID = 3;
    public static long MEASUREMENT_REPORTING_JOB_PERIOD_MS = 5 * 60 * 1000; // 5 minutes.

    /**
     * Returns the max time period (in millis) between each reporting maintenance job run.
     */
    public static long getMeasurementReportingJobPeriodMs() {
        return MEASUREMENT_REPORTING_JOB_PERIOD_MS;
    }

    /**
     * Job Id for Measurement Delete Expired Records Job ({@link DeleteExpiredJobService})
     */
    public static final int MEASUREMENT_DELETE_EXPIRED_JOB_ID = 4;
    public static long MEASUREMENT_DELETE_EXPIRED_JOB_PERIOD_MS =
            24L * 60L * 60L * 1000L; // 24 hours.
    public static long MEASUREMENT_DELETE_EXPIRED_WINDOW_MS =
            28L * 24L * 60L * 60L * 1000L; // 28 days.

    /**
     * Returns the max time period (in millis) between each expired-record deletion maintenance job
     * run.
     */
    public static long getMeasurementDeleteExpiredJobPeriodMs() {
        return MEASUREMENT_DELETE_EXPIRED_JOB_PERIOD_MS;
    }
}
