/*
 * Copyright (C) 2021 The Android Open Source Project
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

import java.util.Arrays;
import java.util.List;

/** Container class for pre-enrolled Adtech enrollment data to aid testing. */
final class PreEnrolledAdTechForTest {

    static List<EnrollmentData> getList() {
        return Arrays.asList(SIMPLE_ENROLLMENT, ONE_SDK_MULTIPLE_URLS, SECOND_ENROLLMENT);
    }

    private static final EnrollmentData SIMPLE_ENROLLMENT =
            new EnrollmentData.Builder()
                    .setEnrollmentId("E1")
                    .setCompanyId("1001")
                    .setSdkNames("sdk1")
                    .setAttributionSourceRegistrationUrl(Arrays.asList("https://test.com/source"))
                    .setAttributionTriggerRegistrationUrl(Arrays.asList("https://test.com/trigger"))
                    .setAttributionReportingUrl(Arrays.asList("https://test.com"))
                    .setRemarketingResponseBasedRegistrationUrl(Arrays.asList("https://test.com"))
                    .setEncryptionKeyUrl(Arrays.asList("https://test.com/keys"))
                    .build();

    private static final EnrollmentData ONE_SDK_MULTIPLE_URLS =
            new EnrollmentData.Builder()
                    .setEnrollmentId("E2")
                    .setCompanyId("1002")
                    .setSdkNames("sdk2")
                    .setAttributionSourceRegistrationUrl(
                            Arrays.asList(
                                    "https://test2.com/source", "https://testtest.com/source"))
                    .setAttributionTriggerRegistrationUrl(
                            Arrays.asList("https://test2.com/trigger"))
                    .setAttributionReportingUrl(Arrays.asList("https://test2.com"))
                    .setRemarketingResponseBasedRegistrationUrl(Arrays.asList("https://test2.com"))
                    .setEncryptionKeyUrl(Arrays.asList("https://test2.com/keys"))
                    .build();

    private static final EnrollmentData SECOND_ENROLLMENT =
            new EnrollmentData.Builder()
                    .setEnrollmentId("E3")
                    .setCompanyId("1001")
                    .setSdkNames("sdk3")
                    .setAttributionSourceRegistrationUrl(Arrays.asList("https://test3.com/source"))
                    .setAttributionTriggerRegistrationUrl(
                            Arrays.asList("https://test3.com/trigger"))
                    .setAttributionReportingUrl(Arrays.asList("https://test3.com"))
                    .setRemarketingResponseBasedRegistrationUrl(Arrays.asList("https://test3.com"))
                    .setEncryptionKeyUrl(Arrays.asList("https://test3.com/keys"))
                    .build();
}
