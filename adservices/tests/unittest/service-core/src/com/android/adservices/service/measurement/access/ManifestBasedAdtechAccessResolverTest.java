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
package com.android.adservices.service.measurement.access;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AppManifestConfigHelper;
import com.android.adservices.service.enrollment.EnrollmentFixture;
import com.android.adservices.service.measurement.WebUtil;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.quality.Strictness;

@RunWith(MockitoJUnitRunner.class)
public class ManifestBasedAdtechAccessResolverTest {

    private static final String ERROR_MESSAGE = "Caller is not authorized.";
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String PACKAGE = "package";
    private static final Uri ENROLLED_AD_TECH_URL = WebUtil.validUri("https://test.test/source");
    private static final Uri ENROLLED_AD_TECH_URL_WITH_QUERY =
            WebUtil.validUri("https://test.test/source?hello");
    private static final Uri ENROLLED_AD_TECH_URL_WITH_QUERY_FRAGMENT =
            WebUtil.validUri("https://test.test/source?hello#now");
    private static final Uri UNENROLLED_AD_TECH_URL =
            WebUtil.validUri("https://notatest.test/source");
    private static final Uri EMPTY_URL = Uri.parse("");
    private ManifestBasedAdtechAccessResolver mClassUnderTest;
    private EnrollmentDao mMockEnrollmentDao;
    @Mock private Flags mFlags;
    public MockitoSession mMockitoSession;

    @Before
    public void setup() {
        mMockEnrollmentDao = mock(EnrollmentDao.class);
        doReturn(EnrollmentFixture.getValidEnrollment())
                .when(mMockEnrollmentDao)
                .getEnrollmentDataFromMeasurementUrl(eq(ENROLLED_AD_TECH_URL));
        mMockitoSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(AppManifestConfigHelper.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();
        when(mFlags.isEnrollmentBlocklisted(any())).thenReturn(false);
    }

    @After
    public void teardown() {
        mMockitoSession.finishMocking();
    }

    @Test
    public void isAllowedNoPermissionFails() {
        mClassUnderTest =
                new ManifestBasedAdtechAccessResolver(
                        mMockEnrollmentDao, mFlags, PACKAGE, ENROLLED_AD_TECH_URL);
        doReturn(false).when(mFlags).isDisableMeasurementEnrollmentCheck();
        when(AppManifestConfigHelper.isAllowedAttributionAccess(any(), any(), any()))
                .thenReturn(false);
        assertFalse(mClassUnderTest.isAllowed(CONTEXT));
    }

    @Test
    public void isAllowedNotEnrolledFails() {
        mClassUnderTest =
                new ManifestBasedAdtechAccessResolver(
                        mMockEnrollmentDao, mFlags, PACKAGE, UNENROLLED_AD_TECH_URL);
        doReturn(false).when(mFlags).isDisableMeasurementEnrollmentCheck();
        assertFalse(mClassUnderTest.isAllowed(CONTEXT));
    }

    @Test
    public void isAllowedNullUrlFails() {
        mClassUnderTest =
                new ManifestBasedAdtechAccessResolver(mMockEnrollmentDao, mFlags, PACKAGE, null);
        doReturn(false).when(mFlags).isDisableMeasurementEnrollmentCheck();
        assertFalse(mClassUnderTest.isAllowed(CONTEXT));
    }

    @Test
    public void isAllowedEmptyUrlFails() {
        mClassUnderTest =
                new ManifestBasedAdtechAccessResolver(
                        mMockEnrollmentDao, mFlags, PACKAGE, EMPTY_URL);
        doReturn(false).when(mFlags).isDisableMeasurementEnrollmentCheck();
        assertFalse(mClassUnderTest.isAllowed(CONTEXT));
    }

    @Test
    public void isAllowedSuccess() {
        mClassUnderTest =
                new ManifestBasedAdtechAccessResolver(
                        mMockEnrollmentDao, mFlags, PACKAGE, ENROLLED_AD_TECH_URL);
        doReturn(false).when(mFlags).isDisableMeasurementEnrollmentCheck();
        when(AppManifestConfigHelper.isAllowedAttributionAccess(any(), any(), any()))
                .thenReturn(true);
        assertTrue(mClassUnderTest.isAllowed(CONTEXT));
    }

    @Test
    public void isAllowedSuccessClearsQuery() {
        mClassUnderTest =
                new ManifestBasedAdtechAccessResolver(
                        mMockEnrollmentDao, mFlags, PACKAGE, ENROLLED_AD_TECH_URL_WITH_QUERY);
        doReturn(false).when(mFlags).isDisableMeasurementEnrollmentCheck();
        when(AppManifestConfigHelper.isAllowedAttributionAccess(any(), any(), any()))
                .thenReturn(true);
        assertTrue(mClassUnderTest.isAllowed(CONTEXT));
    }

    @Test
    public void isAllowedSuccessClearsFragment() {
        mClassUnderTest =
                new ManifestBasedAdtechAccessResolver(
                        mMockEnrollmentDao,
                        mFlags,
                        PACKAGE,
                        ENROLLED_AD_TECH_URL_WITH_QUERY_FRAGMENT);
        doReturn(false).when(mFlags).isDisableMeasurementEnrollmentCheck();
        when(AppManifestConfigHelper.isAllowedAttributionAccess(any(), any(), any()))
                .thenReturn(true);
        assertTrue(mClassUnderTest.isAllowed(CONTEXT));
    }

    @Test
    public void isAllowedNotEnrolledPassesWhenEnrollmentEnforcementOff() {
        mClassUnderTest =
                new ManifestBasedAdtechAccessResolver(
                        mMockEnrollmentDao, mFlags, PACKAGE, UNENROLLED_AD_TECH_URL);
        doReturn(true).when(mFlags).isDisableMeasurementEnrollmentCheck();
        assertTrue(mClassUnderTest.isAllowed(CONTEXT));
    }

    @Test
    public void isAllowedNullUrlPassesWhenEnrollmentEnforcementOff() {
        mClassUnderTest =
                new ManifestBasedAdtechAccessResolver(mMockEnrollmentDao, mFlags, PACKAGE, null);
        doReturn(true).when(mFlags).isDisableMeasurementEnrollmentCheck();
        assertTrue(mClassUnderTest.isAllowed(CONTEXT));
    }

    @Test
    public void isAllowedEmptyUrlPassesWhenEnrollmentEnforcementOff() {
        mClassUnderTest =
                new ManifestBasedAdtechAccessResolver(
                        mMockEnrollmentDao, mFlags, PACKAGE, EMPTY_URL);
        doReturn(true).when(mFlags).isDisableMeasurementEnrollmentCheck();
        assertTrue(mClassUnderTest.isAllowed(CONTEXT));
    }

    @Test
    public void isAllowedSuccessAlsoPassesWhenEnrollmentEnforcementOff() {
        mClassUnderTest =
                new ManifestBasedAdtechAccessResolver(
                        mMockEnrollmentDao, mFlags, PACKAGE, ENROLLED_AD_TECH_URL);
        doReturn(true).when(mFlags).isDisableMeasurementEnrollmentCheck();
        when(AppManifestConfigHelper.isAllowedAttributionAccess(any(), any(), any()))
                .thenReturn(true);
        assertTrue(mClassUnderTest.isAllowed(CONTEXT));
    }

    @Test
    public void isNotAllowed_enrollmentInBlocklist() {
        mClassUnderTest =
                new ManifestBasedAdtechAccessResolver(
                        mMockEnrollmentDao, mFlags, PACKAGE, ENROLLED_AD_TECH_URL);
        when(mFlags.isDisableMeasurementEnrollmentCheck()).thenReturn(false);
        when(AppManifestConfigHelper.isAllowedAttributionAccess(any(), any(), any()))
                .thenReturn(true);

        String enrollmentId = EnrollmentFixture.getValidEnrollment().getEnrollmentId();
        when(mFlags.isEnrollmentBlocklisted(enrollmentId)).thenReturn(true);

        assertFalse(mClassUnderTest.isAllowed(CONTEXT));
    }

    @Test
    public void getErrorMessage() {
        mClassUnderTest =
                new ManifestBasedAdtechAccessResolver(
                        mMockEnrollmentDao, mFlags, PACKAGE, UNENROLLED_AD_TECH_URL);
        assertEquals(ERROR_MESSAGE, mClassUnderTest.getErrorMessage());
    }
}
