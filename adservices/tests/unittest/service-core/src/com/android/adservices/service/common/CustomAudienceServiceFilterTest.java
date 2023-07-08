/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.adservices.customaudience.CustomAudienceFixture.getValidFetchUriByBuyer;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyString;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.content.Context;
import android.os.LimitExceededException;
import android.os.Process;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

import java.util.function.Supplier;

public class CustomAudienceServiceFilterTest {
    private static final String CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;

    @Spy private Context mContext = ApplicationProvider.getApplicationContext();

    private static final Flags TEST_FLAGS = FlagsFactory.getFlagsForTest();
    private static final Flags FLAGS_WITH_ENROLLMENT_CHECK =
            new Flags() {
                @Override
                public boolean getDisableFledgeEnrollmentCheck() {
                    return false;
                }
            };

    @Mock private ConsentManager mConsentManagerMock;

    @Mock AppImportanceFilter mAppImportanceFilter;

    private final AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);

    @Spy
    FledgeAllowListsFilter mFledgeAllowListsFilterSpy =
            new FledgeAllowListsFilter(TEST_FLAGS, mAdServicesLoggerMock);

    @Spy
    FledgeAuthorizationFilter mFledgeAuthorizationFilterSpy =
            new FledgeAuthorizationFilter(
                    mContext.getPackageManager(),
                    new EnrollmentDao(mContext, DbTestUtil.getSharedDbHelperForTest(), TEST_FLAGS),
                    mAdServicesLoggerMock);

    @Mock private Throttler mMockThrottler;

    private final Supplier<Throttler> mThrottlerSupplier = () -> mMockThrottler;

    private MockitoSession mStaticMockSession = null;

    private CustomAudienceServiceFilter mCustomAudienceServiceFilter;

    private static final AdTechIdentifier SELLER_VALID =
            AdTechIdentifier.fromString("developer.android.com");

    private static final int API_NAME = 0;

    private static final int MY_UID = Process.myUid();

    @Before
    public void setUp() throws Exception {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .initMocks(this)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        mCustomAudienceServiceFilter =
                new CustomAudienceServiceFilter(
                        mContext,
                        mConsentManagerMock,
                        TEST_FLAGS,
                        mAppImportanceFilter,
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy,
                        mThrottlerSupplier);

        when(mMockThrottler.tryAcquire(eq(Throttler.ApiKey.UNKNOWN), anyString())).thenReturn(true);
    }

    @After
    public void tearDown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testFilterRequest_invalidPackageName_throws() {
        assertThrows(
                FledgeAuthorizationFilter.CallerMismatchException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequest(
                                SELLER_VALID,
                                "invalidPackageName",
                                false,
                                false,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN));
    }

    @Test
    public void testFilterRequest_throttled_throws() {
        when(mMockThrottler.tryAcquire(eq(Throttler.ApiKey.UNKNOWN), anyString()))
                .thenReturn(false);

        assertThrows(
                LimitExceededException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequest(
                                SELLER_VALID,
                                CALLER_PACKAGE_NAME,
                                false,
                                false,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN));
    }

    @Test
    public void testFilterRequest_enforceForegroundTrue_foregroundCheckFails_throws() {
        doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(MY_UID, API_NAME, null);

        assertThrows(
                AppImportanceFilter.WrongCallingApplicationStateException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequest(
                                SELLER_VALID,
                                CALLER_PACKAGE_NAME,
                                true,
                                false,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN));
    }

    @Test
    public void testFilterRequest_enforceForegroundFalse_foregroundCheckFails_succeeds() {
        doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(Process.myUid(), API_NAME, null);

        mCustomAudienceServiceFilter.filterRequest(
                SELLER_VALID,
                CALLER_PACKAGE_NAME,
                false,
                false,
                MY_UID,
                API_NAME,
                Throttler.ApiKey.UNKNOWN);
    }

    @Test
    public void testFilterRequest_adTechNotAuthorized_throws() {
        // Create new CustomAudienceServiceFilter with new flags
        mCustomAudienceServiceFilter =
                new CustomAudienceServiceFilter(
                        mContext,
                        mConsentManagerMock,
                        FLAGS_WITH_ENROLLMENT_CHECK,
                        mAppImportanceFilter,
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy,
                        mThrottlerSupplier);

        doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mFledgeAuthorizationFilterSpy)
                .assertAdTechAllowed(mContext, CALLER_PACKAGE_NAME, SELLER_VALID, API_NAME);

        assertThrows(
                FledgeAuthorizationFilter.AdTechNotAllowedException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequest(
                                SELLER_VALID,
                                CALLER_PACKAGE_NAME,
                                false,
                                false,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN));
    }

    @Test
    public void testFilterRequest_nullAdTech_skipCheck() {
        mCustomAudienceServiceFilter.filterRequest(
                null,
                CALLER_PACKAGE_NAME,
                false,
                false,
                MY_UID,
                API_NAME,
                Throttler.ApiKey.UNKNOWN);

        verify(mFledgeAuthorizationFilterSpy, never())
                .assertAdTechAllowed(any(), anyString(), any(), anyInt());
    }

    @Test
    public void testFilterRequest_appNotInAllowlist_throws() {
        doThrow(new FledgeAllowListsFilter.AppNotAllowedException())
                .when(mFledgeAllowListsFilterSpy)
                .assertAppCanUsePpapi(CALLER_PACKAGE_NAME, API_NAME);

        assertThrows(
                FledgeAllowListsFilter.AppNotAllowedException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequest(
                                SELLER_VALID,
                                CALLER_PACKAGE_NAME,
                                false,
                                false,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN));
    }

    @Test
    public void testFilterRequest_enforceConsentTrue_hasUserConsentForApp_succeeds() {
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(CALLER_PACKAGE_NAME);
        mCustomAudienceServiceFilter.filterRequest(
                SELLER_VALID,
                CALLER_PACKAGE_NAME,
                false,
                true,
                MY_UID,
                API_NAME,
                Throttler.ApiKey.UNKNOWN);
    }

    @Test
    public void testFilterRequest_enforceConsentTrue_lacksUserConsentForApp_throws() {
        doReturn(true)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(CALLER_PACKAGE_NAME);

        assertThrows(
                ConsentManager.RevokedConsentException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequest(
                                SELLER_VALID,
                                CALLER_PACKAGE_NAME,
                                false,
                                true,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN));
    }

    @Test
    public void testFilterRequest_enforceConsentFalse_lacksUserConsentForApp_throws() {
        doReturn(true)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(CALLER_PACKAGE_NAME);
        mCustomAudienceServiceFilter.filterRequest(
                SELLER_VALID,
                CALLER_PACKAGE_NAME,
                false,
                false,
                MY_UID,
                API_NAME,
                Throttler.ApiKey.UNKNOWN);
    }

    @Test
    public void testFilterRequestAndExtractIdentifier_invalidPackageName_throws() {
        assertThrows(
                FledgeAuthorizationFilter.CallerMismatchException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequestAndExtractIdentifier(
                                getValidFetchUriByBuyer(SELLER_VALID),
                                "invalidPackageName",
                                false,
                                false,
                                false,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN));
    }

    @Test
    public void testFilterRequestAndExtractIdentifier_throttled_throws() {
        when(mMockThrottler.tryAcquire(eq(Throttler.ApiKey.UNKNOWN), anyString()))
                .thenReturn(false);

        assertThrows(
                LimitExceededException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequestAndExtractIdentifier(
                                getValidFetchUriByBuyer(SELLER_VALID),
                                CALLER_PACKAGE_NAME,
                                false,
                                false,
                                false,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN));
    }

    @Test
    public void testFilterRequestAndExtractIdentifier_enforceForegroundTrue_foregroundCheckFails() {
        doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(MY_UID, API_NAME, null);

        assertThrows(
                AppImportanceFilter.WrongCallingApplicationStateException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequestAndExtractIdentifier(
                                getValidFetchUriByBuyer(SELLER_VALID),
                                CALLER_PACKAGE_NAME,
                                false,
                                true,
                                false,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN));
    }

    @Test
    public void
            testFilterRequestAndExtractIdentifier_enforceForegroundFalse_foregroundCheckFails() {
        doReturn(SELLER_VALID)
                .when(mFledgeAuthorizationFilterSpy)
                .getAndAssertAdTechFromUriAllowed(
                        mContext,
                        CALLER_PACKAGE_NAME,
                        getValidFetchUriByBuyer(SELLER_VALID),
                        API_NAME);

        doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(Process.myUid(), API_NAME, null);

        mCustomAudienceServiceFilter.filterRequestAndExtractIdentifier(
                getValidFetchUriByBuyer(SELLER_VALID),
                CALLER_PACKAGE_NAME,
                false,
                false,
                false,
                MY_UID,
                API_NAME,
                Throttler.ApiKey.UNKNOWN);
    }

    @Test
    public void testFilterRequestAndExtractIdentifier_enableEnrollmentCheck_invalidAdTech_throws() {
        doReturn(SELLER_VALID)
                .when(mFledgeAuthorizationFilterSpy)
                .getAndAssertAdTechFromUriAllowed(
                        mContext,
                        CALLER_PACKAGE_NAME,
                        getValidFetchUriByBuyer(SELLER_VALID),
                        API_NAME);

        doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mFledgeAuthorizationFilterSpy)
                .getAndAssertAdTechFromUriAllowed(
                        mContext,
                        CALLER_PACKAGE_NAME,
                        getValidFetchUriByBuyer(SELLER_VALID),
                        API_NAME);

        assertThrows(
                FledgeAuthorizationFilter.AdTechNotAllowedException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequestAndExtractIdentifier(
                                getValidFetchUriByBuyer(SELLER_VALID),
                                CALLER_PACKAGE_NAME,
                                false,
                                false,
                                false,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN));
    }

    @Test
    public void
            testFilterRequestAndExtractIdentifier_disableEnrollmentCheck_eTLDPlus1NotExtracted() {
        AdTechIdentifier seller =
                mCustomAudienceServiceFilter.filterRequestAndExtractIdentifier(
                        getValidFetchUriByBuyer(SELLER_VALID),
                        CALLER_PACKAGE_NAME,
                        true,
                        false,
                        false,
                        MY_UID,
                        API_NAME,
                        Throttler.ApiKey.UNKNOWN);

        // Assert URI host is extracted as the ad tech identifier
        assertThat(seller).isEqualTo(SELLER_VALID);

        // Assert eTLD+1 extractions and the implied enrollment check is skipped.
        verify(mFledgeAuthorizationFilterSpy, never())
                .getAndAssertAdTechFromUriAllowed(
                        mContext,
                        CALLER_PACKAGE_NAME,
                        getValidFetchUriByBuyer(SELLER_VALID),
                        API_NAME);
    }

    @Test
    public void testFilterRequestAndExtractIdentifier_appNotInAllowlist_throws() {
        doReturn(SELLER_VALID)
                .when(mFledgeAuthorizationFilterSpy)
                .getAndAssertAdTechFromUriAllowed(
                        mContext,
                        CALLER_PACKAGE_NAME,
                        getValidFetchUriByBuyer(SELLER_VALID),
                        API_NAME);

        doThrow(new FledgeAllowListsFilter.AppNotAllowedException())
                .when(mFledgeAllowListsFilterSpy)
                .assertAppCanUsePpapi(CALLER_PACKAGE_NAME, API_NAME);

        assertThrows(
                FledgeAllowListsFilter.AppNotAllowedException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequestAndExtractIdentifier(
                                getValidFetchUriByBuyer(SELLER_VALID),
                                CALLER_PACKAGE_NAME,
                                false,
                                false,
                                false,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN));
    }

    @Test
    public void testFilterRequestAndExtractIdentifier_enforceConsentTrue_hasUserConsentForApp() {
        doReturn(SELLER_VALID)
                .when(mFledgeAuthorizationFilterSpy)
                .getAndAssertAdTechFromUriAllowed(
                        mContext,
                        CALLER_PACKAGE_NAME,
                        getValidFetchUriByBuyer(SELLER_VALID),
                        API_NAME);

        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(CALLER_PACKAGE_NAME);

        mCustomAudienceServiceFilter.filterRequestAndExtractIdentifier(
                getValidFetchUriByBuyer(SELLER_VALID),
                CALLER_PACKAGE_NAME,
                false,
                false,
                true,
                MY_UID,
                API_NAME,
                Throttler.ApiKey.UNKNOWN);
    }

    @Test
    public void testFilterRequestAndExtractIdentifier_enforceConsentTrue_lacksUserConsentForApp() {
        doReturn(SELLER_VALID)
                .when(mFledgeAuthorizationFilterSpy)
                .getAndAssertAdTechFromUriAllowed(
                        mContext,
                        CALLER_PACKAGE_NAME,
                        getValidFetchUriByBuyer(SELLER_VALID),
                        API_NAME);

        doReturn(true)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(CALLER_PACKAGE_NAME);

        assertThrows(
                ConsentManager.RevokedConsentException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequestAndExtractIdentifier(
                                getValidFetchUriByBuyer(SELLER_VALID),
                                CALLER_PACKAGE_NAME,
                                false,
                                false,
                                true,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN));
    }

    @Test
    public void testFilterRequestAndExtractIdentifier_enforceConsentFalse_lacksUserConsentForApp() {
        doReturn(SELLER_VALID)
                .when(mFledgeAuthorizationFilterSpy)
                .getAndAssertAdTechFromUriAllowed(
                        mContext,
                        CALLER_PACKAGE_NAME,
                        getValidFetchUriByBuyer(SELLER_VALID),
                        API_NAME);

        doReturn(true)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(CALLER_PACKAGE_NAME);

        mCustomAudienceServiceFilter.filterRequestAndExtractIdentifier(
                getValidFetchUriByBuyer(SELLER_VALID),
                CALLER_PACKAGE_NAME,
                false,
                false,
                false,
                MY_UID,
                API_NAME,
                Throttler.ApiKey.UNKNOWN);
    }
}
