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

package com.android.cobalt.registry;

import static com.google.cobalt.MetricDefinition.MetricType.OCCURRENCE;
import static com.google.cobalt.MetricDefinition.MetricType.STRING;
import static com.google.cobalt.ReportDefinition.PrivacyMechanism.DE_IDENTIFICATION;
import static com.google.cobalt.ReportDefinition.PrivacyMechanism.SHUFFLED_DIFFERENTIAL_PRIVACY;
import static com.google.cobalt.ReportDefinition.ReportType.FLEETWIDE_OCCURRENCE_COUNTS;
import static com.google.cobalt.ReportDefinition.ReportType.STRING_COUNTS;
import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.common.AdServicesUnitTestCase;

import com.google.cobalt.IntegerBuckets;
import com.google.cobalt.MetricDefinition;
import com.google.cobalt.ReportDefinition;
import com.google.cobalt.ReportDefinition.PrivacyMechanism;
import com.google.cobalt.ReportDefinition.ReportType;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RegistryValidatorTest extends AdServicesUnitTestCase {

    @Test
    public void testValidateReportType_occurrenceMetric_onlySupportsFleetwideOccurrenceCounts() {
        for (ReportType reportType : ReportType.values()) {
            switch (reportType) {
                case FLEETWIDE_OCCURRENCE_COUNTS:
                    expect.withMessage(
                                    "RegistryValidator.validateReportType(OCCURRENCE, reportType)")
                            .that(RegistryValidator.validateReportType(OCCURRENCE, reportType))
                            .isTrue();
                    break;
                default:
                    expect.withMessage(
                                    "RegistryValidator.validateReportType(OCCURRENCE, reportType)")
                            .that(RegistryValidator.validateReportType(OCCURRENCE, reportType))
                            .isFalse();
                    break;
            }
        }
    }

    @Test
    public void testValidateReportType_stringMetric_onlySupportsStringCounts() {
        for (ReportType reportType : ReportType.values()) {
            switch (reportType) {
                case STRING_COUNTS:
                    expect.withMessage("RegistryValidator.validateReportType(STRING, reportType)")
                            .that(RegistryValidator.validateReportType(STRING, reportType))
                            .isTrue();
                    break;
                default:
                    expect.withMessage("RegistryValidator.validateReportType(STRING, reportType)")
                            .that(RegistryValidator.validateReportType(STRING, reportType))
                            .isFalse();
                    break;
            }
        }
    }

    @Test
    public void testValidatePrivacyMechanism_fleetwideOccurrenceCounts_supportsDeIdAndShuffledDp() {
        for (PrivacyMechanism privacyMechanism : PrivacyMechanism.values()) {
            switch (privacyMechanism) {
                case DE_IDENTIFICATION:
                case SHUFFLED_DIFFERENTIAL_PRIVACY:
                    expect.withMessage(
                                    "RegistryValidator.validatePrivacyMechanism("
                                            + "FLEETWIDE_OCCURRENCE_COUNTS,privacyMechanism))")
                            .that(
                                    RegistryValidator.validatePrivacyMechanism(
                                            FLEETWIDE_OCCURRENCE_COUNTS, privacyMechanism))
                            .isTrue();
                    break;
                default:
                    expect.withMessage(
                                    "RegistryValidator.validatePrivacyMechanism("
                                            + "FLEETWIDE_OCCURRENCE_COUNTS,privacyMechanism))")
                            .that(
                                    RegistryValidator.validatePrivacyMechanism(
                                            FLEETWIDE_OCCURRENCE_COUNTS, privacyMechanism))
                            .isFalse();
                    break;
            }
        }
    }

    @Test
    public void testValidatePrivacyMechanism_stringCounts_supportsDeId() {
        for (PrivacyMechanism privacyMechanism : PrivacyMechanism.values()) {
            switch (privacyMechanism) {
                case DE_IDENTIFICATION:
                    expect.withMessage(
                                    "RegistryValidator.validatePrivacyMechanism(STRING_COUNTS,"
                                            + " privacyMechanism))")
                            .that(
                                    RegistryValidator.validatePrivacyMechanism(
                                            STRING_COUNTS, privacyMechanism))
                            .isTrue();
                    break;
                default:
                    expect.withMessage(
                                    "RegistryValidator.validatePrivacyMechanism(STRING_COUNTS,"
                                            + " privacyMechanism))")
                            .that(
                                    RegistryValidator.validatePrivacyMechanism(
                                            STRING_COUNTS, privacyMechanism))
                            .isFalse();
                    break;
            }
        }
    }

    @Test
    public void testValidateIntegerBuckets_defaultInstancePasses() {
        assertThat(RegistryValidator.validateIntegerBuckets(IntegerBuckets.getDefaultInstance()))
                .isTrue();
    }

    @Test
    public void testValidateIntegerBuckets_nonDefaultInstanceFails() {
        assertThat(
                        RegistryValidator.validateIntegerBuckets(
                                IntegerBuckets.newBuilder().setSparseOutput(true).build()))
                .isFalse();
    }

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_privateFleetwideOccurrenceCounts() {
        MetricDefinition metric = MetricDefinition.newBuilder().setMetricType(OCCURRENCE).build();
        ReportDefinition report =
                ReportDefinition.newBuilder()
                        .setReportType(FLEETWIDE_OCCURRENCE_COUNTS)
                        .setPrivacyMechanism(SHUFFLED_DIFFERENTIAL_PRIVACY)
                        .build();
        assertThat(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report)).isTrue();
    }

    @Test
    public void tetIsValidReportTypeAndPrivacyMechanism_deIdFleetwideOccurrenceCounts() {
        MetricDefinition metric = MetricDefinition.newBuilder().setMetricType(OCCURRENCE).build();
        ReportDefinition report =
                ReportDefinition.newBuilder()
                        .setReportType(FLEETWIDE_OCCURRENCE_COUNTS)
                        .setPrivacyMechanism(DE_IDENTIFICATION)
                        .build();
        assertThat(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report)).isTrue();
    }

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_deIdStringCounts() {
        MetricDefinition metric = MetricDefinition.newBuilder().setMetricType(STRING).build();
        ReportDefinition report =
                ReportDefinition.newBuilder()
                        .setReportType(STRING_COUNTS)
                        .setPrivacyMechanism(DE_IDENTIFICATION)
                        .build();
        assertThat(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report)).isTrue();
    }

    @Test
    public void
            testIsValidReportTypeAndPrivacyMechanism_fleetwideOccurrenceCounts_unsetIntegerBuckets() {
        MetricDefinition metric = MetricDefinition.newBuilder().setMetricType(OCCURRENCE).build();
        ReportDefinition report =
                ReportDefinition.newBuilder()
                        .setReportType(FLEETWIDE_OCCURRENCE_COUNTS)
                        .setPrivacyMechanism(DE_IDENTIFICATION)
                        .build();
        expect.withMessage("RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report)")
                .that(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report))
                .isTrue();
        expect.withMessage(
                        "RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric,report"
                                + ".toBuilder().setIntBuckets(IntegerBuckets.newBuilder()"
                                + ".setSparseOutput(true).build()).build())")
                .that(
                        RegistryValidator.isValidReportTypeAndPrivacyMechanism(
                                metric,
                                report.toBuilder()
                                        .setIntBuckets(
                                                IntegerBuckets.newBuilder()
                                                        .setSparseOutput(true)
                                                        .build())
                                        .build()))
                .isFalse();
    }

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_stringCounts_defaultIntegerBuckets() {
        MetricDefinition metric = MetricDefinition.newBuilder().setMetricType(STRING).build();
        ReportDefinition report =
                ReportDefinition.newBuilder()
                        .setReportType(STRING_COUNTS)
                        .setPrivacyMechanism(DE_IDENTIFICATION)
                        .build();
        expect.withMessage("RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report)")
                .that(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report))
                .isTrue();
        expect.withMessage(
                        "RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric,"
                                + "report.toBuilder().setIntBuckets(IntegerBuckets.newBuilder()"
                                + ".setSparseOutput(true).build()).build())")
                .that(
                        RegistryValidator.isValidReportTypeAndPrivacyMechanism(
                                metric,
                                report.toBuilder()
                                        .setIntBuckets(
                                                IntegerBuckets.newBuilder()
                                                        .setSparseOutput(true)
                                                        .build())
                                        .build()))
                .isFalse();
    }
}
