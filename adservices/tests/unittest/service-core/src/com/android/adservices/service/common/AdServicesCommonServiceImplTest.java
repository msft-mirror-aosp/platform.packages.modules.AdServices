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

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.adservices.common.IAdServicesCommonCallback;
import android.adservices.common.IsAdServicesEnabledResult;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.TelephonyManager;

import com.android.adservices.service.Flags;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.ConsentManager;
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
    @Mock private PackageManager mPackageManager;
    @Mock private SharedPreferences mSharedPreferences;
    @Mock private SharedPreferences.Editor mEditor;
    @Mock private ConsentManager mConsentManager;
    @Mock private TelephonyManager mTelephonyManager;
    @Captor ArgumentCaptor<String> mStringArgumentCaptor;
    @Captor ArgumentCaptor<Integer> mIntegerArgumentCaptor;
    private static final int BINDER_CONNECTION_TIMEOUT_MS = 5_000;

    private MockitoSession mStaticMockSession = null;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mFlags.getAdServicesEnabled()).thenReturn(true);

        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(ConsentNotificationJobService.class)
                        .spyStatic(ConsentManager.class)
                        .spyStatic(BackgroundJobsManager.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();

        ExtendedMockito.doNothing()
                .when(() -> BackgroundJobsManager.scheduleAllBackgroundJobs(any(Context.class)));
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void getAdserviceStatusTest() throws InterruptedException {
        when(mFlags.getAdServicesEnabled()).thenReturn(true);
        when(mFlags.getGaUxFeatureEnabled()).thenReturn(false);
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
        when(mFlags.getAdServicesEnabled()).thenReturn(false);

        // Calling again, expect to false
        capturedResponseParcel = getStatusResult();
        assertThat(
                        mGetCommonCallbackLatch.await(
                                BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isTrue();
        IsAdServicesEnabledResult getStatusResult2 = capturedResponseParcel[0];
        assertThat(getStatusResult2.getAdServicesEnabled()).isFalse();
    }

    @Test
    public void isAdservicesEnabledReconsentTest() throws InterruptedException {
        when(mFlags.getAdServicesEnabled()).thenReturn(true);
        when(mFlags.getGaUxFeatureEnabled()).thenReturn(true);
        when(mContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mSharedPreferences);
        when(mSharedPreferences.contains(anyString())).thenReturn(true);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        Mockito.doNothing().when(mEditor).apply();
        doNothing()
                .when(
                        () ->
                                ConsentNotificationJobService.schedule(
                                        any(Context.class),
                                        any(Boolean.class),
                                        any(Boolean.class)));
        ExtendedMockito.when(mConsentManager.getConsent())
                .thenReturn(AdServicesApiConsent.getConsent(true));
        ExtendedMockito.doReturn(mConsentManager)
                .when(() -> ConsentManager.getInstance(any(Context.class)));
        ExtendedMockito.when(mConsentManager.wasGaUxNotificationDisplayed()).thenReturn(false);
        ExtendedMockito.when(mConsentManager.wasNotificationDisplayed()).thenReturn(true);
        doReturn("pl").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(mTelephonyManager).when(mContext).getSystemService(TelephonyManager.class);

        // Happy case
        mCommonService = new AdServicesCommonServiceImpl(mContext, mFlags);
        // Calling get adservice status, init set the flag to true, expect to return true
        IsAdServicesEnabledResult[] capturedResponseParcel = getStatusResult();
        assertThat(
                        mGetCommonCallbackLatch.await(
                                BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isTrue();
        IsAdServicesEnabledResult getStatusResult1 = capturedResponseParcel[0];
        assertThat(getStatusResult1.getAdServicesEnabled()).isTrue();
        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), anyBoolean(), anyBoolean()),
                times(1));

        // GA UX feature disable, should not execute scheduler
        when(mFlags.getGaUxFeatureEnabled()).thenReturn(false);
        capturedResponseParcel = getStatusResult();
        assertThat(
                        mGetCommonCallbackLatch.await(
                                BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isTrue();
        getStatusResult1 = capturedResponseParcel[0];
        assertThat(getStatusResult1.getAdServicesEnabled()).isTrue();
        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), anyBoolean(), anyBoolean()),
                times(1));

        // GA UX feature enable, set device to not EU, not execute scheduler
        when(mFlags.getGaUxFeatureEnabled()).thenReturn(true);
        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        capturedResponseParcel = getStatusResult();
        assertThat(
                        mGetCommonCallbackLatch.await(
                                BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isTrue();
        getStatusResult1 = capturedResponseParcel[0];
        assertThat(getStatusResult1.getAdServicesEnabled()).isTrue();
        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), anyBoolean(), anyBoolean()),
                times(1));

        // GA UX feature enabled, device set to EU, GA UX notification set to displayed
        doReturn("pl").when(mTelephonyManager).getSimCountryIso();
        ExtendedMockito.when(mConsentManager.wasGaUxNotificationDisplayed()).thenReturn(true);
        capturedResponseParcel = getStatusResult();
        assertThat(
                        mGetCommonCallbackLatch.await(
                                BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isTrue();
        getStatusResult1 = capturedResponseParcel[0];
        assertThat(getStatusResult1.getAdServicesEnabled()).isTrue();
        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), anyBoolean(), anyBoolean()),
                times(1));

        // GA UX notification set to not displayed, sharedpreference set to not contains
        ExtendedMockito.when(mConsentManager.wasGaUxNotificationDisplayed()).thenReturn(false);
        when(mSharedPreferences.contains(anyString())).thenReturn(false);
        capturedResponseParcel = getStatusResult();
        assertThat(
                        mGetCommonCallbackLatch.await(
                                BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isTrue();
        getStatusResult1 = capturedResponseParcel[0];
        assertThat(getStatusResult1.getAdServicesEnabled()).isTrue();
        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), anyBoolean(), anyBoolean()),
                times(1));

        // Sharedpreference set to contains, user consent set to revoke
        when(mSharedPreferences.contains(anyString())).thenReturn(true);
        ExtendedMockito.when(mConsentManager.getConsent())
                .thenReturn(AdServicesApiConsent.getConsent(false));
        capturedResponseParcel = getStatusResult();
        assertThat(
                        mGetCommonCallbackLatch.await(
                                BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isTrue();
        getStatusResult1 = capturedResponseParcel[0];
        assertThat(getStatusResult1.getAdServicesEnabled()).isTrue();
        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), anyBoolean(), anyBoolean()),
                times(1));
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
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mSharedPreferences.edit()).thenReturn(mEditor);
        when(mEditor.putInt(anyString(), anyInt())).thenReturn(mEditor);
        Mockito.doNothing().when(mEditor).apply();
        doNothing()
                .when(
                        () ->
                                ConsentNotificationJobService.schedule(
                                        any(Context.class),
                                        any(Boolean.class),
                                        any(Boolean.class)));
        when(mFlags.getAdServicesEnabled()).thenReturn(true);
        ExtendedMockito.when(mConsentManager.getConsent())
                .thenReturn(AdServicesApiConsent.getConsent(true));
        ExtendedMockito.doReturn(mConsentManager)
                .when(() -> ConsentManager.getInstance(any(Context.class)));

        mCommonService = new AdServicesCommonServiceImpl(mContext, mFlags);
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), any(Boolean.class)),
                times(1));
        ExtendedMockito.verify(
                () -> BackgroundJobsManager.scheduleAllBackgroundJobs(any(Context.class)));

        Mockito.verify(mEditor)
                .putInt(mStringArgumentCaptor.capture(), mIntegerArgumentCaptor.capture());
        assertThat(mStringArgumentCaptor.getValue()).isEqualTo(KEY_ADSERVICES_ENTRY_POINT_STATUS);
        assertThat(mIntegerArgumentCaptor.getValue())
                .isEqualTo(ADSERVICES_ENTRY_POINT_STATUS_ENABLE);

        mCommonService.setAdServicesEnabled(false, true);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(true), any(Boolean.class)),
                times(0));
        Mockito.verify(mEditor, times(2))
                .putInt(mStringArgumentCaptor.capture(), mIntegerArgumentCaptor.capture());
        assertThat(mStringArgumentCaptor.getValue()).isEqualTo(KEY_ADSERVICES_ENTRY_POINT_STATUS);
        assertThat(mIntegerArgumentCaptor.getValue())
                .isEqualTo(ADSERVICES_ENTRY_POINT_STATUS_DISABLE);
    }

    @Test
    public void setAdservicesEnabledConsentTest() throws InterruptedException {
        when(mContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mSharedPreferences);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mSharedPreferences.edit()).thenReturn(mEditor);
        when(mEditor.putInt(anyString(), anyInt())).thenReturn(mEditor);
        Mockito.doNothing().when(mEditor).apply();
        // Set device to ROW
        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(mTelephonyManager).when(mContext).getSystemService(TelephonyManager.class);

        ExtendedMockito.when(mConsentManager.wasGaUxNotificationDisplayed()).thenReturn(false);
        ExtendedMockito.when(mConsentManager.wasNotificationDisplayed()).thenReturn(true);
        doNothing()
                .when(
                        () ->
                                ConsentNotificationJobService.schedule(
                                        any(Context.class),
                                        any(Boolean.class),
                                        any(Boolean.class)));
        when(mFlags.getAdServicesEnabled()).thenReturn(true);
        when(mFlags.getGaUxFeatureEnabled()).thenReturn(true);
        ExtendedMockito.when(mConsentManager.getConsent())
                .thenReturn(AdServicesApiConsent.getConsent(true));
        ExtendedMockito.doReturn(mConsentManager)
                .when(() -> ConsentManager.getInstance(any(Context.class)));

        // Reconsent happy case
        mCommonService = new AdServicesCommonServiceImpl(mContext, mFlags);
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(true)),
                times(1));

        // GA UX feature disable
        when(mFlags.getGaUxFeatureEnabled()).thenReturn(false);
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(true)),
                times(1));

        // enable GA UX feature, but EU device
        when(mFlags.getGaUxFeatureEnabled()).thenReturn(true);
        doReturn("pl").when(mTelephonyManager).getSimCountryIso();
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(true)),
                times(1));

        // ROW device, GA UX notification displayed
        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        ExtendedMockito.when(mConsentManager.wasGaUxNotificationDisplayed()).thenReturn(true);
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(true)),
                times(1));

        // GA UX notification not displayed, notification not displayed, this also trigger
        // first consent case, but we verify here for reconsentStatus as true
        ExtendedMockito.when(mConsentManager.wasGaUxNotificationDisplayed()).thenReturn(false);
        ExtendedMockito.when(mConsentManager.wasNotificationDisplayed()).thenReturn(false);
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(true)),
                times(1));

        // Notification displayed, user consent is revoked
        ExtendedMockito.when(mConsentManager.wasNotificationDisplayed()).thenReturn(true);
        ExtendedMockito.when(mConsentManager.getConsent())
                .thenReturn(AdServicesApiConsent.getConsent(false));
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(true)),
                times(1));

        // First Consent happy case, should be 2nd time
        ExtendedMockito.when(mConsentManager.wasGaUxNotificationDisplayed()).thenReturn(false);
        ExtendedMockito.when(mConsentManager.wasNotificationDisplayed()).thenReturn(false);
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(false)),
                times(2));

        // GA UX notification was displayed
        ExtendedMockito.when(mConsentManager.wasGaUxNotificationDisplayed()).thenReturn(true);
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(false)),
                times(2));

        // Notification was displayed
        ExtendedMockito.when(mConsentManager.wasGaUxNotificationDisplayed()).thenReturn(false);
        ExtendedMockito.when(mConsentManager.wasNotificationDisplayed()).thenReturn(true);
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(false)),
                times(2));
    }
}
