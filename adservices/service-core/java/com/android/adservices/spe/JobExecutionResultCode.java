/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.spe;

/** Enum class that defines various result of a background job's execution */
public enum JobExecutionResultCode {
    // Successful execution
    SUCCESSFUL(0),

    // Failed execution with retrying the job.
    FAILED_WITH_RETRY(1),

    // Failed execution without retrying the job.
    FAILED_WITHOUT_RETRY(2),

    // OnJobStop() is invoked with retrying the job.
    ONSTOP_CALLED_WITH_RETRY(3),

    // OnJobStop() is invoked without retrying the job.
    ONSTOP_CALLED_WITHOUT_RETRY(4),

    // The execution is halted by system or device, leaving a not finished execution.
    HALTED_FOR_UNKNOWN_REASON(5);

    // TODO(b/278790270): Add more result codes for Background job Logging.

    private final int mResultCode;

    /**
     * Get the integer result code from an enum.
     *
     * @return the integer result code
     */
    public int getResultCode() {
        return mResultCode;
    }

    JobExecutionResultCode(int resultCode) {
        this.mResultCode = resultCode;
    }
}
