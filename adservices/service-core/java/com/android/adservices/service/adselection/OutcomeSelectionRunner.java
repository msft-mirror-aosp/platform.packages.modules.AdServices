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

import static android.adservices.common.AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;

import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionFromOutcomesInput;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.FledgeErrorResponse;
import android.annotation.NonNull;
import android.net.Uri;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.data.adselection.AdSelectionEntryDao;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Orchestrator that runs the logic retrieved on a list of outcomes and signals.
 *
 * <p>Class takes in an executor on which it runs the OutcomeSelection logic
 */
public class OutcomeSelectionRunner {
    @NonNull private final AdSelectionEntryDao mAdSelectionEntryDao;
    @NonNull protected final ListeningExecutorService mBackgroundExecutorService;
    @NonNull private final ListeningExecutorService mLightweightExecutorService;

    @VisibleForTesting
    static final String AD_OUTCOMES_LIST_INPUT_CANNOT_BE_EMPTY_MSG =
            "Ad outcomes list should at least have one element inside";

    public OutcomeSelectionRunner(
            @NonNull final AdSelectionEntryDao adSelectionEntryDao,
            @NonNull final ExecutorService backgroundExecutorService,
            @NonNull final ExecutorService lightweightExecutorService) {
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(lightweightExecutorService);

        mAdSelectionEntryDao = adSelectionEntryDao;
        mBackgroundExecutorService = MoreExecutors.listeningDecorator(backgroundExecutorService);
        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutorService);
    }

    /**
     * Runs outcome selection logic on given list of outcomes and signals.
     *
     * @param inputParams includes list of outcomes, selection signals and URI to download the logic
     * @param callback is used to notify the results to the caller
     */
    public void runOutcomeSelection(
            @NonNull AdSelectionFromOutcomesInput inputParams,
            @NonNull AdSelectionCallback callback) {
        Objects.requireNonNull(inputParams);
        Objects.requireNonNull(callback);

        try {
            ListenableFuture<Void> validateRequestFuture =
                    Futures.submit(() -> validateRequest(inputParams), mLightweightExecutorService);

            ListenableFuture<AdSelectionOutcome> adSelectionOutcomeFuture =
                    FluentFuture.from(validateRequestFuture)
                            .transformAsync(
                                    ignoredVoid ->
                                            orchestrateOutcomeSelection(
                                                    inputParams.getAdOutcomes(),
                                                    inputParams.getSelectionSignals(),
                                                    inputParams.getSelectionLogicUri()),
                                    mLightweightExecutorService);

            Futures.addCallback(
                    adSelectionOutcomeFuture,
                    new FutureCallback<AdSelectionOutcome>() {
                        @Override
                        public void onSuccess(AdSelectionOutcome result) {
                            notifySuccessToCaller(result, callback);
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            notifyFailureToCaller(t, callback);
                        }
                    },
                    mLightweightExecutorService);

        } catch (Throwable t) {
            LogUtil.v("runOutcomeSelection fails fast with exception %s.", t.toString());
            notifyFailureToCaller(t, callback);
        }
    }

    private ListenableFuture<AdSelectionOutcome> orchestrateOutcomeSelection(
            @NonNull List<AdSelectionOutcome> adOutcomes,
            @NonNull AdSelectionSignals selectionSignals,
            @NonNull Uri selectionUri) {
        // TODO(249843968): Implement outcome selection service orchestration
        FluentFuture<Map<Long, Double>> outcomeIdBidPairsFuture =
                FluentFuture.from(retrieveAdSelectionIdToBidMap(adOutcomes));

        return mLightweightExecutorService.submit(() -> null);
    }

    private void notifySuccessToCaller(AdSelectionOutcome result, AdSelectionCallback callback) {
        try {
            if (result == null) {
                callback.onSuccess(null);
            } else {
                callback.onSuccess(
                        new AdSelectionResponse.Builder()
                                .setAdSelectionId(result.getAdSelectionId())
                                .setRenderUri(result.getRenderUri())
                                .build());
            }
        } catch (RemoteException e) {
            LogUtil.e(e, "Encountered exception during callback");
            throw new IllegalStateException(e);
        }
    }

    private void notifyFailureToCaller(Throwable t, AdSelectionCallback callback) {
        // TODO(257678151): Implement more informative failure handling
        try {
            LogUtil.e("Notify caller of error: " + t);
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setErrorMessage(t.getMessage())
                            .setStatusCode(STATUS_UNKNOWN_ERROR)
                            .build());
        } catch (RemoteException e) {
            LogUtil.e("Failed to notify caller: " + e);
        }
    }

    private Void validateRequest(AdSelectionFromOutcomesInput adSelectionOutcome) {
        // TODO(258020359): Implement validators for AdSelectionFromOutcomesInput
        Preconditions.checkArgument(
                !adSelectionOutcome.getAdOutcomes().isEmpty(),
                new IllegalArgumentException(AD_OUTCOMES_LIST_INPUT_CANNOT_BE_EMPTY_MSG));
        return null;
    }

    /** Retrieves winner ad bids using ad selection ids of already run ad selections' outcomes. */
    @VisibleForTesting
    ListenableFuture<Map<Long, Double>> retrieveAdSelectionIdToBidMap(
            List<AdSelectionOutcome> adOutcomes) {
        Map<Long, Double> retrievedIdBidPairs = new HashMap<>();
        return mBackgroundExecutorService.submit(
                () -> {
                    List<Long> adOutcomeIds =
                            adOutcomes.parallelStream()
                                    .map(AdSelectionOutcome::getAdSelectionId)
                                    .collect(Collectors.toList());
                    mAdSelectionEntryDao.getAdSelectionEntities(adOutcomeIds).parallelStream()
                            .forEach(
                                    e ->
                                            retrievedIdBidPairs.put(
                                                    e.getAdSelectionId(), e.getWinningAdBid()));
                    return retrievedIdBidPairs;
                });
    }
}
