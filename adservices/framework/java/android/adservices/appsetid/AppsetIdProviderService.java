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

package android.adservices.appsetid;

import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SdkConstant;
import android.annotation.SystemApi;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import java.io.IOException;

/**
 * Base class for service that provides abstract class for getting appsetId from the provider
 * service.
 *
 * @hide
 */
@SystemApi
public abstract class AppsetIdProviderService extends Service {

    /** The intent that the service must respond to. Add it to the intent filter of the service. */
    @SdkConstant(SdkConstant.SdkConstantType.SERVICE_ACTION)
    public static final String SERVICE_INTERFACE =
            "android.adservices.appsetid.AppsetIdProviderService";

    /** Abstract method which will be overrider by provider to provide the appsetid. */
    @NonNull
    public abstract AppsetId onGetAppsetIdProvider(int clientUid, @NonNull String clientPackageName)
            throws IOException;

    private final android.adservices.appsetid.IAppsetIdProviderService mInterface =
            new android.adservices.appsetid.IAppsetIdProviderService.Stub() {
                @Override
                public void getAppsetIdProvider(
                        int appUID,
                        @NonNull String packageName,
                        @NonNull IGetAppsetIdProviderCallback resultCallback)
                        throws RemoteException {
                    try {
                        AppsetId appsetId = onGetAppsetIdProvider(appUID, packageName);
                        GetAppsetIdResult appsetIdInternal =
                                new GetAppsetIdResult.Builder()
                                        .setStatusCode(STATUS_SUCCESS)
                                        .setErrorMessage("")
                                        .setAppsetId(appsetId.getAppsetId())
                                        .setAppsetIdScope(appsetId.getAppsetIdScope())
                                        .build();

                        resultCallback.onResult(appsetIdInternal);
                    } catch (Throwable e) {
                        resultCallback.onError(e.getMessage());
                    }
                }
            };

    @Nullable
    @Override
    public final IBinder onBind(@Nullable Intent intent) {
        return mInterface.asBinder();
    }
}
