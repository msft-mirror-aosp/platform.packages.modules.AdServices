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

package com.android.adservices.service.common;

import static com.android.adservices.stats.FledgeApiCallStatsMatcher.aCallStatForFledgeApiWithStatus;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyNoMoreInteractions;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyZeroInteractions;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.CommonFixture;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.Spy;

public class FledgeAuthorizationFilterTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final int UID = 111;
    private static final int API_NAME_LOGGING_ID =
            AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE;
    private static final String PACKAGE_NAME = "pkg_name";
    private static final String PACKAGE_NAME_OTHER = "other_pkg_name";
    private static final String ENROLLMENT_ID = "enroll_id";
    private static final EnrollmentData ENROLLMENT_DATA =
            new EnrollmentData.Builder().setEnrollmentId(ENROLLMENT_ID).build();

    @Mock private PackageManager mPackageManager;
    @Mock private EnrollmentDao mEnrollmentDao;
    @Spy private final AdServicesLogger mAdServicesLoggerSpy = AdServicesLoggerImpl.getInstance();

    public MockitoSession mMockitoSession;

    private FledgeAuthorizationFilter mChecker;

    @Before
    public void setup() {
        mMockitoSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(PermissionHelper.class)
                        .mockStatic(AppManifestConfigHelper.class)
                        .initMocks(this)
                        .startMocking();
        mChecker =
                new FledgeAuthorizationFilter(
                        mPackageManager, mEnrollmentDao, mAdServicesLoggerSpy);
    }

    @After
    public void teardown() {
        mMockitoSession.finishMocking();
    }

    @Test
    public void testAssertCallingPackageName_isCallingPackageName() {
        when(mPackageManager.getPackagesForUid(UID))
                .thenReturn(new String[] {PACKAGE_NAME, PACKAGE_NAME_OTHER});

        mChecker.assertCallingPackageName(PACKAGE_NAME, UID, API_NAME_LOGGING_ID);

        verify(mPackageManager).getPackagesForUid(UID);
        verifyNoMoreInteractions(mPackageManager);
        verifyZeroInteractions(mAdServicesLoggerSpy, mEnrollmentDao);
    }

    @Test
    public void testAssertCallingPackageName_nullPackageName_throwNpe() {
        assertThrows(
                NullPointerException.class,
                () -> mChecker.assertCallingPackageName(null, UID, API_NAME_LOGGING_ID));

        verifyZeroInteractions(mPackageManager, mAdServicesLoggerSpy, mEnrollmentDao);
    }

    @Test
    public void testAssertCallingPackageName_isNotCallingPackageName_throwSecurityException() {
        when(mPackageManager.getPackagesForUid(UID)).thenReturn(new String[] {PACKAGE_NAME_OTHER});

        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mChecker.assertCallingPackageName(
                                        PACKAGE_NAME, UID, API_NAME_LOGGING_ID));

        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE,
                exception.getMessage());
        verify(mPackageManager).getPackagesForUid(UID);
        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        API_NAME_LOGGING_ID, AdServicesStatusUtils.STATUS_UNAUTHORIZED);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                API_NAME_LOGGING_ID, AdServicesStatusUtils.STATUS_UNAUTHORIZED));
        verifyNoMoreInteractions(mPackageManager, mAdServicesLoggerSpy, mEnrollmentDao);
    }

    @Test
    public void testAssertCallingPackageName_packageNotExist_throwSecurityException() {
        when(mPackageManager.getPackagesForUid(UID)).thenReturn(new String[] {});

        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mChecker.assertCallingPackageName(
                                        PACKAGE_NAME, UID, API_NAME_LOGGING_ID));

        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE,
                exception.getMessage());
        verify(mPackageManager).getPackagesForUid(UID);
        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        API_NAME_LOGGING_ID, AdServicesStatusUtils.STATUS_UNAUTHORIZED);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                API_NAME_LOGGING_ID, AdServicesStatusUtils.STATUS_UNAUTHORIZED));
        verifyNoMoreInteractions(mPackageManager, mEnrollmentDao, mAdServicesLoggerSpy);
    }

    @Test
    public void testAssertAppHasPermission_appHasPermission() {
        when(PermissionHelper.hasCustomAudiencesPermission(CONTEXT)).thenReturn(true);

        mChecker.assertAppDeclaredPermission(CONTEXT, API_NAME_LOGGING_ID);

        verifyZeroInteractions(mPackageManager, mEnrollmentDao, mAdServicesLoggerSpy);
    }

    @Test
    public void testAssertAppHasPermission_appDoesNotHavePermission_throwSecurityException() {
        when(PermissionHelper.hasCustomAudiencesPermission(CONTEXT)).thenReturn(false);

        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () -> mChecker.assertAppDeclaredPermission(CONTEXT, API_NAME_LOGGING_ID));

        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_PERMISSION_NOT_REQUESTED_ERROR_MESSAGE,
                exception.getMessage());
        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        API_NAME_LOGGING_ID, AdServicesStatusUtils.STATUS_PERMISSION_NOT_REQUESTED);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                API_NAME_LOGGING_ID,
                                AdServicesStatusUtils.STATUS_PERMISSION_NOT_REQUESTED));
        verifyNoMoreInteractions(mAdServicesLoggerSpy);
        verifyZeroInteractions(mPackageManager, mEnrollmentDao);
    }

    @Test
    public void testAssertAppHasPermission_nullContext_throwNpe() {
        assertThrows(
                NullPointerException.class,
                () -> mChecker.assertAppDeclaredPermission(null, API_NAME_LOGGING_ID));

        verifyZeroInteractions(mPackageManager, mEnrollmentDao, mAdServicesLoggerSpy);
    }

    @Test
    public void testAssertAdTechHasPermission_hasPermission() {
        when(mEnrollmentDao.getEnrollmentDataForFledgeByAdTechIdentifier(
                        CommonFixture.VALID_BUYER_1))
                .thenReturn(ENROLLMENT_DATA);
        when(AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                        CONTEXT, PACKAGE_NAME, ENROLLMENT_ID))
                .thenReturn(true);

        mChecker.assertAdTechAllowed(
                CONTEXT, PACKAGE_NAME, CommonFixture.VALID_BUYER_1, API_NAME_LOGGING_ID);
        verify(mEnrollmentDao)
                .getEnrollmentDataForFledgeByAdTechIdentifier(CommonFixture.VALID_BUYER_1);
        verifyNoMoreInteractions(mEnrollmentDao);
        verifyZeroInteractions(mPackageManager, mAdServicesLoggerSpy);
    }

    @Test
    public void testAssertAdTechHasPermission_noEnrollmentForAdTech_throwSecurityException() {
        when(mEnrollmentDao.getEnrollmentDataForFledgeByAdTechIdentifier(
                        CommonFixture.VALID_BUYER_1))
                .thenReturn(null);

        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mChecker.assertAdTechAllowed(
                                        CONTEXT,
                                        PACKAGE_NAME,
                                        CommonFixture.VALID_BUYER_1,
                                        API_NAME_LOGGING_ID));

        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE,
                exception.getMessage());
        verify(mEnrollmentDao)
                .getEnrollmentDataForFledgeByAdTechIdentifier(CommonFixture.VALID_BUYER_1);
        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        API_NAME_LOGGING_ID, AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                API_NAME_LOGGING_ID,
                                AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED));
        verifyNoMoreInteractions(mEnrollmentDao, mAdServicesLoggerSpy);
        verifyZeroInteractions(mPackageManager);
    }

    @Test
    public void testAssertAdTechHasPermission_appManifestNoPermission_throwSecurityException() {
        when(mEnrollmentDao.getEnrollmentDataForFledgeByAdTechIdentifier(
                        CommonFixture.VALID_BUYER_1))
                .thenReturn(ENROLLMENT_DATA);
        when(AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                        CONTEXT, PACKAGE_NAME, ENROLLMENT_ID))
                .thenReturn(false);

        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mChecker.assertAdTechAllowed(
                                        CONTEXT,
                                        PACKAGE_NAME,
                                        CommonFixture.VALID_BUYER_1,
                                        API_NAME_LOGGING_ID));

        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE,
                exception.getMessage());
        verify(mEnrollmentDao)
                .getEnrollmentDataForFledgeByAdTechIdentifier(CommonFixture.VALID_BUYER_1);
        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        API_NAME_LOGGING_ID, AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                API_NAME_LOGGING_ID,
                                AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED));
        verifyNoMoreInteractions(mEnrollmentDao, mAdServicesLoggerSpy);
        verifyZeroInteractions(mPackageManager);
    }

    @Test
    public void testAssertAdTechHasPermission_nullContext_throwNpe() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mChecker.assertAdTechAllowed(
                                null,
                                PACKAGE_NAME,
                                CommonFixture.VALID_BUYER_1,
                                API_NAME_LOGGING_ID));

        verifyZeroInteractions(mPackageManager, mEnrollmentDao, mAdServicesLoggerSpy);
    }

    @Test
    public void testAssertAdTechHasPermission_nullPackageName_throwNpe() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mChecker.assertAdTechAllowed(
                                CONTEXT, null, CommonFixture.VALID_BUYER_1, API_NAME_LOGGING_ID));

        verifyZeroInteractions(mPackageManager, mEnrollmentDao, mAdServicesLoggerSpy);
    }

    @Test
    public void testAssertAdTechHasPermission_nullAdTechIdentifier_throwNpe() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mChecker.assertAdTechAllowed(
                                CONTEXT, PACKAGE_NAME, null, API_NAME_LOGGING_ID));

        verifyZeroInteractions(mPackageManager, mEnrollmentDao, mAdServicesLoggerSpy);
    }
}
