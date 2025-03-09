/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.common;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_CONSENT_FILTER_ALL_APIS_CONSENT_DISABLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_CONSENT_FILTER_CONSENT_REVOKED_FOR_APP;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_CONSENT_FILTER_MISSING_ANY_NOTIFICATION_DISPLAYED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_CONSENT_FILTER_USER_CONSENT_FOR_API_IS_NOT_GIVEN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED;

import android.adservices.common.AdServicesStatusUtils;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdsRelevanceStatusUtils;

/** Filter for checking user consent in the PA/PAS (formerly FLEDGE) APIs. */
@RequiresApi(Build.VERSION_CODES.S)
public class FledgeConsentFilter {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    private final ConsentManager mConsentManager;
    private final AdServicesLogger mAdServicesLogger;

    public FledgeConsentFilter(ConsentManager consentManager, AdServicesLogger adServicesLogger) {
        mConsentManager = consentManager;
        mAdServicesLogger = adServicesLogger;
    }

    // TODO(b/343521354): Check whether the consent notification for PS has been seen

    /**
     * Asserts that FLEDGE APIs and the Privacy Sandbox as a whole have user consent.
     *
     * <p>Also logs telemetry for the API call.
     *
     * @throws ConsentManager.RevokedConsentException if FLEDGE or the Privacy Sandbox do not have
     *     user consent
     */
    public void assertCallerHasApiUserConsent(String callerPackageName, int apiName) {
        sLogger.v("Checking user consent for FLEDGE API while calling API %d", apiName);
        AdServicesApiConsent userConsent = mConsentManager.getConsent(AdServicesApiType.FLEDGE);

        if (!userConsent.isGiven()) {
            sLogger.v("User consent revoked for FLEDGE API while calling API %d", apiName);
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    callerPackageName,
                    AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED,
                    0);
            int celApiNameId = AdsRelevanceStatusUtils.getCelPpApiNameId(apiName);
            if (celApiNameId != AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED) {
                ErrorLogUtil.e(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_CONSENT_FILTER_USER_CONSENT_FOR_API_IS_NOT_GIVEN,
                        celApiNameId);
            }
            throw new ConsentManager.RevokedConsentException();
        }
    }

    /**
     * Asserts caller has user consent to use FLEDGE APIs in the calling app and persists consent if
     * new.
     *
     * <p>Also logs telemetry for the API call.
     *
     * @throws ConsentManager.RevokedConsentException if FLEDGE or the Privacy Sandbox do not have
     *     user consent
     */
    public void assertAndPersistCallerHasUserConsentForApp(String callerPackageName, int apiName)
            throws ConsentManager.RevokedConsentException {
        sLogger.v(
                "Checking user consent for calling app %s while calling API %d",
                callerPackageName, apiName);
        if (mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(callerPackageName)) {
            sLogger.v(
                    "User consent revoked for calling app %s while calling API %d",
                    callerPackageName, apiName);
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    callerPackageName,
                    AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED,
                    0);
            int celApiNameId = AdsRelevanceStatusUtils.getCelPpApiNameId(apiName);
            if (celApiNameId != AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED) {
                ErrorLogUtil.e(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_CONSENT_FILTER_CONSENT_REVOKED_FOR_APP,
                        celApiNameId);
            }
            throw new ConsentManager.RevokedConsentException();
        }
    }

    /**
     * Asserts that the enrollment job should be scheduled. This will happen if the UX consent
     * notification was displayed, or the user opted into one of the APIs.
     *
     * @throws ConsentManager.RevokedConsentException if the enrollment job should not be scheduled
     */
    public void assertEnrollmentShouldBeScheduled(
            boolean enforceConsentGiven,
            boolean enforceNotificationShown,
            String callerPackageName,
            int apiName) {
        sLogger.v("Checking whether user has seen a notification and opted into any PP API");
        boolean wasAnyNotificationDisplayed;
        int celApiNameId = AdsRelevanceStatusUtils.getCelPpApiNameId(apiName);

        if (!enforceNotificationShown) {
            // Hardcode if we don't need to enforce notification
            wasAnyNotificationDisplayed = true;
        } else {
            wasAnyNotificationDisplayed =
                    mConsentManager.wasNotificationDisplayed()
                            || mConsentManager.wasGaUxNotificationDisplayed()
                            || mConsentManager.wasU18NotificationDisplayed()
                            || mConsentManager.wasPasNotificationDisplayed();
        }

        if (!wasAnyNotificationDisplayed) {
            sLogger.v("UX notification was not displayed!");
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    callerPackageName,
                    AdServicesStatusUtils.STATUS_USER_CONSENT_NOTIFICATION_NOT_DISPLAYED_YET,
                    0);
            if (celApiNameId != AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED) {
                ErrorLogUtil.e(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_CONSENT_FILTER_MISSING_ANY_NOTIFICATION_DISPLAYED,
                        celApiNameId);
            }
            throw new ConsentManager.RevokedConsentException();
        } else if (enforceConsentGiven && mConsentManager.areAllApisDisabled()) {
            sLogger.v("All PP APIs are disabled!");
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    callerPackageName,
                    AdServicesStatusUtils.STATUS_CONSENT_REVOKED_ALL_APIS,
                    0);
            if (celApiNameId != AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED) {
                ErrorLogUtil.e(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_CONSENT_FILTER_ALL_APIS_CONSENT_DISABLED,
                        celApiNameId);
            }
            throw new ConsentManager.RevokedConsentException();
        }
    }
}
