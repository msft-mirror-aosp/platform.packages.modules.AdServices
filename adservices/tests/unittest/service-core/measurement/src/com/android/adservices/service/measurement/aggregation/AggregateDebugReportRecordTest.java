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

import android.net.Uri;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/** Unit tests for {@link AggregateDebugReportRecord} */
@SmallTest
public final class AggregateDebugReportRecordTest {

    @Test
    public void equals_pass() {
        assertThat(createExample1AggregateDebugReportRecord())
                .isEqualTo(createExample1AggregateDebugReportRecord());
    }

    @Test
    public void equals_fail() {
        assertThat(createExample1AggregateDebugReportRecord())
                .isNotEqualTo(createExample2AggregateDebugReportRecord());
    }

    @Test
    public void hashCode_pass() {
        assertThat(createExample1AggregateDebugReportRecord().hashCode())
                .isEqualTo(createExample1AggregateDebugReportRecord().hashCode());
    }

    @Test
    public void hashCode_fail() {
        assertThat(createExample1AggregateDebugReportRecord().hashCode())
                .isNotEqualTo(createExample2AggregateDebugReportRecord().hashCode());
    }

    @Test
    public void getters() {
        AggregateDebugReportRecord aggregateDebugReportRecord =
                createExample1AggregateDebugReportRecord();
        assertThat(aggregateDebugReportRecord.getReportGenerationTime()).isEqualTo(1000L);
        assertThat(Uri.parse("android-app://com.example1.sample"))
                .isEqualTo(aggregateDebugReportRecord.getTopLevelRegistrant());
        assertThat(Uri.parse("com.test1.myapp"))
                .isEqualTo(aggregateDebugReportRecord.getRegistrantApp());
        assertThat(Uri.parse("https://destination.test"))
                .isEqualTo(aggregateDebugReportRecord.getRegistrationOrigin());
        assertThat(aggregateDebugReportRecord.getSourceId()).isEqualTo("S1");
        assertThat(aggregateDebugReportRecord.getTriggerId()).isEqualTo("T1");
        assertThat(aggregateDebugReportRecord.getContributions()).isEqualTo(9);
    }

    @Test
    public void validate() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AggregateDebugReportRecord.Builder(
                                        /* reportGenerationTime= */ 0L,
                                        /* topLevelRegistrant= */ null,
                                        /* registrantApp= */ Uri.parse("com.test1.myapp"),
                                        /* registrationOrigin= */ Uri.parse(
                                                "https://destination.test"),
                                        /* contributions= */ 0)
                                .build());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AggregateDebugReportRecord.Builder(
                                        /* reportGenerationTime= */ 0L,
                                        /* topLevelRegistrant= */ Uri.parse(
                                                "android-app://com.example1.sample"),
                                        /* registrantApp= */ null,
                                        /* registrationOrigin= */ Uri.parse(
                                                "https://destination.test"),
                                        /* contributions= */ 0)
                                .build());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AggregateDebugReportRecord.Builder(
                                        /* reportGenerationTime= */ 0L,
                                        /* topLevelRegistrant= */ Uri.parse(
                                                "android-app://com.example1.sample"),
                                        /* registrantApp= */ Uri.parse("com.test1.myapp"),
                                        /* registrationOrigin= */ null,
                                        /* contributions= */ 0)
                                .build());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AggregateDebugReportRecord.Builder(
                                        /* reportGenerationTime= */ 0L,
                                        /* topLevelRegistrant= */ null,
                                        /* registrantApp= */ null,
                                        /* registrationOrigin= */ Uri.parse(
                                                "https://destination.test"),
                                        /* contributions= */ 0)
                                .build());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AggregateDebugReportRecord.Builder(
                                        /* reportGenerationTime= */ 0L,
                                        /* topLevelRegistrant= */ null,
                                        /* registrantApp= */ Uri.parse("com.test1.myapp"),
                                        /* registrationOrigin= */ null,
                                        /* contributions= */ 0)
                                .build());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AggregateDebugReportRecord.Builder(
                                        /* reportGenerationTime= */ 0L,
                                        /* topLevelRegistrant= */ Uri.parse(
                                                "android-app://com.example1.sample"),
                                        /* registrantApp= */ null,
                                        /* registrationOrigin= */ null,
                                        /* contributions= */ 0)
                                .build());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AggregateDebugReportRecord.Builder(
                                        /* reportGenerationTime= */ 0L,
                                        /* topLevelRegistrant= */ null,
                                        /* registrantApp= */ null,
                                        /* registrationOrigin= */ null,
                                        /* contributions= */ 0)
                                .build());
    }

    private static AggregateDebugReportRecord createExample1AggregateDebugReportRecord() {
        return new AggregateDebugReportRecord.Builder(
                        /* reportGenerationTime= */ 1000L,
                        /* topLevelRegistrant= */ Uri.parse("android-app://com.example1.sample"),
                        /* registrantApp= */ Uri.parse("com.test1.myapp"),
                        /* registrationOrigin= */ Uri.parse("https://destination.test"),
                        /* contributions= */ 9)
                .setSourceId("S1")
                .setTriggerId("T1")
                .build();
    }

    private static AggregateDebugReportRecord createExample2AggregateDebugReportRecord() {
        return new AggregateDebugReportRecord.Builder(
                        /* reportGenerationTime= */ 1000L,
                        /* topLevelRegistrant= */ Uri.parse("android-app://com.example1.sample"),
                        /* registrantApp= */ Uri.parse("com.test2.myapp"),
                        /* registrationOrigin= */ Uri.parse("https://destination.test"),
                        /* contributions= */ 9)
                .setSourceId("S1")
                .setTriggerId("T1")
                .build();
    }
}
