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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.MeasurementImpl;
import com.android.adservices.service.topics.AppUpdateManager;
import com.android.adservices.service.topics.BlockedTopicsManager;
import com.android.adservices.service.topics.CacheManager;
import com.android.adservices.service.topics.EpochManager;
import com.android.adservices.service.topics.TopicsWorker;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

/** Unit test for {@link com.android.adservices.service.common.PackageChangedReceiver}. */
@SmallTest
public class PackageChangedReceiverTest {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String SAMPLE_PACKAGE = "com.example.measurement.sampleapp";
    private static final String PACKAGE_SCHEME = "package:";
    private static final int BACKGROUND_THREAD_TIMEOUT_MS = 5_000;

    @Mock PackageChangedReceiver mMockPackageChangedReceiver;
    @Mock EpochManager mMockEpochManager;
    @Mock CacheManager mMockCacheManager;
    @Mock BlockedTopicsManager mBlockedTopicsManager;
    @Mock AppUpdateManager mMockAppUpdateManager;
    @Mock Flags mMockFlags;

    private TopicsWorker mTopicsWorker;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
        doNothing()
                .when(mMockPackageChangedReceiver)
                .measurementOnPackageFullyRemoved(any(Context.class), any(Uri.class));
        doNothing()
                .when(mMockPackageChangedReceiver)
                .measurementOnPackageAdded(any(Context.class), any(Uri.class));
        doNothing()
                .when(mMockPackageChangedReceiver)
                .measurementOnPackageDataCleared(any(Context.class), any(Uri.class));
        doCallRealMethod()
                .when(mMockPackageChangedReceiver)
                .onReceive(any(Context.class), any(Intent.class));

        // Mock TopicsWorker to test app update flow in topics API.
        // Start a mockitoSession to mock static method
        mTopicsWorker =
                new TopicsWorker(
                        mMockEpochManager,
                        mMockCacheManager,
                        mBlockedTopicsManager,
                        mMockAppUpdateManager,
                        FlagsFactory.getFlagsForTest());
    }

    @Test
    public void testReceivePackageFullyRemoved_topicsKillSwitchOff() throws InterruptedException {
        Intent intent = new Intent();
        intent.setAction(PackageChangedReceiver.PACKAGE_CHANGED_BROADCAST);
        intent.setData(Uri.parse(PACKAGE_SCHEME + SAMPLE_PACKAGE));
        intent.putExtra(
                PackageChangedReceiver.ACTION_KEY, PackageChangedReceiver.PACKAGE_FULLY_REMOVED);

        // Start a mockitoSession to mock static method
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(TopicsWorker.class)
                        .spyStatic(FlagsFactory.class)
                        .startMocking();
        try {
            // Killswitch is off.
            doReturn(false).when(mMockFlags).getTopicsKillSwitch();

            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

            // Stubbing TopicsWorker.getInstance() to return mocked TopicsWorker instance
            ExtendedMockito.doReturn(mTopicsWorker)
                    .when(() -> TopicsWorker.getInstance(eq(sContext)));

            mMockPackageChangedReceiver.onReceive(sContext, intent);

            // Grant some time to allow background thread to execute
            Thread.sleep(BACKGROUND_THREAD_TIMEOUT_MS);

            verify(mMockPackageChangedReceiver, times(1))
                    .measurementOnPackageFullyRemoved(any(Context.class), any(Uri.class));
            verify(mMockPackageChangedReceiver, never())
                    .measurementOnPackageAdded(any(Context.class), any(Uri.class));
            verify(mMockPackageChangedReceiver, never())
                    .measurementOnPackageDataCleared(any(Context.class), any(Uri.class));

            // Verify method in AppUpdateManager is invoked
            // Note that only package name is passed into following methods.
            verify(mMockAppUpdateManager).deleteAppDataByUri(eq(Uri.parse(SAMPLE_PACKAGE)));
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testReceivePackageFullyRemoved_topicsKillSwitchOn() throws InterruptedException {
        Intent intent = new Intent();
        intent.setAction(PackageChangedReceiver.PACKAGE_CHANGED_BROADCAST);
        intent.setData(Uri.parse(PACKAGE_SCHEME + SAMPLE_PACKAGE));
        intent.putExtra(
                PackageChangedReceiver.ACTION_KEY, PackageChangedReceiver.PACKAGE_FULLY_REMOVED);

        // Start a mockitoSession to mock static method
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(TopicsWorker.class)
                        .spyStatic(FlagsFactory.class)
                        .startMocking();
        try {
            // Killswitch is on.
            doReturn(true).when(mMockFlags).getTopicsKillSwitch();

            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

            mMockPackageChangedReceiver.onReceive(sContext, intent);

            // Grant some time to allow background thread to execute
            Thread.sleep(BACKGROUND_THREAD_TIMEOUT_MS);

            // When the kill switch is on, there is no Topics related work.
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testReceivePackageFullyRemoved_measurementKillSwitchOff() throws Exception {
        Intent intent = new Intent();
        intent.setAction(PackageChangedReceiver.PACKAGE_CHANGED_BROADCAST);
        intent.setData(Uri.parse(PACKAGE_SCHEME + SAMPLE_PACKAGE));
        intent.putExtra(
                PackageChangedReceiver.ACTION_KEY, PackageChangedReceiver.PACKAGE_FULLY_REMOVED);

        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(MeasurementImpl.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        try {
            // Killswitch is off.
            doReturn(false).when(mMockFlags).getMeasurementReceiverDeletePackagesKillSwitch();

            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

            MeasurementImpl mockMeasurementImpl = ExtendedMockito.mock(MeasurementImpl.class);
            ExtendedMockito.doReturn(mockMeasurementImpl)
                    .when(() -> MeasurementImpl.getInstance(any()));

            PackageChangedReceiver spyReceiver = Mockito.spy(new PackageChangedReceiver());
            spyReceiver.onReceive(sContext, intent);

            verify(spyReceiver, never())
                    .measurementOnPackageDataCleared(any(Context.class), any(Uri.class));
            verify(spyReceiver, times(1))
                    .measurementOnPackageFullyRemoved(any(Context.class), any(Uri.class));
            verify(spyReceiver, never())
                    .measurementOnPackageAdded(any(Context.class), any(Uri.class));
            // Allow background thread to execute
            Thread.sleep(50);
            ExtendedMockito.verify(mockMeasurementImpl, times(1)).deletePackageRecords(any());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testReceivePackageFullyRemoved_measurementKillSwitchOn() throws Exception {
        Intent intent = new Intent();
        intent.setAction(PackageChangedReceiver.PACKAGE_CHANGED_BROADCAST);
        intent.setData(Uri.parse(PACKAGE_SCHEME + SAMPLE_PACKAGE));
        intent.putExtra(
                PackageChangedReceiver.ACTION_KEY, PackageChangedReceiver.PACKAGE_FULLY_REMOVED);

        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(MeasurementImpl.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        try {
            // Killswitch is on.
            doReturn(true).when(mMockFlags).getMeasurementReceiverDeletePackagesKillSwitch();

            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

            MeasurementImpl mockMeasurementImpl = ExtendedMockito.mock(MeasurementImpl.class);
            ExtendedMockito.doReturn(mockMeasurementImpl)
                    .when(() -> MeasurementImpl.getInstance(any()));

            PackageChangedReceiver spyReceiver = Mockito.spy(new PackageChangedReceiver());
            spyReceiver.onReceive(sContext, intent);

            verify(spyReceiver, never())
                    .measurementOnPackageDataCleared(any(Context.class), any(Uri.class));
            verify(spyReceiver, times(1))
                    .measurementOnPackageFullyRemoved(any(Context.class), any(Uri.class));
            verify(spyReceiver, never())
                    .measurementOnPackageAdded(any(Context.class), any(Uri.class));
            // Allow background thread to execute
            Thread.sleep(50);
            ExtendedMockito.verify(mockMeasurementImpl, never()).deletePackageRecords(any());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testReceivePackageAdded() throws InterruptedException {
        final long epochId = 1;

        Intent intent = new Intent();
        intent.setAction(PackageChangedReceiver.PACKAGE_CHANGED_BROADCAST);
        intent.setData(Uri.parse(PACKAGE_SCHEME + SAMPLE_PACKAGE));
        intent.putExtra(PackageChangedReceiver.ACTION_KEY, PackageChangedReceiver.PACKAGE_ADDED);

        // Start a mockitoSession to mock static method
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(TopicsWorker.class)
                        .spyStatic(FlagsFactory.class)
                        .startMocking();

        try {
            // Stubbing TopicsWorker.getInstance() to return mocked TopicsWorker instance
            ExtendedMockito.doReturn(mTopicsWorker)
                    .when(() -> TopicsWorker.getInstance(eq(sContext)));
            when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId);

            mMockPackageChangedReceiver.onReceive(sContext, intent);

            // Grant some time to allow background thread to execute
            Thread.sleep(BACKGROUND_THREAD_TIMEOUT_MS);

            verify(mMockPackageChangedReceiver, times(1))
                    .measurementOnPackageAdded(any(Context.class), any(Uri.class));
            verify(mMockPackageChangedReceiver, never())
                    .measurementOnPackageFullyRemoved(any(Context.class), any(Uri.class));

            // Verify method in AppUpdateManager is invoked
            // Note that only package name is passed into following methods.
            verify(mMockEpochManager).getCurrentEpochId();
            verify(mMockAppUpdateManager)
                    .assignTopicsToNewlyInstalledApps(eq(Uri.parse(SAMPLE_PACKAGE)), eq(epochId));

            verify(mMockPackageChangedReceiver, never())
                    .measurementOnPackageDataCleared(any(Context.class), any(Uri.class));
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testReceivePackageAdded_measurementKillSwitchOff() throws Exception {
        Intent intent = new Intent();
        intent.setAction(PackageChangedReceiver.PACKAGE_CHANGED_BROADCAST);
        intent.setData(Uri.parse(PACKAGE_SCHEME + SAMPLE_PACKAGE));
        intent.putExtra(PackageChangedReceiver.ACTION_KEY, PackageChangedReceiver.PACKAGE_ADDED);

        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(MeasurementImpl.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        try {
            // Killswitch is off.
            doReturn(false).when(mMockFlags).getMeasurementReceiverInstallAttributionKillSwitch();

            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

            MeasurementImpl mockMeasurementImpl = ExtendedMockito.mock(MeasurementImpl.class);
            ExtendedMockito.doReturn(mockMeasurementImpl)
                    .when(() -> MeasurementImpl.getInstance(any()));

            PackageChangedReceiver spyReceiver = Mockito.spy(new PackageChangedReceiver());
            spyReceiver.onReceive(sContext, intent);

            verify(spyReceiver, never())
                    .measurementOnPackageDataCleared(any(Context.class), any(Uri.class));
            verify(spyReceiver, never())
                    .measurementOnPackageFullyRemoved(any(Context.class), any(Uri.class));
            verify(spyReceiver, times(1))
                    .measurementOnPackageAdded(any(Context.class), any(Uri.class));
            // Allow background thread to execute
            Thread.sleep(50);
            ExtendedMockito.verify(mockMeasurementImpl, times(1))
                    .doInstallAttribution(any(), anyLong());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testReceivePackageAdded_measurementKillSwitchOn() throws Exception {
        Intent intent = new Intent();
        intent.setAction(PackageChangedReceiver.PACKAGE_CHANGED_BROADCAST);
        intent.setData(Uri.parse(PACKAGE_SCHEME + SAMPLE_PACKAGE));
        intent.putExtra(PackageChangedReceiver.ACTION_KEY, PackageChangedReceiver.PACKAGE_ADDED);

        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(MeasurementImpl.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        try {
            // Killswitch is on.
            doReturn(true).when(mMockFlags).getMeasurementReceiverInstallAttributionKillSwitch();

            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

            MeasurementImpl mockMeasurementImpl = ExtendedMockito.mock(MeasurementImpl.class);
            ExtendedMockito.doReturn(mockMeasurementImpl)
                    .when(() -> MeasurementImpl.getInstance(any()));

            PackageChangedReceiver spyReceiver = Mockito.spy(new PackageChangedReceiver());
            spyReceiver.onReceive(sContext, intent);

            verify(spyReceiver, never())
                    .measurementOnPackageDataCleared(any(Context.class), any(Uri.class));
            verify(spyReceiver, never())
                    .measurementOnPackageFullyRemoved(any(Context.class), any(Uri.class));
            verify(spyReceiver, times(1))
                    .measurementOnPackageAdded(any(Context.class), any(Uri.class));
            // Allow background thread to execute
            Thread.sleep(50);
            ExtendedMockito.verify(mockMeasurementImpl, never())
                    .doInstallAttribution(any(), anyLong());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testReceivePackageDataCleared_measurementKillSwitchOff() throws Exception {
        Intent intent = new Intent();
        intent.setAction(PackageChangedReceiver.PACKAGE_CHANGED_BROADCAST);
        intent.setData(Uri.parse(PACKAGE_SCHEME + SAMPLE_PACKAGE));
        intent.putExtra(
                PackageChangedReceiver.ACTION_KEY, PackageChangedReceiver.PACKAGE_DATA_CLEARED);

        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(MeasurementImpl.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        try {
            // Killswitch is off.
            doReturn(false).when(mMockFlags).getMeasurementReceiverDeletePackagesKillSwitch();

            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

            MeasurementImpl mockMeasurementImpl = ExtendedMockito.mock(MeasurementImpl.class);
            ExtendedMockito.doReturn(mockMeasurementImpl)
                    .when(() -> MeasurementImpl.getInstance(any()));

            PackageChangedReceiver spyReceiver = Mockito.spy(new PackageChangedReceiver());
            spyReceiver.onReceive(sContext, intent);

            verify(spyReceiver, times(1))
                    .measurementOnPackageDataCleared(any(Context.class), any(Uri.class));
            verify(spyReceiver, never())
                    .measurementOnPackageFullyRemoved(any(Context.class), any(Uri.class));
            verify(spyReceiver, never())
                    .measurementOnPackageAdded(any(Context.class), any(Uri.class));
            // Allow background thread to execute
            Thread.sleep(50);
            ExtendedMockito.verify(mockMeasurementImpl, times(1)).deletePackageRecords(any());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testReceivePackageDataCleared_measurementKillSwitchOn() throws Exception {
        Intent intent = new Intent();
        intent.setAction(PackageChangedReceiver.PACKAGE_CHANGED_BROADCAST);
        intent.setData(Uri.parse(PACKAGE_SCHEME + SAMPLE_PACKAGE));
        intent.putExtra(
                PackageChangedReceiver.ACTION_KEY, PackageChangedReceiver.PACKAGE_DATA_CLEARED);

        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(MeasurementImpl.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        try {
            // Killswitch is on.
            doReturn(true).when(mMockFlags).getMeasurementReceiverDeletePackagesKillSwitch();

            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

            MeasurementImpl mockMeasurementImpl = ExtendedMockito.mock(MeasurementImpl.class);
            ExtendedMockito.doReturn(mockMeasurementImpl)
                    .when(() -> MeasurementImpl.getInstance(any()));

            PackageChangedReceiver spyReceiver = Mockito.spy(new PackageChangedReceiver());
            spyReceiver.onReceive(sContext, intent);

            verify(spyReceiver, times(1))
                    .measurementOnPackageDataCleared(any(Context.class), any(Uri.class));
            verify(spyReceiver, never())
                    .measurementOnPackageFullyRemoved(any(Context.class), any(Uri.class));
            verify(spyReceiver, never())
                    .measurementOnPackageAdded(any(Context.class), any(Uri.class));
            // Allow background thread to execute
            Thread.sleep(50);
            ExtendedMockito.verify(mockMeasurementImpl, never()).deletePackageRecords(any());
        } finally {
            session.finishMocking();
        }
    }
}
