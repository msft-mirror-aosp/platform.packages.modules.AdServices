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
import android.adservices.adselection.ReportImpressionCallback;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.FledgeErrorResponse;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Pair;

import com.android.adservices.LogUtil;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.DBAdSelectionEntry;
import com.android.internal.util.Preconditions;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.json.JSONException;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/** Encapsulates the Impression Reporting logic */
public class ImpressionReporter {

    @NonNull private final Context mContext;
    @NonNull private final AdSelectionEntryDao mAdSelectionEntryDao;
    @NonNull private final AdSelectionHttpClient mAdSelectionHttpClient;
    @NonNull private final ListeningExecutorService mListeningExecutorService;
    @NonNull private final ReportImpressionScriptEngine mJsEngine;

    public ImpressionReporter(
            Context context,
            ExecutorService executor,
            AdSelectionEntryDao adSelectionEntryDao,
            AdSelectionHttpClient adSelectionHttpClient) {
        mContext = context;
        mListeningExecutorService = MoreExecutors.listeningDecorator(executor);
        mAdSelectionEntryDao = adSelectionEntryDao;
        mAdSelectionHttpClient = adSelectionHttpClient;
        mJsEngine = new ReportImpressionScriptEngine(mContext);
    }

    /**
     * Run the impression report logic asynchronously. Invoked seller's reportResult() as well as
     * the buyer's reportWin() in the case of a remarketing ad.
     *
     * <p>After invoking the javascript functions, invokes the onSuccess function of the callback
     * and reports URLs resulting from the javascript functions.
     *
     * @param requestParams request parameters containing the {@code adSelectionId} and {@code
     *     adSelectionConfig}
     * @param callback callback function to be called in case of success or failure
     */
    public void reportImpression(
            @NonNull ReportImpressionRequest requestParams,
            @NonNull ReportImpressionCallback callback) {
        long adSelectionId = requestParams.getAdSelectionId();
        AdSelectionConfig adSelectionConfig = requestParams.getAdSelectionConfig();

        FluentFuture<ReportingUrls> reportingUrlFuture =
                computeReportingUrls(adSelectionId, adSelectionConfig);
        reportingUrlFuture
                .transform(
                        reportingUrls -> notifySuccessToCaller(callback, reportingUrls),
                        mListeningExecutorService)
                .transformAsync(this::doReport, mListeningExecutorService)
                .addCallback(
                        new FutureCallback<List<Void>>() {
                            @Override
                            public void onSuccess(List<Void> result) {
                                LogUtil.d("Report impression succeeded!");
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                LogUtil.e(t, "Report Impression failed!");
                                notifyFailureToCaller(callback, t);
                            }
                        },
                        mListeningExecutorService);
    }

    private ReportingUrls notifySuccessToCaller(
            @NonNull ReportImpressionCallback callback, ReportingUrls reportingUrls) {
        invokeSuccess(callback);
        return reportingUrls;
    }

    private void notifyFailureToCaller(
            @NonNull ReportImpressionCallback callback, @NonNull Throwable t) {
        if (t instanceof IllegalArgumentException) {
            invokeFailure(callback, AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, t.getMessage());
        } else {
            invokeFailure(callback, AdServicesStatusUtils.STATUS_INTERNAL_ERROR, t.getMessage());
        }
    }

    @NonNull
    private ListenableFuture<List<Void>> doReport(ReportingUrls reportingUrls) {
        ListenableFuture<Void> sellerFuture =
                mAdSelectionHttpClient.reportUrl(reportingUrls.sellerReportingUri);
        ListenableFuture<Void> buyerFuture;

        if (!Objects.isNull(reportingUrls.buyerReportingUri)) {
            buyerFuture = mAdSelectionHttpClient.reportUrl(reportingUrls.buyerReportingUri);
        } else {
            buyerFuture = Futures.immediateFuture(null);
        }

        return Futures.allAsList(sellerFuture, buyerFuture);
    }

    private FluentFuture<ReportingUrls> computeReportingUrls(
            long adSelectionId, AdSelectionConfig adSelectionConfig) {
        return fetchAdSelectionEntry(adSelectionId)
                .transformAsync(
                        dbAdSelectionEntry -> {
                            ReportingContext ctx = new ReportingContext();
                            ctx.mDBAdSelectionEntry = dbAdSelectionEntry;
                            ctx.mAdSelectionConfig = adSelectionConfig;
                            return fetchSellerDecisionLogic(ctx);
                        },
                        mListeningExecutorService)
                .transformAsync(
                        decisionLogicJsAndCtx ->
                                invokeSellerScript(
                                        decisionLogicJsAndCtx.first, decisionLogicJsAndCtx.second),
                        mListeningExecutorService)
                .transformAsync(
                        sellerResultAndCtx ->
                                invokeBuyerScript(
                                        sellerResultAndCtx.first, sellerResultAndCtx.second),
                        mListeningExecutorService)
                .transform(urlsAndContext -> urlsAndContext.first, mListeningExecutorService);
    }

    private FluentFuture<DBAdSelectionEntry> fetchAdSelectionEntry(long adSelectionId) {
        return FluentFuture.from(
                mListeningExecutorService.submit(
                        () -> {
                            Preconditions.checkArgument(
                                    mAdSelectionEntryDao.doesAdSelectionIdExist(adSelectionId),
                                    "Unable to find ad selection with given ID");
                            return mAdSelectionEntryDao.getAdSelectionEntityById(adSelectionId);
                        }));
    }

    private FluentFuture<Pair<String, ReportingContext>> fetchSellerDecisionLogic(
            ReportingContext ctx) {
        return FluentFuture.from(
                        mAdSelectionHttpClient.fetchJavascript(
                                ctx.mAdSelectionConfig.getDecisionLogicUrl()))
                .transform(
                        stringResult -> Pair.create(stringResult, ctx), mListeningExecutorService);
    }

    private FluentFuture<Pair<ReportImpressionScriptEngine.SellerReportingResult, ReportingContext>>
            invokeSellerScript(String decisionLogicJs, ReportingContext ctx) {
        try {
            return FluentFuture.from(
                            mJsEngine.reportResult(
                                    decisionLogicJs,
                                    ctx.mAdSelectionConfig,
                                    ctx.mDBAdSelectionEntry.getWinningAdRenderUrl(),
                                    ctx.mDBAdSelectionEntry.getWinningAdBid(),
                                    ctx.mDBAdSelectionEntry.getContextualSignals()))
                    .transform(
                            sellerResult -> Pair.create(sellerResult, ctx),
                            mListeningExecutorService);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid JSON data", e);
        }
    }

    private FluentFuture<Pair<ReportingUrls, ReportingContext>> invokeBuyerScript(
            ReportImpressionScriptEngine.SellerReportingResult sellerReportingResult,
            ReportingContext ctx) {
        final boolean isContextual =
                Objects.isNull(ctx.mDBAdSelectionEntry.getCustomAudienceSignals())
                        && Objects.isNull(ctx.mDBAdSelectionEntry.getBuyerDecisionLogicJs());

        if (isContextual) {
            return FluentFuture.from(
                    Futures.immediateFuture(
                            Pair.create(
                                    new ReportingUrls(
                                            null, sellerReportingResult.getReportingUrl()),
                                    ctx)));
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
                                    ctx.mDBAdSelectionEntry.getContextualSignals(),
                                    ctx.mDBAdSelectionEntry.getCustomAudienceSignals()))
                    .transform(
                            resultUri ->
                                    Pair.create(
                                            new ReportingUrls(
                                                    resultUri,
                                                    sellerReportingResult.getReportingUrl()),
                                            ctx),
                            mListeningExecutorService);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid JSON args", e);
        }
    }

    /** Invokes the onFailure function from the callback and handles the exception. */
    public static void invokeFailure(
            @androidx.annotation.NonNull ReportImpressionCallback callback,
            int statusCode,
            String errorMessage) {
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(statusCode)
                            .setErrorMessage(errorMessage)
                            .build());
        } catch (RemoteException e) {
            LogUtil.e("Unable to send failed result to the callback", e);
            throw e.rethrowFromSystemServer();
        }
    }

    /** Invokes the onSuccess function from the callback and handles the exception. */
    public static void invokeSuccess(
            @androidx.annotation.NonNull ReportImpressionCallback callback) {
        try {
            callback.onSuccess();
        } catch (RemoteException e) {
            LogUtil.e("Unable to send successful result to the callback", e);
            throw e.rethrowFromSystemServer();
        }
    }

    private static class ReportingContext {
        @NonNull AdSelectionConfig mAdSelectionConfig;
        @NonNull DBAdSelectionEntry mDBAdSelectionEntry;
    }

    private static final class ReportingUrls {
        @Nullable public final Uri buyerReportingUri;
        @NonNull public final Uri sellerReportingUri;

        private ReportingUrls(Uri buyerReportingUri, Uri sellerReportingUri) {
            this.buyerReportingUri = buyerReportingUri;
            this.sellerReportingUri = sellerReportingUri;
        }
    }
}
