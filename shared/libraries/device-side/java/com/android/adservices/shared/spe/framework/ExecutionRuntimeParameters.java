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

package com.android.adservices.shared.spe.framework;

import android.annotation.Nullable;
import android.app.job.JobParameters;
import android.os.PersistableBundle;

/**
 * A wrapper of {@link android.app.job.JobParameters}. It's used to store the parameters when a job
 * execution is invoked.
 *
 * <p>Currently, the only usage is to get the extras configured in the job scheduling. If you need
 * extra parameters, please reach out to Infra team.
 */
public final class ExecutionRuntimeParameters {
    @Nullable private final PersistableBundle mExtras;

    private ExecutionRuntimeParameters(@Nullable PersistableBundle extras) {
        mExtras = extras;
    }

    /** Returns the extras configured in job scheduling. */
    @Nullable
    public PersistableBundle getExtras() {
        return mExtras;
    }

    /**
     * Converts a {@link JobParameters} to {@link ExecutionRuntimeParameters}.
     *
     * @param jobParameters the {@link JobParameters} to be converted
     * @return a {@link ExecutionRuntimeParameters}.
     */
    public static ExecutionRuntimeParameters convertJobParameters(
            @Nullable JobParameters jobParameters) {
        ExecutionRuntimeParameters.Builder builder = new ExecutionRuntimeParameters.Builder();
        if (jobParameters == null) {
            return builder.build();
        }

        return builder.setExtras(jobParameters.getExtras()).build();
    }

    @Override
    public String toString() {
        return "ExecutionRuntimeParameters{" + "mExtras=" + mExtras + '}';
    }

    /** Builder class for {@link ExecutionRuntimeParameters}. */
    public static final class Builder {
        @Nullable private PersistableBundle mExtras;

        /** Setter of {@link #getExtras()}. */
        public Builder setExtras(@Nullable PersistableBundle value) {
            mExtras = value;
            return this;
        }

        /** Build an instance of {@link ExecutionRuntimeParameters}. */
        public ExecutionRuntimeParameters build() {
            return new ExecutionRuntimeParameters(mExtras);
        }
    }
}
