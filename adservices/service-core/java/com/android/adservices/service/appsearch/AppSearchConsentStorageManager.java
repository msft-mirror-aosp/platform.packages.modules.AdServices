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

import static com.android.adservices.AdServicesCommon.ADEXTSERVICES_PACKAGE_NAME_SUFFIX;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.adservices.topics.TopicParcel;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.data.common.BooleanFileDatastore;
import com.android.adservices.data.consent.AppConsentDao;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.service.common.feature.PrivacySandboxFeatureType;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.AdServicesStorageManager;
import com.android.adservices.service.consent.ConsentConstants;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.consent.IConsentStorage;
import com.android.adservices.service.topics.BlockedTopicsManager;
import com.android.adservices.service.ui.enrollment.collection.PrivacySandboxEnrollmentChannelCollection;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;

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
 *
 * <p>IMPORTANT: Until ConsentManagerV2 is launched, keep in sync with AppSearchConsentManager.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class AppSearchConsentStorageManager implements IConsentStorage {

    private static final Object SINGLETON_LOCK = new Object();

    @GuardedBy("SINGLETON_LOCK")
    private static AppSearchConsentStorageManager sSingleton;

    private final AppSearchConsentWorker mAppSearchConsentWorker;

    @VisibleForTesting
    public AppSearchConsentStorageManager(@NonNull AppSearchConsentWorker appSearchConsentWorker) {
        Objects.requireNonNull(appSearchConsentWorker);
        mAppSearchConsentWorker = appSearchConsentWorker;
    }

    /** Returns an instance of AppSearchConsentStorageManager. */
    public static AppSearchConsentStorageManager getInstance() {
        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                sSingleton =
                        new AppSearchConsentStorageManager(AppSearchConsentWorker.getInstance());
            }
            return sSingleton;
        }
    }

    /** Record a blocked topic in AppSearch. */
    public void blockTopic(Topic topic) {
        mAppSearchConsentWorker.recordBlockedTopic(topic);
    }

    /**
     * Deletes all app consent data and all app data gathered or generated by the Privacy Sandbox.
     *
     * <p>This should be called when the Privacy Sandbox has been disabled.
     */
    @Override
    public void clearAllAppConsentData() {
        mAppSearchConsentWorker.clearAppsWithConsent(AppSearchAppConsentDao.APPS_WITH_CONSENT);
        mAppSearchConsentWorker.clearAppsWithConsent(
                AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT);
    }

    /** Clear all blocked topics in AppSearch. */
    public void clearAllBlockedTopics() {
        mAppSearchConsentWorker.clearBlockedTopics();
    }

    @Override
    public void clearConsentForUninstalledApp(@NonNull String packageName) {
        clearConsentForUninstalledApp(packageName, 0);
    }

    /**
     * Clear consent data after an app was uninstalled, but the package Uid is unavailable. This
     * happens because the INTERACT_ACROSS_USERS_FULL permission is not available on Android
     * versions prior to T.
     *
     * <p><strong>This method should only be used for R/S back-compat scenarios.</strong>
     *
     * @param packageName the package name that had been uninstalled.
     * @param packageUid packageUid, unused in AppsearchConsentManager.
     */
    @Override
    public void clearConsentForUninstalledApp(@NonNull String packageName, int packageUid) {
        // For appsearch storage, we don't need the packageUid, unused in AppsearchConsentManager
        Objects.requireNonNull(packageName);

        mAppSearchConsentWorker.removeAppWithConsent(
                AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT, packageName);
        mAppSearchConsentWorker.removeAppWithConsent(
                AppSearchAppConsentDao.APPS_WITH_CONSENT, packageName);
    }

    /**
     * Deletes the list of known allowed apps as well as all app data from the Privacy Sandbox.
     * Migrated from clearKnownAppsWithConsent()
     *
     * <p>The list of blocked apps is not reset.
     */
    @Override
    public void clearKnownAppsWithConsent() throws IOException {
        mAppSearchConsentWorker.clearAppsWithConsent(AppSearchAppConsentDao.APPS_WITH_CONSENT);
    }

    /**
     * Get apps with consent revoked, as stored in AppSearch.
     *
     * @return an {@link ImmutableList} of all known apps in the database that have had user consent
     *     revoked.
     */
    @Override
    public ImmutableList<String> getAppsWithRevokedConsent() {
        List<String> apps =
                mAppSearchConsentWorker.getAppsWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT);
        Set<String> installedPackages = getInstalledPackages();
        List<String> result = new ArrayList<>();
        for (String app : apps) {
            if (installedPackages.contains(app)) {
                result.add(app);
            }
        }
        return ImmutableList.copyOf(result);
    }

    /**
     * Gets the consent for this user ID for this API type, as stored in AppSearch. Returns false if
     * the database doesn't exist in AppSearch.
     */
    @Override
    public AdServicesApiConsent getConsent(AdServicesApiType apiType) {
        Objects.requireNonNull(apiType);
        return AdServicesApiConsent.getConsent(
                mAppSearchConsentWorker.getConsent(apiType.toAppSearchKey()));
    }

    /** Gets the current privacy sandbox feature. */
    @Override
    public PrivacySandboxFeatureType getCurrentPrivacySandboxFeature() {
        return mAppSearchConsentWorker.getPrivacySandboxFeature();
    }

    /** Returns the current privacy sandbox enrollment channel. */
    @Override
    public PrivacySandboxEnrollmentChannelCollection getEnrollmentChannel(
            PrivacySandboxUxCollection ux) {
        return mAppSearchConsentWorker.getEnrollmentChannel(ux);
    }

    /**
     * Get known apps with consent as stored in AppSearch.
     *
     * @return an {@link ImmutableList} of all known apps in the database that have not had user
     *     consent revoked.
     */
    @Override
    public ImmutableList<String> getKnownAppsWithConsent() {
        List<String> apps =
                mAppSearchConsentWorker.getAppsWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_CONSENT);
        Set<String> installedPackages = getInstalledPackages();
        List<String> result = new ArrayList<>();
        for (String app : apps) {
            if (installedPackages.contains(app)) {
                result.add(app);
            }
        }
        return ImmutableList.copyOf(result);
    }

    /**
     * Returns information whether user interacted with consent manually.
     *
     * @return true if the user interacted with the consent manually, otherwise false.
     */
    @ConsentManager.UserManualInteraction
    public int getUserManualInteractionWithConsent() {
        return mAppSearchConsentWorker.getUserManualInteractionWithConsent();
    }

    /** Returns the current privacy sandbox UX. */
    @Override
    public PrivacySandboxUxCollection getUx() {
        return mAppSearchConsentWorker.getUx();
    }

    /** Returns whether the isAdIdEnabled bit is true. */
    @Override
    public boolean isAdIdEnabled() {
        return mAppSearchConsentWorker.isAdIdEnabled();
    }

    /** Returns whether the isAdultAccount bit is true. */
    @Override
    public boolean isAdultAccount() {
        return mAppSearchConsentWorker.isAdultAccount();
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
    @Override
    public boolean isConsentRevokedForApp(@NonNull String packageName) {
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

    /** Returns whether the isEntryPointEnabled bit is true. */
    @Override
    public boolean isEntryPointEnabled() {
        return mAppSearchConsentWorker.isEntryPointEnabled();
    }

    /** Returns whether the isU18Account bit is true. */
    @Override
    public boolean isU18Account() {
        return mAppSearchConsentWorker.isU18Account();
    }

    /**
     * Migrate consent data to PPAPI and system server. This includes the following:
     *
     * <p>a) notification data. Set notification displayed only when value is TRUE. FALSE and null
     * are regarded as not displayed,
     *
     * <p>b) app consent data. All apps recorded as consented or revoked are migrated,
     *
     * <p>c) current Privacy Sandbox feature type and
     *
     * <p>d) blocked topics.
     *
     * @return whether or not we performed a migration
     */
    public boolean migrateConsentDataIfNeeded(
            @NonNull SharedPreferences sharedPreferences,
            @NonNull BooleanFileDatastore datastore,
            @Nullable AdServicesStorageManager adServicesManager,
            @NonNull AppConsentDao appConsentDao)
            throws IOException {
        Objects.requireNonNull(sharedPreferences);
        Objects.requireNonNull(datastore);
        Objects.requireNonNull(appConsentDao);
        // On R/S, this function should never be executed because AppSearch to PPAPI and
        // System Server migration is a T+ feature. On T+, this function should only execute
        // if it's within the AdServices APK and not ExtServices. So check if it's within
        // ExtServices, and bail out if that's the case on any platform.
        String packageName = ApplicationContextSingleton.get().getPackageName();
        LogUtil.d(packageName);
        if (packageName != null && packageName.endsWith(ADEXTSERVICES_PACKAGE_NAME_SUFFIX)) {
            LogUtil.d(
                    "Aborting attempt to migrate Consent data to PPAPI and System Service in"
                            + " ExtServices");
            return false;
        }
        // Only perform migration if all the pre-conditions are met.
        // <p>a) The device is T+
        // <p>b) Data is not already migrated
        // <p>c) We showed the notification on S- (as recorded in AppSearch).
        boolean isU18AppSearchMigrationEnabled =
                FlagsFactory.getFlags().getEnableU18AppsearchMigration();
        if (!shouldInitConsentDataFromAppSearch(
                sharedPreferences, datastore, adServicesManager, isU18AppSearchMigrationEnabled)) {
            return false;
        }

        boolean wasNotificationDisplayed = wasNotificationDisplayed();
        boolean wasGaUxNotificationDisplayed = wasGaUxNotificationDisplayed();
        boolean wasU18NotificationDisplayed =
                isU18AppSearchMigrationEnabled && wasU18NotificationDisplayed();

        if (wasNotificationDisplayed) {
            datastore.put(ConsentConstants.NOTIFICATION_DISPLAYED_ONCE, true);
            adServicesManager.recordNotificationDisplayed(true);
        }
        if (wasGaUxNotificationDisplayed) {
            datastore.put(ConsentConstants.GA_UX_NOTIFICATION_DISPLAYED_ONCE, true);
            adServicesManager.recordGaUxNotificationDisplayed(true);
        }
        if (wasU18NotificationDisplayed) {
            datastore.put(ConsentConstants.WAS_U18_NOTIFICATION_DISPLAYED, true);
            adServicesManager.setU18NotificationDisplayed(true);
        }
        if (!wasGaUxNotificationDisplayed
                && !wasNotificationDisplayed
                && !wasU18NotificationDisplayed) {
            // This shouldn't happen since we checked that one of these notifications is
            // displayed per AppSearch before entering.
            LogUtil.e("AppSearch has not recorded notification displayed. Aborting migration");
            return false;
        }

        // Migrate app consent data to PP API and system server. All apps recorded as consented or
        // revoked are migrated.
        List<String> consentedApps = getKnownAppsWithConsent();
        if (consentedApps != null && !consentedApps.isEmpty()) {
            for (String app : consentedApps) {
                appConsentDao.setConsentForApp(app, /* isConsentRevoked= */ false);
                adServicesManager.setConsentForApp(app, /* isConsentRevoked= */ false);
            }
        }
        List<String> revokedApps = getAppsWithRevokedConsent();
        if (revokedApps != null && !revokedApps.isEmpty()) {
            for (String app : revokedApps) {
                appConsentDao.setConsentForApp(app, /* isConsentRevoked= */ true);
                adServicesManager.setConsentForApp(app, /* isConsentRevoked= */ true);
            }
        }

        // Migrate the current Privacy Sandbox feature type to PP API and system server.
        PrivacySandboxFeatureType currentFeatureType = getCurrentPrivacySandboxFeature();
        for (PrivacySandboxFeatureType featureType : PrivacySandboxFeatureType.values()) {
            if (featureType.name().equals(currentFeatureType.name())) {
                datastore.put(featureType.name(), true);
            } else {
                datastore.put(featureType.name(), false);
            }
        }

        adServicesManager.setCurrentPrivacySandboxFeature(currentFeatureType);

        // Migrate the blocked topics data.
        List<TopicParcel> topics = new ArrayList<>();
        for (Topic topic : mAppSearchConsentWorker.getBlockedTopics()) {
            topics.add(topic.convertTopicToTopicParcel());
        }
        if (!topics.isEmpty()) {
            adServicesManager.recordBlockedTopic(topics);
        }
        // Save migration has happened into shared preferences.
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(BlockedTopicsManager.SHARED_PREFS_KEY_HAS_MIGRATED, true);
        if (editor.commit()) {
            LogUtil.d("Finished migrating blocked topics from AppSearch to System Service");
        } else {
            LogUtil.e(
                    "Finished migrating blocked topics from AppSearch to System Service but shared"
                            + " preference is not updated.");
        }
        return true;
    }

    /**
     * Saves information to the storage that GA UX notification was displayed for the first time to
     * the user.
     */
    @Override
    public void recordGaUxNotificationDisplayed(boolean wasNotificationDisplayed) {
        mAppSearchConsentWorker.recordGaUxNotificationDisplayed(wasNotificationDisplayed);
    }

    /**
     * Saves information to the storage that notification was displayed for the first time to the
     * user.
     */
    @Override
    public void recordNotificationDisplayed(boolean wasNotificationDisplayed) {
        mAppSearchConsentWorker.recordNotificationDisplayed(wasNotificationDisplayed);
    }

    /** Saves information to the storage that user interacted with consent manually. */
    public void recordUserManualInteractionWithConsent(int interaction) {
        mAppSearchConsentWorker.recordUserManualInteractionWithConsent(interaction);
    }

    /** Retrieve all blocked topics in AppSearch. */
    public List<Topic> retrieveAllBlockedTopics() {
        return mAppSearchConsentWorker.getBlockedTopics();
    }

    /** Saves the isAdIdEnabled bit. */
    @Override
    public void setAdIdEnabled(boolean isAdIdEnabled) {
        mAppSearchConsentWorker.setAdIdEnabled(isAdIdEnabled);
    }

    /** Save the isAdultAccount bit. */
    @Override
    public void setAdultAccount(boolean isAdultAccount) {
        mAppSearchConsentWorker.setAdultAccount(isAdultAccount);
    }

    /**
     * Sets the consent for this user ID for this API type in AppSearch. If we do not get
     * confirmation that the write was successful, then we throw an exception so that user does not
     * incorrectly think that the consent is updated.
     */
    @Override
    public void setConsent(@NonNull AdServicesApiType apiType, boolean consented) {
        Objects.requireNonNull(apiType);
        mAppSearchConsentWorker.setConsent(apiType.toAppSearchKey(), consented);
    }

    @Override
    public void setConsentForApp(@NonNull String packageName, boolean isConsentRevoked) {
        Objects.requireNonNull(packageName);
        if (isConsentRevoked) {
            mAppSearchConsentWorker.addAppWithConsent(
                    AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT, packageName);
            mAppSearchConsentWorker.removeAppWithConsent(
                    AppSearchAppConsentDao.APPS_WITH_CONSENT, packageName);
        } else {
            mAppSearchConsentWorker.removeAppWithConsent(
                    AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT, packageName);
            mAppSearchConsentWorker.addAppWithConsent(
                    AppSearchAppConsentDao.APPS_WITH_CONSENT, packageName);
        }
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
     * @param isConsentRevoked Boolean is Consent Revoked
     * @return {@code true} if user consent has been revoked for the application or API, {@code
     *     false} otherwise
     * @throws IllegalArgumentException if the package name is invalid or not found as an installed
     *     application
     */
    @Override
    public boolean setConsentForAppIfNew(@NonNull String packageName, boolean isConsentRevoked)
            throws IllegalArgumentException {
        if (FlagsFactory.getFlags().getEnableAppsearchConsentData()) {
            boolean isRevoked =
                    mAppSearchConsentWorker
                            .getAppsWithConsent(AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT)
                            .contains(packageName);

            if (isRevoked) {
                return true;
            }
            return !mAppSearchConsentWorker.addAppWithConsent(
                    AppSearchAppConsentDao.APPS_WITH_CONSENT, packageName);
        }
        return true;
    }

    /** Sets the current privacy sandbox feature. */
    @Override
    public void setCurrentPrivacySandboxFeature(
            @NonNull PrivacySandboxFeatureType currentFeatureType) {
        Objects.requireNonNull(currentFeatureType);
        mAppSearchConsentWorker.setCurrentPrivacySandboxFeature(currentFeatureType);
    }

    /** Set the current privacy sandbox enrollment channel. */
    @Override
    public void setEnrollmentChannel(
            PrivacySandboxUxCollection ux,
            PrivacySandboxEnrollmentChannelCollection enrollmentChannel) {
        mAppSearchConsentWorker.setEnrollmentChannel(ux, enrollmentChannel);
    }

    /** Save the isEntryPointEnabled bit. */
    @Override
    public void setEntryPointEnabled(boolean isEntryPointEnabled) {
        mAppSearchConsentWorker.setEntryPointEnabled(isEntryPointEnabled);
    }

    /** Save the isU18Account bit. */
    @Override
    public void setU18Account(boolean isU18Account) {
        mAppSearchConsentWorker.setU18Account(isU18Account);
    }

    /** Save the wasU18NotificationDisplayed bit. */
    @Override
    public void setU18NotificationDisplayed(boolean wasU18NotificationDisplayed) {
        mAppSearchConsentWorker.setU18NotificationDisplayed(wasU18NotificationDisplayed);
    }

    /** Set the current privacy sandbox UX. */
    @Override
    public void setUx(PrivacySandboxUxCollection ux) {
        mAppSearchConsentWorker.setUx(ux);
    }

    /** Remove a previously record of a blocked topic in AppSearch. */
    public void unblockTopic(Topic topic) {
        mAppSearchConsentWorker.recordUnblockedTopic(topic);
    }

    /**
     * Retrieves if GA UX notification has been displayed.
     *
     * @return true if GA UX Consent Notification was displayed, otherwise false.
     */
    @Override
    public boolean wasGaUxNotificationDisplayed() {
        return mAppSearchConsentWorker.wasGaUxNotificationDisplayed();
    }

    /**
     * Retrieves if notification has been displayed.
     *
     * @return true if Consent Notification was displayed, otherwise false.
     */
    @Override
    public boolean wasNotificationDisplayed() {
        return mAppSearchConsentWorker.wasNotificationDisplayed();
    }

    /** Returns whether the wasU18NotificationDisplayed bit is true. */
    @Override
    public boolean wasU18NotificationDisplayed() {
        return mAppSearchConsentWorker.wasU18NotificationDisplayed();
    }

    /**
     * Checks whether migration of consent data from AppSearch to PPAPI/System server should occur.
     * The migration should only happen once after OTA from S to T.
     *
     * @return whether migration should occur.
     */
    @VisibleForTesting
    // Suppress lint warning for context.getUser in R since this code is unused in R
    @SuppressWarnings("NewApi")
    boolean shouldInitConsentDataFromAppSearch(
            SharedPreferences sharedPreferences,
            BooleanFileDatastore datastore,
            AdServicesStorageManager adServicesManager,
            boolean isU18AppSearchMigrationEnabled) {
        if (!SdkLevel.isAtLeastT() || !FlagsFactory.getFlags().getEnableAppsearchConsentData()) {
            return false;
        }
        Objects.requireNonNull(adServicesManager);

        // Exit if migration has happened. If system server has received consent data via a
        // migration, do not attempt another migration.
        boolean shouldSkipMigration =
                sharedPreferences.getBoolean(
                                ConsentConstants.SHARED_PREFS_KEY_APPSEARCH_HAS_MIGRATED,
                                /* defValue= */ false)
                        || sharedPreferences.getBoolean(
                                ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED,
                                /* defValue= */ false)
                        || sharedPreferences.getBoolean(
                                ConsentConstants
                                        .SHARED_PREFS_KEY_MIGRATED_FROM_ADEXTDATA_TO_SYSTEM_SERVER,
                                /* defValue= */ false);

        if (shouldSkipMigration) {
            LogUtil.d(
                    "Consent migration from AppSearch is already done for user %d.",
                    ApplicationContextSingleton.get().getUser().getIdentifier());
            return false;
        }

        // If this is a T+ device, check if we have shown notification at all. If we have never
        // recorded showing notification in PP API or system service and we have consent data stored
        // in AppSearch, we should migrate that data to AdServices and record notification as
        // displayed. This avoids showing the notification to the user again after OTA to T.
        boolean wasU18NotificationRecordedInPpapiOrSystemServer =
                isU18AppSearchMigrationEnabled
                        && (datastore.get(ConsentConstants.WAS_U18_NOTIFICATION_DISPLAYED)
                                || adServicesManager.wasU18NotificationDisplayed());
        boolean wasNotificationDisplayedInAdServices =
                datastore.get(ConsentConstants.NOTIFICATION_DISPLAYED_ONCE)
                        || datastore.get(ConsentConstants.GA_UX_NOTIFICATION_DISPLAYED_ONCE)
                        || adServicesManager.wasNotificationDisplayed()
                        || adServicesManager.wasGaUxNotificationDisplayed()
                        || wasU18NotificationRecordedInPpapiOrSystemServer;
        if (!wasNotificationDisplayedInAdServices) {
            // Check notification status in AppSearch
            boolean wasU18NotificationDisplayed =
                    isU18AppSearchMigrationEnabled && wasU18NotificationDisplayed();
            boolean result =
                    wasU18NotificationDisplayed
                            || wasNotificationDisplayed()
                            || wasGaUxNotificationDisplayed();
            LogUtil.d("For consent migration AppSearch notification status: " + result);
            return result;
        }
        return false;
    }

    /** Returns the list of packages installed on the device of the user. */
    @NonNull
    private Set<String> getInstalledPackages() {
        return PackageManagerCompatUtils.getInstalledApplications(
                        ApplicationContextSingleton.get().getPackageManager(), 0)
                .stream()
                .map(applicationInfo -> applicationInfo.packageName)
                .collect(Collectors.toSet());
    }
}
