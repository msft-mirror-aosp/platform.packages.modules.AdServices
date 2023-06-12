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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import android.adservices.common.AdTechIdentifier;
import android.net.Uri;
import android.util.ArrayMap;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.ValidatorUtil;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Event-level debug reporting for ad selection.
 *
 * <p>Protected Audience debug reporting allows ad tech developers to declare remote URLs to receive
 * a GET request from devices when an auction is won / lost. This allows the following use-cases:
 *
 * <ul>
 *   <li>See if auctions are being won / lost
 *   <li>Understand why auctions are being lost (e.g. understand if it’s an issue with a bidding /
 *       scoring script implementation or a core logic issue)
 *   <li>Monitor roll-outs of new JavaScript logic to clients
 * </ul>
 *
 * <p>Debug reporting consists of two JS APIs available for usage, both of which take a URL string:
 * <li>forDebuggingOnly.reportAdAuctionWin(String url)
 * <li>forDebuggingOnly.reportAdAuctionWin(String url)
 *
 *     <p>For the classes that wrap JavaScript code, see {@link DebugReportingScriptStrategy}.
 */
// TODO(b/284451364): Queue URLs onto background thread for actual calling.
// TODO(b/284451364): Return script strategy based on flag and AdId.
class DebugReportProcessor {

    private static final ImmutableSet<String> VALID_SELLER_REJECT_REASONS =
            ImmutableSet.of(
                    "not-available",
                    "invalid-bid",
                    "bid-below-auction-floor",
                    "pending-approval-by-exchange",
                    "disapproved-by-exchange",
                    "blocked-by-publisher",
                    "language-exclusions",
                    "category-exclusions");
    // UTF-8 characters are 2 bytes each, so a limit of 2000 is ~4 KB.
    private static final int MAX_URI_CHARACTER_LENGTH = 2048;

    // Cap the number of URIs at 75 custom audiences, based on P95 OT results.
    @VisibleForTesting public static final int MAX_NUMBER_OF_URIS_PER_AUCTION_PER_AD_TECH = 75;

    @VisibleForTesting public static final String UNKNOWN_VARIABLE_STRING = "unknown";

    @VisibleForTesting public static final String WINNING_BID_VARIABLE_TEMPLATE = "${winningBid}";

    @VisibleForTesting
    public static final String MADE_WINNING_BID_VARIABLE_TEMPLATE = "${madeWinningBid}";

    @VisibleForTesting
    public static final String HIGHEST_SCORING_OTHER_BID_VARIABLE_TEMPLATE =
            "${highestScoringOtherBid}";

    @VisibleForTesting
    public static final String MADE_HIGHEST_SCORING_OTHER_BID_VARIABLE_TEMPLATE =
            "${madeHighestScoringOtherBid}";

    @VisibleForTesting
    public static final String REJECT_REASON_VARIABLE_TEMPLATE = "${rejectReason}";

    /**
     * Process all debug reporting {@link Uri}s from an ad auction's results.
     *
     * <p>This method processes every element in the given ad auction results, extracts and filters
     * for valid debug reporting URLs (such as enforcing the constraint that ad tech domain must
     * match the debug URL), and finally substituting auction data into the URL templates.
     *
     * <p>See the explainer on event-level debug reporting for more information.
     *
     * @param generateBidResults list of bidding results from an auction. Each element of this list
     *     is expected to correlate with elements in scoreAdResults.
     * @param scoreAdResults list of scoring results from an auction. Not every bid is required to
     *     be scored, e.g. if there was a failure in runAdBidding.
     * @return a list of valid {@link Uri} objects. Make network calls to these to deliver
     *     event-level debug reporting information to SSP and DSPs.
     */
    static List<Uri> getUrisFromAdAuction(
            @NonNull List<GenerateBidResult> generateBidResults,
            @NonNull List<ScoreAdResult> scoreAdResults,
            @Nullable ScoreAdResult topScoringAd,
            @Nullable ScoreAdResult secondHighestAd) {
        checkNotNull(generateBidResults);
        checkNotNull(scoreAdResults);
        List<AuctionResult> results = joinResults(generateBidResults, scoreAdResults);
        return getUrisFromAdAuctionImpl(
                results,
                matchScoreToAuctionResult(topScoringAd, results),
                matchScoreToAuctionResult(secondHighestAd, results));
    }

    private static List<Uri> getUrisFromAdAuctionImpl(
            List<AuctionResult> results,
            @Nullable AuctionResult winningResult,
            @Nullable AuctionResult highestScoringOtherResult) {
        return applyPerAdTechLimit(
                results.stream()
                        .map(
                                result ->
                                        getFinalizedUrisFromResult(
                                                result, winningResult, highestScoringOtherResult))
                        .flatMap(List::stream)
                        .collect(Collectors.toList()));
    }

    @Nullable
    private static AuctionResult matchScoreToAuctionResult(
            ScoreAdResult scoreAdResult, List<AuctionResult> auctionResults) {
        return auctionResults.stream()
                .filter(
                        auctionResult ->
                                Objects.equals(scoreAdResult, auctionResult.mScoreAdResult))
                .findFirst()
                .orElse(null);
    }

    private static List<Uri> applyPerAdTechLimit(List<Uri> debugReportingUris) {
        // Processing for the ad tech limit must be done at the final stage, as each AuctionResult
        // can be 0 or more URIs and for both sell-side and buy-side.
        Multimap<String, Uri> hostToUriMap =
                ArrayListMultimap.create(
                        /* expectedKeys= */ 5, MAX_NUMBER_OF_URIS_PER_AUCTION_PER_AD_TECH);

        for (Uri debugReportingUri : debugReportingUris) {
            String adTechHost = debugReportingUri.getHost();
            if (hostToUriMap.get(adTechHost).size() >= MAX_NUMBER_OF_URIS_PER_AUCTION_PER_AD_TECH) {
                continue;
            }
            hostToUriMap.put(debugReportingUri.getHost(), debugReportingUri);
        }

        return new ArrayList<>(hostToUriMap.values());
    }

    private static List<Uri> getFinalizedUrisFromResult(
            AuctionResult result,
            @Nullable AuctionResult winningResult,
            @Nullable AuctionResult highestScoringOtherResult) {
        // Process every Uri such that it contains debugging data. Also run a final filtering step
        // to check that the Uri is still considered "callable".
        Preconditions.checkNotNull(result);
        ImmutableList<Uri> uris =
                result.equals(winningResult)
                        ? result.getAuctionWinUris()
                        : result.getAuctionLossUris();
        return uris.stream()
                .map(
                        uri ->
                                applyVariablesToUri(
                                        uri,
                                        collectVariablesFromAdAuction(
                                                result, winningResult, highestScoringOtherResult)))
                .filter(DebugReportProcessor::isValidUri)
                .collect(Collectors.toList());
    }

    private static List<AuctionResult> joinResults(
            List<GenerateBidResult> generateBidResults, List<ScoreAdResult> scoreAdResults) {
        checkArgument(generateBidResults.size() >= scoreAdResults.size());
        List<AuctionResult> results = new ArrayList<>();

        Map<String, GenerateBidResult> mappedBidResults = new ArrayMap<>();
        Map<String, ScoreAdResult> mappedScoreAdResults = new ArrayMap<>();

        for (GenerateBidResult bidResult : generateBidResults) {
            mappedBidResults.put(
                    String.format(
                            "%s:%s",
                            bidResult.getCustomAudienceName(), bidResult.getCustomAudienceBuyer()),
                    bidResult);
        }

        for (ScoreAdResult scoreAdResult : scoreAdResults) {
            mappedScoreAdResults.put(
                    String.format(
                            "%s:%s",
                            scoreAdResult.getCustomAudienceName(),
                            scoreAdResult.getCustomAudienceBuyer()),
                    scoreAdResult);
        }

        for (Map.Entry<String, GenerateBidResult> mappedBidResult : mappedBidResults.entrySet()) {
            String key = mappedBidResult.getKey();
            GenerateBidResult generateBidResult = mappedBidResult.getValue();

            if (mappedScoreAdResults.containsKey(key)) {
                ScoreAdResult scoreAdResult = mappedScoreAdResults.get(key);
                results.add(
                        AuctionResult.of(generateBidResult, Objects.requireNonNull(scoreAdResult)));
            } else {
                // A partial result may be a bid without scoring.
                results.add(AuctionResult.of(generateBidResult));
            }
        }

        return results;
    }

    private static Map<String, String> collectVariablesFromAdAuction(
            AuctionResult currentBid,
            @Nullable AuctionResult winningBid,
            @Nullable AuctionResult highestScoringOtherBid) {
        Map<String, String> templateToVariableMap = new HashMap<>();
        templateToVariableMap.put(
                WINNING_BID_VARIABLE_TEMPLATE,
                Objects.isNull(winningBid)
                        ? UNKNOWN_VARIABLE_STRING
                        : String.valueOf(winningBid.getBid()));
        templateToVariableMap.put(
                MADE_WINNING_BID_VARIABLE_TEMPLATE,
                String.valueOf(currentBid.hasSameCustomAudienceBuyer(winningBid)));
        templateToVariableMap.put(
                HIGHEST_SCORING_OTHER_BID_VARIABLE_TEMPLATE,
                Objects.isNull(highestScoringOtherBid)
                        ? UNKNOWN_VARIABLE_STRING
                        : String.valueOf(highestScoringOtherBid.getBid()));
        templateToVariableMap.put(
                MADE_HIGHEST_SCORING_OTHER_BID_VARIABLE_TEMPLATE,
                String.valueOf(currentBid.hasSameCustomAudienceBuyer(highestScoringOtherBid)));
        String sellerRejectReason = currentBid.getSellerRejectReason();
        templateToVariableMap.put(
                REJECT_REASON_VARIABLE_TEMPLATE,
                isValidSellerRejectReason(sellerRejectReason)
                        ? sellerRejectReason
                        : UNKNOWN_VARIABLE_STRING);
        return templateToVariableMap;
    }

    private static Uri applyVariablesToUri(Uri input, Map<String, String> templateToVariableMap) {
        // Apply variables to both query parameter and path, as ad techs can choose to use both
        // query parameters or path fragments for variables.
        return Uri.parse(applyVariablesToString(input.toString(), templateToVariableMap));
    }

    private static String applyVariablesToString(
            String input, Map<String, String> templateToVariableMap) {
        if (Strings.isNullOrEmpty(input)) {
            return null;
        }

        for (Map.Entry<String, String> templateToVariable : templateToVariableMap.entrySet()) {
            String template = templateToVariable.getKey();
            String variable = templateToVariable.getValue();
            input = input.replace(template, variable);
        }
        return input;
    }

    private static boolean hasValidUriForAdTech(
            @Nullable Uri uri, AdTechIdentifier adTechIdentifier) {
        // The host for a URL must match the given ad tech identifier. This also tests for
        // subdomains a buyer or seller might have.
        return Objects.nonNull(uri)
                && new AdTechUriValidator(
                                ValidatorUtil.AD_TECH_ROLE_SELLER,
                                adTechIdentifier.toString(),
                                DebugReportProcessor.class.getSimpleName(),
                                "matching ad tech identifier")
                        .getValidationViolations(uri)
                        .isEmpty();
    }

    private static boolean isValidUri(@Nullable Uri uri) {
        return uri.toString().length() < MAX_URI_CHARACTER_LENGTH
                && uri.getScheme().equals("https")
                && URLUtil.isNetworkUrl(uri.toString());
    }

    private static boolean isValidSellerRejectReason(@Nullable String sellerRejectReason) {
        return !Strings.isNullOrEmpty(sellerRejectReason)
                && VALID_SELLER_REJECT_REASONS.contains(sellerRejectReason);
    }

    private static final class AuctionResult {
        public static final double MISSING_AD_SCORE = -1D;
        private final GenerateBidResult mGenerateBidResult;
        private final ScoreAdResult mScoreAdResult;

        AuctionResult(GenerateBidResult generateBidResult, ScoreAdResult scoreAdResult) {
            this.mGenerateBidResult = generateBidResult;
            this.mScoreAdResult = scoreAdResult;
        }

        static AuctionResult of(@NonNull GenerateBidResult generateBidResult) {
            return new AuctionResult(generateBidResult, null);
        }

        static AuctionResult of(
                @NonNull GenerateBidResult generateBidResult, ScoreAdResult scoreAdResult) {
            checkArgument(
                    generateBidResult
                            .getCustomAudienceName()
                            .equals(scoreAdResult.getCustomAudienceName()));
            checkArgument(
                    generateBidResult
                            .getOwnerAppPackage()
                            .equals(scoreAdResult.getOwnerAppPackage()));
            return new AuctionResult(generateBidResult, scoreAdResult);
        }

        Double getBid() {
            return mGenerateBidResult.getAdWithBid().getBid();
        }

        String getSellerRejectReason() {
            if (Objects.isNull(mScoreAdResult)) {
                return UNKNOWN_VARIABLE_STRING;
            }
            return mScoreAdResult.getSellerRejectReason();
        }

        public ImmutableList<Uri> getAuctionWinUris() {
            ImmutableList.Builder<Uri> uris = ImmutableList.builder();
            Uri uri;
            uri = mGenerateBidResult.getWinDebugReportUri();
            if (uri != null
                    && hasValidUriForAdTech(uri, mGenerateBidResult.getCustomAudienceBuyer())) {
                uris.add(uri);
            }

            if (mScoreAdResult != null) {
                uri = mScoreAdResult.getWinDebugReportUri();
                if (uri != null && hasValidUriForAdTech(uri, mScoreAdResult.getPublisher())) {
                    uris.add(uri);
                }
            }

            return uris.build();
        }

        public ImmutableList<Uri> getAuctionLossUris() {
            ImmutableList.Builder<Uri> uris = ImmutableList.builder();
            Uri uri;
            uri = mGenerateBidResult.getLossDebugReportUri();
            if (uri != null
                    && hasValidUriForAdTech(uri, mGenerateBidResult.getCustomAudienceBuyer())) {
                uris.add(uri);
            }

            if (mScoreAdResult != null) {
                uri = mScoreAdResult.getLossDebugReportUri();
                if (uri != null && hasValidUriForAdTech(uri, mScoreAdResult.getPublisher())) {
                    uris.add(uri);
                }
            }

            return uris.build();
        }

        boolean hasSameCustomAudienceBuyer(@Nullable AuctionResult otherResult) {
            return Objects.nonNull(otherResult)
                    && mGenerateBidResult
                            .getCustomAudienceBuyer()
                            .equals(otherResult.mGenerateBidResult.getCustomAudienceBuyer());
        }
    }
}
