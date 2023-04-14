/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.appsearch;

import android.annotation.NonNull;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.service.common.feature.PrivacySandboxFeatureType;
import com.android.adservices.service.consent.App;
import com.android.adservices.service.consent.ConsentManager;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class manages the interface to AppSearch for reading/writing all AdServices consent data on
 * S- devices. This is needed because AdServices does not run any code in the system server on S-
 * devices, so consent data is rollback safe by storing it in AppSearch.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class AppSearchConsentManager {
    private Context mContext;
    private AppSearchConsentWorker mAppSearchConsentWorker;

    private AppSearchConsentManager(
            @NonNull Context context, @NonNull AppSearchConsentWorker appSearchConsentWorker) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(appSearchConsentWorker);
        mContext = context;
        mAppSearchConsentWorker = appSearchConsentWorker;
    }

    /** Returns an instance of AppSearchConsentManager. */
    public static AppSearchConsentManager getInstance(@NonNull Context context) {
        Objects.requireNonNull(context);
        return new AppSearchConsentManager(context, AppSearchConsentWorker.getInstance(context));
    }

    /**
     * Get the consent for this user ID for this API type, as stored in AppSearch. Returns false if
     * the database doesn't exist in AppSearch.
     */
    public boolean getConsent(@NonNull String apiType) {
        Objects.requireNonNull(apiType);
        return mAppSearchConsentWorker.getConsent(apiType);
    }

    /**
     * Sets the consent for this user ID for this API type in AppSearch. If we do not get
     * confirmation that the write was successful, then we throw an exception so that user does not
     * incorrectly think that the consent is updated.
     */
    public void setConsent(@NonNull String apiType, @NonNull Boolean consented) {
        Objects.requireNonNull(apiType);
        Objects.requireNonNull(consented);
        mAppSearchConsentWorker.setConsent(apiType, consented);
    }

    /**
     * Get known apps with consent as stored in AppSearch.
     *
     * @return an {@link ImmutableList} of all known apps in the database that have not had user
     *     consent revoked.
     */
    public ImmutableList<App> getKnownAppsWithConsent() {
        List<String> apps =
                mAppSearchConsentWorker.getAppsWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_CONSENT);
        Set<String> installedPackages = getInstalledPackages();
        List<App> result = new ArrayList<>();
        for (String app : apps) {
            if (installedPackages.contains(app)) {
                result.add(App.create(app));
            }
        }
        return ImmutableList.copyOf(result);
    }

    /**
     * Get apps with consent revoked, as stored in AppSearch.
     *
     * @return an {@link ImmutableList} of all known apps in the database that have had user consent
     *     revoked.
     */
    public ImmutableList<App> getAppsWithRevokedConsent() {
        List<String> apps =
                mAppSearchConsentWorker.getAppsWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT);
        Set<String> installedPackages = getInstalledPackages();
        List<App> result = new ArrayList<>();
        for (String app : apps) {
            if (installedPackages.contains(app)) {
                result.add(App.create(app));
            }
        }
        return ImmutableList.copyOf(result);
    }

    /**
     * Clears all app data related to the provided {@link App}.
     *
     * @param app {@link App} to block.
     */
    public void revokeConsentForApp(@NonNull App app) {
        Objects.requireNonNull(app);
        mAppSearchConsentWorker.addAppWithConsent(
                AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT, app.getPackageName());
        mAppSearchConsentWorker.removeAppWithConsent(
                AppSearchAppConsentDao.APPS_WITH_CONSENT, app.getPackageName());
    }

    /**
     * Restore consent for provided {@link App}.
     *
     * @param app {@link App} to restore consent for.
     */
    public void restoreConsentForApp(@NonNull App app) {
        Objects.requireNonNull(app);
        mAppSearchConsentWorker.removeAppWithConsent(
                AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT, app.getPackageName());
        mAppSearchConsentWorker.addAppWithConsent(
                AppSearchAppConsentDao.APPS_WITH_CONSENT, app.getPackageName());
    }

    /**
     * Deletes all app consent data and all app data gathered or generated by the Privacy Sandbox.
     *
     * <p>This should be called when the Privacy Sandbox has been disabled.
     */
    public void clearAllAppConsentData() {
        mAppSearchConsentWorker.clearAppsWithConsent(AppSearchAppConsentDao.APPS_WITH_CONSENT);
        mAppSearchConsentWorker.clearAppsWithConsent(
                AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT);
    }

    /**
     * Deletes the list of known allowed apps as well as all app data from the Privacy Sandbox.
     *
     * <p>The list of blocked apps is not reset.
     */
    public void clearKnownAppsWithConsent() throws IOException {
        mAppSearchConsentWorker.clearAppsWithConsent(AppSearchAppConsentDao.APPS_WITH_CONSENT);
    }

    /**
     * Checks whether a single given installed application (identified by its package name) has had
     * user consent to use the FLEDGE APIs revoked.
     *
     * <p>This method also checks whether a user has opted out of the FLEDGE Privacy Sandbox
     * initiative.
     *
     * @param packageName String package name that uniquely identifies an installed application to
     *     check
     * @return {@code true} if either the FLEDGE Privacy Sandbox initiative has been opted out or if
     *     the user has revoked consent for the given application to use the FLEDGE APIs
     * @throws IllegalArgumentException if the package name is invalid or not found as an installed
     *     application
     */
    public boolean isFledgeConsentRevokedForApp(@NonNull String packageName) {
        Objects.requireNonNull(packageName);

        boolean isConsented =
                mAppSearchConsentWorker
                        .getAppsWithConsent(AppSearchAppConsentDao.APPS_WITH_CONSENT)
                        .contains(packageName);
        boolean isRevoked =
                mAppSearchConsentWorker
                        .getAppsWithConsent(AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT)
                        .contains(packageName);
        return isRevoked || !isConsented;
    }

    /**
     * Persists the use of a FLEDGE API by a single given installed application (identified by its
     * package name) if the app has not already had its consent revoked.
     *
     * <p>This method also checks whether a user has opted out of the FLEDGE Privacy Sandbox
     * initiative.
     *
     * <p>This is only meant to be called by the FLEDGE APIs.
     *
     * @param packageName String package name that uniquely identifies an installed application that
     *     has used a FLEDGE API
     * @return {@code true} if user consent has been revoked for the application or API, {@code
     *     false} otherwise
     * @throws IllegalArgumentException if the package name is invalid or not found as an installed
     *     application
     */
    public boolean isFledgeConsentRevokedForAppAfterSettingFledgeUse(@NonNull String packageName) {
        Objects.requireNonNull(packageName);

        return !mAppSearchConsentWorker.addAppWithConsent(
                AppSearchAppConsentDao.APPS_WITH_CONSENT, packageName);
    }

    /**
     * Clear consent data after an app was uninstalled, but the package Uid is unavailable. This
     * happens because the INTERACT_ACROSS_USERS_FULL permission is not available on Android
     * versions prior to T.
     *
     * <p><strong>This method should only be used for R/S back-compat scenarios.</strong>
     *
     * @param packageName the package name that had been uninstalled.
     */
    public void clearConsentForUninstalledApp(@NonNull String packageName) {
        Objects.requireNonNull(packageName);

        mAppSearchConsentWorker.removeAppWithConsent(
                AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT, packageName);
        mAppSearchConsentWorker.removeAppWithConsent(
                AppSearchAppConsentDao.APPS_WITH_CONSENT, packageName);
    }

    /**
     * Saves information to the storage that notification was displayed for the first time to the
     * user.
     */
    public void recordNotificationDisplayed() {
        mAppSearchConsentWorker.recordNotificationDisplayed();
    }

    /**
     * Retrieves if notification has been displayed.
     *
     * @return true if Consent Notification was displayed, otherwise false.
     */
    public Boolean wasNotificationDisplayed() {
        return mAppSearchConsentWorker.wasNotificationDisplayed();
    }

    /**
     * Saves information to the storage that GA UX notification was displayed for the first time to
     * the user.
     */
    public void recordGaUxNotificationDisplayed() {
        mAppSearchConsentWorker.recordGaUxNotificationDisplayed();
    }

    /**
     * Retrieves if GA UX notification has been displayed.
     *
     * @return true if GA UX Consent Notification was displayed, otherwise false.
     */
    public Boolean wasGaUxNotificationDisplayed() {
        return mAppSearchConsentWorker.wasGaUxNotificationDisplayed();
    }

    /** Get the current privacy sandbox feature. */
    public PrivacySandboxFeatureType getCurrentPrivacySandboxFeature() {
        return mAppSearchConsentWorker.getPrivacySandboxFeature();
    }

    /** Set the current privacy sandbox feature. */
    public void setCurrentPrivacySandboxFeature(
            @NonNull PrivacySandboxFeatureType currentFeatureType) {
        Objects.requireNonNull(currentFeatureType);
        mAppSearchConsentWorker.setCurrentPrivacySandboxFeature(currentFeatureType);
    }

    /**
     * Returns information whether user interacted with consent manually.
     *
     * @return true if the user interacted with the consent manually, otherwise false.
     */
    public @ConsentManager.UserManualInteraction int getUserManualInteractionWithConsent() {
        return mAppSearchConsentWorker.getUserManualInteractionWithConsent();
    }

    /** Saves information to the storage that user interacted with consent manually. */
    public void recordUserManualInteractionWithConsent(
            @ConsentManager.UserManualInteraction int interaction) {
        mAppSearchConsentWorker.recordUserManualInteractionWithConsent(interaction);
    }

    /** Returns the list of packages installed on the device of the user. */
    @NonNull
    private Set<String> getInstalledPackages() {
        return PackageManagerCompatUtils.getInstalledApplications(mContext.getPackageManager(), 0)
                .stream()
                .map(applicationInfo -> applicationInfo.packageName)
                .collect(Collectors.toSet());
    }
}
