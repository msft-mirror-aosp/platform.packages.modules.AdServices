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

import com.android.adservices.LogUtil;
import com.android.adservices.data.common.BooleanFileDatastore;

import java.io.IOException;
import java.util.Objects;

/**
 * Manager to handle user's consent.
 *
 * <p> For Beta the consent is given for all {@link AdServicesApiType} or for none. </p>
 */
public class ConsentManager {
    public static final String ERROR_MESSAGE_DATASTORE_EXCEPTION_WHILE_GET_CONTENT =
            "getConsent method failed. Revoked consent is returned as fallback.";
    private static final String CONSENT_ALREADY_INITIALIZED_KEY = "CONSENT-ALREADY-INITIALIZED";
    private static final String CONSENT_KEY = "CONSENT";
    private static final String ERROR_MESSAGE_DATASTORE_IO_EXCEPTION_WHILE_SET_CONTENT =
            "setConsent method failed due to IOException thrown by Datastore.";
    private static final int STORAGE_VERSION = 1;
    private static final String STORAGE_XML_IDENTIFIER = "ConsentManagerStorageIdentifier.xml";
    public static final String EEA_DEVICE = "com.google.android.feature.EEA_DEVICE";

    private static volatile ConsentManager sConsentManager;
    private BooleanFileDatastore mDatastore;

    ConsentManager(@NonNull Context appContext) throws IOException {
        init(appContext);
    }

    /**
     * Gets an instance of {@link ConsentManager} to be used.
     *
     * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the
     * existing instance will be returned.
     */
    @NonNull
    public static ConsentManager getInstance(@NonNull Context context) throws IOException {
        if (sConsentManager == null) {
            synchronized (ConsentManager.class) {
                if (sConsentManager == null) {
                    sConsentManager = new ConsentManager(context);
                }
            }
        }
        return sConsentManager;
    }

    private void init(@NonNull Context context) throws IOException {
        mDatastore = new BooleanFileDatastore(context, STORAGE_XML_IDENTIFIER, STORAGE_VERSION);
        mDatastore.initialize();
        if (mDatastore.get(CONSENT_ALREADY_INITIALIZED_KEY) == null) {
            mDatastore.put(CONSENT_ALREADY_INITIALIZED_KEY, true);
            initConsent(context);
        }
    }

    private void initConsent(Context context) {
        // The existence of this feature means that device should be treated as EU device.
        if (context.getPackageManager().hasSystemFeature(EEA_DEVICE)) {
            disable(context);
        } else {
            enable(context);
        }
    }

    /**
     * Enables all PP API services. It gives consent to Topics, Fledge and Measurements
     * services.
     *
     * @param context application context of the caller.
     */
    public void enable(@NonNull Context context) {
        Objects.requireNonNull(context);

        // Enable all the APIs
        try {
            setConsent(AdServicesApiConsent.GIVEN);
        } catch (IOException e) {
            LogUtil.e(e, ERROR_MESSAGE_DATASTORE_IO_EXCEPTION_WHILE_SET_CONTENT);
            throw new RuntimeException(ERROR_MESSAGE_DATASTORE_IO_EXCEPTION_WHILE_SET_CONTENT, e);
        }
    }

    /**
     * Disables all PP API services. It revokes consent to Topics, Fledge and
     * Measurements services.
     *
     * @param context application context of the caller.
     */
    public void disable(@NonNull Context context) {
        Objects.requireNonNull(context);

        // Disable all the APIs
        try {
            setConsent(AdServicesApiConsent.REVOKED);
        } catch (IOException e) {
            LogUtil.e(e, ERROR_MESSAGE_DATASTORE_IO_EXCEPTION_WHILE_SET_CONTENT);
            throw new RuntimeException(ERROR_MESSAGE_DATASTORE_IO_EXCEPTION_WHILE_SET_CONTENT, e);
        }
    }

    /**
     * Retrieves the consent for all PP API services.
     *
     * @param context application context of the caller.
     */
    public AdServicesApiConsent getConsent(@NonNull Context context) {
        Objects.requireNonNull(context);

        if (getConsent().isGiven()) {
            return AdServicesApiConsent.GIVEN;
        }
        return AdServicesApiConsent.REVOKED;
    }

    private AdServicesApiConsent getConsent() {
        try {
            return AdServicesApiConsent.getConsent(mDatastore.get(CONSENT_KEY));
        } catch (NullPointerException | IllegalArgumentException e) {
            LogUtil.e(e, ERROR_MESSAGE_DATASTORE_EXCEPTION_WHILE_GET_CONTENT);
            return AdServicesApiConsent.REVOKED;
        }
    }

    private void setConsent(AdServicesApiConsent state)
            throws IOException {
        mDatastore.put(CONSENT_KEY, state.isGiven());
    }
}
