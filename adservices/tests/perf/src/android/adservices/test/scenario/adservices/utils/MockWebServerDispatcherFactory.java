/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.adservices.test.scenario.adservices.utils;

import android.adservices.common.AdSelectionSignals;

import com.google.common.collect.ImmutableList;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.RecordedRequest;

import java.util.ArrayList;
import java.util.Arrays;

/** Setup the dispatcher for mock web server. */
public final class MockWebServerDispatcherFactory {

    public static final String DECISION_LOGIC_PATH = "/seller/decision/simple_logic_with_delay";
    public static final String TRUSTED_SCORING_SIGNAL_PATH =
            "/trusted/scoringsignals/simple_with_delay";
    public static final String TRUSTED_BIDDING_SIGNALS_PATH =
            "/trusted/biddingsignals/simple_with_delay";
    public static final ArrayList<String> VALID_TRUSTED_BIDDING_KEYS =
            new ArrayList<>(Arrays.asList("example", "valid", "list", "of", "keys"));
    // Estimated based on
    // https://docs.google.com/spreadsheets/d/1EP_cwBbwYI-NMro0Qq5uif1krwjIQhjK8fjOu15j7hQ/edit?usp=sharing&resourcekey=0-A67kzEnAKKz1k7qpshSedg
    public static final int SCORING_JS_EXECUTION_TIME_p50_MS = 40;
    public static final int BIDDING_JS_EXECUTION_TIME_p50_MS = 40;
    public static final int SCORING_JS_EXECUTION_TIME_p90_MS = 70;
    public static final int BIDDING_JS_EXECUTION_TIME_p90_MS = 70;
    public static final int DECISION_LOGIC_FETCH_DELAY_5G_p50_MS = 22;
    public static final int DECISION_LOGIC_FETCH_DELAY_5G_p90_MS = 23;
    public static final int DECISION_LOGIC_FETCH_DELAY_4GPLUS_p50_MS = 56;
    public static final int DECISION_LOGIC_FETCH_DELAY_4GPLUS_p90_MS = 57;
    public static final int DECISION_LOGIC_FETCH_DELAY_4G_p50_MS = 114;
    public static final int DECISION_LOGIC_FETCH_DELAY_4G_p90_MS = 116;
    public static final int BIDDING_LOGIC_FETCH_DELAY_5G_p50_MS = 23;
    public static final int BIDDING_LOGIC_FETCH_DELAY_5G_p90_MS = 25;
    public static final int BIDDING_LOGIC_FETCH_DELAY_4GPLUS_p50_MS = 57;
    public static final int BIDDING_LOGIC_FETCH_DELAY_4GPLUS_p90_MS = 62;
    public static final int BIDDING_LOGIC_FETCH_DELAY_4G_p50_MS = 116;
    public static final int BIDDING_LOGIC_FETCH_DELAY_4G_p90_MS = 128;
    public static final int SCORING_SIGNALS_FETCH_DELAY_5G_p50_MS = 21;
    public static final int SCORING_SIGNALS_FETCH_DELAY_5G_p90_MS = 22;
    public static final int SCORING_SIGNALS_FETCH_DELAY_4GPLUS_p50_MS = 51;
    public static final int SCORING_SIGNALS_FETCH_DELAY_4GPLUS_p90_MS = 52;
    public static final int SCORING_SIGNALS_FETCH_DELAY_4G_p50_MS = 101;
    public static final int SCORING_SIGNALS_FETCH_DELAY_4G_p90_MS = 104;
    public static final int BIDDING_SIGNALS_FETCH_DELAY_5G_p50_MS = 22;
    public static final int BIDDING_SIGNALS_FETCH_DELAY_5G_p90_MS = 47;
    public static final int BIDDING_SIGNALS_FETCH_DELAY_4GPLUS_p50_MS = 53;
    public static final int BIDDING_SIGNALS_FETCH_DELAY_4GPLUS_p90_MS = 123;
    public static final int BIDDING_SIGNALS_FETCH_DELAY_4G_p50_MS = 105;
    public static final int BIDDING_SIGNALS_FETCH_DELAY_4G_p90_MS = 275;
    private static final String BUYER_REPORTING_PATH = "/reporting/buyer";
    private static final String SELLER_REPORTING_PATH = "/reporting/seller";
    private static final String BUYER_BIDDING_LOGIC_URI_PATH =
            "/buyer/bidding/simple_logic_with_delay";
    private static final String DEFAULT_DECISION_LOGIC_JS_WITH_EXECUTION_TIME_FORMAT =
            "function scoreAd(ad, bid, auction_config, seller_signals,"
                    + " trusted_scoring_signals, contextual_signal, user_signal,"
                    + " custom_audience_signal) { \n"
                    + " const start = Date.now(); let now = start; while (now-start < %d) "
                    + "{now=Date.now();}\n"
                    + "  return {'status': 0, 'score': bid };\n"
                    + "}\n"
                    + "function reportResult(ad_selection_config, render_uri, bid,"
                    + " contextual_signals) { \n"
                    + " return {'status': 0, 'results': {'signals_for_buyer':"
                    + " '{\"signals_for_buyer\":1}', 'reporting_uri': '%s"
                    + "' } };\n"
                    + "}";
    private static final String DEFAULT_BIDDING_LOGIC_JS_WITH_EXECUTION_TIME_FORMAT =
            "function generateBid(ad, auction_signals, per_buyer_signals,"
                    + " trusted_bidding_signals, contextual_signals,"
                    + " custom_audience_signals) { \n"
                    + " const start = Date.now(); let now = start; while (now-start < %d) "
                    + "{now=Date.now();}\n"
                    + " return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                    + "}\n"
                    + "function reportWin(ad_selection_signals, per_buyer_signals,"
                    + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                    + " return {'status': 0, 'results': {'reporting_uri': '%s"
                    + "' } };\n"
                    + "}";
    private static final AdSelectionSignals TRUSTED_SCORING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"render_uri_1\": \"signals_for_1\",\n"
                            + "\t\"render_uri_2\": \"signals_for_2\"\n"
                            + "}");
    private static final AdSelectionSignals TRUSTED_BIDDING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"example\": \"example\",\n"
                            + "\t\"valid\": \"Also valid\",\n"
                            + "\t\"list\": \"list\",\n"
                            + "\t\"of\": \"of\",\n"
                            + "\t\"keys\": \"trusted bidding signal Values\"\n"
                            + "}");

    public static Dispatcher create5Gp50LatencyDispatcher(MockWebServerRule mockWebServerRule) {
        return create(
                DECISION_LOGIC_FETCH_DELAY_5G_p50_MS,
                BIDDING_LOGIC_FETCH_DELAY_5G_p50_MS,
                SCORING_SIGNALS_FETCH_DELAY_5G_p50_MS,
                BIDDING_SIGNALS_FETCH_DELAY_5G_p50_MS,
                BIDDING_JS_EXECUTION_TIME_p50_MS,
                SCORING_JS_EXECUTION_TIME_p50_MS,
                mockWebServerRule);
    }

    public static Dispatcher create5Gp90LatencyDispatcher(MockWebServerRule mockWebServerRule) {
        return create(
                DECISION_LOGIC_FETCH_DELAY_5G_p90_MS,
                BIDDING_LOGIC_FETCH_DELAY_5G_p90_MS,
                SCORING_SIGNALS_FETCH_DELAY_5G_p90_MS,
                BIDDING_SIGNALS_FETCH_DELAY_5G_p90_MS,
                BIDDING_JS_EXECUTION_TIME_p90_MS,
                SCORING_JS_EXECUTION_TIME_p90_MS,
                mockWebServerRule);
    }

    public static Dispatcher create4GPlusp50LatencyDispatcher(MockWebServerRule mockWebServerRule) {
        return create(
                DECISION_LOGIC_FETCH_DELAY_4GPLUS_p50_MS,
                BIDDING_LOGIC_FETCH_DELAY_4GPLUS_p50_MS,
                SCORING_SIGNALS_FETCH_DELAY_4GPLUS_p50_MS,
                BIDDING_SIGNALS_FETCH_DELAY_4GPLUS_p50_MS,
                BIDDING_JS_EXECUTION_TIME_p50_MS,
                SCORING_JS_EXECUTION_TIME_p50_MS,
                mockWebServerRule);
    }

    public static Dispatcher create4GPlusp90LatencyDispatcher(MockWebServerRule mockWebServerRule) {
        return create(
                DECISION_LOGIC_FETCH_DELAY_4GPLUS_p90_MS,
                BIDDING_LOGIC_FETCH_DELAY_4GPLUS_p90_MS,
                SCORING_SIGNALS_FETCH_DELAY_4GPLUS_p90_MS,
                BIDDING_SIGNALS_FETCH_DELAY_4GPLUS_p90_MS,
                BIDDING_JS_EXECUTION_TIME_p90_MS,
                SCORING_JS_EXECUTION_TIME_p90_MS,
                mockWebServerRule);
    }

    public static Dispatcher create4Gp50LatencyDispatcher(MockWebServerRule mockWebServerRule) {
        return create(
                DECISION_LOGIC_FETCH_DELAY_4G_p50_MS,
                BIDDING_LOGIC_FETCH_DELAY_4G_p50_MS,
                SCORING_SIGNALS_FETCH_DELAY_4G_p50_MS,
                BIDDING_SIGNALS_FETCH_DELAY_4G_p50_MS,
                BIDDING_JS_EXECUTION_TIME_p50_MS,
                SCORING_JS_EXECUTION_TIME_p50_MS,
                mockWebServerRule);
    }

    public static Dispatcher create4Gp90LatencyDispatcher(MockWebServerRule mockWebServerRule) {
        return create(
                DECISION_LOGIC_FETCH_DELAY_4G_p90_MS,
                BIDDING_LOGIC_FETCH_DELAY_4G_p90_MS,
                SCORING_SIGNALS_FETCH_DELAY_4G_p90_MS,
                BIDDING_SIGNALS_FETCH_DELAY_4G_p90_MS,
                BIDDING_JS_EXECUTION_TIME_p90_MS,
                SCORING_JS_EXECUTION_TIME_p90_MS,
                mockWebServerRule);
    }

    private static Dispatcher create(
            int decisionLogicFetchDelayMs,
            int biddingLogicFetchDelayMs,
            int scoringSignalFetchDelayMs,
            int biddingSignalFetchDelayMs,
            int biddingLogicExecutionRunMs,
            int scoringLogicExecutionRunMs,
            MockWebServerRule mockWebServerRule) {

        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (DECISION_LOGIC_PATH.equals(request.getPath())) {
                    return new MockResponse()
                            .setBodyDelayTimeMs(decisionLogicFetchDelayMs)
                            .setBody(
                                    getDecisionLogicJS(
                                            scoringLogicExecutionRunMs,
                                            mockWebServerRule
                                                    .uriForPath(SELLER_REPORTING_PATH)
                                                    .toString()));
                } else if (BUYER_BIDDING_LOGIC_URI_PATH.equals(request.getPath())) {
                    return new MockResponse()
                            .setBodyDelayTimeMs(biddingLogicFetchDelayMs)
                            .setBody(
                                    getBiddingLogicJS(
                                            biddingLogicExecutionRunMs,
                                            mockWebServerRule
                                                    .uriForPath(BUYER_REPORTING_PATH)
                                                    .toString()));
                } else if (BUYER_REPORTING_PATH.equals(request.getPath())
                        || SELLER_REPORTING_PATH.equals(request.getPath())) {
                    return new MockResponse().setBody("");
                } else if (request.getPath().startsWith(TRUSTED_SCORING_SIGNAL_PATH)) {
                    return new MockResponse()
                            .setBodyDelayTimeMs(scoringSignalFetchDelayMs)
                            .setBody(TRUSTED_SCORING_SIGNALS.toString());
                } else if (request.getPath().startsWith(TRUSTED_BIDDING_SIGNALS_PATH)) {
                    return new MockResponse()
                            .setBodyDelayTimeMs(biddingSignalFetchDelayMs)
                            .setBody(TRUSTED_BIDDING_SIGNALS.toString());
                }
                return new MockResponse().setResponseCode(404);
            }
        };
    }

    public static String getBiddingLogicUriPath() {
        return BUYER_BIDDING_LOGIC_URI_PATH;
    }

    public static String getDecisionLogicPath() {
        return DECISION_LOGIC_PATH;
    }

    public static String getTrustedScoringSignalPath() {
        return TRUSTED_SCORING_SIGNAL_PATH;
    }

    public static ImmutableList<String> getValidTrustedBiddingKeys() {
        return ImmutableList.copyOf(VALID_TRUSTED_BIDDING_KEYS);
    }

    public static String getTrustedBiddingSignalsPath() {
        return TRUSTED_BIDDING_SIGNALS_PATH;
    }

    private static String getDecisionLogicJS(
            int scoringLogicExecutionRunMs, String sellerReportingUri) {
        return String.format(
                DEFAULT_DECISION_LOGIC_JS_WITH_EXECUTION_TIME_FORMAT,
                scoringLogicExecutionRunMs,
                sellerReportingUri);
    }

    private static String getBiddingLogicJS(
            int biddingLogicExecutionRunMs, String buyerReportingUri) {
        return String.format(
                DEFAULT_BIDDING_LOGIC_JS_WITH_EXECUTION_TIME_FORMAT,
                biddingLogicExecutionRunMs,
                buyerReportingUri);
    }
}
