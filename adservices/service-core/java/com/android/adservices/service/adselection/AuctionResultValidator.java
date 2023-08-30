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

import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.Validator;
import com.android.adservices.service.common.ValidatorUtil;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.AuctionResult;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableCollection;

import java.util.Locale;

/** Validator to validate auction result. */
public class AuctionResultValidator implements Validator<AuctionResult> {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting
    static final String NEGATIVE_BID = "Bid (%f) in the auction result is negative.";

    @VisibleForTesting
    static final String NEGATIVE_SCORE = "Score (%f) in the auction result is negative.";

    /**
     * Validates {@link AuctionResult}.
     *
     * <ol>
     *   <li>The ad render uri in the data is part of the custom audience specified in the result;
     *   <li>The bid and the score is not negative.
     * </ol>
     */
    @Override
    public void addValidation(
            AuctionResult auctionResult, ImmutableCollection.Builder<String> violations) {
        if (auctionResult.getIsChaff()) {
            sLogger.v(
                    "AuctionResult validation skipped because isChaff=%s",
                    auctionResult.getIsChaff());
            return;
        }

        AdTechUriValidator adRenderUriValidator =
                new AdTechUriValidator(
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        auctionResult.getBuyer().toString(),
                        "AuctionResult",
                        "Ad render URL");
        adRenderUriValidator.addValidation(Uri.parse(auctionResult.getAdRenderUrl()), violations);

        if (auctionResult.getBid() < 0) {
            violations.add(String.format(Locale.ENGLISH, NEGATIVE_BID, auctionResult.getBid()));
        }
        if (auctionResult.getScore() < 0) {
            violations.add(String.format(Locale.ENGLISH, NEGATIVE_SCORE, auctionResult.getScore()));
        }
    }
}
