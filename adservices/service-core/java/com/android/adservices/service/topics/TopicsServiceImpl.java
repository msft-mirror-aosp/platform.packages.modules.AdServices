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

import static android.adservices.common.AdServicesStatusUtils.STATUS_ADSERVICES_DISABLED;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__TARGETING;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS_PREVIEW_API;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;

import android.adservices.common.CallerMetadata;
import android.adservices.topics.GetTopicsParam;
import android.adservices.topics.GetTopicsResult;
import android.adservices.topics.IGetTopicsCallback;
import android.adservices.topics.ITopicsService;
import android.annotation.NonNull;
import android.content.Context;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.AdsRelevanceStatusUtils;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.shared.util.Clock;

import java.util.concurrent.Executor;

/**
 * Implementation of {@link ITopicsService}.
 *
 * @hide
 */
@RequiresApi(Build.VERSION_CODES.S)
public class TopicsServiceImpl extends ITopicsService.Stub {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getTopicsLogger();
    private static final Executor sBackgroundExecutor = AdServicesExecutors.getBackgroundExecutor();

    private final Context mContext;
    private final TopicsWorker mTopicsWorker;
    private final AdServicesLogger mAdServicesLogger;
    private final ConsentManager mConsentManager;
    private final Clock mClock;
    private final Flags mFlags;
    private final Throttler mThrottler;
    private final EnrollmentDao mEnrollmentDao;
    private final AppImportanceFilter mAppImportanceFilter;

    public TopicsServiceImpl(
            Context context,
            TopicsWorker topicsWorker,
            ConsentManager consentManager,
            AdServicesLogger adServicesLogger,
            Clock clock,
            Flags flags,
            Throttler throttler,
            EnrollmentDao enrollmentDao,
            AppImportanceFilter appImportanceFilter) {
        mContext = context;
        mTopicsWorker = topicsWorker;
        mConsentManager = consentManager;
        mAdServicesLogger = adServicesLogger;
        mClock = clock;
        mFlags = flags;
        mThrottler = throttler;
        mEnrollmentDao = enrollmentDao;
        mAppImportanceFilter = appImportanceFilter;
    }

    @Override
    public void getTopics(
            @NonNull GetTopicsParam topicsParam,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IGetTopicsCallback callback) {

        // Logs API deprecated.
        int apiName =
                topicsParam.shouldRecordObservation()
                        ? AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS
                        : AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS_PREVIEW_API;
        String packageName = topicsParam.getAppPackageName();
        String sdkName = topicsParam.getSdkName();
        mAdServicesLogger.logApiCallStats(
                new ApiCallStats.Builder()
                        .setCode(AdServicesStatsLog.AD_SERVICES_API_CALLED)
                        .setApiClass(AD_SERVICES_API_CALLED__API_CLASS__TARGETING)
                        .setApiName(apiName)
                        .setAppPackageName(packageName)
                        .setSdkPackageName(sdkName)
                        .setLatencyMillisecond(0)
                        .setResultCode(STATUS_ADSERVICES_DISABLED)
                        .build());
        sLogger.e("Got in-coming calls but TopicsService APIs are deprecated.");

        // Sent back deprecation message throw callback
        try {
            callback.onResult(
                    new GetTopicsResult.Builder()
                            .setResultCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
        }
    }

    /** Init the Topics Service. */
    public void init() {
        // This is to prevent cold-start latency on getTopics API.
        // Load cache when the service is created.
        // The recommended pattern is:
        // 1) In app startup, wake up the TopicsService.
        // 2) The TopicsService will load the Topics Cache from DB into memory.
        // 3) Later, when the app calls Topics API, the returned Topics will be served
        // from
        // Cache in memory.
        sBackgroundExecutor.execute(mTopicsWorker::loadCache);
    }
}
