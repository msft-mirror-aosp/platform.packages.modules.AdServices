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
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_SUCCESS;

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

public class SelectAdsFromOutcomesExecutionLoggerImplTest extends AdServicesUnitTestCase {
    final int mCountIds = 5;
    final int mCountNonExistingIds = 1;
    final boolean mUsedPrebuilt = false;

    final int mDownloadResultCode = 200;
    final int mDownloadLatencyMs = 123;
    final long mDownloadStartTimestamp = 98;
    final long mDownloadEndTimestamp = mDownloadStartTimestamp + mDownloadLatencyMs;

    final @AdsRelevanceStatusUtils.JsRunStatus int mExecutionResultCode = JS_RUN_STATUS_SUCCESS;
    final int mExecutionLatencyMs = 423;
    final long mExecutionStartTimestamp = 198;
    final long mExecutionEndTimestamp = mExecutionStartTimestamp + mExecutionLatencyMs;

    @Mock private Clock mClockMock;

    @Mock private AdServicesLogger mAdServicesLoggerMock;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSelectAdsFromOutcomesExecutionLogger_successDownloadAndExecution() {
        ArgumentCaptor<SelectAdsFromOutcomesApiCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(SelectAdsFromOutcomesApiCalledStats.class);

        when(mClockMock.elapsedRealtime())
                .thenReturn(
                        mDownloadStartTimestamp,
                        mDownloadEndTimestamp,
                        mExecutionStartTimestamp,
                        mExecutionEndTimestamp);

        SelectAdsFromOutcomesExecutionLoggerImpl executionLogger =
                new SelectAdsFromOutcomesExecutionLoggerImpl(mClockMock, mAdServicesLoggerMock);
        executionLogger.setCountIds(mCountIds);
        executionLogger.setCountNonExistingIds(mCountNonExistingIds);
        executionLogger.setUsedPrebuilt(mUsedPrebuilt);

        executionLogger.startDownloadScriptTimestamp();
        executionLogger.endDownloadScriptTimestamp(mDownloadResultCode);
        executionLogger.startExecutionScriptTimestamp();
        executionLogger.endExecutionScriptTimestamp(mExecutionResultCode);

        executionLogger.logSelectAdsFromOutcomesApiCalledStats();

        // Capture and verify
        verify(mAdServicesLoggerMock)
                .logSelectAdsFromOutcomesApiCalledStats(argumentCaptor.capture());

        SelectAdsFromOutcomesApiCalledStats capturedStats = argumentCaptor.getValue();
        assertThat(capturedStats.getCountIds()).isEqualTo(mCountIds);
        assertThat(capturedStats.getCountNonExistingIds()).isEqualTo(mCountNonExistingIds);
        assertThat(capturedStats.getUsedPrebuilt()).isEqualTo(mUsedPrebuilt);
        assertThat(capturedStats.getDownloadResultCode()).isEqualTo(mDownloadResultCode);
        assertThat(capturedStats.getDownloadLatencyMillis()).isEqualTo(mDownloadLatencyMs);
        assertThat(capturedStats.getExecutionResultCode()).isEqualTo(mExecutionResultCode);
        assertThat(capturedStats.getExecutionLatencyMillis()).isEqualTo(mExecutionLatencyMs);
    }

    @Test
    public void testSelectAdsFromOutcomesExecutionLogger_missingStartOfDownload() {
        ArgumentCaptor<SelectAdsFromOutcomesApiCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(SelectAdsFromOutcomesApiCalledStats.class);

        // Simulate the missing 'start' timestamp
        when(mClockMock.elapsedRealtime())
                .thenReturn(
                        mDownloadEndTimestamp, mExecutionStartTimestamp, mExecutionEndTimestamp);

        SelectAdsFromOutcomesExecutionLoggerImpl executionLogger =
                new SelectAdsFromOutcomesExecutionLoggerImpl(mClockMock, mAdServicesLoggerMock);

        executionLogger.setCountIds(mCountIds);
        executionLogger.setCountNonExistingIds(mCountNonExistingIds);
        executionLogger.setUsedPrebuilt(mUsedPrebuilt);

        // Skip startDownloadScriptTimestamp() on purpose
        executionLogger.endDownloadScriptTimestamp(mDownloadResultCode);
        executionLogger.startExecutionScriptTimestamp();
        executionLogger.endExecutionScriptTimestamp(mExecutionResultCode);

        executionLogger.logSelectAdsFromOutcomesApiCalledStats();

        verify(mAdServicesLoggerMock)
                .logSelectAdsFromOutcomesApiCalledStats(argumentCaptor.capture());

        SelectAdsFromOutcomesApiCalledStats capturedStats = argumentCaptor.getValue();

        assertThat(capturedStats.getCountIds()).isEqualTo(mCountIds);
        assertThat(capturedStats.getCountNonExistingIds()).isEqualTo(mCountNonExistingIds);
        assertThat(capturedStats.getUsedPrebuilt()).isEqualTo(mUsedPrebuilt);
        assertThat(capturedStats.getDownloadResultCode()).isEqualTo(mDownloadResultCode);
        assertThat(capturedStats.getDownloadLatencyMillis()).isEqualTo(FIELD_UNSET);
        assertThat(capturedStats.getExecutionResultCode()).isEqualTo(mExecutionResultCode);
        assertThat(capturedStats.getExecutionLatencyMillis()).isEqualTo(mExecutionLatencyMs);
    }

    @Test
    public void testSelectAdsFromOutcomesExecutionLogger_repeatedEndOfDownload() {
        ArgumentCaptor<SelectAdsFromOutcomesApiCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(SelectAdsFromOutcomesApiCalledStats.class);

        when(mClockMock.elapsedRealtime())
                .thenReturn(
                        mDownloadStartTimestamp,
                        mDownloadEndTimestamp,
                        mDownloadEndTimestamp + 1,
                        mExecutionStartTimestamp,
                        mExecutionEndTimestamp); // Extra DOWNLOAD_END_TIMESTAMP

        SelectAdsFromOutcomesExecutionLoggerImpl executionLogger =
                new SelectAdsFromOutcomesExecutionLoggerImpl(mClockMock, mAdServicesLoggerMock);

        executionLogger.setCountIds(mCountIds);
        executionLogger.setCountNonExistingIds(mCountNonExistingIds);
        executionLogger.setUsedPrebuilt(mUsedPrebuilt);

        executionLogger.startDownloadScriptTimestamp();
        executionLogger.endDownloadScriptTimestamp(mDownloadResultCode);
        executionLogger.endDownloadScriptTimestamp(mDownloadResultCode); // Repeated call
        executionLogger.startExecutionScriptTimestamp();
        executionLogger.endExecutionScriptTimestamp(mExecutionResultCode);

        executionLogger.logSelectAdsFromOutcomesApiCalledStats();

        verify(mAdServicesLoggerMock)
                .logSelectAdsFromOutcomesApiCalledStats(argumentCaptor.capture());

        SelectAdsFromOutcomesApiCalledStats capturedStats = argumentCaptor.getValue();

        assertThat(capturedStats.getCountIds()).isEqualTo(mCountIds);
        assertThat(capturedStats.getCountNonExistingIds()).isEqualTo(mCountNonExistingIds);
        assertThat(capturedStats.getUsedPrebuilt()).isEqualTo(mUsedPrebuilt);
        assertThat(capturedStats.getDownloadResultCode()).isEqualTo(mDownloadResultCode);
        assertThat(capturedStats.getDownloadLatencyMillis()).isEqualTo(FIELD_UNSET);
        assertThat(capturedStats.getExecutionResultCode()).isEqualTo(mExecutionResultCode);
        assertThat(capturedStats.getExecutionLatencyMillis()).isEqualTo(mExecutionLatencyMs);
    }

    @Test
    public void testSelectAdsFromOutcomesExecutionLogger_missingStartOfExecution() {
        ArgumentCaptor<SelectAdsFromOutcomesApiCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(SelectAdsFromOutcomesApiCalledStats.class);

        // Simulate the missing 'start' timestamp
        when(mClockMock.elapsedRealtime())
                .thenReturn(mDownloadStartTimestamp, mDownloadEndTimestamp, mExecutionEndTimestamp);

        SelectAdsFromOutcomesExecutionLoggerImpl executionLogger =
                new SelectAdsFromOutcomesExecutionLoggerImpl(mClockMock, mAdServicesLoggerMock);

        executionLogger.setCountIds(mCountIds);
        executionLogger.setCountNonExistingIds(mCountNonExistingIds);
        executionLogger.setUsedPrebuilt(mUsedPrebuilt);

        executionLogger.startDownloadScriptTimestamp();
        executionLogger.endDownloadScriptTimestamp(mDownloadResultCode);

        // Skip startExecutionScriptTimestamp() on purpose
        executionLogger.endExecutionScriptTimestamp(mExecutionResultCode);

        executionLogger.logSelectAdsFromOutcomesApiCalledStats();

        verify(mAdServicesLoggerMock)
                .logSelectAdsFromOutcomesApiCalledStats(argumentCaptor.capture());

        SelectAdsFromOutcomesApiCalledStats capturedStats = argumentCaptor.getValue();

        assertThat(capturedStats.getCountIds()).isEqualTo(mCountIds);
        assertThat(capturedStats.getCountNonExistingIds()).isEqualTo(mCountNonExistingIds);
        assertThat(capturedStats.getUsedPrebuilt()).isEqualTo(mUsedPrebuilt);
        assertThat(capturedStats.getDownloadResultCode()).isEqualTo(mDownloadResultCode);
        assertThat(capturedStats.getDownloadLatencyMillis()).isEqualTo(mDownloadLatencyMs);
        assertThat(capturedStats.getExecutionResultCode()).isEqualTo(mExecutionResultCode);
        assertThat(capturedStats.getExecutionLatencyMillis()).isEqualTo(FIELD_UNSET);
    }

    @Test
    public void testSelectAdsFromOutcomesExecutionLogger_repeatedEndOfExecution() {
        ArgumentCaptor<SelectAdsFromOutcomesApiCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(SelectAdsFromOutcomesApiCalledStats.class);

        when(mClockMock.elapsedRealtime())
                .thenReturn(
                        mDownloadStartTimestamp,
                        mDownloadEndTimestamp,
                        mExecutionStartTimestamp,
                        mExecutionEndTimestamp,
                        mExecutionEndTimestamp + 1); // Extra EXECUTION_END_TIMESTAMP

        SelectAdsFromOutcomesExecutionLoggerImpl executionLogger =
                new SelectAdsFromOutcomesExecutionLoggerImpl(mClockMock, mAdServicesLoggerMock);

        executionLogger.setCountIds(mCountIds);
        executionLogger.setCountNonExistingIds(mCountNonExistingIds);
        executionLogger.setUsedPrebuilt(mUsedPrebuilt);

        executionLogger.startDownloadScriptTimestamp();
        executionLogger.endDownloadScriptTimestamp(mDownloadResultCode);
        executionLogger.startExecutionScriptTimestamp();
        executionLogger.endExecutionScriptTimestamp(mExecutionResultCode);
        executionLogger.endExecutionScriptTimestamp(mExecutionResultCode); // Repeated call

        executionLogger.logSelectAdsFromOutcomesApiCalledStats();

        verify(mAdServicesLoggerMock)
                .logSelectAdsFromOutcomesApiCalledStats(argumentCaptor.capture());

        SelectAdsFromOutcomesApiCalledStats capturedStats = argumentCaptor.getValue();

        assertThat(capturedStats.getCountIds()).isEqualTo(mCountIds);
        assertThat(capturedStats.getCountNonExistingIds()).isEqualTo(mCountNonExistingIds);
        assertThat(capturedStats.getUsedPrebuilt()).isEqualTo(mUsedPrebuilt);
        assertThat(capturedStats.getDownloadResultCode()).isEqualTo(mDownloadResultCode);
        assertThat(capturedStats.getDownloadLatencyMillis()).isEqualTo(mDownloadLatencyMs);
        assertThat(capturedStats.getExecutionResultCode()).isEqualTo(mExecutionResultCode);
        assertThat(capturedStats.getExecutionLatencyMillis()).isEqualTo(FIELD_UNSET);
    }
}
