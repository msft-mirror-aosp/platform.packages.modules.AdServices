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
import android.adservices.adselection.PersistAdSelectionResultRequest;
import android.adservices.adselection.PersistAdSelectionResultResponse;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.exceptions.AdServicesException;
import android.annotation.NonNull;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.DBEncryptionContext;
import com.android.adservices.data.adselection.DBReportingUris;
import com.android.adservices.data.adselection.EncryptionContextDao;
import com.android.adservices.data.adselection.EncryptionKeyConstants;
import com.android.adservices.data.adselection.ReportingUrisDao;
import com.android.adservices.ohttp.ObliviousHttpClient;
import com.android.adservices.ohttp.ObliviousHttpKeyConfig;
import com.android.adservices.ohttp.ObliviousHttpRequestContext;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.AuctionResult;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.WinReportingUrls;
import com.android.adservices.service.stats.AdServicesLoggerUtil;
import com.android.adservices.service.stats.AdServicesStatsLog;

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
    @NonNull private final EncryptionContextDao mEncryptionContextDao;
    @NonNull private final ReportingUrisDao mReportingUrisDao;
    @NonNull private final AdSelectionServiceFilter mAdSelectionServiceFilter;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;
    @NonNull private final ListeningExecutorService mLightweightExecutorService;
    @NonNull private final Flags mFlags;
    @NonNull private final int mCallerUid;

    @NonNull private AuctionServerDataCompressor mDataCompressor;
    @NonNull private AuctionServerPayloadFormatter mPayloadFormatter;

    public PersistAdSelectionResultRunner(
            @NonNull final EncryptionContextDao encryptionContextDao,
            @NonNull final ReportingUrisDao reportingUrisDao,
            @NonNull final AdSelectionServiceFilter adSelectionServiceFilter,
            @NonNull final ExecutorService backgroundExecutorService,
            @NonNull final ExecutorService lightweightExecutorService,
            @NonNull final Flags flags,
            @NonNull final int callerUid) {
        Objects.requireNonNull(encryptionContextDao);
        Objects.requireNonNull(reportingUrisDao);
        Objects.requireNonNull(adSelectionServiceFilter);
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(flags);

        mEncryptionContextDao = encryptionContextDao;
        mReportingUrisDao = reportingUrisDao;
        mAdSelectionServiceFilter = adSelectionServiceFilter;
        mBackgroundExecutorService = MoreExecutors.listeningDecorator(backgroundExecutorService);
        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutorService);
        mFlags = flags;
        mCallerUid = callerUid;
    }

    /** Orchestrates PersistAdSelectionResultRunner process. */
    public void run(
            @NonNull PersistAdSelectionResultInput inputParams,
            @NonNull PersistAdSelectionResultCallback callback) {
        Objects.requireNonNull(inputParams);
        Objects.requireNonNull(callback);

        int apiName = AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;
        long adSelectionId = inputParams.getPersistAdSelectionResultRequest().getAdSelectionId();
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
                                            inputParams
                                                    .getPersistAdSelectionResultRequest()
                                                    .getSeller(),
                                            inputParams.getCallerPackageName(),
                                            false,
                                            true,
                                            mCallerUid,
                                            apiName,
                                            Throttler.ApiKey.FLEDGE_API_SELECT_ADS);
                                } finally {
                                    sLogger.v("Completed filtering.");
                                }
                            },
                            mLightweightExecutorService);

            ListenableFuture<AuctionResult> getAdSelectionDataResult =
                    FluentFuture.from(filteredRequest)
                            .transformAsync(
                                    ignoredVoid ->
                                            orchestratePersistAdSelectionResultRunner(
                                                    inputParams
                                                            .getPersistAdSelectionResultRequest()),
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
                                notifyEmptySuccessToCaller(callback);
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
            @NonNull PersistAdSelectionResultRequest request) {
        long adSelectionId = request.getAdSelectionId();

        return FluentFuture.from(mLightweightExecutorService.submit(request::getAdSelectionResult))
                // TODO(ag/23781507): Add encryption to the unittest once ag/23781507 is merged
                //                 .transform((resultBytes) -> decryptBytes(resultBytes,
                // adSelectionId),
                //                 mLightweightExecutorService)
                .transform(this::parseAdSelectionResult, mLightweightExecutorService)
                .transformAsync(
                        auctionResult -> persistReportingUris(auctionResult, adSelectionId),
                        mLightweightExecutorService);
        // TODO(b/278087551): Check if ad render uri is present on the device
    }

    private AuctionResult parseAdSelectionResult(byte[] resultBytes) {
        initializeDataCompressor(resultBytes);
        initializePayloadFormatter(resultBytes);

        sLogger.v("Applying formatter on AuctionResult bytes");
        AuctionServerPayloadFormatter.UnformattedData unformattedResult =
                mPayloadFormatter.extract(
                        AuctionServerPayloadFormatter.FormattedData.create(resultBytes));

        sLogger.v("Applying decompression on AuctionResult bytes");
        AuctionServerDataCompressor.UncompressedData uncompressedResult =
                mDataCompressor.decompress(
                        AuctionServerDataCompressor.CompressedData.create(
                                unformattedResult.getData()));

        return composeAuctionResult(uncompressedResult);
    }

    private byte[] decryptBytes(byte[] encryptedAuctionResult, long adSelectionId) {
        try {
            DBEncryptionContext dbContext =
                    mEncryptionContextDao.getEncryptionContext(
                            adSelectionId,
                            EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_AUCTION);

            ObliviousHttpKeyConfig config = dbContext.getKeyConfig();
            Objects.requireNonNull(config);

            ObliviousHttpClient client = ObliviousHttpClient.create(config);
            Objects.requireNonNull(client);

            ObliviousHttpRequestContext context =
                    ObliviousHttpRequestContext.create(
                            config,
                            dbContext.getSharedSecret(),
                            ObliviousHttpRequestContext.deserializeSeed(dbContext.getSeed()));
            Objects.requireNonNull(context);

            return client.decryptObliviousHttpResponse(encryptedAuctionResult, context);
        } catch (Exception e) {
            sLogger.e("Unexpected error during decryption");
            throw new RuntimeException(e);
        }
    }

    private void initializeDataCompressor(byte[] resultBytes) {
        Objects.requireNonNull(resultBytes, "AdSelectionResult bytes cannot be null");

        byte metaInfoByte = resultBytes[0];
        int version = AuctionServerPayloadFormatter.extractCompressionVersion(metaInfoByte);
        mDataCompressor = AuctionServerDataCompressorFactory.getDataCompressor(version);
    }

    private void initializePayloadFormatter(byte[] resultBytes) {
        Objects.requireNonNull(resultBytes, "AdSelectionResult bytes cannot be null");

        byte metaInfoByte = resultBytes[0];
        int version = AuctionServerPayloadFormatter.extractFormatterVersion(metaInfoByte);
        mPayloadFormatter = AuctionServerPayloadFormatterFactory.getPayloadFormatter(version);
    }

    private AuctionResult composeAuctionResult(
            AuctionServerDataCompressor.UncompressedData uncompressedData) {
        try {
            return AuctionResult.parseFrom(uncompressedData.getData());
        } catch (InvalidProtocolBufferException ex) {
            sLogger.e("Error during parsing AuctionResult proto from byte[]");
            throw new RuntimeException(ex);
        }
    }

    private ListenableFuture<AuctionResult> persistReportingUris(
            AuctionResult auctionResult, long adSelectionId) {
        return mBackgroundExecutorService.submit(
                () -> {
                    WinReportingUrls winReportingUrls = auctionResult.getWinReportingUrls();
                    String buyerReportingUrl =
                            winReportingUrls.getBuyerReportingUrls().getReportingUrl();
                    String sellerReportingUrl =
                            winReportingUrls.getComponentSellerReportingUrls().getReportingUrl();

                    mReportingUrisDao.insertReportingUris(
                            DBReportingUris.builder()
                                    .setAdSelectionId(adSelectionId)
                                    // TODO(b/288622004): Validate that reporting url domain is
                                    // same as seller buyer domain. Also, remove Uri.EMPTY
                                    // once auction server support event level reporting.
                                    .setBuyerReportingUri(
                                            buyerReportingUrl.isEmpty()
                                                    ? Uri.EMPTY
                                                    : Uri.parse(buyerReportingUrl))
                                    .setSellerReportingUri(
                                            sellerReportingUrl.isEmpty()
                                                    ? Uri.EMPTY
                                                    : Uri.parse(sellerReportingUrl))
                                    .build());
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

    private void notifyEmptySuccessToCaller(@NonNull PersistAdSelectionResultCallback callback) {
        try {
            // TODO(b/288368908): Determine what is an appropriate empty response for revoked
            //  consent
            // TODO(b/288370270): Collect API metrics
            callback.onSuccess(null);
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
}
