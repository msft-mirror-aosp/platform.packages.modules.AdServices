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

import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.EmbeddedWebSourceRegistrationRequestInternal;
import android.adservices.measurement.EmbeddedWebTriggerRegistrationRequestInternal;
import android.adservices.measurement.IMeasurementCallback;
import android.adservices.measurement.IMeasurementService;
import android.adservices.measurement.RegistrationRequest;
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
                        callback.onResult(
                                mMeasurementImpl.register(request, System.currentTimeMillis()));
                    } catch (RemoteException e) {
                        LogUtil.e("Unable to send result to the callback", e);
                    }
                });
    }

    @Override
    public void registerEmbeddedWebSource(
            @NonNull EmbeddedWebSourceRegistrationRequestInternal registrationRequest,
            @NonNull IMeasurementCallback iMeasurementCallback) {
        // TODO: Implementation
    }

    @Override
    public void registerEmbeddedWebTrigger(
            @NonNull EmbeddedWebTriggerRegistrationRequestInternal registrationRequest,
            @NonNull IMeasurementCallback iMeasurementCallback) {
        // TODO: Implementation
    }

    @Override
    public void deleteRegistrations(
            @NonNull DeletionRequest request, @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callback);

        sBackgroundExecutor.execute(
                () -> {
                    try {
                        callback.onResult(mMeasurementImpl.deleteRegistrations(request));
                    } catch (RemoteException e) {
                        LogUtil.e("Unable to send result to the callback", e);
                    }
                });
    }
}
