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

import static com.android.adservices.ResultCode.RESULT_INTERNAL_ERROR;
import static com.android.adservices.ResultCode.RESULT_INVALID_ARGUMENT;
import static com.android.adservices.ResultCode.RESULT_IO_ERROR;
import static com.android.adservices.ResultCode.RESULT_OK;
import static com.android.adservices.ResultCode.RESULT_UNAUTHORIZED_CALL;

import android.adservices.AdServicesApiUtil;
import android.adservices.exceptions.AdServicesException;
import android.adservices.exceptions.MeasurementException;
import android.annotation.CallbackExecutor;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.sdksandbox.SandboxedSdkContext;
import android.content.Context;
import android.net.Uri;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.view.InputEvent;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.LogUtil;
import com.android.adservices.ServiceBinder;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * MeasurementManager.
 */
public class MeasurementManager {

    // TODO (b/237295093): Remove the codes and just surface the corresponding Java standatd
    //  exceptions.
    /**
     * Result codes from {@link MeasurementManager} methods.
     *
     * @hide
     */
    @IntDef(
            value = {
                RESULT_OK,
                RESULT_INTERNAL_ERROR,
                RESULT_UNAUTHORIZED_CALL,
                RESULT_INVALID_ARGUMENT,
                RESULT_IO_ERROR,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ResultCode {}

    /** @hide */
    public static final String MEASUREMENT_SERVICE = "measurement_service";

     /**
     * This state indicates that Measurement APIs are unavailable.
     * Invoking them will result in an {@link UnsupportedOperationException}.
     */
    public static final int MEASUREMENT_API_STATE_DISABLED = 0;

    /**
     * This state indicates that Measurement APIs are enabled.
     */
    public static final int MEASUREMENT_API_STATE_ENABLED = 1;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "MEASUREMENT_API_STATE_", value = {
            MEASUREMENT_API_STATE_DISABLED,
            MEASUREMENT_API_STATE_ENABLED,
    })
    public @interface MeasurementApiState {}

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

    /**
     * Retrieves an {@link IMeasurementService} implementation
     *
     * @hide
     */
    @VisibleForTesting
    @NonNull
    public IMeasurementService getService() {
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
                        public void onResult() {
                            if (callback != null && executor != null) {
                                executor.execute(
                                        () -> {
                                            callback.onResult(null);
                                        });
                            }
                        }

                        @Override
                        public void onFailure(MeasurementErrorResponse failureParcel) {
                            if (callback != null
                                    && executor != null
                                    && failureParcel.getStatusCode() == RESULT_UNAUTHORIZED_CALL) {
                                executor.execute(
                                        () -> {
                                            callback.onError(failureParcel.asException());
                                        });
                            }
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e(e, "RemoteException");
            if (callback != null && executor != null) {
                executor.execute(
                        () -> callback.onError(new AdServicesException("Internal Error", e)));
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
                        .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                        .setRegistrationUri(attributionSource)
                        .setInputEvent(inputEvent)
                        .setPackageName(getPackageName())
                        .build(),
                executor,
                callback);
    }

    /**
     * Register an attribution source(click or view) from web context. This API will not process any
     * redirects, all registration URLs should be supplied with the request. At least one of
     * appDestination or webDestination parameters are required to be provided. If the registration
     * is successful, {@code callback}'s {@link OutcomeReceiver#onResult} is invoked with null. In
     * case of failure, a {@link MeasurementException} is sent through {@code callback}'s {@link
     * OutcomeReceiver#onError}. Both success and failure feedback are executed on the provided
     * {@link Executor}.
     *
     * @param request source registration request
     * @param executor used by callback to dispatch results.
     * @param callback intended to notify asynchronously the API result.
     */
    public void registerWebSource(
            @NonNull WebSourceRegistrationRequest request,
            @Nullable Executor executor,
            @Nullable OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(request);
        final IMeasurementService service = getService();

        try {
            service.registerWebSource(
                    new WebSourceRegistrationRequestInternal.Builder(request, getPackageName())
                            .build(),
                    new IMeasurementCallback.Stub() {
                        @Override
                        public void onResult() {
                            if (callback != null && executor != null) {
                                executor.execute(() -> callback.onResult(null));
                            }
                        }

                        @Override
                        public void onFailure(MeasurementErrorResponse failureParcel) {
                            if (callback != null
                                    && executor != null
                                    && failureParcel.getStatusCode() == RESULT_UNAUTHORIZED_CALL) {
                                executor.execute(
                                        () -> {
                                            callback.onError(failureParcel.asException());
                                        });
                            }
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e(e, "RemoteException");
            if (callback != null && executor != null) {
                executor.execute(
                        () -> callback.onError(new MeasurementException("Internal Error", e)));
            }
        }
    }

    /**
     * Register an attribution trigger(click or view) from web context. This API will not process
     * any redirects, all registration URLs should be supplied with the request. If the registration
     * is successful, {@code callback}'s {@link OutcomeReceiver#onResult} is invoked with null. In
     * case of failure, a {@link MeasurementException} is sent through {@code callback}'s {@link
     * OutcomeReceiver#onError}. Both success and failure feedback are executed on the provided
     * {@link Executor}.
     *
     * @param request trigger registration request
     * @param executor used by callback to dispatch results
     * @param callback intended to notify asynchronously the API result
     */
    public void registerWebTrigger(
            @NonNull WebTriggerRegistrationRequest request,
            @Nullable Executor executor,
            @Nullable OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(request);
        final IMeasurementService service = getService();

        try {
            service.registerWebTrigger(
                    new WebTriggerRegistrationRequestInternal.Builder(request, getPackageName())
                            .build(),
                    new IMeasurementCallback.Stub() {
                        @Override
                        public void onResult() {
                            if (callback != null && executor != null) {
                                executor.execute(() -> callback.onResult(null));
                            }
                        }

                        @Override
                        public void onFailure(MeasurementErrorResponse failureParcel) {
                            if (callback != null
                                    && executor != null
                                    && failureParcel.getStatusCode() == RESULT_UNAUTHORIZED_CALL) {
                                executor.execute(
                                        () -> {
                                            callback.onError(failureParcel.asException());
                                        });
                            }
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e(e, "RemoteException");
            if (callback != null && executor != null) {
                executor.execute(
                        () -> callback.onError(new MeasurementException("Internal Error", e)));
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
                        .setRegistrationType(RegistrationRequest.REGISTER_TRIGGER)
                        .setRegistrationUri(trigger)
                        .setPackageName(getPackageName())
                        .build(),
                executor,
                callback);
    }

    /**
     * Delete previously registered data.
     *
     * @hide
     */
    private void deleteRegistrations(
            @NonNull DeletionParam deletionParam,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(deletionParam);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        final IMeasurementService service = getService();

        try {
            service.deleteRegistrations(
                    deletionParam,
                    new IMeasurementCallback.Stub() {
                        @Override
                        public void onResult() {
                            executor.execute(() -> callback.onResult(null));
                        }

                        @Override
                        public void onFailure(MeasurementErrorResponse failureParcel) {
                            executor.execute(
                                    () -> {
                                        callback.onError(failureParcel.asException());
                                    });
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e(e, "RemoteException");
            executor.execute(() -> callback.onError(new MeasurementException("Internal Error", e)));
        }
    }

    /**
     * Delete previous registrations. If the deletion is successful, the callback's {@link
     * OutcomeReceiver#onResult} is invoked with null. In case of failure, a {@link Exception} is
     * sent through the callback's {@link OutcomeReceiver#onError}. Both success and failure
     * feedback are executed on the provided {@link Executor}.
     *
     * @param deletionRequest The request for deleting data.
     * @param executor The executor to run callback.
     * @param callback intended to notify asynchronously the API result.
     */
    public void deleteRegistrations(
            @NonNull DeletionRequest deletionRequest,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback) {
        deleteRegistrations(
                new DeletionParam.Builder()
                        .setOriginUris(deletionRequest.getOriginUris())
                        .setDomainUris(deletionRequest.getDomainUris())
                        .setDeletionMode(deletionRequest.getDeletionMode())
                        .setMatchBehavior(deletionRequest.getMatchBehavior())
                        .setStart(deletionRequest.getStart())
                        .setEnd(deletionRequest.getEnd())
                        .setPackageName(getPackageName())
                        .build(),
                executor,
                callback);
    }

    /**
     * Get Measurement API status.
     *
     * <p>The callback's {@code Integer} value is one of {@code MeasurementApiState}.
     *
     * @param executor used by callback to dispatch results.
     * @param callback intended to notify asynchronously the API result.
     */
    public void getMeasurementApiStatus(
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Integer, Exception> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        // TODO: Remove here and apply across the board.
        if (AdServicesApiUtil.getAdServicesApiState()
                == AdServicesApiUtil.ADSERVICES_API_STATE_DISABLED) {
            executor.execute(() -> {
                callback.onResult(MEASUREMENT_API_STATE_DISABLED);
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
            LogUtil.e(e, "RemoteException");
            executor.execute(() -> callback.onError(new AdServicesException("Internal Error", e)));
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

    /** Returns the client's (caller's) package name from the SDK or app context */
    private String getPackageName() {
        if (mContext instanceof SandboxedSdkContext) {
            return ((SandboxedSdkContext) mContext).getClientPackageName();
        } else {
            return mContext.getPackageName();
        }
    }
}
