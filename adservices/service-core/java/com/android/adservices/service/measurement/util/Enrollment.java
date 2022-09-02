/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.measurement.util;

import android.net.Uri;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.enrollment.EnrollmentData;

import java.util.Optional;

/** Enrollment utilities for measurement. */
public final class Enrollment {

    private Enrollment() { }

    /**
     * Returns an {@code Optional<String>} of the ad-tech enrollment record ID.
     *
     * @param registrationUri the ad-tech URL used to register a source or trigger.
     * @param enrollmentDao an instance of {@code EnrollmentDao}.
     */
    public static Optional<String> maybeGetEnrollmentId(Uri registrationUri,
            EnrollmentDao enrollmentDao) {
        Uri uriWithoutParams = registrationUri.buildUpon().clearQuery().fragment(null).build();
        EnrollmentData enrollmentData = enrollmentDao.getEnrollmentDataFromMeasurementUrl(
                uriWithoutParams.toString());
        if (enrollmentData != null && enrollmentData.getEnrollmentId() != null) {
            return Optional.of(enrollmentData.getEnrollmentId());
        }
        return Optional.empty();
    }

    /**
     * Returns an {@code Optional<Uri>} of the ad-tech server URL that accepts attribution reports.
     *
     * @param enrollmentId the enrollment record ID.
     * @param enrollmentDao an instance of {@code EnrollmentDao}.
     */
    public static Optional<Uri> maybeGetReportingOrigin(String enrollmentId,
            EnrollmentDao enrollmentDao) {
        EnrollmentData enrollmentData = enrollmentDao.getEnrollmentData(enrollmentId);
        if (enrollmentData != null && enrollmentData.getAttributionReportingUrl().size() > 0) {
            return Optional.of(Uri.parse(enrollmentData.getAttributionReportingUrl().get(0)));
        }
        return Optional.empty();
    }
}
