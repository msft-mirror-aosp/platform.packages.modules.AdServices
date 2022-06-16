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

import static com.android.adservices.ResultCode.RESULT_INTERNAL_ERROR;
import static com.android.adservices.ResultCode.RESULT_OK;
import static com.android.adservices.ResultCode.RESULT_UNAUTHORIZED_CALL;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__TARGETING;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS;

import android.adservices.common.CallerMetadata;
import android.adservices.topics.GetTopicsParam;
import android.adservices.topics.IGetTopicsCallback;
import android.adservices.topics.ITopicsService;
import android.annotation.NonNull;
import android.content.Context;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.service.AdServicesExecutors;
import com.android.adservices.service.common.PermissionHelper;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.service.stats.Clock;

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
    private final AdServicesLogger mAdServicesLogger;
    private Clock mClock;

    public TopicsServiceImpl(Context context, TopicsWorker topicsWorker,
            AdServicesLogger adServicesLogger, Clock clock) {
        mContext = context;
        mTopicsWorker = topicsWorker;
        mAdServicesLogger = adServicesLogger;
        mClock = clock;
    }

    @Override
    public void getTopics(
            @NonNull GetTopicsParam topicsParam,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IGetTopicsCallback callback) {

        final long startServiceTime = mClock.elapsedRealtime();
        final String packageName = topicsParam.getAttributionSource().getPackageName();
        final String sdkName = topicsParam.getSdkName();

        // Check the permission in the same thread since we're looking for caller's permissions.
        boolean permitted = PermissionHelper.hasTopicsPermission(mContext);
        sBackgroundExecutor.execute(
                () -> {
                    int resultCode = RESULT_OK;

                    try {
                        // Check if caller has permission to invoke this API.
                        if (!permitted) {
                            resultCode = RESULT_UNAUTHORIZED_CALL;
                            LogUtil.e("Unauthorized caller " + sdkName);
                            callback.onFailure(resultCode);
                        } else {
                            callback.onResult(mTopicsWorker.getTopics(packageName, sdkName));

                            mTopicsWorker.recordUsage(
                                    topicsParam.getAttributionSource().getPackageName(),
                                    topicsParam.getSdkName());
                        }
                    } catch (RemoteException e) {
                        LogUtil.e("Unable to send result to the callback", e);
                        resultCode = RESULT_INTERNAL_ERROR;
                    } finally {
                        long binderCallStartTimeMillis = callerMetadata.getBinderElapsedTimestamp();
                        long serviceLatency = mClock.elapsedRealtime() - startServiceTime;
                        // Double it to simulate the return binder time is same to call binder time
                        long binderLatency = (startServiceTime - binderCallStartTimeMillis) * 2;

                        final int apiLatency = (int) (serviceLatency + binderLatency);
                        mAdServicesLogger.logApiCallStats(
                                new ApiCallStats.Builder()
                                        .setCode(AdServicesStatsLog.AD_SERVICES_API_CALLED)
                                        .setApiClass(AD_SERVICES_API_CALLED__API_CLASS__TARGETING)
                                        .setApiName(AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS)
                                        .setAppPackageName(packageName)
                                        .setSdkPackageName(sdkName)
                                        .setLatencyMillisecond(apiLatency)
                                        .setResultCode(resultCode)
                                        .build());
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
