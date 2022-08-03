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

package com.android.adservices.data.enrollment;

import com.android.adservices.service.enrollment.EnrollmentData;

/** Interface for enrollment related data access operations. */
public interface IEnrollmentDao {

    /**
     * Returns the {@link EnrollmentData}.
     *
     * @param enrollmentId ID provided to the adtech during the enrollment process.
     * @return the EnrollmentData; Null in case of SQL failure
     */
    EnrollmentData getEnrollmentData(String enrollmentId);

    /**
     * Returns the {@link EnrollmentData} given measurement registration URLs.
     *
     * @param url could be source registration url or trigger registration url.
     * @return the EnrollmentData; Null in case of SQL failure.
     */
    EnrollmentData getEnrollmentDataGivenUrl(String url);

    /**
     * Returns the {@link EnrollmentData} given AdTech SDK Name.
     *
     * @param sdkName List of SDKs belonging to the same enrollment.
     * @return the EnrollmentData; Null in case of SQL failure
     */
    EnrollmentData getEnrollmentDataGivenSdkName(String sdkName);

    /**
     * Inserts {@link EnrollmentData} into DB table.
     *
     * @param enrollmentData the EnrollmentData to insert.
     */
    void insertEnrollmentData(EnrollmentData enrollmentData);

    /**
     * Deletes {@link EnrollmentData} from DB table.
     *
     * @param enrollmentId ID provided to the adtech at the end of the enrollment process.
     */
    void deleteEnrollmentData(String enrollmentId);

    /** Deletes the whole EnrollmentData table. */
    void deleteEnrollmentDataTable();
}
