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

import static android.adservices.common.AdServicesStatusUtils.STATUS_ADSERVICES_DISABLED;

import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertWithMessage;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.CustomAudienceOverrideCallback;
import android.adservices.customaudience.FetchAndJoinCustomAudienceCallback;
import android.adservices.customaudience.FetchAndJoinCustomAudienceInput;
import android.adservices.customaudience.ICustomAudienceCallback;
import android.adservices.customaudience.PartialCustomAudience;
import android.adservices.customaudience.ScheduleCustomAudienceUpdateCallback;
import android.adservices.customaudience.ScheduleCustomAudienceUpdateInput;
import android.os.RemoteException;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.AdFilteringFeatureFactory;
import com.android.adservices.service.adselection.JsVersionRegister;
import com.android.adservices.service.common.AppImportanceFilter;
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
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.time.Duration;
import java.util.concurrent.ExecutorService;

@MockStatic(BackgroundFetchJob.class)
@MockStatic(FlagsFactory.class)
@MockStatic(DebugFlags.class)
@SetErrorLogUtilDefaultParams(
        throwable = Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED)
public final class CustomAudienceServiceImplTest extends AdServicesExtendedMockitoTestCase {

    private static final ExecutorService DIRECT_EXECUTOR = MoreExecutors.newDirectExecutorService();
    private static final CustomAudience VALID_CUSTOM_AUDIENCE =
            CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1).build();
    public static Duration VALID_DELAY = Duration.ofMinutes(100);
    public static PartialCustomAudience VALID_PARTIAL_CA =
            new PartialCustomAudience.Builder("fake_ca").build();
    public static ImmutableList<PartialCustomAudience> VALID_PARTIAL_CA_LIST =
            ImmutableList.of(VALID_PARTIAL_CA);

    @Mock private CustomAudienceImpl mCustomAudienceImplMock;
    @Mock private FledgeAuthorizationFilter mFledgeAuthorizationFilterMock;
    @Mock private FledgeAllowListsFilter mFledgeAllowListsFilterMock;
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private FledgeConsentFilter mFledgeConsentFilterMock;
    @Mock private ICustomAudienceCallback mICustomAudienceCallbackMock;
    @Mock private FetchAndJoinCustomAudienceCallback mFetchAndJoinCustomAudienceCallbackMock;
    @Mock private CustomAudienceOverrideCallback mCustomAudienceOverrideCallbackMock;
    @Mock private ScheduleCustomAudienceUpdateCallback mScheduleCustomAudienceUpdateCallback;
    @Mock private AppImportanceFilter mAppImportanceFilterMock;
    @Mock private AppInstallDao mAppInstallDaoMock;
    @Mock private FrequencyCapDao mFrequencyCapDaoMock;
    @Mock DevContextFilter mDevContextFilterMock;
    private final AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);
    @Mock private FledgeApiThrottleFilter mFledgeApiThrottleFilterMock;

    private final Flags mFlagsWithAllCheckEnabled = new FlagsWithCheckEnabledSwitch(true, true);

    private CustomAudienceServiceImpl mService;
    private ArgumentCaptor<FledgeErrorResponse> mActualResponseCaptor;

    @Before
    public void setup() throws Exception {
        mockGetConsentNotificationDebugMode(false);
        mocker.mockGetFlags(mFlagsWithAllCheckEnabled);
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
                        mFakeDebugFlags,
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
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevIdentity());
        mActualResponseCaptor = ArgumentCaptor.forClass(FledgeErrorResponse.class);
    }

    @Test
    public void testJoinCustomAudience_succeeded_shouldReturnDisabledStatus() throws Exception {
        mService.joinCustomAudience(
                VALID_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                mICustomAudienceCallbackMock);

        verify(mICustomAudienceCallbackMock).onFailure(mActualResponseCaptor.capture());
        assertWithMessage("Check API disabled response code")
                .that(mActualResponseCaptor.getValue().getStatusCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__JOIN_CUSTOM_AUDIENCE,
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void testJoinCustomAudience_callbackFailed_shouldLogCel() throws Exception {
        doThrow(new RemoteException()).when(mICustomAudienceCallbackMock).onFailure(any());

        mService.joinCustomAudience(
                VALID_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                mICustomAudienceCallbackMock);

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testFetchAndJoinCustomAudience_succeeded_shouldReturnDisabledStatus()
            throws Exception {
        mService.fetchAndJoinCustomAudience(
                new FetchAndJoinCustomAudienceInput.Builder(
                                CustomAudienceFixture.getValidFetchUriByBuyer(
                                        CommonFixture.VALID_BUYER_1),
                                CustomAudienceFixture.VALID_OWNER)
                        .build(),
                mFetchAndJoinCustomAudienceCallbackMock);

        verify(mFetchAndJoinCustomAudienceCallbackMock).onFailure(mActualResponseCaptor.capture());
        assertWithMessage("Check API disabled response code")
                .that(mActualResponseCaptor.getValue().getStatusCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE,
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void testFetchAndJoinCustomAudience_callbackFailed_shouldLogCel() throws Exception {
        doThrow(new RemoteException())
                .when(mFetchAndJoinCustomAudienceCallbackMock)
                .onFailure(any());

        mService.fetchAndJoinCustomAudience(
                new FetchAndJoinCustomAudienceInput.Builder(
                                CustomAudienceFixture.getValidFetchUriByBuyer(
                                        CommonFixture.VALID_BUYER_1),
                                CustomAudienceFixture.VALID_OWNER)
                        .build(),
                mFetchAndJoinCustomAudienceCallbackMock);

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testLeaveCustomAudience_succeeded_shouldReturnDisabledStatus() throws Exception {
        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallbackMock);

        verify(mICustomAudienceCallbackMock).onFailure(mActualResponseCaptor.capture());
        assertWithMessage("Check API disabled response code")
                .that(mActualResponseCaptor.getValue().getStatusCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__LEAVE_CUSTOM_AUDIENCE,
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void testLeaveCustomAudience_callbackFailed_shouldLogCel() throws Exception {
        doThrow(new RemoteException()).when(mICustomAudienceCallbackMock).onFailure(any());

        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallbackMock);

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testScheduleCustomAudienceUpdate_succeeded_shouldReturnDisabledStatus()
            throws Exception {
        mService.scheduleCustomAudienceUpdate(
                new ScheduleCustomAudienceUpdateInput.Builder(
                                CustomAudienceFixture.getValidFetchUriByBuyer(
                                        CommonFixture.VALID_BUYER_1),
                                CustomAudienceFixture.VALID_OWNER,
                                VALID_DELAY,
                                VALID_PARTIAL_CA_LIST)
                        .build(),
                mScheduleCustomAudienceUpdateCallback);

        verify(mScheduleCustomAudienceUpdateCallback).onFailure(mActualResponseCaptor.capture());
        assertWithMessage("Check API disabled response code")
                .that(mActualResponseCaptor.getValue().getStatusCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE,
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void testScheduleCustomAudienceUpdate_callbackFailed_shouldLogCel() throws Exception {
        doThrow(new RemoteException()).when(mScheduleCustomAudienceUpdateCallback).onFailure(any());

        mService.scheduleCustomAudienceUpdate(
                new ScheduleCustomAudienceUpdateInput.Builder(
                                CustomAudienceFixture.getValidFetchUriByBuyer(
                                        CommonFixture.VALID_BUYER_1),
                                CustomAudienceFixture.VALID_OWNER,
                                VALID_DELAY,
                                VALID_PARTIAL_CA_LIST)
                        .build(),
                mScheduleCustomAudienceUpdateCallback);

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testOverrideCustomAudienceRemoteInfo_succeeded_shouldReturnDisabledStatus()
            throws Exception {
        mService.overrideCustomAudienceRemoteInfo(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                CustomAudienceFixture.VALID_NAME,
                "",
                JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3,
                AdSelectionSignals.EMPTY,
                mCustomAudienceOverrideCallbackMock);

        verify(mCustomAudienceOverrideCallbackMock).onFailure(mActualResponseCaptor.capture());
        assertWithMessage("Check API disabled response code")
                .that(mActualResponseCaptor.getValue().getStatusCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void testOverrideCustomAudienceRemoteInfo_callbackFailed_shouldLogCel()
            throws Exception {
        doThrow(new RemoteException()).when(mCustomAudienceOverrideCallbackMock).onFailure(any());

        mService.overrideCustomAudienceRemoteInfo(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                CustomAudienceFixture.VALID_NAME,
                "",
                JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3,
                AdSelectionSignals.EMPTY,
                mCustomAudienceOverrideCallbackMock);

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testRemoveCustomAudienceRemoteInfoOverride_succeeded_shouldReturnDisabledStatus()
            throws Exception {
        mService.removeCustomAudienceRemoteInfoOverride(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                CustomAudienceFixture.VALID_NAME,
                mCustomAudienceOverrideCallbackMock);

        verify(mCustomAudienceOverrideCallbackMock).onFailure(mActualResponseCaptor.capture());
        assertWithMessage("Check API disabled response code")
                .that(mActualResponseCaptor.getValue().getStatusCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void testRemoveCustomAudienceRemoteInfoOverride_callbackFailed_shouldLogCel()
            throws Exception {
        doThrow(new RemoteException()).when(mCustomAudienceOverrideCallbackMock).onFailure(any());

        mService.removeCustomAudienceRemoteInfoOverride(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                CustomAudienceFixture.VALID_NAME,
                mCustomAudienceOverrideCallbackMock);

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testResetAllCustomAudienceOverrides_succeeded_shouldReturnDisabledStatus()
            throws Exception {
        mService.resetAllCustomAudienceOverrides(mCustomAudienceOverrideCallbackMock);

        verify(mCustomAudienceOverrideCallbackMock).onFailure(mActualResponseCaptor.capture());
        assertWithMessage("Check API disabled response code")
                .that(mActualResponseCaptor.getValue().getStatusCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void testResetAllCustomAudienceOverrides_callbackFailed_shouldLogCel() throws Exception {
        doThrow(new RemoteException()).when(mCustomAudienceOverrideCallbackMock).onFailure(any());

        mService.resetAllCustomAudienceOverrides(mCustomAudienceOverrideCallbackMock);

        verifyLoggerMock(
                AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES,
                CustomAudienceFixture.VALID_OWNER,
                STATUS_ADSERVICES_DISABLED);
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
