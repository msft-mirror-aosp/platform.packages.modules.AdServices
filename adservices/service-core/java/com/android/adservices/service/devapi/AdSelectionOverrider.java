/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionOverrideCallback;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.FledgeErrorResponse;
import android.annotation.NonNull;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.service.stats.AdServicesLogger;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Objects;
import java.util.concurrent.ExecutorService;

/** Encapsulates the AdSelection Override Logic */
public class AdSelectionOverrider {
    @NonNull private final AdSelectionEntryDao mAdSelectionEntryDao;
    @NonNull private final ListeningExecutorService mListeningExecutorService;
    @NonNull private final AdSelectionDevOverridesHelper mAdSelectionDevOverridesHelper;
    @NonNull private final AdServicesLogger mAdServicesLogger;

    /**
     * Creates an instance of {@link AdSelectionOverrider} with the given {@link DevContext}, {@link
     * AdSelectionEntryDao}, executor, and {@link AdSelectionDevOverridesHelper}.
     */
    public AdSelectionOverrider(
            @NonNull DevContext devContext,
            @NonNull AdSelectionEntryDao adSelectionEntryDao,
            @NonNull ExecutorService executor,
            @NonNull AdServicesLogger adServicesLogger) {
        Objects.requireNonNull(devContext);
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(adServicesLogger);

        this.mAdSelectionEntryDao = adSelectionEntryDao;
        this.mListeningExecutorService = MoreExecutors.listeningDecorator(executor);
        this.mAdSelectionDevOverridesHelper =
                new AdSelectionDevOverridesHelper(devContext, mAdSelectionEntryDao);
        this.mAdServicesLogger = adServicesLogger;
    }

    /**
     * Configures our fetching logic relating to {@code adSelectionConfig} to use {@code
     * decisionLogicJS} instead of fetching from remote servers
     *
     * @param callback callback function to be called in case of success or failure
     */
    public void addOverride(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull String decisionLogicJS,
            @NonNull AdSelectionSignals trustedScoringSignals,
            @NonNull AdSelectionOverrideCallback callback) {
        // Auto-generated variable name is too long for lint check
        int shortApiName =
                AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO;

        callAddOverride(adSelectionConfig, decisionLogicJS, trustedScoringSignals)
                .addCallback(
                        new FutureCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                LogUtil.d("Add dev override succeeded!");
                                invokeSuccess(callback, shortApiName);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                LogUtil.e(t, "Add dev override failed!");
                                notifyFailureToCaller(callback, t, shortApiName);
                            }
                        },
                        mListeningExecutorService);
    }

    /**
     * Removes a decision logic override matching this {@code adSelectionConfig} and {@code
     * appPackageName}
     *
     * @param callback callback function to be called in case of success or failure
     */
    public void removeOverride(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull AdSelectionOverrideCallback callback) {
        // Auto-generated variable name is too long for lint check
        int shortApiName =
                AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE;

        callRemoveOverride(adSelectionConfig)
                .addCallback(
                        new FutureCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                LogUtil.d("Removing dev override succeeded!");
                                invokeSuccess(callback, shortApiName);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                LogUtil.e(t, "Removing dev override failed!");
                                notifyFailureToCaller(callback, t, shortApiName);
                            }
                        },
                        mListeningExecutorService);
    }

    /**
     * Removes all ad selection overrides matching the {@code appPackageName}
     *
     * @param callback callback function to be called in case of success or failure
     */
    public void removeAllOverrides(@NonNull AdSelectionOverrideCallback callback) {
        // Auto-generated variable name is too long for lint check
        int shortApiName =
                AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES;

        callRemoveAllOverrides()
                .addCallback(
                        new FutureCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                LogUtil.d("Removing all dev overrides succeeded!");
                                invokeSuccess(callback, shortApiName);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                LogUtil.e(t, "Removing all dev overrides failed!");
                                notifyFailureToCaller(callback, t, shortApiName);
                            }
                        },
                        mListeningExecutorService);
    }

    private FluentFuture<Void> callAddOverride(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull String decisionLogicJS,
            @NonNull AdSelectionSignals trustedScoringSignals) {
        return FluentFuture.from(
                mListeningExecutorService.submit(
                        () -> {
                            mAdSelectionDevOverridesHelper.addAdSelectionSellerOverride(
                                    adSelectionConfig, decisionLogicJS, trustedScoringSignals);
                            return null;
                        }));
    }

    private FluentFuture<Void> callRemoveOverride(@NonNull AdSelectionConfig adSelectionConfig) {
        return FluentFuture.from(
                mListeningExecutorService.submit(
                        () -> {
                            mAdSelectionDevOverridesHelper.removeAdSelectionSellerOverride(
                                    adSelectionConfig);
                            return null;
                        }));
    }

    private FluentFuture<Void> callRemoveAllOverrides() {
        return FluentFuture.from(
                mListeningExecutorService.submit(
                        () -> {
                            mAdSelectionDevOverridesHelper.removeAllDecisionLogicOverrides();
                            return null;
                        }));
    }

    /** Invokes the onFailure function from the callback and handles the exception. */
    private void invokeFailure(
            @NonNull AdSelectionOverrideCallback callback,
            int statusCode,
            String errorMessage,
            int apiName) {
        int resultCode = statusCode;
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(statusCode)
                            .setErrorMessage(errorMessage)
                            .build());
        } catch (RemoteException e) {
            LogUtil.e(e, "Unable to send failed result to the callback");
            resultCode = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
            throw e.rethrowFromSystemServer();
        } finally {
            mAdServicesLogger.logFledgeApiCallStats(apiName, resultCode);
        }
    }

    /** Invokes the onSuccess function from the callback and handles the exception. */
    private void invokeSuccess(@NonNull AdSelectionOverrideCallback callback, int apiName) {
        int resultCode = AdServicesStatusUtils.STATUS_SUCCESS;
        try {
            callback.onSuccess();
        } catch (RemoteException e) {
            LogUtil.e(e, "Unable to send successful result to the callback");
            resultCode = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
            throw e.rethrowFromSystemServer();
        } finally {
            mAdServicesLogger.logFledgeApiCallStats(apiName, resultCode);
        }
    }

    private void notifyFailureToCaller(
            @NonNull AdSelectionOverrideCallback callback, @NonNull Throwable t, int apiName) {
        if (t instanceof IllegalArgumentException) {
            invokeFailure(
                    callback,
                    AdServicesStatusUtils.STATUS_INVALID_ARGUMENT,
                    t.getMessage(),
                    apiName);
        } else if (t instanceof IllegalStateException) {
            invokeFailure(
                    callback, AdServicesStatusUtils.STATUS_INTERNAL_ERROR, t.getMessage(), apiName);
        } else {
            invokeFailure(
                    callback, AdServicesStatusUtils.STATUS_UNAUTHORIZED, t.getMessage(), apiName);
        }
    }
}
