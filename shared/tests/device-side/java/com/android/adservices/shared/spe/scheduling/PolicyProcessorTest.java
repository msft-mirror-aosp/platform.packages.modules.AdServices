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

import static android.app.job.JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS;

import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_CHARGING;
import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
import static com.android.adservices.shared.proto.JobPolicy.NetworkType.NETWORK_TYPE_NONE;
import static com.android.adservices.shared.spe.JobErrorMessage.ERROR_MESSAGE_JOB_PROCESSOR_INVALID_JOB_POLICY_CHARGING_IDLE;
import static com.android.adservices.shared.spe.JobErrorMessage.ERROR_MESSAGE_JOB_PROCESSOR_MISMATCHED_JOB_ID_WHEN_MERGING_JOB_POLICY;
import static com.android.adservices.shared.spe.framework.TestJobServiceFactory.JOB_ID_1;
import static com.android.adservices.shared.spe.scheduling.PolicyProcessor.applyPolicyToJobInfo;
import static com.android.adservices.shared.spe.scheduling.PolicyProcessor.convertNetworkType;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.job.JobInfo;
import android.app.job.JobInfo.TriggerContentUri;
import android.content.ComponentName;
import android.net.Uri;

import com.android.adservices.shared.SharedUnitTestCase;
import com.android.adservices.shared.proto.JobPolicy;
import com.android.adservices.shared.proto.JobPolicy.BatteryType;
import com.android.adservices.shared.proto.JobPolicy.NetworkType;
import com.android.adservices.shared.proto.JobPolicy.TriggerContentJobParams;
import com.android.adservices.shared.spe.framework.TestJobService;

import org.junit.Test;

/** Unit tests for {@link PolicyProcessor}. */
public final class PolicyProcessorTest extends SharedUnitTestCase {

    @Test
    public void testApplyPolicyToJobInfo_nullPolicy() {
        JobInfo.Builder builder = getBaseJobInfoBuilder();

        assertThat(applyPolicyToJobInfo(builder, /* jobPolicy= */ null))
                .isEqualTo(getBaseJobInfoBuilder().build());
    }

    @Test
    public void testApplyPolicyToJobInfo_network() {
        JobInfo.Builder builder =
                getBaseJobInfoBuilder().setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE);

        NetworkType overridingValue = NetworkType.NETWORK_TYPE_CELLULAR;
        JobPolicy jobPolicy = JobPolicy.newBuilder().setNetworkType(overridingValue).build();
        assertThat(applyPolicyToJobInfo(builder, jobPolicy).getNetworkType())
                .isEqualTo(convertNetworkType(overridingValue));
    }

    @Test
    public void testApplyPolicyToJobInfo_battery_charging() {
        JobInfo.Builder builder = getBaseJobInfoBuilder().setRequiresCharging(true);

        BatteryType overridingValue = BATTERY_TYPE_REQUIRE_CHARGING;
        JobPolicy jobPolicy = JobPolicy.newBuilder().setBatteryType(overridingValue).build();
        assertThat(applyPolicyToJobInfo(builder, jobPolicy).isRequireCharging()).isEqualTo(true);

        overridingValue = BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
        jobPolicy = JobPolicy.newBuilder().setBatteryType(overridingValue).build();
        assertThat(applyPolicyToJobInfo(builder, jobPolicy).isRequireCharging()).isEqualTo(false);

        overridingValue = BatteryType.BATTERY_TYPE_REQUIRE_NONE;
        jobPolicy = JobPolicy.newBuilder().setBatteryType(overridingValue).build();
        assertThat(applyPolicyToJobInfo(builder, jobPolicy).isRequireCharging()).isEqualTo(false);
    }

    @Test
    public void testApplyPolicyToJobInfo_battery_notLow() {
        JobInfo.Builder builder = getBaseJobInfoBuilder().setRequiresBatteryNotLow(true);

        BatteryType overridingValue = BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
        JobPolicy jobPolicy = JobPolicy.newBuilder().setBatteryType(overridingValue).build();
        assertThat(applyPolicyToJobInfo(builder, jobPolicy).isRequireBatteryNotLow())
                .isEqualTo(true);

        overridingValue = BATTERY_TYPE_REQUIRE_CHARGING;
        jobPolicy = JobPolicy.newBuilder().setBatteryType(overridingValue).build();
        assertThat(applyPolicyToJobInfo(builder, jobPolicy).isRequireBatteryNotLow())
                .isEqualTo(false);

        overridingValue = BatteryType.BATTERY_TYPE_REQUIRE_NONE;
        jobPolicy = JobPolicy.newBuilder().setBatteryType(overridingValue).build();
        assertThat(applyPolicyToJobInfo(builder, jobPolicy).isRequireBatteryNotLow())
                .isEqualTo(false);
    }

    @Test
    public void testApplyPolicyToJobInfo_battery_none() {
        JobInfo.Builder builder = getBaseJobInfoBuilder();

        BatteryType overridingValue = BatteryType.BATTERY_TYPE_REQUIRE_NONE;
        JobPolicy jobPolicy = JobPolicy.newBuilder().setBatteryType(overridingValue).build();
        assertThat(applyPolicyToJobInfo(builder, jobPolicy).isRequireCharging()).isEqualTo(false);
        assertThat(applyPolicyToJobInfo(builder, jobPolicy).isRequireBatteryNotLow())
                .isEqualTo(false);

        overridingValue = BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
        jobPolicy = JobPolicy.newBuilder().setBatteryType(overridingValue).build();
        assertThat(applyPolicyToJobInfo(builder, jobPolicy).isRequireCharging()).isEqualTo(false);
        assertThat(applyPolicyToJobInfo(builder, jobPolicy).isRequireBatteryNotLow())
                .isEqualTo(true);

        overridingValue = BATTERY_TYPE_REQUIRE_CHARGING;
        jobPolicy = JobPolicy.newBuilder().setBatteryType(overridingValue).build();
        assertThat(applyPolicyToJobInfo(builder, jobPolicy).isRequireCharging()).isEqualTo(true);
        assertThat(applyPolicyToJobInfo(builder, jobPolicy).isRequireBatteryNotLow())
                .isEqualTo(false);
    }

    @Test
    public void testApplyPolicyToJobInfo_storage() {
        JobInfo.Builder builder = getBaseJobInfoBuilder();

        builder.setRequiresStorageNotLow(true);
        JobPolicy jobPolicy = JobPolicy.newBuilder().setRequireStorageNotLow(false).build();
        assertThat(applyPolicyToJobInfo(builder, jobPolicy).isRequireStorageNotLow())
                .isEqualTo(false);

        builder.setRequiresStorageNotLow(false);
        jobPolicy = JobPolicy.newBuilder().setRequireStorageNotLow(true).build();
        assertThat(applyPolicyToJobInfo(builder, jobPolicy).isRequireStorageNotLow())
                .isEqualTo(true);
    }

    @Test
    public void testApplyPolicyToJobInfo_triggerContentUri() {
        String uriString1 = "content://test.trigger1";
        String uriString2 = "content://test.trigger2";
        TriggerContentUri uri1 =
                new TriggerContentUri(Uri.parse(uriString1), FLAG_NOTIFY_FOR_DESCENDANTS);
        TriggerContentUri uri2 =
                new TriggerContentUri(Uri.parse(uriString2), FLAG_NOTIFY_FOR_DESCENDANTS);

        JobPolicy jobPolicy1 =
                JobPolicy.newBuilder()
                        .setTriggerContentJobParams(
                                TriggerContentJobParams.newBuilder()
                                        .setTriggerContentUriString(uriString1)
                                        .build())
                        .build();
        JobPolicy jobPolicy2 =
                JobPolicy.newBuilder()
                        .setTriggerContentJobParams(
                                TriggerContentJobParams.newBuilder()
                                        .setTriggerContentUriString(uriString2)
                                        .build())
                        .build();

        JobInfo.Builder builder = getBaseJobInfoBuilder();
        JobInfo actualInfo1 = applyPolicyToJobInfo(builder, jobPolicy1);
        expect.that(actualInfo1.getTriggerContentUris()).hasLength(1);
        expect.that(actualInfo1.getTriggerContentUris()[0]).isEqualTo(uri1);

        JobInfo actualInfo2 = applyPolicyToJobInfo(builder, jobPolicy2);
        expect.that(actualInfo2.getTriggerContentUris()).hasLength(2);
        expect.that(actualInfo2.getTriggerContentUris()).asList().containsExactly(uri1, uri2);
    }

    @Test
    public void testApplyPolicyToJobInfo_triggerContentMaxDelay() {
        JobInfo.Builder builder = getBaseJobInfoBuilder().setTriggerContentMaxDelay(100);

        long overridingValue = 200;
        JobPolicy jobPolicy =
                JobPolicy.newBuilder()
                        .setTriggerContentJobParams(
                                TriggerContentJobParams.newBuilder()
                                        .setTriggerContentMaxDelayMs(overridingValue)
                                        .build())
                        .build();
        assertThat(applyPolicyToJobInfo(builder, jobPolicy).getTriggerContentMaxDelay())
                .isEqualTo(overridingValue);
    }

    @Test
    public void testApplyPolicyToJobInfo_triggerContentUpdateDelay() {
        JobInfo.Builder builder = getBaseJobInfoBuilder().setTriggerContentUpdateDelay(100);

        long overridingValue = 200;
        JobPolicy jobPolicy =
                JobPolicy.newBuilder()
                        .setTriggerContentJobParams(
                                TriggerContentJobParams.newBuilder()
                                        .setTriggerContentUpdateDelayMs(overridingValue)
                                        .build())
                        .build();
        assertThat(applyPolicyToJobInfo(builder, jobPolicy).getTriggerContentUpdateDelay())
                .isEqualTo(overridingValue);
    }

    @Test
    public void testApplyPolicyToJobInfo_periodicInterval() {
        JobInfo.Builder builder = getBaseJobInfoBuilder().setPeriodic(1_000_000);

        long overridingValue = 2_000_000;
        JobPolicy jobPolicy =
                JobPolicy.newBuilder()
                        .setPeriodicJobParams(
                                JobPolicy.PeriodicJobParams.newBuilder()
                                        .setPeriodicIntervalMs(overridingValue)
                                        .build())
                        .build();
        expect.that(applyPolicyToJobInfo(builder, jobPolicy).getIntervalMillis())
                .isEqualTo(overridingValue);

        overridingValue = 3_000_000;
        long overridingFlexValue = 1_000_000;
        jobPolicy =
                JobPolicy.newBuilder()
                        .setPeriodicJobParams(
                                JobPolicy.PeriodicJobParams.newBuilder()
                                        .setPeriodicIntervalMs(overridingValue)
                                        .setFlexInternalMs(overridingFlexValue)
                                        .build())
                        .build();

        JobInfo actualInfo = applyPolicyToJobInfo(getBaseJobInfoBuilder(), jobPolicy);
        expect.that(actualInfo.getIntervalMillis()).isEqualTo(overridingValue);
        expect.that(actualInfo.getFlexMillis()).isEqualTo(overridingFlexValue);
    }

    @Test
    public void testApplyPolicyToJobInfo_minimumLatency() {
        JobInfo.Builder builder = getBaseJobInfoBuilder().setMinimumLatency(100);

        long overridingValue = 200;
        JobPolicy jobPolicy =
                JobPolicy.newBuilder()
                        .setOneOffJobParams(
                                JobPolicy.OneOffJobParams.newBuilder()
                                        .setMinimumLatencyMs(overridingValue)
                                        .build())
                        .build();
        assertThat(applyPolicyToJobInfo(builder, jobPolicy).getMinLatencyMillis())
                .isEqualTo(overridingValue);
    }

    @Test
    public void testApplyPolicyToJobInfo_overrideDeadline() {
        JobInfo.Builder builder = getBaseJobInfoBuilder().setOverrideDeadline(100);
        long overridingValue = 200;
        JobPolicy jobPolicy =
                JobPolicy.newBuilder()
                        .setOneOffJobParams(
                                JobPolicy.OneOffJobParams.newBuilder()
                                        .setOverrideDeadlineMs(overridingValue)
                                        .build())
                        .build();
        assertThat(applyPolicyToJobInfo(builder, jobPolicy).getMaxExecutionDelayMillis())
                .isEqualTo(overridingValue);
    }

    @Test
    public void testApplyPolicyToJobInfo_isPersisted() {
        JobInfo.Builder builder = getBaseJobInfoBuilder();

        JobPolicy jobPolicy = JobPolicy.newBuilder().setIsPersisted(true).build();
        assertThat(applyPolicyToJobInfo(builder, jobPolicy).isPersisted()).isEqualTo(true);

        jobPolicy = JobPolicy.newBuilder().setIsPersisted(false).build();
        assertThat(applyPolicyToJobInfo(builder, jobPolicy).isPersisted()).isEqualTo(false);
    }

    @Test
    public void testMergeTwoJobPolicies_onlyExistingInJobPolicy1() {
        int period = 1_000_000;

        JobPolicy jobPolicy1 =
                JobPolicy.newBuilder()
                        .setJobId(JOB_ID_1)
                        .setNetworkType(NETWORK_TYPE_NONE)
                        .setPeriodicJobParams(
                                JobPolicy.PeriodicJobParams.newBuilder()
                                        .setPeriodicIntervalMs(period)
                                        .build())
                        .build();

        JobPolicy jobPolicy2 = JobPolicy.newBuilder().setJobId(JOB_ID_1).build();

        JobPolicy mergedPolicy = PolicyProcessor.mergeTwoJobPolicies(jobPolicy1, jobPolicy2);

        expect.withMessage("jobPolicy 1 setting only")
                .that(mergedPolicy.getBatteryType())
                .isEqualTo(jobPolicy1.getBatteryType());
        expect.withMessage("jobPolicy 1 setting only")
                .that(mergedPolicy.getPeriodicJobParams().getPeriodicIntervalMs())
                .isEqualTo(jobPolicy1.getPeriodicJobParams().getPeriodicIntervalMs());
    }

    @Test
    public void testMergeTwoJobPolicies_onlyExistingInJobPolicy2() {
        int period = 1_000_000;

        JobPolicy jobPolicy1 = JobPolicy.newBuilder().setJobId(JOB_ID_1).build();

        JobPolicy jobPolicy2 =
                JobPolicy.newBuilder()
                        .setJobId(JOB_ID_1)
                        .setRequireStorageNotLow(false) // Existed in both but different
                        .setOneOffJobParams(
                                JobPolicy.OneOffJobParams.newBuilder()
                                        .setOverrideDeadlineMs(period)
                                        .build())
                        .build();

        JobPolicy mergedPolicy = PolicyProcessor.mergeTwoJobPolicies(jobPolicy1, jobPolicy2);

        expect.withMessage("jobPolicy 2 setting only")
                .that(mergedPolicy.getRequireStorageNotLow())
                .isEqualTo(jobPolicy2.getRequireStorageNotLow());
        expect.withMessage("jobPolicy 2 setting only")
                .that(mergedPolicy.getOneOffJobParams().getOverrideDeadlineMs())
                .isEqualTo(jobPolicy2.getOneOffJobParams().getOverrideDeadlineMs());
    }

    @Test
    public void testMergeTwoJobPolicies_existedInBothPolicies() {
        int period1 = 1_000_000;
        int period2 = period1 * 2;

        JobPolicy jobPolicy1 =
                JobPolicy.newBuilder()
                        .setJobId(JOB_ID_1)
                        .setIsPersisted(true)
                        .setBatteryType(BATTERY_TYPE_REQUIRE_CHARGING)
                        .setTriggerContentJobParams(
                                JobPolicy.TriggerContentJobParams.newBuilder()
                                        .setTriggerContentUriString("jobPolicy1")
                                        .setTriggerContentMaxDelayMs(period1)
                                        .build())
                        .build();

        JobPolicy jobPolicy2 =
                JobPolicy.newBuilder()
                        .setJobId(JOB_ID_1)
                        .setIsPersisted(false)
                        .setBatteryType(BATTERY_TYPE_REQUIRE_NOT_LOW)
                        .setTriggerContentJobParams(
                                JobPolicy.TriggerContentJobParams.newBuilder()
                                        .setTriggerContentUriString("jobPolicy2")
                                        .setTriggerContentMaxDelayMs(period2)
                                        .build())
                        .build();

        JobPolicy mergedPolicy = PolicyProcessor.mergeTwoJobPolicies(jobPolicy1, jobPolicy2);

        expect.withMessage("Merged jobPolicy's getIsPersisted()")
                .that(mergedPolicy.getIsPersisted())
                .isEqualTo(jobPolicy2.getIsPersisted());
        expect.withMessage("Merged jobPolicy's getBatteryType()")
                .that(mergedPolicy.getBatteryType())
                .isEqualTo(jobPolicy2.getBatteryType());
        expect.withMessage("Merged jobPolicy's getTriggerContentUriString()")
                .that(mergedPolicy.getTriggerContentJobParams().getTriggerContentUriString())
                .isEqualTo(jobPolicy2.getTriggerContentJobParams().getTriggerContentUriString());
        expect.withMessage("Merged jobPolicy's getTriggerContentMaxDelayMs()")
                .that(mergedPolicy.getTriggerContentJobParams().getTriggerContentMaxDelayMs())
                .isEqualTo(jobPolicy2.getTriggerContentJobParams().getTriggerContentMaxDelayMs());
    }

    @Test
    public void testMergeTwoJobPolicies_enforceValidity() {
        JobPolicy jobPolicy1 =
                JobPolicy.newBuilder()
                        .setJobId(JOB_ID_1)
                        .setRequireDeviceIdle(true)
                        .setBatteryType(BATTERY_TYPE_REQUIRE_CHARGING)
                        .build();

        assertThrows(
                ERROR_MESSAGE_JOB_PROCESSOR_INVALID_JOB_POLICY_CHARGING_IDLE,
                IllegalArgumentException.class,
                () -> PolicyProcessor.mergeTwoJobPolicies(jobPolicy1, /* jobPolicy2= */ null));
    }

    @Test
    public void testMergeTwoJobPolicies_misConfiguredJobId() {
        JobPolicy jobPolicy1 = JobPolicy.newBuilder().setJobId(JOB_ID_1).build();

        JobPolicy jobPolicy2 = JobPolicy.newBuilder().setJobId(JOB_ID_1 * 2).build();

        assertThrows(
                ERROR_MESSAGE_JOB_PROCESSOR_MISMATCHED_JOB_ID_WHEN_MERGING_JOB_POLICY,
                IllegalArgumentException.class,
                () -> PolicyProcessor.mergeTwoJobPolicies(jobPolicy1, jobPolicy2));
    }

    @Test
    public void testEnforceJobPolicyValidity() {
        JobPolicy jobPolicy1 =
                JobPolicy.newBuilder()
                        .setJobId(JOB_ID_1)
                        .setRequireDeviceIdle(true)
                        .setBatteryType(BATTERY_TYPE_REQUIRE_CHARGING)
                        .build();

        assertThrows(
                ERROR_MESSAGE_JOB_PROCESSOR_INVALID_JOB_POLICY_CHARGING_IDLE,
                IllegalArgumentException.class,
                () -> PolicyProcessor.mergeTwoJobPolicies(jobPolicy1, /* jobPolicy2= */ null));
    }

    private JobInfo.Builder getBaseJobInfoBuilder() {
        return new JobInfo.Builder(JOB_ID_1, new ComponentName(sContext, TestJobService.class));
    }
}
