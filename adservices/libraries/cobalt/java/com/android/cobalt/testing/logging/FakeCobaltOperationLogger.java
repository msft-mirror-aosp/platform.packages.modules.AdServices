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

package com.android.cobalt.testing.logging;

import android.util.Pair;

import androidx.annotation.VisibleForTesting;

import com.android.cobalt.logging.CobaltOperationLogger;

import java.util.HashMap;
import java.util.Map;

/** An operation logger that doesn't log. */
public final class FakeCobaltOperationLogger implements CobaltOperationLogger {

    private final Map<Pair<Integer, Integer>, Integer> mStringBufferMaxExceededOccurrences;

    public FakeCobaltOperationLogger() {
        mStringBufferMaxExceededOccurrences = new HashMap<Pair<Integer, Integer>, Integer>();
    }

    /**
     * NoOp log that a Cobalt logging event exceeds the string buffer max. Increment the count of
     * this function being called for (metricId, reportId).
     */
    @VisibleForTesting
    public void logStringBufferMaxExceeded(int metricId, int reportId) {
        Pair<Integer, Integer> key = new Pair<Integer, Integer>(metricId, reportId);
        mStringBufferMaxExceededOccurrences.compute(key, (k, v) -> (v == null) ? 1 : v + 1);
    }

    /** Returns the total occurrences of string buffer max was exceed for (metricId, reportId). */
    public int getNumStringBufferMaxExceededOccurrences(int metricId, int reportId) {
        return mStringBufferMaxExceededOccurrences.getOrDefault(
                new Pair<Integer, Integer>(metricId, reportId), 0);
    }
}
