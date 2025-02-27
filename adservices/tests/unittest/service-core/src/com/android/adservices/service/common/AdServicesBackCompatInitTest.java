/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__BACK_COMPAT_INIT_CANCEL_JOB_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__BACK_COMPAT_INIT_DISABLE_RECEIVER_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__BACK_COMPAT_INIT_ENABLE_RECEIVER_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__BACK_COMPAT_INIT_UPDATE_ACTIVITY_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__BACK_COMPAT_INIT_UPDATE_SERVICE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__JOB_SCHEDULER_IS_UNAVAILABLE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Build;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.List;

@SpyStatic(FlagsFactory.class)
@SpyStatic(SdkLevel.class)
@SpyStatic(PackageChangedReceiver.class)
@SpyStatic(AdServicesBackCompatInit.class)
@SetErrorLogUtilDefaultParams(
        throwable = Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON)
public final class AdServicesBackCompatInitTest extends AdServicesExtendedMockitoTestCase {
    private static final String TEST_PACKAGE_NAME = "test";
    private static final String AD_SERVICES_APK_PKG_SUFFIX = "android.adservices.api";
    private static final int AD_SERVICES_ATTRIBUTION_JOB_ID = 1;
    private static final int EXT_SERVICES_APP_SEARCH_JOB_SERVICE_ID = 2;
    private static final int AD_SERVICES_EPOCH_JOB_SERVICE_ID = 3;

    private static final ImmutableList<String> SUPPORTED_SERVICES_ON_R =
            ImmutableList.of(
                    "com.android.adservices.adid.AdIdService",
                    "com.android.adservices.measurement.MeasurementService",
                    "com.android.adservices.common.AdServicesCommonService");

    private static final ImmutableList<String> SUPPORTED_SERVICES_ON_SPLUS =
            ImmutableList.of(
                    "com.android.adservices.adid.AdIdService",
                    "com.android.adservices.measurement.MeasurementService",
                    "com.android.adservices.common.AdServicesCommonService",
                    "com.android.adservices.adselection.AdSelectionService",
                    "com.android.adservices.customaudience.CustomAudienceService",
                    "android.adservices.signals.ProtectedSignalsService",
                    "com.android.adservices.topics.TopicsService",
                    "com.android.adservices.appsetid.AppSetIdService");

    @Mock private PackageManager mPackageManager;
    @Mock private JobScheduler mJobScheduler;

    private AdServicesBackCompatInit mSpyCompatInit;

    @Before
    public void setup() {
        mocker.mockGetFlags(mMockFlags);
        appContext.set(mMockContext);
        mSpyCompatInit = spy(AdServicesBackCompatInit.getInstance());
    }

    @Test
    public void testInitializeComponents_withNullPkg_doesNothing() {
        mockPackageName(null);

        mSpyCompatInit.initializeComponents();

        verifyZeroInteractions(mMockFlags, mPackageManager, mJobScheduler);
    }

    @Test
    public void testInitializeComponents_withAdServicesPkg_doesNothing() {
        mockPackageName(AD_SERVICES_APK_PKG_SUFFIX);

        mSpyCompatInit.initializeComponents();

        verifyZeroInteractions(mMockFlags, mPackageManager, mJobScheduler);
    }

    @Test
    public void testInitializeComponents_withTPlusFlagsOff_disablesComponentsCancelsJobs() {
        mocker.mockIsAtLeastT(true);
        doReturn(Build.VERSION_CODES.TIRAMISU).when(AdServicesBackCompatInit::getSdkLevelInt);
        mockAdServicesFlags(false);
        mockPackageName(TEST_PACKAGE_NAME);
        when(mMockContext.getSystemService(JobScheduler.class)).thenReturn(mJobScheduler);
        when(mJobScheduler.getAllPendingJobs()).thenReturn(getJobInfos());
        doNothing().when(mJobScheduler).cancel(anyInt());

        mSpyCompatInit.initializeComponents();

        verifyDisablingComponents();
        verifyNoMoreInteractions(mPackageManager, mJobScheduler);
    }

    @Test
    public void testInitializeComponents_withTPlusFlagsOn_disablesComponentsCancelsJobs() {
        mocker.mockIsAtLeastT(true);
        doReturn(Build.VERSION_CODES.TIRAMISU).when(AdServicesBackCompatInit::getSdkLevelInt);
        mockAdServicesFlags(true);
        mockPackageName(TEST_PACKAGE_NAME);
        when(mMockContext.getSystemService(JobScheduler.class)).thenReturn(mJobScheduler);
        when(mJobScheduler.getAllPendingJobs()).thenReturn(getJobInfos());
        doNothing().when(mJobScheduler).cancel(anyInt());

        mSpyCompatInit.initializeComponents();

        verifyDisablingComponents();
        verifyNoMoreInteractions(mPackageManager, mJobScheduler);
    }

    @Test
    public void testInitializeComponents_withSMinusFlagOff_doesNothing() {
        mocker.mockIsAtLeastT(false);
        mockAdServicesFlags(false);
        mockPackageName(TEST_PACKAGE_NAME);

        mSpyCompatInit.initializeComponents();

        verifyZeroInteractions(mPackageManager, mJobScheduler);
    }

    @Test
    public void testInitializeComponents_withSFlagOn_enablesComponents() {
        mocker.mockIsAtLeastT(false);
        doReturn(Build.VERSION_CODES.S).when(AdServicesBackCompatInit::getSdkLevelInt);
        mockAdServicesFlags(true);
        mockPackageName(TEST_PACKAGE_NAME);

        mSpyCompatInit.initializeComponents();

        verifyEnablingComponents(SUPPORTED_SERVICES_ON_SPLUS);
        verifyNoMoreInteractions(mPackageManager, mJobScheduler);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__BACK_COMPAT_INIT_CANCEL_JOB_FAILURE)
    public void testInitializeComponents_disableScheduledBackgroundJobsException_celLogged() {
        // Mock NullPointerException when getSystemService is called before the system is ready
        when(mMockContext.getSystemService(JobScheduler.class))
                .thenThrow(NullPointerException.class);
        mockAdServicesFlags(true);
        mocker.mockIsAtLeastT(true);
        mockPackageName(TEST_PACKAGE_NAME);

        // No exception expected, so no need to explicitly handle any exceptions here
        mSpyCompatInit.initializeComponents();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__JOB_SCHEDULER_IS_UNAVAILABLE)
    public void testInitializeComponents_jobSchedulerIsNull_celLogged() {
        when(mMockContext.getSystemService(JobScheduler.class)).thenReturn(null);
        mocker.mockIsAtLeastT(true);
        mockAdServicesFlags(true);
        mockPackageName(TEST_PACKAGE_NAME);

        // No exception expected, so no need to explicitly handle any exceptions here
        mSpyCompatInit.initializeComponents();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__BACK_COMPAT_INIT_UPDATE_SERVICE_FAILURE)
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__BACK_COMPAT_INIT_UPDATE_ACTIVITY_FAILURE)
    public void testInitializeComponents_updateComponentsThrowsException_celLogged() {
        doThrow(IllegalArgumentException.class)
                .when(mSpyCompatInit)
                .updateComponents(anyList(), anyBoolean());
        mocker.mockIsAtLeastT(false);
        mockAdServicesFlags(true);
        mockPackageName(TEST_PACKAGE_NAME);

        // No exception expected, so no need to explicitly handle any exceptions here
        mSpyCompatInit.initializeComponents();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__BACK_COMPAT_INIT_DISABLE_RECEIVER_FAILURE)
    public void testInitializeComponents_disableReceiverFailure_celLogged() {
        doReturn(false).when(() -> PackageChangedReceiver.disableReceiver(any(), any()));
        mocker.mockIsAtLeastT(true);
        mockAdServicesFlags(true);
        mockPackageName(TEST_PACKAGE_NAME);
        when(mMockContext.getSystemService(JobScheduler.class)).thenReturn(mJobScheduler);
        when(mJobScheduler.getAllPendingJobs()).thenReturn(ImmutableList.of());

        // No exception expected, so no need to explicitly handle any exceptions here
        mSpyCompatInit.initializeComponents();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__BACK_COMPAT_INIT_ENABLE_RECEIVER_FAILURE)
    public void testInitializeComponents_enableReceiverFailure_celLogged() {
        doReturn(false).when(() -> PackageChangedReceiver.enableReceiver(any(), any()));
        mocker.mockIsAtLeastT(false);
        mockAdServicesFlags(true);
        mockPackageName(TEST_PACKAGE_NAME);

        // No exception expected, so no need to explicitly handle any exceptions here
        mSpyCompatInit.initializeComponents();
    }

    private void verifyDisablingComponents() {
        // PackageChangedReceiver should be disabled
        verifyPackageChangedReceiverEnabledSettingCall(
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        // Ensure activities are disabled
        verifyCallToSetComponentEnabledSetting(
                PackageManagerCompatUtils.CONSENT_ACTIVITIES_CLASSES,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        // Ensure services are disabled
        verifyCallToSetComponentEnabledSetting(
                SUPPORTED_SERVICES_ON_SPLUS, PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        // Ensure AdServices jobs are cancelled
        verifyAdServicesJobsCancelled();
    }

    private void verifyEnablingComponents(List<String> serviceClassesToEnable) {
        // PackageChangedReceiver should be enabled
        verifyPackageChangedReceiverEnabledSettingCall(
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        // Ensure activities are enabled
        verifyCallToSetComponentEnabledSetting(
                PackageManagerCompatUtils.CONSENT_ACTIVITIES_CLASSES,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        // Ensure services are enabled
        verifyCallToSetComponentEnabledSetting(
                serviceClassesToEnable, PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
    }

    private void verifyAdServicesJobsCancelled() {
        verify(mJobScheduler).getAllPendingJobs();
        verify(mJobScheduler).cancel(AD_SERVICES_ATTRIBUTION_JOB_ID);
        verify(mJobScheduler).cancel(AD_SERVICES_EPOCH_JOB_SERVICE_ID);
        verify(mJobScheduler, never()).cancel(EXT_SERVICES_APP_SEARCH_JOB_SERVICE_ID);
        verify(mJobScheduler, never()).cancelAll();
    }

    private void mockAdServicesFlags(boolean isEnabled) {
        doReturn(isEnabled).when(mMockFlags).getEnableBackCompat();
        doReturn(isEnabled).when(mMockFlags).getAdServicesEnabled();
        doReturn(!isEnabled).when(mMockFlags).getGlobalKillSwitch();
    }

    private void mockPackageName(String packageName) {
        when(mMockContext.getPackageManager()).thenReturn(mPackageManager);
        when(mMockContext.getPackageName()).thenReturn(packageName);
    }

    private void verifyCallToSetComponentEnabledSetting(List<String> components, int state) {
        for (String component : components) {
            verify(mPackageManager)
                    .setComponentEnabledSetting(
                            new ComponentName(TEST_PACKAGE_NAME, component),
                            state,
                            PackageManager.DONT_KILL_APP);
        }
    }

    private void verifyPackageChangedReceiverEnabledSettingCall(int state) {
        verify(mPackageManager)
                .setComponentEnabledSetting(
                        new ComponentName(mMockContext, PackageChangedReceiver.class),
                        state,
                        PackageManager.DONT_KILL_APP);
    }

    private static List<JobInfo> getJobInfos() {
        return List.of(
                new JobInfo.Builder(
                                AD_SERVICES_ATTRIBUTION_JOB_ID,
                                new ComponentName(
                                        TEST_PACKAGE_NAME,
                                        "com.android.adservices.service.measurement.attribution"
                                                + ".AttributionJobService"))
                        .build(),
                new JobInfo.Builder(
                                EXT_SERVICES_APP_SEARCH_JOB_SERVICE_ID,
                                new ComponentName(
                                        TEST_PACKAGE_NAME,
                                        "com.android.extservice.common"
                                                + ".AdServicesAppsearchDeleteSchedulerJobService"))
                        .build(),
                new JobInfo.Builder(
                                AD_SERVICES_EPOCH_JOB_SERVICE_ID,
                                new ComponentName(
                                        TEST_PACKAGE_NAME,
                                        "com.android.adservices.service.topics"
                                                + ".EpochJobService"))
                        .build());
    }
}
