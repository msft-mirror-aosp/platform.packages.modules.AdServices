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

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionOverrideCallback;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.FledgeErrorResponse;
import android.annotation.NonNull;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.data.adselection.AdSelectionEntryDao;

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

    /**
     * Creates an instance of {@link AdSelectionOverrider} with the given {@link DevContext}, {@link
     * AdSelectionEntryDao}, executor, and {@link AdSelectionDevOverridesHelper}.
     */
    public AdSelectionOverrider(
            @NonNull DevContext devContext,
            @NonNull AdSelectionEntryDao adSelectionEntryDao,
            @NonNull ExecutorService executor) {
        Objects.requireNonNull(devContext);
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(executor);

        this.mAdSelectionEntryDao = adSelectionEntryDao;
        this.mListeningExecutorService = MoreExecutors.listeningDecorator(executor);
        this.mAdSelectionDevOverridesHelper =
                new AdSelectionDevOverridesHelper(devContext, mAdSelectionEntryDao);
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
            @NonNull AdSelectionOverrideCallback callback) {

        callAddOverride(adSelectionConfig, decisionLogicJS)
                .addCallback(
                        new FutureCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                LogUtil.d("Add dev override succeeded!");
                                invokeSuccess(callback);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                LogUtil.e(t, "Add dev override failed!");
                                notifyFailureToCaller(callback, t);
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

        callRemoveOverride(adSelectionConfig)
                .addCallback(
                        new FutureCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                LogUtil.d("Removing dev override succeeded!");
                                invokeSuccess(callback);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                LogUtil.e(t, "Removing dev override failed!");
                                notifyFailureToCaller(callback, t);
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

        callRemoveAllOverrides()
                .addCallback(
                        new FutureCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                LogUtil.d("Removing all dev overrides succeeded!");
                                invokeSuccess(callback);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                LogUtil.e(t, "Removing all dev overrides failed!");
                                notifyFailureToCaller(callback, t);
                            }
                        },
                        mListeningExecutorService);
    }

    private FluentFuture<Void> callAddOverride(
            @NonNull AdSelectionConfig adSelectionConfig, @NonNull String decisionLogicJS) {
        return FluentFuture.from(
                mListeningExecutorService.submit(
                        () -> {
                            mAdSelectionDevOverridesHelper.addDecisionLogicOverride(
                                    adSelectionConfig, decisionLogicJS);
                            return null;
                        }));
    }

    private FluentFuture<Void> callRemoveOverride(@NonNull AdSelectionConfig adSelectionConfig) {
        return FluentFuture.from(
                mListeningExecutorService.submit(
                        () -> {
                            mAdSelectionDevOverridesHelper.removeDecisionLogicOverride(
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
    private static void invokeFailure(
            @NonNull AdSelectionOverrideCallback callback, int statusCode, String errorMessage) {
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(statusCode)
                            .setErrorMessage(errorMessage)
                            .build());
        } catch (RemoteException e) {
            LogUtil.e("Unable to send failed result to the callback", e);
            throw e.rethrowFromSystemServer();
        }
    }

    /** Invokes the onSuccess function from the callback and handles the exception. */
    private static void invokeSuccess(@NonNull AdSelectionOverrideCallback callback) {
        try {
            callback.onSuccess();
        } catch (RemoteException e) {
            LogUtil.e("Unable to send successful result to the callback", e);
            throw e.rethrowFromSystemServer();
        }
    }

    private void notifyFailureToCaller(
            @NonNull AdSelectionOverrideCallback callback, @NonNull Throwable t) {
        if (t instanceof IllegalArgumentException) {
            invokeFailure(callback, AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, t.getMessage());
        } else if (t instanceof IllegalStateException) {
            invokeFailure(callback, AdServicesStatusUtils.STATUS_INTERNAL_ERROR, t.getMessage());
        } else {
            invokeFailure(callback, AdServicesStatusUtils.STATUS_UNAUTHORIZED, t.getMessage());
        }
    }
}
