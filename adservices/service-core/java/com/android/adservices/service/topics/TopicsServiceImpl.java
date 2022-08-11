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

import static android.adservices.common.AdServicesPermissions.ACCESS_ADSERVICES_TOPICS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_PERMISSION_NOT_REQUESTED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__TARGETING;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.CallerMetadata;
import android.adservices.topics.GetTopicsParam;
import android.adservices.topics.IGetTopicsCallback;
import android.adservices.topics.ITopicsService;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Process;
import android.os.RemoteException;
import android.text.TextUtils;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AllowLists;
import com.android.adservices.service.common.AppManifestConfigHelper;
import com.android.adservices.service.common.PermissionHelper;
import com.android.adservices.service.common.SdkRuntimeUtil;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.enrollment.EnrollmentData;
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
    private static final Executor sBackgroundExecutor = AdServicesExecutors.getBackgroundExecutor();
    private final Context mContext;
    private final TopicsWorker mTopicsWorker;
    private final AdServicesLogger mAdServicesLogger;
    private final ConsentManager mConsentManager;
    private final Clock mClock;
    private final Flags mFlags;
    private final Throttler mThrottler;
    private final EnrollmentDao mEnrollmentDao;

    public TopicsServiceImpl(
            Context context,
            TopicsWorker topicsWorker,
            ConsentManager consentManager,
            AdServicesLogger adServicesLogger,
            Clock clock,
            Flags flags,
            Throttler throttler,
            EnrollmentDao enrollmentDao) {
        mContext = context;
        mTopicsWorker = topicsWorker;
        mConsentManager = consentManager;
        mAdServicesLogger = adServicesLogger;
        mClock = clock;
        mFlags = flags;
        mThrottler = throttler;
        mEnrollmentDao = enrollmentDao;
    }

    @Override
    @RequiresPermission(ACCESS_ADSERVICES_TOPICS)
    public void getTopics(
            @NonNull GetTopicsParam topicsParam,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IGetTopicsCallback callback) {

        if (isThrottled(topicsParam, callback)) return;

        final long startServiceTime = mClock.elapsedRealtime();
        // TODO(b/236380919): Verify that the passed App PackageName belongs to the caller uid
        final String packageName = topicsParam.getAppPackageName();
        final String sdkName = topicsParam.getSdkName();
        final String sdkPackageName = topicsParam.getSdkPackageName();

        // We need to save the Calling Uid before offloading to the background executor. Otherwise
        // the Binder.getCallingUid will return the PPAPI process Uid. This also needs to be final
        // since it's used in the lambda.
        final int callingUid = Binder.getCallingUidOrThrow();

        // Check the permission in the same thread since we're looking for caller's permissions.
        // Note: The permission check uses sdk package name since PackageManager checks if the
        // permission is declared in the manifest of that package name.
        boolean hasTopicsPermission =
                PermissionHelper.hasTopicsPermission(
                        mContext, Process.isSdkSandboxUid(callingUid), sdkPackageName);

        sBackgroundExecutor.execute(
                () -> {
                    int resultCode = STATUS_SUCCESS;

                    try {
                        if (!canCallerInvokeTopicsService(
                                hasTopicsPermission, topicsParam, callingUid, callback)) {
                            return;
                        }

                        callback.onResult(mTopicsWorker.getTopics(packageName, sdkName));

                        mTopicsWorker.recordUsage(
                                topicsParam.getAppPackageName(), topicsParam.getSdkName());
                    } catch (RemoteException e) {
                        LogUtil.e(e, "Unable to send result to the callback");
                        resultCode = STATUS_INTERNAL_ERROR;
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

    // Throttle the Topics API.
    // Return true if we should throttle (don't allow the API call).
    private boolean isThrottled(GetTopicsParam topicsParam, IGetTopicsCallback callback) {
        // There are 2 cases for throttling:
        // Case 1: the App calls Topics API directly, not via a SDK. In this case,
        // the SdkName == Empty
        // Case 2: the SDK calls Topics API.
        boolean throttled =
                TextUtils.isEmpty(topicsParam.getSdkName())
                        ? !mThrottler.tryAcquire(
                                Throttler.ApiKey.TOPICS_API_APP_PACKAGE_NAME,
                                topicsParam.getAppPackageName())
                        : !mThrottler.tryAcquire(
                                Throttler.ApiKey.TOPICS_API_SDK_NAME, topicsParam.getSdkName());

        if (throttled) {
            LogUtil.e("Rate Limit Reached for TOPICS_API");
            try {
                callback.onFailure(STATUS_RATE_LIMIT_REACHED);
            } catch (RemoteException e) {
                LogUtil.e(e, "Fail to call the callback on Rate Limit Reached.");
            }
            return true;
        }
        return false;
    }

    /**
     * Check whether caller can invoke the Topics API. The caller is not allowed to do it when one
     * of the following occurs:
     *
     * <ul>
     *   <li>Permission was not requested.
     *   <li>Caller is not allowed - not present in the allowed list.
     *   <li>User consent was revoked.
     * </ul>
     *
     * @param sufficientPermission boolean which tells whether caller has sufficient permissions.
     * @param topicsParam {@link GetTopicsParam} to get information about the request.
     * @param callback {@link IGetTopicsCallback} to invoke when caller is not allowed.
     * @return true if caller is allowed to invoke Topics API, false otherwise.
     */
    private boolean canCallerInvokeTopicsService(
            boolean sufficientPermission,
            GetTopicsParam topicsParam,
            int callingUid,
            IGetTopicsCallback callback) {
        if (!sufficientPermission) {
            invokeCallbackWithStatus(
                    callback,
                    STATUS_PERMISSION_NOT_REQUESTED,
                    "Unauthorized caller. Permission not requested.");
            return false;
        }

        // This needs to access PhFlag which requires READ_DEVICE_CONFIG which
        // is not granted for binder thread. So we have to check it with one
        // of non-binder thread of the PPAPI.
        boolean appCanUsePpapi = AllowLists.appCanUsePpapi(mFlags, topicsParam.getAppPackageName());
        if (!appCanUsePpapi) {
            invokeCallbackWithStatus(
                    callback,
                    STATUS_CALLER_NOT_ALLOWED,
                    "Unauthorized caller. Caller is not allowed.");
            return false;
        }

        // Check whether calling package belongs to the callingUid
        int resultCode =
                enforceCallingPackageBelongsToUid(topicsParam.getAppPackageName(), callingUid);
        if (resultCode != STATUS_SUCCESS) {
            invokeCallbackWithStatus(callback, resultCode, "Caller is not authorized.");
            return false;
        }

        AdServicesApiConsent userConsent = mConsentManager.getConsent(mContext.getPackageManager());
        if (!userConsent.isGiven()) {
            invokeCallbackWithStatus(
                    callback, STATUS_USER_CONSENT_REVOKED, "User consent revoked.");
            return false;
        }

        // The app developer declares which SDKs they would like to allow Topics
        // access to using the enrollment ID. Get the enrollment ID for this SDK and
        // check that against the app's manifest.
        if (!mFlags.isDisableTopicsEnrollmentCheck() && !topicsParam.getSdkName().isEmpty()) {
            EnrollmentData enrollmentData =
                    mEnrollmentDao.getEnrollmentDataFromSdkName(topicsParam.getSdkName());
            boolean permitted =
                    (enrollmentData != null && enrollmentData.getEnrollmentId() != null)
                            && AppManifestConfigHelper.isAllowedTopicsAccess(
                                    mContext,
                                    topicsParam.getAppPackageName(),
                                    enrollmentData.getEnrollmentId());
            if (!permitted) {
                invokeCallbackWithStatus(
                        callback, STATUS_CALLER_NOT_ALLOWED, "Caller is not authorized.");
                return false;
            }
        }

        return true;
    }

    private void invokeCallbackWithStatus(
            IGetTopicsCallback callback,
            @AdServicesStatusUtils.StatusCode int statusCode,
            String message) {
        LogUtil.e(message);
        try {
            callback.onFailure(statusCode);
        } catch (RemoteException e) {
            LogUtil.e(e, String.format("Fail to call the callback. %s", message));
        }
    }

    // Enforce that the callingPackage has the callingUid.
    private int enforceCallingPackageBelongsToUid(String callingPackage, int callingUid) {
        int appCallingUid = SdkRuntimeUtil.getCallingAppUid(callingUid);
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

    /** Init the Topics Service. */
    public void init() {
        sBackgroundExecutor.execute(
                () -> {
                    // This is to prevent cold-start latency on getTopics API.
                    // Load cache when the service is created.
                    // The recommended pattern is:
                    // 1) In app startup, wake up the TopicsService.
                    // 2) The TopicsService will load the Topics Cache from DB into memory.
                    // 3) Later, when the app calls Topics API, the returned Topics will be served
                    // from
                    // Cache in memory.
                    mTopicsWorker.loadCache();
                });
    }
}
