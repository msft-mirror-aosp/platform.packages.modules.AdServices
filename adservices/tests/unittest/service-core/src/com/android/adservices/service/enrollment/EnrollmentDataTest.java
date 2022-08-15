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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;


import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.Arrays;

/** Unit tests for {@link EnrollmentData} */
@SmallTest
public final class EnrollmentDataTest {

    private EnrollmentData createEnrollmentData() {
        return new EnrollmentData.Builder()
                .setEnrollmentId("1")
                .setCompanyId("100")
                .setSdkNames(Arrays.asList("Admob"))
                .setAttributionSourceRegistrationUrl(
                        Arrays.asList("source1.example.com", "source2.example.com"))
                .setAttributionTriggerRegistrationUrl(
                        Arrays.asList("trigger1.example.com", "trigger2.example.com"))
                .setAttributionReportingUrl(
                        Arrays.asList("reporting1.example.com", "reporting2.example.com"))
                .setRemarketingResponseBasedRegistrationUrl(
                        Arrays.asList("remarketing1.example.com", "remarketing2.example.com"))
                .setEncryptionKeyUrl(
                        Arrays.asList("encryption1.example.com", "encryption2.example.com"))
                .build();
    }

    @Test
    public void testCreation() throws Exception {
        EnrollmentData enrollmentData = createEnrollmentData();
        assertEquals("1", enrollmentData.getEnrollmentId());
        assertEquals("100", enrollmentData.getCompanyId());
        assertThat(enrollmentData.getSdkNames()).containsExactly("Admob");
        assertThat(enrollmentData.getAttributionSourceRegistrationUrl())
                .containsExactly("source1.example.com", "source2.example.com");
        assertThat(enrollmentData.getAttributionTriggerRegistrationUrl())
                .containsExactly("trigger1.example.com", "trigger2.example.com");
        assertThat(enrollmentData.getAttributionReportingUrl())
                .containsExactly("reporting1.example.com", "reporting2.example.com");
        assertThat(enrollmentData.getRemarketingResponseBasedRegistrationUrl())
                .containsExactly("remarketing1.example.com", "remarketing2.example.com");
        assertThat(enrollmentData.getEncryptionKeyUrl())
                .containsExactly("encryption1.example.com", "encryption2.example.com");
    }

    @Test
    public void testDefaults() throws Exception {
        EnrollmentData enrollmentData = new EnrollmentData.Builder().build();
        assertNull(enrollmentData.getEnrollmentId());
        assertNull(enrollmentData.getCompanyId());
        assertEquals(enrollmentData.getSdkNames().size(), 0);
        assertEquals(enrollmentData.getAttributionSourceRegistrationUrl().size(), 0);
        assertEquals(enrollmentData.getAttributionTriggerRegistrationUrl().size(), 0);
        assertEquals(enrollmentData.getAttributionReportingUrl().size(), 0);
        assertEquals(enrollmentData.getRemarketingResponseBasedRegistrationUrl().size(), 0);
        assertEquals(enrollmentData.getEncryptionKeyUrl().size(), 0);
    }

    @Test
    public void testEquals() {
        EnrollmentData e1 =
                new EnrollmentData.Builder()
                        .setEnrollmentId("2")
                        .setCompanyId("1002")
                        .setSdkNames("2sdk")
                        .setAttributionSourceRegistrationUrl(
                                Arrays.asList(
                                        "https://2test.com/source", "https://2test2.com/source"))
                        .setAttributionTriggerRegistrationUrl(
                                Arrays.asList("https://2test.com/trigger"))
                        .setAttributionReportingUrl(Arrays.asList("https://2test.com"))
                        .setRemarketingResponseBasedRegistrationUrl(
                                Arrays.asList("https://2test.com"))
                        .setEncryptionKeyUrl(Arrays.asList("https://2test.com/keys"))
                        .build();
        EnrollmentData e2 =
                new EnrollmentData.Builder()
                        .setEnrollmentId("2")
                        .setCompanyId("1002")
                        .setSdkNames("2sdk")
                        .setAttributionSourceRegistrationUrl(
                                Arrays.asList(
                                        "https://2test.com/source", "https://2test2.com/source"))
                        .setAttributionTriggerRegistrationUrl(
                                Arrays.asList("https://2test.com/trigger"))
                        .setAttributionReportingUrl(Arrays.asList("https://2test.com"))
                        .setRemarketingResponseBasedRegistrationUrl(
                                Arrays.asList("https://2test.com"))
                        .setEncryptionKeyUrl(Arrays.asList("https://2test.com/keys"))
                        .build();
        assertEquals(e1, e2);
    }
}
