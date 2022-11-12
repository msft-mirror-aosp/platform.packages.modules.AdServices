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

package com.android.adservices.service.adselection;

import android.adservices.common.AdSelectionSignals;
import android.annotation.NonNull;
import android.net.Uri;

import com.google.common.util.concurrent.FluentFuture;

import java.util.Map;

/**
 * Interface that selects ad outcome from list of ad outcomes that have been winners of {@link
 * android.adservices.adselection.AdSelectionManager#selectAds}
 */
public interface AdOutcomeSelector {
    /**
     * @param adSelectionIdBidMap list of ad selection id and bid pairs
     * @param selectionSignals signals provided by seller for running ad Selection
     * @param selectionLogicUri uri pointing to the JS logic
     * @return a Future of {@code Long} {code @AdSelectionId} of the winner. If no winner then
     *     returns null
     */
    FluentFuture<Long> runAdOutcomeSelector(
            @NonNull Map<Long, Double> adSelectionIdBidMap,
            @NonNull AdSelectionSignals selectionSignals,
            @NonNull Uri selectionLogicUri);
}
