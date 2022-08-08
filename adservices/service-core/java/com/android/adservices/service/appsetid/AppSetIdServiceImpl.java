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
package com.android.adservices.service.appsetid;

import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__APPSETID;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_APPSETID;

import android.adservices.appsetid.GetAppSetIdParam;
import android.adservices.appsetid.IAppSetIdService;
import android.adservices.appsetid.IGetAppSetIdCallback;
import android.adservices.common.CallerMetadata;
import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Process;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.service.stats.Clock;

import java.util.concurrent.Executor;

/**
 * Implementation of {@link IAppSetIdService}.
 *
 * @hide
 */
public class AppSetIdServiceImpl extends IAppSetIdService.Stub {
    private final Context mContext;
    private final AppSetIdWorker mAppSetIdWorker;
    private static final Executor sBackgroundExecutor = AdServicesExecutors.getBackgroundExecutor();
    private final AdServicesLogger mAdServicesLogger;
    private final Clock mClock;

    public AppSetIdServiceImpl(
            Context context,
            AppSetIdWorker appsetidWorker,
            AdServicesLogger adServicesLogger,
            Clock clock) {
        mContext = context;
        mAppSetIdWorker = appsetidWorker;
        mAdServicesLogger = adServicesLogger;
        mClock = clock;
    }

    @Override
    public void getAppSetId(
            @NonNull GetAppSetIdParam appSetIdParam,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IGetAppSetIdCallback callback) {

        final long startServiceTime = mClock.elapsedRealtime();
        final String packageName = appSetIdParam.getAppPackageName();
        final String sdkPackageName = appSetIdParam.getSdkPackageName();

        // We need to save the Calling Uid before offloading to the background executor. Otherwise
        // the Binder.getCallingUid will return the PPAPI process Uid. This also needs to be final
        // since it's used in the lambda.
        final int callingUid = Binder.getCallingUid();

        sBackgroundExecutor.execute(
                () -> {
                    int resultCode = STATUS_SUCCESS;

                    try {
                        resultCode = enforceCallingPackageBelongsToUid(packageName, callingUid);
                        if (resultCode != STATUS_SUCCESS) {
                            callback.onError(resultCode);
                            return;
                        }

                        int appCallingUid = callingUid;

                        if (Process.isSdkSandboxUid(callingUid)) {
                            // The callingUid is the Sandbox UId, convert it to the app UId.
                            appCallingUid = Process.getAppUidForSdkSandboxUid(callingUid);
                        }

                        mAppSetIdWorker.getAppSetId(packageName, appCallingUid, callback);

                    } catch (RemoteException e) {
                        LogUtil.e(e, "Unable to send result to the callback");
                    } finally {
                        long binderCallStartTimeMillis = callerMetadata.getBinderElapsedTimestamp();
                        long serviceLatency = mClock.elapsedRealtime() - startServiceTime;
                        // Double it to simulate the return binder time is same to call binder time
                        long binderLatency = (startServiceTime - binderCallStartTimeMillis) * 2;

                        final int apiLatency = (int) (serviceLatency + binderLatency);
                        mAdServicesLogger.logApiCallStats(
                                new ApiCallStats.Builder()
                                        .setCode(AdServicesStatsLog.AD_SERVICES_API_CALLED)
                                        .setApiClass(AD_SERVICES_API_CALLED__API_CLASS__APPSETID)
                                        .setApiName(AD_SERVICES_API_CALLED__API_NAME__GET_APPSETID)
                                        .setAppPackageName(packageName)
                                        .setSdkPackageName(sdkPackageName)
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
            return STATUS_UNAUTHORIZED;
        }
        if (packageUid != appCallingUid) {
            LogUtil.e(callingPackage + " does not belong to uid " + callingUid);
            return STATUS_UNAUTHORIZED;
        }
        return STATUS_SUCCESS;
    }
}
