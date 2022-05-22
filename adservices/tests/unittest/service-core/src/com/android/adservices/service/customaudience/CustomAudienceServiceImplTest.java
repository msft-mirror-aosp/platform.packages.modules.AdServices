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

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.ICustomAudienceCallback;
import android.content.Context;
import android.os.RemoteException;

import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.concurrent.ExecutorService;

public class CustomAudienceServiceImplTest {

    private static final ExecutorService DIRECT_EXECUTOR = MoreExecutors.newDirectExecutorService();

    private static final CustomAudience VALID_CUSTOM_AUDIENCE =
            CustomAudienceFixture.getValidBuilder().build();

    @Mock private Context mContext;
    @Mock private CustomAudienceImpl mCustomAudienceImpl;
    @Mock private ICustomAudienceCallback mICustomAudienceCallback;

    private CustomAudienceServiceImpl mService;

    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock DevContextFilter mDevContextFilter;

    @Before
    public void setup() {
        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        mService =
                new CustomAudienceServiceImpl(
                        mContext, mCustomAudienceImpl, mDevContextFilter, DIRECT_EXECUTOR);
    }

    @Test
    public void testJoinCustomAudience_runNormally() throws RemoteException {
        mService.joinCustomAudience(VALID_CUSTOM_AUDIENCE, mICustomAudienceCallback);
        verify(mCustomAudienceImpl).joinCustomAudience(VALID_CUSTOM_AUDIENCE);
        verify(mICustomAudienceCallback).onSuccess();
        verifyNoMoreInteractions(mCustomAudienceImpl, mICustomAudienceCallback, mContext);
    }

    @Test
    public void testJoinCustomAudience_nullInput() {
        assertThrows(
                NullPointerException.class,
                () -> mService.joinCustomAudience(null, mICustomAudienceCallback));
        verifyNoMoreInteractions(mCustomAudienceImpl, mICustomAudienceCallback, mContext);
    }

    @Test
    public void testJoinCustomAudience_nullCallback() {
        assertThrows(
                NullPointerException.class,
                () -> mService.joinCustomAudience(VALID_CUSTOM_AUDIENCE, null));
        verifyNoMoreInteractions(mCustomAudienceImpl, mICustomAudienceCallback, mContext);
    }

    @Test
    public void testJoinCustomAudience_errorCreateCustomAudience() throws RemoteException {
        doThrow(RuntimeException.class)
                .when(mCustomAudienceImpl)
                .joinCustomAudience(VALID_CUSTOM_AUDIENCE);

        mService.joinCustomAudience(VALID_CUSTOM_AUDIENCE, mICustomAudienceCallback);

        verify(mCustomAudienceImpl).joinCustomAudience(VALID_CUSTOM_AUDIENCE);
        verify(mICustomAudienceCallback).onFailure(any(FledgeErrorResponse.class));
        verifyNoMoreInteractions(mCustomAudienceImpl, mICustomAudienceCallback, mContext);
    }

    @Test
    public void testJoinCustomAudience_errorReturnCallback() throws RemoteException {
        doThrow(RemoteException.class).when(mICustomAudienceCallback).onSuccess();

        mService.joinCustomAudience(VALID_CUSTOM_AUDIENCE, mICustomAudienceCallback);

        verify(mCustomAudienceImpl).joinCustomAudience(VALID_CUSTOM_AUDIENCE);
        verify(mICustomAudienceCallback).onSuccess();
        verify(mICustomAudienceCallback).onFailure(any(FledgeErrorResponse.class));
        verifyNoMoreInteractions(mCustomAudienceImpl, mICustomAudienceCallback, mContext);
    }

    @Test
    public void testLeaveCustomAudience_runNormally() throws RemoteException {
        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CustomAudienceFixture.VALID_BUYER,
                CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallback);

        verify(mCustomAudienceImpl)
                .leaveCustomAudience(
                        CustomAudienceFixture.VALID_OWNER,
                        CustomAudienceFixture.VALID_BUYER,
                        CustomAudienceFixture.VALID_NAME);
        verify(mICustomAudienceCallback).onSuccess();
        verifyNoMoreInteractions(mCustomAudienceImpl, mICustomAudienceCallback, mContext);
    }

    @Test
    public void testLeaveCustomAudience_nullOwner() throws RemoteException {
        mService.leaveCustomAudience(
                null,
                CustomAudienceFixture.VALID_BUYER,
                CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallback);

        verify(mCustomAudienceImpl)
                .leaveCustomAudience(
                        null, CustomAudienceFixture.VALID_BUYER, CustomAudienceFixture.VALID_NAME);
        verify(mICustomAudienceCallback).onSuccess();
        verifyNoMoreInteractions(mCustomAudienceImpl, mICustomAudienceCallback, mContext);
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
        verifyNoMoreInteractions(mCustomAudienceImpl, mICustomAudienceCallback, mContext);
    }

    @Test
    public void testLeaveCustomAudience_nullName() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mService.leaveCustomAudience(
                                CustomAudienceFixture.VALID_OWNER,
                                CustomAudienceFixture.VALID_BUYER,
                                null,
                                mICustomAudienceCallback));
        verifyNoMoreInteractions(mCustomAudienceImpl, mICustomAudienceCallback, mContext);
    }

    @Test
    public void testLeaveCustomAudience_nullCallback() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mService.leaveCustomAudience(
                                CustomAudienceFixture.VALID_OWNER,
                                CustomAudienceFixture.VALID_BUYER,
                                CustomAudienceFixture.VALID_NAME,
                                null));
        verifyNoMoreInteractions(mCustomAudienceImpl, mICustomAudienceCallback, mContext);
    }

    @Test
    public void testLeaveCustomAudience_errorCallCustomAudienceImpl() throws RemoteException {
        doThrow(RuntimeException.class)
                .when(mCustomAudienceImpl)
                .leaveCustomAudience(
                        CustomAudienceFixture.VALID_OWNER,
                        CustomAudienceFixture.VALID_BUYER,
                        CustomAudienceFixture.VALID_NAME);

        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CustomAudienceFixture.VALID_BUYER,
                CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallback);

        verify(mCustomAudienceImpl)
                .leaveCustomAudience(
                        CustomAudienceFixture.VALID_OWNER,
                        CustomAudienceFixture.VALID_BUYER,
                        CustomAudienceFixture.VALID_NAME);
        verify(mICustomAudienceCallback).onSuccess();
        verifyNoMoreInteractions(mCustomAudienceImpl, mICustomAudienceCallback, mContext);
    }

    @Test
    public void testLeaveCustomAudience_errorReturnCallback() throws RemoteException {
        doThrow(RemoteException.class).when(mICustomAudienceCallback).onSuccess();

        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CustomAudienceFixture.VALID_BUYER,
                CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallback);

        verify(mCustomAudienceImpl)
                .leaveCustomAudience(
                        CustomAudienceFixture.VALID_OWNER,
                        CustomAudienceFixture.VALID_BUYER,
                        CustomAudienceFixture.VALID_NAME);
        verify(mICustomAudienceCallback).onSuccess();
        verifyNoMoreInteractions(mCustomAudienceImpl, mICustomAudienceCallback, mContext);
    }
}
