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

package com.android.adservices.service.adselection.encryption;

import static com.android.adservices.service.adselection.encryption.AdSelectionEncryptionKey.VALID_AD_SELECTION_ENCRYPTION_KEY_TYPES;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__AD_SELECTION_ENCRYPTION_KEY_MANAGER_NULL_FETCH_URI;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_DATABASE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_KEY_FETCH_SOURCE_AUCTION;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.DBEncryptionKey;
import com.android.adservices.data.adselection.EncryptionKeyConstants;
import com.android.adservices.data.adselection.EncryptionKeyDao;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.ohttp.ObliviousHttpKeyConfig;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.profiling.Tracing;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.FetchProcessLogger;
import com.android.adservices.service.stats.ServerAuctionKeyFetchExecutionLoggerFactory;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FluentFuture;

import java.security.spec.InvalidKeySpecException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Class to manage key fetch. */
public class AdSelectionEncryptionKeyManager extends ProtectedServersEncryptionConfigManagerBase {
    private final EncryptionKeyDao mEncryptionKeyDao;

    public AdSelectionEncryptionKeyManager(
            @NonNull EncryptionKeyDao encryptionKeyDao,
            @NonNull Flags flags,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull ExecutorService lightweightExecutor,
            @NonNull AdServicesLogger adServicesLogger) {
        super(
                flags,
                Clock.systemUTC(),
                new AuctionEncryptionKeyParser(flags),
                new JoinEncryptionKeyParser(flags),
                adServicesHttpsClient,
                lightweightExecutor,
                adServicesLogger);

        Objects.requireNonNull(encryptionKeyDao);
        mEncryptionKeyDao = encryptionKeyDao;
    }

    @VisibleForTesting
    AdSelectionEncryptionKeyManager(
            @NonNull EncryptionKeyDao encryptionKeyDao,
            @NonNull Flags flags,
            @NonNull Clock clock,
            @NonNull AuctionEncryptionKeyParser auctionEncryptionKeyParser,
            @NonNull JoinEncryptionKeyParser joinEncryptionKeyParser,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull ExecutorService lightweightExecutor,
            @NonNull AdServicesLogger adServicesLogger) {
        super(
                flags,
                clock,
                auctionEncryptionKeyParser,
                joinEncryptionKeyParser,
                adServicesHttpsClient,
                lightweightExecutor,
                adServicesLogger);
        mEncryptionKeyDao = encryptionKeyDao;
    }

    /**
     * Returns an ObliviousHttpKeyConfig consisting of the latest active key of keyType. Can return
     * null if no active keys are available.
     */
    @Nullable
    public FluentFuture<ObliviousHttpKeyConfig> getLatestActiveOhttpKeyConfigOfType(
            @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType int adSelectionEncryptionKeyType,
            long timeoutMs,
            DevContext devContext,
            FetchProcessLogger keyFetchLogger) {
        return FluentFuture.from(
                        immediateFuture(getLatestActiveKeyOfType(adSelectionEncryptionKeyType)))
                .transformAsync(
                        encryptionKey ->
                                encryptionKey == null
                                        ? fetchPersistAndGetActiveKeyOfType(
                                                adSelectionEncryptionKeyType,
                                                timeoutMs,
                                                devContext,
                                                keyFetchLogger)
                                        : immediateFuture(encryptionKey),
                        mLightweightExecutor)
                .transform(
                        key -> {
                            try {
                                return getOhttpKeyConfigForKey(key);
                            } catch (InvalidKeySpecException e) {
                                // TODO(b/286839408): Delete all keys of given keyType if they
                                //  can't be parsed into key config.
                                throw new IllegalStateException(
                                        "Unable to parse the key into ObliviousHttpKeyConfig.");
                            }
                        },
                        mLightweightExecutor);
    }

    /**
     * Returns an ObliviousHttpKeyConfig consisting of the latest key of keyType. Can return null if
     * no keys are available. Ignores the passed coordinator in this implementation.
     */
    @Nullable
    public FluentFuture<ObliviousHttpKeyConfig> getLatestOhttpKeyConfigOfType(
            @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType int adSelectionEncryptionKeyType,
            long timeoutMs,
            @Nullable Uri coordinatorUrl,
            DevContext devContext) {
        sLogger.v("Ignoring the coordinatorUrl passed, if any.");
        ServerAuctionKeyFetchExecutionLoggerFactory serverAuctionKeyFetchExecutionLoggerFactory =
                new ServerAuctionKeyFetchExecutionLoggerFactory(
                        com.android.adservices.shared.util.Clock.getInstance(),
                        mAdServicesLogger,
                        mFlags);
        FetchProcessLogger keyFetchLogger =
                serverAuctionKeyFetchExecutionLoggerFactory.getAdsRelevanceExecutionLogger();
        keyFetchLogger.setSource(SERVER_AUCTION_KEY_FETCH_SOURCE_AUCTION);
        keyFetchLogger.setCoordinatorSource(SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT);
        return getLatestOhttpKeyConfigOfType(
                adSelectionEncryptionKeyType, timeoutMs, devContext, keyFetchLogger);
    }

    @Nullable
    private AdSelectionEncryptionKey maybeRefreshAndGetLatestKeyFromDatabase(
            @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType int adSelectionEncryptionKeyType,
            @NonNull FetchProcessLogger fetchProcessLogger) {
        AdSelectionEncryptionKey key =
                mFlags.getFledgeAuctionServerRefreshExpiredKeysDuringAuction()
                        ? getLatestActiveKeyOfType(adSelectionEncryptionKeyType)
                        : getLatestKeyOfType(adSelectionEncryptionKeyType);

        if (key != null) {
            fetchProcessLogger.setEncryptionKeySource(
                    SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_DATABASE);
            fetchProcessLogger.logServerAuctionKeyFetchCalledStatsFromDatabase();
        }
        return key;
    }

    /**
     * Returns an ObliviousHttpKeyConfig consisting of the latest key of keyType. The key might be
     * expired. Can return null if no keys are available.
     */
    @Nullable
    private FluentFuture<ObliviousHttpKeyConfig> getLatestOhttpKeyConfigOfType(
            @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType int adSelectionEncryptionKeyType,
            long timeoutMs,
            DevContext devContext,
            FetchProcessLogger keyFetchLogger) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.GET_LATEST_OHTTP_KEY_CONFIG);
        return FluentFuture.from(
                        immediateFuture(
                                maybeRefreshAndGetLatestKeyFromDatabase(
                                        adSelectionEncryptionKeyType, keyFetchLogger)))
                .transformAsync(
                        encryptionKey ->
                                encryptionKey == null
                                        ? fetchPersistAndGetActiveKeyOfType(
                                                adSelectionEncryptionKeyType,
                                                timeoutMs,
                                                devContext,
                                                keyFetchLogger)
                                        : immediateFuture(encryptionKey),
                        mLightweightExecutor)
                .transform(
                        key -> {
                            try {
                                ObliviousHttpKeyConfig configKey = getOhttpKeyConfigForKey(key);
                                Tracing.endAsyncSection(
                                        Tracing.GET_LATEST_OHTTP_KEY_CONFIG, traceCookie);
                                return configKey;
                            } catch (InvalidKeySpecException e) {
                                // TODO(b/286839408): Delete all keys of given keyType if they
                                //  can't be parsed into key config.
                                Tracing.endAsyncSection(
                                        Tracing.GET_LATEST_OHTTP_KEY_CONFIG, traceCookie);
                                throw new IllegalStateException(
                                        "Unable to parse the key into ObliviousHttpKeyConfig.");
                            }
                        },
                        mLightweightExecutor);
    }

    /**
     * Returns the latest active key of keyType JOIN. Can return null if no active keys are
     * available.
     */
    @Nullable
    public AdSelectionEncryptionKey getLatestActiveKeyOfType(
            @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType
                    int adSelectionEncryptionKeyType) {
        sLogger.d("Getting latest encryption key from database excluding expired keys");

        List<DBEncryptionKey> keys =
                mEncryptionKeyDao.getLatestExpiryNActiveKeysOfType(
                        EncryptionKeyConstants.from(adSelectionEncryptionKeyType),
                        mClock.instant(),
                        getKeyCountForType(adSelectionEncryptionKeyType));

        return keys.isEmpty() ? null : selectRandomDbKeyAndParse(keys);
    }

    /**
     * Returns one of the latest key of given keyType.
     *
     * <p>Multiple keys of a given keyType with the same expiry are present in the Database. This
     * method selects one of the keys and returns it. Can return keys which have expired which could
     * be useful if no active keys are available. If null is returned, then no keys are available in
     * the db.
     */
    @Nullable
    public AdSelectionEncryptionKey getLatestKeyOfType(
            @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType
                    int adSelectionEncryptionKeyType) {
        sLogger.d("Getting latest encryption key from database including expired keys");

        List<DBEncryptionKey> keys =
                mEncryptionKeyDao.getLatestExpiryNKeysOfType(
                        EncryptionKeyConstants.from(adSelectionEncryptionKeyType),
                        getKeyCountForType(adSelectionEncryptionKeyType));

        return keys.isEmpty() ? null : selectRandomDbKeyAndParse(keys);
    }

    /**
     * For given AdSelectionKeyType, this method does the following - 1. Fetches the active key from
     * the server. 2. Once the active keys are fetched, it persists the fetched key to
     * db_encryption_key table. 3. Deletes the expired keys of given type. 4. Returns one of the
     * latest active key.
     */
    public FluentFuture<AdSelectionEncryptionKey> fetchPersistAndGetActiveKeyOfType(
            @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType int adSelectionKeyType,
            long timeoutMs,
            DevContext devContext,
            FetchProcessLogger keyFetchLogger) {
        Instant fetchInstant = mClock.instant();
        return fetchAndPersistActiveKeysOfType(
                        adSelectionKeyType,
                        fetchInstant,
                        timeoutMs,
                        null,
                        devContext,
                        keyFetchLogger)
                .transform(keys -> selectRandomDbKeyAndParse(keys), mLightweightExecutor);
    }

    /**
     * For given AdSelectionKeyType, this method does the following - 1. Fetches the active key from
     * the server. 2. Once the active keys are fetched, it persists the fetched key to
     * db_encryption_key table. 3. Deletes the expired keys of given type and which expired at the
     * given instant.
     */
    public FluentFuture<List<DBEncryptionKey>> fetchAndPersistActiveKeysOfType(
            @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType int adSelectionKeyType,
            Instant keyExpiryInstant,
            long timeoutMs,
            @Nullable Uri unusedCoordinatorUrl,
            DevContext devContext,
            FetchProcessLogger keyFetchLogger) {
        Uri fetchUri = getKeyFetchUriOfType(adSelectionKeyType, null, null, keyFetchLogger);
        if (fetchUri == null) {
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__AD_SELECTION_ENCRYPTION_KEY_MANAGER_NULL_FETCH_URI,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE);
            throw new IllegalStateException(
                    "Uri to fetch active key of type " + adSelectionKeyType + " is null.");
        }

        return FluentFuture.from(
                        fetchKeyPayload(adSelectionKeyType, fetchUri, devContext, keyFetchLogger))
                .transform(
                        response -> parseKeyResponse(response, adSelectionKeyType),
                        mLightweightExecutor)
                .transform(
                        result -> {
                            sLogger.d("Persisting " + result.size() + " fetched active keys.");

                            mEncryptionKeyDao.insertAllKeys(result);
                            mEncryptionKeyDao.deleteExpiredRowsByType(
                                    adSelectionKeyType, keyExpiryInstant);
                            return result;
                        },
                        mLightweightExecutor)
                .withTimeout(timeoutMs, TimeUnit.MILLISECONDS, AdServicesExecutors.getScheduler());
    }

    /** Returns the AdSelectionEncryptionKeyType which are expired at the given instant. */
    public Set<Integer> getExpiredAdSelectionEncryptionKeyTypes(Instant keyExpiryInstant) {
        return mEncryptionKeyDao.getExpiredKeys(keyExpiryInstant).stream()
                .map(
                        key ->
                                EncryptionKeyConstants.toAdSelectionEncryptionKeyType(
                                        key.getEncryptionKeyType()))
                .collect(Collectors.toSet());
    }

    /** Returns the AdSelectionEncryptionKeyTypes which are absent. */
    public Set<Integer> getAbsentAdSelectionEncryptionKeyTypes() {
        return VALID_AD_SELECTION_ENCRYPTION_KEY_TYPES.stream()
                .filter(
                        keyType ->
                                mEncryptionKeyDao.getLatestExpiryNKeysOfType(keyType, 1).isEmpty())
                .collect(Collectors.toSet());
    }

    private AdSelectionEncryptionKey selectRandomDbKeyAndParse(List<DBEncryptionKey> keys) {
        Random random = new Random();
        return parseDbEncryptionKey(keys.get(random.nextInt(keys.size())));
    }
}
