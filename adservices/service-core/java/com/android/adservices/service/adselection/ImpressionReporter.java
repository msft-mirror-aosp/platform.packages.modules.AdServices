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

import static android.adservices.adselection.ReportInteractionRequest.FLAG_REPORTING_DESTINATION_BUYER;
import static android.adservices.adselection.ReportInteractionRequest.FLAG_REPORTING_DESTINATION_SELLER;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.ReportImpressionCallback;
import android.adservices.adselection.ReportImpressionInput;
import android.adservices.adselection.ReportInteractionRequest;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.FledgeErrorResponse;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.Uri;
import android.os.LimitExceededException;
import android.os.RemoteException;
import android.os.Trace;
import android.util.Pair;

import com.android.adservices.LogUtil;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.DBAdSelectionEntry;
import com.android.adservices.data.adselection.DBRegisteredAdInteraction;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.AppImportanceFilter.WrongCallingApplicationStateException;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.FledgeServiceFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.ValidatorUtil;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.AdSelectionDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.profiling.Tracing;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.internal.util.Preconditions;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Encapsulates the Impression Reporting logic */
public class ImpressionReporter {
    public static final String UNABLE_TO_FIND_AD_SELECTION_WITH_GIVEN_ID =
            "Unable to find ad selection with given ID";
    public static final String CALLER_PACKAGE_NAME_MISMATCH =
            "Caller package name does not match name used in ad selection";

    private static final String REPORTING_URI_FIELD_NAME = "reporting URI";
    private static final String EVENT_URI_FIELD_NAME = "event URI";

    @NonNull private final AdSelectionEntryDao mAdSelectionEntryDao;
    @NonNull private final AdServicesHttpsClient mAdServicesHttpsClient;
    @NonNull private final ListeningExecutorService mLightweightExecutorService;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;
    @NonNull private final ScheduledThreadPoolExecutor mScheduledExecutor;
    @NonNull private final ReportImpressionScriptEngine mJsEngine;
    @NonNull private final AdSelectionDevOverridesHelper mAdSelectionDevOverridesHelper;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final Flags mFlags;
    @NonNull private final FledgeServiceFilter mFledgeServiceFilter;
    private int mCallerUid;

    public ImpressionReporter(
            @NonNull Context context,
            @NonNull ExecutorService lightweightExecutor,
            @NonNull ExecutorService backgroundExecutor,
            @NonNull ScheduledThreadPoolExecutor scheduledExecutor,
            @NonNull AdSelectionEntryDao adSelectionEntryDao,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull DevContext devContext,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull final Flags flags,
            @NonNull final FledgeServiceFilter fledgeServiceFilter,
            final int callerUid) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(lightweightExecutor);
        Objects.requireNonNull(backgroundExecutor);
        Objects.requireNonNull(scheduledExecutor);
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(adServicesHttpsClient);
        Objects.requireNonNull(devContext);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(fledgeServiceFilter);

        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutor);
        mBackgroundExecutorService = MoreExecutors.listeningDecorator(backgroundExecutor);
        mScheduledExecutor = scheduledExecutor;
        mAdSelectionEntryDao = adSelectionEntryDao;
        mAdServicesHttpsClient = adServicesHttpsClient;
        mJsEngine =
                new ReportImpressionScriptEngine(
                        context,
                        () -> flags.getEnforceIsolateMaxHeapSize(),
                        () -> flags.getIsolateMaxHeapSizeBytes());
        mAdSelectionDevOverridesHelper =
                new AdSelectionDevOverridesHelper(devContext, mAdSelectionEntryDao);
        mAdServicesLogger = adServicesLogger;
        mFlags = flags;
        mFledgeServiceFilter = fledgeServiceFilter;
        mCallerUid = callerUid;
    }

    @VisibleForTesting
    public ImpressionReporter(
            @NonNull Context context,
            @NonNull ExecutorService lightweightExecutor,
            @NonNull ExecutorService backgroundExecutor,
            @NonNull ScheduledThreadPoolExecutor scheduledExecutor,
            @NonNull AdSelectionEntryDao adSelectionEntryDao,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull ConsentManager consentManager,
            @NonNull DevContext devContext,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull AppImportanceFilter appImportanceFilter,
            @NonNull final Flags flags,
            @NonNull final Supplier<Throttler> throttlerSupplier,
            int callerUid,
            @NonNull FledgeAuthorizationFilter fledgeAuthorizationFilter,
            @NonNull final FledgeAllowListsFilter fledgeAllowListsFilter) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(lightweightExecutor);
        Objects.requireNonNull(backgroundExecutor);
        Objects.requireNonNull(scheduledExecutor);
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(adServicesHttpsClient);
        Objects.requireNonNull(consentManager);
        Objects.requireNonNull(devContext);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(appImportanceFilter);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(throttlerSupplier);
        Objects.requireNonNull(fledgeAuthorizationFilter);
        Objects.requireNonNull(fledgeAllowListsFilter);

        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutor);
        mBackgroundExecutorService = MoreExecutors.listeningDecorator(backgroundExecutor);
        mScheduledExecutor = scheduledExecutor;
        mAdSelectionEntryDao = adSelectionEntryDao;
        mAdServicesHttpsClient = adServicesHttpsClient;
        mJsEngine =
                new ReportImpressionScriptEngine(
                        context,
                        () -> flags.getEnforceIsolateMaxHeapSize(),
                        () -> flags.getIsolateMaxHeapSizeBytes());
        mAdSelectionDevOverridesHelper =
                new AdSelectionDevOverridesHelper(devContext, mAdSelectionEntryDao);
        mAdServicesLogger = adServicesLogger;
        mFlags = flags;
        mFledgeServiceFilter =
                new FledgeServiceFilter(
                        context,
                        consentManager,
                        flags,
                        appImportanceFilter,
                        fledgeAuthorizationFilter,
                        fledgeAllowListsFilter,
                        throttlerSupplier);
    }

    /** Invokes the onFailure function from the callback and handles the exception. */
    private void invokeFailure(
            @NonNull ReportImpressionCallback callback, int statusCode, String errorMessage) {
        int resultCode = AdServicesStatusUtils.STATUS_UNSET;
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(statusCode)
                            .setErrorMessage(errorMessage)
                            .build());
            resultCode = statusCode;
        } catch (RemoteException e) {
            LogUtil.e(e, "Unable to send failed result to the callback");
            resultCode = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
            throw e.rethrowFromSystemServer();
        } finally {
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION, resultCode, 0);
        }
    }

    /** Invokes the onSuccess function from the callback and handles the exception. */
    private void invokeSuccess(@NonNull ReportImpressionCallback callback, int resultCode) {
        try {
            callback.onSuccess();
        } catch (RemoteException e) {
            LogUtil.e(e, "Unable to send successful result to the callback");
            resultCode = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
            throw e.rethrowFromSystemServer();
        } finally {
            // TODO(b/233681870): Investigate implementation of actual failures in
            //  logs/metrics
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION, resultCode, 0);
        }
    }

    /**
     * Run the impression report logic asynchronously. Invoked seller's reportResult() as well as
     * the buyer's reportWin() in the case of a remarketing ad.
     *
     * <p>After invoking the javascript functions, invokes the onSuccess function of the callback
     * and reports URIs resulting from the javascript functions.
     *
     * @param requestParams request parameters containing the {@code adSelectionId}, {@code
     *     adSelectionConfig}, and {@code callerPackageName}
     * @param callback callback function to be called in case of success or failure
     */
    public void reportImpression(
            @NonNull ReportImpressionInput requestParams,
            @NonNull ReportImpressionCallback callback) {
        LogUtil.v("Executing reportImpression API");
        // Getting PH flags in a non binder thread
        FluentFuture<Long> timeoutFuture =
                FluentFuture.from(
                        mLightweightExecutorService.submit(
                                mFlags::getReportImpressionOverallTimeoutMs));

        timeoutFuture.addCallback(
                new FutureCallback<Long>() {
                    @Override
                    public void onSuccess(Long timeout) {
                        invokeReporting(requestParams, callback);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(t, "Report Impression failed!");
                        notifyFailureToCaller(callback, t);
                    }
                },
                mLightweightExecutorService);
    }

    private void invokeReporting(
            @NonNull ReportImpressionInput requestParams,
            @NonNull ReportImpressionCallback callback) {
        long adSelectionId = requestParams.getAdSelectionId();
        AdSelectionConfig adSelectionConfig = requestParams.getAdSelectionConfig();
        ListenableFuture<Void> filterAndValidateRequestFuture =
                Futures.submit(
                        () -> {
                            try {
                                Trace.beginSection(Tracing.VALIDATE_REQUEST);
                                LogUtil.v("Starting filtering and validation.");
                                mFledgeServiceFilter.filterRequest(
                                        adSelectionConfig.getSeller(),
                                        requestParams.getCallerPackageName(),
                                        mFlags
                                                .getEnforceForegroundStatusForFledgeReportImpression(),
                                        mCallerUid,
                                        AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                                        Throttler.ApiKey.FLEDGE_API_REPORT_IMPRESSIONS);
                                validateAdSelectionConfig(adSelectionConfig);
                            } finally {
                                LogUtil.v("Completed filtering and validation.");
                                Trace.endSection();
                            }
                        },
                        mLightweightExecutorService);

        FluentFuture.from(filterAndValidateRequestFuture)
                .transformAsync(
                        ignoredVoid ->
                                computeReportingUris(
                                        adSelectionId,
                                        adSelectionConfig,
                                        requestParams.getCallerPackageName()),
                        mLightweightExecutorService)
                .transform(
                        reportingUrisAndContext ->
                                notifySuccessToCaller(
                                        callback,
                                        reportingUrisAndContext.first,
                                        reportingUrisAndContext.second),
                        mLightweightExecutorService)
                .withTimeout(
                        mFlags.getReportImpressionOverallTimeoutMs(),
                        TimeUnit.MILLISECONDS,
                        // TODO(b/237103033): Comply with thread usage policy for AdServices;
                        //  use a global scheduled executor
                        mScheduledExecutor)
                .transformAsync(
                        reportingUrisAndContext ->
                                doReport(
                                        reportingUrisAndContext.first,
                                        reportingUrisAndContext.second),
                        mLightweightExecutorService)
                .addCallback(
                        new FutureCallback<List<Void>>() {
                            @Override
                            public void onSuccess(List<Void> result) {
                                LogUtil.d("Report impression succeeded!");
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                LogUtil.e(t, "Report Impression invocation failed!");
                                if (t instanceof ConsentManager.RevokedConsentException) {
                                    invokeSuccess(
                                            callback,
                                            AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED);
                                } else {
                                    notifyFailureToCaller(callback, t);
                                }
                            }
                        },
                        mLightweightExecutorService);
    }

    private Pair<ReportingUris, ReportingContext> notifySuccessToCaller(
            @NonNull ReportImpressionCallback callback,
            @NonNull ReportingUris reportingUris,
            @NonNull ReportingContext ctx) {
        invokeSuccess(callback, AdServicesStatusUtils.STATUS_SUCCESS);
        return Pair.create(reportingUris, ctx);
    }

    private void notifyFailureToCaller(
            @NonNull ReportImpressionCallback callback, @NonNull Throwable t) {
        if (t instanceof IllegalArgumentException) {
            invokeFailure(callback, AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, t.getMessage());
        } else if (t instanceof WrongCallingApplicationStateException) {
            invokeFailure(callback, AdServicesStatusUtils.STATUS_BACKGROUND_CALLER, t.getMessage());
        } else if (t instanceof FledgeAuthorizationFilter.AdTechNotAllowedException
                || t instanceof FledgeAllowListsFilter.AppNotAllowedException) {
            invokeFailure(
                    callback, AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED, t.getMessage());
        } else if (t instanceof FledgeAuthorizationFilter.CallerMismatchException) {
            invokeFailure(callback, AdServicesStatusUtils.STATUS_UNAUTHORIZED, t.getMessage());
        } else if (t instanceof LimitExceededException) {
            invokeFailure(
                    callback, AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED, t.getMessage());
        } else {
            invokeFailure(callback, AdServicesStatusUtils.STATUS_INTERNAL_ERROR, t.getMessage());
        }
    }

    @NonNull
    private ListenableFuture<List<Void>> doReport(
            ReportingUris reportingUris, ReportingContext ctx) {
        LogUtil.v("Reporting URIs");

        ListenableFuture<Void> sellerFuture;

        // Validate seller uri before reporting
        AdTechUriValidator sellerValidator =
                new AdTechUriValidator(
                        ValidatorUtil.AD_TECH_ROLE_SELLER,
                        ctx.mAdSelectionConfig.getSeller().toString(),
                        this.getClass().getSimpleName(),
                        REPORTING_URI_FIELD_NAME);
        try {
            sellerValidator.validate(reportingUris.sellerReportingUri);
            // Perform reporting if no exception was thrown
            sellerFuture =
                    mAdServicesHttpsClient.getAndReadNothing(reportingUris.sellerReportingUri);
        } catch (IllegalArgumentException e) {
            LogUtil.v("Seller reporting URI validation failed!");
            sellerFuture = Futures.immediateFuture(null);
        }

        ListenableFuture<Void> buyerFuture;

        // Validate buyer uri if it exists
        if (!Objects.isNull(reportingUris.buyerReportingUri)) {
            CustomAudienceSignals customAudienceSignals =
                    Objects.requireNonNull(ctx.mDBAdSelectionEntry.getCustomAudienceSignals());

            AdTechUriValidator buyerValidator =
                    new AdTechUriValidator(
                            ValidatorUtil.AD_TECH_ROLE_BUYER,
                            customAudienceSignals.getBuyer().toString(),
                            this.getClass().getSimpleName(),
                            REPORTING_URI_FIELD_NAME);
            try {
                buyerValidator.validate(reportingUris.buyerReportingUri);
                // Perform reporting if no exception was thrown
                buyerFuture =
                        mAdServicesHttpsClient.getAndReadNothing(reportingUris.buyerReportingUri);
            } catch (IllegalArgumentException e) {
                LogUtil.v("Buyer reporting URI validation failed!");
                buyerFuture = Futures.immediateFuture(null);
            }
        } else {
            // In case of contextual ad
            buyerFuture = Futures.immediateFuture(null);
        }

        return Futures.allAsList(sellerFuture, buyerFuture);
    }

    private FluentFuture<Pair<ReportingUris, ReportingContext>> computeReportingUris(
            long adSelectionId, AdSelectionConfig adSelectionConfig, String callerPackageName) {
        return fetchAdSelectionEntry(adSelectionId, callerPackageName)
                .transformAsync(
                        dbAdSelectionEntry -> {
                            ReportingContext ctx = new ReportingContext();
                            ctx.mDBAdSelectionEntry = dbAdSelectionEntry;
                            ctx.mAdSelectionConfig = adSelectionConfig;
                            return fetchSellerDecisionLogic(ctx);
                        },
                        mLightweightExecutorService)
                .transformAsync(
                        decisionLogicJsAndCtx ->
                                invokeSellerScript(
                                        decisionLogicJsAndCtx.first, decisionLogicJsAndCtx.second),
                        mLightweightExecutorService)
                .transformAsync(
                        sellerResultAndCtx ->
                                commitSellerRegisteredEvents(
                                        sellerResultAndCtx.first, sellerResultAndCtx.second),
                        mLightweightExecutorService)
                .transformAsync(
                        sellerResultAndCtx ->
                                invokeBuyerScript(
                                        sellerResultAndCtx.first, sellerResultAndCtx.second),
                        mLightweightExecutorService)
                .transformAsync(
                        reportingResultsAndCtx ->
                                commitBuyerRegisteredEvents(
                                        reportingResultsAndCtx.first,
                                        reportingResultsAndCtx.second),
                        mLightweightExecutorService);
    }

    private FluentFuture<DBAdSelectionEntry> fetchAdSelectionEntry(
            long adSelectionId, String callerPackageName) {
        LogUtil.v(
                "Fetching ad selection entry ID %d for caller \"%s\"",
                adSelectionId, callerPackageName);
        return FluentFuture.from(
                mBackgroundExecutorService.submit(
                        () -> {
                            Preconditions.checkArgument(
                                    mAdSelectionEntryDao.doesAdSelectionIdExist(adSelectionId),
                                    UNABLE_TO_FIND_AD_SELECTION_WITH_GIVEN_ID);
                            Preconditions.checkArgument(
                                    mAdSelectionEntryDao
                                            .doesAdSelectionMatchingCallerPackageNameExist(
                                                    adSelectionId, callerPackageName),
                                    CALLER_PACKAGE_NAME_MISMATCH);
                            return mAdSelectionEntryDao.getAdSelectionEntityById(adSelectionId);
                        }));
    }

    private FluentFuture<Pair<String, ReportingContext>> fetchSellerDecisionLogic(
            ReportingContext ctx) {
        LogUtil.v("Fetching Seller decision logic");
        FluentFuture<String> jsOverrideFuture =
                FluentFuture.from(
                        mBackgroundExecutorService.submit(
                                () ->
                                        mAdSelectionDevOverridesHelper.getDecisionLogicOverride(
                                                ctx.mAdSelectionConfig)));

        return jsOverrideFuture
                .transformAsync(
                        jsOverride -> {
                            if (jsOverride == null) {
                                return FluentFuture.from(
                                                mAdServicesHttpsClient.fetchPayload(
                                                        ctx.mAdSelectionConfig
                                                                .getDecisionLogicUri()))
                                        .transform(
                                                response -> response.getResponseBody(),
                                                mLightweightExecutorService);
                            } else {
                                LogUtil.i(
                                        "Developer options enabled and an override JS is provided "
                                                + "for the current ad selection config. "
                                                + "Skipping call to server.");
                                return Futures.immediateFuture(jsOverride);
                            }
                        },
                        mLightweightExecutorService)
                .transform(
                        stringResult -> Pair.create(stringResult, ctx),
                        mLightweightExecutorService);
    }

    private FluentFuture<Pair<ReportImpressionScriptEngine.SellerReportingResult, ReportingContext>>
            invokeSellerScript(String decisionLogicJs, ReportingContext ctx) {
        LogUtil.v("Invoking seller script");
        try {
            return FluentFuture.from(
                            mJsEngine.reportResult(
                                    decisionLogicJs,
                                    ctx.mAdSelectionConfig,
                                    ctx.mDBAdSelectionEntry.getWinningAdRenderUri(),
                                    ctx.mDBAdSelectionEntry.getWinningAdBid(),
                                    AdSelectionSignals.fromString(
                                            ctx.mDBAdSelectionEntry.getContextualSignals())))
                    .transform(
                            sellerResult -> Pair.create(sellerResult, ctx),
                            mLightweightExecutorService);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid JSON data", e);
        }
    }

    private FluentFuture<Pair<ReportingResults, ReportingContext>> invokeBuyerScript(
            ReportImpressionScriptEngine.SellerReportingResult sellerReportingResult,
            ReportingContext ctx) {
        LogUtil.v("Invoking buyer script");
        final boolean isContextual =
                Objects.isNull(ctx.mDBAdSelectionEntry.getCustomAudienceSignals())
                        && Objects.isNull(ctx.mDBAdSelectionEntry.getBuyerDecisionLogicJs());

        if (isContextual) {
            return FluentFuture.from(
                    Futures.immediateFuture(
                            Pair.create(new ReportingResults(null, sellerReportingResult), ctx)));
        }
        final CustomAudienceSignals customAudienceSignals =
                Objects.requireNonNull(ctx.mDBAdSelectionEntry.getCustomAudienceSignals());
        try {
            return FluentFuture.from(
                            mJsEngine.reportWin(
                                    ctx.mDBAdSelectionEntry.getBuyerDecisionLogicJs(),
                                    ctx.mAdSelectionConfig.getAdSelectionSignals(),
                                    ctx.mAdSelectionConfig
                                            .getPerBuyerSignals()
                                            .get(customAudienceSignals.getBuyer()),
                                    sellerReportingResult.getSignalsForBuyer(),
                                    AdSelectionSignals.fromString(
                                            ctx.mDBAdSelectionEntry.getContextualSignals()),
                                    ctx.mDBAdSelectionEntry.getCustomAudienceSignals()))
                    .transform(
                            buyerReportingResult ->
                                    Pair.create(
                                            new ReportingResults(
                                                    buyerReportingResult, sellerReportingResult),
                                            ctx),
                            mLightweightExecutorService);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid JSON args", e);
        }
    }

    private FluentFuture<Pair<ReportImpressionScriptEngine.SellerReportingResult, ReportingContext>>
            commitSellerRegisteredEvents(
                    ReportImpressionScriptEngine.SellerReportingResult sellerReportingResult,
                    ReportingContext ctx) {
        // Validate seller uri before reporting
        AdTechUriValidator sellerValidator =
                new AdTechUriValidator(
                        ValidatorUtil.AD_TECH_ROLE_SELLER,
                        ctx.mAdSelectionConfig.getSeller().toString(),
                        this.getClass().getSimpleName(),
                        EVENT_URI_FIELD_NAME);

        return FluentFuture.from(
                mBackgroundExecutorService.submit(
                        () -> {
                            commitRegisteredAdEventsToDatabase(
                                    sellerReportingResult.getInteractionReportingUris(),
                                    sellerValidator,
                                    ctx.mDBAdSelectionEntry.getAdSelectionId(),
                                    FLAG_REPORTING_DESTINATION_SELLER);
                            return Pair.create(sellerReportingResult, ctx);
                        }));
    }

    private FluentFuture<Pair<ReportingUris, ReportingContext>> commitBuyerRegisteredEvents(
            ReportingResults reportingResults, ReportingContext ctx) {
        if (Objects.isNull(reportingResults.mBuyerReportingResult)) {
            return FluentFuture.from(
                    Futures.immediateFuture(
                            Pair.create(
                                    new ReportingUris(
                                            null,
                                            reportingResults.mSellerReportingResult
                                                    .getReportingUri()),
                                    ctx)));
        }

        CustomAudienceSignals customAudienceSignals =
                Objects.requireNonNull(ctx.mDBAdSelectionEntry.getCustomAudienceSignals());

        AdTechUriValidator buyerValidator =
                new AdTechUriValidator(
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        customAudienceSignals.getBuyer().toString(),
                        this.getClass().getSimpleName(),
                        REPORTING_URI_FIELD_NAME);

        return FluentFuture.from(
                mBackgroundExecutorService.submit(
                        () -> {
                            commitRegisteredAdEventsToDatabase(
                                    reportingResults.mBuyerReportingResult
                                            .getInteractionReportingUris(),
                                    buyerValidator,
                                    ctx.mDBAdSelectionEntry.getAdSelectionId(),
                                    FLAG_REPORTING_DESTINATION_BUYER);
                            return Pair.create(
                                    new ReportingUris(
                                            reportingResults.mBuyerReportingResult
                                                    .getReportingUri(),
                                            reportingResults.mSellerReportingResult
                                                    .getReportingUri()),
                                    ctx);
                        }));
    }

    /**
     * Iterates through each {@link InteractionUriRegistrationInfo}, validates each {@link
     * InteractionUriRegistrationInfo#getInteractionReportingUri()}, and commits it to the {@code
     * registered_ad_interactions} table if it's valid. Note: For system health purposes, we will
     * only commit a maximum of {@code mMaxRegisteredAdEventsPerAdTech} entries to the database.
     */
    private void commitRegisteredAdEventsToDatabase(
            @NonNull List<InteractionUriRegistrationInfo> interactionUriRegistrationInfos,
            @NonNull AdTechUriValidator validator,
            long adSelectionId,
            @ReportInteractionRequest.ReportingDestination int destination) {
        long numSellerEventUriEntries = 0;
        long maxRegisteredAdEventsPerAdTech = mFlags.getReportImpressionMaxEventUriEntriesCount();

        List<DBRegisteredAdInteraction> adEventsToRegister = new ArrayList<>();

        for (InteractionUriRegistrationInfo uriRegistrationInfo : interactionUriRegistrationInfos) {
            if (numSellerEventUriEntries >= maxRegisteredAdEventsPerAdTech) {
                LogUtil.v(
                        "Registered maximum number of registeredAEvents for this ad-tech! The rest"
                                + " in this list will be skipped.");
                break;
            }
            Uri uriToValidate = uriRegistrationInfo.getInteractionReportingUri();
            try {
                validator.validate(uriToValidate);
                DBRegisteredAdInteraction dbRegisteredAdInteraction =
                        DBRegisteredAdInteraction.builder()
                                .setAdSelectionId(adSelectionId)
                                .setInteractionKey(uriRegistrationInfo.getInteractionKey())
                                .setInteractionReportingUri(uriToValidate)
                                .setDestination(destination)
                                .build();
                adEventsToRegister.add(dbRegisteredAdInteraction);
                numSellerEventUriEntries++;
            } catch (IllegalArgumentException e) {
                LogUtil.v(
                        String.format(
                                "Uri %s failed validation! Skipping persistence of this event URI"
                                        + " pair.",
                                uriToValidate));
            }
        }
        mAdSelectionEntryDao.persistDBRegisteredAdInteractions(adEventsToRegister);
    }

    /**
     * Validates the {@code adSelectionConfig} from the request.
     *
     * @param adSelectionConfig the adSelectionConfig to be validated
     * @throws IllegalArgumentException if the provided {@code adSelectionConfig} is not valid
     */
    private void validateAdSelectionConfig(AdSelectionConfig adSelectionConfig)
            throws IllegalArgumentException {
        AdSelectionConfigValidator adSelectionConfigValidator = new AdSelectionConfigValidator();
        adSelectionConfigValidator.validate(adSelectionConfig);
    }

    private static class ReportingContext {
        @NonNull AdSelectionConfig mAdSelectionConfig;
        @NonNull DBAdSelectionEntry mDBAdSelectionEntry;
    }

    private static final class ReportingUris {
        @Nullable public final Uri buyerReportingUri;
        @NonNull public final Uri sellerReportingUri;

        private ReportingUris(@Nullable Uri buyerReportingUri, @NonNull Uri sellerReportingUri) {
            Objects.requireNonNull(sellerReportingUri);

            this.buyerReportingUri = buyerReportingUri;
            this.sellerReportingUri = sellerReportingUri;
        }
    }

    private static final class ReportingResults {
        @Nullable
        public final ReportImpressionScriptEngine.BuyerReportingResult mBuyerReportingResult;

        @NonNull
        public final ReportImpressionScriptEngine.SellerReportingResult mSellerReportingResult;

        private ReportingResults(
                @Nullable ReportImpressionScriptEngine.BuyerReportingResult buyerReportingResult,
                @NonNull ReportImpressionScriptEngine.SellerReportingResult sellerReportingResult) {
            Objects.requireNonNull(sellerReportingResult);

            mBuyerReportingResult = buyerReportingResult;
            mSellerReportingResult = sellerReportingResult;
        }
    }
}
