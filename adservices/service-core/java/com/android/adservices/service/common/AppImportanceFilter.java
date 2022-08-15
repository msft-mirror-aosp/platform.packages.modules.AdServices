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

package com.android.adservices.service.common;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED;

import android.adservices.common.AdServicesStatusUtils;
import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Utility class to be used by PPAPI services to impose that the caller is running in foreground.
 */
public class AppImportanceFilter {
    /** Represents failures when checking the foreground status of the calling application. */
    public static class WrongCallingApplicationStateException extends IllegalStateException {
        /** Creates an instance of {@link WrongCallingApplicationStateException}. */
        public WrongCallingApplicationStateException() {
            super(AdServicesStatusUtils.ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE);
        }
    }

    @VisibleForTesting public static final String UNKNOWN_APP_PACKAGE_NAME = "unknown";

    @NonNull private final ActivityManager mActivityManager;
    @NonNull private final PackageManager mPackageManager;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    private final int mApiClass;
    private final Supplier<Integer> mImportanceThresholdSupplier;

    /**
     * Creates an instance of {@link AppImportanceFilter}.
     *
     * @param context the current service context
     * @param apiClassIdForLogging the id to use to identify the API class in the events generated
     *     for failed foreground status assertions.
     * @param importanceThresholdSupplier A function to provide the maximum importance value
     *     representing a foreground application, for example {@link
     *     RunningAppProcessInfo#IMPORTANCE_FOREGROUND_SERVICE} or {@link
     *     RunningAppProcessInfo#IMPORTANCE_FOREGROUND}. It is assumed to be read from a feature
     *     flag. Using a {@link Supplier} instead of the final value to allow building an instance
     *     of this class in Binder threads (e.g. in Service's constructors) using pH flags.
     */
    public static AppImportanceFilter create(
            @NonNull Context context,
            int apiClassIdForLogging,
            // Cannot pass directly the value since this is read from pH and this shouldn't happen
            // in binder threads. I'm expecting to build a filter in Services' constructor.
            @NonNull Supplier<Integer> importanceThresholdSupplier) {
        return new AppImportanceFilter(
                context.getSystemService(ActivityManager.class),
                context.getPackageManager(),
                AdServicesLoggerImpl.getInstance(),
                apiClassIdForLogging,
                importanceThresholdSupplier);
    }

    /**
     * Creates an instance of {@link AppImportanceFilter}.
     *
     * @param activityManager Instance of {@link ActivityManager}
     * @param packageManager Instance of {@link PackageManager}
     * @param adServicesLogger The {@link AdServicesLogger} to use to log validation failed events
     * @param apiClass The ID to use in the logs to identify the API class
     * @param importanceThresholdSupplier The maximum importance value representing a foreground
     *     application for example {@link RunningAppProcessInfo#IMPORTANCE_FOREGROUND_SERVICE} or
     *     {@link RunningAppProcessInfo#IMPORTANCE_FOREGROUND}
     */
    @VisibleForTesting
    public AppImportanceFilter(
            @NonNull ActivityManager activityManager,
            @NonNull PackageManager packageManager,
            @NonNull AdServicesLogger adServicesLogger,
            int apiClass,
            @NonNull Supplier<Integer> importanceThresholdSupplier) {
        Objects.requireNonNull(activityManager);
        Objects.requireNonNull(packageManager);
        Objects.requireNonNull(importanceThresholdSupplier);

        mActivityManager = activityManager;
        mPackageManager = packageManager;
        mAdServicesLogger = adServicesLogger;
        mApiClass = apiClass;
        mImportanceThresholdSupplier = importanceThresholdSupplier;
    }

    /**
     * Utility method to use to assert that the given application package name corresponds to an
     * application currently running in foreground. If the requirement is not satisfied this method
     * will throw a {@link WrongCallingApplicationStateException} after generating a telemetry event
     * with code {@link
     * com.android.adservices.service.stats.AdServicesStatsLog#AD_SERVICES_API_CALLED}, result code
     * {@link AdServicesStatusUtils#STATUS_BACKGROUND_CALLER}, specifying the given package name and
     * the identifiers of the API class and name.
     *
     * @param appPackageName the package name of the application to check
     * @param apiNameIdForLogging the value to be used as API name identifier when building the
     *     {@link ApiCallStats} for event log in case the check failed
     * @param sdkName the name of the calling SDK to use in the failed assertion events. Null if not
     *     known
     */
    public void assertCallerIsInForeground(
            @NonNull String appPackageName, int apiNameIdForLogging, @Nullable String sdkName)
            throws WrongCallingApplicationStateException {
        List<RunningAppProcessInfo> processes = getProcesses();
        int minImportance = Integer.MAX_VALUE;
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (Arrays.asList(process.pkgList).contains(appPackageName)) {
                minImportance = Math.min(minImportance, process.importance);
            }
        }
        LogUtil.v(
                "Package "
                        + appPackageName
                        + " has minimum importance "
                        + minImportance
                        + " comparing with threshold of "
                        + mImportanceThresholdSupplier.get());
        if (minImportance > mImportanceThresholdSupplier.get()) {
            logForegroundViolation(appPackageName, apiNameIdForLogging, sdkName);

            throw new WrongCallingApplicationStateException();
        }
    }

    /**
     * Utility method to use to assert that the given application UID corresponds to one application
     * currently running in foreground. If the requirement is not satisfied this method will throw a
     * {@link WrongCallingApplicationStateException} after generating a telemetry event with code
     * {@link com.android.adservices.service.stats.AdServicesStatsLog#AD_SERVICES_API_CALLED},
     * result code {@link AdServicesStatusUtils#STATUS_BACKGROUND_CALLER}, specifying the given
     * package name if there is only one package name corresponding to the given uid or "unknown"
     * otherwise and the identifiers of the API class and name.
     *
     * @param appUid the package name of the application to check
     * @param apiNameLoggingId the value to be used as API name identifier when building the {@link
     *     ApiCallStats} for event log in case the check failed
     * @param sdkName the name of the calling SDK to use in the failed assertion events. Null if not
     *     known
     */
    public void assertCallerIsInForeground(
            int appUid, int apiNameLoggingId, @Nullable String sdkName)
            throws WrongCallingApplicationStateException {
        List<RunningAppProcessInfo> processes = getProcesses();
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (process.uid == appUid) {
                LogUtil.v(
                        "Process "
                                + process.uid
                                + "has importance "
                                + process.importance
                                + " comparing with threshold of "
                                + mImportanceThresholdSupplier.get());
                if (process.importance > mImportanceThresholdSupplier.get()) {
                    logForegroundViolation(
                            process.pkgList.length == 1
                                    ? process.pkgList[0]
                                    : UNKNOWN_APP_PACKAGE_NAME,
                            apiNameLoggingId,
                            sdkName);
                    throw new WrongCallingApplicationStateException();
                }
                return;
            }
        }
        logForegroundViolation(UNKNOWN_APP_PACKAGE_NAME, apiNameLoggingId, sdkName);
        throw new WrongCallingApplicationStateException();
    }

    /** Get list of processes */
    private List<RunningAppProcessInfo> getProcesses() {
        try {
            return mActivityManager.getRunningAppProcesses();
        } catch (SecurityException e) {
            LogUtil.e(
                    e,
                    "Failed to check the app state, considering the application not in foreground");
            return Collections.EMPTY_LIST;
        }
    }

    private void logForegroundViolation(
            @NonNull String appPackageName, int apiNameLoggingId, @Nullable String sdkName) {
        mAdServicesLogger.logApiCallStats(
                new ApiCallStats.Builder()
                        .setCode(AD_SERVICES_API_CALLED)
                        .setApiClass(mApiClass)
                        .setApiName(apiNameLoggingId)
                        .setResultCode(AdServicesStatusUtils.STATUS_BACKGROUND_CALLER)
                        .setSdkPackageName(sdkName != null ? sdkName : "")
                        .setAppPackageName(appPackageName)
                        .build());
    }
}
