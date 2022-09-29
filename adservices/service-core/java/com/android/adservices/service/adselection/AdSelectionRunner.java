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

import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_SELECT_ADS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS;

import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionInput;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.FledgeErrorResponse;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.Uri;
import android.os.LimitExceededException;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.adselection.DBBuyerDecisionLogic;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.AppImportanceFilter.WrongCallingApplicationStateException;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.UncheckedTimeoutException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Orchestrator that runs the Ads Auction/Bidding and Scoring logic The class expects the caller to
 * create a concrete object instance of the class. The instances are mutually exclusive and do not
 * share any values across shared class instance.
 *
 * <p>Class takes in an executor on which it runs the AdSelection logic
 */
public abstract class AdSelectionRunner {

    @VisibleForTesting static final String AD_SELECTION_ERROR_PATTERN = "%s: %s";

    @VisibleForTesting
    static final String ERROR_AD_SELECTION_FAILURE = "Encountered failure during Ad Selection";

    @VisibleForTesting static final String ERROR_NO_WINNING_AD_FOUND = "No winning Ads found";

    @VisibleForTesting
    static final String ERROR_NO_VALID_BIDS_FOR_SCORING = "No valid bids for scoring";

    @VisibleForTesting static final String ERROR_NO_CA_AVAILABLE = "No Custom Audience available";

    @VisibleForTesting
    static final String ERROR_NO_BUYERS_AVAILABLE =
            "The list of the custom audience buyers should not be empty.";

    @VisibleForTesting
    static final String AD_SELECTION_TIMED_OUT = "Ad selection exceeded allowed time limit";

    @VisibleForTesting
    static final String AD_SELECTION_THROTTLED = "Ad selection exceeded allowed rate limit";

    public static final long DAY_IN_SECONDS = 60 * 60 * 24;

    @NonNull protected final Context mContext;
    @NonNull protected final CustomAudienceDao mCustomAudienceDao;
    @NonNull protected final AdSelectionEntryDao mAdSelectionEntryDao;
    @NonNull protected final ListeningExecutorService mLightweightExecutorService;
    @NonNull protected final ListeningExecutorService mBackgroundExecutorService;
    @NonNull protected final AdSelectionIdGenerator mAdSelectionIdGenerator;
    @NonNull protected final Clock mClock;
    @NonNull protected final ConsentManager mConsentManager;
    @NonNull protected final AdServicesLogger mAdServicesLogger;
    @NonNull protected final Flags mFlags;
    @NonNull protected final AppImportanceFilter mAppImportanceFilter;
    @NonNull protected final Supplier<Throttler> mThrottlerSupplier;
    @NonNull protected final FledgeAuthorizationFilter mFledgeAuthorizationFilter;
    @NonNull protected final FledgeAllowListsFilter mFledgeAllowListsFilter;
    protected final int mCallerUid;

    public AdSelectionRunner(
            @NonNull final Context context,
            @NonNull final CustomAudienceDao customAudienceDao,
            @NonNull final AdSelectionEntryDao adSelectionEntryDao,
            @NonNull final ExecutorService lightweightExecutorService,
            @NonNull final ExecutorService backgroundExecutorService,
            @NonNull final ConsentManager consentManager,
            @NonNull final AdServicesLogger adServicesLogger,
            @NonNull final DevContext devContext,
            @NonNull AppImportanceFilter appImportanceFilter,
            @NonNull final Flags flags,
            @NonNull final Supplier<Throttler> throttlerSupplier,
            int callerUid,
            @NonNull final FledgeAuthorizationFilter fledgeAuthorizationFilter,
            @NonNull final FledgeAllowListsFilter fledgeAllowListsFilter) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(consentManager);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(devContext);
        Objects.requireNonNull(appImportanceFilter);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(throttlerSupplier);
        Objects.requireNonNull(fledgeAuthorizationFilter);
        Objects.requireNonNull(fledgeAllowListsFilter);
        mContext = context;
        mCustomAudienceDao = customAudienceDao;
        mAdSelectionEntryDao = adSelectionEntryDao;
        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutorService);
        mBackgroundExecutorService = MoreExecutors.listeningDecorator(backgroundExecutorService);
        mConsentManager = consentManager;
        mAdServicesLogger = adServicesLogger;
        mAdSelectionIdGenerator = new AdSelectionIdGenerator();
        mClock = Clock.systemUTC();
        mFlags = flags;
        mThrottlerSupplier = throttlerSupplier;
        mAppImportanceFilter = appImportanceFilter;
        mCallerUid = callerUid;
        mFledgeAuthorizationFilter = fledgeAuthorizationFilter;
        mFledgeAllowListsFilter = fledgeAllowListsFilter;
    }

    @VisibleForTesting
    AdSelectionRunner(
            @NonNull final Context context,
            @NonNull final CustomAudienceDao customAudienceDao,
            @NonNull final AdSelectionEntryDao adSelectionEntryDao,
            @NonNull final ExecutorService lightweightExecutorService,
            @NonNull final ExecutorService backgroundExecutorService,
            @NonNull final ConsentManager consentManager,
            @NonNull final AdSelectionIdGenerator adSelectionIdGenerator,
            @NonNull Clock clock,
            @NonNull final AdServicesLogger adServicesLogger,
            @NonNull AppImportanceFilter appImportanceFilter,
            @NonNull final Flags flags,
            @NonNull final Supplier<Throttler> throttlerSupplier,
            int callerUid,
            @NonNull final FledgeAuthorizationFilter fledgeAuthorizationFilter,
            @NonNull final FledgeAllowListsFilter fledgeAllowListsFilter) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(consentManager);
        Objects.requireNonNull(adSelectionIdGenerator);
        Objects.requireNonNull(clock);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(appImportanceFilter);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(fledgeAuthorizationFilter);

        mContext = context;
        mCustomAudienceDao = customAudienceDao;
        mAdSelectionEntryDao = adSelectionEntryDao;
        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutorService);
        mBackgroundExecutorService = MoreExecutors.listeningDecorator(backgroundExecutorService);
        mConsentManager = consentManager;
        mAdSelectionIdGenerator = adSelectionIdGenerator;
        mClock = clock;
        mAdServicesLogger = adServicesLogger;
        mFlags = flags;
        mThrottlerSupplier = throttlerSupplier;
        mAppImportanceFilter = appImportanceFilter;
        mCallerUid = callerUid;
        mFledgeAuthorizationFilter = fledgeAuthorizationFilter;
        mFledgeAllowListsFilter = fledgeAllowListsFilter;
    }

    /**
     * Runs the ad selection for a given seller
     *
     * @param inputParams containing {@link AdSelectionConfig} and {@code callerPackageName}
     * @param callback used to notify the result back to the calling seller
     */
    public void runAdSelection(
            @NonNull AdSelectionInput inputParams, @NonNull AdSelectionCallback callback) {
        Objects.requireNonNull(inputParams);
        Objects.requireNonNull(callback);

        try {
            ListenableFuture<Void> validateRequestFuture =
                    Futures.submit(
                            () ->
                                    validateRequest(
                                            inputParams.getAdSelectionConfig(),
                                            inputParams.getCallerPackageName()),
                            mLightweightExecutorService);

            ListenableFuture<DBAdSelection> dbAdSelectionFuture =
                    FluentFuture.from(validateRequestFuture)
                            .transformAsync(
                                    ignoredVoid ->
                                            orchestrateAdSelection(
                                                    inputParams.getAdSelectionConfig(),
                                                    inputParams.getCallerPackageName()),
                                    mLightweightExecutorService);

            Futures.addCallback(
                    dbAdSelectionFuture,
                    new FutureCallback<DBAdSelection>() {
                        @Override
                        public void onSuccess(DBAdSelection result) {
                            notifySuccessToCaller(result, callback);
                            // TODO(242280808): Schedule a clear for stale data instead of this hack
                            clearExpiredAdSelectionData();
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            if (t instanceof ConsentManager.RevokedConsentException) {
                                notifyEmptySuccessToCaller(
                                        callback,
                                        AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED);
                            } else {
                                notifyFailureToCaller(callback, t);
                            }
                            // TODO(242280808): Schedule a clear for stale data instead of this hack
                            clearExpiredAdSelectionData();
                        }
                    },
                    mLightweightExecutorService);
        } catch (Throwable t) {
            notifyFailureToCaller(callback, t);
        }
    }

    private void notifySuccessToCaller(
            @NonNull DBAdSelection result, @NonNull AdSelectionCallback callback) {
        int resultCode = AdServicesStatusUtils.STATUS_UNSET;
        try {
            callback.onSuccess(
                    new AdSelectionResponse.Builder()
                            .setAdSelectionId(result.getAdSelectionId())
                            .setRenderUri(result.getWinningAdRenderUri())
                            .build());
            resultCode = AdServicesStatusUtils.STATUS_SUCCESS;
        } catch (RemoteException e) {
            LogUtil.e(e, "Encountered exception during notifying AdSelection callback");
            resultCode = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
        } finally {
            LogUtil.v(
                    "Ad Selection with Id:%d completed, attempted notifying success",
                    result.getAdSelectionId());
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS, resultCode);
        }
    }

    /** Sends a successful response to the caller that represents a silent failure. */
    private void notifyEmptySuccessToCaller(@NonNull AdSelectionCallback callback, int resultCode) {
        try {
            callback.onSuccess(
                    new AdSelectionResponse.Builder()
                            .setAdSelectionId(mAdSelectionIdGenerator.generateId())
                            .setRenderUri(Uri.EMPTY)
                            .build());
        } catch (RemoteException e) {
            LogUtil.e(e, "Encountered exception during notifying AdSelection callback");
            resultCode = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
        } finally {
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS, resultCode);
        }
    }

    private void notifyFailureToCaller(
            @NonNull AdSelectionCallback callback, @NonNull Throwable t) {
        int resultCode = AdServicesStatusUtils.STATUS_UNSET;
        try {
            if (t instanceof WrongCallingApplicationStateException) {
                resultCode = AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
            } else if (t instanceof UncheckedTimeoutException) {
                resultCode = AdServicesStatusUtils.STATUS_TIMEOUT;
            } else if (t instanceof FledgeAuthorizationFilter.AdTechNotAllowedException
                    || t instanceof FledgeAllowListsFilter.AppNotAllowedException) {
                resultCode = AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
            } else if (t instanceof FledgeAuthorizationFilter.CallerMismatchException) {
                resultCode = AdServicesStatusUtils.STATUS_UNAUTHORIZED;
            } else if (t instanceof IllegalArgumentException) {
                resultCode = AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
            } else if (t instanceof LimitExceededException) {
                resultCode = AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
            } else {
                resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
            }

            FledgeErrorResponse selectionFailureResponse =
                    new FledgeErrorResponse.Builder()
                            .setErrorMessage(
                                    String.format(
                                            AD_SELECTION_ERROR_PATTERN,
                                            ERROR_AD_SELECTION_FAILURE,
                                            t.getMessage()))
                            .setStatusCode(resultCode)
                            .build();
            LogUtil.e(t, "Ad Selection failure: ");
            callback.onFailure(selectionFailureResponse);
        } catch (RemoteException e) {
            LogUtil.e(e, "Encountered exception during notifying AdSelection callback");
            resultCode = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
        } finally {
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS, resultCode);
        }
    }

    /**
     * Overall moderator for running Ad Selection
     *
     * @param adSelectionConfig Set of data from Sellers and Buyers needed for Ad Auction and
     *     Selection
     * @return {@link AdSelectionResponse}
     */
    private ListenableFuture<DBAdSelection> orchestrateAdSelection(
            @NonNull final AdSelectionConfig adSelectionConfig,
            @NonNull final String callerPackageName) {
        LogUtil.v("Beginning Ad Selection Orchestration");

        ListenableFuture<List<DBCustomAudience>> buyerCustomAudience =
                getBuyersCustomAudience(adSelectionConfig);
        ListenableFuture<AdSelectionOrchestrationResult> dbAdSelection =
                orchestrateAdSelection(adSelectionConfig, callerPackageName, buyerCustomAudience);

        AsyncFunction<AdSelectionOrchestrationResult, DBAdSelection> saveResultToPersistence =
                adSelectionAndJs -> {
                    return persistAdSelection(
                            adSelectionAndJs.mDbAdSelectionBuilder,
                            adSelectionAndJs.mBuyerDecisionLogicJs,
                            callerPackageName);
                };

        return FluentFuture.from(dbAdSelection)
                .transformAsync(saveResultToPersistence, mLightweightExecutorService)
                .withTimeout(
                        mFlags.getAdSelectionOverallTimeoutMs(),
                        TimeUnit.MILLISECONDS,
                        // TODO(b/237103033): Comply with thread usage policy for AdServices;
                        //  use a global scheduled executor
                        new ScheduledThreadPoolExecutor(1))
                .catching(
                        TimeoutException.class,
                        this::handleTimeoutError,
                        mLightweightExecutorService);
    }

    abstract ListenableFuture<AdSelectionOrchestrationResult> orchestrateAdSelection(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull String callerPackageName,
            @NonNull ListenableFuture<List<DBCustomAudience>> buyerCustomAudience);

    @Nullable
    private DBAdSelection handleTimeoutError(TimeoutException e) {
        LogUtil.e(e, "Ad Selection exceeded time limit");
        throw new UncheckedTimeoutException(AD_SELECTION_TIMED_OUT);
    }

    private ListenableFuture<List<DBCustomAudience>> getBuyersCustomAudience(
            final AdSelectionConfig adSelectionConfig) {
        return mBackgroundExecutorService.submit(
                () -> {
                    Preconditions.checkArgument(
                            !adSelectionConfig.getCustomAudienceBuyers().isEmpty(),
                            ERROR_NO_BUYERS_AVAILABLE);
                    List<DBCustomAudience> buyerCustomAudience =
                            mCustomAudienceDao.getActiveCustomAudienceByBuyers(
                                    adSelectionConfig.getCustomAudienceBuyers(),
                                    mClock.instant(),
                                    mFlags.getFledgeCustomAudienceActiveTimeWindowInMs());
                    if (buyerCustomAudience == null || buyerCustomAudience.isEmpty()) {
                        // TODO(b/233296309) : Remove this exception after adding contextual
                        // ads
                        throw new IllegalStateException(ERROR_NO_CA_AVAILABLE);
                    }
                    return buyerCustomAudience;
                });
    }

    private ListenableFuture<DBAdSelection> persistAdSelection(
            @NonNull DBAdSelection.Builder dbAdSelectionBuilder,
            @NonNull String buyerDecisionLogicJS,
            @NonNull String callerPackageName) {
        final long adSelectionId = mAdSelectionIdGenerator.generateId();
        LogUtil.v("Persisting Ad Selection Result for Id:%d", adSelectionId);
        return mBackgroundExecutorService.submit(
                () -> {
                    // TODO : b/230568647 retry ID generation in case of collision
                    DBAdSelection dbAdSelection;
                    dbAdSelectionBuilder
                            .setAdSelectionId(adSelectionId)
                            .setCreationTimestamp(mClock.instant())
                            .setCallerPackageName(callerPackageName);
                    dbAdSelection = dbAdSelectionBuilder.build();
                    mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
                    mAdSelectionEntryDao.persistBuyerDecisionLogic(
                            new DBBuyerDecisionLogic.Builder()
                                    .setBuyerDecisionLogicJs(buyerDecisionLogicJS)
                                    .setBiddingLogicUri(dbAdSelection.getBiddingLogicUri())
                                    .build());
                    return dbAdSelection;
                });
    }

    /**
     * Asserts that FLEDGE APIs and the Privacy Sandbox as a whole have user consent.
     *
     * @return an ignorable {@code null}
     * @throws ConsentManager.RevokedConsentException if FLEDGE or the Privacy Sandbox do not have
     *     user consent
     */
    private Void assertCallerHasUserConsent() throws ConsentManager.RevokedConsentException {
        if (!mConsentManager.getConsent(mContext.getPackageManager()).isGiven()) {
            throw new ConsentManager.RevokedConsentException();
        }
        return null;
    }

    /**
     * Asserts that the caller has the appropriate foreground status, if enabled.
     *
     * @return an ignorable {@code null}
     * @throws WrongCallingApplicationStateException if the foreground check is enabled and fails
     */
    private Void maybeAssertForegroundCaller() throws WrongCallingApplicationStateException {
        if (mFlags.getEnforceForegroundStatusForFledgeRunAdSelection()) {
            mAppImportanceFilter.assertCallerIsInForeground(
                    mCallerUid, AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS, null);
        }
        return null;
    }

    /**
     * Asserts that the package name provided by the caller is one of the packages of the calling
     * uid.
     *
     * @param callerPackageName caller package name from the request
     * @throws FledgeAuthorizationFilter.CallerMismatchException if the provided {@code
     *     callerPackageName} is not valid
     * @return an ignorable {@code null}
     */
    private Void assertCallerPackageName(String callerPackageName)
            throws FledgeAuthorizationFilter.CallerMismatchException {
        mFledgeAuthorizationFilter.assertCallingPackageName(
                callerPackageName, mCallerUid, AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS);
        return null;
    }

    /**
     * Validates the {@code adSelectionConfig} from the request.
     *
     * @param adSelectionConfig the adSelectionConfig to be validated
     * @throws IllegalArgumentException if the provided {@code adSelectionConfig} is not valid
     * @return an ignorable {@code null}
     */
    private Void validateAdSelectionConfig(AdSelectionConfig adSelectionConfig)
            throws IllegalArgumentException {
        AdSelectionConfigValidator adSelectionConfigValidator = new AdSelectionConfigValidator();
        adSelectionConfigValidator.validate(adSelectionConfig);

        return null;
    }

    /**
     * Check if a certain ad tech is enrolled and authorized to perform the operation for the
     * package.
     *
     * @param callerPackageName the package name to check against
     * @param adSelectionConfig contains the ad tech to check against
     * @throws FledgeAuthorizationFilter.AdTechNotAllowedException if the ad tech is not authorized
     *     to perform the operation
     */
    private Void assertFledgeEnrollment(
            AdSelectionConfig adSelectionConfig, String callerPackageName)
            throws FledgeAuthorizationFilter.AdTechNotAllowedException {
        if (!mFlags.getDisableFledgeEnrollmentCheck()) {
            mFledgeAuthorizationFilter.assertAdTechAllowed(
                    mContext,
                    callerPackageName,
                    adSelectionConfig.getSeller(),
                    AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS);
        }

        return null;
    }

    /**
     * Asserts the package is allowed to call PPAPI.
     *
     * @param callerPackageName the package name to be validated.
     * @throws FledgeAllowListsFilter.AppNotAllowedException if the package is not authorized.
     */
    private Void assertAppInAllowList(String callerPackageName)
            throws FledgeAllowListsFilter.AppNotAllowedException {
        mFledgeAllowListsFilter.assertAppCanUsePpapi(
                callerPackageName, AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS);

        return null;
    }

    /**
     * Ensures that the caller package is not throttled from calling the current API
     *
     * @param callerPackageName the package name, which should be verified
     * @throws LimitExceededException if the provided {@code callerPackageName} exceeds its rate
     *     limits
     * @return an ignorable {@code null}
     */
    private Void assertCallerNotThrottled(final String callerPackageName)
            throws LimitExceededException {
        LogUtil.v("Checking if API is throttled for package: %s ", callerPackageName);
        Throttler throttler = mThrottlerSupplier.get();
        boolean isThrottled = !throttler.tryAcquire(FLEDGE_API_SELECT_ADS, callerPackageName);

        if (isThrottled) {
            LogUtil.e("Rate Limit Reached for API: %s", FLEDGE_API_SELECT_ADS);
            throw new LimitExceededException(AD_SELECTION_THROTTLED);
        }
        return null;
    }

    /**
     * Validates the {@code runAdSelection} request.
     *
     * @param adSelectionConfig the adSelectionConfig to be validated
     * @param callerPackageName caller package name to be validated
     * @throws FledgeAuthorizationFilter.CallerMismatchException if the {@code callerPackageName} is
     *     not valid
     * @throws WrongCallingApplicationStateException if the foreground check is enabled and fails
     * @throws FledgeAuthorizationFilter.AdTechNotAllowedException if the ad tech is not authorized
     *     to perform the operation
     * @throws FledgeAllowListsFilter.AppNotAllowedException if the package is not authorized.
     * @throws ConsentManager.RevokedConsentException if FLEDGE or the Privacy Sandbox do not have
     *     user consent
     * @throws LimitExceededException if the provided {@code callerPackageName} exceeds the rate
     *     limits
     * @throws IllegalArgumentException if the provided {@code adSelectionConfig} is not valid
     * @return an ignorable {@code null}
     */
    private Void validateRequest(AdSelectionConfig adSelectionConfig, String callerPackageName) {
        assertCallerPackageName(callerPackageName);
        assertCallerNotThrottled(callerPackageName);
        maybeAssertForegroundCaller();
        assertFledgeEnrollment(adSelectionConfig, callerPackageName);
        assertAppInAllowList(callerPackageName);
        assertCallerHasUserConsent();
        validateAdSelectionConfig(adSelectionConfig);

        return null;
    }

    private void clearExpiredAdSelectionData() {
        Instant expirationTime = mClock.instant().minusSeconds(DAY_IN_SECONDS);
        mAdSelectionEntryDao.removeExpiredAdSelection(expirationTime);
    }

    static class AdSelectionOrchestrationResult {
        DBAdSelection.Builder mDbAdSelectionBuilder;
        String mBuyerDecisionLogicJs;

        AdSelectionOrchestrationResult(
                DBAdSelection.Builder dbAdSelectionBuilder, String buyerDecisionLogicJs) {
            this.mDbAdSelectionBuilder = dbAdSelectionBuilder;
            this.mBuyerDecisionLogicJs = buyerDecisionLogicJs;
        }
    }
}
