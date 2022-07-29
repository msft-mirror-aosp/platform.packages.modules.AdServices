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


import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.VisibleForTesting;

import com.android.adservices.LogUtil;
import com.android.adservices.data.common.BooleanFileDatastore;
import com.android.adservices.data.consent.AppConsentDao;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.service.topics.TopicsWorker;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manager to handle user's consent.
 *
 * <p> For Beta the consent is given for all {@link AdServicesApiType} or for none. </p>
 */
public class ConsentManager {
    public static final String EEA_DEVICE = "com.google.android.feature.EEA_DEVICE";
    private static final String ERROR_MESSAGE_DATASTORE_EXCEPTION_WHILE_GET_CONTENT =
            "getConsent method failed. Revoked consent is returned as fallback.";
    private static final String NOTIFICATION_DISPLAYED_ONCE = "NOTIFICATION-DISPLAYED-ONCE";
    private static final String CONSENT_ALREADY_INITIALIZED_KEY = "CONSENT-ALREADY-INITIALIZED";
    private static final String CONSENT_KEY = "CONSENT";
    private static final String ERROR_MESSAGE_DATASTORE_IO_EXCEPTION_WHILE_SET_CONTENT =
            "setConsent method failed due to IOException thrown by Datastore.";
    private static final int STORAGE_VERSION = 1;
    private static final String STORAGE_XML_IDENTIFIER = "ConsentManagerStorageIdentifier.xml";

    private static volatile ConsentManager sConsentManager;
    private volatile Boolean mInitialized = false;

    private final TopicsWorker mTopicsWorker;
    private final BooleanFileDatastore mDatastore;
    private final AppConsentDao mAppConsentDao;

    ConsentManager(
            @NonNull Context context,
            @NonNull TopicsWorker topicsWorker,
            @NonNull AppConsentDao appConsentDao) {
        mTopicsWorker = topicsWorker;
        mDatastore = new BooleanFileDatastore(context, STORAGE_XML_IDENTIFIER, STORAGE_VERSION);
        mAppConsentDao = appConsentDao;
    }

    /**
     * Gets an instance of {@link ConsentManager} to be used.
     *
     * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the
     * existing instance will be returned.
     */
    @NonNull
    public static ConsentManager getInstance(@NonNull Context context) {
        if (sConsentManager == null) {
            synchronized (ConsentManager.class) {
                if (sConsentManager == null) {
                    sConsentManager =
                            new ConsentManager(
                                    context,
                                    TopicsWorker.getInstance(context),
                                    AppConsentDao.getInstance(context));
                }
            }
        }
        return sConsentManager;
    }

    /**
     * Enables all PP API services. It gives consent to Topics, Fledge and Measurements services.
     */
    public void enable(@NonNull PackageManager packageManager) {
        // Enable all the APIs
        try {
            init(packageManager);
            setConsent(AdServicesApiConsent.GIVEN);
        } catch (IOException e) {
            LogUtil.e(e, ERROR_MESSAGE_DATASTORE_IO_EXCEPTION_WHILE_SET_CONTENT);
            throw new RuntimeException(ERROR_MESSAGE_DATASTORE_IO_EXCEPTION_WHILE_SET_CONTENT, e);
        }
    }

    /**
     * Disables all PP API services. It revokes consent to Topics, Fledge and Measurements services.
     */
    public void disable(@NonNull PackageManager packageManager) {
        // Disable all the APIs
        try {
            init(packageManager);
            setConsent(AdServicesApiConsent.REVOKED);
        } catch (IOException e) {
            LogUtil.e(e, ERROR_MESSAGE_DATASTORE_IO_EXCEPTION_WHILE_SET_CONTENT);
            throw new RuntimeException(ERROR_MESSAGE_DATASTORE_IO_EXCEPTION_WHILE_SET_CONTENT, e);
        }
    }

    /** Retrieves the consent for all PP API services. */
    public AdServicesApiConsent getConsent(@NonNull PackageManager packageManager) {
        try {
            init(packageManager);
            return AdServicesApiConsent.getConsent(mDatastore.get(CONSENT_KEY));
        } catch (NullPointerException | IllegalArgumentException | IOException e) {
            LogUtil.e(e, ERROR_MESSAGE_DATASTORE_EXCEPTION_WHILE_GET_CONTENT);
            return AdServicesApiConsent.REVOKED;
        }
    }

    /**
     * Proxy call to {@link TopicsWorker} to get {@link ImmutableList} of {@link Topic}s which could
     * be returned to the {@link TopicsWorker} clients.
     *
     * @return {@link ImmutableList} of {@link Topic}s.
     */
    @NonNull
    public ImmutableList<Topic> getKnownTopicsWithConsent() {
        return mTopicsWorker.getKnownTopicsWithConsent();
    }

    /**
     * Proxy call to {@link TopicsWorker} to get {@link ImmutableList} of {@link Topic}s which were
     * blocked by the user.
     *
     * @return {@link ImmutableList} of blocked {@link Topic}s.
     */
    @NonNull
    public ImmutableList<Topic> getTopicsWithRevokedConsent() {
        return mTopicsWorker.getTopicsWithRevokedConsent();
    }

    /**
     * Proxy call to {@link TopicsWorker} to revoke consent for provided {@link Topic} (block
     * topic).
     *
     * @param topic {@link Topic} to block.
     */
    @NonNull
    public void revokeConsentForTopic(@NonNull Topic topic) {
        mTopicsWorker.revokeConsentForTopic(topic);
    }

    /**
     * Proxy call to {@link TopicsWorker} to restore consent for provided {@link Topic} (unblock the
     * topic).
     *
     * @param topic {@link Topic} to restore consent for.
     */
    @NonNull
    public void restoreConsentForTopic(@NonNull Topic topic) {
        mTopicsWorker.restoreConsentForTopic(topic);
    }

    /** Wipes out all the data gathered by Topics API but blocked topics. */
    public void resetTopics() {
        mTopicsWorker.clearAllTopicsData(List.of(TopicsTables.BlockedTopicsContract.TABLE));
    }

    /**
     * @return an {@link ImmutableList} of all known apps in the database that have not had user
     *     consent revoked
     */
    public ImmutableList<App> getKnownAppsWithConsent() {
        try {
            return ImmutableList.copyOf(
                    mAppConsentDao.getKnownAppsWithConsent().stream()
                            .map(App::create)
                            .collect(Collectors.toList()));
        } catch (IOException e) {
            LogUtil.e(e, "getKnownAppsWithConsent failed due to IOException.");
            return ImmutableList.of();
        }
    }

    /**
     * @return an {@link ImmutableList} of all known apps in the database that have had user consent
     *     revoked
     */
    public ImmutableList<App> getAppsWithRevokedConsent() {
        try {
            return ImmutableList.copyOf(
                    mAppConsentDao.getAppsWithRevokedConsent().stream()
                            .map(App::create)
                            .collect(Collectors.toList()));
        } catch (IOException e) {
            LogUtil.e(e, "getAppsWithRevokedConsent failed due to IOException.");
            return ImmutableList.of();
        }
    }

    /**
     * Proxy call to {@link AppConsentDao} to revoke consent for provided {@link App}.
     *
     * @param app {@link App} to block.
     */
    @NonNull
    public void revokeConsentForApp(@NonNull App app) throws IOException {
        mAppConsentDao.setConsentForApp(app.getPackageName(), true);
    }

    /**
     * Proxy call to {@link AppConsentDao} to restore consent for provided {@link App}.
     *
     * @param app {@link App} to restore consent for.
     */
    @NonNull
    public void restoreConsentForApp(@NonNull App app) throws IOException {
        mAppConsentDao.setConsentForApp(app.getPackageName(), false);
    }

    /**
     * Checks whether a single given installed application (identified by its package name) has had
     * user consent to use the FLEDGE APIs revoked.
     *
     * <p>This method also checks whether a user has opted out of the FLEDGE Privacy Sandbox
     * initiative.
     *
     * @param packageManager the {@link PackageManager} used to check initial consent for the
     *     Privacy Sandbox
     * @param packageName String package name that uniquely identifies an installed application to
     *     check
     * @return {@code true} if either the FLEDGE Privacy Sandbox initiative has been opted out or if
     *     the user has revoked consent for the given application to use the FLEDGE APIs
     * @throws IllegalArgumentException if the package name is invalid or not found as an installed
     *     application
     * @throws IOException if the operation fails
     */
    public boolean isFledgeConsentRevokedForApp(
            @NonNull PackageManager packageManager, @NonNull String packageName)
            throws IllegalArgumentException, IOException {
        // TODO(b/238464639): Implement API-specific consent for FLEDGE
        if (!getConsent(packageManager).isGiven()) {
            return true;
        }

        return mAppConsentDao.isConsentRevokedForApp(packageName);
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
     * @param packageManager the {@link PackageManager} used to check initial consent for the
     *     Privacy Sandbox
     * @param packageName String package name that uniquely identifies an installed application that
     *     has used a FLEDGE API
     * @return {@code true} if user consent has been revoked for the application or API, {@code
     *     false} otherwise
     * @throws IllegalArgumentException if the package name is invalid or not found as an installed
     *     application
     * @throws IOException if the operation fails
     */
    public boolean isFledgeConsentRevokedForAppAfterSettingFledgeUse(
            @NonNull PackageManager packageManager, @NonNull String packageName)
            throws IllegalArgumentException, IOException {
        // TODO(b/238464639): Implement API-specific consent for FLEDGE
        if (!getConsent(packageManager).isGiven()) {
            return true;
        }

        return mAppConsentDao.setConsentForAppIfNew(packageName, false);
    }

    /**
     * Saves information to the storage that notification was displayed for the first time to the
     * user.
     */
    public void recordNotificationDisplayed(@NonNull PackageManager packageManager) {
        try {
            init(packageManager);
            // TODO(b/229725886): add metrics / logging
            mDatastore.put(NOTIFICATION_DISPLAYED_ONCE, true);
        } catch (IOException e) {
            LogUtil.e(e, "Record notification failed due to IOException thrown by Datastore.");
        }
    }

    /**
     * Returns information whether Consent Notification was displayed or not.
     *
     * @return true if Consent Notification was displayed, otherwise false.
     */
    public Boolean wasNotificationDisplayed(@NonNull PackageManager packageManager) {
        try {
            init(packageManager);
            return mDatastore.get(NOTIFICATION_DISPLAYED_ONCE);
        } catch (IOException e) {
            LogUtil.e(e, "Record notification failed due to IOException thrown by Datastore.");
            return false;
        }
    }

    private void setConsent(AdServicesApiConsent state)
            throws IOException {
        mDatastore.put(CONSENT_KEY, state.isGiven());
    }

    void init(PackageManager packageManager) throws IOException {
        initializeStorage();
        if (mDatastore.get(CONSENT_ALREADY_INITIALIZED_KEY) == null
                || mDatastore.get(CONSENT_KEY) == null) {
            boolean initialConsent = getInitialConsent(packageManager);
            setInitialConsent(initialConsent);
            mDatastore.put(NOTIFICATION_DISPLAYED_ONCE, false);
            mDatastore.put(CONSENT_ALREADY_INITIALIZED_KEY, true);
        }
    }

    private void initializeStorage() throws IOException {
        if (!mInitialized) {
            synchronized (ConsentManager.class) {
                if (!mInitialized) {
                    mDatastore.initialize();
                    mInitialized = true;
                }
            }
        }
    }

    private void setInitialConsent(boolean initialConsent) throws IOException {
        if (initialConsent) {
            setConsent(AdServicesApiConsent.GIVEN);
        } else {
            setConsent(AdServicesApiConsent.REVOKED);
        }
    }

    @VisibleForTesting
    boolean getInitialConsent(PackageManager packageManager) {
        // The existence of this feature means that device should be treated as EU device.
        return !packageManager.hasSystemFeature(EEA_DEVICE);
    }
}
