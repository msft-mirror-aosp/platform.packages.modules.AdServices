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

package com.android.adservices.service.appsetid;

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;

import static com.android.adservices.AdServicesCommon.ACTION_APPSETID_PROVIDER_SERVICE;

import android.adservices.appsetid.GetAppSetIdResult;
import android.adservices.appsetid.IAppSetIdProviderService;
import android.adservices.appsetid.IGetAppSetIdCallback;
import android.adservices.appsetid.IGetAppSetIdProviderCallback;
import android.annotation.NonNull;
import android.annotation.WorkerThread;
import android.content.Context;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.ServiceBinder;

import java.util.Objects;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Worker class to handle AppSetId API Implementation.
 *
 * <p>This class is thread safe.
 *
 * @hide
 */
@ThreadSafe
@WorkerThread
public class AppSetIdWorker {
    // Singleton instance of the AppSetIdWorker.
    private static volatile AppSetIdWorker sAppSetIdWorker;

    private final Context mContext;
    private final ServiceBinder<IAppSetIdProviderService> mServiceBinder;

    // @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
    public AppSetIdWorker(Context context) {
        mContext = context;
        mServiceBinder =
                ServiceBinder.getServiceBinder(
                        context,
                        ACTION_APPSETID_PROVIDER_SERVICE,
                        IAppSetIdProviderService.Stub::asInterface);
    }

    /**
     * Gets an instance of AppSetIdWorker to be used.
     *
     * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the
     * existing instance will be returned.
     */
    @NonNull
    public static AppSetIdWorker getInstance(Context context) {
        if (sAppSetIdWorker == null) {
            synchronized (AppSetIdWorker.class) {
                if (sAppSetIdWorker == null) {
                    sAppSetIdWorker = new AppSetIdWorker(context);
                }
            }
        }
        return sAppSetIdWorker;
    }

    @NonNull
    private IAppSetIdProviderService getService() {
        IAppSetIdProviderService service = mServiceBinder.getService();
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
     * Get appSetId for the specified app and sdk.
     *
     * @param packageName is the app package name
     * @param appUid is the current UID of the calling app;
     * @param callback is used to return the result.
     */
    @NonNull
    public void getAppSetId(
            @NonNull String packageName, int appUid, @NonNull IGetAppSetIdCallback callback) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(callback);
        LogUtil.v("AppSetIdWorker.getAppSetId for %s, %d", packageName, appUid);
        final IAppSetIdProviderService service = getService();

        try {
            // Call appSetId provider service method to retrieve the appsetid and lat.
            service.getAppSetId(
                    appUid,
                    packageName,
                    new IGetAppSetIdProviderCallback.Stub() {
                        @Override
                        public void onResult(GetAppSetIdResult resultParcel) {
                            GetAppSetIdResult result =
                                    new GetAppSetIdResult.Builder()
                                            .setStatusCode(resultParcel.getStatusCode())
                                            .setErrorMessage(resultParcel.getErrorMessage())
                                            .setAppSetId(resultParcel.getAppSetId())
                                            .setAppSetIdScope(resultParcel.getAppSetIdScope())
                                            .build();
                            try {
                                callback.onResult(result);
                            } catch (RemoteException e) {
                                LogUtil.e("RemoteException");
                            } finally {
                                // Since we are sure, the provider service api has returned,
                                // we can safely unbind the appSetId provider service.
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
                                // we can safely unbind the appSetId provider service.
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
