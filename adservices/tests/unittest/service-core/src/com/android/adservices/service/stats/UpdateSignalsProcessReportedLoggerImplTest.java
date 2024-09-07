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

import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static com.android.adservices.service.stats.AdServicesLoggerUtil.FIELD_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_MEDIUM;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_UNSET;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLoggerImpl;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedStats;
import com.android.adservices.shared.util.Clock;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public final class UpdateSignalsProcessReportedLoggerImplTest extends AdServicesMockitoTestCase {
    private static final long TEST_UPDATE_SIGNALS_START_TIME = 100L;
    private static final long TEST_UPDATE_SIGNALS_END_TIME = 300L;
    private static final int TEST_UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS =
            (int) (TEST_UPDATE_SIGNALS_END_TIME - TEST_UPDATE_SIGNALS_START_TIME);
    private static final int TEST_ADSERVICES_API_STATUS_CODE = STATUS_SUCCESS;
    private static final int TEST_SIGNALS_WRITTEN_COUNT = 10;
    private static final int TEST_KEYS_STORED_COUNT = 6;
    private static final int TEST_VALUES_STORED_COUNT = TEST_SIGNALS_WRITTEN_COUNT;
    private static final int TEST_EVICTION_RULES_COUNT = 8;
    private static final int TEST_EXACT_PER_BUYER_SIGNAL_SIZE = 300;
    private static final int TEST_BUCKETED_PER_BUYER_SIGNAL_SIZE = SIZE_MEDIUM;
    private static final float TEST_MEAN_RAW_PROTECTED_SIGNALS_SIZE_BYTES =
            (float) TEST_EXACT_PER_BUYER_SIGNAL_SIZE / TEST_SIGNALS_WRITTEN_COUNT;
    private static final float TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES = 345.67F;
    private static final float TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES = 0.0001F;

    @Mock private Clock mClockMock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;
    private UpdateSignalsProcessReportedLoggerImpl mUpdateSignalsProcessReportedLoggerImpl;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mUpdateSignalsProcessReportedLoggerImpl =
                new UpdateSignalsProcessReportedLoggerImpl(mAdServicesLoggerMock, mClockMock);
    }

    @Test
    public void testUpdateSignalsProcessReportedStatsLogger_successLogging() {
        ArgumentCaptor<UpdateSignalsProcessReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(UpdateSignalsProcessReportedStats.class);

        when(mClockMock.elapsedRealtime()).thenReturn(TEST_UPDATE_SIGNALS_END_TIME);

        mUpdateSignalsProcessReportedLoggerImpl.setUpdateSignalsStartTimestamp(
                TEST_UPDATE_SIGNALS_START_TIME);
        mUpdateSignalsProcessReportedLoggerImpl.setAdservicesApiStatusCode(
                TEST_ADSERVICES_API_STATUS_CODE);
        mUpdateSignalsProcessReportedLoggerImpl.setSignalsWrittenAndValuesCount(
                TEST_SIGNALS_WRITTEN_COUNT);
        mUpdateSignalsProcessReportedLoggerImpl.setKeysStoredCount(TEST_KEYS_STORED_COUNT);
        mUpdateSignalsProcessReportedLoggerImpl.setEvictionRulesCount(TEST_EVICTION_RULES_COUNT);
        mUpdateSignalsProcessReportedLoggerImpl.setPerBuyerSignalSize(
                TEST_EXACT_PER_BUYER_SIGNAL_SIZE);
        mUpdateSignalsProcessReportedLoggerImpl.setMaxRawProtectedSignalsSizeBytes(
                TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        mUpdateSignalsProcessReportedLoggerImpl.setMinRawProtectedSignalsSizeBytes(
                TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(argumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = argumentCaptor.getValue();
        assertThat(stats.getUpdateSignalsProcessLatencyMillis())
                .isEqualTo(TEST_UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS);
        assertThat(stats.getAdservicesApiStatusCode()).isEqualTo(TEST_ADSERVICES_API_STATUS_CODE);
        assertThat(stats.getSignalsWrittenCount()).isEqualTo(TEST_SIGNALS_WRITTEN_COUNT);
        assertThat(stats.getKeysStoredCount()).isEqualTo(TEST_KEYS_STORED_COUNT);
        assertThat(stats.getValuesStoredCount()).isEqualTo(TEST_VALUES_STORED_COUNT);
        assertThat(stats.getEvictionRulesCount()).isEqualTo(TEST_EVICTION_RULES_COUNT);
        assertThat(stats.getPerBuyerSignalSize()).isEqualTo(TEST_BUCKETED_PER_BUYER_SIGNAL_SIZE);
        assertThat(stats.getMeanRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MEAN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        assertThat(stats.getMaxRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        assertThat(stats.getMinRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
    }

    @Test
    public void testUpdateSignalsProcessReportedStatsLogger_missingUpdateSignalsStartTime() {
        ArgumentCaptor<UpdateSignalsProcessReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(UpdateSignalsProcessReportedStats.class);

        when(mClockMock.elapsedRealtime()).thenReturn(TEST_UPDATE_SIGNALS_END_TIME);

        // Skip setUpdateSignalsProcessReportedStartTimestamp() on purpose
        mUpdateSignalsProcessReportedLoggerImpl.setAdservicesApiStatusCode(
                TEST_ADSERVICES_API_STATUS_CODE);
        mUpdateSignalsProcessReportedLoggerImpl.setSignalsWrittenAndValuesCount(
                TEST_SIGNALS_WRITTEN_COUNT);
        mUpdateSignalsProcessReportedLoggerImpl.setKeysStoredCount(TEST_KEYS_STORED_COUNT);
        mUpdateSignalsProcessReportedLoggerImpl.setEvictionRulesCount(TEST_EVICTION_RULES_COUNT);
        mUpdateSignalsProcessReportedLoggerImpl.setPerBuyerSignalSize(
                TEST_EXACT_PER_BUYER_SIGNAL_SIZE);
        mUpdateSignalsProcessReportedLoggerImpl.setMaxRawProtectedSignalsSizeBytes(
                TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        mUpdateSignalsProcessReportedLoggerImpl.setMinRawProtectedSignalsSizeBytes(
                TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(argumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = argumentCaptor.getValue();
        assertThat(stats.getUpdateSignalsProcessLatencyMillis()).isEqualTo(FIELD_UNSET);
        assertThat(stats.getAdservicesApiStatusCode()).isEqualTo(TEST_ADSERVICES_API_STATUS_CODE);
        assertThat(stats.getSignalsWrittenCount()).isEqualTo(TEST_SIGNALS_WRITTEN_COUNT);
        assertThat(stats.getKeysStoredCount()).isEqualTo(TEST_KEYS_STORED_COUNT);
        assertThat(stats.getValuesStoredCount()).isEqualTo(TEST_VALUES_STORED_COUNT);
        assertThat(stats.getEvictionRulesCount()).isEqualTo(TEST_EVICTION_RULES_COUNT);
        assertThat(stats.getPerBuyerSignalSize()).isEqualTo(TEST_BUCKETED_PER_BUYER_SIGNAL_SIZE);
        assertThat(stats.getMeanRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MEAN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        assertThat(stats.getMaxRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        assertThat(stats.getMinRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
    }

    @Test
    public void testUpdateSignalsProcessReportedStatsLogger_missingAdservicesApiStatusCode() {
        ArgumentCaptor<UpdateSignalsProcessReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(UpdateSignalsProcessReportedStats.class);

        when(mClockMock.elapsedRealtime()).thenReturn(TEST_UPDATE_SIGNALS_END_TIME);

        mUpdateSignalsProcessReportedLoggerImpl.setUpdateSignalsStartTimestamp(
                TEST_UPDATE_SIGNALS_START_TIME);
        // Skip setAdservicesApiStatusCode() on purpose
        mUpdateSignalsProcessReportedLoggerImpl.setSignalsWrittenAndValuesCount(
                TEST_SIGNALS_WRITTEN_COUNT);
        mUpdateSignalsProcessReportedLoggerImpl.setKeysStoredCount(TEST_KEYS_STORED_COUNT);
        mUpdateSignalsProcessReportedLoggerImpl.setEvictionRulesCount(TEST_EVICTION_RULES_COUNT);
        mUpdateSignalsProcessReportedLoggerImpl.setPerBuyerSignalSize(
                TEST_EXACT_PER_BUYER_SIGNAL_SIZE);
        mUpdateSignalsProcessReportedLoggerImpl.setMaxRawProtectedSignalsSizeBytes(
                TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        mUpdateSignalsProcessReportedLoggerImpl.setMinRawProtectedSignalsSizeBytes(
                TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(argumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = argumentCaptor.getValue();
        assertThat(stats.getUpdateSignalsProcessLatencyMillis())
                .isEqualTo(TEST_UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS);
        assertThat(stats.getAdservicesApiStatusCode()).isEqualTo(FIELD_UNSET);
        assertThat(stats.getSignalsWrittenCount()).isEqualTo(TEST_SIGNALS_WRITTEN_COUNT);
        assertThat(stats.getKeysStoredCount()).isEqualTo(TEST_KEYS_STORED_COUNT);
        assertThat(stats.getValuesStoredCount()).isEqualTo(TEST_VALUES_STORED_COUNT);
        assertThat(stats.getEvictionRulesCount()).isEqualTo(TEST_EVICTION_RULES_COUNT);
        assertThat(stats.getPerBuyerSignalSize()).isEqualTo(TEST_BUCKETED_PER_BUYER_SIGNAL_SIZE);
        assertThat(stats.getMeanRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MEAN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        assertThat(stats.getMaxRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        assertThat(stats.getMinRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
    }

    @Test
    public void testUpdateSignalsProcessReportedStatsLogger_missingSignalsWrittenAndValuesCount() {
        ArgumentCaptor<UpdateSignalsProcessReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(UpdateSignalsProcessReportedStats.class);

        when(mClockMock.elapsedRealtime()).thenReturn(TEST_UPDATE_SIGNALS_END_TIME);

        mUpdateSignalsProcessReportedLoggerImpl.setUpdateSignalsStartTimestamp(
                TEST_UPDATE_SIGNALS_START_TIME);
        mUpdateSignalsProcessReportedLoggerImpl.setAdservicesApiStatusCode(
                TEST_ADSERVICES_API_STATUS_CODE);
        // Skip setSignalsWrittenAndValuesCount() on purpose
        // Note that signalsWrittenCount == ValuesStoredCount and are both set in
        // setSignalsWrittenAndValuesCount()
        mUpdateSignalsProcessReportedLoggerImpl.setKeysStoredCount(TEST_KEYS_STORED_COUNT);
        mUpdateSignalsProcessReportedLoggerImpl.setEvictionRulesCount(TEST_EVICTION_RULES_COUNT);
        mUpdateSignalsProcessReportedLoggerImpl.setPerBuyerSignalSize(
                TEST_EXACT_PER_BUYER_SIGNAL_SIZE);
        mUpdateSignalsProcessReportedLoggerImpl.setMaxRawProtectedSignalsSizeBytes(
                TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        mUpdateSignalsProcessReportedLoggerImpl.setMinRawProtectedSignalsSizeBytes(
                TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(argumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = argumentCaptor.getValue();
        assertThat(stats.getUpdateSignalsProcessLatencyMillis())
                .isEqualTo(TEST_UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS);
        assertThat(stats.getAdservicesApiStatusCode()).isEqualTo(TEST_ADSERVICES_API_STATUS_CODE);
        assertThat(stats.getSignalsWrittenCount()).isEqualTo(0);
        assertThat(stats.getKeysStoredCount()).isEqualTo(TEST_KEYS_STORED_COUNT);
        assertThat(stats.getValuesStoredCount()).isEqualTo(0);
        assertThat(stats.getEvictionRulesCount()).isEqualTo(TEST_EVICTION_RULES_COUNT);
        assertThat(stats.getPerBuyerSignalSize()).isEqualTo(TEST_BUCKETED_PER_BUYER_SIGNAL_SIZE);
        assertThat(stats.getMeanRawProtectedSignalsSizeBytes()).isEqualTo(SIZE_UNSET);
        assertThat(stats.getMaxRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        assertThat(stats.getMinRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
    }

    @Test
    public void testUpdateSignalsProcessReportedStatsLogger_missingKeysStoredCount() {
        ArgumentCaptor<UpdateSignalsProcessReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(UpdateSignalsProcessReportedStats.class);

        when(mClockMock.elapsedRealtime()).thenReturn(TEST_UPDATE_SIGNALS_END_TIME);

        mUpdateSignalsProcessReportedLoggerImpl.setUpdateSignalsStartTimestamp(
                TEST_UPDATE_SIGNALS_START_TIME);
        mUpdateSignalsProcessReportedLoggerImpl.setAdservicesApiStatusCode(
                TEST_ADSERVICES_API_STATUS_CODE);
        mUpdateSignalsProcessReportedLoggerImpl.setSignalsWrittenAndValuesCount(
                TEST_SIGNALS_WRITTEN_COUNT);
        // Skip setKeysStoredCount() on purpose
        mUpdateSignalsProcessReportedLoggerImpl.setEvictionRulesCount(TEST_EVICTION_RULES_COUNT);
        mUpdateSignalsProcessReportedLoggerImpl.setPerBuyerSignalSize(
                TEST_EXACT_PER_BUYER_SIGNAL_SIZE);
        mUpdateSignalsProcessReportedLoggerImpl.setMaxRawProtectedSignalsSizeBytes(
                TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        mUpdateSignalsProcessReportedLoggerImpl.setMinRawProtectedSignalsSizeBytes(
                TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(argumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = argumentCaptor.getValue();
        assertThat(stats.getUpdateSignalsProcessLatencyMillis())
                .isEqualTo(TEST_UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS);
        assertThat(stats.getAdservicesApiStatusCode()).isEqualTo(TEST_ADSERVICES_API_STATUS_CODE);
        assertThat(stats.getSignalsWrittenCount()).isEqualTo(TEST_SIGNALS_WRITTEN_COUNT);
        assertThat(stats.getKeysStoredCount()).isEqualTo(0);
        assertThat(stats.getValuesStoredCount()).isEqualTo(TEST_VALUES_STORED_COUNT);
        assertThat(stats.getEvictionRulesCount()).isEqualTo(TEST_EVICTION_RULES_COUNT);
        assertThat(stats.getPerBuyerSignalSize()).isEqualTo(TEST_BUCKETED_PER_BUYER_SIGNAL_SIZE);
        assertThat(stats.getMeanRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MEAN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        assertThat(stats.getMaxRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        assertThat(stats.getMinRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
    }

    @Test
    public void testUpdateSignalsProcessReportedStatsLogger_missingEvictionRulesCount() {
        ArgumentCaptor<UpdateSignalsProcessReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(UpdateSignalsProcessReportedStats.class);

        when(mClockMock.elapsedRealtime()).thenReturn(TEST_UPDATE_SIGNALS_END_TIME);

        mUpdateSignalsProcessReportedLoggerImpl.setUpdateSignalsStartTimestamp(
                TEST_UPDATE_SIGNALS_START_TIME);
        mUpdateSignalsProcessReportedLoggerImpl.setAdservicesApiStatusCode(
                TEST_ADSERVICES_API_STATUS_CODE);
        mUpdateSignalsProcessReportedLoggerImpl.setSignalsWrittenAndValuesCount(
                TEST_SIGNALS_WRITTEN_COUNT);
        mUpdateSignalsProcessReportedLoggerImpl.setKeysStoredCount(TEST_KEYS_STORED_COUNT);
        // Skip setEvictionRulesCount() on purpose
        mUpdateSignalsProcessReportedLoggerImpl.setPerBuyerSignalSize(
                TEST_EXACT_PER_BUYER_SIGNAL_SIZE);
        mUpdateSignalsProcessReportedLoggerImpl.setMaxRawProtectedSignalsSizeBytes(
                TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        mUpdateSignalsProcessReportedLoggerImpl.setMinRawProtectedSignalsSizeBytes(
                TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(argumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = argumentCaptor.getValue();
        assertThat(stats.getUpdateSignalsProcessLatencyMillis())
                .isEqualTo(TEST_UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS);
        assertThat(stats.getAdservicesApiStatusCode()).isEqualTo(TEST_ADSERVICES_API_STATUS_CODE);
        assertThat(stats.getSignalsWrittenCount()).isEqualTo(TEST_SIGNALS_WRITTEN_COUNT);
        assertThat(stats.getKeysStoredCount()).isEqualTo(TEST_KEYS_STORED_COUNT);
        assertThat(stats.getValuesStoredCount()).isEqualTo(TEST_VALUES_STORED_COUNT);
        assertThat(stats.getEvictionRulesCount()).isEqualTo(0);
        assertThat(stats.getPerBuyerSignalSize()).isEqualTo(TEST_BUCKETED_PER_BUYER_SIGNAL_SIZE);
        assertThat(stats.getMeanRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MEAN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        assertThat(stats.getMaxRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        assertThat(stats.getMinRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
    }

    @Test
    public void testUpdateSignalsProcessReportedStatsLogger_missingPerBuyerSignalSize() {
        ArgumentCaptor<UpdateSignalsProcessReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(UpdateSignalsProcessReportedStats.class);

        when(mClockMock.elapsedRealtime()).thenReturn(TEST_UPDATE_SIGNALS_END_TIME);

        mUpdateSignalsProcessReportedLoggerImpl.setUpdateSignalsStartTimestamp(
                TEST_UPDATE_SIGNALS_START_TIME);
        mUpdateSignalsProcessReportedLoggerImpl.setAdservicesApiStatusCode(
                TEST_ADSERVICES_API_STATUS_CODE);
        mUpdateSignalsProcessReportedLoggerImpl.setSignalsWrittenAndValuesCount(
                TEST_SIGNALS_WRITTEN_COUNT);
        mUpdateSignalsProcessReportedLoggerImpl.setKeysStoredCount(TEST_KEYS_STORED_COUNT);
        mUpdateSignalsProcessReportedLoggerImpl.setEvictionRulesCount(TEST_EVICTION_RULES_COUNT);
        // Skip setPerBuyerSignalSize() on purpose
        mUpdateSignalsProcessReportedLoggerImpl.setMaxRawProtectedSignalsSizeBytes(
                TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        mUpdateSignalsProcessReportedLoggerImpl.setMinRawProtectedSignalsSizeBytes(
                TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(argumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = argumentCaptor.getValue();
        assertThat(stats.getUpdateSignalsProcessLatencyMillis())
                .isEqualTo(TEST_UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS);
        assertThat(stats.getAdservicesApiStatusCode()).isEqualTo(TEST_ADSERVICES_API_STATUS_CODE);
        assertThat(stats.getSignalsWrittenCount()).isEqualTo(TEST_SIGNALS_WRITTEN_COUNT);
        assertThat(stats.getKeysStoredCount()).isEqualTo(TEST_KEYS_STORED_COUNT);
        assertThat(stats.getValuesStoredCount()).isEqualTo(TEST_VALUES_STORED_COUNT);
        assertThat(stats.getEvictionRulesCount()).isEqualTo(TEST_EVICTION_RULES_COUNT);
        assertThat(stats.getPerBuyerSignalSize()).isEqualTo(SIZE_UNSET);
        assertThat(stats.getMeanRawProtectedSignalsSizeBytes()).isEqualTo(SIZE_UNSET);
        assertThat(stats.getMaxRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        assertThat(stats.getMinRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
    }

    @Test
    public void
            testUpdateSignalsProcessReportedStatsLogger_missingMaxRawProtectedSignalsSizeBytes() {
        ArgumentCaptor<UpdateSignalsProcessReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(UpdateSignalsProcessReportedStats.class);

        when(mClockMock.elapsedRealtime()).thenReturn(TEST_UPDATE_SIGNALS_END_TIME);

        mUpdateSignalsProcessReportedLoggerImpl.setUpdateSignalsStartTimestamp(
                TEST_UPDATE_SIGNALS_START_TIME);
        mUpdateSignalsProcessReportedLoggerImpl.setAdservicesApiStatusCode(
                TEST_ADSERVICES_API_STATUS_CODE);
        mUpdateSignalsProcessReportedLoggerImpl.setSignalsWrittenAndValuesCount(
                TEST_SIGNALS_WRITTEN_COUNT);
        mUpdateSignalsProcessReportedLoggerImpl.setKeysStoredCount(TEST_KEYS_STORED_COUNT);
        mUpdateSignalsProcessReportedLoggerImpl.setEvictionRulesCount(TEST_EVICTION_RULES_COUNT);
        mUpdateSignalsProcessReportedLoggerImpl.setPerBuyerSignalSize(
                TEST_EXACT_PER_BUYER_SIGNAL_SIZE);
        // Skip setMaxRawProtectedSignalsSizeBytes() on purpose
        mUpdateSignalsProcessReportedLoggerImpl.setMinRawProtectedSignalsSizeBytes(
                TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(argumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = argumentCaptor.getValue();
        assertThat(stats.getUpdateSignalsProcessLatencyMillis())
                .isEqualTo(TEST_UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS);
        assertThat(stats.getAdservicesApiStatusCode()).isEqualTo(TEST_ADSERVICES_API_STATUS_CODE);
        assertThat(stats.getSignalsWrittenCount()).isEqualTo(TEST_SIGNALS_WRITTEN_COUNT);
        assertThat(stats.getKeysStoredCount()).isEqualTo(TEST_KEYS_STORED_COUNT);
        assertThat(stats.getValuesStoredCount()).isEqualTo(TEST_VALUES_STORED_COUNT);
        assertThat(stats.getEvictionRulesCount()).isEqualTo(TEST_EVICTION_RULES_COUNT);
        assertThat(stats.getPerBuyerSignalSize()).isEqualTo(TEST_BUCKETED_PER_BUYER_SIGNAL_SIZE);
        assertThat(stats.getMeanRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MEAN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        assertThat(stats.getMaxRawProtectedSignalsSizeBytes()).isEqualTo(0F);
        assertThat(stats.getMinRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
    }

    @Test
    public void
            testUpdateSignalsProcessReportedStatsLogger_missingMinRawProtectedSignalsSizeBytes() {
        ArgumentCaptor<UpdateSignalsProcessReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(UpdateSignalsProcessReportedStats.class);

        when(mClockMock.elapsedRealtime()).thenReturn(TEST_UPDATE_SIGNALS_END_TIME);

        mUpdateSignalsProcessReportedLoggerImpl.setUpdateSignalsStartTimestamp(
                TEST_UPDATE_SIGNALS_START_TIME);
        mUpdateSignalsProcessReportedLoggerImpl.setAdservicesApiStatusCode(
                TEST_ADSERVICES_API_STATUS_CODE);
        mUpdateSignalsProcessReportedLoggerImpl.setSignalsWrittenAndValuesCount(
                TEST_SIGNALS_WRITTEN_COUNT);
        mUpdateSignalsProcessReportedLoggerImpl.setKeysStoredCount(TEST_KEYS_STORED_COUNT);
        mUpdateSignalsProcessReportedLoggerImpl.setEvictionRulesCount(TEST_EVICTION_RULES_COUNT);
        mUpdateSignalsProcessReportedLoggerImpl.setPerBuyerSignalSize(
                TEST_EXACT_PER_BUYER_SIGNAL_SIZE);
        mUpdateSignalsProcessReportedLoggerImpl.setMaxRawProtectedSignalsSizeBytes(
                TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        // Skip setMinRawProtectedSignalsSizeBytes() on purpose
        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(argumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = argumentCaptor.getValue();
        assertThat(stats.getUpdateSignalsProcessLatencyMillis())
                .isEqualTo(TEST_UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS);
        assertThat(stats.getAdservicesApiStatusCode()).isEqualTo(TEST_ADSERVICES_API_STATUS_CODE);
        assertThat(stats.getSignalsWrittenCount()).isEqualTo(TEST_SIGNALS_WRITTEN_COUNT);
        assertThat(stats.getKeysStoredCount()).isEqualTo(TEST_KEYS_STORED_COUNT);
        assertThat(stats.getValuesStoredCount()).isEqualTo(TEST_VALUES_STORED_COUNT);
        assertThat(stats.getEvictionRulesCount()).isEqualTo(TEST_EVICTION_RULES_COUNT);
        assertThat(stats.getPerBuyerSignalSize()).isEqualTo(TEST_BUCKETED_PER_BUYER_SIGNAL_SIZE);
        assertThat(stats.getMeanRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MEAN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        assertThat(stats.getMaxRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        assertThat(stats.getMinRawProtectedSignalsSizeBytes()).isEqualTo(0F);
    }
}
