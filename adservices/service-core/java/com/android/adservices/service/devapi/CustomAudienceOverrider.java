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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudienceOverrideCallback;
import android.adservices.exceptions.ApiNotAuthorizedException;
import android.annotation.NonNull;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.service.stats.AdServicesLogger;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Objects;
import java.util.concurrent.ExecutorService;

/** Encapsulates the Custom Audience Override Logic */
public class CustomAudienceOverrider {
    @NonNull private final CustomAudienceDao mCustomAudienceDao;
    @NonNull private final ListeningExecutorService mListeningExecutorService;
    @NonNull private final CustomAudienceDevOverridesHelper mCustomAudienceDevOverridesHelper;
    @NonNull private final AdServicesLogger mAdServicesLogger;

    /**
     * Creates an instance of {@link CustomAudienceOverrider} with the given {@link DevContext},
     * {@link CustomAudienceDao}, executor, and {@link CustomAudienceDevOverridesHelper}.
     */
    public CustomAudienceOverrider(
            @NonNull DevContext devContext,
            @NonNull CustomAudienceDao customAudienceDao,
            @NonNull ExecutorService executorService,
            @NonNull AdServicesLogger adServicesLogger) {
        Objects.requireNonNull(devContext);
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(executorService);
        Objects.requireNonNull(adServicesLogger);

        this.mCustomAudienceDao = customAudienceDao;
        this.mListeningExecutorService = MoreExecutors.listeningDecorator(executorService);
        this.mCustomAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(devContext, mCustomAudienceDao);
        this.mAdServicesLogger = adServicesLogger;
    }

    /**
     * Configures our fetching logic relating to the combination of {@code owner}, {@code buyer},
     * and {@code name} to use {@code biddingLogicJS} and {@code trustedBiddingData} instead of
     * fetching from remote servers
     *
     * @param callback callback function to be called in case of success or failure
     */
    public void addOverride(
            @NonNull String owner,
            @NonNull String buyer,
            @NonNull String name,
            @NonNull String biddingLogicJS,
            @NonNull String trustedBiddingData,
            @NonNull CustomAudienceOverrideCallback callback) {
        // Auto-generated variable name is too long for lint check
        int shortApiName = AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO;

        callAddOverride(owner, buyer, name, biddingLogicJS, trustedBiddingData)
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
     * Removes a bidding logic override matching the combination of {@code owner}, {@code buyer},
     * {@code name} and {@code appPackageName}
     *
     * @param callback callback function to be called in case of success or failure
     */
    public void removeOverride(
            @NonNull String owner,
            @NonNull String buyer,
            @NonNull String name,
            @NonNull CustomAudienceOverrideCallback callback) {
        // Auto-generated variable name is too long for lint check
        int shortApiName =
                AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE;

        callRemoveOverride(owner, buyer, name)
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
     * Removes all custom audience overrides matching the {@code appPackageName}
     *
     * @param callback callback function to be called in case of success or failure
     */
    public void removeAllOverrides(@NonNull CustomAudienceOverrideCallback callback) {
        // Auto-generated variable name is too long for lint check
        int shortApiName = AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES;

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
            @NonNull String owner,
            @NonNull String buyer,
            @NonNull String name,
            @NonNull String biddingLogicJS,
            @NonNull String trustedBiddingData) {
        return FluentFuture.from(
                mListeningExecutorService.submit(
                        () -> {
                            mCustomAudienceDevOverridesHelper.addOverride(
                                    owner, buyer, name, biddingLogicJS, trustedBiddingData);
                            return null;
                        }));
    }

    private FluentFuture<Void> callRemoveOverride(
            @NonNull String owner, @NonNull String buyer, @NonNull String name) {
        return FluentFuture.from(
                mListeningExecutorService.submit(
                        () -> {
                            mCustomAudienceDevOverridesHelper.removeOverride(owner, buyer, name);
                            return null;
                        }));
    }

    private FluentFuture<Void> callRemoveAllOverrides() {
        return FluentFuture.from(
                mListeningExecutorService.submit(
                        () -> {
                            mCustomAudienceDevOverridesHelper.removeAllOverrides();
                            return null;
                        }));
    }

    /** Invokes the onFailure function from the callback and handles the exception. */
    private void invokeFailure(
            @NonNull CustomAudienceOverrideCallback callback,
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
            LogUtil.e("Unable to send failed result to the callback", e);
            resultCode = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
            throw e.rethrowFromSystemServer();
        } finally {
            mAdServicesLogger.logFledgeApiCallStats(apiName, resultCode);
        }
    }

    /** Invokes the onSuccess function from the callback and handles the exception. */
    private void invokeSuccess(@NonNull CustomAudienceOverrideCallback callback, int apiName) {
        int resultCode = AdServicesStatusUtils.STATUS_SUCCESS;
        try {
            callback.onSuccess();
        } catch (RemoteException e) {
            LogUtil.e("Unable to send successful result to the callback", e);
            resultCode = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
            throw e.rethrowFromSystemServer();
        } finally {
            mAdServicesLogger.logFledgeApiCallStats(apiName, resultCode);
        }
    }

    private void notifyFailureToCaller(
            @NonNull CustomAudienceOverrideCallback callback, @NonNull Throwable t, int apiName) {
        if (t instanceof IllegalArgumentException) {
            invokeFailure(
                    callback,
                    AdServicesStatusUtils.STATUS_INVALID_ARGUMENT,
                    t.getMessage(),
                    apiName);
        } else if (t instanceof ApiNotAuthorizedException) {
            invokeFailure(
                    callback, AdServicesStatusUtils.STATUS_UNAUTHORIZED, t.getMessage(), apiName);
        } else {
            invokeFailure(
                    callback, AdServicesStatusUtils.STATUS_INTERNAL_ERROR, t.getMessage(), apiName);
        }
    }
}
