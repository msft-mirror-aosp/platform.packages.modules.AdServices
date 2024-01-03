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

package com.android.adservices.service.consent;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATASTORE_EXCEPTION_WHILE_RECORDING_DEFAULT_CONSENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATASTORE_EXCEPTION_WHILE_RECORDING_MANUAL_CONSENT_INTERACTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATASTORE_EXCEPTION_WHILE_RECORDING_NOTIFICATION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_WHILE_GET_CONSENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PRIVACY_SANDBOX_SAVE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.U18_UX;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.common.feature.PrivacySandboxFeatureType;
import com.android.adservices.service.exception.ConsentStorageDeferException;
import com.android.adservices.service.ui.enrollment.collection.PrivacySandboxEnrollmentChannelCollection;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.io.IOException;

/**
 * CompositeStorage to handle read/write user's to multiple source of truth
 *
 * <p>Every source of truth should have its own dedicated storage class that implements the
 * IConsentStorage interface, and pass in the instances to ConsentCompositeStorage.
 *
 * <p>By default, when caller set value to the storage, CompositeStorage will iterate through every
 * instance in mConsentStorageList and call the corresponding method. For getter, CompositeStorage
 * will only return the first one.
 *
 * <p>If the method not available in some implementation, the implementation class should throw
 * {code ConsentStorageDeferException}, CompositeStorage will try to get the result for next
 * instance.
 */
@RequiresApi(Build.VERSION_CODES.S)
public class ConsentCompositeStorage implements IConsentStorage {
    private static final int UNKNOWN = 0;
    private final ImmutableList<IConsentStorage> mConsentStorageList;

    /**
     * Constructor of ConsentCompositeStorage.
     *
     * @param consentStorageList storage implementation instance list.
     */
    public ConsentCompositeStorage(ImmutableList<IConsentStorage> consentStorageList) {
        if (consentStorageList == null || consentStorageList.isEmpty()) {
            throw new IllegalArgumentException("consent storage list can not be empty!");
        }
        this.mConsentStorageList = consentStorageList;
    }

    /**
     * Deletes all app consent data and all app data gathered or generated by the Privacy Sandbox.
     *
     * <p>This should be called when the Privacy Sandbox has been disabled.
     */
    @Override
    public void clearAllAppConsentData() {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                storage.clearAllAppConsentData();
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_WHILE_GET_CONSENT,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Clear consent data after an app was uninstalled.
     *
     * @param packageName the package name that had been uninstalled.
     */
    @Override
    public void clearConsentForUninstalledApp(@NonNull String packageName) {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                storage.clearConsentForUninstalledApp(packageName);
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_WHILE_GET_CONSENT,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
            }
        }
    }

    /**
     * Clear consent data after an app was uninstalled.
     *
     * @param packageName the package name that had been uninstalled.
     * @param packageUid the package uid that had been uninstalled.
     */
    @Override
    public void clearConsentForUninstalledApp(String packageName, int packageUid) {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                storage.clearConsentForUninstalledApp(packageName, packageUid);
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_WHILE_GET_CONSENT,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
            }
        }
    }

    /**
     * Deletes the list of known allowed apps as well as all app data from the Privacy Sandbox.
     *
     * <p>The list of blocked apps is not reset.
     */
    @Override
    public void clearKnownAppsWithConsent() {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                storage.clearKnownAppsWithConsent();
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_WHILE_GET_CONSENT,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
            }
        }
    }

    /**
     * @return an {@link ImmutableList} of all known apps in the database that have had user consent
     *     revoked
     */
    @Override
    public ImmutableList<String> getAppsWithRevokedConsent() {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                return storage.getAppsWithRevokedConsent();
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_WHILE_GET_CONSENT,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
            }
        }
        return ImmutableList.of();
    }

    /**
     * Retrieves the consent for all PP API services.
     *
     * <p>To read from PPAPI consent if source of truth is PPAPI. To read from system server consent
     * if source of truth is system server or dual sources.
     *
     * @return AdServicesApiConsent the consent
     */
    @Override
    public AdServicesApiConsent getConsent(AdServicesApiType apiType) {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                return storage.getConsent(apiType);
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException | RuntimeException e) {
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_WHILE_GET_CONSENT,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
                return AdServicesApiConsent.REVOKED;
            }
        }
        return AdServicesApiConsent.REVOKED;
    }

    /**
     * Gets the list of ConsentStorage instance.
     *
     * @return list of {@link IConsentStorage}
     */
    @VisibleForTesting
    public ImmutableList<IConsentStorage> getConsentStorageList() {
        return mConsentStorageList;
    }

    /**
     * Get the current privacy sandbox feature.
     *
     * <p>To write to PPAPI if consent source of truth is PPAPI_ONLY or dual sources. To write to
     * system server if consent source of truth is SYSTEM_SERVER_ONLY or dual sources.
     *
     * @return PrivacySandboxFeatureType privacy sandbox feature
     */
    @Override
    public PrivacySandboxFeatureType getCurrentPrivacySandboxFeature() {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                return storage.getCurrentPrivacySandboxFeature();
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException | RuntimeException e) {
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PRIVACY_SANDBOX_SAVE_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
                return PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED;
            }
        }
        return PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED;
    }

    /** Sets the current privacy sandbox feature. */
    @Override
    public void setCurrentPrivacySandboxFeature(PrivacySandboxFeatureType featureType) {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                storage.setCurrentPrivacySandboxFeature(featureType);
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PRIVACY_SANDBOX_SAVE_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
                throw new RuntimeException(
                        getClass().getSimpleName() + " failed. " + e.getMessage());
            }
        }
    }

    /**
     * Retrieves the default AdId state.
     *
     * @return true if the AdId is enabled by default, false otherwise.
     */
    @Override
    public boolean getDefaultAdIdState() {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                return storage.getDefaultAdIdState();
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDatastoreWhileRecordingDefaultConsent(e);
                return false;
            }
        }
        return false;
    }

    /**
     * Retrieves the PP API default consent.
     *
     * @return AdServicesApiConsent.
     */
    @Override
    public AdServicesApiConsent getDefaultConsent(AdServicesApiType apiType) {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                return storage.getDefaultConsent(apiType);
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDatastoreWhileRecordingDefaultConsent(e);
                return AdServicesApiConsent.REVOKED;
            }
        }
        return AdServicesApiConsent.REVOKED;
    }

    /** Returns current enrollment channel. */
    @Override
    public PrivacySandboxEnrollmentChannelCollection getEnrollmentChannel(
            PrivacySandboxUxCollection ux) {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                return storage.getEnrollmentChannel(ux);
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDatastoreWhileRecordingDefaultConsent(e);
            }
        }
        return null;
    }

    /**
     * Gets KnownApps that has Consent
     *
     * @return an {@link ImmutableList} of all known apps in the database that have not had user
     *     consent revoked
     */
    @Override
    public ImmutableList<String> getKnownAppsWithConsent() {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                return storage.getKnownAppsWithConsent();
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDatastoreWhileRecordingDefaultConsent(e);
            } catch (IllegalStateException e) {
                LogUtil.i("IllegalStateException" + e);
            }
        }
        return ImmutableList.of();
    }

    /**
     * Returns information whether user interacted with consent manually.
     *
     * @return true if the user interacted with the consent manually, otherwise false.
     */
    @Override
    public int getUserManualInteractionWithConsent() {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                return storage.getUserManualInteractionWithConsent();
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDatastoreManualInteractionException(e);
                return UNKNOWN;
            }
        }
        return 0;
    }

    /** Returns current UX. */
    @Override
    public PrivacySandboxUxCollection getUx() {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                return storage.getUx();
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDatastoreManualInteractionException(e);
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    /** Sets the current UX to storage. */
    @Override
    public void setUx(PrivacySandboxUxCollection ux) {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                storage.setUx(ux);
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDataStoreWhileRecordingException(e);
            }
        }
    }

    /** Returns whether the isAdIdEnabled bit is true. */
    @Override
    public boolean isAdIdEnabled() {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                return storage.isAdIdEnabled();
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDatastoreManualInteractionException(e);
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    /** Set the AdIdEnabled bit to storage. */
    @Override
    public void setAdIdEnabled(boolean isAdIdEnabled) {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                storage.setAdIdEnabled(isAdIdEnabled);
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDataStoreWhileRecordingException(e);
            }
        }
    }

    /** Returns whether the isAdultAccount bit is true. */
    @Override
    public boolean isAdultAccount() {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                return storage.isAdultAccount();
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDatastoreManualInteractionException(e);
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    /** Set the AdultAccount bit to storage. */
    @Override
    public void setAdultAccount(boolean isAdultAccount) {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                storage.setAdultAccount(isAdultAccount);
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDataStoreWhileRecordingException(e);
            }
        }
    }

    /**
     * Returns whether a given application (identified by package name) has had user consent
     * revoked.
     *
     * <p>If the given application is installed but is not found in the datastore, the application
     * is treated as having user consent, and this method returns {@code false}.
     *
     * @throws IllegalArgumentException if the package name is invalid or not found as an installed
     *     application
     */
    @Override
    public boolean isConsentRevokedForApp(String packageName) throws IllegalArgumentException {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                return storage.isConsentRevokedForApp(packageName);
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDatastoreManualInteractionException(e);
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    /** Returns whether the isEntryPointEnabled bit is true. */
    @Override
    public boolean isEntryPointEnabled() {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                return storage.isEntryPointEnabled();
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDatastoreManualInteractionException(e);
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    /** Sets the EntryPointEnabled bit to storage . */
    @Override
    public void setEntryPointEnabled(boolean isEntryPointEnabled) {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                storage.setEntryPointEnabled(isEntryPointEnabled);
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDataStoreWhileRecordingException(e);
            }
        }
    }

    /** Returns whether the isU18Account bit is true. */
    @Override
    public boolean isU18Account() {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                return storage.isU18Account();
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDatastoreManualInteractionException(e);
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    /** Sets the U18Account bit to storage. */
    @Override
    public void setU18Account(boolean isU18Account) {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                storage.setU18Account(isU18Account);
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDataStoreWhileRecordingException(e);
            }
        }
    }

    /** Saves the default AdId state bit to data stores based on source of truth. */
    @Override
    public void recordDefaultAdIdState(boolean defaultAdIdState) {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                storage.recordDefaultAdIdState(defaultAdIdState);
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDatastoreManualInteractionException(e);
                throw new RuntimeException(e);
            }
        }
    }

    /** Saves the default consent of a user. */
    @Override
    public void recordDefaultConsent(AdServicesApiType apiType, boolean defaultConsent) {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                storage.recordDefaultConsent(apiType, defaultConsent);
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDatastoreManualInteractionException(e);
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Saves information to the storage that GA UX notification was displayed for the first time to
     * the user.
     */
    @Override
    public void recordGaUxNotificationDisplayed(boolean wasGaUxDisplayed) {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                storage.recordGaUxNotificationDisplayed(wasGaUxDisplayed);
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDatastoreManualInteractionException(e);
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Saves information to the storage that notification was displayed for the first time to the
     * user.
     */
    @Override
    public void recordNotificationDisplayed(boolean wasNotificationDisplayed) {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                storage.recordNotificationDisplayed(wasNotificationDisplayed);
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDatastoreManualInteractionException(e);
                throw new RuntimeException(e);
            }
        }
    }

    /** Saves information to the storage that user interacted with consent manually. */
    @Override
    public void recordUserManualInteractionWithConsent(int interaction) {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                storage.recordUserManualInteractionWithConsent(interaction);
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDatastoreManualInteractionException(e);
                throw new RuntimeException(
                        getClass().getSimpleName() + " failed. " + e.getMessage());
            }
        }
    }

    /**
     * Sets the consent for this user ID for this API type in AppSearch. If we do not get
     * confirmation that the write was successful, then we throw an exception so that user does not
     * incorrectly think that the consent is updated.
     */
    @Override
    public void setConsent(AdServicesApiType apiType, boolean isGiven) {
        setConsentToApiType(apiType, isGiven);
        if (apiType == AdServicesApiType.ALL_API) {
            return;
        }
        setAggregatedConsent();
    }

    private void setConsentToApiType(AdServicesApiType apiType, boolean isGiven) {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                storage.setConsent(apiType, isGiven);
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDataStoreWhileRecordingException(e);
            }
        }
    }

    // Set the aggregated consent so that after the rollback of the module
    // and the flag which controls the consent flow everything works as expected.
    // The problematic edge case which is covered:
    // T1: AdServices is installed in pre-GA UX version and the consent is given
    // T2: AdServices got upgraded to GA UX binary and GA UX feature flag is enabled
    // T3: Consent for the Topics API got revoked
    // T4: AdServices got rolledback and the feature flags which controls consent flow
    // (SYSTEM_SERVER_ONLY and DUAL_WRITE) also got rolledback
    // T5: Restored consent should be revoked
    @VisibleForTesting
    void setAggregatedConsent() {
        if (getUx() == U18_UX) {
            // The edge case does not apply to U18 UX.
            return;
        }
        setConsentToApiType(
                AdServicesApiType.ALL_API,
                getConsent(AdServicesApiType.TOPICS).isGiven()
                        && getConsent(AdServicesApiType.MEASUREMENTS).isGiven()
                        && getConsent(AdServicesApiType.FLEDGE).isGiven());
    }

    /**
     * Sets consent for a given installed application, identified by package name.
     *
     * @throws IllegalArgumentException if the package name is invalid or not found as an installed
     *     application
     */
    @Override
    public void setConsentForApp(String packageName, boolean isConsentRevoked) {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                storage.setConsentForApp(packageName, isConsentRevoked);
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDataStoreWhileRecordingException(e);
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Tries to set consent for a given installed application, identified by package name, if it
     * does not already exist in the datastore, and returns the current consent setting after
     * checking.
     *
     * @return the current consent for the given {@code packageName} after trying to set the {@code
     *     value}
     * @throws IllegalArgumentException if the package name is invalid or not found as an installed
     *     application
     */
    @Override
    public boolean setConsentForAppIfNew(String packageName, boolean isConsentRevoked) {
        for (IConsentStorage storage : getConsentStorageList().reverse()) {
            try {
                // Same as original logic, call the PPAPI first
                // TODO(b/317595641): clean up the logic
                boolean ret = storage.setConsentForAppIfNew(packageName, isConsentRevoked);
                if (ret) {
                    return true;
                }
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                logDataStoreWhileRecordingException(e);
                return false;
            }
        }
        return false;
    }

    /** Sets the current enrollment channel to storage. */
    @Override
    public void setEnrollmentChannel(
            PrivacySandboxUxCollection ux, PrivacySandboxEnrollmentChannelCollection channel) {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                storage.setEnrollmentChannel(ux, channel);
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDataStoreWhileRecordingException(e);
            }
        }
    }

    /** Sets the U18NotificationDisplayed bit to storage. */
    @Override
    public void setU18NotificationDisplayed(boolean wasU18NotificationDisplayed) {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                storage.setU18NotificationDisplayed(wasU18NotificationDisplayed);
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDataStoreWhileRecordingException(e);
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Retrieves if GA UX notification has been displayed.
     *
     * @return true if GA UX Consent Notification was displayed, otherwise false.
     */
    @Override
    public boolean wasGaUxNotificationDisplayed() {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                return storage.wasGaUxNotificationDisplayed();
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDataStoreWhileRecordingException(e);
                return false;
            }
        }
        return false;
    }

    /**
     * Retrieves if notification has been displayed.
     *
     * @return true if Consent Notification was displayed, otherwise false.
     */
    @SuppressLint("NameOfTheRuleToSuppress")
    @Override
    public boolean wasNotificationDisplayed() {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                return storage.wasNotificationDisplayed();
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDataStoreWhileRecordingException(e);
                return false;
            }
        }
        return false;
    }

    /** Returns whether the wasU18NotificationDisplayed bit is true. */
    @Override
    public boolean wasU18NotificationDisplayed() {
        for (IConsentStorage storage : getConsentStorageList()) {
            try {
                return storage.wasU18NotificationDisplayed();
            } catch (ConsentStorageDeferException e) {
                LogUtil.i(
                        "Skip current storage manager %s. Defer to next one",
                        storage.getClass().getSimpleName());
            } catch (IOException e) {
                logDataStoreWhileRecordingException(e);
                return false;
            }
        }
        return false;
    }

    private static void logDataStoreWhileRecordingException(IOException e) {
        ErrorLogUtil.e(
                e,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATASTORE_EXCEPTION_WHILE_RECORDING_NOTIFICATION,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
    }

    private static void logDatastoreManualInteractionException(IOException e) {
        ErrorLogUtil.e(
                e,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATASTORE_EXCEPTION_WHILE_RECORDING_MANUAL_CONSENT_INTERACTION,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
    }

    private static void logDatastoreWhileRecordingDefaultConsent(IOException e) {
        ErrorLogUtil.e(
                e,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATASTORE_EXCEPTION_WHILE_RECORDING_DEFAULT_CONSENT,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
    }
}
