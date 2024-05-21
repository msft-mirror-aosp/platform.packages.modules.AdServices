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

public interface ReportImpressionExecutionLogger extends JsScriptExecutionLogger {

    /** Sets whether the ReportWin BuyerContextualSignals contained ad cost. */
    void setReportWinBuyerAdditionalSignalsContainedAdCost(
            boolean reportWinBuyerAdditionalSignalsContainedAdCost);

    /** Sets whether the ReportWin BuyerContextualSignals contained data version. */
    void setReportWinBuyerAdditionalSignalsContainedDataVersion(
            boolean reportWinBuyerAdditionalSignalsContainedDataVersion);

    /** Specified whether ReportResult the SellerContextualSignals contained data version. */
    void setReportResultSellerAdditionalSignalsContainedDataVersion(
            boolean reportResultSellerAdditionalSignalsContainedDataVersion);

    /** Sets the result code of the buyer JS script. */
    void setReportWinJsScriptResultCode(
            @AdsRelevanceStatusUtils.JsRunStatus int reportWinJsScriptResultCode);

    /** Sets the result code of the seller JS script. */
    void setReportResultJsScriptResultCode(
            @AdsRelevanceStatusUtils.JsRunStatus int reportResultJsScriptResultCode);

    /** Invokes the logger to log {@link ReportImpressionApiCalledStats}. */
    void logReportImpressionApiCalledStats();
}
