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

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.net.Uri;
import android.os.Trace;

import com.android.adservices.LogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.CustomAudienceDevOverridesHelper;
import com.android.adservices.service.profiling.Tracing;
import com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.List;
import java.util.Objects;

/** Class to fetch JavaScript code both on and off device. */
public class JsFetcher {
    @VisibleForTesting
    static final String MISSING_BIDDING_LOGIC = "Error fetching bidding js logic";

    private final ListeningExecutorService mBackgroundExecutorService;
    private final ListeningExecutorService mLightweightExecutorService;
    private final CustomAudienceDevOverridesHelper mCustomAudienceDevOverridesHelper;
    private final AdServicesHttpsClient mAdServicesHttpsClient;
    private final Flags mFlags;

    public JsFetcher(
            @NonNull ListeningExecutorService backgroundExecutorService,
            @NonNull ListeningExecutorService lightweightExecutorService,
            @NonNull CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull Flags flags) {
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(customAudienceDevOverridesHelper);
        Objects.requireNonNull(adServicesHttpsClient);
        Objects.requireNonNull(flags);

        mBackgroundExecutorService = backgroundExecutorService;
        mCustomAudienceDevOverridesHelper = customAudienceDevOverridesHelper;
        mAdServicesHttpsClient = adServicesHttpsClient;
        mLightweightExecutorService = lightweightExecutorService;
        mFlags = flags;
    }

    /**
     * Fetch the buyer decision logic. Check locally to see if an override is present, otherwise
     * fetch from server. Does not use caching by default
     *
     * @return buyer decision logic
     */
    // TODO(b/260043950): Remove this method when telemetry logging is added on
    //  TrustedServerAdSelectionRunner.
    public FluentFuture<String> getBuyerDecisionLogic(
            @NonNull final Uri decisionLogicUri,
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name) {
        return getBuyerDecisionLogic(decisionLogicUri, owner, buyer, name, false);
    }

    /**
     * Fetch the buyer decision logic. Check locally to see if an override is present, otherwise
     * fetch from server. Makes use of caching optional.
     *
     * @return buyer decision logic
     */
    public FluentFuture<String> getBuyerDecisionLogic(
            @NonNull final Uri decisionLogicUri,
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            boolean useCaching) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.GET_BUYER_DECISION_LOGIC);

        FluentFuture<String> jsOverrideFuture =
                FluentFuture.from(
                        mBackgroundExecutorService.submit(
                                () ->
                                        mCustomAudienceDevOverridesHelper.getBiddingLogicOverride(
                                                owner, buyer, name)));
        return jsOverrideFuture
                .transformAsync(
                        jsOverride -> {
                            Trace.endAsyncSection(Tracing.GET_BUYER_DECISION_LOGIC, traceCookie);
                            if (jsOverride == null) {
                                LogUtil.v(
                                        "Fetching buyer decision logic from server: %s",
                                        decisionLogicUri.toString());
                                return FluentFuture.from(
                                                mAdServicesHttpsClient.fetchPayload(
                                                        AdServicesHttpClientRequest.builder()
                                                                .setUri(decisionLogicUri)
                                                                .setUseCache(useCaching)
                                                                .build()))
                                        .transform(
                                                response -> response.getResponseBody(),
                                                mLightweightExecutorService);
                            } else {
                                LogUtil.d(
                                        "Developer options enabled and an override JS is provided "
                                                + "for the current Custom Audience. "
                                                + "Skipping call to server.");
                                return Futures.immediateFuture(jsOverride);
                            }
                        },
                        mLightweightExecutorService)
                .catching(
                        Exception.class,
                        e -> {
                            Trace.endAsyncSection(Tracing.GET_BUYER_DECISION_LOGIC, traceCookie);
                            LogUtil.w(
                                    e, "Exception encountered when fetching buyer decision logic");
                            throw new IllegalStateException(MISSING_BIDDING_LOGIC);
                        },
                        mLightweightExecutorService);
    }

    /**
     * Fetch the buyer decision logic with telemetry logger. Check locally to see if an override is
     * present, otherwise fetch from server. Make use of caching optional.
     *
     * @return buyer decision logic
     */
    public FluentFuture<AdServicesHttpClientResponse> getBuyerDecisionLogicWithLogger(
            @NonNull final AdServicesHttpClientRequest decisionLogicUri,
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
                                        mCustomAudienceDevOverridesHelper.getBiddingLogicOverride(
                                                owner, buyer, name)));
        return jsOverrideFuture
                .transformAsync(
                        jsOverride -> {
                            Trace.endAsyncSection(Tracing.GET_BUYER_DECISION_LOGIC, traceCookie);
                            if (jsOverride == null) {
                                LogUtil.v(
                                        "Fetching buyer decision logic from server: %s",
                                        decisionLogicUri.toString());
                                return FluentFuture.from(
                                        mAdServicesHttpsClient.fetchPayload(decisionLogicUri));
                            } else {
                                LogUtil.d(
                                        "Developer options enabled and an override JS is provided "
                                                + "for the current Custom Audience. "
                                                + "Skipping call to server.");
                                final ImmutableMap<String, List<String>> versionHeader =
                                        JsVersionHelper.constructVersionHeader(
                                                JsVersionHelper
                                                        .JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS,
                                                mFlags.getFledgeAdSelectionBiddingLogicJsVersion());
                                return Futures.immediateFuture(
                                        AdServicesHttpClientResponse.builder()
                                                .setResponseBody(jsOverride)
                                                .setResponseHeaders(versionHeader)
                                                .build());
                            }
                        },
                        mLightweightExecutorService)
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
                            LogUtil.w(
                                    e, "Exception encountered when fetching buyer decision logic");
                            throw new IllegalStateException(MISSING_BIDDING_LOGIC);
                        },
                        mLightweightExecutorService);
    }
}
