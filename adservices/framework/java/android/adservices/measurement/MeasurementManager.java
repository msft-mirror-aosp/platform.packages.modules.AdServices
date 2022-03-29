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
package android.adservices.measurement;

import android.adservices.exceptions.AdServicesException;
import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.Uri;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.view.InputEvent;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.LogUtil;
import com.android.adservices.ServiceBinder;
import com.android.internal.annotations.VisibleForTesting;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * MeasurementManager.
 *
 * @hide
 */
public class MeasurementManager {
    public static final String MEASUREMENT_SERVICE = "measurement_service";

    private final Context mContext;
    private final ServiceBinder<IMeasurementService> mServiceBinder;

    /**
     * Create MeasurementManager.
     *
     * @hide
     */
    public MeasurementManager(Context context) {
        mContext = context;
        mServiceBinder = ServiceBinder.getServiceBinder(
                context,
                AdServicesCommon.ACTION_MEASUREMENT_SERVICE,
                IMeasurementService.Stub::asInterface);
    }

    @NonNull
    private IMeasurementService getService() {
        IMeasurementService service = mServiceBinder.getService();
        if (service == null) {
            throw new IllegalStateException("Unable to find the service");
        }
        return service;
    }

    /**
     * Register an attribution source / trigger.
     */
    public void register(
            @NonNull RegistrationRequest registrationRequest,
            @Nullable @CallbackExecutor Executor executor,
            @Nullable OutcomeReceiver<Integer, AdServicesException> callback) {
        Objects.requireNonNull(registrationRequest);
        final IMeasurementService service = getService();

        try {
            service.register(
                    registrationRequest,
                    new IMeasurementCallback.Stub() {
                        @Override
                        public void onResult(int result) {
                            if (callback != null && executor != null) {
                                executor.execute(() -> {
                                    callback.onResult(Integer.valueOf(result));
                                });
                            }
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e("RemoteException", e);
            callback.onError(new AdServicesException("Internal Error!"));
        }
    }

    /**
     * Register an attribution source URI.
     */
    public void registerSource(
            @NonNull Uri attributionSource,
            @Nullable InputEvent inputEvent,
            @NonNull Uri topOrigin,
            @NonNull Uri referrer,
            @Nullable @CallbackExecutor Executor executor,
            @Nullable OutcomeReceiver<Integer, AdServicesException> callback) {
        Objects.requireNonNull(attributionSource);
        Objects.requireNonNull(topOrigin);
        Objects.requireNonNull(referrer);
        register(
                new RegistrationRequest.Builder()
                .setRegistrationType(
                    RegistrationRequest.REGISTER_SOURCE)
                .setRegistrationUri(attributionSource)
                .setInputEvent(inputEvent)
                .setTopOriginUri(topOrigin)
                .setReferrerUri(referrer)
                .setAttributionSource(mContext.getAttributionSource())
                .build(),
                executor, callback);
    }

    /**
     * Register an attribution source URI.
     * Shortcut for the common case.
     */
    public void registerSource(
            @NonNull Uri attributionSource,
            @Nullable InputEvent inputEvent) {
        Objects.requireNonNull(attributionSource);
        registerSource(
                attributionSource, inputEvent, Uri.EMPTY, Uri.EMPTY,
                null, null);
    }

    /**
     * Register a trigger URI.
     */
    public void registerTrigger(
            @NonNull Uri trigger,
            @NonNull Uri topOrigin,
            @NonNull Uri referrer,
            @Nullable @CallbackExecutor Executor executor,
            @Nullable OutcomeReceiver<Integer, AdServicesException> callback) {
        Objects.requireNonNull(trigger);
        Objects.requireNonNull(topOrigin);
        Objects.requireNonNull(referrer);
        register(
                new RegistrationRequest.Builder()
                .setRegistrationType(
                    RegistrationRequest.REGISTER_TRIGGER)
                .setRegistrationUri(trigger)
                .setTopOriginUri(topOrigin)
                .setReferrerUri(referrer)
                .setAttributionSource(mContext.getAttributionSource())
                .build(),
                executor, callback);
    }

    /**
     * Register a trigger URI.
     * Shortcut for the common case.
     */
    public void registerTrigger(@NonNull Uri trigger) {
        Objects.requireNonNull(trigger);
        registerTrigger(trigger, Uri.EMPTY, Uri.EMPTY, null, null);
    }

    /**
     * Delete previously registered data.
     */
    public void deleteRegistrations(
            @NonNull DeletionRequest deletionRequest,
            @Nullable @CallbackExecutor Executor executor,
            @Nullable OutcomeReceiver<Integer, AdServicesException> callback) {
        Objects.requireNonNull(deletionRequest);
        final IMeasurementService service = getService();

        try {
            service.deleteRegistrations(
                    deletionRequest,
                    new IMeasurementCallback.Stub() {
                        @Override
                        public void onResult(int result) {
                            if (callback != null && executor != null) {
                                executor.execute(() -> {
                                    callback.onResult(Integer.valueOf(result));
                                });
                            }
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e("RemoteException", e);
            callback.onError(new AdServicesException("Internal Error!"));
        }
    }

    /**
     * Delete previous registrations.
     */
    public void deleteRegistrations(
            @NonNull Uri origin,
            @Nullable Instant start,
            @Nullable Instant end,
            @Nullable @CallbackExecutor Executor executor,
            @Nullable OutcomeReceiver<Integer, AdServicesException> callback) {
        Objects.requireNonNull(origin);
        deleteRegistrations(
                new DeletionRequest.Builder()
                .setOriginUri(origin)
                .setStart(start)
                .setEnd(end)
                .setAttributionSource(mContext.getAttributionSource())
                .build(),
                executor, callback);
    }

    /**
     * If the service is in an APK (as opposed to the system service), unbind it from the service
     * to allow the APK process to die.
     * @hide Not sure if we'll need this functionality in the final API. For now, we need it
     * for performance testing to simulate "cold-start" situations.
     */
    @VisibleForTesting
    public void unbindFromService() {
        mServiceBinder.unbindFromService();
    }
}
