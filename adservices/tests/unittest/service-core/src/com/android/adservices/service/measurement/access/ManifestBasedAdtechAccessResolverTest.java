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
import static org.mockito.Mockito.doReturn;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AppManifestConfigHelper;
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
    private static final Uri PRE_ENROLLED_URL = Uri.parse("https://test.com/source");
    private static final Uri UNENROLLED_URL = Uri.parse("https://notatest.com/source");
    private static final Uri EMPTY_URL = Uri.parse("");
    private ManifestBasedAdtechAccessResolver mClassUnderTest;
    private EnrollmentDao mEnrollmentDao = EnrollmentDao.getInstance(CONTEXT);
    @Mock private Flags mFlags;
    public MockitoSession mMockitoSession;

    @Before
    public void setup() {
        mMockitoSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(AppManifestConfigHelper.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();
    }

    @After
    public void teardown() {
        mMockitoSession.finishMocking();
    }

    @Test
    public void isAllowedNoPermissionFails() {
        mClassUnderTest =
                new ManifestBasedAdtechAccessResolver(
                        mEnrollmentDao, mFlags, PACKAGE, PRE_ENROLLED_URL);
        doReturn(false).when(mFlags).isDisableMeasurementEnrollmentCheck();
        when(AppManifestConfigHelper.isAllowedAttributionAccess(any(), any(), any()))
                .thenReturn(false);
        assertFalse(mClassUnderTest.isAllowed(CONTEXT));
    }

    @Test
    public void isAllowedNotEnrolledFails() {
        mClassUnderTest =
                new ManifestBasedAdtechAccessResolver(
                        mEnrollmentDao, mFlags, PACKAGE, UNENROLLED_URL);
        doReturn(false).when(mFlags).isDisableMeasurementEnrollmentCheck();
        assertFalse(mClassUnderTest.isAllowed(CONTEXT));
    }

    @Test
    public void isAllowedNullUrlFails() {
        mClassUnderTest =
                new ManifestBasedAdtechAccessResolver(mEnrollmentDao, mFlags, PACKAGE, null);
        doReturn(false).when(mFlags).isDisableMeasurementEnrollmentCheck();
        assertFalse(mClassUnderTest.isAllowed(CONTEXT));
    }

    @Test
    public void isAllowedEmptyUrlFails() {
        mClassUnderTest =
                new ManifestBasedAdtechAccessResolver(mEnrollmentDao, mFlags, PACKAGE, EMPTY_URL);
        doReturn(false).when(mFlags).isDisableMeasurementEnrollmentCheck();
        assertFalse(mClassUnderTest.isAllowed(CONTEXT));
    }

    @Test
    public void isAllowedSuccess() {
        mClassUnderTest =
                new ManifestBasedAdtechAccessResolver(
                        mEnrollmentDao, mFlags, PACKAGE, PRE_ENROLLED_URL);
        doReturn(false).when(mFlags).isDisableMeasurementEnrollmentCheck();
        when(AppManifestConfigHelper.isAllowedAttributionAccess(any(), any(), any()))
                .thenReturn(true);
        assertTrue(mClassUnderTest.isAllowed(CONTEXT));
    }

    @Test
    public void isAllowedNotEnrolledPassesWhenEnrollmentEnforcementOff() {
        mClassUnderTest =
                new ManifestBasedAdtechAccessResolver(
                        mEnrollmentDao, mFlags, PACKAGE, UNENROLLED_URL);
        doReturn(true).when(mFlags).isDisableMeasurementEnrollmentCheck();
        assertTrue(mClassUnderTest.isAllowed(CONTEXT));
    }

    @Test
    public void isAllowedNullUrlPassesWhenEnrollmentEnforcementOff() {
        mClassUnderTest =
                new ManifestBasedAdtechAccessResolver(mEnrollmentDao, mFlags, PACKAGE, null);
        doReturn(true).when(mFlags).isDisableMeasurementEnrollmentCheck();
        assertTrue(mClassUnderTest.isAllowed(CONTEXT));
    }

    @Test
    public void isAllowedEmptyUrlPassesWhenEnrollmentEnforcementOff() {
        mClassUnderTest =
                new ManifestBasedAdtechAccessResolver(mEnrollmentDao, mFlags, PACKAGE, EMPTY_URL);
        doReturn(true).when(mFlags).isDisableMeasurementEnrollmentCheck();
        assertTrue(mClassUnderTest.isAllowed(CONTEXT));
    }

    @Test
    public void isAllowedSuccessAlsoPassesWhenEnrollmentEnforcementOff() {
        mClassUnderTest =
                new ManifestBasedAdtechAccessResolver(
                        mEnrollmentDao, mFlags, PACKAGE, PRE_ENROLLED_URL);
        doReturn(true).when(mFlags).isDisableMeasurementEnrollmentCheck();
        when(AppManifestConfigHelper.isAllowedAttributionAccess(any(), any(), any()))
                .thenReturn(true);
        assertTrue(mClassUnderTest.isAllowed(CONTEXT));
    }

    @Test
    public void getErrorMessage() {
        mClassUnderTest =
                new ManifestBasedAdtechAccessResolver(
                        mEnrollmentDao, mFlags, PACKAGE, UNENROLLED_URL);
        assertEquals(ERROR_MESSAGE, mClassUnderTest.getErrorMessage());
    }
}
