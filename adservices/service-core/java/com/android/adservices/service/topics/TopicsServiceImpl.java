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
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Process;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.common.AppManifestConfigHelper;
import com.android.adservices.service.common.PermissionHelper;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.ConsentManager;
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
    private final ConsentManager mConsentManager;
    private Clock mClock;

    public TopicsServiceImpl(
            Context context,
            TopicsWorker topicsWorker,
            ConsentManager consentManager,
            AdServicesLogger adServicesLogger,
            Clock clock) {
        mContext = context;
        mTopicsWorker = topicsWorker;
        mConsentManager = consentManager;
        mAdServicesLogger = adServicesLogger;
        mClock = clock;
    }

    @Override
    public void getTopics(
            @NonNull GetTopicsParam topicsParam,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IGetTopicsCallback callback) {

        final long startServiceTime = mClock.elapsedRealtime();
        // TODO(b/236380919): Verify that the passed App PackageName belongs to the caller uid
        final String packageName = topicsParam.getAppPackageName();
        final String sdkName = topicsParam.getSdkName();

        // Check the permission in the same thread since we're looking for caller's permissions.
        boolean permitted =
                PermissionHelper.hasTopicsPermission(mContext)
                        && AppManifestConfigHelper.isAllowedTopicsAccess(
                                mContext, packageName, sdkName);

        // We need to save the Calling Uid before offloading to the background executor. Otherwise
        // the Binder.getCallingUid will return the PPAPI process Uid. This also needs to be final
        // since it's used in the lambda.
        final int callingUid = Binder.getCallingUid();
        sBackgroundExecutor.execute(
                () -> {
                    int resultCode = RESULT_OK;

                    try {
                        AdServicesApiConsent userConsent =
                                mConsentManager.getConsent(mContext.getPackageManager());
                        // Check if caller has permission to invoke this API and user has given
                        // a consent
                        if (!permitted || !userConsent.isGiven()) {
                            resultCode = RESULT_UNAUTHORIZED_CALL;
                            LogUtil.e("Unauthorized caller " + sdkName);
                            callback.onFailure(resultCode);
                            return;
                        }

                        resultCode = enforceCallingPackageBelongsToUid(packageName, callingUid);
                        if (resultCode != RESULT_OK) {
                            callback.onFailure(resultCode);
                            return;
                        }

                        callback.onResult(mTopicsWorker.getTopics(packageName, sdkName));

                        mTopicsWorker.recordUsage(
                                topicsParam.getAppPackageName(), topicsParam.getSdkName());
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

    // Enforce that the callingPackage has the callingUid.
    private int enforceCallingPackageBelongsToUid(String callingPackage, int callingUid) {
        int appCallingUid;
        // Check the Calling Package Name
        if (Process.isSdkSandboxUid(callingUid)) {
            // The callingUid is the Sandbox UId, convert it to the app UId.
            appCallingUid = Process.getAppUidForSdkSandboxUid(callingUid);
        } else {
            appCallingUid = callingUid;
        }
        int packageUid;
        try {
            packageUid = mContext.getPackageManager().getPackageUid(callingPackage, /* flags */ 0);
        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.e(e, callingPackage + " not found");
            return RESULT_UNAUTHORIZED_CALL;
        }
        if (packageUid != appCallingUid) {
            LogUtil.e(callingPackage + " does not belong to uid " + callingUid);
            return RESULT_UNAUTHORIZED_CALL;
        }
        return RESULT_OK;
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
