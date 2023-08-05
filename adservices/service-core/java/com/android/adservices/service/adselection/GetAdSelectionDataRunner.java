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

import android.adservices.adselection.GetAdSelectionDataCallback;
import android.adservices.adselection.GetAdSelectionDataInput;
import android.adservices.adselection.GetAdSelectionDataRequest;
import android.adservices.adselection.GetAdSelectionDataResponse;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.exceptions.AdServicesException;
import android.annotation.NonNull;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.AuctionServerAdSelectionDao;
import com.android.adservices.data.adselection.DBAuctionServerAdSelection;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.BuyerInput;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.ProtectedAudienceInput;
import com.android.adservices.service.stats.AdServicesLoggerUtil;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/** Runner class for GetAdSelectionData service */
@RequiresApi(Build.VERSION_CODES.S)
public class GetAdSelectionDataRunner {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final ObliviousHttpEncryptor mObliviousHttpEncryptor;
    @NonNull private final CustomAudienceDao mCustomAudienceDao;
    @NonNull private final AuctionServerAdSelectionDao mAuctionServerAdSelectionDao;
    @NonNull private final AdSelectionServiceFilter mAdSelectionServiceFilter;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;
    @NonNull private final ListeningExecutorService mLightweightExecutorService;
    @NonNull private final Flags mFlags;
    private final int mCallerUid;

    @NonNull protected final AdSelectionIdGenerator mAdSelectionIdGenerator;
    @NonNull private final BuyerInputGenerator mBuyerInputGenerator;
    @NonNull private final AuctionServerDataCompressor mDataCompressor;
    @NonNull private final AuctionServerPayloadFormatter mPayloadFormatter;
    @NonNull private final DevContext mDevContext;

    public GetAdSelectionDataRunner(
            @NonNull final ObliviousHttpEncryptor obliviousHttpEncryptor,
            @NonNull final CustomAudienceDao customAudienceDao,
            @NonNull final AuctionServerAdSelectionDao auctionServerAdSelectionDao,
            @NonNull final AdSelectionServiceFilter adSelectionServiceFilter,
            @NonNull final AdFilterer adFilterer,
            @NonNull final ExecutorService backgroundExecutorService,
            @NonNull final ExecutorService lightweightExecutorService,
            @NonNull final Flags flags,
            final int callerUid,
            @NonNull final DevContext devContext) {
        Objects.requireNonNull(obliviousHttpEncryptor);
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(adFilterer);
        Objects.requireNonNull(auctionServerAdSelectionDao);
        Objects.requireNonNull(adSelectionServiceFilter);
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(devContext);

        mObliviousHttpEncryptor = obliviousHttpEncryptor;
        mCustomAudienceDao = customAudienceDao;
        mAuctionServerAdSelectionDao = auctionServerAdSelectionDao;
        mAdSelectionServiceFilter = adSelectionServiceFilter;
        mBackgroundExecutorService = MoreExecutors.listeningDecorator(backgroundExecutorService);
        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutorService);
        mFlags = flags;
        mCallerUid = callerUid;
        mDevContext = devContext;

        mAdSelectionIdGenerator = new AdSelectionIdGenerator();
        mBuyerInputGenerator =
                new BuyerInputGenerator(
                        mCustomAudienceDao,
                        adFilterer,
                        mFlags,
                        mLightweightExecutorService,
                        mBackgroundExecutorService);
        mDataCompressor =
                AuctionServerDataCompressorFactory.getDataCompressor(
                        mFlags.getFledgeAuctionServerCompressionAlgorithmVersion());
        mPayloadFormatter =
                AuctionServerPayloadFormatterFactory.createPayloadFormatter(
                        mFlags.getFledgeAuctionServerPayloadFormatVersion(),
                        mFlags.getFledgeAuctionServerPayloadBucketSizes());
    }

    /** Orchestrates GetAdSelectionData process. */
    public void run(
            @NonNull GetAdSelectionDataInput inputParams,
            @NonNull GetAdSelectionDataCallback callback) {
        Objects.requireNonNull(inputParams);
        Objects.requireNonNull(callback);

        int apiName = AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;
        long adSelectionId = mAdSelectionIdGenerator.generateId();
        try {
            ListenableFuture<Void> filteredRequest =
                    Futures.submit(
                            () -> {
                                try {
                                    sLogger.v("Starting filtering for GetAdSelectionData API.");
                                    mAdSelectionServiceFilter.filterRequest(
                                            inputParams.getAdSelectionDataRequest().getSeller(),
                                            inputParams.getCallerPackageName(),
                                            /*enforceForeground:*/ false,
                                            /*enforceConsent:*/ true,
                                            mCallerUid,
                                            apiName,
                                            Throttler.ApiKey.FLEDGE_API_GET_AD_SELECTION_DATA,
                                            mDevContext);
                                } finally {
                                    sLogger.v("Completed filtering.");
                                }
                            },
                            mLightweightExecutorService);

            ListenableFuture<byte[]> getAdSelectionDataResult =
                    FluentFuture.from(filteredRequest)
                            .transformAsync(
                                    ignoredVoid ->
                                            orchestrateGetAdSelectionDataRunner(
                                                    inputParams.getAdSelectionDataRequest(),
                                                    adSelectionId),
                                    mLightweightExecutorService);

            Futures.addCallback(
                    getAdSelectionDataResult,
                    new FutureCallback<>() {
                        @Override
                        public void onSuccess(byte[] result) {
                            Objects.requireNonNull(result);

                            notifySuccessToCaller(result, adSelectionId, callback);
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
            sLogger.v("runOutcomeSelection fails fast with exception %s.", t.toString());
            notifyFailureToCaller(t, callback);
        }
    }

    private ListenableFuture<byte[]> orchestrateGetAdSelectionDataRunner(
            @NonNull GetAdSelectionDataRequest request, long adSelectionId) {
        Objects.requireNonNull(request);

        long keyFetchTimeout = mFlags.getFledgeAuctionServerAuctionKeyFetchTimeoutMs();
        return mBuyerInputGenerator
                .createBuyerInputs()
                .transform(
                        buyerInputs -> createPayload(buyerInputs, request, adSelectionId),
                        mLightweightExecutorService)
                .transformAsync(
                        formatted -> {
                            sLogger.v("Encrypting composed proto bytes");
                            return mObliviousHttpEncryptor.encryptBytes(
                                    formatted.getData(), adSelectionId, keyFetchTimeout);
                        },
                        mLightweightExecutorService)
                .transformAsync(
                        encrypted ->
                                persistAdSelectionIdRequest(
                                        adSelectionId, request.getSeller(), encrypted),
                        mLightweightExecutorService);
    }

    private AuctionServerPayloadFormattedData createPayload(
            Map<AdTechIdentifier, BuyerInput> buyerInputs,
            GetAdSelectionDataRequest request,
            long adSelectionId) {
        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedBuyerInput =
                compressEachBuyerInput(buyerInputs);
        ProtectedAudienceInput protectedAudienceInput =
                composeProtectedAudienceInputBytes(
                        compressedBuyerInput, request.getSeller(), adSelectionId);
        sLogger.v("ProtectedAudienceInput composed");
        return applyPayloadFormatter(protectedAudienceInput);
    }

    private ListenableFuture<byte[]> persistAdSelectionIdRequest(
            long adSelectionId, AdTechIdentifier seller, byte[] encryptedBytes) {
        return mBackgroundExecutorService.submit(
                () -> {
                    mAuctionServerAdSelectionDao.insertAuctionServerAdSelection(
                            DBAuctionServerAdSelection.builder()
                                    .setAdSelectionId(adSelectionId)
                                    .setSeller(seller)
                                    .build());
                    return encryptedBytes;
                });
    }

    private Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData>
            compressEachBuyerInput(Map<AdTechIdentifier, BuyerInput> buyerInputMap) {
        // TODO(b/284302132): Move inside the BuyerInputGenerator
        return buyerInputMap.entrySet().parallelStream()
                .collect(
                        Collectors.toMap(
                                e -> e.getKey(),
                                e -> {
                                    sLogger.v(
                                            String.format(
                                                    "Compressing request for buyer %s",
                                                    e.getKey().toString()));
                                    return mDataCompressor.compress(
                                            AuctionServerDataCompressor.UncompressedData.create(
                                                    e.getValue().toByteArray()));
                                }));
    }

    @VisibleForTesting
    ProtectedAudienceInput composeProtectedAudienceInputBytes(
            Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedBuyerInputs,
            AdTechIdentifier seller,
            long adSelectionId) {
        sLogger.v("Composing ProtectedAudienceInput with buyer inputs and publisher");
        return ProtectedAudienceInput.newBuilder()
                .putAllBuyerInput(
                        compressedBuyerInputs.entrySet().parallelStream()
                                .collect(
                                        Collectors.toMap(
                                                e -> e.getKey().toString(),
                                                e -> ByteString.copyFrom(e.getValue().getData()))))
                .setPublisherName(seller.toString())
                .setEnableDebugReporting(mFlags.getFledgeAuctionServerEnableDebugReporting())
                // TODO(b/288287435): Set generation ID as a UUID generated per request which is not
                //  accessible in plaintext.
                .setGenerationId(String.valueOf(adSelectionId))
                .build();
    }

    private AuctionServerPayloadFormattedData applyPayloadFormatter(
            ProtectedAudienceInput protectedAudienceInput) {
        int version = mFlags.getFledgeAuctionServerCompressionAlgorithmVersion();
        sLogger.v("Applying formatter V" + version + " on protected audience input bytes");
        AuctionServerPayloadUnformattedData unformattedData =
                AuctionServerPayloadUnformattedData.create(protectedAudienceInput.toByteArray());
        return mPayloadFormatter.apply(unformattedData, version);
    }

    private void notifySuccessToCaller(
            byte[] result, long adSelectionId, GetAdSelectionDataCallback callback) {
        Objects.requireNonNull(result);

        try {
            callback.onSuccess(
                    new GetAdSelectionDataResponse.Builder()
                            .setAdSelectionId(adSelectionId)
                            .setAdSelectionData(result)
                            .build());
        } catch (RemoteException e) {
            sLogger.e(e, "Encountered exception during notifying GetAdSelectionDataCallback");
        } finally {
            sLogger.v("Get Ad Selection Data completed and attempted notifying success");
        }
    }

    private void notifyEmptySuccessToCaller(@NonNull GetAdSelectionDataCallback callback) {
        try {
            // TODO(b/259522822): Determine what is an appropriate empty response for revoked
            //  consent for selectAdsFromOutcomes
            callback.onSuccess(null);
        } catch (RemoteException e) {
            sLogger.e(e, "Encountered exception during notifying GetAdSelectionDataCallback");
        } finally {
            sLogger.v(
                    "Get Ad Selection Data completed, attempted notifying success for a"
                            + " silent failure");
        }
    }

    private void notifyFailureToCaller(Throwable t, GetAdSelectionDataCallback callback) {
        try {
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
            sLogger.e(e, "Encountered exception during notifying GetAdSelectionDataCallback");
        } finally {
            sLogger.v("Get Ad Selection Data failed");
        }
    }
}
