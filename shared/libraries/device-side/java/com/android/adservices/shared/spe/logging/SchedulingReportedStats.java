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

package com.android.adservices.shared.spe.logging;

import com.google.auto.value.AutoValue;

/**
 * Class for BackgroundJobSchedulingReported atom.
 *
 * <p><b>Note: This is a data transfer object (DTO) used for logging. Though it isn't "final", it
 * should NOT be extended any more.</b>
 */
@AutoValue
public abstract class SchedulingReportedStats {

    /** Returns the unique id of a background job. */
    public abstract int getJobId();

    /** Returns the Scheduling result code. */
    public abstract int getResultCode();

    /** Returns the scheduler type that schedules the job. */
    public abstract int getSchedulerType();

    /** Returns module name from which the job execution is being reported. */
    public abstract int getModuleName();

    /** Create an instance for {@link SchedulingReportedStats.Builder}. */
    public static SchedulingReportedStats.Builder builder() {
        return new AutoValue_SchedulingReportedStats.Builder();
    }

    /** Builder class for {@link SchedulingReportedStats}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the unique id of a background job. */
        public abstract Builder setJobId(int value);

        /** Sets the Scheduling result code. */
        public abstract Builder setResultCode(int value);

        /** Sets the scheduler type that schedules the job. */
        public abstract Builder setSchedulerType(int value);

        /** Sets module name from which the job execution is being reported. */
        public abstract Builder setModuleName(int value);

        /** Build an instance of {@link SchedulingReportedStats}. */
        public abstract SchedulingReportedStats build();
    }
}
