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

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES;
import static com.android.adservices.stats.FledgeApiCallStatsMatcher.aCallStatForFledgeApiWithStatus;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyNoMoreInteractions;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyZeroInteractions;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.CustomAudienceOverrideCallback;
import android.adservices.customaudience.ICustomAudienceCallback;
import android.content.Context;
import android.os.Binder;
import android.os.Process;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.AppImportanceFilter.WrongCallingApplicationStateException;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.Spy;

import java.util.concurrent.ExecutorService;

public class CustomAudienceServiceImplTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final ExecutorService DIRECT_EXECUTOR = MoreExecutors.newDirectExecutorService();

    private static final CustomAudience VALID_CUSTOM_AUDIENCE =
            CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER).build();

    @Mock private CustomAudienceImpl mCustomAudienceImpl;
    @Mock private FledgeAuthorizationFilter mFledgeAuthorizationFilter;
    @Mock private ICustomAudienceCallback mICustomAudienceCallback;
    @Mock private CustomAudienceOverrideCallback mCustomAudienceOverrideCallback;
    @Mock private AppImportanceFilter mAppImportanceFilter;
    @Mock private CustomAudienceDao mCustomAudienceDao;
    private Flags mFlagsWithForegroundCheckEnabled = new FlagsWithForegroundCheckOverride(true);
    private Flags mFlagsWithForegroundCheckDisabled = new FlagsWithForegroundCheckOverride(false);

    private CustomAudienceServiceImpl mService;

    @Mock DevContextFilter mDevContextFilter;
    @Spy private final AdServicesLogger mAdServicesLoggerSpy = AdServicesLoggerImpl.getInstance();

    private MockitoSession mStaticMockitoSession;

    @Before
    public void setup() throws Exception {
        mStaticMockitoSession =
                ExtendedMockito.mockitoSession()
                        .initMocks(this)
                        .mockStatic(Binder.class)
                        .startMocking();

        mService =
                new CustomAudienceServiceImpl(
                        CONTEXT,
                        mCustomAudienceImpl,
                        mFledgeAuthorizationFilter,
                        mDevContextFilter,
                        DIRECT_EXECUTOR,
                        mAdServicesLoggerSpy,
                        mAppImportanceFilter,
                        mFlagsWithForegroundCheckEnabled);
    }

    @After
    public void teardown() {
        mStaticMockitoSession.finishMocking();
    }

    @Test
    public void testJoinCustomAudience_runNormally() throws RemoteException {
        doReturn(Process.myUid()).when(Binder::getCallingUidOrThrow);

        mService.joinCustomAudience(VALID_CUSTOM_AUDIENCE, mICustomAudienceCallback);
        verify(mCustomAudienceImpl).joinCustomAudience(VALID_CUSTOM_AUDIENCE);
        verify(mICustomAudienceCallback).onSuccess();
        verify(mFledgeAuthorizationFilter)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE);

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE, STATUS_SUCCESS);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                                STATUS_SUCCESS));

        verifyNoMoreInteractions(
                mCustomAudienceImpl,
                mFledgeAuthorizationFilter,
                mICustomAudienceCallback,
                mAdServicesLoggerSpy);
    }

    @Test
    public void testJoinCustomAudience_notInBinderThread() {
        when(Binder.getCallingUidOrThrow()).thenThrow(IllegalStateException.class);

        assertThrows(
                IllegalStateException.class,
                () -> mService.joinCustomAudience(VALID_CUSTOM_AUDIENCE, mICustomAudienceCallback));

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        STATUS_INTERNAL_ERROR);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                                STATUS_INTERNAL_ERROR));

        verifyNoMoreInteractions(
                mCustomAudienceImpl,
                mFledgeAuthorizationFilter,
                mICustomAudienceCallback,
                mAdServicesLoggerSpy);
    }

    @Test
    public void testJoinCustomAudience_ownerAssertFailed() {
        doReturn(Process.myUid()).when(Binder::getCallingUidOrThrow);
        doThrow(SecurityException.class)
                .when(mFledgeAuthorizationFilter)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE);

        assertThrows(
                SecurityException.class,
                () -> mService.joinCustomAudience(VALID_CUSTOM_AUDIENCE, mICustomAudienceCallback));
        verify(mFledgeAuthorizationFilter)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE);

        verifyNoMoreInteractions(
                mCustomAudienceImpl,
                mFledgeAuthorizationFilter,
                mICustomAudienceCallback,
                mAdServicesLoggerSpy);
    }

    @Test
    public void testJoinCustomAudience_nullInput() {
        assertThrows(
                NullPointerException.class,
                () -> mService.joinCustomAudience(null, mICustomAudienceCallback));

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        STATUS_INVALID_ARGUMENT);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                                STATUS_INVALID_ARGUMENT));

        verifyNoMoreInteractions(
                mCustomAudienceImpl,
                mFledgeAuthorizationFilter,
                mICustomAudienceCallback,
                mAdServicesLoggerSpy);
    }

    @Test
    public void testJoinCustomAudience_nullCallback() {
        assertThrows(
                NullPointerException.class,
                () -> mService.joinCustomAudience(VALID_CUSTOM_AUDIENCE, null));

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        STATUS_INVALID_ARGUMENT);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                                STATUS_INVALID_ARGUMENT));

        verifyNoMoreInteractions(
                mCustomAudienceImpl,
                mFledgeAuthorizationFilter,
                mICustomAudienceCallback,
                mAdServicesLoggerSpy);
    }

    @Test
    public void testJoinCustomAudience_errorCreateCustomAudience() throws RemoteException {
        when(Binder.getCallingUidOrThrow()).thenReturn(Process.myUid());
        doThrow(new RuntimeException("Simulating Error creating Custom Audience"))
                .when(mCustomAudienceImpl)
                .joinCustomAudience(VALID_CUSTOM_AUDIENCE);

        mService.joinCustomAudience(VALID_CUSTOM_AUDIENCE, mICustomAudienceCallback);

        verify(mCustomAudienceImpl).joinCustomAudience(VALID_CUSTOM_AUDIENCE);
        verify(mICustomAudienceCallback).onFailure(any(FledgeErrorResponse.class));
        verify(mFledgeAuthorizationFilter)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE);

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        STATUS_INTERNAL_ERROR);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                                STATUS_INTERNAL_ERROR));

        verifyNoMoreInteractions(
                mCustomAudienceImpl,
                mFledgeAuthorizationFilter,
                mICustomAudienceCallback,
                mAdServicesLoggerSpy);
    }

    @Test
    public void testJoinCustomAudience_errorReturnCallback() throws RemoteException {
        when(Binder.getCallingUidOrThrow()).thenReturn(Process.myUid());
        doThrow(RemoteException.class).when(mICustomAudienceCallback).onSuccess();

        mService.joinCustomAudience(VALID_CUSTOM_AUDIENCE, mICustomAudienceCallback);

        verify(mCustomAudienceImpl).joinCustomAudience(VALID_CUSTOM_AUDIENCE);
        verify(mICustomAudienceCallback).onSuccess();
        verify(mICustomAudienceCallback).onFailure(any(FledgeErrorResponse.class));
        verify(mFledgeAuthorizationFilter)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE);

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        STATUS_INTERNAL_ERROR);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                                STATUS_INTERNAL_ERROR));

        verifyNoMoreInteractions(
                mCustomAudienceImpl,
                mFledgeAuthorizationFilter,
                mICustomAudienceCallback,
                mAdServicesLoggerSpy);
    }

    @Test
    public void testLeaveCustomAudience_runNormally() throws RemoteException {
        when(Binder.getCallingUidOrThrow()).thenReturn(Process.myUid());

        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER,
                CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallback);

        verify(mCustomAudienceImpl)
                .leaveCustomAudience(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER,
                        CustomAudienceFixture.VALID_NAME);
        verify(mICustomAudienceCallback).onSuccess();
        verify(mFledgeAuthorizationFilter)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE);

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE, STATUS_SUCCESS);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                                STATUS_SUCCESS));

        verifyNoMoreInteractions(
                mCustomAudienceImpl,
                mFledgeAuthorizationFilter,
                mICustomAudienceCallback,
                mAdServicesLoggerSpy);
    }

    @Test
    public void testLeaveCustomAudience_notInBinderThread() {
        when(Binder.getCallingUidOrThrow()).thenThrow(IllegalStateException.class);

        assertThrows(
                IllegalStateException.class,
                () ->
                        mService.leaveCustomAudience(
                                CustomAudienceFixture.VALID_OWNER,
                                CommonFixture.VALID_BUYER,
                                CustomAudienceFixture.VALID_NAME,
                                mICustomAudienceCallback));

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        STATUS_INTERNAL_ERROR);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                                STATUS_INTERNAL_ERROR));

        verifyNoMoreInteractions(
                mCustomAudienceImpl,
                mFledgeAuthorizationFilter,
                mICustomAudienceCallback,
                mAdServicesLoggerSpy);
    }

    @Test
    public void testLeaveCustomAudience_ownerAssertFailed() {
        doReturn(Process.myUid()).when(Binder::getCallingUidOrThrow);
        doThrow(SecurityException.class)
                .when(mFledgeAuthorizationFilter)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE);

        assertThrows(
                SecurityException.class,
                () ->
                        mService.leaveCustomAudience(
                                CustomAudienceFixture.VALID_OWNER,
                                CommonFixture.VALID_BUYER,
                                CustomAudienceFixture.VALID_NAME,
                                mICustomAudienceCallback));

        verify(mFledgeAuthorizationFilter)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE);

        verifyNoMoreInteractions(
                mCustomAudienceImpl,
                mFledgeAuthorizationFilter,
                mICustomAudienceCallback,
                mAdServicesLoggerSpy);
    }

    @Test
    public void testLeaveCustomAudience_nullOwner() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mService.leaveCustomAudience(
                                null,
                                CommonFixture.VALID_BUYER,
                                CustomAudienceFixture.VALID_NAME,
                                mICustomAudienceCallback));

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        STATUS_INVALID_ARGUMENT);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                                STATUS_INVALID_ARGUMENT));

        verifyNoMoreInteractions(
                mCustomAudienceImpl,
                mFledgeAuthorizationFilter,
                mICustomAudienceCallback,
                mAdServicesLoggerSpy);
    }

    @Test
    public void testLeaveCustomAudience_nullBuyer() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mService.leaveCustomAudience(
                                CustomAudienceFixture.VALID_OWNER,
                                null,
                                CustomAudienceFixture.VALID_NAME,
                                mICustomAudienceCallback));

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        STATUS_INVALID_ARGUMENT);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                                STATUS_INVALID_ARGUMENT));

        verifyNoMoreInteractions(
                mCustomAudienceImpl,
                mFledgeAuthorizationFilter,
                mICustomAudienceCallback,
                mAdServicesLoggerSpy);
    }

    @Test
    public void testLeaveCustomAudience_nullName() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mService.leaveCustomAudience(
                                CustomAudienceFixture.VALID_OWNER,
                                CommonFixture.VALID_BUYER,
                                null,
                                mICustomAudienceCallback));

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        STATUS_INVALID_ARGUMENT);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                                STATUS_INVALID_ARGUMENT));

        verifyNoMoreInteractions(
                mCustomAudienceImpl,
                mFledgeAuthorizationFilter,
                mICustomAudienceCallback,
                mAdServicesLoggerSpy);
    }

    @Test
    public void testLeaveCustomAudience_nullCallback() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mService.leaveCustomAudience(
                                CustomAudienceFixture.VALID_OWNER,
                                CommonFixture.VALID_BUYER,
                                CustomAudienceFixture.VALID_NAME,
                                null));

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        STATUS_INVALID_ARGUMENT);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                                STATUS_INVALID_ARGUMENT));

        verifyNoMoreInteractions(
                mCustomAudienceImpl,
                mFledgeAuthorizationFilter,
                mICustomAudienceCallback,
                mAdServicesLoggerSpy);
    }

    @Test
    public void testLeaveCustomAudience_errorCallCustomAudienceImpl() throws RemoteException {
        when(Binder.getCallingUidOrThrow()).thenReturn(Process.myUid());
        doThrow(new RuntimeException("Simulating Error calling CustomAudienceImpl"))
                .when(mCustomAudienceImpl)
                .leaveCustomAudience(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER,
                        CustomAudienceFixture.VALID_NAME);

        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER,
                CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallback);

        verify(mCustomAudienceImpl)
                .leaveCustomAudience(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER,
                        CustomAudienceFixture.VALID_NAME);
        verify(mICustomAudienceCallback).onSuccess();
        verify(mFledgeAuthorizationFilter)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE);

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        STATUS_INTERNAL_ERROR);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                                STATUS_INTERNAL_ERROR));

        verifyNoMoreInteractions(
                mCustomAudienceImpl,
                mFledgeAuthorizationFilter,
                mICustomAudienceCallback,
                mAdServicesLoggerSpy);
    }

    @Test
    public void testLeaveCustomAudience_errorReturnCallback() throws RemoteException {
        when(Binder.getCallingUidOrThrow()).thenReturn(Process.myUid());
        doThrow(RemoteException.class).when(mICustomAudienceCallback).onSuccess();

        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER,
                CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallback);

        verify(mCustomAudienceImpl)
                .leaveCustomAudience(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER,
                        CustomAudienceFixture.VALID_NAME);
        verify(mICustomAudienceCallback).onSuccess();
        verify(mFledgeAuthorizationFilter)
                .assertCallingPackageName(
                        CustomAudienceFixture.VALID_OWNER,
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE);

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        STATUS_UNKNOWN_ERROR);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                                STATUS_UNKNOWN_ERROR));

        verifyNoMoreInteractions(
                mCustomAudienceImpl,
                mFledgeAuthorizationFilter,
                mICustomAudienceCallback,
                mAdServicesLoggerSpy);
    }

    @Test
    public void testAppImportanceTestFails_joinCustomAudienceThrowsException()
            throws RemoteException {
        when(Binder.getCallingUidOrThrow()).thenReturn(Process.myUid());
        doThrow(new WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        null);

        mService.joinCustomAudience(VALID_CUSTOM_AUDIENCE, mICustomAudienceCallback);

        ArgumentCaptor<FledgeErrorResponse> errorCaptor =
                ArgumentCaptor.forClass(FledgeErrorResponse.class);
        verify(mICustomAudienceCallback).onFailure(errorCaptor.capture());
        assertThat(errorCaptor.getValue().getStatusCode())
                .isEqualTo(AdServicesStatusUtils.STATUS_BACKGROUND_CALLER);
        assertThat(errorCaptor.getValue().getErrorMessage())
                .isEqualTo(AdServicesStatusUtils.ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE);
    }

    @Test
    public void testAppImportanceDisabledCallerInBackground_joinCustomAudienceSucceeds()
            throws RemoteException {
        mService =
                new CustomAudienceServiceImpl(
                        CONTEXT,
                        mCustomAudienceImpl,
                        mFledgeAuthorizationFilter,
                        mDevContextFilter,
                        DIRECT_EXECUTOR,
                        mAdServicesLoggerSpy,
                        mAppImportanceFilter,
                        mFlagsWithForegroundCheckDisabled);

        mService.joinCustomAudience(VALID_CUSTOM_AUDIENCE, mICustomAudienceCallback);

        verify(mICustomAudienceCallback).onSuccess();
        verifyZeroInteractions(mAppImportanceFilter);
    }

    @Test
    public void testAppImportanceTestFails_leaveCustomAudienceThrowsException()
            throws RemoteException {
        when(Binder.getCallingUidOrThrow()).thenReturn(Process.myUid());
        doThrow(new WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        null);

        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER,
                CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallback);

        ArgumentCaptor<FledgeErrorResponse> errorCaptor =
                ArgumentCaptor.forClass(FledgeErrorResponse.class);
        verify(mICustomAudienceCallback).onFailure(errorCaptor.capture());
        assertThat(errorCaptor.getValue().getStatusCode())
                .isEqualTo(AdServicesStatusUtils.STATUS_BACKGROUND_CALLER);
        assertThat(errorCaptor.getValue().getErrorMessage())
                .isEqualTo(AdServicesStatusUtils.ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE);
    }

    @Test
    public void testAppImportanceDisabledCallerInBackground_leaveCustomAudienceSucceeds()
            throws RemoteException {
        mService =
                new CustomAudienceServiceImpl(
                        CONTEXT,
                        mCustomAudienceImpl,
                        mFledgeAuthorizationFilter,
                        mDevContextFilter,
                        DIRECT_EXECUTOR,
                        mAdServicesLoggerSpy,
                        mAppImportanceFilter,
                        mFlagsWithForegroundCheckDisabled);

        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER,
                CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallback);

        verify(mICustomAudienceCallback).onSuccess();
        verifyZeroInteractions(mAppImportanceFilter);
    }

    @Test
    public void testAppImportanceTestFails_overrideCustomAudienceThrowsException()
            throws RemoteException {
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setCallingAppPackageName("")
                                .setDevOptionsEnabled(true)
                                .build());
        when(mCustomAudienceImpl.getCustomAudienceDao()).thenReturn(mCustomAudienceDao);
        doThrow(new WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(
                        CustomAudienceFixture.VALID_OWNER,
                        AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO,
                        null);

        mService.overrideCustomAudienceRemoteInfo(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER,
                CustomAudienceFixture.VALID_NAME,
                "",
                AdSelectionSignals.EMPTY,
                mCustomAudienceOverrideCallback);

        ArgumentCaptor<FledgeErrorResponse> errorCaptor =
                ArgumentCaptor.forClass(FledgeErrorResponse.class);
        verify(mCustomAudienceOverrideCallback).onFailure(errorCaptor.capture());
        assertThat(errorCaptor.getValue().getStatusCode())
                .isEqualTo(AdServicesStatusUtils.STATUS_BACKGROUND_CALLER);
        assertThat(errorCaptor.getValue().getErrorMessage())
                .isEqualTo(AdServicesStatusUtils.ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE);
    }

    @Test
    public void testAppImportanceDisabledCallerInBackground_overrideCustomAudienceSucceeds()
            throws RemoteException {
        when(mCustomAudienceImpl.getCustomAudienceDao()).thenReturn(mCustomAudienceDao);
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setCallingAppPackageName("")
                                .setDevOptionsEnabled(true)
                                .build());
        mService =
                new CustomAudienceServiceImpl(
                        CONTEXT,
                        mCustomAudienceImpl,
                        mFledgeAuthorizationFilter,
                        mDevContextFilter,
                        DIRECT_EXECUTOR,
                        mAdServicesLoggerSpy,
                        mAppImportanceFilter,
                        mFlagsWithForegroundCheckDisabled);

        mService.overrideCustomAudienceRemoteInfo(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER,
                CustomAudienceFixture.VALID_NAME,
                "",
                AdSelectionSignals.EMPTY,
                mCustomAudienceOverrideCallback);

        verify(mCustomAudienceOverrideCallback).onSuccess();
        verifyZeroInteractions(mAppImportanceFilter);
    }

    @Test
    public void testAppImportanceTestFails_removeCustomAudienceOverrideThrowsException()
            throws RemoteException {
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setCallingAppPackageName("")
                                .setDevOptionsEnabled(true)
                                .build());
        when(mCustomAudienceImpl.getCustomAudienceDao()).thenReturn(mCustomAudienceDao);
        int apiName = AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE;
        doThrow(new WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(CustomAudienceFixture.VALID_OWNER, apiName, null);

        mService.removeCustomAudienceRemoteInfoOverride(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER,
                CustomAudienceFixture.VALID_NAME,
                mCustomAudienceOverrideCallback);

        ArgumentCaptor<FledgeErrorResponse> errorCaptor =
                ArgumentCaptor.forClass(FledgeErrorResponse.class);
        verify(mCustomAudienceOverrideCallback).onFailure(errorCaptor.capture());
        assertThat(errorCaptor.getValue().getStatusCode())
                .isEqualTo(AdServicesStatusUtils.STATUS_BACKGROUND_CALLER);
        assertThat(errorCaptor.getValue().getErrorMessage())
                .isEqualTo(AdServicesStatusUtils.ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE);
    }

    @Test
    public void testAppImportanceDisabledCallerInBackground_removeCustomAudienceOverrideSucceeds()
            throws RemoteException {
        when(mCustomAudienceImpl.getCustomAudienceDao()).thenReturn(mCustomAudienceDao);
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setCallingAppPackageName("")
                                .setDevOptionsEnabled(true)
                                .build());
        mService =
                new CustomAudienceServiceImpl(
                        CONTEXT,
                        mCustomAudienceImpl,
                        mFledgeAuthorizationFilter,
                        mDevContextFilter,
                        DIRECT_EXECUTOR,
                        mAdServicesLoggerSpy,
                        mAppImportanceFilter,
                        mFlagsWithForegroundCheckDisabled);

        mService.removeCustomAudienceRemoteInfoOverride(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER,
                CustomAudienceFixture.VALID_NAME,
                mCustomAudienceOverrideCallback);

        verify(mCustomAudienceOverrideCallback).onSuccess();
        verifyZeroInteractions(mAppImportanceFilter);
    }

    @Test
    public void testAppImportanceTestFails_resetOverridesThrowsException() throws RemoteException {
        when(mCustomAudienceImpl.getCustomAudienceDao()).thenReturn(mCustomAudienceDao);
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setCallingAppPackageName("")
                                .setDevOptionsEnabled(true)
                                .build());
        when(Binder.getCallingUidOrThrow()).thenReturn(Process.myUid());
        int apiName = AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES;
        doThrow(new WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(Process.myUid(), apiName, null);

        mService.resetAllCustomAudienceOverrides(mCustomAudienceOverrideCallback);

        ArgumentCaptor<FledgeErrorResponse> errorCaptor =
                ArgumentCaptor.forClass(FledgeErrorResponse.class);
        verify(mCustomAudienceOverrideCallback).onFailure(errorCaptor.capture());
        assertThat(errorCaptor.getValue().getStatusCode())
                .isEqualTo(AdServicesStatusUtils.STATUS_BACKGROUND_CALLER);
        assertThat(errorCaptor.getValue().getErrorMessage())
                .isEqualTo(AdServicesStatusUtils.ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE);
    }

    @Test
    public void testAppImportanceDisabledCallerInBackground_resetOverridesSucceeds()
            throws RemoteException {
        when(mCustomAudienceImpl.getCustomAudienceDao()).thenReturn(mCustomAudienceDao);
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setCallingAppPackageName("")
                                .setDevOptionsEnabled(true)
                                .build());
        mService =
                new CustomAudienceServiceImpl(
                        CONTEXT,
                        mCustomAudienceImpl,
                        mFledgeAuthorizationFilter,
                        mDevContextFilter,
                        DIRECT_EXECUTOR,
                        mAdServicesLoggerSpy,
                        mAppImportanceFilter,
                        mFlagsWithForegroundCheckDisabled);

        mService.overrideCustomAudienceRemoteInfo(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER,
                CustomAudienceFixture.VALID_NAME,
                "",
                AdSelectionSignals.EMPTY,
                mCustomAudienceOverrideCallback);

        verify(mCustomAudienceOverrideCallback).onSuccess();
        verifyZeroInteractions(mAppImportanceFilter);
    }

    private static class FlagsWithForegroundCheckOverride implements Flags {
        private final boolean mEnabled;

        FlagsWithForegroundCheckOverride(boolean enabled) {
            mEnabled = enabled;
        }

        @Override
        public boolean getEnforceForegroundStatusForFledgeCustomAudience() {
            return mEnabled;
        }

        @Override
        public boolean getEnforceForegroundStatusForFledgeOverrides() {
            return mEnabled;
        }
    }
}
