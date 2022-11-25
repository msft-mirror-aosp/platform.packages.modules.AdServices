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

import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_TIMEOUT;

import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.START_ELAPSED_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.STOP_ELAPSED_TIMESTAMP;
import static com.android.adservices.service.stats.AdServicesLoggerUtil.UNSET;
import static com.android.adservices.service.stats.BackgroundFetchExecutionLoggerTest.BACKGROUND_FETCH_START_TIMESTAMP;

import static com.google.common.truth.Truth.assertThat;

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
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.CommonFixture;
import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.LogUtil;
import com.android.adservices.customaudience.DBCustomAudienceBackgroundFetchDataFixture;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudienceBackgroundFetchData;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerUtil;
import com.android.adservices.service.stats.BackgroundFetchExecutionLogger;
import com.android.adservices.service.stats.BackgroundFetchProcessReportedStats;
import com.android.adservices.service.stats.Clock;
import com.android.adservices.service.stats.UpdateCustomAudienceExecutionLogger;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
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

    @Mock private PackageManager mPackageManagerMock;
    @Mock private EnrollmentDao mEnrollmentDaoMock;
    @Mock private Clock mClockMock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;

    @Captor
    private ArgumentCaptor<BackgroundFetchProcessReportedStats>
            mBackgroundFetchProcessReportedStatsArgumentCaptor;

    private CustomAudienceDao mCustomAudienceDaoSpy;
    private BackgroundFetchRunner mBackgroundFetchRunnerSpy;
    private BackgroundFetchWorker mBackgroundFetchWorker;
    private BackgroundFetchExecutionLogger mBackgroundFetchExecutionLogger;

    @Before
    public void setup() {
        mCustomAudienceDaoSpy =
                Mockito.spy(
                        Room.inMemoryDatabaseBuilder(CONTEXT, CustomAudienceDatabase.class)
                                .build()
                                .customAudienceDao());
        when(mClockMock.elapsedRealtime()).thenReturn(START_ELAPSED_TIMESTAMP);
        mBackgroundFetchExecutionLogger =
                new BackgroundFetchExecutionLogger(mClockMock, mAdServicesLoggerMock);
        mBackgroundFetchRunnerSpy =
                Mockito.spy(
                        new BackgroundFetchRunner(
                                mCustomAudienceDaoSpy,
                                mPackageManagerMock,
                                mEnrollmentDaoMock,
                                mFlags));

        mBackgroundFetchWorker =
                new BackgroundFetchWorker(
                        mCustomAudienceDaoSpy,
                        mFlags,
                        mBackgroundFetchRunnerSpy,
                        mBackgroundFetchExecutionLogger);
    }

    @Test
    public void testBackgroundFetchWorkerNullInputsCauseFailure() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new BackgroundFetchWorker(
                                null,
                                FlagsFactory.getFlagsForTest(),
                                mBackgroundFetchRunnerSpy,
                                mBackgroundFetchExecutionLogger));

        assertThrows(
                NullPointerException.class,
                () ->
                        new BackgroundFetchWorker(
                                mCustomAudienceDaoSpy,
                                null,
                                mBackgroundFetchRunnerSpy,
                                mBackgroundFetchExecutionLogger));

        assertThrows(
                NullPointerException.class,
                () ->
                        new BackgroundFetchWorker(
                                mCustomAudienceDaoSpy,
                                FlagsFactory.getFlagsForTest(),
                                null,
                                mBackgroundFetchExecutionLogger));
        assertThrows(
                NullPointerException.class,
                () ->
                        new BackgroundFetchWorker(
                                mCustomAudienceDaoSpy,
                                FlagsFactory.getFlagsForTest(),
                                mBackgroundFetchRunnerSpy,
                                null));
        verifyZeroInteractions(mAdServicesLoggerMock);
    }

    @Test
    public void testRunBackgroundFetchNullInputThrows() {
        assertThrows(
                NullPointerException.class, () -> mBackgroundFetchWorker.runBackgroundFetch(null));
        verifyZeroInteractions(mAdServicesLoggerMock);
    }

    @Test
    public void testRunBackgroundFetchThrowsTimeout() {
        // Time out before the job even started
        Instant jobStartTime = CommonFixture.FIXED_NOW.minusMillis(24L * 60L * 60L * 1000L);
        when(mClockMock.elapsedRealtime())
                .thenReturn(BACKGROUND_FETCH_START_TIMESTAMP, STOP_ELAPSED_TIMESTAMP);
        assertThrows(
                TimeoutException.class,
                () -> mBackgroundFetchWorker.runBackgroundFetch(jobStartTime));

        verify(mBackgroundFetchRunnerSpy, never()).updateCustomAudience(any(), any(), any());
        verifyBackgroundFetchTimeout(UNSET, STATUS_TIMEOUT);
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
                super(customAudienceDao, mPackageManagerMock, mEnrollmentDaoMock, flags);
            }

            @Override
            public void deleteExpiredCustomAudiences(@NonNull Instant jobStartTime) {
                // Do nothing
            }

            @Override
            public void updateCustomAudience(
                    @NonNull Instant jobStartTime,
                    @NonNull DBCustomAudienceBackgroundFetchData fetchData,
                    @NonNull
                            UpdateCustomAudienceExecutionLogger
                                    updateCustomAudienceExecutionLogger) {
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
                        backgroundFetchRunnerWithSleep,
                        mBackgroundFetchExecutionLogger);
        when(mClockMock.elapsedRealtime())
                .thenReturn(BACKGROUND_FETCH_START_TIMESTAMP, STOP_ELAPSED_TIMESTAMP);
        // Mock a custom audience eligible for update
        DBCustomAudienceBackgroundFetchData fetchData =
                DBCustomAudienceBackgroundFetchDataFixture.getValidBuilderByBuyer(
                                CommonFixture.VALID_BUYER_1)
                        .setEligibleUpdateTime(CommonFixture.FIXED_NOW)
                        .build();
        doReturn(Arrays.asList(fetchData))
                .when(mCustomAudienceDaoSpy)
                .getActiveEligibleCustomAudienceBackgroundFetchData(any(), anyLong());
        when(mClockMock.elapsedRealtime())
                .thenReturn(BACKGROUND_FETCH_START_TIMESTAMP, STOP_ELAPSED_TIMESTAMP);
        // Time out while updating custom audiences
        Throwable throwable =
                assertThrows(
                        TimeoutException.class,
                        () -> backgroundFetchWorkerThatTimesOut.runBackgroundFetch(Instant.now()));
        verifyBackgroundFetchTimeout(1, AdServicesLoggerUtil.getResultCodeFromException(throwable));
    }

    @Test
    public void testRunBackgroundFetchNothingToUpdate()
            throws ExecutionException, InterruptedException, TimeoutException {
        assertTrue(
                mCustomAudienceDaoSpy
                        .getActiveEligibleCustomAudienceBackgroundFetchData(
                                CommonFixture.FIXED_NOW, 1)
                        .isEmpty());
        when(mClockMock.elapsedRealtime())
                .thenReturn(BACKGROUND_FETCH_START_TIMESTAMP, STOP_ELAPSED_TIMESTAMP);
        mBackgroundFetchWorker.runBackgroundFetch(CommonFixture.FIXED_NOW);

        verify(mBackgroundFetchRunnerSpy).deleteExpiredCustomAudiences(any());
        verify(mCustomAudienceDaoSpy).deleteAllExpiredCustomAudienceData(any());
        verify(mBackgroundFetchRunnerSpy).deleteDisallowedOwnerCustomAudiences();
        verify(mCustomAudienceDaoSpy).deleteAllDisallowedOwnerCustomAudienceData(any(), any());
        verify(mBackgroundFetchRunnerSpy).deleteDisallowedBuyerCustomAudiences();
        verify(mCustomAudienceDaoSpy).deleteAllDisallowedBuyerCustomAudienceData(any(), any());
        verifyBackgroundFetchNothingToUpdate(STATUS_SUCCESS);
        verify(mBackgroundFetchRunnerSpy, never()).updateCustomAudience(any(), any(), any());
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

        when(mClockMock.elapsedRealtime())
                .thenReturn(BACKGROUND_FETCH_START_TIMESTAMP, STOP_ELAPSED_TIMESTAMP);

        doNothing().when(mBackgroundFetchRunnerSpy).updateCustomAudience(any(), any(), any());

        mBackgroundFetchWorker.runBackgroundFetch(CommonFixture.FIXED_NOW);

        verify(mBackgroundFetchRunnerSpy).deleteExpiredCustomAudiences(any());
        verify(mCustomAudienceDaoSpy).deleteAllExpiredCustomAudienceData(any());
        verify(mBackgroundFetchRunnerSpy).deleteDisallowedOwnerCustomAudiences();
        verify(mCustomAudienceDaoSpy).deleteAllDisallowedOwnerCustomAudienceData(any(), any());
        verify(mBackgroundFetchRunnerSpy).deleteDisallowedBuyerCustomAudiences();
        verify(mCustomAudienceDaoSpy).deleteAllDisallowedBuyerCustomAudienceData(any(), any());
        verifyBackgroundFetchSuccess(1);
        verify(mBackgroundFetchRunnerSpy).updateCustomAudience(any(), any(), any());
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
        when(mClockMock.elapsedRealtime())
                .thenReturn(BACKGROUND_FETCH_START_TIMESTAMP, STOP_ELAPSED_TIMESTAMP);
        doNothing().when(mBackgroundFetchRunnerSpy).updateCustomAudience(any(), any(), any());
        mBackgroundFetchWorker.runBackgroundFetch(CommonFixture.FIXED_NOW);

        verify(mBackgroundFetchRunnerSpy).deleteExpiredCustomAudiences(any());
        verify(mCustomAudienceDaoSpy).deleteAllExpiredCustomAudienceData(any());
        verify(mBackgroundFetchRunnerSpy).deleteDisallowedOwnerCustomAudiences();
        verify(mCustomAudienceDaoSpy).deleteAllDisallowedOwnerCustomAudienceData(any(), any());
        verify(mBackgroundFetchRunnerSpy).deleteDisallowedBuyerCustomAudiences();
        verify(mCustomAudienceDaoSpy).deleteAllDisallowedBuyerCustomAudienceData(any(), any());
        verify(mBackgroundFetchRunnerSpy, times(numEligibleCustomAudiences))
                .updateCustomAudience(any(), any(), any());
        verifyBackgroundFetchSuccess(numEligibleCustomAudiences);
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
        doAnswer(
                        unusedInvocation -> {
                            Thread.sleep(100);
                            partialCompletionLatch.countDown();
                            return null;
                        })
                .when(mBackgroundFetchRunnerSpy)
                .updateCustomAudience(any(), any(), any());
        when(mClockMock.elapsedRealtime())
                .thenReturn(BACKGROUND_FETCH_START_TIMESTAMP, STOP_ELAPSED_TIMESTAMP);
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
        verify(mCustomAudienceDaoSpy).deleteAllExpiredCustomAudienceData(any());
        verify(mBackgroundFetchRunnerSpy).deleteDisallowedOwnerCustomAudiences();
        verify(mCustomAudienceDaoSpy).deleteAllDisallowedOwnerCustomAudienceData(any(), any());
        verify(mBackgroundFetchRunnerSpy).deleteDisallowedBuyerCustomAudiences();
        verify(mCustomAudienceDaoSpy).deleteAllDisallowedBuyerCustomAudienceData(any(), any());
        verify(mBackgroundFetchRunnerSpy, times(numEligibleCustomAudiences))
                .updateCustomAudience(any(), any(), any());
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
        doAnswer(
                        unusedInvocation -> {
                            Thread.sleep(100);
                            partialCompletionLatch.countDown();
                            return null;
                        })
                .when(mBackgroundFetchRunnerSpy)
                .updateCustomAudience(any(), any(), any());

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
        // should have already counted down, although there may be slight (<10ms) delays between the
        // stop latch and returning from the method
        assertTrue(
                "stopWork() failed to wait until the background fetch job returned",
                bgfWorkStoppedLatch.await(10, TimeUnit.MILLISECONDS));
    }

    private void verifyBackgroundFetchSuccess(int numOfEligibleToUpdateCAs) {
        verify(mAdServicesLoggerMock)
                .logBackgroundFetchProcessReportedStats(
                        mBackgroundFetchProcessReportedStatsArgumentCaptor.capture());
        BackgroundFetchProcessReportedStats backgroundFetchProcessReportedStats =
                mBackgroundFetchProcessReportedStatsArgumentCaptor.getValue();

        assertThat(backgroundFetchProcessReportedStats.getResultCode()).isEqualTo(STATUS_SUCCESS);
        assertThat(backgroundFetchProcessReportedStats.getNumOfEligibleToUpdateCas())
                .isEqualTo(numOfEligibleToUpdateCAs);
        assertThat(backgroundFetchProcessReportedStats.getLatencyInMillis())
                .isEqualTo((int) (STOP_ELAPSED_TIMESTAMP - BACKGROUND_FETCH_START_TIMESTAMP));
    }

    private void verifyBackgroundFetchNothingToUpdate(int resultCode) {
        verify(mAdServicesLoggerMock)
                .logBackgroundFetchProcessReportedStats(
                        mBackgroundFetchProcessReportedStatsArgumentCaptor.capture());
        BackgroundFetchProcessReportedStats backgroundFetchProcessReportedStats =
                mBackgroundFetchProcessReportedStatsArgumentCaptor.getValue();

        assertThat(backgroundFetchProcessReportedStats.getResultCode()).isEqualTo(resultCode);
        assertThat(backgroundFetchProcessReportedStats.getNumOfEligibleToUpdateCas()).isEqualTo(0);
        assertThat(backgroundFetchProcessReportedStats.getLatencyInMillis())
                .isEqualTo((int) (STOP_ELAPSED_TIMESTAMP - BACKGROUND_FETCH_START_TIMESTAMP));
    }

    private void verifyBackgroundFetchTimeout(int numOfEligibleToUpdateCAs, int resultCode) {
        assertThat(resultCode).isEqualTo(STATUS_TIMEOUT);
        verify(mAdServicesLoggerMock)
                .logBackgroundFetchProcessReportedStats(
                        mBackgroundFetchProcessReportedStatsArgumentCaptor.capture());
        BackgroundFetchProcessReportedStats backgroundFetchProcessReportedStats =
                mBackgroundFetchProcessReportedStatsArgumentCaptor.getValue();

        assertThat(backgroundFetchProcessReportedStats.getResultCode()).isEqualTo(STATUS_TIMEOUT);
        assertThat(backgroundFetchProcessReportedStats.getNumOfEligibleToUpdateCas())
                .isEqualTo(numOfEligibleToUpdateCAs);
        assertThat(backgroundFetchProcessReportedStats.getLatencyInMillis())
                .isEqualTo((int) (STOP_ELAPSED_TIMESTAMP - BACKGROUND_FETCH_START_TIMESTAMP));
    }
}
