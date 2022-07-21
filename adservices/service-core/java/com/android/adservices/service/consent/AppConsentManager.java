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

package com.android.adservices.service.consent;

import android.content.Context;

import androidx.annotation.NonNull;

import com.android.adservices.data.consent.AppConsentDao;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.internal.annotations.VisibleForTesting;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

/**
 * Service-layer intermediary between the AdServices Consent Manager and the App Consent datastore.
 */
public class AppConsentManager {
    private static volatile AppConsentManager sAppConsentManager;

    private final AppConsentDao mAppConsentDao;
    private final CustomAudienceDao mCustomAudienceDao;

    @VisibleForTesting
    AppConsentManager(
            @NonNull AppConsentDao appConsentDao, @NonNull CustomAudienceDao customAudienceDao) {
        Objects.requireNonNull(appConsentDao);
        Objects.requireNonNull(customAudienceDao);

        mAppConsentDao = appConsentDao;
        mCustomAudienceDao = customAudienceDao;
    }

    /** @return the singleton instance of the {@link AppConsentManager} */
    public static AppConsentManager getInstance(@NonNull Context context) {
        Objects.requireNonNull(context, "Context must be provided.");

        if (sAppConsentManager == null) {
            synchronized (AppConsentManager.class) {
                if (sAppConsentManager == null) {
                    sAppConsentManager =
                            new AppConsentManager(
                                    AppConsentDao.getInstance(context),
                                    CustomAudienceDatabase.getInstance(context)
                                            .customAudienceDao());
                }
            }
        }

        return sAppConsentManager;
    }

    /**
     * @return a list of package names corresponding to all installed applications that have used
     *     any FLEDGE APIs and which have not had user consent revoked
     * @throws IOException if the operation fails
     */
    @NonNull
    public Set<String> getKnownAppsWithConsent() throws IOException {
        return mAppConsentDao.getKnownAppsWithConsent();
    }

    /**
     * @return a list of package names corresponding to installed applications that had user consent
     *     to use FLEDGE APIs revoked
     * @throws IOException if the operation fails
     */
    @NonNull
    public Set<String> getAppsWithRevokedConsent() throws IOException {
        return mAppConsentDao.getAppsWithRevokedConsent();
    }

    /**
     * Revokes user consent to use the FLEDGE APIs for an installed application (identified by its
     * package name).
     *
     * <p>Revoking user consent for an application also immediately clears all FLEDGE data
     * associated with the application.
     *
     * <p>Note that this setting is cleared by {@link #clearConsentForUninstalledApp(String, int)}
     * when the application is uninstalled.
     *
     * @param packageName String package name that uniquely identifies an installed application to
     *     block
     * @throws IllegalArgumentException if the package name is invalid or not found as an installed
     *     application
     * @throws IOException if the operation fails
     */
    public void revokeConsentForApp(@NonNull String packageName)
            throws IllegalArgumentException, IOException {
        mAppConsentDao.setConsentForApp(packageName, true);
        // TODO(b/234642471): Clear data from FLEDGE CA datastore
    }

    /**
     * Restores user consent to use the FLEDGE APIs for an installed application (identified by its
     * package name).
     *
     * <p>Note that this setting is cleared by {@link #clearConsentForUninstalledApp(String, int)}
     * when the application is uninstalled.
     *
     * @param packageName String package name that uniquely identifies an installed application to
     *     allow
     * @throws IllegalArgumentException if the package name is invalid or not found as an installed
     *     application
     * @throws IOException if the operation fails
     */
    public void restoreConsentForApp(@NonNull String packageName)
            throws IllegalArgumentException, IOException {
        mAppConsentDao.setConsentForApp(packageName, false);
    }

    /**
     * Clears all data from the FLEDGE Consent datastore.
     *
     * <p>This method is meant to be called when a user opts out from the FLEDGE Privacy Sandbox
     * initiative or resets all Custom Audiences.
     *
     * <p>Clearing user consent for an application also immediately clears all FLEDGE data
     * associated with the application.
     *
     * @throws IOException if the operation fails
     */
    public void clearAllConsentData() throws IOException {
        mAppConsentDao.clearAllConsentData();
        // TODO(b/234642471): Clear data from FLEDGE CA datastore
    }

    /**
     * Clears the persisted allowed or blocked use of a FLEDGE API by a given application
     * (identified by its package name) which has been uninstalled.
     *
     * <p>Clearing user consent for an application also immediately clears all FLEDGE data
     * associated with the application.
     *
     * @param packageName String package name that uniquely identifies an application which has been
     *     uninstalled
     * @param packageUid integer UID that was associated with the uninstalled application
     * @throws IllegalArgumentException if the package name is invalid or not found as an installed
     *     application
     * @throws IOException if the operation fails
     */
    public void clearConsentForUninstalledApp(@NonNull String packageName, int packageUid)
            throws IllegalArgumentException, IOException {
        mAppConsentDao.clearConsentForUninstalledApp(packageName, packageUid);
        // TODO(b/234642471): Clear data from FLEDGE CA datastore
    }
}
