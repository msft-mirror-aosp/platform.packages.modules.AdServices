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

package com.android.adservices.service.stats;

public interface SelectAdsFromOutcomesExecutionLogger extends JsScriptExecutionLogger {
    /** Sets the timestamp of the download script start. */
    default void startDownloadScriptTimestamp() {
        // do nothing
    }

    /** Sets the timestamp of the download script end and stores the result code. */
    default void endDownloadScriptTimestamp(int resultCode) {
        // do nothing
    }

    /** Sets the timestamp of the execution script start. */
    default void startExecutionScriptTimestamp() {
        // do nothing
    }

    /** Sets the timestamp of the execution script end and stores the result code. */
    default void endExecutionScriptTimestamp(@AdsRelevanceStatusUtils.JsRunStatus int resultCode) {
        // do nothing
    }

    /** Sets the count ids. */
    default void setCountIds(int countIds) {
        // do nothing
    }

    /** Sets the count non existing ids */
    default void setCountNonExistingIds(int countNonExistingIds) {
        // do nothing
    }

    /** Sets whether the JS script is a prebuilt. */
    default void setUsedPrebuilt(boolean usedPrebuilt) {
        // do nothing
    }

    /** Invokes the logger to log {@link SelectAdsFromOutcomesApiCalledStats}. */
    default void logSelectAdsFromOutcomesApiCalledStats() {
        // do nothing
    }
}
