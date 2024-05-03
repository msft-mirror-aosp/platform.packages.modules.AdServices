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

import static org.junit.Assert.assertThrows;

import android.os.PersistableBundle;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.proto.JobPolicy;
import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

/** Unit tests for {@link JobSpec}. */
public final class JobSpecTest extends AdServicesUnitTestCase {
    private static final int JOB_ID = 1;
    private static final JobPolicy sJobPolicy = JobPolicy.newBuilder().setJobId(JOB_ID).build();

    @Test
    public void testGetters() {
        JobSpec jobSpec = new JobSpec.Builder(sJobPolicy).build();

        expect.that(jobSpec.getJobPolicy()).isEqualTo(sJobPolicy);
        expect.that(jobSpec.getBackoffPolicy()).isEqualTo(new BackoffPolicy.Builder().build());
        expect.that(jobSpec.getExtras()).isNull();
        expect.that(jobSpec.getShouldForceSchedule()).isFalse();
    }

    @Test
    public void testGetters_nullCheck_jobPolicy() {
        assertThrows(
                NullPointerException.class,
                () -> new JobSpec.Builder(/* jobPolicy= */ null).build());
    }

    @Test
    public void testGetters_noJobIdInJobPolicy() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JobSpec.Builder(JobPolicy.getDefaultInstance()).build());
    }

    @Test
    public void testSetters() {
        BackoffPolicy backoffPolicy =
                new BackoffPolicy.Builder().setShouldRetryOnExecutionFailure(true).build();
        PersistableBundle extras = new PersistableBundle();
        String key = "testKey";
        boolean value = true;
        extras.putBoolean(key, value);
        boolean shouldForceSchedule = true;

        JobSpec jobSpec =
                new JobSpec.Builder(sJobPolicy)
                        .setBackoffPolicy(backoffPolicy)
                        .setExtras(extras)
                        .setShouldForceSchedule(shouldForceSchedule)
                        .build();

        expect.that(jobSpec.getJobPolicy()).isEqualTo(sJobPolicy);
        expect.that(jobSpec.getBackoffPolicy()).isEqualTo(backoffPolicy);
        // PersistableBundle doesn't override equals().
        expect.that(jobSpec.getExtras()).isNotNull();
        expect.that(jobSpec.getExtras().getBoolean(key)).isEqualTo(value);
        expect.that(jobSpec.getShouldForceSchedule()).isEqualTo(shouldForceSchedule);
    }

    @Test
    public void testEqualsAndHashCode() {
        EqualsTester et = new EqualsTester(expect);
        int jobId1 = 1;
        int jobId2 = 2;
        JobPolicy jobPolicy1 = JobPolicy.newBuilder().setJobId(jobId1).build();
        JobPolicy jobPolicy2 = JobPolicy.newBuilder().setJobId(jobId2).build();
        BackoffPolicy backoffPolicy1 = new BackoffPolicy.Builder().build();
        BackoffPolicy backoffPolicy2 =
                new BackoffPolicy.Builder()
                        .setShouldRetryOnExecutionFailure(
                                !backoffPolicy1.shouldRetryOnExecutionFailure())
                        .build();
        boolean shouldForceSchedule = true;
        PersistableBundle extras = new PersistableBundle();
        extras.putString("testKey", "testVal");

        JobSpec equals1 =
                new JobSpec.Builder(jobPolicy1)
                        .setBackoffPolicy(backoffPolicy1)
                        .setShouldForceSchedule(shouldForceSchedule)
                        .build();
        JobSpec equals2 =
                new JobSpec.Builder(jobPolicy1)
                        .setBackoffPolicy(backoffPolicy1)
                        .setShouldForceSchedule(shouldForceSchedule)
                        .build();
        JobSpec equals3 =
                new JobSpec.Builder(jobPolicy1)
                        .setBackoffPolicy(backoffPolicy1)
                        .setShouldForceSchedule(shouldForceSchedule)
                        .setExtras(extras)
                        .build();

        JobSpec differentInJobPolicy =
                new JobSpec.Builder(jobPolicy2)
                        .setBackoffPolicy(backoffPolicy1)
                        .setShouldForceSchedule(shouldForceSchedule)
                        .build();
        JobSpec differentInBackoffPolicy =
                new JobSpec.Builder(jobPolicy1)
                        .setBackoffPolicy(backoffPolicy2)
                        .setShouldForceSchedule(shouldForceSchedule)
                        .build();
        JobSpec differentInShouldForceSchedule =
                new JobSpec.Builder(jobPolicy1)
                        .setBackoffPolicy(backoffPolicy1)
                        .setShouldForceSchedule(!shouldForceSchedule)
                        .build();

        et.expectObjectsAreEqual(equals1, equals1);
        et.expectObjectsAreEqual(equals1, equals2);
        et.expectObjectsAreEqual(equals1, equals3);

        et.expectObjectsAreNotEqual(equals1, null);

        et.expectObjectsAreNotEqual(equals1, differentInJobPolicy);
        et.expectObjectsAreNotEqual(equals1, differentInBackoffPolicy);
        et.expectObjectsAreNotEqual(equals1, differentInShouldForceSchedule);
    }

    @Test
    public void testToString() {
        JobSpec jobSpec = new JobSpec.Builder(sJobPolicy).build();

        expect.that(jobSpec.toString())
                .isEqualTo(
                        "JobSpec{mBackoffPolicy=BackoffPolicy{mShouldRetryOnExecutionFailure=false,"
                                + " mShouldRetryOnExecutionStop=false}, mExtras=null,"
                                + " mShouldForceSchedule=false}");
    }
}
