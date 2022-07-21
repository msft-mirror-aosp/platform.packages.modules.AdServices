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

package android.adservices.common;

import android.adservices.exceptions.AdServicesException;
import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.content.Context;
import android.os.OutcomeReceiver;
import android.os.RemoteException;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.LogUtil;
import com.android.adservices.ServiceBinder;

import java.util.concurrent.Executor;

/**
 * AdServicesCommonManager contains APIs common across the various Adservices. Current we set It to
 * SystemApi as all two methods, isAdServicesEnabled and setAdServicesEntryPointEnabled, Are all
 * systemApi.
 *
 * @hide
 */
@SystemApi
public class AdServicesCommonManager {
    /** @hide */
    public static final String AD_SERVICES_COMMON_SERVICE = "ad_services_common_service";

    private final Context mContext;
    private final ServiceBinder<IAdServicesCommonService>
            mAdServicesCommonServiceBinder;

    /**
     * Create AdServicesCommonManager.
     * @hide
     */
    public AdServicesCommonManager(@NonNull Context context) {
        mContext = context;
        mAdServicesCommonServiceBinder = ServiceBinder.getServiceBinder(
                context,
                AdServicesCommon.ACTION_AD_SERVICES_COMMON_SERVICE,
                IAdServicesCommonService.Stub::asInterface);
    }

    @NonNull
    private IAdServicesCommonService getService() {
        IAdServicesCommonService service =
                mAdServicesCommonServiceBinder.getService();
        if (service == null) {
            throw new IllegalStateException("Unable to find the service");
        }
        return service;
    }

    /**
     * Get the adservices status.
     *
     * @hide
     */
    @SystemApi
    public void isAdServicesEnabled(
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Boolean, AdServicesException> callback) {
        final IAdServicesCommonService service = getService();
        try {
            service.isAdServicesEnabled(
                    new IAdServicesCommonCallback.Stub() {
                        @Override
                        public void onResult(IsAdServicesEnabledResult result) {
                            executor.execute(
                                    () -> {
                                        callback.onResult(result.getAdServicesEnabled());
                                    });
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            executor.execute(
                                    () -> {
                                        callback.onError(new AdServicesException(errorMessage));
                                    });
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e("RemoteException", e);
            executor.execute(() -> callback.onError(new AdServicesException("Internal Error!")));
        }
    }

    /**
     * Send the privacy sandbox UI entry point enable status to AdServices, indicating whether
     * Privacy sandbox is enabled or not. If this is enabled, and AdServices is enabled, AdServices
     * will send out notification to user.
     *
     * @hide
     */
    @SystemApi
    public void setAdServicesEntryPointEnabled(@NonNull boolean adservicesEntryPointEnabled) {
        final IAdServicesCommonService service = getService();
        try {
            service.setAdServicesEntryPointEnabled(adservicesEntryPointEnabled);
        } catch (RemoteException e) {
            LogUtil.e("RemoteException", e);
        }
    }
}
