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

import static android.adservices.common.AdServicesPermissions.ACCESS_ADSERVICES_MANAGER;
import static android.app.adservices.AdServicesManager.AD_SERVICES_SYSTEM_SERVICE;

import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.SystemService;
import android.app.adservices.consent.ConsentParcel;
import android.content.Context;
import android.os.RemoteException;


import java.util.Objects;

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

    @SuppressWarnings("unused")
    public AdServicesManager(@NonNull Context unusedContext, @NonNull IAdServicesManager binder) {
        mService = binder;
    }

    /** Return the User Consent */
    @RequiresPermission(ACCESS_ADSERVICES_MANAGER)
    public ConsentParcel getConsent(@ConsentParcel.ConsentApiType int consentApiType) {
        try {
            return mService.getConsent(consentApiType);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /** Set the User Consent */
    @RequiresPermission(ACCESS_ADSERVICES_MANAGER)
    public void setConsent(@NonNull ConsentParcel consentParcel) {
        Objects.requireNonNull(consentParcel);
        try {
            mService.setConsent(consentParcel);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Saves information to the storage that notification was displayed for the first time to the
     * user.
     */
    @RequiresPermission(ACCESS_ADSERVICES_MANAGER)
    public void recordNotificationDisplayed() {
        try {
            mService.recordNotificationDisplayed();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns information whether Consent Notification was displayed or not.
     *
     * @return true if Consent Notification was displayed, otherwise false.
     */
    @RequiresPermission(ACCESS_ADSERVICES_MANAGER)
    public boolean wasNotificationDisplayed() {
        try {
            return mService.wasNotificationDisplayed();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
