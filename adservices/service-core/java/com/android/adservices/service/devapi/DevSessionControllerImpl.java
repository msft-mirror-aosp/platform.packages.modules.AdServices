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

import static com.android.adservices.service.devapi.DevSessionControllerResult.FAILURE;
import static com.android.adservices.service.devapi.DevSessionControllerResult.NO_OP;
import static com.android.adservices.service.devapi.DevSessionControllerResult.SUCCESS;
import static com.android.adservices.service.devapi.DevSessionState.IN_DEV;
import static com.android.adservices.service.devapi.DevSessionState.IN_PROD;
import static com.android.adservices.service.devapi.DevSessionState.TRANSITIONING_DEV_TO_PROD;
import static com.android.adservices.service.devapi.DevSessionState.TRANSITIONING_PROD_TO_DEV;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.common.DatabaseClearer;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Objects;
import java.util.concurrent.Executor;

public final class DevSessionControllerImpl implements DevSessionController {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final boolean PROD_MODE_SERVER_AUCTION_TEST_KEYS_DISABLED = false;

    private final DatabaseClearer mDatabaseClearer;
    private final DevSessionDataStore mDevSessionDataStore;
    private final Executor mLightWeightExecutor;

    public DevSessionControllerImpl(
            DatabaseClearer databaseClearer,
            DevSessionDataStore devSessionDataStore,
            Executor lightWeightExecutor) {
        mDatabaseClearer =
                Objects.requireNonNull(databaseClearer, "DatabaseClearer must not be null");
        mDevSessionDataStore =
                Objects.requireNonNull(devSessionDataStore, "DevSessionDataStore must not be null");
        mLightWeightExecutor =
                Objects.requireNonNull(
                        lightWeightExecutor, "(Lightweight) Executor must not be null");
    }

    @Override
    public ListenableFuture<DevSessionControllerResult> startDevSession(
            boolean setServerAuctionTestKeysEnabled) throws IllegalStateException {
        return tryUpdateDevSession(true, setServerAuctionTestKeysEnabled);
    }

    @Override
    public ListenableFuture<DevSessionControllerResult> endDevSession()
            throws IllegalStateException {
        return tryUpdateDevSession(
                /* setDevSessionEnabled */ false, PROD_MODE_SERVER_AUCTION_TEST_KEYS_DISABLED);
    }

    private ListenableFuture<DevSessionControllerResult> tryUpdateDevSession(
            boolean setDevSessionEnabled, boolean setServerAuctionTestKeysEnabled)
            throws IllegalStateException {
        sLogger.d(
                "Beginning DevSessionControllerImpl.set(%b, %b)",
                setDevSessionEnabled, setServerAuctionTestKeysEnabled);
        return FluentFuture.from(mDevSessionDataStore.get())
                .transformAsync(
                        devSession -> {
                            sLogger.d(
                                    "devSession: %s, setDevSessionEnabled: %b and"
                                            + " setServerAuctionTestKeysEnabled: %b",
                                    devSession,
                                    setDevSessionEnabled,
                                    setServerAuctionTestKeysEnabled);

                            DevSessionState state = devSession.getState();
                            if ((!setDevSessionEnabled && state == IN_PROD)
                                    || (setDevSessionEnabled && state == IN_DEV)) {
                                return immediateFuture(NO_OP);
                            }
                            // Note that transitory states can go in either direction, so we ignore
                            // them when doing the check below.
                            if (setDevSessionEnabled) {
                                // Note this also handles all the transitory states.
                                return handleProdOrRecoveryToDev(setServerAuctionTestKeysEnabled);
                            } else {
                                // Otherwise, we are moving from IN_DEV to IN_PROD.
                                return handleDevToProd();
                            }
                        },
                        mLightWeightExecutor);
    }

    @SuppressWarnings("FutureReturnValueIgnored") // TODO(b/331285831): fix this
    private ListenableFuture<DevSessionControllerResult> handleDevToProd() {
        return FluentFuture.from(
                        setDevSession(
                                TRANSITIONING_DEV_TO_PROD,
                                PROD_MODE_SERVER_AUCTION_TEST_KEYS_DISABLED))
                .transformAsync(this::clearDatabase, mLightWeightExecutor)
                .transformAsync(
                        ignoreVoid ->
                                setDevSession(IN_PROD, PROD_MODE_SERVER_AUCTION_TEST_KEYS_DISABLED),
                        mLightWeightExecutor)
                .transform(
                        state -> {
                            sLogger.v("completed transition to IN_PROD");
                            return SUCCESS;
                        },
                        mLightWeightExecutor)
                .catching(
                        Exception.class,
                        e -> {
                            sLogger.e(e, "failed to move from IN_DEV to IN_PROD");
                            return FAILURE;
                        },
                        mLightWeightExecutor);
    }

    @SuppressWarnings("FutureReturnValueIgnored") // TODO(b/331285831): fix this
    private ListenableFuture<DevSessionControllerResult> handleProdOrRecoveryToDev(
            boolean setServerAuctionTestKeysEnabled) {
        return FluentFuture.from(
                        setDevSession(
                                TRANSITIONING_PROD_TO_DEV,
                                PROD_MODE_SERVER_AUCTION_TEST_KEYS_DISABLED))
                .transformAsync(this::clearDatabase, mLightWeightExecutor)
                .transformAsync(
                        ignoreVoid -> setDevSession(IN_DEV, setServerAuctionTestKeysEnabled),
                        mLightWeightExecutor)
                .transform(
                        state -> {
                            sLogger.v("completed transition to IN_DEV");
                            return SUCCESS;
                        },
                        mLightWeightExecutor)
                .catching(
                        Exception.class,
                        e -> {
                            sLogger.e(e, "failed to move from IN_PROD to IN_DEV");
                            return FAILURE;
                        },
                        mLightWeightExecutor);
    }

    private ListenableFuture<DevSession> setDevSession(
            DevSessionState desiredState, boolean setServerAuctionTestKeysEnabled) {
        sLogger.d(
                "Beginning setDevSession(%s, setServerAuctionTestKeysEnabled: %b)",
                desiredState, setServerAuctionTestKeysEnabled);
        return mDevSessionDataStore.set(
                DevSession.builder()
                        .setState(desiredState)
                        .setServerAuctionTestKeysEnabled(setServerAuctionTestKeysEnabled)
                        .build());
    }

    private ListenableFuture<Void> clearDatabase(DevSession devSession) {
        sLogger.d("Beginning clearDatabase()");
        boolean shouldDeleteEncryptionConfigData =
                devSession.getState() == DevSessionState.TRANSITIONING_DEV_TO_PROD;
        return FluentFuture.from(
                        mDatabaseClearer.deleteProtectedAudienceAppSignalsAndEncryptionConfigData(
                                /* deleteCustomAudienceUpdate= */ true,
                                /* deleteAppInstallFiltering= */ true,
                                /* deleteProtectedSignals= */ true,
                                shouldDeleteEncryptionConfigData))
                .transformAsync(
                        ignoreVoid -> mDatabaseClearer.deleteMeasurementData(),
                        mLightWeightExecutor);
    }
}
