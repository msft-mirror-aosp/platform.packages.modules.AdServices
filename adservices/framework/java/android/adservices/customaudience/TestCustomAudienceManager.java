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

package android.adservices.customaudience;

import android.adservices.common.FledgeErrorResponse;
import android.adservices.exceptions.AdServicesException;
import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.os.OutcomeReceiver;
import android.os.RemoteException;

import com.android.adservices.LogUtil;

import java.util.Objects;
import java.util.concurrent.Executor;

/** TestCustomAudienceManager provides APIs for app and ad-SDKs to test custom audiences. */
public class TestCustomAudienceManager {

    private final CustomAudienceManager mCustomAudienceManager;

    TestCustomAudienceManager(@NonNull CustomAudienceManager customAudienceManager) {
        Objects.requireNonNull(customAudienceManager);

        mCustomAudienceManager = customAudienceManager;
    }

    /**
     * Overrides the Custom Audience API to avoid fetching data from remote servers and use the data
     * provided in {@link AddCustomAudienceOverrideRequest} instead. The {@link
     * AddCustomAudienceOverrideRequest} is provided by the Ads SDK.
     *
     * <p>This method is intended to be used for end-to-end testing. This API is enabled only for
     * apps in debug mode with developer options enabled.
     *
     * <p>This call will fail silently if the {@code owner} in the {@code request} is not the
     * calling app's package name.
     *
     * @throws IllegalStateException if this API is not enabled for the caller
     *     <p>The receiver either returns a {@code void} for a successful run, or an {@link
     *     AdServicesException} indicates the error.
     */
    public void overrideCustomAudienceRemoteInfo(
            @NonNull AddCustomAudienceOverrideRequest request,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Object, AdServicesException> receiver) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);
        try {
            final ICustomAudienceService service = mCustomAudienceManager.getService();
            service.overrideCustomAudienceRemoteInfo(
                    request.getOwnerPackageName(),
                    request.getBuyer(),
                    request.getName(),
                    request.getBiddingLogicJs(),
                    request.getTrustedBiddingSignals(),
                    new CustomAudienceOverrideCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            executor.execute(() -> receiver.onResult(new Object()));
                        }

                        @Override
                        public void onFailure(FledgeErrorResponse failureParcel) {
                            executor.execute(() -> receiver.onError(failureParcel.asException()));
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e(e, "Exception");
            receiver.onError(new AdServicesException("Internal Error!"));
        }
    }
    /**
     * Removes an override in th Custom Audience API with associated the data in {@link
     * RemoveCustomAudienceOverrideRequest}.
     *
     * <p>This method is intended to be used for end-to-end testing. This API is enabled only for
     * apps in debug mode with developer options enabled.
     *
     * @throws IllegalStateException if this API is not enabled for the caller
     *     <p>The {@link RemoveCustomAudienceOverrideRequest} is provided by the Ads SDK. The
     *     receiver either returns a {@code void} for a successful run, or an {@link
     *     AdServicesException} indicates the error.
     */
    public void removeCustomAudienceRemoteInfoOverride(
            @NonNull RemoveCustomAudienceOverrideRequest request,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Object, AdServicesException> receiver) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);
        try {
            final ICustomAudienceService service = mCustomAudienceManager.getService();
            service.removeCustomAudienceRemoteInfoOverride(
                    request.getOwnerPackageName(),
                    request.getBuyer(),
                    request.getName(),
                    new CustomAudienceOverrideCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            executor.execute(() -> receiver.onResult(new Object()));
                        }

                        @Override
                        public void onFailure(FledgeErrorResponse failureParcel) {
                            executor.execute(() -> receiver.onError(failureParcel.asException()));
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e(e, "Exception");
            receiver.onError(new AdServicesException("Internal Error!"));
        }
    }
    /**
     * Removes all override data in the Custom Audience API.
     *
     * <p>This method is intended to be used for end-to-end testing. This API is enabled only for
     * apps in debug mode with developer options enabled.
     *
     * @throws IllegalStateException if this API is not enabled for the caller
     *     <p>The receiver either returns a {@code void} for a successful run, or an {@link
     *     AdServicesException} indicates the error.
     */
    public void resetAllCustomAudienceOverrides(
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Object, AdServicesException> receiver) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);
        try {
            final ICustomAudienceService service = mCustomAudienceManager.getService();
            service.resetAllCustomAudienceOverrides(
                    new CustomAudienceOverrideCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            executor.execute(() -> receiver.onResult(new Object()));
                        }

                        @Override
                        public void onFailure(FledgeErrorResponse failureParcel) {
                            executor.execute(() -> receiver.onError(failureParcel.asException()));
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e(e, "Exception");
            receiver.onError(new AdServicesException("Internal Error!"));
        }
    }
}
