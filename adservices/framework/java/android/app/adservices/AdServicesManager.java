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

import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.app.adservices.consent.ConsentParcel;
import android.app.sdksandbox.SdkSandboxManager;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * AdServices Manager to handle the internal communication between PPAPI process and AdServices
 * System Service.
 *
 * @hide
 */
public final class AdServicesManager {
    private static AdServicesManager sSingleton;
    private final IAdServicesManager mService;

    @VisibleForTesting
    public AdServicesManager(@NonNull IAdServicesManager iAdServicesManager) {
        Objects.requireNonNull(iAdServicesManager, "AdServicesManager is NULL!");
        mService = iAdServicesManager;
    }

    /** Get the singleton of AdServicesManager */
    public static AdServicesManager getInstance(@NonNull Context context) {
        synchronized (AdServicesManager.class) {
            if (sSingleton == null) {
                // TODO(b/262282035): Fix this work around in U+.
                // Get the AdServicesManagerService's Binder from the SdkSandboxManager.
                // This is a workaround for b/262282035.
                IBinder iBinder =
                        context.getSystemService(SdkSandboxManager.class).getAdServicesManager();
                sSingleton = new AdServicesManager(IAdServicesManager.Stub.asInterface(iBinder));
            }
        }
        return sSingleton;
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

    /**
     * Saves information to the storage that notification was displayed for the first time to the
     * user.
     */
    @RequiresPermission(ACCESS_ADSERVICES_MANAGER)
    public void recordGaUxNotificationDisplayed() {
        try {
            mService.recordGaUxNotificationDisplayed();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns information whether Consent GA UX Notification was displayed or not.
     *
     * @return true if Consent GA UX Notification was displayed, otherwise false.
     */
    @RequiresPermission(ACCESS_ADSERVICES_MANAGER)
    public boolean wasGaUxNotificationDisplayed() {
        try {
            return mService.wasGaUxNotificationDisplayed();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Saves information to the storage that topics consent page was displayed for the first time to
     * the user.
     */
    @RequiresPermission(ACCESS_ADSERVICES_MANAGER)
    public void recordTopicsConsentPageDisplayed() {
        try {
            mService.recordTopicsConsentPageDisplayed();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns information whether topics Consent page was displayed or not.
     *
     * @return true if topics consent page was displayed, otherwise false.
     */
    @RequiresPermission(ACCESS_ADSERVICES_MANAGER)
    public boolean wasTopicsConsentPageDisplayed() {
        try {
            return mService.wasTopicsConsentPageDisplayed();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Saves information to the storage that fledge and msmt consent page was displayed for the
     * first time to the user.
     */
    @RequiresPermission(ACCESS_ADSERVICES_MANAGER)
    public void recordFledgeAndMsmtConsentPageDisplayed() {
        try {
            mService.recordFledgeAndMsmtConsentPageDisplayed();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns information whether fledge and msmt Consent page was displayed or not.
     *
     * @return true if fledge and msmt consent page was displayed, otherwise false.
     */
    @RequiresPermission(ACCESS_ADSERVICES_MANAGER)
    public boolean wasFledgeAndMsmtConsentPageDisplayed() {
        try {
            return mService.wasFledgeAndMsmtConsentPageDisplayed();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
