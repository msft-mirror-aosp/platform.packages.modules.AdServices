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

import android.annotation.NonNull;
import android.app.adservices.consent.ConsentParcel;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.adservices.LogUtil;
import com.android.server.adservices.common.BooleanFileDatastore;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

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

    static final String TOPICS_CONSENT_PAGE_DISPLAYED = "TOPICS-CONSENT-PAGE-DISPLAYED";

    static final String FLEDGE_AND_MSMT_CONSENT_PAGE_DISPLAYED =
            "FLDEGE-AND-MSMT-CONDENT-PAGE-DISPLAYED";

    private static final String CONSENT_API_TYPE_PREFIX = "CONSENT_API_TYPE_";

    // Deprecate this since we store each version in its own folder.
    static final int STORAGE_VERSION = 1;
    static final String STORAGE_XML_IDENTIFIER = "ConsentManagerStorageIdentifier.xml";

    private final BooleanFileDatastore mDatastore;

    @VisibleForTesting static final String DEFAULT_CONSENT = "DEFAULT_CONSENT";

    @VisibleForTesting static final String TOPICS_DEFAULT_CONSENT = "TOPICS_DEFAULT_CONSENT";

    @VisibleForTesting static final String FLEDGE_DEFAULT_CONSENT = "FLEDGE_DEFAULT_CONSENT";

    @VisibleForTesting
    static final String MEASUREMENT_DEFAULT_CONSENT = "MEASUREMENT_DEFAULT_CONSENT";

    @VisibleForTesting static final String DEFAULT_AD_ID_STATE = "DEFAULT_AD_ID_STATE";

    private ConsentManager(@NonNull BooleanFileDatastore datastore) {
        Objects.requireNonNull(datastore);

        mDatastore = datastore;
    }

    /** Create a ConsentManager with base directory and for userIdentifier */
    @NonNull
    public static ConsentManager createConsentManager(@NonNull String baseDir, int userIdentifier)
            throws IOException {
        Objects.requireNonNull(baseDir, "Base dir must be provided.");

        // The Data store is in folder with the following format.
        // /data/system/adservices/user_id/consent/data_schema_version/
        // Create the consent directory if needed.
        String consentDataStoreDir =
                ConsentDatastoreLocationHelper.getConsentDataStoreDirAndCreateDir(
                        baseDir, userIdentifier);

        BooleanFileDatastore datastore = createAndInitBooleanFileDatastore(consentDataStoreDir);

        return new ConsentManager(datastore);
    }

    @NonNull
    @VisibleForTesting
    static BooleanFileDatastore createAndInitBooleanFileDatastore(String consentDataStoreDir)
            throws IOException {
        // Create the DataStore and initialize it.
        BooleanFileDatastore datastore =
                new BooleanFileDatastore(
                        consentDataStoreDir, STORAGE_XML_IDENTIFIER, STORAGE_VERSION, VERSION_KEY);
        datastore.initialize();
        // TODO(b/259607624): implement a method in the datastore which would support
        // this exact scenario - if the value is null, return default value provided
        // in the parameter (similar to SP apply etc.)
        if (datastore.get(NOTIFICATION_DISPLAYED_ONCE) == null) {
            datastore.put(NOTIFICATION_DISPLAYED_ONCE, false);
        }
        if (datastore.get(GA_UX_NOTIFICATION_DISPLAYED_ONCE) == null) {
            datastore.put(GA_UX_NOTIFICATION_DISPLAYED_ONCE, false);
        }
        if (datastore.get(TOPICS_CONSENT_PAGE_DISPLAYED) == null) {
            datastore.put(TOPICS_CONSENT_PAGE_DISPLAYED, false);
        }
        if (datastore.get(FLEDGE_AND_MSMT_CONSENT_PAGE_DISPLAYED) == null) {
            datastore.put(FLEDGE_AND_MSMT_CONSENT_PAGE_DISPLAYED, false);
        }
        return datastore;
    }

    /** Retrieves the consent for all PP API services. */
    public ConsentParcel getConsent(@ConsentParcel.ConsentApiType int consentApiType) {
        LogUtil.d("ConsentManager.getConsent() is invoked for consentApiType = " + consentApiType);

        synchronized (this) {
            try {
                return new ConsentParcel.Builder()
                        .setConsentApiType(consentApiType)
                        .setIsGiven(mDatastore.get(getConsentApiTypeKey(consentApiType)))
                        .build();
            } catch (NullPointerException | IllegalArgumentException e) {
                LogUtil.e(e, ERROR_MESSAGE_DATASTORE_EXCEPTION_WHILE_GET_CONTENT);
                return ConsentParcel.createRevokedConsent(consentApiType);
            }
        }
    }

    /** Set Consent */
    public void setConsent(ConsentParcel consentParcel) throws IOException {
        synchronized (this) {
            mDatastore.put(
                    getConsentApiTypeKey(consentParcel.getConsentApiType()),
                    consentParcel.isIsGiven());
            if (consentParcel.getConsentApiType() == ConsentParcel.ALL_API) {
                // Convert from 1 to 3 consents.
                mDatastore.put(
                        getConsentApiTypeKey(ConsentParcel.TOPICS), consentParcel.isIsGiven());
                mDatastore.put(
                        getConsentApiTypeKey(ConsentParcel.FLEDGE), consentParcel.isIsGiven());
                mDatastore.put(
                        getConsentApiTypeKey(ConsentParcel.MEASUREMENT), consentParcel.isIsGiven());
            } else {
                // Convert from 3 consents to 1 consent.
                if (mDatastore.get(
                                getConsentApiTypeKey(ConsentParcel.TOPICS), /* defaultValue */
                                false)
                        && mDatastore.get(
                                getConsentApiTypeKey(ConsentParcel.FLEDGE), /* defaultValue */
                                false)
                        && mDatastore.get(
                                getConsentApiTypeKey(ConsentParcel.MEASUREMENT), /* defaultValue */
                                false)) {
                    mDatastore.put(getConsentApiTypeKey(ConsentParcel.ALL_API), true);
                } else {
                    mDatastore.put(getConsentApiTypeKey(ConsentParcel.ALL_API), false);
                }
            }
        }
    }

    /**
     * Saves information to the storage that notification was displayed for the first time to the
     * user.
     */
    public void recordNotificationDisplayed() throws IOException {
        synchronized (this) {
            try {
                // TODO(b/229725886): add metrics / logging
                mDatastore.put(NOTIFICATION_DISPLAYED_ONCE, true);
            } catch (IOException e) {
                LogUtil.e(e, "Record notification failed due to IOException thrown by Datastore.");
            }
        }
    }

    /**
     * Returns information whether Consent Notification was displayed or not.
     *
     * @return true if Consent Notification was displayed, otherwise false.
     */
    public boolean wasNotificationDisplayed() {
        synchronized (this) {
            return mDatastore.get(NOTIFICATION_DISPLAYED_ONCE);
        }
    }

    /**
     * Saves information to the storage that GA UX notification was displayed for the first time to
     * the user.
     */
    public void recordGaUxNotificationDisplayed() throws IOException {
        synchronized (this) {
            try {
                // TODO(b/229725886): add metrics / logging
                mDatastore.put(GA_UX_NOTIFICATION_DISPLAYED_ONCE, true);
            } catch (IOException e) {
                LogUtil.e(e, "Record notification failed due to IOException thrown by Datastore.");
            }
        }
    }

    /**
     * Returns information whether GA Ux Consent Notification was displayed or not.
     *
     * @return true if GA UX Consent Notification was displayed, otherwise false.
     */
    public boolean wasGaUxNotificationDisplayed() {
        synchronized (this) {
            Boolean displayed = mDatastore.get(GA_UX_NOTIFICATION_DISPLAYED_ONCE);
            return displayed != null ? displayed : false;
        }
    }

    /** Saves the default consent of a user. */
    public void recordDefaultConsent(boolean defaultConsent) throws IOException {
        synchronized (this) {
            try {
                mDatastore.put(DEFAULT_CONSENT, defaultConsent);
            } catch (IOException e) {
                LogUtil.e(
                        e,
                        "Record default consent failed due to IOException thrown by Datastore: "
                                + e.getMessage());
            }
        }
    }

    /** Saves the default topics consent of a user. */
    public void recordTopicsDefaultConsent(boolean defaultConsent) throws IOException {
        synchronized (this) {
            try {
                mDatastore.put(TOPICS_DEFAULT_CONSENT, defaultConsent);
            } catch (IOException e) {
                LogUtil.e(
                        e,
                        "Record topics default consent failed due to IOException thrown by"
                                + " Datastore: "
                                + e.getMessage());
            }
        }
    }

    /** Saves the default FLEDGE consent of a user. */
    public void recordFledgeDefaultConsent(boolean defaultConsent) throws IOException {
        synchronized (this) {
            try {
                mDatastore.put(FLEDGE_DEFAULT_CONSENT, defaultConsent);
            } catch (IOException e) {
                LogUtil.e(
                        e,
                        "Record fledge default consent failed due to IOException thrown by"
                                + " Datastore: "
                                + e.getMessage());
            }
        }
    }

    /** Saves the default measurement consent of a user. */
    public void recordMeasurementDefaultConsent(boolean defaultConsent) throws IOException {
        synchronized (this) {
            try {
                mDatastore.put(MEASUREMENT_DEFAULT_CONSENT, defaultConsent);
            } catch (IOException e) {
                LogUtil.e(
                        e,
                        "Record measurement default consent failed due to IOException thrown by"
                                + " Datastore: "
                                + e.getMessage());
            }
        }
    }

    /** Saves the default AdId state of a user. */
    public void recordDefaultAdIdState(boolean defaultAdIdState) throws IOException {
        synchronized (this) {
            try {
                mDatastore.put(DEFAULT_AD_ID_STATE, defaultAdIdState);
            } catch (IOException e) {
                LogUtil.e(
                        e,
                        "Record default AdId failed due to IOException thrown by Datastore: "
                                + e.getMessage());
            }
        }
    }

    /**
     * Returns the default consent state.
     *
     * @return true if default consent is given, otherwise false.
     */
    public boolean getDefaultConsent() {
        synchronized (this) {
            Boolean defaultConsent = mDatastore.get(DEFAULT_CONSENT);
            return defaultConsent != null ? defaultConsent : false;
        }
    }

    /**
     * Returns the topics default consent state.
     *
     * @return true if topics default consent is given, otherwise false.
     */
    public boolean getTopicsDefaultConsent() {
        synchronized (this) {
            Boolean topicsDefaultConsent = mDatastore.get(TOPICS_DEFAULT_CONSENT);
            return topicsDefaultConsent != null ? topicsDefaultConsent : false;
        }
    }

    /**
     * Returns the FLEDGE default consent state.
     *
     * @return true if default consent is given, otherwise false.
     */
    public boolean getFledgeDefaultConsent() {
        synchronized (this) {
            Boolean fledgeDefaultConsent = mDatastore.get(DEFAULT_CONSENT);
            return fledgeDefaultConsent != null ? fledgeDefaultConsent : false;
        }
    }

    /**
     * Returns the measurement default consent state.
     *
     * @return true if default consent is given, otherwise false.
     */
    public boolean getMeasurementDefaultConsent() {
        synchronized (this) {
            Boolean measurementDefaultConsent = mDatastore.get(DEFAULT_CONSENT);
            return measurementDefaultConsent != null ? measurementDefaultConsent : false;
        }
    }

    /**
     * Returns the default AdId state when consent notification was sent.
     *
     * @return true if AdId is enabled by default, otherwise false.
     */
    public boolean getDefaultAdIdState() {
        synchronized (this) {
            Boolean defaultAdIdState = mDatastore.get(DEFAULT_AD_ID_STATE);
            return defaultAdIdState != null ? defaultAdIdState : false;
        }
    }

    /**
     * Deletes the user directory which contains consent information present at
     * /data/system/adservices/user_id
     */
    public boolean deleteUserDirectory(File dir) throws IOException {
        synchronized (this) {
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
        }
    }

    @VisibleForTesting
    String getConsentApiTypeKey(@ConsentParcel.ConsentApiType int consentApiType) {
        return CONSENT_API_TYPE_PREFIX + consentApiType;
    }

    /** tearDown method used for Testing only. */
    @VisibleForTesting
    public void tearDownForTesting() {
        synchronized (this) {
            mDatastore.tearDownForTesting();
        }
    }
}
