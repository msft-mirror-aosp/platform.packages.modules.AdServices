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
package com.android.adservices.service.measurement;

import static android.adservices.common.AdServicesStatusUtils.STATUS_ADSERVICES_DISABLED;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__MEASUREMENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__DELETE_REGISTRATIONS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_MEASUREMENT_API_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REGISTER_SOURCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REGISTER_SOURCES;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REGISTER_TRIGGER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REGISTER_WEB_SOURCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REGISTER_WEB_TRIGGER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;

import android.adservices.common.AdServicesPermissions;
import android.adservices.common.CallerMetadata;
import android.adservices.measurement.DeletionParam;
import android.adservices.measurement.IMeasurementApiStatusCallback;
import android.adservices.measurement.IMeasurementCallback;
import android.adservices.measurement.IMeasurementService;
import android.adservices.measurement.MeasurementErrorResponse;
import android.adservices.measurement.MeasurementManager;
import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.SourceRegistrationRequestInternal;
import android.adservices.measurement.StatusParam;
import android.adservices.measurement.WebSourceRegistrationRequestInternal;
import android.adservices.measurement.WebTriggerRegistrationRequestInternal;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * Implementation of {@link IMeasurementService}.
 *
 * @hide
 */
@RequiresApi(Build.VERSION_CODES.S)
public class MeasurementServiceImpl extends IMeasurementService.Stub {
    private final Clock mClock;
    private final AdServicesLogger mAdServicesLogger;

    public MeasurementServiceImpl(@NonNull Clock clock) {
        this(clock, AdServicesLoggerImpl.getInstance());
    }

    @VisibleForTesting
    MeasurementServiceImpl(@NonNull Clock clock, @NonNull AdServicesLogger adServicesLogger) {
        mClock = clock;
        mAdServicesLogger = adServicesLogger;
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_ATTRIBUTION)
    public void register(
            @NonNull RegistrationRequest request,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callerMetadata);
        Objects.requireNonNull(callback);
        invokeCallbackOnFailureOnAllDevices(callback);
        final long serviceStartTime = mClock.elapsedRealtime();
        final int apiNameId = getApiNameId(request);
        logApiStats(
                apiNameId,
                request.getAppPackageName(),
                request.getSdkPackageName(),
                getLatency(callerMetadata, serviceStartTime),
                STATUS_ADSERVICES_DISABLED);
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_ATTRIBUTION)
    public void registerWebSource(
            @NonNull WebSourceRegistrationRequestInternal request,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callerMetadata);
        Objects.requireNonNull(callback);
        invokeCallbackOnFailureOnAllDevices(callback);
        final long serviceStartTime = mClock.elapsedRealtime();
        final int apiNameId = AD_SERVICES_API_CALLED__API_NAME__REGISTER_WEB_SOURCE;
        logApiStats(
                apiNameId,
                request.getAppPackageName(),
                request.getSdkPackageName(),
                getLatency(callerMetadata, serviceStartTime),
                STATUS_ADSERVICES_DISABLED);
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_ATTRIBUTION)
    public void registerSource(
            @NonNull SourceRegistrationRequestInternal request,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callerMetadata);
        Objects.requireNonNull(callback);
        invokeCallbackOnFailureOnAllDevices(callback);
        final long serviceStartTime = mClock.elapsedRealtime();
        final int apiNameId = AD_SERVICES_API_CALLED__API_NAME__REGISTER_SOURCES;
        logApiStats(
                apiNameId,
                request.getAppPackageName(),
                request.getSdkPackageName(),
                getLatency(callerMetadata, serviceStartTime),
                STATUS_ADSERVICES_DISABLED);
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_ATTRIBUTION)
    public void registerWebTrigger(
            @NonNull WebTriggerRegistrationRequestInternal request,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callerMetadata);
        Objects.requireNonNull(callback);
        invokeCallbackOnFailureOnAllDevices(callback);
        final long serviceStartTime = mClock.elapsedRealtime();
        final int apiNameId = AD_SERVICES_API_CALLED__API_NAME__REGISTER_WEB_TRIGGER;
        logApiStats(
                apiNameId,
                request.getAppPackageName(),
                request.getSdkPackageName(),
                getLatency(callerMetadata, serviceStartTime),
                STATUS_ADSERVICES_DISABLED);
    }

    @Override
    public void deleteRegistrations(
            @NonNull DeletionParam request,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callerMetadata);
        Objects.requireNonNull(callback);
        invokeCallbackOnFailureOnAllDevices(callback);
        final long serviceStartTime = mClock.elapsedRealtime();
        final int apiNameId = AD_SERVICES_API_CALLED__API_NAME__DELETE_REGISTRATIONS;
        logApiStats(
                apiNameId,
                request.getAppPackageName(),
                request.getSdkPackageName(),
                getLatency(callerMetadata, serviceStartTime),
                STATUS_ADSERVICES_DISABLED);
    }

    @Override
    public void getMeasurementApiStatus(
            @NonNull StatusParam statusParam,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IMeasurementApiStatusCallback callback) {
        Objects.requireNonNull(statusParam);
        Objects.requireNonNull(callerMetadata);
        Objects.requireNonNull(callback);
        try {
            // API status callback doesn't have an onError/onFailure
            callback.onResult(MeasurementManager.MEASUREMENT_API_STATE_DISABLED);
        } catch (RemoteException e) {
            String errorMsg = "Measurement API is disabled.";
            LogUtil.e(e, "Fail to call the callback. %s", errorMsg);
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
        }

        final long serviceStartTime = mClock.elapsedRealtime();
        final int apiNameId = AD_SERVICES_API_CALLED__API_NAME__GET_MEASUREMENT_API_STATUS;
        logApiStats(
                apiNameId,
                statusParam.getAppPackageName(),
                statusParam.getSdkPackageName(),
                getLatency(callerMetadata, serviceStartTime),
                STATUS_ADSERVICES_DISABLED);
        return;
    }

    @Override
    public void schedulePeriodicJobs(IMeasurementCallback callback) {
        invokeCallbackOnFailureOnAllDevices(callback);
    }

    private void invokeCallbackOnFailureOnAllDevices(IMeasurementCallback callback) {
        String errorMsg = "Measurement API is disabled";
        MeasurementErrorResponse response =
                new MeasurementErrorResponse.Builder()
                        .setStatusCode(STATUS_ADSERVICES_DISABLED)
                        .setErrorMessage(errorMsg)
                        .build();

        try {
            if (callback != null) {
                callback.onFailure(response);
            }
        } catch (RemoteException e) {
            LogUtil.e(e, String.format("Fail to call the callback. %s", errorMsg));
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
        }
    }

    private int getApiNameId(RegistrationRequest request) {
        return RegistrationRequest.REGISTER_SOURCE == request.getRegistrationType()
                ? AD_SERVICES_API_CALLED__API_NAME__REGISTER_SOURCE
                : AD_SERVICES_API_CALLED__API_NAME__REGISTER_TRIGGER;
    }

    private int getLatency(CallerMetadata metadata, long serviceStartTime) {
        long binderCallStartTimeMillis = metadata.getBinderElapsedTimestamp();
        long serviceLatency = mClock.elapsedRealtime() - serviceStartTime;
        // Double it to simulate the return binder time is same to call binder time
        long binderLatency = (serviceStartTime - binderCallStartTimeMillis) * 2;

        return (int) (serviceLatency + binderLatency);
    }

    private void logApiStats(
            int apiNameId,
            String appPackageName,
            String sdkPackageName,
            int latency,
            int resultCode) {
        mAdServicesLogger.logApiCallStats(
                new ApiCallStats.Builder()
                        .setCode(AD_SERVICES_API_CALLED)
                        .setApiClass(AD_SERVICES_API_CALLED__API_CLASS__MEASUREMENT)
                        .setApiName(apiNameId)
                        .setAppPackageName(appPackageName)
                        .setSdkPackageName(sdkPackageName)
                        .setLatencyMillisecond(latency)
                        .setResultCode(resultCode)
                        .build());
    }
}
