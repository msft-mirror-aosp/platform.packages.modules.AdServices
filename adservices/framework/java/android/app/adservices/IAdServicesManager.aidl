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

package android.app.adservices;

import android.app.adservices.consent.ConsentParcel;
import android.app.adservices.topics.TopicParcel;

/**
  * AdServices Manager Service
  *
  * {@hide}
  */
interface IAdServicesManager {
    /**
     * Get Consent
     */
    ConsentParcel getConsent(in int consentApiType);

    /**
     * Set Consent
     */
    void setConsent(in ConsentParcel consentParcel);

    /**
     * Saves information to the storage that a deletion of measurement data occurred.
     */
     void recordAdServicesDeletionOccurred(in int deletionType);

    /**
     * Saves information to the storage that notification was displayed for the first time to the
     * user.
     */
    void recordNotificationDisplayed();

    /**
     * Returns information whether Consent Notification was displayed or not.
     *
     * @return true if Consent Notification was displayed, otherwise false.
     */
    boolean wasNotificationDisplayed();

    /**
     * Saves information to the storage that GA UX notification was displayed for the
     * first time to the user.
     */
    void recordGaUxNotificationDisplayed();

    /**
     * Returns information whether GA UX Consent Notification was displayed or not.
     *
     * @return true if GA UX Consent Notification was displayed, otherwise false.
     */
    boolean wasGaUxNotificationDisplayed();

    /**
     * Saves information to the storage that topics consent page was displayed for the
     * first time to the user.
     */
    void recordTopicsConsentPageDisplayed();

    /**
     * Record a blocked topic.
     */
    void recordBlockedTopic(in List<TopicParcel> blockedTopicParcels);

    /**
     * Remove a blocked topic.
     */
    void removeBlockedTopic(in TopicParcel blockedTopicParcel);

    /**
     * Get all blocked topics.
     */
    List<TopicParcel> retrieveAllBlockedTopics();

    /**
     * Returns information whether topics consent page was displayed or not.
     *
     * @return true if topics consent page was displayed, otherwise false.
     */
    boolean wasTopicsConsentPageDisplayed();

    /**
     * Saves information to the storage that fledge consent page was displayed for the
     * first time to the user.
     */
    void recordFledgeAndMsmtConsentPageDisplayed();

    /**
     * Returns information whether fledge and measurement consent page was displayed or not.
     *
     * @return true if fledge and measurement consent page was displayed, otherwise false.
     */
    boolean wasFledgeAndMsmtConsentPageDisplayed();

    List<String> getKnownAppsWithConsent(in List<String> installedPackages);

    List<String> getAppsWithRevokedConsent(in List<String> installedPackages);

    void setConsentForApp(in String packageName,in int packageUid,in boolean isConsentRevoked);

    void clearKnownAppsWithConsent();

    void clearAllAppConsentData();

    boolean isConsentRevokedForApp(in String packageName,in int packageUid);

    boolean setConsentForAppIfNew(in String packageName,in int packageUid,in boolean isConsentRevoked);

    void clearConsentForUninstalledApp(in String packageName,in int packageUid);
}
