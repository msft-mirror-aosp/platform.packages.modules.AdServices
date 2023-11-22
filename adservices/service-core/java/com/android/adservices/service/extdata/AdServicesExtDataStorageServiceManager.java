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

import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_FALSE;
import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_TRUE;
import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_UNKNOWN;
import static android.adservices.extdata.AdServicesExtDataParams.STATE_UNKNOWN;
import static android.adservices.extdata.AdServicesExtDataParams.UserManualInteraction;
import static android.adservices.extdata.AdServicesExtDataStorageService.AdServicesExtDataFieldId;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_ADULT_ACCOUNT;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_MEASUREMENT_CONSENTED;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_NOTIFICATION_DISPLAYED;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_U18_ACCOUNT;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_MANUAL_INTERACTION_WITH_CONSENT_STATUS;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_MEASUREMENT_ROLLBACK_APEX_VERSION;

import static com.android.adservices.service.measurement.rollback.MeasurementRollbackCompatManager.APEX_VERSION_WHEN_NOT_FOUND;

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
public final class AdServicesExtDataStorageServiceManager {
    // Conservative timeouts based on what's used for AppSearch operations (500 ms for reads,
    // 2000 ms writes). An additional 100 ms buffer is added for delays such as binder latency.
    private static final long READ_OPERATION_TIMEOUT_MS = 600L;
    private static final long WRITE_OPERATION_TIMEOUT_MS = 2100L;

    private static final AdServicesExtDataParams DEFAULT_PARAMS =
            new AdServicesExtDataParams.Builder()
                    .setNotificationDisplayed(BOOLEAN_UNKNOWN)
                    .setMsmtConsent(BOOLEAN_UNKNOWN)
                    .setIsU18Account(BOOLEAN_UNKNOWN)
                    .setIsAdultAccount(BOOLEAN_UNKNOWN)
                    .setManualInteractionWithConsentStatus(STATE_UNKNOWN)
                    .setMsmtRollbackApexVersion(APEX_VERSION_WHEN_NOT_FOUND)
                    .build();

    private static final int[] ALL_FIELDS =
            new int[] {
                FIELD_IS_NOTIFICATION_DISPLAYED,
                FIELD_IS_MEASUREMENT_CONSENTED,
                FIELD_IS_U18_ACCOUNT,
                FIELD_IS_ADULT_ACCOUNT,
                FIELD_MANUAL_INTERACTION_WITH_CONSENT_STATUS,
                FIELD_MEASUREMENT_ROLLBACK_APEX_VERSION
            };

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
                params = DEFAULT_PARAMS;
            } else {
                params =
                        isSuccess.get()
                                ? constructParams(
                                        notificationDisplayed.get(),
                                        msmtConsent.get(),
                                        isU18Account.get(),
                                        isAdultAccount.get(),
                                        manualInteractionWithConsentStatus.get(),
                                        apexVersion.get())
                                : DEFAULT_PARAMS;
            }
        } catch (Exception e) {
            LogUtil.e(e, "Error when awaiting latch! Returning default values.");
            params = DEFAULT_PARAMS;
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

    /**
     * Sets Measurement API consent.
     *
     * @return true if update was successful; false otherwise.
     */
    public boolean setMsmtConsent(boolean isGiven) {
        return setAdServicesExtData(
                new AdServicesExtDataParams.Builder()
                        .setMsmtConsent(isGiven ? BOOLEAN_TRUE : BOOLEAN_FALSE)
                        .build(),
                new int[] {FIELD_IS_MEASUREMENT_CONSENTED});
    }

    /**
     * Retrieves the stored consent bit for Measurement API, converted into boolean. -1 (no data) is
     * considered false.
     */
    public boolean getMsmtConsent() {
        return getAdServicesExtData().getIsMeasurementConsented() == BOOLEAN_TRUE;
    }

    /**
     * Sets manual interaction with consent status.
     *
     * @return true if update was successful; false otherwise.
     */
    public boolean setManualInteractionWithConsentStatus(
            @UserManualInteraction int manualInteractionWithConsentStatus) {
        return setAdServicesExtData(
                new AdServicesExtDataParams.Builder()
                        .setManualInteractionWithConsentStatus(manualInteractionWithConsentStatus)
                        .build(),
                new int[] {FIELD_MANUAL_INTERACTION_WITH_CONSENT_STATUS});
    }

    /** Retrieves stored value for manual interaction with consent status. */
    @UserManualInteraction
    public int getManualInteractionWithConsentStatus() {
        return getAdServicesExtData().getManualInteractionWithConsentStatus();
    }

    /**
     * Sets if notification was displayed.
     *
     * @return true if update was successful; false otherwise.
     */
    public boolean setNotificationDisplayed(boolean isGiven) {
        return setAdServicesExtData(
                new AdServicesExtDataParams.Builder()
                        .setNotificationDisplayed(isGiven ? BOOLEAN_TRUE : BOOLEAN_FALSE)
                        .build(),
                new int[] {FIELD_IS_NOTIFICATION_DISPLAYED});
    }

    /**
     * Retrieves the stored consent bit for notification displayed, converted into boolean. -1 (no
     * data) is considered false.
     */
    public boolean getNotificationDisplayed() {
        return getAdServicesExtData().getIsNotificationDisplayed() == BOOLEAN_TRUE;
    }

    /**
     * Sets if account is U18
     *
     * @return true if update was successful; false otherwise.
     */
    public boolean setIsU18Account(boolean isU18Account) {
        return setAdServicesExtData(
                new AdServicesExtDataParams.Builder()
                        .setIsU18Account(isU18Account ? BOOLEAN_TRUE : BOOLEAN_FALSE)
                        .build(),
                new int[] {FIELD_IS_U18_ACCOUNT});
    }

    /** Retrieves if account is U18, converted into boolean. -1 (no data) is considered false. */
    public boolean getIsU18Account() {
        return getAdServicesExtData().getIsU18Account() == BOOLEAN_TRUE;
    }

    /**
     * Sets if adult account.
     *
     * @return true if update was successful; false otherwise.
     */
    public boolean setIsAdultAccount(boolean isAdultAccount) {
        return setAdServicesExtData(
                new AdServicesExtDataParams.Builder()
                        .setIsAdultAccount(isAdultAccount ? BOOLEAN_TRUE : BOOLEAN_FALSE)
                        .build(),
                new int[] {FIELD_IS_ADULT_ACCOUNT});
    }

    /** Retrieves if adult account, converted into boolean. -1 (no data) is considered false. */
    public boolean getIsAdultAccount() {
        return getAdServicesExtData().getIsAdultAccount() == BOOLEAN_TRUE;
    }

    /**
     * Sets the apex version when a measurement deletion event occurred, for rollback purposes
     *
     * @param apexVersion the version of the ExtServices apex that was running
     * @return {@code true} if successful, {@code false} otherwise.
     */
    public boolean setMeasurementRollbackApexVersion(long apexVersion) {
        return setAdServicesExtData(
                new AdServicesExtDataParams.Builder()
                        .setMsmtRollbackApexVersion(apexVersion)
                        .build(),
                new int[] {FIELD_MEASUREMENT_ROLLBACK_APEX_VERSION});
    }

    /**
     * Returns the saved ExtServices apex version that had a measurement deletion event, if any
     *
     * @return the saved apex version if present, -1 otherwise.
     */
    public long getMeasurementRollbackApexVersion() {
        return getAdServicesExtData().getMeasurementRollbackApexVersion();
    }

    /**
     * Sets all AdExt data values to their respective default values to indicate data clearance in a
     * non-blocking manner.
     */
    public void clearAllDataAsync() {
        mDataWorker.setAdServicesExtData(
                DEFAULT_PARAMS,
                ALL_FIELDS,
                new AdServicesOutcomeReceiver<>() {
                    @Override
                    public void onResult(AdServicesExtDataParams result) {
                        LogUtil.d("Cleared AdExt Data.");
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        // TODO (b/301163895) Add logging to capture deletion failure.
                        LogUtil.e(e, "Exception when clearing AdExt data!");
                    }
                });
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
