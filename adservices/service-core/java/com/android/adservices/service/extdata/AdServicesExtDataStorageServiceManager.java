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

package com.android.adservices.service.extdata;

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_TIMEOUT;
import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_UNKNOWN;
import static android.adservices.extdata.AdServicesExtDataParams.STATE_UNKNOWN;
import static android.adservices.extdata.AdServicesExtDataStorageService.AdServicesExtDataFieldId;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_ADULT_ACCOUNT;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_MEASUREMENT_CONSENTED;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_NOTIFICATION_DISPLAYED;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_U18_ACCOUNT;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_MANUAL_INTERACTION_WITH_CONSENT_STATUS;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_MEASUREMENT_ROLLBACK_APEX_VERSION;

import static com.android.adservices.service.measurement.rollback.MeasurementRollbackCompatManager.APEX_VERSION_WHEN_NOT_FOUND;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__ADEXT_DATA_SERVICE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_AD_SERVICES_EXT_DATA;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__PUT_AD_SERVICES_EXT_DATA;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_ADEXT_DATA_SERVICE_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PUT_ADEXT_DATA_SERVICE_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__ADEXT_DATA_SERVICE;

import android.adservices.common.AdServicesOutcomeReceiver;
import android.adservices.extdata.AdServicesExtDataParams;
import android.adservices.extdata.AdServicesExtDataStorageService;

import com.android.adservices.LogUtil;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class manages the interface to read and write data required on Android R through {@link
 * AdServicesExtDataStorageService}.
 */
public final class AdServicesExtDataStorageServiceManager {
    @VisibleForTesting static final String UNKNOWN_PACKAGE_NAME = "unknown";

    private static final AdServicesExtDataParams DEFAULT_PARAMS =
            new AdServicesExtDataParams.Builder()
                    .setNotificationDisplayed(BOOLEAN_UNKNOWN)
                    .setMsmtConsent(BOOLEAN_UNKNOWN)
                    .setIsU18Account(BOOLEAN_UNKNOWN)
                    .setIsAdultAccount(BOOLEAN_UNKNOWN)
                    .setManualInteractionWithConsentStatus(STATE_UNKNOWN)
                    .setMsmtRollbackApexVersion(APEX_VERSION_WHEN_NOT_FOUND)
                    .build();

    // FIELD_IS_NOTIFICATION_DISPLAYED is not cleared as this information is used for notification
    // eligibility upon OTA. As there is no requirement to delete this metadata, we won't be
    // clearing it to simplify OTA deletion logic which can now happen immediately after consent
    // migration.
    private static final int[] DATA_FIELDS_TO_CLEAR_POST_OTA =
            new int[] {
                FIELD_IS_MEASUREMENT_CONSENTED,
                FIELD_IS_U18_ACCOUNT,
                FIELD_IS_ADULT_ACCOUNT,
                FIELD_MANUAL_INTERACTION_WITH_CONSENT_STATUS,
                FIELD_MEASUREMENT_ROLLBACK_APEX_VERSION
            };

    private final AdServicesExtDataStorageServiceWorker mDataWorker;
    private final AdServicesLogger mAdServicesLogger;
    private final Clock mClock;
    private final String mPackageName;

    private AdServicesExtDataStorageServiceManager(
            AdServicesExtDataStorageServiceWorker dataWorker,
            AdServicesLogger adServicesLogger,
            String packageName) {
        mDataWorker = Objects.requireNonNull(dataWorker);
        mAdServicesLogger = Objects.requireNonNull(adServicesLogger);
        mPackageName = Objects.requireNonNull(packageName);
        mClock = Clock.getInstance();
    }

    /** Init {@link AdServicesExtDataStorageServiceManager}. */
    public static AdServicesExtDataStorageServiceManager getInstance() {
        return new AdServicesExtDataStorageServiceManager(
                AdServicesExtDataStorageServiceWorker.getInstance(),
                AdServicesLoggerImpl.getInstance(),
                ApplicationContextSingleton.get().getPackageName());
    }

    /**
     * Fetches all AdExt data.
     *
     * @return {@link AdServicesExtDataParams} data object with the current state of AdExt data.
     */
    public AdServicesExtDataParams getAdServicesExtData() {
        long startServiceTime = mClock.elapsedRealtime();
        CountDownLatch latch = new CountDownLatch(1);

        AtomicBoolean isSuccess = new AtomicBoolean();
        AtomicInteger notificationDisplayed = new AtomicInteger();
        AtomicInteger msmtConsent = new AtomicInteger();
        AtomicInteger isU18Account = new AtomicInteger();
        AtomicInteger isAdultAccount = new AtomicInteger();
        AtomicInteger manualInteractionWithConsentStatus = new AtomicInteger();
        AtomicLong apexVersion = new AtomicLong();

        mDataWorker.getAdServicesExtData(
                new AdServicesOutcomeReceiver<>() {
                    @Override
                    public void onResult(AdServicesExtDataParams result) {
                        notificationDisplayed.set(result.getIsNotificationDisplayed());
                        msmtConsent.set(result.getIsMeasurementConsented());
                        isU18Account.set(result.getIsU18Account());
                        isAdultAccount.set(result.getIsAdultAccount());
                        manualInteractionWithConsentStatus.set(
                                result.getManualInteractionWithConsentStatus());
                        apexVersion.set(result.getMeasurementRollbackApexVersion());
                        isSuccess.set(true);
                        latch.countDown();
                    }

                    @Override
                    public void onError(Exception e) {
                        LogUtil.e(e, "Error when getting AdExt data! Returning default values.");
                        ErrorLogUtil.e(
                                e,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_ADEXT_DATA_SERVICE_ERROR,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__ADEXT_DATA_SERVICE);
                        latch.countDown();
                    }
                });

        boolean timedOut;
        AdServicesExtDataParams params = DEFAULT_PARAMS;
        int resultCode = STATUS_SUCCESS;
        try {
            int timeoutMs = FlagsFactory.getFlags().getAdExtReadTimeoutMs();
            timedOut = !latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (timedOut) {
                LogUtil.e("Getting AdExt data timed out! Returning default values.");
                resultCode = STATUS_TIMEOUT;
            } else {
                if (isSuccess.get()) {
                    params =
                            constructParams(
                                    notificationDisplayed.get(),
                                    msmtConsent.get(),
                                    isU18Account.get(),
                                    isAdultAccount.get(),
                                    manualInteractionWithConsentStatus.get(),
                                    apexVersion.get());
                } else {
                    resultCode = STATUS_INTERNAL_ERROR;
                }
            }
        } catch (Exception e) {
            LogUtil.e(e, "Error when awaiting latch! Returning default values.");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_ADEXT_DATA_SERVICE_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__ADEXT_DATA_SERVICE);
            resultCode = STATUS_INTERNAL_ERROR;
        } finally {
            logApiCallStats(
                    startServiceTime,
                    AD_SERVICES_API_CALLED__API_NAME__GET_AD_SERVICES_EXT_DATA,
                    resultCode);
        }

        LogUtil.d("Returned AdExt data: %s", params);
        return params;
    }

    /**
     * Updates AdExt data.
     *
     * @param params data object that holds the values to be updated.
     * @param fieldsToUpdate explicit list of field IDs that correspond to the fields from params,
     *     which are meant to be updated.
     * @return true if update is successful; false otherwise.
     */
    public boolean setAdServicesExtData(
            AdServicesExtDataParams params, @AdServicesExtDataFieldId int[] fieldsToUpdate) {
        Objects.requireNonNull(params);
        Objects.requireNonNull(fieldsToUpdate);

        long startServiceTime = mClock.elapsedRealtime();

        if (fieldsToUpdate.length == 0) {
            LogUtil.d("No AdExt data fields to update. Returning early.");
            return true;
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean isSuccess = new AtomicBoolean();
        mDataWorker.setAdServicesExtData(
                params,
                fieldsToUpdate,
                new AdServicesOutcomeReceiver<>() {
                    @Override
                    public void onResult(AdServicesExtDataParams result) {
                        LogUtil.d(
                                "Updated AdExt Data: %s",
                                updateRequestToString(params, fieldsToUpdate));
                        isSuccess.set(true);
                        latch.countDown();
                    }

                    @Override
                    public void onError(Exception e) {
                        LogUtil.e(e, "Exception when updating AdExt data!");
                        ErrorLogUtil.e(
                                e,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PUT_ADEXT_DATA_SERVICE_ERROR,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__ADEXT_DATA_SERVICE);
                        latch.countDown();
                    }
                });

        boolean timedOut;
        int resultCode = STATUS_SUCCESS;
        try {
            int timeoutMs = FlagsFactory.getFlags().getAdExtWriteTimeoutMs();
            timedOut = !latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (timedOut) {
                resultCode = STATUS_TIMEOUT;
                LogUtil.e("Updating AdExt data failed due to timeout!");
                return false;
            } else if (isSuccess.get()) {
                return true;
            } else {
                resultCode = STATUS_INTERNAL_ERROR;
                return false;
            }
        } catch (Exception e) {
            LogUtil.e(e, "Failed to update AdExt data! Exception when awaiting latch! ");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PUT_ADEXT_DATA_SERVICE_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__ADEXT_DATA_SERVICE);
            resultCode = STATUS_INTERNAL_ERROR;
            return false;
        } finally {
            logApiCallStats(
                    startServiceTime,
                    AD_SERVICES_API_CALLED__API_NAME__PUT_AD_SERVICES_EXT_DATA,
                    resultCode);
        }
    }

    private AdServicesExtDataParams constructParams(
            int notificationDisplayed,
            int msmtConsent,
            int isU18Account,
            int isAdultAccount,
            int manualInteractionStatus,
            long msmtApex) {
        return new AdServicesExtDataParams.Builder()
                .setNotificationDisplayed(notificationDisplayed)
                .setMsmtConsent(msmtConsent)
                .setIsU18Account(isU18Account)
                .setIsAdultAccount(isAdultAccount)
                .setManualInteractionWithConsentStatus(manualInteractionStatus)
                .setMsmtRollbackApexVersion(msmtApex)
                .build();
    }

    private void logApiCallStats(long startServiceTime, int apiName, int resultCode) {
        // only log when not using debug proxy
        if (!FlagsFactory.getFlags().getEnableAdExtServiceDebugProxy()) {
            int apiLatency = (int) (mClock.elapsedRealtime() - startServiceTime);
            mAdServicesLogger.logApiCallStats(
                    new ApiCallStats.Builder()
                            .setCode(AdServicesStatsLog.AD_SERVICES_API_CALLED)
                            .setApiClass(AD_SERVICES_API_CALLED__API_CLASS__ADEXT_DATA_SERVICE)
                            .setApiName(apiName)
                            .setAppPackageName(mPackageName)
                            .setSdkPackageName(UNKNOWN_PACKAGE_NAME)
                            .setLatencyMillisecond(apiLatency)
                            .setResultCode(resultCode)
                            .build());
        }
    }

    /** Converts AdExt data to be updated into readable string, used for debug logging. */
    @VisibleForTesting
    String updateRequestToString(AdServicesExtDataParams params, int[] fieldsToUpdate) {
        StringBuilder sb = new StringBuilder("{");
        for (int fieldId : fieldsToUpdate) {
            switch (fieldId) {
                case FIELD_IS_MEASUREMENT_CONSENTED:
                    sb.append("MsmtConsent: ").append(params.getIsMeasurementConsented());
                    break;
                case FIELD_IS_NOTIFICATION_DISPLAYED:
                    sb.append("NotificationDisplayed: ")
                            .append(params.getIsNotificationDisplayed());
                    break;
                case FIELD_IS_U18_ACCOUNT:
                    sb.append("IsU18Account: ").append(params.getIsU18Account());
                    break;
                case FIELD_IS_ADULT_ACCOUNT:
                    sb.append("IsAdultAccount: ").append(params.getIsAdultAccount());
                    break;
                case FIELD_MANUAL_INTERACTION_WITH_CONSENT_STATUS:
                    sb.append("ManualInteractionWithConsentStatus: ")
                            .append(params.getManualInteractionWithConsentStatus());
                    break;
                case FIELD_MEASUREMENT_ROLLBACK_APEX_VERSION:
                    sb.append("MsmtRollbackApexVersion: ")
                            .append(params.getMeasurementRollbackApexVersion());
                    break;
                default:
                    // Handle gracefully because this is only used for debugging.
                    LogUtil.e("Invalid AdExt data field Id detected: %d", fieldId);
                    sb.append("INVALID_FIELD_ID: ").append(fieldId);
            }
            sb.append(",");
        }
        return sb.append("}").toString();
    }
}
