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
package com.android.adservices.service.topics;

import android.adservices.topics.GetTopicsRequest;
import android.adservices.topics.IGetTopicsCallback;
import android.adservices.topics.ITopicsService;
import android.annotation.NonNull;
import android.content.Context;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.service.AdServicesExecutors;

import java.util.concurrent.Executor;

/**
 * Implementation of {@link ITopicsService}.
 *
 * @hide
 */
public class TopicsServiceImpl extends ITopicsService.Stub {
    private final Context mContext;
    private final TopicsWorker mTopicsWorker;
    private static final Executor sBackgroundExecutor = AdServicesExecutors.getBackgroundExecutor();

    public TopicsServiceImpl(Context context, TopicsWorker topicsWorker) {
        mContext = context;
        mTopicsWorker = topicsWorker;
    }

    @Override
    public void getTopics(@NonNull GetTopicsRequest getTopicsRequest,
            @NonNull IGetTopicsCallback callback) {
        sBackgroundExecutor.execute(() -> {
            try {
                callback.onResult(mTopicsWorker.getTopics(
                        getTopicsRequest.getAttributionSource().getPackageName(),
                        getTopicsRequest.getSdkName()));

                mTopicsWorker.recordUsage(getTopicsRequest.getAttributionSource().getPackageName(),
                        getTopicsRequest.getSdkName());
            } catch (RemoteException e) {
                LogUtil.e("Unable to send result to the callback", e);
            }
        });
    }

    /**
     * Init the Topics Service.
     */
    public void init() {
        sBackgroundExecutor.execute(() -> {
            // This is to prevent cold-start latency on getTopics API.
            // Load cache when the service is created.
            // The recommended pattern is:
            // 1) In app startup, wake up the TopicsService.
            // 2) The TopicsService will load the Topics Cache from DB into memory.
            // 3) Later, when the app calls Topics API, the returned Topics will be served from
            // Cache in memory.
            mTopicsWorker.loadCache();
        });
    }
}
