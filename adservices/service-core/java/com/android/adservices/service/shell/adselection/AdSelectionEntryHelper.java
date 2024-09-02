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

import android.net.Uri;

import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.DBAdSelectionEntry;
import com.android.adservices.data.adselection.datahandlers.RegisteredAdInteraction;
import com.android.adservices.data.adselection.datahandlers.ReportingData;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.AuctionResult;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.WinReportingUrls;

import java.util.List;

/** Helper for parting {@link DBAdSelectionEntry} objects into protobuf. */
public class AdSelectionEntryHelper {

    /**
     * Construct an {@link AuctionResult} for a given ad selection.
     *
     * @param adSelectionEntry Data for ad selection.
     * @param reportingUris Data for reporting.
     * @return Valid proto.
     */
    static AuctionResult getAuctionResultFromAdSelectionEntry(
            DBAdSelectionEntry adSelectionEntry,
            ReportingData reportingUris,
            List<RegisteredAdInteraction> buyerAdInteractions,
            List<RegisteredAdInteraction> sellerAdInteractions) {
        AuctionResult.Builder auctionResult =
                AuctionResult.newBuilder()
                        .setBid(
                                Float.parseFloat(
                                        Double.toString(adSelectionEntry.getWinningAdBid())))
                        .setAdRenderUrl(adSelectionEntry.getWinningAdRenderUri().toString())
                        .setIsChaff(false)
                        .setWinReportingUrls(
                                getWinReportingUrls(
                                        reportingUris, buyerAdInteractions, sellerAdInteractions));

        if (adSelectionEntry.getCustomAudienceSignals() != null) {
            CustomAudienceSignals signals = adSelectionEntry.getCustomAudienceSignals();
            auctionResult
                    .setBuyer(signals.getBuyer().toString())
                    .setCustomAudienceOwner(signals.getOwner())
                    .setCustomAudienceName(signals.getName());
        }

        return auctionResult.build();
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
