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

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.net.Uri;
import android.os.Trace;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.AdSelectionDevOverridesHelper;
import com.android.adservices.service.devapi.CustomAudienceDevOverridesHelper;
import com.android.adservices.service.profiling.Tracing;
import com.android.adservices.service.stats.AdSelectionExecutionLogger;
import com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.Objects;

/** Class to fetch JavaScript code both on and off device. */
public class JsFetcher {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting
    static final String MISSING_BIDDING_LOGIC = "Error fetching bidding js logic";

    @VisibleForTesting
    static final String MISSING_SCORING_LOGIC = "Error fetching scoring decision logic";

    @VisibleForTesting
    static final String MISSING_OUTCOME_SELECTION_LOGIC =
            "Error fetching outcome selection decision logic";

    @NonNull private final ListeningExecutorService mBackgroundExecutorService;
    @NonNull private final ListeningExecutorService mLightweightExecutorService;
    @NonNull private final AdServicesHttpsClient mAdServicesHttpsClient;
    @NonNull private final PrebuiltLogicGenerator mPrebuiltLogicGenerator;

    public JsFetcher(
            @NonNull ListeningExecutorService backgroundExecutorService,
            @NonNull ListeningExecutorService lightweightExecutorService,
            @NonNull AdServicesHttpsClient adServicesHttpsClient) {
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(adServicesHttpsClient);

        mBackgroundExecutorService = backgroundExecutorService;
        mAdServicesHttpsClient = adServicesHttpsClient;
        mLightweightExecutorService = lightweightExecutorService;
        mPrebuiltLogicGenerator = new PrebuiltLogicGenerator();
    }

    /**
     * Fetch the buyer's bidding logic. Check locally to see if an override is present, otherwise
     * fetch from server. This method doesn't use caching. Use {@link JsFetcher#getBiddingLogic(Uri,
     * CustomAudienceDevOverridesHelper, String, AdTechIdentifier, String, boolean)} to enable
     * caching
     *
     * @return buyer decision logic
     */
    // TODO(b/260043950): Remove this method when telemetry logging is added on
    //  TrustedServerAdSelectionRunner.
    public FluentFuture<String> getBiddingLogic(
            @NonNull final Uri decisionLogicUri,
            @NonNull final CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper,
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name) {
        return getBiddingLogic(
                decisionLogicUri, customAudienceDevOverridesHelper, owner, buyer, name, false);
    }

    /**
     * Fetch the buyer's bidding logic. Check locally to see if an override is present, otherwise
     * fetch from server. Makes use of caching optional.
     *
     * @return buyer decision logic
     */
    public FluentFuture<String> getBiddingLogic(
            @NonNull final Uri biddingLogicUri,
            @NonNull final CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper,
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            boolean useCaching) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.GET_BUYER_DECISION_LOGIC);

        FluentFuture<String> jsOverrideFuture =
                FluentFuture.from(
                        mBackgroundExecutorService.submit(
                                () ->
                                        customAudienceDevOverridesHelper.getBiddingLogicOverride(
                                                owner, buyer, name)));

        AdServicesHttpClientRequest biddingLogicRequest =
                AdServicesHttpClientRequest.builder()
                        .setUri(biddingLogicUri)
                        .setUseCache(useCaching)
                        .build();

        return resolveJsScriptSource(jsOverrideFuture, biddingLogicRequest)
                .transform(
                        AdServicesHttpClientResponse::getResponseBody, mLightweightExecutorService)
                .catching(
                        Exception.class,
                        e -> {
                            Trace.endAsyncSection(Tracing.GET_BUYER_DECISION_LOGIC, traceCookie);
                            sLogger.w(
                                    e, "Exception encountered when fetching buyer decision logic");
                            throw new IllegalStateException(MISSING_BIDDING_LOGIC);
                        },
                        mLightweightExecutorService);
    }

    /**
     * Fetch the buyer's bidding logic with telemetry logger. Check locally to see if an override is
     * present, otherwise fetch from server. Make use of caching optional.
     *
     * @return buyer decision logic
     */
    public FluentFuture<AdServicesHttpClientResponse> getBuyerDecisionLogicWithLogger(
            @NonNull final AdServicesHttpClientRequest biddingLogicRequest,
            @NonNull final CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper,
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.GET_BUYER_DECISION_LOGIC);
        runAdBiddingPerCAExecutionLogger.startGetBuyerDecisionLogic();
        FluentFuture<String> jsOverrideFuture =
                FluentFuture.from(
                        mBackgroundExecutorService.submit(
                                () ->
                                        customAudienceDevOverridesHelper.getBiddingLogicOverride(
                                                owner, buyer, name)));

        return resolveJsScriptSource(jsOverrideFuture, biddingLogicRequest)
                .transform(
                        buyerDecisionLogicJs -> {
                            runAdBiddingPerCAExecutionLogger.endGetBuyerDecisionLogic(
                                    buyerDecisionLogicJs.getResponseBody());
                            return buyerDecisionLogicJs;
                        },
                        mLightweightExecutorService)
                .catching(
                        Exception.class,
                        e -> {
                            Trace.endAsyncSection(Tracing.GET_BUYER_DECISION_LOGIC, traceCookie);
                            sLogger.w(
                                    e, "Exception encountered when fetching buyer decision logic");
                            throw new IllegalStateException(MISSING_BIDDING_LOGIC);
                        },
                        mLightweightExecutorService);
    }

    /**
     * Fetch the seller's scoring logic with telemetry logger. Check locally to see if an override
     * is present, otherwise fetch from server. Make use of caching optional.
     *
     * @return buyer decision logic
     */
    public FluentFuture<String> getScoringLogic(
            @NonNull final AdServicesHttpClientRequest scoringLogicRequest,
            @NonNull final AdSelectionDevOverridesHelper adSelectionDevOverridesHelper,
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull AdSelectionExecutionLogger adSelectionExecutionLogger) {
        Objects.requireNonNull(scoringLogicRequest);
        Objects.requireNonNull(adSelectionDevOverridesHelper);
        Objects.requireNonNull(adSelectionConfig);
        Objects.requireNonNull(adSelectionExecutionLogger);

        adSelectionExecutionLogger.startGetAdSelectionLogic();
        int traceCookie = Tracing.beginAsyncSection(Tracing.GET_AD_SELECTION_LOGIC);
        FluentFuture<String> jsOverrideFuture =
                FluentFuture.from(
                        mBackgroundExecutorService.submit(
                                () ->
                                        adSelectionDevOverridesHelper.getDecisionLogicOverride(
                                                adSelectionConfig)));

        return resolveJsScriptSource(jsOverrideFuture, scoringLogicRequest)
                .transform(
                        AdServicesHttpClientResponse::getResponseBody, mLightweightExecutorService)
                .transform(
                        input -> {
                            Tracing.endAsyncSection(Tracing.GET_AD_SELECTION_LOGIC, traceCookie);
                            return input;
                        },
                        mLightweightExecutorService)
                .transform(
                        scoringLogic -> {
                            adSelectionExecutionLogger.endGetAdSelectionLogic(scoringLogic);
                            return scoringLogic;
                        },
                        mLightweightExecutorService)
                .catching(
                        Exception.class,
                        e -> {
                            Tracing.endAsyncSection(Tracing.GET_AD_SELECTION_LOGIC, traceCookie);
                            sLogger.e(e, "Exception encountered when fetching scoring logic");
                            throw new IllegalStateException(MISSING_SCORING_LOGIC);
                        },
                        mLightweightExecutorService);
    }

    /**
     * Fetch the buyer decision logic with telemetry logger. Check locally to see if an override is
     * present, otherwise fetch from server. Make use of caching optional.
     *
     * @return buyer decision logic
     */
    public FluentFuture<String> getOutcomeSelectionLogic(
            @NonNull final AdServicesHttpClientRequest outcomeLogicRequest,
            @NonNull final AdSelectionDevOverridesHelper adSelectionDevOverridesHelper,
            @NonNull AdSelectionFromOutcomesConfig adSelectionFromOutcomesConfig) {
        FluentFuture<String> jsOverrideFuture =
                FluentFuture.from(
                        mBackgroundExecutorService.submit(
                                () ->
                                        adSelectionDevOverridesHelper.getSelectionLogicOverride(
                                                adSelectionFromOutcomesConfig)));

        return resolveJsScriptSource(jsOverrideFuture, outcomeLogicRequest)
                .transform(
                        AdServicesHttpClientResponse::getResponseBody, mLightweightExecutorService)
                .catching(
                        Exception.class,
                        e -> {
                            sLogger.e(
                                    e,
                                    "Exception encountered when fetching outcome selection logic");
                            throw new IllegalStateException(MISSING_OUTCOME_SELECTION_LOGIC);
                        },
                        mLightweightExecutorService);
    }

    /**
     * This method controls the order of the decision logic sources. Currently, the order is:
     *
     * <ol>
     *   <li>JS developer overrides
     *   <li>FLEDGE Prebuilt JS generation
     *   <li>HTTPS fetching
     * </ol>
     */
    private FluentFuture<AdServicesHttpClientResponse> resolveJsScriptSource(
            FluentFuture<String> jsOverrideFuture, AdServicesHttpClientRequest jsFetchingRequest) {
        return jsOverrideFuture.transformAsync(
                jsOverride -> {
                    if (Objects.isNull(jsOverride)) {
                        if (mPrebuiltLogicGenerator.isPrebuiltUri(jsFetchingRequest.getUri())) {
                            sLogger.i(
                                    "Prebuilt URI is detected. Generating JS function from"
                                            + " prebuilt implementations");
                            return Futures.immediateFuture(
                                    AdServicesHttpClientResponse.builder()
                                            .setResponseBody(
                                                    mPrebuiltLogicGenerator.jsScriptFromPrebuiltUri(
                                                            jsFetchingRequest.getUri()))
                                            .build());
                        } else {
                            sLogger.v(
                                    "Fetching decision logic from the server with cache enabled"
                                            + " is: "
                                            + jsFetchingRequest.getUseCache());
                            return FluentFuture.from(
                                    mAdServicesHttpsClient.fetchPayload(jsFetchingRequest));
                        }
                    } else {
                        sLogger.d(
                                "Developer options enabled and an override JS is provided."
                                        + "Skipping the call to server.");
                        return Futures.immediateFuture(
                                AdServicesHttpClientResponse.builder()
                                        .setResponseBody(jsOverride)
                                        .build());
                    }
                },
                mLightweightExecutorService);
    }
}
