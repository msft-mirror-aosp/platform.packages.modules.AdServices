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

import android.adservices.common.AdTechIdentifier;

import androidx.annotation.VisibleForTesting;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.service.signals.updateprocessors.UpdateOutput;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLogger;

import java.util.Comparator;
import java.util.List;

/** Signal Evictor based on creation time. */
public class PrioritizedFifoSignalEvictor implements SignalEvictor {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting
    static final Comparator<DBProtectedSignal> LOWEST_EVICTION_PRIORITY_THEN_EARLIEST_CREATED =
            Comparator.comparing(DBProtectedSignal::getEvictionPriority)
                    .thenComparing(DBProtectedSignal::getCreationTime);

    /**
     * {@inheritDoc} Triggers eviction if and only if total size exceeding the oversubscription
     * policy (hard limit).
     *
     * <p>Removes signal with the lowest priority and oldest creation time (in that order) from the
     * signal list and adds to the to remove list in the {@code combinedUpdates} until the total
     * size of signals fall below the {@code maxAllowedSignalSize}.
     */
    @Override
    public boolean evict(
            AdTechIdentifier adTechIdentifier,
            List<DBProtectedSignal> updatedSignals,
            UpdateOutput combinedUpdates,
            int maxAllowedSignalSize,
            int maxAllowedSignalSizeWithOversubscription,
            UpdateSignalsProcessReportedLogger updateSignalsProcessReportedLogger) {
        sLogger.v("Start prioritized FIFO eviction.");
        int currentSignalSize = SignalSizeCalculator.calculate(updatedSignals);
        int numSignalsToEvict = 0;
        int initialSignalSize = currentSignalSize;

        if (currentSignalSize <= maxAllowedSignalSizeWithOversubscription) {
            sLogger.v("Signal size within the limit, skipping the prioritized FIFO eviction.");
            setUpdateSignalsProcessReportedLoggerValues(
                    updateSignalsProcessReportedLogger,
                    numSignalsToEvict,
                    currentSignalSize,
                    SignalSizeCalculator.maxSignalsSizeBytes(updatedSignals),
                    SignalSizeCalculator.minSignalsSizeBytes(updatedSignals),
                    initialSignalSize - currentSignalSize);
            return false;
        }

        updatedSignals.sort(LOWEST_EVICTION_PRIORITY_THEN_EARLIEST_CREATED);

        while (currentSignalSize > maxAllowedSignalSize
                && numSignalsToEvict < updatedSignals.size()) {
            DBProtectedSignal toEvictSignal = updatedSignals.get(numSignalsToEvict);
            currentSignalSize -= SignalSizeCalculator.calculate(toEvictSignal);
            numSignalsToEvict++;
            updateSignalsProcessReportedLogger.addEvictedSignalEvictionPriority(
                    toEvictSignal.getEvictionPriority());
        }

        combinedUpdates.getToRemove().addAll(updatedSignals.subList(0, numSignalsToEvict));

        List<DBProtectedSignal> updatedSignalsAfterEviction =
                updatedSignals.subList(numSignalsToEvict, updatedSignals.size());

        setUpdateSignalsProcessReportedLoggerValues(
                updateSignalsProcessReportedLogger,
                numSignalsToEvict,
                currentSignalSize,
                SignalSizeCalculator.maxSignalsSizeBytes(updatedSignalsAfterEviction),
                SignalSizeCalculator.minSignalsSizeBytes(updatedSignalsAfterEviction),
                initialSignalSize - currentSignalSize);

        sLogger.v(
                "Finished prioritized FIFO signal Eviction, %d signals to add, and %d signals to"
                        + " remove",
                combinedUpdates.getToAddSize(), combinedUpdates.getToRemoveSize());
        return true;
    }

    // TODO: b/402995096 - Set new eviction metrics here once available.
    private void setUpdateSignalsProcessReportedLoggerValues(
            UpdateSignalsProcessReportedLogger updateSignalsProcessReportedLogger,
            int evictionRulesCount,
            int currentSignalSize,
            float maxRawProtectedSignalsSizeBytes,
            float minRawProtectedSignalsSizeBytes,
            int totalEvictedSignalSize) {
        updateSignalsProcessReportedLogger.setEvictionRulesCount(evictionRulesCount);
        updateSignalsProcessReportedLogger.setPerBuyerSignalSize(currentSignalSize);

        updateSignalsProcessReportedLogger.setMaxRawProtectedSignalsSizeBytes(
                maxRawProtectedSignalsSizeBytes);
        updateSignalsProcessReportedLogger.setMinRawProtectedSignalsSizeBytes(
                minRawProtectedSignalsSizeBytes);
        updateSignalsProcessReportedLogger.setPerBuyerEvictedSignalSize(totalEvictedSignalSize);
    }
}
