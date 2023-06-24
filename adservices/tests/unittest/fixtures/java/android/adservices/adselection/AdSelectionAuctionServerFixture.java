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

package android.adservices.adselection;

import com.android.adservices.service.adselection.AuctionServerDataCompressor;
import com.android.adservices.service.adselection.AuctionServerDataCompressorV0;
import com.android.adservices.service.adselection.AuctionServerPayloadFormatter;
import com.android.adservices.service.adselection.AuctionServerPayloadFormatterV0;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.AuctionResult;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.WinReportingUrls;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.WinReportingUrls.ReportingUrls;

public class AdSelectionAuctionServerFixture {

    public static byte[] formattedAuctionResult() {
        AuctionServerDataCompressorV0 mDataCompressorV0 = new AuctionServerDataCompressorV0();
        WinReportingUrls urls =
                WinReportingUrls.newBuilder()
                        .setBuyerReportingUrls(
                                ReportingUrls.newBuilder()
                                        .setReportingUrl("https://foobarbuyer.reporting")
                                        .build())
                        .setComponentSellerReportingUrls(
                                ReportingUrls.newBuilder()
                                        .setReportingUrl("https://foobarseller.reporting")
                                        .build())
                        .build();
        AuctionResult result =
                AuctionResult.newBuilder()
                        .setAdRenderUrl("https://foo.bar")
                        .setCustomAudienceName("test CA")
                        .setCustomAudienceOwner("test owner")
                        .setScore(1.4f)
                        .setBid(1.2f)
                        .setIsChaff(false)
                        .setWinReportingUrls(urls)
                        .build();

        AuctionServerDataCompressor.UncompressedData uncompressedData =
                AuctionServerDataCompressor.UncompressedData.create(result.toByteArray());
        AuctionServerDataCompressor.CompressedData compressedData =
                mDataCompressorV0.compress(uncompressedData);

        AuctionServerPayloadFormatterV0 formatterV0 = new AuctionServerPayloadFormatterV0();

        AuctionServerPayloadFormatter.UnformattedData input =
                AuctionServerPayloadFormatter.UnformattedData.create(compressedData.getData());

        AuctionServerPayloadFormatter.FormattedData formatted = formatterV0.apply(input, 0);

        return formatted.getData();
    }
}
