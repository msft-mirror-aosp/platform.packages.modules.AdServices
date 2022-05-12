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

package android.adservices.adselection;

import android.adservices.common.FledgeErrorResponse;
import android.adservices.exceptions.AdServicesException;
import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.content.Context;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.os.TransactionTooLargeException;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.LogUtil;
import com.android.adservices.ServiceBinder;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * AdSelection Manager provides APIs for app and ad-SDKs to run ad selection processes as well
 * as report impressions.
 */
public class AdSelectionManager {
    public static final String AD_SELECTION_SERVICE = "ad_selection_service";

    private final Context mContext;

    private final ServiceBinder<AdSelectionService> mServiceBinder;

    /**
     * Create AdSelectionManager
     *
     * @hide
     */
    public AdSelectionManager(@NonNull Context context) {
        Objects.requireNonNull(context);

        mContext = context;
        mServiceBinder =
                ServiceBinder.getServiceBinder(
                        context,
                        AdServicesCommon.ACTION_AD_SELECTION_SERVICE,
                        AdSelectionService.Stub::asInterface);
    }

    @NonNull
    private AdSelectionService getService() {
        AdSelectionService service = mServiceBinder.getService();
        if (service == null) {
            throw new IllegalStateException("Unable to find the ad selection service");
        }
        return service;
    }

    /**
     * Runs the ad selection process on device to select a remarketing ad for the caller
     * application.
     * <p>
     * The input {@code adSelectionConfig} is provided by the Ads SDK and the
     * {@link AdSelectionConfig} object is transferred via a Binder call. For this reason, the
     * total size of these objects is bound to the Android IPC limitations. Failures to transfer the
     * {@link AdSelectionConfig} will throws an {@link TransactionTooLargeException}.
     * <p>
     * The output is passed by the receiver, which either returns an {@link AdSelectionOutcome} for
     * a successful run, or an {@link AdServicesException} includes the type of the exception thrown
     * and the corresponding error message.
     * <p>
     * If the result of the {@link AdServicesException#getCause} is an {@link
     * IllegalArgumentException}, it is caused by invalid input argument the API received to run the
     * ad selection.
     * <p>
     * If the result of the {@link AdServicesException#getCause} is an {@link RemoteException} with
     * error message "Failure of AdSelection services.", it is caused by an internal failure of the
     * ad selection service.
     */
    public void runAdSelection(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull Executor executor,
            @NonNull OutcomeReceiver<AdSelectionOutcome, AdServicesException> receiver) {
        Objects.requireNonNull(adSelectionConfig);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);

        try {
            final AdSelectionService service = getService();
            service.runAdSelection(
                    adSelectionConfig,
                    new AdSelectionCallback.Stub() {
                        @Override
                        public void onSuccess(AdSelectionResponse resultParcel) {
                            executor.execute(
                                    () -> {
                                        receiver.onResult(
                                                new AdSelectionOutcome.Builder()
                                                        .setAdSelectionId(
                                                                resultParcel.getAdSelectionId())
                                                        .setRenderUrl(resultParcel.getRenderUrl())
                                                        .build());
                                    });
                        }

                        @Override
                        public void onFailure(FledgeErrorResponse failureParcel) {
                            executor.execute(
                                    () -> {
                                        receiver.onError(failureParcel.asException());
                                    });
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e("Failure of AdSelection service.", e);
            receiver.onError(new AdServicesException("Failure of AdSelection service."));
        }
    }

    /**
     * Report the given impression. The {@link ReportImpressionRequest} is provided by the Ads SDK.
     * The receiver either returns a {@code void} for a successful run, or an {@link
     * AdServicesException} indicates the error.
     */
    @NonNull
    public void reportImpression(
            @NonNull ReportImpressionRequest request,
            @NonNull Executor executor,
            @NonNull OutcomeReceiver<Void, AdServicesException> receiver) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);

        try {
            final AdSelectionService service = getService();
            service.reportImpression(
                    new ReportImpressionInput.Builder()
                            .setAdSelectionId(request.getAdSelectionId())
                            .setAdSelectionConfig(request.getAdSelectionConfig())
                            .build(),
                    new ReportImpressionCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            executor.execute(
                                    () -> {
                                        receiver.onResult(null);
                                    });
                        }

                        @Override
                        public void onFailure(FledgeErrorResponse failureParcel) {
                            executor.execute(
                                    () -> {
                                        receiver.onError(failureParcel.asException());
                                    });
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e("Exception", e);
            receiver.onError(new AdServicesException("Internal Error!"));
        }
    }

    /**
     * Overrides the AdSelection API to avoid fetching data from remote servers and use the data
     * provided in {@link AddAdSelectionOverrideRequest} instead. The {@link
     * AddAdSelectionOverrideRequest} is provided by the Ads SDK. The receiver either returns a
     * {@code void} for a successful run, or an {@link AdServicesException} indicates the error.
     *
     * @hide
     */
    @NonNull
    public void overrideAdSelectionConfigRemoteInfo(
            @NonNull AddAdSelectionOverrideRequest request,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, AdServicesException> receiver) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);

        try {
            final AdSelectionService service = getService();
            service.overrideAdSelectionConfigRemoteInfo(
                    request.getAdSelectionConfig(),
                    request.getDecisionLogicJs(),
                    new AdSelectionOverrideCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            executor.execute(
                                    () -> {
                                        receiver.onResult(null);
                                    });
                        }

                        @Override
                        public void onFailure(FledgeErrorResponse failureParcel) {
                            executor.execute(
                                    () -> {
                                        receiver.onError(failureParcel.asException());
                                    });
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e("Exception", e);
            receiver.onError(new AdServicesException("Internal Error!"));
        }
    }

    /**
     * Removes an override in th Ad Selection API with associated the data in {@link
     * RemoveAdSelectionOverrideRequest}. The {@link RemoveAdSelectionOverrideRequest} is provided
     * by the Ads SDK. The receiver either returns a {@code void} for a successful run, or an {@link
     * AdServicesException} indicates the error.
     *
     * @hide
     */
    @NonNull
    public void removeAdSelectionConfigRemoteInfoOverride(
            @NonNull RemoveAdSelectionOverrideRequest request,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, AdServicesException> receiver) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);

        try {
            final AdSelectionService service = getService();
            service.removeAdSelectionConfigRemoteInfoOverride(
                    request.getAdSelectionConfig(),
                    new AdSelectionOverrideCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            executor.execute(
                                    () -> {
                                        receiver.onResult(null);
                                    });
                        }

                        @Override
                        public void onFailure(FledgeErrorResponse failureParcel) {
                            executor.execute(
                                    () -> {
                                        receiver.onError(failureParcel.asException());
                                    });
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e("Exception", e);
            receiver.onError(new AdServicesException("Internal Error!"));
        }
    }

    /**
     * Removes all override data in the Ad Selection API. The receiver either returns a {@code void}
     * for a successful run, or an {@link AdServicesException} indicates the error.
     *
     * @hide
     */
    @NonNull
    public void resetAllAdSelectionConfigRemoteOverrides(
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, AdServicesException> receiver) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);

        try {
            final AdSelectionService service = getService();
            service.resetAllAdSelectionConfigRemoteOverrides(
                    new AdSelectionOverrideCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            executor.execute(
                                    () -> {
                                        receiver.onResult(null);
                                    });
                        }

                        @Override
                        public void onFailure(FledgeErrorResponse failureParcel) {
                            executor.execute(
                                    () -> {
                                        receiver.onError(failureParcel.asException());
                                    });
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e("Exception", e);
            receiver.onError(new AdServicesException("Internal Error!"));
        }
    }
}
