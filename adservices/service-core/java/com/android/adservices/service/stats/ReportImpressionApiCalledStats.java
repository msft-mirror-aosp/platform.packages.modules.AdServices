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

import com.google.auto.value.AutoValue;

/** Class for ReportImpression API Called stats. */
@AutoValue
public abstract class ReportImpressionApiCalledStats {

    /** Whether the BuyerContextualSignals contained ad cost. */
    public abstract boolean getReportWinBuyerAdditionalSignalsContainedAdCost();

    /** Whether the BuyerContextualSignals contained data version. */
    public abstract boolean getReportWinBuyerAdditionalSignalsContainedDataVersion();

    /** Whether the SellerContextualSignals contained data version. */
    public abstract boolean getReportResultSellerAdditionalSignalsContainedDataVersion();

    /** Result code of the buyer JS script (reportWin). */
    @AdsRelevanceStatusUtils.JsRunStatus
    public abstract int getReportWinJsScriptResultCode();

    /** Result code of the seller JS script (reportResult). */
    @AdsRelevanceStatusUtils.JsRunStatus
    public abstract int getReportResultJsScriptResultCode();

    /** Returns a generic builder. */
    public static Builder builder() {
        return new AutoValue_ReportImpressionApiCalledStats.Builder()
                .setReportWinJsScriptResultCode(JS_RUN_STATUS_UNSET)
                .setReportResultJsScriptResultCode(JS_RUN_STATUS_UNSET);
    }

    /** Builder class for ReportImpressionApiCalledStats. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets whether the BuyerContextualSignals contained ad cost. */
        public abstract Builder setReportWinBuyerAdditionalSignalsContainedAdCost(
                boolean reportWinBuyerAdditionalSignalsContainedAdCost);

        /** Sets whether the BuyerContextualSignals contained data version. */
        public abstract Builder setReportWinBuyerAdditionalSignalsContainedDataVersion(
                boolean reportWinBuyerAdditionalSignalsContainedDataVersion);

        /** Sets whether the SellerContextualSignals contained data version. */
        public abstract Builder setReportResultSellerAdditionalSignalsContainedDataVersion(
                boolean reportResultSellerAdditionalSignalsContainedDataVersion);

        /** Sets the result code of the buyer JS script (reportWin). */
        public abstract Builder setReportWinJsScriptResultCode(
                @AdsRelevanceStatusUtils.JsRunStatus int resultCode);

        /** Sets the result code of the seller JS script (reportResult). */
        public abstract Builder setReportResultJsScriptResultCode(
                @AdsRelevanceStatusUtils.JsRunStatus int resultCode);

        /** Builds the {@link ReportImpressionApiCalledStats} object. */
        public abstract ReportImpressionApiCalledStats build();
    }
}
