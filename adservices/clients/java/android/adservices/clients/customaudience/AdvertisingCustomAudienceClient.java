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

package android.adservices.clients.customaudience;

import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceManager;
import android.adservices.exceptions.AdServicesException;
import android.annotation.NonNull;
import android.content.Context;
import android.os.OutcomeReceiver;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * AdvertisingCustomAudienceClient.
 * Currently, this is for test purpose only, not exposing to the client.
 */
// TODO: This should be in JetPack code.
public class AdvertisingCustomAudienceClient {
    private final CustomAudienceManager mCustomAudienceManager;
    private final Context mContext;
    private final Executor mExecutor;

    private AdvertisingCustomAudienceClient(@NonNull Context context, @NonNull Executor executor) {
        mContext = context;
        mExecutor = executor;
        mCustomAudienceManager =
                mContext.getSystemService(CustomAudienceManager.class);
    }

    /** Gets the context. */
    @NonNull
    public Context getContext() {
        return mContext;
    }

    /** Gets the worker executor. */
    @NonNull
    public Executor getExecutor() {
        return mExecutor;
    }

    /** Join custom audience. */
    @NonNull
    public ListenableFuture<Void> joinCustomAudience(CustomAudience customAudience) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mCustomAudienceManager.joinCustomAudience(
                            customAudience,
                            mExecutor,
                            new OutcomeReceiver<Void, AdServicesException>() {
                                @Override
                                public void onResult(Void result) {
                                    completer.set(null);
                                }

                                @Override
                                public void onError(AdServicesException error) {
                                    completer.setException(error);
                                }
                            });
                    // This value is used only for debug purposes: it will be used in toString()
                    // of returned future or error cases.
                    return "joinCustomAudience";
                });
    }

    /** Leave custom audience. */
    @NonNull
    public ListenableFuture<Void> leaveCustomAudience(@NonNull String owner, @NonNull String buyer,
            @NonNull String name) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mCustomAudienceManager.leaveCustomAudience(owner, buyer, name,
                            mExecutor,
                            new OutcomeReceiver<Void, AdServicesException>() {
                                @Override
                                public void onResult(Void result) {
                                    completer.set(null);
                                }

                                @Override
                                public void onError(AdServicesException error) {
                                    completer.setException(error);
                                }
                            });
                    // This value is used only for debug purposes: it will be used in toString()
                    // of returned future or error cases.
                    return "leaveCustomAudience";
                });
    }

    /** Builder class. */
    public static final class Builder {
        private Context mContext;
        private Executor mExecutor;

        /** Empty-arg constructor with an empty body for Builder */
        public Builder() {
        }

        /** Sets the context. */
        @NonNull
        public Builder setContext(@NonNull Context context) {
            Objects.requireNonNull(context);
            mContext = context;
            return this;
        }

        /**
         * Sets the worker executor.
         *
         * <p>If an executor is not provided, the {@link AdvertisingCustomAudienceClient} default
         * executor will be
         * used.
         *
         * @param executor the worker executor used to run heavy background tasks.
         */
        @NonNull
        public Builder setExecutor(@NonNull Executor executor) {
            Objects.requireNonNull(executor);
            mExecutor = executor;
            return this;
        }

        /** Builds a {@link AdvertisingCustomAudienceClient} instance */
        @NonNull
        public AdvertisingCustomAudienceClient build() {
            if (mExecutor == null) {
                mExecutor = Executors.newCachedThreadPool();
            }
            return new AdvertisingCustomAudienceClient(mContext, mExecutor);
        }
    }
}
