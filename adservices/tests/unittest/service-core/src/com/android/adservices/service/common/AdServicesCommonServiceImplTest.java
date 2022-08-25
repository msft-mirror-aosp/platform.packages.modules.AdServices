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

import static com.android.adservices.data.common.AdservicesEntryPointConstant.ADSERVICES_ENTRY_POINT_STATUS_DISABLE;
import static com.android.adservices.data.common.AdservicesEntryPointConstant.ADSERVICES_ENTRY_POINT_STATUS_ENABLE;
import static com.android.adservices.data.common.AdservicesEntryPointConstant.KEY_ADSERVICES_ENTRY_POINT_STATUS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.adservices.common.IAdServicesCommonCallback;
import android.adservices.common.IsAdServicesEnabledResult;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.RemoteException;

import com.android.adservices.service.Flags;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AdServicesCommonServiceImplTest {
    private AdServicesCommonServiceImpl mCommonService;
    private CountDownLatch mGetCommonCallbackLatch;
    @Mock private Flags mFlags;
    @Mock private Context mContext;
    @Mock private SharedPreferences mSharedPreferences;
    @Mock private SharedPreferences.Editor mEditor;
    @Captor ArgumentCaptor<String> mStringArgumentCaptor;
    @Captor ArgumentCaptor<Integer> mIntegerArgumentCaptor;
    private static final int BINDER_CONNECTION_TIMEOUT_MS = 5_000;

    private MockitoSession mStaticMockSession = null;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mFlags.getAdservicesEnableStatus()).thenReturn(true);

        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(ConsentNotificationJobService.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void getAdserviceStatusTest() throws InterruptedException {
        when(mFlags.getAdservicesEnableStatus()).thenReturn(true);
        mCommonService = new AdServicesCommonServiceImpl(mContext, mFlags);
        // Calling get adservice status, init set the flag to true, expect to return true
        IsAdServicesEnabledResult[] capturedResponseParcel = getStatusResult();
        assertThat(
                        mGetCommonCallbackLatch.await(
                                BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isTrue();
        IsAdServicesEnabledResult getStatusResult1 = capturedResponseParcel[0];
        assertThat(getStatusResult1.getAdServicesEnabled()).isTrue();

        // Set the flag to false
        when(mFlags.getAdservicesEnableStatus()).thenReturn(false);

        // Calling again, expect to false
        capturedResponseParcel = getStatusResult();
        assertThat(
                        mGetCommonCallbackLatch.await(
                                BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isTrue();
        IsAdServicesEnabledResult getStatusResult2 = capturedResponseParcel[0];
        assertThat(getStatusResult2.getAdServicesEnabled()).isFalse();
    }

    private IsAdServicesEnabledResult[] getStatusResult() {
        final IsAdServicesEnabledResult[] capturedResponseParcel = new IsAdServicesEnabledResult[1];
        mGetCommonCallbackLatch = new CountDownLatch(1);
        mCommonService.isAdServicesEnabled(
                new IAdServicesCommonCallback() {
                    @Override
                    public void onResult(IsAdServicesEnabledResult responseParcel)
                            throws RemoteException {
                        capturedResponseParcel[0] = responseParcel;
                        mGetCommonCallbackLatch.countDown();
                    }

                    @Override
                    public void onFailure(int statusCode) {
                        Assert.fail();
                    }

                    @Override
                    public IBinder asBinder() {
                        return null;
                    }
                });
        return capturedResponseParcel;
    }

    @Test
    public void setAdservicesEntryPointStatusTest() throws InterruptedException {
        when(mContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mSharedPreferences);
        when(mSharedPreferences.edit()).thenReturn(mEditor);
        when(mEditor.putInt(anyString(), anyInt())).thenReturn(mEditor);
        Mockito.doNothing().when(mEditor).apply();
        doNothing()
                .when(
                        () ->
                                ConsentNotificationJobService.schedule(
                                        any(Context.class), any(Boolean.class)));
        when(mFlags.getAdservicesEnableStatus()).thenReturn(true);

        mCommonService = new AdServicesCommonServiceImpl(mContext, mFlags);
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () -> ConsentNotificationJobService.schedule(any(Context.class), eq(false)),
                times(1));
        Mockito.verify(mEditor)
                .putInt(mStringArgumentCaptor.capture(), mIntegerArgumentCaptor.capture());
        assertThat(mStringArgumentCaptor.getValue()).isEqualTo(KEY_ADSERVICES_ENTRY_POINT_STATUS);
        assertThat(mIntegerArgumentCaptor.getValue())
                .isEqualTo(ADSERVICES_ENTRY_POINT_STATUS_ENABLE);

        mCommonService.setAdServicesEnabled(false, true);
        Thread.sleep(1000);

        verify(
                () -> ConsentNotificationJobService.schedule(any(Context.class), eq(true)),
                times(0));
        Mockito.verify(mEditor, times(2))
                .putInt(mStringArgumentCaptor.capture(), mIntegerArgumentCaptor.capture());
        assertThat(mStringArgumentCaptor.getValue()).isEqualTo(KEY_ADSERVICES_ENTRY_POINT_STATUS);
        assertThat(mIntegerArgumentCaptor.getValue())
                .isEqualTo(ADSERVICES_ENTRY_POINT_STATUS_DISABLE);
    }
}
