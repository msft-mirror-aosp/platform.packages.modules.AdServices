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

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;

import com.android.cobalt.data.ObservationGenerator;
import com.android.cobalt.domain.Project;
import com.android.cobalt.system.SystemData;

import com.google.cobalt.MetricDefinition;
import com.google.cobalt.ReportDefinition;
import com.google.cobalt.ReportDefinition.ReportType;

import java.security.SecureRandom;

/** Creates {@link ObservationGenerator} instances for reports. */
public final class ObservationGeneratorFactory {
    private final Project mProject;
    private final SystemData mSystemData;
    private final PrivacyGenerator mPrivacyGenerator;
    private final SecureRandom mSecureRandom;

    public ObservationGeneratorFactory(
            @NonNull Project project,
            @NonNull SystemData systemData,
            @NonNull PrivacyGenerator privacyGenerator,
            @NonNull SecureRandom secureRandom) {
        mProject = requireNonNull(project);
        mSystemData = requireNonNull(systemData);
        mPrivacyGenerator = requireNonNull(privacyGenerator);
        mSecureRandom = requireNonNull(secureRandom);
    }

    /**
     * Creates an {@link ObservationGenerator} instance for a report.
     *
     * <p>Note, only FLEETWIDE_OCCURRENCE_COUNTS are supported.
     *
     * @param metric the metric observations are being generated for
     * @param report the metric observations are being generated for
     * @return the {@link ObservationGenerator} required for the provided report
     */
    public ObservationGenerator getObservationGenerator(
            MetricDefinition metric, ReportDefinition report) {
        if (report.getReportType() != ReportType.FLEETWIDE_OCCURRENCE_COUNTS) {
            throw new AssertionError(
                    "Unrecognized or unsupported report type: " + report.getReportTypeValue());
        }

        switch (report.getPrivacyLevel()) {
            case NO_ADDED_PRIVACY:
                return new NonPrivateObservationGenerator(
                        mSecureRandom,
                        new IntegerEncoder(mSecureRandom),
                        mProject.getCustomerId(),
                        mProject.getProjectId(),
                        metric.getId(),
                        report);
            case LOW_PRIVACY:
            case MEDIUM_PRIVACY:
            case HIGH_PRIVACY:
                return new PrivateObservationGenerator(
                        mSystemData,
                        mPrivacyGenerator,
                        mSecureRandom,
                        new PrivateIntegerEncoder(mSecureRandom, metric, report),
                        mProject.getCustomerId(),
                        mProject.getProjectId(),
                        metric,
                        report);
            default:
                throw new AssertionError(
                        "Unknown or unset privacy level: " + report.getPrivacyLevelValue());
        }
    }
}
