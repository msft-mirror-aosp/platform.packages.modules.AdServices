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

package com.android.adservices.service.stats;

import static com.android.adservices.data.adselection.AdSelectionDatabase.DATABASE_NAME;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.MISSING_END_PERSIST_AD_SELECTION;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.MISSING_PERSIST_AD_SELECTION;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.MISSING_START_PERSIST_AD_SELECTION;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.REPEATED_END_PERSIST_AD_SELECTION;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.REPEATED_START_PERSIST_AD_SELECTION;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.CallerMetadata;
import android.content.Context;
import android.net.Uri;

import com.android.adservices.data.adselection.DBAdSelection;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;

public class AdSelectionExecutionLoggerTest {

    private static final long BINDER_ELAPSED_TIMESTAMP = 100L;
    public static final CallerMetadata sCallerMetadata =
            new CallerMetadata.Builder()
                    .setBinderElapsedTimestamp(BINDER_ELAPSED_TIMESTAMP)
                    .build();
    public static final long START_ELAPSED_TIMESTAMP = 105L;
    public static final long PERSIST_AD_SELECTION_START_TIMESTAMP = 107L;
    public static final long PERSIST_AD_SELECTION_END_TIMESTAMP = 109L;
    public static final long STOP_ELAPSED_TIMESTAMP = 110L;
    private static final int BINDER_LATENCY_MS =
            (int) ((START_ELAPSED_TIMESTAMP - BINDER_ELAPSED_TIMESTAMP) * 2);
    public static final int RUN_AD_SELECTION_INTERNAL_FINAL_LATENCY_MS =
            (int) (STOP_ELAPSED_TIMESTAMP - START_ELAPSED_TIMESTAMP);
    public static final int RUN_AD_SELECTION_OVERALL_LATENCY_MS =
            BINDER_LATENCY_MS + RUN_AD_SELECTION_INTERNAL_FINAL_LATENCY_MS;
    public static final long DB_AD_SELECTION_FILE_SIZE = 10L;
    public static final boolean IS_RMKT_ADS_WON_UNSET = false;
    public static final int DB_AD_SELECTION_SIZE_IN_BYTES_UNSET = -1;
    public static final boolean IS_RMKT_ADS_WON = true;
    private static final Uri DECISION_LOGIC_URI =
            Uri.parse("https://developer.android.com/test/decisions_logic_uris");

    @Mock private Context mContextMock;
    @Mock private File mMockDBAdSelectionFile;
    @Mock private Clock mMockClock;
    @Mock private DBAdSelection mMockDBAdSelection;
    @Mock private AdServicesLogger mAdServicesLoggerMock;

    @Captor
    ArgumentCaptor<RunAdSelectionProcessReportedStats>
            mRunAdSelectionProcessReportedStatsArgumentCaptor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testAdSelectionExecutionLogger_Success() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        PERSIST_AD_SELECTION_START_TIMESTAMP,
                        PERSIST_AD_SELECTION_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        when(mContextMock.getDatabasePath(DATABASE_NAME)).thenReturn(mMockDBAdSelectionFile);
        when(mMockDBAdSelectionFile.length()).thenReturn(DB_AD_SELECTION_FILE_SIZE);
        when(mMockDBAdSelection.getBiddingLogicUri()).thenReturn(DECISION_LOGIC_URI);
        // Start the Ad selection execution logger and set start state of the process.
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        // Set start state of the subcomponent persist-ad-selection process.
        adSelectionExecutionLogger.startPersistAdSelection();
        // Set end state of the subcomponent persist-ad-selection process.
        adSelectionExecutionLogger.endPersistAdSelection();
        // Close the Ad selection execution logger and log the data into the AdServicesLogger.
        int resultCode = AdServicesStatusUtils.STATUS_SUCCESS;
        adSelectionExecutionLogger.close(mMockDBAdSelection, resultCode);

        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        mRunAdSelectionProcessReportedStatsArgumentCaptor.capture());
        RunAdSelectionProcessReportedStats runAdSelectionProcessReportedStats =
                mRunAdSelectionProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdSelectionProcessReportedStats.getIsRemarketingAdsWon())
                .isEqualTo(IS_RMKT_ADS_WON);
        assertThat(runAdSelectionProcessReportedStats.getDBAdSelectionSizeInBytes())
                .isEqualTo((int) (DB_AD_SELECTION_FILE_SIZE));
        assertThat(runAdSelectionProcessReportedStats.getPersistAdSelectionLatencyInMillis())
                .isEqualTo(
                        (int)
                                (PERSIST_AD_SELECTION_END_TIMESTAMP
                                        - PERSIST_AD_SELECTION_START_TIMESTAMP));
        assertThat(runAdSelectionProcessReportedStats.getPersistAdSelectionResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionLatencyInMillis())
                .isEqualTo(RUN_AD_SELECTION_INTERNAL_FINAL_LATENCY_MS);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionResultCode())
                .isEqualTo(resultCode);
    }

    @Test
    public void testAdSelectionExecutionLogger_redundantStartOfPersistAdSelection() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(START_ELAPSED_TIMESTAMP, PERSIST_AD_SELECTION_START_TIMESTAMP);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        // Set start state of the subcomponent persist-ad-selection process.
        adSelectionExecutionLogger.startPersistAdSelection();

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> adSelectionExecutionLogger.startPersistAdSelection());
        assertThat(throwable.getMessage()).contains(REPEATED_START_PERSIST_AD_SELECTION);
    }

    @Test
    public void testAdSelectionExecutionLogger_redundantEndOfPersistAdSelection() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        PERSIST_AD_SELECTION_START_TIMESTAMP,
                        PERSIST_AD_SELECTION_END_TIMESTAMP);
        when(mContextMock.getDatabasePath(DATABASE_NAME)).thenReturn(mMockDBAdSelectionFile);
        when(mMockDBAdSelectionFile.length()).thenReturn(DB_AD_SELECTION_FILE_SIZE);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        // Set start and end states of the subcomponent persist-ad-selection process.
        adSelectionExecutionLogger.startPersistAdSelection();
        adSelectionExecutionLogger.endPersistAdSelection();

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> adSelectionExecutionLogger.endPersistAdSelection());
        assertThat(throwable.getMessage()).contains(REPEATED_END_PERSIST_AD_SELECTION);
    }

    @Test
    public void testAdSelectionExecutionLogger_missingStartOfPersistAdSelection() {
        when(mMockClock.elapsedRealtime()).thenReturn(START_ELAPSED_TIMESTAMP);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> adSelectionExecutionLogger.endPersistAdSelection());
        assertThat(throwable.getMessage()).contains(MISSING_START_PERSIST_AD_SELECTION);
    }

    @Test
    public void testAdSelectionExecutionLogger_missingEndOfPersistAdSelection() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        PERSIST_AD_SELECTION_START_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        when(mMockDBAdSelection.getBiddingLogicUri()).thenReturn(DECISION_LOGIC_URI);
        // Start the Ad selection execution logger and set start state of the process.
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        // Set start state of the subcomponent persist-ad-selection process.
        adSelectionExecutionLogger.startPersistAdSelection();
        // Close the Ad selection execution logger and log the data into the AdServicesLogger.
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                adSelectionExecutionLogger.close(
                                        mMockDBAdSelection, AdServicesStatusUtils.STATUS_SUCCESS));
        assertThat(throwable.getMessage()).contains(MISSING_END_PERSIST_AD_SELECTION);
    }

    @Test
    public void testAdSelectionExecutionLogger_missingPersistAdSelection() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(START_ELAPSED_TIMESTAMP, STOP_ELAPSED_TIMESTAMP);
        when(mMockDBAdSelection.getBiddingLogicUri()).thenReturn(DECISION_LOGIC_URI);
        // Start the Ad selection execution logger and set start state of the process.
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        // Close the Ad selection execution logger and log the data into the AdServicesLogger.
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                adSelectionExecutionLogger.close(
                                        mMockDBAdSelection, AdServicesStatusUtils.STATUS_SUCCESS));
        assertThat(throwable.getMessage()).contains(MISSING_PERSIST_AD_SELECTION);
    }

    @Test
    public void testAdSelectionExecutionLogger_RunAdSelectionFailedBeforePersistAdSelection() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(START_ELAPSED_TIMESTAMP, STOP_ELAPSED_TIMESTAMP);
        // Start the Ad selection execution logger and set start state of the process.
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        int resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
        adSelectionExecutionLogger.close(null, resultCode);

        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        mRunAdSelectionProcessReportedStatsArgumentCaptor.capture());
        RunAdSelectionProcessReportedStats runAdSelectionProcessReportedStats =
                mRunAdSelectionProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdSelectionProcessReportedStats.getIsRemarketingAdsWon())
                .isEqualTo(IS_RMKT_ADS_WON_UNSET);
        assertThat(runAdSelectionProcessReportedStats.getDBAdSelectionSizeInBytes())
                .isEqualTo(DB_AD_SELECTION_SIZE_IN_BYTES_UNSET);
        assertThat(runAdSelectionProcessReportedStats.getPersistAdSelectionLatencyInMillis())
                .isEqualTo(AdServicesStatusUtils.STATUS_UNSET);
        assertThat(runAdSelectionProcessReportedStats.getPersistAdSelectionResultCode())
                .isEqualTo(AdServicesStatusUtils.STATUS_UNSET);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionLatencyInMillis())
                .isEqualTo(RUN_AD_SELECTION_INTERNAL_FINAL_LATENCY_MS);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionResultCode())
                .isEqualTo(resultCode);
    }

    @Test
    public void testAdSelectionExecutionLogger_RunAdSelectionFailedDuringPersistAdSelection() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        PERSIST_AD_SELECTION_START_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Start the Ad selection execution logger and set start state of the process.
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        adSelectionExecutionLogger.startPersistAdSelection();

        int resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
        adSelectionExecutionLogger.close(null, resultCode);

        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        mRunAdSelectionProcessReportedStatsArgumentCaptor.capture());
        RunAdSelectionProcessReportedStats runAdSelectionProcessReportedStats =
                mRunAdSelectionProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdSelectionProcessReportedStats.getIsRemarketingAdsWon())
                .isEqualTo(IS_RMKT_ADS_WON_UNSET);
        assertThat(runAdSelectionProcessReportedStats.getDBAdSelectionSizeInBytes())
                .isEqualTo(DB_AD_SELECTION_SIZE_IN_BYTES_UNSET);
        assertThat(runAdSelectionProcessReportedStats.getPersistAdSelectionLatencyInMillis())
                .isEqualTo(AdServicesStatusUtils.STATUS_UNSET);
        assertThat(runAdSelectionProcessReportedStats.getPersistAdSelectionResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionLatencyInMillis())
                .isEqualTo(RUN_AD_SELECTION_INTERNAL_FINAL_LATENCY_MS);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionResultCode())
                .isEqualTo(resultCode);
    }

    @Test
    public void testRunAdSelectionLatencyCalculator_getRunAdSelectionOverallLatency() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(START_ELAPSED_TIMESTAMP, STOP_ELAPSED_TIMESTAMP);
        // Start the Ad selection execution logger and set start state of the process.
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        assertThat(adSelectionExecutionLogger.getRunAdSelectionOverallLatencyInMs())
                .isEqualTo(RUN_AD_SELECTION_OVERALL_LATENCY_MS);
    }
}
