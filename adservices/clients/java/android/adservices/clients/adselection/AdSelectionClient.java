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

package android.adservices.clients.adselection;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionManager;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.AddAdSelectionOverrideRequest;
import android.adservices.adselection.RemoveAdSelectionOverrideRequest;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.exceptions.AdServicesException;
import android.annotation.NonNull;
import android.content.Context;
import android.os.OutcomeReceiver;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Objects;
import java.util.concurrent.Executor;


/**
 * This is the Ad Selection Client which will be used in the cts tests.
 */
// TODO: This should be in JetPack code.
public class AdSelectionClient {
    private AdSelectionManager mAdSelectionManager;
    private Context mContext;
    private Executor mExecutor;

    private AdSelectionClient(@NonNull Context context, @NonNull Executor executor) {
        mContext = context;
        mExecutor = executor;
        mAdSelectionManager = mContext.getSystemService(AdSelectionManager.class);
    }

    /**
     * Invokes the {@code runAdSelection} method of {@link AdSelectionManager}, and returns a future
     * with {@link AdSelectionOutcome} if succeeds, or an {@link AdServicesException} if fails.
     */
    @NonNull
    public ListenableFuture<AdSelectionOutcome> runAdSelection(
            @NonNull AdSelectionConfig adSelectionConfig) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mAdSelectionManager.runAdSelection(
                            adSelectionConfig,
                            mExecutor,
                            new OutcomeReceiver<AdSelectionOutcome, AdServicesException>() {

                                @Override
                                public void onResult(@NonNull AdSelectionOutcome result) {
                                    completer.set(
                                            new AdSelectionOutcome.Builder()
                                                    .setAdSelectionId(result.getAdSelectionId())
                                                    .setRenderUri(result.getRenderUri())
                                                    .build());
                                }

                                @Override
                                public void onError(@NonNull AdServicesException error) {
                                    completer.setException(error);
                                }
                            });
                    return "Ad Selection";
                });
    }

    /**
     * Invokes the {@code reportImpression} method of {@link AdSelectionManager}, and returns a Void
     * future
     */
    @NonNull
    public ListenableFuture<Void> reportImpression(
            @NonNull ReportImpressionRequest input) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mAdSelectionManager.reportImpression(
                            input,
                            mExecutor,
                            new OutcomeReceiver<Void, AdServicesException>() {
                                @Override
                                public void onResult(@NonNull Void result) {
                                    completer.set(result);
                                }

                                @Override
                                public void onError(@NonNull AdServicesException error) {
                                    completer.setException(error);
                                }
                            });
                    return "reportImpression";
                });
    }

    /**
     * Invokes the {@code overrideAdSelectionConfigRemoteInfo} method of {@link AdSelectionManager},
     * and returns a Void future
     *
     * This method is only available when Developer mode is enabled and the app is debuggable.
     *
     * @hide
     */
    @NonNull
    public ListenableFuture<Void> overrideAdSelectionConfigRemoteInfo(
            @NonNull AddAdSelectionOverrideRequest request) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mAdSelectionManager.overrideAdSelectionConfigRemoteInfo(
                            request,
                            mExecutor,
                            new OutcomeReceiver<Void, AdServicesException>() {

                                @Override
                                public void onResult(Void result) {
                                    completer.set(result);
                                }

                                @Override
                                public void onError(@NonNull AdServicesException error) {
                                    completer.setException(error);
                                }
                            });
                    return "overrideAdSelectionConfigRemoteInfo";
                });
    }

    /**
     * Invokes the {@code removeAdSelectionConfigRemoteInfoOverride} method of {@link
     * AdSelectionManager}, and returns a Void future
     *
     * This method is only available when Developer mode is enabled and the app is debuggable.
     *
     * @hide
     */
    @NonNull
    public ListenableFuture<Void> removeAdSelectionConfigRemoteInfoOverride(
            @NonNull RemoveAdSelectionOverrideRequest request) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mAdSelectionManager.removeAdSelectionConfigRemoteInfoOverride(
                            request,
                            mExecutor,
                            new OutcomeReceiver<Void, AdServicesException>() {

                                @Override
                                public void onResult(Void result) {
                                    completer.set(result);
                                }

                                @Override
                                public void onError(@NonNull AdServicesException error) {
                                    completer.setException(error);
                                }
                            });
                    return "removeAdSelectionConfigRemoteInfoOverride";
                });
    }

    /**
     * Invokes the {@code removeAdSelectionConfigRemoteInfoOverride} method of {@link
     * AdSelectionManager}, and returns a Void future
     *
     * This method is only available when Developer mode is enabled and the app is debuggable.
     *
     * @hide
     */
    @NonNull
    public ListenableFuture<Void> resetAllAdSelectionConfigRemoteOverrides() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mAdSelectionManager.resetAllAdSelectionConfigRemoteOverrides(
                            mExecutor,
                            new OutcomeReceiver<Void, AdServicesException>() {

                                @Override
                                public void onResult(Void result) {
                                    completer.set(result);
                                }

                                @Override
                                public void onError(@NonNull AdServicesException error) {
                                    completer.setException(error);
                                }
                            });
                    return "resetAllAdSelectionConfigRemoteOverrides";
                });
    }

    /** Builder class. */
    public static final class Builder {
        private Context mContext;
        private Executor mExecutor;

        /** Empty-arg constructor with an empty body for Builder */
        public Builder() {}

        /** Sets the context. */
        @NonNull
        public AdSelectionClient.Builder setContext(@NonNull Context context) {
            Objects.requireNonNull(context);

            mContext = context;
            return this;
        }

        /**
         * Sets the worker executor.
         *
         * @param executor the worker executor used to run heavy background tasks.
         */
        @NonNull
        public AdSelectionClient.Builder setExecutor(@NonNull Executor executor) {
            Objects.requireNonNull(executor);

            mExecutor = executor;
            return this;
        }

        /**
         * Builds the Ad Selection Client.
         *
         * @throws NullPointerException if {@code mContext} is null or if {@code mExecutor} is null
         */
        @NonNull
        public AdSelectionClient build() {
            Objects.requireNonNull(mContext);
            Objects.requireNonNull(mExecutor);

            return new AdSelectionClient(mContext, mExecutor);
        }
    }
}
