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

import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.signals.ProtectedSignalsDao;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.Objects;

/** Shared logic for resetting AdServices databases to factory-new states. */
public final class DatabaseRefresher {

    private final CustomAudienceDao mCustomAudienceDao;
    private final AppInstallDao mAppInstallDao;
    private final ProtectedSignalsDao mProtectedSignalsDao;
    private final FrequencyCapDao mFrequencyCapDao;
    private final ListeningExecutorService mBackgroundExecutor;

    public DatabaseRefresher(
            CustomAudienceDao customAudienceDao,
            AppInstallDao appInstallDao,
            FrequencyCapDao frequencyCapDao,
            ProtectedSignalsDao protectedSignalsDao,
            ListeningExecutorService backgroundExecutor) {
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(appInstallDao);
        Objects.requireNonNull(frequencyCapDao);
        Objects.requireNonNull(protectedSignalsDao);
        Objects.requireNonNull(backgroundExecutor);

        mCustomAudienceDao = customAudienceDao;
        mAppInstallDao = appInstallDao;
        mFrequencyCapDao = frequencyCapDao;
        mProtectedSignalsDao = protectedSignalsDao;
        mBackgroundExecutor = backgroundExecutor;
    }

    /**
     * Reset ad services data to a fresh state. This does not include consent data.
     *
     * @param scheduleCustomAudienceUpdateEnabled If the schedule custom audience update feature is
     *     enabled and to erase that data from the custom audience DAO.
     * @param frequencyCapFilteringEnabled If frequency cap filtering is enabled, and to erase the
     *     frequency capping data if true.
     * @param appInstallFilteringEnabled If app install filtering is enabled, and to erase app
     *     install data if true.
     * @param protectedSignalsCleanupEnabled If true, erase protected signals data.
     * @return A future indicating completion of all DAO operations. If any of the DAO operations
     *     fail, the future will fail with the exception from the first failing DAO operation.
     */
    public ListenableFuture<Void> deleteAllProtectedAudienceAndAppSignalsData(
            boolean scheduleCustomAudienceUpdateEnabled,
            boolean frequencyCapFilteringEnabled,
            boolean appInstallFilteringEnabled,
            boolean protectedSignalsCleanupEnabled) {
        return mBackgroundExecutor.submit(
                () -> {
                    mCustomAudienceDao.deleteAllCustomAudienceData(
                            scheduleCustomAudienceUpdateEnabled);
                    if (frequencyCapFilteringEnabled) {
                        mFrequencyCapDao.deleteAllHistogramData();
                    }
                    if (appInstallFilteringEnabled) {
                        mAppInstallDao.deleteAllAppInstallData();
                    }
                    if (protectedSignalsCleanupEnabled) {
                        mProtectedSignalsDao.deleteAllSignals();
                    }
                    return null;
                });
    }
}
