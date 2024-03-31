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

package com.android.adservices.service.stats;

import com.google.auto.value.AutoValue;

/** Class for logging Ad counter histogram updater per DB insertion. */
@AutoValue
public abstract class AdCounterHistogramUpdaterReportedStats {
    /** Returns latency when calling Ad counter histogram updater. */
    public abstract int getLatencyInMillis();

    /** Returns the status response code in AdServices. */
    public abstract int getStatusCode();

    /**
     * Returns the total number of Ad events in database after Ad counter histogram updater process.
     */
    public abstract int getTotalNumberOfEventsInDatabaseAfterInsert();

    /**
     * Returns the number of histogram events were inserted in database during Ad counter histogram
     * updater process.
     */
    public abstract int getNumberOfInsertedEvent();

    /**
     * Returns the number of histogram events were evicted from database during Ad counter histogram
     * updater process.
     */
    public abstract int getNumberOfEvictedEvent();

    /**
     * @return generic builder
     */
    public static Builder builder() {
        return new AutoValue_AdCounterHistogramUpdaterReportedStats.Builder();
    }

    /** Builder class for AdCounterHistogramUpdaterReportedStats. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Returns latency when calling Ad counter histogram updater. */
        public abstract Builder setLatencyInMillis(int value);

        /** Returns the status response code in AdServices. */
        public abstract Builder setStatusCode(int value);

        /**
         * Sets the total number of Ad events in database after Ad counter histogram updater
         * process.
         */
        public abstract Builder setTotalNumberOfEventsInDatabaseAfterInsert(int value);

        /**
         * Sets the number of histogram events were inserted in database during Ad counter histogram
         * updater process.
         */
        public abstract Builder setNumberOfInsertedEvent(int value);

        /**
         * Sets the number of histogram events were evicted from database during Ad counter
         * histogram updater process.
         */
        public abstract Builder setNumberOfEvictedEvent(int value);

        /** Returns an instance of {@link AdCounterHistogramUpdaterReportedStats} */
        public abstract AdCounterHistogramUpdaterReportedStats build();
    }
}
