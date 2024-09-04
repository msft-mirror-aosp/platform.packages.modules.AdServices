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

import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER;
import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;

import static com.android.adservices.data.adselection.DBRegisteredAdInteractionFixture.INTERACTION_KEY_CLICK;
import static com.android.adservices.data.adselection.DBRegisteredAdInteractionFixture.INTERACTION_KEY_VIEW;
import static com.android.adservices.service.shell.adselection.AdSelectionShellCommandConstants.AD_SELECTION_ID;
import static com.android.adservices.service.shell.adselection.AdSelectionShellCommandConstants.OUTPUT_PROTO_FIELD_NAME;
import static com.android.adservices.service.shell.adselection.ViewAuctionResultCommand.CMD;
import static com.android.adservices.service.shell.adselection.ViewAuctionResultCommand.HELP;
import static com.android.adservices.service.shell.signals.SignalsShellCommandFactory.COMMAND_PREFIX;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_AD_SELECTION_VIEW_AUCTION_RESULT;

import static org.mockito.Mockito.when;

import android.net.Uri;
import android.util.Base64;

import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelectionEntry;
import com.android.adservices.data.adselection.DBAdSelectionFixture;
import com.android.adservices.data.adselection.ReportingDataFixture;
import com.android.adservices.data.adselection.datahandlers.RegisteredAdInteraction;
import com.android.adservices.data.adselection.datahandlers.ReportingData;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.AuctionResult;
import com.android.adservices.service.shell.ShellCommandTestCase;
import com.android.adservices.service.stats.ShellCommandStats;

import com.google.protobuf.InvalidProtocolBufferException;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.mockito.Mock;

import java.util.List;

public final class ViewAuctionResultCommandTest
        extends ShellCommandTestCase<ViewAuctionResultCommand> {

    @ShellCommandStats.Command
    private static final int EXPECTED_COMMAND = COMMAND_AD_SELECTION_VIEW_AUCTION_RESULT;

    @Mock private AdSelectionEntryDao mAdSelectionEntryDao;

    private static final DBAdSelectionEntry AD_SELECTION_ENTRY =
            DBAdSelectionFixture.getValidDbAdSelectionEntryBuilder().build();
    private static final RegisteredAdInteraction BUYER_CLICK_AD_INTERACTION =
            RegisteredAdInteraction.builder()
                    .setInteractionKey(INTERACTION_KEY_CLICK)
                    .setInteractionReportingUri(
                            Uri.parse(
                                    "https://" + AD_SELECTION_ENTRY.getBiddingLogicUri().getHost()))
                    .build();
    private static final RegisteredAdInteraction SELLER_VIEW_AD_INTERACTION =
            RegisteredAdInteraction.builder()
                    .setInteractionKey(INTERACTION_KEY_VIEW)
                    .setInteractionReportingUri(
                            Uri.parse(
                                    "https://" + AD_SELECTION_ENTRY.getBiddingLogicUri().getHost()))
                    .build();

    @Test
    public void testRun_missingAdSelectionId_returnsHelp() {
        runAndExpectInvalidArgument(
                new ViewAuctionResultCommand(mAdSelectionEntryDao),
                HELP,
                EXPECTED_COMMAND,
                COMMAND_PREFIX,
                CMD);
    }

    @Test
    public void testRun_withUnknownAdSelectionId_throwsException() {
        when(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(false);
        when(mAdSelectionEntryDao.listRegisteredAdInteractions(
                        AD_SELECTION_ENTRY.getAdSelectionId(), FLAG_REPORTING_DESTINATION_SELLER))
                .thenReturn(List.of());
        when(mAdSelectionEntryDao.listRegisteredAdInteractions(
                        AD_SELECTION_ENTRY.getAdSelectionId(), FLAG_REPORTING_DESTINATION_BUYER))
                .thenReturn(List.of());

        runAndExpectInvalidArgument(
                new ViewAuctionResultCommand(mAdSelectionEntryDao),
                HELP,
                EXPECTED_COMMAND,
                COMMAND_PREFIX,
                CMD,
                AD_SELECTION_ID,
                Long.toString(AD_SELECTION_ENTRY.getAdSelectionId()));
    }

    @Test
    public void testRun_withValidAdSelectionIdButNoInteractionUri_returnsSuccess()
            throws JSONException, InvalidProtocolBufferException {
        when(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(true);
        when(mAdSelectionEntryDao.getAdSelectionEntityById(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(AD_SELECTION_ENTRY);
        when(mAdSelectionEntryDao.getReportingUris(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(ReportingDataFixture.REPORTING_DATA_WITHOUT_COMPUTATION_DATA);
        when(mAdSelectionEntryDao.listRegisteredAdInteractions(
                        AD_SELECTION_ENTRY.getAdSelectionId(), FLAG_REPORTING_DESTINATION_SELLER))
                .thenReturn(List.of());
        when(mAdSelectionEntryDao.listRegisteredAdInteractions(
                        AD_SELECTION_ENTRY.getAdSelectionId(), FLAG_REPORTING_DESTINATION_BUYER))
                .thenReturn(List.of());

        Result result =
                run(
                        new ViewAuctionResultCommand(mAdSelectionEntryDao),
                        COMMAND_PREFIX,
                        CMD,
                        AD_SELECTION_ID,
                        Long.toString(AD_SELECTION_ENTRY.getAdSelectionId()));

        expectSuccess(result, EXPECTED_COMMAND);
        AuctionResult auctionResult =
                AuctionResult.parseFrom(
                        Base64.decode(
                                new JSONObject(result.mOut).getString(OUTPUT_PROTO_FIELD_NAME),
                                Base64.DEFAULT));
        expect.that(auctionResult.getAdRenderUrl())
                .isEqualTo(AD_SELECTION_ENTRY.getWinningAdRenderUri().toString());
        expect.that(auctionResult.getBid())
                .isEqualTo(Float.parseFloat(Double.toString(AD_SELECTION_ENTRY.getWinningAdBid())));
        expect.that(auctionResult.getIsChaff()).isFalse();
        expect.that(auctionResult.getBuyer())
                .isEqualTo(AD_SELECTION_ENTRY.getCustomAudienceSignals().getBuyer().toString());
        expect.that(auctionResult.getCustomAudienceOwner())
                .isEqualTo(AD_SELECTION_ENTRY.getCustomAudienceSignals().getOwner());
        expect.that(auctionResult.getCustomAudienceName())
                .isEqualTo(AD_SELECTION_ENTRY.getCustomAudienceSignals().getName());
        expect.that(
                        auctionResult
                                .getWinReportingUrls()
                                .getTopLevelSellerReportingUrls()
                                .getReportingUrl())
                .isEqualTo(ReportingDataFixture.SELLER_REPORTING_URI_1.toString());
        expect.that(auctionResult.getWinReportingUrls().getBuyerReportingUrls().getReportingUrl())
                .isEqualTo(ReportingDataFixture.BUYER_REPORTING_URI_1.toString());
    }

    @Test
    public void testRun_withRegisteredAdInteractionsButNoReportingUris_returnsSuccess()
            throws JSONException, InvalidProtocolBufferException {
        when(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(true);
        when(mAdSelectionEntryDao.getAdSelectionEntityById(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(AD_SELECTION_ENTRY);
        when(mAdSelectionEntryDao.getReportingUris(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(null);
        when(mAdSelectionEntryDao.listRegisteredAdInteractions(
                        AD_SELECTION_ENTRY.getAdSelectionId(), FLAG_REPORTING_DESTINATION_BUYER))
                .thenReturn(List.of(BUYER_CLICK_AD_INTERACTION));
        when(mAdSelectionEntryDao.listRegisteredAdInteractions(
                        AD_SELECTION_ENTRY.getAdSelectionId(), FLAG_REPORTING_DESTINATION_SELLER))
                .thenReturn(List.of(SELLER_VIEW_AD_INTERACTION));

        Result result =
                run(
                        new ViewAuctionResultCommand(mAdSelectionEntryDao),
                        COMMAND_PREFIX,
                        CMD,
                        AD_SELECTION_ID,
                        Long.toString(AD_SELECTION_ENTRY.getAdSelectionId()));

        expectSuccess(result, EXPECTED_COMMAND);
        AuctionResult auctionResult =
                AuctionResult.parseFrom(
                        Base64.decode(
                                new JSONObject(result.mOut).getString(OUTPUT_PROTO_FIELD_NAME),
                                Base64.DEFAULT));
        expect.that(
                        auctionResult
                                .getWinReportingUrls()
                                .getTopLevelSellerReportingUrls()
                                .getInteractionReportingUrlsMap()
                                .get(SELLER_VIEW_AD_INTERACTION.getInteractionKey()))
                .isEqualTo(SELLER_VIEW_AD_INTERACTION.getInteractionReportingUri().toString());
        expect.that(
                        auctionResult
                                .getWinReportingUrls()
                                .getBuyerReportingUrls()
                                .getInteractionReportingUrlsMap()
                                .get(BUYER_CLICK_AD_INTERACTION.getInteractionKey()))
                .isEqualTo(BUYER_CLICK_AD_INTERACTION.getInteractionReportingUri().toString());
        expect.that(
                        auctionResult
                                .getWinReportingUrls()
                                .getTopLevelSellerReportingUrls()
                                .getReportingUrl())
                .isEmpty();
        expect.that(auctionResult.getWinReportingUrls().getBuyerReportingUrls().getReportingUrl())
                .isEmpty();
    }

    @Test
    public void testRun_withRegisteredAdInteractionsAndReportingUris_returnsSuccess()
            throws JSONException, InvalidProtocolBufferException {
        when(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(true);
        when(mAdSelectionEntryDao.getAdSelectionEntityById(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(AD_SELECTION_ENTRY);
        when(mAdSelectionEntryDao.getReportingUris(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(ReportingDataFixture.REPORTING_DATA_WITHOUT_COMPUTATION_DATA);
        when(mAdSelectionEntryDao.listRegisteredAdInteractions(
                        AD_SELECTION_ENTRY.getAdSelectionId(), FLAG_REPORTING_DESTINATION_BUYER))
                .thenReturn(List.of(BUYER_CLICK_AD_INTERACTION));
        when(mAdSelectionEntryDao.listRegisteredAdInteractions(
                        AD_SELECTION_ENTRY.getAdSelectionId(), FLAG_REPORTING_DESTINATION_SELLER))
                .thenReturn(List.of(SELLER_VIEW_AD_INTERACTION));

        Result result =
                run(
                        new ViewAuctionResultCommand(mAdSelectionEntryDao),
                        COMMAND_PREFIX,
                        CMD,
                        AD_SELECTION_ID,
                        Long.toString(AD_SELECTION_ENTRY.getAdSelectionId()));

        expectSuccess(result, EXPECTED_COMMAND);
        AuctionResult auctionResult =
                AuctionResult.parseFrom(
                        Base64.decode(
                                new JSONObject(result.mOut).getString(OUTPUT_PROTO_FIELD_NAME),
                                Base64.DEFAULT));
        expect.that(
                        auctionResult
                                .getWinReportingUrls()
                                .getTopLevelSellerReportingUrls()
                                .getInteractionReportingUrlsMap()
                                .get(SELLER_VIEW_AD_INTERACTION.getInteractionKey()))
                .isEqualTo(SELLER_VIEW_AD_INTERACTION.getInteractionReportingUri().toString());
        expect.that(
                        auctionResult
                                .getWinReportingUrls()
                                .getBuyerReportingUrls()
                                .getInteractionReportingUrlsMap()
                                .get(BUYER_CLICK_AD_INTERACTION.getInteractionKey()))
                .isEqualTo(BUYER_CLICK_AD_INTERACTION.getInteractionReportingUri().toString());
    }

    @Test
    public void testRun_withEmptyReportingUris_fieldNotPresent()
            throws JSONException, InvalidProtocolBufferException {
        when(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(true);
        when(mAdSelectionEntryDao.getAdSelectionEntityById(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(AD_SELECTION_ENTRY);
        when(mAdSelectionEntryDao.getReportingUris(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(null);
        when(mAdSelectionEntryDao.listRegisteredAdInteractions(
                        AD_SELECTION_ENTRY.getAdSelectionId(), FLAG_REPORTING_DESTINATION_BUYER))
                .thenReturn(List.of());
        when(mAdSelectionEntryDao.listRegisteredAdInteractions(
                        AD_SELECTION_ENTRY.getAdSelectionId(), FLAG_REPORTING_DESTINATION_SELLER))
                .thenReturn(List.of());

        Result result =
                run(
                        new ViewAuctionResultCommand(mAdSelectionEntryDao),
                        COMMAND_PREFIX,
                        CMD,
                        AD_SELECTION_ID,
                        Long.toString(AD_SELECTION_ENTRY.getAdSelectionId()));

        expectSuccess(result, EXPECTED_COMMAND);
        AuctionResult auctionResult =
                AuctionResult.parseFrom(
                        Base64.decode(
                                new JSONObject(result.mOut).getString(OUTPUT_PROTO_FIELD_NAME),
                                Base64.DEFAULT));
        expect.that(
                        auctionResult
                                .getWinReportingUrls()
                                .getTopLevelSellerReportingUrls()
                                .getReportingUrl())
                .isEmpty();
        expect.that(auctionResult.getWinReportingUrls().getBuyerReportingUrls().getReportingUrl())
                .isEmpty();
    }

    @Test
    public void testRun_withoutSellerReportingUri_fieldNotPresent()
            throws JSONException, InvalidProtocolBufferException {
        when(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(true);
        when(mAdSelectionEntryDao.getAdSelectionEntityById(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(AD_SELECTION_ENTRY);
        when(mAdSelectionEntryDao.getReportingUris(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(
                        ReportingData.builder()
                                .setBuyerWinReportingUri(ReportingDataFixture.BUYER_REPORTING_URI_1)
                                .build());
        when(mAdSelectionEntryDao.listRegisteredAdInteractions(
                        AD_SELECTION_ENTRY.getAdSelectionId(), FLAG_REPORTING_DESTINATION_BUYER))
                .thenReturn(List.of());
        when(mAdSelectionEntryDao.listRegisteredAdInteractions(
                        AD_SELECTION_ENTRY.getAdSelectionId(), FLAG_REPORTING_DESTINATION_SELLER))
                .thenReturn(List.of());

        Result result =
                run(
                        new ViewAuctionResultCommand(mAdSelectionEntryDao),
                        COMMAND_PREFIX,
                        CMD,
                        AD_SELECTION_ID,
                        Long.toString(AD_SELECTION_ENTRY.getAdSelectionId()));

        expectSuccess(result, EXPECTED_COMMAND);
        AuctionResult auctionResult =
                AuctionResult.parseFrom(
                        Base64.decode(
                                new JSONObject(result.mOut).getString(OUTPUT_PROTO_FIELD_NAME),
                                Base64.DEFAULT));
        expect.that(
                        auctionResult
                                .getWinReportingUrls()
                                .getTopLevelSellerReportingUrls()
                                .getReportingUrl())
                .isEmpty();
        expect.that(auctionResult.getWinReportingUrls().getBuyerReportingUrls().getReportingUrl())
                .isEqualTo(ReportingDataFixture.BUYER_REPORTING_URI_1.toString());
    }

    @Test
    public void testRun_withoutBuyerReportingUri_fieldNotPresent()
            throws JSONException, InvalidProtocolBufferException {
        when(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(true);
        when(mAdSelectionEntryDao.getAdSelectionEntityById(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(AD_SELECTION_ENTRY);
        when(mAdSelectionEntryDao.getReportingUris(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(
                        ReportingData.builder()
                                .setSellerWinReportingUri(
                                        ReportingDataFixture.SELLER_REPORTING_URI_1)
                                .build());
        when(mAdSelectionEntryDao.listRegisteredAdInteractions(
                        AD_SELECTION_ENTRY.getAdSelectionId(), FLAG_REPORTING_DESTINATION_BUYER))
                .thenReturn(List.of());
        when(mAdSelectionEntryDao.listRegisteredAdInteractions(
                        AD_SELECTION_ENTRY.getAdSelectionId(), FLAG_REPORTING_DESTINATION_SELLER))
                .thenReturn(List.of());

        Result result =
                run(
                        new ViewAuctionResultCommand(mAdSelectionEntryDao),
                        COMMAND_PREFIX,
                        CMD,
                        AD_SELECTION_ID,
                        Long.toString(AD_SELECTION_ENTRY.getAdSelectionId()));

        expectSuccess(result, EXPECTED_COMMAND);
        AuctionResult auctionResult =
                AuctionResult.parseFrom(
                        Base64.decode(
                                new JSONObject(result.mOut).getString(OUTPUT_PROTO_FIELD_NAME),
                                Base64.DEFAULT));
        expect.that(
                        auctionResult
                                .getWinReportingUrls()
                                .getTopLevelSellerReportingUrls()
                                .getReportingUrl())
                .isEqualTo(ReportingDataFixture.SELLER_REPORTING_URI_1.toString());
        expect.that(auctionResult.getWinReportingUrls().getBuyerReportingUrls().getReportingUrl())
                .isEmpty();
    }
}
