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

package com.android.adservices.service.signals;

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.service.signals.updateprocessors.UpdateOutput;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessorSelector;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Applies JSON signal updates to the DB. */
public class UpdateProcessingOrchestrator {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @NonNull private final ProtectedSignalsDao mProtectedSignalsDao;
    @NonNull private final UpdateProcessorSelector mUpdateProcessorSelector;

    public UpdateProcessingOrchestrator(
            @NonNull ProtectedSignalsDao protectedSignalsDao,
            @NonNull UpdateProcessorSelector updateProcessorSelector) {
        Objects.requireNonNull(protectedSignalsDao);
        Objects.requireNonNull(updateProcessorSelector);
        mProtectedSignalsDao = protectedSignalsDao;
        mUpdateProcessorSelector = updateProcessorSelector;
    }

    /**
     * Takes a signal update JSON and adds/removes signals based on it.
     *
     * @param json The JSON to process.
     */
    public void processUpdates(
            AdTechIdentifier adtech, String packageName, Instant creationTime, JSONObject json) {
        sLogger.v("Processing signal updates for " + adtech);
        try {
            // Load the current signals, organizing them into a map for quick access
            Map<ByteBuffer, Set<DBProtectedSignal>> currentSignalsMap = getCurrentSignals(adtech);
            /* List of signals to add. Each update processor can append to this and the signals will
             * be added after all the processors are run.
             */
            List<DBProtectedSignal.Builder> signalsToAdd = new ArrayList<>();
            /* List of signals to remove. Each update processor can append to this and the signals
             * will be removed after all the update processors are run.
             */
            List<DBProtectedSignal> signalsToDelete = new ArrayList<>();
            // Running set of keys interacted with, kept to ensure no key is modified twice.
            Set<ByteBuffer> keysTouched = new HashSet<>();

            runProcessors(json, currentSignalsMap, signalsToAdd, signalsToDelete, keysTouched);

            sLogger.v(
                    "Finished parsing JSON %d signals to add, and %d signals to remove",
                    signalsToAdd.size(), signalsToDelete.size());

            writeChanges(adtech, packageName, creationTime, signalsToAdd, signalsToDelete);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Couldn't unpack signal updates JSON", e);
        }
    }

    /**
     * @param adtech The adtech to retrieve signals for.
     * @return A map from keys to the set of all signals associated with that key for the given
     *     buyer.
     */
    private Map<ByteBuffer, Set<DBProtectedSignal>> getCurrentSignals(AdTechIdentifier adtech) {
        Map<ByteBuffer, Set<DBProtectedSignal>> toReturn = new HashMap<>();
        List<DBProtectedSignal> currentSignalsList = mProtectedSignalsDao.getSignalsByBuyer(adtech);
        for (DBProtectedSignal signal : currentSignalsList) {
            ByteBuffer wrappedKey = ByteBuffer.wrap(signal.getKey());
            if (!toReturn.containsKey(wrappedKey)) {
                toReturn.put(wrappedKey, new HashSet<>());
            }
            toReturn.get(wrappedKey).add(signal);
        }
        return toReturn;
    }

    private void runProcessors(
            JSONObject json,
            Map<ByteBuffer, Set<DBProtectedSignal>> currentSignalsMap,
            List<DBProtectedSignal.Builder> signalsToAdd,
            List<DBProtectedSignal> signalsToDelete,
            Set<ByteBuffer> keysTouched)
            throws JSONException {
        sLogger.v("Running update processors");
        // Run each of the update processors
        for (Iterator<String> iter = json.keys(); iter.hasNext(); ) {
            String key = iter.next();
            sLogger.v("Running update processor %s", key);
            JSONObject value = json.getJSONObject(key);
            UpdateOutput output =
                    mUpdateProcessorSelector
                            .getUpdateProcessor(key)
                            .processUpdates(value, currentSignalsMap);
            signalsToAdd.addAll(output.getToAdd());
            signalsToDelete.addAll(output.getToRemove());
            if (!Collections.disjoint(keysTouched, output.getKeysTouched())) {
                throw new IllegalArgumentException(
                        "Updates JSON attempts to perform multiple operations on a single key");
            }
            keysTouched.addAll(output.getKeysTouched());
        }
    }

    private void writeChanges(
            AdTechIdentifier adtech,
            String packageName,
            Instant creationTime,
            List<DBProtectedSignal.Builder> signalsToAdd,
            List<DBProtectedSignal> signalsToDelete) {
        /* Modify the DB based on the output of the update processors. Might be worth skipping
         * this is both signalsToAdd and signalsToDelete are empty.
         */
        mProtectedSignalsDao.insertAndDelete(
                signalsToAdd.stream()
                        .map(
                                builder ->
                                        builder.setBuyer(adtech)
                                                .setPackageName(packageName)
                                                .setCreationTime(creationTime)
                                                .build())
                        .collect(Collectors.toList()),
                signalsToDelete);
    }
}
