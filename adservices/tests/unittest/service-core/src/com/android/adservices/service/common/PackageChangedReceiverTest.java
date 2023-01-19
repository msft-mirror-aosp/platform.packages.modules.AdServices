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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyLong;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doCallRealMethod;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyZeroInteractions;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.ConsentManager;
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

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Unit test for {@link com.android.adservices.service.common.PackageChangedReceiver}. */
@SmallTest
public class PackageChangedReceiverTest {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String SAMPLE_PACKAGE = "com.example.measurement.sampleapp";
    private static final String PACKAGE_SCHEME = "package:";
    private static final int BACKGROUND_THREAD_TIMEOUT_MS = 50;
    private static final int DEFAULT_PACKAGE_UID = -1;

    @Mock PackageChangedReceiver mMockPackageChangedReceiver;
    @Mock EpochManager mMockEpochManager;
    @Mock CacheManager mMockCacheManager;
    @Mock BlockedTopicsManager mBlockedTopicsManager;
    @Mock AppUpdateManager mMockAppUpdateManager;
    @Mock CustomAudienceDatabase mCustomAudienceDatabaseMock;
    @Mock CustomAudienceDao mCustomAudienceDaoMock;
    @Mock ConsentManager mConsentManager;
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
        doNothingForFledge(spyReceiver);
        doNothingForConsent(spyReceiver);
        return spyReceiver;
    }

    private PackageChangedReceiver createSpyPackageReceiverForTopics() {
        PackageChangedReceiver spyReceiver = Mockito.spy(new PackageChangedReceiver());
        doNothingForMeasurement(spyReceiver);
        doNothingForFledge(spyReceiver);
        doNothingForConsent(spyReceiver);
        return spyReceiver;
    }

    private PackageChangedReceiver createSpyPackageReceiverForFledge() {
        PackageChangedReceiver spyReceiver = Mockito.spy(new PackageChangedReceiver());
        doNothingForMeasurement(spyReceiver);
        doNothingForTopics(spyReceiver);
        doNothingForConsent(spyReceiver);
        return spyReceiver;
    }

    private PackageChangedReceiver createSpyPackageReceiverForConsent() {
        PackageChangedReceiver spyReceiver = Mockito.spy(new PackageChangedReceiver());
        doNothingForMeasurement(spyReceiver);
        doNothingForTopics(spyReceiver);
        doNothingForFledge(spyReceiver);
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

    private void doNothingForFledge(PackageChangedReceiver receiver) {
        doNothing().when(receiver).fledgeOnPackageFullyRemovedOrDataCleared(any(), any());
    }

    private void doNothingForConsent(PackageChangedReceiver receiver) {
        doNothing().when(receiver).consentOnPackageFullyRemoved(any(), any(), anyInt());
    }

    private Intent createDefaultIntentWithAction(String value) {
        Intent intent = new Intent();
        intent.setAction(PackageChangedReceiver.PACKAGE_CHANGED_BROADCAST);
        intent.setData(Uri.parse(PACKAGE_SCHEME + SAMPLE_PACKAGE));
        intent.putExtra(PackageChangedReceiver.ACTION_KEY, value);
        intent.putExtra(Intent.EXTRA_UID, 0);

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
            long epochId = 1;

            // Kill switch is off.
            doReturn(false).when(mMockFlags).getTopicsKillSwitch();

            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            when(FlagsFactory.getFlags()).thenReturn(mMockFlags);

            // Enable TopicContributors feature
            when(mMockEpochManager.supportsTopicContributorFeature()).thenReturn(true);

            // Stubbing TopicsWorker.getInstance() to return mocked TopicsWorker instance
            doReturn(mSpyTopicsWorker).when(() -> TopicsWorker.getInstance(any()));
            doReturn(epochId).when(mMockEpochManager).getCurrentEpochId();

            // Initialize package receiver meant for Topics
            PackageChangedReceiver spyReceiver = createSpyPackageReceiverForTopics();
            spyReceiver.onReceive(sContext, intent);

            // Grant some time to allow background thread to execute
            Thread.sleep(BACKGROUND_THREAD_TIMEOUT_MS);

            // Verify method in AppUpdateManager is invoked
            // getCurrentEpochId() is invoked twice: handleAppUninstallation() + loadCache()
            // Note that only package name is passed into following methods.
            verify(mMockEpochManager, times(2)).getCurrentEpochId();
            verify(mMockAppUpdateManager)
                    .handleAppUninstallationInRealTime(Uri.parse(SAMPLE_PACKAGE), epochId);
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
            when(FlagsFactory.getFlags()).thenReturn(mMockFlags);

            // Initialize package receiver meant for Topics and execute
            createSpyPackageReceiverForTopics().onReceive(sContext, intent);

            // Grant some time to allow background thread to execute
            Thread.sleep(BACKGROUND_THREAD_TIMEOUT_MS);

            // When the kill switch is on, there is no Topics related work.
            verify(mSpyTopicsWorker, never()).handleAppUninstallation(any());
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
            when(FlagsFactory.getFlags()).thenReturn(mMockFlags);

            // Mock static method MeasurementImpl.getInstance that executes on a separate thread
            MeasurementImpl mockMeasurementImpl = mock(MeasurementImpl.class);
            doReturn(mockMeasurementImpl).when(() -> MeasurementImpl.getInstance(any()));

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
            doReturn(mMockFlags).when(FlagsFactory::getFlags);

            // Mock static method MeasurementImpl.getInstance that executes on a separate thread
            MeasurementImpl mockMeasurementImpl = mock(MeasurementImpl.class);
            doReturn(mockMeasurementImpl).when(() -> MeasurementImpl.getInstance(any()));

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
    public void testReceivePackageFullyRemoved_fledgeKillSwitchOff() throws InterruptedException {
        Intent intent = createDefaultIntentWithAction(PackageChangedReceiver.PACKAGE_FULLY_REMOVED);

        // Start a mockitoSession to mock static method
        // Lenient added to allow easy disabling of other APIs' methods
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .mockStatic(FlagsFactory.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();
        try {
            // Kill switch is off; service is enabled
            doReturn(false).when(mMockFlags).getFledgeCustomAudienceServiceKillSwitch();
            doReturn(mMockFlags).when(FlagsFactory::getFlags);

            // Mock static method CustomAudienceDatabase.getInstance() executed on a separate thread
            doReturn(mCustomAudienceDaoMock).when(mCustomAudienceDatabaseMock).customAudienceDao();

            CountDownLatch completionLatch = new CountDownLatch(1);
            doAnswer(
                            unusedInvocation -> {
                                completionLatch.countDown();
                                return null;
                            })
                    .when(mCustomAudienceDaoMock)
                    .deleteCustomAudienceDataByOwner(any());

            // Initialize package receiver meant for FLEDGE
            PackageChangedReceiver spyReceiver = createSpyPackageReceiverForFledge();
            doReturn(mCustomAudienceDatabaseMock)
                    .when(spyReceiver)
                    .getCustomAudienceDatabase(any());
            spyReceiver.onReceive(sContext, intent);

            verify(spyReceiver).fledgeOnPackageFullyRemovedOrDataCleared(any(), any());

            // Verify method inside background thread executes
            assertThat(completionLatch.await(500, TimeUnit.MILLISECONDS)).isTrue();
            verify(mCustomAudienceDaoMock).deleteCustomAudienceDataByOwner(any());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testReceivePackageFullyRemoved_fledgeKillSwitchOn() {
        Intent intent = createDefaultIntentWithAction(PackageChangedReceiver.PACKAGE_FULLY_REMOVED);

        // Start a mockitoSession to mock static method
        // Lenient added to allow easy disabling of other APIs' methods
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .mockStatic(FlagsFactory.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();
        try {
            // Kill switch is on; service is disabled
            doReturn(true).when(mMockFlags).getFledgeCustomAudienceServiceKillSwitch();
            doReturn(mMockFlags).when(FlagsFactory::getFlags);

            // Initialize package receiver meant for FLEDGE
            PackageChangedReceiver spyReceiver = createSpyPackageReceiverForFledge();
            spyReceiver.onReceive(sContext, intent);

            verify(spyReceiver).fledgeOnPackageFullyRemovedOrDataCleared(any(), any());

            // Verify no executions
            verify(spyReceiver, never()).getCustomAudienceDatabase(any());
            verifyZeroInteractions(mCustomAudienceDatabaseMock, mCustomAudienceDaoMock);
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testReceivePackageFullyRemoved_consent() throws InterruptedException, IOException {
        Intent intent = createDefaultIntentWithAction(PackageChangedReceiver.PACKAGE_FULLY_REMOVED);

        // Start a mockitoSession to mock static method
        // Lenient added to allow easy disabling of other APIs' methods
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .mockStatic(ConsentManager.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();
        try {
            // Mock static method AppConsentDao.getInstance() executed on a separate thread
            doReturn(mConsentManager).when(() -> ConsentManager.getInstance(any()));

            CountDownLatch completionLatch = new CountDownLatch(1);
            doAnswer(
                            unusedInvocation -> {
                                completionLatch.countDown();
                                return null;
                            })
                    .when(mConsentManager)
                    .clearConsentForUninstalledApp(any(), anyInt());

            // Initialize package receiver meant for Consent
            PackageChangedReceiver spyReceiver = createSpyPackageReceiverForConsent();
            spyReceiver.onReceive(sContext, intent);

            verify(spyReceiver).consentOnPackageFullyRemoved(any(), any(), anyInt());

            // Verify method inside background thread executes
            assertThat(completionLatch.await(500, TimeUnit.MILLISECONDS)).isTrue();
            verify(mConsentManager).clearConsentForUninstalledApp(any(), anyInt());
        } finally {
            session.finishMocking();
        }
    }

    /**
     * Tests that when no packageUid is present via the Intent Extra, consent data for all apps
     * needs to be cleared.
     */
    @Test
    public void testReceivePackageFullyRemoved_consent_noPackageUid()
            throws InterruptedException, IOException {
        Intent intent = createDefaultIntentWithAction(PackageChangedReceiver.PACKAGE_FULLY_REMOVED);
        intent.removeExtra(Intent.EXTRA_UID);

        validateConsentClearedWhenPackageUidAbsent(intent);
    }

    /**
     * Tests that wen packageUid is explicitly set to the default value via the Intent Extra,
     * consent data for all apps needs to be cleared.
     */
    @Test
    public void testReceivePackageFullyRemoved_consent_packageUidIsExplicitlyDefault()
            throws InterruptedException, IOException {
        Intent intent = createDefaultIntentWithAction(PackageChangedReceiver.PACKAGE_FULLY_REMOVED);
        intent.putExtra(Intent.EXTRA_UID, DEFAULT_PACKAGE_UID);

        validateConsentClearedWhenPackageUidAbsent(intent);
    }

    private void validateConsentClearedWhenPackageUidAbsent(Intent intent)
            throws IOException, InterruptedException {
        // Start a mockitoSession to mock static method
        // Lenient added to allow easy disabling of other APIs' methods
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .mockStatic(ConsentManager.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();
        try {
            // Mock static method AppConsentDao.getInstance() executed on a separate thread
            doReturn(mConsentManager).when(() -> ConsentManager.getInstance(any()));

            CountDownLatch completionLatch = new CountDownLatch(1);
            doAnswer(
                            unusedInvocation -> {
                                completionLatch.countDown();
                                return null;
                            })
                    .when(mConsentManager)
                    .resetAppsAndBlockedApps();

            // Initialize package receiver meant for Consent
            PackageChangedReceiver spyReceiver = createSpyPackageReceiverForConsent();
            spyReceiver.onReceive(sContext, intent);

            // Package UID is expected to be -1 if there is no EXTRA_UID in the Intent's Extra.
            verify(spyReceiver).consentOnPackageFullyRemoved(any(), any(), eq(DEFAULT_PACKAGE_UID));

            // Verify method inside background thread executes
            assertThat(completionLatch.await(500, TimeUnit.MILLISECONDS)).isTrue();
            verify(mConsentManager).resetAppsAndBlockedApps();
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
            doReturn(mSpyTopicsWorker).when(() -> TopicsWorker.getInstance(eq(sContext)));
            when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId);

            // Initialize package receiver meant for Topics and execute
            createSpyPackageReceiverForTopics().onReceive(sContext, intent);

            // Grant some time to allow background thread to execute
            Thread.sleep(BACKGROUND_THREAD_TIMEOUT_MS);

            // Verify method in AppUpdateManager is invoked.
            // getCurrentEpochId() is invoked twice: handleAppInstallation() + loadCache()
            // Note that only package name is passed into following methods.
            verify(mMockEpochManager, times(2)).getCurrentEpochId();
            verify(mMockAppUpdateManager)
                    .handleAppInstallationInRealTime(Uri.parse(SAMPLE_PACKAGE), epochId);
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
            doReturn(mMockFlags).when(FlagsFactory::getFlags);

            // Mock static method MeasurementImpl.getInstance that executes on a separate thread
            MeasurementImpl mockMeasurementImpl = mock(MeasurementImpl.class);
            doReturn(mockMeasurementImpl).when(() -> MeasurementImpl.getInstance(any()));

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
            doReturn(mMockFlags).when(FlagsFactory::getFlags);

            // Mock static method MeasurementImpl.getInstance that executes on a separate thread
            MeasurementImpl mockMeasurementImpl = mock(MeasurementImpl.class);
            doReturn(mockMeasurementImpl).when(() -> MeasurementImpl.getInstance(any()));

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
            doReturn(mMockFlags).when(FlagsFactory::getFlags);

            // Mock static method MeasurementImpl.getInstance that executes on a separate thread
            MeasurementImpl mockMeasurementImpl = mock(MeasurementImpl.class);
            doReturn(mockMeasurementImpl).when(() -> MeasurementImpl.getInstance(any()));

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
            doReturn(mMockFlags).when(FlagsFactory::getFlags);

            // Mock static method MeasurementImpl.getInstance that executes on a separate thread
            MeasurementImpl mockMeasurementImpl = mock(MeasurementImpl.class);
            doReturn(mockMeasurementImpl).when(() -> MeasurementImpl.getInstance(any()));

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

    @Test
    public void testReceivePackageDataCleared_fledgeKillSwitchOff() throws InterruptedException {
        Intent intent = createDefaultIntentWithAction(PackageChangedReceiver.PACKAGE_DATA_CLEARED);

        // Start a mockitoSession to mock static method
        // Lenient added to allow easy disabling of other APIs' methods
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .mockStatic(FlagsFactory.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();
        try {
            // Kill switch is off; service is enabled
            doReturn(false).when(mMockFlags).getFledgeCustomAudienceServiceKillSwitch();
            doReturn(mMockFlags).when(FlagsFactory::getFlags);

            // Mock static method CustomAudienceDatabase.getInstance() executed on a separate thread
            doReturn(mCustomAudienceDaoMock).when(mCustomAudienceDatabaseMock).customAudienceDao();

            CountDownLatch completionLatch = new CountDownLatch(1);
            doAnswer(
                            unusedInvocation -> {
                                completionLatch.countDown();
                                return null;
                            })
                    .when(mCustomAudienceDaoMock)
                    .deleteCustomAudienceDataByOwner(any());

            // Initialize package receiver meant for FLEDGE
            PackageChangedReceiver spyReceiver = createSpyPackageReceiverForFledge();
            doReturn(mCustomAudienceDatabaseMock)
                    .when(spyReceiver)
                    .getCustomAudienceDatabase(any());
            spyReceiver.onReceive(sContext, intent);

            verify(spyReceiver).fledgeOnPackageFullyRemovedOrDataCleared(any(), any());

            // Verify method inside background thread executes
            assertThat(completionLatch.await(500, TimeUnit.MILLISECONDS)).isTrue();
            verify(mCustomAudienceDaoMock).deleteCustomAudienceDataByOwner(any());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testReceivePackageDataCleared_fledgeKillSwitchOn() {
        Intent intent = createDefaultIntentWithAction(PackageChangedReceiver.PACKAGE_DATA_CLEARED);

        // Start a mockitoSession to mock static method
        // Lenient added to allow easy disabling of other APIs' methods
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .mockStatic(FlagsFactory.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();
        try {
            // Kill switch is on; service is disabled
            doReturn(true).when(mMockFlags).getFledgeCustomAudienceServiceKillSwitch();
            doReturn(mMockFlags).when(FlagsFactory::getFlags);

            // Initialize package receiver meant for FLEDGE
            PackageChangedReceiver spyReceiver = createSpyPackageReceiverForFledge();
            spyReceiver.onReceive(sContext, intent);

            verify(spyReceiver).fledgeOnPackageFullyRemovedOrDataCleared(any(), any());

            // Verify no executions
            verify(spyReceiver, never()).getCustomAudienceDatabase(any());
            verifyZeroInteractions(mCustomAudienceDatabaseMock, mCustomAudienceDaoMock);
        } finally {
            session.finishMocking();
        }
    }
}
