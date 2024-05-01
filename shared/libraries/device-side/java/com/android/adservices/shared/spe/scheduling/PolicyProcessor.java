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

import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_CHARGING;
import static com.android.adservices.shared.spe.JobErrorMessage.ERROR_MESSAGE_JOB_PROCESSOR_INVALID_JOB_POLICY_CHARGING_IDLE;
import static com.android.adservices.shared.spe.JobErrorMessage.ERROR_MESSAGE_JOB_PROCESSOR_INVALID_NETWORK_TYPE;
import static com.android.adservices.shared.spe.JobErrorMessage.ERROR_MESSAGE_JOB_PROCESSOR_MISMATCHED_JOB_ID_WHEN_MERGING_JOB_POLICY;

import android.annotation.Nullable;
import android.app.job.JobInfo;
import android.net.Uri;

import com.android.adservices.shared.proto.JobPolicy;
import com.android.adservices.shared.proto.JobPolicy.NetworkType;
import com.android.adservices.shared.proto.JobPolicy.OneOffJobParams;
import com.android.adservices.shared.proto.JobPolicy.PeriodicJobParams;
import com.android.adservices.shared.proto.JobPolicy.TriggerContentJobParams;
import com.android.internal.annotations.VisibleForTesting;

/** A class to process proto-based {@link JobPolicy}. */
public final class PolicyProcessor {
    /**
     * Apply {@link JobPolicy} synced from server to the default {@link JobInfo}. Note {@link
     * JobPolicy} prevails for the same field.
     *
     * @param builder a builder for the default {@link JobInfo}
     * @param jobPolicy the {@link JobPolicy} synced from server
     * @return a merged {@link JobInfo}. {@link JobPolicy} will override the value if a field
     *     presents in both {@code builder} and {@code jobPolicy}.
     */
    public static JobInfo applyPolicyToJobInfo(
            JobInfo.Builder builder, @Nullable JobPolicy jobPolicy) {
        if (jobPolicy == null) {
            return builder.build();
        }

        if (jobPolicy.hasNetworkType()) {
            builder.setRequiredNetworkType(convertNetworkType(jobPolicy.getNetworkType()));
        }

        if (jobPolicy.hasBatteryType()) {
            setBatteryConstraint(builder, jobPolicy);
        }

        if (jobPolicy.hasRequireDeviceIdle()) {
            builder.setRequiresDeviceIdle(jobPolicy.getRequireDeviceIdle());
        }

        if (jobPolicy.hasRequireStorageNotLow()) {
            builder.setRequiresStorageNotLow(jobPolicy.getRequireStorageNotLow());
        }

        if (jobPolicy.hasIsPersisted()) {
            builder.setPersisted(jobPolicy.getIsPersisted());
        }

        if (jobPolicy.hasPeriodicJobParams()) {
            setPeriodicJobParams(builder, jobPolicy.getPeriodicJobParams());
        }

        if (jobPolicy.hasOneOffJobParams()) {
            setOneOffJobParams(builder, jobPolicy.getOneOffJobParams());
        }

        if (jobPolicy.hasTriggerContentJobParams()) {
            setTriggerContentJobParams(builder, jobPolicy.getTriggerContentJobParams());
        }

        return builder.build();
    }

    /**
     * Merges two JobPolicy. The strategy is left-join, i.e. the second JobPolicy overrides the same
     * field if it also presents in the first JobPolicy.
     *
     * @param jobPolicy1 the {@link JobPolicy} to be merged to. (destination)
     * @param jobPolicy2 the {@link JobPolicy} to merge from. (source)
     * @return a merged {@link JobPolicy}
     */
    @Nullable
    public static JobPolicy mergeTwoJobPolicies(JobPolicy jobPolicy1, JobPolicy jobPolicy2) {
        JobPolicy mergedPolicy;
        if (jobPolicy1 == null && jobPolicy2 == null) {
            return null;
        } else if (jobPolicy1 == null) {
            mergedPolicy = jobPolicy2;
        } else if (jobPolicy2 == null) {
            mergedPolicy = jobPolicy1;
        } else {
            // It requires the job ID of two Policies are same.
            if (!jobPolicy1.hasJobId()
                    || !jobPolicy2.hasJobId()
                    || jobPolicy1.getJobId() != jobPolicy2.getJobId()) {
                throw new IllegalArgumentException(
                        ERROR_MESSAGE_JOB_PROCESSOR_MISMATCHED_JOB_ID_WHEN_MERGING_JOB_POLICY);
            }

            // mergeFrom() merges the contents of other into this message, overwriting singular
            // scalar fields, merging composite fields, and concatenating repeated fields.
            mergedPolicy = jobPolicy1.toBuilder().mergeFrom(jobPolicy2).build();
        }

        enforceJobPolicyValidity(mergedPolicy);

        return mergedPolicy;
    }

    // An extra validation for jobPolicy before JobInfo.enforceValidity().
    @VisibleForTesting
    static void enforceJobPolicyValidity(JobPolicy jobPolicy) {
        // Charging cannot be set with Device Idle. See b/221454240 for details.
        if (jobPolicy.hasRequireDeviceIdle()
                && jobPolicy.getRequireDeviceIdle()
                && jobPolicy.hasBatteryType()
                && jobPolicy.getBatteryType() == BATTERY_TYPE_REQUIRE_CHARGING) {
            throw new IllegalArgumentException(
                    ERROR_MESSAGE_JOB_PROCESSOR_INVALID_JOB_POLICY_CHARGING_IDLE);
        }
    }

    // Map network type from Policy's NetworkType to JobInfo.NetworkType.
    @VisibleForTesting
    static int convertNetworkType(NetworkType networkType) {
        switch (networkType) {
            case NETWORK_TYPE_NONE:
                return JobInfo.NETWORK_TYPE_NONE;
            case NETWORK_TYPE_ANY:
                return JobInfo.NETWORK_TYPE_ANY;
            case NETWORK_TYPE_UNMETERED:
                return JobInfo.NETWORK_TYPE_UNMETERED;
            case NETWORK_TYPE_NOT_ROAMING:
                return JobInfo.NETWORK_TYPE_NOT_ROAMING;
            case NETWORK_TYPE_CELLULAR:
                return JobInfo.NETWORK_TYPE_CELLULAR;
            default:
                // The error will be caught in the PolicyJobScheduler#applyPolicyFromServer().
                throw new IllegalArgumentException(
                        String.format(
                                ERROR_MESSAGE_JOB_PROCESSOR_INVALID_NETWORK_TYPE,
                                networkType.getNumber()));
        }
    }

    // Process the battery constraint. Allow one condition to be true and others will be overridden
    // to false.
    //
    // Note: Based on current charging speed, Charging and BatteryNotLow should be mutual excluded.
    // That says, if a job is defined as requiring charging, it should not care if the battery level
    // is low or not. To set both conditions to be true will harm the expected job execution
    // frequency. Therefore, SPE limits to use one condition or none.
    private static void setBatteryConstraint(JobInfo.Builder builder, JobPolicy jobPolicy) {
        switch (jobPolicy.getBatteryType()) {
            case BATTERY_TYPE_REQUIRE_CHARGING:
                builder.setRequiresCharging(true);
                builder.setRequiresBatteryNotLow(false);
                return;
            case BATTERY_TYPE_REQUIRE_NOT_LOW:
                builder.setRequiresBatteryNotLow(true);
                builder.setRequiresCharging(false);
                return;
            case BATTERY_TYPE_REQUIRE_NONE:
            default:
                builder.setRequiresCharging(false);
                builder.setRequiresBatteryNotLow(false);
        }
    }

    private static void setPeriodicJobParams(JobInfo.Builder builder, PeriodicJobParams params) {
        if (!params.hasPeriodicIntervalMs()) {
            return;
        }

        if (params.hasFlexInternalMs()) {
            builder.setPeriodic(params.getPeriodicIntervalMs(), params.getFlexInternalMs());
        } else {
            builder.setPeriodic(params.getPeriodicIntervalMs());
        }
    }

    private static void setOneOffJobParams(JobInfo.Builder builder, OneOffJobParams params) {
        if (params.hasMinimumLatencyMs()) {
            builder.setMinimumLatency(params.getMinimumLatencyMs());
        }

        if (params.hasOverrideDeadlineMs()) {
            builder.setOverrideDeadline(params.getOverrideDeadlineMs());
        }
    }

    private static void setTriggerContentJobParams(
            JobInfo.Builder builder, TriggerContentJobParams params) {
        if (params.hasTriggerContentUriString()) {
            builder.addTriggerContentUri(
                    new JobInfo.TriggerContentUri(
                            Uri.parse(params.getTriggerContentUriString()),
                            // There is only one flag value, and it's a required field to construct
                            // TriggerContentUri. Set it by default.
                            JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS));
        }

        if (params.hasTriggerContentMaxDelayMs()) {
            builder.setTriggerContentMaxDelay(params.getTriggerContentMaxDelayMs());
        }

        if (params.hasTriggerContentUpdateDelayMs()) {
            builder.setTriggerContentUpdateDelay(params.getTriggerContentUpdateDelayMs());
        }
    }
}
