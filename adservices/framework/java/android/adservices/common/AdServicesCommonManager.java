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

import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.LogUtil;
import com.android.adservices.ServiceBinder;

/**
 * AdServicesCommonManager class.
 * Ths contains APIs common across the various PPAPIs.
 * @hide
 */
public class AdServicesCommonManager {
    public static final String AD_SERVICES_COMMON_SERVICE =
            "ad_services_common_service";

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
     * Delete a package's records.
     * @hide
     */
    public void onPackageFullyRemoved(@NonNull Uri packageUri) {
        final IAdServicesCommonService service = getService();
        try {
            service.onPackageFullyRemoved(packageUri);
        } catch (RemoteException e) {
            LogUtil.e("RemoteException", e);
        } finally {
            mAdServicesCommonServiceBinder.unbindFromService();
        }
    }

    /**
     * Notify adservices that this app was installed for attribution purposes.
     * @param packageUri installed package URI.
     */
    public void onPackageAdded(@NonNull Uri packageUri) {
        final IAdServicesCommonService service = getService();
        try {
            service.onPackageAdded(packageUri);
        } catch (RemoteException e) {
            LogUtil.e("RemoteException", e);
        } finally {
            mAdServicesCommonServiceBinder.unbindFromService();
        }
    }
}
