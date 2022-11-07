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

package com.android.adservices.service.stats;

import static com.android.adservices.data.adselection.AdSelectionDatabase.DATABASE_NAME;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.CallerMetadata;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * Class for logging the run ad selection process. It collects and log the corresponding metrics of
 * an ad selection process as well as its subcomponent process persist ad selection into the
 * de-identified WestWorld logs.
 */
public class AdSelectionExecutionLogger extends ApiServiceLatencyCalculator {
    private static final boolean IS_RMKT_ADS_WON_UNSET = false;
    private static final int DB_AD_SELECTION_SIZE_IN_BYTES_UNSET = -1;

    @VisibleForTesting
    static final String REPEATED_START_PERSIST_AD_SELECTION =
            "The logger has already set the start of the persist-ad-selection process.";

    @VisibleForTesting
    static final String REPEATED_END_PERSIST_AD_SELECTION =
            "The logger has already set the end of the persist-ad-selection process.";

    @VisibleForTesting
    static final String MISSING_START_PERSIST_AD_SELECTION =
            "The logger should set the start of the persist-ad-selection process.";

    @VisibleForTesting
    static final String MISSING_END_PERSIST_AD_SELECTION =
            "The logger should set the end of the persist-ad-selection process.";

    @VisibleForTesting
    static final String MISSING_PERSIST_AD_SELECTION =
            "The ad selection execution logger should log the persist-ad-selection process.";

    private final Context mContext;
    private final long mBinderElapsedTimestamp;
    private long mPersistAdSelectionStartTimestamp;
    private long mPersistAdSelectionEndTimestamp;
    private long mDBAdSelectionSizeInBytes;
    private AdServicesLogger mAdServicesLogger;

    public AdSelectionExecutionLogger(
            @NonNull CallerMetadata callerMetadata,
            @NonNull Clock clock,
            @NonNull Context context,
            @NonNull AdServicesLogger adServicesLogger) {
        super(clock);
        Objects.requireNonNull(callerMetadata);
        Objects.requireNonNull(clock);
        Objects.requireNonNull(context);
        Objects.requireNonNull(adServicesLogger);
        this.mBinderElapsedTimestamp = callerMetadata.getBinderElapsedTimestamp();
        this.mContext = context;
        this.mAdServicesLogger = adServicesLogger;
        LogUtil.v("AdSelectionExecutionLogger starts.");
    }

    /** records the start state of a persist-ad-selection process. */
    public void startPersistAdSelection() throws IllegalStateException {
        if (mPersistAdSelectionStartTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_START_PERSIST_AD_SELECTION);
        }
        LogUtil.v("Start the persisting ad selection.");
        this.mPersistAdSelectionStartTimestamp = getServiceElapsedTimestamp();
    }

    /** records the end state of a finished persist-ad-selection process. */
    public void endPersistAdSelection() throws IllegalStateException {
        if (mPersistAdSelectionStartTimestamp == 0L) {
            throw new IllegalStateException(MISSING_START_PERSIST_AD_SELECTION);
        }
        if (mPersistAdSelectionEndTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_END_PERSIST_AD_SELECTION);
        }
        LogUtil.v("Ends the persisting ad selection.");
        this.mPersistAdSelectionEndTimestamp = getServiceElapsedTimestamp();
        this.mDBAdSelectionSizeInBytes = mContext.getDatabasePath(DATABASE_NAME).length();
        LogUtil.v("The persistAdSelection end timestamp is %d:", mPersistAdSelectionEndTimestamp);
        LogUtil.v("The database file size is %d", mDBAdSelectionSizeInBytes);
    }

    /**
     * This method should be called at the end of an ad selection process to generate and log the
     * {@link RunAdSelectionProcessReportedStats} into the {@link AdServicesLogger}.
     */
    public void close(@Nullable DBAdSelection dbAdSelection, int resultCode)
            throws IllegalStateException {
        RunAdSelectionProcessReportedStats runAdSelectionProcessReportedStats =
                getRunAdSelectionProcessReportedStats(
                        dbAdSelection, resultCode, getApiServiceInternalFinalLatencyInMs());
        mAdServicesLogger.logRunAdSelectionProcessReportedStats(runAdSelectionProcessReportedStats);
    }

    /**
     * @return the overall latency in milliseconds of runAdSelection called by the client interface.
     */
    public int getRunAdSelectionOverallLatencyInMs() {
        return getBinderLatencyInMs(mBinderElapsedTimestamp)
                + getApiServiceInternalFinalLatencyInMs();
    }

    private RunAdSelectionProcessReportedStats getRunAdSelectionProcessReportedStats(
            @Nullable DBAdSelection result,
            int runAdSelectionResultCode,
            int runAdSelectionLatencyInMs) {
        if (Objects.isNull(result)) {
            LogUtil.v("Log RunAdSelectionProcessReportedStats for a failed ad selection run.");
            return RunAdSelectionProcessReportedStats.builder()
                    .setIsRemarketingAdsWon(IS_RMKT_ADS_WON_UNSET)
                    .setDBAdSelectionSizeInBytes(DB_AD_SELECTION_SIZE_IN_BYTES_UNSET)
                    .setPersistAdSelectionLatencyInMillis(getPersistAdSelectionLatencyInMs())
                    .setPersistAdSelectionResultCode(
                            getPersistAdSelectionResultCode(runAdSelectionResultCode))
                    .setRunAdSelectionLatencyInMillis(runAdSelectionLatencyInMs)
                    .setRunAdSelectionResultCode(runAdSelectionResultCode)
                    .build();
        } else {
            LogUtil.v(
                    "Log RunAdSelectionProcessReportedStats for a successful ad selection "
                            + "run.");
            if (mPersistAdSelectionStartTimestamp == 0L
                    && mPersistAdSelectionEndTimestamp == 0L
                    && mDBAdSelectionSizeInBytes == 0L) {
                throw new IllegalStateException(MISSING_PERSIST_AD_SELECTION);
            } else if (mPersistAdSelectionEndTimestamp == 0L || mDBAdSelectionSizeInBytes == 0L) {
                throw new IllegalStateException(MISSING_END_PERSIST_AD_SELECTION);
            }
            return RunAdSelectionProcessReportedStats.builder()
                    .setIsRemarketingAdsWon(!Objects.isNull(result.getBiddingLogicUri()))
                    .setDBAdSelectionSizeInBytes((int) mDBAdSelectionSizeInBytes)
                    .setPersistAdSelectionLatencyInMillis(getPersistAdSelectionLatencyInMs())
                    .setPersistAdSelectionResultCode(
                            getPersistAdSelectionResultCode(runAdSelectionResultCode))
                    .setRunAdSelectionLatencyInMillis(runAdSelectionLatencyInMs)
                    .setRunAdSelectionResultCode(runAdSelectionResultCode)
                    .build();
        }
    }

    private int getBinderLatencyInMs(long binderElapsedTimestamp) {
        return (int) (getStartElapsedTimestamp() - binderElapsedTimestamp) * 2;
    }

    private int getPersistAdSelectionResultCode(int resultCode) {
        if (mPersistAdSelectionEndTimestamp > mPersistAdSelectionStartTimestamp) {
            return AdServicesStatusUtils.STATUS_SUCCESS;
        } else if (mPersistAdSelectionStartTimestamp == 0) {
            return AdServicesStatusUtils.STATUS_UNSET;
        }
        return resultCode;
    }

    /**
     * @return the latency in milliseconds of the persist-ad-selection process if succeeded,
     *     otherwise the {@link AdServicesStatusUtils#STATUS_UNSET} if failed.
     */
    private int getPersistAdSelectionLatencyInMs() {
        if (mPersistAdSelectionEndTimestamp <= mPersistAdSelectionStartTimestamp) {
            return AdServicesStatusUtils.STATUS_UNSET;
        }
        return (int) (mPersistAdSelectionEndTimestamp - mPersistAdSelectionStartTimestamp);
    }
}
