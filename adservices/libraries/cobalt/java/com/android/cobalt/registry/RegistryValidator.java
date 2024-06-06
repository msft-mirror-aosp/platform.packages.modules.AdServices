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

import com.android.internal.annotations.VisibleForTesting;

import com.google.cobalt.MetricDefinition;
import com.google.cobalt.MetricDefinition.MetricType;
import com.google.cobalt.ReportDefinition;
import com.google.cobalt.ReportDefinition.PrivacyMechanism;
import com.google.cobalt.ReportDefinition.ReportType;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/** Validates that Cobalt registry objects are valid and supported by the Cobalt client. */
public final class RegistryValidator {
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
            return false;
        }

        if (!validatePrivacyMechanism(report.getReportType(), report.getPrivacyMechanism())) {
            return false;
        }

        // TODO(b/343722587): Add remaining validations from the Cobalt config validator. This
        // includes:
        //   * integer buckets
        //   * poisson fields for different privacy mechanisms
        //   * min and max values for different privacy mechansims and report types
        //   * max count (is unset)
        //   * local aggregation period (is WINDOW_DAYS_1)
        //   * local aggregation procedure (is unset)
        //   * local aggregation procedure percentile n (is unset)
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

    private RegistryValidator() {}
}