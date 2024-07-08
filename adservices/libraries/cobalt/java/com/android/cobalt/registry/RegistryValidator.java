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
import static com.google.cobalt.ReportDefinition.PrivacyMechanism.DE_IDENTIFICATION;
import static com.google.cobalt.ReportDefinition.PrivacyMechanism.SHUFFLED_DIFFERENTIAL_PRIVACY;
import static com.google.cobalt.ReportDefinition.ReportType.FLEETWIDE_OCCURRENCE_COUNTS;
import static com.google.cobalt.ReportDefinition.ReportType.STRING_COUNTS;

import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import com.google.cobalt.IntegerBuckets;
import com.google.cobalt.MetricDefinition;
import com.google.cobalt.MetricDefinition.MetricType;
import com.google.cobalt.ReportDefinition;
import com.google.cobalt.ReportDefinition.LocalAggregationProcedure;
import com.google.cobalt.ReportDefinition.PrivacyMechanism;
import com.google.cobalt.ReportDefinition.ReportType;
import com.google.cobalt.WindowSize;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

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
     * Checks that an input metric and report combination is valid and supported by the Cobalt
     * implementation.
     *
     * @param metric the metric being validated
     * @param report the report being validated
     * @return true if metric and report are a valid and supported combination
     */
    public static boolean isValidReportTypeAndPrivacyMechanism(
            MetricDefinition metric, ReportDefinition report) {
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

        // TODO(b/343722587): Add remaining validations from the Cobalt config validator. This
        // includes:
        //   * poisson fields for different privacy mechanisms
        //   * expedited sending (is unset)
        //   * max release stage (set and report's is less than metric's)
        //   * reporting interval (must be DAYS_1)
        //   * exempt from conset (is unset)
        //   * max private index (fits in int32)
        //   * system profile selection (is REPORT_ALL)
        //   * system profile fields (is one of the supported values and experiment ids are empty)
        //   * report specific validations

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

    private static void logValidationFailure(String format, Object... params) {
        if (Log.isLoggable(TAG, Log.WARN)) {
            Log.w(TAG, String.format(Locale.US, format, params));
        }
    }

    private RegistryValidator() {}
}
