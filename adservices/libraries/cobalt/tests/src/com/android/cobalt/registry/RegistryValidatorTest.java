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
import static com.google.cobalt.ReportDefinition.LocalAggregationProcedure.LOCAL_AGGREGATION_PROCEDURE_UNSET;
import static com.google.cobalt.ReportDefinition.LocalAggregationProcedure.SUM_PROCEDURE;
import static com.google.cobalt.ReportDefinition.PrivacyMechanism.DE_IDENTIFICATION;
import static com.google.cobalt.ReportDefinition.PrivacyMechanism.SHUFFLED_DIFFERENTIAL_PRIVACY;
import static com.google.cobalt.ReportDefinition.ReportType.FLEETWIDE_OCCURRENCE_COUNTS;
import static com.google.cobalt.ReportDefinition.ReportType.STRING_COUNTS;
import static com.google.cobalt.WindowSize.WINDOW_1_DAY;
import static com.google.cobalt.WindowSize.WINDOW_28_DAYS;
import static com.google.cobalt.WindowSize.WINDOW_30_DAYS;
import static com.google.cobalt.WindowSize.WINDOW_7_DAYS;
import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.common.AdServicesUnitTestCase;

import com.google.cobalt.IntegerBuckets;
import com.google.cobalt.MetricDefinition;
import com.google.cobalt.MetricDefinition.MetricType;
import com.google.cobalt.ReportDefinition;
import com.google.cobalt.ReportDefinition.LocalAggregationProcedure;
import com.google.cobalt.ReportDefinition.PrivacyMechanism;
import com.google.cobalt.ReportDefinition.ReportType;
import com.google.cobalt.WindowSize;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.function.BiPredicate;

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
    public void testValidateMinAndMaxValues_notPrivateFleetwideOccurrenceCounts() {
        for (ReportType reportType : ReportType.values()) {
            for (PrivacyMechanism privacyMechanism : PrivacyMechanism.values()) {
                if (reportType.equals(FLEETWIDE_OCCURRENCE_COUNTS)
                        && privacyMechanism.equals(SHUFFLED_DIFFERENTIAL_PRIVACY)) {
                    continue;
                }
                expect.that(
                                RegistryValidator.validateMinAndMaxValues(
                                        reportType,
                                        privacyMechanism,
                                        /* minValue= */ 0,
                                        /* maxValue= */ 0))
                        .isTrue();
                expect.that(
                                RegistryValidator.validateMinAndMaxValues(
                                        reportType,
                                        privacyMechanism,
                                        /* minValue= */ 1,
                                        /* maxValue= */ 0))
                        .isFalse();
                expect.that(
                                RegistryValidator.validateMinAndMaxValues(
                                        reportType,
                                        privacyMechanism,
                                        /* minValue= */ 0,
                                        /* maxValue= */ 1))
                        .isFalse();
            }
        }
    }

    @Test
    public void testValidateMinAndMaxValues_privateFleetwideOccurrenceCounts() {
        BiPredicate<Long, Long> validateMinAndMaxValues =
                (minValue, maxValue) ->
                        RegistryValidator.validateMinAndMaxValues(
                                FLEETWIDE_OCCURRENCE_COUNTS,
                                SHUFFLED_DIFFERENTIAL_PRIVACY,
                                minValue,
                                maxValue);

        // Both positive and maxValue >= minValue
        expect.withMessage("validateMinAndMaxValues(1,1)")
                .that(validateMinAndMaxValues.test(/* minValue= */ 1L, /* maxValue= */ 1L))
                .isTrue();
        expect.withMessage("validateMinAndMaxValues(1,2)")
                .that(validateMinAndMaxValues.test(/* minValue= */ 1L, /* maxValue= */ 2L))
                .isTrue();

        // minValue <= 0
        expect.withMessage("validateMinAndMaxValues(0,1)")
                .that(validateMinAndMaxValues.test(/* minValue= */ 0L, /* maxValue= */ 1L))
                .isFalse();
        expect.withMessage("validateMinAndMaxValues(-1,1)")
                .that(validateMinAndMaxValues.test(/* minValue= */ -1L, /* maxValue= */ 1L))
                .isFalse();

        // maxValue <= 0
        expect.withMessage("validateMinAndMaxValues(1,0)")
                .that(validateMinAndMaxValues.test(/* minValue= */ 1L, /* maxValue= */ 0L))
                .isFalse();
        expect.withMessage("validateMinAndMaxValues(1,-1)")
                .that(validateMinAndMaxValues.test(/* minValue= */ 1L, /* maxValue= */ -1L))
                .isFalse();

        // maxValue < minValue
        expect.withMessage("validateMinAndMaxValues(2,1)")
                .that(validateMinAndMaxValues.test(/* minValue= */ 2L, /* maxValue= */ 1L))
                .isFalse();
    }

    @Test
    public void testValidateMaxCount_nonZeroFails() {
        assertThat(RegistryValidator.validateMaxCount(1)).isFalse();
    }

    @Test
    public void testValidateMaxCount_zeroPasses() {
        assertThat(RegistryValidator.validateMaxCount(0)).isTrue();
    }

    @Test
    public void testLocalAggregationPeriod_windowUnsetSupported() {
        for (WindowSize windowSize : WindowSize.values()) {
            switch (windowSize) {
                case UNSET:
                    expect.that(RegistryValidator.validateLocalAggregationPeriod(windowSize))
                            .isTrue();
                    break;
                default:
                    expect.that(RegistryValidator.validateLocalAggregationPeriod(windowSize))
                            .isFalse();
                    break;
            }
        }
    }

    @Test
    public void testLocalAggregationProcedure_noneSuppoorted() {
        for (LocalAggregationProcedure localAggregationProcedure :
                LocalAggregationProcedure.values()) {
            switch (localAggregationProcedure) {
                case LOCAL_AGGREGATION_PROCEDURE_UNSET:
                    expect.that(
                                    RegistryValidator.validateLocalAggregationProcedure(
                                            localAggregationProcedure))
                            .isTrue();
                    break;
                default:
                    expect.that(
                                    RegistryValidator.validateLocalAggregationProcedure(
                                            localAggregationProcedure))
                            .isFalse();
                    break;
            }
        }
    }

    @Test
    public void testLocalAggregationProcedurePercentileN_zeroSuppoorted() {
        expect.that(RegistryValidator.validateLocalAggregationPercentileN(0)).isTrue();
        for (int i = 9; i < 100; i += 10) {
            expect.that(RegistryValidator.validateLocalAggregationPercentileN(i)).isFalse();
        }
    }

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_privateFleetwideOccurrenceCounts() {
        MetricDefinition metric = getMetricDefinition(OCCURRENCE);
        ReportDefinition report =
                getReportDefinition(FLEETWIDE_OCCURRENCE_COUNTS, SHUFFLED_DIFFERENTIAL_PRIVACY)
                        .toBuilder()
                        .setMinValue(1L)
                        .setMaxValue(2L)
                        .build();
        assertThat(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report)).isTrue();
    }

    @Test
    public void tetIsValidReportTypeAndPrivacyMechanism_deIdFleetwideOccurrenceCounts() {
        MetricDefinition metric = getMetricDefinition(OCCURRENCE);
        ReportDefinition report =
                getReportDefinition(FLEETWIDE_OCCURRENCE_COUNTS, DE_IDENTIFICATION);
        assertThat(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report)).isTrue();
    }

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_deIdStringCounts() {
        MetricDefinition metric = getMetricDefinition(STRING);
        ReportDefinition report = getReportDefinition(STRING_COUNTS, DE_IDENTIFICATION);
        assertThat(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report)).isTrue();
    }

    @Test
    public void
            testIsValidReportTypeAndPrivacyMechanism_fleetwideOccurrenceCounts_unsetIntegerBuckets() {
        MetricDefinition metric = getMetricDefinition(OCCURRENCE);
        ReportDefinition report =
                getReportDefinition(FLEETWIDE_OCCURRENCE_COUNTS, DE_IDENTIFICATION);
        expect.that(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report))
                .isTrue();
        expect.that(
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
        MetricDefinition metric = getMetricDefinition(STRING);
        ReportDefinition report = getReportDefinition(STRING_COUNTS, DE_IDENTIFICATION);
        expect.that(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report))
                .isTrue();
        expect.that(
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
    public void testIsValidReportTypeAndPrivacyMechanism_fleetwideOccurrenceCounts_minAndMax() {
        MetricDefinition metric = getMetricDefinition(OCCURRENCE);
        ReportDefinition report =
                getReportDefinition(FLEETWIDE_OCCURRENCE_COUNTS, DE_IDENTIFICATION);

        // De-identified reports must have minValue == maxValue == 0
        expect.that(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report))
                .isTrue();
        expect.that(
                        RegistryValidator.isValidReportTypeAndPrivacyMechanism(
                                metric, report.toBuilder().setMinValue(1).setMaxValue(2).build()))
                .isFalse();

        report = getReportDefinition(FLEETWIDE_OCCURRENCE_COUNTS, SHUFFLED_DIFFERENTIAL_PRIVACY);

        // Private reports must have 0 < minValue <= maxValue
        expect.that(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report))
                .isFalse();
        expect.that(
                        RegistryValidator.isValidReportTypeAndPrivacyMechanism(
                                metric, report.toBuilder().setMinValue(1).setMaxValue(2).build()))
                .isTrue();
    }

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_stringCounts_minAndMax() {
        MetricDefinition metric = getMetricDefinition(STRING);
        ReportDefinition report = getReportDefinition(STRING_COUNTS, DE_IDENTIFICATION);

        // Reports must have minValue == maxValue == 0
        expect.that(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report))
                .isTrue();
        expect.that(
                        RegistryValidator.isValidReportTypeAndPrivacyMechanism(
                                metric, report.toBuilder().setMinValue(1).setMaxValue(2).build()))
                .isFalse();
    }

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_fleetwideOccurrenceCounts_maxCount() {
        MetricDefinition metric = getMetricDefinition(OCCURRENCE);
        ReportDefinition report =
                getReportDefinition(FLEETWIDE_OCCURRENCE_COUNTS, DE_IDENTIFICATION);

        // De-identified reports must have minValue == maxValue == 0
        expect.that(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report))
                .isTrue();
        expect.that(
                        RegistryValidator.isValidReportTypeAndPrivacyMechanism(
                                metric, report.toBuilder().setMaxCount(2).build()))
                .isFalse();

        report = getReportDefinition(FLEETWIDE_OCCURRENCE_COUNTS, SHUFFLED_DIFFERENTIAL_PRIVACY);

        expect.that(
                        RegistryValidator.isValidReportTypeAndPrivacyMechanism(
                                metric,
                                report.toBuilder()
                                        .setMinValue(1)
                                        .setMaxValue(2)
                                        .setMaxCount(1)
                                        .build()))
                .isFalse();
    }

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_stringCounts_maxCount() {
        MetricDefinition metric = getMetricDefinition(STRING);
        ReportDefinition report = getReportDefinition(STRING_COUNTS, DE_IDENTIFICATION);

        // Reports must have minValue == maxValue == 0
        expect.that(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report))
                .isTrue();
        expect.that(
                        RegistryValidator.isValidReportTypeAndPrivacyMechanism(
                                metric, report.toBuilder().setMinValue(1).setMaxValue(2).build()))
                .isFalse();
    }

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_setlocalAggregationPeriodFails() {
        MetricDefinition metric = getMetricDefinition(OCCURRENCE);
        ReportDefinition report =
                getReportDefinition(FLEETWIDE_OCCURRENCE_COUNTS, DE_IDENTIFICATION);

        expect.that(
                        RegistryValidator.isValidReportTypeAndPrivacyMechanism(
                                metric,
                                report.toBuilder().setLocalAggregationPeriod(WINDOW_1_DAY).build()))
                .isFalse();
        expect.that(
                        RegistryValidator.isValidReportTypeAndPrivacyMechanism(
                                metric,
                                report.toBuilder()
                                        .setLocalAggregationPeriod(WINDOW_7_DAYS)
                                        .build()))
                .isFalse();
        expect.that(
                        RegistryValidator.isValidReportTypeAndPrivacyMechanism(
                                metric,
                                report.toBuilder()
                                        .setLocalAggregationPeriod(WINDOW_28_DAYS)
                                        .build()))
                .isFalse();
        expect.that(
                        RegistryValidator.isValidReportTypeAndPrivacyMechanism(
                                metric,
                                report.toBuilder()
                                        .setLocalAggregationPeriod(WINDOW_30_DAYS)
                                        .build()))
                .isFalse();
    }

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_anyLocalAggregationProcedureFails() {
        MetricDefinition metric = getMetricDefinition(OCCURRENCE);
        ReportDefinition report =
                getReportDefinition(FLEETWIDE_OCCURRENCE_COUNTS, DE_IDENTIFICATION).toBuilder()
                        .setLocalAggregationProcedure(SUM_PROCEDURE)
                        .build();
        assertThat(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report))
                .isFalse();
    }

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_nonZeroLocalAggregationPercentileFails() {
        MetricDefinition metric = getMetricDefinition(OCCURRENCE);
        ReportDefinition report =
                getReportDefinition(FLEETWIDE_OCCURRENCE_COUNTS, DE_IDENTIFICATION).toBuilder()
                        .setLocalAggregationProcedurePercentileN(1)
                        .build();
        assertThat(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report))
                .isFalse();
    }

    private static MetricDefinition getMetricDefinition(MetricType metricType) {
        return MetricDefinition.newBuilder().setMetricType(metricType).build();
    }

    private static ReportDefinition getReportDefinition(
            ReportType reportType, PrivacyMechanism privacyMechanism) {
        return ReportDefinition.newBuilder()
                .setReportType(reportType)
                .setPrivacyMechanism(privacyMechanism)
                .build();
    }
}
