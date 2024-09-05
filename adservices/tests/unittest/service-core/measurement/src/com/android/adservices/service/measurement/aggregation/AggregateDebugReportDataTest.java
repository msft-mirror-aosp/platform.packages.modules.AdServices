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
package com.android.adservices.service.measurement.aggregation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;

/** Unit tests for {@link AggregateDebugReportData} */
@SmallTest
public final class AggregateDebugReportDataTest {

    @Test
    public void equals_pass() {
        assertThat(createExample1AggregateDebugReportData())
                .isEqualTo(createExample1AggregateDebugReportData());
    }

    @Test
    public void equals_fail() {
        assertThat(createExample1AggregateDebugReportData())
                .isNotEqualTo(createExample2AggregateDebugReportData());
    }

    @Test
    public void hashCode_pass() {
        assertThat(createExample1AggregateDebugReportData().hashCode())
                .isEqualTo(createExample1AggregateDebugReportData().hashCode());
    }

    @Test
    public void hashCode_fail() {
        assertThat(createExample1AggregateDebugReportData().hashCode())
                .isNotEqualTo(createExample2AggregateDebugReportData().hashCode());
    }

    @Test
    public void getters() {
        AggregateDebugReportData aggregateDebugReportData =
                createExample1AggregateDebugReportData();
        assertThat(new HashSet<>(Arrays.asList("source-storage-limit")))
                .isEqualTo(aggregateDebugReportData.getReportType());
        assertThat(new BigInteger("3", 16)).isEqualTo(aggregateDebugReportData.getKeyPiece());
        assertThat(aggregateDebugReportData.getValue()).isEqualTo(100);
    }

    @Test
    public void validate() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AggregateDebugReportData.Builder(
                                        /* reportType= */ null,
                                        /* keyPiece= */ null,
                                        /* value= */ 0)
                                .build());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AggregateDebugReportData.Builder(
                                        /* reportType= */ null,
                                        /* keyPiece= */ new BigInteger("3", 16),
                                        /* value= */ 0)
                                .build());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AggregateDebugReportData.Builder(
                                        /* reportType= */ new HashSet<>(
                                                Arrays.asList("source-storage-limit")),
                                        /* keyPiece= */ null,
                                        /* value= */ 0)
                                .build());
    }

    private static AggregateDebugReportData createExample1AggregateDebugReportData() {
        return new AggregateDebugReportData.Builder(
                        /* reportType= */ new HashSet<>(Arrays.asList("source-storage-limit")),
                        /* keyPiece= */ new BigInteger("3", 16),
                        /* value= */ 100)
                .build();
    }

    private static AggregateDebugReportData createExample2AggregateDebugReportData() {
        return new AggregateDebugReportData.Builder(
                        /* reportType= */ new HashSet<>(
                                Arrays.asList("source-storage-limit", "source-noised")),
                        /* keyPiece= */ new BigInteger("3", 16),
                        /* value= */ 100)
                .build();
    }
}
