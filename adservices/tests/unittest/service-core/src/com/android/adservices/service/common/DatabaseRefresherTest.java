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

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.signals.ProtectedSignalsDao;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public final class DatabaseRefresherTest extends AdServicesMockitoTestCase {

    @Mock private CustomAudienceDao mCustomAudienceDao;
    @Mock private FrequencyCapDao mFrequencyCapDao;
    @Mock private AppInstallDao mAppInstallDao;
    @Mock private ProtectedSignalsDao mProtectedSignalsDao;
    private DatabaseRefresher mDatabaseRefresher;

    @Before
    public void setUp() {
        ListeningExecutorService backgroundExecutor =
                MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
        mDatabaseRefresher =
                new DatabaseRefresher(
                        mCustomAudienceDao,
                        mAppInstallDao,
                        mFrequencyCapDao,
                        mProtectedSignalsDao,
                        backgroundExecutor);
    }

    @Test
    public void testDeleteAllProtectedAudienceAndAppSignals_Data_success() throws Exception {
        ListenableFuture<Void> future =
                mDatabaseRefresher.deleteAllProtectedAudienceAndAppSignalsData(
                        /* scheduleCustomAudienceUpdateEnabled= */ true,
                        /* frequencyCapFilteringEnabled= */ true,
                        /* appInstallFilteringEnabled= */ true,
                        /* protectedSignalsCleanupEnabled= */ true);
        future.get(); // Wait for the future to complete

        verify(mCustomAudienceDao, times(1)).deleteAllCustomAudienceData(true);
        verify(mFrequencyCapDao, times(1)).deleteAllHistogramData();
        verify(mAppInstallDao, times(1)).deleteAllAppInstallData();
        verify(mProtectedSignalsDao, times(1)).deleteAllSignals();
    }

    @Test
    public void testDeleteAllProtectedAudienceAndAppSignals_customAudienceDaoFailsData() {
        doThrow(new RuntimeException("Custom Audience DAO failed"))
                .when(mCustomAudienceDao)
                .deleteAllCustomAudienceData(true);

        assertThrows(
                ExecutionException.class,
                () -> {
                    ListenableFuture<Void> future =
                            mDatabaseRefresher.deleteAllProtectedAudienceAndAppSignalsData(
                                    /* scheduleCustomAudienceUpdateEnabled= */ true,
                                    /* frequencyCapFilteringEnabled= */ true,
                                    /* appInstallFilteringEnabled= */ true,
                                    /* protectedSignalsCleanupEnabled= */ true);
                    future.get(); // Wait for the future to complete
                });
    }

    @Test
    public void testDeleteAllProtectedAudienceAndAppSignals_Data_frequencyCapDaoFails() {
        doThrow(new RuntimeException("Frequency Cap DAO failed"))
                .when(mFrequencyCapDao)
                .deleteAllHistogramData();

        assertThrows(
                ExecutionException.class,
                () -> {
                    ListenableFuture<Void> future =
                            mDatabaseRefresher.deleteAllProtectedAudienceAndAppSignalsData(
                                    /* scheduleCustomAudienceUpdateEnabled= */ true,
                                    /* frequencyCapFilteringEnabled= */ true,
                                    /* appInstallFilteringEnabled= */ true,
                                    /* protectedSignalsCleanupEnabled= */ true);
                    future.get();
                });
    }

    @Test
    public void testDeleteAllProtectedAudienceAndAppSignals_appInstallDaoFailsData() {
        doThrow(new RuntimeException("App Install DAO failed"))
                .when(mAppInstallDao)
                .deleteAllAppInstallData();

        assertThrows(
                ExecutionException.class,
                () -> {
                    ListenableFuture<Void> future =
                            mDatabaseRefresher.deleteAllProtectedAudienceAndAppSignalsData(
                                    /* scheduleCustomAudienceUpdateEnabled= */ true,
                                    /* frequencyCapFilteringEnabled= */ true,
                                    /* appInstallFilteringEnabled= */ true,
                                    /* protectedSignalsCleanupEnabled= */ true);
                    future.get();
                });
    }

    @Test
    public void testDeleteAllProtectedAudienceAndAppSignals_protectedSignalsDataDaoFails() {
        doThrow(new RuntimeException("Protected Signals DAO failed"))
                .when(mProtectedSignalsDao)
                .deleteAllSignals();

        assertThrows(
                ExecutionException.class,
                () -> {
                    ListenableFuture<Void> future =
                            mDatabaseRefresher.deleteAllProtectedAudienceAndAppSignalsData(
                                    /* scheduleCustomAudienceUpdateEnabled= */ true,
                                    /* frequencyCapFilteringEnabled= */ true,
                                    /* appInstallFilteringEnabled= */ true,
                                    /* protectedSignalsCleanupEnabled= */ true);
                    future.get();
                });
    }

    @Test
    public void testDeleteAllProtectedAudienceAndAppSignals_Data_allDaosFail() {
        doThrow(new RuntimeException("Custom Audience DAO failed"))
                .when(mCustomAudienceDao)
                .deleteAllCustomAudienceData(true);
        doThrow(new RuntimeException("Frequency Cap DAO failed"))
                .when(mFrequencyCapDao)
                .deleteAllHistogramData();
        doThrow(new RuntimeException("App Install DAO failed"))
                .when(mAppInstallDao)
                .deleteAllAppInstallData();
        doThrow(new RuntimeException("Protected Signals DAO failed"))
                .when(mProtectedSignalsDao)
                .deleteAllSignals();

        assertThrows(
                ExecutionException.class,
                () -> {
                    ListenableFuture<Void> future =
                            mDatabaseRefresher.deleteAllProtectedAudienceAndAppSignalsData(
                                    /* scheduleCustomAudienceUpdateEnabled= */ true,
                                    /* frequencyCapFilteringEnabled= */ true,
                                    /* appInstallFilteringEnabled= */ true,
                                    /* protectedSignalsCleanupEnabled= */ true);
                    future.get();
                });
    }
}
