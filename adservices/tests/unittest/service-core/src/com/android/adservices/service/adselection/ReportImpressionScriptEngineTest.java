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

package com.android.adservices.service.adselection;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.service.adselection.ReportImpressionScriptEngine.BuyerReportingResult;
import com.android.adservices.service.adselection.ReportImpressionScriptEngine.ReportingScriptResult;
import com.android.adservices.service.adselection.ReportImpressionScriptEngine.SellerReportingResult;
import com.android.adservices.service.exception.JSExecutionException;
import com.android.adservices.service.js.IsolateSettings;
import com.android.adservices.service.js.JSScriptArgument;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

@SmallTest
public class ReportImpressionScriptEngineTest {
    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String TAG = "ReportImpressionScriptEngineTest";
    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(1);
    IsolateSettings mIsolateSettings = IsolateSettings.forMaxHeapSizeEnforcementDisabled();
    private final ReportImpressionScriptEngine mReportImpressionScriptEngine =
            new ReportImpressionScriptEngine(
                    sContext,
                    () -> mIsolateSettings.getEnforceMaxHeapSizeFeature(),
                    () -> mIsolateSettings.getMaxHeapSizeBytes());

    private static final AdTechIdentifier BUYER_1 = AdSelectionConfigFixture.BUYER_1;

    private final String mResultField = "result";

    private final String mDummyDomain = "http://www.domain.com/adverts/123";

    private final AdSelectionSignals mContextualSignals =
            AdSelectionSignals.fromString("{\"test_contextual_signals\":1}");

    private final AdSelectionSignals mSignalsForBuyer =
            AdSelectionSignals.fromString("{\"test_signals_for_buyer\":1}");

    private final CustomAudienceSignals mCustomAudienceSignals =
            new CustomAudienceSignals.Builder()
                    .setOwner("test_owner")
                    .setBuyer(AdTechIdentifier.fromString("test_buyer"))
                    .setName("test_name")
                    .setActivationTime(Instant.now())
                    .setExpirationTime(Instant.now())
                    .setUserBiddingSignals(
                            AdSelectionSignals.fromString("{\"user_bidding_signals\":1}"))
                    .build();

    private static final Uri REPORTING_URI = Uri.parse("https://domain.com/reporting");
    private static final String SELLER_KEY = "{\"seller\":\"";

    private static final Uri CLICK_URI = Uri.parse("https://domain.com/click");
    private static final Uri HOVER_URI = Uri.parse("https://domain.com/hover");

    private static final EventUriRegistrationInfo CLICK_EVENT_URI_REGISTRATION_INFO =
            EventUriRegistrationInfo.builder().setEventType("click").setEventUri(CLICK_URI).build();
    private static final EventUriRegistrationInfo HOVER_EVENT_URI_REGISTRATION_INFO =
            EventUriRegistrationInfo.builder().setEventType("hover").setEventUri(HOVER_URI).build();

    @Test
    public void testCanCallScript() throws Exception {

        AdData advert = new AdData(Uri.parse(mDummyDomain), "{}");
        ImmutableList.Builder<JSScriptArgument> args = new ImmutableList.Builder<>();
        args.add(AdDataArgument.asScriptArgument("ignored", advert));
        final ReportingScriptResult result =
                callReportingEngine(
                        "function helloAdvert(ad) { return {'status': 0, 'results': {'result':"
                                + " 'hello ' + ad.render_uri }}; }",
                        "helloAdvert",
                        args.build());
        assertThat(result.status).isEqualTo(0);
        assertThat((result.results.getString(mResultField))).isEqualTo("hello " + mDummyDomain);
    }

    @Test
    public void testThrowsJSExecutionExceptionIfFunctionNotFound() throws Exception {

        AdData advert = new AdData(Uri.parse(mDummyDomain), "{}");
        ImmutableList.Builder<JSScriptArgument> args = new ImmutableList.Builder<>();
        args.add(AdDataArgument.asScriptArgument("ignored", advert));

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            callReportingEngine(
                                    "function helloAdvert(ad) { return {'status': 0, 'results':"
                                            + " {'result': 'hello ' + ad.render_uri }}; }",
                                    "helloAdvertWrongName",
                                    args.build());
                        });
        assertThat(exception.getCause()).isInstanceOf(JSExecutionException.class);
    }

    @Test
    public void testThrowsIllegalStateExceptionIfScriptIsNotReturningJson() throws Exception {
        AdData advert = new AdData(Uri.parse("http://www.domain.com/adverts/123"), "{}");
        ImmutableList.Builder<JSScriptArgument> args = new ImmutableList.Builder<>();
        args.add(AdDataArgument.asScriptArgument("ignored", advert));

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            callReportingEngine(
                                    "function helloAdvert(ad) { return 'hello ' + ad.render_uri; }",
                                    "helloAdvert",
                                    args.build());
                        });
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testReportResultSuccessfulCase() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"seller\":\"' + ad_selection_config.seller + '\"}', "
                        + "'reporting_uri': 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        Uri renderUri = Uri.parse(mDummyDomain);
        double bid = 5;

        final SellerReportingResult result =
                reportResult(jsScript, adSelectionConfig, renderUri, bid, mContextualSignals);

        assertThat(
                        AdSelectionSignals.fromString(
                                SELLER_KEY + adSelectionConfig.getSeller() + "\"}"))
                .isEqualTo(result.getSignalsForBuyer());

        assertEquals(REPORTING_URI, result.getReportingUri());
    }

    @Test
    public void testReportResultSuccessfulCaseWithMoreResultsFieldsThanExpected() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                    + " \n"
                    + " return {'status': 0, 'results': {'signals_for_buyer': '{\"seller\":\"' +"
                    + " ad_selection_config.seller + '\"}', 'reporting_uri':"
                    + " 'https://domain.com/reporting', 'extra_key':'extra_value' } };\n"
                    + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        Uri renderUri = Uri.parse(mDummyDomain);
        double bid = 5;

        final SellerReportingResult result =
                reportResult(jsScript, adSelectionConfig, renderUri, bid, mContextualSignals);

        assertThat(
                        AdSelectionSignals.fromString(
                                SELLER_KEY + adSelectionConfig.getSeller() + "\"}"))
                .isEqualTo(result.getSignalsForBuyer());

        assertEquals(REPORTING_URI, result.getReportingUri());
    }

    @Test
    public void testReportResultSuccessfulCaseWithCallingRegisterAdBeacon() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "    registerAdBeacon('click', 'https://domain.com/click');\n"
                        + "    registerAdBeacon('hover', 'https://domain.com/hover');\n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"seller\":\"' + ad_selection_config.seller + '\"}', "
                        + "'reporting_uri': 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        Uri renderUri = Uri.parse(mDummyDomain);
        double bid = 5;

        final SellerReportingResult result =
                reportResult(jsScript, adSelectionConfig, renderUri, bid, mContextualSignals);

        assertEquals(REPORTING_URI, result.getReportingUri());

        assertThat(
                        AdSelectionSignals.fromString(
                                SELLER_KEY + adSelectionConfig.getSeller() + "\"}"))
                .isEqualTo(result.getSignalsForBuyer());

        assertEquals(2, result.getEventUris().size());

        assertThat(
                        ImmutableList.of(
                                CLICK_EVENT_URI_REGISTRATION_INFO,
                                HOVER_EVENT_URI_REGISTRATION_INFO))
                .containsExactlyElementsIn(result.getEventUris());
    }

    @Test
    public void testReportResultSuccessfulCaseWithCallingRegisterAdBeaconWithSamePair()
            throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "    registerAdBeacon('click', 'https://domain.com/click');\n"
                        + "    registerAdBeacon('click', 'https://domain.com/click');\n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"seller\":\"' + ad_selection_config.seller + '\"}', "
                        + "'reporting_uri': 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        Uri renderUri = Uri.parse(mDummyDomain);
        double bid = 5;

        final SellerReportingResult result =
                reportResult(jsScript, adSelectionConfig, renderUri, bid, mContextualSignals);

        assertEquals(REPORTING_URI, result.getReportingUri());

        assertThat(
                        AdSelectionSignals.fromString(
                                SELLER_KEY + adSelectionConfig.getSeller() + "\"}"))
                .isEqualTo(result.getSignalsForBuyer());

        assertEquals(2, result.getEventUris().size());

        assertThat(
                        ImmutableList.of(
                                CLICK_EVENT_URI_REGISTRATION_INFO,
                                CLICK_EVENT_URI_REGISTRATION_INFO))
                .containsExactlyElementsIn(result.getEventUris());
    }

    @Test
    public void testReportResultSuccessfulCaseSkipsInvalidEventType() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals)"
                    + " {\n"
                    + "    registerAdBeacon('click', 'https://domain.com/click');\n"
                    + "    registerAdBeacon('hover', 'https://domain.com/hover');\n"
                    + "    registerAdBeacon({'eventType':'invalidEventType'},"
                    + " 'https://domain.com/view');\n"
                    + " return {'status': 0, 'results': {'signals_for_buyer': '{\"seller\":\"' +"
                    + " ad_selection_config.seller + '\"}', 'reporting_uri':"
                    + " 'https://domain.com/reporting' } };\n"
                    + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        Uri renderUri = Uri.parse(mDummyDomain);
        double bid = 5;

        final SellerReportingResult result =
                reportResult(jsScript, adSelectionConfig, renderUri, bid, mContextualSignals);

        assertEquals(REPORTING_URI, result.getReportingUri());

        assertThat(
                        AdSelectionSignals.fromString(
                                SELLER_KEY + adSelectionConfig.getSeller() + "\"}"))
                .isEqualTo(result.getSignalsForBuyer());

        assertEquals(2, result.getEventUris().size());

        assertThat(
                        ImmutableList.of(
                                CLICK_EVENT_URI_REGISTRATION_INFO,
                                HOVER_EVENT_URI_REGISTRATION_INFO))
                .containsExactlyElementsIn(result.getEventUris());
    }

    @Test
    public void testReportResultSuccessfulCaseSkipsInvalidEventUri() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "    registerAdBeacon('click', 'https://domain.com/click');\n"
                        + "    registerAdBeacon('hover', 'https://domain.com/hover');\n"
                        + "    registerAdBeacon('view', {'uri':'https://domain.com/view'});\n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"seller\":\"' + ad_selection_config.seller + '\"}', "
                        + "'reporting_uri': 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        Uri renderUri = Uri.parse(mDummyDomain);
        double bid = 5;

        final SellerReportingResult result =
                reportResult(jsScript, adSelectionConfig, renderUri, bid, mContextualSignals);

        assertEquals(REPORTING_URI, result.getReportingUri());

        assertThat(
                        AdSelectionSignals.fromString(
                                SELLER_KEY + adSelectionConfig.getSeller() + "\"}"))
                .isEqualTo(result.getSignalsForBuyer());

        assertEquals(2, result.getEventUris().size());

        assertThat(
                        ImmutableList.of(
                                CLICK_EVENT_URI_REGISTRATION_INFO,
                                HOVER_EVENT_URI_REGISTRATION_INFO))
                .containsExactlyElementsIn(result.getEventUris());
    }

    @Test
    public void testReportResultSuccessfulCaseWithNoBeaconRegistered() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"seller\":\"' + ad_selection_config.seller + '\"}', "
                        + "'reporting_uri': 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        Uri renderUri = Uri.parse(mDummyDomain);
        double bid = 5;

        final SellerReportingResult result =
                reportResult(jsScript, adSelectionConfig, renderUri, bid, mContextualSignals);

        assertEquals(REPORTING_URI, result.getReportingUri());

        assertThat(
                        AdSelectionSignals.fromString(
                                SELLER_KEY + adSelectionConfig.getSeller() + "\"}"))
                .isEqualTo(result.getSignalsForBuyer());

        assertEquals(0, result.getEventUris().size());
    }

    @Test
    public void testReportResultFailedStatusCase() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': -1, 'results': {'signals_for_buyer':"
                        + " '{\"seller\":\"' + ad_selection_config.seller + '\"}', "
                        + "'reporting_uri': 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        Uri renderUri = Uri.parse(mDummyDomain);
        double bid = 5;

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportResult(
                                    jsScript,
                                    adSelectionConfig,
                                    renderUri,
                                    bid,
                                    mContextualSignals);
                        });
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testReportResultFailedCaseNoReportingUri() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"seller\":\"' + ad_selection_config.seller + '\"}' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        Uri renderUri = Uri.parse(mDummyDomain);
        double bid = 5;

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportResult(
                                    jsScript,
                                    adSelectionConfig,
                                    renderUri,
                                    bid,
                                    mContextualSignals);
                        });
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testReportResultIncorrectReportingUriNameCase() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"seller\":\"' + ad_selection_config.seller + '\"}', "
                        + "'incorrect_name_reporting_uri': 'https://domain.com/reporting' }"
                        + " };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        Uri renderUri = Uri.parse(mDummyDomain);
        double bid = 5;

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportResult(
                                    jsScript,
                                    adSelectionConfig,
                                    renderUri,
                                    bid,
                                    mContextualSignals);
                        });
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testReportResultIncorrectNameForResultsCase() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'incorrect_results': {'signals_for_buyer':"
                        + " '{\"seller\":\"' + ad_selection_config.seller + '\"}', "
                        + "'reporting_uri': 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        Uri renderUri = Uri.parse(mDummyDomain);
        double bid = 5;

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportResult(
                                    jsScript,
                                    adSelectionConfig,
                                    renderUri,
                                    bid,
                                    mContextualSignals);
                        });
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testReportWinSuccessfulCase() throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri':"
                        + " 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        final BuyerReportingResult result =
                reportWin(
                        jsScript,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                        mSignalsForBuyer,
                        adSelectionConfig.getSellerSignals(),
                        mCustomAudienceSignals);
        assertEquals(REPORTING_URI, result.getReportingUri());
    }

    @Test
    public void testReportWinSuccessfulCaseMoreResultsFieldsThanExpected() throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer, "
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "    return {'status': 0, 'results': {'reporting_uri': 'https://domain"
                        + ".com/reporting', 'extra_key': 'extra_value'}}\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        final BuyerReportingResult result =
                reportWin(
                        jsScript,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                        mSignalsForBuyer,
                        adSelectionConfig.getSellerSignals(),
                        mCustomAudienceSignals);
        assertEquals(REPORTING_URI, result.getReportingUri());
    }

    @Test
    public void testReportWinSuccessfulCaseWithCallingRegisterAdBeacon() throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "    registerAdBeacon('click', 'https://domain.com/click');\n"
                        + "    registerAdBeacon('hover', 'https://domain.com/hover');\n"
                        + "    return {'status': 0, 'results': {'reporting_uri': 'https://domain"
                        + ".com/reporting' }};\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        final BuyerReportingResult result =
                reportWin(
                        jsScript,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                        mSignalsForBuyer,
                        adSelectionConfig.getSellerSignals(),
                        mCustomAudienceSignals);
        assertEquals(REPORTING_URI, result.getReportingUri());

        assertEquals(2, result.getEventUris().size());

        assertThat(
                        ImmutableList.of(
                                CLICK_EVENT_URI_REGISTRATION_INFO,
                                HOVER_EVENT_URI_REGISTRATION_INFO))
                .containsExactlyElementsIn(result.getEventUris());
    }

    @Test
    public void testReportWinSuccessfulCaseWithCallingRegisterAdBeaconWithSamePair()
            throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "    registerAdBeacon('click', 'https://domain.com/click');\n"
                        + "    registerAdBeacon('click', 'https://domain.com/click');\n"
                        + "    return {'status': 0, 'results': {'reporting_uri': 'https://domain"
                        + ".com/reporting' }};\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        final BuyerReportingResult result =
                reportWin(
                        jsScript,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                        mSignalsForBuyer,
                        adSelectionConfig.getSellerSignals(),
                        mCustomAudienceSignals);
        assertEquals(REPORTING_URI, result.getReportingUri());

        assertEquals(2, result.getEventUris().size());

        assertThat(
                        ImmutableList.of(
                                CLICK_EVENT_URI_REGISTRATION_INFO,
                                CLICK_EVENT_URI_REGISTRATION_INFO))
                .containsExactlyElementsIn(result.getEventUris());
    }

    @Test
    public void testReportWinSuccessfulCaseSkipsInvalidEventType() throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer"
                        + " ,contextual_signals, custom_audience_signals) {\n"
                        + "    registerAdBeacon('click', 'https://domain.com/click');\n"
                        + "    registerAdBeacon('hover', 'https://domain.com/hover');\n"
                        + "    registerAdBeacon({'eventType':'invalidEventType'},"
                        + " 'https://domain.com/view');\n"
                        + "    return {'status': 0, 'results': {'reporting_uri':"
                        + " 'https://domain.com/reporting' }};\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        final BuyerReportingResult result =
                reportWin(
                        jsScript,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                        mSignalsForBuyer,
                        adSelectionConfig.getSellerSignals(),
                        mCustomAudienceSignals);
        assertEquals(REPORTING_URI, result.getReportingUri());

        assertEquals(2, result.getEventUris().size());

        assertThat(
                        ImmutableList.of(
                                CLICK_EVENT_URI_REGISTRATION_INFO,
                                HOVER_EVENT_URI_REGISTRATION_INFO))
                .containsExactlyElementsIn(result.getEventUris());
    }

    @Test
    public void testReportWinSuccessfulCaseSkipsInvalidEventUri() throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "    registerAdBeacon('click', 'https://domain.com/click');\n"
                        + "    registerAdBeacon('hover', 'https://domain.com/hover');\n"
                        + "    registerAdBeacon('view', {'uri':'https://domain.com/view'});\n"
                        + "    return {'status': 0, 'results': {'reporting_uri': 'https://domain"
                        + ".com/reporting' }};\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        final BuyerReportingResult result =
                reportWin(
                        jsScript,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                        mSignalsForBuyer,
                        adSelectionConfig.getSellerSignals(),
                        mCustomAudienceSignals);
        assertEquals(REPORTING_URI, result.getReportingUri());

        assertEquals(2, result.getEventUris().size());

        assertThat(
                        ImmutableList.of(
                                CLICK_EVENT_URI_REGISTRATION_INFO,
                                HOVER_EVENT_URI_REGISTRATION_INFO))
                .containsExactlyElementsIn(result.getEventUris());
    }

    @Test
    public void testReportWinSuccessfulCaseWithNoBeaconRegistered() throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "    return {'status': 0, 'results': {'reporting_uri': 'https://domain"
                        + ".com/reporting' }};\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        final BuyerReportingResult result =
                reportWin(
                        jsScript,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                        mSignalsForBuyer,
                        adSelectionConfig.getSellerSignals(),
                        mCustomAudienceSignals);
        assertEquals(REPORTING_URI, result.getReportingUri());

        assertEquals(0, result.getEventUris().size());
    }

    @Test
    public void testReportWinFailedStatusCase() throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': -1, 'results': {'reporting_uri':"
                        + " 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        assertThrows(
                ExecutionException.class,
                () -> {
                    reportWin(
                            jsScript,
                            adSelectionConfig.getAdSelectionSignals(),
                            adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                            mSignalsForBuyer,
                            adSelectionConfig.getSellerSignals(),
                            mCustomAudienceSignals);
                });
    }

    @Test
    public void testReportWinIncorrectReportingUriNameCase() throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'incorrect_reporting_uri':"
                        + " 'https://domain.com/incorrectReporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        assertThrows(
                ExecutionException.class,
                () -> {
                    reportWin(
                            jsScript,
                            adSelectionConfig.getAdSelectionSignals(),
                            adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                            mSignalsForBuyer,
                            adSelectionConfig.getSellerSignals(),
                            mCustomAudienceSignals);
                });
    }

    @Test
    public void testReportWinIncorrectNameForResultsCase() throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'incorrect_results': {'reporting_uri':"
                        + " 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        assertThrows(
                ExecutionException.class,
                () -> {
                    reportWin(
                            jsScript,
                            adSelectionConfig.getAdSelectionSignals(),
                            adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                            mSignalsForBuyer,
                            adSelectionConfig.getSellerSignals(),
                            mCustomAudienceSignals);
                });
    }

    private ReportingScriptResult callReportingEngine(
            String jsScript, String functionCall, List<JSScriptArgument> args) throws Exception {
        return waitForFuture(
                () -> {
                    Log.i(TAG, "Calling Reporting Script Engine");
                    return mReportImpressionScriptEngine.runReportingScript(
                            jsScript, functionCall, args);
                });
    }

    private SellerReportingResult reportResult(
            String jsScript,
            AdSelectionConfig adSelectionConfig,
            Uri renderUri,
            double bid,
            AdSelectionSignals contextualSignals)
            throws Exception {

        return waitForFuture(
                () -> {
                    Log.i(TAG, "Calling reportResult");
                    return mReportImpressionScriptEngine.reportResult(
                            jsScript, adSelectionConfig, renderUri, bid, contextualSignals);
                });
    }

    private BuyerReportingResult reportWin(
            String jsScript,
            AdSelectionSignals adSelectionSignals,
            AdSelectionSignals perBuyerSignals,
            AdSelectionSignals signalsForBuyer,
            AdSelectionSignals contextualSignals,
            CustomAudienceSignals customAudienceSignals)
            throws Exception {
        return waitForFuture(
                () -> {
                    Log.i(TAG, "Calling reportWin");
                    return mReportImpressionScriptEngine.reportWin(
                            jsScript,
                            adSelectionSignals,
                            perBuyerSignals,
                            signalsForBuyer,
                            contextualSignals,
                            customAudienceSignals);
                });
    }

    private <T> T waitForFuture(ThrowingSupplier<ListenableFuture<T>> function) throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        AtomicReference<ListenableFuture<T>> futureResult = new AtomicReference<>();
        futureResult.set(function.get());
        futureResult.get().addListener(resultLatch::countDown, mExecutorService);
        resultLatch.await();
        return futureResult.get().get();
    }

    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
