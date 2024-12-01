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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__AUCTION_RESULT_VALIDATOR_AD_TECH_NOT_ALLOWED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE;

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.annotation.RequiresApi;
import android.net.Uri;
import android.os.Build;

import com.android.adservices.LoggerFactory;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.Validator;
import com.android.adservices.service.common.ValidatorUtil;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.AuctionResult;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableCollection;

import java.util.Locale;
import java.util.Objects;

/** Validator to validate auction result. */
@RequiresApi(Build.VERSION_CODES.S)
public class AuctionResultValidator implements Validator<AuctionResult> {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting
    static final String NEGATIVE_BID = "Bid (%f) in the auction result is negative.";

    @VisibleForTesting
    static final String NEGATIVE_SCORE = "Score (%f) in the auction result is negative.";

    @VisibleForTesting
    static final String BUYER_ENROLLMENT = "Buyer (%s) in the auction result is not enrolled.";

    @VisibleForTesting
    static final String BUYER_EMPTY = "Buyer in the auction result is an empty string.";

    @VisibleForTesting
    static final String WINNING_SELLER_EMPTY =
            "Winning seller in the auction result in an empty string.";

    @VisibleForTesting
    static final String WINNING_SELLER_ENROLLMENT =
            "Winning seller (%s) in the auction result is not enrolled.";

    private FledgeAuthorizationFilter mFledgeAuthorizationFilter;
    private boolean mDisableFledgeEnrollmentCheck;
    private boolean mEnableWinningSellerInAdSelectionOutcome;

    public AuctionResultValidator(
            @NonNull FledgeAuthorizationFilter fledgeAuthorizationFilter,
            boolean disableFledgeEnrollmentCheck,
            boolean enableWinningSellerInAdSelectionOutcome) {
        Objects.requireNonNull(fledgeAuthorizationFilter);
        mFledgeAuthorizationFilter = fledgeAuthorizationFilter;
        mDisableFledgeEnrollmentCheck = disableFledgeEnrollmentCheck;
        mEnableWinningSellerInAdSelectionOutcome = enableWinningSellerInAdSelectionOutcome;
    }


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
        if (auctionResult.getIsChaff() || auctionResult.hasError()) {
            sLogger.v(
                    "AuctionResult validation skipped because isChaff=%s or result has error=%s",
                    auctionResult.getIsChaff(), auctionResult.hasError());
            return;
        }

        if (auctionResult.getBuyer().isEmpty()) {
            violations.add(BUYER_EMPTY);
        }

        if (mEnableWinningSellerInAdSelectionOutcome
                && auctionResult.getWinningSeller().isEmpty()) {
            violations.add(WINNING_SELLER_EMPTY);
        }

        if (!mDisableFledgeEnrollmentCheck) {
            assertAdTechEnrolledAndAddViolations(
                    violations, auctionResult.getBuyer(), BUYER_ENROLLMENT);
            if (mEnableWinningSellerInAdSelectionOutcome) {
                assertAdTechEnrolledAndAddViolations(
                        violations, auctionResult.getWinningSeller(), WINNING_SELLER_ENROLLMENT);
            }
        }

        AdTechUriValidator adRenderUriValidator =
                new AdTechUriValidator(
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        auctionResult.getBuyer(),
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

    private void assertAdTechEnrolledAndAddViolations(
            ImmutableCollection.Builder<String> violations,
            String adTech,
            String violationStringMsg) {
        try {
            mFledgeAuthorizationFilter.assertAdTechEnrolled(
                    AdTechIdentifier.fromString(adTech),
                    AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN);
        } catch (FledgeAuthorizationFilter.AdTechNotAllowedException e) {
            violations.add(String.format(Locale.ENGLISH, violationStringMsg, adTech));
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__AUCTION_RESULT_VALIDATOR_AD_TECH_NOT_ALLOWED,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE);
        }
    }
}
