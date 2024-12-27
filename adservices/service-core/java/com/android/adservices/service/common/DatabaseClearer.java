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

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.service.adselection.FrequencyCapDataClearer;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.Collections;
import java.util.Objects;

/** Shared logic for clearing AdServices databases to factory-new states. */
public final class DatabaseClearer {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();

    private final CustomAudienceDao mCustomAudienceDao;
    private final AppInstallDao mAppInstallDao;
    private final ProtectedSignalsDao mProtectedSignalsDao;
    private final FrequencyCapDataClearer mFrequencyCapDataClearer;
    private final DatastoreManager mDatastoreManager;
    private final ListeningExecutorService mBackgroundExecutor;

    public DatabaseClearer(
            CustomAudienceDao customAudienceDao,
            AppInstallDao appInstallDao,
            FrequencyCapDataClearer frequencyCapDataClearer,
            ProtectedSignalsDao protectedSignalsDao,
            DatastoreManager datastoreManager,
            ListeningExecutorService backgroundExecutor) {
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(appInstallDao);
        Objects.requireNonNull(protectedSignalsDao);
        Objects.requireNonNull(backgroundExecutor);

        mCustomAudienceDao = customAudienceDao;
        mAppInstallDao = appInstallDao;
        mProtectedSignalsDao = protectedSignalsDao;
        mFrequencyCapDataClearer = frequencyCapDataClearer;
        mDatastoreManager = datastoreManager;
        mBackgroundExecutor = backgroundExecutor;
    }

    /**
     * Clear Protected Audience and Protected App Signals services data to an empty state. This does
     * not include consent data. For that logic, see {@link
     * com.android.adservices.service.consent.ConsentManager}.
     *
     * @param deleteCustomAudienceUpdate If true, erase custom audience data.
     * @param deleteAppInstallFiltering If true, erase app install data.
     * @param deleteProtectedSignals If true, erase protected signals data.
     * @return A future indicating completion of all DAO operations. If any of the DAO operations
     *     fail, the future will fail with the exception from the first failing DAO operation.
     */
    public ListenableFuture<Boolean> deleteProtectedAudienceAndAppSignalsData(
            boolean deleteCustomAudienceUpdate,
            boolean deleteAppInstallFiltering,
            boolean deleteProtectedSignals) {
        return mBackgroundExecutor.submit(
                () -> {
                    sLogger.v(
                            "DatabaseClearer: Beginning Protected Audience and App Signals"
                                    + " database clearing");
                    mCustomAudienceDao.deleteAllCustomAudienceData(deleteCustomAudienceUpdate);
                    int numClearedEvents = mFrequencyCapDataClearer.clear();
                    sLogger.v("DatabaseClearer: Cleared %d frequency cap events", numClearedEvents);
                    if (deleteAppInstallFiltering) {
                        mAppInstallDao.deleteAllAppInstallData();
                    }
                    if (deleteProtectedSignals) {
                        mProtectedSignalsDao.deleteAllSignals();
                    }
                    sLogger.v(
                            "DatabaseClearer: Completed Protected Audience and App Signals"
                                    + " DB clear operation");
                    return true;
                });
    }

    /**
     * Clear Measurement data to an empty state. This does not include consent data. For that logic,
     * see {@link com.android.adservices.service.consent.ConsentManager}.
     *
     * @return A future indicating completion of DAO operation.
     */
    public ListenableFuture<Void> deleteMeasurementData() {
        return mBackgroundExecutor.submit(
                () -> {
                    sLogger.v("DatabaseClearer: Beginning Measurement database clearing");
                    mDatastoreManager.runInTransaction(
                            (dao) -> dao.deleteAllMeasurementData(Collections.emptyList()));
                    sLogger.v("DatabaseClearer: Completed Measurement DB clear operation");
                    return null;
                });
    }
}
