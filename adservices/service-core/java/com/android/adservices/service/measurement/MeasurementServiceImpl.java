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

import static com.android.adservices.ResultCode.RESULT_OK;

import android.adservices.measurement.DeletionParam;
import android.adservices.measurement.IMeasurementApiStatusCallback;
import android.adservices.measurement.IMeasurementCallback;
import android.adservices.measurement.IMeasurementService;
import android.adservices.measurement.MeasurementErrorResponse;
import android.adservices.measurement.MeasurementManager.ResultCode;
import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.WebSourceRegistrationRequestInternal;
import android.adservices.measurement.WebTriggerRegistrationRequestInternal;
import android.annotation.NonNull;
import android.content.Context;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.service.AdServicesExecutors;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Implementation of {@link IMeasurementService}.
 *
 * @hide
 */
public class MeasurementServiceImpl extends IMeasurementService.Stub {
    private static final Executor sBackgroundExecutor = AdServicesExecutors.getBackgroundExecutor();
    private final MeasurementImpl mMeasurementImpl;

    public MeasurementServiceImpl(Context context) {
        mMeasurementImpl = MeasurementImpl.getInstance(context);
    }

    @VisibleForTesting
    MeasurementServiceImpl(MeasurementImpl measurementImpl) {
        mMeasurementImpl = measurementImpl;
    }

    @Override
    public void register(@NonNull RegistrationRequest request,
                         @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callback);

        sBackgroundExecutor.execute(
                () -> {
                    try {
                        LogUtil.d("MeasurementServiceImpl: register: ");
                        mMeasurementImpl.register(request, System.currentTimeMillis());
                        callback.onResult();
                    } catch (RemoteException e) {
                        LogUtil.e("Unable to send result to the callback", e);
                    }
                });
    }

    @Override
    public void registerWebSource(
            @NonNull WebSourceRegistrationRequestInternal registrationRequest,
            @NonNull IMeasurementCallback iMeasurementCallback) {
        Objects.requireNonNull(registrationRequest);
        Objects.requireNonNull(iMeasurementCallback);
        sBackgroundExecutor.execute(
                () -> {
                    try {
                        LogUtil.d("MeasurementServiceImpl: registerWebSource: ");
                        // TODO: call measurement Impl and remove the callback invocation below
                        iMeasurementCallback.onResult();
                    } catch (RemoteException e) {
                        LogUtil.e("Unable to send result to the callback", e);
                    }
                });
    }

    @Override
    public void registerWebTrigger(
            @NonNull WebTriggerRegistrationRequestInternal registrationRequest,
            @NonNull IMeasurementCallback iMeasurementCallback) {
        sBackgroundExecutor.execute(
                () -> {
                    try {
                        LogUtil.d("MeasurementServiceImpl: registerWebTrigger: ");
                        // TODO: call measurement Impl and remove the callback invocation below
                        iMeasurementCallback.onResult();
                    } catch (RemoteException e) {
                        LogUtil.e("Unable to send result to the callback", e);
                    }
                });
    }

    @Override
    public void deleteRegistrations(
            @NonNull DeletionParam request, @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callback);

        sBackgroundExecutor.execute(
                () -> {
                    try {

                        @ResultCode int resultCode = mMeasurementImpl.deleteRegistrations(request);
                        if (resultCode == RESULT_OK) {
                            callback.onResult();
                        } else {
                            callback.onFailure(
                                    new MeasurementErrorResponse.Builder()
                                            .setResultCode(resultCode)
                                            .setErrorMessage(
                                                    "Encountered failure during "
                                                            + "Measurement deletion.")
                                            .build());
                        }
                    } catch (RemoteException e) {
                        LogUtil.e("Unable to send result to the callback", e);
                    }
                });
    }

    @Override
    public void getMeasurementApiStatus(@NonNull IMeasurementApiStatusCallback callback) {
        Objects.requireNonNull(callback);

        try {
            callback.onResult(Integer.valueOf(mMeasurementImpl.getMeasurementApiStatus()));
        } catch (RemoteException e) {
            LogUtil.e("Unable to send result to the callback", e);
        }
    }
}
