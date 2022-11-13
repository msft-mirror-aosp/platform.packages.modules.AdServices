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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;

import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionFromOutcomesInput;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.exceptions.AdServicesException;
import android.annotation.NonNull;
import android.net.Uri;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerUtil;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Orchestrator that runs the logic retrieved on a list of outcomes and signals.
 *
 * <p>Class takes in an executor on which it runs the OutcomeSelection logic
 */
public class OutcomeSelectionRunner {
    @VisibleForTesting static final String AD_SELECTION_FROM_OUTCOMES_ERROR_PATTERN = "%s: %s";

    @VisibleForTesting
    static final String ERROR_AD_SELECTION_FROM_OUTCOMES_FAILURE =
            "Encountered failure during Ad Selection";

    @VisibleForTesting
    static final String AD_OUTCOMES_LIST_INPUT_CANNOT_BE_EMPTY_MSG =
            "Ad outcomes list should at least have one element inside";

    private final int mCallerUid;
    @NonNull private final AdSelectionEntryDao mAdSelectionEntryDao;
    @NonNull protected final ListeningExecutorService mBackgroundExecutorService;
    @NonNull private final ListeningExecutorService mLightweightExecutorService;
    @NonNull protected final AdServicesLogger mAdServicesLogger;

    public OutcomeSelectionRunner(
            int callerUid,
            @NonNull final AdSelectionEntryDao adSelectionEntryDao,
            @NonNull final ExecutorService backgroundExecutorService,
            @NonNull final ExecutorService lightweightExecutorService,
            @NonNull final AdServicesLogger adServicesLogger) {
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(adServicesLogger);

        mCallerUid = callerUid;
        mAdSelectionEntryDao = adSelectionEntryDao;
        mBackgroundExecutorService = MoreExecutors.listeningDecorator(backgroundExecutorService);
        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutorService);
        mAdServicesLogger = adServicesLogger;
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
                            if (t instanceof ConsentManager.RevokedConsentException) {
                                notifyEmptySuccessToCaller(
                                        callback,
                                        AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED);
                            } else {
                                if (t.getCause() instanceof AdServicesException) {
                                    notifyFailureToCaller(t.getCause(), callback);
                                } else {
                                    notifyFailureToCaller(t, callback);
                                }
                            }
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
        FluentFuture<List<AdSelectionIdWithBid>> outcomeIdBidPairsFuture =
                FluentFuture.from(retrieveAdSelectionIdWithBidList(adOutcomes));

        return mLightweightExecutorService.submit(() -> null);
    }

    private void notifySuccessToCaller(AdSelectionOutcome result, AdSelectionCallback callback) {
        int resultCode = AdServicesStatusUtils.STATUS_UNSET;
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
            LogUtil.e(e, "Encountered exception during notifying AdSelectionCallback");
            resultCode = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
        } finally {
            LogUtil.v("Ad Selection from outcomes completed and attempted notifying success");
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN, resultCode, 0);
        }
    }

    /** Sends a successful response to the caller that represents a silent failure. */
    private void notifyEmptySuccessToCaller(@NonNull AdSelectionCallback callback, int resultCode) {
        try {
            callback.onSuccess(null);
        } catch (RemoteException e) {
            LogUtil.e(e, "Encountered exception during notifying AdSelectionCallback");
            resultCode = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
        } finally {
            LogUtil.v(
                    "Ad Selection from outcomes completed, attempted notifying success for a"
                            + " silent failure");
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN, resultCode, 0);
        }
    }

    /** Sends a failure notification to the caller */
    private void notifyFailureToCaller(Throwable t, AdSelectionCallback callback) {
        int resultCode = AdServicesStatusUtils.STATUS_UNSET;
        try {
            LogUtil.e("Notify caller of error: " + t);
            resultCode = AdServicesLoggerUtil.getResultCodeFromException(t);
            FledgeErrorResponse selectionFailureResponse =
                    new FledgeErrorResponse.Builder()
                            .setErrorMessage(
                                    String.format(
                                            AD_SELECTION_FROM_OUTCOMES_ERROR_PATTERN,
                                            ERROR_AD_SELECTION_FROM_OUTCOMES_FAILURE,
                                            t.getMessage()))
                            .setStatusCode(resultCode)
                            .build();
            LogUtil.e(t, "Ad Selection failure: ");
            callback.onFailure(selectionFailureResponse);
        } catch (RemoteException e) {
            LogUtil.e(e, "Encountered exception during notifying AdSelectionCallback");
            resultCode = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
        } finally {
            LogUtil.v("Ad Selection From Outcomes failed");
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN, resultCode, 0);
        }
    }

    /**
     * Validates the {@link AdSelectionFromOutcomesInput} request.
     *
     * @param inputParams the input to be validated
     * @throws IllegalArgumentException if the provided {@code inputParams} is not valid
     * @return an ignorable {@code null}
     */
    private Void validateRequest(@NonNull AdSelectionFromOutcomesInput inputParams) {
        // TODO(b/258020359): Implement validators for AdSelectionFromOutcomesInput
        validateInputParams(inputParams);
        return null;
    }

    /**
     * Validates the {@link AdSelectionFromOutcomesInput} from the request.
     *
     * @param inputParams the adSelectionConfig to be validated
     * @throws IllegalArgumentException if the provided {@code adSelectionConfig} is not valid
     * @return an ignorable {@code null}
     */
    private Void validateInputParams(@NonNull AdSelectionFromOutcomesInput inputParams)
            throws IllegalArgumentException {
        Objects.requireNonNull(inputParams);

        AdSelectionFromOutcomesInputValidator validator =
                new AdSelectionFromOutcomesInputValidator(mAdSelectionEntryDao);
        validator.validate(inputParams);

        return null;
    }

    /** Retrieves winner ad bids using ad selection ids of already run ad selections' outcomes. */
    @VisibleForTesting
    ListenableFuture<List<AdSelectionIdWithBid>> retrieveAdSelectionIdWithBidList(
            List<AdSelectionOutcome> adOutcomes) {
        List<AdSelectionIdWithBid> adSelectionIdWithBidList = new ArrayList<>();
        return mBackgroundExecutorService.submit(
                () -> {
                    List<Long> adOutcomeIds =
                            adOutcomes.parallelStream()
                                    .map(AdSelectionOutcome::getAdSelectionId)
                                    .collect(Collectors.toList());
                    mAdSelectionEntryDao.getAdSelectionEntities(adOutcomeIds).parallelStream()
                            .forEach(
                                    e ->
                                            adSelectionIdWithBidList.add(
                                                    AdSelectionIdWithBid.builder()
                                                            .setAdSelectionId(e.getAdSelectionId())
                                                            .setBid(e.getWinningAdBid())
                                                            .build()));
                    return adSelectionIdWithBidList;
                });
    }
}
