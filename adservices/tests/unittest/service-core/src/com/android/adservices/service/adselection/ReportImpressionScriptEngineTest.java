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
import com.android.adservices.service.adselection.ReportImpressionScriptEngine.ReportingScriptResult;
import com.android.adservices.service.adselection.ReportImpressionScriptEngine.SellerReportingResult;
import com.android.adservices.service.exception.JSExecutionException;
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
    private final ReportImpressionScriptEngine mReportImpressionScriptEngine =
            new ReportImpressionScriptEngine(sContext);

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
                    .setBuyer("test_buyer")
                    .setName("test_name")
                    .setActivationTime(Instant.now())
                    .setExpirationTime(Instant.now())
                    .setUserBiddingSignals("{\"user_bidding_signals\":1}")
                    .build();

    @Test
    public void testCanCallScript() throws Exception {

        AdData advert = new AdData(Uri.parse(mDummyDomain), "{}");
        ImmutableList.Builder<JSScriptArgument> args = new ImmutableList.Builder<>();
        args.add(AdDataArgument.asScriptArgument("ignored", advert));
        final ReportingScriptResult result =
                callReportingEngine(
                        "function helloAdvert(ad) { return {'status': 0, 'results': {'result':"
                                + " 'hello ' + ad.render_url }}; }",
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
                                            + " {'result': 'hello ' + ad.render_url }}; }",
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
                                    "function helloAdvert(ad) { return 'hello ' + ad.render_url; }",
                                    "helloAdvert",
                                    args.build());
                        });
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testReportResultSuccessfulCase() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_url, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"seller\":\"' + ad_selection_config.seller + '\"}', "
                        + "'reporting_url': render_url } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        Uri renderUrl = Uri.parse(mDummyDomain);
        double bid = 5;

        final SellerReportingResult result =
                reportResult(jsScript, adSelectionConfig, renderUrl, bid, mContextualSignals);

        assertThat(result.getSignalsForBuyer())
                .isEqualTo(
                        AdSelectionSignals.fromString(
                                "{\"seller\":\"" + adSelectionConfig.getSeller() + "\"}"));
        assertThat(result.getReportingUrl()).isEqualTo(renderUrl);
    }

    @Test
    public void testReportResultFailedStatusCase() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_url, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': -1, 'results': {'signals_for_buyer':"
                        + " '{\"seller\":\"' + ad_selection_config.seller + '\"}', "
                        + "'reporting_url': render_url } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        Uri renderUrl = Uri.parse(mDummyDomain);
        double bid = 5;

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportResult(
                                    jsScript,
                                    adSelectionConfig,
                                    renderUrl,
                                    bid,
                                    mContextualSignals);
                        });
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testReportResultFailedNumResultsCase() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_url, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"seller\":\"' + ad_selection_config.seller + '\"}' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        Uri renderUrl = Uri.parse(mDummyDomain);
        double bid = 5;

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportResult(
                                    jsScript,
                                    adSelectionConfig,
                                    renderUrl,
                                    bid,
                                    mContextualSignals);
                        });
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testReportResultFailedResultNamesCase() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_url, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"seller\":\"' + ad_selection_config.seller + '\"}', "
                        + "'incorrect_name_reporting_url': render_url }"
                        + " };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        Uri renderUrl = Uri.parse(mDummyDomain);
        double bid = 5;

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportResult(
                                    jsScript,
                                    adSelectionConfig,
                                    renderUrl,
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
                    + " return {'status': 0, 'results': {'reporting_url': signals_for_buyer } };\n"
                    + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        final Uri result =
                reportWin(
                        jsScript,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                        mSignalsForBuyer,
                        adSelectionConfig.getSellerSignals(),
                        mCustomAudienceSignals);
        // TODO: Quit comparing a URI to a JSON object (b/239497492)
        assertThat(result.toString()).isEqualTo(mSignalsForBuyer.toString());
    }

    @Test
    public void testReportWinFailedStatusCase() throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                    + " contextual_signals, custom_audience_signals) { \n"
                    + " return {'status': -1, 'results': {'reporting_url': signals_for_buyer } };\n"
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
    public void testReportWinIncorrectResultNameCase() throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'incorrect_reporting_url':"
                        + " signals_for_buyer } };\n"
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
            Uri renderUrl,
            double bid,
            AdSelectionSignals contextualSignals)
            throws Exception {

        return waitForFuture(
                () -> {
                    Log.i(TAG, "Calling reportResult");
                    return mReportImpressionScriptEngine.reportResult(
                            jsScript, adSelectionConfig, renderUrl, bid, contextualSignals);
                });
    }

    private Uri reportWin(
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
