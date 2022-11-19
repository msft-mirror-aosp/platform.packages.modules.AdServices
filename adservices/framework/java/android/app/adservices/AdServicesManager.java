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

package android.app.adservices;

import static android.app.adservices.AdServicesManager.AD_SERVICES_SYSTEM_SERVICE;

import android.annotation.NonNull;
import android.annotation.SystemService;
import android.content.Context;
import android.os.RemoteException;

import com.android.adservices.LogUtil;

/**
 * AdServices Manager to handle the internal communication between PPAPI process and AdServices
 * System Service.
 *
 * @hide
 */
@SystemService(AD_SERVICES_SYSTEM_SERVICE)
public class AdServicesManager {
    public static final String AD_SERVICES_SYSTEM_SERVICE = "adservices_manager";

    private final IAdServicesManager mService;
    private final Context mContext;

    /** @hide */
    public AdServicesManager(@NonNull Context context, @NonNull IAdServicesManager binder) {
        mContext = context;
        mService = binder;
    }

    /** Return the User Consent */
    public void getConsent() {
        try {
            mService.getConsent();
        } catch (RemoteException e) {
            LogUtil.e("Failed to get User Consent.", e);
            throw e.rethrowFromSystemServer();
        }
    }
}
