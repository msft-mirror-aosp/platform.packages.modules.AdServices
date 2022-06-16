/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.measurement.aggregation;

import static org.junit.Assert.assertEquals;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.math.BigInteger;

/** Unit tests for {@link AggregateHistogramContribution} */
@SmallTest
public final class AggregateHistogramContributionTest {

    private AggregateHistogramContribution createExample() {
        return new AggregateHistogramContribution.Builder()
                .setKey(BigInteger.valueOf(100L))
                .setValue(1).build();
    }

    @Test
    public void testCreation() throws Exception {
        AggregateHistogramContribution contribution = createExample();
        assertEquals(100L, contribution.getKey().longValue());
        assertEquals(1, contribution.getValue());
    }

    @Test
    public void testDefaults() throws Exception {
        AggregateHistogramContribution contribution =
                new AggregateHistogramContribution.Builder().build();
        assertEquals(0L, contribution.getKey().longValue());
        assertEquals(0, contribution.getValue());
    }
}
