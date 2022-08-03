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

package android.adservices.adid;

import static android.adservices.adid.AdIdManager.RESULT_OK;

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
 * Base class for service that provides abstract class for getting adId from the provider service.
 *
 * @hide
 */
@SystemApi
public abstract class AdIdProviderService extends Service {

    /** The intent that the service must respond to. Add it to the intent filter of the service. */
    @SdkConstant(SdkConstant.SdkConstantType.SERVICE_ACTION)
    public static final String SERVICE_INTERFACE = "android.adservices.adid.AdIdProviderService";

    /**
     * Abstract method which will be overridden by provider to provide the adId. For multi-user,
     * multi-profiles on-devicei scenarios, separate instance of service per user is expected to
     * implement this method.
     */
    @NonNull
    public abstract AdId onGetAdId(int clientUid, @NonNull String clientPackageName)
            throws IOException;

    private final android.adservices.adid.IAdIdProviderService mInterface =
            new android.adservices.adid.IAdIdProviderService.Stub() {
                @Override
                public void getAdIdProvider(
                        int appUID,
                        @NonNull String packageName,
                        @NonNull IGetAdIdProviderCallback resultCallback)
                        throws RemoteException {
                    try {
                        AdId adId = onGetAdId(appUID, packageName);
                        GetAdIdResult adIdInternal =
                                new GetAdIdResult.Builder()
                                        .setResultCode(RESULT_OK)
                                        .setErrorMessage("")
                                        .setAdId(adId.getAdId())
                                        .setLatEnabled(adId.isLimitAdTrackingEnabled())
                                        .build();

                        resultCallback.onResult(adIdInternal);
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
