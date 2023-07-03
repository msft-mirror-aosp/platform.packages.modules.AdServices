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

import static com.google.common.base.Preconditions.checkNotNull;

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;

import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.ValidatorUtil;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    // UTF-8 characters are 2 bytes each, so a limit of 2000 is ~4 KB.
    private static final int MAX_URI_CHARACTER_LENGTH = 2048;

    // Cap the number of URIs at 75 custom audiences, based on P95 OT results.
    @VisibleForTesting public static final int MAX_NUMBER_OF_URIS_PER_AUCTION_PER_AD_TECH = 75;

    private static final String WINNING_BID_VARIABLE_TEMPLATE = "${winningBid}";

    private static final String WINNING_BID_DEFAULT_VALUE = "0.0";

    private static final String MADE_WINNING_BID_VARIABLE_TEMPLATE = "${madeWinningBid}";

    private static final String HIGHEST_SCORING_OTHER_BID_VARIABLE_TEMPLATE =
            "${highestScoringOtherBid}";

    private static final String HIGHEST_SCORING_OTHER_BID_DEFAULT_VALUE = "0.0";
    private static final String MADE_HIGHEST_SCORING_OTHER_BID_VARIABLE_TEMPLATE =
            "${madeHighestScoringOtherBid}";

    /**
     * Process all debug reporting {@link Uri}s from an ad auction's results.
     *
     * <p>This method processes every element in the given list, extracts and filters for valid
     * debug reporting URLs (such as enforcing the constraint that ad tech domain must match the
     * debug URL), and finally substituting auction data into the URL templates.
     *
     * <p>See the explainer on event-level debug reporting for more information.
     *
     * @param debugReports list of debug reports generated during the ad selection process.
     * @param postAuctionSignals signals generated after the ad selection process finished..
     * @return a list of valid {@link Uri} objects. Make network calls to these to deliver
     *     event-level debug reporting information to SSP and DSPs.
     */
    static List<Uri> getUrisFromAdAuction(
            @NonNull List<DebugReport> debugReports,
            @NonNull PostAuctionSignals postAuctionSignals) {
        checkNotNull(debugReports);
        checkNotNull(postAuctionSignals);
        List<Uri> debugUrls = new ArrayList<>();
        for (DebugReport debugReport : debugReports) {
            Uri debugUri = getDebugUri(debugReport, postAuctionSignals);
            if (!Objects.isNull(debugUri)) {
                debugUrls.add(debugUri);
            }
        }
        return applyPerAdTechLimit(debugUrls);
    }

    private static Uri getDebugUri(
            @NonNull DebugReport debugReport, @NonNull PostAuctionSignals postAuctionSignals) {
        Uri debugUri;
        boolean isWinnerCA =
                isDebugReportForCustomAudience(
                        debugReport,
                        postAuctionSignals.getWinningBuyer(),
                        postAuctionSignals.getWinningCustomAudienceName());
        if (isWinnerCA && !Objects.isNull(debugReport.getWinDebugReportUri())) {
            debugUri = debugReport.getWinDebugReportUri();
        } else if (!Objects.isNull(debugReport.getLossDebugReportUri())) {
            debugUri = debugReport.getLossDebugReportUri();
        } else {
            return null;
        }
        // Seller field is only set for seller specific debug reports.
        AdTechIdentifier adTechIdentifier =
                Objects.isNull(debugReport.getSeller())
                        ? debugReport.getCustomAudienceBuyer()
                        : debugReport.getSeller();
        if (!hasValidUriForAdTech(debugUri, adTechIdentifier)) {
            return null;
        }
        return applyVariablesToUri(
                debugUri, collectVariablesFromAdAuction(debugReport, postAuctionSignals));
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

    private static boolean isDebugReportForCustomAudience(
            @NonNull final DebugReport debugReport,
            @Nullable final AdTechIdentifier customAudienceBuyer,
            @Nullable final String customAudienceName) {
        if (Objects.isNull(customAudienceBuyer) || Objects.isNull(customAudienceName)) {
            return false;
        }
        return customAudienceBuyer.equals(debugReport.getCustomAudienceBuyer())
                && customAudienceName.equals(debugReport.getCustomAudienceName());
    }

    private static boolean isDebugReportForCustomAudienceBuyer(
            @NonNull final DebugReport debugReport,
            @Nullable final AdTechIdentifier customAudienceBuyer) {
        if (Objects.isNull(customAudienceBuyer)) {
            return false;
        }
        return customAudienceBuyer.equals(debugReport.getCustomAudienceBuyer());
    }

    private static Map<String, String> collectVariablesFromAdAuction(
            @NonNull DebugReport debugReport, @NonNull PostAuctionSignals signals) {
        Map<String, String> templateToVariableMap = new HashMap<>();
        templateToVariableMap.put(
                WINNING_BID_VARIABLE_TEMPLATE,
                Objects.isNull(signals.getWinningBid())
                        ? WINNING_BID_DEFAULT_VALUE
                        : String.valueOf(signals.getWinningBid()));
        templateToVariableMap.put(
                MADE_WINNING_BID_VARIABLE_TEMPLATE,
                String.valueOf(
                        isDebugReportForCustomAudienceBuyer(
                                debugReport, signals.getWinningBuyer())));
        templateToVariableMap.put(
                HIGHEST_SCORING_OTHER_BID_VARIABLE_TEMPLATE,
                Objects.isNull(signals.getSecondHighestScoredBid())
                        ? HIGHEST_SCORING_OTHER_BID_DEFAULT_VALUE
                        : String.valueOf(signals.getSecondHighestScoredBid()));
        templateToVariableMap.put(
                MADE_HIGHEST_SCORING_OTHER_BID_VARIABLE_TEMPLATE,
                String.valueOf(
                        isDebugReportForCustomAudienceBuyer(
                                debugReport, signals.getSecondHighestScoredBuyer())));
        return templateToVariableMap;
    }

    private static Uri applyVariablesToUri(
            @NonNull Uri input, Map<String, String> templateToVariableMap) {
        // Apply variables to both query parameter and path, as ad techs can choose to use both
        // query parameters or path fragments for variables.
        String uriString = input.toString();
        for (Map.Entry<String, String> templateToVariable : templateToVariableMap.entrySet()) {
            String template = templateToVariable.getKey();
            String variable = templateToVariable.getValue();
            uriString = uriString.replace(template, variable);
        }
        if (uriString.length() > MAX_URI_CHARACTER_LENGTH) {
            return null;
        }
        return Uri.parse(uriString);
    }

    private static boolean hasValidUriForAdTech(
            @Nullable Uri uri, @NonNull AdTechIdentifier adTechIdentifier) {
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
}
