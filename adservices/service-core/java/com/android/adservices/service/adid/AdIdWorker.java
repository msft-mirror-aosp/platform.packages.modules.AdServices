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

package com.android.adservices.service.adid;

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;

import static com.android.adservices.AdServicesCommon.ACTION_ADID_PROVIDER_SERVICE;

import android.adservices.adid.GetAdIdResult;
import android.adservices.adid.IAdIdProviderService;
import android.adservices.adid.IGetAdIdCallback;
import android.adservices.adid.IGetAdIdProviderCallback;
import android.annotation.NonNull;
import android.annotation.WorkerThread;
import android.content.Context;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.ServiceBinder;

import java.util.Objects;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Worker class to handle AdId API Implementation.
 *
 * <p>This class is thread safe.
 *
 * @hide
 */
@ThreadSafe
@WorkerThread
public class AdIdWorker {
    // Singleton instance of the AdIdWorker.
    private static volatile AdIdWorker sAdIdWorker;

    private final Context mContext;
    private final ServiceBinder<IAdIdProviderService> mServiceBinder;

    // @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
    public AdIdWorker(Context context) {
        mContext = context;
        mServiceBinder =
                ServiceBinder.getServiceBinder(
                        context,
                        ACTION_ADID_PROVIDER_SERVICE,
                        IAdIdProviderService.Stub::asInterface);
    }

    /**
     * Gets an instance of AdIdWorker to be used.
     *
     * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the
     * existing instance will be returned.
     */
    @NonNull
    public static AdIdWorker getInstance(Context context) {
        if (sAdIdWorker == null) {
            synchronized (AdIdWorker.class) {
                if (sAdIdWorker == null) {
                    sAdIdWorker = new AdIdWorker(context);
                }
            }
        }
        return sAdIdWorker;
    }

    @NonNull
    private IAdIdProviderService getService() {
        IAdIdProviderService service = mServiceBinder.getService();
        if (service == null) {
            throw new IllegalStateException("Unable to find the service");
        }
        return service;
    }

    @NonNull
    private void unbindFromService() {
        mServiceBinder.unbindFromService();
    }

    /**
     * Get adId for the specified app and sdk.
     *
     * @param packageName is the app package name
     * @param appUid is the current UID of the calling app;
     * @param callback is used to return the result.
     */
    @NonNull
    public void getAdId(
            @NonNull String packageName, int appUid, @NonNull IGetAdIdCallback callback) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(callback);
        LogUtil.v("AdIdWorker.getAdId for %s, %d", packageName, appUid);
        final IAdIdProviderService service = getService();

        try {
            // Call adId provider service method to retrieve the adid and lat.
            service.getAdIdProvider(
                    appUid,
                    packageName,
                    new IGetAdIdProviderCallback.Stub() {
                        @Override
                        public void onResult(GetAdIdResult resultParcel) {
                            GetAdIdResult result =
                                    new GetAdIdResult.Builder()
                                            .setStatusCode(resultParcel.getStatusCode())
                                            .setErrorMessage(resultParcel.getErrorMessage())
                                            .setAdId(resultParcel.getAdId())
                                            .setLatEnabled(resultParcel.isLatEnabled())
                                            .build();
                            try {
                                callback.onResult(result);
                            } catch (RemoteException e) {
                                LogUtil.e("RemoteException");
                            } finally {
                                // Since we are sure, the provider service api has returned,
                                // we can safely unbind the adId provider service.
                                unbindFromService();
                            }
                        }

                        @Override
                        public void onError(String errorMessage) {
                            try {
                                callback.onError(STATUS_INTERNAL_ERROR);
                            } catch (RemoteException e) {
                                LogUtil.e("RemoteException");
                            } finally {
                                // Since we are sure, the provider service api has returned,
                                // we can safely unbind the adId provider service.
                                unbindFromService();
                            }
                        }
                    });

        } catch (RemoteException e) {
            LogUtil.e(e, "RemoteException");
            try {
                callback.onError(STATUS_INTERNAL_ERROR);
            } catch (RemoteException err) {
                LogUtil.e("RemoteException");
            } finally {
                unbindFromService();
            }
        }
    }
}
