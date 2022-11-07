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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.net.Uri;

import androidx.test.filters.SmallTest;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.enrollment.EnrollmentData;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

@SmallTest
public final class EnrollmentTest {

    private static final Uri REGISTRATION_URI = Uri.parse("https://ad-tech.test/register");
    private static final String ENROLLMENT_ID = "enrollment-id";
    private static final EnrollmentData ENROLLMENT = new EnrollmentData.Builder()
            .setEnrollmentId("enrollment-id")
            .setAttributionReportingUrl(List.of("https://origin1.test", "https://origin2.test"))
            .build();
    @Mock private EnrollmentDao mEnrollmentDao;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testMaybeGetEnrollmentId_enrollmentDataNull() {
        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(eq(REGISTRATION_URI)))
                .thenReturn(null);
        assertEquals(
                Optional.empty(),
                Enrollment.maybeGetEnrollmentId(REGISTRATION_URI, mEnrollmentDao));
    }

    @Test
    public void testMaybeGetEnrollmentId_enrollmentDataNonNull() {
        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(eq(REGISTRATION_URI)))
                .thenReturn(ENROLLMENT);
        assertEquals(
                Optional.of(ENROLLMENT.getEnrollmentId()),
                Enrollment.maybeGetEnrollmentId(REGISTRATION_URI, mEnrollmentDao));
    }

    @Test
    public void testMaybeGetReportingOrigin_success() {
        when(mEnrollmentDao.getEnrollmentData(eq(ENROLLMENT_ID))).thenReturn(ENROLLMENT);
        assertEquals(
                Optional.of(Uri.parse("https://origin1.test")),
                Enrollment.maybeGetReportingOrigin(ENROLLMENT_ID, mEnrollmentDao));
    }

    @Test
    public void testMaybeGetReportingOrigin_enrollmentDataNull() {
        when(mEnrollmentDao.getEnrollmentData(eq(ENROLLMENT_ID))).thenReturn(null);
        assertEquals(
                Optional.empty(),
                Enrollment.maybeGetReportingOrigin(ENROLLMENT_ID, mEnrollmentDao));
    }

    @Test
    public void testMaybeGetReportingOrigin_attributionReportingUrlEmpty() {
        EnrollmentData enrollment = new EnrollmentData.Builder().build();
        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(eq(Uri.parse(ENROLLMENT_ID))))
                .thenReturn(enrollment);
        assertEquals(
                Optional.empty(),
                Enrollment.maybeGetReportingOrigin(ENROLLMENT_ID, mEnrollmentDao));
    }
}
