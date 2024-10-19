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
    public ListenableFuture<DevSessionControllerResult> startDevSession()
            throws IllegalStateException {
        return tryUpdateDevSessionState(true);
    }

    @Override
    public ListenableFuture<DevSessionControllerResult> endDevSession()
            throws IllegalStateException {
        return tryUpdateDevSessionState(false);
    }

    private ListenableFuture<DevSessionControllerResult> tryUpdateDevSessionState(
            boolean setDevSessionEnabled) throws IllegalStateException {
        sLogger.d("Beginning DevSessionControllerImpl.set(%b)", setDevSessionEnabled);
        return FluentFuture.from(mDevSessionDataStore.get())
                .transformAsync(
                        devSession -> {
                            sLogger.d(
                                    "devSession: %s and setDevSessionEnabled: %b",
                                    devSession, setDevSessionEnabled);

                            DevSessionState state = devSession.getState();
                            if ((!setDevSessionEnabled && state == IN_PROD)
                                    || (setDevSessionEnabled && state == IN_DEV)) {
                                return immediateFuture(NO_OP);
                            }
                            // Note that transitory states can go in either direction, so we ignore
                            // them when doing the check below.
                            if (setDevSessionEnabled) {
                                // Note this also handles all the transitory states.
                                return handleProdOrRecoveryToDev();
                            } else {
                                // Otherwise, we are moving from IN_DEV to IN_PROD.
                                return handleDevToProd();
                            }
                        },
                        mLightWeightExecutor);
    }

    @SuppressWarnings("FutureReturnValueIgnored") // TODO(b/331285831): fix this
    private ListenableFuture<DevSessionControllerResult> handleDevToProd() {
        return FluentFuture.from(setDevSessionState(TRANSITIONING_DEV_TO_PROD))
                .transformAsync(this::clearDatabase, mLightWeightExecutor)
                .transformAsync(success -> setDevSessionState(IN_PROD), mLightWeightExecutor)
                .transformAsync(
                        state -> {
                            sLogger.v("completed transition to IN_PROD");
                            return immediateFuture(SUCCESS);
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
    private ListenableFuture<DevSessionControllerResult> handleProdOrRecoveryToDev() {
        return FluentFuture.from(setDevSessionState(TRANSITIONING_PROD_TO_DEV))
                .transformAsync(this::clearDatabase, mLightWeightExecutor)
                .transformAsync(success -> setDevSessionState(IN_DEV), mLightWeightExecutor)
                .transformAsync(
                        state -> {
                            sLogger.v("completed transition to IN_DEV");
                            return immediateFuture(SUCCESS);
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

    private ListenableFuture<DevSession> setDevSessionState(DevSessionState desiredState) {
        sLogger.d("Beginning setDevSessionState(%s)", desiredState);
        return mDevSessionDataStore.set(DevSession.builder().setState(desiredState).build());
    }

    private ListenableFuture<Boolean> clearDatabase(DevSession unused) {
        sLogger.d("Beginning clearDatabase()");
        return mDatabaseClearer.deleteProtectedAudienceAndAppSignalsData(
                /* deleteCustomAudienceUpdate= */ true,
                /* deleteAppInstallFiltering= */ true,
                /* deleteProtectedSignals= */ true);
    }
}
