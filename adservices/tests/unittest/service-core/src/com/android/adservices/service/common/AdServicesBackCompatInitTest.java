/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.List;

@SmallTest
public class AdServicesBackCompatInitTest {
    private static final String TEST_PACKAGE_NAME = "test";
    private static final String AD_SERVICES_APK_PKG_SUFFIX = "android.adservices.api";
    private static final String EXT_SERVICES_APK_PKG_SUFFIX = "android.ext.services";
    private static final int NUM_ACTIVITIES = 7;
    private static final int NUM_SERVICE_CLASSES = 8;
    private static final int NUM_SERVICE_CLASSES_TO_ENABLE_ON_R = 3;

    @Mock Flags mMockFlags;
    @Mock Context mContext;
    @Mock PackageManager mPackageManager;
    MockitoSession mSession;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);

        // Start a mockitoSession to mock static method
        mSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(PackageChangedReceiver.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();

        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        when(FlagsFactory.getFlags()).thenReturn(mMockFlags);
    }

    @After
    public void teardown() {
        mSession.finishMocking();
    }

    @Test
    public void testOnReceive_tPlus_flagOff() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        AdServicesBackCompatInit adServicesBackCompatInit =
                Mockito.spy(new AdServicesBackCompatInit(mContext));
        doReturn(true).when(mMockFlags).getGlobalKillSwitch();

        adServicesBackCompatInit.initializeComponents();

        verify(adServicesBackCompatInit, never()).registerPackagedChangedBroadcastReceivers();
        verify(adServicesBackCompatInit, never()).updateAdExtServicesServices(eq(true));
        verify(adServicesBackCompatInit, atLeastOnce()).updateAdExtServicesActivities(eq(false));
        verify(adServicesBackCompatInit, atLeastOnce()).updateAdExtServicesServices(eq(false));
        verify(adServicesBackCompatInit, atLeastOnce())
                .unregisterPackageChangedBroadcastReceivers();
        verify(adServicesBackCompatInit, atLeastOnce()).disableScheduledBackgroundJobs();
    }

    @Test
    public void testOnReceive_tPlus_flagOn() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        AdServicesBackCompatInit adServicesBackCompatInit =
                Mockito.spy(new AdServicesBackCompatInit(mContext));
        doReturn(true).when(mMockFlags).getEnableBackCompat();
        doReturn(true).when(mMockFlags).getAdServicesEnabled();
        doReturn(false).when(mMockFlags).getGlobalKillSwitch();

        adServicesBackCompatInit.initializeComponents();
        verify(adServicesBackCompatInit, never()).registerPackagedChangedBroadcastReceivers();
        verify(adServicesBackCompatInit, never()).updateAdExtServicesServices(eq(true));
        verify(adServicesBackCompatInit, atLeastOnce()).updateAdExtServicesActivities(eq(false));
        verify(adServicesBackCompatInit, atLeastOnce()).updateAdExtServicesServices(eq(false));
        verify(adServicesBackCompatInit, atLeastOnce())
                .unregisterPackageChangedBroadcastReceivers();
        verify(adServicesBackCompatInit, atLeastOnce()).disableScheduledBackgroundJobs();
    }

    @Test
    public void testOnReceive_sminus_flagsOff() {
        Assume.assumeFalse(SdkLevel.isAtLeastT());
        AdServicesBackCompatInit adServicesBackCompatInit =
                Mockito.spy(new AdServicesBackCompatInit(mContext));
        doReturn(false).when(mMockFlags).getEnableBackCompat();

        adServicesBackCompatInit.initializeComponents();
        verify(adServicesBackCompatInit, never()).registerPackagedChangedBroadcastReceivers();
        verify(adServicesBackCompatInit, never()).updateAdExtServicesActivities(eq(true));
        verify(adServicesBackCompatInit, never()).updateAdExtServicesServices(eq(true));

        doReturn(true).when(mMockFlags).getEnableBackCompat();
        doReturn(false).when(mMockFlags).getAdServicesEnabled();

        adServicesBackCompatInit.initializeComponents();
        verify(adServicesBackCompatInit, never()).registerPackagedChangedBroadcastReceivers();
        verify(adServicesBackCompatInit, never()).updateAdExtServicesActivities(eq(true));
        verify(adServicesBackCompatInit, never()).updateAdExtServicesServices(eq(true));

        doReturn(true).when(mMockFlags).getEnableBackCompat();
        doReturn(true).when(mMockFlags).getAdServicesEnabled();
        doReturn(true).when(mMockFlags).getGlobalKillSwitch();

        adServicesBackCompatInit.initializeComponents();
        verify(adServicesBackCompatInit, never()).registerPackagedChangedBroadcastReceivers();
        verify(adServicesBackCompatInit, never()).updateAdExtServicesActivities(eq(true));
        verify(adServicesBackCompatInit, never()).updateAdExtServicesServices(eq(true));
        verify(adServicesBackCompatInit, never()).disableScheduledBackgroundJobs();
    }

    @Test
    public void testOnReceive_sminus_flagsOn() {
        Assume.assumeFalse(SdkLevel.isAtLeastT());
        AdServicesBackCompatInit adServicesBackCompatInit =
                Mockito.spy(new AdServicesBackCompatInit(mContext));
        doReturn(true).when(mMockFlags).getEnableBackCompat();
        doReturn(true).when(mMockFlags).getAdServicesEnabled();
        doReturn(false).when(mMockFlags).getGlobalKillSwitch();

        adServicesBackCompatInit.initializeComponents();
        verify(adServicesBackCompatInit).registerPackagedChangedBroadcastReceivers();
        verify(adServicesBackCompatInit).updateAdExtServicesActivities(eq(true));
        verify(adServicesBackCompatInit).updateAdExtServicesServices(eq(true));
        verify(adServicesBackCompatInit, never()).disableScheduledBackgroundJobs();
    }

    @Test
    public void testRegisterReceivers_extServicesPackage_succeed() {
        Assume.assumeFalse(SdkLevel.isAtLeastT());
        AdServicesBackCompatInit adServicesBackCompatInit =
                (new AdServicesBackCompatInit(mContext));
        setCommonMocks(EXT_SERVICES_APK_PKG_SUFFIX);
        adServicesBackCompatInit.registerPackagedChangedBroadcastReceivers();
        verify(() -> PackageChangedReceiver.enableReceiver(any(Context.class), any(Flags.class)));
    }

    @Test
    public void testRegisterReceivers_adServicesPackage_skipped() {
        Assume.assumeFalse(SdkLevel.isAtLeastT());
        AdServicesBackCompatInit adServicesBackCompatInit =
                (new AdServicesBackCompatInit(mContext));
        setCommonMocks(AD_SERVICES_APK_PKG_SUFFIX);
        adServicesBackCompatInit.registerPackagedChangedBroadcastReceivers();
        verify(
                () -> PackageChangedReceiver.enableReceiver(any(Context.class), any(Flags.class)),
                never());
    }

    @Test
    public void testUnregisterReceivers_extServicesPackage_succeed() {
        doReturn(true).when(() -> PackageChangedReceiver.disableReceiver(any(), any()));

        AdServicesBackCompatInit adServicesBackCompatInit =
                (new AdServicesBackCompatInit(mContext));
        setCommonMocks(EXT_SERVICES_APK_PKG_SUFFIX);
        adServicesBackCompatInit.unregisterPackageChangedBroadcastReceivers();

        verify(() -> PackageChangedReceiver.disableReceiver(any(Context.class), any(Flags.class)));
    }

    @Test
    public void testUnregisterReceivers_adServicesPackage_skipped() {
        doReturn(true).when(() -> PackageChangedReceiver.disableReceiver(any(), any()));

        AdServicesBackCompatInit adServicesBackCompatInit =
                (new AdServicesBackCompatInit(mContext));
        setCommonMocks(AD_SERVICES_APK_PKG_SUFFIX);
        adServicesBackCompatInit.unregisterPackageChangedBroadcastReceivers();

        verify(
                () -> PackageChangedReceiver.disableReceiver(any(Context.class), any(Flags.class)),
                never());
    }

    @Test
    public void testEnableActivities_sminus() {
        Assume.assumeFalse(SdkLevel.isAtLeastT());
        AdServicesBackCompatInit adServicesBackCompatInit =
                Mockito.spy(new AdServicesBackCompatInit(mContext));

        setCommonMocks(TEST_PACKAGE_NAME);

        // Call the method we're testing.
        adServicesBackCompatInit.updateAdExtServicesActivities(true);

        verify(mPackageManager, times(NUM_ACTIVITIES))
                .setComponentEnabledSetting(
                        any(ComponentName.class),
                        eq(PackageManager.COMPONENT_ENABLED_STATE_ENABLED),
                        eq(PackageManager.DONT_KILL_APP));
    }

    @Test
    public void testDisableActivities_tPlus() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        AdServicesBackCompatInit adServicesBackCompatInit =
                Mockito.spy(new AdServicesBackCompatInit(mContext));
        setCommonMocks(TEST_PACKAGE_NAME);

        // Call the method we're testing.
        adServicesBackCompatInit.updateAdExtServicesActivities(false);

        verify(mPackageManager, times(NUM_ACTIVITIES))
                .setComponentEnabledSetting(
                        any(ComponentName.class),
                        eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED),
                        eq(PackageManager.DONT_KILL_APP));
    }

    @Test
    public void testDisableAdExtServicesServices_tPlus() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        AdServicesBackCompatInit adServicesBackCompatInit =
                Mockito.spy(new AdServicesBackCompatInit(mContext));
        setCommonMocks(TEST_PACKAGE_NAME);

        // Call the method we're testing.
        adServicesBackCompatInit.updateAdExtServicesServices(/* shouldEnable= */ false);

        verify(mPackageManager, times(NUM_SERVICE_CLASSES))
                .setComponentEnabledSetting(
                        any(ComponentName.class),
                        eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED),
                        eq(PackageManager.DONT_KILL_APP));
    }

    @Test
    public void testEnableAdExtServicesServices_onS() {
        Assume.assumeTrue(SdkLevel.isAtLeastS() && !SdkLevel.isAtLeastT());
        AdServicesBackCompatInit adServicesBackCompatInit =
                Mockito.spy(new AdServicesBackCompatInit(mContext));
        setCommonMocks(TEST_PACKAGE_NAME);

        // Call the method we're testing
        adServicesBackCompatInit.updateAdExtServicesServices(/* shouldEnable= */ true);

        verify(mPackageManager, times(NUM_SERVICE_CLASSES))
                .setComponentEnabledSetting(
                        any(ComponentName.class),
                        eq(PackageManager.COMPONENT_ENABLED_STATE_ENABLED),
                        eq(PackageManager.DONT_KILL_APP));
    }

    @Test
    public void testEnableAdExtServicesServices_onR() {
        Assume.assumeFalse(SdkLevel.isAtLeastS());
        AdServicesBackCompatInit adServicesBackCompatInit =
                Mockito.spy(new AdServicesBackCompatInit(mContext));
        setCommonMocks(TEST_PACKAGE_NAME);

        // Call the method we're testing
        adServicesBackCompatInit.updateAdExtServicesServices(/* shouldEnable= */ true);

        verify(mPackageManager, times(NUM_SERVICE_CLASSES_TO_ENABLE_ON_R))
                .setComponentEnabledSetting(
                        any(ComponentName.class),
                        eq(PackageManager.COMPONENT_ENABLED_STATE_ENABLED),
                        eq(PackageManager.DONT_KILL_APP));
    }

    @Test
    public void testDisableAdExtServicesServices_onSMinus() {
        Assume.assumeFalse(SdkLevel.isAtLeastT());
        AdServicesBackCompatInit adServicesBackCompatInit =
                Mockito.spy(new AdServicesBackCompatInit(mContext));
        setCommonMocks(TEST_PACKAGE_NAME);

        // Call the method we're testing
        adServicesBackCompatInit.updateAdExtServicesServices(/* shouldEnable= */ false);

        verify(mPackageManager, times(NUM_SERVICE_CLASSES))
                .setComponentEnabledSetting(
                        any(ComponentName.class),
                        eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED),
                        eq(PackageManager.DONT_KILL_APP));
    }

    @Test
    public void testUpdateAdExtServicesActivities_withAdServicesPackageSuffix_doesNotUpdate() {
        AdServicesBackCompatInit adServicesBackCompatInit =
                Mockito.spy(new AdServicesBackCompatInit(mContext));
        setCommonMocks(AD_SERVICES_APK_PKG_SUFFIX);

        // Call the method we're testing.
        adServicesBackCompatInit.updateAdExtServicesActivities(false);

        verify(mPackageManager, never())
                .setComponentEnabledSetting(
                        any(ComponentName.class),
                        eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED),
                        eq(PackageManager.DONT_KILL_APP));
    }

    @Test
    public void testDisableAdExtServicesServices_withAdServicesPackageSuffix_doesNotUpdate() {
        AdServicesBackCompatInit adServicesBackCompatInit =
                Mockito.spy(new AdServicesBackCompatInit(mContext));
        setCommonMocks(AD_SERVICES_APK_PKG_SUFFIX);

        // Call the method we're testing.
        adServicesBackCompatInit.updateAdExtServicesServices(/* shouldEnable= */ false);

        verify(mPackageManager, never())
                .setComponentEnabledSetting(
                        any(ComponentName.class),
                        eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED),
                        eq(PackageManager.DONT_KILL_APP));
    }

    @Test
    public void testUpdateComponents_withAdServicesPackageSuffix_throwsException() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        AdServicesBackCompatInit.updateComponents(
                                mContext, ImmutableList.of(), AD_SERVICES_APK_PKG_SUFFIX, false));
    }

    @Test
    public void testUpdateComponents_adServicesPackageNamePresentButNotSuffix_disablesComponent() {
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        AdServicesBackCompatInit.updateComponents(
                mContext,
                ImmutableList.of("test"),
                AD_SERVICES_APK_PKG_SUFFIX + TEST_PACKAGE_NAME,
                false);

        verify(mPackageManager)
                .setComponentEnabledSetting(
                        any(ComponentName.class),
                        eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED),
                        eq(PackageManager.DONT_KILL_APP));
    }

    @Test
    public void testDisableScheduledBackgroundJobs_contextNull() {
        assertThrows(
                NullPointerException.class,
                () -> (new AdServicesBackCompatInit(null)).disableScheduledBackgroundJobs());
    }

    @Test
    public void testDisableScheduledBackgroundJobs_withAdServicesPackageSuffix_doesNotUpdate() {
        AdServicesBackCompatInit adServicesBackCompatInit =
                Mockito.spy(new AdServicesBackCompatInit(mContext));
        setCommonMocks(AD_SERVICES_APK_PKG_SUFFIX);

        adServicesBackCompatInit.disableScheduledBackgroundJobs();
        verify(mContext, never()).getSystemService(eq(JobScheduler.class));
    }

    @Test
    public void
            testDisableScheduledBackgroundJobs_adServicesPackagePresentButNotSuffix_cancelsAllJobs() {
        AdServicesBackCompatInit adServicesBackCompatInit =
                Mockito.spy(new AdServicesBackCompatInit(mContext));

        JobScheduler mockScheduler = Mockito.mock(JobScheduler.class);
        when(mContext.getSystemService(JobScheduler.class)).thenReturn(mockScheduler);
        when(mockScheduler.getAllPendingJobs()).thenReturn(getJobInfos());
        doNothing().when(mockScheduler).cancel(anyInt());
        setCommonMocks(AD_SERVICES_APK_PKG_SUFFIX + TEST_PACKAGE_NAME);

        adServicesBackCompatInit.disableScheduledBackgroundJobs();
        verify(mockScheduler).cancel(1);
        verify(mockScheduler).cancel(3);
        verify(mockScheduler, never()).cancel(2);
        verify(mockScheduler, never()).cancelAll();
    }

    private static List<JobInfo> getJobInfos() {
        return List.of(
                new JobInfo.Builder(
                                1,
                                new ComponentName(
                                        TEST_PACKAGE_NAME,
                                        "com.android.adservices.service.measurement.attribution"
                                                + ".AttributionJobService"))
                        .build(),
                new JobInfo.Builder(
                                2,
                                new ComponentName(
                                        TEST_PACKAGE_NAME,
                                        "com.android.extservice.common"
                                                + ".AdServicesAppsearchDeleteSchedulerJobService"))
                        .build(),
                new JobInfo.Builder(
                                3,
                                new ComponentName(
                                        TEST_PACKAGE_NAME,
                                        "com.android.adservices.service.topics"
                                                + ".EpochJobService"))
                        .build());
    }

    @Test
    public void testDisableScheduledBackgroundJobs_cancelsAllJobs() {
        AdServicesBackCompatInit adServicesBackCompatInit =
                Mockito.spy(new AdServicesBackCompatInit(mContext));

        JobScheduler mockScheduler = Mockito.mock(JobScheduler.class);
        when(mContext.getSystemService(JobScheduler.class)).thenReturn(mockScheduler);
        when(mockScheduler.getAllPendingJobs()).thenReturn(getJobInfos());
        doNothing().when(mockScheduler).cancel(anyInt());

        setCommonMocks(TEST_PACKAGE_NAME);

        adServicesBackCompatInit.disableScheduledBackgroundJobs();
        verify(mockScheduler).cancel(1);
        verify(mockScheduler).cancel(3);
        verify(mockScheduler, never()).cancel(2);
        verify(mockScheduler, never()).cancelAll();
    }

    @Test
    public void testDisableScheduledBackgroundJobs_handlesException() throws Exception {
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.getPackageInfo(anyString(), anyInt()))
                .thenThrow(PackageManager.NameNotFoundException.class);

        AdServicesBackCompatInit adServicesBackCompatInit =
                Mockito.spy(new AdServicesBackCompatInit(mContext));
        adServicesBackCompatInit.disableScheduledBackgroundJobs();
        verify(mContext, never()).getSystemService(eq(JobScheduler.class));
    }

    @Test
    public void testClassNameMatchesExpectedValue() {
        assertWithMessage(
                        "AdServicesBackCompatInit class name is hard-coded in ExtServices"
                            + " BootCompletedReceiver. If the name changes, that class needs to be"
                            + " modified in unison")
                .that(AdServicesBackCompatInit.class.getName())
                .isEqualTo("com.android.adservices.service.common.AdServicesBackCompatInit");
    }

    private void setCommonMocks(String packageName) {
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.getPackageName()).thenReturn(packageName);
    }
}
