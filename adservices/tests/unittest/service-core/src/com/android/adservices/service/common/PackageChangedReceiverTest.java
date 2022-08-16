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
    private static final int BACKGROUND_THREAD_TIMEOUT_MS = 50;

    @Mock PackageChangedReceiver mMockPackageChangedReceiver;
    @Mock EpochManager mMockEpochManager;
    @Mock CacheManager mMockCacheManager;
    @Mock BlockedTopicsManager mBlockedTopicsManager;
    @Mock AppUpdateManager mMockAppUpdateManager;
    @Mock Flags mMockFlags;

    private TopicsWorker mSpyTopicsWorker;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
        doCallRealMethod()
                .when(mMockPackageChangedReceiver)
                .onReceive(any(Context.class), any(Intent.class));
        // Mock TopicsWorker to test app update flow in topics API.
        // Start a mockitoSession to mock static method
        mSpyTopicsWorker =
                Mockito.spy(
                        new TopicsWorker(
                                mMockEpochManager,
                                mMockCacheManager,
                                mBlockedTopicsManager,
                                mMockAppUpdateManager,
                                FlagsFactory.getFlagsForTest()));
    }

    private PackageChangedReceiver createSpyPackageReceiverForMeasurement() {
        PackageChangedReceiver spyReceiver = Mockito.spy(new PackageChangedReceiver());
        doNothingForTopics(spyReceiver);
        return spyReceiver;
    }

    private PackageChangedReceiver createSpyPackageReceiverForTopics() {
        PackageChangedReceiver spyReceiver = Mockito.spy(new PackageChangedReceiver());
        doNothingForMeasurement(spyReceiver);
        return spyReceiver;
    }

    private void doNothingForMeasurement(PackageChangedReceiver receiver) {
        doNothing().when(receiver).measurementOnPackageFullyRemoved(any(), any());
        doNothing().when(receiver).measurementOnPackageAdded(any(), any());
        doNothing().when(receiver).measurementOnPackageDataCleared(any(), any());
    }

    private void doNothingForTopics(PackageChangedReceiver receiver) {
        doNothing().when(receiver).topicsOnPackageFullyRemoved(any(), any());
        doNothing().when(receiver).topicsOnPackageAdded(any(), any());
    }

    private Intent createDefaultIntentWithAction(String value) {
        Intent intent = new Intent();
        intent.setAction(PackageChangedReceiver.PACKAGE_CHANGED_BROADCAST);
        intent.setData(Uri.parse(PACKAGE_SCHEME + SAMPLE_PACKAGE));
        intent.putExtra(PackageChangedReceiver.ACTION_KEY, value);
        return intent;
    }

    @Test
    public void testReceivePackageFullyRemoved_topicsKillSwitchOff() throws InterruptedException {
        Intent intent = createDefaultIntentWithAction(PackageChangedReceiver.PACKAGE_FULLY_REMOVED);

        // Start a mockitoSession to mock static method
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(TopicsWorker.class)
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        try {
            // Kill switch is off.
            doReturn(false).when(mMockFlags).getTopicsKillSwitch();

            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.when(FlagsFactory.getFlags()).thenReturn(mMockFlags);

            // Stubbing TopicsWorker.getInstance() to return mocked TopicsWorker instance
            ExtendedMockito.doReturn(mSpyTopicsWorker).when(() -> TopicsWorker.getInstance(any()));

            // Initialize package receiver meant for Topics
            PackageChangedReceiver spyReceiver = createSpyPackageReceiverForTopics();
            spyReceiver.onReceive(sContext, intent);

            // Grant some time to allow background thread to execute
            Thread.sleep(BACKGROUND_THREAD_TIMEOUT_MS);

            // Verify method in AppUpdateManager is invoked
            // Note that only package name is passed into following methods.
            verify(mMockAppUpdateManager).deleteAppDataByUri(eq(Uri.parse(SAMPLE_PACKAGE)));
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testReceivePackageFullyRemoved_topicsKillSwitchOn() throws InterruptedException {
        Intent intent = createDefaultIntentWithAction(PackageChangedReceiver.PACKAGE_FULLY_REMOVED);

        // Start a mockitoSession to mock static method
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(TopicsWorker.class)
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        try {
            // Kill switch is on.
            doReturn(true).when(mMockFlags).getTopicsKillSwitch();

            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.when(FlagsFactory.getFlags()).thenReturn(mMockFlags);

            // Initialize package receiver meant for Topics and execute
            createSpyPackageReceiverForTopics().onReceive(sContext, intent);

            // Grant some time to allow background thread to execute
            Thread.sleep(BACKGROUND_THREAD_TIMEOUT_MS);

            // When the kill switch is on, there is no Topics related work.
            verify(mSpyTopicsWorker, never()).deletePackageData(any());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testReceivePackageFullyRemoved_measurementKillSwitchOff() throws Exception {
        Intent intent = createDefaultIntentWithAction(PackageChangedReceiver.PACKAGE_FULLY_REMOVED);

        // Start a mockitoSession to mock static method
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(MeasurementImpl.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        try {
            // Kill switch is off.
            doReturn(false).when(mMockFlags).getMeasurementReceiverDeletePackagesKillSwitch();

            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.when(FlagsFactory.getFlags()).thenReturn(mMockFlags);

            // Mock static method MeasurementImpl.getInstance that executes on a separate thread
            MeasurementImpl mockMeasurementImpl = ExtendedMockito.mock(MeasurementImpl.class);
            ExtendedMockito.doReturn(mockMeasurementImpl)
                    .when(() -> MeasurementImpl.getInstance(any()));

            // Initialize package receiver meant for Measurement
            PackageChangedReceiver spyReceiver = createSpyPackageReceiverForMeasurement();
            spyReceiver.onReceive(sContext, intent);

            // Verify only measurement fully removed method was executed from measurement methods
            verify(spyReceiver, never()).measurementOnPackageDataCleared(any(), any());
            verify(spyReceiver, times(1)).measurementOnPackageFullyRemoved(any(), any());
            verify(spyReceiver, never()).measurementOnPackageAdded(any(), any());

            // Grant some time to allow background thread to execute
            Thread.sleep(BACKGROUND_THREAD_TIMEOUT_MS);

            // Verify method inside measurement background thread executes
            verify(mockMeasurementImpl, times(1)).deletePackageRecords(any());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testReceivePackageFullyRemoved_measurementKillSwitchOn() throws Exception {
        Intent intent = createDefaultIntentWithAction(PackageChangedReceiver.PACKAGE_FULLY_REMOVED);

        // Start a mockitoSession to mock static method
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(MeasurementImpl.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        try {
            // Kill Switch is on.
            doReturn(true).when(mMockFlags).getMeasurementReceiverDeletePackagesKillSwitch();

            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

            // Mock static method MeasurementImpl.getInstance that executes on a separate thread
            MeasurementImpl mockMeasurementImpl = ExtendedMockito.mock(MeasurementImpl.class);
            ExtendedMockito.doReturn(mockMeasurementImpl)
                    .when(() -> MeasurementImpl.getInstance(any()));

            // Initialize package receiver meant for Measurement
            PackageChangedReceiver spyReceiver = createSpyPackageReceiverForMeasurement();
            spyReceiver.onReceive(sContext, intent);

            // Verify only measurement fully removed method was executed from measurement methods
            verify(spyReceiver, never()).measurementOnPackageDataCleared(any(), any());
            verify(spyReceiver, times(1)).measurementOnPackageFullyRemoved(any(), any());
            verify(spyReceiver, never()).measurementOnPackageAdded(any(), any());

            // Allow background thread to execute
            Thread.sleep(BACKGROUND_THREAD_TIMEOUT_MS);

            // Verify method inside measurement background thread does not execute
            verify(mockMeasurementImpl, never()).deletePackageRecords(any());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testReceivePackageAdded_topics() throws InterruptedException {
        final long epochId = 1;

        Intent intent = createDefaultIntentWithAction(PackageChangedReceiver.PACKAGE_ADDED);

        // Start a mockitoSession to mock static method
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(TopicsWorker.class)
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();

        try {
            // Stubbing TopicsWorker.getInstance() to return mocked TopicsWorker instance
            ExtendedMockito.doReturn(mSpyTopicsWorker)
                    .when(() -> TopicsWorker.getInstance(eq(sContext)));
            when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId);

            // Initialize package receiver meant for Topics and execute
            createSpyPackageReceiverForTopics().onReceive(sContext, intent);

            // Grant some time to allow background thread to execute
            Thread.sleep(BACKGROUND_THREAD_TIMEOUT_MS);

            // Verify method in AppUpdateManager is invoked
            // Note that only package name is passed into following methods.
            verify(mMockEpochManager).getCurrentEpochId();
            verify(mMockAppUpdateManager)
                    .assignTopicsToNewlyInstalledApps(eq(Uri.parse(SAMPLE_PACKAGE)), eq(epochId));
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testReceivePackageAdded_measurementKillSwitchOff() throws Exception {
        Intent intent = createDefaultIntentWithAction(PackageChangedReceiver.PACKAGE_ADDED);

        // Start a mockitoSession to mock static method
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(MeasurementImpl.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        try {
            // Kill Switch is off.
            doReturn(false).when(mMockFlags).getMeasurementReceiverInstallAttributionKillSwitch();

            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

            // Mock static method MeasurementImpl.getInstance that executes on a separate thread
            MeasurementImpl mockMeasurementImpl = ExtendedMockito.mock(MeasurementImpl.class);
            ExtendedMockito.doReturn(mockMeasurementImpl)
                    .when(() -> MeasurementImpl.getInstance(any()));

            // Initialize package receiver meant for Measurement
            PackageChangedReceiver spyReceiver = createSpyPackageReceiverForMeasurement();
            spyReceiver.onReceive(sContext, intent);

            // Verify only measurement added method was executed from measurement methods
            verify(spyReceiver, never()).measurementOnPackageDataCleared(any(), any());
            verify(spyReceiver, never()).measurementOnPackageFullyRemoved(any(), any());
            verify(spyReceiver, times(1)).measurementOnPackageAdded(any(), any());

            // Allow background thread to execute
            Thread.sleep(BACKGROUND_THREAD_TIMEOUT_MS);

            // Verify method inside measurement background thread executes
            verify(mockMeasurementImpl, times(1)).doInstallAttribution(any(), anyLong());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testReceivePackageAdded_measurementKillSwitchOn() throws Exception {
        Intent intent = createDefaultIntentWithAction(PackageChangedReceiver.PACKAGE_ADDED);

        // Start a mockitoSession to mock static method
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(MeasurementImpl.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        try {
            // Kill Switch is on.
            doReturn(true).when(mMockFlags).getMeasurementReceiverInstallAttributionKillSwitch();

            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

            // Mock static method MeasurementImpl.getInstance that executes on a separate thread
            MeasurementImpl mockMeasurementImpl = ExtendedMockito.mock(MeasurementImpl.class);
            ExtendedMockito.doReturn(mockMeasurementImpl)
                    .when(() -> MeasurementImpl.getInstance(any()));

            // Initialize package receiver meant for Measurement
            PackageChangedReceiver spyReceiver = createSpyPackageReceiverForMeasurement();
            spyReceiver.onReceive(sContext, intent);

            // Verify only measurement added method was executed from measurement methods
            verify(spyReceiver, never()).measurementOnPackageDataCleared(any(), any());
            verify(spyReceiver, never()).measurementOnPackageFullyRemoved(any(), any());
            verify(spyReceiver, times(1)).measurementOnPackageAdded(any(), any());

            // Allow background thread to execute
            Thread.sleep(BACKGROUND_THREAD_TIMEOUT_MS);

            // Verify method inside measurement background thread does not execute
            verify(mockMeasurementImpl, never()).doInstallAttribution(any(), anyLong());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testReceivePackageDataCleared_measurementKillSwitchOff() throws Exception {
        Intent intent = createDefaultIntentWithAction(PackageChangedReceiver.PACKAGE_DATA_CLEARED);

        // Start a mockitoSession to mock static method
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(MeasurementImpl.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        try {
            // Kill Switch is off.
            doReturn(false).when(mMockFlags).getMeasurementReceiverDeletePackagesKillSwitch();

            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

            // Mock static method MeasurementImpl.getInstance that executes on a separate thread
            MeasurementImpl mockMeasurementImpl = ExtendedMockito.mock(MeasurementImpl.class);
            ExtendedMockito.doReturn(mockMeasurementImpl)
                    .when(() -> MeasurementImpl.getInstance(any()));

            // Initialize package receiver meant for Measurement
            PackageChangedReceiver spyReceiver = createSpyPackageReceiverForMeasurement();
            spyReceiver.onReceive(sContext, intent);

            // Verify only measurement cleared method was executed from measurement methods
            verify(spyReceiver, times(1)).measurementOnPackageDataCleared(any(), any());
            verify(spyReceiver, never()).measurementOnPackageFullyRemoved(any(), any());
            verify(spyReceiver, never()).measurementOnPackageAdded(any(), any());

            // Allow background thread to execute
            Thread.sleep(BACKGROUND_THREAD_TIMEOUT_MS);

            // Verify method inside measurement background thread executes
            verify(mockMeasurementImpl, times(1)).deletePackageRecords(any());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testReceivePackageDataCleared_measurementKillSwitchOn() throws Exception {
        Intent intent = createDefaultIntentWithAction(PackageChangedReceiver.PACKAGE_DATA_CLEARED);

        // Start a mockitoSession to mock static method
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(MeasurementImpl.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        try {
            // Kill Switch is on.
            doReturn(true).when(mMockFlags).getMeasurementReceiverDeletePackagesKillSwitch();

            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

            // Mock static method MeasurementImpl.getInstance that executes on a separate thread
            MeasurementImpl mockMeasurementImpl = ExtendedMockito.mock(MeasurementImpl.class);
            ExtendedMockito.doReturn(mockMeasurementImpl)
                    .when(() -> MeasurementImpl.getInstance(any()));

            // Initialize package receiver meant for Measurement
            PackageChangedReceiver spyReceiver = createSpyPackageReceiverForMeasurement();
            spyReceiver.onReceive(sContext, intent);

            // Verify only measurement cleared method was executed from measurement methods
            verify(spyReceiver, times(1)).measurementOnPackageDataCleared(any(), any());
            verify(spyReceiver, never()).measurementOnPackageFullyRemoved(any(), any());
            verify(spyReceiver, never()).measurementOnPackageAdded(any(), any());

            // Allow background thread to execute
            Thread.sleep(BACKGROUND_THREAD_TIMEOUT_MS);

            // Verify method inside measurement background thread does not execute
            verify(mockMeasurementImpl, never()).deletePackageRecords(any());
        } finally {
            session.finishMocking();
        }
    }
}
