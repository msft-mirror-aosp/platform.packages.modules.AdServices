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
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.service.topics.TopicsWorker;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.List;

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

    ConsentManager(@NonNull Context context, @NonNull TopicsWorker topicsWorker) {
        mTopicsWorker = topicsWorker;
        mDatastore = new BooleanFileDatastore(context, STORAGE_XML_IDENTIFIER, STORAGE_VERSION);
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
                            new ConsentManager(context, TopicsWorker.getInstance(context));
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
     * Saves information to the storage that notification was displayed for the first time to the
     * user.
     */
    public void recordNotificationDisplayed() {
        try {
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
    public Boolean wasNotificationDisplayed() {
        return mDatastore.get(NOTIFICATION_DISPLAYED_ONCE);
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
