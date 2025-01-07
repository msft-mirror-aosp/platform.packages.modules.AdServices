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

import static android.adservices.common.AdServicesCommonManager.MODULE_STATE_DISABLED;
import static android.adservices.common.AdServicesCommonManager.MODULE_STATE_ENABLED;
import static android.adservices.common.AdServicesCommonManager.NOTIFICATION_ONGOING;
import static android.adservices.common.AdServicesCommonManager.NOTIFICATION_REGULAR;
import static android.adservices.common.AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN;
import static android.adservices.common.AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT;
import static android.adservices.common.AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN;
import static android.adservices.common.AdServicesStatusUtils.STATUS_ADSERVICES_ACTIVITY_DISABLED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED_PACKAGE_NOT_IN_ALLOWLIST;
import static android.adservices.common.AdServicesStatusUtils.STATUS_KILLSWITCH_ENABLED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;
import static android.adservices.common.Module.PROTECTED_APP_SIGNALS;
import static android.adservices.common.Module.PROTECTED_AUDIENCE;

import static com.android.adservices.data.common.AdservicesEntryPointConstant.ADSERVICES_ENTRY_POINT_STATUS_DISABLE;
import static com.android.adservices.data.common.AdservicesEntryPointConstant.ADSERVICES_ENTRY_POINT_STATUS_ENABLE;
import static com.android.adservices.data.common.AdservicesEntryPointConstant.KEY_ADSERVICES_ENTRY_POINT_STATUS;
import static com.android.adservices.service.FlagsConstants.KEY_ADSERVICES_CONSENT_BUSINESS_LOGIC_MIGRATION_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_ADSERVICES_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_AD_SERVICES_SYSTEM_API;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_BACK_COMPAT;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_BACK_COMPAT_INIT;
import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_GET_ADSERVICES_COMMON_STATES_ALLOW_LIST;
import static com.android.adservices.service.FlagsConstants.KEY_IS_BACK_COMPACT_ACTIVITY_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_IS_GET_ADSERVICES_COMMON_STATES_API_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_UI_EEA_COUNTRIES;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IAPC_UPDATE_AD_ID_API_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__AD_ID;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.GA_UX;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

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
import android.adservices.common.NotificationType.NotificationTypeCode;
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
import com.android.adservices.common.annotations.DisableGlobalKillSwitch;
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
import com.android.adservices.shared.testing.annotations.SetFlagFalse;
import com.android.adservices.shared.testing.annotations.SetFlagTrue;
import com.android.adservices.shared.testing.annotations.SetStringFlag;
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
@SetFlagTrue(KEY_ADSERVICES_ENABLED)
// Set device to EU
@SetStringFlag(name = KEY_UI_EEA_COUNTRIES, value = Flags.UI_EEA_COUNTRIES)
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

    // TODO(b/384949821): move to superclass
    private final Flags mFakeFlags = flags.getFlags();

    @Before
    public void setup() {
        mCommonService =
                new AdServicesCommonServiceImpl(
                        mMockContext,
                        mFakeFlags,
                        mMockDebugFlags,
                        mUxEngine,
                        mUxStatesManager,
                        mMockAdIdWorker,
                        mAdServicesLogger,
                        mClock);
        mLogApiCallStatsCallback = mocker.mockLogApiCallStats(mAdServicesLogger);
        mocker.mockGetFlags(mFakeFlags);
        mocker.mockGetDebugFlags(mMockDebugFlags);

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

        doReturn("pl").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mMockContext).getPackageManager();
        doReturn(mTelephonyManager).when(mMockContext).getSystemService(TelephonyManager.class);
        doReturn(true).when(mUxStatesManager).isEnrolledUser(mMockContext);
    }

    // For the old entry point logic, we only check the UX flag and user enrollment is irrelevant.
    @SetFlagFalse(KEY_ENABLE_AD_SERVICES_SYSTEM_API)
    @Test
    public void isAdServiceEnabledTest_userNotEnrolledEntryPointLogicV1() throws Exception {
        doReturn(false).when(mUxStatesManager).isEnrolledUser(mMockContext);
        mockGaUxFeatureEnabled(false);
        mCommonService =
                new AdServicesCommonServiceImpl(
                        mMockContext,
                        mFakeFlags,
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
    @SetFlagTrue(KEY_ENABLE_AD_SERVICES_SYSTEM_API)
    public void isAdServiceEnabledTest_userNotEnrolledEntryPointLogicV2() throws Exception {
        doReturn(false).when(mUxStatesManager).isEnrolledUser(mMockContext);
        doReturn(GA_UX).when(mConsentManager).getUx();

        mCommonService =
                new AdServicesCommonServiceImpl(
                        mMockContext,
                        mFakeFlags,
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
        mockGaUxFeatureEnabled(false);
        mCommonService =
                new AdServicesCommonServiceImpl(
                        mMockContext,
                        mFakeFlags,
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
        flags.setFlag(KEY_ADSERVICES_ENABLED, false);

        // Calling again, expect to false
        getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isFalse();
    }

    @Test
    @SetFlagTrue(KEY_IS_BACK_COMPACT_ACTIVITY_FEATURE_ENABLED)
    public void getAdserviceStatusWithCheckActivityTest() throws Exception {
        mockGaUxFeatureEnabled(false);
        mCommonService =
                new AdServicesCommonServiceImpl(
                        mMockContext,
                        mFakeFlags,
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
        mockGaUxFeatureEnabled(true);
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
        mockGaUxFeatureEnabled(false);
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
        mockGaUxFeatureEnabled(true);
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
        mockGaUxFeatureEnabled(true);
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
        mockGaUxFeatureEnabled(true);
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
        mockGaUxFeatureEnabled(true);
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
        mockGaUxFeatureEnabled(true);
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
        mockGaUxFeatureEnabled(true);
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
    public void setAdservicesEnabledConsentTest_ReconsentGaUxFeatureDisabled() throws Exception {
        // GA UX feature disable
        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        mockGaUxFeatureEnabled(false);
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
        mockGaUxFeatureEnabled(true);
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
            throws Exception {
        // ROW device, GA UX notification displayed
        mockGaUxFeatureEnabled(true);
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
            throws Exception {
        // GA UX notification not displayed, notification not displayed, this also trigger
        // first consent case, but we verify here for reconsentStatus as true
        mockGaUxFeatureEnabled(true);
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
    public void setAdservicesEnabledConsentTest_ReconsentUserConsentRevoked() throws Exception {
        // Notification displayed, user consent is revoked
        mockGaUxFeatureEnabled(true);
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
    public void setAdservicesEnabledConsentTest_FirstConsentHappycase() throws Exception {
        // First Consent happy case, should be executed
        mockGaUxFeatureEnabled(true);
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
            throws Exception {
        // GA UX notification was displayed
        mockGaUxFeatureEnabled(true);
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
            throws Exception {
        // Notification was displayed
        mockGaUxFeatureEnabled(true);
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
        verify(mUxEngine, never()).start(any());
    }

    @Test
    @FlakyTest(bugId = 299686058)
    @SetFlagFalse(KEY_ENABLE_AD_SERVICES_SYSTEM_API)
    public void enableAdServicesTest_apiDisabled() throws Exception {
        SyncIEnableAdServicesCallback callback =
                new SyncIEnableAdServicesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        ExtendedMockito.doReturn(true)
                .when(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));

        mCommonService.enableAdServices(new AdServicesStates.Builder().build(), callback);
        assertThat(callback.assertSuccess().isApiEnabled()).isFalse();

        ExtendedMockito.verify(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        verify(mUxEngine, never()).start(any());
    }

    @Test
    @SetFlagTrue(KEY_ENABLE_AD_SERVICES_SYSTEM_API)
    public void enableAdServicesTest_engineStarted() throws Exception {
        SyncIEnableAdServicesCallback callback =
                new SyncIEnableAdServicesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        ExtendedMockito.doReturn(true)
                .when(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));

        mCommonService.enableAdServices(new AdServicesStates.Builder().build(), callback);
        EnableAdServicesResponse response = callback.assertSuccess();
        assertThat(response.isApiEnabled()).isTrue();
        assertThat(response.isSuccess()).isTrue();

        ExtendedMockito.verify(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        verify(mUxEngine).start(any());
    }

    @Test
    @SetFlagTrue(KEY_ENABLE_AD_SERVICES_SYSTEM_API)
    @SetFlagTrue(KEY_ENABLE_BACK_COMPAT_INIT)
    @SetFlagTrue(KEY_ENABLE_BACK_COMPAT)
    @SetFlagTrue(KEY_ADSERVICES_ENABLED)
    @DisableGlobalKillSwitch
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

        doReturn(EXT_SERVICES_APK_PKG_SUFFIX).when(mMockContext).getPackageName();
        spyBackCompatInit();
        ExtendedMockito.doReturn(true)
                .when(() -> PackageManagerCompatUtils.isAdServicesActivityEnabled(any()));

        mCommonService.enableAdServices(new AdServicesStates.Builder().build(), callback);
        EnableAdServicesResponse response = callback.assertSuccess();
        assertThat(response.isApiEnabled()).isTrue();
        assertThat(response.isSuccess()).isTrue();

        ExtendedMockito.verify(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        verify(mSpyBackCompatInit).initializeComponents();
    }

    @Test
    @RequiresSdkLevelAtLeastT
    @SetFlagTrue(KEY_ENABLE_AD_SERVICES_SYSTEM_API)
    @SetFlagTrue(KEY_ENABLE_BACK_COMPAT_INIT)
    public void enableAdServicesTest_tPlus_adServicesPackage_skipBackCompatInit() throws Exception {
        SyncIEnableAdServicesCallback callback =
                new SyncIEnableAdServicesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        ExtendedMockito.doReturn(true)
                .when(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        doReturn(AD_SERVICES_APK_PKG_SUFFIX).when(mMockContext).getPackageName();
        spyBackCompatInit();
        ExtendedMockito.doReturn(true)
                .when(() -> PackageManagerCompatUtils.isAdServicesActivityEnabled(any()));

        mCommonService.enableAdServices(new AdServicesStates.Builder().build(), callback);
        EnableAdServicesResponse response = callback.assertSuccess();
        assertThat(response.isApiEnabled()).isTrue();
        assertThat(response.isSuccess()).isTrue();

        ExtendedMockito.verify(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        verify(mSpyBackCompatInit).initializeComponents();
        verify(mPackageManager, never())
                .setComponentEnabledSetting(
                        any(ComponentName.class),
                        eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED),
                        eq(PackageManager.DONT_KILL_APP));
    }

    @Test
    @SetFlagTrue(KEY_ENABLE_AD_SERVICES_SYSTEM_API)
    @SetFlagTrue(KEY_ENABLE_BACK_COMPAT_INIT)
    public void enableAdServicesTest_activitiesDisabled_skipUxEngine() throws Exception {
        SyncIEnableAdServicesCallback callback =
                new SyncIEnableAdServicesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        ExtendedMockito.doReturn(true)
                .when(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        spyBackCompatInit();
        ExtendedMockito.doReturn(false)
                .when(() -> PackageManagerCompatUtils.isAdServicesActivityEnabled(any()));

        mCommonService.enableAdServices(new AdServicesStates.Builder().build(), callback);
        callback.assertFailed(STATUS_ADSERVICES_ACTIVITY_DISABLED);

        ExtendedMockito.verify(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        verify(mSpyBackCompatInit).initializeComponents();
        verify(mUxEngine, never()).start(any());
    }

    @Test
    @RequiresSdkLevelAtLeastT
    @SetFlagTrue(KEY_ENABLE_AD_SERVICES_SYSTEM_API)
    @SetFlagTrue(KEY_ENABLE_BACK_COMPAT_INIT)
    public void enableAdServicesTest_activitiesEnabled_startUxEngine() throws Exception {
        SyncIEnableAdServicesCallback callback =
                new SyncIEnableAdServicesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        ExtendedMockito.doReturn(true)
                .when(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        spyBackCompatInit();
        ExtendedMockito.doReturn(true)
                .when(() -> PackageManagerCompatUtils.isAdServicesActivityEnabled(any()));

        mCommonService.enableAdServices(new AdServicesStates.Builder().build(), callback);
        EnableAdServicesResponse response = callback.assertSuccess();
        assertThat(response.isApiEnabled()).isTrue();
        assertThat(response.isSuccess()).isTrue();

        ExtendedMockito.verify(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        verify(mSpyBackCompatInit).initializeComponents();
        verify(mUxEngine).start(any());
    }

    @Test
    public void testUpdateAdIdChange() throws Exception {
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
    public void testUpdateAdIdChange_unauthorizedCaller() throws Exception {
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
    public void testUpdateAdIdChange_throwsException() throws Exception {
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
        mockAdServicesCommonStatesAllowList(INVALID_PACKAGE_NAME);
        mCommonService.getAdServicesCommonStates(params, metadata, callback);
        callback.assertFailed(STATUS_CALLER_NOT_ALLOWED_PACKAGE_NOT_IN_ALLOWLIST);

        ExtendedMockito.verify(
                () -> PermissionHelper.hasAccessAdServicesCommonStatePermission(any(), any()));
        ApiCallStats apiCallStats = logApiCallStatsCallback.assertResultReceived();
        assertThat(apiCallStats).isNotNull();
        assertThat(apiCallStats.getResultCode())
                .isEqualTo(STATUS_CALLER_NOT_ALLOWED_PACKAGE_NOT_IN_ALLOWLIST);
        assertThat(apiCallStats.getAppPackageName()).isEqualTo(TEST_APP_PACKAGE_NAME);
        assertThat(apiCallStats.getSdkPackageName()).isEqualTo(SOME_SDK_NAME);
    }

    @Test
    @SetFlagTrue(KEY_IS_GET_ADSERVICES_COMMON_STATES_API_ENABLED)
    public void testGetAdservicesCommonStates_getCommonStatus() throws Exception {
        mockAdServicesCommonStatesAllowList(mPackageName);
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                PermissionHelper.hasAccessAdServicesCommonStatePermission(
                                        any(), any()));
        ExtendedMockito.doReturn(mConsentManager).when(ConsentManager::getInstance);
        doReturn(true).when(mConsentManager).isOdpMeasurementConsentGiven();
        doReturn(false).when(mConsentManager).isPasConsentGiven();
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
    @SetFlagTrue(KEY_IS_GET_ADSERVICES_COMMON_STATES_API_ENABLED)
    public void testGetAdservicesCommonStates_getCommonStatus_reset() throws Exception {
        mockAdServicesCommonStatesAllowList(mPackageName);

        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                PermissionHelper.hasAccessAdServicesCommonStatePermission(
                                        any(), any()));
        ExtendedMockito.doReturn(mConsentManager).when(ConsentManager::getInstance);
        doReturn(true).when(mConsentManager).isOdpMeasurementConsentGiven();
        doReturn(false).when(mConsentManager).isPasConsentGiven();
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
    @SetFlagFalse(KEY_IS_GET_ADSERVICES_COMMON_STATES_API_ENABLED)
    public void testGetAdservicesCommonStates_NotEnabled() throws Exception {
        mockAdServicesCommonStatesAllowList(mPackageName);
        SyncIAdServicesCommonStatesCallback callback =
                new SyncIAdServicesCommonStatesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                PermissionHelper.hasAccessAdServicesCommonStatePermission(
                                        any(), any()));
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
    public void testRequestAdServicesModuleOverrides_GaAlreadyEnrolled() {
        requestAdServicesModuleOverridesHelper(
                new int[] {
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_DISABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_DISABLED
                },
                new int[] {
                    USER_CHOICE_OPTED_IN,
                    USER_CHOICE_OPTED_IN,
                    USER_CHOICE_UNKNOWN,
                    USER_CHOICE_OPTED_IN,
                    USER_CHOICE_UNKNOWN
                },
                new int[] {
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_DISABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_DISABLED
                },
                NOTIFICATION_REGULAR,
                false,
                new boolean[] {false, true, false});
    }

    @Test
    public void testRequestAdServicesModuleOverrides_PasAlreadyEnrolled() {
        requestAdServicesModuleOverridesHelper(
                new int[] {
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED
                },
                new int[] {
                    USER_CHOICE_OPTED_IN,
                    USER_CHOICE_OPTED_IN,
                    USER_CHOICE_OPTED_IN,
                    USER_CHOICE_OPTED_IN,
                    USER_CHOICE_OPTED_IN
                },
                new int[] {
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED
                },
                NOTIFICATION_REGULAR,
                false,
                new boolean[] {false, true, false});
    }

    @Test
    public void testRequestAdServicesModuleOverrides_GaFirstTimeReg() {
        requestAdServicesModuleOverridesHelper(
                new int[] {
                    MODULE_STATE_DISABLED,
                    MODULE_STATE_DISABLED,
                    MODULE_STATE_DISABLED,
                    MODULE_STATE_DISABLED,
                    MODULE_STATE_DISABLED
                },
                new int[] {
                    USER_CHOICE_UNKNOWN,
                    USER_CHOICE_UNKNOWN,
                    USER_CHOICE_UNKNOWN,
                    USER_CHOICE_UNKNOWN,
                    USER_CHOICE_UNKNOWN
                },
                new int[] {
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_DISABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_DISABLED
                },
                NOTIFICATION_REGULAR,
                true,
                new boolean[] {false, true, false});
    }

    @Test
    public void testRequestAdServicesModuleOverrides_PasRenotifyRegAllOptIn() {
        requestAdServicesModuleOverridesHelper(
                new int[] {
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_DISABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_DISABLED
                },
                new int[] {
                    USER_CHOICE_OPTED_IN,
                    USER_CHOICE_OPTED_IN,
                    USER_CHOICE_UNKNOWN,
                    USER_CHOICE_OPTED_IN,
                    USER_CHOICE_UNKNOWN
                },
                new int[] {
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED
                },
                NOTIFICATION_REGULAR,
                true,
                new boolean[] {true, true, false});
    }

    @Test
    public void testRequestAdServicesModuleOverrides_PasRenotifyRegSomeOptIn() {
        requestAdServicesModuleOverridesHelper(
                new int[] {
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_DISABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_DISABLED
                },
                new int[] {
                    USER_CHOICE_OPTED_IN,
                    USER_CHOICE_OPTED_OUT,
                    USER_CHOICE_UNKNOWN,
                    USER_CHOICE_OPTED_IN,
                    USER_CHOICE_UNKNOWN
                },
                new int[] {
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED
                },
                NOTIFICATION_REGULAR,
                true,
                new boolean[] {true, true, false});
    }

    @Test
    public void testRequestAdServicesModuleOverrides_PasRenotifyOngoingSomeOptIn() {
        requestAdServicesModuleOverridesHelper(
                new int[] {
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_DISABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_DISABLED
                },
                new int[] {
                    USER_CHOICE_OPTED_IN,
                    USER_CHOICE_OPTED_OUT,
                    USER_CHOICE_UNKNOWN,
                    USER_CHOICE_OPTED_IN,
                    USER_CHOICE_UNKNOWN
                },
                new int[] {
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED
                },
                NOTIFICATION_ONGOING,
                true,
                new boolean[] {true, true, true});
    }

    @Test
    public void testRequestAdServicesModuleOverrides_PasRenotifyRegAllOptOut() {
        requestAdServicesModuleOverridesHelper(
                new int[] {
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_DISABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_DISABLED
                },
                new int[] {
                    USER_CHOICE_OPTED_OUT,
                    USER_CHOICE_OPTED_OUT,
                    USER_CHOICE_UNKNOWN,
                    USER_CHOICE_OPTED_OUT,
                    USER_CHOICE_UNKNOWN
                },
                new int[] {
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED
                },
                NOTIFICATION_REGULAR,
                false,
                new boolean[] {true, true, false});
    }

    @Test
    public void testRequestAdServicesModuleOverrides_MsmtOnlyFirstTime() {
        requestAdServicesModuleOverridesHelper(
                new int[] {
                    MODULE_STATE_DISABLED,
                    MODULE_STATE_DISABLED,
                    MODULE_STATE_DISABLED,
                    MODULE_STATE_DISABLED,
                    MODULE_STATE_DISABLED
                },
                new int[] {
                    USER_CHOICE_UNKNOWN,
                    USER_CHOICE_UNKNOWN,
                    USER_CHOICE_UNKNOWN,
                    USER_CHOICE_UNKNOWN,
                    USER_CHOICE_UNKNOWN
                },
                new int[] {
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_DISABLED,
                    MODULE_STATE_DISABLED,
                    MODULE_STATE_DISABLED,
                    MODULE_STATE_DISABLED
                },
                NOTIFICATION_REGULAR,
                true,
                new boolean[] {false, false, false});
    }

    @Test
    public void testRequestAdServicesModuleOverrides_MsmtOnlyDetention() {
        requestAdServicesModuleOverridesHelper(
                new int[] {
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_DISABLED,
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_DISABLED
                },
                new int[] {
                    USER_CHOICE_OPTED_IN,
                    USER_CHOICE_OPTED_IN,
                    USER_CHOICE_UNKNOWN,
                    USER_CHOICE_OPTED_IN,
                    USER_CHOICE_UNKNOWN
                },
                new int[] {
                    MODULE_STATE_ENABLED,
                    MODULE_STATE_DISABLED,
                    MODULE_STATE_DISABLED,
                    MODULE_STATE_DISABLED,
                    MODULE_STATE_DISABLED
                },
                NOTIFICATION_REGULAR,
                true,
                new boolean[] {true, false, false});
    }

    /**
     * Ordering of modules: {MSMT, PA, PAS, TOPICS, ODP} Ordering of notification booleans:
     * {isRenotify, isNewAdPersonalizationModuleEnabled, isOngoingNotification}
     */
    private void requestAdServicesModuleOverridesHelper(
            int[] curStates,
            int[] userChoices,
            int[] desiredStates,
            @NotificationTypeCode int notificationType,
            boolean expectNotification,
            boolean[] expectedBooleans) {
        try {
            requestAdServicesModuleOverridesHelper(
                    toSparseIntArray(curStates),
                    toSparseIntArray(userChoices),
                    toSparseIntArray(desiredStates),
                    notificationType,
                    expectNotification,
                    expectedBooleans);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private SparseIntArray toSparseIntArray(int[] array) {
        SparseIntArray sparseArray = new SparseIntArray(array.length);
        for (int i = 0; i < array.length; i++) {
            if (array[i] < 0) {
                continue;
            }
            sparseArray.put(i, array[i]);
        }
        return sparseArray;
    }

    private void requestAdServicesModuleOverridesHelper(
            SparseIntArray curStates,
            SparseIntArray userChoices,
            SparseIntArray desiredStates,
            @NotificationTypeCode int notificationType,
            boolean expectNotification,
            boolean[] expectedBooleans)
            throws InterruptedException {
        // common setup
        flags.setFlag(KEY_ADSERVICES_CONSENT_BUSINESS_LOGIC_MIGRATION_ENABLED, true);
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                PermissionHelper.hasAccessAdServicesCommonStatePermission(
                                        any(), any()));
        ExtendedMockito.doReturn(mConsentManager).when(ConsentManager::getInstance);

        RequestAdServicesModuleOverridesCallback callback =
                new RequestAdServicesModuleOverridesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        doNothing()
                .when(
                        () ->
                                ConsentNotificationJobService.scheduleNotificationV2(
                                        any(), anyBoolean(), anyBoolean(), anyBoolean()));

        // specific setup
        for (int i = 0; i < userChoices.size(); i++) {
            int module = userChoices.keyAt(i);
            int userChoice = userChoices.valueAt(i);
            doReturn(userChoice).when(mConsentManager).getUserChoice(module);
        }
        for (int i = 0; i < curStates.size(); i++) {
            int module = curStates.keyAt(i);
            int state = curStates.valueAt(i);
            doReturn(state).when(mConsentManager).getModuleState(module);
        }

        // specific inputs
        UpdateAdServicesModuleStatesParams.Builder builder =
                new UpdateAdServicesModuleStatesParams.Builder();
        for (int i = 0; i < desiredStates.size(); i++) {
            int module = desiredStates.keyAt(i);
            int state = desiredStates.valueAt(i);
            builder.setModuleState(module, state);
        }
        builder.setNotificationType(notificationType);
        UpdateAdServicesModuleStatesParams params = builder.build();

        // make call
        mCommonService.requestAdServicesModuleOverrides(params, callback);

        // common checks
        callback.assertSuccess();
        verify(mConsentManager, atLeastOnce())
                .setModuleStates(mAdservicesModuleStatesArgumentCaptor.capture());

        // specific checks
        SparseIntArray actualModuleStates = mAdservicesModuleStatesArgumentCaptor.getValue();
        assertThat(actualModuleStates.size()).isEqualTo(desiredStates.size());
        for (int i = 0; i < actualModuleStates.size(); i++) {
            int module = actualModuleStates.keyAt(i);
            int actualState = actualModuleStates.valueAt(i);
            int expectedState = desiredStates.get(module);

            expect.withMessage("state for module:" + module)
                    .that(actualState)
                    .isEqualTo(expectedState);
        }
        if (expectNotification) {
            ExtendedMockito.verify(
                    () ->
                            ConsentNotificationJobService.scheduleNotificationV2(
                                    any(),
                                    eq(expectedBooleans[0]),
                                    eq(expectedBooleans[1]),
                                    eq(expectedBooleans[2])),
                    times(1));
        } else {
            ExtendedMockito.verify(
                    () ->
                            ConsentNotificationJobService.scheduleNotificationV2(
                                    any(), anyBoolean(), anyBoolean(), anyBoolean()),
                    never());
        }
    }

    @Test
    @SetFlagFalse(KEY_ADSERVICES_CONSENT_BUSINESS_LOGIC_MIGRATION_ENABLED)
    public void testRequestAdServicesModuleOverrides_callbackFail() throws Exception {
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
                        .setModuleState(PROTECTED_AUDIENCE, MODULE_STATE_ENABLED)
                        .setModuleState(PROTECTED_APP_SIGNALS, MODULE_STATE_DISABLED)
                        .setNotificationType(NotificationType.NOTIFICATION_ONGOING)
                        .build();
        doNothing()
                .when(
                        () ->
                                ConsentNotificationJobService.scheduleNotificationV2(
                                        any(), anyBoolean(), anyBoolean(), anyBoolean()));
        mCommonService.requestAdServicesModuleOverrides(params, callback);

        callback.assertFailed(STATUS_KILLSWITCH_ENABLED);
        verify(mConsentManager, never()).setModuleStates(any());
    }

    @Test
    @SetFlagTrue(KEY_ADSERVICES_CONSENT_BUSINESS_LOGIC_MIGRATION_ENABLED)
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
                        .setUserChoice(PROTECTED_AUDIENCE, USER_CHOICE_OPTED_IN)
                        .setUserChoice(PROTECTED_APP_SIGNALS, USER_CHOICE_OPTED_OUT)
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
                case PROTECTED_AUDIENCE:
                    isPaAvailable = true;
                    expect.withMessage("state.getModule(): PROTECTED_AUDIENCE")
                            .that(userChoice.getUserChoice())
                            .isEqualTo(USER_CHOICE_OPTED_IN);
                    break;
                case PROTECTED_APP_SIGNALS:
                    isPasAvailable = true;
                    expect.withMessage("state.getModule(): PROTECTED_APP_SIGNALS")
                            .that(userChoice.getUserChoice())
                            .isEqualTo(USER_CHOICE_OPTED_OUT);
                    break;
                default:
                    break;
            }
        }
        expect.withMessage("isPaAvailable").that(isPaAvailable).isTrue();
        expect.withMessage("isPasAvailable").that(isPasAvailable).isTrue();
    }

    @Test
    @SetFlagFalse(KEY_ADSERVICES_CONSENT_BUSINESS_LOGIC_MIGRATION_ENABLED)
    public void testSetAdServicesUserChoices_callbackFail() throws Exception {
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
                                PROTECTED_AUDIENCE, AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN)
                        .setUserChoice(
                                PROTECTED_APP_SIGNALS,
                                AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT)
                        .build();
        mCommonService.requestAdServicesModuleUserChoices(params, callback);
        callback.assertFailed(STATUS_KILLSWITCH_ENABLED);
        verify(mConsentManager, never()).setUserChoices(any());
    }

    @Test
    @SetFlagTrue(KEY_ADSERVICES_CONSENT_BUSINESS_LOGIC_MIGRATION_ENABLED)
    public void testSetAdServicesUserChoices_noOverrideUnlessUnknown() throws Exception {
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                PermissionHelper.hasAccessAdServicesCommonStatePermission(
                                        any(), any()));
        ExtendedMockito.doReturn(mConsentManager).when(ConsentManager::getInstance);
        doReturn(USER_CHOICE_UNKNOWN).when(mConsentManager).getUserChoice(PROTECTED_AUDIENCE);
        doReturn(USER_CHOICE_OPTED_OUT).when(mConsentManager).getUserChoice(PROTECTED_APP_SIGNALS);
        doReturn(USER_CHOICE_OPTED_IN).when(mConsentManager).getUserChoice(Module.MEASUREMENT);

        RequestAdServicesModuleUserChoicesCallback callback =
                new RequestAdServicesModuleUserChoicesCallback(BINDER_CONNECTION_TIMEOUT_MS);

        UpdateAdServicesUserChoicesParams params =
                new UpdateAdServicesUserChoicesParams.Builder()
                        .setUserChoice(PROTECTED_AUDIENCE, USER_CHOICE_OPTED_IN)
                        .setUserChoice(PROTECTED_APP_SIGNALS, USER_CHOICE_OPTED_IN)
                        .setUserChoice(Module.MEASUREMENT, USER_CHOICE_UNKNOWN)
                        .build();
        mCommonService.requestAdServicesModuleUserChoices(params, callback);
        callback.assertSuccess();
        verify(mConsentManager, atLeastOnce())
                .setUserChoices(mAdservicesModuleUserChoiceArgumentCaptor.capture());
        List<AdServicesModuleUserChoice> userChoiceList =
                mAdservicesModuleUserChoiceArgumentCaptor.getValue();
        assertThat(userChoiceList).hasSize(2);
        boolean isPaOptedIn = false;
        boolean isPasOptedIn = false;
        boolean isMsmtReset = false;
        for (AdServicesModuleUserChoice userChoice : userChoiceList) {
            switch (userChoice.getModule()) {
                case PROTECTED_AUDIENCE:
                    isPaOptedIn = userChoice.getUserChoice() == USER_CHOICE_OPTED_IN;
                    break;
                case PROTECTED_APP_SIGNALS:
                    fail("PROTECTED_APP_SIGNALS should not be set");
                    break;
                case Module.MEASUREMENT:
                    isMsmtReset = userChoice.getUserChoice() == USER_CHOICE_UNKNOWN;
                    break;
                default:
                    break;
            }
        }
        expect.withMessage("isPaOptedIn").that(isPaOptedIn).isTrue();
        expect.withMessage("isPasOptedIn").that(isPasOptedIn).isFalse();
        expect.withMessage("isMsmtReset").that(isMsmtReset).isTrue();
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

    private void mockGaUxFeatureEnabled(boolean value) {
        flags.setFlag(KEY_GA_UX_FEATURE_ENABLED, value);
    }

    private void mockAdServicesCommonStatesAllowList(String packageName) {
        flags.setFlag(KEY_GET_ADSERVICES_COMMON_STATES_ALLOW_LIST, packageName);
    }
}
