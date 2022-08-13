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
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE;
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

import com.android.adservices.service.common.AppImportanceFilter.WrongCallingApplicationStateException;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AppImportanceFilterTest {
    private static final int API_CLASS = AD_SERVICES_API_CALLED__API_CLASS__TARGETING;
    private static final int API_NAME = AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS;
    private static final int APP_UID = 321;
    private static final String APP_PACKAGE_NAME = "test.package.name";
    private static final String SDK_NAME = "sdk.name";
    private static final String PROCESS_NAME = "process_name";

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
        when(mActivityManager.getRunningAppProcesses())
                .thenReturn(
                        Collections.singletonList(
                                generateProcessInfo(
                                        APP_PACKAGE_NAME, IMPORTANCE_FOREGROUND, APP_UID)));

        // No exception is thrown
        mAppImportanceFilter.assertCallerIsInForeground(APP_PACKAGE_NAME, API_NAME, SDK_NAME);

        verifyZeroInteractions(mAdServiceLogger, mPackageManager);
    }

    @Test
    public void testPackageInMultipleProcesses_succeed() {
        when(mActivityManager.getRunningAppProcesses())
                .thenReturn(
                        Arrays.asList(
                                generateProcessInfo(
                                        APP_PACKAGE_NAME, IMPORTANCE_FOREGROUND, APP_UID),
                                generateProcessInfo(APP_PACKAGE_NAME, IMPORTANCE_GONE, APP_UID)));

        // No exception is thrown
        mAppImportanceFilter.assertCallerIsInForeground(APP_PACKAGE_NAME, API_NAME, SDK_NAME);

        verifyZeroInteractions(mAdServiceLogger, mPackageManager);
    }

    @Test
    public void testCalledWithForegroundServiceImportanceAppPackageName_succeed() {
        when(mActivityManager.getRunningAppProcesses())
                .thenReturn(
                        Collections.singletonList(
                                generateProcessInfo(
                                        APP_PACKAGE_NAME, IMPORTANCE_FOREGROUND_SERVICE, APP_UID)));

        // No exception is thrown
        mAppImportanceFilter.assertCallerIsInForeground(APP_PACKAGE_NAME, API_NAME, SDK_NAME);

        verifyZeroInteractions(mAdServiceLogger, mPackageManager);
    }

    @Test
    public void
            testCalledWithLessThanForegroundImportanceAppPackageName_throwsIllegalStateException() {
        when(mActivityManager.getRunningAppProcesses())
                .thenReturn(
                        Collections.singletonList(
                                generateProcessInfo(
                                        APP_PACKAGE_NAME, IMPORTANCE_VISIBLE, APP_UID)));

        assertThrows(
                WrongCallingApplicationStateException.class,
                () ->
                        mAppImportanceFilter.assertCallerIsInForeground(
                                APP_PACKAGE_NAME, API_NAME, SDK_NAME));

        verifyZeroInteractions(mPackageManager);
    }

    @Test
    public void testCalledWithLessThanForegroundImportanceAppPackageName_logsFailure() {
        when(mActivityManager.getRunningAppProcesses())
                .thenReturn(
                        Collections.singletonList(
                                generateProcessInfo(
                                        APP_PACKAGE_NAME, IMPORTANCE_VISIBLE, APP_UID)));

        assertThrows(
                WrongCallingApplicationStateException.class,
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
    public void testLessThanForegroundImportanceAppPackageNameMultiplePackages_logsFailure() {
        String otherAppPackageName = "other.app.package";
        when(mActivityManager.getRunningAppProcesses())
                .thenReturn(
                        Collections.singletonList(
                                generateProcessInfo(
                                        Arrays.asList(APP_PACKAGE_NAME, otherAppPackageName),
                                        IMPORTANCE_VISIBLE,
                                        APP_UID)));

        assertThrows(
                WrongCallingApplicationStateException.class,
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

        verifyZeroInteractions(mPackageManager);
    }

    @Test
    public void testCalledWithForegroundAppUid_succeed() {
        when(mActivityManager.getRunningAppProcesses())
                .thenReturn(
                        Collections.singletonList(
                                generateProcessInfo(
                                        APP_PACKAGE_NAME, IMPORTANCE_FOREGROUND, APP_UID)));

        // No exception is thrown
        mAppImportanceFilter.assertCallerIsInForeground(APP_UID, API_NAME, SDK_NAME);

        verifyZeroInteractions(mAdServiceLogger);
    }

    @Test
    public void testCalledWithAppUidWithOneAppInForeground_succeed() {
        String otherAppPackageName = "other.app.package";
        int otherAppUid = APP_UID + 1;
        when(mActivityManager.getRunningAppProcesses())
                .thenReturn(
                        Arrays.asList(
                                generateProcessInfo(
                                        APP_PACKAGE_NAME, IMPORTANCE_FOREGROUND_SERVICE, APP_UID),
                                generateProcessInfo(
                                        otherAppPackageName, IMPORTANCE_VISIBLE, otherAppUid)));

        // No exception is thrown
        mAppImportanceFilter.assertCallerIsInForeground(APP_UID, API_NAME, SDK_NAME);

        verifyZeroInteractions(mAdServiceLogger);
    }

    @Test
    public void testCalledWithForegroundServiceImportanceAppUid_succeed() {
        when(mActivityManager.getRunningAppProcesses())
                .thenReturn(
                        Collections.singletonList(
                                generateProcessInfo(
                                        APP_PACKAGE_NAME, IMPORTANCE_FOREGROUND_SERVICE, APP_UID)));

        // No exception is thrown
        mAppImportanceFilter.assertCallerIsInForeground(APP_UID, API_NAME, SDK_NAME);

        verifyZeroInteractions(mAdServiceLogger);
    }

    @Test
    public void testCalledWithLessThanForegroundImportanceAppUid_throwsIllegalStateException() {
        when(mActivityManager.getRunningAppProcesses())
                .thenReturn(
                        Collections.singletonList(
                                generateProcessInfo(
                                        APP_PACKAGE_NAME, IMPORTANCE_VISIBLE, APP_UID)));

        assertThrows(
                WrongCallingApplicationStateException.class,
                () -> mAppImportanceFilter.assertCallerIsInForeground(APP_UID, API_NAME, SDK_NAME));
    }

    @Test
    public void testLessThanForegroundImportanceMultipleProcesses_throwsIllegalStateException() {
        String otherAppPackageName = "other.app.package";
        int otherAppUid = APP_UID + 1;
        when(mActivityManager.getRunningAppProcesses())
                .thenReturn(
                        Arrays.asList(
                                generateProcessInfo(APP_PACKAGE_NAME, IMPORTANCE_VISIBLE, APP_UID),
                                generateProcessInfo(
                                        otherAppPackageName, IMPORTANCE_FOREGROUND, otherAppUid)));

        assertThrows(
                WrongCallingApplicationStateException.class,
                () -> mAppImportanceFilter.assertCallerIsInForeground(APP_UID, API_NAME, SDK_NAME));
    }

    @Test
    public void testCalledWithLessThanForegroundImportanceAppUid_logsFailure() {
        when(mActivityManager.getRunningAppProcesses())
                .thenReturn(
                        Collections.singletonList(
                                generateProcessInfo(
                                        APP_PACKAGE_NAME, IMPORTANCE_VISIBLE, APP_UID)));

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
        when(mActivityManager.getRunningAppProcesses())
                .thenReturn(
                        Collections.singletonList(
                                generateProcessInfo(
                                        APP_PACKAGE_NAME, IMPORTANCE_VISIBLE, APP_UID)));

        assertThrows(
                WrongCallingApplicationStateException.class,
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
        int otherAppUid = APP_UID + 1;
        when(mActivityManager.getRunningAppProcesses())
                .thenReturn(
                        Arrays.asList(
                                generateProcessInfo(APP_PACKAGE_NAME, IMPORTANCE_VISIBLE, APP_UID),
                                generateProcessInfo(
                                        otherAppPackageName, IMPORTANCE_VISIBLE, otherAppUid)));

        assertThrows(
                IllegalStateException.class,
                () -> mAppImportanceFilter.assertCallerIsInForeground(APP_UID, API_NAME, SDK_NAME));
    }

    @Test
    public void testCalledWithMultipleLessThanForegroundImportanceAppUid_logsFailure() {
        String otherAppPackageName = "other.app.package";
        int otherAppUid = APP_UID + 1;
        when(mActivityManager.getRunningAppProcesses())
                .thenReturn(
                        Arrays.asList(
                                generateProcessInfo(APP_PACKAGE_NAME, IMPORTANCE_VISIBLE, APP_UID),
                                generateProcessInfo(
                                        otherAppPackageName, IMPORTANCE_VISIBLE, otherAppUid)));

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
    public void testFailureTryingToRetrievePackageImportanceFromUid_throwsIllegalStateException() {
        when(mActivityManager.getRunningAppProcesses())
                .thenThrow(new SecurityException("Simulating failure calling activity manager"));

        assertThrows(
                IllegalStateException.class,
                () -> mAppImportanceFilter.assertCallerIsInForeground(APP_UID, API_NAME, SDK_NAME));
    }

    @Test
    public void testFailureTryingToRetrievePackageImportanceFromUid_logsFailure() {
        when(mActivityManager.getRunningAppProcesses())
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
                .hasAppPackageName(UNKNOWN_APP_PACKAGE_NAME);
    }

    @Test
    public void testFailureFindingProcess_throwsIllegalStateException() {
        when(mActivityManager.getRunningAppProcesses()).thenReturn(Collections.EMPTY_LIST);

        assertThrows(
                IllegalStateException.class,
                () -> mAppImportanceFilter.assertCallerIsInForeground(APP_UID, API_NAME, SDK_NAME));
    }

    @Test
    public void testFailureFindingProcess_logsFailure() {
        when(mActivityManager.getRunningAppProcesses()).thenReturn(Collections.EMPTY_LIST);

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
    public void testFailureFindingPackage_logsFailure() {
        when(mActivityManager.getRunningAppProcesses()).thenReturn(Collections.EMPTY_LIST);

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
    }

    /** Helper to generate a process info object */
    private static ActivityManager.RunningAppProcessInfo generateProcessInfo(
            String packageName, int importance, int uid) {
        return generateProcessInfo(Collections.singletonList(packageName), importance, uid);
    }

    private static ActivityManager.RunningAppProcessInfo generateProcessInfo(
            List<String> packageNames, int importance, int uid) {
        ActivityManager.RunningAppProcessInfo process =
                new ActivityManager.RunningAppProcessInfo(
                        PROCESS_NAME, 100, packageNames.toArray(new String[0]));
        process.importance = importance;
        process.uid = uid;
        return process;
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
