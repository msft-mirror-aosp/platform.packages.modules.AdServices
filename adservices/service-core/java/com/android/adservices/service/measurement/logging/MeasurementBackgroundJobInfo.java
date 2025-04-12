/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.adservices.service.measurement.logging;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

/**
 * Class for AdservicesMeasurementBackgroundJobInfo atom in
 * stats/atoms/adservices/adservices_extension_atoms.proto. It's used by {@link
 * com.android.adservices.service.measurement.logging.MeasurementBackgroundJobLogger}.
 */
@AutoValue
public abstract class MeasurementBackgroundJobInfo {
    /**
     * @return the unique id of a background job. Enums managed by {@link
     *     com.android.adservices.spe.AdServicesJobInfo}.
     */
    public abstract int getJobId();

    /**
     * @return the list of MeasurementBackgroundItemsInfo in the database before being processed by
     *     a background job.
     */
    public abstract ImmutableList<MeasurementBackgroundItemsInfo>
            getDatabaseItemsBeforeProcessing();

    /**
     * @return the list of MeasurementBackgroundItemsInfo processed by a background job. It can be
     *     different from database items before processing because of redirects and retries.
     */
    public abstract ImmutableList<MeasurementBackgroundItemsInfo> getItemsProcessed();

    /**
     * @return Time interval from the start to the end of an execution of a background job. It is on
     *     a millisecond basis.
     */
    public abstract int getJobDurationMs();

    /**
     * @return Type of the result code that implies different execution results.
     */
    public abstract int getExecutionResultCode();

    /**
     * @return The returned public reason onStopJob() was called. This is only applicable when the
     *     state is FINISHED, but may be undefined if JobService.onStopJob() was never called for
     *     the job. The default value is STOP_REASON_UNDEFINED.
     */
    public abstract int getStopReason();

    /** Creates an instance for {@link MeasurementBackgroundJobInfo.Builder}. */
    public static MeasurementBackgroundJobInfo.Builder builder() {
        return new AutoValue_MeasurementBackgroundJobInfo.Builder();
    }

    /** Builder class for {@link MeasurementBackgroundJobInfo} */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Set Job ID. Enums managed by {@link com.android.adservices.spe.AdServicesJobInfo}. */
        public abstract Builder setJobId(int value);

        /**
         * Sets a list of MeasurementBackgroundItemsInfo that represents the items inside the
         * database before being processed by the background job.
         */
        public abstract Builder setDatabaseItemsBeforeProcessing(
                ImmutableList<MeasurementBackgroundItemsInfo> itemsBeforeProcessing);

        /**
         * Sets a list of MeasurementBackgroundItemsInfo that represents the items being processed
         * by the background job. It can be different from database items before processing because
         * of redirects and retries.
         */
        public abstract Builder setItemsProcessed(
                ImmutableList<MeasurementBackgroundItemsInfo> itemsProcessed);

        /** Sets job execution duration in millisecond. */
        public abstract Builder setJobDurationMs(int value);

        /** Sets job execution result code. */
        public abstract Builder setExecutionResultCode(int value);

        /** Sets job public stop reason. */
        public abstract Builder setStopReason(int value);

        /** Builds an instance of {@link MeasurementBackgroundJobInfo}. */
        public abstract MeasurementBackgroundJobInfo build();
    }
}
