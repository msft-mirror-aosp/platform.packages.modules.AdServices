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
import android.util.Dumpable;

import androidx.annotation.Nullable;

import java.io.PrintWriter;

/**
 * AdServices Feature Flags interface.
 * This Flags interface hold the default values of Ad Services Flags.
 */
public interface Flags extends Dumpable {
    /** Topics Epoch Job Period. */
    long TOPICS_EPOCH_JOB_PERIOD_MS = 7 * 86_400_000; // 7 days.

    /**
     * Returns the max time period (in millis) between each epoch computation job run.
     */
    default long getTopicsEpochJobPeriodMs() {
        return TOPICS_EPOCH_JOB_PERIOD_MS;
    }

    /** Topics Epoch Job Flex. */
    long TOPICS_EPOCH_JOB_FLEX_MS = 5 * 60 * 60 * 1000; // 5 hours.

    /**
     * Returns flex for the Epoch computation job in Millisecond.
     */
    default long getTopicsEpochJobFlexMs() {
        return TOPICS_EPOCH_JOB_FLEX_MS;
    }


    /* The percentage that we will return a random topic from the Taxonomy. */
    int TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC = 5;

    /** Returns the percentage that we will return a random topic from the Taxonomy. */
    default int getTopicsPercentageForRandomTopic() {
        return TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC;
    }

    /** The number of top Topics for each epoch. */
    int TOPICS_NUMBER_OF_TOP_TOPICS = 5;

    /** Returns the number of top topics. */
    default int getTopicsNumberOfTopTopics() {
        return TOPICS_NUMBER_OF_TOP_TOPICS;
    }

    /** The number of random Topics for each epoch. */
    int TOPICS_NUMBER_OF_RANDOM_TOPICS = 1;

    /** Returns the number of top topics. */
    default int getTopicsNumberOfRandomTopics() {
        return TOPICS_NUMBER_OF_RANDOM_TOPICS;
    }

    /** How many epochs to look back when deciding if a caller has observed a topic before. */
    int TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS = 3;

    /** Returns the percentage that we will return a random topic from the Taxonomy. */
    default int getTopicsNumberOfLookBackEpochs() {
        return TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS;
    }

    /* The default period for the Maintenance job. */
    long MAINTENANCE_JOB_PERIOD_MS = 86_400_000; // 1 day.

    /**
     * Returns the max time period (in millis) between each idle maintenance job run.
     */
    default long getMaintenanceJobPeriodMs() {
        return MAINTENANCE_JOB_PERIOD_MS;
    }

    /* The default flex for Maintenaine Job. */
    long MAINTENANCE_JOB_FLEX_MS = 3 * 60 * 60 * 1000;  // 3 hours.

    /**
     * Returns flex for the Daily Maintenance job in Millisecond.
     */
    default long getMaintenanceJobFlexMs() {
        return MAINTENANCE_JOB_FLEX_MS;
    }

    /** Dump some debug info for the flags */
    default void dump(@NonNull PrintWriter writer, @Nullable String[] args) {}
}
