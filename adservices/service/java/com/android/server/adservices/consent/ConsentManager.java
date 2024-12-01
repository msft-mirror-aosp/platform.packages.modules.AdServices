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
package com.android.server.adservices.consent;

import android.annotation.UserIdInt;
import android.app.adservices.consent.ConsentParcel;

import com.android.adservices.shared.storage.AtomicFileDatastore;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.adservices.FlagsFactory;
import com.android.server.adservices.LogUtil;
import com.android.server.adservices.errorlogging.AdServicesErrorLoggerImpl;
import com.android.server.adservices.feature.PrivacySandboxEnrollmentChannelCollection;
import com.android.server.adservices.feature.PrivacySandboxFeatureType;
import com.android.server.adservices.feature.PrivacySandboxUxCollection;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

/**
 * Manager to handle user's consent. We will have one ConsentManager instance per user.
 *
 * @hide
 */
public final class ConsentManager {
    public static final String ERROR_MESSAGE_DATASTORE_EXCEPTION_WHILE_GET_CONTENT =
            "getConsent method failed. Revoked consent is returned as fallback.";

    public static final String VERSION_KEY = "android.app.adservices.consent.VERSION";

    @VisibleForTesting
    static final String NOTIFICATION_DISPLAYED_ONCE = "NOTIFICATION-DISPLAYED-ONCE";

    static final String GA_UX_NOTIFICATION_DISPLAYED_ONCE = "GA-UX-NOTIFICATION-DISPLAYED-ONCE";

    static final String PAS_NOTIFICATION_DISPLAYED_ONCE = "PAS_NOTIFICATION_DISPLAYED_ONCE";

    static final String PAS_NOTIFICATION_OPENED = "PAS_NOTIFICATION_OPENED";

    static final String TOPICS_CONSENT_PAGE_DISPLAYED = "TOPICS-CONSENT-PAGE-DISPLAYED";

    static final String FLEDGE_AND_MSMT_CONSENT_PAGE_DISPLAYED =
            "FLDEGE-AND-MSMT-CONDENT-PAGE-DISPLAYED";

    private static final String CONSENT_API_TYPE_PREFIX = "CONSENT_API_TYPE_";

    // Deprecate this since we store each version in its own folder.
    static final int STORAGE_VERSION = 1;
    static final String STORAGE_XML_IDENTIFIER = "ConsentManagerStorageIdentifier.xml";

    private final AtomicFileDatastore mDatastore;

    @VisibleForTesting static final String DEFAULT_CONSENT = "DEFAULT_CONSENT";

    @VisibleForTesting static final String TOPICS_DEFAULT_CONSENT = "TOPICS_DEFAULT_CONSENT";

    @VisibleForTesting static final String FLEDGE_DEFAULT_CONSENT = "FLEDGE_DEFAULT_CONSENT";

    @VisibleForTesting
    static final String MEASUREMENT_DEFAULT_CONSENT = "MEASUREMENT_DEFAULT_CONSENT";

    @VisibleForTesting static final String DEFAULT_AD_ID_STATE = "DEFAULT_AD_ID_STATE";

    @VisibleForTesting
    static final String MANUAL_INTERACTION_WITH_CONSENT_RECORDED =
            "MANUAL_INTERACTION_WITH_CONSENT_RECORDED";

    private final ReadWriteLock mReadWriteLock = new ReentrantReadWriteLock();

    private ConsentManager(AtomicFileDatastore datastore) {
        mDatastore = Objects.requireNonNull(datastore, "datastore cannot be null");
    }

    /** Creates a ConsentManager with base directory and for userId */
    public static ConsentManager createConsentManager(String baseDir, @UserIdInt int userId)
            throws IOException {
        Objects.requireNonNull(baseDir, "Base dir must be provided.");

        // The Data store is in folder with the following format.
        // /data/system/adservices/user_id/consent/data_schema_version/
        // Create the consent directory if needed.
        String consentDataStoreDir =
                ConsentDatastoreLocationHelper.getConsentDataStoreDirAndCreateDir(baseDir, userId);

        LogUtil.i("Creating datastore for user %d on dir %s", userId, consentDataStoreDir);
        AtomicFileDatastore datastore = createAndInitAtomicFileDatastore(consentDataStoreDir);

        return new ConsentManager(datastore);
    }

    @VisibleForTesting
    static AtomicFileDatastore createAndInitAtomicFileDatastore(String consentDataStoreDir)
            throws IOException {
        // Create the DataStore and initialize it.
        AtomicFileDatastore datastore =
                new AtomicFileDatastore(
                        new File(consentDataStoreDir, STORAGE_XML_IDENTIFIER),
                        STORAGE_VERSION,
                        VERSION_KEY,
                        AdServicesErrorLoggerImpl.getInstance());
        datastore.initialize();

        if (FlagsFactory.getFlags().getEnableAtomicFileDatastoreBatchUpdateApiInSystemServer()) {
            datastore.update(
                    updateOperation -> {
                        updateOperation.putBooleanIfNew(NOTIFICATION_DISPLAYED_ONCE, false);
                        updateOperation.putBooleanIfNew(GA_UX_NOTIFICATION_DISPLAYED_ONCE, false);
                        updateOperation.putBooleanIfNew(TOPICS_CONSENT_PAGE_DISPLAYED, false);
                        updateOperation.putBooleanIfNew(
                                FLEDGE_AND_MSMT_CONSENT_PAGE_DISPLAYED, false);
                    });
        } else {
            if (datastore.getBoolean(NOTIFICATION_DISPLAYED_ONCE) == null) {
                datastore.putBoolean(NOTIFICATION_DISPLAYED_ONCE, false);
            }
            if (datastore.getBoolean(GA_UX_NOTIFICATION_DISPLAYED_ONCE) == null) {
                datastore.putBoolean(GA_UX_NOTIFICATION_DISPLAYED_ONCE, false);
            }
            if (datastore.getBoolean(TOPICS_CONSENT_PAGE_DISPLAYED) == null) {
                datastore.putBoolean(TOPICS_CONSENT_PAGE_DISPLAYED, false);
            }
            if (datastore.getBoolean(FLEDGE_AND_MSMT_CONSENT_PAGE_DISPLAYED) == null) {
                datastore.putBoolean(FLEDGE_AND_MSMT_CONSENT_PAGE_DISPLAYED, false);
            }
        }

        return datastore;
    }

    /** Retrieves the consent for all PP API services. */
    public ConsentParcel getConsent(@ConsentParcel.ConsentApiType int consentApiType) {
        LogUtil.d("ConsentManager.getConsent() is invoked for consentApiType = " + consentApiType);

        mReadWriteLock.readLock().lock();
        try {
            return new ConsentParcel.Builder()
                    .setConsentApiType(consentApiType)
                    .setIsGiven(mDatastore.getBoolean(getConsentApiTypeKey(consentApiType)))
                    .build();
        } catch (NullPointerException | IllegalArgumentException e) {
            LogUtil.e(e, ERROR_MESSAGE_DATASTORE_EXCEPTION_WHILE_GET_CONTENT);
            return ConsentParcel.createRevokedConsent(consentApiType);
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }

    /** Set Consent */
    public void setConsent(ConsentParcel consentParcel) throws IOException {
        mReadWriteLock.writeLock().lock();
        try {
            if (FlagsFactory.getFlags()
                    .getEnableAtomicFileDatastoreBatchUpdateApiInSystemServer()) {
                mDatastore.update(
                        updateOperation -> {
                            updateOperation.putBoolean(
                                    getConsentApiTypeKey(consentParcel.getConsentApiType()),
                                    consentParcel.isIsGiven());
                            if (consentParcel.getConsentApiType() == ConsentParcel.ALL_API) {
                                // Convert from 1 to 3 consents.
                                updateOperation.putBoolean(
                                        getConsentApiTypeKey(ConsentParcel.TOPICS),
                                        consentParcel.isIsGiven());
                                updateOperation.putBoolean(
                                        getConsentApiTypeKey(ConsentParcel.FLEDGE),
                                        consentParcel.isIsGiven());
                                updateOperation.putBoolean(
                                        getConsentApiTypeKey(ConsentParcel.MEASUREMENT),
                                        consentParcel.isIsGiven());
                            } else {
                                // Convert from 3 consents to 1 consent.
                                if (mDatastore.getBoolean(
                                                getConsentApiTypeKey(
                                                        ConsentParcel.TOPICS), /* defaultValue */
                                                false)
                                        && mDatastore.getBoolean(
                                                getConsentApiTypeKey(
                                                        ConsentParcel.FLEDGE), /* defaultValue */
                                                false)
                                        && mDatastore.getBoolean(
                                                getConsentApiTypeKey(
                                                        ConsentParcel
                                                                .MEASUREMENT), /* defaultValue */
                                                false)) {
                                    updateOperation.putBoolean(
                                            getConsentApiTypeKey(ConsentParcel.ALL_API), true);
                                } else {
                                    updateOperation.putBoolean(
                                            getConsentApiTypeKey(ConsentParcel.ALL_API), false);
                                }
                            }
                        });
            } else {
                mDatastore.putBoolean(
                        getConsentApiTypeKey(consentParcel.getConsentApiType()),
                        consentParcel.isIsGiven());
                if (consentParcel.getConsentApiType() == ConsentParcel.ALL_API) {
                    // Convert from 1 to 3 consents.
                    mDatastore.putBoolean(
                            getConsentApiTypeKey(ConsentParcel.TOPICS), consentParcel.isIsGiven());
                    mDatastore.putBoolean(
                            getConsentApiTypeKey(ConsentParcel.FLEDGE), consentParcel.isIsGiven());
                    mDatastore.putBoolean(
                            getConsentApiTypeKey(ConsentParcel.MEASUREMENT),
                            consentParcel.isIsGiven());
                } else {
                    // Convert from 3 consents to 1 consent.
                    if (mDatastore.getBoolean(
                                    getConsentApiTypeKey(ConsentParcel.TOPICS), /* defaultValue */
                                    false)
                            && mDatastore.getBoolean(
                                    getConsentApiTypeKey(ConsentParcel.FLEDGE), /* defaultValue */
                                    false)
                            && mDatastore.getBoolean(
                                    getConsentApiTypeKey(
                                            ConsentParcel.MEASUREMENT), /* defaultValue */
                                    false)) {
                        mDatastore.putBoolean(getConsentApiTypeKey(ConsentParcel.ALL_API), true);
                    } else {
                        mDatastore.putBoolean(getConsentApiTypeKey(ConsentParcel.ALL_API), false);
                    }
                }
            }
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    /**
     * Saves information to the storage that notification was displayed for the first time to the
     * user.
     */
    public void recordNotificationDisplayed(boolean wasNotificationDisplayed) {
        setValueWithLock(
                NOTIFICATION_DISPLAYED_ONCE,
                wasNotificationDisplayed,
                "recordNotificationDisplayed");
    }

    /**
     * Returns information whether Consent Notification was displayed or not.
     *
     * @return true if Consent Notification was displayed, otherwise false.
     */
    public boolean wasNotificationDisplayed() {
        return getValueWithLock(NOTIFICATION_DISPLAYED_ONCE);
    }

    /**
     * Saves information to the storage that GA UX notification was displayed for the first time to
     * the user.
     */
    public void recordGaUxNotificationDisplayed(boolean wasNotificationDisplayed) {
        setValueWithLock(
                GA_UX_NOTIFICATION_DISPLAYED_ONCE,
                wasNotificationDisplayed,
                "recordGaUxNotificationDisplayed");
    }

    /**
     * Returns information whether GA Ux Consent Notification was displayed or not.
     *
     * @return true if GA UX Consent Notification was displayed, otherwise false.
     */
    public boolean wasGaUxNotificationDisplayed() {
        return getValueWithLock(GA_UX_NOTIFICATION_DISPLAYED_ONCE);
    }

    /**
     * Saves information to the storage that PAS notification was displayed for the first time to
     * the user.
     */
    public void recordPasNotificationDisplayed(boolean wasNotificationDisplayed) {
        setValueWithLock(
                PAS_NOTIFICATION_DISPLAYED_ONCE,
                wasNotificationDisplayed,
                "recordPasNotificationDisplayed");
    }

    /**
     * Returns information whether PAS Consent Notification was displayed or not.
     *
     * @return true if PAS Consent Notification was displayed, otherwise false.
     */
    public boolean wasPasNotificationDisplayed() {
        return getValueWithLock(PAS_NOTIFICATION_DISPLAYED_ONCE);
    }

    /**
     * Saves information to the storage that PAS notification was opened for the first time to the
     * user.
     */
    public void recordPasNotificationOpened(boolean wasNotificationOpened) {
        setValueWithLock(
                PAS_NOTIFICATION_OPENED, wasNotificationOpened, "recordPasNotificationOpened");
    }

    /**
     * Returns information whether PAS Consent Notification was opened or not.
     *
     * @return true if PAS Consent Notification was opened, otherwise false.
     */
    public boolean wasPasNotificationOpened() {
        return getValueWithLock(PAS_NOTIFICATION_OPENED);
    }

    /** Saves the default consent of a user. */
    public void recordDefaultConsent(boolean defaultConsent) {
        setValueWithLock(DEFAULT_CONSENT, defaultConsent, /* callerName */ "recordDefaultConsent");
    }

    /** Saves the default topics consent of a user. */
    public void recordTopicsDefaultConsent(boolean defaultConsent) {
        setValueWithLock(
                TOPICS_DEFAULT_CONSENT,
                defaultConsent, /* callerName */
                "recordTopicsDefaultConsent");
    }

    /** Saves the default FLEDGE consent of a user. */
    public void recordFledgeDefaultConsent(boolean defaultConsent) {
        setValueWithLock(
                FLEDGE_DEFAULT_CONSENT,
                defaultConsent, /* callerName */
                "recordFledgeDefaultConsent");
    }

    /** Saves the default measurement consent of a user. */
    public void recordMeasurementDefaultConsent(boolean defaultConsent) {
        setValueWithLock(
                MEASUREMENT_DEFAULT_CONSENT,
                defaultConsent, /* callerName */
                "recordMeasurementDefaultConsent");
    }

    /** Saves the default AdId state of a user. */
    public void recordDefaultAdIdState(boolean defaultAdIdState) {
        setValueWithLock(
                DEFAULT_AD_ID_STATE, defaultAdIdState, /* callerName */ "recordDefaultAdIdState");
    }

    /** Saves the information whether the user interated manually with the consent. */
    public void recordUserManualInteractionWithConsent(int interaction) {
        mReadWriteLock.writeLock().lock();
        try {
            switch (interaction) {
                case -1:
                    mDatastore.putBoolean(MANUAL_INTERACTION_WITH_CONSENT_RECORDED, false);
                    break;
                case 0:
                    mDatastore.remove(MANUAL_INTERACTION_WITH_CONSENT_RECORDED);
                    break;
                case 1:
                    mDatastore.putBoolean(MANUAL_INTERACTION_WITH_CONSENT_RECORDED, true);
                    break;
                default:
                    throw new IllegalArgumentException(
                            String.format("InteractionId < %d > can not be handled.", interaction));
            }
        } catch (IOException e) {
            LogUtil.e(
                    e,
                    "Record manual interaction with consent failed due to IOException"
                            + " thrown by Datastore: %s",
                    e.getMessage());
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    /** Returns information whether user interacted with consent manually. */
    public int getUserManualInteractionWithConsent() {
        mReadWriteLock.readLock().lock();
        try {
            Boolean userManualInteractionWithConsent =
                    mDatastore.getBoolean(MANUAL_INTERACTION_WITH_CONSENT_RECORDED);
            if (userManualInteractionWithConsent == null) {
                return 0;
            } else if (Boolean.TRUE.equals(userManualInteractionWithConsent)) {
                return 1;
            } else {
                return -1;
            }
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }

    /**
     * Returns the default consent state.
     *
     * @return true if default consent is given, otherwise false.
     */
    public boolean getDefaultConsent() {
        return getValueWithLock(DEFAULT_CONSENT);
    }

    /**
     * Returns the topics default consent state.
     *
     * @return true if topics default consent is given, otherwise false.
     */
    public boolean getTopicsDefaultConsent() {
        return getValueWithLock(TOPICS_DEFAULT_CONSENT);
    }


    /**
     * Returns the FLEDGE default consent state.
     *
     * @return true if default consent is given, otherwise false.
     */
    public boolean getFledgeDefaultConsent() {
        return getValueWithLock(FLEDGE_DEFAULT_CONSENT);
    }

    /**
     * Returns the measurement default consent state.
     *
     * @return true if default consent is given, otherwise false.
     */
    public boolean getMeasurementDefaultConsent() {
        return getValueWithLock(MEASUREMENT_DEFAULT_CONSENT);
    }

    /**
     * Returns the default AdId state when consent notification was sent.
     *
     * @return true if AdId is enabled by default, otherwise false.
     */
    public boolean getDefaultAdIdState() {
        return getValueWithLock(DEFAULT_AD_ID_STATE);
    }

    /** Set the current enabled privacy sandbox feature. */
    public void setCurrentPrivacySandboxFeature(String currentFeatureType) {
        mReadWriteLock.writeLock().lock();
        try {
            if (FlagsFactory.getFlags()
                    .getEnableAtomicFileDatastoreBatchUpdateApiInSystemServer()) {
                try {
                    mDatastore.update(
                            updateOperation -> {
                                for (PrivacySandboxFeatureType featureType :
                                        PrivacySandboxFeatureType.values()) {
                                    updateOperation.putBoolean(
                                            featureType.name(),
                                            currentFeatureType.equals(featureType.name()));
                                }
                            });
                } catch (IOException e) {
                    LogUtil.e(
                            "IOException caught while saving privacy sandbox feature. %s",
                            e.getMessage());
                }
            } else {
                for (PrivacySandboxFeatureType featureType : PrivacySandboxFeatureType.values()) {
                    try {
                        mDatastore.putBoolean(
                                featureType.name(), currentFeatureType.equals(featureType.name()));
                    } catch (IOException e) {
                        LogUtil.e(
                                "IOException caught while saving privacy sandbox feature. %s",
                                e.getMessage());
                    }
                }
            }
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    /** Returns whether a privacy sandbox feature is enabled. */
    public boolean isPrivacySandboxFeatureEnabled(PrivacySandboxFeatureType featureType) {
        return getValueWithLock(featureType.name());
    }

    /**
     * Deletes the user directory which contains consent information present at
     * /data/system/adservices/user_id
     */
    public boolean deleteUserDirectory(File dir) {
        mReadWriteLock.writeLock().lock();
        try {
            boolean success = true;
            File[] files = dir.listFiles();
            // files will be null if dir is not a directory
            if (files != null) {
                for (File file : files) {
                    if (!deleteUserDirectory(file)) {
                        LogUtil.d("Failed to delete " + file);
                        success = false;
                    }
                }
            }
            return success && dir.delete();
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    @VisibleForTesting
    String getConsentApiTypeKey(@ConsentParcel.ConsentApiType int consentApiType) {
        return CONSENT_API_TYPE_PREFIX + consentApiType;
    }

    @VisibleForTesting static final String IS_AD_ID_ENABLED = "IS_AD_ID_ENABLED";

    /** Returns whether the isAdIdEnabled bit is true. */
    public boolean isAdIdEnabled() {
        return getValueWithLock(IS_AD_ID_ENABLED);
    }

    /** Set the AdIdEnabled bit in system server. */
    public void setAdIdEnabled(boolean isAdIdEnabled) {
        setValueWithLock(IS_AD_ID_ENABLED, isAdIdEnabled, /* callerName */ "setAdIdEnabled");
    }

    @VisibleForTesting static final String IS_U18_ACCOUNT = "IS_U18_ACCOUNT";

    /** Returns whether the isU18Account bit is true. */
    public boolean isU18Account() {
        return getValueWithLock(IS_U18_ACCOUNT);
    }

    /** Set the U18Account bit in system server. */
    public void setU18Account(boolean isU18Account) {
        setValueWithLock(IS_U18_ACCOUNT, isU18Account, /* callerName */ "setU18Account");
    }

    @VisibleForTesting static final String IS_ENTRY_POINT_ENABLED = "IS_ENTRY_POINT_ENABLED";

    /** Returns whether the isEntryPointEnabled bit is true. */
    public boolean isEntryPointEnabled() {
        return getValueWithLock(IS_ENTRY_POINT_ENABLED);
    }

    /** Set the EntryPointEnabled bit in system server. */
    public void setEntryPointEnabled(boolean isEntryPointEnabled) {
        setValueWithLock(
                IS_ENTRY_POINT_ENABLED,
                isEntryPointEnabled, /* callerName */
                "setEntryPointEnabled");
    }

    @VisibleForTesting static final String IS_ADULT_ACCOUNT = "IS_ADULT_ACCOUNT";

    /** Returns whether the isAdultAccount bit is true. */
    public boolean isAdultAccount() {
        return getValueWithLock(IS_ADULT_ACCOUNT);
    }

    /** Set the AdultAccount bit in system server. */
    public void setAdultAccount(boolean isAdultAccount) {
        setValueWithLock(IS_ADULT_ACCOUNT, isAdultAccount, /* callerName */ "setAdultAccount");
    }

    @VisibleForTesting
    static final String WAS_U18_NOTIFICATION_DISPLAYED = "WAS_U18_NOTIFICATION_DISPLAYED";

    /** Returns whether the wasU18NotificationDisplayed bit is true. */
    public boolean wasU18NotificationDisplayed() {
        return getValueWithLock(WAS_U18_NOTIFICATION_DISPLAYED);
    }

    /** Set the U18NotificationDisplayed bit in system server. */
    public void setU18NotificationDisplayed(boolean wasU18NotificationDisplayed)
            throws IOException {
        setValueWithLock(
                WAS_U18_NOTIFICATION_DISPLAYED,
                wasU18NotificationDisplayed,
                /* callerName */ "setU18NotificationDisplayed");
    }

    /** Set the current enabled privacy sux. */
    public void setUx(String eligibleUx) {
        mReadWriteLock.writeLock().lock();
        try {
            if (FlagsFactory.getFlags()
                    .getEnableAtomicFileDatastoreBatchUpdateApiInSystemServer()) {
                try {
                    mDatastore.update(
                            updateOperation -> {
                                Stream.of(PrivacySandboxUxCollection.values())
                                        .forEach(
                                                ux -> {
                                                    updateOperation.putBoolean(
                                                            ux.toString(),
                                                            ux.toString().equals(eligibleUx));
                                                });
                            });
                } catch (IOException e) {
                    LogUtil.e(
                            "IOException caught while saving privacy sandbox feature. %s",
                            e.getMessage());
                }
            } else {
                Stream.of(PrivacySandboxUxCollection.values())
                        .forEach(
                                ux -> {
                                    try {
                                        mDatastore.putBoolean(
                                                ux.toString(), ux.toString().equals(eligibleUx));
                                    } catch (IOException e) {
                                        LogUtil.e(
                                                "IOException caught while setting the current UX."
                                                        + " %s",
                                                e.getMessage());
                                    }
                                });
            }
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    /** Returns the current UX. */
    public String getUx() {
        mReadWriteLock.readLock().lock();
        try {
            return Stream.of(PrivacySandboxUxCollection.values())
                    .filter(ux -> Boolean.TRUE.equals(mDatastore.getBoolean(ux.toString())))
                    .findFirst()
                    .orElse(PrivacySandboxUxCollection.UNSUPPORTED_UX)
                    .toString();
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }

    /** Set the current enrollment channel. */
    public void setEnrollmentChannel(String enrollmentChannel) {
        mReadWriteLock.writeLock().lock();
        try {
            if (FlagsFactory.getFlags()
                    .getEnableAtomicFileDatastoreBatchUpdateApiInSystemServer()) {
                try {
                    mDatastore.update(
                            updateOperation -> {
                                Stream.of(PrivacySandboxEnrollmentChannelCollection.values())
                                        .forEach(
                                                channel -> {
                                                    updateOperation.putBoolean(
                                                            channel.toString(),
                                                            channel.toString()
                                                                    .equals(enrollmentChannel));
                                                });
                            });
                } catch (IOException e) {
                    LogUtil.e(
                            "IOException caught while saving privacy sandbox feature."
                                    + e.getMessage());
                }
            } else {
                Stream.of(PrivacySandboxEnrollmentChannelCollection.values())
                        .forEach(
                                channel -> {
                                    try {
                                        mDatastore.putBoolean(
                                                channel.toString(),
                                                channel.toString().equals(enrollmentChannel));
                                    } catch (IOException e) {
                                        LogUtil.e(
                                                "IOException caught while setting the current "
                                                        + "enrollment channel."
                                                        + e.getMessage());
                                    }
                                });
            }
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    /** Returns the current enrollment channel. */
    public String getEnrollmentChannel() {
        mReadWriteLock.readLock().lock();
        try {
            PrivacySandboxEnrollmentChannelCollection enrollmentChannel =
                    Stream.of(PrivacySandboxEnrollmentChannelCollection.values())
                            .filter(
                                    channel ->
                                            Boolean.TRUE.equals(
                                                    mDatastore.getBoolean(channel.toString())))
                            .findFirst()
                            .orElse(null);
            if (enrollmentChannel != null) {
                return enrollmentChannel.toString();
            }
        } finally {
            mReadWriteLock.readLock().unlock();
        }
        return null;
    }

    @VisibleForTesting static final String IS_MEASUREMENT_DATA_RESET = "IS_MEASUREMENT_DATA_RESET";

    /** Returns whether the isMeasurementDataReset bit is true. */
    public boolean isMeasurementDataReset() {
        return getValueWithLock(IS_MEASUREMENT_DATA_RESET);
    }

    /** Set the isMeasurementDataReset bit in system server. */
    public void setMeasurementDataReset(boolean isMeasurementDataReset) throws IOException {
        setValueWithLock(
                IS_MEASUREMENT_DATA_RESET,
                isMeasurementDataReset,
                /* callerName */ "isMeasurementDataReset");
    }

    @VisibleForTesting static final String IS_PA_DATA_RESET = "IS_Pa_DATA_RESET";

    /** Returns whether the isPaDataReset bit is true. */
    public boolean isPaDataReset() {
        return getValueWithLock(IS_PA_DATA_RESET);
    }

    /** Set the isPaDataReset bit in system server. */
    public void setPaDataReset(boolean isPaDataReset) throws IOException {
        setValueWithLock(IS_PA_DATA_RESET, isPaDataReset, /* callerName */ "isPaDataReset");
    }

    @VisibleForTesting static final String MODULE_ENROLLMENT_STATE = "MODULE_ENROLLMENT_STATE";

    /** Returns the enrollmentState. */
    public String getModuleEnrollmentState() {
        return getStringValueWithLock(MODULE_ENROLLMENT_STATE);
    }

    /** Set enrollmentState in system server. */
    public void setModuleEnrollmentState(String enrollmentState) throws IOException {
        setStringValueWithLock(
                MODULE_ENROLLMENT_STATE, enrollmentState, /* callerName */ "enrollmentState");
    }

    private boolean getValueWithLock(String key) {
        mReadWriteLock.readLock().lock();
        try {
            Boolean value = mDatastore.getBoolean(key);
            return value != null ? value : false;
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }

    /**
     * Sets the boolean value to the Datastore, using read write to make sure it is thread safe.
     *
     * @param key for the datastore
     * @param value boolean value to set
     * @param callerName the function name who call this setValueWithLock
     */
    private void setValueWithLock(String key, Boolean value, String callerName) {
        mReadWriteLock.writeLock().lock();
        try {
            mDatastore.putBoolean(key, value);
        } catch (IOException e) {
            LogUtil.e(
                    e,
                    "%s operation failed due to IOException thrown by Datastore: %s",
                    callerName,
                    e.getMessage());
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    private String getStringValueWithLock(String key) {
        mReadWriteLock.readLock().lock();
        try {
            String value = mDatastore.getString(key);
            return value != null ? value : "";
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }

    private void setStringValueWithLock(String key, String value, String callerName) {
        mReadWriteLock.writeLock().lock();
        try {
            mDatastore.putString(key, value);
        } catch (IOException e) {
            LogUtil.e(
                    e,
                    "%s operation failed due to IOException thrown by Datastore: %s",
                    callerName,
                    e.getMessage());
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    /** Dumps its internal state. */
    public void dump(PrintWriter writer, String prefix) {
        writer.printf("%sConsentManager:\n", prefix);
        String prefix2 = prefix + "  ";

        mDatastore.dump(writer, prefix2, /* args= */ null);
    }
}
