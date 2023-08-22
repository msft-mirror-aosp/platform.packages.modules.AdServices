/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.adservices.adselection.PersistAdSelectionResultCallback;
import android.adservices.adselection.PersistAdSelectionResultInput;
import android.adservices.adselection.PersistAdSelectionResultResponse;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.exceptions.AdServicesException;
import android.annotation.NonNull;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.datahandlers.AdSelectionResultBidAndUri;
import com.android.adservices.data.adselection.datahandlers.ReportingData;
import com.android.adservices.data.adselection.datahandlers.WinningCustomAudience;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.profiling.Tracing;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.AuctionResult;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.WinReportingUrls;
import com.android.adservices.service.stats.AdServicesLoggerUtil;
import com.android.adservices.service.stats.AdServicesStatsLog;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Objects;
import java.util.concurrent.ExecutorService;

/** Runner class for ProcessAdSelectionResultRunner service */
@RequiresApi(Build.VERSION_CODES.S)
public class PersistAdSelectionResultRunner {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final ObliviousHttpEncryptor mObliviousHttpEncryptor;
    @NonNull private final AdSelectionEntryDao mAdSelectionEntryDao;
    @NonNull private final AdSelectionServiceFilter mAdSelectionServiceFilter;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;
    @NonNull private final ListeningExecutorService mLightweightExecutorService;
    @NonNull private final int mCallerUid;
    @NonNull private final DevContext mDevContext;
    @NonNull private AuctionServerDataCompressor mDataCompressor;
    @NonNull private AuctionServerPayloadExtractor mPayloadExtractor;

    public PersistAdSelectionResultRunner(
            @NonNull final ObliviousHttpEncryptor obliviousHttpEncryptor,
            @NonNull final AdSelectionEntryDao adSelectionEntryDao,
            @NonNull final AdSelectionServiceFilter adSelectionServiceFilter,
            @NonNull final ExecutorService backgroundExecutorService,
            @NonNull final ExecutorService lightweightExecutorService,
            @NonNull final int callerUid,
            @NonNull final DevContext devContext) {
        Objects.requireNonNull(obliviousHttpEncryptor);
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(adSelectionServiceFilter);
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(devContext);

        mObliviousHttpEncryptor = obliviousHttpEncryptor;
        mAdSelectionEntryDao = adSelectionEntryDao;
        mAdSelectionServiceFilter = adSelectionServiceFilter;
        mBackgroundExecutorService = MoreExecutors.listeningDecorator(backgroundExecutorService);
        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutorService);
        mCallerUid = callerUid;
        mDevContext = devContext;
    }

    /** Orchestrates PersistAdSelectionResultRunner process. */
    public void run(
            @NonNull PersistAdSelectionResultInput inputParams,
            @NonNull PersistAdSelectionResultCallback callback) {
        Objects.requireNonNull(inputParams);
        Objects.requireNonNull(callback);

        int apiName = AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;
        long adSelectionId = inputParams.getAdSelectionId();
        try {
            ListenableFuture<Void> filteredRequest =
                    Futures.submit(
                            () -> {
                                try {
                                    sLogger.v(
                                            "Starting filtering for PersistAdSelectionResultRunner"
                                                    + " API.");
                                    // TODO(b/288371478): Validate seller owns the ad selection id
                                    mAdSelectionServiceFilter.filterRequest(
                                            inputParams.getSeller(),
                                            inputParams.getCallerPackageName(),
                                            false,
                                            true,
                                            mCallerUid,
                                            apiName,
                                            Throttler.ApiKey.FLEDGE_API_PERSIST_AD_SELECTION_RESULT,
                                            mDevContext);
                                } finally {
                                    sLogger.v("Completed filtering.");
                                }
                            },
                            mLightweightExecutorService);

            ListenableFuture<AuctionResult> getAdSelectionDataResult =
                    FluentFuture.from(filteredRequest)
                            .transformAsync(
                                    ignoredVoid ->
                                            orchestratePersistAdSelectionResultRunner(inputParams),
                                    mLightweightExecutorService);

            Futures.addCallback(
                    getAdSelectionDataResult,
                    new FutureCallback<>() {
                        @Override
                        public void onSuccess(AuctionResult result) {
                            Uri adRenderUri =
                                    (result.getIsChaff())
                                            ? Uri.EMPTY
                                            : Uri.parse(result.getAdRenderUrl());
                            notifySuccessToCaller(adRenderUri, adSelectionId, callback);
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            if (t instanceof FilterException
                                    && t.getCause()
                                            instanceof ConsentManager.RevokedConsentException) {
                                // Skip logging if a FilterException occurs.
                                // AdSelectionServiceFilter ensures the failing assertion is logged
                                // internally.

                                // Fail Silently by notifying success to caller
                                notifyEmptySuccessToCaller(callback, adSelectionId);
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
            sLogger.v("PersistAdSelectionResult fails fast with exception %s.", t.toString());
            notifyFailureToCaller(t, callback);
        }
    }

    private ListenableFuture<AuctionResult> orchestratePersistAdSelectionResultRunner(
            PersistAdSelectionResultInput request) {
        int traceCookie =
                Tracing.beginAsyncSection(Tracing.ORCHESTRATE_PERSIST_AD_SELECTION_RESULT);
        long adSelectionId = request.getAdSelectionId();
        AdTechIdentifier seller = request.getSeller();
        return decryptBytes(request)
                .transform(this::parseAdSelectionResult, mLightweightExecutorService)
                .transformAsync(
                        auctionResult -> {
                            ListenableFuture<AuctionResult> auctionResultFuture =
                                    persistAuctionResults(auctionResult, adSelectionId, seller);
                            Tracing.endAsyncSection(
                                    Tracing.ORCHESTRATE_PERSIST_AD_SELECTION_RESULT, traceCookie);
                            return auctionResultFuture;
                        },
                        mLightweightExecutorService);
        // TODO(b/278087551): Check if ad render uri is present on the device
    }

    private FluentFuture<byte[]> decryptBytes(PersistAdSelectionResultInput request) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.OHTTP_DECRYPT_BYTES);
        byte[] encryptedAuctionResult = request.getAdSelectionResult();
        long adSelectionId = request.getAdSelectionId();

        return FluentFuture.from(
                mLightweightExecutorService.submit(
                        () -> {
                            sLogger.v("Decrypting auction result data for :" + adSelectionId);
                            byte[] decryptedBytes =
                                    mObliviousHttpEncryptor.decryptBytes(
                                            encryptedAuctionResult, adSelectionId);
                            Tracing.endAsyncSection(Tracing.OHTTP_DECRYPT_BYTES, traceCookie);
                            return decryptedBytes;
                        }));
    }

    private AuctionResult parseAdSelectionResult(byte[] resultBytes) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.PARSE_AD_SELECTION_RESULT);
        initializeDataCompressor(resultBytes);
        initializePayloadFormatter(resultBytes);

        sLogger.v("Applying formatter on AuctionResult bytes");
        AuctionServerPayloadUnformattedData unformattedResult =
                mPayloadExtractor.extract(AuctionServerPayloadFormattedData.create(resultBytes));

        sLogger.v("Applying decompression on AuctionResult bytes");
        AuctionServerDataCompressor.UncompressedData uncompressedResult =
                mDataCompressor.decompress(
                        AuctionServerDataCompressor.CompressedData.create(
                                unformattedResult.getData()));

        AuctionResult auctionResult = composeAuctionResult(uncompressedResult);
        Tracing.endAsyncSection(Tracing.PARSE_AD_SELECTION_RESULT, traceCookie);
        return auctionResult;
    }

    private void initializeDataCompressor(@NonNull byte[] resultBytes) {
        Objects.requireNonNull(resultBytes, "AdSelectionResult bytes cannot be null");

        byte metaInfoByte = resultBytes[0];
        int version = AuctionServerPayloadFormattingUtil.extractCompressionVersion(metaInfoByte);
        mDataCompressor = AuctionServerDataCompressorFactory.getDataCompressor(version);
    }

    private void initializePayloadFormatter(byte[] resultBytes) {
        Objects.requireNonNull(resultBytes, "AdSelectionResult bytes cannot be null");

        byte metaInfoByte = resultBytes[0];
        int version = AuctionServerPayloadFormattingUtil.extractFormatterVersion(metaInfoByte);
        mPayloadExtractor = AuctionServerPayloadFormatterFactory.createPayloadExtractor(version);
    }

    private AuctionResult composeAuctionResult(
            AuctionServerDataCompressor.UncompressedData uncompressedData) {
        try {
            AuctionResult result = AuctionResult.parseFrom(uncompressedData.getData());
            logAuctionResult(result);
            return result;
        } catch (InvalidProtocolBufferException ex) {
            sLogger.e("Error during parsing AuctionResult proto from byte[]");
            throw new RuntimeException(ex);
        }
    }

    private ListenableFuture<AuctionResult> persistAuctionResults(
            AuctionResult auctionResult, long adSelectionId, AdTechIdentifier seller) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.PERSIST_AUCTION_RESULTS);
        return mBackgroundExecutorService.submit(
                () -> {
                    // TODO(b/288622004): Validate seller against the db before persisting

                    if (!auctionResult.getIsChaff()) {
                        Preconditions.checkArgument(
                                !auctionResult.getCustomAudienceName().isEmpty(),
                                "Custom audience name should not be empty.");
                        Preconditions.checkArgument(
                                !auctionResult.getAdRenderUrl().isEmpty(),
                                "Ad render uri should not be empty");
                        Preconditions.checkArgument(
                                !auctionResult.getBuyer().isEmpty(), "Buyer should not be empty");

                        WinReportingUrls winReportingUrls = auctionResult.getWinReportingUrls();
                        String buyerReportingUrl =
                                winReportingUrls.getBuyerReportingUrls().getReportingUrl();
                        String sellerReportingUrl =
                                winReportingUrls
                                        .getComponentSellerReportingUrls()
                                        .getReportingUrl();

                        mAdSelectionEntryDao.persistAdSelectionResultForCustomAudience(
                                adSelectionId,
                                AdSelectionResultBidAndUri.builder()
                                        .setWinningAdBid(auctionResult.getBid())
                                        .setWinningAdRenderUri(
                                                Uri.parse(auctionResult.getAdRenderUrl()))
                                        .build(),
                                AdTechIdentifier.fromString(auctionResult.getBuyer()),
                                WinningCustomAudience.builder()
                                        .setName(auctionResult.getCustomAudienceName())
                                        .build());
                        mAdSelectionEntryDao.persistReportingData(
                                adSelectionId,
                                ReportingData.builder()
                                        // TODO(b/288622004): Validate that reporting url domain is
                                        //  same as seller buyer domain. Also, remove Uri.EMPTY
                                        //  once auction server support event level reporting.
                                        .setBuyerWinReportingUri(
                                                Objects.isNull(buyerReportingUrl)
                                                        ? Uri.EMPTY
                                                        : Uri.parse(buyerReportingUrl))
                                        .setSellerWinReportingUri(
                                                Objects.isNull(sellerReportingUrl)
                                                        ? Uri.EMPTY
                                                        : Uri.parse(sellerReportingUrl))
                                        .build());
                    }
                    Tracing.endAsyncSection(Tracing.PERSIST_AUCTION_RESULTS, traceCookie);
                    return auctionResult;
                });
    }

    private void notifySuccessToCaller(
            Uri renderUri, long adSelectionId, PersistAdSelectionResultCallback callback) {
        try {
            // TODO(b/288370270): Collect API metrics
            callback.onSuccess(
                    new PersistAdSelectionResultResponse.Builder()
                            .setAdSelectionId(adSelectionId)
                            .setAdRenderUri(renderUri)
                            .build());
        } catch (RemoteException e) {
            sLogger.e(e, "Encountered exception during notifying PersistAdSelectionResultCallback");
        } finally {
            sLogger.v("Persist Ad Selection Result completed and attempted notifying success");
        }
    }

    private void notifyEmptySuccessToCaller(
            @NonNull PersistAdSelectionResultCallback callback, long adSelectionId) {
        try {
            // TODO(b/288368908): Determine what is an appropriate empty response for revoked
            //  consent
            // TODO(b/288370270): Collect API metrics
            callback.onSuccess(
                    new PersistAdSelectionResultResponse.Builder()
                            .setAdSelectionId(adSelectionId)
                            .setAdRenderUri(Uri.EMPTY)
                            .build());
        } catch (RemoteException e) {
            sLogger.e(e, "Encountered exception during notifying PersistAdSelectionResultCallback");
        } finally {
            sLogger.v(
                    "Persist Ad Selection Result completed, attempted notifying success for a"
                            + " silent failure");
        }
    }

    private void notifyFailureToCaller(Throwable t, PersistAdSelectionResultCallback callback) {
        try {
            // TODO(b/288370270): Collect API metrics
            sLogger.e("Notify caller of error: " + t);
            int resultCode = AdServicesLoggerUtil.getResultCodeFromException(t);

            FledgeErrorResponse selectionFailureResponse =
                    new FledgeErrorResponse.Builder()
                            .setErrorMessage("Error while collecting and compressing CAs")
                            .setStatusCode(resultCode)
                            .build();
            sLogger.e(t, "Ad Selection failure: ");
            callback.onFailure(selectionFailureResponse);
        } catch (RemoteException e) {
            sLogger.e(e, "Encountered exception during notifying PersistAdSelectionResultCallback");
        } finally {
            sLogger.v("Persist Ad Selection Result failed");
        }
    }

    private void logAuctionResult(AuctionResult auctionResult) {
        sLogger.v(
                " Decrypted AuctionResult proto: "
                        + "\nadRenderUrl: %s"
                        + "\ncustom audience name: %s"
                        + "\nbuyer: %s"
                        + "\nscore: %s"
                        + "\nbid: %s"
                        + "\nis_chaff: %s"
                        + "\nbuyer reporting url: %s"
                        + "\nseller reporting url: %s",
                auctionResult.getAdRenderUrl(),
                auctionResult.getCustomAudienceName(),
                auctionResult.getBuyer(),
                auctionResult.getScore(),
                auctionResult.getBid(),
                auctionResult.getIsChaff(),
                auctionResult.getWinReportingUrls().getBuyerReportingUrls().getReportingUrl(),
                auctionResult
                        .getWinReportingUrls()
                        .getComponentSellerReportingUrls()
                        .getReportingUrl());
    }
}
