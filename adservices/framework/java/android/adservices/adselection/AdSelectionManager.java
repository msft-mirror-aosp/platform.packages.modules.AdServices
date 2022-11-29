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
import android.adservices.common.CallerMetadata;
import android.adservices.common.FledgeErrorResponse;
import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.app.sdksandbox.SandboxedSdkContext;
import android.content.Context;
import android.os.LimitExceededException;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.TransactionTooLargeException;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.LogUtil;
import com.android.adservices.ServiceBinder;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

/**
 * AdSelection Manager provides APIs for app and ad-SDKs to run ad selection processes as well
 * as report impressions.
 */
public class AdSelectionManager {
    /**
     * Constant that represents the service name for {@link AdSelectionManager} to be used in {@link
     * android.adservices.AdServicesFrameworkInitializer#registerServiceWrappers}
     *
     * @hide
     */
    public static final String AD_SELECTION_SERVICE = "ad_selection_service";

    @NonNull private Context mContext;
    @NonNull private ServiceBinder<AdSelectionService> mServiceBinder;

    /**
     * Create AdSelectionManager
     *
     * @hide
     */
    public AdSelectionManager(@NonNull Context context) {
        Objects.requireNonNull(context);

        // In case the AdSelectionManager is initiated from inside a sdk_sandbox process the
        // fields will be immediately rewritten by the initialize method below.
        initialize(context);
    }

    /**
     * Initializes {@link AdSelectionManager} with the given {@code context}.
     *
     * <p>This method is called by the {@link SandboxedSdkContext} to propagate the correct context.
     * For more information check the javadoc on the {@link
     * android.app.sdksandbox.SdkSandboxSystemServiceRegistry}.
     *
     * @hide
     * @see android.app.sdksandbox.SdkSandboxSystemServiceRegistry
     */
    public AdSelectionManager initialize(@NonNull Context context) {
        Objects.requireNonNull(context);

        mContext = context;
        mServiceBinder =
                ServiceBinder.getServiceBinder(
                        context,
                        AdServicesCommon.ACTION_AD_SELECTION_SERVICE,
                        AdSelectionService.Stub::asInterface);
        return this;
    }

    @NonNull
    public TestAdSelectionManager getTestAdSelectionManager() {
        return new TestAdSelectionManager(this);
    }

    @NonNull
    AdSelectionService getService() {
        return mServiceBinder.getService();
    }

    /**
     * Runs the ad selection process on device to select a remarketing ad for the caller
     * application.
     *
     * <p>The input {@code adSelectionConfig} is provided by the Ads SDK and the {@link
     * AdSelectionConfig} object is transferred via a Binder call. For this reason, the total size
     * of these objects is bound to the Android IPC limitations. Failures to transfer the {@link
     * AdSelectionConfig} will throws an {@link TransactionTooLargeException}.
     *
     * <p>The output is passed by the receiver, which either returns an {@link AdSelectionOutcome}
     * for a successful run, or an {@link Exception} includes the type of the exception thrown and
     * the corresponding error message.
     *
     * <p>If the {@link IllegalArgumentException} is thrown, it is caused by invalid input argument
     * the API received to run the ad selection.
     *
     * <p>If the {@link IllegalStateException} is thrown with error message "Failure of AdSelection
     * services.", it is caused by an internal failure of the ad selection service.
     *
     * <p>If the {@link TimeoutException} is thrown, it is caused when a timeout is encountered
     * during bidding, scoring, or overall selection process to find winning Ad.
     *
     * <p>If the {@link LimitExceededException} is thrown, it is caused when the calling package
     * exceeds the allowed rate limits and is throttled.
     */
    @RequiresPermission(ACCESS_ADSERVICES_CUSTOM_AUDIENCE)
    public void selectAds(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<AdSelectionOutcome, Exception> receiver) {
        Objects.requireNonNull(adSelectionConfig);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);

        try {
            final AdSelectionService service = getService();
            service.selectAds(
                    new AdSelectionInput.Builder()
                            .setAdSelectionConfig(adSelectionConfig)
                            .setCallerPackageName(getCallerPackageName())
                            .build(),
                    new CallerMetadata.Builder()
                            .setBinderElapsedTimestamp(SystemClock.elapsedRealtime())
                            .build(),
                    new AdSelectionCallback.Stub() {
                        @Override
                        public void onSuccess(AdSelectionResponse resultParcel) {
                            executor.execute(
                                    () ->
                                            receiver.onResult(
                                                    new AdSelectionOutcome.Builder()
                                                            .setAdSelectionId(
                                                                    resultParcel.getAdSelectionId())
                                                            .setRenderUri(
                                                                    resultParcel.getRenderUri())
                                                            .build()));
                        }

                        @Override
                        public void onFailure(FledgeErrorResponse failureParcel) {
                            executor.execute(
                                    () -> {
                                        receiver.onError(
                                                AdServicesStatusUtils.asException(failureParcel));
                                    });
                        }
                    });
        } catch (NullPointerException e) {
            LogUtil.e(e, "Unable to find the AdSelection service.");
            receiver.onError(
                    new IllegalStateException("Unable to find the AdSelection service.", e));
        } catch (RemoteException e) {
            LogUtil.e(e, "Failure of AdSelection service.");
            receiver.onError(new IllegalStateException("Failure of AdSelection service.", e));
        }
    }

    /**
     * Selects an ad from the results of previously ran ad selections.
     *
     * <p>The input {@code adSelectionFromOutcomesConfig} is provided by the Ads SDK and the {@link
     * AdSelectionFromOutcomesConfig} object is transferred via a Binder call. For this reason, the
     * total size of these objects is bound to the Android IPC limitations. Failures to transfer the
     * {@link AdSelectionFromOutcomesConfig} will throws an {@link TransactionTooLargeException}.
     *
     * <p>The output is passed by the receiver, which either returns an {@link AdSelectionOutcome}
     * for a successful run, or an {@link Exception} includes the type of the exception thrown and
     * the corresponding error message.
     *
     * <p>The input {@code adSelectionFromOutcomesConfig} contains:
     * <li>{@code Seller} is required to be a registered {@link
     *     android.adservices.common.AdTechIdentifier}. Otherwise, {@link IllegalStateException}
     *     will be thrown.
     * <li>{@code List of ad selection ids} should exist and come from {@link
     *     AdSelectionManager#selectAds} calls originated from the same application. Otherwise,
     *     {@link IllegalArgumentException} for input validation will raise listing violating ad
     *     selection ids.
     * <li>{@code Selection logic URI} should match the {@code seller} host. Otherwise, {@link
     *     IllegalArgumentException} will be thrown.
     *
     *     <p>If the {@link IllegalArgumentException} is thrown, it is caused by invalid input
     *     argument the API received to run the ad selection.
     *
     *     <p>If the {@link IllegalStateException} is thrown with error message "Failure of
     *     AdSelection services.", it is caused by an internal failure of the ad selection service.
     *
     * @hide
     */
    public void selectAds(
            @NonNull AdSelectionFromOutcomesConfig adSelectionFromOutcomesConfig,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<AdSelectionOutcome, Exception> receiver) {
        Objects.requireNonNull(adSelectionFromOutcomesConfig);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);

        try {
            final AdSelectionService service = getService();
            service.selectAdsFromOutcomes(
                    new AdSelectionFromOutcomesInput.Builder()
                            .setAdSelectionFromOutcomesConfig(adSelectionFromOutcomesConfig)
                            .setCallerPackageName(getCallerPackageName())
                            .build(),
                    new CallerMetadata.Builder()
                            .setBinderElapsedTimestamp(SystemClock.elapsedRealtime())
                            .build(),
                    new AdSelectionCallback.Stub() {
                        @Override
                        public void onSuccess(AdSelectionResponse resultParcel) {
                            executor.execute(
                                    () -> {
                                        if (resultParcel == null) {
                                            receiver.onResult(AdSelectionOutcome.NO_OUTCOME);
                                        } else {
                                            receiver.onResult(
                                                    new AdSelectionOutcome.Builder()
                                                            .setAdSelectionId(
                                                                    resultParcel.getAdSelectionId())
                                                            .setRenderUri(
                                                                    resultParcel.getRenderUri())
                                                            .build());
                                        }
                                    });
                        }

                        @Override
                        public void onFailure(FledgeErrorResponse failureParcel) {
                            executor.execute(
                                    () -> {
                                        receiver.onError(
                                                AdServicesStatusUtils.asException(failureParcel));
                                    });
                        }
                    });
        } catch (NullPointerException e) {
            LogUtil.e(e, "Unable to find the AdSelection service.");
            receiver.onError(
                    new IllegalStateException("Unable to find the AdSelection service.", e));
        } catch (RemoteException e) {
            LogUtil.e(e, "Failure of AdSelection service.");
            receiver.onError(new IllegalStateException("Failure of AdSelection service.", e));
        }
    }

    /**
     * Report the given impression. The {@link ReportImpressionRequest} is provided by the Ads SDK.
     * The receiver either returns a {@code void} for a successful run, or an {@link Exception}
     * indicates the error.
     */
    @RequiresPermission(ACCESS_ADSERVICES_CUSTOM_AUDIENCE)
    public void reportImpression(
            @NonNull ReportImpressionRequest request,
            @NonNull Executor executor,
            @NonNull OutcomeReceiver<Object, Exception> receiver) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);

        try {
            final AdSelectionService service = getService();
            service.reportImpression(
                    new ReportImpressionInput.Builder()
                            .setAdSelectionId(request.getAdSelectionId())
                            .setAdSelectionConfig(request.getAdSelectionConfig())
                            .setCallerPackageName(getCallerPackageName())
                            .build(),
                    new ReportImpressionCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            executor.execute(() -> receiver.onResult(new Object()));
                        }

                        @Override
                        public void onFailure(FledgeErrorResponse failureParcel) {
                            executor.execute(
                                    () -> {
                                        receiver.onError(
                                                AdServicesStatusUtils.asException(failureParcel));
                                    });
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

    private String getCallerPackageName() {
        if (mContext instanceof SandboxedSdkContext) {
            return ((SandboxedSdkContext) mContext).getClientPackageName();
        } else {
            return mContext.getPackageName();
        }
    }
}
