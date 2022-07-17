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
import android.adservices.adselection.AdWithBid;
import android.adservices.common.AdData;
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
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.json.JSONException;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * This class implements the ad bid generator. A new instance is assumed to be created for every
 * call
 */
public class AdBidGeneratorImpl implements AdBidGenerator {

    @VisibleForTesting static final String QUERY_PARAM_KEYS = "keys";

    @VisibleForTesting
    static final String MISSING_TRUSTED_BIDDING_SIGNALS = "Error fetching trusted bidding signals";

    @NonNull private final Context mContext;
    @NonNull private final ListeningExecutorService mListeningExecutorService;
    @NonNull private final AdSelectionScriptEngine mAdSelectionScriptEngine;
    @NonNull private final AdServicesHttpsClient mAdServicesHttpsClient;
    @NonNull private final CustomAudienceDevOverridesHelper mCustomAudienceDevOverridesHelper;
    @NonNull private final Flags mFlags;

    public AdBidGeneratorImpl(
            @NonNull Context context,
            @NonNull ExecutorService listeningExecutorService,
            @NonNull DevContext devContext,
            @NonNull CustomAudienceDao customAudienceDao,
            @NonNull Flags flags) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(listeningExecutorService);

        mContext = context;
        mListeningExecutorService = MoreExecutors.listeningDecorator(listeningExecutorService);
        mAdSelectionScriptEngine = new AdSelectionScriptEngine(mContext);
        mAdServicesHttpsClient = new AdServicesHttpsClient(listeningExecutorService);
        mCustomAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(devContext, customAudienceDao);
        mFlags = flags;
    }

    @VisibleForTesting
    AdBidGeneratorImpl(
            @NonNull Context context,
            @NonNull ListeningExecutorService listeningExecutorService,
            @NonNull AdSelectionScriptEngine adSelectionScriptEngine,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper,
            @NonNull Flags flags) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(listeningExecutorService);
        Objects.requireNonNull(adSelectionScriptEngine);
        Objects.requireNonNull(adServicesHttpsClient);
        Objects.requireNonNull(customAudienceDevOverridesHelper);
        Objects.requireNonNull(flags);

        mContext = context;
        mListeningExecutorService = listeningExecutorService;
        mAdSelectionScriptEngine = adSelectionScriptEngine;
        mAdServicesHttpsClient = adServicesHttpsClient;
        mCustomAudienceDevOverridesHelper = customAudienceDevOverridesHelper;
        mFlags = flags;
    }

    @Override
    @NonNull
    public FluentFuture<AdBiddingOutcome> runAdBiddingPerCA(
            @NonNull DBCustomAudience customAudience,
            @NonNull String adSelectionSignals,
            @NonNull String buyerSignals,
            @NonNull String contextualSignals,
            @NonNull AdSelectionConfig adSelectionConfig) {
        Objects.requireNonNull(customAudience);
        Objects.requireNonNull(adSelectionSignals);
        Objects.requireNonNull(buyerSignals);
        Objects.requireNonNull(contextualSignals);
        Objects.requireNonNull(adSelectionConfig);

        if (customAudience.getAds().isEmpty()) {
            return FluentFuture.from(Futures.immediateFuture(null));
        }

        String userSignals = buildUserSignals(customAudience);
        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignals.buildFromCustomAudience(customAudience);

        // TODO(b/221862406): implement ads filtering logic.

        FluentFuture<String> buyerDecisionLogic =
                getBuyerDecisionLogic(
                        customAudience.getBiddingLogicUrl(),
                        customAudience.getOwner(),
                        customAudience.getBuyer(),
                        customAudience.getName());

        FluentFuture<Pair<AdWithBid, String>> adWithBidPair =
                buyerDecisionLogic.transformAsync(
                        decisionLogic -> {
                            return runBidding(
                                    customAudience,
                                    decisionLogic,
                                    buyerSignals,
                                    contextualSignals,
                                    customAudienceSignals,
                                    userSignals,
                                    adSelectionSignals);
                        },
                        mListeningExecutorService);
        return adWithBidPair
                .transform(
                        candidate -> {
                            if (Objects.isNull(candidate)
                                    || Objects.isNull(candidate.first)
                                    || candidate.first.getBid() <= 0.0) {
                                return null;
                            }
                            CustomAudienceBiddingInfo customAudienceInfo =
                                    CustomAudienceBiddingInfo.create(
                                            customAudience, candidate.second);
                            AdBiddingOutcome result =
                                    AdBiddingOutcome.builder()
                                            .setAdWithBid(candidate.first)
                                            .setCustomAudienceBiddingInfo(customAudienceInfo)
                                            .build();
                            return result;
                        },
                        mListeningExecutorService)
                .withTimeout(
                        mFlags.getAdSelectionBiddingTimeoutPerCaMs(),
                        TimeUnit.MILLISECONDS,
                        // TODO(b/237103033): Compile with thread usage policy for AdServices;
                        //  use a global scheduled executor
                        new ScheduledThreadPoolExecutor(1))
                .catching(JSONException.class, this::handleBiddingError, mListeningExecutorService);
    }

    @Nullable
    private AdBiddingOutcome handleBiddingError(JSONException e) {
        // TODO(b/231326420): Define and implement the certain non-expected exceptions should be
        // re-throw from the AdBidGenerator.
        LogUtil.e("Failed to generate bids for the ads in this custom audience.", e);
        return null;
    }

    private FluentFuture<String> getTrustedBiddingSignals(
            @NonNull DBTrustedBiddingData trustedBiddingData,
            @NonNull String owner,
            @NonNull String buyer,
            @NonNull String name) {
        Objects.requireNonNull(trustedBiddingData);
        final Uri trustedBiddingUri = trustedBiddingData.getUrl();
        final List<String> trustedBiddingKeys = trustedBiddingData.getKeys();
        final String keysQueryParams = String.join(",", trustedBiddingKeys);
        final Uri trustedBiddingUriWithKeys =
                Uri.parse(trustedBiddingUri.toString())
                        .buildUpon()
                        .appendQueryParameter(QUERY_PARAM_KEYS, keysQueryParams)
                        .build();

        FluentFuture<String> jsOverrideFuture =
                FluentFuture.from(
                        mListeningExecutorService.submit(
                                () ->
                                        mCustomAudienceDevOverridesHelper
                                                .getTrustedBiddingSignalsOverride(
                                                        owner, buyer, name)));
        return jsOverrideFuture
                .transformAsync(
                        jsOverride -> {
                            if (jsOverride == null) {
                                return mAdServicesHttpsClient.fetchPayload(
                                        trustedBiddingUriWithKeys);
                            } else {
                                LogUtil.d(
                                        "Developer options enabled and override trusted signals"
                                                + " are provided for the current Custom Audience."
                                                + " Skipping call to server.");
                                return Futures.immediateFuture(jsOverride);
                            }
                        },
                        mListeningExecutorService)
                .catching(
                        Exception.class,
                        e -> {
                            LogUtil.w("Exception encountered when fetching trusted signals", e);
                            throw new IllegalStateException(MISSING_TRUSTED_BIDDING_SIGNALS);
                        },
                        mListeningExecutorService);
    }

    private FluentFuture<String> getBuyerDecisionLogic(
            @NonNull final Uri decisionLogicUri,
            @NonNull String owner,
            @NonNull String buyer,
            @NonNull String name) {
        FluentFuture<String> jsOverrideFuture =
                FluentFuture.from(
                        mListeningExecutorService.submit(
                                () ->
                                        mCustomAudienceDevOverridesHelper.getBiddingLogicOverride(
                                                owner, buyer, name)));
        return jsOverrideFuture.transformAsync(
                jsOverride -> {
                    if (jsOverride == null) {
                        return mAdServicesHttpsClient.fetchPayload(decisionLogicUri);
                    } else {
                        LogUtil.d(
                                "Developer options enabled and an override JS is provided "
                                        + "for the current Custom Audience. "
                                        + "Skipping call to server.");
                        return Futures.immediateFuture(jsOverride);
                    }
                },
                mListeningExecutorService);
    }

    /**
     * @return user information with respect to the custom audience will be available to
     *     generateBid(). This could include language, demographic information, information about
     *     custom audience such as time in list, number of impressions, last N winning impression
     *     timestamp etc.
     */
    @NonNull
    public String buildUserSignals(@Nullable DBCustomAudience customAudience) {
        // TODO: implement how to build user_signals with respect to customAudience.
        return "{}";
    }

    /** @return the {@link AdWithBid} with the best bid per CustomAudience. */
    @NonNull
    @VisibleForTesting
    FluentFuture<Pair<AdWithBid, String>> runBidding(
            @NonNull DBCustomAudience customAudience,
            @NonNull String buyerDecisionLogicJs,
            @NonNull String buyerSignals,
            @NonNull String contextualSignals,
            @NonNull CustomAudienceSignals customAudienceSignals,
            @NonNull String userSignals,
            @NonNull String adSelectionSignals) {

        FluentFuture<String> trustedBiddingSignals =
                getTrustedBiddingSignals(
                        customAudience.getTrustedBiddingData(),
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
                                    userSignals,
                                    customAudienceSignals);
                        },
                        mListeningExecutorService)
                .transform(
                        adWithBids -> {
                            return new Pair<>(
                                    getBestAdWithBidPerCA(adWithBids), buyerDecisionLogicJs);
                        },
                        mListeningExecutorService);
    }

    @Nullable
    private AdWithBid getBestAdWithBidPerCA(@NonNull List<AdWithBid> adWithBids) {
        if (adWithBids.size() == 0) return null;
        AdWithBid maxBidCandidate =
                adWithBids.stream().max(Comparator.comparingDouble(AdWithBid::getBid)).get();
        if (maxBidCandidate.getBid() <= 0.0) {
            LogUtil.d("The max bid candidate should have positive bid.");
            return null;
        }
        return maxBidCandidate;
    }
}
