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

import android.content.Context;

/**
 * Enums used in {@link JobWorker#getExecutionFuture(Context, ExecutionRuntimeParameters)} to set
 * the result dynamically based on the execution future, if needed.
 *
 * <p>In most of the time, it should set {@link #SUCCESS} in {@link
 * JobWorker#getExecutionFuture(Context, ExecutionRuntimeParameters)}.
 */
public enum ExecutionResult {
    /**
     * Indicates the execution finishes without issue.
     *
     * <p>This value should be used for most of the jobs, usually not computing the result based on
     * the execution future. That says, if you don't know which value to use, use this one.
     */
    SUCCESS,

    /** Indicates the execution is failed and doesn't need to retry the execution again. */
    FAILURE_WITHOUT_RETRY,

    /** Indicates the execution is failed and needs to retry the execution again. */
    FAILURE_WITH_RETRY,

    /**
     * Indicates the execution future is cancelled by the platform scheduler for various of reasons.
     * For example, user cancellation action, constraint not met, etc.
     *
     * <p><b>Note: This value is ONLY used by SPE (Scheduling Policy Engine) framework and you
     * should NEVER set this value for your job.</b>
     */
    CANCELLED_BY_SCHEDULER
}
