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

import static com.android.internal.annotations.VisibleForTesting.Visibility.PACKAGE;

import android.annotation.IntDef;
import android.content.Context;

import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Enums used in {@link JobWorker#getExecutionFuture(Context, ExecutionRuntimeParameters)} to set
 * the result dynamically based on the execution future, if needed.
 *
 * <p>In most of the time, it should set {@link #SUCCESS} in {@link
 * JobWorker#getExecutionFuture(Context, ExecutionRuntimeParameters)}.
 */
public final class ExecutionResult {
    @IntDef(
            value = {
                RESULT_CODE_SUCCESS,
                RESULT_CODE_FAILURE_WITHOUT_RETRY,
                RESULT_CODE_FAILURE_WITH_RETRY,
                RESULT_CODE_CANCELLED_BY_SCHEDULER
            })
    @Retention(RetentionPolicy.SOURCE)
    private @interface ExecutionResultCode {}

    // Indicates the execution finishes without issue.
    private static final int RESULT_CODE_SUCCESS = 0;

    // Indicates the execution is failed and doesn't need to do the execution again.
    private static final int RESULT_CODE_FAILURE_WITHOUT_RETRY = 1;

    // Indicates the execution is failed and needs to do the execution again.
    private static final int RESULT_CODE_FAILURE_WITH_RETRY = 2;

    // Indicates the execution future is cancelled by the platform scheduler for various of reasons.
    // For example, user cancellation action, constraint not met, etc.
    private static final int RESULT_CODE_CANCELLED_BY_SCHEDULER = 3;

    /**
     * A {@link ExecutionResult} instance that indicates the execution finishes successfully.
     *
     * <p>This value should be used for most of the jobs, usually not computing the result based on
     * the execution future. That says, if you don't know which value to use, use this one.
     */
    public static final ExecutionResult SUCCESS = new ExecutionResult(RESULT_CODE_SUCCESS);

    /**
     * A {@link ExecutionResult} instance that indicates the execution is failed and doesn't need to
     * do the execution again.
     */
    public static final ExecutionResult FAILURE_WITHOUT_RETRY =
            new ExecutionResult(RESULT_CODE_FAILURE_WITHOUT_RETRY);

    /**
     * A {@link ExecutionResult} instance that indicates the execution is failed and doesn't need to
     * do the execution again.
     */
    public static final ExecutionResult FAILURE_WITH_RETRY =
            new ExecutionResult(RESULT_CODE_FAILURE_WITH_RETRY);

    // A ExecutionResult that indicates the execution future is cancelled by the platform scheduler
    // for various of reasons. For example, user cancellation action, constraint not met, etc.
    //
    // Note: This value is ONLY used by SPE (Scheduling Policy Engine) framework, and you
    // should NEVER set this value for your job.
    @VisibleForTesting(visibility = PACKAGE)
    public static final ExecutionResult CANCELLED_BY_SCHEDULER =
            new ExecutionResult(RESULT_CODE_CANCELLED_BY_SCHEDULER);

    @ExecutionResultCode private final int mExecutionResultCode;

    private ExecutionResult(@ExecutionResultCode int resultCode) {
        mExecutionResultCode = resultCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExecutionResult that)) return false;
        return mExecutionResultCode == that.mExecutionResultCode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mExecutionResultCode);
    }

    @Override
    public String toString() {
        return switch (mExecutionResultCode) {
            case RESULT_CODE_SUCCESS -> "SUCCESS";
            case RESULT_CODE_FAILURE_WITH_RETRY -> "FAILURE_WITH_RETRY";
            case RESULT_CODE_FAILURE_WITHOUT_RETRY -> "FAILURE_WITHOUT_RETRY";
            case RESULT_CODE_CANCELLED_BY_SCHEDULER -> "CANCELLED_BY_SCHEDULER";
            default -> "Invalid Result Code: " + mExecutionResultCode;
        };
    }
}
