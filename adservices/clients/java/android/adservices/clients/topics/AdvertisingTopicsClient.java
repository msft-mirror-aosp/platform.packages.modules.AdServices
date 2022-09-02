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
package android.adservices.clients.topics;

import android.adservices.topics.GetTopicsRequest;
import android.adservices.topics.GetTopicsResponse;
import android.adservices.topics.TopicsManager;
import android.annotation.NonNull;
import android.content.Context;
import android.os.OutcomeReceiver;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Objects;
import java.util.concurrent.Executor;

/** AdvertisingTopicsClient. Add more java doc here. */
// TODO: This should be in JetPack code.
public class AdvertisingTopicsClient {

    private String mSdkName;
    private TopicsManager mTopicsManager;
    private Context mContext;
    private Executor mExecutor;

    private AdvertisingTopicsClient(
            @NonNull Context context, @NonNull Executor executor, @NonNull String sdkName) {
        mContext = context;
        mSdkName = sdkName;
        mExecutor = executor;
        mTopicsManager = mContext.getSystemService(TopicsManager.class);
    }

    /** Gets the SdkName. */
    @NonNull
    public String getSdkName() {
        return mSdkName;
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

    /** Gets the topics. */
    public @NonNull ListenableFuture<GetTopicsResponse> getTopics() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    GetTopicsRequest request =
                            mSdkName == null
                                    ? GetTopicsRequest.create()
                                    : GetTopicsRequest.createWithAdsSdkName(mSdkName);
                    mTopicsManager.getTopics(
                            request,
                            mExecutor,
                            new OutcomeReceiver<GetTopicsResponse, Exception>() {
                                @Override
                                public void onResult(@NonNull GetTopicsResponse result) {
                                    completer.set(result);
                                }

                                @Override
                                public void onError(@NonNull Exception error) {
                                    completer.setException(error);
                                }
                            });
                    // This value is used only for debug purposes: it will be used in toString()
                    // of returned future or error cases.
                    return "getTopics";
                });
    }

    /** Builder class. */
    public static final class Builder {
        private String mSdkName;
        private Context mContext;
        private Executor mExecutor;

        /** Empty-arg constructor with an empty body for Builder */
        public Builder() {}

        /** Sets the context. */
        public @NonNull AdvertisingTopicsClient.Builder setContext(@NonNull Context context) {
            mContext = context;
            return this;
        }

        /** Sets the SdkName. */
        public @NonNull Builder setSdkName(@NonNull String sdkName) {
            mSdkName = sdkName;
            return this;
        }

        /**
         * Sets the worker executor.
         *
         * <p>If an executor is not provided, the AdvertisingTopicsClient default executor will be
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

        /** Builds a {@link AdvertisingTopicsClient} instance */
        public @NonNull AdvertisingTopicsClient build() {
            if (mExecutor == null) {
                throw new NullPointerException("Executor is not set");
            }
            return new AdvertisingTopicsClient(mContext, mExecutor, mSdkName);
        }
    }
}
