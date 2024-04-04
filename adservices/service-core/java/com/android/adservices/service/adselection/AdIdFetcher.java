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

import static android.adservices.adid.AdId.ZERO_OUT;

import static androidx.concurrent.futures.CallbackToFutureAdapter.Resolver;

import android.adservices.adid.GetAdIdResult;
import android.adservices.adid.IGetAdIdCallback;
import android.annotation.NonNull;
import android.content.Context;

import androidx.annotation.VisibleForTesting;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.adid.AdIdWorker;
import com.android.adservices.service.common.PermissionHelper;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Class to fetch Ad Id related data required to run ad selection. */
public class AdIdFetcher {
    @VisibleForTesting public static final boolean DEFAULT_IS_LAT_ENABLED = true;
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final Context mContext;
    @NonNull private final AdIdWorker mAdIdWorker;
    @NonNull private final ListeningExecutorService mLightweightExecutorService;
    @NonNull private final ScheduledThreadPoolExecutor mScheduledExecutor;

    /**
     * Default constructor
     *
     * @param adIdWorker Ad Id Worker.
     * @param lightweightExecutorService lightweight executor service
     * @param scheduledExecutor scheduler executor
     */
    public AdIdFetcher(
            @NonNull Context context,
            @NonNull AdIdWorker adIdWorker,
            @NonNull final ExecutorService lightweightExecutorService,
            @NonNull final ScheduledThreadPoolExecutor scheduledExecutor) {
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(adIdWorker, "adIdWorker cannot be null");
        Objects.requireNonNull(
                lightweightExecutorService, "lightweightExecutorService cannot be null");
        Objects.requireNonNull(scheduledExecutor, "scheduledExecutor cannot be null");

        mContext = context;
        mAdIdWorker = adIdWorker;
        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutorService);
        mScheduledExecutor = scheduledExecutor;
    }

    /**
     * Converts callback to a future which determines if limited ad tracking is enabled for the
     * calling packageName and calling Uid. The futures time out after a fixed interval determined
     * by { @link com.android.adservices.service.adselection.AD_ID_TIMEOUT_IN_MS }.
     *
     * @param packageName the packageName
     * @param callingUid the calling Udi
     * @return a future of boolean indicating if limited ad tracking is enabled or disabled.
     */
    public ListenableFuture<Boolean> isLimitedAdTrackingEnabled(
            @NonNull String packageName, int callingUid, long adIdFetcherTimeoutMs) {

        // Check if the permission is declared in the manifest of that package name.
        sLogger.v(
                "Checking if Package %s with calling Uid %d has declared permission for AdId",
                packageName, callingUid);
        boolean hasAdIdPermission =
                PermissionHelper.hasAdIdPermission(mContext, packageName, callingUid);
        if (!hasAdIdPermission) {
            sLogger.v(
                    "Package %s with calling Uid %d has not declared permission for AdId. Returning"
                            + " isLimitedAdTrackingEnabled %b",
                    packageName, callingUid, DEFAULT_IS_LAT_ENABLED);
            return Futures.immediateFuture(DEFAULT_IS_LAT_ENABLED);
        }
        sLogger.v(
                "Package %s with calling Uid %d has declared permission for AdId. Checking if"
                        + " AdId is Zeroed out",
                packageName, callingUid);
        return FluentFuture.from(
                        getFutureWithTimeout(
                                convertToCallback(packageName, callingUid),
                                adIdFetcherTimeoutMs,
                                mScheduledExecutor))
                .catching(
                        TimeoutException.class,
                        timeoutException -> {
                            sLogger.v(
                                    "Timeout after %d ms while calling getAdId api. Returning"
                                            + " isLimitedAdTrackingEnabled %b",
                                    adIdFetcherTimeoutMs, DEFAULT_IS_LAT_ENABLED);
                            return DEFAULT_IS_LAT_ENABLED;
                        },
                        mLightweightExecutorService)
                .catching(
                        IllegalStateException.class,
                        illegalStateException -> {
                            sLogger.v(
                                    "IllegalStateException while calling getAdId api. Returning"
                                            + " isLimitedAdTrackingEnabled %b",
                                    DEFAULT_IS_LAT_ENABLED);
                            return DEFAULT_IS_LAT_ENABLED;
                        },
                        mLightweightExecutorService);
    }

    /**
     * @return a future with timeout after converting the callback to future
     */
    private <T> ListenableFuture<T> getFutureWithTimeout(
            Resolver<T> resolver,
            long timeoutInMs,
            ScheduledExecutorService scheduledExecutorService) {
        SettableFuture<T> out = SettableFuture.create();
        // Start timeout before invoking the callback
        ListenableFuture<T> result =
                Futures.withTimeout(
                        out, timeoutInMs, TimeUnit.MILLISECONDS, scheduledExecutorService);
        out.setFuture(androidx.concurrent.futures.CallbackToFutureAdapter.getFuture(resolver));
        return result;
    }

    private Resolver<Boolean> convertToCallback(String packageName, int callingUid) {
        return completer -> {
            mAdIdWorker.getAdId(
                    packageName,
                    callingUid,
                    new IGetAdIdCallback.Stub() {
                        @Override
                        public void onResult(GetAdIdResult getAdIdResult) {
                            String id = getAdIdResult.getAdId();
                            boolean isLatEnabledResult = getAdIdResult.isLatEnabled();
                            boolean isAdIdZeroedOut = ZERO_OUT.equals(id);
                            sLogger.v(
                                    "isLatEnabled is: %b.", isLatEnabledResult || isAdIdZeroedOut);
                            completer.set(isLatEnabledResult || isAdIdZeroedOut);
                        }

                        @Override
                        public void onError(int i) {
                            sLogger.v(
                                    "Got error calling getAdId API, throwing"
                                            + " IllegalStateException");
                            completer.setException(
                                    new IllegalStateException(
                                            "Failed while trying to call getAdId"));
                        }
                    });
            return "isLimitedAdTrackingEnabled";
        };
    }
}
