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

import android.adservices.adselection.AdWithBid;
import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.Uri;
import android.util.Pair;

import com.android.adservices.LogUtil;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdServicesHttpsClient;
import com.android.adservices.service.devapi.CustomAudienceDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.js.IsolateSettings;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.UncheckedTimeoutException;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * This class implements the ad bid generator. A new instance is assumed to be created for every
 * call
 */
public class AdBidGeneratorImpl implements AdBidGenerator {

    @VisibleForTesting
    static final String MISSING_TRUSTED_BIDDING_SIGNALS = "Error fetching trusted bidding signals";

    @VisibleForTesting
    static final String BIDDING_TIMED_OUT = "Bidding exceeded allowed time limit";

    @VisibleForTesting
    static final String BIDDING_ENCOUNTERED_UNEXPECTED_ERROR =
            "Bidding failed for unexpected error";

    @NonNull private final Context mContext;
    @NonNull private final ListeningExecutorService mLightweightExecutorService;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;
    @NonNull private final ScheduledThreadPoolExecutor mScheduledExecutor;
    @NonNull private final AdSelectionScriptEngine mAdSelectionScriptEngine;
    @NonNull private final CustomAudienceDevOverridesHelper mCustomAudienceDevOverridesHelper;
    @NonNull private final Flags mFlags;
    @NonNull private final JsFetcher mJsFetcher;

    public AdBidGeneratorImpl(
            @NonNull Context context,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull ListeningExecutorService lightweightExecutorService,
            @NonNull ListeningExecutorService backgroundExecutorService,
            @NonNull ScheduledThreadPoolExecutor scheduledExecutor,
            @NonNull DevContext devContext,
            @NonNull CustomAudienceDao customAudienceDao,
            @NonNull Flags flags) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(adServicesHttpsClient);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(scheduledExecutor);
        Objects.requireNonNull(devContext);
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(flags);

        mContext = context;
        mLightweightExecutorService = lightweightExecutorService;
        mBackgroundExecutorService = backgroundExecutorService;
        mScheduledExecutor = scheduledExecutor;
        mCustomAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(devContext, customAudienceDao);
        mFlags = flags;
        mAdSelectionScriptEngine =
                new AdSelectionScriptEngine(
                        mContext,
                        () -> mFlags.getEnforceIsolateMaxHeapSize(),
                        () -> mFlags.getIsolateMaxHeapSizeBytes());
        mJsFetcher =
                new JsFetcher(
                        backgroundExecutorService,
                        lightweightExecutorService,
                        mCustomAudienceDevOverridesHelper,
                        adServicesHttpsClient);
    }

    @VisibleForTesting
    AdBidGeneratorImpl(
            @NonNull Context context,
            @NonNull ListeningExecutorService lightWeightExecutorService,
            @NonNull ListeningExecutorService backgroundExecutorService,
            @NonNull ScheduledThreadPoolExecutor scheduledExecutor,
            @NonNull AdSelectionScriptEngine adSelectionScriptEngine,
            @NonNull CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper,
            @NonNull Flags flags,
            @NonNull IsolateSettings isolateSettings,
            @NonNull JsFetcher jsFetcher) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(lightWeightExecutorService);
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(scheduledExecutor);
        Objects.requireNonNull(adSelectionScriptEngine);
        Objects.requireNonNull(customAudienceDevOverridesHelper);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(isolateSettings);
        Objects.requireNonNull(jsFetcher);

        mContext = context;
        mLightweightExecutorService = lightWeightExecutorService;
        mBackgroundExecutorService = backgroundExecutorService;
        mScheduledExecutor = scheduledExecutor;
        mAdSelectionScriptEngine = adSelectionScriptEngine;
        mCustomAudienceDevOverridesHelper = customAudienceDevOverridesHelper;
        mFlags = flags;
        mJsFetcher = jsFetcher;
    }

    @Override
    @NonNull
    public FluentFuture<AdBiddingOutcome> runAdBiddingPerCA(
            @NonNull DBCustomAudience customAudience,
            @NonNull Map<Uri, JSONObject> trustedBiddingDataPerBaseUri,
            @NonNull AdSelectionSignals adSelectionSignals,
            @NonNull AdSelectionSignals buyerSignals,
            @NonNull AdSelectionSignals contextualSignals) {
        Objects.requireNonNull(customAudience);
        Objects.requireNonNull(trustedBiddingDataPerBaseUri);
        Objects.requireNonNull(adSelectionSignals);
        Objects.requireNonNull(buyerSignals);
        Objects.requireNonNull(contextualSignals);

        LogUtil.v("Running Ad Bidding for CA : %s", customAudience.getName());
        if (customAudience.getAds().isEmpty()) {
            LogUtil.v("No Ads found for CA: %s, skipping", customAudience.getName());
            return FluentFuture.from(Futures.immediateFuture(null));
        }

        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignals.buildFromCustomAudience(customAudience);

        // TODO(b/221862406): implement ads filtering logic.

        FluentFuture<String> buyerDecisionLogic =
                mJsFetcher.getBuyerDecisionLogic(
                        customAudience.getBiddingLogicUri(),
                        customAudience.getOwner(),
                        customAudience.getBuyer(),
                        customAudience.getName(),
                        mFlags.getFledgeJsCachingEnabled());

        FluentFuture<Pair<AdWithBid, String>> adWithBidPair =
                buyerDecisionLogic.transformAsync(
                        decisionLogic -> {
                            return runBidding(
                                    customAudience,
                                    decisionLogic,
                                    buyerSignals,
                                    contextualSignals,
                                    customAudienceSignals,
                                    adSelectionSignals,
                                    trustedBiddingDataPerBaseUri);
                        },
                        mLightweightExecutorService);
        return adWithBidPair
                .transform(
                        candidate -> {
                            if (Objects.isNull(candidate)
                                    || Objects.isNull(candidate.first)
                                    || candidate.first.getBid() <= 0.0) {
                                LogUtil.v(
                                        "Bidding for CA completed but result %s is filtered out",
                                        candidate);
                                return null;
                            }
                            CustomAudienceBiddingInfo customAudienceInfo =
                                    CustomAudienceBiddingInfo.create(
                                            customAudience, candidate.second);
                            LogUtil.v(
                                    "Creating Ad Bidding Outcome for CA: %s",
                                    customAudience.getName());
                            AdBiddingOutcome result =
                                    AdBiddingOutcome.builder()
                                            .setAdWithBid(candidate.first)
                                            .setCustomAudienceBiddingInfo(customAudienceInfo)
                                            .build();
                            LogUtil.d("Bidding for CA %s transformed", customAudience.getName());
                            return result;
                        },
                        mLightweightExecutorService)
                .withTimeout(
                        mFlags.getAdSelectionBiddingTimeoutPerCaMs(),
                        TimeUnit.MILLISECONDS,
                        mScheduledExecutor)
                .catching(
                        JSONException.class, this::handleBiddingError, mLightweightExecutorService)
                .catching(
                        TimeoutException.class,
                        this::handleTimeoutError,
                        mLightweightExecutorService);
    }

    @Nullable
    private AdBiddingOutcome handleTimeoutError(TimeoutException e) {
        LogUtil.e(e, "Bid Generation exceeded time limit");
        // Despite this exception will be flattened, after doing `successfulAsList` on bids, keeping
        // it consistent with Scoring and overall Ad Selection timeouts
        throw new UncheckedTimeoutException(BIDDING_TIMED_OUT);
    }

    @Nullable
    private AdBiddingOutcome handleBiddingError(JSONException e) {
        // TODO(b/231326420): Define and implement the certain non-expected exceptions should be
        // re-throw from the AdBidGenerator.
        LogUtil.e(e, "Failed to generate bids for the ads in this custom audience.");
        return null;
    }

    private FluentFuture<AdSelectionSignals> getTrustedBiddingSignals(
            @NonNull DBTrustedBiddingData trustedBiddingData,
            @NonNull Map<Uri, JSONObject> trustedBiddingDataByBaseUri,
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name) {
        Objects.requireNonNull(trustedBiddingData);
        final Uri trustedBiddingUri = trustedBiddingData.getUri();
        final List<String> trustedBiddingKeys = trustedBiddingData.getKeys();

        FluentFuture<AdSelectionSignals> trustedSignalsOverride =
                FluentFuture.from(
                        mBackgroundExecutorService.submit(
                                () ->
                                        mCustomAudienceDevOverridesHelper
                                                .getTrustedBiddingSignalsOverride(
                                                        owner, buyer, name)));
        return trustedSignalsOverride
                .transformAsync(
                        jsOverride -> {
                            if (jsOverride == null) {
                                LogUtil.v("Fetching trusted bidding Signals from server");
                                return Futures.immediateFuture(
                                        TrustedBiddingDataFetcher.extractKeys(
                                                trustedBiddingDataByBaseUri.get(trustedBiddingUri),
                                                trustedBiddingKeys));
                            } else {
                                LogUtil.d(
                                        "Developer options enabled and override trusted signals"
                                                + " are provided for the current Custom Audience."
                                                + " Skipping call to server.");
                                return Futures.immediateFuture(jsOverride);
                            }
                        },
                        mLightweightExecutorService)
                .catching(
                        Exception.class,
                        e -> {
                            LogUtil.w(e, "Exception encountered when fetching trusted signals");
                            throw new IllegalStateException(MISSING_TRUSTED_BIDDING_SIGNALS);
                        },
                        mLightweightExecutorService);
    }

    /** @return the {@link AdWithBid} with the best bid per CustomAudience. */
    @NonNull
    @VisibleForTesting
    FluentFuture<Pair<AdWithBid, String>> runBidding(
            @NonNull DBCustomAudience customAudience,
            @NonNull String buyerDecisionLogicJs,
            @NonNull AdSelectionSignals buyerSignals,
            @NonNull AdSelectionSignals contextualSignals,
            @NonNull CustomAudienceSignals customAudienceSignals,
            @NonNull AdSelectionSignals adSelectionSignals,
            @NonNull Map<Uri, JSONObject> trustedBiddingDataByBaseUri) {
        FluentFuture<AdSelectionSignals> trustedBiddingSignals =
                getTrustedBiddingSignals(
                        customAudience.getTrustedBiddingData(),
                        trustedBiddingDataByBaseUri,
                        customAudience.getOwner(),
                        customAudience.getBuyer(),
                        customAudience.getName());
        // TODO(b/231265311): update AdSelectionScriptEngine AdData class objects with DBAdData
        //  classes and remove this conversion.
        List<AdData> ads =
                customAudience.getAds().stream()
                        .map(
                                adData -> {
                                    return new AdData(adData.getRenderUri(), adData.getMetadata());
                                })
                        .collect(Collectors.toList());
        return trustedBiddingSignals
                .transformAsync(
                        biddingSignals -> {
                            return mAdSelectionScriptEngine.generateBids(
                                    buyerDecisionLogicJs,
                                    ads,
                                    adSelectionSignals,
                                    buyerSignals,
                                    biddingSignals,
                                    contextualSignals,
                                    customAudienceSignals);
                        },
                        mLightweightExecutorService)
                .transform(
                        adWithBids -> {
                            return new Pair<>(
                                    getBestAdWithBidPerCA(adWithBids), buyerDecisionLogicJs);
                        },
                        mLightweightExecutorService);
    }

    @Nullable
    private AdWithBid getBestAdWithBidPerCA(@NonNull List<AdWithBid> adWithBids) {
        if (adWithBids.size() == 0) {
            LogUtil.v("No ad with bids for current CA");
            return null;
        }
        AdWithBid maxBidCandidate =
                adWithBids.stream().max(Comparator.comparingDouble(AdWithBid::getBid)).get();
        LogUtil.v("Obtained #%d ads with bids for current CA", adWithBids.size());
        if (maxBidCandidate.getBid() <= 0.0) {
            LogUtil.v("No positive bids found, no valid bids to return");
            return null;
        }
        LogUtil.v("Returning ad candidate with highest bid: %s", maxBidCandidate);
        return maxBidCandidate;
    }
}
