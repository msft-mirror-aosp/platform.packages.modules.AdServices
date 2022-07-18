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
import static com.android.adservices.ResultCode.RESULT_UNAUTHORIZED_CALL;

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
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.ConsentManager;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Implementation of {@link IMeasurementService}.
 *
 * @hide
 */
public class MeasurementServiceImpl extends IMeasurementService.Stub {
    private static final Executor sBackgroundExecutor = AdServicesExecutors.getBackgroundExecutor();
    private final MeasurementImpl mMeasurementImpl;
    private final ConsentManager mConsentManager;
    private final Context mContext;
    private static final String UNAUTHORIZED_ERROR_MESSAGE =
            "Caller is not authorized to call this API.";

    public MeasurementServiceImpl(Context context, ConsentManager consentManager) {
        mContext = context;
        mMeasurementImpl = MeasurementImpl.getInstance(context);
        mConsentManager = consentManager;
    }

    @VisibleForTesting
    MeasurementServiceImpl(
            MeasurementImpl measurementImpl, Context context, ConsentManager consentManager) {
        mContext = context;
        mMeasurementImpl = measurementImpl;
        mConsentManager = consentManager;
    }

    @Override
    public void register(
            @NonNull RegistrationRequest request, @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callback);

        sBackgroundExecutor.execute(
                () -> {
                    performWorkIfAllowed(
                            (mMeasurementImpl) ->
                                    mMeasurementImpl.register(request, System.currentTimeMillis()),
                            callback);
                });
    }

    @Override
    public void registerWebSource(
            @NonNull WebSourceRegistrationRequestInternal request,
            @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callback);

        sBackgroundExecutor.execute(
                () -> {
                    performWorkIfAllowed(
                            (mMeasurementImpl) ->
                                    mMeasurementImpl.registerWebSource(
                                            request, System.currentTimeMillis()),
                            callback);
                });
    }

    @Override
    public void registerWebTrigger(
            @NonNull WebTriggerRegistrationRequestInternal request,
            @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callback);

        sBackgroundExecutor.execute(
                () -> {
                    performWorkIfAllowed(
                            (measurementImpl) ->
                                    measurementImpl.registerWebTrigger(
                                            request, System.currentTimeMillis()),
                            callback);
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

    private void performWorkIfAllowed(
            Consumer<MeasurementImpl> execute, IMeasurementCallback callback) {
        try {
            AdServicesApiConsent userConsent =
                    mConsentManager.getConsent(mContext.getPackageManager());

            if (!userConsent.isGiven()) {
                callback.onFailure(
                        new MeasurementErrorResponse.Builder()
                                .setResultCode(RESULT_UNAUTHORIZED_CALL)
                                .setErrorMessage(UNAUTHORIZED_ERROR_MESSAGE)
                                .build());
            } else {
                execute.accept(mMeasurementImpl);
                callback.onResult();
            }
        } catch (RemoteException e) {
            LogUtil.e("Unable to send result to the callback", e);
        }
    }
}
