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

package com.android.adservices.data.signals;

import android.adservices.common.AdTechIdentifier;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Responsible for handling downloads, updates & delete for encoder logics for buyers
 *
 * <p>Thread safety:
 *
 * <ol>
 *   <li>The updates are thread safe per buyer
 *   <li>Updates for one buyer do not block updates for other buyer
 * </ol>
 */
public class EncoderLogicHandler {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting static final String ENCODER_VERSION_RESPONSE_HEADER = "X_ENCODER_VERSION";
    @VisibleForTesting static final int FALLBACK_VERSION = 0;
    @NonNull private final EncoderPersistenceDao mEncoderPersistenceDao;
    @NonNull private final EncoderEndpointsDao mEncoderEndpointsDao;
    @NonNull private final EncoderLogicDao mEncoderLogicDao;
    @NonNull private final AdServicesHttpsClient mAdServicesHttpsClient;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;
    @NonNull private final Map<AdTechIdentifier, ReentrantLock> mBuyerTransactionLocks;

    @NonNull
    private final ImmutableSet<String> mDownloadRequestProperties =
            ImmutableSet.of(ENCODER_VERSION_RESPONSE_HEADER);

    @VisibleForTesting
    public EncoderLogicHandler(
            @NonNull EncoderPersistenceDao encoderPersistenceDao,
            @NonNull EncoderEndpointsDao encoderEndpointsDao,
            @NonNull EncoderLogicDao encoderLogicDao,
            @NonNull AdServicesHttpsClient httpsClient,
            @NonNull ListeningExecutorService backgroundExecutorService) {
        Objects.requireNonNull(encoderPersistenceDao);
        Objects.requireNonNull(encoderEndpointsDao);
        Objects.requireNonNull(encoderLogicDao);
        Objects.requireNonNull(httpsClient);
        Objects.requireNonNull(backgroundExecutorService);
        mEncoderPersistenceDao = encoderPersistenceDao;
        mEncoderEndpointsDao = encoderEndpointsDao;
        mEncoderLogicDao = encoderLogicDao;
        mAdServicesHttpsClient = httpsClient;
        mBackgroundExecutorService = backgroundExecutorService;
        mBuyerTransactionLocks = new HashMap<>();
    }

    public EncoderLogicHandler(@NonNull Context context) {
        this(
                EncoderPersistenceDao.getInstance(context),
                ProtectedSignalsDatabase.getInstance(context).getEncoderEndpointsDao(),
                ProtectedSignalsDatabase.getInstance(context).getEncoderLogicDao(),
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBackgroundExecutor(),
                        CacheProviderFactory.createNoOpCache()),
                AdServicesExecutors.getBackgroundExecutor());
    }

    /**
     * When requested to update encoder for a buyer, following events take place
     *
     * <ol>
     *   <li>1. Fetch the encoding URI from {@link EncoderEndpointsDao}
     *   <li>2. Make a web request using {@link AdServicesHttpsClient} to download the encoder
     *   <li>3. Extract the encoder from the web-response and persist
     *       <ol>
     *         <li>3a. The encoder body is persisted in file storage using {@link
     *             EncoderPersistenceDao}
     *         <li>3b. The entry for the downloaded encoder and the version is persisted using
     *             {@link EncoderLogicDao}
     *       </ol>
     * </ol>
     *
     * @param buyer The buyer for which the encoder logic is required to be updated
     * @param devContext development context used for testing network calls
     * @return a Fluent Future with success or failure in the form of boolean
     */
    public FluentFuture<Boolean> downloadAndUpdate(
            @NonNull AdTechIdentifier buyer, @NonNull DevContext devContext) {
        Objects.requireNonNull(buyer);

        DBEncoderEndpoint encoderEndpoint = mEncoderEndpointsDao.getEndpoint(buyer);
        if (encoderEndpoint == null) {
            sLogger.v(
                    String.format(
                            "No encoder endpoint found for buyer: %s, skipping download and update",
                            buyer));
            return FluentFuture.from(Futures.immediateFuture(false));
        }

        AdServicesHttpClientRequest downloadRequest =
                AdServicesHttpClientRequest.builder()
                        .setUri(encoderEndpoint.getDownloadUri())
                        .setUseCache(false)
                        .setResponseHeaderKeys(mDownloadRequestProperties)
                        .setDevContext(devContext)
                        .build();
        sLogger.v(
                "Initiating encoder download request for buyer: %s, uri: %s",
                buyer, encoderEndpoint.getDownloadUri());
        FluentFuture<AdServicesHttpClientResponse> response =
                FluentFuture.from(mAdServicesHttpsClient.fetchPayload(downloadRequest));

        return response.transform(
                r -> extractAndPersistEncoder(buyer, r), mBackgroundExecutorService);
    }

    @VisibleForTesting
    protected boolean extractAndPersistEncoder(
            AdTechIdentifier buyer, AdServicesHttpClientResponse response) {

        if (response == null || response.getResponseBody().isEmpty()) {
            sLogger.e("Empty response from from client for downloading encoder");
            return false;
        }

        String encoderLogicBody = response.getResponseBody();

        int version = FALLBACK_VERSION;
        try {
            if (response.getResponseHeaders() != null
                    && response.getResponseHeaders().get(ENCODER_VERSION_RESPONSE_HEADER) != null) {
                version =
                        Integer.valueOf(
                                response.getResponseHeaders()
                                        .get(ENCODER_VERSION_RESPONSE_HEADER)
                                        .get(0));
            }

        } catch (NumberFormatException e) {
            sLogger.e("Invalid or missing version, setting to fallback: " + FALLBACK_VERSION);
        }

        DBEncoderLogic encoderLogicEntry =
                DBEncoderLogic.builder()
                        .setBuyer(buyer)
                        .setCreationTime(Instant.now())
                        .setVersion(version)
                        .build();
        boolean updateSucceeded = false;

        ReentrantLock buyerLock = getBuyerLock(buyer);
        if (buyerLock.tryLock()) {
            updateSucceeded = mEncoderPersistenceDao.persistEncoder(buyer, encoderLogicBody);

            if (updateSucceeded) {
                sLogger.v(
                        "Update for encoding logic on persistence layer succeeded, updating DB"
                                + " entry");
                mEncoderLogicDao.persistEncoder(encoderLogicEntry);
            } else {
                sLogger.e(
                        "Update for encoding logic on persistence layer failed, skipping update"
                                + " entry");
            }
            buyerLock.unlock();
        }
        return updateSucceeded;
    }

    @VisibleForTesting
    protected ReentrantLock getBuyerLock(AdTechIdentifier buyer) {
        synchronized (mBuyerTransactionLocks) {
            ReentrantLock lock = mBuyerTransactionLocks.get(buyer);
            if (lock == null) {
                lock = new ReentrantLock();
                mBuyerTransactionLocks.put(buyer, lock);
            }
            return lock;
        }
    }
}
