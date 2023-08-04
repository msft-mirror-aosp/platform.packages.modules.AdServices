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

import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_PERMISSION_NOT_REQUESTED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyNoMoreInteractions;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyZeroInteractions;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.PhFlags;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.enrollment.EnrollmentStatus;
import com.android.adservices.service.enrollment.EnrollmentUtil;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

public class FledgeAuthorizationFilterTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final int UID = 111;
    private static final int API_NAME_LOGGING_ID =
            AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE;
    private static final String PACKAGE_NAME = "pkg_name";
    private static final String PACKAGE_NAME_OTHER = "other_pkg_name";
    private static final String ENROLLMENT_ID = "enroll_id";
    private static final Uri URI_FOR_AD_TECH =
            CommonFixture.getUriWithValidSubdomain(CommonFixture.VALID_BUYER_1.toString(), "/path");
    private static final EnrollmentData ENROLLMENT_DATA =
            new EnrollmentData.Builder().setEnrollmentId(ENROLLMENT_ID).build();

    @Mock private PackageManager mPackageManagerMock;
    @Mock private EnrollmentDao mEnrollmentDaoMock;
    @Mock private EnrollmentUtil mEnrollmentUtilMock;
    @Mock private PhFlags mPhFlagsMock;
    private final AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);

    public MockitoSession mMockitoSession;

    private FledgeAuthorizationFilter mChecker;

    @Before
    public void setup() {
        mMockitoSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(PermissionHelper.class)
                        .mockStatic(AppManifestConfigHelper.class)
                        .mockStatic(PhFlags.class)
                        .initMocks(this)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        mChecker =
                new FledgeAuthorizationFilter(
                        mPackageManagerMock,
                        mEnrollmentDaoMock,
                        mAdServicesLoggerMock,
                        mEnrollmentUtilMock);
    }

    @After
    public void teardown() {
        mMockitoSession.finishMocking();
    }

    @Test
    public void testAssertCallingPackageName_isCallingPackageName() {
        when(mPackageManagerMock.getPackagesForUid(UID))
                .thenReturn(new String[] {PACKAGE_NAME, PACKAGE_NAME_OTHER});

        mChecker.assertCallingPackageName(PACKAGE_NAME, UID, API_NAME_LOGGING_ID);

        verify(mPackageManagerMock).getPackagesForUid(UID);
        verifyNoMoreInteractions(mPackageManagerMock);
        verifyZeroInteractions(mAdServicesLoggerMock, mEnrollmentDaoMock);
    }

    @Test
    public void testAssertCallingPackageName_nullPackageName_throwNpe() {
        assertThrows(
                NullPointerException.class,
                () -> mChecker.assertCallingPackageName(null, UID, API_NAME_LOGGING_ID));

        verifyZeroInteractions(mPackageManagerMock, mAdServicesLoggerMock, mEnrollmentDaoMock);
    }

    @Test
    public void testAssertCallingPackageName_isNotCallingPackageName_throwSecurityException() {
        when(mPackageManagerMock.getPackagesForUid(UID))
                .thenReturn(new String[] {PACKAGE_NAME_OTHER});

        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mChecker.assertCallingPackageName(
                                        PACKAGE_NAME, UID, API_NAME_LOGGING_ID));

        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE,
                exception.getMessage());
        verify(mPackageManagerMock).getPackagesForUid(UID);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(eq(API_NAME_LOGGING_ID), eq(STATUS_UNAUTHORIZED), anyInt());
        verifyNoMoreInteractions(mPackageManagerMock, mAdServicesLoggerMock, mEnrollmentDaoMock);
    }

    @Test
    public void testAssertCallingPackageName_packageNotExist_throwSecurityException() {
        when(mPackageManagerMock.getPackagesForUid(UID)).thenReturn(new String[] {});

        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mChecker.assertCallingPackageName(
                                        PACKAGE_NAME, UID, API_NAME_LOGGING_ID));

        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE,
                exception.getMessage());
        verify(mPackageManagerMock).getPackagesForUid(UID);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(eq(API_NAME_LOGGING_ID), eq(STATUS_UNAUTHORIZED), anyInt());
        verifyNoMoreInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testAssertAppHasPermission_appHasPermission() {
        when(PermissionHelper.hasCustomAudiencesPermission(CONTEXT)).thenReturn(true);

        mChecker.assertAppDeclaredPermission(CONTEXT, API_NAME_LOGGING_ID);

        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
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
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME_LOGGING_ID), eq(STATUS_PERMISSION_NOT_REQUESTED), anyInt());
        verifyNoMoreInteractions(mAdServicesLoggerMock);
        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock);
    }

    @Test
    public void testAssertAppHasPermission_nullContext_throwNpe() {
        assertThrows(
                NullPointerException.class,
                () -> mChecker.assertAppDeclaredPermission(null, API_NAME_LOGGING_ID));

        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testAssertAdTechHasPermission_hasPermission() {
        when(mEnrollmentUtilMock.getBuildId()).thenReturn(-1);
        when(mEnrollmentUtilMock.getFileGroupStatus()).thenReturn(0);
        when(mEnrollmentDaoMock.getEnrollmentRecordCountForLogging()).thenReturn(0);
        when(mEnrollmentDaoMock.getEnrollmentDataForFledgeByAdTechIdentifier(
                        CommonFixture.VALID_BUYER_1))
                .thenReturn(ENROLLMENT_DATA);
        when(AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                        CONTEXT, PACKAGE_NAME, ENROLLMENT_ID))
                .thenReturn(true);
        when(PhFlags.getInstance()).thenReturn(mPhFlagsMock);
        when(mPhFlagsMock.isEnrollmentBlocklisted(ENROLLMENT_ID)).thenReturn(false);

        mChecker.assertAdTechAllowed(
                CONTEXT, PACKAGE_NAME, CommonFixture.VALID_BUYER_1, API_NAME_LOGGING_ID);
        verify(mEnrollmentDaoMock).getEnrollmentRecordCountForLogging();
        verify(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByAdTechIdentifier(CommonFixture.VALID_BUYER_1);
        verifyNoMoreInteractions(mEnrollmentDaoMock);
        verifyZeroInteractions(mPackageManagerMock, mAdServicesLoggerMock);
    }

    @Test
    public void testAssertAdTechHasPermission_noEnrollmentForAdTech_throwSecurityException() {
        when(mEnrollmentUtilMock.getBuildId()).thenReturn(2);
        when(mEnrollmentUtilMock.getFileGroupStatus()).thenReturn(1);
        when(mEnrollmentDaoMock.getEnrollmentRecordCountForLogging()).thenReturn(3);
        when(mEnrollmentDaoMock.getEnrollmentDataForFledgeByAdTechIdentifier(
                        CommonFixture.VALID_BUYER_1))
                .thenReturn(null);
        when(PhFlags.getInstance()).thenReturn(mPhFlagsMock);

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
        verify(mEnrollmentUtilMock).getBuildId();
        verify(mEnrollmentUtilMock).getFileGroupStatus();
        verify(mEnrollmentDaoMock).getEnrollmentRecordCountForLogging();
        verify(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByAdTechIdentifier(CommonFixture.VALID_BUYER_1);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME_LOGGING_ID), eq(STATUS_CALLER_NOT_ALLOWED), anyInt());
        verify(mEnrollmentUtilMock)
                .logEnrollmentFailedStats(
                        eq(mAdServicesLoggerMock),
                        eq(2),
                        eq(1),
                        eq(3),
                        eq(CommonFixture.VALID_BUYER_1.toString()),
                        eq(
                                EnrollmentStatus.ErrorCause.ENROLLMENT_NOT_FOUND_ERROR_CAUSE
                                        .getValue()));
        verifyNoMoreInteractions(mEnrollmentDaoMock, mAdServicesLoggerMock);
        verifyZeroInteractions(mPackageManagerMock);
    }

    @Test
    public void testAssertAdTechHasPermission_appManifestNoPermission_throwSecurityException() {
        when(mEnrollmentUtilMock.getBuildId()).thenReturn(2);
        when(mEnrollmentUtilMock.getFileGroupStatus()).thenReturn(1);
        when(mEnrollmentDaoMock.getEnrollmentRecordCountForLogging()).thenReturn(3);
        when(mEnrollmentDaoMock.getEnrollmentDataForFledgeByAdTechIdentifier(
                        CommonFixture.VALID_BUYER_1))
                .thenReturn(ENROLLMENT_DATA);
        when(AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                        CONTEXT, PACKAGE_NAME, ENROLLMENT_ID))
                .thenReturn(false);
        when(PhFlags.getInstance()).thenReturn(mPhFlagsMock);

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
        verify(mEnrollmentUtilMock).getBuildId();
        verify(mEnrollmentUtilMock).getFileGroupStatus();
        verify(mEnrollmentDaoMock).getEnrollmentRecordCountForLogging();
        verify(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByAdTechIdentifier(CommonFixture.VALID_BUYER_1);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME_LOGGING_ID), eq(STATUS_CALLER_NOT_ALLOWED), anyInt());
        verify(mEnrollmentUtilMock)
                .logEnrollmentFailedStats(
                        eq(mAdServicesLoggerMock),
                        eq(2),
                        eq(1),
                        eq(3),
                        eq(CommonFixture.VALID_BUYER_1.toString()),
                        eq(EnrollmentStatus.ErrorCause.UNKNOWN_ERROR_CAUSE.getValue()));
        verifyNoMoreInteractions(mEnrollmentDaoMock, mAdServicesLoggerMock);
        verifyZeroInteractions(mPackageManagerMock);
    }

    @Test
    public void testAdTechInBlocklist_throwSecurityException() {
        when(mEnrollmentUtilMock.getBuildId()).thenReturn(2);
        when(mEnrollmentUtilMock.getFileGroupStatus()).thenReturn(1);
        when(mEnrollmentDaoMock.getEnrollmentRecordCountForLogging()).thenReturn(3);
        when(mEnrollmentDaoMock.getEnrollmentDataForFledgeByAdTechIdentifier(
                        CommonFixture.VALID_BUYER_1))
                .thenReturn(ENROLLMENT_DATA);
        when(AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                        CONTEXT, PACKAGE_NAME, ENROLLMENT_ID))
                .thenReturn(true);
        // Add ENROLLMENT_ID to blocklist.
        when(PhFlags.getInstance()).thenReturn(mPhFlagsMock);
        when(mPhFlagsMock.isEnrollmentBlocklisted(ENROLLMENT_ID)).thenReturn(true);

        assertThrows(
                SecurityException.class,
                () ->
                        mChecker.assertAdTechAllowed(
                                CONTEXT,
                                PACKAGE_NAME,
                                CommonFixture.VALID_BUYER_1,
                                API_NAME_LOGGING_ID));

        verify(mEnrollmentUtilMock).getBuildId();
        verify(mEnrollmentUtilMock).getFileGroupStatus();
        verify(mEnrollmentDaoMock).getEnrollmentRecordCountForLogging();
        verify(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByAdTechIdentifier(CommonFixture.VALID_BUYER_1);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME_LOGGING_ID), eq(STATUS_CALLER_NOT_ALLOWED), anyInt());
        verify(mEnrollmentUtilMock)
                .logEnrollmentFailedStats(
                        eq(mAdServicesLoggerMock),
                        eq(2),
                        eq(1),
                        eq(3),
                        eq(CommonFixture.VALID_BUYER_1.toString()),
                        eq(
                                EnrollmentStatus.ErrorCause.ENROLLMENT_BLOCKLISTED_ERROR_CAUSE
                                        .getValue()));
        verifyNoMoreInteractions(mEnrollmentDaoMock, mAdServicesLoggerMock);
        verifyZeroInteractions(mPackageManagerMock);
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

        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testAssertAdTechHasPermission_nullPackageName_throwNpe() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mChecker.assertAdTechAllowed(
                                CONTEXT, null, CommonFixture.VALID_BUYER_1, API_NAME_LOGGING_ID));

        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testAssertAdTechHasPermission_nullAdTechIdentifier_throwNpe() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mChecker.assertAdTechAllowed(
                                CONTEXT, PACKAGE_NAME, null, API_NAME_LOGGING_ID));

        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testAdTechNotEnrolled_throwSecurityException() {
        when(mEnrollmentUtilMock.getBuildId()).thenReturn(-1);
        when(mEnrollmentUtilMock.getFileGroupStatus()).thenReturn(0);
        when(mEnrollmentDaoMock.getEnrollmentRecordCountForLogging()).thenReturn(3);
        when(mEnrollmentDaoMock.getEnrollmentDataForFledgeByAdTechIdentifier(
                        CommonFixture.VALID_BUYER_1))
                .thenReturn(null);

        assertThrows(
                SecurityException.class,
                () ->
                        mChecker.assertAdTechEnrolled(
                                CommonFixture.VALID_BUYER_1, API_NAME_LOGGING_ID));

        verify(mEnrollmentDaoMock).getEnrollmentRecordCountForLogging();
        verify(mEnrollmentUtilMock)
                .logEnrollmentFailedStats(
                        eq(mAdServicesLoggerMock),
                        eq(-1),
                        eq(0),
                        eq(3),
                        eq(CommonFixture.VALID_BUYER_1.toString()),
                        eq(
                                EnrollmentStatus.ErrorCause.ENROLLMENT_NOT_FOUND_ERROR_CAUSE
                                        .getValue()));
    }

    @Test
    public void testAdTechEnrolled_isEnrolled() {
        when(mEnrollmentDaoMock.getEnrollmentDataForFledgeByAdTechIdentifier(
                        CommonFixture.VALID_BUYER_1))
                .thenReturn(ENROLLMENT_DATA);

        mChecker.assertAdTechEnrolled(CommonFixture.VALID_BUYER_1, API_NAME_LOGGING_ID);

        verify(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByAdTechIdentifier(CommonFixture.VALID_BUYER_1);
        verifyNoMoreInteractions(mEnrollmentDaoMock);
    }

    @Test
    public void testGetAndAssertAdTechFromUriAllowed_nullContext_throwsNullPointerException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mChecker.getAndAssertAdTechFromUriAllowed(
                                /* context= */ null,
                                PACKAGE_NAME,
                                URI_FOR_AD_TECH,
                                API_NAME_LOGGING_ID));

        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void
            testGetAndAssertAdTechFromUriAllowed_nullAppPackageName_throwsNullPointerException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mChecker.getAndAssertAdTechFromUriAllowed(
                                CONTEXT,
                                /* appPackageName= */ null,
                                URI_FOR_AD_TECH,
                                API_NAME_LOGGING_ID));

        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testGetAndAssertAdTechFromUriAllowed_nullUriForAdTech_throwsNullPointerException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mChecker.getAndAssertAdTechFromUriAllowed(
                                CONTEXT,
                                PACKAGE_NAME,
                                /* uriForAdTech= */ null,
                                API_NAME_LOGGING_ID));

        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testGetAndAssertAdTechFromUriAllowed_notEnrolled_throwsNotAllowedException() {
        when(mEnrollmentUtilMock.getBuildId()).thenReturn(2);
        when(mEnrollmentUtilMock.getFileGroupStatus()).thenReturn(1);
        when(mEnrollmentDaoMock.getEnrollmentRecordCountForLogging()).thenReturn(3);
        doReturn(null)
                .when(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByMatchingAdTechIdentifier(eq(URI_FOR_AD_TECH));
        when(PhFlags.getInstance()).thenReturn(mPhFlagsMock);

        assertThrows(
                FledgeAuthorizationFilter.AdTechNotAllowedException.class,
                () ->
                        mChecker.getAndAssertAdTechFromUriAllowed(
                                CONTEXT, PACKAGE_NAME, URI_FOR_AD_TECH, API_NAME_LOGGING_ID));

        verify(mEnrollmentUtilMock).getBuildId();
        verify(mEnrollmentUtilMock).getFileGroupStatus();
        verify(mEnrollmentDaoMock).getEnrollmentRecordCountForLogging();
        verify(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByMatchingAdTechIdentifier(eq(URI_FOR_AD_TECH));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME_LOGGING_ID), eq(STATUS_CALLER_NOT_ALLOWED), anyInt());
        verify(mEnrollmentUtilMock)
                .logEnrollmentFailedStats(
                        eq(mAdServicesLoggerMock),
                        eq(2),
                        eq(1),
                        eq(3),
                        eq(URI_FOR_AD_TECH.toString()),
                        eq(
                                EnrollmentStatus.ErrorCause.ENROLLMENT_NOT_FOUND_ERROR_CAUSE
                                        .getValue()));
        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testGetAndAssertAdTechFromUriAllowed_notAllowedByApp_throwsNotAllowedException() {
        when(mEnrollmentUtilMock.getBuildId()).thenReturn(2);
        when(mEnrollmentUtilMock.getFileGroupStatus()).thenReturn(1);
        when(mEnrollmentDaoMock.getEnrollmentRecordCountForLogging()).thenReturn(3);
        doReturn(new Pair<>(CommonFixture.VALID_BUYER_1, ENROLLMENT_DATA))
                .when(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByMatchingAdTechIdentifier(eq(URI_FOR_AD_TECH));
        doReturn(false)
                .when(
                        () ->
                                AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                        eq(CONTEXT), eq(PACKAGE_NAME), eq(ENROLLMENT_ID)));
        when(PhFlags.getInstance()).thenReturn(mPhFlagsMock);
        when(mPhFlagsMock.isEnrollmentBlocklisted(ENROLLMENT_ID)).thenReturn(false);

        assertThrows(
                FledgeAuthorizationFilter.AdTechNotAllowedException.class,
                () ->
                        mChecker.getAndAssertAdTechFromUriAllowed(
                                CONTEXT, PACKAGE_NAME, URI_FOR_AD_TECH, API_NAME_LOGGING_ID));

        verify(mEnrollmentUtilMock).getBuildId();
        verify(mEnrollmentUtilMock).getFileGroupStatus();
        verify(mEnrollmentDaoMock).getEnrollmentRecordCountForLogging();
        verify(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByMatchingAdTechIdentifier(eq(URI_FOR_AD_TECH));
        verify(
                () ->
                        AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                eq(CONTEXT), eq(PACKAGE_NAME), eq(ENROLLMENT_ID)));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME_LOGGING_ID), eq(STATUS_CALLER_NOT_ALLOWED), anyInt());
        verify(mEnrollmentUtilMock)
                .logEnrollmentFailedStats(
                        eq(mAdServicesLoggerMock),
                        eq(2),
                        eq(1),
                        eq(3),
                        eq(URI_FOR_AD_TECH.toString()),
                        eq(EnrollmentStatus.ErrorCause.UNKNOWN_ERROR_CAUSE.getValue()));
        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testGetAndAssertAdTechFromUriAllowed_adTechBlocklisted_throwsNotAllowedException() {
        when(mEnrollmentUtilMock.getBuildId()).thenReturn(2);
        when(mEnrollmentUtilMock.getFileGroupStatus()).thenReturn(1);
        when(mEnrollmentDaoMock.getEnrollmentRecordCountForLogging()).thenReturn(3);
        doReturn(new Pair<>(CommonFixture.VALID_BUYER_1, ENROLLMENT_DATA))
                .when(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByMatchingAdTechIdentifier(eq(URI_FOR_AD_TECH));
        doReturn(true)
                .when(
                        () ->
                                AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                        eq(CONTEXT), eq(PACKAGE_NAME), eq(ENROLLMENT_ID)));
        doReturn(mPhFlagsMock).when(PhFlags::getInstance);
        doReturn(true).when(mPhFlagsMock).isEnrollmentBlocklisted(eq(ENROLLMENT_ID));

        assertThrows(
                FledgeAuthorizationFilter.AdTechNotAllowedException.class,
                () ->
                        mChecker.getAndAssertAdTechFromUriAllowed(
                                CONTEXT, PACKAGE_NAME, URI_FOR_AD_TECH, API_NAME_LOGGING_ID));

        verify(mEnrollmentUtilMock).getBuildId();
        verify(mEnrollmentUtilMock).getFileGroupStatus();
        verify(mEnrollmentDaoMock).getEnrollmentRecordCountForLogging();
        verify(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByMatchingAdTechIdentifier(eq(URI_FOR_AD_TECH));
        verify(
                () ->
                        AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                eq(CONTEXT), eq(PACKAGE_NAME), eq(ENROLLMENT_ID)));
        verify(mPhFlagsMock).isEnrollmentBlocklisted(eq(ENROLLMENT_ID));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME_LOGGING_ID), eq(STATUS_CALLER_NOT_ALLOWED), anyInt());
        verify(mEnrollmentUtilMock)
                .logEnrollmentFailedStats(
                        eq(mAdServicesLoggerMock),
                        eq(2),
                        eq(1),
                        eq(3),
                        eq(URI_FOR_AD_TECH.toString()),
                        eq(
                                EnrollmentStatus.ErrorCause.ENROLLMENT_BLOCKLISTED_ERROR_CAUSE
                                        .getValue()));
        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testGetAndAssertAdTechFromUriAllowed_enrolled_returnsAdTechIdentifier() {
        when(mEnrollmentUtilMock.getBuildId()).thenReturn(-1);
        when(mEnrollmentUtilMock.getFileGroupStatus()).thenReturn(0);
        when(mEnrollmentDaoMock.getEnrollmentRecordCountForLogging()).thenReturn(0);
        doReturn(new Pair<>(CommonFixture.VALID_BUYER_1, ENROLLMENT_DATA))
                .when(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByMatchingAdTechIdentifier(eq(URI_FOR_AD_TECH));
        doReturn(true)
                .when(
                        () ->
                                AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                        eq(CONTEXT), eq(PACKAGE_NAME), eq(ENROLLMENT_ID)));
        doReturn(mPhFlagsMock).when(PhFlags::getInstance);
        doReturn(false).when(mPhFlagsMock).isEnrollmentBlocklisted(eq(ENROLLMENT_ID));

        AdTechIdentifier returnedAdTechIdentifier =
                mChecker.getAndAssertAdTechFromUriAllowed(
                        CONTEXT, PACKAGE_NAME, URI_FOR_AD_TECH, API_NAME_LOGGING_ID);

        assertWithMessage("Returned AdTechIdentifier")
                .that(returnedAdTechIdentifier)
                .isEqualTo(CommonFixture.VALID_BUYER_1);

        verify(mEnrollmentDaoMock).getEnrollmentRecordCountForLogging();
        verify(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByMatchingAdTechIdentifier(eq(URI_FOR_AD_TECH));
        verify(
                () ->
                        AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                eq(CONTEXT), eq(PACKAGE_NAME), eq(ENROLLMENT_ID)));
        verify(mPhFlagsMock).isEnrollmentBlocklisted(eq(ENROLLMENT_ID));
        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }
}
