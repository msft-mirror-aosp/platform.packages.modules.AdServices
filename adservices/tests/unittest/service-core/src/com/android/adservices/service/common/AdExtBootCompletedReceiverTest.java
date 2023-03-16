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
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static org.mockito.Mockito.atLeastOnce;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@SmallTest
public class AdExtBootCompletedReceiverTest {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Intent sIntent = new Intent();
    private static final String TEST_PACKAGE_NAME = "test";
    @Mock Flags mMockFlags;
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
        AdExtBootCompletedReceiver bootCompletedReceiver =
                Mockito.spy(new AdExtBootCompletedReceiver());
        doReturn(true).when(mMockFlags).getGlobalKillSwitch();

        bootCompletedReceiver.onReceive(sContext, sIntent);
        verify(bootCompletedReceiver, never()).registerPackagedChangedBroadcastReceivers(any());
        verify(bootCompletedReceiver, atLeastOnce())
                .updateAdExtServicesActivities(any(), eq(false));
    }

    @Test
    public void testOnReceive_tPlus_flagOn() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        AdExtBootCompletedReceiver bootCompletedReceiver =
                Mockito.spy(new AdExtBootCompletedReceiver());
        doReturn(true).when(mMockFlags).getEnableBackCompat();
        doReturn(true).when(mMockFlags).getAdServicesEnabled();
        doReturn(false).when(mMockFlags).getGlobalKillSwitch();

        bootCompletedReceiver.onReceive(sContext, sIntent);
        verify(bootCompletedReceiver, never()).registerPackagedChangedBroadcastReceivers(any());
        verify(bootCompletedReceiver, atLeastOnce())
                .updateAdExtServicesActivities(any(), eq(false));
    }

    @Test
    public void testOnReceive_s_flagsOff() {
        Assume.assumeTrue(Build.VERSION.SDK_INT == 31 || Build.VERSION.SDK_INT == 32);
        AdExtBootCompletedReceiver bootCompletedReceiver =
                Mockito.spy(new AdExtBootCompletedReceiver());
        doReturn(false).when(mMockFlags).getEnableBackCompat();

        bootCompletedReceiver.onReceive(sContext, sIntent);
        verify(bootCompletedReceiver, never()).registerPackagedChangedBroadcastReceivers(any());
        verify(bootCompletedReceiver, never()).updateAdExtServicesActivities(any(), eq(true));

        doReturn(true).when(mMockFlags).getEnableBackCompat();
        doReturn(false).when(mMockFlags).getAdServicesEnabled();

        bootCompletedReceiver.onReceive(sContext, sIntent);
        verify(bootCompletedReceiver, never()).registerPackagedChangedBroadcastReceivers(any());
        verify(bootCompletedReceiver, never()).updateAdExtServicesActivities(any(), eq(true));

        doReturn(true).when(mMockFlags).getEnableBackCompat();
        doReturn(true).when(mMockFlags).getAdServicesEnabled();
        doReturn(true).when(mMockFlags).getGlobalKillSwitch();

        bootCompletedReceiver.onReceive(sContext, sIntent);
        verify(bootCompletedReceiver, never()).registerPackagedChangedBroadcastReceivers(any());
        verify(bootCompletedReceiver, never()).updateAdExtServicesActivities(any(), eq(true));
    }

    @Test
    public void testOnReceive_SFlagsOn() {
        Assume.assumeTrue(Build.VERSION.SDK_INT == 31 || Build.VERSION.SDK_INT == 32);
        AdExtBootCompletedReceiver bootCompletedReceiver =
                Mockito.spy(new AdExtBootCompletedReceiver());
        doReturn(true).when(mMockFlags).getEnableBackCompat();
        doReturn(true).when(mMockFlags).getAdServicesEnabled();
        doReturn(false).when(mMockFlags).getGlobalKillSwitch();

        bootCompletedReceiver.onReceive(sContext, sIntent);
        verify(bootCompletedReceiver).registerPackagedChangedBroadcastReceivers(any());
        verify(bootCompletedReceiver).updateAdExtServicesActivities(any(), eq(true));
    }

    @Test
    public void testRegisterReceivers() {
        Assume.assumeTrue(Build.VERSION.SDK_INT == 31 || Build.VERSION.SDK_INT == 32);
        AdExtBootCompletedReceiver bootCompletedReceiver =
                Mockito.spy(new AdExtBootCompletedReceiver());

        bootCompletedReceiver.registerPackagedChangedBroadcastReceivers(sContext);

        verify(() -> PackageChangedReceiver.enableReceiver(any(Context.class)));
        doReturn(true).when(() -> PackageChangedReceiver.enableReceiver(eq(sContext)));
    }

    @Test
    public void testEnableActivities_s() throws Exception {
        Assume.assumeTrue(Build.VERSION.SDK_INT == 31 || Build.VERSION.SDK_INT == 32);
        AdExtBootCompletedReceiver bootCompletedReceiver =
                Mockito.spy(new AdExtBootCompletedReceiver());

        Context mockContext = mock(Context.class);
        when(mockContext.getPackageManager()).thenReturn(mPackageManager);
        when(mockContext.getPackageName()).thenReturn(TEST_PACKAGE_NAME);

        PackageInfo packageInfo = Mockito.spy(PackageInfo.class);
        packageInfo.packageName = TEST_PACKAGE_NAME;
        packageInfo.isApex = true;
        when(mPackageManager.getPackageInfo(eq(packageInfo.packageName), eq(0)))
                .thenReturn(packageInfo);

        // Call the method we're testing.
        bootCompletedReceiver.updateAdExtServicesActivities(mockContext, true);

        verify(mPackageManager, times(7))
                .setComponentEnabledSetting(
                        any(ComponentName.class),
                        eq(PackageManager.COMPONENT_ENABLED_STATE_ENABLED),
                        eq(PackageManager.DONT_KILL_APP));
    }

    @Test
    public void testDisableActivities_tPlus() throws Exception {
        Assume.assumeTrue(Build.VERSION.SDK_INT == 33);
        AdExtBootCompletedReceiver bootCompletedReceiver =
                Mockito.spy(new AdExtBootCompletedReceiver());

        Context mockContext = mock(Context.class);
        when(mockContext.getPackageManager()).thenReturn(mPackageManager);
        when(mockContext.getPackageName()).thenReturn(TEST_PACKAGE_NAME);

        PackageInfo packageInfo = Mockito.spy(PackageInfo.class);
        packageInfo.packageName = TEST_PACKAGE_NAME;
        packageInfo.isApex = true;
        when(mPackageManager.getPackageInfo(eq(packageInfo.packageName), eq(0)))
                .thenReturn(packageInfo);

        // Call the method we're testing.
        bootCompletedReceiver.updateAdExtServicesActivities(mockContext, false);

        verify(mPackageManager, times(7))
                .setComponentEnabledSetting(
                        any(ComponentName.class),
                        eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED),
                        eq(PackageManager.DONT_KILL_APP));
    }
}
