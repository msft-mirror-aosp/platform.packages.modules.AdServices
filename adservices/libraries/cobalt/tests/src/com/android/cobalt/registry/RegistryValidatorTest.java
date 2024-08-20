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
import static com.google.cobalt.ReportDefinition.ReportingInterval.DAYS_1;
import static com.google.cobalt.ReportDefinition.ReportingInterval.HOURS_1;
import static com.google.cobalt.ReportDefinition.ReportingInterval.REPORTING_INTERVAL_UNSET;
import static com.google.cobalt.SystemProfileField.APP_VERSION;
import static com.google.cobalt.SystemProfileField.CHANNEL;
import static com.google.cobalt.SystemProfileSelectionPolicy.REPORT_ALL;
import static com.google.cobalt.SystemProfileSelectionPolicy.SELECT_FIRST;
import static com.google.cobalt.WindowSize.WINDOW_1_DAY;
import static com.google.cobalt.WindowSize.WINDOW_28_DAYS;
import static com.google.cobalt.WindowSize.WINDOW_30_DAYS;
import static com.google.cobalt.WindowSize.WINDOW_7_DAYS;
import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.common.AdServicesUnitTestCase;

import com.google.cobalt.IntegerBuckets;
import com.google.cobalt.MetricDefinition;
import com.google.cobalt.MetricDefinition.Metadata;
import com.google.cobalt.MetricDefinition.MetricDimension;
import com.google.cobalt.MetricDefinition.MetricType;
import com.google.cobalt.ReleaseStage;
import com.google.cobalt.ReportDefinition;
import com.google.cobalt.ReportDefinition.LocalAggregationProcedure;
import com.google.cobalt.ReportDefinition.PrivacyMechanism;
import com.google.cobalt.ReportDefinition.ReportType;
import com.google.cobalt.ReportDefinition.ReportingInterval;
import com.google.cobalt.ReportDefinition.ShuffledDifferentialPrivacyConfig;
import com.google.cobalt.ReportDefinition.ShuffledDifferentialPrivacyConfig.DevicePrivacyDependencySet;
import com.google.cobalt.StringSketchParameters;
import com.google.cobalt.SystemProfileField;
import com.google.cobalt.SystemProfileSelectionPolicy;
import com.google.cobalt.WindowSize;

import org.junit.Test;

import java.util.List;
import java.util.function.BiPredicate;

public final class RegistryValidatorTest extends AdServicesUnitTestCase {

    @Test
    public void testDimensionsAreEquivalent_emptyDimensions() {
        assertThat(RegistryValidator.dimensionsAreEquivalent(List.of(), List.of())).isTrue();
    }

    @Test
    public void testDimensionsAreEquivalent_sameMaxEventCodes() {
        assertThat(
                        RegistryValidator.dimensionsAreEquivalent(
                                List.of(getMetricDimension(1), getMetricDimension(2)),
                                List.of(getMetricDimension(1), getMetricDimension(2))))
                .isTrue();
    }

    @Test
    public void testDimensionsAreEquivalent_differentMaxEventCodes() {
        assertThat(
                        RegistryValidator.dimensionsAreEquivalent(
                                List.of(getMetricDimension(1), getMetricDimension(2)),
                                List.of(getMetricDimension(2), getMetricDimension(1))))
                .isFalse();
    }

    @Test
    public void testDimensionsAreEquivalent_differentMaxEventCodesSize() {
        assertThat(
                        RegistryValidator.dimensionsAreEquivalent(
                                List.of(getMetricDimension(1), getMetricDimension(2)),
                                List.of(getMetricDimension(1))))
                .isFalse();
    }

    @Test
    public void testDimensionsAreEquivalent_sameEnumeratedDimensions() {
        assertThat(
                        RegistryValidator.dimensionsAreEquivalent(
                                List.of(getMetricDimension(List.of(1, 2))),
                                List.of(getMetricDimension(List.of(1, 2)))))
                .isTrue();
    }

    @Test
    public void testDimensionsAreEquivalent_sameEnumeratedDimensions_differentEventCodeOrder() {
        assertThat(
                        RegistryValidator.dimensionsAreEquivalent(
                                List.of(getMetricDimension(List.of(1, 2))),
                                List.of(getMetricDimension(List.of(2, 1)))))
                .isTrue();
    }

    @Test
    public void testDimensionsAreEquivalent_differentEnumeratedDimensionsSize() {
        assertThat(
                        RegistryValidator.dimensionsAreEquivalent(
                                List.of(getMetricDimension(List.of(1, 2))), List.of()))
                .isFalse();
    }

    @Test
    public void testDimensionsAreEquivalent_enumeratedDimensions_oneIsSubset() {
        assertThat(
                        RegistryValidator.dimensionsAreEquivalent(
                                List.of(getMetricDimension(List.of(1, 2, 3))),
                                List.of(getMetricDimension(List.of(1, 2)))))
                .isFalse();
    }

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
    public void testValidateSystemProfileSelection_reportAllRequired() {
        for (SystemProfileSelectionPolicy systemProfileSelection :
                SystemProfileSelectionPolicy.values()) {
            switch (systemProfileSelection) {
                case REPORT_ALL:
                    expect.withMessage("validateSystemProfileSelection(REPORT_ALL)")
                            .that(
                                    RegistryValidator.validateSystemProfileSelection(
                                            systemProfileSelection))
                            .isTrue();
                    break;
                default:
                    expect.withMessage("validateSystemProfileSelection(%s)", systemProfileSelection)
                            .that(
                                    RegistryValidator.validateSystemProfileSelection(
                                            systemProfileSelection))
                            .isFalse();
                    break;
            }
        }
    }

    @Test
    public void testValidateSystemProfileFields_emptySupported() {
        assertThat(RegistryValidator.validateSystemProfileFields(List.of())).isTrue();
    }

    @Test
    public void testValidateSystemProfileFields_supportedFields() {
        for (SystemProfileField systemProfileField : SystemProfileField.values()) {
            switch (systemProfileField) {
                case APP_VERSION:
                case ARCH:
                case BOARD_NAME:
                case OS:
                case SYSTEM_VERSION:
                    expect.withMessage("validateSystemProfileFields(%s)", systemProfileField)
                            .that(
                                    RegistryValidator.validateSystemProfileFields(
                                            List.of(systemProfileField)))
                            .isTrue();
                    break;
                default:
                    expect.withMessage("validateSystemProfileFields(%s)", systemProfileField)
                            .that(
                                    RegistryValidator.validateSystemProfileFields(
                                            List.of(systemProfileField)))
                            .isFalse();
                    break;
            }
        }
    }

    @Test
    public void testValidateExperimentIds_requiredEmpty() {
        expect.withMessage("validateExperimentIds([])")
                .that(RegistryValidator.validateExperimentIds(List.of()))
                .isTrue();
        expect.withMessage("validateExperimentIds([1])")
                .that(RegistryValidator.validateExperimentIds(List.of(1L)))
                .isFalse();
    }

    @Test
    public void testValidatePoissonFields_supported() {
        assertThat(
                        RegistryValidator.validatePoissonFields(
                                FLEETWIDE_OCCURRENCE_COUNTS,
                                SHUFFLED_DIFFERENTIAL_PRIVACY,
                                /* numIndexPoints= */ 1,
                                StringSketchParameters.getDefaultInstance()))
                .isTrue();
    }

    @Test
    public void testValidatePoissonFields_numIndexPointsZeroFails() {
        assertThat(
                        RegistryValidator.validatePoissonFields(
                                FLEETWIDE_OCCURRENCE_COUNTS,
                                SHUFFLED_DIFFERENTIAL_PRIVACY,
                                /* numIndexPoints= */ 0,
                                StringSketchParameters.getDefaultInstance()))
                .isFalse();
    }

    @Test
    public void testValidatePoissonFields_stringSketchParamsSetFails() {
        assertThat(
                        RegistryValidator.validatePoissonFields(
                                FLEETWIDE_OCCURRENCE_COUNTS,
                                SHUFFLED_DIFFERENTIAL_PRIVACY,
                                /* numIndexPoints= */ 1,
                                StringSketchParameters.newBuilder().setNumHashes(1).build()))
                .isFalse();
    }

    @Test
    public void testValidatePoissonFields_deIdentifiedReportPasses() {
        assertThat(
                        RegistryValidator.validatePoissonFields(
                                FLEETWIDE_OCCURRENCE_COUNTS,
                                DE_IDENTIFICATION,
                                /* numIndexPoints= */ 1,
                                StringSketchParameters.getDefaultInstance()))
                .isTrue();
    }

    @Test
    public void testValidatePoissonFields_nonFleetwideOccurrenceCountsFails() {
        assertThat(
                        RegistryValidator.validatePoissonFields(
                                STRING_COUNTS,
                                SHUFFLED_DIFFERENTIAL_PRIVACY,
                                /* numIndexPoints= */ 1,
                                StringSketchParameters.getDefaultInstance()))
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
    public void testExpeditedSending_falseSupported() {
        expect.withMessage("validateExpeditedSending(false)")
                .that(RegistryValidator.validateExpeditedSending(false))
                .isTrue();
        expect.withMessage("validateExpeditedSending(true)")
                .that(RegistryValidator.validateExpeditedSending(true))
                .isFalse();
    }

    @Test
    public void testExemptFromConsent_falseSupported() {
        expect.withMessage("validateExemptFromConsent(false)")
                .that(RegistryValidator.validateExemptFromConsent(false))
                .isTrue();
        expect.withMessage("validateExemptFromConsent(true)")
                .that(RegistryValidator.validateExemptFromConsent(true))
                .isFalse();
    }

    @Test
    public void testReportingInterval_days1Supported() {
        for (ReportingInterval reportingInterval : ReportingInterval.values()) {
            switch (reportingInterval) {
                case DAYS_1:
                    expect.withMessage("validateReportingInterval(DAYS_1)")
                            .that(RegistryValidator.validateReportingInterval(reportingInterval))
                            .isTrue();
                    break;
                default:
                    expect.withMessage("validateReportingInterval(%s)", reportingInterval)
                            .that(RegistryValidator.validateReportingInterval(reportingInterval))
                            .isFalse();
                    break;
            }
        }
    }

    @Test
    public void testMaxPrivateIndex_mustBeLessThanMaxInt32() {
        // Dimension with 1000 possible events.
        List<MetricDimension> metricDimensions = List.of(getMetricDimension(999));

        for (ReportType reportType : ReportType.values()) {
            switch (reportType) {
                case REPORT_TYPE_UNSET:
                    break;
                case FLEETWIDE_OCCURRENCE_COUNTS:
                    // Max index points isn't checked for de-identified reports.
                    expect.that(
                                    RegistryValidator.validateMaxPrivateIndex(
                                            reportType,
                                            DE_IDENTIFICATION,
                                            metricDimensions,
                                            /* numIndexPoints= */ 10))
                            .isTrue();
                    // Max index points isn't checked for de-identified reports.
                    expect.that(
                                    RegistryValidator.validateMaxPrivateIndex(
                                            reportType,
                                            DE_IDENTIFICATION,
                                            metricDimensions,
                                            /* numIndexPoints= */ Integer.MAX_VALUE / 10))
                            .isTrue();
                    // Passes because 1000 * 10 < Integer.MAX_VALUE
                    expect.that(
                                    RegistryValidator.validateMaxPrivateIndex(
                                            reportType,
                                            SHUFFLED_DIFFERENTIAL_PRIVACY,
                                            metricDimensions,
                                            /* numIndexPoints= */ 10))
                            .isTrue();
                    // Fails because 1000 * Integer.MAX_VALUE / 10 > Integer.MAX_VALUE
                    expect.that(
                                    RegistryValidator.validateMaxPrivateIndex(
                                            reportType,
                                            SHUFFLED_DIFFERENTIAL_PRIVACY,
                                            metricDimensions,
                                            /* numIndexPoints= */ Integer.MAX_VALUE / 10))
                            .isFalse();
                    break;
                default:
                    // Max index points isn't checked for de-identified reports.
                    expect.that(
                                    RegistryValidator.validateMaxPrivateIndex(
                                            reportType,
                                            DE_IDENTIFICATION,
                                            metricDimensions,
                                            /* numIndexPoints= */ 10))
                            .isTrue();
                    // Max index points isn't checked for de-identified reports.
                    expect.that(
                                    RegistryValidator.validateMaxPrivateIndex(
                                            reportType,
                                            DE_IDENTIFICATION,
                                            metricDimensions,
                                            /* numIndexPoints= */ Integer.MAX_VALUE / 10))
                            .isTrue();
                    // Max index points not valid for reports other than private
                    // FLEETWIDE_OCCURRENCE_COUNTS.
                    expect.that(
                                    RegistryValidator.validateMaxPrivateIndex(
                                            reportType,
                                            SHUFFLED_DIFFERENTIAL_PRIVACY,
                                            metricDimensions,
                                            /* numIndexPoints= */ 10))
                            .isFalse();
                    // Max index points not valid for reports other than private
                    // FLEETWIDE_OCCURRENCE_COUNTS.
                    expect.that(
                                    RegistryValidator.validateMaxPrivateIndex(
                                            reportType,
                                            SHUFFLED_DIFFERENTIAL_PRIVACY,
                                            metricDimensions,
                                            /* numIndexPoints= */ Integer.MAX_VALUE / 10))
                            .isFalse();
                    break;
            }
        }
    }

    @Test
    public void testValidateMaxReleaseStages_reportNotSetSupported() {
        assertThat(
                        RegistryValidator.validateMaxReleaseStages(
                                /* metricMaxReleaseStage= */ ReleaseStage.GA,
                                /* reportMaxReleaseStage= */ ReleaseStage.RELEASE_STAGE_NOT_SET))
                .isTrue();
    }

    @Test
    public void testValidateMaxReleaseStages_metricGreaterThanReportPasses() {
        expect.withMessage("validateMaxReleaseStages(GA, OPEN_BETA)")
                .that(
                        RegistryValidator.validateMaxReleaseStages(
                                /* metricMaxReleaseStage= */ ReleaseStage.GA,
                                /* reportMaxReleaseStage= */ ReleaseStage.OPEN_BETA))
                .isTrue();
        expect.withMessage("validateMaxReleaseStages(OPEN_BETA, DOGFOOD)")
                .that(
                        RegistryValidator.validateMaxReleaseStages(
                                /* metricMaxReleaseStage= */ ReleaseStage.OPEN_BETA,
                                /* reportMaxReleaseStage= */ ReleaseStage.DOGFOOD))
                .isTrue();
        expect.withMessage("validateMaxReleaseStages(DOGFOOD, FISHFOOD)")
                .that(
                        RegistryValidator.validateMaxReleaseStages(
                                /* metricMaxReleaseStage= */ ReleaseStage.DOGFOOD,
                                /* reportMaxReleaseStage= */ ReleaseStage.FISHFOOD))
                .isTrue();
        expect.withMessage("validateMaxReleaseStages(FISHFOOD, DEBUG)")
                .that(
                        RegistryValidator.validateMaxReleaseStages(
                                /* metricMaxReleaseStage= */ ReleaseStage.FISHFOOD,
                                /* reportMaxReleaseStage= */ ReleaseStage.DEBUG))
                .isTrue();
    }

    @Test
    public void testValidateMaxReleaseStages_metricEqualToReportPasses() {
        expect.withMessage("validateMaxReleaseStages(GA, GA)")
                .that(
                        RegistryValidator.validateMaxReleaseStages(
                                /* metricMaxReleaseStage= */ ReleaseStage.GA,
                                /* reportMaxReleaseStage= */ ReleaseStage.GA))
                .isTrue();
        expect.withMessage("validateMaxReleaseStages(OPEN_BETA, OPEN_BETA)")
                .that(
                        RegistryValidator.validateMaxReleaseStages(
                                /* metricMaxReleaseStage= */ ReleaseStage.OPEN_BETA,
                                /* reportMaxReleaseStage= */ ReleaseStage.OPEN_BETA))
                .isTrue();
        expect.withMessage("validateMaxReleaseStages(DOGFOOD, DOGFOOD)")
                .that(
                        RegistryValidator.validateMaxReleaseStages(
                                /* metricMaxReleaseStage= */ ReleaseStage.DOGFOOD,
                                /* reportMaxReleaseStage= */ ReleaseStage.DOGFOOD))
                .isTrue();
        expect.withMessage("validateMaxReleaseStages(FISHFOOD, FISHFOOD)")
                .that(
                        RegistryValidator.validateMaxReleaseStages(
                                /* metricMaxReleaseStage= */ ReleaseStage.FISHFOOD,
                                /* reportMaxReleaseStage= */ ReleaseStage.FISHFOOD))
                .isTrue();
        expect.withMessage("validateMaxReleaseStages(DEBUG, DEBUG)")
                .that(
                        RegistryValidator.validateMaxReleaseStages(
                                /* metricMaxReleaseStage= */ ReleaseStage.DEBUG,
                                /* reportMaxReleaseStage= */ ReleaseStage.DEBUG))
                .isTrue();
    }

    @Test
    public void testValidateMaxReleaseStages_metricLessThanReportFails() {
        expect.withMessage("validateMaxReleaseStages(OPEN_BETA, GA)")
                .that(
                        RegistryValidator.validateMaxReleaseStages(
                                /* metricMaxReleaseStage= */ ReleaseStage.OPEN_BETA,
                                /* reportMaxReleaseStage= */ ReleaseStage.GA))
                .isFalse();
        expect.withMessage("validateMaxReleaseStages(DOGFOOD, OPEN_BETA)")
                .that(
                        RegistryValidator.validateMaxReleaseStages(
                                /* metricMaxReleaseStage= */ ReleaseStage.DOGFOOD,
                                /* reportMaxReleaseStage= */ ReleaseStage.OPEN_BETA))
                .isFalse();
        expect.withMessage("validateMaxReleaseStages(FISHFOOD, DOGFOOD)")
                .that(
                        RegistryValidator.validateMaxReleaseStages(
                                /* metricMaxReleaseStage= */ ReleaseStage.FISHFOOD,
                                /* reportMaxReleaseStage= */ ReleaseStage.DOGFOOD))
                .isFalse();
        expect.withMessage("validateMaxReleaseStages(DEBUG, FISHFOOD)")
                .that(
                        RegistryValidator.validateMaxReleaseStages(
                                /* metricMaxReleaseStage= */ ReleaseStage.DEBUG,
                                /* reportMaxReleaseStage= */ ReleaseStage.FISHFOOD))
                .isFalse();
        expect.withMessage("validateMaxReleaseStages(RELEASE_STAGE_NOT_SET, DEBUG)")
                .that(
                        RegistryValidator.validateMaxReleaseStages(
                                /* metricMaxReleaseStage= */ ReleaseStage.RELEASE_STAGE_NOT_SET,
                                /* reportMaxReleaseStage= */ ReleaseStage.DEBUG))
                .isFalse();
    }

    @Test
    public void testValidateShuffledDp_deIdentifiedReport() {
        assertThat(
                        RegistryValidator.validateShuffledDp(
                                DE_IDENTIFICATION,
                                ShuffledDifferentialPrivacyConfig.getDefaultInstance()))
                .isTrue();
    }

    @Test
    public void testValidateShuffledDp() {
        assertThat(
                        RegistryValidator.validateShuffledDp(
                                DE_IDENTIFICATION,
                                ShuffledDifferentialPrivacyConfig.newBuilder()
                                        .setPoissonMean(0.1)
                                        .setDevicePrivacyDependencySet(
                                                DevicePrivacyDependencySet.V1)
                                        .build()))
                .isTrue();
    }

    @Test
    public void testValidateShuffledDp_negativePoissonMeanFails() {
        assertThat(
                        RegistryValidator.validateShuffledDp(
                                SHUFFLED_DIFFERENTIAL_PRIVACY,
                                ShuffledDifferentialPrivacyConfig.newBuilder()
                                        .setPoissonMean(-0.1)
                                        .setDevicePrivacyDependencySet(
                                                DevicePrivacyDependencySet.V1)
                                        .build()))
                .isFalse();
    }

    @Test
    public void testValidateShuffledDp_zeroPoissonMeanFails() {
        assertThat(
                        RegistryValidator.validateShuffledDp(
                                SHUFFLED_DIFFERENTIAL_PRIVACY,
                                ShuffledDifferentialPrivacyConfig.newBuilder()
                                        .setPoissonMean(0.0)
                                        .setDevicePrivacyDependencySet(
                                                DevicePrivacyDependencySet.V1)
                                        .build()))
                .isFalse();
    }

    @Test
    public void testValidateShuffledDp_unsetPrivacyDependencySetFails() {
        assertThat(
                        RegistryValidator.validateShuffledDp(
                                SHUFFLED_DIFFERENTIAL_PRIVACY,
                                ShuffledDifferentialPrivacyConfig.newBuilder()
                                        .setPoissonMean(0.1)
                                        .build()))
                .isFalse();
    }

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_privateFleetwideOccurrenceCounts() {
        MetricDefinition metric = getMetricDefinition(OCCURRENCE);
        ReportDefinition report =
                getReportDefinition(FLEETWIDE_OCCURRENCE_COUNTS, SHUFFLED_DIFFERENTIAL_PRIVACY)
                        .toBuilder()
                        .setNumIndexPoints(1)
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
    public void testIsValidReportTypeAndPrivacyMechanism_supportedSystemProfileSelection() {
        MetricDefinition metric = getMetricDefinition(STRING);
        ReportDefinition report =
                getReportDefinition(STRING_COUNTS, DE_IDENTIFICATION).toBuilder()
                        .setSystemProfileSelection(REPORT_ALL)
                        .build();
        assertThat(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report)).isTrue();
    }

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_unsupportedSystemProfileSelection() {
        MetricDefinition metric = getMetricDefinition(STRING);
        ReportDefinition report =
                getReportDefinition(STRING_COUNTS, DE_IDENTIFICATION).toBuilder()
                        .setSystemProfileSelection(SELECT_FIRST)
                        .build();
        assertThat(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report))
                .isFalse();
    }

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_supportedSystemProfileField() {
        MetricDefinition metric = getMetricDefinition(STRING);
        ReportDefinition report =
                getReportDefinition(STRING_COUNTS, DE_IDENTIFICATION).toBuilder()
                        .addSystemProfileField(APP_VERSION)
                        .build();
        assertThat(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report)).isTrue();
    }

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_unsupportedSystemProfileField() {
        MetricDefinition metric = getMetricDefinition(STRING);
        ReportDefinition report =
                getReportDefinition(STRING_COUNTS, DE_IDENTIFICATION).toBuilder()
                        .addSystemProfileField(CHANNEL)
                        .build();
        assertThat(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report))
                .isFalse();
    }

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_experimentIdsSetFails() {
        MetricDefinition metric = getMetricDefinition(STRING);
        ReportDefinition report =
                getReportDefinition(STRING_COUNTS, DE_IDENTIFICATION).toBuilder()
                        .addExperimentId(1L)
                        .build();
        assertThat(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report))
                .isFalse();
    }

    @Test
    public void
            testIsValidReportTypeAndPrivacyMechanism_fleetwideOccurrenceCounts_validPoissonFields() {
        MetricDefinition metric = getMetricDefinition(OCCURRENCE);
        ReportDefinition report =
                getReportDefinition(FLEETWIDE_OCCURRENCE_COUNTS, SHUFFLED_DIFFERENTIAL_PRIVACY)
                        .toBuilder()
                        .setNumIndexPoints(1)
                        .setMinValue(1)
                        .setMaxValue(2)
                        .build();
        assertThat(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report)).isTrue();
    }

    @Test
    public void
            testIsValidReportTypeAndPrivacyMechanism_fleetwideOccurrenceCounts_zeroNumIndexPointsFails() {
        MetricDefinition metric = getMetricDefinition(OCCURRENCE);
        ReportDefinition report =
                getReportDefinition(FLEETWIDE_OCCURRENCE_COUNTS, SHUFFLED_DIFFERENTIAL_PRIVACY)
                        .toBuilder()
                        .setNumIndexPoints(0)
                        .setMinValue(1)
                        .setMaxValue(2)
                        .build();
        assertThat(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report))
                .isFalse();
    }

    @Test
    public void
            testIsValidReportTypeAndPrivacyMechanism_fleetwideOccurrenceCounts_stringSketchSetFails() {
        MetricDefinition metric = getMetricDefinition(OCCURRENCE);
        ReportDefinition report =
                getReportDefinition(FLEETWIDE_OCCURRENCE_COUNTS, SHUFFLED_DIFFERENTIAL_PRIVACY)
                        .toBuilder()
                        .setNumIndexPoints(1)
                        .setMinValue(1)
                        .setMaxValue(2)
                        .setStringSketchParams(StringSketchParameters.newBuilder().setNumHashes(1))
                        .build();
        assertThat(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report))
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

        report =
                getReportDefinition(FLEETWIDE_OCCURRENCE_COUNTS, SHUFFLED_DIFFERENTIAL_PRIVACY)
                        .toBuilder()
                        .setNumIndexPoints(1)
                        .build();

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

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_expeditedSendingFails() {
        MetricDefinition metric = getMetricDefinition(OCCURRENCE);
        ReportDefinition report =
                getReportDefinition(FLEETWIDE_OCCURRENCE_COUNTS, DE_IDENTIFICATION).toBuilder()
                        .setExpeditedSending(true)
                        .build();
        assertThat(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report))
                .isFalse();
    }

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_1dayReportingIntervalSupported() {
        MetricDefinition metric = getMetricDefinition(OCCURRENCE);
        ReportDefinition report =
                getReportDefinition(FLEETWIDE_OCCURRENCE_COUNTS, DE_IDENTIFICATION);

        expect.that(
                        RegistryValidator.isValidReportTypeAndPrivacyMechanism(
                                metric,
                                report.toBuilder()
                                        .setReportingInterval(REPORTING_INTERVAL_UNSET)
                                        .build()))
                .isFalse();
        expect.that(
                        RegistryValidator.isValidReportTypeAndPrivacyMechanism(
                                metric, report.toBuilder().setReportingInterval(HOURS_1).build()))
                .isFalse();
        expect.that(
                        RegistryValidator.isValidReportTypeAndPrivacyMechanism(
                                metric, report.toBuilder().setReportingInterval(DAYS_1).build()))
                .isTrue();
    }

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_exemptFromConsentFails() {
        MetricDefinition metric = getMetricDefinition(OCCURRENCE);
        ReportDefinition report =
                getReportDefinition(FLEETWIDE_OCCURRENCE_COUNTS, DE_IDENTIFICATION);

        expect.that(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report))
                .isTrue();
        expect.that(
                        RegistryValidator.isValidReportTypeAndPrivacyMechanism(
                                metric, report.toBuilder().setExemptFromConsent(true).build()))
                .isFalse();
    }

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_maxPrivateIndexTooLargeFails() {
        // Metric with a dimension with 1000 possible events.
        MetricDefinition metric =
                getMetricDefinition(OCCURRENCE).toBuilder()
                        .addMetricDimensions(getMetricDimension(999))
                        .build();

        // Report with 1/10th of Integer.MAX_VALUE the number of index points.
        ReportDefinition report =
                getReportDefinition(FLEETWIDE_OCCURRENCE_COUNTS, SHUFFLED_DIFFERENTIAL_PRIVACY)
                        .toBuilder()
                        .setNumIndexPoints(Integer.MAX_VALUE / 10)
                        .build();

        // Fails because 1000 * Integer.MAX_VALUE / 10 > Integer.MAX_VALUE
        assertThat(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report))
                .isFalse();
    }

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_reportReleaseStageNotSetSupported() {
        MetricDefinition metric =
                getMetricDefinition(OCCURRENCE).toBuilder()
                        .addMetricDimensions(getMetricDimension(999))
                        .build();
        ReportDefinition report =
                getReportDefinition(FLEETWIDE_OCCURRENCE_COUNTS, DE_IDENTIFICATION).toBuilder()
                        .setMaxReleaseStage(ReleaseStage.RELEASE_STAGE_NOT_SET)
                        .build();

        assertThat(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report)).isTrue();
    }

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_metricReleaseStageGreaterSupported() {
        MetricDefinition metric =
                getMetricDefinition(OCCURRENCE).toBuilder()
                        .setMetaData(
                                MetricDefinition.Metadata.newBuilder()
                                        .setMaxReleaseStage(ReleaseStage.GA))
                        .build();
        ReportDefinition report =
                getReportDefinition(FLEETWIDE_OCCURRENCE_COUNTS, DE_IDENTIFICATION).toBuilder()
                        .setMaxReleaseStage(ReleaseStage.DEBUG)
                        .build();

        assertThat(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report)).isTrue();
    }

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_metricReleaseStageEqualSupported() {
        MetricDefinition metric =
                getMetricDefinition(OCCURRENCE).toBuilder()
                        .setMetaData(
                                MetricDefinition.Metadata.newBuilder()
                                        .setMaxReleaseStage(ReleaseStage.GA))
                        .build();
        ReportDefinition report =
                getReportDefinition(FLEETWIDE_OCCURRENCE_COUNTS, DE_IDENTIFICATION).toBuilder()
                        .setMaxReleaseStage(ReleaseStage.GA)
                        .build();

        assertThat(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report)).isTrue();
    }

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_metricReleaseStageLessFails() {
        MetricDefinition metric =
                getMetricDefinition(OCCURRENCE).toBuilder()
                        .setMetaData(
                                MetricDefinition.Metadata.newBuilder()
                                        .setMaxReleaseStage(ReleaseStage.DEBUG))
                        .build();
        ReportDefinition report =
                getReportDefinition(FLEETWIDE_OCCURRENCE_COUNTS, DE_IDENTIFICATION).toBuilder()
                        .setMaxReleaseStage(ReleaseStage.GA)
                        .build();

        assertThat(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report))
                .isFalse();
    }

    @Test
    public void testIsValidReportTypeAndPrivacyMechanism_invalidShuffledDpConfigFails() {
        MetricDefinition metric = getMetricDefinition(OCCURRENCE).toBuilder().build();
        ReportDefinition report =
                getReportDefinition(FLEETWIDE_OCCURRENCE_COUNTS, SHUFFLED_DIFFERENTIAL_PRIVACY)
                        .toBuilder()
                        .setShuffledDp(
                                ShuffledDifferentialPrivacyConfig.newBuilder()
                                        .setPoissonMean(-0.1)
                                        .setDevicePrivacyDependencySet(
                                                DevicePrivacyDependencySet.V1)
                                        .build())
                        .build();

        assertThat(RegistryValidator.isValidReportTypeAndPrivacyMechanism(metric, report))
                .isFalse();
    }

    private static MetricDefinition getMetricDefinition(MetricType metricType) {
        return MetricDefinition.newBuilder()
                .setMetricType(metricType)
                .setMetaData(Metadata.newBuilder().setMaxReleaseStage(ReleaseStage.GA))
                .build();
    }

    private static ReportDefinition getReportDefinition(
            ReportType reportType, PrivacyMechanism privacyMechanism) {
        ReportDefinition.Builder report =
                ReportDefinition.newBuilder()
                        .setReportType(reportType)
                        .setPrivacyMechanism(privacyMechanism)
                        .setReportingInterval(DAYS_1)
                        .setSystemProfileSelection(REPORT_ALL)
                        .setMaxReleaseStage(ReleaseStage.DEBUG);
        if (privacyMechanism.equals(SHUFFLED_DIFFERENTIAL_PRIVACY)) {
            report.setShuffledDp(
                    ShuffledDifferentialPrivacyConfig.newBuilder()
                            .setPoissonMean(0.1)
                            .setDevicePrivacyDependencySet(DevicePrivacyDependencySet.V1));
        }
        return report.build();
    }

    private static MetricDimension getMetricDimension(int maxEventCode) {
        return MetricDimension.newBuilder().setMaxEventCode(maxEventCode).build();
    }

    private static MetricDimension getMetricDimension(Iterable<Integer> eventCodes) {
        MetricDimension.Builder dimension = MetricDimension.newBuilder();
        for (Integer eventCode : eventCodes) {
            dimension.putEventCodes(eventCode, "UNUSED");
        }
        return dimension.build();
    }
}
