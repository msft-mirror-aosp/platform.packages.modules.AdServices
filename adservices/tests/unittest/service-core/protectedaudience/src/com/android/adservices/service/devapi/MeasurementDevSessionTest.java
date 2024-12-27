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

package com.android.adservices.service.devapi;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import androidx.room.Room;

import com.android.adservices.LoggerFactory;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.MeasurementDbHelper;
import com.android.adservices.data.measurement.SQLDatastoreManager;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.testutils.DevSessionHelper;
import com.android.modules.utils.testing.ExtendedMockitoRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@ExtendedMockitoRule.SpyStatic(FlagsFactory.class)
public class MeasurementDevSessionTest extends AdServicesExtendedMockitoTestCase {
    private DevSessionHelper mDevSessionHelper;
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getMeasurementLogger();
    private DatastoreManager mMeasurementDatastoreManager;
    private CustomAudienceDao mCustomAudienceDao;
    private AppInstallDao mAppInstallDao;
    private FrequencyCapDao mFrequencyCapDao;

    @Before
    public void setup() {
        doReturn(new MeasurementServiceE2ETestFlags()).when(FlagsFactory::getFlags);

        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(mContext, CustomAudienceDatabase.class)
                        .addTypeConverter(new DBCustomAudience.Converters(true, true, true))
                        .build()
                        .customAudienceDao();

        SharedStorageDatabase sharedDb =
                Room.inMemoryDatabaseBuilder(mContext, SharedStorageDatabase.class).build();
        mAppInstallDao = sharedDb.appInstallDao();
        mFrequencyCapDao = sharedDb.frequencyCapDao();

        ProtectedSignalsDao protectedSignalsDao =
                Room.inMemoryDatabaseBuilder(mContext, ProtectedSignalsDatabase.class)
                        .build()
                        .protectedSignalsDao();

        mMeasurementDatastoreManager =
                new SQLDatastoreManager(
                        MeasurementDbHelper.getInstance(),
                        Mockito.mock(AdServicesErrorLogger.class));

        mDevSessionHelper =
                new DevSessionHelper(
                        mCustomAudienceDao,
                        mAppInstallDao,
                        mFrequencyCapDao,
                        protectedSignalsDao,
                        mMeasurementDatastoreManager);
    }

    @After
    public void tearDown() throws Exception {
        mDevSessionHelper.endDevSession();
    }

    @Test
    public void testStartDevSession_thenInsertSource_concurrentThreads()
            throws InterruptedException {
        Source validSource = SourceFixture.getValidSourceBuilder().setId("1").build();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch devSessionStartedLatch = new CountDownLatch(1);

        executor.execute(
                () -> {
                    mDevSessionHelper.startDevSession();
                    devSessionStartedLatch.countDown();
                });
        executor.execute(
                () -> {
                    try {
                        devSessionStartedLatch.await();
                        mMeasurementDatastoreManager.runInTransaction(
                                (dao) -> {
                                    dao.insertSource(validSource);
                                });
                    } catch (InterruptedException e) {
                        fail("Execution error: " + e.getMessage());
                    }
                });
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        mMeasurementDatastoreManager.runInTransaction(
                (dao) -> {
                    Source s = dao.getSource(validSource.getId());
                    assertNotNull(s);
                });
        mMeasurementDatastoreManager.runInTransaction(
                (dao) -> {
                    dao.deleteSources(List.of("1"));

                    assertThrows(
                            DatastoreException.class,
                            () -> {
                                dao.getSource("1");
                            });
                });
    }

    @Test
    public void testStartDevSession_duringMeasurementTransaction_concurrentThreads()
            throws InterruptedException {
        Source validSource = SourceFixture.getValidSourceBuilder().setId("1").build();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch measurementTransactionStartedLatch = new CountDownLatch(1);
        AtomicReference<String> insertedSourceId = new AtomicReference<>();

        executor.execute(
                () -> {
                    mMeasurementDatastoreManager.runInTransaction(
                            (dao) -> {
                                sLogger.i("Starting insertSource() Measurement Transaction");
                                measurementTransactionStartedLatch.countDown();
                                try {
                                    // Added sleep to ensure that startDevSession() is being called
                                    // while this measurement transaction is running
                                    Thread.sleep(2000);
                                } catch (InterruptedException e) {
                                    fail("Execution error: " + e.getMessage());
                                }
                                String sourceId = dao.insertSource(validSource);
                                assertNotNull(sourceId);
                                insertedSourceId.set(sourceId);
                                sLogger.i(
                                        "Completed insertSource transaction with sourceId:" + " %s",
                                        sourceId);
                            });
                });
        executor.execute(
                () -> {
                    try {
                        sLogger.i("Waiting for insertSource() to start");
                        measurementTransactionStartedLatch.await();
                        sLogger.i("Starting startDevSession()");
                        mDevSessionHelper.startDevSession();
                        sLogger.i("Ended startDevSession()");
                    } catch (InterruptedException e) {
                        fail("Execution error: " + e.getMessage());
                    }
                });
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        mMeasurementDatastoreManager.runInTransaction(
                (dao) -> {
                    assertThrows(
                            DatastoreException.class, () -> dao.getSource(insertedSourceId.get()));
                });
    }

    private static class MeasurementServiceE2ETestFlags implements Flags {
        @Override
        public boolean getEnableDatabaseSchemaVersion8() {
            return true;
        }
    }
}
