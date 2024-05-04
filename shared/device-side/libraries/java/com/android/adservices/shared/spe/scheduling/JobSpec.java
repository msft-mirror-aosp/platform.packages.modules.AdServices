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

package com.android.adservices.shared.spe.scheduling;

import android.annotation.Nullable;
import android.app.job.JobInfo;
import android.os.PersistableBundle;

import com.android.adservices.shared.proto.JobPolicy;

import java.util.Objects;

/**
 * This class stores the specifications used to schedule a job using {@link PolicyJobScheduler},
 * which is a part of SPE (Scheduling Policy Engine) framework.
 *
 * <p>An instance of {@link JobSpec} needed to be passed into {@link PolicyJobScheduler} with a job
 * ID and {@link JobPolicy} at least. And by default, a {@link JobSpec} is created with a default
 * {@link BackoffPolicy}.
 */
public final class JobSpec {
    private final JobPolicy mJobPolicy;
    private final BackoffPolicy mBackoffPolicy;
    private final PersistableBundle mExtras;
    private final boolean mShouldForceSchedule;

    private JobSpec(
            JobPolicy jobPolicy,
            @Nullable BackoffPolicy backoffPolicy,
            @Nullable PersistableBundle extras,
            boolean shouldForceSchedule) {
        mJobPolicy = Objects.requireNonNull(jobPolicy);
        mBackoffPolicy = backoffPolicy == null ? BackoffPolicy.DEFAULT : backoffPolicy;
        mExtras = extras;
        mShouldForceSchedule = shouldForceSchedule;
    }

    /** Returns a {@link JobPolicy} used for the default constraints to schedule a job. */
    public JobPolicy getJobPolicy() {
        return mJobPolicy;
    }

    /**
     * Returns a {@link BackoffPolicy} to schedule a job. See {@link JobInfo#getBackoffPolicy()} for
     * more details.
     */
    public BackoffPolicy getBackoffPolicy() {
        return mBackoffPolicy;
    }

    /**
     * Returns a {@link PersistableBundle} to contain any data that's used for communications
     * between job scheduling and execution. See {@link JobInfo#getExtras()} for more details.
     */
    @Nullable
    public PersistableBundle getExtras() {
        return mExtras;
    }

    /**
     * Returns a {@link Boolean} to indicate if the job will be scheduled even with a same {@link
     * JobInfo}.
     */
    public boolean getShouldForceSchedule() {
        return mShouldForceSchedule;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JobSpec that)) {
            return false;
        }

        // There is no public method to compare to PersistableBundle. Ignore the difference on it
        // for the reasons 1) only a few jobs will use this extra field. 2) this equals() method for
        // JobSpec is not used in Production.
        return mJobPolicy.equals(that.mJobPolicy)
                && mBackoffPolicy.equals(that.mBackoffPolicy)
                && mShouldForceSchedule == that.mShouldForceSchedule;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mJobPolicy, mBackoffPolicy, mShouldForceSchedule);
    }

    @Override
    public String toString() {
        return "JobSpec{"
                + "mBackoffPolicy="
                + mBackoffPolicy
                + ", mExtras="
                + mExtras
                + ", mShouldForceSchedule="
                + mShouldForceSchedule
                + '}';
    }

    /** Builder class for {@link JobSpec}. */
    public static final class Builder {
        private final JobPolicy mJobPolicy;
        @Nullable private BackoffPolicy mBackoffPolicy;
        @Nullable private PersistableBundle mExtras;
        // By default, the job should not force to schedule with same info.
        private boolean mShouldForceSchedule;

        /**
         * Constructor.
         *
         * @param jobPolicy the {@link JobPolicy} of the job to schedule.
         * @throws IllegalArgumentException if the {@code jobPolicy} doesn't configure a job ID.
         */
        public Builder(JobPolicy jobPolicy) {
            mJobPolicy = Objects.requireNonNull(jobPolicy);

            if (!mJobPolicy.hasJobId()) {
                throw new IllegalArgumentException("JobPolicy must configure the job ID!");
            }
        }

        /**
         * Setter for {@link #getBackoffPolicy()}.
         *
         * <p>By default, {@link #mBackoffPolicy} uses {@link BackoffPolicy#DEFAULT} if it's not
         * set.
         */
        public Builder setBackoffPolicy(@Nullable BackoffPolicy value) {
            mBackoffPolicy = value;
            return this;
        }

        /** Setter for {@link #getExtras()}. */
        public Builder setExtras(@Nullable PersistableBundle value) {
            mExtras = value;
            return this;
        }

        /** Setter for {@link #getShouldForceSchedule()}. */
        public Builder setShouldForceSchedule(boolean value) {
            mShouldForceSchedule = value;
            return this;
        }

        /** Build an instance of {@link JobSpec}. */
        public JobSpec build() {
            return new JobSpec(mJobPolicy, mBackoffPolicy, mExtras, mShouldForceSchedule);
        }
    }
}
