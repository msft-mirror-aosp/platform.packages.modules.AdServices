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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Build;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.Flags;
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
public class AdServicesBackCompatInitTest extends AdServicesExtendedMockitoTestCase {
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

    @Mock private Flags mMockFlags;
    @Mock private PackageManager mPackageManager;
    @Mock private JobScheduler mJobScheduler;

    private AdServicesBackCompatInit mSpyCompatInit;

    @Before
    public void setup() {
        extendedMockito.mockGetFlags(mMockFlags);
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
        extendedMockito.mockIsAtLeastT(true);
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
        extendedMockito.mockIsAtLeastT(true);
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
        extendedMockito.mockIsAtLeastT(false);
        mockAdServicesFlags(false);
        mockPackageName(TEST_PACKAGE_NAME);

        mSpyCompatInit.initializeComponents();

        verifyZeroInteractions(mPackageManager, mJobScheduler);
    }

    @Test
    public void testInitializeComponents_withSFlagOn_enablesComponents() {
        extendedMockito.mockIsAtLeastT(false);
        doReturn(Build.VERSION_CODES.S).when(AdServicesBackCompatInit::getSdkLevelInt);
        mockAdServicesFlags(true);
        mockPackageName(TEST_PACKAGE_NAME);

        mSpyCompatInit.initializeComponents();

        verifyEnablingComponents(SUPPORTED_SERVICES_ON_SPLUS);
        verifyNoMoreInteractions(mPackageManager, mJobScheduler);
    }

    @Test
    public void testInitializeComponents_withRFlagOn_enablesComponents() {
        extendedMockito.mockIsAtLeastT(false);
        doReturn(Build.VERSION_CODES.R).when(AdServicesBackCompatInit::getSdkLevelInt);
        mockAdServicesFlags(true);
        mockPackageName(TEST_PACKAGE_NAME);

        mSpyCompatInit.initializeComponents();

        verifyEnablingComponents(SUPPORTED_SERVICES_ON_R);
        verifyNoMoreInteractions(mPackageManager, mJobScheduler);
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
