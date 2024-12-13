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

import android.adservices.common.AdTechIdentifier;

import com.android.adservices.service.stats.AdsRelevanceStatusUtils;

public interface EncodingExecutionLogHelper {
    /** Start the clock for timing the js execution. */
    void startClock();

    /** Set the status of the JS run. */
    void setStatus(@AdsRelevanceStatusUtils.JsRunStatus int status);

    /**
     * Log the adtech whose JS is being run. This could be made more efficient by passing the
     * enrollment id around with the ETLD+1 instead of having to retrieve it every time.
     */
    void setAdtech(AdTechIdentifier adtech);

    /** Set the size of encoded signal size in bytes. */
    void setEncodedSignalSize(int encodedSignalSize);

    /**
     * Finish the timer, and log the metric if not already finished. If already finished, do
     * nothing.
     */
    void finish();
}
