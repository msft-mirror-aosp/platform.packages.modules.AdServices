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
import static com.google.cobalt.ReleaseStage.RELEASE_STAGE_NOT_SET;
import static com.google.cobalt.ReportDefinition.LocalAggregationProcedure.LOCAL_AGGREGATION_PROCEDURE_UNSET;
import static com.google.cobalt.ReportDefinition.PrivacyMechanism.DE_IDENTIFICATION;
import static com.google.cobalt.ReportDefinition.PrivacyMechanism.SHUFFLED_DIFFERENTIAL_PRIVACY;
import static com.google.cobalt.ReportDefinition.ReportType.FLEETWIDE_OCCURRENCE_COUNTS;
import static com.google.cobalt.ReportDefinition.ReportType.STRING_COUNTS;
import static com.google.cobalt.ReportDefinition.ReportingInterval.DAYS_1;
import static com.google.cobalt.SystemProfileSelectionPolicy.REPORT_ALL;

import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import com.google.cobalt.IntegerBuckets;
import com.google.cobalt.MetricDefinition;
import com.google.cobalt.MetricDefinition.MetricDimension;
import com.google.cobalt.MetricDefinition.MetricType;
import com.google.cobalt.ReleaseStage;
import com.google.cobalt.ReportDefinition;
import com.google.cobalt.ReportDefinition.LocalAggregationProcedure;
import com.google.cobalt.ReportDefinition.PrivacyMechanism;
import com.google.cobalt.ReportDefinition.ReportType;
import com.google.cobalt.ReportDefinition.ReportingInterval;
import com.google.cobalt.ReportDefinition.ShuffledDifferentialPrivacyConfig;
import com.google.cobalt.StringSketchParameters;
import com.google.cobalt.SystemProfileField;
import com.google.cobalt.SystemProfileSelectionPolicy;
import com.google.cobalt.WindowSize;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.List;
import java.util.Locale;

/** Validates that Cobalt registry objects are valid and supported by the Cobalt client. */
public final class RegistryValidator {
    private static final String TAG = "cobalt.registry";
    private static final ImmutableMap<MetricType, ImmutableSet<ReportType>> ALLOWED_REPORT_TYPES =
            ImmutableMap.of(
                    OCCURRENCE, ImmutableSet.of(FLEETWIDE_OCCURRENCE_COUNTS),
                    STRING, ImmutableSet.of(STRING_COUNTS));

    private static final ImmutableMap<ReportType, ImmutableSet<PrivacyMechanism>>
            ALLOWED_PRIVACY_MECHANSISMS =
                    ImmutableMap.of(
                            FLEETWIDE_OCCURRENCE_COUNTS,
                            ImmutableSet.of(DE_IDENTIFICATION, SHUFFLED_DIFFERENTIAL_PRIVACY),
                            STRING_COUNTS,
                            ImmutableSet.of(DE_IDENTIFICATION));

    /**
     * Checks that two metrics have equivalent dimensions and therefore private reports can be
     * merged.
     *
     * @return true if the dimensions are equivalent
     */
    public static boolean dimensionsAreEquivalent(
            Iterable<MetricDimension> dimensions1, Iterable<MetricDimension> dimensions2) {
        return getMaxEventCodes(dimensions1).equals(getMaxEventCodes(dimensions2))
                && getEventCodes(dimensions1).equals(getEventCodes(dimensions2));
    }

    /**
     * Checks that an input metric and report combination is valid and supported by the Cobalt
     * implementation.
     *
     * @param metric the metric being validated
     * @param report the report being validated
     * @return true if metric and report are a valid and supported combination
     */
    public static boolean isValid(MetricDefinition metric, ReportDefinition report) {
        if (!validateReportType(metric.getMetricType(), report.getReportType())) {
            logValidationFailure(
                    "Metric type (%s) and report type (%s) failed validation",
                    metric.getMetricType(), report.getReportType());
            return false;
        }

        if (!validatePrivacyMechanism(report.getReportType(), report.getPrivacyMechanism())) {
            logValidationFailure(
                    "Report type (%s) and privacy mechanism (%s) failed validation",
                    report.getReportType(), report.getPrivacyMechanism());
            return false;
        }

        if (!validateIntegerBuckets(report.getIntBuckets())) {
            logValidationFailure("Integer buckets failed validation");
            return false;
        }

        if (!validateSystemProfileSelection(report.getSystemProfileSelection())) {
            logValidationFailure(
                    "System profile selection policy (%s) failed validation",
                    report.getSystemProfileSelection());
            return false;
        }

        if (!validateSystemProfileFields(report.getSystemProfileFieldList())) {
            logValidationFailure(
                    "System profile fields (%s) failed validation",
                    report.getSystemProfileFieldList());
            return false;
        }

        if (!validateExperimentIds(report.getExperimentIdList())) {
            logValidationFailure(
                    "Experiment ids (%s) failed validation", report.getExperimentIdList());
            return false;
        }

        if (!validatePoissonFields(
                report.getReportType(),
                report.getPrivacyMechanism(),
                report.getNumIndexPoints(),
                report.getStringSketchParams())) {
            logValidationFailure("Poisson fields failed validation");
            return false;
        }

        if (!validateMinAndMaxValues(
                report.getReportType(),
                report.getPrivacyMechanism(),
                report.getMinValue(),
                report.getMaxValue())) {
            logValidationFailure(
                    "Report type (%s), privacy mechanism (%s), min value (%d), and max value (%d)"
                            + " failed validation",
                    report.getReportType(),
                    report.getPrivacyMechanism(),
                    report.getMinValue(),
                    report.getMaxValue());
            return false;
        }

        if (!validateMaxCount(report.getMaxCount())) {
            logValidationFailure("Max count (%d) failed validation", report.getMaxCount());
            return false;
        }

        if (!validateLocalAggregationPeriod(report.getLocalAggregationPeriod())) {
            logValidationFailure(
                    "Local aggregation period (%s) failed validation",
                    report.getLocalAggregationPeriod());
            return false;
        }

        if (!validateLocalAggregationProcedure(report.getLocalAggregationProcedure())) {
            logValidationFailure(
                    "Local aggregation procedure (%s) failed validation",
                    report.getLocalAggregationProcedure());
            return false;
        }

        if (!validateLocalAggregationPercentileN(
                report.getLocalAggregationProcedurePercentileN())) {
            logValidationFailure(
                    "Local aggregation procedure percentile N (%d) failed validation",
                    report.getLocalAggregationProcedurePercentileN());
            return false;
        }

        if (!validateExpeditedSending(report.getExpeditedSending())) {
            logValidationFailure(
                    "Expedited sending (%b) failed validation", report.getExpeditedSending());
            return false;
        }

        if (!validateReportingInterval(report.getReportingInterval())) {
            logValidationFailure(
                    "Reporting interval (%s) failed validation", report.getReportingInterval());
            return false;
        }

        if (!validateExemptFromConsent(report.getExemptFromConsent())) {
            logValidationFailure(
                    "Exempt from consent (%b) failed validation", report.getExemptFromConsent());
            return false;
        }

        if (!validateMaxPrivateIndex(
                report.getReportType(),
                report.getPrivacyMechanism(),
                metric.getMetricDimensionsList(),
                report.getNumIndexPoints())) {
            logValidationFailure("Max private index failed validation");
            return false;
        }

        if (!validateMaxReleaseStages(
                metric.getMetaData().getMaxReleaseStage(), report.getMaxReleaseStage())) {
            logValidationFailure(
                    "Metric max release stage (%s) and report max release stage (%s) failed"
                            + " validation",
                    metric.getMetaData().getMaxReleaseStage(), report.getMaxReleaseStage());
            return false;
        }

        if (!validateShuffledDp(report.getPrivacyMechanism(), report.getShuffledDp())) {
            logValidationFailure("Shuffled differential privacy config failed validation");
            return false;
        }

        return true;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static boolean validateReportType(MetricType metricType, ReportType reportType) {
        ImmutableSet<ReportType> allowedReportTypes = ALLOWED_REPORT_TYPES.get(metricType);
        if (allowedReportTypes == null) {
            return false;
        }

        return allowedReportTypes.contains(reportType);
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static boolean validatePrivacyMechanism(
            ReportType reportType, PrivacyMechanism privacyMechanism) {
        ImmutableSet<PrivacyMechanism> allowedPrivacyMechanisms =
                ALLOWED_PRIVACY_MECHANSISMS.get(reportType);
        if (allowedPrivacyMechanisms == null) {
            return false;
        }

        return allowedPrivacyMechanisms.contains(privacyMechanism);
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static boolean validateIntegerBuckets(IntegerBuckets intBuckets) {
        return intBuckets.equals(IntegerBuckets.getDefaultInstance());
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static boolean validateMinAndMaxValues(
            ReportType reportType,
            PrivacyMechanism privacyMechanism,
            long minValue,
            long maxValue) {
        if (reportType.equals(FLEETWIDE_OCCURRENCE_COUNTS)
                && privacyMechanism.equals(SHUFFLED_DIFFERENTIAL_PRIVACY)) {
            return minValue > 0 && maxValue >= minValue;
        }

        return minValue == 0 && maxValue == 0;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static boolean validateMaxCount(long maxCount) {
        return maxCount == 0;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static boolean validateLocalAggregationPeriod(WindowSize localAggregationPeriod) {
        // Some parts of the code always assume a local aggregation period of 1 day independent of
        // the values in reports, e.g. database clean. Supporting larger windows in reports must be
        // done with a careful check of existing code.
        return localAggregationPeriod.equals(WindowSize.UNSET);
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static boolean validateLocalAggregationProcedure(
            LocalAggregationProcedure localAggregationProcedure) {
        return localAggregationProcedure.equals(LOCAL_AGGREGATION_PROCEDURE_UNSET);
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static boolean validateLocalAggregationPercentileN(int localAggregationPercentileN) {
        return localAggregationPercentileN == 0;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static boolean validateExpeditedSending(boolean expeditedSending) {
        return !expeditedSending;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static boolean validateReportingInterval(ReportingInterval reportingInterval) {
        return reportingInterval.equals(DAYS_1);
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static boolean validateExemptFromConsent(boolean exemptFromConsent) {
        return !exemptFromConsent;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static boolean validateSystemProfileSelection(
            SystemProfileSelectionPolicy systemProfileSelection) {
        return systemProfileSelection.equals(REPORT_ALL);
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static boolean validateSystemProfileFields(List<SystemProfileField> systemProfileFields) {
        for (int i = 0; i < systemProfileFields.size(); ++i) {
            switch (systemProfileFields.get(i)) {
                case APP_VERSION:
                case ARCH:
                case BOARD_NAME:
                case OS:
                case SYSTEM_VERSION:
                    break;
                default:
                    return false;
            }
        }

        return true;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static boolean validateExperimentIds(List<Long> experimentIds) {
        return experimentIds.isEmpty();
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static boolean validatePoissonFields(
            ReportType reportType,
            PrivacyMechanism privacyMechanism,
            int numIndexPoints,
            StringSketchParameters stringSketchParams) {
        if (privacyMechanism.equals(DE_IDENTIFICATION)) {
            return true;
        }

        if (!reportType.equals(FLEETWIDE_OCCURRENCE_COUNTS)) {
            return false;
        }

        if (numIndexPoints == 0) {
            return false;
        }

        if (!stringSketchParams.equals(StringSketchParameters.getDefaultInstance())) {
            // Note, STRING_COUNTS and UNIQUE_DEVICE_STRING_COUNTS require StringSketchParams to be
            // checked and this function must be updated to account for report types.
            return false;
        }

        return true;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static boolean validateMaxPrivateIndex(
            ReportType reportType,
            PrivacyMechanism privacyMechanism,
            List<MetricDimension> metricDimensions,
            int numIndexPoints) {
        if (privacyMechanism.equals(DE_IDENTIFICATION)) {
            return true;
        }

        if (!reportType.equals(FLEETWIDE_OCCURRENCE_COUNTS)) {
            return false;
        }

        // Each event vector and value is mapped to an integer that represents a tuple in the
        // (event, value) space. This means the number of private indices is num events * num
        // values. Note, this does not account reports with other values that may need to be
        // encoded, e.g. histogram buckets.
        //
        // See {@link com.android.cobalt.observations.PrivateIndexCalculations#eventVectorToIndex}
        // and {@link com.android.cobalt.observations.PrivateIndexCalculations#doubleToIndex} for
        // details about how private index encoding is done.
        long numPrivateIndices = 1;
        for (int i = 0; i < metricDimensions.size(); ++i) {
            if (numPrivateIndices < 0) {
                //  Overflow occurred.
                return false;
            }

            MetricDimension d = metricDimensions.get(i);
            if (d.getMaxEventCode() != 0) {
                // Dimensions with a max event code cover the range [0, MAX].
                numPrivateIndices *= d.getMaxEventCode() + 1;
            } else {
                numPrivateIndices *= d.getEventCodesCount();
            }
        }

        numPrivateIndices *= numIndexPoints;
        if (numPrivateIndices < 0) {
            //  Overflow occurred.
            return false;
        }

        return numPrivateIndices < (long) Integer.MAX_VALUE;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static boolean validateMaxReleaseStages(
            ReleaseStage metricMaxReleaseStage, ReleaseStage reportMaxReleaseStage) {
        if (reportMaxReleaseStage.equals(RELEASE_STAGE_NOT_SET)) {
            return true;
        }

        return reportMaxReleaseStage.getNumber() <= metricMaxReleaseStage.getNumber();
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static boolean validateShuffledDp(
            PrivacyMechanism privacyMechanism, ShuffledDifferentialPrivacyConfig shuffledDpConfig) {
        if (!privacyMechanism.equals(SHUFFLED_DIFFERENTIAL_PRIVACY)) {
            return true;
        }

        return shuffledDpConfig.getPoissonMean() > 0
                && shuffledDpConfig
                        .getDevicePrivacyDependencySet()
                        .equals(ShuffledDifferentialPrivacyConfig.DevicePrivacyDependencySet.V1);
    }

    private static ImmutableList<Integer> getMaxEventCodes(Iterable<MetricDimension> dimensions) {
        ImmutableList.Builder<Integer> maxEventCodes = ImmutableList.builder();
        for (MetricDimension dimension : dimensions) {
            maxEventCodes.add(dimension.getMaxEventCode());
        }
        return maxEventCodes.build();
    }

    private static ImmutableList<ImmutableList<Integer>> getEventCodes(
            Iterable<MetricDimension> dimensions) {
        ImmutableList.Builder<ImmutableList<Integer>> eventCodes = ImmutableList.builder();
        for (MetricDimension dimension : dimensions) {
            // The order of event codes in a dimension doesn't matter.
            eventCodes.add(ImmutableList.sortedCopyOf(dimension.getEventCodesMap().keySet()));
        }

        return eventCodes.build();
    }

    private static void logValidationFailure(String format, Object... params) {
        if (Log.isLoggable(TAG, Log.WARN)) {
            Log.w(TAG, String.format(Locale.US, format, params));
        }
    }

    private RegistryValidator() {}
}
