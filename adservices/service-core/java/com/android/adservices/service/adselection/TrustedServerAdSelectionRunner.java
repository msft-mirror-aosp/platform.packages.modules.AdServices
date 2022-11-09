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
import android.adservices.common.AdSelectionSignals;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.Uri;
import android.util.Pair;

import com.android.adservices.LogUtil;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdServicesHttpsClient;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.CustomAudienceDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.proto.SellerFrontEndGrpc;
import com.android.adservices.service.proto.SellerFrontendService.BuyerInput;
import com.android.adservices.service.proto.SellerFrontendService.SelectWinningAdRequest;
import com.android.adservices.service.proto.SellerFrontendService.SelectWinningAdRequest.SelectWinningAdRawRequest.ClientType;
import com.android.adservices.service.proto.SellerFrontendService.SelectWinningAdResponse;
import com.android.adservices.service.stats.AdSelectionExecutionLogger;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.grpc.Codec;
import io.grpc.ManagedChannel;
import io.grpc.okhttp.OkHttpChannelBuilder;

/**
 * Offload execution to Bidding & Auction services. Sends an umbrella request to the Seller Frontend
 * Service.
 */
public class TrustedServerAdSelectionRunner extends AdSelectionRunner {
    public static final String GZIP = new Codec.Gzip().getMessageEncoding(); // "gzip"

    @NonNull private final JsFetcher mJsFetcher;

    public TrustedServerAdSelectionRunner(
            @NonNull final Context context,
            @NonNull final CustomAudienceDao customAudienceDao,
            @NonNull final AdSelectionEntryDao adSelectionEntryDao,
            @NonNull final AdServicesHttpsClient adServicesHttpsClient,
            @NonNull final ExecutorService lightweightExecutorService,
            @NonNull final ExecutorService backgroundExecutorService,
            @NonNull final ScheduledThreadPoolExecutor scheduledExecutor,
            @NonNull final ConsentManager consentManager,
            @NonNull final AdServicesLogger adServicesLogger,
            @NonNull final DevContext devContext,
            @NonNull AppImportanceFilter appImportanceFilter,
            @NonNull final Flags flags,
            @NonNull final Supplier<Throttler> throttlerSupplier,
            int callerUid,
            @NonNull final FledgeAuthorizationFilter fledgeAuthorizationFilter,
            @NonNull final FledgeAllowListsFilter fledgeAllowListsFilter,
            @NonNull final AdSelectionExecutionLogger adSelectionExecutionLogger) {
        super(
                context,
                customAudienceDao,
                adSelectionEntryDao,
                lightweightExecutorService,
                backgroundExecutorService,
                scheduledExecutor,
                consentManager,
                adServicesLogger,
                appImportanceFilter,
                flags,
                throttlerSupplier,
                callerUid,
                fledgeAuthorizationFilter,
                fledgeAllowListsFilter,
                adSelectionExecutionLogger);

        CustomAudienceDevOverridesHelper mCustomAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(devContext, customAudienceDao);
        mJsFetcher =
                new JsFetcher(
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mCustomAudienceDevOverridesHelper,
                        adServicesHttpsClient);
    }

    @VisibleForTesting
    TrustedServerAdSelectionRunner(
            @NonNull final Context context,
            @NonNull final CustomAudienceDao customAudienceDao,
            @NonNull final AdSelectionEntryDao adSelectionEntryDao,
            @NonNull final ExecutorService lightweightExecutorService,
            @NonNull final ExecutorService backgroundExecutorService,
            @NonNull final ScheduledThreadPoolExecutor scheduledExecutor,
            @NonNull final ConsentManager consentManager,
            @NonNull final AdSelectionIdGenerator adSelectionIdGenerator,
            @NonNull Clock clock,
            @NonNull final AdServicesLogger adServicesLogger,
            @NonNull AppImportanceFilter appImportanceFilter,
            @NonNull final Flags flags,
            @NonNull final Supplier<Throttler> throttlerSupplier,
            int callerUid,
            @NonNull final FledgeAuthorizationFilter fledgeAuthorizationFilter,
            @NonNull final FledgeAllowListsFilter fledgeAllowListsFilter,
            @NonNull final JsFetcher jsFetcher,
            @NonNull final AdSelectionExecutionLogger adSelectionExecutionLogger) {
        super(
                context,
                customAudienceDao,
                adSelectionEntryDao,
                lightweightExecutorService,
                backgroundExecutorService,
                scheduledExecutor,
                consentManager,
                adSelectionIdGenerator,
                clock,
                adServicesLogger,
                appImportanceFilter,
                flags,
                throttlerSupplier,
                callerUid,
                fledgeAuthorizationFilter,
                fledgeAllowListsFilter,
                adSelectionExecutionLogger);

        this.mJsFetcher = jsFetcher;
    }

    /** Prepares request and calls Seller Front-end Service to orchestrate ad selection. */
    public ListenableFuture<AdSelectionOrchestrationResult> orchestrateAdSelection(
            @NonNull final AdSelectionConfig adSelectionConfig,
            @NonNull final String callerPackageName,
            @NonNull ListenableFuture<List<DBCustomAudience>> buyersCustomAudiences) {

        Function<List<DBCustomAudience>, Map<String, BuyerInput>> createBuyerInputs =
                buyerCAs -> {
                    return createBuyerInputs(buyerCAs, adSelectionConfig);
                };

        Function<Map<String, BuyerInput>, SelectWinningAdRequest> createSelectWinningAdRequest =
                encryptedInputPerBuyer -> {
                    return createSelectWinningAdRequest(adSelectionConfig, encryptedInputPerBuyer);
                };

        AsyncFunction<SelectWinningAdRequest, SelectWinningAdResponse> callSelectWinningAd =
                req -> {
                    return callSelectWinningAd(req);
                };

        // Return the DBCustomAudience to fetch the buyerLogicJs in the next future.
        Function<SelectWinningAdResponse, Pair<DBAdSelection.Builder, DBCustomAudience>>
                getCustomAudienceAndDBAdSelection =
                        selectWinningAdResponse -> {
                            return getCustomAudienceAndDBAdSelection(
                                    selectWinningAdResponse,
                                    callerPackageName,
                                    buyersCustomAudiences);
                        };

        // TODO(b/254066067): Confirm if buyer logic for reporting can be fetched after rendering.
        AsyncFunction<
                        Pair<DBAdSelection.Builder, DBCustomAudience>,
                        Pair<DBAdSelection.Builder, FluentFuture<String>>>
                fetchBuyerLogicJs =
                        dbAdSelectionAndCAPair -> {
                            return fetchBuyerLogicJs(dbAdSelectionAndCAPair);
                        };

        Function<Pair<DBAdSelection.Builder, FluentFuture<String>>, AdSelectionOrchestrationResult>
                createAdSelectionResult =
                        dbAdSelectionAndBuyerLogicJsPair -> {
                            return createAdSelectionResult(dbAdSelectionAndBuyerLogicJsPair);
                        };

        return FluentFuture.from(buyersCustomAudiences)
                .transform(createBuyerInputs, mLightweightExecutorService)
                .transform(createSelectWinningAdRequest, mLightweightExecutorService)
                .transformAsync(callSelectWinningAd, mBackgroundExecutorService)
                .transform(getCustomAudienceAndDBAdSelection, mLightweightExecutorService)
                .transformAsync(fetchBuyerLogicJs, mBackgroundExecutorService)
                .transform(createAdSelectionResult, mLightweightExecutorService)
                .withTimeout(
                        mFlags.getAdSelectionOffDeviceOverallTimeoutMs(),
                        TimeUnit.MILLISECONDS,
                        mScheduledExecutor)
                .catching(
                        TimeoutException.class,
                        this::handleTimeoutError,
                        mLightweightExecutorService);
    }

    private Map<String, BuyerInput> createBuyerInputs(
            List<DBCustomAudience> buyerCAs, AdSelectionConfig adSelectionConfig) {
        Map<String, BuyerInput> buyerInputs = new HashMap<>();
        for (DBCustomAudience customAudience : buyerCAs) {
            BuyerInput.CustomAudience.Builder customAudienceBuilder =
                    BuyerInput.CustomAudience.newBuilder()
                            .setName(customAudience.getName())
                            .addAllBiddingSignalsKeys(getBiddingSignalKeys(customAudience));

            AdSelectionSignals perBuyerSignals =
                    adSelectionConfig.getPerBuyerSignals().get(customAudience.getBuyer());
            BuyerInput input =
                    BuyerInput.newBuilder()
                            .addCustomAudiences(customAudienceBuilder)
                            .setBuyerSignals(convertSignalsToStruct(perBuyerSignals))
                            .build();
            // TODO(b/254325545): Update the key to the domain of the BFE service, not buyer name.
            buyerInputs.put(customAudience.getBuyer().toString(), input);
        }

        return buyerInputs;
    }

    private List<String> getBiddingSignalKeys(DBCustomAudience customAudience) {
        List<String> biddingSignalKeys = customAudience.getTrustedBiddingData().getKeys();
        // If the bidding signal keys is just the CA name, we don't need to pass it to the server.
        if (biddingSignalKeys.size() == 1
                && customAudience.getName().equals(biddingSignalKeys.get(0))) {
            return ImmutableList.of();
        }

        // Remove the CA name from the bidding signal keys list to save space.
        biddingSignalKeys.remove(customAudience.getName());
        return biddingSignalKeys;
    }

    private SelectWinningAdRequest createSelectWinningAdRequest(
            AdSelectionConfig adSelectionConfig, Map<String, BuyerInput> rawInputPerBuyer) {
        SelectWinningAdRequest.SelectWinningAdRawRequest.AuctionConfig.Builder auctionConfig =
                SelectWinningAdRequest.SelectWinningAdRawRequest.AuctionConfig.newBuilder()
                        .setSellerSignals(
                                convertSignalsToStruct((adSelectionConfig.getSellerSignals())))
                        // TODO(b/254068070): Check if this is contextually derived auction_signals.
                        .setAuctionSignals(
                                convertSignalsToStruct(adSelectionConfig.getAdSelectionSignals()));

        SelectWinningAdRequest.SelectWinningAdRawRequest.Builder rawRequestBuilder =
                SelectWinningAdRequest.SelectWinningAdRawRequest.newBuilder()
                        .setAdSelectionRequestId(mAdSelectionIdGenerator.generateId())
                        .putAllRawBuyerInput(rawInputPerBuyer)
                        .setAuctionConfig(auctionConfig)
                        // FLEDGE is currently only supported on GMS core devices.
                        .setClientType(ClientType.ANDROID);

        return SelectWinningAdRequest.newBuilder().setRawRequest(rawRequestBuilder).build();
    }

    private ListenableFuture<SelectWinningAdResponse> callSelectWinningAd(
            SelectWinningAdRequest req) {
        // TODO(b/249575366): Pass in address + port when the fields are added.
        ManagedChannel channel = OkHttpChannelBuilder.forAddress("localhost", 8080).build();
        SellerFrontEndGrpc.SellerFrontEndFutureStub stub =
                SellerFrontEndGrpc.newFutureStub(channel);

        if (mFlags.getAdSelectionOffDeviceRequestCompressionEnabled()) {
            stub = stub.withCompression(GZIP);
        }

        return stub.selectWinningAd(req);
    }

    private Pair<DBAdSelection.Builder, DBCustomAudience> getCustomAudienceAndDBAdSelection(
            SelectWinningAdResponse selectWinningAdResponse,
            String callerPackageName,
            ListenableFuture<List<DBCustomAudience>> buyerCustomAudiences) {
        SelectWinningAdResponse.SelectWinningAdRawResponse rawResponse =
                selectWinningAdResponse.getRawResponse();
        Uri winningAdRenderUri = Uri.parse(rawResponse.getAdRenderUrl());

        // Find custom audience of the winning ad.
        DBCustomAudience customAudience;
        try {
            // buyerCustomAudiences's future is already complete by the time this method is called.
            List<DBCustomAudience> customAudiences = buyerCustomAudiences.get();
            List<DBCustomAudience> filteredCustomAudiences =
                    customAudiences.stream()
                            .filter(
                                    audience ->
                                            audience.getName()
                                                    .equals(rawResponse.getCustomAudienceName()))
                            .collect(Collectors.toList());
            customAudience = Iterables.getOnlyElement(filteredCustomAudiences);
        } catch (InterruptedException | ExecutionException e) {
            // Will never be thrown since the future has already completed for the code to be here.
            throw new RuntimeException("Could not read buyerCustomAudiences list from device");
        } catch (NoSuchElementException e) {
            throw new IllegalStateException(
                    "Could not find corresponding custom audience returned from Bidding & Auction"
                            + " services");
        }

        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignals.buildFromCustomAudience(customAudience);
        DBAdSelection.Builder builder =
                new DBAdSelection.Builder()
                        .setWinningAdBid(rawResponse.getBidPrice())
                        .setWinningAdRenderUri(winningAdRenderUri)
                        .setCustomAudienceSignals(customAudienceSignals)
                        .setBiddingLogicUri(customAudience.getBiddingLogicUri())
                        .setContextualSignals("{}")
                        .setCallerPackageName(callerPackageName);

        return new Pair(builder, customAudience);
    }

    private ListenableFuture<Pair<DBAdSelection.Builder, FluentFuture<String>>> fetchBuyerLogicJs(
            Pair<DBAdSelection.Builder, DBCustomAudience> dbAdSelectionAndCAPair) {
        return mBackgroundExecutorService.submit(
                () -> {
                    DBCustomAudience customAudience = dbAdSelectionAndCAPair.second;
                    FluentFuture<String> buyerDecisionLogic =
                            mJsFetcher.getBuyerDecisionLogic(
                                    customAudience.getBiddingLogicUri(),
                                    customAudience.getOwner(),
                                    customAudience.getBuyer(),
                                    customAudience.getName());
                    return new Pair(dbAdSelectionAndCAPair.first, buyerDecisionLogic);
                });
    }

    private AdSelectionOrchestrationResult createAdSelectionResult(
            Pair<DBAdSelection.Builder, FluentFuture<String>> dbAdSelectionAndBuyerLogicJsPair) {
        try {
            String buyerJsLogic = dbAdSelectionAndBuyerLogicJsPair.second.get();
            return new AdSelectionOrchestrationResult(
                    dbAdSelectionAndBuyerLogicJsPair.first, buyerJsLogic);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException("Could not fetch buyerJsLogic", e);
        }
    }

    private Struct convertSignalsToStruct(AdSelectionSignals adSelectionSignals) {
        Struct.Builder signals = Struct.newBuilder();
        try {
            JSONObject json = new JSONObject(adSelectionSignals.toString());
            for (String keyStr : json.keySet()) {
                Object obj = json.get(keyStr);
                if (obj instanceof String) {
                    signals.putFields(
                            keyStr, Value.newBuilder().setStringValue((String) obj).build());
                }
            }
        } catch (JSONException e) {
            String error = "Invalid JSON found during SelectWinningAdRequest construction";
            throw new IllegalArgumentException(error, e);
        }

        return signals.build();
    }

    @Nullable
    private AdSelectionOrchestrationResult handleTimeoutError(TimeoutException e) {
        LogUtil.e(e, "Ad Selection exceeded time limit");
        throw new UncheckedTimeoutException(AD_SELECTION_TIMED_OUT);
    }
}
