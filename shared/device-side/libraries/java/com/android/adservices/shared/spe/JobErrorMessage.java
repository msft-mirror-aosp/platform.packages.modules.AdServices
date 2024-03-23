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

package com.android.adservices.shared.spe;

/** Class to store the error message for SPE (Scheduling Policy Engine). */
public final class JobErrorMessage {
    private JobErrorMessage() {
        throw new AssertionError(
                "The class only contains static method and should be noninstantiability.");
    }

    /** The error message to throw when job id is configured incorrectly */
    public static final String
            ERROR_MESSAGE_JOB_PROCESSOR_MISMATCHED_JOB_ID_WHEN_MERGING_JOB_POLICY =
                    "Two JobPolicy to be merged should both configure job_id field and have a same"
                            + " job_id!";

    /** The error message to throw when job policy is invalid. */
    public static final String ERROR_MESSAGE_JOB_PROCESSOR_INVALID_JOB_POLICY_CHARGING_IDLE =
            "Invalid JobPolicy: Charging cannot be configured with DeviceIdle! See b/221454240.";

    /** The error message to throw when network type is invalid. */
    public static final String ERROR_MESSAGE_JOB_PROCESSOR_INVALID_NETWORK_TYPE =
            "Invalid Network Type: %d";

    /** The error message to throw when created {@link android.app.job.JobInfo} is invalid. */
    public static final String ERROR_MESSAGE_POLICY_JOB_SCHEDULER_INVALID_JOB_INFO =
            "Invalid Job Constraints configuration for Job %s!";
}
