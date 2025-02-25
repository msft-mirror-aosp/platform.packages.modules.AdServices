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

package com.android.adservices.service.adselection.encryption;

import static com.android.adservices.common.CommonFlagsValues.EXTENDED_AD_SELECTION_DATA_BACKGROUND_KEY_FETCH_NETWORK_CONNECT_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_AD_SELECTION_DATA_BACKGROUND_KEY_FETCH_NETWORK_READ_TIMEOUT_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_URI;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_AUCTION_KEY_FETCH_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_JOIN_KEY_FETCH_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_IN_ADVANCE_INTERVAL_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_PERIOD_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RESPONSE_SIZE_B;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RUNTIME_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_CONNECT_TIMEOUT_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_READ_TIMEOUT_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_ON_EMPTY_DB_AND_IN_ADVANCE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_COORDINATOR_URL_ALLOWLIST;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENCRYPTION_KEY_MAX_AGE_SECONDS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_JOIN_KEY_FETCH_URI;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_KEY_FETCH_METRICS_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.BACKGROUND_KEY_FETCH_STATUS_NO_OP;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.BACKGROUND_KEY_FETCH_STATUS_REFRESH_KEYS_INITIATED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.common.CommonFixture;

import com.android.adservices.LoggerFactory;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.FakeFlagsFactory;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.FetchProcessLogger;
import com.android.adservices.service.stats.ServerAuctionBackgroundKeyFetchScheduledStats;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;
import com.android.adservices.shared.testing.annotations.SetLongFlag;
import com.android.modules.utils.testing.ExtendedMockitoRule;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SetFlagEnabled(KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_ENABLED)
@SetFlagEnabled(KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_AUCTION_KEY_FETCH_ENABLED)
@SetFlagEnabled(KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_JOIN_KEY_FETCH_ENABLED)
@SetFlagEnabled(KEY_FLEDGE_AUCTION_SERVER_KEY_FETCH_METRICS_ENABLED)
@SetIntegerFlag(
        name = KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_CONNECT_TIMEOUT_MS,
        value = EXTENDED_AD_SELECTION_DATA_BACKGROUND_KEY_FETCH_NETWORK_CONNECT_TIMEOUT_MS)
@SetIntegerFlag(
        name = KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_NETWORK_READ_TIMEOUT_MS,
        value = EXTENDED_AD_SELECTION_DATA_BACKGROUND_KEY_FETCH_NETWORK_READ_TIMEOUT_MS)
@SetIntegerFlag(
        name = KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RESPONSE_SIZE_B,
        value = 100)
@SetLongFlag(name = KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RUNTIME_MS, value = 500L)
@SetLongFlag(name = KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_PERIOD_MS, value = 1000L)
@SetLongFlag(name = KEY_FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS, value = 10L)
@SetLongFlag(name = KEY_FLEDGE_AUCTION_SERVER_ENCRYPTION_KEY_MAX_AGE_SECONDS, value = 604800L)
@ExtendedMockitoRule.MockStatic(FlagsFactory.class)
public final class BackgroundKeyFetchWorkerTest extends AdServicesExtendedMockitoTestCase {
    private static final String AUCTION_KEY_FETCH_URI = "https://foo.auction";
    private static final String AUCTION_KEY_FETCH_URI_2 = "https://foo2.auction";
    private static final String JOIN_KEY_FETCH_URI = "https://foo.join";
    private static final Set<Integer> ENCRYPTION_KEY_SET =
            Set.of(
                    AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                    AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN);

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(8);

    @Mock private Clock mClockMock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;

    @Captor
    private ArgumentCaptor<ServerAuctionBackgroundKeyFetchScheduledStats>
            mServerAuctionBackgroundKeyFetchScheduledStatsArgumentCaptor;

    @Mock private ProtectedServersEncryptionConfigManager mConfigManagerMock;
    private DevContext mDevContext;
    private BackgroundKeyFetchWorker mBackgroundKeyFetchWorker;

    @Before
    public void setup() throws Exception {
        flags.setFlag(KEY_FLEDGE_AUCTION_SERVER_JOIN_KEY_FETCH_URI, JOIN_KEY_FETCH_URI);
        flags.setFlag(KEY_FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_URI, AUCTION_KEY_FETCH_URI);
        flags.setFlag(
                KEY_FLEDGE_AUCTION_SERVER_COORDINATOR_URL_ALLOWLIST,
                AUCTION_KEY_FETCH_URI + "," + AUCTION_KEY_FETCH_URI_2);

        mDevContext = DevContext.builder(mPackageName).setDeviceDevOptionsEnabled(true).build();
        mocker.mockGetFlags(mFakeFlags);

        createBackgroundKeyFetchWorker();

        when(mClockMock.instant()).thenReturn(CommonFixture.FIXED_NOW);
        when(mConfigManagerMock.fetchAndPersistActiveKeysOfType(
                        anyInt(), any(), anyLong(), any(), any(), any()))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(new ArrayList<>())));
    }

    @Test
    public void testBackgroundKeyFetchWorker_nullInputs_causeFailure() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new BackgroundKeyFetchWorker(
                                null,
                                mDevContext,
                                FakeFlagsFactory.getFlagsForTest(),
                                mClockMock,
                                mAdServicesLoggerMock));

        assertThrows(
                NullPointerException.class,
                () ->
                        new BackgroundKeyFetchWorker(
                                mConfigManagerMock,
                                null,
                                FakeFlagsFactory.getFlagsForTest(),
                                mClockMock,
                                mAdServicesLoggerMock));

        assertThrows(
                NullPointerException.class,
                () ->
                        new BackgroundKeyFetchWorker(
                                mConfigManagerMock,
                                mDevContext,
                                null,
                                mClockMock,
                                mAdServicesLoggerMock));

        assertThrows(
                NullPointerException.class,
                () ->
                        new BackgroundKeyFetchWorker(
                                mConfigManagerMock,
                                mDevContext,
                                FakeFlagsFactory.getFlagsForTest(),
                                null,
                                mAdServicesLoggerMock));

        assertThrows(
                NullPointerException.class,
                () ->
                        new BackgroundKeyFetchWorker(
                                mConfigManagerMock,
                                mDevContext,
                                FakeFlagsFactory.getFlagsForTest(),
                                mClockMock,
                                null));
    }

    @SetLongFlag(name = KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RUNTIME_MS, value = 100L)
    @Test
    public void testRunBackgroundKeyFetch_longRuntime_throwsTimeoutDuringFetch() {
        createBackgroundKeyFetchWorker();

        Answer<FluentFuture<Void>> mockAnswer =
                unused ->
                        FluentFuture.from(
                                AdServicesExecutors.getBlockingExecutor()
                                        .submit(
                                                () -> {
                                                    try {
                                                        Thread.sleep(500L);
                                                    } catch (InterruptedException e) {
                                                        sLogger.e(
                                                                e,
                                                                "Exception encountered while"
                                                                    + " running background fetch");
                                                    }
                                                    return null;
                                                }));

        when(mConfigManagerMock.getExpiredAdSelectionEncryptionKeyTypes(any()))
                .thenReturn(ENCRYPTION_KEY_SET);
        when(mConfigManagerMock.fetchAndPersistActiveKeysOfType(
                        anyInt(), any(), anyLong(), any(), any(), any()))
                .thenAnswer(mockAnswer);

        // Time out while fetching active keys
        ExecutionException expected =
                assertThrows(
                        ExecutionException.class,
                        () -> mBackgroundKeyFetchWorker.runBackgroundKeyFetch().get());
        assertThat(expected.getCause()).isInstanceOf(TimeoutException.class);
    }

    @Test
    public void testRunBackgroundFetch_noExpiredKeys_nothingToFetch() throws Exception {
        when(mConfigManagerMock.getExpiredAdSelectionEncryptionKeyTypes(any()))
                .thenReturn(Set.of());

        mBackgroundKeyFetchWorker.runBackgroundKeyFetch().get();

        verify(mConfigManagerMock).getExpiredAdSelectionEncryptionKeyTypes(any());
        verify(mAdServicesLoggerMock)
                .logServerAuctionBackgroundKeyFetchScheduledStats(
                        mServerAuctionBackgroundKeyFetchScheduledStatsArgumentCaptor.capture());
        ServerAuctionBackgroundKeyFetchScheduledStats
                serverAuctionBackgroundKeyFetchScheduledStats =
                        mServerAuctionBackgroundKeyFetchScheduledStatsArgumentCaptor.getValue();
        assertThat(serverAuctionBackgroundKeyFetchScheduledStats.getStatus())
                .isEqualTo(BACKGROUND_KEY_FETCH_STATUS_NO_OP);
        assertThat(serverAuctionBackgroundKeyFetchScheduledStats.getCountAuctionUrls())
                .isEqualTo(0);
        assertThat(serverAuctionBackgroundKeyFetchScheduledStats.getCountJoinUrls()).isEqualTo(0);
    }

    @SetFlagDisabled(KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_ENABLED)
    @SetFlagDisabled(KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_AUCTION_KEY_FETCH_ENABLED)
    @SetFlagDisabled(KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_JOIN_KEY_FETCH_ENABLED)
    @SetLongFlag(
            name = KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_MAX_RUNTIME_MS,
            value = 30000L)
    @Test
    public void testRunBackgroundKeyFetch_keyFetchJobDisabled_nothingToFetch() throws Exception {
        createBackgroundKeyFetchWorker();

        when(mConfigManagerMock.getExpiredAdSelectionEncryptionKeyTypes(any()))
                .thenReturn(ENCRYPTION_KEY_SET);

        mBackgroundKeyFetchWorker.runBackgroundKeyFetch().get();

        verify(mConfigManagerMock, times(1)).getExpiredAdSelectionEncryptionKeyTypes(any());
        verify(mConfigManagerMock, never())
                .fetchAndPersistActiveKeysOfType(anyInt(), any(), anyLong(), any(), any(), any());

        verify(mAdServicesLoggerMock)
                .logServerAuctionBackgroundKeyFetchScheduledStats(
                        mServerAuctionBackgroundKeyFetchScheduledStatsArgumentCaptor.capture());
        ServerAuctionBackgroundKeyFetchScheduledStats
                serverAuctionBackgroundKeyFetchScheduledStats =
                        mServerAuctionBackgroundKeyFetchScheduledStatsArgumentCaptor.getValue();
        assertThat(serverAuctionBackgroundKeyFetchScheduledStats.getStatus())
                .isEqualTo(BACKGROUND_KEY_FETCH_STATUS_NO_OP);
        assertThat(serverAuctionBackgroundKeyFetchScheduledStats.getCountAuctionUrls())
                .isEqualTo(0);
        assertThat(serverAuctionBackgroundKeyFetchScheduledStats.getCountJoinUrls()).isEqualTo(0);
    }

    @SetFlagDisabled(KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_AUCTION_KEY_FETCH_ENABLED)
    @Test
    public void testRunBackgroundKeyFetch_auctionKeyFetchJobDisabled_joinKeysFetched()
            throws Exception {
        createBackgroundKeyFetchWorker();

        when(mConfigManagerMock.getExpiredAdSelectionEncryptionKeyTypes(any()))
                .thenReturn(ENCRYPTION_KEY_SET);

        mBackgroundKeyFetchWorker.runBackgroundKeyFetch().get();

        verify(mConfigManagerMock).getExpiredAdSelectionEncryptionKeyTypes(any());
        verify(mConfigManagerMock, times(1))
                .fetchAndPersistActiveKeysOfType(
                        eq(AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN),
                        any(Instant.class),
                        anyLong(),
                        isNull(),
                        any(DevContext.class),
                        any(FetchProcessLogger.class));
        verify(mAdServicesLoggerMock)
                .logServerAuctionBackgroundKeyFetchScheduledStats(
                        mServerAuctionBackgroundKeyFetchScheduledStatsArgumentCaptor.capture());
        ServerAuctionBackgroundKeyFetchScheduledStats
                serverAuctionBackgroundKeyFetchScheduledStats =
                        mServerAuctionBackgroundKeyFetchScheduledStatsArgumentCaptor.getValue();
        assertThat(serverAuctionBackgroundKeyFetchScheduledStats.getStatus())
                .isEqualTo(BACKGROUND_KEY_FETCH_STATUS_REFRESH_KEYS_INITIATED);
        assertThat(serverAuctionBackgroundKeyFetchScheduledStats.getCountAuctionUrls())
                .isEqualTo(0);
        assertThat(serverAuctionBackgroundKeyFetchScheduledStats.getCountJoinUrls()).isEqualTo(1);
    }

    @SetFlagDisabled(KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_JOIN_KEY_FETCH_ENABLED)
    @Test
    public void testRunBackgroundKeyFetch_joinKeyFetchJobDisabled_allAuctionKeysFetched()
            throws ExecutionException, InterruptedException {
        createBackgroundKeyFetchWorker();

        when(mConfigManagerMock.getExpiredAdSelectionEncryptionKeyTypes(any()))
                .thenReturn(ENCRYPTION_KEY_SET);

        mBackgroundKeyFetchWorker.runBackgroundKeyFetch().get();

        verify(mConfigManagerMock).getExpiredAdSelectionEncryptionKeyTypes(any());

        // called once per url in allowlist
        verify(mConfigManagerMock, times(2))
                .fetchAndPersistActiveKeysOfType(anyInt(), any(), anyLong(), any(), any(), any());

        verify(mAdServicesLoggerMock)
                .logServerAuctionBackgroundKeyFetchScheduledStats(
                        mServerAuctionBackgroundKeyFetchScheduledStatsArgumentCaptor.capture());
        ServerAuctionBackgroundKeyFetchScheduledStats
                serverAuctionBackgroundKeyFetchScheduledStats =
                        mServerAuctionBackgroundKeyFetchScheduledStatsArgumentCaptor.getValue();
        assertThat(serverAuctionBackgroundKeyFetchScheduledStats.getStatus())
                .isEqualTo(BACKGROUND_KEY_FETCH_STATUS_REFRESH_KEYS_INITIATED);
        assertThat(serverAuctionBackgroundKeyFetchScheduledStats.getCountAuctionUrls())
                .isEqualTo(2);
        assertThat(serverAuctionBackgroundKeyFetchScheduledStats.getCountJoinUrls()).isEqualTo(0);
    }

    @SetFlagEnabled(
            KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_ON_EMPTY_DB_AND_IN_ADVANCE_ENABLED)
    @Test
    public void testRunBackgroundKeyFetch_OnEmptyDbAndInAdvanceEnabled_EmptyDb_allKeysFetched()
            throws ExecutionException, InterruptedException {
        createBackgroundKeyFetchWorker();

        when(mConfigManagerMock.getExpiredAdSelectionEncryptionKeyTypes(any()))
                .thenReturn(Set.of());
        when(mConfigManagerMock.getAbsentAdSelectionEncryptionKeyTypes())
                .thenReturn(ENCRYPTION_KEY_SET);

        mBackgroundKeyFetchWorker.runBackgroundKeyFetch().get();

        verify(mConfigManagerMock).getExpiredAdSelectionEncryptionKeyTypes(any());
        verify(mConfigManagerMock).getAbsentAdSelectionEncryptionKeyTypes();

        verify(mConfigManagerMock, times(3))
                .fetchAndPersistActiveKeysOfType(anyInt(), any(), anyLong(), any(), any(), any());
    }

    @SetFlagEnabled(
            KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_ON_EMPTY_DB_AND_IN_ADVANCE_ENABLED)
    @Test
    public void testRunBackgroundKeyFetch_OnEmptyDbAndInAdvanceEnabled_InAdvance_allKeysFetched()
            throws ExecutionException, InterruptedException {
        long inAdvanceIntervalMs = TimeUnit.SECONDS.toMillis(2);

        flags.setFlag(
                KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_IN_ADVANCE_INTERVAL_MS,
                inAdvanceIntervalMs);

        createBackgroundKeyFetchWorker();

        when(mClockMock.instant()).thenReturn(CommonFixture.FIXED_NOW);
        when(mConfigManagerMock.getExpiredAdSelectionEncryptionKeyTypes(any()))
                .thenReturn(ENCRYPTION_KEY_SET);
        when(mConfigManagerMock.getAbsentAdSelectionEncryptionKeyTypes()).thenReturn(Set.of());

        mBackgroundKeyFetchWorker.runBackgroundKeyFetch().get();

        verify(mConfigManagerMock)
                .getExpiredAdSelectionEncryptionKeyTypes(
                        CommonFixture.FIXED_NOW.plusMillis(inAdvanceIntervalMs));
        verify(mConfigManagerMock).getAbsentAdSelectionEncryptionKeyTypes();

        verify(mConfigManagerMock, times(3))
                .fetchAndPersistActiveKeysOfType(anyInt(), any(), anyLong(), any(), any(), any());
    }

    @Test
    public void test_runBackgroundKeyFetchInSequence()
            throws InterruptedException, ExecutionException {
        int fetchKeyCount = 2;
        CountDownLatch completionLatch = new CountDownLatch(fetchKeyCount);

        int totalKeysFetched = 3 * fetchKeyCount; // (2 auction + 1 join) * fetchKeyCount

        Answer<FluentFuture<Void>> mockAnswer =
                unused -> {
                    Thread.sleep(100);
                    completionLatch.countDown();
                    return FluentFuture.from(Futures.immediateFuture(null));
                };
        when(mConfigManagerMock.fetchAndPersistActiveKeysOfType(
                        anyInt(), any(), anyLong(), any(), any(), any()))
                .thenAnswer(mockAnswer);
        when(mConfigManagerMock.getExpiredAdSelectionEncryptionKeyTypes(any()))
                .thenReturn(ENCRYPTION_KEY_SET);

        CountDownLatch bgfWorkStoppedLatch = new CountDownLatch(1);
        mExecutorService.execute(
                () -> {
                    try {
                        mBackgroundKeyFetchWorker.runBackgroundKeyFetch().get();
                    } catch (Exception exception) {
                        sLogger.e(
                                exception, "Exception encountered while running background fetch");
                    } finally {
                        bgfWorkStoppedLatch.countDown();
                    }
                });

        // Wait till fetch and persist are complete, then try running background fetch again and
        // verify the second run, calls fetch and persist again.
        completionLatch.await();
        bgfWorkStoppedLatch.await();
        mBackgroundKeyFetchWorker.runBackgroundKeyFetch().get();

        verify(mConfigManagerMock, times(2)).getExpiredAdSelectionEncryptionKeyTypes(any());
        verify(mConfigManagerMock, times(totalKeysFetched))
                .fetchAndPersistActiveKeysOfType(anyInt(), any(), anyLong(), any(), any(), any());
    }

    // To create the worker again with new flags
    private void createBackgroundKeyFetchWorker() {
        mBackgroundKeyFetchWorker =
                new BackgroundKeyFetchWorker(
                        mConfigManagerMock,
                        mDevContext,
                        mFakeFlags,
                        mClockMock,
                        mAdServicesLoggerMock);
    }
}
