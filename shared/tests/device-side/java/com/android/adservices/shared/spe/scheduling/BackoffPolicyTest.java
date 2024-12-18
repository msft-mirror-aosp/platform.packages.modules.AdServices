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

import com.android.adservices.shared.SharedUnitTestCase;
import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

/** Unit tests for {@link BackoffPolicy}. */
public final class BackoffPolicyTest extends SharedUnitTestCase {
    @Test
    public void testDefaultGetters() {
        BackoffPolicy policy = new BackoffPolicy.Builder().build();

        expect.that(policy.shouldRetryOnExecutionFailure()).isFalse();
        expect.that(policy.shouldRetryOnExecutionStop()).isFalse();
    }

    @Test
    public void testSetters() {
        BackoffPolicy policy =
                new BackoffPolicy.Builder()
                        .setShouldRetryOnExecutionFailure(true)
                        .setShouldRetryOnExecutionStop(true)
                        .build();

        expect.that(policy.shouldRetryOnExecutionFailure()).isTrue();
        expect.that(policy.shouldRetryOnExecutionStop()).isTrue();
    }

    @Test
    public void testEqualsAndHashCode() {
        EqualsTester et = new EqualsTester(expect);
        BackoffPolicy equals1 = new BackoffPolicy.Builder().build();
        BackoffPolicy equals2 = new BackoffPolicy.Builder().build();

        BackoffPolicy different1 =
                new BackoffPolicy.Builder().setShouldRetryOnExecutionFailure(true).build();
        BackoffPolicy different2 =
                new BackoffPolicy.Builder().setShouldRetryOnExecutionStop(true).build();

        et.expectObjectsAreEqual(equals1, equals1);
        et.expectObjectsAreEqual(equals1, equals2);

        et.expectObjectsAreNotEqual(equals1, null);

        et.expectObjectsAreNotEqual(equals1, different1);
        et.expectObjectsAreNotEqual(equals2, different2);
    }

    @Test
    public void testToString() {
        BackoffPolicy policy = new BackoffPolicy.Builder().build();

        expect.that(policy.toString())
                .isEqualTo(
                        "BackoffPolicy{mShouldRetryOnExecutionFailure=false,"
                                + " mShouldRetryOnExecutionStop=false}");
    }
}
