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

import static android.adservices.common.AdServicesStatusUtils.STATUS_ADSERVICES_ACTIVITY_DISABLED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED_PACKAGE_NOT_IN_ALLOWLIST;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;

import static com.android.adservices.data.common.AdservicesEntryPointConstant.ADSERVICES_ENTRY_POINT_STATUS_DISABLE;
import static com.android.adservices.data.common.AdservicesEntryPointConstant.ADSERVICES_ENTRY_POINT_STATUS_ENABLE;
import static com.android.adservices.data.common.AdservicesEntryPointConstant.KEY_ADSERVICES_ENTRY_POINT_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IAPC_UPDATE_AD_ID_API_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__AD_ID;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.GA_UX;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesCommonStatesResponse;
import android.adservices.common.AdServicesModuleUserChoice;
import android.adservices.common.AdServicesStates;
import android.adservices.common.CallerMetadata;
import android.adservices.common.CommonFixture;
import android.adservices.common.ConsentStatus;
import android.adservices.common.EnableAdServicesResponse;
import android.adservices.common.GetAdServicesCommonStatesParams;
import android.adservices.common.IAdServicesCommonCallback;
import android.adservices.common.IAdServicesCommonStatesCallback;
import android.adservices.common.IEnableAdServicesCallback;
import android.adservices.common.IRequestAdServicesModuleOverridesCallback;
import android.adservices.common.IRequestAdServicesModuleUserChoicesCallback;
import android.adservices.common.IUpdateAdIdCallback;
import android.adservices.common.IsAdServicesEnabledResult;
import android.adservices.common.Module;
import android.adservices.common.NotificationType;
import android.adservices.common.UpdateAdIdRequest;
import android.adservices.common.UpdateAdServicesModuleStatesParams;
import android.adservices.common.UpdateAdServicesUserChoicesParams;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.telephony.TelephonyManager;
import android.util.SparseIntArray;

import androidx.test.filters.FlakyTest;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adid.AdIdWorker;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.service.ui.UxEngine;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.shared.testing.IntFailureSyncCallback;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.concurrency.ResultSyncCallback;
import com.android.adservices.shared.util.Clock;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.List;

@SpyStatic(AdServicesBackCompatInit.class)
@SpyStatic(ConsentNotificationJobService.class)
@SpyStatic(ConsentManager.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(DebugFlags.class)
@SpyStatic(BackgroundJobsManager.class)
@SpyStatic(PermissionHelper.class)
@SpyStatic(UxStatesManager.class)
@SpyStatic(PackageManagerCompatUtils.class)
@SpyStatic(SdkLevel.class)
public final class AdServicesCommonServiceImplTest extends AdServicesExtendedMockitoTestCase {
    private static final String UNUSED_AD_ID = "unused_ad_id";

    private AdServicesCommonServiceImpl mCommonService;
    @Mock private PackageManager mPackageManager;
    @Mock private UxEngine mUxEngine;
    @Mock private UxStatesManager mUxStatesManager;
    @Mock private SharedPreferences mSharedPreferences;
    @Mock private SharedPreferences.Editor mEditor;
    @Mock private ConsentManager mConsentManager;
    @Mock private TelephonyManager mTelephonyManager;
    @Mock private AdIdWorker mMockAdIdWorker;
    @Mock private Clock mClock;
    @Captor private ArgumentCaptor<String> mStringArgumentCaptor;
    @Captor private ArgumentCaptor<Integer> mIntegerArgumentCaptor;

    @Captor private ArgumentCaptor<SparseIntArray> mAdservicesModuleStatesArgumentCaptor;

    @Captor
    private ArgumentCaptor<List<AdServicesModuleUserChoice>>
            mAdservicesModuleUserChoiceArgumentCaptor;

    private static final int BINDER_CONNECTION_TIMEOUT_MS = 5_000;
    private static final String TEST_APP_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final String INVALID_PACKAGE_NAME = "com.do_not_exists";
    private static final String SOME_SDK_NAME = "SomeSdkName";
    private ResultSyncCallback<ApiCallStats> mLogApiCallStatsCallback;
    private static final String AD_SERVICES_APK_PKG_SUFFIX = "android.adservices.api";
    private static final String EXT_SERVICES_APK_PKG_SUFFIX = "android.ext.services";

    private final AdServicesLogger mAdServicesLogger =
            Mockito.spy(AdServicesLoggerImpl.getInstance());
    private AdServicesBackCompatInit mSpyBackCompatInit;

    @Before
    public void setup() {
        mCommonService =
                new AdServicesCommonServiceImpl(
                        mMockContext,
                        mMockFlags,
                        mMockDebugFlags,
                        mUxEngine,
                        mUxStatesManager,
                        mMockAdIdWorker,
                        mAdServicesLogger,
                        mClock);
        mLogApiCallStatsCallback = mocker.mockLogApiCallStats(mAdServicesLogger);
        mocker.mockGetFlags(mMockFlags);
        mocker.mockGetDebugFlags(mMockDebugFlags);
        doReturn(true).when(mMockFlags).getAdServicesEnabled();

        ExtendedMockito.doNothing()
                .when(() -> BackgroundJobsManager.scheduleAllBackgroundJobs(any(Context.class)));

        ExtendedMockito.doReturn(mUxStatesManager).when(UxStatesManager::getInstance);

        doNothing()
                .when(
                        () ->
                                ConsentNotificationJobService.schedule(
                                        any(Context.class),
                                        any(Boolean.class),
                                        any(Boolean.class)));

        doReturn(mSharedPreferences).when(mMockContext).getSharedPreferences(anyString(), anyInt());
        doReturn(mPackageManager).when(mMockContext).getPackageManager();
        doReturn(mEditor).when(mSharedPreferences).edit();
        doReturn(mEditor).when(mEditor).putInt(anyString(), anyInt());
        doReturn(true).when(mEditor).commit();
        doReturn(true).when(mSharedPreferences).contains(anyString());

        ExtendedMockito.doReturn(mConsentManager).when(ConsentManager::getInstance);

        // Set device to EU
        doReturn(Flags.UI_EEA_COUNTRIES).when(mMockFlags).getUiEeaCountries();
        doReturn("pl").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mMockContext).getPackageManager();
        doReturn(mTelephonyManager).when(mMockContext).getSystemService(TelephonyManager.class);
        doReturn(true).when(mUxStatesManager).isEnrolledUser(mMockContext);
    }

    // For the old entry point logic, we only check the UX flag and user enrollment is irrelevant.
    @Test
    public void isAdServiceEnabledTest_userNotEnrolledEntryPointLogicV1() throws Exception {
        doReturn(false).when(mUxStatesManager).isEnrolledUser(mMockContext);
        doReturn(false).when(mMockFlags).getEnableAdServicesSystemApi();
        mCommonService =
                new AdServicesCommonServiceImpl(
                        mMockContext,
                        mMockFlags,
                        mMockDebugFlags,
                        mUxEngine,
                        mUxStatesManager,
                        mMockAdIdWorker,
                        mAdServicesLogger,
                        mClock);

        // Calling get adservice status, init set the flag to true, expect to return true
        IsAdServicesEnabledResult getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isTrue();
    }

    // For the new entry point logic, only enrolled user that has gone through UxEngine
    // can see the entry point.
    @Test
    public void isAdServiceEnabledTest_userNotEnrolledEntryPointLogicV2() throws Exception {
        doReturn(false).when(mUxStatesManager).isEnrolledUser(mMockContext);
        doReturn(true).when(mMockFlags).getEnableAdServicesSystemApi();
        doReturn(GA_UX).when(mConsentManager).getUx();

        mCommonService =
                new AdServicesCommonServiceImpl(
                        mMockContext,
                        mMockFlags,
                        mMockDebugFlags,
                        mUxEngine,
                        mUxStatesManager,
                        mMockAdIdWorker,
                        mAdServicesLogger,
                        mClock);
        // Calling get adservice status, init set the flag to true, expect to return true
        IsAdServicesEnabledResult getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isFalse();
    }

    @Test
    public void getAdserviceStatusTest() throws Exception {
        doReturn(false).when(mMockFlags).getGaUxFeatureEnabled();
        mCommonService =
                new AdServicesCommonServiceImpl(
                        mMockContext,
                        mMockFlags,
                        mMockDebugFlags,
                        mUxEngine,
                        mUxStatesManager,
                        mMockAdIdWorker,
                        mAdServicesLogger,
                        mClock);
        // Calling get adservice status, init set the flag to true, expect to return true
        IsAdServicesEnabledResult getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isTrue();

        // Set the flag to false
        doReturn(false).when(mMockFlags).getAdServicesEnabled();

        // Calling again, expect to false
        getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isFalse();
    }

    @Test
    public void getAdserviceStatusWithCheckActivityTest() throws Exception {
        doReturn(true).when(mMockFlags).isBackCompatActivityFeatureEnabled();

        doReturn(false).when(mMockFlags).getGaUxFeatureEnabled();
        mCommonService =
                new AdServicesCommonServiceImpl(
                        mMockContext,
                        mMockFlags,
                        mMockDebugFlags,
                        mUxEngine,
                        mUxStatesManager,
                        mMockAdIdWorker,
                        mAdServicesLogger,
                        mClock);
        ExtendedMockito.doReturn(true)
                .when(() -> PackageManagerCompatUtils.isAdServicesActivityEnabled(any()));

        // Calling get adservice status, set the activity to enabled, expect to return true
        IsAdServicesEnabledResult getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isTrue();

        // Set the activity to disabled
        ExtendedMockito.doReturn(false)
                .when(() -> PackageManagerCompatUtils.isAdServicesActivityEnabled(any()));

        // Calling again, expect to false
        getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isFalse();
    }

    @Test
    public void isAdservicesEnabledReconsentTest_happycase() throws Exception {
        // Happy case
        // Calling get adservice status, init set the flag to true, expect to return true
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();

        IsAdServicesEnabledResult getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isTrue();
        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), anyBoolean(), anyBoolean()),
                times(1));
    }

    @Test
    public void isAdservicesEnabledReconsentTest_gaUxFeatureDisabled() throws Exception {
        // GA UX feature disable, should not execute scheduler
        doReturn(false).when(mMockFlags).getGaUxFeatureEnabled();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();

        IsAdServicesEnabledResult getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isTrue();
        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), anyBoolean(), anyBoolean()),
                times(0));
    }

    @Test
    public void isAdservicesEnabledReconsentTest_deviceNotEu() throws Exception {
        // GA UX feature enable, set device to not EU, not execute scheduler
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();

        IsAdServicesEnabledResult getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isTrue();
        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), anyBoolean(), anyBoolean()),
                times(0));
    }

    @Test
    public void isAdservicesEnabledReconsentTest_gaUxNotificationDisplayed() throws Exception {
        // GA UX feature enabled, device set to EU, GA UX notification set to displayed
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        doReturn("pl").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();

        IsAdServicesEnabledResult getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isTrue();
        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), anyBoolean(), anyBoolean()),
                times(0));
    }

    @Test
    public void isAdservicesEnabledReconsentTest_sharedPreferenceNotContain() throws Exception {
        // GA UX notification set to not displayed, sharedpreference set to not contains
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(false).when(mSharedPreferences).contains(anyString());
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();

        IsAdServicesEnabledResult getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isTrue();
        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), anyBoolean(), anyBoolean()),
                times(0));
    }

    @Test
    public void isAdservicesEnabledReconsentTest_userConsentRevoked() throws Exception {
        // Sharedpreference set to contains, user consent set to revoke
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(AdServicesApiConsent.getConsent(false)).when(mConsentManager).getConsent();

        IsAdServicesEnabledResult getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isTrue();
        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), anyBoolean(), anyBoolean()),
                times(0));
    }

    @Test
    public void setAdservicesEntryPointStatusTest() throws Exception {
        // Not reconsent, as not ROW devices, Not first Consent, as notification displayed is true
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), any(Boolean.class)),
                times(0));
        ExtendedMockito.verify(
                () -> BackgroundJobsManager.scheduleAllBackgroundJobs(any(Context.class)));

        Mockito.verify(mEditor)
                .putInt(mStringArgumentCaptor.capture(), mIntegerArgumentCaptor.capture());
        assertThat(mStringArgumentCaptor.getValue()).isEqualTo(KEY_ADSERVICES_ENTRY_POINT_STATUS);
        assertThat(mIntegerArgumentCaptor.getValue())
                .isEqualTo(ADSERVICES_ENTRY_POINT_STATUS_ENABLE);

        // Not executed, as entry point enabled status is false
        doReturn(false).when(mConsentManager).wasNotificationDisplayed();
        mCommonService.setAdServicesEnabled(false, true);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), any(Boolean.class)),
                times(0));
        Mockito.verify(mEditor, times(2))
                .putInt(mStringArgumentCaptor.capture(), mIntegerArgumentCaptor.capture());
        assertThat(mStringArgumentCaptor.getValue()).isEqualTo(KEY_ADSERVICES_ENTRY_POINT_STATUS);
        assertThat(mIntegerArgumentCaptor.getValue())
                .isEqualTo(ADSERVICES_ENTRY_POINT_STATUS_DISABLE);
    }

    @Test
    public void setAdservicesEnabledConsentTest_happycase() throws Exception {
        // Set device to ROW
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(true)),
                times(1));
    }

    @Test
    public void setAdservicesEnabledConsentTest_ReconsentGaUxFeatureDisabled()
            throws InterruptedException {
        // GA UX feature disable
        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn(false).when(mMockFlags).getGaUxFeatureEnabled();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(true)),
                times(0));
    }

    @Test
    public void setAdservicesEnabledConsentTest_ReconsentEUDevice() throws Exception {
        // enable GA UX feature, but EU device
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(true)),
                times(0));
    }

    @Test
    public void setAdservicesEnabledConsentTest_ReconsentGaUxNotificationDisplayed()
            throws InterruptedException {
        // ROW device, GA UX notification displayed
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(true)),
                times(0));
    }

    @Test
    public void setAdservicesEnabledConsentTest_ReconsentNotificationNotDisplayed()
            throws InterruptedException {
        // GA UX notification not displayed, notification not displayed, this also trigger
        // first consent case, but we verify here for reconsentStatus as true
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(false).when(mConsentManager).wasNotificationDisplayed();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(true)),
                times(0));
    }

    @Test
    public void setAdservicesEnabledConsentTest_ReconsentUserConsentRevoked()
            throws InterruptedException {
        // Notification displayed, user consent is revoked
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn(AdServicesApiConsent.getConsent(false)).when(mConsentManager).getConsent();
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(true)),
                times(0));
    }

    @Test
    public void setAdservicesEnabledConsentTest_FirstConsentHappycase()
            throws InterruptedException {
        // First Consent happy case, should be executed
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(false).when(mConsentManager).wasNotificationDisplayed();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(false)),
                times(1));
    }

    @Test
    public void setAdservicesEnabledConsentTest_FirstConsentGaUxNotificationDisplayed()
            throws InterruptedException {
        // GA UX notification was displayed
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn(true).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(false)),
                times(0));
    }

    @Test
    public void setAdservicesEnabledConsentTest_FirstConsentNotificationDisplayed()
            throws InterruptedException {
        // Notification was displayed
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(false)),
                times(0));
    }

    @Test
    public void enableAdServicesTest_unauthorizedCaller() throws Exception {
        SyncIEnableAdServicesCallback callback =
                new SyncIEnableAdServicesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        ExtendedMockito.doReturn(false)
                .when(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));

        mCommonService.enableAdServices(new AdServicesStates.Builder().build(), callback);
        callback.assertFailed(STATUS_UNAUTHORIZED);

        ExtendedMockito.verify(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        verify(mMockFlags, never()).getEnableAdServicesSystemApi();
        verify(mUxEngine, never()).start(any());
    }

    @Test
    @FlakyTest(bugId = 299686058)
    public void enableAdServicesTest_apiDisabled() throws InterruptedException {
        SyncIEnableAdServicesCallback callback =
                new SyncIEnableAdServicesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        ExtendedMockito.doReturn(true)
                .when(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        doReturn(false).when(mMockFlags).getEnableAdServicesSystemApi();

        mCommonService.enableAdServices(new AdServicesStates.Builder().build(), callback);
        assertThat(callback.assertSuccess().isApiEnabled()).isFalse();

        ExtendedMockito.verify(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        verify(mMockFlags).getEnableAdServicesSystemApi();
        verify(mUxEngine, never()).start(any());
    }

    @Test
    public void enableAdServicesTest_engineStarted() throws InterruptedException {
        SyncIEnableAdServicesCallback callback =
                new SyncIEnableAdServicesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        ExtendedMockito.doReturn(true)
                .when(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        doReturn(true).when(mMockFlags).getEnableAdServicesSystemApi();

        mCommonService.enableAdServices(new AdServicesStates.Builder().build(), callback);
        EnableAdServicesResponse response = callback.assertSuccess();
        assertThat(response.isApiEnabled()).isTrue();
        assertThat(response.isSuccess()).isTrue();

        ExtendedMockito.verify(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        verify(mMockFlags).getEnableAdServicesSystemApi();
        verify(mUxEngine).start(any());
    }

    @Test
    public void enableAdServicesTest_extServicesPackage_initializesComponents() throws Exception {
        SyncIEnableAdServicesCallback callback =
                new SyncIEnableAdServicesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        // Components are not initialized on T+; rather they are disabled including background
        // jobs which this test does not provide appropriate mocks for leading to ErrorLogUtil
        // logging. Mock SDK level so that error logging does not need to be verified on T+,
        // enforced by the AdServicesLoggingUsageRule.
        mocker.mockIsAtLeastT(false);
        ExtendedMockito.doReturn(true)
                .when(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        doReturn(true).when(mMockFlags).getEnableAdServicesSystemApi();
        doReturn(true).when(mMockFlags).getEnableBackCompatInit();
        doReturn(true).when(mMockFlags).getEnableBackCompat();
        doReturn(true).when(mMockFlags).getAdServicesEnabled();
        doReturn(false).when(mMockFlags).getGlobalKillSwitch();

        doReturn(EXT_SERVICES_APK_PKG_SUFFIX).when(mMockContext).getPackageName();
        spyBackCompatInit();
        ExtendedMockito.doReturn(true)
                .when(() -> PackageManagerCompatUtils.isAdServicesActivityEnabled(any()));

        mCommonService.enableAdServices(new AdServicesStates.Builder().build(), callback);
        EnableAdServicesResponse response = callback.assertSuccess();
        assertThat(response.isApiEnabled()).isTrue();
        assertThat(response.isSuccess()).isTrue();

        ExtendedMockito.verify(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        verify(mMockFlags).getEnableBackCompatInit();
        verify(mSpyBackCompatInit).initializeComponents();
    }

    @Test
    @RequiresSdkLevelAtLeastT
    public void enableAdServicesTest_tPlus_adServicesPackage_skipBackCompatInit()
            throws InterruptedException {
        SyncIEnableAdServicesCallback callback =
                new SyncIEnableAdServicesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        ExtendedMockito.doReturn(true)
                .when(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        doReturn(true).when(mMockFlags).getEnableAdServicesSystemApi();
        doReturn(true).when(mMockFlags).getEnableBackCompatInit();
        doReturn(AD_SERVICES_APK_PKG_SUFFIX).when(mMockContext).getPackageName();
        spyBackCompatInit();
        ExtendedMockito.doReturn(true)
                .when(() -> PackageManagerCompatUtils.isAdServicesActivityEnabled(any()));

        mCommonService.enableAdServices(new AdServicesStates.Builder().build(), callback);
        EnableAdServicesResponse response = callback.assertSuccess();
        assertThat(response.isApiEnabled()).isTrue();
        assertThat(response.isSuccess()).isTrue();

        ExtendedMockito.verify(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        verify(mMockFlags).getEnableBackCompatInit();
        verify(mSpyBackCompatInit).initializeComponents();
        verify(mPackageManager, never())
                .setComponentEnabledSetting(
                        any(ComponentName.class),
                        eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED),
                        eq(PackageManager.DONT_KILL_APP));
    }

    @Test
    public void enableAdServicesTest_activitiesDisabled_skipUxEngine() throws InterruptedException {
        SyncIEnableAdServicesCallback callback =
                new SyncIEnableAdServicesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        ExtendedMockito.doReturn(true)
                .when(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        doReturn(true).when(mMockFlags).getEnableAdServicesSystemApi();
        doReturn(true).when(mMockFlags).getEnableBackCompatInit();
        spyBackCompatInit();
        ExtendedMockito.doReturn(false)
                .when(() -> PackageManagerCompatUtils.isAdServicesActivityEnabled(any()));

        mCommonService.enableAdServices(new AdServicesStates.Builder().build(), callback);
        callback.assertFailed(STATUS_ADSERVICES_ACTIVITY_DISABLED);

        ExtendedMockito.verify(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        verify(mMockFlags).getEnableBackCompatInit();
        verify(mSpyBackCompatInit).initializeComponents();
        verify(mUxEngine, never()).start(any());
    }

    @Test
    @RequiresSdkLevelAtLeastT
    public void enableAdServicesTest_activitiesEnabled_startUxEngine() throws InterruptedException {
        SyncIEnableAdServicesCallback callback =
                new SyncIEnableAdServicesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        ExtendedMockito.doReturn(true)
                .when(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        doReturn(true).when(mMockFlags).getEnableAdServicesSystemApi();
        doReturn(true).when(mMockFlags).getEnableBackCompatInit();
        spyBackCompatInit();
        ExtendedMockito.doReturn(true)
                .when(() -> PackageManagerCompatUtils.isAdServicesActivityEnabled(any()));

        mCommonService.enableAdServices(new AdServicesStates.Builder().build(), callback);
        EnableAdServicesResponse response = callback.assertSuccess();
        assertThat(response.isApiEnabled()).isTrue();
        assertThat(response.isSuccess()).isTrue();

        ExtendedMockito.verify(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        verify(mMockFlags).getEnableBackCompatInit();
        verify(mSpyBackCompatInit).initializeComponents();
        verify(mUxEngine).start(any());
    }

    @Test
    public void testUpdateAdIdChange() throws InterruptedException {
        ExtendedMockito.doReturn(true)
                .when(() -> PermissionHelper.hasUpdateAdIdCachePermission(any()));

        UpdateAdIdRequest request = new UpdateAdIdRequest.Builder(UNUSED_AD_ID).build();
        doNothing().when(mMockAdIdWorker).updateAdId(request);

        SyncIUpdateAdIdCallback callback = callUpdateAdIdCache(request);
        callback.assertResultReceived();

        ExtendedMockito.verify(() -> PermissionHelper.hasUpdateAdIdCachePermission(any()));
        verify(mMockAdIdWorker).updateAdId(request);
    }

    @Test
    public void testUpdateAdIdChange_unauthorizedCaller() throws InterruptedException {
        ExtendedMockito.doReturn(false)
                .when(() -> PermissionHelper.hasUpdateAdIdCachePermission(any()));

        UpdateAdIdRequest request = new UpdateAdIdRequest.Builder(UNUSED_AD_ID).build();
        doNothing().when(mMockAdIdWorker).updateAdId(request);

        SyncIUpdateAdIdCallback callback = callUpdateAdIdCache(request);
        callback.assertFailed(STATUS_UNAUTHORIZED);

        ExtendedMockito.verify(() -> PermissionHelper.hasUpdateAdIdCachePermission(any()));
        verify(mMockAdIdWorker, never()).updateAdId(request);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            throwable = RuntimeException.class,
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IAPC_UPDATE_AD_ID_API_ERROR,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__AD_ID)
    public void testUpdateAdIdChange_throwsException() throws InterruptedException {
        ExtendedMockito.doReturn(true)
                .when(() -> PermissionHelper.hasUpdateAdIdCachePermission(any()));

        RuntimeException exception = new RuntimeException("Update AdId Error.");
        UpdateAdIdRequest request = new UpdateAdIdRequest.Builder(UNUSED_AD_ID).build();
        doThrow(exception).when(mMockAdIdWorker).updateAdId(request);

        SyncIUpdateAdIdCallback callback = callUpdateAdIdCache(request);
        callback.assertFailureReceived();

        ExtendedMockito.verify(() -> PermissionHelper.hasUpdateAdIdCachePermission(any()));
        verify(mMockAdIdWorker).updateAdId(request);
    }

    @Test
    public void testGetAdservicesCommonStates_unauthorizedUser() throws Exception {
        SyncIAdServicesCommonStatesCallback callback =
                new SyncIAdServicesCommonStatesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        ExtendedMockito.doReturn(false)
                .when(
                        () ->
                                PermissionHelper.hasAccessAdServicesCommonStatePermission(
                                        any(), any()));
        when(mClock.elapsedRealtime()).thenReturn(150L, 200L);
        GetAdServicesCommonStatesParams params =
                new GetAdServicesCommonStatesParams.Builder(TEST_APP_PACKAGE_NAME, SOME_SDK_NAME)
                        .build();
        CallerMetadata metadata = new CallerMetadata.Builder().setBinderElapsedTimestamp(0).build();
        mCommonService.getAdServicesCommonStates(params, metadata, callback);
        callback.assertFailed(STATUS_UNAUTHORIZED);

        ExtendedMockito.verify(
                () -> PermissionHelper.hasAccessAdServicesCommonStatePermission(any(), any()));
        verify(mMockFlags, never()).isGetAdServicesCommonStatesApiEnabled();
        ApiCallStats apiCallStats = mLogApiCallStatsCallback.assertResultReceived();
        assertThat(apiCallStats).isNotNull();
        assertThat(apiCallStats.getAppPackageName()).isEqualTo(TEST_APP_PACKAGE_NAME);
        assertThat(apiCallStats.getSdkPackageName()).isEqualTo(SOME_SDK_NAME);
        assertThat(apiCallStats.getResultCode()).isEqualTo(STATUS_UNAUTHORIZED);
        assertThat(apiCallStats.getLatencyMillisecond()).isEqualTo(350);
    }

    @Test
    public void testGetAdservicesCommonStates_notAllowed() throws Exception {
        SyncIAdServicesCommonStatesCallback callback =
                new SyncIAdServicesCommonStatesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                PermissionHelper.hasAccessAdServicesCommonStatePermission(
                                        any(), any()));
        ResultSyncCallback<ApiCallStats> logApiCallStatsCallback =
                mocker.mockLogApiCallStats(mAdServicesLogger);
        when(mClock.elapsedRealtime()).thenReturn(150L, 200L);
        GetAdServicesCommonStatesParams params =
                new GetAdServicesCommonStatesParams.Builder(TEST_APP_PACKAGE_NAME, SOME_SDK_NAME)
                        .build();
        CallerMetadata metadata = new CallerMetadata.Builder().setBinderElapsedTimestamp(0).build();
        doReturn(INVALID_PACKAGE_NAME).when(mMockFlags).getAdServicesCommonStatesAllowList();
        mCommonService.getAdServicesCommonStates(params, metadata, callback);
        callback.assertFailed(STATUS_CALLER_NOT_ALLOWED_PACKAGE_NOT_IN_ALLOWLIST);

        ExtendedMockito.verify(
                () -> PermissionHelper.hasAccessAdServicesCommonStatePermission(any(), any()));
        verify(mMockFlags, never()).isGetAdServicesCommonStatesApiEnabled();
        ApiCallStats apiCallStats = logApiCallStatsCallback.assertResultReceived();
        assertThat(apiCallStats).isNotNull();
        assertThat(apiCallStats.getResultCode())
                .isEqualTo(STATUS_CALLER_NOT_ALLOWED_PACKAGE_NOT_IN_ALLOWLIST);
        assertThat(apiCallStats.getAppPackageName()).isEqualTo(TEST_APP_PACKAGE_NAME);
        assertThat(apiCallStats.getSdkPackageName()).isEqualTo(SOME_SDK_NAME);
    }

    @Test
    public void testGetAdservicesCommonStates_getCommonStatus() throws Exception {
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                PermissionHelper.hasAccessAdServicesCommonStatePermission(
                                        any(), any()));
        doReturn(true).when(mMockFlags).isGetAdServicesCommonStatesApiEnabled();
        doReturn("com.android.adservices.servicecoretest")
                .when(mMockFlags)
                .getAdServicesCommonStatesAllowList();
        ExtendedMockito.doReturn(mConsentManager).when(ConsentManager::getInstance);
        doReturn(true).when(mConsentManager).isPasMeasurementConsentGiven();
        doReturn(false).when(mConsentManager).isPasFledgeConsentGiven();
        doReturn(false).when(mConsentManager).isMeasurementDataReset();
        doReturn(false).when(mConsentManager).isPaDataReset();
        doNothing().when(mConsentManager).setMeasurementDataReset(anyBoolean());
        doNothing().when(mConsentManager).setPaDataReset(anyBoolean());
        SyncIAdServicesCommonStatesCallback callback =
                new SyncIAdServicesCommonStatesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        when(mClock.elapsedRealtime()).thenReturn(150L, 200L);

        GetAdServicesCommonStatesParams params =
                new GetAdServicesCommonStatesParams.Builder(TEST_APP_PACKAGE_NAME, SOME_SDK_NAME)
                        .build();
        CallerMetadata metadata = new CallerMetadata.Builder().setBinderElapsedTimestamp(0).build();
        mCommonService.getAdServicesCommonStates(params, metadata, callback);
        AdServicesCommonStatesResponse response = callback.assertSuccess();
        assertThat(response.getAdServicesCommonStates().getMeasurementState())
                .isEqualTo(ConsentStatus.GIVEN);
        assertThat(response.getAdServicesCommonStates().getPaState())
                .isEqualTo(ConsentStatus.REVOKED);

        ApiCallStats apiCallStats = mLogApiCallStatsCallback.assertResultReceived();
        assertThat(apiCallStats).isNotNull();
        assertThat(apiCallStats.getAppPackageName()).isEqualTo(TEST_APP_PACKAGE_NAME);
        assertThat(apiCallStats.getSdkPackageName()).isEqualTo(SOME_SDK_NAME);
        assertThat(apiCallStats.getResultCode()).isEqualTo(STATUS_SUCCESS);
        assertThat(apiCallStats.getLatencyMillisecond()).isEqualTo(350);
    }

    @Test
    public void testGetAdservicesCommonStates_getCommonStatus_reset() throws Exception {
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                PermissionHelper.hasAccessAdServicesCommonStatePermission(
                                        any(), any()));
        doReturn(true).when(mMockFlags).isGetAdServicesCommonStatesApiEnabled();
        doReturn("com.android.adservices.servicecoretest")
                .when(mMockFlags)
                .getAdServicesCommonStatesAllowList();
        ExtendedMockito.doReturn(mConsentManager).when(ConsentManager::getInstance);
        doReturn(true).when(mConsentManager).isPasMeasurementConsentGiven();
        doReturn(false).when(mConsentManager).isPasFledgeConsentGiven();
        doReturn(true).when(mConsentManager).isMeasurementDataReset();
        doReturn(true).when(mConsentManager).isPaDataReset();
        doNothing().when(mConsentManager).setMeasurementDataReset(anyBoolean());
        doNothing().when(mConsentManager).setPaDataReset(anyBoolean());
        SyncIAdServicesCommonStatesCallback callback =
                new SyncIAdServicesCommonStatesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        when(mClock.elapsedRealtime()).thenReturn(150L, 200L);

        GetAdServicesCommonStatesParams params =
                new GetAdServicesCommonStatesParams.Builder(TEST_APP_PACKAGE_NAME, SOME_SDK_NAME)
                        .build();
        CallerMetadata metadata = new CallerMetadata.Builder().setBinderElapsedTimestamp(0).build();
        mCommonService.getAdServicesCommonStates(params, metadata, callback);
        AdServicesCommonStatesResponse response = callback.assertSuccess();
        assertThat(response.getAdServicesCommonStates().getMeasurementState())
                .isEqualTo(ConsentStatus.WAS_RESET);
        assertThat(response.getAdServicesCommonStates().getPaState())
                .isEqualTo(ConsentStatus.REVOKED);

        ApiCallStats apiCallStats = mLogApiCallStatsCallback.assertResultReceived();
        assertThat(apiCallStats).isNotNull();
        assertThat(apiCallStats.getAppPackageName()).isEqualTo(TEST_APP_PACKAGE_NAME);
        assertThat(apiCallStats.getSdkPackageName()).isEqualTo(SOME_SDK_NAME);
        assertThat(apiCallStats.getResultCode()).isEqualTo(STATUS_SUCCESS);
        assertThat(apiCallStats.getLatencyMillisecond()).isEqualTo(350);
    }

    @Test
    public void testGetAdservicesCommonStates_NotEnabled() throws Exception {
        SyncIAdServicesCommonStatesCallback callback =
                new SyncIAdServicesCommonStatesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                PermissionHelper.hasAccessAdServicesCommonStatePermission(
                                        any(), any()));
        doReturn(false).when(mMockFlags).isGetAdServicesCommonStatesApiEnabled();
        doReturn("com.android.adservices.servicecoretest")
                .when(mMockFlags)
                .getAdServicesCommonStatesAllowList();
        ExtendedMockito.doReturn(mConsentManager).when(ConsentManager::getInstance);
        doReturn(true).when(mConsentManager).isMeasurementDataReset();
        doReturn(false).when(mConsentManager).isPaDataReset();
        doNothing().when(mConsentManager).setMeasurementDataReset(anyBoolean());
        doNothing().when(mConsentManager).setPaDataReset(anyBoolean());
        when(mClock.elapsedRealtime()).thenReturn(150L, 200L);

        GetAdServicesCommonStatesParams params =
                new GetAdServicesCommonStatesParams.Builder(TEST_APP_PACKAGE_NAME, SOME_SDK_NAME)
                        .build();
        CallerMetadata metadata = new CallerMetadata.Builder().setBinderElapsedTimestamp(0).build();
        mCommonService.getAdServicesCommonStates(params, metadata, callback);
        AdServicesCommonStatesResponse response = callback.assertSuccess();
        assertThat(response.getAdServicesCommonStates().getMeasurementState())
                .isEqualTo(ConsentStatus.SERVICE_NOT_ENABLED);
        assertThat(response.getAdServicesCommonStates().getPaState())
                .isEqualTo(ConsentStatus.SERVICE_NOT_ENABLED);
        ApiCallStats apiCallStats = mLogApiCallStatsCallback.assertResultReceived();
        assertThat(apiCallStats).isNotNull();
        assertThat(apiCallStats.getAppPackageName()).isEqualTo(TEST_APP_PACKAGE_NAME);
        assertThat(apiCallStats.getSdkPackageName()).isEqualTo(SOME_SDK_NAME);
        assertThat(apiCallStats.getResultCode()).isEqualTo(STATUS_SUCCESS);
        assertThat(apiCallStats.getLatencyMillisecond()).isEqualTo(350);
    }

    @Test
    public void testRequestAdServicesModuleOverrides() throws Exception {
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                PermissionHelper.hasAccessAdServicesCommonStatePermission(
                                        any(), any()));

        ExtendedMockito.doReturn(mConsentManager).when(ConsentManager::getInstance);

        RequestAdServicesModuleOverridesCallback callback =
                new RequestAdServicesModuleOverridesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        UpdateAdServicesModuleStatesParams params =
                new UpdateAdServicesModuleStatesParams.Builder()
                        .setModuleState(
                                Module.PROTECTED_AUDIENCE,
                                AdServicesCommonManager.MODULE_STATE_ENABLED)
                        .setModuleState(
                                Module.PROTECTED_APP_SIGNALS,
                                AdServicesCommonManager.MODULE_STATE_DISABLED)
                        .setNotificationType(NotificationType.NOTIFICATION_ONGOING)
                        .build();
        doNothing()
                .when(
                        () ->
                                ConsentNotificationJobService.scheduleNotificationV2(
                                        any(), anyBoolean(), anyBoolean(), anyBoolean()));
        mCommonService.requestAdServicesModuleOverrides(params, callback);

        callback.assertSuccess();
        verify(mConsentManager, atLeastOnce())
                .setModuleStates(mAdservicesModuleStatesArgumentCaptor.capture());
        SparseIntArray moduleStates = mAdservicesModuleStatesArgumentCaptor.getValue();
        assertThat(moduleStates.size()).isEqualTo(2);
        boolean isPaAvailable = false;
        boolean isPasAvailable = false;
        for (int i = 0; i < moduleStates.size(); i++) {
            int key = moduleStates.keyAt(i);
            int value = moduleStates.valueAt(i);
            switch (key) {
                case Module.PROTECTED_AUDIENCE:
                    isPaAvailable = true;
                    expect.withMessage("module: PROTECTED_AUDIENCE")
                            .that(value)
                            .isEqualTo(AdServicesCommonManager.MODULE_STATE_ENABLED);
                    break;
                case Module.PROTECTED_APP_SIGNALS:
                    isPasAvailable = true;
                    expect.withMessage("module: PROTECTED_APP_SIGNALS")
                            .that(value)
                            .isEqualTo(AdServicesCommonManager.MODULE_STATE_DISABLED);
                    break;
                default:
                    break;
            }
        }
        expect.withMessage("isPaAvailable").that(isPaAvailable).isTrue();
        expect.withMessage("isPasAvailable").that(isPasAvailable).isTrue();
    }

    @Test
    public void testSetAdServicesUserChoices() throws Exception {
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                PermissionHelper.hasAccessAdServicesCommonStatePermission(
                                        any(), any()));
        ExtendedMockito.doReturn(mConsentManager).when(ConsentManager::getInstance);

        RequestAdServicesModuleUserChoicesCallback callback =
                new RequestAdServicesModuleUserChoicesCallback(BINDER_CONNECTION_TIMEOUT_MS);

        UpdateAdServicesUserChoicesParams params =
                new UpdateAdServicesUserChoicesParams.Builder()
                        .setUserChoice(
                                Module.PROTECTED_AUDIENCE,
                                AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN)
                        .setUserChoice(
                                Module.PROTECTED_APP_SIGNALS,
                                AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT)
                        .build();
        mCommonService.requestAdServicesModuleUserChoices(params, callback);
        callback.assertSuccess();
        verify(mConsentManager, atLeastOnce())
                .setUserChoices(mAdservicesModuleUserChoiceArgumentCaptor.capture());
        List<AdServicesModuleUserChoice> userChoiceList =
                mAdservicesModuleUserChoiceArgumentCaptor.getValue();
        assertThat(userChoiceList).hasSize(2);
        boolean isPaAvailable = false;
        boolean isPasAvailable = false;
        for (AdServicesModuleUserChoice userChoice : userChoiceList) {
            switch (userChoice.getModule()) {
                case Module.PROTECTED_AUDIENCE:
                    isPaAvailable = true;
                    expect.withMessage("state.getModule(): PROTECTED_AUDIENCE")
                            .that(userChoice.getUserChoice())
                            .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN);
                    break;
                case Module.PROTECTED_APP_SIGNALS:
                    isPasAvailable = true;
                    expect.withMessage("state.getModule(): PROTECTED_APP_SIGNALS")
                            .that(userChoice.getUserChoice())
                            .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT);
                    break;
                default:
                    break;
            }
        }
        expect.withMessage("isPaAvailable").that(isPaAvailable).isTrue();
        expect.withMessage("isPasAvailable").that(isPasAvailable).isTrue();
    }

    @Test
    public void testInvalidAdServicesEnrollmentInfo() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new UpdateAdServicesModuleStatesParams.Builder().setModuleState(6, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new UpdateAdServicesModuleStatesParams.Builder().setModuleState(1, 3));
        assertThrows(IllegalArgumentException.class, () -> new AdServicesModuleUserChoice(6, 2));
        assertThrows(IllegalArgumentException.class, () -> new AdServicesModuleUserChoice(5, 4));
    }

    private IsAdServicesEnabledResult getStatusResult() throws Exception {
        SyncIAdServicesCommonCallback callback =
                new SyncIAdServicesCommonCallback(BINDER_CONNECTION_TIMEOUT_MS);

        mCommonService.isAdServicesEnabled(callback);
        return callback.assertResultReceived();
    }

    private SyncIUpdateAdIdCallback callUpdateAdIdCache(UpdateAdIdRequest request) {
        SyncIUpdateAdIdCallback callback =
                new SyncIUpdateAdIdCallback(BINDER_CONNECTION_TIMEOUT_MS);
        mCommonService.updateAdIdCache(request, callback);

        return callback;
    }

    private static final class SyncIAdServicesCommonCallback
            extends IntFailureSyncCallback<IsAdServicesEnabledResult>
            implements IAdServicesCommonCallback {
        private SyncIAdServicesCommonCallback(long timeoutMs) {
            super(timeoutMs);
        }
    }

    private static final class SyncIUpdateAdIdCallback extends IntFailureSyncCallback<String>
            implements IUpdateAdIdCallback {
        private SyncIUpdateAdIdCallback(long timeoutMs) {
            super(timeoutMs);
        }
    }

    private static final class SyncIEnableAdServicesCallback
            extends IntFailureSyncCallback<EnableAdServicesResponse>
            implements IEnableAdServicesCallback {
        private SyncIEnableAdServicesCallback(long timeoutMs) {
            super(timeoutMs);
        }
    }

    private static final class SyncIAdServicesCommonStatesCallback
            extends IntFailureSyncCallback<AdServicesCommonStatesResponse>
            implements IAdServicesCommonStatesCallback {
        private SyncIAdServicesCommonStatesCallback(long timeoutMs) {
            super(timeoutMs);
        }
    }

    private static final class RequestAdServicesModuleOverridesCallback
            extends IntFailureSyncCallback<Void>
            implements IRequestAdServicesModuleOverridesCallback {
        private RequestAdServicesModuleOverridesCallback(long timeoutMs) {
            super(timeoutMs);
        }

        @Override
        public void onSuccess() throws RemoteException {
            super.onResult(null);
        }
    }

    private static final class RequestAdServicesModuleUserChoicesCallback
            extends IntFailureSyncCallback<Void>
            implements IRequestAdServicesModuleUserChoicesCallback {
        private RequestAdServicesModuleUserChoicesCallback(long timeoutMs) {
            super(timeoutMs);
        }

        @Override
        public void onSuccess() throws RemoteException {
            super.onResult(null);
        }
    }

    private void spyBackCompatInit() {
        mSpyBackCompatInit = Mockito.spy(new AdServicesBackCompatInit(mMockContext));
        ExtendedMockito.doReturn(mSpyBackCompatInit).when(AdServicesBackCompatInit::getInstance);
    }
}
