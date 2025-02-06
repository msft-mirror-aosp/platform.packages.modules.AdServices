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

package com.android.adservices.service.shell.adselection;

import android.adservices.common.AdTechIdentifier;
import android.net.Uri;

import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.DBAuctionServerAdSelection;
import com.android.adservices.data.adselection.datahandlers.AdSelectionResultBidAndUri;
import com.android.adservices.data.adselection.datahandlers.RegisteredAdInteraction;
import com.android.adservices.data.adselection.datahandlers.ReportingData;
import com.android.adservices.data.adselection.datahandlers.WinningCustomAudience;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.AuctionResult;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.WinReportingUrls;

import java.util.List;

/** Helper for parsing {@link DBAuctionServerAdSelection} objects into protobuf. */
public class AdSelectionEntryHelper {

    /**
     * Construct an {@link AuctionResult} for a given ad selection.
     *
     * @param adSelectionEntry Data for ad selection.
     * @param reportingUris Data for reporting.
     * @return Valid proto
     */
    static AuctionResult createAuctionResultFromAdSelectionData(
            AdSelectionResultBidAndUri adSelection,
            AdTechIdentifier winningBuyer,
            WinningCustomAudience winningCustomAudience,
            ReportingData reportingUris,
            List<RegisteredAdInteraction> buyerAdInteractions,
            List<RegisteredAdInteraction> sellerAdInteractions) {
        AuctionResult.Builder builder = AuctionResult.newBuilder()
                .setAdRenderUrl(adSelection.getWinningAdRenderUri().toString())
                .setBid((float) adSelection.getWinningAdBid())
                .setBuyer(winningBuyer.toString())
                .setIsChaff(false)
                .setWinReportingUrls(
                        getWinReportingUrls(
                                reportingUris, buyerAdInteractions, sellerAdInteractions));
      if (winningCustomAudience != null) {
            builder
                    .setCustomAudienceName(winningCustomAudience.getName())
                    .setCustomAudienceOwner(winningCustomAudience.getOwner());
        }
        return builder.build();
    }

    private static WinReportingUrls getWinReportingUrls(
            ReportingData reportingData,
            List<RegisteredAdInteraction> buyerAdInteractions,
            List<RegisteredAdInteraction> sellerAdInteractions) {
        WinReportingUrls.Builder builder = WinReportingUrls.newBuilder();
        builder.setBuyerReportingUrls(
                getWinReportingUrls(
                        reportingData != null && reportingData.getBuyerWinReportingUri() != null
                                ? reportingData.getBuyerWinReportingUri()
                                : Uri.EMPTY,
                        buyerAdInteractions));
        builder.setTopLevelSellerReportingUrls(
                getWinReportingUrls(
                        reportingData != null && reportingData.getSellerWinReportingUri() != null
                                ? reportingData.getSellerWinReportingUri()
                                : Uri.EMPTY,
                        sellerAdInteractions));
        return builder.build();
    }

    private static WinReportingUrls.ReportingUrls getWinReportingUrls(
            Uri winReportingUri, List<RegisteredAdInteraction> filteredAdInteractions) {
        WinReportingUrls.ReportingUrls.Builder builder =
                WinReportingUrls.ReportingUrls.newBuilder()
                        .setReportingUrl(winReportingUri.toString());
        if (!filteredAdInteractions.isEmpty()) {
            for (RegisteredAdInteraction adInteraction : filteredAdInteractions) {
                builder.putInteractionReportingUrls(
                        adInteraction.getInteractionKey(),
                        adInteraction.getInteractionReportingUri().toString());
            }
        }
        return builder.build();
    }
}
