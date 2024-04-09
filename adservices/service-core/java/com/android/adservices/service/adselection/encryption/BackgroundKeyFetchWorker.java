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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.BACKGROUND_KEY_FETCH_STATUS_NO_OP;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.BACKGROUND_KEY_FETCH_STATUS_REFRESH_KEYS_INITIATED;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_COORDINATOR_SOURCE_API;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_KEY_FETCH_SOURCE_BACKGROUND_FETCH;

import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.DBEncryptionKey;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.MultiCloudSupportStrategyFactory;
import com.android.adservices.service.common.AllowLists;
import com.android.adservices.service.common.SingletonRunner;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdsRelevanceStatusUtils;
import com.android.adservices.service.stats.FetchProcessLogger;
import com.android.adservices.service.stats.ServerAuctionBackgroundKeyFetchScheduledStats;
import com.android.adservices.service.stats.ServerAuctionKeyFetchExecutionLoggerFactory;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.ExecutionSequencer;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Worker instance for fetching encryption keys and persisting to DB. */
public class BackgroundKeyFetchWorker {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    public static final String JOB_DESCRIPTION = "Ad selection data encryption key fetch job";
    private static final Object SINGLETON_LOCK = new Object();
    private static volatile BackgroundKeyFetchWorker sBackgroundKeyFetchWorker;
    private final ProtectedServersEncryptionConfigManagerBase mKeyConfigManager;
    private final Flags mFlags;
    private final Clock mClock;
    private final AdServicesLogger mAdServicesLogger;
    private final SingletonRunner<Void> mSingletonRunner =
            new SingletonRunner<>(JOB_DESCRIPTION, this::doRun);

    @VisibleForTesting
    protected BackgroundKeyFetchWorker(
            @NonNull ProtectedServersEncryptionConfigManagerBase keyConfigManager,
            @NonNull Flags flags,
            @NonNull Clock clock,
            @NonNull AdServicesLogger adServicesLogger) {
        Objects.requireNonNull(keyConfigManager);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(clock);
        Objects.requireNonNull(adServicesLogger);
        mKeyConfigManager = keyConfigManager;
        mClock = clock;
        mFlags = flags;
        mAdServicesLogger = adServicesLogger;
    }

    /**
     * Gets an instance of a {@link BackgroundKeyFetchWorker}. If an instance hasn't been
     * initialized, a new singleton will be created and returned.
     */
    @NonNull
    public static BackgroundKeyFetchWorker getInstance(@NonNull Context context) {
        Objects.requireNonNull(context);

        if (sBackgroundKeyFetchWorker == null) {
            synchronized (SINGLETON_LOCK) {
                if (sBackgroundKeyFetchWorker == null) {
                    Flags flags = FlagsFactory.getFlags();
                    AdServicesHttpsClient adServicesHttpsClient =
                            new AdServicesHttpsClient(
                                    AdServicesExecutors.getBlockingExecutor(),
                                    flags
                                            .getFledgeAuctionServerBackgroundKeyFetchNetworkConnectTimeoutMs(),
                                    flags
                                            .getFledgeAuctionServerBackgroundKeyFetchNetworkReadTimeoutMs(),
                                    flags.getFledgeAuctionServerBackgroundKeyFetchMaxResponseSizeB());
                    ProtectedServersEncryptionConfigManagerBase configManager =
                            MultiCloudSupportStrategyFactory.getStrategy(
                                            flags.getFledgeAuctionServerMultiCloudEnabled(),
                                            flags.getFledgeAuctionServerCoordinatorUrlAllowlist())
                                    .getEncryptionConfigManager(
                                            context, flags, adServicesHttpsClient);
                    sBackgroundKeyFetchWorker =
                            new BackgroundKeyFetchWorker(
                                    configManager,
                                    flags,
                                    Clock.systemUTC(),
                                    AdServicesLoggerImpl.getInstance());
                }
            }
        }
        return sBackgroundKeyFetchWorker;
    }

    public Flags getFlags() {
        return mFlags;
    }

    private Set<Integer> concatAbsentAndExpiredKeyTypes(Instant keyExpiryInstant) {
        return Stream.concat(
                        mKeyConfigManager
                                .getExpiredAdSelectionEncryptionKeyTypes(keyExpiryInstant)
                                .stream(),
                        mKeyConfigManager.getAbsentAdSelectionEncryptionKeyTypes().stream())
                .collect(Collectors.toSet());
    }

    private FluentFuture<Set<Integer>> getAbsentAndExpiredKeyTypes(Instant keyExpiryInstant) {
        return FluentFuture.from(
                AdServicesExecutors.getBackgroundExecutor()
                        .submit(() -> concatAbsentAndExpiredKeyTypes(keyExpiryInstant)));
    }

    private FluentFuture<Set<Integer>> getExpiredKeyTypes(Instant keyExpiryInstant) {
        return FluentFuture.from(
                AdServicesExecutors.getBackgroundExecutor()
                        .submit(
                                () ->
                                        mKeyConfigManager.getExpiredAdSelectionEncryptionKeyTypes(
                                                keyExpiryInstant)));
    }

    private FluentFuture<Void> fetchNewKeys(
            Set<Integer> expiredKeyTypes, Instant keyExpiryInstant, Supplier<Boolean> shouldStop) {
        if (expiredKeyTypes.isEmpty()) {
            if (mFlags.getFledgeAuctionServerKeyFetchMetricsEnabled()) {
                mAdServicesLogger.logServerAuctionBackgroundKeyFetchScheduledStats(
                        ServerAuctionBackgroundKeyFetchScheduledStats.builder()
                                .setStatus(BACKGROUND_KEY_FETCH_STATUS_NO_OP)
                                .setCountAuctionUrls(0)
                                .setCountJoinUrls(0)
                                .build());
            }

            return FluentFuture.from(Futures.immediateVoidFuture())
                    .transform(ignored -> null, AdServicesExecutors.getLightWeightExecutor());
        }

        List<ListenableFuture<List<DBEncryptionKey>>> keyFetchFutures = new ArrayList<>();
        int countAuctionUrls = 0;
        int countJoinUrls = 0;

        // Keys are fetched and persisted in sequence to prevent making multiple network
        // calls in parallel.
        ExecutionSequencer sequencer = ExecutionSequencer.create();
        ServerAuctionKeyFetchExecutionLoggerFactory serverAuctionKeyFetchExecutionLoggerFactory =
                new ServerAuctionKeyFetchExecutionLoggerFactory(
                        com.android.adservices.shared.util.Clock.getInstance(),
                        mAdServicesLogger,
                        mFlags);
        FetchProcessLogger keyFetchLogger =
                serverAuctionKeyFetchExecutionLoggerFactory.getAdsRelevanceExecutionLogger();
        keyFetchLogger.setSource(SERVER_AUCTION_KEY_FETCH_SOURCE_BACKGROUND_FETCH);

        if (mFlags.getFledgeAuctionServerBackgroundAuctionKeyFetchEnabled()
                && expiredKeyTypes.contains(
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION)
                && !shouldStop.get()) {

            boolean multicloudEnabled = mFlags.getFledgeAuctionServerMultiCloudEnabled();
            String allowlist = mFlags.getFledgeAuctionServerCoordinatorUrlAllowlist();

            if (multicloudEnabled && !Strings.isNullOrEmpty(allowlist)) {
                List<String> allowedUrls = AllowLists.splitAllowList(allowlist);
                countAuctionUrls = allowedUrls.size();
                keyFetchLogger.setCoordinatorSource(SERVER_AUCTION_COORDINATOR_SOURCE_API);
                for (String coordinator : allowedUrls) {
                    keyFetchFutures.add(
                            fetchAndPersistAuctionKeys(
                                    keyExpiryInstant,
                                    sequencer,
                                    Uri.parse(coordinator),
                                    keyFetchLogger));
                }
            } else {
                String defaultUrl = mFlags.getFledgeAuctionServerAuctionKeyFetchUri();
                if (defaultUrl != null) {
                    countAuctionUrls = 1;
                    keyFetchLogger.setCoordinatorSource(SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT);
                    keyFetchFutures.add(
                            fetchAndPersistAuctionKeys(
                                    keyExpiryInstant,
                                    sequencer,
                                    Uri.parse(defaultUrl),
                                    keyFetchLogger));
                }
            }
        }

        if (mFlags.getFledgeAuctionServerBackgroundJoinKeyFetchEnabled()
                && expiredKeyTypes.contains(
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN)
                && !shouldStop.get()) {
            countJoinUrls = 1;
            keyFetchLogger.setCoordinatorSource(SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT);
            keyFetchFutures.add(
                    fetchAndPersistJoinKey(keyExpiryInstant, sequencer, keyFetchLogger));
        }

        if (mFlags.getFledgeAuctionServerKeyFetchMetricsEnabled()) {
            @AdsRelevanceStatusUtils.BackgroundKeyFetchStatus
            int status =
                    countAuctionUrls + countJoinUrls > 0
                            ? BACKGROUND_KEY_FETCH_STATUS_REFRESH_KEYS_INITIATED
                            : BACKGROUND_KEY_FETCH_STATUS_NO_OP;

            mAdServicesLogger.logServerAuctionBackgroundKeyFetchScheduledStats(
                    ServerAuctionBackgroundKeyFetchScheduledStats.builder()
                            .setStatus(status)
                            .setCountAuctionUrls(countAuctionUrls)
                            .setCountJoinUrls(countJoinUrls)
                            .build());
        }

        return FluentFuture.from(Futures.allAsList(keyFetchFutures))
                .withTimeout(
                        mFlags.getFledgeAuctionServerBackgroundKeyFetchJobMaxRuntimeMs(),
                        TimeUnit.MILLISECONDS,
                        AdServicesExecutors.getScheduler())
                .transform(ignored -> null, AdServicesExecutors.getLightWeightExecutor());
    }

    private FluentFuture<Void> doRun(@NonNull Supplier<Boolean> shouldStop) {
        if (shouldStop.get()) {
            sLogger.d("Stopping " + JOB_DESCRIPTION);
            return FluentFuture.from(Futures.immediateVoidFuture())
                    .transform(ignored -> null, AdServicesExecutors.getLightWeightExecutor());
        }

        Instant currentInstant = mClock.instant();
        if (mFlags.getFledgeAuctionServerBackgroundKeyFetchOnEmptyDbAndInAdvanceEnabled()) {
            long inAdvanceIntervalMs =
                    mFlags.getFledgeAuctionServerBackgroundKeyFetchInAdvanceIntervalMs();
            return getAbsentAndExpiredKeyTypes(currentInstant.plusMillis(inAdvanceIntervalMs))
                    .transformAsync(
                            keyTypesToFetch ->
                                    fetchNewKeys(keyTypesToFetch, currentInstant, shouldStop),
                            AdServicesExecutors.getBackgroundExecutor());
        }

        return getExpiredKeyTypes(currentInstant)
                .transformAsync(
                        expiredKeyTypes ->
                                fetchNewKeys(expiredKeyTypes, currentInstant, shouldStop),
                        AdServicesExecutors.getBackgroundExecutor());
    }

    /**
     * Runs the background key fetch job for Ad Selection Data, including persisting fetched key and
     * removing expired keys.
     *
     * @return A future to be used to check when the task has completed.
     */
    public FluentFuture<Void> runBackgroundKeyFetch() {
        sLogger.d("Starting %s", JOB_DESCRIPTION);
        return mSingletonRunner.runSingleInstance();
    }

    /** Requests that any ongoing work be stopped gracefully and waits for work to be stopped. */
    public void stopWork() {
        mSingletonRunner.stopWork();
    }

    private ListenableFuture<List<DBEncryptionKey>> fetchAndPersistAuctionKeys(
            Instant keyExpiryInstant,
            ExecutionSequencer sequencer,
            Uri coordinatorUri,
            FetchProcessLogger keyFetchLogger) {

        return sequencer.submitAsync(
                () ->
                        mKeyConfigManager.fetchAndPersistActiveKeysOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                keyExpiryInstant,
                                mFlags.getFledgeAuctionServerBackgroundKeyFetchJobMaxRuntimeMs(),
                                coordinatorUri,
                                keyFetchLogger),
                AdServicesExecutors.getBackgroundExecutor());
    }

    private ListenableFuture<List<DBEncryptionKey>> fetchAndPersistJoinKey(
            Instant keyExpiryInstant,
            ExecutionSequencer sequencer,
            FetchProcessLogger keyFetchLogger) {
        return sequencer.submitAsync(
                () ->
                        mKeyConfigManager.fetchAndPersistActiveKeysOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN,
                                keyExpiryInstant,
                                mFlags.getFledgeAuctionServerBackgroundKeyFetchJobMaxRuntimeMs(),
                                null,
                                keyFetchLogger),
                AdServicesExecutors.getBackgroundExecutor());
    }
}
