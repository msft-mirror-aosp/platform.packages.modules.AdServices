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

import java.util.Objects;

/**
 * Manager to handle user's consent.
 *
 * <p> For Beta the consent is given for all {@link AdServicesApiType} or for none. </p>
 */
public class ConsentManager {
    private static ConsentManager sConsentManager;

    ConsentManager() {
        init();
    }

    /**
     * Gets an instance of {@link ConsentManager} to be used.
     *
     * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the
     * existing instance will be returned.
     */
    @NonNull
    public static ConsentManager getInstance() {
        if (sConsentManager == null) {
            sConsentManager = new ConsentManager();
        }
        return sConsentManager;
    }

    private void init() {
        //TODO(b/228287844): init db
    }

    /**
     * Enables all PP API services. It atomically gives consent to Topics, Fledge and Measurements
     * services.
     *
     * @param appContext application context of the caller.
     */
    public void enable(Context appContext) {
        Objects.requireNonNull(appContext);

        // Enable all the APIs one by one
        setConsent(AdServicesApiType.TOPICS, AdServicesApiConsent.getGivenConsent());
        setConsent(AdServicesApiType.FLEDGE, AdServicesApiConsent.getGivenConsent());
        setConsent(AdServicesApiType.MEASUREMENTS, AdServicesApiConsent.getGivenConsent());
    }

    /**
     * Disables all PP API services. It atomically revokes consent to Topics, Fledge and
     * Measurements services.
     *
     * @param appContext application context of the caller.
     */
    public void disable(Context appContext) {
        Objects.requireNonNull(appContext);

        // Disable all the APIs one by one
        setConsent(AdServicesApiType.TOPICS, AdServicesApiConsent.getRevokedConsent());
        setConsent(AdServicesApiType.FLEDGE, AdServicesApiConsent.getRevokedConsent());
        setConsent(AdServicesApiType.MEASUREMENTS, AdServicesApiConsent.getRevokedConsent());
    }

    /**
     * Retrieves the consent for all PP API services. Consent is given if and only if consents to
     * all PP API services were given.
     *
     * @param appContext application context of the caller.
     */
    public AdServicesApiConsent getConsent(Context appContext) {
        Objects.requireNonNull(appContext);

        if (getConsent(AdServicesApiType.TOPICS).isGiven()
                && getConsent(AdServicesApiType.FLEDGE).isGiven()
                && getConsent(AdServicesApiType.MEASUREMENTS).isGiven()) {
            return AdServicesApiConsent.getGivenConsent();
        }
        return AdServicesApiConsent.getRevokedConsent();
    }

    private AdServicesApiConsent getConsent(AdServicesApiType apiType) {
        //TODO(b/228287844): change it to real values
        return AdServicesApiConsent.getGivenConsent();
    }

    private void setConsent(AdServicesApiType apiType, AdServicesApiConsent state) {
        //TODO(b/228287844): add logic to store consent
    }
}
