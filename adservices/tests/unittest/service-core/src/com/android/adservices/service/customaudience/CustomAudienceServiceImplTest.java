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
import static com.android.adservices.stats.FledgeApiCallStatsMatcher.aCallStatForFledgeApiWithStatus;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyNoMoreInteractions;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.ICustomAudienceCallback;
import android.content.Context;
import android.os.Binder;
import android.os.Process;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
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
                        mAdServicesLoggerSpy);
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
        doThrow(RuntimeException.class)
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
        doThrow(RuntimeException.class)
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
}
