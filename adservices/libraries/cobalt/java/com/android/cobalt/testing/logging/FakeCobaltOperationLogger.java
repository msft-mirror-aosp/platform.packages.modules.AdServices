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

import com.android.cobalt.logging.CobaltOperationLogger;

import java.util.HashMap;
import java.util.Map;

/** An operation logger that doesn't log. */
public final class FakeCobaltOperationLogger implements CobaltOperationLogger {

    private final Map<Pair<Integer, Integer>, Integer> mStringBufferMaxExceededOccurrences;
    private final Map<Pair<Integer, Integer>, Integer> mEventVectorBufferMaxExceededOccurrences;
    private final Map<Pair<Integer, Integer>, Integer> mMaxValueExceededOccurrences;
    private int mUploadFailureOccurrences;
    private int mUploadSuccessOccurrences;

    public FakeCobaltOperationLogger() {
        mStringBufferMaxExceededOccurrences = new HashMap<Pair<Integer, Integer>, Integer>();
        mEventVectorBufferMaxExceededOccurrences = new HashMap<Pair<Integer, Integer>, Integer>();
        mMaxValueExceededOccurrences = new HashMap<Pair<Integer, Integer>, Integer>();
        mUploadFailureOccurrences = 0;
        mUploadSuccessOccurrences = 0;
    }

    /**
     * NoOp logs that a Cobalt logging event exceeds the string buffer max. Increments the
     * occurrences of string buffer max was exceeded for (metricId, reportId).
     */
    public void logStringBufferMaxExceeded(int metricId, int reportId) {
        Pair<Integer, Integer> key = new Pair<Integer, Integer>(metricId, reportId);
        mStringBufferMaxExceededOccurrences.compute(key, (k, v) -> (v == null) ? 1 : v + 1);
    }

    /** Returns the total occurrences of string buffer max was exceeded for (metricId, reportId). */
    public int getNumStringBufferMaxExceededOccurrences(int metricId, int reportId) {
        return mStringBufferMaxExceededOccurrences.getOrDefault(
                new Pair<Integer, Integer>(metricId, reportId), 0);
    }

    /**
     * NoOp logs that a Cobalt logging event exceeds the event vector buffer max. Increments the
     * occurrences of event vector buffer max was exceeded for (metricId, reportId).
     */
    public void logEventVectorBufferMaxExceeded(int metricId, int reportId) {
        Pair<Integer, Integer> key = new Pair<Integer, Integer>(metricId, reportId);
        mEventVectorBufferMaxExceededOccurrences.compute(key, (k, v) -> (v == null) ? 1 : v + 1);
    }

    /**
     * Returns the total occurrences of event vector buffer max was exceeded for (metricId,
     * reportId).
     */
    public int getNumEventVectorBufferMaxExceededOccurrences(int metricId, int reportId) {
        return mEventVectorBufferMaxExceededOccurrences.getOrDefault(
                new Pair<Integer, Integer>(metricId, reportId), 0);
    }

    /**
     * NoOp logs that a Cobalt logging event exceeds the max value when calculating its private
     * index. Increments the occurrences of max value was exceeded for (metricId, reportId).
     */
    public void logMaxValueExceeded(int metricId, int reportId) {
        Pair<Integer, Integer> key = new Pair<Integer, Integer>(metricId, reportId);
        mMaxValueExceededOccurrences.compute(key, (k, v) -> (v == null) ? 1 : v + 1);
    }

    /** Returns the total occurrences of max value was exceeded for (metricId, reportId). */
    public int getNumMaxValueExceededOccurrences(int metricId, int reportId) {
        return mMaxValueExceededOccurrences.getOrDefault(
                new Pair<Integer, Integer>(metricId, reportId), 0);
    }

    /**
     * NoOp logs that Cobalt periodical job failed to upload observations. Increments the
     * occurrences of upload failure.
     */
    public void logUploadFailure() {
        mUploadFailureOccurrences++;
    }

    /** Returns the total occurrences of upload failure. */
    public int getNumUploadFailureOccurrences() {
        return mUploadFailureOccurrences;
    }

    /**
     * NoOp logs that Cobalt periodical job upload observations successfully. Increments the
     * occurrences of upload success.
     */
    public void logUploadSuccess() {
        mUploadSuccessOccurrences++;
    }

    /** Returns the total occurrences of upload success. */
    public int getNumUploadSuccessOccurrences() {
        return mUploadSuccessOccurrences;
    }
}
