/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.adservices.service.signals.evict;

import static android.adservices.common.CommonFixture.VALID_BUYER_1;

import static com.android.adservices.service.signals.SignalsFixture.ID_1;
import static com.android.adservices.service.signals.SignalsFixture.ID_2;
import static com.android.adservices.service.signals.SignalsFixture.KEY_1;
import static com.android.adservices.service.signals.SignalsFixture.KEY_2;
import static com.android.adservices.service.signals.SignalsFixture.VALUE_1;
import static com.android.adservices.service.signals.SignalsFixture.VALUE_2;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.verify;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.service.signals.SignalsFixture;
import com.android.adservices.service.signals.updateprocessors.UpdateOutput;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLogger;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.junit.Test;
import org.mockito.Mock;

import java.time.Instant;
import java.util.List;

@RequiresSdkLevelAtLeastT(reason = "PAS is only supported on T+")
public class PrioritizedFifoSignalEvictorTest extends AdServicesMockitoTestCase {
    @Mock private UpdateSignalsProcessReportedLogger mUpdateSignalsProcessReportedLoggerMock;

    PrioritizedFifoSignalEvictor mPrioritizedFifoSignalEvictor = new PrioritizedFifoSignalEvictor();

    @Test
    public void testEvict_lowerPriorityCreatedEarlier_evictedFirst() {
        DBProtectedSignal lowerPriorityCreatedEarlier =
                SignalsFixture.createSignal(
                        KEY_1,
                        VALUE_1,
                        ID_1,
                        Instant.ofEpochMilli(100L),
                        EvictionPriority.EVICT_SOONER);
        DBProtectedSignal higherPriorityCreatedLater =
                SignalsFixture.createSignal(
                        KEY_2,
                        VALUE_2,
                        ID_2,
                        Instant.ofEpochMilli(200L),
                        EvictionPriority.EVICT_LATER);

        List<DBProtectedSignal> signals =
                Lists.newArrayList(lowerPriorityCreatedEarlier, higherPriorityCreatedLater);
        UpdateOutput updateOutput = new UpdateOutput();

        assertWithMessage("Eviction result")
                .that(
                        mPrioritizedFifoSignalEvictor.evict(
                                VALID_BUYER_1,
                                signals,
                                updateOutput,
                                SignalSizeCalculator.calculate(higherPriorityCreatedLater) + 1,
                                SignalSizeCalculator.calculate(signals) - 1,
                                mUpdateSignalsProcessReportedLoggerMock))
                .isTrue();

        expect.withMessage("toRemove")
                .that(updateOutput.getToRemove())
                .containsExactly(lowerPriorityCreatedEarlier);
        List<DBProtectedSignal> expectedSignalsAfterEviction =
                ImmutableList.of(higherPriorityCreatedLater);
        verifyUpdateSignalsProcessReportedLoggerArguments(
                1,
                SignalSizeCalculator.calculate(expectedSignalsAfterEviction),
                SignalSizeCalculator.maxSignalsSizeBytes(expectedSignalsAfterEviction),
                SignalSizeCalculator.minSignalsSizeBytes(expectedSignalsAfterEviction));
    }

    @Test
    public void testEvict_lowerPriorityCreatedLater_evictedFirst() {
        DBProtectedSignal lowerPriorityCreatedLater =
                SignalsFixture.createSignal(
                        KEY_1,
                        VALUE_1,
                        ID_1,
                        Instant.ofEpochMilli(200L),
                        EvictionPriority.EVICT_SOONER);
        DBProtectedSignal higherPriorityCreatedEarlier =
                SignalsFixture.createSignal(
                        KEY_2,
                        VALUE_2,
                        ID_2,
                        Instant.ofEpochMilli(100L),
                        EvictionPriority.EVICT_LATER);

        List<DBProtectedSignal> signals =
                Lists.newArrayList(lowerPriorityCreatedLater, higherPriorityCreatedEarlier);
        UpdateOutput updateOutput = new UpdateOutput();

        assertWithMessage("Eviction result")
                .that(
                        mPrioritizedFifoSignalEvictor.evict(
                                VALID_BUYER_1,
                                signals,
                                updateOutput,
                                SignalSizeCalculator.calculate(higherPriorityCreatedEarlier) + 1,
                                SignalSizeCalculator.calculate(signals) - 1,
                                mUpdateSignalsProcessReportedLoggerMock))
                .isTrue();

        expect.withMessage("toRemove")
                .that(updateOutput.getToRemove())
                .containsExactly(lowerPriorityCreatedLater);
        List<DBProtectedSignal> expectedSignalsAfterEviction =
                ImmutableList.of(higherPriorityCreatedEarlier);
        verifyUpdateSignalsProcessReportedLoggerArguments(
                1,
                SignalSizeCalculator.calculate(expectedSignalsAfterEviction),
                SignalSizeCalculator.maxSignalsSizeBytes(expectedSignalsAfterEviction),
                SignalSizeCalculator.minSignalsSizeBytes(expectedSignalsAfterEviction));
    }

    @Test
    public void testEvict_samePriorityCreatedEarlier_evictedFirst() {
        DBProtectedSignal samePriorityCreatedEarlier =
                SignalsFixture.createSignal(
                        KEY_1,
                        VALUE_1,
                        ID_1,
                        Instant.ofEpochMilli(100L),
                        EvictionPriority.EVICT_SOONER);
        DBProtectedSignal samePriorityCreatedLater =
                SignalsFixture.createSignal(
                        KEY_2,
                        VALUE_2,
                        ID_2,
                        Instant.ofEpochMilli(200L),
                        EvictionPriority.EVICT_SOONER);

        List<DBProtectedSignal> signals =
                Lists.newArrayList(samePriorityCreatedEarlier, samePriorityCreatedLater);
        UpdateOutput updateOutput = new UpdateOutput();

        assertWithMessage("Eviction result")
                .that(
                        mPrioritizedFifoSignalEvictor.evict(
                                VALID_BUYER_1,
                                signals,
                                updateOutput,
                                SignalSizeCalculator.calculate(samePriorityCreatedLater) + 1,
                                SignalSizeCalculator.calculate(signals) - 1,
                                mUpdateSignalsProcessReportedLoggerMock))
                .isTrue();

        expect.withMessage("toRemove")
                .that(updateOutput.getToRemove())
                .containsExactly(samePriorityCreatedEarlier);
        List<DBProtectedSignal> expectedSignalsAfterEviction =
                ImmutableList.of(samePriorityCreatedLater);
        verifyUpdateSignalsProcessReportedLoggerArguments(
                1,
                SignalSizeCalculator.calculate(expectedSignalsAfterEviction),
                SignalSizeCalculator.maxSignalsSizeBytes(expectedSignalsAfterEviction),
                SignalSizeCalculator.minSignalsSizeBytes(expectedSignalsAfterEviction));
    }

    @Test
    public void testEvict_signalSizeBelowMax_isNotEvicted() {
        DBProtectedSignal signal =
                SignalsFixture.createSignal(
                        KEY_1,
                        VALUE_1,
                        ID_1,
                        Instant.ofEpochMilli(200L),
                        EvictionPriority.EVICT_SOONER);
        int signalSize = SignalSizeCalculator.calculate(signal);

        List<DBProtectedSignal> signals = Lists.newArrayList(signal);
        UpdateOutput updateOutput = new UpdateOutput();

        assertWithMessage("Eviction result")
                .that(
                        mPrioritizedFifoSignalEvictor.evict(
                                VALID_BUYER_1,
                                signals,
                                updateOutput,
                                signalSize,
                                signalSize + 1,
                                mUpdateSignalsProcessReportedLoggerMock))
                .isFalse();

        expect.withMessage("toRemove").that(updateOutput.getToRemove()).isEmpty();
        verifyUpdateSignalsProcessReportedLoggerArguments(
                0,
                signalSize,
                SignalSizeCalculator.maxSignalsSizeBytes(signals),
                SignalSizeCalculator.minSignalsSizeBytes(signals));
    }

    @Test
    public void testEvict_signalSizeBetweenMaxAndOversubscribe_isNotEvicted() {
        DBProtectedSignal signal =
                SignalsFixture.createSignal(
                        KEY_1,
                        VALUE_1,
                        ID_1,
                        Instant.ofEpochMilli(200L),
                        EvictionPriority.EVICT_SOONER);
        int signalSize = SignalSizeCalculator.calculate(signal);

        List<DBProtectedSignal> signals = Lists.newArrayList(signal);
        UpdateOutput updateOutput = new UpdateOutput();

        assertWithMessage("Eviction result")
                .that(
                        mPrioritizedFifoSignalEvictor.evict(
                                VALID_BUYER_1,
                                signals,
                                updateOutput,
                                signalSize - 1,
                                signalSize + 1,
                                mUpdateSignalsProcessReportedLoggerMock))
                .isFalse();

        expect.withMessage("toRemove").that(updateOutput.getToRemove()).isEmpty();
        verifyUpdateSignalsProcessReportedLoggerArguments(
                0,
                signalSize,
                SignalSizeCalculator.maxSignalsSizeBytes(signals),
                SignalSizeCalculator.minSignalsSizeBytes(signals));
    }

    // TODO: b/402995096 - Verify new eviction metrics are set here once available.
    private void verifyUpdateSignalsProcessReportedLoggerArguments(
            int evictionRulesCount,
            int perBuyerSignalSize,
            float maxRawProtectedSignalsSizeBytes,
            float minRawProtectedSignalsSizeBytes) {
        verify(mUpdateSignalsProcessReportedLoggerMock).setEvictionRulesCount(evictionRulesCount);
        verify(mUpdateSignalsProcessReportedLoggerMock).setPerBuyerSignalSize(perBuyerSignalSize);
        verify(mUpdateSignalsProcessReportedLoggerMock)
                .setMaxRawProtectedSignalsSizeBytes(maxRawProtectedSignalsSizeBytes);
        verify(mUpdateSignalsProcessReportedLoggerMock)
                .setMinRawProtectedSignalsSizeBytes(minRawProtectedSignalsSizeBytes);
    }
}
