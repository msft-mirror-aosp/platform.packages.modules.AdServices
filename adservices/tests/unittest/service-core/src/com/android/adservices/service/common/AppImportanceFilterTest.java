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

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;

import static com.android.adservices.service.common.AppImportanceFilter.UNKNOWN_APP_PACKAGE_NAME;
import static com.android.adservices.service.common.AppImportanceFilterTest.ApiCallStatsSubject.apiCallStats;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__TARGETING;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.AdServicesStatusUtils;
import android.app.ActivityManager;
import android.content.pm.PackageManager;

import androidx.annotation.Nullable;

import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.ApiCallStats;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AppImportanceFilterTest {
    private static final int API_CLASS = AD_SERVICES_API_CALLED__API_CLASS__TARGETING;
    private static final int API_NAME = AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS;
    private static final int APP_UID = 321;
    private static final String APP_PACKAGE_NAME = "test.package.name";
    private static final String SDK_NAME = "sdk.name";

    @Mock private PackageManager mPackageManager;
    @Captor ArgumentCaptor<ApiCallStats> mApiCallStatsArgumentCaptor;
    @Mock private ActivityManager mActivityManager;
    @Mock AdServicesLogger mAdServiceLogger;

    private AppImportanceFilter mAppImportanceFilter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mAppImportanceFilter =
                new AppImportanceFilter(
                        mActivityManager,
                        mPackageManager,
                        mAdServiceLogger,
                        API_CLASS,
                        () -> IMPORTANCE_FOREGROUND_SERVICE);
    }

    @Test
    public void testCalledWithForegroundAppPackageName_succeed() {
        when(mActivityManager.getPackageImportance(APP_PACKAGE_NAME))
                .thenReturn(IMPORTANCE_FOREGROUND);

        // No exception is thrown
        mAppImportanceFilter.assertCallerIsInForeground(APP_PACKAGE_NAME, API_NAME, SDK_NAME);

        verifyZeroInteractions(mAdServiceLogger, mPackageManager);
    }

    @Test
    public void testCalledWithForegroundServiceImportanceAppPackageName_succeed() {
        when(mActivityManager.getPackageImportance(APP_PACKAGE_NAME))
                .thenReturn(IMPORTANCE_FOREGROUND_SERVICE);

        // No exception is thrown
        mAppImportanceFilter.assertCallerIsInForeground(APP_PACKAGE_NAME, API_NAME, SDK_NAME);

        verifyZeroInteractions(mAdServiceLogger, mPackageManager);
    }

    @Test
    public void
            testCalledWithLessThanForegroundImportanceAppPackageName_throwsIllegalStateException() {
        when(mActivityManager.getPackageImportance(APP_PACKAGE_NAME))
                .thenReturn(IMPORTANCE_VISIBLE);

        assertThrows(
                IllegalStateException.class,
                () ->
                        mAppImportanceFilter.assertCallerIsInForeground(
                                APP_PACKAGE_NAME, API_NAME, SDK_NAME));

        verifyZeroInteractions(mPackageManager);
    }

    @Test
    public void testCalledWithLessThanForegroundImportanceAppPackageName_logsFailure() {
        when(mActivityManager.getPackageImportance(APP_PACKAGE_NAME))
                .thenReturn(IMPORTANCE_VISIBLE);

        assertThrows(
                IllegalStateException.class,
                () ->
                        mAppImportanceFilter.assertCallerIsInForeground(
                                APP_PACKAGE_NAME, API_NAME, SDK_NAME));

        verify(mAdServiceLogger).logApiCallStats(mApiCallStatsArgumentCaptor.capture());
        assertWithMessage("")
                .about(apiCallStats())
                .that(mApiCallStatsArgumentCaptor.getValue())
                .hasCode(AD_SERVICES_API_CALLED)
                .hasApiName(API_NAME)
                .hasApiClass(API_CLASS)
                .hasResultCode(AdServicesStatusUtils.STATUS_BACKGROUND_CALLER)
                .hasSdkPackageName(SDK_NAME)
                .hasAppPackageName(APP_PACKAGE_NAME);

        verifyZeroInteractions(mPackageManager);
    }

    @Test
    public void testCalledWithForegroundAppUid_succeed() {
        when(mPackageManager.getPackagesForUid(APP_UID))
                .thenReturn(new String[] {APP_PACKAGE_NAME});
        when(mActivityManager.getPackageImportance(APP_PACKAGE_NAME))
                .thenReturn(IMPORTANCE_FOREGROUND);

        // No exception is thrown
        mAppImportanceFilter.assertCallerIsInForeground(APP_UID, API_NAME, SDK_NAME);

        verifyZeroInteractions(mAdServiceLogger);
    }

    @Test
    public void testCalledWithAppUidWithOneAppInForeground_succeed() {
        String otherAppPackageName = "other.app.package";
        when(mPackageManager.getPackagesForUid(APP_UID))
                .thenReturn(new String[] {APP_PACKAGE_NAME, otherAppPackageName});
        when(mActivityManager.getPackageImportance(APP_PACKAGE_NAME))
                .thenReturn(IMPORTANCE_FOREGROUND);
        when(mActivityManager.getPackageImportance(otherAppPackageName))
                .thenReturn(IMPORTANCE_VISIBLE);

        // No exception is thrown
        mAppImportanceFilter.assertCallerIsInForeground(APP_UID, API_NAME, SDK_NAME);

        verifyZeroInteractions(mAdServiceLogger);
    }

    @Test
    public void testCalledWithForegroundServiceImportanceAppUid_succeed() {
        when(mPackageManager.getPackagesForUid(APP_UID))
                .thenReturn(new String[] {APP_PACKAGE_NAME});
        when(mActivityManager.getPackageImportance(APP_PACKAGE_NAME))
                .thenReturn(IMPORTANCE_FOREGROUND_SERVICE);

        // No exception is thrown
        mAppImportanceFilter.assertCallerIsInForeground(APP_UID, API_NAME, SDK_NAME);

        verifyZeroInteractions(mAdServiceLogger);
    }

    @Test
    public void testCalledWithLessThanForegroundImportanceAppUid_throwsIllegalStateException() {
        when(mPackageManager.getPackagesForUid(APP_UID))
                .thenReturn(new String[] {APP_PACKAGE_NAME});
        when(mActivityManager.getPackageImportance(APP_PACKAGE_NAME))
                .thenReturn(IMPORTANCE_VISIBLE);

        assertThrows(
                IllegalStateException.class,
                () -> mAppImportanceFilter.assertCallerIsInForeground(APP_UID, API_NAME, SDK_NAME));
    }

    @Test
    public void testCalledWithLessThanForegroundImportanceAppUid_logsFailure() {
        when(mPackageManager.getPackagesForUid(APP_UID))
                .thenReturn(new String[] {APP_PACKAGE_NAME});
        when(mActivityManager.getPackageImportance(APP_PACKAGE_NAME))
                .thenReturn(IMPORTANCE_VISIBLE);

        assertThrows(
                IllegalStateException.class,
                () -> mAppImportanceFilter.assertCallerIsInForeground(APP_UID, API_NAME, SDK_NAME));

        verify(mAdServiceLogger).logApiCallStats(mApiCallStatsArgumentCaptor.capture());
        assertWithMessage("")
                .about(apiCallStats())
                .that(mApiCallStatsArgumentCaptor.getValue())
                .hasCode(AD_SERVICES_API_CALLED)
                .hasApiName(API_NAME)
                .hasApiClass(API_CLASS)
                .hasResultCode(AdServicesStatusUtils.STATUS_BACKGROUND_CALLER)
                .hasSdkPackageName(SDK_NAME)
                .hasAppPackageName(APP_PACKAGE_NAME);
    }

    @Test
    public void testCalledWithLessThanForegroundImportanceAppUidAndNullSdkName_logsFailure() {
        when(mPackageManager.getPackagesForUid(APP_UID))
                .thenReturn(new String[] {APP_PACKAGE_NAME});
        when(mActivityManager.getPackageImportance(APP_PACKAGE_NAME))
                .thenReturn(IMPORTANCE_VISIBLE);

        assertThrows(
                IllegalStateException.class,
                () -> mAppImportanceFilter.assertCallerIsInForeground(APP_UID, API_NAME, null));

        verify(mAdServiceLogger).logApiCallStats(mApiCallStatsArgumentCaptor.capture());
        assertWithMessage("")
                .about(apiCallStats())
                .that(mApiCallStatsArgumentCaptor.getValue())
                .hasCode(AD_SERVICES_API_CALLED)
                .hasApiName(API_NAME)
                .hasApiClass(API_CLASS)
                .hasResultCode(AdServicesStatusUtils.STATUS_BACKGROUND_CALLER)
                .hasSdkPackageName("")
                .hasAppPackageName(APP_PACKAGE_NAME);
    }

    @Test
    public void
            testCalledWithMultipleLessThanForegroundImportanceAppUid_throwsIllegalStateException() {
        String otherAppPackageName = "other.app.package";
        when(mPackageManager.getPackagesForUid(APP_UID))
                .thenReturn(new String[] {APP_PACKAGE_NAME, otherAppPackageName});
        when(mActivityManager.getPackageImportance(APP_PACKAGE_NAME))
                .thenReturn(IMPORTANCE_VISIBLE);
        when(mActivityManager.getPackageImportance(otherAppPackageName))
                .thenReturn(IMPORTANCE_VISIBLE);

        assertThrows(
                IllegalStateException.class,
                () -> mAppImportanceFilter.assertCallerIsInForeground(APP_UID, API_NAME, SDK_NAME));
    }

    @Test
    public void testCalledWithMultipleLessThanForegroundImportanceAppUid_logsFailure() {
        String otherAppPackageName = "other.app.package";
        when(mPackageManager.getPackagesForUid(APP_UID))
                .thenReturn(new String[] {APP_PACKAGE_NAME, otherAppPackageName});
        when(mActivityManager.getPackageImportance(APP_PACKAGE_NAME))
                .thenReturn(IMPORTANCE_VISIBLE);
        when(mActivityManager.getPackageImportance(otherAppPackageName))
                .thenReturn(IMPORTANCE_VISIBLE);

        assertThrows(
                IllegalStateException.class,
                () -> mAppImportanceFilter.assertCallerIsInForeground(APP_UID, API_NAME, SDK_NAME));

        verify(mAdServiceLogger).logApiCallStats(mApiCallStatsArgumentCaptor.capture());
        assertWithMessage("")
                .about(apiCallStats())
                .that(mApiCallStatsArgumentCaptor.getValue())
                .hasCode(AD_SERVICES_API_CALLED)
                .hasApiName(API_NAME)
                .hasApiClass(API_CLASS)
                .hasResultCode(AdServicesStatusUtils.STATUS_BACKGROUND_CALLER)
                .hasSdkPackageName(SDK_NAME)
                .hasAppPackageName(UNKNOWN_APP_PACKAGE_NAME);
    }

    @Test
    public void testFailureTryingToRetrievePackageImportanceFromUid_throwsIllegalStateException() {
        when(mPackageManager.getPackagesForUid(APP_UID))
                .thenReturn(new String[] {APP_PACKAGE_NAME});
        when(mActivityManager.getPackageImportance(APP_PACKAGE_NAME))
                .thenThrow(new SecurityException("Simulating failure calling activity manager"));

        assertThrows(
                IllegalStateException.class,
                () -> mAppImportanceFilter.assertCallerIsInForeground(APP_UID, API_NAME, SDK_NAME));
    }

    @Test
    public void testFailureTryingToRetrievePackageImportanceFromUid_logsFailure() {
        when(mPackageManager.getPackagesForUid(APP_UID))
                .thenReturn(new String[] {APP_PACKAGE_NAME});
        when(mActivityManager.getPackageImportance(APP_PACKAGE_NAME))
                .thenThrow(new SecurityException("Simulating failure calling activity manager"));

        assertThrows(
                IllegalStateException.class,
                () -> mAppImportanceFilter.assertCallerIsInForeground(APP_UID, API_NAME, SDK_NAME));

        verify(mAdServiceLogger).logApiCallStats(mApiCallStatsArgumentCaptor.capture());
        assertWithMessage("")
                .about(apiCallStats())
                .that(mApiCallStatsArgumentCaptor.getValue())
                .hasCode(AD_SERVICES_API_CALLED)
                .hasApiName(API_NAME)
                .hasApiClass(API_CLASS)
                .hasResultCode(AdServicesStatusUtils.STATUS_BACKGROUND_CALLER)
                .hasSdkPackageName(SDK_NAME)
                .hasAppPackageName(APP_PACKAGE_NAME);
    }

    public static final class ApiCallStatsSubject extends Subject {
        public static Factory<ApiCallStatsSubject, ApiCallStats> apiCallStats() {
            return ApiCallStatsSubject::new;
        }

        @Nullable private final ApiCallStats mActual;

        ApiCallStatsSubject(FailureMetadata metadata, @Nullable Object actual) {
            super(metadata, actual);
            this.mActual = (ApiCallStats) actual;
        }

        public ApiCallStatsSubject hasCode(int code) {
            check("getCode()").that(mActual.getCode()).isEqualTo(code);
            return this;
        }

        public ApiCallStatsSubject hasApiClass(int apiClass) {
            check("getApiClass()").that(mActual.getApiClass()).isEqualTo(apiClass);
            return this;
        }

        public ApiCallStatsSubject hasApiName(int apiName) {
            check("getApiName()").that(mActual.getApiName()).isEqualTo(apiName);
            return this;
        }

        public ApiCallStatsSubject hasResultCode(int resultCode) {
            check("getResultCode()").that(mActual.getResultCode()).isEqualTo(resultCode);
            return this;
        }

        public ApiCallStatsSubject hasSdkPackageName(String sdkPackageName) {
            check("getSdkPackageName()")
                    .that(mActual.getSdkPackageName())
                    .isEqualTo(sdkPackageName);
            return this;
        }

        public ApiCallStatsSubject hasAppPackageName(String sdkPackageName) {
            check("getAppPackageName()")
                    .that(mActual.getAppPackageName())
                    .isEqualTo(sdkPackageName);
            return this;
        }
    }
}
