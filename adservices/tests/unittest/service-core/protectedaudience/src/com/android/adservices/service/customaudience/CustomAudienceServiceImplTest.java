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

package com.android.adservices.service.customaudience;

import static android.adservices.common.AdServicesStatusUtils.ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.RATE_LIMIT_REACHED_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static com.android.adservices.service.common.AppManifestConfigCall.API_CUSTOM_AUDIENCES;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CUSTOM_AUDIENCE_SERVICE_GET_CALLING_UID_ILLEGAL_STATE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CUSTOM_AUDIENCE_SERVICE_NULL_ARGUMENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__LEAVE_CUSTOM_AUDIENCE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyString;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesPermissions;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.CallingAppUidSupplierFailureImpl;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.CustomAudienceOverrideCallback;
import android.adservices.customaudience.FetchAndJoinCustomAudienceCallback;
import android.adservices.customaudience.FetchAndJoinCustomAudienceInput;
import android.adservices.customaudience.ICustomAudienceCallback;
import android.os.LimitExceededException;
import android.os.Process;
import android.os.RemoteException;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudienceOverride;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.AdFilteringFeatureFactory;
import com.android.adservices.service.adselection.JsVersionRegister;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.AppImportanceFilter.WrongCallingApplicationStateException;
import com.android.adservices.service.common.CustomAudienceServiceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeApiThrottleFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.FledgeConsentFilter;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.shared.testing.SkipLoggingUsageRule;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.quality.Strictness;

import java.util.concurrent.ExecutorService;

// TODO (b/315812832) - Refactor test so strictness can be lenient and enable logging usage rule.
@SkipLoggingUsageRule(reason = "Overrides mocking strictness to STRICT_STUBS.")
@MockStatic(BackgroundFetchJob.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(DebugFlags.class)
public final class CustomAudienceServiceImplTest extends AdServicesExtendedMockitoTestCase {

    private static final ExecutorService DIRECT_EXECUTOR = MoreExecutors.newDirectExecutorService();

    private static final CustomAudience VALID_CUSTOM_AUDIENCE =
            CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1).build();

    @Mock private CustomAudienceImpl mCustomAudienceImplMock;
    @Mock private FledgeAuthorizationFilter mFledgeAuthorizationFilterMock;
    @Mock private FledgeAllowListsFilter mFledgeAllowListsFilterMock;
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private FledgeConsentFilter mFledgeConsentFilterMock;
    @Mock private ICustomAudienceCallback mICustomAudienceCallbackMock;
    @Mock private FetchAndJoinCustomAudienceCallback mFetchAndJoinCustomAudienceCallbackMock;
    @Mock private CustomAudienceOverrideCallback mCustomAudienceOverrideCallbackMock;
    @Mock private AppImportanceFilter mAppImportanceFilterMock;
    @Mock private CustomAudienceDao mCustomAudienceDaoMock;
    @Mock private AppInstallDao mAppInstallDaoMock;
    @Mock private FrequencyCapDao mFrequencyCapDaoMock;
    @Mock DevContextFilter mDevContextFilterMock;
    private final AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);
    @Mock private FledgeApiThrottleFilter mFledgeApiThrottleFilterMock;

    private static final int MY_UID = Process.myUid();

    private final Flags mFlagsWithAllCheckEnabled = new FlagsWithCheckEnabledSwitch(true, true);
    private final Flags mFlagsWithForegroundCheckDisabled =
            new FlagsWithCheckEnabledSwitch(false, true);
    private final Flags mFlagsWithEnrollmentCheckDisabled =
            new FlagsWithCheckEnabledSwitch(true, false);

    private CustomAudienceServiceImpl mService;

    @Before
    public void setup() throws Exception {
        mocker.mockGetConsentNotificationDebugMode(false);
        mService =
                new CustomAudienceServiceImpl(
                        sContext,
                        mCustomAudienceImplMock,
                        mFledgeAuthorizationFilterMock,
                        mConsentManagerMock,
                        mDevContextFilterMock,
                        DIRECT_EXECUTOR,
                        mAdServicesLoggerMock,
                        mAppImportanceFilterMock,
                        mFlagsWithAllCheckEnabled,
                        mMockDebugFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        new CustomAudienceServiceFilter(
                                sContext,
                                mFledgeConsentFilterMock,
                                mFlagsWithAllCheckEnabled,
                                mAppImportanceFilterMock,
                                mFledgeAuthorizationFilterMock,
                                mFledgeAllowListsFilterMock,
                                mFledgeApiThrottleFilterMock),
                        new AdFilteringFeatureFactory(
                                mAppInstallDaoMock,
                                mFrequencyCapDaoMock,
                                mFlagsWithAllCheckEnabled));

        Mockito.lenient()
                .doReturn(DevContext.createForDevOptionsDisabled())
                .when(mDevContextFilterMock)
                .createDevContext();
    }

    // Though it applies to all test cases, please do not move this into @After to avoid making
    // debugging of test cases in this file difficult. A test assertion that could be failing in the
    // actual @Test will likely be hidden due to verifyNoMoreInteractions failures in @After.
    private void verifyNoMoreMockInteractions() {
        verifyNoMoreInteractions(
                mCustomAudienceImplMock,
                mFledgeAuthorizationFilterMock,
                staticMockMarker(BackgroundFetchJob.class),
                mFledgeAllowListsFilterMock,
                mICustomAudienceCallbackMock,
                mCustomAudienceOverrideCallbackMock,
                mCustomAudienceDaoMock,
                mAppImportanceFilterMock,
                mConsentManagerMock,
                mAdServicesLoggerMock);
    }

    // TODO(b/315812832): need to set STRICT_STUBS; ideally test should be refactored to not need it
    @Override
    protected AdServicesExtendedMockitoRule getAdServicesExtendedMockitoRule() {
        return new AdServicesExtendedMockitoRule.Builder(this)
                .setStrictness(Strictness.STRICT_STUBS)
                .build();
    }

    @Test
    public void testJoinCustomAudience_runNormally() throws RemoteException {

        mService.joinCustomAudience(
                VALID_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                mICustomAudienceCallbackMock);
        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        verify(mCustomAudienceImplMock)
                .joinCustomAudience(
                        VALID_CUSTOM_AUDIENCE,
                        CustomAudienceFixture.VALID_OWNER,
                        DevContext.createForDevOptionsDisabled());
        verify(() -> BackgroundFetchJob.schedule(mFlagsWithAllCheckEnabled));
        verify(mICustomAudienceCallbackMock).onSuccess();
        verify(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE);
        verify(mAppImportanceFilterMock)
                .assertCallerIsInForeground(
                        MY_UID, AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE, null);
        verify(mFledgeAuthorizationFilterMock)
                .assertAdTechAllowed(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mConsentManagerMock).isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());
        verify(mFledgeAllowListsFilterMock)
                .assertAppInAllowlist(
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_SUCCESS);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testJoinCustomAudience_runNormallyWithUNotificationEnforcementDisabled()
            throws RemoteException {
        CustomAudienceServiceFilter customAudienceServiceFilterMock =
                Mockito.mock(CustomAudienceServiceFilter.class);
        mocker.mockGetConsentNotificationDebugMode(true);

        CustomAudienceServiceImpl service =
                new CustomAudienceServiceImpl(
                        sContext,
                        mCustomAudienceImplMock,
                        mFledgeAuthorizationFilterMock,
                        mConsentManagerMock,
                        mDevContextFilterMock,
                        DIRECT_EXECUTOR,
                        mAdServicesLoggerMock,
                        mAppImportanceFilterMock,
                        mFlagsWithAllCheckEnabled,
                        mMockDebugFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        customAudienceServiceFilterMock,
                        new AdFilteringFeatureFactory(
                                mAppInstallDaoMock,
                                mFrequencyCapDaoMock,
                                mFlagsWithAllCheckEnabled));

        service.joinCustomAudience(
                VALID_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                mICustomAudienceCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);

        verify(mCustomAudienceImplMock)
                .joinCustomAudience(
                        VALID_CUSTOM_AUDIENCE,
                        CustomAudienceFixture.VALID_OWNER,
                        DevContext.createForDevOptionsDisabled());

        verify(mConsentManagerMock).isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        verify(() -> BackgroundFetchJob.schedule(mFlagsWithAllCheckEnabled));
        verify(mICustomAudienceCallbackMock).onSuccess();
        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_SUCCESS);

        verify(customAudienceServiceFilterMock)
                .filterRequest(
                        any(),
                        any(),
                        anyBoolean(),
                        anyBoolean(),
                        eq(false),
                        anyInt(),
                        anyInt(),
                        any(),
                        any());

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testJoinCustomAudienceWithRevokedUserConsentSuccess() throws RemoteException {
        doReturn(true)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        mService.joinCustomAudience(
                VALID_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                mICustomAudienceCallbackMock);
        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        any(),
                        eq(CustomAudienceFixture.VALID_OWNER),
                        eq(AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE),
                        eq(AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE));
        verify(mICustomAudienceCallbackMock).onSuccess();
        verify(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE);
        verify(mFledgeAuthorizationFilterMock)
                .assertAdTechAllowed(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mConsentManagerMock).isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());
        verify(mFledgeAllowListsFilterMock)
                .assertAppInAllowlist(
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mAppImportanceFilterMock)
                .assertCallerIsInForeground(
                        MY_UID, AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE, null);

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_USER_CONSENT_REVOKED);

        verifyNoMoreMockInteractions();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CUSTOM_AUDIENCE_SERVICE_GET_CALLING_UID_ILLEGAL_STATE,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__JOIN_CUSTOM_AUDIENCE,
            throwable = IllegalStateException.class)
    public void testJoinCustomAudience_notInBinderThread() {
        mService =
                new CustomAudienceServiceImpl(
                        sContext,
                        mCustomAudienceImplMock,
                        mFledgeAuthorizationFilterMock,
                        mConsentManagerMock,
                        mDevContextFilterMock,
                        DIRECT_EXECUTOR,
                        mAdServicesLoggerMock,
                        mAppImportanceFilterMock,
                        mFlagsWithAllCheckEnabled,
                        mMockDebugFlags,
                        CallingAppUidSupplierFailureImpl.create(),
                        new CustomAudienceServiceFilter(
                                sContext,
                                mFledgeConsentFilterMock,
                                mFlagsWithAllCheckEnabled,
                                mAppImportanceFilterMock,
                                mFledgeAuthorizationFilterMock,
                                mFledgeAllowListsFilterMock,
                                mFledgeApiThrottleFilterMock),
                        new AdFilteringFeatureFactory(
                                mAppInstallDaoMock,
                                mFrequencyCapDaoMock,
                                mFlagsWithAllCheckEnabled));

        assertThrows(
                IllegalStateException.class,
                () ->
                        mService.joinCustomAudience(
                                VALID_CUSTOM_AUDIENCE,
                                CustomAudienceFixture.VALID_OWNER,
                                mICustomAudienceCallbackMock));

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                TEST_PACKAGE_NAME,
                STATUS_INTERNAL_ERROR);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testJoinCustomAudience_ownerAssertFailed() throws RemoteException {
        doThrow(new FledgeAuthorizationFilter.CallerMismatchException())
                .when(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE);

        mService.joinCustomAudience(
                VALID_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                mICustomAudienceCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        verifyErrorResponseICustomAudienceCallback(
                STATUS_UNAUTHORIZED, SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE);
        verify(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE);

        verifyNoMoreMockInteractions();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            throwable = NullPointerException.class,
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CUSTOM_AUDIENCE_SERVICE_NULL_ARGUMENT,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__JOIN_CUSTOM_AUDIENCE)
    public void testJoinCustomAudience_nullInput() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mService.joinCustomAudience(
                                null,
                                CustomAudienceFixture.VALID_OWNER,
                                mICustomAudienceCallbackMock));

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                TEST_PACKAGE_NAME,
                STATUS_INVALID_ARGUMENT);

        verifyNoMoreMockInteractions();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CUSTOM_AUDIENCE_SERVICE_NULL_ARGUMENT,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__JOIN_CUSTOM_AUDIENCE,
            throwable = NullPointerException.class)
    public void testJoinCustomAudience_nullCallerPackageName() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mService.joinCustomAudience(
                                VALID_CUSTOM_AUDIENCE, null, mICustomAudienceCallbackMock));

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                null,
                STATUS_INVALID_ARGUMENT);

        verifyNoMoreMockInteractions();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CUSTOM_AUDIENCE_SERVICE_NULL_ARGUMENT,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__JOIN_CUSTOM_AUDIENCE,
            throwable = NullPointerException.class)
    public void testJoinCustomAudience_nullCallback() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mService.joinCustomAudience(
                                VALID_CUSTOM_AUDIENCE, CustomAudienceFixture.VALID_OWNER, null));

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                TEST_PACKAGE_NAME,
                STATUS_INVALID_ARGUMENT);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testJoinCustomAudience_errorCreateCustomAudience() throws RemoteException {
        String errorMessage = "Simulating Error creating Custom Audience";
        doThrow(new RuntimeException(errorMessage))
                .when(mCustomAudienceImplMock)
                .joinCustomAudience(
                        VALID_CUSTOM_AUDIENCE,
                        CustomAudienceFixture.VALID_OWNER,
                        DevContext.createForDevOptionsDisabled());

        mService.joinCustomAudience(
                VALID_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                mICustomAudienceCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        verify(mCustomAudienceImplMock)
                .joinCustomAudience(
                        VALID_CUSTOM_AUDIENCE,
                        CustomAudienceFixture.VALID_OWNER,
                        DevContext.createForDevOptionsDisabled());
        verifyErrorResponseICustomAudienceCallback(STATUS_INTERNAL_ERROR, errorMessage);
        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_INTERNAL_ERROR);
        verify(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE);
        verify(mFledgeAuthorizationFilterMock)
                .assertAdTechAllowed(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mAppImportanceFilterMock)
                .assertCallerIsInForeground(
                        MY_UID, AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE, null);
        verify(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        CustomAudienceFixture.VALID_OWNER);
        verify(mFledgeAllowListsFilterMock)
                .assertAppInAllowlist(
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_INTERNAL_ERROR);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testJoinCustomAudience_errorReturnCallback() throws RemoteException {
        doThrow(RemoteException.class).when(mICustomAudienceCallbackMock).onSuccess();

        mService.joinCustomAudience(
                VALID_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                mICustomAudienceCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        verify(mCustomAudienceImplMock)
                .joinCustomAudience(
                        VALID_CUSTOM_AUDIENCE,
                        CustomAudienceFixture.VALID_OWNER,
                        DevContext.createForDevOptionsDisabled());
        verify(() -> BackgroundFetchJob.schedule(any()));
        verify(mICustomAudienceCallbackMock).onSuccess();
        verify(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE);
        verify(mFledgeAuthorizationFilterMock)
                .assertAdTechAllowed(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mAppImportanceFilterMock)
                .assertCallerIsInForeground(
                        MY_UID, AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE, null);
        verify(mFledgeAllowListsFilterMock)
                .assertAppInAllowlist(
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        CustomAudienceFixture.VALID_OWNER);
        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_INTERNAL_ERROR);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testJoinCustomAudience_devOptionsEnabled() throws RemoteException {
        DevContext devContextEnabled =
                DevContext.builder(mPackageName).setDeviceDevOptionsEnabled(true).build();
        Mockito.lenient()
                .doReturn(devContextEnabled)
                .when(mDevContextFilterMock)
                .createDevContext();

        mService.joinCustomAudience(
                VALID_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                mICustomAudienceCallbackMock);
        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        verify(mCustomAudienceImplMock)
                .joinCustomAudience(
                        VALID_CUSTOM_AUDIENCE,
                        CustomAudienceFixture.VALID_OWNER,
                        devContextEnabled);
        verify(() -> BackgroundFetchJob.schedule(mFlagsWithAllCheckEnabled));
        verify(mICustomAudienceCallbackMock).onSuccess();
        verify(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE);
        verify(mAppImportanceFilterMock)
                .assertCallerIsInForeground(
                        MY_UID, AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE, null);
        verify(mFledgeAuthorizationFilterMock)
                .assertAdTechAllowed(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mConsentManagerMock).isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());
        verify(mFledgeAllowListsFilterMock)
                .assertAppInAllowlist(
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_SUCCESS);

        verifyNoMoreMockInteractions();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CUSTOM_AUDIENCE_SERVICE_NULL_ARGUMENT,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE,
            throwable = NullPointerException.class)
    public void testFetchCustomAudience_nullCallback() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mService.fetchAndJoinCustomAudience(
                                new FetchAndJoinCustomAudienceInput.Builder(
                                                CustomAudienceFixture.getValidFetchUriByBuyer(
                                                        CommonFixture.VALID_BUYER_1),
                                                CustomAudienceFixture.VALID_OWNER)
                                        .build(),
                                null));

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE,
                STATUS_INVALID_ARGUMENT);

        verifyNoMoreMockInteractions();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CUSTOM_AUDIENCE_SERVICE_NULL_ARGUMENT,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE,
            throwable = NullPointerException.class)
    public void testFetchCustomAudience_nullInput() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mService.fetchAndJoinCustomAudience(
                                null, mFetchAndJoinCustomAudienceCallbackMock));

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE,
                STATUS_INVALID_ARGUMENT);

        verifyNoMoreMockInteractions();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CUSTOM_AUDIENCE_SERVICE_GET_CALLING_UID_ILLEGAL_STATE,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE,
            throwable = IllegalStateException.class)
    public void testFetchAndJoinCustomAudience_notInBinderThread() {
        mService =
                new CustomAudienceServiceImpl(
                        sContext,
                        mCustomAudienceImplMock,
                        mFledgeAuthorizationFilterMock,
                        mConsentManagerMock,
                        mDevContextFilterMock,
                        DIRECT_EXECUTOR,
                        mAdServicesLoggerMock,
                        mAppImportanceFilterMock,
                        mFlagsWithAllCheckEnabled,
                        mMockDebugFlags,
                        CallingAppUidSupplierFailureImpl.create(),
                        new CustomAudienceServiceFilter(
                                sContext,
                                mFledgeConsentFilterMock,
                                mFlagsWithAllCheckEnabled,
                                mAppImportanceFilterMock,
                                mFledgeAuthorizationFilterMock,
                                mFledgeAllowListsFilterMock,
                                mFledgeApiThrottleFilterMock),
                        new AdFilteringFeatureFactory(
                                mAppInstallDaoMock,
                                mFrequencyCapDaoMock,
                                mFlagsWithAllCheckEnabled));

        assertThrows(
                IllegalStateException.class,
                () ->
                        mService.fetchAndJoinCustomAudience(
                                new FetchAndJoinCustomAudienceInput.Builder(
                                                CustomAudienceFixture.getValidFetchUriByBuyer(
                                                        CommonFixture.VALID_BUYER_1),
                                                CustomAudienceFixture.VALID_OWNER)
                                        .build(),
                                mFetchAndJoinCustomAudienceCallbackMock));

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE,
                TEST_PACKAGE_NAME,
                STATUS_INTERNAL_ERROR);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testLeaveCustomAudience_runNormally() throws RemoteException {
        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        any(),
                        eq(CustomAudienceFixture.VALID_OWNER),
                        eq(AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE),
                        eq(AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE));
        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        CustomAudienceFixture.VALID_NAME);
        verify(mICustomAudienceCallbackMock).onSuccess();
        verify(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE);
        verify(mFledgeAuthorizationFilterMock)
                .assertAdTechAllowed(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mAppImportanceFilterMock)
                .assertCallerIsInForeground(
                        MY_UID, AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE, null);
        verify(mFledgeAllowListsFilterMock)
                .assertAppInAllowlist(
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mConsentManagerMock).isFledgeConsentRevokedForApp(CustomAudienceFixture.VALID_OWNER);

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_SUCCESS);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testLeaveCustomAudience_runNormallyWithUNotificationEnforcementDisabled()
            throws RemoteException {
        CustomAudienceServiceFilter customAudienceServiceFilterMock =
                Mockito.mock(CustomAudienceServiceFilter.class);
        mocker.mockGetConsentNotificationDebugMode(true);

        CustomAudienceServiceImpl service =
                new CustomAudienceServiceImpl(
                        sContext,
                        mCustomAudienceImplMock,
                        mFledgeAuthorizationFilterMock,
                        mConsentManagerMock,
                        mDevContextFilterMock,
                        DIRECT_EXECUTOR,
                        mAdServicesLoggerMock,
                        mAppImportanceFilterMock,
                        mFlagsWithAllCheckEnabled,
                        mMockDebugFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        customAudienceServiceFilterMock,
                        new AdFilteringFeatureFactory(
                                mAppInstallDaoMock,
                                mFrequencyCapDaoMock,
                                mFlagsWithAllCheckEnabled));

        service.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        any(),
                        eq(CustomAudienceFixture.VALID_OWNER),
                        eq(AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE),
                        eq(AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE));

        verify(mConsentManagerMock).isFledgeConsentRevokedForApp(any());

        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        CustomAudienceFixture.VALID_NAME);
        verify(mICustomAudienceCallbackMock).onSuccess();
        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_SUCCESS);

        verify(customAudienceServiceFilterMock)
                .filterRequest(
                        any(),
                        any(),
                        anyBoolean(),
                        anyBoolean(),
                        eq(false),
                        anyInt(),
                        anyInt(),
                        any(),
                        any());

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testLeaveCustomAudienceWithRevokedUserConsent() throws RemoteException {
        doReturn(true).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());

        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        any(),
                        eq(CustomAudienceFixture.VALID_OWNER),
                        eq(AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE),
                        eq(AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE));
        verify(mICustomAudienceCallbackMock).onSuccess();
        verify(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE);
        verify(mFledgeAuthorizationFilterMock)
                .assertAdTechAllowed(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mFledgeAllowListsFilterMock)
                .assertAppInAllowlist(
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mConsentManagerMock).isFledgeConsentRevokedForApp(CustomAudienceFixture.VALID_OWNER);
        verify(mAppImportanceFilterMock)
                .assertCallerIsInForeground(
                        MY_UID, AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE, null);

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_USER_CONSENT_REVOKED);

        verifyNoMoreMockInteractions();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CUSTOM_AUDIENCE_SERVICE_GET_CALLING_UID_ILLEGAL_STATE,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__LEAVE_CUSTOM_AUDIENCE,
            throwable = IllegalStateException.class)
    public void testLeaveCustomAudience_notInBinderThread() {
        mService =
                new CustomAudienceServiceImpl(
                        sContext,
                        mCustomAudienceImplMock,
                        mFledgeAuthorizationFilterMock,
                        mConsentManagerMock,
                        mDevContextFilterMock,
                        DIRECT_EXECUTOR,
                        mAdServicesLoggerMock,
                        mAppImportanceFilterMock,
                        mFlagsWithAllCheckEnabled,
                        mMockDebugFlags,
                        CallingAppUidSupplierFailureImpl.create(),
                        new CustomAudienceServiceFilter(
                                sContext,
                                mFledgeConsentFilterMock,
                                mFlagsWithAllCheckEnabled,
                                mAppImportanceFilterMock,
                                mFledgeAuthorizationFilterMock,
                                mFledgeAllowListsFilterMock,
                                mFledgeApiThrottleFilterMock),
                        new AdFilteringFeatureFactory(
                                mAppInstallDaoMock,
                                mFrequencyCapDaoMock,
                                mFlagsWithAllCheckEnabled));

        assertThrows(
                IllegalStateException.class,
                () ->
                        mService.leaveCustomAudience(
                                CustomAudienceFixture.VALID_OWNER,
                                CommonFixture.VALID_BUYER_1,
                                CustomAudienceFixture.VALID_NAME,
                                mICustomAudienceCallbackMock));

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        any(),
                        eq(CustomAudienceFixture.VALID_OWNER),
                        eq(AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE),
                        eq(AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE));
        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                TEST_PACKAGE_NAME,
                STATUS_INTERNAL_ERROR);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testLeaveCustomAudience_ownerAssertFailed() throws RemoteException {
        doThrow(new FledgeAuthorizationFilter.CallerMismatchException())
                .when(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE);

        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        any(),
                        eq(CustomAudienceFixture.VALID_OWNER),
                        eq(AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE),
                        eq(AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE));
        verifyErrorResponseICustomAudienceCallback(
                STATUS_UNAUTHORIZED, SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE);
        verify(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE);

        verifyNoMoreMockInteractions();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CUSTOM_AUDIENCE_SERVICE_NULL_ARGUMENT,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__LEAVE_CUSTOM_AUDIENCE,
            throwable = NullPointerException.class)
    public void testLeaveCustomAudience_nullOwner() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mService.leaveCustomAudience(
                                null,
                                CommonFixture.VALID_BUYER_1,
                                CustomAudienceFixture.VALID_NAME,
                                mICustomAudienceCallbackMock));

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                null,
                STATUS_INVALID_ARGUMENT);

        verifyNoMoreMockInteractions();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CUSTOM_AUDIENCE_SERVICE_NULL_ARGUMENT,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__LEAVE_CUSTOM_AUDIENCE,
            throwable = NullPointerException.class)
    public void testLeaveCustomAudience_nullBuyer() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mService.leaveCustomAudience(
                                CustomAudienceFixture.VALID_OWNER,
                                null,
                                CustomAudienceFixture.VALID_NAME,
                                mICustomAudienceCallbackMock));

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                TEST_PACKAGE_NAME,
                STATUS_INVALID_ARGUMENT);

        verifyNoMoreMockInteractions();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CUSTOM_AUDIENCE_SERVICE_NULL_ARGUMENT,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__LEAVE_CUSTOM_AUDIENCE,
            throwable = NullPointerException.class)
    public void testLeaveCustomAudience_nullName() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mService.leaveCustomAudience(
                                CustomAudienceFixture.VALID_OWNER,
                                CommonFixture.VALID_BUYER_1,
                                null,
                                mICustomAudienceCallbackMock));

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                TEST_PACKAGE_NAME,
                STATUS_INVALID_ARGUMENT);

        verifyNoMoreMockInteractions();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CUSTOM_AUDIENCE_SERVICE_NULL_ARGUMENT,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__LEAVE_CUSTOM_AUDIENCE,
            throwable = NullPointerException.class)
    public void testLeaveCustomAudience_nullCallback() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mService.leaveCustomAudience(
                                CustomAudienceFixture.VALID_OWNER,
                                CommonFixture.VALID_BUYER_1,
                                CustomAudienceFixture.VALID_NAME,
                                null));

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                TEST_PACKAGE_NAME,
                STATUS_INVALID_ARGUMENT);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testLeaveCustomAudience_errorCallCustomAudienceImpl() throws RemoteException {
        doThrow(new RuntimeException("Simulating Error calling CustomAudienceImpl"))
                .when(mCustomAudienceImplMock)
                .leaveCustomAudience(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        CustomAudienceFixture.VALID_NAME);

        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        any(),
                        eq(CustomAudienceFixture.VALID_OWNER),
                        eq(AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE),
                        eq(AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE));
        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        CustomAudienceFixture.VALID_NAME);
        verify(mICustomAudienceCallbackMock).onSuccess();
        verify(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE);
        verify(mFledgeAuthorizationFilterMock)
                .assertAdTechAllowed(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mAppImportanceFilterMock)
                .assertCallerIsInForeground(
                        MY_UID, AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE, null);
        verify(mFledgeAllowListsFilterMock)
                .assertAppInAllowlist(
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mConsentManagerMock).isFledgeConsentRevokedForApp(CustomAudienceFixture.VALID_OWNER);

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_INTERNAL_ERROR);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testLeaveCustomAudience_errorReturnCallback() throws RemoteException {
        doThrow(RemoteException.class).when(mICustomAudienceCallbackMock).onSuccess();

        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallbackMock);

        verify(mAppImportanceFilterMock)
                .assertCallerIsInForeground(
                        MY_UID, AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE, null);
        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        any(),
                        eq(CustomAudienceFixture.VALID_OWNER),
                        eq(AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE),
                        eq(AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE));
        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        CustomAudienceFixture.VALID_NAME);
        verify(mICustomAudienceCallbackMock).onSuccess();
        verify(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE);
        verify(mFledgeAuthorizationFilterMock)
                .assertAdTechAllowed(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mFledgeAllowListsFilterMock)
                .assertAppInAllowlist(
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mConsentManagerMock).isFledgeConsentRevokedForApp(CustomAudienceFixture.VALID_OWNER);
        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_INTERNAL_ERROR);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testAppImportanceTestFails_joinCustomAudienceThrowsException()
            throws RemoteException {
        doThrow(new WrongCallingApplicationStateException())
                .when(mAppImportanceFilterMock)
                .assertCallerIsInForeground(
                        MY_UID, AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE, null);

        mService.joinCustomAudience(
                VALID_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                mICustomAudienceCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        verify(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE);
        verify(mAppImportanceFilterMock)
                .assertCallerIsInForeground(
                        MY_UID, AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE, null);
        verifyErrorResponseICustomAudienceCallback(
                STATUS_BACKGROUND_CALLER, ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testAppImportanceDisabledCallerInBackground_joinCustomAudienceSucceeds()
            throws RemoteException {
        mService =
                new CustomAudienceServiceImpl(
                        sContext,
                        mCustomAudienceImplMock,
                        mFledgeAuthorizationFilterMock,
                        mConsentManagerMock,
                        mDevContextFilterMock,
                        DIRECT_EXECUTOR,
                        mAdServicesLoggerMock,
                        mAppImportanceFilterMock,
                        mFlagsWithForegroundCheckDisabled,
                        mMockDebugFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        new CustomAudienceServiceFilter(
                                sContext,
                                mFledgeConsentFilterMock,
                                mFlagsWithForegroundCheckDisabled,
                                mAppImportanceFilterMock,
                                mFledgeAuthorizationFilterMock,
                                mFledgeAllowListsFilterMock,
                                mFledgeApiThrottleFilterMock),
                        new AdFilteringFeatureFactory(
                                mAppInstallDaoMock,
                                mFrequencyCapDaoMock,
                                mFlagsWithForegroundCheckDisabled));

        mService.joinCustomAudience(
                VALID_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                mICustomAudienceCallbackMock);
        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        verify(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE);
        verify(mFledgeAuthorizationFilterMock)
                .assertAdTechAllowed(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mFledgeAllowListsFilterMock)
                .assertAppInAllowlist(
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        CustomAudienceFixture.VALID_OWNER);
        verify(mCustomAudienceImplMock)
                .joinCustomAudience(
                        VALID_CUSTOM_AUDIENCE,
                        CustomAudienceFixture.VALID_OWNER,
                        DevContext.createForDevOptionsDisabled());
        verify(() -> BackgroundFetchJob.schedule(any()));
        verify(mICustomAudienceCallbackMock).onSuccess();
        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_SUCCESS);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testAppImportanceTestFails_leaveCustomAudienceThrowsException()
            throws RemoteException {
        doThrow(new WrongCallingApplicationStateException())
                .when(mAppImportanceFilterMock)
                .assertCallerIsInForeground(
                        MY_UID, AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE, null);

        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        verify(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE);
        verify(mAppImportanceFilterMock)
                .assertCallerIsInForeground(
                        MY_UID, AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE, null);
        verifyErrorResponseICustomAudienceCallback(
                AdServicesStatusUtils.STATUS_BACKGROUND_CALLER,
                ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testAppImportanceDisabledCallerInBackground_leaveCustomAudienceSucceeds()
            throws RemoteException {
        mService =
                new CustomAudienceServiceImpl(
                        sContext,
                        mCustomAudienceImplMock,
                        mFledgeAuthorizationFilterMock,
                        mConsentManagerMock,
                        mDevContextFilterMock,
                        DIRECT_EXECUTOR,
                        mAdServicesLoggerMock,
                        mAppImportanceFilterMock,
                        mFlagsWithForegroundCheckDisabled,
                        mMockDebugFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        new CustomAudienceServiceFilter(
                                sContext,
                                mFledgeConsentFilterMock,
                                mFlagsWithForegroundCheckDisabled,
                                mAppImportanceFilterMock,
                                mFledgeAuthorizationFilterMock,
                                mFledgeAllowListsFilterMock,
                                mFledgeApiThrottleFilterMock),
                        new AdFilteringFeatureFactory(
                                mAppInstallDaoMock,
                                mFrequencyCapDaoMock,
                                mFlagsWithForegroundCheckDisabled));

        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        verify(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE);
        verify(mFledgeAuthorizationFilterMock)
                .assertAdTechAllowed(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mAppImportanceFilterMock)
                .assertCallerIsInForeground(
                        MY_UID, AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE, null);
        verify(mFledgeAllowListsFilterMock)
                .assertAppInAllowlist(
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mConsentManagerMock).isFledgeConsentRevokedForApp(CustomAudienceFixture.VALID_OWNER);
        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        CustomAudienceFixture.VALID_NAME);
        verify(mICustomAudienceCallbackMock).onSuccess();
        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_SUCCESS);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testAppImportanceTestFails_overrideCustomAudienceThrowsException()
            throws RemoteException {
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder(mPackageName).setDeviceDevOptionsEnabled(true).build());
        when(mCustomAudienceImplMock.getCustomAudienceDao()).thenReturn(mCustomAudienceDaoMock);
        doThrow(new WrongCallingApplicationStateException())
                .when(mAppImportanceFilterMock)
                .assertCallerIsInForeground(
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO,
                        null);

        mService.overrideCustomAudienceRemoteInfo(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                CustomAudienceFixture.VALID_NAME,
                "",
                JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3,
                AdSelectionSignals.EMPTY,
                mCustomAudienceOverrideCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        verify(mDevContextFilterMock).createDevContext();
        verify(mCustomAudienceImplMock).getCustomAudienceDao();
        verify(mAppImportanceFilterMock)
                .assertCallerIsInForeground(
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO,
                        null);
        verifyErrorResponseCustomAudienceOverrideCallback(
                AdServicesStatusUtils.STATUS_BACKGROUND_CALLER,
                ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE);
        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO,
                mPackageName,
                STATUS_BACKGROUND_CALLER);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testAppImportanceDisabledCallerInBackground_overrideCustomAudienceSucceeds()
            throws RemoteException {
        when(mCustomAudienceImplMock.getCustomAudienceDao()).thenReturn(mCustomAudienceDaoMock);
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder(CustomAudienceFixture.VALID_OWNER)
                                .setDeviceDevOptionsEnabled(true)
                                .build());
        mService =
                new CustomAudienceServiceImpl(
                        sContext,
                        mCustomAudienceImplMock,
                        mFledgeAuthorizationFilterMock,
                        mConsentManagerMock,
                        mDevContextFilterMock,
                        DIRECT_EXECUTOR,
                        mAdServicesLoggerMock,
                        mAppImportanceFilterMock,
                        mFlagsWithForegroundCheckDisabled,
                        mMockDebugFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        new CustomAudienceServiceFilter(
                                sContext,
                                mFledgeConsentFilterMock,
                                mFlagsWithForegroundCheckDisabled,
                                mAppImportanceFilterMock,
                                mFledgeAuthorizationFilterMock,
                                mFledgeAllowListsFilterMock,
                                mFledgeApiThrottleFilterMock),
                        new AdFilteringFeatureFactory(
                                mAppInstallDaoMock,
                                mFrequencyCapDaoMock,
                                mFlagsWithForegroundCheckDisabled));

        mService.overrideCustomAudienceRemoteInfo(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                CustomAudienceFixture.VALID_NAME,
                "",
                JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3,
                AdSelectionSignals.EMPTY,
                mCustomAudienceOverrideCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        verify(mDevContextFilterMock).createDevContext();
        verify(mCustomAudienceImplMock).getCustomAudienceDao();
        verify(mConsentManagerMock).isFledgeConsentRevokedForApp(CustomAudienceFixture.VALID_OWNER);
        verify(mCustomAudienceDaoMock)
                .persistCustomAudienceOverride(
                        DBCustomAudienceOverride.builder()
                                .setOwner(CustomAudienceFixture.VALID_OWNER)
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .setName(CustomAudienceFixture.VALID_NAME)
                                .setBiddingLogicJS("")
                                .setBiddingLogicJsVersion(
                                        JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3)
                                .setTrustedBiddingData(AdSelectionSignals.EMPTY.toString())
                                .setAppPackageName(CustomAudienceFixture.VALID_OWNER)
                                .build());
        verify(mCustomAudienceOverrideCallbackMock).onSuccess();
        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO,
                TEST_PACKAGE_NAME,
                STATUS_SUCCESS);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testAppImportanceTestFails_removeCustomAudienceOverrideThrowsException()
            throws RemoteException {
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder(mPackageName).setDeviceDevOptionsEnabled(true).build());
        when(mCustomAudienceImplMock.getCustomAudienceDao()).thenReturn(mCustomAudienceDaoMock);
        int apiName = AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE;
        doThrow(new WrongCallingApplicationStateException())
                .when(mAppImportanceFilterMock)
                .assertCallerIsInForeground(CustomAudienceFixture.VALID_OWNER, apiName, null);

        mService.removeCustomAudienceRemoteInfoOverride(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                CustomAudienceFixture.VALID_NAME,
                mCustomAudienceOverrideCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        apiName,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        verify(mDevContextFilterMock).createDevContext();
        verify(mCustomAudienceImplMock).getCustomAudienceDao();
        verify(mAppImportanceFilterMock)
                .assertCallerIsInForeground(CustomAudienceFixture.VALID_OWNER, apiName, null);
        verifyErrorResponseCustomAudienceOverrideCallback(
                STATUS_BACKGROUND_CALLER, ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE);
        verifyLoggerMock(apiName, mPackageName, STATUS_BACKGROUND_CALLER);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testAppImportanceDisabledCallerInBackground_removeCustomAudienceOverrideSucceeds()
            throws RemoteException {
        when(mCustomAudienceImplMock.getCustomAudienceDao()).thenReturn(mCustomAudienceDaoMock);
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder(CustomAudienceFixture.VALID_OWNER)
                                .setDeviceDevOptionsEnabled(true)
                                .build());
        int apiName = AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE;
        mService =
                new CustomAudienceServiceImpl(
                        sContext,
                        mCustomAudienceImplMock,
                        mFledgeAuthorizationFilterMock,
                        mConsentManagerMock,
                        mDevContextFilterMock,
                        DIRECT_EXECUTOR,
                        mAdServicesLoggerMock,
                        mAppImportanceFilterMock,
                        mFlagsWithForegroundCheckDisabled,
                        mMockDebugFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        new CustomAudienceServiceFilter(
                                sContext,
                                mFledgeConsentFilterMock,
                                mFlagsWithForegroundCheckDisabled,
                                mAppImportanceFilterMock,
                                mFledgeAuthorizationFilterMock,
                                mFledgeAllowListsFilterMock,
                                mFledgeApiThrottleFilterMock),
                        new AdFilteringFeatureFactory(
                                mAppInstallDaoMock,
                                mFrequencyCapDaoMock,
                                mFlagsWithForegroundCheckDisabled));

        mService.removeCustomAudienceRemoteInfoOverride(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                CustomAudienceFixture.VALID_NAME,
                mCustomAudienceOverrideCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        apiName,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        verify(mDevContextFilterMock).createDevContext();
        verify(mCustomAudienceImplMock).getCustomAudienceDao();
        verify(mConsentManagerMock).isFledgeConsentRevokedForApp(CustomAudienceFixture.VALID_OWNER);
        verify(mCustomAudienceDaoMock)
                .removeCustomAudienceOverrideByPrimaryKeyAndPackageName(
                        CustomAudienceFixture.VALID_OWNER, CommonFixture.VALID_BUYER_1,
                        CustomAudienceFixture.VALID_NAME, CustomAudienceFixture.VALID_OWNER);
        verify(mCustomAudienceOverrideCallbackMock).onSuccess();
        verifyLoggerMock(apiName, TEST_PACKAGE_NAME, STATUS_SUCCESS);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testAppImportanceTestFails_resetOverridesThrowsException() throws RemoteException {
        when(mCustomAudienceImplMock.getCustomAudienceDao()).thenReturn(mCustomAudienceDaoMock);
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder(CustomAudienceFixture.VALID_OWNER)
                                .setDeviceDevOptionsEnabled(true)
                                .build());
        int apiName = AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES;
        doThrow(new WrongCallingApplicationStateException())
                .when(mAppImportanceFilterMock)
                .assertCallerIsInForeground(Process.myUid(), apiName, null);

        mService.resetAllCustomAudienceOverrides(mCustomAudienceOverrideCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        apiName,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        verify(mDevContextFilterMock).createDevContext();
        verify(mCustomAudienceImplMock).getCustomAudienceDao();
        verify(mAppImportanceFilterMock).assertCallerIsInForeground(Process.myUid(), apiName, null);
        verifyLoggerMock(apiName, TEST_PACKAGE_NAME, STATUS_BACKGROUND_CALLER);
        verifyErrorResponseCustomAudienceOverrideCallback(
                STATUS_BACKGROUND_CALLER, ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testAppImportanceDisabledCallerInBackground_resetOverridesSucceeds()
            throws RemoteException {
        when(mCustomAudienceImplMock.getCustomAudienceDao()).thenReturn(mCustomAudienceDaoMock);
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder(CustomAudienceFixture.VALID_OWNER)
                                .setDeviceDevOptionsEnabled(true)
                                .build());
        mService =
                new CustomAudienceServiceImpl(
                        sContext,
                        mCustomAudienceImplMock,
                        mFledgeAuthorizationFilterMock,
                        mConsentManagerMock,
                        mDevContextFilterMock,
                        DIRECT_EXECUTOR,
                        mAdServicesLoggerMock,
                        mAppImportanceFilterMock,
                        mFlagsWithForegroundCheckDisabled,
                        mMockDebugFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        new CustomAudienceServiceFilter(
                                sContext,
                                mFledgeConsentFilterMock,
                                mFlagsWithForegroundCheckDisabled,
                                mAppImportanceFilterMock,
                                mFledgeAuthorizationFilterMock,
                                mFledgeAllowListsFilterMock,
                                mFledgeApiThrottleFilterMock),
                        new AdFilteringFeatureFactory(
                                mAppInstallDaoMock,
                                mFrequencyCapDaoMock,
                                mFlagsWithForegroundCheckDisabled));

        mService.resetAllCustomAudienceOverrides(mCustomAudienceOverrideCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        verify(mDevContextFilterMock).createDevContext();
        verify(mCustomAudienceImplMock).getCustomAudienceDao();
        verify(mConsentManagerMock).isFledgeConsentRevokedForApp(CustomAudienceFixture.VALID_OWNER);
        verify(mCustomAudienceDaoMock)
                .removeCustomAudienceOverridesByPackageName(CustomAudienceFixture.VALID_OWNER);
        verify(mCustomAudienceOverrideCallbackMock).onSuccess();
        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES,
                TEST_PACKAGE_NAME,
                STATUS_SUCCESS);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testAppManifestPermissionNotRequested_joinCustomAudience_fails() {
        doThrow(SecurityException.class)
                .when(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);

        assertThrows(
                SecurityException.class,
                () ->
                        mService.joinCustomAudience(
                                VALID_CUSTOM_AUDIENCE,
                                CustomAudienceFixture.VALID_OWNER,
                                mICustomAudienceCallbackMock));

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testAppManifestPermissionNotRequested_fetchCustomAudience_fails() {
        doThrow(SecurityException.class)
                .when(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);

        assertThrows(
                SecurityException.class,
                () ->
                        mService.fetchAndJoinCustomAudience(
                                new FetchAndJoinCustomAudienceInput.Builder(
                                                CustomAudienceFixture.getValidFetchUriByBuyer(
                                                        CommonFixture.VALID_BUYER_1),
                                                CustomAudienceFixture.VALID_OWNER)
                                        .build(),
                                mFetchAndJoinCustomAudienceCallbackMock));

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testAppManifestPermissionNotRequested_leaveCustomAudience_fails() {
        doThrow(SecurityException.class)
                .when(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);

        assertThrows(
                SecurityException.class,
                () ->
                        mService.leaveCustomAudience(
                                CustomAudienceFixture.VALID_OWNER,
                                CommonFixture.VALID_BUYER_1,
                                CustomAudienceFixture.VALID_NAME,
                                mICustomAudienceCallbackMock));

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testAppManifestPermissionNotRequested_overrideCustomAudienceRemoteInfo_fails() {
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder(CustomAudienceFixture.VALID_OWNER)
                                .setDeviceDevOptionsEnabled(true)
                                .build());
        doThrow(SecurityException.class)
                .when(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);

        assertThrows(
                SecurityException.class,
                () ->
                        mService.overrideCustomAudienceRemoteInfo(
                                CustomAudienceFixture.VALID_OWNER,
                                CommonFixture.VALID_BUYER_1,
                                CustomAudienceFixture.VALID_NAME,
                                "",
                                JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3,
                                AdSelectionSignals.EMPTY,
                                mCustomAudienceOverrideCallbackMock));

        verifyNoMoreMockInteractions();
    }

    @Test
    public void
            testAppManifestPermissionNotRequested_removeCustomAudienceRemoteInfoOverride_fails() {
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder(CustomAudienceFixture.VALID_OWNER)
                                .setDeviceDevOptionsEnabled(true)
                                .build());
        int apiName = AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE;
        doThrow(SecurityException.class)
                .when(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        apiName,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);

        assertThrows(
                SecurityException.class,
                () ->
                        mService.removeCustomAudienceRemoteInfoOverride(
                                CustomAudienceFixture.VALID_OWNER,
                                CommonFixture.VALID_BUYER_1,
                                CustomAudienceFixture.VALID_NAME,
                                mCustomAudienceOverrideCallbackMock));

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testAppManifestPermissionNotRequested_resetAllCustomAudienceOverrides_fails() {
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder(CustomAudienceFixture.VALID_OWNER)
                                .setDeviceDevOptionsEnabled(true)
                                .build());
        doThrow(SecurityException.class)
                .when(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);

        assertThrows(
                SecurityException.class,
                () ->
                        mService.resetAllCustomAudienceOverrides(
                                mCustomAudienceOverrideCallbackMock));

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testEnrollmentCheckEnabledWithNoEnrollment_joinCustomAudience_fails()
            throws RemoteException {
        doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mFledgeAuthorizationFilterMock)
                .assertAdTechAllowed(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);

        mService.joinCustomAudience(
                VALID_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                mICustomAudienceCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        verify(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE);
        verify(mFledgeAuthorizationFilterMock)
                .assertAdTechAllowed(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mAppImportanceFilterMock)
                .assertCallerIsInForeground(
                        MY_UID, AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE, null);

        verifyErrorResponseICustomAudienceCallback(
                STATUS_CALLER_NOT_ALLOWED, SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testEnrollmentCheckDisabled_joinCustomAudience_runNormally()
            throws RemoteException {
        mService =
                new CustomAudienceServiceImpl(
                        sContext,
                        mCustomAudienceImplMock,
                        mFledgeAuthorizationFilterMock,
                        mConsentManagerMock,
                        mDevContextFilterMock,
                        DIRECT_EXECUTOR,
                        mAdServicesLoggerMock,
                        mAppImportanceFilterMock,
                        mFlagsWithEnrollmentCheckDisabled,
                        mMockDebugFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        new CustomAudienceServiceFilter(
                                sContext,
                                mFledgeConsentFilterMock,
                                mFlagsWithEnrollmentCheckDisabled,
                                mAppImportanceFilterMock,
                                mFledgeAuthorizationFilterMock,
                                mFledgeAllowListsFilterMock,
                                mFledgeApiThrottleFilterMock),
                        new AdFilteringFeatureFactory(
                                mAppInstallDaoMock,
                                mFrequencyCapDaoMock,
                                mFlagsWithEnrollmentCheckDisabled));

        mService.joinCustomAudience(
                VALID_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                mICustomAudienceCallbackMock);
        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        verify(mCustomAudienceImplMock)
                .joinCustomAudience(
                        VALID_CUSTOM_AUDIENCE,
                        CustomAudienceFixture.VALID_OWNER,
                        DevContext.createForDevOptionsDisabled());
        verify(() -> BackgroundFetchJob.schedule(any()));
        verify(mICustomAudienceCallbackMock).onSuccess();
        verify(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE);
        verify(mAppImportanceFilterMock)
                .assertCallerIsInForeground(
                        MY_UID, AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE, null);
        verify(mFledgeAllowListsFilterMock)
                .assertAppInAllowlist(
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mConsentManagerMock).isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_SUCCESS);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testEnrollmentCheckEnabledWithNoEnrollment_leaveCustomAudience_fails()
            throws RemoteException {
        doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mFledgeAuthorizationFilterMock)
                .assertAdTechAllowed(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);

        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        verify(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE);
        verify(mFledgeAuthorizationFilterMock)
                .assertAdTechAllowed(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mAppImportanceFilterMock)
                .assertCallerIsInForeground(
                        MY_UID, AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE, null);

        verifyErrorResponseICustomAudienceCallback(
                STATUS_CALLER_NOT_ALLOWED, SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testEnrollmentCheckDisabled_leaveCustomAudience_runNormally()
            throws RemoteException {
        mService =
                new CustomAudienceServiceImpl(
                        sContext,
                        mCustomAudienceImplMock,
                        mFledgeAuthorizationFilterMock,
                        mConsentManagerMock,
                        mDevContextFilterMock,
                        DIRECT_EXECUTOR,
                        mAdServicesLoggerMock,
                        mAppImportanceFilterMock,
                        mFlagsWithEnrollmentCheckDisabled,
                        mMockDebugFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        new CustomAudienceServiceFilter(
                                sContext,
                                mFledgeConsentFilterMock,
                                mFlagsWithEnrollmentCheckDisabled,
                                mAppImportanceFilterMock,
                                mFledgeAuthorizationFilterMock,
                                mFledgeAllowListsFilterMock,
                                mFledgeApiThrottleFilterMock),
                        new AdFilteringFeatureFactory(
                                mAppInstallDaoMock,
                                mFrequencyCapDaoMock,
                                mFlagsWithEnrollmentCheckDisabled));

        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        any(),
                        eq(CustomAudienceFixture.VALID_OWNER),
                        eq(AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE),
                        eq(AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE));
        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        CustomAudienceFixture.VALID_NAME);
        verify(mICustomAudienceCallbackMock).onSuccess();
        verify(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE);
        verify(mAppImportanceFilterMock)
                .assertCallerIsInForeground(
                        MY_UID, AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE, null);
        verify(mFledgeAllowListsFilterMock)
                .assertAppInAllowlist(
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mConsentManagerMock).isFledgeConsentRevokedForApp(CustomAudienceFixture.VALID_OWNER);

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_SUCCESS);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testNotInAllowList_joinCustomAudience_fail() throws RemoteException {
        doThrow(new FledgeAllowListsFilter.AppNotAllowedException())
                .when(mFledgeAllowListsFilterMock)
                .assertAppInAllowlist(
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);

        mService.joinCustomAudience(
                VALID_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                mICustomAudienceCallbackMock);
        verify(mFledgeAuthorizationFilterMock)
                .assertAdTechAllowed(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        verify(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE);
        verify(mAppImportanceFilterMock)
                .assertCallerIsInForeground(
                        MY_UID, AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE, null);
        verify(mFledgeAllowListsFilterMock)
                .assertAppInAllowlist(
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verifyErrorResponseICustomAudienceCallback(
                STATUS_CALLER_NOT_ALLOWED, SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testNotInAllowList_leaveCustomAudience_fail() throws RemoteException {
        doThrow(new FledgeAllowListsFilter.AppNotAllowedException())
                .when(mFledgeAllowListsFilterMock)
                .assertAppInAllowlist(
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);

        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallbackMock);
        verify(mFledgeAuthorizationFilterMock)
                .assertAdTechAllowed(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        verify(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE);
        verify(mAppImportanceFilterMock)
                .assertCallerIsInForeground(
                        MY_UID, AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE, null);
        verify(mFledgeAllowListsFilterMock)
                .assertAppInAllowlist(
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        API_CUSTOM_AUDIENCES);
        verifyErrorResponseICustomAudienceCallback(
                STATUS_CALLER_NOT_ALLOWED, SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testJoinCustomAudience_throttledFailure() throws RemoteException {
        // Throttle Join Custom Audience
        doThrow(new LimitExceededException(RATE_LIMIT_REACHED_ERROR_MESSAGE))
                .when(mFledgeApiThrottleFilterMock)
                .assertCallerNotThrottled(anyString(), any(), anyInt());

        mService.joinCustomAudience(
                VALID_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                mICustomAudienceCallbackMock);

        ArgumentCaptor<FledgeErrorResponse> actualResponseCaptor =
                ArgumentCaptor.forClass(FledgeErrorResponse.class);
        verify(mICustomAudienceCallbackMock).onFailure(actualResponseCaptor.capture());
        assertEquals(STATUS_RATE_LIMIT_REACHED, actualResponseCaptor.getValue().getStatusCode());
        assertEquals(
                RATE_LIMIT_REACHED_ERROR_MESSAGE,
                actualResponseCaptor.getValue().getErrorMessage());
        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        verify(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE);

        verifyNoMoreMockInteractions();
    }

    @Test
    public void testLeaveCustomAudience_throttledFailure() throws RemoteException {
        // Throttle Leave Custom Audience
        doThrow(new LimitExceededException(RATE_LIMIT_REACHED_ERROR_MESSAGE))
                .when(mFledgeApiThrottleFilterMock)
                .assertCallerNotThrottled(anyString(), any(), anyInt());

        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallbackMock);

        ArgumentCaptor<FledgeErrorResponse> actualResponseCaptor =
                ArgumentCaptor.forClass(FledgeErrorResponse.class);
        verify(mICustomAudienceCallbackMock).onFailure(actualResponseCaptor.capture());
        assertEquals(STATUS_RATE_LIMIT_REACHED, actualResponseCaptor.getValue().getStatusCode());
        assertEquals(
                RATE_LIMIT_REACHED_ERROR_MESSAGE,
                actualResponseCaptor.getValue().getErrorMessage());
        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        sContext,
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        verify(mFledgeAuthorizationFilterMock)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE);

        verifyNoMoreMockInteractions();
    }

    private void verifyErrorResponseICustomAudienceCallback(int statusCode, String errorMessage)
            throws RemoteException {
        ArgumentCaptor<FledgeErrorResponse> errorCaptor =
                ArgumentCaptor.forClass(FledgeErrorResponse.class);
        verify(mICustomAudienceCallbackMock).onFailure(errorCaptor.capture());
        assertEquals(statusCode, errorCaptor.getValue().getStatusCode());
        assertEquals(errorMessage, errorCaptor.getValue().getErrorMessage());
    }

    private void verifyErrorResponseCustomAudienceOverrideCallback(
            int statusCode, String errorMessage) throws RemoteException {
        ArgumentCaptor<FledgeErrorResponse> errorCaptor =
                ArgumentCaptor.forClass(FledgeErrorResponse.class);
        verify(mCustomAudienceOverrideCallbackMock).onFailure(errorCaptor.capture());
        assertEquals(statusCode, errorCaptor.getValue().getStatusCode());
        assertEquals(errorMessage, errorCaptor.getValue().getErrorMessage());
    }

    private void verifyLoggerMock(int apiName, int statusCode) {
        verify(mAdServicesLoggerMock).logFledgeApiCallStats(eq(apiName), eq(statusCode), anyInt());
    }

    private void verifyLoggerMock(int apiName, String appPackageName, int statusCode) {
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(eq(apiName), eq(appPackageName), eq(statusCode), anyInt());
    }

    private static class FlagsWithCheckEnabledSwitch implements Flags {
        private final boolean mForegroundCheckEnabled;
        private final boolean mEnrollmentCheckEnabled;

        FlagsWithCheckEnabledSwitch(
                boolean foregroundCheckEnabled, boolean enrollmentCheckEnabled) {
            mForegroundCheckEnabled = foregroundCheckEnabled;
            mEnrollmentCheckEnabled = enrollmentCheckEnabled;
        }

        @Override
        public boolean getEnforceForegroundStatusForFledgeCustomAudience() {
            return mForegroundCheckEnabled;
        }

        @Override
        public boolean getEnforceForegroundStatusForFledgeOverrides() {
            return mForegroundCheckEnabled;
        }

        @Override
        public boolean getDisableFledgeEnrollmentCheck() {
            return !mEnrollmentCheckEnabled;
        }
    }
}
