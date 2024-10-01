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

package com.android.adservices.service.devapi;

import static java.time.temporal.ChronoUnit.HOURS;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.common.DatabaseClearer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.Executor;

public class DevSessionSetterImpl implements DevSessionSetter {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private final DatabaseClearer mDatabaseClearer;
    private final DevSessionDataStore mDevSessionDataStore;
    private final Executor mLightWeightExecutor;
    private final Clock mClock;

    @VisibleForTesting static final int DAYS_UNTIL_EXPIRY = 365 * 24;

    public DevSessionSetterImpl(
            DatabaseClearer databaseClearer,
            DevSessionDataStore devSessionDataStore,
            Executor lightWeightExecutor,
            Clock clock) {
        mDatabaseClearer = Objects.requireNonNull(databaseClearer);
        mDevSessionDataStore = Objects.requireNonNull(devSessionDataStore);
        mLightWeightExecutor = Objects.requireNonNull(lightWeightExecutor);
        mClock = Objects.requireNonNull(clock);
    }

    @Override
    public ListenableFuture<Boolean> set(boolean setDevSessionEnabled)
            throws IllegalStateException {
        sLogger.d("Beginning DevSessionRefresherImpl.reset()");
        return FluentFuture.from(mDevSessionDataStore.isDevSessionActive())
                .transformAsync(
                        isDevSessionActive -> {
                            sLogger.d(
                                    "isDevSessionActive:%b and setDevSessionEnabled:%b",
                                    isDevSessionActive, setDevSessionEnabled);
                            if (isDevSessionActive == setDevSessionEnabled) {
                                throw new IllegalStateException(
                                        "dev session already set to desired state");
                            } else {
                                ListenableFuture<Void> sessionFuture =
                                        setDevSessionState(setDevSessionEnabled);
                                return FluentFuture.from(sessionFuture)
                                        .transformAsync(
                                                unused -> clearDatabase(), mLightWeightExecutor);
                            }
                        },
                        mLightWeightExecutor);
    }

    private ListenableFuture<Void> setDevSessionState(boolean enable) {
        // TODO(b/368881771): Update this when switching to a transaction model.
        sLogger.d("Beginning setDevSessionState");
        return enable
                ? mDevSessionDataStore.startDevSession(
                        mClock.instant().plus(DAYS_UNTIL_EXPIRY, HOURS))
                : mDevSessionDataStore.endDevSession();
    }

    private ListenableFuture<Boolean> clearDatabase() {
        sLogger.d("Beginning clearDatabase");
        return Futures.transform(
                mDatabaseClearer.deleteProtectedAudienceAndAppSignalsData(
                        /* deleteCustomAudienceUpdate= */ true,
                        /* deleteAppInstallFiltering= */ true,
                        /* deleteProtectedSignals= */ true),
                unused -> true,
                mLightWeightExecutor);
    }
}
