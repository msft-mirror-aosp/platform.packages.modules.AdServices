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

import static android.adservices.common.AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.FledgeErrorResponse;
import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.os.OutcomeReceiver;
import android.os.RemoteException;

import com.android.adservices.LogUtil;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * TestAdSelectionManager provides APIs for app and ad-SDKs to test ad selection processes as well
 * as report impressions.
 */
public class TestAdSelectionManager {

    private final AdSelectionManager mAdSelectionManager;

    TestAdSelectionManager(@NonNull AdSelectionManager adSelectionManager) {
        Objects.requireNonNull(adSelectionManager);

        mAdSelectionManager = adSelectionManager;
    }

    /**
     * Overrides the AdSelection API for a given {@link AdSelectionConfig} to avoid fetching data
     * from remote servers and use the data provided in {@link AddAdSelectionOverrideRequest}
     * instead. The {@link AddAdSelectionOverrideRequest} is provided by the Ads SDK.
     *
     * <p>This method is intended to be used for end-to-end testing. This API is enabled only for
     * apps in debug mode with developer options enabled.
     *
     * @throws IllegalStateException if this API is not enabled for the caller
     *     <p>The receiver either returns a {@code void} for a successful run, or an {@link
     *     Exception} indicates the error.
     */
    @RequiresPermission(ACCESS_ADSERVICES_CUSTOM_AUDIENCE)
    public void overrideAdSelectionConfigRemoteInfo(
            @NonNull AddAdSelectionOverrideRequest request,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Object, Exception> receiver) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);

        try {
            final AdSelectionService service = mAdSelectionManager.getService();
            service.overrideAdSelectionConfigRemoteInfo(
                    request.getAdSelectionConfig(),
                    request.getDecisionLogicJs(),
                    request.getTrustedScoringSignals(),
                    new AdSelectionOverrideCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            executor.execute(() -> receiver.onResult(new Object()));
                        }

                        @Override
                        public void onFailure(FledgeErrorResponse failureParcel) {
                            executor.execute(
                                    () ->
                                            receiver.onError(
                                                    AdServicesStatusUtils.asException(
                                                            failureParcel)));
                        }
                    });
        } catch (NullPointerException e) {
            LogUtil.e(e, "Unable to find the AdSelection service.");
            receiver.onError(
                    new IllegalStateException("Unable to find the AdSelection service.", e));
        } catch (RemoteException e) {
            LogUtil.e(e, "Exception");
            receiver.onError(new IllegalStateException("Failure of AdSelection service.", e));
        }
    }

    /**
     * Removes an override for {@link AdSelectionConfig} in the Ad Selection API with associated the
     * data in {@link RemoveAdSelectionOverrideRequest}. The {@link
     * RemoveAdSelectionOverrideRequest} is provided by the Ads SDK.
     *
     * <p>This method is intended to be used for end-to-end testing. This API is enabled only for
     * apps in debug mode with developer options enabled.
     *
     * @throws IllegalStateException if this API is not enabled for the caller
     *     <p>The receiver either returns a {@code void} for a successful run, or an {@link
     *     Exception} indicates the error.
     */
    @RequiresPermission(ACCESS_ADSERVICES_CUSTOM_AUDIENCE)
    public void removeAdSelectionConfigRemoteInfoOverride(
            @NonNull RemoveAdSelectionOverrideRequest request,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Object, Exception> receiver) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);

        try {
            final AdSelectionService service = mAdSelectionManager.getService();
            service.removeAdSelectionConfigRemoteInfoOverride(
                    request.getAdSelectionConfig(),
                    new AdSelectionOverrideCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            executor.execute(() -> receiver.onResult(new Object()));
                        }

                        @Override
                        public void onFailure(FledgeErrorResponse failureParcel) {
                            executor.execute(
                                    () ->
                                            receiver.onError(
                                                    AdServicesStatusUtils.asException(
                                                            failureParcel)));
                        }
                    });
        } catch (NullPointerException e) {
            LogUtil.e(e, "Unable to find the AdSelection service.");
            receiver.onError(
                    new IllegalStateException("Unable to find the AdSelection service.", e));
        } catch (RemoteException e) {
            LogUtil.e(e, "Exception");
            receiver.onError(new IllegalStateException("Failure of AdSelection service.", e));
        }
    }

    /**
     * Removes all override data for {@link AdSelectionConfig} in the Ad Selection API.
     *
     * <p>This method is intended to be used for end-to-end testing. This API is enabled only for
     * apps in debug mode with developer options enabled.
     *
     * @throws IllegalStateException if this API is not enabled for the caller
     *     <p>The receiver either returns a {@code void} for a successful run, or an {@link
     *     Exception} indicates the error.
     */
    @RequiresPermission(ACCESS_ADSERVICES_CUSTOM_AUDIENCE)
    public void resetAllAdSelectionConfigRemoteOverrides(
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Object, Exception> receiver) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);

        try {
            final AdSelectionService service = mAdSelectionManager.getService();
            service.resetAllAdSelectionConfigRemoteOverrides(
                    new AdSelectionOverrideCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            executor.execute(() -> receiver.onResult(new Object()));
                        }

                        @Override
                        public void onFailure(FledgeErrorResponse failureParcel) {
                            executor.execute(
                                    () ->
                                            receiver.onError(
                                                    AdServicesStatusUtils.asException(
                                                            failureParcel)));
                        }
                    });
        } catch (NullPointerException e) {
            LogUtil.e(e, "Unable to find the AdSelection service.");
            receiver.onError(
                    new IllegalStateException("Unable to find the AdSelection service.", e));
        } catch (RemoteException e) {
            LogUtil.e(e, "Exception");
            receiver.onError(new IllegalStateException("Failure of AdSelection service.", e));
        }
    }

    /**
     * Overrides the AdSelection API for {@link AdSelectionFromOutcomesConfig} to avoid fetching
     * data from remote servers and use the data provided in {@link
     * AddAdSelectionFromOutcomesOverrideRequest} instead. The {@link
     * AddAdSelectionFromOutcomesOverrideRequest} is provided by the Ads SDK.
     *
     * <p>This method is intended to be used for end-to-end testing. This API is enabled only for
     * apps in debug mode with developer options enabled.
     *
     * @throws IllegalStateException if this API is not enabled for the caller
     *     <p>The receiver either returns a {@code void} for a successful run, or an {@link
     *     Exception} indicates the error.
     * @hide
     */
    @RequiresPermission(ACCESS_ADSERVICES_CUSTOM_AUDIENCE)
    public void overrideAdSelectionFromOutcomesConfigRemoteInfo(
            @NonNull AddAdSelectionFromOutcomesOverrideRequest request,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Object, Exception> receiver) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);

        try {
            final AdSelectionService service = mAdSelectionManager.getService();
            service.overrideAdSelectionFromOutcomesConfigRemoteInfo(
                    request.getAdSelectionConfig(),
                    request.getSelectionLogicJs(),
                    request.getSelectionSignals(),
                    new AdSelectionOverrideCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            executor.execute(() -> receiver.onResult(new Object()));
                        }

                        @Override
                        public void onFailure(FledgeErrorResponse failureParcel) {
                            executor.execute(
                                    () ->
                                            receiver.onError(
                                                    AdServicesStatusUtils.asException(
                                                            failureParcel)));
                        }
                    });
        } catch (NullPointerException e) {
            LogUtil.e(e, "Unable to find the AdSelection service.");
            receiver.onError(
                    new IllegalStateException("Unable to find the AdSelection service.", e));
        } catch (RemoteException e) {
            LogUtil.e(e, "Exception");
            receiver.onError(new IllegalStateException("Failure of AdSelection service.", e));
        }
    }

    /**
     * Removes an override for {@link AdSelectionFromOutcomesConfig} in th Ad Selection API with
     * associated the data in {@link RemoveAdSelectionOverrideRequest}. The {@link
     * RemoveAdSelectionOverrideRequest} is provided by the Ads SDK.
     *
     * <p>This method is intended to be used for end-to-end testing. This API is enabled only for
     * apps in debug mode with developer options enabled.
     *
     * @throws IllegalStateException if this API is not enabled for the caller
     *     <p>The receiver either returns a {@code void} for a successful run, or an {@link
     *     Exception} indicates the error.
     * @hide
     */
    @RequiresPermission(ACCESS_ADSERVICES_CUSTOM_AUDIENCE)
    public void removeAdSelectionFromOutcomesConfigRemoteInfoOverride(
            @NonNull RemoveAdSelectionFromOutcomesOverrideRequest request,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Object, Exception> receiver) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);

        try {
            final AdSelectionService service = mAdSelectionManager.getService();
            service.removeAdSelectionFromOutcomesConfigRemoteInfoOverride(
                    request.getAdSelectionFromOutcomesConfig(),
                    new AdSelectionOverrideCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            executor.execute(() -> receiver.onResult(new Object()));
                        }

                        @Override
                        public void onFailure(FledgeErrorResponse failureParcel) {
                            executor.execute(
                                    () ->
                                            receiver.onError(
                                                    AdServicesStatusUtils.asException(
                                                            failureParcel)));
                        }
                    });
        } catch (NullPointerException e) {
            LogUtil.e(e, "Unable to find the AdSelection service.");
            receiver.onError(
                    new IllegalStateException("Unable to find the AdSelection service.", e));
        } catch (RemoteException e) {
            LogUtil.e(e, "Exception");
            receiver.onError(new IllegalStateException("Failure of AdSelection service.", e));
        }
    }

    /**
     * Removes all override data for {@link AdSelectionFromOutcomesConfig} in the Ad Selection API.
     *
     * <p>This method is intended to be used for end-to-end testing. This API is enabled only for
     * apps in debug mode with developer options enabled.
     *
     * @throws IllegalStateException if this API is not enabled for the caller
     *     <p>The receiver either returns a {@code void} for a successful run, or an {@link
     *     Exception} indicates the error.
     * @hide
     */
    @RequiresPermission(ACCESS_ADSERVICES_CUSTOM_AUDIENCE)
    public void resetAllAdSelectionFromOutcomesConfigRemoteOverrides(
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Object, Exception> receiver) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);

        try {
            final AdSelectionService service = mAdSelectionManager.getService();
            service.resetAllAdSelectionFromOutcomesConfigRemoteOverrides(
                    new AdSelectionOverrideCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            executor.execute(() -> receiver.onResult(new Object()));
                        }

                        @Override
                        public void onFailure(FledgeErrorResponse failureParcel) {
                            executor.execute(
                                    () ->
                                            receiver.onError(
                                                    AdServicesStatusUtils.asException(
                                                            failureParcel)));
                        }
                    });
        } catch (NullPointerException e) {
            LogUtil.e(e, "Unable to find the AdSelection service.");
            receiver.onError(
                    new IllegalStateException("Unable to find the AdSelection service.", e));
        } catch (RemoteException e) {
            LogUtil.e(e, "Exception");
            receiver.onError(new IllegalStateException("Failure of AdSelection service.", e));
        }
    }
}
