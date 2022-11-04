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
import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;

import com.android.adservices.LogUtil;
import com.android.adservices.data.customaudience.DBCustomAudience;

import com.google.common.util.concurrent.ExecutionSequencer;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Runs bidding for a buyer and its associated Custom Audience. The bidding for every buyer is time
 * capped, where the incomplete CAs are dropped from bidding when timed out while preserving the
 * ones that were already completed
 */
public class PerBuyerBiddingRunner {
    @NonNull private AdBidGenerator mAdBidGenerator;
    @NonNull private ScheduledThreadPoolExecutor mScheduledExecutor;
    @NonNull private ListeningExecutorService mBackgroundExecutorService;

    public PerBuyerBiddingRunner(
            @NonNull AdBidGenerator adBidGenerator,
            @NonNull ScheduledThreadPoolExecutor scheduledExecutor,
            @NonNull ListeningExecutorService backgroundExecutorService) {
        mAdBidGenerator = adBidGenerator;
        mScheduledExecutor = scheduledExecutor;
        mBackgroundExecutorService = backgroundExecutorService;
    }
    /**
     * This method executes bidding sequentially on the list of CustomAudience for a buyer. By
     * leveraging the sequential executor, the bidding for subsequent Custom Audience is not even
     * started until the previous bidding completes. This leads to significant saving of resources
     * as without sequence, all the CAs begin bidding async and start downloading JS and consuming
     * other resources. This ensures that at any point, only one bidding would be in progress.
     *
     * @param buyerTimeoutMs timeout value, post which incomplete CA bids are cancelled
     * @param adSelectionConfig for the current Ad Selection
     * @return list of futures with bidding outcomes
     */
    public List<ListenableFuture<AdBiddingOutcome>> runBidding(
            final AdTechIdentifier buyer,
            final List<DBCustomAudience> customAudienceList,
            final long buyerTimeoutMs,
            final AdSelectionConfig adSelectionConfig) {
        LogUtil.v(
                "Running bid for #%d Custom Audiences for buyer: %s",
                customAudienceList.size(), buyer);

        /*
         * We require a unique sequencer per buyer, as using a global sequencer enforces sequence
         * across buyers, where a buyer can starve other buyers' CAs from bidding.
         */
        ExecutionSequencer sequencer = ExecutionSequencer.create();
        List<ListenableFuture<AdBiddingOutcome>> buyerBiddingOutcomes =
                customAudienceList.stream()
                        .map(
                                (customAudience) ->
                                        sequencer.submitAsync(
                                                () ->
                                                        runBiddingPerCA(
                                                                customAudience, adSelectionConfig),
                                                mBackgroundExecutorService))
                        .collect(Collectors.toList());

        eventuallyTimeoutIncompleteTasks(buyerTimeoutMs, buyerBiddingOutcomes);
        return buyerBiddingOutcomes;
    }

    private ListenableFuture<AdBiddingOutcome> runBiddingPerCA(
            @NonNull final DBCustomAudience customAudience,
            @NonNull final AdSelectionConfig adSelectionConfig) {
        LogUtil.v(String.format("Invoking bidding for CA: %s", customAudience.getName()));

        // TODO(b/233239475) : Validate Buyer signals in Ad Selection Config
        AdSelectionSignals buyerSignal =
                Optional.ofNullable(
                                adSelectionConfig
                                        .getPerBuyerSignals()
                                        .get(customAudience.getBuyer()))
                        .orElse(AdSelectionSignals.EMPTY);
        return mAdBidGenerator.runAdBiddingPerCA(
                customAudience,
                adSelectionConfig.getAdSelectionSignals(),
                buyerSignal,
                AdSelectionSignals.EMPTY);
    }

    /**
     * Instead of timing out entire list of future, we only cancel the ones which are not done. This
     * helps preserve tasks that are already completed while freeing up resources from the tasks
     * which maybe in progress or are yet to be scheduled by cancelling them.
     *
     * @param timeoutMs delay after which these tasks should be cancelled
     * @param runningTasks potentially ongoing tasks, that need to be timed-out
     */
    private <T> void eventuallyTimeoutIncompleteTasks(
            final long timeoutMs, List<ListenableFuture<T>> runningTasks) {
        Runnable cancelOngoingTasks =
                () -> {
                    int incompleteTaskCount = 0;
                    for (ListenableFuture<T> runningTask : runningTasks) {
                        // TODO(b/254176437): use Closing futures to free up resources
                        if (runningTask.cancel(true)) {
                            incompleteTaskCount++;
                        }
                    }
                    LogUtil.v(
                            "Total tasks: #%d, cancelled incomplete tasks: #%d",
                            runningTasks.size(), incompleteTaskCount);
                };
        mScheduledExecutor.schedule(cancelOngoingTasks, timeoutMs, TimeUnit.MILLISECONDS);
    }
}
