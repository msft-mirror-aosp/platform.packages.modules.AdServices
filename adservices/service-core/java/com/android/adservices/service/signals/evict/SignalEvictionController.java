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

package com.android.adservices.service.signals.evict;

import android.adservices.common.AdTechIdentifier;

import androidx.annotation.VisibleForTesting;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.service.signals.updateprocessors.UpdateOutput;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLogger;

import com.google.common.collect.ImmutableList;

import java.util.List;

/** Controller to run a series of {@link SignalEvictor}s in a water fall modal. */
public class SignalEvictionController {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private final int mMaxAllowedSignalSize;
    private final int mMaxAllowedSignalSizeWithOversubscription;
    private final List<SignalEvictor> mSignalEvictors;

    public SignalEvictionController(
            int maxAllowedSignalSize,
            int maxAllowedSignalSizeWithOversubscription,
            boolean enablePrioritizedEviction) {
        mMaxAllowedSignalSize = maxAllowedSignalSize;
        mMaxAllowedSignalSizeWithOversubscription = maxAllowedSignalSizeWithOversubscription;
        mSignalEvictors = getSignalEvictors(enablePrioritizedEviction);
    }

    @VisibleForTesting
    public SignalEvictionController(
            List<SignalEvictor> evictors,
            int maxAllowedSignalSize,
            int maxAllowedSignalSizeWithOversubscription) {
        mSignalEvictors = evictors;
        mMaxAllowedSignalSize = maxAllowedSignalSize;
        mMaxAllowedSignalSizeWithOversubscription = maxAllowedSignalSizeWithOversubscription;
    }

    /**
     * Run signal eviction with a waterfall module of defined evictors. Skips the following evictors
     * if the previous evictor takes no action (returns false).
     */
    public void evict(
            AdTechIdentifier adTech,
            List<DBProtectedSignal> updatedSignals,
            UpdateOutput combinedUpdates,
            UpdateSignalsProcessReportedLogger updateSignalsProcessReportedLogger) {
        sLogger.v("Start running signal eviction.");
        for (SignalEvictor evictor : mSignalEvictors) {
            // TODO: b/402995096 - Set evictorsUsed metric here once available.
            if (!evictor.evict(
                    adTech,
                    updatedSignals,
                    combinedUpdates,
                    mMaxAllowedSignalSize,
                    mMaxAllowedSignalSizeWithOversubscription,
                    updateSignalsProcessReportedLogger)) {
                sLogger.v("Eviction finished.");
                break;
            }
        }
    }

    private static List<SignalEvictor> getSignalEvictors(boolean enablePrioritizedEviction) {
        if (enablePrioritizedEviction) {
            return ImmutableList.of(new PrioritizedFifoSignalEvictor());
        } else {
            return ImmutableList.of(new FifoSignalEvictor());
        }
    }
}
