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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.adselection.ProtectedServersEncryptionConfigDao;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.adselection.AdFilteringFeatureFactory;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public final class DatabaseClearerTest extends AdServicesMockitoTestCase {

    @Mock private CustomAudienceDao mCustomAudienceDao;
    @Mock private FrequencyCapDao mFrequencyCapDao;
    @Mock private AppInstallDao mAppInstallDao;
    @Mock private EncodedPayloadDao mEncodedPayloadDao;
    @Mock private ProtectedSignalsDao mProtectedSignalsDao;
    @Mock private DatastoreManager mDatastoreManager;
    @Mock private ProtectedServersEncryptionConfigDao mProtectedServersEncryptionConfigDao;
    private DatabaseClearer mDatabaseClearer;

    @Before
    public void setUp() {
        ListeningExecutorService backgroundExecutor =
                MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
        mDatabaseClearer =
                new DatabaseClearer(
                        mCustomAudienceDao,
                        mAppInstallDao,
                        new AdFilteringFeatureFactory(
                                        mAppInstallDao,
                                        mFrequencyCapDao,
                                        new Flags() {
                                            @Override
                                            public boolean getFledgeFrequencyCapFilteringEnabled() {
                                                return true;
                                            }
                                        })
                                .getFrequencyCapDataClearer(),
                        mProtectedSignalsDao,
                        mEncodedPayloadDao,
                        mDatastoreManager,
                        mProtectedServersEncryptionConfigDao,
                        backgroundExecutor);
    }

    @Test
    public void testDeleteProtectedAudienceAppSignalsAndEncryptionConfigData_success()
            throws Exception {
        ListenableFuture<Void> future =
                mDatabaseClearer.deleteProtectedAudienceAppSignalsAndEncryptionConfigData(
                        /* deleteCustomAudienceUpdate= */ true,
                        /* deleteAppInstallFiltering= */ true,
                        /* deleteProtectedSignals= */ true,
                        /* deleteEncryptionConfigData= */ true);

        future.get(); // Wait for the future to complete

        verify(mCustomAudienceDao, times(1)).deleteAllCustomAudienceData(true);
        verify(mFrequencyCapDao, times(1)).deleteAllHistogramData();
        verify(mAppInstallDao, times(1)).deleteAllAppInstallData();
        verify(mProtectedSignalsDao, times(1)).deleteAllSignals();
        verify(mEncodedPayloadDao, times(1)).deleteAllEncodedPayloads();
        verify(mProtectedServersEncryptionConfigDao, times(1)).deleteAllEncryptionKeys();
    }

    @Test
    public void
            testDeleteProtectedAudienceAppSignalsAndEncryptionConfigData_customAudienceDaoFails() {
        doThrow(new RuntimeException("Custom Audience DAO failed"))
                .when(mCustomAudienceDao)
                .deleteAllCustomAudienceData(true);

        ListenableFuture<Void> future =
                mDatabaseClearer.deleteProtectedAudienceAppSignalsAndEncryptionConfigData(
                        /* deleteCustomAudienceUpdate= */ true,
                        /* deleteAppInstallFiltering= */ true,
                        /* deleteProtectedSignals= */ true,
                        /* deleteEncryptionConfigData= */ true);

        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    public void
            testDeleteProtectedAudienceAppSignalsAndEncryptionConfigData_frequencyCapDaoFails() {
        doThrow(new RuntimeException("Frequency Cap DAO failed"))
                .when(mFrequencyCapDao)
                .deleteAllHistogramData();

        ListenableFuture<Void> future =
                mDatabaseClearer.deleteProtectedAudienceAppSignalsAndEncryptionConfigData(
                        /* deleteCustomAudienceUpdate= */ true,
                        /* deleteAppInstallFiltering= */ true,
                        /* deleteProtectedSignals= */ true,
                        /* deleteEncryptionConfigData= */ true);

        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    public void testDeleteProtectedAudienceAppSignalsAndEncryptionConfigData_appInstallDaoFails() {
        doThrow(new RuntimeException("App Install DAO failed"))
                .when(mAppInstallDao)
                .deleteAllAppInstallData();

        ListenableFuture<Void> future =
                mDatabaseClearer.deleteProtectedAudienceAppSignalsAndEncryptionConfigData(
                        /* deleteCustomAudienceUpdate= */ true,
                        /* deleteAppInstallFiltering= */ true,
                        /* deleteProtectedSignals= */ true,
                        /* deleteEncryptionConfigData= */ true);

        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    public void
            testDeleteProtectedAudienceAppSignalsAndEncryptionConfigData_protectedSignalsDaoFails() {
        doThrow(new RuntimeException("Protected Signals DAO failed"))
                .when(mProtectedSignalsDao)
                .deleteAllSignals();

        ListenableFuture<Void> future =
                mDatabaseClearer.deleteProtectedAudienceAppSignalsAndEncryptionConfigData(
                        /* deleteCustomAudienceUpdate= */ true,
                        /* deleteAppInstallFiltering= */ true,
                        /* deleteProtectedSignals= */ true,
                        /* deleteEncryptionConfigData= */ true);

        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    public void testDeleteProtectedAudienceAppSignalsAndEncryptionConfigData_encryptionDaoFails() {
        doThrow(new RuntimeException("Encryption Config DAO failed"))
                .when(mProtectedServersEncryptionConfigDao)
                .deleteAllEncryptionKeys();

        ListenableFuture<Void> future =
                mDatabaseClearer.deleteProtectedAudienceAppSignalsAndEncryptionConfigData(
                        /* deleteCustomAudienceUpdate= */ true,
                        /* deleteAppInstallFiltering= */ true,
                        /* deleteProtectedSignals= */ true,
                        /* deleteEncryptionConfigData= */ true);

        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    public void testDeleteProtectedAudienceAppSignalsAndEncryptionConfigData_allDaosFail() {
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
        doThrow(new RuntimeException("Encryption Config DAO failed"))
                .when(mProtectedServersEncryptionConfigDao)
                .deleteAllEncryptionKeys();

        ListenableFuture<Void> future =
                mDatabaseClearer.deleteProtectedAudienceAppSignalsAndEncryptionConfigData(
                        /* deleteCustomAudienceUpdate= */ true,
                        /* deleteAppInstallFiltering= */ true,
                        /* deleteProtectedSignals= */ true,
                        /* deleteEncryptionConfigData= */ true);

        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    public void testDeleteMeasurementData_success() throws Exception {
        when(mDatastoreManager.runInTransaction(any())).thenReturn(true);

        ListenableFuture<Void> future = mDatabaseClearer.deleteMeasurementData();
        future.get();

        verify(mDatastoreManager, times(1)).runInTransaction(any());
    }

    @Test
    public void testDeleteMeasurementData_fail() {
        doThrow(new RuntimeException("MeasurementImpl delete measurement data failed"))
                .when(mDatastoreManager)
                .runInTransaction(any());

        ListenableFuture<Void> future = mDatabaseClearer.deleteMeasurementData();

        assertThrows(ExecutionException.class, future::get);
    }
}
