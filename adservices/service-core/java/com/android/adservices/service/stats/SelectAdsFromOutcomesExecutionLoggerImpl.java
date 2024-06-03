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

import static com.android.adservices.service.stats.AdServicesLoggerUtil.FIELD_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_UNSET;

import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.shared.util.Clock;

import java.util.Objects;

/**
 * Class for logging the select ads from outcomes metrics. It provides the functions to collect and
 * log the corresponding select ads from outcomes process and log the data into the statsd logs.
 * This class collect data for the telemetry atoms:
 *
 * <ul>
 *   <li>SelectAdsFromOutcomesApiCalledStats for API calls
 * </ul>
 *
 * <p>Each complete process should start the stopwatch immediately on construction this Logger
 * object, and call its corresponding end method to record its states and log the generated atom
 * proto into the statsd logger.
 */
public class SelectAdsFromOutcomesExecutionLoggerImpl
        implements SelectAdsFromOutcomesExecutionLogger {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    private long mDownloadScriptStartTimestamp;
    private long mDownloadScriptEndTimestamp;
    private boolean mIsDownloadLatencyAvailable;
    private int mDownloadScriptResultCode;
    private long mExecutionScriptStartTimestamp;
    private long mExecutionScriptEndTimestamp;
    private boolean mIsExecutionLatencyAvailable;
    private @AdsRelevanceStatusUtils.JsRunStatus int mExecutionScriptResultCode;

    private int mCountIds;
    private int mCountNonExistingIds;
    private boolean mUsedPrebuilt;
    private final Clock mClock;
    private final AdServicesLogger mAdServicesLogger;

    public SelectAdsFromOutcomesExecutionLoggerImpl(
            @NonNull Clock clock, @NonNull AdServicesLogger adServicesLogger) {
        Objects.requireNonNull(clock);
        Objects.requireNonNull(adServicesLogger);
        mClock = clock;
        mAdServicesLogger = adServicesLogger;
        mIsDownloadLatencyAvailable = false;
        mIsExecutionLatencyAvailable = false;
        mDownloadScriptResultCode = FIELD_UNSET;
        mExecutionScriptResultCode = JS_RUN_STATUS_UNSET;
        sLogger.v("SelectAdsFromOutcomesExecutionLoggerImpl starts.");
    }

    /** Start the script download process. */
    @Override
    public void startDownloadScriptTimestamp() {
        sLogger.v("Start logging the SelectAdsFromOutcomes script download process.");
        mDownloadScriptStartTimestamp = mClock.elapsedRealtime();
    }

    /** End the script download process. */
    @Override
    public void endDownloadScriptTimestamp(int resultCode) {
        sLogger.v("End logging the SelectAdsFromOutcomes script download process.");
        mIsDownloadLatencyAvailable = true;
        if (mDownloadScriptStartTimestamp == 0L) {
            sLogger.e(
                    "The logger should set the start of select ads from outcomes script download.");
            mIsDownloadLatencyAvailable = false;
        }
        if (mDownloadScriptEndTimestamp > 0L) {
            sLogger.e(
                    "The logger has already set the end of select ads from outcomes script"
                            + " download.");
            mIsDownloadLatencyAvailable = false;
        }
        mDownloadScriptEndTimestamp = mClock.elapsedRealtime();
        mDownloadScriptResultCode = resultCode;
    }

    /** Start the script execution process. */
    @Override
    public void startExecutionScriptTimestamp() {
        sLogger.v("Start logging the SelectAdsFromOutcomes script execution process.");
        mExecutionScriptStartTimestamp = mClock.elapsedRealtime();
    }

    /** End the script execution process. */
    @Override
    public void endExecutionScriptTimestamp(@AdsRelevanceStatusUtils.JsRunStatus int resultCode) {
        sLogger.v("End logging the SelectAdsFromOutcomes script execution process.");
        mIsExecutionLatencyAvailable = true;
        if (mExecutionScriptStartTimestamp == 0L) {
            sLogger.e(
                    "The logger should set the start of select ads from outcomes script"
                            + " execution.");
            mIsExecutionLatencyAvailable = false;
        }
        if (mExecutionScriptEndTimestamp > 0L) {
            sLogger.e(
                    "The logger has already set the end of select ads from outcomes script"
                            + " execution.");
            mIsExecutionLatencyAvailable = false;
        }
        mExecutionScriptEndTimestamp = mClock.elapsedRealtime();
        mExecutionScriptResultCode = resultCode;
    }

    /**
     * Close and log the select ads from outcome process into AdServicesLogger once the network call
     * finished.
     */
    @Override
    public void logSelectAdsFromOutcomesApiCalledStats() {
        sLogger.v("Close SelectAdsFromOutcomesExecutionLogger for network source.");

        int downloadScriptLatency =
                mIsDownloadLatencyAvailable
                        ? (int) (mDownloadScriptEndTimestamp - mDownloadScriptStartTimestamp)
                        : FIELD_UNSET;

        int executionScriptLatency =
                mIsExecutionLatencyAvailable
                        ? (int) (mExecutionScriptEndTimestamp - mExecutionScriptStartTimestamp)
                        : FIELD_UNSET;
        sLogger.v("Select Ads from outcomes process has been logged into AdServicesLogger.");
        mAdServicesLogger.logSelectAdsFromOutcomesApiCalledStats(
                SelectAdsFromOutcomesApiCalledStats.builder()
                        .setCountIds(mCountIds)
                        .setCountNonExistingIds(mCountNonExistingIds)
                        .setUsedPrebuilt(mUsedPrebuilt)
                        .setDownloadResultCode(mDownloadScriptResultCode)
                        .setDownloadLatencyMillis(downloadScriptLatency)
                        .setExecutionResultCode(mExecutionScriptResultCode)
                        .setExecutionLatencyMillis(executionScriptLatency)
                        .build());
    }

    /** Sets the number of IDs passed to the mediation call */
    @Override
    public void setCountIds(int countIds) {
        mCountIds = countIds;
    }

    /** Sets the number of non-existing IDs during mediation call */
    @Override
    public void setCountNonExistingIds(int countNonExistingIds) {
        mCountNonExistingIds = countNonExistingIds;
    }

    /** Sets whether the truncation API call used a prebuilt script */
    @Override
    public void setUsedPrebuilt(boolean usedPrebuilt) {
        mUsedPrebuilt = usedPrebuilt;
    }
}
