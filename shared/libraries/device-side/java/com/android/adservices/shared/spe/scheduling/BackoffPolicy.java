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

import java.util.Objects;

// TODO(b/324247018): Support a more comprehensive Backoff Policy for SPE.
/** The backoff policy if a job execution is failed. */
public final class BackoffPolicy {
    public static final BackoffPolicy DEFAULT = new BackoffPolicy.Builder().build();

    private final boolean mShouldRetryOnExecutionFailure;
    private final boolean mShouldRetryOnExecutionStop;

    private BackoffPolicy(
            boolean shouldRetryOnExecutionFailure, boolean shouldRetryOnExecutionStop) {
        mShouldRetryOnExecutionFailure = shouldRetryOnExecutionFailure;
        mShouldRetryOnExecutionStop = shouldRetryOnExecutionStop;
    }

    /**
     * Indicates if the execution needs to be retried if it encounters errors happening during the
     * execution of a job.
     *
     * @return whether to retry the execution.
     */
    public boolean shouldRetryOnExecutionFailure() {
        return mShouldRetryOnExecutionFailure;
    }

    /**
     * Indicates if the execution needs to be retried if it's stopped by {@link
     * android.app.job.JobScheduler} due to reasons like device issues, constraint not met, etc.
     *
     * @return whether to retry the execution.
     */
    public boolean shouldRetryOnExecutionStop() {
        return mShouldRetryOnExecutionStop;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mShouldRetryOnExecutionFailure, mShouldRetryOnExecutionStop);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BackoffPolicy that)) {
            return false;
        }
        return mShouldRetryOnExecutionFailure == that.mShouldRetryOnExecutionFailure
                && mShouldRetryOnExecutionStop == that.mShouldRetryOnExecutionStop;
    }

    @Override
    public String toString() {
        return "BackoffPolicy{"
                + "mShouldRetryOnExecutionFailure="
                + mShouldRetryOnExecutionFailure
                + ", mShouldRetryOnExecutionStop="
                + mShouldRetryOnExecutionStop
                + '}';
    }

    /** Builder class for {@link BackoffPolicy}. */
    public static final class Builder {
        // By default, the job should not retry.
        private boolean mShouldRetryOnExecutionFailure;
        private boolean mShouldRetryOnExecutionStop;

        /** Setter for {@link #shouldRetryOnExecutionFailure()} */
        public Builder setShouldRetryOnExecutionFailure(boolean value) {
            mShouldRetryOnExecutionFailure = value;
            return this;
        }

        /** Setter for {@link #shouldRetryOnExecutionStop()} */
        public Builder setShouldRetryOnExecutionStop(boolean value) {
            mShouldRetryOnExecutionStop = value;
            return this;
        }

        /** Build an instance of {@link BackoffPolicy}. */
        public BackoffPolicy build() {
            return new BackoffPolicy(mShouldRetryOnExecutionFailure, mShouldRetryOnExecutionStop);
        }
    }
}
