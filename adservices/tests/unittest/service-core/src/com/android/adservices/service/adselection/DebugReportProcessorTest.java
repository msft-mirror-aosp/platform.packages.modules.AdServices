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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;

import android.adservices.common.AdTechIdentifier;
import android.net.Uri;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class DebugReportProcessorTest {

    private static final String CUSTOM_AUDIENCE_NAME_1 = "example_ca_1";
    private static final String CUSTOM_AUDIENCE_NAME_2 = "example_ca_2";
    private static final String CUSTOM_AUDIENCE_NAME_3 = "example_ca_3";
    public static final AdTechIdentifier AD_TECH_IDENTIFIER_1 =
            AdTechIdentifier.fromString("example.com");
    public static final AdTechIdentifier AD_TECH_IDENTIFIER_2 =
            AdTechIdentifier.fromString("google.com");

    public static final double AD_BID_1 = 1.0;
    public static final double AD_BID_2 = 2.0;

    @Test
    public void singleBuyerSessionSuccessfulCase_returnsWinUri() {
        Uri winUri = Uri.parse("https://example.com/reportWin");
        Uri lossUri = Uri.parse("https://example.com/reportLoss");
        PostAuctionSignals signals = newDefaultPostAuctionSignals().build();
        DebugReport debugReport =
                DebugReport.builder()
                        .setLossDebugReportUri(lossUri)
                        .setWinDebugReportUri(winUri)
                        .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                        .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                        .build();

        List<Uri> uris = DebugReportProcessor.getUrisFromAdAuction(List.of(debugReport), signals);

        assertThat(uris).containsExactly(winUri);
    }

    @Test
    public void multiBuyerSessionFailedCase_returnsLosingUri() {
        Uri lossUri1 = Uri.parse("https://example.com/reportLoss");
        Uri lossUri2 = Uri.parse("https://google.com/reportLoss");
        PostAuctionSignals signals = newDefaultPostAuctionSignals().build();
        DebugReport debugReport =
                DebugReport.builder()
                        .setLossDebugReportUri(lossUri1)
                        .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                        .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_2)
                        .build();
        DebugReport debugReport2 =
                DebugReport.builder()
                        .setLossDebugReportUri(lossUri2)
                        .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_2)
                        .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_3)
                        .build();

        List<Uri> uris =
                DebugReportProcessor.getUrisFromAdAuction(
                        List.of(debugReport, debugReport2), signals);

        assertThat(uris).containsExactly(lossUri1, lossUri2);
    }

    @Test
    public void singleBuyerSessionWithWinningBid_returnsWinningUri() {
        Uri winUri = Uri.parse("https://example.com?b=${winningBid}");
        PostAuctionSignals signals = newDefaultPostAuctionSignals().build();
        DebugReport debugReport =
                DebugReport.builder()
                        .setWinDebugReportUri(winUri)
                        .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                        .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                        .build();

        List<Uri> uris = DebugReportProcessor.getUrisFromAdAuction(List.of(debugReport), signals);

        assertThat(Objects.requireNonNull(uris).get(0).toString())
                .isEqualTo("https://example.com?b=1.0");
    }

    @Test
    public void singleBuyerSessionWithMismatchedAdTech_returnsNoUri() {
        AdTechIdentifier buyer = AdTechIdentifier.fromString("not_google.com");
        String firstDomain = "google.com";
        PostAuctionSignals signals = newDefaultPostAuctionSignals().setWinningBuyer(buyer).build();
        DebugReport debugReport =
                DebugReport.builder()
                        .setWinDebugReportUri(Uri.parse(firstDomain))
                        .setCustomAudienceBuyer(buyer)
                        .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                        .build();

        List<Uri> uris = DebugReportProcessor.getUrisFromAdAuction(List.of(debugReport), signals);

        assertThat(uris).isEmpty();
    }

    @Test
    public void singleBuyerSessionWithEmptyUri_returnsNoUri() {
        PostAuctionSignals signals = newDefaultPostAuctionSignals().build();
        DebugReport debugReport =
                DebugReport.builder()
                        .setWinDebugReportUri(Uri.EMPTY)
                        .setLossDebugReportUri(Uri.EMPTY)
                        .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                        .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                        .build();

        List<Uri> uris = DebugReportProcessor.getUrisFromAdAuction(List.of(debugReport), signals);

        assertThat(uris).isEmpty();
    }

    @Test
    public void singleBuyerSessionWithNonHttpsUri_returnsNoUri() {
        PostAuctionSignals signals = newDefaultPostAuctionSignals().build();
        DebugReport debugReport =
                DebugReport.builder()
                        .setLossDebugReportUri(Uri.parse("http://example.com"))
                        .setLossDebugReportUri(Uri.parse("http://example.com"))
                        .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                        .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                        .build();

        List<Uri> uris = DebugReportProcessor.getUrisFromAdAuction(List.of(debugReport), signals);

        assertThat(uris).isEmpty();
    }

    @Test
    public void singleBuyerSessionWithUriOverMaximumSize_returnsNoUri() {
        // There is a 4 KB limit on URL length. Java chars are 2 bytes each, so this will hit the
        // limit.
        int numberOfChars = 2500;
        Uri overlyLongUri =
                Uri.parse(
                        String.format(
                                "https://example.com/%s", generateRandomString(numberOfChars)));
        PostAuctionSignals signals = newDefaultPostAuctionSignals().build();
        DebugReport debugReport =
                DebugReport.builder()
                        .setLossDebugReportUri(overlyLongUri)
                        .setLossDebugReportUri(overlyLongUri)
                        .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                        .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                        .build();

        List<Uri> uris = DebugReportProcessor.getUrisFromAdAuction(List.of(debugReport), signals);

        assertThat(uris).isEmpty();
    }

    @Test
    public void singleBuyerSessionWithTooManyUrisPerAdTech_returnsLimitedUris() {
        PostAuctionSignals signals = newDefaultPostAuctionSignals().setWinningBid(0.0).build();
        int numberOfDebugReportsPerAdTech = 100;
        List<DebugReport> debugReports = new ArrayList<>();
        for (int i = 0; i < numberOfDebugReportsPerAdTech; i++) {
            debugReports.add(
                    DebugReport.builder()
                            .setLossDebugReportUri(makeUri(AD_TECH_IDENTIFIER_1, i))
                            .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                            .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1 + "_" + i)
                            .build());
            debugReports.add(
                    DebugReport.builder()
                            .setLossDebugReportUri(makeUri(AD_TECH_IDENTIFIER_2, i))
                            .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_2)
                            .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_2 + "_" + i)
                            .build());
        }

        List<Uri> uris = DebugReportProcessor.getUrisFromAdAuction(debugReports, signals);

        assertThat(uris)
                .hasSize(DebugReportProcessor.MAX_NUMBER_OF_URIS_PER_AUCTION_PER_AD_TECH * 2);
    }

    @Test
    public void singleBuyerSessionWithSellerUris_returnsWinUri() {
        Uri winUri = Uri.parse("https://google.com/reportWin");
        Uri lossUri = Uri.parse("https://google.com/reportLoss");
        PostAuctionSignals signals = newDefaultPostAuctionSignals().build();
        DebugReport debugReport =
                DebugReport.builder()
                        .setLossDebugReportUri(lossUri)
                        .setWinDebugReportUri(winUri)
                        .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                        .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                        .setSeller(AD_TECH_IDENTIFIER_2)
                        .build();

        List<Uri> uris = DebugReportProcessor.getUrisFromAdAuction(List.of(debugReport), signals);

        assertThat(uris).containsExactly(winUri);
    }
    @Test
    public void singleBuyerSessionWithComplexUrlVariables_returnsAllVariablesCorrectly() {
        // Test both changes to path and query parameters.
        Uri winUri =
                Uri.parse(
                        "https://example.com/reportWin/${winningBid}/?s=${highestScoringOtherBid}&test=123");
        Uri expectedUri = Uri.parse("https://example.com/reportWin/1.0/?s=2.0&test=123");
        PostAuctionSignals signals =
                newDefaultPostAuctionSignals()
                        .setSecondHighestScoredBuyer(AD_TECH_IDENTIFIER_1)
                        .setSecondHighestScoredBid(AD_BID_2)
                        .build();
        DebugReport debugReport =
                DebugReport.builder()
                        .setWinDebugReportUri(winUri)
                        .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                        .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                        .build();

        List<Uri> uris = DebugReportProcessor.getUrisFromAdAuction(List.of(debugReport), signals);

        assertThat(Objects.requireNonNull(uris).size()).isEqualTo(1);
        assertEquals(expectedUri, uris.get(0));
    }

    @Test
    public void singleBuyerSessionWithNoWinner_returnsUnknownWinningBid() {
        Uri lossUri = Uri.parse("https://example.com/reportLoss?wb=${winningBid}");
        Uri expectedUri = Uri.parse("https://example.com/reportLoss?wb=0.0");
        PostAuctionSignals signals = PostAuctionSignals.builder().build();
        DebugReport debugReport =
                DebugReport.builder()
                        .setLossDebugReportUri(lossUri)
                        .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                        .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                        .build();

        List<Uri> uris = DebugReportProcessor.getUrisFromAdAuction(List.of(debugReport), signals);

        assertThat(Objects.requireNonNull(uris).size()).isEqualTo(1);
        assertEquals(expectedUri, uris.get(0));
    }

    @Test
    public void singleBuyerSessionWithNoWinner_returnsUnknownHighestOtherBid() {
        Uri lossUri = Uri.parse("https://example.com/reportLoss?hob=${highestScoringOtherBid}");
        Uri expectedUri = Uri.parse("https://example.com/reportLoss?hob=0.0");
        PostAuctionSignals signals = PostAuctionSignals.builder().build();
        DebugReport debugReport =
                DebugReport.builder()
                        .setLossDebugReportUri(lossUri)
                        .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                        .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                        .build();

        List<Uri> uris = DebugReportProcessor.getUrisFromAdAuction(List.of(debugReport), signals);

        assertThat(Objects.requireNonNull(uris).size()).isEqualTo(1);
        assertEquals(expectedUri, uris.get(0));
    }

    @Test
    public void multiBuyerLostSession_returnsWinningBid() {
        Uri lossUri = Uri.parse("https://example.com/reportLoss?wb=${winningBid}");
        Uri expectedUri = Uri.parse("https://example.com/reportLoss?wb=1.0");
        PostAuctionSignals signals =
                newDefaultPostAuctionSignals()
                        .setWinningBuyer(AD_TECH_IDENTIFIER_2)
                        .setWinningCustomAudienceName(CUSTOM_AUDIENCE_NAME_2)
                        .build();
        DebugReport debugReport =
                DebugReport.builder()
                        .setLossDebugReportUri(lossUri)
                        .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                        .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                        .build();

        List<Uri> uris = DebugReportProcessor.getUrisFromAdAuction(List.of(debugReport), signals);

        assertThat(Objects.requireNonNull(uris).size()).isEqualTo(1);
        assertEquals(expectedUri, uris.get(0));
    }

    @Test
    public void multiBuyerWonSession_returnsMadeWinningBid() {
        Uri winUri = Uri.parse("https://example.com/${madeWinningBid}");
        Uri lossUri = Uri.parse("https://google.com/${madeWinningBid}");
        DebugReport debugReport =
                DebugReport.builder()
                        .setLossDebugReportUri(winUri)
                        .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                        .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                        .build();
        DebugReport debugReport2 =
                DebugReport.builder()
                        .setLossDebugReportUri(lossUri)
                        .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_2)
                        .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_2)
                        .build();

        List<Uri> uris =
                DebugReportProcessor.getUrisFromAdAuction(
                        List.of(debugReport, debugReport2), newDefaultPostAuctionSignals().build());

        assertThat(uris)
                .containsExactly(
                        Uri.parse("https://google.com/false"),
                        Uri.parse("https://example.com/true"));
    }

    @Test
    public void multiBuyerWonSession_returnsOtherWinningBid() {
        Uri lossUri = Uri.parse("https://example.com/reportLoss?wb=${highestScoringOtherBid}");
        PostAuctionSignals signals =
                newDefaultPostAuctionSignals()
                        .setWinningBuyer(AD_TECH_IDENTIFIER_2)
                        .setWinningCustomAudienceName(CUSTOM_AUDIENCE_NAME_2)
                        .setSecondHighestScoredBid(AD_BID_2)
                        .setSecondHighestScoredBuyer(AD_TECH_IDENTIFIER_2)
                        .build();
        DebugReport debugReport =
                DebugReport.builder()
                        .setLossDebugReportUri(lossUri)
                        .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                        .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                        .build();

        List<Uri> uris = DebugReportProcessor.getUrisFromAdAuction(List.of(debugReport), signals);

        Uri actualUri = Objects.requireNonNull(uris).get(0);
        assertEquals(Uri.parse("https://example.com/reportLoss?wb=2.0"), actualUri);
    }

    @Test
    public void singleBuyerLostSession_returnsMadeOtherWinningBid() {
        Uri winUri = Uri.parse("https://example.com/reportWin?wb=${madeHighestScoringOtherBid}");
        PostAuctionSignals signals =
                newDefaultPostAuctionSignals()
                        .setSecondHighestScoredBid(AD_BID_2)
                        .setSecondHighestScoredBuyer(AD_TECH_IDENTIFIER_1)
                        .build();
        DebugReport debugReport =
                DebugReport.builder()
                        .setWinDebugReportUri(winUri)
                        .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                        .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                        .build();

        List<Uri> uris = DebugReportProcessor.getUrisFromAdAuction(List.of(debugReport), signals);

        assertThat(uris).containsExactly(Uri.parse("https://example.com/reportWin?wb=true"));
    }

    @Test
    public void singleBuyerWithPartialResults_returnsBuyerLossUriCorrectly() {
        Uri lossUri = Uri.parse("https://example.com/reportLoss");
        DebugReport debugReport =
                DebugReport.builder()
                        .setLossDebugReportUri(lossUri)
                        .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                        .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_2)
                        .build();

        List<Uri> uris =
                DebugReportProcessor.getUrisFromAdAuction(
                        List.of(debugReport), newDefaultPostAuctionSignals().build());

        assertThat(uris).containsExactly(lossUri);
    }

    @Test
    public void emptySession_doesNotThrow() {
        List<Uri> uris =
                DebugReportProcessor.getUrisFromAdAuction(
                        List.of(), PostAuctionSignals.builder().build());

        assertThat(uris).isEmpty();
    }

    private static PostAuctionSignals.Builder newDefaultPostAuctionSignals() {
        return PostAuctionSignals.builder()
                .setWinningBuyer(AD_TECH_IDENTIFIER_1)
                .setWinningCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                .setWinningBid(AD_BID_1);
    }
    private static Uri makeUri(AdTechIdentifier adTechIdentifier, int i) {
        // Quickly create lots of small Uris for testing without using randomness.
        return Uri.parse(String.format("https://%s/report%d", adTechIdentifier, i));
    }

    private static String generateRandomString(int numberOfChars) {
        StringBuilder buffer = new StringBuilder();
        char[] alphabet = "abcdefg123456".toCharArray();
        for (int i = 0; i < numberOfChars; i++) {
            int j = new Random().nextInt(alphabet.length);
            buffer.append(alphabet[j]);
        }
        return buffer.toString();
    }
}
