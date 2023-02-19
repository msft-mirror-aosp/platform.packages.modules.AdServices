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

package com.android.adservices.service.stats;

import static com.android.adservices.service.stats.EpochComputationClassifierStats.ClassifierType;
import static com.android.adservices.service.stats.EpochComputationClassifierStats.OnDeviceClassifierStatus;
import static com.android.adservices.service.stats.EpochComputationClassifierStats.PrecomputedClassifierStatus;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.android.adservices.service.Flags;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;

public class StatsdAdServicesLoggerTest {
    // Atom IDs
    private static final int TOPICS_REPORTED_ATOM_ID = 535;
    private static final int EPOCH_COMPUTATION_CLASSIFIER_ATOM_ID = 537;
    private static final int TOPICS_REPORTED_COMPAT_ATOM_ID = 598;
    private static final int EPOCH_COMPUTATION_CLASSIFIER_COMPAT_ATOM_ID = 599;

    // Test params for GetTopicsReportedStats
    private static final int FILTERED_BLOCKED_TOPIC_COUNT = 0;
    private static final int DUPLICATE_TOPIC_COUNT = 0;
    private static final int TOPIC_IDS_COUNT = 1;

    private static final GetTopicsReportedStats TOPICS_REPORTED_STATS_DATA =
            GetTopicsReportedStats.builder()
                    .setFilteredBlockedTopicCount(FILTERED_BLOCKED_TOPIC_COUNT)
                    .setDuplicateTopicCount(DUPLICATE_TOPIC_COUNT)
                    .setTopicIdsCount(TOPIC_IDS_COUNT)
                    .build();

    // Test params for EpochComputationClassifierStats
    private static final ImmutableList<Integer> TOPIC_IDS = ImmutableList.of(10230, 10227);
    private static final int BUILD_ID = 8;
    private static final String ASSET_VERSION = "2";

    private static final EpochComputationClassifierStats EPOCH_COMPUTATION_CLASSIFIER_STATS_DATA =
            EpochComputationClassifierStats.builder()
                    .setTopicIds(TOPIC_IDS)
                    .setBuildId(BUILD_ID)
                    .setAssetVersion(ASSET_VERSION)
                    .setClassifierType(ClassifierType.ON_DEVICE_CLASSIFIER)
                    .setOnDeviceClassifierStatus(
                            OnDeviceClassifierStatus.ON_DEVICE_CLASSIFIER_STATUS_SUCCESS)
                    .setPrecomputedClassifierStatus(
                            PrecomputedClassifierStatus.PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED)
                    .build();

    private MockitoSession mMockitoSession;
    private StatsdAdServicesLogger mLogger;
    @Mock private Flags mFlags;

    @Before
    public void setUp() {
        mMockitoSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(SdkLevel.class)
                        .mockStatic(AdServicesStatsLog.class)
                        .initMocks(this)
                        .startMocking();

        mLogger = new StatsdAdServicesLogger(mFlags);
    }

    @After
    public void tearDown() {
        mMockitoSession.finishMocking();
    }

    @Test
    public void testLogGetTopicsReportedStats_tPlus() {
        // Mocks
        when(mFlags.getCompatLoggingKillSwitch()).thenReturn(false);
        ExtendedMockito.doReturn(true).when(SdkLevel::isAtLeastT);
        ExtendedMockito.doNothing()
                .when(() -> AdServicesStatsLog.write(anyInt(), anyInt(), anyInt(), anyInt()));
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(), any(int[].class), anyInt(), anyInt(), anyInt()));

        // Invoke logging call
        mLogger.logGetTopicsReportedStats(TOPICS_REPORTED_STATS_DATA);

        // Verify compat logging
        ExtendedMockito.verify(
                () ->
                        AdServicesStatsLog.write(
                                TOPICS_REPORTED_COMPAT_ATOM_ID,
                                FILTERED_BLOCKED_TOPIC_COUNT,
                                DUPLICATE_TOPIC_COUNT,
                                TOPIC_IDS_COUNT));
        // Verify T+ logging
        ExtendedMockito.verify(
                () ->
                        AdServicesStatsLog.write(
                                TOPICS_REPORTED_ATOM_ID,
                                /* topic_ids = */ new int[] {},
                                FILTERED_BLOCKED_TOPIC_COUNT,
                                DUPLICATE_TOPIC_COUNT,
                                TOPIC_IDS_COUNT));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogGetTopicsReportedStats_tPlus_noCompatLoggingDueToKillSwitch() {
        // Mocks
        when(mFlags.getCompatLoggingKillSwitch()).thenReturn(true);
        ExtendedMockito.doReturn(true).when(SdkLevel::isAtLeastT);
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(), any(int[].class), anyInt(), anyInt(), anyInt()));

        // Invoke logging call
        mLogger.logGetTopicsReportedStats(TOPICS_REPORTED_STATS_DATA);

        // Verify T+ logging only
        ExtendedMockito.verify(
                () ->
                        AdServicesStatsLog.write(
                                TOPICS_REPORTED_ATOM_ID,
                                /* topic_ids = */ new int[] {},
                                FILTERED_BLOCKED_TOPIC_COUNT,
                                DUPLICATE_TOPIC_COUNT,
                                TOPIC_IDS_COUNT));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogGetTopicsReportedStats_sMinus() {
        // Mocks
        when(mFlags.getCompatLoggingKillSwitch()).thenReturn(false);
        ExtendedMockito.doReturn(false).when(SdkLevel::isAtLeastT);
        ExtendedMockito.doNothing()
                .when(() -> AdServicesStatsLog.write(anyInt(), anyInt(), anyInt(), anyInt()));

        // Invoke logging call
        mLogger.logGetTopicsReportedStats(TOPICS_REPORTED_STATS_DATA);

        // Verify only compat logging took place
        ExtendedMockito.verify(
                () ->
                        AdServicesStatsLog.write(
                                TOPICS_REPORTED_COMPAT_ATOM_ID,
                                FILTERED_BLOCKED_TOPIC_COUNT,
                                DUPLICATE_TOPIC_COUNT,
                                TOPIC_IDS_COUNT));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogGetTopicsReportedStats_sMinus_noLoggingDueToKillSwitch() {
        // Mocks
        when(mFlags.getCompatLoggingKillSwitch()).thenReturn(true);
        ExtendedMockito.doReturn(false).when(SdkLevel::isAtLeastT);

        // Invoke logging call
        mLogger.logGetTopicsReportedStats(TOPICS_REPORTED_STATS_DATA);

        // No compat (and T+) logging should happen
        verifyZeroInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogEpochComputationClassifierStats_tPlus() {
        // Mocks
        when(mFlags.getCompatLoggingKillSwitch()).thenReturn(false);
        ExtendedMockito.doReturn(true).when(SdkLevel::isAtLeastT);
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        any(byte[].class),
                                        anyInt(),
                                        anyString(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt()));
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        any(int[].class),
                                        anyInt(),
                                        anyString(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt()));

        // Invoke logging call
        mLogger.logEpochComputationClassifierStats(EPOCH_COMPUTATION_CLASSIFIER_STATS_DATA);

        // Verify compat logging
        ExtendedMockito.verify(
                () ->
                        AdServicesStatsLog.write(
                                eq(EPOCH_COMPUTATION_CLASSIFIER_COMPAT_ATOM_ID),
                                any(byte[].class), // topic ids converted into byte[]
                                eq(BUILD_ID),
                                eq(ASSET_VERSION),
                                eq(ClassifierType.ON_DEVICE_CLASSIFIER.getCompatLoggingValue()),
                                eq(
                                        OnDeviceClassifierStatus.ON_DEVICE_CLASSIFIER_STATUS_SUCCESS
                                                .getCompatLoggingValue()),
                                eq(
                                        PrecomputedClassifierStatus
                                                .PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED
                                                .getCompatLoggingValue())));
        // Verify T+ logging
        ExtendedMockito.verify(
                () ->
                        AdServicesStatsLog.write(
                                EPOCH_COMPUTATION_CLASSIFIER_ATOM_ID,
                                TOPIC_IDS.stream().mapToInt(Integer::intValue).toArray(),
                                BUILD_ID,
                                ASSET_VERSION,
                                ClassifierType.ON_DEVICE_CLASSIFIER.getLoggingValue(),
                                OnDeviceClassifierStatus.ON_DEVICE_CLASSIFIER_STATUS_SUCCESS
                                        .getLoggingValue(),
                                PrecomputedClassifierStatus
                                        .PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED
                                        .getLoggingValue()));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogEpochComputationClassifierStats_tPlus_noCompatLoggingDueToKillSwitch() {
        // Mocks
        when(mFlags.getCompatLoggingKillSwitch()).thenReturn(true);
        ExtendedMockito.doReturn(true).when(SdkLevel::isAtLeastT);
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        any(int[].class),
                                        anyInt(),
                                        anyString(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt()));

        // Invoke logging call
        mLogger.logEpochComputationClassifierStats(EPOCH_COMPUTATION_CLASSIFIER_STATS_DATA);

        // Verify T+ logging
        ExtendedMockito.verify(
                () ->
                        AdServicesStatsLog.write(
                                EPOCH_COMPUTATION_CLASSIFIER_ATOM_ID,
                                TOPIC_IDS.stream().mapToInt(Integer::intValue).toArray(),
                                BUILD_ID,
                                ASSET_VERSION,
                                ClassifierType.ON_DEVICE_CLASSIFIER.getLoggingValue(),
                                OnDeviceClassifierStatus.ON_DEVICE_CLASSIFIER_STATUS_SUCCESS
                                        .getLoggingValue(),
                                PrecomputedClassifierStatus
                                        .PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED
                                        .getLoggingValue()));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogEpochComputationClassifierStats_sMinus() {
        // Mocks
        when(mFlags.getCompatLoggingKillSwitch()).thenReturn(false);
        ExtendedMockito.doReturn(false).when(SdkLevel::isAtLeastT);
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        any(byte[].class),
                                        anyInt(),
                                        anyString(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt()));

        // Invoke logging call
        mLogger.logEpochComputationClassifierStats(EPOCH_COMPUTATION_CLASSIFIER_STATS_DATA);

        // Verify only compat logging took place
        ExtendedMockito.verify(
                () ->
                        AdServicesStatsLog.write(
                                eq(EPOCH_COMPUTATION_CLASSIFIER_COMPAT_ATOM_ID),
                                any(byte[].class), // topic ids converted into byte[]
                                eq(BUILD_ID),
                                eq(ASSET_VERSION),
                                eq(ClassifierType.ON_DEVICE_CLASSIFIER.getCompatLoggingValue()),
                                eq(
                                        OnDeviceClassifierStatus.ON_DEVICE_CLASSIFIER_STATUS_SUCCESS
                                                .getCompatLoggingValue()),
                                eq(
                                        PrecomputedClassifierStatus
                                                .PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED
                                                .getCompatLoggingValue())));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogEpochComputationClassifierStats_sMinus_noLoggingDueToKillSwitch() {
        // Mocks
        when(mFlags.getCompatLoggingKillSwitch()).thenReturn(true);
        ExtendedMockito.doReturn(false).when(SdkLevel::isAtLeastT);

        // Invoke logging call
        mLogger.logEpochComputationClassifierStats(EPOCH_COMPUTATION_CLASSIFIER_STATS_DATA);

        // No compat (and T+) logging should happen
        verifyZeroInteractions(staticMockMarker(AdServicesStatsLog.class));
    }
}
