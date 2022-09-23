/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.customaudience;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.adservices.common.CommonFixture;
import android.annotation.NonNull;
import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.LogUtil;
import com.android.adservices.customaudience.DBCustomAudienceBackgroundFetchDataFixture;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudienceBackgroundFetchData;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class BackgroundFetchWorkerTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    private final Flags mFlags =
            new Flags() {
                @Override
                public int getFledgeBackgroundFetchThreadPoolSize() {
                    return 4;
                }
            };
    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(8);

    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Spy
    private CustomAudienceDao mCustomAudienceDaoSpy =
            Room.inMemoryDatabaseBuilder(CONTEXT, CustomAudienceDatabase.class)
                    .build()
                    .customAudienceDao();

    @Spy
    private BackgroundFetchRunner mBackgroundFetchRunnerSpy =
            new BackgroundFetchRunner(mCustomAudienceDaoSpy, mFlags);

    private BackgroundFetchWorker mBackgroundFetchWorker;

    @Before
    public void setup() {
        mBackgroundFetchWorker =
                new BackgroundFetchWorker(mCustomAudienceDaoSpy, mFlags, mBackgroundFetchRunnerSpy);
    }

    @Test
    public void testBackgroundFetchWorkerNullInputsCauseFailure() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new BackgroundFetchWorker(
                            null, FlagsFactory.getFlagsForTest(), mBackgroundFetchRunnerSpy);
                });

        assertThrows(
                NullPointerException.class,
                () -> {
                    new BackgroundFetchWorker(
                            mCustomAudienceDaoSpy, null, mBackgroundFetchRunnerSpy);
                });

        assertThrows(
                NullPointerException.class,
                () -> {
                    new BackgroundFetchWorker(
                            mCustomAudienceDaoSpy, FlagsFactory.getFlagsForTest(), null);
                });
    }

    @Test
    public void testRunBackgroundFetchNullInputThrows() {
        assertThrows(
                NullPointerException.class, () -> mBackgroundFetchWorker.runBackgroundFetch(null));
    }

    @Test
    public void testRunBackgroundFetchThrowsTimeout() {
        // Time out before the job even started
        Instant jobStartTime = CommonFixture.FIXED_NOW.minusMillis(24L * 60L * 60L * 1000L);
        assertThrows(
                TimeoutException.class,
                () -> mBackgroundFetchWorker.runBackgroundFetch(jobStartTime));

        verify(mBackgroundFetchRunnerSpy, never()).updateCustomAudience(any(), any());
    }

    @Test
    public void testRunBackgroundFetchThrowsTimeoutDuringUpdates() {
        class FlagsWithSmallTimeout implements Flags {
            @Override
            public long getFledgeBackgroundFetchJobMaxRuntimeMs() {
                return 100L;
            }
        }

        class BackgroundFetchRunnerWithSleep extends BackgroundFetchRunner {
            BackgroundFetchRunnerWithSleep(
                    @NonNull CustomAudienceDao customAudienceDao, @NonNull Flags flags) {
                super(customAudienceDao, flags);
            }

            @Override
            public void deleteExpiredCustomAudiences(@NonNull Instant jobStartTime) {
                // Do nothing
            }

            @Override
            public void updateCustomAudience(
                    @NonNull Instant jobStartTime,
                    @NonNull DBCustomAudienceBackgroundFetchData fetchData) {
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        Flags flagsWithSmallTimeout = new FlagsWithSmallTimeout();
        BackgroundFetchRunner backgroundFetchRunnerWithSleep =
                new BackgroundFetchRunnerWithSleep(mCustomAudienceDaoSpy, flagsWithSmallTimeout);
        BackgroundFetchWorker backgroundFetchWorkerThatTimesOut =
                new BackgroundFetchWorker(
                        mCustomAudienceDaoSpy,
                        flagsWithSmallTimeout,
                        backgroundFetchRunnerWithSleep);

        // Mock a custom audience eligible for update
        DBCustomAudienceBackgroundFetchData fetchData =
                DBCustomAudienceBackgroundFetchDataFixture.getValidBuilderByBuyer(
                                CommonFixture.VALID_BUYER_1)
                        .setEligibleUpdateTime(CommonFixture.FIXED_NOW)
                        .build();
        doReturn(Arrays.asList(fetchData))
                .when(mCustomAudienceDaoSpy)
                .getActiveEligibleCustomAudienceBackgroundFetchData(any(), anyLong());

        // Time out while updating custom audiences
        assertThrows(
                TimeoutException.class,
                () -> backgroundFetchWorkerThatTimesOut.runBackgroundFetch(Instant.now()));
    }

    @Test
    public void testRunBackgroundFetchNothingToUpdate()
            throws ExecutionException, InterruptedException, TimeoutException {
        assertTrue(
                mCustomAudienceDaoSpy
                        .getActiveEligibleCustomAudienceBackgroundFetchData(
                                CommonFixture.FIXED_NOW, 1)
                        .isEmpty());

        mBackgroundFetchWorker.runBackgroundFetch(CommonFixture.FIXED_NOW);

        verify(mBackgroundFetchRunnerSpy, never()).updateCustomAudience(any(), any());
    }

    @Test
    public void testRunBackgroundFetchUpdateOneCustomAudience()
            throws ExecutionException, InterruptedException, TimeoutException {
        // Mock a single custom audience eligible for update
        DBCustomAudienceBackgroundFetchData fetchData =
                DBCustomAudienceBackgroundFetchDataFixture.getValidBuilderByBuyer(
                                CommonFixture.VALID_BUYER_1)
                        .setEligibleUpdateTime(CommonFixture.FIXED_NOW)
                        .build();
        doReturn(Arrays.asList(fetchData))
                .when(mCustomAudienceDaoSpy)
                .getActiveEligibleCustomAudienceBackgroundFetchData(any(), anyLong());
        doNothing().when(mBackgroundFetchRunnerSpy).updateCustomAudience(any(), any());

        mBackgroundFetchWorker.runBackgroundFetch(CommonFixture.FIXED_NOW);

        verify(mBackgroundFetchRunnerSpy).updateCustomAudience(any(), any());
    }

    @Test
    public void testRunBackgroundFetchUpdateCustomAudiences()
            throws ExecutionException, InterruptedException, TimeoutException {
        int numEligibleCustomAudiences = 12;

        // Mock a list of custom audiences eligible for update
        DBCustomAudienceBackgroundFetchData.Builder fetchDataBuilder =
                DBCustomAudienceBackgroundFetchDataFixture.getValidBuilderByBuyer(
                                CommonFixture.VALID_BUYER_1)
                        .setEligibleUpdateTime(CommonFixture.FIXED_NOW);
        List<DBCustomAudienceBackgroundFetchData> fetchDataList = new ArrayList<>();
        for (int i = 0; i < numEligibleCustomAudiences; i++) {
            fetchDataList.add(fetchDataBuilder.setName("ca" + i).build());
        }

        doReturn(fetchDataList)
                .when(mCustomAudienceDaoSpy)
                .getActiveEligibleCustomAudienceBackgroundFetchData(any(), anyLong());
        doNothing().when(mBackgroundFetchRunnerSpy).updateCustomAudience(any(), any());

        mBackgroundFetchWorker.runBackgroundFetch(CommonFixture.FIXED_NOW);

        verify(mBackgroundFetchRunnerSpy, times(numEligibleCustomAudiences))
                .updateCustomAudience(any(), any());
    }

    @Test
    public void testRunBackgroundFetchChecksWorkInProgress()
            throws InterruptedException, ExecutionException, TimeoutException {
        int numEligibleCustomAudiences = 16;
        CountDownLatch partialCompletionLatch = new CountDownLatch(numEligibleCustomAudiences / 4);

        // Mock a list of custom audiences eligible for update
        DBCustomAudienceBackgroundFetchData.Builder fetchDataBuilder =
                DBCustomAudienceBackgroundFetchDataFixture.getValidBuilderByBuyer(
                                CommonFixture.VALID_BUYER_1)
                        .setEligibleUpdateTime(CommonFixture.FIXED_NOW);
        List<DBCustomAudienceBackgroundFetchData> fetchDataList = new ArrayList<>();
        for (int i = 0; i < numEligibleCustomAudiences; i++) {
            fetchDataList.add(fetchDataBuilder.setName("ca" + i).build());
        }

        doReturn(fetchDataList)
                .when(mCustomAudienceDaoSpy)
                .getActiveEligibleCustomAudienceBackgroundFetchData(any(), anyLong());
        doNothing().when(mBackgroundFetchRunnerSpy).deleteExpiredCustomAudiences(any());
        doAnswer(
                        unusedInvocation -> {
                            Thread.sleep(100);
                            partialCompletionLatch.countDown();
                            return null;
                        })
                .when(mBackgroundFetchRunnerSpy)
                .updateCustomAudience(any(), any());

        CountDownLatch bgfWorkStoppedLatch = new CountDownLatch(1);
        mExecutorService.execute(
                () -> {
                    try {
                        mBackgroundFetchWorker.runBackgroundFetch(CommonFixture.FIXED_NOW);
                    } catch (Exception exception) {
                        LogUtil.e(
                                exception, "Exception encountered while running background fetch");
                    } finally {
                        bgfWorkStoppedLatch.countDown();
                    }
                });

        // Wait til updates are partially complete, then try running background fetch again and
        // verify nothing is done
        partialCompletionLatch.await();
        mBackgroundFetchWorker.runBackgroundFetch(CommonFixture.FIXED_NOW.plusSeconds(1));

        bgfWorkStoppedLatch.await();
        verify(mBackgroundFetchRunnerSpy).deleteExpiredCustomAudiences(any());
        verify(mBackgroundFetchRunnerSpy, times(numEligibleCustomAudiences))
                .updateCustomAudience(any(), any());
    }

    @Test
    public void testStopWorkWithoutRunningFetchDoesNothing() {
        // Verify no errors/exceptions thrown when no work in progress
        mBackgroundFetchWorker.stopWork();
    }

    @Test
    public void testStopWorkGracefullyStopsBackgroundFetch() throws InterruptedException {
        int numEligibleCustomAudiences = 16;
        CountDownLatch partialCompletionLatch = new CountDownLatch(numEligibleCustomAudiences / 4);

        // Mock a list of custom audiences eligible for update
        DBCustomAudienceBackgroundFetchData.Builder fetchDataBuilder =
                DBCustomAudienceBackgroundFetchDataFixture.getValidBuilderByBuyer(
                                CommonFixture.VALID_BUYER_1)
                        .setEligibleUpdateTime(CommonFixture.FIXED_NOW);
        List<DBCustomAudienceBackgroundFetchData> fetchDataList = new ArrayList<>();
        for (int i = 0; i < numEligibleCustomAudiences; i++) {
            fetchDataList.add(fetchDataBuilder.setName("ca" + i).build());
        }

        doReturn(fetchDataList)
                .when(mCustomAudienceDaoSpy)
                .getActiveEligibleCustomAudienceBackgroundFetchData(any(), anyLong());
        doNothing().when(mBackgroundFetchRunnerSpy).deleteExpiredCustomAudiences(any());
        doAnswer(
                        unusedInvocation -> {
                            Thread.sleep(100);
                            partialCompletionLatch.countDown();
                            return null;
                        })
                .when(mBackgroundFetchRunnerSpy)
                .updateCustomAudience(any(), any());

        CountDownLatch bgfWorkStoppedLatch = new CountDownLatch(1);
        mExecutorService.execute(
                () -> {
                    try {
                        mBackgroundFetchWorker.runBackgroundFetch(CommonFixture.FIXED_NOW);
                    } catch (Exception exception) {
                        LogUtil.e(
                                exception, "Exception encountered while running background fetch");
                    } finally {
                        bgfWorkStoppedLatch.countDown();
                    }
                });

        // Wait til updates are partially complete, then try stopping background fetch
        partialCompletionLatch.await();
        mBackgroundFetchWorker.stopWork();
        // stopWork() should wait for full stoppage before returning, so the bgfWorkStoppedLatch
        // should have already counted down
        assertTrue(bgfWorkStoppedLatch.await(0, TimeUnit.MILLISECONDS));
    }
}
