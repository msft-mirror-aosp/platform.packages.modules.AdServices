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

import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_UNKNOWN;
import static android.adservices.extdata.AdServicesExtDataParams.STATE_UNKNOWN;
import static android.adservices.extdata.AdServicesExtDataStorageService.AdServicesExtDataFieldId;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_ADULT_ACCOUNT;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_MEASUREMENT_CONSENTED;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_NOTIFICATION_DISPLAYED;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_U18_ACCOUNT;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_MANUAL_INTERACTION_WITH_CONSENT_STATUS;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_MEASUREMENT_ROLLBACK_APEX_VERSION;

import android.adservices.common.AdServicesOutcomeReceiver;
import android.adservices.extdata.AdServicesExtDataParams;
import android.adservices.extdata.AdServicesExtDataStorageService;
import android.annotation.NonNull;
import android.content.Context;

import com.android.adservices.LogUtil;
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
public class AdServicesExtDataStorageServiceManager {
    // Conservative timeouts based on what's used for AppSearch operations (500 ms for reads,
    // 2000 ms writes). An additional 100 ms buffer is added for delays such as binder latency.
    private static final long READ_OPERATION_TIMEOUT_MS = 600L;
    private static final long WRITE_OPERATION_TIMEOUT_MS = 2100L;
    // TODO (b/303513097): Reference the variable directly from AppSearchMeasurementRollbackManager
    //  when it's supported on R to avoid lint error.
    private static final long APEX_VERSION_WHEN_NOT_FOUND = -1L;

    private final AdServicesExtDataStorageServiceWorker mDataWorker;

    private AdServicesExtDataStorageServiceManager(
            @NonNull AdServicesExtDataStorageServiceWorker dataWorker) {
        mDataWorker = Objects.requireNonNull(dataWorker);
    }

    /** Init {@link AdServicesExtDataStorageServiceManager}. */
    @NonNull
    public static AdServicesExtDataStorageServiceManager getInstance(@NonNull Context context) {
        Objects.requireNonNull(context);
        return new AdServicesExtDataStorageServiceManager(
                AdServicesExtDataStorageServiceWorker.getInstance(context));
    }

    /**
     * Fetches all AdExt data.
     *
     * @return {@link AdServicesExtDataParams} data object with the current state of AdExt data.
     */
    @NonNull
    public AdServicesExtDataParams getAdServicesExtData() {
        CountDownLatch latch = new CountDownLatch(1);

        AtomicBoolean isSuccess = new AtomicBoolean();
        AtomicInteger notifDisplayed = new AtomicInteger();
        AtomicInteger msmtConsent = new AtomicInteger();
        AtomicInteger isU18Account = new AtomicInteger();
        AtomicInteger isAdultAccount = new AtomicInteger();
        AtomicInteger manualInteractionWithConsentStatus = new AtomicInteger();
        AtomicLong apexVersion = new AtomicLong();

        mDataWorker.getAdServicesExtData(
                new AdServicesOutcomeReceiver<AdServicesExtDataParams, Exception>() {
                    @Override
                    public void onResult(AdServicesExtDataParams result) {
                        notifDisplayed.set(result.getIsNotificationDisplayed());
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
                    public void onError(@NonNull Exception e) {
                        LogUtil.e(e, "Error when getting AdExt data! Returning default values.");
                        latch.countDown();
                    }
                });

        boolean timedOut;
        AdServicesExtDataParams params;
        try {
            timedOut = !latch.await(READ_OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (timedOut) {
                LogUtil.e("Getting AdExt data timed out! Returning default values.");
                params = constructDefaultParams();
            } else {
                params =
                        isSuccess.get()
                                ? constructParams(
                                        notifDisplayed.get(),
                                        msmtConsent.get(),
                                        isU18Account.get(),
                                        isAdultAccount.get(),
                                        manualInteractionWithConsentStatus.get(),
                                        apexVersion.get())
                                : constructDefaultParams();
            }
        } catch (Exception e) {
            LogUtil.e(e, "Error when awaiting latch! Returning default values.");
            params = constructDefaultParams();
        }

        LogUtil.d("Returned AdExt data: " + params);
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
            @NonNull AdServicesExtDataParams params,
            @NonNull @AdServicesExtDataFieldId int[] fieldsToUpdate) {
        Objects.requireNonNull(params);
        Objects.requireNonNull(fieldsToUpdate);

        if (fieldsToUpdate.length == 0) {
            LogUtil.d("No AdExt data fields to update. Returning early.");
            return true;
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean isSuccess = new AtomicBoolean();
        mDataWorker.setAdServicesExtData(
                params,
                fieldsToUpdate,
                new AdServicesOutcomeReceiver<AdServicesExtDataParams, Exception>() {
                    @Override
                    public void onResult(AdServicesExtDataParams result) {
                        LogUtil.d(
                                "Updated AdExt Data: "
                                        + updateRequestToStr(params, fieldsToUpdate));
                        isSuccess.set(true);
                        latch.countDown();
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        LogUtil.e(e, "Exception when updating AdExt data!");
                        latch.countDown();
                    }
                });

        boolean timedOut;
        try {
            timedOut = !latch.await(WRITE_OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            LogUtil.e(e, "Failed to update AdExt data! Exception when awaiting latch! ");
            return false;
        }

        if (timedOut) {
            LogUtil.e("Updating AdExt data failed due to timeout!");
            return false;
        }

        return isSuccess.get();
    }

    private AdServicesExtDataParams constructParams(
            int notifDisplayed,
            int msmtConsent,
            int isU18Account,
            int isAdultAccount,
            int manualInteractionStatus,
            long msmtApex) {
        return new AdServicesExtDataParams.Builder()
                .setNotificationDisplayed(notifDisplayed)
                .setMsmtConsent(msmtConsent)
                .setIsU18Account(isU18Account)
                .setIsAdultAccount(isAdultAccount)
                .setManualInteractionWithConsentStatus(manualInteractionStatus)
                .setMsmtRollbackApexVersion(msmtApex)
                .build();
    }

    private AdServicesExtDataParams constructDefaultParams() {
        return new AdServicesExtDataParams.Builder()
                .setNotificationDisplayed(BOOLEAN_UNKNOWN)
                .setMsmtConsent(BOOLEAN_UNKNOWN)
                .setIsU18Account(BOOLEAN_UNKNOWN)
                .setIsAdultAccount(BOOLEAN_UNKNOWN)
                .setManualInteractionWithConsentStatus(STATE_UNKNOWN)
                .setMsmtRollbackApexVersion(APEX_VERSION_WHEN_NOT_FOUND)
                .build();
    }

    /** Converts AdExt data to be updated into readable string, used for debug logging. */
    @VisibleForTesting
    String updateRequestToStr(AdServicesExtDataParams params, int[] fieldsToUpdate) {
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
                    LogUtil.e("Invalid AdExt data field Id detected: " + fieldId);
                    sb.append("INVALID_FIELD_ID: ").append(fieldId);
            }
            sb.append(",");
        }
        sb.append("}");
        return sb.toString();
    }
}
