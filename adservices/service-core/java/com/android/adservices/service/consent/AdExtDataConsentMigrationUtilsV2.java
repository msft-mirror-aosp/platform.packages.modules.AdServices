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

package com.android.adservices.service.consent;

import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_TRUE;
import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_UNKNOWN;
import static android.adservices.extdata.AdServicesExtDataParams.STATE_MANUAL_INTERACTIONS_RECORDED;

import static com.android.adservices.service.consent.ConsentManager.getConsentManagerStatsForLogging;

import android.adservices.extdata.AdServicesExtDataParams;
import android.annotation.NonNull;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.adservices.service.appsearch.AppSearchConsentStorageManager;
import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.adservices.service.extdata.AdServicesExtDataStorageServiceManager;
import com.android.adservices.service.stats.ConsentMigrationStats;
import com.android.adservices.service.stats.StatsdAdServicesLogger;
import com.android.modules.utils.build.SdkLevel;

import java.util.Objects;

/**
 * Utility methods for consent migration from AdExtDataStorage (Android R) to AppSearch (Android S)
 * and System Server (Android T+) for ConsentManagerV2 paradigm.
 */
// TODO(b/324273438): Add unit tests and support for consent migration to system server (T+)
public class AdExtDataConsentMigrationUtilsV2 {
    private AdExtDataConsentMigrationUtilsV2() {
        // prevent instantiations
    }

    /**
     * This method handles migration of consent data to AppSearch post-OTA R -> S. Consent data is
     * written to AdServicesExtDataStorageService on R and ported over to AppSearch after OTA to S
     * as it's the new consent source of truth. If any new data is written for consent, we need to
     * make sure it is migrated correctly post-OTA in this method.
     */
    public static void handleConsentMigrationToAppSearchIfNeededV2(
            @NonNull Context context,
            @Nullable AppSearchConsentStorageManager appSearchConsentManager,
            @Nullable AdServicesExtDataStorageServiceManager adExtDataManager,
            @Nullable StatsdAdServicesLogger statsdAdServicesLogger) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(statsdAdServicesLogger);
        LogUtil.d("Check if consent migration to AppSearch is needed.");
        AppConsents appConsents = null;

        // TODO (b/306753680): Add consent migration logging.
        try {
            SharedPreferences sharedPreferences =
                    FileCompatUtils.getSharedPreferencesHelper(
                            context, ConsentConstants.SHARED_PREFS_CONSENT, Context.MODE_PRIVATE);

            if (!isMigrationToAppSearchNeededV2(
                    context, sharedPreferences, appSearchConsentManager, adExtDataManager)) {
                LogUtil.d("Skipping consent migration to AppSearch");
                return;
            }

            // Reduce number of read calls by fetching all the AdExt data at once.
            AdServicesExtDataParams dataFromR = adExtDataManager.getAdServicesExtData();
            if (dataFromR.getIsNotificationDisplayed() != BOOLEAN_TRUE) {
                LogUtil.d("Skipping consent migration to AppSearch; notification not shown on R");
                return;
            }

            appConsents = migrateDataToAppSearchV2(appSearchConsentManager, dataFromR);

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED_TO_APP_SEARCH, true);
            if (editor.commit()) {
                LogUtil.d("Finished migrating consent to AppSearch.");
                logMigrationStatus(
                        statsdAdServicesLogger,
                        appConsents,
                        ConsentMigrationStats.MigrationStatus.SUCCESS_WITH_SHARED_PREF_UPDATED,
                        context);
            } else {
                LogUtil.e("Finished migrating consent to AppSearch. Shared prefs not updated.");
                logMigrationStatus(
                        statsdAdServicesLogger,
                        appConsents,
                        ConsentMigrationStats.MigrationStatus.SUCCESS_WITH_SHARED_PREF_NOT_UPDATED,
                        context);
            }

            // No longer need access to Android R data. Safe to clear here.
            adExtDataManager.clearDataOnOtaAsync();
        } catch (Exception e) {
            LogUtil.e("Consent migration to AppSearch failed: ", e);
            logMigrationStatus(
                    statsdAdServicesLogger,
                    appConsents,
                    ConsentMigrationStats.MigrationStatus.FAILURE,
                    context);
        }
    }

    private static boolean isMigrationToAppSearchNeededV2(
            Context context,
            SharedPreferences sharedPreferences,
            AppSearchConsentStorageManager appSearchConsentManager,
            AdServicesExtDataStorageServiceManager adExtDataManager) {
        if (SdkLevel.isAtLeastT() || !SdkLevel.isAtLeastS()) {
            LogUtil.d("Not S device. Consent migration to AppSearch not needed");
            return false;
        }

        // Cannot be null on S since the consent source of truth has to be APPSEARCH_ONLY.
        Objects.requireNonNull(appSearchConsentManager);

        // There could be a case where we may need to ramp down enable_adext_service_consent_data
        // flag on S, in which case we should gracefully handle consent migration by skipping.
        if (adExtDataManager == null) {
            LogUtil.d("AdExtDataManager is null. Consent migration to AppSearch not needed");
            return false;
        }

        boolean isMigrationToAppSearchDone =
                sharedPreferences.getBoolean(
                        ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED_TO_APP_SEARCH,
                        /* defValue= */ false);
        if (isMigrationToAppSearchDone) {
            LogUtil.d(
                    "Consent migration to AppSearch is already done for user %d.",
                    context.getUser().getIdentifier());
            return false;
        }

        // Just in case, check all notification types to ensure notification is not shown. We do not
        // want to override consent if notification is already shown.
        boolean isNotificationDisplayedOnS =
                appSearchConsentManager.wasU18NotificationDisplayed()
                        || appSearchConsentManager.wasNotificationDisplayed()
                        || appSearchConsentManager.wasGaUxNotificationDisplayed();
        LogUtil.d(
                "Notification shown status on S for migrating consent to AppSearch: "
                        + isNotificationDisplayedOnS);

        // If notification is not shown, we will need to perform another check to ensure
        // notification was shown on R before performing migration. This check will be performed
        // later in order to reduce number of calls to AdExtDataService in the consent migration
        // process.
        return !isNotificationDisplayedOnS;
    }

    @TargetApi(Build.VERSION_CODES.S)
    private static AppConsents migrateDataToAppSearchV2(
            AppSearchConsentStorageManager appSearchConsentStorageManager,
            AdServicesExtDataParams dataFromR) {

        boolean isMeasurementConsented = dataFromR.getIsMeasurementConsented() == BOOLEAN_TRUE;
        appSearchConsentStorageManager.setConsent(
                AdServicesApiType.MEASUREMENTS, isMeasurementConsented);

        appSearchConsentStorageManager.setU18NotificationDisplayed(
                dataFromR.getIsNotificationDisplayed() == BOOLEAN_TRUE);

        // Record interaction data only if we recorded an interaction in
        // AdServicesExtDataStorageService.
        int manualInteractionRecorded = dataFromR.getManualInteractionWithConsentStatus();
        if (manualInteractionRecorded == STATE_MANUAL_INTERACTIONS_RECORDED) {
            appSearchConsentStorageManager.recordUserManualInteractionWithConsent(
                    manualInteractionRecorded);
        }

        if (dataFromR.getIsU18Account() != BOOLEAN_UNKNOWN) {
            appSearchConsentStorageManager.setU18Account(
                    dataFromR.getIsU18Account() == BOOLEAN_TRUE);
        }

        if (dataFromR.getIsAdultAccount() != BOOLEAN_UNKNOWN) {
            appSearchConsentStorageManager.setAdultAccount(
                    dataFromR.getIsAdultAccount() == BOOLEAN_TRUE);
        }
        AppConsents appConsents =
                AppConsents.builder()
                        .setMsmtConsent(isMeasurementConsented)
                        .setFledgeConsent(false)
                        .setTopicsConsent(false)
                        .build();
        return appConsents;
    }

    @TargetApi(Build.VERSION_CODES.S)
    private static void logMigrationStatus(
            StatsdAdServicesLogger statsdAdServicesLogger,
            AppConsents appConsents,
            ConsentMigrationStats.MigrationStatus migrationStatus,
            Context context) {
        statsdAdServicesLogger.logConsentMigrationStats(
                getConsentManagerStatsForLogging(
                        appConsents,
                        migrationStatus,
                        SdkLevel.isAtLeastT()
                                ? ConsentMigrationStats.MigrationType
                                .ADEXT_SERVICE_TO_SYSTEM_SERVICE
                                : ConsentMigrationStats.MigrationType.ADEXT_SERVICE_TO_APPSEARCH,
                        context));
    }
}
