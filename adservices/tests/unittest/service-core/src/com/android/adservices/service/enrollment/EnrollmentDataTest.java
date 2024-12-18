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

package com.android.adservices.service.enrollment;

import static com.android.adservices.service.enrollment.EnrollmentData.SEPARATOR;

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.service.proto.PrivacySandboxApi;
import com.android.adservices.shared.testing.EqualsTester;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Unit tests for {@link EnrollmentData} */
public final class EnrollmentDataTest extends AdServicesUnitTestCase {
    private static final String ENROLLMENT_ID = "1";
    private static final String ENROLLED_API_NAMES =
            "PRIVACY_SANDBOX_API_TOPICS PRIVACY_SANDBOX_API_PROTECTED_AUDIENCE TEST1";
    private static final ImmutableList<PrivacySandboxApi> ENROLLED_API_ENUMS =
            ImmutableList.of(
                    PrivacySandboxApi.PRIVACY_SANDBOX_API_TOPICS,
                    PrivacySandboxApi.PRIVACY_SANDBOX_API_PROTECTED_AUDIENCE,
                    PrivacySandboxApi.PRIVACY_SANDBOX_API_UNKNOWN);
    private static final ImmutableList<String> SDK_NAMES = ImmutableList.of("Admob");
    private static final ImmutableList<String> ATTRIBUTION_SOURCE_REGISTRATION_URLS =
            ImmutableList.of("source1.example.com", "source2.example.com");
    private static final ImmutableList<String> ATTRIBUTION_TRIGGER_REGISTRATION_URLS =
            ImmutableList.of("trigger1.example.com", "trigger2.example.com");
    private static final ImmutableList<String> ATTRIBUTION_REPORTING_REGISTRATION_URLS =
            ImmutableList.of("reporting1.example.com", "reporting2.example.com");
    private static final ImmutableList<String> REMARKETING_RESPONSE_BASED_REGISTRATION_URLS =
            ImmutableList.of("remarketing1.example.com", "remarketing2.example.com");
    private static final String ENCRYPTION_KEY_URL = "encryption1.example.com";

    private EnrollmentData createEnrollmentData() {
        return new EnrollmentData.Builder()
                .setEnrollmentId(ENROLLMENT_ID)
                .setEnrolledAPIs(ENROLLED_API_NAMES)
                .setSdkNames(SDK_NAMES)
                .setAttributionSourceRegistrationUrl(ATTRIBUTION_SOURCE_REGISTRATION_URLS)
                .setAttributionTriggerRegistrationUrl(ATTRIBUTION_TRIGGER_REGISTRATION_URLS)
                .setAttributionReportingUrl(ATTRIBUTION_REPORTING_REGISTRATION_URLS)
                .setRemarketingResponseBasedRegistrationUrl(
                        REMARKETING_RESPONSE_BASED_REGISTRATION_URLS)
                .setEncryptionKeyUrl(ENCRYPTION_KEY_URL)
                .build();
    }

    @Test
    public void testCreation() throws Exception {
        EnrollmentData enrollmentData = createEnrollmentData();

        expect.that(enrollmentData.getEnrollmentId()).isEqualTo(ENROLLMENT_ID);
        expect.that(enrollmentData.getEnrolledAPIs()).containsExactlyElementsIn(ENROLLED_API_ENUMS);
        expect.that(enrollmentData.getEnrolledAPIsString()).isEqualTo(ENROLLED_API_NAMES);
        expect.that(enrollmentData.getSdkNames()).containsExactlyElementsIn(SDK_NAMES);
        expect.that(enrollmentData.getAttributionSourceRegistrationUrl())
                .containsExactlyElementsIn(ATTRIBUTION_SOURCE_REGISTRATION_URLS);
        expect.that(enrollmentData.getAttributionTriggerRegistrationUrl())
                .containsExactlyElementsIn(ATTRIBUTION_TRIGGER_REGISTRATION_URLS);
        expect.that(enrollmentData.getAttributionReportingUrl())
                .containsExactlyElementsIn(ATTRIBUTION_REPORTING_REGISTRATION_URLS);
        expect.that(enrollmentData.getRemarketingResponseBasedRegistrationUrl())
                .containsExactlyElementsIn(REMARKETING_RESPONSE_BASED_REGISTRATION_URLS);
        expect.that(enrollmentData.getEncryptionKeyUrl()).isEqualTo(ENCRYPTION_KEY_URL);
    }

    @Test
    public void testCreationFromStrings() {
        EnrollmentData enrollmentData =
                new EnrollmentData.Builder()
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setEnrolledAPIs(ENROLLED_API_NAMES)
                        .setSdkNames(String.join(SEPARATOR, SDK_NAMES))
                        .setAttributionSourceRegistrationUrl(
                                String.join(SEPARATOR, ATTRIBUTION_SOURCE_REGISTRATION_URLS))
                        .setAttributionTriggerRegistrationUrl(
                                String.join(SEPARATOR, ATTRIBUTION_TRIGGER_REGISTRATION_URLS))
                        .setAttributionReportingUrl(
                                String.join(SEPARATOR, ATTRIBUTION_REPORTING_REGISTRATION_URLS))
                        .setRemarketingResponseBasedRegistrationUrl(
                                String.join(
                                        SEPARATOR, REMARKETING_RESPONSE_BASED_REGISTRATION_URLS))
                        .setEncryptionKeyUrl(ENCRYPTION_KEY_URL)
                        .build();

        expect.that(enrollmentData.getEnrollmentId()).isEqualTo(ENROLLMENT_ID);
        expect.that(enrollmentData.getEnrolledAPIs()).containsExactlyElementsIn(ENROLLED_API_ENUMS);
        expect.that(enrollmentData.getEnrolledAPIsString()).isEqualTo(ENROLLED_API_NAMES);
        expect.that(enrollmentData.getSdkNames()).containsExactlyElementsIn(SDK_NAMES);
        expect.that(enrollmentData.getAttributionSourceRegistrationUrl())
                .containsExactlyElementsIn(ATTRIBUTION_SOURCE_REGISTRATION_URLS);
        expect.that(enrollmentData.getAttributionTriggerRegistrationUrl())
                .containsExactlyElementsIn(ATTRIBUTION_TRIGGER_REGISTRATION_URLS);
        expect.that(enrollmentData.getAttributionReportingUrl())
                .containsExactlyElementsIn(ATTRIBUTION_REPORTING_REGISTRATION_URLS);
        expect.that(enrollmentData.getRemarketingResponseBasedRegistrationUrl())
                .containsExactlyElementsIn(REMARKETING_RESPONSE_BASED_REGISTRATION_URLS);
        expect.that(enrollmentData.getEncryptionKeyUrl()).isEqualTo(ENCRYPTION_KEY_URL);
    }

    @Test
    public void testSplitEnrollmentInputToList_emptyString() {
        assertThat(EnrollmentData.splitEnrollmentInputToList("")).isEmpty();
    }

    @Test
    public void testSplitEnrollmentInputToList_singleItem() {
        String item = "one.item";
        assertThat(EnrollmentData.splitEnrollmentInputToList(item)).containsExactly(item);
    }

    @Test
    public void testSplitEnrollmentInputToList_multipleItems() {
        List<String> items = Arrays.asList("first.item", "second.item");
        String itemListString = String.join(SEPARATOR, items);
        assertThat(EnrollmentData.splitEnrollmentInputToList(itemListString))
                .containsExactlyElementsIn(items);
    }

    @Test
    public void testDefaults() throws Exception {
        EnrollmentData enrollmentData = new EnrollmentData.Builder().build();
        expect.that(enrollmentData.getEnrollmentId()).isNull();
        expect.that(enrollmentData.getEnrolledAPIs()).hasSize(0);
        expect.that(enrollmentData.getEnrolledAPIsString()).isNull();
        expect.that(enrollmentData.getSdkNames()).hasSize(0);
        expect.that(enrollmentData.getAttributionSourceRegistrationUrl()).hasSize(0);
        expect.that(enrollmentData.getAttributionTriggerRegistrationUrl()).hasSize(0);
        expect.that(enrollmentData.getAttributionReportingUrl()).hasSize(0);
        expect.that(enrollmentData.getRemarketingResponseBasedRegistrationUrl()).hasSize(0);
        expect.that(enrollmentData.getEncryptionKeyUrl()).isNull();
    }

    @Test
    public void testEquals() {
        EnrollmentData e1 =
                new EnrollmentData.Builder()
                        .setEnrollmentId("2")
                        .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                        .setSdkNames("2sdk")
                        .setAttributionSourceRegistrationUrl(
                                Arrays.asList(
                                        "https://2test.com/source", "https://2test2.com/source"))
                        .setAttributionTriggerRegistrationUrl(
                                Arrays.asList("https://2test.com/trigger"))
                        .setAttributionReportingUrl(Arrays.asList("https://2test.com"))
                        .setRemarketingResponseBasedRegistrationUrl(
                                Arrays.asList("https://2test.com"))
                        .setEncryptionKeyUrl("https://2test.com/keys")
                        .build();
        EnrollmentData e2 =
                new EnrollmentData.Builder()
                        .setEnrollmentId("2")
                        .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                        .setSdkNames("2sdk")
                        .setAttributionSourceRegistrationUrl(
                                Arrays.asList(
                                        "https://2test.com/source", "https://2test2.com/source"))
                        .setAttributionTriggerRegistrationUrl(
                                Arrays.asList("https://2test.com/trigger"))
                        .setAttributionReportingUrl(Arrays.asList("https://2test.com"))
                        .setRemarketingResponseBasedRegistrationUrl(
                                Arrays.asList("https://2test.com"))
                        .setEncryptionKeyUrl("https://2test.com/keys")
                        .build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(e1, e2);
    }

    @Test
    public void testSetEnrolledAPIs_returnsUnknownAPI() {
        EnrollmentData e1 =
                new EnrollmentData.Builder()
                        .setEnrollmentId("2")
                        .setEnrolledAPIs("foo-inc")
                        .setSdkNames("2sdk")
                        .setAttributionSourceRegistrationUrl(
                                Arrays.asList(
                                        "https://2test.com/source", "https://2test2.com/source"))
                        .setAttributionTriggerRegistrationUrl(
                                Arrays.asList("https://2test.com/trigger"))
                        .setAttributionReportingUrl(Arrays.asList("https://2test.com"))
                        .setRemarketingResponseBasedRegistrationUrl(
                                Arrays.asList("https://2test.com"))
                        .setEncryptionKeyUrl("https://2test.com/keys")
                        .build();

        String enrolledAPIsString = e1.getEnrolledAPIsString();
        expect.that(enrolledAPIsString).isEqualTo("foo-inc");
        ImmutableList<PrivacySandboxApi> enrolledAPIUnknown =
                ImmutableList.of(PrivacySandboxApi.PRIVACY_SANDBOX_API_UNKNOWN);
        expect.that(e1.getEnrolledAPIs()).hasSize(1);
        expect.that(e1.getEnrolledAPIs()).containsExactlyElementsIn(enrolledAPIUnknown);
    }
}
