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

import android.adservices.adselection.AdWithBid;
import android.adservices.common.AdData;
import android.adservices.common.AdTechIdentifier;
import android.net.Uri;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class DebugReportProcessorTest {

    private static final AdWithBid AD_WITH_BID_1 =
            new AdWithBid(
                    new AdData.Builder().setMetadata("").setRenderUri(Uri.EMPTY).build(), 1.0);
    private static final AdWithBid AD_WITH_BID_2 =
            new AdWithBid(
                    new AdData.Builder().setMetadata("").setRenderUri(Uri.EMPTY).build(), 2.0);
    private static final String CUSTOM_AUDIENCE_NAME_1 = "example_ca_1";
    private static final String CUSTOM_AUDIENCE_NAME_2 = "example_ca_2";
    private static final String CUSTOM_AUDIENCE_NAME_3 = "example_ca_3";
    private static final String OWNER_APP_PACKAGE = "com.android.adservices.example";
    public static final AdTechIdentifier AD_TECH_IDENTIFIER_1 =
            AdTechIdentifier.fromString("example.com");
    public static final AdTechIdentifier AD_TECH_IDENTIFIER_2 =
            AdTechIdentifier.fromString("google.com");
    public static final double LOST_AD_SCORE = 0.0;
    public static final double WINNING_AD_SCORE = 1.0;

    @Test
    public void singleBuyerSessionSuccessfulCase_returnsWinUri() {
        Uri winUri = Uri.parse("https://example.com/reportWin");
        Uri lossUri = Uri.parse("https://example.com/reportLoss");
        List<GenerateBidResult> bidResults =
                List.of(
                        newDefaultGenerateBidResult()
                                .setAdWithBid(AD_WITH_BID_1)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                                .setWinDebugReportUri(winUri)
                                .setLossDebugReportUri(lossUri)
                                .build());
        List<ScoreAdResult> scoreAdResults =
                List.of(
                        newDefaultScoreAdResult()
                                .setAdScore(WINNING_AD_SCORE)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                                .setPublisher(AD_TECH_IDENTIFIER_1)
                                .build());

        List<Uri> uris =
                DebugReportProcessor.getUrisFromAdAuction(
                        bidResults,
                        scoreAdResults,
                        /* topScoringAd= */ scoreAdResults.get(0),
                        /* secondHighestAd= */ null);

        assertThat(uris).containsExactly(winUri);
    }

    @Test
    public void multiBuyerSessionFailedCase_returnsLosingUri() {
        Uri lossUri1 = Uri.parse("https://example.com/reportLoss");
        Uri lossUri2 = Uri.parse("https://google.com/reportLoss");
        List<GenerateBidResult> bidResults =
                List.of(
                        newDefaultGenerateBidResult()
                                .setAdWithBid(AD_WITH_BID_1)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                                .setLossDebugReportUri(lossUri1)
                                .build());
        List<ScoreAdResult> scoreAdResults =
                List.of(
                        newDefaultScoreAdResult()
                                .setAdScore(LOST_AD_SCORE)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                                .setPublisher(AD_TECH_IDENTIFIER_2)
                                .setLossDebugReportUri(lossUri2)
                                .build());

        List<Uri> uris =
                DebugReportProcessor.getUrisFromAdAuction(
                        bidResults,
                        scoreAdResults,
                        /* topScoringAd= */ null,
                        /* secondHighestAd= */ null);

        assertThat(uris).containsExactly(lossUri1, lossUri2);
    }

    @Test
    public void singleBuyerSessionWithWinningBid_returnsWinningUri() {
        Uri winUri = Uri.parse("https://example.com?b=${winningBid}");
        List<GenerateBidResult> bidResults =
                List.of(
                        newDefaultGenerateBidResult()
                                .setAdWithBid(AD_WITH_BID_1)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                                .setWinDebugReportUri(winUri)
                                .build());
        List<ScoreAdResult> scoreAdResults =
                List.of(
                        newDefaultScoreAdResult()
                                .setAdScore(WINNING_AD_SCORE)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                                .setPublisher(AD_TECH_IDENTIFIER_1)
                                .build());

        List<Uri> uris =
                DebugReportProcessor.getUrisFromAdAuction(
                        bidResults,
                        scoreAdResults,
                        /* topScoringAd= */ scoreAdResults.get(0),
                        /* secondHighestAd= */ null);

        assertThat(Objects.requireNonNull(uris).get(0).toString())
                .isEqualTo("https://example.com?b=1.0");
    }

    @Test
    public void singleBuyerSessionWithMismatchedAdTech_returnsNoUri() {
        String firstDomain = "google.com";
        List<GenerateBidResult> bidResults =
                List.of(
                        newDefaultGenerateBidResult()
                                .setWinDebugReportUri(Uri.parse(firstDomain))
                                .setCustomAudienceBuyer(
                                        AdTechIdentifier.fromString("not_google.com"))
                                .build());
        List<ScoreAdResult> scoreAdResults = List.of(newDefaultScoreAdResult().build());

        List<Uri> uris =
                DebugReportProcessor.getUrisFromAdAuction(
                        bidResults,
                        scoreAdResults,
                        /* topScoringAd= */ scoreAdResults.get(0),
                        /* secondHighestAd= */ null);

        assertThat(uris).isEmpty();
    }

    @Test
    public void singleBuyerSessionWithEmptyUri_returnsNoUri() {
        List<GenerateBidResult> bidResults =
                List.of(newDefaultGenerateBidResult().setWinDebugReportUri(Uri.EMPTY).build());
        List<ScoreAdResult> scoreAdResults = List.of(newDefaultScoreAdResult().build());

        List<Uri> uris =
                DebugReportProcessor.getUrisFromAdAuction(bidResults, scoreAdResults, null, null);

        assertThat(uris).isEmpty();
    }

    @Test
    public void singleBuyerSessionWithNonHttpsUri_returnsNoUri() {
        List<GenerateBidResult> bidResults =
                List.of(
                        newDefaultGenerateBidResult()
                                .setWinDebugReportUri(Uri.parse("http://example.com"))
                                .build());
        List<ScoreAdResult> scoreAdResults = List.of(newDefaultScoreAdResult().build());

        List<Uri> uris =
                DebugReportProcessor.getUrisFromAdAuction(bidResults, scoreAdResults, null, null);

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
        List<GenerateBidResult> bidResults =
                List.of(
                        newDefaultGenerateBidResult()
                                .setAdWithBid(AD_WITH_BID_1)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                                .setWinDebugReportUri(overlyLongUri)
                                .build());
        List<ScoreAdResult> scoreAdResults =
                List.of(
                        newDefaultScoreAdResult()
                                .setAdScore(WINNING_AD_SCORE)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                                .setPublisher(AD_TECH_IDENTIFIER_1)
                                .build());

        List<Uri> uris =
                DebugReportProcessor.getUrisFromAdAuction(bidResults, scoreAdResults, null, null);

        assertThat(uris).isEmpty();
    }

    @Test
    public void singleBuyerSessionWithTooManyUrisPerAdTech_returnsLimitedUris() {
        int numberOfCustomAudiences = 100;
        List<GenerateBidResult> generateBidResults = new ArrayList<>();
        List<ScoreAdResult> scoreAdResults = new ArrayList<>();
        for (int i = 0; i < numberOfCustomAudiences; i++) {
            generateBidResults.add(
                    newDefaultGenerateBidResult()
                            .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1 + "_" + i)
                            .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                            .setLossDebugReportUri(makeUri(AD_TECH_IDENTIFIER_1, i))
                            .build());
            scoreAdResults.add(
                    newDefaultScoreAdResult().setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1).build());
        }

        for (int i = 0; i < numberOfCustomAudiences; i++) {
            generateBidResults.add(
                    newDefaultGenerateBidResult()
                            .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_2 + "_" + i)
                            .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_2)
                            .setLossDebugReportUri(makeUri(AD_TECH_IDENTIFIER_2, i))
                            .build());
            scoreAdResults.add(
                    newDefaultScoreAdResult().setCustomAudienceBuyer(AD_TECH_IDENTIFIER_2).build());
        }

        List<Uri> uris =
                DebugReportProcessor.getUrisFromAdAuction(
                        generateBidResults, scoreAdResults, null, null);

        assertThat(uris)
                .hasSize(DebugReportProcessor.MAX_NUMBER_OF_URIS_PER_AUCTION_PER_AD_TECH * 2);
    }

    @Test
    public void singleBuyerSessionWithSellerUris_returnsWinUri() {
        Uri winUri = Uri.parse("https://google.com/reportWin");
        List<GenerateBidResult> bidResults =
                List.of(
                        newDefaultGenerateBidResult()
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                                .build(),
                        newDefaultGenerateBidResult()
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_2)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_2)
                                .build());
        List<ScoreAdResult> scoreAdResults =
                List.of(
                        newDefaultScoreAdResult()
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                                .setPublisher(AD_TECH_IDENTIFIER_2)
                                .setWinDebugReportUri(winUri)
                                .build(),
                        newDefaultScoreAdResult()
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_2)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_2)
                                .setPublisher(AD_TECH_IDENTIFIER_2)
                                .setWinDebugReportUri(winUri)
                                .build());

        List<Uri> uris =
                DebugReportProcessor.getUrisFromAdAuction(
                        bidResults,
                        scoreAdResults,
                        /* topScoringAd= */ scoreAdResults.get(1),
                        null);

        assertThat(uris).containsExactly(winUri);
    }

    @Test
    public void singleBuyerSessionWithSellerRejectReason_returnsCorrectRejectReason() {
        Uri winUri = Uri.parse("https://example.com/reportWin?s=${rejectReason}");
        String sellerRejectReason = "invalid-bid";
        List<GenerateBidResult> bidResults =
                List.of(newDefaultGenerateBidResult().setWinDebugReportUri(winUri).build());
        List<ScoreAdResult> scoreAdResults =
                List.of(
                        newDefaultScoreAdResult()
                                .setAdScore(LOST_AD_SCORE)
                                .setSellerRejectReason(sellerRejectReason)
                                .build());

        List<Uri> uris =
                DebugReportProcessor.getUrisFromAdAuction(
                        bidResults,
                        scoreAdResults,
                        /* topScoringAd= */ scoreAdResults.get(0),
                        /* secondHighestAd= */ null);

        assertThat(Objects.requireNonNull(uris).size()).isEqualTo(1);
        assertThat(uris.get(0).toString())
                .doesNotContain(DebugReportProcessor.REJECT_REASON_VARIABLE_TEMPLATE);
        assertThat(uris.get(0).toString()).contains(sellerRejectReason);
    }

    @Test
    public void singleBuyerSessionWithInvalidSellerRejectReason_returnsUnknownRejectReason() {
        Uri winUri = Uri.parse("https://example.com/reportWin?s=${rejectReason}");
        String sellerRejectReason = "invalid-seller-reject-reason";
        List<GenerateBidResult> bidResults =
                List.of(newDefaultGenerateBidResult().setWinDebugReportUri(winUri).build());
        List<ScoreAdResult> scoreAdResults =
                List.of(
                        newDefaultScoreAdResult()
                                .setSellerRejectReason(sellerRejectReason)
                                .setAdScore(LOST_AD_SCORE)
                                .build());

        List<Uri> uris =
                DebugReportProcessor.getUrisFromAdAuction(
                        bidResults,
                        scoreAdResults,
                        /* topScoringAd= */ scoreAdResults.get(0),
                        /* secondHighestAd= */ null);

        assertThat(Objects.requireNonNull(uris).size()).isEqualTo(1);
        assertThat(uris.get(0).toString())
                .doesNotContain(DebugReportProcessor.REJECT_REASON_VARIABLE_TEMPLATE);
        assertThat(uris.get(0).toString()).contains(DebugReportProcessor.UNKNOWN_VARIABLE_STRING);
    }

    @Test
    public void singleBuyerSessionWithComplexUrlVariables_returnsAllVariablesCorrectly() {
        // Test both changes to path and query parameters.
        Uri winUri =
                Uri.parse(
                        "https://example.com/reportWin/${winningBid}/?s=${rejectReason}&test=123");
        String sellerRejectReason = "invalid-bid";
        List<GenerateBidResult> bidResults =
                List.of(
                        newDefaultGenerateBidResult()
                                .setAdWithBid(AD_WITH_BID_2)
                                .setWinDebugReportUri(winUri)
                                .build());
        List<ScoreAdResult> scoreAdResults =
                List.of(
                        newDefaultScoreAdResult()
                                .setAdScore(WINNING_AD_SCORE)
                                .setSellerRejectReason(sellerRejectReason)
                                .build());

        List<Uri> uris =
                DebugReportProcessor.getUrisFromAdAuction(
                        bidResults, scoreAdResults, scoreAdResults.get(0), null);

        assertThat(Objects.requireNonNull(uris).size()).isEqualTo(1);
        assertThat(uris.get(0).toString())
                .doesNotContain(DebugReportProcessor.WINNING_BID_VARIABLE_TEMPLATE);
        assertThat(uris.get(0).toString())
                .doesNotContain(DebugReportProcessor.REJECT_REASON_VARIABLE_TEMPLATE);
        assertThat(uris.get(0).toString()).contains("invalid-bid");
        assertThat(uris.get(0).toString()).contains(String.valueOf(AD_WITH_BID_2.getBid()));
    }

    @Test
    public void singleBuyerSessionWithNoWinner_returnsUnknownWinningBid() {
        Uri lossUri = Uri.parse("https://example.com/reportLoss?wb=${winningBid}");
        List<GenerateBidResult> bidResults =
                List.of(newDefaultGenerateBidResult().setLossDebugReportUri(lossUri).build());
        List<ScoreAdResult> scoreAdResults =
                List.of(newDefaultScoreAdResult().setAdScore(LOST_AD_SCORE).build());

        List<Uri> uris =
                DebugReportProcessor.getUrisFromAdAuction(bidResults, scoreAdResults, null, null);

        assertThat(Objects.requireNonNull(uris).size()).isEqualTo(1);
        assertThat(uris.get(0).toString())
                .doesNotContain(DebugReportProcessor.WINNING_BID_VARIABLE_TEMPLATE);
        assertThat(uris.get(0).toString()).contains(DebugReportProcessor.UNKNOWN_VARIABLE_STRING);
    }

    @Test
    public void multiBuyerLostSession_returnsWinningBid() {
        Uri lossUri = Uri.parse("https://example.com/reportLoss?wb=${winningBid}");
        List<GenerateBidResult> bidResults =
                List.of(
                        newDefaultGenerateBidResult()
                                .setAdWithBid(AD_WITH_BID_1)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                                .setLossDebugReportUri(lossUri)
                                .build(),
                        newDefaultGenerateBidResult()
                                .setAdWithBid(AD_WITH_BID_2)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_2)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_2)
                                .build());
        List<ScoreAdResult> scoreAdResults =
                List.of(
                        newDefaultScoreAdResult()
                                .setAdScore(LOST_AD_SCORE)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                                .setPublisher(AD_TECH_IDENTIFIER_1)
                                .build(),
                        newDefaultScoreAdResult()
                                .setAdScore(WINNING_AD_SCORE)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_2)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_2)
                                .setPublisher(AD_TECH_IDENTIFIER_2)
                                .build());

        List<Uri> uris =
                DebugReportProcessor.getUrisFromAdAuction(
                        bidResults,
                        scoreAdResults,
                        /* topScoringAd= */ scoreAdResults.get(1),
                        /* secondHighestAd= */ null);

        Uri actualUri = Objects.requireNonNull(uris).get(0);
        assertThat(actualUri.toString())
                .doesNotContain(DebugReportProcessor.WINNING_BID_VARIABLE_TEMPLATE);
        assertThat(actualUri.toString()).contains("2.0");
    }

    @Test
    public void multiBuyerWonSession_returnsMadeWinningBid() {
        Uri winUri = Uri.parse("https://example.com/${madeWinningBid}");
        Uri lossUri = Uri.parse("https://google.com/${madeWinningBid}");
        List<GenerateBidResult> bidResults =
                List.of(
                        newDefaultGenerateBidResult()
                                .setAdWithBid(AD_WITH_BID_1)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                                .setWinDebugReportUri(winUri)
                                .build(),
                        newDefaultGenerateBidResult()
                                .setAdWithBid(AD_WITH_BID_2)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_2)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_2)
                                .setLossDebugReportUri(lossUri)
                                .build());
        List<ScoreAdResult> scoreAdResults =
                List.of(
                        newDefaultScoreAdResult()
                                .setAdScore(WINNING_AD_SCORE)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                                .build(),
                        newDefaultScoreAdResult()
                                .setAdScore(LOST_AD_SCORE)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_2)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_2)
                                .build());

        List<Uri> uris =
                DebugReportProcessor.getUrisFromAdAuction(
                        bidResults,
                        scoreAdResults,
                        /* topScoringAd= */ scoreAdResults.get(0),
                        /* secondHighestAd= */ null);

        assertThat(uris)
                .containsExactly(
                        Uri.parse("https://google.com/false"),
                        Uri.parse("https://example.com/true"));
    }

    @Test
    public void multiBuyerWonSession_returnsOtherWinningBid() {
        Uri lossUri = Uri.parse("https://example.com/reportLoss?wb=${highestScoringOtherBid}");
        List<GenerateBidResult> bidResults =
                List.of(
                        newDefaultGenerateBidResult()
                                .setAdWithBid(AD_WITH_BID_1)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                                .setLossDebugReportUri(lossUri)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                                .build(),
                        newDefaultGenerateBidResult()
                                .setAdWithBid(AD_WITH_BID_1)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_2)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_2)
                                .build(),
                        newDefaultGenerateBidResult()
                                .setAdWithBid(AD_WITH_BID_2)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_3)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_2)
                                .build());
        List<ScoreAdResult> scoreAdResults =
                List.of(
                        newDefaultScoreAdResult()
                                .setAdScore(LOST_AD_SCORE)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                                .setPublisher(AD_TECH_IDENTIFIER_1)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                                .build(),
                        newDefaultScoreAdResult()
                                .setAdScore(WINNING_AD_SCORE)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_2)
                                .setPublisher(AD_TECH_IDENTIFIER_2)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_2)
                                .build(),
                        newDefaultScoreAdResult()
                                .setAdScore(0.5)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_3)
                                .setPublisher(AD_TECH_IDENTIFIER_2)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_2)
                                .build());

        List<Uri> uris =
                DebugReportProcessor.getUrisFromAdAuction(
                        bidResults,
                        scoreAdResults,
                        /* topScoringAd= */ scoreAdResults.get(1),
                        /* secondHighestAd= */ scoreAdResults.get(2));

        Uri actualUri = Objects.requireNonNull(uris).get(0);
        assertThat(actualUri.toString())
                .doesNotContain(DebugReportProcessor.HIGHEST_SCORING_OTHER_BID_VARIABLE_TEMPLATE);
        assertThat(actualUri.toString()).contains("2.0");
    }

    @Test
    public void singleBuyerLostSession_returnsMadeOtherWinningBid() {
        Uri winUri = Uri.parse("https://example.com/reportWin?wb=${madeHighestScoringOtherBid}");
        List<GenerateBidResult> bidResults =
                List.of(
                        newDefaultGenerateBidResult()
                                .setAdWithBid(AD_WITH_BID_1)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                                .setWinDebugReportUri(winUri)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                                .build(),
                        newDefaultGenerateBidResult()
                                .setAdWithBid(AD_WITH_BID_1)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_2)
                                .build(),
                        newDefaultGenerateBidResult()
                                .setAdWithBid(AD_WITH_BID_2)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_3)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                                .build());
        List<ScoreAdResult> scoreAdResults =
                List.of(
                        newDefaultScoreAdResult()
                                .setAdScore(LOST_AD_SCORE)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                                .build(),
                        newDefaultScoreAdResult()
                                .setAdScore(WINNING_AD_SCORE)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                                .build(),
                        newDefaultScoreAdResult()
                                .setAdScore(1.5)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_3)
                                .build());

        List<Uri> uris =
                DebugReportProcessor.getUrisFromAdAuction(
                        bidResults,
                        scoreAdResults,
                        /* topScoringAd= */ scoreAdResults.get(1),
                        /* secondHighestAd= */ scoreAdResults.get(2));

        Uri actualUri = Objects.requireNonNull(uris).get(0);
        assertThat(actualUri.toString())
                .doesNotContain(
                        DebugReportProcessor.MADE_HIGHEST_SCORING_OTHER_BID_VARIABLE_TEMPLATE);
        assertThat(actualUri.toString()).contains("true");
    }

    @Test
    public void singleBuyerWithOutOfOrderResults_returnsCorrectly() {
        Uri buyerWinUri = Uri.parse("https://example.com/reportWin");
        Uri buyerLossUri = Uri.parse("https://example.com/reportLoss");
        Uri sellerWinUri = Uri.parse("https://google.com/reportWin");
        Uri sellerLossUri = Uri.parse("https://google.com/reportLoss");
        List<GenerateBidResult> bidResults =
                List.of(
                        newDefaultGenerateBidResult()
                                .setAdWithBid(AD_WITH_BID_1)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_2)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                                .setLossDebugReportUri(buyerLossUri)
                                .build(),
                        newDefaultGenerateBidResult()
                                .setAdWithBid(AD_WITH_BID_2)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                                .setWinDebugReportUri(buyerWinUri)
                                .build());
        List<ScoreAdResult> scoreAdResults =
                List.of(
                        newDefaultScoreAdResult()
                                .setAdScore(WINNING_AD_SCORE)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                                .setPublisher(AD_TECH_IDENTIFIER_2)
                                .setWinDebugReportUri(sellerWinUri)
                                .build(),
                        newDefaultScoreAdResult()
                                .setAdScore(LOST_AD_SCORE)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_2)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                                .setPublisher(AD_TECH_IDENTIFIER_2)
                                .setLossDebugReportUri(sellerLossUri)
                                .build());

        List<Uri> uris =
                DebugReportProcessor.getUrisFromAdAuction(
                        bidResults,
                        scoreAdResults,
                        /* topScoringAd= */ scoreAdResults.get(0),
                        /* secondHighestAd= */ scoreAdResults.get(1));

        assertThat(uris).containsExactly(buyerWinUri, buyerLossUri, sellerWinUri, sellerLossUri);
    }

    @Test
    public void singleBuyerWithPartialResults_returnsBuyerLossUriCorrectly() {
        Uri lossUri = Uri.parse("https://google.com/reportLoss");
        List<GenerateBidResult> bidResults =
                List.of(
                        newDefaultGenerateBidResult()
                                .setAdWithBid(AD_WITH_BID_1)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                                .build(),
                        newDefaultGenerateBidResult()
                                .setLossDebugReportUri(lossUri)
                                .setAdWithBid(AD_WITH_BID_2)
                                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_2)
                                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_2)
                                .build());
        ScoreAdResult winningAd =
                newDefaultScoreAdResult()
                        .setAdScore(WINNING_AD_SCORE)
                        .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                        .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1)
                        .setPublisher(AD_TECH_IDENTIFIER_1)
                        .build();
        List<ScoreAdResult> scoreAdResults = List.of(winningAd);

        List<Uri> uris =
                DebugReportProcessor.getUrisFromAdAuction(
                        bidResults, scoreAdResults, winningAd, null);

        assertThat(uris).containsExactly(lossUri);
    }

    @Test
    public void emptySession_doesNotThrow() {
        List<GenerateBidResult> bidResults = List.of();
        List<ScoreAdResult> scoreAdResults = List.of();

        List<Uri> uris =
                DebugReportProcessor.getUrisFromAdAuction(bidResults, scoreAdResults, null, null);

        assertThat(uris).isEmpty();
    }

    private static GenerateBidResult.Builder newDefaultGenerateBidResult() {
        return GenerateBidResult.builder()
                .setAdWithBid(AD_WITH_BID_1)
                .setOwnerAppPackage(OWNER_APP_PACKAGE)
                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1);
    }

    private static ScoreAdResult.Builder newDefaultScoreAdResult() {
        return ScoreAdResult.builder()
                .setAdScore(WINNING_AD_SCORE)
                .setOwnerAppPackage(OWNER_APP_PACKAGE)
                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME_1)
                .setPublisher(AD_TECH_IDENTIFIER_1)
                .setCustomAudienceBuyer(AD_TECH_IDENTIFIER_1);
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
