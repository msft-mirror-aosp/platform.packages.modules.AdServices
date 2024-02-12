/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.kanon;

import android.content.Context;

import androidx.annotation.NonNull;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionServerDatabase;
import com.android.adservices.data.adselection.EncryptionKeyDao;
import com.android.adservices.data.kanon.KAnonDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.encryption.AdSelectionEncryptionKeyManager;
import com.android.adservices.service.adselection.encryption.KAnonObliviousHttpEncryptorImpl;
import com.android.adservices.service.common.SingletonRunner;
import com.android.adservices.service.common.UserProfileIdManager;
import com.android.adservices.service.common.bhttp.BinaryHttpMessageDeserializer;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

public class KAnonSignJoinBackgroundJobWorker {
    public static final String JOB_DESCRIPTION = "FLEDGE KAnon Sign Join Background Job";
    private static final Object SINGLETON_LOCK = new Object();
    private static volatile KAnonSignJoinBackgroundJobWorker sKAnonSignJoinBackgroundJobWorker;

    private final KAnonSignJoinManager mKAnonSignJoinManager;
    private final Flags mFlags;
    private final SingletonRunner<Void> mSingletonRunner =
            new SingletonRunner<>(JOB_DESCRIPTION, this::doRun);

    /**
     * Gets an instance of a {@link KAnonSignJoinBackgroundJobWorker}. If an instance hasn't been
     * initialized, a new singleton will be created and returned.
     */
    public static KAnonSignJoinBackgroundJobWorker getInstance(@NonNull Context context) {
        Objects.requireNonNull(context);
        if (sKAnonSignJoinBackgroundJobWorker == null) {
            synchronized (SINGLETON_LOCK) {
                EncryptionKeyDao encryptionKeyDao =
                        AdSelectionServerDatabase.getInstance(context).encryptionKeyDao();
                Flags flags = FlagsFactory.getFlags();
                int networkConnectTimeout =
                        flags.getFledgeAuctionServerBackgroundKeyFetchNetworkConnectTimeoutMs();
                int newtWorkReadTimeout =
                        flags.getFledgeAuctionServerBackgroundKeyFetchNetworkReadTimeoutMs();
                int keyFetchMaxResponseSize =
                        flags.getFledgeAuctionServerBackgroundKeyFetchMaxResponseSizeB();
                ExecutorService backgroundExecutor = AdServicesExecutors.getBackgroundExecutor();
                AdServicesHttpsClient adServicesHttpsClient =
                        new AdServicesHttpsClient(
                                AdServicesExecutors.getBlockingExecutor(),
                                networkConnectTimeout,
                                newtWorkReadTimeout,
                                keyFetchMaxResponseSize);
                AdSelectionEncryptionKeyManager keyManager =
                        new AdSelectionEncryptionKeyManager(
                                encryptionKeyDao, flags, adServicesHttpsClient, backgroundExecutor);
                KAnonObliviousHttpEncryptorImpl kAnonObliviousHttpEncryptor =
                        new KAnonObliviousHttpEncryptorImpl(keyManager, backgroundExecutor);
                KAnonMessageManager kAnonMessageManager =
                        new KAnonMessageManager(
                                KAnonDatabase.getInstance(context).kAnonMessageDao(),
                                FlagsFactory.getFlags(),
                                Clock.systemUTC());
                sKAnonSignJoinBackgroundJobWorker =
                        new KAnonSignJoinBackgroundJobWorker(
                                context,
                                FlagsFactory.getFlags(),
                                new KAnonSignJoinManager(
                                        new KAnonCallerImpl(
                                                backgroundExecutor,
                                                new AnonymousCountingTokensImpl(),
                                                adServicesHttpsClient,
                                                KAnonDatabase.getInstance(context)
                                                        .clientParametersDao(),
                                                KAnonDatabase.getInstance(context)
                                                        .serverParametersDao(),
                                                UserProfileIdManager.getInstance(context),
                                                new BinaryHttpMessageDeserializer(),
                                                flags,
                                                kAnonObliviousHttpEncryptor,
                                                kAnonMessageManager),
                                        kAnonMessageManager,
                                        FlagsFactory.getFlags(),
                                        Clock.systemUTC()));
            }
        }
        return sKAnonSignJoinBackgroundJobWorker;
    }

    @VisibleForTesting
    public KAnonSignJoinBackgroundJobWorker(
            @NonNull Context context,
            @NonNull Flags flags,
            @NonNull KAnonSignJoinManager kAnonSignJoinManager) {
        Objects.requireNonNull(kAnonSignJoinManager);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(context);

        mKAnonSignJoinManager = kAnonSignJoinManager;
        mFlags = flags;
    }

    /**
     * Runs the k-anon sign job.
     *
     * @return A future to be used to check when the task has completed.
     */
    public FluentFuture<Void> runSignJoinBackgroundProcess() {
        return mSingletonRunner.runSingleInstance();
    }

    private FluentFuture<Void> doRun(@NonNull Supplier<Boolean> shouldStop) {
        if (shouldStop.get()) {
            return FluentFuture.from(Futures.immediateVoidFuture());
        }
        return FluentFuture.from(Futures.immediateVoidFuture())
                .transform(
                        ignored -> {
                            mKAnonSignJoinManager.processMessagesFromDatabase(
                                    mFlags.getFledgeKAnonMessagesPerBackgroundProcess());
                            return null;
                        },
                        AdServicesExecutors.getLightWeightExecutor());
    }

    /** Requests that any ongoing work be stopped. */
    public void stopWork() {
        mSingletonRunner.stopWork();
    }
}
