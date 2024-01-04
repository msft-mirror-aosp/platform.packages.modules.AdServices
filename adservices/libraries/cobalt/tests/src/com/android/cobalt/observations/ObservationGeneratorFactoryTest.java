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

package com.android.cobalt.observations;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.cobalt.data.ObservationGenerator;
import com.android.cobalt.domain.Project;
import com.android.cobalt.observations.testing.FakeSecureRandom;
import com.android.cobalt.system.SystemData;

import com.google.cobalt.MetricDefinition;
import com.google.cobalt.ReportDefinition;
import com.google.cobalt.ReportDefinition.PrivacyLevel;
import com.google.cobalt.ReportDefinition.ReportType;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.security.SecureRandom;
import java.util.List;

@RunWith(JUnit4.class)
public final class ObservationGeneratorFactoryTest {
    private final ObservationGeneratorFactory mFactory;

    public ObservationGeneratorFactoryTest() {
        Project project =
                Project.create(/* customerId= */ 0, /* projectId= */ 1, /* metrics= */ List.of());
        SecureRandom secureRandom = new FakeSecureRandom();
        this.mFactory =
                new ObservationGeneratorFactory(
                        project,
                        new SystemData(),
                        new PrivacyGenerator(secureRandom),
                        secureRandom);
    }

    @Test
    public void getObservationGenerator_nonPrivateFleetwideOccurrenceCounts() throws Exception {
        ObservationGenerator generator =
                mFactory.getObservationGenerator(
                        MetricDefinition.getDefaultInstance(),
                        ReportDefinition.newBuilder()
                                .setReportType(ReportType.FLEETWIDE_OCCURRENCE_COUNTS)
                                .setPrivacyLevel(PrivacyLevel.NO_ADDED_PRIVACY)
                                .build());

        assertThat(generator).isInstanceOf(NonPrivateObservationGenerator.class);
    }

    @Test
    public void getObservationGenerator_privateFleetwideOccurrenceCounts() throws Exception {
        ObservationGenerator generator =
                mFactory.getObservationGenerator(
                        MetricDefinition.getDefaultInstance(),
                        ReportDefinition.newBuilder()
                                .setReportType(ReportType.FLEETWIDE_OCCURRENCE_COUNTS)
                                .setPrivacyLevel(PrivacyLevel.HIGH_PRIVACY)
                                .build());

        assertThat(generator).isInstanceOf(PrivateObservationGenerator.class);
    }

    @Test
    public void getObservationGenerator_fleetwideOccurrenceCounts_noPrivacyLevelSet()
            throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder()
                                        .setReportType(ReportType.FLEETWIDE_OCCURRENCE_COUNTS)
                                        .build()));
    }

    @Test
    public void getObservationGenerator_reportTypeNotSet_throwsAssertionError() throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder().build()));
    }

    @Test
    public void getObservationGenerator_reportTypeUnset_throwsAssertionError() throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder()
                                        .setReportType(ReportType.REPORT_TYPE_UNSET)
                                        .build()));
    }

    @Test
    public void getObservationGenerator_uniqueDeviceCounts_throwsAssertionError() throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder()
                                        .setReportType(ReportType.UNIQUE_DEVICE_COUNTS)
                                        .build()));
    }

    @Test
    public void getObservationGenerator_uniqueDeviceHistograms_throwsAssertionError()
            throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder()
                                        .setReportType(ReportType.UNIQUE_DEVICE_HISTOGRAMS)
                                        .build()));
    }

    @Test
    public void getObservationGenerator_hourlyValueHistograms_throwsAssertionError()
            throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder()
                                        .setReportType(ReportType.HOURLY_VALUE_HISTOGRAMS)
                                        .build()));
    }

    @Test
    public void getObservationGenerator_fleetwideHistograms_throwsAssertionError()
            throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder()
                                        .setReportType(ReportType.FLEETWIDE_HISTOGRAMS)
                                        .build()));
    }

    @Test
    public void getObservationGenerator_fleetwideMeans_throwsAssertionError() throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder()
                                        .setReportType(ReportType.FLEETWIDE_MEANS)
                                        .build()));
    }

    @Test
    public void getObservationGenerator_uniqueDeviceNumericStats_throwsAssertionError()
            throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder()
                                        .setReportType(ReportType.UNIQUE_DEVICE_NUMERIC_STATS)
                                        .build()));
    }

    @Test
    public void getObservationGenerator_hourlyValueNumericStats_throwsAssertionError()
            throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder()
                                        .setReportType(ReportType.HOURLY_VALUE_NUMERIC_STATS)
                                        .build()));
    }

    @Test
    public void getObservationGenerator_stringsCounts_throwsAssertionError() throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder()
                                        .setReportType(ReportType.STRING_COUNTS)
                                        .build()));
    }

    @Test
    public void getObservationGenerator_uniqueDeviceStringCounts_throwsAssertionError()
            throws Exception {
        assertThrows(
                AssertionError.class,
                () ->
                        mFactory.getObservationGenerator(
                                MetricDefinition.getDefaultInstance(),
                                ReportDefinition.newBuilder()
                                        .setReportType(ReportType.UNIQUE_DEVICE_STRING_COUNTS)
                                        .build()));
    }
}
