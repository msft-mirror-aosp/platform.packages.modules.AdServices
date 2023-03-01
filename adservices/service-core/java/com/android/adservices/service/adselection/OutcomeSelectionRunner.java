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
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionFromOutcomesInput;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.exceptions.AdServicesException;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.Build;
import android.os.RemoteException;
import android.os.Trace;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.FledgeServiceFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.AdSelectionDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.profiling.Tracing;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerUtil;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.UncheckedTimeoutException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Orchestrator that runs the logic retrieved on a list of outcomes and signals.
 *
 * <p>Class takes in an executor on which it runs the OutcomeSelection logic
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class OutcomeSelectionRunner {
    @VisibleForTesting static final String AD_SELECTION_FROM_OUTCOMES_ERROR_PATTERN = "%s: %s";

    @VisibleForTesting
    static final String ERROR_AD_SELECTION_FROM_OUTCOMES_FAILURE =
            "Encountered failure during Ad Selection";

    @VisibleForTesting
    static final String SELECTED_OUTCOME_MUST_BE_ONE_OF_THE_INPUTS =
            "Outcome selection must return a valid ad selection id";

    @VisibleForTesting
    static final String AD_SELECTION_TIMED_OUT = "Ad selection exceeded allowed time limit";

    @NonNull private final AdSelectionEntryDao mAdSelectionEntryDao;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;
    @NonNull private final ListeningExecutorService mLightweightExecutorService;
    @NonNull private final ScheduledThreadPoolExecutor mScheduledExecutor;
    @NonNull private final AdServicesHttpsClient mAdServicesHttpsClient;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final Context mContext;
    @NonNull private final Flags mFlags;

    @NonNull private final AdOutcomeSelector mAdOutcomeSelector;
    @NonNull private final FledgeServiceFilter mFledgeServiceFilter;
    private final int mCallerUid;

    /**
     * @param adSelectionEntryDao DAO to access ad selection storage
     * @param backgroundExecutorService executor for longer running tasks (ex. network calls)
     * @param lightweightExecutorService executor for running short tasks
     * @param scheduledExecutor executor for tasks to be run with a delay or timed executions
     * @param adServicesHttpsClient HTTPS client to use when fetch JS logics
     * @param adServicesLogger logger for logging calls to PPAPI
     * @param context service context
     * @param flags for accessing feature flags
     * @param fledgeServiceFilter to validate the request
     */
    public OutcomeSelectionRunner(
            @NonNull final AdSelectionEntryDao adSelectionEntryDao,
            @NonNull final ExecutorService backgroundExecutorService,
            @NonNull final ExecutorService lightweightExecutorService,
            @NonNull final ScheduledThreadPoolExecutor scheduledExecutor,
            @NonNull final AdServicesHttpsClient adServicesHttpsClient,
            @NonNull final AdServicesLogger adServicesLogger,
            @NonNull final DevContext devContext,
            @NonNull final Context context,
            @NonNull final Flags flags,
            @NonNull final FledgeServiceFilter fledgeServiceFilter,
            final int callerUid) {
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(scheduledExecutor);
        Objects.requireNonNull(adServicesHttpsClient);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(devContext);
        Objects.requireNonNull(context);
        Objects.requireNonNull(flags);

        mAdSelectionEntryDao = adSelectionEntryDao;
        mBackgroundExecutorService = MoreExecutors.listeningDecorator(backgroundExecutorService);
        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutorService);
        mScheduledExecutor = scheduledExecutor;
        mAdServicesHttpsClient = adServicesHttpsClient;
        mAdServicesLogger = adServicesLogger;
        mContext = context;
        mFlags = flags;

        mAdOutcomeSelector =
                new AdOutcomeSelectorImpl(
                        new AdSelectionScriptEngine(
                                mContext,
                                flags::getEnforceIsolateMaxHeapSize,
                                flags::getIsolateMaxHeapSizeBytes),
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdServicesHttpsClient,
                        new AdSelectionDevOverridesHelper(devContext, adSelectionEntryDao),
                        mFlags);
        mFledgeServiceFilter = fledgeServiceFilter;
        mCallerUid = callerUid;
    }

    @VisibleForTesting
    public OutcomeSelectionRunner(
            int callerUid,
            @NonNull final AdOutcomeSelector adOutcomeSelector,
            @NonNull final AdSelectionEntryDao adSelectionEntryDao,
            @NonNull final ExecutorService backgroundExecutorService,
            @NonNull final ExecutorService lightweightExecutorService,
            @NonNull final ScheduledThreadPoolExecutor scheduledExecutor,
            @NonNull final AdServicesLogger adServicesLogger,
            @NonNull final Context context,
            @NonNull final Flags flags,
            @NonNull final FledgeServiceFilter fledgeServiceFilter) {
        Objects.requireNonNull(adOutcomeSelector);
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(scheduledExecutor);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(context);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(fledgeServiceFilter);

        mAdSelectionEntryDao = adSelectionEntryDao;
        mBackgroundExecutorService = MoreExecutors.listeningDecorator(backgroundExecutorService);
        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutorService);
        mScheduledExecutor = scheduledExecutor;
        mAdServicesHttpsClient =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        CacheProviderFactory.create(context, flags));
        mAdServicesLogger = adServicesLogger;
        mContext = context;
        mFlags = flags;

        mAdOutcomeSelector = adOutcomeSelector;
        mFledgeServiceFilter = fledgeServiceFilter;
        mCallerUid = callerUid;
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
        AdSelectionFromOutcomesConfig adSelectionFromOutcomesConfig =
                inputParams.getAdSelectionFromOutcomesConfig();
        try {
            ListenableFuture<Void> filterAndValidateRequestFuture =
                    Futures.submit(
                            () -> {
                                try {
                                    Trace.beginSection(Tracing.VALIDATE_REQUEST);
                                    LogUtil.v("Starting filtering and validation.");
                                    mFledgeServiceFilter.filterRequest(
                                            adSelectionFromOutcomesConfig.getSeller(),
                                            inputParams.getCallerPackageName(),
                                            mFlags
                                                    .getEnforceForegroundStatusForFledgeRunAdSelection(),
                                            true,
                                            mCallerUid,
                                            AdServicesStatsLog
                                                    .AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN,
                                            Throttler.ApiKey.FLEDGE_API_SELECT_ADS);
                                    validateAdSelectionFromOutcomesConfig(inputParams);
                                } finally {
                                    LogUtil.v("Completed filtering and validation.");
                                    Trace.endSection();
                                }
                            },
                            mLightweightExecutorService);

            ListenableFuture<AdSelectionOutcome> adSelectionOutcomeFuture =
                    FluentFuture.from(filterAndValidateRequestFuture)
                            .transformAsync(
                                    ignoredVoid ->
                                            orchestrateOutcomeSelection(
                                                    inputParams.getAdSelectionFromOutcomesConfig(),
                                                    inputParams.getCallerPackageName()),
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
            @NonNull AdSelectionFromOutcomesConfig config, @NonNull String callerPackageName) {
        FluentFuture<List<AdSelectionIdWithBidAndRenderUri>> outcomeIdBidPairsFuture =
                FluentFuture.from(
                        retrieveAdSelectionIdWithBidList(
                                config.getAdSelectionIds(), callerPackageName));

        FluentFuture<Long> selectedAdSelectionIdFuture =
                outcomeIdBidPairsFuture.transformAsync(
                        outcomeIdBids ->
                                mAdOutcomeSelector.runAdOutcomeSelector(outcomeIdBids, config),
                        mLightweightExecutorService);

        return selectedAdSelectionIdFuture
                .transformAsync(
                        selectedId ->
                                (selectedId != null)
                                        ? convertAdSelectionIdToAdSelectionOutcome(
                                                outcomeIdBidPairsFuture, selectedId)
                                        : Futures.immediateFuture(null),
                        mLightweightExecutorService)
                .withTimeout(
                        mFlags.getAdSelectionFromOutcomesOverallTimeoutMs(),
                        TimeUnit.MILLISECONDS,
                        mScheduledExecutor)
                .catching(
                        TimeoutException.class,
                        this::handleTimeoutError,
                        mLightweightExecutorService);
    }

    @Nullable
    private AdSelectionOutcome handleTimeoutError(TimeoutException e) {
        LogUtil.e(e, "Ad Selection exceeded time limit");
        throw new UncheckedTimeoutException(AD_SELECTION_TIMED_OUT);
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
            resultCode = AdServicesStatusUtils.STATUS_SUCCESS;
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
            // TODO(b/259522822): Determine what is an appropriate empty response for revoked
            //  consent for selectAdsFromOutcomes
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

    /** Retrieves winner ad bids using ad selection ids of already run ad selections' outcomes. */
    private ListenableFuture<List<AdSelectionIdWithBidAndRenderUri>>
            retrieveAdSelectionIdWithBidList(List<Long> adOutcomeIds, String callerPackageName) {
        List<AdSelectionIdWithBidAndRenderUri> adSelectionIdWithBidAndRenderUriList =
                new ArrayList<>();
        return mBackgroundExecutorService.submit(
                () -> {
                    mAdSelectionEntryDao
                            .getAdSelectionEntities(adOutcomeIds, callerPackageName)
                            .parallelStream()
                            .forEach(
                                    e ->
                                            adSelectionIdWithBidAndRenderUriList.add(
                                                    AdSelectionIdWithBidAndRenderUri.builder()
                                                            .setAdSelectionId(e.getAdSelectionId())
                                                            .setBid(e.getWinningAdBid())
                                                            .setRenderUri(e.getWinningAdRenderUri())
                                                            .build()));
                    return adSelectionIdWithBidAndRenderUriList;
                });
    }

    /** Retrieves winner ad bids using ad selection ids of already run ad selections' outcomes. */
    private ListenableFuture<AdSelectionOutcome> convertAdSelectionIdToAdSelectionOutcome(
            FluentFuture<List<AdSelectionIdWithBidAndRenderUri>>
                    adSelectionIdWithBidAndRenderUrisFuture,
            Long adSelectionId) {
        return adSelectionIdWithBidAndRenderUrisFuture.transformAsync(
                idWithBidAndUris -> {
                    LogUtil.i(
                            "Converting ad selection id: <%s> to AdSelectionOutcome.",
                            adSelectionId);
                    return idWithBidAndUris.stream()
                            .filter(e -> Objects.equals(e.getAdSelectionId(), adSelectionId))
                            .findFirst()
                            .map(
                                    e ->
                                            Futures.immediateFuture(
                                                    new AdSelectionOutcome.Builder()
                                                            .setAdSelectionId(e.getAdSelectionId())
                                                            .setRenderUri(e.getRenderUri())
                                                            .build()))
                            .orElse(
                                    Futures.immediateFailedFuture(
                                            new IllegalStateException(
                                                    SELECTED_OUTCOME_MUST_BE_ONE_OF_THE_INPUTS)));
                },
                mLightweightExecutorService);
    }
    /**
     * Validates the {@link AdSelectionFromOutcomesInput} from the request.
     *
     * @param inputParams the adSelectionConfig to be validated
     * @throws IllegalArgumentException if the provided {@code adSelectionConfig} is not valid
     */
    private void validateAdSelectionFromOutcomesConfig(
            @NonNull AdSelectionFromOutcomesInput inputParams) throws IllegalArgumentException {
        Objects.requireNonNull(inputParams);

        AdSelectionFromOutcomesConfigValidator validator =
                new AdSelectionFromOutcomesConfigValidator(
                        mAdSelectionEntryDao, inputParams.getCallerPackageName());
        validator.validate(inputParams.getAdSelectionFromOutcomesConfig());
    }

}
