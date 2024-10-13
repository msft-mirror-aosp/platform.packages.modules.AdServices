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

import static android.adservices.common.AdServicesStatusUtils.RATE_LIMIT_REACHED_ERROR_MESSAGE;

import static com.android.adservices.service.common.AppManifestConfigCall.API_AD_SELECTION;
import static com.android.adservices.service.common.AppManifestConfigCall.API_CUSTOM_AUDIENCES;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyBoolean;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyString;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.os.LimitExceededException;
import android.os.Process;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.common.DbTestUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.devapi.DevSessionFixture;
import com.android.adservices.service.FakeFlagsFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;

public final class AdSelectionServiceFilterTest extends AdServicesMockitoTestCase {

    private static final String CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final Flags TEST_FLAGS =
            new FakeFlagsFactory.TestFlags() {
                @Override
                public String getPpapiAppAllowList() {
                    return CALLER_PACKAGE_NAME;
                }
            };

    private static final Flags FLAGS_WITH_ENROLLMENT_CHECK =
            new Flags() {
                @Override
                public boolean getDisableFledgeEnrollmentCheck() {
                    return false;
                }
            };

    @Mock AppImportanceFilter mAppImportanceFilter;

    private final AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);

    @Spy
    FledgeAllowListsFilter mFledgeAllowListsFilterSpy =
            new FledgeAllowListsFilter(TEST_FLAGS, mAdServicesLoggerMock);

    @Mock private FledgeConsentFilter mFledgeConsentFilterMock;

    @Spy
    FledgeAuthorizationFilter mFledgeAuthorizationFilterSpy =
            new FledgeAuthorizationFilter(
                    mSpyContext.getPackageManager(),
                    new EnrollmentDao(
                            mSpyContext, DbTestUtil.getSharedDbHelperForTest(), TEST_FLAGS),
                    mAdServicesLoggerMock);

    @Mock private FledgeApiThrottleFilter mFledgeApiThrottleFilterMock;

    private AdSelectionServiceFilter mAdSelectionServiceFilter;

    private static final AdTechIdentifier SELLER_VALID =
            AdTechIdentifier.fromString("developer.android.com");
    private static final AdTechIdentifier SELLER_LOCALHOST =
            AdTechIdentifier.fromString("localhost");

    private static final int API_NAME = 0;

    private static final int MY_UID = Process.myUid();

    @Before
    public void setUp() throws Exception {
        mAdSelectionServiceFilter =
                new AdSelectionServiceFilter(
                        mSpyContext,
                        mFledgeConsentFilterMock,
                        TEST_FLAGS,
                        mAppImportanceFilter,
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy,
                        mFledgeApiThrottleFilterMock);
    }

    @Test
    public void testFilterRequest_noExceptionsThrown_succeeds() {
        mAdSelectionServiceFilter.filterRequest(
                SELLER_VALID,
                CALLER_PACKAGE_NAME,
                true,
                true,
                true,
                MY_UID,
                API_NAME,
                Throttler.ApiKey.UNKNOWN,
                DevContext.createForDevOptionsDisabled());
    }

    @Test
    public void testFilterRequestThrowsCallerMismatchExceptionWithInvalidPackageName() {
        FilterException exception =
                assertThrows(
                        FilterException.class,
                        () ->
                                mAdSelectionServiceFilter.filterRequest(
                                        SELLER_VALID,
                                        "invalidPackageName",
                                        false,
                                        true,
                                        true,
                                        MY_UID,
                                        API_NAME,
                                        Throttler.ApiKey.UNKNOWN,
                                        DevContext.createForDevOptionsDisabled()));
        assertThat(exception)
                .hasCauseThat()
                .isInstanceOf(FledgeAuthorizationFilter.CallerMismatchException.class);
    }

    @Test
    public void testFilterRequest_throttled_throwsLimitExceededException() {
        doThrow(new LimitExceededException(RATE_LIMIT_REACHED_ERROR_MESSAGE))
                .when(mFledgeApiThrottleFilterMock)
                .assertCallerNotThrottled(anyString(), any(), anyInt());

        FilterException exception =
                assertThrows(
                        FilterException.class,
                        () ->
                                mAdSelectionServiceFilter.filterRequest(
                                        SELLER_VALID,
                                        CALLER_PACKAGE_NAME,
                                        false,
                                        true,
                                        true,
                                        MY_UID,
                                        API_NAME,
                                        Throttler.ApiKey.UNKNOWN,
                                        DevContext.createForDevOptionsDisabled()));

        assertThat(exception).hasCauseThat().isInstanceOf(LimitExceededException.class);
    }

    @Test
    public void
            testFilterRequestThrowsWrongCallingApplicationStateExceptionIfForegroundCheckFails() {
        doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(Process.myUid(), API_NAME, null);
        FilterException exception =
                assertThrows(
                        FilterException.class,
                        () ->
                                mAdSelectionServiceFilter.filterRequest(
                                        SELLER_VALID,
                                        CALLER_PACKAGE_NAME,
                                        true,
                                        true,
                                        true,
                                        MY_UID,
                                        API_NAME,
                                        Throttler.ApiKey.UNKNOWN,
                                        DevContext.createForDevOptionsDisabled()));
        assertThat(exception)
                .hasCauseThat()
                .isInstanceOf(AppImportanceFilter.WrongCallingApplicationStateException.class);
    }

    @Test
    public void testFilterRequestSucceedsForBackgroundAppsWhenEnforceForegroundFalse() {
        doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(Process.myUid(), API_NAME, null);

        mAdSelectionServiceFilter.filterRequest(
                SELLER_VALID,
                CALLER_PACKAGE_NAME,
                false,
                true,
                true,
                MY_UID,
                API_NAME,
                Throttler.ApiKey.UNKNOWN,
                DevContext.createForDevOptionsDisabled());
    }

    @Test
    public void testFilterRequestThrowsAdTechNotAllowedExceptionWhenAdTechNotAuthorized() {
        // Create new AdSelectionServiceFilter with new flags
        mAdSelectionServiceFilter =
                new AdSelectionServiceFilter(
                        mSpyContext,
                        mFledgeConsentFilterMock,
                        FLAGS_WITH_ENROLLMENT_CHECK,
                        mAppImportanceFilter,
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy,
                        mFledgeApiThrottleFilterMock);

        doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mFledgeAuthorizationFilterSpy)
                .assertAdTechAllowed(
                        mSpyContext,
                        CALLER_PACKAGE_NAME,
                        SELLER_VALID,
                        API_NAME,
                        API_CUSTOM_AUDIENCES);
        FilterException exception =
                assertThrows(
                        FilterException.class,
                        () ->
                                mAdSelectionServiceFilter.filterRequest(
                                        SELLER_VALID,
                                        CALLER_PACKAGE_NAME,
                                        false,
                                        true,
                                        true,
                                        MY_UID,
                                        API_NAME,
                                        Throttler.ApiKey.UNKNOWN,
                                        DevContext.createForDevOptionsDisabled()));
        assertThat(exception)
                .hasCauseThat()
                .isInstanceOf(FledgeAuthorizationFilter.AdTechNotAllowedException.class);
    }

    @Test
    public void testFilterRequestThrowsAppNotAllowedExceptionWhenAppNotInAllowlist() {
        doThrow(new FledgeAllowListsFilter.AppNotAllowedException())
                .when(mFledgeAllowListsFilterSpy)
                .assertAppInAllowlist(CALLER_PACKAGE_NAME, API_NAME, API_AD_SELECTION);
        FilterException exception =
                assertThrows(
                        FilterException.class,
                        () ->
                                mAdSelectionServiceFilter.filterRequest(
                                        SELLER_VALID,
                                        CALLER_PACKAGE_NAME,
                                        false,
                                        true,
                                        true,
                                        MY_UID,
                                        API_NAME,
                                        Throttler.ApiKey.UNKNOWN,
                                        DevContext.createForDevOptionsDisabled()));
        assertThat(exception)
                .hasCauseThat()
                .isInstanceOf(FledgeAllowListsFilter.AppNotAllowedException.class);
    }

    @Test
    public void testFilterRequest_apiConsentRevoked_throwsRevokedConsentException() {
        doThrow(new ConsentManager.RevokedConsentException())
                .when(mFledgeConsentFilterMock)
                .assertCallerHasApiUserConsent(anyString(), anyInt());

        FilterException exception =
                assertThrows(
                        FilterException.class,
                        () ->
                                mAdSelectionServiceFilter.filterRequest(
                                        SELLER_VALID,
                                        CALLER_PACKAGE_NAME,
                                        false,
                                        true,
                                        true,
                                        MY_UID,
                                        API_NAME,
                                        Throttler.ApiKey.UNKNOWN,
                                        DevContext.createForDevOptionsDisabled()));

        assertThat(exception)
                .hasCauseThat()
                .isInstanceOf(ConsentManager.RevokedConsentException.class);
    }

    @Test
    public void testFilterRequest_apiConsentRevoked_enforceConsentFalse_succeeds() {
        doThrow(new ConsentManager.RevokedConsentException())
                .when(mFledgeConsentFilterMock)
                .assertCallerHasApiUserConsent(anyString(), anyInt());

        mAdSelectionServiceFilter.filterRequest(
                SELLER_VALID,
                CALLER_PACKAGE_NAME,
                false,
                false,
                true,
                MY_UID,
                API_NAME,
                Throttler.ApiKey.UNKNOWN,
                DevContext.createForDevOptionsDisabled());
    }

    @Test
    public void testFilterRequestDoesNotDoEnrollmentCheckWhenAdTechParamIsNull() {
        mAdSelectionServiceFilter.filterRequest(
                null,
                CALLER_PACKAGE_NAME,
                false,
                true,
                true,
                MY_UID,
                API_NAME,
                Throttler.ApiKey.UNKNOWN,
                DevContext.createForDevOptionsDisabled());

        verify(mFledgeAuthorizationFilterSpy, never())
                .assertAdTechAllowed(any(), anyString(), any(), anyInt(), anyInt());
    }

    @Test
    public void testFilterRequest_withLocalhostDomain_doesNotPass() {
        mAdSelectionServiceFilter =
                new AdSelectionServiceFilter(
                        mSpyContext,
                        mFledgeConsentFilterMock,
                        FLAGS_WITH_ENROLLMENT_CHECK,
                        mAppImportanceFilter,
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy,
                        mFledgeApiThrottleFilterMock);

        FilterException exception =
                assertThrows(
                        FilterException.class,
                        () ->
                                mAdSelectionServiceFilter.filterRequest(
                                        SELLER_LOCALHOST,
                                        CALLER_PACKAGE_NAME,
                                        false,
                                        false,
                                        true,
                                        MY_UID,
                                        API_NAME,
                                        Throttler.ApiKey.UNKNOWN,
                                        DevContext.createForDevOptionsDisabled()));

        assertThat(exception)
                .hasCauseThat()
                .isInstanceOf(FledgeAuthorizationFilter.AdTechNotAllowedException.class);
    }

    @Test
    public void testFilterRequest_withDeveloperMode_succeeds() {
        mAdSelectionServiceFilter.filterRequest(
                SELLER_VALID,
                CALLER_PACKAGE_NAME,
                false,
                false,
                true,
                MY_UID,
                API_NAME,
                Throttler.ApiKey.UNKNOWN,
                DevContext.builder(CALLER_PACKAGE_NAME).setDeviceDevOptionsEnabled(true).build());
    }

    @Test
    public void testFilterRequest_withLocalhostDomainInDeveloperMode_skipCheck() {
        mAdSelectionServiceFilter =
                new AdSelectionServiceFilter(
                        mSpyContext,
                        mFledgeConsentFilterMock,
                        FLAGS_WITH_ENROLLMENT_CHECK,
                        mAppImportanceFilter,
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy,
                        mFledgeApiThrottleFilterMock);

        mAdSelectionServiceFilter.filterRequest(
                SELLER_LOCALHOST,
                CALLER_PACKAGE_NAME,
                false,
                false,
                true,
                MY_UID,
                API_NAME,
                Throttler.ApiKey.UNKNOWN,
                DevContext.builder(CALLER_PACKAGE_NAME).setDeviceDevOptionsEnabled(true).build());

        verify(mFledgeAuthorizationFilterSpy, never())
                .assertAdTechAllowed(any(), anyString(), any(), anyInt(), anyInt());
    }

    @Test
    public void testFilterRequest_withEnrollmentJobNotScheduledAndEnrollmentCheckFails() {
        doThrow(new ConsentManager.RevokedConsentException())
                .when(mFledgeConsentFilterMock)
                .assertEnrollmentShouldBeScheduled(
                        anyBoolean(), anyBoolean(), anyString(), anyInt());

        doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mFledgeAuthorizationFilterSpy)
                .assertAdTechAllowed(any(), anyString(), any(), anyInt(), anyInt());

        mAdSelectionServiceFilter =
                new AdSelectionServiceFilter(
                        mSpyContext,
                        mFledgeConsentFilterMock,
                        FLAGS_WITH_ENROLLMENT_CHECK,
                        mAppImportanceFilter,
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy,
                        mFledgeApiThrottleFilterMock);

        FilterException exception =
                assertThrows(
                        FilterException.class,
                        () ->
                                mAdSelectionServiceFilter.filterRequest(
                                        SELLER_LOCALHOST,
                                        CALLER_PACKAGE_NAME,
                                        true,
                                        true,
                                        true,
                                        MY_UID,
                                        API_NAME,
                                        Throttler.ApiKey.UNKNOWN,
                                        DevContext.builder(CALLER_PACKAGE_NAME)
                                                .setDeviceDevOptionsEnabled(true)
                                                .setDevSession(DevSessionFixture.IN_PROD)
                                                .build()));

        assertThat(exception)
                .hasCauseThat()
                .isInstanceOf(ConsentManager.RevokedConsentException.class);
    }
}
