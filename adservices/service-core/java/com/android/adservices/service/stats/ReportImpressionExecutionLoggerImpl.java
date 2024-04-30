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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_UNSET;

import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.Flags;

import java.util.Objects;

/**
 * Class for logging the report impression metrics. It provides the functions to collect and log the
 * corresponding report impression process and log the data into the statsd logs. This class collect
 * data for the telemetry atoms:
 *
 * <ul>
 *   <li>ReportImpressionApiCalledStats for API calls
 * </ul>
 */
public final class ReportImpressionExecutionLoggerImpl implements ReportImpressionExecutionLogger {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    private boolean mReportWinBuyerAdditionalSignalsContainedAdCost;
    private boolean mReportWinBuyerAdditionalSignalsContainedDataVersion;
    private boolean mReportResultSellerAdditionalSignalsContainedDataVersion;
    private @AdsRelevanceStatusUtils.JsRunStatus int mReportWinJsScriptResultCode;
    private @AdsRelevanceStatusUtils.JsRunStatus int mReportResultJsScriptResultCode;

    private final AdServicesLogger mAdServicesLogger;
    private final boolean mCPCMetricsEnabled;
    private final boolean mDataHeaderMetricsEnabled;

    public ReportImpressionExecutionLoggerImpl(
            @NonNull AdServicesLogger adServicesLogger, Flags flags) {
        Objects.requireNonNull(adServicesLogger);
        mAdServicesLogger = adServicesLogger;
        mCPCMetricsEnabled = flags.getFledgeCpcBillingMetricsEnabled();
        mDataHeaderMetricsEnabled = flags.getFledgeDataVersionHeaderMetricsEnabled();
        mReportWinJsScriptResultCode = JS_RUN_STATUS_UNSET;
        mReportResultJsScriptResultCode = JS_RUN_STATUS_UNSET;
        sLogger.v("ReportImpressionExecutionLoggerImpl starts.");
    }

    /** Close and log the Report Impression process into AdServicesLogger */
    @Override
    public void logReportImpressionApiCalledStats() {
        sLogger.v("Close ReportImpressionExecutionLogger.");
        sLogger.v("Report Impression process has been logged into AdServicesLogger.");
        mAdServicesLogger.logReportImpressionApiCalledStats(
                ReportImpressionApiCalledStats.builder()
                        .setReportWinBuyerAdditionalSignalsContainedAdCost(
                                mReportWinBuyerAdditionalSignalsContainedAdCost)
                        .setReportWinBuyerAdditionalSignalsContainedDataVersion(
                                mReportWinBuyerAdditionalSignalsContainedDataVersion)
                        .setReportResultSellerAdditionalSignalsContainedDataVersion(
                                mReportResultSellerAdditionalSignalsContainedDataVersion)
                        .setReportWinJsScriptResultCode(mReportWinJsScriptResultCode)
                        .setReportResultJsScriptResultCode(mReportResultJsScriptResultCode)
                        .build());
    }

    /** Sets whether the ReportWin BuyerContextualSignals contained ad cost. */
    @Override
    public void setReportWinBuyerAdditionalSignalsContainedAdCost(
            boolean reportWinBuyerAdditionalSignalsContainedAdCost) {
        if (mCPCMetricsEnabled) {
            mReportWinBuyerAdditionalSignalsContainedAdCost =
                    reportWinBuyerAdditionalSignalsContainedAdCost;
        }
    }

    /** Sets whether the ReportWin BuyerContextualSignals contained data version. */
    @Override
    public void setReportWinBuyerAdditionalSignalsContainedDataVersion(
            boolean reportWinBuyerAdditionalSignalsContainedDataVersion) {
        if (mDataHeaderMetricsEnabled) {
            mReportWinBuyerAdditionalSignalsContainedDataVersion =
                    reportWinBuyerAdditionalSignalsContainedDataVersion;
        }
    }

    /** Specified whether ReportResult the SellerContextualSignals contained data version. */
    @Override
    public void setReportResultSellerAdditionalSignalsContainedDataVersion(
            boolean reportResultSellerAdditionalSignalsContainedDataVersion) {
        if (mDataHeaderMetricsEnabled) {
            mReportResultSellerAdditionalSignalsContainedDataVersion =
                    reportResultSellerAdditionalSignalsContainedDataVersion;
        }
    }

    /** Sets the result code of the buyer JS script. */
    @Override
    public void setReportWinJsScriptResultCode(
            @AdsRelevanceStatusUtils.JsRunStatus int reportWinJsScriptResultCode) {
        mReportWinJsScriptResultCode = reportWinJsScriptResultCode;
    }

    /** Sets the result code of the seller JS script. */
    @Override
    public void setReportResultJsScriptResultCode(
            @AdsRelevanceStatusUtils.JsRunStatus int reportResultJsScriptResultCode) {
        mReportResultJsScriptResultCode = reportResultJsScriptResultCode;
    }
}
