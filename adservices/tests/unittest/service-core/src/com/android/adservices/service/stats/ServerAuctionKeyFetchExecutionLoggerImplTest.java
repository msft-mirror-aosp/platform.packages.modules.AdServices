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

package com.android.adservices.service.stats;

import static com.android.adservices.service.stats.AdServicesLoggerUtil.FIELD_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_COORDINATOR_SOURCE_API;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_DATABASE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_NETWORK;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_KEY_FETCH_SOURCE_AUCTION;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_KEY_FETCH_SOURCE_BACKGROUND_FETCH;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.util.Clock;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ServerAuctionKeyFetchExecutionLoggerImplTest extends AdServicesUnitTestCase {
    public static final int KEY_FETCH_NETWORK_STATUS_CODE = 200;
    public static final int KEY_FETCH_NETWORK_LATENCY_MS = 123;
    public static final long KEY_FETCH_NETWORK_START_TIMESTAMP = 98;
    public static final long KEY_FETCH_NETWORK_END_TIMESTAMP =
            KEY_FETCH_NETWORK_START_TIMESTAMP + KEY_FETCH_NETWORK_LATENCY_MS;

    @Mock private Clock mClockMock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testServerAuctionKeyFetchExecutionLogger_successNetworkKeyFetch() {
        ArgumentCaptor<ServerAuctionKeyFetchCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(ServerAuctionKeyFetchCalledStats.class);

        when(mClockMock.elapsedRealtime())
                .thenReturn(KEY_FETCH_NETWORK_START_TIMESTAMP, KEY_FETCH_NETWORK_END_TIMESTAMP);

        // Start the Ad selection execution logger and set start state of the process.
        ServerAuctionKeyFetchExecutionLoggerImpl keyFetchLogger =
                new ServerAuctionKeyFetchExecutionLoggerImpl(mClockMock, mAdServicesLoggerMock);
        keyFetchLogger.setSource(SERVER_AUCTION_KEY_FETCH_SOURCE_BACKGROUND_FETCH);
        keyFetchLogger.setEncryptionKeySource(SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_NETWORK);
        keyFetchLogger.setCoordinatorSource(SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT);
        keyFetchLogger.startNetworkCallTimestamp();
        keyFetchLogger.logServerAuctionKeyFetchCalledStatsFromNetwork(
                KEY_FETCH_NETWORK_STATUS_CODE);

        // Verify the logging of the auction server APIs.
        verify(mAdServicesLoggerMock).logServerAuctionKeyFetchCalledStats(argumentCaptor.capture());

        ServerAuctionKeyFetchCalledStats serverAuctionKeyFetchCalledStats =
                argumentCaptor.getValue();
        assertThat(serverAuctionKeyFetchCalledStats.getSource())
                .isEqualTo(SERVER_AUCTION_KEY_FETCH_SOURCE_BACKGROUND_FETCH);
        assertThat(serverAuctionKeyFetchCalledStats.getEncryptionKeySource())
                .isEqualTo(SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_NETWORK);
        assertThat(serverAuctionKeyFetchCalledStats.getCoordinatorSource())
                .isEqualTo(SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT);
        assertThat(serverAuctionKeyFetchCalledStats.getNetworkStatusCode())
                .isEqualTo(KEY_FETCH_NETWORK_STATUS_CODE);
        assertThat(serverAuctionKeyFetchCalledStats.getNetworkLatencyMillis())
                .isEqualTo(KEY_FETCH_NETWORK_LATENCY_MS);
    }

    @Test
    public void testServerAuctionKeyFetchExecutionLogger_successDatabaseKeyFetch() {
        ArgumentCaptor<ServerAuctionKeyFetchCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(ServerAuctionKeyFetchCalledStats.class);

        // Start the key fetch execution logger and set start state of the process.
        ServerAuctionKeyFetchExecutionLoggerImpl keyFetchLogger =
                new ServerAuctionKeyFetchExecutionLoggerImpl(mClockMock, mAdServicesLoggerMock);
        keyFetchLogger.setSource(SERVER_AUCTION_KEY_FETCH_SOURCE_AUCTION);
        keyFetchLogger.setEncryptionKeySource(SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_DATABASE);
        keyFetchLogger.setCoordinatorSource(SERVER_AUCTION_COORDINATOR_SOURCE_API);
        keyFetchLogger.logServerAuctionKeyFetchCalledStatsFromDatabase();

        // Verify the logging of the auction server APIs.
        verify(mAdServicesLoggerMock).logServerAuctionKeyFetchCalledStats(argumentCaptor.capture());

        ServerAuctionKeyFetchCalledStats serverAuctionKeyFetchCalledStats =
                argumentCaptor.getValue();
        assertThat(serverAuctionKeyFetchCalledStats.getSource())
                .isEqualTo(SERVER_AUCTION_KEY_FETCH_SOURCE_AUCTION);
        assertThat(serverAuctionKeyFetchCalledStats.getEncryptionKeySource())
                .isEqualTo(SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_DATABASE);
        assertThat(serverAuctionKeyFetchCalledStats.getCoordinatorSource())
                .isEqualTo(SERVER_AUCTION_COORDINATOR_SOURCE_API);
        assertThat(serverAuctionKeyFetchCalledStats.getNetworkStatusCode()).isEqualTo(FIELD_UNSET);
        assertThat(serverAuctionKeyFetchCalledStats.getNetworkLatencyMillis())
                .isEqualTo(FIELD_UNSET);
    }

    @Test
    public void
            testServerAuctionKeyFetchExecutionLogger_NetworkKeyFetch_WrongEncryptionKeySource() {
        ArgumentCaptor<ServerAuctionKeyFetchCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(ServerAuctionKeyFetchCalledStats.class);

        when(mClockMock.elapsedRealtime())
                .thenReturn(KEY_FETCH_NETWORK_START_TIMESTAMP, KEY_FETCH_NETWORK_END_TIMESTAMP);

        // Start the key fetch execution logger and set start state of the process.
        ServerAuctionKeyFetchExecutionLoggerImpl keyFetchLogger =
                new ServerAuctionKeyFetchExecutionLoggerImpl(mClockMock, mAdServicesLoggerMock);
        keyFetchLogger.setSource(SERVER_AUCTION_KEY_FETCH_SOURCE_BACKGROUND_FETCH);
        keyFetchLogger.setEncryptionKeySource(SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_DATABASE);
        keyFetchLogger.setCoordinatorSource(SERVER_AUCTION_COORDINATOR_SOURCE_API);
        keyFetchLogger.startNetworkCallTimestamp();
        keyFetchLogger.logServerAuctionKeyFetchCalledStatsFromNetwork(
                KEY_FETCH_NETWORK_STATUS_CODE);

        // Verify the logging of the auction server APIs.
        verify(mAdServicesLoggerMock).logServerAuctionKeyFetchCalledStats(argumentCaptor.capture());

        ServerAuctionKeyFetchCalledStats serverAuctionKeyFetchCalledStats =
                argumentCaptor.getValue();
        assertThat(serverAuctionKeyFetchCalledStats.getSource())
                .isEqualTo(SERVER_AUCTION_KEY_FETCH_SOURCE_BACKGROUND_FETCH);
        assertThat(serverAuctionKeyFetchCalledStats.getEncryptionKeySource())
                .isEqualTo(SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_UNSET);
        assertThat(serverAuctionKeyFetchCalledStats.getCoordinatorSource())
                .isEqualTo(SERVER_AUCTION_COORDINATOR_SOURCE_API);
        assertThat(serverAuctionKeyFetchCalledStats.getNetworkStatusCode())
                .isEqualTo(KEY_FETCH_NETWORK_STATUS_CODE);
        assertThat(serverAuctionKeyFetchCalledStats.getNetworkLatencyMillis())
                .isEqualTo(KEY_FETCH_NETWORK_LATENCY_MS);
    }

    @Test
    public void
            testServerAuctionKeyFetchExecutionLogger_DatabaseKeyFetch_WrongEncryptionKeySource() {
        ArgumentCaptor<ServerAuctionKeyFetchCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(ServerAuctionKeyFetchCalledStats.class);

        // Start the key fetch execution logger and set start state of the process.
        ServerAuctionKeyFetchExecutionLoggerImpl keyFetchLogger =
                new ServerAuctionKeyFetchExecutionLoggerImpl(mClockMock, mAdServicesLoggerMock);
        keyFetchLogger.setSource(SERVER_AUCTION_KEY_FETCH_SOURCE_AUCTION);
        keyFetchLogger.setEncryptionKeySource(SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_NETWORK);
        keyFetchLogger.setCoordinatorSource(SERVER_AUCTION_COORDINATOR_SOURCE_API);
        keyFetchLogger.logServerAuctionKeyFetchCalledStatsFromDatabase();

        // Verify the logging of the auction server APIs.
        verify(mAdServicesLoggerMock).logServerAuctionKeyFetchCalledStats(argumentCaptor.capture());

        ServerAuctionKeyFetchCalledStats serverAuctionKeyFetchCalledStats =
                argumentCaptor.getValue();
        assertThat(serverAuctionKeyFetchCalledStats.getSource())
                .isEqualTo(SERVER_AUCTION_KEY_FETCH_SOURCE_AUCTION);
        assertThat(serverAuctionKeyFetchCalledStats.getEncryptionKeySource())
                .isEqualTo(SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_UNSET);
        assertThat(serverAuctionKeyFetchCalledStats.getCoordinatorSource())
                .isEqualTo(SERVER_AUCTION_COORDINATOR_SOURCE_API);
        assertThat(serverAuctionKeyFetchCalledStats.getNetworkStatusCode()).isEqualTo(FIELD_UNSET);
        assertThat(serverAuctionKeyFetchCalledStats.getNetworkLatencyMillis())
                .isEqualTo(FIELD_UNSET);
    }

    @Test
    public void testServerAuctionKeyFetchExecutionLogger_missingStartOfGetAdSelectionData() {
        ArgumentCaptor<ServerAuctionKeyFetchCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(ServerAuctionKeyFetchCalledStats.class);
        ServerAuctionKeyFetchExecutionLoggerImpl keyFetchLogger =
                new ServerAuctionKeyFetchExecutionLoggerImpl(mClockMock, mAdServicesLoggerMock);

        // Set a 0 timestamp for auctionServerApiStartTimestamp to mock
        // missing start of get-ad-selection-data process
        keyFetchLogger.setSource(SERVER_AUCTION_KEY_FETCH_SOURCE_BACKGROUND_FETCH);
        keyFetchLogger.setEncryptionKeySource(SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_NETWORK);
        keyFetchLogger.setCoordinatorSource(SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT);
        keyFetchLogger.startNetworkCallTimestamp();
        keyFetchLogger.setKeyFetchStartTimestamp(0L);
        keyFetchLogger.logServerAuctionKeyFetchCalledStatsFromNetwork(
                KEY_FETCH_NETWORK_STATUS_CODE);

        // Verify the logging of the auction server APIs.
        verify(mAdServicesLoggerMock).logServerAuctionKeyFetchCalledStats(argumentCaptor.capture());

        ServerAuctionKeyFetchCalledStats serverAuctionKeyFetchCalledStats =
                argumentCaptor.getValue();
        assertThat(serverAuctionKeyFetchCalledStats.getSource())
                .isEqualTo(SERVER_AUCTION_KEY_FETCH_SOURCE_BACKGROUND_FETCH);
        assertThat(serverAuctionKeyFetchCalledStats.getEncryptionKeySource())
                .isEqualTo(SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_NETWORK);
        assertThat(serverAuctionKeyFetchCalledStats.getCoordinatorSource())
                .isEqualTo(SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT);
        assertThat(serverAuctionKeyFetchCalledStats.getNetworkStatusCode())
                .isEqualTo(KEY_FETCH_NETWORK_STATUS_CODE);
        assertThat(serverAuctionKeyFetchCalledStats.getNetworkLatencyMillis())
                .isEqualTo(FIELD_UNSET);
    }

    @Test
    public void testServerAuctionKeyFetchExecutionLogger_redundantEndOfGetAdSelectionData() {
        ArgumentCaptor<ServerAuctionKeyFetchCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(ServerAuctionKeyFetchCalledStats.class);
        when(mClockMock.elapsedRealtime())
                .thenReturn(KEY_FETCH_NETWORK_START_TIMESTAMP, KEY_FETCH_NETWORK_END_TIMESTAMP);
        ServerAuctionKeyFetchExecutionLoggerImpl keyFetchLogger =
                new ServerAuctionKeyFetchExecutionLoggerImpl(mClockMock, mAdServicesLoggerMock);

        // Set a positive timestamp for auctionServerApiEndTimestamp to mock
        // redundant end of get-ad-selection-data process
        keyFetchLogger.setSource(SERVER_AUCTION_KEY_FETCH_SOURCE_BACKGROUND_FETCH);
        keyFetchLogger.setEncryptionKeySource(SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_NETWORK);
        keyFetchLogger.setCoordinatorSource(SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT);
        keyFetchLogger.startNetworkCallTimestamp();
        keyFetchLogger.setKeyFetchEndTimestamp(1L);
        keyFetchLogger.logServerAuctionKeyFetchCalledStatsFromNetwork(
                KEY_FETCH_NETWORK_STATUS_CODE);

        // Verify the logging of the auction server APIs.
        verify(mAdServicesLoggerMock).logServerAuctionKeyFetchCalledStats(argumentCaptor.capture());

        ServerAuctionKeyFetchCalledStats serverAuctionKeyFetchCalledStats =
                argumentCaptor.getValue();
        assertThat(serverAuctionKeyFetchCalledStats.getSource())
                .isEqualTo(SERVER_AUCTION_KEY_FETCH_SOURCE_BACKGROUND_FETCH);
        assertThat(serverAuctionKeyFetchCalledStats.getEncryptionKeySource())
                .isEqualTo(SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_NETWORK);
        assertThat(serverAuctionKeyFetchCalledStats.getCoordinatorSource())
                .isEqualTo(SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT);
        assertThat(serverAuctionKeyFetchCalledStats.getNetworkStatusCode())
                .isEqualTo(KEY_FETCH_NETWORK_STATUS_CODE);
        assertThat(serverAuctionKeyFetchCalledStats.getNetworkLatencyMillis())
                .isEqualTo(FIELD_UNSET);
    }
}
