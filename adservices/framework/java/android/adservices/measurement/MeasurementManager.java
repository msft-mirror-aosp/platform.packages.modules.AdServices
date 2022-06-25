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

import android.adservices.AdServicesApiUtil;
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
 */
public class MeasurementManager {
    /** @hide */
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
     *
     * @hide
     */
    private void register(
            @NonNull RegistrationRequest registrationRequest,
            @Nullable @CallbackExecutor Executor executor,
            @Nullable OutcomeReceiver<Void, AdServicesException> callback) {
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
                                    callback.onResult(null);
                                });
                            }
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e("RemoteException", e);
            if (callback != null && executor != null) {
                executor.execute(() ->
                        callback.onError(new AdServicesException("Internal Error"))
                );
            }
        }
    }

    /**
     * Register an attribution source (click or view).
     *
     * @param attributionSource the platform issues a request to this URI in order to fetch metadata
     *                         associated with the attribution source.
     * @param inputEvent either an {@link InputEvent} object (for a click event) or null (for a view
     *                  event).
     * @param executor used by callback to dispatch results.
     * @param callback intended to notify asynchronously the API result.
     */
    public void registerSource(
            @NonNull Uri attributionSource,
            @Nullable InputEvent inputEvent,
            @Nullable @CallbackExecutor Executor executor,
            @Nullable OutcomeReceiver<Void, AdServicesException> callback) {
        Objects.requireNonNull(attributionSource);
        register(
                new RegistrationRequest.Builder()
                .setRegistrationType(
                    RegistrationRequest.REGISTER_SOURCE)
                .setRegistrationUri(attributionSource)
                .setInputEvent(inputEvent)
                .setAttributionSource(mContext.getAttributionSource())
                .build(),
                executor, callback);
    }

    /**
     * Register an attribution source(click or view) from an embedded web context. This API will not
     * process any redirects, all registration URLs should be supplied with the request. At least
     * one of osDestination or webDestination parameters are required to be provided.
     *
     * @param request source registration request
     * @param executor used by callback to dispatch results.
     * @param callback intended to notify asynchronously the API result.
     * @hide
     */
    public void registerWebSource(
            @NonNull WebSourceRegistrationRequest request,
            @Nullable Executor executor,
            @Nullable OutcomeReceiver<Void, AdServicesException> callback) {
        Objects.requireNonNull(request);
        final IMeasurementService service = getService();

        try {
            service.registerWebSource(
                    new WebSourceRegistrationRequestInternal.Builder()
                            .setSourceRegistrationRequest(request)
                            .setAttributionSource(mContext.getAttributionSource())
                            .build(),
                    new IMeasurementCallback.Stub() {
                        @Override
                        public void onResult(int result) {
                            if (callback != null && executor != null) {
                                executor.execute(() -> callback.onResult(null));
                            }
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e("RemoteException", e);
            if (callback != null && executor != null) {
                executor.execute(() -> callback.onError(new AdServicesException("Internal Error")));
            }
        }
    }

    /**
     * Register an attribution trigger(click or view) from an embedded web context. This API will
     * not process any redirects, all registration URLs should be supplied with the request.
     *
     * @param request trigger registration request
     * @param executor used by callback to dispatch results
     * @param callback intended to notify asynchronously the API result
     * @hide
     */
    public void registerWebTrigger(
            @NonNull WebTriggerRegistrationRequest request,
            @Nullable Executor executor,
            @Nullable OutcomeReceiver<Void, AdServicesException> callback) {
        Objects.requireNonNull(request);
        final IMeasurementService service = getService();

        try {
            service.registerWebTrigger(
                    new WebTriggerRegistrationRequestInternal.Builder()
                            .setTriggerRegistrationRequest(request)
                            .setAttributionSource(mContext.getAttributionSource())
                            .build(),
                    new IMeasurementCallback.Stub() {
                        @Override
                        public void onResult(int result) {
                            if (callback != null && executor != null) {
                                executor.execute(() -> callback.onResult(null));
                            }
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e("RemoteException", e);
            if (callback != null && executor != null) {
                executor.execute(() -> callback.onError(new AdServicesException("Internal Error")));
            }
        }
    }

    /**
     * Register a trigger (conversion).
     *
     * @param trigger the API issues a request to this URI to fetch metadata associated with the
     *                trigger.
     * @param executor used by callback to dispatch results.
     * @param callback intended to notify asynchronously the API result.
     */
    public void registerTrigger(
            @NonNull Uri trigger,
            @Nullable @CallbackExecutor Executor executor,
            @Nullable OutcomeReceiver<Void, AdServicesException> callback) {
        Objects.requireNonNull(trigger);
        register(
                new RegistrationRequest.Builder()
                .setRegistrationType(
                    RegistrationRequest.REGISTER_TRIGGER)
                .setRegistrationUri(trigger)
                .setAttributionSource(mContext.getAttributionSource())
                .build(),
                executor, callback);
    }

    /**
     * Delete previously registered data.
     * @hide
     */
    public void deleteRegistrations(
            @NonNull DeletionRequest deletionRequest,
            @Nullable @CallbackExecutor Executor executor,
            @Nullable OutcomeReceiver<Void, AdServicesException> callback) {
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
                                    callback.onResult(null);
                                });
                            }
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e("RemoteException", e);
            if (callback != null && executor != null) {
                executor.execute(() ->
                        callback.onError(new AdServicesException("Internal Error"))
                );
            }
        }
    }

    /**
     * Delete previous registrations.
     * @hide
     */
    public void deleteRegistrations(
            @NonNull Uri origin,
            @Nullable Instant start,
            @Nullable Instant end,
            @Nullable @CallbackExecutor Executor executor,
            @Nullable OutcomeReceiver<Void, AdServicesException> callback) {
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
     * Get Measurement API status.
     *
     * @param executor used by callback to dispatch results.
     * @param callback intended to notify asynchronously the API result.
     *
     * The callback's {@code Integer} value is one of {@code MeasurementApiState}.
     */
    public void getMeasurementApiStatus(
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Integer, AdServicesException> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        // TODO: Remove here and apply across the board.
        if (AdServicesApiUtil.getAdServicesApiState()
                == AdServicesApiUtil.ADSERVICES_API_STATE_DISABLED) {
            executor.execute(() -> {
                callback.onResult(MeasurementApiUtil.MEASUREMENT_API_STATE_DISABLED);
            });
            return;
        }

        final IMeasurementService service = getService();

        try {
            service.getMeasurementApiStatus(
                    new IMeasurementApiStatusCallback.Stub() {
                        @Override
                        public void onResult(int result) {
                            executor.execute(() -> callback.onResult(result));
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e("RemoteException", e);
            executor.execute(() ->
                    callback.onError(new AdServicesException("Internal Error")));
        }
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
