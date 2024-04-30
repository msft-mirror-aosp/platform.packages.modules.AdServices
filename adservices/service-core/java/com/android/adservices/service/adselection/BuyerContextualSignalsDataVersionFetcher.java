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

package com.android.adservices.service.adselection;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;

import com.android.adservices.data.customaudience.DBTrustedBiddingData;

import java.util.Map;

/** Defines a strategy interface for extracting data version into buyer contextual signals. */
public interface BuyerContextualSignalsDataVersionFetcher {
    /** Gets {@link BuyerContextualSignals} for {@code generateBid} */
    @NonNull
    BuyerContextualSignals getContextualSignalsForGenerateBid(
            @NonNull DBTrustedBiddingData trustedBiddingData,
            @NonNull Map<Uri, TrustedBiddingResponse> trustedBiddingDataByBaseUri);

    /** Gets {@link BuyerContextualSignals} for {@code reportWin} */
    @Nullable
    BuyerContextualSignals getContextualSignalsForReportWin(
            @NonNull DBTrustedBiddingData trustedBiddingData,
            @NonNull Map<Uri, TrustedBiddingResponse> trustedBiddingDataByBaseUri,
            @Nullable AdCost adCost);
}
