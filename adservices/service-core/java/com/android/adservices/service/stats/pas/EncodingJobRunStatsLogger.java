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

package com.android.adservices.service.stats.pas;

import com.android.adservices.service.stats.AdsRelevanceStatusUtils;

public interface EncodingJobRunStatsLogger {
    /** Invokes the logger to log {@link EncodingJobRunStats}. */
    void logEncodingJobRunStats();

    /**
     * Adds one count to the count of signal encoding failures when catching an exception during
     * encoding registered buyers.
     */
    void addOneSignalEncodingFailures();

    /**
     * Adds one count to the count of signal encoding failures when skipping the encoding registered
     * buyers.
     */
    void addOneSignalEncodingSkips();

    /**
     * Sets the count of signal encoding successes by given size of filtered buyer encoding list.
     */
    void setSizeOfFilteredBuyerEncodingList(int sizeOfFilteredBuyerEncodingList);

    /** Resets count of signal encoding status and sets the PAS encoding source type. */
    void resetStatsWithEncodingSourceType(
            @AdsRelevanceStatusUtils.PasEncodingSourceType int encodingSourceType);
}
